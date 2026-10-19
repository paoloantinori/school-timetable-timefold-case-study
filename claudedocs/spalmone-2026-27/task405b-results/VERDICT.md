# TASK-405 A/B #2 (retune validation) — M18 weight 250 -> ofMedium(50): VERDICT **ADOPT** (and isolate the residual medium gap as a TASK-394 repair-tail effect, NOT M18)

**Branch:** `feature/cartesian-block-entity` @ `21dd0250` (the M18 weight lowered `ofMedium(250)` ->
`ofMedium(50)` per the A/B #1 RETUNE verdict; TASK-394 fact-based ideal-end + the deterministic
`StudentDayCompaction.anchorRunLeft` left-anchor pass unchanged). **Date:** 2026-07-24 (laptop).
**Procedure:** identical to A/B #1 — 2 live solves, fresh `dev` profile each, heap 2g (`PLANNER_HEAP`
default via `dev-start-and-wait.sh dev`), `verify-baseline` seed+import gates, standalone `caffeinate`
sleep-guard + continuous foreground-poll windows (the background-poll workaround: the solver runs
server-side on the JVM and survives any client poller the harness kills; only the laptop must stay awake).
Baseline = TASK-401 (`9edf3f64`); compared also against A/B #1 = TASK-405 @ weight 250 (`8098e6db`).
**laptop measures only — no adopt/revert/config change on the laptop.** n=2.

## VERDICT: ADOPT the weight-50 retune. The retune met its goal; the residual medium gap is a TASK-394 repair-tail effect on M20, now proven NOT to be M18.

The retune to `ofMedium(50)` did exactly what A/B #1's RETUNE verdict asked: **recurrence recovered
above the ~35% floor (34.3-34.6 -> 35.1-35.9)**, **day-start HELD at 96%** (the deterministic left-anchor
pass carries it independent of the weight, exactly as predicted), **hard/box clean** (0 / -1 with box 0
in both), and **total medium marginally better than weight 250** (~16k better on average, equal to the
M18 reduction). The mechanism works and the weight is now calibrated.

The DIAGNOSTIC is conclusive and is the headline finding: **the +105k M20 jump SURVIVES weight 50
unchanged** (-899k/-883k at 250 -> -904k/-885k at 50, 378 matches both runs). Lowering M18 5x moved M18's
own medium ~5x (-23.4k -> -5.0k avg, float counts held) but left M20 untouched. Therefore the medium
regression vs the TASK-401 baseline is **a TASK-394 repair-tail effect** (the plan-end bound reshaping
which days the construction places leftovers into), **not an M18 pressure effect**. This is the
discriminant the brief asked for; it hands Fedora a dedicated diagnosis (M20 + full-day + blocks all
track the same morning-compression signature) and removes M18 as the suspect.

## Three-way comparison (401 baseline / 405@250 / 405@50)

| metric | 401 baseline (`9edf3f64`) | 405@250 (`8098e6db`) run1/run2 | 405@50 (`21dd0250`) run1/run2 |
|---|---|---|---|
| **final score** | -1hard/-1.40M | 0/-1.570M , 0/-1.505M | **0/-1.540M , -1/-1.503M** |
| **final hard** | -1 | 0 / 0 | **0 / -1** (run2 = 1x H17, not H0) |
| **box (H0 un-taught)** | 0 | 0 / 0 | **0 / 0** |
| **G05 knot (H3)** | 0 | 0 / 0 | **0 / 0** |
| **DAY-START anchored** | 81.9% | 96.7% / 96.2% | **96.0% / 96.9%** |
| full-day rate | 77.7-77.8% | 76.8% / 77.1% | 77.2% / 77.2% |
| recurrence unpinned | 35.8-36.7% | 34.6% / 34.3% (under floor) | **35.9% / 35.1%** (recovered) |
| blocks >=2h | 2092-2139 | 2038 / 2064 | 2035 / 2023 |
| **total medium** | -1.404M / -1.420M | -1.570M / -1.505M (avg -1.537M) | -1.540M / -1.503M (avg -1.521M) |
| **M20 medium** (diagnostic) | **-786k** | -899k / -883k (avg -891k) | **-904k / -885k (avg -895k)** |
| M20 matches | — | 383 / 315 | 378 / 378 |
| M15 medium / matches | 19-23 matches | -76,800/28 , -25,200/13 | -56,400/24 , -40,800/21 |
| **M18 medium** (own) | ~-14 (flat ONE_MEDIUM) | -21,500/72 , -25,250/84 (avg -23.4k) | **-5,700/87 , -4,400/68 (avg -5.05k)** |
| M12 overflow (TASK-394) | -24,022 | -7,298 / -7,014 | -6,433 / -6,510 |
| deterministic left-anchor | — | 132 (50 ph1 + 82 ph4) | 131 (50+81) / 138 (50+88) |
| solve wall (best score) | ~similar | 77.1 / 73.2 min | ~70 / ~70 min |

## GATE check (405@50, both runs)

| gate (per the brief) | run 1 | run 2 | verdict |
|---|---|---|---|
| hard no worse than baseline (-1), **box = 0** | 0 / box 0 | -1 (1x H17) / box 0 | **PASS** (box=0 both; run2's -1 is H17, not H0) |
| day-start HELD >= ~95% | 96.0% | 96.9% | **PASS — held strongly** |
| recurrence RECOVERED >= ~35% | 35.9% | 35.1% | **PASS — both above the floor** (250 was under it) |
| full-day back >= ~77.5% | 77.2% | 77.2% | **MARGINAL** — 0.3 pt under 77.5, but up from 250's 76.8-77.1 |
| blocks back toward ~2100 | 2035 | 2023 | **NOT MET** — flat with 250, still ~70-110 below 401 |
| total medium better than 250-weight | -1.540M | -1.503M | **PASS** — avg -1.521M vs 250's -1.537M (~16k better) |

The two headline gates (recurrence recovered, day-start held) and the critical hard/box gate all PASS.
full-day is a hair under target (recovered direction); blocks did not move back toward 2100. **Both
residuals track the M20 / TASK-394 morning-compression signature (see DIAGNOSTIC), not M18** — so they
belong to the TASK-394 diagnosis, not to further M18 tuning.

## DIAGNOSTIC — the key discriminant (conclusive)

The brief: "report M20's medium in both runs — if it stays ~-880/-900k even at weight 50, the +105k M20
jump is a TASK-394 repair-tail effect, NOT an M18 effect; if it returns toward -786k, it was M18
pressure."

- **M20 SURVIVES weight 50.** -904k/-885k (avg -895k) at weight 50 vs -899k/-883k (avg -891k) at weight
  250 — essentially unchanged, and 378 matches in BOTH 50-runs (vs 383/315 at 250, i.e. if anything
  slightly more consistent). It did NOT return toward the -786k baseline. **=> The +105k M20 jump is a
  TASK-394 repair-tail effect (plan-end bound reshaping construction), NOT an M18 effect.** Lowering M18
  5x left M20 untouched. **Fedora to open a dedicated diagnosis on the TASK-394 repair-tail -> M20 /
  full-day / blocks link.**
- **M18 scales as predicted (~1/5).** M18's own medium: -5,700/87 , -4,400/68 (avg -5.05k) vs 250's
  -21,500/72 , -25,250/84 (avg -23.4k). Ratio ~0.22 (~1/4.5), matching the predicted ~1/5. The float
  COUNT held (87/68 avg 77.5 vs 250's 72/84 avg 78), so the medium scaled ~linearly with the 5x weight
  drop — M18 behaves exactly as the weight change predicts. At weight 50 M18 is a -5k contributor (was
  -23k); it is no longer a meaningful medium mover.
- **M15 stable, variance narrowed.** 24/21 matches (avg 22.5, medium -48.6k avg) vs 250's 28/13 (avg
  20.5). In band with the 401 baseline (19-23) and far under golden 98. The run-1@250 spike of 28 was
  NOT reproduced; the run-to-run spread tightened. Not a trend. (High variance remains M15's nature;
  the 401 watch item is still not confirmed as a systematic regression.)

## The deterministic pass (byte-stable phase-1; phase-4 anchors 131-138 days at 0 conflicts)

Both runs confirm the part of TASK-405 that works regardless of the weight:

- **Phase 1 (construction tail), byte-identical to A/B #1:** `TASK-398 box-the-residual: placed 237
  conflict-free leftovers, BOXED 61`; `TASK-390/405 compaction: student-day gaps 219->138 (moved 60),
  left-anchored 50, conflicts added 0`; `Day-clustered CH complete: placed 9574 ... BOXED 61`.
- **Phase 4 (box repair + final compaction), 0 conflicts both runs:**
  - run 1: `TASK-401 box repair: boxed 1 -> placed 1, still boxed 0, conflicts added 0`; then
    `TASK-393/405 final compaction: gaps 0->0 (moved 0), left-anchored 81, conflicts added 0` -> **box 0, hard 0**.
  - run 2: `TASK-401 box repair: boxed 3 -> placed 3, still boxed 0, conflicts added 0`; then
    `TASK-393/405 final compaction: gaps 1->1 (moved 0), left-anchored 88, conflicts added 0` -> **box 0,
    1 residual gap (the single H17 = run 2's -1 hard)**.
- The left-anchor pass anchored **131 days (50+81) / 138 days (50+88) at zero conflicts and zero
  repair-added conflicts.** Phase-1 is byte-stable at 50; phase-4's left-anchor count varies (81/88 vs
  A/B #1's 82) with the box-repair input (boxed 1/3 vs 5), all at conflicts-added 0. This is the
  load-bearing hard-safety mechanism and it is stable at weight 50.

## Why ADOPT (not RETUNE-AGAIN, not REVERT)

- **The retune met its stated goal.** Recurrence is back above the ~35% floor (the A/B #1 failure mode),
  day-start held at 96% (deterministic pass carries it), M18 scales correctly with the weight, and total
  medium is marginally better than weight 250. Weight 50 is the right calibration of the M18 LS nudge:
  it makes the ~70-80 residual offset-1 floaters non-free without competing at M20/M22 scale.
- **RETUNE-AGAIN (lower M18 further) would not help.** The residual ~110k medium gap vs the TASK-401
  baseline is dominated by M20 (-895k vs -786k = ~109k), now proven independent of the M18 weight. M18
  at 50 is already only a -5k mover; lowering it more buys negligible medium and risks under-anchoring
  the residual floaters (eroding the day-start win the deterministic pass + weight 50 deliver together).
- **REVERT would lose real wins.** TASK-405 delivers day-start 81.9 -> 96% and TASK-394 delivers M12
  -24k -> -6.5k; both are correct model changes (golden-first). Reverting to 401 to recover ~110k medium
  would give up both, and that medium is a repair-tail artifact to diagnose, not a reason to undo the
  model.

## Notes / caveats

- **n=2** — both runs agree on box 0, day-start ~96%, recurrence back over the floor, the byte-stable
  phase-1 pass, and M20 ~-895k (the diagnostic). The variance is concentrated in **hard (0 vs -1)** and
  **M15 (24 vs 21)**; run 2's single -1 hard is one H17 gap the final compaction logged `gaps 1->1`
  (could not close), NOT a box — the box=0 gate holds in both.
- **full-day 77.2% (0.3 pt under the 77.5 target)** and **blocks ~2030 (not back to ~2100)** did not
  fully recover. Both track the M20/repair-tail morning-compression signature (the same effect that
  moved M20 +105k), so they are part of the TASK-394 diagnosis, not M18. They are also within the
  variance band of A/B #1 (76.8-77.1 / 2038-2064), i.e. weight 50 is no worse than 250 here and the
  retune did not introduce a new regression.
- **M12 did not reach ~0** (~-6.5k, 212-217 group-days still overflowing). Same finding as A/B #1:
  TASK-394's fact-based targets work and point the right way, but ~190-220 group-days genuinely overflow
  under capacity (G06 structurally needs the whole year) — real lateness, not a targeting bug.
- **Day-start residuals** (where days still float) concentrate in the window-8 groups as the backlog
  predicted: histogram first-hour {8: ~2095-2124, 9: 68-75, 10: 12-23, 11: 1-2} — the residual is almost
  all offset-1 (09:00 start). See `kpi-daystart.txt`.
- **No solve-time regression** (~70 min both runs, parity with 401/405@250). The benefit is shape
  (day-start), not speed.
- **Artifacts** in `val405b-run{1,2}/`: score, phases, analysis, lessons dump, kpi-fill, kpi-resemblance,
  kpi-moda-knot, kpi-daystart, phase-markers-log (phase-1 + phase-4 box-repair/compaction lines),
  export.json.gz. **No config change made on the laptop** (HEAD `21dd0250` shipped as-is).

## Net

**ADOPT the `ofMedium(50)` retune.** It recovers recurrence above the floor, holds day-start at 96%
(via the deterministic pass), keeps hard/box clean, scales M18 exactly as predicted, and is marginally
better than weight 250 on total medium. **The residual ~110k medium gap vs the TASK-401 baseline is a
TASK-394 repair-tail effect on M20 (proven: M20 is unchanged at weight 50), NOT an M18 effect** —
handing Fedora a dedicated diagnosis (M20 + full-day + blocks) and clearing M18. Do not RETUNE-AGAIN on
M18 (the gap is upstream of it); do not REVERT (would lose the day-start and M12 wins).
