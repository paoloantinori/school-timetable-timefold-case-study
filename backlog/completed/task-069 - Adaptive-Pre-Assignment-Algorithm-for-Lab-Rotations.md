---
id: task-069
title: Adaptive Pre-Assignment Algorithm for Lab Rotations
status: Done
assignee: []
created_date: '2026-02-22 15:52'
updated_date: '2026-02-22 16:41'
labels:
  - claude-planned
  - pre-assignment
  - solver
  - architecture
dependencies: []
priority: high
ordinal: 44000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
The current pre-assignment implementation has rigid `LabRotation.dayOfWeek` values that don't match the actual human schedule (spalmone). This causes errors like "27 slots, only 15 available" when trying to fit a 137-hour course into a 15-week Wednesday rotation.

## Root Cause
1. LabRotations have fixed dayOfWeek + periodStart/periodEnd that don't match reality
2. Pre-assigners don't check TeacherAvailability - they trust LabRotation blindly
3. The spalmone shows teachers work across multiple days (Wed/Tue/Fri), not a single fixed day

## Solution
Make pre-assignment adaptive - find valid slots based on teacher availability and room contention, while still producing a regular weekly pattern (same day each week for extended periods).

## Requirements
1. Respect teacher availability (use TeacherAvailability VACATION/SICK as source of truth)
2. Use full blocks (6-hour slots for labs, not scattered hours)
3. Follow regular weekly pattern (same day each week for extended periods)
4. Be adaptive - if T28 leaves and a new teacher with different constraints joins, no code changes needed
5. Backwards compatible - existing LabRotations with dayOfWeek set continue to work

## Implementation Plan

### Phase 1: Create AdaptiveSlotFinder Service
- New file: `service/AdaptiveSlotFinder.java`
- `findBestDayForRotation()` - score days by availability + contention
- `findAvailableDaysForTeacher()` - query TeacherAvailability for VACATION/SICK
- `countAvailableWeeks()` - exclude holidays and stage blackouts
- `findBestDaysForOverflow()` - ranked alternatives for overflow

### Phase 2: Modify AcconciaturaPreAssigner
- Inject AdaptiveSlotFinder
- When dayOfWeek is null, use adaptive selection
- Add overflow handling when primary rotation exhausted

### Phase 3: Modify LabBlockPreAssigner
- Same pattern as AcconciaturaPreAssigner
- Adaptive day selection + overflow

### Phase 4: Update RotationResult Reporting
- Add overflowAssigned, overflowDay, computedDay fields

### Phase 5: Seed Data Updates
- Verify TeacherAvailability complete for external teachers
- Optionally clear dayOfWeek from LabRotations to test adaptive behavior
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 AdaptiveSlotFinder service created with findBestDayForRotation() method
- [x] #2 AcconciaturaPreAssigner uses adaptive selection when dayOfWeek is null
- [x] #3 LabBlockPreAssigner uses adaptive selection when dayOfWeek is null
- [x] #4 Overflow handling assigns remaining hours to additional days
- [x] #5 No H5 violations (teacher unavailability) in pre-assigned slots
- [x] #6 Unit tests pass for AdaptiveSlotFinder methods
- [x] #7 Integration test shows T26's 137h course spread across multiple days
- [x] #8 Existing LabRotations with dayOfWeek set continue to work (backwards compatible)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
### Phase 1: Create AdaptiveSlotFinder Service
- New file: `service/AdaptiveSlotFinder.java`
- `findBestDayForRotation()` - score days by availability + contention
- `findAvailableDaysForTeacher()` - query TeacherAvailability for VACATION/SICK
- `countAvailableWeeks()` - exclude holidays and stage blackouts
- `findBestDaysForOverflow()` - ranked alternatives for overflow

### Phase 2: Modify AcconciaturaPreAssigner
- Inject AdaptiveSlotFinder
- When dayOfWeek is null, use adaptive selection
- Add overflow handling when primary rotation exhausted

### Phase 3: Modify LabBlockPreAssigner
- Same pattern as AcconciaturaPreAssigner
- Adaptive day selection + overflow

### Phase 4: Update RotationResult Reporting
- Add overflowAssigned, overflowDay, computedDay fields

### Phase 5: Seed Data Updates
- Verify TeacherAvailability complete for external teachers
- Optionally clear dayOfWeek from LabRotations to test adaptive behavior
<!-- SECTION:DESCRIPTION:END -->
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Progress 2026-02-22

### Completed
- ✅ Phase 1: AdaptiveSlotFinder service created
  - findBestDayForRotation() - scores days by availability + contention
  - findAvailableDaysForTeacher() - queries TeacherAvailability
  - countAvailableWeeks() - excludes holidays and stage blackouts
  - findBestDaysForOverflow() - ranked alternatives for overflow
  - computeValidDatesAdaptive() - uses adaptive selection when dayOfWeek is null

### Next
- Phase 2: Modify AcconciaturaPreAssigner to inject AdaptiveSlotFinder

### Completed 2026-02-22 (continued)
- ✅ Phase 2: AcconciaturaPreAssigner modified
  - Injected AdaptiveSlotFinder
  - Uses adaptive selection when dayOfWeek is null
  - Added overflow handling for remaining entities
  - Updated RotationResult with computedDay, overflowDay, overflowAssigned fields

- ✅ Phase 3: LabBlockPreAssigner modified
  - Same pattern as AcconciaturaPreAssigner
  - Adaptive day selection + overflow handling

- ✅ Tests pass (mvn test)

### Completed 2026-02-22 (final)
- ✅ Phase 4: RotationResult updated with new fields
  - computedDay: day selected by adaptive algorithm
  - overflowDay: day used for overflow assignments
  - overflowAssigned: count of entities assigned to overflow day

- ✅ Unit tests created and passing (#6)
  - testFindBestDayForRotation_withNoAvailabilityRestrictions
  - testFindBestDayForRotation_withVacationBlockingWednesday
  - testIsTeacherUnavailableAllDay_withVacation
  - testIsTeacherUnavailableAllDay_withSick
  - testCountAvailableWeeks_withHolidays
  - testComputeValidDates_withDayOfWeekSet
  - testComputeValidDates_withHolidayExclusion
  - testFindBestDaysForOverflow_excludesPrimaryDay
  - testBackwardsCompatibility_withDayOfWeekSet

### Remaining
- [ ] #7 Integration test with real T26 data (requires running app)

### Integration Test Passed ✅

**T26's 137-hour course (G06):**
- Primary day: Wednesday (15 valid dates)
- Overflow day: Thursday
- Total assigned: 27 (15 Wednesday + 12 Thursday overflow)
- Remaining: 0
- Validation: conflictFree=true, no H5 violations

The adaptive overflow successfully spread the course across multiple days when the primary rotation day was exhausted.
<!-- SECTION:NOTES:END -->
