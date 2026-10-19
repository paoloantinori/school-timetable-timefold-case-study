package com.school.planner.solver.multithreaded;

import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;

/**
 * Tells a child thread to create its own isolated score director (via
 * {@code createChildThreadScoreDirector(MOVE_THREAD)}) from the parent's.
 * Sent once at phase start, before any moves.
 */
record SetupOperation<Solution_>(InnerScoreDirector<Solution_, ?> parentScoreDirector)
        implements MoveThreadOperation<Solution_> {
}
