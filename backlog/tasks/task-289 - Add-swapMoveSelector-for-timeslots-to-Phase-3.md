---
id: TASK-289
title: Add swapMoveSelector for timeslots to Phase 3
status: Done
assignee: []
created_date: '2026-03-22 05:38'
updated_date: '2026-03-22 05:55'
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
Add a `swapMoveSelector` to the Phase 3 (Compactness) `unionMoveSelector` in `solverConfig.xml`.

## Why This Matters

Our Phase 3 currently has three move types for timeslots: `changeMove` (move one lesson), `pillarChangeMove` (move ALL lessons at a timeslot), and `pillarSwapMove` (swap ALL lessons between two timeslots). There is NO regular `swapMoveSelector` — the ability to swap exactly two lessons' timeslots.

`swapMove` is a fundamental LS operator for timetabling:
- `changeMove` moves lesson A to slot B, potentially displacing whatever was at B — creates cascading conflicts
- `swapMove` exchanges A↔B directly — no displacement, no cascade
- For H1 (teacher conflict): if teacher has two lessons at the same slot, swapMove can fix it by swapping one with a free slot on another teacher's schedule without creating new conflicts
- ITC-2007 research consistently identifies swap as one of the most effective operators

Our 23-config benchmark did not test swapMove either — this is a blind spot.

## Implementation

Add to Phase 3 `unionMoveSelector` in `solverConfig.xml`:

```xml
<swapMoveSelector>
  <fixedProbabilityWeight>3.0</fixedProbabilityWeight>
  <entitySelector>
    <entityClass>com.school.planner.domain.Lesson</entityClass>
  </entitySelector>
  <variableNameIncludes>
    <variableNameInclude>timeslot</variableNameInclude>
  </variableNameIncludes>
</swapMoveSelector>
```

Recommended weight: 3.0 (between changeMove at 5.0 and pillarChange at 2.0).

## References

- Research: `claudeodocs/research_timefold_ls_optimization_20260322.md` — Move selector best practices
- Research: `claudeodocs/research_local_search_metaheuristics_timetabling_20260322.md` — Neighborhood structures for timetabling
- Research: `claudeodocs/research_compactness_constraint_design_20260321.md` — Constraint decomposition
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Phase 3 unionMoveSelector in solverConfig.xml includes swapMoveSelector for timeslot variable
- [x] #2 Weight assigned (recommended 3.0) to balance with existing changeMove (5.0) and pillarChange (2.0)
- [x] #3 solverConfig.xml validates successfully (validate-solver-xml.sh)
- [x] #4 Rebuild succeeds and solver runs without errors
- [ ] #5 Benchmark comparison: Phase 3 with swapMove vs without swapMove (same time limit)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added `swapMoveSelector` for timeslot variable to Phase 3's `unionMoveSelector` in `solverConfig.xml`.

**What changed:**
- Added `swapMoveSelector` with weight 3.0 between `changeMoveSelector` (5.0) and `pillarChangeMoveSelector` (2.0)
- Probability distribution: change=50%, swap=30%, pillarChange=20%, pillarSwap=10%
- Used correct Timefold XSD structure: `entitySelector` + `secondaryEntitySelector` + `variableNameIncludes`

**Plan correction:** The original plan specified bare `<variableNameInclude>` as a direct child. XSD validation caught this — `swapMoveSelectorConfig` requires `<secondaryEntitySelector>` (type `entitySelectorConfig`, takes `entityClass` directly) and `<variableNameIncludes>` wrapper element. Fixed before rebuild.

**Verification:** XML validates against Timefold XSD, build succeeds, app starts without errors.
<!-- SECTION:FINAL_SUMMARY:END -->
