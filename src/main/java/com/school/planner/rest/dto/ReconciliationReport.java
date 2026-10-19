package com.school.planner.rest.dto;

import java.util.List;

/**
 * Full reconciliation report: replicates the school's own "Classi" Excel
 * sheet (Ore Inc. vs Ore Prg., per teacher/subject/group), plus per-teacher
 * and per-group rollups and a global summary.
 *
 * See claudedocs/spalmone-2026-27/analysis.md §6 for the source semantics,
 * and claudedocs/design/stage-esame-ra-modeling-2026-07-16.md for why
 * pseudo (Stage/Esame/RA) courses carry expected, non-alarming deltas.
 */
public record ReconciliationReport(
    List<Row> rows,
    List<Rollup> teacherRollups,
    List<Rollup> groupRollups,
    Summary summary
) {
    /**
     * One row per Course: (teacher, subject, group) triple.
     * assignedHours = Course.totalHours ("Ore Inc.").
     * scheduledHours = count of that course's lessons with a non-null timeslot ("Ore Prg.").
     * pinnedHours = of those, how many are pinned.
     * expectedUnscheduledHours = Course.expectedUnscheduledHours (0 if null) -
     * the part of {@code delta} that is structurally unschedulable by design
     * (TASK-373 code review H1/H4, e.g. the golden Stage course's closure-day
     * and beyond-Timeslot-horizon rows), not a real mismatch to chase.
     * {@code delta - expectedUnscheduledHours} is the "actionable" delta a
     * consumer should alert on.
     */
    public record Row(
        Long courseId,
        Long teacherId,
        String teacherName,
        String subject,
        Long groupId,
        String groupName,
        int assignedHours,
        int scheduledHours,
        int pinnedHours,
        int delta,
        int expectedUnscheduledHours,
        String status,   // OK | MISMATCH | UNSCHEDULED
        boolean pseudo   // true for synthetic Stage/Esame/RA accounting courses
    ) {}

    /**
     * Aggregated hours for a teacher or a group across all their course rows.
     *
     * {@code issueCount} - number of rows folded into this rollup that are
     * real (non-pseudo) and did not reconcile (status != OK). Drives
     * subtotal severity in the UI instead of the summed {@code delta}'s
     * sign: two rows at +5 and -5 net to a healthy-looking zero delta while
     * still being two unresolved mismatches (see
     * ReconciliationService#isIssue).
     */
    public record Rollup(
        Long id,
        String name,
        int assignedHours,
        int scheduledHours,
        int delta,
        int issueCount
    ) {}

    /** Global counts across all rows, by status. */
    public record Summary(
        int okCount,
        int mismatchCount,
        int unscheduledCount,
        int totalCourses
    ) {}
}
