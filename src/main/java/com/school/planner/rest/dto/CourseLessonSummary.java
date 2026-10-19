package com.school.planner.rest.dto;

/**
 * Minimal per-lesson info for the reconciliation drill-down (TASK-353):
 * the scheduled lessons behind a single Course row, kept in a separate
 * endpoint so the main /api/reconciliation payload stays light.
 */
public record CourseLessonSummary(
    Long lessonId,
    String date,        // ISO date, e.g. "2027-03-01"
    String startTime,   // ISO date-time of the timeslot start
    String roomName,    // null if unassigned
    boolean pinned
) {}
