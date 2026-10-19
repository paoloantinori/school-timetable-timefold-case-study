---
id: task-105
title: Show real-time constraint analysis during solving
status: To Do
assignee: []
created_date: '2026-02-23 19:48'
updated_date: '2026-02-26 15:43'
labels:
  - frontend
  - ux
  - solver
  - claude-planned
dependencies: []
priority: low
ordinal: 50000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Poll and display constraint score breakdown while the solver is running, not just after it stops. Currently the AnalysisPanel only opens post-solve — users watch the score number change but can't see which constraints are improving or worsening.

Inspired by dmon-scheduler which polls both solution and analysis every 2 seconds during solving.

**Approach:**
- Add periodic polling of GET /api/schedule/analysis while solverStatus is SOLVING
- Show a compact mini-breakdown in the SolverControls area (top 5 violated constraints)
- Update every 3-5 seconds (balance UX vs server load)
- Stop polling when solver stops

**Caveat:** SolutionManager.explain() may be expensive on 7,000+ entities. Need to benchmark the analysis endpoint response time during solving. If too slow, consider:
- Caching analysis results for a few seconds
- Only computing analysis for hard constraints during solving
- Adding a server-side flag to enable/disable live analysis

**Key files:**
- `src/main/webui/src/components/SolverControls.tsx` (add polling + mini display)
- `src/main/webui/src/api/scheduleApi.ts` (may need analysis polling function)
- Backend: verify /api/schedule/analysis works during active solving
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Mini constraint breakdown visible during active solving
- [ ] #2 Display updates every 3-5 seconds while solver is running
- [ ] #3 Polling stops automatically when solver finishes
- [ ] #4 No significant performance degradation of solver during polling
- [ ] #5 Graceful fallback if analysis endpoint is slow (skip update, don't block UI)
<!-- AC:END -->
