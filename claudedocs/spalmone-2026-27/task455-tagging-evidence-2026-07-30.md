# TASK-455 tagging: derivazione dal golden (PASSO A, mandato maintainer 2026-07-30)

**Mandato:** non applicare la rimozione del tag, MISURARE quale tagging e' piu'
efficace. PASSO A = derivare il tagging dall'evidenza del golden (non dai nomi):
per ogni materia che porta un tag, compare nel golden in celle dove le lezioni
di quel tag superano le stanze? Una materia che compare sistematicamente e' una
che la scuola di fatto NON tiene in laboratorio; una che non compare mai non ci
dice niente. Tabella per TUTTI i tag, rifatta da me sui raw.

Strumento: `scripts/analyze/golden-tag-evidence-kpi.py`. Fonti: golden
`data/lessons.csv`, `seed/subjects.csv` (subject->tag), capienza per tag dalle
stanze assignable dell'export 30e.

## Risultato: il sovraffollamento golden e' solo acconciatura, ed e' SIMMETRICO

Celle golden sovra-capienza (domanda > stanze) per tag:

| tag | celle | lezioni in esse | cap |
|---|---|---|---|
| **acconciatura** | **42** | **84** | 1 |
| moda | 2 | 4 | 1 |
| informatica / meccanica / saldatura / termoidraulica | 0 | 0 | - |

**Correzione al "44 (30 G14 + 14 G12)" di maintainer:** quel conteggio catturava
solo la META' Servizio stilistico. Le 42 celle hanno TUTTE la stessa firma,
esattamente una lezione per ciascuno dei due soggetti:

```
42 celle x 2 lezioni = 84, firma identica (Servizio stilistico + Servizio tecnico e stilistico)
es: 2026-10-29 h8 = [Servizio tecnico e stilistico, G02, T01]
                   + [Servizio stilistico, G12, T26]
```

Composizione (lati della collisione):
- Servizio stilistico (T26): G14 30 + G12 12 = 42
- Servizio tecnico e stilistico (T01): G06 30 + G02 10 + G10 2 = 42

## Conclusione strutturale: de-tag di Servizio stilistico azzera le 42 celle

Ogni cella sovra-capienza ha esattamente 1 Servizio stilistico + 1 Servizio
tecnico e stilistico. Togliere il tag a uno dei due lascia 1 lezione per cella
(sotto cap). Verificato: de-tag di Servizio stilistico -> 0 celle residue;
de-tag di Servizio tecnico e stilistico -> 0 celle residue.

**Si de-tagga Servizio stilistico (T26, lato flessibile), NON Servizio
tecnico e stilistico** (T01, lab core del giovedi' pinnato da TASK-435:
de-taggarlo romperebbe il modello). L'evidenza isola il soggetto giusto.

## PASSO A, tavola per materia (evidenza golden)

| materia | tag | tot golden | in celle sovra | tasso |
|---|---|---|---|---|
| Servizio stilistico | acconciatura | 200 | 42 | 21% (evidence) |
| Servizio tecnico e stilistico | acconciatura | 231 | 42 | 18% (T01 core, NON de-tag) |
| Modello (Moulage) | moda | 358 | 2 | 0.6% |
| Confezione | moda | 470 | 2 | 0.4% |
| Barberia / Lavaggio | acconciatura | 128 / 160 | 0 / 0 | 0% |
| Cad / Controllo numerico / Informatica | informatica | - | 0 | 0% |
| Laboratorio Termoidraulico | termoidraulica | 313 | 0 | 0% |
| Lavorazioni meccaniche | meccanica | 402 | 0 | 0% |
| Tecniche di saldatura | saldatura | 40 | 0 | 0% |

Solo i due "Servizio" appaiono sistematicamente; di questi, solo Servizio
stilistico e' un candidato de-tag (l'altro e' il lab T01).

## Sizing delle varianti (run 30e, Servizio stilistico room-null per gruppo)

| gruppo | room-null run | esonerato dall'evidenza? |
|---|---|---|
| G14 | 21h | SI' (30 celle golden) |
| G12 | 16h | SI' (12 celle golden) |
| G06 | 20h | NO (0 celle golden) |
| G02 | 3h | NO (0 celle golden) |
| **TOTALE** | **60h** | **37h evidence + 23h naming** |

Domanda totale golden Servizio stilistico: G06 50 + G10 50 + G02 40 +
G12 30 + G14 30 = 200h.

## Meccanismo: la variante 1 (per-corso) E' esprimibile, non si e' forzati a v2

Il tag e' PER MATERIA in `seed/subjects.csv`, ma `seed/subject_overrides.csv`
(formato `group,subject,roomTags,professionalizzanti`, sentinella `NONE` per il
de-tag, vuoto sul 2026/27) supporta override per-(gruppo,materia). Quindi:

- **v0 baseline**: tagging attuale (= run 30e).
- **v1 (evidence)**: de-tag Servizio stilistico SOLO per G14 + G12 via
  `subject_overrides.csv` (righe `G14,Servizio stilistico,NONE,` e
  `G12,Servizio stilistico,NONE,`). Recupero atteso 37h di room-null.
- **v2 (all)**: de-tag Servizio stilistico per tutti via `subjects.csv` (via il
  tag acconciatura dalla riga `Servizio stilistico,acconciatura,true`).
  Recupero atteso 60h, ma 23h (G06 + G02) non poggiano sull'evidenza, solo
  sull'argomento di denominazione (la scuola distingue "Servizio stilistico" da
  "Servizio tecnico e stilistico", il cui pezzo "tecnico" e' plausibilmente il
  lavoro di laboratorio).

**Non si e' costretti a v2 per comodita' di strumento**: v1 e' pulitamente
esprimibile tramite l'override esistente. La scelta tra v1 e v2 e' di maintainer sui
numeri (PASSO B/C), non dettata dal meccanismo.

## PASSO B (da fare): misura delle 3 varianti al checkpoint

Per ciascuna variante (v0=30e gia' catturato, v1, v2): solve staged su profilo
dev fresco (stop+delete+reseed), import-calendario, pre-assign (T01
231/231), capture-run. Confronto: room-null per gruppo/materia + i 3 KPI di
guardia (full-day ex-G14, M15, buchi H17). Scopo: vedere quale tagging rende
piu' e a quale prezzo, non quale sembra piu' sensato. Raccomandazione (PASSO C)
sui numeri, decisione di maintainer.

## PASSO B (fatto al CHECKPOINT, non a solve completo): v0/v1/v2 sul export 30e

Misurato con `RoomPhaseSnapshotTest` in modalita' export-reload (ricarica
l'export 30e e rigira SOLO la fase aule, minuti non 50min, isola l'effetto
aule senza la varianza della ricerca). Leve: `PLANNER_TEST_ROOM_TAG_UNTAG_GROUP`
(v1, per gruppo-materia, azzera roomTags + isLab) e `PLANNER_TEST_ROOM_TAG_UNTAG`
(v2, prefisso materia globale, azzera roomTags ma LASCIA isLab). UNTAG lascia
isLab=true e quindi gonfia il conteggio "lab room-null" (lezioni isLab ma senza
tag, non roomabili come lab): per una v2 onesta si usa UNTAG_GROUP su tutti i 5
gruppi (isLab azzerato). M-TC SPENTO (un fattore alla volta).

Confronto (room-null DOPO la fase-5 LS, budget 10s, sul placement fisso del 30e):

| variante | lab room-null after | non-lab room-null after | totale | hard |
|---|---|---|---|---|
| v0 baseline | 169 | 0 | 169 | -177 |
| **v1** G14+G12 (2 corsi, 136 stanze nulled) | 125 | 1 | 126 | **-134** |
| v2 all-5 (UNTAG_GROUP, 5 corsi, 253 nulled) | 75 | **89** | 164 | -172 |

Lettura:
- **v1 e' una VITTORIA PULITA.** 169 -> 125 lab room-null (44 chiuse, di cui 37
  escono dalla contesa per il de-tag + 7 chiuse dalla LS che riempie la capacita'
  acconciatura liberata), non-lab 0 -> 1 (le 136 lezioni de-taggate trovano tutte
  aula). hard -177 -> -134 (migliore delle tre). Nessuna ora ordinaria peggiora.
- **v2 e' un PAREGGIO.** Chiude 94 ore lab (169 -> 75) ma le 253 lezioni
  de-taggate ALLAGANO le aule: 89 ore ordinarie restano senza stanza (non-lab
  0 -> 89). Totale 169 -> 164 (appena -5): sposta 89 ore da lab a non-lab, non
  le chiude. hard -172 (vicino al baseline).

Perche' v1 si ferma al giusto: de-taggare i 2 corsi dell'evidenza (G14+G12,
37h) scarica 136 lezioni nelle aule che le ASSORBISCONO. De-taggare anche i 3
gruppi senza evidenza (1/2/G10, 23h in piu') porta le lezioni de-taggate a 253
e le aule (10 stanze) non bastano: 89 restano fuori. La capacita' aula e' il
vincolo, ed e' strutturale.

Caveat: il checkpoint fissa il placement del 30e e ri-rooma solo. Per v2, un
solve completo potrebbe SPARGERe le 253 lezioni su altri slot e ridurre le 89
non-lab. Il checkpoint e' quindi CONSERVATIVO per v2 (non mostra il sollievo
dello spreading). Ma v1 e' gia' pulita al checkpoint (aule assorbono a placement
fisso): e' la scelta SICURA. v2 varrebbe un solve completo solo se maintainer vuole
spingere oltre l'evidenza, accettando il rischio aula.

## PASSO C: raccomandazione

**v1** (de-tag Servizio stilistico per G14 + G12 via
`seed/subject_overrides.csv`, righe `G14,Servizio stilistico,NONE,` e
`G12,Servizio stilistico,NONE,`). Motivo: e' l'unica basata sull'EVIDENZA del
golden (i due casi che causano le 42 sovrapposizioni), e al checkpoint chiude 44
ore lab (169->125) senza peggiorare nessuna ora ordinaria (non-lab 0->1), hard
-177->-134. v2 chiude di piu' al lab ma allaga le aule (89 non-lab): scambio
sfavorevole, e i 23h extra (1/2/G10) non hanno evidenza. Decisione di maintainer
sui numeri; se scelta v1, un solve completo di conferma su quella sola variante.

## Nota di metodo (seconda volta oggi)

PRIMA di lanciare qualunque solve lungo, chiedersi se esiste un harness di
checkpoint che risponde alla stessa domanda. La piramide dei test localizzati
(`claudedocs/localized-testing-guidelines.md`) va consultata PRIMA, non dopo.
Qui `RoomPhaseSnapshotTest` (export-reload, leva UNTAG/UNTAG_GROUP) misurava le
3 varianti in minuti isolando la fase aule, contro i ~50min a solve completo con
la varianza della ricerca. L'ho scorso ieri e l'ho dimenticato: perso tempo su
due setup di solve falliti prima di tornare allo strumento giusto.

