# TASK-478 AC#2 - ExactFinisher: design del pass (fix-and-optimize del residuo)

> zai, 2026-08-01 notte8. Design del pass deterministico che completa il residuo unassigned
> post-repair con enumerazione bounded depth<=2. **FLAG DEFAULT OFF, NON ADOTTATO**: prototipo
> dietro `planner.exact-finisher.enabled` (opt-in, stessa forma di M-TC). A/B (AC#4) rimandato
> a maintainer. Complemento (non sostituto) della procedura portfolio multi-start.
>
> Fonti: AC#1 (`ac1-feasibility-residuo-run5-2026-08-01.md`: depth-0 0-hard completion ESISTE,
> margine 4x-75x); mappatura code-surface (insertion point, contratto runner, gate chain,
> `BoxRepair.findEviction`, `computeConstraintMatchSnapshot`, flag pattern); ricerca
> `research_hard-variance-timetabling_2026-08-01.md` (finding 3: fix-and-optimize).

## 1. Problema e obiettivo
Sul path `source=input` l'hard-0 e' PROBABILISTICO (varianza LS: run4=0hard, run5=-38hard con
16 unassigned su capacita' strette T08/G03 e T19/G06). AC#1 ha dimostrato che il
residuo NON e' infeasibile (celle depth-0 0-hard abbondanti: 45h per 11h necessarie su T08,
377h per 5h su T19) ma e' una trappola dell'ottimo locale LS. Obiettivo: trasformare hard-0
da probabilistico a DETERMINISTICO per il residuo, con un pass che ENUMERA (non soggetto
all'ottimo locale) invece di cercare.

## 2. Quando scatta
A fine coda repair (post-SurplusRebalance), se restano `k` lezioni unassigned (`!pinned &&
timeslot == null`) con `0 < k <= MAX_K` (cap, default 30: il pass e' per residui PICCOLI;
`k > MAX_K` significa problema strutturale che la LS deve gestire, il pass ritorna Result zero).
Flag `planner.exact-finisher.enabled` (default OFF): lettura LIVE in cima a `repair`, Result
zero quando spento (disciplina repair-pass, come `LabDayConsolidation`).

## 3. Innesto nel ciclo staged
In `ScheduleService.runStagedCycle`, **post-SurplusRebalance, pre-B-ROOM** (riga 979-980):
```java
                runAnchorRepair(roomProblem);
                runSurplusRebalance(roomProblem);
                runExactFinisher(roomProblem);      // <-- nuovo, gated dal flag
                stagedStage = STAGE_ROOM_SOLVE;
                runRoomSolveStage(roomProblem);
```
ExactFinisher assegna solo TIMESLOT (gira pre-B-ROOM), non stanze: il tier hard che deve
rispettare e' quello TIMESLOT (H1/H3/H5/H7/H11/H14/H17/H20/H23/H24/H25/H26/H28/H-COPRESENCE).
Le stanze (H2/H-M2/H29) le riempie B-ROOM dopo. Il `finally` del ciclo (988-1007) copre gia'
un'eventuale eccezione nel pass (re-pubblica statics, pulisce stagedStage; la release
mid-ciclo al 963 e' gia' corsa).

## 4. Contratto (sibling di HoleRepair/SurplusRebalance)
`ExactFinisher.repair(schedule, moveTimeslot, nullRoom) -> Result`, dove `Result` espone almeno
`conflictsAdded()` (long, `countConflicts(after) - countConflicts(before)`). Il pass muta lo
schedule SOLO tramite i due callback condivisi `MOVE_TIMESLOT`/`NULL_ROOM` (mai tocca
`ScoreDirector`). Il runner fa snapshot/score/revert:
```java
    private void runExactFinisher(SchoolSchedule problem) {
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        long hardBefore = timeslotTierHard(before);
        ExactFinisher.Result result = ExactFinisher.repair(problem, MOVE_TIMESLOT, NULL_ROOM);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        ensureImproveOrNoop("exact finisher", hardBefore, timeslotTierHard(after),
                result.conflictsAdded(), snapshot, before, after);
    }
```
Improve-or-noop e' `shouldRevert = hardAfter < hardBefore || conflictsAdded != 0`
(`ScheduleService:1460`): due trigger indipendenti, entrambi revert-and-continue, mai throw.
Posare un residuo rimuove 1 H0 (peso hard 2 dal TASK-447) quindi hard migliora strettamente;
`conflictsAdded == 0` e' garantito dalla gate chain per-candidato (sez. 6). La revert e' la
rete di sicurezza per qualsiasi hard worsening che la gate chain del pass non vede (es.
over-depth H-COPRESENCE).

## 5. Algoritmo (enumerazione bounded, prototipo depth 0..1; depth<=2 target futuro)
Enumerazione esaustiva bounded del sottoproblema "k residui x celle x catene di bump",
riattivando la FORMA di `DayAssignmentCsp.enumeratePairsRecursive` (DFS, MCV, branch-and-bound,
early-abort al budget di candidati `planner.exact-finisher.max-candidates`, **knob live default 100000**
-raised 2026-08-02 da 10000 dopo che l'A/B AC#4 a 10000 risulto' budget-starved su un seed cattivo
da 15 residui: il budget E' un knob di calibrazione, non una costante strutturale-) e la disciplina
`RotationEnumerator` (sorted List/TreeMap,
mai iterazione HashMap/HashSet, `EnumResult(capped)` per non troncare in silenzio).

- **Variabili di decisione**: i k residui, ordinati MCV (fewest depth-0 cells prima).
- **Valori**: celle candidate depth-0 (no-bump), poi depth-1 (1 eviction). Depth-0 e' il
  workhorse: AC#1 ha mostrato capacita' larghissima (margine 4x-75x), quindi la prima soluzione
  completa si trova in fretta a depth-0.
- **Prune per-candidato**: la gate chain della sez. 6 (0-hard-additive). Un placement committato
  non aggiunge hard: la catena valida l'arrivo (H1/H3/H14/H11/H7/H17/VRP) e, per i bump, anche
  la partenza del bloccato (`isDepartureSafe`) e la sua relocazione (`BoxRepair.isLegalRelocation`,
  relocation-aware: legge originDay/originVacated per non double-countare H17 Same-day).
- **Depth-1 template**: `BoxRepair.findEviction` (245-303) valida il triplo
  `(ts, blocker O, relocation ts2)` PRIMA di committare entrambe le mosse.
- **Obiettivo**: massimizzare i residui collocati a 0-hard-additive; primo completamento
  valido committato (best-effort, non ottimo globale: la improve-or-noop del runner e' la
  garanzia). `EnumResult.capped` loggato se l'early-abort scatta (mai troncamento silenzioso).

### Depth-2 (DEFERRED, non nel prototipo)
Depth-2 (2 eviction nestate) e' nel design target ma **non nel prototipo** (`MAX_DEPTH=1`):
l'arrival di O1 a ts2 (occupata da O2) ha la stessa classe di bug H17 double-count del
fresh-placement gate, ma non puo' riusare `isLegalRelocation` (ts2 e' occupata da O2, e
`isSlotConflictFree` la rejecterebbe). Servirebbe un gate combinato bump+relocation (il
cap-waiver per O2 che lascia, E la handling H17 per l'origin di O1 vacated), che non esiste in
`BoxRepair.findEviction` (depth-1 only), piu' un test di regressione depth-2. AC#1 ha mostrato
che depth-0 ha margine 4x-75x sul residuo noto, quindi depth-2 e' last-resort e il suo A/B
(AC#4) e' rimandato a prova che depth-0/1 sono insufficienti.

## 6. Legalita' per-candidato (gate chain manuale, NESSUN ScoreDirector nel pass)
I repair pass non costruiscono ScoreDirector/Solver/Verifier per testare un candidato: usano
una catena di Predicate puri (cheapest first, VRP last), da `SurplusRebalance.findMove`:
1. **H1/H3** conflitto docente/gruppo: `StudentDayCompaction.isSlotConflictFree(lesson, ts, byTs)`.
2. **H14/H11/H7/H17-arrival**: `BoxRepair.passesPlacementGates(lesson, ts, groupDaySlots, teacherDaily)`.
3. **H17-departure** (lezione spostata): `BoxRepair.isDepartureSafe`.
4. **VRP** H4/H5/H6/H20/H23/H24/H26 (membership autoritativa): `lesson.getAvailableTimeslots().contains(ts)`. Valutato per ULTIMO perche' e' una scan lineare su ~1368 timeslot.
Indici costruiti una volta per pass: `StudentDayCompaction.buildOccupancyIndex`,
`BoxRepair.buildGroupDaySlots`, `BoxRepair.buildTeacherDaily`.

## 7. Valutazione whole-pass (runner)
`computeConstraintMatchSnapshot` (ScoreDirector con `ConstraintMatchPolicy.ENABLED`) prima/dopo,
`timeslotTierHard` somma hard ESCLUDENDO le famiglie room-tier (`H2/H-M2/H29`). H-COPRESENCE
NON e' escluso (quindi over-depth co-presenza conta come worsening timeslot-tier, e triggera'
revert). Questo e' il check autoritativo improve-or-noop; il pass non chiama API di scoring.

## 8. Flag (default OFF opt-in)
`SchoolSchedule.isExactFinisherEnabled()` copia verbatim `isTagCapacityEnabled`:
```java
    public static boolean isExactFinisherEnabled() {
        return Boolean.parseBoolean(System.getProperty("planner.exact-finisher.enabled"));
    }
```
Lettura LIVE in cima a `repair` (disciplina repair-pass, non defineConstraints), Result zero
quando spento. Banner in `refreshSolverStatics` (~linea 1978, specchio M-TC):
`LOG.infof("ExactFinisher: enabled=%b, ...", SchoolSchedule.isExactFinisherEnabled(), ...)`.
Osservabilita' MAVEN_OPTS: il `-D` raggiunge l'app JVM solo sulla riga `java -jar`
(`PLANNER_JAVA_OPTS`), non via `MAVEN_OPTS` sul fork `mvn quarkus:run` (lezione CLAUDE.md).

## 9. Risoluzione residui e celle
- Residui: `for (Lesson l : schedule.getLessons()) if (!l.pinned && l.timeslot == null)`.
- Celle candidate: `L.getAvailableTimeslots()` (VRP-filtered; `List.of(timeslot)` se pinned, ma
  un boxed e' unpinned per definizione).
- Entita': `getTeacher()/getStudentGroup()/getSubject()` stanno sul `Course`; `isLab()`.

## 10. Determinismo
Ogni collezione iterata per una decisione di ordering e' un `List` ordinato o `TreeMap`, mai
iterazione `HashMap`/`HashSet` (il solver esige costruzione riproducibile). Riusa
`RotationEnumerator.sortedWeekdays`-style helpers.

## 11. Test (catena localizzata, vedi `localized-testing-guidelines.md`)
- **Depth-0 completion**: fixture con lezioni boxed e celle libere abbondanti; assert
  `residualAfter == 0`, `conflictsAdded == 0`.
- **Flag OFF = noop**: con flag spento, `repair` ritorna Result zero e lo schedule e' invariato
  (boxed count invariato).
- **Depth-1 H17 regression** (bug confermato, fix: `isLegalRelocation`): fixture dove la sola
  cella per L richiede l'eviction di O (stesso docente, gruppo diverso), e la sola relocazione
  same-day di O allargherebbe un gap H17 sul giorno del gruppo di O; assert la catena e'
  rejectata (L resta boxed, `movesApplied == 0`).
- **MAX_K cap**: piu' di `MAX_K` boxed -> Result zero, nessun tentativo.
- **shouldRevert four-quadrants**: gia' coperto da `RepairGuardRevertTest` (service package).
- **Build**: `./scripts/core/test.sh` EXIT 0 (H2 in-memory, no PostgreSQL).

## 12. Non adozione
Flag DEFAULT OFF. Nessun run di campagna lo attiva (run7/run8 portfolio size=2 lo hanno
spento). AC#4 (A/B n>=2 run input, hard-0 deterministico) rimandato a maintainer con un gate laptop.
Complemento del portfolio (riduce P(strand)), non sostituto: insieme producono hard-0 robusto
e deterministico per il flip m-13.
