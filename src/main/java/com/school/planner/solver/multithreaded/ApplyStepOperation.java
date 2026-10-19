package com.school.planner.solver.multithreaded;

import ai.timefold.solver.core.impl.score.director.InnerScore;
import ai.timefold.solver.core.preview.api.move.Move;

/**
 * Tells a child thread to apply the winning move of a step ("step chasing").
 * The score is carried for logging/diagnostics only.
 */
record ApplyStepOperation<Solution_>(
        int stepIndex,
        Move<Solution_> step,
        InnerScore<?> score
) implements MoveThreadOperation<Solution_> {
}
