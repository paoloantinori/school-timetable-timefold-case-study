---
id: TASK-101.03
title: 'Student Group Start Date Tests (H14.1, H14.2)'
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
ordinal: 60000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add constraint verifier tests for student group start date constraints.

**Constraints to test:**
- H14.1: Student group start date (Lesson)
- H14.2: Student group start date (LabBlock)

**Test file:** `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java`

**Pattern:** Each constraint gets two tests — one that triggers the violation (penalizes) and one that doesn't.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 studentGroupStartDate_penalized test passes
- [ ] #2 studentGroupStartDate_notPenalized test passes
- [ ] #3 studentGroupStartDateBlock_penalized test passes
- [ ] #4 studentGroupStartDateBlock_notPenalized test passes
<!-- AC:END -->
