---
id: TASK-101.02
title: 'L-B Variant Constraint Tests (H2.2, H3.2, H4.2, H5.2, H6.2)'
status: Done
assignee: []
created_date: '2026-02-27 17:59'
updated_date: '2026-03-09 19:50'
labels:
  - testing
  - constraints
dependencies: []
parent_task_id: TASK-101
priority: high
ordinal: 59000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add constraint verifier tests for Lesson-to-LabBlock variant constraints.

**Constraints to test:**
- H2.2: Room conflict (L-B)
- H3.2: Student group conflict (L-B)
- H4.2: Stage blackout (LabBlock)
- H5.2: Teacher unavailability (LabBlock)
- H6.2: School holiday (LabBlock)

**Test file:** `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java`

**Pattern:** Each constraint gets two tests — one that triggers the violation (penalizes) and one that doesn't.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 roomConflictLessonBlock_penalized / _notPenalized tests pass
- [ ] #2 studentGroupConflictLessonBlock_penalized / _notPenalized tests pass
- [ ] #3 stageBlackoutBlock_penalized / _notPenalized tests pass
- [ ] #4 teacherUnavailabilityBlock_penalized / _notPenalized tests pass
- [ ] #5 schoolHolidayBlock_penalized / _notPenalized tests pass
<!-- AC:END -->
