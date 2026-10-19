package com.school.planner.domain.constraints;

import ai.timefold.solver.core.api.score.stream.ConstraintJustification;

import java.time.LocalDate;

/**
 * Justification for H17: Vertically compact constraint.
 * <p>
 * Stores entity IDs for vertical compaction violations. A violation occurs
 * when a student group has gaps between their lessons on the same day.
 * <p>
 * String details (names, dates, gap counts) are resolved on-demand by
 * {@link com.school.planner.service.ConstraintMessageFormatter}
 * to avoid hot-path string allocation during solver runs.
 *
 * @param groupId        ID of the student group with gap violations
 * @param date           Date of the violation
 * @param gapCount       Number of gaps found in the schedule
 */
public record VerticallyCompactJustification(
        long groupId,
        LocalDate date,
        int gapCount
) implements Justification, ConstraintJustification {
}
