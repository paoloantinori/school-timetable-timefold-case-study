package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import org.jboss.logging.Logger;
import java.util.stream.Collectors;

import com.school.planner.domain.*;
import com.school.planner.service.RotationTestRunner.BlockKey;
import com.school.planner.solver.TimeslotSequence;

/**
 * Two-level CSP for lab pre-assignment (Meta-CSP).
 *
 * <p>Replaces pair-by-pair backtracking with a two-level approach:</p>
 * <ol>
 *   <li><b>Level 1 (Day Assignment):</b> Enumerate all feasible (teacher → day) mappings
 *       per roomTag using simplified day-level constraints. This is exhaustive — no heuristic
 *       search needed because the search space is tiny (e.g., 4 teachers × 5 days = 625 combos).</li>
 *   <li><b>Level 2 (Slot Finding):</b> For the best day assignment, run deterministic
 *       slot-finding using PreAssignmentContext conflict checks.</li>
 * </ol>
 *
 * <p><b>Teacher-same-day constraint:</b> A teacher teaching multiple groups in the same roomTag
 * must be on the SAME day (e.g., T16 teaches both G01 and G05 R16 — both on
 * the same day). This reduces variables from N pairs to M teachers (M ≤ N).</p>
 *
 * <p><b>Level 1 constraints (simplified, day-level):</b></p>
 * <ul>
 *   <li>Teacher not on vacation day (H5)</li>
 *   <li>No shared student groups on same day (H3)</li>
 *   <li>Room capacity per day (weekly blocks ≤ available weeks × 6)</li>
 *   <li>No overlap with pinned Phase 1 days</li>
 * </ul>
 *
 * <p><b>NOT checked at Level 1</b> (deferred to Level 2):</p>
 * <ul>
 *   <li>Specific timeslot availability</li>
 *   <li>Room-tag room contention per slot</li>
 *   <li>M15 same-subject consecutive, H16, H17</li>
 * </ul>
 */
public class DayAssignmentCsp {

    private static final Logger LOG = Logger.getLogger(DayAssignmentCsp.class.getName());

    private static final List<DayOfWeek> DAY_PRIORITY = List.of(
        DayOfWeek.THURSDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.FRIDAY
    );

    /** Maximum candidates to evaluate before stopping (prevents combinatorial explosion). */
    private static final int MAX_CANDIDATES = 10000;

    // ========== Result Records ==========

    /**
     * A complete day assignment mapping each BlockKey (teacher, group) to a DayOfWeek.
     */
    public record DayAssignment(
        Map<BlockKey, DayOfWeek> assignment,  // pair → day
        int score,                             // higher = better
        String rejectionReason                 // null if valid
    ) {
        public DayAssignment {
            assignment = assignment != null ? Map.copyOf(assignment) : Map.of();
        }

        /** Get the day for a specific pair. */
        public DayOfWeek getDay(BlockKey key) {
            return assignment.get(key);
        }

        /** Get the day for a specific teacher (all pairs for same teacher share same day). */
        public DayOfWeek getDayForTeacher(long teacherId) {
            return assignment.entrySet().stream()
                .filter(e -> e.getKey().teacherId() == teacherId)
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        }

        /** Get all teacher IDs in this assignment. */
        public Set<Long> getTeacherIds() {
            return assignment.keySet().stream()
                .map(BlockKey::teacherId)
                .collect(Collectors.toSet());
        }

        @Override
        public String toString() {
            return String.format("DayAssignment[score=%d, pairs=%d, %s]",
                score, assignment.size(), rejectionReason != null ? "REJECTED: " + rejectionReason : "VALID");
        }
    }

    /**
     * Result of the day assignment enumeration.
     */
    public record DayAssignmentResult(
        boolean success,
        DayAssignment bestAssignment,
        List<DayAssignment> allFeasible,
        int candidatesEvaluated
    ) {}

    // ========== Level 1: Day Assignment Enumeration ==========

    /**
     * Enumerate all feasible day assignments for a roomTag.
     *
     * <p>Operates at the pair level (teacher, group) but with a soft preference
     * for same-teacher-same-day. Teachers whose total blocks fit on a single
     * day (enough weeks × rooms) get the same-day constraint enforced; overflow
     * teachers are treated as independent pairs.</p>
     *
     * @param roomTag               the room tag (e.g., "moda", "meccanica")
     * @param sessionsByKey         BlockKey → LabSessions map from loadLabSessionsByRoomTag
     * @param config                test configuration with period/holiday info
     * @param existingLessonCounts  H11 cache: groupId → (date → lesson count)
     * @param context               PreAssignmentContext for Phase 1 conflict checks
     * @return DayAssignmentResult with all feasible assignments sorted by score descending
     */
    public DayAssignmentResult enumerateDayAssignments(
            String roomTag,
            Map<BlockKey, List<LabSession>> sessionsByKey,
            RotationTestRunner.TestConfig config,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            PreAssignmentContext context) {

        LOG.infof("[DayAssignmentCsp] Starting Level 1 enumeration for roomTag=%s (%d pairs)",
            roomTag, sessionsByKey.size());

        // Step 1: Group BlockKeys by teacher
        Map<Long, List<BlockKey>> pairsByTeacher = new LinkedHashMap<>();
        for (BlockKey key : sessionsByKey.keySet()) {
            pairsByTeacher.computeIfAbsent(key.teacherId(), k -> new ArrayList<>()).add(key);
        }

        // Step 2: For each pair, compute valid days
        validDaysByPairCache = new HashMap<>();
        Map<BlockKey, List<DayOfWeek>> validDaysByPair = new LinkedHashMap<>();
        for (BlockKey key : sessionsByKey.keySet()) {
            List<LabSession> sessions = sessionsByKey.get(key);
            Set<DayOfWeek> pairDays = computeValidDaysForPair(key, sessions, config, existingLessonCounts);
            List<DayOfWeek> sorted = DAY_PRIORITY.stream()
                .filter(pairDays::contains)
                .collect(Collectors.toList());
            validDaysByPair.put(key, sorted);
            validDaysByPairCache.put(key, sorted);
            LOG.infof("[DayAssignmentCsp] Pair %s has %d valid days: %s", key, sorted.size(), sorted);
        }

        // Step 3: Pre-compute room capacity per day-of-week (weeks × rooms × 6)
        Map<DayOfWeek, Integer> roomSlotsPerDay = computeRoomSlotsPerDay(roomTag, config, context);
        int maxCapacityPerDay = roomSlotsPerDay.values().stream().max(Integer::compare).orElse(0);

        // Step 4: Identify same-day groups and overflow pairs
        // A teacher's pairs get same-day constraint only if total blocks fit on one day
        Map<Long, Boolean> teacherFitsOnOneDay = new LinkedHashMap<>();
        for (Map.Entry<Long, List<BlockKey>> entry : pairsByTeacher.entrySet()) {
            Long teacherId = entry.getKey();
            int totalBlocks = 0;
            for (BlockKey pair : entry.getValue()) {
                for (LabSession session : sessionsByKey.getOrDefault(pair, List.of())) {
                    totalBlocks += session.totalHours;
                }
            }
            boolean fits = totalBlocks <= maxCapacityPerDay;
            teacherFitsOnOneDay.put(teacherId, fits);
            if (fits) {
                LOG.infof("[DayAssignmentCsp] Teacher %d: %d blocks, fits on one day (capacity=%d) — SAME-DAY enforced",
                    teacherId, totalBlocks, maxCapacityPerDay);
            } else {
                LOG.infof("[DayAssignmentCsp] Teacher %d: %d blocks, EXCEEDS one-day capacity (capacity=%d) — pairs treated independently",
                    teacherId, totalBlocks, maxCapacityPerDay);
            }
        }

        // Step 5: Build ordered pair list (most constrained first)
        List<BlockKey> pairsOrdered = new ArrayList<>(sessionsByKey.keySet());
        pairsOrdered.sort((p1, p2) -> {
            // Pairs with fewer valid days first (MCV heuristic)
            int days1 = validDaysByPair.getOrDefault(p1, List.of()).size();
            int days2 = validDaysByPair.getOrDefault(p2, List.of()).size();
            if (days1 != days2) return days1 - days2;
            // Pairs whose teacher fits on one day first (they're more constrained)
            boolean fits1 = teacherFitsOnOneDay.getOrDefault(p1.teacherId(), false);
            boolean fits2 = teacherFitsOnOneDay.getOrDefault(p2.teacherId(), false);
            return Boolean.compare(fits2, fits1);
        });

        // Step 6: Recursive enumeration — assign one day per pair
        List<DayAssignment> feasibleAssignments = new ArrayList<>();
        int[] candidatesCounter = { 0 };
        int[] bestScore = { Integer.MIN_VALUE };

        Map<BlockKey, DayOfWeek> currentPairAssignment = new LinkedHashMap<>();

        // Collect group IDs for conflict checking
        Set<Long> groupIds = sessionsByKey.keySet().stream()
            .map(BlockKey::studentGroupId)
            .collect(Collectors.toSet());

        enumeratePairsRecursive(
            pairsOrdered, 0,
            pairsByTeacher, validDaysByPair, sessionsByKey,
            config, existingLessonCounts, groupIds, roomSlotsPerDay,
            teacherFitsOnOneDay,
            currentPairAssignment,
            feasibleAssignments, candidatesCounter, bestScore, roomTag
        );

        // Sort by score descending
        feasibleAssignments.sort(Comparator.comparingInt(DayAssignment::score).reversed());

        DayAssignment best = feasibleAssignments.isEmpty() ? null : feasibleAssignments.get(0);

        LOG.infof("[DayAssignmentCsp] Level 1 complete for roomTag=%s: %d feasible / %d candidates evaluated",
            roomTag, feasibleAssignments.size(), candidatesCounter[0]);

        if (best != null) {
            LOG.infof("[DayAssignmentCsp] Best assignment (score=%d): %s", best.score(),
                formatAssignment(best));
        }

        return new DayAssignmentResult(
            !feasibleAssignments.isEmpty(),
            best,
            feasibleAssignments,
            candidatesCounter[0]
        );
    }

    /**
     * Recursive enumeration of pair → day assignments.
     *
     * <p>For each pair, try each valid day and check:</p>
     * <ul>
     *   <li>Same-teacher-same-day constraint (if teacher fits on one day)</li>
     *   <li>No student group conflict with already-assigned pairs on this day</li>
     *   <li>Room capacity not exceeded on this day</li>
     * </ul>
     *
     * <p>Uses branch-and-bound: tracks best score and prunes branches that
     * can't beat it.</p>
     */
    private void enumeratePairsRecursive(
            List<BlockKey> pairs,
            int pairIndex,
            Map<Long, List<BlockKey>> pairsByTeacher,
            Map<BlockKey, List<DayOfWeek>> validDaysByPair,
            Map<BlockKey, List<LabSession>> sessionsByKey,
            RotationTestRunner.TestConfig config,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            Set<Long> groupIds,
            Map<DayOfWeek, Integer> roomSlotsPerDay,
            Map<Long, Boolean> teacherFitsOnOneDay,
            Map<BlockKey, DayOfWeek> currentPairAssignment,
            List<DayAssignment> feasibleAssignments,
            int[] candidatesCounter,
            int[] bestScore,
            String roomTag) {

        // Early termination: stop if we've evaluated too many candidates
        if (candidatesCounter[0] >= MAX_CANDIDATES) {
            return;
        }

        if (pairIndex >= pairs.size()) {
            // All pairs assigned — build DayAssignment and score it
            Map<BlockKey, DayOfWeek> pairDays = new LinkedHashMap<>(currentPairAssignment);
            int score = scorePairAssignment(pairDays, pairsByTeacher, sessionsByKey,
                config, roomSlotsPerDay, teacherFitsOnOneDay);
            feasibleAssignments.add(new DayAssignment(pairDays, score, null));
            if (score > bestScore[0]) bestScore[0] = score;
            return;
        }

        BlockKey currentPair = pairs.get(pairIndex);
        List<DayOfWeek> validDays = validDaysByPair.getOrDefault(currentPair, List.of());

        if (validDays.isEmpty()) {
            return; // This pair has no valid days — prune
        }

        for (DayOfWeek day : validDays) {
            candidatesCounter[0]++;

            // Constraint 1: Same-teacher-same-day (if teacher fits on one day)
            if (teacherFitsOnOneDay.getOrDefault(currentPair.teacherId(), false)) {
                for (BlockKey otherPair : pairsByTeacher.getOrDefault(currentPair.teacherId(), List.of())) {
                    if (otherPair.equals(currentPair)) continue;
                    DayOfWeek otherDay = currentPairAssignment.get(otherPair);
                    if (otherDay != null && otherDay != day) {
                        // Conflict: same teacher assigned to different day
                        continue; // Skip this day for this pair
                    }
                }
            }

            // Constraint 2: Student group conflict
            boolean groupConflict = false;
            Long groupId = currentPair.studentGroupId();
            for (Map.Entry<BlockKey, DayOfWeek> assigned : currentPairAssignment.entrySet()) {
                if (assigned.getValue() != day) continue;
                if (assigned.getKey().studentGroupId() == groupId) {
                    groupConflict = true;
                    break;
                }
            }
            if (groupConflict) continue;

            // Constraint 3: Room capacity (soft — just check, don't hard-prune for small overflows)
            int additionalBlocks = 0;
            for (LabSession session : sessionsByKey.getOrDefault(currentPair, List.of())) {
                additionalBlocks += session.totalHours;
            }
            int currentBlocksOnDay = 0;
            for (Map.Entry<BlockKey, DayOfWeek> assigned : currentPairAssignment.entrySet()) {
                if (assigned.getValue() != day) continue;
                for (LabSession session : sessionsByKey.getOrDefault(assigned.getKey(), List.of())) {
                    currentBlocksOnDay += session.totalHours;
                }
            }
            int availableSlots = roomSlotsPerDay.getOrDefault(day, 0);
            if (currentBlocksOnDay + additionalBlocks > availableSlots) {
                continue; // Hard capacity constraint
            }

            // Assign this pair to this day
            currentPairAssignment.put(currentPair, day);

            // Branch-and-bound: estimate max possible remaining score
            // (simplified: just recurse — the search space is small enough)

            enumeratePairsRecursive(
                pairs, pairIndex + 1,
                pairsByTeacher, validDaysByPair, sessionsByKey,
                config, existingLessonCounts, groupIds, roomSlotsPerDay,
                teacherFitsOnOneDay,
                currentPairAssignment,
                feasibleAssignments, candidatesCounter, bestScore, roomTag
            );

            // Backtrack
            currentPairAssignment.remove(currentPair);
        }
    }

    // ========== Level 1 Helper Methods ==========

    /**
     * Compute valid days for a (teacher, group) pair using H5 vacation constraints
     * and LabRotation day preferences.
     *
     * <p>Mirrors the logic from GreedyRotationAssigner.getValidDaysForPair but as
     * a static method usable by DayAssignmentCsp.</p>
     */
    private Set<DayOfWeek> computeValidDaysForPair(
            BlockKey key,
            List<LabSession> blocks,
            RotationTestRunner.TestConfig config,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts) {

        Long teacherId = key.teacherId();

        // Start with ALL weekdays
        Set<DayOfWeek> validDays = new LinkedHashSet<>(DAY_PRIORITY);

        // Remove VACATION days for this teacher
        List<TeacherAvailability> vacations = TeacherAvailability.find(
            "teacher.id = ?1 and type = ?2",
            teacherId, AvailabilityType.VACATION).list();

        Set<DayOfWeek> vacationDays = new HashSet<>();
        for (TeacherAvailability ta : vacations) {
            if (ta.startDateTime != null) {
                vacationDays.add(ta.startDateTime.getDayOfWeek());
            }
        }
        validDays.removeAll(vacationDays);

        // Collect rotation days from blocks
        Set<DayOfWeek> rotationDays = new LinkedHashSet<>();
        boolean hasOverflowBlocks = false;
        for (LabSession session : blocks) {
            if (session.labRotation != null && session.labRotation.dayOfWeek != null) {
                rotationDays.add(session.labRotation.dayOfWeek);
            } else {
                hasOverflowBlocks = true;
            }
        }

        // If ALL blocks have rotation → constrain to rotation day(s) only
        if (!rotationDays.isEmpty() && !hasOverflowBlocks) {
            validDays.retainAll(rotationDays);
        }

        // Remove days where the largest block can't fit. Uniform 8 slots
        // every day (TASK-343) - no Friday-specific reduction.
        int maxBlockSize = blocks.stream().mapToInt(b -> b.lessons.size()).max().orElse(0);
        validDays.removeIf(day -> {
            int slotsPerDay = TimeslotSequence.SLOTS_PER_DAY;
            return maxBlockSize > slotsPerDay;
        });

        // Remove days where the student group already has too many existing lessons.
        // If the group's existing lessons on a given day-of-week leave insufficient
        // consecutive free slots for the block, that day is effectively blocked.
        // Check: count dates where existing lessons + blockSize would exceed available slots.
        if (existingLessonCounts != null && !existingLessonCounts.isEmpty()) {
            Map<LocalDate, Integer> groupLessons = existingLessonCounts.getOrDefault(key.studentGroupId(), Map.of());
            if (!groupLessons.isEmpty()) {
                LocalDate pStart = config.periodStart();
                LocalDate pEnd = config.periodEnd();

                Set<DayOfWeek> saturatedDays = new HashSet<>();
                for (DayOfWeek candidateDay : validDays) {
                    int totalDates = 0;
                    int blockedDates = 0;
                    int slotsPerDay = TimeslotSequence.SLOTS_PER_DAY;

                    for (LocalDate d = pStart; !d.isAfter(pEnd); d = d.plusDays(1)) {
                        if (d.getDayOfWeek() != candidateDay) continue;
                        if (config.holidays().contains(d)) continue;
                        totalDates++;
                        int existing = groupLessons.getOrDefault(d, 0);
                        if (existing + maxBlockSize > slotsPerDay) {
                            blockedDates++;
                        }
                    }

                    // If >70% of dates are blocked, the day is effectively unavailable
                    if (totalDates > 0 && blockedDates > totalDates * 0.7) {
                        saturatedDays.add(candidateDay);
                    }
                }

                if (!saturatedDays.isEmpty()) {
                    validDays.removeAll(saturatedDays);
                    LOG.debugf("[DayAssignmentCsp] Removed saturated days %s for pair %s (group has too many existing lessons)",
                        saturatedDays, key);
                }
            }
        }

        return validDays;
    }

    /**
     * Score a pair-level day assignment. Higher = better.
     *
     * <p>Scoring factors:</p>
     * <ul>
     *   <li>+100 per block assigned (more blocks placed = better)</li>
     *   <li>+50 per teacher where all pairs share the same day (same-day preference)</li>
     *   <li>-30 per day with > roomCount teachers (room contention risk)</li>
     *   <li>+10 per pair assigned to their LabRotation day (if constrained)</li>
     * </ul>
     */
    private int scorePairAssignment(
            Map<BlockKey, DayOfWeek> pairDays,
            Map<Long, List<BlockKey>> pairsByTeacher,
            Map<BlockKey, List<LabSession>> sessionsByKey,
            RotationTestRunner.TestConfig config,
            Map<DayOfWeek, Integer> roomSlotsPerDay,
            Map<Long, Boolean> teacherFitsOnOneDay) {

        int score = 0;

        // 1. +10 per block-hour assigned (more blocks placed = better)
        for (List<LabSession> sessions : sessionsByKey.values()) {
            for (LabSession s : sessions) {
                score += s.totalHours * 10;
            }
        }

        // 2. +50 per teacher where all pairs share the same day (same-day bonus)
        for (Map.Entry<Long, List<BlockKey>> entry : pairsByTeacher.entrySet()) {
            Long teacherId = entry.getKey();
            if (!teacherFitsOnOneDay.getOrDefault(teacherId, false)) continue;

            Set<DayOfWeek> daysUsed = new HashSet<>();
            for (BlockKey pair : entry.getValue()) {
                DayOfWeek d = pairDays.get(pair);
                if (d != null) daysUsed.add(d);
            }
            if (daysUsed.size() == 1) {
                score += 50;
            }
        }

        // 3. Penalize room contention (multiple teachers on same day)
        Map<DayOfWeek, Set<Long>> teachersPerDay = new HashMap<>();
        for (Map.Entry<BlockKey, DayOfWeek> entry : pairDays.entrySet()) {
            teachersPerDay.computeIfAbsent(entry.getValue(), k -> new HashSet<>())
                .add(entry.getKey().teacherId());
        }
        for (Map.Entry<DayOfWeek, Set<Long>> entry : teachersPerDay.entrySet()) {
            int teacherCount = entry.getValue().size();
            if (teacherCount > 1) {
                score -= (teacherCount - 1) * 30;
            }
        }

        // 3b. Penalize teacher bottleneck: a teacher can only teach one session
        // at a time. With 6 slots/day and varying block sizes, the max sessions
        // per day per teacher depends on the block sizes of the assigned groups.
        // Conservative estimate: 3 sessions per day (covers 2h blocks well).
        Map<Long, Map<DayOfWeek, Integer>> teacherPairCount = new HashMap<>();
        for (Map.Entry<BlockKey, DayOfWeek> entry : pairDays.entrySet()) {
            teacherPairCount
                .computeIfAbsent(entry.getKey().teacherId(), k -> new HashMap<>())
                .merge(entry.getValue(), 1, Integer::sum);
        }
        int maxPairsPerDay = 3; // Conservative: teacher teaches max 3 sessions/day
        for (Map.Entry<Long, Map<DayOfWeek, Integer>> teacherEntry : teacherPairCount.entrySet()) {
            for (Map.Entry<DayOfWeek, Integer> dayEntry : teacherEntry.getValue().entrySet()) {
                int pairs = dayEntry.getValue();
                if (pairs > maxPairsPerDay) {
                    score -= (pairs - maxPairsPerDay) * 200;
                }
            }
        }

        // 4. Reward LabRotation day preference
        for (Map.Entry<BlockKey, DayOfWeek> entry : pairDays.entrySet()) {
            BlockKey pair = entry.getKey();
            DayOfWeek assignedDay = entry.getValue();
            List<DayOfWeek> validDays = validDaysByPairCache.get(pair);
            if (validDays != null && validDays.size() == 1 && validDays.get(0).equals(assignedDay)) {
                score += 10;
            }
        }

        // 5. Reward spreading teacher pairs across days (reduces room contention at slot level).
        // Pairs per day: 1→+60, 2→+30, 3→+10, 4+→-20
        for (Map.Entry<Long, Map<DayOfWeek, Integer>> teacherEntry : teacherPairCount.entrySet()) {
            for (Map.Entry<DayOfWeek, Integer> dayEntry : teacherEntry.getValue().entrySet()) {
                int pairs = dayEntry.getValue();
                if (pairs == 1) score += 60;
                else if (pairs == 2) score += 30;
                else if (pairs == 3) score += 10;
                else score -= 20;
            }
        }

        return score;
    }

    /** Cache of valid days per pair, populated during enumeration. */
    private Map<BlockKey, List<DayOfWeek>> validDaysByPairCache = new HashMap<>();

    /**
     * Compute available room slots per day-of-week across the whole year.
     *
     * <p>Returns: day → available weeks for that day (number of school weeks
     * where that day-of-week falls on a non-holiday date).</p>
     *
     * <p>The actual per-day capacity check is: for each day, the sum of all
     * teachers' blocks assigned to that day must ≤ (weeks × rooms × 6).
     * This is a rough capacity estimate for Level 1 pruning.</p>
     */
    private Map<DayOfWeek, Integer> computeRoomSlotsPerDay(
            String roomTag,
            RotationTestRunner.TestConfig config,
            PreAssignmentContext context) {

        // Count rooms with this tag (from pre-loaded context — no DB query)
        List<Room> rooms = context.getRoomsByTag(roomTag);
        int roomCount = rooms.size();
        if (roomCount == 0) {
            LOG.warnf("[DayAssignmentCsp] No rooms found for tag=%s", roomTag);
            return Map.of();
        }

        // Count available weeks for each day of week
        Set<LocalDate> holidays = config.holidays();
        Map<DayOfWeek, Integer> weeksPerDay = new HashMap<>();
        for (DayOfWeek day : DAY_PRIORITY) {
            int weeks = 0;
            LocalDate date = config.periodStart();
            // Find first occurrence of this day
            while (date.getDayOfWeek() != day && !date.isAfter(config.periodEnd())) {
                date = date.plusDays(1);
            }
            // Count weeks
            LocalDate weekStart = date;
            while (!weekStart.isAfter(config.periodEnd())) {
                if (!holidays.contains(weekStart)) {
                    weeks++;
                }
                weekStart = weekStart.plusWeeks(1);
            }
            weeksPerDay.put(day, weeks);
        }

        // Slots per day = weeks × rooms × slotsPerDay (uniform 8 slots/day, TASK-343)
        Map<DayOfWeek, Integer> result = new HashMap<>();
        for (DayOfWeek day : DAY_PRIORITY) {
            int slotsPerDay = TimeslotSequence.SLOTS_PER_DAY;
            result.put(day, weeksPerDay.getOrDefault(day, 0) * roomCount * slotsPerDay);
        }

        LOG.infof("[DayAssignmentCsp] Room capacity for tag=%s: %d rooms, slots per day: %s",
            roomTag, roomCount, result);

        return result;
    }

    /**
     * Count available school weeks for a specific day of week within the period.
     */
    private int countAvailableWeeks(DayOfWeek day, RotationTestRunner.TestConfig config) {
        int weeks = 0;
        Set<LocalDate> holidays = config.holidays();
        LocalDate date = config.periodStart();

        // Find first occurrence of this day
        while (date.getDayOfWeek() != day && !date.isAfter(config.periodEnd())) {
            date = date.plusDays(1);
        }

        while (!date.isAfter(config.periodEnd())) {
            if (!holidays.contains(date)) {
                weeks++;
            }
            date = date.plusWeeks(1);
        }

        return weeks;
    }

    private String formatAssignment(DayAssignment assignment) {
        if (assignment == null || assignment.assignment().isEmpty()) return "{}";
        return assignment.assignment().entrySet().stream()
            .map(e -> String.format("%s→%s", e.getKey(), e.getValue()))
            .collect(Collectors.joining(", "));
    }
}
