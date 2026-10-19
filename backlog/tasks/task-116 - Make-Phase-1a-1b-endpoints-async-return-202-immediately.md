---
id: TASK-116
title: Make Phase 1a/1b endpoints async (return 202 immediately)
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
ordinal: 124000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
Phase 1a and Phase 1b endpoints (`/api/schedule/pre-assign/two-phase/phase1a` and `phase1b`) block for minutes while the solver runs. They expose a synchronous interface but are effectively async operations.

## Solution
- Make endpoints return HTTP 202 Accepted immediately
- Run solver in background (use `@Async` or ExecutorService)
- Return a job ID or status reference that can be polled

## Files to modify
- `src/main/java/com/school/planner/rest/PreAssignmentResource.java`
- `src/main/java/com/school/planner/service/TwoPhasePreAssignmentService.java`

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 POST to `/api/schedule/pre-assign/two-phase/phase1a` returns 202 within 1 second
- [x] #2 POST to `/api/schedule/pre-assign/two-phase/phase1b` returns 202 within 1 second
- [x] #3 Solver runs in background
- [x] #4 Response includes status endpoint to poll for completion
<!-- SECTION:DESCRIPTION:END -->
<!-- AC:END -->
