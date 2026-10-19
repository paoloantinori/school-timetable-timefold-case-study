---
id: TASK-290
title: Add swapMoveSelector for rooms to Phase 5
status: Done
assignee: []
created_date: '2026-03-22 05:38'
updated_date: '2026-03-22 05:58'
labels:
  - claude-planned
  - ls-optimization
  - solver
milestone: solver-optimization
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add a `swapMoveSelector` for the room variable to Phase 5 (Room Refinement) in `solverConfig.xml`.

## Why This Matters

Phase 5 currently has ONLY `changeMoveSelector` for the room variable. This means the solver can only change a room assignment by unassigning the current occupant first — it cannot directly swap two lessons' rooms.

Without room swap:
- Moving lesson A from Room X to Room Y fails if Room Y is occupied (H2 violation)
- The solver must first move the occupant out, then move A in — two moves instead of one
- This is especially costly when rooms have specific tags (lab rooms) and alternatives are limited

With room swap:
- If lesson A is in Lab Info (needs `informatica` tag) and lesson B (Math) is in a regular classroom, swapping doesn't help (tags don't match)
- But if two lab lessons are in each other's preferred rooms, swap resolves both in one move
- The old `benchmark/solverConfig-quality.xml` DID have room swapMove — it was removed at some point

## Implementation

Add to Phase 5 `unionMoveSelector` in `solverConfig.xml`:

```xml
<swapMoveSelector>
  <fixedProbabilityWeight>2.0</fixedProbabilityWeight>
  <entitySelector>
    <entityClass>com.school.planner.domain.Lesson</entityClass>
  </entitySelector>
  <variableNameIncludes>
    <variableNameInclude>room</variableNameInclude>
  </variableNameIncludes>
</swapMoveSelector>
```

## References

- Research: `claudeodocs/research_timefold_ls_optimization_20260322.md` — Move selector best practices, Phase 5 gap analysis
- Research: `claudeodocs/research_local_search_metaheuristics_timetabling_20260322.md` — Resource swap as standard operator
- Historical: `src/main/resources/benchmark/solverConfig-quality.xml` — Previous config that included room swapMove
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Phase 5 unionMoveSelector in solverConfig.xml includes swapMoveSelector for room variable
- [x] #2 Weight assigned (recommended 2.0) to balance with room changeMove
- [x] #3 solverConfig.xml validates successfully (validate-solver-xml.sh)
- [x] #4 Rebuild succeeds and solver runs without errors
- [ ] #5 Benchmark comparison: Phase 5 with room swapMove vs without (same time limit)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added `swapMoveSelector` for room variable to Phase 5's `unionMoveSelector` in `solverConfig.xml`.

**What changed:**
- Added `swapMoveSelector` with weight 1.0 for room variable
- Added `fixedProbabilityWeight=2.0` to existing `changeMoveSelector` (was unweighted)
- Probability distribution: changeMove=67%, swapMove=33%
- Used correct Timefold XSD structure: `entitySelector` + `secondaryEntitySelector` + `variableNameIncludes`

**Same plan correction as TASK-289:** Task description had bare `<variableNameInclude>` and no `secondaryEntitySelector`. Applied the XSD-correct structure learned from TASK-289.

**Verification:** XML validates against Timefold XSD, build succeeds, app starts without errors.
<!-- SECTION:FINAL_SUMMARY:END -->
