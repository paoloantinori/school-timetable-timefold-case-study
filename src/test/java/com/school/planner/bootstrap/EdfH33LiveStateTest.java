package com.school.planner.bootstrap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.EmptyDayFinisher;
import com.school.planner.solver.ScoreCompat;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-577 EDF-H33 LIVE-STATE (7/09): measures the H33 target family on a LIVE
 * construction state (not a replay), with the CONSTRAINT PROVIDER counting H33
 * (never a manual copy). The motivating bank is the v131 delivery's two G14
 * Fridays (2h vs floor 3, the only legal third slot free, a +2 surplus Thursday
 * as gate-E-legal donor).
 *
 * <p>Arms: OFF (construction alone, H33 count) vs ON (same construction +
 * EmptyDayFinisher with {@code planner.construction.edf-h33.enabled}, H33 count
 * + moves). Pre-registered expectation: H33 ON &lt; H33 OFF, hard ON &le; hard
 * OFF. If H33=0 at the boundary the test reports "no food" and exits (the
 * honest negative, same form as the sibling harvest bench).
 *
 * <p>Gated by {@code PLANNER_TEST_PAIR_CELLS=1} (same gate as the sibling).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PAIR_CELLS", matches = ".+")
class EdfH33LiveStateTest {

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
        System.clearProperty(EmptyDayFinisher.FLAG);
        System.clearProperty(EmptyDayFinisher.H33_FLAG);
    }

    @Test
    @Timeout(value = 1200) // construction alone is ~465s on this seed (sibling measured)
    @Transactional
    void edfH33OnLiveState() {
        System.out.println("=== EDF-H33 LIVE-STATE (pre-registered: H33 down, hard <=) ===");

        seedRealSeedFixture();

        List<Timeslot> timeslots = Timeslot.listAll();
        List<StudentGroup> groups = StudentGroup.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
        List<com.school.planner.domain.TeacherAvailability> availabilities =
                com.school.planner.domain.TeacherAvailability.listAll();
        List<DailyActivityQuota> quotas = DailyActivityQuota.listAll();
        SnapshotFixture.wireConstructionStatics(timeslots, groups, lessons, teachers,
                courses, availabilities, List.of(), List.of(), List.of(), null, true, false);

        System.setProperty("planner.construction.grasp.enabled", "false");
        var constructionPhase = new com.school.planner.solver.DayClusteredConstructionPhase<SchoolSchedule>();
        SchoolSchedule problem = new SchoolSchedule(timeslots, List.<Room>of(), List.of(), availabilities,
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

        // ARM OFF: score + H33 count on the live state.
        solutionManager.update(solved);
        var offScore = (BendableScore) solved.getScore();
        int offH33 = SnapshotFixture.providerCount(solutionManager, solved, "H33");
        int offH34 = SnapshotFixture.providerCount(solutionManager, solved, "H34");
        System.out.printf("DIAG OFF: score=%s H33=%d H34=%d%n", offScore, offH33, offH34);
        // The provider counter is the instrument: a -1 (counter exception) makes
        // every downstream number fiction, so it fails the bench, not just prints.
        assertTrue(offH33 >= 0 && offH34 >= 0, "the provider counters must work (got "
                + offH33 + "/" + offH34 + ")");

        if (offH33 == 0) {
            System.out.println("SANITY: H33=0 at the boundary in the test harness: the family has no food here.");
            System.out.println("=== EDF-H33 LIVE-STATE: cells 0 -> 0 | no food ===");
            assertTrue(true, "live-state bench reached (no food)");
            return;
        }

        // ARM ON: the pass with the H33 family armed.
        System.setProperty(EmptyDayFinisher.H33_FLAG, "true");
        var result = EmptyDayFinisher.finish(solved, (l, ts) -> l.timeslot = ts);
        System.clearProperty(EmptyDayFinisher.H33_FLAG);

        solutionManager.update(solved);
        var onScore = (BendableScore) solved.getScore();
        int onH33 = SnapshotFixture.providerCount(solutionManager, solved, "H33");
        int onH34 = SnapshotFixture.providerCount(solutionManager, solved, "H34");
        System.out.printf("DIAG ON: score=%s H33=%d H34=%d moves=%d cellsClosed=%d conflicts=%d%n",
                onScore, onH33, onH34, result.movesApplied(), result.cellsClosed(), result.conflictsAdded());

        System.out.printf("=== EDF-H33 LIVE-STATE: H33 %d -> %d | H34 %d -> %d | hard %d -> %d | moves %d ===%n",
                offH33, onH33, offH34, onH34,
                ScoreCompat.hardOf(offScore), ScoreCompat.hardOf(onScore),
                result.movesApplied());
        // The family may close nothing (a negative bench is information), but it
        // may never CREATE an H33 cell: every move lands hours into an
        // under-floor cell or rolls back atomically.
        assertTrue(onH33 <= offH33, "the family must not create H33 cells ("
                + offH33 + " -> " + onH33 + ")");
        assertTrue(result.conflictsAdded() == 0,
                "the pass may not add H1/H3 conflicts (improve-or-noop contract)");
        // Hard stays print-only ON PURPOSE: the raw pass is guarded by the
        // wiring's improve-or-noop revert in the cycle, not by this harness.
    }

    /** Constraint match count via the PROVIDER's own match policy (never a manual copy). */

    // ── fixture (mirrors the sibling tests) ──────────────────────────────────────

    @Transactional
    void seedRealSeedFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers = new java.util.LinkedHashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("teachers.csv", 6)) {
            var t = new Teacher(row[0].trim());
            String maxH = row[3].trim();
            t.maxDailyHours = maxH.isEmpty() ? null : Integer.valueOf(maxH);
            t.persist();
            teachers.put(t.name, t);
        }
        SnapshotFixture.seedCoursesFromIncarichi(teachers, groups, lessonGenerator, null);
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
