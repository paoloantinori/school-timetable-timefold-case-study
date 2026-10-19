# Filone D - Costo maxAttempts=7 (configurazione CI-robusta flip #1) (2026-08-14 sera)

> Mandato: "un solve-staged source=input con -Dplanner.multistart.max-attempts=7
> (multi-start ON, MT-LS ON default): wall e esito (0hard?) della configurazione
> CI-robusta del flip #1."

## Setup

- Stesso JAR del census (e964f667 + TASK-497 cherry-pick, flag 497 OFF irrilevante qui).
- Profilo fresco `bsinput14` (delete + re-seed + import-calendario + pre-assign
  source=input). PLANNER_JAVA_OPTS:
  `-Dplanner.pre-assign.constrained-teacher.source=input -Dplanner.multistart.max-attempts=7`.
- Banner verificati: `Multi-start staged retry: enabled=true, maxAttempts=7`;
  `Multi-threaded LS: enabled=true, threads=8`;
  `Staged solve: MultiThreadedLocalSearchDecider armed (8 threads, 1 LS phases swapped)`.

## Risultato

| metric | valore |
|--------|--------|
| **hard finale** | **0hard** |
| medium / soft | -1.015.071 / +5.804 |
| **wall solve-staged** | **1216s (~20 min)** |
| tentativi usati | **1 di 7** (stop al primo 0-hard, design TASK-483) |
| full-day | 87,8% |
| ricorrenza unpinned | 74,3% (banda source=input) |
| buchi H17 | 0 |
| ERROR nel log | 0 |

## Lettura

1. **Il costo worst-case di N=7 NON si e' materializzato**: la costruzione deterministica
   (tentativo 1) e' atterrata nel bacino buono e il wrapper si e' fermato subito, exactly
   come da design (stop al primo 0-hard). Questo run NON misura il worst case: misura che
   la configurazione N=7 sul good basin costa come un singolo ciclo (zero overhead quando
   non servono retry).
2. **Stima worst-case teorica** (non misurata stanotte): con p=0.684 single-construction,
   P(serve il 7° tentativo) = (1-p)^6 ≈ 0,1% point (0,8% al CI_lo). Se accadesse, il wall
   sarebbe ~7 x ~20-25 min/tentativo (MT-LS ON, census mean ~2200s mono-thread ridotto
   ~25%) = ~2,5-3h. Il budget knob `planner.multistart.budget-seconds` resta il freno
   operativo se quel caso diventasse rilevante.
3. Nota tecnica osservata: la prima riga "armed (8 threads, 0 LS phases swapped)" e' il
   solver di costruzione (nessuna fase LS: la guard non scatta perche' expectsLs=false),
   la seconda "1 LS phases swapped" e' il solver B-TIMESLOT/B-ROOM: comportamento
   corretto, riportato perche' il log di ieri (A/B) mostra la stessa coppia.

Artefatti: questa directory (score, analysis, export, phases, KPI battery, driver.log).
