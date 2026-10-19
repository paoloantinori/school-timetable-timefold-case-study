---
id: TASK-118
title: Extend status endpoint to include Phase 1a/1b state
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
ordinal: 122000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
The `/api/schedule/pre-assign/status` endpoint only tracks the old pre-assigner states (ACCONCIATURA, LABS, etc.) and doesn't show Phase 1a/1b solver status.

## Solution
- Add phase1a and phase1b status to the response
- Include: state (IDLE/RUNNING/COMPLETE/ERROR), start time, current score (if running), error message (if failed)
- Keep backward compatibility with existing status enum

## Response format
```json
{
  "legacyStatus": "IDLE",
  "phase1a": {
    "state": "RUNNING",
    "startTimeMs": 1234567890,
    "currentScore": "-150hard/0medium/0soft"
  },
  "phase1b": {
    "state": "IDLE",
    "startTimeMs": null,
    "currentScore": null
  }
}
```

## Files to modify
- `src/main/java/com/school/planner/rest/PreAssignmentResource.java`
- `src/main/java/com/school/planner/service/TwoPhasePreAssignmentService.java`

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 GET `/api/schedule/pre-assign/status` includes phase1a and phase1b objects
- [x] #2 Shows RUNNING when solver is active with current score
- [x] #3 Shows COMPLETE with final score when done
- [x] #4 Shows ERROR with message if failed
<!-- SECTION:DESCRIPTION:END -->
<!-- AC:END -->
