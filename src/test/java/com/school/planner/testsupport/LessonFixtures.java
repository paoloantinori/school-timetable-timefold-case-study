package com.school.planner.testsupport;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * Shared JPA-persisted fixture for tests needing a single fully-scheduled {@link Lesson}
 * (Teacher + StudentGroup + Room + Course + Timeslot + Lesson) — the minimal dataset for
 * which {@code ValidationSnapshotRepository.hasScheduledLessons()} is true and
 * {@code ScheduleService.analyzeScore()} has something concrete to score.
 *
 * <p>Extracted (TASK-354 code review, {@code /simplify} pass) from three near-identical
 * copies in {@code ValidationResourceTest}, {@code ValidationServiceDebounceTest}, and
 * {@code ValidationServiceBaselineTest} — public rather than package-private since those
 * three tests span both the {@code rest} and {@code service} test packages.
 */
public final class LessonFixtures {

    private LessonFixtures() {}

    /**
     * Persists a Teacher/StudentGroup/Room/Course/Timeslot and a single pinned, scheduled
     * Lesson on {@code date} at 08:00-09:00. {@code label} distinguishes each caller's rows
     * (e.g. {@code "Watchdog"}, {@code "Debounce Test"}) so parallel fixtures don't collide
     * on entity names.
     *
     * @return the persisted Lesson, whose {@code timeslot.getDate()} equals {@code date}
     */
    public static Lesson persistScheduledLesson(String label, LocalDate date) {
        Room room = new Room();
        room.name = label + " Room";
        room.capacity = 25;
        room.persist();

        Lesson lesson = new Lesson(persistCourse(label));
        lesson.timeslot = persistTimeslot(date);
        lesson.room = room;
        lesson.pinned = true;
        lesson.persist();

        return lesson;
    }

    /**
     * Persists an unassigned (no timeslot), unpinned Lesson - the ordinary
     * "not yet scheduled" state.
     */
    public static Lesson persistUnassignedLesson(String label) {
        Lesson lesson = new Lesson(persistCourse(label));
        lesson.persist();
        return lesson;
    }

    /**
     * Persists an assigned (timeslot set, no room), unpinned Lesson on
     * 2025-10-06 08:00-09:00.
     */
    public static Lesson persistAssignedLesson(String label) {
        Lesson lesson = new Lesson(persistCourse(label));
        lesson.timeslot = persistTimeslot(LocalDate.of(2025, 10, 6));
        lesson.persist();
        return lesson;
    }

    /**
     * Persists a Lesson pinned with a null timeslot - the invalid Timefold state
     * BUG A guards against ({@code LessonResource.togglePin} 409s new attempts to
     * create this state; {@code ScheduleService} heals any pre-existing ones on
     * load). Only reachable via direct entity manipulation like this fixture,
     * simulating a row corrupted before the guard existed.
     */
    public static Lesson persistCorruptedPinnedLesson(String label) {
        Lesson lesson = new Lesson(persistCourse(label));
        lesson.pinned = true;
        lesson.persist();
        return lesson;
    }

    /** Persists the fixtures' canonical one-hour slot: {@code date} 08:00-09:00. */
    private static Timeslot persistTimeslot(LocalDate date) {
        LocalDateTime start = date.atTime(8, 0);
        Timeslot timeslot = new Timeslot(start, start.plusHours(1));
        timeslot.persist();
        return timeslot;
    }

    private static Course persistCourse(String label) {
        Teacher teacher = new Teacher();
        teacher.name = label + " Teacher";
        teacher.persist();

        StudentGroup group = new StudentGroup();
        group.name = label + " Group";
        group.size = 20;
        group.persist();

        Course course = new Course();
        course.teacher = teacher;
        course.studentGroup = group;
        course.subject = label + " Subject";
        course.totalHours = 1;
        course.persist();
        return course;
    }
}
