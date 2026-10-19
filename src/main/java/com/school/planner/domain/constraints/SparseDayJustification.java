package com.school.planner.domain.constraints;

import ai.timefold.solver.core.api.score.stream.ConstraintJustification;

import java.time.LocalDate;

/**
 * Justification for M20: daily quota fill (deficit side of the day-fill family).
 * <p>
 * Stores entity IDs for sparse day violations. String details (names, dates)
 * are resolved on-demand by {@link com.school.planner.service.ConstraintMessageFormatter}
 * to avoid hot-path string allocation during solver runs.
 * <p>
 * A deficit day is a (group, date) whose non-authoritative occupancy is above
 * zero but below the day's expected-hours quota (TASK-385 target, net of the
 * school's own pins since TASK-424).
 *
 * @param groupId          ID of the student group with the deficit day
 * @param date             Date of the deficit day
 * @param actualHours      Hours the solver parked on that day
 * @param requiredHours    The day's expected-hours quota (net target)
 */
public record SparseDayJustification(
        long groupId,
        LocalDate date,
        long actualHours,
        long requiredHours
) implements Justification, ConstraintJustification {
}
