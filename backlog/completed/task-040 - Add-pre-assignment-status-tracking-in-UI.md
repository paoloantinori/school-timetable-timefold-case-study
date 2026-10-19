---
id: task-040
title: Add pre-assignment status tracking in UI
status: Done
assignee: []
created_date: '2026-02-19 17:00'
updated_date: '2026-02-21 10:08'
labels:
  - enhancement
  - ui
  - pre-assignment
  - status-tracking
  - claude-planned
  - playwright-tests
dependencies: []
priority: high
ordinal: 42000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
When clicking "Pre-assign all", the status stays as "NOT SOLVING" instead of showing the current phase (acconciatura → labs → professionalizzanti). Previously it showed elapsed time and phase tracking.

**Root Cause**: Pre-assignment is synchronous while solver is async. No status polling mechanism exists for pre-assignment.

**Requirements**:
1. Add pre-assignment status enum: `PRE_ASSIGNING_ACCONCIATURA`, `PRE_ASSIGNING_LABS`, `PRE_ASSIGNING_PROF`, `PRE_ASSIGN_COMPLETE`
2. Make pre-assignment async (or break into steps with polling)
3. Show elapsed time during pre-assignment
4. Show current phase name

**Implementation approach**:
1. Backend: Track pre-assignment progress in `ScheduleService` or new state bean
2. Add status endpoint: `GET /api/schedule/pre-assign/status`
3. Frontend: Poll status during pre-assignment (reuse solver polling logic)
4. Display phase + elapsed time in `SolverControls.tsx`

**Files affected**:
- `src/main/java/com/school/planner/service/PreAssignmentService.java`
- `src/main/java/com/school/planner/rest/PreAssignmentResource.java`
- `src/main/java/com/school/planner/domain/ScheduleService.java`
- `src/main/webui/src/components/SolverControls.tsx`
- `src/main/webui/src/api/scheduleApi.ts`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given user clicks 'Pre-assign all', When acconciatura step is running, Then status shows 'Acconciatura' phase name
- [x] #2 Given user clicks 'Pre-assign all', When labs step is running, Then status shows 'Labs' phase name
- [x] #3 Given user clicks 'Pre-assign all', When professionalizzanti step is running, Then status shows 'Professionalizzanti' phase name
- [x] #4 Given pre-assignment is running, When 30 seconds have elapsed, Then elapsed time displays approximately '00:30'
- [x] #5 Given pre-assignment completes successfully, When all steps finish, Then status shows 'Complete'

- [x] #6 Given pre-assignment fails, When an error occurs, Then status shows error message to user
- [x] #7 Given pre-assignment is running, When backend becomes unreachable, Then UI shows connection error without crashing
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Step 1: Create PreAssignmentStatus enum

```java
// New file: src/main/java/com/school/planner/domain/PreAssignmentStatus.java
package com.school.planner.domain;

public enum PreAssignmentStatus {
    IDLE,
    ACCONCIATURA,
    LABS,
    PROFESSIONALIZZANTI,
    COMPLETE,
    ERROR
}
```

### Step 2: Add status tracking to ScheduleService

```java
// In ScheduleService.java, add:
private PreAssignmentStatus preAssignmentStatus = PreAssignmentStatus.IDLE;
private Instant preAssignmentStartTime;
private String preAssignmentError;

public PreAssignmentStatus getPreAssignmentStatus() { return preAssignmentStatus; }
public Instant getPreAssignmentStartTime() { return preAssignmentStartTime; }
public String getPreAssignmentError() { return preAssignmentError; }

public void setPreAssignmentStatus(PreAssignmentStatus status) { this.preAssignmentStatus = status; }
public void setPreAssignmentStartTime(Instant time) { this.preAssignmentStartTime = time; }
public void setPreAssignmentError(String error) { this.preAssignmentError = error; }
```

### Step 3: Make pre-assignment async with status updates

```java
// In PreAssignmentService.java:
@Inject ExecutorService executor;

@Produces
ExecutorService produceExecutor() {
    return Executors.newSingleThreadExecutor();
}

public void runAllAsync() {
    scheduleService.setPreAssignmentStatus(PreAssignmentStatus.IDLE);
    scheduleService.setPreAssignmentStartTime(Instant.now());
    scheduleService.setPreAssignmentError(null);

    executor.submit(() -> {
        try {
            scheduleService.setPreAssignmentStatus(PreAssignmentStatus.ACCONCIATURA);
            runAcconciatura();

            scheduleService.setPreAssignmentStatus(PreAssignmentStatus.LABS);
            runLabs();

            scheduleService.setPreAssignmentStatus(PreAssignmentStatus.PROFESSIONALIZZANTI);
            runProfessionalizzanti();

            scheduleService.setPreAssignmentStatus(PreAssignmentStatus.COMPLETE);
        } catch (Exception e) {
            scheduleService.setPreAssignmentStatus(PreAssignmentStatus.ERROR);
            scheduleService.setPreAssignmentError(e.getMessage());
        }
    });
}
```

### Step 4: Add status endpoint

```java
// In PreAssignmentResource.java:
@GET
@Path("/status")
public PreAssignmentStatusResponse getStatus() {
    return new PreAssignmentStatusResponse(
        scheduleService.getPreAssignmentStatus(),
        scheduleService.getPreAssignmentStartTime(),
        scheduleService.getPreAssignmentError()
    );
}
```

### Step 5: Update frontend types

```typescript
// In webui/src/types/index.ts:
export type PreAssignmentStatus =
  | 'IDLE' | 'ACCONCIATURA' | 'LABS' | 'PROFESSIONALIZZANTI' | 'COMPLETE' | 'ERROR';

export interface Schedule {
  // ... existing fields
  preAssignmentStatus?: PreAssignmentStatus;
  preAssignmentStartTime?: string;
  preAssignmentError?: string;
}
```

### Step 6: Update SolverControls.tsx

```typescript
const isPreAssigning = schedule?.preAssignmentStatus &&
  !['IDLE', 'COMPLETE', 'ERROR'].includes(schedule.preAssignmentStatus);

const preAssignPhase = schedule?.preAssignmentStatus?.toLowerCase().replace('_', ' ');

// In status display:
{isPreAssigning && (
  <span>Pre-assigning: {preAssignPhase} ({elapsedTime})</span>
)}
```

### Files to Modify
- `src/main/java/com/school/planner/domain/PreAssignmentStatus.java` (new)
- `src/main/java/com/school/planner/service/ScheduleService.java`
- `src/main/java/com/school/planner/service/PreAssignmentService.java`
- `src/main/java/com/school/planner/rest/PreAssignmentResource.java`
- `src/main/webui/src/types/index.ts`
- `src/main/webui/src/components/SolverControls.tsx`

## Playwright Validation Tests

### Prerequisites
- App running at http://localhost:8080 (`./planner.sh start <profile> && ./planner.sh wait`)
- Database profile with seed data (fresh profile recommended)
- Browser connected via Playwright MCP

### Test 1: Navigate and Verify Initial State
```
mcp__playwright__browser_navigate(url: "http://localhost:8080")
mcp__playwright__browser_snapshot()
```
**Verify**: 
- Page loads with Schedule Timeline visible
- Solver controls show status (should be idle/NOT SOLVING initially)
- "Pre-assign all" button is visible and enabled

### Test 2: Pre-assignment Execution and Status Tracking
```
mcp__playwright__browser_click(ref: <Pre-assign all button ref>)
mcp__playwright__browser_snapshot()
```
**Verify** (may happen quickly, so snapshot may show final state):
- Status indicator shows pre-assignment phase (one of: ACCONCIATURA, LABS, PROFESSIONALIZZANTI, or COMPLETE)
- Elapsed time appears if operation is in progress
- Button may be disabled during operation

### Test 3: Poll for Status Changes (if pre-assignment is slow enough)
```
mcp__playwright__browser_wait_for(text: "Complete", time: 30)
mcp__playwright__browser_snapshot()
```
**Verify**:
- Status transitions to "COMPLETE" (or localized equivalent)
- Elapsed time shows final duration
- "Pre-assign all" button is re-enabled

### Test 4: Verify Final State
```
mcp__playwright__browser_snapshot()
```
**Verify**:
- Status shows "Complete" or returns to idle state
- No error messages displayed
- Pre-assigned lessons are visible on timeline (pinned indicators)

### Test 5: Error Scenario (if testable)
To test error handling, you could:
1. Temporarily break the backend (e.g., stop database mid-operation)
2. Or use a test profile with invalid data that triggers error

```
mcp__playwright__browser_click(ref: <Pre-assign all button ref>)
mcp__playwright__browser_wait_for(text: "Error", time: 10)
mcp__playwright__browser_snapshot()
```
**Verify**:
- Error status is displayed
- Error message is visible (from `preAssignmentError` field)
- User can retry after error

### Test 6: Status Polling During Operation
If pre-assignment takes multiple seconds, use rapid polling:
```
// Start pre-assignment
mcp__playwright__browser_click(ref: <Pre-assign all button ref>)

// Rapid snapshots to catch intermediate states
mcp__playwright__browser_snapshot()  // Capture immediately after click
mcp__playwright__browser_wait_for(time: 0.5)
mcp__playwright__browser_snapshot()  // Capture at 0.5s
mcp__playwright__browser_wait_for(time: 0.5)
mcp__playwright__browser_snapshot()  // Capture at 1s
mcp__playwright__browser_wait_for(time: 1)
mcp__playwright__browser_snapshot()  // Capture at 2s
```
**Verify** (if timing allows):
- Status progresses: ACCONCIATURA → LABS → PROFESSIONALIZZANTI → COMPLETE
- Elapsed time increments during operation

### Validation Checklist
- [ ] Initial state shows idle/ready status
- [ ] Pre-assignment button triggers status change
- [ ] Status shows current phase (ACCONCIATURA/LABS/PROFESSIONALIZZANTI) during operation
- [ ] Elapsed time appears and updates during operation
- [ ] Final state shows "Complete" status
- [ ] Error state displays error message if operation fails
- [ ] Button states (enabled/disabled) are correct throughout

### Notes
- Pre-assignment is typically fast (< 2 seconds with seed data), so intermediate states may be hard to capture
- Focus validation on final state (COMPLETE) and error handling
- Consider adding artificial delay in backend for testing status transitions (remove before production)
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **3/5** (Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 3 | Backend + frontend changes |
| Risk | 2 | Async handling complexity |
| Testing | 3 | Integration testing needed |
| Dependencies | 2 | None blocking |
| **Total** | **3** | 2-3 days |

---

## Priority: **HIGH** (Foundation for Pre-Assignment UX)

Backend status tracking required by task-041, task-050, task-051, task-061. Enables async pre-assignment with progress feedback.
<!-- SECTION:NOTES:END -->
