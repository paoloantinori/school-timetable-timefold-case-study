---
id: TASK-184
title: 'UI: Pre-assign button stays disabled after use until reset'
status: Done
assignee: []
created_date: '2026-03-06 13:12'
updated_date: '2026-03-09 19:50'
labels: []
dependencies: []
priority: medium
ordinal: 96000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

Currently, after the pre-assign button is pressed, it can be clicked again. This is confusing because:
1. Pre-assignment is typically a one-time operation before solving
2. Re-running pre-assignment without reset may produce unexpected results
3. Users may accidentally click pre-assign multiple times

## Proposed Solution

The pre-assign button's enabled state should be derived from the **actual server state**, not local UI state. The button should be disabled when:
- Pre-assignment just completed (server shows `preAssignmentStatus=COMPLETE`) AND no reset has occurred
- Solver is running (server shows `solverStatus=SOLVING_ACTIVE`)

The button re-enables when:
- Reset is pressed (clears all assignments on server)
- Pre-assignment fails with `ERROR` (allow retry)

**Critical**: The UI must poll or receive updates to detect state changes triggered via API or other clients. Don't rely solely on local button state.

Acceptance Criteria:
- [ ] #1 Given the pre-assign button has been pressed and completed successfully, when viewing the pre-assign button, then it is disabled (derived from server preAssignmentStatus=COMPLETE)
- [ ] #2 Given pre-assignment is complete and the reset button is pressed, when viewing the pre-assign button, then it is enabled again (server preAssignmentStatus != COMPLETE)
- [ ] #3 Given pre-assignment failed with an ERROR, when viewing the pre-assign button, then it remains enabled (allow retry)
- [ ] #4 Given the solver is running (solverStatus=SOLVING_ACTIVE via API or UI), when viewing the pre-assign button, then it is disabled
- [ ] #5 Given the solver is started via API (not through UI), when the UI polls server status, then the pre-assign button reflects the correct disabled state

References:
- Related to TASK-061 (which proposes a 5-second timeout)
- Frontend: src/main/webui/src/components/SolverControls.tsx
- API: GET /api/schedule returns solverStatus and preAssignmentStatus
<!-- SECTION:DESCRIPTION:END -->
