package com.school.planner.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import com.school.planner.exception.PreAssignmentInfeasibleException;
import com.school.planner.service.PreAssignmentFeasibilityReport;

/**
 * Maps PreAssignmentInfeasibleException to HTTP 400 Bad Request
 * with a structured JSON response containing the feasibility report.
 */
@Provider
public class PreAssignmentExceptionMapper implements ExceptionMapper<PreAssignmentInfeasibleException> {

    @Override
    public Response toResponse(PreAssignmentInfeasibleException exception) {
        PreAssignmentFeasibilityReport report = exception.getReport();

        ErrorResponse error = new ErrorResponse(
            "PRE_ASSIGNMENT_INFEASIBLE",
            exception.getMessage(),
            report
        );

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(error)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /** Structured error response for the API. */
    public static class ErrorResponse {
        public String code;
        public String message;
        public PreAssignmentFeasibilityReport report;

        public ErrorResponse(String code, String message, PreAssignmentFeasibilityReport report) {
            this.code = code;
            this.message = message;
            this.report = report;
        }
    }
}
