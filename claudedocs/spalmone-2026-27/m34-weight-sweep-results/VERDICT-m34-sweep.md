# VERDICT - M34 weight sensitivity sweep

**Status:** measurement spike, READ-ONLY. NO ADOPTION (default stays `100`).
**Date:** 2026-08-04. **Profile:** `m32-sweep` (reused, source=input pins). **Driver:**
`scripts/benchmark/medium-knob-sweep.sh` (generic; `m32-sweep.sh` is the committed M32 driver).

## Question

Is `planner.m34.weight` (default `100`, M34 = weekly subject hours ABOVE golden cadence,
`ofMedium(weight)` per `(group, subject, week)` hour beyond the golden's max-weekly cap,
self-gating on the golden cadence reference) **tunable**, or **flat** like M32 turned out?
M32 was flat because its target (template cells) was structurally locked by construction.
M34 targets weekly cadence, which is a softer, more LS-flexible dimension, so the prior is
that M34 has a better chance of being tunable than M32. This smoke sweep tests that.

## Design (single variable)

Same deterministic seed as the M32 sweep; only `m34.weight` varies:
- GRASP off, multi-start OFF (`-Dplanner.multistart.enabled=false`), source=input
- 3 values: `100` (default/baseline), `50` (low), `200` (high)
- fresh JVM per value (M34_WEIGHT is `static final`, read once at class-init)
- profile `m32-sweep` reused via `--no-setup` (its source=input pins persist; solve-staged
  re-plans from scratch each boot)

The knob has no dedicated value banner, so verification is the -D MECHANISM check: the
"Multi-start staged retry: enabled=false" + "grasp=false" banners confirm PLANNER_JAVA_OPTS
rode the java -jar line (so m34.weight, in the same opts, arrived). Post-solve, M34's own
medium differing across values confirms the weight took effect.

## EX-ANTE PREDICTION (written 2026-08-04, BEFORE the sweep ran)

**Mechanism:** M34 penalizes each (group, subject, week) tuple whose hours exceed the
golden's max-weekly for that pair. Higher weight means the solver more strongly avoids
over-cap weeks (spreads hours across weeks); lower weight tolerates more over-cap weeks.

**Why M34 could differ from M32:** M32's target (a lesson's template CELL) is locked by
construction, so LS had no profitable move and M32 was flat (matches invariant at ~838). M34's
target (WEEKLY hour distribution per subject) is a softer dimension LS can actually rearrange
(spread vs concentrate hours across weeks). So M34 has a real degree of freedom M32 lacked.

**Predictions:**
1. **M34 matches (over-cap week-count): POSSIBLY moves** (unlike M32's invariant matches). If
   higher weight reduces over-cap weeks, matches falls at weight 200 and rises at weight 50.
   This is the decisive signal: moving matches = tunable; invariant matches = flat (self-penalty).
2. **cadence KPI (cadence-kpi.py burst/gap): the cleanest tunability signal** if M34 is real.
   Expect lower weight = more burst (over-cap weeks), higher weight = more spread.
3. **medium TOTAL:** if tunable, shifts with the weight; if flat, the movement is self-penalty
   (M34 medium scales, matches invariant, non-M34 mediums flat, as M32 showed).
4. **resemblance / full-day / day-start:** likely FLAT (M34 targets cadence, not template/fill).
   These are the wrong KPIs for M34; cadence-kpi is the right one.

**Overall ex-ante lean:** M34 is **more likely tunable than M32** (cadence is LS-flexible),
but the default 100 may already be near-optimal (the golden cadence is the target, and the
solver already chases it via M32/S13/cadence). Honest uncertainty: could go either way. The
decisive read is M34 matches + cadence-kpi burst/gap, NOT the headline medium (which can fool
via self-penalty, as M32 showed).

---

## Results

All 3 runs completed the full staged cycle (`0hard`), same source=input pin set
(resemblance denominator constant at 9404), reusing the m32-sweep profile via `--no-setup`.
Solve wall ~22-34 min. The driver's `wait` returned non-zero each run (benign fetch hiccup
~3s after the last phase ended, as in the M32 sweep); captures are valid final states.

Per-run (value ordered default / low / high):

| value | hard | total medium | M34 medium (matches) | burst>2.0x | gap-weeks | M32 matches | resemblance | full-day |
|-------|------|--------------|----------------------|------------|-----------|-------------|-------------|----------|
| 100 (default) | 0 | -1052524 | -9600 (61) | 35.7% | 1729 | 835 | 74.5% | 87.2% |
| 50 (low) | 0 | -1059583 | -4800 (59) | 38.1% | 1741 | 837 | 74.7% | 87.5% |
| 200 (high) | 0 | -1062519 | -17200 (48) | 37.3% | 1767 | 847 | 73.9% | 87.5% |

## Verdict

**M34 weight is FLAT as a tuning knob (the M32 outcome). NO ADOPTION; default 100 stands.
No tuning campaign is warranted.**

The binary test the sweep was built for (does total medium move more than the ~50k LS
variance?) answers FLAT: the total-medium spread is ~10k (-1052524 / -1059583 / -1062519),
well inside LS variance.

M34 is "less flat" than M32 in one narrow sense, but it cancels out and is therefore not
tunable for our purpose:

- M34's OWN target (over-cap `(group, subject, week)` tuples) does move, but only at 2x
  weight and modestly: 61 (w100) / 59 (w50) / 48 (w200). The 50-100 range is a flat zone
  (59 vs 61); only 200 bites, cutting over-cap tuples ~21%.
- That reduction does NOT translate into better cadence. The cadence burst distribution is
  noise across the sweep (burst>2.0x 35.7 / 38.1 / 37.3, no monotonic trend; burst ratio
  flat 2.1; gap-weeks if anything rise 1729 -> 1767). So fewer over-cap weeks do not smooth
  the cadence; the reduction is absorbed elsewhere.
- It is a TRADEOFF, not a gain: stronger M34 pushes the solver off the template, so M32
  template deviations RISE (835 -> 837 -> 847) and resemblance DROPS (74.5% -> 73.9% at
  w200). The mediums cancel, leaving total medium flat.

Net: pushing M34 weight harder buys fewer over-cap weeks at the price of template
adherence, with no cadence improvement and no net medium gain. The default 100 is already
in the flat zone (50 and 100 are nearly identical). The knob does not improve the schedule.

**Why flat (mechanism):** M34 caps weekly subject hours at the golden cadence, but the
over-cap residual (~48-61 tuples) is structurally forced. The hours that exceed the cap
cannot be relocated into under-cap weeks without breaking harder constraints (M32 template,
M20/M22 fill, M15 consecutiveness). So M34, like M32, penalizes a residual the search
cannot remove; unlike M32 its residual shrinks slightly under high weight, but only by
trading into M32's residual. The cadence KPI confirms the trade is not worth it.

**Ex-ante scorecard:** I leaned "possibly tunable" (cadence is LS-flexible, unlike M32's
locked template). WRONG. The cadence dimension is flexible in principle, but the over-cap
residual is still forced, and the cost of reducing it (template resemblance) exceeds the
benefit. My secondary predictions held: total medium flat (correct), resemblance/full-day
flat (full-day correct; resemblance slightly DOWN at high weight, the tradeoff side-effect).

**Caveat:** 3-point smoke sweep, but the total medium is flat across a 4x weight range
(50..200) and the cadence KPI shows no trend, only noise. A finer sweep would not overturn
"flat."

**Decision value:** M34 weight is NOT a useful tuning knob. Default 100 stands. Next:
`planner.m35.weight` (the mirror, "spread below golden cadence", default 50), then
`planner.tag-capacity.weight` (M-TC, LIVE per-build, prototype behind a flag).
