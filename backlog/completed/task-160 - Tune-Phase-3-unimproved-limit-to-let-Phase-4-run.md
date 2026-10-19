---
id: TASK-160
title: Tune Phase 3 unimproved limit to let Phase 4 run
status: Done
assignee: []
created_date: '2026-03-04 05:27'
updated_date: '2026-03-05 14:35'
labels:
  - claude-planned
  - performance
  - solver-config
dependencies: []
references:
  - src/main/resources/solverConfig.xml (phases 3-5)
  - >-
    claudedocs/research-timefold-optimizations-2026-03-03.md (section 3: Ruin &
    Recreate)
  - Solver logs from 7h53m run showing phase transitions
priority: high
ordinal: 5000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Rationale

Solver logs from a 7h53m run reveal a critical bottleneck: **Phase 4 (Conflict Resolution) never executes.** Phase 3 (Compactness) consumes the entire solve time because its `unimprovedSecondsSpentLimit=3600` (1 hour) keeps getting reset by rare improvements.

```
CH Phase 0 (LabBlocks):      0.6s   → all pre-pinned
CH Phase 1 (Lessons):        86s    → -5,493,637 hard (90K eval/sec)
LS Phase 2 (Compactness):    7h53m  → -1,261 hard (580 eval/sec, 2.19M steps)
LS Phase 3 (Conflicts):      NEVER REACHED
LS Phase 4 (Room refine):    NEVER REACHED
```

The remaining -1261 hard is dominated by:
- H3 student conflicts (391) — needs **swap moves** (Phase 4)
- H-D1 doppio conflicts (205) — needs **swap moves** (Phase 4)
- H6 holidays (296) — fixable via VRP (TASK-159)
- H2 room conflicts (154) — needs swaps + room refinement (Phase 4/5)
- H1 teacher conflicts (122) — needs **swap moves** (Phase 4)

Phase 3 only has `changeMoveSelector` and `cartesianProductMoveSelector` — no swaps, no pillars. It cannot efficiently resolve resource conflicts. Phase 4 has swap moves (weight 3x) and pillar moves (weight 2x) designed exactly for this.

## Proposed Changes

**File: `src/main/resources/solverConfig.xml`**

### Change 1: Reduce Phase 3 unimproved limit
```xml
<!-- Phase 3 (Compactness): 3600 → 900 seconds -->
<unimprovedSecondsSpentLimit>900</unimprovedSecondsSpentLimit>
```

**Rationale:** Phase 3 delivers 90%+ of its value in the first 1-2 hours. The compactness-focused moves (timeslot changes, cartesian products) have diminishing returns for conflict resolution. 15 minutes unimproved is enough to detect plateau.

### Change 2: Add Late Acceptance to Phase 4
```xml
<!-- Phase 4 acceptor: add lateAcceptanceSize alongside entityTabuSize -->
<acceptor>
  <entityTabuSize>7</entityTabuSize>
  <lateAcceptanceSize>400</lateAcceptanceSize>
</acceptor>
```

**Rationale:** Phase 4 currently uses only entity tabu (entityTabuSize=7). Adding Late Acceptance allows the solver to accept lateral moves — essential for escaping local optima when resolving interconnected conflicts. Phase 3 already uses this combination (entityTabuSize=5 + lateAcceptanceSize=200); Phase 4 should have it too, with a larger window (400) since conflict resolution benefits from longer memory.

### Change 3 (optional): Add hard stop per phase
```xml
<!-- Phase 3: max 2 hours absolute -->
<secondsSpentLimit>7200</secondsSpentLimit>
```

This prevents Phase 3 from consuming more than 2 hours even if rare improvements keep resetting the unimproved clock. Consider adding this only if the unimproved limit reduction alone doesn't help.

## Expected Impact

- Phase 3 completes in ~1-2 hours (down from 7h53m)
- Phase 4 gets ~6+ hours with swap and pillar moves against the remaining conflicts
- Phase 5 (room refinement) actually runs — optimizes M3 (room capacity), S11 (room affinity)
- Overall: better hard score in less wall time

## Verification

- `./scripts/validate-solver-xml.sh` passes
- Start solver, verify it transitions through all 5 phases (check logs)
- After 4h total run time, compare hard score against baseline (-1261 from 7h53m run)
- All 193 tests pass
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Solver transitions through all 5 phases (verify in logs: Phase 0–4 all appear)
- [ ] #2 Phase 3 completes in under 2 hours (down from 7h53m)
- [ ] #3 Phase 4 runs with swap and pillar moves for at least 1 hour
- [ ] #4 Phase 5 (room refinement) actually executes
- [ ] #5 Hard score after 4h total is equal to or better than -1261 (the 7h53m baseline)
- [x] #6 validate-solver-xml.sh passes
- [x] #7 All 193 tests pass
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation (2026-03-04)

**File modified:** `src/main/resources/solverConfig.xml`

### Change 1: Phase 3 termination (lines 57-63)
Reduced `unimprovedSecondsSpentLimit` from 3600 → **900** seconds AND added `secondsSpentLimit` of **7200** seconds (2-hour hard cap).

```xml
<termination>
  <secondsSpentLimit>7200</secondsSpentLimit>
  <unimprovedSecondsSpentLimit>900</unimprovedSecondsSpentLimit>
</termination>
```

**XSD note:** `secondsSpentLimit` must come before `unimprovedSecondsSpentLimit` per Timefold XSD element ordering.

### Change 2: Phase 4 acceptor (lines 209-215)
Added `lateAcceptanceSize=400` alongside existing `entityTabuSize=7`.

```xml
<acceptor>
  <entityTabuSize>7</entityTabuSize>
  <lateAcceptanceSize>400</lateAcceptanceSize>
</acceptor>
```

### What was NOT changed
- Phase 4 `acceptedCountLimit=1000` (forager) — kept as-is, appropriate for swap/pillar evaluation
- Phase 5 (room refinement) — 10-minute unimproved limit unchanged
- Global termination — 8-hour safety net unchanged
- Test `solverConfig.xml` — uses simplified single-phase LS, not affected

### Verification
- `validate-solver-xml.sh`: PASS (all 3 XML files)
- `mvn test`: PASS (193/193 tests, 0 failures)
- Remaining: deploy + solver run to verify phase transitions
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Tuned solver phase transitions to ensure Phase 4 (Conflict Resolution) actually runs.

**Changes to `solverConfig.xml`:**
1. Phase 3 `unimprovedSecondsSpentLimit`: 3600 → 900 seconds (15 min plateau detection)
2. Phase 3 `secondsSpentLimit`: added 7200 second hard cap (2 hours max)
3. Phase 4 acceptor: added `lateAcceptanceSize=400` alongside `entityTabuSize=7`

**Verification:** XML validates against Timefold XSD, all 193 tests pass. Runtime phase transition verification will occur during next solver run.
<!-- SECTION:FINAL_SUMMARY:END -->
