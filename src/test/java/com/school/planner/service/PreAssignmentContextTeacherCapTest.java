package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-502: the H7 bookkeeping the greedy's teacherCap gate reads. Pure in-memory
 * (track/untrack touch only the context maps), no Quarkus needed. The symmetric
 * decrement matters as much as the increment: backtracking relies on it.
 */
class PreAssignmentContextTeacherCapTest {

    private static long seq = 1;

    private static Timeslot ts(LocalDate date, LocalTime time) {
        Timeslot t = new Timeslot();
        t.id = seq++;
        t.startDateTime = LocalDateTime.of(date, time);
        return t;
    }

    @Test
    void tracksAndSymmetricallyUntracksTeacherDailyHours() {
        PreAssignmentContext ctx = new PreAssignmentContext();
        Teacher t = new Teacher();
        t.id = 101L;
        LocalDate mon = LocalDate.of(2026, 9, 14);
        LocalDate tue = LocalDate.of(2026, 9, 15);

        Lesson a = lesson(t, mon, LocalTime.of(8, 0));
        Lesson b = lesson(t, mon, LocalTime.of(9, 0));
        Lesson c = lesson(t, tue, LocalTime.of(8, 0));
        for (Lesson l : List.of(a, b, c)) {
            ctx.trackLesson(l);
        }
        assertEquals(2, ctx.teacherDailyHours(t, mon), "two hours on Monday");
        assertEquals(1, ctx.teacherDailyHours(t, tue), "one hour on Tuesday");
        assertEquals(0, ctx.teacherDailyHours(t, LocalDate.of(2026, 9, 16)), "nothing on Wednesday");

        // backtracking a Monday placement must free the hour (round-trip symmetry)
        ctx.untrackLesson(b);
        assertEquals(1, ctx.teacherDailyHours(t, mon), "untrack decrements");
        assertEquals(0, ctx.teacherDailyHours(new Teacher(), mon), "unknown teacher reads zero");
    }

    @Test
    void nullInputsReadZero() {
        PreAssignmentContext ctx = new PreAssignmentContext();
        Teacher t = new Teacher();
        t.id = 7L;
        assertEquals(0, ctx.teacherDailyHours(null, LocalDate.of(2026, 9, 14)));
        assertEquals(0, ctx.teacherDailyHours(t, null));
        assertEquals(0, ctx.teacherDailyHours(t, LocalDate.of(2026, 9, 14)));
    }

    private static Lesson lesson(Teacher t, LocalDate date, LocalTime time) {
        Course course = new Course();
        course.id = seq++;
        course.subject = "Barberia";
        course.teacher = t;
        StudentGroup g = new StudentGroup();
        g.id = seq++;
        g.name = "G02";
        course.studentGroup = g;
        Lesson l = new Lesson(course);
        l.id = seq++;
        l.timeslot = ts(date, time);
        l.room = new Room();
        return l;
    }
}
