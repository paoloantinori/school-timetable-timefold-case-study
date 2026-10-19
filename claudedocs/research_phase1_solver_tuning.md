# Research: Phase 1 Pre-Assignment Solver Tuning

**Date**: 2026-02-24
**Query**: Investigate solver XML tweaks and constraint/score changes to improve Phase 1 efficiency
**Depth**: Exhaustive
**Context**: Phase 1 solver takes ~15 minutes to reach -35 hard score for ~2038 pre-assignment entities

---

## Executive Summary (Updated 2026-02-24 20:00)

**BREAKTHROUGH**: Phase 1a now achieves **0hard** in ~7.3 minutes!

**Root Cause**: H17 (Lab Recurring Weekly Pattern) was mathematically infeasible as a Hard constraint due to shared room capacity constraints.

**Solution**: Downgraded H17/H17.2 to M17/M17.2 (Medium constraint).

---

## Phase 1a Results (LabBlocks - FIXED!)

| Metric | Value |
|--------|-------|
| Entity Count | 232 LabBlocks |
| Duration | ~438 seconds (~7.3 minutes) |
| Final Score | **0hard/-100medium/0soft** |
| Status | **All assignments valid**, 232 LabBlocks pinned |

### Score Progression

| Time | Score | Notes |
|------|-------|-------|
| 1s | -54160hard | Initial CH placement |
| 10s | -49817hard | Rapid improvement |
| 30s | -24751hard | |
| 60s | -12215hard | |
| 80s | **0hard** | First 0hard achieved |
| 137s | 0hard/-138medium | Improved medium score |
| 438s | 0hard/-100medium | **Final** (terminated by unimproved limit) |

---

## Phase 1b Results (Lessons - IN PROGRESS)

| Metric | Value |
|--------|-------|
| Entity Count | 1147 Lessons |
| Pinned LabBlocks | 894 (from Phase 1a + prior pre-assignment) |
| Duration | Still running... |
| Current Score | -842hard/-1644medium/0soft (at 770s) |

### Score Progression (Ongoing)

| Time | Score | Improvement Rate |
|------|-------|------------------|
| 269s | -891hard | |
| 638s | -851hard | ~1 hard/10s |
| 754s | -846hard | ~1 hard/20s |
| 770s | -842hard | ~1 hard/30s |

**Progress is slowing down** - the solver is struggling to find improvements.

---

## Original Executive Summary

The Phase 1 pre-assignment solver has several configuration bottlenecks that are causing slow convergence. The primary issues are: (1) the Local Search phase uses `acceptedCountLimit=1` which is too greedy for escaping local optima, (2) no swap moves are configured, making resource conflict resolution extremely difficult, (3) the `lateAcceptanceSize=100` window is too small for adequate exploration, and (4) medium constraints add unnecessary score calculation overhead in a phase focused on hard feasibility. Below are 12 specific recommendations ordered by estimated impact.

---

## Current Configuration Analysis

### Phase 1 Entity Scale
- ~2038 planning entities (LabBlocks + Lessons)
- ~1520 timeslots (Mon-Fri, Sep-Jun, 6 slots/day)
- ~15-20 rooms
- 29 base constraints (sparse-compatible subset)

### Current LS Configuration (the bottleneck)
```xml
<acceptor>
  <entityTabuSize>5</entityTabuSize>
  <lateAcceptanceSize>100</lateAcceptanceSize>
</acceptor>
<forager>
  <acceptedCountLimit>1</acceptedCountLimit>
</forager>
```

### Known Violation Breakdown (from task-042 notes)
- H-M2 (room suitability): **184 of 310** hard penalty (59%)
- Remaining: teacher/room/group conflicts, stage blackout, etc.

---

## Recommendations

### HIGH IMPACT

#### 1. Add Swap Moves to Local Search
**Problem**: The current LS only has `changeMoveSelector` and `cartesianProductMoveSelector`. No `swapMoveSelector`. This is the single biggest gap.

**Why it matters**: Swap moves are essential for resolving resource conflicts (H1, H2, H3). When teacher A and teacher B both need the same timeslot, a change move can only move one of them to a random different slot — which often creates a new conflict. A swap move exchanges their assignments atomically, resolving one conflict without creating another.

Timefold docs explicitly state: *"Although a SwapMove on a single variable is essentially just two ChangeMoves, it's often the winning step in cases where the first of the two ChangeMoves would not win because it leaves the solution in a state with broken hard constraints."*

**Recommendation**:
```xml
<!-- Add to the unionMoveSelector in LS -->
<swapMoveSelector>
  <fixedProbabilityWeight>3.0</fixedProbabilityWeight>
  <entitySelector>
    <entityClass>com.school.planner.domain.LabBlock</entityClass>
  </entitySelector>
</swapMoveSelector>
<swapMoveSelector>
  <fixedProbabilityWeight>3.0</fixedProbabilityWeight>
  <entitySelector>
    <entityClass>com.school.planner.domain.Lesson</entityClass>
  </entitySelector>
</swapMoveSelector>
```

**Confidence**: Very High — swap moves are standard practice for timetabling and the main solver (Phase 4 in solverConfig.xml) already uses them successfully.

**LabBlock continuity safety**: Swap moves do NOT break LabBlock internal timeslot continuity. This is because:

1. **SwapMove operates on planning variables, not on individual timeslots.** A LabBlock's `occupiedTimeslots` are a `@ShadowVariable` computed by `OccupiedTimeslotsListener` from the single `startTimeslot` planning variable + the fixed `blockSize` field. When two LabBlocks swap, only `startTimeslot` and `room` are exchanged — `blockSize` stays with its entity. The `OccupiedTimeslotsListener.afterVariableChanged()` then recomputes consecutive slots from the new `startTimeslot`, preserving contiguity.

2. **Type separation prevents cross-entity swaps.** The config specifies separate `<swapMoveSelector>` elements per entity class (LabBlock-LabBlock, Lesson-Lesson). No LabBlock-Lesson swaps occur. Even if they did, the timeslot variable names differ (`startTimeslot` vs `timeslot`), so only the shared `room` variable would be swapped.

3. **H9 catches invalid placements.** If a swap moves a LabBlock (blockSize=3) to a start position where only 2 consecutive slots exist (e.g., slot 5 of 6), constraint H9 penalizes the shortfall: `blockSize - occupiedTimeslots.size()`. The solver treats this as a hard violation and works to fix it in subsequent steps.

---

#### 2. Increase `acceptedCountLimit` from 1 to 4-8
**Problem**: `acceptedCountLimit=1` means the solver takes the very first accepted move without evaluating alternatives. Combined with Late Acceptance, this creates a "greedy first-hit" behavior that gets stuck in local optima.

**Why it matters**: With 29 constraints creating a complex score landscape, the solver needs to evaluate more moves per step to find genuinely productive ones. The Timefold docs recommend `acceptedCountLimit=1` for pure Simulated Annealing but `acceptedCountLimit=1000` for pure Tabu Search. For Late Acceptance + Tabu hybrids, values of 4-8 balance speed with quality.

**Recommendation**:
```xml
<forager>
  <acceptedCountLimit>4</acceptedCountLimit>
</forager>
```

**Confidence**: High — this is a standard Timefold tuning parameter. Benchmark values 1, 4, 8 to find the sweet spot.

---

#### 3. Increase `lateAcceptanceSize` from 100 to 400
**Problem**: `lateAcceptanceSize=100` means the solver compares against the score from 100 steps ago. With fast steps (due to `acceptedCountLimit=1`), this window is too short to allow meaningful deterioration and recovery.

**Why it matters**: A larger window lets the solver accept more worsening moves, enabling it to escape deep local optima. The main solver already uses 200 for Phase 3. For Phase 1 where the problem is highly constrained and the solver is stuck at -35 hard, a larger window (400) gives more "runway" to explore.

**Recommendation**:
```xml
<acceptor>
  <entityTabuSize>5</entityTabuSize>
  <lateAcceptanceSize>400</lateAcceptanceSize>
</acceptor>
```

**Confidence**: High — Timefold docs recommend 400 as a standard value for Late Acceptance configurations.

---

#### 4. Remove Medium Constraints from Phase 1
**Problem**: Phase 1's goal is hard feasibility (0 hard violations). The 5 medium constraints (M3.1, M3.2, M5, M12-L, M12-B) add score calculation overhead without helping achieve that goal.

**Why it matters**: Each medium constraint adds to the Constraint Streams node network, increasing per-move evaluation cost. M5 (Informatica after stage) involves a join between Lessons and StagePatterns. M12 involves date calculations. None of these help the solver find hard-feasible placements faster. Worse, the medium score level can mislead the solver into preferring a move that improves medium score at the expense of hard score progress.

**Recommendation**: Modify `BaseConstraintProvider.getBaseConstraints()` to accept a `preAssignmentMode` parameter and exclude medium constraints, OR create a separate `getPhase1Constraints()` method that returns only hard constraints.

Alternatively, use Timefold's constraint weight overrides to set medium constraint weights to zero in Phase 1:
```java
// In TwoPhasePreAssignmentService, before creating solver:
// Use ConstraintConfiguration to zero out medium constraints
```

**Confidence**: High — fewer constraints = faster score calculation = more moves per second = faster convergence on hard score.

---

#### 5. Add Simulated Annealing as Alternative/Hybrid
**Problem**: Late Acceptance can get stuck when the score landscape has many plateaus (which is common in timetabling). Simulated Annealing's temperature-based acceptance gives a more gradual transition from exploration to exploitation.

**Why it matters**: At -35 hard with ~2000 entities, the solver is likely in a region where many single-entity changes are score-neutral or slightly worsening. SA's probabilistic acceptance of worsening moves (calibrated by temperature) can escape these plateaus better than LA's fixed-window comparison.

**Recommendation A** — Try pure SA + small Tabu:
```xml
<acceptor>
  <entityTabuSize>5</entityTabuSize>
  <simulatedAnnealingStartingTemperature>2hard/0medium/0soft</simulatedAnnealingStartingTemperature>
</acceptor>
<forager>
  <acceptedCountLimit>4</acceptedCountLimit>
</forager>
```

**Recommendation B** — Try SA + LA hybrid:
```xml
<acceptor>
  <entityTabuSize>5</entityTabuSize>
  <simulatedAnnealingStartingTemperature>1hard/0medium/0soft</simulatedAnnealingStartingTemperature>
  <lateAcceptanceSize>200</lateAcceptanceSize>
</acceptor>
<forager>
  <acceptedCountLimit>4</acceptedCountLimit>
</forager>
```

The starting temperature `2hard` means the solver will initially accept moves that worsen the score by up to 2 hard points. This decays over time.

**Confidence**: Medium-High — SA is a well-proven metaheuristic for timetabling. Requires benchmarking to find the right temperature.

---

### MEDIUM IMPACT

#### 6. Add Pillar Moves for Multi-Entity Conflict Resolution
**Problem**: Change moves and swaps operate on individual entities. When a teacher has 3+ conflicting lessons in the same timeslot, resolving the conflict requires 2+ sequential moves — each of which may temporarily worsen the score.

**Why it matters**: Pillar moves can move multiple entities that share a property (e.g., same teacher) to a new value simultaneously. This is a coarse-grained move that can resolve multi-entity conflicts in one step.

**Recommendation**:
```xml
<pillarChangeMoveSelector>
  <fixedProbabilityWeight>2.0</fixedProbabilityWeight>
  <pillarSelector>
    <entitySelector>
      <entityClass>com.school.planner.domain.Lesson</entityClass>
    </entitySelector>
    <minimumSubPillarSize>2</minimumSubPillarSize>
    <maximumSubPillarSize>4</maximumSubPillarSize>
  </pillarSelector>
  <valueSelector variableName="timeslot"/>
</pillarChangeMoveSelector>
```

**Confidence**: Medium — pillar moves are already used in the main solver's Phase 4. Worth adding to Phase 1 LS.

---

#### 7. Increase `unimprovedSecondsSpentLimit` from 60 to 180-300
**Problem**: 60 seconds of no improvement triggers LS termination. With 2038 entities and 29 constraints, the solver may need longer plateaus to find improving moves.

**Why it matters**: If the solver reaches -35 hard and then plateaus, it only has 60 seconds before terminating. With better move selectors (swaps, pillars), 60s might be enough, but given the complexity, 180-300s is safer.

**Recommendation**:
```xml
<termination>
  <unimprovedSecondsSpentLimit>180</unimprovedSecondsSpentLimit>
</termination>
```

**Confidence**: Medium — this is a safety net, not the root cause. The other changes should make the solver converge faster within any timeout.

---

#### 8. Tune Construction Heuristic for Better Initial Solution

**Problem**: The CH places LabBlocks first (good — harder to fit), then Lessons. But it uses sequential variable assignment (timeslot, then room separately). For timetabling, Timefold docs note that *cartesian product* of timeslot+room in the CH produces better results, albeit slower.

**Why it matters**: A better initial solution from the CH means the LS starts from a lower hard score, requiring less time to reach feasibility.

**Recommendation A** — Add `DECREASING_DIFFICULTY` sorting to lesson placer too:
```xml
<constructionHeuristic>
  <queuedEntityPlacer>
    <entitySelector id="lessonPlacer">
      <entityClass>com.school.planner.domain.Lesson</entityClass>
      <cacheType>PHASE</cacheType>
      <sorterManner>DECREASING_DIFFICULTY</sorterManner>  <!-- ADD THIS -->
    </entitySelector>
    ...
  </queuedEntityPlacer>
</constructionHeuristic>
```

**Recommendation B** — Use cartesian product in CH for better quality (at cost of CH speed):
```xml
<constructionHeuristic>
  <queuedEntityPlacer>
    <entitySelector id="labBlockPlacer">
      <entityClass>com.school.planner.domain.LabBlock</entityClass>
      <cacheType>PHASE</cacheType>
      <sorterManner>DECREASING_DIFFICULTY</sorterManner>
    </entitySelector>
    <cartesianProductMoveSelector>
      <changeMoveSelector>
        <entitySelector mimicSelectorRef="labBlockPlacer"/>
        <valueSelector variableName="startTimeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <entitySelector mimicSelectorRef="labBlockPlacer"/>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
    </cartesianProductMoveSelector>
  </queuedEntityPlacer>
</constructionHeuristic>
```

**Caveat**: With 1520 timeslots × 20 rooms = 30,400 moves per LabBlock entity in cartesian mode. With ~200 LabBlocks, that's ~6M moves in CH Phase 1 alone. This could make CH significantly slower. Sequential (current) is 1520 + 20 = 1540 moves per entity. Trade-off: better initial score vs. longer CH time.

**Confidence**: Medium — Timefold docs say cartesian "has far better results (especially for timetabling use cases)" but the 20x move increase per entity is significant. Benchmark both.

---

#### 9. Weight Move Selectors by Probability
**Problem**: All move selectors in the Phase 1 LS have equal probability weight (default 1.0). The solver spends equal time on timeslot changes, room changes, and cartesian moves.

**Why it matters**: Since H-M2 (room suitability) accounts for 59% of hard violations, room moves should have higher weight. Conversely, timeslot changes are important for H1/H2/H3 conflicts but less dominant.

**Recommendation**:
```xml
<unionMoveSelector>
  <!-- Timeslot changes (baseline) -->
  <changeMoveSelector>
    <fixedProbabilityWeight>2.0</fixedProbabilityWeight>
    <!-- LabBlock timeslot -->
  </changeMoveSelector>
  <changeMoveSelector>
    <fixedProbabilityWeight>2.0</fixedProbabilityWeight>
    <!-- Lesson timeslot -->
  </changeMoveSelector>
  <!-- Room changes (HIGHER weight for H-M2) -->
  <changeMoveSelector>
    <fixedProbabilityWeight>4.0</fixedProbabilityWeight>
    <!-- LabBlock room -->
  </changeMoveSelector>
  <changeMoveSelector>
    <fixedProbabilityWeight>4.0</fixedProbabilityWeight>
    <!-- Lesson room -->
  </changeMoveSelector>
  <!-- Cartesian moves -->
  <cartesianProductMoveSelector>
    <fixedProbabilityWeight>3.0</fixedProbabilityWeight>
    <!-- ... -->
  </cartesianProductMoveSelector>
  <!-- Swap moves -->
  <swapMoveSelector>
    <fixedProbabilityWeight>3.0</fixedProbabilityWeight>
    <!-- ... -->
  </swapMoveSelector>
</unionMoveSelector>
```

**Confidence**: Medium — the 59% H-M2 dominance suggests room moves should be prioritized. But after fixing the swap/acceptedCount issues, the violation distribution may change.

---

### LOWER IMPACT (but worth investigating)

#### 10. Entity-Level Value Range Filtering for Phase 1
**Problem**: Each Lesson/LabBlock currently considers all ~1520 timeslots. For pre-assignment entities that match LabRotation records, the valid timeslots are far more restricted (specific day-of-week, specific period ranges).

**Why it matters**: If a LabRotation specifies "Thursdays in Period 1 (Oct-Dec)", the entity only has ~13 valid timeslot values (one per Thursday in that range) instead of 1520. This massively reduces the search space per entity.

**Recommendation**: Enhance `getAvailableTimeslots()` / `getAvailableStartTimeslots()` to filter based on LabRotation metadata when in pre-assignment mode. This requires passing rotation context to entities, which is an architectural change.

**Alternative**: Add `SelectionFilter` classes that filter timeslot values based on day-of-week and period constraints. Less efficient than entity-level VRP but doesn't require entity changes.

**Confidence**: Medium — high theoretical impact but requires significant implementation work.

---

#### 11. Consider Ruin & Recreate Move Selector
**Problem**: When the solver is stuck at -35 hard, fine-grained moves (change, swap) may not be sufficient to escape the local optimum. A group of 5-10 interrelated entities may all need to be reassigned simultaneously.

**Recommendation**:
```xml
<ruinRecreateMoveSelector>
  <fixedProbabilityWeight>0.1</fixedProbabilityWeight>
  <minimumRuinedCount>3</minimumRuinedCount>
  <maximumRuinedCount>10</maximumRuinedCount>
</ruinRecreateMoveSelector>
```

**Caveat**: Ruin & Recreate is expensive. The `fixedProbabilityWeight=0.1` ensures it only runs ~1 in every 10 steps. Timefold docs warn this can slow the solver significantly.

**Confidence**: Medium-Low — worth trying if recommendations 1-5 don't suffice.

---

#### 12. Score Trap Audit on Key Constraints
**Problem**: Some constraints may create score traps where multiple solutions have identical scores, preventing the solver from distinguishing better from worse.

**Constraints to audit**:
- **H4 (Stage blackout)**: Penalizes `ONE_HARD` per match. If a lesson overlaps multiple stage patterns, it gets multiple penalties. This is correct (proportional).
- **H-D1 (Doppio teacher conflict)**: Penalizes `ONE_HARD` per match. Could be a score trap if a doppio teacher has 3 conflicting lessons — moving any one doesn't help until 2 are moved.
- **H6 (School holiday)**: Penalizes `ONE_HARD` per entity on a holiday. Correct (proportional).

**Recommendation**: Verify that all `penalize(ONE_HARD)` calls in base constraints use proportional weights where applicable. The B-B variants already do this (`penalize(ONE_HARD, count_of_overlapping_slots)`), but L-L and L-B variants penalize 1 per pair which is correct for pairwise conflicts.

**Confidence**: Low — the current constraints appear correctly proportional. But worth a systematic audit if convergence remains slow after other changes.

---

## Proposed Phase 1 Config (Combining Recommendations 1-5, 7, 9)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<solver xmlns="https://timefold.ai/xsd/solver">

  <solutionClass>com.school.planner.domain.SchoolSchedule</solutionClass>
  <entityClass>com.school.planner.domain.LabBlock</entityClass>
  <entityClass>com.school.planner.domain.Lesson</entityClass>

  <scoreDirectorFactory>
    <constraintProviderClass>com.school.planner.solver.SchoolScheduleConstraintProvider</constraintProviderClass>
  </scoreDirectorFactory>

  <termination>
    <secondsSpentLimit>1800</secondsSpentLimit>
  </termination>

  <!-- CH Phase 1: Place LabBlocks first (harder to fit) -->
  <constructionHeuristic>
    <queuedEntityPlacer>
      <entitySelector id="labBlockPlacer">
        <entityClass>com.school.planner.domain.LabBlock</entityClass>
        <cacheType>PHASE</cacheType>
        <sorterManner>DECREASING_DIFFICULTY</sorterManner>
      </entitySelector>
      <changeMoveSelector>
        <entitySelector mimicSelectorRef="labBlockPlacer"/>
        <valueSelector variableName="startTimeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <entitySelector mimicSelectorRef="labBlockPlacer"/>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
    </queuedEntityPlacer>
  </constructionHeuristic>

  <!-- CH Phase 2: Place Lessons -->
  <constructionHeuristic>
    <queuedEntityPlacer>
      <entitySelector id="lessonPlacer">
        <entityClass>com.school.planner.domain.Lesson</entityClass>
        <cacheType>PHASE</cacheType>
        <sorterManner>DECREASING_DIFFICULTY</sorterManner>
      </entitySelector>
      <changeMoveSelector>
        <entitySelector mimicSelectorRef="lessonPlacer"/>
        <valueSelector variableName="timeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <entitySelector mimicSelectorRef="lessonPlacer"/>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
    </queuedEntityPlacer>
  </constructionHeuristic>

  <!-- Local Search: Conflict resolution with diverse moves -->
  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>180</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <!-- Timeslot changes (2x weight) -->
      <changeMoveSelector>
        <fixedProbabilityWeight>2.0</fixedProbabilityWeight>
        <entitySelector>
          <entityClass>com.school.planner.domain.LabBlock</entityClass>
        </entitySelector>
        <valueSelector variableName="startTimeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <fixedProbabilityWeight>2.0</fixedProbabilityWeight>
        <entitySelector>
          <entityClass>com.school.planner.domain.Lesson</entityClass>
        </entitySelector>
        <valueSelector variableName="timeslot"/>
      </changeMoveSelector>
      <!-- Room changes (4x weight — H-M2 is dominant violation) -->
      <changeMoveSelector>
        <fixedProbabilityWeight>4.0</fixedProbabilityWeight>
        <entitySelector>
          <entityClass>com.school.planner.domain.LabBlock</entityClass>
        </entitySelector>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <fixedProbabilityWeight>4.0</fixedProbabilityWeight>
        <entitySelector>
          <entityClass>com.school.planner.domain.Lesson</entityClass>
        </entitySelector>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
      <!-- Swap moves (3x weight — critical for conflict resolution) -->
      <swapMoveSelector>
        <fixedProbabilityWeight>3.0</fixedProbabilityWeight>
        <entitySelector>
          <entityClass>com.school.planner.domain.LabBlock</entityClass>
        </entitySelector>
      </swapMoveSelector>
      <swapMoveSelector>
        <fixedProbabilityWeight>3.0</fixedProbabilityWeight>
        <entitySelector>
          <entityClass>com.school.planner.domain.Lesson</entityClass>
        </entitySelector>
      </swapMoveSelector>
      <!-- Cartesian moves for atomic timeslot+room changes (2x weight) -->
      <cartesianProductMoveSelector>
        <fixedProbabilityWeight>2.0</fixedProbabilityWeight>
        <changeMoveSelector>
          <entitySelector id="cartesianLabBlock">
            <entityClass>com.school.planner.domain.LabBlock</entityClass>
          </entitySelector>
          <valueSelector variableName="startTimeslot"/>
        </changeMoveSelector>
        <changeMoveSelector>
          <entitySelector mimicSelectorRef="cartesianLabBlock"/>
          <valueSelector variableName="room"/>
        </changeMoveSelector>
      </cartesianProductMoveSelector>
      <cartesianProductMoveSelector>
        <fixedProbabilityWeight>2.0</fixedProbabilityWeight>
        <changeMoveSelector>
          <entitySelector id="cartesianLesson">
            <entityClass>com.school.planner.domain.Lesson</entityClass>
          </entitySelector>
          <valueSelector variableName="timeslot"/>
        </changeMoveSelector>
        <changeMoveSelector>
          <entitySelector mimicSelectorRef="cartesianLesson"/>
          <valueSelector variableName="room"/>
        </changeMoveSelector>
      </cartesianProductMoveSelector>
      <!-- Pillar moves for multi-entity resolution (1x weight) -->
      <pillarChangeMoveSelector>
        <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
        <pillarSelector>
          <entitySelector>
            <entityClass>com.school.planner.domain.Lesson</entityClass>
          </entitySelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>4</maximumSubPillarSize>
        </pillarSelector>
        <valueSelector variableName="timeslot"/>
      </pillarChangeMoveSelector>
    </unionMoveSelector>
    <acceptor>
      <entityTabuSize>7</entityTabuSize>
      <lateAcceptanceSize>400</lateAcceptanceSize>
    </acceptor>
    <forager>
      <acceptedCountLimit>4</acceptedCountLimit>
    </forager>
  </localSearch>
</solver>
```

---

## Benchmarking Strategy

To validate these changes, run a structured comparison:

1. **Baseline**: Current `preAssignmentSolverConfig.xml` (no changes)
2. **Config A**: Add swap moves only (recommendation 1)
3. **Config B**: Config A + acceptedCountLimit=4 + lateAcceptanceSize=400 (recommendations 1-3)
4. **Config C**: Config B + weighted move selectors (recommendations 1-3, 9)
5. **Config D**: Config B + Simulated Annealing instead of LA (recommendation 5)
6. **Config E**: Full proposed config above (recommendations 1-7, 9)

For each config, run 3x with 10-minute timeout and compare:
- Hard score at 1 min, 3 min, 5 min, 10 min
- Construction heuristic time
- Move evaluation speed (moves/sec)
- Time to reach -20 hard, -10 hard, 0 hard

---

## Constraint-Level Recommendations

### Remove Medium Constraints from Phase 1
Modify `BaseConstraintProvider` to optionally exclude medium constraints:

```java
protected Constraint[] getBaseConstraints(ConstraintFactory constraintFactory) {
    List<Constraint> constraints = new ArrayList<>();
    // ... add all hard constraints ...

    if (!SchoolScheduleConstraintProvider.isPreAssignmentMode()) {
        // Only add medium constraints for full solving
        constraints.add(roomCapacity(constraintFactory));
        constraints.add(roomCapacityBlock(constraintFactory));
        constraints.add(rewardInformaticaAfterStage(constraintFactory));
        constraints.add(overflowBeyondTargetEndDate(constraintFactory));
        constraints.add(overflowBeyondTargetEndDateBlock(constraintFactory));
    }

    return constraints.toArray(new Constraint[0]);
}
```

This removes 5 medium constraints from Phase 1, reducing the constraint stream node network and speeding up each move evaluation.

### Consider Proportional Penalization for B-B Conflicts
The B-B conflict constraints (H1.3, H2.3, H3.3) already use proportional penalties:
```java
.penalize(HardMediumSoftScore.ONE_HARD,
    (a, b) -> (int) a.getOccupiedTimeslots().stream()
            .filter(b::occupiesTimeslot).count())
```

The L-B constraints (H1.2, H2.2, H3.2) penalize ONE_HARD per match, which is correct since a lesson occupies exactly one timeslot.

**No score trap issues identified** in the current constraint design.

---

## Sources

- [Timefold Solver: Construction Heuristics](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/construction-heuristics)
- [Timefold Solver: Local Search](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/local-search)
- [Timefold Solver: Move Selector Reference](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference)
- [Timefold Solver: Performance Tips](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance)
- [Timefold Solver: Benchmarking and Tweaking](https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/benchmarking-and-tweaking)
- [Timefold GitHub Discussion #951: Escaping Local Optima](https://github.com/TimefoldAI/timefold-solver/discussions/951)
- [Timefold Solver: Enterprise Edition (Nearby Selection, Multi-threaded)](https://docs.timefold.ai/timefold-solver/latest/enterprise-edition/enterprise-edition)
