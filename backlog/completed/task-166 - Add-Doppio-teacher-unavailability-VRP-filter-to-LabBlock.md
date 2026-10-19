---
id: TASK-166
title: Add Doppio teacher unavailability VRP filter to LabBlock
status: Done
assignee: []
created_date: '2026-03-04 09:30'
updated_date: '2026-03-04 10:38'
labels:
  - claude-planned
  - solver-optimization
  - vrp
dependencies: []
references:
  - 'src/main/java/com/school/planner/domain/LabBlock.java:118-137'
  - src/main/java/com/school/planner/domain/SchoolSchedule.java
  - src/main/java/com/school/planner/solver/BaseConstraintProvider.java (H-D1)
priority: medium
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: Some lab sessions have a `doppioTeacher` (co-teacher) who must be present. Constraint H-D1 prevents scheduling conflicts for the doppio teacher. Currently the VRP in LabBlock only filters by the PRIMARY teacher's availability, not the doppio teacher's.

**Opportunity**: Extend `LabBlock.getAvailableStartTimeslots()` to also filter out timeslots where the doppio teacher is unavailable (VACATION/SICK records).

**Impact Analysis**:
- Affects LabBlocks where `course.doppioTeacher != null`
- Known doppio teachers: T03 (termoidraulica), T10 (meccanica), T14 (moda), T18 (acconciatura)
- Impact varies by teacher — external teachers with many VACATION records (like T28-style patterns) would see biggest reduction
- Estimated 5-10% timeslot reduction for affected LabBlocks

**Implementation approach**:
1. In LabBlock.getAvailableStartTimeslots(), check if `course.doppioTeacher` exists
2. If so, also filter by doppio teacher's availability using the existing `isTeacherUnavailable()` method
3. Use same static `teacherAvailabilities` map already loaded in SchoolSchedule
4. Keep H-D1 constraint as safety net

**Note**: This only filters by TeacherAvailability (VACATION/SICK). The full H-D1 constraint also prevents lesson-vs-block conflicts at solve time, which cannot be pre-filtered.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 LabBlock.getAvailableStartTimeslots() filters by doppio teacher availability when course.doppioTeacher is set
- [ ] #2 Uses existing isTeacherUnavailable() method and static teacherAvailabilities map
- [ ] #3 H-D1 constraint remains as safety net
- [ ] #4 All existing tests pass
- [ ] #5 Log shows reduced start timeslot counts for affected LabBlocks
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added doppio teacher unavailability VRP filter to both `LabBlock.getAvailableStartTimeslots()` and `Lesson.getAvailableTimeslots()`. Reuses existing `isTeacherUnavailable()` helper — null-safe for courses without doppio teacher (no-op). All 193 tests pass. Commit: 185046d.
<!-- SECTION:FINAL_SUMMARY:END -->
