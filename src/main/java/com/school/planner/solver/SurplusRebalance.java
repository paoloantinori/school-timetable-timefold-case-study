package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-422: deterministic surplus -&gt; deficit rebalance - moves ONE hour at a time from a
 * (group, date) that sits ABOVE the school's own expected-hours plan to a day of the SAME
 * group that sits BELOW it. Same family as {@link HoleRepair} and {@link BoxRepair}: pure
 * enumeration over the live grid, improve-or-noop, {@code conflictsAdded} must always be 0,
 * every gate borrowed from {@link BoxRepair} rather than re-derived.
 *
 * <p><b>Why it exists (train-run1 decomposition, 2026-07-26).</b> Of the 1,420 in-plan
 * deficit hours, 164 have a MATCHING surplus on 126 above-plan days of the same class - the
 * only part of the full-day gap that is a pure redistribution and needs no extra capacity.
 * That ~164-hour figure is the measured ask this whole pass is sized against (it is quoted
 * once, here). At the time it was measured, M20 punished the deficit while NOTHING punished
 * the surplus, so the local search parked hours beyond the plan for free and the score never
 * asked for them back. TASK-423's M33 now charges 100 medium per hour above the day's quota,
 * so the surplus is no longer free in the score - but this pass still exists, because a light
 * medium only discourages the surplus while this pass actually moves it onto the deficit days.
 *
 * <p><b>The pricers below deliberately do NOT carry the M33 term yet.</b> The
 * {@code m20DayCost} family ({@link SchoolScheduleConstraintProvider#m20MarginalRemovalCost},
 * {@link SchoolScheduleConstraintProvider#m20MarginalFillGain}) prices the M20 + M20.2 curve
 * only, so a day above quota still reads as costing 0 here. The direction is conservative and
 * that is why it is acceptable as an interim: adding M33 would make lending from an above-plan
 * day strictly MORE attractive and filling a below-plan day no less so, so every ranking this
 * pass produces stays monotone and every committed move stays score-improving - the deltas are
 * merely UNDERSTATED by 100 per surplus hour. Folding the M33 term into the pricers is a
 * follow-up gated on the A/B benchmark (see TASK-423's notes), not a correctness fix.
 *
 * <p><b>Every move is M20-net-positive by construction.</b> A DONOR day is one whose
 * {@link SchoolScheduleConstraintProvider#m20MarginalRemovalCost} is exactly 0 (occupancy
 * strictly above the day's quota - lending an hour is free); a TARGET day is one whose
 * {@link SchoolScheduleConstraintProvider#m20MarginalFillGain} is strictly positive. The two
 * sets are disjoint by construction, so a committed move always strictly lowers the M20 +
 * M20.2 total by the target's marginal gain (at least 1000 medium) - which is also why the
 * loop terminates: a strictly decreasing bounded integer cannot cycle.
 *
 * <p><b>Deliberately NOT {@code removalCost <= 0}.</b> A day holding exactly ONE countable
 * hour next to some authoritative pins prices its removal NEGATIVE: M20's own
 * {@code totalHours > 0} guard makes the day free once its last countable hour leaves, while
 * M20.2 cannot see it as empty (the pins are still there). Treating that as a donor would pay
 * the pass to EVACUATE thin days, the exact inversion M20.2 (TASK-409) was added to close.
 * Only genuine above-quota surplus is lent.
 *
 * <p><b>Targets are days that already have lessons.</b> An empty expected day is M20's most
 * valuable target on the marginal curve (M20.2 prices it at full depth), but opening one
 * changes a group's calendar SPAN, which is M22/M12 territory this pass does not price - so
 * the target pool is the existing day view and the pass can never OPEN a day. A day emptied by
 * an earlier move of this same pass is likewise never re-targeted. It CAN, on the other hand,
 * fully drain a day - but only one whose quota is 0 (a no-session or stage-only date, or
 * G14), where every hour is surplus by definition and lending them all is verified benign:
 * M20.2 charges nothing at quota 0, and M22 counts days USED against
 * {@code ceil(hours / quota)}, so removing the last hour of a day can only lower its penalty.
 *
 * <p><b>Gates, all shared with {@link BoxRepair}</b> (never a near-copy, the A' lesson of
 * TASK-419): conflict-free arrival (H1/H3, {@link StudentDayCompaction#isSlotConflictFree}),
 * {@link BoxRepair#passesPlacementGates} (H14 + H11 + H7 + H17-safe arrival), an H17-safe
 * DEPARTURE ({@link BoxRepair#isDepartureSafe}) so an hour lent can never open a hole behind
 * it, and the mover's own VRP value range LAST in the chain. That ordering is a cost ordering:
 * the VRP membership test is by far the dearest gate (a linear scan of the course's value
 * range, up to ~1,368 timeslots), while the others are O(1) map reads or a small day-map copy
 * (the H17/M15 helpers clone the day's slot map to price a simulated placement). All of them
 * are pure, so ordering by cost cannot change the selection - the E2 precedent.
 * Authoritative pins and pseudo-teacher (Stage) lessons are never moved. The room is nulled
 * on every move, like every mover in this family, because stage B-ROOM reassigns it.
 *
 * <p><b>M18 anchor guard, against the real constraint</b> ({@link
 * SchoolScheduleConstraintProvider#dayShouldStartFromFirstOpenSlot}). M18 penalizes
 * {@code 50 x (day's MIN occupied slot - the group's first calendar-open slot)} per (group,
 * date), over ALL lessons (it is deliberately not pinned-exempt). Only a DEPARTURE can raise
 * that minimum, and only when the mover IS the day's first lesson, so the guard is exactly
 * "recompute M18's own offset with the mover removed and refuse an increase" ({@link
 * #keepsDayAnchored}, asking {@link SchoolScheduleConstraintProvider#m18DayStartOffset}, the
 * constraint's own rule) - the same before/after shape {@link BoxRepair#isH17Safe} uses, not a
 * heuristic. An ARRIVAL can only lower a day's minimum slot, so it needs no guard.
 *
 * <p>It is deliberately a VETO rather than a medium-tier netting, and that is the pass's one
 * knowingly conservative choice. Measured on the val419 smoke2 snapshot (2026-07-26): 35 hours
 * moved with the veto, 66 without it (M20 saved 47,500 vs 96,500 medium, at the price of ~31
 * group-days starting one slot later). Netting instead of vetoing would look free on paper (an
 * M18 offset of 1 costs 50 medium against an M20 hour worth at least 1000), and that is
 * precisely the trap: M18's weight was deliberately retuned DOWN to 50 (TASK-405, from 250)
 * because at 250 it fought M20/M22 and collapsed recurrence - it is a nudge calibrated against
 * the day-start KPI, not an exchange rate, so arbitraging M20 against it would spend a number
 * that was never built to be spent. Nothing re-anchors afterwards either
 * ({@link StudentDayCompaction#anchorRunLeft} runs before the smarcatura, not after this pass),
 * so a day de-anchored here stays de-anchored. The decision rule for a future relaxation is
 * therefore a KPI one, not a score one: judge it on the day-start percentage (golden 99.86%,
 * gate ~97%), and if that holds, relaxing is one line here plus flipping the named test
 * {@code SurplusRebalanceTest.refusesToDeAnchorTheDonorDay}.
 *
 * <p><b>M15 split guard, against the real constraint</b> ({@link
 * SchoolScheduleConstraintProvider#sameDaySameSubjectShouldBeConsecutive}). M15 groups
 * NON-LAB lessons by (group, subject, date) and charges {@code 20 x gap-minutes} for every
 * break between consecutive sequences, exempting a break whose two bounding lessons are both
 * AUTHORITATIVE pins - structurally the same "penalized break width" function H17 applies to
 * the whole day, evaluated on the (group, subject) SLICE of it. So both halves of a move are
 * gated by feeding that slice to the very same helpers: {@link BoxRepair#isH17SafeDeparture}
 * at the donor (never break a same-subject block apart, and never widen an existing split by
 * taking an hour out of the middle of one) and {@link BoxRepair#isH17Safe} at the target
 * (never land a same-day same-subject hour detached from the block already there, and never
 * drop an unpinned hour inside a both-pins-exempt break, which would flip it to penalized).
 * The unit is slot width rather than the constraint's gap-MINUTES, which is exact and not an
 * approximation here: every slot is one hour and {@code calculateBreakGapMinutes} subtracts
 * the 14:00-14:30 lunch, so a break's minutes are exactly {@code 60 x} its slot width. Lab
 * lessons skip the guard entirely, mirroring M15's own {@code !isLab()} filter.
 *
 * <p><b>Ranking, total and stable.</b> Targets: {@code (m20MarginalFillGain DESC, date ASC)}
 * - the deepest hole first, since the curve is progressive ({@link
 * SchoolScheduleConstraintProvider#m20DeficitCost}) and one hour is worth up to 4.5x more to
 * a deep day than to a day one hour short. Movers, for the chosen target day: {@code (M32
 * delta ASC, donor date ASC, lesson id ASC, target slot ASC)}. The M32 term is the EXACT
 * delta of the template-deviation constraint, not merely the course weight - moving an hour
 * onto one of its course's own template cells is free (and moving one that already sits off
 * template is free too), while taking an hour OFF its template cell costs that course's
 * scarcity weight. So a mover that keeps (or joins) the template is always preferred, and a
 * new deviation is only ever created when nothing cheaper can fill the hole.
 *
 * <p><b>Budget.</b> {@link #MAX_MOVES_PER_PASS} committed moves per pass. The measured ask
 * (see "Why it exists" above) is a small fraction of it and the M20 total strictly decreases
 * per move, so hitting the cap is a signal about the input, not a tuning knob - it is logged
 * at WARN and the remaining surplus is left alone rather than churned.
 *
 * <p><b>Staged cycle only, and nothing re-optimizes it afterwards.</b> {@code
 * ScheduleService#runSurplusRebalance} runs it immediately after {@code runHoleRepair} (holes
 * first, then fill) between the smarcatura and stage B-ROOM, where only room variables still
 * move. Every trade is therefore FINAL, which is why the pass refuses to trade any medium it
 * cannot see: it improves M20, is neutral-or-better on M15/M18 by the guards above, prices
 * M32 exactly, and leaves M22/M12 untouched by never opening a day.
 *
 * <p><b>NOT gated: H16b, H-D1 and H4.3</b> (declared, not overlooked). H16b and
 * H-D1 are identical to {@link HoleRepair}'s own note, for the identical reason: B-ROOM
 * re-solves every non-authoritative room right afterwards, and doppio pairs are pinned lab
 * sessions in the 2026/27 seed. (H11-T28 used to be listed here as an unconfirmed
 * watch-item; TASK-440 removed the constraint outright: its hardcoded id 14 pointed at
 * the wrong teacher on the current seed and H7's cap binds first.) The live
 * not-gated set is therefore THREE: H16b, H-D1, H4.3. H4.3 (professionalizzanti constant
 * single-day during stage) is structurally inert while {@code ProfessionalizzantiDay} is
 * seeded with zero rows, the same latent note {@link BoxRepair}'s javadoc carries. All
 * three stay gate watch-items.
 */
public final class SurplusRebalance {

    /** Safety cap on committed moves per pass - see the class javadoc's budget note. */
    private static final int MAX_MOVES_PER_PASS = 500;

    private static final Logger LOG = Logger.getLogger(SurplusRebalance.class);

    private SurplusRebalance() {
    }

    /**
     * {@code movesApplied} = hours moved surplus -&gt; deficit; {@code before}/{@code after} =
     * the day-fill readout of every (group, date) this pass can see, taken on the score's own
     * curve before and after, so the size of the trade is measured rather than inferred;
     * {@code conflictsAdded} must be 0. Declared in EXECUTION order (what the pass did, then
     * what the grid looked like on each side of it, then the invariant).
     */
    public record Result(int movesApplied, DayFill before, DayFill after, long conflictsAdded) {

        /** The pass had nothing to look at (no timeslots, or no placed lesson at all). */
        private static final Result NOOP = new Result(0, DayFill.EMPTY, DayFill.EMPTY, 0);

        public static Result noop() {
            return NOOP;
        }

        /** Medium saved on the M20 axis by this pass (never negative, see the class javadoc). */
        public long m20Saved() {
            return before.m20Cost() - after.m20Cost();
        }
    }

    /**
     * Day-fill readout of a lesson population: the M20 + M20.2 cost, and the two day
     * populations behind it.
     *
     * @param deficitDays days whose {@link SchoolScheduleConstraintProvider#m20MarginalFillGain}
     *                    is strictly positive - i.e. the days this pass would target. An EMPTY
     *                    day is excluded by design even when the plan expects hours on it: the
     *                    pass never opens a day (see the class javadoc), so counting days it
     *                    cannot act on would misreport the ask
     * @param surplusDays days ABOVE their quota, i.e. the days this pass may lend from, counted
     *                    only where the quota is genuinely positive: a quota-0 day (no-session,
     *                    stage-only, G14) is a legal donor but M20 never asked anything of it,
     *                    so counting it as "surplus" would inflate the number an operator reads
     */
    public record DayFill(long m20Cost, int deficitDays, int surplusDays) {

        static final DayFill EMPTY = new DayFill(0, 0, 0);
    }

    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                Consumer<Lesson> nullRoom) {
        if (schedule.getTimeslots() == null) {
            return Result.noop();
        }
        // Same entry contract as HoleRepair: this pass reads the raw pinned flag for its donor
        // pool while every metric and gate around it reads isAuthoritativePin. The two agree
        // ONLY when no promotion is held, which is the designed call site (post-smarcatura) -
        // fail fast instead of silently refusing to move lessons it counts as movable.
        if (!SchoolSchedule.getPromotedLessonIdsStatic().isEmpty()) {
            throw new IllegalStateException("SurplusRebalance.repair called while "
                    + SchoolSchedule.getPromotedLessonIdsStatic().size()
                    + " promoted pin(s) are held - this pass is designed for the post-release grid only");
        }
        // THE day view of this pass, built once and kept live through every commit (shared with
        // HoleRepair, same one-lesson-per-(group, date, slot) contract): donor enumeration, the
        // M15 subject slices and the before/after metrics all read it.
        Map<String, TreeMap<Integer, Lesson>> dayLessons = HoleRepair.buildDayLessons(schedule.getLessons());
        if (dayLessons.isEmpty()) {
            return Result.noop();
        }
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(schedule.getLessons());
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = BoxRepair.buildGroupDaySlots(schedule.getLessons());
        Map<String, Integer> teacherDaily = BoxRepair.buildTeacherDaily(schedule.getLessons());
        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot =
                StudentDayCompaction.buildTimeslotsByDateAndSlot(schedule.getTimeslots());
        Grid grid = new Grid(dayLessons, byTs, groupDaySlots, teacherDaily, tsByDateSlot);

        // (group, date) identities captured BEFORE any commit, so a day this pass empties can
        // still be priced (its day map then holds no lesson to read a group or a date off).
        // Groups in (name, id) order, each group's days chronological: a move in one group
        // occupies a teacher slot that can decide another group's options, so the order must be
        // reproducible (the same rationale StudentDayCompaction/HoleRepair state).
        List<HoleRepair.DayRef> days = HoleRepair.buildDayRefs(dayLessons);
        days.sort(DAY_ORDER);
        DayFill before = measure(days, groupDaySlots);
        long conflictsBefore = StudentDayCompaction.countConflicts(schedule);

        Map<Long, List<HoleRepair.DayRef>> daysByGroup = new LinkedHashMap<>();
        for (HoleRepair.DayRef day : days) {
            daysByGroup.computeIfAbsent(day.group().id, k -> new ArrayList<>()).add(day);
        }

        int moves = 0;
        boolean budgetReached = false;
        for (List<HoleRepair.DayRef> groupDays : daysByGroup.values()) {
            while (moves < MAX_MOVES_PER_PASS && rebalanceOnce(groupDays, grid, moveTimeslot, nullRoom)) {
                moves++;
            }
            if (moves >= MAX_MOVES_PER_PASS) {
                budgetReached = true;
                break;
            }
        }

        DayFill after = measure(days, groupDaySlots);
        if (budgetReached) {
            LOG.warnf("TASK-422 surplus rebalance: move budget of %d reached - %d deficit day(s) and %d surplus "
                            + "day(s) left untouched. The measured ask is ~164 hours for a whole year, so this is a "
                            + "signal about the input, not a tuning knob.",
                    MAX_MOVES_PER_PASS, after.deficitDays(), after.surplusDays());
        }
        LOG.infof("TASK-422 surplus rebalance: %d hour(s) moved above-quota -> deficit, M20 day-fill cost %d -> %d "
                        + "medium over %d (group, date) day(s), deficit days %d -> %d, surplus days %d -> %d",
                moves, before.m20Cost(), after.m20Cost(), days.size(),
                before.deficitDays(), after.deficitDays(), before.surplusDays(), after.surplusDays());
        long conflictsAfter = StudentDayCompaction.countConflicts(schedule);
        return new Result(moves, before, after, conflictsAfter - conflictsBefore);
    }

    /** Group name, then group id, then date - the pass's whole enumeration order. */
    private static final Comparator<HoleRepair.DayRef> DAY_ORDER = Comparator
            .comparing((HoleRepair.DayRef d) -> d.group().name != null ? d.group().name : "")
            .thenComparing(d -> d.group().id)
            .thenComparing(HoleRepair.DayRef::date);

    /**
     * Commit ONE move for this group, or return false when none is legal. The group's donor
     * and target day sets are re-derived from the live maps on every call: a commit changes
     * both days' occupancy, and one hour is often all a day is short of.
     *
     * <p>The DONOR-side gates are applied here, once per call, rather than inside {@link
     * #findMove}: they ask only about the mover and its own day, so re-testing them for every
     * candidate target day was pure repetition (nothing commits during the search below).
     */
    private static boolean rebalanceOnce(List<HoleRepair.DayRef> groupDays, Grid grid,
                                         BiConsumer<Lesson, Timeslot> moveTimeslot, Consumer<Lesson> nullRoom) {
        List<HoleRepair.DayRef> targets = new ArrayList<>();
        List<Donor> donors = new ArrayList<>();
        Map<String, Integer> gainByDay = new HashMap<>();
        for (HoleRepair.DayRef day : groupDays) {
            TreeMap<Integer, Boolean> slots = grid.groupDaySlots().get(day.key());
            // The two sets are disjoint (below quota vs above it), so the else-if is exhaustive:
            // a day exactly at quota is neither, and an emptied day is neither (fillGain reads
            // it as the "opening a day" case, which this pass never does - see the javadoc).
            int gain = fillGain(day, slots);
            if (gain > 0) {
                targets.add(day);
                gainByDay.put(day.key(), gain);
            } else if (isSurplusDay(day, slots)) {
                collectDonors(day, grid, donors);
            }
        }
        if (targets.isEmpty() || donors.isEmpty()) {
            return false;
        }
        targets.sort(Comparator.comparingInt((HoleRepair.DayRef d) -> gainByDay.get(d.key())).reversed()
                .thenComparing(HoleRepair.DayRef::date));
        for (HoleRepair.DayRef target : targets) {
            Move move = findMove(target, donors, grid);
            if (move != null) {
                HoleRepair.commitCrossDayMove(move.lesson(), move.target(), grid.byTs(), grid.groupDaySlots(),
                        grid.teacherDaily(), grid.dayLessons(), moveTimeslot, nullRoom);
                return true;
            }
        }
        return false;
    }

    /**
     * The hours a surplus day may lend, in slot order: every mover that passes all four
     * DONOR-side gates, carrying the two per-lesson M32 terms {@link #findMove} would
     * otherwise recompute for each candidate cell.
     */
    private static void collectDonors(HoleRepair.DayRef day, Grid grid, List<Donor> donors) {
        TreeMap<Integer, Lesson> donorLessons = grid.dayLessons().get(day.key());
        if (donorLessons == null || donorLessons.isEmpty()) {
            return;
        }
        for (Lesson lesson : donorLessons.values()) {
            if (!isMovable(lesson)) continue;
            if (!BoxRepair.isDepartureSafe(lesson, grid.groupDaySlots())) continue;      // H17 departure
            if (!keepsDayAnchored(lesson, grid.groupDaySlots())) continue;               // M18 anchor
            if (!keepsSubjectBlockWhole(lesson, donorLessons)) continue;                 // M15 departure
            int weight = StagedPinPolicy.deviationWeightOf(lesson);
            donors.add(new Donor(lesson, weight, m32CostAt(lesson.course, lesson.timeslot, weight)));
        }
    }

    /** A gated mover with its hoisted M32 terms: the course's scarcity weight and its CURRENT M32 cost. */
    private record Donor(Lesson lesson, int weight, int currentM32Cost) {
    }

    /**
     * The best legal hour to move into {@code target}'s day, or null. Enumerates every gated
     * mover ({@link #collectDonors}) against every free slot of the target day, applying the
     * arrival gates of the class javadoc, and keeps the minimum by {@link #MOVE_ORDER}.
     */
    private static Move findMove(HoleRepair.DayRef target, List<Donor> donors, Grid grid) {
        Map<Integer, Timeslot> slotTs = grid.tsByDateSlot().getOrDefault(target.date(), Map.of());
        if (slotTs.isEmpty()) {
            return null;   // no grid cell at all on that date
        }
        // The day view is built once per pass and never loses a key (a commit empties a day's
        // map, it does not remove it), so this is the invariant, not a fallback.
        TreeMap<Integer, Lesson> targetDay = grid.dayLessons().get(target.key());
        if (targetDay == null) {
            return null;
        }
        // Slot BOUNDS only, never an acceptance: the group's calendar window is the same range
        // H20's VRP filter enforces (GroupCalendarService.isOpen tests exactly this interval),
        // so slots outside it can only ever be refused by the VRP gate below - skipping them
        // saves the scan without touching the selection. The VRP stays authoritative.
        int[] window = SchoolSchedule.getGroupCalendarWindow(
                target.group().id, target.date().getDayOfWeek(), target.date());
        if (window[1] < 0) {
            return null;   // closed-day sentinel: the group has no open slot at all that date
        }
        int lastSlot = Math.min(window[2], TimeslotSequence.SLOTS_PER_DAY - 1);
        // One (group, subject, date) slice per SUBJECT of this target day, built on first use:
        // the M15 arrival gate reads the same slice for every mover of that subject.
        Map<String, TreeMap<Integer, Boolean>> sliceBySubject = new HashMap<>();
        Move best = null;
        for (Donor donor : donors) {
            Lesson lesson = donor.lesson();
            Course course = lesson.course;
            // Lab lessons are outside M15 entirely, which a null slice denotes (see joinsSubjectBlock).
            TreeMap<Integer, Boolean> slice = lesson.isLab() ? null
                    : sliceBySubject.computeIfAbsent(lesson.getSubject(), s -> subjectSlice(s, targetDay));
            for (int slot = window[1]; slot <= lastSlot; slot++) {
                Timeslot ts = slotTs.get(slot);
                if (ts == null) continue;
                Move candidate = new Move(lesson, ts, m32CostAt(course, ts, donor.weight()) - donor.currentM32Cost());
                // Rank BEFORE any gate: they are all pure, so a candidate that cannot win needs
                // no proof of legality (and the dearest gate of all, the VRP scan, stays last).
                if (best != null && MOVE_ORDER.compare(candidate, best) >= 0) continue;
                if (targetDay.containsKey(slot)) continue;                                   // the group is already there
                if (!StudentDayCompaction.isSlotConflictFree(lesson, ts, grid.byTs())) continue;   // H1/H3
                if (!BoxRepair.passesPlacementGates(lesson, ts, grid.groupDaySlots(), grid.teacherDaily())) {
                    continue;                                                                // H14/H11/H7/H17 arrival
                }
                if (!joinsSubjectBlock(slice, slot)) continue;                                // M15 arrival
                if (!lesson.isSlotLegalFresh(ts)) continue;              // VRP, last (TASK-578: per-slot, stale-cache class)
                best = candidate;
            }
        }
        return best;
    }

    /** A validated {@code (mover, target cell)} move with its ranking term, see {@link #MOVE_ORDER}. */
    private record Move(Lesson lesson, Timeslot target, int m32Delta) {
    }

    /** {@code (M32 delta ASC, donor date ASC, lesson id ASC, target slot ASC)} - total and stable. */
    private static final Comparator<Move> MOVE_ORDER = Comparator.comparingInt(Move::m32Delta)
            .thenComparing(m -> m.lesson().timeslot.getDate())
            .thenComparing(m -> m.lesson().id != null ? m.lesson().id : Long.MAX_VALUE)
            .thenComparingInt(m -> TimeslotSequence.slotIndexOf(m.target()));

    /**
     * The EXACT M32 medium this move would add: its course's scarcity weight when the hour
     * leaves a template cell for a non-template one, minus the same weight when it does the
     * opposite (so joining the template is a NEGATIVE cost, always preferred), and 0 whenever
     * neither end is a deviation or the course is untemplated. {@link #findMove} evaluates the
     * same two terms with the current-side one hoisted per mover; both go through {@link
     * #m32CostAt}, so the ranking and this definition cannot drift.
     */
    static int m32Delta(Lesson lesson, Timeslot target) {
        int weight = StagedPinPolicy.deviationWeightOf(lesson);
        return m32CostAt(lesson.course, target, weight) - m32CostAt(lesson.course, lesson.timeslot, weight);
    }

    /**
     * What M32 charges a lesson of {@code course} sitting at {@code ts}: {@code weight} only
     * where the constraint itself charges it, i.e. a template REFERENCE exists ({@link
     * SchoolSchedule#hasTemplateReference}) and the cell is not one of the course's own ({@link
     * SchoolSchedule#isOnTemplateCell}) - the exact pair {@code deviatesFromTemplate} tests.
     *
     * <p>The reference test is not decoration: on a timeslot with no canonical slot index M32
     * charges NOTHING (there is nothing to deviate from), so pricing it as a deviation would
     * make moving that hour look like a saving it is not. Unreachable on the seeded grid, where
     * every placed lesson sits on a canonical slot - fixed for latent-divergence hygiene, since
     * this is the one place the repair re-states the constraint's own cost (TASK-422 D2).
     */
    private static int m32CostAt(Course course, Timeslot ts, int weight) {
        if (weight == 0 || course == null || course.id == null) {
            return 0;   // untemplated course: M32 exempts it wherever it goes
        }
        return SchoolSchedule.hasTemplateReference(course.id, ts)
                && !SchoolSchedule.isOnTemplateCell(course.id, ts) ? weight : 0;
    }

    /** An hour this pass may lend: unpinned (= non-authoritative here), placed, not a Stage stub. */
    static boolean isMovable(Lesson lesson) {
        if (lesson.pinned || lesson.timeslot == null) {
            return false;
        }
        Teacher teacher = lesson.getTeacher();
        return teacher == null || !teacher.pseudo;
    }

    /**
     * M18 guard (see the class javadoc): true iff removing {@code lesson} from its own (group,
     * date) does not raise M18's own day-start offset, which can only happen when the lesson IS
     * the day's first one. The offset is the constraint's own ({@link
     * SchoolScheduleConstraintProvider#m18DayStartOffset}), never a re-derivation. Package-private
     * so {@link AnchorRepair} (TASK-433) reuses the same donor-anchor rule instead of duplicating it.
     */
    static boolean keepsDayAnchored(Lesson lesson, Map<String, TreeMap<Integer, Boolean>> groupDaySlots) {
        StudentGroup group = lesson.getStudentGroup();
        LocalDate date = lesson.timeslot.getDate();
        if (group == null || group.id == null || date == null) {
            return true;
        }
        TreeMap<Integer, Boolean> daySlots = groupDaySlots.get(group.id + "@" + date);
        if (daySlots == null || daySlots.isEmpty()) {
            return true;
        }
        int slot = TimeslotSequence.slotIndexOf(lesson.timeslot);
        if (slot != daySlots.firstKey()) {
            return true;   // the day's minimum slot does not move
        }
        Integer nextSlot = daySlots.higherKey(slot);
        if (nextSlot == null) {
            return true;   // the day empties: M18 groups by (group, date) over lessons, so it has no tuple left
        }
        return SchoolScheduleConstraintProvider.m18DayStartOffset(group, date, nextSlot)
                <= SchoolScheduleConstraintProvider.m18DayStartOffset(group, date, slot);
    }

    /**
     * M15 DEPARTURE guard: true iff taking {@code lesson} off its day does not raise the
     * penalized break width of its own (group, subject) slice. See the class javadoc's M15
     * section for why H17's own helper is the right judge of an M15 break.
     */
    private static boolean keepsSubjectBlockWhole(Lesson lesson, TreeMap<Integer, Lesson> donorDay) {
        if (lesson.isLab()) {
            return true;   // labs are outside M15 (its own !isLab() filter)
        }
        return BoxRepair.isH17SafeDeparture(subjectSlice(lesson.getSubject(), donorDay),
                TimeslotSequence.slotIndexOf(lesson.timeslot));
    }

    /**
     * M15 ARRIVAL guard: true iff landing at {@code slot} does not raise the penalized break
     * width of the (group, subject) {@code slice} it joins; a null slice means the mover is a
     * lab lesson, which M15 does not see at all. Same rationale as above.
     */
    private static boolean joinsSubjectBlock(TreeMap<Integer, Boolean> slice, int slot) {
        return slice == null || BoxRepair.isH17Safe(slice, slot);
    }

    /**
     * The {@code slotIndex -> AUTHORITATIVELY pinned} map of ONE (group, subject, date) slice
     * of a day - the exact population M15 groups by, in the encoding {@link BoxRepair}'s H17
     * helpers read (see the class javadoc's M15 section). The day is never null: both callers
     * hold a live day map they have already found.
     */
    private static TreeMap<Integer, Boolean> subjectSlice(String subject, TreeMap<Integer, Lesson> day) {
        TreeMap<Integer, Boolean> slice = new TreeMap<>();
        for (Map.Entry<Integer, Lesson> entry : day.entrySet()) {
            Lesson other = entry.getValue();
            if (other.isLab() || !Objects.equals(subject, other.getSubject())) continue;
            slice.put(entry.getKey(), BaseConstraintProvider.isAuthoritativePin(other));
        }
        return slice;
    }

    // ─── day classification and metrics (one definition, shared by the pass and the harness) ──

    /** M20's marginal value of one more countable hour on this day, read off the live map. */
    private static int fillGain(HoleRepair.DayRef day, TreeMap<Integer, Boolean> slots) {
        if (slots == null || slots.isEmpty()) {
            return 0;   // an empty day is never a target: opening one is an M22/M12 trade this pass does not price
        }
        return BoxRepair.m20FillGain(day.group(), day.date(), slots);
    }

    /**
     * True iff this day holds a genuine NET surplus above its pin-netted target, so lending one
     * countable hour is free on the M20 axis without hiding the donor's own deficit.
     *
     * <p>TASK-429: post-TASK-424 netting, a mixed day "at plan with pins" (N authoritative pins +
     * one solver hour, dailyQuota N+1, effectiveQuota 1) reads a zero {@code m20MarginalRemovalCost}
     * yet has no real surplus. Lending from it moves the donor to N pins + 0 solver (occupancy 0),
     * and its remaining deficit turns invisible to M20 (the {@code totalHours > 0} filter), so the
     * aggregated deficit is merely redistributed, not paid. The test is therefore occupancy &gt;
     * {@link SchoolScheduleConstraintProvider#effectiveQuota} - the same net-surplus definition M33
     * penalizes, MINUS M33's {@code m20Quota > 0} scope gate. That gate is deliberately NOT here:
     * a quota-0 day (e.g. a stage-only date) is a LEGAL donor (every countable hour is surplus above
     * an effectiveQuota of 0), it is just never a "surplus day" in the {@link DayFill} count, so the
     * {@code m20Quota > 0} filter lives once, in {@link #measure}.
     */
    private static boolean isSurplusDay(HoleRepair.DayRef day, TreeMap<Integer, Boolean> slots) {
        int occupancy = BoxRepair.m20Occupancy(slots);
        if (occupancy <= 0) {
            return false;   // nothing countable to lend
        }
        return occupancy > SchoolScheduleConstraintProvider.effectiveQuota(day.group(), day.date());
    }

    /**
     * The day-fill readout of a whole lesson population - the checkpoint harness's before/after
     * measurement, and the same three numbers {@link #repair} logs off its own live maps.
     */
    public static DayFill measure(List<Lesson> lessons) {
        Map<String, TreeMap<Integer, Lesson>> dayLessons = HoleRepair.buildDayLessons(lessons);
        // Unsorted: a roll-up over every day, so the enumeration order cannot change it.
        return measure(HoleRepair.buildDayRefs(dayLessons), BoxRepair.buildGroupDaySlots(lessons));
    }

    /**
     * TASK-429 diagnostic: the number of (group, date) days the pre-netting rule (marginal
     * removal cost 0) would have called surplus but the netting-aware {@link #isSurplusDay} now
     * excludes, i.e. the at-plan-with-pin false positives this fix removes. Printed by the
     * checkpoint harness so the size of the trade is visible in a single run; not read by the
     * pass itself. Reproduces ONLY the old classification (the new one delegates to isSurplusDay),
     * so it cannot silently drift from either definition.
     */
    public static int countSurplusDaysExcludedByNetting(List<Lesson> lessons) {
        Map<String, TreeMap<Integer, Lesson>> dayLessons = HoleRepair.buildDayLessons(lessons);
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = BoxRepair.buildGroupDaySlots(lessons);
        int excluded = 0;
        for (HoleRepair.DayRef day : HoleRepair.buildDayRefs(dayLessons)) {
            TreeMap<Integer, Boolean> slots = groupDaySlots.get(day.key());
            int placedTotal = slots == null ? 0 : slots.size();
            int occupancy = BoxRepair.m20Occupancy(slots);
            if (occupancy <= 0) {
                continue;
            }
            boolean oldRule = SchoolScheduleConstraintProvider.m20MarginalRemovalCost(
                    day.group(), day.date(), occupancy, placedTotal) == 0;
            if (oldRule && !isSurplusDay(day, slots)) {
                excluded++;
            }
        }
        return excluded;
    }

    private static DayFill measure(List<HoleRepair.DayRef> days,
                                   Map<String, TreeMap<Integer, Boolean>> groupDaySlots) {
        int deficit = 0;
        int surplus = 0;
        for (HoleRepair.DayRef day : days) {
            TreeMap<Integer, Boolean> slots = groupDaySlots.get(day.key());
            if (fillGain(day, slots) > 0) {
                deficit++;
            } else if (isSurplusDay(day, slots)
                    && SchoolScheduleConstraintProvider.m20Quota(day.group(), day.date()) > 0) {
                // TASK-429: isSurplusDay is the net surplus test (occupancy > effectiveQuota); the
                // m20Quota>0 gate stays here so a quota-0 day is a legal donor but never a "surplus" day (DayFill).
                surplus++;
            }
        }
        return new DayFill(HoleRepair.m20DayFillCost(days, groupDaySlots), deficit, surplus);
    }

    /**
     * The five live indexes this pass reads and keeps honest, carried together so every gate
     * asks the same maps: the (group, date) day view, the occupancy index (H1/H3), the
     * per-(group, date) slot map (H11 + every H17/M18 read), the per-teacher daily counts (H7)
     * and the date/slot -&gt; {@link Timeslot} resolver.
     */
    private record Grid(Map<String, TreeMap<Integer, Lesson>> dayLessons,
                        Map<Timeslot, List<Lesson>> byTs,
                        Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                        Map<String, Integer> teacherDaily,
                        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot) {
    }
}
