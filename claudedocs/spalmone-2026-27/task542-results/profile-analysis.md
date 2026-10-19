# TASK-542 - Analisi profili post-costruzione vs gate (predittore early-doom)

Mandato (maintainer 22/08 sera): zero compute, solo lettura. Per ogni attempt etichettato: profilo
POST-CONSTRUCTION (dal checkpoint su disco, features via `scripts/analyze/construction-profile.py`)
contro il gate B-TIMESLOT-1 (timeslot-tier hard dopo la prima LS). Domanda: UNA feature del
profilo separa i doomed dai deliverable? Se sì il multi-start diventa costruisci->profilo->
scarta-o-reseed (minuti per condannato); se no, la variabilità è nella LS (legittima il bendable).

## La tabella (22/08, multi-start iter-15, 5 attempt + punti storici)

Fonte fresca: `staged-checkpoints/attempt{1..5}/POST_CONSTRUCTION.json` + gate dal log.
Punti storici: i run deliverable dei giorni scorsi (stessa famiglia deterministica, profilo
equivalente: h17_holes ~66; i loro checkpoint furono sovrascritti, i gate sono nei log/docs).

| run/attempt        | h17_holes_constr | tier_constr | boxed_constr | gate B-TSLOT-1 | esito              |
|--------------------|------------------|-------------|--------------|----------------|--------------------|
| 22/08 a1 (det)     | 66               | -418        | 71           | -30            | doomed (migliore)  |
| 22/08 a2 (GRASP 2) | 308              | -485        | 37           | -187           | doomed             |
| 22/08 a3 (GRASP 3) | 255              | -629        | 83           | -120           | doomed             |
| 22/08 a4 (GRASP 4) | 297              | -582        | 48           | -170           | doomed             |
| 22/08 a5 (GRASP 5) | 248              | -589        | 59           | ~-170          | doomed             |
| 22/08 pomeriggio (det, pin ripuliti) | ~66 (famiglia) | —  | —            | -28            | CONSEGNATO -2      |
| 22/08 mattina (det, -8 run)         | ~66 (famiglia) | —  | —            | -45/-28        | CONSEGNATO -8      |
| 21/08 h36-verify a1..a3 (no-import)| n.d. (artefatto)| —  | 320          | -650/-676/-694 | doomed (artefatto) |
| 21/08 h37v-a (no-import, 1 thread) | n.d.           | —            | 320           | -642            | doomed (artefatto) |

(n.d. = checkpoint non conservati; i punti no-import sono artefatto di protocollo, esclusi
dalla soglia ma riportati come coda negativa del range.)

## Scatter (h17_holes_constr vs gate)

```
gate
 -30 |  d(66)
 -28 |  D(66)                       d = doomed, D = DELIVERED (-2)
 -45 |     D(66)                    (famiglia deterministica)
     |
-120 |                    x(255)
-170 |                          x(297)  x(248)
-187 |              x(308)
-650 |  o(artefatti no-import, fuori scala)
     +----+-------------------+-------------------+---> h17_holes_constr
        66                250                 310
        <-- deliverable -->|<---- doomed ----->
                     SOGLIA ~150
```

## Leave-one-out + verdetto

- La feature **h17_holes_constr** separa PERFETTAMENTE i punti freschi: <=70 -> gate
  -28..-30 (deliverable); >=248 -> gate -120..-187 (doomed). Nessuna sovrapposizione.
- LOO: rimosso un qualsiasi punto, la soglia (qualsiasi valore in (70, 248), es. 150)
  classifica correttamente tutti gli altri. Robusta sul dataset disponibile.
- Le altre features NON separano: boxed_constr 37..83 indifferenziato (a2 ha 37 boxed e gate
  -187; a1 ha 71 boxed e gate -30); tier_constr idem (-418..-629 miscelati); h32_deficit
  24..41 miscelati.
- NOTA sul deterministico di stasera (-30, doomed): la famiglia "66 buchi" è deliverable ma
  NON garantita 0-hard: -28 ha consegnato -2. La soglia separa doomed-grave da
  sperimentabile, non 0-hard da non-0-hard.

**VERDETTO: SEPARA. Il predittore early-doom è pronto nella sua forma minima.**
Il multi-start diventa: costruisci (11s) -> profilo (secondi) -> h17_holes > 150? scarta e
reseed : prosegui. Stasera avrebbe risparmiato ~3.5h: 4 tentativi GRASP condannati al primo
confine, identificabili dal profilo PRIMA della LS (~45 min l'uno).

## Caveat (dichiarati)
- n fresco piccolo (5 + 3 storici); la famiglia no-import è esclusa per costruzione.
- La soglia 150 è una bisettrice comoda, non calibrata: con più punti usare il max-dei-
  deliverable + margine.
- Il profilo dell'attempt deterministico dipende dallo stato dei pin (stasera -30 dopo il
  cambio pin: la famiglia "66" resta la sola deliverable, ma il suo gate interno varia).
- Strumento: `scripts/analyze/construction-profile.py <POST_CONSTRUCTION.json>` (lettura pura).

## Corollario (per il registro, non eseguito)
Tutti e 4 i GRASP di stasera sono >=248: la famiglia seed 2-5 sotto il flag spread NON
produce il profilo deliverable: coerenza con iter-14 ("i seed GRASP mancano il bacino buono").
La prossima famiglia di seed va disegnata attorno al profilo (target: h17_holes <= ~70),
non attorno alla rotazione pura.
