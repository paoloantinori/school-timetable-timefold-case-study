package com.school.planner.bootstrap;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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
 * TASK-415 phase-2 smoke — runs ONLY the Compactness local search (production phase 2)
 * from a committed snapshot, with a short budget (default 90s, configurable). Its sole
 * purpose: catch "a new mechanism makes the LS diverge/explode" BEFORE burning a laptop run,
 * by watching the hard/medium TREND over the budget. It is explicitly a TREND signal,
 * NEVER adoption evidence.
 *
 * <p><b>Default snapshot is a FINAL state, not construction-end.</b> {@link
 * SnapshotFixture#DEFAULT_SNAPSHOT} (val410-run2) is a solved/final run, so with the default
 * snapshot this smoke measures how well phase 2 HOLDS the recurrence/score from an already-good
 * state (a re-solve stability probe). To instead measure phase 2 running FROM construction:
 * produce {@code target/construction-lessons.txt} ({@code WeeklyTemplateConstructionRealSeedTest}),
 * copy it into a fresh dir as {@code lessons.txt} (no {@code boxed.txt} needed — it is optional,
 * absent = zero boxed), and point the harness at that dir:
 * {@code PLANNER_TEST_PH2SMOKE=/tmp/constr ./scripts/core/test.sh -c Phase2SmokeSnapshotTest}.
 *
 * <p><b>NEVER adoption (TASK-401 checkpoint protocol):</b> the LS is stochastic, and a
 * snapshot fixes ONE trajectory. A single 90s smoke that improves the score says nothing
 * about whether a model change is correct, only that the LS did not visibly break on the
 * committed state. Real adoption passes through the laptop A/B x2 gate.
 *
 * <p>The phase-2 LS is built PROGRAMMATICALLY, mirroring the phase-2 (first) {@code <localSearch>}
 * of {@code solverConfig.xml} field-for-field: union(changeMove w5, swapMove w3, pillarChange w2,
 * pillarSwap w1) with {@link CompositeMoveFilter}, entityTabuSize 7, acceptedCountLimit 1000 —
 * each selector carries a descriptive XML anchor comment so a config drift is visible at review.
 * The termination is the only deliberate divergence: a single short {@code secondsSpentLimit}
 * (production uses diminishedReturns + 3600s + 600s unimproved; a smoke wants one wall-clock cap).
 *
 * <p><b>Default OFF from the suite</b> ({@code @EnabledIfEnvironmentVariable}): a 90s solve is far
 * over the localized-test budget, so it is opt-in via the env var {@code PLANNER_TEST_PH2SMOKE}
 * ({@code 1} = default snapshot, or a snapshot path; optional {@code PLANNER_TEST_PH2SMOKE_BUDGET}
 * overrides the seconds; {@code PLANNER_TEST_PH2SMOKE_ACCEPTOR}={@code tabu}|{@code la} with
 * {@code PLANNER_TEST_PH2SMOKE_ACCEPTOR_PARAM}={@code <size>} screens each B-TIMESLOT-1 arm offline,
 * defaults {@code tabu}/{@code 7} reproduce the production phase-2 acceptor). An env var (not {@code -D}) because {@code scripts/core/test.sh} rejects
 * explicit {@code -D} args and does not forward MAVEN_OPTS to the surefire fork. Per
 * localized-testing-guidelines §4, LS-config changes are benchmark territory — this smoke is the
 * cheap pre-filter, not the verdict.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PH2SMOKE", matches = ".+")
class Phase2SmokeSnapshotTest {

    /** Smoke budget (s). Production phase 2 caps at 3600s; this is a trend pre-filter. */
    private static final long DEFAULT_BUDGET_SECONDS = 90;

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject EntityManager em;
    @Inject ai.timefold.solver.core.api.solver.SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    @Transactional
    void phase2LocalSearchTrendOnConstructionSnapshot() throws Exception {
        Path dir = SnapshotFixture.resolveSnapshotDir(System.getenv("PLANNER_TEST_PH2SMOKE"));
        String budgetEnv = System.getenv("PLANNER_TEST_PH2SMOKE_BUDGET");
        long budget = (budgetEnv == null || budgetEnv.isBlank())
                ? DEFAULT_BUDGET_SECONDS : Long.parseLong(budgetEnv.trim());

        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(dir, availabilityExpansionService, true);
        // Seed quota/expected-hours so M20 scores against the real per-date target (mirrors
        // ScoreASnapshotTest); M32 scores against the real reference template on a final snapshot
        // too (assignment-agnostic buildReference — TASK-416).
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);

        SchoolSchedule problem = snap.problem;
        solutionManager.update(problem); // populate the start score before solving (unsolved = null)
        BendableScore startScore = (BendableScore) problem.getScore();
        double startRecurrence = SnapshotFixture.weeklyRecurrence(snap.lessons).percent();

        LocalSearchAcceptorConfig acceptor = phase2AcceptorFromEnv();
        // (D, draft-005 portfolio) MUTATION CHARACTERIZATION: does solver.solve(problem) mutate the
        // passed instance in place, or clone it? Verdict (2026-07-31, run in PHASE_ASSERT and
        // NON_REPRODUCIBLE): it does NOT mutate. solved != problem (a distinct cloned instance is
        // returned) and the input-marker timeslot is unchanged, so a portfolio may run N solves
        // without a caller-side clone. Detail: portfolio-draft-005.md.
        Lesson marker = problem.getLessons().stream().filter(l -> l.timeslot != null).findFirst().orElse(null);
        long markerTimeslotBefore = marker != null ? marker.timeslot.id : -1L;
        Solver<SchoolSchedule> solver = SolverFactory.<SchoolSchedule>create(phase2LocalSearch(budget, acceptor)).buildSolver();
        SchoolSchedule solved = solver.solve(problem);

        BendableScore endScore = (BendableScore) solved.getScore();
        double endRecurrence = SnapshotFixture.weeklyRecurrence(solved.getLessons()).percent();
        // The marker reference is INTO problem.getLessons(): if solve() mutated the input in place,
        // marker.timeslot now reads the post-solve value (differs from markerTimeslotBefore).
        long markerTimeslotAfter = (marker != null && marker.timeslot != null) ? marker.timeslot.id : -1L;
        boolean solveReturnedSameInstance = solved == problem;

        // End grid in the canonical pipe format, so the KPI scripts in scripts/analyze/
        // (daily-fill, deep-day histogram, resemblance, cadence) can read a smoke's OUTCOME
        // offline - same escape hatch WeeklyTemplateConstructionRealSeedTest offers via
        // target/construction-lessons.txt. Without it a smoke's only visible outcome is the
        // aggregate score, which cannot answer "did the deep (-3h+) days get shallower".
        Path endDump = Path.of("target", "ph2smoke-lessons.txt");
        SnapshotFixture.writePlacedGrid(solved.getLessons(), endDump);
        System.out.println("  end grid dumped to " + endDump.toAbsolutePath());

        System.out.println("=== phase-2 smoke: " + dir + " (budget " + budget + "s, acceptor " + phase2AcceptorLabel(acceptor) + ") ===");
        System.out.printf("  start: hard=%d medium=%d soft=%d  recurrence=%.1f%%%n",
                ScoreCompat.hardOf(startScore), ScoreCompat.mediumOf(startScore), ScoreCompat.softOf(startScore), startRecurrence);
        System.out.printf("  end:   hard=%d medium=%d soft=%d  recurrence=%.1f%%%n",
                ScoreCompat.hardOf(endScore), ScoreCompat.mediumOf(endScore), ScoreCompat.softOf(endScore), endRecurrence);
        System.out.printf("  delta: hard %+d  medium %+d%n",
                ScoreCompat.hardOf(endScore) - ScoreCompat.hardOf(startScore),
                ScoreCompat.mediumOf(endScore) - ScoreCompat.mediumOf(startScore));
        // MUTATION report: solved==problem is the authoritative aliasing signal; the input-marker
        // is a corroborating in-place-mutation signal. Both expected false per the verdict above.
        boolean inputMarkerMoved = markerTimeslotBefore != markerTimeslotAfter;
        System.out.printf("  mutation: solved==input=%b, input-marker moved=%b (ts %d -> %d)%n",
                solveReturnedSameInstance, inputMarkerMoved, markerTimeslotBefore, markerTimeslotAfter);
        // Guard the portfolio's no-mutation premise (ScheduleService.runTimeslotSolveStage relies on
        // it to run N solves without a caller-side clone): a future Timefold contract change must
        // turn this smoke red, not just print a flipped boolean. See portfolio-draft-005.md.
        org.junit.jupiter.api.Assertions.assertFalse(solveReturnedSameInstance,
                "solve() must return a distinct cloned instance, not the input (portfolio parallel safety)");
        org.junit.jupiter.api.Assertions.assertFalse(inputMarkerMoved,
                "solve() must not mutate the input's lessons in place (portfolio parallel safety)");
        System.out.println("  (TREND ONLY — a snapshot fixes one trajectory; NEVER adoption. "
                + "Adoption passes through the laptop A/B x2 gate.)");

        // The smoke's only structural guarantee: the LS runs to the budget without diverging
        // (no exception, finite score). A hard EXPLOSION (end much worse than start) is the
        // human review signal this is built for, not a pass/fail threshold — the exact delta
        // depends on the stochastic trajectory and the snapshot, so it is printed, not asserted.
        org.junit.jupiter.api.Assertions.assertNotNull(endScore, "the LS must produce a score");
    }

    /**
     * Reads the phase-2 acceptor from env so the smoke can screen each B-TIMESLOT-1
     * metaheuristic arm offline (notte2 2026-07-31). Defaults ({@code tabu}, size 7) reproduce
     * today's phase-2 config, so the suite (env unset) is unchanged. Every benchmark arm holds
     * {@code acceptedCountLimit} at 1000, so the forager is fixed here and only the acceptor knob
     * varies. Mirrors the arm knobs in {@code scripts/benchmark/generate-benchmark-arm.py}
     * (A=tabu/7, B=la/400, E=tabu/11, G=la/1000, H=tabu/3).
     */
    private static LocalSearchAcceptorConfig phase2AcceptorFromEnv() {
        String type = System.getenv().getOrDefault("PLANNER_TEST_PH2SMOKE_ACCEPTOR", "tabu").trim().toLowerCase(Locale.ROOT);
        int size = Integer.parseInt(System.getenv().getOrDefault("PLANNER_TEST_PH2SMOKE_ACCEPTOR_PARAM", "7").trim());
        LocalSearchAcceptorConfig acceptor = new LocalSearchAcceptorConfig();
        if (type.startsWith("la")) {
            acceptor.withLateAcceptanceSize(size);
        } else {
            acceptor.withEntityTabuSize(size);
        }
        return acceptor;
    }

    /** A short label for the acceptor under test, for the smoke's banner line. */
    private static String phase2AcceptorLabel(LocalSearchAcceptorConfig acceptor) {
        if (acceptor.getLateAcceptanceSize() != null) {
            return "lateAcceptance " + acceptor.getLateAcceptanceSize();
        }
        return "entityTabu " + acceptor.getEntityTabuSize();
    }

    /** Phase-2 Compactness LS = the phase-2 (first) {@code <localSearch>} of solverConfig.xml,
     *  replicated field-for-field with a single budget cap (descriptive XML anchors, not line
     *  numbers, so the mirror survives XML re-formatting). */
    private SolverConfig phase2LocalSearch(long budgetSeconds, LocalSearchAcceptorConfig acceptor) {
        EntitySelectorConfig lessonEntities = new EntitySelectorConfig(Lesson.class);
        ValueSelectorConfig timeslotVar = new ValueSelectorConfig().withVariableName("timeslot");
        PillarSelectorConfig lessonPillars = new PillarSelectorConfig().withEntitySelectorConfig(lessonEntities);

        // phase-2 changeMoveSelector, w5 (timeslot var).
        ChangeMoveSelectorConfig change = new ChangeMoveSelectorConfig()
                .withFixedProbabilityWeight(5.0)
                .withEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withValueSelectorConfig(timeslotVar);
        // phase-2 swapMoveSelector, w3 (timeslot var).
        SwapMoveSelectorConfig swap = new SwapMoveSelectorConfig()
                .withFixedProbabilityWeight(3.0)
                .withEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withSecondaryEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withVariableNameIncludes("timeslot");
        // phase-2 pillarChangeMoveSelector, w2.
        PillarChangeMoveSelectorConfig pillarChange = new PillarChangeMoveSelectorConfig()
                .withFixedProbabilityWeight(2.0);
        pillarChange.setPillarSelectorConfig(lessonPillars); // inherited setter
        pillarChange.setValueSelectorConfig(new ValueSelectorConfig().withVariableName("timeslot"));
        // phase-2 pillarSwapMoveSelector, w1.
        PillarSwapMoveSelectorConfig pillarSwap = new PillarSwapMoveSelectorConfig()
                .withFixedProbabilityWeight(1.0);
        pillarSwap.setPillarSelectorConfig(lessonPillars);
        pillarSwap.withVariableNameIncludes("timeslot");

        // phase-2 unionMoveSelector + CompositeMoveFilter.
        UnionMoveSelectorConfig moves = new UnionMoveSelectorConfig()
                .withMoveSelectorList(List.of(change, swap, pillarChange, pillarSwap))
                .withFilterClass(CompositeMoveFilter.class);

        LocalSearchPhaseConfig phase2 = new LocalSearchPhaseConfig()
                .withMoveSelectorConfig(moves)
                .withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(budgetSeconds)) // smoke cap
                .withAcceptorConfig(acceptor) // phase-2 acceptor: tabu/LA, env-selected (PLANNER_TEST_PH2SMOKE_ACCEPTOR/PARAM)
                .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1000)); // phase-2 acceptedCountLimit

        return SnapshotFixture.applyEnvironmentMode(new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(phase2)));
    }
}
