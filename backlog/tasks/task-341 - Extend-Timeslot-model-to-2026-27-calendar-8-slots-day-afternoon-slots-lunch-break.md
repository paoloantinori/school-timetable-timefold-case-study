---
id: TASK-341
title: >-
  Extend Timeslot model to 2026/27 calendar: 8 slots/day, afternoon slots, lunch
  break
status: Done
assignee: []
created_date: '2026-07-16 09:32'
updated_date: '2026-07-16 17:19'
labels:
  - claude-planned
  - spalmone-2027
milestone: m-7
dependencies: []
references:
  - claudedocs/spalmone-2026-27/analysis.md
  - claudedocs/spalmone-2026-27/data/closures.csv
  - src/main/java/com/school/planner/bootstrap/DataGenerator.java
priority: high
ordinal: 165000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: The real 2026/27 spalmone (claudedocs/spalmone-2026-27/analysis.md section 2) has 8 slots/day: 6 morning (08:00-14:00) + lunch break 14:00-14:30 + 2 afternoon (14:30-15:30, 15:30-16:30). 575 lessons sit in afternoon slots across 9 groups. Current model generates 6 slots/day (~1520 timeslots) - the real timetable CANNOT be represented.

WHAT:
1. Update Timeslot generation (bootstrap/DataGenerator.java timeslot section and any generator service) to produce 8 slots/day for school year 2026-09-14 to 2027-06-10, using the 29 closure dates from claudedocs/spalmone-2026-27/data/closures.csv.
2. slotIndex 0-5 = morning, 6-7 = afternoon. Store start times: 08,09,10,11,12,13,14:30,15:30.
3. Audit ALL code that assumes 6 slots/day or slot times: grep for slotIndex, SLOTS_PER_DAY, hardcoded 13:00/14:00, TimeslotSequence, H17/H18/H11 constraint logic, LessonGenerator, AdaptiveSlotFinder. List every touched site in implementation notes.
4. Consecutiveness semantics: slot 5 (13:00) and slot 6 (14:30) are NOT consecutive for gap purposes (lunch break) but a teaching block MAY cross lunch (27 real cases). Introduce a single utility (e.g. in TimeslotSequence) answering isAdjacent(slotA, slotB) and isGap(slotA, slotB) so constraints share one definition.
5. Do NOT touch frontend in this task (separate task in m-10).

CONSTRAINT: DataGenerator edits are authorized by the user decision of 2026-07-16 (spalmone 2026/27 overrides all previous data) - record this in the commit message. Do not change the real course/teacher data in this task; only calendar/timeslots.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Timeslot generation produces 8 slots/day over 2026-09-14..2027-06-10 excluding the 29 closures; count asserted in a test
- [x] #2 Single shared adjacency/gap utility exists and is used by H15/H16/H17 logic; lunch is not a gap; blocks may cross lunch
- [x] #3 All 6-slot assumptions found via grep are listed in notes and fixed or explicitly deferred
- [x] #4 ./scripts/core/test.sh passes
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented 2026-07-16. Timeslot generation: 2026-09-14..2027-06-10, 8 slots/day (08-14 + 14:30/15:30), 29 closures seeded (22 weekday-effective; 2026-01-22 kept as documented out-of-range anomaly — school question). Verified: 172 school days = 1376 timeslots asserted in DataGeneratorCalendarTest. TimeslotSequence is the single adjacency/gap source (slotIndexOf O(1) map, isAdjacent date-safe incl. lunch branch, LUNCH_BREAK_MINUTES) used by H15/H16a/H16b/H17/S1/S7/S8/M18; DayClusteredConstructionPhase, TargetEndDateCalculator and StudentGroupCompletionService moved off the 6/5-slot assumption. code-review high: 16 candidates, 15 confirmed; in-scope fixes applied (incl. S7/M18 migration missed initially, hot-path O(1) lookup, cross-date isAdjacent false-positive fix); deferred findings annotated in TASK-343 (stale 2025/26 LabRotations/StagePatterns/ProfessionalizzantiDays => H4 no-op on fresh seed), TASK-358 (afternoon slots invisible in UI), TASK-350 (ScheduleService gap mirrors). Tests: 303/303 green.
<!-- SECTION:FINAL_SUMMARY:END -->
