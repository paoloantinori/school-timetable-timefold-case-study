---
id: TASK-276
title: Exclude G14 student group from solver scheduling
status: Done
assignee: []
created_date: '2026-03-20 04:52'
updated_date: '2026-07-27 04:48'
labels:
  - claude-planned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
G14 is a special student group (12 students) with only 132 total hours that should not be scheduled by the solver. Currently all 132 lessons are included in the solving process, contributing to the problem scale (10,550 entities) and hard constraint violations when they can't be placed within their tight ideal window (Oct 17, only 28 available school days).

## Context
G14 has 6 courses: R16 (60h), Informatica (18h), Orientamento (18h), Diritto del lavoro (16h), ELIA (12h), SIC (8h). With an ideal end date of 2025-10-17, only ~18 lessons can fit in 28 school days. The remaining 114 lessons stay unassigned and contribute to hard score penalties.

## Requirements
- G14 lessons should be excluded from the solver's entity set entirely
- G14 should still be visible in the UI (as a column) for informational purposes
- Pre-assignment should not attempt to place G14 lessons
- The solver should not count G14 lessons in its problem scale

## Possible Approaches
1. **Filter in ScheduleService**: When building the SchoolSchedule for solving, exclude G14 lessons from the entity list
2. **Mark as excluded on StudentGroup**: Add a boolean flag (e.g., `excludedFromSolving`) to StudentGroup, filter in Lesson entity selector
3. **Remove G14 courses**: If G14 scheduling is handled entirely outside this system, remove the courses from the data

## G14 Courses (for reference)
| Subject | Hours | Teacher |
|---------|-------|---------|
| R16 | 60 | T16 |
| Informatica | 18 | T06 |
| Orientamento | 18 | T13 |
| Diritto del lavoro | 16 | T23 |
| ELIA | 12 | T16 |
| SIC | 8 | T15 |
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 G14 lessons are excluded from solver entity set
- [ ] #2 Solver problem scale decreases by 132 entities (from 10,550 to 10,418)
- [ ] #3 No hard constraint violations caused by unassigned G14 lessons
- [ ] #4 G14 remains visible in UI timeline as an informational column
- [ ] #5 Pre-assignment pipeline skips G14
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SUPERATO (2026-07-27): stesso esito di TASK-221 - esenzione M20/M33 adottata al posto dell'esclusione; il nodo dati (990h foglio vs 115h incarichi) e' in TASK-428. Archiviato.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
LIKELY_FIXED (triaged 2026-07-15): EXCLUDED_GROUPS=List.of(G14) in ScheduleService:140. G14 courses/group/rotations commented out in DataGenerator.
<!-- SECTION:FINAL_SUMMARY:END -->
