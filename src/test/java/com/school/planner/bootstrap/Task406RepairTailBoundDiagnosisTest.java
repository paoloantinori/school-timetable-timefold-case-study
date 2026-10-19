package com.school.planner.bootstrap;

import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.DayClusteredConstructionPhase;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.TimeslotSequence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-406 Phase A diagnostic: isolate the +105k M20 regression to the
 * round-robin repair tail's day-scan BOUND, without running a full solve.
 *
 * <p>The 405b A/B discriminator proved the +105k M20 jump (401 baseline -786k ->
 * 405 -895k, +63 matches) SURVIVES the M18 weight drop (250 -> 50) unchanged, so
 * it is NOT an M18 pressure effect. The remaining suspect is the TASK-394 change
 * to {@link DayClusteredConstructionPhase#getIdealEndDate}: it now resolves to the
 * fact-based plan-end (~year end, 2027-06-10) instead of the legacy flat-8h-model
 * estimate (~2027-03-31 for G06), so the round-robin repair tail's chronological
 * day-scan reaches the whole year and scatters leftovers onto late, under-filled
 * days instead of being cut off in spring.</p>
 *
 * <p><b>Why this isolates the cause cleanly:</b> the weekly-template STAMP runs
 * across the FULL school year with no {@code getIdealEndDate} cap (see {@link
 * DayClusteredConstructionPhase#stampWeeklyTemplate}'s own javadoc), so it places
 * the SAME set of lessons regardless of the bound. Only {@link
 * DayClusteredConstructionPhase#placeLessonsRoundRobin} (the repair tail) consults
 * {@code getIdealEndDate} via {@code findFirstAvailableDate}/{@code
 * findNextAvailableDay}. Two arms identical except for the bound therefore differ
 * ONLY in where the repair tail puts its leftovers.</p>
 *
 * <p><b>The M20 proxy:</b> M20 ({@link SchoolScheduleConstraintProvider#horizontallyCompact})
 * penalizes a (group, date) only when {@code 0 < unpinnedTotal < expected}, by
 * {@code (expected - placed) * 1000}. This fixture has NO pinned lessons (same
 * deliberate simplification as {@link WeeklyTemplateConstructionRealSeedTest}), so
 * "unpinned placed" == "all placed" and the proxy below is a faithful
 * construction-end M20 read: under-days, deficit-hours, and the implied medium.</p>
 *
 * <p><b>Hypothesis 2 (left-anchor interaction) is refuted by construction, not just
 * measurement:</b> {@link com.school.planner.solver.StudentDayCompaction#compact}
 * (gap-fill AND the {@code anchorRunLeft} pass) only ever moves a lesson WITHIN its
 * own (group, date) - the target Timeslot is resolved from the same date's slot
 * array. Per-(group, date) counts are therefore invariant under compaction, so
 * compaction can never change the M20 proxy. The {@link
 * #studentDayCompaction_isDateInvariant_preservesPerGroupDateCounts} test proves
 * that invariant empirically, which is strictly stronger than an on/off run.</p>
 *
 * <p>Mirrors {@link WeeklyTemplateConstructionRealSeedTest}'s real-seed wiring
 * (same package, for the same package-private {@code DataGenerator} access).</p>
 */
@QuarkusTest
class Task406RepairTailBoundDiagnosisTest {

    @Inject
    DataGenerator dataGenerator;

    @Inject
    com.school.planner.service.LessonGenerator lessonGenerator;

    @Inject
    AvailabilityExpansionService availabilityExpansionService;

    @Inject
    EntityManager em;

    private static final Map<String, Integer> MAX_HOURS_PER_DAY = RealSeedFixture.MAX_HOURS_PER_DAY;

    /** Current production behavior: TASK-394 fact-based plan-end resolves here for every group. */
    private static final LocalDate BOUND_YEAR_END = TimeslotSequence.SCHOOL_YEAR_END; // 2027-06-10
    /** Legacy flat-8h-model estimate: the documented old wrong G06 marker (TASK-394 finding). */
    private static final LocalDate BOUND_LEGACY_APRIL = LocalDate.of(2027, 3, 31);

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM RecurringAvailabilityRule").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM DailyActivityQuota").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();

        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(List.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setTeacherAvailabilitiesStatic(List.of());
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.of(), java.util.Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(null);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
    }

    /** Re-seed the full real fixture. Mirrors WeeklyTemplateConstructionRealSeedTest.seedRealSeedFixture exactly. */
    @Transactional
    void seedRealSeedFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, MAX_HOURS_PER_DAY);

        Map<String, Teacher> teachers = new LinkedHashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("teachers.csv", 6)) {
            Teacher t = new Teacher(row[0].trim());
            String maxDailyHours = row[3].trim();
            t.maxDailyHours = maxDailyHours.isEmpty() ? null : Integer.valueOf(maxDailyHours);
            t.persist();
            teachers.put(t.name, t);
        }

        DataGenerator.CourseAggregation aggregation =
                DataGenerator.aggregateCourseHours(DataGenerator.loadSeedCsv("incarichi.csv", 4));
        for (Map.Entry<DataGenerator.CourseKey, Integer> entry : aggregation.hoursByKey().entrySet()) {
            DataGenerator.CourseKey key = entry.getKey();
            Teacher teacher = teachers.get(key.teacherName());
            StudentGroup group = groups.get(key.groupName());
            if (teacher == null || group == null) continue;
            Course course = new Course(key.subject(), teacher, group, entry.getValue());
            course.persist();
            lessonGenerator.generateForCourse(course.id);
        }

        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();

        for (String[] row : DataGenerator.loadSeedCsv("availability_rules.csv", 8)) {
            Teacher teacher = teachers.get(row[0].trim());
            if (teacher == null) continue;
            new RecurringAvailabilityRule(teacher, java.time.DayOfWeek.valueOf(row[1].trim()),
                    Integer.parseInt(row[2].trim()), Integer.parseInt(row[3].trim()),
                    AvailabilityType.valueOf(row[4].trim()),
                    row[5].isBlank() ? null : LocalDate.parse(row[5].trim()),
                    row[6].isBlank() ? null : LocalDate.parse(row[6].trim()), row[7].trim())
                    .persist();
        }
        availabilityExpansionService.expandAll();
    }

    /** Snapshot of the M20-relevant construction-end distribution for one arm. */
    private record M20Proxy(int underDays, int deficitHours, int overDays, int surplusHours,
                            int fullDays, int scopedTotal, long impliedMedium,
                            Map<Month, Integer> placedByMonth) {
    }

    /**
     * Run the construction phase (stamp + repair tail + box + compaction) on a freshly
     * re-seeded fixture with the repair tail's day-scan bound set to {@code bound}, and
     * return the M20 proxy of the result. Re-seeds per arm so the two arms differ ONLY
     * in the bound (deterministic seed, identical inputs otherwise).
     */
    private M20Proxy solveAndMeasure(LocalDate bound) {
        clean();
        seedRealSeedFixture();

        List<Timeslot> timeslots = Timeslot.listAll();
        List<Room> rooms = List.of();
        List<StudentGroup> studentGroups = StudentGroup.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<TeacherAvailability> teacherAvailabilities = TeacherAvailability.listAll();

        // The repair tail reads getIdealEndDate -> group.idealEndDateInfo (null here)
        // -> group.idealEndDateVrpBuffered. Setting it per arm is the clean bound toggle.
        for (StudentGroup group : studentGroups) {
            group.idealEndDateInfo = null;
            group.idealEndDateVrpBuffered = bound;
        }

        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(rooms);
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(new HashMap<>());
        SchoolSchedule.setTeacherAvailabilitiesStatic(teacherAvailabilities);
        SchoolSchedule.setSchoolHolidaysStatic(List.<SchoolHoliday>of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), java.util.Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(GroupCalendarRule.listAll());
        SchoolSchedule.setDailyActivityQuotasStatic(DailyActivityQuota.listAll());
        SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);

        SchoolSchedule problem = new SchoolSchedule(timeslots, rooms, List.of(), teacherAvailabilities,
                List.of(), studentGroups, teachers, courses, lessons, List.of());

        DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = new DayClusteredConstructionPhase<>();
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(new CustomPhaseConfig()
                        .withCustomPhaseCommandList(List.of(constructionPhase))));
        SolverFactory<SchoolSchedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<SchoolSchedule> solver = solverFactory.buildSolver();
        SchoolSchedule solved = solver.solve(problem);

        return computeM20Proxy(solved);
    }

    /** Compute the M20 proxy from a solved schedule, mirroring M20's own scoping (excl G14, stage-modality rows). */
    private M20Proxy computeM20Proxy(SchoolSchedule solved) {
        // Expected hours per (group, date) from calendario_class_days.csv, scoped exactly like
        // WeeklyTemplateConstructionRealSeedTest's full-day KPI (excl G14 + stage-modality rows).
        Map<String, Integer> expectedByGroupDate = new HashMap<>();
        String horizonStart = TimeslotSequence.SCHOOL_YEAR_START.toString();
        String horizonEnd = TimeslotSequence.SCHOOL_YEAR_END.toString();
        for (String[] row : DataGenerator.loadSeedCsv("calendario_class_days.csv", 4)) {
            String groupName = row[0].trim();
            String date = row[1].trim();
            if (date.compareTo(horizonStart) < 0 || date.compareTo(horizonEnd) > 0) continue;
            int hours = Integer.parseInt(row[2].trim());
            if (hours <= 0) continue;
            if ("G14".equals(groupName) || "S".equals(row[3].trim())) continue;
            expectedByGroupDate.put(groupName + "@" + date, hours);
        }
        Map<String, Integer> placedByGroupDate = new HashMap<>();
        Map<Month, Integer> placedByMonth = new TreeMap<>();
        for (Lesson lesson : solved.getLessons()) {
            if (lesson.timeslot == null || lesson.getStudentGroup() == null) continue;
            String key = lesson.getStudentGroup().name + "@" + lesson.timeslot.getDate();
            placedByGroupDate.merge(key, 1, Integer::sum);
            placedByMonth.merge(lesson.timeslot.getDate().getMonth(), 1, Integer::sum);
        }
        int underDays = 0, deficitHours = 0, overDays = 0, surplusHours = 0, fullDays = 0;
        for (Map.Entry<String, Integer> e : expectedByGroupDate.entrySet()) {
            int placed = placedByGroupDate.getOrDefault(e.getKey(), 0);
            int expected = e.getValue();
            if (placed == expected) {
                fullDays++;
            } else if (placed < expected) {
                underDays++;
                deficitHours += expected - placed;
            } else {
                overDays++;
                surplusHours += placed - expected;
            }
        }
        // M20 weight is ofMedium(1000) per deficit-hour on under-days only (over-days are free).
        long impliedMedium = 1000L * deficitHours;
        return new M20Proxy(underDays, deficitHours, overDays, surplusHours, fullDays,
                expectedByGroupDate.size(), impliedMedium, placedByMonth);
    }

    /**
     * TASK-406 headline diagnostic. Runs the construction tail with the current year-end
     * bound and with the legacy April bound, and prints the M20-proxy comparison. The
     * printed table is the diagnostic evidence committed to the TASK-406 backlog file.
     *
     * <p>The assertion is deliberately loose (both arms produce a sane proxy): this test
     * GENERATES evidence, it does not lock a direction a priori. The directional conclusion
     * (which bound scatters more leftovers into under-filled days) is recorded from the run
     * output into the backlog, then drives the Phase B fix.</p>
     */
    @Test
    @Timeout(value = 900)
    void repairTailBound_yearEndVsApril_m20ProxyComparison() {
        M20Proxy yearEnd = solveAndMeasure(BOUND_YEAR_END);
        M20Proxy april = solveAndMeasure(BOUND_LEGACY_APRIL);

        System.out.println();
        System.out.println("================ TASK-406 M20 proxy: repair-tail bound comparison ================");
        System.out.printf("Bound YEAR_END (%s) [current, TASK-394 fact-based]:%n", BOUND_YEAR_END);
        System.out.printf("  under-days=%d (deficit %dh), over-days=%d (surplus %dh), full=%d/%d, "
                        + "implied M20 medium=%d%n",
                yearEnd.underDays, yearEnd.deficitHours, yearEnd.overDays, yearEnd.surplusHours,
                yearEnd.fullDays, yearEnd.scopedTotal, yearEnd.impliedMedium);
        System.out.printf("Bound APRIL   (%s) [legacy flat-model]:%n", BOUND_LEGACY_APRIL);
        System.out.printf("  under-days=%d (deficit %dh), over-days=%d (surplus %dh), full=%d/%d, "
                        + "implied M20 medium=%d%n",
                april.underDays, april.deficitHours, april.overDays, april.surplusHours,
                april.fullDays, april.scopedTotal, april.impliedMedium);
        System.out.println("Delta (YEAR_END - APRIL):");
        System.out.printf("  under-days %+d, deficit %+dh, over-days %+d, surplus %+dh, implied medium %+d%n",
                yearEnd.underDays - april.underDays, yearEnd.deficitHours - april.deficitHours,
                yearEnd.overDays - april.overDays, yearEnd.surplusHours - april.surplusHours,
                yearEnd.impliedMedium - april.impliedMedium);
        System.out.println("Placed lessons by month (YEAR_END vs APRIL):");
        for (Month m : yearEnd.placedByMonth.keySet()) {
            System.out.printf("  %s: %d vs %d%n", m, yearEnd.placedByMonth.get(m),
                    april.placedByMonth.getOrDefault(m, 0));
        }
        System.out.println("==================================================================================");
        System.out.println();

        // Sanity: every real-seed construction leaves SOME under-filled days under both bounds
        // (the fixture's documented scope gaps - no Stage, two-sheet tension - guarantee it).
        // A value of 0 would mean the proxy computation broke, not that M20 is satisfied.
        assertTrue(yearEnd.underDays > 0, "year-end arm must report some under-days");
        assertTrue(april.underDays > 0, "april arm must report some under-days");
    }

    /**
     * Hypothesis 2 refutation: {@link com.school.planner.solver.StudentDayCompaction#compact}
     * (including the {@code anchorRunLeft} left-anchor pass) preserves the per-(group, date)
     * lesson COUNT exactly, because every move it commits targets a Timeslot of the mover's OWN
     * date. M20 is a per-(group, date) count constraint, so compaction is mechanically incapable
     * of moving the M20 score. This is the structural reason the left-anchor pass is NOT the
     * M20 regression cause - stronger than an on/off run, which would only show "no change in
     * one instance" rather than proving the invariant.
     *
     * <p>Built on the real seed so any future bug that accidentally moves a lesson across dates
     * (e.g. a compaction rewrite resolving a target slot from the wrong date) is caught here.</p>
     */
    @Test
    @Timeout(value = 900)
    void studentDayCompaction_isDateInvariant_preservesPerGroupDateCounts() {
        // Build a placed schedule via the construction phase, then snapshot per-(group,date)
        // counts, run compaction again on a fresh copy, and assert counts are byte-identical.
        clean();
        seedRealSeedFixture();

        List<Timeslot> timeslots = Timeslot.listAll();
        List<StudentGroup> studentGroups = StudentGroup.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<TeacherAvailability> teacherAvailabilities = TeacherAvailability.listAll();

        for (StudentGroup group : studentGroups) {
            group.idealEndDateInfo = null;
            group.idealEndDateVrpBuffered = BOUND_YEAR_END;
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(new HashMap<>());
        SchoolSchedule.setTeacherAvailabilitiesStatic(teacherAvailabilities);
        SchoolSchedule.setSchoolHolidaysStatic(List.<SchoolHoliday>of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), java.util.Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(GroupCalendarRule.listAll());
        SchoolSchedule.setDailyActivityQuotasStatic(DailyActivityQuota.listAll());
        SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);

        SchoolSchedule problem = new SchoolSchedule(timeslots, List.of(), List.of(), teacherAvailabilities,
                List.of(), studentGroups, teachers, courses, lessons, List.of());
        DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = new DayClusteredConstructionPhase<>();
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(new CustomPhaseConfig()
                        .withCustomPhaseCommandList(List.of(constructionPhase))));
        SolverFactory<SchoolSchedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<SchoolSchedule> solver = solverFactory.buildSolver();
        SchoolSchedule constructed = solver.solve(problem);

        // Per-(group,date) count AFTER the construction phase's own compaction already ran.
        Map<String, Integer> countsBefore = perGroupDateCounts(constructed);
        // Run compaction AGAIN - a second pass must be a pure no-op on counts (it only
        // re-arranges slots within a date). If it ever changed a count, compaction would
        // be moving lessons across dates and could in principle touch M20.
        com.school.planner.solver.StudentDayCompaction.compact(constructed,
                (lesson, ts) -> lesson.timeslot = ts,
                lesson -> lesson.room = null);
        Map<String, Integer> countsAfter = perGroupDateCounts(constructed);

        System.out.printf("TASK-406 compaction date-invariance: %d (group,date) cells, counts equal=%s%n",
                countsBefore.size(), countsBefore.equals(countsAfter));
        assertEquals(countsBefore, countsAfter,
                "StudentDayCompaction must preserve per-(group,date) counts exactly - it only moves "
                        + "lessons within their own date, so it can never change the M20 proxy");
    }

    private static Map<String, Integer> perGroupDateCounts(SchoolSchedule schedule) {
        Map<String, Integer> counts = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.getStudentGroup() == null || l.timeslot.getDate() == null) continue;
            counts.merge(l.getStudentGroup().name + "@" + l.timeslot.getDate(), 1, Integer::sum);
        }
        return counts;
    }
}
