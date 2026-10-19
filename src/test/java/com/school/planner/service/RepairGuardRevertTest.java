package com.school.planner.service;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.TestDataBuilder;
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.ScheduleService.PlacementSnapshot;
import com.school.planner.solver.ScoreCompat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-473: the improve-or-noop guard's revert decision and the {@link ScheduleService.PlacementSnapshot}
 * that backs it. Pure unit (no @QuarkusTest): the verdict is a static function and the snapshot is plain
 * field capture/restore, so the decision table and the byte-identical restore are testable with a tiny
 * fixture, the same way {@code LabDayConsolidationTest} exercises its pass.
 *
 * <p>What this pins:
 * <ul>
 * <li>the headline behaviour change: a guard violation reverts instead of throwing (the abort that
 * killed the source=input run at AnchorRepair);</li>
 * <li>both triggers are kept (hard worsening AND compensated H1/H3 double-booking), the latter being
 * the only signal that catches a conflict B-ROOM cannot repair;</li>
 * <li>the snapshot restores the exact pre-pass (timeslot, room) for every lesson.</li>
 * </ul>
 * The real ScoreDirector path and the end-to-end cycle continuation are proven by the run3 re-test
 * (the campaign A/B gate), not here.
 */
class RepairGuardRevertTest {

    private long id;
    private Timeslot ts0;
    private Timeslot ts1;
    private Room room;
    private Course lab;

    @BeforeEach
    void setUpFixture() {
        LocalDate day = LocalDate.of(2026, 9, 18);
        id = 100;
        ts0 = TestDataBuilder.timeslot(1, day, 8, 0, 9, 0);
        ts1 = TestDataBuilder.timeslot(2, day, 9, 0, 10, 0);
        room = TestDataBuilder.room(10, "Aula 1", 20);
        Teacher t = TestDataBuilder.teacher(20, "T10");
        StudentGroup g = TestDataBuilder.group(30, "G13", 8);
        lab = TestDataBuilder.course(40, "Lab", t, g, true);
    }

    private Lesson labLesson(Timeslot ts) {
        return TestDataBuilder.lesson(id++, lab, ts, room);
    }

    // ---- the verdict decision table (ScheduleService.shouldRevert) ----

    private static ScoreCompat.HardLevels levels(long pure, long quasi) {
        return new ScoreCompat.HardLevels(pure, quasi);
    }

    @Test
    void shouldRevertFiresWhenTimeslotTierHardWorsens() {
        // The exact TASK-473 abort case: the H1/H3 counter was clean (0) but the authoritative
        // timeslot-tier hard worsened (-6 -> -7). Today's guard would throw; the cure reverts.
        assertTrue(ScheduleService.shouldRevert(levels(-6, 0), levels(-7, 0), 0));
    }

    @Test
    void shouldRevertStaysSilentOnImproveOrNoop() {
        assertFalse(ScheduleService.shouldRevert(levels(-6, 0), levels(-6, 0), 0), "no-op: hard unchanged, no conflict added");
        assertFalse(ScheduleService.shouldRevert(levels(-6, 0), levels(-5, 0), 0), "improve: hard better, no conflict added");
    }

    @Test
    void shouldRevertFiresOnConflictsAddedEvenWhenScoreCompensated() {
        // R1: net hard flat (or better) but a new H1/H3 double-booking appeared. B-ROOM moves only
        // rooms, so an H1 would survive into the final solution; the H1/H3 clause is the only
        // signal that catches the compensated case. This is the test that proves dropping the clause
        // (the strict single-metric alternative) would be wrong.
        assertTrue(ScheduleService.shouldRevert(levels(-6, 0), levels(-6, 0), 1), "compensated H1/H3 conflict must still revert");
        assertTrue(ScheduleService.shouldRevert(levels(-6, 0), levels(-5, 0), 1), "a net-improving pass that adds a conflict still reverts");
    }

    // ---- the lexicographic (pure, quasi) comparison (quasi-tier flag ON) ----

    @Test
    void shouldRevertSeesQuasiDamageWhenPureIsFlat() {
        // With the quasi-tier flag ON, H32/H34 sit on hard level 1. A pass that worsens them
        // while leaving pure hard untouched is damage the oracle MUST see: nothing downstream
        // (B-ROOM moves rooms only) can repair it.
        assertTrue(ScheduleService.shouldRevert(levels(-6, -3), levels(-6, -4), 0),
                "quasi worsened at pure flat: revert");
        assertFalse(ScheduleService.shouldRevert(levels(-6, -4), levels(-6, -3), 0),
                "quasi improved at pure flat: the repair passes keep working under the flag");
    }

    @Test
    void shouldRevertNeverLetsQuasiGainsPayForPureHardDamage() {
        // The sum-oracle divergence: pure hard worsened (-6 -> -7) while quasi improved enough
        // that a summed single number would look BETTER (net +97). The score's own ordering
        // forbids the trade: one pure-hard violation outweighs any number of quasi.
        assertTrue(ScheduleService.shouldRevert(levels(-6, -3), levels(-7, 100), 0),
                "pure-hard damage cannot be bought with quasi gains (lexicographic, not sum)");
    }

    // ---- PlacementSnapshot capture/restore (the revert mechanism) ----

    @Test
    void placementSnapshotRestoresTimeslotAndRoomByteIdentical() {
        Lesson a = labLesson(ts0);
        Lesson b = labLesson(ts1);
        PlacementSnapshot snap = PlacementSnapshot.capture(List.of(a, b));
        // Mutate like a repair pass: reassign timeslot/room by raw field writes (the callbacks).
        a.timeslot = ts1;
        a.room = null;
        b.timeslot = null;
        b.room = null;

        snap.restore();

        assertSame(ts0, a.timeslot, "lesson a timeslot restored to pre-capture");
        assertSame(room, a.room, "lesson a room restored to pre-capture");
        assertSame(ts1, b.timeslot, "lesson b timeslot restored to pre-capture");
        assertSame(room, b.room, "lesson b room restored to pre-capture");
    }

    @Test
    void placementSnapshotRestoreEndToEndRevertsAGuardViolation() {
        // The wiring: ensureImproveOrNoop must NOT throw on a violation, must restore the grid,
        // and must return reverted=true. Snapshots null because the per-family diagnostic is
        // best-effort; this test is about the revert mechanism, not the diagnostic text.
        Lesson moved = labLesson(ts0);
        PlacementSnapshot snap = PlacementSnapshot.capture(List.of(moved));
        moved.timeslot = ts1;   // the pass "improved" late-start but worsened co-presenza
        moved.room = null;

        boolean reverted = ScheduleService.ensureImproveOrNoop("anchor repair", levels(-6, 0),
                levels(-7, 0), 0L, snap, null, null);

        assertTrue(reverted, "the violation reverts instead of aborting");
        assertSame(ts0, moved.timeslot, "the moved lesson is rolled back to its pre-pass slot");
        assertSame(room, moved.room, "the moved lesson's room is rolled back too");
    }

    @Test
    void placementSnapshotIsNoOpForUnmovedLessons() {
        Lesson a = labLesson(ts0);
        PlacementSnapshot snap = PlacementSnapshot.capture(List.of(a));
        snap.restore();

        assertSame(ts0, a.timeslot, "unmoved lesson keeps its timeslot (reference-semantics contract)");
        assertSame(room, a.room);
        // ensureImproveOrNoop is also a no-op (returns false, does not touch the grid) when clean.
        assertFalse(ScheduleService.ensureImproveOrNoop("pass", levels(-6, 0), levels(-6, 0), 0L,
                PlacementSnapshot.capture(List.of(a)), null, null));
        assertSame(ts0, a.timeslot, "clean verdict leaves the grid untouched");
        assertSame(room, a.room, "clean verdict leaves the room untouched");
    }
}
