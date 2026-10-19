---
id: TASK-101.01
title: 'B-B Variant Constraint Tests (H1.3, H2.3, H3.3)'
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
ordinal: 58000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add constraint verifier tests for LabBlock-to-LabBlock variant constraints.

**Constraints to test:**
- H1.3: Teacher conflict (B-B)
- H2.3: Room conflict (B-B)
- H3.3: Student group conflict (B-B)

**Test file:** `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java`

**Pattern:** Each constraint gets two tests — one that triggers the violation (penalizes) and one that doesn't.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 teacherConflictBlockBlock_penalized test passes
- [ ] #2 teacherConflictBlockBlock_notPenalized test passes
- [ ] #3 roomConflictBlockBlock_penalized test passes
- [ ] #4 roomConflictBlockBlock_notPenalized test passes
- [ ] #5 studentGroupConflictBlockBlock_penalized test passes
- [ ] #6 studentGroupConflictBlockBlock_notPenalized test passes
<!-- AC:END -->
