---
id: TASK-221
title: 'TASK-222: Exclude G14 from solver planning'
status: Done
assignee: []
created_date: '2026-03-10 05:36'
updated_date: '2026-07-27 04:48'
labels:
  - claude-planned
  - documentation
  - data-model
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Background

G14 is a short course with fixed dates (Sep-Oct, ~180h), not fully bound to the main planning rules. It primarily affects the rest of the planning through:
1. Lab access (G14 uses labs like Lab Termoidraulico, R15)
2. Teacher double-booking (G14 teachers: T13, T16, T03, T15, T23, T06)

## Implementation

Added `EXCLUDED_GROUPS` filter in `ScheduleService.loadSchedule()` (similar to existing `EXCLUDED_ROOMS`):

```java
private static final List<String> EXCLUDED_GROUPS = List.of("G14");
```

Filters applied:
- Lessons: Filter out where `course.studentGroup.name == "G14"`
- LabBlocks: Filter out where `course.studentGroup.name == "G14"`

## Rationale

By excluding G14 from the planning solution, the solver:
- Focuses on the core student groups (FB, FA, FD, MODA, FC, G13)
- Reduces search space (~180 fewer lessons to schedule)
- Avoids hard constraint violations from G14's special scheduling needs

G14 teachers are still checked for conflicts with other groups (H1: Teacher conflict constraint).

## Reverting

To re-enable G14 planning:
1. Remove "G14" from `EXCLUDED_GROUPS` list
2. Rebuild: `./scripts/rebuild-quick.sh`
3. G14 lessons will be included in solver

## Notes

- DataGenerator still creates G14 courses/lessons (no changes to seed data)
- G14 teachers, rooms, and courses remain in the database
- Only the planning entities (Lessons, LabBlocks) are filtered out
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SUPERATO (2026-07-27): l'approccio adottato non e' l'esclusione di G14 dal solver ma l'esenzione dai vincoli day-fill (M20/M33, task-010/Q16) + domanda alla scuola sul 990h-vs-115h (TASK-428). Duplicato di TASK-276. Archiviato.
<!-- SECTION:NOTES:END -->
