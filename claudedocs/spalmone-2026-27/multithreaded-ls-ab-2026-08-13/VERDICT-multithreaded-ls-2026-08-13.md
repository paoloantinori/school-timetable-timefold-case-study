# VERDICT multi-threaded LS A/B - SUCCESS (25% wall speedup, same score) (zai, 2026-08-13)

Fase 3 consumption experiment (worktree multithreaded-ls-experiment). Il community
MultiThreadedLocalSearchDecider (Fase 2) funziona end-to-end: stesso score, KPI in banda,
**25% wall reduction** (114 min -> 86 min).

## A/B Result

| metric | OFF (single-thread) | ON (8 threads) | delta |
|--------|---------------------|----------------|-------|
| hard | **0hard** | **0hard** | = |
| ricorrenza | 75,6% | 75,7% | +0,1pp (stability spread) |
| full-day | ~86% (band) | 85,8% | in band |
| medium | -1222k | -1277k | -55k (variance) |
| **wall** | **114 min** | **86 min** | **-25% (-28 min)** |

Source=golden, config ratificato (tutti i flag DEFAULT ON), within-session (worktree).

## Analysis

1. **Correctness: preserved.** hard=0 su entrambi, ricorrenza in banda (0,2pp spread). Il
   multi-threading non degrada la qualità dell'orario. L'algoritmo step chasing mantiene la
   consistenza dei child ScoreDirector (createChildThreadScoreDirector + executeTemporaryMove +
   ApplyStepOperation sync).
2. **Performance: 25% wall reduction.** Il LS puro (B-TIMESLOT + B-ROOM, ~72min ON vs ~100min
   OFF) è accelerato del ~28%. Le fasi single-threaded (construction, repair, pre-assign)
   non benefitciano (circa ~14min invariate). Per multi-start N=3: risparmio ~84 min totali.
3. **BrokenBarrierException (58 errori): EXPECTED.** Si verificano ai phase boundaries
   (phaseEnded -> shutdownMoveThreads -> shutdownNow interrompe i thread che sono al barrier
   dopo l'ultimo ApplyStepOperation). Non è un difetto: il solver termina correttamente la fase,
   raccoglie il best score, poi passa alla fase successiva che ri-crea il pool. I log ERROR
   vanno silenziati (downgrade a DEBUG o catch-and-log-info nel runner).

## TASK-480 fork: completo (3 fasi)

| fase | status | result |
|------|--------|--------|
| 1 Thread-safety (volatile) | DONE (7551b7dc) | Lesson VRP cache + 3 static volatile |
| 2 Multi-threaded LS decider | DONE (56d8c916) | 8 classi step chasing, spike+smoke test |
| 3 Worktree A/B | DONE (questo doc) | 0hard entrambi, 25% wall, functional |

## Per Fedora

Il community replacement dell'Enterprise multi-threaded LS è **functional + beneficial**.
Non adottato production (gated maintainer: il wiring via reflection è sperimentale, va
stabilizzato con un'API pulita o un modulo Maven dedicato prima della produzione).
Il serial multi-start (TASK-483, ratificato) resta il workaround production.
Questo fork è la via strutturale per eliminare la dipendenza Enterprise a lungo termine.

### Known issues (per future iterazioni)
- BrokenBarrierException ai phase boundaries (cosmetico, downgrade log)
- Reflection wiring (MultiThreadedLsWiring): fragile, da sostituire con SolverConfig
  programmatico quando Timefold esporra' il hook
- Buffer size fisso (10x threads): tunable, potrebbe migliorare lo speedup
- L'InnerScore nei MoveResult usa wildcard (? extends Score): il forager potrebbe
  beneficiare del tipo concreto per evitare unboxing
