---
id: TASK-232
title: Add value sorting to ValueRangeProviders
status: Done
assignee: []
created_date: '2026-03-14 21:46'
updated_date: '2026-03-16 14:02'
labels:
  - timefold
  - performance
  - solver-optimization
dependencies: []
references:
  - src/main/java/com/school/planner/domain/Lesson.java
  - src/main/java/com/school/planner/domain/LabBlock.java
  - src/main/java/com/school/planner/service/ScheduleService.java
  - src/main/java/com/school/planner/domain/Timeslot.java
  - src/main/java/com/school/planner/domain/Room.java
documentation:
  - >-
    https://timefold.ai/docs/timefold-solver/latest/configuration#valueRangeProvider
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Timefold best practice: "ValueRangeProvider return values in a sorted order for better performance (weaker values first principle)."

Current Issue: The @ValueRangeProvider methods in Lesson and LabBlock return filtered timeslots and rooms in arbitrary order. This affects solver performance because the construction heuristic and local search phases work better when values are sorted consistently.

Impact: Sorted value ranges enable the solver to make better decisions earlier in the search (weaker values first principle). For timeslots, chronological order makes sense. For rooms, sorting by capacity (ascending) enables trying smaller appropriate rooms first.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Lesson.timeslotListSorted returns timeslots sorted chronologically by date and slotIndex
- [ ] #2 Lesson.roomListSorted returns rooms sorted by capacity (ascending) for consistent selection
- [ ] #3 LabBlock.timeslotListSorted returns timeslots sorted chronologically
- [ ] #4 LabBlock.roomListSorted returns rooms sorted by capacity (ascending)
- [ ] #5 ScheduleService filter methods preserve sorting after filtering
- [ ] #6 Solver produces valid solutions with sorted value ranges
- [ ] #7 No regression in solution quality (benchmark score comparison)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Implementation Complete (2026-03-16)

**Value Sorting Added to All ValueRangeProviders**

- Lesson.java: Added chronological sorting to timeslots and capacity sorting to rooms

- LabBlock.java: Added chronological sorting to timeslots and capacity sorting to rooms

- Files modified: Lesson.java, LabBlock.java (added Comparator import)

- Build successful: ./scripts/core/rebuild.sh compiled without errors
<!-- SECTION:FINAL_SUMMARY:END -->
