package com.school.planner.domain.constraints;

import ai.timefold.solver.core.api.score.stream.ConstraintJustification;

import java.time.LocalDate;

/**
 * Justification for M18: Day should start from first calendar-open slot.
 * <p>
 * Stores entity IDs for a (student group, date) day whose first lesson starts
 * later than the group's first calendar-open slot for that day (see
 * {@code SchoolSchedule.getGroupCalendarWindow}). Was H18 ("5-hour groups must
 * start from slot 0"): widened to every group-day and demoted hard-&gt;medium
 * by TASK-247/405 - see CLAUDE.md "H17 afternoon-contiguity + M18 gate
 * recalibration" and "TASK-405".
 * <p>
 * String details (names, dates) are resolved on-demand by
 * {@link com.school.planner.service.ConstraintMessageFormatter}
 * to avoid hot-path string allocation during solver runs.
 *
 * @param groupId     ID of the student group whose day starts late
 * @param date        Date of the violation
 * @param offsetSlots Slots between the day's actual first lesson and the group's
 *                    first calendar-open slot
 */
public record DayStartOffsetJustification(
        long groupId,
        LocalDate date,
        int offsetSlots
) implements Justification, ConstraintJustification {
}
