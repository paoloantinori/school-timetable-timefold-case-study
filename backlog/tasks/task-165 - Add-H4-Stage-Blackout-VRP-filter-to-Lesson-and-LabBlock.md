---
id: TASK-165
title: Add H4 Stage Blackout VRP filter to Lesson and LabBlock
status: Done
assignee: []
created_date: '2026-03-04 09:30'
updated_date: '2026-03-10 05:16'
labels:
  - claude-planned
  - solver-optimization
  - vrp
dependencies: []
references:
  - 'src/main/java/com/school/planner/domain/Lesson.java:95-111'
  - 'src/main/java/com/school/planner/domain/LabBlock.java:118-137'
  - src/main/java/com/school/planner/domain/SchoolSchedule.java
  - >-
    src/main/java/com/school/planner/solver/BaseConstraintProvider.java (H4.1,
    H4.2)
priority: high
ordinal: 20750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: The H4 Stage Blackout constraint prevents non-professionalizzanti lessons from being scheduled during stage periods. Currently this is enforced ONLY as a solver constraint (H4.1 for Lesson, H4.2 for LabBlock), meaning the solver wastes move evaluations trying and rejecting these placements.

**Opportunity**: Add entity-level VRP filtering in `getAvailableTimeslots()` (Lesson) and `getAvailableStartTimeslots()` (LabBlock) to pre-exclude timeslots that fall on stage blackout dates for the entity's student group.

**Impact Analysis** (from database queries):
- ~2,091 non-professionalizzanti lessons across 7 student groups with stage patterns
- Stage blackout removes 125–284 timeslots per group (15–19% reduction)
- LabBlocks also affected: ~200 non-prof blocks across the same groups

**Implementation approach**:
1. Add static `Map<Long, Set<LocalDate>> stageBlackoutDates` to SchoolSchedule (keyed by studentGroup.id)
2. Populate in `SchoolSchedule.precomputeFilterData()` from StagePattern entities
3. Add filter in Lesson.getAvailableTimeslots(): skip timeslots where date is in stageBlackoutDates AND course is NOT professionalizzanti
4. Add same filter in LabBlock.getAvailableStartTimeslots()
5. Keep H4.1/H4.2 constraints as safety net (they should fire 0 times with correct VRP)

**Existing VRP pattern to follow**: Same static caching pattern used by targetEndDate, holidays (H6), and teacher availability (H5).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Lesson.getAvailableTimeslots() excludes stage blackout dates for non-professionalizzanti courses
- [ ] #2 LabBlock.getAvailableStartTimeslots() excludes stage blackout dates for non-professionalizzanti courses
- [ ] #3 SchoolSchedule has static stageBlackoutDates map populated from StagePattern entities
- [ ] #4 H4.1 and H4.2 constraints remain as safety net (not removed)
- [ ] #5 All existing tests pass (mvn test)
- [ ] #6 Solver XML validation passes (./scripts/validate-solver-xml.sh)
- [ ] #7 Log output shows reduced timeslot counts for affected groups (15-19% fewer)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Notes (from code exploration)

### Existing VRP Pattern (to follow exactly)
- Static fields on `SchoolSchedule.java` (6 existing: allTimeslots, allRooms, targetEndDates, teacherAvailabilities, schoolHolidays, timeslotSequence)
- Initialized in `ScheduleService.loadSchedule()` before solver starts
- Accessed by entity VRP methods via `SchoolSchedule.getXxxStatic()`
- Entity VRP methods cache results in `@Transient` fields (null-check pattern)

### Key Reusable Code
- `StagePattern.expandDates(holidays)` — expands date range + dayOfWeekMask into concrete `Set<LocalDate>`, excluding weekends and holidays
- Same `Map.merge()` pattern used for teacher availability grouping

### H4 Constraint Logic (for reference)
- H4.1 (Lesson): joins Lesson with StagePattern on studentGroup, checks date in range + dayOfWeekMask
- H4.2 (LabBlock): same join, checks if ANY occupied timeslot falls on blackout date, penalizes by count
- Both exempt `course.professionalizzanti == true`

### LabBlock Simplification
- VRP only filters `startTimeslot` — since blocks are intra-day, if start date is not blackout, all occupied timeslots are safe
- This is consistent with how H6.2 (holiday) VRP works for LabBlock

### Ordering Dependency
- `setStageBlackoutDatesStatic()` must come AFTER `setSchoolHolidaysStatic()` because `expandDates()` needs the holidays set to exclude holidays from stage dates
<!-- SECTION:NOTES:END -->
