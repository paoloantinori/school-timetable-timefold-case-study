---
id: TASK-220
title: 'UI: Progression elapsed time doesn''t match main page elapsed time'
status: Done
assignee: []
created_date: '2026-03-09 18:40'
updated_date: '2026-03-10 05:16'
labels:
  - ui
  - bug
  - progression
  - time-tracking
dependencies: []
priority: low
ordinal: 6750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The elapsed time shown in the ProgressionPanel modal doesn't match the elapsed time shown in UnifiedSolverControls on the main page. The times diverge significantly.

**Current Behavior:**
- Main page shows one elapsed time
- Progression modal shows a different elapsed time
- Values can differ by seconds or even minutes

**Expected Behavior:**
- Both elapsed times should show the same value
- They should be synchronized

**Investigation Notes:**
- UnifiedSolverControls likely calculates elapsed from `solveStartTime` in schedule data
- ProgressionPanel may calculate from its own tracking
- Possible causes:
  1. Different timestamps being used (solve start vs first phase start)
  2. One includes pre-planning time, the other doesn't
  3. Polling frequency causing display lag
  4. Rounding/formatting differences

**Files to Investigate:**
- `src/main/webui/src/components/controls/UnifiedSolverControls.tsx` - main elapsed time
- `src/main/webui/src/components/ProgressionPanel.tsx` - modal elapsed time
- Backend: `ScheduleService.java` - solve start time tracking
<!-- SECTION:DESCRIPTION:END -->
