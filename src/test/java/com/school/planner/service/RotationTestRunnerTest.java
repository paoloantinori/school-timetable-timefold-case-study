package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.RotationTestRunner.BlockKey;
import com.school.planner.service.RotationTestRunner.TestConfig;
import com.school.planner.service.RotationTestRunner.TestResult;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Unit tests for RotationTestRunner.
 *
 * <p>Tests the early-abort rotation testing with in-memory conflict checking.
 * Verifies that the runner correctly identifies valid and invalid rotation candidates.</p>
 *
 * <p>Each test creates its own isolated data using unique room tags to avoid
 * interference between tests.</p>
 */
@QuarkusTest
class RotationTestRunnerTest {

    @Inject
    RotationTestRunner runner;

    @Inject
    PreAssignmentContext context;

    private static final LocalDate PERIOD_START = LocalDate.of(2025, 9, 8); // Monday
    private static final LocalDate PERIOD_END = LocalDate.of(2025, 12, 19);

    @BeforeEach
    @Transactional
    void setup() {
        // Reset context before each test
        context.reset();
    }

    /**
     * Test that a valid candidate with no conflicts succeeds.
     */
    @Test
    @Transactional
    void testValidCandidateSucceeds() {
        // Setup: Create teacher, group, course, session
        Teacher teacher = createTeacher("valid-teacher");
        StudentGroup group = createGroup("valid-group");
        Room room = createRoom("valid-room", "valid-tag");
        Course course = createCourse(teacher, group, "valid-subject", true, 6, "valid-tag");
        LabSession session = createSession(course, 6);

        // Create timeslots for Monday (the assigned day)
        createTimeslotsForDay(PERIOD_START); // Monday

        // Create candidate assigning Monday to this teacher-group pair
        BlockKey key = new BlockKey(teacher.id, group.id);
        Map<BlockKey, DayOfWeek> assignments = new HashMap<>();
        assignments.put(key, DayOfWeek.MONDAY);
        RotationCandidate candidate = new RotationCandidate(assignments, 1);

        Map<BlockKey, List<LabSession>> sessionsByKey = new HashMap<>();
        sessionsByKey.put(key, List.of(session));

        TestConfig config = TestConfig.of(PERIOD_START, PERIOD_END, Set.of(), List.of(), "valid-tag");

        // Initialize context (no pre-pinned entities)
        context.initialize();

        // Test the candidate
        TestResult result = runner.testCandidate(candidate, sessionsByKey, config);

        // Verify success
        assertTrue(result.success(), "Valid candidate should succeed");
        assertEquals(1, result.assignments().size(), "Should have one assignment");
        assertNull(result.failedBlock(), "No failed block on success");
        assertNull(result.analysis(), "No analysis on success");

        // Verify assignment details
        RotationTestRunner.SlotAssignment assignment = result.assignments().get(0);
        // Session should have 6 lessons
        assertNotNull(assignment.session(), "Session should be assigned");
    }

    /**
     * Test that a conflicting candidate aborts early on first conflict.
     */
    @Test
    @Transactional
    void testConflictingCandidateAbortsEarly() {
        // Setup: Create two teachers, one group, same room
        Teacher teacher1 = createTeacher("conflict-teacher-1");
        Teacher teacher2 = createTeacher("conflict-teacher-2");
        StudentGroup group1 = createGroup("conflict-group-1");
        StudentGroup group2 = createGroup("conflict-group-2");
        Room room = createRoom("conflict-room", "conflict-tag");

        Course course1 = createCourse(teacher1, group1, "subject-1", true, 6, "conflict-tag");
        Course course2 = createCourse(teacher2, group2, "subject-2", true, 6, "conflict-tag");

        LabSession session1 = createSession(course1, 6);
        LabSession session2 = createSession(course2, 6);

        // Create timeslots
        createTimeslotsForDay(PERIOD_START); // Monday

        // Pin session1's lessons to Monday's first 6 slots (08:00-14:00), one
        // lesson per distinct timeslot - NOT all six sharing a single slot.
        // With the 2026/27 8-slot day (TASK-343), a 6-hour block has 3 valid
        // start positions (0, 1, 2); occupying slots 0-5 fully overlaps every
        // one of them ([0-5], [1-6], [2-7]), so the room conflict is
        // guaranteed regardless of which candidate is tried.
        LocalTime[] slotTimes = SlotCandidate.getSlotTimes(PERIOD_START.getDayOfWeek());
        Timeslot startSlot = Timeslot.find("startDateTime", LocalDateTime.of(PERIOD_START, LocalTime.of(8, 0))).firstResult();
        assertNotNull(startSlot, "Start slot should exist");

        List<Lesson> session1Lessons = session1.getLessons();
        for (int i = 0; i < session1Lessons.size(); i++) {
            Lesson lesson = session1Lessons.get(i);
            Timeslot slot = Timeslot.find("startDateTime", LocalDateTime.of(PERIOD_START, slotTimes[i])).firstResult();
            assertNotNull(slot, "Slot " + i + " should exist");
            lesson.timeslot = slot;
            lesson.room = room;
            lesson.pinned = true;
            lesson.persist();
        }
        session1.room = room;
        session1.startTimeslot = startSlot;

        // Initialize context with pinned session
        context.initialize();

        // Create candidate assigning both to Monday
        BlockKey key1 = new BlockKey(teacher1.id, group1.id);
        BlockKey key2 = new BlockKey(teacher2.id, group2.id);
        Map<BlockKey, DayOfWeek> assignments = new HashMap<>();
        assignments.put(key1, DayOfWeek.MONDAY);
        assignments.put(key2, DayOfWeek.MONDAY);
        RotationCandidate candidate = new RotationCandidate(assignments, 1);

        Map<BlockKey, List<LabSession>> sessionsByKey = new HashMap<>();
        sessionsByKey.put(key1, List.of()); // session1 already pinned, no unpinned sessions
        sessionsByKey.put(key2, List.of(session2));

        // Use single-day period so conflict is guaranteed
        TestConfig config = TestConfig.of(PERIOD_START, PERIOD_START, Set.of(), List.of(), "conflict-tag");

        // Test - session2 should fail due to room conflict with pinned session1
        TestResult result = runner.testCandidate(candidate, sessionsByKey, config);

        // Verify early abort
        assertFalse(result.success(), "Conflicting candidate should fail");
        assertEquals(key2, result.failedBlock(), "Should abort on key2's session");
        assertNotNull(result.failureReason(), "Should have failure reason");
        assertNotNull(result.analysis(), "Should have failure analysis");
    }

    /**
     * Test that failure analysis provides detailed conflict information.
     */
    @Test
    @Transactional
    void testFailureAnalysisProvidesDetails() {
        Teacher teacher = createTeacher("analysis-teacher");
        StudentGroup group = createGroup("analysis-group");
        Room room = createRoom("analysis-room", "analysis-tag");
        Course course = createCourse(teacher, group, "analysis-subject", true, 6, "analysis-tag");
        LabSession session = createSession(course, 6);

        createTimeslotsForDay(PERIOD_START);

        // Pin this teacher busy at the slots common to EVERY possible 6-hour
        // start position, not just 08:00. With the 2026/27 8-slot day
        // (TASK-343), a 6-hour block has 3 valid starts (0, 1, 2), whose
        // windows are [0-5], [1-6], [2-7]; their intersection is [2-5]. A
        // single conflicting slot at index 0 (the old 6-slot day's only
        // possible start) no longer blocks starts 1 and 2, so occupying the
        // full intersection guarantees the conflict regardless of which
        // candidate window the runner tries - while keeping the room's
        // occupied count (4) well under total capacity (8), so the runner
        // takes the normal per-slot conflict path instead of the room's
        // early "no capacity" bailout (which reports zero conflict patterns
        // by design).
        LocalTime[] slotTimes = SlotCandidate.getSlotTimes(PERIOD_START.getDayOfWeek());
        int blockSize = session.lessons.size();
        int intersectionStart = slotTimes.length - blockSize;
        int intersectionEndInclusive = blockSize - 1;
        for (int i = intersectionStart; i <= intersectionEndInclusive; i++) {
            Lesson pinnedLesson = new Lesson();
            pinnedLesson.course = course;
            Timeslot conflictSlot = Timeslot.find("startDateTime", LocalDateTime.of(PERIOD_START, slotTimes[i])).firstResult();
            assertNotNull(conflictSlot, "Slot " + slotTimes[i] + " should exist");
            pinnedLesson.timeslot = conflictSlot;
            pinnedLesson.room = room;
            pinnedLesson.pinned = true;
            pinnedLesson.persist();
        }

        context.initialize();

        BlockKey key = new BlockKey(teacher.id, group.id);
        Map<BlockKey, DayOfWeek> assignments = new HashMap<>();
        assignments.put(key, DayOfWeek.MONDAY);
        RotationCandidate candidate = new RotationCandidate(assignments, 1);

        Map<BlockKey, List<LabSession>> sessionsByKey = Map.of(key, List.of(session));
        // Use single-day period so conflict is guaranteed
        TestConfig config = TestConfig.of(PERIOD_START, PERIOD_START, Set.of(), List.of(), "analysis-tag");

        TestResult result = runner.testCandidate(candidate, sessionsByKey, config);

        assertFalse(result.success());
        assertNotNull(result.analysis());
        assertEquals("1a-ROTATION-TEST", result.analysis().phase);
        assertNotNull(result.analysis().reason);
        assertFalse(result.analysis().conflictPatterns.isEmpty(), "Should have conflict patterns");
        assertTrue(result.analysis().conflictingTeacherIds.contains(teacher.id), "Should include teacher ID");
        assertNotNull(result.analysis().suggestion, "Should have actionable suggestion");
    }

    /**
     * Test that pinned sessions from previous phases are respected.
     */
    @Test
    @Transactional
    void testRespectsPinnedSessions() {
        Teacher teacher = createTeacher("pinned-teacher");
        StudentGroup group = createGroup("pinned-group");
        Room room = createRoom("pinned-room", "pinned-tag");
        Course course = createCourse(teacher, group, "pinned-subject", true, 6, "pinned-tag");

        // Create two sessions - one already pinned
        LabSession pinnedSession = createSession(course, 3);
        LabSession unpinnedSession = createSession(course, 3);

        createTimeslotsForDay(PERIOD_START);

        // Pin the first session to 08:00-10:00
        Timeslot pinnedStart = Timeslot.find("startDateTime", LocalDateTime.of(PERIOD_START, LocalTime.of(8, 0))).firstResult();
        for (Lesson lesson : pinnedSession.getLessons()) {
            lesson.timeslot = pinnedStart;
            lesson.room = room;
            lesson.pinned = true;
            lesson.persist();
        }
        pinnedSession.room = room;
        pinnedSession.startTimeslot = pinnedStart;

        context.initialize();

        BlockKey key = new BlockKey(teacher.id, group.id);
        Map<BlockKey, DayOfWeek> assignments = new HashMap<>();
        assignments.put(key, DayOfWeek.MONDAY);
        RotationCandidate candidate = new RotationCandidate(assignments, 1);

        // Only test unpinned session
        Map<BlockKey, List<LabSession>> sessionsByKey = Map.of(key, List.of(unpinnedSession));
        TestConfig config = TestConfig.of(PERIOD_START, PERIOD_END, Set.of(), List.of(), "pinned-tag");

        TestResult result = runner.testCandidate(candidate, sessionsByKey, config);

        // Should succeed - unpinned session can be placed after pinned session
        assertTrue(result.success(), "Should find slot after pinned session");

        // Verify the assignment doesn't overlap with pinned session
        RotationTestRunner.SlotAssignment assignment = result.assignments().get(0);
        boolean overlaps = assignment.occupiedTimeslots().stream()
            .anyMatch(ts -> ts.id.equals(pinnedStart.id));
        assertFalse(overlaps, "Assignment should not overlap with pinned session");
    }

    /**
     * Test that holidays are excluded from slot candidates.
     */
    @Test
    @Transactional
    void testHolidayFiltering() {
        Teacher teacher = createTeacher("holiday-teacher");
        StudentGroup group = createGroup("holiday-group");
        Room room = createRoom("holiday-room", "holiday-tag");
        Course course = createCourse(teacher, group, "holiday-subject", true, 6, "holiday-tag");
        LabSession session = createSession(course, 6);

        // Create timeslots for Monday only (short period - one week)
        LocalDate monday = PERIOD_START;
        LocalDate friday = PERIOD_START.plusDays(4); // End of same week
        createTimeslotsForDay(monday);

        // Monday is a holiday
        Set<LocalDate> holidays = Set.of(monday);

        context.initialize();

        BlockKey key = new BlockKey(teacher.id, group.id);
        Map<BlockKey, DayOfWeek> assignments = new HashMap<>();
        assignments.put(key, DayOfWeek.MONDAY); // Request Monday (which is a holiday)
        RotationCandidate candidate = new RotationCandidate(assignments, 1);

        Map<BlockKey, List<LabSession>> sessionsByKey = Map.of(key, List.of(session));
        // Use short period - just this week
        TestConfig config = TestConfig.of(monday, friday, holidays, List.of(), "holiday-tag");

        TestResult result = runner.testCandidate(candidate, sessionsByKey, config);

        // Should fail - no valid slots on Monday (holiday) within the short period
        assertFalse(result.success(), "Should fail when only slot is on a holiday");
        assertTrue(result.failureReason().contains("MONDAY") || result.analysis().reason.contains("Monday"),
            "Failure should mention Monday");
    }

    /**
     * Test that stage blackout days are excluded for non-professionalizzanti sessions.
     */
    @Test
    @Transactional
    void testStageBlackoutFiltering() {
        Teacher teacher = createTeacher("stage-teacher");
        StudentGroup group = createGroup("stage-group");
        Room room = createRoom("stage-room", "stage-tag");
        Course course = createCourse(teacher, group, "stage-subject", true, 6, "stage-tag");
        course.professionalizzanti = false; // NOT exempt from H4
        course.persist();

        LabSession session = createSession(course, 6);

        // Use short period - just this week
        LocalDate monday = PERIOD_START;
        LocalDate friday = PERIOD_START.plusDays(4);
        createTimeslotsForDay(monday);

        // Stage pattern blocks Monday for this group (short period that covers just this week)
        StagePattern stagePattern = new StagePattern(
            group, monday, friday, Set.of(DayOfWeek.MONDAY));
        stagePattern.persist();

        context.initialize();

        BlockKey key = new BlockKey(teacher.id, group.id);
        Map<BlockKey, DayOfWeek> assignments = new HashMap<>();
        assignments.put(key, DayOfWeek.MONDAY);
        RotationCandidate candidate = new RotationCandidate(assignments, 1);

        Map<BlockKey, List<LabSession>> sessionsByKey = Map.of(key, List.of(session));
        // Use short period - just this week
        TestConfig config = TestConfig.of(monday, friday, Set.of(), List.of(stagePattern), "stage-tag");

        TestResult result = runner.testCandidate(candidate, sessionsByKey, config);

        // Should fail - Monday is blocked by stage (short period, no other Mondays available)
        assertFalse(result.success(), "Should fail when slot is on stage blackout day");
    }

    /**
     * Test that professionalizzanti sessions are NOT blocked by stage patterns (H4 exemption).
     */
    @Test
    @Transactional
    void testProfessionalizzantiExemptFromStageBlackout() {
        Teacher teacher = createTeacher("prof-teacher");
        StudentGroup group = createGroup("prof-group");
        Room room = createRoom("prof-room", "prof-tag");
        Course course = createCourse(teacher, group, "prof-subject", true, 6, "prof-tag");
        course.professionalizzanti = true; // Exempt from H4
        course.persist();

        LabSession session = createSession(course, 6);

        LocalDate monday = PERIOD_START;
        createTimeslotsForDay(monday);

        // Stage pattern blocks Monday for this group
        StagePattern stagePattern = new StagePattern(
            group, monday, monday.plusWeeks(12), Set.of(DayOfWeek.MONDAY));
        stagePattern.persist();

        context.initialize();

        BlockKey key = new BlockKey(teacher.id, group.id);
        Map<BlockKey, DayOfWeek> assignments = new HashMap<>();
        assignments.put(key, DayOfWeek.MONDAY);
        RotationCandidate candidate = new RotationCandidate(assignments, 1);

        Map<BlockKey, List<LabSession>> sessionsByKey = Map.of(key, List.of(session));
        TestConfig config = TestConfig.of(PERIOD_START, PERIOD_END, Set.of(), List.of(stagePattern), "prof-tag");

        TestResult result = runner.testCandidate(candidate, sessionsByKey, config);

        // Should succeed - professionalizzanti exempt from stage blackout
        assertTrue(result.success(), "Professionalizzanti should be exempt from stage blackout");
    }

    /**
     * Test that teacher unavailability (H5 - VACATION) filters slots.
     */
    @Test
    @Transactional
    void testTeacherUnavailabilityH5() {
        Teacher teacher = createTeacher("h5-teacher");
        StudentGroup group = createGroup("h5-group");
        Room room = createRoom("h5-room", "h5-tag");
        Course course = createCourse(teacher, group, "h5-subject", true, 6, "h5-tag");
        LabSession session = createSession(course, 6);

        // Use short period - just this week
        LocalDate monday = PERIOD_START;
        LocalDate friday = PERIOD_START.plusDays(4);
        createTimeslotsForDay(monday);

        // Teacher is on vacation Monday
        TeacherAvailability vacation = new TeacherAvailability(
            teacher,
            monday.atTime(0, 0),
            monday.atTime(23, 59),
            AvailabilityType.VACATION
        );
        vacation.persist();

        context.initialize();

        BlockKey key = new BlockKey(teacher.id, group.id);
        Map<BlockKey, DayOfWeek> assignments = new HashMap<>();
        assignments.put(key, DayOfWeek.MONDAY);
        RotationCandidate candidate = new RotationCandidate(assignments, 1);

        Map<BlockKey, List<LabSession>> sessionsByKey = Map.of(key, List.of(session));
        // Use short period - just this week
        TestConfig config = TestConfig.of(monday, friday, Set.of(), List.of(), "h5-tag");

        TestResult result = runner.testCandidate(candidate, sessionsByKey, config);

        // Should fail - teacher on vacation Monday (short period, no other Mondays available)
        assertFalse(result.success(), "Should fail when teacher is unavailable (VACATION)");
    }

    /**
     * Test TestConfig.fromDatabase() loads holidays and stage patterns.
     */
    @Test
    @Transactional
    void testTestConfigFromDatabase() {
        // Create holiday
        SchoolHoliday holiday = new SchoolHoliday(PERIOD_START, "Test Holiday");
        holiday.persist();

        // Create stage pattern
        StudentGroup group = createGroup("config-group");
        StagePattern pattern = new StagePattern(group, PERIOD_START, PERIOD_END, Set.of(DayOfWeek.MONDAY));
        pattern.persist();

        // Load config
        TestConfig config = TestConfig.fromDatabase(PERIOD_START, PERIOD_END, "test-tag");

        assertNotNull(config);
        assertEquals(PERIOD_START, config.periodStart());
        assertEquals(PERIOD_END, config.periodEnd());
        assertTrue(config.holidays().contains(PERIOD_START), "Should include holiday");
        assertFalse(config.stagePatterns().isEmpty(), "Should include stage patterns");
        assertEquals("test-tag", config.roomTag());
    }

    // ─── Helper Methods ─────────────────────────────────────────────

    private Teacher createTeacher(String name) {
        Teacher teacher = new Teacher();
        teacher.name = name;
        teacher.persist();
        return teacher;
    }

    private StudentGroup createGroup(String name) {
        StudentGroup group = new StudentGroup();
        group.name = name;
        group.size = 20;
        group.persist();
        return group;
    }

    private Room createRoom(String name, String... tags) {
        Room room = new Room();
        room.name = name;
        room.capacity = 25;
        room.tags = Arrays.asList(tags);
        room.persist();
        return room;
    }

    private Course createCourse(Teacher teacher, StudentGroup group, String subject,
                                boolean isLab, int blockSize, String... roomTags) {
        Course course = new Course();
        course.teacher = teacher;
        course.studentGroup = group;
        course.subject = subject;
        course.totalHours = 66;
        course.isLab = isLab;
        course.labBlockSize = blockSize;
        course.requiredRoomTags = Arrays.asList(roomTags);
        course.persist();
        return course;
    }

    /**
     * Create a LabSession with placeholder lessons (timeslots assigned during placement).
     */
    private LabSession createSession(Course course, int totalHours) {
        LabSession session = new LabSession(course);
        session.totalHours = totalHours;

        for (int i = 0; i < totalHours; i++) {
            Lesson lesson = new Lesson();
            lesson.course = course;
            lesson.pinned = false;
            lesson.persist();
            session.addLesson(lesson);
        }

        return session;
    }

    private void createTimeslotsForDay(LocalDate date) {
        LocalTime[] slotTimes = SlotCandidate.getSlotTimes(date.getDayOfWeek());
        for (LocalTime time : slotTimes) {
            LocalDateTime start = LocalDateTime.of(date, time);
            LocalDateTime end = start.plusHours(1);

            // Check if timeslot already exists
            Timeslot existing = Timeslot.find("startDateTime", start).firstResult();
            if (existing == null) {
                Timeslot ts = new Timeslot();
                ts.startDateTime = start;
                ts.endDateTime = end;
                ts.persist();
            }
        }
    }
}
