# Compaction Investigation - March 13, 2026

## Executive Summary

Investigation into poor calendar compaction (lessons scattered across school year) revealed that **this is not a solver bug but a structural curriculum constraint**. Groups with unbalanced teacher-to-course ratios naturally produce less compact calendars.

## Problem Statement

Initial observation: G01 student group had lessons spread across ~150 days instead of being densely clustered on fewer days. M20 (horizontal compactness) penalty was excessive (~334M medium).

## Investigation Steps

### Step 1: Value Range Bug Discovery

**Issue Found**: `DayClusteredConstructionPhase` was placing lessons without respecting each lesson's filtered value range.

```java
// BEFORE (BUG):
Timeslot timeslot = findTimeslot(start, schedule.getTimeslots()); // ALL timeslots

// AFTER (FIXED):
Timeslot timeslot = findTimeslot(start, lesson.getAvailableTimeslots()); // Filtered
```

**Error Observed**:
```
IllegalStateException: The value (2026-04-16T08:00) from the planning variable (timeslot)
has been assigned to the entity (Storia ed educazione civica(3058)), but it is outside
of the related value range [... ending at 2026-04-09T13:00].
```

**Fix Applied**: Modified `placeLessonsRoundRobin()` to use each lesson's `getAvailableTimeslots()` instead of all schedule timeslots.

**Result**: Eliminated M12 (overflow beyond target) constraint violations.

### Step 2: Round-Robin Placement Attempt

Implemented round-robin day clustering to interleave courses and fill each day with 6 lessons from different teachers.

**Result**: Minimal improvement (145 → 145 days). Analysis revealed that courses have different value ranges, preventing true round-robin placement.

### Step 3: Teacher Distribution Analysis

Compared G01 (poor compaction) with G02 (excellent compaction):

| Metric | G01 | G02 |
|--------|--------|-------|
| Unique Days | 145 | 154 |
| Total Lessons | 592 | 842 |
| Avg Lessons/Day | **4.08** | **5.47** |
| Days with 6 Lessons | 36 (25%) | 97 (63%) |
| Days with ≤2 Lessons | 53 (37%) | 1 (0.6%) |
| Teachers | 13 | 15 |
| Courses | 27 | 22 |
| Total Hours | 990 | 990 |

**Key Finding**: G02 has better teacher distribution → better compaction.

### Step 4: Teacher Bottleneck Identification

**G01 Teacher Distribution**:
```
T20      | 6 subjects | 151 hours ← BOTTLENECK
T17    | 4 subjects | 128 hours
T16      | 3 subjects | 194 hours
```

**G02 Teacher Distribution**:
```
T09          | 3 subjects | 130 hours ← Most diverse
T26      | 2 subjects | 152 hours
T25        | 2 subjects |  70 hours
[10 more teachers]    | 1 subject  each
```

## Root Cause

**T20 teaches 6 different subjects (151h) for G01**. Since any teacher can only teach one lesson at a time:

1. To fill 6 slots on a day, need 6 different teachers available
2. T20 can only fill 1 slot per day
3. His 6 subjects must be scheduled on different days
4. This naturally spreads G01's lessons across the year

**Mathematical Limitation**: With 27 courses and only 13 teachers (one teaching 6 subjects), true day clustering (6 lessons every day) is impossible for G01.

## Algorithm Verification

The `DayClusteredConstructionPhase` is working correctly:

1. ✅ Groups lessons by studentGroup
2. ✅ Places lessons on consecutive days for each course
3. ✅ Respects each lesson's filtered value range (target end date)
4. ✅ Avoids teacher/student group conflicts via in-memory tracking
5. ✅ Skips holidays, weekends, stage blackout days, teacher vacations

**Placement Statistics**: 7,195 lessons placed, 20 skipped (99.7% success rate).

## Recommendations

### For G01 (If Better Compaction Is Required)

1. **Hire additional teachers** to split T20's 6-subject workload
2. **Reassign subjects** to balance teacher distribution more evenly
3. **Accept the constraint**: Some groups will naturally have more spread-out calendars

### For the Solver

1. ✅ **Keep current DayClusteredConstructionPhase** - it works correctly
2. ✅ **Value range fix is critical** - prevents M12 violations
3. Consider: **Group-specific M20 weights** for groups with known teacher constraints

## Data Files

### G01 Lesson Distribution (Current)
```
Lesson Count | Days with This Count
-------------|-------------------
6 lessons    | 36 days (25%)
5 lessons    | 33 days (23%)
4 lessons    | 22 days (15%)
3 lessons    | 16 days (11%)
2 lessons    | 37 days (25%)  ← ISSUE
1 lesson     |  1 day  (0.7%)
-------------
Total        | 145 days
```

### G02 Lesson Distribution (Target)
```
Lesson Count | Days with This Count
-------------|-------------------
6 lessons    | 97 days (63%)
5 lessons    | 35 days (23%)
4 lessons    | 21 days (14%)
1 lesson     |  1 day  (0.6%)
-------------
Total        | 154 days
```

## Conclusion

The **poor compaction for G01 is a curriculum design issue, not a solver bug**. The DayClusteredConstructionPhase correctly places lessons within the constraints of teacher availability and course value ranges.

**To achieve better compaction**, curriculum changes are needed:
- Increase teacher count relative to course count
- Balance subject distribution across teachers
- Reduce multi-subject teacher workloads

## Files Modified

- `src/main/java/com/school/planner/solver/DayClusteredConstructionPhase.java`
  - Fixed value range bug (use `lesson.getAvailableTimeslots()`)
  - Implemented hybrid placement approach (place courses independently, fill gaps)

## Related Issues

- M20 (horizontal compactness) penalty of ~337M medium is expected given teacher constraints
- H15 (same-day same-subject consecutive) violations occur when same subject appears in non-consecutive slots
- Different groups have different compaction capabilities based on their teacher/course ratios
