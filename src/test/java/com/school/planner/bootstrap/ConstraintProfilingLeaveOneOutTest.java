package com.school.planner.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.impl.solver.DefaultSolver;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.CompositeMoveFilter;
import com.school.planner.solver.SchoolScheduleConstraintProvider;

/**
 * E1 profiling (leave-one-out, 2026-07-31). Measures each constraint's score-calculation cost by
 * running K short LS solves at parity, each with ONE canonical constraint excluded via
 * {@code planner.constraints.diag.exclude}, and ranking the delta in score-calculation speed.
 *
 * <p><b>Metric.</b> {@link DefaultSolver#getScoreCalculationSpeed()} (score calculations/sec). A
 * constraint is "expensive" when EXCLUDING it raises the speed the most: each surviving
 * calculation got cheaper. {@code armSpeed - baselineSpeed} is the cost contribution of that
 * constraint. It is a RATE, so it is robust to trajectory noise; the fixed budget and the warmup
 * solve (see below) keep arms comparable.
 *
 * <p><b>Warmup is mandatory.</b> The baseline must run on a JIT-hot JVM. The first solve compiles
 * the constraint-stream hot paths; if the baseline were that first solve, every later arm would
 * look "faster" purely from warmup (measured: a no-op exclusion arm ran +1518 calcs/s vs a cold
 * baseline, i.e. ~12% pure warmup signal). So a discarded full-set warmup solve runs first, then
 * the baseline, then the arms. The {@code S9} arm (S9 is DISABLED, task-273: its call is
 * commented out so it is not built at all; excluding it is a true no-op) is the in-run sanity
 * check: it must land at ~0 speedup, confirming the warmup removed the cold-start confound.
 * (Contrast M-TC: it IS built but flag-inert, so excluding it drops a real but empty stream and
 * is NOT a no-op.)
 *
 * <p><b>Caveat (read before trusting a number).</b> Excluding a constraint changes the score of
 * every move, so the LS trajectory diverges between arms; calc-speed is therefore a PROXY for
 * marginal per-calculation cost, not a perfectly isolated measurement. The RANKING is the
 * deliverable, not any single delta. Also: the default snapshot carries no rooms, so room-tier
 * constraints (H2, H29, H-M2, M3, M-LF) are NOT representative here (H29 fires on every null-room
 * lesson; H2/M3 look inert). That is the right view for the campaign's bottleneck, which is the
 * B-TIMESLOT (timeslot) phase: room assignment is a later phase, so the non-room constraints are
 * the actionable cost picture.
 *
 * <p><b>Default OFF</b> ({@code @EnabledIfEnvironmentVariable}): K short solves (default 20s each x
 * ~60 solves) is far over the suite budget. Opt-in:
 * <pre>
 *   PLANNER_TEST_PROFILE=1                       # all IDs, default budget
 *   PLANNER_TEST_PROFILE=1 PLANNER_TEST_PROFILE_BUDGET=20
 *   PLANNER_TEST_PROFILE=1 PLANNER_TEST_PROFILE_IDS="H17,M32,M34"   # subset (smoke)
 *   PLANNER_TEST_PROFILE=/path/to/snapshotdir    # snapshot path (else default final snapshot)
 * </pre>
 * The exclude property is set IN-PROCESS per arm (not a JVM flag) because
 * {@code scripts/core/test.sh} does not forward {@code -D}/MAVEN_OPTS to the surefire fork; each
 * arm builds a FRESH {@link SolverFactory} so {@code defineConstraints} re-reads it.
 *
 * <p><b>Transaction scope.</b> The snapshot load touches the DB ({@code persist}/{@code listAll})
 * and runs in a short {@code @BeforeEach} transaction. The solve loop does NOT touch the DB, so
 * {@code @Test} is non-transactional: holding one JTA transaction across a ~20-min solve loop
 * exceeds the 300s default timeout and rolls back at commit (ARJUNA016102).
 *
 * <p><b>NEVER adoption.</b> A measurement tool only. It changes no model behavior: the filter is
 * inert in the default solve (verified by {@link ConstraintDiagExcludeFilterTest}), and this test
 * is env-gated out of the suite. Its output (the ranking) tells solver benchmarking WHERE to look.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PROFILE", matches = ".+")
class ConstraintProfilingLeaveOneOutTest {

    /** Default per-arm LS budget (s). Production phase 2 caps at 3600s; this is a cost probe. */
    private static final long DEFAULT_BUDGET_SECONDS = 20;

    /**
     * The leave-one-out population: every canonical constraint ID that appears in the FULL
     * provider's {@code .asConstraint("<ID> - ...")} strings. A superset of what is built at runtime:
     * M-TC IS built but flag-inert (excluding it drops a real but empty stream); S9 is DISABLED
     * (call commented out, not built at all, excluding it is a true no-op -> the warmup sanity
     * floor). NOTE: a renamed/added constraint silently drops out of this list; the per-arm WARN
     * catches extra (unmatched) IDs but not missing ones.
     */
    private static final List<String> ALL_CANONICAL_IDS = List.of(
            "H0", "H1", "H2", "H3", "H4", "H4.3", "H5", "H6", "H7", "H11", "H14",
            "H31", "H32",  // start-of-day hard + quota-fill hard (promoted 19/08): profiling coverage
            "H17", "H20", "H23", "H24", "H25", "H26", "H28", "H29", "H30", "H-M2", "M3", "M5", "M12",
            "M15", "M18", "M20", "M20.2", "M21", "M22", "M23", "M24", "M25", "M27",
            "M28", "M29", "M30.1", "M30.2", "M31", "M32", "M33", "M34", "M35", "M37",
            "M-LA", "M-LF", "M-TC", "S1", "S3", "S7", "S8", "S9", "S10", "S11", "S12",
            "S13", "S16", "S17");

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject EntityManager em;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    /** Loaded once in @BeforeEach (DB transaction); reused (detached, in-memory) by every solve. */
    private SchoolSchedule problem;
    /** Resolved once in @BeforeEach; reused in the banner (avoids re-reading the env + re-resolving). */
    private Path snapshotDir;

    @BeforeEach
    @Transactional
    void loadProblem() throws Exception {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
        System.clearProperty(SchoolScheduleConstraintProvider.DIAG_EXCLUDE_PROPERTY);
        // Guarantee FULL constraint mode: profiling needs the full set, and a partial set
        // (compaction/debug) would make every arm identical -> silent garbage ranking.
        // resetStatics() does not touch the provider's diag mode flags, so force them off.
        SchoolScheduleConstraintProvider.setDebugConstraintsDisabled(false);
        SchoolScheduleConstraintProvider.setCompactionOnlyMode(false);
        snapshotDir = SnapshotFixture.resolveSnapshotDir(System.getenv("PLANNER_TEST_PROFILE"));
        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(snapshotDir, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap); // generates DailyActivityQuotas + expected hours
        problem = snap.problem;
    }

    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
        System.clearProperty(SchoolScheduleConstraintProvider.DIAG_EXCLUDE_PROPERTY);
    }

    @Test
    void profileConstraintCosts() throws Exception {
        long budget = parseLongEnv("PLANNER_TEST_PROFILE_BUDGET", DEFAULT_BUDGET_SECONDS);
        List<String> ids = resolveIds();
        solutionManager.update(problem); // populate the start score (unsolved = null)

        System.out.println("=== E1 leave-one-out profiling: " + ids.size() + " constraints, budget "
                + budget + "s/arm, mode NO_ASSERT seed 0, snapshot "
                + snapshotDir + " ===");

        // Warmup: one discarded full-set solve so the baseline is measured on a JIT-hot JVM.
        runArm("WARMUP", budget, null);
        Arm baseline = runArm("BASELINE", budget, null);
        System.out.printf("  BASELINE: speed=%d calcs/s, count=%d, score=%s%n",
                baseline.speed, baseline.count, baseline.score);

        List<Arm> results = new ArrayList<>();
        for (String id : ids) {
            Arm arm = runArm(id, budget, id);
            results.add(arm);
            System.out.printf("  %-8s speed=%d calcs/s (excluded speedup %+d), count=%d%n",
                    id, arm.speed, arm.speed - baseline.speed, arm.count);
        }

        // cost(C) = how much FASTER the solver runs with C excluded = armSpeed - baselineSpeed.
        // A costlier constraint slows every score calculation more, so excluding it yields a bigger
        // speedup. Costliest first = biggest speedup first.
        List<Arm> ranked = new ArrayList<>(results);
        ranked.sort(Comparator.comparingLong((Arm a) -> a.speed - baseline.speed).reversed());
        System.out.println("=== RANKING (costliest first: armSpeed - baselineSpeed = speedup when excluded) ===");
        List<String> lines = new ArrayList<>();
        lines.add("# E1 leave-one-out constraint-cost ranking (budget " + budget
                + "s/arm, mode NO_ASSERT seed 0; warmup-discarded before baseline)");
        lines.add("# metric: armSpeed - baselineSpeed (calcs/sec). LARGER = the constraint slows scoring more (costlier).");
        lines.add("# SANITY: S9 (disabled, not built) is the true no-op floor (~0); M-TC is built but flag-inert.");
        lines.add("# CAVEAT: room-tier (H2/H29/H-M2/M3/M-LF) not representative (no rooms in snapshot).");
        lines.add("# baseline speed=" + baseline.speed + " calcs/s, count=" + baseline.count);
        lines.add("rank\tconstraint\tspeed_calcs_per_s\tcount\tspeedup_when_excluded");
        int rank = 0;
        for (Arm a : ranked) {
            rank++;
            long speedup = a.speed - baseline.speed;
            lines.add(String.format(Locale.ROOT, "%d\t%s\t%d\t%d\t%+d", rank, a.id, a.speed, a.count, speedup));
            System.out.printf("  %2d. %-8s speedup=%+d (speed %d)%n", rank, a.id, speedup, a.speed);
        }
        Path out = Path.of("target", "e1-leave-one-out-ranking.txt");
        Files.write(out, lines);
        System.out.println("  ranking dumped to " + out.toAbsolutePath());

        org.junit.jupiter.api.Assertions.assertTrue(baseline.speed > 0,
                "baseline must produce score calculations (speed > 0)");
    }

    /**
     * One short LS solve with the named constraint excluded ({@code excludeId == null} = full set,
     * used for warmup + baseline), reading calc-speed. Each arm builds a FRESH factory so
     * {@code defineConstraints} re-reads the live exclude property.
     */
    private Arm runArm(String label, long budgetSeconds, String excludeId) {
        if (excludeId == null) {
            System.clearProperty(SchoolScheduleConstraintProvider.DIAG_EXCLUDE_PROPERTY);
        } else {
            System.setProperty(SchoolScheduleConstraintProvider.DIAG_EXCLUDE_PROPERTY, excludeId);
        }
        Solver<SchoolSchedule> solver = SolverFactory
                .<SchoolSchedule>create(profilingLsConfig(budgetSeconds))
                .buildSolver();
        SchoolSchedule solved = solver.solve(problem);
        DefaultSolver<SchoolSchedule> ds = (DefaultSolver<SchoolSchedule>) solver;
        return new Arm(label, ds.getScoreCalculationSpeed(), ds.getScoreCalculationCount(),
                solved.getScore() == null ? "null" : solved.getScore().toString());
    }

    /**
     * A compact LS representative of phase 2 (union changeMove w5 + swapMove w3 on the timeslot
     * var, {@link CompositeMoveFilter}, entityTabuSize 7, acceptedCountLimit 1000, one budget cap).
     * Mirrors {@code Phase2SmokeSnapshotTest}'s phase-2 builder minus the pillar moves; calc-speed
     * is dominated by per-calculation constraint-stream cost (trajectory-agnostic), so the simpler
     * selector does not distort the ranking.
     */
    private SolverConfig profilingLsConfig(long budgetSeconds) {
        ChangeMoveSelectorConfig change = new ChangeMoveSelectorConfig()
                .withFixedProbabilityWeight(5.0)
                .withEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withValueSelectorConfig(new ValueSelectorConfig().withVariableName("timeslot"));
        SwapMoveSelectorConfig swap = new SwapMoveSelectorConfig()
                .withFixedProbabilityWeight(3.0)
                .withEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withSecondaryEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withVariableNameIncludes("timeslot");
        UnionMoveSelectorConfig moves = new UnionMoveSelectorConfig()
                .withMoveSelectorList(List.of(change, swap))
                .withFilterClass(CompositeMoveFilter.class);
        LocalSearchPhaseConfig ls = new LocalSearchPhaseConfig()
                .withMoveSelectorConfig(moves)
                .withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(budgetSeconds))
                .withAcceptorConfig(new LocalSearchAcceptorConfig().withEntityTabuSize(7))
                .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1000));
        return new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(ls))
                .withEnvironmentMode(ai.timefold.solver.core.config.solver.EnvironmentMode.NO_ASSERT)
                .withRandomSeed(0L);
    }

    private static List<String> resolveIds() {
        String subset = System.getenv("PLANNER_TEST_PROFILE_IDS");
        if (subset != null && !subset.isBlank()) {
            return Arrays.stream(subset.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return ALL_CANONICAL_IDS;
    }

    private static long parseLongEnv(String name, long defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : Long.parseLong(v.trim());
    }

    private record Arm(String id, long speed, long count, String score) {
    }
}
