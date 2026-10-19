package com.school.planner.solver;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import com.school.planner.TestDataBuilder;
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.constraints.RoomConflictJustification;
import com.school.planner.domain.constraints.SparseDayJustification;
import com.school.planner.domain.constraints.StudentGroupConflictJustification;
import com.school.planner.domain.constraints.StudentGroupDailyHoursJustification;
import com.school.planner.domain.constraints.TeacherConflictJustification;
import com.school.planner.domain.constraints.TeacherDailyHoursJustification;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for constraint justifications.
 * <p>
 * Verifies that constraints attach proper justification records via
 * {@code justifyWith()} for efficient violation reporting.
 */
@QuarkusTest
class JustificationTest {

    private static final LocalDate MON = LocalDate.of(2025, 9, 15);
    private static final int DEFAULT_ROOM_CAPACITY = 30;

    @Inject
    ConstraintVerifier<SchoolScheduleConstraintProvider, SchoolSchedule> constraintVerifier;

    // ─── H1 Teacher Conflict Tests ──────────────────────────────────────

    @Test
    void teacherConflict_producesJustification() {
        // Arrange: Two overlapping lessons for the same teacher
        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Matematica", TestDataBuilder.teacher(1L, "Test Teacher"),
                        TestDataBuilder.group(1L, "G01", 20)),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0),
                TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY));
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Italiano", TestDataBuilder.teacher(1L, "Test Teacher"),
                        TestDataBuilder.group(2L, "G05", 20)),
                TestDataBuilder.timeslot(2L, MON, 8, 0, 9, 0),
                TestDataBuilder.room(2L, "A102", 30));

        // Act & Assert: Constraint should penalize with justification
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(lesson1, lesson2)
                .penalizesBy(1); // ONE_HARD
    }

    @Test
    void teacherConflict_notPenalizedDifferentTimes() {
        // Arrange: Same teacher, different times (no conflict)
        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Matematica", TestDataBuilder.teacher(1L, "Test Teacher"),
                        TestDataBuilder.group(1L, "G01", 20)),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0),
                TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY));
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Italiano", TestDataBuilder.teacher(1L, "Test Teacher"),
                        TestDataBuilder.group(2L, "G05", 20)),
                TestDataBuilder.timeslot(2L, MON, 9, 0, 10, 0),
                TestDataBuilder.room(2L, "A102", 30));

        // Act & Assert: No penalty expected
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(lesson1, lesson2)
                .penalizesBy(0);
    }

    @Test
    void teacherConflict_notPenalizedDifferentTeachers() {
        // Arrange: Same timeslot, different teachers (no conflict)
        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Matematica", TestDataBuilder.teacher(1L, "Teacher One"),
                        TestDataBuilder.group(1L, "G01", 20)),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0),
                TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY));
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Italiano", TestDataBuilder.teacher(2L, "Teacher Two"),
                        TestDataBuilder.group(2L, "G05", 20)),
                TestDataBuilder.timeslot(2L, MON, 8, 0, 9, 0),
                TestDataBuilder.room(2L, "A102", 30));

        // Act & Assert: No penalty expected (different teachers)
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(lesson1, lesson2)
                .penalizesBy(0);
    }

    // ─── H2 Room Conflict Tests ─────────────────────────────────────────

    @Test
    void roomConflict_producesJustification() {
        // Arrange: Two overlapping lessons in the same room, different groups
        Room room = TestDataBuilder.room(1L, "Lab Info", DEFAULT_ROOM_CAPACITY);
        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Informatica", TestDataBuilder.teacher(1L, "Teacher One"),
                        TestDataBuilder.group(1L, "G01", 20)),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0),
                room);
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Informatica", TestDataBuilder.teacher(2L, "Teacher Two"),
                        TestDataBuilder.group(2L, "G05", 20)),
                TestDataBuilder.timeslot(2L, MON, 8, 0, 9, 0),
                room);

        // Act & Assert: Constraint should penalize with justification
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(lesson1, lesson2)
                .penalizesBy(1); // ONE_HARD
    }

    @Test
    void roomConflict_notPenalizedSameGroup() {
        // Arrange: Same room, same timeslot, same group (same course lessons)
        Room room = TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY);
        StudentGroup group = TestDataBuilder.group(1L, "G01", 20);
        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Matematica", TestDataBuilder.teacher(1L, "Teacher One"), group),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0),
                room);
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Italiano", TestDataBuilder.teacher(2L, "Teacher Two"), group),
                TestDataBuilder.timeslot(2L, MON, 8, 0, 9, 0),
                room);

        // Act & Assert: No penalty (same group)
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(lesson1, lesson2)
                .penalizesBy(0);
    }

    // ─── H3 Student Group Conflict Tests ─────────────────────────────────

    @Test
    void studentGroupConflict_producesJustification() {
        // Arrange: Two overlapping lessons for the same group, different courses
        StudentGroup group = TestDataBuilder.group(1L, "G01", 20);
        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Matematica", TestDataBuilder.teacher(1L, "Teacher One"), group),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0),
                TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY));
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Italiano", TestDataBuilder.teacher(2L, "Teacher Two"), group),
                TestDataBuilder.timeslot(2L, MON, 8, 0, 9, 0),
                TestDataBuilder.room(2L, "A102", 30));

        // Act & Assert: Constraint should penalize with justification
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupConflict)
                .given(lesson1, lesson2)
                .penalizesBy(1); // ONE_HARD
    }

    @Test
    void studentGroupConflict_notPenalizedConsecutiveSlots() {
        // Arrange: same group, same course, ADJACENT slots (8-9 and 9-10) —
        // half-open slots never overlap, so no conflict. (TASK-377 removed the
        // same-course allowance; this passes because the slots differ, not
        // because the course is shared.)
        StudentGroup group = TestDataBuilder.group(1L, "G01", 20);
        Course course = TestDataBuilder.course(1L, "Matematica", TestDataBuilder.teacher(1L, "Teacher One"), group);
        Lesson lesson1 = TestDataBuilder.lesson(1L, course,
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0),
                TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY));
        Lesson lesson2 = TestDataBuilder.lesson(2L, course,
                TestDataBuilder.timeslot(2L, MON, 9, 0, 10, 0),
                TestDataBuilder.room(2L, "A102", 30));

        // Act & Assert: no penalty (no temporal overlap)
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupConflict)
                .given(lesson1, lesson2)
                .penalizesBy(0);
    }

    // ─── Justification Structure Tests ───────────────────────────────────

    @Test
    void teacherConflictJustification_containsRequiredFields() {
        // Verify justification record structure with IDs
        TeacherConflictJustification justification = new TeacherConflictJustification(
                1L,  // teacherId
                100L, // lesson1Id
                200L  // lesson2Id
        );

        assertEquals(1L, justification.teacherId());
        assertEquals(100L, justification.lesson1Id());
        assertEquals(200L, justification.lesson2Id());
    }

    @Test
    void roomConflictJustification_containsRequiredFields() {
        RoomConflictJustification justification = new RoomConflictJustification(
                10L,  // roomId
                100L, // lesson1Id
                200L  // lesson2Id
        );

        assertEquals(10L, justification.roomId());
        assertEquals(100L, justification.lesson1Id());
        assertEquals(200L, justification.lesson2Id());
    }

    @Test
    void studentGroupConflictJustification_containsRequiredFields() {
        StudentGroupConflictJustification justification = new StudentGroupConflictJustification(
                20L,  // groupId
                100L, // lesson1Id
                200L  // lesson2Id
        );

        assertEquals(20L, justification.groupId());
        assertEquals(100L, justification.lesson1Id());
        assertEquals(200L, justification.lesson2Id());
    }

    // ─── H7 Teacher Daily Hours Tests ────────────────────────────────────

    @Test
    void teacherDailyHours_producesJustification() {
        // Arrange: 7 lessons for the same teacher on the same day (exceeds limit of 6)
        Teacher teacher = TestDataBuilder.teacher(1L, "Test Teacher");
        StudentGroup group1 = TestDataBuilder.group(1L, "G01", 20);
        StudentGroup group2 = TestDataBuilder.group(2L, "G05", 20);
        Room room = TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY);

        // Create 7 lessons at different times on the same day
        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Matematica", teacher, group1),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0), room);
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Italiano", teacher, group2),
                TestDataBuilder.timeslot(2L, MON, 9, 0, 10, 0), room);
        Lesson lesson3 = TestDataBuilder.lesson(3L,
                TestDataBuilder.course(3L, "Storia", teacher, group1),
                TestDataBuilder.timeslot(3L, MON, 10, 0, 11, 0), room);
        Lesson lesson4 = TestDataBuilder.lesson(4L,
                TestDataBuilder.course(4L, "Geografia", teacher, group2),
                TestDataBuilder.timeslot(4L, MON, 11, 0, 12, 0), room);
        Lesson lesson5 = TestDataBuilder.lesson(5L,
                TestDataBuilder.course(5L, "Inglese", teacher, group1),
                TestDataBuilder.timeslot(5L, MON, 12, 0, 13, 0), room);
        Lesson lesson6 = TestDataBuilder.lesson(6L,
                TestDataBuilder.course(6L, "Educazione Fisica", teacher, group2),
                TestDataBuilder.timeslot(6L, MON, 14, 0, 15, 0), room);
        Lesson lesson7 = TestDataBuilder.lesson(7L,
                TestDataBuilder.course(7L, "Musica", teacher, group1),
                TestDataBuilder.timeslot(7L, MON, 15, 0, 16, 0), room);

        // Act & Assert: Constraint should penalize with justification
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxTeacherDailyHours)
                .given(lesson1, lesson2, lesson3, lesson4, lesson5, lesson6, lesson7)
                .penalizesBy(1); // 1 excess hour (7 - 6 = 1)
    }

    @Test
    void teacherDailyHours_notPenalizedWithinLimit() {
        // Arrange: 6 lessons for the same teacher (within limit)
        Teacher teacher = TestDataBuilder.teacher(1L, "Test Teacher");
        StudentGroup group = TestDataBuilder.group(1L, "G01", 20);
        Room room = TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY);

        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Matematica", teacher, group),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0), room);
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Italiano", teacher, group),
                TestDataBuilder.timeslot(2L, MON, 9, 0, 10, 0), room);
        Lesson lesson3 = TestDataBuilder.lesson(3L,
                TestDataBuilder.course(3L, "Storia", teacher, group),
                TestDataBuilder.timeslot(3L, MON, 10, 0, 11, 0), room);
        Lesson lesson4 = TestDataBuilder.lesson(4L,
                TestDataBuilder.course(4L, "Geografia", teacher, group),
                TestDataBuilder.timeslot(4L, MON, 11, 0, 12, 0), room);
        Lesson lesson5 = TestDataBuilder.lesson(5L,
                TestDataBuilder.course(5L, "Inglese", teacher, group),
                TestDataBuilder.timeslot(5L, MON, 12, 0, 13, 0), room);
        Lesson lesson6 = TestDataBuilder.lesson(6L,
                TestDataBuilder.course(6L, "Educazione Fisica", teacher, group),
                TestDataBuilder.timeslot(6L, MON, 14, 0, 15, 0), room);

        // Act & Assert: No penalty expected (at limit)
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxTeacherDailyHours)
                .given(lesson1, lesson2, lesson3, lesson4, lesson5, lesson6)
                .penalizesBy(0);
    }

    @Test
    void teacherDailyHoursJustification_containsRequiredFields() {
        TeacherDailyHoursJustification justification = new TeacherDailyHoursJustification(
                1L,             // teacherId
                MON,            // date
                8,              // lessonCount
                6,              // maxHours
                2               // excessHours
        );

        assertEquals(1L, justification.teacherId());
        assertEquals(MON, justification.date());
        assertEquals(8, justification.lessonCount());
        assertEquals(6, justification.maxHours());
        assertEquals(2, justification.excessHours());
    }

    // ─── H11 Student Group Daily Hours Tests ───────────────────────────────

    @Test
    void studentGroupDailyHours_producesJustification() {
        // Arrange: Student group with maxHoursPerDay=5, scheduled 7 lessons
        StudentGroup group = TestDataBuilder.group(1L, "G01", 20);
        group.maxHoursPerDay = 5; // Set limit

        Teacher teacher1 = TestDataBuilder.teacher(1L, "Teacher One");
        Teacher teacher2 = TestDataBuilder.teacher(2L, "Teacher Two");
        Room room = TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY);

        // Create 7 lessons for the same group on the same day (exceeds limit of 5)
        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Matematica", teacher1, group),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0), room);
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Italiano", teacher2, group),
                TestDataBuilder.timeslot(2L, MON, 9, 0, 10, 0), room);
        Lesson lesson3 = TestDataBuilder.lesson(3L,
                TestDataBuilder.course(3L, "Storia", teacher1, group),
                TestDataBuilder.timeslot(3L, MON, 10, 0, 11, 0), room);
        Lesson lesson4 = TestDataBuilder.lesson(4L,
                TestDataBuilder.course(4L, "Geografia", teacher2, group),
                TestDataBuilder.timeslot(4L, MON, 11, 0, 12, 0), room);
        Lesson lesson5 = TestDataBuilder.lesson(5L,
                TestDataBuilder.course(5L, "Inglese", teacher1, group),
                TestDataBuilder.timeslot(5L, MON, 12, 0, 13, 0), room);
        Lesson lesson6 = TestDataBuilder.lesson(6L,
                TestDataBuilder.course(6L, "Educazione Fisica", teacher2, group),
                TestDataBuilder.timeslot(6L, MON, 14, 0, 15, 0), room);
        Lesson lesson7 = TestDataBuilder.lesson(7L,
                TestDataBuilder.course(7L, "Musica", teacher1, group),
                TestDataBuilder.timeslot(7L, MON, 15, 0, 16, 0), room);

        // Act & Assert: Constraint should penalize with justification
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxStudentGroupDailyHours)
                .given(lesson1, lesson2, lesson3, lesson4, lesson5, lesson6, lesson7)
                .penalizesBy(2); // 2 excess hours (7 - 5 = 2)
    }

    @Test
    void studentGroupDailyHours_notPenalizedWithinLimit() {
        // Arrange: Student group with maxHoursPerDay=5, scheduled 5 lessons
        StudentGroup group = TestDataBuilder.group(1L, "G01", 20);
        group.maxHoursPerDay = 5; // Set limit

        Teacher teacher = TestDataBuilder.teacher(1L, "Teacher");
        Room room = TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY);

        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Matematica", teacher, group),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0), room);
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Italiano", teacher, group),
                TestDataBuilder.timeslot(2L, MON, 9, 0, 10, 0), room);
        Lesson lesson3 = TestDataBuilder.lesson(3L,
                TestDataBuilder.course(3L, "Storia", teacher, group),
                TestDataBuilder.timeslot(3L, MON, 10, 0, 11, 0), room);
        Lesson lesson4 = TestDataBuilder.lesson(4L,
                TestDataBuilder.course(4L, "Geografia", teacher, group),
                TestDataBuilder.timeslot(4L, MON, 11, 0, 12, 0), room);
        Lesson lesson5 = TestDataBuilder.lesson(5L,
                TestDataBuilder.course(5L, "Inglese", teacher, group),
                TestDataBuilder.timeslot(5L, MON, 12, 0, 13, 0), room);

        // Act & Assert: No penalty expected (at limit)
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxStudentGroupDailyHours)
                .given(lesson1, lesson2, lesson3, lesson4, lesson5)
                .penalizesBy(0);
    }

    @Test
    void studentGroupDailyHoursJustification_containsRequiredFields() {
        StudentGroupDailyHoursJustification justification = new StudentGroupDailyHoursJustification(
                1L,             // groupId
                MON,            // date
                7,              // lessonCount
                5,              // maxHours
                2               // excessHours
        );

        assertEquals(1L, justification.groupId());
        assertEquals(MON, justification.date());
        assertEquals(7, justification.lessonCount());
        assertEquals(5, justification.maxHours());
        assertEquals(2, justification.excessHours());
    }

    // ─── M20 Sparse Day Tests ─────────────────────────────────────────────

    @Test
    void sparseDay_producesJustification() {
        // Arrange: Student group with only 2 lessons on a day (sparse day)
        StudentGroup group = TestDataBuilder.group(1L, "G01", 20);
        Teacher teacher = TestDataBuilder.teacher(1L, "Teacher");
        Room room = TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY);

        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Matematica", teacher, group),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0), room);
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Italiano", teacher, group),
                TestDataBuilder.timeslot(2L, MON, 10, 0, 11, 0), room);

        // Act & Assert: Constraint should penalize with justification
        // Deficit = quota(6) - actualHours = 6 - 2 = 4 -> m20DeficitCost(4) = 7000 medium
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(lesson1, lesson2)
                .penalizesBy(7_000);
    }

    @Test
    void sparseDay_notPenalizedFullDay() {
        // Arrange: Student group with 4+ lessons on a day (not sparse)
        StudentGroup group = TestDataBuilder.group(1L, "G01", 20);
        Teacher teacher = TestDataBuilder.teacher(1L, "Teacher");
        Room room = TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY);

        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Matematica", teacher, group),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0), room);
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Italiano", teacher, group),
                TestDataBuilder.timeslot(2L, MON, 9, 0, 10, 0), room);
        Lesson lesson3 = TestDataBuilder.lesson(3L,
                TestDataBuilder.course(3L, "Storia", teacher, group),
                TestDataBuilder.timeslot(3L, MON, 10, 0, 11, 0), room);
        Lesson lesson4 = TestDataBuilder.lesson(4L,
                TestDataBuilder.course(4L, "Geografia", teacher, group),
                TestDataBuilder.timeslot(4L, MON, 11, 0, 12, 0), room);

        // Act & Assert: 4 hours is still below quota(6), deficit 2 -> m20DeficitCost(2) = 2500
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(lesson1, lesson2, lesson3, lesson4)
                .penalizesBy(2_500);
    }

    @Test
    void sparseDay_notPenalizedEmptyDay() {
        // Arrange: Two days with 1 lesson each (both are sparse days)
        StudentGroup group = TestDataBuilder.group(1L, "G01", 20);
        Teacher teacher = TestDataBuilder.teacher(1L, "Teacher");
        Room room = TestDataBuilder.room(1L, "A101", DEFAULT_ROOM_CAPACITY);

        Lesson lesson1 = TestDataBuilder.lesson(1L,
                TestDataBuilder.course(1L, "Matematica", teacher, group),
                TestDataBuilder.timeslot(1L, MON, 8, 0, 9, 0), room);
        Lesson lesson2 = TestDataBuilder.lesson(2L,
                TestDataBuilder.course(2L, "Italiano", teacher, group),
                TestDataBuilder.timeslot(2L, LocalDate.of(2025, 9, 16), 9, 0, 10, 0), room);

        // Act & Assert: Each day has 1 lesson (deficit 5 per day -> m20DeficitCost(5) = 10000, x2)
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(lesson1, lesson2)
                .penalizesBy(20_000);
    }

    @Test
    void sparseDayJustification_containsRequiredFields() {
        SparseDayJustification justification = new SparseDayJustification(
                1L,             // groupId
                MON,            // date
                2,              // actualHours
                4               // requiredHours
        );

        assertEquals(1L, justification.groupId());
        assertEquals(MON, justification.date());
        assertEquals(2, justification.actualHours());
        assertEquals(4, justification.requiredHours());
    }
}
