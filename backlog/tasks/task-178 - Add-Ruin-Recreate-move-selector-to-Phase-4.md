---
id: TASK-178
title: Add Ruin & Recreate move selector to Phase 4
status: Done
assignee: []
created_date: '2026-03-05 15:27'
updated_date: '2026-03-10 05:16'
labels:
  - solver
  - performance
  - claude-planned
milestone: Timefold Upgrade
dependencies:
  - TASK-177
references:
  - src/main/resources/solverConfig.xml
  - claudedocs/research-timefold-rr-upgrade-2026-03-05.md
  - >-
    https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference
priority: high
ordinal: 16750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

After upgrading to Timefold 1.31.0, R&R with `entitySelector` is available for multi-entity models. R&R destroys 5-15 entities and rebuilds them via construction heuristic, enabling escape from local optima that swap/pillar moves cannot reach.

## Implementation

Add to Phase 4 (Conflict Resolution) `unionMoveSelector` in `solverConfig.xml`:

```xml
<ruinRecreateMoveSelector>
  <fixedProbabilityWeight>0.01</fixedProbabilityWeight>
  <minimumRuinedCount>5</minimumRuinedCount>
  <maximumRuinedCount>15</maximumRuinedCount>
  <entitySelector>
    <entityClass>com.school.planner.domain.Lesson</entityClass>
  </entitySelector>
</ruinRecreateMoveSelector>
```

## Design Decisions

- **Target Lesson only** (not LabBlock): All 1,135 LabBlocks are pre-pinned. Only Lesson entities (7,538) are moved by the solver. R&R on pinned entities is wasteful.
- **Weight 0.01**: 1 R&R move per 100 fine-grained moves. R&R is expensive (runs a mini CH). Starting conservative; tune via benchmarking.
- **Count 5-15**: Timefold defaults are 5-20. Starting smaller since our entities are highly interconnected (teacher/room/group conflicts cascade).
- **Phase 4 only**: Phase 3 (compactness) uses greedy moves that don't benefit from R&R. Phase 5 (room-only) is too narrow.

## Validation

Run `./scripts/validate-solver-xml.sh` after changes.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 R&R move selector added to Phase 4 in solverConfig.xml
- [ ] #2 entitySelector specifies Lesson class (not LabBlock)
- [ ] #3 XML validates with ./scripts/validate-solver-xml.sh
- [ ] #4 App starts without RuinRecreateMoveSelectorFactory errors
- [ ] #5 Solver runs and R&R moves appear in logs (score improvements from R&R)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Ruin & Recreate implemented in solverConfig.xml Phase 4 (lines 222-230). Configuration: weight=0.01, minRuined=5, maxRuined=15, targets Lesson only. Feature complete - benchmarking deferred to TASK-180.
<!-- SECTION:FINAL_SUMMARY:END -->
