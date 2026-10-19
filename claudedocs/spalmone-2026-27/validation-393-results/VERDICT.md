# TASK-392 + TASK-393 HEAD validation — laptop results & verdict

**Branch:** `feature/cartesian-block-entity` @ `62f10f8e`. **Run date:** 2026-07-22/23 (laptop).
**Procedure:** `scripts/benchmark/laptop-RUNBOOK.md` (per-arm, **without** generate-benchmark-arm —
this is a HEAD solve, no config variant). 2 independent live solves, heap 2g (TASK-389),
each from a fresh `dev` profile (delete → reseed → `verify-baseline.sh seed` → import-calendario
→ `verify-baseline.sh import` → pre-assign → solve). One solve at a time; caffeinate-guarded.
**laptop measures only — no adoption/config change on the laptop** (per primary directive).

## What HEAD carries (the 4 levers being validated)
1. **R1 ruin 5-20** adopted into phase 3 (TASK-391).
2. **H15 → M15 medium**, `ofMedium(20)` × gap-min (TASK-392) — must NOT scatter.
3. **Construction H17 compaction** in the phase-1 tail (TASK-390).
4. **NEW final compaction phase (phase 4)** after ruin-recreate (TASK-393) — re-closes gaps the LS reopens. Pipeline is now 6 phases.

## Results — both runs

| measure | run 1 | run 2 | threshold / ref |
|---|---|---|---|
| **hard (final)** | **-1** (H17 ×1) | **0** | 0 wins; TASK-391 R1 was {-66, -70} |
| medium | -1,427,215 | -1,400,893 | — |
| soft | +78,912 | +76,029 | — |
| ph1 (construction) end | -44,100 hard | -44,100 hard | deterministic baseline (byte-identical) |
| ph2 (LS-Compactness) end | -1 hard | **0 hard** | 60-min budget, both |
| **M15** matches / medium | 14 / -26,400 | 13 / -22,800 | must NOT explode (golden itself splits 98×) |
| **G05 knot** (stacked cells) | **0** | **0** | expect 0 |
| H3 stacked cells (ALL groups) | **0** | **0** | expect 0 |
| full-day rate | 77.5% | 77.4% | ≥ ~74% |
| blocks ≥2h | 2,018 | 2,078 | ≥ ~1,800 (golden 1,299) |
| recurrence unpinned | 36.9% | 35.7% | ≥ ~38% (structural ~37%, see caveat) |
| construction compaction (TASK-390) | 149→112 (moved 29) | 149→112 (moved 29) | deterministic |
| final compaction (TASK-393, ph4) | gaps 1→1 (moved 0) | gaps 0→0 (moved 0) | safety net |
| conflicts added (either phase) | **0** | **0** | must be 0 |

Per-run artifacts: `val-run{1,2}/{score.txt, phases.json, analysis.json, lessons.txt, kpi-*.txt, compaction-excerpt.txt}`.

## Verdict — direct answers to the task's questions

**A. Hard finale + stability.** **{-1, 0}** across the two runs — stable at ~0. The hard residual is
**H17-only**: run 1 has a single uncloseable H17 gap (the final compaction tried, `gaps 1→1 moved 0` —
closing it would create a conflict); run 2 has **none** (fully hard-feasible). **H15 is absent from the
hard tier in both runs** — the demotion worked. This is the headline result: against TASK-391's R1
baseline of **{-66, -70}** (which *included* the H15 -60), HEAD lands at **{-1, 0}**. The H15→M15
demotion removed the -60 H15 from the hard tier, exactly as intended. The lone -1 is run-to-run
variance in whether that one H17 gap is avoidable, not a regression.

**B. Did M15 scatter?** **No.** 13–14 matches / -22,800 to -26,400 medium, **stable** across runs.
For reference the golden itself splits same-day-same-subject **98 times**, so the solver at ~13 is
~7× cleaner than the human planner. `ofMedium(20)` held — no scatter-to-hundreds. (A touch above the
"1–few" hope, but firmly within golden practice; the demotion correctly lets the solver trade a few
splits for better day-fill/compactness, which is the intended medium-tier behavior.)

**C. Did the final compaction phase (ph4) act?** It **ran** both times; **conflicts added = 0** always
(the safety guarantee holds). `gapsBefore` was **1 (run 1) / 0 (run 2)**, `moved 0` both times. So the
hypothesis "does the LS reopen holes?" is answered: **marginally** — run 1's LS left 1 gap (= the H17
residual), run 2's left 0. The phase is a **safety net that is effectively a no-op** here: construction
compaction (149→112) + LS already close essentially all gaps, leaving 0–1 for ph4, none closeable
without a conflict. It is harmless (~0.5s) but low-value at current LS quality; **keep-vs-drop is a
Fedora call**, not a correctness issue.

**D. G05 knot.** **0 in both runs**, and **0 stacked cells across ALL groups** (not just G05).
R1 (ruin 5-20) cracks it reliably — consistent with TASK-391. ✅

**E. KPIs held?** full-day **77.4–77.5%** ✅ (≥74); blocks **2,018–2,078** ✅ (≥1800, and above
golden's 1,299); recurrence unpinned **35.7–36.9%** ⚠️ — a hair under the 38% soft target, but
**identical to TASK-391's ~37%** (structural to the current model/seed, not moved by these levers;
golden strict is 41.5%).

## Recommendation
**All 4 levers validate cleanly and reproducibly; HEAD (62f10f8e) is sound.** The H15→M15 demotion
(TASK-392) is the decisive win (hard {-66,-70} → {-1,0}); the `ofMedium(20)` weight is correctly
calibrated (no scatter); the construction compaction (TASK-390) is deterministic and useful
(149→112 gaps, 0 conflicts); the final compaction phase (TASK-393) is a harmless safety net that is
usually a no-op at current LS quality. No lever needs reverting. The only soft spot is recurrence
(~37%, structural, pre-existing) — out of scope for TASK-392/393.

## Caveats
- **n=2** resolves the headline (both ~0 hard, both 0 knot, M15 stable) but is a small sample.
- **Recurrence ~37%** is structural (unchanged from TASK-391); not a regression these levers cause.
- The **-1 H17 in run 1** is variance, not a model flaw — run 2 closed it to 0.
- **Pre-assign is a documented no-op** on the 2026/27 seed (`LabRotation` intentionally empty,
  `DataGenerator.java:1054`) — so both runs are comparable to each other and to TASK-391.
- **Incidental perf observation (NOT a 392/393 regression):** `TargetEndDateCalculator.computeGroupTargetDate`
  logs its per-group summary line **thousands of times per solve** (run 1: ~26.5k lines, run 2: ~8.3k,
  counted within the solve window) — each log line is one full group recompute (a `Course.find` DB query
  + a forward day-scan), so the ideal-end-date calc appears to run in a hot loop during solving rather
  than the expected single setup call. (The run-1 `/tmp/planner.log` also carried prior-session lines,
  so the exact count is uncertain — but it is unambiguously far more than one.) Pre-existing
  (TASK-207 era), unrelated to the 4 levers; worth a Fedora look as a possible ph2-budget drain.
- **laptop measures only.** Any decision on the M15 weight, the final-compaction phase, or recurrence
  is Fedora's, on this evidence.
