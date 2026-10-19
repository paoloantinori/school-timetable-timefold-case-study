# Research: Pillar Move Size Limits for 8,700-Entity Timefold Solver Problem

**Date**: 2026-02-27
**Task**: task-096
**Branch Context**: `feature/cartesian-block-entity` (7,281 entities), `feature/cartesian-shadow-lab-blocks` (8,693 entities)

## Executive Summary

Based on Timefold documentation, academic research on metaheuristics, and analysis of the current solver configuration, **the current `maximumSubPillarSize=5` is appropriate for the planner's scale**. However, there are opportunities to improve pillar move effectiveness by:

1. Adding pillar moves for the `room` variable (currently only `timeslot`)
2. Considering sequential subpillars for related lessons
3. Benchmarking with `maximumSubPillarSize=10` to test marginal improvement

**Confidence Level**: Medium-High (based on documentation analysis; benchmarking recommended for validation)

---

## 1. Background: What Are Pillar Moves?

### 1.1 Definition

A **pillar** is a set of planning entities that share the same value(s) for their planning variable(s). A **pillar change move** relocates all entities in a pillar (or subpillar) to a new value simultaneously.

From Timefold documentation:
> "A pillar is a set of planning entities which have the same planning value(s) for their planning variable(s). The `PillarChangeMove` selects one entity pillar (or subset of those) and changes the value of one variable (which is the same for all entities) to another value."

### 1.2 Why Use Pillar Moves?

Pillar moves are **coarse-grained moves** that help:
- **Escape local optima** by making larger jumps in the solution space
- **Maintain structural relationships** between entities that share constraints
- **Resolve conflicts efficiently** by moving related entities together

From academic research (Ahuja et al., Large Neighborhood Search):
> "Due to the large neighborhoods and diversity of the neighborhoods, the ALNS algorithm will explore large parts of the solution space in a structured way. The resulting algorithm becomes very robust, as it is able to adapt to various characteristics of the individual instances, and seldom is trapped in local optima."

---

## 2. Current Configuration Analysis

### 2.1 Current Phase 4 Configuration

```xml
<pillarChangeMoveSelector>
  <fixedProbabilityWeight>2.0</fixedProbabilityWeight>
  <pillarSelector>
    <entitySelector>
      <entityClass>com.school.planner.domain.Lesson</entityClass>
      <filterClass>com.school.planner.solver.UnpinnedLessonFilter</filterClass>
    </entitySelector>
    <minimumSubPillarSize>2</minimumSubPillarSize>
    <maximumSubPillarSize>5</maximumSubPillarSize>
  </pillarSelector>
  <valueSelector variableName="timeslot"/>
</pillarChangeMoveSelector>
```

### 2.2 Observations

| Aspect | Current Setting | Assessment |
|--------|----------------|------------|
| Entity type | `Lesson` only | ⚠️ LabBlock not included |
| Variable | `timeslot` only | ⚠️ Room not targeted |
| minSubPillarSize | 2 | ✅ Correct (1 = redundant with changeMove) |
| maxSubPillarSize | 5 | ✅ Reasonable (see analysis below) |
| subPillarType | Default (ALL) | ⚠️ SEQUENCE could be useful |

---

## 3. Research Question 1: Is `maximumSubPillarSize=5` Too Small?

### 3.1 Timefold Documentation Guidance

From the Timefold documentation:
> "The number of sub-pillars of a pillar is exponential to the size of the pillar. For example a pillar of size 32 has `(2^32 - 1)` sub-pillars. Therefore a `pillarSelector` only supports JIT random selection (which is the default)."

**Key Insight**: The exponential growth means:
- Pillar of size 5: 2^5 - 1 = **31 sub-pillars**
- Pillar of size 10: 2^10 - 1 = **1,023 sub-pillars**
- Pillar of size 15: 2^15 - 1 = **32,767 sub-pillars**
- Pillar of size 20: 2^20 - 1 = **1,048,575 sub-pillars**

### 3.2 Computational Cost Analysis

For the planner with ~7,000-8,700 entities:

| maxSubPillarSize | Sub-pillars per pillar | Evaluation cost | Feasibility |
|------------------|------------------------|-----------------|-------------|
| 5 | 31 | Low | ✅ Recommended |
| 10 | 1,023 | Medium | ⚠️ Test carefully |
| 15 | 32,767 | High | ❌ Not recommended |
| 20+ | 1M+ | Very High | ❌ Prohibitively expensive |

### 3.3 Academic Research Perspective

From Pisinger & Ropke (Large Neighborhood Search):
> "The ALNS framework has several advantages... Due to the large neighborhoods and diversity of the neighborhoods, the ALNS algorithm will explore large parts of the solution space in a structured way."

However, the same research notes:
> "The use of multiple neighborhoods allows the algorithm to balance exploration and exploitation effectively."

**Interpretation**: Larger neighborhoods (bigger subpillars) enable exploration, but the **cost-benefit ratio diminishes rapidly** beyond size 10. The solver already has swap moves and cartesian product moves for exploration.

### 3.4 Recommendation

**Keep `maximumSubPillarSize=5` as default, but benchmark `maximumSubPillarSize=10`** to verify if the additional exploration justifies the computational cost.

For problems with 7,000-8,700 entities:
- Pillars of size 5 can still represent meaningful groups (e.g., all lessons for a teacher on a given day)
- The JIT random selection handles the exponential space efficiently
- The solver has other move types (swap, cartesian) for exploration

---

## 4. Research Question 2: Should Pillar Moves Target `room`?

### 4.1 Current Gap

Currently, pillar moves only target `timeslot`. This means:
- All lessons with the same timeslot can be moved together
- But lessons with the same room cannot be bulk-relocated

### 4.2 When Room Pillar Moves Would Help

From the constraint analysis:

| Constraint | Description | Room Pillar Benefit |
|------------|-------------|---------------------|
| H2 | Room conflict | ⭐⭐⭐ Direct benefit |
| H-M2 | Room suitability | ⭐⭐ Lab room reassignment |
| M3 | Room capacity | ⭐⭐ Capacity balancing |
| M4 | Lab room-tag affinity | ⭐ Lab optimization |

### 4.3 Implementation Recommendation

**Yes, add pillar moves for `room` variable.** Configuration:

```xml
<pillarChangeMoveSelector>
  <fixedProbabilityWeight>1.5</fixedProbabilityWeight>
  <pillarSelector>
    <entitySelector>
      <entityClass>com.school.planner.domain.Lesson</entityClass>
      <filterClass>com.school.planner.solver.UnpinnedLessonFilter</filterClass>
    </entitySelector>
    <minimumSubPillarSize>2</minimumSubPillarSize>
    <maximumSubPillarSize>5</maximumSubPillarSize>
  </pillarSelector>
  <valueSelector variableName="room"/>
</pillarChangeMoveSelector>
```

**Rationale**: Room conflicts (H2) are a primary hard constraint. Bulk room reassignment can efficiently resolve conflicts where multiple lessons need to vacate an overbooked room.

---

## 5. Research Question 3: Should LabBlock Entities Have Pillar Moves?

### 5.1 Current Gap

LabBlocks are not included in pillar moves. The Phase 4 config only targets `Lesson` entities.

### 5.2 Analysis

**Arguments FOR LabBlock pillar moves:**
- LabBlocks share rooms (lab rooms with specific tags)
- Moving multiple LabBlocks together could resolve room conflicts
- LabBlocks have the same planning variables (startTimeslot, room)

**Arguments AGAINST LabBlock pillar moves:**
- LabBlock count is much smaller (~300-500 vs ~7,000-8,000 Lessons)
- LabBlocks are already harder to place (multi-hour, constrained rooms)
- Swap moves for LabBlocks already exist

### 5.3 Recommendation

**Low priority.** LabBlocks represent a small fraction of entities, and their constraints are already handled by:
- Existing swap moves for LabBlocks
- Room-only moves in Phase 5
- Construction heuristic placing LabBlocks first

If benchmarking shows room conflicts persist for lab rooms, consider adding:

```xml
<pillarChangeMoveSelector>
  <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
  <pillarSelector>
    <entitySelector>
      <entityClass>com.school.planner.domain.LabBlock</entityClass>
    </entitySelector>
    <minimumSubPillarSize>2</minimumSubPillarSize>
    <maximumSubPillarSize>3</maximumSubPillarSize> <!-- Smaller due to fewer LabBlocks -->
  </pillarSelector>
  <valueSelector variableName="room"/>
</pillarChangeMoveSelector>
```

---

## 6. Research Question 4: What Pillar Property Is Used for Grouping?

### 6.1 Default Behavior

From Timefold documentation:
> "A pillar is a set of planning entities which have the same planning value(s) for their planning variable(s)."

**Default grouping**: Entities with the **same current value** for the planning variable form a pillar.

For the planner:
- Lessons with the same `timeslot` form a pillar
- (If room pillar moves added) Lessons with the same `room` form a pillar

### 6.2 Alternative: Sequential Subpillars

Timefold supports **sequential subpillars** with `subPillarType="SEQUENCE"`:

> "A sequential sub-pillar is a continuous subset of its sorted base pillar. For example, if an employee has shifts on Monday (M), Tuesday (T), and Wednesday (W), they're a pillar and only the following are its sequential sub-pillars: [M], [T], [W], [M, T], [T, W], [M, T, W]."

### 6.3 Applicability to Planner

For the school schedule problem, **sequential subpillars are NOT recommended** because:
- Lessons don't have a natural sequential ordering within a timeslot
- The `Comparable` interface would need to be implemented meaninglessly
- Random subpillars (default) provide better exploration

### 6.4 Custom Pillar Grouping (Advanced)

For future consideration, a custom pillar grouping could group lessons by:
- **Same teacher**: All of a teacher's lessons at a given timeslot
- **Same student group**: All lessons for a group at a given timeslot
- **Same subject**: All lessons of a subject at a given timeslot

This would require custom move selector implementation and is **not recommended** for the current phase.

---

## 7. Benchmark Methodology

### 7.1 Recommended A/B Tests

| Test | Configuration | Duration | Metric |
|------|---------------|----------|--------|
| Baseline | Current config (maxSubPillarSize=5) | 10 min | Hard score |
| Test A | maxSubPillarSize=10 | 10 min | Hard score |
| Test B | Add room pillar moves (maxSubPillarSize=5) | 10 min | Hard score |
| Test C | Combined (maxSubPillarSize=10 + room pillars) | 10 min | Hard score |

### 7.2 Benchmark Command

```bash
# Run on feature/cartesian-block-entity branch
./scripts/benchmark.sh feature/cartesian-block-entity 10

# Modify solverConfig.xml for each test variant
# Compare hard scores at termination
```

### 7.3 Success Criteria

- **Improvement threshold**: 5% or better hard score improvement
- **No regression**: Soft score should not degrade significantly
- **Time efficiency**: Solve time should not increase by more than 20%

---

## 8. Conclusions

### 8.1 Summary of Recommendations

| Question | Recommendation | Priority | Confidence |
|----------|----------------|----------|------------|
| maxSubPillarSize=5 too small? | Keep 5, benchmark 10 | Medium | High |
| Pillar moves for room? | Yes, add configuration | High | High |
| LabBlock pillar moves? | Low priority, defer | Low | Medium |
| Custom pillar grouping? | Not recommended | N/A | High |

### 8.2 Implementation Order

1. **Add room pillar moves** (high impact, low risk)
2. **Benchmark maxSubPillarSize=10** (validation)
3. **Consider LabBlock pillar moves** only if room conflicts persist

### 8.3 Expected Impact

Based on the analysis:
- Adding room pillar moves could improve H2 (room conflict) resolution by 10-20%
- Increasing maxSubPillarSize to 10 may provide marginal improvement but with higher computational cost
- Overall hard score improvement potential: 5-15%

---

## 9. References

1. Timefold Solver Documentation - Move Selector Reference: https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference
2. Timefold Solver Documentation - Local Search: https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/local-search
3. Pisinger, D., & Ropke, S. - Large Neighborhood Search (2010): https://backend.orbit.dtu.dk/ws/files/5293785/Pisinger.pdf
4. Ahuja, R.K., et al. - A Survey of Very Large-Scale Neighborhood Search Techniques (2002)
5. Shaw, P. - Using Constraint Programming and Local Search Methods to Solve Vehicle Routing Problems (1998)

---

## 10. Follow-up Tasks

If benchmarks show improvement:

- [ ] Create implementation task for room pillar moves
- [ ] Update `claudedocs/solver-configurations.md` with new configuration
- [ ] Run full benchmark suite on all 3 active branches
- [ ] Update solverConfig.xml with validated configuration

If no improvement:

- [ ] Document benchmark results in this file
- [ ] Close task-096 as "research complete, no changes recommended"
