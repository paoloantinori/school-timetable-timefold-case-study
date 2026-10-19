package com.school.planner.service;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable assignment of teachers to their rotation days.
 *
 * <p>Replaces the pair-based RotationCandidate with a teacher-centric model.
 * Since all groups of a teacher share the same day, this captures the
 * actual decision variable: which day each teacher gets.</p>
 *
 * <p>This dramatically reduces the search space. For acconciatura with 3 teachers,
 * we have 3-15 combinations (3 teachers × 1-5 valid days each) instead of
 * 390,625 pair-based combinations.</p>
 *
 * <p>Pattern: Follows {@link RotationCandidate} - immutable record with Comparable.</p>
 */
public record TeacherDayAssignment(
    /** Map from teacherId to their assigned day of week */
    Map<Long, DayOfWeek> teacherToDay,

    /** Sequential assignment number for ordering */
    int assignmentNumber
) implements Comparable<TeacherDayAssignment> {

    /**
     * Compact constructor that creates a defensive copy of the map.
     */
    public TeacherDayAssignment {
        teacherToDay = teacherToDay == null ? Map.of() : Map.copyOf(teacherToDay);
    }

    /**
     * Compare by assignment number for deterministic ordering.
     */
    @Override
    public int compareTo(TeacherDayAssignment other) {
        return Integer.compare(this.assignmentNumber, other.assignmentNumber);
    }

    /**
     * Get the assigned day for a specific teacher.
     *
     * @param teacherId the teacher's ID
     * @return the assigned DayOfWeek, or null if not found
     */
    public DayOfWeek getDayForTeacher(Long teacherId) {
        return teacherToDay.get(teacherId);
    }

    /**
     * @return the number of teacher assignments in this assignment
     */
    public int size() {
        return teacherToDay.size();
    }

    /**
     * Check if this assignment includes a specific teacher.
     *
     * @param teacherId the teacher's ID
     * @return true if the teacher has an assignment
     */
    public boolean hasTeacher(Long teacherId) {
        return teacherToDay.containsKey(teacherId);
    }

    /**
     * Create a new assignment with an additional teacher-day mapping.
     *
     * @param teacherId the teacher to add
     * @param day the day to assign
     * @return new TeacherDayAssignment with the additional mapping
     */
    public TeacherDayAssignment withTeacher(Long teacherId, DayOfWeek day) {
        Map<Long, DayOfWeek> newMap = new LinkedHashMap<>(teacherToDay);
        newMap.put(teacherId, day);
        return new TeacherDayAssignment(newMap, assignmentNumber);
    }

    /**
     * Create a new assignment without a specific teacher.
     *
     * @param teacherId the teacher to remove
     * @return new TeacherDayAssignment without the teacher
     */
    public TeacherDayAssignment withoutTeacher(Long teacherId) {
        Map<Long, DayOfWeek> newMap = new LinkedHashMap<>(teacherToDay);
        newMap.remove(teacherId);
        return new TeacherDayAssignment(newMap, assignmentNumber);
    }

    /**
     * Get all teacher IDs in this assignment.
     *
     * @return unmodifiable set of teacher IDs
     */
    public java.util.Set<Long> getTeacherIds() {
        return Collections.unmodifiableSet(teacherToDay.keySet());
    }

    /**
     * Create an empty assignment.
     *
     * @return empty TeacherDayAssignment
     */
    public static TeacherDayAssignment empty() {
        return new TeacherDayAssignment(Map.of(), 0);
    }

    /**
     * Create an empty assignment with a specific number.
     *
     * @param assignmentNumber the assignment number
     * @return empty TeacherDayAssignment
     */
    public static TeacherDayAssignment empty(int assignmentNumber) {
        return new TeacherDayAssignment(Map.of(), assignmentNumber);
    }

    @Override
    public String toString() {
        return String.format("TeacherDayAssignment[#%d, teachers=%s]",
            assignmentNumber, teacherToDay);
    }
}
