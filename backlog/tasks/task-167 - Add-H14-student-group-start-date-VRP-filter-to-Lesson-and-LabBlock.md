---
id: TASK-167
title: Add H14 student group start date VRP filter to Lesson and LabBlock
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
  - 'src/main/java/com/school/planner/domain/StudentGroup.java:startDate field'
  - >-
    src/main/java/com/school/planner/solver/BaseConstraintProvider.java (H14.1,
    H14.2)
priority: low
ordinal: 29750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: Constraint H14 prevents lessons from being scheduled before a student group's `startDate`. Currently enforced only as a solver constraint (H14.1 for Lesson, H14.2 for LabBlock). Could be pre-filtered in VRP.

**Opportunity**: Add entity-level VRP filtering to exclude timeslots before the student group's start date.

**Impact Analysis**:
- Currently LOW impact — no student groups have `startDate` set in DataGenerator seed data
- The field exists on StudentGroup but defaults to null (meaning "no restriction")
- Would become impactful if/when late-joining groups are added (e.g., G12 entering February)
- When active, could eliminate ~300-500 timeslots per affected group (Sept-Jan = ~40% of year)

**Implementation approach**:
1. In Lesson.getAvailableTimeslots() and LabBlock.getAvailableStartTimeslots(), add filter:
   `.filter(ts -> startDate == null || !ts.getDate().isBefore(startDate))`
2. Get startDate from `getStudentGroup().startDate`
3. Keep H14.1/H14.2 constraints as safety net

**Note**: This is a future-proofing optimization. Only implement when student groups with startDate are actually added to seed data. Low priority since the constraint fires 0 times currently.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Lesson.getAvailableTimeslots() filters timeslots before studentGroup.startDate
- [ ] #2 LabBlock.getAvailableStartTimeslots() filters timeslots before studentGroup.startDate
- [ ] #3 Filter is no-op when startDate is null (current default)
- [ ] #4 H14.1 and H14.2 constraints remain as safety net
- [ ] #5 All existing tests pass
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Low impact optimization - no student groups have startDate set in DataGenerator. Task itself notes this is "future-proofing" only. Revisit if late-joining groups are added to seed data.
<!-- SECTION:FINAL_SUMMARY:END -->
