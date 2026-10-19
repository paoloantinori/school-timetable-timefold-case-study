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
import com.school.planner.solver.SurplusRebalance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-422 checkpoint: replay a committed snapshot and measure what {@link SurplusRebalance}
 * actually redistributes on it, offline and without re-solving. Twin of {@link
 * Task419HoleRepairCheckpointTest} for the day-fill pass, and the executable version of the
 * train-run1 decomposition that sized this task (164 deficit hours with a matching surplus on
 * 126 above-plan days of the same class).
 *
 * <p>Env-gated because the snapshot is a local run artifact, not a committed fixture:
 * {@code PLANNER_TEST_REBALANCE=claudedocs/spalmone-2026-27/task419-results/val419-staged-smoke2 ./scripts/core/test.sh -c Task422RebalanceCheckpointTest}.
 * With {@code 1}/{@code true} it falls back to {@link SnapshotFixture#DEFAULT_SNAPSHOT}. Every
 * assertion here is an INVARIANT that must hold on ANY snapshot - no absolute figure is pinned,
 * because the pass runs in the staged cycle AFTER the hole repair and each snapshot carries its
 * own surplus/deficit shape; the numbers are PRINTED for the gate to read.
 *
 * <p>The three invariants: no conflict added, the M20 + M20.2 day-fill cost never rises (and
 * strictly falls whenever a move was applied, since every move is net-positive by
 * construction), and the H17 penalized hole count never rises - the last one is what makes this
 * pass safe to run right after the hole repair, whose whole output it could otherwise undo.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_REBALANCE", matches = ".+")
class Task422RebalanceCheckpointTest {

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
    void rebalanceMovesSurplusHoursOntoDeficitDays() throws Exception {
        Path dir = SnapshotFixture.resolveSnapshotDir(System.getenv("PLANNER_TEST_REBALANCE"));
        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(dir, availabilityExpansionService, true);
        // The expected-hours facts ARE this pass's target definition, so they must be seeded
        // (M20's quota reads them): without them every day would fall back to maxHoursPerDay.
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);

        SurplusRebalance.DayFill before = SurplusRebalance.measure(snap.lessons);
        int holesBefore = HoleRepair.countPenalizedGapSlots(snap.lessons);
        long conflictsBefore = StudentDayCompaction.countConflicts(snap.problem);

        SurplusRebalance.Result r = SurplusRebalance.repair(snap.problem,
                (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);

        SurplusRebalance.DayFill after = SurplusRebalance.measure(snap.lessons);
        int holesAfter = HoleRepair.countPenalizedGapSlots(snap.lessons);

        System.out.println("=== TASK-422 surplus rebalance checkpoint: " + dir + " ===");
        System.out.printf("  hours moved above-quota -> deficit: %d%n", r.movesApplied());
        System.out.printf("  M20 + M20.2 day-fill cost: %d -> %d medium (saved %d)%n",
                r.before().m20Cost(), r.after().m20Cost(), r.m20Saved());
        // Read off the pass's own readout: deficit days are the days it would target (fill gain
        // > 0, so never an EMPTY expected day, which it does not open) and surplus days are genuine
        // NET surplus only (TASK-429: occupancy > effectiveQuota), excluding both quota-0 dates
        // (legal donors, but nothing M20 ever asked hours of) and at-plan-with-pin days whose zero
        // marginal was a false positive pre-netting. The excluded count sizes the TASK-429 fix.
        int excludedByNetting = SurplusRebalance.countSurplusDaysExcludedByNetting(snap.lessons);
        System.out.printf("  deficit days: %d -> %d, surplus days: %d -> %d%n",
                r.before().deficitDays(), r.after().deficitDays(),
                r.before().surplusDays(), r.after().surplusDays());
        System.out.printf("  surplus days excluded by netting (TASK-429): %d (at-plan-with-pin, no real surplus)%n",
                excludedByNetting);
        System.out.printf("  H17 penalized hole slots: %d -> %d%n", holesBefore, holesAfter);
        System.out.printf("  conflicts added: %d (was %d before the pass)%n",
                r.conflictsAdded(), conflictsBefore);

        assertEquals(before, r.before(), "the pass and the standalone measurement must agree");
        assertEquals(after, r.after());
        assertEquals(0, r.conflictsAdded(), "conflict-free by construction");
        assertEquals(conflictsBefore, StudentDayCompaction.countConflicts(snap.problem),
                "the absolute conflict count is unchanged, not merely the delta");
        assertTrue(r.after().m20Cost() <= r.before().m20Cost(), "improve-or-noop on the M20 axis");
        assertTrue(r.movesApplied() == 0 || r.after().m20Cost() < r.before().m20Cost(),
                "every applied move is M20-net-positive by construction, so any move must show up as a saving");
        assertTrue(holesAfter <= holesBefore,
                "the pass must never undo the hole repair that runs immediately before it");
        assertTrue(after.deficitDays() <= before.deficitDays(),
                "a day is only ever filled toward its plan, never emptied below it");
    }
}
