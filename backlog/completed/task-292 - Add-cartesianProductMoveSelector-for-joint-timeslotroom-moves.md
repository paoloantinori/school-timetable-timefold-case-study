---
id: TASK-292
title: Add cartesianProductMoveSelector for joint timeslot+room moves
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
ordinal: 270
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add a `cartesianProductMoveSelector` combining timeslot changeMove + room changeMove to Phase 3 or a new joint optimization phase in `solverConfig.xml`.

## Why This Matters

Our current phase architecture separates timeslot optimization (Phase 3) from room optimization (Phase 5). This creates a potential **score trap**: some improvements require changing BOTH a lesson's timeslot AND its room simultaneously. For example:
- Lesson A is in Room X at slot 8:00 — has a teacher conflict (H1)
- Moving to slot 9:00 resolves H1 but Room X is now occupied (H2)
- Moving to Room Y resolves H2 but Room Y doesn't have the right tags (H-M2)
- The only improvement requires changing timeslot AND room at once

With a cartesian product move, the solver can explore (timeslot=9:00, room=Y) as a single move, escaping the score trap.

The Timefold docs note: "Use cartesianProductMoveSelector to combine two move selectors to create a composite move that changes two variables of the same entity at the same time."

## Implementation

Option A: Add to Phase 3 unionMoveSelector (simplest):
```xml
<cartesianProductMoveSelector>
  <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
  <changeMoveSelector>
    <entitySelector>
      <entityClass>com.school.planner.domain.Lesson</entityClass>
    </entitySelector>
    <valueSelector variableName="timeslot"/>
  </changeMoveSelector>
  <changeMoveSelector>
    <entitySelector>
      <entityClass>com.school.planner.domain.Lesson</entityClass>
    </entitySelector>
    <valueSelector variableName="room"/>
  </changeMoveSelector>
</cartesianProductMoveSelector>
```

Option B: Create a new Phase 3.5 (joint optimization) between current Phase 3 and Phase 5.

**Trade-off**: Cartesian product significantly increases the move neighborhood size (timeslot_count × room_count per entity). Start with a low weight (1.0) and monitor performance.

## References

- Research: `claudeodocs/research_timefold_ls_optimization_20260322.md` — Score trap analysis, cartesianProduct recommendation
- Research: `claudeodocs/research_local_search_metaheuristics_timetabling_20260322.md` — Combined move operators in hybrid approaches
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 cartesianProductMoveSelector added to solverConfig.xml combining timeslot+room changeMoves
- [ ] #2 Weight assigned (recommended 1.0) — lower than single-variable moves due to larger neighborhood
- [ ] #3 solverConfig.xml validates successfully (validate-solver-xml.sh)
- [ ] #4 Rebuild succeeds and solver runs without errors
- [ ] #5 Benchmark: compare solve quality and moves-per-second with and without cartesian product
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RULED-OUT dalla campagna metaheuristic (synthesis 2026-08-05): cartesianProductMoveSelector non e' nel solverConfig di produzione (il nome del branch e' storico, documentato in branch-families.md reality note); la famiglia joint-moves e' stata esclusa dalla campagna esausta. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
