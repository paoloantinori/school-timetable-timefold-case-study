---
id: TASK-140
title: Fix Reset button to clear all planning assignments
status: Done
assignee: []
created_date: '2026-03-01 13:01'
updated_date: '2026-03-09 19:50'
labels:
  - ui
  - bug
  - solver
dependencies: []
priority: high
ordinal: 109000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The Reset button in SolverControls shows "Status reset to IDLE" but doesn't actually reset the planning. Users need it to:

1. Clear all timeslot/room assignments from Lessons and LabBlocks
2. Unpin all entities
3. Reset solver state completely
4. Return to initial unassigned state

**Current behavior**: Only resets the pre-assignment status flag, doesn't touch entity assignments.

**Expected behavior**: Full reset to initial state - all entities unassigned and unpinned.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Backend: Add POST /api/schedule/reset endpoint that clears all timeslot/room assignments and unpins all entities
- [x] #2 Frontend: Update handleReset to call the new endpoint
- [x] #3 Endpoint returns 200 with confirmation message
- [x] #4 After reset, all Lessons and LabBlocks have null timeslot/room and pinned=false
<!-- AC:END -->
