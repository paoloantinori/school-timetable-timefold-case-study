package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.school.planner.domain.SchoolSchedule;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * TASK-536 follow-up (H2 wiring): harness reproduction of the delivered grid's
 * room problem. The h33b-536 combined run ended with 2380 H2 room conflicts
 * (-2.38M hard) ALTHOUGH the TASK-498 delivery refinement ran (605s, banner in
 * the log). This harness loads the DELIVERED export (the frozen conflicting
 * grid), scores it, re-runs the room-only phases (the same
 * {@code ScheduleService.roomPhases} extraction stage B-ROOM uses), and scores
 * again: if the harness cures H2, the live failure was an interaction (prime
 * suspect: statics re-published mid-refinement by concurrent REST loads - the
 * operator's polls fell inside the refinement window), not the refinement; if
 * it does not, the grid is structurally unrefinable and the blocker is
 * upstream (tag saturation per timeslot).
 *
 * <p>Enabled by {@code PLANNER_TEST_ROOMREF=/path/to/export.json[.gz]}.</p>
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_ROOMREF", matches = ".+")
class RoomRefinementCheckpointTest {

    @Inject com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject ai.timefold.solver.core.api.solver.SolutionManager<SchoolSchedule, BendableScore> solutionManager;
    @Inject jakarta.persistence.EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    @Timeout(value = 1500)
    void roomPhasesCureTheDeliveredGridsH2() throws Exception {
        Path export = Path.of(System.getenv("PLANNER_TEST_ROOMREF"));
        // the load persists entities (needs a tx) but the 10-min solve must NOT hold
        // one (the JTA reaper cancels long transactions): short transaction for the
        // load/seed, then the solve runs untransacted
        final SnapshotFixture.Snapshot snap = io.quarkus.narayana.jta.QuarkusTransaction
                .requiringNew().call(() -> {
                    try {
                        SnapshotFixture.Snapshot s = SnapshotFixture.loadFromExport(
                                export, availabilityExpansionService, true);
                        SnapshotFixture.seedMediumTierFacts(dataGenerator, s);
                        return s;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        SchoolSchedule problem = snap.problem;
        // discriminating variant (TASK-536 H2): frees every NON-pinned room,
        // mirroring the production delivery refinement's released input. On the
        // h33b-run1 export the residual pinned H2 (the Stage display-room pile)
        // stays: that birth defect is fixed at the source in
        // GoldenScheduleImportService.findDisplayRoomFor; full story in TASK-536.
        if ("1".equals(System.getenv("PLANNER_TEST_ROOMREF_FREE"))) {
            int freed = 0;
            for (var l : problem.getLessons()) {
                if (l.room != null && !l.isPinned()) {
                    l.room = null;
                    freed++;
                }
            }
            System.out.printf("  FREED %d non-authoritative room(s) before the solve%n", freed);
        }
        solutionManager.update(problem);
        BendableScore before = (BendableScore) problem.getScore();
        System.out.println("=== TASK-536 room-refinement checkpoint: " + export + " ===");
        System.out.printf("  BEFORE: hard=%d medium=%d soft=%d (H2 %s)%n",
                ScoreCompat.hardOf(before), ScoreCompat.mediumOf(before), ScoreCompat.softOf(before),
                h2Total(problem));

        // the room-only phases exactly as stage B-ROOM runs them (CH + refinement LS).
        // From FILE: the test classpath shadows solverConfig.xml with a shorter
        // fixture variant (no CompactionPhase), which would break the split.
        SolverConfig roomConfig = com.school.planner.service.ScheduleService.roomPhases(
                SolverConfig.createFromXmlFile(
                        java.nio.file.Path.of("src/main/resources/solverConfig.xml").toFile()));
        roomConfig.getPhaseConfigList().stream()
                .filter(pc -> pc instanceof ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig)
                .map(pc -> (ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig) pc)
                .forEach(pc -> pc.setTerminationConfig(
                        new ai.timefold.solver.core.config.solver.termination.TerminationConfig()
                                .withUnimprovedSecondsSpentLimit(Long.valueOf(120))));
        SolverFactory<SchoolSchedule> factory = SolverFactory.create(roomConfig);
        Solver<SchoolSchedule> solver = factory.buildSolver();
        SchoolSchedule refined = solver.solve(problem);

        solutionManager.update(refined);
        BendableScore after = (BendableScore) refined.getScore();
        System.out.printf("  AFTER : hard=%d medium=%d soft=%d (H2 %s)%n",
                ScoreCompat.hardOf(after), ScoreCompat.mediumOf(after), ScoreCompat.softOf(after),
                h2Total(refined));
        assertTrue(ScoreCompat.hardOf(after) >= ScoreCompat.hardOf(before),
                "room phases are best-solution-monotonic: hard " + ScoreCompat.hardOf(before)
                        + " -> " + ScoreCompat.hardOf(after));
    }

    /** H2's hard total (weight-1000 per conflict: |total|/1000 is the conflict count). */
    private String h2Total(SchoolSchedule schedule) {
        try {
            var factory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager).getScoreDirectorFactory();
            try (var sd = factory.createScoreDirectorBuilder()
                    .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                    .withLookUpEnabled(false)
                    .withExpectShadowVariablesInCorrectState(false)
                    .build()) {
                sd.setWorkingSolution(schedule);
                sd.calculateScore();
                for (var t : sd.getConstraintMatchTotalMap().values()) {
                    if (t.getConstraintRef().id().startsWith("H2")) {
                        return t.getScore().toString() + " over " + t.getConstraintMatchCount() + " matches";
                    }
                }
                return "0 (no matches)";
            }
        } catch (Exception e) {
            return "n/a (" + e.getMessage() + ")";
        }
    }
}
