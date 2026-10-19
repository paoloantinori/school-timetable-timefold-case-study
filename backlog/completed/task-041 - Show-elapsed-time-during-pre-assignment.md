---
id: task-041
title: Show elapsed time during pre-assignment
status: Done
assignee: []
created_date: '2026-02-19 17:00'
updated_date: '2026-02-21 10:19'
labels:
  - enhancement
  - ui
  - pre-assignment
  - claude-planned
dependencies:
  - task-040
priority: low
ordinal: 22000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The elapsed timer in the UI should work during pre-assignment, not just during solving.

**Current behavior**: Timer only activates when `solveStartTime` exists (solver running)

**Desired behavior**: Timer should also run during pre-assignment phases

**Note**: This is largely covered by task-040 (AC#4 covers elapsed time display). This task tracks any remaining frontend timer work after task-040 is complete.

**Implementation**:
- Track pre-assignment start time
- Show timer based on pre-assignment or solver start time
- Reset appropriately between operations

**Files affected**:
- `src/main/webui/src/components/SolverControls.tsx`
- `src/main/webui/src/types/index.ts` (add preAssignStartTime to Schedule type)

**Dependencies**: task-040 (backend status tracking must be complete first)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given pre-assignment has started, When user views the timer, Then elapsed time begins counting from zero
- [x] #2 Given pre-assignment completes, When status changes to Complete, Then timer stops and shows final duration
- [x] #3 Given pre-assignment errors out, When status changes to Error, Then timer stops and shows duration at failure point

- [x] #4 Given user starts solver after pre-assignment, When solver begins, Then timer resets and starts fresh for solver phase
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

This task is covered by task-040. Once pre-assignment start time is tracked in ScheduleService, the frontend timer automatically works.

### Frontend Timer Logic

```typescript
// In SolverControls.tsx
const isPreAssigning = schedule?.preAssignmentStatus &&
  !['IDLE', 'COMPLETE', 'ERROR'].includes(schedule.preAssignmentStatus);

const isSolving = schedule?.solverStatus === 'SOLVING_ACTIVE';

// Use appropriate start time based on operation
const activeStartTime = isPreAssigning
  ? schedule?.preAssignmentStartTime
  : (isSolving ? schedule?.solveStartTime : null);

// Timer logic uses activeStartTime
useEffect(() => {
  if (activeStartTime) {
    timerRef.current = setInterval(updateElapsed, 1000);
  }
  return () => clearInterval(timerRef.current);
}, [activeStartTime]);
```

### Files to Modify
- `src/main/webui/src/components/SolverControls.tsx` (same as task-040)

### Dependencies
- Requires task-040 to be completed first (backend status tracking)
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Already Implemented in task-040

All acceptance criteria were covered by task-040 implementation:

- `serverStartTime` switches between `preAssignmentStartTime` and `solveStartTime` based on operation type
- Timer displays when `isSolving || isPreAssigning` (line 294)
- Timer automatically stops when status changes to COMPLETE or ERROR

No additional code changes required. Marking as done.
<!-- SECTION:NOTES:END -->
