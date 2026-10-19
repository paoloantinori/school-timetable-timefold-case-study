package com.school.planner.solver.neighborhood;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TASK-589: the harvest stream's unit contract. The enumeration semantics are
 * the shared HarvestNeighborhood's (pinned separately by the pass's own tests:
 * fonte unica); here we pin the FACTORY's behavior: flag off -> inert empty
 * list (the union child costs nothing), flag on -> the gated pairs as moves.
 */
class HarvestMoveListFactoryTest {

    @AfterEach
    void clean() {
        System.clearProperty(HarvestMoveListFactory.FLAG);
        com.school.planner.domain.SchoolSchedule.setDailyActivityQuotasStatic(List.of());
    }

    @Test
    void flagOff_emptyList() {
        System.clearProperty(HarvestMoveListFactory.FLAG);
        var factory = new HarvestMoveListFactory();
        assertTrue(factory.createMoveList(fixture()).isEmpty(),
                "flag off: the union child must be inert");
    }

    @Test
    void flagOn_yieldsGatedMoves() {
        System.setProperty(HarvestMoveListFactory.FLAG, "true");
        var factory = new HarvestMoveListFactory();
        List<HarvestMove> moves = factory.createMoveList(fixture());
        assertFalse(moves.isEmpty(), "the gated donor pair must be offered to the LS");
        for (HarvestMove m : moves) {
            assertEquals("HarvestChange", m.describe(),
                    "the benchmarker's per-type statistics ride on this label");
            assertNotNull(m.getPlanningEntities());
        }
        // the fixture's only viable pair: the slot-1 donor -> the target's first-open slot 0
        LocalDate targetDate = LocalDate.of(2026, 6, 9);
        assertTrue(moves.stream().anyMatch(m ->
                        m.getPlanningValues().iterator().next() instanceof Timeslot ts
                        && ts.getDate().equals(targetDate)),
                "expected a pair landing on the empty target day");
    }

    /** One group: donor day over quota (2 lessons, quota 1), target day empty quota 1. */
    static SchoolSchedule fixture() {
        Teacher t1 = new Teacher("StreamTeacher");
        t1.id = 1L;
        StudentGroup g = new StudentGroup("StreamGroup", 6);
        g.id = 1L;
        g.startDate = LocalDate.of(2026, 1, 1);
        Course course = new Course("StreamSubject", t1, g, 100);
        course.id = 1L;
        LocalDate donorDate = LocalDate.of(2026, 9, 16);
        LocalDate targetDate = LocalDate.of(2026, 6, 9);
        List<Timeslot> timeslots = new java.util.ArrayList<>();
        int id = 1;
        for (LocalDate d : List.of(donorDate, targetDate)) {
            for (int h = 8; h < 16; h++) {
                var ts = new Timeslot(java.time.LocalDateTime.of(d, LocalTime.of(h, 0)),
                        java.time.LocalDateTime.of(d, LocalTime.of(h + 1, 0)));
                ts.id = (long) id++;
                timeslots.add(ts);
            }
        }
        com.school.planner.domain.DailyActivityQuota donorQuota =
                new com.school.planner.domain.DailyActivityQuota(g, donorDate, 1, 0, 0);
        com.school.planner.domain.DailyActivityQuota targetQuota =
                new com.school.planner.domain.DailyActivityQuota(g, targetDate, 1, 0, 0);
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(donorQuota, targetQuota));
        List<Lesson> lessons = new java.util.ArrayList<>();
        lessons.add(lesson(course, timeslots.get(0)));   // donor day slot 0
        lessons.add(lesson(course, timeslots.get(1)));   // donor day slot 1: the donor
        return new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(g), List.of(t1), List.of(course), lessons, List.of());
    }

    private static Lesson lesson(Course course, Timeslot ts) {
        Lesson l = new Lesson();
        l.id = (long) System.identityHashCode(l);
        l.course = course;
        l.timeslot = ts;
        return l;
    }
}
