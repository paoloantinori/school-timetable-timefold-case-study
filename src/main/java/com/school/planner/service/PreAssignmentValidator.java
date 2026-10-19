package com.school.planner.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;

/**
 * Validator for checking constraint conflicts before pinning entities during pre-assignment.
 *
 * This ensures that pre-assignment operations do not create violations of hard constraints:
 * - H1: Teacher conflict
 * - H2: Room conflict
 * - H3: Student group conflict
 */
@ApplicationScoped
public class PreAssignmentValidator {

    private static final Logger LOG = Logger.getLogger(PreAssignmentValidator.class);

    @jakarta.inject.Inject
    PreAssignmentContext context;

    /**
     * Check if teacher has any pinned Lesson at the given timeslot.
     * H1 (Teacher conflict) validation.
     *
     * Note: LabBlocks were deprecated in TASK-228. All labs are now pinned Lessons.
     *
     * @param teacher the teacher to check
     * @param timeslot the timeslot to check
     * @return true if there is a conflict (teacher already has a pinned entity at this timeslot)
     */
    public boolean hasTeacherConflict(Teacher teacher, Timeslot timeslot) {
        if (teacher == null || timeslot == null) {
            return false;
        }

        // Check for pinned Lesson at this timeslot (includes lab lessons with isLabHour=true)
        long lessonCount = Lesson.find(
                "course.teacher = ?1 and timeslot = ?2 and pinned = true",
                teacher, timeslot).count();
        if (lessonCount > 0) {
            LOG.debugf("Teacher conflict: %s has pinned Lesson at %s", teacher.name, timeslot);
            return true;
        }

        return false;
    }

    /**
     * Check if room has any pinned Lesson at the given timeslot.
     * H2 (Room conflict) validation.
     *
     * Note: LabBlocks were deprecated in TASK-228. All labs are now pinned Lessons.
     *
     * @param room the room to check
     * @param timeslot the timeslot to check
     * @return true if there is a conflict (room already has a pinned entity at this timeslot)
     */
    public boolean hasRoomConflict(Room room, Timeslot timeslot) {
        if (room == null || timeslot == null) {
            return false;
        }

        // Check for pinned Lesson at this timeslot in this room
        long lessonCount = Lesson.find(
                "room = ?1 and timeslot = ?2 and pinned = true",
                room, timeslot).count();
        if (lessonCount > 0) {
            LOG.debugf("Room conflict: %s has pinned Lesson at %s", room.name, timeslot);
            return true;
        }

        return false;
    }

    /**
     * Check if student group has any pinned LabBlock OR Lesson at the given timeslot.
     * H3 (Student group conflict) validation.
     *
     * @param group the student group to check
     * @param timeslot the timeslot to check
     * @return true if there is a conflict (group already has a pinned entity at this timeslot)
     */
    public boolean hasStudentGroupConflict(StudentGroup group, Timeslot timeslot) {
        if (group == null || timeslot == null) {
            return false;
        }

        // Check for pinned Lesson at this timeslot for this group
        long lessonCount = Lesson.find(
                "course.studentGroup = ?1 and timeslot = ?2 and pinned = true",
                group, timeslot).count();
        if (lessonCount > 0) {
            LOG.debugf("Student group conflict: %s has pinned Lesson at %s", group.name, timeslot);
            return true;
        }

        return false;
    }

    // ─── Additional Hard Constraint Validations ─────────────────────────

    /**
     * H5: Check if teacher is unavailable (VACATION or SICK) at the given timeslot.
     *
     * @param teacher the teacher to check
     * @param timeslot the timeslot to check
     * @return true if teacher has VACATION or SICK availability overlapping this slot
     */
    public boolean hasH5Violation(Teacher teacher, Timeslot timeslot) {
        if (teacher == null || timeslot == null) {
            return false;
        }

        long count = TeacherAvailability.count(
                "teacher = ?1 and type in (?2, ?3) " +
                "and startDateTime < ?4 and endDateTime > ?5",
                teacher, AvailabilityType.VACATION, AvailabilityType.SICK,
                timeslot.endDateTime, timeslot.startDateTime);
        if (count > 0) {
            LOG.debugf("H5 violation: Teacher %s unavailable at %s", teacher.name, timeslot);
            return true;
        }
        return false;
    }

    /**
     * H5: Check if teacher is unavailable for any slot occupied by a LabBlock.
     *
     * @param block the LabBlock to check
     * Note: hasH5Violation(LabBlock, Timeslot) was removed - LabBlocks deprecated in TASK-228

    // Note: hasH9Violation(LabBlock, Timeslot) was removed - LabBlocks deprecated in TASK-228
    // All labs are now pinned Lessons. H9 constraint now applies at Lesson level.

    /**
     * M15: Same-day same-subject lessons must be consecutive (no gaps).
     * If there are already pinned same-subject lessons on the same day,
     * the proposed slot must be adjacent to them.
     *
     * @param lesson the lesson to check
     * @param proposedTimeslot the proposed timeslot
     * @return true if this slot would create a gap with existing same-subject lessons
     */
    public boolean hasH15Violation(Lesson lesson, Timeslot proposedTimeslot) {
        if (lesson == null || proposedTimeslot == null) {
            return false;
        }

        // Lab lessons are exempt from M15 (handled by LabBlock constraints)
        if (lesson.isLab()) {
            return false;
        }

        StudentGroup group = lesson.getStudentGroup();
        String subject = lesson.getSubject();
        if (group == null || subject == null) {
            return false;
        }

        LocalDate date = proposedTimeslot.getDate();

        // Find all pinned lessons of same subject on same day for same group
        // Use date range: startDateTime >= startOfDay AND startDateTime < startOfNextDay
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<Lesson> sameSubjectLessons = Lesson.find(
                "course.studentGroup = ?1 and " +
                "course.subject = ?2 and " +
                "timeslot.startDateTime >= ?3 and " +
                "timeslot.startDateTime < ?4 and " +
                "pinned = true and " +
                "id != ?5",
                group, subject, dayStart, dayEnd, lesson.id).list();

        if (sameSubjectLessons.isEmpty()) {
            // No existing same-subject lessons, no M15 concern
            return false;
        }

        // Check if proposed slot is adjacent to any existing same-subject lesson
        LocalTime proposedStart = proposedTimeslot.startDateTime.toLocalTime();
        LocalTime proposedEnd = proposedTimeslot.endDateTime.toLocalTime();

        for (Lesson existing : sameSubjectLessons) {
            // Check adjacency: proposed starts when existing ends, or vice versa
            LocalTime existingEnd = existing.timeslot.endDateTime.toLocalTime();
            LocalTime existingStart = existing.timeslot.startDateTime.toLocalTime();
            if (proposedStart.equals(existingEnd) ||
                proposedEnd.equals(existingStart)) {
                // Adjacent! This is good, no M15 violation
                return false;
            }
        }

        // Not adjacent to any existing same-subject lesson → would create gap
        LOG.debugf("M15 violation would occur: %s for group %s on %s - slot %s-%s not adjacent to %d existing lessons",
                subject, group.name, date, proposedStart, proposedEnd, Integer.valueOf(sameSubjectLessons.size()));
        return true;
    }

    /**
     * Find an M15-safe timeslot for this lesson on the given date.
     * Returns a slot that is adjacent to existing same-subject lessons, or first available if none exist.
     *
     * @param lesson the lesson to place
     * @param date the target date
     * @param availableSlots all available slots on that date
     * @return an M15-safe slot, or null if no adjacent slot is available
     */
    public Timeslot findH15SafeSlot(Lesson lesson, LocalDate date, List<Timeslot> availableSlots) {
        if (lesson == null || lesson.isLab() || availableSlots == null || availableSlots.isEmpty()) {
            return null;
        }

        StudentGroup group = lesson.getStudentGroup();
        String subject = lesson.getSubject();
        if (group == null || subject == null) {
            return availableSlots.get(0); // No M15 concern, return first available
        }

        // Find existing same-subject lessons on this day
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<Lesson> sameSubjectLessons = Lesson.find(
                "course.studentGroup = ?1 and " +
                "course.subject = ?2 and " +
                "timeslot.startDateTime >= ?3 and " +
                "timeslot.startDateTime < ?4 and " +
                "pinned = true and " +
                "id != ?5",
                group, subject, dayStart, dayEnd, lesson.id).list();

        if (sameSubjectLessons.isEmpty()) {
            // No existing same-subject lessons, any slot is M15-safe
            return availableSlots.get(0);
        }

        // Find the time boundaries of existing lessons
        LocalTime earliestEnd = sameSubjectLessons.stream()
                .map(l -> l.timeslot.endDateTime.toLocalTime())
                .min(LocalTime::compareTo)
                .orElse(null);
        LocalTime latestStart = sameSubjectLessons.stream()
                .map(l -> l.timeslot.startDateTime.toLocalTime())
                .max(LocalTime::compareTo)
                .orElse(null);

        // Prefer slots adjacent to existing cluster
        for (Timeslot slot : availableSlots) {
            LocalTime slotStart = slot.startDateTime.toLocalTime();
            LocalTime slotEnd = slot.endDateTime.toLocalTime();
            // Check if slot starts where earliest lesson ends (extend downward)
            if (earliestEnd != null && slotStart.equals(earliestEnd)) {
                return slot;
            }
            // Check if slot ends where latest lesson starts (extend upward)
            if (latestStart != null && slotEnd.equals(latestStart)) {
                return slot;
            }
        }

        // No adjacent slot available
        return null;
    }

    /**
     * H16: Consecutive same-subject lessons must use same teacher and room.
     * If this lesson would be consecutive with an existing same-subject lesson,
     * they must have the same teacher and room.
     *
     * @param lesson the lesson to check
     * @param proposedTimeslot the proposed timeslot
     * @param proposedRoom the proposed room
     * @return true if this would create an H16 violation
     */
    public boolean hasH16Violation(Lesson lesson, Timeslot proposedTimeslot, Room proposedRoom) {
        if (lesson == null || proposedTimeslot == null) {
            return false;
        }

        // Lab lessons are exempt
        if (lesson.isLab()) {
            return false;
        }

        StudentGroup group = lesson.getStudentGroup();
        String subject = lesson.getSubject();
        Teacher teacher = lesson.getTeacher();
        if (group == null || subject == null || teacher == null) {
            return false;
        }

        LocalDate date = proposedTimeslot.getDate();

        // Find pinned same-subject lessons on same day
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<Lesson> adjacentLessons = Lesson.find(
                "course.studentGroup = ?1 and course.subject = ?2 and " +
                "timeslot.startDateTime >= ?3 and timeslot.startDateTime < ?4 " +
                "and pinned = true and id != ?5",
                group, subject, dayStart, dayEnd, lesson.id).list();

        for (Lesson existing : adjacentLessons) {
            // Check if they would be consecutive
            LocalTime proposedStart = proposedTimeslot.startDateTime.toLocalTime();
            LocalTime proposedEnd = proposedTimeslot.endDateTime.toLocalTime();
            LocalTime existingEnd = existing.timeslot.endDateTime.toLocalTime();
            LocalTime existingStart = existing.timeslot.startDateTime.toLocalTime();

            boolean wouldBeConsecutive =
                    proposedStart.equals(existingEnd) ||
                    proposedEnd.equals(existingStart);

            if (wouldBeConsecutive) {
                // H16: Must have same teacher and room
                if (!teacher.id.equals(existing.getTeacher().id)) {
                    LOG.debugf("H16 violation: Different teachers for consecutive %s lessons", subject);
                    return true;
                }
                if (proposedRoom != null && existing.room != null &&
                    !proposedRoom.id.equals(existing.room.id)) {
                    LOG.debugf("H16 violation: Different rooms for consecutive %s lessons", subject);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * H-M2: Room suitability - lab course must be in room with matching tags.
     *
     * @param lesson the lesson to check
     * @param room the proposed room
     * @return true if room doesn't have required tags
     */
    public boolean hasRoomSuitabilityViolation(Lesson lesson, Room room) {
        if (lesson == null || lesson.course == null) {
            return false;
        }

        List<String> requiredTags = lesson.course.requiredRoomTags;
        if (requiredTags == null || requiredTags.isEmpty()) {
            return false; // No room requirements
        }
        if (room == null || room.tags == null) {
            LOG.debugf("H-M2 violation: No room/tags for lesson %d requiring %s",
                    Long.valueOf(lesson.id), requiredTags.toString());
            return true;
        }

        // room.tags is List<String>
        List<String> roomTags = room.tags;

        for (String requiredTag : requiredTags) {
            if (requiredTag != null && !roomTags.contains(requiredTag)) {
                LOG.debugf("H-M2 violation: Room %s missing required tag '%s' for lesson %d",
                        room.name, requiredTag, Long.valueOf(lesson.id));
                return true;
            }
        }
        return false;
    }

    // Note: hasRoomSuitabilityViolation(LabBlock, Room) was removed - LabBlocks deprecated in TASK-228
    // All labs are now pinned Lessons. Use hasRoomSuitabilityViolation(Lesson, Room) instead.

    // Note: canPin(LabBlock, Timeslot, Room) was removed - LabBlocks deprecated in TASK-228
    // All labs are now pinned Lessons. Pre-assignment works at Lesson level.


    /**
     * H11: Max student group daily hours.
     * Check if adding this lesson would exceed the group's maxHoursPerDay limit.
     *
     * @param lesson the lesson to check
     * @param proposedTimeslot the proposed timeslot
     * @return true if this lesson would exceed maxHoursPerDay for the group
     */
    public boolean hasH11Violation(Lesson lesson, Timeslot proposedTimeslot) {
        if (lesson == null || proposedTimeslot == null) {
            return false;
        }

        StudentGroup group = lesson.getStudentGroup();
        if (group == null || group.maxHoursPerDay == null) {
            // No limit set, no H11 violation possible
            return false;
        }

        // T28 EXCEPTION (TASK-245): External teacher lessons are exempt from H11
        // T28 (external=true) creates 6-hour days for 5-hour groups (G10, 4 FA)
        if (lesson.course != null && lesson.course.teacher != null
                && lesson.course.teacher.external) {
            return false;  // External teacher lessons don't count toward daily limit
        }

        LocalDate date = proposedTimeslot.getDate();

        // Count existing pinned lessons for this group on this date
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        long existingHours = Lesson.count(
                "course.studentGroup = ?1 and " +
                "timeslot.startDateTime >= ?2 and " +
                "timeslot.startDateTime < ?3 and " +
                "pinned = true",
                group, dayStart, dayEnd);

        // Adding this lesson would exceed the limit
        if (existingHours >= group.maxHoursPerDay) {
            LOG.debugf("H11 violation: Group %s already has %d hours on %s (max=%d)",
                    group.name, existingHours, date, group.maxHoursPerDay);
            return true;
        }

        return false;
    }

    // Note: hasH11Violation(LabBlock, Timeslot) was removed - LabBlocks deprecated in TASK-228

    /**
     * Check if a lesson can be pinned without violating constraints.
     * Validates H1, H2, H3, H5, H11, M15, H16, H-M2 constraints.
     *
     * @param lesson the lesson to check
     * @param timeslot the proposed timeslot
     * @param room the proposed room
     * @return true if the lesson can be pinned safely
     */
    public boolean canPin(Lesson lesson, Timeslot timeslot, Room room) {
        if (lesson == null || timeslot == null || room == null) {
            return false;
        }

        Teacher teacher = lesson.getTeacher();
        StudentGroup group = lesson.getStudentGroup();

        // H1: Teacher conflict
        if (hasTeacherConflict(teacher, timeslot)) {
            LOG.warnf("Cannot pin Lesson %d: H1 teacher %s conflict at %s",
                    lesson.id, teacher != null ? teacher.name : "null", timeslot);
            return false;
        }

        // H2: Room conflict
        if (hasRoomConflict(room, timeslot)) {
            LOG.warnf("Cannot pin Lesson %d: H2 room %s conflict at %s",
                    lesson.id, room.name, timeslot);
            return false;
        }

        // H3: Student group conflict
        if (hasStudentGroupConflict(group, timeslot)) {
            LOG.warnf("Cannot pin Lesson %d: H3 student group %s conflict at %s",
                    lesson.id, group != null ? group.name : "null", timeslot);
            return false;
        }

        // H5: Teacher unavailability
        if (hasH5Violation(teacher, timeslot)) {
            LOG.warnf("Cannot pin Lesson %d: H5 teacher unavailable", lesson.id);
            return false;
        }

        // H11: Max student group daily hours
        if (hasH11Violation(lesson, timeslot)) {
            LOG.warnf("Cannot pin Lesson %d: H11 would exceed maxHoursPerDay for group %s",
                    lesson.id, group != null ? group.name : "null");
            return false;
        }

        // H-M2: Room suitability
        if (hasRoomSuitabilityViolation(lesson, room)) {
            LOG.warnf("Cannot pin Lesson %d: H-M2 wrong room type", lesson.id);
            return false;
        }

        // M15: Same-day same-subject consecutive (no gaps)
        if (hasH15Violation(lesson, timeslot)) {
            LOG.warnf("Cannot pin Lesson %d: M15 would create gap with same-subject lessons", lesson.id);
            return false;
        }

        // H16: Consecutive same-subject teacher-room consistency
        if (hasH16Violation(lesson, timeslot, room)) {
            LOG.warnf("Cannot pin Lesson %d: H16 inconsistent teacher/room with consecutive lesson", lesson.id);
            return false;
        }

        return true;
    }

    public boolean canPinInMemory(Lesson lesson, Timeslot timeslot, Room room) {
        if (lesson == null || timeslot == null || room == null) {
            return false;
        }

        if (!context.isInitialized()) {
            LOG.warn("PreAssignmentContext not initialized - falling back to DB validation");
            return canPin(lesson, timeslot, room);
        }

        Teacher teacher = lesson.getTeacher();
        StudentGroup group = lesson.getStudentGroup();

        // H1: Teacher conflict
        if (context.hasTeacherConflict(teacher, timeslot)) {
            LOG.debugf("Cannot pin Lesson %d (in-memory): H1 teacher %s conflict at %s",
                    lesson.id, teacher != null ? teacher.name : "null", timeslot);
            return false;
        }

        // H2: Room conflict
        if (context.hasRoomConflict(room, timeslot)) {
            LOG.debugf("Cannot pin Lesson %d (in-memory): H2 room %s conflict at %s",
                    lesson.id, room.name, timeslot);
            return false;
        }

        // H3: Student group conflict
        if (context.hasStudentGroupConflict(group, timeslot)) {
            LOG.debugf("Cannot pin Lesson %d (in-memory): H3 student group %s conflict at %s",
                    lesson.id, group != null ? group.name : "null", timeslot);
            return false;
        }

        // H5: Teacher unavailability
        if (context.hasH5Violation(teacher, timeslot)) {
            LOG.debugf("Cannot pin Lesson %d (in-memory): H5 teacher unavailable", lesson.id);
            return false;
        }

        // H11: Max student group daily hours
        if (context.hasH11Violation(lesson, timeslot)) {
            LOG.debugf("Cannot pin Lesson %d (in-memory): H11 would exceed maxHoursPerDay for group %s",
                    lesson.id, group != null ? group.name : "null");
            return false;
        }

        // H-M2: Room suitability (still needs DB for course tags, but quick)
        if (hasRoomSuitabilityViolation(lesson, room)) {
            LOG.debugf("Cannot pin Lesson %d (in-memory): H-M2 wrong room type", lesson.id);
            return false;
        }

        // M15: Same-day same-subject consecutive (no gaps)
        if (context.hasH15Violation(lesson, timeslot)) {
            LOG.debugf("Cannot pin Lesson %d (in-memory): M15 would create gap with same-subject lessons", lesson.id);
            return false;
        }

        // H16: Consecutive same-subject teacher-room consistency
        if (context.hasH16Violation(lesson, timeslot, room)) {
            LOG.debugf("Cannot pin Lesson %d (in-memory): H16 inconsistent teacher/room with consecutive lesson", lesson.id);
            return false;
        }

        return true;
    }

    /**
     * TASK-435: hard pin veto for the constrained-teacher phase. Validates the HARD constraints
     * a (timeslot, room) placement can violate (H1 teacher, H2 room, H3 group, H5 availability,
     * H11 group daily hours, H-M2 room suitability) but SKIPS the medium-shape checks M15
     * (same-subject no-gaps) and H16 (same-subject teacher-room consistency).
     *
     * <p>Why the medium skip: the pin reproduces the school's golden timetable, which LEGITIMATELY
     * splits a teacher's same-subject day around the lunch break (e.g. T01 G10:
     * 08:00-13:00 + 14:30-15:30, a 30-min gap M15/H16 penalise). Those are MEDIUM violations the
     * golden carries by design; vetoing on them would refuse a golden-faithful placement.
     *
     * <p>Why the room is required: the pin sets {@code pinned=true}, which (via {@code @PlanningPin}
     * on {@code isPinned()}) makes BOTH timeslot and room immovable. A pin that left
     * {@code room=null} would leave the lesson permanently room-less (the room phase 4/5 cannot
     * assign a room to a pinned entity), so the pin must assign the room itself (the caller passes
     * the course's {@code findRoomForLesson} result, shared across the day's block so H16b is
     * satisfied by construction).
     */
    public boolean canPinHardConstraints(Lesson lesson, Timeslot timeslot, Room room) {
        if (lesson == null || timeslot == null || room == null) {
            return false;
        }
        if (!context.isInitialized()) {
            // Defensive: the production run() path always calls context.initialize() before
            // Phase 0, so this is dead in normal flow. If invoked cold, REFUSE the pin (leave it
            // for the solver) rather than blindly approving an unvalidated placement.
            LOG.warnf("canPinHardConstraints: PreAssignmentContext not initialized - refusing pin for Lesson %d",
                    lesson.id);
            return false;
        }
        Teacher teacher = lesson.getTeacher();
        StudentGroup group = lesson.getStudentGroup();
        if (context.hasTeacherConflict(teacher, timeslot)) {
            return false;
        }
        if (context.hasRoomConflict(room, timeslot)) {
            return false;
        }
        if (context.hasStudentGroupConflict(group, timeslot)) {
            return false;
        }
        if (context.hasH5Violation(teacher, timeslot)) {
            return false;
        }
        if (context.hasH11Violation(lesson, timeslot)) {
            return false;
        }
        if (hasRoomSuitabilityViolation(lesson, room)) {
            return false;
        }
        return true;
    }
}
