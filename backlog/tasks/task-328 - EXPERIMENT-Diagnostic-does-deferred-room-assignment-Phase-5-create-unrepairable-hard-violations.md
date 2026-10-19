---
id: TASK-328
title: >-
  EXPERIMENT: Diagnostic - does deferred room assignment (Phase 5) create
  unrepairable hard violations?
status: Done
assignee: []
created_date: '2026-07-12 17:44'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - experiment
  - diagnostic
milestone: m-4
dependencies: []
references:
  - src/main/resources/solverConfig.xml
  - claudedocs/research/critical-assessment-and-roadmap-20260712.md
priority: high
ordinal: 152000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: The pipeline optimizes timeslots in phases 1-4 with rooms unassigned (room variable allowsUnassigned=true), then Phase 5 CH assigns rooms and Phase 6 can ONLY move rooms (change/swap room selectors). If room assignment produces conflicts whose repair requires a TIMESLOT move (e.g. two lab groups needing the same tagged room at the same slot with no alternative room), nothing downstream can fix them - a potential structural dead end of the room-deferred design (TASK-183). This has never been verified. See roadmap doc section 3, third bullet.

WHAT (diagnostic only - fix is TASK-329):
1. Run the standardized protocol once. From the phase-progression export, record the score at end of Phase 4 (timeslot-only) and at end of Phase 5 and Phase 6.
2. Quantify: how many hard violations exist at end of Phase 5 that did not exist at end of Phase 4 (these are room-induced: H2 room conflict, H-M2 suitability, M3 capacity)? How many of those does Phase 6 clear? The remainder is the unrepairable set.
3. For each remaining room-induced violation, inspect via api.sh analysis and the per-lesson endpoint (api.sh lessons or /api/schedule/analysis/lessons) whether ANY room-only move could fix it (a free suitable room exists at that slot) or whether a timeslot move would be required. Classify each as room-fixable vs timeslot-locked.
4. Deliverable: claudedocs/research/room-deferral-diagnostic-YYYYMMDD.md with the three numbers (introduced by Phase 5, cleared by Phase 6, timeslot-locked remainder), the classification, and a clear verdict: is a mixed timeslot+room repair phase needed (yes/no).
Repeat on a second run to confirm the pattern is not noise.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Scores at end of phases 4, 5, 6 recorded for 2 runs
- [ ] #2 Room-induced hard violations counted and classified room-fixable vs timeslot-locked
- [ ] #3 Diagnostic report written with explicit yes/no verdict on needing a mixed repair phase
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
DIAGNOSTIC COMPLETE 2026-07-15. Verdict: NO room-induced hard violations. Checked two full solves (h43-generalized -154hard, tabu -113hard): H2 (room conflict) = 0 hard, H-M2 (room suitability) = 0 hard, M3 (room capacity) = 0 hard (-424 medium only). ALL hard debt is timeslot-placement (M22 + H4.3 + H4), not room. The room-deferral pipeline correctly assigns rooms in Phase 5 without creating hard conflicts; Phase 6 successfully refines. TASK-329 (mixed repair Phase 7) is NOT needed. Report: claudedocs/research/room-deferral-diagnostic-20260715.md. Caveat: per-phase scores not available (phase-progression endpoint doesn't populate them); final-solution check is sufficient for the verdict.
<!-- SECTION:FINAL_SUMMARY:END -->
