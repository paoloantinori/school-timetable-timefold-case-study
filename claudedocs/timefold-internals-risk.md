# Registro fork-risk: dipendenze dai Timefold internals

Data: 2026-09-27 (TASK-653). Versione di riferimento: **2.7.0-rc-1** (pom.xml:16).

## Metodo

Censimento meccanico (grep su src/main/java) di ogni import/uso da `impl/*`,
`preview/*`, reflection wiring e API enterprise-gated. Per ognuna: cosa facciamo,
traiettoria upstream nota, segnale d'allarme, piano B.

---

## A. impl/score/* (catena di consegna)

### A1. ConstraintMatch + ConstraintMatchTotal + ConstraintMatchPolicy
- **Dove**: ScheduleService (analysis drill-down, analyzeScore), ViolationScopedSteepestFinisher (score con match per target selection)
- **Cosa facciamo**: ricaviamo le violazioni per vincolo (nome, match, punteggio) per il drill-down UI e per il target del 638. Policy ENABLED su entrambi i SolutionManager.
- **Traiettoria upstream**: l'api.score.analysis pubblic è documentata "exclusive to Enterprise Edition". I impl.score.constraint sono la base dell'enterprise; potrebbero essere spostati/murati.
- **Allarme**: compile error su upgrade (import mancante) oppure ClassNotFoundException a runtime. **VERDE su rc-1** (suite 1370/0 + banco 3/3).
- **Piano B**: il drill-down è già nostro (uso privato di dati che il nostro score director produce); la via di uscita è un nostro re-scorer che gira i constraint stream e colleziona match, come fa il banco DeliveryTrioBenchTest (già nostro codice, zero Timefold).

### A2. InnerScoreDirector + ScoreDirectorFactory + DefaultSolutionManager
- **Dove**: ScheduleService (analyzeScore, solve orchestration), MoveThreadRunner (executeTemporaryMove nel MT-LS)
- **Cosa facciamo**: costruiamo SolutionManager/InnerScoreDirector per ricavare score e match, e per il multithreaded LS valutiamo le mosse in parallelo.
- **Traiettoria**: l'InnerScoreDirector è l'ossatura interna del solver. #2649 (rewrite MoveDirector) ne ha riscritto il consumer diretto.
- **Allarme**: firma di executeTemporaryMove cambiata (parametri/return type). **VERDE su rc-1**.
- **Piano B**: il MT-LS decider è già nostro (multiStartEnabled → our wrapper); se executeTemporaryMove cambia, la reimplementazione è un clone + apply + revert, tutto codice nostro possibile.

### A3. ScoreDirector (impl, non api)
- **Dove**: AdaptiveStreamWeightFactories, CompositeMoveFilter, GapPreservingMoveFilter, PairBlockShiftMoveIteratorFactory + multithreaded/
- **Nota**: in 2.3.0 è in impl, NON in api (commento esplicito in PairBlockShiftMoveIteratorFactory.java:10). La firma usata è minimale (calculateScore, getWorkingSolution ecc.).
- **Allarme**: se viene promossa a api (cambio package), l'import cambia ma il codice resta identico.
- **Piano B**: nessun piano B necessario (interfaccia stabile, uso minimale).

---

## B. impl/localsearch/* (MT-LS decider)

### B1. MultiThreadedLocalSearchDecider wiring (famiglia più profonda)
- **Dove**: solver/multithreaded/ (7 file, il nostro decider custom)
- **Cosa facciamo**: decider MT-LS completo con canali per-thread, single-writer dispatch, adaptive weights. Wiring via reflection su DefaultLocalSearchPhase.decider (MultiThreadedLsWiring.java:52-53: getDeclaredField + setAccessible).
- **Traiettoria**: la famiglia LocalSearchDecider/Acceptor/Forager è interna da sempre. La reflection sul campo decider è fragile per costruzione (documentato nel javadoc del wiring come "pinned to Timefold 2.3.0 internals").
- **Allarme**: cambio del nome del campo decider, o introduzione di un layer intermedio (già visto in 2.7.0 con DefaultLocalSearchPhase che ora ha un costruttore diverso). **VERDE su rc-1** (con l'aggiornamento del wiring).
- **Piano B**: REIMPLEMENTAZIONE PROPRIA. Il decider è già nostro nel contenuto (il 90% del codice è nostro: canali, dispatch, adaptive weights, barrier). Il wiring reflection è l'unica parte che tocca internals. Un eigen-solver ad hoc (senza SolverFactory) è la via di uscita: non serve il framework, serve solo un builder che genera mosse e un nostro score director (vedi A1 piano B).

### B2. InnerScore + ChildThreadType
- **Dove**: ApplyStepOperation, MoveResultQueue, MoveThreadRunner
- **Allarme**: tipo cancellato o rinominato. **VERDE su rc-1**.
- **Piano B**: InnerScore è un record semplicissimo (score + match): rimpiazzabile con un nostro record in un'ora.

### B3. MoveRepository + scope classes (LocalSearchMoveScope, LocalSearchPhaseScope, LocalSearchStepScope)
- **Dove**: MultiThreadedLocalSearchDecider
- **Allarme**: firma o gerarchia cambiata. **VERDE su rc-1**.
- **Piano B**: i nostri scope wrapper sono già quasi tutti nostri; le classi Timefold forniscono solo il contesto (move, step index, ecc.), rimpiazzabile con un nostro context object.

---

## C. impl/heuristic/* (selector e filter)

### C1. SelectionProbabilityWeightFactory + SelectionFilter
- **Dove**: AdaptiveStreamWeightFactories (probability weights), CompositeMoveFilter + GapPreservingMoveFilter (selection filters)
- **Nota**: queste sono le interfacce TIMEFOLD per pluggare nel solver config XML. Se Timefold le muove, il solverConfig.xml non le trova più.
- **Allarme**: NoSuchBeanDefinition o errore di configurazione XML all'avvio del solver.
- **Piano B**: il move filtering può avvenire nel nostro decider (già lo fa per alcuni filtri); la probability weight è per i move selector, che potremmo sostituire con il nostro generatore di mosse.

### C2. MoveIteratorFactory
- **Dove**: PairBlockShiftMoveIteratorFactory (meccanica pair-block, macchina storica)
- **Allarme**: interfaccia cambiata. **VERDE su rc-1**.
- **Piano B**: il PairBlockShift è già sostituibile dal metamodel-based VSF (641).

---

## D. preview/api/* (API in preview)

### D1. Moves.compose + Move (preview)
- **Dove**: ViolationScopedSteepestFinisher (compose per composite move + room reassign), MoveThreadRunner (rebasing)
- **Nota**: preview API. Possono cambiare senza preavviso. La 641 room-aware grammar è costruita su metamodel + Moves.compose.
- **Allarme**: rimozione da preview (promozione a stable con firma diversa, o deprecazione).
- **Piano B**: compose è un loop di apply: reimplementabile. Il metamodel è più delicato (vedi D2).

### D2. PlanningVariableMetaModel + metamodel-based moves
- **Dove**: ViolationScopedSteepestFinisher (variableMetaModel, roomMetaModel, moveFor)
- **Nota**: il metamodel è il cuore della 641 room-aware grammar. Il metamodel è già preview da 2.0; la traiettoria è verso stable ma con breaking changes possibili.
- **Allarme**: firma di PlanningVariableMetaModel / moveFor cambiata. **VERDE su rc-1**.
- **Piano B**: il 641 può essere riscritto in move-style classico (Move interface con doMove/undoMove, come il vecchio PairBlockShift): più codice ma zero preview API.

### D3. executeTemporaryMove (preview su InnerScoreDirector)
- **Dove**: VSF (valutazione), MoveThreadRunner (MT-LS)
- **Nota**: metodo di InnerScoreDirector che esegue una mossa temporanea e ritorna lo score senza commit. È il cuore della valutazione incrementale sia del 638 che del MT-LS.
- **Allarme**: firma o semantica cambiata (es. ritorna tipo diverso). **VERDE su rc-1**.
- **Piano B**: clone + apply + calculateScore + revert = executeTemporaryMove fatta a mano. Già fattibile (il banco lo fa in modalità replay).

---

## E. Reflection wiring (MT-LS)

### E1. MultiThreadedLsWiring (getDeclaredField su DefaultLocalSearchPhase.decider)
- **Dove**: MultiThreadedLsWiring.java:52-53
- **Nota**: l'UNICO punto in cui usiamo reflection su Timefold. Fragile per costruzione, documentato come "pinned to 2.3.0 internals". Aggiornato per 2.7.0-rc-1 (TASK-543).
- **Allarme**: NoSuchFieldException a runtime (campo rinominato o rimosso).
- **Piano B**: costruire il nostro DefaultLocalSearchPhase subclass senza reflection (costruttore esplicito con il nostro decider), oppure un eigen-solver senza SolverFactory (vedi B1).

---

## Sintesi per priorità di rischio

| # | Dipendenza | Rischio | Impatto se rotta | Costo piano B |
|---|---|---|---|---|
| E1 | Reflection wiring decider | **ALTO** (fragile per costruzione) | MT-LS morto | Medio (eigen-solver) |
| A1 | ConstraintMatch/Policy | **MEDIO** (enterprise-gated) | Drill-down + VSF morti | Medio (re-scorer nostro) |
| D1-D2 | Preview API (Moves/metamodel) | **MEDIO** (preview = no garanzia) | 641/638 morti | Alto (riscrittura move-style) |
| A2 | InnerScoreDirector | **BASSO** (impl stabile) | Score/MT-LS | Basso (clone+apply+revert) |
| B2-B3 | Scope/ThreadType | **BASSO** (tipi semplici) | MT-LS compile | Basso (nostri record) |
| C1-C2 | Selector interfaces | **BASSO** (XML config) | Solver config | Basso (nostro generatore) |
| A3 | ScoreDirector impl | **BASSO** (interfaccia minimale) | Filters | Basso |

## Checklist upgrade 2.7.0 finale (29/09)

1. `diff pom.xml` → timefold-solver.version 2.7.0-rc-1 → 2.7.0
2. `mvn clean compile` → cercare import impl/* e preview/* mancanti
3. `grep -rn "getDeclaredField" src/main/java` → il wiring reflection compila ancora?
4. Release notes 2.7.0: cercare "MoveDirector" (rewrite #2649), "Indictment" (rimossa), "score analysis" (enterprise), "preview" (promotion/deprecation), "InnerScoreDirector" (firma)
5. Suite completa (`test.sh`) → tutti i test che toccano impl/* passano?
6. Banco DeliveryTrioBenchTest con PLANNER_TEST_REPLAY_609=1 → il 638/641 gira?
7. Se qualcosa si rompe: **il registro sopra dice il piano B per ogni voce**

## Vincolo sul follow-up mini-GLS nel decider

La direttiva strategica (23/09) impone: il decider MT-LS è già nostro. Un mini-GLS nel decider richiederebbe ConstraintMatchPolicy.ENABLED durante la LS, che oggi funziona solo via MonitoringConfig/environmentMode. Il census deve verificare che questa via sia raggiungibile su OSS **prima** di investire nell'esperimento (gap 3 del PoC 645, confluito qui). Status: NON VERIFICATO su 2.7.0 finale. Da controllare nella finestra del 29/09.
