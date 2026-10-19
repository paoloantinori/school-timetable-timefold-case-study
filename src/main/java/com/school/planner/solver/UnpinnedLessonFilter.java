package com.school.planner.solver;

import ai.timefold.solver.core.impl.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;

/**
 * Filter that excludes pinned lessons from move selectors.
 * This ensures swap and pillar moves never touch pinned entities.
 *
 * While @PlanningPin should theoretically handle this automatically,
 * explicit filtering provides a safety net for swap/pillar selectors.
 *
 * IMPORTANT: This method is called millions of times per second during local search.
 * Do NOT add any logging or I/O operations here — they will cripple solver performance.
 */
public class UnpinnedLessonFilter implements SelectionFilter<SchoolSchedule, Lesson> {

    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, Lesson lesson) {
        return !lesson.pinned;
    }
}
