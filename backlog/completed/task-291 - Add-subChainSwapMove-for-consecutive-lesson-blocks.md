---
id: TASK-291
title: Add subChainSwapMove for consecutive lesson blocks
status: Done
assignee: []
created_date: '2026-03-22 05:38'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
  - ls-optimization
  - solver
milestone: solver-optimization
dependencies:
  - TASK-280
priority: medium
ordinal: 260
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add `subChainSwapMoveSelector` to Phase 3 (Compactness) in `solverConfig.xml` to enable swapping chains of consecutive lessons as a unit.

## Why This Matters

Several constraints depend on consecutive lesson patterns:
- **H15** (same-day same-subject consecutive): Two lessons of the same subject for the same group should be adjacent in the daily schedule
- **S7** (reward consecutive student lessons): Rewards lessons that are back-to-back for the same group
- **Lab blocks**: Even though lab lessons are pinned, non-pinned lessons can form consecutive patterns (e.g., a teacher teaching 3 hours of the same subject)

Currently, if the solver needs to move a 3-hour consecutive block, it must move each lesson individually — potentially breaking the consecutive pattern at each step. `subChainSwapMove` swaps an entire chain of consecutive lessons with another chain, preserving internal adjacency.

From the research: "block move/swaps" are identified as one of the six standard operators for timetabling. The ITC-2011 competition identified "Event Block Swap" as a key neighborhood structure.

## Implementation

Add to Phase 3 `unionMoveSelector` in `solverConfig.xml`:

```xml
<subChainSwapMoveSelector>
  <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
  <entitySelector>
    <entityClass>com.school.planner.domain.Lesson</entityClass>
  </entitySelector>
  <variableNameIncludes>
    <variableNameInclude>timeslot</variableNameInclude>
  </variableNameIncludes>
  <subChainSelector>
    <minimumSubChainSize>2</minimumSubChainSize>
    <maximumSubChainSize>4</maximumSubChainSize>
  </subChainSelector>
</subChainSwapMoveSelector>
```

Note: The `subChainSelector` determines what constitutes a "chain" — consecutive lessons for the same student group, or same teacher. Verify Timefold's default chain selection behavior for our entity model.

## References

- Research: `claudeodocs/research_local_search_metaheuristics_timetabling_20260322.md` — Neighborhood structures (block swap identified as standard operator)
- Research: `claudeodocs/research_timefold_ls_optimization_20260322.md` — SubChainSwapMove configuration
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Phase 3 unionMoveSelector includes subChainSwapMoveSelector for timeslot variable
- [ ] #2 subChainSelector configured with minimumSubChainSize=2 and maximumSubChainSize=4
- [ ] #3 solverConfig.xml validates successfully (validate-solver-xml.sh)
- [ ] #4 Rebuild succeeds and solver runs without errors
- [ ] #5 Benchmark: measure impact on H15/S7 scores
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RULED-OUT dalla campagna metaheuristic (synthesis 2026-08-05): i pillar/subchain moves sono censiti non-robusti o inerti sul pipeline attuale; il benchmark della notte 04/08 (TASK-284 VND handoff) e il synthesis chiudono la famiglia. Le mosse cartesianProduct sopravvivono solo nei benchmark XML sperimentali. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
