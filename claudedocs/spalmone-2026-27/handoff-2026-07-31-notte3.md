# HANDOFF notte3 31/07 (zai, dopo (C) CHIUSO + (D) BUILD)

HEAD `feature/cartesian-block-entity`. Pushato fino a 280278f1 (pre-(C)-results). (C) results +
VERDICT da committare ora. (A)/(B)/diagnosi gia' chiusi (notte1/2).

## (C) B-TIMESLOT-1 metaheuristic benchmark CHIUSO
4 bracci staged, budget phase-2 identico dichiarato (3600s/600unimproved/diminishedReturns, acl 1000).
Profilo production fresco per braccio (drop-and-create wipea il DB a ogni restart, per cui ogni
braccio richiede import-calendario + pre-assign freschi: condizioni iniziali identiche, ottimo).
constrained-teacher ON verificato (T01 231/231).

| arm | acceptor | BT1 ttH | BT1 box | BT1 med | final H | final M | fullday | BT1 time |
|-----|----------|--------:|--------:|--------:|--------:|--------:|--------:|---------:|
| A | tabu7 (control) | -16 | 8 | -842684 | -121 | -964300 | 89.3% | ~14m |
| H | tabu3 | -16 | 8 | -857970 | -121 | -978474 | 88.8% | ~22m |
| E | tabu11 | -18 | 9 | -849365 | n/a | n/a | n/a | ~24m |
| B | LA400 | -20 | 10 | -848557 | -125 | -971072 | 88.3% | ~42m |

**VERDETTO: KEEP tabu entity-7.** Miglior B-TIMESLOT-1 (box 8, medium migliore), piu' veloce (14min),
final migliore, full-day migliore. Nessun braccio lo batte. tabu7 e' lo sweet spot (3 e 11 peggiorano).
I repair downstream compensano (final cluster -121/-125, KPI reggono ovunque). Coerente TASK-386 +
benchmark marzo. Dettaglio + tabella raw: `bench-btimeslot1/VERDICT.md`. Capture per-arm in
`bench-btimeslot1/arm-{A-control,B-la400,H-tabu3,E-tabu11}/`.

- **G (LA1000) SKIPPED**: LA400 dominata+lenta (box 10, 42min); smoke LA1000~=LA400. Point
  confermatorio per prossimo run (~75min), bassa priorita' dato il margine chiaro.
- **arm E killed pre-B-ROOM** (budget token): B-TIMESLOT #1-3 completi nel log (box 9->7->5),
  solo lo stadio stanze non finito. La metrica B-TIMESLOT-1 (la primaria) e' integra.

## (D) Portfolio BUILD (flag OFF, commit 280278f1) - NON completato stanotte
- `PortfolioPickBest` + 6 unit test (pass): pick best di N solve per HardMediumSoftScore.
- Wrapper in `ScheduleService.runTimeslotSolveStage` dietro `-Dplanner.portfolio.size` default 1
  (size=1 byte-identical = inerte). size>1: N solve distinct-seed sequenziali + pickBest ( ogni
  loadSchedule e' un problema indipendente; statici read-only durante un solve, draft-005).
- Mutation characterization "solve() muta input?" nel smoke `Phase2SmokeSnapshotTest` (compila,
  NON ancora girato: @QuarkusTest confligge sulla porta 8080 con app up).
- **DA FARE (prossima sessione, app down):**
  1. Run mutation test: `PLANNER_TEST_PH2SMOKE=1 PLANNER_TEST_PH2SMOKE_ACCEPTOR=tabu test.sh -c Phase2SmokeSnapshotTest` (con app GIU'). Conferma solve() muta l'input (atteso: si') => clone necessario.
  2. Portfolio parallelo (executor + clone): draft-005 misura (a) speedup. Ora e' sequenziale = misura (b).
  3. DoD piena sul diff (D): /simplify + /code-review high + test.sh EXIT. (Sul (C) tooling gia' fatto checkpoint; i risultati sono measurement, non codice).

## GOTCHA CRITICO (costato un braccio di setup)
`prod-start-and-wait.sh` lancia `java -jar` RAW, e `application.properties:18` ha
`schema-management=drop-and-create`. `planner.sh start/prod` lo overridea a `update`, ma il launch
raw NO: **ogni restart wipea il DB** e DataGenerator re-seeda. => Per OGNI braccio: dopo restart,
rifare import-calendario + pre-assign-reset + pre-assign (pin non persistono). Verificato T01
231 dopo re-setup. Per futuri benchmark staged su production: o usare `planner.sh prod` (update), o
aggiungere `-Dquarkus.hibernate-orm.schema-management.strategy=update` a PLANNER_JAVA_OPTS.

## Standing
- Flag default-OFF ovunque, NESSUNA adozione (C verdict = "non cambiare"; D = prototipo flag-OFF).
- solverConfig.xml restaurato a canonico (tabu7) dopo il benchmark.
- App DOWN (kill-app alla fine di arm E). caffeinate attivo (PID 22768, ~10h).
- DoD: (C) tooling checkpoint 280278f1 (compile-verify); risultati = measurement. (D) diff non ancora /code-review (flag-OFF scaffolding, zero impatto produzione a size=1).

## Come riprendere
- Verdetto (C): `bench-btimeslot1/VERDICT.md` + tabella raw `btimeslot-bench-summary.py`.
- (D) next: mutation test run (app down) + portfolio parallelo + DoD.
- (E) se resta notte prossima: profiling leave-one-out, A/B isG14, A/B M20-1500 (tutti LS-only, full-solve).
