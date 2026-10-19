package com.school.planner.solver.multithreaded;

/**
 * Tells a child thread to stop (phase ended). Sent once per child thread at phase end.
 */
record DestroyOperation<Solution_>() implements MoveThreadOperation<Solution_> {
}
