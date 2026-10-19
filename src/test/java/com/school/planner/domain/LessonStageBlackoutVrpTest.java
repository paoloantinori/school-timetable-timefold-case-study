package com.school.planner.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.school.planner.TestDataBuilder.course;
import static com.school.planner.TestDataBuilder.group;
import static com.school.planner.TestDataBuilder.lesson;
import static com.school.planner.TestDataBuilder.teacher;
import static com.school.planner.TestDataBuilder.timeslot;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the H4.1 VRP stage-blackout filter's pseudo-teacher
 * exemption in {@link Lesson#getAvailableTimeslots()} (TASK-381 code review):
 * the H4 CONSTRAINT has always excluded pseudo-teacher lessons
 * ({@code nonProfLessonStagePairs} - a Stage lesson trivially belongs INSIDE
 * its own group's stage window), but the VRP filter lacked the matching
 * exemption. Unreachable before TASK-381 "stage on the grid" (Stage lessons
 * only ever existed pre-pinned, and pinned lessons skip the VRP entirely) -
 * with unpinned Stage stubs now seeded at startup, a missing exemption would
 * make a Stage stub's own stage window structurally unassignable for the
 * three StagePattern groups (G07, G11, G12; 885h combined).
 *
 * <p>Statics setup/teardown mirrors {@code SchoolScheduleConstraintProviderTest}'s
 * per-test idiom (set what the test needs, reset to empty - not null - after).</p>
 */
class LessonStageBlackoutVrpTest {

    private static final LocalDate IN_WINDOW = LocalDate.of(2027, 3, 24); // a Wednesday inside the window
    private static final LocalDate OUT_WINDOW = LocalDate.of(2027, 5, 31); // a Monday outside it

    @AfterEach
    void clearStaticState() {
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.of(), Set.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
    }

    private StudentGroup setupBlackout() {
        StudentGroup g = group(1L, "G07", 20);
        StagePattern sp = new StagePattern();
        sp.studentGroup = g;
        sp.startDate = LocalDate.of(2027, 3, 23);
        sp.endDate = LocalDate.of(2027, 3, 26);
        sp.dayOfWeekMask = Set.of(java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY);
        SchoolSchedule.setAllTimeslotsStatic(List.of(
                timeslot(1L, IN_WINDOW, 8, 0, 9, 0),
                timeslot(2L, OUT_WINDOW, 8, 0, 9, 0)));
        SchoolSchedule.setStageBlackoutDatesStatic(List.of(sp), Set.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        return g;
    }

    @Test
    void pseudoStageLesson_keepsItsOwnStageWindowDates() {
        StudentGroup g = setupBlackout();
        Teacher stage = teacher(10L, "Stage", true);
        Course stageCourse = course(100L, "Stage", stage, g);
        stageCourse.professionalizzanti = false;
        Lesson stageLesson = lesson(1000L, stageCourse, null, null, false);

        List<LocalDate> dates = stageLesson.getAvailableTimeslots().stream()
                .map(Timeslot::getDate).toList();
        assertTrue(dates.contains(IN_WINDOW),
                "a pseudo Stage lesson must be placeable INSIDE its own group's stage window");
        assertTrue(dates.contains(OUT_WINDOW));
    }

    @Test
    void realTeacherNonProfLesson_stillExcludedFromStageWindow() {
        StudentGroup g = setupBlackout();
        Teacher real = teacher(11L, "T25", false);
        Course scienze = course(101L, "Scienze", real, g);
        scienze.professionalizzanti = false;
        Lesson lesson = lesson(1001L, scienze, null, null, false);

        List<LocalDate> dates = lesson.getAvailableTimeslots().stream()
                .map(Timeslot::getDate).toList();
        assertFalse(dates.contains(IN_WINDOW),
                "an ordinary non-prof lesson must still be blacked out of the stage window (H4.1 VRP)");
        assertTrue(dates.contains(OUT_WINDOW));
    }
}
