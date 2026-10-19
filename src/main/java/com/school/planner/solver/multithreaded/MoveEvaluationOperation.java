package com.school.planner.solver.multithreaded;

import ai.timefold.solver.core.preview.api.move.Move;

/**
 * Tells a child thread to evaluate a specific move (score calculation) for the given step.
 * The child thread rebases the move to its own score director, checks doability,
 * calculates the score, and puts the result in the result queue.
 */
record MoveEvaluationOperation<Solution_>(
        int stepIndex,
        int moveIndex,
        Move<Solution_> move
) implements MoveThreadOperation<Solution_> {
}
