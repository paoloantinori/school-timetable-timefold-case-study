---
id: TASK-225
title: >-
  Investigate M13/M20 zero matches - compactness constraints not generating
  violations
status: Done
assignee: []
created_date: '2026-03-10 12:18'
updated_date: '2026-03-10 16:27'
labels:
  - bug
  - constraints
  - solver
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
M13 (Vertically compact) and M20 (Horizontally compact) constraints are showing 0 matches in the constraint analysis, despite having ~7,400 lessons assigned to timeslots.

Expected behavior:
- M13 should penalize gaps between consecutive lessons for each student group on each day
- M20 should penalize lessons scheduled too far from the start date
- With 7,400 lessons, there should be many violations

Current behavior:
- Constraints are correctly defined and pass tests
- Lessons are properly distributed across timeslots
- But M13 and M20 have 0 matches in constraint analysis

Investigation needed:
1. Check if lessons are actually distributed across dates (not just timeslots)
2. Verify the groupBy logic in M13 is working correctly  
3. Check if there's an issue with constraint evaluation during solving
4. May need to add debug logging or check solver internals
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Root Cause Found & Fixed

**Issue:** `forEach(Lesson.class)` was using Timefold's `isInitializedPredicate` which was filtering out ALL Lesson entities for constraint analysis, resulting in 0 matches for M13, M20, S1, and other Lesson-based constraints.

**Fix Applied:** Changed ALL `forEach(Lesson.class)` to `forEachIncludingUnassigned(Lesson.class)` in:
- `SchoolScheduleConstraintProvider.java` (M13, M20, S1, S2, S3, S4, S7, S8, S9, S10, S11, H7, H11)
- `BaseConstraintProvider.java` (H1.1, H2.1, H3.1, M-LA.1, M-LA.2, M3.1, M5, M12, M21, H-M2.1, H-M2.2, overflow, targetEndDate, roomSuitability)

**Results:**
- M13: 0 → **1,805 matches** (-987,500 medium)
- M20: 0 → **7,334 matches** (-71,536,100 medium)
- S1: 0 → **2,930 matches** (-248,160 soft)
- S2: 0 → **8,742 matches** (-17,484 soft)
- S4: 0 → **830 matches** (-249,000 medium)
- S7: 0 → **3,886 matches** (+11,658 soft)
- S8: 0 → **2,717 matches** (-54,340 soft)
- S9: 0 → **1,964 matches** (-31,300 soft)
- M-LA.1: 0 → **672 matches** (-672 medium)

**New Score:** -2,334hard/-101,175,207medium/-339,626soft (was -156hard/-1,199,935medium/0soft)

**Files Modified:**
- `src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java`
- `src/main/java/com/school/planner/solver/BaseConstraintProvider.java`
- `TASK-225-backlog.md` (investigation documentation)

**Technical Details:**
- `forEach()` uses `isInitializedPredicate` to filter entities based on planning variable initialization
- `forEachIncludingUnassigned()` bypasses this filter and includes all entities
- Manual `.filter(l -> l.timeslot != null)` applied after to exclude unassigned lessons
- The root cause of why `isInitializedPredicate` filtered all Lesson entities remains unknown (possibly Timefold bug or interaction with PanacheEntityBase/EAGER fetch)
<!-- SECTION:FINAL_SUMMARY:END -->
