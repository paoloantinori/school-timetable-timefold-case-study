package com.school.planner.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.ExactFinisher;
import com.school.planner.solver.StudentDayCompaction;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TASK-478 AC#4 discriminator, now a PINNED regression guard (2026-08-02): re-runs
 * {@link ExactFinisher#repair} on the run4 post-state with the budget UNCAPPED and asserts the
 * verdict the live run established (5 of 5 placed at depth 0..1). The answer comes from the SAME
 * gate chain the live probe uses (not the AC#1 Python proxy, whose H2/H20/H5 caveats were exactly
 * the open question).
 *
 * <p><b>Why the 5 residual answer the 18 too.</b> The export is POST-EF: the 13 lessons EF placed
 * @ 100000 are already in the grid (timeslot set). The 5 left boxed (timeslot null) are the
 * residual. Re-running EF UNCAPPED on this post-state is byte-for-byte "EF resumes past the cap":
 * the grid it sees is the grid EF itself produced. So all 18 were depth-0/1 placeable iff these 5
 * close (13 proven by run4's EF + these 5 here). No need to reconstruct the 18 (the pre-EF grid is
 * not a captured artifact).
 *
 * <p><b>Pinned verdict (outcome i):</b> run4's 5 residuals CLOSE at depth 0..1 uncapped, so the
 * budget cap was the only obstacle and depth-2 is NOT required for the known worst case. The test
 * guards that invariant: if a gate-chain change ever stops closing run4's residual, this fails
 * loudly. (The probe @ knob 1000000 confirms the same on a fresh bad seed; this test fixes the
 * run4 trajectory deterministically.)
 *
 * <p><b>Fidelity (no proxy):</b> {@link SnapshotFixture#loadFromExport} reconstructs the run4 grid
 * room- and id-faithfully from the committed {@code export.json.gz} AND with
 * {@code includeQ22GoldenDerivedRules=true} (run4 is a post-Q22 snapshot, Q22 active since
 * 2026-07-24). This is load-bearing: 4 of the 5 residuals are T19/G06/Barberia, and T19 has
 * ONLY Q22 availability rules (VACATION Wed/Thu/Fri -> Mon-Tue only); passing {@code false} would
 * drop them and widen T19's VRP from 2 days to 5, and the verdict would be a false pass on an
 * unfaithful grid (caught by the DoD /code-review skeptic, 2026-08-02). {@link
 * SnapshotFixture#seedMediumTierFacts} layers the production {@code DailyActivityQuota} +
 * expected-hours facts, which the VRP filter ({@link Lesson#getAvailableTimeslots}) consults for
 * no-session (H26) and lab-quota-day (H23) filtering. This matters for run4's residual in
 * particular: 4 of the 5 are T19/G06/Barberia LAB lessons, whose VRP cells are quota-bound
 * (lab days), so empty quota would silently widen the candidate set. ExactFinisher does NOT gate
 * on H2/rooms (it nulls the room; rooms are B-ROOM's job), so room fidelity is not load-bearing
 * for the verdict but is carried faithfully regardless. The 5 residual are all unpinned (confirmed
 * against the export), so EF's {@code !pinned && timeslot == null} box scan finds exactly them.
 *
 * <p>Default-suite regression guard (like {@link BoxRepairCheckpointTest} and {@link
 * BoxVrpFeasibilityDiscriminatorTest}); run it alone with
 * {@code ./scripts/core/test.sh -c ExactFinisherRun4DiscriminatorTest}. It boots Quarkus +
 * reconstructs ~12.5k lessons (~15-20s); the EF pass itself is sub-second.
 */
@QuarkusTest
class ExactFinisherRun4DiscriminatorTest {

    private static final Path RUN4_EXPORT =
            Path.of("claudedocs", "spalmone-2026-27", "task478-results", "ab-ef-run4", "export.json.gz");

    private static final String FLAG = "planner.exact-finisher.enabled";
    private static final String BUDGET = "planner.exact-finisher.max-candidates";

    /**
     * The uncapped budget. A constrained residual scans the whole ~1,368-cell VRP range at
     * depth-0, and depth-1 is ~1,368 outer cells x up to ~1,368 relocation cells per blocker;
     * 5 residuals therefore need well under 10M evaluations in the worst case, so 100M is
     * effectively uncapped (any residual left boxed here is left by the GATES, not the budget).
     */
    private static final String UNCAP = "100000000";

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject EntityManager em;

    private String priorFlag;
    private String priorBudget;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @BeforeEach
    void saveProps() {
        priorFlag = System.getProperty(FLAG);
        priorBudget = System.getProperty(BUDGET);
    }

    @AfterEach
    void restoreProps() {
        if (priorFlag != null) {
            System.setProperty(FLAG, priorFlag);
        } else {
            System.clearProperty(FLAG);
        }
        if (priorBudget != null) {
            System.setProperty(BUDGET, priorBudget);
        } else {
            System.clearProperty(BUDGET);
        }
    }

    /**
     * The discriminator: ExactFinisher UNCAPPED on the run4 post-state. Pins the structural
     * invariants that hold for the known verdict (box is exactly the 5 committed H0; the pass is
     * 0-hard-additive by construction) plus the verdict itself (residual 0, outcome i). Property
     * teardown is {@link #restoreProps()} (@AfterEach), which runs on every exit path.
     */
    @Test
    @Timeout(value = 300)
    @Transactional
    void run4ResidualUncappedDepth01Discriminator() throws Exception {
        // clone pubblico: l'export .gz e' espulso (regola zero-binari,
        // TASK-684) e il banco si salta; nel repo PRIVATO (publish-prep
        // presente) un export assente e' una PERDITA e fallisce forte
        if (!Files.exists(RUN4_EXPORT)) {
            if (Files.exists(Path.of("publish-prep"))) {
                org.junit.jupiter.api.Assertions.fail(
                        "export perso nel repo privato: " + RUN4_EXPORT);
            }
            Assumptions.assumeTrue(false, "export assente (clone pubblico): " + RUN4_EXPORT);
        }
        System.setProperty(FLAG, "true");
        System.setProperty(BUDGET, UNCAP);

        // includeQ22GoldenDerivedRules = TRUE: run4 is a post-Q22 snapshot (Q22 active since
        // 2026-07-24), and 4 of the 5 residuals are T19/G06/Barberia. T19 has ONLY Q22
        // availability rules (VACATION Wed/Thu/Fri -> Mon-Tue only); dropping them would widen
        // T19's VRP from 2 days to 5 and the verdict would be a false pass on an unfaithful grid.
        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadFromExport(RUN4_EXPORT, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);

        SchoolSchedule problem = snap.problem;
        List<Lesson> boxed = snap.boxedLessons;

        // Structural invariants.
        assertEquals(5, boxed.size(), "run4 leftover: exactly the 5 H0 residuals");
        assertEquals(0, StudentDayCompaction.countConflicts(problem),
                "checkpoint starts H1/H3 conflict-free (run4 final grid)");
        assertEquals(0, SchoolSchedule.getPromotedLessonIdsStatic().size(),
                "EF runs post-release (no promoted pins held)");

        System.out.println("=== TASK-478 ExactFinisher UNCAPPED discriminator (run4 post-state) ===");
        System.out.printf("  budget=%s (uncapped), depth<=%d, box=%d%n",
                UNCAP, ExactFinisher.MAX_DEPTH, boxed.size());
        System.out.println("  residuals:");
        for (Lesson l : boxed) {
            System.out.printf("    %-8s | %-13s | %-25s | lab=%s%n",
                    l.getStudentGroup() != null ? l.getStudentGroup().name : "?",
                    l.getTeacher() != null ? l.getTeacher().name : "?",
                    l.getSubject(), l.course != null && l.course.isLab);
        }

        ExactFinisher.Result r = ExactFinisher.repair(problem,
                (l, ts) -> l.timeslot = ts, l -> l.room = null);

        System.out.printf("  RESULT: placed %d of %d, residual %d, conflicts added %d%n",
                r.movesApplied(), boxed.size(), r.residualAfter(), r.conflictsAdded());
        System.out.println("  placements:");
        for (Lesson l : boxed) {
            String cell = l.timeslot == null ? "STILL BOXED"
                    : l.timeslot.getDate() + "@" + l.timeslot.startDateTime.getHour();
            System.out.printf("    %-8s | %-13s | %-25s -> %s%n",
                    l.getStudentGroup() != null ? l.getStudentGroup().name : "?",
                    l.getTeacher() != null ? l.getTeacher().name : "?",
                    l.getSubject(), cell);
        }

        // The pass is 0-hard-additive by construction: the start grid is conflict-free, the boxed
        // lessons have null timeslots (no H1/H3 contribution), and every committed placement passed
        // the full gate chain, so conflictsAdded is provably 0.
        assertEquals(0, r.conflictsAdded(), "ExactFinisher is 0-hard-additive by construction");
        // Pinned verdict (outcome i): run4's residual closes at depth 0..1 uncapped, so the cap was
        // the only obstacle and depth-2 is not required for the known worst case.
        assertEquals(0, r.residualAfter(),
                "discriminator verdict: run4's 5 residuals close at depth 0..1 uncapped (cap was the only obstacle)");
        System.out.println("VERDICT: (i) depth-0/1 completion EXISTS uncapped -> the cap was the only obstacle. "
                + "Probe @ knob 1000000 is the right next step; depth-2 is not required for the known worst case.");
    }
}
