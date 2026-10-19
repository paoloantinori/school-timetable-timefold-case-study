package com.school.planner.domain;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Completion information for a student group.
 * Represents the theoretical first day after all lessons assuming full horizontal compaction.
 *
 * This is NOT persisted - computed dynamically when schedule is loaded.
 */
public class CompletionInfo {

    /**
     * The last day any lesson exists for this group.
     * This is the actual end date based on scheduled lessons.
     */
    @JsonProperty
    public LocalDate lastLessonDate;

    /**
     * The number of lessons on the final day.
     * Used to determine if it's a "full" day or partial.
     */
    @JsonProperty
    public int finalDayLessonCount;

    /**
     * Whether this represents perfect horizontal compaction.
     * True if there are no sparse days (days with 1-3 hours) after the start.
     * This is computed by checking if all lesson days are "full" (4+ hours).
     */
    @JsonProperty
    public boolean isPerfectlyCompacted;

    public CompletionInfo() {
    }

    public CompletionInfo(LocalDate lastLessonDate, int finalDayLessonCount, boolean isPerfectlyCompacted) {
        this.lastLessonDate = lastLessonDate;
        this.finalDayLessonCount = finalDayLessonCount;
        this.isPerfectlyCompacted = isPerfectlyCompacted;
    }

    /**
     * Get the completion date - the first school day after the last lesson.
     * This skips weekends and holidays.
     */
    @JsonProperty("completionDate")
    public LocalDate getCompletionDate() {
        if (lastLessonDate == null) {
            return null;
        }

        LocalDate nextDay = lastLessonDate.plusDays(1);

        // Skip weekends
        while (nextDay.getDayOfWeek().getValue() >= 6) { // 6=Saturday, 7=Sunday
            nextDay = nextDay.plusDays(1);
        }

        // Note: We don't skip holidays here because the completion marker
        // shows the theoretical day after lessons end, even if it's a holiday

        return nextDay;
    }

    /**
     * Get a human-readable label for the completion marker.
     */
    @JsonProperty("label")
    public String getLabel() {
        if (isPerfectlyCompacted) {
            return "All Done";
        } else if (finalDayLessonCount >= 4) {
            return "Last Day";
        } else {
            return "Ends";
        }
    }
}
