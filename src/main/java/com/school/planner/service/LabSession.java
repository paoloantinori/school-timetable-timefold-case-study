package com.school.planner.service;

import java.util.ArrayList;
import java.util.List;

import com.school.planner.domain.Course;
import com.school.planner.domain.LabRotation;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * Represents a group of consecutive lab lessons that form a single lab session.
 * Used by pre-assignment to group lab lessons for assignment.
 *
 * All lessons in a session share:
 * - Same course
 * - Same room
 * - Consecutive timeslots (startSlotIndex 0 to N-1)
 */
public class LabSession {
    public final Course course;
    public final List<Lesson> lessons = new ArrayList<>();
    public Room room;
    public Timeslot startTimeslot;
    public int totalHours;
    public LabRotation labRotation;

    public LabSession(Course course) {
        this.course = course;
    }

    public void addLesson(Lesson lesson) {
        if (lesson.course == null || !lesson.course.equals(course)) {
            throw new IllegalArgumentException("Lesson must belong to this LabSession's course");
        }
        lessons.add(lesson);
        if (lessons.size() == 1) {
            startTimeslot = lesson.timeslot;
            room = lesson.room;
        }
    }

    public List<Lesson> getLessons() {
        return lessons;
    }

    /**
     * Get the first lesson in the lab session (for pinning).
     */
    public Lesson getFirstLesson() {
        return lessons.isEmpty() ? null : lessons.get(0);
    }

    /**
     * Get all timeslots occupied by this session.
     */
    public List<Timeslot> getOccupiedTimeslots() {
        List<Timeslot> slots = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (lesson.timeslot != null) {
                slots.add(lesson.timeslot);
            }
        }
        return slots;
    }

    /**
     * Check if this session has been assigned (has timeslot and room).
     */
    public boolean isAssigned() {
        return startTimeslot != null && room != null;
    }

    /**
     * Get the teacher for this session (from the course).
     */
    public Teacher getTeacher() {
        return course != null ? course.teacher : null;
    }

    /**
     * Get the student group for this session (from the course).
     */
    public StudentGroup getStudentGroup() {
        return course != null ? course.studentGroup : null;
    }

    /**
     * Get the subject for this session (from the course).
     */
    public String getSubject() {
        return course != null ? course.subject : null;
    }
}
