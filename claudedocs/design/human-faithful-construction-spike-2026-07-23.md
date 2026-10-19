# Human-faithful construction — spike design (TASK-396)

> Companion to backlog task **TASK-396** (the strategic pivot). This doc defines the **spike** that
> gates any rewrite of the 6-phase pipeline: prove the paradigm scales *before* touching the live
> solver. Fedora-side search-architecture decision; laptop is the designated machine for the spike
> (non-brief test).

## Goal
Prove or disprove that a **human-faithful constructive solver** — place hardest-first into a
hard-feasible slot only, box the rest (H0 / null timeslot), **bounded** chronological backtrack —
reaches near-0-hard at construction **END**, without exorbitant backtracking, on a representative
slice of the 2026/27 problem. The spike's go/no-go decides: full pipeline rewrite vs hybrid vs reject.

## Hypothesis
A constructive search that maintains the hard-feasibility invariant at every step (the human's
invariant — "every placed piece is feasible, the rest stay in the box, backtrack the most-recent on
stall") can place the large majority of the 12,301 lessons feasibly, leaving a small "box" for the
genuinely-constrained residual, with **bounded** backtracking. Construction ends near-0-hard; local
search then only places the box (minutes, not the current 60-min repair of -44,100 violations).

## Standalone — do NOT wire into the live pipeline
Exercise the constructive solver via a real-seed test fixture (mirror
`WeeklyTemplateConstructionRealSeedTest` / `CalendarioImportRealSeedStageTest`: a `SolverConfig` with a
single custom phase + a retained instance to read stats back). No live server, no `solverConfig.xml`
change. Reuse, don't reinvent:
- **VRP gate** — `Lesson.getAvailableTimeslots()`: encodes H4/H5/H6/H20/quota + H23/H24/H25 stage
  confinement in one precomputed membership check (the single authoritative "is this slot legal" gate).
- **In-memory feasibility checks** — the `canPlaceLesson` / `exceedsDailyCap` helpers
  `DayClusteredConstructionPhase` already uses (H1/H3/H7/H11).
- **Backtracking scaffold** — `BacktrackingPreAssigner`'s shape (the pre-assignment CSP already does
  exactly this for a subset).
- **H0 (null timeslot)** — the "box"; already supported by the model.

## Algorithm
1. **Order hardest-first (MRV — minimum remaining values):** most-constrained lessons first — the
   shared-teacher tight groups (G05, G10, …), fewest feasible slots, pinned/pseudo lessons last.
2. **Place:** for each lesson, find a hard-feasible slot (VRP gate + `canPlaceLesson`). Place the first
   feasible slot (tie-break cheaply toward H17 compactness). **Never** force into a conflict.
3. **Box:** if no feasible slot exists, leave the lesson null (H0). Record it as boxed.
4. **Bounded backtrack on stall:** if the box grows or progress stalls over N placements, undo the
   most-recent K placements (the user's "change the most-recent ones"), mark the triggering lesson's
   last-tried slot exhausted, retry. **Bounds are the spike's variables:** max undo depth `D`, max total
   undos `U`. The spike characterizes what `D`/`U` are viable.
5. **Terminate:** all placed, or box stops shrinking, or budget exhausted.

## Scope — escalate to characterize scaling
Run the slices in order; **stop and report at the first slice where backtracking goes exponential** —
that point defines the scaling ceiling and routes the decision to hybrid vs full-rewrite.
- **Slice A — hardest core:** the shared-teacher tight groups only (the ones that produce H3 knots
  today). Can the constructive solver place them feasibly with bounded backtrack?
- **Slice B — one full group:** all of one group's courses against the shared teachers' real availability.
- **Slice C — scaled-down full problem:** 3–4 groups with the real teacher-sharing + stage structure
  (cross-group teacher conflicts, the actual hardness source).
- **Slice D — full 12,301:** only if A–C succeed. Does it scale end-to-end?

## Measurements (the numbers that decide go/no-go)
- **construction-end HARD** (target ~0; baseline **-44,100**). Per slice.
- **box size** (unassigned lesson count) at construction end.
- **backtrack count** + total backtrack time + max undo depth reached.
- **total construction wall-time.**
- **scaling curve:** backtrack cost vs slice size (A<B<C<D) — roughly linear/polynomial (scales) or
  exponential (doesn't)?

## Go / no-go
- **GO (full rewrite viable):** by Slice C/D, construction-end hard ~0, box small, backtrack cost
  sub-linear-ish, wall-time well under today's 60-min LS.
- **HYBRID:** constructive solver places the hard core feasibly (Slice A/B) but doesn't scale to all
  12k — use it for the tight core, existing CH for the easy majority.
- **NO-GO:** backtracking explodes by Slice B/C, or construction can't reach near-0-hard even with a
  large box — the pure approach doesn't scale; keep scatter-and-repair or find a different lever.

## Non-goals (for the spike)
- Do **not** touch the live 6-phase pipeline.
- Do **not** optimize medium/soft — construction only needs hard-feasibility.
- Do **not** handle rooms (orthogonal, later phase).
- Do **not** solve box placement — that's LS's job, measured separately post-spike.

## Prerequisite
**Fix TASK-397 first** (the `TargetEndDateCalculator` hot-loop). Otherwise the spike's wall-time
numbers are polluted by the calc re-running on every UI poll during the test solves.

## Reuses the validation's before-number
The -44,100 construction-end hard and the 60-min ph2 are measured in
`claudedocs/spalmone-2026-27/validation-393-results/VERDICT.md` — the baseline this spike must beat.
