package com.school.planner.solver;

import static com.school.planner.TestDataBuilder.course;
import static com.school.planner.TestDataBuilder.group;
import static com.school.planner.TestDataBuilder.lesson;
import static com.school.planner.TestDataBuilder.teacher;
import static com.school.planner.TestDataBuilder.timeslot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.TimeslotSequence;


/**
 * TASK-499: the template-proximity grading of {@link NearbyTemplateMoveFilter} and its
 * wiring. Pure in-memory JUnit (the same fixture shape MarginalDeficitCostTest uses):
 * the grades are a pure function of the published template cells + the destination
 * timeslot, so the table is testable without a solver. The probabilistic grade-3 branch
 * is tested only through its bounds (keep-probability 0 never passes grade 3, 1 always
 * does); the interior is ThreadLocalRandom by design (the filter's javadoc).
 */
class NearbyTemplateMoveFilterTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 14);   // Monday

    private long nextId = 1;

    @BeforeEach
    void setUp() {
        List<Timeslot> ts = new ArrayList<>();
        for (LocalDate d : List.of(MON, MON.plusDays(1))) {
            for (int s = 0; s < TimeslotSequence.SLOTS_PER_DAY; s++) {
                ts.add(timeslot(nextId++,
                        LocalDateTime.of(d, TimeslotSequence.SLOT_START_TIMES[s])));
            }
        }
        SchoolSchedule.setAllTimeslotsStatic(ts);
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(ts));
        // Course 1 templated on (Mon,0) and (Tue,4): both grade-1 and grade-2 reachable.
        // Course 2 untemplated: the filter must stand aside (everything grade 0).
        SchoolSchedule.setWeeklyTemplateStatic(
                Map.of(1L, Set.of(SchoolSchedule.encodeTemplateCell(DayOfWeek.MONDAY, 0),
                        SchoolSchedule.encodeTemplateCell(DayOfWeek.TUESDAY, 4))),
                Map.of());
    }

    @AfterEach
    void tearDown() {
        SchoolSchedule.clearStaticState();
        System.clearProperty("planner.ls.nearby-template-filter.keep-probability");
    }

    @Test
    void gradeTable() {
        assertEquals(0, NearbyTemplateMoveFilter.proximityGrade(1L, at(DayOfWeek.MONDAY, 0)),
                "on a template cell: grade 0");
        assertEquals(0, NearbyTemplateMoveFilter.proximityGrade(2L, at(DayOfWeek.MONDAY, 6)),
                "untemplated course: the filter stands aside (grade 0, everything passes)");
        assertEquals(1, NearbyTemplateMoveFilter.proximityGrade(1L, at(DayOfWeek.MONDAY, 6)),
                "same weekday as a template cell, different slot: grade 1");
        assertEquals(2, NearbyTemplateMoveFilter.proximityGrade(1L, at(DayOfWeek.WEDNESDAY, 4)),
                "same slot as a template cell, different weekday: grade 2");
        assertEquals(3, NearbyTemplateMoveFilter.proximityGrade(1L, at(DayOfWeek.FRIDAY, 7)),
                "far from every template cell: grade 3 (the filtered one)");
    }


    @Test
    void gradeThreeBounds() {
        StudentGroup g = group(nextId++, "TEST", 20);
        Teacher t = teacher(nextId++, "Prof");
        var c = course(1L, "Materia", t, g);
        Lesson farLesson = lesson(nextId++, c, null, null);
        Lesson onCellLesson = lesson(nextId++, c, null, null);
        System.setProperty("planner.ls.nearby-template-filter.keep-probability", "0.0");
        NearbyTemplateMoveFilter closed = new NearbyTemplateMoveFilter();
        assertFalse(closed.accept(null, new StubMove(farLesson, at(DayOfWeek.FRIDAY, 7))),
                "keep-probability 0: grade 3 never passes");
        assertTrue(closed.accept(null, new StubMove(onCellLesson, at(DayOfWeek.MONDAY, 0))),
                "grades 0-2 always pass");
        System.setProperty("planner.ls.nearby-template-filter.keep-probability", "1.0");
        NearbyTemplateMoveFilter open = new NearbyTemplateMoveFilter();
        assertTrue(open.accept(null, new StubMove(farLesson, at(DayOfWeek.FRIDAY, 7))),
                "keep-probability 1: the escape valve is fully open");
    }

    private static Timeslot at(DayOfWeek day, int slot) {
        LocalDate d = MON.with(day);
        // The canonical slot START times (slot 6 begins 14:30, after the lunch break):
        // deriving the hour arithmetically (8 + slot) would miss the break and read -1.
        return timeslot(-1, LocalDateTime.of(d, TimeslotSequence.SLOT_START_TIMES[slot]));
    }

    /** Minimal Move stand-in exposing the planning pair the filter reads. */
    private record StubMove(Lesson lesson, Timeslot to)
            implements ai.timefold.solver.core.preview.api.move.Move<SchoolSchedule> {
        @Override
        public java.util.SequencedCollection<Object> getPlanningEntities() {
            return List.of(lesson);
        }

        @Override
        public java.util.SequencedCollection<Object> getPlanningValues() {
            return List.of(to);
        }

        @Override
        public void execute(ai.timefold.solver.core.preview.api.move.MutableSolutionView<SchoolSchedule> view) {
            throw new UnsupportedOperationException("stub");
        }
    }
}
