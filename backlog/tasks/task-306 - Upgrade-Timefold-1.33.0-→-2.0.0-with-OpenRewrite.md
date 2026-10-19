---
id: TASK-306
title: Upgrade Timefold 1.33.0 → 2.0.0 with OpenRewrite
status: Done
assignee: []
created_date: '2026-05-16 04:26'
updated_date: '2026-05-21 11:26'
labels:
  - upgrade
  - solver
milestone: m-0
dependencies:
  - TASK-304
  - TASK-305
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Major version upgrade using OpenRewrite automated migration.\n\n**Prerequisites:** Java 21 (TASK-304), Timefold 1.33.0 (TASK-305)\n\n**Automated changes (OpenRewrite):**\n- Run Timefold OpenRewrite recipe for 2.0.0 migration\n- Handles most API renames and package moves\n\n**Manual changes required:**\n- `timefold-solver-test` dependency removal (moved to core API)\n- Import path updates for moved classes\n- Score package flattening imports\n- Verify `@PlanningId` on all domain entities\n- Remove `autoDiscoverMemberType` if present\n\n**Acceptance criteria:**\n- Timefold 2.0.0 in pom.xml\n- Project compiles without errors\n- All tests pass\n- Solver runs with existing solverConfig.xml\n\n**Reference:** claudedocs/research_timefold-upgrade-2.x_20260516.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Timefold 2.0.0 in pom.xml
- [x] #2 Project compiles
- [x] #3 All tests pass
- [x] #4 Solver runs with existing config
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Upgraded Timefold Solver from 1.33.0 to 2.0.0. Major API migrations: ScoreDirector→PhaseCommandContext (2 PhaseCommand files), SelectionFilter imports (3 MoveFilter files), Indictment→ScoreAnalysis (ScheduleService), SolverManager generic simplification, @PlanningId added to all domain entities, EnvironmentMode renames, solver XML DECREASING_DIFFICULTY→DESCENDING across 12 config files, ConstraintVerifier imports migrated, timefold-solver-test dependency removed. 41 files changed. Build passes with zero Timefold deprecation warnings.
<!-- SECTION:FINAL_SUMMARY:END -->
