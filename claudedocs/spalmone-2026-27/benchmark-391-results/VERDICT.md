# TASK-391 Phase-3 Ruin & Recreate Count Sweep — laptop Results & Verdict

**Procedure:** `scripts/benchmark/laptop-RUNBOOK.md` (§ "Ruin & Recreate sweep") + `backlog/tasks/task-391`.
3 arms × 2 runs = 6 sequential live solves, heap 2g (TASK-389), phase-2 budget pinned 3600s.
Arms differ ONLY in phase-3 `ruinRecreateMoveSelector` min/max ruined counts; phase 2 held at control
(verified byte-identical `ssl=3600` across A/R1/R2). Within-laptop comparability.
**Run date:** 2026-07-22. **Model unchanged** — search-config only (primary directive).

## Arms

| arm | phase-3 ruin (min-max) | notes |
|-----|------------------------|-------|
| A  | 2-5  (current shipping config) | control — below Timefold's 5-20 default |
| R1 | 5-20 (Timefold default)        | the docs' recommended size for large datasets |
| R2 | 10-40 (aggressive)             | for the tight multi-lesson cluster |

## Results (both runs each)

| run | hard | medium | soft | phase-2 end | phase-3 end | phase-3 Δ | H3 | G05 stacks | full-day | rec% | blocks≥2h |
|-----|-----:|-------:|-----:|------------:|------------:|----------:|---:|--------------:|---------:|-----:|----------:|
| A-run1  |  −121 | −1,410,895 | +77,450 | −135  | −135  |    0 |    0 | 0 | 78.9% | 36.7 | 2100 |
| A-run2  | −1123 | −1,395,888 | +78,028 | −2138 | −1138 | +1000 | −1000 | 0 | 78.3% | 36.2 | 2121 |
| **R1-run1** |  **−66** | −1,460,130 | +75,136 | −196  |  −80  |  +116 |    0 | 0 | 78.1% | 36.9 | 2081 |
| **R1-run2** |  **−70** | −1,463,387 | +68,558 | −2199 |  −88  | +2111 |    0 | 0 | 76.9% | 37.2 | 2005 |
| R2-run1  | −2182 | −1,465,158 | +79,350 | −2204 | −2204 |    0 | −2000 | 0 | 78.3% | 37.6 | 2108 |
| R2-run2  | −1146 | −1,479,679 | +69,730 | −3021 | −1168 | +1853 | −1000 | 0 | 75.9% | 36.5 | 2060 |

Per-arm hard distributions: **R1 {−66, −70} mean −68 spread 4**; A {−121, −1123} mean −622
spread 1002; R2 {−2182, −1146} mean −1664 spread 1036.

## Verdict

### 1. R1 (ruin 5-20, the Timefold default) is the winner — adopt it.
R1 lands at **−66 and −70 across two independent runs (spread of 4)** — reliably low, where the
control spread is 1002 and R2's is 1036. This is exactly the "reliably-low-beats-occasionally-lucky"
principle the runbook mandates. Both R1 runs are clean (H3 = 0) and all KPIs hold (full-day 76.9-78.1%
≥ 74; blocks 2005-2081 ≥ 1800; recurrence 36.9-37.2%, structurally ~37% across every arm, not
arm-specific). **Recommendation: adopt R1 — raise phase-3 `minimumRuinedCount` 2→5 and
`maximumRuinedCount` 5→20** (= Timefold default) on Fedora, with the solver-config-sync ripple +
golden re-verification per DoD. This is a search-config change; the model is untouched.

### 2. The win mechanism is reliable phase-3 basin rescue.
Phase 2 is identical across arms, so the difference is phase 3. R1's phase-3 Δ is **+116 (run1) and
+2111 (run2)** — when phase 2 lands badly (run2: −2199), R1's ruin-5-20 *rescues* it to −88. The
control's small ruin (2-5) managed only +1000 from a similar bad start (−2138 → −1138) and was inert
(+0) in its other run. R1's bigger ruin reliably dislodges basins the shipping size can't. Timefold's
default (5-20) is the right size for this 12,581-lesson problem.

### 3. R2 (ruin 10-40) is counterproductive — the COST concern is confirmed.
R2 is reliably the **worst** arm (mean −1664). Too-aggressive a ruin makes each R&R move expensive,
so fewer moves fit in the budget and it can't escape — the exact failure mode the docs warn about.
R2 also picked up H3 violations in both runs (−2000, −1000) that R1 never did. R2 is ruled out.

### 4. The "G05 H3 knot" premise is REFUTED — it never appeared.
The task's central hypothesis — that the persistent residual is the G05 Scienze/T09 vs
Matematica/T08 stacked H3 pair — does **not hold**: **G05 stacks = 0 in all 6 runs**
(control included). The binding hard residual is **H15** (same-day same-subject non-consecutive,
−60 to −180) ± occasional H3 in *other* groups (in the worse A-run2/R2 runs). So this is not a
knot-specific fix; R1's win is general reliable convergence, not G05-knot-busting. (H3 violations
that do appear correlate with worse solves — they're a symptom, and R1 produces none.)

## Direct answer to the task's question
*"Did the R&R tuning crack the G05 knot reliably?"* — The G05 knot was never the binding
residual (0/6 runs), so there was no knot to crack in this session. **But the deeper goal — a
search-config change that drives hard reliably low — succeeded: R1 (ruin 5-20) converges to ~−68
hard in both runs vs the control's −121/−1123, with KPIs intact.** Recommend adopting R1.

## Caveats / what this does NOT settle
- Recurrence KPI is ~37% across ALL arms (control too), under the ~38% soft target — that is a
  structural property of the current model/seed, not something this phase-3 knob changes.
- n=2 per arm resolved R1 decisively (spread 4) but the control's distribution is still only
  {−121, −1123}; R1's reliability is the strong signal, not a tight estimate of its mean.
- Per the primary directive, adoption (solverConfig edit + ripple + golden re-verification) happens
  on Fedora with full DoD — this laptop run only produces the recommendation.

## Artifacts
Each `arm-*-runN/` folder holds `score.txt`, `phases.json`, `analysis.json` (per-constraint
breakdown), `lessons.txt` (raw placements, KPI source + the G05 stack measurement),
`kpi-daily-fill.txt`, `kpi-resemblance.txt`. Phase-1 endScore was byte-identical
(`−57457hard/−2053121medium/−123080soft`) across all 6 runs → deterministic construction baseline,
clean A/B.
