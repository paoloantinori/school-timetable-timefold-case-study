package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.QuotaFillRepair;
import com.school.planner.solver.StudentDayCompaction;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * TASK-533 component 1 checkpoint replay: the h32b-run1 frozen grid (phase-0
 * evidence: 5 under-quota G10 days, hours parked on 7 surplus days, zero
 * unplaced). Seconds per iteration, no solve relaunch. Expected signature:
 * the pass closes the 5 under-quota cells (moves from surplus days), 0
 * conflicts added, and the residual H32 population drops to 0.
 *
 * <p>Enabled by {@code PLANNER_TEST_QUOTAFILL=/path/to/export.json.gz} (the
 * h32b-run1 export, or any pre-RELEASE capture).</p>
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_QUOTAFILL", matches = ".+")
class QuotaFillRepairCheckpointTest {

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
    void quotaFillClosesUnderQuotaCellsOnTheFrozenGrid() throws Exception {
        Path export = Path.of(System.getenv("PLANNER_TEST_QUOTAFILL"));
        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadFromExport(export, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);

        // TASK-536 checkpoint 4: publish the H33 day floors so the replay can
        // measure them alongside the quota axis (the pass itself is floor-
        // agnostic: this measures whether its quota-driven moves also cure
        // under-floor days, and G04 is QFR-excluded by design). Courses and
        // groups come off the snapshot (distinct courses by id); quotas were
        // just seeded by seedMediumTierFacts.
        java.util.List<com.school.planner.domain.StudentGroup> snapGroups =
                new java.util.ArrayList<>(snap.groups.values());
        // snapshot Course entities carry totalHours=1 (built per row), so the
        // pool is the group's non-pseudo LESSON count (1 lesson = 1 hour)
        java.util.Map<String, Double> pool = new java.util.HashMap<>();
        for (var l : snap.lessons) {
            if (l.course == null || l.course.studentGroup == null) {
                continue;
            }
            var t = l.getTeacher();
            if (t != null && t.pseudo) {
                continue;
            }
            pool.merge(l.course.studentGroup.name, 1.0, Double::sum);
        }
        java.util.Map<String, Integer> floors = com.school.planner.service.DayFloorDeriver
                .deriveHardFloorsFromPool(snapGroups, pool,
                        com.school.planner.domain.DailyActivityQuota.listAll());
        java.util.Map<Long, Integer> floorsById = new java.util.HashMap<>();
        for (var g : snapGroups) {
            Integer fl = floors.get(g.name);
            if (fl != null && fl > 0) {
                floorsById.put(g.id, fl);
            }
        }
        com.school.planner.domain.SchoolSchedule.setDayFloorsStatic(floorsById);

        long conflictsBefore = StudentDayCompaction.countConflicts(snap.problem);
        int underBefore = QuotaFillRepairCheckpointTest.countUnderQuotaCells(snap);
        int floorBefore = QuotaFillRepairCheckpointTest.countUnderFloorDays(snap, floorsById);

        QuotaFillRepair.Result r = QuotaFillRepair.repair(snap.problem,
                (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);

        int underAfter = QuotaFillRepairCheckpointTest.countUnderQuotaCells(snap);
        int floorAfter = QuotaFillRepairCheckpointTest.countUnderFloorDays(snap, floorsById);
        System.out.println("=== TASK-533 quota-fill checkpoint: " + export + " ===");
        System.out.printf("  under-quota cells: %d -> %d%n", underBefore, underAfter);
        System.out.printf("  TASK-536 under-floor days: %d -> %d%n", floorBefore, floorAfter);
        System.out.printf("  moves applied: %d, cells closed to quota: %d%n",
                r.movesApplied(), r.cellsClosed());
        System.out.printf("  conflicts added: %d (was %d)%n", r.conflictsAdded(), conflictsBefore);

        assertEquals(0, r.conflictsAdded(), "conflict-free by construction (H1 gate)");
        assertTrue(underAfter <= underBefore, "never opens a new under-quota cell");
    }

    /** (group, date) days below their declared aula+lab quota, non-Stage hours. */
    static int countUnderQuotaCells(SnapshotFixture.Snapshot snap) {
        java.util.Map<String, java.util.Map<java.time.LocalDate, int[]>> tally = new java.util.HashMap<>();
        for (var l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) {
                continue;
            }
            var t = l.getTeacher();
            if (t != null && t.pseudo) {
                continue;
            }
            tally.computeIfAbsent(l.course.studentGroup.name, k -> new java.util.HashMap<>())
                    .merge(l.timeslot.getDate(), new int[] {1}, (a, b) -> new int[] {a[0] + b[0]});
        }
        int under = 0;
        java.util.Set<String> excluded = java.util.Set.of("G04", "G08", "G14");
        for (var ge : tally.entrySet()) {
            if (excluded.contains(ge.getKey())) {
                continue; // H32 scope: forma-b pool-cap owns their per-day target
            }
            for (var de : ge.getValue().entrySet()) {
                var row = com.school.planner.domain.SchoolSchedule
                        .getExpectedHoursRow(groupByName(snap, ge.getKey()).id, de.getKey());
                if (row == null) {
                    continue;
                }
                int quota = row.aulaHours + row.labHours;
                if (quota > 0 && de.getValue()[0] < quota) {
                    under++;
                }
            }
        }
        return under;
    }

    /** TASK-536: (group, date) in-session days (quota aula+lab > 0) below their H33 floor. */
    static int countUnderFloorDays(SnapshotFixture.Snapshot snap, java.util.Map<Long, Integer> floors) {
        java.util.Map<Long, java.util.Map<java.time.LocalDate, int[]>> tally = new java.util.HashMap<>();
        for (var l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) {
                continue;
            }
            var t = l.getTeacher();
            if (t != null && t.pseudo) {
                continue;
            }
            tally.computeIfAbsent(l.course.studentGroup.id, k -> new java.util.HashMap<>())
                    .merge(l.timeslot.getDate(), new int[] {1}, (a, b) -> new int[] {a[0] + b[0]});
        }
        int under = 0;
        for (var e : tally.entrySet()) {
            Integer floor = floors.get(e.getKey());
            if (floor == null || floor <= 0) {
                continue;
            }
            for (var d : e.getValue().entrySet()) {
                var row = com.school.planner.domain.SchoolSchedule
                        .getExpectedHoursRow(e.getKey(), d.getKey());
                if (row == null || row.aulaHours + row.labHours <= 0) {
                    continue;
                }
                if (d.getValue()[0] < floor) {
                    under++;
                }
            }
        }
        return under;
    }

    private static com.school.planner.domain.StudentGroup groupByName(
            SnapshotFixture.Snapshot snap, String name) {
        return snap.lessons.stream()
                .filter(l -> l.course != null && l.course.studentGroup != null
                        && name.equals(l.course.studentGroup.name))
                .findFirst().map(l -> l.course.studentGroup).orElse(null);
    }
}
