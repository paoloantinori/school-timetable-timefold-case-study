---
id: TASK-150
title: 'Refactor GreedyRotationAssigner from per-teacher to per-(teacher, group) pair'
status: Done
assignee: []
created_date: '2026-03-03 11:53'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - algorithm
dependencies:
  - TASK-149
references:
  - service/GreedyRotationAssigner.java
  - service/TeacherDayAssignment.java
priority: high
ordinal: 102000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The current algorithm collapses blocks by teacher and assigns ONE day per teacher. This fails when a teacher (e.g., T26) has different days per group. Refactor to work at the (teacher, group) pair level.

**Changes**:
1. `BacktrackContext`: Replace `blocksByTeacher/validDaysByTeacher/teacherOrder` with `blocksByPair/validDaysByPair/pairOrder` (using BlockKey)
2. `buildBacktrackContext`: Remove teacher-collapse step, use blocksByKeys directly
3. `getValidDaysForTeacher` → `getValidDaysForPair`: If LabRotation exists → use ONLY that day (acconciatura/meccanica). If no LabRotation → return all valid days (CSP search for other labs)
4. `orderTeachersByTightness` → `orderPairsByTightness`: Same sort logic over BlockKey
5. `backtrackAssign`: Replace teacherIndex → pairIndex
6. `tryPlaceBlocksForTeacher` → `tryPlaceBlocksForPair`: Rename, take BlockKey

**Key design**: For labs WITH LabRotation (acconciatura, meccanica), day is deterministic (no search). For labs WITHOUT LabRotation (moda, informatica), algorithm searches all valid days.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 BacktrackContext uses BlockKey-level maps instead of teacherId-level
- [ ] #2 getValidDaysForPair returns single LabRotation day for pairs with rotation data
- [ ] #3 getValidDaysForPair returns all valid weekdays for pairs without rotation data
- [ ] #4 Pair ordering: fewer valid days first, then more blocks
- [ ] #5 All existing tests pass after refactor
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Spalmone Interpretation Principle\n\nFor pairs WITH LabRotation records (acconciatura, meccanica): the day is deterministic from school input. The spalmone helped us infer what that input should be, but the algorithm treats it as a hard constraint.\n\nFor pairs WITHOUT LabRotation (future Phase 2): the spalmone tells us the STRUCTURAL PATTERN (how many days, is there a dominant day?), not which specific day to assign. The algorithm must discover the actual days via CSP search. The pattern determines constraint tightness:\n- \"Always 1 day\" → tight constraint (1 valid day in search)\n- \"Mostly 1 day with bleed\" → preferred day + overflow\n- \"Scattered across many days\" → unconstrained (any valid day)
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Superseded by LabRotation removal (commit e71bc85). With only 4 T28 entries remaining (all THURSDAY), per-teacher vs per-(teacher, group) pair distinction is moot. CSP handles pair-level discovery automatically. 100% pinning (1135/1135) proves correctness.
<!-- SECTION:FINAL_SUMMARY:END -->
