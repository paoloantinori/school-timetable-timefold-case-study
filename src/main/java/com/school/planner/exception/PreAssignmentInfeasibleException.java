package com.school.planner.exception;

import com.school.planner.service.PreAssignmentFeasibilityReport;

/**
 * Exception thrown when pre-assignment cannot complete because the seed data
 * is infeasible (e.g., required blocks > available slots).
 *
 * This is a deterministic failure - the administrator must fix the seed data
 * before pre-assignment can succeed.
 */
public class PreAssignmentInfeasibleException extends RuntimeException {

    private final PreAssignmentFeasibilityReport report;

    public PreAssignmentInfeasibleException(String message, PreAssignmentFeasibilityReport report) {
        super(message);
        this.report = report;
    }

    public PreAssignmentInfeasibleException(String message, PreAssignmentFeasibilityReport report, Throwable cause) {
        super(message, cause);
        this.report = report;
    }

    public PreAssignmentFeasibilityReport getReport() {
        return report;
    }
}
