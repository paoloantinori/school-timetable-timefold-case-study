---
id: TASK-101.07
title: 'Soft Constraint Tests (S1, S2, S3, S5, S10)'
status: Done
assignee: []
created_date: '2026-02-27 17:59'
updated_date: '2026-03-09 19:50'
labels:
  - testing
  - constraints
dependencies: []
parent_task_id: TASK-101
priority: medium
ordinal: 64000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add constraint verifier tests for remaining soft constraints.

**Constraints to test:**
- S1: Teacher gap minimization
- S2: Subject distribution
- S3: Teacher preference
- S5: Student gap minimization (weight=500)
- S10: Discourage generic lessons in labs

**Test file:** `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java`

**Pattern:** Each constraint gets two tests — one that triggers the penalty and one that doesn't.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 minimizeTeacherGaps_penalized / _notPenalized tests pass
- [ ] #2 subjectDistribution_penalized / _notPenalized tests pass
- [ ] #3 teacherPreference_penalized / _notPenalized tests pass
- [ ] #4 minimizeStudentGaps_penalized / _notPenalized tests pass
- [ ] #5 discourageGenericInLabs_penalized / _notPenalized tests pass
<!-- AC:END -->
