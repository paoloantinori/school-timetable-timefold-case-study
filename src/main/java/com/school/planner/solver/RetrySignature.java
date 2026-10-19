package com.school.planner.solver;

import java.util.Map;

/**
 * TASK-503: the pure end-of-attempt failure classifier behind the signature-conditioned
 * retry. Input is the attempt's HARD breakdown (canonical constraint id -> negative hard
 * units, e.g. from {@code computeConstraintMatchSnapshot}); output is the failure
 * SIGNATURE the multi-start wrapper consults to arm the next attempt's levers.
 *
 * <p>Rules are the census (task480-results bad-seed frequency, n=19) turned mechanical:
 * a family OWNS the failure when it holds >= {@link #DOMINANCE} of the total hard units;
 * no owner (mixed composition failures like round-2's H0+H20+H7, or ties) reads UNKNOWN,
 * which the lever table maps to today's behavior (blind reseed). With max ~4 feedback
 * points per run the inference must be rules, not statistics.
 *
 * <p>Pure (no state, no side effects), unit-testable without Quarkus, mirroring
 * {@link MultiStartRunner}'s separation. DELIBERATELY knows nothing about WHICH lever
 * answers a signature: that mapping lives in the wrapper's table (single place to extend).
 */
public final class RetrySignature {

    /** A family must own at least this share of the hard units to name the signature. */
    static final double DOMINANCE = 0.50;

    public enum Signature {
        /** Room-tag tier saturation (H29 room-null, the M-TC census signature, e.g. grasp-seed-6). */
        TAG_SATURATION,
        /** H17 composition holes (e.g. G11 Wednesday, G13: the TASK-481/482 family). */
        H17_COMPOSITION,
        /** Boxed H0 residual (ExactFinisher's territory: already deterministic, nothing to arm). */
        BOXED_H0,
        /** No dominant family (or zero hard): today's behavior, blind reseed. */
        UNKNOWN
    }

    private RetrySignature() {
    }

    /**
     * Classify one attempt's hard breakdown. Keys are canonical constraint ids
     * ("H29 - Assigned lesson must have a room", "H17 - ...", "H0 - ..."): matched by
     * id PREFIX so the description text is free to change.
     */
    public static Signature classify(Map<String, Integer> hardByConstraint) {
        if (hardByConstraint == null || hardByConstraint.isEmpty()) {
            return Signature.UNKNOWN;
        }
        long total = 0;
        long tag = 0;
        long h17 = 0;
        long h0 = 0;
        for (Map.Entry<String, Integer> e : hardByConstraint.entrySet()) {
            int units = e.getValue() == null ? 0 : Math.abs(e.getValue());
            if (units == 0) {
                continue;
            }
            total += units;
            String id = e.getKey() == null ? "" : e.getKey();
            if (id.startsWith("H29") || id.startsWith("H-M2")) {
                tag += units;          // the room-TAG tier: saturation reads as room-null
            } else if (id.startsWith("H17")) {
                h17 += units;
            } else if (id.startsWith("H0") && (id.length() == 2 || id.charAt(2) == ' ')) {
                h0 += units;           // "H0 - ..." exactly, not "H0x"
            }
        }
        if (total == 0) {
            return Signature.UNKNOWN;
        }
        if (tag > DOMINANCE * total) {
            return Signature.TAG_SATURATION;
        }
        if (h17 > DOMINANCE * total) {
            return Signature.H17_COMPOSITION;
        }
        if (h0 > DOMINANCE * total) {
            return Signature.BOXED_H0;
        }
        return Signature.UNKNOWN;
    }
}
