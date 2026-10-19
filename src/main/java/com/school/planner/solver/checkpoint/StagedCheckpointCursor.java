package com.school.planner.solver.checkpoint;

/**
 * TASK-542 stage 1: where in the staged cycle this checkpoint was captured, plus the
 * multi-start context needed to continue the SAME attempt (not restart the wrapper).
 *
 * @param boundary  the capture boundary (never the resume point: that is
 *                  {@code boundary.resumeEntryPoint()})
 * @param iteration 1-based B-TIMESLOT/C-DEMOTION iteration, 0 for linear boundaries
 * @param attempt   multi-start attempt number (1 = deterministic path), 0 outside the wrapper
 * @param graspSeed the GRASP construction seed in force for this attempt (0 = off)
 */
public record StagedCheckpointCursor(
        StagedPhaseBoundary boundary,
        int iteration,
        int attempt,
        long graspSeed) {
}
