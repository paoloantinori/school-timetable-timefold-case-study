package com.school.planner.exception;

/**
 * TASK-419: an operation was refused because a STAGED solve is running.
 *
 * <p>Extends {@link IllegalStateException} so every pre-existing caller that already
 * handles "the solver is busy" keeps working unchanged; the distinct type exists only so
 * the REST layer can answer 409 CONFLICT (the busy/refuse convention {@code import-golden}
 * and {@code import-calendario} already use) instead of the 400 it gives an ordinary
 * IllegalStateException - without sniffing an exception message.
 */
public class StagedSolveInProgressException extends IllegalStateException {

    public StagedSolveInProgressException(String message) {
        super(message);
    }
}
