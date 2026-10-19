package com.school.planner.solver;

import ai.timefold.solver.core.impl.score.director.ScoreDirector;
import ai.timefold.solver.core.preview.api.move.builtin.CompositeMove;
import ai.timefold.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;

import com.school.planner.domain.SchoolSchedule;

/**
 * Filter that rejects auto-generated CompositeMoves in the Local Search phase.
 *
 * <p>Timefold auto-generates CompositeMove selectors when an entity has multiple
 * planning variables (e.g., Lesson has both timeslot and room). In Phase 3 LS,
 * we only want timeslot-level moves (change, swap, pillar), but the solver
 * also generates CompositeChangeMove(timeslot, room) which wastes ~8% of evaluation
 * budget with zero improvements (confirmed by benchmark data).</p>
 *
 * <p>Benchmark results (2026-03-24):</p>
 * <ul>
 *   <li>CompositeMove: 1.18M evals (8% of total), 0 improvements</li>
 *   <li>All other move types: 6.08-6.31 improvements per 10K evals</li>
 * </ul>
 */
public class CompositeMoveFilter implements SelectionFilter<SchoolSchedule, Object> {

    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, Object selection) {
        // Reject CompositeMove instances - they combine multiple variable changes
        // (e.g., room + timeslot) which we don't want in Phase 3 LS
        return !(selection instanceof CompositeMove);
    }
}
