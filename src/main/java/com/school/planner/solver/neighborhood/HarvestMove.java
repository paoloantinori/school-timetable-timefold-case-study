package com.school.planner.solver.neighborhood;

import java.util.List;
import java.util.SequencedCollection;
import java.util.Objects;

import ai.timefold.solver.core.api.domain.common.Lookup;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningSolutionMetaModel;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel;
import ai.timefold.solver.core.preview.api.move.Move;
import ai.timefold.solver.core.preview.api.move.MutableSolutionView;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;

/**
 * TASK-589: one gated harvest move as the LS sees it: a single-lesson timeslot
 * change from an over-quota day to an empty declared cell's first-open landing.
 * The generation is gated (see {@link HarvestNeighborhood}); the ACCEPTANCE is
 * the LS's own score evaluation, which subsumes the pass's per-move oracle
 * (H17/H7/H33 are score-visible there, and the medium the pass never sees is
 * weighed too).
 *
 * <p>{@link #describe()} returns a stable label so the benchmarker's
 * MOVE_COUNT_PER_TYPE / PICKED_MOVE_TYPE_BEST_SCORE_DIFF statistics separate
 * the harvest stream from the generic change pool (the spike's measuring rod).
 */
public final class HarvestMove implements Move<SchoolSchedule> {

    private static final PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> TIMESLOT_VARIABLE =
            PlanningSolutionMetaModel.of(SchoolSchedule.class, Lesson.class)
                    .genuineEntity(Lesson.class)
                    .basicVariable("timeslot", Timeslot.class);

    private final Lesson lesson;
    private final Timeslot landing;

    public HarvestMove(Lesson lesson, Timeslot landing) {
        this.lesson = Objects.requireNonNull(lesson);
        this.landing = Objects.requireNonNull(landing);
    }

    @Override
    public void execute(MutableSolutionView<SchoolSchedule> solutionView) {
        solutionView.changeVariable(TIMESLOT_VARIABLE, lesson, landing);
    }

    @Override
    public HarvestMove rebase(Lookup lookup) {
        return new HarvestMove(lookup.lookUpWorkingObject(lesson), lookup.lookUpWorkingObject(landing));
    }

    @Override
    public SequencedCollection<Object> getPlanningEntities() {
        return List.of(lesson);
    }

    @Override
    public SequencedCollection<Object> getPlanningValues() {
        return List.of(landing);
    }

    @Override
    public String describe() {
        return "HarvestChange";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HarvestMove other
                && Objects.equals(lesson, other.lesson)
                && Objects.equals(landing, other.landing);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lesson, landing);
    }

    @Override
    public String toString() {
        return "HarvestChange(" + lesson + " -> " + landing + ")";
    }
}
