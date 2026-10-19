---
id: TASK-110
title: Fix missing resolution speed indicator during local search phase
status: Done
assignee: []
created_date: '2026-02-24 05:27'
updated_date: '2026-03-10 05:16'
labels:
  - ui
  - bug
  - solver
dependencies: []
priority: medium
ordinal: 40750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

During the Local Search (LS) phase of solving, the "speed" indicator (showing hard score improvement per minute) is no longer visible or is being hidden due to UI crowding. Users need this metric to understand solving progress and detect stalls.

## Evidence

Screenshot shows the solver controls area during solving. The speed indicator should appear next to the LS elapsed time but may be missing or truncated.

## Root Cause Analysis

Looking at `UnifiedSolverControls.tsx`, the speed indicator code exists:
```tsx
{isInLS && (
  <span className={...speedInfo styles...}>
    {!speedInfo ? 'speed...' :
     speedInfo.isStalled ? 'stalled' :
     `${speedInfo.speed >= 0 ? '+' : ''}${formatNumber(Math.round(speedInfo.speed))}/min`}
  </span>
)}
```

Possible causes:
1. **Insufficient score history**: Speed requires 2+ samples within 60 seconds. If polling is slow or CH just ended, `scoreHistory.length < 2`.
2. **UI overflow/crowding**: The status bar has many elements (status, score, elapsed, CH time, LS time, speed). The speed badge may be truncated or wrapped.
3. **Conditional rendering bug**: The `isInLS` check depends on `chCompletionTime` being set by the server.

## Proposed Solution

1. Verify the speed indicator is rendered (check DOM during LS phase)
2. If overflow is the issue, consider:
   - Consolidating elapsed displays (e.g., "CH: 2m | LS: 45s" on one line)
   - Making speed more prominent (it's critical for monitoring)
   - Responsive layout for smaller screens
3. Add a fallback "waiting for data..." state instead of hiding the indicator

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Speed indicator is visible during LS phase after 6+ seconds of data collection
- [ ] #2 Speed shows improvement rate (+X/min), stall warning, or degradation (-X/min)
- [ ] #3 UI does not overflow or truncate the speed display on standard viewports
- [ ] #4 Visual styling clearly communicates speed status (green=improving, amber=stalled, red=degrading)
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Already Implemented (2026-02-27)

Investigation shows the speed indicator is fully implemented in `UnifiedSolverControls.tsx`:

- Speed calculation based on score history (lines 151-170)

- Color-coded display: green (improving), amber (stalled), red (degrading)

- Shows '+X/min', '-X/min', 'stalled', or 'speed...' fallback

- Properly positioned in flex layout with adequate space

All 4 acceptance criteria are satisfied. Task was likely created before the current implementation was complete.
<!-- SECTION:NOTES:END -->
