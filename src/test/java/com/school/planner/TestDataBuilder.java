package com.school.planner;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.LabAssistant;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * Shared test data builder utilities.
 * <p>
 * Provides helper methods for creating test domain objects with minimal boilerplate.
 * Extracted from {@link com.school.planner.solver.SchoolScheduleConstraintProviderTest}
 * to avoid duplication across test classes.
 */
public class TestDataBuilder {

    // Default values for test data
    private static final int DEFAULT_COURSE_HOURS = 66;

    private TestDataBuilder() {
        // Utility class
    }

    // ─── Timeslot ────────────────────────────────────────────────────────

    public static Timeslot timeslot(long id, LocalDate date, int startHour, int startMin, int endHour, int endMin) {
        Timeslot ts = new Timeslot();
        ts.id = id;
        ts.startDateTime = LocalDateTime.of(date, LocalTime.of(startHour, startMin));
        ts.endDateTime = LocalDateTime.of(date, LocalTime.of(endHour, endMin));
        return ts;
    }

    public static Timeslot timeslot(long id, LocalDateTime start) {
        Timeslot ts = new Timeslot();
        ts.id = id;
        ts.startDateTime = start;
        ts.endDateTime = start.plusHours(1);
        return ts;
    }

    // ─── Room ─────────────────────────────────────────────────────────────

    public static Room room(long id, String name, int capacity) {
        Room r = new Room();
        r.id = id;
        r.name = name;
        r.capacity = capacity;
        r.tags = List.of();
        return r;
    }

    public static Room room(long id, String name, int capacity, List<String> tags) {
        Room r = new Room();
        r.id = id;
        r.name = name;
        r.capacity = capacity;
        r.tags = tags != null ? tags : List.of();
        return r;
    }

    // ─── Teacher ──────────────────────────────────────────────────────────

    public static Teacher teacher(long id, String name) {
        Teacher t = new Teacher();
        t.id = id;
        t.name = name;
        return t;
    }

    public static Teacher teacher(long id, String name, boolean pseudo) {
        Teacher t = teacher(id, name);
        t.pseudo = pseudo;
        return t;
    }

    // TASK-348: per-teacher H7 override (e.g. T01's 8h/day exception)
    public static Teacher teacherWithMaxDailyHours(long id, String name, int maxDailyHours) {
        Teacher t = teacher(id, name);
        t.maxDailyHours = maxDailyHours;
        return t;
    }

    // TASK-368: per-teacher M24 override (e.g. T11's "one 8am day/week")
    public static Teacher teacherWithMaxEarlyStartDaysPerWeek(long id, String name, int maxEarlyStartDaysPerWeek) {
        Teacher t = teacher(id, name);
        t.maxEarlyStartDaysPerWeek = maxEarlyStartDaysPerWeek;
        return t;
    }

    // TASK-368: per-teacher M25 override (e.g. T05's "one free day/week")
    public static Teacher teacherWithRequiredFreeDaysPerWeek(long id, String name, int requiredFreeDaysPerWeek) {
        Teacher t = teacher(id, name);
        t.requiredFreeDaysPerWeek = requiredFreeDaysPerWeek;
        return t;
    }

    // ─── Student Group ─────────────────────────────────────────────────────

    public static StudentGroup group(long id, String name, int size) {
        StudentGroup g = new StudentGroup();
        g.id = id;
        g.name = name;
        g.size = size;
        return g;
    }

    public static StudentGroup group(long id, String name, int size, Integer maxHoursPerDay) {
        StudentGroup g = group(id, name, size);
        g.maxHoursPerDay = maxHoursPerDay;
        return g;
    }

    // ─── Course ───────────────────────────────────────────────────────────

    public static Course course(long id, String subject, Teacher teacher, StudentGroup group) {
        Course c = new Course();
        c.id = id;
        c.subject = subject;
        c.teacher = teacher;
        c.studentGroup = group;
        c.totalHours = DEFAULT_COURSE_HOURS;
        c.isLab = false;
        c.requiredRoomTags = List.of();
        return c;
    }

    public static Course course(long id, String subject, Teacher teacher, StudentGroup group, boolean isLab) {
        Course c = course(id, subject, teacher, group);
        c.isLab = isLab;
        return c;
    }

    // ─── Lesson ───────────────────────────────────────────────────────────

    public static Lesson lesson(long id, Course course, Timeslot ts, Room room) {
        Lesson l = new Lesson();
        l.id = id;
        l.course = course;
        l.timeslot = ts;
        l.room = room;
        l.pinned = false;
        return l;
    }

    public static Lesson lesson(long id, Course course, Timeslot ts, Room room, boolean pinned) {
        Lesson l = lesson(id, course, ts, room);
        l.pinned = pinned;
        return l;
    }

    // ─── School Holiday ─────────────────────────────────────────────────────

    public static SchoolHoliday holiday(long id, LocalDate date) {
        SchoolHoliday h = new SchoolHoliday();
        h.id = id;
        h.date = date;
        h.description = "Test holiday";
        return h;
    }

    // ─── Lab Assistant ──────────────────────────────────────────────────────

    public static LabAssistant labAssistant(long id, Teacher teacher, String roomTag) {
        LabAssistant la = new LabAssistant();
        la.id = id;
        la.teacher = teacher;
        la.roomTag = roomTag;
        return la;
    }

    // ─── Group Calendar Rule (TASK-342) ─────────────────────────────────────

    public static GroupCalendarRule groupCalendarRule(StudentGroup group, DayOfWeek dayOfWeek,
                                                       int minSlot, int maxSlot) {
        return new GroupCalendarRule(group, dayOfWeek, minSlot, maxSlot);
    }

    public static GroupCalendarRule groupCalendarRule(StudentGroup group, DayOfWeek dayOfWeek,
                                                       int minSlot, int maxSlot,
                                                       LocalDate effectiveFrom, LocalDate effectiveTo) {
        return new GroupCalendarRule(group, dayOfWeek, minSlot, maxSlot, effectiveFrom, effectiveTo);
    }

    // ─── Daily Activity Quota (TASK-381 "L1-bis") ────────────────────────────

    public static DailyActivityQuota dailyActivityQuota(StudentGroup group, LocalDate date,
                                                          int aulaHours, int labHours, int stageHours) {
        return new DailyActivityQuota(group, date, aulaHours, labHours, stageHours);
    }

    // TASK-384/385: class-days-sourced stage-day fact (FCQuota=false, e.g. a
    // G07/G10/G12 stage day from DataGenerator.generateExpectedDailyHours).
    public static DailyActivityQuota dailyActivityQuota(StudentGroup group, LocalDate date,
                                                          int aulaHours, int labHours, int stageHours,
                                                          boolean FCQuota) {
        return new DailyActivityQuota(group, date, aulaHours, labHours, stageHours, FCQuota);
    }
}
