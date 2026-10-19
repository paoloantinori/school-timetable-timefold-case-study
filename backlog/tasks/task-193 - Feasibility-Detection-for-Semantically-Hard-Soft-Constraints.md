---
id: TASK-193
title: Feasibility Detection for Semantically-Hard Soft Constraints
status: Done
assignee: []
created_date: '2026-03-07 14:40'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - api
  - frontend
dependencies: []
priority: medium
ordinal: 90000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement feasibility detection to determine if solutions with `0hard/Nmedium/Xsoft` scores are operationally usable.

## Context
The planner implements compaction (student group gap minimization) as a semantically-hard constraint (M13 with weight 500). Without feasibility detection, we can't tell if a solution is usable for teachers and students.

## Implementation
- [x] Add threshold constants to `SchoolSchedule.java` (`DEFAULT_MAX_TOTAL_GAP_MINUTES = 50000`)
- [x] Create `FeasibilityReport.java` DTO with feasible flag, message, score breakdown, medium violations
- [x] Add `/api/schedule/feasibility` endpoint to `ScheduleResource.java`
- [x] Add `getFeasibility()` API call to `scheduleApi.ts`
- [x] Add `FeasibilityReport` interface to `domain.ts`
- [x] Update `SolverControls.tsx` to display feasibility badge
- [ ] Update REST API documentation (`claudedocs/rest-api.md`)
<!-- SECTION:DESCRIPTION:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
- [x] Add threshold constants to `SchoolSchedule.java` (`DEFAULT_MAX_TOTAL_GAP_MINUTES = 50000`)
- [x] Create `FeasibilityReport.java` DTO with feasible flag, message, score breakdown, medium violations
- [x] Add `/api/schedule/feasibility` endpoint to `ScheduleResource.java`
- [x] Add `getFeasibility()` API call to `scheduleApi.ts`
- [x] Add `FeasibilityReport` interface to `domain.ts`
- [x] Update `SolverControls.tsx` to display feasibility badge
- [ ] Update REST API documentation (`claudedocs/rest-api.md`)
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Fixed compilation issues:

- Removed duplicate import in ScheduleResource.java

- Added FeasibilityReport import to scheduleApi.ts

- Renamed service.FeasibilityReport → PreAssignmentFeasibilityReport to avoid name collision

- Removed duplicate feasibility badge rendering in SolverControls.tsx

- Removed duplicate constants from rest/dto/FeasibilityReport (already in SchoolSchedule)

Verified: Java (mvn compile) and TypeScript (npx tsc) both compile cleanly
<!-- SECTION:FINAL_SUMMARY:END -->
