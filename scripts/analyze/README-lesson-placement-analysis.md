# Lesson Placement Analysis

## Overview

This script analyzes where lessons are actually placed in the current solution and compares it against the expected placement based on each student group's ideal end date.

## What It Checks

For each student group, the script analyzes:

1. **Total lessons generated** - Sum of `Course.totalHours` (what should be scheduled)
2. **Total lessons assigned** - Lessons with both timeslot and room assigned
3. **Lessons before ideal end date** - Lessons placed on or before the group's `idealEndDate`
4. **Lessons after ideal end date** - Lessons placed AFTER the group's `idealEndDate` (overflow)
5. **Date range** - Earliest and latest lesson dates
6. **Valid timeslots count** - Total available timeslots from start date to ideal end date

## When to Use

Use this script to:

- **Identify overflow lessons** - Lessons placed after a group's ideal end date
- **Verify solver compliance** - Check if the solver is respecting date constraints
- **Debug completion tracking** - Compare actual placement vs. completion API reports
- **Validate constraint effectiveness** - Check if M12 (overflow penalty) is working

## How to Run

### Prerequisites

1. Make sure the database is running:
   ```bash
   ./planner.sh start <profile>
   ```

2. Install Python dependencies (first time only):
   ```bash
   pip install psycopg2-binary
   ```

### Execution

```bash
python3 scripts/analyze/lesson-placement-analysis.py
```

### Expected Output

The script produces a tabular report:

```
====================================================================================================================
LESSON PLACEMENT ANALYSIS REPORT
====================================================================================================================
Generated: 2026-03-23 10:30:45

Group           Total    Assigned  Unassigned  Before  After   Valid Slots  Start        Ideal End    Actual Range
--------------------------------------------------------------------------------------------------------------------
G03          682      682       0           682     0       912          2024-09-02  2025-06-07  2024-09-02 to 2025-06-07
G07          680      680       0           680     0       912          2024-09-02  2025-06-07  2024-09-02 to 2025-06-07
G01          666      666       0           666     0       892          2024-09-02  2025-06-07  2024-09-02 to 2025-06-07
...
--------------------------------------------------------------------------------------------------------------------
TOTALS          8753     8753      0           -       0       -            GROUPS WITH OVERFLOW:      0/14
====================================================================================================================
```

## Understanding the Output

### Columns

- **Group**: Student group name
- **Total**: Total hours to schedule (from `Course.totalHours`)
- **Assigned**: Lessons actually assigned (with timeslot + room)
- **Unassigned**: Lessons not yet assigned (`Total - Assigned`)
- **Before**: Lessons on or before `idealEndDate`
- **After**: Lessons AFTER `idealEndDate` (⚠️ indicates overflow)
- **Valid Slots**: Total available timeslots from start to ideal end date
- **Start**: Group's start date
- **Ideal End**: Group's ideal end date
- **Actual Range**: Date range of assigned lessons

### Overflow Section

If any lessons are placed after `idealEndDate`, a detailed section shows:

```
⚠️  DETAILED OVERFLOW ANALYSIS
--------------------------------------------------------------------------------------------------------------------
G01: 15 lessons after ideal end date
  Ideal end date: 2025-06-07
  Overflow range: 2025-06-08 to 2025-06-14
  Overflow extends 7 days beyond ideal end
```

### Exit Codes

- **0**: All lessons assigned within valid date ranges
- **1**: Issues detected (overflow or unassigned lessons)

## Common Issues and Solutions

### Issue: Lessons After Ideal End Date

**Symptom**: The "After" column shows non-zero values

**Possible Causes**:
1. **Insufficient capacity** - Not enough valid timeslots before ideal end date
2. **Weak M12 constraint** - Overflow penalty (M12) not strong enough
3. **Hard constraint conflicts** - Other hard constraints force overflow

**Investigation Steps**:
1. Check if `Valid Slots < Total` (insufficient capacity)
2. Verify M12 constraint weight in `SchoolScheduleConstraintProvider.java`
3. Check for constraint violations forcing lessons into overflow periods

**Solution**:
- Increase M12 constraint weight (currently 1soft per hour)
- Add hard constraint to forbid lessons after ideal end date
- Increase valid timeslots (extend school year, reduce holidays)

### Issue: Unassigned Lessons

**Symptom**: The "Unassigned" column shows non-zero values

**Possible Causes**:
1. **Solver hasn't finished** - Solver is still running
2. **Infeasible problem** - No valid solution exists
3. **Constraint conflicts** - Hard constraints prevent assignment

**Investigation Steps**:
1. Check solver status: `./scripts/api/api.sh status`
2. Check for hard constraint violations: `./scripts/api/constraint-violations.sh`
3. Review solver logs for infeasibility

**Solution**:
- Wait for solver to finish
- Relax or remove conflicting constraints
- Increase solver time limit

### Issue: Valid Slots < Total Hours

**Symptom**: A group has fewer valid timeslots than total hours to schedule

**Example**:
```
G01: 666 total hours, but only 600 valid slots
```

**This means**: Overflow is inevitable - there's no way to fit all lessons before ideal end date

**Solution**:
- Extend the ideal end date for this group
- Reduce total hours (curriculum change)
- Add more timeslots per day (extend school hours)

## Integration with Other Tools

### Compare with Completion API

The completion API endpoint (`GET /api/student-groups/completion`) reports completion percentage. Compare:

1. Run this script to get actual placement data
2. Call the completion API: `./scripts/api/api.sh get /api/student-groups/completion | jq`
3. Verify that the API's "assigned" count matches this script's "Assigned" count

### Check Constraint Violations

If overflow is detected, check what's causing it:

```bash
# Get all constraint violations
./scripts/api/constraint-violations.sh

# Focus on M12 (overflow penalty)
./scripts/api/constraint-violations.sh M12
```

### View Specific Lessons in Overflow

To see which specific lessons are overflowing for a group:

```sql
SELECT c.subject, t.date, ts.slotindex
FROM lesson l
JOIN course c ON l.course_id = c.id
JOIN timeslot t ON l.timeslot_id = t.id
JOIN timeslot ts ON l.timeslot_id = ts.id
WHERE c.studentgroup_id = (SELECT id FROM studentgroup WHERE name = 'G01')
AND t.date > (SELECT idealenddate FROM studentgroup WHERE name = 'G01')
ORDER BY t.date, ts.slotindex;
```

## Technical Details

### Database Schema

The script queries these tables:

- `studentgroup` - Group metadata (start date, ideal end date)
- `course` - Course definitions with total hours
- `lesson` - Individual lesson assignments
- `timeslot` - Date/time slot definitions
- `schoolholiday` - Holiday exclusions

### Valid Timeslots Calculation

A "valid timeslot" is:
- On or after the group's start date
- On or before the group's ideal end date
- Not a school holiday
- Has a slot index (0-5, representing 6 daily periods)

### Overflow Detection

Overflow is detected when a lesson's `timeslot.date > group.idealenddate`. This means:

- The lesson is scheduled for a date after the group's ideal end
- This violates the school's scheduling requirement
- The M12 constraint should penalize this (but may not be strong enough)

## Troubleshooting

### Script fails with "connection refused"

**Cause**: Database is not running

**Fix**:
```bash
./planner.sh start <profile>
```

### Script fails with "psycopg2 not installed"

**Cause**: Missing Python dependency

**Fix**:
```bash
pip install psycopg2-binary
```

### Output shows all zeros

**Cause**: No lessons assigned yet (solver hasn't run)

**Fix**:
```bash
# Start the solver
./scripts/api/api.sh solve

# Wait for completion, then re-run analysis
./scripts/api/api.sh wait
python3 scripts/analyze/lesson-placement-analysis.py
```

## Related Scripts

- `./scripts/db/db-stats.py` - General database statistics
- `./scripts/analyze/g13-detailed-analysis.py` - G13 group-specific analysis
- `./scripts/api/constraint-violations.sh` - Check constraint violations
- `./scripts/api/api.sh` - General API interaction
