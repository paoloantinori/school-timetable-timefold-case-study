# Knob-sweep synthesis - medium-constraint WEIGHTS are not the lever

**Status:** consolidation of an already-measured series. READ-ONLY. NO ADOPTION.
**Date:** 2026-08-04. **Scope:** the medium-constraint weight/numerator knobs.

## Verdict (one line)

Five medium-constraint WEIGHT knobs have been swept and all five are FLAT: moving the
weight changes the score NUMBER (self-penalty scaling), not the TIMETABLE. The solver is
NOT tunable via medium weights. This is a useful negative result: it excludes an entire
class of tuning.

## The five sweeps

| Knob (-D) | Default | Range tested | Flat mechanism | Source / commit |
|-----------|---------|--------------|----------------|-----------------|
| `planner.m32.numerator` | 100000 | 20k / 100k / 300k | forced residual: template-deviation matches invariant 834/841/837 across 15x | `m32-sweep-results/` (`7f7e61f5`) |
| `planner.m34.weight` | 100 | 50 / 100 / 200 | self-canceling tradeoff: over-cap tuples move only at 2x (61/59/48), cadence does not improve, template resemblance worsens | `m34-weight-sweep-results/` (`a8a1df2b`) |
| `planner.m35.weight` | 50 | 25 / 50 / 100 | cleanest flat: matches 90/83/88, gap-weeks flat, resemblance flat (no tradeoff even at 2x) | `m35-weight-sweep-results/` (`9c6b1e01`) |
| `planner.tag-capacity.weight` (M-TC) | 1000 | 1000 / 2000 | satisfaction: 0 tag-over-capacity violations at both weights (non-binding on the good seed) | `m-tc-weight-sweep-results/` (`834b62dd`) |
| `planner.m37.weight` (prior) | 100 | 100 / 200 / 400 / 800 / 1500 | forced residual: ~106 room-changes at every weight, forced by room availability | CLAUDE.md M37 row; `task474-origin-map-2026-08-01.md` (TASK-446/447 era) |

All on the same good `source=input` seed (GRASP off, multi-start off, single deterministic
solve), single-variable per sweep. Per-knob verdicts and raw captures live in the
`*-sweep-results/` dirs; the M32/M34/M35/M-TC drivers are
`scripts/benchmark/m32-sweep.sh` and `scripts/benchmark/medium-knob-sweep.sh`.

## Strategic implication

**Medium weights are not the lever. The direction to improve the schedule is the
metaheuristic and the phase config, not the weights.**

The two flat mechanisms converge on the same conclusion:
- **Forced residual (M32, M34, M35, M37):** the weekly template / cadence / room-keeping
  targets are set by construction, and the residual each medium penalizes is structurally
  locked by construction plus the fill family (M22 ~-563k and M20 ~-141k dominate the
  medium and do not trade off meaningfully with these). LS has no profitable move the
  weight could incentivize, so the medium penalizes a residual the search cannot remove.
- **Satisfaction (M-TC):** the room phase already reaches 0 violations, so there is nothing
  for the weight to tune.

Either way the weight only re-scales the penalty on a fixed (or empty) residual. The
score moves; the timetable does not.

This excludes medium-weight tuning as a productive direction. Do not spend solve time on
future medium-weight sweeps expecting schedule improvement (the lesson is also saved as
memory `medium-weight-knobs-flat`).

## Where the lever actually is

The schedule-quality levers move the SEARCH, not the score weighting, so they can change
which basin the solver reaches:

- **Metaheuristic config** in `solverConfig.xml`:
  - Tabu `entityTabuSize` (currently 7) and the forager `acceptedCountLimit` (currently a
    uniform 1000 in both LS phases). These control move evaluation breadth and short-term
    memory.
  - Late Acceptance size and the acceptor choice (Tabu vs LA vs the VND hybrid).
  - VND neighborhood ordering: which move selectors run in which order.
- **Phase config:** the per-phase termination (`diminishedReturns`, `unimprovedSecondsSpentLimit`,
  `secondsSpentLimit`) and the move-selector union composition.

Two backlog tasks are exactly this direction, both To Do in the `solver-optimization`
milestone:
- **TASK-296** "Tune acceptedCountLimit per LS phase" (replace the uniform 1000).
- **TASK-284** "Benchmark VND variants - neighborhood orderings, VND+Tabu hybrid"
  (depends on TASK-280, priority medium).

These have a higher prior of moving the needle than any medium weight, because they change
where the search goes, not how a fixed residual is penalized.

## Caveat (the one open medium-weight thread)

M-TC's flatness is seed-qualified. On this good seed M-TC is non-binding (0 violations), so
its weight is irrelevant. On a SATURATED basin (the H17 bad-seed regime, the meccanica tag
with only 1 room; the golden itself has ~44 accepted tag-over-capacity violations) M-TC IS
binding and its weight could matter. That is the one medium-weight scenario not covered by
this series, and it needs a saturated-seed sweep (a different, harder setup). It does not
weaken the strategic verdict for the good-seed production path.

## Bottom line

Five medium weight knobs, five flats. Stop sweeping medium weights for schedule gains on
the good seed. Pivot the tuning effort to the metaheuristic (TASK-296, TASK-284) and phase
config, which is where the search actually changes.
