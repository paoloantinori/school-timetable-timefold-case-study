# VERDICT - M32 template-deviation numerator sensitivity sweep

**Status:** measurement spike, READ-ONLY. NO ADOPTION (default stays `100000`).
**Date:** 2026-08-04. **Profile:** `multistart`. **Driver:** `scripts/benchmark/m32-sweep.sh`.

## Question

Is the M32 weight knob `-Dplanner.m32.numerator` (default `100000`, formula
`clamp(numerator / |VRP cells of the course|, min=20, max=500)`) **tunable**, or **flat**
(like the M37 weight sweep turned out: ~106 room-changes across weight 100..1500)?
M32 had never been swept systematically. This is a 3-point smoke sweep to see if there is
any signal above LS variance (~50k medium) before committing to a real tuning campaign.

## Design (single variable)

Same deterministic good seed for all 3 runs; only `m32.numerator` varies:
- GRASP off (default; deterministic construction)
- multi-start OFF (`-Dplanner.multistart.enabled=false` to get a single staged cycle)
- source=input (`-Dplanner.pre-assign.constrained-teacher.source=input`)
- min=20 / max=500 held constant
- 3 values: `100000` (default/baseline), `20000` (low), `300000` (high)

The knob is `static final` in `SchoolSchedule` (read once at class-init), so each value
needs a fresh JVM boot. `solve-staged` re-plans from scratch each boot; the source=input
pins are set up once and persist. So the 3 runs differ ONLY in the M32 weight. Each boot
VERIFIES the knob reached the app via the `M32 template-deviation weight knobs: numerator=V`
banner (the MAVEN_OPTS lesson made into a gate: abort the run if it did not arrive).

## Weight-mechanism note (for interpreting results)

M32 penalizes each unpinned real-teacher lesson sitting outside its course's template cell,
weight = `clamp(numerator / |VRP cells|, 20, 500)`. Approximate per-deviation weight by arm:
- `20000` (low): most courses hit the **floor 20** (20k / hundreds-of-cells < 20 floors).
  M32 ~5x weaker than default. LS is nearly free to drift lessons off-template.
- `100000` (default): ~100-150/deviation for typical courses. Mid-strength medium.
- `300000` (high): many courses hit the **cap 500** (300k / ~600 cells caps). M32 ~3x
  stronger than default. LS resists off-template moves hard.

For reference, M20/M22 (fill/compactness) are 1000/day and M15 ~1200/split, so M32 at its
default (~100-150/deviation) is a MID-strength medium, weaker than the fill family.

## EX-ANTE PREDICTION (written 2026-08-04 11:10 CEST, BEFORE the sweep ran)

Locking the expectation here so it cannot be retro-fit to the numbers.

**Mechanism:** the weekly template is built in construction (Phase 1) at ~76% resemblance
(measured at construction end). LS (Phase 2/5) ERODES it to ~73-75% final. M32 is the
medium that defends the template during LS: it penalizes exactly the off-template drift
that erosion produces. So M32's DIRECT target is **resemblance-unpinned** (template
adherence). Higher M32 means less LS erosion means higher final resemblance.

**Predictions:**
1. **resemblance-unpinned: SENSITIVE, monotonic** (low 20000 < default 100000 < high 300000).
   This is M32's direct target; the weight directly controls how cheaply LS can drift a
   lesson off its template cell. Expected swing ~1-3 percentage points across the range,
   bounded above by the construction ceiling (~76%) and below by max erosion. This is the
   cleanest signal and the one that decides "tunable."
2. **medium score TOTAL: likely FLAT (within ~50k LS variance).** M32's own contribution
   changes, but it trades off against the stronger fill family (M20/M22 at 1000/day): a
   stronger M32 keeps lessons on-template at the cost of fill/compactness, so the mediums
   partially cancel. Net medium near-constant is the likely outcome.
3. **full-day rate: likely FLAT or slightly DOWN at high M32** (less freedom to fill days
   when lessons are pinned to template cells), but probably within noise.
4. **day-start (M18): FLAT.** M32 does not target day-start anchoring; no mechanism for a
   first-order effect.

**Overall ex-ante lean:** M32 is **tunable on its direct target (resemblance)** but the
effect on the headline KPIs (medium, full-day, day-start) is likely **small**, because M32
is one mid-strength medium competing with stronger ones and the construction already
enforces most of the template. Unlike M37 (whose target, room-keeping, was forced by room
AVAILABILITY not weight, hence flat), M32's target IS directly weight-controllable, so I
expect resemblance to move. **If resemblance moves monotonically by more than ~1pp, M32 is
tunable for resemblance-tuning; if even resemblance is flat (under 1pp swing), M32 is
dominated by the construction plus the fill family and is effectively flat (M37 outcome).**

**Hard expectation (the binary the user asked for):** I expect a TUNABLE signal on
resemblance and a FLAT signal on medium/full-day. The decision value: if resemblance is
tunable, M32 is a candidate for future resemblance-targeted tuning; the default 100000 is
not obviously wrong for the headline KPIs regardless.

---

## Results

All 3 runs completed the full 5-phase pipeline (each `phases.json` ends at
"LS - Room Refinement"), `0hard`, on the same source=input pin set (resemblance
denominator constant at 9404 unpinned). Solve wall ~28-33 min each. (The driver
logged `wait TIMEOUT/FAILED` for each run; this is a benign client-side artifact:
`api.sh wait` does `fetch /status || exit 1`, and a fetch during the solve's
pin-release post-processing exits the poller non-zero ~3s AFTER the last phase
already ended. The server-side solve had finished; the capture ran after and got
valid final scores. Not a solve failure.)

Per-run results (numerator ordered low -> default -> high):

| numerator | hard | total medium | M32 medium (matches) | non-M32 medium | resemblance-unpinned | full-day | soft |
|-----------|------|--------------|----------------------|----------------|----------------------|----------|------|
| 20000  (low) | 0 | -946570  | -27170  (834) | -919400 | 74.2% (6978/9404) | 87.3% | 6239 |
| 100000 (def) | 0 | -1039780 | -130593 (841) | -909187 | 74.7% (7024/9404) | 87.3% | 6560 |
| 300000 (hi)  | 0 | -1224954 | -320338 (837) | -904616 | 74.7% (7021/9404) | 87.6% | 5054 |

The dominant non-M32 mediums are stable across the sweep (numerator-100000):
M22 Calendar compactness -563000 (13 days), M20 Daily quota fill -141500,
M-LF -73000, M15 -38400, M37 -29500. None of them move meaningfully with the
numerator.

## Verdict

**M32 numerator is FLAT as a tuning knob (the M37 outcome). NO ADOPTION; the
default 100000 stands. No tuning campaign is warranted.**

The headline `total medium` DOES move (278k spread, well above the ~50k LS
variance), so a naive read would call it "sensitive." That read is wrong. The
movement is **mechanical self-penalty**, not a schedule change. Three independent
signals agree:

1. **M32 `matches` is invariant: 834 / 841 / 837** (<1% swing across a 15x
   numerator range). The actual number of template-deviating lessons the schedule
   contains does not respond to the weight. M32 penalizes the SAME ~838 deviations
   at a different per-deviation rate (low ~33/dev, default ~155/dev, high ~382/dev,
   the extremes flattened by the clamp floor 20 / cap 500).
2. **The non-M32 mediums are flat at ~-910k** (15k spread, within LS variance).
   The total-medium spread (278k) is almost exactly M32's own spread (293k); the
   rest of the score is unchanged.
3. **The schedule-quality KPIs are flat**: resemblance-unpinned 74.2 / 74.7 / 74.7
   (0.5pp), full-day 87.3 / 87.3 / 87.6, hard 0 / 0 / 0.

Moving `planner.m32.numerator` across 20k..300k changes the score NUMBER without
changing the TIMETABLE. That is the definition of a non-tunable knob for our
purpose (we tune to change the schedule, not the score).

**Why flat (mechanism):** the weekly template is set in construction at ~74.7%
unpinned resemblance, and the ~838 off-template deviations are structurally locked:
relocating them back onto their template cell would violate harder constraints
(M22 calendar compactness -563k and M20 daily-quota fill dominate the medium at
~5x and ~1x M32's default contribution respectively, and they do not trade off
meaningfully with M32). So LS has no profitable move that M32's weight could
incentivize. This is the same reason M37 was flat (its target was forced by room
availability, not weight): M32's target is forced by construction + the fill family.

**Ex-ante scorecard:** my prediction was partly wrong and partly right.
- WRONG: I expected resemblance-unpinned to be SENSITIVE (M32's direct target). It
  is FLAT (0.5pp). M32 turned out more dominated than I expected; the deviations
  are not weight-relocatable.
- WRONG on the surface, RIGHT in spirit: I predicted total medium FLAT. The total
  moved 278k, but that is M32 self-penalty; the non-M32 mediums ARE flat, exactly
  as predicted.
- RIGHT: full-day and day-start FLAT.
- RIGHT: the conditional "if resemblance is flat, M32 is dominated (M37 outcome)."
  That is what happened.

**Caveat:** this is a 3-point smoke sweep. But the numerator spans a 15x range
(20k..300k) covering the clamp's floor-to-cap regime, and M32 matches is invariant
across all of it. A real signal would show matches moving; they do not. A finer
sweep would only refine the "flat" conclusion, not overturn it.

**Decision value:** M32 numerator is NOT a useful tuning knob. The default 100000
is as good as any value in 20k..300k. Move on. Next knob candidates
(planner.m34.weight, planner.m35.weight are static-final like M32; M-TC
planner.tag-capacity.weight is LIVE per-build) should be swept the same way before
any is assumed tunable.
