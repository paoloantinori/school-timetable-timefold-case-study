package com.school.planner.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.school.planner.solver.multithreaded.MultiThreadedLocalSearchDecider;

/**
 * Wiring tests for the TASK-480 Fase 3 multi-threaded LS gate in {@link ScheduleService}:
 * the ratified default (DEFAULT ON since 2026-08-14, opt-out with explicit "false"), the
 * thread-count knob (default 8, floored at 1) and the decider's constructor validation.
 * Plain JUnit (no {@code @QuarkusTest}), same form as {@code ScheduleServiceMultiStartTest}:
 * these exercise only the LIVE-read knobs, no solve (the smoke solve lives in {@code
 * MultiThreadedLsSmokeTest}).
 */
class ScheduleServiceMultiThreadedLsTest {

    @AfterEach
    void clearFlags() {
        System.clearProperty("planner.multithreaded-ls.enabled");
        System.clearProperty("planner.multithreaded-ls.threads");
    }

    @Test
    void multiThreadedLsIsOnByDefaultSinceTheRatification() {
        // RATIFIED DEFAULT ON (maintainer, 2026-08-14) on the A/B evidence: 25% wall saving, 0hard
        // both arms. Absent property => enabled, same opt-out form as multi-start/M-TC.
        assertTrue(ScheduleService.multiThreadedLsEnabled());
    }

    @Test
    void onlyAnExplicitFalseOptsOut() {
        // The opt-out escape hatch: any other value (or none) leaves the multi-threaded LS armed.
        System.setProperty("planner.multithreaded-ls.enabled", "false");
        assertFalse(ScheduleService.multiThreadedLsEnabled());
        System.setProperty("planner.multithreaded-ls.enabled", "true");
        assertTrue(ScheduleService.multiThreadedLsEnabled());
    }

    @Test
    void threadsDefaultToTheAbValueOfEight() {
        assertEquals(8, ScheduleService.multiThreadedLsThreads());
    }

    @Test
    void threadsAreFlooredAtOne() {
        // 0 or negative would build a zero-party barrier in the decider: floor, don't crash.
        System.setProperty("planner.multithreaded-ls.threads", "0");
        assertEquals(1, ScheduleService.multiThreadedLsThreads());
        System.setProperty("planner.multithreaded-ls.threads", "-4");
        assertEquals(1, ScheduleService.multiThreadedLsThreads());
    }

    @Test
    void deciderConstructorValidatesCounts() {
        // Fail fast at wiring time rather than mid-phase: a zero-party barrier is a
        // configuration error, not a runtime state to discover later.
        assertThrows(IllegalArgumentException.class,
                () -> new MultiThreadedLocalSearchDecider<>(
                        null, null, null, null, null, 0));
    }
}
