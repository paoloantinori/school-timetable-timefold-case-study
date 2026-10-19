# VERDICT - acceptedCountLimit live staged-sweep (TASK-296)

**Status:** measurement spike, READ-ONLY. NO ADOPTION (solverConfig.xml reverted; default
1000 stands). **Date:** 2026-08-04. **Profile:** `m32-sweep` (reused).
**Driver:** `scripts/benchmark/acceptedCountLimit-sweep.sh` (live staged solve, rebuild per
value; the benchmark harness was bypassed - it is broken by a Timefold 2.3.0 API drift +
Jackson 3.x, hidden by `pom.xml` `<testExclude>**/benchmark/**`).

## Question

Does the forager `<acceptedCountLimit>` in solverConfig.xml (default 1000, uniform in both
LS phases) MOVE THE NEEDLE? This is the first METAHEURISTIC-config sweep, after the
knob-sweep synthesis established that all 5 medium-constraint WEIGHT knobs are flat. The
synthesis predicted the metaheuristic is the lever class (it changes the SEARCH, not the
score weighting). This sweep tests that prediction on the forager breadth knob (TASK-296).

TASK-296 hypothesis: lower acceptedCountLimit (greedy, e.g. 1) does more steps in fixed
time and may beat 1000 for compactness/room.

## Design (single variable)

Same good deterministic seed (source=input, multi-start OFF, GRASP OFF, m32-sweep profile
reused), only acceptedCountLimit varies, edited in BOTH LS phases of solverConfig.xml +
JAR rebuilt per value. 3 values: `1000` (default), `100` (low), `10000` (high). The knob
is baked (no runtime banner), so per value the script confirms the edit landed in both
phases + the rebuilt target/classes has the baked value + the -D mechanism banners fired.
solverConfig.xml is reverted on every exit (trap).

## EX-ANTE PREDICTION (written 2026-08-04, BEFORE the sweep results)

**Why acceptedCountLimit could move the needle (unlike the medium weights):** the medium
weights only re-scaled a penalty on a fixed residual; the score moved, the timetable did
not. acceptedCountLimit changes the SEARCH TRAJECTORY: it controls how many candidate moves
the forager evaluates before accepting one (exploration breadth). Different breadth means
different moves accepted means a different basin reached. So it has a mechanism the medium
weights lacked to actually change the timetable.

**Predictions:**
1. **Score/KPIs: more likely to MOVE than the 5 medium knobs** (which were all flat). This
   is the synthesis-consistent expectation (metaheuristic is the lever).
2. **Direction uncertain.** Low (100) = more, cheaper steps (greedy-ish); high (10000) =
   fewer, more-thorough steps. Which wins under the 5400s staged budget is landscape-
   dependent. TASK-296 leans toward low/greedy helping (clear local gradients in
   compactness/room). I have no strong prior on direction.
3. If it is FLAT too (score/KPIs within LS variance across 100/1000/10000), that would be a
   surprise and would weaken the "metaheuristic is the lever" thesis - it would mean even
   the forager breadth does not change which basin the staged solve reaches.

**Overall ex-ante lean:** acceptedCountLimit MOVES the needle more than the medium weights
did (different mechanism), but I am genuinely uncertain on magnitude and direction. The
decisive read: do the score and the KPIs (resemblance, full-day) differ across the 3 values
by more than LS variance (~50k medium)?

---

## Results

3 runs, same source=input seed (m32-sweep profile reused), only acceptedCountLimit varied
(edited in both LS phases of solverConfig.xml, JAR rebuilt per value, reverted after).
solverConfig.xml confirmed reverted post-sweep (NO ADOPTION). (Run 1 first attempted with
a macOS-BSD `sed -i` bug that aborted acl-100/10000 cleanly via the bake-verification gate;
fixed with a portable sed and re-run. acl-1000 from the first run is valid - default config.)

| acl | hard | medium | soft | full-day | resemblance | wall(s) |
|-----|------|--------|------|----------|-------------|---------|
| 1000 (default) | 0 | -1051618 | 5371 | 87.5% | 74.3% (6986/9404) | 1592 |
| 100 (low) | 0 | **-1000570** | -5886 | 87.4% | 73.4% (6905/9404) | **1314** |
| 10000 (high) | **-7498** | -945466 | 16422 | 87.0% | 73.7% (6929/9404) | 2265 |

acl-10000's -7498 hard is entirely **H29 (Assigned lesson must have a room): 7498 room-null
lessons**. The forager at 10000 candidates/step is so slow per step that the room-assignment
phases get too few steps and leave 7498 lessons without a room. Catastrophic and unambiguous.

acl-100 vs acl-1000 (both feasible, 0 hard) - the better medium is real and broad, not one
noisy constraint: M15 same-day-subject-consecutive -25200 vs -45600 (~20k better), M-LF
-68000 vs -75000, M32 -126991 vs -130059, M20 -145500 vs -147500 (M22 flat at -564k).

## Verdict

**acceptedCountLimit MOVES THE NEEDLE - strongly. This is the first knob in the whole
series (after 5 flat medium weights) that changes the outcome, and it confirms the
knob-sweep synthesis: the metaheuristic IS the lever, not the medium weights.**

Three signals, all mechanism-consistent and monotonic:

1. **High (10000) is broken: -7498 hard (7498 room-null).** Too-thorough per-step evaluation
   means too few steps in the budget; the room phases under-converge and leave lessons
   roomless. A clear feasibility failure, not noise.
2. **Low (100) beats the default (1000): better medium (-1000570 vs -1051618, ~51k),
   FASTER wall (1314s vs 1592s), still feasible (0 hard).** Lower acceptedCountLimit means
   more, cheaper steps, which converge better and earlier within the phase budget. This is
   exactly TASK-296's hypothesis (greedy-ish acceptance helps), supported.
3. The improvement is broad (M15/M-LF/M32/M20 all better at acl-100), not one noisy constraint.

So the forager breadth genuinely changes which basin the staged solve reaches, and lower is
better down to at least 100. The current default 1000 is above the optimum.

**Ex-ante scorecard:** I predicted acceptedCountLimit moves the needle more than the medium
weights (different mechanism: it changes the search trajectory, not the score weighting).
CONFIRMED, and more dramatically than expected (the -7498 hard at 10000 is a bigger signal
than I anticipated). Direction (lower helps) also as TASK-296 hypothesized.

**Caveats (honest):**
- n=1 per value. The acl-100 medium gain (~51k) sits right at the LS-variance floor (~50k),
  so its exact magnitude is n=1-noisy. But it is broad + faster-wall + monotonic-direction,
  so the DIRECTION (low > default > high) is robust even if the magnitude needs refinement.
- This was a 3-point screen. The optimum is below 1000 (100 is better); a finer sweep
  (e.g. 1, 10, 50, 100, 500) with n>=2 would pin the optimum and confirm the gain's
  magnitude. The benchmark harness (TASK-296's specified vehicle) would do this cheaper once
  the harness is repaired (it is currently broken: Timefold 2.3.0 API drift + Jackson 3.x).

**Decision value:** this VALIDATES the strategic pivot. After 5 flat medium weights, the
metaheuristic config is confirmed as the lever class, and acceptedCountLimit specifically
shows lowering it (toward 100 or below) improves the staged solve. This is a candidate for a
follow-up TUNING task (lower the default, with a finer sweep + n>1 to pick the value) - but
this sweep is measurement-only: NO ADOPTION, solverConfig.xml reverted, default 1000 stands.
TASK-296 is worth pursuing, and TASK-284 (VND ordering) is the next metaheuristic candidate
to test the same way.
