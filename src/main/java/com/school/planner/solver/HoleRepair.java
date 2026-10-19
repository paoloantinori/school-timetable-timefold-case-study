package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-419: deterministic H17 hole repair - closes the student-day breaks the staged cycle
 * leaves behind, by IMPORTING an hour from another day into the hole (or, failing that,
 * sliding the day's own first lesson up into it). Same shape as {@link BoxRepair}: pure
 * enumeration, improve-or-noop, {@code conflictsAdded} must always be 0.
 *
 * <p><b>Why it exists (val419 staged smoke anatomy, 2026-07-26).</b> The smoke ended at
 * H17 -14 across 9 breaks, and NONE of them was closable by the moves the pipeline already
 * has. {@link StudentDayCompaction} only ever slides the day's LAST lesson down into a hole,
 * and on all 9 breaks that lesson could reach no gap slot legally (its teacher was busy
 * elsewhere in the hole's hours); the local search could not fix them either, because every
 * single-lesson move out of those days trades an H17 for another. The anatomy measured what
 * DOES close them: 5 of the 14 hole slots take a lesson imported from a free (off-template)
 * day, and the remaining 9 take one imported from an on-template day - 14 of 14, with no
 * conflict, no cap breach and no new hole at the origin. That is a whole-hour import across
 * dates, which is not in any existing move selector's repertoire as a single step.
 *
 * <p><b>Three levels, cheapest damage first.</b> For each empty slot of each penalized break:
 * <ol>
 * <li><b>Free import</b> - a same-group unpinned lesson sitting on ANOTHER date, OFF its
 *     course's M32 template cell, whose departure is H17-safe. Costs nothing: it was not on
 *     a template cell to begin with, so M32 does not move.</li>
 * <li><b>On-template import</b> - same as (1) but the mover IS on a template cell, so M32
 *     charges its course's scarcity weight. Priced: the cheapest such lesson (lowest M32
 *     weight) is taken first, so the damage is bounded by MAX = 500 medium.</li>
 * <li><b>Min-slide</b> - the day's own FIRST lesson moves up into the hole ({@link
 *     StudentDayCompaction#slideMinIntoGap}, the symmetric counterpart of the compaction's
 *     max-slide). LAST RESORT (TASK-419 review F4): it imports nothing, so it does not fill
 *     the day, it only re-shapes it - and it can cost M18 on top of the mover's own M32 (see
 *     the de-anchoring note below), which an import never does.</li>
 * </ol>
 * The order is by damage, not by likelihood: both imports are tried on every hole before the
 * slide is considered for it.
 *
 * <p><b>The min-slide can DE-ANCHOR the day, and is allowed to (logged).</b> If the day's
 * first lesson is sitting on the group's first calendar-open slot, moving it up leaves the day
 * starting later - an M18 (day-start) violation of at most {@code 50 x offset} medium. The
 * pass takes that trade knowingly when the slide is the ONLY thing that closes the hole,
 * because the tier hierarchy decides it: H17 is HARD, M18 is MEDIUM, and no amount of medium
 * outranks one hard point. Every such move is logged at INFO with the group and date, so the
 * gate can see whether the day-start KPI moved for this reason.
 *
 * <p><b>Budget.</b> At most {@link #MAX_MOVES_PER_PASS} moves are committed in one pass (a
 * healthy grid arrives here with fewer than ~15 hole slots; the val419 smoke2 had 14). The cap
 * is a safety belt against a pathological grid, never an expected limit - reaching it is logged
 * at WARN and leaves the remaining holes untouched rather than churning the schedule.
 *
 * <p><b>No timeslot local search runs after this pass</b> ({@code
 * ScheduleService#runHoleRepair} places it between the smarcatura and stage B-ROOM, and the
 * room stage moves rooms only). So every trade made here is FINAL: nothing downstream will
 * re-optimize the M20/M22/M12/M32/M18 cost of an import or a slide, which is precisely why the
 * order above is by damage and the whole pass is improve-or-noop on its own hard metric.
 *
 * <p><b>No demotion is involved, and none is needed.</b> The pass runs AFTER the smarcatura
 * ({@code ScheduleService.releasePromotedPinsAndRepublish}), where every promoted pin has
 * already been handed back: an "ex-promoted" lesson is by then an ordinary unpinned lesson
 * that merely happens to sit on its template cell. So the three-level policy the design
 * sketched (free / promoted-needing-demotion / demoted) collapses into the two import
 * levels above, distinguished only by M32 damage. The only immovable lessons here are the
 * AUTHORITATIVE pins.
 *
 * <p><b>Staged cycle only.</b> The ordinary {@link CompactionPhase} is deliberately left
 * untouched. An import is a cross-DATE move, so unlike the compaction's within-day slide it
 * changes M20/M22/M12/M32 as well as H17 - a trade this pass is only entitled to make
 * because the staged cycle's own frozen template is what produced these particular breaks
 * (the ordinary pipeline's local search still has the freedom to resolve them itself, and a
 * deterministic cross-day importer bolted onto its final phase would be spending medium-tier
 * score to buy hard-tier points nobody measured it needs).
 *
 * <p><b>Gates, all shared with {@link BoxRepair}</b> (never a near-copy: the A' class of the
 * TASK-419 discriminator is exactly what a near-copy costs): the target cell must be in the
 * mover's VRP value range, conflict-free (H1/H3), pass {@link
 * BoxRepair#passesPlacementGates} (H14 + H11 + H7 + H17-safe arrival) and - for an import -
 * the mover's DEPARTURE must be H17-safe ({@link BoxRepair#isDepartureSafe}), so closing one
 * hole can never open another. Authoritative pins are never moved. Every committed move
 * therefore strictly reduces the penalized-gap total and adds no hard violation.
 *
 * <p><b>NOT gated: H16b and H-D1</b> (declared, not overlooked). A move here can split a
 * consecutive same-subject same-teacher pair's room (H16b) or place a lesson on the hour a
 * doppio teacher is needed elsewhere (H-D1). H16b is structurally mitigated by WHERE the pass
 * runs: stage B-ROOM re-solves every non-authoritative room right afterwards, so it can
 * re-harmonize the block - unless the mover lands next to an AUTHORITATIVE pin whose room it
 * cannot replicate. H-D1 is not mitigated, and is left ungated because doppio pairs are pinned
 * lab sessions in the 2026/27 seed. Both are gate WATCH-ITEMS (expected H16b = 0 at the gate);
 * if either shows up non-zero, the fix is a gate here, not a re-tuning elsewhere.
 */
public final class HoleRepair {

    /**
     * Safety cap on committed moves per pass (see the class javadoc's budget note): a sane
     * grid reaches this pass with well under 15 hole slots.
     */
    static final int MAX_MOVES_PER_PASS = 60;

    private static final Logger LOG = Logger.getLogger(HoleRepair.class);

    private HoleRepair() {
    }

    /**
     * {@code gapSlotsBefore/After} = penalized (non-pinned-exempt) H17 hole slots, i.e. the
     * hard points H17 charges; the three {@code closedBy*} counters are the per-level tally
     * of the class javadoc, DECLARED IN LEVEL ORDER (free import, on-template import,
     * min-slide) so a positional construction cannot silently swap two same-typed levels;
     * {@code conflictsAdded} must be 0.
     */
    public record Result(int gapSlotsBefore, int gapSlotsAfter, int closedByFreeImport,
                         int closedByTemplateImport, int closedByMinSlide, long conflictsAdded) {

        /** Total holes this pass closed, all levels. */
        public int closed() {
            return closedByFreeImport + closedByTemplateImport + closedByMinSlide;
        }
    }

    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                Consumer<Lesson> nullRoom) {
        if (schedule.getTimeslots() == null) {
            return new Result(0, 0, 0, 0, 0, 0);
        }
        // Code-review 2026-07-26: this pass reads the raw pinned flag for its donor pool and
        // min-slide movers, while penalizedHoleSlots counts breaks on isAuthoritativePin. The
        // two agree ONLY when no promotion is held (pinned == authoritative), which is the
        // designed call site (post-smarcatura). Called mid-promotion, the pass would count
        // holes as closable that its own movers may never touch - fail fast instead.
        if (!SchoolSchedule.getPromotedLessonIdsStatic().isEmpty()) {
            throw new IllegalStateException("HoleRepair.repair called while " +
                    SchoolSchedule.getPromotedLessonIdsStatic().size()
                    + " promoted pin(s) are held - this pass is designed for the post-release grid only");
        }
        // THE day view of this pass, built once and kept live through every commit: the hole
        // enumeration, the min-slide, the budget log and the before/after totals all read it,
        // so there is one answer to "what does this day look like right now", never two.
        Map<String, TreeMap<Integer, Lesson>> dayLessons = buildDayLessons(schedule.getLessons());
        int gapSlotsBefore = countPenalizedGapSlots(dayLessons);
        if (gapSlotsBefore == 0) {
            return new Result(0, 0, 0, 0, 0, 0);
        }
        long conflictsBefore = StudentDayCompaction.countConflicts(schedule);

        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot =
                StudentDayCompaction.buildTimeslotsByDateAndSlot(schedule.getTimeslots());
        // The three gate indexes BoxRepair owns and reads (occupancy, H11/H17 group-day slots,
        // H7 teacher daily counts), kept live alongside the day view above (see commitCrossDayMove /
        // the min-slide branch).
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(schedule.getLessons());
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = BoxRepair.buildGroupDaySlots(schedule.getLessons());
        Map<String, Integer> teacherDaily = BoxRepair.buildTeacherDaily(schedule.getLessons());

        // Import candidates: unpinned, PLACED, real lessons, per group, in stable id order
        // (shared builder, also used by AnchorRepair - TASK-433).
        Map<Long, List<Lesson>> candidatesByGroup = buildCandidatesByGroup(schedule.getLessons());

        // Deterministic day order: group name, then date (a fill in one day occupies a
        // teacher slot that can decide another day's options - the solver demands a
        // reproducible order, same rationale as StudentDayCompaction's own). Precomputed as
        // plain sort keys, before any commit can move a lesson out of a day.
        List<String> dayKeys = new ArrayList<>(dayLessons.keySet());
        Map<String, String> sortKeys = new HashMap<>();
        for (String dayKey : dayKeys) {
            Lesson any = dayLessons.get(dayKey).firstEntry().getValue();
            StudentGroup group = any.getStudentGroup();
            sortKeys.put(dayKey, (group != null && group.name != null ? group.name : "")
                    + '|' + any.timeslot.getDate() + '|' + dayKey);
        }
        dayKeys.sort(Comparator.comparing(sortKeys::get));
        // The same (group, date) identities, captured BEFORE any commit can move a lesson out
        // of its day, so the M20 metric below can price even a day this pass empties (whose
        // day map then holds no lesson to read a group or a date off).
        List<DayRef> dayRefs = buildDayRefs(dayLessons);
        long m20CostBefore = m20DayFillCost(dayRefs, groupDaySlots);

        int free = 0;
        int slid = 0;
        int template = 0;
        dayLoop:
        for (String dayKey : dayKeys) {
            TreeMap<Integer, Lesson> bySlot = dayLessons.get(dayKey);
            if (bySlot == null || bySlot.size() < 2) continue; // emptied by an earlier import, or too short to hole
            // Re-derive the day's penalized holes after every commit: a fill changes the
            // day's shape, and one import can turn a width-2 break into a width-1 one.
            for (int guard = 0; guard < TimeslotSequence.SLOTS_PER_DAY; guard++) {
                if (free + template + slid >= MAX_MOVES_PER_PASS) {
                    LOG.warnf("TASK-419 hole repair: move budget of %d reached - %d hole slot(s) left untouched. "
                                    + "A healthy grid needs well under 15 moves, so this is a signal about the "
                                    + "input, not a tuning knob.",
                            MAX_MOVES_PER_PASS, countPenalizedGapSlots(dayLessons));
                    break dayLoop;
                }
                List<Integer> holes = penalizedHoleSlots(bySlot);
                if (holes.isEmpty()) break;
                int level = 0;
                for (int hole : holes) {
                    level = tryClose(bySlot, hole, tsByDateSlot, byTs, groupDaySlots, teacherDaily,
                            dayLessons, candidatesByGroup, moveTimeslot, nullRoom);
                    if (level > 0) break;
                }
                if (level == 0) break;   // no hole of this day is closable: leave it as it is
                if (level == 1) free++;
                else if (level == 2) template++;
                else slid++;
            }
        }

        int gapSlotsAfter = countPenalizedGapSlots(dayLessons);
        LOG.infof("TASK-419 hole repair: M20 day-fill cost %d -> %d medium over %d (group, date) day(s) "
                        + "(imports are priced on it, see findImport - no local search re-optimizes it after this pass)",
                m20CostBefore, m20DayFillCost(dayRefs, groupDaySlots), dayRefs.size());
        long conflictsAfter = StudentDayCompaction.countConflicts(schedule);
        return new Result(gapSlotsBefore, gapSlotsAfter, free, template, slid,
                conflictsAfter - conflictsBefore);
    }

    /**
     * Try to fill ONE hole slot, cheapest level first (see the class javadoc). Returns the
     * level that closed it (1 free import, 2 on-template import, 3 min-slide) or 0 if none
     * could - in which case nothing was committed.
     */
    private static int tryClose(TreeMap<Integer, Lesson> bySlot, int hole,
                                Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot,
                                Map<Timeslot, List<Lesson>> byTs,
                                Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                Map<String, Integer> teacherDaily,
                                Map<String, TreeMap<Integer, Lesson>> dayLessons,
                                Map<Long, List<Lesson>> candidatesByGroup,
                                BiConsumer<Lesson, Timeslot> moveTimeslot, Consumer<Lesson> nullRoom) {
        Lesson any = bySlot.firstEntry().getValue();
        StudentGroup group = any.getStudentGroup();
        LocalDate date = any.timeslot.getDate();
        Timeslot target = tsByDateSlot.getOrDefault(date, Map.of()).get(hole);
        if (target == null) {
            return 0; // no timeslot row on the grid at all: structurally unfillable
        }
        List<Lesson> pool = candidatesByGroup.getOrDefault(group.id, List.of());

        // ── level 1: import a FREE (off-template) hour from another date ──
        Lesson pick = findImport(pool, target, date, false, byTs, groupDaySlots, teacherDaily, false);
        if (pick != null) {
            commitCrossDayMove(pick, target, byTs, groupDaySlots, teacherDaily, dayLessons, moveTimeslot, nullRoom);
            return 1;
        }
        // ── level 2: import an ON-TEMPLATE hour, cheapest M32 weight first ──
        pick = findImport(pool, target, date, true, byTs, groupDaySlots, teacherDaily, false);
        if (pick != null) {
            commitCrossDayMove(pick, target, byTs, groupDaySlots, teacherDaily, dayLessons, moveTimeslot, nullRoom);
            return 2;
        }
        // ── level 3 (LAST RESORT): slide the day's own first lesson up into the hole ──
        int firstSlot = bySlot.firstKey();
        Lesson minLesson = bySlot.get(firstSlot);
        // De-anchoring guard (TASK-419 review F4): if that first lesson IS the day's anchor
        // (sitting on the group's first calendar-open slot, i.e. M18's own offset is 0 there),
        // the slide starts the day later and M18 charges 50 x offset medium. Committed anyway -
        // no import could close this hole, and a hard H17 point outranks any medium - but never
        // silently. The offset is asked of M18's own rule (TASK-422 A4) instead of re-deriving
        // it; the closed-day sentinel is still tested here, because that rule deliberately
        // collapses a calendar-closed day to offset 0 (M18 does not penalize it, so no slide on
        // such a day can de-anchor anything).
        int[] window = SchoolSchedule.getGroupCalendarWindow(group.id, date.getDayOfWeek(), date);
        boolean deAnchors = window[1] >= 0
                && SchoolScheduleConstraintProvider.m18DayStartOffset(group, date, firstSlot) == 0;
        if (StudentDayCompaction.slideMinIntoGap(bySlot, hole, target, byTs, moveTimeslot, nullRoom)) {
            TreeMap<Integer, Boolean> daySlots = groupDaySlots.get(group.id + "@" + date);
            if (daySlots != null) {
                daySlots.remove(firstSlot);
                daySlots.put(hole, BaseConstraintProvider.isAuthoritativePin(minLesson));
            }
            if (deAnchors) {
                LOG.infof("TASK-419 hole repair: min-slide DE-ANCHORS %s / %s (day started at open slot %d, "
                                + "now at %d) to close the H17 hole at slot %d - no import could close it, and "
                                + "hard H17 outranks medium M18",
                        group.name, date, firstSlot, bySlot.firstKey(), hole);
            }
            return 3;
        }
        return 0;
    }

    /**
     * The cheapest same-group lesson that can legally move from ANOTHER date into
     * {@code target}: {@code onTemplate} selects which half of the pool is considered (false
     * = the free tail, no M32 cost; true = the template cells, charged at their course's
     * scarcity weight). Gates: VRP, conflict-free, {@link BoxRepair#passesPlacementGates} and
     * an H17-safe DEPARTURE.
     *
     * <p><b>Ranking key</b> (TASK-419 follow-up A4): {@code (M32 + M20 SOURCE-DAY cost ASC,
     * pool order = id ASC)}, a total and stable order. The M32 term alone was shape-blind:
     * it is capped at 500 medium, while emptying one more hour out of an already-thin day now
     * costs up to 4,500 on M20's progressive curve ({@link
     * SchoolScheduleConstraintProvider#m20DeficitCost}) - so an unpriced donor could pay 9x
     * its own nominal price in a cost nobody was looking at. Both levels are priced the same
     * way (the free tail simply has a zero M32 term), so the free level no longer returns the
     * first legal candidate but the one whose day can best afford to lend the hour.
     *
     * <p>The LEVEL ORDER is unchanged and stays the outer key: a free import is still tried
     * before any on-template one, whatever their M20 terms. The TARGET day's own M20 gain is
     * deliberately not part of the key either - it is the same hole for every candidate, so it
     * cannot separate them.
     *
     * <p>The pricing is only ever an ORDERING: no candidate is refused for being expensive,
     * exactly as before. A hard H17 point outranks any medium, the same tier argument the
     * min-slide's de-anchoring note makes.
     */
    /**
     * Import donor candidates: unpinned, PLACED, real lessons, grouped by group, each group's
     * pool in stable id order (the tie-breaker {@link #findImport}'s cost key relies on). A
     * boxed lesson is BoxRepair's business (placing one is an H0 gain this pass does not
     * measure, and its "departure" has no meaning), so it never enters a pool. Package-private
     * so {@link AnchorRepair} (TASK-433) builds the same donor pool over the same live grid.
     */
    static Map<Long, List<Lesson>> buildCandidatesByGroup(List<Lesson> lessons) {
        Map<Long, List<Lesson>> candidatesByGroup = new HashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.pinned || lesson.timeslot == null) continue;
            StudentGroup group = lesson.getStudentGroup();
            if (group == null || group.id == null) continue;
            candidatesByGroup.computeIfAbsent(group.id, k -> new ArrayList<>()).add(lesson);
        }
        for (List<Lesson> pool : candidatesByGroup.values()) {
            pool.sort(Comparator.comparing(l -> l.id != null ? l.id : Long.MAX_VALUE));
        }
        return candidatesByGroup;
    }

    static Lesson findImport(List<Lesson> pool, Timeslot target, LocalDate holeDate,
                             boolean onTemplate, Map<Timeslot, List<Lesson>> byTs,
                             Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                             Map<String, Integer> teacherDaily, boolean m18Guard) {
        Lesson best = null;
        int bestCost = Integer.MAX_VALUE;
        for (Lesson lesson : pool) {
            if (lesson.timeslot == null || holeDate.equals(lesson.timeslot.getDate())) continue;
            if (SchoolSchedule.isOnTemplateCell(lesson) != onTemplate) continue;
            // Order is by COST, not by strength: the three gates below are O(1) map reads,
            // while the VRP membership test is a per-slot legality check (TASK-578: O(1)
            // filters on one cell, was a linear scan of the course's value range (up
            // to ~1,368 timeslots). All four are pure, so the selection is unchanged.
            if (!StudentDayCompaction.isSlotConflictFree(lesson, target, byTs)) continue;
            if (!BoxRepair.passesPlacementGates(lesson, target, groupDaySlots, teacherDaily)) continue;
            if (!BoxRepair.isDepartureSafe(lesson, groupDaySlots)) continue;
            // M18 donor-anchor guard (TASK-433): when the import closes a LEADING M18 hole, the
            // donor must not be its own day's first lesson (a pure shell game, same M18 tier).
            // HoleRepair's own calls pass false: an H17 mid-day hard point outranks the donor's
            // M18 medium, so de-anchoring a donor to close a mid-day gap is an allowed trade.
            if (m18Guard && !SurplusRebalance.keepsDayAnchored(lesson, groupDaySlots)) continue;
            // TASK-578: per-slot legality (O(1), stale-cache crash class; see
            // Lesson.isSlotLegalFresh - the Fresh full-range rebuild measured ~220us
            // and this gate fires up to ~10^5 times per pass)
            if (!lesson.isSlotLegalFresh(target)) continue;
            // TASK-576 F7-A: a donor sitting on one of its pair's cells pays the guard's
            // exit surcharge - same ORDERING semantics as every other term here (no
            // candidate is refused), so the hole still closes but the pass first spends
            // the lessons that carry no form. 0 (and the selection byte-identical) when
            // the guard is not armed or the pair builder never published cells.
            int cost = (onTemplate ? StagedPinPolicy.deviationWeightOf(lesson) : 0)
                    + sourceDayRemovalCost(lesson, groupDaySlots)
                    + PairCellGuard.exitSurcharge(lesson);
            if (cost < bestCost) {   // strictly less: the pool's id order breaks every tie
                bestCost = cost;
                best = lesson;
            }
        }
        return best;
    }

    /**
     * The M20 medium that taking {@code lesson} off its CURRENT date would add: 0 from a day
     * above quota (a surplus hour is free to lend), 1000 from a day exactly at quota, up to
     * 4500 from an already-deep one. Delegated whole to {@link
     * SchoolScheduleConstraintProvider#m20MarginalRemovalCost} - the repair never re-derives
     * the quota rules - and read off the live gate map, so a donor already lent from earlier
     * in this pass is priced at its CURRENT depth, not its initial one.
     */
    private static int sourceDayRemovalCost(Lesson lesson,
                                            Map<String, TreeMap<Integer, Boolean>> groupDaySlots) {
        StudentGroup group = lesson.getStudentGroup();
        LocalDate date = lesson.timeslot == null ? null : lesson.timeslot.getDate();
        if (group == null || group.id == null || date == null) return 0;
        TreeMap<Integer, Boolean> daySlots = groupDaySlots.get(group.id + "@" + date);
        return SchoolScheduleConstraintProvider.m20MarginalRemovalCost(group, date,
                BoxRepair.m20Occupancy(daySlots), daySlots == null ? 0 : daySlots.size());
    }

    /**
     * Commit a cross-date move: execute it and keep every live index honest - the occupancy
     * index, the origin/destination group-day slot maps (H11 + every H17 gate), the per-teacher
     * daily counts (H7) and the (group, date) day view this pass enumerates holes from. The
     * origin day SHRINKS, which is why nothing here may be skipped: a stale origin entry would
     * let a later move read a departure as safe against a lesson that has already left.
     *
     * <p>Package-private: {@link SurplusRebalance} commits the same cross-date move over the
     * same five indexes (TASK-422), and a near-copy of index maintenance is exactly the class
     * of drift the shared {@link BoxRepair} gates exist to prevent.
     */
    static void commitCrossDayMove(Lesson lesson, Timeslot target,
                                     Map<Timeslot, List<Lesson>> byTs,
                                     Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                     Map<String, Integer> teacherDaily,
                                     Map<String, TreeMap<Integer, Lesson>> dayLessons,
                                     BiConsumer<Lesson, Timeslot> moveTimeslot, Consumer<Lesson> nullRoom) {
        Timeslot origin = lesson.timeslot;
        LocalDate originDate = origin.getDate();
        LocalDate targetDate = target.getDate();
        int originSlot = TimeslotSequence.slotIndexOf(origin);
        int targetSlot = TimeslotSequence.slotIndexOf(target);
        StudentGroup group = lesson.getStudentGroup();
        Teacher teacher = lesson.getTeacher();

        moveTimeslot.accept(lesson, target);
        nullRoom.accept(lesson);

        List<Lesson> originList = byTs.get(origin);
        if (originList != null) originList.remove(lesson);
        byTs.computeIfAbsent(target, k -> new ArrayList<>()).add(lesson);

        // An emptied origin day is left in both maps as an EMPTY entry rather than removed:
        // the day-processing loop holds a snapshot of the key set, and an empty day is a
        // correct answer to every question asked of it (no hole, no occupancy, no cap).
        String originKey = group.id + "@" + originDate;
        String targetKey = group.id + "@" + targetDate;
        TreeMap<Integer, Boolean> originDay = groupDaySlots.get(originKey);
        if (originDay != null) originDay.remove(originSlot);
        groupDaySlots.computeIfAbsent(targetKey, k -> new TreeMap<>())
                .put(targetSlot, BaseConstraintProvider.isAuthoritativePin(lesson));

        TreeMap<Integer, Lesson> originLessons = dayLessons.get(originKey);
        if (originLessons != null) originLessons.remove(originSlot);
        dayLessons.computeIfAbsent(targetKey, k -> new TreeMap<>()).put(targetSlot, lesson);

        if (teacher != null && !teacher.pseudo) {
            BoxRepair.decrement(teacherDaily, teacher.id + "@" + originDate);
            teacherDaily.merge(teacher.id + "@" + targetDate, 1, Integer::sum);
        }
    }

    /**
     * A (group, date) identity captured before a pass commits anything, so a day the pass
     * EMPTIES can still be priced (its day map then holds no lesson to read a group or a date
     * off). Package-private and shared with {@link SurplusRebalance}, which needs the identical
     * triple for the identical reason (TASK-422): it lives here, next to {@link
     * #buildDayLessons}, because it is an index INTO that day view - {@link BoxRepair} owns the
     * per-day cost primitives ({@code m20Occupancy}, {@code m20DayCost}), this class owns the
     * day view and its population-level roll-up below.
     */
    record DayRef(String key, StudentGroup group, LocalDate date) {
    }

    /**
     * The (group, date) identities of a day view, in the map's own iteration order - each
     * caller sorts them into whatever order its enumeration needs (this pass sorts its own day
     * KEYS separately, {@link SurplusRebalance} sorts by group name then date).
     */
    static List<DayRef> buildDayRefs(Map<String, TreeMap<Integer, Lesson>> dayLessons) {
        List<DayRef> refs = new ArrayList<>();
        for (Map.Entry<String, TreeMap<Integer, Lesson>> entry : dayLessons.entrySet()) {
            Lesson any = entry.getValue().firstEntry().getValue();
            refs.add(new DayRef(entry.getKey(), any.getStudentGroup(), any.timeslot.getDate()));
        }
        return refs;
    }

    /**
     * The M20 + M20.2 day-fill cost of a whole day population, on the score's own curve
     * ({@link SchoolScheduleConstraintProvider#m20DayCost}). Reported before and after this
     * pass because an import is a cross-DATE move that trades day-fill for an H17 hard point,
     * and nothing downstream re-optimizes it - so the live log and any harness reading it can
     * see the size of that trade instead of inferring it from the final score. Shared with
     * {@link SurplusRebalance}, whose whole objective IS this number (TASK-422).
     */
    static long m20DayFillCost(List<DayRef> dayRefs,
                               Map<String, TreeMap<Integer, Boolean>> groupDaySlots) {
        long total = 0;
        for (DayRef ref : dayRefs) {
            TreeMap<Integer, Boolean> daySlots = groupDaySlots.get(ref.key());
            total += SchoolScheduleConstraintProvider.m20DayCost(ref.group(), ref.date(),
                    BoxRepair.m20Occupancy(daySlots), daySlots == null ? 0 : daySlots.size());
        }
        return total;
    }

    /**
     * The empty slots of {@code bySlot}'s PENALIZED breaks, ascending - the exact slots H17
     * charges for, so a break whose bounding lessons are BOTH authoritative pins contributes
     * none (task-350's break-level exemption, {@code verticallyCompact}'s own rule, read
     * through {@link com.school.planner.solver.BaseConstraintProvider#isAuthoritativePin} so
     * the pass and the score can never disagree on what a hole is).
     */
    static List<Integer> penalizedHoleSlots(TreeMap<Integer, Lesson> bySlot) {
        List<Integer> holes = new ArrayList<>();
        Map.Entry<Integer, Lesson> prev = null;
        for (Map.Entry<Integer, Lesson> e : bySlot.entrySet()) {
            if (prev != null && e.getKey() > prev.getKey() + 1
                    && !(BaseConstraintProvider.isAuthoritativePin(prev.getValue())
                            && BaseConstraintProvider.isAuthoritativePin(e.getValue()))) {
                for (int s = prev.getKey() + 1; s < e.getKey(); s++) {
                    holes.add(s);
                }
            }
            prev = e;
        }
        return holes;
    }

    /** Total penalized hole slots over every (group, date) - the H17 hard points on the table. */
    public static int countPenalizedGapSlots(List<Lesson> lessons) {
        return countPenalizedGapSlots(buildDayLessons(lessons));
    }

    /** The same total over an already-built (and possibly live) day view, see {@link #repair}. */
    private static int countPenalizedGapSlots(Map<String, TreeMap<Integer, Lesson>> dayLessons) {
        int total = 0;
        for (TreeMap<Integer, Lesson> day : dayLessons.values()) {
            total += penalizedHoleSlots(day).size();
        }
        return total;
    }

    /**
     * (group, date) -&gt; slotIndex -&gt; lesson, keyed {@code groupId@date} like the gate maps.
     *
     * <p><b>One lesson per (group, date, slot), by declared assumption.</b> On an H3-CLEAN
     * grid - what this pass is handed and what it preserves ({@code conflictsAdded} must be
     * 0) - a group has at most one lesson per slot and the map loses nothing. On an H3-DIRTY
     * grid two stacked same-group lessons COLLAPSE onto one entry (last read wins). The set of
     * OCCUPIED slots, and therefore the hole enumeration itself, is unaffected; what degrades
     * is which lesson each hole's bounding slots resolve to (so a break's pinned-exemption may
     * be read off the stack-mate) and the min-slide's assumption that moving the entry empties
     * its slot. A multimap is deliberately not used: every actual MOVE still goes through the
     * shared occupancy index ({@code byTs}), which keeps the full list per timeslot, so a
     * stack can only make this pass do LESS than it could, never place a conflict.
     *
     * <p>Package-private: {@link SurplusRebalance} enumerates its donors from the same view
     * under the same assumption (TASK-422).
     */
    static Map<String, TreeMap<Integer, Lesson>> buildDayLessons(List<Lesson> lessons) {
        Map<String, TreeMap<Integer, Lesson>> dayLessons = new HashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.timeslot == null || lesson.timeslot.getDate() == null) continue;
            StudentGroup group = lesson.getStudentGroup();
            if (group == null || group.id == null) continue;
            dayLessons.computeIfAbsent(group.id + "@" + lesson.timeslot.getDate(), k -> new TreeMap<>())
                    .put(TimeslotSequence.slotIndexOf(lesson.timeslot), lesson);
        }
        return dayLessons;
    }

}
