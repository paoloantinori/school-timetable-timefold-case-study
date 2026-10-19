package com.school.planner.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.school.planner.TestDataBuilder.course;
import static com.school.planner.TestDataBuilder.dailyActivityQuota;
import static com.school.planner.TestDataBuilder.group;
import static com.school.planner.TestDataBuilder.lesson;
import static com.school.planner.TestDataBuilder.teacher;
import static com.school.planner.TestDataBuilder.timeslot;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the Stage confinement VRP filter in {@link
 * Lesson#getAvailableTimeslots()} (TASK-384, follow-up of TASK-381's
 * "operational risk" note in CLAUDE.md): before this task, an unpinned
 * pseudo "Stage" lesson stub (seeded at startup for all 7 stage-eligible
 * groups since TASK-381 "stage on the grid") was completely unconfined by
 * the VRP - only workflow discipline (running {@code import-calendario}
 * before the first solve) kept it from scattering across arbitrary
 * weekdays. TASK-385 persisted stage-day facts ({@link
 * DailyActivityQuota#stageHours}) for all 7 stage groups; this test proves
 * the VRP now confines an unpinned Stage stub to those dates.
 *
 * <p>Statics setup/teardown mirrors {@link LessonStageBlackoutVrpTest}'s
 * per-test idiom.</p>
 */
class LessonStageConfinementVrpTest {

    private static final LocalDate STAGE_DAY = LocalDate.of(2027, 3, 24);
    private static final LocalDate NON_STAGE_DAY = LocalDate.of(2027, 3, 25);

    @AfterEach
    void clearStaticState() {
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.of(), Set.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
    }

    /** Shared scaffolding (timeslots + empty resets) every test below needs, regardless of group/quota data. */
    private void setupCommonStatics() {
        SchoolSchedule.setAllTimeslotsStatic(List.of(
                timeslot(1L, STAGE_DAY, 8, 0, 9, 0),
                timeslot(2L, NON_STAGE_DAY, 8, 0, 9, 0)));
        SchoolSchedule.setStageBlackoutDatesStatic(List.of(), Set.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
    }

    /**
     * Setup for the 4 tests below whose group DOES carry stage-day facts
     * (only {@link #pseudoStageLesson_groupWithNoStageFacts_unconfined}
     * needs different quota data) - mirrors {@link LessonStageBlackoutVrpTest#setupBlackout()}'s
     * "build and return the fully-configured group" idiom. TASK-385
     * class-days-sourced facts (FCQuota=false): STAGE_DAY is a real stage
     * day, NON_STAGE_DAY has an ordinary-classroom row.
     */
    private StudentGroup setupConfined() {
        StudentGroup g = group(1L, "G07", 20);
        setupCommonStatics();
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(g, STAGE_DAY, 0, 0, 4, false),
                dailyActivityQuota(g, NON_STAGE_DAY, 5, 0, 0, false)));
        return g;
    }

    @Test
    void pseudoStageLesson_groupWithStageFacts_confinedToStageDates() {
        StudentGroup g = setupConfined();
        Teacher stage = teacher(10L, "Stage", true);
        Course stageCourse = course(100L, "Stage", stage, g);
        Lesson stageLesson = lesson(1000L, stageCourse, null, null, false);

        List<LocalDate> dates = stageLesson.getAvailableTimeslots().stream()
                .map(Timeslot::getDate).toList();
        assertTrue(dates.contains(STAGE_DAY), "an unpinned Stage stub must be placeable on its group's stage day");
        assertFalse(dates.contains(NON_STAGE_DAY), "an unpinned Stage stub must NOT be placeable on a non-stage day (H25 VRP)");
    }

    @Test
    void pseudoStageLesson_groupWithNoStageFacts_unconfined() {
        // "G04" has no Stage course/stage-day facts at all (see
        // DailyActivityQuota.QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP's own javadoc) -
        // groupHasStageFacts() must be false, so the filter is a structural
        // no-op. Different quota data from setupConfined() (no
        // DailyActivityQuota rows at all), so this test uses the shared
        // scaffolding directly rather than setupConfined()'s quota-seeded variant.
        StudentGroup g = group(2L, "G04", 20);
        setupCommonStatics();
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());

        Teacher stage = teacher(10L, "Stage", true);
        Course stageCourse = course(101L, "Stage", stage, g);
        Lesson stageLesson = lesson(1001L, stageCourse, null, null, false);

        List<LocalDate> dates = stageLesson.getAvailableTimeslots().stream()
                .map(Timeslot::getDate).toList();
        assertTrue(dates.contains(STAGE_DAY));
        assertTrue(dates.contains(NON_STAGE_DAY), "a group with no stage-day facts at all must be unrestricted");
    }

    @Test
    void nonStagePseudoLesson_unaffected() {
        // "Esame" is a different pseudo-teacher accounting course - the Stage
        // confinement filter must only ever apply to the "Stage" subject.
        StudentGroup g = setupConfined();
        Teacher esame = teacher(11L, "Esame", true);
        Course esameCourse = course(102L, "Esame", esame, g);
        Lesson esameLesson = lesson(1002L, esameCourse, null, null, false);

        List<LocalDate> dates = esameLesson.getAvailableTimeslots().stream()
                .map(Timeslot::getDate).toList();
        assertTrue(dates.contains(STAGE_DAY));
        assertTrue(dates.contains(NON_STAGE_DAY), "a non-Stage pseudo lesson must be unaffected by Stage confinement");
    }

    @Test
    void realTeacherLesson_unaffected() {
        StudentGroup g = setupConfined();
        Teacher real = teacher(12L, "T25", false);
        Course scienze = course(103L, "Scienze", real, g);
        Lesson lesson = lesson(1003L, scienze, null, null, false);

        List<LocalDate> dates = lesson.getAvailableTimeslots().stream()
                .map(Timeslot::getDate).toList();
        assertTrue(dates.contains(STAGE_DAY));
        assertTrue(dates.contains(NON_STAGE_DAY), "a real-teacher lesson must be unaffected by Stage confinement");
    }

    @Test
    void pinnedStageLesson_unaffected_onlyItsOwnTimeslot() {
        StudentGroup g = setupConfined();
        Teacher stage = teacher(10L, "Stage", true);
        Course stageCourse = course(104L, "Stage", stage, g);
        Timeslot nonStageTs = timeslot(2L, NON_STAGE_DAY, 8, 0, 9, 0);
        // Pinned on the NON-stage day - proves the pinned short-circuit at the
        // top of getAvailableTimeslots() runs BEFORE the Stage confinement
        // filter, so a pinned lesson is never blocked by it.
        Lesson pinned = lesson(1004L, stageCourse, nonStageTs, null, true);

        List<Timeslot> available = pinned.getAvailableTimeslots();
        assertTrue(available.size() == 1 && available.contains(nonStageTs),
                "a pinned lesson must return only its own timeslot, unaffected by the Stage confinement filter");
    }
}
