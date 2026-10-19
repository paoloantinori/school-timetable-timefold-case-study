---
id: TASK-297
title: Add slot utilization metric to timeline and week view headers
status: Done
assignee: []
created_date: '2026-03-22 05:45'
labels:
  - claude-planned
  - frontend
  - solver-optimization
milestone: solver-optimization
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Display per-group slot utilization (assigned/total valid slots) in the timeline and week view header rows.

**Backend:**
- New `SlotUtilization` DTO (groupId, groupName, totalValidSlots, assignedSlots)
- `calculateSlotUtilizationForAllGroups()` in StudentGroupCompletionService — counts valid slots within planning horizon (ideal end date, holidays, stage blackout, maxHoursPerDay filter)
- Extended `/api/completion` endpoint with `slotUtilization` array
- Key fix: unified `validSlotStarts` set + `COUNT(DISTINCT timeslot)` to handle H3 violations correctly

**Frontend:**
- `scheduleApi.ts`: new `getSlotUtilization()` fetcher
- `useScheduleData.ts`: fetch slot utilization on solver status change
- `WeekViewGrid.tsx`: display `assigned/total` next to group name
- `TimelineView.tsx`: same display in timeline headers
- `types.ts` + `domain.ts`: TypeScript interfaces

Committed as `7ebe12b`.
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Slot utilization metric fully implemented and committed (7ebe12b). Backend calculates valid slots per group within planning horizon; frontend displays assigned/total in both WeekViewGrid and TimelineView headers. No acceptance criteria were defined upfront since the task was created retroactively.
<!-- SECTION:FINAL_SUMMARY:END -->
