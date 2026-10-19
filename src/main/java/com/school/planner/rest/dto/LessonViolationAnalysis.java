package com.school.planner.rest.dto;

import com.school.planner.domain.constraints.Justification;

import java.util.List;

/**
 * Per-entity violation analysis for a single Lesson (the only planning entity since TASK-228/267).
 * Used by the frontend to highlight problematic schedule entries.
 */
public record LessonViolationAnalysis(
    Long lessonId,
    String entityType,           // always "Lesson" since TASK-228/267 (no LabBlock entity remains)
    String teacher,
    String subject,
    String studentGroup,
    String timeslot,             // ISO datetime or null
    String room,
    boolean hasViolations,
    String totalScore,
    List<ViolationDetail> violations
) {
    /**
     * Details about a single constraint violation affecting this entity.
     */
    public record ViolationDetail(
        String constraint,       // e.g., "H1.1"
        String level,            // "HARD", "MEDIUM", "SOFT"
        long score,
        String message,          // null for now - could hold human-readable explanation
        Justification justification  // Structured justification with entity IDs (null if not available)
    ) {}
}
