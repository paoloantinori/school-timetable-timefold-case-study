---
id: TASK-187
title: 'TASK-187: Fix ImportService DayOfWeek Constraint Violation'
status: Done
assignee:
  - Claude
created_date: '2026-03-06 22:42'
updated_date: '2026-03-09 19:50'
labels:
  - bug
  - import-export
  - claude-planned
dependencies: []
priority: high
ordinal: 95000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
# Import fails when schedule contains SUNDAY stage patterns

## Problem
Import fails with error:
```
ERROR: new row for relation "stagepattern_dayofweekmask" violates check constraint
Detail: Failing row contains (stagepattern_id, dayofweekmask)=(7, 7)
```

## Root Cause
**Schema/Data Mismatch:**
- Database constraint: `CHECK (dayofweekmask >= 0 AND dayofweekmask <= 6)`
- Java `DayOfWeek.getValue()`: Returns 1-7 (MONDAY=1, SUNDAY=7)
- When exported schedule contains SUNDAY patterns, import fails because SUNDAY=7 violates the 0-6 constraint

## Solutions

### Option A: Fix the database constraint (RECOMMENDED)
```sql
ALTER TABLE stagepattern_dayofweekmask 
DROP CONSTRAINT stagepattern_dayofweekmask_dayofweekmask_check;

ALTER TABLE stagepattern_dayofweekmask 
ADD CONSTRAINT stagepattern_dayofweekmask_check 
CHECK (dayofweekmask >= 1 AND dayofweekmask <= 7);
```

### Option B: Fix ImportService to skip invalid days
In `insertStagePatternDayMask()`, filter out SUNDAY:
```java
for (DayOfWeek dow : p.dayOfWeekMask) {
    int value = dow.getValue(); // 1-7
    if (value == 7) {
        LOG.warnf("Skipping SUNDAY (constraint allows 0-6 only) for stagepattern %d", p.id);
        continue;
    }
    // ... insert
}
```

## Recommendation
**Option A** is correct - fix the constraint to match Java's DayOfWeek values (1-7).

## Files Affected
- `src/main/java/com/school/planner/service/ImportService.java` (Option B workaround)
- Database migration needed for Option A

## Workaround (Immediate)
If user needs to import immediately, they can:
1. Edit export file to remove SUNDAY from stagePatterns
2. Or run the SQL fix in Option A manually
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Fix Applied

**Root Cause (Corrected):** The bug was in ImportService, not the database.

- `DayOfWeek.getValue()` returns ISO-8601 values (1-7, where SUNDAY=7)

- `DayOfWeek.ordinal()` returns enum ordinal (0-6, where SUNDAY=6)

- Hibernate stores enums as ordinals by default, so the database constraint `CHECK (0-6)` is CORRECT

**Fix:** Changed line 418 in `ImportService.java`:

```java

// Before:

ps.setInt(2, dow.getValue()); // 1=MONDAY, 7=SUNDAY

// After:

ps.setInt(2, dow.ordinal()); // 0=MONDAY, 6=SUNDAY (Hibernate ordinal storage)

```

No database migration required. The existing schema is correct.
<!-- SECTION:FINAL_SUMMARY:END -->
