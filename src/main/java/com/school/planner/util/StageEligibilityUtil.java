package com.school.planner.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.school.planner.domain.Course;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;

/**
 * Utility class for stage eligibility checks.
 * Consolidates duplicate logic from GreedyRotationAssigner, AdaptiveSlotFinder,
 * RotationTestRunner, and DayClusteredConstructionPhase.
 */
public final class StageEligibilityUtil {

    private StageEligibilityUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Check if a date is a stage blackout day for the student group.
     *
     * A date is a stage blackout if:
     * - It falls within a stage period (startDate to endDate)
     * - The day of week matches the stage's dayOfWeekMask
     * - The stage pattern belongs to the student group
     *
     * Uses the most defensive implementation (AdaptiveSlotFinder version)
     * with explicit null check for dayOfWeekMask.
     *
     * @param date The date to check
     * @param group The student group (must match stage pattern's group)
     * @param stagePatterns List of stage patterns to check against
     * @return true if the date is a stage blackout day
     */
    public static boolean isStageBlackout(LocalDate date, StudentGroup group,
                                           List<StagePattern> stagePatterns) {
        if (group == null || stagePatterns == null) {
            return false;
        }

        DayOfWeek dow = date.getDayOfWeek();
        return stagePatterns.stream().anyMatch(sp ->
            sp.studentGroup != null &&
            sp.studentGroup.id.equals(group.id) &&
            !date.isBefore(sp.startDate) &&
            !date.isAfter(sp.endDate) &&
            sp.dayOfWeekMask != null &&
            sp.dayOfWeekMask.contains(dow)
        );
    }

    /**
     * Shared "teaching-open date" gate (TASK-413 simplify): not a holiday, not
     * a weekend, not a stage-blackout day for the group. Single source for the
     * date-availability composition {@code WeeklyTemplatePlanner} and
     * {@code DayClusteredConstructionPhase.isDateAvailable} both need, so the
     * template path and the round-robin repair tail cannot drift on what
     * counts as an available date (the phase layers its teacher-vacation check
     * on top of this).
     *
     * @param holidays the school-holiday date set (already expanded)
     */
    public static boolean isOpenTeachingDate(LocalDate date, StudentGroup group,
                                             java.util.Set<LocalDate> holidays,
                                             List<StagePattern> stagePatterns) {
        if (holidays != null && holidays.contains(date)) {
            return false;
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !isStageBlackout(date, group, stagePatterns);
    }

    /**
     * Check if a course should skip stage days for the student group.
     *
     * Professionalizzanti courses may be scheduled on stage days only when
     * the group's allowProfessionalizzantiDuringStage flag is true.
     * Non-professionalizzanti courses always skip stage days.
     *
     * @param course The course to check (null = non-professionalizzani)
     * @param group The student group
     * @return true if the course should skip stage days
     */
    public static boolean shouldSkipStageDay(Course course, StudentGroup group) {
        if (group == null) {
            return true; // Defensive: can't schedule without a group
        }
        boolean isProfCourse = course != null && course.professionalizzanti;
        boolean groupExcludedDuringStage = !Boolean.TRUE.equals(group.allowProfessionalizzantiDuringStage);
        return !isProfCourse || groupExcludedDuringStage;
    }

    /**
     * Combined check: should this course be skipped on this specific date?
     *
     * Convenience method that combines both checks.
     *
     * @param course The course to check
     * @param group The student group
     * @param date The date to check
     * @param stagePatterns List of stage patterns
     * @return true if the course should not be scheduled on this date
     */
    public static boolean shouldSkipDate(Course course, StudentGroup group,
                                        LocalDate date, List<StagePattern> stagePatterns) {
        return shouldSkipStageDay(course, group) && isStageBlackout(date, group, stagePatterns);
    }
}
