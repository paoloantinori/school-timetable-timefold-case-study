# VERDICT: Repair-pass ablation (read-only sensitivity), 2026-08-04

**Motivation (from the VND-FI staged A/B):** on the staged pipeline both Tabu and VND-FI reach
hard=0, so the LS acceptor is inert on hard. The orchestration layer (the deterministic repair
passes) owns hard=0. This study measures HOW MUCH each repair pass contributes, by disabling one
at a time. **NO ADOPTION**: disabling a repair is only to measure; ScheduleService.java and all
flags are reverted after. Decisional value: the most load-bearing repair is where future tuning
investment pays off.

**Setup:** `scripts/benchmark/repair-ablation.sh vndfi-ab`. Baseline = all repair ON, reused from
the vndfi-ab tabu-baseline (same profile, committed: 0hard/-1051561medium/+4301soft, full-day
87.1%, recurrence 74.1%, day-start 99.5%, wall 1600s). Each ablation disables ONE pass (rest ON),
source=input, multi-start OFF, single deterministic solve:
- `exact-finisher-off`: `-Dplanner.exact-finisher.enabled=false` (flag-gated, runtime, no rebuild).
- `hole-repair-off`: comment the `runHoleRepair(roomProblem)` call (unconditional pass, edit + rebuild).
- `anchor-repair-off`: comment the `runAnchorRepair(roomProblem)` call (unconditional pass, edit + rebuild).

(SurplusRebalance and LabDayConsolidation are not in this round; CompositionHoleCloser is
default-OFF already.)

## Ex-ante prediction (written BEFORE the outcome, 2026-08-04, pre-registered)

This is a CHARACTERIZATION, not a confirmation, and the prediction is genuinely uncertain for
one reason: the vndfi-ab profile is a GOOD basin (all-ON reached hard=0). On a good basin the
construction + LS may leave only a SMALL hard residual, so each repair pass closes a small share
and the per-pass deltas could all be small. The informative signal is therefore which pass, when
removed, produces the LARGEST hard rise (or the largest medium/day-start regression).

Per-pass reasoning:
- **ExactFinisher** is the deterministic H0 closer (closes the small unassigned "boxed" tail the
  LS leaves). Prediction: if the LS leaves any boxed tail on this seed, ExactFinisher-off shows
  the LARGEST hard rise. Caveat: on a clean basin the LS may place everything (boxed=0 at EF
  entry), making EF inert and its delta small.
- **HoleRepair** closes internal mid-day H17 student-day gaps. Prediction: a moderate hard rise
  (the H17 residual the LS leaves). H17 is the campaign's recurring hard struggle, so this could
  be the load-bearing one if the LS leaves H17 holes.
- **AnchorRepair** closes LEADING M18 late-start holes (cross-date import); M18 is medium, though
  it also closes some leading H17. Prediction: the SMALLEST hard impact of the three, but the
  LARGEST day-start regression (it is the leading-slot anchor).

Most likely ranking by hard-impact: ExactFinisher >= HoleRepair > AnchorRepair, with day-start
dominated by AnchorRepair. Realistic alternative: on this good basin all three hard deltas are
small (each repair closes a tiny residual), and the study's value is showing the orchestration
is REDUNDANT-ROBUST (no single repair is a hard=0 single-point-of-failure here) rather than
finding one dominant pass. Either outcome is decisional.

Refutation of the framing would be: every ablation still reaches hard=0 (the remaining repair
passes + LS cover for the disabled one), meaning the repair layer is fully redundant on this
seed and the "repair owns hard" finding is robust to single-pass failure.

## Outcome

Run completed (background `b08vsiwdo`, profile vndfi-ab reused, source=input, multi-start OFF,
all three ablations single-deterministic). ScheduleService.java reverted (git-clean,
runHoleRepair/runAnchorRepair invocations active, 0 ABLATION comments).

| ablation | hard | medium | soft | wall | full-day | recurrence | day-start |
|----------|-----:|------:|----:|-----:|---------:|-----------:|----------:|
| baseline (all ON) | 0 | -1051561 | +4301 | 1600s | 87.1% | 74.1% | 99.5% |
| exact-finisher-off | 0 | -1040884 | +5756 | 1970s | 87.4% | 74.5% | 99.3% |
| hole-repair-off | -6 | -1026885 | +4629 | 1977s | 87.7% | 74.3% | 99.1% |
| anchor-repair-off | 0 | -1021603 | +5583 | 1748s | 87.8% | 74.5% | 98.1% |

**Verdict: the "redundant-robust on a good basin" branch of the ex-ante won, not the
"ExactFinisher most load-bearing" branch. Hard is stubbornly stable; only HoleRepair moves it,
and only by 6.**

- **Hard: HoleRepair is the ONLY pass with a hard effect, and it is tiny (-6).** ExactFinisher-off
  and AnchorRepair-off both stay at hard=0. ExactFinisher is INERT on this good basin (boxed=0 at
  its entry, as the CLAUDE.md predicts for good seeds; its value is on bad seeds where the LS
  leaves a boxed tail). AnchorRepair is hard-inert here. So no single repair is a hard=0
  single-point-of-failure on this seed: the construction + LS already leave almost no hard
  residual, and the repair layer is a safety net that is barely exercised.
- **Day-start: AnchorRepair IS load-bearing** (98.1% vs 99.5%, -1.4pp). It is the leading-slot
  anchor; this is its job (medium-tier M18, not hard). If day-start tuning matters, AnchorRepair
  is the lever.
- **Every repair COSTS a little medium/soft.** Disabling any one slightly IMPROVES medium
  (-1021603 to -1040884 vs baseline -1051561) and soft. The repair passes move lessons to secure
  structure (close holes, anchor starts), which disturbs the LS's medium/soft optimum. On this
  good basin where hard is already ~0, that medium/soft cost is "paid for nothing" on hard, so
  ablating the repair nets a small medium/soft gain. (SurplusRebalance, not ablated here, is the
  one pass whose purpose IS medium, so it would likely not show this pattern.)
- Wall differences are LS-variance, not repair (the repair passes are deterministic and fast; the
  1600-1977s spread is within the LS band seen across prior staged runs).

## Conclusion (decisional)

- **For hard=0 tuning on a GOOD basin: no repair is the lever.** Hard is secured by construction
  + LS; the repair layer is redundant-robust here (only HoleRepair contributes, -6). Investing
  hard-tuning effort in a repair pass would not pay off on good basins.
- **For DAY-START: AnchorRepair is the load-bearing pass** (-1.4pp when removed). That is where
  day-start tuning investment would land.
- **The "repair owns hard=0" framing (from the VND-FI A/B) is refined:** repair is the hard
  SAFETY NET, crucial on bad basins and barely exercised on good ones. This study was on a good
  basin and therefore UNDERESTIMATES the repair layer's hard leverage. A bad-basin ablation
  (e.g., the H17 G11 @10 seed) is the experiment that would quantify the real hard
  contribution of HoleRepair/ExactFinisher; that is the natural next ablation if repair-lever
  tuning becomes a goal.

NO ADOPTION. All repair passes stand (default ON). The study is a read-only sensitivity
measurement; ScheduleService.java and all flags were reverted.

## Open follow-up (not this study)

Ablate the repair passes on a BAD basin (the H17 G11 bad-seed, where hard is nonzero and the
LS leaves real residual) to measure the repair layer's hard leverage where it actually matters.
On this good-basin run the leverage is near-zero (redundant-robust), so the good-basin numbers
must not be read as the repair layer's production value.
