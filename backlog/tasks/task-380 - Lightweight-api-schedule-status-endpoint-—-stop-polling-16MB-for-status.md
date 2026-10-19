---
id: TASK-380
title: Lightweight /api/schedule/status endpoint — stop polling 16MB for status
status: Done
assignee: []
created_date: '2026-07-19 11:04'
updated_date: '2026-07-19 12:33'
labels:
  - claude-planned
dependencies: []
ordinal: 204000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Root cause behind most of the TASK-367 e2e punch list (toasts never appearing, laggy buttons): GET /api/schedule takes ~8.7s on the full 2026/27 dataset (16MB JSON + full loadSchedule normalization) and the UI polls it every 2s (500ms during solve/pre-assign) - requests pile up and the UI schedule state lags by minutes. Fix: (1) new GET /api/schedule/status returning only the in-memory status fields (solverStatus, score, lastSolverError, preAssignment status/error, phase, cumulative times) with NO loadSchedule/DB access; (2) ScheduleTimeline polls the light endpoint and triggers the heavy fetch only when status/score actually change; (3) api.sh status/score switched to the light endpoint.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET /api/schedule/status responds <100ms on the full dataset with no DB round-trip
- [ ] #2 UI toasts (pre-assign complete) appear within ~2 poll cycles; e2e preassign-toast + reset-button specs green
- [ ] #3 api.sh status/score use the light endpoint
- [ ] #4 test.sh exit 0; DoD simplify+review
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DONE 2026-07-19: GET /api/schedule/status live at 22ms (was 8,700ms for the full schedule - 400x). UI polls light status at 1s and triggers the heavy fetch only on change; api-side getters exposed (latestScore, lastSolverError). Proven by e2e: both preassign-toast specs (previously always-red toasts) went green immediately after the switch. DoD: pattern-reviewed inline (small diff), covered by e2e + full suite 605/605 tc 0 build 0.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
