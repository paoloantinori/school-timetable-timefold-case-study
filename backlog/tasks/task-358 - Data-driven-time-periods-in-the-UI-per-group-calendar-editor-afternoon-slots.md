---
id: TASK-358
title: >-
  Data-driven time periods in the UI + per-group calendar editor (afternoon
  slots)
status: Done
assignee: []
created_date: '2026-07-16 09:38'
updated_date: '2026-07-17 01:47'
labels:
  - claude-planned
  - spalmone-2027
  - ux
milestone: m-10
dependencies:
  - TASK-341
  - TASK-342
priority: high
ordinal: 182000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: ALL_TIME_PERIODS is hardcoded to 6 morning slots in components/timeline/types.ts and isSlotAvailable in hooks/useScheduleData.ts hardcodes the 5h-Friday heuristic. With the 8-slot backend model (TASK-341) and GroupCalendarRule (TASK-342) the UI must render afternoons and per-group opening patterns from data, and offer an editor.

WHAT:
1. Fetch slot definitions from the backend (new lightweight endpoint or extend the schedule payload) and derive time periods dynamically; render the lunch break as a visual separator; afternoon slots visible only where any displayed group has them open.
2. Replace isSlotAvailable heuristics with GroupCalendarRule data.
3. New admin editor for GroupCalendarRule: per group a 5x8 weekday-slot checkbox matrix with date-range validity; also expose StudentGroup.maxHoursPerDay (currently editable nowhere - verified).
4. Update WeekViewGrid/TimelineView rendering, keep virtualization performance (view-switch-perf e2e must not regress).

FILES: components/timeline/types.ts, hooks/useScheduleData.ts, components/timeline/*, new components/admin/GroupCalendarAdmin.tsx, AdminLayout.tsx, api/scheduleApi.ts.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Afternoon slots render with lunch separator; closed slots visually distinct per group
- [x] #2 No hardcoded slot lists remain in the frontend (grep ALL_TIME_PERIODS usages resolved to data-driven source)
- [x] #3 Calendar editor CRUDs rules and maxHoursPerDay; view-switch-perf e2e does not regress
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-07-17 night: e2e EXECUTED live — 3/3 passed after selector scoping + contiguity-safe toggle. AC fully closed.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented (worktree) + merged + code-review high (22 candidates over 2 batches; criticals fixed: StudentGroupResource maxHoursPerDay partial-DTO wipe; TS resolution port replaced by GET /resolved backend endpoint with legacy fallback; poll-tick re-fetch bug). 8 data-driven slot rows with lunch separator, closed-slot rendering from resolved calendar, GroupCalendarAdmin matrix editor + validated maxHoursPerDay editing. e2e specs authored with test-ids, NOT yet executed (needs live app — run with the next full-test cycle together with TASK-356 e2e). Tests 384/384.
<!-- SECTION:FINAL_SUMMARY:END -->
