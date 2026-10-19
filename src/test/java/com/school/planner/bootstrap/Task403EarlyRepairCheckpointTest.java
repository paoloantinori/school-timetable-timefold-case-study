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
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.CompactionPhase;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.StudentDayCompaction;

/**
 * TASK-403 evidence (E3). Runs phase 4 ({@link CompactionPhase} = BoxRepair + eviction +
 * compaction) on the ENTRY-of-phase-3 dump of val408-run1, which is the END of phase 2, to
 * quantify how much an EARLY repair (inserted between phase 2 and the inert phase 3) would
 * resolve. In the real run, phase 3 (RuinRecreate) was inert (score unchanged for the third
 * validation in a row) and the phase-4 repair that ran AFTER it took hard from -31 to -2. If
 * the same deterministic repair applied straight to the end-of-phase-2 state produces the same
 * hard collapse, then phase 3's ~10 minutes bought nothing the repair could not have done
 * immediately, the TASK-403 hypothesis.
 *
 * <p>Same checkpoint mechanics as {@link BoxRepairCheckpointTest} (reconstruct the grid via
 * {@link SnapshotFixture}, run ONLY {@link CompactionPhase}), with the medium-tier facts
 * seeded so the VRP gate BoxRepair consults is production-faithful for FC/quota groups.
 *
 * <p><b>Caveats (TASK-403's own E3 note, 2026-07-25):</b> val408-run1's committed
 * {@code phase-3-...txt} dump predates {@code PhaseTransitionMarker}'s box-dump sibling
 * (TASK-419), so it carries only placed lessons and this snapshot reconstructs with ZERO
 * boxed lessons - the hard measured here excludes H0, which is exactly the tier the real
 * phase-4 repair spends most of its improvement on (-31 -> -2, "quasi tutto H0" per the
 * task note). What CompactionPhase resolves on THIS snapshot is only the H17-gap and
 * H1/H3-conflict portion of the hard. {@link #boxedDump()} auto-detects a sibling
 * {@code phase-3-...-boxed.txt} next to any future dump path (e.g. a fresh laptop run captured
 * after TASK-419 lands): when present, it is loaded via {@link SnapshotFixture}'s existing
 * {@code boxed.txt} support and this checkpoint becomes DECISIVE for the box tier too;
 * when absent (as with the currently-committed val408-run1 dump), the test still runs with
 * the same inconclusive-for-H0 caveat as before. TREND/diagnosis only, never adoption either
 * way.
 *
 * <p><b>TASK-420 (2026-07-27):</b> this evidence is what retired phase 3. The RuinRecreate
 * local search is GONE from the pipeline, so a fresh run never writes a
 * {@code phase-3-3-ls-advanced-ruinrecreate.txt} again (its phase-3 dump is now the
 * Compaction boundary). The default path below still points at the COMMITTED val408-run1
 * artifact, which keeps its historical name, so this checkpoint keeps running unchanged;
 * point the env var at any other dump to re-measure the same deterministic repair from a
 * different state. The test itself is phase-independent: it builds its own solver config
 * with {@link CompactionPhase} alone and never reads {@code solverConfig.xml}.
 *
 * <p>Gated OFF via {@code PLANNER_TEST_403_REPAIR} (env var, not {@code -D}).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_403_REPAIR", matches = ".+")
class Task403EarlyRepairCheckpointTest {

    /** Default ph3-entry dump; il VALORE dell'env var (se non e' un flag 1/true)
     *  lo sovrascrive con un path esplicito - stesso idioma degli altri harness.
     *  I dump post-2026-07-25 sera hanno il sibling -boxed.txt che rende il
     *  test DECISIVO (vedi note TASK-403). */
    private static final Path PH3_ENTRY_DUMP = resolveDump();

    private static Path resolveDump() {
        String v = System.getenv("PLANNER_TEST_403_REPAIR");
        if (v != null && !v.isBlank() && !v.trim().equals("1") && !v.trim().equalsIgnoreCase("true")) {
            return Path.of(v.trim());
        }
        return Path.of("claudedocs", "spalmone-2026-27",
                "task408-results", "val408-run1", "phase-transitions",
                "phase-3-3-ls-advanced-ruinrecreate.txt");
    }

    /** TASK-419: the box dump written alongside {@link #PH3_ENTRY_DUMP} by a
     *  {@code PhaseTransitionMarker} run new enough to carry the box-dump sibling
     *  ({@code phase-<n>-<slug>-boxed.txt}, next to {@code phase-<n>-<slug>.txt}). The
     *  currently-committed val408-run1 artifact predates TASK-419 and has none - see the
     *  class javadoc. */
    private static Path boxedDump() {
        String name = PH3_ENTRY_DUMP.getFileName().toString(); // phase-3-3-ls-advanced-ruinrecreate.txt
        String boxedName = name.substring(0, name.length() - ".txt".length()) + "-boxed.txt";
        return PH3_ENTRY_DUMP.resolveSibling(boxedName);
    }

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

    @Test
    @Transactional
    void earlyCompactionOnEndOfPhase2() throws Exception {
        Path workDir = Path.of("target", "task403-ph3entry");
        Files.createDirectories(workDir);
        Files.copy(PH3_ENTRY_DUMP, workDir.resolve("lessons.txt"), StandardCopyOption.REPLACE_EXISTING);

        // TASK-419: if a box-dump sibling exists next to the phase-3-entry dump (a laptop run
        // captured with the PhaseTransitionMarker box-dump fix), copy it in as boxed.txt so
        // SnapshotFixture.loadSnapshotNoClean reconstructs the real box population instead of
        // zero. Absent (the currently-committed val408-run1 dump) means the same
        // inconclusive-for-H0 run as before - never a test failure either way.
        Path boxedDump = boxedDump();
        boolean boxSeeded = Files.exists(boxedDump);
        if (boxSeeded) {
            Files.copy(boxedDump, workDir.resolve("boxed.txt"), StandardCopyOption.REPLACE_EXISTING);
        }

        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(workDir, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        SchoolSchedule problem = snap.problem;
        List<Lesson> lessons = snap.lessons;

        int startGaps = StudentDayCompaction.countGaps(problem);
        long startConflicts = StudentDayCompaction.countConflicts(problem);
        long startBox = lessons.stream().filter(l -> !l.pinned && l.timeslot == null).count();

        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(new CustomPhaseConfig()
                        .withCustomPhaseCommandList(List.of(new CompactionPhase<SchoolSchedule>()))));
        Solver<SchoolSchedule> solver = SolverFactory.<SchoolSchedule>create(solverConfig).buildSolver();

        // Score the start state via the same solver's score director by solving a 0-op first?
        // Simpler: read the score after solve for the end; for the start, count gaps/conflicts
        // (the hard drivers) directly. Then solve and read the end ScoreCompat.
        SchoolSchedule solved = solver.solve(problem);
        BendableScore endScore = (BendableScore) solved.getScore();
        int endGaps = StudentDayCompaction.countGaps(solved);
        long endConflicts = StudentDayCompaction.countConflicts(solved);
        long endBox = solved.getLessons().stream().filter(l -> !l.pinned && l.timeslot == null).count();

        System.out.println("=== TASK-403 early-repair checkpoint: end-of-phase-2 state (val408-run1) ===");
        System.out.printf("  box source: %s (%s)%n", boxSeeded ? boxedDump : "none",
                boxSeeded ? "TASK-419 box dump found - box tier is DECISIVE" : "no box dump - box tier still INCONCLUSIVE (see class javadoc)");
        System.out.printf("  start: gaps(H17)=%d  conflicts(H1/H3)=%d  box(H0)=%d%n",
                startGaps, startConflicts, startBox);
        System.out.printf("  after CompactionPhase: hard=%d medium=%d soft=%d | gaps=%d conflicts=%d box=%d%n",
                ScoreCompat.hardOf(endScore), ScoreCompat.mediumOf(endScore), ScoreCompat.softOf(endScore),
                endGaps, endConflicts, endBox);
        System.out.printf("  delta: gaps %+d  conflicts %+d  box %+d%n",
                endGaps - startGaps, endConflicts - startConflicts, endBox - startBox);
        System.out.println("  real run: phase 4 (after INERT phase 3) took hard -31 -> -2; "
                + "this measures the same repair applied straight to end-of-phase-2");
        System.out.println("  (TREND/diagnosis only; NEVER adoption. laptop A/B x2 gate.)");

        org.junit.jupiter.api.Assertions.assertNotNull(endScore, "the compaction phase must produce a score");
    }
}
