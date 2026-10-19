---
id: TASK-199
title: Show solver phase timeouts in Progression Panel
status: Done
assignee: []
created_date: '2026-03-08 09:41'
updated_date: '2026-03-09 19:50'
labels:
  - enhancement
  - ui
  - solver
dependencies: []
priority: medium
ordinal: 88000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Display the timeout values from solverConfig.xml in the Progression Panel modal for each phase.

## Current State
The Progression Panel shows phase timeline with durations and scores, but does not show the configured timeout limits.

## Solver XML Timeouts (from solverConfig.xml)
- **Overall limit**: 28800 seconds (8 hours)
- **Phase 1** (CH LabBlocks): 7200s max + 900s unimproved
- **Phase 2** (CH Lessons): 7200s max + 1800s unimproved  
- **Phase 3** (LS Compactness): 1200s max + 300s unimproved
- **Phase 4** (LS Conflict): 2700s max + 900s unimproved
- **Phase 5** (LS Room): 2700s max + 900s unimproved

## Implementation Plan

### Backend Changes
1. Create DTO to capture phase timeout configs from solver XML
2. Parse solverConfig.xml at startup to extract phase timeouts
3. Add endpoint or extend existing `/api/phase-progression` to include timeout data
4. Map phase names/numbers to their configured limits

### Frontend Changes (ProgressionPanel.tsx)
1. Add new summary card showing "Overall Limit: 8h"
2. For each phase card, display:
   - Hard timeout (e.g., "Limit: 2h")
   - Unimproved timeout (e.g., "Unimproved: 15m")
3. Optional: Progress bar showing time spent vs limit
4. Visual indicator when phase hits timeout vs converges early

### Files to Modify
- `src/main/java/com/school/planner/rest/PhaseProgressionResource.java` - Add timeout data
- `src/main/java/com/school/planner/service/PhaseProgressionService.java` - Parse XML, include timeouts
- `src/main/webui/src/components/ProgressionPanel.tsx` - Display timeout info
- `src/main/resources/solverConfig.xml` - Reference for timeout values

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Progression Panel shows overall time limit (8h)
- [x] #2 Each phase card displays both `secondsSpentLimit` and `unimprovedSecondsSpentLimit`
- [x] #3 Timeouts are formatted human-readable (e.g., "2h", "15m")
- [x] #4 Backend reads actual values from solverConfig.xml (not hardcoded)
- [x] #5 Phase data includes timeout config in JSON response
- [x] #6 UI handles missing timeout config gracefully
- [x] #7 Playwright test verifies timeout display

## Related Files
- `src/main/resources/solverConfig.xml` - Source of timeout config
- `src/main/webui/e2e/progression-button.spec.ts` - Existing tests
<!-- AC:END -->

## Notes
- The `unimprovedSecondsSpentLimit` causes smart termination when score stops improving
- Phase numbers map 1-5 to the configured timeouts in order
- Overall limit of 28800s (8h) applies to entire solver run
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented timeout configuration display from solverConfig.xml in the progression modal. SolverConfigParser parses XML to extract per-phase timeout limits (secondsSpentLimit, unimprovedSecondsSpentLimit). PhaseRecord includes these fields, and ProgressionPanel displays them in phase cards. Overall limit shown in summary section.
<!-- SECTION:FINAL_SUMMARY:END -->
