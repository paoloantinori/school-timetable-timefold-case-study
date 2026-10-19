package com.school.planner.bootstrap;

import java.nio.file.Path;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.HoleRepair;
import com.school.planner.solver.StudentDayCompaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-419 checkpoint: replay a committed staged snapshot and prove {@link HoleRepair} closes
 * its H17 residual, offline and without re-solving. The counterpart of {@code
 * BoxRepairCheckpointTest} for the hole pass, and the executable version of what {@link
 * Task419H17GapAnatomyTest}'s greedy sizing measured by hand.
 *
 * <p><b>Reference measurement (val419 staged smoke2, 2026-07-26).</b> The anatomy harness read
 * 9 non-exempt H17 breaks totalling 14 hole slots (hard -14) on that dump, and sized the fix
 * at 5 slots closable by importing a free (off-template) hour and the other 9 by importing an
 * on-template one - 14 of 14, no conflict added. This test asserts the pass actually delivers
 * that on the same input, which the sizing alone could not: the sizing enumerated candidates
 * with a hand-written filter chain, while this runs the production code and its shared
 * {@link com.school.planner.solver.BoxRepair} gates.
 *
 * <p>Env-gated because the snapshot is a local run artifact, not a committed fixture:
 * {@code PLANNER_TEST_HOLE_REPAIR=/tmp/val419-staged-smoke2 ./scripts/core/test.sh -c Task419HoleRepairCheckpointTest}.
 * With {@code 1}/{@code true} it falls back to {@link SnapshotFixture#DEFAULT_SNAPSHOT}, where
 * the absolute numbers differ (a pre-staged run has its own H17 residual) - hence the two
 * assertions that always hold, on any snapshot, are the INVARIANTS (no conflict added, never
 * more holes than it started with), while the 14 -> 0 figure is only asserted when the smoke2
 * dump is the one supplied.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_HOLE_REPAIR", matches = ".+")
class Task419HoleRepairCheckpointTest {

    /** The snapshot the 14 -> 0 reference figure was measured on (see the class javadoc). */
    private static final String SMOKE2 = "val419-staged-smoke2";

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject jakarta.persistence.EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    @Transactional
    void holeRepairClosesTheStagedH17Residual() throws Exception {
        Path dir = SnapshotFixture.resolveSnapshotDir(System.getenv("PLANNER_TEST_HOLE_REPAIR"));
        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(dir, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);

        int holesBefore = HoleRepair.countPenalizedGapSlots(snap.lessons);
        long conflictsBefore = StudentDayCompaction.countConflicts(snap.problem);

        HoleRepair.Result r = HoleRepair.repair(snap.problem,
                (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);

        System.out.println("=== TASK-419 hole repair checkpoint: " + dir + " ===");
        System.out.printf("  H17 penalized hole slots: %d -> %d (closed %d)%n",
                r.gapSlotsBefore(), r.gapSlotsAfter(), r.closed());
        System.out.printf("  by level: free import %d, min-slide %d, on-template import %d%n",
                r.closedByFreeImport(), r.closedByMinSlide(), r.closedByTemplateImport());
        System.out.printf("  conflicts added: %d (was %d before the pass)%n",
                r.conflictsAdded(), conflictsBefore);

        // Invariants, true on ANY snapshot: the pass is conflict-free and improve-or-noop, and
        // its own before-count agrees with the standalone counter (one definition of a hole).
        assertEquals(holesBefore, r.gapSlotsBefore(), "the pass and the counter must see the same holes");
        assertEquals(0, r.conflictsAdded(), "conflict-free by construction");
        assertTrue(r.gapSlotsAfter() <= r.gapSlotsBefore(), "improve-or-noop");
        // >=, not ==: a level counter records the hole a move was aimed at, but the SAME move
        // can also collapse a hole at its origin (the departure gate forbids opening one, it
        // does not forbid closing one), so the measured reduction can exceed the tally.
        assertTrue(r.gapSlotsBefore() - r.gapSlotsAfter() >= r.closed(),
                "every closed hole must be reflected in the measured reduction");
        assertEquals(conflictsBefore, StudentDayCompaction.countConflicts(snap.problem),
                "the absolute conflict count is unchanged, not merely the delta");

        // The measured reference, asserted only on the dump it was measured on.
        if (dir.toString().contains(SMOKE2)) {
            assertEquals(14, r.gapSlotsBefore(),
                    "smoke2's known H17 residual: 9 non-exempt breaks, 14 hole slots (hard -14)");
            assertEquals(0, r.gapSlotsAfter(),
                    "the anatomy sized every one of the 14 as closable by a cross-day import");
        }
    }
}
