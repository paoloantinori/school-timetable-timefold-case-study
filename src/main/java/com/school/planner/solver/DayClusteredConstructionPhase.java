package com.school.planner.solver;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import ai.timefold.solver.core.api.solver.phase.PhaseCommand;
import ai.timefold.solver.core.api.solver.phase.PhaseCommandContext;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel;
import ai.timefold.solver.core.preview.api.move.builtin.Moves;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Room;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.domain.LessonDifficultyComparator;
import com.school.planner.util.StageEligibilityUtil;
import com.school.planner.util.SubjectNormalization;

/**
 * Custom CH phase that places lessons in a day-clustered manner using
 * day-first strategy (matching spalmone's human planner approach).
 *
 * <p><b>TASK-381 "L2":</b> {@link #stampWeeklyTemplate} (built from {@link
 * WeeklyTemplateBuilder} - one representative school week per group, stamped
 * across every matching school date) is now the PRIMARY placement mechanism,
 * run first in {@link #changeWorkingSolution}. It produces the golden's
 * weekly recurrence "by construction" instead of hoping local search
 * discovers it. Everything described below - the day-clustered round-robin
 * ({@link #placeLessonsRoundRobin}) and {@link #boxUnplaceableLeftovers} - is now
 * the REPAIR TAIL: it only ever touches whatever the stamp couldn't place
 * (quota-day mismatches, exhausted courses, teacher clashes on a specific
 * date), exactly as it did before this task (both loops already skip any
 * lesson whose timeslot is no longer null).</p>
 *
 * <p>Algorithm (the repair tail, historically THE algorithm before "L2"):
 * <ol>
 *   <li>Group unassigned lessons by (studentGroup, course)</li>
 *   <li>For each group:
 *     <ul>
 *       <li>Iterate through available days (day-first strategy)</li>
 *       <li>For each day, try to fill the morning slots with lessons from DIFFERENT courses/teachers</li>
 *       <li>Prioritize courses that haven't been placed recently (true round-robin)</li>
 *       <li>Skip slots where pinned lab lessons (isLabHour=true) exist</li>
 *       <li>Check H1/H2/H3 constraints via in-memory tracking</li>
 *       <li>When day is full or no more lessons fit, move to next day</li>
 *     </ul>
 *   </li>
 *   <li>Continue until all lessons placed or no valid days remain</li>
 * </ol>
 *
 * <p>This produces DENSE initial placement (5-6 lessons/day) similar to spalmone,
 * by mixing multiple subjects on each day instead of placing each course
 * on separate consecutive days.
 *
 * <p>Key difference from old algorithm:
 * <ul>
 *   <li>OLD: Subject-first - place each course on consecutive days, scattered calendar</li>
 *   <li>NEW: Day-first - fill each day with multiple courses, dense calendar</li>
 * </ul>
 *
 * <p>Note: {@code BacktrackingPreAssigner} was deprecated in TASK-228 (LabBlocks → Lessons).
 * This phase applies similar day-clustered placement logic to all unpinned lessons.
 */
public class DayClusteredConstructionPhase<Solution_> implements PhaseCommand<Solution_> {

    private static final Logger LOG = Logger.getLogger(DayClusteredConstructionPhase.class);

    // Note: We cannot use CDI injection in custom phases - no CDI request context active
    // All data must come from the SchoolSchedule problem facts

    private int lessonsPlaced = 0;

    /** TASK-636 census: how many slot evaluations the golden-pace boost flipped
     *  to top priority. Zero with the flag armed = observably inert (the same
     *  census-row rule every activation carries). */
    private int paceBoostApplications = 0;

    /** TASK-637 census: how many quota-fill placements landed through the
     *  theory-repeat last-resort rank. Zero with the flag armed means no
     *  placement went through the hold (the hold may still have fired as a
     *  demotion that lost the slot) - "observably inert", the TASK-636
     *  wording; the banner echo separates it from "flag never reached the
     *  JVM". */
    private int theoryHoldFills = 0;

    /**
     * TASK-636: the SINGLE "behind golden pace" predicate both construction
     * attach points (the round-robin repair tail and the stamp's quota-aware
     * fill tail) consume: flag check, sentinel check, comparison and census
     * increment in one place, so the two passes cannot drift on when the
     * preference fires. The "placed so far" side stays at the call sites (the
     * two passes count it differently: explicit map vs arithmetic from the
     * unplaced queue).
     */
    private boolean behindGoldenPace(StudentGroup group, Course course, LocalDate date,
            int placedSoFar) {
        if (!cadencePaceEnabled()) {
            return false;
        }
        int expectedByNow = SchoolSchedule.getCadenceGoldenCumulative(
                group.name,
                SubjectNormalization.normalize(course.subject),
                TimeslotSequence.isoWeekKey(date));
        return expectedByNow != Integer.MAX_VALUE && placedSoFar < expectedByNow;
    }

    // TASK-446 Alt-B: every non-pinned placement leaves room=null (by structure, see
    // assignVariables). Counts placements, NOT failures (pre-Alt-B it counted FAILED
    // room assignments; the log label "roomNullAfterPlace" is kept for continuity).
    private int roomNullCount = 0;
    private int roundRobinLeftovers = 0;
    private int compactedGaps = 0;
    private long compactionAddedConflicts = 0;
    private Map<StudentGroup, Integer> lessonsPerGroup = new HashMap<>();

    // TASK-381 "L2" stamp-only stats (Step 2); public getters below - see
    // StampStats/getStampStats' own javadoc for why these are tracked
    // separately.
    private int stampPlaced = 0;
    /** TASK-542 iter-20: cells placed by the day-compact fabricator (0 when the flag is OFF). */
    private int dayCompactPlaced = 0;
    /** TASK-542 iter-20: cells the day-compact menu declined (fallback to the historic stamp). */
    private int dayCompactDeclined = 0;
    /** TASK-542 F8 (/simplify 23/08): blocks rolled back after a PARTIAL placement (never shipped truncated). */
    private int dayCompactTruncated = 0;
    /** TASK-542 cure-2: hours deferred because their block runs AHEAD of the course's proportional budget pace. */
    private int dayCompactPacedOff = 0;
    /** TASK-542 iter-20-ter: up to 3 concrete decline examples for the log (diagnosis). */
    private final java.util.List<String> dayCompactDeclineExamples = new java.util.ArrayList<>();
    private int stampSkipConflict = 0;
    private int stampSkipIllegalOrClosed = 0;
    private int stampSkipExhausted = 0;
    // TASK-385: distinct from stampSkipConflict - see exceedsExpectedHoursGoal's own javadoc.
    private int stampSkipExpectedHoursGoal = 0;
    /** TASK-576 F5: lessons the stamp placed via PAIR-EXHAUSTION substitution (0 when the flag is OFF). */
    private int stampPairExhaustionPlaced = 0;
    /**
     * TASK-576 F6 diagnosis: per-group skip counts [conflict, illegal, exhausted,
     * quota], logged at stamp end so the residual holes have a geography AND a
     * cause. Keyed by group name (log-readable); reset with the other stamp counters.
     */
    private final Map<String, int[]> stampSkipsByGroup = new HashMap<>();
    // cause indexes into stampSkipsByGroup's int[4] (order in the end-of-stamp log)
    private static final int CAUSE_CONFLICT = 0;
    private static final int CAUSE_ILLEGAL = 1;
    private static final int CAUSE_EXHAUSTED = 2;
    private static final int CAUSE_QUOTA = 3;

    /** TASK-576 F6 diagnosis: count one skip of this cause for this group. */
    private void skipForGroup(StudentGroup group, int cause) {
        stampSkipsByGroup.computeIfAbsent(group.name, k -> new int[4])[cause]++;
    }

    // Track assigned timeslots in memory to avoid conflicts during this phase
    private Map<Long, Set<Timeslot>> teacherAssignedTimeslots = new HashMap<>();
    private Map<Long, Set<Timeslot>> groupAssignedTimeslots = new HashMap<>();

    // TASK-446 Alt-B: room is decoupled from this phase. Non-pinned lessons are left
    // room=null here (H2/H16b/H-M2 silent on room-null, censimento TASK-446); phase 4
    // (CH room assignment) assigns all rooms on the complete grid. No in-memory room
    // tracking needed in construction.

    // Track daily lesson count per group to enforce H11 (Max student group daily hours)
    // Key: (studentGroup, date) → Value: number of lessons placed on that date
    private Map<Long, Map<LocalDate, Integer>> dailyLessonCount = new HashMap<>();

    // Track daily PSEUDO-teacher (e.g. the synthetic "Stage" course) lesson
    // count per (group, date), a subset of dailyLessonCount above - TASK-385
    // stage-exclusive stamp cap fix: expectedHoursCapFor now compares against
    // aula+lab only (see its own javadoc), but dailyLessonCount counts every
    // placed lesson including pinned Stage hours, so the stamp's comparison
    // nets this map out to avoid double-charging Stage hours against an
    // aula/lab-only budget.
    private Map<Long, Map<LocalDate, Integer>> pseudoDailyLessonCount = new HashMap<>();

    // Track daily lesson count per teacher to enforce H7 (Max teacher daily hours) —
    // added for TASK-381 "L2" stamping (see exceedsDailyCap()): the weekly
    // template already shapes each day within teacher.maxDailyHours (see
    // WeeklyTemplateBuilder's teacherDailyCap), but a specific calendar date
    // can still deviate from the modal week the template was built from
    // (closures/quota variance), so the stamp step re-checks it live, the
    // same way dailyLessonCount already re-checks H11 for groups.
    private Map<Long, Map<LocalDate, Integer>> teacherDailyLessonCount = new HashMap<>();

    // Cache holidays and stage patterns from problem facts
    private Set<LocalDate> holidays;
    private List<StagePattern> stagePatterns;
    private Map<Long, Set<LocalDate>> teacherVacationDates;

    /**
     * Required no-arg constructor for Timefold's PhaseCommand instantiation.
     */
    public DayClusteredConstructionPhase() {
    }

    /**
     * Initialize data from problem facts.
     * Cannot use database queries in custom phase - no CDI request context.
     */
    private void initializeFromProblemFacts(SchoolSchedule schedule) {
        // Cache holidays from problem facts
        holidays = new HashSet<>();
        if (schedule.getSchoolHolidays() != null) {
            for (var h : schedule.getSchoolHolidays()) {
                if (h.date != null) {
                    holidays.add(h.date);
                }
            }
        }

        // Cache stage patterns from problem facts
        stagePatterns = schedule.getStagePatterns();
        if (stagePatterns == null) {
            stagePatterns = new ArrayList<>();
        }

        // Build teacher vacation date map for quick lookup
        teacherVacationDates = new HashMap<>();
        if (schedule.getTeacherAvailabilities() != null) {
            for (var ta : schedule.getTeacherAvailabilities()) {
                if (ta.teacher != null && ta.teacher.id != null &&
                    ta.type == com.school.planner.domain.AvailabilityType.VACATION) {
                    teacherVacationDates
                        .computeIfAbsent(ta.teacher.id, k -> new HashSet<>())
                        .add(ta.startDateTime.toLocalDate());
                    // Add all dates in the range
                    LocalDate d = ta.startDateTime.toLocalDate().plusDays(1);
                    while (!d.isAfter(ta.endDateTime.toLocalDate())) {
                        teacherVacationDates.get(ta.teacher.id).add(d);
                        d = d.plusDays(1);
                    }
                }
            }
        }
    }

    // TASK-378: mutations MUST go through context.execute(Moves.change(...)) -
    // direct field writes are invisible to Timefold's incremental score streams
    // and initialization bookkeeping (the root cause of the constant
    // -9635 phase score, the "uninitialized entities" LS refusal, and the old
    // fallback CH re-placing everything this phase had silently built).
    private PhaseCommandContext<Solution_> phaseContext;
    private PlanningVariableMetaModel<Solution_, Lesson, Timeslot> timeslotVariable;
    private PlanningVariableMetaModel<Solution_, Lesson, Room> roomVariable;

    void assignVariables(Lesson lesson, Timeslot timeslot) {
        phaseContext.execute(Moves.change(timeslotVariable, lesson, timeslot));
        // TASK-446 Alt-B: every construction placement is room=null BY STRUCTURE, not
        // by entry-state assumption. Null the room at this commit point (the same Move
        // the compaction pass uses) so the invariant holds even on dirty re-solve state
        // (a prior staged B-ROOM pass may have assigned rooms). This keeps the
        // roomNullCount canary truthful. H2/H16b/H-M2 stay silent on room-null
        // (forEach/forEachUniquePair are initialized-only in this Timefold version;
        // phase 4 assigns every room on the full grid).
        phaseContext.execute(Moves.change(roomVariable, lesson, null));
        roomNullCount++;
    }

    @Override
    public void changeWorkingSolution(PhaseCommandContext<Solution_> context) {
        LOG.info("Day-clustered CH phase starting...");
        this.phaseContext = context;
        this.timeslotVariable = (PlanningVariableMetaModel<Solution_, Lesson, Timeslot>)
                context.getSolutionMetaModel().entity(Lesson.class).variable("timeslot", Timeslot.class);
        this.roomVariable = (PlanningVariableMetaModel<Solution_, Lesson, Room>)
                context.getSolutionMetaModel().entity(Lesson.class).variable("room", Room.class);

        // Cache data from problem facts (avoid database queries outside transaction)
        SchoolSchedule schedule = (SchoolSchedule) context.getWorkingSolution();
        initializeFromProblemFacts(schedule);

        // Get unassigned lessons (excluding pre-pinned and already assigned)
        List<Lesson> unassignedLessons = schedule.getLessons().stream()
            .filter(l -> !l.isPinned() && l.timeslot == null)
            .collect(Collectors.toList());

        LOG.infof("Processing %d unassigned lessons", unassignedLessons.size());

        // Group by studentGroup for round-robin day clustering
        Map<StudentGroup, List<Lesson>> groupedByStudentGroup = groupByStudentGroup(unassignedLessons);
        // TASK-590 coda-prima: the HashMap iteration order was nondeterministic
        // across JVMs (each batch run = a different construction order = a
        // different basin). Two changes: (a) deterministic order (LinkedHashMap
        // on sorted keys), (b) groups with LATER empty declared cells go FIRST
        // (most-constrained-first: their donor pool is scarcest, they need to
        // claim their hours before the mid-year groups consume the surplus).
        groupedByStudentGroup = sortGroupsCodaPrima(schedule, groupedByStudentGroup);

        // Track each group's starting (pre-stamp, pre-round-robin) unplaced
        // lesson count for the end-of-phase distribution log below - captured
        // HERE (not after stampWeeklyTemplate) since that method mutates
        // these same Lesson objects' timeslot field in place, which would
        // otherwise make "lessons processed" silently report the group's
        // TOTAL count instead of what was actually unplaced at phase start.
        for (Map.Entry<StudentGroup, List<Lesson>> entry : groupedByStudentGroup.entrySet()) {
            lessonsPerGroup.put(entry.getKey(), entry.getValue().size());
        }

        // Initialize tracking maps
        initializeTrackingMaps(schedule);

        // TASK-381 "L2": build one representative school week per group and
        // stamp it across the year BEFORE the day-clustered round-robin runs.
        // This is now the PRIMARY placement mechanism (weekly recurrence "by
        // construction" - see WeeklyTemplateBuilder's javadoc); round-robin
        // below becomes its repair tail for whatever the stamp couldn't fit
        // (quota-day mismatches, exhausted courses, teacher clashes on a
        // specific date). Both loops below already skip any lesson whose
        // timeslot is no longer null, so nothing needs to change there.
        stampWeeklyTemplate(schedule, groupedByStudentGroup);

        // Process each student group with round-robin placement (repair tail)
        for (Map.Entry<StudentGroup, List<Lesson>> entry : groupedByStudentGroup.entrySet()) {
            List<Lesson> groupLessons = entry.getValue();
            StudentGroup group = entry.getKey();

            // Sort by difficulty (lab lessons first, then by totalHours)
            groupLessons.sort(new LessonDifficultyComparator());

            // Place lessons using round-robin day clustering
            placeLessonsRoundRobin(groupLessons, schedule);
        }

        int boxedLeftovers = boxUnplaceableLeftovers(schedule);

        // TASK-390: leftward compaction pass. The stamp + round-robin + force-place
        // above leave a handful of single-slot student-day gaps (empirically ~6-10
        // per full solve, R1 benchmark) - a lesson sitting a slot too far right with
        // an empty hole before it, because at placement time the intervening slot was
        // momentarily blocked. Those gaps are H17 (vertical compactness) hard
        // violations; the golden has zero of them (human planners pack tight). ~85%
        // are AVOIDABLE - a single legal relocation closes them (verified: the gap
        // slot is VRP-available + the teacher is free there, see
        // WeeklyTemplateConstructionRealSeedTest#h17AvoidableGaps...) - the LS just
        // doesn't reach the tail move before termination. Closing them by construction
        // (pack tight from the start) is the human heuristic; the ~15% forced by
        // cross-group teacher conflicts are left for the LS/ruin.
        StudentDayCompaction.Result compaction = StudentDayCompaction.compact(schedule,
                (lesson, ts) -> phaseContext.execute(Moves.change(timeslotVariable, lesson, ts)),
                lesson -> { if (lesson.room != null) phaseContext.execute(Moves.change(roomVariable, lesson, null)); });
        compactedGaps = compaction.moved();
        compactionAddedConflicts = compaction.conflictsAdded();
        LOG.infof("TASK-390/405 compaction: student-day gaps %d->%d (moved %d), left-anchored %d, conflicts added %d",
                  compaction.gapsBefore(), compaction.gapsAfter(), compaction.moved(), compaction.anchored(), compaction.conflictsAdded());

        // TASK-533 via (b), flag-gated DEFAULT OFF: the fill tail inside the stamp
        // can only place STILL-UNPLACED lessons; when the group's pool is fully
        // placed but mis-distributed (round-robin tail and pins may overshoot one
        // day while another sits under quota), the under-quota day needs hours to
        // MOVE. This is the construction counterpart of QuotaFillRepair, run at
        // the SIGNATURE point (end of the construction phase = end of
        // A-CONSTRUCTION), border-aware: donor run stays contiguous, target only
        // extends its run upward, never the first hour.
        quotaTailMovesPlaced(schedule, groupedByStudentGroup);

        LOG.infof("Day-clustered CH complete: placed %d lessons, roundRobinLeftovers %d, BOXED %d (H0, TASK-398), compactedGaps %d, roomNullAfterPlace %d",
                  lessonsPlaced, roundRobinLeftovers, boxedLeftovers, compactedGaps, roomNullCount);
        LOG.infof("Cadence-pace tail (TASK-636): armed=%b paceBoostApplications=%d",
                cadencePaceEnabled(), paceBoostApplications);
        LOG.infof("Theory-spread tail (TASK-637): armed=%b theoryHoldFills=%d",
                WeeklyTemplateBuilder.isTheorySpreadEnabled(), theoryHoldFills);

        // Log distribution statistics
        for (Map.Entry<StudentGroup, Integer> entry : lessonsPerGroup.entrySet()) {
            LOG.infof("Group %s: %d lessons processed", entry.getKey().name, entry.getValue());
        }

        // TASK-590: pin cells that construction filled to quota, so the LS
        // can't drain them one lesson at a time (the v131 data: the LS makes
        // individually hard-neutral moves that collectively empty the cell;
        // H34 fires only when it's COMPLETELY empty, too late). promotedPin
        // is the existing mechanism: the VRP blocks the entity, the LS skips
        // it, the demotion stage releases it at the next attempt.
        pinFullDeclaredCells(schedule);
    }

    /**
     * TASK-590: for each declared cell (group, date) that is exactly at its
     * aula+lab quota after construction, set promotedPin=true on its non-Stage
     * lessons. The LS's entity selector then skips them. Flag opt-in:
     * {@code planner.construction.pin-full-cells.enabled}, DEFAULT OFF.
     */
    void pinFullDeclaredCells(SchoolSchedule schedule) {
        if (!"true".equalsIgnoreCase(System.getProperty("planner.construction.pin-full-cells.enabled"))) {
            return;
        }
        int pinned = 0;
        Map<Long, Map<LocalDate, Integer>> counts = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.pinned || l.course == null
                    || l.course.studentGroup == null) continue;
            if (l.course.teacher != null && l.course.teacher.pseudo) continue;
            counts.computeIfAbsent(l.course.studentGroup.id, k -> new HashMap<>())
                    .merge(l.timeslot.getDate(), 1, Integer::sum);
        }
        // Distinct dates only: iterating raw timeslots visits each date once
        // per slot (8x on this seed), re-pinning the same lessons and
        // inflating the counter the banner reports.
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (Timeslot ts : schedule.getTimeslots()) {
            if (ts.getDate() != null) dates.add(ts.getDate());
        }
        for (StudentGroup group : schedule.getStudentGroups()) {
            if (group == null || group.id == null) continue;
            for (LocalDate date : dates) {
                var row = SchoolSchedule.getExpectedHoursRow(group.id, date);
                if (row == null || row.aulaHours + row.labHours <= 0) continue;
                int quota = row.aulaHours + row.labHours;
                int placed = counts.getOrDefault(group.id, Map.of()).getOrDefault(date, 0);
                if (placed == quota && placed > 0) {
                    for (Lesson l : schedule.getLessons()) {
                        if (l.timeslot == null || l.pinned || l.course == null
                                || l.course.studentGroup == null
                                || !l.course.studentGroup.id.equals(group.id)) continue;
                        if (!l.timeslot.getDate().equals(date)) continue;
                        if (l.course.teacher != null && l.course.teacher.pseudo) continue;
                        // BOTH flags: promotedPin alone restricts nothing (the VRP
                        // shortcut and the LS entity filter key on `pinned`, and
                        // StagedPinPolicy releases promoted pins by
                        // pinned && promotedPin - Lesson.java's own invariant).
                        l.pinned = true;
                        l.promotedPin = true;
                        pinned++;
                    }
                }
            }
        }
        if (pinned > 0) {
            LOG.infof("TASK-590 pin-full-cells: promotedPin set on %d lessons in full declared cells", pinned);
        }
    }

    /**
     * Initialize tracking maps with every already-timeslotted entity —
     * pinned lessons (the common case) AND unpinned lessons that already
     * carry a timeslot (review fix: a re-solve after a manual unassign of a
     * sibling lesson, or any other partially-solved starting state, leaves
     * genuinely placed-but-unpinned {@code Lesson}s on the working solution;
     * seeding only from {@code isPinned()} made those invisible to this
     * phase's own conflict tracking, so the stamp/round-robin could double-
     * book right on top of them). On a fresh solve this is a no-op — no
     * unpinned lesson has a timeslot yet at CH start — so nothing changes
     * there.
     */
    private void initializeTrackingMaps(SchoolSchedule schedule) {
        // TASK-381 "L2" stamp-only stats - reset alongside the maps above so a
        // reused phase instance doesn't accumulate counts across solves.
        stampPlaced = 0;
        stampSkipConflict = 0;
        dayCompactPlaced = 0;
        dayCompactDeclined = 0;
        dayCompactTruncated = 0;
        dayCompactPacedOff = 0;
        dayCompactDeclineExamples.clear();
        // TASK-446: reset placement counters too, so a reused phase instance (Timefold
        // CustomPhaseConfig reuses the CustomPhaseCommand) starts each solve at 0.
        // roomNullCount is now load-bearing (the Alt-B canary, getRoomNullCount).
        lessonsPlaced = 0;
        roomNullCount = 0;
        stampSkipIllegalOrClosed = 0;
        stampSkipExhausted = 0;
        stampSkipExpectedHoursGoal = 0;
        stampPairExhaustionPlaced = 0;
        stampSkipsByGroup.clear();
        compactedGaps = 0;
        compactionAddedConflicts = 0;
        resyncTrackingMaps(schedule);
    }

    /**
     * Rebuild the in-memory occupancy maps from the lessons' CURRENT placements.
     * Split out of {@link #initializeTrackingMaps} (TASK-564) so a mid-phase pass
     * that places lessons outside this phase's own placement methods (the
     * declared-day pre-booker) can re-arm H1/H3/H7/H11 tracking without touching
     * the phase's counters. Counters NOT updated here on purpose.
     */
    private void resyncTrackingMaps(SchoolSchedule schedule) {
        teacherAssignedTimeslots.clear();
        groupAssignedTimeslots.clear();
        dailyLessonCount.clear();
        pseudoDailyLessonCount.clear();
        teacherDailyLessonCount.clear();
        // Track every already-timeslotted lesson (pinned or not - see
        // initializeTrackingMaps' javadoc) for conflict tracking and H7/H11 limits.
        for (Lesson lesson : schedule.getLessons()) {
            if (lesson.timeslot != null) {
                if (lesson.getTeacher() != null) {
                    teacherAssignedTimeslots
                        .computeIfAbsent(lesson.getTeacher().id, k -> new HashSet<>())
                        .add(lesson.timeslot);
                    LocalDate lessonDate = lesson.timeslot.getDate();
                    if (lessonDate != null) {
                        incrementTeacherDailyLessonCount(lesson.getTeacher().id, lessonDate);
                    }
                }
                if (lesson.getStudentGroup() != null) {
                    groupAssignedTimeslots
                        .computeIfAbsent(lesson.getStudentGroup().id, k -> new HashSet<>())
                        .add(lesson.timeslot);
                    LocalDate lessonDate = lesson.timeslot.getDate();
                    if (lessonDate != null) {
                        incrementDailyLessonCount(lesson.getStudentGroup().id, lessonDate);
                        if (lesson.getTeacher() != null && lesson.getTeacher().pseudo) {
                            incrementPseudoDailyLessonCount(lesson.getStudentGroup().id, lessonDate);
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if a lesson can be placed at the given timeslot.
     * Uses in-memory tracking to avoid database queries.
     * Checks H1 (teacher) and H3 (student group). TASK-446 Alt-B: room is decoupled
     * from placement (room=null left to phase 4), so there is no room-availability gate.
     */
    boolean canPlaceLesson(Lesson lesson, Timeslot timeslot) {
        Teacher teacher = lesson.getTeacher();
        StudentGroup group = lesson.getStudentGroup();

        // Check teacher conflict (H1)
        if (teacher != null) {
            Set<Timeslot> teacherSlots = teacherAssignedTimeslots.get(teacher.id);
            if (teacherSlots != null && teacherSlots.contains(timeslot)) {
                return false;
            }
        }

        // Check student group conflict (H3)
        if (group != null) {
            Set<Timeslot> groupSlots = groupAssignedTimeslots.get(group.id);
            if (groupSlots != null && groupSlots.contains(timeslot)) {
                return false;
            }
        }

        // TASK-446 Alt-B: room is decoupled from timeslot placement. The construction
        // phase leaves room=null on non-pinned lessons (H2/H16b/H-M2 are silent on
        // room-null, censimento TASK-446); phase 4 (CH room assignment) assigns all
        // rooms on the complete grid. No room-availability gate here.
        return true;
    }

    /**
     * Track a lesson assignment in memory.
     */
    void trackAssignment(Lesson lesson, Timeslot timeslot) {
        Teacher teacher = lesson.getTeacher();
        StudentGroup group = lesson.getStudentGroup();

        if (teacher != null) {
            teacherAssignedTimeslots
                .computeIfAbsent(teacher.id, k -> new HashSet<>())
                .add(timeslot);
            LocalDate lessonDate = timeslot.getDate();
            if (lessonDate != null) {
                incrementTeacherDailyLessonCount(teacher.id, lessonDate);
            }
        }

        if (group != null) {
            groupAssignedTimeslots
                .computeIfAbsent(group.id, k -> new HashSet<>())
                .add(timeslot);

            // Track daily count for H11 (Max student group daily hours)
            LocalDate lessonDate = timeslot.getDate();
            if (lessonDate != null) {
                incrementDailyLessonCount(group.id, lessonDate);
                if (teacher != null && teacher.pseudo) {
                    incrementPseudoDailyLessonCount(group.id, lessonDate);
                }
                // TASK-577 over-quota trace (7/09): the construction-end census
                // measured 46 days above their declared quota (+79h, +51h in
                // September) while every audited placement path consults
                // expectedDeficit - this trace names the placement that pushes a
                // day PAST its cap, live. Opt-in diagnostic, off by default.
                if (OVERQUOTA_TRACE && teacher != null && !teacher.pseudo) {
                    int cap = expectedHoursCapFor(group, lessonDate);
                    int nonPseudoCount = getDailyLessonCount(group.id, lessonDate)
                            - getPseudoDailyLessonCount(group.id, lessonDate);
                    if (cap >= 0 && nonPseudoCount > cap) {
                        String caller = Arrays.stream(new Throwable().getStackTrace())
                                .map(StackTraceElement::getMethodName)
                                .filter(m -> !m.equals("trackAssignment") && !m.equals("assignVariables"))
                                .findFirst().orElse("?");
                        LOG.warnf("OVERQUOTA-TRACE %s %s: %d > cap %d by %s",
                                group.name, lessonDate, nonPseudoCount, cap, caller);
                    }
                }
            }
        }
    }

    /** The over-quota trace's opt-in switch (see trackAssignment). */
    private static final boolean OVERQUOTA_TRACE =
            Boolean.getBoolean("planner.construction.overquota-trace");

    /**
     * Find first available date for this group/teacher combination.
     * Stops at the group's idealEndDate to avoid M12 overflow violations.
     */
    private LocalDate findFirstAvailableDate(StudentGroup group, Teacher teacher) {
        LocalDate date = TimeslotSequence.SCHOOL_YEAR_START; // scan start (placement still needs a real timeslot)
        LocalDate maxDate = getIdealEndDate(group);

        while (!date.isAfter(maxDate)) {
            if (isDateAvailable(date, group, teacher)) {
                return date;
            }
            date = date.plusDays(1);
        }

        return null;
    }

    /**
     * Find next available date starting from the given date.
     * Stops at the group's idealEndDate to avoid M12 overflow violations.
     */
    private LocalDate findNextAvailableDay(LocalDate startDate, StudentGroup group, Teacher teacher) {
        LocalDate date = startDate;
        LocalDate maxDate = getIdealEndDate(group);

        while (!date.isAfter(maxDate)) {
            if (isDateAvailable(date, group, teacher)) {
                return date;
            }
            date = date.plusDays(1);
        }

        return null;
    }

    /**
     * Get the ideal end date for a group — the tight date the UI uses for coloring.
     * Falls back to idealEndDateVrpBuffered (VRP boundary) or year end if idealEndDateInfo is not set.
     */
    private LocalDate getIdealEndDate(StudentGroup group) {
        if (group != null && group.idealEndDateInfo != null) {
            return group.idealEndDateInfo.idealEndDate();
        }
        if (group != null && group.idealEndDateVrpBuffered != null) {
            return group.idealEndDateVrpBuffered;
        }
        return TimeslotSequence.SCHOOL_YEAR_END;
    }

    /**
     * Check if a date is available for scheduling: the shared teaching-open
     * gate ({@link StageEligibilityUtil#isOpenTeachingDate}, same one the
     * template path uses) plus the teacher's vacation dates.
     */
    private boolean isDateAvailable(LocalDate date, StudentGroup group, Teacher teacher) {
        if (!StageEligibilityUtil.isOpenTeachingDate(date, group, holidays, stagePatterns)) {
            return false;
        }

        // Check teacher vacation dates
        if (teacher != null && teacherVacationDates.containsKey(teacher.id)) {
            if (teacherVacationDates.get(teacher.id).contains(date)) {
                return false;
            }
        }

        return true;
    }

    private LocalTime[] getSlotTimes(DayOfWeek day) {
        // Uniform 8-slot schema for every school day (TASK-341) - no Friday-specific
        // reduction. Derived from TimeslotSequence.SLOT_START_TIMES, the single
        // source of truth for the slot schema.
        return TimeslotSequence.SLOT_START_TIMES;
    }

    /**
     * Key for grouping lessons by (studentGroup, course).
     */
    /**
     * Group lessons by (studentGroup, course) for day-clustered placement.
     */
    /**
     * Group lessons by studentGroup for round-robin day clustering.
     * This allows mixing different courses/teachers on the same day.
     */
    private Map<StudentGroup, List<Lesson>> groupByStudentGroup(List<Lesson> lessons) {
        Map<StudentGroup, List<Lesson>> result = new HashMap<>();
        for (Lesson lesson : lessons) {
            StudentGroup group = lesson.getStudentGroup();
            if (group != null) {
                result.computeIfAbsent(group, k -> new ArrayList<>()).add(lesson);
            }
        }
        return result;
    }

    /**
     * TASK-590 coda-prima: sort the group map so that groups whose LATEST
     * declared-empty cell is LATER in the year go FIRST. Rationale: the donor
     * pool is consumed chronologically; a group with June cells has ~9 months
     * of competitors before its turn, while a group with October cells has
     * ~1 month. Most-constrained-first (literature: "those most likely to
     * cause trouble if deferred"). The order is DETERMINISTIC (same key set =
     * same order, unlike the HashMap it replaces). Default ON, opt-out with
     * {@code planner.construction.coda-prima.enabled=false}.
     */
    private Map<StudentGroup, List<Lesson>> sortGroupsCodaPrima(
            SchoolSchedule schedule, Map<StudentGroup, List<Lesson>> grouped) {
        if ("false".equalsIgnoreCase(System.getProperty("planner.construction.coda-prima.enabled"))) {
            return grouped;
        }
        Map<StudentGroup, LocalDate> latestEmptyCell = new HashMap<>();
        for (StudentGroup group : grouped.keySet()) {
            LocalDate latest = null;
            // TASK-669: take the LATEST positive-quota date in the grid, not the
            // first one the scan stumbles on. The javadoc/TASK-590 intent is the
            // coda (tail) of the group's declared year: with the first-row break,
            // 13/14 groups on the 2026/27 seed shared the ~14/09 key and the
            // ordering was inert, and the key also varied with the unordered DB
            // scan. max() over the grid is order-independent by construction.
            // The grid restriction is load-bearing: quota rows beyond the grid
            // do not count (H32 semantics).
            Map<LocalDate, com.school.planner.domain.DailyActivityQuota> byDate =
                    SchoolSchedule.getExpectedHoursForGroup(group.id);
            for (Timeslot ts : schedule.getTimeslots()) {
                LocalDate date = ts.getDate();
                var row = byDate.get(date);
                if (row == null || row.aulaHours + row.labHours <= 0) continue;
                if (latest == null || date.isAfter(latest)) {
                    latest = date;
                }
            }
            // TASK-666: getOrDefault does NOT replace present-but-null keys;
            // omitting the put keeps the LocalDate.MIN fallback so a quota-less
            // group sorts last instead of NPE-ing the comparator below.
            if (latest != null) {
                latestEmptyCell.put(group, latest);
            }
        }
        Map<StudentGroup, List<Lesson>> sorted = new java.util.LinkedHashMap<>();
        grouped.entrySet().stream()
                .sorted((a, b) -> {
                    LocalDate da = latestEmptyCell.getOrDefault(a.getKey(), LocalDate.MIN);
                    LocalDate db = latestEmptyCell.getOrDefault(b.getKey(), LocalDate.MIN);
                    int byDate = db.compareTo(da); // later dates first = coda-prima
                    // TASK-669 (review F4): 9/14 groups on the real seed share the
                    // 10-June key; without a tie-break that block keeps the source
                    // HashMap's iteration order, the nondeterminism TASK-590 set
                    // out to kill. Stable secondary key: group id.
                    return byDate != 0 ? byDate : Long.compare(a.getKey().id, b.getKey().id);
                })
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    /**
     * Place lessons for a student group using TRUE day-clustered interleaving.
     *
     * <p>Algorithm (FIXED - now matches spalmone's approach):
     * <ol>
     *   <li>Group lessons by course (for teacher conflict tracking)</li>
     *   <li>For each available day:
     *     <ul>
     *       <li>Try to fill the morning slots with lessons from DIFFERENT courses/teachers</li>
     *       <li>Skip slots where pinned lab lessons (isLabHour=true) exist</li>
     *       <li>Prioritize courses that haven't been placed recently (true round-robin)</li>
     *       <li>When day is full or no more lessons fit, move to next day</li>
     *     </ul>
     *   </li>
     *   <li>Continue until all lessons placed or no valid days remain</li>
     * </ol>
     *
     * <p>This produces DENSE calendars (5-6 lessons/day) like spalmone, by
     * mixing multiple subjects on each day instead of placing each course
     * on separate consecutive days.
     *
     * <p>Key change from old algorithm: Day-first strategy (fill each day)
     * instead of subject-first strategy (place each course on separate days).
     */
    private void placeLessonsRoundRobin(List<Lesson> groupLessons,
                                         SchoolSchedule schedule) {
        if (groupLessons.isEmpty()) {
            return;
        }

        StudentGroup group = groupLessons.get(0).getStudentGroup();

        // Group lessons by course for efficient lookups
        Map<Course, List<Lesson>> lessonsByCourse = new HashMap<>();
        for (Lesson lesson : groupLessons) {
            if (lesson.course != null) {
                lessonsByCourse.computeIfAbsent(lesson.course, k -> new ArrayList<>()).add(lesson);
            }
        }

        // Track last placement date for each course (for round-robin prioritization).
        // TASK-381 "L2": seeded from lessons stampWeeklyTemplate() already
        // placed for this group (groupLessons is the same list the stamp
        // mutated in place) - without this, a course the stamp filled every
        // week since September would still read as "never placed"
        // (daysSinceLastPlacement=999 in findBestLessonForSlot below),
        // making round-robin over-prioritize topping it up further ahead of
        // a course the stamp genuinely never touched.
        Map<Course, LocalDate> lastPlacementDate = new HashMap<>();
        for (Lesson lesson : groupLessons) {
            if (lesson.course == null || lesson.timeslot == null) continue;
            LocalDate placedDate = lesson.timeslot.getDate();
            lastPlacementDate.merge(lesson.course, placedDate, (a, b) -> a.isAfter(b) ? a : b);
        }

        // TASK-636: placed-hours count per course, seeded from the stamp's
        // placements the same way lastPlacementDate is, and incremented on
        // every tail assignment (main slot + block partner) below. This is
        // the "placed so far" side of the golden-pace comparison.
        Map<Course, Integer> placedCountByCourse = new HashMap<>();
        for (Lesson lesson : groupLessons) {
            if (lesson.course == null || lesson.timeslot == null) continue;
            placedCountByCourse.merge(lesson.course, 1, Integer::sum);
        }

        // Get pinned LabBlock slots for this group (to skip them)
        Set<Timeslot> pinnedLabBlockSlots = getPinnedLabBlockSlotsForGroup(group, schedule);

        // Statistics tracking
        int daysUsed = 0;
        int totalSlotsFilled = 0;

        // DAY-FIRST STRATEGY: Iterate through days, fill each with multiple courses
        LocalDate currentDate = findFirstAvailableDate(group, null);

        while (currentDate != null && hasUnplacedLessons(lessonsByCourse)) {
            LocalTime[] slotTimes = getSlotTimes(currentDate.getDayOfWeek());
            int maxSlots = slotTimes.length;

            LOG.debugf("Processing group %s for date %s", group.name, currentDate);

            // Build a list of available slots for this day (excluding pinned LabBlocks)
            List<Timeslot> availableSlots = new ArrayList<>();
            for (int slotIndex = 0; slotIndex < maxSlots; slotIndex++) {
                LocalDateTime start = LocalDateTime.of(currentDate, slotTimes[slotIndex]);
                Timeslot slotTimeslot = findTimeslotInSchedule(start, schedule);
                if (slotTimeslot == null || pinnedLabBlockSlots.contains(slotTimeslot)) {
                    continue;
                }
                // M28 (TASK-378 iteration 3): the bulk fill never uses afternoon
                // slots - they are stage-reserved last resorts (the golden uses
                // them sparingly). The force-place pass and the LS may still use
                // them at their M28-aware cost when mornings run out.
                if (TimeslotSequence.slotIndexOf(slotTimeslot) >= TimeslotSequence.AFTERNOON_START_SLOT_INDEX) {
                    continue;
                }
                availableSlots.add(slotTimeslot);
            }

            if (availableSlots.isEmpty()) {
                currentDate = findNextAvailableDay(currentDate.plusDays(1), group, null);
                continue;
            }

            // Fill this day's slots using round-robin course selection
            // For each slot, try to find a lesson from a different course/teacher
            Set<Long> teachersUsedToday = new HashSet<>();
            Set<Long> coursesUsedToday = new HashSet<>();

            for (Timeslot slotToFill : availableSlots) {
                Lesson lessonToPlace = findBestLessonForSlot(
                    slotToFill, lessonsByCourse, lastPlacementDate, group,
                    teachersUsedToday, coursesUsedToday, currentDate, placedCountByCourse);

                if (lessonToPlace != null) {
                    assignVariables(lessonToPlace, slotToFill);

                    // Track assignment
                    trackAssignment(lessonToPlace, slotToFill);
                    placedCountByCourse.merge(lessonToPlace.course, 1, Integer::sum);

                    // Track what's been used today (for variety)
                    if (lessonToPlace.getTeacher() != null) {
                        teachersUsedToday.add(lessonToPlace.getTeacher().id);
                    }
                    coursesUsedToday.add(lessonToPlace.course.id);

                    // Update course's last placement date
                    lastPlacementDate.put(lessonToPlace.course, currentDate);

                    lessonsPlaced++;
                    totalSlotsFilled++;

                    LOG.debugf("  Placed %s with %s at %s",
                        lessonToPlace.course.subject,
                        lessonToPlace.getTeacher() != null ? lessonToPlace.getTeacher().name : "no teacher",
                        currentDate);

                    // M23: Block pair awareness — if this lesson has a block partner,
                    // try to place it in the next consecutive slot immediately
                    int nextSlotIndex = availableSlots.indexOf(slotToFill) + 1;
                    if (nextSlotIndex < availableSlots.size() && lessonToPlace.sessionIndex >= 0) {
                        Lesson partner = findBlockPartner(lessonToPlace, lessonsByCourse);
                        if (partner != null && partner.timeslot == null) {
                            Timeslot nextSlot = availableSlots.get(nextSlotIndex);
                            // TASK-406: respect the same M20 expected-target cap as the main slot above.
                            if (canPlaceLesson(partner, nextSlot) && expectedDeficit(group, currentDate) > 0) {
                                assignVariables(partner, nextSlot);

                                trackAssignment(partner, nextSlot);
                                placedCountByCourse.merge(partner.course, 1, Integer::sum);
                                if (partner.getTeacher() != null) {
                                    teachersUsedToday.add(partner.getTeacher().id);
                                }
                                coursesUsedToday.add(partner.course.id);
                                lessonsPlaced++;
                                totalSlotsFilled++;
                                LOG.debugf("  Placed block partner %s at %s",
                                    partner.course.subject, currentDate);
                            }
                        }
                    }
                }
            }

            if (!coursesUsedToday.isEmpty()) {
                daysUsed++;
            }

            // Move to next available day
            currentDate = findNextAvailableDay(currentDate.plusDays(1), group, null);
        }

        // Count any remaining unplaced lessons
        int unplaced = countUnplacedLessons(lessonsByCourse);
        if (unplaced > 0) {
            roundRobinLeftovers += unplaced;
            LOG.warnf("Round-robin could not place %d lessons for group %s (boxed as H0 unless a conflict-free slot exists, TASK-398)", unplaced, group.name);
        }

        LOG.infof("Group %s: placed %d lessons on %d days (%.2f lessons/day avg)",
            group.name, lessonsPlaced, daysUsed,
            daysUsed > 0 ? (double)totalSlotsFilled / daysUsed : 0);
    }

    /**
     * TASK-398: BOX the residual the round-robin couldn't fit, instead of force-placing
     * it into conflicts. TASK-378 force-placed every leftover at its least-conflicting
     * slot BECAUSE {@code Lesson.timeslot} had no {@code allowsUnassigned} - the local
     * search could not start on a null variable, so a forced conflict (the -44,100
     * scatter) beat a null. Now that {@code timeslot} is {@code allowsUnassigned = true}
     * (TASK-398), a lesson may legally sit unassigned ("the box", H0 -1 each), and the
     * LS places it as capacity opens - a far smaller job than repairing 44k of conflicts.
     * So: place a leftover only into a slot that adds no HARD conflict - the same
     * {@link #canPlaceLesson} (H1/H2/H3) + {@link #exceedsDailyCap} (H7/H11) gate the
     * stamp uses (deliberately a HARD-only gate, not a total conflict-cost test: a
     * cost test would also reject a legal afternoon slot on M28's medium cost, but
     * boxing (H0 hard) is strictly worse than a medium-tier placement). Otherwise
     * leave it null (box). Placed lessons are
     * hard-conflict-free by construction (spike RESULTS: construction-end hard -8
     * H17-only vs -44,100). Returns the box count. EXPERIMENTAL pending the TASK-398 laptop
     * A/B - if the LS doesn't clear the box to 0, revert to force-place.
     */
    private int boxUnplaceableLeftovers(SchoolSchedule schedule) {
        List<Lesson> leftovers = schedule.getLessons().stream()
            .filter(l -> !l.isPinned() && l.timeslot == null)
            .collect(Collectors.toList());
        if (leftovers.isEmpty()) {
            return 0;
        }
        int placed = 0;
        int placedOnDeficitDay = 0;
        int boxed = 0;
        int boxedByDeficitDiscipline = 0;
        boolean deficitOnly = deficitOnlyBoxing();
        for (Lesson lesson : leftovers) {
            // TASK-406: place on the first conflict-free slot on a day UNDER its M20
            // expected target when one exists; otherwise the first conflict-free slot.
            // No conflict-free slot at all -> box (H0), the last resort as before.
            // TASK-577 (8/09, deficit-only flag): the fallback branch is the
            // construction-end over-quota census's single culprit (ALL 420
            // over-cap placements traced to this line): parking a leftover on an
            // at-quota day decorates September while H34 starves June - a trade
            // priced before H34 existed (TASK-406, 18/07). With the flag on, a
            // covered day (quota row exists) accepts a leftover ONLY in deficit;
            // days without a row keep the legacy fallback (no fact to overflow).
            Timeslot conflictFreeSlot = null;     // legacy semantics: any covered-or-not slot
            Timeslot uncoveredFreeSlot = null;    // a row-less day's slot (always acceptable)
            Timeslot deficitFreeSlot = null;
            // Review F7 (8/09): a pseudo (Stage) leftover is cap-NEUTRAL (netted out
            // of nonPseudoCount): the discipline would box it on its own stage day
            // (aula+lab 0 = deficit 0) serving no over-quota purpose. Only real-teacher
            // leftovers ride the deficit discipline.
            boolean disciplineApplies = deficitOnly
                    && lesson.getTeacher() != null && !lesson.getTeacher().pseudo;
            for (Timeslot ts : lesson.getAvailableTimeslotsFresh()) {  // (TASK-578: FRESH, stale-cache crash class)
                if (!canPlaceLesson(lesson, ts) || exceedsDailyCap(lesson, ts)) {
                    continue;
                }
                if (conflictFreeSlot == null) {
                    conflictFreeSlot = ts;
                }
                if (disciplineApplies && expectedHoursCapFor(lesson.getStudentGroup(), ts.getDate()) < 0) {
                    if (uncoveredFreeSlot == null) {
                        uncoveredFreeSlot = ts;
                    }
                } else if (deficitFreeSlot == null && expectedDeficit(lesson.getStudentGroup(), ts.getDate()) > 0) {
                    deficitFreeSlot = ts;
                }
            }
            Timeslot chosenSlot = deficitFreeSlot != null ? deficitFreeSlot
                    : disciplineApplies ? uncoveredFreeSlot : conflictFreeSlot;
            if (chosenSlot != null) {
                // A conflict-free slot the round-robin missed - place it. TASK-446 Alt-B:
                assignVariables(lesson, chosenSlot);
                trackAssignment(lesson, chosenSlot);
                lessonsPlaced++;
                placed++;
                if (deficitFreeSlot != null) placedOnDeficitDay++;
            } else {
                // No conflict-free slot - BOX it (leave timeslot null). H0 penalizes the
                // box; the local search places it later without ever creating a conflict.
                boxed++;
                if (deficitOnly && conflictFreeSlot != null) {
                    // legal ONLY under the legacy fallback: the discipline held it back
                    boxedByDeficitDiscipline++;
                }
            }
        }
        LOG.infof("TASK-398/406 box-the-residual: placed %d conflict-free leftovers (%d on M20-deficit days), BOXED %d (H0; LS places them, no forced conflicts; deficit-discipline holds %d in the box)",
                placed, placedOnDeficitDay, boxed, boxedByDeficitDiscipline);
        return boxed;
    }

    /** TASK-577 (8/09): {@link #boxUnplaceableLeftovers}'s deficit-only discipline.
     *  DEFAULT OFF (opt-in prototype form): the banco measures H34/H0 at construction,
     *  the cycle arbitrates the delivery. */
    static final String BOX_DEFICIT_ONLY_FLAG = "planner.construction.box-deficit-only.enabled";

    /**
     * TASK-577 9/09 (RATIFICATO DEFAULT ON da maintainer, 9/09): construction
     * fill-goal follows the RATIFIED pool-cap per-date targets (TASK-497,
     * input-derived) for over-capacity groups. Forma opt-out standard:
     * null/absent -> ON, solo il letterale "false" disarma (stesso idioma
     * di SchoolSchedule.isM20CapPoolEnabled). Evidenza banco (PoolCapFillBench,
     * 2 run identici): celle dichiarate vuote 55->34, G04 20->9, G08 9->0,
     * boxed 140->136, H33 29->26, H34 22->21, totali per gruppo invariati.
     */
    static final String POOL_CAP_FILL_FLAG = "planner.construction.pool-cap-fill.enabled";

    public static boolean poolCapFillEnabled() {
        String v = System.getProperty(POOL_CAP_FILL_FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    private static boolean deficitOnlyBoxing() {
        return Boolean.getBoolean(BOX_DEFICIT_ONLY_FLAG);
    }

    /**
     * Find the best lesson to place in a given slot.
     * Prioritizes courses that haven't been placed recently (true round-robin).
     * Avoids placing the same teacher/course multiple times per day (for variety).
     * Enforces H11 (Max student group daily hours) - stops placing when maxHoursPerDay reached.
     */
    /**
     * Find the block session partner for a lesson (same course, same sessionIndex).
     */
    private Lesson findBlockPartner(Lesson lesson, Map<Course, List<Lesson>> lessonsByCourse) {
        List<Lesson> courseLessons = lessonsByCourse.get(lesson.course);
        if (courseLessons == null) return null;
        for (Lesson candidate : courseLessons) {
            if (candidate.id != lesson.id
                    && candidate.sessionIndex == lesson.sessionIndex
                    && candidate.timeslot == null) {
                return candidate;
            }
        }
        return null;
    }

    private Lesson findBestLessonForSlot(
            Timeslot targetSlot,
            Map<Course, List<Lesson>> lessonsByCourse,
            Map<Course, LocalDate> lastPlacementDate,
            StudentGroup group,
            Set<Long> teachersUsedToday,
            Set<Long> coursesUsedToday,
            LocalDate currentDate,
            Map<Course, Integer> placedCountByCourse) {

        // H11: Max student group daily hours - check if group has reached its daily limit
        if (groupDailyCapReached(group, currentDate)) {
            return null; // Day is full for this group, don't place more
        }
        // TASK-406: also stop at the M20 expected-hours target (dailyQuota), not just
        // the H11 max - see expectedDeficit's own javadoc.
        if (expectedDeficit(group, currentDate) == 0) {
            return null;
        }

        Lesson bestLesson = null;
        LocalDate oldestPlacement = null;
        int bestPriority = Integer.MAX_VALUE;

        for (Map.Entry<Course, List<Lesson>> entry : lessonsByCourse.entrySet()) {
            Course course = entry.getKey();
            List<Lesson> courseLessons = entry.getValue();

            // Skip if this course was already used today (for variety)
            if (coursesUsedToday.contains(course.id)) {
                continue;
            }

            // Find an unplaced lesson from this course
            for (Lesson lesson : courseLessons) {
                if (lesson.timeslot != null) {
                    continue; // Already placed
                }

                // Skip if this teacher was already used today (max once per day per teacher)
                if (lesson.getTeacher() != null && teachersUsedToday.contains(lesson.getTeacher().id)) {
                    continue;
                }

                // Check if lesson can be placed at this slot (value range + conflicts)
                if (!canPlaceLesson(lesson, targetSlot)) {
                    continue; // Can't place here
                }

                // TASK-578: per-slot legality (the Fresh full list here was the
                // round-robin's ~10^4-10^5 rebuild site: targetSlot is a grid Timeslot,
                // id-equality makes the membership check equivalent)
                if (!lesson.isSlotLegalFresh(targetSlot)) {
                    continue; // Outside lesson's value range
                }

                // Calculate priority: courses placed longer ago get higher priority
                LocalDate lastPlaced = lastPlacementDate.get(course);
                int daysSinceLastPlacement = (lastPlaced == null) ? 999 :
                    (int) ChronoUnit.DAYS.between(lastPlaced, targetSlot.getDate());

                // TASK-636: golden-pace tracking. A course BEHIND its published
                // cadence-reference cumulative curve for this week keeps top
                // round-robin priority (dense-first recovery); at or ahead of
                // pace, or with no reference covering the pair (MAX_VALUE
                // sentinel), plain aging applies - byte-identical to before.
                int effectiveDaysSince = daysSinceLastPlacement;
                boolean paceBoosted = false;
                if (lastPlaced != null
                        && behindGoldenPace(group, course, targetSlot.getDate(),
                                placedCountByCourse.getOrDefault(course, 0))) {
                    // The winner is the MINIMUM priority (10000 - daysSince - size/10,
                    // oldest-first): a behind-pace course must read as maximally OLD,
                    // not maximally fresh - 9999 beats every real day age (the never-
                    // placed sentinel is 999, a school year is ~200 days).
                    effectiveDaysSince = 9999;
                    paceBoosted = true;
                }

                // Prioritize by oldest placement (round-robin), then by course size (larger first)
                int priority = 10000 - effectiveDaysSince - (course.totalHours / 10);

                if (priority < bestPriority) {
                    bestLesson = lesson;
                    bestPriority = priority;
                    oldestPlacement = lastPlaced;
                    // TASK-636 census honesty (code-review 21/09): count FLIPS, not
                    // evaluations - the boost is real only when the boosted candidate
                    // actually wins the slot.
                    if (paceBoosted) {
                        paceBoostApplications++;
                    }
                }
            }
        }

        return bestLesson;
    }

    /**
     * Get all timeslots occupied by pinned lab lessons for a student group.
     * Used to skip these slots during day filling.
     *
     * NOTE: LabBlocks are deprecated (TASK-228). This method now returns
     * an empty set since all labs are represented as pinned Lessons with isLabHour=true.
     */
    private Set<Timeslot> getPinnedLabBlockSlotsForGroup(StudentGroup group, SchoolSchedule schedule) {
        // LabBlocks are deprecated - no longer used (TASK-228)
        // All labs are now pinned Lessons with isLabHour=true
        return Collections.emptySet();
    }

    /**
     * Find a timeslot by start time in the schedule's timeslot list.
     */
    private Timeslot findTimeslotInSchedule(LocalDateTime start, SchoolSchedule schedule) {
        if (schedule.getTimeslots() == null) {
            return null;
        }
        for (Timeslot ts : schedule.getTimeslots()) {
            if (ts.startDateTime.equals(start)) {
                return ts;
            }
        }
        return null;
    }

    /**
     * Check if there are any unplaced lessons remaining.
     */
    private boolean hasUnplacedLessons(Map<Course, List<Lesson>> lessonsByCourse) {
        for (List<Lesson> courseLessons : lessonsByCourse.values()) {
            for (Lesson lesson : courseLessons) {
                if (lesson.timeslot == null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Count unplaced lessons.
     */
    private int countUnplacedLessons(Map<Course, List<Lesson>> lessonsByCourse) {
        int count = 0;
        for (List<Lesson> courseLessons : lessonsByCourse.values()) {
            for (Lesson lesson : courseLessons) {
                if (lesson.timeslot == null) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Get the number of lessons placed for a group on a specific date.
     * Used for H11 (Max student group daily hours) enforcement.
     */
    private int getDailyLessonCount(Long groupId, LocalDate date) {
        Map<LocalDate, Integer> groupDailyCount = dailyLessonCount.get(groupId);
        if (groupDailyCount == null) {
            return 0;
        }
        return groupDailyCount.getOrDefault(date, 0);
    }

    /**
     * Increment the daily lesson count for a group after placing a lesson.
     */
    private void incrementDailyLessonCount(Long groupId, LocalDate date) {
        dailyLessonCount
            .computeIfAbsent(groupId, k -> new HashMap<>())
            .merge(date, 1, Integer::sum);
    }

    /**
     * Get the number of PSEUDO-teacher lessons (e.g. Stage) counted toward
     * {@link #getDailyLessonCount} for a group on a specific date - see
     * {@link #pseudoDailyLessonCount}'s own javadoc.
     */
    private int getPseudoDailyLessonCount(Long groupId, LocalDate date) {
        Map<LocalDate, Integer> groupPseudoCount = pseudoDailyLessonCount.get(groupId);
        if (groupPseudoCount == null) {
            return 0;
        }
        return groupPseudoCount.getOrDefault(date, 0);
    }

    /**
     * Increment the daily PSEUDO-teacher lesson count for a group - called
     * alongside {@link #incrementDailyLessonCount} whenever the placed/
     * already-placed lesson's teacher is pseudo (see {@link
     * #pseudoDailyLessonCount}'s own javadoc).
     */
    private void incrementPseudoDailyLessonCount(Long groupId, LocalDate date) {
        pseudoDailyLessonCount
            .computeIfAbsent(groupId, k -> new HashMap<>())
            .merge(date, 1, Integer::sum);
    }

    /**
     * True if {@code group} has already reached its H11 max-hours-per-day cap
     * on {@code date} (null group, unset/zero {@code maxHoursPerDay} = never
     * capped). Shared by {@link #findBestLessonForSlot} and
     * {@link #exceedsDailyCap} - the same H11 predicate, previously
     * duplicated at each call site.
     */
    private boolean groupDailyCapReached(StudentGroup group, LocalDate date) {
        return group != null && group.maxHoursPerDay != null && group.maxHoursPerDay > 0
                && getDailyLessonCount(group.id, date) >= group.maxHoursPerDay;
    }

    /**
     * Get the number of lessons placed for a teacher on a specific date.
     * Used for H7 (Max teacher daily hours) enforcement (TASK-381 "L2" stamp).
     */
    private int getTeacherDailyLessonCount(Long teacherId, LocalDate date) {
        Map<LocalDate, Integer> teacherDailyCount = teacherDailyLessonCount.get(teacherId);
        if (teacherDailyCount == null) {
            return 0;
        }
        return teacherDailyCount.getOrDefault(date, 0);
    }

    /**
     * Increment the daily lesson count for a teacher after placing a lesson.
     */
    private void incrementTeacherDailyLessonCount(Long teacherId, LocalDate date) {
        teacherDailyLessonCount
            .computeIfAbsent(teacherId, k -> new HashMap<>())
            .merge(date, 1, Integer::sum);
    }

    /** TASK-527 short-day scatter: the MOVE mirror of the increment above. */
    private void decrementTeacherDailyLessonCount(Long teacherId, LocalDate date) {
        Map<LocalDate, Integer> byDate = teacherDailyLessonCount.get(teacherId);
        if (byDate != null) {
            byDate.merge(date, -1, Integer::sum);
        }
    }

    /** TASK-527 short-day scatter: the MOVE mirror of {@link #incrementDailyLessonCount}. */
    private void decrementDailyLessonCount(Long groupId, LocalDate date) {
        Map<LocalDate, Integer> byDate = dailyLessonCount.get(groupId);
        if (byDate != null) {
            byDate.merge(date, -1, Integer::sum);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // TASK-381 "L2" — weekly template construction (template-first + the
    // above round-robin/force-place as the repair tail, see class javadoc).
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds ONE representative school week per group ({@link
     * WeeklyTemplateBuilder}) from the real calendar/quota/availability data
     * this phase already has cached, then STAMPS it across every matching
     * school date (Step 2 of the L2 design). Whatever the stamp can't place
     * — quota-day mismatches, teacher unavailable on that specific date,
     * course exhausted before the week's template quota, etc. — is left for
     * the existing round-robin pass and {@link #boxUnplaceableLeftovers} to
     * repair, exactly as before this task (they still iterate every lesson
     * with {@code timeslot == null}, template-placed or not).
     *
     * <p>Every legality check the STAMP performs mirrors the checks the old
     * round-robin path already made (VRP membership via {@code
     * Lesson.getAvailableTimeslots()}, in-memory H1/H2/H3 via {@link
     * #canPlaceLesson}, H7/H11 daily caps) - so any imprecision in the
     * template itself (built from MODAL/majority patterns, not exact
     * per-date truth) only costs recurrence quality, never correctness: a
     * cell that isn't legal on a specific date is simply skipped, never
     * force-placed here.</p>
     *
     * <p>The stamp deliberately runs across the FULL school year with no
     * {@link #getIdealEndDate} cap, unlike the repair tail's (round-robin/
     * {@link #boxUnplaceableLeftovers}) legacy day-scan bound - that cap is kept
     * as-is for the repair tail, this is not a call to remove it there. This
     * is the golden-first calibration from TASK-379/M12: the school itself
     * spreads 29.3% of hours beyond the computed target end dates ("lo
     * spalmone è lo spalmare"), M12 at {@code ONE_MEDIUM}/day is the
     * calibrated counterweight for the resulting overflow, and S12 rewards
     * year-long weekly subject presence - all three assume the stamp is free
     * to place recurring cells all the way to the year's real end.</p>
     */
    /** TASK-542: the day-compact fabricator's opt-in flag (DEFAULT OFF, standard form). */
    static final String DAY_COMPACT_FLAG = "planner.construction.day-compact.enabled";

    static boolean dayCompactEnabled() {
        return Boolean.parseBoolean(System.getProperty(DAY_COMPACT_FLAG));
    }

    /** TASK-576 F4: the pair-cells template builder's opt-in flag (DEFAULT OFF, standard
     *  form). Stays opt-in - the real-seed guard regressed under it (+18 under-quota
     *  cells, TASK-577); see the TASK-576 register before flipping. */
    public static final String PAIR_CELLS_FLAG = "planner.construction.pair-cells.enabled";

    /** Public since F7-A: ScheduleService's statics publication reads it pre-solve. */
    public static boolean pairCellsEnabled() {
        return Boolean.parseBoolean(System.getProperty(PAIR_CELLS_FLAG));
    }

    /**
     * TASK-576 F5: pair-exhaustion substitution in the stamp, opt-in flag (DEFAULT OFF,
     * standard form). When the cell's course has no lessons left, the next sibling
     * course of the SAME (teacher, group) pair takes the cell: the prassi's own
     * "first course A exhausts, then course B starts in the same slots". Without it
     * the skipped cell leaves a mid-day H17 hole (F4 signature: exhausted 3945 vs
     * 3032 at equal fixture, holes 114 vs 43 slots). Independent of the pair-cells
     * template flag: it helps the historic builder too (its 3032 skips are the same
     * phenomenon, just less damaging because its blocks sit at day edges).
     */
    static final String PAIR_EXHAUSTION_FLAG = "planner.construction.pair-exhaustion.enabled";

    static boolean pairExhaustionEnabled() {
        return Boolean.parseBoolean(System.getProperty(PAIR_EXHAUSTION_FLAG));
    }

    /**
     * TASK-542 F1 (/simplify 23/08): the graft's {@link DayCompactBuilder.CellPlacer} -
     * the live-queue half of the shared {@link DayCompactBuilder#stampDay} walk. Each cell
     * comes from the same per-course queue the historic stamp polls, passes the course's
     * VRP set, and commits via the same {@link #assignVariables}+{@link #trackAssignment}
     * path (TASK-378). {@link #undo} rolls a partial block back: untrack, unassign, and
     * the lesson returns to the HEAD of its queue (the walk undoes in reverse order, so
     * each offerFirst restores the original queue order).
     */
    private DayCompactBuilder.CellPlacer graftPlacer(StudentGroup group, LocalDate date,
            Timeslot[] dayTs, Map<Long, ArrayDeque<Lesson>> queuesById,
            Map<Course, Set<Timeslot>> availableTimeslotsByCourse,
            Map<Long, Integer> placedByCourse) {
        return new DayCompactBuilder.CellPlacer() {
            @Override
            public Object place(Course course, int slot) {
                Timeslot ts = slot < dayTs.length ? dayTs[slot] : null;
                Set<Timeslot> allowed = availableTimeslotsByCourse.get(course);
                ArrayDeque<Lesson> queue = queuesById.get(course.id);
                Lesson lesson = null;
                String reason = null;
                if (ts == null) {
                    reason = "slot senza timeslot";
                } else if (allowed != null && !allowed.contains(ts)) {
                    reason = "cella fuori value range (VRP size=" + (allowed == null ? -1 : allowed.size()) + ")";
                } else if (queue == null || (lesson = queue.pollFirst()) == null) {
                    reason = "coda vuota o assente per il corso " + course.subject + " (id=" + course.id + ")";
                }
                if (reason != null) {
                    recordDeclineExample(group, date, slot, course, reason);
                    if (lesson != null) {
                        queue.offerFirst(lesson); // poll succeeded but a later guard failed
                    }
                    return null;
                }
                assignVariables(lesson, ts);
                trackAssignment(lesson, ts);
                lessonsPlaced++;
                placedByCourse.merge(lesson.course.id, 1, Integer::sum);
                return lesson;
            }

            @Override
            public void undo(Object handle) {
                Lesson lesson = (Lesson) handle;
                untrackAssignment(lesson);
                phaseContext.execute(Moves.change(timeslotVariable, lesson, null));
                roomNullCount--; // assignVariables counted it; the cell is unplaced again
                lessonsPlaced--;
                placedByCourse.merge(lesson.course.id, -1, Integer::sum);
                queuesById.get(lesson.course.id).offerFirst(lesson);
            }
        };
    }

    /** TASK-542: keep up to 3 concrete decline examples for the log (F7: one id, one reason). */
    private void recordDeclineExample(StudentGroup group, LocalDate date, int slot, Course course, String reason) {
        if (dayCompactDeclineExamples.size() < 3) {
            dayCompactDeclineExamples.add(String.format("%s|%s slot%d: %s -> %s",
                    group.name, date, slot, course.subject, reason));
        }
    }

    /** TASK-542: cells placed by the day-compact fabricator (0 when the flag is OFF). */
    public int getDayCompactPlaced() {
        return dayCompactPlaced;
    }

    /** TASK-542: menu hours declined by the fabricator (fallback to the historic stamp). */
    public int getDayCompactDeclined() {
        return dayCompactDeclined;
    }

    /** TASK-542 F8: blocks rolled back whole after a partial placement (must stay rare). */
    public int getDayCompactTruncated() {
        return dayCompactTruncated;
    }

    /** TASK-542: up to 3 concrete decline examples (diagnosis). */
    public List<String> getDayCompactDeclineExamples() {
        return List.copyOf(dayCompactDeclineExamples);
    }

    private void stampWeeklyTemplate(SchoolSchedule schedule, Map<StudentGroup, List<Lesson>> groupedByStudentGroup) {
        if (groupedByStudentGroup.isEmpty()) {
            return;
        }

        // O(1) (date, slotIndex) -> Timeslot index for the Step 2 stamp loop
        // (Step 1's own copy lives in WeeklyTemplatePlanner, TASK-413).
        Map<LocalDate, Timeslot[]> timeslotsByDateSlot = new HashMap<>();
        if (schedule.getTimeslots() != null) {
            for (Timeslot ts : schedule.getTimeslots()) {
                int slotIdx = TimeslotSequence.slotIndexOf(ts);
                if (slotIdx < 0) continue;
                timeslotsByDateSlot
                    .computeIfAbsent(ts.getDate(), d -> new Timeslot[TimeslotSequence.SLOTS_PER_DAY])[slotIdx] = ts;
            }
        }
        List<LocalDate> allSchoolDates = new ArrayList<>(timeslotsByDateSlot.keySet());
        Collections.sort(allSchoolDates);
        if (allSchoolDates.isEmpty()) {
            return;
        }
        List<StudentGroup> groups = new ArrayList<>(groupedByStudentGroup.keySet());
        groups.sort(Comparator.comparing(g -> g.name));

        // ── Step 1: build the weekly template ──
        // Extracted to WeeklyTemplatePlanner (TASK-413) so ScheduleService can
        // publish a BYTE-IDENTICAL template PRE-SOLVE for the M32 (Template
        // deviation) constraint. The planner is a pure function of the same
        // pre-solve problem facts this phase runs on (holidays/stagePatterns
        // already cached here, lessons/timeslots straight off the working
        // solution), so its result coincides with the pre-solve one - see the
        // planner's own javadoc and
        // claudedocs/design/task-413-quasi-pinning-graduato-2026-07-24.md.
        Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> template =
                pairCellsEnabled()
                        ? WeeklyTemplatePlanner.buildPairCells(schedule.getLessons(), schedule.getTimeslots(), holidays, stagePatterns)
                        : WeeklyTemplatePlanner.build(schedule.getLessons(), schedule.getTimeslots(), holidays, stagePatterns);

        // TASK-576 F7-A: with the pair builder armed, hand the EXACT template this phase
        // is about to stamp to the repair-side guard, right here where it exists. This
        // is the only publish point: the phase owns the truth (seed included, on GRASP
        // attempts), and publishing from the pre-solve statics point instead proved
        // WRONG (the promotion release re-runs that point on an all-placed population
        // and would wipe the guard one stage before the repairs - code review
        // 2026-09-01). Cleared once per cycle by the staged runner.
        if (pairCellsEnabled()) {
            PairCellGuard.publish(template);
            LOG.infof("Pair-cell guard (TASK-576 F7-A): %d pairs / %d template cells published from the construction phase (repair-aware=%b)",
                    PairCellGuard.publishedPairCount(), PairCellGuard.publishedCellCount(), PairCellGuard.enabled());
        } else {
            // The guard's LIVE count line lives in the publish above; this branch keeps a
            // solve that runs WITHOUT the pair builder from charging M38/F9/F7-A against a
            // previous cycle's stale form (the staged entry clear does not cover the plain
            // /solve path - simplify review 2026-09-01).
            PairCellGuard.clear();
        }

        // ── Step 2: stamp the template across the year ──
        Map<Course, ArrayDeque<Lesson>> unplacedByCourse = new TreeMap<>(Comparator.comparing(c -> c.id));
        for (List<Lesson> lessons : groupedByStudentGroup.values()) {
            for (Lesson lesson : lessons) {
                if (lesson.course != null) {
                    unplacedByCourse.computeIfAbsent(lesson.course, c -> new ArrayDeque<>()).add(lesson);
                }
            }
        }
        Map<Course, Set<Timeslot>> availableTimeslotsByCourse = new HashMap<>();
        Map<Long, ArrayDeque<Lesson>> queuesById = new HashMap<>();
        for (Map.Entry<Course, ArrayDeque<Lesson>> entry : unplacedByCourse.entrySet()) {
            // Deterministic placement order within a course (id ascending);
            // every unpinned Lesson of the same Course shares the same VRP
            // filter result (see Lesson.getAvailableTimeslots() javadoc), so
            // one representative's set covers the whole course.
            List<Lesson> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator.comparing(l -> l.id));
            entry.getValue().clear();
            entry.getValue().addAll(sorted);
            Lesson representative = entry.getValue().peekFirst();
            queuesById.put(entry.getKey().id, entry.getValue());
            if (representative != null) {
                // TASK-578: FRESH source for the per-course memo (the widest memoization in
                // the codebase: the first thing to break if a statics writer ever lands in-build)
                availableTimeslotsByCourse.put(entry.getKey(), new HashSet<>(representative.getAvailableTimeslotsFresh()));
            }
        }

        // ── Step 0 (TASK-542 iter-20): the day-compact fabricator. When armed, place the
        // golden-frozen block menu FIRST: every (group, weekday) whose menu resolves becomes
        // ONE contiguous run from the group's first calendar-open slot (zero H17/H31 by
        // construction, the basin analysis' finding). The cells are taken from the SAME
        // unplaced queues the historic stamp uses, so a group/weekday the menu does not
        // cover simply falls through to the historic stamp below (the declared fallback).
        // The slot walk is the SHARED DayCompactBuilder.stampDay core (F1, /simplify 23/08):
        // a block that cannot complete is rolled back WHOLE (F8 - a truncated block is a
        // non-golden shape), and the per-cell VRP check inside the placer subsumes the old
        // block-level first-cell guard (F2). Placements go through the SAME
        // assignVariables+trackAssignment path as the historic stamp (TASK-378: direct
        // field writes are invisible to Timefold's bookkeeping and left the live conflict
        // maps blind to the graft's placements).
        if (dayCompactEnabled()) {
            var menuRows = DayCompactMenuSource.load();
            var menu = DayCompactMenuSource.resolve(menuRows,
                    schedule.getStudentGroups() != null ? schedule.getStudentGroups() : List.of(),
                    schedule.getCourses() != null ? schedule.getCourses() : List.of());
            // TASK-542 cure-2 (post-mortem iter-22): BUDGET PACING. The menu demands the
            // FULL weekly composition every week, but course budgets are annual: unpaced,
            // the graft drains the queues on the earliest weeks (iter-22: 6664h "coda
            // vuota" from January on, 1014 boxed at construction, 21 empty G08 days in
            // spring). The historic stamp paces via weeklyTargets (hours/active-weeks);
            // here the same idea as a Bresenham-style allowance: at the w-th occurrence
            // (0-based) of a weekday out of W, a block of h hours may be placed only if
            // (placed + h) * W <= budget * (w + 1) - never AHEAD of the proportional pace.
            // A course whose budget can sustain every week is untouched; a thin course
            // spreads its hours evenly across the year instead of front-loading them.
            Map<DayOfWeek, List<LocalDate>> datesByWeekday = new HashMap<>();
            for (LocalDate d : allSchoolDates) {
                datesByWeekday.computeIfAbsent(d.getDayOfWeek(), k -> new ArrayList<>()).add(d);
            }
            Map<Long, Integer> budget0 = new HashMap<>();
            Map<Long, Integer> placedByCourse = new HashMap<>();
            for (var e : queuesById.entrySet()) {
                budget0.put(e.getKey(), e.getValue().size());
            }
            for (var groupEntry : menu.entrySet()) {
                StudentGroup g = groupEntry.getKey();
                var byWeekday = groupEntry.getValue();
                for (var wdEntry : byWeekday.entrySet()) {
                    DayOfWeek wd = wdEntry.getKey();
                    List<LocalDate> wdDates = datesByWeekday.getOrDefault(wd, List.of());
                    int weekIndex = 0;
                    for (LocalDate date : wdDates) {
                        List<DayCompactBuilder.MenuBlock> dayMenu = wdEntry.getValue();
                        int w = weekIndex++;
                        int totalWeeks = wdDates.size();
                        // pace-filter this date's menu: blocks ahead of pace are deferred
                        List<DayCompactBuilder.MenuBlock> pacedMenu = new ArrayList<>(dayMenu.size());
                        for (DayCompactBuilder.MenuBlock block : dayMenu) {
                            int budget = budget0.getOrDefault(block.course().id, 0);
                            int placed = placedByCourse.getOrDefault(block.course().id, 0);
                            // TASK-636 (decision A, maintainer 21/09: "la risposta che
                            // piu' si allinea al Golden attuale"): W EFFECTIVE per
                            // course from the golden cadence reference. The uniform
                            // Bresenham (W = totalWeeks) is the iter-22 cure and
                            // manufactures the every-Nth-week dilution for thin
                            // courses; the golden instead CONCENTRATES some courses
                            // (the fixed-teacher labs: T01 Servizio tecnico =
                            // 10 active weeks, not 34). With W = the golden's own
                            // active-week count the same inequality paces the course
                            // to complete INSIDE its golden window (60h / W=10 admits
                            // 6h/week for 10 weeks: exactly the golden burst), while
                            // courses without a reference keep today's uniform spread
                            // (fallback untouched: pacing stays, iter-22 stays cured;
                            // the golden's own existence proves the aggregate does
                            // not starve the tail - its spring days are filled by the
                            // other courses that pace to year-end). PROTOTYPE opt-in.
                            int paceWeeks = totalWeeks;
                            if (goldenWeeksPacingEnabled() && block.course().subject != null) {
                                int goldenWeeks = SchoolSchedule.getCadenceActiveWeeks(
                                        g.name,
                                        SubjectNormalization.normalize(block.course().subject));
                                if (goldenWeeks > 0 && goldenWeeks < totalWeeks) {
                                    paceWeeks = goldenWeeks;
                                }
                            }
                            if (budget <= 0
                                    || (placed + block.hours()) * paceWeeks <= budget * (w + 1)) {
                                pacedMenu.add(block);
                            } else {
                                dayCompactPacedOff += block.hours();
                            }
                        }
                        if (pacedMenu.isEmpty()) {
                            continue; // whole day deferred to the historic stamp
                        }
                        int[] window = SchoolSchedule.getGroupCalendarWindow(g.id, wd, date);
                        Timeslot[] dayTs = timeslotsByDateSlot.get(date);
                        if (window == null || window[1] < 0 || dayTs == null) {
                            dayCompactDeclined += pacedMenu.stream()
                                    .mapToInt(DayCompactBuilder.MenuBlock::hours).sum();
                            continue; // calendar closed: the day is declined, never forced
                        }
                        DayCompactBuilder.DayWalk walk = DayCompactBuilder.stampDay(
                                pacedMenu, window[1], window[2], graftPlacer(g, date, dayTs,
                                        queuesById, availableTimeslotsByCourse, placedByCourse));
                        dayCompactPlaced += walk.placedCells();
                        dayCompactDeclined += walk.declinedHours();
                        dayCompactTruncated += walk.truncatedBlocks();
                    }
                }
            }
            LOG.infof("day-compact (iter-20 REAL GRAFT + cure-2 pacing): placed %d cell(s)"
                    + " from the frozen menu, declined %d hour(s) (calendar/budget/window/VRP,"
                    + " of which %d block(s) rolled back after a partial placement), paced-off"
                    + " %d hour(s) (ahead of the proportional budget pace, deferred to the"
                    + " historic stamp); decline examples: %s",
                    dayCompactPlaced, dayCompactDeclined, dayCompactTruncated, dayCompactPacedOff,
                    dayCompactDeclineExamples);
        }

        // TASK-432 (candidate, flag-gated, default OFF): profile-following pre-pass. Before the
        // chronological stamp consumes each course's queue date-by-date (the UNIFORM cadence that
        // burns a concentrated course's hours on the earliest, low-quota days and leaves the school's
        // high-expectation "monographic" days under-filled), this pass re-allocates the courses whose
        // per-date expected-hours profile is NON-UNIFORM so their hours land on the high-quota days
        // first, in descending-quota order - exactly the shape the school's own sheet encodes. It
        // commits straight into the live tracking maps and the per-course queue, so the UNCHANGED
        // chronological stamp below simply finds those queues emptier and skips them; every placement
        // is gated on the same hard checks the stamp uses, so the pass can add no H1/H2/H3/H7/H11
        // conflict and no over-quota cell. NON-ADOPTED: enabled only by
        // -Dplanner.construction.profile-following.enabled=true; measured by
        // WeeklyTemplateConstructionRealSeedTest, not shipped.
        if (Boolean.getBoolean("planner.construction.profile-following.enabled")) {
            reserveProfileFollowingPlacements(unplacedByCourse, availableTimeslotsByCourse);
        }

        // TASK-542 iter-25 (mandato maintainer 23/08): teacher-weekday STEERING pre-pass, flag
        // gated DEFAULT OFF (planner.construction.teacher-weekday-steering.enabled). For
        // every teacher in the golden-frozen matrix (>=50% of their hours on one weekday),
        // allocate their courses' queues on their TARGET weekday's dates FIRST
        // (chronological, VRP-filtered, same hard gates as the stamp: canPlaceLesson
        // H1/H3, exceedsDailyCap H7/H11, per-date expected cap pseudo-netted). Preference,
        // never a pin: every placement can still be declined, the unchanged chronological
        // stamp below covers any remainder on any weekday, and the parity check TASK-517
        // runs on the resulting plan before any adoption.
        if (teacherWeekdaySteeringEnabled()) {
            reserveTeacherWeekdayPlacements(unplacedByCourse, availableTimeslotsByCourse);
        }

        // TASK-564 v2 (27/08 notte, stamp-aware: the place-only pass measured the
        // post-stamp venues EXHAUSTED - 0/13 empty cells even before the box pass,
        // registro 27/08): the tail of the year is where the chronological stamp's
        // course queues run dry and the cross-group teacher contention leaves
        // declared days empty. This pre-pass gives the group's LAST declared dates
        // (the measured risk window: the 13 residual cells all sit in the last 8
        // weeks) FIRST PICK of their own template cells, in descending-date order,
        // with the same gates as the chronological stamp. The unchanged stamp below
        // finds those queues emptier and skips; the shape stays the template's own
        // (weekday, slot) cells, so M32/recurrence are neutral by construction.
        if (DeclaredDayPreBooker.enabled()) {
            // TASK-590: the census statics are cleared HERE, before the prebooking
            // gate's reserve pass, so a construction with prebooking disabled leaves
            // them EMPTY (a stale previous census would be indistinguishable from a
            // fresh one at the bench)
            DeclaredDayPreBooker.RESERVE_CENSUS.clear();
            DeclaredDayPreBooker.ALLOWANCE_SNAPSHOT.clear();
            reserveDeclaredDayPlacements(schedule, template, timeslotsByDateSlot,
                    unplacedByCourse, availableTimeslotsByCourse);
            // TASK-577 9/09: slot retry for the dates the block-guard left at zero
            if (SlotRetryPass.enabled()) {
                SlotRetryPass.retry(schedule, template, timeslotsByDateSlot,
                        unplacedByCourse, availableTimeslotsByCourse, this);
            }
        }

        // TASK-576 F5: sibling courses per (teacher, group) pair, for the
        // pair-exhaustion substitution below. Built once, empty (and unused) when
        // the flag is OFF.
        Map<Course, List<Course>> siblingsByCourse = pairExhaustionEnabled()
                ? buildPairSiblingCourses(unplacedByCourse.keySet())
                : Map.of();

        for (LocalDate date : allSchoolDates) {
            DayOfWeek weekday = date.getDayOfWeek();
            if (weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY) continue;
            Timeslot[] daySlots = timeslotsByDateSlot.get(date);
            for (StudentGroup group : groups) {
                Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>> groupTemplate = template.get(group);
                if (groupTemplate == null) continue;
                List<WeeklyTemplateBuilder.TemplateCell> cells = groupTemplate.get(weekday);
                if (cells == null || cells.isEmpty()) continue;

                // TASK-385: per-(date, group) expected-hours fill-goal cap,
                // computed ONCE here rather than per slot below - both inputs
                // (the sheet fact and the calendar-open count) are invariant
                // for this whole (date, group) pair; only getDailyLessonCount
                // in the slot loop below is genuinely dynamic (it changes as
                // this same loop places lessons).
                int expectedHoursCap = expectedHoursCapFor(group, date);

                for (WeeklyTemplateBuilder.TemplateCell cell : cells) {
                    Course course = cell.course();
                    ArrayDeque<Lesson> queue = unplacedByCourse.get(course);
                    Set<Timeslot> legal = availableTimeslotsByCourse.get(course);
                    // TASK-576 F5: once the cell's course is exhausted, the cell STAYS
                    // with the pair: the substitution below re-points course/queue/legal
                    // to a sibling course of the same pair, so later slots of this cell
                    // (and this skip's own slot) serve the sibling instead of skipping.
                    boolean cellRepointedToF5Sibling = false;
                    for (int slot = cell.slotIndex(); slot < cell.slotIndex() + cell.length(); slot++) {
                        Timeslot ts = daySlots != null && slot < daySlots.length ? daySlots[slot] : null;
                        if (ts == null) {
                            stampSkipIllegalOrClosed++;
                            skipForGroup(group, CAUSE_ILLEGAL);
                            continue;
                        }
                        if (queue == null || queue.isEmpty()) {
                            // TASK-576 F5 (prassi referente's own exhaustion): the pair's
                            // cell does not die with its course. When the cell's course
                            // has no lessons left, the next sibling course of the SAME
                            // (teacher, group) pair takes the cell ("first course A
                            // exhausts, then course B starts in the same slots"). The
                            // normal gates below still apply to the substitute; if no
                            // sibling has a legal lesson this stays the historic skip.
                            if (pairExhaustionEnabled() && !cellRepointedToF5Sibling) {
                                Course sibling = firstSiblingWithQueueFor(course, ts, siblingsByCourse,
                                        unplacedByCourse, availableTimeslotsByCourse);
                                if (sibling != null) {
                                    course = sibling;
                                    queue = unplacedByCourse.get(sibling);
                                    legal = availableTimeslotsByCourse.get(sibling);
                                    cellRepointedToF5Sibling = true;
                                }
                            }
                            if (queue == null || queue.isEmpty()) {
                                stampSkipExhausted++;
                                skipForGroup(group, CAUSE_EXHAUSTED);
                                continue;
                            }
                        }
                        if (legal != null && !legal.contains(ts)) {
                            stampSkipIllegalOrClosed++;
                            skipForGroup(group, CAUSE_ILLEGAL);
                            continue;
                        }
                        Lesson next = queue.peekFirst();
                        if (!canPlaceLesson(next, ts) || exceedsDailyCap(next, ts)) {
                            stampSkipConflict++;
                            skipForGroup(group, CAUSE_CONFLICT);
                            continue;
                        }
                        // Stage-exclusive cap (see expectedHoursCapFor's own javadoc)
                        // vs. a count that includes any already-placed pinned Stage
                        // hours - net those out via pseudoDailyLessonCount so the
                        // comparison is apples-to-apples (TASK-385 code-review fix).
                        int nonPseudoCount = getDailyLessonCount(group.id, date) - getPseudoDailyLessonCount(group.id, date);
                        if (expectedHoursCap >= 0 && nonPseudoCount >= expectedHoursCap) {
                            stampSkipExpectedHoursGoal++;
                            skipForGroup(group, CAUSE_QUOTA);
                            continue;
                        }

                        queue.pollFirst();
                        assignVariables(next, ts);
                        trackAssignment(next, ts);
                        lessonsPlaced++;
                        stampPlaced++;
                        if (cellRepointedToF5Sibling) {
                            stampPairExhaustionPlaced++;
                        }
                    }
                }

                // TASK-532 quota-aware fill tail: the template cells define the
                // SHAPE, but the day must reach its DECLARED aula+lab quota (the
                // same fact H32 reads). When the cells' queues ran dry or their
                // slots conflicted, the old stamp left the day short and the LS
                // inherited 57 scattered H32 cells. Here we place ANY of the
                // group's still-unplaced lessons (VRP-legal, conflict-free, under
                // the day cap) into the day's remaining open slots until the quota
                // is met. Same hard gates as the cell loop above: this can accept
                // no conflict and no over-quota cell; it only ever fills forward.
                if (expectedHoursCap > 0) {
                    int current = getDailyLessonCount(group.id, date) - getPseudoDailyLessonCount(group.id, date);
                    if (current < expectedHoursCap) {
                        // TASK-637 second lever: with theory-spread ON, the fill tail
                        // HOLDS BACK a second hour of a theory course on a day that
                        // already has one (the remaining M-TR source after the 1h-chunk
                        // fix: the tail refills quota holes without subject-day memory).
                        // One pass over the schedule per DEFICIT (group, date); full
                        // days skip it; the flag is default OFF, so the OFF path is
                        // free. Keyed by SubjectNormalization.theoryRuleGroupingKey, the
                        // SAME key M-TR and the builder's own spread check group by, so
                        // the tail's notion of "same subject" cannot drift from the
                        // meter (the T06 alias hook is the divergence case;
                        // unlike M-TR's population this scan includes pseudo-teacher
                        // hours, inert because pinned Stage lessons never sit in the
                        // unplaced queues).
                        // If this lever is ever ratified default ON, replace the scan
                        // with a journal-maintained sibling of dailyLessonCount (the
                        // efficiency reviewer's named cheaper form).
                        Set<String> theorySubjectsToday = null;
                        if (WeeklyTemplateBuilder.isTheorySpreadEnabled()) {
                            theorySubjectsToday = new HashSet<>();
                            for (Lesson placedLesson : schedule.getLessons()) {
                                if (placedLesson.timeslot != null
                                        && date.equals(placedLesson.timeslot.getDate())
                                        && placedLesson.course != null
                                        && placedLesson.course.studentGroup != null
                                        && placedLesson.course.studentGroup.id == group.id
                                        && SubjectNormalization.isTheoryForRule(
                                                placedLesson.course.isLab, placedLesson.course.subject)) {
                                    theorySubjectsToday.add(SubjectNormalization.theoryRuleGroupingKey(
                                            placedLesson.course.subject));
                                }
                            }
                        }
                        Timeslot[] daySlotsLocal = daySlots;
                        for (int slot = 0; slot < slotSlotsLimit(daySlotsLocal); slot++) {
                            if (current >= expectedHoursCap) {
                                break;
                            }
                            Timeslot ts = daySlotsLocal != null && slot < daySlotsLocal.length ? daySlotsLocal[slot] : null;
                            if (ts == null) {
                                continue;
                            }
                            // any of the group's courses with a legal, conflict-free lesson fits.
                            // TASK-637/636 selection: one best-candidate slot with an
                            // EXPLICIT three-tier rank (the review's altitude finding:
                            // the old two-slot form made theory-spread silently outrank
                            // the pace boost by statement order). Rank 0 = behind golden
                            // pace (TASK-636, early break keeps its short-circuit), 1 =
                            // eligible, 2 = held theory-repeat (TASK-637 last resort:
                            // still fills if nothing else can, the quota contract holds).
                            Map.Entry<Course, ArrayDeque<Lesson>> chosen = null;
                            int chosenRank = Integer.MAX_VALUE;
                            boolean chosenByBoost = false;
                            for (Map.Entry<Course, ArrayDeque<Lesson>> entry : unplacedByCourse.entrySet()) {
                                Course course = entry.getKey();
                                if (course.studentGroup == null || course.studentGroup.id != group.id) {
                                    continue;
                                }
                                ArrayDeque<Lesson> queue = entry.getValue();
                                if (queue == null || queue.isEmpty()) {
                                    continue;
                                }
                                Set<Timeslot> legal = availableTimeslotsByCourse.get(course);
                                if (legal != null && !legal.contains(ts)) {
                                    continue;
                                }
                                Lesson next = queue.peekFirst();
                                if (!canPlaceLesson(next, ts) || exceedsDailyCap(next, ts)) {
                                    continue;
                                }
                                boolean held = theorySubjectsToday != null
                                        && SubjectNormalization.isTheoryForRule(course.isLab, course.subject)
                                        && theorySubjectsToday.contains(
                                                SubjectNormalization.theoryRuleGroupingKey(course.subject));
                                int rank = held ? 2 : 1;
                                boolean byBoost = false;
                                if (!held
                                        && behindGoldenPace(group, course, date, course.totalHours - queue.size())) {
                                    rank = 0;
                                    byBoost = true;
                                }
                                if (rank < chosenRank) {
                                    chosen = entry;
                                    chosenRank = rank;
                                    chosenByBoost = byBoost;
                                    if (rank == 0) {
                                        break;
                                    }
                                }
                            }
                            if (chosen != null && current < expectedHoursCap) {
                                Lesson next = chosen.getValue().peekFirst();
                                chosen.getValue().pollFirst();
                                assignVariables(next, ts);
                                trackAssignment(next, ts);
                                lessonsPlaced++;
                                stampPlaced++;
                                quotaFillPlaced++;
                                if (chosenRank == 2) {
                                    theoryHoldFills++;
                                } else if (theorySubjectsToday != null
                                        && SubjectNormalization.isTheoryForRule(
                                                chosen.getKey().isLab, chosen.getKey().subject)) {
                                    // first hour of a theory subject today: it enters the
                                    // hold set so later slots treat it as a repeat
                                    theorySubjectsToday.add(SubjectNormalization.theoryRuleGroupingKey(
                                            chosen.getKey().subject));
                                }
                                if (chosenByBoost) {
                                    paceBoostApplications++;
                                }
                                current++;
                            }
                        }
                    }
                }
            }
        }
        LOG.infof("Weekly-template stamp (TASK-381 L2) complete: placed %d (of which %d by the TASK-532"
                + " quota-aware fill tail, %d by pair-exhaustion TASK-576 F5), skipped conflict/cap %d, "
                + "skipped illegal/closed %d, skipped exhausted %d, skipped expected-hours goal %d (TASK-385)",
                stampPlaced, quotaFillPlaced, stampPairExhaustionPlaced, stampSkipConflict, stampSkipIllegalOrClosed, stampSkipExhausted, stampSkipExpectedHoursGoal);
        // TASK-576 F6 diagnosis: per-group skip geography, top 8 by total (causes:
        // conflict, illegal, exhausted, quota) - the residual holes' cause profile.
        stampSkipsByGroup.entrySet().stream()
                .sorted((a, b) -> {
                    int byTotal = Integer.compare(
                            b.getValue()[0] + b.getValue()[1] + b.getValue()[2] + b.getValue()[3],
                            a.getValue()[0] + a.getValue()[1] + a.getValue()[2] + a.getValue()[3]);
                    // deterministic tie-break: same-total groups keep name order across
                    // JVMs so consecutive stamp logs are diffable (code-review nit)
                    return byTotal != 0 ? byTotal : a.getKey().compareTo(b.getKey());
                })
                .limit(8)
                .forEach(e -> LOG.infof("  stamp skips %s: conflict %d, illegal %d, exhausted %d, quota %d",
                        e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]));

        // TASK-533/536 combined package (maintainer 20/08): floor-aware tail. The
        // quota-aware fill tail above aims at the DECLARED quota; when that is
        // unreachable (queues dry, VRP-illegal, conflicts) the day can sit
        // below the H33 floor at the signature. This SECOND sweep, after the
        // chronological loop consumed what it could, retries every under-floor
        // (group, date) with whatever is still unplaced: same hard gates as
        // the fill tail (canPlaceLesson H1/H3, VRP set, day cap never
        // exceeded), it only RETARGETS the placement priority at the floor.
        // It never moves placed lessons (that was the rejected via (b)).
        floorAwareTail(groupedByStudentGroup, timeslotsByDateSlot, unplacedByCourse,
                availableTimeslotsByCourse);

        // TASK-527 BUG A/B cure (design 8f581832): the stamp's fixed weekly cell
        // layout repeats one short weekday all year (26 Tuesdays at 4h for G12,
        // Mon-5/Tue-Fri-4 for G08, Fridays for G04). The deterministic post-pass
        // below moves one movable lesson from a FULL weekday to the SHORT one, per
        // ISO week, cycling the source weekday week by week so the deficit SCATTERS
        // instead of patterning. Every move is gated on the stamp's own hard checks
        // (canPlaceLesson H1/H3, expected-hours cap via the pseudo-netted count) and
        // on the course's VRP set: the pass can accept no conflict and no over-quota.
        shortDayScatter(groupedByStudentGroup, timeslotsByDateSlot, availableTimeslotsByCourse);
    }

    /** Golden-weeks pacing (Fedora 21/09 prototype): pace a course over the golden's
     *  own active-week count instead of the uniform year spread. Opt-in, only the
     *  literal "true" arms (bench-gated). */
    static final String GOLDEN_WEEKS_PACING_FLAG = "planner.construction.golden-weeks-pacing.enabled";

    static boolean goldenWeeksPacingEnabled() {
        return "true".equals(System.getProperty(GOLDEN_WEEKS_PACING_FLAG));
    }

    /**
     * TASK-636: golden-pace tracking in the construction tails. RATIFIED
     * DEFAULT ON (maintainer, 21/09 sera, A/B 5/5 criteri: braccio ON [0/0]hard al
     * tentativo 1 vs OFF -2, ricorrenza 70,5 vs 69,2, sanity migliore, medium
     * pari; banco costruzione OFF byte-identico / ON +1,4pp ricorrenza con
     * distribuzione Servizio tecnico G06 da 16h-in-coda a 38h distribuite;
     * VERDICT task636-results/ab-adoption). Standard opt-out form: null/absent
     * enables, only the literal "false" disarms. With the flag armed, a course
     * BEHIND its published cadence-reference cumulative-hours curve
     * (SchoolSchedule.getCadenceGoldenCumulative, same reference as M34/M35 and
     * the same declared-input survival path, workbook sheet 21_CadenzaAttesa)
     * keeps top priority in the round-robin repair tail and wins the slot in
     * the quota-aware fill tail; at or ahead of pace, or with no curve covering
     * the pair, plain behavior applies. A NEVER-placed course needs no boost:
     * it already holds the 999 never-placed sentinel. Preference, never force:
     * no gate is bypassed, an absent reference is inert, and the curve only
     * ever pulls a course UP TO the reference line, never beyond the declared
     * weekly caps M34 already enforces.
     */
    static final String CADENCE_PACE_FLAG = "planner.construction.cadence-pace.enabled";

    public static boolean cadencePaceEnabled() {
        String v = System.getProperty(CADENCE_PACE_FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    /** Opt-in flag for the TASK-533/536 floor-aware construction tail (standard form). */
    static final String FLOOR_AWARE_TAIL_FLAG = "planner.construction.floor-aware-tail.enabled";

    static boolean floorAwareTailEnabled() {
        return Boolean.getBoolean(FLOOR_AWARE_TAIL_FLAG);
    }

    /** TASK-533/536 combined package: second stamp sweep that retries every
     *  under-floor (group, date) with the still-unplaced pool (see the call
     *  site's comment). Deterministic (groups by name, dates chronological,
     *  courses by id); improve-or-skip per placement; pinned/pseudo never
     *  touched; the day cap is still the ceiling (quota is never exceeded to
     *  reach the floor: floor <= quota by derivation). */
    private void floorAwareTail(Map<StudentGroup, List<Lesson>> groupedByStudentGroup,
            Map<LocalDate, Timeslot[]> timeslotsByDateSlot,
            Map<Course, ArrayDeque<Lesson>> unplacedByCourse,
            Map<Course, Set<Timeslot>> availableTimeslotsByCourse) {
        if (!floorAwareTailEnabled() || groupedByStudentGroup.isEmpty()) {
            return;
        }
        int placed = 0;
        int cellsClosed = 0;
        List<StudentGroup> groups = new ArrayList<>(groupedByStudentGroup.keySet());
        groups.sort(Comparator.comparing(g -> g.name));
        List<LocalDate> dates = new ArrayList<>(timeslotsByDateSlot.keySet());
        Collections.sort(dates);
        for (StudentGroup group : groups) {
            Integer floorObj = SchoolSchedule.getDayFloor(group.id);
            if (floorObj == null || floorObj <= 0) {
                continue;
            }
            int floor = floorObj;
            for (LocalDate date : dates) {
                int cap = expectedHoursCapFor(group, date);
                if (cap <= 0) {
                    continue; // no quota fact / closed day: not a session day
                }
                int current = getDailyLessonCount(group.id, date) - getPseudoDailyLessonCount(group.id, date);
                if (current >= floor) {
                    continue;
                }
                boolean closedThis = false;
                Timeslot[] daySlotsLocal = timeslotsByDateSlot.get(date);
                for (int slot = 0; daySlotsLocal != null && slot < daySlotsLocal.length; slot++) {
                    if (current >= floor || current >= cap) {
                        break;
                    }
                    Timeslot ts = daySlotsLocal[slot];
                    if (ts == null) {
                        continue;
                    }
                    for (Map.Entry<Course, ArrayDeque<Lesson>> entry : unplacedByCourse.entrySet()) {
                        if (current >= floor || current >= cap) {
                            break;
                        }
                        Course course = entry.getKey();
                        if (course.studentGroup == null || course.studentGroup.id != group.id) {
                            continue;
                        }
                        ArrayDeque<Lesson> queue = entry.getValue();
                        if (queue == null || queue.isEmpty()) {
                            continue;
                        }
                        Set<Timeslot> legal = availableTimeslotsByCourse.get(course);
                        if (legal != null && !legal.contains(ts)) {
                            continue;
                        }
                        Lesson next = queue.peekFirst();
                        if (!canPlaceLesson(next, ts) || exceedsDailyCap(next, ts)) {
                            continue;
                        }
                        queue.pollFirst();
                        assignVariables(next, ts);
                        trackAssignment(next, ts);
                        lessonsPlaced++;
                        stampPlaced++;
                        placed++;
                        current++;
                        closedThis = true;
                    }
                }
                if (current >= floor && closedThis) {
                    cellsClosed++;
                }
            }
        }
        LOG.infof("Floor-aware tail (TASK-533/536): placed %d lesson(s), closed %d under-floor"
                + " cell(s) at stamp end", placed, cellsClosed);
    }

    /** Opt-in flag for the TASK-527 short-day scatter post-pass (standard form). */
    static final String SHORT_DAY_SCATTER_FLAG = "planner.construction.short-day-scatter.enabled";

    static boolean shortDayScatterEnabled() {
        return Boolean.getBoolean(SHORT_DAY_SCATTER_FLAG);
    }

    /**
     * TASK-527: scatter the stamp's patterned short days. For each (group, ISO week):
     * find the weekday(s) at cap-1 and the weekday(s) at cap; move ONE movable
     * lesson from a full weekday to the short one, choosing the SOURCE weekday by
     * week index modulo (so week 1 robs Tuesday, week 2 Wednesday, ...), and the
     * slot as the first VRP-legal free slot of the short day. Deterministic,
     * improve-or-skip per move, never touches pinned/pseudo lessons.
     */
    private void shortDayScatter(Map<StudentGroup, List<Lesson>> groupedByStudentGroup,
            Map<LocalDate, Timeslot[]> timeslotsByDateSlot,
            Map<Course, Set<Timeslot>> availableTimeslotsByCourse) {
        if (!shortDayScatterEnabled() || groupedByStudentGroup.isEmpty()) {
            return;
        }
        int moved = 0;
        for (Map.Entry<StudentGroup, List<Lesson>> ge : groupedByStudentGroup.entrySet()) {
            StudentGroup group = ge.getKey();
            // placed, movable lessons of the group by date (non-pinned, non-pseudo)
            Map<LocalDate, List<Lesson>> movableByDate = new HashMap<>();
            for (Lesson l : ge.getValue()) {
                if (l.timeslot == null || l.pinned || l.course == null) {
                    continue;
                }
                Teacher t = l.getTeacher();
                if (t != null && t.pseudo) {
                    continue;
                }
                movableByDate.computeIfAbsent(l.timeslot.getDate(), d -> new ArrayList<>()).add(l);
            }
            // group the school dates by ISO week (chronological)
            Map<Integer, List<LocalDate>> weeks = new TreeMap<>();
            for (LocalDate d : timeslotsByDateSlot.keySet()) {
                weeks.computeIfAbsent(d.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()), w -> new ArrayList<>()).add(d);
            }
            int weekIndex = 0;
            for (List<LocalDate> week : weeks.values()) {
                // sort BEFORE iterating the list (sorting mid-iteration is the CME
                // the first scatter run hit at line 1262: ArrayList sort while iterating)
                Collections.sort(week);
                // short day: count == cap-1 (pseudo-netted); full days: count >= cap
                // TURN0-3 refinement (Fedora 19/08 11:46): (a) G12 Tuesdays at 4h
                // survived the first pass because the single cycled source often had
                // no legally movable lesson: now EVERY full weekday is tried, cycled
                // order first; (b) G04 Fridays sat at 3h vs 5h DECLARED (deficit of
                // 2, the old count == cap-1 test missed it): now ANY deficit below
                // the declared quota is filled, up to the full gap, one move at a
                // time. Total is conserved (each move shifts one hour), so the
                // pool-cap yearly sum is untouched: only the SHAPE follows the
                // school's per-weekday declaration.
                LocalDate shortDay = null;
                int deficit = 0;
                List<LocalDate> fullDays = new ArrayList<>();
                for (LocalDate d : week) {
                    int cap = expectedHoursCapFor(group, d);
                    if (cap <= 0) {
                        continue;
                    }
                    int count = getDailyLessonCount(group.id, d) - getPseudoDailyLessonCount(group.id, d);
                    if (count < cap && shortDay == null) {
                        shortDay = d;
                        deficit = cap - count;
                    } else if (count >= cap) {
                        fullDays.add(d);
                    }
                }
                if (shortDay == null || fullDays.isEmpty()) {
                    weekIndex++;
                    continue;
                }
                // source order: the cycled weekday first (scatter signature), then
                // the remaining full days as fallback
                List<LocalDate> sourceOrder = new ArrayList<>(fullDays);
                Collections.rotate(sourceOrder, -(weekIndex % fullDays.size()));
                Timeslot[] shortSlots = timeslotsByDateSlot.get(shortDay);
                int placedMoves = 0;
                for (LocalDate source : sourceOrder) {
                    if (placedMoves >= deficit) {
                        break;
                    }
                    List<Lesson> candidates = movableByDate.getOrDefault(source, List.of());
                    for (Lesson l : new ArrayList<>(candidates)) {
                        if (placedMoves >= deficit) {
                            break;
                        }
                        Set<Timeslot> legal = availableTimeslotsByCourse.get(l.course);
                        for (int slot = 0; shortSlots != null && slot < shortSlots.length; slot++) {
                            Timeslot ts = shortSlots[slot];
                            if (ts == null || (legal != null && !legal.contains(ts))) {
                                continue;
                            }
                            if (!canPlaceLesson(l, ts)) {
                                continue;
                            }
                            untrackAssignment(l);
                            assignVariables(l, ts);
                            trackAssignment(l, ts);
                            movableByDate.getOrDefault(source, new ArrayList<>()).remove(l);
                            movableByDate.computeIfAbsent(shortDay, k -> new ArrayList<>()).add(l);
                            moved++;
                            placedMoves++;
                            break;
                        }
                    }
                }
                weekIndex++;
            }
        }
        LOG.infof("Short-day scatter (TASK-527): moved %d lesson(s) from full weekdays to"
                + " short weekdays (cycled per ISO week)", moved);
    }

    /** Opt-in flag for the TASK-533 via-(b) quota-tail moves-placed pass (standard form). */
    static final String QUOTA_TAIL_MOVES_PLACED_FLAG =
            "planner.construction.quota-tail-moves-placed.enabled";

    static boolean quotaTailMovesPlacedEnabled() {
        return Boolean.getBoolean(QUOTA_TAIL_MOVES_PLACED_FLAG);
    }

    /** TASK-533 via (b): moves already-PLACED hours (the fill tail only places
     *  unplaced ones) from the group's surplus days to its under-quota days, so
     *  the H32 signature is closed at end of A-CONSTRUCTION. Border-aware rules
     *  (the QFR lesson): the donor lesson is taken from the END of the donor
     *  day's occupied run (the run stays contiguous - no H17 hole opened), and
     *  the target slot is the run's max+1 (the target day only EXTENDS upward:
     *  never the first hour, never a mid-run fill that would strand hours).
     *  Deterministic order (targets most-under-quota first, donors most-surplus
     *  first, ties by date), same hard gates as the stamp (H1/H3 via the live
     *  maps, course VRP set), pinned/pseudo never moved, donor never drops
     *  below its own quota. H32 scope exclusions mirror QuotaFillRepair
     *  (G04 / G08 pool-cap, G14 import-pinned). */
    private void quotaTailMovesPlaced(SchoolSchedule schedule,
            Map<StudentGroup, List<Lesson>> groupedByStudentGroup) {
        if (!quotaTailMovesPlacedEnabled() || groupedByStudentGroup.isEmpty()) {
            return;
        }
        // same O(1) (date, slotIndex) -> Timeslot index the stamp builds
        Map<LocalDate, Timeslot[]> timeslotsByDateSlot = new HashMap<>();
        if (schedule.getTimeslots() != null) {
            for (Timeslot ts : schedule.getTimeslots()) {
                int slotIdx = TimeslotSequence.slotIndexOf(ts);
                if (slotIdx < 0) continue;
                timeslotsByDateSlot
                    .computeIfAbsent(ts.getDate(), d -> new Timeslot[TimeslotSequence.SLOTS_PER_DAY])[slotIdx] = ts;
            }
        }
        int moved = 0;
        int cellsClosed = 0;
        int rejNoSlot = 0, rejPinned = 0, rejPseudo = 0, rejVrp = 0, rejConflict = 0;
        for (Map.Entry<StudentGroup, List<Lesson>> ge : groupedByStudentGroup.entrySet()) {
            StudentGroup group = ge.getKey();
            if ("G04".equals(group.name) || "G08".equals(group.name)
                    || "G14".equals(group.name)) {
                continue; // H32 scope: pool-cap / import-pinned own their per-day target
            }
            // live occupied-run view per date: slot -> lesson (ALL placed lessons,
            // pinned/pseudo included - they are occupancy the borders must respect)
            Map<LocalDate, TreeMap<Integer, Lesson>> runByDate = new HashMap<>();
            for (Lesson l : ge.getValue()) {
                if (l.timeslot == null) {
                    continue;
                }
                runByDate.computeIfAbsent(l.timeslot.getDate(), d -> new TreeMap<>())
                        .put(TimeslotSequence.slotIndexOf(l.timeslot), l);
            }
            // classify (deficit / surplus) on the non-pseudo count, quota = same
            // accessor H32 and the fill tail read
            final class Cell {
                final LocalDate date;
                final int cap;
                int hours;
                Cell(LocalDate date, int cap, int hours) {
                    this.date = date;
                    this.cap = cap;
                    this.hours = hours;
                }
            }
            List<Cell> deficits = new ArrayList<>();
            List<Cell> surpluses = new ArrayList<>();
            for (var re : runByDate.entrySet()) {
                int cap = expectedHoursCapFor(group, re.getKey());
                if (cap <= 0) {
                    continue; // no quota fact (or closed day): never a target NOR a donor
                }
                int nonPseudo = 0;
                for (Lesson l : re.getValue().values()) {
                    var t = l.getTeacher();
                    if (t == null || !t.pseudo) {
                        nonPseudo++;
                    }
                }
                if (nonPseudo < cap) {
                    deficits.add(new Cell(re.getKey(), cap, nonPseudo));
                } else if (nonPseudo > cap) {
                    surpluses.add(new Cell(re.getKey(), cap, nonPseudo));
                }
            }
            if (!deficits.isEmpty() && !surpluses.isEmpty()) {
                LOG.infof("[quota-tail-moves] group %s: %d deficit cell(s), %d surplus cell(s)",
                        group.name, deficits.size(), surpluses.size());
            }
            if (deficits.isEmpty() || surpluses.isEmpty()) {
                continue;
            }
            deficits.sort(Comparator.comparingInt((Cell c) -> c.cap - c.hours).reversed()
                    .thenComparing(c -> c.date));
            surpluses.sort(Comparator.comparingInt((Cell c) -> c.hours - c.cap).reversed()
                    .thenComparing(c -> c.date));
            for (Cell target : deficits) {
                for (Cell donor : surpluses) {
                    if (target.hours >= target.cap || donor.hours <= donor.cap) {
                        continue;
                    }
                    TreeMap<Integer, Lesson> targetRun = runByDate.get(target.date);
                    TreeMap<Integer, Lesson> donorRun = runByDate.get(donor.date);
                    if (targetRun == null || donorRun == null || donorRun.isEmpty()) {
                        continue;
                    }
                    // target border: extend the run upward, never the first hour
                    int targetSlot = targetRun.lastKey() + 1;
                    Timeslot[] daySlots = timeslotsByDateSlot.get(target.date);
                    Timeslot targetTs = daySlots != null && targetSlot < daySlots.length
                            ? daySlots[targetSlot] : null;
                    if (targetTs == null) {
                        rejNoSlot++;
                        break; // run already ends at the day's last slot: cell stays
                    }
                    // donor border: only the run's ENDS are removable (contiguity
                    // kept) - the tail first, then the head when it is not the
                    // day's first hour (removing a slot-0 occupant would push the
                    // donor into a late start M18 does not forgive).
                    boolean placed = false;
                    List<Lesson> borderCandidates = new ArrayList<>(List.of(donorRun.lastEntry().getValue()));
                    if (donorRun.size() > 1 && donorRun.firstKey() > 0) {
                        borderCandidates.add(donorRun.firstEntry().getValue());
                    }
                    for (Lesson candidate : borderCandidates) {
                        if (candidate.pinned) {
                            rejPinned++;
                            continue; // pinned end: this donor's border is frozen
                        }
                        var ct = candidate.getTeacher();
                        if (ct != null && ct.pseudo) {
                            rejPseudo++;
                            continue; // Stage hour: counted in the stageHours bucket, not H32's
                        }
                        // TASK-578: per-slot legality (stale-cache class)
                        if (!candidate.isSlotLegalFresh(targetTs)) {
                            rejVrp++;
                            continue; // VRP: this course may not sit at that slot
                        }
                        if (!canPlaceLesson(candidate, targetTs)) {
                            rejConflict++;
                            continue; // H1/H3 live gate
                        }
                        int donorSlotKey = donorRun.entrySet().stream()
                                .filter(en -> en.getValue() == candidate)
                                .map(Map.Entry::getKey).findFirst().orElse(-1);
                        untrackAssignment(candidate);
                        assignVariables(candidate, targetTs);
                        trackAssignment(candidate, targetTs);
                        donorRun.remove(donorSlotKey);
                        targetRun.put(targetSlot, candidate);
                        donor.hours--;
                        target.hours++;
                        moved++;
                        quotaTailMoved++;
                        placed = true;
                        break;
                    }
                    if (placed) {
                        continue;
                    }
                }
                if (target.hours >= target.cap) {
                    cellsClosed++;
                }
            }
        }
        LOG.infof("Quota-tail moves-placed (TASK-533 via b): moved %d lesson(s), closed %d"
                + " under-quota cell(s) at construction end (rejected: no-target-slot %d,"
                + " donor-tail-pinned %d, donor-tail-pseudo %d, vrp %d, conflict %d)",
                moved, cellsClosed, rejNoSlot, rejPinned, rejPseudo, rejVrp, rejConflict);
    }

    /** Mirror of {@link #trackAssignment} for a MOVE: drop the lesson's current
     *  timeslot from the phase's live conflict maps before re-tracking it. */
    private void untrackAssignment(Lesson lesson) {
        Timeslot old = lesson.timeslot;
        if (old == null) {
            return;
        }
        Teacher teacher = lesson.getTeacher();
        StudentGroup group = lesson.getStudentGroup();
        if (teacher != null) {
            Set<Timeslot> ts = teacherAssignedTimeslots.get(teacher.id);
            if (ts != null) {
                ts.remove(old);
            }
            decrementTeacherDailyLessonCount(teacher.id, old.getDate());
        }
        if (group != null) {
            Set<Timeslot> ts = groupAssignedTimeslots.get(group.id);
            if (ts != null) {
                ts.remove(old);
            }
            decrementDailyLessonCount(group.id, old.getDate());
        }
    }

    /**
     * TASK-432 (candidate, flag-gated, default OFF): profile-following pre-pass, the
     * construction-level counterpart to the uniform weekly template's chronological
     * stamp. For each course whose per-date expected-hours profile is NON-UNIFORM
     * (the school's own "monographic day" signal - one candidate day's quota sits at
     * least {@code CONCENTRATION_THRESHOLD} hours above the modal weekday quota), it
     * re-allocates the course's still-unplaced hours in DESCENDING-quota order,
     * filling each high-expectation day up to that day's expected-hours capacity
     * before touching any lower-quota day. Uniform courses (every candidate day the
     * same quota) are skipped untouched, so the pass cannot perturb them.
     *
     * <p>This is the pattern-exhaustion-first lever (CLAUDE.md): exhaust the school's
     * per-date profile at 100% - the high-quota days filled to their plan - and let
     * only a true excess become the remainder. It is NEVER a proportional/diluting
     * spread. Canonical case: G10 / Servizio tecnico e stilistico (T01,
     * Thursday-only) has 9 monographic Thursdays whose quotas (5+5+7+8x6) sum to the
     * course's 65h EXACTLY, so descending-quota fill empties the queue on those 9
     * days and leaves the autumn 4h Thursdays free of the subject - inverting the
     * baseline's 37h-on-autumn / 24h-on-monographic maldistribution.
     *
     * <p>The two-phase allocation the task specifies is realized by the single
     * descending walk: phase 1 is the high-quota days (filled to quota first); the
     * same walk then absorbs any remainder on the lower-quota days (phase 2). Any
     * residual the walk could not place (a date hit its VRP/H7/H11 ceiling before
     * its expected quota) stays in the queue and falls through to the UNCHANGED
     * chronological stamp below, exactly like a course this pass never touched.
     *
     * <p><b>Safety (mirrors {@link #reserveSlot0Anchors}'s flag-gated sibling, TASK-430):</b>
     * every placement is gated on the SAME hard checks the chronological stamp's own
     * inner loop uses - {@link #canPlaceLesson} (H1/H2/H3), {@link #exceedsDailyCap}
     * (H7/H11), and the per-date expected-hours cap ({@link #expectedHoursCapFor},
     * stage-exclusive via the {@link #getPseudoDailyLessonCount} netting) - and the
     * candidate slots come straight from the course's VRP set
     * ({@link Lesson#getAvailableTimeslots}), which already excludes closed,
     * no-session, stage-zero-quota and teacher-unavailable days. So the pass can
     * ACCEPT NO conflict and NO over-quota cell; it only ever skips forward. It
     * commits each placement through the same {@link #assignVariables} +
     * {@link #trackAssignment} path and decrements the shared per-course queue, so
     * the chronological stamp reads the updated live maps and emptier queues and
     * cannot double-place. The {@code stampSkipConflict} stat therefore stays 0
     * with the flag ON (the pass adds no accepted-conflict category of its own).
     *
     * <p><b>Determinism:</b> courses are processed in a stable SEMANTIC order
     * (group/subject/teacher names - never raw {@code course.id}, same cross-profile
     * reproducibility rationale as the L2 review fixes) and every date collection is
     * a sorted list or a {@code TreeMap}, so the pass is reproducible across
     * re-seeded profiles. (A teacher shared across groups - e.g. T01 on 1/2/3
     * FA + G12, all Thursday-only - is naturally rotated by the school's own
     * per-group high-quota days; where two groups' high days collide on the same
     * date the second-processed group yields to H1 and takes its next-best day, a
     * residual for the repair tail / LS, not a correctness issue.)
     *
     * <p><b>NON-ADOPTED:</b> enabled only by
     * {@code -Dplanner.construction.profile-following.enabled=true}; OFF by default,
     * so it does not affect any solve until ratified. The recurrence guard in
     * {@code WeeklyTemplateConstructionRealSeedTest} HOLDS at the flag-OFF level with
     * the flag ON (measured 74.7% both states on the real seed): the monographic days
     * fall in weekly-consistent clusters (Dec / Jan / May-Jun), so concentrating the
     * subject onto them does not erode week-over-week recurrence, and the low-quota
     * days the subject vacates are picked up by the displaced multi-weekday courses.
     * The dedicated G10 Thursday-profile metric (65h on the 9 monographic days, 0h
     * elsewhere) is the signal that the pass is concentrating, measured alongside it.
     */
    /** TASK-542 iter-25: the teacher-weekday steering pre-pass flag (standard form, DEFAULT OFF). */
    static final String TEACHER_WEEKDAY_STEERING_FLAG =
            "planner.construction.teacher-weekday-steering.enabled";

    static boolean teacherWeekdaySteeringEnabled() {
        return Boolean.parseBoolean(System.getProperty(TEACHER_WEEKDAY_STEERING_FLAG));
    }

    /** TASK-542 iter-25: cells placed by the steering pre-pass (0 when the flag is OFF). */
    private int steeringPlaced = 0;

    /**
     * TASK-542 iter-25: place each steered teacher's still-unplaced hours on their
     * golden-concentrated weekday's dates, BEFORE the chronological stamp can spread
     * them across the week. Same safety shape as {@link #reserveProfileFollowingPlacements}:
     * every placement passes the stamp's own hard gates (VRP set, {@link #canPlaceLesson},
     * {@link #exceedsDailyCap}, per-date expected-hours cap pseudo-netted), so the pass can
     * accept no conflict and no over-quota cell; the chronological stamp below finds these
     * queues emptier and simply skips them, and any remainder (queue dry, target weekday
     * saturated) falls through unchanged. Deterministic (teachers by matrix order, courses
     * by id, dates chronological, slots ascending).
     */
    private void reserveTeacherWeekdayPlacements(Map<Course, ArrayDeque<Lesson>> unplacedByCourse,
            Map<Course, Set<Timeslot>> availableTimeslotsByCourse) {
        Map<String, DayOfWeek> steerByTeacher =
                TeacherWeekdaySteeringSource.byTeacherName(TeacherWeekdaySteeringSource.load());
        if (steerByTeacher.isEmpty()) {
            return;
        }
        // deterministic course order (id ascending, same key the queues use)
        List<Course> courses = new ArrayList<>(unplacedByCourse.keySet());
        courses.sort(Comparator.comparing(c -> c.id));
        int teachersSteered = 0;
        for (Course course : courses) {
            Teacher teacher = course.teacher;
            if (teacher == null || teacher.name == null) continue;
            DayOfWeek target = steerByTeacher.get(teacher.name);
            if (target == null) continue;
            ArrayDeque<Lesson> queue = unplacedByCourse.get(course);
            if (queue == null || queue.isEmpty()) continue;
            Set<Timeslot> legal = availableTimeslotsByCourse.get(course);
            if (legal == null || legal.isEmpty()) continue;
            StudentGroup group = course.studentGroup;
            if (group == null) continue;
            teachersSteered++;
            // the teacher's target-weekday slots, chronological, ascending slot index
            List<Timeslot> targetSlots = new ArrayList<>();
            for (Timeslot ts : legal) {
                if (ts.getDate() != null && ts.getDate().getDayOfWeek() == target) {
                    targetSlots.add(ts);
                }
            }
            targetSlots.sort(Comparator
                    .comparing(Timeslot::getDate)
                    .thenComparingInt(TimeslotSequence::slotIndexOf));
            for (Timeslot ts : targetSlots) {
                if (queue.isEmpty()) break;
                int expectedCap = expectedHoursCapFor(group, ts.getDate());
                if (expectedCap >= 0) {
                    int nonPseudo = getDailyLessonCount(group.id, ts.getDate())
                            - getPseudoDailyLessonCount(group.id, ts.getDate());
                    if (nonPseudo >= expectedCap) continue; // day at its expected quota
                }
                Lesson next = queue.peekFirst();
                if (!canPlaceLesson(next, ts) || exceedsDailyCap(next, ts)) continue;
                queue.pollFirst();
                assignVariables(next, ts);
                trackAssignment(next, ts);
                lessonsPlaced++;
                stampPlaced++;
                steeringPlaced++;
            }
        }
        LOG.infof("Teacher-weekday steering (TASK-542 iter-25): placed %d cell(s) for %d"
                + " steered course(s) on their golden weekday", steeringPlaced, teachersSteered);
    }

    /**
     * TASK-576 F5: for each course, its sibling courses of the SAME (teacher, group)
     * pair (id order, so the substitute choice is deterministic). Courses without a
     * teacher or group have no pair and map to nothing; singleton pairs are omitted
     * (no siblings to substitute).
     */
    private static Map<Course, List<Course>> buildPairSiblingCourses(Set<Course> courses) {
        record PairKey(long teacherId, long groupId) {}
        Map<PairKey, List<Course>> byPair = new HashMap<>();
        for (Course c : courses) {
            if (c == null || c.teacher == null || c.studentGroup == null) continue;
            byPair.computeIfAbsent(new PairKey(c.teacher.id, c.studentGroup.id), k -> new ArrayList<>()).add(c);
        }
        Map<Course, List<Course>> siblings = new HashMap<>();
        for (List<Course> pairCourses : byPair.values()) {
            if (pairCourses.size() < 2) continue;
            pairCourses.sort(java.util.Comparator.comparing(c -> c.id));
            for (Course c : pairCourses) {
                List<Course> others = new ArrayList<>(pairCourses);
                others.remove(c);
                siblings.put(c, others);
            }
        }
        return siblings;
    }

    /**
     * TASK-576 F5: the first sibling of the cell's course that still has lessons and
     * may legally use this timeslot. The conflict/cap/quota gates are NOT checked
     * here: the caller's normal slot flow applies them to the substitute, so a
     * failing substitute counts as the historic skip of its own cause.
     */
    private static Course firstSiblingWithQueueFor(Course cellCourse, Timeslot ts,
            Map<Course, List<Course>> siblingsByCourse,
            Map<Course, ArrayDeque<Lesson>> unplacedByCourse,
            Map<Course, Set<Timeslot>> availableTimeslotsByCourse) {
        List<Course> siblings = siblingsByCourse.get(cellCourse);
        if (siblings == null) return null;
        for (Course sibling : siblings) {
            ArrayDeque<Lesson> q = unplacedByCourse.get(sibling);
            if (q == null || q.isEmpty()) continue;
            Set<Timeslot> legal = availableTimeslotsByCourse.get(sibling);
            if (legal != null && !legal.contains(ts)) continue;
            return sibling;
        }
        return null;
    }

    /**
     * TASK-564 v2: reserve the group's TAIL declared dates' template cells before
     * the chronological stamp burns the course queues. The tail window defaults to
     * the last 56 declared dates of each group (the measured risk window: every
     * residual empty cell of the 26-27/08 cycles sits in the last 8 weeks),
     * overridable with {@code planner.construction.prebooking.tail-days}. Dates are
     * served DESCENDING (the latest, most exhaustion-exposed date picks first);
     * every placement passes the chronological stamp's own gates (VRP range, H1/H3
     * via canPlaceLesson, H7/H11 via exceedsDailyCap, the pseudo-netted expected
     * cap) and rides the same assign/track path, so the unchanged stamp below
     * simply finds those queues emptier.
     *
     * <p>TASK-564 v2.1 (28/08 notte batch, gate esito -81hard): the gate's forensics
     * attributed the damage to the reserve's SIZE, not its existence - 3122 cells
     * (~30% of the year) re-ordered tail-first: (a) mid-year days of the
     * last-in-alphabet groups (G10/G13/G11) kept 50 of the 74 delivered H17
     * gaps because their courses' queues were drained under their own mid-year
     * template cells (the 8,9,[10],11,[12],13 signature), and (b) the raw-quota cap
     * chased unreachable shapes for the pool-cap groups. Two cuts refine it, each
     * opt-out via its own knob for the differential bench ({@link
     * DeclaredDayPreBooker#QUOTA_AWARE_KNOB}, {@link DeclaredDayPreBooker#BLOCK_GUARD_KNOB}):
     * <ul>
     *   <li>QUOTA-AWARE: fill-excluded groups (1/G08 pool-cap, G14) are skipped
     *       wholesale (H32/H34 do not protect their declared days, and the raw-quota
     *       cap would chase targets their pools cannot reach - post-scope the live
     *       fill-family target and {@link #expectedHoursCapFor} coincide for every
     *       remaining group, which is why no second accessor is introduced); and a
     *       course's queue may only give the tail its SURPLUS beyond the mid-year
     *       obligation - the hours the course's own cells claim on VRP-reachable
     *       non-tail declared dates. An exactly-provisioned course is untouchable,
     *       so the mid-year stamp finds every queue as full as the legacy run left
     *       it and the cannibalization collateral dies by construction.</li>
     *   <li>BLOCK GUARD (the H17 census' rule: the rare cell is worth less than the
     *       block): a date's reserved part must be a slot-contiguous PREFIX - cells
     *       are walked in slot order and the FIRST failure of any cause (dry queue,
     *   exhausted surplus, VRP, H1/H3/H7 gate) stops the whole date. The legacy
     *       skip-and-continue is what interleaved two teachers around a hole
     *       (66 of the 74 delivered gaps) and split same-teacher runs (the G13
     *       T25 11..[12]..13 signature).</li>
     * </ul>
     * The CONTROL arm needs ALL THREE cuts off ({@code quota-aware=false},
     * {@code block-guard=false}, {@code parity=false}): parity is a third
     * default-armed cut with its own knob, and the two walk-semantics changes
     * (stop at first failure, off-grid slot) only exist under the guard.
     */
    private void reserveDeclaredDayPlacements(
            SchoolSchedule schedule,
            Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> template,
            Map<LocalDate, Timeslot[]> timeslotsByDateSlot,
            Map<Course, ArrayDeque<Lesson>> unplacedByCourse,
            Map<Course, Set<Timeslot>> availableTimeslotsByCourse) {
        int tailDays = DeclaredDayPreBooker.tailDays();
        boolean quotaAware = DeclaredDayPreBooker.quotaAwareRefinementEnabled();
        boolean blockGuard = DeclaredDayPreBooker.blockGuardRefinementEnabled();
        boolean parity = DeclaredDayPreBooker.parityRefinementEnabled();
        com.school.planner.solver.DeclaredDayPreBooker.GoldenWeekdayPractice practice =
                parity ? DeclaredDayPreBooker.goldenWeekdayPractice() : null;
        boolean journal = Boolean.getBoolean(DeclaredDayPreBooker.DEBUG_FLAG);
        boolean substitution = DeclaredDayPreBooker.substitutionEnabled();
        if (journal) {
            // one journal PER CONSTRUCTION: a multi-start cycle runs up to five
            // of these on one JVM, and a never-drained static would fuse them
            // into a reserve-plan no single attempt produced
            DeclaredDayPreBooker.RESERVE_JOURNAL.clear();
        }
        // TASK-590 rejection census: cleared BEFORE the prebooking gate (see caller);
        // always collected (one line per declared date), never read in production
        int reserved = 0;
        int datesServed = 0;
        int prefixStops = 0;
        int budgetCappedDates = 0;
        int parityCuts = 0;
        // F8 baseline: hours already placed per (teacher, date) across the whole
        // schedule - computed once, read per reserved date (cheap: one pass over
        // the lessons)
        Map<String, Integer> placedPairDay = new HashMap<>();
        if (parity) {
            for (Lesson l : schedule.getLessons()) {
                if (l.timeslot == null || l.getTeacher() == null || l.getTeacher().pseudo) {
                    continue;
                }
                placedPairDay.merge(l.getTeacher().name + "|" + l.timeslot.getDate(), 1, Integer::sum);
            }
        }
        // deterministic group order: the template map's groups by name (same order
        // the chronological stamp's own group loop uses)
        List<StudentGroup> groups = new ArrayList<>(template.keySet());
        groups.sort(Comparator.comparing(g -> g.name));
        for (StudentGroup group : groups) {
            if (group == null || group.id == null) continue;
            if (quotaAware && SchoolScheduleConstraintProvider.isQuotaFillExcluded(group.name)) {
                continue; // nothing H34-protectable to win; the raw cap would chase the pool-gap
            }
            Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>> groupTemplate = template.get(group);
            if (groupTemplate == null || groupTemplate.isEmpty()) continue;
            // the group's declared dates, in-grid, newest first, capped to the tail window
            List<LocalDate> allDeclared = new ArrayList<>();
            for (var row : SchoolSchedule.getExpectedHoursForGroup(group.id).entrySet()) {
                if (row.getValue().aulaHours + row.getValue().labHours > 0
                        && timeslotsByDateSlot.containsKey(row.getKey())) {
                    allDeclared.add(row.getKey());
                }
            }
            allDeclared.sort(Comparator.reverseOrder());
            List<LocalDate> declared = allDeclared.size() > tailDays
                    ? new ArrayList<>(allDeclared.subList(0, tailDays))
                    : allDeclared;
            // QUOTA-AWARE per-date chronological claims: the reserve may only book, on
            // date D, hours the chronological stamp would NOT have consumed before
            // reaching D. The claim of everything before D is the prefix sum, over the
            // group's ascending declared dates, of each course's own cell hours on each
            // date's weekday - restricted to dates where the course has at least one
            // VRP-legal slot (a stage Friday claims nothing from an ordinary course).
            // This is what makes the reserve non-cannibalizing: the early-tail dates
            // (mid-March/April) keep their chronological priority over their own later
            // siblings, so serving the window DESCENDING can no longer starve the window
            // boundary - the measured whack-a-mole of both the gate run and the first
            // quota-aware attempt (which claimed only up to the window EDGE).
            Map<LocalDate, Map<Course, Integer>> claimBeforeByDate = new HashMap<>();
            if (quotaAware) {
                Map<Course, Map<DayOfWeek, Integer>> cellHours = new HashMap<>();
                for (var wdCells : groupTemplate.entrySet()) {
                    for (WeeklyTemplateBuilder.TemplateCell cell : wdCells.getValue()) {
                        if (cell.course() == null) continue;
                        cellHours.computeIfAbsent(cell.course(), k -> new HashMap<>())
                                .merge(wdCells.getKey(), cell.length(), Integer::sum);
                    }
                }
                List<LocalDate> ascending = new ArrayList<>(allDeclared);
                java.util.Collections.reverse(ascending);
                // only the tail window's dates are ever queried below, so the
                // snapshot copies start at the window; the reachability scan
                // itself stays PER-DATE on purpose (the course VRP set carries
                // date-specific facts - dated H5 vacations, stage windows - so a
                // (course, weekday) memo would reuse one date's verdict all year)
                Set<LocalDate> tailSet = new HashSet<>(declared);
                Map<Course, Integer> cum = new HashMap<>();
                for (LocalDate d : ascending) {
                    if (tailSet.contains(d)) {
                        claimBeforeByDate.put(d, new HashMap<>(cum));
                    }
                    Timeslot[] daySlots = timeslotsByDateSlot.get(d);
                    if (daySlots == null) continue;
                    for (var e : cellHours.entrySet()) {
                        Integer hours = e.getValue().get(d.getDayOfWeek());
                        if (hours == null) continue;
                        Set<Timeslot> legal = availableTimeslotsByCourse.get(e.getKey());
                        if (legal != null) {
                            boolean reachable = false;
                            for (Timeslot ts : daySlots) {
                                if (ts != null && legal.contains(ts)) {
                                    reachable = true;
                                    break;
                                }
                            }
                            if (!reachable) continue;
                        }
                        cum.merge(e.getKey(), hours, Integer::sum);
                    }
                }
            }
            for (LocalDate date : declared) {
                List<WeeklyTemplateBuilder.TemplateCell> cells = groupTemplate.get(date.getDayOfWeek());
                if (cells == null || cells.isEmpty()) {
                    DeclaredDayPreBooker.RESERVE_CENSUS.add(group.name + "|" + date
                            + "|NEVER_ATTEMPTED|no template cells for " + date.getDayOfWeek());
                    continue;
                }
                Timeslot[] daySlots = timeslotsByDateSlot.get(date);
                if (daySlots == null) continue;
                // the prefix walk needs the day's own slot order; the legacy arm keeps
                // the template's own list order so the control reproduces the gate run
                List<WeeklyTemplateBuilder.TemplateCell> orderedCells = blockGuard
                        ? new ArrayList<>(cells) : cells;
                if (blockGuard) {
                    orderedCells.sort(Comparator.comparingInt(WeeklyTemplateBuilder.TemplateCell::slotIndex));
                }
                int expectedHoursCap = expectedHoursCapFor(group, date);
                boolean served = false;
                boolean prefixBroken = false;
                boolean budgetLimited = false;
                // TASK-590 census state: the holder makes "first walk-stopping rejection
                // wins" a mechanism instead of a repeated null-check idiom
                ReserveCensusState census = new ReserveCensusState();
                Map<Course, Integer> claimBefore = quotaAware
                        ? claimBeforeByDate.getOrDefault(date, Map.of()) : null;
                // PARITY (TASK-517): the pair-day counter starts from the pair's
                // ALREADY-PLACED hours on this date (pins + the earlier reserve*
                // passes own part of the day too), then counts this pass's bookings
                Map<String, Integer> pairDayBooked = parity ? new HashMap<>(placedPairDay) : null;
                for (WeeklyTemplateBuilder.TemplateCell cell : orderedCells) {
                    if (prefixBroken) break;
                    Course course = cell.course();
                    // null-safe: the builder never emits null-course cells today, but
                    // the pre-fix code was accidentally null-safe via the queue break
                    String subj = course != null && course.subject != null ? course.subject : "?";
                    ArrayDeque<Lesson> queue = unplacedByCourse.get(course);
                    Set<Timeslot> legal = availableTimeslotsByCourse.get(course);
                    // allowance: this course's hours that belong to D-and-after (the live
                    // queue already reflects any take by a LATER date's reservation, so
                    // the subtraction stays exact as the descending walk progresses)
                    int allowance = Integer.MAX_VALUE;
                    if (quotaAware) {
                        int qSize = queue != null ? queue.size() : 0;
                        allowance = Math.max(0, qSize - claimBefore.getOrDefault(course, 0));
                    }
                    for (int slot = cell.slotIndex(); slot < cell.slotIndex() + cell.length(); slot++) {
                        Timeslot ts = slot < daySlots.length ? daySlots[slot] : null;
                        if (ts == null) {
                            census.reject("VRP_REJECTION", "off-grid slot " + slot + " " + subj);
                            prefixBroken |= blockGuard;
                            break; // off-grid tail; legacy skipped on (uniform grid: same)
                        }
                        if (allowance <= 0) {
                            // everything this course still holds is claimed by dates
                            // BEFORE this one: booking here would cannibalize them
                            // TASK-590 AC#4: the date's allowance table at the FIRST
                            // allowance exhaustion, regardless of an earlier parity
                            // class (parity cuts continue the walk, so a date can
                            // carry both; the snapshot is the substitution evidence)
                            if (!census.allowanceDumped) {
                                census.allowanceDumped = true;
                                dumpAllowanceSnapshot(group, date, orderedCells,
                                        unplacedByCourse, claimBefore);
                            }
                            // Phase E prototype (DEFAULT OFF): at the cell's FIRST
                            // slot only (mid-cell deaths have their start occupied
                            // by the dying course's own bookings), try the first
                            // LATER cell-course with non-cannibalizing allowance at
                            // this cell's slot range under every gate the original
                            // faces. Later cells only: an earlier cell's course is
                            // already booked this date, and re-booking it here would
                            // be the same (group, subject) twice in one day (H28).
                            // A PARTIAL fill stops the date like the original death
                            // would: booking the next cell above the gap would be
                            // the H17 hole the block guard exists to prevent.
                            if (substitution && slot == cell.slotIndex()) {
                                int subBooked = attemptSubstitution(group, date, cell, subj,
                                        orderedCells, daySlots, unplacedByCourse,
                                        availableTimeslotsByCourse, claimBefore, quotaAware,
                                        parity, expectedHoursCap, practice, pairDayBooked,
                                        journal, census);
                                if (subBooked > 0) {
                                    reserved += subBooked;
                                    served = true; // the date IS served, by the alternative
                                    budgetLimited = true; // the leading course WAS exhausted
                                    if (subBooked < cell.length()) {
                                        prefixBroken |= blockGuard; // partial: stop the date
                                    }
                                    break; // this cell is served by the alternative
                                }
                            }
                            census.reject("ALLOWANCE_EXHAUSTED", subj + "@slot " + slot);
                            budgetLimited = true;
                            prefixBroken |= blockGuard;
                            break;
                        }
                        if (queue == null || queue.isEmpty()) {
                            census.reject("NO_CANDIDATE", subj + " pool empty@slot " + slot);
                            prefixBroken |= blockGuard;
                            break;
                        }
                        if (legal != null && !legal.contains(ts)) {
                            if (blockGuard) {
                                census.reject("VRP_REJECTION", subj + "@slot " + slot);
                                prefixBroken = true;
                                break; // F3: never book above a hole inside the cell
                            }
                            continue;
                        }
                        Lesson next = queue.peekFirst();
                        boolean conflict = !canPlaceLesson(next, ts);
                        if (conflict || exceedsDailyCap(next, ts)) {
                            if (blockGuard) {
                                census.reject("CONFLICT", subj + "@slot " + slot + " "
                                        + (conflict ? "conflict" : "daily-cap"));
                                prefixBroken = true;
                                break; // F3: a failed slot inside the cell stops the date
                            }
                            continue;
                        }
                        int nonPseudo = getDailyLessonCount(group.id, date)
                                - getPseudoDailyLessonCount(group.id, date);
                        if (expectedHoursCap >= 0 && nonPseudo >= expectedHoursCap) {
                            census.capReached = true;
                            break;
                        }
                        if (parity) {
                            String teacherName = next.getTeacher() != null ? next.getTeacher().name : null;
                            Integer goldenMax = teacherName != null
                                    ? practice.maxPairDayHours(teacherName, group.name,
                                            date.getDayOfWeek().getValue())
                                    : null;
                            if (goldenMax != null
                                    && pairDayBooked.getOrDefault(teacherName, 0) + 1 > goldenMax) {
                                // PAIR-DAY cap: this pair-day would be denser than the
                                // golden's own densest day on this (pair, weekday) cell -
                                // the booking is unfounded. (Deliberately NOT the offline
                                // check's axis A, which averages over >=3 days: a single
                                // reserved day is judged against the practiced MAX.)
                                census.reject("PARITY_REJECTION",
                                        "pair-day cap " + teacherName + "@slot " + slot);
                                parityCuts++;
                                prefixBroken |= blockGuard;
                                continue;
                            }
                            Double goldenShare = course.subject != null
                                    ? practice.subjectWeekdayShare(group.name, course.subject,
                                            date.getDayOfWeek().getValue())
                                    : null;
                            if (goldenShare != null && goldenShare < 0.15d) {
                                // WEEKDAY-share bar: the golden schedules this (group,
                                // subject) meaningfully on other weekdays and barely on
                                // this one - a booking here concentrates what the school
                                // spreads. (Deliberately a REFUSAL bar, unlike the offline
                                // check's axis C which flags >=95% concentration of a
                                // golden-spread subject; the 0.15 bar is that script's own
                                // "major weekday" definition.)
                                census.reject("PARITY_REJECTION",
                                        "weekday-share bar " + subj + "@slot " + slot);
                                parityCuts++;
                                prefixBroken |= blockGuard;
                                continue;
                            }
                        }
                        queue.pollFirst();
                        assignVariables(next, ts);
                        trackAssignment(next, ts);
                        lessonsPlaced++;
                        stampPlaced++;
                        reserved++;
                        census.booked++;
                        served = true;
                        allowance--;
                        if (parity && next.getTeacher() != null && next.getTeacher().name != null) {
                            pairDayBooked.merge(next.getTeacher().name, 1, Integer::sum);
                        }
                        if (journal) {
                            DeclaredDayPreBooker.RESERVE_JOURNAL.add(String.format("%s|%s|%d|%s|%s",
                                    group.name, date, slot,
                                    course.subject != null ? course.subject : "?",
                                    course.teacher != null ? course.teacher.name : "?"));
                        }
                    }
                }
                // TASK-590 census entry: the outcome summarizes the date (served tracks
                // any booking); rejections carry the first walk-stopping class + detail
                String censusKey;
                if (census.rejectionClass != null) {
                    // a stop class, when present, stays the headline even if a
                    // substitution rescued the leading cell: the walk still ended
                    // on that rejection, and the tail carries the substitution
                    censusKey = served ? "BOOKED_PARTIAL" : census.rejectionClass;
                } else if (!census.substitutions.isEmpty()) {
                    // substitutions are recorded only on booked>0, which is the
                    // same branch that sets served: the conjunct is implied
                    censusKey = "BOOKED_SUBSTITUTED";
                } else if (census.capReached) {
                    censusKey = served ? "BOOKED_CAP_FULL" : "FULL_AT_CAP";
                } else {
                    censusKey = served ? "BOOKED" : "UNCLASSIFIED";
                }
                String censusTail = (census.rejectionClass != null
                        ? census.rejectionClass + " " + census.rejectionDetail + " "
                        : "")
                        + (!census.substitutions.isEmpty()
                                ? "substitution " + String.join(", ", census.substitutions) + " "
                                : "")
                        + "booked=" + census.booked;
                DeclaredDayPreBooker.RESERVE_CENSUS.add(group.name + "|" + date
                        + "|" + censusKey + "|" + censusTail);
                if (served) datesServed++;
                if (prefixBroken) prefixStops++;
                if (budgetLimited) budgetCappedDates++;
            }
        }
        Map<String, Integer> censusCounts = new TreeMap<>();
        for (String line : DeclaredDayPreBooker.RESERVE_CENSUS) {
            String[] parts = line.split("\\|");
            censusCounts.merge(parts.length > 2 ? parts[2] : "?", 1, Integer::sum);
        }
        LOG.infof("TASK-590 reserve census (live path): %s", censusCounts);
        LOG.infof("Declared-day pre-booking v2.1 (TASK-564, stamp-aware): reserved %d cell(s)"
                        + " across %d tail declared date(s) (window %d day(s)/group, descending;"
                        + " quota-aware=%b block-guard=%b parity=%b, prefix-stops %d,"
                        + " claim-capped dates %d, parity cuts %d)",
                reserved, datesServed, tailDays, quotaAware, blockGuard, parity, prefixStops,
                budgetCappedDates, parityCuts);
    }

    /** TASK-590 per-date census state: the first walk-stopping rejection wins. */
    private static final class ReserveCensusState {
        String rejectionClass;
        String rejectionDetail;
        int booked;
        boolean capReached;
        boolean allowanceDumped;
        /** every orig -> alt rescue this date (a second rescue must not erase the first). */
        final List<String> substitutions = new ArrayList<>();

        void reject(String cls, String detail) {
            // post-cap rejections are not the date's story: the cap already ended
            // its meaningful walk, and recording them would relabel cap-full dates
            // as rejections (BOOKED_CAP_FULL/FULL_AT_CAP must win over them)
            if (rejectionClass == null && !capReached) {
                rejectionClass = cls;
                rejectionDetail = detail;
            }
        }
    }

    /**
     * TASK-590 AC#4 gate data: the allowance table of every template course on the
     * date whose leading cell just died on allowance exhaustion. Whether another
     * course still had non-cannibalizing allowance is the substitution question.
     */
    private void dumpAllowanceSnapshot(StudentGroup group, LocalDate date,
            List<WeeklyTemplateBuilder.TemplateCell> orderedCells,
            Map<Course, ArrayDeque<Lesson>> unplacedByCourse,
            Map<Course, Integer> claimBefore) {
        for (WeeklyTemplateBuilder.TemplateCell c2 : orderedCells) {
            Course cc = c2.course();
            if (cc == null) continue;
            var q2 = unplacedByCourse.get(cc);
            int pool = q2 != null ? q2.size() : 0;
            // quotaAware at the call site guarantees a non-null claim map
            int claim = claimBefore.getOrDefault(cc, 0);
            DeclaredDayPreBooker.ALLOWANCE_SNAPSHOT.add(group.name + "|" + date + "|"
                    + (cc.subject != null ? cc.subject : "?")
                    + "|pool=" + pool + "|claim=" + claim
                    + "|allow=" + Math.max(0, pool - claim));
        }
    }

    /**
     * TASK-590 Phase E prototype (armed by {@link
     * DeclaredDayPreBooker#substitutionEnabled}, DEFAULT OFF): at a cell whose
     * course died on allowance exhaustion, try the first OTHER cell-course of
     * the same date (cell order) that still has non-cannibalizing allowance
     * under the same claim accounting, and book it at this cell's slot range
     * under every gate the original faces (VRP legality, occupancy, daily cap,
     * expected-hours cap, both parity bars). Books contiguously from the cell's
     * start; any bar skip skips the whole alternative (parity bars are course-
     * or teacher-shaped, a later slot of the same cell would hit the same wall).
     * Returns how many lessons booked (0 = no alternative qualified; the caller
     * falls back to the unchanged allowance-death path) and records the
     * substitution on the census state.
     */
    private int attemptSubstitution(StudentGroup group, java.time.LocalDate date,
            WeeklyTemplateBuilder.TemplateCell cell, String origSubj,
            List<WeeklyTemplateBuilder.TemplateCell> orderedCells,
            Timeslot[] daySlots,
            Map<Course, ArrayDeque<Lesson>> unplacedByCourse,
            Map<Course, Set<Timeslot>> availableTimeslotsByCourse,
            Map<Course, Integer> claimBefore,
            boolean quotaAware, boolean parity, int expectedHoursCap,
            DeclaredDayPreBooker.GoldenWeekdayPractice practice,
            Map<String, Integer> pairDayBooked,
            boolean journal, ReserveCensusState census) {
        // LATER cells only: an earlier cell's course is already booked this date,
        // and re-booking it at this cell's slots would put the same
        // (group, subject, teacher) on the day twice, separated (hard H28)
        int from = orderedCells.indexOf(cell) + 1;
        for (int ci = from; ci < orderedCells.size(); ci++) {
            Course altCourse = orderedCells.get(ci).course();
            if (altCourse == null || altCourse.equals(cell.course())) continue;
            ArrayDeque<Lesson> queue = unplacedByCourse.get(altCourse);
            if (queue == null || queue.isEmpty()) continue;
            // the alternative's OWN allowance, computed once and decremented per
            // booking exactly as the main walk does: booking past it would
            // cannibalize the dates that claimed those hours
            int altAllowance = Integer.MAX_VALUE;
            if (quotaAware) {
                int claim = claimBefore.getOrDefault(altCourse, 0);
                altAllowance = Math.max(0, queue.size() - claim);
                if (altAllowance <= 0) continue; // same non-cannibalizing bar
            }
            if (parity && practice != null && altCourse.subject != null) {
                Double share = practice.subjectWeekdayShare(group.name, altCourse.subject,
                        date.getDayOfWeek().getValue());
                if (share != null && share < 0.15d) continue; // course-shaped bar
            }
            Set<Timeslot> legal = availableTimeslotsByCourse.get(altCourse);
            int booked = 0;
            for (int slot = cell.slotIndex(); slot < cell.slotIndex() + cell.length(); slot++) {
                if (altAllowance <= 0) break; // the alternative's own claim wall
                Timeslot ts = slot < daySlots.length ? daySlots[slot] : null;
                if (ts == null) break;
                if (legal != null && !legal.contains(ts)) break;
                Lesson next = queue.peekFirst();
                if (next == null) break;
                if (!canPlaceLesson(next, ts) || exceedsDailyCap(next, ts)) break;
                int nonPseudo = getDailyLessonCount(group.id, date)
                        - getPseudoDailyLessonCount(group.id, date);
                if (expectedHoursCap >= 0 && nonPseudo >= expectedHoursCap) {
                    census.capReached = true; // same accounting the main walk keeps
                    break;
                }
                if (parity && practice != null) {
                    String teacherName = next.getTeacher() != null ? next.getTeacher().name : null;
                    Integer goldenMax = teacherName != null
                            ? practice.maxPairDayHours(teacherName, group.name,
                                    date.getDayOfWeek().getValue())
                            : null;
                    if (goldenMax != null
                            && pairDayBooked.getOrDefault(teacherName, 0) + 1 > goldenMax) {
                        break; // teacher-shaped bar: later slots hit the same wall
                    }
                }
                Lesson placed = queue.pollFirst();
                assignVariables(placed, ts);
                trackAssignment(placed, ts);
                lessonsPlaced++;
                stampPlaced++;
                census.booked++;
                booked++;
                altAllowance--;
                if (parity && placed.getTeacher() != null && placed.getTeacher().name != null) {
                    pairDayBooked.merge(placed.getTeacher().name, 1, Integer::sum);
                }
                if (journal) {
                    DeclaredDayPreBooker.RESERVE_JOURNAL.add(String.format("%s|%s|%d|%s|%s",
                            group.name, date, slot,
                            altCourse.subject != null ? altCourse.subject : "?",
                            altCourse.teacher != null ? altCourse.teacher.name : "?"));
                }
            }
            if (booked > 0) {
                census.substitutions.add(origSubj + " -> "
                        + (altCourse.subject != null ? altCourse.subject : "?"));
                return booked;
            }
        }
        return 0;
    }

    private void reserveProfileFollowingPlacements(Map<Course, ArrayDeque<Lesson>> unplacedByCourse,
            Map<Course, Set<Timeslot>> availableTimeslotsByCourse) {
        // A "monographic" day is at least this many hours above the modal weekday quota.
        // 2 keeps the pass off uniform courses (quota variance 0-1) while catching the
        // G10 Thursdays (modal 4h, max 8h, delta 4).
        final int concentrationThreshold = 2;

        // Deterministic semantic course order - never raw course.id (re-seed reproducibility).
        List<Course> courses = new ArrayList<>(unplacedByCourse.keySet());
        courses.sort(Comparator
                .comparing((Course c) -> c.studentGroup != null ? c.studentGroup.name : "")
                .thenComparing(c -> c.subject == null ? "" : c.subject)
                .thenComparing(c -> c.teacher != null ? c.teacher.name : ""));

        for (Course course : courses) {
            ArrayDeque<Lesson> queue = unplacedByCourse.get(course);
            if (queue == null || queue.isEmpty()) continue;
            StudentGroup group = course.studentGroup;
            if (group == null) continue;
            Set<Timeslot> legal = availableTimeslotsByCourse.get(course);
            if (legal == null || legal.isEmpty()) continue;

            // CHEAP discriminator first: a course whose teacher is available on more than
            // one weekday can spread to an alternative day, so it is NOT a "must-concentrate"
            // course. Computing the weekday set straight off the VRP-legal slots skips the
            // multi-weekday majority BEFORE the per-date slot map and quota frequency below.
            // This single-weekday test is a PROXY for "the subject the school concentrates":
            // the deeper abstraction would be subject-kind-aware quotas (labHours for lab
            // courses via Course.isLab, aulaHours for aula), but that classification lives
            // in the protected DataGenerator (getRoomTags/isLabSubject, private) and is
            // unset in the construction-fixture, so the single-weekday teacher test
            // approximates it. A multi-weekday lab course that truly concentrates would be
            // skipped here and fall through to the uniform stamp - fail-safe, not fail-bad.
            // Canonical case: T01, Thursday-only via VACATION; running multi-weekday
            // courses here too would make every subject compete for the same group-total
            // high-quota days (whichever sorts first would grab them).
            DayOfWeek firstWeekday = null;
            boolean singleWeekday = true;
            for (Timeslot ts : legal) {
                LocalDate d = ts.getDate();
                if (d == null) continue;
                DayOfWeek dow = d.getDayOfWeek();
                if (firstWeekday == null) firstWeekday = dow;
                else if (dow != firstWeekday) { singleWeekday = false; break; }
            }
            if (!singleWeekday) continue;

            // Candidate dates: every date with at least one VRP-legal slot (the VRP
            // already excludes closed/no-session/stage-zero-quota/teacher-unavailable
            // days), each date's slots ascending by slot index for compact bottom-up fill.
            Map<LocalDate, List<Timeslot>> slotsByDate = new TreeMap<>();
            for (Timeslot ts : legal) {
                LocalDate d = ts.getDate();
                if (d == null) continue;
                slotsByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(ts);
            }
            if (slotsByDate.isEmpty()) continue;
            for (List<Timeslot> slots : slotsByDate.values()) {
                slots.sort(Comparator.comparingInt(TimeslotSequence::slotIndexOf));
            }

            // Detect the non-uniform profile. modal = most frequent candidate-day quota;
            // max = the highest. Skip uniform courses (max - modal < threshold) untouched.
            Map<Integer, Integer> quotaFreq = new TreeMap<>();
            int maxQuota = 0;
            for (LocalDate d : slotsByDate.keySet()) {
                int q = expectedHoursCapFor(group, d);
                quotaFreq.merge(q, 1, Integer::sum);
                if (q > maxQuota) maxQuota = q;
            }
            Integer modalResult = WeeklyTemplatePlanner.mostFrequentKey(quotaFreq);
            int modalQuota = modalResult != null ? modalResult : 0;
            if (maxQuota - modalQuota < concentrationThreshold) continue;
            // Exhaust the profile in DESCENDING-quota order (chronological tie-break):
            // fill the school's high-expectation days first, to each day's expected-hours
            // capacity, before any lower-quota day. Every placement re-checks the live
            // hard constraints (canPlaceLesson/exceedsDailyCap mutate as the day fills)
            // and the per-date expected-hours cap, so the pass never over-fills or
            // conflicts - it skips to the next slot/date instead.
            List<LocalDate> rankedDates = new ArrayList<>(slotsByDate.keySet());
            rankedDates.sort(Comparator
                    .comparingInt((LocalDate d) -> expectedHoursCapFor(group, d)).reversed()
                    .thenComparing(d -> d));
            for (LocalDate date : rankedDates) {
                if (queue.isEmpty()) break;
                int expectedCap = expectedHoursCapFor(group, date);
                for (Timeslot ts : slotsByDate.get(date)) {
                    if (queue.isEmpty()) break;
                    if (expectedCap >= 0) {
                        int nonPseudo = getDailyLessonCount(group.id, date) - getPseudoDailyLessonCount(group.id, date);
                        if (nonPseudo >= expectedCap) break; // day at its expected-hours quota
                    }
                    Lesson next = queue.peekFirst();
                    if (!canPlaceLesson(next, ts) || exceedsDailyCap(next, ts)) continue;
                    queue.pollFirst();
                    assignVariables(next, ts);
                    trackAssignment(next, ts);
                    lessonsPlaced++;
                    stampPlaced++;
                }
            }
            // Any remainder stays in `queue`; the chronological stamp below polls the
            // same queue and places it unchanged, exactly like an untouched course.
        }
    }

    /** True if placing {@code lesson} at {@code ts} would exceed H11 (group) or H7 (teacher) daily caps. */
    boolean exceedsDailyCap(Lesson lesson, Timeslot ts) {
        StudentGroup group = lesson.getStudentGroup();
        if (groupDailyCapReached(group, ts.getDate())) {
            return true;
        }
        Teacher teacher = lesson.getTeacher();
        if (teacher != null && !teacher.pseudo) {
            int cap = teacher.getEffectiveMaxDailyHours();
            // >0 guard matches the group check above and WeeklyTemplateBuilder's
            // own teacherDailyCap check: a cap of exactly 0 is treated as
            // unrestricted, not "zero hours allowed" (count>=0 would otherwise
            // reject a teacher's very first hour of every day).
            if (cap > 0 && getTeacherDailyLessonCount(teacher.id, ts.getDate()) >= cap) {
                return true;
            }
        }
        return false;
    }

    /**
     * TASK-385 "stamp fill goal": the school's own per-DATE expected-hours
     * cap for {@code group} on {@code date} (still capped by the calendar,
     * Q16 - G14's sheet gives it days its Thu-Fri calendar doesn't have),
     * or -1 if no fact exists for that (group, date) - meaning "no cap from
     * this goal", the caller falls back to whatever other caps apply.
     * Computed ONCE per (date, group) by the caller (both inputs are
     * invariant for the whole date) and compared against the DYNAMIC
     * {@link #getDailyLessonCount} net of {@link #getPseudoDailyLessonCount}
     * (see the call site), which changes as the stamp loop places lessons -
     * deliberately kept SEPARATE from {@link #exceedsDailyCap} (its own
     * counter at the call site, not folded into {@code stampSkipConflict}):
     * the template's day shapes are built from the MODAL/typical per-weekday
     * target ({@link WeeklyTemplateBuilder.GroupWeeklyShape#dailyTargetHours}),
     * so a specific date can legitimately deviate from it (see {@code
     * WeeklyTemplatePlanner#buildGroupWeeklyShape}'s own javadoc for the same modal-vs-actual
     * distinction already established for the calendar window) - that is
     * an EXPECTED, non-conflict skip category, unlike H1/H2/H3/H7/H11 which
     * the template is built to never violate at all.
     *
     * <p><b>Stage-exclusive, code-review fix:</b> uses {@link
     * SchoolSchedule#getExpectedHoursRow} (aula+lab only), not {@link
     * SchoolSchedule#getExpectedHours} (aula+lab+stage) - Stage hours are
     * only "already spent" capacity when a pinned Stage lesson actually
     * exists for that date; when it doesn't (skipped/off-grid asks,
     * fixtures without Stage), a stage-inclusive cap would let this stamp
     * over-place aula/lab lessons, the exact bug M20's own aula+lab fix
     * ({@link SchoolScheduleConstraintProvider#dailyQuota}) already closed
     * for the constraint side. The call site nets Stage hours out of the
     * comparison count instead, so both sides of the comparison share the
     * same aula+lab-only basis.
     */
    private static int slotSlotsLimit(Timeslot[] daySlots) {
        return daySlots == null ? 0 : daySlots.length;
    }

    /** TASK-532 quota-aware fill tail counter (see the stamp loop). */
    private int quotaFillPlaced = 0;

    /** TASK-533 via-(b) quota-tail moves-placed counter (see the pass method). */
    private int quotaTailMoved = 0;

    /** TASK-533 via (b): hours MOVED (not placed) by the quota-tail moves-placed pass. */
    public int getQuotaTailMoved() {
        return quotaTailMoved;
    }

    private int expectedHoursCapFor(StudentGroup group, LocalDate date) {
        if (group == null) return -1;
        DailyActivityQuota row = SchoolSchedule.getExpectedHoursRow(group.id, date);
        if (row == null) {
            // TASK-408: for a sheet-covered group, a date with no fact row is a
            // no-session (closed) day - cap 0 ("place nothing / target is 0"), NOT
            // -1 "no cap from this goal". Matches the M20 target (dailyQuota -> 0)
            // and the VRP no-session filter, so the stamp/repair never treats a
            // phantom day as under-filled. Uncovered groups keep the -1 fallback
            // (behave as before - genuinely no expected-hours data).
            return SchoolSchedule.isNoSessionDate(group.id, date) ? 0 : -1;
        }
        int expected = row.aulaHours + row.labHours;
        int openSlots = SchoolSchedule.getGroupCalendarOpenSlotCount(group.id, date.getDayOfWeek(), date);
        int cap = Math.min(expected, openSlots);
        // TASK-577 9/09 (census cure): the sheet over-asks the pool-cap groups
        // (G04 840 in-grid vs pool 750), so a construction following the SHEET
        // front-loads and starves the tail: the 9/09 census on the live cycle's
        // POST_CONSTRUCTION found 15 fully empty declared G04 days (all 9
        // declared Apr-May Fridays plus 6 weekdays) that the golden serves at
        // 5h each, plus 10 for G08. Following the RATIFIED TASK-497 per-date
        // targets spreads the placement the same way the score-side M20 family
        // already does.
        if (poolCapFillEnabled()) {
            int poolTarget = SchoolSchedule.getM20PoolCapTarget(group.id, date);
            if (poolTarget >= 0) {
                cap = Math.min(cap, poolTarget);
            }
        }
        return cap;
    }

    /**
     * TASK-406: remaining M20-target capacity on {@code group}'s day. Returns how many
     * more lessons the day is still UNDER its expected-hours target (the
     * {@code dailyQuota} {@code horizontallyCompact} penalizes deficit against),
     * using the SAME stage-exclusive count the stamp's fill-goal uses (all placed
     * minus pseudo Stage, see {@link #expectedHoursCapFor}'s own javadoc). Returns
     * 0 when there is no expected-hours fact for the day OR the day is already at
     * or above target, so callers fall back to the H11-only cap / plain
     * conflict-free slot (unchanged behavior). Shared by the repair tail's day-cap
     * ({@link #findBestLessonForSlot}) and the box-repair's deficit-preferred slot
     * choice ({@link #boxUnplaceableLeftovers}).
     */
    private int expectedDeficit(StudentGroup group, LocalDate date) {
        if (group == null) return 0;
        int expectedCap = expectedHoursCapFor(group, date);
        if (expectedCap < 0) return 0;
        int nonPseudoCount = getDailyLessonCount(group.id, date) - getPseudoDailyLessonCount(group.id, date);
        return Math.max(0, expectedCap - nonPseudoCount);
    }

    /**
     * TASK-381 "L2" stamp-only stats (Step 2), as a single diagnostic
     * snapshot rather than four separate getters - lets {@code
     * WeeklyTemplateConstructionRealSeedTest} measure conflicts/skips
     * introduced by THE STAMP specifically, as distinct from the
     * round-robin/force-place repair tail that runs after it (a separate,
     * pre-existing mechanism with its own known least-conflict tradeoffs -
     * see {@link #boxUnplaceableLeftovers}'s own javadoc).
     */
    public record StampStats(int placed, int skipConflict, int skipIllegalOrClosed, int skipExhausted,
                              int skipExpectedHoursGoal) {
    }

    public StampStats getStampStats() {
        return new StampStats(stampPlaced, stampSkipConflict, stampSkipIllegalOrClosed, stampSkipExhausted,
                stampSkipExpectedHoursGoal);
    }

    /** TASK-390: number of student-day gaps the compaction pass closed. */
    public int getCompactedGaps() {
        return compactedGaps;
    }

    /** TASK-390: teacher+group conflicts the compaction pass ADDED (must be 0 - it only moves into conflict-free slots). */
    public long getCompactionAddedConflicts() {
        return compactionAddedConflicts;
    }

    /** TASK-446 Alt-B: non-pinned lessons this phase left room=null (must equal the non-pinned placed count). */
    public int getRoomNullCount() {
        return roomNullCount;
    }

}
