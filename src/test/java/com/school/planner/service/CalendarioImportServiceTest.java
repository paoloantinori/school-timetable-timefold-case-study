package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
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
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.testsupport.ModaCourseFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CalendarioImportService} (TASK-364).
 *
 * <p>{@code CalendarioImportService} always reads the real, fixed
 * {@code src/main/resources/seed/calendario_moda.csv} (313 rows) - there is no
 * test-injectable CSV, same constraint as {@code GoldenScheduleImportServiceTest}.
 * Rather than a tiny hand-picked fixture (which would drown every test in
 * "no Timeslot" noise for the ~310 other rows it doesn't seed), this class
 * seeds the FULL school year's Timeslots plus every real (teacher, group)
 * MODA lab course, mirroring production - the real data then does the rest:
 * it contains exactly one genuine same-day mixed-shift conflict
 * (2027-06-09, G05 T16/T17 - see
 * {@code CalendarioImportService#computeSlotRange}'s javadoc), which is what
 * every "all-or-nothing" assertion below relies on.</p>
 *
 * <p>The pure shift-to-slot mapping ({@code computeSlotRange}) and the
 * teacher-initial mapping ({@code resolveTeacherName}) are exercised directly
 * as static methods - no DB needed, and calendario_moda.csv itself never
 * contains an invalid shift/initial (verified against the real CSV), so
 * those failure modes can only be exercised this way.</p>
 */
@QuarkusTest
class CalendarioImportServiceTest {

    @Inject
    CalendarioImportService importService;

    @Inject
    EntityManager em;

    private StudentGroup moda1;
    private StudentGroup moda2;
    private StudentGroup moda3;

    @BeforeEach
    @Transactional
    void cleanDatabase() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM LabAssistant").executeUpdate();
        em.createQuery("DELETE FROM LabRotation").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM ProfessionalizzantiDay").executeUpdate();
        em.createQuery("DELETE FROM StagePattern").executeUpdate();
        em.createQuery("DELETE FROM DailyActivityQuota").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
        em.createQuery("DELETE FROM Room").executeUpdate();
    }

    /**
     * Full school-year Timeslots (mirrors DataGenerator's real seed shape) +
     * the G09 groups + T17/T16/T07's real lab courses
     * (isLab classification matches DataGenerator.getRoomTags: Modello
     * (Moulage) and Confezione are lab-tagged "moda", Tecnologia e
     * creatività is not) + a spare T17 RA course on G01 (proving
     * resolveModaCourse prefers the lab-tagged course over an ambiguous
     * second candidate) + a fully-open GroupCalendarRule for all 3 groups.
     */
    @Transactional
    void seedFullYearFixture() {
        seedFullYearFixture(Set.of());
    }

    /**
     * Overload excluding specific dates' Timeslots entirely (TASK-364 code
     * review K2) - used only to build a "clean" (zero {@code modaFailures})
     * fixture for the force-re-run idempotency test below. The real CSV's one
     * known conflict (2027-06-09, G05 - see {@code computeSlotRange}'s
     * javadoc) is a genuine same-day mixed-shift overlap; the only way to
     * avoid it without touching the real data is to make that date's rows
     * fall into the non-blocking "no Timeslot" skip bucket instead of the
     * blocking conflict bucket, which requires removing that date's Timeslots
     * (not just one group/course) from the fixture entirely. The 3 baseline
     * tests above use the zero-arg overload specifically BECAUSE they assert
     * on that known conflict.
     */
    @Transactional
    void seedFullYearFixture(Set<LocalDate> excludedDates) {
        LocalDate date = TimeslotSequence.SCHOOL_YEAR_START;
        while (!date.isAfter(TimeslotSequence.SCHOOL_YEAR_END)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !excludedDates.contains(date)) {
                for (var startTime : TimeslotSequence.SLOT_START_TIMES) {
                    LocalDateTime start = LocalDateTime.of(date, startTime);
                    new Timeslot(start, start.plusHours(1)).persist();
                }
            }
            date = date.plusDays(1);
        }

        moda1 = new StudentGroup("G01", 20);
        moda1.persist();
        moda2 = new StudentGroup("G05", 20);
        moda2.persist();
        moda3 = new StudentGroup("G09", 20);
        moda3.persist();

        // Real incarichi.csv hours (TASK-379): the per-course "Orario wins"
        // budget cap makes totalHours load-bearing for planning - shared with
        // CalendarioImportRealSeedStageTest via ModaCourseFixtures.
        ModaCourseFixtures.persistRealModaLabCourses(moda1, moda2, moda3);
        // Ambiguity-avoidance fixture: T17 also has a non-lab RA course on
        // G01 - resolveModaCourse must still pick "Modello (Moulage)" (the
        // isLab=true candidate), not fail as ambiguous.
        Teacher T17Ref = Teacher.find("name", "T17").firstResult();
        Course raT17 = new Course("Recuperi ed Approfondimenti", T17Ref, moda1, 15);
        raT17.isLab = false;
        raT17.persist();

        Set<DayOfWeek> weekdays = new HashSet<>(Set.of(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        for (StudentGroup group : new StudentGroup[] {moda1, moda2, moda3}) {
            for (DayOfWeek dow : weekdays) {
                new GroupCalendarRule(group, dow, 0, TimeslotSequence.SLOTS_PER_DAY - 1).persist();
            }
        }
    }

    @Test
    void dryRun_reportsRealDataShapeWithoutPersisting() {
        seedFullYearFixture();

        CalendarioImportReport report = importService.importCalendario(true, false);

        assertTrue(report.dryRun);
        assertFalse(report.persisted, "dry run must never persist");
        assertEquals(0, Lesson.count(), "dry run must not touch the database");
        // 312 rows since the 2027-06-09 G05 PE mixed-shift row was excluded from the
        // seed data (school divergence #8, calendario-scolastico-format.md section 4: the
        // golden resolved that day differently — 'Orario wins' per the school's Q3 answer).
        assertEquals(312, report.modaRows, "calendario_moda.csv has 312 data rows");
        // TASK-550 extension: 2027-06-14/15 now HAVE timeslots, so the g05 rows that
        // used to be beyond-horizon are no longer skipped there
        assertEquals(0, report.modaSkippedNoTimeslot);
        assertEquals(0, report.modaFailures.size(),
                "no conflicts expected after the divergent row's exclusion: " + report.modaFailures);
        // TASK-550 extension: the 2 formerly beyond-horizon rows (06-14/15) now plan,
        // so "Orario wins" grows to 19 and planned stays 293 (312 - 19 - 0 skips)
        assertEquals(293, report.modaPlanned);
        assertEquals(19, report.modaSkippedBeyondCourseHours);
        // Sum of hours across those 310 rows (one row spans multiple Lessons) -
        // independently recomputed from the real CSV, see class javadoc.
        assertEquals(866, report.modaLessonsPinned);
    }

    @Test
    void persist_succeedsOnCleanRealData() {
        seedFullYearFixture();

        CalendarioImportReport report = importService.importCalendario(false, false);

        assertFalse(report.dryRun);
        assertTrue(report.persisted, "clean data (divergent row excluded) must persist: " + report.modaFailures);
        assertEquals(0, report.modaFailures.size());
        assertEquals(866, Lesson.count("pinned", true), "all 293 planned rows pinned (866 lesson-hours, Orario-wins capped)");
    }

    @Test
    void isOpenValidationFailure_blocksWholeBatch() {
        seedFullYearFixture();
        // Real row: 2026-09-15,G01,PE,3,first -> slots 0-2 (08:00-11:00).
        // Close G01 on Tuesdays for exactly this date via a date-scoped
        // override (GroupCalendarService: most-specific-rule-wins), leaving
        // every other Tuesday untouched.
        addClosureOverride(moda1, DayOfWeek.TUESDAY, LocalDate.of(2026, 9, 15));

        CalendarioImportReport report = importService.importCalendario(true, false);

        boolean foundCalendarClosedFailure = report.modaFailures.stream()
                .anyMatch(f -> "2026-09-15".equals(f.date) && "G01".equals(f.group) && f.reason.contains("calendar closed"));
        assertTrue(foundCalendarClosedFailure, "expected a calendar-closed failure for 2026-09-15/G01: " + report.modaFailures);
        // Plus the one known 2027-06-09 conflict that's always present in the real data.
        assertEquals(1, report.modaFailures.size(), "expected exactly the synthetic closed-slot failure: " + report.modaFailures);
        assertFalse(report.persisted);
    }

    @Transactional
    void addClosureOverride(StudentGroup group, DayOfWeek dayOfWeek, LocalDate date) {
        new GroupCalendarRule(group, dayOfWeek, 1, 0, date, date).persist(); // maxSlot < minSlot = fully closed
    }

    @Test
    void resolveTeacherName_knownInitialsMapCorrectly() {
        assertEquals("T17", CalendarioImportService.resolveTeacherName("PE"));
        assertEquals("T16", CalendarioImportService.resolveTeacherName("MU"));
        assertEquals("T07", CalendarioImportService.resolveTeacherName("BO"));
    }

    @Test
    void resolveTeacherName_unknownInitialThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CalendarioImportService.resolveTeacherName("XX"));
        assertTrue(ex.getMessage().contains("XX"));
    }

    @Test
    void computeSlotRange_firstShiftAnchorsAtZero() {
        assertArrayEqualsSlots(new int[] {0, 2}, CalendarioImportService.computeSlotRange("first", 3));
        assertArrayEqualsSlots(new int[] {0, 4}, CalendarioImportService.computeSlotRange("first", 5));
    }

    @Test
    void computeSlotRange_secondShiftAnchorsAtThreeWhenItFits() {
        assertArrayEqualsSlots(new int[] {3, 3}, CalendarioImportService.computeSlotRange("second", 1));
        assertArrayEqualsSlots(new int[] {3, 4}, CalendarioImportService.computeSlotRange("second", 2));
        assertArrayEqualsSlots(new int[] {3, 5}, CalendarioImportService.computeSlotRange("second", 3));
    }

    @Test
    void computeSlotRange_secondShiftFallsBackToZeroWhenItDoesNotFit() {
        // hours=6 > 3, can't start at slot 3 (would need slots 3..8) - falls
        // back to anchor 0, matching the golden grid's actual 08:00-14:00 placement.
        assertArrayEqualsSlots(new int[] {0, 5}, CalendarioImportService.computeSlotRange("second", 6));
    }

    @Test
    void computeSlotRange_secondShiftHoursFourFallsBackToZero() {
        // TASK-364 code review K8: hours=4 takes the same ">3" fallback branch
        // as hours=6 (anchor 0, slots 0-3) - but unlike 3/5/6, this magnitude
        // never occurs in the real 2026/27 calendario_moda.csv (verified: only
        // 1,2,3,5,6 appear), so this path is fallback-rule semantics only,
        // never golden-verified against an actual row.
        assertArrayEqualsSlots(new int[] {0, 3}, CalendarioImportService.computeSlotRange("second", 4));
    }

    @Test
    void computeSlotRange_invalidShiftThrows() {
        assertThrows(IllegalArgumentException.class, () -> CalendarioImportService.computeSlotRange("third", 3));
    }

    /**
     * TASK-530 extension (20/08): the display-room backfill for imported pins.
     * Tagged course -> first assignable room carrying the tag; TAG-LESS (aula)
     * course -> first assignable room with NO tags (the 24+24+24 MODA aula pins
     * the 535 gate counted roomless); never a cap-0 (non-assignable) room;
     * null only when literally nothing fits.
     */
    @Test
    @Transactional
    void findDisplayRoomFor_taggedTagLessAndUnassignable() {
        seedFullYearFixture();
        Room lab = new Room("LAB MODA", 20, new java.util.ArrayList<>(java.util.List.of("moda")));
        lab.persist();
        Room generic = new Room("AULA 1", 24, null);
        generic.persist();
        Room polival = new Room("R18", 0, null);
        polival.persist();

        Course moulage = Course.find("subject = 'Modello (Moulage)' and studentGroup = ?1", moda1).firstResult();
        assertNotNull(moulage, "fixture seeds the lab-tagged course");
        // the shared fixture builds courses without tags; the tag is what the
        // tagged branch keys on, so set it exactly like DataGenerator does
        moulage.requiredRoomTags = new java.util.ArrayList<>(java.util.List.of("moda"));
        assertEquals("LAB MODA", GoldenScheduleImportService.findDisplayRoomFor(moulage).name);

        Course theory = Course.find("subject = 'Tecnologia e creatività' and studentGroup = ?1", moda1).firstResult();
        assertNotNull(theory, "fixture seeds a tag-less course");
        Room shown = GoldenScheduleImportService.findDisplayRoomFor(theory);
        assertNotNull(shown, "tag-less pin gets the generic classroom, not null");
        assertTrue(shown.tags == null || shown.tags.isEmpty(), "fallback must be a generic aula");

        // TASK-536 H2 root cause (20/08): pseudo-Stage courses keep room=null even
        // when a generic classroom exists - the tag-less fallback used to hand all
        // 1993 Stage stubs the SAME room, an uncurable pinned H2 pile (-2380 hard
        // on h33b-run1). Stage is off-premises: null is the honest display. The
        // fixture does not seed Stage courses, so build the minimal shape inline.
        Teacher stageTeacher = new Teacher(GoldenScheduleImportService.STAGE_NAME);
        stageTeacher.pseudo = true;
        stageTeacher.persist();
        StudentGroup anyGroup = StudentGroup.<StudentGroup>findAll().firstResult();
        Course stage = new Course(GoldenScheduleImportService.STAGE_NAME, stageTeacher, anyGroup, 8);
        stage.persist();
        assertNull(GoldenScheduleImportService.findDisplayRoomFor(stage),
                "pseudo-Stage pin keeps room=null even with a generic room available (TASK-530b)");

        // only the cap-0 room present: nothing assignable -> null, never polivalente
        lab.delete();
        generic.delete();
        assertNull(GoldenScheduleImportService.findDisplayRoomFor(theory));
    }

    @Test
    void computeSlotRange_overflowingRangeThrows() {
        assertThrows(IllegalArgumentException.class, () -> CalendarioImportService.computeSlotRange("first", 9));
    }

    private void assertArrayEqualsSlots(int[] expected, int[] actual) {
        assertEquals(expected[0], actual[0], "start slot");
        assertEquals(expected[1], actual[1], "end slot");
    }

    // ─── K1: stub consumption ────────────────────────────────────────────
    //
    // consumeStubs is exercised directly (package-private, not through the
    // full importCalendario CSV pipeline): the real calendario_moda.csv
    // always contains its one known conflict (see computeSlotRange's
    // javadoc), so a full-fixture import always ends with persisted=false,
    // and the persisted branch that calls consumeStubs can never be reached
    // that way - see CalendarioImportService#consumeStubs's javadoc.

    @Test
    @Transactional
    void consumeStubs_consumesExactlyNStubs_leavingTotalHoursMinusNUnpinned() {
        seedFullYearFixture();
        Course moulage = Course.find("subject = 'Modello (Moulage)' and studentGroup = ?1", moda1).firstResult();
        seedStubs(moulage, 5); // mimics LessonGenerator.generateForCourse's totalHours stubs

        List<CalendarioImportReport.StubConsumption> result = importService.consumeStubs(Map.of(moulage, 3));

        assertEquals(1, result.size());
        CalendarioImportReport.StubConsumption sc = result.get(0);
        assertEquals(3, sc.lessonsPinned);
        assertEquals(3, sc.consumedStubs);
        assertEquals(0, sc.stubShortfall);
        assertEquals(2, Lesson.count("course", moulage), "5 stubs - 3 consumed = 2 left unpinned");
    }

    @Test
    @Transactional
    void consumeStubs_reportsShortfallWhenFewerStubsThanPinned() {
        seedFullYearFixture();
        Course moulage = Course.find("subject = 'Modello (Moulage)' and studentGroup = ?1", moda1).firstResult();
        seedStubs(moulage, 2);

        List<CalendarioImportReport.StubConsumption> result = importService.consumeStubs(Map.of(moulage, 5));

        CalendarioImportReport.StubConsumption sc = result.get(0);
        assertEquals(5, sc.lessonsPinned);
        assertEquals(2, sc.consumedStubs);
        assertEquals(3, sc.stubShortfall);
        assertEquals(0, Lesson.count("course", moulage), "both available stubs consumed, none left");
    }

    @Test
    @Transactional
    void consumeStubs_prefersNullTimeslotStubsOverTimeslotted() {
        seedFullYearFixture();
        Course moulage = Course.find("subject = 'Modello (Moulage)' and studentGroup = ?1", moda1).firstResult();
        Timeslot anyTimeslot = Timeslot.findAll().firstResult();
        Lesson timeslotted = new Lesson(moulage);
        timeslotted.timeslot = anyTimeslot; // an unpinned Lesson that's already been placed, unlike a fresh stub
        timeslotted.persist();
        seedStubs(moulage, 1); // the true, never-touched stub

        importService.consumeStubs(Map.of(moulage, 1));

        Lesson remaining = Lesson.<Lesson>find("course", moulage).firstResult();
        assertEquals(1, Lesson.count("course", moulage), "exactly one Lesson left");
        assertEquals(anyTimeslot.id, remaining.timeslot.id,
                "the null-timeslot stub must be consumed first, leaving the already-timeslotted one behind");
    }

    @Transactional
    void seedStubs(Course course, int count) {
        for (int i = 0; i < count; i++) {
            new Lesson(course).persist();
        }
    }

    // ─── K2: re-run / force guard ─────────────────────────────────────────

    @Test
    void forceReRun_isIdempotent_andPlainReRunRefusesWithoutForce() {
        // Excludes the one known 2027-06-09 conflict date entirely (see the
        // seedFullYearFixture(Set) overload's javadoc) so this fixture is the
        // only way to reach persisted=true, which the K2 guard requires to be
        // exercised at all.
        seedFullYearFixture(Set.of(LocalDate.of(2027, 6, 9)));

        CalendarioImportReport first = importService.importCalendario(false, false);
        assertTrue(first.persisted, "expected a clean import with the known-conflict date excluded: " + first.modaFailures);
        long pinnedAfterFirst = Lesson.count("pinned", true);
        long totalAfterFirst = Lesson.count();
        assertTrue(pinnedAfterFirst > 0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importService.importCalendario(false, false));
        assertTrue(ex.getMessage().contains("force=true"), ex.getMessage());
        assertEquals(pinnedAfterFirst, Lesson.count("pinned", true), "a refused re-run must not touch any Lesson");
        assertEquals(totalAfterFirst, Lesson.count());

        CalendarioImportReport second = importService.importCalendario(false, true);
        assertTrue(second.persisted, "expected the force re-run to succeed identically: " + second.modaFailures);
        assertEquals(pinnedAfterFirst, second.deletedPriorPins, "must clear exactly what the first run pinned");
        assertEquals(pinnedAfterFirst, Lesson.count("pinned", true), "idempotent: same pinned count as the first run");
        assertEquals(totalAfterFirst, Lesson.count(), "idempotent: same total Lesson count as the first run");
    }

    // ─── TASK-381 "stage on the grid" (formerly "L1-bis"): planStagePins ────
    //
    // planStagePins is exercised directly (package-private, not through the
    // full importCalendario CSV pipeline) against a minimal fixture, same
    // rationale as consumeStubs' own K1 tests above: this isolates the
    // N=min(hours, calendar-open-slot-count, remaining-stub-budget)
    // truncation logic and its skip/off-grid counters without needing the
    // real calendario_fc.csv/calendario_class_days.csv's full data.

    private static final LocalDate STAGE_MON = LocalDate.of(2026, 9, 14); // a real school Monday (SCHOOL_YEAR_START)

    @Transactional
    void seedStageFixture(StudentGroup group, int slotsThatDay, int stubCount) {
        for (int i = 0; i < slotsThatDay; i++) {
            var startTime = TimeslotSequence.SLOT_START_TIMES[i];
            LocalDateTime start = LocalDateTime.of(STAGE_MON, startTime);
            new Timeslot(start, start.plusHours(1)).persist();
        }
        seedStageCourse(group, stubCount);
    }

    /**
     * The pseudo Stage teacher + course + stub trio (the DataGenerator/
     * GoldenScheduleImportService convention) - single owner in this file so
     * the convention can't drift across the fixtures that need timeslots on
     * dates other than {@link #STAGE_MON}.
     */
    Course seedStageCourse(StudentGroup group, int stubCount) {
        Teacher stageTeacher = new Teacher("Stage");
        stageTeacher.pseudo = true;
        stageTeacher.persist();
        Course stageCourse = new Course("Stage", stageTeacher, group, stubCount);
        stageCourse.persist();
        seedStubs(stageCourse, stubCount);
        return stageCourse;
    }

    @Test
    @Transactional
    void planStagePins_truncatesToStubBudget() {
        StudentGroup group = new StudentGroup("G04", 20);
        group.persist();
        seedStageFixture(group, TimeslotSequence.SLOTS_PER_DAY, 3); // full 8-slot day, but only 3 stubs
        new DailyActivityQuota(group, STAGE_MON, 0, 0, 5).persist(); // sheet asks for 5

        CalendarioImportService.StagePlan plan = importService.planStagePins(
                Map.of(group.name, group), Map.of(), GoldenScheduleImportService.buildTimeslotIndex(), List.of());

        assertEquals(3, plan.stagePinned);
        assertEquals(3, plan.pins.size());
        assertEquals(0, plan.stageSkippedCalendarClosed);
        assertEquals(2, plan.stageSkippedNoBudget);
    }

    @Test
    @Transactional
    void planStagePins_truncatesToCalendarOpenSlotCount() {
        StudentGroup group = new StudentGroup("G08", 20);
        group.persist();
        seedStageFixture(group, TimeslotSequence.SLOTS_PER_DAY, 10); // plenty of stub budget
        new DailyActivityQuota(group, STAGE_MON, 0, 0, 5).persist(); // sheet asks for 5
        // Calendar only opens the first 3 slots that day.
        List<GroupCalendarRule> rules = List.of(new GroupCalendarRule(group, STAGE_MON.getDayOfWeek(), 0, 2));

        CalendarioImportService.StagePlan plan = importService.planStagePins(
                Map.of(group.name, group), Map.of(group.id, rules), GoldenScheduleImportService.buildTimeslotIndex(), List.of());

        assertEquals(3, plan.stagePinned);
        assertEquals(2, plan.stageSkippedCalendarClosed);
        assertEquals(0, plan.stageSkippedNoBudget);
    }

    @Test
    @Transactional
    void planStagePins_noStageCourseCountsAsNoBudget() {
        StudentGroup group = new StudentGroup("G13", 20);
        group.persist();
        new DailyActivityQuota(group, STAGE_MON, 0, 0, 4).persist();
        // No Stage Course/Teacher seeded at all - mirrors a profile that never ran the golden import.

        CalendarioImportService.StagePlan plan = importService.planStagePins(
                Map.of(group.name, group), Map.of(), GoldenScheduleImportService.buildTimeslotIndex(), List.of());

        assertEquals(0, plan.stagePinned);
        assertTrue(plan.pins.isEmpty());
        assertEquals(0, plan.stageSkippedCalendarClosed);
        assertEquals(4, plan.stageSkippedNoBudget);
    }

    @Test
    @Transactional
    void planStagePins_fillsContiguouslyFromCalendarWindowStart() {
        StudentGroup group = new StudentGroup("G04", 20);
        group.persist();
        seedStageFixture(group, TimeslotSequence.SLOTS_PER_DAY, 10);
        new DailyActivityQuota(group, STAGE_MON, 0, 0, 3).persist();

        CalendarioImportService.StagePlan plan = importService.planStagePins(
                Map.of(group.name, group), Map.of(), GoldenScheduleImportService.buildTimeslotIndex(), List.of());

        assertEquals(3, plan.pins.size());
        List<Integer> hours = plan.pins.stream()
                .map(p -> p.timeslot().startDateTime.getHour())
                .sorted()
                .toList();
        assertEquals(List.of(8, 9, 10), hours, "must fill slots 0-2 (08:00-11:00) contiguously from the window start");
    }

    @Test
    @Transactional
    void planStagePins_countsClosureDateHoursAsOffGridAndDrawsBudget() {
        // The real sheet books G08 stage on 3 Easter-closure dates (24h)
        // whose Timeslot rows don't exist (quota seeding is horizon-filtered
        // but NOT closure-filtered). Those hours must land in
        // stageOffGridHours (TASK-381 "stage on the grid" - renamed from
        // stageSkippedNoTimeslot), not silently vanish from every counter -
        // AND must draw from the stub budget like a pin would (verified here
        // via offGridHoursByCourse; the actual stub deletion + Course.
        // expectedUnscheduledHours add happens in importCalendario's persist
        // phase, see the persist-phase tests below).
        StudentGroup group = new StudentGroup("G08", 20);
        group.persist();
        seedStageFixture(group, 0, 10); // Stage course + stubs, but ZERO Timeslots that day
        new DailyActivityQuota(group, STAGE_MON, 0, 0, 4).persist();
        Course stageCourse = Course.find("subject = 'Stage' and studentGroup = ?1", group).firstResult();

        CalendarioImportService.StagePlan plan = importService.planStagePins(
                Map.of(group.name, group), Map.of(), GoldenScheduleImportService.buildTimeslotIndex(), List.of());

        assertEquals(0, plan.stagePinned);
        assertTrue(plan.pins.isEmpty());
        assertEquals(4, plan.stageOffGridHours, "all 4 asked hours hit the off-grid path");
        assertEquals(4, plan.offGridHoursByCourse.get(stageCourse), "off-grid hours tracked per-course for persist-phase consumption");
        assertEquals(0, plan.stageSkippedCalendarClosed);
        assertEquals(0, plan.stageSkippedNoBudget, "10 stubs cover the 4 off-grid hours - no budget shortfall");
    }

    @Test
    @Transactional
    void planStagePins_offGridHoursExhaustBudgetJustLikeAPinWould() {
        // Off-grid hours draw from the SAME remaining-stub budget as pinned
        // hours (TASK-381 "stage on the grid") - with only 2 stubs and a
        // 4-hour off-grid ask, exactly 2 must be off-grid and the rest must
        // report as stageSkippedNoBudget, never silently over-consumed.
        StudentGroup group = new StudentGroup("G08", 20);
        group.persist();
        seedStageFixture(group, 0, 2); // Stage course + only 2 stubs, ZERO Timeslots that day
        new DailyActivityQuota(group, STAGE_MON, 0, 0, 4).persist();

        CalendarioImportService.StagePlan plan = importService.planStagePins(
                Map.of(group.name, group), Map.of(), GoldenScheduleImportService.buildTimeslotIndex(), List.of());

        assertEquals(0, plan.stagePinned);
        assertEquals(2, plan.stageOffGridHours);
        assertEquals(2, plan.stageSkippedNoBudget);
    }

    @Test
    @Transactional
    void planStagePins_classDaysSourceGroupIsPinned() {
        // Non-quota groups (G07, G11, G10, G12, G09 for 2026/27)
        // are read from calendario_class_days.csv's modality=S rows instead
        // of DailyActivityQuota - no DailyActivityQuota row exists for this
        // group at all, proving buildStageAsks correctly falls back to the
        // class-days source rather than silently skipping it.
        StudentGroup group = new StudentGroup("G10", 20);
        group.persist();
        seedStageFixture(group, TimeslotSequence.SLOTS_PER_DAY, 10);
        List<String[]> classDaysRows = List.<String[]>of(
                new String[] {"G10", STAGE_MON.toString(), "3", "S"});

        CalendarioImportService.StagePlan plan = importService.planStagePins(
                Map.of(group.name, group), Map.of(), GoldenScheduleImportService.buildTimeslotIndex(), classDaysRows);

        assertEquals(3, plan.stagePinned);
        assertEquals(3, plan.pins.size());
        assertEquals(0, plan.stageSkippedCalendarClosed);
        assertEquals(0, plan.stageSkippedNoBudget);
    }

    @Test
    @Transactional
    void planStagePins_quotaGroupNeverAlsoReadFromClassDays() {
        // A group with ANY DailyActivityQuota row (even aula/lab only, no
        // stage) is a quota group and must be excluded from the class-days
        // source entirely - otherwise the same date's hours could be
        // double-counted from both sources at once.
        StudentGroup group = new StudentGroup("G08", 20);
        group.persist();
        seedStageFixture(group, TimeslotSequence.SLOTS_PER_DAY, 10);
        new DailyActivityQuota(group, STAGE_MON, 0, 0, 3).persist(); // quota source: asks for 3
        List<String[]> classDaysRows = List.<String[]>of(
                new String[] {"G08", STAGE_MON.toString(), "5", "S"}); // class-days source: would ask for 5 more

        CalendarioImportService.StagePlan plan = importService.planStagePins(
                Map.of(group.name, group), Map.of(), GoldenScheduleImportService.buildTimeslotIndex(), classDaysRows);

        assertEquals(3, plan.stagePinned, "only the quota source's 3h are asked - the class-days row must be ignored");
    }

    @Test
    @Transactional
    void planStagePins_budgetRunsOutAtTheLastChronologicalDate() {
        // Ordered processing (TASK-381): when a course's total sheet ask
        // exceeds its stub budget, the shortfall must land on the LAST date
        // chronologically, not an arbitrary one - mirrors G07's real-seed
        // shortfall (incarichi budget 295h vs sheet 300h).
        StudentGroup group = new StudentGroup("G07", 20);
        group.persist();
        LocalDate day1 = STAGE_MON;
        LocalDate day2 = STAGE_MON.plusDays(1);
        for (LocalDate d : List.of(day1, day2)) {
            for (int i = 0; i < TimeslotSequence.SLOTS_PER_DAY; i++) {
                var startTime = TimeslotSequence.SLOT_START_TIMES[i];
                LocalDateTime start = LocalDateTime.of(d, startTime);
                new Timeslot(start, start.plusHours(1)).persist();
            }
        }
        seedStageCourse(group, 5); // budget: only 5 stubs total

        List<String[]> classDaysRows = List.of(
                new String[] {"G07", day1.toString(), "3", "S"},
                new String[] {"G07", day2.toString(), "3", "S"}); // 3+3=6 > 5 budget

        CalendarioImportService.StagePlan plan = importService.planStagePins(
                Map.of(group.name, group), Map.of(), GoldenScheduleImportService.buildTimeslotIndex(), classDaysRows);

        assertEquals(5, plan.stagePinned, "day1's 3h fully pinned + day2's first 2h - budget exhausted mid-day2");
        assertEquals(1, plan.stageSkippedNoBudget, "day2's last 1h has no budget left");
        List<LocalDate> pinnedDates = plan.pins.stream().map(p -> p.timeslot().getDate()).sorted().toList();
        assertEquals(day1, pinnedDates.get(0), "day1 (earlier date) must be fully pinned before day2");
    }

    @Test
    @Transactional
    void planStagePins_secondRunCountsExistingPinsInsteadOfDuplicating() {
        // Same-course idempotency: a re-run (force or not) must never re-spend
        // leftover stub budget onto dates a prior run (or the golden import)
        // already stage-pinned - that would stack duplicate PINNED lessons on
        // the same (group, date, slot), a permanent H3 no solver can fix.
        StudentGroup group = new StudentGroup("G13", 20);
        group.persist();
        seedStageFixture(group, TimeslotSequence.SLOTS_PER_DAY, 10); // 10 stubs: leftover budget after "run 1"
        new DailyActivityQuota(group, STAGE_MON, 0, 0, 3).persist();
        Course stageCourse = Course.find("subject = 'Stage' and studentGroup = ?1", group).firstResult();
        // Simulate run 1's persisted outcome: slots 0-2 already pinned.
        for (int i = 0; i < 3; i++) {
            var startTime = TimeslotSequence.SLOT_START_TIMES[i];
            Timeslot ts = Timeslot.find("startDateTime", java.time.LocalDateTime.of(STAGE_MON, startTime)).firstResult();
            Lesson pinnedStage = new Lesson(stageCourse);
            pinnedStage.timeslot = ts;
            pinnedStage.pinned = true;
            pinnedStage.persist();
        }

        CalendarioImportService.StagePlan plan = importService.planStagePins(
                Map.of(group.name, group), Map.of(), GoldenScheduleImportService.buildTimeslotIndex(), List.of());

        assertEquals(0, plan.stagePinned, "no new pins - the date is already fully covered");
        assertTrue(plan.pins.isEmpty());
        assertEquals(3, plan.stageAlreadyPinned);
        assertEquals(0, plan.stageSkippedCalendarClosed);
        assertEquals(0, plan.stageSkippedNoBudget);
    }

    @Test
    @Transactional
    void planStagePins_offGridSurplusBudgetDoesNotDoubleCountOnRerun() {
        // Code-review-caught bug: off-grid hours leave no pinned Lesson to
        // detect on a re-run (unlike on-grid pins, guarded above by
        // alreadyPinnedSlots). If a course's stub budget exceeds its actual
        // off-grid demand (a genuine surplus - none exists on the real
        // 2026/27 seed, but nothing prevented it structurally), a naive
        // re-run would re-draw the leftover surplus against the SAME
        // already-covered off-grid date, double-adding to
        // Course.expectedUnscheduledHours. Course.expectedUnscheduledHours
        // itself is read back as a cursor to prevent this - see
        // planStagePins' idempotency javadoc.
        StudentGroup group = new StudentGroup("G08", 20);
        group.persist();
        seedStageFixture(group, 0, 10); // 10 stubs, ZERO Timeslots that day (off-grid) - a surplus vs the 4h ask
        new DailyActivityQuota(group, STAGE_MON, 0, 0, 4).persist();
        Course stageCourse = Course.find("subject = 'Stage' and studentGroup = ?1", group).firstResult();

        CalendarioImportService.StagePlan run1 = importService.planStagePins(
                Map.of(group.name, group), Map.of(), GoldenScheduleImportService.buildTimeslotIndex(), List.of());
        assertEquals(4, run1.stageOffGridHours, "run 1: the full 4h ask is off-grid");
        importService.consumeOffGridStageStubs(run1.offGridHoursByCourse); // simulates importCalendario's persist phase
        Course reloaded = Course.findById(stageCourse.id);
        assertEquals(4, reloaded.expectedUnscheduledHours);
        assertEquals(6, Lesson.count("course = ?1 and pinned = false", stageCourse), "10 stubs - 4 consumed = 6 genuine surplus left");

        CalendarioImportService.StagePlan run2 = importService.planStagePins(
                Map.of(group.name, group), Map.of(), GoldenScheduleImportService.buildTimeslotIndex(), List.of());

        assertEquals(0, run2.stageOffGridHours, "already fully accounted for in run 1 - must not re-draw the leftover surplus");
        assertTrue(run2.offGridHoursByCourse.isEmpty());
    }

    // ─── TASK-381 "stage on the grid": consumeOffGridStageStubs ─────────────
    //
    // Exercised directly (package-private, not through the full
    // importCalendario CSV pipeline - same rationale as consumeStubs' own K1
    // tests): isolates the stub-deletion + Course.expectedUnscheduledHours
    // add from the CSV-driven planning phase.

    @Test
    @Transactional
    void consumeOffGridStageStubs_deletesStubsAndAddsExpectedUnscheduledHours() {
        StudentGroup group = new StudentGroup("G08", 20);
        group.persist();
        seedStageFixture(group, 0, 10);
        Course stageCourse = Course.find("subject = 'Stage' and studentGroup = ?1", group).firstResult();

        importService.consumeOffGridStageStubs(Map.of(stageCourse, 4));

        assertEquals(6, Lesson.count("course", stageCourse), "10 stubs - 4 consumed = 6 left");
        Course reloaded = Course.findById(stageCourse.id);
        assertEquals(4, reloaded.expectedUnscheduledHours);
    }

    @Test
    @Transactional
    void consumeOffGridStageStubs_isNullSafeAndAdditive() {
        StudentGroup group = new StudentGroup("G08", 20);
        group.persist();
        seedStageFixture(group, 0, 10);
        Course stageCourse = Course.find("subject = 'Stage' and studentGroup = ?1", group).firstResult();
        assertEquals(null, stageCourse.expectedUnscheduledHours, "fresh Course.expectedUnscheduledHours is null, not 0");

        importService.consumeOffGridStageStubs(Map.of(stageCourse, 3));
        importService.consumeOffGridStageStubs(Map.of(stageCourse, 2)); // simulates a second contributing course batch

        Course reloaded = Course.findById(stageCourse.id);
        assertEquals(5, reloaded.expectedUnscheduledHours, "null-safe + additive across calls");
        assertEquals(5, Lesson.count("course", stageCourse), "10 stubs - 3 - 2 = 5 left");
    }

    @Test
    @Transactional
    void consumeOffGridStageStubs_logsShortfallWithoutThrowingWhenBudgetInsufficient() {
        StudentGroup group = new StudentGroup("G08", 20);
        group.persist();
        seedStageFixture(group, 0, 2); // only 2 stubs
        Course stageCourse = Course.find("subject = 'Stage' and studentGroup = ?1", group).firstResult();

        importService.consumeOffGridStageStubs(Map.of(stageCourse, 5)); // asks for more than available

        assertEquals(0, Lesson.count("course", stageCourse), "both available stubs consumed");
        Course reloaded = Course.findById(stageCourse.id);
        assertEquals(2, reloaded.expectedUnscheduledHours, "only the 2 actually-consumed stubs are added, not the full ask");
    }

    // ─── TASK-381 "stage on the grid": full-pipeline idempotency ────────────
    //
    // Exercises off-grid stage accounting through the real importCalendario
    // pipeline (not just planStagePins/consumeOffGridStageStubs in
    // isolation): calendario_class_days.csv is a real, fixed classpath
    // resource (not test-injectable, same constraint as calendario_moda.csv -
    // see the class javadoc). Its real "G10" modality=S rows start
    // 2027-03-02 (Tue) 8h/2027-03-03 (Wed) 8h/2027-03-04 (Thu) 8h/2027-03-05
    // (Fri) 8h = 32h on-grid, then 2027-03-06 (Sat) 8h off-grid (no Timeslot
    // exists for a Saturday in this fixture, which only creates weekday
    // Timeslots) - a 36-stub budget exhausts exactly 4h into that Saturday,
    // so run 1 exercises BOTH consumeStubs (32h pinned) AND
    // consumeOffGridStageStubs (4h off-grid) in the same run, then leaves
    // zero budget behind to verify a force re-run adds nothing further.

    @Test
    @Transactional
    void importCalendario_forceReRun_offGridStageIsIdempotent() {
        seedFullYearFixture(Set.of(LocalDate.of(2027, 6, 9))); // exclude the one known MODA conflict date

        StudentGroup FAGroup = new StudentGroup("G10", 20);
        FAGroup.persist();
        Course stageCourse = seedStageCourse(FAGroup, 36);

        CalendarioImportReport first = importService.importCalendario(false, false);
        assertTrue(first.persisted, "expected a clean import: " + first.modaFailures);

        Course reloadedFirst = Course.findById(stageCourse.id);
        assertEquals(4, reloadedFirst.expectedUnscheduledHours,
                "32h pinned on-grid (Tue-Fri) + 4h of the following Saturday's 8h ask before the 36-stub budget runs out");
        assertEquals(32, Lesson.count("course = ?1 and pinned = true", stageCourse));
        long unpinnedAfterFirst = Lesson.count("course = ?1 and pinned = false", stageCourse);
        assertEquals(0, unpinnedAfterFirst, "36 stubs - 32 pinned - 4 off-grid = 0 left");

        CalendarioImportReport second = importService.importCalendario(false, true);
        assertTrue(second.persisted, "expected the force re-run to succeed: " + second.modaFailures);

        Course reloadedSecond = Course.findById(stageCourse.id);
        assertEquals(4, reloadedSecond.expectedUnscheduledHours,
                "idempotent: a force re-run must not add further expectedUnscheduledHours once the course's stub budget is exhausted");
        assertEquals(32, Lesson.count("course = ?1 and pinned = true", stageCourse),
                "idempotent: a force re-run must not pin further hours once the course's stub budget is exhausted");
        assertEquals(unpinnedAfterFirst, Lesson.count("course = ?1 and pinned = false", stageCourse),
                "idempotent: a force re-run must not delete further stubs once the course's stub budget is exhausted");
    }
}
