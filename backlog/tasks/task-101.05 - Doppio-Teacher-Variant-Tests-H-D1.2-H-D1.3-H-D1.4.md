---
id: TASK-101.05
title: 'Doppio Teacher Variant Tests (H-D1.2, H-D1.3, H-D1.4)'
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
ordinal: 62000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add constraint verifier tests for doppio teacher conflict variants involving LabBlocks.

**Constraints to test:**
- H-D1.2: Doppio teacher conflict (Lesson-LabBlock)
- H-D1.3: Doppio teacher conflict (LabBlock-Lesson)
- H-D1.4: Doppio teacher conflict (LabBlock-LabBlock)

**Test file:** `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java`

**Pattern:** Each constraint gets two tests — one that triggers the violation (penalizes) and one that doesn't.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 doppioTeacherConflictLessonBlock_penalized / _notPenalized tests pass
- [ ] #2 doppioTeacherConflictBlockLesson_penalized / _notPenalized tests pass
- [ ] #3 doppioTeacherConflictBlockBlock_penalized / _notPenalized tests pass
<!-- AC:END -->
