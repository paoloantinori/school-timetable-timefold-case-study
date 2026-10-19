# TASK-394+405 A/B — fact-based ideal-end dates + day-start anchoring (widened M18 ofMedium(250) + deterministic left-anchor pass): VERDICT **RETUNE (lower the M18 weight)**

**Branch:** `feature/cartesian-block-entity` @ `8098e6db` (TASK-394 fact-based ideal-end via
`DailyActivityQuota` expected-hours cumulation + TASK-405 M18 widened to every group-day with
`ofMedium(250)`×offset gradient + `StudentDayCompaction.anchorRunLeft` deterministic left-anchor pass).
**Date:** 2026-07-24 (laptop). **Procedure:** 2 live solves, fresh `dev` profile each, heap 2g (PLANNER_HEAP
default), `verify-baseline` seed+import gates, foreground-poll-guarded (background pollers were killed at
~12 min by this harness, so the standalone `caffeinate` + solver CPU + continuous foreground polling kept
the runs valid). Baseline = TASK-401 (`9edf3f64`). **laptop measures only — no adopt/revert/config change
on the laptop.** n=2.

## VERDICT: RETUNE — mechanism works and delivers the day-start goal, but `ofMedium(250)` is too strong

Both runs pass the hard/box/day-start gates **cleanly** and the headline wins are real and stable:
**day-start 81.9% → 96.2-96.7%**, **hard -1 → 0**, **box 0**, **M12 overflow -24,022 → -7,014 avg**
(TASK-394 fact-based targets), and the deterministic left-anchor pass anchors **132 days (50 phase-1 +
82 phase-4) at 0 conflicts**, byte-identical across both runs. **TASK-405's core idea works.**

But the price the `ofMedium(250)` weight exacts is the brief's predicted "too strong" signature, confirmed
in **both** runs: **full-day regressed 77.7-77.8 → 76.8-77.1**, **recurrence regressed 35.8-36.7 → 34.3-34.6
(both runs below the ~35% floor)**, and **total medium regressed ~85-165k**. Per the brief: "if day-start
improves but full-day or recurrence regresses, that points to 250 being too strong — report, do not retune."
That is exactly what both runs show. **Do not ADOPT as-is; do not REVERT. Lower 250 and re-benchmark on
Fedora.** The deterministic left-anchor pass anchors 132 days for free regardless of the weight, so most of
the day-start gain should survive a lower M18 weight — the weight is only the local-search nudge for the
~72-84 still-floating group-days, and at 250 it is over-nudging into the recurrence/full-day trade.

## Per-run results vs baseline

| metric | run 1 | run 2 | baseline (TASK-401 `9edf3f64`) | gate | verdict |
|---|---|---|---|---|---|
| **final hard** | **0** | **0** | -1 | ≤ baseline (0 better) | **PASS** |
| **box (H0 un-taught)** | **0** | **0** | 0 | == 0 | **PASS (critical)** |
| **grid row count** | 12301 | 12301 | 12301 | == 12301 (12581-280 off-grid) | **PASS** |
| **G05 knot (H3)** | **0** | **0** | 0 | 0 | **PASS** |
| **DAY-START anchored** | **96.7%** (2118/2190) | **96.2%** (2105/2189) | 81.9% | ≥ ~90% (target ~95%+) | **PASS — big win** |
| full-day rate | 76.8% | 77.1% | 77.7-77.8% | ≥ ~74% | PASS floor, **-0.7-1.0 pt regression** |
| blocks ≥2h | 2038 | 2064 | 2092-2139 | ≥ ~1800 | PASS floor, **-28 to -101 regression** |
| recurrence unpinned | 34.6% | 34.3% | 35.8-36.7% | ≥ ~35% | **BORDERLINE FAIL (both under floor & baseline)** |
| M15 (scatter, golden 98) | 28 / -76,800 | 13 / -25,200 | 19-23 | no explosion | high variance, avg ~20.5 (in band); run-1 spike 28 not reproduced |
| M18 (new widened, was -14/14) | 72 / -21,500 | 84 / -25,250 | — | (new visible medium) | ~23k new medium |
| M12 (overflow, TASK-394) | 221 / -7,298 | 190 / -7,014 | 865 / -24,022 | ~0 expected | **big drop (~-17k better); not ~0 but small, genuine lateness** |
| **total medium** | -1,569,559 | -1,505,337 | -1,404,375 / -1,420,xxx | not worse | **regressed ~+85-165k** |
| solve wall (best score) | 77.1 min | 73.2 min | ~similar | — | parity |

## The deterministic pass (the part of TASK-405 that clearly works)

Both runs are **byte-identical** on the construction/compaction markers (NON_REPRODUCIBLE mode, so this is
genuine determinism of the custom-phase passes, not luck):

- **Phase 1 (construction tail):** `TASK-390/405 compaction: student-day gaps 219->138 (moved 60),
  left-anchored 50, conflicts added 0`. BOXED 61 (TASK-398 box-the-residual), construction hard **-1156**
  (matches the 401 baseline's -1160).
- **Phase 4 (box repair + final compaction):** `TASK-401 box repair (pre-compaction): boxed 5 -> placed 5,
  still boxed 0, conflicts added 0`; then `TASK-393/405 final compaction: student-day gaps 0->0 (moved 0),
  left-anchored 82, conflicts added 0`. → box 0, zero repair conflicts.

The left-anchor pass anchored **132 days total (50 + 82) at zero conflicts and zero gaps** in both runs —
the hard-safety the Opus skeptic confirmed holds in production. This is the load-bearing mechanism and it
is stable. It is NOT what needs retuning.

## Why 250 is too strong (the brief's signature, confirmed twice)

The brief predicted: "if day-start improves but full-day or recurrence regresses, that points to 250 being
too strong." Both runs show exactly that, in a consistent direction:

- **recurrence 35.8-36.7 → 34.3-34.6** (both runs below baseline AND the ~35% floor — a real ~1.5 pt
  regression, not noise: two consistent draws). Mechanism (the brief's own): anchoring everyone to the
  left compresses the morning and raises teacher contention, which costs weekly recurrence.
- **full-day 77.7-77.8 → 76.8-77.1** (~1 pt) and **blocks 2092-2139 → 2038-2064** (~28-101) — small but
  consistent dips, same morning-contention mechanism.
- **total medium +85-165k.** Composition (run1/run2): the largest mover is **M20 (Daily quota fill)
  -786,000 → -899,000 / -883,000 (+~105k; matches 315 → 383, reproducible)**, then **M22 +~24k**, then the
  new **M18 +~23k**, then **M15 +~8k avg (high variance)**; offset by **M12 -~17k** (TASK-394) and
  **M28 -~10k**. Net ~+127k worse. **The M20 jump is the single biggest contributor to the medium
  regression and is NOT obviously caused by the M18 weight alone — flag for Fedora to diagnose** (candidate:
  the TASK-394 repair-tail bound, now plan-end, reshapes which days the construction places leftovers into).

## Notes / caveats

- **n=2** — both runs agree on hard 0, box 0, day-start ~96%, the 132-day deterministic pass (byte-identical),
  and the direction of the full-day/recurrence/medium regressions. The variance is concentrated in **M15
  (28 vs 13 matches)** — run 1's 28 is a high draw, run 2's 13 is below the 401 band; averaged (~20.5) M15
  did NOT systematically explode (golden is 98), so the 401 watch item is **not confirmed as a trend** but
  M15's run-to-run spread widened.
- **M12 did not reach ~0** (brief's expectation). It dropped sharply (-24,022 → ~-7,156 avg, ~70% reduction)
  — TASK-394's fact-based targets clearly work and the gradient now points the right way, but **~190-221
  group-days still overflow the plan** (~7k medium). This is genuine lateness the solver could not relieve
  under capacity (G06 structurally needs the whole year, TASK-394's own analysis), not a targeting bug.
- **Day-start residuals** (where days still float) concentrate, as the backlog predicted, in the window-8
  groups: **G10 (15-22 late), G11 (16), G07 (13-17), G08 (10-11), G13 (9-10)**. First-hour
  histogram {8: ~2100, 9: 72-80, 10: 12-15, 11: 1}: the residual is almost all offset-1 (09:00 start).
- **No solve-time change** (~73-77 min, parity with 401). The benefit is shape (day-start), not speed.
- **Phase-2 LS-Compactness** ran its full 3600s in both runs (best -5hard at phase-2 end), then phase-3
  RuinRecreate + phase-4 repair/compaction + phase-5/6 rooms drove hard -5 → **0**.
- **Artifacts** in `val405-run{1,2}/`: score, phases, analysis, lessons dump, kpi-fill, kpi-resemblance,
  kpi-moda-knot, kpi-daystart, phase-markers-log (phase-1 + phase-4 box-repair/compaction lines),
  export.json.gz. **No config change made on the laptop** (HEAD `8098e6db` shipped as-is).

## Net

**RETUNE, do not ADOPT or REVERT.** TASK-394 (fact-based ideal-end) and TASK-405's deterministic
left-anchor pass both work end-to-end and deliver the headline wins: **day-start 81.9 → 96.2-96.7%**,
**hard -1 → 0**, **box 0**, **M12 ~-24k → ~-7k**, the **132-day deterministic anchor at 0 conflicts**
(byte-identical across runs). The only failure is the **`ofMedium(250)` weight over-nudging** the
local search: it costs ~1 pt full-day, ~1.5 pt recurrence (both runs below the ~35% floor), and ~+127k
medium (dominated by an M20 jump to diagnose). **Recommendation for Fedora: keep the mechanism, lower the
M18 weight (the deterministic pass carries most of the day-start gain independent of the weight; 250 is
only the LS nudge for the ~72-84 residual floaters and is too strong), and re-benchmark — watch recurrence
recover back over ~35% and the M20 medium move. If the M20 jump survives a lower M18 weight, it is a
TASK-394 repair-tail effect, not an M18 effect, and gets its own diagnosis.**
