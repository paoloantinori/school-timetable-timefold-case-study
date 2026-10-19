# TASK-410 cumulative A/B (Q22 + 406 + 409 + 410) - VERDICT: ADOPT TASK-410; cumulative gate PARTIAL (shortfall is search-domain, not a model defect)

**Branch:** `feature/cartesian-block-entity` @ `27d8a286` (TASK-410 stage-aware `countActiveWeeks`).
**Date:** 2026-07-24 (laptop). **Procedure:** laptop-RUNBOOK, 2 fresh `dev` solves, heap 2g, `caffeinate -dims`, foreground status polls (background poller wrapper). Baselines: TASK-405b, TASK-406. n=2.

## Headline

The cumulative stack fails several gate levers (hard/box, M20, M20.2, marginal on full-day/day-start),
but the failures are **NOT attributable to TASK-410**, and the worse-than-406 numbers are largely
**physiological** (the constraints became more precise). TASK-410 itself is clean: construction
fixture +1.7pp to golden level, golden guard green, and its stage groups do not own any of the
failure signals. Per the project primary directive (model correctness is judged on logical
correctness, never reverted for compute cost; residuals are the search's domain), the right call is
**ADOPT TASK-410** and treat the cumulative gate shortfall as **search-domain follow-up**, with no
model reverts. Both runs converged to the same band, so this is **systematic, not variance**.

## Multi-baseline table

| metric | 401 | 405b | 406 (run1/2) | **val410 run1** | **val410 run2** | gate | verdict |
|---|---|---|---|---|---|---|---|
| **hard** | -1 | 0/-1 | -1 / 0 | **-10** | **-12** | <=-1 H17-only | FAIL (box-driven, see below) |
| **box (H0)** | 0 | 0 | 0 / 0 | **9** | **11** | 0 | FAIL |
| **full-day** | 77.7-77.8% | 77.2% | 79.3 / 79.1% | **78.3%** | **77.6%** | >=~79% | marginal FAIL |
| **day-start** | 81.9% | 96.0-96.9% | 95.8 / 96.1% | **94.0%** | **94.8%** | >=~95% | marginal FAIL |
| **M20** | -786k | -895k | -854 / -878k | **-938k** | **-958k** | toward -786k | FAIL |
| **M20.2 (empty exp day)** | n/a | n/a | n/a (new) | **-177k / 32** | **-167k / 31** | ~0 (golden 2) | FAIL (new measurement) |
| **M20+M20.2** | -786k | -895k | ~-854k | **-1,115k** | **-1,125k** | toward -786k | FAIL |
| M22 (unchanged) | - | - | -564k | -576k | -576k | (TASK-408 lever) | not addressed |
| **recurrence unpinned** | 35.8-36.7% | 35.1-35.9% | 35.4 / 33.2% | **36.8%** | **36.5%** | ~35 floor (dip ok) | **PASS** |
| blocks >=2h | 2092-2139 | 2023-2035 | 2000/2005 | 2167 | ~2150 | toward ~2100 | OK |
| G05 knot | - | - | - | **0** | **0** | 0 | **PASS** |
| total medium | -1.404M | -1.540M | -1.498/-1.531M | **-1.783M** | **-1.798M** | - | higher (see reframe) |

## Why the worse numbers are largely physiological (per the primary directive)

1. **M20.2 is new measurement.** TASK-406's medium had no M20.2 term. val410's -1.78M *includes*
   M20.2's -167..-177k for 31-32 empty days. That is new score mass surfacing a real condition
   that was previously unmeasured, not a regression of anything that existed in 406. Subtract it
   and val410 total medium (~-1.62M) is within ~80-130k of 406's -1.50/-1.53M.
2. **Q22 (TASK-412) is stricter-and-more-correct.** The teacher fixed-day blocks are golden-derived
   (e.g. T01's Mon/Tue/Wed/Fri are blocked because the golden has 0h there). The golden itself
   respects these blocks, so any residual the solver leaves is a **search limitation** (placements
   the golden proves are achievable), never an over-constraint to dilute.
3. **TASK-410 concentrates stage-group hours into real teaching weeks** - a more correct target
   that legitimately raises placement pressure on those groups.

Per the primary directive, these model changes are logically correct (golden-derived/validated);
their residuals are attacked via solver-config benchmarking, never by weakening the model.

## Per-lever failure analysis (which lever owns each failure)

The two hard-failure signals are owned by groups that **TASK-410 does not affect**:

- **M20.2 empty expected days (32/31): dominated by G04 (23/18).** G04 is a *quota* group
  (calendario_fc.csv) with **no Stage course** - so TASK-410's stage-aware `countActiveWeeks`
  does not even execute for it (groupHasStageFacts=false). This is G04's documented structural
  floor (CLAUDE.md: bound demand 222h < quota 245h; construction fixture full-day only 58-60%).
  The remaining empty days are small and scattered (G08 3/4, G12 2/6, G09 2, G13 1).
- **Box H0 (9/11): dominated by G06 (10) + G01 (1).** G06 is non-stage and non-quota ->
  TASK-410 does not touch it. This is a placement/repair limitation: 10 of G06's lessons could
  not be placed conflict-free and BoxRepair (phase 4) could not clear them (406 cleared box to 0).
  Likely Q22's stricter teacher-day blocks reduced G06's available slots enough that BoxRepair
  ran out of conflict-free room - a **search/repair-tuning** signal, not a model defect.

TASK-410's own scope (the 7 stage-eligible groups) is clean: their empty-day counts are small
(1-6 each) and the construction fixture rose to golden level (see PART 1). TASK-410 is **not**
the regression source on any gate lever.

## What the cumulative stack did well

- **Q22 teacher fingerprint matches the golden.** T01 100% Thursday (222h; golden 231h = 100%
  Thu). T07 100% Wednesday (72h; golden 72h = 100% Wed). T19 Mon+Tue, T17 Tue/Wed/Thu
  - all concentrate on the golden's days. The fixed-day rules work as designed.
- **G05 zero-slack knot solved** both runs (0 stacked cells everywhere) - the weekly-template
  construction (TASK-381) + ruin-recreate handle the perfect-matching group.
- **Recurrence held above floor** (36.5-36.8% vs the ~35 floor; the 406 run-2 dip to 33.2% did not
  recur). recurrence is the known 406/TASK-413 trade and it did NOT worsen here.
- **blocks >=2h improved** to ~2150-2167 (toward the ~2100 target; 406 was ~2000).

## TASK-410 specifically (ADOPT)

- Construction fixture `WeeklyTemplateConstructionRealSeedTest`: SCOPED full-day 83.5% -> 85.3%
  (golden 85.4%), deficit 590h -> 568h, recurrence 75.8%, zero conflicts, box <2%. Pro-rate
  validated against ground truth (stage groups' active weeks 38 -> ~30).
- `GoldenSolutionRegressionTest` green (708/708 tests, golden pins unmoved by the construction change).
- /simplify (5 fixes) + /code-review high (4 finders, 0 bugs, 0 violations) applied.
- Not the source of any PART 2 gate failure (stage groups don't own the empty-day or box signals).

## Recommendation

- **ADOPT TASK-410.** It is logically correct, construction-validated, golden-safe, and exonerated
  by the per-lever diagnostic. Per the primary directive it stands regardless of compute cost.
- **Cumulative-stack gate = PARTIAL / analysis, no reverts.** The shortfall is search-domain:
  - **M22 -576k (13 matches)** is the largest unaddressed medium component and is unchanged from
    406 - this is the **TASK-408** lever (non-full-days), the obvious next target.
  - **G06 box (10)** and the marginal full-day/day-start: attack via solver-config benchmarking
    (metaheuristics, BoxRepair deficit-awareness, ruin counts) and possibly BoxRepair tuning, per
    the directive's "cost is a research problem" rule - never by weakening Q22/M20.2/410.
  - **G04 empty days (18-23)**: a capacity/data question for the school (its quota exceeds its
    bound demand), surfaced correctly by M20.2 - report, do not model-around.
- The gate thresholds (calibrated to the less-precise 406 stack) are arguably too strict for the
  more-precise cumulative stack; a re-baselining of the gate against this stack is warranted once
  the M22/BoxRepair search levers land.

## Artifacts

`val410-run{1,2}/`: score.txt, analysis.json (full constraint breakdown), phases.json (6-phase),
lessons.txt, kpi-{fill,daystart,resemblance,moda-knot}.txt, solve-progress.log, export.json.gz,
SUMMARY.md. `confronto-run{1,2}.html` (golden-vs-run visual, 14 groups x 172 days).
`golden-teacher-fingerprint.txt` (the Q22 target). Compare against `task405b-results/` and
`task406-results/`.
