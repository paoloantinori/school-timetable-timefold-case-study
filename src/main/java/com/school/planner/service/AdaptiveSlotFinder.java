package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.LabRotation;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.util.StageEligibilityUtil;

/**
 * Adaptive slot finder for pre-assignment.
 *
 * Instead of relying on rigid LabRotation.dayOfWeek values, this service finds
 * the best available days based on:
 * 1. Teacher availability (respects VACATION/SICK records)
 * 2. Room contention (avoids days where the lab room is heavily used)
 * 3. Regular weekly patterns (prefers same day each week for consistency)
 *
 * This enables adaptive pre-assignment that works even when:
 * - Teachers change availability patterns
 * - External teachers join/leave
 * - Room usage patterns shift
 */
@ApplicationScoped
public class AdaptiveSlotFinder {

    private static final Logger LOG = Logger.getLogger(AdaptiveSlotFinder.class);

    @Inject
    PreAssignmentValidator validator;

    // 2026/27 calendar (TASK-341/343): 8 uniform slots every school day, no
    // Friday-specific reduction. Single source of truth: TimeslotSequence.SLOT_START_TIMES.

    /**
     * Result of finding the best day for a rotation.
     */
    public static class BestDayResult {
        public final DayOfWeek bestDay;
        public final int availableWeeks;
        public final Map<DayOfWeek, Integer> dayScores;
        public final String warning;

        public BestDayResult(DayOfWeek bestDay, int availableWeeks,
                             Map<DayOfWeek, Integer> dayScores, String warning) {
            this.bestDay = bestDay;
            this.availableWeeks = availableWeeks;
            this.dayScores = dayScores;
            this.warning = warning;
        }
    }

    /**
     * Find the best day of week for a lab rotation.
     *
     * Scores each weekday based on:
     * - Teacher availability (days with VACATION/SICK get penalized)
     * - Room contention (days with fewer existing assignments preferred)
     * - Consistency (prefers days with contiguous availability)
     *
     * @param rotation the lab rotation (teacher, group, period, roomTag)
     * @param labRoom the lab room to use
     * @param holidays set of school holidays
     * @param stagePatterns all stage patterns for blackout checking
     * @return BestDayResult with the optimal day and scoring details
     */
    public BestDayResult findBestDayForRotation(LabRotation rotation, Room labRoom,
                                                 Set<LocalDate> holidays,
                                                 List<StagePattern> stagePatterns) {
        Teacher teacher = rotation.teacher;
        StudentGroup group = rotation.studentGroup;
        LocalDate periodStart = rotation.periodStart;
        LocalDate periodEnd = rotation.periodEnd;

        Map<DayOfWeek, Integer> dayScores = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, Integer> availableWeeksByDay = new EnumMap<>(DayOfWeek.class);

        // Initialize all days
        for (DayOfWeek day : DayOfWeek.values()) {
            dayScores.put(day, 0);
            availableWeeksByDay.put(day, 0);
        }

        // Score each day based on teacher availability and room contention
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                dayScores.put(day, -1000); // Heavily penalize weekends
                continue;
            }

            DayScore score = scoreDay(day, teacher, group, labRoom,
                    periodStart, periodEnd, holidays, stagePatterns);
            dayScores.put(day, score.totalScore);
            availableWeeksByDay.put(day, score.availableWeeks);
        }

        // Find the best day (highest score)
        DayOfWeek bestDay = dayScores.entrySet().stream()
                .filter(e -> e.getValue() > -500) // Exclude heavily penalized days
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);

        if (bestDay == null) {
            return new BestDayResult(null, 0, dayScores,
                    "No suitable day found - teacher may be unavailable all week");
        }

        int availableWeeks = availableWeeksByDay.get(bestDay);
        String warning = null;
        if (availableWeeks == 0) {
            warning = "Best day " + bestDay + " has zero available weeks in the period";
        }

        LOG.infof("Best day for %s -> %s in %s: %s (score=%d, weeks=%d)%s",
                teacher != null ? teacher.name : "group",
                group != null ? group.name : "?",
                rotation.roomTag,
                bestDay,
                dayScores.get(bestDay),
                availableWeeks,
                warning != null ? " WARNING: " + warning : "");

        return new BestDayResult(bestDay, availableWeeks, dayScores, warning);
    }

    /**
     * Score a specific day of the week for the given rotation parameters.
     */
    private DayScore scoreDay(DayOfWeek day, Teacher teacher, StudentGroup group,
                              Room labRoom, LocalDate periodStart, LocalDate periodEnd,
                              Set<LocalDate> holidays, List<StagePattern> stagePatterns) {
        int score = 0;
        int availableWeeks = 0;
        int blockedWeeks = 0;

        LocalDate date = periodStart;
        while (!date.isAfter(periodEnd)) {
            if (date.getDayOfWeek() == day) {
                boolean isAvailable = true;

                // Check holiday
                if (holidays.contains(date)) {
                    score -= 10; // Minor penalty - affects all equally
                    isAvailable = false;
                }

                // Check stage blackout
                if (StageEligibilityUtil.isStageBlackout(date, group, stagePatterns)) {
                    score -= 10; // Minor penalty - affects all equally
                    isAvailable = false;
                }

                // Check teacher availability (MAJOR factor)
                if (teacher != null && isTeacherUnavailableAllDay(teacher, date)) {
                    score -= 100; // Heavy penalty - can't use this day
                    isAvailable = false;
                    blockedWeeks++;
                }

                // Check room contention
                int roomContention = countRoomContention(labRoom, date);
                score -= roomContention * 5; // 5 points per existing assignment

                if (isAvailable) {
                    availableWeeks++;
                    score += 10; // Base score for each available week
                }
            }
            date = date.plusDays(1);
        }

        // Bonus for consistency (if most weeks are available)
        if (availableWeeks > blockedWeeks * 2) {
            score += 20; // Consistency bonus
        }

        return new DayScore(score, availableWeeks);
    }

    /**
     * Check if teacher is unavailable for the entire day.
     * Queries TeacherAvailability for VACATION/SICK records covering the whole day.
     */
    public boolean isTeacherUnavailableAllDay(Teacher teacher, LocalDate date) {
        if (teacher == null) {
            return false;
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(LocalTime.MAX);

        // Check if there's a full-day VACATION or SICK record
        long count = TeacherAvailability.count(
                "teacher = ?1 and type in (?2, ?3) " +
                "and startDateTime <= ?4 and endDateTime >= ?5",
                teacher, AvailabilityType.VACATION, AvailabilityType.SICK,
                dayStart, dayEnd);

        return count > 0;
    }

    /**
     * Find all days of week where a teacher is available during a period.
     * Returns days sorted by availability score (best first).
     */
    public List<DayOfWeek> findAvailableDaysForTeacher(Teacher teacher,
                                                        LocalDate periodStart,
                                                        LocalDate periodEnd,
                                                        Set<LocalDate> holidays) {
        Map<DayOfWeek, Integer> availableWeeksByDay = new EnumMap<>(DayOfWeek.class);

        for (DayOfWeek day : DayOfWeek.values()) {
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                continue;
            }

            int weeks = countAvailableWeeks(teacher, day, periodStart, periodEnd, holidays);
            if (weeks > 0) {
                availableWeeksByDay.put(day, weeks);
            }
        }

        // Sort by available weeks (descending), return days only
        return availableWeeksByDay.entrySet().stream()
                .sorted(Map.Entry.<DayOfWeek, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Count available weeks for a teacher on a specific day of week.
     * Excludes holidays and days where teacher has VACATION/SICK.
     */
    public int countAvailableWeeks(Teacher teacher, DayOfWeek day,
                                   LocalDate periodStart, LocalDate periodEnd,
                                   Set<LocalDate> holidays) {
        int count = 0;
        LocalDate date = periodStart;

        while (!date.isAfter(periodEnd)) {
            if (date.getDayOfWeek() == day) {
                if (!holidays.contains(date)) {
                    if (teacher == null || !isTeacherUnavailableAllDay(teacher, date)) {
                        count++;
                    }
                }
            }
            date = date.plusDays(1);
        }

        return count;
    }

    /**
     * Count how many pinned entities already occupy the lab room on a given date.
     * Used to assess room contention for adaptive day selection.
     */
    private int countRoomContention(Room room, LocalDate date) {
        if (room == null) {
            return 0;
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        // Count pinned lessons in this room on this date
        // Note: LabBlocks were deprecated in TASK-228. Lab lessons are now Lessons with isLabHour=true.
        long lessonCount = Lesson.count(
                "room = ?1 and pinned = true and timeslot is not null " +
                "and timeslot.startDateTime >= ?2 and timeslot.startDateTime < ?3",
                room, dayStart, dayEnd);

        return (int) lessonCount;
    }

    /**
     * Find ranked alternative days for overflow assignment.
     * When the primary rotation day is exhausted, use this to find
     * the next best days for assigning remaining blocks.
     *
     * @param rotation the original rotation
     * @param labRoom the lab room
     * @param primaryDay the day already being used (will be ranked lower)
     * @param holidays set of school holidays
     * @param stagePatterns all stage patterns
     * @return list of days ranked by suitability (best first)
     */
    public List<DayWithScore> findBestDaysForOverflow(LabRotation rotation, Room labRoom,
                                                       DayOfWeek primaryDay,
                                                       Set<LocalDate> holidays,
                                                       List<StagePattern> stagePatterns) {
        BestDayResult result = findBestDayForRotation(rotation, labRoom, holidays, stagePatterns);

        return result.dayScores.entrySet().stream()
                .filter(e -> e.getKey() != primaryDay) // Exclude the primary day
                .filter(e -> e.getValue() > -500) // Exclude heavily penalized days
                .filter(e -> e.getKey() != DayOfWeek.SATURDAY)
                .filter(e -> e.getKey() != DayOfWeek.SUNDAY)
                .filter(e -> canDayFitBlock(e.getKey(), rotation.slotStart, rotation.blockSize)) // H9: block must fit
                .sorted(Map.Entry.<DayOfWeek, Integer>comparingByValue().reversed())
                .map(e -> new DayWithScore(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Check if a day can accommodate a block starting at slotStart with given blockSize.
     * Every weekday has 8 uniform slots (TASK-343) - no Friday-specific reduction.
     *
     * @param day the day of week to check
     * @param slotStart the starting slot index (0-based)
     * @param blockSize the number of consecutive slots needed
     * @return true if the day has enough slots for the block starting at slotStart
     */
    private static boolean canDayFitBlock(DayOfWeek day, int slotStart, int blockSize) {
        int maxSlots = TimeslotSequence.SLOT_START_TIMES.length;
        return slotStart + blockSize <= maxSlots;
    }

    /**
     * Compute valid dates for a rotation using adaptive day selection.
     * If rotation.dayOfWeek is null, uses the best available day.
     *
     * @param rotation the lab rotation (dayOfWeek may be null for adaptive selection)
     * @param labRoom the lab room to use
     * @param holidays set of school holidays
     * @param stagePatterns all stage patterns for blackout checking
     * @return list of valid dates for assignment
     */
    public List<LocalDate> computeValidDatesAdaptive(LabRotation rotation, Room labRoom,
                                                      Set<LocalDate> holidays,
                                                      List<StagePattern> stagePatterns) {
        DayOfWeek targetDay = rotation.dayOfWeek;

        // If dayOfWeek is null, find the best available day
        if (targetDay == null) {
            BestDayResult bestDay = findBestDayForRotation(rotation, labRoom,
                    holidays, stagePatterns);
            if (bestDay.bestDay == null) {
                LOG.warnf("No available day found for rotation %s -> %s",
                        rotation.teacher != null ? rotation.teacher.name : "?",
                        rotation.studentGroup != null ? rotation.studentGroup.name : "?");
                return List.of();
            }
            targetDay = bestDay.bestDay;
            LOG.infof("Adaptive selection: using %s for rotation %s -> %s",
                    targetDay,
                    rotation.teacher != null ? rotation.teacher.name : "?",
                    rotation.studentGroup != null ? rotation.studentGroup.name : "?");
        }

        // Compute valid dates for the selected day
        return computeValidDates(rotation, targetDay, holidays, stagePatterns);
    }

    /**
     * Compute valid dates for a rotation with an explicit day of week.
     * (Original behavior, used when dayOfWeek is already set)
     */
    public List<LocalDate> computeValidDates(LabRotation rotation, DayOfWeek dayOfWeek,
                                              Set<LocalDate> holidays,
                                              List<StagePattern> stagePatterns) {
        List<LocalDate> validDates = new ArrayList<>();
        LocalDate date = rotation.periodStart;

        while (!date.isAfter(rotation.periodEnd)) {
            if (date.getDayOfWeek() == dayOfWeek
                    && !holidays.contains(date)
                    && !StageEligibilityUtil.isStageBlackout(date, rotation.studentGroup, stagePatterns)) {
                // Also check teacher availability
                if (rotation.teacher == null ||
                    !isTeacherUnavailableAllDay(rotation.teacher, date)) {
                    validDates.add(date);
                }
            }
            date = date.plusDays(1);
        }

        return validDates;
    }

    /**
     * Check if a teacher is available for a specific timeslot.
     * (Granular slot-level check, not full-day)
     */
    public boolean isTeacherAvailableForSlot(Teacher teacher, Timeslot timeslot) {
        if (teacher == null || timeslot == null) {
            return true; // No teacher/slot = assume available
        }

        return !validator.hasH5Violation(teacher, timeslot);
    }

    /**
     * Find timeslots for a specific date, starting from the given slot index.
     */
    public List<Timeslot> findTimeslotsForDate(LocalDate date, int slotStart, int count) {
        LocalTime[] slotTimes = TimeslotSequence.SLOT_START_TIMES;

        List<Timeslot> result = new ArrayList<>();
        for (int i = slotStart; i < Math.min(slotStart + count, slotTimes.length); i++) {
            LocalDateTime start = LocalDateTime.of(date, slotTimes[i]);
            Timeslot ts = Timeslot.find("startDateTime", start).firstResult();
            if (ts != null) {
                result.add(ts);
            }
        }
        return result;
    }

    /**
     * Find the start timeslot for a specific date and slot index.
     */
    public Timeslot findStartTimeslot(LocalDate date, int slotStart) {
        LocalTime[] slotTimes = TimeslotSequence.SLOT_START_TIMES;

        if (slotStart >= slotTimes.length) return null;
        LocalDateTime start = LocalDateTime.of(date, slotTimes[slotStart]);
        return Timeslot.find("startDateTime", start).firstResult();
    }

    /**
     * Get all holidays as a set of LocalDate.
     */
    public Set<LocalDate> getHolidays() {
        return SchoolHoliday.<SchoolHoliday>listAll().stream()
                .map(h -> h.date)
                .collect(Collectors.toSet());
    }

    // ─── Inner classes ─────────────────────────────────────────────

    private static class DayScore {
        final int totalScore;
        final int availableWeeks;

        DayScore(int totalScore, int availableWeeks) {
            this.totalScore = totalScore;
            this.availableWeeks = availableWeeks;
        }
    }

    public static class DayWithScore {
        public final DayOfWeek day;
        public final int score;

        public DayWithScore(DayOfWeek day, int score) {
            this.day = day;
            this.score = score;
        }
    }

    // ─── Multi-Option Pre-Assignment Support ─────────────────────────────────

    /**
     * A candidate day for pre-assignment with detailed conflict scoring.
     *
     * Instead of just scoring by availability, this includes actual conflict counts
     * against existing pinned entities and in-memory tracking.
     */
    public static class DayCandidate {
        public final DayOfWeek day;
        public final List<LocalDate> validDates;
        /** Total conflicts: teacher unavailability + room contention + group conflicts */
        public final int conflictScore;
        /** Number of weeks where teacher is fully available (no VACATION/SICK) */
        public final int availableWeeks;
        /** Weeks where teacher has VACATION or SICK (contributes to conflictScore) */
        public final int blockedWeeks;
        /** Existing pinned entities in the room during these dates */
        public final int roomContention;
        /** Reason for rejection, or null if conflict-free */
        public final String rejectionReason;

        public DayCandidate(DayOfWeek day, List<LocalDate> validDates, int conflictScore,
                            int availableWeeks, int blockedWeeks, int roomContention,
                            String rejectionReason) {
            this.day = day;
            this.validDates = validDates;
            this.conflictScore = conflictScore;
            this.availableWeeks = availableWeeks;
            this.blockedWeeks = blockedWeeks;
            this.roomContention = roomContention;
            this.rejectionReason = rejectionReason;
        }

        /**
         * True if this day has zero conflicts (teacher available all weeks, no room contention).
         */
        public boolean isConflictFree() {
            return conflictScore == 0;
        }

        /** Getter for conflict score (used in method references) */
        public int getConflictScore() {
            return conflictScore;
        }

        @Override
        public String toString() {
            return String.format("DayCandidate{day=%s, conflicts=%d, weeks=%d, dates=%d, reason=%s}",
                    day, conflictScore, availableWeeks, validDates.size(), rejectionReason);
        }
    }

    /**
     * Generate candidate assignments for ALL weekdays, not just the best one.
     *
     * This implements the "generate-and-test" pattern that matches human planner behavior:
     * instead of predicting the best day upfront, generate multiple options and
     * select based on actual conflicts.
     *
     * @param rotation the lab rotation (teacher, group, period, roomTag)
     * @param labRoom the lab room to use
     * @param holidays set of school holidays
     * @param stagePatterns all stage patterns for blackout checking
     * @return map of DayOfWeek -> DayCandidate with conflict scores
     */
    public Map<DayOfWeek, DayCandidate> generateAllDayCandidates(
            LabRotation rotation, Room labRoom,
            Set<LocalDate> holidays, List<StagePattern> stagePatterns) {

        Map<DayOfWeek, DayCandidate> candidates = new EnumMap<>(DayOfWeek.class);

        for (DayOfWeek day : DayOfWeek.values()) {
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                continue; // Skip weekends
            }

            // H9: Skip days that can't fit the block at the specified slotStart
            if (!canDayFitBlock(day, rotation.slotStart, rotation.blockSize)) {
                LOG.debugf("Skipping %s for %d-hour block starting at slot %d (insufficient slots)",
                        day, rotation.blockSize, rotation.slotStart);
                continue;
            }

            DayCandidate candidate = buildDayCandidate(day, rotation, labRoom,
                    holidays, stagePatterns);
            candidates.put(day, candidate);
        }

        return candidates;
    }

    /**
     * Build a DayCandidate for a specific day with conflict scoring.
     */
    private DayCandidate buildDayCandidate(DayOfWeek day, LabRotation rotation,
                                            Room labRoom, Set<LocalDate> holidays,
                                            List<StagePattern> stagePatterns) {
        Teacher teacher = rotation.teacher;
        StudentGroup group = rotation.studentGroup;
        LocalDate periodStart = rotation.periodStart;
        LocalDate periodEnd = rotation.periodEnd;

        List<LocalDate> validDates = new ArrayList<>();
        int availableWeeks = 0;
        int blockedWeeks = 0;
        int roomContention = 0;

        LocalDate date = periodStart;
        while (!date.isAfter(periodEnd)) {
            if (date.getDayOfWeek() == day) {
                // Check basic availability
                if (holidays.contains(date)) {
                    // Skip holidays - don't count as blocked
                } else if (StageEligibilityUtil.isStageBlackout(date, group, stagePatterns)) {
                    // Skip stage blackout - don't count as blocked
                } else {
                    // Check teacher availability (H5)
                    if (teacher != null && isTeacherUnavailableAllDay(teacher, date)) {
                        blockedWeeks++;
                    } else {
                        availableWeeks++;
                        validDates.add(date);
                        // Count room contention for available dates
                        roomContention += countRoomContention(labRoom, date);
                    }
                }
            }
            date = date.plusDays(1);
        }

        // Calculate conflict score
        // - 10 points per week where teacher has VACATION/SICK
        // - 5 points per existing room assignment (contention)
        int conflictScore = blockedWeeks * 10 + roomContention;

        // Determine rejection reason
        String rejectionReason = null;
        if (validDates.isEmpty()) {
            rejectionReason = "No valid dates (all blocked by holidays/stage)";
        } else if (blockedWeeks > 0 && availableWeeks == 0) {
            rejectionReason = "Teacher unavailable all weeks";
        } else if (conflictScore > 50) {
            rejectionReason = String.format("High conflict score (%d): %d blocked weeks, %d room conflicts",
                    conflictScore, blockedWeeks, roomContention);
        }

        return new DayCandidate(day, validDates, conflictScore, availableWeeks,
                blockedWeeks, roomContention, rejectionReason);
    }

    /**
     * Select the best conflict-free day from candidates.
     *
     * Priority:
     * 1. Conflict-free days (conflictScore == 0)
     * 2. Most available weeks (for assignment capacity)
     * 3. Lower conflict score (for ties)
     *
     * @param candidates map of day -> candidate
     * @return the best day, or null if no conflict-free option exists
     */
    public DayOfWeek selectBestConflictFreeDay(Map<DayOfWeek, DayCandidate> candidates) {
        return candidates.values().stream()
                .filter(DayCandidate::isConflictFree)
                .filter(c -> !c.validDates.isEmpty())
                .max(Comparator.comparingInt((DayCandidate c) -> c.availableWeeks)
                        .thenComparing(c -> -c.conflictScore)) // Lower is better
                .map(c -> c.day)
                .orElse(null);
    }

    /**
     * Get all candidates sorted by suitability (best first).
     * Conflict-free days come first, sorted by available weeks.
     */
    public List<DayCandidate> getSortedCandidates(Map<DayOfWeek, DayCandidate> candidates) {
        return candidates.values().stream()
                .filter(c -> !c.validDates.isEmpty())
                .sorted(Comparator
                        .comparingInt(DayCandidate::getConflictScore) // Lower is better
                        .thenComparing(Comparator.comparingInt((DayCandidate c) -> c.availableWeeks).reversed()))
                .collect(Collectors.toList());
    }
}
