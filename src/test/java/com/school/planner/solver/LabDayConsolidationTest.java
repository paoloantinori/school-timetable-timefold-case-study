package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.TestDataBuilder;
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-463: unit tests for {@link LabDayConsolidation}, the cross-group teacher-day coordinator
 * that closes a lab-day H17 gap by RELOCATING the cross-group donor that saturates the anchor
 * teacher at the gap slot, then IMPORTING an anchor-course lesson into the freed slot.
 *
 * <p>The fixture models the verified root cause (run1 lessons.txt): G13's Friday lab-day has a
 * mid-day gap because the anchor teacher (T10) is saturated at the gap slot by ANOTHER group's
 * lesson (FC Tecnologia Meccanica). The golden solves it by coordinating T10's day across
 * groups; this pass does the same deterministically, post-smarcatura.
 *
 * <p>Every case asserts the invariants whatever the outcome: the pass never adds a conflict and
 * never leaves MORE penalized hole slots than it found (improve-or-noop), and the donor group is
 * never de-anchored (anti-shell-game, mirror of AnchorRepair).
 */
class LabDayConsolidationTest {

    private static final LocalDate FRI = LocalDate.of(2026, 9, 18);
    private static final LocalDate MON = LocalDate.of(2026, 9, 21);
    private static final int SLOTS = 5;

    private long nextId = 1;
    private StudentGroup g13;
    private StudentGroup fc;
    private Teacher T10;
    private Teacher t15;
    private Course lmCourse;       // T10 / Lavorazioni meccaniche / G13 (lab) - the anchor course
    private Course tecmecCourse;   // T10 / Tecnologia Meccanica / FC (non-lab) - the donor course
    private final List<Timeslot> timeslots = new ArrayList<>();
    private final List<Lesson> all = new ArrayList<>();

    @BeforeEach
    void setUp() {
        g13 = TestDataBuilder.group(nextId++, "G13", 8);
        fc = TestDataBuilder.group(nextId++, "G04", 15);
        T10 = TestDataBuilder.teacher(nextId++, "T10");
        t15 = TestDataBuilder.teacher(nextId++, "T15");
        lmCourse = TestDataBuilder.course(nextId++, "Lavorazioni meccaniche", T10, g13, true);
        tecmecCourse = TestDataBuilder.course(nextId++, "Tecnologia Meccanica", T10, fc, false);

        for (LocalDate date : List.of(FRI, MON)) {
            for (int slot = 0; slot < SLOTS; slot++) {
                timeslots.add(TestDataBuilder.timeslot(nextId++, date, 8 + slot, 0, 9 + slot, 0));
            }
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setTeacherAvailabilitiesStatic(List.of());
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        SchoolSchedule.setWeeklyTemplateStatic(Map.of(), Map.of());
        SchoolSchedule.setPromotedLessonIdsStatic(Set.of());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("planner.construction.lab-day-consolidation.enabled");
        SchoolSchedule.clearStaticState();
    }

    /**
     * The happy path (flag ON): G13 Friday lab-day is T10 LM at slots 0,1,2,4 with a gap at
     * slot 3. T10 is saturated at slot 3 by FC's Tecnologia Meccanica (the donor), which is
     * NOT its day's first lesson (FC also has one at slot 0). A spare T10/LM hour sits on
     * Monday slot 0. The pass must: relocate the FC donor off Friday slot 3, import the spare
     * LM hour into the gap, and leave G13 Friday as a contiguous T10 block 0-4 with 0 added
     * conflicts and the gap closed.
     */
    @Test
    void closesLabDayGapByRelocatingDonorAndImportingAnchor() {
        System.setProperty("planner.construction.lab-day-consolidation.enabled", "true");
        Lesson G13Fri0 = placed(lmCourse, FRI, 0);
        Lesson G13Fri1 = placed(lmCourse, FRI, 1);
        Lesson G13Fri2 = placed(lmCourse, FRI, 2);
        Lesson G13Fri4 = placed(lmCourse, FRI, 4);
        Lesson FCFri0 = placed(tecmecCourse, FRI, 0);   // makes the donor NOT FC's first lesson
        Lesson donor = placed(tecmecCourse, FRI, 3);     // saturates T10 at the gap slot
        Lesson spare = placed(lmCourse, MON, 0);         // anchor-course hour to import

        LabDayConsolidation.Result r = repair();

        assertEquals(1, r.labGapsBefore(), "G13 Friday slot 3 is the one penalized lab-day gap");
        assertEquals(0, r.labGapsAfter(), "the gap was closed");
        assertEquals(1, r.gapsClosed());
        assertEquals(1, r.donorsRelocated(), "the FC donor was relocated off the gap slot");
        assertEquals(0, r.donorsSkippedShellGame(), "the donor was not FC's anchor, so no shell-game skip");
        assertEquals(0, r.conflictsAdded());
        assertSame(timeslot(FRI, 3), spare.timeslot, "the spare LM hour landed in the gap slot");
        assertNotSlot(donor, FRI, 3, "the donor no longer sits on the gap slot");
        // G13 Friday is now a contiguous T10 block 0-4 (slots 0,1,2,3,4 all occupied).
        assertTrue(contiguous(g13, FRI, 0, 4), "G13 Friday is a contiguous block after consolidation");
    }

    /**
     * Flag OPTED OUT: the pass is byte-identical inert. No lesson moves, no gap closed, zero
     * Result. The default flipped to ON at the 2026-08-01 ratification, so the opt-out path is
     * exercised by setting the property to "false" explicitly (clear would now resolve to ON).
     */
    @Test
    void isByteIdenticalNoOpWhenFlagOff() {
        System.setProperty("planner.construction.lab-day-consolidation.enabled", "false");
        Lesson G13Fri0 = placed(lmCourse, FRI, 0);
        Lesson G13Fri1 = placed(lmCourse, FRI, 1);
        Lesson G13Fri2 = placed(lmCourse, FRI, 2);
        Lesson G13Fri4 = placed(lmCourse, FRI, 4);
        Lesson FCFri0 = placed(tecmecCourse, FRI, 0);
        Lesson donor = placed(tecmecCourse, FRI, 3);
        Lesson spare = placed(lmCourse, MON, 0);
        Timeslot spareTs = spare.timeslot;
        Timeslot donorTs = donor.timeslot;

        LabDayConsolidation.Result r = repair();

        assertEquals(0, r.labGapsBefore(), "flag off: the pass does not even tally gaps");
        assertEquals(0, r.labGapsAfter());
        assertEquals(0, r.gapsClosed());
        assertEquals(0, r.donorsRelocated());
        assertEquals(0, r.conflictsAdded());
        assertSame(spareTs, spare.timeslot, "flag off: nothing moved");
        assertSame(donorTs, donor.timeslot);
    }

    /**
     * Anti-shell-game / no-legal-move: the donor IS FC's first (and only other) lesson of the
     * day, so relocating it would de-anchor FC's day (M18). The pass must refuse the move and
     * leave the grid byte-identical rather than trade an G13 gap for an FC late-start.
     */
    @Test
    void leavesGridUntouchedWhenDonorRelocationWouldDeAnchorDonorGroup() {
        System.setProperty("planner.construction.lab-day-consolidation.enabled", "true");
        Lesson G13Fri0 = placed(lmCourse, FRI, 0);
        Lesson G13Fri1 = placed(lmCourse, FRI, 1);
        Lesson G13Fri2 = placed(lmCourse, FRI, 2);
        Lesson G13Fri4 = placed(lmCourse, FRI, 4);
        // The donor is FC's FIRST Friday lesson (slot 3) with another at slot 4: relocating it
        // would shift FC's day start from slot 3 to slot 4 (M18 offset 3 -> 4). keepsDayAnchored
        // refuses that, so no relocation, no import, and the G13 gap survives byte-identical.
        Lesson donor = placed(tecmecCourse, FRI, 3);
        Lesson FCFri4 = placed(tecmecCourse, FRI, 4);
        Lesson spare = placed(lmCourse, MON, 0);
        Timeslot donorTs = donor.timeslot;
        Timeslot spareTs = spare.timeslot;

        LabDayConsolidation.Result r = repair();

        assertTrue(r.labGapsBefore() >= 1, "the G13 gap is still on the table");
        assertEquals(r.labGapsBefore(), r.labGapsAfter(), "improve-or-noop: the gap survives, not displaced");
        assertEquals(0, r.gapsClosed());
        assertEquals(0, r.donorsRelocated());
        assertEquals(0, r.conflictsAdded());
        assertSame(donorTs, donor.timeslot, "the donor was not moved");
        assertSame(spareTs, spare.timeslot, "no import happened");
    }

    // ─── fixture helpers ───────────────────────────────────────────────────

    private LabDayConsolidation.Result repair() {
        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(g13, fc), List.of(T10, t15),
                List.of(lmCourse, tecmecCourse), all, List.of());
        LabDayConsolidation.Result r = LabDayConsolidation.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);
        assertEquals(0, r.conflictsAdded(), "the pass is conflict-free by construction");
        assertTrue(r.labGapsAfter() <= r.labGapsBefore(), "improve-or-noop on the lab-day gap count");
        return r;
    }

    private Lesson placed(Course course, LocalDate date, int slot) {
        Lesson l = TestDataBuilder.lesson(nextId++, course, timeslot(date, slot), null, false);
        all.add(l);
        return l;
    }

    private Timeslot timeslot(LocalDate date, int slot) {
        int idx = (date.equals(FRI) ? 0 : SLOTS) + slot;
        return timeslots.get(idx);
    }

    private void assertNotSlot(Lesson lesson, LocalDate date, int slot, String message) {
        if (lesson.timeslot != null && lesson.timeslot.getDate().equals(date)
                && TimeslotSequence.slotIndexOf(lesson.timeslot) == slot) {
            throw new AssertionError(message + " (lesson " + lesson.id + " still on " + date + " slot " + slot + ")");
        }
    }

    private boolean contiguous(StudentGroup group, LocalDate date, int fromSlot, int toSlot) {
        boolean[] occupied = new boolean[toSlot + 1];
        for (Lesson l : all) {
            if (l.timeslot == null || !group.equals(l.getStudentGroup())
                    || !date.equals(l.timeslot.getDate())) continue;
            int s = TimeslotSequence.slotIndexOf(l.timeslot);
            if (s >= fromSlot && s <= toSlot) occupied[s] = true;
        }
        for (int s = fromSlot; s <= toSlot; s++) if (!occupied[s]) return false;
        return true;
    }
}
