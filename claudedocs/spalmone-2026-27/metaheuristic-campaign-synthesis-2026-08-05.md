# Metaheuristic campaign synthesis, 2026-08-05

**Purpose:** consolidate the entire 2026-08-04/05 metaheuristic + ablation campaign into one
decisional framework so maintainer can choose the next INTERVENTION direction. Every finding below is
already measured, committed, and VERDICT-documented; this doc only consolidates and draws the
strategic implication. **NO ADOPTION** (the campaign is measurement; production stays as ratified).

**The campaign question:** where are the real schedule-quality levers after the staged-pipeline
ratifications (multi-start TASK-483, the repair passes, Tabu-Size7)? Are they in the medium
weights, the LS metaheuristic, or the orchestration (repair)? Answer below.

## Consolidated findings

### 1. Medium-constraint WEIGHTS are FLAT (knob sweep)
Sweeping five medium-constraint weight/numerator knobs across wide ranges (0.2x to 3x):
`planner.m32.numerator`, `planner.m34.weight`, `planner.m35.weight`, `planner.tag-capacity.weight`
(M-TC), `planner.m37.weight`. Result: moving each changes the SCORE NUMBER (self-penalty scaling)
but NOT the TIMETABLE. They are flat via either a forced residual (M32/M34/M35: cadence/template
locked by construction + the fill family) or satisfaction (M-TC: non-binding on the good seed).
**Implication: the solver is NOT tunable via medium weights. Do not spend solve time sweeping
them.** Evidence: `m32/m34/m35/m-tc-sweep-results/VERDICT-*.md`.

### 2. The LS metaheuristic ACCEPTOR is a WEAK lever on the staged pipeline
Two independent measurements converge:
- **Controlled benchmark (LS-only, old March dataset):** VND-FirstImprovement and its variants
  crush Tabu on HARD, ~25x (-242/-284 hard vs Tabu -6699/-6740). Proper VND (first-improvement,
  acl=1) is best; VND-HillClimbing (best-improvement, acl=1000) is worst. The benchmark harness
  was repaired first (commit be404746: compile API drift + SolutionFileIO Jackson + a runtime JAXB
  blocker from excluding timefold-solver-jaxb). Evidence: `task284-results/VERDICT-tabuvnd-benchmark-*`.
- **Staged pipeline (current 2026/27 seed):** that 25x hard advantage DOES NOT TRANSFER. VND-FI
  and Tabu both reach hard=0; VND-FI is only ~4.7% better on medium and 47% SLOWER. The controlled
  benchmark misled because it had no staged repair. **Implication: on the staged pipeline the LS
  acceptor only fine-tunes medium/soft (~5%); it does not move hard.** Evidence:
  `task284-results/VERDICT-vnd-staged-*`.
- **acceptedCountLimit (the one metaheuristic knob that MOVED the needle):** acl=100 beats the
  default 1000 on medium (-1000570 vs -1051618, ~5%), is faster (1314s vs 1592s), and feasible;
  acl=10000 breaks (-7498 H29). Same story as VND-FI: a ~5% MEDIUM lever, not a hard lever.
  Evidence: `acceptedcountlimit-sweep-results/VERDICT-*`. TASK-296 (ACL tune) is still To Do.

### 3. The orchestration (repair) layer owns hard; HoleRepair is the one hard repair lever
Repair-pass ablation (disable one at a time), on BOTH a good basin and a bad basin (grasp-seed-1,
the H17 -9 structural bad seed):
- **HoleRepair is the load-bearing hard repair on BOTH basins** (good: -6; bad: -10, i.e.
  hole-repair-off sent bad-basin hard from -12 to -22). It is the ONE repair with a hard effect.
- **ExactFinisher is hard-inert on both basins** (its value is the boxed-H0 tail, which the
  bad-seed residual is not; on a good basin it is inert with boxed=0 at entry).
- **AnchorRepair is hard-inert on both basins but is the DAY-START lever** (good 98.6% vs 99.5%;
  bad 97.4% vs 100.0%).
- **The bad-seed residual SPLITS into a closeable part (~10 hard, HoleRepair closes it) and a
  STRUCTURAL uncloseable part (-12 remains with all repair ON: the @10 shell-game, TASK-481).**
  Multi-start (basin selection, TASK-483 RATIFIED) is the ONLY cure for the structural part.
  Evidence: `task284-results/VERDICT-repair-ablation-*-2026-08-0{4,5}.md`.

## The lever hierarchy (what moves what, and by how much)

| Lever | Moves | Magnitude | Status |
|-------|-------|-----------|--------|
| Multi-start (basin selection, TASK-483) | structural HARD (bad-seed residual) | the difference between hard<0 and hard=0 on bad seeds | RATIFIED, in place |
| HoleRepair | HARD (closeable part) | ~10 hard on bad basin, ~6 on good | default ON; bounded by the structural residual |
| acceptedCountLimit / LS acceptor | MEDIUM | ~5% medium (acl=100 vs 1000; VND-FI vs Tabu) | default 1000; TASK-296 To Do |
| AnchorRepair | DAY-START (medium) | 1-3pp day-start | default ON (TASK-433 ratified) |
| Medium-constraint weights (m32/m34/m35/m-tc/m37) | nothing (timetable) | FLAT | dead end |
| LS acceptor for HARD on staged | nothing | inert (VND-FI staged hard=0 = Tabu) | dead end for hard |

## Strategic implication

The productive directions are NOT weight tuning or acceptor tuning. They are:
1. **Multi-start for the structural hard residual** (TASK-483, RATIFIED, in place). This is THE
   hard=0 lever; the campaign confirms it. Nothing else closes the structural @10.
2. **HoleRepair for the closeable hard margin** (the one repair hard-lever). It already does its
   ~10 on bad basins; its ceiling is the structural residual. The open question is whether its
   ~10 is its true ceiling or whether failure-characterization finds more closeable holes it
   currently misses.
3. **AnchorRepair for day-start** (load-bearing, already ON). Day-start tuning investment lands
   here.
4. **acceptedCountLimit (TASK-296) for medium quality** (the one metaheuristic lever that moved
   the needle, ~5% medium). This is the productive MEDIUM direction if schedule-quality (not
   hard=0) is the goal.

What is EXHAUSTED (measured dead ends): medium-constraint weight sweeps (flat), and the LS
acceptor as a HARD lever on the staged pipeline (VND-FI inert). Do not revisit these for hard.

## Campaign status: solved / marginal / exhausted

- **SOLVED:** hard=0 is a basin-selection problem, handled by multi-start (ratified). The
  structural H17 bad-seed residual is uncloseable by any repair or metaheuristic; multi-start
  avoids it.
- **MARGINAL (bounded levers):** HoleRepair (~10 hard, bounded by the structural residual);
  acceptedCountLimit / LS acceptor (~5% medium); AnchorRepair (1-3pp day-start).
- **EXHAUSTED (dead ends):** medium-weight sweeps; LS acceptor for hard on staged.

## Recommended next gated intervention

In priority order (maintainer decides; these are gated):
1. **TASK-296 acceptedCountLimit tune** (the one lever that MOVED the needle). Finer sweep
   (1/10/50/100/500) + n>1 on the staged pipeline to pin the medium optimum. Productive for
   schedule QUALITY (medium). Lowest risk (medium-tier, no hard impact).
2. **HoleRepair failure characterization** (read-only): on the bad-basin run's hole data, identify
   WHICH holes HoleRepair closes vs where it fails. If it fails only on the structural @10, its
   ~10 is the confirmed ceiling and refinement is futile; if it misses closeable holes, refinement
   has room. Decisional for whether hard-tuning investment in HoleRepair pays off.
3. Declare the metaheuristic + repair investigation COMPLETE: the levers are characterized,
   multi-start handles the structural problem, the rest are marginal. Shift focus to other
   campaign threads.

The metaheuristic-is-the-lever story is now NUANCED and complete: the lever is real for MEDIUM and
for the pre-repair landscape, but on the staged production pipeline the ORCHESTRATION layer
(multi-start + repair) secures hard=0, and the LS metaheuristic is a medium-quality knob.

## ADDENDUM 2026-08-05 (post-synthesis): TASK-296 ruled out, investigation EXHAUSTED

Two follow-ups after this synthesis closed the remaining open threads:

1. **HoleRepair failure characterization** (VERDICT-holerepair-characterization-2026-08-05):
   HoleRepair's ~10-hard bad-basin contribution is its CEILING (it closes the closeable G08
   noon holes; it is inert on the 12 structural G11 @10/@11 Wednesday shell-game holes that
   no local repair can close). "Refine HoleRepair" is RULED OUT. The hard side is fully
   characterized: structural 12 -> multi-start (DONE); closeable 10 -> HoleRepair (DONE, at
   ceiling).

2. **TASK-296 finer ACL sweep** (VERDICT-task296-2026-08-05): NO robust sweet spot. The
   medium-vs-ACL curve is state-dependent and non-monotonic at n=1 (prior 3090-pin state:
   lower ACL better; redo 2907-pin state: higher ACL better). The ~5% medium range ACL spans is
   at the ~6% pre-assign run-to-run variance floor, so the effect is NOT robust. ACL does NOT
   move hard (only 10000 breaks). ACL is NOT a reliable adoption candidate. The default 1000
   stands. Side finding: the source=input pre-assign is not fully deterministic (~6% pinned
   variance run-to-run), which confounds cross-session comparisons.

**The synthesis's recommended next interventions are now all resolved:** TASK-296 (ACL) ruled
out (non-robust); HoleRepair refinement ruled out (ceiling); AnchorRepair already ON; multi-start
already ratified. The metaheuristic + repair investigation is EXHAUSTED. Every candidate lever
(medium weights, LS acceptor/ACL, repair passes) is measured flat / inert / non-robust / at
ceiling. The structural hard=0 problem is solved by multi-start (ratified, in place). The
recommendation is now to DECLARE THE INVESTIGATION COMPLETE and shift focus to other campaign
threads.
