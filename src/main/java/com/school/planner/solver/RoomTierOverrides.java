package com.school.planner.solver;

import java.util.LinkedHashMap;
import java.util.Map;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.BendableScore;

/**
 * TASK-E1/E2 (27/08, {@code claudedocs/research_room-tier-distraction-phased-solving_2026-08-27.md}):
 * the room-tier weight regimes for the distraction experiments. The six families whose
 * mass B-ROOM resolves anyway (the research's decomposition: H29 roomless is constant
 * through the timeslot stages, H2-provisorio + M3/M-LF/M37 are the VARIABLE part the
 * timeslot moves shuffle pseudo-randomly), zeroed or damped on the timeslot stages so
 * the LS gradient stops paying noise it cannot fix there.
 *
 * <p>The ITC caution is honored by OMITTION: the roomability proxies M-TC and
 * H-COPRESENCE are NOT in this map and stay at full weight in every arm (the two-stage
 * decomposition winners keep feasibility cuts on rooms in the timeslot phase; PATAT 2022
 * Abou Kasm et al.).
 *
 * <p>Base weights mirror the constraint providers exactly (id strings included - a
 * mismatch fails fast in Timefold's override validation):
 * <ul>
 * <li>H2 - Room conflict: {@code ScoreCompat.ofHard(1000)}</li>
 * <li>H-M2 - Room suitability: {@code ScoreCompat.ONE_HARD}</li>
 * <li>H29 - Assigned lesson must have a room: {@code ScoreCompat.ofHard(1)}</li>
 * <li>M3 - Room capacity: {@code ScoreCompat.ONE_MEDIUM}</li>
 * <li>M-LF - Non-lab lesson in a tagged room: {@code ScoreCompat.ofMedium(1000)} (knob default)</li>
 * <li>M37 - Consecutive same-subject same-teacher should keep the room:
 *     {@code ScoreCompat.ofMedium(100)} (knob default)</li>
 * </ul>
 */
public final class RoomTierOverrides {

    private RoomTierOverrides() {
    }

    /**
     * The knob vocabulary shared by every room-tier-regime call site (the staged cycle's
     * {@code ScheduleService.withRoomTierOverrideIfKnobbed} and the benchmark runner's
     * {@code BenchmarkRoomTierOverride}): {@code planner.room-tier.override} =
     * {@code none|zero|damped}. Returns {@code null} when unarmed (absent/blank/"none"),
     * the regime's overrides when armed; unknown values fail loud with the shared
     * vocabulary (two drifting copies of this parsing was a simplify-review finding).
     */
    public static ConstraintWeightOverrides<BendableScore> fromKnob(String knob) {
        if (knob == null || knob.isBlank() || "none".equalsIgnoreCase(knob.trim())) {
            return null;
        }
        return switch (knob.trim().toLowerCase()) {
            case "zero" -> roomZero();
            case "damped" -> roomDamped();
            default -> throw new IllegalArgumentException(
                    "planner.room-tier.override: unknown value '" + knob + "' (none|zero|damped)");
        };
    }

    /** Arm B: every room family at zero (excluded from processing, Timefold's zero rule). */
    public static ConstraintWeightOverrides<BendableScore> roomZero() {
        Map<String, BendableScore> map = new LinkedHashMap<>();
        for (String id : ids()) {
            map.put(id, ScoreCompat.of(0, 0, 0));
        }
        return ConstraintWeightOverrides.of(map);
    }

    /**
     * Arm C: H2 at exactly 1/100 of its base (1000 -> 10) and M3 at its minimum
     * representable trace (base 1medium: 1/100 is not representable in integer scores,
     * so it stays at 1 - declared in the registro), the other four families as arm B.
     * The pairwise-conflict and capacity signals stay alive as feasibility cuts while
     * the big room masses (H29, M-LF, M37, H-M2) go quiet.
     */
    public static ConstraintWeightOverrides<BendableScore> roomDamped() {
        Map<String, BendableScore> map = new LinkedHashMap<>();
        for (String id : ids()) {
            map.put(id, ScoreCompat.of(0, 0, 0));
        }
        map.put("H2 - Room conflict", ScoreCompat.of(10, 0, 0));
        map.put("M3 - Room capacity", ScoreCompat.of(0, 1, 0));
        return ConstraintWeightOverrides.of(map);
    }

    private static String[] ids() {
        return new String[] {
                "H2 - Room conflict",
                "H-M2 - Room suitability",
                "H29 - Assigned lesson must have a room",
                "M3 - Room capacity",
                "M-LF - Non-lab lesson in a tagged room",
                "M37 - Consecutive same-subject same-teacher should keep the room",
        };
    }
}
