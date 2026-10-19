package com.school.planner.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.service.ScheduleService;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.checkpoint.StagedCheckpointCursor;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;
import ai.timefold.solver.core.api.score.stream.ConstraintRef;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LS VARIANCE BENCH (19/09, mandato maintainer "Vai. Sperimenta"): is the
 * multi-threaded LS race the neck of 0-hard? Replay the DOWNSTREAM of the
 * fixed deterministic construction N times in-process and measure p(final
 * hard == 0). Same construction (checkpoint), same repair chain, same room
 * refinement: ONLY the MT-LS arrival-order race varies (natural thread
 * timing, uncontrolled by design - it is the measurand; that is also why the
 * runs are SEQUENTIAL: parallel JVMs would contend the CPU and pollute the
 * race).
 *
 * <p>Checkpoint: staged-checkpoints/attempt1/POST_CONSTRUCTION.json (19/09
 * 05:28, INTEGRATO-5's deterministic attempt-1 construction, main-era). The
 * resume cycle from POST_CONSTRUCTION runs PROMOTION -> B-TIMESLOT loop ->
 * RELEASE -> repair chain -> B-ROOM (its own room LS plays corner-b's role
 * here: the single run is "the last attempt", so skip-doomed-room never
 * disarms the refinement). Live terminations, NO knobs: iter-1 is wall-capped
 * at 3600s in production. Expect ~113 min/run.
 *
 * <p>CLASSPATH TRAP (see StagedSolvePinLifecycleTest's "refusing loudly"
 * test): src/test/resources/solverConfig.xml is a 10s smoke config that
 * shadows the production XML on the test classpath, and the staged cycle's
 * phase split refuses it. The profile below points the service at {@code
 * lsvar-live-solver-config.xml}, a byte-identical COMMITTED copy of the main
 * XML (the seed-CSV duplication pattern): the always-on guard in {@link
 * LsVarConfigGuardTest} fails the suite the moment the two diverge, so the
 * copy cannot rot.
 *
 * <p>Prediction and verdict criteria pre-registered in
 * claudedocs/spalmone-2026-27/integrato-1/ls-variance-bench/PREDIZIONE.md.
 * Gated by {@code -Dlsvar.run=true}; {@code lsvar.n} (default 8) runs;
 * {@code lsvar.ck} overrides the checkpoint path. A FAILED cycle aborts the
 * bench loudly (a failure is not a non-zero measurement).
 */
@QuarkusTest
@TestProfile(LsVarianceBenchTest.LsVarProfile.class)
class LsVarianceBenchTest {

    /** Points the staged cycle (and the Timefold extension) at the guarded
     * byte-identical copy of the MAIN solver config, and restores the LIVE
     * environment mode (the default test profile runs STEP_ASSERT, which the
     * production cycle never sees). */
    public static class LsVarProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.timefold.solver.solver-config-xml", "lsvar-live-solver-config.xml",
                    "quarkus.timefold.solver.environment-mode", "NON_REPRODUCIBLE");
        }
    }

    private static final long RUN_WALL_CAP_SECONDS = 3 * 3600;

    @Inject ScheduleService scheduleService;
    @Inject DataGenerator dataGenerator;
    @Inject EntityManager em;
    @Inject UserTransaction userTransaction;
    @Inject com.school.planner.service.LessonGenerator lessonGenerator;
    @Inject com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;

    @BeforeEach
    @AfterEach
    @jakarta.transaction.Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    @Timeout(value = 86400)
    void measuresZeroHardFrequencyAcrossLsRaces() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("lsvar.run"), "ls-variance bench: -Dlsvar.run=true");
        int n = Integer.getInteger("lsvar.n", 8);
        Path ck = Path.of(System.getProperty("lsvar.ck",
                "staged-checkpoints/attempt1/POST_CONSTRUCTION.json"));
        Assumptions.assumeTrue(Files.isRegularFile(ck), "checkpoint missing: " + ck);
        System.out.printf("[LSVAR] === LS VARIANCE BENCH: %d run(s) from %s ===%n", n, ck);
        seedLiveOrderFixture();

        String trioFlag = System.getProperty("lsvar.trio");
        boolean withTrio = "1".equals(trioFlag) || Boolean.parseBoolean(trioFlag);
        int zeros = 0;
        for (int i = 1; i <= n; i++) {
            boolean zero = runOne(i, ck, withTrio);
            if (zero) {
                zeros++;
            }
        }
        System.out.printf("[LSVAR] SUMMARY p(0-hard) = %d/%d = %.1f%%%n", zeros, n, 100.0 * zeros / n);
        assertTrue(n > 0, "at least one run");
    }

    private boolean runOne(int index, Path ck, boolean withTrio) throws Exception {
        System.out.printf("[LSVAR] run %d: applying checkpoint (explicit commit: the cycle"
                + " thread reads a fresh context)%n", index);
        userTransaction.begin();
        StagedCheckpointCursor cursor;
        try {
            cursor = scheduleService.applyStagedCheckpoint(ck);
        } finally {
            userTransaction.commit();
        }
        Instant start = Instant.now();
        Map<String, Instant> transitions = new LinkedHashMap<>();
        scheduleService.startResumeCycle(cursor);
        // the documented rest condition: NOT_SOLVING + stagedStage null; the
        // refiningForDelivery marker covers the wrapper's corner-b window (not
        // used by the plain cycle, checked anyway - the false-quiescence lesson)
        while (true) {
            Thread.sleep(5000);
            String stage = scheduleService.getStagedStage();
            if (stage != null) {
                transitions.putIfAbsent(stage, Instant.now());
            }
            if (!scheduleService.isBusy() && stage == null && !scheduleService.isRefiningForDelivery()) {
                break;
            }
            if (Duration.between(start, Instant.now()).getSeconds() > RUN_WALL_CAP_SECONDS) {
                throw new IllegalStateException("run " + index + " exceeded the "
                        + RUN_WALL_CAP_SECONDS + "s wall cap - aborting honestly, no partial score read");
            }
        }
        long wall = Duration.between(start, Instant.now()).getSeconds();
        transitions.forEach((stage, at) -> System.out.printf("[LSVAR] run %d stage %-16s at +%ds%n",
                index, stage, Duration.between(start, at).getSeconds()));

        SchoolSchedule problem = scheduleService.getFullScheduleForExport();
        String failure = scheduleService.getLastSolverError();
        if (failure != null) {
            throw new IllegalStateException("run " + index + ": the cycle FAILED (lastSolverError="
                    + failure + ") - a failure is not a non-zero measurement, aborting the bench");
        }
        long boxed = problem.getLessons().stream().filter(l -> l.timeslot == null).count();
        Result r = score(problem);
        System.out.printf("[LSVAR] run %d DONE wall=%ds boxed=%d hard=%d medium=%d zero=%b%n",
                index, wall, boxed, r.hard, r.medium, r.hard == 0);
        r.hardFamilies.forEach((id, t) -> System.out.printf("[LSVAR] run %d   %s: h=%d matches=%d%n",
                index, id, t.hard, t.matches));
        if (!withTrio) {
            return r.hard == 0;
        }
        System.out.printf("[LSVAR] run %d: pre-trio hard=%d -> DELIVERY TRIO (banco form)%n", index, r.hard);
        runDeliveryTrio(problem);
        Result after = score(problem);
        System.out.printf("[LSVAR] run %d POST-TERNA hard=%d medium=%d zero=%b%n",
                index, after.hard, after.medium, after.hard == 0);
        after.hardFamilies.forEach((id, t) -> System.out.printf("[LSVAR] run %d   post %s: h=%d matches=%d%n",
                index, id, t.hard, t.matches));
        // TASK-638 near-miss feed: the CELL identities of the residual hard land
        // in the log (the @AfterEach wipe clears the DB, the log is the only
        // surviving artifact) so a blind puzzle is built from real data.
        SnapshotFixture.printResidualCells("run " + index + " post-terna", problem);
        return after.hard == 0;
    }

    /** The wrapper's pre-save order, banco form (DirectWrites mover): CSF -> EDF
     * [relay+h32] -> HRF. h7swap left to its launcher flag (default OFF). */
    private void runDeliveryTrio(SchoolSchedule problem) {
        System.setProperty("hrf.debug", "true");
        try {
            System.setProperty(com.school.planner.solver.CompositeSwapFinisher.FLAG, "true");
            try {
                com.school.planner.solver.CompositeSwapFinisher.finish(
                        problem, (lesson, ts) -> lesson.timeslot = ts);
            } finally {
                System.clearProperty(com.school.planner.solver.CompositeSwapFinisher.FLAG);
            }
            System.setProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG, "true");
            System.setProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG, "true");
            try {
                System.setProperty(com.school.planner.solver.EmptyDayFinisher.H33_FLAG, "false"); // frozen bank
                var edf = com.school.planner.solver.EmptyDayFinisher.finish(
                        problem, (lesson, ts) -> lesson.timeslot = ts);
                System.out.printf("[LSVAR] trio EDF: %s%n", edf);
            } finally {
                System.clearProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG);
                System.clearProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG);
            }
            System.setProperty(com.school.planner.solver.HoleRelayFinisher.FLAG, "true");
            try {
                var hrf = com.school.planner.solver.HoleRelayFinisher.finish(
                        problem, (lesson, ts) -> lesson.timeslot = ts);
                System.out.printf("[LSVAR] trio HRF: targets=%d slides=%d rotations=%d moves=%d%n",
                        hrf.targets(), hrf.slides(), hrf.rotations(), hrf.movesApplied());
            } finally {
                System.clearProperty(com.school.planner.solver.HoleRelayFinisher.FLAG);
            }
        } finally {
            System.clearProperty("hrf.debug");
        }
    }

    private record FamilyTotal(long hard, long matches) {
    }

    private record Result(long hard, long medium, Map<String, FamilyTotal> hardFamilies) {
    }

    /** Full re-score with constraint-match totals (the RoomTierMassRescore pattern). */
    private Result score(SchoolSchedule problem) {
        SolverFactory<SchoolSchedule> factory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class)));
        SolutionManager<SchoolSchedule, BendableScore> manager = SolutionManager.create(factory);
        manager.update(problem);
        var directorFactory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) manager)
                .getScoreDirectorFactory();
        try (var director = directorFactory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            director.setWorkingSolution(problem);
            BendableScore total = director.calculateScore().raw();
            Map<String, FamilyTotal> hardFamilies = new java.util.TreeMap<>();
            for (var entry : director.getConstraintMatchTotalMap().entrySet()) {
                ConstraintMatchTotal<BendableScore> t = entry.getValue();
                long h = com.school.planner.solver.ScoreCompat.hardOf(t.getScore());
                if (h < 0) {
                    hardFamilies.put(entry.getKey().id(),
                            new FamilyTotal(h, t.getConstraintMatchCount()));
                }
            }
            return new Result(com.school.planner.solver.ScoreCompat.hardOf(total),
                    com.school.planner.solver.ScoreCompat.mediumOf(total), hardFamilies);
        }
    }

    /** Mirror of the live boot's seeding order: the checkpoint restore maps by lesson
     * DB id, so the generation order must match the captured cycle's (mismatch fails
     * LOUD in StagedCheckpointRestore). Same shape as Pratica2ReplayTest. */
    @jakarta.transaction.Transactional
    void seedLiveOrderFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers =
                RealSeedFixture.seedTeachersAndAvailabilityRules(availabilityExpansionService, true);
        RealSeedFixture.seedRooms();
        com.school.planner.service.SubjectRegistry registry = new com.school.planner.service.SubjectRegistry();
        registry.load();
        SnapshotFixture.seedCoursesFromIncarichi(teachers, groups, lessonGenerator, registry);
        dataGenerator.generateStageCourses();
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
        // the cycle loads rooms through loadSchedule's own filter; the seeded set
        // must be assignable-tag complete for B-ROOM to behave like the live one
        assertTrue(Room.count() > 0, "rooms seeded");
    }
}
