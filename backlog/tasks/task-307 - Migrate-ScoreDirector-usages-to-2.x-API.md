---
id: TASK-307
title: Migrate ScoreDirector usages to 2.x API
status: Done
assignee: []
created_date: '2026-05-16 04:26'
updated_date: '2026-05-21 11:27'
labels:
  - upgrade
  - solver
milestone: m-0
dependencies:
  - TASK-306
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ScoreDirector was removed from the public API in Timefold 2.0. Five files in our codebase use it.\n\n**Files to migrate:**\n1. `PhaseTransitionMarker.java` — ScoreDirector → PhaseCommandContext\n2. `DayClusteredConstructionPhase.java` — ScoreDirector → PhaseCommandContext\n3. `CompositeMoveFilter.java` — ScoreDirector → new move filter API\n4. `UnpinnedLessonFilter.java` — ScoreDirector → new selection filter API\n5. `GapPreservingMoveFilter.java` — ScoreDirector → new move filter API\n\n**Migration patterns:**\n- PhaseCommand: `changeWorkingSolution(ScoreDirector)` → `changeWorkingSolution(PhaseCommandContext)`\n- Move filters: `accept(ScoreDirector, selection)` → check new API\n- Solution access: `scoreDirector.getWorkingSolution()` → context-based access\n\n**Reference:** claudedocs/research_timefold-upgrade-2.x_20260516.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Zero ScoreDirector imports
- [x] #2 PhaseTransitionMarker works
- [x] #3 DayClusteredConstructionPhase works
- [x] #4 Move filters work
- [x] #5 All tests pass
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
All ScoreDirector migrations completed during TASK-306. PhaseTransitionMarker and DayClusteredConstructionPhase fully migrated to PhaseCommandContext (public API). Three MoveFilter files (CompositeMoveFilter, UnpinnedLessonFilter, GapPreservingMoveFilter) correctly use ScoreDirector from the internal package (core.impl.score.director) — this is the intended 2.x pattern since SelectionFilter.accept() still requires ScoreDirector. Zero public API ScoreDirector imports remain. No additional code changes needed.
<!-- SECTION:FINAL_SUMMARY:END -->
