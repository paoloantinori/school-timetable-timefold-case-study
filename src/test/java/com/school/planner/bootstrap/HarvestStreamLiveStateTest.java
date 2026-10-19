package com.school.planner.bootstrap;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;

import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.ScoreCompat;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.solver.neighborhood.HarvestMoveListFactory;

/**
 * TASK-589 THE SPIKE'S PROOF: does the harvest stream change what the LS sees?
 * Same live construction state, same LS budget, arms differ ONLY by the flag:
 * OFF (the union as today) vs ON (the union + the gated harvest child).
 * Pre-registered expectation: arm ON closes H34 cells the plain LS cannot
 * sample (the long-range jump is ~15Mx out of its budget), so H34(ON) &lt;
 * H34(OFF) at equal budget; hard(ON) &lt;= hard(OFF).
 *
 * <p>Gated by {@code PLANNER_TEST_STREAM=1} (the live-state family form).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_STREAM", matches = ".+")
class HarvestStreamLiveStateTest {

    @Inject EntityManager em;
    @Inject com.school.planner.service.LessonGenerator lessonGenerator;
    @Inject DataGenerator dataGenerator;
    @Inject com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
        System.clearProperty(HarvestMoveListFactory.FLAG);
    }

    @Test
    @Timeout(value = 900)
    void harvestStreamLiveState() {
        System.out.println("=== HARVEST-STREAM LIVE-STATE (pre-registered: H34 ON < OFF, hard ON <= OFF) ===");

        // review C1: solve() does NOT mutate its input (the 4/09 instance-bug
        // contract) - the RETURN is the searched state, the passed one is intact.
        // The first bench discarded it and measured the construction twice: fixed.
        SchoolSchedule offState = buildLiveConstructionState();
        SchoolSchedule onState = buildLiveConstructionState();

        // OFF arm: the union as configured today (the factory child is inert: flag off)
        System.clearProperty(HarvestMoveListFactory.FLAG);
        SchoolSchedule offSolved = runLocalSearch(offState, 90L);
        // ON arm: same budget, the gated harvest child armed
        System.setProperty(HarvestMoveListFactory.FLAG, "true");
        SchoolSchedule onSolved = runLocalSearch(onState, 90L);
        System.clearProperty(HarvestMoveListFactory.FLAG);

        int offH34 = countH34(offSolved);
        int onH34 = countH34(onSolved);
        System.out.printf("=== HARVEST-STREAM LIVE-STATE: H34 %d -> %d | hard %s -> %s ===%n",
                offH34, onH34,
                ScoreCompat.hardOf((BendableScore) offSolved.getScore()),
                ScoreCompat.hardOf((BendableScore) onSolved.getScore()));
        System.out.println("VERDICT-raw: dati grezzi, la decisione e' di Fedora sui raw");
    }

    private SchoolSchedule runLocalSearch(SchoolSchedule state, Long unimprovedSeconds) {
        // the union comes from the production solverConfig.xml (including the
        // harvest child, whose inertness is governed by the flag): the bench
        // MUST run the configured union, not a bare default
        var solver = ai.timefold.solver.core.api.solver.SolverFactory
                .<SchoolSchedule>create(parseUnionFromConfig(unimprovedSeconds)).buildSolver();
        return solver.solve(state);
    }

    /** The bench MUST run the configured union (with the harvest child): load the
     *  production solverConfig's LS phase rather than a bare default. */
    private ai.timefold.solver.core.config.solver.SolverConfig parseUnionFromConfig(Long unimprovedSeconds) {
        try {
            var loaded = ai.timefold.solver.core.config.solver.SolverConfig.createFromXmlResource(
                    "solverConfig.xml");
            loaded.setSolutionClass(SchoolSchedule.class);
            loaded.setEntityClassList(List.of(Lesson.class));
            loaded.setEnvironmentMode(null);
            // keep ONLY the first local search phase (phase 2: the union lives there)
            var phases = loaded.getPhaseConfigList();
            var firstLs = phases.stream()
                    .filter(p -> p instanceof ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig)
                    .findFirst().orElseThrow();
            // shorten its termination for the bench
            firstLs.setTerminationConfig(new ai.timefold.solver.core.config.solver.termination.TerminationConfig()
                    .withUnimprovedSecondsSpentLimit(unimprovedSeconds)
                    .withSecondsSpentLimit(300L));
            loaded.setPhaseConfigList(List.of(firstLs));
            loaded.setTerminationConfig(null);
            return loaded;
        } catch (Exception e) {
            throw new IllegalStateException("the bench cannot load the configured union", e);
        }
    }

    private int countH34(SchoolSchedule schedule) {
        var factory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager)
                .getScoreDirectorFactory();
        try (var sd = factory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            sd.setWorkingSolution(schedule);
            sd.calculateScore();
            for (var t : sd.getConstraintMatchTotalMap().values()) {
                if (t.getConstraintRef().id().startsWith("H34")) {
                    return t.getConstraintMatchCount();
                }
            }
        } catch (Exception e) {
            System.out.printf("DIAG countH34 exception: %s%n", e.getMessage());
        }
        return -1;
    }

    /** Two identical live construction states (deterministic build, seeded once). */
    private SchoolSchedule buildLiveConstructionState() {
        // re-seed per arm is too slow: build ONCE, deep-copy placements for arm 2
        if (built == null) {
            built = buildConstructionFresh();
        }
        return copyWithPlacements(built);
    }

    private SchoolSchedule built;

    private SchoolSchedule buildConstructionFresh() {
        seedRealSeedFixture();
        List<Timeslot> timeslots = Timeslot.listAll();
        List<StudentGroup> groups = StudentGroup.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<com.school.planner.domain.Teacher> teachers = com.school.planner.domain.Teacher.listAll();
        List<com.school.planner.domain.Course> courses = com.school.planner.domain.Course.listAll();
        List<com.school.planner.domain.TeacherAvailability> availabilities =
                com.school.planner.domain.TeacherAvailability.listAll();
        for (var g : groups) g.idealEndDateVrpBuffered = TimeslotSequence.SCHOOL_YEAR_END;
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setTeacherAvailabilitiesStatic(availabilities);
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.of(), Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(
                com.school.planner.domain.GroupCalendarRule.listAll());
        List<DailyActivityQuota> quotas = DailyActivityQuota.listAll();
        SchoolSchedule.setDailyActivityQuotasStatic(quotas);
        SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);

        System.setProperty("planner.construction.grasp.enabled", "false");
        var constructionPhase = new com.school.planner.solver.DayClusteredConstructionPhase<SchoolSchedule>();
        SchoolSchedule problem = new SchoolSchedule(timeslots, List.of(), List.of(), availabilities,
                List.of(), groups, teachers, courses, lessons, List.of());
        problem.setDailyActivityQuotas(quotas);
        var solverConfig = SnapshotFixture.applyEnvironmentMode(new ai.timefold.solver.core.config.solver.SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(com.school.planner.solver.SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(new ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig()
                        .withCustomPhaseCommandList(List.of(constructionPhase)))));
        var solved = ai.timefold.solver.core.api.solver.SolverFactory
                .<SchoolSchedule>create(solverConfig).buildSolver().solve(problem);
        System.clearProperty("planner.construction.grasp.enabled");
        return solved;
    }

    private SchoolSchedule copyWithPlacements(SchoolSchedule source) {
        SchoolSchedule copy = new SchoolSchedule(source.getTimeslots(), source.getRooms(),
                source.getStagePatterns(), source.getTeacherAvailabilities(), source.getSchoolHolidays(),
                source.getStudentGroups(), source.getTeachers(), source.getCourses(),
                cloneLessons(source), source.getLabAssistants());
        copy.setDailyActivityQuotas(source.getDailyActivityQuotas());
        return copy;
    }

    private List<Lesson> cloneLessons(SchoolSchedule source) {
        // the LS works on the schedule's own lesson instances: fresh clones per arm
        // (timeslot/room/pinned copied; ids preserved for the statics)
        List<Lesson> clones = new java.util.ArrayList<>();
        for (Lesson l : source.getLessons()) {
            Lesson c = new Lesson();
            c.id = l.id;
            c.course = l.course;
            c.timeslot = l.timeslot;
            c.room = l.room;
            c.pinned = l.pinned;
            c.promotedPin = l.promotedPin;
            clones.add(c);
        }
        return clones;
    }

    @Transactional
    void seedRealSeedFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, com.school.planner.domain.Teacher> teachers = new java.util.LinkedHashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("teachers.csv", 6)) {
            var t = new com.school.planner.domain.Teacher(row[0].trim());
            String maxH = row[3].trim();
            t.maxDailyHours = maxH.isEmpty() ? null : Integer.valueOf(maxH);
            t.persist();
            teachers.put(t.name, t);
        }
        var aggregation = DataGenerator.aggregateCourseHours(DataGenerator.loadSeedCsv("incarichi.csv", 4));
        for (var entry : aggregation.hoursByKey().entrySet()) {
            var key = entry.getKey();
            var teacher = teachers.get(key.teacherName());
            var group = groups.get(key.groupName());
            if (teacher == null || group == null) continue;
            var course = new com.school.planner.domain.Course(key.subject(), teacher, group, entry.getValue());
            course.persist();
            lessonGenerator.generateForCourse(course.id);
        }
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
        for (String[] row : DataGenerator.loadSeedCsv("availability_rules.csv", 8)) {
            var teacher = teachers.get(row[0].trim());
            if (teacher == null) continue;
            new com.school.planner.domain.RecurringAvailabilityRule(teacher,
                    java.time.DayOfWeek.valueOf(row[1].trim()),
                    Integer.parseInt(row[2].trim()), Integer.parseInt(row[3].trim()),
                    com.school.planner.domain.AvailabilityType.valueOf(row[4].trim()),
                    row[5].isBlank() ? null : java.time.LocalDate.parse(row[5].trim()),
                    row[6].isBlank() ? null : java.time.LocalDate.parse(row[6].trim()), row[7].trim())
                    .persist();
        }
        availabilityExpansionService.expandAll();
    }
}
