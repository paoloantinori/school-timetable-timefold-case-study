package com.school.planner.service;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * Represents a candidate rotation assignment for lab blocks.
 * Maps (teacherId, studentGroupId) pairs to their assigned dayOfWeek.
 *
 * <p>This is used in the hybrid Phase 1a approach where multiple rotation
 * candidates are generated and tested by the solver. The candidate number
 * provides ordering for deterministic processing.</p>
 *
 * <p>Pattern: Follows {@link SlotCandidate} - immutable Java record with Comparable.</p>
 *
 * <p><strong>Note:</strong> Uses {@link RotationTestRunner.BlockKey} for the teacher-group pair key.</p>
 */
public record RotationCandidate(
    Map<RotationTestRunner.BlockKey, DayOfWeek> assignments,
    int candidateNumber
) implements Comparable<RotationCandidate> {

    /**
     * Compact constructor that creates a defensive copy of the assignments map.
     */
    public RotationCandidate {
        // Defensive copy for immutability
        assignments = assignments == null ? Map.of() : Map.copyOf(assignments);
    }

    /**
     * Compare by candidate number for deterministic ordering.
     */
    @Override
    public int compareTo(RotationCandidate other) {
        return Integer.compare(this.candidateNumber, other.candidateNumber);
    }

    /**
     * Get the assigned day for a specific teacher-group pair.
     *
     * @param teacherId the teacher's ID
     * @param studentGroupId the student group's ID
     * @return the assigned DayOfWeek, or null if not found
     */
    public DayOfWeek getDayFor(long teacherId, long studentGroupId) {
        return assignments.get(new RotationTestRunner.BlockKey(teacherId, studentGroupId));
    }

    /**
     * @return the number of assignments in this candidate
     */
    public int size() {
        return assignments.size();
    }
}
