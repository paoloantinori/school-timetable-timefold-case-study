---
id: TASK-229
title: Fix disabled tests after TASK-228 LabBlock → Lesson refactoring
status: Done
assignee: []
created_date: '2026-03-13 14:07'
updated_date: '2026-03-13 14:17'
labels:
  - testing
  - TASK-228
  - debt
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Three test files were temporarily disabled during TASK-228 (LabBlock denormalization) due to API changes from LabBlock to LabSession:

**Files to restore and fix:**
1. `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java.bak`
2. `src/test/java/com/school/planner/service/GreedyRotationAssignerTest.java.bak`
3. `src/test/java/com/school/planner/service/RotationTestRunnerTest.java.bak`

**Issues:**
- Constraint methods were consolidated (Block/Lesson variants merged)
- LabSession replaced LabBlock in pre-assignment APIs
- Constructor signatures changed (SchoolSchedule: 12 params → 10 params)
- Method references need updating (e.g., `stageBlackoutBlock` → `stageBlackout`)

**Acceptance Criteria:**
- Restore all three .bak files to their original locations
- Update SchoolScheduleConstraintProviderTest: Fix method references to match consolidated constraints
- Update GreedyRotationAssignerTest: Replace LabBlock with LabSession usage
- Update RotationTestRunnerTest: Replace LabBlock with LabSession usage
- All tests compile and pass with `mvn test`
- Test coverage restored to pre-refactoring levels
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Fixed all three disabled tests after TASK-228 LabBlock → Lesson refactoring:

**Changes made:**
1. **SchoolScheduleConstraintProviderTest.java** - Removed obsolete tests for split constraint methods (`teacherConflictLessonBlock`, `teacherConflictBlockBlock`, `roomConflictBlockBlock`, etc.) that were consolidated into single unified methods (`teacherConflict`, `roomConflict`, `studentGroupConflict`). Updated test expectations (H15 penalizes by gap minutes, not count).

2. **GreedyRotationAssignerTest.java** - Updated to use `LabSession` instead of `LabBlock`. Changed `Map<BlockKey, List<LabBlock>>` to `Map<BlockKey, List<LabSession>>`. Added helper method `createSession()` that creates placeholder lessons without timeslots (assigned during placement).

3. **RotationTestRunnerTest.java** - Updated to use `LabSession` instead of `LabBlock`. Changed `Map<BlockKey, List<LabBlock>>` to `Map<BlockKey, List<LabSession>>`. Added `createSession()` helper method.

**Key API changes from TASK-228:**
- Constraint methods consolidated: split block/lesson variants merged into single methods with `isSameLabSession()` filter
- `LabSession` service-level wrapper replaces direct `LabBlock` JPA entity usage in pre-assignment
- `LabSession` contains placeholder `Lesson` entities that get timeslots assigned during placement

**Test results:**
- SchoolScheduleConstraintProviderTest: 23 tests, 0 failures
- GreedyRotationAssignerTest: 7 tests, 0 failures
- RotationTestRunnerTest: 9 tests, 0 failures
<!-- SECTION:FINAL_SUMMARY:END -->
