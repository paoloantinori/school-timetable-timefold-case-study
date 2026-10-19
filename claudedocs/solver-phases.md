> **AGGIORNAMENTO 2026-07-27 (TASK-420):** la pipeline è ora a **5 fasi**. La vecchia
> Fase 3 (LS Advanced, RuinRecreate) è stata RIMOSSA: inerte in 6 misure consecutive
> (val413 r1/r2, val408 r1/r2 con score invariato alla 4a cifra, val403-early r1/r2 con
> partenza hard-pulita), al costo di 120-160s per run. Di conseguenza la fase
> "Repair + Compaction" (introdotta da TASK-393/401) è la Fase 3, la CH Room Assignment
> è la Fase 4 e la LS Room Refinement è la Fase 5. Questo documento usa direttamente la
> numerazione live corrente. La sezione storica "TASK-183" più in basso descrive lo stato
> del 2026-03-10 (quando esisteva ancora la vecchia Fase 2 "CH - Fallback Placement",
> eliminata da TASK-378) e va letta come registro storico, non come stato attuale.

# Solver Multiphase Architecture

The Timefold solver is configured with a **5-phase pipeline**: one construction heuristic (CH) phase for timeslot placement, one local search (LS) phase for timeslot optimization, one deterministic repair + compaction phase that clears the box and re-closes any gaps the local search reopened, and one CH + LS pair for deferred room assignment. Each phase has a distinct goal, move palette, and termination strategy. This design mirrors how a human scheduler would approach the problem — place the hardest items first, optimize timeslots, close any remaining gaps, then assign rooms last.

**Configuration file:** `src/main/resources/solverConfig.xml`

**TASK-183 (2026-03-10):** Room assignment is **deferred** until AFTER all timeslot optimization (Phases 2-3). This matches the human planner approach: perfect timeslot placement FIRST, then assign rooms.

> **RELATED:** [Orchestration Layer Architecture](orchestration-layer-architecture.md) describes the
> deterministic layer that wraps this 5-phase pipeline: staged pinning, tier-scoped gating, repair
> passes with improve-or-noop contracts, the ExactFinisher sub-solver, and the framework for when to
> use stochastic versus exact operators.

> **MULTI-START WRAPPER (TASK-483, ratified DEFAULT ON 2026-08-03):** the campaign
> reference solve mode (`POST /api/schedule/solve-staged`) does NOT run this 5-phase
> pipeline once - it wraps the whole staged cycle (which contains this pipeline plus
> the repair operators) in a **multi-start retry loop**: attempt 1 is the deterministic
> path of always, attempts >= 2 use GRASP-randomized construction; the loop keeps the
> best (hard, medium) result and **stops at the first 0-hard**. Opt out with
> `-Dplanner.multistart.enabled=false`. Narration fields `stagedAttempt` /
> `stagedMaxAttempts` publish the attempt count to the UI. This is how a single
> `solve-staged` call recovers a bad construction seed (p(0-hard) ~0.75 per single
> construction). Details in `claudedocs/orchestration-layer-architecture.md`.
>
> **M-TC per-retry (TASK-486, ratified DEFAULT ON 2026-08-04):** inside that loop the
> tag-capacity constraint (M-TC) is armed per-attempt - attempt 1 runs M-TC OFF (the
> good basin), retries run M-TC ON (the cure for the room-tier bad basin). The loop
> owns `planner.tag-capacity.enabled` for its duration. Opt out with
> `-Dplanner.multistart.mtc-per-retry.enabled=false`.

## Architecture Overview

```
+--------------------------------------------------------------------------+
|                         SOLVER PIPELINE                                  |
|                                                                          |
|  Phase 1: CH Lesson Placement (DayClustered)          ~1-6 min           |
|      |                                                                   |
|      v                                                                   |
|  Phase 2: LS Compactness (change/swap/pillar, tabu 7)  30-120 min        |
|      |                                                                   |
|      v                                                                   |
|  Phase 3: Repair + Compaction (deterministic)          seconds           |
|      |                                                                   |
|      v                                                                   |
|  Phase 4: CH Room Assignment                           ~5-10 min         |
|      |                                                                   |
|      v                                                                   |
|  Phase 5: LS Room Refinement                           2-10 min          |
|                                                                          |
|  Global safety net: 8-hour hard stop (28,800s)                           |
|  KEY: Phases 1-3 = timeslot, Phases 4-5 = rooms (assigned LAST)          |
+--------------------------------------------------------------------------+
```

## Phase 1: Construction Heuristic — Lesson Placement

**Goal:** Place all `Lesson` entities into timeslots using a template-first weekly construction heuristic.

**How it works (TASK-381 "L2", 2026-07-20):** `DayClusteredConstructionPhase` first builds ONE representative school week per group (`WeeklyTemplateBuilder` — a school-wide, cross-group-consistent weekly pattern honoring calendar windows, quota-group lab/aula budgets, teacher/group majority-blocked cells, H7/H11 daily caps, and M28's morning-first/afternoon-last-resort rule), then STAMPS that template across every matching school date. Weekly recurrence — the school's own template-first practice — is produced BY CONSTRUCTION instead of hoped for from local search (measured 16.9% under the old pure round-robin CH vs ~75-81% weekly recurrence AT CONSTRUCTION END, measured on the real seed — varies run-to-run from the shared suite's IDENTITY-based ordering, always well above the 50% assertion floor; whether it survives the LS phases is measured live per run, see `claudedocs/spalmone-2026-27/experiment-log.md`). The pre-existing day-clustered round-robin pass then runs as the REPAIR TAIL for whatever the stamp couldn't fit (quota-day mismatches, teacher clashes on a specific date, courses the template exhausted early), followed by the least-conflict force-place pass for any remaining leftovers. This phase assigns **timeslots only, no rooms**: each placed lesson keeps `room = null` through Phases 1-3 (the phase actively nulls any prior room; rooms are assigned for the first time in Phase 4). TASK-378 eliminated the old separate fallback CH that used to assign rooms, because Timefold's `QueuedEntityPlacer` re-placed already-initialized entities and destroyed the custom phase's result.

**Entity ordering:** Custom difficulty scoring — most constrained lessons placed first (repair tail only; the template stamp orders by hardest-constrained teacher first, see `WeeklyTemplateBuilder`'s own javadoc).

**Duration:** ~1-6 minutes for ~6,000 lessons.

**Typical output:** Most lessons placed with timeslots assigned. Some may remain unplaced due to constraint conflicts.

## Phase 2: Local Search — Compactness

**Goal:** Minimize gaps (S1/H17), reward same-subject blocks (S17, TASK-381), achieve horizontal/vertical compactness. Uses single-entity and pillar moves.

**Move Palette:**

| Move Type | Weight | Entity | Variable | Purpose |
|-----------|--------|--------|----------|---------|
| Change (timeslot) | 5.0 | Lesson | `timeslot` | Primary compactness lever |
| Swap (timeslot) | 3.0 | Lesson | `timeslot` | Exchange two lessons' timeslots |
| Pillar Change | 2.0 | Lesson | `timeslot` | Move all lessons at a timeslot together |
| Pillar Swap | 1.0 | Lesson | `timeslot` | Exchange entire timeslot schedules |

**Pillar moves** preserve consecutive student patterns (S7) while resolving teacher conflicts (H1). They shift ALL lessons at a source timeslot to a destination timeslot together.

**CompositeMoveFilter:** Filters out auto-generated `CompositeMove(room, timeslot)` which wastes ~8% of eval budget with zero improvements.

### Metaheuristic

- **Acceptor:** Entity Tabu (size 7) — prevents cycling back to recently explored solutions
- **Forager:** `acceptedCountLimit=1000` — evaluate many moves per step to escape local optima

### Termination

- **Diminished returns:** PT2M sliding window, 0.1 minimum improvement ratio (primary auto-terminate mechanism)
- **Unimproved limit:** 600 seconds (10 minutes) - added TASK-381 "L3" as a second, independent auto-terminate: run 12 measured this phase burning the full hard cap despite diminishedReturns (a re-solve of an already-converged schedule cost 60+ min)
- **Hard cap:** 3,600 seconds (1 hour)

### What it can't do

Phase 2's single-entity moves (Change, Swap) and pillar moves can resolve most conflicts but struggle with **coordinated multi-entity violations**, and they reliably leave a handful of boxed (unassigned) lessons whose few feasible cells stochastic selection misses. A RuinRecreate local search used to sit after this phase for exactly that reason; TASK-420 removed it after 6 consecutive measurements in which it changed nothing, because what actually resolves those residuals is Phase 3's deterministic repair (direct enumeration of the ~3-7 feasible cells per stuck lesson).

**Typical output:** Hard score improves significantly. Compactness metrics improve. May plateau at ~-10 to -30 hard score, with the residual collapsing in Phase 3.

## Phase 3: Repair + Compaction (Deterministic Box-Clearing and Gap-Closing)

**Class:** `com.school.planner.solver.CompactionPhase` (Timefold `customPhaseCommandClass`), running `BoxRepair` (TASK-401) around `StudentDayCompaction` (TASK-390/393)

**Goal:** Two deterministic clean-ups nothing after this phase can do (Phases 4-5 are room-only, so the timeslot layout is frozen here): (1) **clear the box**, i.e. place any lessons still unassigned (TASK-398's H0 residual) into VRP-legal, conflict-free, cap-checked, H17-safe cells; (2) **re-close avoidable H17 student-day gaps** that Phase 2's local search reopened while resolving a bigger hard violation elsewhere (trading one H17 -1 for a bigger H3 -1000 fix, a net hard gain the acceptor correctly takes, but one that leaves an avoidable gap behind).

**Why enumeration for the box (TASK-399's measurement):** each lesson the LS leaves boxed has only ~3-7 feasible cells among ~1,000 VRP-legal ones — a 1-in-~200 regime that stochastic move selection reliably misses but a direct scan trivially finds. Validated on the TASK-398 run-2 checkpoint (`BoxRepairCheckpointTest`): box 6 → 0, hard -6 → 0, zero conflicts/gaps added.

**How it works:** `BoxRepair.repair` runs first (filling an internal hole beats relocating a lesson into it), then `StudentDayCompaction.compact` — which since TASK-405 also LEFT-ANCHORS each contiguous group-day run toward the group's first calendar-open slot (the golden anchors 99.3% of days there; the move-max-to-lo-1 trick preserves contiguity, so it is H17-neutral and conflict-gated like every other move) — — the exact same gap-closing logic already used at the tail of Phase 1's construction (TASK-390) — then a second `BoxRepair` pass if anything is still boxed (the compaction's relocations free cells the first pass couldn't use). All passes are **improve-or-noop and conflict-free**: a lesson is placed/moved only when doing so cannot introduce a new hard violation, and nothing changes when there is nothing to fix.

**Duration:** Seconds. Deterministic passes, not a metaheuristic search, so there is no `secondsSpentLimit`/`unimprovedSecondsSpentLimit` termination configuration; it runs once and the pipeline moves on to Phase 4.

**Typical output:** On runs where the LS left boxed lessons or reopened H17 gaps, those are repaired without touching anything Phase 2 already fixed; a no-op when there is nothing to fix. Since TASK-420 this is where the residual hard collapses (the removed RuinRecreate phase never contributed to it).

## Phase 4: Construction Heuristic - Room Assignment (FIRST TIME)

**Goal:** Assign rooms to all Lessons for the **FIRST TIME** using construction heuristic.

**Why deferred:** Human schedulers assign rooms LAST — after timeslots are perfectly placed and all conflicts resolved.

**Move type:** `changeMoveSelector` — assigns `room` only, with `DECREASING_DIFFICULTY` sorting.

**Duration:** ~5-10 minutes.

**Typical output:** All Lessons have `room` assigned. Initial H-M2 (room suitability) violations resolved.

## Phase 5: Local Search - Room Refinement

**Goal:** Polish the initial room assignment from Phase 4. Optimize H-M2 (room suitability), M3 (capacity), S10 (generic in labs), S11 (room affinity).

### Move Palette

| Move Type | Entity | Variable | Purpose |
|-----------|--------|----------|---------|
| Change (room) | Lesson | `room` | Room reassignment |
| Swap (room) | Lesson | `room` | Exchange two lessons' rooms |

Only room moves, no timeslot changes. This prevents undoing the timeslot optimization work from Phases 1-3.

### Metaheuristic

- **Acceptor:** Entity Tabu (size 7)
- **Forager:** `acceptedCountLimit=1000`

### Termination

- **Diminished returns:** PT1M sliding window, 0.05 minimum improvement ratio
- **Hard cap:** 600 seconds (10 minutes)

**Typical output:** Room quality optimized. H-M2 violations resolved, M3 and S11 penalties minimized.

## Global Termination

An 8-hour hard stop (`secondsSpentLimit=28800`) acts as a safety net for unattended overnight runs. The solver typically terminates much earlier via the per-phase unimproved limits.

## Why Multiple Phases?

Most Timefold tutorials show a single CH + single LS configuration. Our **5-phase design** has several advantages:

1. **Divide and conquer:** Each phase has a focused objective and tailored move palette.

2. **Progressive refinement:** Phase 1 carves the rough shape, Phase 2 compacts, Phase 3 repairs the box and re-closes the gaps the search reopened, Phase 4 assigns rooms, Phase 5 polishes rooms.

3. **Mirrors human expertise:** Place lessons first (hardest), compact, tidy up the leftovers, assign rooms LAST.

4. **Deterministic cleanup instead of a second stochastic phase:** Phase 3 (TASK-393/401) costs seconds, is a no-op when there is nothing to fix, and is measurably what resolves the LS residual. TASK-420 removed the RuinRecreate LS that used to precede it precisely because the deterministic pass had already made it redundant (6 consecutive runs with zero contribution, 120-160s each).

## Phase Timing (Typical Run)

| Phase | Duration | Key Metric |
|-------|----------|------------|
| Phase 1 (CH: Lesson Placement) | ~1-6 min | Most lessons placed |
| Phase 2 (LS: Compactness) | 30-120 min | Hard score improves, compactness metrics |
| Phase 3 (Repair + Compaction) | Seconds | Box cleared, H17 gaps reopened by the LS re-closed; no-op otherwise |
| Phase 4 (CH: Room Assign) | ~5-10 min | Rooms assigned FIRST TIME |
| Phase 5 (LS: Room Refinement) | 2-10 min | Room quality polish |

## Relationship to Pre-Assignment

The pre-assignment pipeline (CSP-based `HybridPhase1aService`, see
`claudedocs/pre-assignment-algorithms.md`) runs **before** the solver. It pins
lessons deterministically, reducing the solver's search space. There is **no
separate `LabBlock` entity** (removed TASK-228/267): labs are ordinary pinned
`Lesson` rows. By the time the solver starts:

- Acconciatura and other lab rotations are pinned (timeslot + room) via the CSP
- Constrained-teacher lessons are pinned (TASK-435, DEFAULT ON)
- Professionalizzanti lessons on stage days are pinned
- The solver only needs to place the remaining unpinned lessons and optimize
  their arrangement

## Configuration Reference

| Parameter | Phase 2 | Phase 5 |
|-----------|---------|---------|
| `entityTabuSize` | 7 | 7 |
| `acceptedCountLimit` | 1000 | 1000 |
| `unimprovedSecondsSpentLimit` | 600 (TASK-381; also diminishedReturns PT2M/0.1) | none (diminishedReturns PT1M/0.05) |
| `secondsSpentLimit` (hard cap) | 3600 | 600 |
| Pillar moves | Yes (Change 2x, Swap 1x) | No |
| Swap moves | Yes (3x timeslot) | Yes (1x room) |
| Room moves | No | Yes (Change 2x, Swap 1x) |
| CompositeMoveFilter | Yes | No |

Phases 1, 3 and 4 have no entry above: they are a custom construction phase, a deterministic `customPhaseCommandClass` and a Timefold construction heuristic respectively, so none of these local-search tunables apply to them.

## TASK-183: Deferred Room Assignment (2026-03-10, historical)

**Note (TASK-420):** this section is a historical record of the pipeline as it stood in 2026-03-10, when a separate "CH - Fallback Placement" phase (Phase 2 below) still existed. That phase was eliminated by TASK-378 (2026-07-18); a "Compaction" phase was inserted by TASK-393 (2026-07-22); the RuinRecreate LS (Phase 4 in the table below) was removed by TASK-420 (2026-07-27). Phase numbers in this section reflect 2026-03-10's numbering, not the current pipeline documented above.

### Summary

Room assignment is now **deferred** until AFTER all timeslot optimization. This matches the human planner approach: perfect timeslot placement FIRST, then assign rooms.

### Key Changes (2026-03-10 design, superseded)

Historical note: that design listed six phases, adding a "CH - Fallback Placement" after Phase 1 (removed by TASK-378) and an "LS - Advanced (RuinRecreate)" after the compactness LS (removed by TASK-420); its acceptance criteria (including XSD validation) were all verified at the time, and the pipeline in force today is the 5-phase one documented above.
