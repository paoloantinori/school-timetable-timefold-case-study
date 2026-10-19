---
id: TASK-277
title: >-
  Refactor targetEndDate → idealEndDate + buffer, eliminate duplicate
  computation
status: To Do
assignee: []
created_date: '2026-03-20 04:56'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - refactoring
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
`StudentGroup` has two dates that are confusingly similar and computed independently in `TargetEndDateCalculator`:
- `targetEndDate` — buffered date used by VRP filtering and M12 constraint
- `idealEndDateInfo.idealEndDate` — tight date used by UI coloring

Both are computed in the same method with overlapping logic. The `targetEndDate` adds `BUFFER_WEEKS` + 10% `ensureEnoughSlots()` on top of `idealEndDate`, but this relationship is implicit and easy to miss (as demonstrated by multiple confusions in this session).

## Requirements

### 1. Rename `targetEndDate` to include "buffer" and "vrp"
Rename `StudentGroup.targetEndDate` and all references to make its purpose explicit:
- `targetEndDate` → `idealEndDateVrpBuffered` (or similar name that contains "buffer" and "vrp")
- Update all references: `Lesson.getAvailableTimeslots()`, `BaseConstraintProvider` (M12), `SchoolSchedule.setTargetEndDatesStatic()`, `DayClusteredConstructionPhase`, `SchoolScheduleSolutionFileIO`, `ScheduleService`, etc.

### 2. Compute the buffered date FROM idealEndDate
Currently `TargetEndDateCalculator.computeGroupTargetDate()` scans forward independently to find both dates. Refactor to:
1. Compute `idealEndDate` first (tight: first date with enough cumulative slots)
2. Derive `idealEndDateVrpBuffered` = `idealEndDate` + buffer weeks + progressive extension
3. Store both on `StudentGroup`

This makes the relationship explicit: the VRP boundary is always `idealEndDate + buffer`.

### 3. Update `DayClusteredConstructionPhase` cap
Phase 1 should use `idealEndDate` (tight) as the cap. The `idealEndDateVrpBuffered` is only for VRP filtering in `Lesson.getAvailableTimeslots()` and M12 constraint.

## Files to Update
- `StudentGroup.java` — rename field
- `TargetEndDateCalculator.java` — refactor to compute from idealEndDate
- `Lesson.java` — VRP uses renamed field
- `BaseConstraintProvider.java` — M12 uses renamed field
- `SchoolSchedule.java` — static setter renamed
- `ScheduleService.java` — static setter call renamed
- `DayClusteredConstructionPhase.java` — cap uses idealEndDate
- `SchoolScheduleSolutionFileIO.java` — benchmark IO renamed
- `StudentGroupCompletionService.java` — if references exist
- Frontend types if exposed via API

## Naming Options
- `idealEndDateVrpBuffered` — explicit about purpose
- `vrpBufferedEndDate` — shorter
- `idealEndDateWithVrpBuffer` — readable
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 StudentGroup.targetEndDate renamed to include 'buffer' and 'vrp' in the name
- [ ] #2 Buffered date is computed FROM idealEndDate (not independently)
- [ ] #3 All references updated across codebase (Lesson VRP, M12, static setters, Phase 1, SolutionFileIO)
- [ ] #4 DayClusteredConstructionPhase uses idealEndDate as cap
- [ ] #5 No duplicate forward-scanning logic for the two dates
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): gia' consolidato: idealEndDate -> +buffer -> idealEndDateVrpBuffered e' la catena attuale (StudentGroup.java:51, TargetEndDateCalculator :218-231), nessun doppio forward-scan.
<!-- SECTION:NOTES:END -->
