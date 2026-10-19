# VERDICT: Repair-pass ablation on a BAD basin (grasp-seed-1, H17 G11), 2026-08-05

**Motivation:** the good-basin ablation (VERDICT-repair-ablation-2026-08-04) found the repair
layer is redundant-robust for hard on a good basin (only HoleRepair moved hard, by 6; ExactFinisher
inert). That UNDERESTIMATES the repair layer's hard leverage because a good basin leaves almost no
residual. This study runs the same ablation on a BAD basin (grasp-seed-1: the H17 -9 structural
residual on G11 Wednesdays), where the LS leaves real hard residual and the repair layer has
work to do. **NO ADOPTION**: read-only sensitivity; ScheduleService.java + flags reverted after.

**Setup:** `GRASP_SEED=1 RUN_BASELINE=1 RESULTS_DIR=...repair-ablation-badbasin ./scripts/benchmark/repair-ablation.sh vndfi-ab`.
Same protocol as the good-basin study (disable one repair at a time: ExactFinisher via flag,
HoleRepair/AnchorRepair via commented invocation + rebuild) but the construction is the GRASP
seed-1 bad basin (source=input, multi-start OFF, single deterministic solve). Baseline = all
repair ON on the bad basin (run fresh, since it differs from the good-basin baseline).

## Ex-ante prediction (written BEFORE the outcome, 2026-08-05, pre-registered)

This is a CHARACTERIZATION and the prediction is genuinely bivalent, because TASK-481 found that
the bad-seed-1 H17 residual (the @10 slot on G11 Wednesdays) is a SHELL-GAME: a mid-run
saturated slot whose blocker is edge-removable only by relocating the hole to another group (a
cascade that CompositionHoleCloser/HoleRepair cannot close without shelling). That is precisely
WHY multi-start (basin selection) is the ratified cure for H17, not a repair pass. So:

- **If the bad-seed residual is repair-closeable** (it has a boxed-H0 or plain-mid-day-H17
  component the repair can close): ablating that repair shows a LARGE hard rise. Per the user's
  framing, ExactFinisher-off (boxed H0) and HoleRepair-off (mid-day H17) would be much more
  load-bearing here than on the good basin. If ExactFinisher-off sends hard to a large negative,
  ExactFinisher is the most important repair on bad basins.
- **If the bad-seed residual is the structural @10 shell-game** (uncloseable by local repair):
  ablating any repair shows a SMALL hard delta (the repair was inert on the uncloseable part
  regardless), mirroring the good-basin result. This would CONFIRM that the bad-seed H17 is a
  CONSTRUCTION/basin problem (multi-start is the only cure), not a repair problem, and that
  investing repair-pass tuning would not fix the H17 bad-seed cases.

Per-pass priors on the bad basin:
- **ExactFinisher**: closes the boxed H0 tail. Delta = the boxed-H0 component of the bad-seed
  residual. If the residual is mostly H17 (not H0), EF stays near-inert (as on the good basin).
- **HoleRepair**: closes mid-day H17. The @10 residual is the relevant target, but TASK-481 says
  it is a shell-game HoleRepair cannot close. So HoleRepair-off delta may be small even here
  (it closes only the plain mid-day breaks, few on this basin).
- **AnchorRepair**: leading-slot M18 / medium. Hard-inert likely (as on the good basin); the
  day-start lever.

Most likely outcome: SMALL-to-MODERATE deltas, dominated by whether the bad-seed residual has a
closeable component. The decisive signal is the SIZE of the deltas: large deltas vindicate the
"repair closes bad-basin hard" framing and identify the load-bearing pass; small deltas vindicate
the "bad-seed H17 is a basin problem, multi-start is the cure" framing (TASK-481). Either is
decisional: it tells the campaign whether to invest in repair-pass tuning or in basin selection
for the H17 bad-seed cases.

## Outcome

Run completed (background `b7gopqu47`, profile vndfi-ab, GRASP seed=1, source=input,
multi-start OFF, all four runs single-deterministic). GRASP engaged on every run (banner
`grasp=true seed=1`); the bad basin was produced (baseline hard=-12, not 0). ScheduleService.java
reverted (git-clean, 4 repair invocations active, 0 ABLATION comments).

| run | hard | medium | soft | wall | full-day | recurrence | day-start |
|-----|-----:|------:|----:|-----:|---------:|-----------:|----------:|
| baseline (all ON) | -12 | -1180548 | -26909 | 1946s | 84.9% | 74.4% | 100.0% |
| exact-finisher-off | -10 | -1183446 | -25483 | 2394s | 85.1% | 73.9% | 100.0% |
| hole-repair-off | -22 | -1171334 | -29404 | 1332s | 84.8% | 74.1% | 99.9% |
| anchor-repair-off | -10 | -1132249 | -21115 | 2588s | 84.1% | 74.3% | 97.4% |

**Verdict: HYBRID outcome (both ex-ante branches partially confirmed). HoleRepair is the only
load-bearing hard repair on the bad basin; ExactFinisher and AnchorRepair are hard-inert; a
structural uncloseable residual remains that validates multi-start.**

- **HoleRepair is the hard lever: hole-repair-off is -22 vs baseline -12, i.e. HoleRepair closes
  ~10 hard.** This is the one clear, out-of-noise signal. So the bad-seed residual has a
  CLOSEABLE component (~10 hard of plain mid-day H17) that HoleRepair removes.
- **ExactFinisher is hard-inert on the bad basin** (exact-finisher-off -10 vs baseline -12). The
  bad-seed residual is H17, not boxed-H0, so ExactFinisher (the H0 closer) has nothing to close
  here. This REFUTES the user's hypothesis that ExactFinisher-off would send hard to a large
  negative: ExactFinisher's value is H0, and the bad-seed-1 problem is H17.
- **AnchorRepair is hard-inert** (anchor-repair-off -10 vs baseline -12) but is the DAY-START
  lever (97.4% vs 100.0%, -2.6pp), as on the good basin.
- **The -2 "improvements" from disabling ExactFinisher/AnchorRepair (-12 -> -10) are within
  LS-trajectory sensitivity, not a real signal.** The LS is downstream of the repair passes, so
  removing one shifts the LS's starting point and lands on a slightly different hard (a 2-hard
  wobble). The robust, out-of-noise signal is HoleRepair's +10.
- **A structural uncloseable residual remains at -12 even with all repair ON.** This is the
  shell-game @10 component (TASK-481): a mid-run saturated slot no local repair can close. It is
  exactly why multi-start (basin selection) is the ratified cure for H17 (TASK-483): multi-start
  AVOIDS the bad basin rather than repairing it. This study CONFIRMS that.

## Conclusion (decisional, complements the good-basin study)

- **HoleRepair is the load-bearing hard repair on BOTH basins** (good: -6; bad: -10). It is the
  one repair worth hard-tuning investment. But even with HoleRepair (all ON) the bad basin still
  leaves -12 structural, so HoleRepair tuning can REDUCE bad-basin hard by ~10 but cannot reach
  0. Multi-start remains essential for the structural residual.
- **ExactFinisher and AnchorRepair are hard-inert on BOTH basins.** ExactFinisher's value is the
  boxed-H0 tail (which appears on bad seeds with a boxed tail, NOT this H17 bad seed; on
  grasp-seed-6 room-null it might differ). AnchorRepair's value is day-start (medium), where it
  IS load-bearing on both basins.
- **Multi-start (TASK-483) is validated as the only cure for the structural H17 bad-seed
  residual.** Repair-pass tuning cannot close the -12 structural part; only avoiding the basin
  (multi-start) does. This closes the loop: the campaign's multi-start ratification is the right
  call for H17, and repair-pass tuning is a marginal (~10 hard) supplement, not a substitute.

## What this study does NOT cover

grasp-seed-6 (the room-null -1 bad seed, a tag-saturation failure, not H17) may have a different
repair-leverage profile (ExactFinisher or room-repair could matter there). This study is
grasp-seed-1 (H17) only. A seed-6 ablation would complete the picture, but the H17 case (the
campaign's main struggle) is now characterized.

NO ADOPTION. All repair passes stand (default ON); ScheduleService.java and all flags reverted.
