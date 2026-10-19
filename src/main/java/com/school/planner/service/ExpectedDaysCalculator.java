package com.school.planner.service;

import java.time.temporal.ChronoUnit;

import org.jboss.logging.Logger;

import com.school.planner.domain.Course;
import com.school.planner.domain.LabRotation;

/**
 * Calculates expected number of days per week for lab courses.
 *
 * The expected days are based on the actual rotation period duration,
 * not a hardcoded 38-week school year. This ensures accurate classification
 * of single-day vs multi-day lab patterns.
 *
 * For courses without a LabRotation, uses 38 weeks as a default (full school year).
 */
public class ExpectedDaysCalculator {

    private static final Logger LOG = Logger.getLogger(ExpectedDaysCalculator.class);

    /**
     * Default weeks in a school year (September → June, excluding holidays).
     * Used as fallback when no LabRotation period is available.
     */
    private static final double DEFAULT_WEEKS = 38.0;

    /**
     * Calculates expected days per week for a course given its rotation period.
     *
     * @param course The course (must be a lab course)
     * @param rotation The LabRotation (can be null for non-rotation labs)
     * @return Expected days per week (minimum 1)
     */
    public static int computeExpectedDays(Course course, LabRotation rotation) {
        if (course == null || !course.isLab || course.labBlockSize <= 0) {
            return 1;
        }

        int blockCount = (int) Math.ceil(
            (double) course.totalHours / course.labBlockSize);

        double weeks = getWeeksInPeriod(rotation);

        int expectedDays = Math.max(1, (int) Math.round((double) blockCount / weeks));

        LOG.debugf("Expected days for %s: %d blocks / %.1f weeks = %d days/week",
            course.subject, blockCount, weeks, expectedDays);

        return expectedDays;
    }

    /**
     * Calculates expected days per week for a course using its current
     * expectedLabDaysPerWeek field value (for backward compatibility).
     *
     * @param course The course
     * @return Expected days per week
     */
    public static int computeExpectedDays(Course course) {
        if (course == null) {
            return 1;
        }

        // If the field has been set and is non-zero, use it
        if (course.expectedLabDaysPerWeek != null && course.expectedLabDaysPerWeek > 0) {
            return course.expectedLabDaysPerWeek;
        }

        // Fallback to legacy computation (38 weeks)
        return computeExpectedDays(course, null);
    }

    /**
     * Gets the number of weeks in a rotation period.
     *
     * @param rotation The LabRotation (can be null)
     * @return Number of weeks (fractional for precision)
     */
    private static double getWeeksInPeriod(LabRotation rotation) {
        if (rotation == null || rotation.periodStart == null || rotation.periodEnd == null) {
            return DEFAULT_WEEKS;
        }

        // Use fractional weeks for precision
        long days = ChronoUnit.DAYS.between(rotation.periodStart, rotation.periodEnd);
        return (double) days / 7.0;
    }

    /**
     * Updates the expectedLabDaysPerWeek field on a Course entity
     * and persists it to the database.
     *
     * @param course The course to update
     * @param rotation The LabRotation (can be null)
     * @return true if the value was changed, false otherwise
     */
    public static boolean updateCourseExpectedDays(Course course, LabRotation rotation) {
        int computed = computeExpectedDays(course, rotation);

        Integer current = course.expectedLabDaysPerWeek;
        if (current == null || !current.equals(computed)) {
            LOG.infof("Updating expected days for %s: %s → %d",
                course.subject, current, computed);
            course.expectedLabDaysPerWeek = computed;
            course.persist();
            return true;
        }

        return false;
    }
}
