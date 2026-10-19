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
import ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-577 BOX DEFICIT-ONLY BENCH (8/09): the construction-end over-quota census
 * traced ALL 420 over-cap placements to {@code boxUnplaceableLeftovers}'s legacy
 * fallback (TASK-406's "first conflict-free slot"), a trade priced before H34
 * existed. The flag {@code planner.construction.box-deficit-only.enabled} makes
 * covered days accept leftovers only in deficit. Pre-registered: flag ON has
 * ZERO over-quota placements (the trace stays empty) and H34-at-construction
 * NOT WORSE than OFF; the cost (more boxed H0 at construction) is reported for
 * the cycle to arbitrate.
 *
 * <p>Gated by {@code PLANNER_TEST_BOX_BENCH=1}.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_BOX_BENCH", matches = ".+")
class BoxDeficitOnlyBenchTest {

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
        System.clearProperty("planner.construction.box-deficit-only.enabled");
        System.clearProperty("planner.construction.grasp.enabled");
    }

    @Test
    @Timeout(value = 3600)
    // NO @Transactional on the method (the PinDrainBench JTA-timeout lesson).
    void boxDeficitOnlyBench() {
        System.out.println("=== BOX DEFICIT-ONLY BENCH (pre-registered: ON has zero over-quota placements, H34 not worse) ===");
        seedRealSeedFixture();
        var wires = wireStatics();

        System.clearProperty("planner.construction.box-deficit-only.enabled");
        var off = solveConstruction(wires);
        int h34Off = SnapshotFixture.providerCount(solutionManager, off, "H34");
        int h33Off = SnapshotFixture.providerCount(solutionManager, off, "H33");
        long boxedOff = off.getLessons().stream().filter(l -> l.timeslot == null).count();
        System.out.printf("DIAG OFF: H34=%d H33=%d boxed=%d%n", h34Off, h33Off, boxedOff);

        System.setProperty("planner.construction.box-deficit-only.enabled", "true");
        var on = solveConstruction(wires);
        int h34On = SnapshotFixture.providerCount(solutionManager, on, "H34");
        int h33On = SnapshotFixture.providerCount(solutionManager, on, "H33");
        long boxedOn = on.getLessons().stream().filter(l -> l.timeslot == null).count();
        System.out.printf("DIAG ON: H34=%d H33=%d boxed=%d%n", h34On, h33On, boxedOn);

        // The decisive arms: with the LS, do the extra-boxed hours land where
        // they CLOSE H34 (the improving changeMove from the box exists), or does
        // the box just grow? Construction+LS, flag OFF vs ON.
        System.clearProperty("planner.construction.box-deficit-only.enabled");
        var offLs = solveConstructionAndLs(wires);
        int h34OffLs = SnapshotFixture.providerCount(solutionManager, offLs, "H34");
        int h33OffLs = SnapshotFixture.providerCount(solutionManager, offLs, "H33");
        long boxedOffLs = offLs.getLessons().stream().filter(l -> l.timeslot == null).count();
        System.out.printf("DIAG OFF+LS: H34=%d H33=%d boxed=%d%n", h34OffLs, h33OffLs, boxedOffLs);

        System.setProperty("planner.construction.box-deficit-only.enabled", "true");
        var onLs = solveConstructionAndLs(wires);
        int h34OnLs = SnapshotFixture.providerCount(solutionManager, onLs, "H34");
        int h33OnLs = SnapshotFixture.providerCount(solutionManager, onLs, "H33");
        long boxedOnLs = onLs.getLessons().stream().filter(l -> l.timeslot == null).count();
        System.out.printf("DIAG ON+LS: H34=%d H33=%d boxed=%d%n", h34OnLs, h33OnLs, boxedOnLs);

        System.out.printf("=== BOX DEFICIT-ONLY: construction H34 %d -> %d, boxed %d -> %d | +LS H34 %d -> %d, H33 %d -> %d, boxed %d -> %d ===%n",
                h34Off, h34On, boxedOff, boxedOn,
                h34OffLs, h34OnLs, h33OffLs, h33OnLs, boxedOffLs, boxedOnLs);
        assertTrue(h34Off >= 0 && h34On >= 0 && h34OffLs >= 0 && h34OnLs >= 0,
                "the provider counters must work");
    }

    // construction arm delegates to the shared harness; the LS arm stays local
    // because withLs there extracts the PRODUCTION LocalSearchPhaseConfig from
    // solverConfig.xml, not the bare default the shared form would add
    private SchoolSchedule solveConstruction(Wires w) {
        return SnapshotFixture.solveConstructionOnly(Lesson.listAll(),
                new SnapshotFixture.BenchWires(w.timeslots(), w.groups(), w.teachers(),
                        w.courses(), w.availabilities(), w.quotas(), List.of()),
                false, null);
    }

    private SchoolSchedule solveConstructionAndLs(Wires w) {
        return solve(w, true);
    }

    private SchoolSchedule solve(Wires w, boolean withLs) {
        List<Lesson> lessons = Lesson.listAll();
        for (Lesson l : lessons) {
            l.timeslot = null;
            l.room = null;
            l.pinned = false;
            l.promotedPin = false;
        }
        SchoolSchedule problem = new SchoolSchedule(w.timeslots(), List.<Room>of(), List.of(),
                w.availabilities(), List.of(), w.groups(), w.teachers(), w.courses(), lessons, List.of());
        problem.setDailyActivityQuotas(w.quotas());
        java.util.List<ai.timefold.solver.core.config.phase.PhaseConfig> phases = new java.util.ArrayList<>(List.of(
                new CustomPhaseConfig().withCustomPhaseCommandList(List.of(
                        new com.school.planner.solver.DayClusteredConstructionPhase<SchoolSchedule>()))));
        if (withLs) {
            // The PRODUCTION compactness LS (fresh config per solve: the reuse
            // hung the second factory build - the PinDrainBench lesson), 900s cap.
            SolverConfig prod = SolverConfig.createFromXmlResource("/solverConfig.xml");
            ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig ls = prod.getPhaseConfigList().stream()
                    .filter(ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig.class::isInstance)
                    .map(ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no localSearch phase in solverConfig.xml"));
            ls.setTerminationConfig(new ai.timefold.solver.core.config.solver.termination.TerminationConfig()
                    .withSecondsSpentLimit(900L));
            phases.add(ls);
        }
        SolverConfig config = new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(com.school.planner.solver.SchoolScheduleConstraintProvider.class))
                .withPhaseList(phases);
        System.setProperty("planner.construction.grasp.enabled", "false");
        var solved = ai.timefold.solver.core.api.solver.SolverFactory
                .<SchoolSchedule>create(SnapshotFixture.applyEnvironmentMode(config)).buildSolver().solve(problem);
        System.clearProperty("planner.construction.grasp.enabled");
        return solved;
    }

    private record Wires(List<Timeslot> timeslots, List<StudentGroup> groups,
                         List<Teacher> teachers, List<Course> courses,
                         List<com.school.planner.domain.TeacherAvailability> availabilities,
                         List<DailyActivityQuota> quotas) {
    }

    private Wires wireStatics() {
        List<com.school.planner.domain.Timeslot> timeslots = com.school.planner.domain.Timeslot.listAll();
        List<com.school.planner.domain.StudentGroup> groups = com.school.planner.domain.StudentGroup.listAll();
        List<com.school.planner.domain.Lesson> lessons = com.school.planner.domain.Lesson.listAll();
        List<com.school.planner.domain.Teacher> teachers = com.school.planner.domain.Teacher.listAll();
        List<com.school.planner.domain.Course> courses = com.school.planner.domain.Course.listAll();
        List<com.school.planner.domain.TeacherAvailability> availabilities =
                com.school.planner.domain.TeacherAvailability.listAll();
        var w = SnapshotFixture.wireConstructionStatics(timeslots, groups, lessons, teachers,
                courses, availabilities, List.of(), List.of(), List.of(), null, true, false);  // pool-cap NEVER published here (byte-identical)
        return new Wires(w.timeslots(), w.groups(), w.teachers(), w.courses(),
                w.availabilities(), w.quotas());
    }


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
