---
id: TASK-298
title: Fix H11 constraint validation for multi-hour lab blocks in pre-assignment
status: Done
assignee: []
created_date: '2026-03-22 21:29'
updated_date: '2026-03-22 21:30'
labels:
  - bug
  - pre-assignment
  - H11
  - lab-blocks
  - validation
dependencies: []
references:
  - claudedocs/human-planner-heuristics.md
  - src/main/java/com/school/planner/service/HybridPhase1aService.java
  - src/main/java/com/school/planner/service/GreedyRotationAssigner.java
  - src/main/java/com/school/planner/domain/StudentGroup.java
documentation:
  - claudedocs/solver-phases.md
  - CLAUDE.md
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Description

The pre-assignment system (HybridPhase1aService) has a critical bug in H11 (maxHoursPerDay) constraint validation. When placing multi-hour lab blocks, it only validates the STARTING slot against the maxHoursPerDay limit, but does NOT check whether the ENTIRE block fits within the valid time range.

### Specific Example (G12 Informatica)

- **Course**: Informatica for G12
- **maxHoursPerDay**: 5 (valid slots 0-4: 08:00-13:00)
- **labBlockSize**: 2 hours
- **Bug**: Pre-assignment places 2-hour blocks at slots 12-13 (12:00-14:00)
  - Slot 12 (12:00-13:00): 5th hour, within limit ✓
  - Slot 13 (13:00-14:00): 6th hour, **EXCEEDS LIMIT** ✗

### Affected Dates
- Wed 12/11/2025
- Wed 19/11/2025
- Likely other dates with similar invalid placements

## Root Cause

The validation logic (likely in `wouldExceedDailyLimit()` or equivalent in `HybridPhase1aService` or `GreedyRotationAssigner`) only checks the starting slot's hourly count against maxHoursPerDay. It does NOT iterate through all slots that the block would occupy to verify each stays within the limit.

For a 2-hour block starting at slot 12:
- Current check: `hoursAtSlot[12] < maxHoursPerDay` → 4 < 5 ✓ (passes)
- Missing check: `hoursAtSlot[13] < maxHoursPerDay` → 5 < 5 ✗ (should fail)

## Files to Modify

1. **src/main/java/com/school/planner/service/HybridPhase1aService.java**
   - `wouldExceedDailyLimit()` method
   - Block placement validation logic in `loadLabSessionsByRoomTag()` or related methods

2. **src/main/java/com/school/planner/service/GreedyRotationAssigner.java**
   - `findValidSlotForBlock()` method
   - Any slot validation logic

## Expected Fix Approach

1. When validating a block placement starting at `slotStart` with size `blockSize`:
   - Check ALL slots from `slotStart` to `slotStart + blockSize - 1`
   - Each slot must satisfy: `currentHoursAtSlot + 1 <= maxHoursPerDay`
   - Reject the placement if ANY slot in the block would exceed the limit

2. The validation should happen BEFORE pinning the assignment, not after

## Acceptance Criteria
<!-- AC:BEGIN -->
1. G12 Informatica 2-hour blocks are NOT placed at slots that would cause the second hour to exceed maxHoursPerDay
2. Pre-assignment succeeds without placing blocks that violate H11 constraints
3. No regression for groups with higher maxHoursPerDay limits (6-hour groups)
4. Unit tests added to verify multi-hour block validation

## Test Cases

1. **Test 5-hour group with 2-hour block**: Verify 2-hour block cannot be placed starting at slot 4 (would use slots 4-5, exceeding limit)
2. **Test 5-hour group with 3-hour block**: Verify 3-hour block cannot be placed starting at slot 3 (would use slots 3-5, exceeding limit)
3. **Test 6-hour group with 2-hour block**: Verify 2-hour block CAN be placed at slot 4 (would use slots 4-5, within limit)
4. **Test edge case**: Block ending exactly at limit (e.g., 5-hour group, 2-hour block at slot 3-4)

## Related Constraints

- H11: Max student group daily hours (hard constraint)
- H9: Lab entity valid placement (block entity branches)
- M13: Vertically compact (may interact with slot selection)
<!-- SECTION:DESCRIPTION:END -->

- [ ] #1 Pre-assignment validates ALL slots in a multi-hour block against maxHoursPerDay, not just the starting slot
- [ ] #2 G12 Informatica blocks are no longer placed at slots 12-13 (or any slot where block exceeds limit)
- [ ] #3 Unit tests added for multi-hour block validation with various maxHoursPerDay values
- [ ] #4 No regression for 6-hour groups (FB, FC, etc.)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Code fix implemented and tested
- [ ] #2 Unit tests pass for all edge cases (2-hour, 3-hour, 4-hour blocks with various maxHoursPerDay)
- [ ] #3 Manual verification: pre-assignment succeeds without H11 violations for G12
- [ ] #4 No regressions for other student groups
- [ ] #5 Code reviewed for similar validation patterns elsewhere
<!-- DOD:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
