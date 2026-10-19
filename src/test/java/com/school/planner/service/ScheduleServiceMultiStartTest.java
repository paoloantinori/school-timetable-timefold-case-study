package com.school.planner.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring tests for the TASK-483 multi-start retry in {@link ScheduleService}: the gate default
 * (DEFAULT ON since the 2026-08-03 ratification, opt-out with explicit "false") and the per-attempt
 * GRASP seed strategy (the load-bearing "attempt 1 is deterministic" decision). Plain JUnit (no {@code
 * @QuarkusTest}): these exercise only the LIVE-read knob and two pure strategy helpers, no solve.
 * The loop logic itself (retry, pick-best, stop-at-0-hard, restore) is covered by {@code
 * MultiStartRunnerTest}.
 */
class ScheduleServiceMultiStartTest {

    @AfterEach
    void clearFlags() {
        // Only this suite touches planner.multistart.* (and the GRASP/M-TC props the delivery test
        // flips); clearing all of them keeps the default assertions honest and test-isolated.
        System.clearProperty("planner.multistart.enabled");
        System.clearProperty("planner.multistart.max-attempts");
        System.clearProperty("planner.multistart.budget-seconds");
        System.clearProperty("planner.multistart.mtc-per-retry.enabled");
        System.clearProperty("planner.multistart.test.force-grasp-seed");
        System.clearProperty("planner.tag-capacity.enabled");
        System.clearProperty("planner.construction.grasp.enabled");
        System.clearProperty("planner.construction.grasp.seed");
    }

    @Test
    void multiStartIsOnByDefaultSinceTheRatification() {
        // RATIFIED DEFAULT ON (maintainer, 2026-08-03) on the n=8 evidence: p(0-hard) = 0.750 per single
        // construction, so one attempt ships residual hard about once in four. Absent property =>
        // enabled, same opt-out form as ExactFinisher.
        assertTrue(ScheduleService.multiStartEnabled());
    }

    @Test
    void onlyAnExplicitFalseOptsOut() {
        // The opt-out escape hatch: any other value (or none) leaves the retry active.
        System.setProperty("planner.multistart.enabled", "false");
        assertFalse(ScheduleService.multiStartEnabled());
        System.setProperty("planner.multistart.enabled", "true");
        assertTrue(ScheduleService.multiStartEnabled());
    }

    @Test
    void attempt1IsDeterministicGraspOff() {
        // The deterministic first attempt is the best-on-medium path production runs today; the
        // randomized attempts are the fallback. Attempt 1 must NOT enable GRASP.
        assertFalse(ScheduleService.graspEnabledForAttempt(1));
    }

    @Test
    void attemptsGe2RandomizeGraspWithSeedEqualToAttempt() {
        // Each retry gets a distinct, reproducible seed (= its attempt number) so N attempts produce
        // N genuinely independent constructions (TASK-480 GRASP diversification contract).
        assertTrue(ScheduleService.graspEnabledForAttempt(2));
        assertTrue(ScheduleService.graspEnabledForAttempt(3));
        assertEquals(2L, ScheduleService.graspSeedForAttempt(2));
        assertEquals(3L, ScheduleService.graspSeedForAttempt(3));
    }

    // ---- TASK-486: M-TC per-retry (ON only on multi-start attempts >= 2) ----
    // RATIFIED DEFAULT ON (maintainer, 2026-08-04): absent property arms the per-retry override (opt-out
    // with explicit "false", same form as multiStartEnabled/isExactFinisherEnabled). Attempt 1 keeps
    // M-TC off (good basin pays no 2x); attempts >=2 turn it on (bad basin gets the deterministic
    // room-tier cure). Evidence: good-seed A/B no-overhead (1558s/0hard vs 1673s/0hard) + bad-seed
    // grasp-seed-6 retry cures to 0-hard (a35633f3).

    @Test
    void mtcPerRetryIsOnByDefaultSinceTheRatification() {
        // RATIFIED DEFAULT ON (maintainer, 2026-08-04): absent property => armed. Good basin pays no 2x
        // (attempt 1 stays M-TC-off), bad basin gets the cure at the retry.
        assertTrue(ScheduleService.mtcPerRetryEnabled());
    }

    @Test
    void mtcPerRetryOnlyAnExplicitFalseOptsOut() {
        // Opt-out escape hatch: any value other than null/true disables. Same opt-out form as the
        // other ratified flags.
        System.setProperty("planner.multistart.mtc-per-retry.enabled", "false");
        assertFalse(ScheduleService.mtcPerRetryEnabled());
        System.setProperty("planner.multistart.mtc-per-retry.enabled", "true");
        assertTrue(ScheduleService.mtcPerRetryEnabled());
    }

    @Test
    void mtcPerRetryAttempt1KeepsMtcOff() {
        // Good basin: attempt 1 is the deterministic best-on-medium path. M-TC must stay OFF here so a
        // good basin stops at attempt 1 with no 2x overhead (byte-identical to M-TC-off).
        assertFalse(ScheduleService.mtcEnabledForAttempt(1));
    }

    @Test
    void mtcPerRetryAttemptsGe2TurnMtcOn() {
        // Bad basin: only the retries (a different GRASP construction that left residual hard) arm the
        // deterministic room-tier cure. The strategy is pure (no side effects), mirroring graspEnabledForAttempt.
        assertTrue(ScheduleService.mtcEnabledForAttempt(2));
        assertTrue(ScheduleService.mtcEnabledForAttempt(3));
    }

    // ---- TASK-486 AC#4: the per-retry DELIVERY (applyMtcForAttempt side-effect) + the A/B hook ----
    // The pure strategy (mtcEnabledForAttempt) is tested above; these exercise the applier that
    // actually flips planner.tag-capacity.enabled per attempt, and the force-grasp-seed hook that
    // makes AC#4 triggerable in-vivo (the wrapper otherwise forces attempt 1 deterministic, so a bad
    // basin never appears at attempt 1 and the retry never fires on a good-basin seed).

    @Test
    void applyMtcForAttemptFlipsTheFlagPerAttemptWhenArmed() {
        // The delivery: armed, attempt 1 forces tag-capacity OFF (good basin), attempt >=2 forces it ON.
        System.setProperty("planner.multistart.mtc-per-retry.enabled", "true");
        ScheduleService.applyMtcForAttempt(1);
        assertEquals("false", System.getProperty("planner.tag-capacity.enabled"));
        ScheduleService.applyMtcForAttempt(2);
        assertEquals("true", System.getProperty("planner.tag-capacity.enabled"));
        ScheduleService.applyMtcForAttempt(3);
        assertEquals("true", System.getProperty("planner.tag-capacity.enabled"));
    }

    @Test
    void applyMtcForAttemptNeverTouchesTheFlagWhenPrototypeOff() {
        // Opt-out path: with the per-retry lever explicitly disabled, the applier is a no-op, so
        // whatever the operator set (or nothing) is left untouched. (The lever is DEFAULT ON since
        // the 2026-08-04 ratification, so this test must opt out explicitly to reach the inert path.)
        System.setProperty("planner.multistart.mtc-per-retry.enabled", "false");
        System.clearProperty("planner.tag-capacity.enabled");
        ScheduleService.applyMtcForAttempt(2); // would arm M-TC if the lever were on
        assertNull(System.getProperty("planner.tag-capacity.enabled"));
        System.setProperty("planner.tag-capacity.enabled", "true"); // operator's explicit value
        ScheduleService.applyMtcForAttempt(2);
        assertEquals("true", System.getProperty("planner.tag-capacity.enabled")); // untouched
    }

    @Test
    void forceGraspSeedHookOverridesAttempt1Determinism() {
        // Without the hook: attempt 1 is deterministic (GRASP off), the per-attempt seed is not set.
        new ScheduleService().applyGraspSeedForAttempt(1, -1L); // mech: TASK-573 arity (honor off = -1)
        assertEquals("false", System.getProperty("planner.construction.grasp.enabled"));
        assertNull(System.getProperty("planner.construction.grasp.seed"));
        // With the hook armed: attempt 1 uses GRASP ON with the FIXED seed (the AC#4 override that puts
        // a bad basin on attempt 1, which a plain -Dgrasp.seed cannot do because the wrapper forces
        // attempt 1 deterministic). The same seed is held at attempt 2, so the construction is constant
        // and M-TC (off at 1, on at 2) is the only variable.
        System.setProperty("planner.multistart.test.force-grasp-seed", "6");
        new ScheduleService().applyGraspSeedForAttempt(1, -1L); // mech: TASK-573 arity (honor off = -1)
        assertEquals("true", System.getProperty("planner.construction.grasp.enabled"));
        assertEquals("6", System.getProperty("planner.construction.grasp.seed"));
        new ScheduleService().applyGraspSeedForAttempt(2, -1L); // mech: TASK-573 arity (honor off = -1)
        assertEquals("true", System.getProperty("planner.construction.grasp.enabled"));
        assertEquals("6", System.getProperty("planner.construction.grasp.seed"));
    }
}
