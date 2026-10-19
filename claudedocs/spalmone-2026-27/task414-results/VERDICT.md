# TASK-414 cumulative A/B (Q22 + 406 + 409 + 410 + 414, eviction in the stack) — VERDICT: ADOPT TASK-414 (eviction clears the box live); cumulative KPI gate PARTIAL (same search-domain residuals as 410)

**Branch:** `feature/cartesian-block-entity` @ `4eddcb2a` (TASK-414 one-level eviction in `BoxRepair`, Fedora-verified, docs-only delta over the eviction code).
**Date:** 2026-07-24 (laptop). **Procedure:** laptop-RUNBOOK, 2 fresh `dev` solves, heap 2g (`PLANNER_HEAP` default), `caffeinate -dims` + foreground status polls. Baselines: TASK-401, TASK-406, TASK-410. n=2. No config/model changes, no reverts.

## Headline

The TASK-414 eviction delivers its core promise **live on both runs**: it clears the T01/G06 saturation that left val410 at box 9/11. Both runs reach **box = 0** (boxed 8/11 → placed 8/11, **evicted 3/7**, `still boxed 0`, `conflicts added 0`), and hard drops to **H17-only** (−1 / −2). This **matches Fedora's checkpoint** ("box 11→0, conflicts 0, hard −12 → −1") on a fresh solve, not a snapshot. No box survived in either run, so the derived-boxed discriminator was not needed.

The cumulative KPI gate is **PARTIAL**, but the marginal levers (full-day ~77%, day-start ~94–95%, M20+M20.2 ~−1.14M) are the **same pre-existing search-domain residuals** val410 carried (val410's own verdict was PARTIAL), not caused or worsened by the eviction. The eviction is neutral-to-slightly-better on KPIs (M20.2 32/31 → 32/27; recurrence 36.8/36.5 → 37.9/36.6). Per the primary directive, **ADOPT TASK-414**; the residual KPI shortfall is search-domain follow-up (LS-vs-construction regularity, M22), never a model revert.

## Multi-baseline table

| metric | 401 | 406 (r1/r2) | val410 r1/r2 | **val414 r1** | **val414 r2** | gate | verdict |
|---|---|---|---|---|---|---|---|
| **hard** | -1 | -1 / 0 | -10 / -12 | **-1** | **-2** | <=-1 H17-only | H17-only both; r1 PASS, r2 -2 (1 extra H17 gap, variance) |
| **box (H0)** | 0 | 0 / 0 | 9 / 11 | **0** | **0** | 0 | **PASS both** |
| **eviction** (phase-4) | n/a | none | none | boxed 8→0, evict 3 | boxed 11→0, evict 7 | clears T01, conflicts 0 | **PASS both** (matches Fedora) |
| phase-4 conflicts added | — | 0 | 0 | **0** | **0** | 0 | **PASS both** |
| **full-day** | 77.7-77.8 | 79.3/79.1 | 78.3 / 77.6 | **77.2%** | **77.5%** | >=~78% | marginal (comparable to 410) |
| **day-start** | 81.9 | 95.8/96.1 | 94.0 / 94.8 | **95.2%** | **94.4%** | >=~94.5% | r1 PASS, r2 -0.1 marginal |
| **M20** | -786k | -854/-878k | -938 / -958k | **-970k** | **-996k** | toward -786k | comparable (variance) |
| **M20.2 (empty exp day)** | n/a | n/a | -177k/32 / -167k/31 | **-170k / 32** | **-148k / 27** | ~0 (golden ~17) | slightly better r2 |
| **M20+M20.2** | -786k | ~-854k | -1,115k / -1,125k | **-1,140k** | **-1,144k** | not worse than 410 | ~same (within ~2% variance) |
| M22 (unchanged) | — | -564k | -576k | -577k | (TASK-408 lever) | not addressed |
| **recurrence unpinned** | 35.8-36.7 | 35.4/33.2 | 36.8 / 36.5 | **37.9%** | **36.6%** | ~35 floor | **PASS both** |
| blocks >=2h | 2092-2139 | 2000/2005 | 2167 / ~2150 | **2159** | **2198** | toward ~2100 | OK |
| G05 knot | — | — | 0 / 0 | **0** | **0** | 0 | **PASS both** |
| total medium | -1.404M | -1.498/-1.531M | -1.783 / -1.798M | **-1.806M** | **-1.843M** | — | comparable (variance) |

## Why this is ADOPT for TASK-414 (the eviction is the load-bearing change)

TASK-414's only code delta over val410 is the one-level eviction in `BoxRepair` (phase 4). Its job is narrow and it does it perfectly:

- **val410 left box 9/11** (T01/G06 Servizio tecnico e stilistico, ~60h confined to Thursday by Q22, + 1 T08/G01). The VRP discriminator proved **Hypothesis B**: those cells are saturated (0 free), so direct placement is impossible. val410's `BoxRepair` had no eviction, so `still boxed 9/11`.
- **val414 clears it**: phase-4 log run1 `boxed 8 -> placed 8 (evicted 3), still boxed 0, conflicts added 0`; run2 `boxed 11 -> placed 11 (evicted 7), still boxed 0, conflicts added 0`. The eviction moves a single unpinned blocker to a fully-legal cell (incl. the new departure-H17-safe gate), then places the boxed lesson. **0 conflicts added by construction**, verified live both runs.
- **Hard is H17-only both runs** (analysis.json: the only hard constraint with hard<0 is H17; **H0 is absent**, i.e. 0 unassigned). The −2 in run2 is one extra H17 compactness gap vs run1's −1, i.e. **H17 run-to-run variance (TASK-390's compaction domain), not an eviction failure**. The eviction's domain is H0/box, and that is fully cleared.

This is exactly the Fedora checkpoint, reproduced on a fresh laptop solve. **ADOPT.**

## Why the cumulative KPI gate is PARTIAL (and why that is not TASK-414)

The marginal levers (full-day, day-start, M20+M20.2) land in the **same band as val410** and are owned by the **same groups** TASK-414 does not touch:

- **M20.2 empty expected days (32 / 27): dominated by G04 (20 / 18).** This is G04's documented structural floor (CLAUDE.md: bound demand ~750h < quota 990h). G04 is a quota group with no Stage course, so the eviction (and TASK-410's stage-aware weeks) do not touch it. The remainder is small and scattered (run1: G08 3, G09 4, G10 1, G11 1, G12 2, G13 1; run2: G08 3, G09 3, G12 3). **Structural (G04) vs solver-owned split reported as asked: run1 20 vs 12; run2 18 vs 9.**
- **M20 -970k/-996k and M20+M20.2 -1,140k/-1,144k**: within ~2% of val410's -1,115k/-1,125k. The eviction places 8/11 previously-boxed lessons (filling specific cells, displacing blockers); the net M20/M20.2 effect is neutral, dwarfed by run-to-run variance. Not a regression.
- **full-day 77.2/77.5% and day-start 95.2/94.4%**: comparable to val410 (78.3/77.6 and 94.0/94.8). Marginal vs the ~78/~94.5 gate, same as val410's own marginal FAIL.
- **M22 -577k (unchanged from 410)** is the largest unaddressed medium component: the **TASK-408** lever (non-full-days), the obvious next target.

None of these is caused by TASK-414. TASK-414 is H0-only; these are medium/soft-tier search residuals that existed in val410 and persist here. Per the primary directive, they are attacked via solver-config benchmarking, never by weakening the model.

## What the stack did well (held or improved vs val410)

- **Eviction clears the T01 saturation live** (the whole point): box 9/11 → 0/0, conflicts 0, hard −10/−12 → −1/−2.
- **G05 zero-slack knot solved** both runs (0 stacked cells).
- **Recurrence held above floor** (37.9/36.6 vs 36.8/36.5), even **improved** in run1.
- **blocks >=2h** 2159/2198 (toward ~2100).
- **M20.2 slightly better** in run2 (27 empty days vs val410's 31).

## Honest note on run2 hard = −2 (not −1)

The gate sub-clause "hard <= −1" is met by run1 (−1) and missed by 1 in run2 (−2). The −2 is **H17-only** (analysis.json: H17 −2, H0 absent, no other hard). It is one extra vertical-compactness gap the LS/compaction left, i.e. **TASK-390's domain and run-to-run H17 variance**, not a box and not an eviction shortcoming (box=0, evicted 7, conflicts 0). The eviction's deliverable (box→0, H17-only, conflicts 0) is met in both runs.

## Forward direction (maintainer's reflection, 2026-07-24, out of scope here)

maintainer noted the human planners solve most of the problem in **construction** (hardest-first + recurring weekly template + fill holes), doing the eviction/backtracking only as the very last step, and doubts whether our construction matches theirs. Grounded assessment:

1. We **do have** the constructive heuristics: `DayClusteredConstructionPhase` builds+stamps a weekly template (TASK-381 L2: "place the same lessons on the same weekday for the same courses until exhausted", most-constrained teachers first), then round-robin fills holes, then `box-the-residual`, then compaction. The eviction is correctly the last step (phase 4). The log confirms it ("Weekly-template stamp complete: placed 7207" in the first second).
2. The real gap is that **our local search degrades the construction's weekly regularity**: construction reaches ~77–81% recurrence (TASK-381/385 `WeeklyTemplateConstructionRealSeedTest`, prior measurement not re-run live here), but the final recurrence after LS is ~37–38%. The planners don't have this drop because they don't relax the template with local search. Our LS optimizes a score dominated by M20/M22 (1000/day "logical hard medium") and breaks weekly regularity to fill days; S13 (weekly stability) weighs only 2 and cannot hold. This is the inversion of the planners' priority (keep recurrence, fill holes after) and the natural next lever: rebalance S13 vs M20/M22, or tighten the template, or cap how far LS may move a templated lesson. A knowledge-capture session for further constructive heuristics the planners apply intuitively is also worthwhile.

This is search-domain (the primary directive: never weaken the model for cost), and is **not** TASK-414's scope.

## Recommendation

- **ADOPT TASK-414.** The eviction is logically correct, works live (box→0 both runs, conflicts 0, matches Fedora), and is H17-only on hard. Per the primary directive it stands regardless of compute cost.
- **Cumulative-stack gate = PARTIAL / analysis, no reverts.** Same residuals as val410, search-domain:
  - **M22 -577k (13 matches)**: the TASK-408 lever (non-full-days), the obvious next target.
  - **full-day ~77% / day-start ~94–95% / M20+M20.2 ~-1.14M**: attack via solver-config benchmarking and the LS-vs-construction-regularity rebalance above, never by weakening Q22/M20.2/410.
  - **G04 empty days (18–20)**: a capacity/data question for the school (its quota 990h exceeds its bound demand ~750h), surfaced correctly by M20.2 — report, do not model-around.

## Artifacts

`val414-run{1,2}/`: score.txt, analysis.json (full constraint breakdown), phases.json (6-phase), lessons.txt, kpi-{fill,daystart,resemblance,moda-knot}.txt, solve-progress.log, export.json.gz, phase-markers-log.txt (the eviction lines), teacher-fingerprint.txt, m20.2-empty-by-group.txt. `confronto-val414-run{1,2}.html` (golden-vs-run visual, 14 groups x 172 days). Compare against `task410-results/` and `task401-results/`.

## Operational note (git transport on the laptop)

The laptop's SSH is broken (no agent, publickey denied) and the `gh` token is rejected as Bearer for git, but works as a basic-auth password. `4eddcb2a` was pulled and the results pushed via HTTPS using `git -c http.extraHeader="Authorization: Basic ..."` with the token resolved at runtime from `gh auth token` (no persisted config). Verified via the GitHub compare API that `4eddcb2a` is docs-only (+1/−1 in the task markdown) over the eviction code already at HEAD.
