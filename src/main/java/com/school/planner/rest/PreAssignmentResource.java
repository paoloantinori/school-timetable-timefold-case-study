package com.school.planner.rest;

import java.util.List;

import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.school.planner.service.FailureAnalysis;
import com.school.planner.service.HybridPhase1aService;
import com.school.planner.service.HybridPhase1aReport;

/**
 * Pre-Assignment REST API.
 *
 * <p>Uses the hybrid CSP-based pre-assignment strategy which iterates through
 * rotation candidates, tests each for 0hard violations, and pins successful ones.
 * This mimics human planner behavior and completes in seconds.</p>
 *
 * <p>Phases: acconciatura → labs → professionalizzanti</p>
 */
@Path("/api/schedule/pre-assign")
@Produces(MediaType.APPLICATION_JSON)
public class PreAssignmentResource {

    private static final Logger LOG = Logger.getLogger(PreAssignmentResource.class);

    @Inject
    HybridPhase1aService hybridPhase1aService;

    /**
     * Run hybrid pre-assignment (generate → test → pin).
     *
     * <p>This is an <strong>asynchronous</strong> operation that mimics human planner behavior:
     * iterate rotation candidates, test each for 0hard, abort on failure,
     * retry with next candidate.</p>
     *
     * <p>Phases run sequentially: acconciatura → labs → professionalizzanti</p>
     *
     * @return 202 Accepted immediately; check status via GET /pre-assign/status
     *         409 Conflict if already running
     */
    @POST
    public Response runPreAssignment() {
        if (hybridPhase1aService.isRunning()) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new HybridStatusResponse(
                    "Pre-assignment already running",
                    hybridPhase1aService.getStatus().name(),
                    hybridPhase1aService.getCurrentPhase(),
                    hybridPhase1aService.getCurrentCandidatesTried(),
                    hybridPhase1aService.getElapsedSeconds(),
                    hybridPhase1aService.getLastError(),
                    null  // No failure analysis for "already running"
                ))
                .build();
        }

        // Reset to clear previous pre-assignment results before starting fresh
        hybridPhase1aService.resetStatus();

        // Start async execution
        hybridPhase1aService.runAsync();

        return Response.status(Response.Status.ACCEPTED)
            .entity(new HybridStatusResponse(
                "Pre-assignment started",
                hybridPhase1aService.getStatus().name(),
                hybridPhase1aService.getCurrentPhase(),
                hybridPhase1aService.getCurrentCandidatesTried(),
                hybridPhase1aService.getElapsedSeconds(),
                null
            ))
            .build();
    }

    /**
     * Get pre-assignment status.
     *
     * @return Current hybrid execution status with optional failure analysis
     */
    @GET
    @Path("/status")
    public HybridStatusResponse getStatus() {
        LOG.info("[PreAssignmentResource] getStatus() called - thread=" + Thread.currentThread().getName());
        long start = System.currentTimeMillis();

        try {
            // Fast path: read volatile fields directly
            HybridPhase1aService.HybridStatus status = hybridPhase1aService.getStatus();
            LOG.infov("[PreAssignmentResource] status={0}, phase={1}, elapsed={2}ms",
                status, hybridPhase1aService.getCurrentPhase(),
                System.currentTimeMillis() - start);

            FailureAnalysisDto failureAnalysisDto = null;

            // Include failure analysis only when status is ERROR
            if (status == HybridPhase1aService.HybridStatus.ERROR) {
                FailureAnalysis analysis = HybridPhase1aService.getLastFailureAnalysis();
                if (analysis != null) {
                    failureAnalysisDto = new FailureAnalysisDto(
                        analysis.phase,
                        analysis.reason,
                        analysis.conflictingTeacherNames,
                        analysis.conflictingGroupNames,
                        analysis.periodRange,
                        analysis.suggestion,
                        analysis.conflictPatterns.stream()
                            .map(p -> new ConflictPatternDto(p.type, p.description, p.occurrenceCount, p.examples))
                            .toList()
                    );
                }
            }

            String currentPhase = hybridPhase1aService.getCurrentPhase();
            String message;
            if (status == HybridPhase1aService.HybridStatus.COMPLETE) {
                // completionNote carries an informative message for special-case
                // completions (e.g. the zero-rotation no-op); it's null for a normal
                // successful run, unlike currentPhase which only ever holds phase
                // codes ("1-ACCONCIATURA") or null.
                String completionNote = hybridPhase1aService.getCompletionNote();
                message = (completionNote != null && !completionNote.isBlank())
                    ? completionNote : "Pre-assignment completed";
            } else {
                message = status.name();
            }

            HybridStatusResponse response = new HybridStatusResponse(
                message,
                status.name(),
                currentPhase,
                hybridPhase1aService.getCurrentCandidatesTried(),
                hybridPhase1aService.getElapsedSeconds(),
                hybridPhase1aService.getLastError(),
                failureAnalysisDto
            );

            LOG.infov("[PreAssignmentResource] getStatus() completed in {0}ms", System.currentTimeMillis() - start);
            return response;
        } catch (Exception e) {
            LOG.errorv("[PreAssignmentResource] getStatus() failed after {0}ms: {1}",
                System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    /**
     * Reset pre-assignment status to IDLE.
     *
     * @return 204 No Content
     */
    @POST
    @Path("/reset")
    public Response resetStatus() {
        hybridPhase1aService.resetStatus();
        return Response.noContent().build();
    }

    /**
     * Get pre-assignment phase history.
     *
     * <p>Returns the phase results from the last completed pre-assignment run.
     * Includes phase names, durations, entities pinned, and success status.</p>
     *
     * @return Phase history from last completed pre-assignment, or 404 if none exists
     */
    @GET
    @Path("/phases")
    public Response getPhaseHistory() {
        com.school.planner.service.HybridPhase1aReport report =
            hybridPhase1aService.getLastCompletedReport();

        if (report == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new PhaseHistoryResponse("No pre-assignment has been completed yet", null))
                .build();
        }

        return Response.ok(new PhaseHistoryResponse("Pre-assignment phase history", report)).build();
    }

    // ─── Response classes ─────────────────────────────────────────────

    /**
     * Hybrid status response.
     */
    public static class HybridStatusResponse {
        public String message;
        public String status;
        public String currentPhase;
        public int candidatesTried;
        public long elapsedSeconds;
        public String error;
        public FailureAnalysisDto failureAnalysis;

        /**
         * Full constructor with all fields.
         */
        public HybridStatusResponse(String message, String status, String currentPhase,
                                     int candidatesTried, long elapsedSeconds, String error,
                                     FailureAnalysisDto failureAnalysis) {
            this.message = message;
            this.status = status;
            this.currentPhase = currentPhase;
            this.candidatesTried = candidatesTried;
            this.elapsedSeconds = elapsedSeconds;
            this.error = error;
            this.failureAnalysis = failureAnalysis;
        }

        /**
         * Constructor without failure analysis (for running states).
         */
        public HybridStatusResponse(String message, String status, String currentPhase,
                                     int candidatesTried, long elapsedSeconds, String error) {
            this(message, status, currentPhase, candidatesTried, elapsedSeconds, error, null);
        }

        /**
         * Backward-compatible constructor (no elapsed seconds, no failure analysis).
         * @deprecated Use constructor with elapsedSeconds instead.
         */
        @Deprecated
        public HybridStatusResponse(String message, String status, String currentPhase,
                                     int candidatesTried, String error) {
            this(message, status, currentPhase, candidatesTried, 0, error, null);
        }
    }

    /**
     * Detailed failure analysis for error responses.
     */
    public static class FailureAnalysisDto {
        public String phase;
        public String reason;
        public List<String> conflictingTeacherNames;
        public List<String> conflictingGroupNames;
        public String periodRange;
        public String suggestion;
        public List<ConflictPatternDto> conflictPatterns;

        public FailureAnalysisDto(String phase, String reason,
                                   List<String> conflictingTeacherNames,
                                   List<String> conflictingGroupNames,
                                   String periodRange,
                                   String suggestion,
                                   List<ConflictPatternDto> conflictPatterns) {
            this.phase = phase;
            this.reason = reason;
            this.conflictingTeacherNames = conflictingTeacherNames;
            this.conflictingGroupNames = conflictingGroupNames;
            this.periodRange = periodRange;
            this.suggestion = suggestion;
            this.conflictPatterns = conflictPatterns;
        }
    }

    /**
     * A conflict pattern that contributed to the failure.
     */
    public static class ConflictPatternDto {
        public String type;
        public String description;
        public int occurrenceCount;
        public List<String> examples;

        public ConflictPatternDto(String type, String description,
                                   int occurrenceCount, List<String> examples) {
            this.type = type;
            this.description = description;
            this.occurrenceCount = occurrenceCount;
            this.examples = examples;
        }
    }

    /**
     * Phase history response.
     */
    public static class PhaseHistoryResponse {
        public String message;
        public HybridPhase1aReport report;

        public PhaseHistoryResponse(String message, HybridPhase1aReport report) {
            this.message = message;
            this.report = report;
        }
    }
}
