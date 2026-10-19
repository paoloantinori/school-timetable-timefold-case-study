package com.school.planner.solver;

import ai.timefold.solver.core.impl.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import ai.timefold.solver.core.impl.heuristic.selector.move.generic.SelectorBasedSwapMove;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;

/**
 * TASK-549 famiglia (b): the QUOTA-PRESERVING swap move filter. Accepts a timeslot swap
 * only when the two lessons belong to the SAME student group and sit on DIFFERENT dates:
 * each of the two days then loses one hour and gains one hour, so every per-day hour
 * count - and therefore H32 (day fill) and H33 (floor) - is INVARIANT BY CONSTRUCTION.
 * The current move set could only move hours BETWEEN days by breaking the day counts
 * first and re-filling later (exactly the shell-game shape TASK-481 documented); this
 * move family makes the trade legal in a single atomic step.
 *
 * <p>Wired into the B-TIMESLOT LS union selector by
 * {@code ScheduleService.applyLsExperimentKnobs} behind the prototype flag
 * {@code planner.ls.quota-preserving-swap.enabled} (DEFAULT OFF, absent from XML).
 */
public class QuotaPreservingSwapFilter implements SelectionFilter<SchoolSchedule, Object> {

    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, Object selection) {
        // Timefold 2.7.0-rc-1 migration: the classic swap selector now produces
        // SelectorBasedSwapMove (the preview builtin SwapMove class was REMOVED in
        // rc-1; same left/right accessors, single type parameter).
        if (!(selection instanceof SelectorBasedSwapMove<?> swap)) {
            return false; // this filter scopes ONE selector: only its own swaps
        }
        Object left = swap.getLeftEntity();
        Object right = swap.getRightEntity();
        if (!(left instanceof Lesson a) || !(right instanceof Lesson b)) {
            return false;
        }
        return quotaPreserving(a, b);
    }

    /**
     * The pure predicate (package-private for the TDD): true iff the two lessons are
     * same-group, both dated, and on DIFFERENT dates - the exact conditions under which
     * swapping their timeslots leaves every per-day hour count (H32/H33) untouched.
     */
    static boolean quotaPreserving(Lesson a, Lesson b) {
        if (a.getStudentGroup() == null || b.getStudentGroup() == null
                || a.getStudentGroup().id == null
                || !a.getStudentGroup().id.equals(b.getStudentGroup().id)) {
            return false; // cross-group swaps change both groups' day counts
        }
        if (a.timeslot == null || b.timeslot == null
                || a.timeslot.getDate() == null || b.timeslot.getDate() == null
                || a.timeslot.getDate().equals(b.timeslot.getDate())) {
            return false; // same-date swaps preserve nothing H32 cares about (and H17
                          // handles same-day compaction better than a swap can)
        }
        return true;
    }
}
