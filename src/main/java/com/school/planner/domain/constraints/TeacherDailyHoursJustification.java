package com.school.planner.domain.constraints;

import ai.timefold.solver.core.api.score.stream.ConstraintJustification;

import java.time.LocalDate;

/**
 * Justification for H7: Max teacher daily hours constraint.
 * <p>
 * Stores entity IDs for teacher daily hour violations. String details (names, dates)
 * are resolved on-demand by {@link com.school.planner.service.ConstraintMessageFormatter}
 * to avoid hot-path string allocation during solver runs.
 *
 * @param teacherId      ID of the teacher exceeding the daily limit
 * @param date           Date of the violation
 * @param lessonCount    Actual number of hours scheduled
 * @param maxHours       Maximum allowed hours (per-teacher override via
 *                       {@code Teacher.maxDailyHours}, default 6 - TASK-348)
 * @param excessHours    Number of hours over the limit
 */
public record TeacherDailyHoursJustification(
        long teacherId,
        LocalDate date,
        long lessonCount,
        long maxHours,
        long excessHours
) implements Justification, ConstraintJustification {
}
