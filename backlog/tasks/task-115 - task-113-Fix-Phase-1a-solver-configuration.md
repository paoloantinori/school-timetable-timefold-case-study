---
id: TASK-115
title: task-113 - Fix Phase 1a solver configuration
status: Done
assignee: []
created_date: '2026-02-24 20:24'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - solver-tuning
  - phase1a
dependencies: []
ordinal: 125000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Fix acceptedCountLimit from 4 to 1 and increase lateAcceptanceSize to 600, improve unimproved termination limit, and validate solver config and test Phase 1a
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Phase 1a reaches significantly better hard score with room filtering
- [x] #2 Only entities without hard violations are pinned
- [x] #3 M17 constraint excluded from Phase 1 to focus on hard feasibility
- [x] #4 Room filtering eliminates H-M2.2 violations
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation (2026-02-25)

### Problem Analysis
Phase 1a was stuck at -307hard with 263 H-M2.2 (room suitability) violations. Root causes:
1. **Room filtering not working**: CH placed LabBlocks in any room, ignoring required room tags
2. **Pinning logic flawed**: ALL assigned entities were pinned, even those with hard violations
3. **M17 distraction**: Medium constraint M17 (lab recurring pattern) was active during pre-assignment

### Fixes Applied

**1. Room Filtering** (`LabBlock.java`, `SchoolSchedule.java`, `ScheduleService.java`)
- Added `@ValueRangeProvider(id = "room")` on `LabBlock.getAvailableRooms()`
- Filters rooms by `course.requiredRoomTags` - only returns rooms with matching tags
- Added `SchoolSchedule.setAllRoomsStatic()` / `getAllRooms()` for entity-level access
- Explicitly referenced via `@PlanningVariable(valueRangeProviderRefs = {"room"})`

**2. Pinning Logic Fix** (`TwoPhasePreAssignmentService.java`)
- Extract violating entity IDs from `ScoreExplanation.getConstraintMatchTotalMap()`
- Only pin entities NOT in violation sets
- Added logging of violation entity counts

**3. M17 Exclusion** (`BaseConstraintProvider.java`)
- Moved M17 (lab recurring pattern) inside the `!isPreAssignmentMode()` check
- Phase 1 now focuses purely on hard feasibility

### Results

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Phase 1a final score | -307hard | **-38hard** | **8x better** |
| H-M2.2 violations | -263 | **0** | **Eliminated** |
| Violating LabBlocks | 277 | **35** | **87% reduction** |

### Remaining Violations (35 blocks)
- H5.2 (Teacher unavailability): 17 blocks
- H6.2 (School holiday): 14 blocks
- H9 (Block valid placement): 6 blocks
- H1.3 (Teacher conflict): 1 block

These require additional solver time or constraint tuning to eliminate.
<!-- SECTION:NOTES:END -->
