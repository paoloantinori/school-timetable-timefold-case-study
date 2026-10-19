# H28 shadow variable spike - design e verdetto (2026-08-16)

> Filone 2 del mandato Fedora 16/08. Domanda: puo' una custom shadow variable
> precomputare "docente A ha gia' ceduto il blocco in questo (gruppo, materia,
> giorno)" cosi' che H28 non debba fare il join a tre ad ogni valutazione?
> Criterio del mandato: prototipo gated SOLO se beneficio plausibile >10% move-eval
> speed; altrimenti doc-only con stima costo/benefizio e chiusura dello spike.
> Riferimento: `claudedocs/spalmone-2026-27/constraint-profiling-2026-08-15.md`
> (commits e5672334).

## VERDETTO: doc-only, SPIKE CHIUSO. La barra >10% non e' raggiunta per un fattore ~3x, e il meccanismo Timefold 2.3.0 non ha un'espressione pulita per la semantica richiesta.

## 1. Cosa e' H28 oggi (forma del codice)

`SchoolScheduleConstraintProvider.teacherNoReturnWithinGroupSubjectDay`: dentro
(gruppo, materia, data), penalizza la coppia di lezioni STESSO docente (a, b) tra
le quali esiste una lezione c con docente DIVERSO in slot strettamente intermedio
(pattern A-B-A). Implementazione: self-join indicizzato su tre chiavi
(Joiners.equal gruppo/materia/data) + filtering (ordine slot, stesso docente) +
ifExists su un terzo stream (stesse tre chiavi + filtering slot-strettamente-tra
e docente-diverso). E' l'unico della top del profiler con join a tre e
ordinamento intra-giorno.

## 2. La lettura ONESTA dei numeri del profiler (la parte che decide)

Il leave-one-out misura `armSpeed - baselineSpeed` in CALCOLI/SECONDO: quanto
accelera il cardio' quando il vincolo viene escluso. Base 13.977 calcs/s
(`e1-leave-one-out-ranking-2026-08-15.txt`, budget 20s/braccio):

| | delta | lettura |
|---|---|---|
| H1 | +1975 | rank 1 |
| H3 | +1798 | rank 2 |
| **H28** | **+1641** | **rank 3** |
| **S9 (no-op vero: non costruito)** | **+1203** | **il pavimento** |

Il pavimento e' la chiave: escludere un vincolo che NON ESISTE "accelera" di
+1203 calcs/s, perche' l'esclusione cambia lo score di ogni mossa e quindi la
TRAiettoria della LS (caveat scritto nel test stesso: "the RANKING is the
deliverable, not any single delta"). Il costo MARGINALE di H28 sopra il rumore
di traiettoria e':

```
1641 - 1203 = 438 calcs/s  ->  438 / 13977 = 3,1% del cardio
```

 Quel 3,1% e' il soffitto del guadagno se H28 diventasse LETTERALMENTE gratuito
 (zero tuple, zero join, zero listener). Una shadow variable non lo rende
 gratuito: nella forma migliore il vincolo restere' uno stream forEach+filter
 (la forma del pavimento stesso, un tuple per lezione) PIU' il costo del
 recompute del supplier ad ogni mossa. Guadagno realistico atteso: 1-2% netto,
 con rischio di segno negativo (sez. 4).

## 3. Il meccanismo Timefold 2.3.0 non e' il "VariableListener" del profiler doc

Il suggerimento del profiler doc e' scritto in vocabolario OptaPlanner-era
("VariableListener", "precompute per-lezione"). Verificato sull'artefatto reale
(jar `timefold-solver-core-2.3.0`, listing + javap) e sulla documentazione
corrente (migration guide "variable-listeners-to-custom-shadow-variables"):

- NON esiste `@CustomShadowVariable` ne' `VariableListener` pubblico in
  `ai.timefold.solver.core.api.domain.variable` (solo: ShadowVariable,
  ShadowVariablesInconsistent, InverseRelation, Previous/NextElement, Index,
  CascadingUpdate, ShadowSources).
- Il modello 2.x e' SUPPLIER-BASED: `@ShadowVariable(supplierName="...")` su un
  metodo @ShadowSources dell'ENTITA', puro e deterministico, che computa il
  valore della shadow di QUELL'entita' dalle variabili sorgente dichiarate.

Il mismatch per H28: "il docente di questa lezione torna piu' avanti nel blocco
(gruppo, materia, data)" dipende dai TIMESLOT delle ALTRE lezioni del blocco,
non solo dai propri. Le sorgenti cross-entita' in Timefold 2.x si esprimono
SOLO attraverso shadow relazioni DICHIARATE (inverse relation, previous/next),
che operano sull'identita' del valore della genuine variable (timeslot ->
lezioni a QUEL timeslot). La chiave di blocco (gruppo, materia, DATA) e'
invece una chiave COMPOSTA e DINAMICA (la data viene dal timeslot, che e' la
planning variable): non esiste una shadow relation built-in per "i membri del
mio blocco", e un supplier che scanni la soluzione non verrebbe ri-triggerato
quando un'ALTRA lezione entra/esce dal blocco (il trigger e' sulle sorgenti
dell'entita' stessa). Le vie d'uscita sono tutte brutte:

(a) supplier con scansione della soluzione e trigger dichiarato su una sorgente
    che cambia "spesso abbastanza": semantica impura, shadow stantie = score
    corrotto. VIETATA (score corruption e' il rischio numero uno della casa).
(b) catena di shadow per-slot (inverse relation su timeslot + predicato per
    slot + cascading): O(slot del giorno) shadow per lezione, ognuna da
    mantenere nel hot path. Complessita' che mangia il 3% di soffitto.
(c) rimodellare il blocco come entita' (planning entity "blocco"): cambio di
    MODELLO, fuori dallo scope della direttiva (il modello si tocca solo per
    correttezza logica, mai per costo).

## 4. Il costo del recompute nel hot path (dove finirebbe il lavoro)

Il lavoro non sparisce: si sposta nel supplier. Nella MT-LS le 8 move thread
valutano ~100k mosse/secondi totali (banda misurata, vedi AdaptiveMoveWeights:
"a ~100k evals/s a 4000-eval segment ticks every ~40ms"); ogni valutazione
APPLICA la mossa sul proprio score director e la undo. Con la shadow, ogni
apply tocca i supplier delle lezioni coinvolte E delle lezioni dei blocchi
interessati (via cascading se si fa la (b)). A fronte, il join a tre attuale
gira sul motore incrementale di Timefold: una mossa di timeslot ritira/reinserisce
SOLO le tuple dei bucket (gruppo, materia, data) toccati. Il +438 sopra pavimento
e' gia' il costo incrementale, non un ricomputo globale. Non c'e' margine
strutturale: si scambia tuple churn indicizzato con lavoro di recompute
dichiarativo, al netto di ~0.

## 5. Cosa resta sul tavolo (micro-leva, NON fatta)

Se un giorno servisse stringere H28 senza shadow: spostare l'uguaglianza
same-teacher dal .filter al Joiners.equal(Lesson::GetTeacher) nel self-join
(pota le coppie candidate prima del filtering lambda). E' un one-liner con
guadagno atteso piccolo (frazione del 3,1%); non lo facciamo ora perche' il
quadro dice che non e' il collo (H1/H3 sono il modello stesso, raccomandazione
del profiler: "NON toccare finche' le leve di selezione rendono piu' di cosi'").

## 6. Riepilogo

| voce | valore |
|---|---|
| Barra del mandato | >10% move-eval speed |
| Soffitto teorico (H28 gratis) | 3,1% (438/13977 calcs/s sopra pavimento) |
| Realistico atteso (shadow shape + supplier) | 1-2%, rischio negativo |
| Meccanismo API | supplier-based, no cross-entity dinamico pulito (sez. 3) |
| Decisione | doc-only, spike CHIUSO, nessun prototipo, nessun flag |

Quando rivalutare: solo se (i) il wall torna ad essere IL collo di tutta la
campagna E (ii) qualcuo altro ha gia' riscosso le leve piu' grosse (selezione
mosse), E (iii) Timefold introduce una shadow relation a chiave composta. Nessuna
delle tre oggi.
