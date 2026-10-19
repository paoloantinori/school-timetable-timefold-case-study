package com.school.planner.solver.checkpoint;

/**
 * TASK-542 stage 3: which stages of the staged cycle run when resuming from a
 * {@link StagedCheckpointCursor}. Pure: the guards in {@code runStagedCycle} delegate here, so
 * the resume semantics are unit-tested, not embedded in cycle conditionals.
 *
 * <p>Rule: a stage runs iff its completion boundary comes STRICTLY AFTER the cursor's boundary
 * in the cycle's order (the cursor's boundary is already completed - re-running it would
 * double-apply promotion/release semantics). The iterated pair is loop-aware: a cursor inside
 * the B-TIMESLOT loop keeps the loop running (from the next iteration) and everything after it.
 * A null cursor is a plain full cycle.
 */
public record StagedResumePlan(StagedCheckpointCursor cursor) {

    /** Does the stage whose completion boundary is {@code boundary} run on this cycle? */
    public boolean runs(StagedPhaseBoundary boundary) {
        if (cursor == null) {
            return true;
        }
        StagedPhaseBoundary at = cursor.boundary();
        if (boundary == at && !boundary.isIterated()) {
            return false; // already completed by the checkpoint's cycle
        }
        if (at.isIterated()) {
            // cursor inside the B-TIMESLOT loop: construction/promotion are behind it, the
            // loop may still iterate, and every linear stage from POST_RELEASE on runs
            if (boundary.isIterated()) {
                // the loop is re-entered at the NEXT iteration (firstLoopIteration): the
                // iterated stages keep running - the loop's own verdict logic decides when
                // to break, exactly as on a full cycle
                return true;
            }
            return linearIndex(boundary) >= linearIndex(StagedPhaseBoundary.POST_RELEASE);
        }
        if (boundary.isIterated()) {
            // linear cursor: the loop runs only if the cursor has not passed it yet
            return linearIndex(at) <= linearIndex(StagedPhaseBoundary.POST_PROMOTION);
        }
        return linearIndex(boundary) > linearIndex(at);
    }

    /** The iteration number the B-TIMESLOT loop re-enters at (1 on a full cycle). */
    public int firstLoopIteration() {
        if (cursor == null) {
            return 1;
        }
        return switch (cursor.boundary()) {
            case POST_B_TIMESLOT, POST_DEMOTION -> cursor.iteration() + 1;
            default -> 1;
        };
    }

    private static int linearIndex(StagedPhaseBoundary boundary) {
        if (boundary == StagedPhaseBoundary.POST_B_TIMESLOT) {
            return StagedPhaseBoundary.linearOrder().indexOf(StagedPhaseBoundary.POST_PROMOTION) + 1;
        }
        if (boundary == StagedPhaseBoundary.POST_DEMOTION) {
            return StagedPhaseBoundary.linearOrder().indexOf(StagedPhaseBoundary.POST_PROMOTION) + 2;
        }
        return StagedPhaseBoundary.linearOrder().indexOf(boundary);
    }
}
