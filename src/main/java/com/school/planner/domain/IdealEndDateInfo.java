package com.school.planner.domain;

import java.time.LocalDate;

/**
 * Extended information about the ideal end date calculation for a student group.
 *
 * This data is computed by TargetEndDateCalculator and exposed via the REST API
 * for UI display (TASK-109: ideal last day of school markers).
 *
 * @param idealEndDate The earliest date when all required hours can be scheduled
 * @param totalHours Total required course hours for this group
 * @param excludedDays Number of school days excluded (holidays + stage days)
 * @param availableDays Number of available school days for scheduling
 */
public record IdealEndDateInfo(
    LocalDate idealEndDate,
    int totalHours,
    int excludedDays,
    int availableDays
) {
    /**
     * Compact constructor for Jackson serialization.
     */
    public IdealEndDateInfo {
        // All fields are required
        if (idealEndDate == null) {
            throw new IllegalArgumentException("idealEndDate cannot be null");
        }
    }
}
