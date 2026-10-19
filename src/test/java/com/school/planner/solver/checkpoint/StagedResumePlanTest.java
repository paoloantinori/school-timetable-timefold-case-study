package com.school.planner.solver.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * TASK-542 stage 3: the resume plan - which stages of the cycle run when resuming from a
 * cursor. Pure decision logic, so the cycle surgery (the guards in runStagedCycle) is driven
 * by a tested rule, not by inline conditionals. A null cursor is a plain full cycle: every
 * stage runs.
 */
class StagedResumePlanTest {

    @Test
    void nullCursorRunsEverything() {
        StagedResumePlan plan = new StagedResumePlan(null);
        for (StagedPhaseBoundary b : StagedPhaseBoundary.values()) {
            assertTrue(plan.runs(b), b + " must run on a full cycle");
        }
        assertEquals(1, plan.firstLoopIteration());
    }

    @Test
    void resumeAtPostReleaseRunsOnlyTheRepairsThatFollowIt() {
        // the -642 bench case: the grid is released, the repair passes and the room stage run
        StagedResumePlan plan = new StagedResumePlan(
                new StagedCheckpointCursor(StagedPhaseBoundary.POST_RELEASE, 0, 1, 0L));
        assertFalse(plan.runs(StagedPhaseBoundary.CYCLE_START));
        assertFalse(plan.runs(StagedPhaseBoundary.POST_CONSTRUCTION));
        assertFalse(plan.runs(StagedPhaseBoundary.POST_PROMOTION));
        assertFalse(plan.runs(StagedPhaseBoundary.POST_B_TIMESLOT));
        assertFalse(plan.runs(StagedPhaseBoundary.POST_DEMOTION));
        assertFalse(plan.runs(StagedPhaseBoundary.POST_RELEASE));
        assertTrue(plan.runs(StagedPhaseBoundary.POST_HOLE_REPAIR));
        assertTrue(plan.runs(StagedPhaseBoundary.POST_SURPLUS_REBALANCE));
        assertTrue(plan.runs(StagedPhaseBoundary.POST_EXACT_FINISHER));
        assertTrue(plan.runs(StagedPhaseBoundary.POST_B_ROOM));
        assertTrue(plan.runs(StagedPhaseBoundary.POST_ROOM_QUOTA_FILL_REPAIR));
    }

    @Test
    void resumeMidLoopContinuesFromTheNextIteration() {
        StagedResumePlan plan = new StagedResumePlan(
                new StagedCheckpointCursor(StagedPhaseBoundary.POST_B_TIMESLOT, 2, 1, 0L));
        assertFalse(plan.runs(StagedPhaseBoundary.POST_CONSTRUCTION));
        assertTrue(plan.runs(StagedPhaseBoundary.POST_B_TIMESLOT));
        assertEquals(3, plan.firstLoopIteration(),
                "resuming after B-TIMESLOT iteration 2 must re-enter the loop at iteration 3");
        assertTrue(plan.runs(StagedPhaseBoundary.POST_RELEASE));
    }

    @Test
    void resumeAtPostDemotionReentersTheLoop() {
        StagedResumePlan plan = new StagedResumePlan(
                new StagedCheckpointCursor(StagedPhaseBoundary.POST_DEMOTION, 1, 1, 0L));
        assertTrue(plan.runs(StagedPhaseBoundary.POST_B_TIMESLOT));
        assertEquals(2, plan.firstLoopIteration());
    }

    @Test
    void resumeAtPostPromotionEntersTheLoopAtIterationOne() {
        StagedResumePlan plan = new StagedResumePlan(
                new StagedCheckpointCursor(StagedPhaseBoundary.POST_PROMOTION, 0, 1, 0L));
        assertTrue(plan.runs(StagedPhaseBoundary.POST_B_TIMESLOT));
        assertEquals(1, plan.firstLoopIteration());
    }

    @Test
    void resumeAtTheLastBoundaryRunsNothingButIsNotAnError() {
        // the driver should refuse it (nothing to do), but the plan itself answers honestly.
        // TASK-557 census fix: the last boundary is derived, not hardcoded - the
        // medium-pure arm (POST_MEDIUM_PURE_ARM) closes the cycle since 26/08.
        StagedResumePlan plan = new StagedResumePlan(
                new StagedCheckpointCursor(StagedPhaseBoundary.linearOrder().getLast(), 0, 1, 0L));
        for (StagedPhaseBoundary b : StagedPhaseBoundary.values()) {
            assertFalse(plan.runs(b));
        }
    }
}
