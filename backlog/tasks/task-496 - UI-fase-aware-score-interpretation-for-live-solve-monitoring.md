---
id: TASK-496
title: 'UI: fase-aware score interpretation for live solve monitoring'
status: To Do
assignee: []
created_date: '2026-08-13 14:11'
labels:
  - claude-planned
  - ui
  - observability
dependencies: []
references:
  - claudedocs/solver-phases.md
  - src/main/java/com/school/planner/service/ScheduleService.java
  - src/main/java/com/school/planner/service/PhaseProgressionService.java
  - src/main/webui/src/components/UnifiedSolverControls.tsx
documentation:
  - >-
    CLAUDE.md section 'Canonical Constraint IDs' (H29/H0 TASK-447 accounting:
    hard == boxed*2 + room-null*1 pre-B-ROOM)
  - >-
    CLAUDE.md section 'Osservare un solve staged' (two false signals that cost a
    run)
priority: low
type: feature
ordinal: 309000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
During a staged solve the raw score is misleading without phase context. Example observed 2026-08-13 (TASK-470 run2): mid-solve the score read "-1619hard/-845438medium" during B-ROOM (phase 5), which looks alarming but is EXPECTED because rooms are not yet assigned (TASK-447 accounting: pre-B-ROOM hard = boxed*2 + room-null*1, so hard is negative mid-pipeline and only reaches 0 when B-ROOM completes). Similarly medium can rise from end-of-phase-2 to final because room assignment trades medium for hard. A user following the solve live has no way to know that a worsening-looking score is progress, not regression.

This task: surface phase-aware interpretation in the solver UI so a live-following user understands what the current score MEANS given the current stagedStage. Scope: the existing stagedStage + phase progression data is already published (ScheduleService.refreshSolverStatics, PhaseProgressionResource); the work is presentation, not new telemetry.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 During an active staged solve, the UI shows the current stagedStage AND a one-line human-readable interpretation of what the score means at this phase (e.g. during B-TIMESLOT: 'hard negative = lessons not yet room-assigned, expected'; during B-ROOM: 'hard descending toward 0 as rooms fill').
- [ ] #2 The interpretation is driven by the canonical phase semantics (phases 1-5, see claudedocs/solver-phases.md and the TASK-447 hard-accounting note in CLAUDE.md), not hardcoded score thresholds.
- [ ] #3 Non-staged / completed solves show the final interpretation ('0hard = feasible; medium/soft = quality').
- [ ] #4 i18n: messages in both messages_en.properties and messages_it.properties.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
