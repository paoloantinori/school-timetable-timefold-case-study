package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import java.util.Objects;

import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;
import com.school.planner.util.SubjectNormalization;

/**
 * TASK-537 prototype: final closing campaign on the multi-start WINNER, with
 * the true/quasi-hard hierarchy READ from the golden (audit 20/08).
 *
 * <p>Semantics (the point of the pass): a move is accepted iff it never
 * increases a TRUE hard (H1/H3 conflicts, H17 compaction, H28) while it MAY
 * increase a QUASI hard deficit (H32 quota, H33 floor) as long as every quasi
 * family stays within the budget the golden itself practices. This is the
 * trade the uniform improve-or-noop guards cannot express: closing a TRUE H17
 * hole by paying one QUASI hour is a legal human move and an illegal machine
 * move today. The OPPOSITE direction (paying a TRUE to close a QUASI, the
 * reverted QFR moves of 19/08) is rejected here BY DIRECTION, not by delta.
 *
 * <p>Budgets (golden's own violations, audit 20/08, to be derived not
 * hardcoded in the production version): H32 1h/year total, H33 4 under-floor
 * days, H11 hours above the practiced cap 8 are NOT consumed here (the pass
 * never imports an hour into a day already at its aula+lab quota). H31: a
 * same-day pull-up fills an interior hole without touching the day's first
 * lesson, and the donor rule (LAST slot of the donor day only, code-review
 * finding 1/6) means a removal can never move a day start either. The quasi
 * costs mirror the REAL constraint semantics: Stage pseudo hours are not
 * counted (H32/H33 filter pseudo teachers), the floor is
 * {@link SchoolSchedule#getDayFloor} (fallback 4 when the derivation is
 * degraded), H32 scope exclusions (G04 / G08 / G14) and the G09 1h
 * tolerance are honored via {@code isQuotaFillExcluded}-equivalent checks.
 *
 * <p>Move set v1: slot-compact an H17 hole by pulling the lesson AFTER the
 * hole up into it, when the (group, date, slot) cell is free and the teacher
 * is free there (H1/H3 gated). Free in quasi terms: the mover stays on the
 * same day, so no quota axis moves.
 *
 * <p>Move set v2 (cross-day trade): when an H17 hole cannot be closed
 * intra-day (every pull-up is H1-blocked), IMPORT one hour from a donor
 * lesson of the SAME group on ANOTHER day: unpinned, non-pseudo teacher,
 * non-Sicurezza subject, at the LAST occupied slot of its own day (removing
 * an interior lesson would open a NEW true H17 hole in the donor day, and
 * taking the last slot also makes a day-start move impossible), teacher free
 * in the hole cell, group free there (the hole itself proves it), the hole
 * cell inside the group's calendar window (H20) and the import free of H28
 * A..B..A returns on the target day. Lab quota-bound donors (H23/H24 VRP
 * scope) are NOT excluded by the quasi-price accounting itself; the false
 * mitigation claimed here until 14/09 ("quota-bound lab subjects are
 * FC-only") was FALSIFIED by the TASK-614 walk (G13/Lavorazioni
 * meccaniche is quota-bound too): every landing now carries the
 * {@code isSlotLegalFresh} gate instead - the VRP IS accessible cheaply and
 * this pass is no longer the family's H5-blind exception. If removing
 * the donor drops its day below the aula+lab quota (minus the group's H32
 * tolerance, and only for groups IN H32 scope) the trade pays 1 quasi H32
 * hour; if it also drops below the per-group floor
 * ({@link SchoolSchedule#getDayFloor}, fallback 4) it pays 1 quasi H33 day.
 * The trade is accepted only while both budgets stay non-negative. Donor
 * order is deterministic: (date, slot).
 */
public final class FinalClosingCampaign {

    private static final Logger LOG = Logger.getLogger(FinalClosingCampaign.class);

    /** Golden budgets (audit 20/08). v1 constants; the DERIVED twins live in
     *  {@link GoldenSacrificeBudget} (TASK-537): on the 2026/27 seed the
     *  derivation CONFIRMS H33=4 and H11=43 but yields H32=0 (not 1) because
     *  the only in-scope golden under-quota day (G09 2026-10-01, 5h vs 6)
     *  is absorbed by the G09 tolerance this pass charges with. Wiring the
     *  derivation would therefore change the campaign semantics (H32 budget
     *  1 -> 0) and needs an explicit decision on the tolerance, so the
     *  constants stay audit-pinned until then. */
    static final int H32_BUDGET_HOURS = 1;
    static final int H33_BUDGET_DAYS = 4;
    /** Golden-practiced daily floor (H33 axis): never push a day under 4h. */
    static final int H33_FLOOR_HOURS = 4;

    private FinalClosingCampaign() {
    }

    /** {@code trueH17Closed} = holes closed; {@code quasiH32Paid}/{@code quasiH33Paid}
     *  = quasi hours/days spent (must stay within budget or the move is skipped);
     *  {@code movesRejectedByDirection} = refusals that would pay a TRUE (H1-blocked
     *  pull-up with no donor, plus H1-blocked donor candidates);
     *  {@code rejectedByBudget} = refusals of otherwise-legal trades (budget spent). */
    public record Result(int trueH17Closed, int quasiH32Paid, int quasiH33Paid, int movesRejectedByDirection,
            int rejectedByBudget) {

        public static Result noop() {
            return new Result(0, 0, 0, 0, 0);
        }
    }

    /** Production entry: golden budgets, quota facts from the SchoolSchedule
     *  static snapshot (aula+lab, stage excluded - the M20 axis). No cross-day
     *  room invalidation callback (the caller manages rooms elsewhere). */
    public static Result close(SchoolSchedule schedule, BiConsumer<Lesson, Timeslot> moveTimeslot) {
        return close(schedule, moveTimeslot, FinalClosingCampaign::defaultQuotaAulaLab,
                H32_BUDGET_HOURS, H33_BUDGET_DAYS);
    }

    /** Production entry with the cross-day room invalidation callback (see the
     *  full-entry javadoc). */
    public static Result close(SchoolSchedule schedule, BiConsumer<Lesson, Timeslot> moveTimeslot,
            Consumer<Lesson> nullRoom) {
        return close(schedule, moveTimeslot, FinalClosingCampaign::defaultQuotaAulaLab,
                H32_BUDGET_HOURS, H33_BUDGET_DAYS, nullRoom);
    }

    /** aula+lab quota for (group, date) from the static snapshot, or null when
     *  no row exists (no fact -> no quasi charge, no H11 guard). */
    private static Integer defaultQuotaAulaLab(Long groupId, LocalDate date) {
        DailyActivityQuota row = SchoolSchedule.getExpectedHoursRow(groupId, date);
        return row == null ? null : row.aulaHours + row.labHours;
    }

    /** Full entry: quota function and starting budgets are injectable so the
     *  checkpoint tests can drive both without a database. {@code nullRoom},
     *  when non-null, is invoked on a donor lesson right after a CROSS-DAY
     *  move: the room it carries may be illegal in the new day's cell
     *  (H29-family), so the callback lets the caller null it (same contract
     *  as the siblings' Consumer&lt;Lesson&gt; nullRoom). Same-day pull-ups
     *  never trigger it (the cell's room legality does not change). */
    public static Result close(SchoolSchedule schedule, BiConsumer<Lesson, Timeslot> moveTimeslot,
            BiFunction<Long, LocalDate, Integer> quotaAulaLab, int h32Budget, int h33Budget) {
        return close(schedule, moveTimeslot, quotaAulaLab, h32Budget, h33Budget, null);
    }

    /** Full entry with cross-day room invalidation (see the 5-arg javadoc). */
    public static Result close(SchoolSchedule schedule, BiConsumer<Lesson, Timeslot> moveTimeslot,
            BiFunction<Long, LocalDate, Integer> quotaAulaLab, int h32Budget, int h33Budget,
            Consumer<Lesson> nullRoom) {
        if (schedule.getTimeslots() == null || schedule.getLessons() == null) {
            return Result.noop();
        }
        Map<String, TreeMap<Integer, Lesson>> dayLessons = HoleRepair.buildDayLessons(schedule.getLessons());
        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot =
                StudentDayCompaction.buildTimeslotsByDateAndSlot(schedule.getTimeslots());
        // H1 capacity: teacher ID -> (date, slot) -> count, from the live grid
        // (keyed by id, not name: homonym teachers must not collide)
        Map<Long, Map<Long, Integer>> teacherBusy = new HashMap<>();
        // Quota axis: group id -> date -> lesson hours currently placed
        Map<Long, Map<LocalDate, Integer>> groupDayHours = new HashMap<>();
        // Donor candidates, prebuilt ONCE: group id -> unpinned placed lessons
        // of that group, deterministic (date, slot) order
        Map<Long, List<Lesson>> donorCandidates = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null) {
                continue;
            }
            LocalDate d = l.timeslot.startDateTime.toLocalDate();
            if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                teacherBusy.computeIfAbsent(l.getTeacher().id, k -> new HashMap<>())
                        .merge(cell(d, TimeslotSequence.slotIndexOf(l.timeslot)), 1, Integer::sum);
            }
            if (l.course != null && l.course.studentGroup != null && l.course.studentGroup.id != null) {
                // Quota/floor axes count NON-STAGE hours only (H32/H33 filter
                // pseudo teachers): the budget must price what the real
                // constraints price (code-review finding 4a).
                boolean nonStage = l.getTeacher() == null || !l.getTeacher().pseudo;
                if (nonStage) {
                    groupDayHours.computeIfAbsent(l.course.studentGroup.id, k -> new HashMap<>())
                            .merge(d, 1, Integer::sum);
                }
                // Donor filters (finding 5): unpinned, non-Stage (a pseudo
                // teacher passes H1 always and its hours belong to the Stage
                // bucket) and not Sicurezza (H30 pins it before stage).
                if (!l.pinned && nonStage
                        && !BaseConstraintProvider.SICUREZZA_SUBJECT
                                .equals(SubjectNormalization.normalize(l.getSubject()))) {
                    donorCandidates.computeIfAbsent(l.course.studentGroup.id, k -> new ArrayList<>()).add(l);
                }
            }
        }
        for (List<Lesson> cands : donorCandidates.values()) {
            cands.sort(Comparator.comparing((Lesson l) -> l.timeslot.startDateTime.toLocalDate())
                    .thenComparing(l -> TimeslotSequence.slotIndexOf(l.timeslot)));
        }
        Set<Lesson> movedDonors = new HashSet<>();
        int closed = 0;
        int h32Paid = 0;
        int h33Paid = 0;
        int rejected = 0;
        int rejectedBudget = 0;
        List<String> keys = new ArrayList<>(dayLessons.keySet());
        keys.sort(null); // deterministic
        for (String key : keys) {
            TreeMap<Integer, Lesson> day = dayLessons.get(key);
            int at = key.lastIndexOf('@');
            if (at <= 0 || day == null || day.isEmpty()) {
                // finding 2: a donor day emptied by a v2 trade leaves an empty
                // TreeMap in the day view - nothing to compact there
                continue;
            }
            Lesson any = day.firstEntry().getValue();
            if (any.course == null || any.course.studentGroup == null || any.course.studentGroup.id == null) {
                continue;
            }
            Long groupId = any.course.studentGroup.id;
            LocalDate date = LocalDate.parse(key.substring(at + 1));
            // v1 scope is same-day slot compaction (H31 can never move); v2
            // adds the cross-day donor import for holes the pull-up cannot
            // close. Iterate while compacting: after a move the day view shifts.
            boolean changed = true;
            while (changed) {
                changed = false;
                List<Integer> slots = new ArrayList<>(day.keySet());
                for (int i = 1; i < slots.size(); i++) {
                    int prev = slots.get(i - 1);
                    int cur = slots.get(i);
                    if (cur == prev + 1) {
                        continue;
                    }
                    // hole between prev and cur: first try pulling the lesson
                    // at cur up into prev+1 (free, same-day)...
                    int targetSlot = prev + 1;
                    Lesson mover = day.get(cur);
                    Map<Integer, Timeslot> dayTs = tsByDateSlot.get(date);
                    Timeslot targetTs = dayTs == null ? null : dayTs.get(targetSlot);
                    if (targetTs == null || mover.pinned) {
                        continue;
                    }
                    // finding 7: H20 guard - never fill a hole cell outside the
                    // group's calendar window for that weekday
                    int[] window = SchoolSchedule.getGroupCalendarWindow(
                            groupId, date.getDayOfWeek(), date);
                    if (window[1] >= 0 && (targetSlot < window[1] || targetSlot > window[2])) {
                        continue;
                    }
                    String tname = mover.getTeacher() == null ? null : mover.getTeacher().name;
                    Long tid = mover.getTeacher() == null ? null : mover.getTeacher().id;
                    boolean teacherFree = tname == null
                            || busy(teacherBusy, tid, date, targetSlot) == 0;
                    // TASK-614 root-cause cure (n=2 crash 14/09): H1-free is NOT legal -
                    // the gate chain here never consulted the lesson's own value range,
                    // so a pull-up could land T10/G13 on an H5.1-unavailable slot
                    // (06-10T11, the delivery-killing signature). The fresh per-slot VRP
                    // carries ALL the placement filters (H5/H6/H23/H26/M32): a slot it
                    // rejects would survive in the state and kill the next solver
                    // re-init with assertValueRange. A VRP-blocked pull-up FALLS THROUGH
                    // to the v2 donor trade (verification 4:47 PM: skipping the whole
                    // iteration would strip the hole of its import option).
                    if (teacherFree && mover.isSlotLegalFresh(targetTs)) {
                        // Quasi price: the mover stays on the SAME day, so no
                        // quota axis moves; a same-day pull-up is free.
                        applyMove(moveTimeslot, teacherBusy, groupDayHours, mover, targetTs, date, targetSlot);
                        day.remove(cur);
                        day.put(targetSlot, mover);
                        closed++;
                        changed = true;
                        break;
                    }
                    // ...H1-blocked (a TRUE): never force it. v2 trade: import
                    // a donor hour from another day of the same group.
                    DonorOutcome outcome = findDonor(donorCandidates, movedDonors, teacherBusy,
                            dayLessons, day, targetTs, groupId, date, targetSlot, quotaAulaLab,
                            groupDayHours, h32Budget - h32Paid, h33Budget - h33Paid);
                    rejectedBudget += outcome.refusedByBudget();
                    rejected += outcome.refusedByDirection();
                    Lesson donor = outcome.donor();
                    if (donor == null && !outcome.quotaGuard()) {
                        // no donor at all and the H11 quota guard did not fire:
                        // the hole stays open because every move would pay a TRUE
                        rejected++;
                    }
                    if (donor != null) {
                        LocalDate donorDate = donor.timeslot.startDateTime.toLocalDate();
                        int donorSlot = TimeslotSequence.slotIndexOf(donor.timeslot);
                        applyMove(moveTimeslot, teacherBusy, groupDayHours, donor, targetTs, date, targetSlot);
                        if (nullRoom != null) {
                            // cross-day move: the donor's room may be illegal in
                            // the new day's cell (H29-family) -> let the caller null it
                            nullRoom.accept(donor);
                        }
                        movedDonors.add(donor);
                        TreeMap<Integer, Lesson> donorDay = dayLessons.get(donorKey(donorDate, groupId));
                        if (donorDay != null) {
                            donorDay.remove(donorSlot);
                        }
                        day.put(targetSlot, donor);
                        h32Paid += outcome.cost32();
                        h33Paid += outcome.cost33();
                        closed++;
                        changed = true;
                        break;
                    }
                    // no donor at all: hole stays, move on to the next hole
                }
            }
        }
        LOG.infof("[FinalClosingCampaign] TASK-537: true H17 holes closed=%d, quasi H32 paid=%d (budget %d), "
                + "H33 paid=%d (budget %d), rejected-by-direction=%d, rejected-by-budget=%d",
                closed, h32Paid, h32Budget, h33Paid, h33Budget, rejected, rejectedBudget);
        return new Result(closed, h32Paid, h33Paid, rejected, rejectedBudget);
    }

    /** Donor search outcome: the accepted donor (or null), the quasi costs of
     *  REMOVING that donor (single source of truth, the caller must not
     *  recompute them), how many otherwise-legal candidates were refused
     *  because a quasi budget was already spent, how many candidates were
     *  refused because they would pay a TRUE (H1), and whether the H11 quota
     *  guard stopped the search before any candidate was examined. */
    private record DonorOutcome(Lesson donor, int cost32, int cost33, int refusedByBudget,
            int refusedByDirection, boolean quotaGuard) {

        static DonorOutcome stop(boolean quotaGuard) {
            return new DonorOutcome(null, 0, 0, 0, 0, quotaGuard);
        }
    }

    private static long cell(LocalDate date, int slot) {
        return date.toEpochDay() * 100 + slot;
    }

    private static int busy(Map<Long, Map<Long, Integer>> teacherBusy, Long teacherId, LocalDate date, int slot) {
        return teacherBusy.getOrDefault(teacherId, Map.of()).getOrDefault(cell(date, slot), 0);
    }

    private static int hours(Map<Long, Map<LocalDate, Integer>> groupDayHours, Long groupId, LocalDate date) {
        return groupDayHours.getOrDefault(groupId, Map.of()).getOrDefault(date, 0);
    }

    private static String donorKey(LocalDate date, Long groupId) {
        return groupId + "@" + date;
    }

    /** Commit one move and keep the H1 and quota indexes live: same-day
     *  pull-ups net zero on the quota axis, donor imports shift one hour
     *  from the donor day to the target day. */
    private static void applyMove(BiConsumer<Lesson, Timeslot> moveTimeslot,
            Map<Long, Map<Long, Integer>> teacherBusy, Map<Long, Map<LocalDate, Integer>> groupDayHours,
            Lesson mover, Timeslot targetTs, LocalDate targetDate, int targetSlot) {
        Timeslot oldTs = mover.timeslot;
        LocalDate oldDate = oldTs.startDateTime.toLocalDate();
        int oldSlot = TimeslotSequence.slotIndexOf(oldTs);
        moveTimeslot.accept(mover, targetTs);
        if (mover.getTeacher() != null && !mover.getTeacher().pseudo) {
            var tb = teacherBusy.computeIfAbsent(mover.getTeacher().id, k -> new HashMap<>());
            tb.merge(cell(oldDate, oldSlot), -1, Integer::sum);
            tb.merge(cell(targetDate, targetSlot), 1, Integer::sum);
        }
        Long gid = mover.course != null && mover.course.studentGroup != null ? mover.course.studentGroup.id : null;
        if (gid != null) {
            var gh = groupDayHours.computeIfAbsent(gid, k -> new HashMap<>());
            gh.merge(oldDate, -1, Integer::sum);
            gh.merge(targetDate, 1, Integer::sum);
        }
    }

    /**
     * First deterministic donor (date, slot order) for the hole cell
     * {@code (groupId, date, targetSlot)}: unpinned non-Stage non-Sicurezza
     * lesson of the same group on ANOTHER day, sitting at the LAST occupied
     * slot of its own day (finding 1: removing an interior lesson would open a
     * NEW true H17 hole in the donor day; taking the last slot also makes a
     * day-start move impossible, finding 6), whose teacher is free in the
     * hole cell, whose import creates no H28 A..B..A return on the target day
     * (finding 3), and whose removal keeps both quasi budgets non-negative.
     * Never returns a donor that would add a TRUE (H1 teacher conflict; H3 is
     * free by construction, the target cell is a hole of this very group) or
     * push the target day above its aula+lab quota (H11 axis: this pass never
     * adds hours above quota).
     *
     * <p>Quasi costs mirror the real constraints (finding 4): pseudo-Stage
     * hours were never counted in {@code groupDayHours}; the H32 charge is
     * skipped for groups out of H32 scope (G04 / G08 / G14, same list as
     * {@code SchoolScheduleConstraintProvider.isQuotaFillExcluded}) and uses
     * the G09 1h tolerance; the H33 charge uses
     * {@link SchoolSchedule#getDayFloor} with the 4h fallback; both charges
     * apply only on in-session days (aula+lab quota row > 0).
     *
     * <p>{@code targetTs} is the already-resolved timeslot of the hole cell
     * (the caller verified it non-null). Candidates come from the prebuilt
     * {@code donorCandidates} map (group id -> (date, slot)-sorted lessons);
     * lessons of the target day and donors already moved in this pass are
     * skipped.
     */
    private static DonorOutcome findDonor(Map<Long, List<Lesson>> donorCandidates, Set<Lesson> movedDonors,
            Map<Long, Map<Long, Integer>> teacherBusy, Map<String, TreeMap<Integer, Lesson>> dayLessons,
            TreeMap<Integer, Lesson> targetDay, Timeslot targetTs,
            Long groupId, LocalDate date, int targetSlot, BiFunction<Long, LocalDate, Integer> quotaAulaLab,
            Map<Long, Map<LocalDate, Integer>> groupDayHours, int h32Left, int h33Left) {
        Integer targetQuota = quotaAulaLab.apply(groupId, date);
        if (targetQuota != null && hours(groupDayHours, groupId, date) + 1 > targetQuota) {
            return DonorOutcome.stop(true);
        }
        int refusedByBudget = 0;
        int refusedByDirection = 0;
        for (Lesson cand : donorCandidates.getOrDefault(groupId, List.of())) {
            if (movedDonors.contains(cand)
                    || cand.timeslot.startDateTime.toLocalDate().equals(date)) {
                continue;
            }
            Long tid = cand.getTeacher() == null ? null : cand.getTeacher().id;
            if (tid != null && busy(teacherBusy, tid, date, targetSlot) != 0) {
                refusedByDirection++; // would add H1 (a TRUE): direction-forbidden
                continue;
            }
            // TASK-614 root-cause cure, donor side (verification 4:47 PM: the gate
            // belongs in the candidate loop, same form as the H1 check, so the hole
            // closes with the FIRST VRP-legal candidate instead of staying open when
            // candidate #1 is illegal and later ones are legal): the donor lands at
            // targetTs - its own value range must accept the cell or the placement
            // becomes the next assertValueRange delivery-killer.
            if (!cand.isSlotLegalFresh(targetTs)) {
                refusedByDirection++; // direction-forbidden by the candidate's own VRP
                continue;
            }
            LocalDate dd = cand.timeslot.startDateTime.toLocalDate();
            // finding 1: only the LAST occupied slot of the donor day may be
            // taken - the donor day stays contiguous and its start cannot move
            TreeMap<Integer, Lesson> donorDay = dayLessons.get(donorKey(dd, groupId));
            if (donorDay == null || donorDay.isEmpty()
                    || donorDay.lastKey() != TimeslotSequence.slotIndexOf(cand.timeslot)) {
                continue;
            }
            // finding 3: H28 guard - the import must not complete an A..B..A
            // return of the donor's subject on the target day (a TRUE)
            if (createsH28Return(targetDay, cand, targetSlot)) {
                refusedByDirection++;
                continue;
            }
            Integer donorQuota = quotaAulaLab.apply(groupId, dd);
            boolean inSession = donorQuota != null && donorQuota > 0;
            String groupName = cand.course.studentGroup.name;
            int tolerance = SchoolScheduleConstraintProvider.quotaFillTolerance(groupName);
            int after = hours(groupDayHours, groupId, dd) - 1;
            int cost32 = inSession && !SchoolScheduleConstraintProvider.isQuotaFillExcluded(groupName)
                    && after < donorQuota - tolerance ? 1 : 0;
            Integer derivedFloor = SchoolSchedule.getDayFloor(groupId);
            int floor = derivedFloor == null || derivedFloor <= 0 ? H33_FLOOR_HOURS : derivedFloor;
            int cost33 = inSession && after < floor ? 1 : 0;
            if (cost32 > h32Left || cost33 > h33Left) {
                refusedByBudget++;
                continue; // quasi budget spent: refuse this trade, keep looking
            }
            return new DonorOutcome(cand, cost32, cost33, refusedByBudget, refusedByDirection, false);
        }
        return new DonorOutcome(null, 0, 0, refusedByBudget, refusedByDirection, false);
    }

    /**
     * H28 semantics on the day view (finding 3), mirroring
     * {@code teacherNoReturnWithinGroupSubjectDay}: placing {@code donor} of
     * subject S at {@code targetSlot} of the target day would create a TRUE
     * violation iff, among the target day's S lessons (donor included):
     * either a same-teacher pair (a, b) with a below and b above the target
     * slot exists whose teacher differs from the donor's (donor plays the
     * different-teacher "c" between them), or a same-teacher lesson x exists
     * on the other side of the target slot with a different-teacher S lesson
     * strictly between x and the donor (donor plays a pair member). Assumes
     * the pre-import day was H28-clean, so any violation involves the donor.
     */
    private static boolean createsH28Return(TreeMap<Integer, Lesson> targetDay, Lesson donor, int targetSlot) {
        String subject = donor.getSubject();
        var donorTeacher = donor.getTeacher();
        List<Lesson> sameSubject = new ArrayList<>();
        for (Map.Entry<Integer, Lesson> e : targetDay.entrySet()) {
            if (e.getKey() == targetSlot) {
                continue; // the hole: nothing there pre-import
            }
            if (Objects.equals(e.getValue().getSubject(), subject)) {
                sameSubject.add(e.getValue());
            }
        }
        // donor as the different-teacher c: same-teacher pair straddling the
        // target slot, teacher != donor's
        Lesson below = null;
        Lesson above = null;
        for (Lesson l : sameSubject) {
            int s = TimeslotSequence.slotIndexOf(l.timeslot);
            if (s < targetSlot && (below == null || s > TimeslotSequence.slotIndexOf(below.timeslot))) {
                below = l;
            }
            if (s > targetSlot && (above == null || s < TimeslotSequence.slotIndexOf(above.timeslot))) {
                above = l;
            }
        }
        if (below != null && above != null && Objects.equals(below.getTeacher(), above.getTeacher())
                && !Objects.equals(below.getTeacher(), donorTeacher)) {
            return true;
        }
        // donor as a pair member: nearest same-TEACHER lesson on either side,
        // with a different-teacher same-subject lesson strictly between
        for (Lesson pair : sameSubject) {
            if (!Objects.equals(pair.getTeacher(), donorTeacher)) {
                continue;
            }
            int ps = TimeslotSequence.slotIndexOf(pair.timeslot);
            int lo = Math.min(ps, targetSlot);
            int hi = Math.max(ps, targetSlot);
            for (Lesson middle : sameSubject) {
                int ms = TimeslotSequence.slotIndexOf(middle.timeslot);
                if (ms > lo && ms < hi && !Objects.equals(middle.getTeacher(), donorTeacher)) {
                    return true;
                }
            }
        }
        return false;
    }
}
