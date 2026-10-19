package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.timefold.solver.core.config.solver.PreviewFeature;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import com.school.planner.solver.RoomTierOverrides;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.KempeCellNeighborhoodProvider;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.StudentDayCompaction;

import io.quarkus.test.junit.QuarkusTest;

/**
 * TASK-560 banco Kempe (26/08, decisione (b)): the Move Streams whole-cell
 * neighborhood on the h34on delivery state (10 residual H34 cells). The window is
 * a plain programmatic solver - hill climbing (acl=1, NO acceptor: hard-worsening
 * transactions are never taken), 120s cap, 45s unimproved - deliberately OUTSIDE
 * the MT-LS decider (its reflection swap of LS internals is the declared
 * integration risk). PREDICTION (locked in the registro BEFORE this bench):
 * 2..6 of the 10 cells closed, hard never worsened, wall within budget; if 0:
 * the cells are multi-start / Kempe-chain territory (TASK-482) and we declare it.
 *
 * <p><b>KNOWN LIMIT (27/08, rilancio 2 infra c):</b> this fixture only accepts
 * exports whose every assignment is inside the value range REBUILT from the CSV
 * seed. A cycle that ran with QFR v2 ON moves lessons onto cells the fixture's
 * rebuilt availability/window statics exclude, and Timefold's input validation
 * rejects the solution ("outside of the related value range", measured on the
 * dci-h34ms winner export: lesson 407 at 2027-02-25T10:00). For those states use
 * the CHECKPOINT-based bench (resume from POST_ROOM_QUOTA_FILL_REPAIR with the
 * pass flags), which runs on the live statics and has no such gap. Not fixed
 * here: aligning the fixture's statics with the live profile's is a fidelity
 * project of its own (the same caveat class as the -124.013 vs -14 baseline).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_KEMPE_CELL", matches = ".+")
class KempeCellNeighborhoodDiscriminatorTest {

    private static final java.nio.file.Path H34ON_EXPORT =
            java.nio.file.Path.of("claudedocs", "spalmone-2026-27", "task557-results",
                    "ciclo-h34-on", "export.json.gz");

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    /** H34 in-scope empty declared cells, recomputed from the statics + the live lessons. */
    private static Map<String, Integer> emptyDeclaredCells(SchoolSchedule problem) {
        Map<String, Integer> byGroup = new HashMap<>();
        Map<Long, Map<LocalDate, Integer>> hours = new HashMap<>();
        for (Lesson l : problem.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) {
                continue;
            }
            var t = l.getTeacher();
            if (t != null && t.pseudo) {
                continue;
            }
            hours.computeIfAbsent(l.course.studentGroup.id, k -> new HashMap<>())
                    .merge(l.timeslot.getDate(), 1, Integer::sum);
        }
        for (var g : problem.getStudentGroups()) {
            if (g == null || g.id == null
                    || SchoolScheduleConstraintProvider.isQuotaFillExcluded(g.name)) {
                continue;
            }
            for (var row : SchoolSchedule.getExpectedHoursForGroup(g.id).entrySet()) {
                if (row.getValue().aulaHours + row.getValue().labHours > 0
                        && hours.getOrDefault(g.id, Map.of()).getOrDefault(row.getKey(), 0) == 0) {
                    byGroup.merge(g.name, 1, Integer::sum);
                }
            }
        }
        return byGroup;
    }

    private static int totalCells(Map<String, Integer> byGroup) {
        return byGroup.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Test
    @Timeout(value = 420)
    @Transactional
    void kempeWholeCellWindowOnH34Residuals() throws Exception {
        var snap =
                SnapshotFixture.loadFromExport(H34ON_EXPORT, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        SchoolSchedule problem = snap.problem;

        // E2-quater arm (27/08): PLANNER_TEST_KEMPE_CELL=zero runs the SAME window
        // under RoomTierOverrides.roomZero() - the distraction test for the whole-cell
        // composite (the original 0/10 was priced with the room mass in the sum). Any
        // other non-empty value keeps the original full-weight arm.
        boolean zeroArm = "zero".equalsIgnoreCase(System.getenv("PLANNER_TEST_KEMPE_CELL"));
        if (zeroArm) {
            problem.setConstraintWeightOverrides(RoomTierOverrides.roomZero());
        }

        Map<String, Integer> before = emptyDeclaredCells(problem);
        int cellsBefore = totalCells(before);
        assertEquals(10, cellsBefore, "the h34on delivery carries exactly the 10 residual cells");
        assertEquals(0, StudentDayCompaction.countConflicts(problem), "grid starts conflict-free");

        LocalSearchPhaseConfig phase = new LocalSearchPhaseConfig();
        phase.setNeighborhoodProviderClass(KempeCellNeighborhoodProvider.class);
        phase.setForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1));
        phase.setTerminationConfig(new TerminationConfig()
                .withSecondsSpentLimit(120L)
                .withUnimprovedSecondsSpentLimit(45L));
        SolverConfig config = new SolverConfig()
                .withPreviewFeature(PreviewFeature.NEIGHBORHOODS)
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withConstraintProviderClass(SchoolScheduleConstraintProvider.class)
                .withPhases(phase);
        SolverFactory<SchoolSchedule> factory = SolverFactory.create(config);
        Solver<SchoolSchedule> solver = factory.buildSolver();
        int[] improvingSteps = {0};
        solver.addEventListener(e -> improvingSteps[0]++);

        System.out.println("=== TASK-560 banco Kempe (whole-cell Move Streams window) ===");
        System.out.println("  braccio: " + (zeroArm ? "room-ZERO (E2-quater)" : "pieno (originale)"));
        System.out.println("  celle vuote prima: " + cellsBefore + " " + before);
        long t0 = System.currentTimeMillis();
        SchoolSchedule result = solver.solve(problem);
        long wall = System.currentTimeMillis() - t0;

        Map<String, Integer> after = emptyDeclaredCells(result);
        int cellsAfter = totalCells(after);
        System.out.println("  celle vuote dopo: " + cellsAfter + " " + after);
        System.out.println("  wall: " + wall + "ms");
        System.out.println("  score finale (regime del braccio): " + result.getScore());
        System.out.println("  miglioramenti accettati (best-solution changes): " + improvingSteps[0]);
        if (zeroArm) {
            // the roomability bill: the SAME final grid priced at FULL weights (the
            // count B-ROOM will pay if this window ever ships into the cycle)
            result.setConstraintWeightOverrides(null);
            BendableScore full = (BendableScore) SolutionManager.create(
                    SolverFactory.create(new SolverConfig()
                            .withSolutionClass(SchoolSchedule.class)
                            .withEntityClasses(Lesson.class)
                            .withConstraintProviderClass(SchoolScheduleConstraintProvider.class)))
                    .update(result);
            System.out.println("  score finale A PESI PIENI (conto roomability): " + full);
        }

        assertTrue(cellsAfter <= cellsBefore, "hill climbing never opens a cell it closed");
        assertTrue(result.getScore() != null, "the solver returned a scored solution");
        if (snap.problem.getScore() != null) {
            assertTrue(ScoreCompat.hardOf(result.getScore()) <= ScoreCompat.hardOf(snap.problem.getScore()),
                    "hard never worsens under hill climbing (was " + snap.problem.getScore()
                            + ", is " + result.getScore() + ")");
        }
        System.out.println("  CELLE CHIUSE: " + (cellsBefore - cellsAfter) + " / " + cellsBefore);
    }
}
