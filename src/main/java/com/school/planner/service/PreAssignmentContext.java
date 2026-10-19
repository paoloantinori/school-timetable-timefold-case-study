package com.school.planner.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Lesson;
import com.school.planner.service.LabSession;
import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;

/**
 * In-memory context for pre-assignment validation.
 *
 * Preloads ALL pinned Lessons into memory at initialization,
 * then provides O(1) conflict checks without any database queries.
 *
 * Note: LabBlocks were deprecated in TASK-228 - this context now only handles Lessons.
 *
 * This is CRITICAL for performance: the pre-assignment pipeline runs validations
 * in nested loops (groups x dates x slots x lessons), which could cause 100,000+
 * database queries without this optimization.
 *
 * MUTABILITY: The context is mutable - call trackLesson() after each successful
 * assignment so subsequent validations are accurate.
 */
@ApplicationScoped
public class PreAssignmentContext {

    private static final Logger LOG = Logger.getLogger(PreAssignmentContext.class);

    // ─── Core conflict maps - O(1) lookups ─────────────────────────────────

    /** Key: "{teacherId}|{timeslotId}" -> Set of entity references at this slot */
    private Map<String, Set<EntityRef>> teacherSlots = new HashMap<>();
    /** TASK-502: teacher -> date -> pinned hours, the H7 mirror of groupDailyHours. */
    private Map<Long, Map<LocalDate, Integer>> teacherDailyHours = new HashMap<>();

    /** Key: "{roomId}|{timeslotId}" -> Set of entity references at this slot */
    private Map<String, Set<EntityRef>> roomSlots = new HashMap<>();

    /** Key: "{groupId}|{timeslotId}" -> Set of entity references at this slot */
    private Map<String, Set<EntityRef>> groupSlots = new HashMap<>();

    // ─── Preloaded reference data ─────────────────────────────────

    /** Teacher unavailability (VACATION/SICK) by teacher ID */
    private Map<Long, List<TimeRange>> teacherUnavailability = new HashMap<>();

    /** Same-subject lessons by "{groupId}|{date}|{subject}" for M15/H16 checks */
    private Map<String, List<LessonInfo>> sameSubjectLessonsByDate = new HashMap<>();

    /** Daily lesson count by group for H11 validation. Key: "{groupId}|{date}" -> hour count */
    private Map<String, Integer> groupDailyHours = new HashMap<>();

    /** Rooms indexed by tag for O(1) lookup. Key: tag (e.g., "informatica") */
    private Map<String, List<Room>> roomsByTag = new HashMap<>();

    /** Timeslots indexed by "date|slotIndex" for O(1) lookup.
     *  Key: "YYYY-MM-DD|slotIndex" (e.g., "2025-10-06|0" for 08:00 on Oct 6).
     *  ~1,520 entries for a full school year. */
    private Map<String, Timeslot> timeslotsByDateAndSlot = new HashMap<>();

    /** Flag indicating if context has been initialized */
    private boolean initialized = false;

    // ─── Reference to validator for fallback checks ─────────────────────────────────

    /**
     * Preload all pinned entities and reference data.
     * Call ONCE at the start of pre-assignment.
     */
    public void initialize() {
        LOG.info("PreAssignmentContext: Initializing in-memory conflict tracking...");

        // Clear previous state
        teacherSlots.clear();
        roomSlots.clear();
        groupSlots.clear();
        teacherUnavailability.clear();
        sameSubjectLessonsByDate.clear();
        timeslotsByDateAndSlot.clear();

        // Load all pinned Lessons
        List<Lesson> pinnedLessons = Lesson.list("pinned = true and timeslot is not null");
        LOG.infof("PreAssignmentContext: Loaded %d pinned Lessons", pinnedLessons.size());

        for (Lesson lesson : pinnedLessons) {
            if (lesson.timeslot == null) continue;

            EntityRef ref = new EntityRef(lesson.id, "Lesson");

            // Track teacher slot
            if (lesson.getTeacher() != null) {
                String key = lesson.getTeacher().id + "|" + lesson.timeslot.id;
                teacherSlots.computeIfAbsent(key, k -> new HashSet<>()).add(ref);
            }


            // Track room slot
            if (lesson.room != null) {
                String key = lesson.room.id + "|" + lesson.timeslot.id;
                roomSlots.computeIfAbsent(key, k -> new HashSet<>()).add(ref);
            }


            // Track student group slot
            if (lesson.getStudentGroup() != null) {
                String key = lesson.getStudentGroup().id + "|" + lesson.timeslot.id;
                groupSlots.computeIfAbsent(key, k -> new HashSet<>()).add(ref);
            }


            // Track for H11 (max hours per day)
            if (lesson.getStudentGroup() != null) {
                String dailyKey = lesson.getStudentGroup().id + "|" + lesson.timeslot.getDate();
                groupDailyHours.merge(dailyKey, 1, Integer::sum);
            }

            // Track for M15/H16 (same-subject on same day)
            if (lesson.getStudentGroup() != null && lesson.getSubject() != null) {
                String key = lesson.getStudentGroup().id + "|" + lesson.timeslot.getDate() + "|" + lesson.getSubject();
                LessonInfo info = new LessonInfo(
                        lesson.id,
                        lesson.timeslot,
                        lesson.room,
                        lesson.getTeacher(),
                        lesson.getSubject()
                );
                sameSubjectLessonsByDate.computeIfAbsent(key, k -> new ArrayList<>()).add(info);
            }

        }


        // Note: LabBlocks were deprecated in TASK-228 - all lab rotations are now pinned Lessons

        // Preload H5: Teacher unavailability (VACATION/SICK only)
        List<TeacherAvailability> unavailable = TeacherAvailability.find(
                "type in (?1, ?2)", AvailabilityType.VACATION, AvailabilityType.SICK).list();

        for (TeacherAvailability ta : unavailable) {
            if (ta.teacher == null) continue;
            TimeRange range = new TimeRange(ta.startDateTime, ta.endDateTime);
            teacherUnavailability.computeIfAbsent(ta.teacher.id, k -> new ArrayList<>()).add(range);
        }

        LOG.infof("PreAssignmentContext: Loaded %d teacher unavailability records", unavailable.size());

        // Pre-load rooms indexed by tag for capacity checks
        List<Room> allRooms = Room.listAll();
        for (Room room : allRooms) {
            if (room.tags != null) {
                for (String tag : room.tags) {
                    roomsByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(room);
                }

            }

        }

        LOG.infof("PreAssignmentContext: Loaded %d rooms across %d tags",
            allRooms.size(), roomsByTag.size());

        // Pre-load ALL timeslots indexed by date+slotIndex for O(1) lookup
        // This eliminates Timeslot.find() DB queries during slot placement
        List<Timeslot> allTimeslots = Timeslot.listAll();
        for (Timeslot ts : allTimeslots) {
            if (ts.startDateTime != null) {
                String key = ts.getDate() + "|" + (ts.startDateTime.getHour() - 8);
                timeslotsByDateAndSlot.put(key, ts);
            }

        }

        LOG.infof("PreAssignmentContext: Loaded %d timeslots into lookup map",
            timeslotsByDateAndSlot.size());

        initialized = true;
        LOG.infof("PreAssignmentContext: Initialized with %d teacher slots, %d room slots, %d group slots, %d daily hour entries",
                teacherSlots.size(), roomSlots.size(), groupSlots.size(), groupDailyHours.size());
    }


    /**
     * Track a newly pinned lesson in the context.
     * Call this AFTER each successful assignment so subsequent validations are accurate.
     */
    public void trackLesson(Lesson lesson) {
        if (lesson == null || lesson.timeslot == null) return;

        EntityRef ref = new EntityRef(lesson.id, "Lesson");

        // Track teacher slot
        if (lesson.getTeacher() != null) {
            String key = lesson.getTeacher().id + "|" + lesson.timeslot.id;
            teacherSlots.computeIfAbsent(key, k -> new HashSet<>()).add(ref);
            // TASK-502: H7 bookkeeping (the greedy's teacherCap skip reads this)
            teacherDailyHours.computeIfAbsent(lesson.getTeacher().id, t -> new HashMap<>())
                    .merge(lesson.timeslot.getDate(), 1, Integer::sum);
        }


        // Track room slot
        if (lesson.room != null) {
            String key = lesson.room.id + "|" + lesson.timeslot.id;
            roomSlots.computeIfAbsent(key, k -> new HashSet<>()).add(ref);
        }


        // Track student group slot
        if (lesson.getStudentGroup() != null) {
            String key = lesson.getStudentGroup().id + "|" + lesson.timeslot.id;
            groupSlots.computeIfAbsent(key, k -> new HashSet<>()).add(ref);
        }


        // Track for H11 (max hours per day)
        if (lesson.getStudentGroup() != null) {
            String dailyKey = lesson.getStudentGroup().id + "|" + lesson.timeslot.getDate();
            groupDailyHours.merge(dailyKey, 1, Integer::sum);
        }

        // Track for M15/H16
        if (lesson.getStudentGroup() != null && lesson.getSubject() != null) {
            String key = lesson.getStudentGroup().id + "|" + lesson.timeslot.getDate() + "|" + lesson.getSubject();
            LessonInfo info = new LessonInfo(
                    lesson.id,
                    lesson.timeslot,
                    lesson.room,
                    lesson.getTeacher(),
                    lesson.getSubject()
            );
            sameSubjectLessonsByDate.computeIfAbsent(key, k -> new ArrayList<>()).add(info);
        }

    }



    /**
     * Untrack a lesson - call during backtracking to undo an assignment.
     * Removes the lesson from all conflict maps.
     */
    public void untrackLesson(Lesson lesson) {
        if (lesson == null || lesson.timeslot == null) return;

        EntityRef ref = new EntityRef(lesson.id, "Lesson");

        // Remove from teacher slots
        if (lesson.getTeacher() != null) {
            String key = lesson.getTeacher().id + "|" + lesson.timeslot.id;
            Set<EntityRef> refs = teacherSlots.get(key);
            if (refs != null) {
                refs.remove(ref);
                if (refs.isEmpty()) {
                    teacherSlots.remove(key);
                }

            }
            // TASK-502: H7 bookkeeping mirror
            Map<LocalDate, Integer> byDate = teacherDailyHours.get(lesson.getTeacher().id);
            if (byDate != null) {
                byDate.merge(lesson.timeslot.getDate(), -1, Integer::sum);
            }
        }


        // Remove from room slots
        if (lesson.room != null) {
            String key = lesson.room.id + "|" + lesson.timeslot.id;
            Set<EntityRef> refs = roomSlots.get(key);
            if (refs != null) {
                refs.remove(ref);
                if (refs.isEmpty()) {
                    roomSlots.remove(key);
                }

            }

        }


        // Remove from student group slots
        if (lesson.getStudentGroup() != null) {
            String key = lesson.getStudentGroup().id + "|" + lesson.timeslot.id;
            Set<EntityRef> refs = groupSlots.get(key);
            if (refs != null) {
                refs.remove(ref);
                if (refs.isEmpty()) {
                    groupSlots.remove(key);
                }

            }

        }


        // Remove from H11 tracking
        if (lesson.getStudentGroup() != null) {
            String dailyKey = lesson.getStudentGroup().id + "|" + lesson.timeslot.getDate();
            Integer current = groupDailyHours.get(dailyKey);
            if (current != null) {
                if (current <= 1) {
                    groupDailyHours.remove(dailyKey);
                } else {
                    groupDailyHours.put(dailyKey, current - 1);
                }
            }
        }

        // Remove from same-subject tracking
        if (lesson.getStudentGroup() != null && lesson.getSubject() != null) {
            String key = lesson.getStudentGroup().id + "|" + lesson.timeslot.getDate() + "|" + lesson.getSubject();
            List<LessonInfo> infos = sameSubjectLessonsByDate.get(key);
            if (infos != null) {
                infos.removeIf(info -> info.id.equals(lesson.id));
                if (infos.isEmpty()) {
                    sameSubjectLessonsByDate.remove(key);
                }

            }

        }

    }

    /**
     * Track a newly pinned LabSession in the context.
     * Delegates to trackLesson for each lesson in the session.
     * Call this AFTER each successful session assignment.
     */
    public void trackSession(LabSession session) {
        if (session == null || session.lessons == null) return;
        for (Lesson lesson : session.lessons) {
            trackLesson(lesson);
        }
    }

    /**
     * Untrack a LabSession - call during backtracking to undo an assignment.
     * Delegates to untrackLesson for each lesson in the session.
     */
    public void untrackSession(LabSession session) {
        if (session == null || session.lessons == null) return;
        for (Lesson lesson : session.lessons) {
            untrackLesson(lesson);
        }
    }

    // Note: LabBlocks were deprecated in TASK-228 - trackBlock/untrackBlock removed


    // ─── O(1) Conflict checks ─────────────────────────────────

    /**
     * TASK-516: per (teacher, group) pair, the H7 daily headroom reserved for the
     * pair's quota-bound lab demand this phase is NOT pinning ({@code
     * BudgetReservation}). Empty = no reservation (flag off): the greedy gate
     * reads the teacher's raw cap, byte-identical to the pre-TASK-516 path.
     */
    private Map<RotationTestRunner.BlockKey, Integer> h7ReservationByPair = Map.of();
    /** TASK-518: teacherId -> max reservation over their pairs (see setter). */
    private Map<Long, Integer> h7ReservationByTeacher = Map.of();

    /**
     * TASK-516: publish the phase's reservations (replaces any previous phase's:
     * each phase's pin population defines its own non-enumerated demand).
     */
    public void setH7BudgetReservations(Map<RotationTestRunner.BlockKey, Integer> byPair) {
        this.h7ReservationByPair = byPair == null ? Map.of() : byPair;
        // TASK-518: the TEACHER-level view of the same map (max over pairs). A
        // reservation earned by ONE pair's non-enumerated demand must net the
        // teacher's cap for EVERY pair's pins: without it, the other groups' pins
        // (round 7: G08 + G13, full 6h) consume the very headroom the
        // reservation created and the clipped tails box elsewhere.
        Map<Long, Integer> byTeacher = new HashMap<>();
        for (var e : this.h7ReservationByPair.entrySet()) {
            byTeacher.merge(e.getKey().teacherId(), e.getValue(), Math::max);
        }
        this.h7ReservationByTeacher = byTeacher;
    }

    /** TASK-516: the reserved headroom for one pair, 0 when unarmed. */
    public int reservedH7Hours(Teacher teacher, StudentGroup group) {
        if (teacher == null || group == null || h7ReservationByPair.isEmpty()) {
            return 0;
        }
        return h7ReservationByPair.getOrDefault(
                new RotationTestRunner.BlockKey(teacher.id, group.id), 0);
    }

    /** TASK-518: the strongest reservation of any of the teacher's pairs, 0 when unarmed. */
    public int reservedH7Hours(Teacher teacher) {
        if (teacher == null || h7ReservationByPair.isEmpty()) {
            return 0;
        }
        return h7ReservationByTeacher.getOrDefault(teacher.id, 0);
    }

    /**
     * TASK-502: H7 - the teacher's already-placed hours on the given date
     * (pinned + placed-in-this-phase; backtracking decrements symmetrically).
     */
    public int teacherDailyHours(Teacher teacher, LocalDate date) {
        if (teacher == null || date == null) return 0;
        Map<LocalDate, Integer> byDate = teacherDailyHours.get(teacher.id);
        return byDate == null ? 0 : byDate.getOrDefault(date, 0);
    }

    /**
     * H1: Check if teacher has any conflict at the given timeslot.
     */
    public boolean hasTeacherConflict(Teacher teacher, Timeslot timeslot) {
        if (teacher == null || timeslot == null) return false;
        String key = teacher.id + "|" + timeslot.id;
        return teacherSlots.containsKey(key);
    }


    /**
     * H2: Check if room has any conflict at the given timeslot.
     */
    public boolean hasRoomConflict(Room room, Timeslot timeslot) {
        if (room == null || timeslot == null) return false;
        String key = room.id + "|" + timeslot.id;
        return roomSlots.containsKey(key);
    }


    /**
     * Get the room slots map for external capacity checks.
     * Key format: "roomId|timeslotId"
     */
    public Map<String, Set<EntityRef>> getRoomSlots() {
        return roomSlots;
    }


    /**
     * Get rooms that have a specific tag.
     * @param tag The room tag (e.g., "informatica", "acconciatura")
     * @return List of rooms with this tag, empty list if none
     */
    public List<Room> getRoomsByTag(String tag) {
        return roomsByTag.getOrDefault(tag, List.of());
    }


    /**
     * H3: Check if student group has any conflict at the given timeslot.
     */
    public boolean hasStudentGroupConflict(StudentGroup group, Timeslot timeslot) {
        if (group == null || timeslot == null) return false;
        String key = group.id + "|" + timeslot.id;
        return groupSlots.containsKey(key);
    }


    /**
     * Get a timeslot by date and slot index (0-based, 0 = 08:00).
     * Uses the pre-loaded timeslot map for O(1) lookup — no database query.
     *
     * @param date the date
     * @param slotIndex 0-based slot index (0 = 08:00, 1 = 09:00, etc.)
     * @return the Timeslot, or null if not found
     */
    public Timeslot getTimeslot(LocalDate date, int slotIndex) {
        String key = date + "|" + slotIndex;
        return timeslotsByDateAndSlot.get(key);
    }


    /**
     * Get consecutive occupied timeslots starting from a given date and slot index.
     * Uses the pre-loaded timeslot map — no database queries.
     *
     * @param date the date
     * @param startSlotIndex 0-based start slot index
     * @param blockSize number of consecutive slots
     * @return list of occupied timeslots (may be shorter than blockSize if slots don't exist)
     */
    public List<Timeslot> getOccupiedTimeslots(LocalDate date, int startSlotIndex, int blockSize) {
        List<Timeslot> result = new ArrayList<>();
        for (int i = 0; i < blockSize; i++) {
            Timeslot ts = getTimeslot(date, startSlotIndex + i);
            if (ts != null) {
                result.add(ts);
            }

        }

        return result;
    }


    /**
     * H5: Check if teacher is unavailable (VACATION/SICK) on the given date.
     * Convenience overload that checks against any slot on that date.
     *
     * @param teacher the teacher
     * @param date the date to check
     * @return true if teacher is unavailable on this date
     */
    public boolean hasH5Violation(Teacher teacher, LocalDate date) {
        if (teacher == null || date == null) return false;

        List<TimeRange> ranges = teacherUnavailability.get(teacher.id);
        if (ranges == null) return false;

        // Check if any unavailability range overlaps with this date (any time)
        LocalDateTime dayStart = date.atTime(0, 0);
        LocalDateTime dayEnd = date.plusDays(1).atTime(0, 0);

        for (TimeRange range : ranges) {
            if (range.start.isBefore(dayEnd) && range.end.isAfter(dayStart)) {
                return true;
            }

        }

        return false;
    }


    /**
     * H5: Check if teacher is unavailable (VACATION/SICK) at the given timeslot.
     */
    public boolean hasH5Violation(Teacher teacher, Timeslot timeslot) {
        if (teacher == null || timeslot == null) return false;

        List<TimeRange> ranges = teacherUnavailability.get(teacher.id);
        if (ranges == null) return false;

        for (TimeRange range : ranges) {
            // Overlaps if: range.start < timeslot.end AND range.end > timeslot.start
            if (range.start.isBefore(timeslot.endDateTime) && range.end.isAfter(timeslot.startDateTime)) {
                return true;
            }

        }

        return false;
    }


    /**
     * M15: Same-day same-subject lessons must be consecutive (no gaps).
     * If there are already pinned same-subject lessons on the same day,
     * the proposed slot must be adjacent to them.
     */
    public boolean hasH15Violation(Lesson lesson, Timeslot proposedTimeslot) {
        if (lesson == null || proposedTimeslot == null) return false;

        // Lab lessons are exempt from M15
        if (lesson.isLab()) return false;

        StudentGroup group = lesson.getStudentGroup();
        String subject = lesson.getSubject();
        if (group == null || subject == null) return false;

        LocalDate date = proposedTimeslot.getDate();
        String key = group.id + "|" + date + "|" + subject;

        List<LessonInfo> existing = sameSubjectLessonsByDate.get(key);
        if (existing == null || existing.isEmpty()) {
            return false; // No existing same-subject lessons
        }


        // Check if proposed slot is adjacent to any existing same-subject lesson
        LocalDateTime proposedStart = proposedTimeslot.startDateTime;
        LocalDateTime proposedEnd = proposedTimeslot.endDateTime;

        for (LessonInfo info : existing) {
            // Check adjacency: proposed starts when existing ends, or vice versa
            if (proposedStart.equals(info.timeslot.endDateTime) ||
                proposedEnd.equals(info.timeslot.startDateTime)) {
                // Adjacent! No M15 violation
                return false;
            }

        }


        // Not adjacent to any existing same-subject lesson -> would create gap
        return true;
    }


    /**
     * H16: Consecutive same-subject lessons must use same teacher and room.
     */
    public boolean hasH16Violation(Lesson lesson, Timeslot proposedTimeslot, Room proposedRoom) {
        if (lesson == null || proposedTimeslot == null) return false;

        // Lab lessons are exempt
        if (lesson.isLab()) return false;

        StudentGroup group = lesson.getStudentGroup();
        String subject = lesson.getSubject();
        Teacher teacher = lesson.getTeacher();
        if (group == null || subject == null || teacher == null) return false;

        LocalDate date = proposedTimeslot.getDate();
        String key = group.id + "|" + date + "|" + subject;

        List<LessonInfo> existing = sameSubjectLessonsByDate.get(key);
        if (existing == null || existing.isEmpty()) return false;

        LocalDateTime proposedStart = proposedTimeslot.startDateTime;
        LocalDateTime proposedEnd = proposedTimeslot.endDateTime;

        for (LessonInfo info : existing) {
            // Check if they would be consecutive
            boolean wouldBeConsecutive =
                    proposedStart.equals(info.timeslot.endDateTime) ||
                    proposedEnd.equals(info.timeslot.startDateTime);

            if (wouldBeConsecutive) {
                // H16: Must have same teacher and room
                if (!teacher.id.equals(info.teacher.id)) {
                    return true; // Different teachers for consecutive lessons
                }

                if (proposedRoom != null && info.room != null &&
                    !proposedRoom.id.equals(info.room.id)) {
                    return true; // Different rooms for consecutive lessons
                }

            }

        }

        return false;
    }



    /**
     * H11: Max student group daily hours for Lesson.
     * Check if adding this lesson would exceed the group's maxHoursPerDay limit.
     */
    public boolean hasH11Violation(Lesson lesson, Timeslot proposedTimeslot) {
        if (lesson == null || proposedTimeslot == null) return false;

        StudentGroup group = lesson.getStudentGroup();
        if (group == null || group.maxHoursPerDay == null) return false;

        // T28 EXCEPTION (TASK-245): External teacher lessons are exempt from H11
        if (lesson.course != null && lesson.course.teacher != null
                && lesson.course.teacher.external) {
            return false;  // External teacher lessons don't count toward daily limit
        }

        LocalDate date = proposedTimeslot.getDate();
        String key = group.id + "|" + date;

        // Get existing hours for this group on this date
        int existingHours = groupDailyHours.getOrDefault(key, 0);

        // Adding this lesson would exceed the limit
        return existingHours >= group.maxHoursPerDay;
    }

    // Note: LabBlocks were deprecated in TASK-228 - hasH11Violation(LabBlock) removed

    /**
     * Check if context has been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }


    /**
     * Reset the context (call if you need to re-initialize).
     */
    public void reset() {
        initialized = false;
        teacherSlots.clear();
        roomSlots.clear();
        groupSlots.clear();
        teacherUnavailability.clear();
        sameSubjectLessonsByDate.clear();
        groupDailyHours.clear();
        roomsByTag.clear();
        timeslotsByDateAndSlot.clear();
    }


    // ─── Inner classes ─────────────────────────────────

    /** Reference to a pinned Lesson entity */
    private static class EntityRef {
        final Long id;
        final String type; // "Lesson" (LabBlocks deprecated in TASK-228)

        EntityRef(Long id, String type) {
            this.id = id;
            this.type = type;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EntityRef that = (EntityRef) o;
            return id.equals(that.id) && type.equals(that.type);
        }


        @Override
        public int hashCode() {
            return 31 * id.hashCode() + type.hashCode();
        }

    }


    /** Time range for teacher unavailability */
    private static class TimeRange {
        final LocalDateTime start;
        final LocalDateTime end;

        TimeRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

    }


    /** Lesson info for M15/H16 checks */
    private static class LessonInfo {
        final Long id;
        final Timeslot timeslot;
        final Room room;
        final Teacher teacher;
        final String subject;

        LessonInfo(Long id, Timeslot timeslot, Room room, Teacher teacher, String subject) {
            this.id = id;
            this.timeslot = timeslot;
            this.room = room;
            this.teacher = teacher;
            this.subject = subject;
        }

    }

}
