package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.rest.dto.LessonViolationAnalysis;

/**
 * Tests that groupBy-based constraint violation analysis methods report
 * exactly one violation per group/subject/date combo, matching the solver's
 * matchCount() from the score breakdown.
 *
 * <p>Constraints H7, H11, M15, H17, M18 use {@code groupBy()} in the constraint
 * provider, so matchCount counts group-level violations. The analysis detail methods
 * must produce the same count — one entry per violating group, not one per lesson.</p>
 */
class GroupByViolationAnalysisTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 15);
    private static final LocalDate START = DATE.minusDays(1);
    private static final LocalDate END = DATE.plusDays(1);

    private ScheduleService scheduleService;
    private Method addH7Violations;
    private Method addM15Violations;
    private Method addH17Violations;
    private Method addM18Violations;

    @BeforeEach
    void setUp() throws Exception {
        scheduleService = new ScheduleService();

        Class<?>[] params = new Class<?>[]{
                List.class, SchoolSchedule.class, LocalDate.class, LocalDate.class, Locale.class, String.class
        };

        addH7Violations = ScheduleService.class.getDeclaredMethod("addH7Violations", params);
        addH7Violations.setAccessible(true);

        addM15Violations = ScheduleService.class.getDeclaredMethod("addM15Violations", params);
        addM15Violations.setAccessible(true);

        addH17Violations = ScheduleService.class.getDeclaredMethod("addH17Violations", params);
        addH17Violations.setAccessible(true);

        addM18Violations = ScheduleService.class.getDeclaredMethod("addM18Violations", params);
        addM18Violations.setAccessible(true);
    }

    // --- Helper methods ---

    private Teacher makeTeacher(String name) {
        Teacher t = new Teacher();
        t.name = name;
        t.id = System.nanoTime();
        return t;
    }

    private StudentGroup makeGroup(String name, int maxHours) {
        StudentGroup g = new StudentGroup();
        g.name = name;
        g.maxHoursPerDay = maxHours;
        g.id = System.nanoTime();
        return g;
    }

    private Course makeCourse(Teacher teacher, StudentGroup group, String subject) {
        Course c = new Course();
        c.teacher = teacher;
        c.studentGroup = group;
        c.subject = subject;
        c.isLab = false;
        c.id = System.nanoTime();
        return c;
    }

    private Timeslot makeTimeslot(LocalDate date, int hour) {
        Timeslot ts = new Timeslot(
                LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)),
                LocalDateTime.of(date, java.time.LocalTime.of(hour + 1, 0)));
        ts.id = System.nanoTime();
        return ts;
    }

    private Lesson makeLesson(long id, Course course, Timeslot timeslot) {
        Lesson l = new Lesson();
        l.id = id;
        l.course = course;
        l.timeslot = timeslot;
        l.pinned = false;
        l.isLabHour = false;
        return l;
    }

    private Lesson makeLesson(long id, Course course, Timeslot timeslot, boolean pinned) {
        Lesson l = makeLesson(id, course, timeslot);
        l.pinned = pinned;
        return l;
    }

    private SchoolSchedule makeSchedule(List<Lesson> lessons) {
        SchoolSchedule schedule = new SchoolSchedule();
        schedule.setLessons(lessons);
        return schedule;
    }

    private void invoke(Method method, List<LessonViolationAnalysis> results, SchoolSchedule schedule, String constraintId) {
        try {
            method.invoke(scheduleService, results, schedule, START, END, Locale.ENGLISH, constraintId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long countConstraint(List<LessonViolationAnalysis> results, String constraintId) {
        return results.stream()
                .flatMap(r -> r.violations().stream())
                .filter(v -> v.constraint().equals(constraintId))
                .count();
    }

    // --- H7: Teacher has >6 hours on a day ---

    @Test
    void h7_teacherExceedsMaxHours_reportsOneViolationPerTeacherDate() {
        Teacher teacher = makeTeacher("Rossi");
        StudentGroup group = makeGroup("G03", 6);
        Course course = makeCourse(teacher, group, "Matematica");

        // 7 lessons on same day for same teacher (hours 8-14)
        List<Lesson> lessons = new ArrayList<>();
        for (int h = 8; h <= 14; h++) {
            lessons.add(makeLesson(h, course, makeTimeslot(DATE, h)));
        }

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addH7Violations, results, makeSchedule(lessons), "H7");

        assertEquals(1, countConstraint(results, "H7"),
                "H7 should report 1 violation per teacher/date, not 7");
    }

    @Test
    void h7_twoTeachersExceedMax_reportsTwoViolations() {
        Teacher teacher1 = makeTeacher("Rossi");
        Teacher teacher2 = makeTeacher("Bianchi");
        StudentGroup group = makeGroup("G03", 6);
        Course course1 = makeCourse(teacher1, group, "Matematica");
        Course course2 = makeCourse(teacher2, group, "Italiano");

        List<Lesson> lessons = new ArrayList<>();
        for (int h = 8; h <= 14; h++) {
            lessons.add(makeLesson(h, course1, makeTimeslot(DATE, h)));
            lessons.add(makeLesson(100 + h, course2, makeTimeslot(DATE, h)));
        }

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addH7Violations, results, makeSchedule(lessons), "H7");

        assertEquals(2, countConstraint(results, "H7"),
                "H7 should report 2 violations (one per teacher)");
    }

    // TASK-348 code review (D2): a teacher whose override LOWERS the cap
    // below the default (6) must still be flagged - the fast path used to
    // compare against the global default, so a 5h day for a 4h-max teacher
    // was silently skipped even though it exceeds THEIR cap.
    @Test
    void h7_teacherWithLowerOverride_fiveHourDayViolates() {
        Teacher teacher = makeTeacher("Rossi");
        teacher.maxDailyHours = 4;
        StudentGroup group = makeGroup("G03", 6);
        Course course = makeCourse(teacher, group, "Matematica");

        // 5 lessons on the same day - under the global default (6) but over
        // this teacher's own override (4).
        List<Lesson> lessons = new ArrayList<>();
        for (int h = 8; h <= 12; h++) {
            lessons.add(makeLesson(h, course, makeTimeslot(DATE, h)));
        }

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addH7Violations, results, makeSchedule(lessons), "H7");

        assertEquals(1, countConstraint(results, "H7"),
                "a 5h day must violate H7 for a teacher whose override caps at 4");
    }

    // --- M15: Same-subject non-consecutive on same day ---

    @Test
    void h15_userScenario_disegnoAndPersonalizzazione_reportsTwoViolations() {
        // User's exact scenario: 3 Disegno lessons with 1 gap, 2 Personalizzazione with 1 gap
        Teacher teacher = makeTeacher("Verdi");
        StudentGroup group = makeGroup("G02", 6);

        Course disegno = makeCourse(teacher, group, "Disegno");
        Course personalizzazione = makeCourse(teacher, group, "Personalizzazione");

        List<Lesson> lessons = new ArrayList<>();
        // Disegno: hours 8, 9, 11 → gap at 10
        lessons.add(makeLesson(1, disegno, makeTimeslot(DATE, 8)));
        lessons.add(makeLesson(2, disegno, makeTimeslot(DATE, 9)));
        lessons.add(makeLesson(3, disegno, makeTimeslot(DATE, 11)));

        // Personalizzazione: hours 12, 14 → gap at 13
        lessons.add(makeLesson(4, personalizzazione, makeTimeslot(DATE, 12)));
        lessons.add(makeLesson(5, personalizzazione, makeTimeslot(DATE, 14)));

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addM15Violations, results, makeSchedule(lessons), "M15");

        assertEquals(2, countConstraint(results, "M15"),
                "M15 should report 2 violations (1 Disegno + 1 Personalizzazione), not 5");
    }

    @Test
    void h15_threeDisegnoOneGap_reportsOneViolation() {
        Teacher teacher = makeTeacher("Verdi");
        StudentGroup group = makeGroup("G02", 6);
        Course disegno = makeCourse(teacher, group, "Disegno");

        List<Lesson> lessons = new ArrayList<>();
        lessons.add(makeLesson(1, disegno, makeTimeslot(DATE, 8)));
        lessons.add(makeLesson(2, disegno, makeTimeslot(DATE, 9)));
        lessons.add(makeLesson(3, disegno, makeTimeslot(DATE, 11)));

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addM15Violations, results, makeSchedule(lessons), "M15");

        assertEquals(1, countConstraint(results, "M15"),
                "M15 should report 1 violation for one subject/group/date combo with gap");
    }

    @Test
    void h15_consecutiveLessons_reportsNoViolations() {
        Teacher teacher = makeTeacher("Verdi");
        StudentGroup group = makeGroup("G02", 6);
        Course disegno = makeCourse(teacher, group, "Disegno");

        List<Lesson> lessons = new ArrayList<>();
        lessons.add(makeLesson(1, disegno, makeTimeslot(DATE, 8)));
        lessons.add(makeLesson(2, disegno, makeTimeslot(DATE, 9)));
        lessons.add(makeLesson(3, disegno, makeTimeslot(DATE, 10)));

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addM15Violations, results, makeSchedule(lessons), "M15");

        assertEquals(0, countConstraint(results, "M15"),
                "M15 should report 0 violations when lessons are consecutive");
    }

    @Test
    void h15_bothSidesPinned_reportsNoViolation() {
        // TASK-349: mirrors sameDaySameSubjectMustBeConsecutive's break-level pinned
        // exemption - this drill-down must never disagree with the M15 score.
        Teacher teacher = makeTeacher("Verdi");
        StudentGroup group = makeGroup("G02", 6);
        Course disegno = makeCourse(teacher, group, "Disegno");

        List<Lesson> lessons = new ArrayList<>();
        Lesson l1 = makeLesson(1, disegno, makeTimeslot(DATE, 8));
        Lesson l2 = makeLesson(2, disegno, makeTimeslot(DATE, 11)); // gap at 9, 10, both sides pinned
        l1.pinned = true;
        l2.pinned = true;
        lessons.add(l1);
        lessons.add(l2);

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addM15Violations, results, makeSchedule(lessons), "M15");

        assertEquals(0, countConstraint(results, "M15"),
                "M15 must not report a violation for a pinned-pinned split (matches the score's pinned exemption)");
    }

    @Test
    void h15_oneSidePinned_stillReportsViolation() {
        // TASK-349: only one side pinned - the solver must not be allowed to create a
        // new split against a pinned lesson, so this must still be reported.
        Teacher teacher = makeTeacher("Verdi");
        StudentGroup group = makeGroup("G02", 6);
        Course disegno = makeCourse(teacher, group, "Disegno");

        List<Lesson> lessons = new ArrayList<>();
        Lesson l1 = makeLesson(1, disegno, makeTimeslot(DATE, 8));
        Lesson l2 = makeLesson(2, disegno, makeTimeslot(DATE, 11)); // gap at 9, 10, only l1 pinned
        l1.pinned = true;
        l2.pinned = false;
        lessons.add(l1);
        lessons.add(l2);

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addM15Violations, results, makeSchedule(lessons), "M15");

        assertEquals(1, countConstraint(results, "M15"),
                "M15 must still report a violation when only one side of the gap is pinned");
    }

    // --- H17: Vertically compact (gaps in student schedule) ---

    @Test
    void h17_groupWithGaps_reportsOneViolationPerGroupDate() {
        Teacher teacher1 = makeTeacher("Rossi");
        Teacher teacher2 = makeTeacher("Bianchi");
        StudentGroup group = makeGroup("G03", 6);
        Course mat = makeCourse(teacher1, group, "Matematica");
        Course ita = makeCourse(teacher2, group, "Italiano");

        // 3 lessons at 8, 9, 12 → gap at 10, 11
        List<Lesson> lessons = new ArrayList<>();
        lessons.add(makeLesson(1, mat, makeTimeslot(DATE, 8)));
        lessons.add(makeLesson(2, mat, makeTimeslot(DATE, 9)));
        lessons.add(makeLesson(3, ita, makeTimeslot(DATE, 12)));

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addH17Violations, results, makeSchedule(lessons), "H17");

        assertEquals(1, countConstraint(results, "H17"),
                "H17 should report 1 violation per group/date, not 3");
    }

    @Test
    void h17_twoGroupsSameDate_reportsTwoViolations() {
        Teacher teacher = makeTeacher("Rossi");
        StudentGroup group1 = makeGroup("G03", 6);
        StudentGroup group2 = makeGroup("G07", 6);
        Course course1 = makeCourse(teacher, group1, "Matematica");
        Course course2 = makeCourse(teacher, group2, "Matematica");

        List<Lesson> lessons = new ArrayList<>();
        // Group 1: gap at 10
        lessons.add(makeLesson(1, course1, makeTimeslot(DATE, 8)));
        lessons.add(makeLesson(2, course1, makeTimeslot(DATE, 11)));
        // Group 2: gap at 10
        lessons.add(makeLesson(3, course2, makeTimeslot(DATE, 8)));
        lessons.add(makeLesson(4, course2, makeTimeslot(DATE, 11)));

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addH17Violations, results, makeSchedule(lessons), "H17");

        assertEquals(2, countConstraint(results, "H17"),
                "H17 should report 2 violations (one per group)");
    }

    @Test
    void h17_bothSidesPinned_reportsNoViolation() {
        // TASK-350: mirrors verticallyCompact's break-level pinned exemption - this
        // drill-down must never disagree with the H17 score.
        Teacher teacher = makeTeacher("Rossi");
        StudentGroup group = makeGroup("G03", 6);
        Course course = makeCourse(teacher, group, "Matematica");

        List<Lesson> lessons = new ArrayList<>();
        lessons.add(makeLesson(1, course, makeTimeslot(DATE, 8), true));
        lessons.add(makeLesson(2, course, makeTimeslot(DATE, 11), true)); // gap at 9, 10, both pinned

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addH17Violations, results, makeSchedule(lessons), "H17");

        assertEquals(0, countConstraint(results, "H17"),
                "H17 must not report a violation for a pinned-pinned gap (matches the score's pinned exemption)");
    }

    @Test
    void h17_oneSidePinned_stillReportsViolation() {
        // TASK-350: only one side of the gap pinned - the solver must not be allowed
        // to create a fresh gap against a pinned lesson.
        Teacher teacher = makeTeacher("Rossi");
        StudentGroup group = makeGroup("G03", 6);
        Course course = makeCourse(teacher, group, "Matematica");

        List<Lesson> lessons = new ArrayList<>();
        lessons.add(makeLesson(1, course, makeTimeslot(DATE, 8), true));
        lessons.add(makeLesson(2, course, makeTimeslot(DATE, 11), false)); // gap at 9, 10, only l1 pinned

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addH17Violations, results, makeSchedule(lessons), "H17");

        assertEquals(1, countConstraint(results, "H17"),
                "H17 must still report a violation when only one side of the gap is pinned");
    }

    // --- M18: day should start from first open slot (widened TASK-405; was H18/5-hour-gated) ---

    @Test
    void m18_groupNotStartingAt8am_reportsOneViolationPerGroupDate() {
        Teacher teacher = makeTeacher("Rossi");
        StudentGroup group = makeGroup("G09", 5); // maxHoursPerDay=5
        Course course = makeCourse(teacher, group, "Disegno");

        // 3 lessons starting at 9:00, not 08:00
        List<Lesson> lessons = new ArrayList<>();
        lessons.add(makeLesson(1, course, makeTimeslot(DATE, 9)));
        lessons.add(makeLesson(2, course, makeTimeslot(DATE, 10)));
        lessons.add(makeLesson(3, course, makeTimeslot(DATE, 11)));

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addM18Violations, results, makeSchedule(lessons), "M18");

        assertEquals(1, countConstraint(results, "M18"),
                "M18 should report 1 violation per group/date, not 3");
    }

    @Test
    void m18_groupStartingAt8am_reportsNoViolations() {
        Teacher teacher = makeTeacher("Rossi");
        StudentGroup group = makeGroup("G09", 5);
        Course course = makeCourse(teacher, group, "Disegno");

        List<Lesson> lessons = new ArrayList<>();
        lessons.add(makeLesson(1, course, makeTimeslot(DATE, 8)));
        lessons.add(makeLesson(2, course, makeTimeslot(DATE, 9)));
        lessons.add(makeLesson(3, course, makeTimeslot(DATE, 10)));

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addM18Violations, results, makeSchedule(lessons), "M18");

        assertEquals(0, countConstraint(results, "M18"),
                "M18 should report 0 violations when group starts at 08:00");
    }

    @Test
    void m18_sixHourGroup_nowReportedAfterWidening() {
        // TASK-405: the <=5-open-slots gate is gone - a 6h group's late-start day is
        // reported like any other (the drill-down mirrors the widened constraint).
        Teacher teacher = makeTeacher("Rossi");
        StudentGroup group = makeGroup("G03", 6); // maxHoursPerDay > 5
        Course course = makeCourse(teacher, group, "Matematica");

        List<Lesson> lessons = new ArrayList<>();
        lessons.add(makeLesson(1, course, makeTimeslot(DATE, 10))); // Not at 08:00
        lessons.add(makeLesson(2, course, makeTimeslot(DATE, 11)));

        List<LessonViolationAnalysis> results = new ArrayList<>();
        invoke(addM18Violations, results, makeSchedule(lessons), "M18");

        assertEquals(1, countConstraint(results, "M18"),
                "M18 must report the late-start day for wide-window groups too (widened TASK-405)");
    }
}
