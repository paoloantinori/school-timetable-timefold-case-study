---
id: TASK-246
title: >-
  Fix Phase Progression - Last Phase Shown as In-Progress After Natural
  Termination
status: Done
assignee: []
created_date: '2026-03-16 08:55'
updated_date: '2026-03-16 09:07'
labels: []
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Root Cause
When the solver ends naturally (not via stop button), `finalizeCurrentPhase()` is never called, leaving the last phase marked as "in progress" in the UI.

## Implementation Plan

### 1. Backend: ScheduleService.java
Create a separate handler for final solution that finalizes the phase:
- Add `saveFinalSolution()` method that calls `finalizeCurrentPhase()`
- Change `withFinalBestSolutionConsumer(this::saveFinalSolution)` 
- Keep `withBestSolutionConsumer(this::saveSchedule)` for intermediate solutions

### 2. Create Playwright E2E Test
Test file: `src/main/webui/e2e/phase-progression-complete.spec.ts`
- Start solver and let it finish naturally (no manual stop)
- Open Analysis modal after completion
- Verify all phases show as complete (no "in progress" indicator)
- Also test the stop button case to ensure it still works

### 3. Edge Cases to Test
- Early termination (unimproved time limit reached)
- Full timeout (secondsSpentLimit reached)
- Manual stop (already working, verify regression)
- Pre-assignment + main solve (both completion paths)

## Files to Modify
1. `src/main/java/com/school/planner/service/ScheduleService.java` - Add saveFinalSolution method
2. `src/main/webui/e2e/phase-progression-complete.spec.ts` - New test file

## Success Criteria
- All phases marked complete after natural solver termination
- E2E test passes
- Manual stop still works correctly (regression test)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Backend: saveFinalSolution() method calls finalizeCurrentPhase()
- [x] #2 E2E test verifies all phases complete after natural termination
- [x] #3 E2E test verifies manual stop still works (regression)
- [x] #4 All edge cases tested: timeout, unimproved limit, manual stop
<!-- AC:END -->
