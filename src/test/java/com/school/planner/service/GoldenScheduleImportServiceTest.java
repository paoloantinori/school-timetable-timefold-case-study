package com.school.planner.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.GoldenScheduleImportService.RowCategorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link GoldenScheduleImportService} against a SMALL,
 * hand-built fixture (2 real dates worth of Timeslots + a couple of real
 * Courses via {@link com.school.planner.TestDataBuilder}-style direct
 * persistence) - NOT the full DataGenerator seed (disabled in tests, see
 * application.properties).
 *
 * <p>{@code GoldenScheduleImportService} always reads the real, fixed
 * {@code src/main/resources/seed/lessons.csv} (12,979 rows) - there is no
 * test-injectable CSV. So this fixture doesn't invent synthetic rows; it
 * cherry-picks a handful of REAL rows from that file (verified against the
 * CSV directly, see the comments on each fixture) and seeds just enough
 * Timeslot/Course/StudentGroup/Teacher data for those specific rows to
 * resolve one way or another. Every other one of the 12,979 rows necessarily
 * falls into skippedClosed/skippedEsame (constant, fixture-independent) or
 * unmatchedNoTimeslot (no Timeslot exists for almost the whole school year
 * in this tiny fixture) - that's expected and asserted as an invariant
 * (bucket counts partition all rows) rather than a magic number.</p>
 */
@QuarkusTest
class GoldenScheduleImportServiceTest {

    @Inject
    GoldenScheduleImportService importService;

    @Inject
    EntityManager em;

    // Real row: 2026-09-14,Lunedì,09:00 10:00,False,G03,T02,Orientamento
    private static final LocalDateTime MATCHED_ROW_START = LocalDateTime.of(2026, 9, 14, 9, 0);
    // Real row: 2026-09-14,Lunedì,10:00 11:00,False,G03,T23,Economia e Diritto del lavoro
    // (deliberately NOT given a Course - exercises unmatchedNoCourse)
    private static final LocalDateTime UNMATCHED_COURSE_ROW_START = LocalDateTime.of(2026, 9, 14, 10, 0);
    // Real row: 2026-11-20,Venerdì,08:00 09:00,False,G12,Stage,Stage
    private static final LocalDateTime STAGE_ROW_START = LocalDateTime.of(2026, 11, 20, 8, 0);

    @BeforeEach
    @Transactional
    void cleanDatabase() {
        // Full FK-respecting wipe (not just what this test creates): the
        // shared H2 instance (jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1) can carry
        // leftover Teacher/StudentGroup rows - and their dependents - from
        // whatever test class ran before this one in the same suite.
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM LabAssistant").executeUpdate();
        em.createQuery("DELETE FROM LabRotation").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM ProfessionalizzantiDay").executeUpdate();
        em.createQuery("DELETE FROM StagePattern").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
    }

    @Transactional
    void seedSmallFixture() {
        new Timeslot(MATCHED_ROW_START, MATCHED_ROW_START.plusHours(1)).persist();
        new Timeslot(UNMATCHED_COURSE_ROW_START, UNMATCHED_COURSE_ROW_START.plusHours(1)).persist();
        new Timeslot(STAGE_ROW_START, STAGE_ROW_START.plusHours(1)).persist();

        StudentGroup fb = new StudentGroup("G03", 20);
        fb.persist();
        new StudentGroup("G12", 20).persist();
        // The real incarichi.csv (not test-injectable, see class javadoc) has
        // Esame/RA pseudo-teacher rows for these groups too (M2/M3) -
        // createAccountingCourses() iterates ALL of incarichi.csv's Esame/RA
        // rows unconditionally, so every group they reference must exist or
        // the import throws (mirrors production, where DataGenerator always
        // seeds every real StudentGroup first).
        new StudentGroup("G11", 20).persist();
        new StudentGroup("G08", 20).persist();
        new StudentGroup("G10", 20).persist();
        new StudentGroup("G09", 20).persist();
        new StudentGroup("G13", 20).persist();
        StudentGroup FC1 = new StudentGroup("G04", 20);
        FC1.persist();
        // Code review H4: with only 3 Timeslots seeded in this fixture,
        // almost every real Stage row across the whole year (not just
        // closure-day ones) has no matching Timeslot and now lands in
        // stageBeyondHorizonByGroup instead of the generic
        // unmatchedNoTimeslot bucket - findOrCreateStageCourse() is then
        // called for every group with a lessons.csv Stage row, including
        // "G07" (295 open, 0 closed - see the design doc's stage-on-closure
        // table), which none of this fixture's other groups already cover.
        new StudentGroup("G07", 20).persist();

        Teacher t02 = new Teacher("T02");
        t02.persist();
        new Course("Orientamento", t02, fb, 10).persist();
        // Deliberately no Course for (T23, "Economia e Diritto del lavoro", G03).

        // Mirrors production (DataGenerator seeds all real courses before any
        // golden import runs): T15's real G04 "Recuperi ed
        // Approfondimenti" course is what makes incarichi's G04 RA pool row
        // a duplicate, so the import must skip the G04 RA accounting stub
        // (design doc section C). None of T15's lessons.csv rows fall on
        // this fixture's three timeslots, so report.imported stays unaffected.
        Teacher t15 = new Teacher("T15");
        t15.persist();
        new Course("Recuperi ed Approfondimenti", t15, FC1, 50).persist();

        // TASK-366: this fixture's 3 global Timeslots incidentally also match
        // two real lessons.csv rows for G08 with the raw subject abbreviation
        // "RA" (2026-09-14,09:00,G08,T11,RA and 2026-09-14,10:00,G08,De
        // Lisi,RA - Timeslots aren't group-specific, so any row at these exact
        // date/times resolves regardless of group). Neither teacher has a
        // Course seeded here - in PRODUCTION (TASK-592 cure) they do (T11 1h,
        // T10 5h real Recuperi rows for G08 in incarichi.csv), so the RA
        // fallback's find-or-create reuses those real courses there; this
        // fixture deliberately withholds them to keep exercising the
        // per-teacher micro-course creation the fallback performs when no real
        // course exists.
        new Teacher("T11").persist();
        new Teacher("T10").persist();
    }

    @Test
    void importsRealRowAndPinsLesson() {
        seedSmallFixture();

        GoldenImportReport report = importService.importGolden(false);

        // 1 real T02/Orientamento row + 2 TASK-366 RA micro-course rows
        // (T11, T10 - see seedSmallFixture()'s comment) = 3.
        assertEquals(3, report.imported, "real row plus the two RA micro-course rows this fixture incidentally matches");
        Lesson lesson = Lesson.<Lesson>find("course.subject = ?1 and course.teacher.name = ?2",
                "Orientamento", "T02").firstResult();
        assertNotNull(lesson, "the matched row must be persisted as a Lesson");
        assertTrue(lesson.pinned, "golden-imported lessons must be pinned");
        assertNull(lesson.room, "the golden grid has no room column - room must stay null");
        assertEquals(MATCHED_ROW_START, lesson.timeslot.startDateTime);
    }

    // TASK-366: lessons.csv rows with raw subject "RA" under a real teacher and
    // no matching Course get their own per-teacher "Recuperi ed Approfondimenti"
    // micro-course instead of being dropped as unmatchedNoCourse.
    @Test
    void createsRaMicroCourseForUnmatchedRaRow() {
        seedSmallFixture();

        GoldenImportReport report = importService.importGolden(false);

        Teacher t11 = Teacher.find("name = ?1 and pseudo = false", "T11").firstResult();
        assertNotNull(t11);
        Course microCourse = Course.find("teacher = ?1 and subject = ?2 and studentGroup.name = ?3",
                t11, "Recuperi ed Approfondimenti", "G08").firstResult();
        assertNotNull(microCourse, "T11 must get his own G08 RA micro-course");
        assertEquals(1, microCourse.totalHours, "totalHours must equal the row count this teacher covers");
        assertEquals(false, microCourse.professionalizzanti);

        Lesson lesson = Lesson.<Lesson>find("course = ?1", microCourse).firstResult();
        assertNotNull(lesson, "the RA row must be pinned as a real Lesson, not dropped");
        assertTrue(lesson.pinned);
        assertNull(lesson.room);

        // TASK-592 cure (11/09): with the RA pool row gone from incarichi.csv the
        // micro-courses remain the fallback for a fixture/sheet lacking real
        // coverage, and NO pool accounting Course is created alongside them.
        assertEquals(0, report.raCoursesCreated, "no pool accounting Course without an RA pool row");
        assertEquals(0, Course.count("teacher.name = ?1", "RA"),
                "no Course may hang off the pseudo 'RA' teacher");
    }

    @Test
    void createsSyntheticStageCourseAndPinsStageLesson() {
        seedSmallFixture();

        GoldenImportReport report = importService.importGolden(false);

        assertEquals(1, report.importedStage, "exactly one Stage row resolves in this fixture");
        Teacher stageTeacher = Teacher.find("name", "Stage").firstResult();
        assertNotNull(stageTeacher, "a pseudo-teacher 'Stage' must be created lazily");
        Course stageCourse = Course.find("teacher = ?1 and studentGroup.name = ?2", stageTeacher, "G12").firstResult();
        assertNotNull(stageCourse, "a synthetic per-group Stage course must be created lazily");
        assertEquals("Stage", stageCourse.subject);
        assertEquals(false, stageCourse.professionalizzanti);
        // TASK-373 (code review H4): totalHours = matched-open Stage rows +
        // expectedUnscheduledHours (closure-day rows + beyond-Timeslot-horizon
        // rows), NOT incarichi.csv (see reconcileStageCourseHours's javadoc -
        // incarichi is now only a drift cross-check). This tiny fixture seeds
        // exactly ONE Timeslot that a G12 Stage row matches
        // (STAGE_ROW_START), so every one of G12's other 111 "open" CSV
        // rows (112 total minus the 1 match) has no matching Timeslot in this
        // fixture and is now counted as beyond-horizon, same class as the 183
        // real closure-day rows - both are structurally unschedulable here.
        // 1 matched + (183 closure + 111 beyond-horizon) = 295, which happens
        // to equal G12's full lessons.csv grid total (112 open + 183
        // closed) precisely because only one row resolves in this fixture.
        assertEquals(294, stageCourse.expectedUnscheduledHours,
                "expectedUnscheduledHours = 183 real closure-day rows + 111 beyond-horizon rows (112 open CSV "
                        + "rows for G12 minus the 1 this fixture's only Timeslot matches)");
        assertEquals(295, stageCourse.totalHours,
                "totalHours = 1 matched-open Stage row (this fixture's only matching Timeslot) + 294 expectedUnscheduledHours");

        Lesson stageLesson = Lesson.<Lesson>find("course = ?1", stageCourse).firstResult();
        assertNotNull(stageLesson);
        assertTrue(stageLesson.pinned);
        assertNull(stageLesson.room);
        assertEquals(STAGE_ROW_START, stageLesson.timeslot.startDateTime);
    }

    // TASK-373 code review H3: a group whose Stage rows fall ENTIRELY on
    // closure days (or entirely beyond the Timeslot horizon) has zero
    // stageMatches entries - stageGroupsNeedingCourse() must still surface it
    // so importGolden() creates a Stage course for it, not just for groups
    // with at least one matched row. None observed on the current real seed
    // (see the design doc's stage-on-closure table), so this is exercised
    // with a hand-built RowCategorization rather than the fixed lessons.csv
    // (golden import always reads that real classpath resource - no
    // test-injectable CSV, see the class javadoc).
    @Test
    void stageGroupsNeedingCourseIncludesClosureOnlyAndBeyondHorizonOnlyGroups() {
        RowCategorization cat = stageOnlyCategorization(
                Map.of("CLOSURE ONLY GROUP", 5), Map.of("BEYOND HORIZON ONLY GROUP", 3));

        Set<String> groups = GoldenScheduleImportService.stageGroupsNeedingCourse(cat);

        assertTrue(groups.contains("CLOSURE ONLY GROUP"),
                "a group with zero matched Stage rows but a closure-day row must still need a course");
        assertTrue(groups.contains("BEYOND HORIZON ONLY GROUP"),
                "a group with zero matched Stage rows but a beyond-horizon row must still need a course");
        assertEquals(2, groups.size());
    }

    // TASK-373 code review H3: exercises reconcileStageCourseHours' formula
    // directly for a synthetic closure-only group (zero matched/pinned Stage
    // Lessons, only a closure-day count) - the scenario
    // stageGroupsNeedingCourse makes reachable but that does not occur in the
    // current real seed data.
    @Test
    @Transactional
    void reconcileStageCourseHoursHandlesClosureOnlyGroupWithZeroPinnedLessons() {
        Course stageCourse = persistBareStageCourse("CLOSURE ONLY GROUP");
        RowCategorization cat = stageOnlyCategorization(Map.of("CLOSURE ONLY GROUP", 5), Map.of());

        List<String> warnings = GoldenScheduleImportService.reconcileStageCourseHours(
                Map.of("CLOSURE ONLY GROUP", stageCourse), cat, List.of());

        assertEquals(5, stageCourse.totalHours, "totalHours must equal closureHours when the group has zero matched Stage Lessons");
        assertEquals(5, stageCourse.expectedUnscheduledHours);
        assertEquals(0, Lesson.count("course", stageCourse), "a closure-only group must have zero pinned Lessons");
        assertTrue(warnings.isEmpty(), "no incarichi.csv Stage row exists for this synthetic group, so there is nothing to drift-check");
    }

    // TASK-373 code review H6: the incarichi-vs-grid drift check must be
    // report-visible (stageDriftWarnings), not log-only.
    @Test
    @Transactional
    void reconcileStageCourseHoursReportsDriftWhenIncarichiDisagreesWithGrid() {
        Course stageCourse = persistBareStageCourse("DRIFT GROUP");
        RowCategorization cat = stageOnlyCategorization(Map.of("DRIFT GROUP", 5), Map.of());
        // incarichi.csv claims 999h of Stage for this group, but the grid only totals 5 (0 open + 5 closure).
        List<String[]> incarichiRows = List.<String[]>of(new String[]{"Stage", "Stage", "DRIFT GROUP", "999"});

        List<String> warnings = GoldenScheduleImportService.reconcileStageCourseHours(
                Map.of("DRIFT GROUP", stageCourse), cat, incarichiRows);

        assertEquals(1, warnings.size(), "a mismatching incarichi.csv Stage row must produce exactly one drift warning");
        assertTrue(warnings.get(0).contains("DRIFT GROUP"));
        assertTrue(warnings.get(0).contains("999"));
        assertEquals(5, stageCourse.totalHours, "totalHours is still computed from the grid, not incarichi, even when they disagree");
    }

    /**
     * A bare synthetic Stage pseudo-teacher/Course for the given group,
     * outside the fixed lessons.csv (TASK-373 code review H3/H6 tests
     * exercise {@code reconcileStageCourseHours} directly against a
     * hand-built {@link RowCategorization}, not a full {@code importGolden()}
     * run, so no real Stage course exists yet to reuse).
     */
    @Transactional
    Course persistBareStageCourse(String groupName) {
        StudentGroup group = new StudentGroup(groupName, 20);
        group.persist();
        Teacher stageTeacher = new Teacher("Stage");
        stageTeacher.pseudo = true;
        stageTeacher.persist();
        Course stageCourse = new Course("Stage", stageTeacher, group, 0);
        stageCourse.persist();
        return stageCourse;
    }

    /** A {@link RowCategorization} with only the Stage closure/beyond-horizon buckets populated. */
    private RowCategorization stageOnlyCategorization(Map<String, Integer> stageOnClosureByGroup,
                                                       Map<String, Integer> stageBeyondHorizonByGroup) {
        return new RowCategorization(List.of(), List.of(), 0, 0, 0, Map.of(), Map.of(),
                stageOnClosureByGroup, stageBeyondHorizonByGroup);
    }

    @Test
    void countsUnmatchedNoCourseRowsWithTriple() {
        seedSmallFixture();

        GoldenImportReport report = importService.importGolden(false);

        assertTrue(report.getUnmatchedNoCourseRows() >= 1, "the T23 row has a Timeslot but no Course");
        boolean foundTriple = report.unmatchedNoCourseTriples.stream().anyMatch(t ->
                "T23".equals(t.teacher) && "Economia e Diritto del lavoro".equals(t.subject) && "G03".equals(t.group));
        assertTrue(foundTriple, "unmatched triple for T23/Economia e Diritto del lavoro/G03 must be reported");
    }

    @Test
    void unmatchedNoTimeslotAndBucketCountsPartitionAllRows() {
        seedSmallFixture();

        GoldenImportReport report = importService.importGolden(false);

        // Global, fixture-independent (checked before any DB lookup): closed=True
        // (excluding stage-on-closure, TASK-373) or blank-teacher rows, and
        // Esame rows (never scheduled in the golden grid).
        assertEquals(117, report.skippedClosed, "428 total closed/blank-teacher rows minus 311 stage-on-closure");
        assertEquals(0, report.skippedEsame);

        // Most of the school year has no Timeslot in this tiny fixture -
        // every row not otherwise accounted for must land in unmatchedNoTimeslot.
        // Code review H4: stageBeyondHorizonByGroup must be included too -
        // with only 3 Timeslots seeded, almost every real Stage row (not just
        // closure-day ones) has no matching Timeslot and is now diverted into
        // that bucket instead of the generic one.
        int accountedFor = report.imported + report.importedStage + report.skippedEsame
                + report.skippedClosed + report.getStageOnClosure() + report.getStageBeyondHorizon()
                + report.getUnmatchedNoCourseRows();
        assertEquals(report.rows - accountedFor, report.unmatchedNoTimeslot,
                "bucket counts must partition all rows");
        assertTrue(report.unmatchedNoTimeslot > 10000,
                "most of the 12,979 rows still have no Timeslot in this fixture, Stage rows now counted separately");
    }

    // TASK-373: referente (Q8) confirmed stage-on-closure days (e.g. G08's 24
    // Easter-break rows) are real, school-confirmed stage hours, not skipped
    // ones - this bucket is fixture-independent (derived purely from
    // lessons.csv's own closed/teacher columns, before any DB Timeslot
    // lookup), so it's exercised here directly against the real CSV rather
    // than a hand-typed literal per group.
    @Test
    void reportsStageOnClosurePerGroupDerivedFromRealCsv() throws IOException {
        seedSmallFixture();

        GoldenImportReport report = importService.importGolden(false);

        Map<String, Integer> expected = RawLessonsCsv.stageOnClosureByGroup(RawLessonsCsv.readRawLines());
        assertEquals(expected, report.stageOnClosureByGroup,
                "stage-on-closure rows per group must match an independent derivation from lessons.csv");
        assertEquals(expected.values().stream().mapToInt(Integer::intValue).sum(), report.getStageOnClosure());
        // Mechanically verified WHY-section numbers (2026-07-17, TASK-373 WHY).
        assertEquals(24, expected.get("G08"), "G08's Easter-break (2027-03-25/26/30) stage rows");
        assertEquals(183, expected.get("G12"));
        assertEquals(16, expected.get("G04"));
        assertEquals(8, expected.get("G09"));
        assertEquals(72, expected.get("G10"));
        assertEquals(8, expected.get("G11"));
    }

    @Test
    void createsEsameAccountingCoursesAndNoRaStub() {
        seedSmallFixture();

        GoldenImportReport report = importService.importGolden(false);

        // incarichi.csv: Esame,Esame,G12,20.0 / Esame,Esame,G11,24.0 / ...
        // 6 groups total have an Esame row on the current seed.
        assertEquals(6, report.esameCoursesCreated, "one Esame accounting Course per incarichi.csv Esame row");
        Teacher esameTeacher = Teacher.find("name = ?1 and pseudo = true", "Esame").firstResult();
        assertNotNull(esameTeacher, "a pseudo-teacher 'Esame' must be created");
        Course esameCourse = Course.find("teacher = ?1 and studentGroup.name = ?2", esameTeacher, "G12").firstResult();
        assertNotNull(esameCourse);
        assertEquals(20, esameCourse.totalHours, "totalHours must come from incarichi.csv's Esame row for G12");
        assertEquals(false, esameCourse.professionalizzanti);
        assertEquals(0, Lesson.count("course", esameCourse), "Esame must never have scheduled Lessons");

        // TASK-592 cure (11/09): the G08 RA pool row no longer exists in
        // incarichi.csv (replaced by 9 real per-teacher rows matching the golden's
        // own split), so the import creates NO RA accounting Course anywhere.
        assertEquals(0, report.raCoursesCreated, "no RA accounting Course without an RA pool row");
        assertEquals(0, Course.count("teacher.name = ?1", "RA"),
                "no Course may hang off the pseudo 'RA' teacher (the pool has real courses)");
    }

    // TASK-366 code review (D5): a real course that only PARTIALLY covers the
    // pool row's hours must NOT be treated as "the pool row duplicates a real
    // course" - the old check declared a duplicate on the mere EXISTENCE of
    // any one matching real course, regardless of its hours, so a 5h partial
    // course would have wrongly masqueraded as full coverage of the 20h pool
    // and silently dropped the accounting Course (and the other 15h from
    // reconciliation). TASK-592 cure (11/09): the RA pool row no longer exists
    // in incarichi.csv, so the guard is pinned on the Esame branch - the SAME
    // createAccountingCourses code every pseudo-keyed pool row flows through.
    @Test
    void esameAccountingCourseStillCreatedWhenRealCoverageIsPartial() {
        seedSmallFixture();
        seedPartialEsameCoverageFor4FD();

        importService.importGolden(false);

        Teacher esameTeacher = Teacher.find("name = ?1 and pseudo = true", "Esame").firstResult();
        Course poolCourse = Course.find("teacher = ?1 and studentGroup.name = ?2", esameTeacher, "G12").firstResult();
        assertNotNull(poolCourse, "pool accounting Course must not be mistaken for fully covered by a partial real course");
        assertEquals(20, poolCourse.totalHours, "5h of partial real coverage does not meet the 20h pool");
    }

    @Transactional
    void seedPartialEsameCoverageFor4FD() {
        StudentGroup FD4 = StudentGroup.find("name", "G12").firstResult();
        Teacher partialCoverageTeacher = new Teacher("PartialCoverageTeacher");
        partialCoverageTeacher.persist();
        new Course("Esame", partialCoverageTeacher, FD4, 5).persist();
    }

    // TASK-366 code review (D7): a stale-but-already-existing RA micro-course
    // (found via the M11 re-run guard) must have its totalHours refreshed to
    // the current row count, not left at whatever a prior run set it to.
    @Test
    void raMicroCourseTotalHoursRefreshedOnReimport() {
        seedSmallFixture();
        importService.importGolden(false);
        corruptT11MicroCourseTotalHours(999);

        importService.importGolden(true);

        Course refreshed = Course.<Course>find(
                "teacher.name = ?1 and subject = ?2 and studentGroup.name = ?3",
                "T11", "Recuperi ed Approfondimenti", "G08").firstResult();
        assertNotNull(refreshed);
        assertEquals(1, refreshed.totalHours,
                "totalHours must be refreshed to the current row count on reimport, not left stale");
    }

    @Transactional
    void corruptT11MicroCourseTotalHours(int staleHours) {
        Course microCourse = Course.<Course>find(
                "teacher.name = ?1 and subject = ?2", "T11", "Recuperi ed Approfondimenti").firstResult();
        microCourse.totalHours = staleHours;
    }

    @Test
    void reportCountsDeletedLessonsOnlyOnForceReimport() {
        seedSmallFixture();

        GoldenImportReport first = importService.importGolden(false);
        assertEquals(0, first.deletedPinned, "nothing existed yet on the first import");
        assertEquals(0, first.deletedUnpinned);

        long pinnedBeforeSecondImport = Lesson.count("pinned", true);
        GoldenImportReport second = importService.importGolden(true);
        assertEquals(pinnedBeforeSecondImport, second.deletedPinned,
                "force=true must report exactly how many pinned Lessons it deleted");
        assertEquals(0, second.deletedUnpinned, "the golden import only ever creates pinned Lessons");
    }

    @Test
    void refusesReimportWithoutForce() {
        seedSmallFixture();
        importService.importGolden(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> importService.importGolden(false));
        assertTrue(ex.getMessage().contains("force=true"), "error must point at the force flag");
    }

    @Test
    void forceReimportClearsExistingLessonsAndReimportsCleanly() {
        seedSmallFixture();
        GoldenImportReport first = importService.importGolden(false);
        long firstLessonCount = Lesson.count();

        GoldenImportReport second = importService.importGolden(true);

        assertEquals(first.imported, second.imported);
        assertEquals(first.importedStage, second.importedStage);
        assertEquals(firstLessonCount, Lesson.count(), "force re-import must not duplicate lessons");
    }
}
