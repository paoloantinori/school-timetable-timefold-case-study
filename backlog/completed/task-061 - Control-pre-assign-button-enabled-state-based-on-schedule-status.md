---
id: task-061
title: Control pre-assign button enabled state based on schedule status
status: Done
assignee:
  - claude
created_date: '2026-02-20 20:32'
updated_date: '2026-02-21 21:17'
labels:
  - ui
  - claude-planned
  - pre-assignment
  - ux
dependencies:
  - task-040
priority: low
ordinal: 40000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

The "Pre-assign" button should not always be clickable. Currently, it may be active even when:
1. Pre-assignment was just completed (no point in running again immediately)
2. The solver is actively running (would conflict with solver state)
3. No lessons exist to pre-assign
4. Pre-assignment is already in progress (when async status tracking is implemented via task-040)

This leads to confusing UX where users click the button and either see no change or get an error.

## Proposed Solution

Implement a computed `canPreAssign` property in the frontend that evaluates when the button should be enabled:

**Button should be DISABLED when:**
- `solverStatus === 'SOLVING_ACTIVE'` - solver is running
- `preAssignmentStatus === 'ACCONCIATURA' | 'LABS' | 'PROFESSIONALIZZANTI'` - pre-assignment in progress (after task-040)
- No lessons exist in the schedule
- (Optional) All lessons are already pinned

**Button should be ENABLED when:**
- `solverStatus === 'NOT_SOLVING'`
- `preAssignmentStatus === 'IDLE' | 'COMPLETE' | 'ERROR'`
- At least some unpinned lessons exist

## Interaction with task-040

This task depends on task-040 (pre-assignment status tracking) for the `preAssignmentStatus` field. If task-040 is not yet complete, implement a simpler version that only checks solver status and lesson existence.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Given the solver is actively running (SOLVING_ACTIVE), when viewing the pre-assign button, then it is disabled and shows a tooltip 'Stop solving before pre-assigning'
- [ ] #2 Given pre-assignment just completed (preAssignmentStatus=COMPLETE), when the user tries to click pre-assign again within 5 seconds, then the button is disabled with tooltip 'Pre-assignment already complete'
- [ ] #3 Given a schedule with no lessons, when viewing the pre-assign button, then it is disabled with tooltip 'No lessons to pre-assign'
- [ ] #4 Given the solver is NOT running and unpinned lessons exist, when viewing the pre-assign button, then it is enabled and clickable
- [ ] #5 Given pre-assignment is in progress (status=ACCONCIATURA/LABS/PROFESSIONALIZZANTI), when viewing the pre-assign button, then it is disabled with tooltip 'Pre-assignment in progress'
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **1/5** (Low)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 1 | Single button state logic |
| Risk | 1 | UI only, no backend |
| Testing | 1 | Click verification |
| Dependencies | 1 | Partial dep on task-040 |
| **Total** | **1** | 1-2 hours |

---

## Priority: **LOW** (UX Polish)

Can implement simplified version without task-040. Well-defined scope.
<!-- SECTION:NOTES:END -->
