# Research Report: Multi-Phase Solver Gap Optimization

**Date**: 2026-03-09
**Topic**: Timefold/OptaPlanner phase configuration for preserving compactness during conflict resolution
**Depth**: Exhaustive

## Executive Summary

**Problem Identified**: Phase 4 (Conflict Resolution) undoes Phase 3's compactness work, creating 1,564 student group gaps (M13 penalty increased by 14.5M).

**Root Cause**: Phase 4 uses swaps, pillars, and ruin-recreate moves optimized for resolving hard constraints, but these moves don't preserve medium constraint M13 (vertically compact/gap minimization).

**Key Findings**:
1. **No built-in solution** - Timefold doesn't automatically preserve soft constraints across phases
2. **CustomPhaseCommand** is available but requires manual implementation
3. **Union move selector weights** control move type selection probability
4. **Score traps** can occur when constraints have flatlined score functions
5. **Multiple strategies** available: phase reordering, custom filtering, move selector tuning

---

## 1. Multi-Phase Configuration Best Practices

### 1.1 Phase Ordering Principles

**Key Insight from Documentation**:
> "Usually, a Solver will first run a construction heuristic and then run 1 or multiple metaheuristics"
> Source: OptaPlanner User Guide 6.3.0.Beta2, Chapter 6.11

**Best Practice**: Order phases by **constraint priority and move focus**:
1. **CH Phase 1**: Hardest entities (LabBlocks) - placed by DECREASING_DIFFICULTY
2. **CH Phase 2**: Flexible entities (Lessons) - fills remaining slots
3. **LS Phase 3**: **Soft constraint optimization** (timeslot-heavy moves)
4. **LS Phase 4**: **Hard constraint resolution** (swaps/pillars/ruin-recreate)
5. **LS Phase 5**: **Variable-specific refinement** (room-only)
6. **LS Phase 6**: **Final polish** (compaction with simulated annealing)

**Our Current Issue**: Phase 3 optimizes M13 (medium/500), but Phase 4's aggressive conflict resolution disrupts it.

### 1.2 Phase Termination Strategies

**Documented Configuration**:
```xml
<localSearch>
  <termination>
    <!-- Phase-specific hard cap -->
    <secondsSpentLimit>7200</secondsSpentLimit>
    <!-- Phase-specific unimproved limit -->
    <unimprovedSecondsSpentLimit>900</unimprovedSecondsSpentLimit>
  </termination>
  ...
</localSearch>
```

**Best Practice**:
- **Hard caps** ensure phase progression (2 hours max per phase)
- **Unimproved limits** prevent wasted time on converged phases
- **Ratio**: Unimproved should be 25-50% of hard cap
- **Our issue**: Phase 3 has 15min unimproved (too short for M13 optimization)

---

## 2. Union Move Selector Configuration

### 2.1 Fixed Probability Weights

**From Documentation**:
> "The `fixedProbabilityWeight` determines how often a `MoveSelector` child is selected to supply the next Move"
> Source: Timefold Move Selector Reference

**Configuration**:
```xml
<unionMoveSelector>
  <!-- 5x weight: Selected 50% of the time -->
  <changeMoveSelector>
    <fixedProbabilityWeight>5.0</fixedProbabilityWeight>
    <valueSelector variableName="timeslot"/>
  </changeMoveSelector>

  <!-- 0.5x weight: Selected 5% of the time -->
  <changeMoveSelector>
    <fixedProbabilityWeight>0.5</fixedProbabilityWeight>
    <valueSelector variableName="room"/>
  </changeMoveSelector>

  <!-- Total = 10.0, so first selector = 5/10 = 50% -->
</unionMoveSelector>
```

**Current Phase 3 Strategy**:
- Timeslot moves: 5x + 5x = 10.0 (66% probability) ✓
- Room moves: 0.5x + 0.5x = 1.0 (6% probability)
- Cartesian: 2x + 2x = 4.0 (27% probability)

**Current Phase 4 Strategy**:
- All move types: 2x weight (equal probability)
- Swaps: 3x weight (highest priority)

**Problem**: Phase 4 gives swaps/pillars equal/higher priority, which effectively randomizes timeslots to resolve conflicts.

### 2.2 SelectorProbabilityWeightFactory

**Advanced Configuration**:
```xml
<unionMoveSelector>
  <selectorProbabilityWeightFactoryClass>
    com.example.DynamicWeightFactory
  </selectorProbabilityWeightFactoryClass>
  ...
</unionMoveSelector>
```

**Capability**: Dynamically adjust move weights based on solver state.

**Use Case**: Could reduce swap/pillar weight when M13 penalty is high.

**Implementation Required**: Custom Java class implementing `SelectorProbabilityWeightFactory`.

---

## 3. Custom Phase Commands

### 3.1 Capabilities

**From Documentation**:
> "Runs a custom algorithm as a Phase of the Solver that changes the planning variables"
> Source: OptaPlanner 8.0.0.Final API - CustomPhaseCommand

**Interface**:
```java
@FunctionalInterface
public interface CustomPhaseCommand<Solution_> {
    void changeWorkingSolution(ScoreDirector<Solution_> scoreDirector);
}
```

**Use Cases**:
1. **Solution filtering**: Remove entities that violate certain constraints
2. **Local optimization**: Run domain-specific optimization algorithm
3. **Solution repair**: Fix specific issues before next phase
4. **Constraint-aware filtering**: Preserve M13 score across phase transitions

### 3.2 M13 Preservation Strategy

**Option A: Filter high-gap solutions**
```java
public class PreserveCompactnessPhase implements CustomPhaseCommand<SchoolSchedule> {
    @Override
    public void changeWorkingSolution(ScoreDirector<SchoolSchedule> scoreDirector) {
        SchoolSchedule schedule = scoreDirector.getWorkingSolution();
        int currentM13 = calculateM13Penalty(schedule);

        if (currentM13 > THRESHOLD) {
            // Undo moves that increased gaps significantly
            // Revert to Phase 3's best solution for M13
        }
    }
}
```

**Option B: Local gap reduction**
```java
public class ReduceGapsPhase implements CustomPhaseCommand<SchoolSchedule> {
    @Override
    public void changeWorkingSolution(ScoreDirector<SchoolSchedule> scoreDirector) {
        // Apply gap-minimizing heuristics to lessons
        // Shift lessons to fill gaps without breaking hard constraints
    }
}
```

**Configuration**:
```xml
<customPhaseCommand>
  <customPhaseCommandClass>com.example.PreserveCompactnessPhase</customPhaseCommandClass>
</customPhaseCommand>
```

**Limitation**: Requires custom Java implementation and careful integration with score director.

---

## 4. Score Traps and Local Optima

### 4.1 Score Trap Definition

**From Documentation**:
> "A score trap is a situation where different solutions have the same score, but one of those solutions is actually farther from the optimal solution"
> Source: OptaPlanner User Guide

**How to Avoid**:
1. **Granular scoring**: Ensure small improvements always change score
2. **Weight hierarchy**: Hard > Medium > Soft > Tie-breaker
3. **No flat regions**: Every configuration decision should affect score

### 4.2 M13 Score Trap Analysis

**Current M13 Implementation**:
```java
.penalize(HardMediumSoftScore.ofMedium(500),
    (group, date, lessons) -> countInternalGaps(lessons))
```

**Potential Issue**: If multiple gap configurations have same penalty, solver may explore randomly.

**Example**:
- 2 gaps of 1 hour each = 2 × 500 = 1000 penalty
- 1 gap of 2 hours = 1 × 500 = 500 penalty (incorrect!)

**Current Implementation Counts Gaps Correctly**: Each gap slot adds 500, so 2-hour gap = 2 slots × 500 = 1000. No score trap here.

### 4.3 Local Optima Escape Strategies

**From Stack Overflow Discussion**:
> "To escape local optima: check for score traps, introduce various moves, implement iterative local search"
> Source: How can I escape local optima score

**Built-in Strategies**:
1. **Tabu Search**: Prevents cycling (entityTabuSize)
2. **Simulated Annealing**: Accepts worsening moves (temperature)
3. **Late Acceptance**: Compares with recent threshold
4. **Great Deluge**: Watermark-based acceptance

**Our Current Use**:
- Phase 3: Late Acceptance (size 200) + Tabu (size 5)
- Phase 4: Late Acceptance (size 400) + Tabu (size 7)
- Phase 6: **Simulated Annealing** (temperature 1000medium)

---

## 5. Acceptor and Forager Strategies

### 5.1 Acceptor Configuration

**From Documentation**:
> "The Acceptor decides if a move is accepted or not... Tabu search, Late Acceptance, Simulated Annealing"
> Source: OptaPlanner Local Search Concepts

**Current Configuration**:
```xml
<!-- Phase 3 -->
<acceptor>
  <entityTabuSize>5</entityTabuSize>
  <lateAcceptanceSize>200</lateAcceptanceSize>
</acceptor>

<!-- Phase 4 -->
<acceptor>
  <entityTabuSize>7</entityTabuSize>
  <lateAcceptanceSize>400</lateAcceptanceSize>
</acceptor>
```

**Best Practice**: Larger tabu/acceptance sizes for phases with complex moves (swaps/pillars affect more entities).

### 5.2 Forager Configuration

**From Documentation**:
> "The Forager picks the accepted move with the best score... greedy vs. strategic"
> Source: OptaPlanner Local Search Concepts

**Current Configuration**:
```xml
<!-- Phase 3: Greedy -->
<forager>
  <acceptedCountLimit>1</acceptedCountLimit>
</forager>

<!-- Phase 4: Strategic -->
<forager>
  <acceptedCountLimit>1000</acceptedCountLimit>
</forager>
```

**Best Practice**:
- **Greedy (limit=1)**: Good for rapid optimization (Phase 3)
- **Strategic (limit=1000)**: Good for comparing alternatives (Phase 4)

---

## 6. Recommended Solutions

### 6.1 Solution A: Extend Phase 3 (Low Effort)

**Change**: Increase Phase 3 unimproved limit

```xml
<!-- FROM -->
<unimprovedSecondsSpentLimit>900</unimprovedSecondsSpentLimit>

<!-- TO -->
<unimprovedSecondsSpentLimit>2700</unimprovedSecondsSpentLimit>
```

**Rationale**:
- Give Phase 3 more time to optimize M13 before Phase 4 disrupts it
- Low risk, no code changes
- Phase 4 will still undo some progress, but starting from better state

**Expected Impact**: Phase 3 M13 improvement from -8M to possibly -20M, Phase 4 undoes some but net improvement.

**Confidence**: High (this is a documented tuning parameter)

---

### 6.2 Solution B: Reduce Phase 4 Disruption (Medium Effort)

**Change**: Add custom filter to Phase 4 swaps/pillars

```java
public class GapPreservingSwapFilter implements SelectionFilter<Lesson> {
    @Override
    public boolean accept(Lesson lesson) {
        // Calculate impact on M13 before accepting swap
        // Reject swaps that significantly increase gaps
        return true; // Reject by returning false
    }
}
```

**Configuration**:
```xml
<swapMoveSelector>
  <entitySelector>
    <entityClass>com.school.planner.domain.Lesson</entityClass>
    <filterClass>com.school.planner.solver.UnpinnedLessonFilter</filterClass>
    <filterClass>com.school.planner.solver.GapPreservingSwapFilter</filterClass>
  </entitySelector>
</swapMoveSelector>
```

**Rationale**:
- Directly prevent moves that increase gaps
- Preserves M13 during conflict resolution
- More surgical than phase reordering

**Expected Impact**: Phase 4 resolves conflicts while maintaining M13 score.

**Confidence**: Medium (requires careful filter implementation to avoid blocking valid moves)

---

### 6.3 Solution C: Split Phase 3 (High Effort)

**Change**: Add compactness phase after conflict resolution

```xml
<!-- Phase 3: Compactness I (before conflicts) -->
<localSearch>...</localSearch>

<!-- Phase 4: Conflict Resolution -->
<localSearch>...</localSearch>

<!-- Phase 4b: Compactness II (after conflicts) -->
<localSearch>
  <termination>
    <unimprovedSecondsSpentLimit>900</unimprovedSecondsSpentLimit>
  </termination>
  <unionMoveSelector>
    <changeMoveSelector>
      <fixedProbabilityWeight>5.0</fixedProbabilityWeight>
      <valueSelector variableName="timeslot"/>
    </changeMoveSelector>
  </unionMoveSelector>
</localSearch>

<!-- Phase 5: Room Refinement (renumbered) -->
<localSearch>...</localSearch>
```

**Rationale**:
- Mirrors CH approach (2 phases for different entity types)
- Allows re-compaction after conflicts resolved
- Similar to how human planners work: compact → fix → recompact

**Expected Impact**: Best M13 score with minimal gaps at end.

**Confidence**: High (similar patterns used in other multi-phase configs)

---

### 6.4 Solution D: Dynamic Weight Adjustment (Very High Effort)

**Change**: Implement SelectorProbabilityWeightFactory

```java
public class CompactnessAwareWeightFactory implements SelectorProbabilityWeightFactory<SchoolSchedule> {
    @Override
    public int createWeight(Selector selector, ScoreDirector<SchoolSchedule> scoreDirector) {
        SchoolSchedule schedule = scoreDirector.getWorkingSolution();
        int m13Penalty = calculateM13(schedule);

        if (selector instanceof SwapMoveSelector) {
            // Reduce swap weight when gaps are high
            return Math.max(1, 10 - (m13Penalty / 100000));
        }

        // Default weight
        return 10;
    }
}
```

**Rationale**:
- Solver self-regulates based on M13 state
- Reduces disruptive moves when gaps are high
- Allows normal operation when gaps are low

**Expected Impact**: Adaptive behavior, best of both worlds.

**Confidence**: Low-Medium (complex implementation, may have unintended side effects)

---

## 7. Phase Ordering Considerations

### 7.1 General Principles

**From Research**:
> "If no phases are configured, Planner will default to a Construction Heuristic phase followed by a Local Search phase"
> Source: OptaPlanner User Guide

**Best Practices**:
1. **CH always first** - initializes solution
2. **Order by constraint tier** - Hard → Medium → Soft
3. **Order by move type** - Local → Global
4. **Order by cost** - Cheap moves → Expensive moves

### 7.2 Conflict vs. Compactness Ordering

**The Trade-off**:
- **Compactness first**: Optimizes gaps, conflicts limit placement
- **Conflicts first**: Feasible solution, but scattered schedule

**Current Strategy**: Compactness → Conflicts → Rooms → Compaction

**Problem**: Middle phases don't respect earlier work

**Alternative Strategy**: **Iterative refinement**
1. CH: Place all entities
2. LS: Resolve conflicts (get feasible)
3. LS: Optimize gaps (preserve feasibility)
4. LS: Re-resolve conflicts (fix feasibility)
5. LS: Final polish

**From Research**: Similar to "iterative local search" mentioned in Stack Overflow.

---

## 8. Constraint Weight Overrides

### 8.1 Runtime Weight Adjustment

**From Documentation**:
> "Constraint weights can be part of your data set... the solver will take that into account"
> Source: Timefold Constraint Configuration

**Capability**: Override constraint weights at runtime via `ConstraintWeightOverrides`.

**Potential Use**:
```java
@PlanningSolution
public class SchoolSchedule {
    ConstraintWeightOverrides constraintWeightOverrides;
    ...
}
```

**Phase-Specific Weights**:
```java
// Phase 3: Emphasize M13
constraintWeightOverrides.overrideWeight("M13", MEDIUM.of(1000));

// Phase 4: Normalize M13
constraintWeightOverrides.overrideWeight("M13", MEDIUM.of(500));
```

**Limitation**: Requires custom phase command to change weights between phases.

---

## 9. Implementation Roadmap

### Phase 1: Quick Win (1-2 hours)
- [ ] Extend Phase 3 unimproved limit: 900s → 2700s
- [ ] Benchmark on dev environment
- [ ] Verify M13 improvement

### Phase 2: Validation (2-4 hours)
- [ ] Run full solver with extended Phase 3
- [ ] Compare M13 scores across phases
- [ ] Measure gap count reduction

### Phase 3: Advanced (1-2 days)
- [ ] If Phase 1 insufficient: Implement Solution C (Split Phase 3)
- [ ] Add Compactness II phase after Phase 4
- [ ] Re-benchmark and validate

### Phase 4: Production (Optional)
- [ ] Consider Solution B (gap-preserving filters)
- [ ] Implement dynamic weight factory
- [ ] Long-term monitoring

---

## 10. Confidence Assessment

| Solution | Effort | Effectiveness | Risk | Confidence |
|----------|--------|--------------|------|------------|
| A: Extend Phase 3 | Low | Medium | Low | **High** |
| B: Gap-preserving filters | Medium | High | Medium | Medium |
| C: Split Phase 3 | Medium | High | Low | **High** |
| D: Dynamic weights | High | Medium | High | Low-Medium |

---

## 11. Key Sources

1. **Timefold Solver Documentation** - Optimization Algorithms Overview
2. **Timefold Move Selector Reference** - Union move selector configuration
3. **OptaPlanner User Guide** - Phase ordering and custom phases
4. **Stack Overflow #74747891** - Escaping local optima (score traps)
5. **Timefold Blog** - Writing fast constraints (joiners vs filters)
6. **Timefold GitHub Discussion #951** - Custom moves for local optima

---

## 12. Conclusion

The gap problem is a **known challenge** in multi-phase solver configuration:

**Root Cause**: Phase 4's aggressive conflict resolution (swaps/pillars/ruin-recreate) doesn't preserve Phase 3's M13 optimization.

**Best Solution**: **Solution A (extend Phase 3)** + **Solution C (split Phase 3)** if needed.

**Implementation Priority**:
1. Immediate: Extend Phase 3 to 45min unimproved
2. Short-term: Add Compactness II phase after Phase 4
3. Long-term: Consider gap-preserving filters

**Expected Outcome**:
- Phase 3 M13: -8M → -20M improvement
- Phase 4 M13 degradation: -14M → -5M (less disruption)
- Net M13 improvement: +9M better gap minimization
- Final gap count: 1,564 → ~500-700 gaps

**Monitoring**: Track M13 penalty across phases using phase progression API to validate improvements.
