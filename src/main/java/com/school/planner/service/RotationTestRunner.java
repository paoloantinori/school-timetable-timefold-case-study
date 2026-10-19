package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jboss.logging.Logger;

import com.school.planner.service.LabSession;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.domain.LabRotation;
import com.school.planner.domain.Lesson;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.util.StageEligibilityUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Tests rotation candidates with early-abort behavior.
 *
 * <p>Evaluates a RotationCandidate by trying to assign all blocks
 * according to the day-of-week assignments. Aborts immediately on
 * first conflict without persisting anything.</p>
 *
 * <p>Uses PreAssignmentContext for O(1) in-memory conflict checking.</p>
 *
 * <p><strong>Algorithm:</strong></p>
 * <ol>
 *   <li>For each (BlockKey, DayOfWeek) in candidate.assignments:</li>
 *   <li>Find the LabBlock for this key</li>
 *   <li>Generate SlotCandidates for the assigned day (filtered by holidays, stage, H5)</li>
 *   <li>For each slot in priority order:</li>
 *   <li>Check H1/H2/H3 conflicts via PreAssignmentContext</li>
 *   <li>If conflict-free, track assignment and continue</li>
 *   <li>If no valid slot found, ABORT and return failure with analysis</li>
 * </ol>
 *
 * <p><strong>Performance:</strong> O(n × m) where n = number of blocks,
 * m = average slots per day. Early abort means we typically don't explore
 * the full space.</p>
 */
@ApplicationScoped
public class RotationTestRunner {

    /**
     * Key identifying a unique teacher-group pairing for rotation assignment.
     *
     * <p>Moved from RotationCandidate when that class was deleted.
     * Used to identify which blocks belong to which teacher-group pair.</p>
     */
    public record BlockKey(long teacherId, long studentGroupId) {
        @Override
        public String toString() {
            return "BlockKey[teacher=" + teacherId + ", group=" + studentGroupId + "]";
        }
    }

    private static final Logger LOG = Logger.getLogger(RotationTestRunner.class);

    @Inject
    PreAssignmentContext context;

    /**
     * Cache for room capacity checks.
     * Key: "roomTag|dayOfWeek", Value: true if ANY room has capacity for this tag+day
     * This prevents re-checking capacity for every block when rooms are full.
     */
    private final Map<String, Boolean> roomCapacityCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Test a rotation candidate with early abort.
     *
     * @param candidate The rotation to test (day-of-week assignments)
     * @param sessionsByKeys Map from BlockKey to the LabSessions to assign
     * @param config Test configuration (period, holidays, stage patterns, roomTag)
     * @return TestResult with success/failure and analysis
     */
    public TestResult testCandidate(RotationCandidate candidate,
                                     Map<BlockKey, List<LabSession>> sessionsByKeys,
                                     TestConfig config) {
        LOG.debugf("[TestRunner] Testing candidate #%d with %d assignments",
            candidate.candidateNumber(), candidate.size());

        // Ensure context is initialized (preload pinned entities)
        if (!context.isInitialized()) {
            context.initialize();
        }

        // Track all successful assignments for this test run
        List<SlotAssignment> assignments = new ArrayList<>();

        // Track sessions we've assigned during this test (for context cleanup)
        List<LabSession> trackedSessions = new ArrayList<>();

        // Process each assignment in the candidate
        for (Map.Entry<BlockKey, DayOfWeek> entry : candidate.assignments().entrySet()) {
            BlockKey key = entry.getKey();
            DayOfWeek assignedDay = entry.getValue();

            List<LabSession> sessionsForKey = sessionsByKeys.get(key);
            if (sessionsForKey == null || sessionsForKey.isEmpty()) {
                LOG.debugf("[TestRunner] No sessions found for key %s, skipping", key);
                continue;
            }

            // Try to assign all sessions for this key
            for (LabSession session : sessionsForKey) {
                Optional<SlotAssignment> slotOpt = findValidSlot(session, assignedDay, config);

                if (slotOpt.isEmpty()) {
                    // EARLY ABORT - no valid slot found
                    LOG.debugf("[TestRunner] EARLY ABORT: No valid slot for session (course=%s) on %s",
                        session.course != null ? session.course.subject : "null", assignedDay);

                    // Build failure analysis
                    FailureAnalysis analysis = buildFailureAnalysis(candidate, key, session, assignedDay, config);

                    // Cleanup tracked sessions
                    cleanupTrackedSessions(trackedSessions);

                    return TestResult.failure(candidate, key,
                        "No valid slot available on " + assignedDay, analysis);
                }

                SlotAssignment slot = slotOpt.get();
                assignments.add(slot);

                // Track this assignment in context so subsequent sessions see it
                trackSessionAssignment(session, slot);
                trackedSessions.add(session);
            }
        }

        // SUCCESS - all sessions assigned
        LOG.debugf("[TestRunner] SUCCESS: Candidate #%d passed with %d assignments",
            candidate.candidateNumber(), assignments.size());

        // Cleanup tracked sessions
        cleanupTrackedSessions(trackedSessions);

        return TestResult.success(candidate, assignments);
    }

    /**
     * Find a valid slot assignment for a session on the specified day.
     *
     * @param session The LabSession to assign
     * @param day The day of week to assign to
     * @param config Test configuration
     * @return Optional SlotAssignment if found, empty if no valid slot
     */
    private Optional<SlotAssignment> findValidSlot(LabSession session, DayOfWeek day, TestConfig config) {
        List<SlotCandidate> candidates = generateSlotCandidates(session, day, config);

        LOG.debugf("[TestRunner] Generated %d slot candidates for session (course=%s) on %s",
            candidates.size(), session.course != null ? session.course.subject : "null", day);

        // Try each candidate in priority order
        for (SlotCandidate candidate : candidates) {
            Timeslot startSlot = candidate.startSlot();

            // Get the room (from session's course or first available with matching tag)
            Room room = findRoom(session, config);
            if (room == null) {
                LOG.debugf("[TestRunner] No room found for session with tag %s",
                    config.roomTag());
                continue;
            }

            // Get all occupied timeslots for this session
            int blockSize = session.lessons.size();
            List<Timeslot> occupiedSlots = getOccupiedSlots(startSlot, blockSize);

            // Check for conflicts
            Optional<ConflictInfo> conflictOpt = checkConflicts(session, room, occupiedSlots);
            if (conflictOpt.isEmpty()) {
                // No conflicts - this slot is valid!
                return Optional.of(new SlotAssignment(
                    new BlockKey(
                        session.getTeacher().id,
                        session.getStudentGroup().id
                    ),
                    session,
                    startSlot,
                    room,
                    occupiedSlots
                ));
            }

            LOG.tracef("[TestRunner] Conflict found at slot %s: %s",
                startSlot, conflictOpt.get());
        }

        // No valid slot found
        return Optional.empty();
    }

    /**
     * Generate slot candidates for a specific day of week.
     * Filters by holidays, stage blackout, and H5 teacher unavailability.
     *
     * @param session The LabSession to assign
     * @param day The day of week to generate candidates for
     * @param config Test configuration
     * @return Sorted list of slot candidates (by priority)
     */
    private List<SlotCandidate> generateSlotCandidates(LabSession session, DayOfWeek day, TestConfig config) {
        List<SlotCandidate> candidates = new ArrayList<>();

        Teacher teacher = session.getTeacher();
        StudentGroup group = session.getStudentGroup();
        int blockSize = session.lessons.size();

        // Determine the effective period for this session
        // If session has a labRotation, use its period; otherwise use config period
        LocalDate effectiveStart = config.periodStart();
        LocalDate effectiveEnd = config.periodEnd();
        DayOfWeek effectiveDay = day;  // Default to requested day

        // Get labRotation from the first lesson if available
        LabRotation labRotation = session.labRotation;

        if (labRotation != null) {
            if (labRotation.periodStart != null) {
                effectiveStart = labRotation.periodStart;
            }
            if (labRotation.periodEnd != null) {
                effectiveEnd = labRotation.periodEnd;
            }

            // Determine if this teacher is "external" (limited to 1 day by VACATION records)
            int teacherValidDays = countValidDaysForTeacher(teacher, config);
            boolean isExternalTeacher = (teacherValidDays == 1);

            if (isExternalTeacher) {
                // External teacher: VACATION records are the real constraint.
                // The 'day' parameter from backtracking should be respected.
                LOG.infof("[TestRunner] Session (group=%s, teacher=%s) is EXTERNAL (1 valid day), using backtracking day=%s",
                    group != null ? group.name : "null", teacher != null ? teacher.name : "null", effectiveDay);
            } else {
                // Internal teacher: rotation.dayOfWeek IS the constraint (fixed day assignment).
                if (labRotation.dayOfWeek != null) {
                    effectiveDay = labRotation.dayOfWeek;
                    LOG.infof("[TestRunner] Session (group=%s, teacher=%s) is INTERNAL (%d valid days), using rotation day=%s",
                        group != null ? group.name : "null", teacher != null ? teacher.name : "null", teacherValidDays, effectiveDay);
                }
            }
        } else {
            LOG.warnf("[TestRunner] Session (group=%s) has NO rotation, using config period: %s to %s",
                group != null ? group.name : "null", effectiveStart, effectiveEnd);
        }

        // Early bailout: Check if any room has capacity for this day
        if (!hasRoomCapacity(config.roomTag(), effectiveDay, effectiveStart, effectiveEnd, config)) {
            LOG.infof("[TestRunner] Early bailout: All rooms for tag %s are at capacity on %s",
                config.roomTag(), effectiveDay);
            return candidates; // Return empty list
        }

        // Iterate through all dates in the effective period
        LocalDate date = effectiveStart;
        int datesChecked = 0;
        int skippedWrongDay = 0;
        int skippedHoliday = 0;
        int skippedStage = 0;
        int skippedUnavailable = 0;
        int datesWithSlots = 0;

        while (!date.isAfter(effectiveEnd)) {
            datesChecked++;
            // Only consider the effective day of week (from rotation or requested)
            if (date.getDayOfWeek() != effectiveDay) {
                date = date.plusDays(1);
                skippedWrongDay++;
                continue;
            }

            // Filter: H6 - School holidays
            if (config.holidays().contains(date)) {
                date = date.plusDays(1);
                skippedHoliday++;
                continue;
            }

            // Filter: H4 - Stage blackout (non-professionalizzanti only)
            if (StageEligibilityUtil.isStageBlackout(date, group, config.stagePatterns())) {
                // Professionalizzanti courses are exempt from H4
                if (session.course == null || !session.course.professionalizzanti) {
                    date = date.plusDays(1);
                    skippedStage++;
                    continue;
                }
            }

            // Filter: H5 - Teacher unavailability
            if (isTeacherUnavailable(teacher, date)) {
                date = date.plusDays(1);
                skippedUnavailable++;
                continue;
            }

            datesWithSlots++;

            // Generate candidates for all valid slots on this day
            LocalTime[] slotTimes = SlotCandidate.getSlotTimes(effectiveDay);
            for (int slotIndex = 0; slotIndex < slotTimes.length; slotIndex++) {
                // H9 - Check if session fits on this day
                if (slotIndex + blockSize > slotTimes.length) {
                    continue;
                }

                // Find the timeslot
                LocalDateTime start = LocalDateTime.of(date, slotTimes[slotIndex]);
                Timeslot startTs = Timeslot.find("startDateTime", start).firstResult();
                if (startTs == null) {
                    continue;
                }

                // Calculate priority (earlier slots = higher priority)
                int priority = slotIndex + 1;

                candidates.add(new SlotCandidate(
                    startTs,
                    blockSize,
                    true,  // preferredDay (we're on the assigned day)
                    slotIndex == 0,  // preferredSlot (first slot)
                    priority
                ));
            }

            date = date.plusDays(1);
        }

        // Log summary of slot generation
        LOG.infof("[TestRunner] Session (group=%s, day=%s): checked %d dates, %d wrong day, %d holidays, %d stage, %d unavailable, %d with slots, generated %d candidates",
            group != null ? group.name : "null", effectiveDay, datesChecked, skippedWrongDay, skippedHoliday, skippedStage, skippedUnavailable, datesWithSlots, candidates.size());

        // Sort by priority
        Collections.sort(candidates);

        return candidates;
    }

    /**
     * Check all occupied slots for conflicts using PreAssignmentContext.
     *
     * @param session The LabSession being assigned
     * @param room The room being assigned
     * @param occupiedSlots List of timeslots the session will occupy
     * @return Optional ConflictInfo if conflict found, empty if no conflict
     */
    private Optional<ConflictInfo> checkConflicts(LabSession session, Room room, List<Timeslot> occupiedSlots) {
        Teacher teacher = session.getTeacher();
        StudentGroup group = session.getStudentGroup();

        for (Timeslot ts : occupiedSlots) {
            // H1 - Teacher conflict
            if (context.hasTeacherConflict(teacher, ts)) {
                return Optional.of(new ConflictInfo(
                    "H1_TEACHER",
                    teacher,
                    null,
                    null,
                    ts
                ));
            }

            // H2 - Room conflict
            if (context.hasRoomConflict(room, ts)) {
                return Optional.of(new ConflictInfo(
                    "H2_ROOM",
                    null,
                    room,
                    null,
                    ts
                ));
            }

            // H3 - Student group conflict
            if (context.hasStudentGroupConflict(group, ts)) {
                return Optional.of(new ConflictInfo(
                    "H3_GROUP",
                    null,
                    null,
                    group,
                    ts
                ));
            }

            // H5 - Teacher unavailability at this timeslot
            if (context.hasH5Violation(teacher, ts)) {
                return Optional.of(new ConflictInfo(
                    "H5_UNAVAILABLE",
                    teacher,
                    null,
                    null,
                    ts
                ));
            }
        }

        // No conflicts
        return Optional.empty();
    }

    /**
     * Find a room for the session based on its course's required room tags.
     *
     * @param session The LabSession needing a room
     * @param config Test configuration with roomTag
     * @return A suitable room, or null if none found
     */
    private Room findRoom(LabSession session, TestConfig config) {
        // If session already has a room assigned, use it
        if (session.room != null) {
            return session.room;
        }

        // Find room by tag from config (using JOIN for ElementCollection)
        if (config.roomTag() != null) {
            return Room.find("SELECT r FROM Room r JOIN r.tags t WHERE t = ?1", config.roomTag()).firstResult();
        }

        // Find room from course's required tags
        if (session.course != null && session.course.requiredRoomTags != null && !session.course.requiredRoomTags.isEmpty()) {
            String tag = session.course.requiredRoomTags.iterator().next();
            return Room.find("SELECT r FROM Room r JOIN r.tags t WHERE t = ?1", tag).firstResult();
        }

        // No room found
        return null;
    }

    /**
     * Get all occupied timeslots for a block starting at the given slot.
     *
     * @param startSlot The starting timeslot
     * @param blockSize Number of consecutive slots
     * @return List of occupied timeslots
     */
    private List<Timeslot> getOccupiedSlots(Timeslot startSlot, int blockSize) {
        List<Timeslot> occupied = new ArrayList<>();
        LocalDate date = startSlot.getDate();
        LocalTime[] slotTimes = SlotCandidate.getSlotTimes(date.getDayOfWeek());

        int startHour = startSlot.startDateTime.getHour();
        int startSlotIndex = -1;
        for (int i = 0; i < slotTimes.length; i++) {
            if (slotTimes[i].getHour() == startHour) {
                startSlotIndex = i;
                break;
            }
        }

        if (startSlotIndex == -1) {
            return occupied;
        }

        for (int i = 0; i < blockSize && startSlotIndex + i < slotTimes.length; i++) {
            LocalDateTime start = LocalDateTime.of(date, slotTimes[startSlotIndex + i]);
            Timeslot ts = Timeslot.find("startDateTime", start).firstResult();
            if (ts != null) {
                occupied.add(ts);
            }
        }

        return occupied;
    }

    /**
     * Count how many days of the week a teacher is available (not blocked by VACATION).
     * External teachers (like T28) will have 1 day. Internal teachers will have 5 days.
     */
    private int countValidDaysForTeacher(Teacher teacher, TestConfig config) {
        if (teacher == null) {
            return 5; // Default to all days
        }

        int validDays = 0;
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                continue; // Skip weekend
            }
            // Check if teacher has any VACATION blocking this entire day
            // Use the period start date to check availability
            LocalDate testDate = config.periodStart();
            // Find first occurrence of this day in the period
            while (testDate.getDayOfWeek() != day && !testDate.isAfter(config.periodEnd())) {
                testDate = testDate.plusDays(1);
            }
            // If this day is not blocked by VACATION, it's a valid day
            if (!isTeacherUnavailable(teacher, testDate)) {
                validDays++;
            }
        }
        return validDays;
    }

    /**
     * Check if teacher is unavailable (VACATION/SICK) on the given date.
     */
    private boolean isTeacherUnavailable(Teacher teacher, LocalDate date) {
        if (teacher == null) {
            return false;
        }

        // Use PreAssignmentContext for H5 check
        // Create a dummy timeslot to check
        LocalDateTime start = date.atTime(8, 0);
        Timeslot ts = Timeslot.find("startDateTime", start).firstResult();
        if (ts == null) {
            return false;
        }

        return context.hasH5Violation(teacher, ts);
    }

    /**
     * Track a session assignment in the context for subsequent conflict checks.
     */
    private void trackSessionAssignment(LabSession session, SlotAssignment assignment) {
        // Track each lesson in the session
        for (Lesson lesson : session.lessons) {
            // Create a temporary lesson with the assignment for tracking
            Lesson tempLesson = new Lesson();
            tempLesson.id = lesson.id;
            tempLesson.course = lesson.course;
            tempLesson.timeslot = assignment.startTimeslot();
            tempLesson.room = assignment.room();

            context.trackLesson(tempLesson);
        }
    }

    /**
     * Cleanup tracked sessions from context after test run.
     */
    private void cleanupTrackedSessions(List<LabSession> trackedSessions) {
        for (LabSession session : trackedSessions) {
            for (Lesson lesson : session.lessons) {
                context.untrackLesson(lesson);
            }
        }
    }

    /**
     * Build failure analysis from the conflict info.
     */
    private FailureAnalysis buildFailureAnalysis(RotationCandidate candidate,
                                                  BlockKey failedKey,
                                                  LabSession failedSession,
                                                  DayOfWeek assignedDay,
                                                  TestConfig config) {
        Teacher teacher = failedSession.getTeacher();
        StudentGroup group = failedSession.getStudentGroup();

        // Build conflict patterns
        List<FailureAnalysis.ConflictPattern> patterns = new ArrayList<>();

        // Check what caused the failure
        List<SlotCandidate> slots = generateSlotCandidates(failedSession, assignedDay, config);
        int h1Count = 0, h2Count = 0, h3Count = 0, h5Count = 0;
        List<String> examples = new ArrayList<>();

        for (SlotCandidate slot : slots) {
            Room room = findRoom(failedSession, config);
            if (room == null) continue;

            List<Timeslot> occupiedSlots = getOccupiedSlots(slot.startSlot(), failedSession.lessons.size());
            Optional<ConflictInfo> conflictOpt = checkConflicts(failedSession, room, occupiedSlots);

            if (conflictOpt.isPresent()) {
                ConflictInfo conflict = conflictOpt.get();
                switch (conflict.type()) {
                    case "H1_TEACHER" -> h1Count++;
                    case "H2_ROOM" -> h2Count++;
                    case "H3_GROUP" -> h3Count++;
                    case "H5_UNAVAILABLE" -> h5Count++;
                }
                if (examples.size() < 3) {
                    examples.add(String.format("%s at %s", conflict.type(), conflict.conflictingTimeslot()));
                }
            }
        }

        // Add dominant conflict pattern
        if (h1Count > 0) {
            patterns.add(new FailureAnalysis.ConflictPattern(
                "TEACHER_CONFLICT",
                String.format("Teacher %s has conflicts at %d slots", teacher.name, h1Count),
                h1Count,
                examples
            ));
        }
        if (h2Count > 0) {
            patterns.add(new FailureAnalysis.ConflictPattern(
                "ROOM_CONFLICT",
                String.format("Room has conflicts at %d slots", h2Count),
                h2Count,
                examples
            ));
        }
        if (h3Count > 0) {
            patterns.add(new FailureAnalysis.ConflictPattern(
                "GROUP_CONFLICT",
                String.format("Student group %s has conflicts at %d slots", group.name, h3Count),
                h3Count,
                examples
            ));
        }
        if (h5Count > 0) {
            patterns.add(new FailureAnalysis.ConflictPattern(
                "TEACHER_UNAVAILABLE",
                String.format("Teacher %s is unavailable at %d slots", teacher.name, h5Count),
                h5Count,
                examples
            ));
        }

        // Build suggestion
        String suggestion = buildSuggestion(patterns, assignedDay, teacher, group);

        Set<Long> teacherIds = teacher != null ? Set.of(teacher.id) : Set.of();
        Set<Long> groupIds = group != null ? Set.of(group.id) : Set.of();

        return new FailureAnalysis(
            "1a-ROTATION-TEST",
            "No valid slot found for session on " + assignedDay,
            patterns,
            teacherIds,
            groupIds,
            suggestion
        );
    }

    /**
     * Build a human-readable suggestion for resolving the failure.
     */
    private String buildSuggestion(List<FailureAnalysis.ConflictPattern> patterns,
                                   DayOfWeek day, Teacher teacher, StudentGroup group) {
        if (patterns.isEmpty()) {
            return "Consider adding more dates to the rotation period or checking for data issues.";
        }

        FailureAnalysis.ConflictPattern dominant = patterns.stream()
            .max((a, b) -> Integer.compare(a.occurrenceCount, b.occurrenceCount))
            .orElse(patterns.get(0));

        return switch (dominant.type) {
            case "TEACHER_CONFLICT" ->
                String.format("Teacher %s has too many conflicts on %s. Consider assigning to a different day.", teacher.name, day);
            case "ROOM_CONFLICT" ->
                String.format("The lab room is overbooked on %s. Consider spreading rotations across more days.", day);
            case "GROUP_CONFLICT" ->
                String.format("Student group %s has too many conflicts on %s. Consider reordering assignments.", group.name, day);
            case "TEACHER_UNAVAILABLE" ->
                String.format("Teacher %s is unavailable on %s. Check vacation/sick records.", teacher.name, day);
            default ->
                "Review the conflict patterns and adjust rotation assignments accordingly.";
        };
    }

    // ─── Nested records ─────────────────────────────────────────

    /** Result of testing a rotation candidate. */
    public record TestResult(
        boolean success,
        RotationCandidate candidate,
        List<SlotAssignment> assignments,
        BlockKey failedBlock,
        String failureReason,
        FailureAnalysis analysis
    ) {
        public static TestResult success(RotationCandidate candidate, List<SlotAssignment> assignments) {
            return new TestResult(true, candidate, assignments, null, null, null);
        }

        public static TestResult failure(RotationCandidate candidate,
                                          BlockKey failedBlock,
                                          String failureReason,
                                          FailureAnalysis analysis) {
            return new TestResult(false, candidate, List.of(), failedBlock, failureReason, analysis);
        }
    }

    /** A successful slot assignment for a lab session. */
    public record SlotAssignment(
        BlockKey key,
        LabSession session,
        Timeslot startTimeslot,
        Room room,
        List<Timeslot> occupiedTimeslots
    ) {}

    /** Information about a conflict. */
    public record ConflictInfo(
        String type,  // "H1_TEACHER", "H2_ROOM", "H3_GROUP", "H5_UNAVAILABLE"
        Teacher conflictingTeacher,
        Room conflictingRoom,
        StudentGroup conflictingGroup,
        Timeslot conflictingTimeslot
    ) {}

    /** Configuration for test runs. */
    public record TestConfig(
        LocalDate periodStart,
        LocalDate periodEnd,
        Set<LocalDate> holidays,
        List<StagePattern> stagePatterns,
        String roomTag
    ) {
        /**
         * Create a TestConfig from the given parameters.
         */
        public static TestConfig of(LocalDate periodStart, LocalDate periodEnd,
                                    Set<LocalDate> holidays, List<StagePattern> stagePatterns,
                                    String roomTag) {
            return new TestConfig(
                periodStart,
                periodEnd,
                holidays != null ? holidays : Set.of(),
                stagePatterns != null ? stagePatterns : List.of(),
                roomTag
            );
        }

        /**
         * Create a TestConfig by loading holidays and stage patterns from the database.
         */
        public static TestConfig fromDatabase(LocalDate periodStart, LocalDate periodEnd, String roomTag) {
            Set<LocalDate> holidays = new HashSet<>();
            List<SchoolHoliday> holidayEntities = SchoolHoliday.listAll();
            for (SchoolHoliday h : holidayEntities) {
                if (h.date != null) {
                    holidays.add(h.date);
                }
            }

            List<StagePattern> stagePatterns = StagePattern.listAll();

            return new TestConfig(periodStart, periodEnd, holidays, stagePatterns, roomTag);
        }
    }

    /**
     * Check if ANY room with the given tag has available capacity for the specified day.
     * Uses pre-loaded room data and in-memory slot tracking - NO database queries.
     *
     * @param roomTag The room tag to check (e.g., "informatica")
     * @param day The day of week to check
     * @param periodStart Start of the period
     * @param periodEnd End of the period
     * @param config Test configuration
     * @return true if at least one room has ANY available slot, false if all are full
     */
    private boolean hasRoomCapacity(String roomTag, DayOfWeek day, LocalDate periodStart,
            LocalDate periodEnd, TestConfig config) {

        List<Room> rooms = context.getRoomsByTag(roomTag);
        if (rooms.isEmpty()) {
            LOG.debugf("[TestRunner] No rooms found for tag %s, allowing iteration", roomTag);
            return true;
        }

        // Count total slots available for this day in the period
        int totalSlots = countTotalSlotsForDay(day, periodStart, periodEnd, config);
        if (totalSlots == 0) {
            LOG.debugf("[TestRunner] No slots available for %s in period", day);
            return false;
        }

        // Check if ANY room has available capacity using existing roomSlots map
        for (Room room : rooms) {
            int occupied = countOccupiedForRoomAndDay(room.id, periodStart, periodEnd);
            if (occupied < totalSlots) {
                LOG.debugf("[TestRunner] Room %s has capacity: %d/%d slots used",
                    room.name, occupied, totalSlots);
                return true;
            }
        }

        LOG.debugf("[TestRunner] All %d rooms for tag %s are at capacity on %s",
            rooms.size(), roomTag, day);
        return false;
    }

    /**
     * Count total slots available for a day of week in the period (excluding
     * holidays). Uniform 8 slots every day (TASK-343) - single source of
     * truth: TimeslotSequence.SLOT_START_TIMES.
     */
    private int countTotalSlotsForDay(DayOfWeek day, LocalDate periodStart,
            LocalDate periodEnd, TestConfig config) {
        int count = 0;
        LocalDate date = periodStart;
        int slotsPerDay = TimeslotSequence.SLOTS_PER_DAY;

        while (!date.isAfter(periodEnd)) {
            if (date.getDayOfWeek() == day && !config.holidays().contains(date)) {
                count += slotsPerDay;
            }
            date = date.plusDays(1);
        }

        return count;
    }

    /**
     * Count occupied slots for a room in the period using pre-loaded roomSlots map.
     */
    private int countOccupiedForRoomAndDay(Long roomId, LocalDate periodStart, LocalDate periodEnd) {
        int count = 0;
        String prefix = roomId + "|";

        for (String key : context.getRoomSlots().keySet()) {
            if (key.startsWith(prefix)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Clear the room capacity cache.
     * Note: Capacity check now uses PreAssignmentContext, so this is a no-op.
     * Kept for backward compatibility.
     */
    public void clearCapacityCache() {
        roomCapacityCache.clear();
        LOG.debug("[TestRunner] Capacity check now uses PreAssignmentContext");
    }
}
