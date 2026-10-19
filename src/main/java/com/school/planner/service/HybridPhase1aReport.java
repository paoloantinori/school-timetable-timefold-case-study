package com.school.planner.service;

import java.time.Duration;
import java.util.List;

/**
 * Report from running the hybrid Phase 1a pre-assignment.
 *
 * <p>This report captures the results of the generate-test-abort-retry loop
 * that mimics human planner behavior for lab block pre-assignment.</p>
 *
 * <p>Pattern: Follows {@link PreAssignmentFeasibilityReport} - regular class with public static nested classes.</p>
 */
public class HybridPhase1aReport {

    /** Whether the hybrid approach found a 0hard solution */
    public final boolean success;

    /** The phase that completed or failed: "COMPLETE" or the failed phase name */
    public final String phase;

    /** Total time spent in the hybrid loop */
    public final Duration totalDuration;

    /** Total number of rotation candidates evaluated */
    public final int totalCandidatesTried;

    /** The winning rotation candidate (null if failed) */
    public final RotationCandidate winningCandidate;

    /** Detailed failure analysis (null if success) */
    public final FailureAnalysis failureAnalysis;

    /** Results from each phase of the pipeline */
    public final List<PhaseResult> phaseResults;

    /**
     * Construct a new hybrid Phase 1a report.
     *
     * @param success whether a 0hard solution was found
     * @param phase the current phase ("COMPLETE" or failed phase name)
     * @param totalDuration total time spent
     * @param totalCandidatesTried number of rotation candidates evaluated
     * @param winningCandidate the winning rotation (null if failed)
     * @param failureAnalysis failure details (null if success)
     * @param phaseResults per-phase results
     */
    public HybridPhase1aReport(boolean success, String phase, Duration totalDuration,
                               int totalCandidatesTried, RotationCandidate winningCandidate,
                               FailureAnalysis failureAnalysis, List<PhaseResult> phaseResults) {
        this.success = success;
        this.phase = phase;
        this.totalDuration = totalDuration;
        this.totalCandidatesTried = totalCandidatesTried;
        this.winningCandidate = winningCandidate;
        this.failureAnalysis = failureAnalysis;
        this.phaseResults = phaseResults == null ? List.of() : List.copyOf(phaseResults);
    }

    /**
     * Result of a single phase (1=Acconciatura, 2=Labs, 3=Professionalizzanti).
     */
    public static class PhaseResult {
        /** Name of the phase (e.g., "1-ACCONCIATURA", "2-LABS-acconciatura") */
        public final String phaseName;

        /** Whether this phase achieved 0hard */
        public final boolean success;

        /** Number of rotation candidates evaluated in this phase */
        public final int candidatesEvaluated;

        /** Time spent in this phase */
        public final Duration duration;

        /** Number of entities pinned by this phase */
        public final int entitiesPinned;

        /** For Phase 2, which roomTag was processed (e.g., "acconciatura", "meccanica") */
        public final String roomTag;

        /**
         * Construct a new phase result.
         *
         * @param phaseName name of the phase
         * @param success whether 0hard was achieved
         * @param candidatesEvaluated number of candidates tried
         * @param duration time spent
         * @param entitiesPinned number of entities pinned
         * @param roomTag the room tag processed (for Phase 2)
         */
        public PhaseResult(String phaseName, boolean success, int candidatesEvaluated,
                          Duration duration, int entitiesPinned, String roomTag) {
            this.phaseName = phaseName;
            this.success = success;
            this.candidatesEvaluated = candidatesEvaluated;
            this.duration = duration;
            this.entitiesPinned = entitiesPinned;
            this.roomTag = roomTag;
        }
    }
}
