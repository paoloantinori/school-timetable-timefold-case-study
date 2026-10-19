package com.school.planner.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;

/**
 * TASK-419 (staged solve): the two SELECTION rules of the staged cycle, as pure
 * functions over an in-memory lesson population - which lessons stage 1 PROMOTES to
 * {@code pinned = true}, and which promoted pins stage 2 DEMOTES again when the
 * frozen template deadlocks the residual. No Timefold, no CDI, no DB: the
 * orchestration ({@code ScheduleService}) does the persistence, the transactions and
 * the statics; this class only decides.
 *
 * <p><b>Promotion</b> shares M32's own on-cell rule ({@link
 * SchoolSchedule#isOnTemplateCell}, which {@code
 * SchoolScheduleConstraintProvider.deviatesFromTemplate} reads too) and its exemptions
 * (pinned, pseudo-teacher, untemplated course, non-canonical slot): a lesson is
 * promotable iff it is already sitting ON one of its course's template cells, so the
 * promotion by construction freezes exactly the part of the grid M32 was paying to keep
 * and never invents a placement. An AUTHORITATIVE pin is never promotable - it is already
 * pinned. Note it is NOT the exact NEGATION of {@code deviatesFromTemplate}: where there
 * is no reference at all (untemplated course, non-canonical slot) M32 exempts the lesson
 * while the promotion refuses it - both correct, see {@code isOnTemplateCell}'s javadoc.
 *
 * <p><b>Demotion</b> is the mirror of {@link BoxRepair}'s eviction (TASK-414's "build
 * from the constrained, evict the free"), applied one level up: for each boxed lesson
 * that the frozen template has DEADLOCKED (zero legal conflict-free cell), the promoted
 * occupants of its legal cells are the blockers, and the LEAST-CONSTRAINED ones go
 * first - ascending M32 deviation weight, which is {@code clamp(100000/|VRP cells of
 * the course|, 20, 500)}, i.e. ascending weight == descending VRP room. A demoted
 * lesson returns to being merely QUASI-pinned (M32 still penalizes it at its own
 * weight if it leaves its cell), never free: that composition is the whole point of
 * the "degrade to quasi-pinned, not to free" design.
 *
 * <p><b>Promotion-blind view required (caller's contract).</b> {@link
 * #selectDemotions} reads {@link Lesson#getAvailableTimeslots()}, whose H3 filter
 * prunes every timeslot occupied by a PINNED lesson of the same group - including the
 * promoted ones. Asking "which promoted pin blocks this boxed lesson" is unanswerable
 * through a value range that has already hidden them, so the caller must publish a
 * promotion-blind {@code pinnedLessonTimeslotsByGroup} static (authoritative pins
 * only) on a lesson population whose VRP caches are still cold, before calling. See
 * {@code ScheduleService#runDemotionStage}.
 */
public final class StagedPinPolicy {

    /** Stage-2 cap: at most this many promoted pins are released per deadlocked boxed lesson. */
    public static final int MAX_DEMOTIONS_PER_BOXED_LESSON = 2;

    private StagedPinPolicy() {
    }

    /**
     * True iff {@code lesson} is an unpinned, placed, real-teacher lesson sitting ON one
     * of its course's weekly-template cells - the stage-1 promotion rule. It shares M32's
     * on-cell test ({@link SchoolSchedule#isOnTemplateCell}) but is NOT the negation of
     * M32's {@code deviatesFromTemplate}: where there is no reference at all (untemplated
     * course, non-canonical slot, {@link SchoolSchedule#hasTemplateReference} false) M32
     * exempts the lesson while this returns false, see the class javadoc and the comment
     * in the body.
     *
     * <p><b>TASK-576 F9 pair promotion</b> (flag {@code planner.construction.pair-promotion
     * .enabled}, DEFAULT OFF, read LIVE here): with the pair-cells builder armed the
     * M32 reference stays COURSE-based, so the promotion above freezes the course form
     * while the PAIR form walks into the local search naked - the E-series measured the
     * consequence (pair-cell match 72,7% at construction, ~30% delivered). When the flag
     * is on, a lesson sitting on one of its PAIR's template cells
     * ({@link PairCellGuard#protects}, PAIR-keyed) is promotable too: the pin is the one
     * instrument that never enters the delta-score market, which is why the penalty (M38)
     * and the repair surcharge (F7-A) could not buy what this does. Everything
     * downstream - the mid-cycle release, the closing release, demotions - already
     * treats promoted pins uniformly, so no other site changes.
     */
    public static boolean isPromotable(Lesson lesson) {
        if (lesson == null || lesson.pinned || lesson.timeslot == null) return false;
        if (lesson.getTeacher() == null || lesson.getTeacher().pseudo) return false;
        // The shared rule (TASK-419 code review): SchoolSchedule owns "is this (course,
        // timeslot) on a template cell" - including the course/timeslot null guards - so M32
        // and the promotion cannot drift on the cell encoding or the lookup. NOT the exact
        // negation of M32's deviatesFromTemplate: an untemplated course / non-canonical slot
        // is "no reference", which M32 EXEMPTS but the promotion must refuse (there is no
        // cell to freeze onto) - and false is exactly that refusal here. See
        // SchoolSchedule#isOnTemplateCell.
        if (SchoolSchedule.isOnTemplateCell(lesson)) return true;
        return pairPromotionEnabled() && PairCellGuard.protects(lesson);
    }

    /** TASK-576 F9 flag: promote pair-form cells too. Opt-in, only the literal "true". */
    public static boolean pairPromotionEnabled() {
        return Boolean.parseBoolean(System.getProperty("planner.construction.pair-promotion.enabled"));
    }

    /** The lessons stage 1 promotes, in stable id order. */
    public static List<Lesson> selectPromotions(List<Lesson> lessons) {
        List<Lesson> out = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (isPromotable(lesson)) out.add(lesson);
        }
        out.sort(Comparator.comparing(l -> l.id != null ? l.id : Long.MAX_VALUE));
        return out;
    }

    /**
     * The promoted pins stage 2 releases: for every DEADLOCKED boxed lesson (unpinned,
     * no timeslot, and not one PLACEABLE cell in its value range), the promoted
     * occupants of its legal cells that actually block it (share its group or its
     * non-pseudo teacher), least-constrained first, at most {@code maxPerBoxed} per boxed
     * lesson. A boxed lesson that still HAS a free cell is left alone - it is a repair/LS
     * coverage miss (class A of the TASK-419 discriminator), not a pinning deadlock, and
     * releasing pins for it would churn the template for nothing.
     *
     * <p><b>"Placeable" is BoxRepair's own gate set, not just conflict-freedom (TASK-419,
     * 2026-07-26).</b> This test used to ask only {@link
     * StudentDayCompaction#isSlotConflictFree}, while the repair that would actually take
     * the cell ({@link BoxRepair}) additionally requires H14, the H11/H7 daily caps and an
     * H17-safe arrival. The gap between the two is a real class of residual, measured on the
     * val419 staged smoke: its one boxed lesson (G12 / T01) had exactly ONE
     * conflict-free cell in 168 VRP cells, and that cell was H17-unsafe (placing there would
     * open a 2-slot break), so BoxRepair refused it - class A' of the discriminator. Stage C
     * read the same lesson as "not deadlocked, it still has a cell", demoted nothing, and the
     * cycle accepted a box it could have cleared: 49 of the lesson's H17-SAFE cells were held
     * by ex-promoted pins, any one of which would have opened one. Asking the same question
     * the repair asks closes that blind spot in both directions (see {@link #takeBlockers}).
     *
     * <p>The result is de-duplicated across boxed lessons (one promoted lesson released
     * once, counting against the budget of every boxed lesson it blocks) and ordered by
     * the same (weight, id) key, so the whole selection is deterministic.
     *
     * @param lessons    the live population (placed + boxed), see the class javadoc's
     *                   promotion-blind contract
     * @param maxPerBoxed release budget per deadlocked boxed lesson
     */
    public static List<Lesson> selectDemotions(List<Lesson> lessons, int maxPerBoxed) {
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(lessons);
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = BoxRepair.buildGroupDaySlots(lessons);
        Map<String, Integer> teacherDaily = BoxRepair.buildTeacherDaily(lessons);
        List<Lesson> boxed = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (!lesson.pinned && lesson.timeslot == null) boxed.add(lesson);
        }
        boxed.sort(Comparator.comparing(l -> l.id != null ? l.id : Long.MAX_VALUE));

        Set<Lesson> chosen = new LinkedHashSet<>();
        for (Lesson lesson : boxed) {
            // TASK-578 audit: this CACHED read is INTENTIONAL and correct here - the
            // analysis runs on a detached fresh population under the deliberately blinded
            // pinned map, so the cache and the live statics agree at first read. Do NOT
            // migrate to Fresh reflexively: a future refactor restoring the map mid-analysis
            // would silently change this filter's meaning.
            List<Timeslot> valueRange = lesson.getAvailableTimeslots();
            boolean deadlocked = true;
            for (Timeslot ts : valueRange) {
                if (StudentDayCompaction.isSlotConflictFree(lesson, ts, byTs)
                        && BoxRepair.passesPlacementGates(lesson, ts, groupDaySlots, teacherDaily)) {
                    deadlocked = false;
                    break;
                }
            }
            if (deadlocked) {
                takeBlockers(lesson, valueRange, byTs, groupDaySlots, teacherDaily, maxPerBoxed, chosen);
            }
        }
        List<Lesson> out = new ArrayList<>(chosen);
        out.sort(demotionOrder());
        return out;
    }

    /** Least-constrained-first: ascending M32 weight (= descending VRP room), id as tiebreak. */
    static Comparator<Lesson> demotionOrder() {
        return Comparator.comparingInt(StagedPinPolicy::deviationWeightOf)
                .thenComparing(l -> l.id != null ? l.id : Long.MAX_VALUE);
    }

    /** The lesson's course M32 deviation weight (0 when its course is untemplated). */
    public static int deviationWeightOf(Lesson lesson) {
        return lesson.course != null && lesson.course.id != null
                ? SchoolSchedule.getTemplateDeviationWeight(lesson.course.id) : 0;
    }

    /**
     * Add up to {@code maxPerBoxed} promoted blockers of {@code lesson} to {@code chosen},
     * cheapest (least-constrained) first. A blocker already chosen for an earlier boxed
     * lesson still counts against this one's budget: its cell is being freed either way.
     *
     * <p>Only cells that pass BoxRepair's gates are mined for blockers (TASK-419): a cell
     * whose H17 arrival refuses the boxed lesson stays refused once its blocker is unpinned,
     * so releasing that blocker would churn the template without ever letting the lesson land
     * - the same gate alignment {@link #selectDemotions}'s deadlock test applies, on the other
     * side of the question.
     *
     * <p><b>The gate is the SWAP-AWARE one</b> ({@link
     * BoxRepair#passesSwapAwarePlacementGates}, TASK-419 review F2): the direct-placement form
     * evaluates H11/H7 with the blocker STILL on the day, so on a day already at
     * {@code maxHoursPerDay} - the very shape a frozen template produces - every cell reads as
     * cap-refused and no blocker is ever mined. The blocker is precisely the hour that is
     * about to be freed, so its own unit of the cap must not be charged to the arriving
     * lesson.
     */
    private static void takeBlockers(Lesson lesson, List<Timeslot> valueRange,
                                     Map<Timeslot, List<Lesson>> byTs,
                                     Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                     Map<String, Integer> teacherDaily, int maxPerBoxed,
                                     Set<Lesson> chosen) {
        List<Lesson> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Timeslot ts : valueRange) {
            List<Lesson> here = byTs.get(ts);
            if (here == null) continue;
            for (Lesson occupant : here) {
                if (occupant == lesson || !isPromotedPin(occupant)) continue;
                if (!StudentDayCompaction.conflicts(lesson, occupant)) continue;
                // Gate per CANDIDATE, not per cell: which cap the swap frees depends on
                // whether this occupant shares the boxed lesson's group or teacher.
                if (!BoxRepair.passesSwapAwarePlacementGates(lesson, ts, occupant, groupDaySlots, teacherDaily)) {
                    continue;
                }
                if (occupant.id == null || seen.add(occupant.id)) candidates.add(occupant);
            }
        }
        candidates.sort(demotionOrder());
        int taken = 0;
        for (Lesson candidate : candidates) {
            if (taken >= maxPerBoxed) break;
            chosen.add(candidate);
            taken++;
        }
    }

    /** A pin this cycle created itself (persistent marker AND registry agree by construction). */
    private static boolean isPromotedPin(Lesson lesson) {
        return lesson.pinned && lesson.promotedPin;
    }
}
