package com.school.planner.solver.moves;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ai.timefold.solver.core.api.domain.common.Lookup;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel;
import ai.timefold.solver.core.preview.api.move.Move;
import ai.timefold.solver.core.preview.api.move.MutableSolutionView;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;

/**
 * TASK-576 F8: the pair-block shift move, the ACTIVE half of the pair-form strategy.
 * The E-series (2026-09-01) proved the pair form is not defensible by constraint: the
 * local search erodes it because every single-entity move it makes for its own axes
 * (H17 compaction, M20 fill, M18 anchoring) slides lessons out of their pair's cells,
 * and no penalty weight compensates the sum of those terms. The only remaining lever
 * is making a MOVE that carries the form: this one shifts the pair's whole block on
 * one date by the same slot offset, landing it back on the form's cells.
 *
 * <p>Why it can WIN on the existing axes (maintainer's design constraint, recorded in the
 * task): (1) per-date hour counts of the group are UNCHANGED by construction (H11/H32/H33
 * cannot move: the block moves within the same date); (2) when the slid block borders the
 * channel its slide opened, shifting it back IS the H17 channel closure that LS wants
 * anyway; (3) M38 (ratified default on) refunds {@code k x weight} for the lessons
 * landing back on form cells; (4) M18/day-start may gain when the block returns to the
 * form's anchor. The LS still sees the full delta and rejects the move if the slide was
 * carrying value elsewhere: the market decides, this move only gives the market a
 * coarse-grained option it did not have.</p>
 *
 * <p>Immutable; rebases per the multithreaded decider (each Lesson and Timeslot via
 * {@link Lookup#lookUpWorkingObject}); exposes every Lesson of the block for entity
 * tabu.</p>
 */
public final class PairBlockShiftMove implements Move<SchoolSchedule> {

    private final PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> timeslotVariable;
    private final List<Lesson> block;
    private final List<Timeslot> targets;
    private final String description;
    private final List<Object> entitiesView;
    private final List<Object> valuesView;

    public PairBlockShiftMove(PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> timeslotVariable,
            List<Lesson> block, List<Timeslot> targets, String description) {
        if (block.size() != targets.size() || block.isEmpty()) {
            throw new IllegalArgumentException("block and targets must be non-empty and same size");
        }
        this.timeslotVariable = Objects.requireNonNull(timeslotVariable);
        this.block = List.copyOf(block);
        this.targets = List.copyOf(targets);
        this.description = description;
        // Frozen covariant views, built once: Timefold queries both collections per move
        // (entity tabu runs in this phase), so the getters must not allocate.
        this.entitiesView = List.copyOf(this.block);
        this.valuesView = List.copyOf(this.targets);
    }

    @Override
    public void execute(MutableSolutionView<SchoolSchedule> solutionView) {
        for (int i = 0; i < block.size(); i++) {
            solutionView.changeVariable(timeslotVariable, block.get(i), targets.get(i));
        }
    }

    @Override
    public Move<SchoolSchedule> rebase(Lookup lookup) {
        List<Lesson> rebasedBlock = new ArrayList<>(block.size());
        for (Lesson lesson : block) {
            rebasedBlock.add(lookup.lookUpWorkingObject(lesson));
        }
        List<Timeslot> rebasedTargets = new ArrayList<>(targets.size());
        for (Timeslot target : targets) {
            rebasedTargets.add(lookup.lookUpWorkingObject(target));
        }
        return new PairBlockShiftMove(timeslotVariable, rebasedBlock, rebasedTargets, description);
    }

    @Override
    public List<Object> getPlanningEntities() {
        return entitiesView;
    }

    @Override
    public List<Object> getPlanningValues() {
        return valuesView;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PairBlockShiftMove other && block.equals(other.block) && targets.equals(other.targets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(block, targets);
    }

    @Override
    public String toString() {
        return description + " (" + block.size() + " lessons)";
    }
}
