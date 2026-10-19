package com.school.planner.solver;

import static com.school.planner.TestDataBuilder.course;
import static com.school.planner.TestDataBuilder.group;
import static com.school.planner.TestDataBuilder.lesson;
import static com.school.planner.TestDataBuilder.room;
import static com.school.planner.TestDataBuilder.teacher;
import static com.school.planner.TestDataBuilder.timeslot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.Timeslot;

/**
 * TASK-652: the shared room-availability predicate. Pins the semantics the
 * eight call sites used to hand-roll: id equality (the domain's own equals),
 * null safety, mover self-exclusion, and the H2 co-presence exemption hook.
 */
class RoomAvailabilityTest {

    private static final LocalDate D = LocalDate.of(2026, 9, 21);

    private final Timeslot tsA = timeslot(1L, D, 8, 0, 9, 0);
    private final Timeslot tsA2 = timeslot(1L, D, 10, 0, 11, 0); // same id, different object
    private final Timeslot tsB = timeslot(2L, D, 8, 0, 9, 0);
    private final Room lab = room(10L, "R16", 20);
    private final Room aula = room(11L, "R07", 22);

    private Lesson mk(long id, Timeslot ts, Room r) {
        return lesson(id, course(100L, "Materia", teacher(200L, "Rossi"), group(300L, "G04", 15)), ts, r);
    }

    // ─── occupies ───────────────────────────────────────────────────────

    @Test
    void occupiesMatchesRoomAndTimeslotById() {
        Lesson x = mk(1L, tsA, lab);
        Lesson sameCellDifferentObjects = mk(2L, tsA2, room(10L, "R16-copy", 5));
        assertTrue(RoomAvailability.occupies(x, lab, tsA));
        assertTrue(RoomAvailability.occupies(sameCellDifferentObjects, lab, tsA2),
                "id equality, not reference equality (cloned facts share ids)");
        assertFalse(RoomAvailability.occupies(x, aula, tsA), "different room");
        assertFalse(RoomAvailability.occupies(x, lab, tsB), "different timeslot");
    }

    @Test
    void occupiesIsNullSafeOnEveryArgument() {
        Lesson x = mk(1L, tsA, lab);
        Lesson noRoom = mk(2L, tsA, null);
        Lesson noTs = mk(3L, null, lab);
        assertFalse(RoomAvailability.occupies(null, lab, tsA));
        assertFalse(RoomAvailability.occupies(x, null, tsA));
        assertFalse(RoomAvailability.occupies(x, lab, null));
        assertFalse(RoomAvailability.occupies(noRoom, lab, tsA));
        assertFalse(RoomAvailability.occupies(noTs, lab, tsA));
    }

    @Test
    void occupiesMatchesSameInstanceWithNullId() {
        // Regression from the SurplusHarvestTest migration: fixtures build
        // rooms via new Room(...) with NO id, and holder/donor share the SAME
        // instance. Domain equals hits the identity fast path there; an
        // id-only predicate wrongly reported the room free.
        Room nullIdRoom = new Room("BusyRoom", 20, List.of());
        Timeslot nullIdTs = new Timeslot();
        nullIdTs.startDateTime = tsA.startDateTime;
        Lesson holder = lesson(7L, course(101L, "Holder", teacher(201L, "Bianchi"), group(301L, "G08", 15)),
                nullIdTs, nullIdRoom);
        Lesson guest = mk(8L, tsA, nullIdRoom);
        assertTrue(RoomAvailability.occupies(holder, nullIdRoom, nullIdTs),
                "same instance, null id: the identity fast path of equals");
        assertTrue(RoomAvailability.occupies(guest, nullIdRoom, tsA),
                "same room instance under a different (id-bearing) timeslot");
    }

    // ─── conflicts ──────────────────────────────────────────────────────

    @Test
    void conflictsExcludesTheMoverItself() {
        Lesson mover = mk(1L, tsA, lab);
        assertFalse(RoomAvailability.conflicts(mover, lab, tsA, mover, null), "self by reference");
        Lesson sameIdOtherObject = mk(1L, tsA, lab);
        assertFalse(RoomAvailability.conflicts(mover, lab, tsA, sameIdOtherObject, null),
                "self by id: a working solution holds each lesson once");
        Lesson other = mk(2L, tsA, lab);
        assertTrue(RoomAvailability.conflicts(mover, lab, tsA, other, null));
        Lesson otherRoom = mk(2L, tsA, aula);
        assertFalse(RoomAvailability.conflicts(mover, lab, tsA, otherRoom, null));
    }

    @Test
    void conflictsHonorsTheCoPresenceExemption() {
        Lesson guest = mk(1L, tsA, lab);
        Lesson host = mk(2L, tsA, lab);
        RoomAvailability.CoPresenceExemption allExempt = (a, b) -> true;
        RoomAvailability.CoPresenceExemption noneExempt = (a, b) -> false;
        assertFalse(RoomAvailability.conflicts(guest, lab, tsA, host, allExempt),
                "an H2-exempt pair may share the room");
        assertTrue(RoomAvailability.conflicts(guest, lab, tsA, host, noneExempt));
        assertTrue(RoomAvailability.conflicts(guest, lab, tsA, host, null),
                "null exemption is today's conservative behavior");
    }

    // ─── occupied ───────────────────────────────────────────────────────

    @Test
    void occupiedReportsAnyHolder() {
        Lesson holder = mk(1L, tsA, lab);
        assertTrue(RoomAvailability.occupied(lab, tsA, List.of(holder)));
        assertFalse(RoomAvailability.occupied(aula, tsA, List.of(holder)));
        assertFalse(RoomAvailability.occupied(null, tsA, List.of(holder)));
        assertFalse(RoomAvailability.occupied(lab, tsA, List.of()));
    }

    // ─── occupiedRoomIdsBySlot ──────────────────────────────────────────

    @Test
    void occupiedRoomIdsBySlotGroupsByTimeslotIdAndSkipsUnplaced() {
        Lesson placed1 = mk(1L, tsA, lab);
        Lesson placed2 = mk(2L, tsA, aula);
        Lesson sameSlotOtherObject = mk(3L, tsA2, lab); // ts id 1 again
        Lesson noRoom = mk(4L, tsB, null);
        Lesson noTs = mk(5L, null, lab);
        Map<Long, Set<Long>> bySlot = RoomAvailability.occupiedRoomIdsBySlot(
                List.of(placed1, placed2, sameSlotOtherObject, noRoom, noTs));
        assertEquals(Map.of(1L, Set.of(10L, 11L)), bySlot,
                "one entry per timeslot id, unplaced lessons skipped");
        assertTrue(RoomAvailability.occupiedRoomIdsBySlot(List.of()).isEmpty());
    }
}
