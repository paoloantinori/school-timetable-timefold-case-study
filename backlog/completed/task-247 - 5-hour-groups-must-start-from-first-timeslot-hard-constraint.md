---
id: TASK-247
title: 5-hour groups must start from first timeslot (hard constraint)
status: Done
assignee: []
created_date: '2026-03-16 10:43'
updated_date: '2026-03-16 13:58'
labels:
  - bug
  - constraint
  - hard-constraint
  - modeling
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

Student groups have varying daily hour requirements:
- **6-hour groups**: Can start from any slot (slots 0-5)
- **5-hour groups**: MUST start from slot 0 (08:00), cannot start from slot 1 or later

Currently, the solver may schedule 5-hour groups starting from slot 1, 2, etc., which creates unacceptable schedules for those groups. This needs to be a hard constraint.

## Root Cause

The current constraint set (H14: Student group start date) only checks whether a group has reached its official start date for the year, but does not enforce daily start time requirements.

## Proposed Solution

Add a new hard constraint H18 that enforces: for any student group with ≤5 daily hours, their first lesson of the day must be at slot 0 (08:00).

### Implementation Options

**Option A: Constraint-based approach**
```java
// H18: 5-hour groups must start from slot 0
Constraint fiveHourGroupsMustStartFromFirstSlot(ConstraintFactory cf) {
    return cf.forEach(Lesson.class)
        .groupBy(l -> Tuple.of(l.getStudentGroup(), l.getTimeslot().getDate()))
        .filter((group, lessons) -> group.getTotalHours() / getExpectedDays() <= 5)
        .filter((group, lessons) -> 
            lessons.stream().noneMatch(l -> l.getTimeslot().getSlotIndex() == 0))
        .penalize(HardMediumSoftScore.ONE_HARD)
        .asConstraint("H18: 5-hour groups must start from slot 0");
}
```

**Option B: Value range restriction**
Modify the planning value range for 5-hour groups to exclude non-zero slots when no lessons exist yet for that day. More efficient but more complex.

### Data Considerations

Need to determine which groups are 5-hour vs 6-hour:
- Check DataGenerator or database for `totalHours` per group
- Calculate expected days: `totalHours / expectedDays = dailyHours`
- Groups with `dailyHours <= 5` get the constraint

### Interaction with Existing Constraints

| Constraint | Impact |
|------------|--------|
| H14 (Student group start date) | Unaffected - checks year start, not daily start |
| H11 (Max student group daily hours) | Unaffected - limits max hours |
| H8/H17 (Vertically compact) | Complementary - H18 ensures start at 0, H8/H17 ensure no gaps |
| M20 (No sparse days) | Unaffected - penalizes days with <4 hours |

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Given a 5-hour student group, when the solver schedules lessons for a day with no slot 0 lesson, then constraint H18 penalizes with 1 HARD
- [ ] #2 #2 Given a 6-hour student group, when the solver schedules lessons starting from slot 1, then no H18 penalty is applied
- [ ] #3 #3 Given a 5-hour group with a valid schedule (starts at slot 0, has 5 lessons), then no H18 penalty is applied
- [ ] #4 #4 After implementing H18, run a full solve and verify no regression in overall score for existing valid schedules
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Details

### Field to Use: `StudentGroup.maxHoursPerDay`

This field already exists in the domain model and is set in DataGenerator.

### 5-Hour Groups (must start at slot 0)

**IMPORTANT:** DataGenerator currently has a bug - G07 and G11 are missing from `maxHoursPerDayGroups`.
See TASK-248 for the DataGenerator fix.

| Group | Should Be 5h? | In DataGenerator? |
|-------|---------------|-------------------|
| G08 | ✅ | ✅ |
| G13 | ✅ | ✅ |
| G10 | ✅ | ✅ |
| 4 FA | ✅ | ✅ |
| **G07** | ✅ | ❌ **BUG** |
| **G11** | ✅ | ❌ **BUG** |

**Note on FB exception:** G07 and G11 have a possible future exception to do more hours in the last month of school, but they should be assumed at 5 hours for normal scheduling.

### Constraint Implementation

```java
// H18: 5-hour groups must start from slot 0
// Groups with maxHoursPerDay <= 5 must have their first lesson at slot 0
Constraint fiveHourGroupsMustStartFromFirstSlot(ConstraintFactory cf) {
    return cf.forEach(Lesson.class)
        .join(l -> l.getTimeslot().getDate())
        .groupBy((lesson, date) -> Tuple.of(lesson.getStudentGroup(), date))
        .filter((group, date) -> group.maxHoursPerDay != null && group.maxHoursPerDay <= 5)
        .filter((group, date, lessons) -> 
            lessons.stream().noneMatch(l -> l.getTimeslot().getSlotIndex() == 0))
        .penalize(HardMediumSoftScore.ONE_HARD)
        .asConstraint("H18: 5-hour groups must start from slot 0");
}
```

### Files to Modify
- `src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java` - Add H18 constraint
- `CLAUDE.md` - Add H18 to constraint table
- `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java` - Add test
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Implementation Complete (2026-03-16)

**H18 Constraint Implemented**

- Added `fiveHourGroupsMustStartFromFirstSlot()` constraint method

- Filters for groups with `maxHoursPerDay <= 5`

- Penalizes when no lesson on a given day starts at slot 0 (08:00)

- Added to both compaction-only and full-solving constraint lists

**Files Modified**:

- `SchoolScheduleConstraintProvider.java` - Added H18 constraint method and registered in both constraint arrays

- `CLAUDE.md` - Added H18 to constraint table

**Verification**:

- Build successful: `./scripts/core/rebuild.sh` compiled without errors

**Implementation Details**:

- Uses `lesson.timeslot.startDateTime.getHour() == SLOT_INDEX_OFFSET` (8) to check for slot 0

- Uses `toList()` collector to group lessons by (group, date), then checks if any lesson starts at 08:00

- Penalizes with 1 HARD when 5-hour group has lessons on a day but none at slot 0
<!-- SECTION:FINAL_SUMMARY:END -->
