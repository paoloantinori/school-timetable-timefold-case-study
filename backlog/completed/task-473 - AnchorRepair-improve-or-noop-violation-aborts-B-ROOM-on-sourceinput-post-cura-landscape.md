---
id: TASK-473
title: >-
  AnchorRepair improve-or-noop violation aborts B-ROOM on source=input post-cura
  landscape
status: Done
assignee: []
created_date: '2026-08-01 06:51'
updated_date: '2026-08-01 08:49'
labels:
  - claude-planned
milestone: m-13
dependencies: []
priority: high
type: bug
ordinal: 296000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Found 2026-08-01 via paired run (b) (TASK-452 cure). On the source=input post-cura landscape (T01 capped to 6h over more Thursdays), the staged cycle aborts at AnchorRepair: `staged solve: anchor repair violated its improve-or-noop contract (conflicts added 0, timeslot-tier hard -6 -> -7) - aborting before stage B-ROOM can persist the grid` (ScheduleService.runStagedCycle:914). Result: B-ROOM never runs -> 9446 room-null (H29) -> run invalid. The cure is validated (0 H20); root cause is AnchorRepair's conflict counter disagreeing with the real timeslot-tier hard (it says 0 conflicts but hard worsened by 1). Suspect: a timeslot-tier hard family not counted by AnchorRepair, likely H-COPRESENCE (enabled, timeslot-tier) or H28. Run-1 pre-cura and run (a) golden do NOT abort. To confirm: re-run with -Dplanner.phase-transition-dump.enabled=true on the java -jar line and inspect which hard family worsens across AnchorRepair. Fix: make AnchorRepair's improve-or-noop accounting complete (count the missing family) or make its move robust. Blocks the m-13 flip until fixed + run (b) re-tested. Evidence: claudedocs/spalmone-2026-27/task452-results/verify-source-input-run2/VERDICT-2026-08-01.md + /tmp/planner-prod.log.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Cure (committed code, DoD gates green: /simplify applied, /code-review high run, test.sh EXIT=0 934/0/0)

Replaced `assertImproveOrNoop` (THREW on improve-or-noop violation, aborting the staged cycle and skipping B-ROOM) with `ensureImproveOrNoop` (revert-and-continue) in `ScheduleService`. Applied to the shared guard so all four deterministic passes (HoleRepair, LabDayConsolidation, AnchorRepair, SurplusRebalance) get the fallback.

Mechanism:
- `PlacementSnapshot` (nested type): captures (Lesson->timeslot,room) refs before the pass; `restore()` rolls them back. Sufficient because every pass routes persistent mutations through `HoleRepair.commitCrossDayMove` (reassigns only those two fields; live indexes are pass-local).
- `shouldRevert(hardBefore, hardAfter, conflictsAdded)` pure verdict (unit-tested).
- Trigger (user-decided KEEP BOTH): `hardAfter < hardBefore || conflictsAdded != 0`. The H1/H3 clause is kept because it is the only signal that catches a *compensated* teacher double-booking (net hard flat) that B-ROOM cannot repair (it moves only rooms).
- `worsenedFamilies` diagnostic: per-family hard delta (names H-COPRESENCE etc. on a revert), wrapped in try-catch so a best-effort diagnostic can NEVER re-abort the cycle (code-review Important #1, fixed).
- `MOVE_TIMESLOT`/`NULL_ROOM` shared constants structurally enforce the snapshot's reference-semantics contract (simplify F2).
- 2 scorings/pass preserved (no third); snapshots reused for the hard sum + diagnostic.

Tests: new L0 `RepairGuardRevertTest` (6 tests): shouldRevert decision table incl. the compensated-conflict case (R1), PlacementSnapshot byte-identical restore, and the headline end-to-end "violation -> revert (no throw, restore, reverted=true)".

## Deferred (annotated)
- L1 diagnostic test: the `worsenedFamilies` path with real `ConstraintMatchSnapshot` data is not unit-exercised (hand-building ConstraintMatchTotal needs addConstraintMatch, fiddly for a non-load-bearing log line). The try-catch makes it safe; run3 is the real integration proof (will surface the named family in the WARN if AnchorRepair reverts). code-review Important #2, deferred to run3.
- [REVERTED] suffix consistency: runLabDayConsolidation/runSurplusRebalance discard the `reverted` flag (no runner INFO to append to). The shared ensureImproveOrNoop WARN fires uniformly for all four, so the revert is always logged. code-review Minor #3, deferred.

## Standing (verified)
- Revert does not break pin-conservation (finishStagedCycle queries pinned/promotedPin flags the passes never touch) nor downstream stages (every later consumer rebuilds its own indexes; B-ROOM rebuilds its working solution + shadows).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Cured: the shared improve-or-noop guard now revert-and-continues instead of aborting. `ensureImproveOrNoop` (replaces `assertImproveOrNoop`) snapshots the pre-pass placement via `PlacementSnapshot` and, on a violation (timeslot-tier hard worsened OR H1/H3 conflict added), restores the grid + WARN + continues to B-ROOM. Applied to all 4 deterministic repair passes. Both triggers kept (the H1/H3 clause catches a compensated double-booking B-ROOM can't fix). `worsenedFamilies` diagnostic (try-catch-wrapped, never re-aborts). Shared `MOVE_TIMESLOT`/`NULL_ROOM` constants enforce the snapshot's reference-semantics contract. L0 RepairGuardRevertTest (6 tests). DoD green: /simplify applied, /code-review high run, test.sh EXIT=0 (934/0/0). Commit 3b177376 (pushed).

VALIDATION run3 (source=input post-cura, same protocol as the aborting run b): the staged cycle COMPLETED end-to-end. AnchorRepair ran clean (no revert, no abort), B-ROOM finished in 604s, final -6hard (3 unassigned = the expected TASK-435 residual; 0 H20, 0 room-null, 0 holes), recurrence unpinned 75.9% (best of 3). The abort blocker is GONE. Flip m-13 PRONTO (resta SI di maintainer). Note: run3 did not exercise the revert path (the run-b H-COPRESENCE violation was stochastic/grid-dependent); the cure is the verified safety net. Verdict: claudedocs/spalmone-2026-27/task452-results/verify-source-input-run3/VERDICT-2026-08-01.md.
<!-- SECTION:FINAL_SUMMARY:END -->
