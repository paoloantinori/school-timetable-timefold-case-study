package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-537 v1: plain unit test on a synthetic schedule (no Quarkus boot).
 * Pins the DIRECTION semantics: a same-day pull-up closes the H17 hole and
 * never adds H1; the quasi budgets are untouched by same-day moves.
 */
class FinalClosingCampaignTest {

    private static Timeslot ts(LocalDate d, int hour) {
        return new Timeslot(d.atTime(hour, 0), d.atTime(hour + 1, 0));
    }

    @AfterEach
    void clearGridStatic() {
        SchoolSchedule.setAllTimeslotsStatic(List.of());
    }

    @Test
    void sameDayPullUpClosesHoleWithoutConflicts() {
        LocalDate d = LocalDate.of(2026, 9, 17);
        List<Timeslot> timeslots = new ArrayList<>();
        for (int h = 8; h <= 14; h++) {
            timeslots.add(ts(d, h));
        }
        Teacher t1 = new Teacher("Rossi");
        StudentGroup g = new StudentGroup("G04", 18); g.id = 1L;
        Course c1 = new Course("Scienze", t1);
        c1.studentGroup = g;
        Course c2 = new Course("Matematica", t1);
        c2.studentGroup = g;

        Lesson a = new Lesson(c1); a.timeslot = ts(d, 8);
        // H17 hole: nothing at 9
        Lesson b = new Lesson(c2); b.timeslot = ts(d, 10);

        SchoolSchedule s = new SchoolSchedule();
        s.setTimeslots(timeslots);
        // TASK-614 fixture fix: the cure's isSlotLegalFresh gates read the STATIC
        // timeslot grid (empty in these fixtures -> every slot rejected -> FCC closed
        // nothing, the 9 suite failures on the laptop stage-1 run). Publish it like
        // every sibling solver test does; the @AfterEach clears it for the shared JVM.
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        List<Lesson> lessons = new ArrayList<>(List.of(a, b));
        s.setLessons(lessons);

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(s, (l, nt) -> l.timeslot = nt);

        assertEquals(1, r.trueH17Closed(), "the 9:00 hole must be closed by pulling the 10:00 lesson up");
        assertEquals(9, b.timeslot.startDateTime.getHour(), "lesson b moved to 9:00");
        assertEquals(0, r.quasiH32Paid());
        assertEquals(0, r.quasiH33Paid());
        assertTrue(lessons.stream().noneMatch(l -> l.timeslot == null), "no lesson de-assigned");
    }

    @Test
    void teacherConflictBlocksThePull() {
        LocalDate d = LocalDate.of(2026, 9, 17);
        List<Timeslot> timeslots = new ArrayList<>();
        for (int h = 8; h <= 14; h++) {
            timeslots.add(ts(d, h));
        }
        Teacher t1 = new Teacher("Rossi");
        StudentGroup ga = new StudentGroup("G04", 18);
        StudentGroup gb = new StudentGroup("G08", 17); gb.id = 2L;
        Course other = new Course("Scienze", t1); other.studentGroup = gb;
        Course c1 = new Course("Scienze", t1); c1.studentGroup = ga;
        Course c2 = new Course("Matematica", t1); c2.studentGroup = ga;

        // Rossi already teaches G08 at 9:00: pulling G04's 10:00 up to 9:00 would add H1 (a TRUE): forbidden by direction
        Lesson blocker = new Lesson(other); blocker.timeslot = ts(d, 9);
        Lesson a = new Lesson(c1); a.timeslot = ts(d, 8);
        Lesson b = new Lesson(c2); b.timeslot = ts(d, 10);

        SchoolSchedule s = new SchoolSchedule();
        s.setTimeslots(timeslots);
        // TASK-614 fixture fix: the cure's isSlotLegalFresh gates read the STATIC
        // timeslot grid (empty in these fixtures -> every slot rejected -> FCC closed
        // nothing, the 9 suite failures on the laptop stage-1 run). Publish it like
        // every sibling solver test does; the @AfterEach clears it for the shared JVM.
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        s.setLessons(new ArrayList<>(List.of(blocker, a, b)));

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(s, (l, nt) -> l.timeslot = nt);

        assertEquals(0, r.trueH17Closed(), "the move would pay a TRUE (H1): must be refused");
        assertEquals(10, b.timeslot.startDateTime.getHour(), "lesson b stays at 10:00");
    }
}
