package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.solver.RetrySignature;
import com.school.planner.solver.RetrySignature.Signature;

/**
 * TASK-503: the classifier and the lever table, pure (no Quarkus). The classifier
 * cases mirror the task480 bad-seed census signatures; the table cases pin the
 * flag-off identity (production byte-identical) and each armed signature's levers.
 */
class RetrySignatureTest {

    @AfterEach
    void cleanFlags() {
        System.clearProperty("planner.multistart.signature-retry.enabled");
        System.clearProperty("planner.multistart.tag-capacity.enabled");
        System.clearProperty("planner.composition-hole-closer.enabled");
        System.clearProperty("planner.multistart.mtc-per-retry.enabled");
    }

    @Test
    void classifierCensusSignatures() {
        // grasp-seed-6: single H29 room-null from tag saturation
        assertEquals(Signature.TAG_SATURATION,
                RetrySignature.classify(Map.of("H29 - Assigned lesson must have a room", -1)));
        // seed-1/9: H17 holes dominate
        assertEquals(Signature.H17_COMPOSITION,
                RetrySignature.classify(Map.of("H17 - Vertically compact", -10)));
        // seed-12 shape: H0 dominant over a minor H17
        assertEquals(Signature.BOXED_H0,
                RetrySignature.classify(Map.of("H0 - Unassigned lesson penalty", -32,
                        "H17 - Vertically compact", -6)));
        // round-2 treatment: mixed H0+H20+H7, nobody owns 50%: UNKNOWN (reseed)
        assertEquals(Signature.UNKNOWN,
                RetrySignature.classify(Map.of("H0 - Unassigned lesson penalty", -28,
                        "H20 - Group calendar closed slot", -21,
                        "H7 - Max teacher daily hours", -15)));
        // exact tie between two families: no owner
        assertEquals(Signature.UNKNOWN,
                RetrySignature.classify(Map.of("H17 - Vertically compact", -8, "H29 - Assigned lesson must have a room", -8)));
        // H0 must not be confused with H0x ids
        assertEquals(Signature.UNKNOWN,
                RetrySignature.classify(Map.of("H05x - not a real id", -3)));
        assertEquals(Signature.UNKNOWN, RetrySignature.classify(Map.of()));
        assertEquals(Signature.UNKNOWN, RetrySignature.classify(null));
        assertEquals(Signature.UNKNOWN,
                RetrySignature.classify(Map.of("H20 - Group calendar closed slot", -21)),
                "families outside the table are never claimed");
    }

    @Test
    void flagOffIsExactlyTodaysRule() {
        // no signature-retry flag: applyLeversForAttempt must reduce to applyMtcForAttempt
        System.setProperty("planner.multistart.mtc-per-retry.enabled", "true");
        ScheduleService.applyLeversForAttempt(1, Signature.TAG_SATURATION);
        assertEquals("false", System.getProperty("planner.tag-capacity.enabled"),
                "attempt 1 deterministic: M-TC off, even with a signature in hand");
        ScheduleService.applyLeversForAttempt(2, Signature.H17_COMPOSITION);
        assertEquals("true", System.getProperty("planner.tag-capacity.enabled"),
                "flag off: the TASK-486 rule ignores the signature");
        assertNull(System.getProperty("planner.composition-hole-closer.enabled"),
                "flag off never touches the CHC lever");
    }

    @Test
    void leverTable() {
        System.setProperty("planner.multistart.signature-retry.enabled", "true");
        System.setProperty("planner.multistart.mtc-per-retry.enabled", "true");

        // attempt 1 (no feedback yet): today's deterministic rule
        ScheduleService.applyLeversForAttempt(1, null);
        assertEquals("false", System.getProperty("planner.tag-capacity.enabled"));
        assertNull(System.getProperty("planner.composition-hole-closer.enabled"));

        // TAG_SATURATION -> M-TC on, CHC untouched
        ScheduleService.applyLeversForAttempt(2, Signature.TAG_SATURATION);
        assertEquals("true", System.getProperty("planner.tag-capacity.enabled"));
        assertNull(System.getProperty("planner.composition-hole-closer.enabled"));

        // H17_COMPOSITION -> CHC on, M-TC off (the failure was not the room tier)
        ScheduleService.applyLeversForAttempt(3, Signature.H17_COMPOSITION);
        assertEquals("true", System.getProperty("planner.composition-hole-closer.enabled"));
        assertNull(System.getProperty("planner.tag-capacity.enabled"));

        // BOXED_H0 -> noop (ExactFinisher is deterministic): both levers cleared
        ScheduleService.applyLeversForAttempt(4, Signature.BOXED_H0);
        assertNull(System.getProperty("planner.tag-capacity.enabled"));
        assertNull(System.getProperty("planner.composition-hole-closer.enabled"));

        // UNKNOWN -> today's reseed rule (M-TC per-retry)
        ScheduleService.applyLeversForAttempt(5, Signature.UNKNOWN);
        assertEquals("true", System.getProperty("planner.tag-capacity.enabled"));
        assertNull(System.getProperty("planner.composition-hole-closer.enabled"));

        // each call starts from a clean slate (no lever leaks from the previous entry)
        ScheduleService.applyLeversForAttempt(6, Signature.BOXED_H0);
        assertNull(System.getProperty("planner.tag-capacity.enabled"));
    }

    @Test
    void flagDefaultsToOff() {
        assertEquals(false, ScheduleService.signatureRetryEnabled(),
                "TASK-503 is opt-in: unset property reads disabled");
    }
}
