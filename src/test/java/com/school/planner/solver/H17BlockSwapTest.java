package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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

/**
 * TASK-542 iter-8: the BlockerSwap pass - an H17 hole whose tail slide is blocked by the
 * tail teacher teaching ANOTHER GROUP in the hole's slot (the G13-Friday shape: T10
 * with G04 at 11:00) closes by SWAPPING the blocker with a same-day partner lesson of the
 * blocker's group whose teacher is free in the hole slot, then sliding the tail. Day totals
 * never change (no H32/M33): the accounting that made every cross-day cure zero-sum.
 */
class H17BlockSwapTest {

    private static final LocalDate FRI = LocalDate.of(2026, 9, 18);
    private static final int SLOTS = 6;

    private long nextId = 1;
    private StudentGroup g13;
    private StudentGroup fc;
    private Teacher T10;
    private Teacher partnerTeacher;
    private Teacher t15;
    private Course G13Lab;
    private Course FCT10;
    private Course FCPartner;
    private Course G13Other;
    private final List<Timeslot> timeslots = new ArrayList<>();
    private final Map<String, Timeslot> byKey = new HashMap<>();

    @BeforeEach
    void setUp() {
        g13 = TestDataBuilder.group(nextId++, "G13", 20);
        fc = TestDataBuilder.group(nextId++, "G04", 20);
        T10 = TestDataBuilder.teacher(nextId++, "T10");
        partnerTeacher = TestDataBuilder.teacher(nextId++, "Partner");
        G13Lab = TestDataBuilder.course(nextId++, "Lavorazioni", T10, g13);
        FCT10 = TestDataBuilder.course(nextId++, "Altro", T10, fc);
        FCPartner = TestDataBuilder.course(nextId++, "Partneriale", partnerTeacher, fc);
        t15 = TestDataBuilder.teacher(nextId++, "T15");
        G13Other = TestDataBuilder.course(nextId++, "Altra G13", t15, g13);

        for (int slot = 0; slot < SLOTS; slot++) {
            Timeslot ts = TestDataBuilder.timeslot(nextId++, FRI, 8 + slot, 0, 9 + slot, 0);
            timeslots.add(ts);
            byKey.put(FRI + "@" + slot, ts);
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
        SchoolSchedule.clearStaticState();
    }

    /**
     * THE Friday shape: G13 0,1,2,_,4,5 (hole at 3, tail by T10); G04 0,1,2,3(T10),
     * 4(Partner). The pass must swap the blocker (G04 slot 3) with its partner (slot 4),
     * then slide the G13 tail left: both groups end contiguous, day totals untouched.
     */
    @Test
    void swapsTheBlockerAndSlidesTheTail() {
        // the REAL Friday shape: the day's HEAD (0,1,2) is another teacher's (like Cad/
        // T15), only the TAIL (4,5) is the blocked teacher's block
        Lesson G130 = placed(G13Other, 0, false);
        Lesson G131 = placed(G13Other, 1, false);
        Lesson G132 = placed(G13Other, 2, false);
        Lesson G134 = placed(G13Lab, 4, false);
        Lesson G135 = placed(G13Lab, 5, false);
        Lesson FC0 = placed(FCPartner, 0, false);
        Lesson FC1 = placed(FCPartner, 1, false);
        Lesson FC2 = placed(FCPartner, 2, false);
        Lesson blocker = placed(FCT10, 3, false);
        Lesson partner = placed(FCPartner, 4, false);

        H17BlockSwap.Result r = repair(
                G130, G131, G132, G134, G135, FC0, FC1, FC2, blocker, partner);

        assertEquals(1, r.gapSlotsBefore());
        assertEquals(0, r.gapSlotsAfter(), "the hole must close");
        assertEquals(1, r.blockerSwaps());
        assertEquals(0, r.conflictsAdded());
        assertNotSame(byKey.get(FRI + "@3"), blocker.timeslot, "the blocker left the hole slot");
        assertNotSame(partner.timeslot, blocker.timeslot, "blocker and partner swapped cells");
        assertSame(byKey.get(FRI + "@3"), G134.timeslot, "the G13 tail slid into the hole");
        assertSame(byKey.get(FRI + "@4"), G135.timeslot);
    }

    /** No partner whose teacher is free in the hole slot: the pass must be a strict no-op. */
    @Test
    void noopWhenNoFreePartnerExists() {
        // the partner teacher also teaches G13 at slot 3? no - keep it simpler: the blocker's
        // group has NO other lesson that day except the blocker: nothing to swap with
        Lesson G130 = placed(G13Lab, 0, false);
        Lesson G131 = placed(G13Lab, 1, false);
        Lesson G132 = placed(G13Lab, 2, false);
        Lesson G134 = placed(G13Lab, 4, false);
        Lesson blocker = placed(FCT10, 3, false);

        H17BlockSwap.Result r = repair(G130, G131, G132, G134, blocker);

        assertEquals(1, r.gapSlotsBefore());
        assertEquals(1, r.gapSlotsAfter(), "nothing closable: strict no-op");
        assertEquals(0, r.blockerSwaps());
        assertEquals(0, r.conflictsAdded());
    }

    /** The blocker's group is pinned (authoritative): untouchable, strict no-op. */
    @Test
    void ignoresAuthoritativeBlockers() {
        Lesson G130 = placed(G13Lab, 0, false);
        Lesson G132 = placed(G13Lab, 2, false);
        Lesson G134 = placed(G13Lab, 4, false);
        Lesson blocker = placed(FCT10, 3, true); // authoritative pin
        Lesson partner = placed(FCPartner, 4, false);

        H17BlockSwap.Result r = repair(G130, G132, G134, blocker, partner);

        assertTrue(r.gapSlotsAfter() >= r.gapSlotsBefore(), "never worse");
        assertEquals(0, r.blockerSwaps(), "a pinned blocker is never swapped");
    }

    // ---- fixtures ---------------------------------------------------------------

    private Lesson placed(Course course, int slot, boolean pinned) {
        return TestDataBuilder.lesson(nextId++, course, byKey.get(FRI + "@" + slot), null, pinned);
    }

    private H17BlockSwap.Result repair(Lesson... lessons) {
        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(g13, fc), List.of(T10, partnerTeacher, t15),
                List.of(G13Lab, FCT10, FCPartner, G13Other), List.of(lessons), List.of());
        H17BlockSwap.Result r = H17BlockSwap.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);
        assertEquals(0, r.conflictsAdded(), "the pass is conflict-free by construction");
        assertTrue(r.gapSlotsAfter() <= r.gapSlotsBefore(), "improve-or-noop");
        return r;
    }
}
