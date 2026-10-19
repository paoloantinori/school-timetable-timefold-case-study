package com.school.planner.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;

import com.school.planner.domain.SchoolSchedule;
import com.school.planner.service.AvailabilityExpansionService;

/**
 * TASK-415 score trajectory (E2). Scores EVERY phase-boundary dump of val408-run1 plus its
 * final state, in a SINGLE Quarkus boot, and prints a phase-by-constraint table so the
 * question "what does phase 2 BUY with the 21.5 recurrence points it burns" is answerable
 * offline (how much M20/M22 it gains, against how much M32 it pays).
 *
 * <p>Reuses {@link ScoreASnapshotTest}'s exact scoring path ({@link SnapshotFixture}
 * reconstruction + {@code seedMediumTierFacts} so the medium tier is comparable, including
 * M32 via the assignment-agnostic reference template of TASK-416), looped over the committed
 * dumps. The phase-boundary dump {@code phase-N-*.txt} is the state at the ENTRY of phase N,
 * i.e. the END of phase N-1 (phase-2 = construction end, phase-3 = end of phase 2, and so on).
 *
 * <p>Each state is scored in its OWN committed transaction ({@link
 * QuarkusTransaction#requiringNew}) so the six seedings never share a persistence context.
 *
 * <p><b>Comparability caveats (localized-testing-guidelines section 3):</b> (1) the pipe dumps
 * capture ONLY placed lessons, so a reconstructed snapshot has ZERO boxed lessons and its hard
 * score does NOT include H0 unassigned penalties (the real phases.json hard values include them);
 * the hard numbers here isolate H1/H3/H17-style violations of the placed grid, not the full run
 * hard. (2) DailyActivityQuota / expected-hours facts are seeded here by the harness (not carried
 * in the dump), so M20/M12 score against the production per-date target exactly as a live solve,
 * which is the point of the comparison. (3) TREND/diagnosis only, never adoption.
 *
 * <p><b>TASK-420 (2026-07-27):</b> the dump names below are val408-run1's, captured under the
 * old 6-phase numbering (phase 3 was the RuinRecreate LS, since removed). They are committed
 * artifacts and keep those names, so this test keeps working; a run captured after TASK-420
 * writes 5 boundaries instead (phase-3 = Compaction, phase-4 = room CH, phase-5 = room LS),
 * and pointing this test at such a run means updating the map below.
 *
 * <p>Gated OFF via {@code PLANNER_TEST_SCORE_TRAJECTORY} (env var, not {@code -D}).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_SCORE_TRAJECTORY", matches = ".+")
class Task415ScoreTrajectoryTest {

    private static final Path RUN = Path.of("claudedocs", "spalmone-2026-27",
            "task408-results", "val408-run1");

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;
    @Inject EntityManager em;

    @Test
    void scoreEveryPhaseBoundary() {
        // (label, source dump file). Order = pipeline order (construction end -> final).
        Map<String, Path> states = new LinkedHashMap<>();
        Path pt = RUN.resolve("phase-transitions");
        states.put("entry-ph2 (constr end)", pt.resolve("phase-2-2-ls-compactness.txt"));
        states.put("entry-ph3 (end ph2)", pt.resolve("phase-3-3-ls-advanced-ruinrecreate.txt"));
        states.put("entry-ph4 (end ph3)", pt.resolve("phase-4-4-compaction.txt"));
        states.put("entry-ph5 (end ph4)", pt.resolve("phase-5-5-ch-room-assignment.txt"));
        states.put("entry-ph6 (end ph5)", pt.resolve("phase-6-6-ls-room-refinement.txt"));
        states.put("final (end ph6)", RUN.resolve("lessons.txt"));

        System.out.println("=== TASK-415 score trajectory: val408-run1 (medium tier seeded; zero-boxed snapshots) ===");
        System.out.printf("  %-24s %8s %12s %12s %10s %8s %8s %10s %8s%n",
                "state", "hard", "medium", "M20*", "M22", "M15", "M18", "M32", "M12");

        for (Map.Entry<String, Path> e : states.entrySet()) {
            Row r = QuarkusTransaction.requiringNew().timeout(300).call(() -> scoreOne(e.getKey(), e.getValue()));
            System.out.printf("  %-24s %8d %12d %12d %10d %8d %8d %10d %8d%n",
                    e.getKey(), r.hard, r.medium, r.m20, r.m22, r.m15, r.m18, r.m32, r.m12);
        }
        System.out.println("  (M20* = sum of every constraint id starting 'M20'; others are exact-id medium totals)");
        System.out.println("  hard EXCLUDES H0 (dumps have zero boxed); medium comparable to live (quota facts seeded)");
    }

    Row scoreOne(String label, Path sourceDump) throws Exception {
        // Fresh DB + statics per state (own transaction, own persistence context).
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();

        Path workDir = Path.of("target", "task415-traj", sanitize(label));
        Files.createDirectories(workDir);
        Files.copy(sourceDump, workDir.resolve("lessons.txt"), StandardCopyOption.REPLACE_EXISTING);

        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(workDir, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        SchoolSchedule schedule = snap.problem;
        solutionManager.update(schedule);
        BendableScore total = (BendableScore) schedule.getScore();

        long m20 = 0, m22 = 0, m15 = 0, m18 = 0, m32 = 0, m12 = 0;
        var factory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager)
                .getScoreDirectorFactory();
        try (var sd = factory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            sd.setWorkingSolution(schedule);
            sd.calculateScore();
            for (var ct : sd.getConstraintMatchTotalMap().values()) {
                String id = ct.getConstraintRef().id();
                long med = ScoreCompat.mediumOf(ct.getScore());
                if (id.startsWith("M20")) m20 += med;
                else if (id.startsWith("M22")) m22 += med;
                else if (id.startsWith("M15")) m15 += med;
                else if (id.startsWith("M18")) m18 += med;
                else if (id.startsWith("M32")) m32 += med;
                else if (id.startsWith("M12")) m12 += med;
            }
        }
        return new Row(ScoreCompat.hardOf(total), ScoreCompat.mediumOf(total), m20, m22, m15, m18, m32, m12);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9]+", "_");
    }

    private record Row(long hard, long medium, long m20, long m22, long m15, long m18, long m32, long m12) {
    }
}
