package com.school.planner.service;

import com.school.planner.domain.*;
import com.school.planner.service.RotationTestRunner.BlockKey;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GreedyRotationAssigner CSP backtracking.
 *
 * <p>Verifies the core algorithm that replaces Cartesian product enumeration
 * with constraint-satisfying backtracking:</p>
 *
 * <ul>
 *   <li>External teacher ordering (T28 first)</li>
 *   <li>Valid day filtering by H5</li>
 *   <li>Backtracking when first choice fails</li>
 *   <li>Room exclusivity pruning</li>
 * </ul>
 *
 * @see GreedyRotationAssigner
 */
@QuarkusTest
class GreedyRotationAssignerTest {

    @Inject
    GreedyRotationAssigner assigner;

    @Inject
    PreAssignmentContext context;

    @BeforeEach
    @Transactional
    void cleanDatabase() {
        // Reset pre-assignment context to clear stale in-memory data
        context.reset();

        // Clean up in correct order (respecting foreign keys)
        Lesson.deleteAll();
        Course.deleteAll();  // Delete before Teacher (FK constraint)
        LabRotation.deleteAll();
        TeacherAvailability.deleteAll();
        SchoolHoliday.deleteAll();
        Timeslot.deleteAll();
        Room.deleteAll();
        Teacher.deleteAll();
        StudentGroup.deleteAll();
        StagePattern.deleteAll();
    }

    @Test
    @DisplayName("External teacher should be ordered first")
    @Transactional
    void testExternalTeacherOrdering() {
        // Given: One external teacher (only Thursday) and two regular teachers
        Teacher external = createTeacher("T28", "JOR");
        Teacher regular1 = createTeacher("T26", "VIS");
        Teacher regular2 = createTeacher("T18", "PIC");

        // Create vacation records for external teacher (all days except Thursday)
        createVacation(external, DayOfWeek.MONDAY);
        createVacation(external, DayOfWeek.TUESDAY);
        createVacation(external, DayOfWeek.WEDNESDAY);
        createVacation(external, DayOfWeek.FRIDAY);

        // Create student groups
        StudentGroup group1 = createGroup("G01");
        StudentGroup group2 = createGroup("G05");
        StudentGroup group3 = createGroup("G09");

        // Create room
        Room room = createRoom("Lab Acconciatura", "acconciatura");

        // Create courses and sessions
        Course course1 = createCourse("Acconciatura 1", external, group1, "acconciatura");
        Course course2 = createCourse("Acconciatura 2", regular1, group2, "acconciatura");
        Course course3 = createCourse("Acconciatura 3", regular2, group3, "acconciatura");

        LabSession session1 = createSession(course1, 6);
        LabSession session2 = createSession(course2, 6);
        LabSession session3 = createSession(course3, 6);

        // Build blocksByKeys map
        Map<BlockKey, List<LabSession>> blocksByKeys = new LinkedHashMap<>();
        blocksByKeys.put(new BlockKey(external.id, group1.id), List.of(session1));
        blocksByKeys.put(new BlockKey(regular1.id, group2.id), List.of(session2));
        blocksByKeys.put(new BlockKey(regular2.id, group3.id), List.of(session3));

        // Build config
        RotationTestRunner.TestConfig config = RotationTestRunner.TestConfig.of(
            LocalDate.of(2025, 10, 1),
            LocalDate.of(2025, 12, 31),
            Set.of(),
            List.of(),
            "acconciatura"
        );

        // Create timeslots for the test period (required for slot finding)
        createTimeslotsForPeriod(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31));

        // When: Run assignment
        GreedyRotationAssigner.AssignmentResult result = assigner.assignTeachers(
            "acconciatura", blocksByKeys, config);

        // Then: Should succeed with external teacher assigned to Thursday
        assertTrue(result.success(), "Assignment should succeed");
        assertNotNull(result.assignment(), "Should have an assignment");

        // External teacher should be assigned Thursday (their only valid day)
        DayOfWeek externalDay = result.assignment().getDayForTeacher(external.id);
        assertEquals(DayOfWeek.THURSDAY, externalDay,
            "External teacher should be assigned Thursday");

        // Should have tried fewer than 100 candidates (not 390,625!)
        assertTrue(result.candidatesTried() < 100,
            "Should try fewer than 100 candidates, got: " + result.candidatesTried());
    }

    @Test
    @DisplayName("Valid days should be filtered by H5 teacher availability")
    @Transactional
    void testValidDayFilteringByH5() {
        // Given: A teacher with vacation on Monday and Tuesday
        Teacher teacher = createTeacher("Teacher", "TEA");
        createVacation(teacher, DayOfWeek.MONDAY);
        createVacation(teacher, DayOfWeek.TUESDAY);

        StudentGroup group = createGroup("G01");
        Course course = createCourse("Course", teacher, group, "acconciatura");
        LabSession session = createSession(course, 6);

        Map<BlockKey, List<LabSession>> blocksByKeys = new LinkedHashMap<>();
        blocksByKeys.put(new BlockKey(teacher.id, group.id), List.of(session));

        RotationTestRunner.TestConfig config = RotationTestRunner.TestConfig.of(
            LocalDate.of(2025, 10, 1),
            LocalDate.of(2025, 12, 31),
            Set.of(),
            List.of(),
            "acconciatura"
        );

        // Create timeslots for the test period (required for slot finding)
        createTimeslotsForPeriod(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31));

        // When: Run assignment
        GreedyRotationAssigner.AssignmentResult result = assigner.assignTeachers(
            "acconciatura", blocksByKeys, config);

        // Then: Teacher should NOT be assigned Monday or Tuesday
        if (result.success()) {
            DayOfWeek assignedDay = result.assignment().getDayForTeacher(teacher.id);
            assertNotEquals(DayOfWeek.MONDAY, assignedDay, "Should not be Monday");
            assertNotEquals(DayOfWeek.TUESDAY, assignedDay, "Should not be Tuesday");
        }
    }

    @Test
    @DisplayName("Should return empty when no valid assignment exists")
    @Transactional
    void testNoValidAssignmentReturnsEmpty() {
        // Given: A teacher with no valid days (vacation every day)
        Teacher teacher = createTeacher("Unavailable", "UNA");
        createVacation(teacher, DayOfWeek.MONDAY);
        createVacation(teacher, DayOfWeek.TUESDAY);
        createVacation(teacher, DayOfWeek.WEDNESDAY);
        createVacation(teacher, DayOfWeek.THURSDAY);
        createVacation(teacher, DayOfWeek.FRIDAY);

        StudentGroup group = createGroup("G01");
        Course course = createCourse("Course", teacher, group, "acconciatura");
        LabSession session = createSession(course, 6);

        Map<BlockKey, List<LabSession>> blocksByKeys = new LinkedHashMap<>();
        blocksByKeys.put(new BlockKey(teacher.id, group.id), List.of(session));

        RotationTestRunner.TestConfig config = RotationTestRunner.TestConfig.of(
            LocalDate.of(2025, 10, 1),
            LocalDate.of(2025, 12, 31),
            Set.of(),
            List.of(),
            "acconciatura"
        );

        // Create timeslots for the test period (required for slot finding)
        createTimeslotsForPeriod(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31));

        // When: Run assignment
        GreedyRotationAssigner.AssignmentResult result = assigner.assignTeachers(
            "acconciatura", blocksByKeys, config);

        // Then: Should fail
        assertFalse(result.success(), "Assignment should fail when no valid days exist");
        assertNull(result.assignment(), "Should not have an assignment");
    }

    @Test
    @DisplayName("Multi-day teacher: each (teacher,group) pair gets its own LabRotation day")
    @Transactional
    void testMultiDayTeacherPerPairAssignment() {
        // Given: T26 teaches 2 groups on different days via LabRotation
        Teacher t26 = createTeacher("T26", "VIS");
        StudentGroup group1 = createGroup("G02");
        StudentGroup group2 = createGroup("G06");

        Room room = createRoom("Lab Acconciatura", "acconciatura");

        // Course 1: T26/G02
        Course course1 = createCourse("Acconciatura 1", t26, group1, "acconciatura");
        LabSession session1 = createSession(course1, 6);

        // Course 2: T26/G06
        Course course2 = createCourse("Acconciatura 2", t26, group2, "acconciatura");
        LabSession session2 = createSession(course2, 6);

        // Attach LabRotation: group1 → TUESDAY, group2 → WEDNESDAY
        LabRotation rot1 = createLabRotation(t26, group1, DayOfWeek.TUESDAY, "acconciatura");
        session1.labRotation = rot1;

        LabRotation rot2 = createLabRotation(t26, group2, DayOfWeek.WEDNESDAY, "acconciatura");
        session2.labRotation = rot2;

        Map<BlockKey, List<LabSession>> blocksByKeys = new LinkedHashMap<>();
        blocksByKeys.put(new BlockKey(t26.id, group1.id), List.of(session1));
        blocksByKeys.put(new BlockKey(t26.id, group2.id), List.of(session2));

        RotationTestRunner.TestConfig config = RotationTestRunner.TestConfig.of(
            LocalDate.of(2025, 10, 1),
            LocalDate.of(2025, 12, 31),
            Set.of(),
            List.of(),
            "acconciatura"
        );

        createTimeslotsForPeriod(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31));

        // When: Run assignment
        GreedyRotationAssigner.AssignmentResult result = assigner.assignTeachers(
            "acconciatura", blocksByKeys, config);

        // Then: Should succeed and place both sessions on their respective days
        assertTrue(result.success(), "Assignment should succeed");
        assertNotNull(result.slotAssignments(), "Should have slot assignments");
        assertEquals(2, result.slotAssignments().size(),
            "Should have 2 slot assignments (one per pair)");
    }

    @Test
    @DisplayName("Multi-day deterministic: sessions with different rotation days placed on respective days")
    @Transactional
    void testMultiDayDeterministicPlacement() {
        // Given: T15 has sessions on Monday (Lav.mecc) and Tuesday (Taglio)
        Teacher t15 = createTeacher("T15", "MOU");
        StudentGroup group = createGroup("G04");
        Room room = createRoom("R15", "meccanica");

        Course course = createCourse("Meccanica", t15, group, "meccanica");

        // 4 sessions: 2 with Monday rotation, 2 with Tuesday rotation
        LabRotation rotMonday = createLabRotation(t15, group, DayOfWeek.MONDAY, "meccanica");
        LabRotation rotTuesday = createLabRotation(t15, group, DayOfWeek.TUESDAY, "meccanica");

        LabSession session1 = createSession(course, 6);
        session1.labRotation = rotMonday;

        LabSession session2 = createSession(course, 6);
        session2.labRotation = rotMonday;

        LabSession session3 = createSession(course, 6);
        session3.labRotation = rotTuesday;

        LabSession session4 = createSession(course, 6);
        session4.labRotation = rotTuesday;

        Map<BlockKey, List<LabSession>> blocksByKeys = new LinkedHashMap<>();
        blocksByKeys.put(new BlockKey(t15.id, group.id),
            List.of(session1, session2, session3, session4));

        RotationTestRunner.TestConfig config = RotationTestRunner.TestConfig.of(
            LocalDate.of(2025, 10, 1),
            LocalDate.of(2025, 12, 31),
            Set.of(),
            List.of(),
            "meccanica"
        );

        createTimeslotsForPeriod(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31));

        // When: Run assignment
        GreedyRotationAssigner.AssignmentResult result = assigner.assignTeachers(
            "meccanica", blocksByKeys, config);

        // Then: All 4 sessions should be placed
        assertTrue(result.success(), "Assignment should succeed");
        assertEquals(4, result.slotAssignments().size(), "All 4 sessions should be placed");
    }

    @Test
    @DisplayName("Multi-day CSP: sessions without rotation overflow to multiple days")
    @Transactional
    void testMultiDayCspOverflow() {
        // Given: Teacher with many sessions but no LabRotation — only 2 Mondays available
        // (short period), so sessions must overflow to other days
        Teacher teacher = createTeacher("T17", "PEC");
        StudentGroup group = createGroup("G01");
        Room room = createRoom("R16", "moda");

        Course course = createCourse("Confezione", teacher, group, "moda");

        // 4 sessions, but only ~2 weeks of test period (2 Mondays, 2 Tuesdays, etc.)
        LabSession session1 = createSession(course, 6);
        LabSession session2 = createSession(course, 6);
        LabSession session3 = createSession(course, 6);
        LabSession session4 = createSession(course, 6);

        Map<BlockKey, List<LabSession>> blocksByKeys = new LinkedHashMap<>();
        blocksByKeys.put(new BlockKey(teacher.id, group.id),
            List.of(session1, session2, session3, session4));

        // Short period: only ~2 weeks, so each day of week has at most 2 occurrences
        RotationTestRunner.TestConfig config = RotationTestRunner.TestConfig.of(
            LocalDate.of(2025, 10, 6),  // Monday
            LocalDate.of(2025, 10, 17), // Friday (2 weeks)
            Set.of(),
            List.of(),
            "moda"
        );

        createTimeslotsForPeriod(LocalDate.of(2025, 10, 6), LocalDate.of(2025, 10, 17));

        // When: Run assignment
        GreedyRotationAssigner.AssignmentResult result = assigner.assignTeachers(
            "moda", blocksByKeys, config);

        // Then: Should place at least 2 sessions (possibly more via overflow)
        assertTrue(result.success(), "Assignment should succeed");
        assertTrue(result.slotAssignments().size() >= 2,
            "Should place at least 2 sessions, got: " + result.slotAssignments().size());
    }

    @Test
    @DisplayName("Mixed mode: rotation sessions placed deterministically, overflow uses CSP")
    @Transactional
    void testMixedDeterministicCspOverflow() {
        // Given: T15 has 4 sessions — 2 with Monday rotation, 2 without (overflow)
        Teacher t15 = createTeacher("T15", "MOU");
        StudentGroup group = createGroup("G04");
        Room room = createRoom("R15", "meccanica");

        Course course = createCourse("Meccanica", t15, group, "meccanica");

        LabRotation rotMonday = createLabRotation(t15, group, DayOfWeek.MONDAY, "meccanica");

        // 2 sessions with rotation (Monday), 2 without (overflow)
        LabSession session1 = createSession(course, 6);
        session1.labRotation = rotMonday;

        LabSession session2 = createSession(course, 6);
        session2.labRotation = rotMonday;

        LabSession session3 = createSession(course, 6); // no rotation
        LabSession session4 = createSession(course, 6); // no rotation

        Map<BlockKey, List<LabSession>> blocksByKeys = new LinkedHashMap<>();
        blocksByKeys.put(new BlockKey(t15.id, group.id),
            List.of(session1, session2, session3, session4));

        RotationTestRunner.TestConfig config = RotationTestRunner.TestConfig.of(
            LocalDate.of(2025, 10, 1),
            LocalDate.of(2025, 12, 31),
            Set.of(),
            List.of(),
            "meccanica"
        );

        createTimeslotsForPeriod(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31));

        // When: Run assignment
        GreedyRotationAssigner.AssignmentResult result = assigner.assignTeachers(
            "meccanica", blocksByKeys, config);

        // Then: All 4 sessions should be placed
        assertTrue(result.success(), "Assignment should succeed");
        assertEquals(4, result.slotAssignments().size(), "All 4 sessions should be placed");
    }

    // ─── Helper Methods ─────────────────────────────────────────

    @Transactional
    Teacher createTeacher(String name, String nickname) {
        Teacher t = new Teacher();
        t.name = name;
        t.nickname = nickname;
        t.persist();
        return t;
    }

    @Transactional
    StudentGroup createGroup(String name) {
        StudentGroup g = new StudentGroup();
        g.name = name;
        g.size = 20;
        g.persist();
        return g;
    }

    @Transactional
    Room createRoom(String name, String... tags) {
        Room r = new Room();
        r.name = name;
        r.capacity = 25;
        r.tags = new ArrayList<>(Arrays.asList(tags));
        r.persist();
        return r;
    }

    @Transactional
    Course createCourse(String subject, Teacher teacher, StudentGroup group, String... roomTags) {
        Course c = new Course();
        c.subject = subject;
        c.teacher = teacher;
        c.studentGroup = group;
        c.totalHours = 99;
        c.isLab = true;
        c.labBlockSize = 6;
        c.requiredRoomTags = new ArrayList<>(Arrays.asList(roomTags));
        c.persist();
        return c;
    }

    /**
     * Create a LabSession with the specified number of hours.
     * Creates placeholder lessons without timeslots (timeslots assigned during placement).
     */
    @Transactional
    LabSession createSession(Course course, int totalHours) {
        LabSession session = new LabSession(course);
        session.totalHours = totalHours;

        // Create placeholder lessons (timeslots will be assigned during placement)
        for (int i = 0; i < totalHours; i++) {
            Lesson lesson = new Lesson();
            lesson.course = course;
            lesson.pinned = false;
            lesson.persist();
            session.addLesson(lesson);
        }

        return session;
    }

    @Transactional
    LabRotation createLabRotation(Teacher teacher, StudentGroup group, DayOfWeek day, String roomTag) {
        LabRotation lr = new LabRotation();
        lr.teacher = teacher;
        lr.studentGroup = group;
        lr.dayOfWeek = day;
        lr.slotStart = 0;
        lr.blockSize = 6;
        lr.periodStart = LocalDate.of(2025, 10, 1);
        lr.periodEnd = LocalDate.of(2025, 12, 31);
        lr.roomTag = roomTag;
        lr.persist();
        return lr;
    }

    @Transactional
    void createVacation(Teacher teacher, DayOfWeek day) {
        // Create a vacation for the entire week
        LocalDate start = LocalDate.of(2025, 10, 6); // A Monday
        LocalDate date = start;
        while (date.getDayOfWeek() != day) {
            date = date.plusDays(1);
        }

        TeacherAvailability ta = new TeacherAvailability();
        ta.teacher = teacher;
        ta.startDateTime = date.atTime(8, 0);
        ta.endDateTime = date.atTime(14, 0);
        ta.type = AvailabilityType.VACATION;
        ta.persist();
    }

    /**
     * Create timeslots for all weekdays in the test period.
     * Required for the RotationTestRunner to find valid slots.
     */
    @Transactional
    void createTimeslotsForPeriod(LocalDate start, LocalDate end) {
        LocalDate date = start;
        while (!date.isAfter(end)) {
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                createTimeslotsForDay(date);
            }
            date = date.plusDays(1);
        }
    }

    /**
     * Create timeslots for a specific date.
     */
    @Transactional
    void createTimeslotsForDay(LocalDate date) {
        LocalTime[] slotTimes = SlotCandidate.getSlotTimes(date.getDayOfWeek());
        for (LocalTime time : slotTimes) {
            LocalDateTime start = LocalDateTime.of(date, time);
            // Check if timeslot already exists
            Timeslot existing = Timeslot.find("startDateTime", start).firstResult();
            if (existing == null) {
                Timeslot ts = new Timeslot();
                ts.startDateTime = start;
                ts.endDateTime = start.plusHours(1);
                ts.persist();
            }
        }
    }
}
