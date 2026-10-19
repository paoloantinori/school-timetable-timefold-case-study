package com.school.planner.domain.constraints;

import ai.timefold.solver.core.api.score.stream.ConstraintJustification;

import java.time.LocalDate;

/**
 * Justification for H11: Max student group daily hours constraint.
 * <p>
 * Stores entity IDs for student group daily hour violations. String details
 * (names, dates) are resolved on-demand by
 * {@link com.school.planner.service.ConstraintMessageFormatter}
 * to avoid hot-path string allocation during solver runs.
 *
 * @param groupId         ID of the student group exceeding the daily limit
 * @param date            Date of the violation
 * @param lessonCount     Actual number of hours scheduled
 * @param maxHours        Maximum allowed hours (varies by group)
 * @param excessHours     Number of hours over the limit
 */
public record StudentGroupDailyHoursJustification(
        long groupId,
        LocalDate date,
        long lessonCount,
        long maxHours,
        long excessHours
) implements Justification, ConstraintJustification {
}
