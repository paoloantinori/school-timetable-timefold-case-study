package com.school.planner.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;


import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.util.SeedCsv;
import com.school.planner.util.SubjectNormalization;

/**
 * Imports the hand-made golden 2026/27 timetable
 * ({@code src/main/resources/seed/lessons.csv}, byte-identical to
 * {@code claudedocs/spalmone-2026-27/data/lessons.csv}) as pinned Lesson
 * entities (TASK-346).
 *
 * <p>Follows the binding design decisions in
 * {@code claudedocs/design/stage-esame-ra-modeling-2026-07-16.md}:</p>
 * <ul>
 *   <li><b>Stage</b> (teacher="Stage"): a synthetic per-group Course (pseudo-teacher
 *   "Stage", subject "Stage", professionalizzanti=false, no room tags) created
 *   lazily; its Lessons are pinned on the exact golden dates/slots.</li>
 *   <li><b>Esame</b> (teacher="Esame"): lessons.csv rows are skipped entirely
 *   (Esame hours are assigned but never scheduled in the golden grid); a
 *   synthetic per-group accounting Course (pseudo-teacher "Esame") is created
 *   from {@code incarichi.csv}'s Esame rows so the hours still reconcile,
 *   with zero scheduled Lessons.</li>
 *   <li><b>RA</b> (Recuperi ed Approfondimenti) rows under real teachers are
 *   ordinary rows, matched like any other real-teacher row. An incarichi.csv
 *   RA pool row becomes a synthetic accounting Course (pseudo-teacher "RA")
 *   only when no real course already covers the same (subject, group) - the
 *   coverage guard and skip logic remain for sheets that reintroduce such a
 *   row. Since the TASK-592 cure (11/09) the shipped incarichi.csv carries NO
 *   RA pool row (G08's 41h are 9 real per-teacher rows matching the golden's
 *   own split), so on the current seed no RA accounting Course is ever
 *   created.</li>
 *   <li><b>RA rows with no matching Course</b> (TASK-366, design doc section C,
 *   pending school answer Q9): lessons.csv rows with the raw subject
 *   abbreviation "RA" under a real teacher never real-match (the alias map has
 *   no RA entry), so they ALWAYS route here: on the current seed all 41 such
 *   rows (G08, 9 real teachers). Each distinct (teacher, group) pair gets a
 *   "Recuperi ed Approfondimenti" course via find-or-create - since the
 *   TASK-592 cure the find FINDS the 9 real per-teacher incarichi courses
 *   (same teacher/subject/group) and pins the golden rows under THEM, creating
 *   no new micro-courses - instead of being dropped (which used to punch
 *   mid-day holes and fabricate 22 H17 vertical-compactness violations).</li>
 * </ul>
 *
 * <p>Rooms are never set (the human grid has no room column). The import is
 * all-or-nothing per invocation: if any Lesson already exists, it refuses
 * unless {@code force=true}, in which case all existing Lessons are deleted
 * first. Refuses (409, via {@link IllegalStateException}) while the solver is
 * running, mirroring {@code ScheduleService#resetSchedule}'s status check.</p>
 */
@ApplicationScoped
public class GoldenScheduleImportService {

    private static final Logger LOG = Logger.getLogger(GoldenScheduleImportService.class);

    @Inject
    ScheduleService scheduleService;

    public static final String STAGE_NAME = "Stage";
    static final String ESAME_TEACHER = "Esame";
    static final String RA_TEACHER = "RA";
    // TASK-366 (design doc section C, pending school answer Q9): the raw
    // lessons.csv subject abbreviation for "Recuperi ed Approfondimenti" rows
    // taught by real teachers. Coincidentally the same string as RA_TEACHER
    // above (the incarichi.csv pool row's teacher column) but a different
    // column/meaning - kept as a distinct constant for clarity.
    static final String RA_ROW_SUBJECT = "RA";
    static final String RA_FULL_SUBJECT_NAME = "Recuperi ed Approfondimenti";
    private static final int LESSONS_CSV_COLUMNS = 7;
    private static final int INCARICHI_CSV_COLUMNS = 4;

    // lessons.csv columns: date,day,slot,closed,group,teacher,subject
    private static final int COL_DATE = 0;
    private static final int COL_SLOT = 2;
    private static final int COL_CLOSED = 3;
    private static final int COL_GROUP = 4;
    private static final int COL_TEACHER = 5;
    private static final int COL_SUBJECT = 6;

    @Transactional
    public GoldenImportReport importGolden(boolean force) {
        refuseIfSolverRunning(scheduleService, "golden import");

        long deletedPinned = 0;
        long deletedUnpinned = 0;
        long existing = Lesson.count();
        if (existing > 0) {
            if (!force) {
                throw new IllegalStateException(
                    "Refusing golden import: " + existing + " Lesson row(s) already exist in this profile. "
                    + "Pass force=true to clear existing lessons first, or use a fresh profile.");
            }
            deletedPinned = Lesson.count("pinned", true);
            deletedUnpinned = Lesson.count("pinned", false);
            LOG.infof("Golden import: force=true, deleting %d existing Lesson row(s) before import "
                    + "(%d pinned, %d unpinned)", existing, deletedPinned, deletedUnpinned);
            Lesson.deleteAll();
        }

        List<String[]> rows = SeedCsv.load("lessons.csv", LESSONS_CSV_COLUMNS);
        List<String[]> incarichiRows = SeedCsv.load("incarichi.csv", INCARICHI_CSV_COLUMNS);

        Map<TimeslotKey, Timeslot> timeslotIndex = buildTimeslotIndex();
        Map<CourseKey, Course> courseIndex = buildCourseIndex();

        RowCategorization cat = categorize(rows, timeslotIndex.keySet()::contains, courseIndex.keySet());

        // NOTE (M7): per-row persist() with IDENTITY-generated Lesson.id defeats
        // Hibernate JDBC batching (each insert needs an immediate round-trip for
        // the generated key) - ImportService's raw-JDBC batch approach is the
        // known faster alternative if this loop's ~10-12k inserts ever become
        // a real bottleneck.
        for (RealMatch m : cat.realMatches()) {
            persistPinnedLesson(courseIndex.get(m.courseKey()), timeslotIndex.get(m.timeslotKey()));
        }

        Map<String, Course> stageCourseByGroup = new HashMap<>();
        Set<String> stageGroups = stageGroupsNeedingCourse(cat);
        if (!stageGroups.isEmpty()) {
            // TASK-373 code review (H3): every group needing a Stage course
            // gets one FIRST, including a group whose Stage rows fall
            // ENTIRELY on closure days or entirely beyond the Timeslot
            // horizon (H4) - otherwise it would never get a course at all,
            // and its unschedulable hours would silently vanish from
            // accounting instead of reconciling as an expected pseudo delta.
            // None observed on the current seed, but not guaranteed by the
            // data - see stageGroupsNeedingCourse()'s own unit test.
            Teacher stageTeacher = findOrCreatePseudoTeacher(STAGE_NAME);
            for (String group : stageGroups) {
                stageCourseByGroup.put(group, findOrCreateStageCourse(group, stageTeacher));
            }
            for (StageMatch m : cat.stageMatches()) {
                persistPinnedLesson(stageCourseByGroup.get(m.group()), timeslotIndex.get(m.timeslotKey()));
            }
        }
        List<String> stageDriftWarnings = reconcileStageCourseHours(stageCourseByGroup, cat, incarichiRows);

        int esameCoursesCreated = createAccountingCourses(incarichiRows, ESAME_TEACHER);
        // Ordering matters here - see createRaMicroCoursesAndLessons()'s javadoc.
        int raCoursesCreated = createAccountingCourses(incarichiRows, RA_TEACHER);
        int raRowsImported = createRaMicroCoursesAndLessons(cat.raNoCourseRows(), timeslotIndex);

        // Mirrors ScheduleService#resetSchedule: this import mutates Lessons
        // directly (not through the solver), so cached score/phase-tracking
        // state from a previous solve is now stale and must be cleared too.
        scheduleService.clearTransientState();

        return buildReport(rows.size(), cat, esameCoursesCreated, raCoursesCreated, raRowsImported,
                deletedPinned, deletedUnpinned, stageDriftWarnings);
    }

    /**
     * Shared 409-style guard (TASK-364 code review K7): both {@code
     * importGolden} and {@link CalendarioImportService#importCalendario}
     * refuse while the solver is running, previously as two copies of the
     * same status check and message. Static + package-visible, taking the
     * caller's own injected {@link ScheduleService} instance (mirrors
     * {@link #persistPinnedLesson}/{@link #buildTimeslotIndex}'s reuse
     * pattern) rather than adding a method to {@code ScheduleService} itself.
     */
    static void refuseIfSolverRunning(ScheduleService scheduleService, String actionDescription) {
        // TASK-419: isBusy(), not the raw SolverStatus - a staged solve runs its construction,
        // promotion and demotion stages with the SolverManager idle, and an import landing in
        // one of those windows would rewrite the pins the cycle is accounting for.
        if (scheduleService.isBusy()) {
            throw new IllegalStateException(
                "Refusing " + actionDescription + ": a solve is currently running (solver status "
                + scheduleService.getStatus() + ", staged stage " + scheduleService.getStagedStage()
                + "). Stop it first (POST /api/schedule/solve DELETE), then retry.");
        }
    }

    /**
     * Static, package-visible so {@link CalendarioImportService} (TASK-364)
     * can reuse the exact same pinned-Lesson shape (room always null - neither
     * import source has a room column) instead of re-declaring it.
     */
    static void persistPinnedLesson(Course course, Timeslot timeslot) {
        Lesson lesson = new Lesson(course);
        lesson.timeslot = timeslot;
        // TASK-530a: deterministic display backfill. The pin's placement is the
        // school's own declaration and is NOT touched; but leaving room=null on
        // imported pins made the UI show healthy lab pins as "no room" cells.
        // The room is derived from the course's own tag (first assignable room
        // carrying it): pure display metadata, the solver never reads it (H29
        // exempts pinned lessons). Pseudo-Stage pins keep room=null and are
        // labeled STAGE client-side (TASK-530b).
        lesson.room = findDisplayRoomFor(course);
        lesson.pinned = true;
        lesson.persist();
    }

    /** TASK-530a: first assignable room whose tags contain the course's first
     *  required tag. TASK-530 extension (20/08, the 24+24+24 MODA aula pins the
     *  535 gate counts roomless): tag-less aula pins fall back to the first
     *  assignable room with NO lab tags (a generic classroom) - same contract,
     *  display-only, placement untouched. TASK-530b/536: pseudo-teacher courses
     *  (the Stage stubs) short-circuit to null BEFORE any fallback. Deterministic
     *  (Room.listAll() is id-ordered by the query planner on the seed). */
    static Room findDisplayRoomFor(Course course) {
        // TASK-530b contract, enforced since the TASK-536 H2 root cause (20/08):
        // pseudo-teacher courses (the Stage stubs) keep room=null - Stage is
        // off-premises, null is the honest display (UI labels it STAGE). The
        // tag-less fallback once backfilled all 1993 stubs into one classroom,
        // an uncurable pinned H2 pile; history in the TASK-536 note.
        if (course == null || course.teacher == null || course.teacher.pseudo) {
            return null;
        }
        if (course.requiredRoomTags == null || course.requiredRoomTags.isEmpty()) {
            for (Room r : Room.<Room>listAll()) {
                if (r.isAssignable() && (r.tags == null || r.tags.isEmpty())) {
                    return r; // generic classroom: plausible display for an aula pin
                }
            }
            return null;
        }
        String tag = course.requiredRoomTags.get(0);
        for (Room r : Room.<Room>listAll()) {
            if (r.isAssignable() && r.tags != null && r.tags.contains(tag)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Single creation point for every synthetic accounting teacher (Stage,
     * Esame, RA - M11): finds by (name, pseudo=true) first so re-running the
     * import (even across a force=true reimport, which does not delete
     * Teachers) never creates a duplicate pseudo-teacher row.
     */
    private Teacher findOrCreatePseudoTeacher(String name) {
        Teacher t = Teacher.find("name = ?1 and pseudo = true", name).firstResult();
        if (t == null) {
            t = new Teacher(name);
            t.pseudo = true;
            t.persist();
        }
        return t;
    }

    private StudentGroup findGroupOrThrow(String groupName, String context) {
        StudentGroup group = StudentGroup.find("name", groupName).firstResult();
        if (group == null) {
            throw new IllegalStateException("Golden import: " + context + " row references unknown student group '" + groupName + "'");
        }
        return group;
    }

    /** Find-or-create guard (M11): re-running the import must not duplicate accounting Courses. */
    private Course findAccountingCourse(Teacher pseudoTeacher, String subject, StudentGroup group) {
        return Course.find("teacher = ?1 and subject = ?2 and studentGroup = ?3", pseudoTeacher, subject, group).firstResult();
    }

    private Course createAccountingCourse(String subject, Teacher pseudoTeacher, StudentGroup group, int totalHours) {
        Course course = new Course(subject, pseudoTeacher, group, totalHours);
        course.professionalizzanti = false;
        course.persist();
        return course;
    }

    private Course findOrCreateStageCourse(String groupName, Teacher stageTeacher) {
        StudentGroup group = findGroupOrThrow(groupName, STAGE_NAME);
        Course existing = findAccountingCourse(stageTeacher, STAGE_NAME, group);
        if (existing != null) {
            return existing;
        }
        // totalHours reconciled after Stage lessons are persisted, see reconcileStageCourseHours.
        return createAccountingCourse(STAGE_NAME, stageTeacher, group, 0);
    }

    /**
     * M2/M3 (design doc sections B/C): each incarichi.csv row under the given
     * pseudo-teacher (Esame, RA) becomes a synthetic per-group accounting
     * Course with zero scheduled Lessons - the subject and hours come from the
     * row itself - UNLESS real (non-pseudo) courses already FULLY cover the
     * same (subject, group): G04's 50h RA row vs T15's real 50h
     * "Recuperi ed Approfondimenti" course, the verified explanation of the
     * 1040-vs-990 anomaly (Q7). On the current seed this creates 6 Esame
     * courses and (since the TASK-592 cure removed the only RA pool row)
     * 0 RA courses.
     *
     * <p>TASK-366/348 code review (D5): coverage is the SUM of every matching
     * real course's totalHours, not just the existence of one - a single
     * partial match (e.g. one of G08's per-teacher RA micro-courses, see
     * {@link #createRaMicroCoursesAndLessons}) must not masquerade as full
     * coverage of the pool row. See the design doc for the worked example
     * (G04 50h fully covered vs. G08 31h partially covered against a 40h
     * pool).</p>
     */
    private int createAccountingCourses(List<String[]> incarichiRows, String pseudoTeacherName) {
        Teacher pseudoTeacher = findOrCreatePseudoTeacher(pseudoTeacherName);
        int created = 0;
        for (String[] row : incarichiRows) {
            if (!pseudoTeacherName.equals(row[0].trim())) continue;
            String subject = SubjectNormalization.normalize(row[1].trim());
            String groupName = row[2].trim();
            StudentGroup group = findGroupOrThrow(groupName, pseudoTeacherName);
            int poolHours = parseIncarichiHours(row);
            List<Course> realCourses = Course.list(
                "teacher is not null and teacher.pseudo = false and subject = ?1 and studentGroup = ?2",
                subject, group);
            int realCoverageHours = realCourses.stream().mapToInt(c -> c.totalHours).sum();
            if (!realCourses.isEmpty() && realCoverageHours >= poolHours) {
                LOG.infof("Golden import: skipping %s accounting course for %s - %d real course(s) fully cover "
                        + "'%s' (%dh >= %dh pool)",
                    pseudoTeacherName, groupName, realCourses.size(), subject, realCoverageHours, poolHours);
                continue;
            }
            if (findAccountingCourse(pseudoTeacher, subject, group) != null) continue; // M11: re-run guard
            createAccountingCourse(subject, pseudoTeacher, group, poolHours);
            created++;
        }
        return created;
    }

    private static int parseIncarichiHours(String[] incarichiRow) {
        return (int) Math.round(Double.parseDouble(incarichiRow[3].trim()));
    }

    /**
     * TASK-366 (design doc section C, pending school answer Q9): imports the
     * lessons.csv rows that categorize() routed to {@code raNoCourseRows}
     * (raw subject "RA" under a real teacher, no matching Course - on the
     * current seed, 41 rows across 9 real teachers, all G08; since the
     * TASK-592 cure the find-or-create reuses the 9 real incarichi courses
     * instead of creating micro-courses). Each distinct
     * (teacher, group) pair gets its own "Recuperi ed Approfondimenti"
     * micro-course (find-or-create, M11 re-run guard), sized to exactly the
     * rows it covers, and every row is pinned as a real Lesson under it -
     * instead of being silently dropped, which used to punch mid-day holes in
     * G08's schedule and fabricate 22 H17 vertical-compactness violations.
     * MUST run after {@link #createAccountingCourses} for RA_TEACHER (see
     * call site comment) so the pool accounting Course's find-or-create guard
     * doesn't mistake a micro-course for pool coverage.
     */
    private int createRaMicroCoursesAndLessons(Map<CourseKey, List<TimeslotKey>> raNoCourseRows,
                                               Map<TimeslotKey, Timeslot> timeslotIndex) {
        int imported = 0;
        for (Map.Entry<CourseKey, List<TimeslotKey>> entry : raNoCourseRows.entrySet()) {
            CourseKey key = entry.getKey();
            List<TimeslotKey> timeslotKeys = entry.getValue();
            Teacher teacher = findRealTeacherOrThrow(key.teacherName(), "RA micro-course");
            StudentGroup group = findGroupOrThrow(key.groupName(), "RA micro-course");
            Course microCourse = findAccountingCourse(teacher, RA_FULL_SUBJECT_NAME, group); // M11 re-run guard
            if (microCourse == null) {
                microCourse = createAccountingCourse(RA_FULL_SUBJECT_NAME, teacher, group, timeslotKeys.size());
            } else {
                // TASK-366 code review (D7): refresh totalHours to the current
                // row count - a re-run whose lessons.csv rows for this
                // (teacher, group) changed (or whose Lessons were deleted and
                // reimported at a different count) must not leave a stale
                // micro-course reporting a prior run's row count.
                microCourse.totalHours = timeslotKeys.size();
            }
            for (TimeslotKey timeslotKey : timeslotKeys) {
                persistPinnedLesson(microCourse, timeslotIndex.get(timeslotKey));
                imported++;
            }
        }
        return imported;
    }

    private Teacher findRealTeacherOrThrow(String teacherName, String context) {
        Teacher t = Teacher.find("name = ?1 and pseudo = false", teacherName).firstResult();
        if (t == null) {
            throw new IllegalStateException("Golden import: " + context + " row references unknown real teacher '" + teacherName + "'");
        }
        return t;
    }

    /**
     * Every student group that needs a synthetic Stage course: anyone with at
     * least one matched Stage row, a closure-day Stage row, or a
     * beyond-Timeslot-horizon Stage row (TASK-373 code review H3/H4). Pure
     * and DB-free (unlike {@link #findOrCreateStageCourse}), so a group whose
     * Stage rows fall ENTIRELY on closure days or entirely beyond the
     * horizon - and would therefore have zero {@code stageMatches} entries -
     * can be unit-tested directly against a hand-built {@link RowCategorization},
     * without needing a synthetic lessons.csv (golden import always reads the
     * real, fixed classpath resource - see the class javadoc).
     */
    static Set<String> stageGroupsNeedingCourse(RowCategorization cat) {
        Set<String> groups = new LinkedHashSet<>();
        for (StageMatch m : cat.stageMatches()) {
            groups.add(m.group());
        }
        groups.addAll(cat.stageOnClosureByGroup().keySet());
        groups.addAll(cat.stageBeyondHorizonByGroup().keySet());
        return groups;
    }

    /**
     * Set each synthetic Stage course's totalHours to the FULL grid total for
     * its group - open-day Stage rows actually pinned as Lessons, PLUS every
     * structurally unschedulable row: closure-day Stage rows (never pinned,
     * see the class javadoc / design doc section A - "do not generate
     * closure Timeslots") and open-day Stage rows dated beyond the Timeslot
     * horizon (code review H4, e.g. G04's 2027-06-11..14 rows) - so
     * {@code assigned == scheduled + expectedUnscheduledHours} reconciles
     * exactly, with no dependency on incarichi.csv's own Stage value
     * (TASK-373; design doc section A). {@link Course#expectedUnscheduledHours}
     * is set to exactly that unschedulable total so {@code ReconciliationService}
     * can subtract it from the raw delta before flagging an actionable
     * mismatch, instead of a frontend heuristic guessing at it.
     *
     * <p>Cross-checked (not sourced) against incarichi.csv: on the current
     * seed every group with an incarichi.csv Stage row has incarichi hours ==
     * grid total already (see {@code scripts/analyze/derive-golden-stage-on-closure.py}),
     * so this is a pure drift detector - logged AND returned as report-visible
     * warnings (code review H6) rather than log-only, non-blocking. G04
     * (the DUALE exception, no incarichi Stage row, see Q-STAGE-1/2) simply
     * has no value to compare against and is skipped.</p>
     *
     * @return one human-readable drift warning per group whose incarichi.csv
     *         Stage hours disagree with the grid total, for
     *         {@link GoldenImportReport#stageDriftWarnings}.
     */
    static List<String> reconcileStageCourseHours(Map<String, Course> stageCourseByGroup, RowCategorization cat,
                                           List<String[]> incarichiRows) {
        Map<String, Integer> incarichiStageHours = new HashMap<>();
        for (String[] row : incarichiRows) {
            if (!STAGE_NAME.equals(row[0].trim())) continue;
            incarichiStageHours.merge(row[2].trim(), parseIncarichiHours(row), Integer::sum);
        }

        List<String> driftWarnings = new ArrayList<>();
        for (Map.Entry<String, Course> entry : stageCourseByGroup.entrySet()) {
            String group = entry.getKey();
            Course stageCourse = entry.getValue();
            // Every Lesson under this course was persisted by this run (any
            // pre-existing Lessons were deleted or refused up front), so the
            // count IS the group's open-day match count.
            int openHours = (int) Lesson.count("course", stageCourse);
            int closureHours = cat.stageOnClosureByGroup().getOrDefault(group, 0);
            int beyondHorizonHours = cat.stageBeyondHorizonByGroup().getOrDefault(group, 0);
            int expectedUnscheduledHours = closureHours + beyondHorizonHours;
            int gridTotal = openHours + expectedUnscheduledHours;
            stageCourse.totalHours = gridTotal;
            stageCourse.expectedUnscheduledHours = expectedUnscheduledHours;

            Integer incarichiHours = incarichiStageHours.get(group);
            if (incarichiHours != null && !incarichiHours.equals(gridTotal)) {
                // Not logged here - buildReport() logs (and reports, code
                // review H6) every entry of the returned list once, so this
                // would otherwise double-log the same warning.
                driftWarnings.add(String.format(
                    "Stage totalHours for %s computed from the grid (%d = %d open + %d closure + %d "
                        + "beyond-horizon) disagrees with incarichi.csv's Stage row (%d) - data may have "
                        + "drifted since TASK-373, investigate",
                    group, gridTotal, openHours, closureHours, beyondHorizonHours, incarichiHours));
            }
        }
        return driftWarnings;
    }

    private GoldenImportReport buildReport(int totalRows, RowCategorization cat, int esameCoursesCreated,
                                           int raCoursesCreated, int raRowsImported, long deletedPinned,
                                           long deletedUnpinned, List<String> stageDriftWarnings) {
        List<GoldenImportReport.UnmatchedCourseTriple> triples = new ArrayList<>();
        for (Map.Entry<CourseKey, Integer> e : cat.unmatchedNoCourseCounts().entrySet()) {
            triples.add(new GoldenImportReport.UnmatchedCourseTriple(
                e.getKey().teacherName(), e.getKey().subject(), e.getKey().groupName(), e.getValue()));
        }
        triples.sort((a, b) -> Integer.compare(b.rowCount, a.rowCount));

        GoldenImportReport report = new GoldenImportReport(
            totalRows,
            cat.realMatches().size() + raRowsImported,
            cat.stageMatches().size(),
            cat.skippedEsame(),
            cat.skippedClosed(),
            cat.stageOnClosureByGroup(),
            cat.stageBeyondHorizonByGroup(),
            cat.unmatchedNoTimeslot(),
            triples,
            esameCoursesCreated,
            raCoursesCreated,
            deletedPinned,
            deletedUnpinned,
            stageDriftWarnings);

        LOG.infof("Golden import complete: rows=%d imported=%d importedStage=%d skippedEsame=%d skippedClosed=%d "
                + "stageOnClosure=%d stageBeyondHorizon=%d unmatchedNoTimeslot=%d unmatchedNoCourseRows=%d "
                + "(%d distinct triples) esameCoursesCreated=%d raCoursesCreated=%d raRowsImported=%d "
                + "deletedPinned=%d deletedUnpinned=%d stageDriftWarnings=%d",
            report.rows, report.imported, report.importedStage, report.skippedEsame, report.skippedClosed,
            report.getStageOnClosure(), report.getStageBeyondHorizon(), report.unmatchedNoTimeslot,
            report.getUnmatchedNoCourseRows(), triples.size(), report.esameCoursesCreated, report.raCoursesCreated,
            raRowsImported, report.deletedPinned, report.deletedUnpinned, stageDriftWarnings.size());
        for (Map.Entry<String, Integer> e : cat.stageOnClosureByGroup().entrySet()) {
            LOG.infof("  stage-on-closure: group='%s' rows=%d", e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : cat.stageBeyondHorizonByGroup().entrySet()) {
            LOG.infof("  stage-beyond-horizon: group='%s' rows=%d", e.getKey(), e.getValue());
        }
        for (GoldenImportReport.UnmatchedCourseTriple t : triples) {
            LOG.infof("  unmatched-no-course: teacher='%s' subject='%s' group='%s' rows=%d",
                t.teacher, t.subject, t.group, t.rowCount);
        }
        for (String warning : stageDriftWarnings) {
            LOG.warnf("  stage-drift: %s", warning);
        }
        return report;
    }

    /** Static, package-visible so {@link CalendarioImportService} (TASK-364) can reuse it instead of re-declaring it. */
    static Map<TimeslotKey, Timeslot> buildTimeslotIndex() {
        Map<TimeslotKey, Timeslot> index = new HashMap<>();
        for (Timeslot ts : Timeslot.<Timeslot>listAll()) {
            index.put(new TimeslotKey(ts.getDate(), ts.startDateTime.toLocalTime()), ts);
        }
        return index;
    }

    private Map<CourseKey, Course> buildCourseIndex() {
        Map<CourseKey, Course> index = new HashMap<>();
        List<Course> courses = Course.list("teacher is not null and studentGroup is not null");
        for (Course c : courses) {
            CourseKey key = new CourseKey(c.teacher.name, c.subject, c.studentGroup.name);
            Course previous = index.putIfAbsent(key, c);
            if (previous != null) {
                LOG.warnf("Golden import: duplicate Course for key %s (course ids %d and %d) - keeping the first",
                    key, previous.id, c.id);
            }
        }
        return index;
    }

    // ─── Pure categorization (no DB access) - shared by production and the
    // dry-run CSV test so the two can never silently drift apart. ──────────

    /**
     * Categorize every lessons.csv row into exactly one bucket, in this
     * precedence order:
     * <ol>
     *   <li>closed="True" and teacher="Stage" -&gt; stageOnClosure, keyed by group (TASK-373)</li>
     *   <li>closed="True" (otherwise) or blank teacher -&gt; skippedClosed</li>
     *   <li>teacher="Esame" -&gt; skippedEsame</li>
     *   <li>teacher="Stage": matching Timeslot -&gt; stage match; no matching
     *       Timeslot -&gt; stageBeyondHorizon, keyed by group (code review H4)</li>
     *   <li>no matching Timeslot for (date, slot-start-time) -&gt; unmatchedNoTimeslot</li>
     *   <li>real-teacher row: (teacher, normalized subject, group) matches a known Course -&gt; real match</li>
     *   <li>subject="RA", no known Course -&gt; raNoCourseRows, keyed by (teacher, "RA", group) (TASK-366)</li>
     *   <li>otherwise -&gt; unmatchedNoCourse, keyed by (teacher, normalized subject, group)</li>
     * </ol>
     * The bucket counts always sum to {@code rows.size()}.
     *
     * <p><b>TASK-373 (stageOnClosure):</b> a Stage row on a closure day is a
     * legitimate, school-confirmed stage hour ("lo stage puo essere fatto
     * anche nei giorni di chiusura", referente Q8) - not a skipped row. It is
     * counted per group but never turned into a pinned Lesson: no Timeslot
     * exists on a closure day and none is generated for one (see the class
     * javadoc / design doc section A, "why not generate closure Timeslots") -
     * these rows only affect the synthetic Stage course's totalHours (see
     * {@link #reconcileStageCourseHours}), not what gets scheduled.</p>
     *
     * <p><b>Code review H4 (stageBeyondHorizon):</b> an open-day (closed=False)
     * Stage row with no matching Timeslot - e.g. G04's rows dated
     * 2027-06-11..14, past {@code TimeslotSequence.SCHOOL_YEAR_END} - is
     * structurally unschedulable for the exact same reason a closure-day row
     * is: no Timeslot exists and none is generated for one. Routing it into
     * the generic {@code unmatchedNoTimeslot} bucket (shared with every other
     * subject) would silently drop it from the Stage course's accounting;
     * keeping it Stage-specific and per-group lets it widen
     * {@code expectedUnscheduledHours} exactly like stageOnClosure does. This
     * is why the Stage check now runs BEFORE the generic timeslot-existence
     * check below, instead of after it.</p>
     */
    static RowCategorization categorize(List<String[]> rows, Predicate<TimeslotKey> timeslotExists,
                                        Set<CourseKey> knownCourseKeys) {
        List<RealMatch> realMatches = new ArrayList<>();
        List<StageMatch> stageMatches = new ArrayList<>();
        int skippedEsame = 0;
        int skippedClosed = 0;
        int unmatchedNoTimeslot = 0;
        Map<CourseKey, Integer> unmatchedNoCourse = new LinkedHashMap<>();
        Map<CourseKey, List<TimeslotKey>> raNoCourseRows = new LinkedHashMap<>();
        Map<String, Integer> stageOnClosureByGroup = new LinkedHashMap<>();
        Map<String, Integer> stageBeyondHorizonByGroup = new LinkedHashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            String closed = row[COL_CLOSED].trim();
            // TASK-346 code review (M10): fail loud on anything but the exact
            // two values lessons.csv uses - a silent-fallback interpretation
            // (e.g. treating "TRUE"/"true"/typos as "not closed") would let a
            // future re-extraction quietly mis-schedule rows instead of erroring.
            if (!"True".equals(closed) && !"False".equals(closed)) {
                throw new RuntimeException("Golden import: lessons.csv line " + (i + 2)
                        + " (1-based, header is line 1) has invalid closed value '" + closed
                        + "' - expected exactly 'True' or 'False'");
            }
            String teacher = row[COL_TEACHER].trim();

            if ("True".equals(closed) && STAGE_NAME.equals(teacher)) {
                stageOnClosureByGroup.merge(row[COL_GROUP].trim(), 1, Integer::sum);
                continue;
            }
            if ("True".equals(closed) || teacher.isEmpty()) {
                skippedClosed++;
                continue;
            }
            if (ESAME_TEACHER.equals(teacher)) {
                skippedEsame++;
                continue;
            }

            LocalDate date = LocalDate.parse(row[COL_DATE].trim());
            LocalTime startTime = parseSlotStartTime(row[COL_SLOT].trim());
            TimeslotKey timeslotKey = new TimeslotKey(date, startTime);
            String group = row[COL_GROUP].trim();

            if (STAGE_NAME.equals(teacher)) {
                if (timeslotExists.test(timeslotKey)) {
                    stageMatches.add(new StageMatch(group, timeslotKey));
                } else {
                    stageBeyondHorizonByGroup.merge(group, 1, Integer::sum);
                }
                continue;
            }

            if (!timeslotExists.test(timeslotKey)) {
                unmatchedNoTimeslot++;
                continue;
            }

            String subject = SubjectNormalization.normalize(row[COL_SUBJECT].trim());
            CourseKey courseKey = new CourseKey(teacher, subject, group);
            if (knownCourseKeys.contains(courseKey)) {
                realMatches.add(new RealMatch(courseKey, timeslotKey));
            } else if (RA_ROW_SUBJECT.equals(subject)) {
                raNoCourseRows.computeIfAbsent(courseKey, k -> new ArrayList<>()).add(timeslotKey);
            } else {
                unmatchedNoCourse.merge(courseKey, 1, Integer::sum);
            }
        }

        return new RowCategorization(realMatches, stageMatches, skippedEsame, skippedClosed,
            unmatchedNoTimeslot, unmatchedNoCourse, raNoCourseRows, stageOnClosureByGroup, stageBeyondHorizonByGroup);
    }

    /** {@code "08:00 09:00"} -&gt; {@code 08:00} (only the start time is needed for the Timeslot lookup). */
    static LocalTime parseSlotStartTime(String slot) {
        int spaceIdx = slot.indexOf(' ');
        String startPart = spaceIdx >= 0 ? slot.substring(0, spaceIdx) : slot;
        return LocalTime.parse(startPart);
    }

    // ─── Value types ────────────────────────────────────────────────────

    record TimeslotKey(LocalDate date, LocalTime startTime) {}

    record CourseKey(String teacherName, String subject, String groupName) {}

    record RealMatch(CourseKey courseKey, TimeslotKey timeslotKey) {}

    record StageMatch(String group, TimeslotKey timeslotKey) {}

    record RowCategorization(List<RealMatch> realMatches, List<StageMatch> stageMatches, int skippedEsame,
                             int skippedClosed, int unmatchedNoTimeslot, Map<CourseKey, Integer> unmatchedNoCourseCounts,
                             Map<CourseKey, List<TimeslotKey>> raNoCourseRows,
                             Map<String, Integer> stageOnClosureByGroup,
                             Map<String, Integer> stageBeyondHorizonByGroup) {}
}
