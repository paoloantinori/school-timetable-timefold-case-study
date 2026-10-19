# Room-Deferral Diagnostic — 2026-07-15 (TASK-328)

**Question:** Does the deferred room-assignment pipeline (Phases 1–4 timeslot-only → Phase 5 CH rooms → Phase 6 LS rooms) create hard violations that Phase 6 cannot repair?

## Method

Checked the final-solution constraint breakdowns from two full solves (tabu baseline `-113hard` and generalized-H4.3 `-154hard`) for room-induced hard violations: H2 (room conflict), H-M2 (room suitability), M3 (room capacity).

## Result: zero room-induced hard violations in both solves

| Constraint | h43-generalized (-154) | tabu baseline (-113) |
|---|---|---|
| H2 (Room conflict) | **0 hard** | **0 hard** |
| H-M2 (Room suitability) | **0 hard** | **0 hard** |
| M3 (Room capacity) | 0 hard, -424 medium | 0 hard, -424 medium |

**All hard debt is timeslot-placement related** — M22 (calendar compactness), H4.3 (professionalizzanti single-day during stage), H4 (stage blackout). None involve rooms.

## Verdict: NO — a mixed timeslot+room repair phase (TASK-329) is NOT needed

The room-deferral design correctly assigns rooms in Phase 5 without creating hard conflicts. Phase 6 successfully refines room placement. The structural hard debt is about *when* lessons are scheduled (calendar/stage), not *where* (rooms). **TASK-329 (add mixed timeslot+room repair Phase 7) is moot.**

## Caveat

This diagnostic checks the FINAL solution, not per-phase progression scores (the `/api/phase-progression` endpoint doesn't populate per-phase score values — only phase names). The final solution having 0 room-induced hard means any transient room conflicts introduced by Phase 5 were resolved by Phase 6. A per-phase-score check (if the endpoint is fixed to populate scores) would add detail but would not change the verdict.

_Method: jq analysis of `local/experiments/h43-generalized-20260715-090946.json` and `local/experiments/timefold-230-20260714-183726.json`._
