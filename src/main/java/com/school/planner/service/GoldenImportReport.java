package com.school.planner.service;

import java.util.List;
import java.util.Map;

/**
 * Report from importing the golden (hand-made) 2026/27 timetable
 * (TASK-346, {@code claudedocs/spalmone-2026-27/data/lessons.csv}) as pinned
 * Lesson entities.
 *
 * <p>Every row of lessons.csv falls into exactly one bucket below; the
 * bucket counts always sum to {@code rows}. See
 * {@code GoldenScheduleImportService#categorize} for the precedence order
 * (closed/blank-teacher, then Esame, then timeslot existence, then
 * stage-vs-real course matching).</p>
 *
 * <p>Pattern: follows {@link PreAssignmentFeasibilityReport} / {@link HybridPhase1aReport}
 * (public final fields + explicit constructor, nested static class for the
 * unmatched-course detail).</p>
 */
public class GoldenImportReport {

    /** Total data rows read from lessons.csv (header excluded). */
    public final int rows;

    /**
     * Rows matched to a real-teacher Course and imported as pinned Lessons,
     * plus (TASK-366) the "RA"-subject rows with no matching Course that were
     * imported under a newly created per-teacher "Recuperi ed Approfondimenti"
     * micro-course instead of falling into {@link #unmatchedNoCourseTriples}.
     */
    public final int imported;

    /** Stage rows (teacher="Stage") imported as pinned Lessons under the synthetic per-group Stage course. */
    public final int importedStage;

    /** Esame rows (teacher="Esame") skipped - Esame is never scheduled as lessons (design doc TASK-344). */
    public final int skippedEsame;

    /**
     * Rows skipped because closed="True" or the teacher column was blank -
     * EXCLUDING closed="True" rows where teacher="Stage" (those are counted
     * separately, see {@link #stageOnClosureByGroup}, TASK-373).
     */
    public final int skippedClosed;

    /**
     * Rows where closed="True" AND teacher="Stage", per student group (group
     * name -> row count) - a real, school-confirmed stage day that happens to
     * fall on a school closure ("lo stage puo essere fatto anche nei giorni
     * di chiusura", referente Q8, TASK-373). These are NOT skipped rows: they
     * widen the synthetic Stage course's totalHours (see
     * {@code GoldenScheduleImportService#reconcileStageCourseHours}) so
     * {@code assigned == scheduled + stageOnClosure} reconciles exactly, even
     * though (per design doc section A) no Lesson is ever pinned for them -
     * no Timeslot exists on a closure day and none is generated for one.
     */
    public final Map<String, Integer> stageOnClosureByGroup;

    /**
     * Rows where closed="False" AND teacher="Stage" but (date, slot-start-time)
     * has no matching Timeslot, per student group (group name -&gt; row count) -
     * e.g. G04's 2027-06-11..14 rows, beyond the school year's Timeslot
     * horizon (TimeslotSequence.SCHOOL_YEAR_END = 2027-06-10; code review H4,
     * TASK-373). Structurally unschedulable for the same reason as
     * {@link #stageOnClosureByGroup}: no Timeslot exists and none is
     * generated for one, so these widen the synthetic Stage course's
     * {@code totalHours}/{@code expectedUnscheduledHours} exactly like
     * stage-on-closure rows do (see
     * {@code GoldenScheduleImportService#reconcileStageCourseHours}), rather
     * than being silently absorbed into the generic {@link #unmatchedNoTimeslot}
     * bucket shared with every other subject.
     */
    public final Map<String, Integer> stageBeyondHorizonByGroup;

    /**
     * Rows whose (date, slot-start-time) has no matching Timeslot - e.g. the
     * 2027-06-11..15 FC exam-week dates, beyond the school year's Timeslot
     * horizon (TimeslotSequence.SCHOOL_YEAR_END = 2027-06-10) - EXCLUDING
     * Stage rows, which are counted separately (see
     * {@link #stageBeyondHorizonByGroup}, code review H4).
     */
    public final int unmatchedNoTimeslot;

    /** Distinct (teacher, subject, group) triples with no matching Course, most frequent first. */
    public final List<UnmatchedCourseTriple> unmatchedNoCourseTriples;

    /**
     * Synthetic per-group Esame accounting Courses created this run (pseudo-teacher
     * "Esame", zero scheduled Lessons - design doc section B, M2). 0 on a re-run
     * against a profile that already has them (find-or-create, M11).
     */
    public final int esameCoursesCreated;

    /**
     * Synthetic RA accounting Course created this run for G08 only (pseudo-teacher
     * "RA" - design doc section C, M3). G04's RA pool duplicates its real
     * T15 course and never gets one, so this is 0 or 1.
     */
    public final int raCoursesCreated;

    /** Pinned Lesson rows deleted by this call's force=true (0 if force was not needed). */
    public final long deletedPinned;

    /** Unpinned Lesson rows deleted by this call's force=true (0 if force was not needed). */
    public final long deletedUnpinned;

    /**
     * One human-readable warning per student group whose incarichi.csv Stage
     * hours disagree with the grid-derived Stage course totalHours (code
     * review H6, TASK-373) - previously log-only ({@code LOG.warnf} in
     * {@code reconcileStageCourseHours}), now API-visible so a drift doesn't
     * require reading server logs to notice. Empty on the current seed (see
     * the design doc's cross-check table).
     */
    public final List<String> stageDriftWarnings;

    public GoldenImportReport(int rows, int imported, int importedStage, int skippedEsame, int skippedClosed,
                              Map<String, Integer> stageOnClosureByGroup,
                              Map<String, Integer> stageBeyondHorizonByGroup,
                              int unmatchedNoTimeslot, List<UnmatchedCourseTriple> unmatchedNoCourseTriples,
                              int esameCoursesCreated, int raCoursesCreated,
                              long deletedPinned, long deletedUnpinned, List<String> stageDriftWarnings) {
        this.rows = rows;
        this.imported = imported;
        this.importedStage = importedStage;
        this.skippedEsame = skippedEsame;
        this.skippedClosed = skippedClosed;
        this.stageOnClosureByGroup = stageOnClosureByGroup;
        this.stageBeyondHorizonByGroup = stageBeyondHorizonByGroup;
        this.unmatchedNoTimeslot = unmatchedNoTimeslot;
        this.unmatchedNoCourseTriples = unmatchedNoCourseTriples;
        this.esameCoursesCreated = esameCoursesCreated;
        this.raCoursesCreated = raCoursesCreated;
        this.deletedPinned = deletedPinned;
        this.deletedUnpinned = deletedUnpinned;
        this.stageDriftWarnings = stageDriftWarnings;
    }

    /**
     * Total row count across all unmatched (teacher, subject, group) triples.
     * Derived from {@link #unmatchedNoCourseTriples} (never stored) so it can't
     * disagree with the list; the getter name keeps the JSON property
     * {@code unmatchedNoCourseRows} identical to the documented report shape.
     */
    public int getUnmatchedNoCourseRows() {
        return unmatchedNoCourseTriples.stream().mapToInt(t -> t.rowCount).sum();
    }

    /**
     * Total stage-on-closure row count. Derived from
     * {@link #stageOnClosureByGroup} (never stored) so it can't disagree with
     * the per-group breakdown; the getter name keeps the JSON property
     * {@code stageOnClosure} in the documented report shape.
     */
    public int getStageOnClosure() {
        return sumValues(stageOnClosureByGroup);
    }

    /**
     * Total stage-beyond-horizon row count. Derived from
     * {@link #stageBeyondHorizonByGroup} (never stored) so it can't disagree
     * with the per-group breakdown; the getter name keeps the JSON property
     * {@code stageBeyondHorizon} in the documented report shape.
     */
    public int getStageBeyondHorizon() {
        return sumValues(stageBeyondHorizonByGroup);
    }

    private static int sumValues(Map<String, Integer> byGroup) {
        return byGroup.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** A distinct (teacher, subject, group) triple from lessons.csv with no matching Course, and how many rows it covers. */
    public static class UnmatchedCourseTriple {
        public String teacher;
        public String subject;
        public String group;
        public int rowCount;

        public UnmatchedCourseTriple() {
        }

        public UnmatchedCourseTriple(String teacher, String subject, String group, int rowCount) {
            this.teacher = teacher;
            this.subject = subject;
            this.group = group;
            this.rowCount = rowCount;
        }
    }
}
