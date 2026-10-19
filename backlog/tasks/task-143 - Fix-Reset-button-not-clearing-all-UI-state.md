---
id: TASK-143
title: Fix Reset button not clearing all UI state
status: Done
assignee: []
created_date: '2026-03-01 16:43'
updated_date: '2026-03-09 19:50'
labels:
  - bug
  - ui
  - claude-planned
dependencies: []
priority: high
ordinal: 106000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

The Reset button doesn't properly clear all UI state. Multiple issues reported:

1. **Sidebar constraint violations not cleared** - After reset, the sidebar still shows stale constraint violation counts
2. **Stats bar messages not cleared** - Hard constraint counts and status messages persist after reset
3. **Pre-assign error message not cleared** - The message "Pre-assign error: Phase 1 (Acconciat)" remains visible after reset
4. **X button on notification toast** - Redundant X button when there's already a "Hide" link

## Root Cause

1. **Frontend state not cleared**: `sidebarAnalysis` in ScheduleTimeline and `violationMap` in useLessonViolations hook weren't being reset
2. **Backend pre-assignment status**: The `preAssignmentError` is stored in static fields in `HybridPhase1aService`, not in the schedule entity

## Implementation

### Backend Changes
- Inject `HybridPhase1aService` into `ScheduleService`
- Call `hybridPhase1aService.resetStatus()` in `resetSchedule()` to clear static error fields

### Frontend Changes
- Add `clear()` function to `useLessonViolations` hook
- Add `handleResetComplete` callback in `ScheduleTimeline` that clears sidebar analysis and violations
- Pass `onResetComplete` callback to `UnifiedSolverControls`
- Clear local state (`preAssignResult`, `analysis`, `error`) on reset
- Remove redundant X button from notification toast, keep only "Hide" link
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given a schedule with pre-assignment error visible, When I click Reset, Then the error message 'Pre-assign error: ...' disappears from the stats bar
- [x] #2 Given a schedule with constraint violations shown in sidebar, When I click Reset, Then the sidebar shows 0 hard/medium/soft violations
- [x] #3 Given a pre-assignment result toast is visible, When I look at the toast, Then I see only 'Copy', 'Details/Hide', and 'Hide' buttons - NO X (×) button
- [x] #4 Given a schedule with highlighted violations in the timeline, When I click Reset, Then the red highlighting on lessons disappears
- [x] #5 Playwright test: reset-button.spec.ts all 3 tests pass

- [x] #6 Given a schedule with a previous score displayed, When I click Reset, Then the score resets to 0hard/0medium/0soft and any error messages disappear
- [x] #7 Given a schedule with score cards visible in the sidebar, When I click Reset, Then the sidebar score cards show 0 for hard/medium/soft
<!-- AC:END -->
