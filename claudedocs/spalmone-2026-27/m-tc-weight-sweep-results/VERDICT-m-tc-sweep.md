# VERDICT - M-TC (tag-capacity) weight sensitivity sweep

**Status:** measurement spike, READ-ONLY. NO ADOPTION (default stays `1000`, flag stays OFF).
**Date:** 2026-08-04. **Profile:** `m32-sweep` (reused). **Driver:**
`scripts/benchmark/medium-knob-sweep.sh` with `EXTRA_FLAGS="-Dplanner.tag-capacity.enabled=true"`.

## Question

Is `planner.tag-capacity.weight` (default `1000`, M-TC = lab tag capacity, scarcity-aware,
`ofMedium(weight)` per excess lesson in a saturated `(tag, startDateTime)` cell) **tunable**
or **flat**? M-TC is the 4th and last knob. The prior three (M32 template, M34 over-cadence,
M35 under-cadence) all came back FLAT. M-TC is different: it is a CAPACITY medium whose
residual lives in the B-ROOM phase (room assignment), not the timeslot/construction phase
where the cadence/template residuals were structurally locked. So M-TC is the most likely of
the four to actually be tunable. This sweep tests that.

NOTE: M-TC is a PROTOTYPE behind `planner.tag-capacity.enabled` (default OFF) and has ~2x
wall cost when enabled flat. This sweep enables it (`=true`) purely to measure its weight
sensitivity. NO ADOPTION: the flag stays default OFF and the weight stays 1000 regardless.

## Design (single variable)

Same seed as before; only `tag-capacity.weight` varies (with the flag ON in every run):
GRASP off, multi-start OFF, source=input, profile m32-sweep reused `--no-setup`, fresh JVM
per value. 2-point SCREEN (not 3) because of the 2x wall: `1000` (default) vs `2000` (2x
high). If the screen shows signal (M-TC matches or KPIs move), a follow-up adds the low
point; if flat, 2 points suffice (no movement at 2x = flat). Mechanism-verified each run;
post-solve M-TC present in analysis.json confirms the flag took effect.

## EX-ANTE PREDICTION (written 2026-08-04, BEFORE the sweep ran)

**Why M-TC could differ from the 3 flats:** M32/M34/M35 residuals were in the timeslot /
construction phase and were structurally forced (the golden cadence/template is set by
construction and locked by the fill family M22/M20). M-TC's residual (tag over-capacity per
`(tag, startDateTime)` cell) is a ROOM-assignment problem addressed in B-ROOM (Phase 4-5),
which has real freedom (rooms can be reassigned). So M-TC's residual might actually be
movable, unlike the cadence/template ones.

**Predictions:**
1. **M-TC matches (excess lessons in saturated cells): POSSIBLY moves** at 2x weight, unlike
   the invariant matches of M32/M34/M35. This is the decisive signal.
2. If tunable, higher weight should reduce tag over-capacity (rooms redistributed), visible
   as fewer M-TC matches AND possibly fewer H29 room-null / better room-tier outcomes.
3. BUT M-TC may also be flat if tag saturation is absolute (the spec notes only ~1 tag per
   cell in DataGenerator.ROOM_TAG_MAP for some tags, so capacity may be genuinely binding
   with no room to redistribute). If saturation is absolute, M-TC is flat (residual forced),
   same outcome as the others.
4. **medium TOTAL:** if tunable, shifts; if flat, self-penalty (M-TC medium scales, matches
   invariant, non-M-TC mediums flat).
5. 2x wall cost is expected per run (~50-65 min each).

**Overall ex-ante lean:** genuinely uncertain, but M-TC is the BEST candidate of the four
for being tunable (different phase). Could go either way. Decisive read: M-TC matches + any
room-tier KPI movement.

---

## Results

Both runs completed the staged cycle (`0hard`), profile m32-sweep reused `--no-setup`.
Wall 2648s (w1000) / 1512s (w2000). The boot log confirms M-TC FIRED in both
(`M-TC lab tag-capacity: enabled=true, rooms-per-tag={acconciatura=1, termoidraulica=1,
saldatura=1, moda=1, informatica=2, meccanica=1}`), so the enable flag reached the app
and the constraint was active.

| value | hard | medium | soft | full-day | resemblance | M-TC in analysis |
|-------|------|--------|------|----------|-------------|------------------|
| 1000 (default) | 0 | -1019476 | 9151 | 87.6% | 74.5% | ABSENT (0 score) |
| 2000 (high) | 0 | -1049993 | 4224 | 87.3% | 74.1% | ABSENT (0 score) |

M-TC is absent from both runs' `analysis.json`. The analysis endpoint excludes zero-score
constraints, so absent == **0 violations in the final schedule** at both weights. The
~30k medium difference and the 44-vs-25-min wall difference are LS time-based-termination
variance, not an M-TC effect (M-TC contributes 0 to both finals).

## Verdict

**M-TC weight is FLAT on this (good) seed. NO ADOPTION; the flag stays default OFF and the
weight stays 1000. No tuning campaign on this seed.**

But the mechanism is DIFFERENT from M32/M34/M35, and the finding is seed-qualified:

- M32/M34/M35 were flat via a FORCED residual: non-zero invariant matches the search could
  not remove. M-TC is flat via SATISFACTION: the solver's final has **0 tag-over-capacity
  violations at both weight 1000 and 2000**. With 0 violations, the weight is irrelevant
  (0 x anything = 0). Any meaningful positive penalty was enough for the solver to avoid
  scheduling two same-tag labs in the same `(tag, startDateTime)` cell beyond the 1-2 rooms
  per tag.
- So on this good source=input seed, M-TC is NON-BINDING. The schedule already avoids tag
  over-capacity (helped by H2 room-conflict + the B-ROOM phase), so M-TC has nothing to
  penalize. Tuning its weight cannot change a schedule that already has 0 violations.

**Caveat (important):** this is seed-specific. CLAUDE.md records M-TC has ~44 violations on
the GOLDEN (the school accepts them), and the spec's scarcity detector was built for
saturated basins (e.g. the H17 bad-seed regime, the meccanica tag with only 1 room). On a
SATURATED seed where M-TC IS binding (non-zero violations the search cannot fully remove),
the weight could matter. This good seed simply is not such a basin. So "M-TC weight is flat"
means "flat where M-TC is non-binding"; it does NOT rule out M-TC weight mattering on a
saturated basin. Testing that would need a saturated-seed sweep (a different, harder setup).

**Ex-ante scorecard:** I called M-TC the best tunability candidate (room phase has freedom,
unlike the locked timeslot residuals). The room phase DID have the freedom to reach 0
violations, which is exactly why the weight is now irrelevant. So the "different phase"
intuition was right about movability, but movability went all the way to 0, leaving nothing
for the weight to tune. Net: flat on this seed, as the others, but for the opposite reason.

**Meta-finding (M32 / M34 / M35 / M-TC, all FLAT):** medium-constraint weight knobs are not
useful tuning levers on the good source=input seed. Three via a forced residual (cadence/
template locked by construction + the fill family), one via satisfaction (capacity already
avoided). The real schedule-quality levers are the metaheuristic config, the construction,
and the hard-constraint repair passes, which the campaign has already tuned. The one open
thread is M-TC on a SATURATED seed, which this sweep does not cover.

**Decision value:** M-TC weight is NOT a useful tuning knob on the good seed (non-binding).
Default 1000 / flag OFF stand. The only medium-knob scenario worth a future look is M-TC on
a deliberately saturated basin, which is a separate experiment.
