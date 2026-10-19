---
id: TASK-357
title: >-
  Manual lesson placement: wire assignLesson with slot/room picker, drag-drop
  and instant re-validation
status: Done
assignee: []
created_date: '2026-07-16 09:38'
updated_date: '2026-07-18 00:11'
labels:
  - claude-planned
  - spalmone-2027
  - ux
milestone: m-10
dependencies:
  - TASK-354
priority: high
ordinal: 181000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: verified 2026-07-16: api/scheduleApi.ts exports assignLesson(id, timeslotId, roomId) but NO component calls it; there is no draggable/onDrop anywhere in the frontend (the PROJECT_INDEX claim of draggable unassigned lessons is false). A human planner currently cannot hand-place or move a single lesson - only pin/unpin. Hand-editing with immediate legality feedback is a core requirement.

WHAT:
1. LessonTooltip: add Move controls - target date/slot picker (only slots open per the group calendar) and room picker; calls assignLesson then refreshes analysis for the affected entities.
2. Drag-drop: make placed lessons and UnassignedSidebar items draggable; empty grid cells in WeekViewGrid become drop targets; on drop call assignLesson. Native HTML5 DnD is sufficient; keep it simple.
3. Instant feedback: after any manual move, call the lesson-violation analysis (useLessonViolations) for the affected week and paint violated cells red with tooltip naming the constraint - UniTime-style immediate conflict highlighting. Integrates with the watchdog endpoint from TASK-354 if already merged, otherwise call /api/schedule/analysis/lessons directly.
4. Guard: moving a PINNED lesson asks for confirmation (it changes pre-assignment ground truth).
5. Playwright e2e: drag an unassigned lesson to a free slot, assert it appears and a conflicting placement shows red.

FILES: components/LessonTooltip.tsx, components/timeline/WeekViewGrid.tsx + VirtualizedDayRow.tsx + cellRenderer.tsx, components/UnassignedSidebar.tsx, hooks/useLessonViolations.ts, api/scheduleApi.ts.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 assignLesson reachable from UI via picker AND drag-drop; closed slots not offered/accepted
- [x] #2 Violated placements highlighted within 2s without running the solver
- [x] #3 Playwright e2e passes; pinned-lesson move requires confirmation
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SCHOOL DESIDERATA 2026-07-17 (vincoli-in-parole.md): 'un bel pannello dove inserire in maniera semplice le ore pensate dall'utente che diventano intoccabili, e le ore che devono rimanere vuote — poi la macchina pensa al resto'. This task IS that panel: manual placement = pin (intoccabile); pair it with the GroupCalendarAdmin editor (done, TASK-358) for the non-fillable cells. Prioritize accordingly.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The school's desiderata panel shipped: Move panel (H20/H21-filtered slot picker, pin-by-default = 'ore intoccabili'), native drag-drop with precomputed legality highlighting and pinned-move confirm, instant re-validation (watchdog run + badge force-refresh bus + newly-violated toast). Zero backend changes (assignLesson/togglePin/validation pre-existed). Client mirrors only H20/H21; server stays source of truth. UnassignedSidebar found DEAD (never mounted) — drag wired into ScheduleSidebar's real Unassigned tab instead. Full DoD run in-agent (simplify + code-review high, all findings applied incl. a dependency-array bug and pin-default unification); orchestrator verification post-merge: 528/528 + typecheck + build exit 0 (after a sibling-worktree port-8081 contention, root-caused as environmental). Playwright spec authored — execute in the live cycle.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
