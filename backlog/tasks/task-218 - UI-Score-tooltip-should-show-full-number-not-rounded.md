---
id: TASK-218
title: 'UI: Score tooltip should show full number, not rounded'
status: Done
assignee: []
created_date: '2026-03-09 18:40'
updated_date: '2026-03-10 05:16'
labels:
  - ui
  - bug
  - tooltip
  - score-display
dependencies: []
priority: low
ordinal: 12750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The score displayed at the top of the page (in UnifiedSolverControls) shows a rounded number like "-1.2M" or "-450k". When hovering over the score, there should be a tooltip showing the full unrounded number.

**Current Behavior:**
- Score is rounded to K (thousands) or M (millions)
- No tooltip on hover to see the exact value

**Expected Behavior:**
- Hovering over the score should show a tooltip with the full number
- Example: Hovering "-1.2M" shows "-1,185,432hard/-52,134,567soft"

**Files to Investigate:**
- `src/main/webui/src/components/controls/UnifiedSolverControls.tsx` - where score is rendered
- `src/main/webui/src/components/ScheduleSidebar.tsx` - also displays score

**Implementation Notes:**
- React Tooltip library or browser title attribute can be used
- Score format is `hard/soft` separated by slash
<!-- SECTION:DESCRIPTION:END -->
