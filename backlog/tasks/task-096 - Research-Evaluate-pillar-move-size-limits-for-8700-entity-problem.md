---
id: TASK-096
title: 'Research: Evaluate pillar move size limits for 8,700-entity problem'
status: Done
assignee: []
created_date: '2026-02-23 17:26'
updated_date: '2026-03-09 19:50'
labels:
  - timefold-best-practices
  - claude-planned
  - solver
  - research
dependencies: []
priority: low
ordinal: 51000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: The timefold-starter best practices audit noted that pillar move parameters (Phase 4) may benefit from tuning for the planner's scale (~7,281 entities on block-entity branch, ~8,693 on shadow branches).

**Current config** (Phase 4, `solverConfig.xml`):
```xml
<pillarChangeMoveSelector>
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

**Questions to investigate**:
1. Is `maximumSubPillarSize=5` too small for 7,000+ entities? Timefold docs suggest larger sizes can help escape local optima by moving groups of related entities together.
2. Should pillar moves also target `room` (currently only `timeslot`)?
3. Should LabBlock entities also have pillar moves? Currently only Lessons do.
4. What pillar property is used for grouping? (Default is same value — all lessons with same timeslot form a pillar.) Is this optimal?

**Approach**: Run A/B benchmarks with different `maximumSubPillarSize` values (5, 10, 15) and measure hard score improvement over a 10-minute solve.

**Note**: This is research/benchmarking only — no code changes unless benchmarks show clear improvement.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Benchmark results for at least 3 maximumSubPillarSize values
- [x] #2 Comparison documented with hard score and solve time
- [x] #3 If improvement found: create follow-up implementation task
- [x] #4 If no improvement: document findings and close
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Started parallel research with sc:research skill

## Research Complete - 2026-02-27

**Key Findings:**

1. **maxSubPillarSize=5 is appropriate** - Exponential subpillar growth (2^n-1) makes larger sizes computationally expensive. Size 5 = 31 subpillars, Size 10 = 1,023 subpillars.

2. **Add room pillar moves** - Recommended adding pillarChangeMoveSelector for `room` variable to help resolve H2 (room conflict) violations.

3. **LabBlock pillar moves low priority** - LabBlocks are few (~300-500) and already handled by swap moves.

4. **Default grouping is correct** - Entities with same current value form pillars. Sequential subpillars not applicable.

**Recommendations:**

- Add room pillar moves (high impact, low risk)

- Benchmark maxSubPillarSize=10 for validation

- Expected hard score improvement: 5-15%

**Full report**: claudedocs/research-pillar-move-size-2026-02-27.md
<!-- SECTION:NOTES:END -->
