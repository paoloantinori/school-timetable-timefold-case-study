---
id: TASK-216
title: 'UI: Solver phase label doesn''t update during solve'
status: Done
assignee: []
created_date: '2026-03-09 18:32'
updated_date: '2026-03-10 05:16'
labels:
  - ui
  - bug
  - solver
  - phase-tracking
dependencies: []
priority: low
ordinal: 14750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The phase label shown next to the elapsed time in the solver controls displays "Construction heuristic (initial violations expected)" but never changes as the solver progresses through its 5 phases. The label should reflect the current solver phase.

**Current Behavior:**
- Phase label is stuck on "Construction heuristic (initial violations expected)"
- Doesn't update when solver moves to later phases (CH Lessons, LS Compactness, LS Conflict, LS Room)

**Expected Behavior:**
- Phase label should update dynamically as solver progresses
- Should match the phases tracked by `PhaseProgressionService`

**Investigation Notes:**
- Backend tracks phases via `PhaseProgressionService` and `SolverProgressionListener`
- API endpoint `/api/phase-progression` returns phase history
- `ProgressionPanel` modal correctly shows phases - this component may be a reference for the fix
- Main solver controls (likely `UnifiedSolverControls.tsx`) need to subscribe to phase updates

**Files to Investigate:**
- `src/main/webui/src/components/controls/UnifiedSolverControls.tsx` - main solver controls UI
- `src/main/webui/src/api/` - phase progression API client (may need to be created)
- `src/main/java/com/school/planner/rest/PhaseProgressionResource.java` - backend phase API
<!-- SECTION:DESCRIPTION:END -->
