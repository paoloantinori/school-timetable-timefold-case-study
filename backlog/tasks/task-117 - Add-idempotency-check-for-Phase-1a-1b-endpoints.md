---
id: TASK-117
title: Add idempotency check for Phase 1a/1b endpoints
status: Done
assignee: []
created_date: '2026-02-25 16:01'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - phase1a
  - backend
dependencies: []
priority: high
ordinal: 123000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
If Phase 1a or Phase 1b endpoints are called multiple times, they start concurrent solver processes. This leads to resource contention and unpredictable behavior.

## Solution
- Track current pre-assignment state (IDLE, PHASE1A_RUNNING, PHASE1B_RUNNING, COMPLETE, ERROR)
- If endpoint called while already running, return HTTP 409 Conflict with current status
- Allow only one solver process at a time

## Files to modify
- `src/main/java/com/school/planner/rest/PreAssignmentResource.java`
- `src/main/java/com/school/planner/service/TwoPhasePreAssignmentService.java`

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Calling phase1a while phase1a is running returns 409 Conflict
- [x] #2 Calling phase1a while phase1b is running returns 409 Conflict
- [x] #3 Response body includes current status and which phase is running
- [x] #4 Reset endpoint clears running state
<!-- SECTION:DESCRIPTION:END -->
<!-- AC:END -->
