package com.school.planner.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.entity.pillar.PillarSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.PillarChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.PillarSwapMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.CompositeMoveFilter;
import com.school.planner.solver.SchoolScheduleConstraintProvider;

/**
 * TASK-415 benchmark-screening DEMO (E4). Runs the phase-2 Compactness LS from the SAME
 * construction-end start (entry-of-phase-2 dump of val408-run1) under TWO config variants and
 * prints the "erosion per medium" metric the metaheuristic benchmark cluster (TASK-280) will
 * use: how much medium each variant gains against how much recurrence it burns. This is the
 * offline screening pre-filter, NOT a benchmark verdict (one snapshot, one seed, one 90s run
 * per arm); it demonstrates the metric and the harness shape, and catches "a config variant
 * makes the LS diverge" before a laptop arm is spent.
 *
 * <p>Variant A = the current production phase-2 config (entityTabuSize 7). Variant B =
 * entityTabuSize 11 (a single-knob screening example). Both solve from the same input problem
 * (Timefold solves a planning clone, so the input is not mutated between arms).
 *
 * <p>TREND/diagnosis only, never adoption (a benchmark verdict needs the laptop A/B x2 gate and
 * multiple seeds). Gated OFF via {@code PLANNER_TEST_BENCH_SCREEN} (env var, not {@code -D});
 * optional {@code PLANNER_TEST_BENCH_SCREEN_BUDGET} overrides the per-arm seconds (default 90).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_BENCH_SCREEN", matches = ".+")
class Task415BenchmarkScreeningDemoTest {

    private static final long DEFAULT_BUDGET_SECONDS = 90;
    private static final Path PH2_ENTRY_DUMP = Path.of("claudedocs", "spalmone-2026-27",
            "task408-results", "val408-run1", "phase-transitions", "phase-2-2-ls-compactness.txt");

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject EntityManager em;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    @Transactional
    void screenTwoPhase2Variants() throws Exception {
        String b = System.getenv("PLANNER_TEST_BENCH_SCREEN_BUDGET");
        long budget = (b == null || b.isBlank()) ? DEFAULT_BUDGET_SECONDS : Long.parseLong(b.trim());

        Path workDir = Path.of("target", "task415-bench-screen");
        Files.createDirectories(workDir);
        Files.copy(PH2_ENTRY_DUMP, workDir.resolve("lessons.txt"), StandardCopyOption.REPLACE_EXISTING);

        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(workDir, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        SchoolSchedule problem = snap.problem;

        solutionManager.update(problem);
        BendableScore startScore = (BendableScore) problem.getScore();
        double startRecurrence = SnapshotFixture.weeklyRecurrence(snap.lessons).percent();

        System.out.println("=== TASK-415 benchmark-screening demo: phase-2 variants from construction end ===");
        System.out.printf("  start: hard=%d medium=%d  recurrence=%.1f%%  (budget %ds/arm)%n",
                ScoreCompat.hardOf(startScore), ScoreCompat.mediumOf(startScore), startRecurrence, budget);
        System.out.printf("  %-24s %8s %12s %14s %12s %14s%n",
                "variant", "hard", "medium", "medium-gained", "recur%", "recur-lost(pt)");

        runArm("A tabu7 (current)", 7, budget, problem, startScore, startRecurrence);
        runArm("B tabu11", 11, budget, problem, startScore, startRecurrence);

        System.out.println("  (screening DEMO: one snapshot/seed/arm = TREND only; the cluster uses this "
                + "medium-gained-vs-recurrence-lost metric across seeds. NEVER adoption.)");
    }

    private void runArm(String label, int tabu, long budget, SchoolSchedule problem,
                        BendableScore startScore, double startRecurrence) {
        Solver<SchoolSchedule> solver =
                SolverFactory.<SchoolSchedule>create(phase2LocalSearch(budget, tabu)).buildSolver();
        SchoolSchedule solved = solver.solve(problem);
        BendableScore end = (BendableScore) solved.getScore();
        double endRecurrence = SnapshotFixture.weeklyRecurrence(solved.getLessons()).percent();
        System.out.printf("  %-24s %8d %12d %14d %12.1f %14.1f%n",
                label, ScoreCompat.hardOf(end), ScoreCompat.mediumOf(end),
                ScoreCompat.mediumOf(end) - ScoreCompat.mediumOf(startScore),
                endRecurrence, startRecurrence - endRecurrence);
    }

    /** Phase-2 Compactness LS with a configurable entityTabuSize (screening knob); otherwise a
     *  field-for-field mirror of solverConfig.xml phase 2 (see {@link Phase2SmokeSnapshotTest}). */
    private SolverConfig phase2LocalSearch(long budgetSeconds, int entityTabuSize) {
        EntitySelectorConfig lessonEntities = new EntitySelectorConfig(Lesson.class);
        ValueSelectorConfig timeslotVar = new ValueSelectorConfig().withVariableName("timeslot");
        PillarSelectorConfig lessonPillars = new PillarSelectorConfig().withEntitySelectorConfig(lessonEntities);

        ChangeMoveSelectorConfig change = new ChangeMoveSelectorConfig()
                .withFixedProbabilityWeight(5.0)
                .withEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withValueSelectorConfig(timeslotVar);
        SwapMoveSelectorConfig swap = new SwapMoveSelectorConfig()
                .withFixedProbabilityWeight(3.0)
                .withEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withSecondaryEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withVariableNameIncludes("timeslot");
        PillarChangeMoveSelectorConfig pillarChange = new PillarChangeMoveSelectorConfig()
                .withFixedProbabilityWeight(2.0);
        pillarChange.setPillarSelectorConfig(lessonPillars);
        pillarChange.setValueSelectorConfig(new ValueSelectorConfig().withVariableName("timeslot"));
        PillarSwapMoveSelectorConfig pillarSwap = new PillarSwapMoveSelectorConfig()
                .withFixedProbabilityWeight(1.0);
        pillarSwap.setPillarSelectorConfig(lessonPillars);
        pillarSwap.withVariableNameIncludes("timeslot");

        UnionMoveSelectorConfig moves = new UnionMoveSelectorConfig()
                .withMoveSelectorList(List.of(change, swap, pillarChange, pillarSwap))
                .withFilterClass(CompositeMoveFilter.class);

        LocalSearchPhaseConfig phase2 = new LocalSearchPhaseConfig()
                .withMoveSelectorConfig(moves)
                .withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(budgetSeconds))
                .withAcceptorConfig(new LocalSearchAcceptorConfig().withEntityTabuSize(entityTabuSize))
                .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1000));

        return SnapshotFixture.applyEnvironmentMode(new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(phase2)));
    }
}
