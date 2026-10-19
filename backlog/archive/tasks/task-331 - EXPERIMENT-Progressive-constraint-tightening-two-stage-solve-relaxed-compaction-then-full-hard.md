---
id: TASK-331
title: >-
  EXPERIMENT: Progressive constraint tightening - two-stage solve (relaxed
  compaction then full hard)
status: To Do
assignee: []
created_date: '2026-07-12 17:46'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - experiment
milestone: m-4
dependencies:
  - TASK-330
references:
  - src/main/java/com/school/planner/service/ScheduleService.java
  - >-
    src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java
priority: high
ordinal: 155000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: Feasibility-first staging is an established pattern for over-constrained problems: solve stage A with the rigid compaction constraints demoted (H17, H15 as medium) so the solver freely resolves conflicts and fills days, then stage B re-solves FROM the stage A solution with full hard weights so compaction is enforced on an already-conflict-free schedule. This is the dynamic version of the static tier question in TASK-330, and it exploits Timefold warm-starting (a solve started from an already-initialized solution skips most CH work). Absorbs TASK-192 (Progressive Constraint Tightening). See roadmap doc sections 3 and 4.

WHAT:
1. Investigate the cleanest switch mechanism first, in this order: (a) Timefold 2.x ConstraintWeightOverrides passed per solve - check current official docs; (b) a provider flag like the existing compactionOnlyMode static pattern in SchoolScheduleConstraintProvider (works but static-mutable - acceptable since one solver runs at a time); document the choice.
2. Orchestrate in ScheduleService: new REST-triggered mode (e.g. POST /api/schedule/solve?mode=two-stage) that runs solver A (relaxed config or overrides, termination diminishedReturns), persists best solution, then immediately runs solver B (full constraints) on it. Reuse the existing SolverManager lifecycle - do NOT run two solvers concurrently.
3. Ensure phase-progression tracking distinguishes stage A and stage B (PhaseProgressionService).
4. Add integration test with the H2 dataset proving the two-stage flow completes and stage B starts from stage A assignments (non-null timeslots at stage B start).
5. A/B: two-stage vs single-run baseline at EQUAL total wall-clock, 2 runs each, standardized scorecards; compare conflict-hard subscore, H17 gaps, avg lessons/day (same metric rules as TASK-330). Append to experiment-log.md.
6. Keep the mode behind the query parameter regardless of outcome (useful tool), but document the verdict.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Switch mechanism investigated and documented (weight overrides vs provider flag), with the chosen one implemented
- [ ] #2 Two-stage mode triggerable via REST; integration test passes; single-solver-at-a-time preserved
- [ ] #3 A/B at equal wall-clock completed with subscore-level comparison; experiment-log.md updated with verdict
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): il bisogno di fondo (staging progressivo dei vincoli) e' coperto dalla pipeline a 6 fasi evoluta (377/381/398/401/405/406/409/410/413/414); il meccanismo two-stage REST non e' mai stato costruito e se la linea fix-and-optimize (333-335) rivivra', andra' rispecificato da zero li'.
<!-- SECTION:NOTES:END -->
