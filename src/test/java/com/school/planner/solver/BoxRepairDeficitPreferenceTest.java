package com.school.planner.solver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TASK-406 follow-up: BoxRepair must PREFER a gate-passing cell on a day with an
 * expected-hours deficit over the chronologically-first passing cell, falling back
 * to chronological-first when no deficit day is legal. Pure in-memory JUnit (no
 * Quarkus/DB): all inputs flow through the SchoolSchedule statics, hand-assigned ids.
 */
class BoxRepairDeficitPreferenceTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 14);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 15);

    private StudentGroup group;
    private Teacher teacher;
    private Course course;
    private Timeslot monTs;
    private Timeslot tueTs;

    @BeforeEach
    void setUp() {
        group = new StudentGroup("TEST", 20);
        group.id = 1L;
        teacher = new Teacher("Prof");
        teacher.id = 1L;
        course = new Course("Materia", teacher, group, 10);
        course.id = 1L;
        monTs = ts(2L, MON);
        tueTs = ts(3L, TUE);
        SchoolSchedule.setAllTimeslotsStatic(List.of(monTs, tueTs));
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setTeacherAvailabilitiesStatic(List.of());
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(List.of(monTs, tueTs)));
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
    }

    @AfterEach
    void tearDown() {
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        SchoolSchedule.setTimeslotSequenceStatic(null);
    }

    private static Timeslot ts(long id, LocalDate date) {
        Timeslot t = new Timeslot(LocalDateTime.of(date, LocalTime.of(8, 0)),
                LocalDateTime.of(date, LocalTime.of(9, 0)));
        t.id = id;
        return t;
    }

    private Lesson boxedLesson() {
        Lesson l = new Lesson(course);
        l.id = 10L;
        return l;
    }

    private SchoolSchedule schedule(Lesson... lessons) {
        return new SchoolSchedule(List.of(monTs, tueTs), List.of(), List.of(), List.of(),
                List.of(), List.of(group), List.of(teacher), List.of(course), List.of(lessons), List.of());
    }

    @Test
    void prefersTheDeficitDayOverTheChronologicallyFirstCell() {
        // Monday has NO expected hours (no fact), Tuesday expects 4h and is empty:
        // both cells pass every gate, but Tuesday carries the deficit.
        DailyActivityQuota tueExpected = new DailyActivityQuota(group, TUE, 4, 0, 0, false);
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(tueExpected));
        Lesson boxed = boxedLesson();

        BoxRepair.Result r = BoxRepair.repair(schedule(boxed),
                (l, t) -> l.timeslot = t, l -> l.room = null);

        assertEquals(1, r.placed());
        assertEquals(tueTs, boxed.timeslot,
                "the deficit day (Tue, expected 4h/placed 0) must beat the chronologically-first Monday cell");
    }

    @Test
    void fallsBackToChronologicalFirstWhenNoDeficitDayExists() {
        // No expected-hours facts at all: no deficit anywhere -> chronological first (Mon).
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        Lesson boxed = boxedLesson();

        BoxRepair.Result r = BoxRepair.repair(schedule(boxed),
                (l, t) -> l.timeslot = t, l -> l.room = null);

        assertEquals(1, r.placed());
        assertEquals(monTs, boxed.timeslot, "no deficit info -> the pre-existing chronological-first choice");
    }
}
