package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.AvailabilityType;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.service.LessonGenerator;
import com.school.planner.solver.DayClusteredConstructionPhase;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.solver.SchoolScheduleConstraintProvider;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * TASK-542 iter-20-ter SURGICAL test: run the CONSTRUCTION phase on the CANONICAL real-seed
 * fixture (same setup as {@link WeeklyTemplateConstructionRealSeedTest}, this same package
 * so no reflection is needed for the seed APIs) with the day-compact flag ON, and read the
 * fabricator's counters off the phase's public getters. This is the definitive diagnosis of
 * the app-level 0-placed observation: if placed is 0 here too, the printed decline examples
 * say why on the committed code.
 */
@QuarkusTest
class DayCompactGraftSurgicalTest {

    /** Mirrors {@link WeeklyTemplateConstructionRealSeedTest}'s own test-local constant. */
    private static final String DAY_COMPACT_FLAG = "planner.construction.day-compact.enabled";

    private static final Map<String, Integer> MAX_HOURS_PER_DAY = RealSeedFixture.MAX_HOURS_PER_DAY;

    @Inject
    DataGenerator dataGenerator;

    @Inject
    LessonGenerator lessonGenerator;

    @Inject
    AvailabilityExpansionService availabilityExpansionService;

    @Inject
    EntityManager em;

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
        em.createQuery("DELETE FROM Room").executeUpdate();
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
        System.clearProperty(DAY_COMPACT_FLAG);
        System.setProperty(com.school.planner.service.HybridPhase1aService.CONSTRAINED_TEACHER_FLAG, "false");
    }

    @Test
    @Timeout(value = 400)
    @Transactional
    void dayCompactGraftPlacesCellsOnTheCanonicalRealSeed() {
        System.setProperty(DAY_COMPACT_FLAG, "true");
        try {
            seedCanonicalFixture();
            DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = solveConstructionOnly();

            int placed = constructionPhase.getDayCompactPlaced();
            System.out.println("SURGICAL: dayCompactPlaced=" + placed
                    + " declined=" + constructionPhase.getDayCompactDeclined()
                    + " truncated=" + constructionPhase.getDayCompactTruncated()
                    + " examples=" + constructionPhase.getDayCompactDeclineExamples());
            var stampStats = constructionPhase.getStampStats();
            System.out.println("SURGICAL stamp stats: placed=" + stampStats.placed()
                    + " skipConflict=" + stampStats.skipConflict()
                    + " skipIllegalOrClosed=" + stampStats.skipIllegalOrClosed()
                    + " skipExhausted=" + stampStats.skipExhausted());
            assertTrue(placed > 0, "the graft must place cells on the canonical real seed (placed="
                    + placed + ", examples=" + constructionPhase.getDayCompactDeclineExamples() + ")");
        } finally {
            System.clearProperty(DAY_COMPACT_FLAG);
        }
    }

    /** Copy of {@code WeeklyTemplateConstructionRealSeedTest.seedRealSeedFixture} (canonical). */
    @Transactional
    void seedCanonicalFixture() {
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
            DayOfWeek dayOfWeek = DayOfWeek.valueOf(row[1].trim());
            int minSlot = Integer.parseInt(row[2].trim());
            int maxSlot = Integer.parseInt(row[3].trim());
            AvailabilityType type = AvailabilityType.valueOf(row[4].trim());
            LocalDate effectiveFrom = row[5].isBlank() ? null : LocalDate.parse(row[5].trim());
            LocalDate effectiveTo = row[6].isBlank() ? null : LocalDate.parse(row[6].trim());
            new RecurringAvailabilityRule(teacher, dayOfWeek, minSlot, maxSlot, type, effectiveFrom, effectiveTo, row[7].trim())
                    .persist();
        }
        availabilityExpansionService.expandAll();
    }

    /** Construction-only solve (copy of the shared helper in WeeklyTemplateConstructionRealSeedTest). */
    private DayClusteredConstructionPhase<SchoolSchedule> solveConstructionOnly() {
        List<Timeslot> timeslots = Timeslot.listAll();
        List<StudentGroup> studentGroups = StudentGroup.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<TeacherAvailability> teacherAvailabilities = TeacherAvailability.listAll();

        assertTrue(lessons.size() > 1000, "expected the real incarichi.csv volume, got " + lessons.size());

        for (StudentGroup group : studentGroups) {
            group.idealEndDateVrpBuffered = TimeslotSequence.SCHOOL_YEAR_END;
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
        solver.solve(problem);
        return constructionPhase;
    }
}
