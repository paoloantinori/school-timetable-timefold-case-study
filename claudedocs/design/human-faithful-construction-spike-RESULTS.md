# Human-faithful construction spike — RESULTS & GO/NO-GO (TASK-396)

**Date:** 2026-07-23 (laptop). **Branch:** `feature/cartesian-block-entity` @ `16227a4e` (TASK-397 landed —
clean wall-times). **Harness:** `src/test/java/com/school/planner/dev/HumanFaithfulConstruction.java`
(the plain-Java constructive solver) + `src/test/java/com/school/planner/bootstrap/HumanFaithfulConstructionSpikeTest.java`
(@QuarkusTest, real-seed fixture, scores via `SolutionManager`). Dev/test only — **not** wired into the
live 6-phase pipeline. Design brief: `human-faithful-construction-spike-2026-07-23.md`.

## VERDICT: GO — and simpler than the design feared

The human-faithful constructive approach **scales to the full problem and reaches near-feasible-by-
construction**, and the spike's headline risk (backtracking hitting Timefold's Branch-&-Bound
scalability wall, rated 0/5) **does not materialize — because backtracking turns out to be
unnecessary**. Pure greedy MRV + box-the-residual already places 98.7% of lessons feasibly at full
scale in ~114 ms, leaving a 1.3% box that local search handles.

**Viable D/U: D = 0** (no backtrack). The bounded-chronological-backtrack knob the design made the
spike's variable is **not needed**: it does not shrink the box at any D/U tested (see below).

## The decisive comparison (placed-lesson hard, full problem)

| | construction-end hard (placed) | mechanism |
|---|---|---|
| **Live CH (scatter-and-repair)** | **-44,100** | `forcePlaceLeftovers` force-places the residual into conflicts |
| **Spike (greedy + box)** | **-8** (H17 only, 3 gap matches) | boxes the residual (H0); placed lessons are conflict-free by construction |

Hard breakdown of the spike's full-problem construction (D=0), via the constraint provider:
- **H0 - Unassigned lesson: -141** (141 matches = the box, the unplaceable residual)
- **H17 - Vertically compact: -8** (3 matches = gap residual, the only placed-lesson hard)
- **H1/H3/H7/H11: 0** (confirmed both by the in-memory feasibility checks AND by direct conflict
  counting on placed lessons — teacher conflicts = 0, group conflicts = 0)

So placed lessons are feasibility-pure (only H17 gaps remain, the known residual the live solve also
ends on at -1). The -44,100 scatter is an artifact of force-placing the ~141-lesson residual into
conflicts; boxing it instead removes the scatter entirely.

## Measurements

### Backtrack sweep (full problem, 10,501 lessons) — does backtrack shrink the box / blow up?

| D (undo depth) | U (total undos) | placed | boxed | backtracks | time | hard (placed) |
|---|---|---|---|---|---|---|
| 0 | 0 | 10,360 | **141** | 0 | 153 ms | -8 (H17) |
| 20 | 500 | 10,360 | **141** | 500 (hit U) | 189 ms | -8 |
| 100 | 2,000 | 10,360 | **141** | 2,000 (hit U) | 310 ms | -9 |
| 500 | 10,000 | 10,360 | **141** | 10,000 (hit U) | 1,183 ms | -8 |

**The box does not shrink at any D/U.** Chronological undo-recent cannot target the specific earlier
placement blocking a boxed lesson (MRV processes the most-constrained — the box candidates — first,
so by the time one is boxed there is little recent context to undo productively). Backtrack fires
(hits the U cap) but is ineffective — and crucially **bounded and cheap** (10,000 backtracks in 1.2 s,
no exponential wall). Conclusion: **D=0 greedy is the viable config; backtrack machinery is not worth
its complexity for this problem.**

### Scaling curve (D=0 greedy, time + box% vs size)

| slice | lessons | placed | boxed | box% | time |
|---|---|---|---|---|---|
| A — tight core (G05 + G10) | 1,660 | 1,660 | 0 | 0.0% | 36 ms |
| C — 6 groups, shared teachers | 4,981 | 4,942 | 39 | 0.8% | 70 ms |
| D — full, all 14 groups | 10,501 | 10,360 | 141 | 1.3% | 114 ms |

Time is **linear/sub-linear** (36→114 ms across ~6× lessons — the per-lesson VRP gate is cached), box%
**grows slowly** with cross-group teacher contention (0→0.8→1.3%). No exponential wall at any scale.
The brief's "stop at the first slice where backtracking goes exponential" never triggers — there is no
backtracking dependency to blow up.

## Recommended shape (for Fedora adoption)

1. **Greedy MRV + box, D=0** — replace `DayClusteredConstructionPhase`'s `forcePlaceLeftovers`
   (force-place residual → -44,100 scatter) with **box-the-residual** (leave timeslot null → H0). This
   is the single change that removes the scatter; it is far simpler than the "bounded backtrack"
   design the spike was scoped around.
2. **`allowsUnassigned = true` on `Lesson.timeslot`** (Lesson.java:89 — currently NOT set, which is
   the mechanical root cause of the scatter: the solver is forced to assign every timeslot). The
   existing **H0** penalty then keeps the box small (a model change → primary directive, Fedora).
3. **Local search places the box** (the 1.3% residual) — the same LS that today repairs the -44,100
   scatter now has ~141 boxed lessons to place as capacity opens, a strictly smaller job. Target: 0
   box, 0 hard (the live solve already reaches -1/0 from a far worse start).
4. **No backtracking machinery needed.** A conflict-directed backtrack (target the blocking
   placement, not the most-recent) might shave the box further, but at 1.3% it is not worth the
   complexity — defer unless LS struggles to clear the box.

## Caveats / what this does NOT settle
- **Fixture simplifications** (matching the L2 test's documented scope): no golden/Calendario import
  → no pinned lessons; no Stage pseudo-course (~2,080 lessons); `isLab`/room-tag classification at
  Java defaults; rooms out of scope (H2/H-M2 not exercised). These narrow the fixture vs a production
  solve but do not change the scaling conclusion — the full non-Stage problem (10,501 lessons) already
  includes the real cross-group teacher contention that produces the H3 knots. The real solve's
  pinned-heavy population would only make the box *smaller* (pinned lessons are pre-placed, not boxed).
- **The box (1.3%) is LS's job**, not solved here (a stated non-goal). Whether LS clears it to 0 is the
  next measurement, post-adoption.
- **Backtrack ineffectiveness is specific to chronological undo-recent + MRV ordering** — a smarter
  conflict-directed backtrack could place some of the 141. Not tested (the brief asked for bounded
  chronological, which is what the user's "change the most recent ones" maps to); reported as low-value,
  not proven impossible.
- **n = the real seed** (deterministic; the solver is pure-greedy so run-to-run variance is zero).

## Net
The strategic pivot (TASK-396) is **vindicated by measurement**: a human-faithful construction that
maintains the hard-feasibility invariant at every step scales to the full 2026/27 problem in ~114 ms
and reaches placed-hard -8 (vs the live CH's -44,100) — and it does so **without backtracking**, which
is the simpler, stronger outcome than the "needs bounded backtrack" hypothesis the spike set out to test.
The decision (full rewrite with `allowsUnassigned` + box, vs hybrid) is Fedora's on this evidence;
this spike says the approach works and is cheaper than expected.
