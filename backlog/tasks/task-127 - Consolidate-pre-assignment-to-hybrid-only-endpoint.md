---
id: TASK-127
title: Consolidate pre-assignment to hybrid-only endpoint
status: Done
assignee: []
created_date: '2026-02-26 15:41'
updated_date: '2026-03-09 19:50'
labels:
  - pre-assignment
  - api
  - refactoring
  - cleanup
dependencies: []
priority: high
ordinal: 74000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

The pre-assignment API has 3 parallel strategies, causing confusion:

1. **Legacy endpoints** (`/pre-assign`, `/acconciatura`, `/labs`, `/professionalizzanti`) - old procedural code
2. **Timefold solver endpoints** (`/two-phase`, `/two-phase/phase1a`) - slow, doesn't reach 0hard
3. **Hybrid greedy CSP** (`/two-phase/hybrid`) - **THE WINNER** - completes in <1s per phase

The hybrid endpoint (task-121) handles all 3 phases (acconciatura → labs → professionalizzanti) in a single pipeline with CSP backtracking.

## Goal

Make the hybrid endpoint the **only** pre-assignment entry point. Remove all other strategies.

## Proposed API

**New primary endpoint:**
```
POST /api/schedule/pre-assign           → Run full hybrid pipeline (was /two-phase/hybrid)
GET  /api/schedule/pre-assign/status    → Get hybrid status (was /two-phase/hybrid/status)
POST /api/schedule/pre-assign/reset     → Reset hybrid status (was /two-phase/hybrid/reset)
```

**Removed endpoints:**
- `/pre-assign/acconciatura`
- `/pre-assign/labs`
- `/pre-assign/professionalizzanti`
- `/pre-assign/dry-run`
- `/pre-assign/reset-all`
- `/pre-assign/generate-rotations`
- `/two-phase` (Timefold)
- `/two-phase/phase1a` (Timefold)
- `/two-phase/dry-run`
- `/two-phase/phase1a/dry-run`
- `/two-phase/reset-status`
- `/two-phase/hybrid` → becomes main `/pre-assign`
- `/two-phase/hybrid/status` → becomes `/pre-assign/status`
- `/two-phase/hybrid/reset` → becomes `/pre-assign/reset`

## Files to Modify

- `rest/PreAssignmentResource.java` - Remove old endpoints, rename hybrid to main
- `service/TwoPhasePreAssignmentService.java` - Consider removing (Timefold solver)
- `service/AcconciaturaPreAssigner.java` - Consider removing (legacy)
- `service/LabBlockPreAssigner.java` - Consider removing (legacy)
- `service/ProfessionalizzantiPreAssigner.java` - Consider removing (legacy)
- `scripts/api.sh` - Update pre-assign commands
- `claudedocs/rest-api.md` - Update documentation

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 `POST /api/schedule/pre-assign` runs the hybrid pipeline
- [x] #2 `GET /api/schedule/pre-assign/status` returns hybrid status
- [x] #3 `POST /api/schedule/pre-assign/reset` resets hybrid status
- [x] #4 All legacy and Timefold endpoints are removed
- [x] #5 `./scripts/api/api.sh pre-assign` uses the new endpoint
- [x] #6 Documentation updated in rest-api.md
<!-- SECTION:DESCRIPTION:END -->
<!-- AC:END -->
