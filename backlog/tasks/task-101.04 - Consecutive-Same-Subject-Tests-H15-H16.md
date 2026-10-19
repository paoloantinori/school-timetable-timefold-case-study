---
id: TASK-101.04
title: 'Consecutive Same-Subject Tests (H15, H16)'
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
ordinal: 61000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add constraint verifier tests for consecutive same-subject constraints.

**Constraints to test:**
- H15: Same-day same-subject must be consecutive
- H16: Consecutive same-subject same teacher-room

**Test file:** `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java`

**Pattern:** Each constraint gets two tests — one that triggers the violation (penalizes) and one that doesn't.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 sameDaySameSubjectMustBeConsecutive_penalized test passes
- [ ] #2 sameDaySameSubjectMustBeConsecutive_notPenalized test passes
- [ ] #3 consecutiveSameSubjectSameTeacherRoom_penalized test passes
- [ ] #4 consecutiveSameSubjectSameTeacherRoom_notPenalized test passes
<!-- AC:END -->
