# VERDICT - M35 weight sensitivity sweep

**Status:** measurement spike, READ-ONLY. NO ADOPTION (default stays `50`).
**Date:** 2026-08-04. **Profile:** `m32-sweep` (reused). **Driver:**
`scripts/benchmark/medium-knob-sweep.sh` (generic).

## Question

Is `planner.m35.weight` (default `50`, M35 = weekly subject spread BELOW golden cadence,
the mirror of M34, self-gating) **tunable** or **flat**? M34 (above-cadence) just came out
flat (over-cap residual forced; reducing it traded template adherence with no cadence gain).
M35 targets the other half of cadence (under-spread: a subject too thin / too many
gap-weeks vs the golden), so the prior is now FLAT, but it is a different dimension and
worth measuring.

## Design (single variable)

Same as the M32/M34 sweeps; only `m35.weight` varies: GRASP off, multi-start OFF,
source=input, 3 values `50` (default) / `25` (low) / `100` (high) (1x / 0.5x / 2x, matching
the M34 ratio), fresh JVM per value, profile `m32-sweep` reused via `--no-setup`. Mechanism
verification (Multi-start/grasp banners) since M35 has no value banner.

## EX-ANTE PREDICTION (written 2026-08-04, BEFORE the sweep ran)

**Updated prior after M34:** M34 was flat with a self-canceling tradeoff (over-cap tuples
moved only at 2x weight, cadence did not improve, template resemblance worsened). M35 is the
mirror dimension (spread, not concentration), but it is still a cadence medium, and the M34
result suggests the cadence residuals are structurally forced (the golden cadence is the
target, and the solver already chases it via M32/S13/M34). So I now lean FLAT.

**Predictions:**
1. **M35 matches (under-spread pairs): likely ~invariant or weakly moving** (analogous to
   M34's 61/59/48). If it moves, check whether the cadence gap-weeks actually improve (M35's
   direct target) or whether it is absorbed like M34's reduction was.
2. **cadence gap-weeks / burst:** if M35 is tunable, higher weight should REDUCE gap-weeks
  (spread the subject across more weeks). If gap-weeks are ~flat/noisy across 25/50/100, M35
  is flat (the spread residual is forced), same conclusion as M34.
3. **medium TOTAL:** expected FLAT within ~50k variance (M35 trades off against M32 template
   + M34 + fill, as M34 did).
4. **resemblance / full-day / day-start:** likely FLAT; resemblance possibly DOWN at high M35
   weight (same tradeoff side-effect M34 showed).

**Overall ex-ante lean:** FLAT (the M34-updated prior). Decisive signal: M35 matches +
cadence gap-weeks. If gap-weeks do not fall monotonically with the weight, M35 is flat and
the default 50 stands.

---

## Results

All 3 runs completed the full staged cycle (`0hard`), same source=input pin set
(resemblance denominator constant 9404), profile m32-sweep reused via `--no-setup`.
Solve wall ~23-31 min. Benign `wait` non-zero each run (fetch hiccup near completion, as
before); captures valid.

Per-run (value ordered default / low / high):

| value | hard | total medium | M35 medium (matches) | burst>2.0x | gap-weeks | M34 matches | resemblance | full-day |
|-------|------|--------------|----------------------|------------|-----------|-------------|-------------|----------|
| 50 (default) | 0 | -1047542 | -22250 (90) | 36.9% | 1742 | 51 | 74.3% | 87.6% |
| 25 (low) | 0 | -1038704 | -11100 (83) | 35.2% | 1803 | 47 | 74.1% | 87.5% |
| 100 (high) | 0 | -1070241 | -44900 (88) | 38.1% | 1751 | 55 | 74.3% | 87.4% |

## Verdict

**M35 weight is FLAT as a tuning knob (the M32 outcome, and the cleanest of the three
sweeps). NO ADOPTION; default 50 stands. No tuning campaign is warranted.**

The decisive signals all read FLAT:

1. **M35 matches invariant: 90 / 83 / 88** (no monotonic trend; ~8% range). M35's direct
   target (under-spread `(group, subject)` pairs) does not respond to the weight. This is
   flatter than M34 (which at least moved 61 -> 48 at 2x).
2. **cadence gap-weeks flat: 1742 / 1803 / 1751** (no trend; M35's target metric). Higher
   weight does NOT reduce gap-weeks (spread the subject across more weeks). burst>2.0x also
   flat/noisy (36.9 / 35.2 / 38.1).
3. **resemblance flat: 74.3 / 74.1 / 74.3** (0.2pp). Unlike M34, there is not even a
   tradeoff side-effect at high weight, so M35 is the most inert of the three.
4. The total-medium spread (31k) is within LS variance (~50k), and it is M35 SELF-PENALTY:
   M35's own medium scales exactly 2x per weight step (-11100 / -22250 / -44900), while the
   non-M35 mediums are flat at ~-1026k (spread ~2k).

Net: moving `planner.m35.weight` across 25..100 changes the score NUMBER (M35 self-penalty)
without changing the TIMETABLE (matches/gap-weeks/resemblance/full-day all flat). Same
conclusion as M32 and M34.

**Ex-ante scorecard:** I leaned FLAT (the M34-updated prior). CONFIRMED, and cleaner than
expected (no tradeoff at all, unlike M34).

**Meta-finding across M32 / M34 / M35:** all three medium-constraint weight knobs are FLAT.
Their target residuals (template cells, over-cap weeks, under-spread pairs) are structurally
forced; the solver already chases the golden cadence/template via construction plus the fill
family (M22 -563k, M20 -141k dominate), which do not trade off meaningfully with these
mediums. The medium score moves with the weight (self-penalty scaling), but the timetable
does not improve. Conclusion: medium-constraint WEIGHTS are not useful tuning levers in this
model. The real schedule-quality levers are elsewhere (metaheuristic config, construction,
hard-constraint repair), which the campaign has already tuned (TASK-419/424/425/...).

**Decision value:** M35 weight is NOT a useful tuning knob. Default 50 stands. Last knob in
the queue: `planner.tag-capacity.weight` (M-TC) - but note M-TC is a PROTOTYPE behind
`planner.tag-capacity.enabled` (default OFF) and LIVE per-build, so its weight only matters
when the flag is ON.
