package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Room;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.util.StageEligibilityUtil;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.service.RotationTestRunner.BlockKey;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * CSP backtracking engine for rotation assignment.
 *
 * <p>Assigns days to (teacher, group) pairs using constraint-satisfying
 * backtracking. Each pair can have its own day, allowing teachers like
 * T26 to teach different groups on different days.</p>
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Use (teacher, group) pairs as the decision units (from blocksByKeys)</li>
 *   <li>Classify each pair into a placement mode:
 *     <ul>
 *       <li>MULTI_DAY_DETERMINISTIC — blocks have different LabRotation days (e.g., T15 Mon+Tue)</li>
 *       <li>SINGLE_DAY — one LabRotation day or one CSP pick (e.g., T28 Thursday)</li>
 *       <li>MULTI_DAY_CSP — no LabRotation, spread greedily across days (e.g., T17 moda)</li>
 *     </ul>
 *   </li>
 *   <li>Order pairs by constraint tightness (deterministic first, then fewer choices first)</li>
 *   <li>For each pair:
 *     <ul>
 *       <li>MULTI_DAY_DETERMINISTIC: place each block on its own labRotation day</li>
 *       <li>SINGLE_DAY: try each valid day, accept best (most blocks placed)</li>
 *       <li>MULTI_DAY_CSP: fill best day first, overflow to next day</li>
 *     </ul>
 *   </li>
 *   <li>Return success when all pairs processed</li>
 * </ol>
 *
 * @see TeacherDayAssignment
 * @see RotationTestRunner
 */
@ApplicationScoped
public class GreedyRotationAssigner {

    private static final Logger LOG = Logger.getLogger(GreedyRotationAssigner.class);

    @Inject
    PreAssignmentContext context;

    @Inject
    TargetEndDateCalculator targetEndDateCalculator;

    /**
     * Maximum backtracking depth before giving up.
     */
    private static final int MAX_BACKTRACK_DEPTH = 100;

    /**
     * Day priority order matching human planner preference.
     * External teachers (like T28 on Thursday) are prioritized first.
     */
    private static final List<DayOfWeek> DAY_PRIORITY = List.of(
        DayOfWeek.THURSDAY,   // T28's day (external teacher)
        DayOfWeek.WEDNESDAY,  // T26's preferred day
        DayOfWeek.TUESDAY,    // T18's preferred day
        DayOfWeek.MONDAY,
        DayOfWeek.FRIDAY
    );


    /**
     * Result of the greedy assignment process.
     * Now carries pre-computed slot assignments so pinning doesn't need to re-search.
     */
    public record AssignmentResult(
        boolean success,
        TeacherDayAssignment assignment,
        int candidatesTried,
        Long failedTeacherId,
        String failureReason,
        FailureAnalysis analysis,
        List<RotationTestRunner.SlotAssignment> slotAssignments
    ) {
        public static AssignmentResult success(TeacherDayAssignment assignment, int candidatesTried,
                                                List<RotationTestRunner.SlotAssignment> slotAssignments) {
            return new AssignmentResult(true, assignment, candidatesTried, null, null, null,
                slotAssignments != null ? slotAssignments : List.of());
        }

        public static AssignmentResult failure(Long failedTeacherId, String reason,
                                                int candidatesTried, FailureAnalysis analysis) {
            return new AssignmentResult(false, null, candidatesTried, failedTeacherId, reason, analysis, List.of());
        }
    }

    /**
     * Placement mode for a (teacher, group) pair.
     *
     * <p>Determines how the algorithm assigns days to blocks:</p>
     * <ul>
     *   <li>SINGLE_DAY — All blocks placed on one day (from LabRotation or CSP pick)</li>
     *   <li>MULTI_DAY_DETERMINISTIC — Blocks have different LabRotation days; each goes to its own day</li>
     *   <li>MULTI_DAY_CSP — No LabRotation; spread blocks greedily across best-fit days</li>
     * </ul>
     */
    enum PairPlacementMode {
        SINGLE_DAY,
        MULTI_DAY_DETERMINISTIC,
        MULTI_DAY_CSP
    }

    // ========== Helper Methods ==========

    /**
     * Update the newLessonDates cache to track lesson hours placed during pre-assignment.
     * This maintains H11 (maxHoursPerDay) constraint validity across placements.
     *
     * <p>External teacher hours (e.g., a visiting teacher) are NOT tracked: the
     * {@code external} flag is the data-driven signal that a teacher's hours follow
     * their own availability rather than the group grid, so counting them would
     * incorrectly block other (non-external) teachers from placing on the same
     * day. No teacher carries the flag on the 2026/27 seed (verified TASK-440).</p>
     *
     * @param ctx Pre-assignment context containing the cache
     * @param groupId Student group ID
     * @param date Lesson date
     * @param hours Number of hours to add (typically session.lessons.size())
     * @param teacher The teacher (external teachers are skipped)
     */
    private void updateNewLessonDatesCache(BacktrackContext ctx, Long groupId, LocalDate date, int hours, Teacher teacher) {
        if (ctx.newLessonDates == null || groupId == null) {
            return;
        }
        // Skip external teachers — their hours don't count against H11 for other teachers
        if (teacher != null && teacher.external) {
            return;
        }
        ctx.newLessonDates
            .computeIfAbsent(groupId, k -> new HashMap<>())
            .merge(date, hours, Integer::sum);
    }

    // ========== Inner Classes ==========

    /**
     * Context for the backtracking search.
     * Tracks state that changes during assignment.
     */
    private static class BacktrackContext {
        // Ordered list of (teacher, group) pairs to assign
        List<BlockKey> pairOrder;

        // BlockKey → List of LabBlocks for this pair
        Map<BlockKey, List<LabSession>> blocksByPair;

        // BlockKey → List of valid days for this pair
        Map<BlockKey, List<DayOfWeek>> validDaysByPair;

        // Current pair→day assignment state (mutable during backtrack)
        Map<BlockKey, DayOfWeek> currentPairAssignment = new LinkedHashMap<>();

        // For tracking candidates tried
        int candidatesTried;

        // Track blocks we've tentatively assigned (for context cleanup)
        List<LabSession> tentativelyTrackedSessions = new ArrayList<>();

        // Accumulated slot assignments from all successfully placed pairs
        List<RotationTestRunner.SlotAssignment> accumulatedSlotAssignments = new ArrayList<>();

        // Placement mode per pair
        Map<BlockKey, PairPlacementMode> placementModeByPair;

        // Current phase (for logging)
        String phase;

        // H11: Daily hour limit enforcement
        // Maps: groupId -> (date -> existing lesson count)
        Map<Long, Map<LocalDate, Integer>> existingLessonCounts;
        // Maps: groupId -> (date -> new lesson count from this phase)
        Map<Long, Map<LocalDate, Integer>> newLessonDates;

        // IDEAL END DATE constraint: groupId -> ideal end date
        // Pre-assignment must never place lessons after this date
        Map<Long, LocalDate> idealEndDatesByGroup;

        // STAGE BLACKOUT constraint: groupId -> list of stage patterns
        // Pre-assignment must never place lab lessons on stage days (when allowProfessionalizzantiDuringStage=false)
        Map<Long, List<StagePattern>> stagePatternsByGroup;

        /** Convert pair assignments to TeacherDayAssignment for backward compat. */
        TeacherDayAssignment toTeacherDayAssignment() {
            TeacherDayAssignment assignment = TeacherDayAssignment.empty();
            for (Map.Entry<BlockKey, DayOfWeek> entry : currentPairAssignment.entrySet()) {
                assignment = assignment.withTeacher(entry.getKey().teacherId(), entry.getValue());
            }
            return assignment;
        }
    }

    /**
     * Assign teachers to days using CSP backtracking.
     *
     * @param roomTag the room tag to process (e.g., "acconciatura")
     * @param blocksByKeys Map from BlockKey to LabBlocks (from HybridPhase1aService)
     * @param config Test configuration
     * @return AssignmentResult with success/failure
     */
    public AssignmentResult assignTeachers(
            String roomTag,
            Map<BlockKey, List<LabSession>> blocksByKeys,
            RotationTestRunner.TestConfig config) {

        long startTime = System.currentTimeMillis();
        LOG.infof("[GreedyAssigner] Starting assignment for roomTag=%s", roomTag);

        // Ensure context is initialized (timeslot map, room map, conflict sets)
        if (!context.isInitialized()) {
            context.initialize();
        }

        // Build backtracking context
        BacktrackContext ctx = buildBacktrackContext(roomTag, blocksByKeys, config);

        if (ctx.pairOrder.isEmpty()) {
            LOG.infof("[GreedyAssigner] No pairs to assign for roomTag=%s", roomTag);
            return AssignmentResult.success(TeacherDayAssignment.empty(), 0, List.of());
        }

        LOG.infof("[GreedyAssigner] %d (teacher,group) pairs to assign: %s",
            ctx.pairOrder.size(), ctx.pairOrder);

        // Start recursive backtracking from first pair
        boolean result = backtrackAssign(ctx, 0, config);

        long elapsed = System.currentTimeMillis() - startTime;
        if (result) {
            // Check for truly empty assignment — no pairs placed at all
            if (ctx.currentPairAssignment.isEmpty()) {
                LOG.errorf("[GreedyAssigner] FAILURE: All %d pairs skipped (no blocks placed) in %dms",
                    ctx.pairOrder.size(), elapsed);
                return AssignmentResult.failure(
                    ctx.pairOrder.get(0).teacherId(),
                    "No pair could be assigned any day",
                    ctx.candidatesTried,
                    buildFailureAnalysis(ctx, config)
                );
            }
            LOG.infof("[GreedyAssigner] SUCCESS: Found valid assignment in %dms after %d candidates (%d slots, %d pairs)",
                elapsed, ctx.candidatesTried, ctx.accumulatedSlotAssignments.size(), ctx.currentPairAssignment.size());
            return AssignmentResult.success(ctx.toTeacherDayAssignment(), ctx.candidatesTried,
                ctx.accumulatedSlotAssignments);
        } else {
            LOG.errorf("[GreedyAssigner] FAILURE: No valid assignment found after %d candidates in %dms",
                ctx.candidatesTried, elapsed);
            return AssignmentResult.failure(
                ctx.pairOrder.isEmpty() ? null : ctx.pairOrder.get(0).teacherId(),
                "No valid day assignment found",
                ctx.candidatesTried,
                buildFailureAnalysis(ctx, config)
            );
        }
    }

    /**
     * Assign teachers to days using CSP backtracking with H11 daily limit enforcement.
     *
     * @param roomTag the room tag to process (e.g., "acconciatura")
     * @param blocksByKeys Map from BlockKey to LabBlocks (from HybridPhase1aService)
     * @param config Test configuration
     * @param existingLessonCounts H11 cache: groupId -> (date -> existing lesson count)
     * @param newLessonDates H11 cache: groupId -> (date -> new lesson count from this phase)
     * @return AssignmentResult with success/failure
     */
    public AssignmentResult assignTeachers(
            String roomTag,
            Map<BlockKey, List<LabSession>> blocksByKeys,
            RotationTestRunner.TestConfig config,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            Map<Long, Map<LocalDate, Integer>> newLessonDates) {
        return assignTeachers(roomTag, blocksByKeys, config,
                existingLessonCounts, newLessonDates, null);
    }

    /**
     * TASK-467 AC#4 wiring: the multi-candidate entry point. Identical to the H11-aware
     * {@link #assignTeachers(String, Map, RotationTestRunner.TestConfig, Map, Map)} except the
     * backtracking's per-pair valid days are INTERSECTED with the enumerator's plan
     * ({@code restrictDaysByPair}: per (teacher, group) pair, the weekdays where that GROUP
     * sits in the teacher's ranked timeline - the quantity that actually VARIES across ranked
     * candidates, so distinct plans are distinct restrictions). {@code null} or a missing pair
     * = unconstrained (current behaviour). An empty
     * intersection makes that pair infeasible, so a plan the input data contradicts fails fast
     * and the caller tries the next ranked candidate: that IS the multi-candidate mechanism,
     * layered on the unchanged slot-finding/validation/pin machinery below.
     */
    public AssignmentResult assignTeachers(
            String roomTag,
            Map<BlockKey, List<LabSession>> blocksByKeys,
            RotationTestRunner.TestConfig config,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            Map<Long, Map<LocalDate, Integer>> newLessonDates,
            Map<BlockKey, Set<DayOfWeek>> restrictDaysByPair) {

        long startTime = System.currentTimeMillis();
        boolean restricted = restrictDaysByPair != null && !restrictDaysByPair.isEmpty();
        LOG.infof("[GreedyAssigner] Starting assignment for roomTag=%s with H11 enforcement%s",
                roomTag, restricted ? " (day restriction applied)" : "");

        // Ensure context is initialized (timeslot map, room map, conflict sets)
        if (!context.isInitialized()) {
            context.initialize();
        }
        BacktrackContext ctx = buildBacktrackContext(roomTag, blocksByKeys, config,
                existingLessonCounts, newLessonDates);
        if (restricted) {
            Set<BlockKey> contradicted = restrictValidDays(ctx.validDaysByPair, restrictDaysByPair);
            LOG.infof("[GreedyAssigner] Day restriction applied: %d pair(s) restricted",
                    restrictDaysByPair.size());
            if (!contradicted.isEmpty()) {
                // NOT a silent skip: a pair the plan empties would be dropped by the
                // backtracking below while the run reports success (shell-game). This
                // plan contradicts the input facts, so it FAILS and the caller moves
                // to the next ranked candidate. Blame is deterministic: the earliest
                // contradicted pair in pairOrder (most-constrained-first), not HashSet
                // iteration order. No failure analysis: the plan loop reads only the
                // reason, and buildFailureAnalysis costs DB lookups per rejected plan.
                BlockKey first = ctx.pairOrder.stream().filter(contradicted::contains).findFirst()
                        .orElse(contradicted.iterator().next());
                return AssignmentResult.failure(first.teacherId(),
                        "Day restriction leaves " + contradicted.size()
                                + " pair(s) with no valid day (restriction infeasible): first " + first,
                        ctx.candidatesTried, null);
            }
        }
        if (ctx.pairOrder.isEmpty()) {
            LOG.infof("[GreedyAssigner] No pairs to assign for roomTag=%s", roomTag);
            return AssignmentResult.success(TeacherDayAssignment.empty(), 0, List.of());
        }

        LOG.infof("[GreedyAssigner] %d (teacher,group) pairs to assign: %s",
                ctx.pairOrder.size(), ctx.pairOrder);

        // Start recursive backtracking from first pair
        boolean result = backtrackAssign(ctx, 0, config);

        long elapsed = System.currentTimeMillis() - startTime;
        if (result && ctx.currentPairAssignment.isEmpty()) {
            // Truly empty assignment: no pairs placed at all
            LOG.errorf("[GreedyAssigner] FAILURE: All %d pairs skipped (no blocks placed) in %dms",
                    ctx.pairOrder.size(), elapsed);
            return AssignmentResult.failure(
                    ctx.pairOrder.get(0).teacherId(),
                    "No pair could be assigned any day" + (restricted ? " (restriction infeasible)" : ""),
                    ctx.candidatesTried, buildFailureAnalysis(ctx, config));
        }
        if (result) {
            LOG.infof("[GreedyAssigner] SUCCESS: Found valid assignment in %dms after %d candidates (%d slots, %d pairs)",
                    elapsed, ctx.candidatesTried, ctx.accumulatedSlotAssignments.size(),
                    ctx.currentPairAssignment.size());
            return AssignmentResult.success(ctx.toTeacherDayAssignment(), ctx.candidatesTried,
                    ctx.accumulatedSlotAssignments);
        }
        LOG.errorf("[GreedyAssigner] FAILURE: No valid assignment found after %d candidates in %dms",
                ctx.candidatesTried, elapsed);
        return AssignmentResult.failure(
                ctx.pairOrder.isEmpty() ? null : ctx.pairOrder.get(0).teacherId(),
                "No valid day assignment found" + (restricted ? " (restriction infeasible)" : ""),
                ctx.candidatesTried, buildFailureAnalysis(ctx, config));
    }

    /**
     * The pure intersection of the greedy's per-pair valid days with the enumerator plan
     * (package-private: unit-testable without a context or DB). Returns the pairs the plan
     * CONTRADICTS (non-empty before, empty after): the caller treats those as "this plan
     * is infeasible, next ranked candidate" instead of letting the backtracking silently
     * drop them. A pair that was already empty stays the baseline's own pre-existing skip.
     */
    static Set<BlockKey> restrictValidDays(Map<BlockKey, List<DayOfWeek>> validDaysByPair,
            Map<BlockKey, Set<DayOfWeek>> restrictDaysByPair) {
        Set<BlockKey> contradicted = new HashSet<>();
        for (Map.Entry<BlockKey, List<DayOfWeek>> e : validDaysByPair.entrySet()) {
            Set<DayOfWeek> allowed = restrictDaysByPair.get(e.getKey());
            if (allowed == null) {
                continue;
            }
            List<DayOfWeek> restricted = e.getValue().stream()
                    .filter(allowed::contains)
                    .collect(Collectors.toList());
            if (restricted.isEmpty() && !e.getValue().isEmpty()) {
                contradicted.add(e.getKey());
            }
            e.setValue(restricted);
        }
        return contradicted;
    }

    /**
     * Build the backtracking context from the blocks and room tag.
     */
    private BacktrackContext buildBacktrackContext(
            String roomTag,
            Map<BlockKey, List<LabSession>> blocksByKeys,
            RotationTestRunner.TestConfig config) {

        BacktrackContext ctx = new BacktrackContext();
        ctx.phase = roomTag;
        ctx.candidatesTried = 0;
        ctx.blocksByPair = new LinkedHashMap<>(blocksByKeys);
        ctx.validDaysByPair = new LinkedHashMap<>();

        // Compute valid days and placement mode for each (teacher, group) pair
        ctx.placementModeByPair = new LinkedHashMap<>();
        ctx.existingLessonCounts = new HashMap<>();
        ctx.newLessonDates = new HashMap<>();
        for (Map.Entry<BlockKey, List<LabSession>> entry : ctx.blocksByPair.entrySet()) {
            BlockKey key = entry.getKey();
            List<LabSession> blocks = entry.getValue();

            ctx.validDaysByPair.put(key, getValidDaysForPair(key, blocks, config));

            // Classify placement mode based on rotation data
            Set<DayOfWeek> rotDays = new LinkedHashSet<>();
            boolean hasOverflowBlocks = false;
            for (LabSession b : blocks) {
                if (b.labRotation != null && b.labRotation.dayOfWeek != null) {
                    rotDays.add(b.labRotation.dayOfWeek);
                } else {
                    hasOverflowBlocks = true;
                }
            }

            PairPlacementMode mode;
            if (hasOverflowBlocks && !rotDays.isEmpty()) {
                // Mixed: some blocks have rotation, some overflow to CSP
                mode = PairPlacementMode.MULTI_DAY_DETERMINISTIC;
                LOG.infof("[GreedyAssigner] Mode for %s: MULTI_DAY_DETERMINISTIC (mixed)", key);
            } else if (rotDays.size() > 1) {
                mode = PairPlacementMode.MULTI_DAY_DETERMINISTIC;
                LOG.infof("[GreedyAssigner] Mode for %s: MULTI_DAY_DETERMINISTIC (multi-rot)", key);
            } else if (rotDays.size() == 1) {
                mode = PairPlacementMode.SINGLE_DAY;
                LOG.infof("[GreedyAssigner] Mode for %s: SINGLE_DAY", key);
            } else {
                mode = PairPlacementMode.MULTI_DAY_CSP;
                LOG.infof("[GreedyAssigner] Mode for %s: MULTI_DAY_CSP", key);
            }
            ctx.placementModeByPair.put(key, mode);

            LOG.infof("[GreedyAssigner] Set placement mode for %s: %s", key, mode);
        }
        LOG.infof("[GreedyAssigner] Finished processing all pairs in buildBacktrackContext");

        LOG.infof("[GreedyAssigner] About to call orderPairsByTightness");
        // Order pairs by constraint tightness
        ctx.pairOrder = orderPairsByTightness(ctx);

        LOG.debugf("[GreedyAssigner] Pair order by tightness: %s", ctx.pairOrder);
        LOG.debugf("[GreedyAssigner] Valid days per pair: %s", ctx.validDaysByPair);

        return ctx;
    }

    /**
     * Build the backtracking context from the blocks and room tag with H11 cache.
     */
    private BacktrackContext buildBacktrackContext(
            String roomTag,
            Map<BlockKey, List<LabSession>> blocksByKeys,
            RotationTestRunner.TestConfig config,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            Map<Long, Map<LocalDate, Integer>> newLessonDates) {

        LOG.infof("[GreedyAssigner] buildBacktrackContext: Starting with %d pairs", blocksByKeys.size());

        BacktrackContext ctx = new BacktrackContext();
        ctx.phase = roomTag;
        ctx.candidatesTried = 0;
        ctx.blocksByPair = new LinkedHashMap<>(blocksByKeys);
        ctx.validDaysByPair = new LinkedHashMap<>();
        ctx.placementModeByPair = new LinkedHashMap<>();
        ctx.existingLessonCounts = existingLessonCounts;
        ctx.newLessonDates = newLessonDates;

        LOG.infof("[GreedyAssigner] H11 cache provided: %d groups in existingLessonCounts",
            existingLessonCounts != null ? existingLessonCounts.size() : 0);

        // Compute ideal end dates for all groups in this phase
        // Pre-assignment must never place lessons after this date
        ctx.idealEndDatesByGroup = computeIdealEndDatesForPairs(blocksByKeys, config);

        // Load stage patterns for stage blackout filtering
        // Pre-assignment must never place lab lessons on stage days when allowProfessionalizzantiDuringStage=false
        ctx.stagePatternsByGroup = computeStagePatternsByPairs(blocksByKeys);

        // Determine valid days for each pair
        for (Map.Entry<BlockKey, List<LabSession>> entry : blocksByKeys.entrySet()) {
            BlockKey key = entry.getKey();
            LOG.infof("[GreedyAssigner] Processing pair %s (blocks: %d)", key, entry.getValue().size());
            List<LabSession> blocks = entry.getValue();

            List<DayOfWeek> validDays = getValidDaysForPair(key, blocks, config);
            ctx.validDaysByPair.put(key, validDays);

            LOG.infof("[GreedyAssigner] After getValidDaysForPair for %s", key);

            // Determine placement mode
            Set<DayOfWeek> rotDays = new LinkedHashSet<>();
            boolean hasOverflowBlocks = false;
            LOG.infof("[GreedyAssigner] Starting placement mode loop for %s (blocks size: %d)", key, blocks.size());
            for (LabSession session : blocks) {
                LOG.infof("[GreedyAssigner] Checking session %s", session);
                if (session.labRotation != null && session.labRotation.dayOfWeek != null) {
                    rotDays.add(session.labRotation.dayOfWeek);
                } else {
                    hasOverflowBlocks = true;
                }
            }
            LOG.infof("[GreedyAssigner] Finished placement mode loop for %s (rotDays: %s, hasOverflow: %s)",
                key, rotDays, hasOverflowBlocks);

            LOG.infof("[GreedyAssigner] Before if-else for %s", key);
            PairPlacementMode mode;
            if (hasOverflowBlocks && !rotDays.isEmpty()) {
                // Mixed: some blocks have rotation, some overflow to CSP
                mode = PairPlacementMode.MULTI_DAY_DETERMINISTIC;
                LOG.infof("[GreedyAssigner] Mode for %s: MULTI_DAY_DETERMINISTIC (mixed)", key);
            } else if (rotDays.size() > 1) {
                mode = PairPlacementMode.MULTI_DAY_DETERMINISTIC;
                LOG.infof("[GreedyAssigner] Mode for %s: MULTI_DAY_DETERMINISTIC (multi-rot)", key);
            } else if (rotDays.size() == 1) {
                mode = PairPlacementMode.SINGLE_DAY;
                LOG.infof("[GreedyAssigner] Mode for %s: SINGLE_DAY", key);
            } else {
                mode = PairPlacementMode.MULTI_DAY_CSP;
                LOG.infof("[GreedyAssigner] Mode for %s: MULTI_DAY_CSP", key);
            }
            ctx.placementModeByPair.put(key, mode);

            LOG.infof("[GreedyAssigner] Set placement mode for %s: %s", key, mode);
        }
        LOG.infof("[GreedyAssigner] Finished processing all pairs in buildBacktrackContext");

        LOG.infof("[GreedyAssigner] About to call orderPairsByTightness");
        // Order pairs by constraint tightness
        ctx.pairOrder = orderPairsByTightness(ctx);

        LOG.debugf("[GreedyAssigner] Pair order by tightness: %s", ctx.pairOrder);
        LOG.debugf("[GreedyAssigner] Valid days per pair: %s", ctx.validDaysByPair);

        return ctx;
    }

    /**
     * Compute ideal end dates for all student groups in the given pairs.
     * Pre-assignment must never place lessons after these dates.
     *
     * Delegates to TargetEndDateCalculator.buildIdealEndDatesMap() to centralize
     * the computation and avoid code duplication.
     *
     * @return Map of groupId -> ideal end date
     */
    private Map<Long, LocalDate> computeIdealEndDatesForPairs(
            Map<BlockKey, List<LabSession>> blocksByKeys,
            RotationTestRunner.TestConfig config) {

        // Collect unique student groups from all pairs
        Set<Long> groupIds = new LinkedHashSet<>();
        for (BlockKey key : blocksByKeys.keySet()) {
            groupIds.add(key.studentGroupId());
        }

        // Use centralized method to build ideal end dates map
        Map<Long, LocalDate> idealEndDates = targetEndDateCalculator.buildIdealEndDatesMap(groupIds);

        // Log the computed dates for debugging
        for (var entry : idealEndDates.entrySet()) {
            StudentGroup group = StudentGroup.findById(entry.getKey());
            if (group != null && group.idealEndDateInfo != null) {
                LOG.infof("[GreedyAssigner] Group %s: idealEndDate = %s (totalHours=%d, availableDays=%d)",
                    group.name, group.idealEndDateInfo.idealEndDate(),
                    group.idealEndDateInfo.totalHours(), group.idealEndDateInfo.availableDays());
            }
        }

        return idealEndDates;
    }

    /**
     * Compute stage patterns for all student groups in the given pairs.
     * Used to filter out stage days when allowProfessionalizzantiDuringStage=false.
     *
     * @return Map of groupId -> list of stage patterns
     */
    private Map<Long, List<StagePattern>> computeStagePatternsByPairs(
            Map<BlockKey, List<LabSession>> blocksByKeys) {

        // Collect unique student groups from all pairs
        Set<Long> groupIds = new LinkedHashSet<>();
        for (BlockKey key : blocksByKeys.keySet()) {
            groupIds.add(key.studentGroupId());
        }

        Map<Long, List<StagePattern>> stagePatternsByGroup = new LinkedHashMap<>();

        // Load stage patterns for all groups
        List<StagePattern> allPatterns = StagePattern.listAll();
        
        // Group patterns by student group
        for (StagePattern sp : allPatterns) {
            if (sp.studentGroup != null && groupIds.contains(sp.studentGroup.id)) {
                stagePatternsByGroup
                    .computeIfAbsent(sp.studentGroup.id, k -> new ArrayList<>())
                    .add(sp);
            }
        }

        // Log the loaded patterns for debugging
        for (var entry : stagePatternsByGroup.entrySet()) {
            StudentGroup group = StudentGroup.findById(entry.getKey());
            if (group != null) {
                LOG.infof("[GreedyAssigner] Group %s: has %d stage pattern(s)",
                    group.name, entry.getValue().size());
            }
        }

        return stagePatternsByGroup;
    }

    /**
     * Check if a date is a stage day for the given group.
     * A date is a stage day if it falls within any stage period AND matches the dayOfWeekMask.
     *
     * @param date The date to check
     * @param group The student group
     * @param stagePatterns List of stage patterns for the group
     * @return true if the date is a stage day
     */
    private boolean isStageDay(LocalDate date, StudentGroup group, List<StagePattern> stagePatterns) {
        if (stagePatterns == null || stagePatterns.isEmpty()) {
            return false;
        }

        DayOfWeek dow = date.getDayOfWeek();
        return stagePatterns.stream().anyMatch(sp ->
            !date.isBefore(sp.startDate) &&
            !date.isAfter(sp.endDate) &&
            sp.dayOfWeekMask.contains(dow)
        );
    }

    /**
     * Order pairs by constraint tightness.
     *
     * <p>Strategy:</p>
     * <ul>
     *   <li>MULTI_DAY_DETERMINISTIC first (0 flexibility — days are fixed)</li>
     *   <li>SINGLE_DAY next, fewer valid days first</li>
     *   <li>MULTI_DAY_CSP last (most flexible — can spread across days)</li>
     *   <li>Within same flexibility: more blocks = higher priority</li>
     * </ul>
     */
    private List<BlockKey> orderPairsByTightness(BacktrackContext ctx) {
        return ctx.blocksByPair.keySet().stream()
            .sorted((a, b) -> {
                // First: flexibility score (lower = more constrained = higher priority)
                int flexA = getFlexibility(ctx, a);
                int flexB = getFlexibility(ctx, b);
                int flexCompare = Integer.compare(flexA, flexB);
                if (flexCompare != 0) return flexCompare;

                // Second: more blocks = harder to place = higher priority
                int blocksA = ctx.blocksByPair.getOrDefault(a, List.of()).size();
                int blocksB = ctx.blocksByPair.getOrDefault(b, List.of()).size();
                int blocksCompare = Integer.compare(blocksB, blocksA);
                if (blocksCompare != 0) return blocksCompare;

                // Third: later periodStart = fewer available dates = more constrained
                // Prevents early-starting rotations from consuming dates needed by
                // later-starting rotations (e.g., G12 starting Feb 1 vs G06
                // starting Sep 10 sharing the same Thursday).
                LocalDate startA = getEarliestPeriodStart(ctx, a);
                LocalDate startB = getEarliestPeriodStart(ctx, b);
                // Descending: later start date first
                return startB.compareTo(startA);
            })
            .collect(Collectors.toList());
    }

    private LocalDate getEarliestPeriodStart(BacktrackContext ctx, BlockKey key) {
        List<LabSession> blocks = ctx.blocksByPair.getOrDefault(key, List.of());
        LocalDate earliest = LocalDate.MAX;
        for (LabSession block : blocks) {
            if (block.labRotation != null && block.labRotation.periodStart != null) {
                if (block.labRotation.periodStart.isBefore(earliest)) {
                    earliest = block.labRotation.periodStart;
                }
            }
        }
        return earliest;
    }

    private int getFlexibility(BacktrackContext ctx, BlockKey key) {
        PairPlacementMode mode = ctx.placementModeByPair.getOrDefault(key, PairPlacementMode.SINGLE_DAY);
        int validDayCount = ctx.validDaysByPair.getOrDefault(key, List.of()).size();
        return switch (mode) {
            case MULTI_DAY_DETERMINISTIC -> 0;            // No choices — all days required
            case SINGLE_DAY -> validDayCount;             // Number of day choices
            case MULTI_DAY_CSP -> validDayCount + 100;    // Most flexible (greedy overflow)
        };
    }

    /**
     * Get valid days for a (teacher, group) pair.
     *
     * <p>Two-tier constraint model:</p>
     * <ol>
     *   <li>If LabRotation exists for this pair → use ONLY that day (deterministic).
     *       This covers acconciatura and meccanica where the school assigns fixed days.</li>
     *   <li>If no LabRotation → filter by H5 VACATION records and return all valid days
     *       for CSP search. This covers moda, informatica, termoidraulica.</li>
     * </ol>
     *
     * @param key the (teacher, group) pair
     * @param blocks the blocks for this pair (used to extract rotation day)
     * @param config test configuration
     * @return list of valid days in priority order
     */
    private List<DayOfWeek> getValidDaysForPair(BlockKey key, List<LabSession> blocks, RotationTestRunner.TestConfig config) {
        Long teacherId = key.teacherId();

        // 1. Start with ALL weekdays
        Set<DayOfWeek> validDays = new HashSet<>(DAY_PRIORITY);

        // 2. Remove VACATION days for this teacher
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

        // 3. Collect ALL distinct rotation days from this pair's blocks
        Set<DayOfWeek> rotationDays = new LinkedHashSet<>();
        boolean hasOverflowBlocks = false;
        for (LabSession session : blocks) {
            if (session.labRotation != null && session.labRotation.dayOfWeek != null) {
                rotationDays.add(session.labRotation.dayOfWeek);
            } else {
                hasOverflowBlocks = true;
            }
        }

        // 4a. ALL blocks have rotation → use ONLY rotation day(s) (pure deterministic)
        if (!rotationDays.isEmpty() && !hasOverflowBlocks) {
            List<DayOfWeek> result = DAY_PRIORITY.stream()
                .filter(d -> rotationDays.contains(d) && validDays.contains(d))
                .collect(Collectors.toList());

            if (result.size() != rotationDays.size()) {
                Set<DayOfWeek> blocked = new LinkedHashSet<>(rotationDays);
                blocked.removeAll(validDays);
                if (!blocked.isEmpty()) {
                    LOG.warnf("[GreedyAssigner] Pair %s: rotation day(s) %s blocked by VACATION", key, blocked);
                }
            }

            LOG.infof("[GreedyAssigner] Pair %s constrained to rotation day(s): %s", key, result);
            return result;
        }

        // 4b. Mixed: some blocks have rotation, some overflow → rotation days first, then others
        if (!rotationDays.isEmpty() && hasOverflowBlocks) {
            List<DayOfWeek> result = new ArrayList<>();
            // Rotation days first (for deterministic blocks)
            for (DayOfWeek d : DAY_PRIORITY) {
                if (rotationDays.contains(d) && validDays.contains(d)) {
                    result.add(d);
                }
            }
            // Then non-rotation days (for CSP overflow)
            for (DayOfWeek d : DAY_PRIORITY) {
                if (!rotationDays.contains(d) && validDays.contains(d)) {
                    result.add(d);
                }
            }
            LOG.infof("[GreedyAssigner] Pair %s: mixed mode — rotation %s + overflow, valid: %s",
                key, rotationDays, result);
            return result;
        }

        // 5. No LabRotation → return all valid days for CSP search
        List<DayOfWeek> result = DAY_PRIORITY.stream()
            .filter(validDays::contains)
            .collect(Collectors.toList());

        LOG.infof("[GreedyAssigner] Pair %s: no rotation, valid days: %s (vacation days: %s)",
            key, result, vacationDays);

        return result;
    }

    /**
     * Recursive backtracking assignment over (teacher, group) pairs.
     *
     * <p>Dispatches to the appropriate placement strategy based on the pair's
     * {@link PairPlacementMode}. MULTI_DAY pairs use {@link #tryPlaceBlocksMultiDay},
     * SINGLE_DAY pairs use the day-selection loop with {@link #tryPlaceBlocksForPair}.</p>
     *
     * <p>IMPORTANT: Supports PARTIAL success. If at least one block can be placed
     * for a pair, the assignment is considered valid. Overflow blocks are left
     * unassigned for the solver to handle.</p>
     *
     * @param ctx backtracking context
     * @param pairIndex current pair index in pairOrder
     * @param config test configuration
     * @return true if assignment succeeded (even partially), false if complete failure
     */
    private boolean backtrackAssign(
            BacktrackContext ctx,
            int pairIndex,
            RotationTestRunner.TestConfig config) {

        // Base case: all pairs processed
        if (pairIndex >= ctx.pairOrder.size()) {
            return true;
        }

        BlockKey pairKey = ctx.pairOrder.get(pairIndex);
        List<DayOfWeek> validDays = ctx.validDaysByPair.getOrDefault(pairKey, List.of());

        if (validDays.isEmpty()) {
            LOG.warnf("[GreedyAssigner] Pair %s has no valid days!", pairKey);
            return backtrackAssign(ctx, pairIndex + 1, config);
        }

        PairPlacementMode mode = ctx.placementModeByPair.getOrDefault(pairKey, PairPlacementMode.SINGLE_DAY);
        LOG.debugf("[GreedyAssigner] Trying pair %s at index %d, mode=%s, valid days: %s",
            pairKey, Integer.valueOf(pairIndex), mode, validDays);

        // Dispatch based on placement mode
        PlacementResult bestResult = null;
        DayOfWeek bestDay = null;

        if (mode == PairPlacementMode.MULTI_DAY_DETERMINISTIC || mode == PairPlacementMode.MULTI_DAY_CSP) {
            // Multi-day: place blocks across multiple days at once
            bestResult = tryPlaceBlocksMultiDay(ctx, pairKey, validDays, config);
            if (bestResult != null && bestResult.hasPartialSuccess()) {
                bestDay = bestResult.slotAssignments().get(0).startTimeslot().startDateTime.getDayOfWeek();
            }
        } else {
            // SINGLE_DAY: try each valid day independently, pick best
            // Check if other pairs share this same single day — if so, limit blocks
            // to 1 per round to prevent one pair from monopolizing the shared day.
            for (DayOfWeek day : validDays) {
                ctx.candidatesTried++;

                if (ctx.candidatesTried > MAX_BACKTRACK_DEPTH * 10) {
                    LOG.warnf("[GreedyAssigner] Exceeded candidate limit at %d", ctx.candidatesTried);
                    return false;
                }

                LOG.debugf("[GreedyAssigner] Trying day %s for pair %s (candidate #%d)",
                    day, pairKey, ctx.candidatesTried);

                PlacementResult result = tryPlaceBlocksForPair(ctx, pairKey, day, config);

                if (bestResult == null || result.blocksPlaced() > bestResult.blocksPlaced()) {
                    bestResult = result;
                    bestDay = day;
                }

                if (result.allPlaced()) {
                    break;
                }
            }
        }

        // Accept the best placement (even if partial)
        if (bestResult != null && bestResult.hasPartialSuccess()) {
            if (bestDay != null) {
                ctx.currentPairAssignment.put(pairKey, bestDay);
            }

            // Track blocks in context so subsequent pairs see them
            for (RotationTestRunner.SlotAssignment slot : bestResult.slotAssignments()) {
                trackTentativeSession(ctx, slot.session(), slot);
            }

            // H11: newLessonDates already updated by updateNewLessonDatesCache() calls
            // inside tryPlaceBlocksMultiDay (per-session). No pair-level update needed here.
            // NOTE: Previous code had a duplicate pair-level update here that caused
            // double-counting (each session's hours were added twice).

            int prevSlotCount = ctx.accumulatedSlotAssignments.size();
            ctx.accumulatedSlotAssignments.addAll(bestResult.slotAssignments());

            String daysSummary = bestResult.slotAssignments().stream()
                .map(s -> s.startTimeslot().startDateTime.getDayOfWeek().toString())
                .distinct().collect(Collectors.joining(","));
            LOG.infof("[GreedyAssigner] Assigned pair %s (%s mode, %d/%d blocks on [%s])",
                pairKey, mode, bestResult.blocksPlaced(), bestResult.totalBlocks(), daysSummary);

            boolean success = backtrackAssign(ctx, pairIndex + 1, config);
            if (success) {
                return true;
            }

            // Backtrack: undo this pair's assignment
            ctx.currentPairAssignment.remove(pairKey);
            cleanupTentativeSessions(ctx);
            while (ctx.accumulatedSlotAssignments.size() > prevSlotCount) {
                ctx.accumulatedSlotAssignments.remove(ctx.accumulatedSlotAssignments.size() - 1);
            }

            // H11: Clean up newLessonDates by removing hours we added for this pair
            // (skip external teachers — their hours were never added)
            for (RotationTestRunner.SlotAssignment slot : bestResult.slotAssignments()) {
                Teacher slotTeacher = slot.session().getTeacher();
                if (slotTeacher != null && slotTeacher.external) {
                    continue;
                }
                Long groupId = slot.key().studentGroupId();
                LocalDate lessonDate = slot.startTimeslot().startDateTime.toLocalDate();
                int hoursToRemove = slot.session().totalHours;

                Map<LocalDate, Integer> groupDates = ctx.newLessonDates.get(groupId);
                if (groupDates != null) {
                    Integer current = groupDates.get(lessonDate);
                    if (current != null) {
                        int newValue = current - hoursToRemove;
                        if (newValue <= 0) {
                            groupDates.remove(lessonDate);
                        } else {
                            groupDates.put(lessonDate, newValue);
                        }
                        LOG.debugf("[GreedyAssigner] H11: Removed %d hours for group %d on %s (remaining: %s)",
                            hoursToRemove, groupId, lessonDate, newValue > 0 ? newValue : "0 (removed)");
                    }
                }
            }

            LOG.debugf("[GreedyAssigner] Backtracking pair %s (mode=%s)", pairKey, mode);
        }

        LOG.warnf("[GreedyAssigner] Could not place ANY blocks for pair %s (mode=%s)", pairKey, mode);
        return backtrackAssign(ctx, pairIndex + 1, config);
    }

    /**
     * Result of trying to place blocks for a teacher on a specific day.
     */
    private record PlacementResult(
        int blocksPlaced,
        int totalBlocks,
        boolean allPlaced,
        List<RotationTestRunner.SlotAssignment> slotAssignments
    ) {
        /** At least some blocks could be placed */
        boolean hasPartialSuccess() {
            return blocksPlaced > 0;
        }
    }

    /**
     * Try to place all blocks for a (teacher, group) pair on a specific day.
     *
     * <p>Uses direct PreAssignmentContext O(1) checks instead of RotationTestRunner.
     * Blocks are tracked temporarily so subsequent blocks within the same pair
     * can see prior placements (avoiding H2 room conflicts on the same date).
     * All tracking is cleaned up before returning — the caller (backtrackAssign)
     * re-tracks the best result's blocks.</p>
     *
     * @param ctx backtracking context
     * @param pairKey the (teacher, group) pair
     * @param day the day to check
     * @param config test configuration
     * @return PlacementResult with count of blocks placed and their slot assignments
     */
    private PlacementResult tryPlaceBlocksForPair(
            BacktrackContext ctx,
            BlockKey pairKey,
            DayOfWeek day,
            RotationTestRunner.TestConfig config) {
        List<LabSession> pairBlocks = ctx.blocksByPair.getOrDefault(pairKey, List.of());
        int totalBlocks = pairBlocks.size();
        int placed = 0;
        List<RotationTestRunner.SlotAssignment> slotAssignments = new ArrayList<>();
        List<LabSession> attemptTrackedSessions = new ArrayList<>();
        Set<LocalDate> usedDates = new HashSet<>();
        Map<Long, Set<LocalDate>> courseUsedDates = new HashMap<>();

        for (LabSession session : pairBlocks) {

            Optional<RotationTestRunner.SlotAssignment> slotOpt =
                findValidSlotForBlock(session, day, config, usedDates, courseUsedDates, ctx);

            if (slotOpt.isEmpty()) {
                LOG.debugf("[GreedyAssigner] Block %d cannot be placed on %s for pair %s (capacity full)",
                    session.lessons.get(0).id, day, pairKey);
                continue;
            }

            RotationTestRunner.SlotAssignment slot = slotOpt.get();
            slotAssignments.add(slot);
            LocalDate slotDate = slot.startTimeslot().getDate();
            usedDates.add(slotDate);
            if (session.course != null) {
                courseUsedDates.computeIfAbsent(session.course.id, k -> new HashSet<>()).add(slotDate);
            }

            LabSession tempSession = createTempSession(slot.session(), slot);
            // Lessons already tracked in createTempSession
            attemptTrackedSessions.add(tempSession);
            placed++;
        }

        // Clean up this attempt's tracking — caller re-tracks the best result
        for (LabSession tracked : attemptTrackedSessions) {
            context.untrackSession(tracked);
        }

        if (placed < totalBlocks) {
            LOG.infof("[GreedyAssigner] PARTIAL: Placed %d/%d blocks for pair %s on %s (overflow to solver)",
                placed, totalBlocks, pairKey, day);
        } else {
            LOG.debugf("[GreedyAssigner] Placed all %d blocks for pair %s on %s",
                placed, pairKey, day);
        }

        return new PlacementResult(placed, totalBlocks, placed == totalBlocks, slotAssignments);
    }

    /**
    /**
     * Place blocks for a pair across multiple days.
     *
     * <p>Supports two strategies:</p>
     * <ul>
     *   <li>MULTI_DAY_DETERMINISTIC: each block goes to its own {@code labRotation.dayOfWeek}</li>
     *   <li>MULTI_DAY_CSP: blocks spread greedily across valid days (fill best day first,
     *       overflow to next day)</li>
     * </ul>
     *
     * <p>Blocks are tracked in context as they're placed, so later blocks see
     * earlier placements (avoiding H2 room conflicts on the same date).
     * All tracking is cleaned up before returning.</p>
     *
     * @param ctx backtracking context
     * @param pairKey the (teacher, group) pair
     * @param validDays valid days in priority order
     * @param config test configuration
     * @return PlacementResult with count of blocks placed and their slot assignments
     */
    private PlacementResult tryPlaceBlocksMultiDay(
            BacktrackContext ctx,
            BlockKey pairKey,
            List<DayOfWeek> validDays,
            RotationTestRunner.TestConfig config) {

        List<LabSession> pairBlocks = ctx.blocksByPair.getOrDefault(pairKey, List.of());
        int totalBlocks = pairBlocks.size();
        int placed = 0;
        List<RotationTestRunner.SlotAssignment> slotAssignments = new ArrayList<>();
        List<LabSession> attemptTrackedSessions = new ArrayList<>();
        Set<LocalDate> usedDates = new HashSet<>();
        Map<Long, Set<LocalDate>> courseUsedDates = new HashMap<>();

        PairPlacementMode mode = ctx.placementModeByPair.getOrDefault(pairKey, PairPlacementMode.MULTI_DAY_CSP);

        if (mode == PairPlacementMode.MULTI_DAY_DETERMINISTIC) {
            // Phase 1: Place sessions with labRotation on their specific day
            List<LabSession> overflowSessions = new ArrayList<>();
            for (LabSession session : pairBlocks) {
                if (session.labRotation != null && session.labRotation.dayOfWeek != null) {
                    DayOfWeek sessionDay = session.labRotation.dayOfWeek;

                    ctx.candidatesTried++;
                    Optional<RotationTestRunner.SlotAssignment> slotOpt =
                        findValidSlotForBlock(session, sessionDay, config, usedDates, courseUsedDates, ctx);

                    if (slotOpt.isEmpty()) {
                        LOG.debugf("[GreedyAssigner] Session (lesson %d) cannot be placed on %s (deterministic) → overflow",
                            session.lessons.isEmpty() ? -1 : session.lessons.get(0).id, sessionDay);
                        overflowSessions.add(session);
                        continue;
                    }

                    RotationTestRunner.SlotAssignment slot = slotOpt.get();
                    slotAssignments.add(slot);
                    LocalDate slotDate = slot.startTimeslot().getDate();
                    usedDates.add(slotDate);

                    // H11: Update newLessonDates cache to reflect this block placement
                    updateNewLessonDatesCache(ctx, session.course.studentGroup.id, slotDate, slot.session().lessons.size(), session.getTeacher());

                    LabSession tempSession = createTempSession(slot.session(), slot);
                    context.trackSession(tempSession);
                    attemptTrackedSessions.add(tempSession);
                    placed++;
                } else {
                    // No labRotation — capacity overflow, handle via CSP
                    overflowSessions.add(session);
                }
            }

            // Phase 2: CSP overflow for sessions without labRotation
            if (!overflowSessions.isEmpty()) {
                LOG.infof("[GreedyAssigner] Pair %s: %d overflow sessions → CSP across %s",
                    pairKey, overflowSessions.size(), validDays);
                for (DayOfWeek day : validDays) {
                    if (overflowSessions.isEmpty()) break;

                    Iterator<LabSession> it = overflowSessions.iterator();
                    while (it.hasNext()) {
                        LabSession session = it.next();
                        ctx.candidatesTried++;

                        Optional<RotationTestRunner.SlotAssignment> slotOpt =
                            findValidSlotForBlock(session, day, config, usedDates, courseUsedDates, ctx);

                        if (slotOpt.isPresent()) {
                            RotationTestRunner.SlotAssignment slot = slotOpt.get();
                            slotAssignments.add(slot);
                            LocalDate slotDate = slot.startTimeslot().getDate();
                            usedDates.add(slotDate);
                            if (session.course != null) {
                                courseUsedDates.computeIfAbsent(session.course.id, k -> new HashSet<>()).add(slotDate);
                            }

                            // H11: Update newLessonDates cache to reflect this block placement
                            updateNewLessonDatesCache(ctx,
                                session.course != null && session.course.studentGroup != null ? session.course.studentGroup.id : null,
                                slotDate, slot.session().lessons.size(), session.getTeacher());

                            LabSession tempSession = createTempSession(slot.session(), slot);
                            context.trackSession(tempSession);
                            attemptTrackedSessions.add(tempSession);
                            placed++;
                            it.remove();
                        }
                    }
                }
            }
        } else {
            // MULTI_DAY_CSP: spread sessions greedily across valid days
            // Sort days by teacher-day affinity (prefer days with existing pinned lessons)
            List<DayOfWeek> sortedDays = sortByTeacherDayAffinity(pairKey.teacherId(), validDays, config);
            List<LabSession> remaining = new ArrayList<>(pairBlocks);

            for (DayOfWeek day : sortedDays) {
                if (remaining.isEmpty()) break;

                Iterator<LabSession> it = remaining.iterator();
                while (it.hasNext()) {
                    LabSession session = it.next();
                    ctx.candidatesTried++;

                    Optional<RotationTestRunner.SlotAssignment> slotOpt =
                        findValidSlotForBlock(session, day, config, usedDates, courseUsedDates, ctx);

                    if (slotOpt.isPresent()) {
                        RotationTestRunner.SlotAssignment slot = slotOpt.get();
                        slotAssignments.add(slot);
                        LocalDate slotDate = slot.startTimeslot().getDate();
                        usedDates.add(slotDate);
                        if (session.course != null) {
                            courseUsedDates.computeIfAbsent(session.course.id, k -> new HashSet<>()).add(slotDate);
                        }

                        // H11: Update newLessonDates cache to reflect this block placement
                        updateNewLessonDatesCache(ctx,
                            session.course != null && session.course.studentGroup != null ? session.course.studentGroup.id : null,
                            slotDate, slot.session().lessons.size(), session.getTeacher());

                        LabSession tempSession = createTempSession(slot.session(), slot);
                        context.trackSession(tempSession);
                        attemptTrackedSessions.add(tempSession);
                        placed++;
                        it.remove();
                    }
                }
            }
        }

        // Clean up tracking — caller re-tracks the best result
        for (LabSession tracked : attemptTrackedSessions) {
            context.untrackSession(tracked);
        }

        String daysSummary = slotAssignments.stream()
            .map(s -> s.startTimeslot().startDateTime.getDayOfWeek().toString())
            .distinct().collect(Collectors.joining(","));

        if (placed < totalBlocks) {
            LOG.infof("[GreedyAssigner] PARTIAL multi-day: Placed %d/%d blocks for pair %s on [%s]",
                placed, totalBlocks, pairKey, daysSummary);
        } else {
            LOG.debugf("[GreedyAssigner] Placed all %d blocks for pair %s across [%s]",
                placed, pairKey, daysSummary);
        }

        return new PlacementResult(placed, totalBlocks, placed == totalBlocks, slotAssignments);
    }

    private Optional<RotationTestRunner.SlotAssignment> findValidSlotForBlock(
            LabSession session,
            DayOfWeek day,
            RotationTestRunner.TestConfig config,
            Set<LocalDate> usedDates,
            Map<Long, Set<LocalDate>> courseUsedDates,
            BacktrackContext ctx) {

        Teacher teacher = session.getTeacher();
        StudentGroup group = session.getStudentGroup();
        if (teacher == null || group == null) {
            LOG.debugf("[GreedyAssigner] Session has no teacher or group, skipping");
            return Optional.empty();
        }

        // 1. Determine period from labRotation (or config fallback)
        LocalDate periodStart = config.periodStart();
        LocalDate periodEnd = config.periodEnd();
        if (session.labRotation != null) {
            if (session.labRotation.periodStart != null) periodStart = session.labRotation.periodStart;
            if (session.labRotation.periodEnd != null) periodEnd = session.labRotation.periodEnd;
        }


        // 2. Get rooms for tag
        List<Room> rooms = context.getRoomsByTag(config.roomTag());
        if (rooms.isEmpty()) {
            LOG.debugf("[GreedyAssigner] No rooms found for tag=%s", config.roomTag());
            return Optional.empty();
        }
        LOG.debugf("[GreedyAssigner] Found %d rooms for tag=%s: %s", rooms.size(), config.roomTag(),
            rooms.stream().map(r -> r.name).toList());

        int blockSize = session.lessons.size();

        // 3. Enumerate dates matching day within period
        int datesChecked = 0;
        int datesSkippedWrongDay = 0;
        int datesSkippedHoliday = 0;
        int datesSkippedH5 = 0;
        int datesSkippedStage = 0;
        int datesSkippedH11 = 0;
        int datesSkippedUsed = 0;
        int datesSkippedIdealEnd = 0;
        // TASK-502: the two hard gates the round-2 treatment missed (H20/H7)
        int datesSkippedCalendarWindow = 0;
        int datesSkippedTeacherCap = 0;
        int datesTried = 0;
        int totalSlotsSkippedTimeslots = 0;
        int totalSlotsSkippedH1H3 = 0;
        int totalSlotsSkippedH2 = 0;

        // Get ideal end date for this group (critical constraint: never place lessons after this date)
        LocalDate idealEndDate = ctx.idealEndDatesByGroup != null
            ? ctx.idealEndDatesByGroup.get(group.id)
            : null;

        // Cache group-level properties outside date loop for efficiency
        List<StagePattern> groupStagePattern = ctx.stagePatternsByGroup != null ? ctx.stagePatternsByGroup.get(group.id) : null;

        // H11: This method places ONE session at a time, so use the session's blockSize.
        // (The old totalBlocksSize was incorrect for MULTI_DAY_CSP mode where sessions
        // are spread across multiple days — it summed ALL sessions for the pair.)
        int sessionBlockSize = session.lessons.size();
        // TASK-516 round 7: the placement size for the CURRENT date. Stays equal to
        // sessionBlockSize unless a positive reservation CLIPS the block (see the H7
        // gate below); every gate and the slot search read this, so a clipped block
        // is checked and placed at its clipped size end-to-end.
        int placementSize = sessionBlockSize;

        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            datesChecked++;
            if (date.getDayOfWeek() != day) { datesSkippedWrongDay++; continue; }
            if (config.holidays().contains(date)) { datesSkippedHoliday++; continue; }
            if (context.hasH5Violation(teacher, date)) {

                datesSkippedH5++;
                continue;
            }

            // Check stage blackout using consolidated utility
            if (StageEligibilityUtil.shouldSkipDate(session.course, group, date, groupStagePattern)) {
                boolean isProfCourse = session.course != null && session.course.professionalizzanti;
                LOG.infof("[GreedyAssigner] Skipping %s for group %s: stage blackout (profCourse=%s, allowDuringStage=%s)",
                    date, group.name, isProfCourse, group.allowProfessionalizzantiDuringStage);
                datesSkippedStage++;
                continue;
            }

            // Max 1 block per date per pair — skip dates already used
            if (usedDates.contains(date)) { datesSkippedUsed++; continue; }

            // H11: Check daily hour limit before checking slots on this day
            if (ctx.existingLessonCounts != null && ctx.newLessonDates != null
                    && wouldExceedDailyLimit(group, date, ctx.existingLessonCounts, ctx.newLessonDates, sessionBlockSize)) {
                datesSkippedH11++;
                continue;
            }
            placementSize = sessionBlockSize;

            // TASK-502 H20: the block must FIT the group's calendar window for this
            // weekday (GroupCalendarRule, the same source the constraint reads). Round 2
            // of the 364+467 chain pinned 8h blocks on weekdays whose window is narrower
            // and the solver inherited -21 H20 it cannot cure without moving
            // authoritative pins. No rule for (group, weekday) = fully open (TASK-342
            // backward compatibility, same as the constraint).
            int[] win = GroupCalendarService.effectiveRange(
                    GroupCalendarRule.<GroupCalendarRule>find("studentGroup", group).list(), day, date);
            int winMin = win[0];
            int winMax = win[1];
            if (winMax < winMin || winMax - winMin + 1 < placementSize) {
                datesSkippedCalendarWindow++;
                continue;
            }

            // TASK-502 H7: the teacher's daily cap counts the hours already placed on
            // this date (pinned + placed-in-phase; PreAssignmentContext decrements on
            // backtracking). Round 2 inherited -15 H7 from pins that ignored it.
            // TASK-516 round 7: when a reservation is armed the block is CLIPPED to
            // the net cap instead of rejected (round 6 rejected whole blocks and the
            // pair overflowed entirely: T15's Wednesdays went 174h pinned to 0).
            // TASK-518 round 8: the reservation is TEACHER-wide (max over pairs): the
            // headroom one pair's non-enumerated demand earns must survive the OTHER
            // groups' pins too, or the clipped tails box elsewhere (round 7's -53).
            // Flag-off keeps the raw cap and the whole-block rejection, byte-identical.
            int reservedH7 = Math.max(context.reservedH7Hours(teacher, group),
                    context.reservedH7Hours(teacher));
            int teacherCap = reservedH7 > 0
                    ? Math.max(1, teacher.getEffectiveMaxDailyHours() - reservedH7)
                    : teacher.getEffectiveMaxDailyHours();
            int remaining = teacherCap - context.teacherDailyHours(teacher, date);
            if (remaining <= 0) {
                datesSkippedTeacherCap++;
                continue;
            }
            if (sessionBlockSize > remaining) {
                if (reservedH7 > 0) {
                    // CLIP: place the part that fits, provided the clipped size also
                    // respects the group's H11 daily limit (checked above at FULL size).
                    if (ctx.existingLessonCounts != null && ctx.newLessonDates != null
                            && wouldExceedDailyLimit(group, date, ctx.existingLessonCounts,
                                    ctx.newLessonDates, remaining)) {
                        datesSkippedH11++;
                        continue;
                    }
                    placementSize = remaining;
                } else {
                    datesSkippedTeacherCap++;
                    continue;
                }
            }

            // IDEAL END DATE constraint: never place lessons strictly after group's ideal end date
            // Use isAfter (not !isBefore) so lessons ON the ideal end date are allowed.
            //
            // EXCEPTION: Skip for external teachers (VACATION records are the binding constraint).
            // External teachers like T28 have fixed availability (e.g., Thursday only).
            // The human planner places them on every available day regardless of ideal end
            // — see spalmone G12: T28 placed on May 14, 21 (past ideal end May 8).
            if (idealEndDate != null && date.isAfter(idealEndDate)
                    && !teacher.external) {
                LOG.infof("[GreedyAssigner] Group %s: date %s is after idealEndDate %s, blocking placement",
                    group.name, date, idealEndDate);
                datesSkippedIdealEnd++;
                break;  // No need to check later dates - they're all beyond ideal end
            }

            // NOTE: Consecutive-day avoidance removed from pre-assignment.
            // Consecutive-day avoidance is left to the solver's soft constraints — enforcing it here
            // artificially caps placement (e.g., 75 blocks needed but only ~70 fit
            // with alternating-day restriction). The solver can optimize spacing.

            datesTried++;

            // 4. Determine valid start slot positions for this block size
            LocalTime[] slotTimes = SlotCandidate.getSlotTimes(day);
            int preferredSlotStart = (session.labRotation != null) ? session.labRotation.slotStart : 0;

            // Cap available slots to group's maxHoursPerDay (e.g., 5h groups can only use slots 0-4)
            int effectiveSlots = slotTimes.length;
            if (group.maxHoursPerDay != null && group.maxHoursPerDay > 0
                    && group.maxHoursPerDay < effectiveSlots) {
                effectiveSlots = group.maxHoursPerDay;
            }
            // TASK-502 H20: also bound by the calendar window for THIS weekday (the
            // maxHoursPerDay cap above is group-generic, the window is per-weekday and
            // date-override aware). Positions must start at/after winMin and end within
            // winMax (see the position filter below the slot order).
            effectiveSlots = Math.min(effectiveSlots, winMax + 1);

            // Try preferred slot first, then all others
            int[] slotOrder = buildSlotOrder(preferredSlotStart, effectiveSlots, placementSize);

            int slotsTried = 0;
            int slotsSkippedTimeslots = 0;
            int slotsSkippedH1H3 = 0;
            int slotsSkippedH2 = 0;

            for (int startSlot : slotOrder) {
                // TASK-502 H20: a block starting before the window's first open slot is
                // a pin on a closed slot; buildSlotOrder starts from 0, the window may not.
                if (startSlot < winMin) {
                    continue;
                }
                slotsTried++;
                // Get occupied timeslots from context (O(1) map lookups, no DB queries)
                List<Timeslot> occupied = context.getOccupiedTimeslots(date, startSlot, placementSize);
                if (occupied.size() < placementSize) {
                    slotsSkippedTimeslots++;
                    continue; // Not enough timeslots exist
                }

                // 5. Check H1/H3 via PreAssignmentContext — O(1) each
                boolean hasConflict = false;
                Timeslot conflictTs = null;
                String conflictType = null;
                for (Timeslot ts : occupied) {
                    if (context.hasTeacherConflict(teacher, ts)) {
                        hasConflict = true;
                        conflictTs = ts;
                        conflictType = "H1-teacher";
                        break;
                    }
                    if (context.hasStudentGroupConflict(group, ts)) {
                        hasConflict = true;
                        conflictTs = ts;
                        conflictType = "H3-group";
                        break;
                    }
                }
                if (hasConflict) {
                    slotsSkippedH1H3++;
                    if (slotsSkippedH1H3 <= 3) { // Limit logging
                        LOG.debugf("[GreedyAssigner] Slot %d on %s has %s conflict at timeslot %s",
                            Integer.valueOf(startSlot), date, conflictType, conflictTs);
                    }
                    continue;
                }

                // 6. Find a room without H2 conflict
                Room validRoom = null;
                for (Room room : rooms) {
                    boolean roomConflict = false;
                    for (Timeslot ts : occupied) {
                        if (context.hasRoomConflict(room, ts)) {
                            roomConflict = true;
                            break;
                        }
                    }
                    if (!roomConflict) { validRoom = room; break; }
                }
                if (validRoom == null) {
                    slotsSkippedH2++;
                    if (slotsSkippedH2 <= 3) {
                        LOG.debugf("[GreedyAssigner] Slot %d on %s has H2 room conflict for all %d rooms",
                            Integer.valueOf(startSlot), date, Integer.valueOf(rooms.size()));
                    }
                    continue;
                }

                // Found a valid slot! Build and return SlotAssignment
                Timeslot startTimeslot = occupied.get(0);
                LOG.infof("[GreedyAssigner] Found valid slot for block %d on %s slot %d room %s (checked %d dates, %d slots, skipped: %d wrongDay, %d holiday, %d H5, %d stage, %d H11, %d used, %d idealEnd, %d calWindow, %d teacherCap, %d timeslots, %d H1H3, %d H2)",
                    session.lessons.get(0).id, date, startSlot, validRoom.name,
                    datesChecked, slotsTried,
                    datesSkippedWrongDay, datesSkippedHoliday,
                    datesSkippedH5, datesSkippedStage,
                    datesSkippedH11, datesSkippedUsed,
                    datesSkippedIdealEnd,
                    datesSkippedCalendarWindow, datesSkippedTeacherCap,
                    slotsSkippedTimeslots, slotsSkippedH1H3,
                    slotsSkippedH2);

                // TASK-516 round 7: a clipped placement carries a session of just the
                // lessons being pinned; the tail stays unpinned for the solver (the
                // round-6 whole-block rejection, in clip form).
                LabSession placed = session;
                if (placementSize < session.lessons.size()) {
                    placed = new LabSession(session.course);
                    for (int i = 0; i < placementSize; i++) {
                        placed.addLesson(session.lessons.get(i));
                    }
                    placed.labRotation = session.labRotation;
                    LOG.infof("[GreedyAssigner] CLIPPED block %d on %s: %d of %d lesson(s) pinned"
                                    + " (teacher cap %d net of %dh reservation, %dh already placed)",
                            session.lessons.get(0).id, date, placementSize, session.lessons.size(),
                            teacherCap, reservedH7, context.teacherDailyHours(teacher, date));
                }
                return Optional.of(new RotationTestRunner.SlotAssignment(
                    new BlockKey(teacher.id, group.id), placed, startTimeslot, validRoom, occupied));
            }
            // Accumulate slot-level skip counts for "No valid slot" diagnostic
            totalSlotsSkippedTimeslots += slotsSkippedTimeslots;
            totalSlotsSkippedH1H3 += slotsSkippedH1H3;
            totalSlotsSkippedH2 += slotsSkippedH2;
        }

        LOG.infof("[GreedyAssigner] No valid slot found for block %d on %s (checked %d dates, %d attempts, skipped: %d wrongDay, %d holiday, %d H5, %d stage, %d H11, %d used, %d idealEnd, %d calWindow, %d teacherCap, %d timeslots, %d H1H3, %d H2)",
            session.lessons.isEmpty() ? -1L : session.lessons.get(0).id, day,
            datesChecked, datesTried,
            datesSkippedWrongDay, datesSkippedHoliday,
            datesSkippedH5, datesSkippedStage,
            datesSkippedH11, datesSkippedUsed,
            datesSkippedIdealEnd,
            datesSkippedCalendarWindow, datesSkippedTeacherCap,
            totalSlotsSkippedTimeslots, totalSlotsSkippedH1H3, totalSlotsSkippedH2);

        return Optional.empty();
    }

    /**
     * Build slot order with preferred slot first, then remaining valid positions.
     */
    private int[] buildSlotOrder(int preferredStart, int totalSlots, int blockSize) {
        List<Integer> order = new ArrayList<>();
        // Preferred slot first (if it fits)
        if (preferredStart + blockSize <= totalSlots) {
            order.add(preferredStart);
        }
        // Then all other valid positions
        for (int i = 0; i + blockSize <= totalSlots; i++) {
            if (i != preferredStart) {
                order.add(i);
            }
        }
        return order.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * H11: Check if placing lessons on this date would exceed the group's daily hour limit.
     *
     * @param group the student group
     * @param date the date to check
     * @param existingLessonCounts cache of existing pinned lessons per group per date
     * @param newLessonDates cache of new lessons being placed in this phase per group per date
     * @return true if placing lessons would exceed maxHoursPerDay
     */
    private boolean wouldExceedDailyLimit(
            StudentGroup group,
            LocalDate date,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            Map<Long, Map<LocalDate, Integer>> newLessonDates) {

        // Groups with maxHoursPerDay = 0 or null have no limit
        if (group.maxHoursPerDay == null || group.maxHoursPerDay == 0) {
            return false;
        }

        int existingCount = existingLessonCounts.getOrDefault(group.id, Collections.emptyMap())
            .getOrDefault(date, 0);
        int newCount = newLessonDates.getOrDefault(group.id, Collections.emptyMap())
            .getOrDefault(date, 0);
        int totalHours = existingCount + newCount;

        return totalHours >= group.maxHoursPerDay;
    }

    /**
     * Check if placing a block of lessons would exceed the daily hour limit for a group.
     *
     * @param group the student group
     * @param date the date to check
     * @param existingLessonCounts cache of existing pinned lessons per group per date
     * @param newLessonDates cache of new lessons being placed in this phase per group per date
     * @param blockSize the number of lessons in the block being placed
     * @return true if placing this block would exceed maxHoursPerDay
     */
    private boolean wouldExceedDailyLimit(
            StudentGroup group,
            LocalDate date,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            Map<Long, Map<LocalDate, Integer>> newLessonDates,
            int blockSize) {

        // Groups with maxHoursPerDay = 0 or null have no limit
        if (group.maxHoursPerDay == null || group.maxHoursPerDay == 0) {
            return false;
        }

        int existingCount = existingLessonCounts.getOrDefault(group.id, Collections.emptyMap())
            .getOrDefault(date, 0);
        int newCount = newLessonDates.getOrDefault(group.id, Collections.emptyMap())
            .getOrDefault(date, 0);
        int totalHours = existingCount + newCount + blockSize;

        return totalHours > group.maxHoursPerDay;
    }

    /**
     * Create a temporary LabBlock with the given slot assignment for context tracking.
     */
    private LabSession createTempSession(LabSession original, RotationTestRunner.SlotAssignment slot) {
        // Track each lesson in the session
        for (int i = 0; i < original.lessons.size(); i++) {
            Lesson tempLesson = new Lesson();
            tempLesson.id = original.lessons.get(i).id;
            tempLesson.course = original.course;
            tempLesson.timeslot = slot.occupiedTimeslots().get(i);
            tempLesson.room = slot.room();
            context.trackLesson(tempLesson);
        }
        return original;
    }

    /**
     * Track a tentative block assignment in the context.
     */
    private void trackTentativeSession(BacktrackContext ctx, LabSession session, RotationTestRunner.SlotAssignment slot) {
        // Track each lesson in the session
        for (int i = 0; i < session.lessons.size(); i++) {
            Lesson tempLesson = new Lesson();
            tempLesson.id = session.lessons.get(i).id;
            tempLesson.course = session.course;
            tempLesson.timeslot = slot.occupiedTimeslots().get(i);
            tempLesson.room = slot.room();
            context.trackLesson(tempLesson);
            ctx.tentativelyTrackedSessions.add(session);
        }
    }

    /**
     * Cleanup tentative blocks from context.
     */
    private void cleanupTentativeSessions(BacktrackContext ctx) {
        for (LabSession session : ctx.tentativelyTrackedSessions) {
            for (Lesson l : session.getLessons()) {
                context.untrackLesson(l);
            };
        }
        ctx.tentativelyTrackedSessions.clear();
    }

    /**
     * Build failure analysis for when no assignment is found.
     */
    private FailureAnalysis buildFailureAnalysis(BacktrackContext ctx, RotationTestRunner.TestConfig config) {
        List<FailureAnalysis.ConflictPattern> patterns = new ArrayList<>();
        List<String> teacherNames = new ArrayList<>();
        List<String> groupNames = new ArrayList<>();
        Set<Long> teacherIds = new HashSet<>();
        Set<Long> groupIds = new HashSet<>();
        String periodRange = "";

        if (config.periodStart() != null && config.periodEnd() != null) {
            periodRange = String.format("%s - %s", config.periodStart(), config.periodEnd());
        }

        // Find the most constrained pair (first in order)
        BlockKey mostConstrained = ctx.pairOrder.isEmpty() ? null : ctx.pairOrder.get(0);

        if (mostConstrained != null) {
            teacherIds.add(mostConstrained.teacherId());
            groupIds.add(mostConstrained.studentGroupId());

            Teacher teacher = Teacher.findById(mostConstrained.teacherId());
            String teacherName = (teacher != null && teacher.name != null)
                ? teacher.name : "Teacher " + mostConstrained.teacherId();
            teacherNames.add(teacherName);

            StudentGroup group = StudentGroup.findById(mostConstrained.studentGroupId());
            String groupName = (group != null && group.name != null)
                ? group.name : "Group " + mostConstrained.studentGroupId();
            groupNames.add(groupName);

            List<DayOfWeek> validDays = ctx.validDaysByPair.getOrDefault(mostConstrained, List.of());
            List<LabSession> pairBlocks = ctx.blocksByPair.getOrDefault(mostConstrained, List.of());

            patterns.add(new FailureAnalysis.ConflictPattern(
                "NO_VALID_DAY",
                String.format("%s / %s has no valid day assignment", teacherName, groupName),
                ctx.candidatesTried,
                List.of("Valid days tried: " + validDays)
            ));

            FailureAnalysis.ConflictPattern capacityPattern = buildEnhancedCapacityPattern(
                mostConstrained.teacherId(), teacher, pairBlocks, validDays, config, groupNames
            );
            if (capacityPattern != null) {
                patterns.add(capacityPattern);
            }
        }

        // Analyze all pairs for capacity issues
        for (BlockKey pairKey : ctx.pairOrder) {
            List<DayOfWeek> validDays = ctx.validDaysByPair.getOrDefault(pairKey, List.of());
            List<LabSession> blocks = ctx.blocksByPair.getOrDefault(pairKey, List.of());

            teacherIds.add(pairKey.teacherId());
            groupIds.add(pairKey.studentGroupId());

            if (validDays.size() == 1) {
                int slotsAvailable = countAvailableSlots(validDays.get(0), config);
                int blocksNeeded = blocks.size();

                if (blocksNeeded > slotsAvailable) {
                    Teacher t = Teacher.findById(pairKey.teacherId());
                    String name = (t != null && t.name != null) ? t.name : "Teacher " + pairKey.teacherId();

                    patterns.add(new FailureAnalysis.ConflictPattern(
                        "CAPACITY_EXCEEDED",
                        String.format("%s: requires %d blocks but only %d slots available on %s",
                            name, blocksNeeded, slotsAvailable, validDays.get(0)),
                        blocksNeeded - slotsAvailable,
                        List.of(
                            "Teacher: " + name,
                            "Blocks needed: " + blocksNeeded,
                            "Slots available: " + slotsAvailable,
                            "Gap: " + (blocksNeeded - slotsAvailable) + " blocks",
                            "Suggestion: Reduce hours or extend rotation period"
                        )
                    ));
                }
            }
        }

        // Resolve any remaining teacher/group names
        for (BlockKey pk : ctx.pairOrder) {
            if (!teacherNames.stream().anyMatch(n -> true)) break; // already have names
        }

        String suggestion = buildSpecificSuggestion(patterns,
            mostConstrained != null ? mostConstrained.teacherId() : null, config, teacherNames);

        return new FailureAnalysis(
            ctx.phase,
            "CSP backtracking exhausted all possibilities",
            patterns,
            teacherIds,
            groupIds,
            teacherNames,
            groupNames,
            periodRange,
            suggestion
        );
    }


    /**
     * Build enhanced capacity pattern with human-readable names and detailed analysis.
     *
     * <p>Provides specific capacity calculations including:</p>
     * <ul>
     *   <li>Teacher and group names (not IDs)</li>
     *   <li>Per-day slot availability breakdown</li>
     *   <li>Exact hours/blocks calculation</li>
     *   <li>Remediation suggestion with specific values</li>
     * </ul>
     */
    private FailureAnalysis.ConflictPattern buildEnhancedCapacityPattern(
            Long teacherId,
            Teacher teacher,
            List<LabSession> blocks,
            List<DayOfWeek> validDays,
            RotationTestRunner.TestConfig config,
            List<String> groupNames) {

        if (blocks.isEmpty() || validDays.isEmpty()) {
            return null;
        }

        String teacherName = (teacher != null && teacher.name != null) ? teacher.name : "Teacher " + teacherId;
        int totalHoursNeeded = blocks.stream()
            .mapToInt(b -> b.lessons.size())
            .sum();

        // Calculate average block size from the sessions
        double avgBlockSize = blocks.stream()
            .mapToInt(b -> b.lessons.size())
            .average()
            .orElse(6.0);
        int blocksNeeded = (int) Math.ceil(totalHoursNeeded / avgBlockSize);

        // Count available slots per day
        List<String> dayAnalysis = new ArrayList<>();
        int totalSlotsAvailable = 0;
        DayOfWeek primaryDay = validDays.get(0);

        for (DayOfWeek day : validDays) {
            int slots = countAvailableSlots(day, config);
            totalSlotsAvailable += slots;
            dayAnalysis.add(String.format("%s: %d slots", day, slots));
        }

        // Build detailed pattern if capacity exceeded
        if (blocksNeeded > totalSlotsAvailable) {
            int suggestedHours = (int) (totalSlotsAvailable * avgBlockSize);
            int hoursToRemove = totalHoursNeeded - suggestedHours;

            List<String> examples = new ArrayList<>();
            examples.add(String.format("Teacher: %s", teacherName));
            examples.add(String.format("Groups: %s", String.join(", ", groupNames)));
            examples.add(String.format("Total hours needed: %d", totalHoursNeeded));
            examples.add(String.format("Blocks needed (%.1fh each): %d", avgBlockSize, blocksNeeded));
            examples.add(String.format("Total slots available: %d", totalSlotsAvailable));
            examples.add(String.format("Per-day breakdown: %s", String.join(", ", dayAnalysis)));
            examples.add(String.format("IMPOSSIBLE: Need %d more slots", blocksNeeded - totalSlotsAvailable));

            return new FailureAnalysis.ConflictPattern(
                "INSUFFICIENT_CAPACITY",
                String.format("%s needs %d blocks (%d hours) but only %d slots available on %s",
                    teacherName, blocksNeeded, totalHoursNeeded, totalSlotsAvailable, validDays),
                blocksNeeded - totalSlotsAvailable,
                examples
            );
        }

        return null;
    }

    /**
     * Build a specific, actionable suggestion with exact values.
     *
     * <p>Provides concrete remediation steps such as:</p>
     * <ul>
     *   <li>Exact hours to reduce (e.g., "Reduce from 64h to 54h")</li>
     *   <li>Period extension options</li>
     *   <li>Reference to verification scripts</li>
     * </ul>
     */
    private String buildSpecificSuggestion(List<FailureAnalysis.ConflictPattern> patterns,
                                           Long failedTeacherId,
                                           RotationTestRunner.TestConfig config,
                                           List<String> teacherNames) {
        // Find capacity issues
        for (FailureAnalysis.ConflictPattern pattern : patterns) {
            if ("INSUFFICIENT_CAPACITY".equals(pattern.type) || "CAPACITY_EXCEEDED".equals(pattern.type)) {
                // Extract values from examples to build specific suggestion
                int totalHours = 0;
                int slotsAvailable = 0;
                int blocksNeeded = 0;

                for (String example : pattern.examples) {
                    if (example.startsWith("Total hours needed: ")) {
                        totalHours = Integer.parseInt(example.substring(20).trim());
                    } else if (example.startsWith("Total slots available: ")) {
                        slotsAvailable = Integer.parseInt(example.substring(22).trim());
                    } else if (example.startsWith("Blocks needed")) {
                        // Extract from "Blocks needed (6h each): 11"
                        String value = example.substring(example.indexOf(": ") + 2).trim();
                        blocksNeeded = Integer.parseInt(value);
                    }
                }

                if (slotsAvailable > 0 && totalHours > 0) {
                    int suggestedHours = slotsAvailable * 6;
                    int hoursToRemove = totalHours - suggestedHours;
                    String teacherName = teacherNames.isEmpty() ? "Teacher" : teacherNames.get(0);

                    return String.format(
                        "DATA DISCREPANCY: %s has %d hours requiring %d blocks, but only %d slots available. " +
                        "Reduce to %d hours (remove %dh) or extend rotation period by %d weeks. " +
                        "Run: python3 scripts/verify-datagenerator.py",
                        teacherName, totalHours, blocksNeeded, slotsAvailable,
                        suggestedHours, hoursToRemove, blocksNeeded - slotsAvailable
                    );
                }
            }
        }

        // Check for external teacher issues
        boolean hasExternalTeacher = patterns.stream()
            .anyMatch(p -> p.examples != null && p.examples.stream().anyMatch(e -> e.contains("external")));

        if (hasExternalTeacher) {
            return "External teacher availability constraint too tight. " +
                   "Consider adjusting rotation periods or reassigning some groups to other teachers.";
        }

        return "Review teacher availability and room constraints. Consider adding more rotation days " +
               "or adjusting period boundaries.";
    }

    /**
     * Count available slots for a specific day of week in the config period.
     *
     * <p>Excludes holidays and dates outside the period.</p>
     */
    private int countAvailableSlots(DayOfWeek day, RotationTestRunner.TestConfig config) {
        if (config.periodStart() == null || config.periodEnd() == null) {
            return 0;
        }

        int count = 0;
        LocalDate date = config.periodStart();

        // Find first occurrence of the day
        while (date.getDayOfWeek() != day && date.isBefore(config.periodEnd())) {
            date = date.plusDays(1);
        }

        // Count all occurrences of the day in the period, excluding holidays
        while (!date.isAfter(config.periodEnd())) {
            if (!config.holidays().contains(date)) {
                count++;
            }
            date = date.plusWeeks(1);
        }

        return count;
    }


    /**
     * Sort valid days by teacher-day affinity: prefer days where the teacher
     * already has pinned lessons, creating consistent weekly patterns.
     */
    private List<DayOfWeek> sortByTeacherDayAffinity(Long teacherId, List<DayOfWeek> validDays,
                                                      RotationTestRunner.TestConfig config) {
        // Count pinned lessons per day-of-week for this teacher
        Map<DayOfWeek, Integer> dayCounts = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) dayCounts.put(d, 0);

        List<Lesson> pinnedLessons = Lesson.find("course.teacher.id = ?1 and pinned = true", teacherId).list();
        for (Lesson l : pinnedLessons) {
            if (l.timeslot != null) {
                DayOfWeek d = l.timeslot.startDateTime.getDayOfWeek();
                dayCounts.merge(d, 1, Integer::sum);
            }
        }

        // Sort: days with more existing pins first, tie-break by DAY_PRIORITY
        Map<DayOfWeek, Integer> priorityOrder = new HashMap<>();
        for (int i = 0; i < DAY_PRIORITY.size(); i++) {
            priorityOrder.put(DAY_PRIORITY.get(i), i);
        }

        return validDays.stream()
            .sorted(Comparator.<DayOfWeek>comparingInt(d -> -dayCounts.getOrDefault(d, 0))
                .thenComparingInt(d -> priorityOrder.getOrDefault(d, 99)))
            .collect(Collectors.toList());
    }
    
}
