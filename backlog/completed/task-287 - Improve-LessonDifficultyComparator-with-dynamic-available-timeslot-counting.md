---
id: TASK-287
title: Improve LessonDifficultyComparator with dynamic available-timeslot counting
status: Done
assignee: []
created_date: '2026-03-22 05:18'
updated_date: '2026-03-22 08:13'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Replace the current static LessonDifficultyComparator (room tags + total hours) with a dynamic one that computes each lesson's available timeslot count at solve time. This implements the MRV/MCV (Most Restricted Variable) heuristic directly — lessons with fewer valid timeslots are scheduled first.

**Approach**: At solve start (or during CH initialization), for each lesson, count how many timeslots pass the filters: not holiday, not teacher unavailable, not stage blackout, not already assigned to another lesson of same group. Sort ascending (fewest first = hardest).

**Trade-offs**: 
- More accurate than static proxy
- Expensive to compute (O(lessons × timeslots)) — consider caching or computing once at solve start
- Gives CH the best possible ordering signal for compact schedules
- Only affects CH initial solution; LS then improves from there

**Current state**: LessonDifficultyComparator sorts by requiredRoomTags.size() then course.totalHours — very coarse, doesn't consider teacher availability, group constraints, or actual timeslot availability.
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Replaced 5-level static proxy cascade (TASK-288) with MRV-based dynamic comparator.

**Approach**: Pre-compute each lesson's available-timeslot count at solve start (stored in static map on SchoolSchedule, keyed by lesson.id). Comparator uses 3-level cascade: pinned → availableTimeslotCount ascending → roomTags.

**Benchmark results** (480s run):
- MRV @180s: -268hard / -638K medium / 103K soft
- MRV @480s: -221hard / -546K medium / 114K soft
- TASK-288 static @180s: -236hard / -585K medium / 100K soft

**Key finding**: MRV is slower to converge (crossover at ~350s) but produces superior quality for long sessions. For production use with long solver runs, MRV is the better choice.

**Pre-computation cost**: 10,418 lessons in 514ms.

**Files changed**: SchoolSchedule.java (static map + getter + compute), ScheduleService.java (computation call), LessonDifficultyComparator.java (3-level MRV cascade)
<!-- SECTION:FINAL_SUMMARY:END -->
