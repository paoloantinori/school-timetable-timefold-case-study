---
id: TASK-142
title: Fix Pre-assign button UI feedback and completion tracking
status: Done
assignee: []
created_date: '2026-03-01 14:42'
updated_date: '2026-03-09 19:50'
labels:
  - ui
  - bug
  - pre-assignment
dependencies: []
priority: high
ordinal: 107000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The Pre-assign button has several issues:

1. **Timer doesn't start** - When clicking Pre-assign, the elapsed timer doesn't show in the UI
2. **No completion feedback** - It's unclear if pre-assignment completed or failed
3. **Status polling may be broken** - The UI doesn't reflect the actual pre-assignment state

## Root Cause Investigation Needed

- Check if `onPreAssignRequested` callback is properly triggering polling
- Verify the timer display logic for pre-assignment phase
- Check if pre-assignment status is being polled correctly
- Ensure the UI shows clear success/failure states

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Clicking Pre-assign starts the elapsed timer display
- [ ] #2 Timer shows "Pre-assigning: [phase]" status during execution
- [ ] #3 Completion shows a clear success/failure message
- [ ] #4 User can see the pre-assignment progress in real-time
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Fixed Issues

### 1. Backend: Pre-assignment status not propagating to schedule
**Root cause**: `HybridPhase1aService` tracked its own status in instance fields, but `ScheduleService.getScheduleWithStatus()` used separate fields that were never updated.

**Fix**: Changed `HybridPhase1aService` to use static volatile fields for status tracking, and modified `ScheduleService.getScheduleWithStatus()` to read from `HybridPhase1aService` static getters:
- `HybridPhase1aService.getCurrentStatus()`
- `HybridPhase1aService.getCurrentPhaseStatic()`
- `HybridPhase1aService.getStartTimeStatic()`
- `HybridPhase1aService.getLastErrorStatic()`

### 2. Frontend: Reset button not clearing calendar
**Root cause**: Reset button called `api.resetPreAssignmentStatus()` which only resets the status enum to IDLE, not the actual lesson assignments.

**Fix**: Changed Reset button to call `api.resetSchedule()` which calls `POST /api/schedule/reset` to clear all lesson/labblock assignments and unpin all entities.

### Files Changed
- `src/main/java/com/school/planner/service/HybridPhase1aService.java` - Static status fields + getters
- `src/main/java/com/school/planner/service/ScheduleService.java` - Read from HybridPhase1aService static getters
- `src/main/webui/src/components/SolverControls.tsx` - Reset button calls resetSchedule()
- `src/main/webui/src/components/UnifiedSolverControls.tsx` - Reset button calls resetSchedule()
- `src/main/webui/e2e/reset-button.spec.ts` - **NEW** Playwright test
<!-- SECTION:NOTES:END -->
