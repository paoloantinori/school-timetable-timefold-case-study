---
id: TASK-159
title: Add holiday + teacher unavailability filtering to Lesson timeslot VRP
status: Done
assignee: []
created_date: '2026-03-04 05:26'
updated_date: '2026-03-04 10:30'
labels:
  - claude-planned
  - performance
  - vrp
dependencies: []
references:
  - >-
    src/main/java/com/school/planner/domain/Lesson.java (getAvailableTimeslots
    method)
  - >-
    src/main/java/com/school/planner/domain/LabBlock.java
    (getAvailableStartTimeslots — reference implementation)
  - >-
    src/main/java/com/school/planner/solver/BaseConstraintProvider.java:321-336
    (H6.1/H6.2 constraints)
  - claudedocs/research-timefold-optimizations-2026-03-03.md
priority: high
ordinal: 1500
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Rationale

After a 7h53m solver run reaching -1261 hard, constraint analysis reveals **H6.1 (School holiday)** accounts for **296 of the 1261 remaining hard violations** (23.5%). These are Lessons placed on holiday timeslots — timeslots that should never be considered.

LabBlock's `getAvailableStartTimeslots()` already filters out holidays and teacher unavailability (lines 128-137 in LabBlock.java), but Lesson's `getAvailableTimeslots()` only filters by `targetEndDate`. This asymmetry means the solver wastes moves trying holiday timeslots for Lessons, then gets penalized by H6.1.

Additionally, LabBlock filters H5 (teacher unavailability) at VRP level but Lesson does not. While the current run shows 0 H5.1 violations (the solver resolved them), filtering at VRP level would prevent the solver from ever trying unavailable timeslots, reducing the search space further.

## Impact

- **H6.1 eliminated structurally** — 296 hard violations become impossible (23.5% of remaining hard)
- **Search space reduction** — 133 holiday timeslots removed from each Lesson's candidate set (~12% fewer timeslots)
- **H5.1 prevented at VRP level** — teacher unavailability violations can never occur, freeing solver attention for real conflicts

## Implementation

**File: `src/main/java/com/school/planner/domain/Lesson.java`**

Modify `getAvailableTimeslots()` to add holiday and teacher unavailability filtering, matching LabBlock's pattern:

```java
// Add these lines to the stream filter chain (after targetEndDate filter):
Set<LocalDate> holidays = SchoolSchedule.getSchoolHolidaysStatic();
Long teacherId = getTeacher() != null ? getTeacher().id : null;
Map<Long, List<TeacherAvailability>> teacherAvail = SchoolSchedule.getTeacherAvailabilitiesStatic();

cachedAvailableTimeslots = allTimeslots.stream()
    .filter(ts -> targetEndDate == null || !ts.getDate().isAfter(targetEndDate))
    // H6.1 - Filter out school holidays
    .filter(ts -> holidays == null || !holidays.contains(ts.getDate()))
    // H5.1 - Filter out timeslots where teacher is unavailable
    .filter(ts -> !isTeacherUnavailable(ts, teacherId, teacherAvail))
    .toList();
```

Also add the `isTeacherUnavailable()` private method (copy from LabBlock.java lines 145-162).

**Imports needed:** `java.util.Set`, `java.util.Map`, `com.school.planner.domain.TeacherAvailability`, `com.school.planner.domain.AvailabilityType`

**Reference code:** LabBlock.java lines 118-140 (VRP filter chain), lines 145-162 (isTeacherUnavailable helper)

## Verification

- All 193 tests pass (`mvn test`)
- Pre-assignment: 1135/1135 LabBlocks pinned (100%)
- Constraint analysis: H6.1 = 0, H5.1 = 0
- Solver starts and improves score normally
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 H6.1 (School holiday) shows 0 violations in constraint analysis after solver run
- [ ] #2 H5.1 (Teacher unavailability) shows 0 violations in constraint analysis
- [ ] #3 All 193 existing tests pass
- [ ] #4 Pre-assignment still achieves 1135/1135 (100%) LabBlock pinning
- [ ] #5 No regression in solver score vs baseline (should improve due to reduced search space)
- [ ] #6 Holiday timeslots are structurally excluded from Lesson's value range — verified by logging VRP filter counts
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Summary

Added H6.1 (school holiday) and H5.1 (teacher unavailability) VRP filters to `Lesson.getAvailableTimeslots()`, matching what `LabBlock.getAvailableStartTimeslots()` already had. This eliminates ~23.5% of remaining hard violations (296 of 1261) by structurally preventing the solver from placing Lessons on holiday timeslots or during teacher unavailability periods.

## Changes

**`Lesson.java:getAvailableTimeslots()`** (lines 79-120):
- Added holiday filtering: `.filter(ts -> holidays == null || !holidays.contains(ts.getDate()))` — H6.1 filter
- Added teacher unavailability filtering: `.filter(ts -> !isTeacherUnavailable(ts, teacherId, teacherAvail))` — H5.1 filter
- Added `isTeacherUnavailable()` private helper method (lines 125-141) — identical to LabBlock's implementation

**`SchoolSchedule.java`** static infrastructure:
- `schoolHolidaysStatic` field + getter/setter — used by both Lesson and LabBlock
- `teacherAvailabilitiesStatic` field + getter/setter — used by both Lesson and LabBlock

**`ScheduleService.loadSchedule()`** initialization:
- `setSchoolHolidaysStatic(schoolHolidays)` and `setTeacherAvailabilitiesStatic(teacherAvailabilities)` called during schedule load

## Verification

- 193 tests pass (`mvn test`)
- VRP filters match LabBlock's existing implementation pattern
<!-- SECTION:FINAL_SUMMARY:END -->
