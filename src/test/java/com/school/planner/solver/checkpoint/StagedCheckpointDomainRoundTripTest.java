package com.school.planner.solver.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;

/**
 * TASK-542 stage 2: domain-level capture/restore round-trip, TDD (no DB, no solver). Pins the
 * two stage-2 design notes from the backlog:
 * (a) CAPTURE reads {@code promotedPin} (not {@code pinned}) for the promoted flag - an
 *     authoritative pin is pinned=true/promotedPin=false and must NOT be captured as promoted;
 * (b) RESTORE writes back the grid (timeslot/room by id), the pinned flag AND the promotedPin
 *     flag, then republishes the promoted statics. The wiring test (resume endpoint) is stage 3.
 */
class StagedCheckpointDomainRoundTripTest {

    @AfterEach
    void clearStatics() {
        SchoolSchedule.setPromotedLessonIdsStatic(List.of());
    }

    @Test
    void captureReadsPromotedPinNotPinned() {
        Lesson authoritative = lesson(1L); // pinned, NOT promoted: the stage-2 trap
        authoritative.pinned = true;
        authoritative.promotedPin = false;
        Lesson promoted = lesson(2L);
        promoted.pinned = true;
        promoted.promotedPin = true;

        StagedCheckpoint checkpoint = StagedCheckpointCapture.from(
                List.of(authoritative, promoted), cursor(StagedPhaseBoundary.POST_PROMOTION),
                "[0/0]hard/[0/0]soft", 0L, 0L);

        // the authoritative pin must NOT be captured as promoted
        assertEquals(1, checkpoint.promotedLessonIds().size());
        assertTrue(checkpoint.promotedLessonIds().contains(2L));
        StagedCheckpointSerializer.fromJson(StagedCheckpointSerializer.toJson(checkpoint))
                .placements().stream()
                .filter(p -> p.lessonId() == 1L)
                .forEach(p -> assertTrue(!p.promoted(),
                        "authoritative pin (pinned, not promotedPin) must not carry the promoted flag"));
    }

    @Test
    void restorePutsTheGridBackExactlyAndRepublishesStatics() {
        Timeslot t1 = timeslot(100L);
        Timeslot t2 = timeslot(101L);
        Room r1 = room(7L);
        Lesson placed = lesson(1L);
        placed.timeslot = t1;
        placed.room = r1;
        placed.pinned = false;
        placed.promotedPin = false;
        Lesson boxed = lesson(2L);
        boxed.timeslot = null;
        boxed.room = null;
        Lesson promotedPin = lesson(3L);
        promotedPin.timeslot = t2;
        promotedPin.room = null;
        promotedPin.pinned = true;
        promotedPin.promotedPin = true;

        StagedCheckpoint checkpoint = StagedCheckpointCapture.from(
                List.of(placed, boxed, promotedPin), cursor(StagedPhaseBoundary.POST_PROMOTION),
                "[-12/0]hard/[0/0]soft", 1L, -12L);
        String json = StagedCheckpointSerializer.toJson(checkpoint);

        // trash the grid entirely, then restore from the serialized checkpoint
        placed.timeslot = null;
        placed.room = null;
        boxed.timeslot = t1;
        boxed.room = r1;
        promotedPin.pinned = false;
        promotedPin.promotedPin = false;
        SchoolSchedule.setPromotedLessonIdsStatic(List.of());

        StagedCheckpointRestore.apply(
                StagedCheckpointSerializer.fromJson(json),
                List.of(placed, boxed, promotedPin),
                Map.of(100L, t1, 101L, t2),
                Map.of(7L, r1));

        assertEquals(t1, placed.timeslot);
        assertEquals(r1, placed.room);
        assertNull(boxed.timeslot, "the boxed lesson must stay unassigned after restore");
        assertNull(boxed.room);
        assertTrue(promotedPin.pinned && promotedPin.promotedPin,
                "restore must write back BOTH pin flags: the resumed cycle's release/demotion"
                        + " semantics read promotedPin from the entity");
        assertTrue(SchoolSchedule.getPromotedLessonIdsStatic().contains(3L),
                "the promoted statics must be republished from the grid's promoted flag");
    }

    @Test
    void captureFailsLoudOnAPlacedLessonWithAnUnknownTimeslotId() {
        Lesson placed = lesson(1L);
        placed.timeslot = timeslot(999L);
        // the id IS resolvable at capture time (it comes from the live entity), so capture
        // succeeds; the failure mode is restore-side and must fail loud there:
        StagedCheckpoint checkpoint = StagedCheckpointCapture.from(
                List.of(placed), cursor(StagedPhaseBoundary.POST_B_ROOM),
                "[0/0]hard/[0/0]soft", 0L, 0L);
        Lesson target = lesson(1L);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> StagedCheckpointRestore.apply(checkpoint, List.of(target),
                        Map.of(), Map.of()),
                "a checkpoint referencing an id the resume host does not know must fail loud,"
                        + " not silently place the lesson");
    }

    // ---- fixtures -----------------------------------------------------------

    private static StagedCheckpointCursor cursor(StagedPhaseBoundary boundary) {
        return new StagedCheckpointCursor(boundary, 0, 1, 0L);
    }

    private static Lesson lesson(long id) {
        Lesson l = new Lesson();
        l.id = id;
        return l;
    }

    private static Timeslot timeslot(long id) {
        Timeslot t = new Timeslot();
        t.id = id;
        return t;
    }

    private static Room room(long id) {
        Room r = new Room();
        r.id = id;
        return r;
    }
}
