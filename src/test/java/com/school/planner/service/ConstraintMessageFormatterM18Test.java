package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;
import com.school.planner.domain.constraints.DayStartOffsetJustification;

/**
 * TASK-376: ConstraintMessageFormatter's M18 case (renamed from the stale H18
 * "5-hour groups must start from slot 0" label, TASK-405 widened the
 * constraint to every group-day). Guards against the rename regressing:
 * the legacy "H18" key must no longer resolve, and "M18" must, via both the
 * DayStartOffsetJustification branch (LIVE since the train review wired
 * {@code .justifyWith} into the M18 constraint) and the Lesson fallback branch.
 */
class ConstraintMessageFormatterM18Test {

    private Lesson makeLesson() {
        StudentGroup group = new StudentGroup();
        group.name = "G09";
        group.id = 1L;

        Course course = new Course();
        course.studentGroup = group;
        course.subject = "Disegno";

        Timeslot ts = new Timeslot(
                LocalDateTime.of(LocalDate.of(2026, 3, 15), java.time.LocalTime.of(9, 0)),
                LocalDateTime.of(LocalDate.of(2026, 3, 15), java.time.LocalTime.of(10, 0)));

        Lesson lesson = new Lesson();
        lesson.id = 1L;
        lesson.course = course;
        lesson.timeslot = ts;
        return lesson;
    }

    @Test
    void m18_legacyH18Key_noLongerResolves() {
        String message = ConstraintMessageFormatter.formatViolation(
                "H18", List.of(makeLesson()), Locale.ENGLISH);

        assertNull(message, "H18 was renamed to M18 (TASK-405/376); the stale key must not resolve");
    }

    @Test
    void m18_lessonFallback_producesAMessage() {
        String message = ConstraintMessageFormatter.formatViolation(
                "M18", List.of(makeLesson()), Locale.ENGLISH);

        assertNotNull(message);
        assertTrue(message.contains("G09"), "message should reference the student group");
    }

    @Test
    void m18_justificationBranch_usesOffsetSlotsDirectly() {
        // Offset 7 appears nowhere in the date/group ids, so the assertion can
        // only pass if the OFFSET itself is rendered (train review: the old
        // offset=2 assertion also matched the "2" in the date 2026-03-15).
        DayStartOffsetJustification justification =
                new DayStartOffsetJustification(1L, LocalDate.of(2026, 3, 15), 7);

        String message = ConstraintMessageFormatter.formatViolation(
                "M18", List.of(justification), Locale.ENGLISH);

        assertNotNull(message);
        assertTrue(message.contains("7"), "message should report the offset slots from the justification");
    }
}
