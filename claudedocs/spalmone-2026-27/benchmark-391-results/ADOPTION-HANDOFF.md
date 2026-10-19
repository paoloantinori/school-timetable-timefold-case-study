# TASK-391 Adoption Handoff — adopt phase-3 ruin 5-20 (self-contained brief for the adopting agent)

> **Read this first.** This is the actionable companion to `VERDICT.md` (full evidence) and the
> per-run artifacts in this folder. It is self-contained: you can act on it without the benchmark
> conversation. **Where to do the work: Fedora** (full DoD). **Branch:** `feature/cartesian-block-entity`.
> **Model is UNCHANGED** — this is a search-config edit (primary directive: performance is a
> search-configuration problem, never a reason to touch the logical model).

## TL;DR
Benchmark TASK-391 (3 phase-3 ruin-count arms × 2 runs, laptop, heap 2g) found **R1 = ruin 5-20
(Timefold's default) converges reliably low**: hard distribution {−66, −70} (spread 4), vs the
shipping control ruin 2-5 {−121, −1123} (spread 1002) and aggressive ruin 10-40 {−2182, −1146}
(spread 1036, reliably worst). **Adopt R1.** All KPIs hold (full-day 76.9–78.9%, blocks 2005–2121).
Mechanism: ruin 5-20 reliably *rescues* bad phase-2 basins (phase-3 Δ +116, +2111) that ruin 2-5
can't dislodge.

## The exact change (one file, two values)
`src/main/resources/solverConfig.xml`, phase-3 (RuinRecreate) `<ruinRecreateMoveSelector>` block
(~lines 168–179 — it is the **only** `ruinRecreateMoveSelector` in the file):

```diff
       <ruinRecreateMoveSelector>
         <fixedProbabilityWeight>5.0</fixedProbabilityWeight>
-        <minimumRuinedCount>2</minimumRuinedCount>
-        <maximumRuinedCount>5</maximumRuinedCount>
+        <minimumRuinedCount>5</minimumRuinedCount>
+        <maximumRuinedCount>20</maximumRuinedCount>
```

Also update the stale comment above it (~line 164) for accuracy: `remove 2-5 lessons' timeslots` →
`remove 5-20 lessons' timeslots`.

## solver-config-sync ripple: NONE functional (verify, then optional wording polish)
Per `.claude/rules/solver-config-sync.md`, the ripple files track **phase count / names /
termination** — none of which change here (still 5 phases, same names, same terminations). I grepped:
every code/doc reference to "RuinRecreate" (`SolverConfigParser.java`, `SolverProgressionListener.java`,
`solverPhases.ts`, `solver-phases.md`) is about the **phase name**, not the ruin counts. So:

- **No functional ripple.** Do NOT touch `SolverConfigParser`, `SolverProgressionListener`,
  `PhaseProgressionService`, `solverPhases.ts`, `SolverPhasesPage.tsx`, `UnifiedSolverControls.tsx`,
  or the i18n bundles — they don't reference the min/max values.
- **Optional accuracy polish** (recommended, keeps docs honest) — update stale "2-5" wording to "5-20":
  - `src/main/java/com/school/planner/domain/LessonDifficultyWeightFactory.java:12` — javadoc
    "RuinRecreate temporarily nullifies 2-5 entities' timeslots".
  - `claudedocs/solver-phases.md` — move table (~line 122: "Remove 2-5 lessons") and prose (~line 126).
  - `solverPhases.ts:80` `moveStrategy` text (descriptive only).
- **Pre-existing inconsistency to be aware of (NOT yours to fix here):** `solver-phases.md` frames the
  pipeline as "6-phase" and calls RuinRecreate "Phase 4", while `solverConfig.xml` +
  `SolverConfigParser` treat RuinRecreate as the 2nd `<localSearch>` ("3. LS - Advanced"). Don't
  conflate this doc drift with the ruin-count edit.

## Verification + DoD (run on Fedora, by the orchestrator session)
1. `bash scripts/validate/validate-solver-xml.sh src/main/resources/solverConfig.xml` — must validate.
2. `./scripts/core/rebuild.sh` (or rebuild-checked) — `BUILD SUCCESS`.
3. Re-run the golden regression guard — a search-param change must NOT move golden scores:
   `./scripts/core/test.sh` (includes `GoldenSolutionRegressionTest`). Expect identical golden pins.
4. DoD (per `backlog/config.yml`): `/simplify` on the diff → `/code-review high` (Opus-verified) →
   final `test.sh` (+ `ui-typecheck.sh`/`ui-build.sh` if frontend touched — it isn't here) with EXIT
   CODE, run by the orchestrator. Check the DoD items on the TASK-391 backlog task.
5. Commit message convention: `perf(TASK-391): raise phase-3 R&R ruin 2-5 → 5-20 (Timefold default) —
   laptop A/B {-66,-70} vs control {-121,-1123}`.

## Why this is safe to adopt (the evidence, condensed)
- **Comparability was clean:** all 3 arms shared an identical phase-2 (`ssl=3600`, verified) and
  differed ONLY in phase-3 min/max ruin. Phase-1 endScore byte-identical (`−57457/−2053121/−123080`)
  across all 6 runs → deterministic construction baseline.
- **R1 is reliably low, not lucky:** {−66, −70}, spread 4. Control spread 1002, R2 spread 1036. This
  is the runbook's "prefer reliably-low over occasionally-lucky" criterion, satisfied decisively.
- **Mechanism = reliable phase-3 rescue:** R1 phase-3 Δ = +116 (run1), +2111 (run2 — rescued a bad
  −2199 phase-2 to −88). Control's small ruin managed only +0/+1000 from similar starts. R2's
  aggressive ruin is counterproductive (fewer, costlier moves → stuck; the COST concern confirmed).
- **KPIs intact:** R1 full-day 76.9–78.1% (≥74), blocks 2005–2081 (≥1800). Recurrence ~37% across
  ALL arms incl. control — structural to the current model/seed, NOT moved by this knob.

## Direct answer to the task's framing question
*"Did R&R crack the G05 knot (Scienze/T09 vs Matematica/T08 stacked H3) reliably?"*
**The knot was never the binding residual:** G05 stacked cells = **0 in all 6 runs, control
included.** The persistent hard residual is **H15** (same-day-same-subject non-consecutive), not H3.
So R1's win is *general reliable convergence*, not G05-knot-busting. (H3 violations that do appear
— in worse A-run2/R2 runs — are in *other* groups and correlate with bad solves; R1 produces none.)
Report this premise correction to whoever set the task.

## Full results (both runs each)
| run | hard | medium | soft | phase-2 end | phase-3 end | phase-3 Δ | H3 | G05 | full-day | rec% | blocks |
|-----|-----:|-------:|-----:|------------:|------------:|----------:|---:|-------:|---------:|-----:|-------:|
| A-run1 (control 2-5)   |  −121 | −1,410,895 | +77,450 |  −135 |  −135 |    0 |    0 | 0 | 78.9% | 36.7 | 2100 |
| A-run2 (control 2-5)   | −1123 | −1,395,888 | +78,028 | −2138 | −1138 | +1000 | −1000 | 0 | 78.3% | 36.2 | 2121 |
| **R1-run1 (5-20)**     |  **−66** | −1,460,130 | +75,136 |  −196 |   −80 |  +116 |    0 | 0 | 78.1% | 36.9 | 2081 |
| **R1-run2 (5-20)**     |  **−70** | −1,463,387 | +68,558 | −2199 |   −88 | +2111 |    0 | 0 | 76.9% | 37.2 | 2005 |
| R2-run1 (10-40)        | −2182 | −1,465,158 | +79,350 | −2204 | −2204 |    0 | −2000 | 0 | 78.3% | 37.6 | 2108 |
| R2-run2 (10-40)        | −1146 | −1,479,679 | +69,730 | −3021 | −1168 | +1853 | −1000 | 0 | 75.9% | 36.5 | 2060 |

Per-arm hard: **R1 {−66,−70} mean −68**; A {−121,−1123} mean −622; R2 {−2182,−1146} mean −1664.
