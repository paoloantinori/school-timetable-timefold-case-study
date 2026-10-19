package com.school.planner.solver.multithreaded;

/**
 * Base type for operations sent from the parent (solver) thread to child (move) threads
 * via the operation queue. Sealed pattern: the 4 implementations are {@link SetupOperation},
 * {@link MoveEvaluationOperation}, {@link ApplyStepOperation}, {@link DestroyOperation}.
 */
sealed interface MoveThreadOperation<Solution_>
        permits SetupOperation, MoveEvaluationOperation, ApplyStepOperation, DestroyOperation {
}
