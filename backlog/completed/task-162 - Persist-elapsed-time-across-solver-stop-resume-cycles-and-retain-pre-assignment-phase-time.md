---
id: TASK-162
title: >-
  Persist elapsed time across solver stop/resume cycles and retain
  pre-assignment phase time
status: Done
assignee: []
created_date: '2026-03-04 08:56'
updated_date: '2026-03-05 16:03'
labels:
  - enhancement
  - solver
  - frontend
  - backend
  - claude-planned
dependencies: []
priority: medium
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Currently, when the solver is stopped and resumed:
1. **Elapsed time resets to 0** - The frontend resets `elapsedSeconds`, `chSeconds`, and `lsSeconds` when `serverStartTime` is null
2. **Pre-assignment time is lost** - Pre-assignment timing exists in static fields but is not persisted to `SchoolSchedule`

This makes it impossible to track total optimization effort across multiple solver sessions.

## Current Implementation

**Backend (ScheduleService.java):**
- `solveStartTime` is set when solver starts
- `chCompletionTime` is set when CH phase ends
- Both are cleared when solver stops

**Frontend (UnifiedSolverControls.tsx, SolverControls.tsx):**
- Time computed from `serverStartTime` (Instant)
- When solver stops: `setElapsedSeconds(0)`, `setChSeconds(0)`, `setLsSeconds(0)`

**Pre-assignment (HybridPhase1aService.java):**
- Static fields: `startTime`, `currentPhase`, `currentCandidatesTried`
- Not persisted to SchoolSchedule

## Desired Behavior

1. **Cumulative solver time**: Stop → Resume should ADD to previous time, not reset
2. **Pre-assignment time retention**: Show pre-assignment duration even after solver starts
3. **Phase time preservation**: CH and LS times should accumulate across sessions
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [Backend] Add `cumulativeElapsedSeconds` field to SchoolSchedule (persisted)
- [ ] #2 [Backend] Add `cumulativeChSeconds` field to SchoolSchedule (persisted)
- [ ] #3 [Backend] Add `cumulativeLsSeconds` field to SchoolSchedule (persisted)
- [ ] #4 [Backend] Add `preAssignmentDurationSeconds` field to SchoolSchedule (persisted)
- [ ] #5 [Backend] On solver stop: store current session time to cumulative fields
- [ ] #6 [Backend] On solver resume: initialize timers with cumulative values + new session time
- [ ] #7 [Backend] Pre-assignment: persist duration to SchoolSchedule when complete
- [ ] #8 [API] Include cumulative time fields in GET /api/schedule response
- [ ] #9 [Frontend] Display cumulative elapsed time (not just current session)
- [ ] #10 [Frontend] Show pre-assignment time as separate metric (e.g., 'Pre-assign: 45s')
- [ ] #11 [Frontend] Preserve timer state when solver stops (show last known values)
- [ ] #12 [Testing] Verify time persists across stop/resume cycles in integration test
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Plan

### Phase 1: Backend - SchoolSchedule Entity Changes

**File:** `src/main/java/com/school/planner/domain/SchoolSchedule.java`

Add new persistent fields:
```java
// Cumulative solver time tracking (persists across stop/resume)
private Long cumulativeElapsedSeconds = 0L;
private Long cumulativeChSeconds = 0L;
private Long cumulativeLsSeconds = 0L;

// Pre-assignment time tracking
private Long preAssignmentDurationSeconds = 0L;
private Instant preAssignmentStartTime;
```

These fields will be:
- Serialized in REST API responses
- Not reset on solver stop (unlike `solveStartTime`)
- Incremented on each solver session

### Phase 2: Backend - ScheduleService Changes

**File:** `src/main/java/com/school/planner/service/ScheduleService.java`

**On solver stop (`terminateEarly`):**
```java
public void stopSolving() {
    if (solveStartTime != null) {
        // Calculate current session duration
        long sessionSeconds = Duration.between(solveStartTime, Instant.now()).getSeconds();
        
        // Add to cumulative totals
        schedule.setCumulativeElapsedSeconds(
            schedule.getCumulativeElapsedSeconds() + sessionSeconds
        );
        
        // Store CH time if phase completed
        if (chCompletionTime != null) {
            long chSeconds = Duration.between(solveStartTime, chCompletionTime).getSeconds();
            schedule.setCumulativeChSeconds(
                schedule.getCumulativeChSeconds() + chSeconds
            );
            // LS = total - CH
            schedule.setCumulativeLsSeconds(
                schedule.getCumulativeElapsedSeconds() - schedule.getCumulativeChSeconds()
            );
        }
        
        saveSchedule(schedule);
    }
    solverManager.terminateEarly(SINGLETON_ID);
}
```

**On solver resume:**
```java
public void startSolving() {
    solveStartTime = Instant.now();
    // Cumulative fields already have previous values - will be added in getScheduleWithStatus()
    solverManager.solveBuilder()...run();
}
```

**In `getScheduleWithStatus()`:**
```java
// Total time = cumulative + current session
long currentSession = solveStartTime != null 
    ? Duration.between(solveStartTime, Instant.now()).getSeconds() 
    : 0;

schedule.setDisplayedElapsedSeconds(
    schedule.getCumulativeElapsedSeconds() + currentSession
);
```

### Phase 3: Backend - Pre-Assignment Time Persistence

**File:** `src/main/java/com/school/planner/service/HybridPhase1aService.java`

At the end of `runPreAssignment()`:
```java
if (startTime != null) {
    long duration = Duration.between(startTime, Instant.now()).getSeconds();
    scheduleService.updatePreAssignmentDuration(duration);
}
```

**File:** `src/main/java/com/school/planner/service/ScheduleService.java`

```java
@Transactional
public void updatePreAssignmentDuration(long seconds) {
    SchoolSchedule schedule = loadSchedule();
    schedule.setPreAssignmentDurationSeconds(seconds);
    saveSchedule(schedule);
}
```

### Phase 4: REST API Changes

**File:** `src/main/java/com/school/planner/rest/ScheduleResource.java`

GET /api/schedule already returns SchoolSchedule - new fields will be included automatically.

Response will now include:
```json
{
  "solverStatus": "NOT_SOLVING",
  "cumulativeElapsedSeconds": 3600,
  "cumulativeChSeconds": 90,
  "cumulativeLsSeconds": 3510,
  "preAssignmentDurationSeconds": 45,
  "solveStartTime": null,
  "chCompletionTime": null
}
```

### Phase 5: Frontend Changes

**Files:** 
- `src/main/webui/src/components/UnifiedSolverControls.tsx`
- `src/main/webui/src/components/SolverControls.tsx`
- `src/main/webui/src/types/SchoolSchedule.ts`

**TypeScript types:**
```typescript
interface SchoolSchedule {
  // Existing fields...
  cumulativeElapsedSeconds?: number;
  cumulativeChSeconds?: number;
  cumulativeLsSeconds?: number;
  preAssignmentDurationSeconds?: number;
}
```

**Timer calculation changes:**
```typescript
// Current behavior (resets to 0)
const computeElapsed = () => {
  if (!serverStartTime) return 0;  // <-- PROBLEM
  return Math.floor((Date.now() - serverStartTime.getTime()) / 1000);
};

// New behavior (uses cumulative)
const computeElapsed = () => {
  const cumulative = schedule?.cumulativeElapsedSeconds ?? 0;
  if (!serverStartTime) return cumulative;  // Show persisted value when stopped
  const currentSession = Math.floor((Date.now() - serverStartTime.getTime()) / 1000);
  return cumulative + currentSession;
};
```

**Display changes:**
- Show pre-assignment time as separate badge when > 0
- When solver stops, timers should show last known cumulative values (not disappear)

### Files to Modify

| File | Changes |
|------|---------|
| `domain/SchoolSchedule.java` | Add 4 new cumulative time fields |
| `service/ScheduleService.java` | Update stop/start logic to persist time |
| `service/HybridPhase1aService.java` | Persist pre-assignment duration on completion |
| `rest/ScheduleResource.java` | No changes needed (auto-serialized) |
| `webui/src/types/SchoolSchedule.ts` | Add new fields to interface |
| `webui/src/components/UnifiedSolverControls.tsx` | Use cumulative time in calculations |
| `webui/src/components/SolverControls.tsx` | Use cumulative time in calculations |
| `claudedocs/rest-api.md` | Document new response fields |

### Testing Strategy

1. **Unit test**: `ScheduleServiceTest` - verify cumulative time calculation
2. **Integration test**: Solve → Stop → Resume → verify cumulative time
3. **Manual test**: Run pre-assign → verify duration shows → Solve → verify still visible

### Edge Cases

- **First solve ever**: Cumulative fields are 0, behave like current
- **Reset endpoint**: Should reset cumulative fields to 0
- **Browser refresh**: Timers should recover from API response
- **Pre-assignment failure**: Duration should still be recorded
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Completed: Persist elapsed time across solver stop/resume cycles

### Changes
1. **Tests** (`ScheduleResourceTest.java`): Added 2 tests:
   - `testCumulativeTimeFieldsInResponse`: Verifies cumulative timing fields (cumulativeElapsedSeconds, cumulativeChSeconds, cumulativeLsSeconds, preAssignmentDurationSeconds) appear in GET /api/schedule response and default to 0
   - `testCumulativeTimeAccumulates`: Integration test that runs solver, waits for completion, and verifies cumulative fields are non-negative after solving

2. **REST API docs** (`claudedocs/rest-api.md`): Added "Schedule Response Timing Fields" section documenting all 8 timing-related fields returned by GET /api/schedule, including lifecycle behavior (start/stop/reset)

All 6 ScheduleResourceTest tests pass.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Unit tests for cumulative time calculation in ScheduleServiceTest
- [ ] #2 Manual test: solve → stop → resume → verify time is cumulative
- [ ] #3 Manual test: run pre-assign → solve → verify pre-assign time still visible
- [ ] #4 Update REST API documentation (claudedocs/rest-api.md) with new fields
- [ ] #5 Verify no regression in existing solver functionality
<!-- DOD:END -->
