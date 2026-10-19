package com.school.planner.service;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.rest.dto.CourseLessonSummary;
import com.school.planner.rest.dto.ReconciliationReport;
import com.school.planner.rest.dto.ReconciliationReport.Row;
import com.school.planner.rest.dto.ReconciliationReport.Rollup;
import com.school.planner.rest.dto.ReconciliationReport.Summary;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replicates the school's "Classi" Excel sheet: per (teacher, subject, group)
 * course row, compares assigned hours (Course.totalHours, "Ore Inc.") against
 * scheduled hours (lessons with a non-null timeslot, "Ore Prg.").
 *
 * See claudedocs/design/stage-esame-ra-modeling-2026-07-16.md for why
 * pseudo-teacher (Stage/Esame/RA) courses carry expected, non-alarming
 * deltas - they are included in the report and flagged {@code pseudo=true}
 * so the UI can style them differently instead of hiding them.
 *
 * Efficient by design: one grouped query over Lesson, one query over Course
 * (with teacher/group join-fetched), joined in memory - no N+1.
 */
@ApplicationScoped
public class ReconciliationService {

    @Inject
    EntityManager em;

    public ReconciliationReport buildReport() {
        List<Course> courses = em.createQuery(
                "SELECT c FROM Course c LEFT JOIN FETCH c.teacher LEFT JOIN FETCH c.studentGroup",
                Course.class)
                .getResultList();

        Map<Long, long[]> scheduledByCourseId = fetchScheduledCounts();

        List<Row> rows = new ArrayList<>();
        Map<Long, RollupAccumulator> teacherAcc = new LinkedHashMap<>();
        Map<Long, RollupAccumulator> groupAcc = new LinkedHashMap<>();

        int okCount = 0;
        int mismatchCount = 0;
        int unscheduledCount = 0;

        for (Course c : courses) {
            long[] counts = scheduledByCourseId.getOrDefault(c.id, EMPTY_COUNTS);
            int scheduledHours = (int) counts[0];
            int pinnedHours = (int) counts[1];
            int assignedHours = c.totalHours;
            int delta = assignedHours - scheduledHours;
            String status = computeStatus(assignedHours, scheduledHours);
            boolean pseudo = c.teacher != null && c.teacher.pseudo;

            switch (status) {
                case "OK" -> okCount++;
                case "UNSCHEDULED" -> unscheduledCount++;
                default -> mismatchCount++;
            }

            Long teacherId = c.teacher != null ? c.teacher.id : null;
            String teacherName = c.teacher != null ? c.teacher.name : null;
            Long groupId = c.studentGroup != null ? c.studentGroup.id : null;
            String groupName = c.studentGroup != null ? c.studentGroup.name : null;
            boolean issue = isIssue(status, pseudo);
            int expectedUnscheduledHours = c.expectedUnscheduledHours != null ? c.expectedUnscheduledHours : 0;

            rows.add(new Row(c.id, teacherId, teacherName, c.subject, groupId, groupName,
                    assignedHours, scheduledHours, pinnedHours, delta, expectedUnscheduledHours, status, pseudo));

            if (teacherId != null) {
                teacherAcc.computeIfAbsent(teacherId, id -> new RollupAccumulator(teacherName))
                        .add(assignedHours, scheduledHours, issue);
            }
            if (groupId != null) {
                groupAcc.computeIfAbsent(groupId, id -> new RollupAccumulator(groupName))
                        .add(assignedHours, scheduledHours, issue);
            }
        }

        rows.sort(Comparator
                .comparing((Row r) -> r.teacherName() == null ? "" : r.teacherName())
                .thenComparing(r -> r.subject() == null ? "" : r.subject())
                .thenComparing(r -> r.groupName() == null ? "" : r.groupName()));

        List<Rollup> teacherRollups = toRollupList(teacherAcc);
        List<Rollup> groupRollups = toRollupList(groupAcc);

        Summary summary = new Summary(okCount, mismatchCount, unscheduledCount, rows.size());

        return new ReconciliationReport(rows, teacherRollups, groupRollups, summary);
    }

    /**
     * The scheduled lessons behind a single Course row, for the reconciliation
     * drill-down. Kept as a separate query so the main report payload stays light.
     */
    public List<CourseLessonSummary> getLessonsForCourse(Long courseId) {
        List<Lesson> lessons = em.createQuery(
                "SELECT l FROM Lesson l LEFT JOIN FETCH l.timeslot LEFT JOIN FETCH l.room " +
                "WHERE l.course.id = :courseId AND l.timeslot IS NOT NULL " +
                "ORDER BY l.timeslot.startDateTime",
                Lesson.class)
                .setParameter("courseId", courseId)
                .getResultList();

        List<CourseLessonSummary> result = new ArrayList<>();
        for (Lesson l : lessons) {
            result.add(new CourseLessonSummary(
                    l.id,
                    l.timeslot.getDate().toString(),
                    l.timeslot.startDateTime.toString(),
                    l.room != null ? l.room.name : null,
                    l.pinned));
        }
        return result;
    }

    /**
     * Pure status logic, kept static so it can be unit-tested without a
     * Quarkus/DB context: UNSCHEDULED wins over MISMATCH when assigned hours
     * exist but nothing was ever placed on the calendar; otherwise a zero
     * delta is OK, any other delta is MISMATCH.
     */
    public static String computeStatus(int assignedHours, int scheduledHours) {
        if (assignedHours > 0 && scheduledHours == 0) {
            return "UNSCHEDULED";
        }
        if (assignedHours == scheduledHours) {
            return "OK";
        }
        return "MISMATCH";
    }

    /**
     * Whether a row counts as an actionable issue: a real (non-pseudo) row
     * that didn't reconcile. This is the single definition shared by the
     * per-bucket {@code Rollup.issueCount} below, the nav badge
     * (useReconciliationBadge.ts) and the client-side subtotal bucketing
     * (ReconciliationPage.tsx) - all three must agree on what counts as an
     * issue, since a +5/-5 offset within a bucket must not net out to
     * "healthy" just because the summed delta happens to be zero.
     */
    public static boolean isIssue(String status, boolean pseudo) {
        return !pseudo && !"OK".equals(status);
    }

    private static final long[] EMPTY_COUNTS = new long[]{0L, 0L};

    /**
     * One grouped query: scheduled hour count and pinned hour count per course id.
     *
     * "Scheduled" here means {@code l.timeslot IS NOT NULL} - the same
     * definition StudentGroupCompletionService uses for its own per-group
     * "scheduled hours" notion (see that class's calculateHoursDistribution,
     * calculateActualEndDate and calculateSlotUtilizationForAllGroups). The
     * two services compute this independently at different granularities
     * (per-course here, per-group there); if the definition of "scheduled"
     * ever changes (e.g. to also require a room), update both.
     */
    private Map<Long, long[]> fetchScheduledCounts() {
        List<Object[]> results = em.createQuery(
                "SELECT l.course.id, COUNT(l), SUM(CASE WHEN l.pinned = true THEN 1L ELSE 0L END) " +
                "FROM Lesson l " +
                "WHERE l.timeslot IS NOT NULL AND l.course IS NOT NULL " +
                "GROUP BY l.course.id",
                Object[].class)
                .getResultList();

        Map<Long, long[]> byCourseId = new HashMap<>();
        for (Object[] row : results) {
            Long courseId = (Long) row[0];
            long scheduled = (Long) row[1];
            long pinned = (Long) row[2];
            byCourseId.put(courseId, new long[]{scheduled, pinned});
        }
        return byCourseId;
    }

    private static List<Rollup> toRollupList(Map<Long, RollupAccumulator> acc) {
        List<Rollup> result = new ArrayList<>();
        for (Map.Entry<Long, RollupAccumulator> e : acc.entrySet()) {
            RollupAccumulator a = e.getValue();
            result.add(new Rollup(e.getKey(), a.name, a.assignedHours, a.scheduledHours,
                    a.assignedHours - a.scheduledHours, a.issueCount));
        }
        result.sort(Comparator.comparing(r -> r.name() == null ? "" : r.name()));
        return result;
    }

    private static final class RollupAccumulator {
        final String name;
        int assignedHours;
        int scheduledHours;
        int issueCount;

        RollupAccumulator(String name) {
            this.name = name;
        }

        void add(int assigned, int scheduled, boolean issue) {
            this.assignedHours += assigned;
            this.scheduledHours += scheduled;
            if (issue) {
                this.issueCount++;
            }
        }
    }
}
