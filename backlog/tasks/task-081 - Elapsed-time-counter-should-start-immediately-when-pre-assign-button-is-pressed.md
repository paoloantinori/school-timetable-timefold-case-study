---
id: TASK-081
title: >-
  Elapsed time counter should start immediately when pre-assign button is
  pressed
status: Done
assignee: []
created_date: '2026-02-23 11:13'
updated_date: '2026-03-09 19:50'
labels:
  - ui
  - ux
dependencies: []
priority: medium
ordinal: 131000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

The elapsed time counter and status display currently only activate during solver execution (`SOLVING_ACTIVE`). When the user clicks a Pre-assign button, there's no immediate feedback that something is happening until the server updates the `preAssignmentStatus`.

## Current Behavior

1. User clicks "Pre-assign All" or any pre-assign button
2. Button shows loading state (`...`) but no elapsed time
3. Status may briefly show "IDLE" until server updates
4. Only after server responds with new `preAssignmentStatus` does polling begin
5. Elapsed time counter uses `serverStartTime` which is only set during solver runs

## Expected Behavior

1. User clicks "Pre-assign All" 
2. **Immediately:** Status shows "Pre-assigning..." with elapsed timer starting from 0
3. Elapsed timer continues through all phases (acconciatura → labs → professionalizzanti)
4. When complete, timer stops and shows final duration
5. If error, timer stops and error is shown

## Technical Details

### Current Implementation (ScheduleTimeline.tsx lines 93-113)
```javascript
const shouldPoll = isSolving || isPreAssigning || solvingRequested;
// isPreAssigning checks preAssignmentStatus NOT in ['IDLE', 'COMPLETE', 'ERROR']
```

The issue is that `isPreAssigning` relies on server state, which has a delay between button click and response.

### Server-Side Times
- `schedule.solveStartTime` — Set when solver starts
- `schedule.preAssignmentStartTime` — Set when pre-assignment starts

### Solution Approach

1. **Optimistic UI update:** Set `preAssignRequested` state immediately on button click
2. **Include in shouldPoll:** `shouldPoll = isSolving || isPreAssigning || solvingRequested || preAssignRequested`
3. **Use correct start time:** When pre-assigning, use `preAssignmentStartTime` for elapsed display
4. **Clear on complete/error:** Reset `preAssignRequested` when status becomes COMPLETE or ERROR

## Related Files

| File | Purpose |
|------|---------|
| `src/main/webui/src/components/ScheduleTimeline.tsx` | Main polling logic and elapsed display |
| `src/main/webui/src/components/SolverControls.tsx` | Pre-assign button handlers |
| `src/main/webui/src/components/UnifiedSolverControls.tsx` | Unified controls with pre-assign menus |
| `src/main/java/com/school/planner/rest/PreAssignmentResource.java` | Backend status tracking |
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Elapsed time counter appears immediately when any Pre-assign button is clicked
- [ ] #2 Status text changes to show pre-assignment phase (e.g., 'Pre-assigning: acconciatura')
- [ ] #3 Elapsed time uses preAssignmentStartTime from server response
- [ ] #4 Timer continues through all pre-assignment phases (acconciatura → labs → professionalizzanti)
- [ ] #5 When pre-assignment completes, elapsed time stops and final duration is shown
- [ ] #6 If pre-assignment fails with error, elapsed time stops and error is displayed
<!-- AC:END -->
