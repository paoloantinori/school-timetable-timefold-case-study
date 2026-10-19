package com.school.planner.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Report detailing why pre-assignment is infeasible for a group-day combination.
 * Contains the gap analysis: required blocks vs available dates, with exclusion reasons.
 */
public class PreAssignmentFeasibilityReport {
    public boolean feasible;
    public String step;          // e.g., "acconciatura"
    public String message;       // Summary message
    public List<GroupFeasibility> groups;

    public PreAssignmentFeasibilityReport(boolean feasible, String step, String message, List<GroupFeasibility> groups) {
        this.feasible = feasible;
        this.step = step;
        this.message = message;
        this.groups = groups;
    }

    /** Feasibility details for a single (group, teacher, day) combination. */
    public static class GroupFeasibility {
        public String groupName;
        public String teacherName;
        public String dayOfWeek;
        public String period;            // e.g., "2025-10-06 – 2025-12-22"
        public int requiredBlocks;
        public int availableDates;
        public List<DateExclusion> exclusions;  // Why dates were excluded

        public GroupFeasibility(String groupName, String teacherName, String dayOfWeek,
                                String period, int requiredBlocks, int availableDates,
                                List<DateExclusion> exclusions) {
            this.groupName = groupName;
            this.teacherName = teacherName;
            this.dayOfWeek = dayOfWeek;
            this.period = period;
            this.requiredBlocks = requiredBlocks;
            this.availableDates = availableDates;
            this.exclusions = exclusions;
        }
    }

    /** A single date exclusion with reason. */
    public static class DateExclusion {
        public LocalDate date;
        public String reason;  // e.g., "holiday", "stage blackout", "outside period"

        public DateExclusion(LocalDate date, String reason) {
            this.date = date;
            this.reason = reason;
        }
    }
}
