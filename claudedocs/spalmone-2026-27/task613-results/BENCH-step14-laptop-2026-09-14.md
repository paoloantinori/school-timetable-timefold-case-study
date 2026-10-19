# TASK-613 step14: prima corsa laptop (banco post-review, 14/09, heap dedicato 12g)

Mandato Fedora 14/09 post-consegna gate A/B TASK-612. HEAD: 06feec68 (+ i due commit
laptop c7cc6700/82947c82, nessun tocco a src/). Comando:
`PLANNER_TEST_REPLAY_609=<export> ./scripts/core/test.sh -c "CompositeRepairReplay609Test#step14HoleRelayClosesTeacherLockedHoles" -mva "-Dsurefire.heap=12g"`.
Log completi sul laptop: /tmp/bench-t613-laptop.log e /tmp/bench-t613-laptop-on.log.

## m39-off

```
=== TASK-613 step14 (hole-relay post CSF+EDF-ext 5 cells): targets=3 imports=0
quota-swaps=0 moves=0 | hard -11 -> -3 (CSF+EDF) -> -3 (holes, delta +0)
Tests run: 1, Failures: 0, Errors: 0  =>  EXIT 0
```

Trio hard: **-11 → -3 → -3 (delta +0)**. Targets=3 (i 3 buchi H17 teacher-locked
attesi: T06 11-04, T10 11-04 e 03-02, T21 04-30) ma
**imports=0, quota-swaps=0, moves=0**: NESSUN candidato import legale trovato.
printHardFamilies post: solo `H17 -3 (3 match)`. L'atteso -3 -> -2 (chiusura 3
FB 04-30, sotto-forma a del census) NON si e' materializzato.

## m39-on (cross-basin)

```
=== TASK-613 step14 (hole-relay post CSF+EDF-ext 4 cells): targets=4 imports=0
quota-swaps=0 moves=0 | hard -13 -> -6 (CSF+EDF) -> -6 (holes, delta +0)
Tests run: 1, Failures: 0, Errors: 0  =>  EXIT 0
```

Trio hard: **-13 → -6 → -6 (delta +0)**. Targets=4, zero mosse anche qui.
printHardFamilies post: `H17 -4 (4 match)`, `H32 -1`, `H7 -1 (3410 match)`.

## Lettura (misura, nessun deep-dive come da mandato)

- L'assert soft passa (non-regressione sempre vera: il pass non muove nulla).
- v0.1 su ENTRAMBI i bacini e' un **no-op totale**: l'enumerazione dei target
  vede i buchi giusti (3 e 4), la scansione degli import non trova UNA sola
  mossa legale (imports=0). Non "chiude parte dei buchi": non entra in campo.
- Il pin della prima corsa laptop (che il commit 06feec68 dichiarava in finestra
  aperta) e' quindi: m39-off -3 -> -3, m39-on -6 -> -6, moves=0/0.
- Ipotesi da verificare su Fedora (NON misurato qui): o i landing-gate post-F1/F2
  (solo ultimo slot nel ramo puro; importatore mosso prima del gate coda) sono
  piu' stretti del census Difetto-5 che ha motivato v0, o la forma dei candidati
  sul replay non matcha la sotto-forma (a). Il passo successivo logico e' un
  reject-census stampato nel banco (stile RELAY-*) per distinguere gate-reject
  da enumerazione-vuota.
