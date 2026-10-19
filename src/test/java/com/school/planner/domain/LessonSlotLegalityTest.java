package com.school.planner.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-578: single-slot legality check, the O(1) companion of
 * {@code getAvailableTimeslotsFresh()}. The full-range Fresh rebuild costs ~220us
 * (measured, production statics); the guards that only need "may THIS lesson sit
 * at THIS slot" pay that per call, which the efficiency review priced at 1-4
 * minutes per staged attempt across the repair chain. This test pins the SEMANTIC
 * EQUIVALENCE of the per-slot check with the range membership on each filter axis
 * that matters to the crash class (the stale-window one that crashed the
 * 2026-09-02 cycle twice).
 */
class LessonSlotLegalityTest {

    private static final LocalDate WED = LocalDate.of(2026, 9, 16);

    @AfterEach
    void clean() {
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
    }

    private long nextId = 1;

    private Timeslot slot(int hour) {
        Timeslot ts = new Timeslot(
                java.time.LocalDateTime.of(WED, LocalTime.of(hour, 0)),
                java.time.LocalDateTime.of(WED, LocalTime.of(hour + 1, 0)));
        ts.id = nextId++;   // Timeslot.equals is id-based: every slot needs one
        return ts;
    }

    private Lesson lessonAt(Timeslot ts) {
        Teacher t = new Teacher("T1");
        t.id = 1L;
        StudentGroup g = new StudentGroup("G1", 6);
        g.id = 1L;
        g.startDate = LocalDate.of(2026, 1, 1);
        Course c = new Course("S1", t, g, 100);
        c.id = 1L;
        Lesson l = new Lesson();
        l.id = 1L;
        l.course = c;
        l.pinned = false;
        l.timeslot = ts;
        return l;
    }

    private void publishStatics(Timeslot... slots) {
        SchoolSchedule.setAllTimeslotsStatic(List.of(slots));
    }

    @Test
    void legalSlot_passes() {
        Timeslot eight = slot(8);
        Timeslot nine = slot(9);   // same object as published: Timeslot.equals is id-based
        publishStatics(eight, nine);
        Lesson l = lessonAt(eight);
        assertTrue(l.isSlotLegalFresh(eight));
        assertTrue(l.isSlotLegalFresh(nine));
    }

    @Test
    void slotOutsidePublishedStatics_isIllegal() {
        Timeslot eight = slot(8);
        publishStatics(eight);
        Lesson l = lessonAt(eight);
        assertFalse(l.isSlotLegalFresh(slot(9)), "a slot not in the static grid is never legal");
    }

    @Test
    void currentSlot_isAlwaysLegal_theGrandfather() {
        // the range getter re-adds the current slot when filters remove it: the
        // per-slot check must grandfather the same way. DISCRIMINATING setup: the
        // lesson sits OUTSIDE the published window, so only the grandfather can
        // accept its own cell (a plain filter pass would reject it)
        Timeslot ten = slot(10);
        publishStatics(slot(8), slot(9), ten);
        Lesson l = lessonAt(ten);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(new GroupCalendarRule(
                l.course.studentGroup, WED.getDayOfWeek(), 0, 1)));   // window [0..1]
        assertTrue(l.isSlotLegalFresh(ten), "own slot stays legal (grandfather beats the closed window)");
        assertTrue(l.getAvailableTimeslotsFresh().contains(ten), "cross-check: the FRESH range grandfathers too");
    }

    @Test
    void slotOutsideAFreshWindow_isIllegal_evenWhenTheCacheIsStale() {
        // THE crash class: cache populated under permissive statics, window
        // published after, guard must see the LIVE window (the stale cached
        // range would accept slot 2)
        Timeslot eight = slot(8);
        Timeslot nine = slot(9);
        Timeslot ten = slot(10);
        publishStatics(eight, nine, ten, slot(11));
        Lesson l = lessonAt(eight);
        l.getAvailableTimeslots();          // populate the cache permissively
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(new GroupCalendarRule(
                l.course.studentGroup, WED.getDayOfWeek(), 0, 1)));   // closes after slot 1
        assertFalse(l.isSlotLegalFresh(ten), "fresh window must reject slot 2 (stale cache would accept)");
        assertTrue(l.isSlotLegalFresh(nine), "fresh window allows slot 1");
    }

    @Test
    void pinnedLesson_onlyItsOwnSlot() {
        Timeslot eight = slot(8);
        Timeslot nine = slot(9);   // the published object: pinning, not grid membership, must reject it
        publishStatics(eight, nine);
        Lesson l = lessonAt(eight);
        l.pinned = true;
        assertTrue(l.isSlotLegalFresh(eight));
        assertFalse(l.isSlotLegalFresh(nine));
    }

    /**
     * The equivalence pin (review I3): for every slot of the published grid, the
     * O(1) per-slot check and the FRESH range membership must agree exactly. This
     * is the property the migration ships; the holiday axis is loaded here as the
     * representative second filter (the other axes share the transcription shape).
     */
    @Test
    void perSlotCheck_agreesWithFreshRange_onEveryGridSlot() {
        Timeslot eight = slot(8);
        Timeslot nine = slot(9);
        Timeslot ten = slot(10);
        Timeslot eleven = slot(11);
        publishStatics(eight, nine, ten, eleven);
        Lesson l = lessonAt(eight);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(new GroupCalendarRule(
                l.course.studentGroup, WED.getDayOfWeek(), 0, 1)));   // window [0..1]
        SchoolSchedule.setSchoolHolidaysStatic(List.of(new SchoolHoliday(WED, "test holiday"))); // closes the whole day
        List<Timeslot> fresh = l.getAvailableTimeslotsFresh();
        for (Timeslot ts : List.of(eight, nine, ten, eleven)) {
            boolean perSlot = l.isSlotLegalFresh(ts);
            boolean inRange = fresh.contains(ts);
            assertTrue(perSlot == inRange,
                    "divergence at slot " + ts.id + ": perSlot=" + perSlot + " freshContains=" + inRange);
        }
        // with the whole day a holiday only the grandfather keeps the own cell
        assertTrue(fresh.contains(eight), "own slot grandfathered in the range");
        assertFalse(fresh.contains(nine), "holiday + window close slot 1");
    }
}
