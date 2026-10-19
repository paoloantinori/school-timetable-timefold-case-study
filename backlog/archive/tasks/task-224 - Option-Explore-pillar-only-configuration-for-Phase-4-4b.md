---
id: TASK-224
title: 'Option: Explore pillar-only configuration for Phase 4/4b'
status: To Do
assignee: []
created_date: '2026-03-10 06:10'
updated_date: '2026-07-27 04:48'
labels: []
dependencies:
  - TASK-280
priority: medium
ordinal: 250
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
If swap removal degrades conflict resolution too much, explore pillar moves as an alternative. Pillar moves on `timeslot` can move multiple entities simultaneously without changing rooms.

**Changes required**:
- `solverConfig.xml` Phase 4: Replace swap selectors with additional `pillarChangeMoveSelector` (timeslot-only)
- `solverConfig.xml` Phase 4b: Add `pillarChangeMoveSelector` (timeslot-only)

**Current Phase 4 has**:
- `pillarChangeMoveSelector` (timeslot) with `minimumSubPillarSize=2`, `maximumSubPillarSize=10`
- `pillarSwapMoveSelector` (exchanges pillar groups atomically)

**Exploration needed**:
- Can pillar moves replace swap effectiveness for conflict resolution?
- What subPillarSize ranges work best for timeslot-only pillar moves?
- Do we need both pillarChange and pillarSwap, or just one?

**Acceptance Criteria**:
- [ ] Research pillar move effectiveness for H1/H3 conflict resolution
- [ ] Implement pillar-only configuration
- [ ] Benchmark vs swap-based configuration
- [ ] Document findings
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SUPERATO (2026-07-27): come TASK-223, numerazione fasi obsoleta; pillar-only e' gia' coperto dal cluster benchmark (332 in primis). Archiviato.
<!-- SECTION:NOTES:END -->
