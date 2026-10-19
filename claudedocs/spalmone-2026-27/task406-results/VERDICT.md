# TASK-406 A/B (deficit-aware repair tail) — VERDICT: PARTIAL / MIXED (decision deferred to maintainer)

**Branch:** `feature/cartesian-block-entity` @ `bb77e615` (Phase B fix: `DayClusteredConstructionPhase`
repair tail + box-repair capped at M20's `dailyQuota`, deficit-aware slot choice). **Date:** 2026-07-24 (laptop).
**Procedure:** laptop-RUNBOOK, 2 fresh `dev` solves, heap 2g (`PLANNER_HEAP`), `verify-baseline` seed+import
gates (1800 stage / 866 MODA / 280 off-grid both runs), import-calendario + pre-assign, standalone
`caffeinate -dims` + foreground status polls. Baseline = TASK-405b (`21dd0250`/`42da7036`); 401 = `9edf3f64`. n=2.

## Headline: the fix achieves its M20/full-day target but at a recurrence + blocks cost. Gate is PARTIAL.

| metric | 401 baseline | 405b baseline | TASK-406 run1 | TASK-406 run2 | gate | verdict |
|---|---|---|---|---|---|---|
| **M20 medium** | -786k | -895k (avg) | **-854k / 364** | **-878k / 384** | toward -786k | **PARTIAL** — avg -866k, recovered ~29k (~27% of the -109k gap) |
| **full-day** | 77.7-77.8% | 77.2% | **79.3%** | **79.1%** | >=77.5 | **PASS** (+~2pt both runs) |
| day-start | 81.9% | 96.0-96.9% | 95.8% | 96.1% | >=95 | PASS (held) |
| **recurrence unpinned** | 35.8-36.7% | 35.1-35.9% | 35.4% | **33.2%** | >=35 | **FAIL run2** — run2 dips below the floor (405b never did) |
| **blocks >=2h** | 2092-2139 | 2023-2035 | 2000 | 2005 | toward ~2100 | **MARGINAL** — slightly below 405b, moved away from 2100 |
| hard | -1 | 0/-1 | -1 (H17 x1) | 0 | <=-1 H17-only | PASS |
| box (H0) | 0 | 0 | 0 | 0 | 0 | PASS |
| total medium | -1.404M | -1.540M/-1.503M | -1.498M | -1.531M | — | both runs beat 405b |

**5 of 7 gates pass cleanly (M20-partial, full-day, day-start, hard, box). Two regress: recurrence
(run2 = 33.2%, below the 35% floor that 405b always cleared) and blocks (~2000, slightly below 405b and
moved away from the ~2100 target).**

## What the fix did well

- **M20 recovered toward the 401 baseline**, both runs (-854k/-878k, avg -866k vs 405b -895k). ~27% of the
  -109k gap closed. The construction diagnostic predicted this (no-LS M20-proxy deficit 1100h->511h, -54%);
  the live recovery is smaller (~27%) because the LS dynamics dampen the construction gain, and the
  phase-4 `BoxRepair` (TASK-401) is NOT deficit-aware (see gap below).
- **Full-day +2pt** (77.2 -> 79.1-79.3%), the intended day-fill effect.
- **Hard/box/day-start all clean** both runs. The -1165hard construction-end (phase 1) recovered to -1/0
  by phase 4; per maintainer's context the extra construction hard is compaction-flavored and the LS handles it.

## What regressed (the trade-off)

- **Recurrence run2 = 33.2%** (run1 35.4%). 405b was 35.1-35.9% (always >=35). The fix introduced
  recurrence VARIANCE that dips below the floor on one of two runs. Mechanism: the deficit-aware cap
  fills under-days with single leftover lessons instead of letting the tail over-fill early days, which
  scatters leftovers out of the weekly-recurring cells the stamp built, lowering unpinned recurrence.
- **Blocks ~2000/2005** (405b 2023-2035, 401 2092-2139). Capping at expected (not maxHoursPerDay)
  reduces the over-fill that formed incidental 2h blocks in the tail; net blocks edged down, away from
  the ~2100 target.

Both regressions are COHERENT consequences of the same mechanism that buys the M20/full-day gain:
stop over-filling early days, redirect to under-days. It is a genuine trade-off, not a bug.

## Known gap limiting the M20 gain (not addressed by this fix)

The phase-4 `BoxRepair` (TASK-401, in `CompactionPhase`) places the remaining box WITHOUT deficit-awareness
— only the phase-1 construction `boxUnplaceableLeftovers` got the deficit-aware slot choice. Phase 4 traded
-4hard (H0) for +~30k medium both runs (placing boxed lessons into non-deficit slots). Making `BoxRepair`
deficit-aware too is the obvious follow-up lever for a larger M20 recovery without re-touching the tail.

## The bigger picture (why the full-day gap persists at ~79% vs golden 85%)

Even with this fix, full-day is ~79% vs the golden's 85%.4%. The medium breakdown shows TWO compaction
drivers, only one of which this task touched:
- **M20 (daily quota fill): -866k avg** — this task's target, partially recovered.
- **M22 (calendar compactness, empty days within the span): -564k avg / 13 matches** — UNCHANGED, and
  nearly as large as M20. This is the broader lever, now captured as **TASK-408** (non-full-days gap,
  including whether M20/M22 should be re-examined for H-vs-M tier now that the human-heuristics stack
  makes full days achievable by construction).

## Recommendation (decision deferred to maintainer — he is present and engaged)

The gate is partial, not clean. The honest read:
- The fix **works as designed** on its target (M20 down, full-day up) and the construction diagnostic
  (-54% deficit) predicted the direction correctly.
- But the live trade-off is **modest and mixed**: only ~27% of the M20 gap closed, recurrence dipped
  below the floor on run 2, and blocks edged away from target. This is not the "M20 recovered toward -786k
  with nothing else regressed" outcome the gate asked for.

Per the mission's gate-fail rule the strict call would be REVERT; but the M20/full-day gains are real and
maintainer is here to weigh the trade-off. Three defensible options:

1. **REVERT** (strict gate-fail): the recurrence run-2 dip + partial M20 recovery don't clear the bar;
   keep the Phase A diagnostic + TASK-407/408, drop the Phase B fix. Cleanest if recurrence stability is
   non-negotiable.
2. **ADOPT as-is**: the M20/full-day gain is the TASK-406 objective and hard/box/day-start are clean;
   accept the recurrence variance (33-35%) and blocks (~2000) as the cost. Risks locking in a regression
   on a previously-stable metric.
3. **RETUNE (recommended if pursuing)**: the mechanism is right but the cap is too blunt. Two follow-ups
   likely recover M20 WITHOUT the recurrence/blocks cost: (a) make the phase-4 `BoxRepair` deficit-aware
   too (closes the +30k phase-4 medium leak); (b) soften the tail cap so it doesn't scatter leftovers out
   of recurring cells (e.g. cap at expected only when an under-deficit day is actually reachable, else
   fall back to the old over-fill). File as a TASK-406b if maintainer wants to pursue.

## Artifacts

`val406-run{1,2}/`: score, analysis.json (full medium/hard breakdown), phases.json, lessons.txt,
kpi-{fill,daystart,resemblance,moda-knot}.txt, export.json.gz. Run-1 phase-markers empty (log overwritten
by the run-2 restart before capture); construction lines captured inline in the run-1 commit message.
Compare against `claudedocs/spalmone-2026-27/task405b-results/`.
