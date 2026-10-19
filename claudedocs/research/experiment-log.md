# Experiment Log

Standardized record of solver experiments. Append one row per run (roadmap §5.3, TASK-319 protocol).
Full scorecards live in `local/experiments/` (gitignored); this log is the version-controlled summary.

| Date | Task | Config delta | Hard | Medium | Soft | Unassigned | Avg lessons/active-day (overall) | Wall-clock | Verdict |
|------|------|--------------|-----:|-------:|-----:|-----------:|--------------------------------:|-----------:|---------|
| 2026-07-14 | TASK-320 rep 1 | current pipeline (tabu 7, R&R 2-5, diminishedReturns 2min/0.1, 6-phase) | -115 | -492,331 | +174,794 | 0 | 5.89 | ~81 min | BASELINE |
| 2026-07-14 | TASK-320 rep 2 | current pipeline (tabu 7, R&R 2-5, diminishedReturns 2min/0.1, 6-phase) | -111 | -498,625 | +168,350 | 0 | ~5.9 | ~80 min | BASELINE rep 2 — confirms rep 1 stable (hard within 3.5%) |
| 2026-07-14 | TASK-323 rep 1 | Timefold 2.3.0 (from 2.1.0); same 6-phase pipeline | -113 | -510,224 | +171,370 | 0 | ~5.9 | ~85 min | KEEP — hard equal (-113 vs ~-113); medium -18k worse (noise-adjacent, confirm reps 2-3); TimSort workaround did NOT trigger on 2.3.0 |
| 2026-07-14 | TASK-324 var 20-80 | R&R ruinedCount 20-80 (control 2-5); Timefold 2.3.0, 6-phase | -115 | -493,426 | +167,392 | 0 | ~5.9 | ~90 min | NEGATIVE on hard (primary): -115 vs -113 control, within noise; medium +17k better. Hard debt (M22+H4) is structural, not a ruined-count lever |
| 2026-07-14 | TASK-330 var M22-med-1000 | M22 calendar-compactness HARD->MEDIUM w1000; TF 2.3.0, ruinedCount 2-5, 6-phase | -20* | -622,129 | +170,790 | 0 | (M22 gaps 92) | ~90 min | MARGINAL — *hard-excl-M22 -20 vs control -25; eliminates H4 (-10->0) but H4.3 untouched (-20); medium +112k worse. Revert M22 to hard |
| 2026-07-15 | TASK-330 H4.3 generalized | H4.3 via ProfessionalizzantiDay (G07->Mon + G11->Tue, hard tier); TF 2.3.0, ruinedCount 2-5, 6-phase | -154 | -488,820 | +170,582 | 0 | ~5.9 | ~90 min | WORSE on hard — option 2 correct modeling but H4.3 -20->-60 (G11 Tue added); hard -154 vs -113. Lever is the tier: relax H4.3 to medium (option 1) |
| 2026-07-15 | TASK-325 LA-400 | Late Acceptance size 400 on Phase 3+4 (was tabu 7); TF 2.3.0, generalized H4.3 hard, ruinedCount 2-5, 6-phase | -158 | -549,933 | +172,566 | 0 | ~2h | NEGATIVE — -158 vs control -154 (within noise); H4.3 unchanged (-60 structural); M22 worse (-93 vs -84); medium -61k worse. Hard debt confirmed data-driven, not a search trap |

## 2026-07-14 — Baseline (TASK-320 rep 1)

First valid baseline under the current architecture (Timefold 2.1.0, 6-phase pipeline, LabBlock→pinned-Lesson denormalization). Scorecard: `local/experiments/baseline-20260714-094049.json`. Git: `b90f0a7`.

**Headline:** `-115hard / -492,331medium / +174,794soft`, **0 unassigned**, overall mean 5.89 lessons/active-day.

**The hard -115 is NOT the conflict class the success criterion names (H1–H3, H5–H7, H11).** It is almost entirely:
- **M22 Calendar compactness: -85** (6 matches)
- **H4.3 Professionalizzanti constant single-day during stage: -20** (20 matches)
- **H4 Stage blackout: -10** (10 matches)

So the path to 0 hard runs through **calendar compactness (M22) and stage constraints (H4/H4.3)**, not teacher/room/group resource conflicts. This contradicts the roadmap's §1 working assumption that hard would be "dominated by H15/H17-class violations" — a key prioritization signal: TASK-330 (H17 re-benchmark) may matter less than a dedicated calendar-compactness investigation, and the score-trap hypothesis (§3) is about a different part of the landscape than where the actual hard debt sits.

**Medium -492,331** is dominated by sparse-day / distribution constraints (M20 daily-quota-fill at 185 matches, plus subject-distribution soft debt).

**vs success criterion** (0 hard-conflict violations; ≥90% spalmone-target lessons/day every group; ≤2h; 3/3 reproducible): **fails** on hard (-115 ≠ 0) and reproducibility (1 of 3 reps). Passes unassigned (0) and roughly wall-clock (~81 min < 2h). Lowest-density groups to watch vs target: G11 4.36, G07 4.73, G12 4.80, G13 4.81.

**Caveat:** the `/api/phase-progression` endpoint returns phase names but does not populate `durationMs`/`score`, so per-phase timings were not captured; wall-clock is estimated from run timestamps (solve started ~08:01, NOT_SOLVING by ~09:22). Fixing phase-progression population is a separate improvement.

**Next:** TASK-320 reps 2–3 (for variance), then TASK-321 feasibility audit. The M22/H4 finding should feed into how m-3/m-4 experiments are prioritized.

## 2026-07-14 — Timefold 2.3.0 upgrade (TASK-323 rep 1)

Upgraded `timefold-solver` 2.1.0 → 2.3.0 (pom BOM bump). Build clean, 273 tests pass, solver XML validates against the 2.3 XSD. Scorecard: `local/experiments/timefold-230-20260714-183726.json`.

**vs 2.1.0 baseline (~-113 hard):** 2.3.0 = `-113hard / -510,224medium / +171,370soft`, 0 unassigned. **Hard is equal** (-113, within noise of rep1 -115 / rep2 -111). Medium is ~18k worse (-510k vs -492k rep1 / -499k rep2) — larger than the rep1↔rep2 variance (~6k), so possibly a small real medium regression with the current tabu config, but not a new violation and not a major bug. Single run; reps 2-3 should confirm the medium delta.

**Verdict: KEEP** (per user guidance — no major bug; hard equal; 2.3.0 brings the upstream benefits the m-3/m-4 experiments depend on: metaheuristic improvement, #2407 Late-Acceptance fix, #2448 R&R-unassigned fix, #2442/#2430 perf).

**Criterion #6 (TimSort):** the `LessonEntitySorter` "Comparison method violates its general contract" crash did NOT trigger on 2.3.0 (absent from logs; solve completed cleanly). → **TASK-311 (remove the workaround) is now viable** to evaluate.

**Next:** m-3 quick wins on 2.3.0 — TASK-324 (R&R ruined-count sweep), TASK-325 (acceptor sweep; Late Acceptance now a first-class candidate per #2407).

## 2026-07-14 — TASK-324 variant: R&R ruinedCount 20-80 (vs 2-5 control)

Hypothesis: Phase 4's ruinedCount 2-5 is too timid to escape compaction traps; raising to 20-80 lets the solver restructure days/weeks and cross hard-score humps. Scorecard: `local/experiments/rr-20-80-20260714-202625.json`.

**Result: NEGATIVE on hard (primary).** `-115hard` vs control `-113hard` — within rep noise (±2-4), i.e. **no hard improvement.** Crucially, the hard debt is **identical to the baseline**: M22 Calendar compactness -85 (6 matches), H4.3 Professionalizzanti single-day-during-stage -20 (20), H4 Stage blackout -10 (10). These violations are **structural** — they do not respond to ruined-count. The hypothesis (larger ruins escape hard traps) is **refuted for hard**.

Medium did improve ~17k (-493k vs control -510k, beyond the ~6k rep variance) — larger ruins help lesson distribution (fewer sparse days) — but medium is secondary and the roadmap's priority is hard.

**Implication:** ruined-count is not a hard-score lever; the 5-20 variant is unlikely to help hard if 20-80 didn't (per roadmap §5.5, don't stack on a refuted idea). The real hard levers are different mechanisms — TASK-325 (acceptor sweep, esp. Late Acceptance now that #2407 fixed its score-tier reset) and TASK-330 (re-benchmark the M22/H17 compaction-as-hard-vs-medium question, since M22 is the top hard contributor). `solverConfig.xml` left at 20-80 pending a revert-to-control decision before the next experiment (keep single-variable).

## 2026-07-14 — TASK-330 (refocused M22): calendar-compactness HARD → MEDIUM w1000

Demoted M22 from HARD to MEDIUM (weight 1000/excess-day) to test whether M22-hard was blocking the stage hard debt. Scorecard: `local/experiments/m22-medium-1000-20260714-223216.json`. M22 changed tier, so headline hard is not comparable — the metric is **hard-excluding-M22**.

**Result: MARGINAL — not worth it, but it pinpoints the real target.** Hard-excl-M22: variant **-20** vs control **-25**. The entire (small) gain is **H4 Stage blackout eliminated: -10 → 0** (so H4 is M22-coupled — calendar freedom lets the solver avoid stage-blackout). But **H4.3 Professionalizzanti-single-day-during-stage is untouched: -20 (20 matches)** — structural, independent of M22 tier. No new resource conflicts (conflict-hard 0). Cost: medium regressed ~112k (-622k vs -510k; M22 now -92k medium for 92 gap-days). A 5-point hard gain for a 112k-medium loss is **not worth it** → revert M22 to HARD.

**Key strategic finding:** the dominant remaining hard debt is **H4.3 (professionalizzanti single-day during stage), -20, structural.** Three m-3/m-4 hypotheses are now resolved against: R&R ruined-count (TASK-324: refuted), compaction-tier (TASK-330: M22-relaxation doesn't touch H4.3), and by extension acceptor/search-trap (the debt is structural, not a trap). The clearest remaining lever is the **professionalizzanti constraint itself / its pre-assignment rotation (TASK-279)** — 20 matches of "must be constant single-day during stage" that can't be satisfied, likely a data/rotation issue. `solverConfig.xml` is at the 2-5 control; the M22-medium Java change is left uncommitted pending revert.

## 2026-07-15 — TASK-330 H4.3 generalized (via ProfessionalizzantiDay, hard tier)

Generalized H4.3 to read the expected professionalizzanti day-of-week from the `ProfessionalizzantiDay` entity per group (resolves the long-standing hardcoded "G07 only, Monday" TODO — correct modeling). Seed has G07→Monday and G11→Tuesday. Scorecard: `local/experiments/h43-generalized-20260715-090946.json`.

**Result: WORSE on hard.** `-154hard` vs control `-113hard`. H4.3 went from `-20 / 20 matches` (G07 only) to **`-60 / 60 matches`** (G07 Monday + G11 Tuesday newly enforced). The generalization is the correct *modeling* (data-driven, no hardcoded special-case, TODO resolved) but keeping the **hard tier does not reduce the debt — it increases it**, because more groups are now enforced. (Medium improved ~21k as a side effect; unassigned still 0.)

**Verdict: option 2 is correct modeling but does not reduce the hard debt. The lever is the tier** — relax H4.3 to medium (option 1), which the user pre-authorized ("option 1 as validation if you have to"). That moves the -60 (and the original G07 -20) out of hard into medium.

**Strategic note:** even after relaxing H4.3 to medium, **M22 calendar compactness (-84) remains the dominant hard** (plus H4 stage blackout -10). The hard debt is a chain of structural constraints (M22 + H4.3 + H4) the data cannot satisfy — relaxing H4.3 alone gets hard only to ~-94 (M22+H4), not 0. Reaching low hard likely needs systematic tier-relaxation of these structural constraints, or data fixes (the feasibility theme, TASK-337). Left at generalized-hard pending the option-1 decision.

## 2026-07-15 — TASK-325: Late Acceptance 400 (Phase 3+4, was tabu 7)

Switched the Phase 3 (Compactness) + Phase 4 (RuinRecreate) acceptor from entityTabuSize=7 to lateAcceptanceSize=400, leveraging 2.3.0 PR #2407 (LA late-list reset on hard-tier change). This was the search-vs-data diagnostic. Scorecard: `local/experiments/late-acceptance-400-20260715-174534.json`.

**Result: NEGATIVE.** -158hard vs control -154 (within noise, marginally worse). H4.3 **completely unchanged at -60/60** — LA cannot place professionalizzanti on the designated day (structural/data). M22 actually **worsened** (-93 vs -84): LA's broader acceptance spread lessons into more empty calendar days. H4 improved slightly (-5 vs -10). Medium -61k worse (LA's broad exploration degrades distribution). Solve took ~2h (LA converges much slower than tabu on this dataset).

**Conclusion: the hard debt is data-driven, not a search trap.** Both search levers are now exhausted: R&R ruined-count (TASK-324: refuted), Late Acceptance (TASK-325: negative). The acceptor/ruin choice doesn't move the structural constraints (M22 + H4.3 + H4) — they're unsatisfiable given the current data, as the feasibility audit (TASK-321) showed. Revert to tabu-7 (control). The path to lower hard runs through correct data (TASK-337), not solver tuning.
