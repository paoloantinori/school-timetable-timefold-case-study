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
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.ScoreCompat;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-590 PIN DRAIN BENCH (7/09 notte): the GO-pin cycle's pre-registered
 * question, measured LOCALLY in minutes instead of one 8h cycle: does an LS
 * on the construction output open declared cells (H34 grows), and does the
 * pin prevent it? Four solves on the real seed:
 * <ol>
 *   <li>construction only, pin OFF  -> H34_c_off (the known 22) + H33_c_off
 *   <li>construction only, pin ON   -> must equal the above (the pin runs at
 *       construction END: it cannot alter the stamp) - the sanity arm
 *   <li>construction + LS, pin OFF  -> H34_ls_off: the DRAIN measurement
 *   <li>construction + LS, pin ON   -> H34_ls_on: the PROTECTION measurement
 * </ol>
 * Pre-registered reading: if H34_ls_off == H34_c_off the LS does not drain
 * and the pin protects nothing (the flag dies here); if H34_ls_off &gt;
 * H34_c_off and H34_ls_on &lt; H34_ls_off the pin has value. The LS is the
 * production phase-2 (compactness) with a 900s wall cap - indicative of the
 * drain mechanism, not the cycle's exact B-TIMESLOT geometry (which runs on
 * the PROMOTED grid; this bench runs on the raw construction output).
 *
 * <p>Gated by {@code PLANNER_TEST_PIN_BENCH=1}. Deterministic construction
 * (grasp off); the LS keeps the production move selectors.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PIN_BENCH", matches = ".+")
class PinDrainBenchTest {

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
        System.clearProperty("planner.construction.pin-full-cells.enabled");
        System.clearProperty("planner.construction.grasp.enabled");
    }

    @Test
    @Timeout(value = 3600)
    // NO @Transactional on the method: the four solves run ~16 min inside one
    // JTA transaction otherwise, and Narayana aborts the commit (arm D died at
    // 988s with RollbackException on 7/09). The seeding and the cleanup keep
    // their own transactions.
    void pinDrainBench() {
        System.out.println("=== PIN DRAIN BENCH (pre-registered: does the LS open declared cells; does the pin prevent it) ===");
        seedRealSeedFixture();
        var wires = wireStatics();

        // Arm A: construction only, pin OFF.
        System.clearProperty("planner.construction.pin-full-cells.enabled");
        var solvedA = solve(wires, false);
        int h34cOff = SnapshotFixture.providerCount(solutionManager, solvedA, "H34");
        int h33cOff = SnapshotFixture.providerCount(solutionManager, solvedA, "H33");
        System.out.printf("DIAG A (construction, pin OFF): H34=%d H33=%d%n", h34cOff, h33cOff);

        // Arm B: construction only, pin ON - must be identical (the pin runs at
        // construction end, after the stamp; it cannot change H34/H33 counts).
        System.setProperty("planner.construction.pin-full-cells.enabled", "true");
        var solvedB = solve(wires, false);
        int h34cOn = SnapshotFixture.providerCount(solutionManager, solvedB, "H34");
        int h33cOn = SnapshotFixture.providerCount(solutionManager, solvedB, "H33");
        System.out.printf("DIAG B (construction, pin ON): H34=%d H33=%d (sanity: == A)%n", h34cOn, h33cOn);

        // Arm C: construction + LS, pin OFF - the drain measurement.
        System.clearProperty("planner.construction.pin-full-cells.enabled");
        var solvedC = solve(wires, true);
        solutionManager.update(solvedC);
        var scoreC = (BendableScore) solvedC.getScore();
        int h34LsOff = SnapshotFixture.providerCount(solutionManager, solvedC, "H34");
        int h33LsOff = SnapshotFixture.providerCount(solutionManager, solvedC, "H33");
        System.out.printf("DIAG C (construction+LS, pin OFF): score=%s H34=%d H33=%d (drain: %d new H34 cells)%n",
                scoreC, h34LsOff, h33LsOff, h34LsOff - h34cOff);

        // Arm D: construction + LS, pin ON - the protection measurement.
        System.setProperty("planner.construction.pin-full-cells.enabled", "true");
        var solvedD = solve(wires, true);
        solutionManager.update(solvedD);
        var scoreD = (BendableScore) solvedD.getScore();
        int h34LsOn = SnapshotFixture.providerCount(solutionManager, solvedD, "H34");
        int h33LsOn = SnapshotFixture.providerCount(solutionManager, solvedD, "H33");
        System.out.printf("DIAG D (construction+LS, pin ON): score=%s H34=%d H33=%d (drain: %d new H34 cells)%n",
                scoreD, h34LsOn, h33LsOn, h34LsOn - h34cOn);

        System.out.printf("=== PIN DRAIN BENCH: drain OFF=%d ON=%d (A/B sanity %d/%d) | H33 %d/%d -> %d/%d ===%n",
                h34LsOff - h34cOff, h34LsOn - h34cOn, h34cOff, h34cOn, h33cOff, h33cOn, h33LsOff, h33LsOn);
        System.out.printf("PIN VERDICT INPUT: LS-drains=%b pin-protects=%b pin-cost-hard=%d%n",
                h34LsOff > h34cOff,
                h34LsOff > h34cOff && h34LsOn < h34LsOff,
                ScoreCompat.hardOf(scoreD) - ScoreCompat.hardOf(scoreC));
        assertTrue(h34cOff >= 0 && h34cOn >= 0, "the provider counters must work");
    }

    // ── the two solve shapes ─────────────────────────────────────────────

    private record Wires(List<Timeslot> timeslots, List<StudentGroup> groups,
                         List<Lesson> lessons, List<Teacher> teachers,
                         List<Course> courses,
                         List<com.school.planner.domain.TeacherAvailability> availabilities,
                         List<DailyActivityQuota> quotas) {
    }

    /** The PRODUCTION compactness LS phase (phase 3 of solverConfig.xml), with
     *  the bench's wall cap: the real move selectors (union pillar/tabu etc.),
     *  never Timefold's defaults - a default-selector LS would not measure the
     *  production drain. Loaded once, cached. */
    private static LocalSearchPhaseConfig productionLsPhase() {
        if (LS_PHASE == null) {
            SolverConfig prod = SolverConfig.createFromXmlResource("/solverConfig.xml");
            LS_PHASE = prod.getPhaseConfigList().stream()
                    .filter(LocalSearchPhaseConfig.class::isInstance)
                    .map(LocalSearchPhaseConfig.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no localSearch phase in solverConfig.xml"));
        }
        LS_PHASE.setTerminationConfig(new TerminationConfig().withSecondsSpentLimit(900L));
        return LS_PHASE;
    }

    private static LocalSearchPhaseConfig LS_PHASE;

    /** Solves construction (+ the production compactness LS when {@code withLs}).
     *  Each solve re-seeds the lesson set: the harness DB rows are reused, so
     *  the lessons are re-listed and their timeslots reset (solve() clones its
     *  input, so arm A's placements never leak into arm B). */
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

        SolverConfig config = new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(com.school.planner.solver.SchoolScheduleConstraintProvider.class))
                .withPhaseList(new java.util.ArrayList<>(List.of(
                        new CustomPhaseConfig().withCustomPhaseCommandList(List.of(
                                new com.school.planner.solver.DayClusteredConstructionPhase<SchoolSchedule>())))));
        if (withLs) {
            config.getPhaseConfigList().add(productionLsPhase());
        }
        System.setProperty("planner.construction.grasp.enabled", "false");
        var solved = ai.timefold.solver.core.api.solver.SolverFactory
                .<SchoolSchedule>create(SnapshotFixture.applyEnvironmentMode(config)).buildSolver().solve(problem);
        System.clearProperty("planner.construction.grasp.enabled");
        return solved;
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
        return new Wires(w.timeslots(), w.groups(), lessons, w.teachers(), w.courses(),
                w.availabilities(), w.quotas());
    }


    // ── fixture (mirrors the sibling tests) ──────────────────────────────

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
