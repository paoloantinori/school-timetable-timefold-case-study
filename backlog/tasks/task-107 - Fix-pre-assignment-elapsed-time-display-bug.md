---
id: TASK-107
title: Fix pre-assignment elapsed time display bug
status: Done
assignee: []
created_date: '2026-02-23 21:58'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - bug
  - frontend
dependencies: []
priority: high
ordinal: 126000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

The elapsed timer does not show during pre-assignment because polling relies on `schedule.preAssignmentStatus` being an active state, but the schedule isn't refreshed during the blocking API call.

**Flow:**
1. User clicks "Pre-assign" → `handlePreAssign` starts
2. API call **awaits** (blocks until server finishes)
3. Schedule isn't refreshed during this time
4. `isPreAssigning` is **false** (status still shows IDLE/COMPLETE)
5. When API returns, status is already COMPLETE
6. Timer never shows

## Solution

Add a `preAssignRequested` state (similar to `solvingRequested`) to trigger polling immediately when the user clicks pre-assign.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Elapsed time displays immediately when user clicks any pre-assign button
- [x] #2 Polling starts within 1 second of button click
- [x] #3 Timer stops and resets when pre-assignment completes (COMPLETE/ERROR)
- [x] #4 Works for all pre-assign operations: all, acconciatura, labs, professionalizzanti, dry-run, reset, resetAll
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation

### Changes Made

1. **UnifiedSolverControls.tsx**
   - Added `onPreAssignRequested?: () => void` prop
   - Called `onPreAssignRequested?.()` in `handlePreAssign` before API call

2. **ScheduleTimeline.tsx**
   - Added `preAssignRequested` state (line 71)
   - Updated `shouldPoll` to include `preAssignRequested`
   - Added timeout clearing logic for `preAssignRequested` (10s timeout, clears when `isPreAssigning` becomes true)
   - Added `onPreAssignRequested` prop to `UnifiedSolverControls`

### Testing
- TypeScript compiles without errors
- App rebuilt and restarted successfully
- Ready for manual testing: click any pre-assign button and verify elapsed timer shows immediately

---
**Verified: 2026-02-23** - User confirmed elapsed timer now shows immediately when clicking pre-assign buttons.
<!-- SECTION:NOTES:END -->
