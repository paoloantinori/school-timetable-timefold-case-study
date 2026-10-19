package com.school.planner.bootstrap;

import java.nio.file.Path;
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

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.SolutionManager;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TASK-401/414 checkpoint harness, validates {@link com.school.planner.solver.BoxRepair}
 * (direct placement + TASK-414 one-level eviction) against the REAL stuck state a laptop
 * A/B ended in, WITHOUT a fresh 2h solve (the checkpoint protocol agreed 2026-07-23):
 * the run's final grid is reconstructed in-memory from the committed artifacts
 * ({@code lessons.txt}: placed rows incl. pins; {@code boxed.txt}: the un-taught
 * lessons), then ONLY phase 4 ({@link CompactionPhase} = repair + compaction + repair)
 * runs on it.
 *
 * <p>The claim under test is TASK-399's discriminator promise, extended by TASK-414:
 * each boxed lesson has VRP-legal cells, and either a free one exists (direct placement
 * clears it) or a single unpinned blocker can be evicted to a fully-legal cell
 * (eviction clears it). So enumeration-based repair must place ALL of them
 * (box → 0, i.e. the run's final H0 → 0) while adding zero H1/H3 conflicts and zero
 * H17 gaps (every placement, direct or evicted, passes the exact same gates plus the
 * new departure-H17-safe gate for the evicted occupant).
 *
 * <p>Two variants share one generalized runner ({@link #runCheckpoint}), mirroring
 * {@link BoxVrpFeasibilityDiscriminatorTest#runDiscriminator}:
 * <ul>
 * <li><b>val398-run2</b> (pre-Q22 world, {@code includeQ22Rules=false}): 6 boxed, grid
 *     starts gap-free. Hypothesis A, direct placement alone clears the box; eviction
 *     is a no-op here (proves the TASK-414 addition did not regress the TASK-401 path).</li>
 * <li><b>val410-run2</b> (Q22 world, {@code includeQ22Rules=true}): 11 boxed (~all
 *     T01/G06, Q22-confined to Thursdays) + 1 pre-existing H17 gap. The
 *     discriminator proved hypothesis B for this grid (T01: 216 Thursday VRP
 *     cells, 0 free), direct placement cannot clear it, eviction is structurally
 *     required, and the golden proves it feasible (231h all-Thursday).</li>
 * </ul>
 *
 * <p>Fixture fidelity mirrors {@link BoxVrpFeasibilityDiscriminatorTest} (same
 * VRP-relevant seeding via {@link RealSeedFixture}; the boxed lessons' groups have no
 * StagePattern and are not quota groups; the pinned-slot VRP exclusion is subsumed by
 * the reconstructed grid's own occupancy). Lessons and Courses are built in-memory
 * with hand-assigned ids (Timefold needs a {@code @PlanningId}, Hibernate is never
 * asked to persist them) — reconstruction takes seconds, not the 80s of a generated
 * fixture, and nothing here runs a local search.
 *
 * <p>Only the HARD tier is asserted. The medium score this harness computes is NOT
 * comparable to the laptop run's (no quota/expected-hours facts are seeded, so M20 falls
 * back to the calendar target) — irrelevant to the box/conflict/gap claim under test.
 */
@QuarkusTest
class BoxRepairCheckpointTest {

    // H31 (start-of-day hard, 19/08) makes each boxed placement also shift the
    // day-start offset, so the pre-H31 "final = start + box" arithmetic no
    // longer holds byte-exact. This test guards the BOX repair's own contract,
    // not H31 (which has StartOfDayHardConstraintTest): disable the flag for
    // the fixture's score arithmetic only.
    @org.junit.jupiter.api.BeforeAll
    static void disableStartOfDayHard() {
        System.setProperty("planner.constraints.start-of-day.enabled", "false");
    }

    @org.junit.jupiter.api.AfterAll
    static void restoreStartOfDayHard() {
        System.clearProperty("planner.constraints.start-of-day.enabled");
    }


    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    /** TASK-401: the pre-Q22 run-2 box (6 lessons) clears by direct placement alone. */
    @Test
    @Timeout(value = 300)
    @Transactional
    void repairClearsTheRun2BoxToZero() throws Exception {
        runCheckpoint(Path.of("claudedocs", "spalmone-2026-27", "task398-results", "val398-run2"),
                // TASK-408: 12295 -> 12233 - the 62 rows the old run placed on the
                // now-closed 2027-01-22 (closure typo fix) are dropped from the grid.
                12233, 6, false, 0, 62);
    }

    /**
     * TASK-414: the Q22-world run-2 box (11 lessons, ~all T01/G06 confined to
     * Thursdays) plus 1 pre-existing H17 gap. Direct placement cannot clear it
     * (discriminator verdict B: 0 free Thursday cells); one-level eviction must.
     */
    @Test
    @Timeout(value = 300)
    @Transactional
    void repairClearsVal410Run2BoxViaEviction() throws Exception {
        runCheckpoint(Path.of("claudedocs", "spalmone-2026-27", "task410-results", "val410-run2"),
                // TASK-408: 12290 -> 12227 - the 63 rows the old run placed on the
                // now-closed 2027-01-22 (closure typo fix) are dropped from the grid.
                12227, 11, true, 1, 63);
    }

    /**
     * Generalized checkpoint runner. Reconstructs the run's final grid + box in-memory,
     * runs ONLY phase 4 (repair incl. TASK-414 eviction + compaction + repair), and
     * asserts the box clears to 0 with no new conflict and no new gap.
     *
     * @param dir                the run artifact directory ({@code lessons.txt} + {@code boxed.txt})
     * @param expectedRows       placed-grid row count (static artifact)
     * @param expectedBoxed      box size (static artifact)
     * @param includeQ22Rules    seed the Q22 golden-derived availability rules (the run's world)
     * @param expectedStartGaps  the run's final H17 gap count (val398=0, val410=1)
     * @param expectedSkippedClosedDateRows  rows the loader must drop as now-closed dates
     *                           (TASK-408 train review: pinned so a future calendar change
     *                           can never silently vaporize snapshot rows)
     */
    private void runCheckpoint(Path dir, int expectedRows, int expectedBoxed,
                               boolean includeQ22Rules, int expectedStartGaps,
                               int expectedSkippedClosedDateRows) throws Exception {
        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(dir, availabilityExpansionService, includeQ22Rules);
        org.junit.jupiter.api.Assertions.assertEquals(expectedSkippedClosedDateRows, snap.skippedClosedDateRows,
                "now-closed-date rows dropped by the loader must match the pinned expectation");
        SchoolSchedule problem = snap.problem;
        List<Lesson> lessons = snap.lessons;
        List<Lesson> boxedLessons = snap.boxedLessons;

        assertEquals(expectedRows, lessons.size() - boxedLessons.size(), "final grid row count (static artifact)");
        assertEquals(expectedBoxed, boxedLessons.size(), "box size (static artifact)");

        // ── Checkpoint invariants before the phase. Both runs start conflict-free; their
        //    H17 gap count differs (val398=0, val410=1, the run's own final state). ──
        long startBox = lessons.stream().filter(l -> !l.pinned && l.timeslot == null).count();
        assertEquals(0, StudentDayCompaction.countConflicts(problem), "checkpoint must start conflict-free");
        assertEquals(expectedStartGaps, StudentDayCompaction.countGaps(problem),
                "checkpoint start gap count (the run's final H17)");
        assertEquals(expectedBoxed, startBox, "checkpoint start box size");

        // Capture the checkpoint's full start hard WITH the box (incl. any pre-existing
        // non-H17 hard on these historical fixtures, e.g. H28 teacher returns on a pre-H28
        // run): the repair places each boxed lesson, which under TASK-447 transitions it
        // H0(-2, timeslot-null) -> H29(-1, placed-but-room-null) = +1 hard net (the fixture
        // has no rooms, so every placed lesson is room-null and H29 is already in the
        // baseline). So the final hard == start hard + startBox. Robust to new hard
        // constraints added later.
        BendableScore startScore = (BendableScore) SolutionManager.create(
                SolverFactory.<SchoolSchedule>create(new SolverConfig()
                        .withSolutionClass(SchoolSchedule.class)
                        .withEntityClasses(Lesson.class)
                        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))))
                .update(problem);
        long startHard = ScoreCompat.hardOf(startScore);

        // ── Run ONLY phase 4 (repair + compaction + repair) on the checkpoint. ──
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(new CustomPhaseConfig()
                        .withCustomPhaseCommandList(List.of(new CompactionPhase<SchoolSchedule>()))));
        Solver<SchoolSchedule> solver = SolverFactory.<SchoolSchedule>create(solverConfig).buildSolver();
        SchoolSchedule solved = solver.solve(problem);

        // ── The TASK-401/414 claim: the box clears to 0 with no new conflict and no new gap. ──
        long boxAfter = solved.getLessons().stream().filter(l -> !l.pinned && l.timeslot == null).count();
        long conflictsAfter = StudentDayCompaction.countConflicts(solved);
        int gapsAfter = StudentDayCompaction.countGaps(solved);
        BendableScore score = (BendableScore) solved.getScore();
        System.out.printf("TASK-401/414 checkpoint repair (%s): box %d -> %d, conflicts %d, gaps %d->%d, score %s%n",
                dir.getFileName(), startBox, boxAfter, conflictsAfter, expectedStartGaps, gapsAfter, score);
        // solver.solve() returns the planning CLONE - resolve the boxed lessons by id in the
        // solved population (the originals in boxedLessons keep their pre-solve null timeslot).
        Map<Long, Lesson> solvedById = new HashMap<>();
        for (Lesson l : solved.getLessons()) solvedById.put(l.id, l);
        for (Lesson original : boxedLessons) {
            Lesson l = solvedById.get(original.id);
            System.out.printf("  %-8s | %-13s | %-30s -> %s%n",
                    l.getStudentGroup().name, l.getTeacher().name, l.getSubject(),
                    l.timeslot == null ? "STILL BOXED" : l.timeslot.getDate() + "@" + l.timeslot.startDateTime.getHour());
        }
        assertEquals(0, boxAfter, "the repair must place every boxed lesson (run's H0 -> 0)");
        assertEquals(0, conflictsAfter, "the repair must add no H1/H3 conflict");
        // Improve-only on gaps: the repair may add NO new H17 gap (every placement, direct
        // or evicted, is H17-safe arrival + H17-safe departure). A run that already ended
        // with a pre-existing H17 gap (val410: the solve's own 1 gap, not closeable by
        // single-lesson compaction) keeps exactly that gap, closing it is compaction/
        // local-search scope (TASK-390), not the H0-clearing repair's job.
        assertEquals(expectedStartGaps, gapsAfter,
                "the repair must add no new H17 gap (H17-safe cells + departure gate)");
        assertEquals(startHard + startBox, ScoreCompat.hardOf(score),
                "final hard = start hard + box (each boxed lesson placed transitions H0(-2) -> "
                        + "H29(-1) = +1 net; the fixture's placed lessons are room-null so H29 is already "
                        + "in start hard, and the repair changed no other hard - pre-existing H17 gaps "
                        + "and, for pre-H28 fixtures, H28 returns)");
    }
}
