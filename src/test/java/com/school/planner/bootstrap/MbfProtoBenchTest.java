package com.school.planner.bootstrap;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.service.ScheduleService;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.ScoreCompat;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * TASK M-BF prototype bench (wt/mbf-proto, 17/09): the lab-teacher day
 * contiguity medium measured BEFORE any porting decision. Three opt-in arms:
 *
 * <ul>
 * <li>GOLDEN ({@code PLANNER_TEST_MBF_GOLDEN=1}): the imported golden pays the
 * self-gating calibration (prediction: 1 interleaved day = 1 match, 1200
 * medium at the default 20/min; small tolerance for isLab-vs-family
 * divergence).</li>
 * <li>REPLAY STATIC ({@code PLANNER_TEST_MBF_REPLAY=1} +
 * {@code PLANNER_TEST_REPLAY_609} at the integrato-1 export): score off vs on;
 * hard MUST be identical (medium lever), the medium delta is the M-BF bill
 * (prediction: ~54 matches).</li>
 * <li>REPLAY LS ({@code PLANNER_TEST_MBF_LS=1} + REPLAY_609): a 600s
 * mini-local-search ARMED on the delivered export; dumps lessons before/after
 * for {@code scripts/analyze/lab-block-form-kpi.py}; hard must never worsen
 * (prediction: interleaved 54 goes down, hard stays -4, full-day/ricorrenza
 * read after via the KPI battery on the dump).</li>
 * </ul>
 */
@QuarkusTest
class MbfProtoBenchTest {

    private static final String FLAG = "planner.constraints.mbf.enabled";

    @Inject
    EntityManager em;

    @Inject
    ScheduleService scheduleService;

    @Inject
    AvailabilityExpansionService availabilityExpansionService;

    @Inject
    DataGenerator dataGenerator;

    // (golden arm lives in GoldenSolutionRegressionTest.mbfGoldenCheck: it reuses
    //  that fixture's full seeding + import pipeline)

    // ------------------------------------------------------------ replay static

    @Test
    @Transactional
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PLANNER_TEST_MBF_REPLAY", matches = "1")
    void replayExportStatic() throws Exception {
        SnapshotFixture.Snapshot snap = loadExport();
        System.setProperty(FLAG, "false"); // RATIFIED 18/09: default ON, the explicit literal is the opt-out
        long[] off = scoreOf(snap.problem);
        System.setProperty(FLAG, "true");
        long[] on = scoreOf(snap.problem);
        System.clearProperty(FLAG); // restore the ratified default (ON) for whatever runs next
        System.out.printf("=== M-BF replay static (integrato-1 [-4]): hard %d -> %d,"
                + " medium %d -> %d (delta %d)%n",
                off[0], on[0], off[1], on[1], on[1] - off[1]);
        org.junit.jupiter.api.Assertions.assertEquals(off[0], on[0],
                "a medium lever cannot touch the export's hard tier");
        System.out.printf("    M-BF export: fragmented multi-teacher lab days=%d,"
                + " gap-minutes=%d (bill at 20/min = %d)%n",
                mbfMatchCount(snap.problem), mbfGapMinutes(snap.problem),
                20 * mbfGapMinutes(snap.problem));
    }

    /** per-day gap sum: the provider helper assumes a SINGLE day's lessons; feeding
     *  it a flattened list computes spans ACROSS dates (negative nonsense). */
    private static int mbfGapMinutes(SchoolSchedule problem) {
        java.util.Map<String, java.util.List<Lesson>> byDay = new java.util.HashMap<>();
        for (Lesson l : problem.getLessons()) {
            if (l.timeslot == null || !l.isLab() || l.getTeacher() == null
                    || l.getTeacher().pseudo) {
                continue;
            }
            byDay.computeIfAbsent(l.getStudentGroup().name + "|" + l.timeslot.getDate(),
                    k -> new java.util.ArrayList<>()).add(l);
        }
        int gaps = 0;
        for (java.util.List<Lesson> day : byDay.values()) {
            gaps += SchoolScheduleConstraintProvider.mbfDayGapMinutes(day);
        }
        return gaps;
    }

    /** M-BF match census ON THE LOADED PROBLEM (analyzeScore() scores the service's
     *  DB schedule, not the replay problem: the wrong-object trap this bench fixes). */
    private static int mbfMatchCount(SchoolSchedule problem) {
        java.util.Map<String, java.util.List<Lesson>> byDay = new java.util.HashMap<>();
        for (Lesson l : problem.getLessons()) {
            if (l.timeslot == null || !l.isLab() || l.getTeacher() == null
                    || l.getTeacher().pseudo) {
                continue;
            }
            byDay.computeIfAbsent(l.getStudentGroup().name + "|" + l.timeslot.getDate(),
                    k -> new java.util.ArrayList<>()).add(l);
        }
        int n = 0;
        for (java.util.List<Lesson> day : byDay.values()) {
            if (SchoolScheduleConstraintProvider.mbfDayGapMinutes(day) > 0) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- replay LS

    @Test
    @Transactional
    @io.quarkus.narayana.jta.runtime.TransactionConfiguration(timeout = 7200)
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PLANNER_TEST_MBF_CTRL", matches = "1")
    void replayExportMiniLsControl() throws Exception {
        runMiniLs("lessons-after-ctrl.txt", false);
    }

    @Test
    @Transactional
    @io.quarkus.narayana.jta.runtime.TransactionConfiguration(timeout = 7200)
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PLANNER_TEST_MBF_LS", matches = "1")
    void replayExportMiniLs() throws Exception {
        runMiniLs("lessons-after.txt", true);
    }

    private void runMiniLs(String afterName, boolean armed) throws Exception {
        SnapshotFixture.Snapshot snap = loadExport();
        System.setProperty(FLAG, "false"); // RATIFIED 18/09: default ON, the explicit literal is the opt-out
        long[] off = scoreOf(snap.problem);
        if (armed) {
            System.setProperty(FLAG, "true");
        }
        long[] start = scoreOf(snap.problem);
        System.setProperty(FLAG, "false"); // RATIFIED 18/09: default ON, the explicit literal is the opt-out
        System.out.printf("=== M-BF mini-LS (%s): start hard %d medium %d%n",
                armed ? "ARMED" : "CONTROL (flag OFF)", start[0], start[1]);
        String outDir = System.getenv("PLANNER_TEST_MBF_OUT");
        java.nio.file.Path dir = java.nio.file.Path.of(outDir == null ? "/tmp/mbf-ls" : outDir);
        java.nio.file.Files.createDirectories(dir);
        dumpLessons(snap.problem, dir.resolve("lessons-before.txt"));
        LocalSearchPhaseConfig phase = new LocalSearchPhaseConfig();
        UnionMoveSelectorConfig moves = new UnionMoveSelectorConfig();
        moves.setMoveSelectorList(java.util.Arrays.asList(
                new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig()));
        phase.setMoveSelectorConfig(moves);
        phase.setTerminationConfig(new TerminationConfig()
                .withSecondsSpentLimit(Long.parseLong(
                        System.getenv().getOrDefault("PLANNER_TEST_MBF_LS_SECONDS", "600")))
                .withUnimprovedSecondsSpentLimit(120L));
        SolverConfig config = new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withConstraintProviderClass(SchoolScheduleConstraintProvider.class)
                .withPhases(phase);
        if (armed) {
            System.setProperty(FLAG, "true");
        }
        SolverFactory<SchoolSchedule> factory = SolverFactory.create(config);
        Solver<SchoolSchedule> solver = factory.buildSolver();
        SchoolSchedule result = solver.solve(snap.problem);
        System.setProperty(FLAG, "false"); // RATIFIED 18/09: default ON, the explicit literal is the opt-out
        long[] after = scoreOf(result);
        System.out.printf("    mini-LS (%s) done: hard %d -> %d, medium %d -> %d%n",
                armed ? "ARMED" : "CONTROL", start[0], after[0], start[1], after[1]);
        org.junit.jupiter.api.Assertions.assertTrue(after[0] >= start[0],
                "hard must never worsen under the armed LS (was " + start[0] + ", is " + after[0] + ")");
        dumpLessons(result, dir.resolve(afterName));
        System.clearProperty(FLAG); // restore the ratified default (ON) for whatever runs next
        System.out.println("    dumps in " + dir);
    }

    // ------------------------------------------------------------------ helpers

    private long[] scoreOf(SchoolSchedule problem) {
        var sm = ai.timefold.solver.core.api.solver.SolutionManager
                .create(SolverFactory.create(new SolverConfig()
                        .withSolutionClass(SchoolSchedule.class)
                        .withEntityClasses(Lesson.class)
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class)));
        ai.timefold.solver.core.api.score.BendableScore total =
                (ai.timefold.solver.core.api.score.BendableScore) sm.update(problem);
        return new long[] { ScoreCompat.hardOf(total), ScoreCompat.mediumOf(total),
                ScoreCompat.softOf(total) };
    }

    private SnapshotFixture.Snapshot loadExport() throws Exception {
        String v = System.getenv("PLANNER_TEST_REPLAY_609");
        java.nio.file.Path export = "1".equals(v)
                ? java.nio.file.Path.of("/tmp/m39off/export.json.gz")
                : java.nio.file.Path.of(v);
        SnapshotFixture.Snapshot snap = SnapshotFixture
                .loadFromExport(export, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        SchoolSchedule.setGoldenCoPresenceDepthStatic(
                CompositeRepairReplay609Test.depthFromSnapshotCourses(snap));
        return snap;
    }

    private void dumpLessons(SchoolSchedule problem, java.nio.file.Path path)
            throws Exception {
        // the solver mutates problem.getLessons(), NOT snap.lessons (separate
        // instances): dumping the fixture list measured the untouched origins
        StringBuilder dump = new StringBuilder("SET\n");
        for (Lesson l : problem.getLessons()) {
            if (l.timeslot == null || l.course == null || l.getStudentGroup() == null) {
                continue;
            }
            dump.append(l.getStudentGroup().name).append('|').append(l.timeslot.getDate())
                    .append('|').append(l.timeslot.startDateTime.getHour())
                    .append('|').append(l.course.subject).append('|')
                    .append(l.getTeacher() == null ? "" : l.getTeacher().name)
                    .append('|').append(l.pinned).append('\n');
        }
        java.nio.file.Files.writeString(path, dump.toString());
    }

}
