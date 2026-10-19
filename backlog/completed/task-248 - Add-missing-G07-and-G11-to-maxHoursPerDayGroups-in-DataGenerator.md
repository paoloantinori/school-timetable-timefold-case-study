---
id: TASK-248
title: Add missing G07 and G11 to maxHoursPerDayGroups in DataGenerator
status: Done
assignee: []
created_date: '2026-03-16 10:48'
updated_date: '2026-03-16 10:59'
labels:
  - bug
  - datagenerator
  - seed-data
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

The `maxHoursPerDayGroups` array in `DataGenerator.java` is missing G07 and G11. These groups should be on a 5-hour calendar but are currently not configured, so they default to 6 hours.

## Root Cause

Lines 340-345 in `DataGenerator.java` define which groups have `maxHoursPerDay = 5`:

```java
String[][] maxHoursPerDayGroups = {
        {"G08", "5"},
        {"G13", "5"},
        {"G10", "5"},  // T28 exception: allows 6h on T28 Thursdays
        {"4 FA", "5"}   // T28 exception: allows 6h on T28 Thursdays
};
```

G07 and G11 are missing from this list.

## Fix

Add G07 and G11 to the `maxHoursPerDayGroups` array:

```java
String[][] maxHoursPerDayGroups = {
        {"G08", "5"},
        {"G13", "5"},
        {"G07", "5"},  // Added: 5-hour calendar
        {"G11", "5"},  // Added: 5-hour calendar (possible last-month exception)
        {"G10", "5"},  // T28 exception: allows 6h on T28 Thursdays
        {"4 FA", "5"}   // T28 exception: allows 6h on T28 Thursdays
};
```

## Notes

- **FB last-month exception:** G07 and G11 have a possible future exception to do more hours in the last month of school, but for normal scheduling they should be assumed at 5 hours.
- After this fix, a database profile rebuild will be required (`./planner.sh delete` then `./planner.sh start`).

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Given DataGenerator.java is modified with G07 and G11 added to maxHoursPerDayGroups, when a new profile is created, then both groups have maxHoursPerDay=5
- [ ] #2 #2 Given an existing profile, after deleting and recreating it, then G07 and G11 are constrained to 5 hours/day by H11
- [ ] #3 #3 Given the fix is applied, verify that H18 (when implemented in TASK-247) correctly enforces slot 0 start for G07 and G11
<!-- SECTION:DESCRIPTION:END -->
<!-- AC:END -->
