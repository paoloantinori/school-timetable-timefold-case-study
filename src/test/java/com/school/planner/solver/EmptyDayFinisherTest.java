package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-560 TDD: the synthetic banco of the diagnostic - a declared EMPTY day
 * whose group's donors all have TAIL lessons that cannot land at the target's
 * first slot (their teacher is busy there), while MIDDLE hours could. QFR v2's
 * tail-only extraction closes nothing on this shape (the live debug's "no
 * move"); the EmptyDayFinisher extracts the middle hour and slides the donor's
 * remaining hours down, atomically to quota.
 */
class EmptyDayFinisherTest {

    private static final LocalDate TARGET_DAY = LocalDate.of(2027, 5, 10);
    private static final LocalDate DONOR_DAY = LocalDate.of(2027, 5, 11);
    private static final LocalDate DONOR2_DAY = LocalDate.of(2027, 5, 12);

    private SchoolSchedule schedule;
    private List<Lesson> lessons;

    @BeforeEach
    void setUp() {
        System.setProperty(EmptyDayFinisher.FLAG, "true");
        schedule = build();
        lessons = schedule.getLessons();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(EmptyDayFinisher.FLAG);
        System.clearProperty(EmptyDayFinisher.V2_SWAP_FLAG);
        System.clearProperty(EmptyDayFinisher.H33_FLAG);
        System.clearProperty(EmptyDayFinisher.RELAY_FLAG);
        System.clearProperty(EmptyDayFinisher.H32_FLAG);
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setPromotedLessonIdsStatic(List.of());
        SchoolSchedule.setDayFloorsStatic(java.util.Map.of());
    }

    @Test
    void fillsTheEmptyDayFromMiddleHoursWhenTailsAreBlocked() {
        EmptyDayFinisher.Result r = EmptyDayFinisher.finish(schedule,
                (lesson, ts) -> lesson.timeslot = ts);
        System.out.println("EDF: " + r.movesApplied() + " moves, " + r.cellsClosed()
                + " cells, conflicts " + r.conflictsAdded());
        assertEquals(0, r.conflictsAdded(), "no H1/H3 conflict may be added");
        assertEquals(1, r.cellsClosed(), "the empty declared day must close");
        assertEquals(List.of(0, 1), slotsAt(group(), TARGET_DAY),
                "target fills contiguously from its first slot (gate d)");
        // donor 1 kept its start (slot 0) and stayed contiguous after the slide
        List<Integer> donor = slotsAt(group(), DONOR_DAY);
        assertEquals(0, donor.get(0), "donor start (slot 0) must not move (H31)");
        for (int i = 1; i < donor.size(); i++) {
            assertEquals(donor.get(i - 1) + 1, donor.get(i), "donor stays contiguous (H17)");
        }
        // donor 2 same contract
        List<Integer> donor2 = slotsAt(group(), DONOR2_DAY);
        assertEquals(0, donor2.get(0), "donor 2 start must not move");
        assertEquals(StudentDayCompaction.countConflicts(schedule), 0, "grid stays conflict-free");
    }

    @Test
    void flagOffIsANoop() {
        // RATIFIED default ON (27/08): absent -> on, so the noop pin must disarm the pass
        // with the opt-OUT literal "false" explicitly.
        System.setProperty(EmptyDayFinisher.FLAG, "false");
        EmptyDayFinisher.Result r = EmptyDayFinisher.finish(schedule,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(0, r.cellsClosed());
        assertEquals(0, r.movesApplied());
        assertTrue(slotsAt(group(), TARGET_DAY).isEmpty(), "target stays empty with the pass disarmed");
    }

    // ─── H33 family (TASK-577, 7/09): under-floor cells as targets ──
    // The v131 delivery's -8 decomposition: two H33 cells are G14 Fridays at
    // 2h (floor 3, quota 3, Friday window slots 0-2), the only legal third
    // slot (slot 2) free, and the group holds a +2 surplus Thursday
    // (quota 3, placed 5) that gate E accepts as donor (5-1 >= max(3,3)).
    // The H34 family can never see these cells (hours != 0) and G14 is
    // quota-fill-excluded anyway; H33 itself applies. The compound (mid-block
    // extraction + same-day slide + landing) is a 3-lesson move the LS's
    // move streams cannot compose, which is why the run shipped them.

    private static final LocalDate H33_FRI = LocalDate.of(2027, 3, 19);
    private static final LocalDate H33_THU = LocalDate.of(2027, 3, 18);
    private static final LocalDate H33_SURPLUS = LocalDate.of(2027, 5, 13);

    private static StudentGroup G14Ref;

    private static StudentGroup g14() {
        if (G14Ref == null) {
            G14Ref = new StudentGroup("G14", 20); // quota-fill-EXCLUDED name
            G14Ref.id = 9L;
        }
        return G14Ref;
    }

    /** G14 geometry: FRI quota 3 placed 2 (under floor 3), THU quota 5 placed
     *  5 (full, gate E must refuse it), SURPLUS quota 3 placed 5 (+2, the donor). */
    private static SchoolSchedule buildH33Banco() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quota(g14(), H33_FRI, 3), quota(g14(), H33_THU, 5),
                quota(g14(), H33_SURPLUS, 3)));
        SchoolSchedule.setDayFloorsStatic(java.util.Map.of(9L, 3));
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalDate d : List.of(H33_FRI, H33_THU, H33_SURPLUS)) {
            for (java.time.LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
                Timeslot ts = new Timeslot();
                ts.id = (long) (timeslots.size() + 1);
                ts.startDateTime = LocalDateTime.of(d, start);
                timeslots.add(ts);
            }
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        StudentGroup g = g14();
        List<Lesson> out = new ArrayList<>();
        long id = 50;
        for (int slot = 0; slot < 2; slot++) { // FRI: 2h, under floor
            out.add(lesson(id++, teacher(id, "F" + slot), g, timeslots.get(slot)));
        }
        for (int slot = 0; slot < 5; slot++) { // THU: 5h at quota 5
            out.add(lesson(id++, teacher(id, "T" + slot), g, timeslots.get(8 + slot)));
        }
        for (int slot = 0; slot < 5; slot++) { // SURPLUS: 5h, quota 3
            out.add(lesson(id++, teacher(id, "S" + slot), g, timeslots.get(16 + slot)));
        }
        return new SchoolSchedule(timeslots, List.<Room>of(), List.of(), List.of(), List.of(),
                List.of(g), List.of(), List.of(), out, List.of());
    }

    @Test
    void h33ClosesTheUnderFloorCellFromTheSurplusDonor() {
        System.setProperty(EmptyDayFinisher.H33_FLAG, "true");
        SchoolSchedule banco = buildH33Banco();
        EmptyDayFinisher.Result r = EmptyDayFinisher.finish(banco,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(0, r.conflictsAdded(), "no H1/H3 conflict may be added");
        assertEquals(1, r.cellsClosed(), "the under-floor Friday must close to floor");
        assertEquals(List.of(0, 1, 2), slotsOf(banco, g14(), H33_FRI),
                "Friday reaches 3h contiguously (slot 2 = the real geometry's hour 10)");
        // TASK-678 floor-form: for the quota-fill-EXCLUDED group BOTH donors are legal
        // (no quota family - hard or medium - scopes G14: the binding axis is the
        // floor alone), and the donor iteration order is not pinned. What IS pinned:
        // exactly one day yields exactly one hour, and EVERY yielding day keeps the
        // donor invariants (start unmoved for H31, contiguity for H17, above floor).
        int donors = 0;
        for (LocalDate day : List.of(H33_THU, H33_SURPLUS)) {
            List<Integer> slots = slotsOf(banco, g14(), day);
            int original = 5; // both donor days carry 5 placed hours (THU at quota, SURPLUS +2)
            if (slots.size() == original - 1) {
                donors++;
                assertEquals(0, slots.get(0), "donor start (slot 0) must not move (H31)");
                for (int i = 1; i < slots.size(); i++) {
                    assertEquals(slots.get(i - 1) + 1, slots.get(i), "donor stays contiguous (H17)");
                }
            } else {
                assertEquals(original, slots.size(), "untouched days stay untouched");
            }
        }
        assertEquals(1, donors, "exactly one donor yields exactly one hour");
        assertEquals(StudentDayCompaction.countConflicts(banco), 0);
    }

    @Test
    void h33FlagOffLeavesTheUnderFloorCellOpen() {
        // RATIFIED DEFAULT ON 04/10: the OFF arm is now the explicit opt-out
        System.setProperty(EmptyDayFinisher.H33_FLAG, "false");
        SchoolSchedule banco = buildH33Banco();
        EmptyDayFinisher.Result r = EmptyDayFinisher.finish(banco,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(0, r.cellsClosed());
        assertEquals(0, r.movesApplied());
        assertEquals(List.of(0, 1), slotsOf(banco, g14(), H33_FRI), "Friday untouched");
        assertEquals(List.of(0, 1, 2, 3, 4), slotsOf(banco, g14(), H33_SURPLUS), "surplus untouched");
    }

    /** TASK-680 (the discriminating form): a donor slide whose target slot is
     *  FRESH-legal but OUTSIDE the mover's CACHED range must be refused - the
     *  slide path now pays the cached-range membership, which is exactly the
     *  04/10 crash class (the fresh check alone would pass it; the director
     *  enforces the cache). The staleness window is manufactured in the fixture:
     *  the calendar rule closing Thursday slot 0 is published WHILE the movers'
     *  ranges are cached, then withdrawn before finish(): isSlotLegalFresh says
     *  legal (live rule set), the cached range still says no. Pre-680 this exact
     *  state shipped an out-of-range slide; the test fails on that code. */
    @Test
    void slideIntoARangeForbiddenSlotRefusesTheExtraction() {
        System.setProperty(EmptyDayFinisher.H33_FLAG, "true");
        StudentGroup g = g14();
        java.util.List<Timeslot> timeslots = new ArrayList<>();
        for (LocalDate d : List.of(H33_THU, H33_FRI)) {
            for (java.time.LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
                Timeslot ts = new Timeslot();
                ts.id = (long) (timeslots.size() + 1);
                ts.startDateTime = LocalDateTime.of(d, start);
                timeslots.add(ts);
            }
        }
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quota(g, H33_THU, 4), quota(g, H33_FRI, 2)));
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setDayFloorsStatic(java.util.Map.of(g.id, 3));
        // the target: 2h Friday (floor 3, need 1)
        Lesson f1 = lesson(70L, teacher(20L, "T1"), g, timeslots.get(8));
        Lesson f2 = lesson(71L, teacher(20L, "T1"), g, timeslots.get(9));
        // the donor: Thursday 4h; the upper pair shares a teacher so extracting
        // slot 0 forces the whole block to slide down
        Lesson t0 = lesson(72L, teacher(30L, "T2"), g, timeslots.get(0));
        Lesson t1 = lesson(73L, teacher(31L, "T3"), g, timeslots.get(1));
        Lesson t2 = lesson(74L, teacher(31L, "T3"), g, timeslots.get(2));
        Lesson t3 = lesson(75L, teacher(31L, "T3"), g, timeslots.get(3));
        SchoolSchedule banco = new SchoolSchedule(timeslots, List.of(), List.of(),
                List.of(), List.of(), List.of(g), List.of(), List.of(),
                List.of(f1, f2, t0, t1, t2, t3), List.of());

        // baseline: the extraction works (both slides legal)
        EmptyDayFinisher.Result ok = EmptyDayFinisher.finish(banco,
                (lesson, ts) -> lesson.timeslot = ts);
        assertTrue(ok.cellsClosed() >= 1, "baseline: the under-floor cell closes (found: " + ok + ")");

        // Fresh banco. THE STALENESS WINDOW: close Thursday slot 0 via the
        // calendar static, PRIME the movers' cached ranges under that rule
        // (getAvailableTimeslots computes and caches), then WITHDRAW the rule:
        // the fresh checks now see slot 0 open, the caches still exclude it.
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(
                new com.school.planner.domain.GroupCalendarRule(g,
                        java.time.DayOfWeek.THURSDAY, 1, 7)));
        try {
            // (the lessons are created below; the priming happens after)
            Lesson g1 = lesson(80L, teacher(20L, "T1"), g, timeslots.get(8));
            Lesson g2 = lesson(81L, teacher(20L, "T1"), g, timeslots.get(9));
            Lesson u0 = lesson(82L, teacher(30L, "T2"), g, timeslots.get(0));
            Lesson u1 = lesson(83L, teacher(31L, "T3"), g, timeslots.get(1));
            Lesson u2 = lesson(84L, teacher(32L, "T4"), g, timeslots.get(2));
            Lesson u3 = lesson(85L, teacher(31L, "T3"), g, timeslots.get(3));
            // cross-group H1 blockers at the Friday landing slot: T3 and T4 busy
            // there close the tail/mid escape hatches, so the ONLY extraction
            // that could still feed the target is u0's - whose slide u1->0 is
            // range-forbidden by the calendar rule. That isolates the gate.
            StudentGroup otherX = new StudentGroup("G06", 20);
            otherX.id = 78L;
            StudentGroup otherY = new StudentGroup("G10", 20);
            otherY.id = 79L;
            Lesson b3 = lesson(90L, teacher(31L, "T3"), otherX, timeslots.get(10));
            Lesson b4 = lesson(91L, teacher(32L, "T4"), otherY, timeslots.get(10));
            SchoolSchedule fresh = new SchoolSchedule(timeslots, List.of(), List.of(),
                    List.of(), List.of(), List.of(g, otherX, otherY), List.of(), List.of(),
                    List.of(g1, g2, u0, u1, u2, u3, b3, b4), List.of());
            // prime the caches under the closed-slot rule (the SLIDERS only:
            // u0 sits AT slot 0, and the grandfather always re-adds the own slot)
            for (Lesson m : List.of(u1, u2, u3)) {
                assertTrue(!m.getAvailableTimeslots().contains(timeslots.get(0)),
                        "cache primed: Thursday slot 0 excluded while the rule is live");
            }
            // ...then withdraw it: fresh now legal, cache still stale
            SchoolSchedule.setGroupCalendarRulesStatic(List.of());
            EmptyDayFinisher.Result refused = EmptyDayFinisher.finish(fresh,
                    (lesson, ts) -> lesson.timeslot = ts);
            assertEquals(0, refused.movesApplied(),
                    "the slide pays the range membership: the extraction is refused (found: " + refused + ")");
        } finally {
            SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        }
    }

    /** Review F1 regression (the roomed form): a need-2 target whose first hour
     *  lands via ROOM REASSIGNMENT and whose second hour has no yielding donor
     *  must roll back BOTH axes leaving the live room index consistent. Fixture:
     *  the Thursday donor (4h, floor 3) yields exactly ONE hour to the floor-form;
     *  the extracted lesson carries ROOM-A, which the cross-group occupant holds
     *  at both Friday landing slots (forcing the reassignment to ROOM-B). Pre-fix,
     *  the undo restored the room BEFORE the de-index: ROOM-A's mark at the
     *  landing slot was erased (the occupant's!) and ROOM-B stayed phantom-occupied
     *  - a second pass would unlock a phantom move and double-book. The
     *  idempotency assert catches exactly that signature. */
    @Test
    void rollbackOfARoomReassigningExtractionKeepsTheRoomIndexConsistent() {
        System.setProperty(EmptyDayFinisher.H33_FLAG, "true");
        com.school.planner.domain.Room roomA = new com.school.planner.domain.Room("ROOM-A", 20, java.util.List.of());
        roomA.id = 100L; // the live indexes key by room.id: null ids collapse every room into one
        com.school.planner.domain.Room roomB = new com.school.planner.domain.Room("ROOM-B", 20, java.util.List.of());
        roomB.id = 101L;
        java.util.List<com.school.planner.domain.Room> rooms = java.util.List.of(roomA, roomB);
        java.util.List<com.school.planner.domain.Teacher> teachers = java.util.List.of(
                teacher(20L, "DONOR"), teacher(21L, "BLOCKER"));
        StudentGroup g = g14();
        StudentGroup other = new StudentGroup("G06", 20);
        other.id = 77L;
        java.util.List<Timeslot> timeslots = new ArrayList<>();
        for (LocalDate d : List.of(H33_THU, H33_FRI)) {
            for (java.time.LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
                Timeslot ts = new Timeslot();
                ts.id = (long) (timeslots.size() + 1);
                ts.startDateTime = LocalDateTime.of(d, start);
                timeslots.add(ts);
            }
        }
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quota(g, H33_THU, 4), quota(g, H33_FRI, 2)));
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(rooms);
        SchoolSchedule.setDayFloorsStatic(java.util.Map.of(g.id, 3));
        List<Lesson> out = new ArrayList<>();
        // Friday target: ONE hour at THU-index 8 (slot 0), teacher BLOCKER
        Lesson seed = lesson(50L, teachers.get(1), g, timeslots.get(8));
        seed.room = roomA;
        out.add(seed);
        // Thursday donor: FOUR hours (slots 0-3), teacher DONOR, ROOM-A each
        for (int i = 0; i < 4; i++) {
            Lesson d = lesson(51L + i, teachers.get(0), g, timeslots.get(i));
            d.room = roomA;
            out.add(d);
        }
        // cross-group ROOM-A occupant at the Friday slots 1,2 (the forcer)
        Lesson o1 = lesson(60L, teachers.get(1), other, timeslots.get(9));
        o1.room = roomA;
        out.add(o1);
        Lesson o2 = lesson(61L, teachers.get(1), other, timeslots.get(10));
        o2.room = roomA;
        out.add(o2);
        SchoolSchedule banco = new SchoolSchedule(timeslots, rooms, List.of(),
                List.of(), List.of(), java.util.List.of(g, other), teachers, List.of(),
                out, List.of());

        EmptyDayFinisher.Result r1 = EmptyDayFinisher.finish(banco,
                (lesson, ts) -> lesson.timeslot = ts);
        EmptyDayFinisher.Result r2 = EmptyDayFinisher.finish(banco,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(0, r2.movesApplied(),
                "idempotent second pass: no phantom occupancy unlocked a move (r1=" + r1 + ")");
        assertEquals(StudentDayCompaction.countConflicts(banco), 0, "no H1/H3 conflict");
    }

    /** The TASK-678 ratification form: unset = armed, only the literal "false"
     *  disarms (the standard opt-OUT shape of every ratified flag). */
    @Test
    void h33DefaultsToArmedSinceThe0410Ratification() {
        System.clearProperty(EmptyDayFinisher.H33_FLAG);
        assertTrue(EmptyDayFinisher.h33Enabled(), "unset property leaves the arm ON");
        System.setProperty(EmptyDayFinisher.H33_FLAG, "false");
        assertFalse(EmptyDayFinisher.h33Enabled(), "only the explicit literal false disarms");
        System.clearProperty(EmptyDayFinisher.H33_FLAG);
    }

    /** v0.1 (26/08, the flaw the reflection registered): a same-teacher block on the
     *  donor (the M15 two-hour shape) must slide into the slot the extraction just
     *  vacated. Pre-fix the slide check ran BEFORE the extraction left the H1 index,
     *  so the block was falsely self-blocked. */
    @Test
    void sameTeacherBlockSlidesIntoTheVacatedSlot() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quota(TARGET_DAY, 1), quota(DONOR_DAY, 1)));
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalDate d : List.of(TARGET_DAY, DONOR_DAY)) {
            for (java.time.LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
                Timeslot ts = new Timeslot();
                ts.id = (long) (timeslots.size() + 1);
                ts.startDateTime = LocalDateTime.of(d, start);
                timeslots.add(ts);
            }
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        StudentGroup g = group();
        List<Lesson> out = new ArrayList<>();
        // donor: slot0 first teacher (stays), slot1+slot2 the SAME teacher (the block)
        out.add(lesson(20L, teacher(20L, "FIRST"), g, timeslots.get(8)));
        out.add(lesson(21L, teacher(21L, "BLOCK"), g, timeslots.get(9)));
        out.add(lesson(22L, teacher(21L, "BLOCK"), g, timeslots.get(10)));
        SchoolSchedule sameBlock = new SchoolSchedule(timeslots, List.<Room>of(), List.of(),
                List.of(), List.of(), List.of(g), List.of(), List.of(), out, List.of());

        EmptyDayFinisher.Result r = EmptyDayFinisher.finish(sameBlock,
                (lesson, ts) -> lesson.timeslot = ts);
        System.out.println("EDF-sameBlock: " + r.movesApplied() + " moves, " + r.cellsClosed());
        assertEquals(1, r.cellsClosed(), "the same-teacher block must not self-block the slide");
        assertEquals(0, r.conflictsAdded());
        List<Integer> donor = slotsOf(sameBlock, g, DONOR_DAY);
        assertEquals(List.of(0, 1), donor, "donor keeps first + the slid block hour, contiguous");
        assertEquals(List.of(0), slotsOf(sameBlock, g, TARGET_DAY));
    }

    private static List<Integer> slotsOf(SchoolSchedule schedule, StudentGroup g, LocalDate date) {
        List<Integer> out = new ArrayList<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot != null && l.timeslot.getDate().equals(date)
                    && l.getStudentGroup() != null && l.getStudentGroup().id.equals(g.id)) {
                out.add(TimeslotSequence.slotIndexOf(l.timeslot));
            }
        }
        out.sort(null);
        return out;
    }

    private List<Integer> slotsAt(StudentGroup g, LocalDate date) {
        List<Integer> out = new ArrayList<>();
        for (Lesson l : lessons) {
            if (l.timeslot != null && l.timeslot.getDate().equals(date)
                    && l.getStudentGroup() != null && l.getStudentGroup().id.equals(g.id)) {
                out.add(TimeslotSequence.slotIndexOf(l.timeslot));
            }
        }
        out.sort(null);
        return out;
    }

    private static DailyActivityQuota quota(LocalDate date, int hours) {
        return quota(group(), date, hours);
    }

    private static DailyActivityQuota quota(StudentGroup g, LocalDate date, int hours) {
        DailyActivityQuota q = new DailyActivityQuota();
        q.studentGroup = g;
        q.date = date;
        q.aulaHours = hours;
        q.labHours = 0;
        q.stageHours = 0;
        return q;
    }

    // ─── v0.2 (27/08, anello 2): the cross-group swap-compound banco ──
    // G's empty declared day D1 (quota 1) is unfillable by v0.1 alone: the only
    // extractable donor hour (T at D2 slot 1) is H1-blocked at D1 slot 0 by X,
    // a lesson of ANOTHER group H taught by the SAME teacher T. The compound
    // evicts X to an H day with quota headroom (D3) and lands the donor hour.
    private static final LocalDate V2_D3_DAY = LocalDate.of(2027, 5, 13);

    private static StudentGroup groupHRef;

    private static StudentGroup groupH() {
        if (groupHRef == null) {
            groupHRef = new StudentGroup("H", 20);
            groupHRef.id = 2L;
        }
        return groupHRef;
    }

    private static StudentGroup groupH2Ref;

    private static StudentGroup groupH2() {
        if (groupH2Ref == null) {
            groupH2Ref = new StudentGroup("H2", 20);
            groupH2Ref.id = 3L;
        }
        return groupH2Ref;
    }

    /** G: D1 empty (quota 1), D2 quota 1 holding pinned T5@0 + donor T@1 (surplus 1).
     *  H: D1 quota 1 holding X(T)@0 + T6@1 (surplus 1); D3 quota {@code d3Quota}
     *  holding T7@0 + T8@1. All fixture teachers carry explicit ids (the H1 live
     *  index keys on teacher id). */
    private static SchoolSchedule buildV2Banco(int d3Quota) {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quota(TARGET_DAY, 1), quota(DONOR_DAY, 1),
                quota(groupH(), TARGET_DAY, 1),
                quota(groupH(), V2_D3_DAY, d3Quota)));
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalDate d : List.of(TARGET_DAY, DONOR_DAY, V2_D3_DAY)) {
            for (java.time.LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
                Timeslot ts = new Timeslot();
                ts.id = (long) (timeslots.size() + 1);
                ts.startDateTime = LocalDateTime.of(d, start);
                timeslots.add(ts);
            }
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        StudentGroup g = group();
        StudentGroup h = groupH();
        List<Lesson> out = new ArrayList<>();
        // G D2: the pinned first hour is NOT extractable; T's hour at slot 1 is the donor
        Lesson pinnedFirst = lesson(30L, teacher(30L, "T5"), g, timeslots.get(8));
        pinnedFirst.pinned = true;
        out.add(pinnedFirst);
        out.add(lesson(31L, teacher(31L, "T"), g, timeslots.get(9)));
        // H D1: X (the H1 blocker, same teacher T) at slot 0, T6 above it
        out.add(lesson(32L, teacher(31L, "T"), h, timeslots.get(0)));
        out.add(lesson(33L, teacher(33L, "T6"), h, timeslots.get(1)));
        // H D3: two hours, quota d3Quota (headroom when d3Quota >= 3)
        out.add(lesson(34L, teacher(34L, "T7"), h, timeslots.get(16)));
        out.add(lesson(35L, teacher(35L, "T8"), h, timeslots.get(17)));
        return new SchoolSchedule(timeslots, List.<Room>of(), List.of(), List.of(), List.of(),
                List.of(g, h), List.of(), List.of(), out, List.of());
    }

    @Test
    void v2SwapClosesTheCellWhenTheOnlyBlockerIsCrossGroup() {
        // TASK-612 ratification: relay/h32 pinned OFF for v2-swap isolation uniformity
        System.setProperty(EmptyDayFinisher.RELAY_FLAG, "false");
        System.setProperty(EmptyDayFinisher.H32_FLAG, "false");
        System.setProperty(EmptyDayFinisher.V2_SWAP_FLAG, "true");
        SchoolSchedule banco = buildV2Banco(3);
        EmptyDayFinisher.Result r = EmptyDayFinisher.finish(banco,
                (lesson, ts) -> lesson.timeslot = ts);
        System.out.println("EDF-v2: " + r.movesApplied() + " moves, " + r.cellsClosed()
                + " cells, " + r.swapCompounds() + " swap-compound(s), conflicts " + r.conflictsAdded());
        assertEquals(0, r.conflictsAdded(), "no H1/H3 conflict may be added");
        assertEquals(1, r.cellsClosed(), "the compound must close the cross-group-blocked cell");
        assertEquals(1, r.swapCompounds());
        assertEquals(3, r.movesApplied(), "eviction + H-day slide + donor extraction");
        assertEquals(List.of(0), slotsOf(banco, group(), TARGET_DAY), "G fills its first slot");
        assertEquals(List.of(0), slotsOf(banco, group(), DONOR_DAY), "G donor keeps the pinned hour");
        assertEquals(List.of(0), slotsOf(banco, groupH(), TARGET_DAY), "H's day slides shut to quota");
        assertEquals(List.of(0, 1, 2), slotsOf(banco, groupH(), V2_D3_DAY),
                "H's landing day absorbs X at last+1 (headroom consumed, no surplus)");
        assertEquals(StudentDayCompaction.countConflicts(banco), 0, "grid stays conflict-free");
    }

    @Test
    void v2SwapFlagOffLeavesTheCellOpen() {
        // Endgame-bundle ratification (16/09): v2-swap is DEFAULT ON, so the
        // is-off contract pins it (and its siblings) OFF explicitly; with relay/h32
        // also off, v0.1 alone stays stuck on the H1 block, nothing moves.
        System.setProperty(EmptyDayFinisher.RELAY_FLAG, "false");
        System.setProperty(EmptyDayFinisher.H32_FLAG, "false");
        System.setProperty(EmptyDayFinisher.V2_SWAP_FLAG, "false");
        try {
            SchoolSchedule banco = buildV2Banco(3);
            EmptyDayFinisher.Result r = EmptyDayFinisher.finish(banco,
                    (lesson, ts) -> lesson.timeslot = ts);
            assertEquals(0, r.cellsClosed());
            assertEquals(0, r.movesApplied());
            assertEquals(0, r.swapCompounds());
            assertTrue(slotsOf(banco, group(), TARGET_DAY).isEmpty());
        } finally {
            System.clearProperty(EmptyDayFinisher.RELAY_FLAG);
            System.clearProperty(EmptyDayFinisher.H32_FLAG);
            System.clearProperty(EmptyDayFinisher.V2_SWAP_FLAG);
        }
    }

    @Test
    void v2SwapRefusesWhenTheBlockerHasNoLandingHeadroom() {
        // H's only other day is AT quota (d3Quota=2): an eviction would create M33
        // surplus, so the compound refuses and the grid is untouched
        System.setProperty(EmptyDayFinisher.V2_SWAP_FLAG, "true");
        SchoolSchedule banco = buildV2Banco(2);
        EmptyDayFinisher.Result r = EmptyDayFinisher.finish(banco,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(0, r.cellsClosed());
        assertEquals(0, r.movesApplied(), "no eviction may be applied without a headroom landing");
        assertEquals(List.of(0, 1), slotsOf(banco, groupH(), TARGET_DAY), "blocker day untouched");
        assertEquals(List.of(0, 1), slotsOf(banco, groupH(), V2_D3_DAY), "landing day untouched");
    }

    @Test
    void v2SwapRollsBackTheEvictionWhenTheRetryFails() {
        // the eviction is legal, but the donor's retry still fails (its ROOM is held
        // at the target slot by a third group's lesson): the compound must undo the
        // eviction and leave the grid byte-identical to the entry state.
        // TASK-612 ratification: relay/h32 pinned OFF (v2-swap isolation, see above).
        System.setProperty(EmptyDayFinisher.RELAY_FLAG, "false");
        System.setProperty(EmptyDayFinisher.H32_FLAG, "false");
        System.setProperty(EmptyDayFinisher.V2_SWAP_FLAG, "true");
        // TASK-678 review R2-F3: the retry's ROOM rejection must stay FINAL here -
        // publish an empty room registry so the reassignment scan cannot rescue a
        // move even if another test in the same JVM leaked real rooms into the
        // static (order independence).
        SchoolSchedule.setAllRoomsStatic(List.of());
        try {
        SchoolSchedule banco = buildV2Banco(3);
        Room shared = new Room("R", 30, List.of());
        shared.id = 40L;
        Lesson donorWithRoom = null;
        for (Lesson l : banco.getLessons()) {
            if (l.id == 31L) {
                donorWithRoom = l;
            }
        }
        donorWithRoom.room = shared;
        Lesson roomHolder = lesson(41L, teacher(41L, "T9"), groupH2(), banco.getTimeslots().get(0));
        roomHolder.room = shared;
        banco.getLessons().add(roomHolder);
        EmptyDayFinisher.Result r = EmptyDayFinisher.finish(banco,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(0, r.cellsClosed(), "the retry fails, the cell stays open");
        assertEquals(0, r.movesApplied(), "the failed eviction must be fully rolled back");
        assertEquals(0, r.swapCompounds());
        assertEquals(List.of(0, 1), slotsOf(banco, groupH(), TARGET_DAY),
                "X back at slot 0 and T6 back at slot 1 after the rollback");
        assertEquals(List.of(0, 1), slotsOf(banco, groupH(), V2_D3_DAY), "landing day untouched");
        assertTrue(slotsOf(banco, group(), TARGET_DAY).isEmpty());
        assertEquals(StudentDayCompaction.countConflicts(banco), 0);
        } finally {
            System.clearProperty(EmptyDayFinisher.RELAY_FLAG);
            System.clearProperty(EmptyDayFinisher.H32_FLAG);
            System.clearProperty(EmptyDayFinisher.V2_SWAP_FLAG);
        }
    }

    private static StudentGroup groupRef;

    private static StudentGroup group() {
        if (groupRef == null) {
            groupRef = new StudentGroup("G", 20);
            groupRef.id = 1L;
        }
        return groupRef;
    }

    /** DONOR1: quota 3, hours at slots 0(TA first, stays), 1(TB middle), 2(TC middle),
     *  3(TD tail, teacher BUSY at the target's slots via the OTHER-group blockers).
     *  Ids are explicit: the H1 live index keys on teacher id, and the entity default
     *  is null for every fixture teacher (which would make everyone "busy"). */
    private static final Teacher TA = teacher(1L, "TA");
    private static final Teacher TB = teacher(2L, "TB");
    private static final Teacher TC = teacher(3L, "TC");
    private static final Teacher TD = teacher(4L, "TD");
    /** DONOR2: quota 1, hours at slots 0(TE first), 1(TF middle), 2(TG tail busy). */
    private static final Teacher TE = teacher(5L, "TE");
    private static final Teacher TF = teacher(6L, "TF");
    private static final Teacher TG = teacher(7L, "TG");

    private static Teacher teacher(long id, String name) {
        Teacher t = new Teacher(name);
        t.id = id;
        return t;
    }

    private SchoolSchedule build() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quota(TARGET_DAY, 2), quota(DONOR_DAY, 3), quota(DONOR2_DAY, 1)));
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalDate d : List.of(TARGET_DAY, DONOR_DAY, DONOR2_DAY)) {
            for (java.time.LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
                Timeslot ts = new Timeslot();
                ts.id = (long) (timeslots.size() + 1);
                ts.startDateTime = LocalDateTime.of(d, start);
                timeslots.add(ts);
            }
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        StudentGroup g = group();
        List<Lesson> out = new ArrayList<>();
        long id = 1;
        for (int slot = 0; slot < 4; slot++) { // DONOR1: slots 0..3
            Teacher t = switch (slot) {
                case 0 -> TA;
                case 1 -> TB;
                case 2 -> TC;
                default -> TD;
            };
            out.add(lesson(id++, t, g, timeslots.get(8 + slot)));
        }
        for (int slot = 0; slot < 3; slot++) { // DONOR2: slots 0..2
            Teacher t = switch (slot) {
                case 0 -> TE;
                case 1 -> TF;
                default -> TG;
            };
            out.add(lesson(id++, t, g, timeslots.get(16 + slot)));
        }
        // OTHER-group blockers: TD busy at TARGET slots 0 and 1, TG busy at TARGET slot 1
        // (distinct groups per slot: two blockers of the same group on one slot would be
        // an H3 conflict in the fixture itself)
        StudentGroup other = new StudentGroup("OTHER", 20);
        other.id = 2L;
        StudentGroup other2 = new StudentGroup("OTHER2", 20);
        other2.id = 3L;
        out.add(lesson(id++, TD, other, timeslots.get(0)));
        out.add(lesson(id++, TD, other, timeslots.get(1)));
        out.add(lesson(id++, TG, other2, timeslots.get(1)));
        return new SchoolSchedule(timeslots, List.<Room>of(), List.of(), List.of(), List.of(),
                List.of(g, other, other2), List.of(), List.of(), out, List.of());
    }

    private static Lesson lesson(long id, Teacher t, StudentGroup g, Timeslot ts) {
        Lesson l = new Lesson();
        l.id = id;
        l.course = new Course("S" + id, t, g, 10);
        l.timeslot = ts;
        return l;
    }
}
