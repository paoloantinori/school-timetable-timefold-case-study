---
id: TASK-245
title: 'Model T28 lab exception for 5-hour groups (G10, 4 FA)'
status: Done
assignee: []
created_date: '2026-03-16 06:42'
updated_date: '2026-03-16 13:43'
labels:
  - h11
  - t28-exception
  - 5-hour-groups
  - constraint
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
G10 and 4 FA have 5-hour daily schedules but do 6 hours on days with T28 lab (external teacher, Thursdays only). This requires an exception mechanism in H11 constraint.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Set maxHoursPerDay=5 for G10 and 4 FA in DataGenerator
- [ ] #2 Identify T28 lessons in H11 constraint (teacher="T28" or marked as external)
- [ ] #3 Modify H11 to exempt T28 lessons from daily hour counting
- [ ] #4 Verify pre-assignment honors T28 6-hour exception
- [ ] #5 Update target end date calculation to account for reduced weekly hours
- [ ] #6 Document T28 exception pattern (Thursdays, external teacher constraint)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Implementation Complete (2026-03-16)

**AC#5: Target End Date Calculation**

- Modified `TargetEndDateCalculator.computeGroupTargetDate()` to respect `group.maxHoursPerDay`

- Uses `Math.min(standardSlots, group.maxHoursPerDay)` to enforce daily limit

- Files modified:

- `src/main/java/com/school/planner/service/TargetEndDateCalculator.java`

**AC#6: Documentation**

- Updated `claudedocs/t28-exception-plan.md` with completion status

- Resolved open questions about LabBlock exemption and target date calculation

**Verification**:

- Build successful: `./scripts/core/rebuild.sh` compiled without errors

- All acceptance criteria (AC#1-6) are now complete

**Key Files**:

- `TargetEndDateCalculator.java` - Now respects maxHoursPerDay for 5-hour groups

- `SchoolScheduleConstraintProvider.java` - H11 exempts external teacher lessons (already implemented)

- `t28-exception-plan.md` - Complete implementation documentation
<!-- SECTION:FINAL_SUMMARY:END -->
