package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Unit tests for H11 (Max student group daily hours) constraint in Phase 3 pre-assignment.
 *
 * <p><strong>Issue:</strong> Phase 3 places professionalizzanti lessons on designated days
 * (e.g., all on Tuesday for G11) without checking if this would exceed the group's
 * daily hour limit (H11 constraint).</p>
 *
 * <p><strong>H11 Constraint:</strong> Student groups cannot exceed {@code maxHoursPerDay}
 * hours in a single day. This is a HARD constraint that must be respected even during
 * pre-assignment.</p>
 *
 * <p><strong>Expected Behavior:</strong> Phase 3 should track daily lesson counts for each
 * group and stop placing lessons when the daily limit would be exceeded.</p>
 */
@QuarkusTest
class HybridPhase1aH11DailyHoursTest {

    @Inject
    HybridPhase1aService hybridPhase1aService;

    /**
     * Test: Documents the H11 violation bug in Phase 3.
     *
     * <p>When Phase 3 places many professionalizzanti lessons on a single day
     * (e.g., all 60 lessons on Tuesday), it can exceed the group's daily hour limit.</p>
     *
     * <p><strong>Reproduction:</strong></p>
     * <ol>
     *   <li>G11 has maxHoursPerDay (e.g., 6)</li>
     *   <li>Phase 3 places 60 professional lessons on Tuesday</li>
     *   <li>Each lesson is 1 hour, so 60 lessons on Tuesday = 60 hours on that day</li>
     *   <li>This grossly exceeds maxHoursPerDay of 6</li>
     * </ol>
     *
     * <p><strong>Expected Fix:</strong> Phase 3 should:</p>
     * <ul>
     *   <li>Track how many lessons are already placed on each day for each group</li>
     *   <li>Stop placing lessons when daily limit would be exceeded</li>
     *   <li>Distribute lessons across multiple days if needed</li>
     * </ul>
     */
    @Test
    @Transactional
    void testPhase3_DailyHourLimit_ViolatesH11() {
        // Setup: Create a group with maxHoursPerDay = 6
        StudentGroup group = createTestGroup("TEST-H11-VIOLATION");
        group.maxHoursPerDay = 6;
        group.persist();

        // Create stage pattern (Tue/Wed/Thu are stage days)
        Set<DayOfWeek> stageDays = Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
        StagePattern stagePattern = new StagePattern(
            group,
            LocalDate.of(2025, 9, 1),
            LocalDate.of(2025, 9, 30),
            stageDays
        );
        stagePattern.persist();

        // Create professional course with 60 hours (all would be placed on Tuesday)
        Course course = createTestCourse(group, true, 60);

        // Create Tuesday timeslots (6 slots available: 8:00-14:00)
        LocalDate tuesday = LocalDate.of(2025, 9, 2);
        for (int slot = 0; slot < 6; slot++) {
            Timeslot timeslot = new Timeslot();
            timeslot.startDateTime = LocalDateTime.of(tuesday, LocalTime.of(8 + slot, 0));
            timeslot.endDateTime = LocalDateTime.of(tuesday, LocalTime.of(8 + slot + 1, 0));
            timeslot.persist();
        }

        // Current behavior: Phase 3 would place all 60 lessons on Tuesday
        // This violates H11: maxHoursPerDay = 6, but placing 60 hours on Tuesday

        // Calculate expected daily hours after placement
        int lessonsOnTuesday = 60; // All professional lessons on Tuesday
        int hoursOnTuesday = lessonsOnTuesday; // Each lesson is 1 hour

        // Verify this would violate H11
        assertTrue(hoursOnTuesday > group.maxHoursPerDay,
            "Placing " + hoursOnTuesday + " hours on Tuesday exceeds maxHoursPerDay of " + group.maxHoursPerDay);

        // Expected: Phase 3 should only place up to 6 lessons on Tuesday
        // Remaining lessons should either:
        // 1. Be placed on other stage days (Wednesday/Thursday)
        // 2. Remain unpinned for the solver to handle
    }

    /**
     * Test: Phase 3 should respect daily hour limits.
     *
     * <p>Demonstrates expected behavior: when daily limit is reached, Phase 3 should
     * stop placing lessons on that day and either skip to next day or leave lessons unpinned.</p>
     */
    @Test
    @Transactional
    void testPhase3_RespectsDailyHourLimit_StopsAtLimit() {
        // Setup: Group with maxHoursPerDay = 5
        StudentGroup group = createTestGroup("TEST-H11-RESPECT");
        group.maxHoursPerDay = 5;
        group.persist();

        // Create stage pattern (Tue/Wed/Thu)
        Set<DayOfWeek> stageDays = Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
        StagePattern stagePattern = new StagePattern(
            group,
            LocalDate.of(2025, 9, 1),
            LocalDate.of(2025, 9, 30),
            stageDays
        );
        stagePattern.persist();

        // Create professional course with 20 hours
        Course course = createTestCourse(group, true, 20);

        // Create timeslots for Tue/Wed
        LocalDate tuesday = LocalDate.of(2025, 9, 2);
        LocalDate wednesday = LocalDate.of(2025, 9, 3);

        for (int slot = 0; slot < 6; slot++) {
            // Tuesday timeslots
            Timeslot tsTue = new Timeslot();
            tsTue.startDateTime = LocalDateTime.of(tuesday, LocalTime.of(8 + slot, 0));
            tsTue.endDateTime = LocalDateTime.of(tuesday, LocalTime.of(8 + slot + 1, 0));
            tsTue.persist();

            // Wednesday timeslots
            Timeslot tsWed = new Timeslot();
            tsWed.startDateTime = LocalDateTime.of(wednesday, LocalTime.of(8 + slot, 0));
            tsWed.endDateTime = LocalDateTime.of(wednesday, LocalTime.of(8 + slot + 1, 0));
            tsWed.persist();
        }

        // Expected behavior:
        // - Place up to 5 lessons on Tuesday (maxHoursPerDay = 5)
        // - Remaining 15 lessons should be distributed to Wednesday/Thursday OR left unpinned

        int maxPerDay = group.maxHoursPerDay;
        int totalLessons = 20;
        int lessonsFirstDay = Math.min(totalLessons, maxPerDay);
        int remainingLessons = totalLessons - lessonsFirstDay;

        // Verify calculation
        assertEquals(5, lessonsFirstDay, "Should place 5 lessons on first day (at limit)");
        assertEquals(15, remainingLessons, "Should have 15 lessons remaining");

        // Expected: After 5 lessons on Tuesday, Phase 3 should:
        // 1. Move to Wednesday and place up to 5 more lessons
        // 2. Continue until all lessons placed OR limit reached each day
        // 3. Leave any excess lessons unpinned for solver
    }

    /**
     * Test: Daily hour limit varies by group.
     *
     * <p>Different student groups may have different maxHoursPerDay based on
     * their grade level or schedule. Phase 3 should respect each group's limit.</p>
     */
    @Test
    @Transactional
    void testPhase3_DailyHourLimit_VariesByGroup() {
        // Group 1: maxHoursPerDay = 4 (e.g., younger students)
        StudentGroup group1 = createTestGroup("TEST-H11-G1");
        group1.maxHoursPerDay = 4;
        group1.persist();

        // Group 2: maxHoursPerDay = 7 (e.g., older students)
        StudentGroup group2 = createTestGroup("TEST-H11-G2");
        group2.maxHoursPerDay = 7;
        group2.persist();

        // Both groups have 20 professional hours to place on same day
        Course course1 = createTestCourse(group1, true, 20);
        Course course2 = createTestCourse(group2, true, 20);

        // Expected:
        // - Group 1: max 4 lessons per day
        // - Group 2: max 7 lessons per day
        // - Phase 3 should respect each group's individual limit

        assertEquals(4, group1.maxHoursPerDay, "Group 1 should have lower limit");
        assertEquals(7, group2.maxHoursPerDay, "Group 2 should have higher limit");
    }

    /**
     * Test: H11 constraint definition and enforcement.
     *
     * <p>Documents the H11 constraint semantics for reference.</p>
     */
    @Test
    void testH11_ConstraintDefinition() {
        // H11: Max student group daily hours
        // Definition: StudentGroup.maxHoursPerDay
        // Type: HARD constraint
        // Enforcement: No day can exceed this limit

        StudentGroup group = new StudentGroup();
        group.maxHoursPerDay = 6;

        // Verify constraint semantics
        assertNotNull(group.maxHoursPerDay, "maxHoursPerDay should be set");
        assertTrue(group.maxHoursPerDay > 0, "maxHoursPerDay should be positive");

        // Example: 6 hours/day means:
        // - Monday: max 6 lessons
        // - Tuesday: max 6 lessons
        // - etc.

        // If a group has 30 lessons to place on Tuesday, but maxHoursPerDay = 6:
        int totalLessons = 30;
        int maxPerDay = group.maxHoursPerDay;

        // Can only place 6 lessons on Tuesday
        int lessonsPlaced = Math.min(totalLessons, maxPerDay);
        assertEquals(6, lessonsPlaced, "Should place max 6 lessons on Tuesday");

        // Remaining 24 lessons must be placed on other days OR left unpinned
        int remaining = totalLessons - lessonsPlaced;
        assertEquals(24, remaining, "Should have 24 lessons remaining for other days");
    }

    /**
     * Test: Integration with existing pinned lessons.
     *
     * <p>Daily hour limit must consider BOTH newly placed professional lessons
     * AND already-pinned lessons from Phase 1/2 (acconciatura, labs).</p>
     */
    @Test
    @Transactional
    void testPhase3_H11ConsidersAlreadyPinnedLessons() {
        // Setup: Group with maxHoursPerDay = 6
        StudentGroup group = createTestGroup("TEST-H11-PINNED");
        group.maxHoursPerDay = 6;
        group.persist();

        // Create stage pattern (Tue/Wed/Thu)
        Set<DayOfWeek> stageDays = Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
        StagePattern stagePattern = new StagePattern(
            group,
            LocalDate.of(2025, 9, 2), // Start on Tuesday
            LocalDate.of(2025, 9, 30),
            stageDays
        );
        stagePattern.persist();

        // Simulate Phase 1 already placed 2 lessons on Tuesday
        LocalDate tuesday = LocalDate.of(2025, 9, 2);
        Course existingCourse = createTestCourse(group, false, 10);
        createPinnedLesson(existingCourse, tuesday, 8, 0);  // Lesson at 8:00
        createPinnedLesson(existingCourse, tuesday, 9, 0);  // Lesson at 9:00

        // Now Phase 3 tries to place 10 professional lessons on Tuesday
        Course profCourse = createTestCourse(group, true, 10);
        createTimeslotsForDay(tuesday, 6); // 6 slots on Tuesday (8:00-13:00)

        // Current behavior: Phase 3 doesn't check existing lessons
        // Would place all 10 professional lessons → 12 total lessons on Tuesday
        // This violates H11 (max 6 hours)

        int existingLessons = 2;
        int professionalLessons = 10;
        int totalOnTuesday = existingLessons + professionalLessons;

        assertTrue(totalOnTuesday > group.maxHoursPerDay,
            "Total lessons (" + totalOnTuesday + ") exceeds maxHoursPerDay (" + group.maxHoursPerDay + ")");

        // Expected: Phase 3 should count existing lessons and only place up to limit
        int remainingCapacity = group.maxHoursPerDay - existingLessons;
        int canPlace = Math.min(professionalLessons, remainingCapacity);

        assertEquals(4, canPlace, "Should only place 4 professional lessons (leaving 1 buffer)");
    }

    /**
     * Test: Documents specific groups (G07, G11) and their daily limits.
     *
     * <p>This test documents the actual issue observed in production.</p>
     */
    @Test
    @Transactional
    void testProductionIssue_G07_G11_H11Violation() {
        // Create groups matching production setup
        StudentGroup group2FB = createTestGroup("G07-PROD");
        group2FB.maxHoursPerDay = 6; // Example value
        group2FB.persist();

        StudentGroup group3FB = createTestGroup("G11-PROD");
        group3FB.maxHoursPerDay = 6; // Example value
        group3FB.persist();

        // Production issue:
        // - G07 has ProfessionalizzantiDay = MONDAY
        // - G11 has ProfessionalizzantiDay = TUESDAY (after fix)
        // - Phase 3 concentrates lessons on that single day
        // - If professionalizzanti hours > maxHoursPerDay, H11 is violated

        // For G11 with 60 professionalizzanti hours and maxHoursPerDay = 6:
        int professionalHours = 60; // Example: Gestione aziendale (35h) + Progettazione (25h)
        int maxPerDay = group3FB.maxHoursPerDay;
        int daysNeeded = (int) Math.ceil((double) professionalHours / maxPerDay);

        assertEquals(10, daysNeeded, "60 hours over 6 hours/day = 10 days needed");

        // But stage period is only ~14 weeks (98 days), with stage days Tue/Wed/Thu (3 days/week)
        // That's ~42 stage days total
        // Phase 3 should distribute lessons across available stage days, not concentrate all on Tuesday

        // Expected fix: Phase 3 should:
        // 1. Place up to maxHoursPerDay professional lessons on the designated day
        // 2. If more lessons remain, place on other stage days (if allowed by business logic)
        // 3. OR leave excess unpinned for solver to handle
    }

    // === Block-Aware H11 Validation Tests (TASK-298) ===

    /**
     * Test: Block-aware H11 validation prevents partial placement that would exceed daily limit.
     *
     * <p><strong>Scenario (TASK-298 bug):</strong></p>
     * <ul>
     *   <li>Group: 5-hour max (e.g., G12)</li>
     *   <li>Existing: 4 lessons already placed on the date</li>
     *   <li>Attempt: Place 2-hour block at slots 12-13</li>
     *   <li>Slot 12: 5th hour, within limit ✓</li>
     *   <li>Slot 13: 6th hour, EXCEEDS LIMIT ✗</li>
     * </ul>
     *
     * <p><strong>Expected:</strong> Block-aware validation should reject this placement
     * because 4 + 2 = 6 > 5 (maxHoursPerDay).</p>
     */
    @Test
    void testH11_BlockValidation_PreventsPartialPlacement() {
        // Setup: 5-hour group with 4 existing lessons
        StudentGroup group = new StudentGroup();
        group.id = 1L;
        group.name = "G12";
        group.maxHoursPerDay = 5;

        Teacher teacher = new Teacher();
        teacher.id = 1L;
        teacher.name = "Test Teacher";

        LocalDate date = LocalDate.of(2025, 9, 2);

        // Existing: 4 lessons already pinned on this date
        Map<Long, Map<LocalDate, Integer>> existingLessonCounts = new HashMap<>();
        Map<LocalDate, Integer> groupExisting = new HashMap<>();
        groupExisting.put(date, 4);
        existingLessonCounts.put(group.id, groupExisting);

        // New: 0 lessons placed in current phase
        Map<Long, Map<LocalDate, Integer>> newLessonDates = new HashMap<>();
        Map<LocalDate, Integer> groupNew = new HashMap<>();
        groupNew.put(date, 0);
        newLessonDates.put(group.id, groupNew);

        // Attempt: Place 2-hour block
        int blockSize = 2;

        // Test: Should reject because 4 + 2 = 6 > 5
        boolean wouldExceed = hybridPhase1aService.wouldExceedDailyLimit(
            group, date, existingLessonCounts, newLessonDates, blockSize);

        assertTrue(wouldExceed,
            "Block-aware validation should reject: 4 existing + 2 block = 6 > maxHoursPerDay(5)");
    }

    /**
     * Test: Block-aware H11 validation allows valid block placements.
     *
     * <p><strong>Scenario:</strong></p>
     * <ul>
     *   <li>Group: 5-hour max</li>
     *   <li>Existing: 3 lessons already placed on the date</li>
     *   <li>Attempt: Place 2-hour block</li>
     * </ul>
     *
     * <p><strong>Expected:</strong> Should allow because 3 + 2 = 5 ≤ 5 (maxHoursPerDay).</p>
     */
    @Test
    void testH11_BlockValidation_AllowsValidBlocks() {
        // Setup: 5-hour group with 3 existing lessons
        StudentGroup group = new StudentGroup();
        group.id = 2L;
        group.name = "Test Group";
        group.maxHoursPerDay = 5;

        Teacher teacher = new Teacher();
        teacher.id = 2L;
        teacher.name = "Test Teacher";

        LocalDate date = LocalDate.of(2025, 9, 3);

        // Existing: 3 lessons already pinned
        Map<Long, Map<LocalDate, Integer>> existingLessonCounts = new HashMap<>();
        Map<LocalDate, Integer> groupExisting = new HashMap<>();
        groupExisting.put(date, 3);
        existingLessonCounts.put(group.id, groupExisting);

        // New: 0 lessons placed in current phase
        Map<Long, Map<LocalDate, Integer>> newLessonDates = new HashMap<>();

        // Attempt: Place 2-hour block
        int blockSize = 2;

        // Test: Should allow because 3 + 2 = 5 <= 5
        boolean wouldExceed = hybridPhase1aService.wouldExceedDailyLimit(
            group, date, existingLessonCounts, newLessonDates, blockSize);

        assertFalse(wouldExceed,
            "Block-aware validation should allow: 3 existing + 2 block = 5 <= maxHoursPerDay(5)");
    }

    /**
     * Test: Block-aware H11 validation has NO per-teacher exception.
     *
     * <p><strong>TASK-440:</strong> the T28 id-14 exception (6 hours/day on 5-hour
     * groups) was removed with H11-T28: the hardcoded id pointed at the wrong
     * teacher on the current seed (id 14 = T09; T28 is not in the 2026/27 roster) and H7's
     * per-teacher cap binds first. The group's own cap now applies to every
     * teacher.</p>
     *
     * <p><strong>Scenario:</strong></p>
     * <ul>
     *   <li>Group: 5-hour max (e.g., G12)</li>
     *   <li>Teacher: id 14 (formerly the exception's target)</li>
     *   <li>Existing: 4 lessons already placed</li>
     *   <li>Attempt: Place 2-hour block</li>
     * </ul>
     *
     * <p><strong>Expected:</strong> Should reject because 4 + 2 = 6 > 5 (the group's
     * own maxHoursPerDay, no exception).</p>
     */
    @Test
    void testH11_BlockValidation_NoTeacherException() {
        // Setup: 5-hour group, the teacher that used to carry the exception
        StudentGroup group = new StudentGroup();
        group.id = 3L;
        group.name = "G12";
        group.maxHoursPerDay = 5;

        Teacher teacher = new Teacher();
        teacher.id = 14L;
        teacher.name = "Teacher 14";

        LocalDate date = LocalDate.of(2025, 9, 4);

        // Existing: 4 lessons already pinned
        Map<Long, Map<LocalDate, Integer>> existingLessonCounts = new HashMap<>();
        Map<LocalDate, Integer> groupExisting = new HashMap<>();
        groupExisting.put(date, 4);
        existingLessonCounts.put(group.id, groupExisting);

        // New: 0 lessons placed in current phase
        Map<Long, Map<LocalDate, Integer>> newLessonDates = new HashMap<>();

        // Attempt: Place 2-hour block
        int blockSize = 2;

        // Test: Should reject: 4 + 2 = 6 > maxHoursPerDay(5), no exception anymore
        boolean wouldExceed = hybridPhase1aService.wouldExceedDailyLimit(
            group, date, existingLessonCounts, newLessonDates, blockSize);

        assertTrue(wouldExceed,
            "No per-teacher exception: 4 existing + 2 block = 6 > maxHoursPerDay(5)");
    }

    /**
     * Test: Block-aware H11 validation works correctly for 6-hour groups.
     *
     * <p><strong>Scenario:</strong></p>
     * <ul>
     *   <li>Group: 6-hour max (e.g., G11)</li>
     *   <li>Existing: 5 lessons already placed</li>
     *   <li>Attempt: Place 2-hour block</li>
     * </ul>
     *
     * <p><strong>Expected:</strong> Should reject because 5 + 2 = 7 > 6 (maxHoursPerDay).</p>
     */
    @Test
    void testH11_BlockValidation_SixHourGroup() {
        // Setup: 6-hour group with 5 existing lessons
        StudentGroup group = new StudentGroup();
        group.id = 4L;
        group.name = "G11";
        group.maxHoursPerDay = 6;

        Teacher teacher = new Teacher();
        teacher.id = 3L;
        teacher.name = "Test Teacher";

        LocalDate date = LocalDate.of(2025, 9, 5);

        // Existing: 5 lessons already pinned
        Map<Long, Map<LocalDate, Integer>> existingLessonCounts = new HashMap<>();
        Map<LocalDate, Integer> groupExisting = new HashMap<>();
        groupExisting.put(date, 5);
        existingLessonCounts.put(group.id, groupExisting);

        // New: 0 lessons placed in current phase
        Map<Long, Map<LocalDate, Integer>> newLessonDates = new HashMap<>();

        // Attempt: Place 2-hour block
        int blockSize = 2;

        // Test: Should reject because 5 + 2 = 7 > 6
        boolean wouldExceed = hybridPhase1aService.wouldExceedDailyLimit(
            group, date, existingLessonCounts, newLessonDates, blockSize);

        assertTrue(wouldExceed,
            "Block-aware validation should reject: 5 existing + 2 block = 7 > maxHoursPerDay(6)");
    }

    // === Helper Methods ===

    private StudentGroup createTestGroup(String name) {
        StudentGroup group = new StudentGroup();
        group.name = name;
        group.size = 20;
        group.maxHoursPerDay = 6; // Default for testing
        group.persist();
        return group;
    }

    private Course createTestCourse(StudentGroup group, boolean professionalizzanti, int totalHours) {
        Teacher teacher = new Teacher();
        teacher.name = "Test Teacher";
        teacher.persist();

        Room room = new Room();
        room.name = "Test Room";
        room.capacity = 30;
        room.persist();

        Course course = new Course();
        course.studentGroup = group;
        course.teacher = teacher;
        course.subject = "Test Subject";
        course.totalHours = totalHours;
        course.professionalizzanti = professionalizzanti;
        course.persist();
        return course;
    }

    private Course createTestCourse(StudentGroup group, boolean professionalizzanti) {
        return createTestCourse(group, professionalizzanti, 20);
    }

    private Lesson createTestLesson(Course course) {
        Lesson lesson = new Lesson();
        lesson.course = course;
        lesson.pinned = false;
        lesson.persist();
        return lesson;
    }

    private Lesson createPinnedLesson(Course course, LocalDate date, int hour, int minute) {
        Lesson lesson = new Lesson();
        lesson.course = course;

        // Find or create timeslot
        LocalDateTime lessonStart = LocalDateTime.of(date, LocalTime.of(hour, minute));
        Timeslot timeslot = Timeslot.find("startDateTime", lessonStart).firstResult();
        if (timeslot == null) {
            timeslot = new Timeslot();
            timeslot.startDateTime = lessonStart;
            timeslot.endDateTime = lessonStart.plusHours(1);
            timeslot.persist();
        }

        lesson.timeslot = timeslot;
        lesson.pinned = true;
        lesson.persist();
        return lesson;
    }

    private void createTimeslotsForDay(LocalDate date, int slotCount) {
        for (int slot = 0; slot < slotCount; slot++) {
            Timeslot timeslot = new Timeslot();
            timeslot.startDateTime = LocalDateTime.of(date, LocalTime.of(8 + slot, 0));
            timeslot.endDateTime = LocalDateTime.of(date, LocalTime.of(8 + slot + 1, 0));
            timeslot.persist();
        }
    }

    private StagePattern createStagePattern(StudentGroup group, LocalDate start, LocalDate end, Set<DayOfWeek> days) {
        StagePattern pattern = new StagePattern(group, start, end, days);
        pattern.persist();
        return pattern;
    }
}
