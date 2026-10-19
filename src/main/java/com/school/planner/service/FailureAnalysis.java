package com.school.planner.service;

import java.util.List;
import java.util.Set;

/**
 * Detailed analysis of why no rotation candidate achieved 0hard.
 * Used to provide actionable feedback to the scheduler.
 *
 * <p>When the hybrid Phase 1a approach fails to find a valid rotation,
 * this analysis captures the common patterns that prevented success,
 * allowing the human scheduler to understand what needs to change.</p>
 *
 * <p>Pattern: Follows {@link PreAssignmentFeasibilityReport} structure with nested static classes.</p>
 */
public class FailureAnalysis {

    /** The phase that failed (e.g., "1-ACCONCIATURA", "2-LABS-meccanica") */
    public final String phase;

    /** High-level reason for failure */
    public final String reason;

    /** Patterns of conflicts that prevented assignment */
    public final List<ConflictPattern> conflictPatterns;

    /** IDs of teachers involved in conflicts */
    public final Set<Long> conflictingTeacherIds;

    /** IDs of student groups involved in conflicts */
    public final Set<Long> conflictingGroupIds;

    /** Names of teachers involved in conflicts (for human-readable output) */
    public final List<String> conflictingTeacherNames;

    /** Names of student groups involved in conflicts */
    public final List<String> conflictingGroupNames;

    /** Period date range (for display) */
    public final String periodRange;

    /** Human-readable suggestion for resolving the issue */
    public final String suggestion;

    /**
     * Construct a new failure analysis.
     *
     * @param phase the phase that failed
     * @param reason high-level reason
     * @param conflictPatterns patterns of conflicts found
     * @param conflictingTeacherIds teachers involved
     * @param conflictingGroupIds groups involved
     * @param conflictingTeacherNames teacher names for display
     * @param conflictingGroupNames group names for display
     * @param periodRange period date range
     * @param suggestion actionable suggestion
     */
    public FailureAnalysis(String phase, String reason, List<ConflictPattern> conflictPatterns,
                          Set<Long> conflictingTeacherIds, Set<Long> conflictingGroupIds,
                          List<String> conflictingTeacherNames, List<String> conflictingGroupNames,
                          String periodRange, String suggestion) {
        this.phase = phase;
        this.reason = reason;
        this.conflictPatterns = conflictPatterns == null ? List.of() : List.copyOf(conflictPatterns);
        this.conflictingTeacherIds = conflictingTeacherIds != null ? Set.copyOf(conflictingTeacherIds) : Set.of();
        this.conflictingGroupIds = conflictingGroupIds != null ? Set.copyOf(conflictingGroupIds) : Set.of();
        this.conflictingTeacherNames = conflictingTeacherNames != null ? List.copyOf(conflictingTeacherNames) : List.of();
        this.conflictingGroupNames = conflictingGroupNames != null ? List.copyOf(conflictingGroupNames) : List.of();
        this.periodRange = periodRange != null ? periodRange : "";
        this.suggestion = suggestion;
    }


    /**
     * Backward-compatible constructor for legacy code.
     *
     * <p>Initializes new fields with empty defaults.</p>
     *
     * @param phase the phase that failed
     * @param reason high-level reason
     * @param conflictPatterns patterns of conflicts found
     * @param conflictingTeacherIds teachers involved
     * @param conflictingGroupIds groups involved
     * @param suggestion actionable suggestion
     */
    public FailureAnalysis(String phase, String reason, List<ConflictPattern> conflictPatterns,
                          Set<Long> conflictingTeacherIds, Set<Long> conflictingGroupIds,
                          String suggestion) {
        this(phase, reason, conflictPatterns, conflictingTeacherIds, conflictingGroupIds,
             List.of(), List.of(), "", suggestion);
    }

    /**
     * Format as human-readable diagnostic message.
     *
     * <p>Produces multi-line output suitable for logging or API response:</p>
     * <pre>
     * INFEASIBLE: T28/G10/acconciatura
     *   Reason: 64 hours / 6h blocks = 11 blocks needed
     *   Period: Apr 5 - Jun 10 (9 Thursdays available)
     *   Constraint: Only 9 slots for 11 blocks
     *   Suggestion: Reduce course to 54 hours or extend rotation period
     * </pre>
     */
    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        
        // Header: teacher/group/phase
        String teacherStr = conflictingTeacherNames.isEmpty() ? "Unknown" : String.join(", ", conflictingTeacherNames);
        String groupStr = conflictingGroupNames.isEmpty() ? "Unknown" : String.join(", ", conflictingGroupNames);
        sb.append("INFEASIBLE: ").append(teacherStr)
          .append("/").append(groupStr)
          .append("/").append(phase).append("\n");

        // Conflict patterns
        for (ConflictPattern pattern : conflictPatterns) {
            sb.append("  ").append(pattern.type).append(": ")
              .append(pattern.description).append("\n");
            for (String example : pattern.examples) {
                sb.append("    ").append(example).append("\n");
            }
        }

        // Period and suggestion
        if (!periodRange.isEmpty()) {
            sb.append("  Period: ").append(periodRange).append("\n");
        }
        sb.append("  Suggestion: ").append(suggestion);

        return sb.toString();
    }

    /**
     * A pattern of conflicts that prevented assignment.
     *
     * <p>Each pattern represents a category of constraint violation that
     * was common across multiple rotation candidates.</p>
     */
    public static class ConflictPattern {
        /** Type of conflict (e.g., "TEACHER_UNAVAILABLE", "ROOM_CONFLICT", "STAGE_BLACKOUT") */
        public final String type;

        /** Human-readable description of the conflict pattern */
        public final String description;

        /** Number of times this pattern occurred across candidates */
        public final int occurrenceCount;

        /** Specific examples of this conflict (for debugging) */
        public final List<String> examples;

        /**
         * Construct a new conflict pattern.
         *
         * @param type conflict type
         * @param description human-readable description
         * @param occurrenceCount number of occurrences
         * @param examples specific examples
         */
        public ConflictPattern(String type, String description, int occurrenceCount, List<String> examples) {
            this.type = type;
            this.description = description;
            this.occurrenceCount = occurrenceCount;
            this.examples = examples == null ? List.of() : List.copyOf(examples);
        }
    }
}
