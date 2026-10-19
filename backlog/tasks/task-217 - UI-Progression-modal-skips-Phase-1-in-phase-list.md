---
id: TASK-217
title: 'UI: Progression modal skips Phase 1 in phase list'
status: Done
assignee: []
created_date: '2026-03-09 18:33'
updated_date: '2026-03-10 05:16'
labels:
  - ui
  - bug
  - progression
  - phase-tracking
dependencies: []
priority: low
ordinal: 13750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The Progression modal (ProgressionPanel) starts listing phases from Phase 2, always skipping Phase 1. The first phase is not displayed in the phase history list.

**Current Behavior:**
- Phase list starts at "Phase 2" 
- Phase 1 (Construction Heuristic - LabBlocks) is never shown
- This happens consistently on every solve run

**Expected Behavior:**
- Phase list should start from Phase 1
- All 5 phases should be displayed in order

**Investigation Notes:**
- The 5-phase solver pipeline starts with CH LabBlocks (Phase 1)
- `ProgressionPanel.tsx` fetches data from `/api/phase-progression`
- `PhaseProgressionService` tracks phases on the backend
- Possible causes:
  1. Backend `PhaseProgressionService` not recording Phase 1
  2. Frontend filtering out the first phase entry
  3. Off-by-one error in phase numbering/indexing

**Files to Investigate:**
- `src/main/webui/src/components/ProgressionPanel.tsx` - Progression modal UI
- `src/main/java/com/school/planner/service/PhaseProgressionService.java` - phase tracking
- `src/main/java/com/school/planner/rest/PhaseProgressionResource.java` - phase API
- `src/main/java/com/school/planner/solver/SolverProgressionListener.java` - detects phase transitions
<!-- SECTION:DESCRIPTION:END -->
