package com.school.planner.solver;

import com.school.planner.service.SolverConfigParser;

/**
 * Utility class for phase naming and solver event handling.
 *
 * - Phase transitions during solving are detected via PhaseTransitionMarker
 *   custom phases in solverConfig.xml
 * - Phase completion on solver termination is handled by saveFinalSolution()
 *   in ScheduleService which calls progressionService.finalizeCurrentPhase()
 */
public class SolverProgressionListener {

    /**
     * Get human-readable phase name with number prefix for UI display.
     * Phase names align with solverConfig.xml phases.
     *
     * <p>Delegates to {@link SolverConfigParser#PHASE_NAMES}, the single source of truth
     * for phase naming, so this and the parser's own {@code getPhaseName} can never drift
     * apart again (they used to be a byte-for-byte duplicated switch/map pair).
     */
    public static String getPhaseName(int phaseNumber) {
        return SolverConfigParser.PHASE_NAMES.getOrDefault(phaseNumber, "Unknown Phase");
    }
}
