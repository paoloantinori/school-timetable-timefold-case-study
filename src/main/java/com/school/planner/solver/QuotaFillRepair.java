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
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;

/**
 * TASK-533 component 1: deterministic quota-fill repair, post-RELEASE sibling
 * of SurplusRebalance. Where SurplusRebalance optimizes the M20 axis (medium),
 * this pass repairs the H32 axis (hard): (group, date) days whose assigned
 * non-Stage hours sit BELOW the declared aula+lab quota, borrowing hours from
 * the same group's days in surplus.
 *
 * <p><b>Why this pass exists (phase 0 evidence, 19/08 night):</b> the
 * quota-aware construction tail fills to quota only with STILL-UNPLACED
 * lessons. When the group's pool is fully placed but mis-distributed (G10:
 * all lessons placed, 5 days 1h under quota, 7 days at 8h against quota 4-5),
 * no construction tail can act: the hours must MOVE. The LS does not close
 * these cells (5 residual H32 at run end), and the existing repair passes are
 * M20-oriented (SurplusRebalance prices the medium axis) so a hard deficit
 * below quota never drives them.
 *
 * <p><b>Contract (same family as every staged pass):</b> zero
 * conflicts-added, improve-or-noop evaluated by the CALLER on the full
 * timeslot-tier hard (H32/H31/H17/H28 included), deterministic order (days
 * most-under-quota first, ties by group name then date), donor pool = the
 * same group's non-pinned, non-pseudo lessons sitting on a day ABOVE quota.
 * Every candidate move is gated on H1 (teacher free at the target slot); the
 * pass moves timeslots only, never rooms.
 */
public final class QuotaFillRepair {

    private static final Logger LOG = Logger.getLogger(QuotaFillRepair.class);

    /**
     * TASK-556: v2 move choice, RATIFIED DEFAULT ON (maintainer, 2026-08-27, handoff mandato
     * mattina 27/08): standard opt-OUT form, null/absent -> v2, only the explicit literal
     * {@code false} keeps the legacy v1 path. Evidence
     * ({@code task556-results/ciclo-gate-e-e2e/VERDICT-2026-08-26.md}): same-seed
     * formality (57 moves / 14 cells / 0 reverts on the full cycle) plus the discriminant
     * control (sanity identical with v2 OFF: no hidden cost). v2 fixes the two move-choice
     * defects the live revert measured ("quota fill repair REVERTED, H31 0 -> -3"):
     * (1) the donor yields from its TAIL, never from slot 0, so the donor day's start
     * never moves (H31-safe); (2) the target's landing slots are BORDER-AWARE (ascending
     * from the day's first open slot on an empty day; interior gaps then last+1 on a
     * partial day), so the fill is contiguous by construction (H17/H31-safe); plus a
     * retry re-sweep while the pass progresses.
     */
    public static final String V2_FLAG = "planner.construction.quota-fill.v2.enabled";

    /** Public for ScheduleService's banner (same form as EmptyDayFinisher.enabled()). */
    public static boolean v2Enabled() {
        String v = System.getProperty(V2_FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    /** TASK-556 item-4 sweep knob: the donor VISITING ORDER. {@link #DONOR_ORDER_SURPLUS}
     *  (default: current v2 shape, biggest surplus first, ties by date) or {@link
     *  #DONOR_ORDER_NEAREST} (date proximity to the target first, ties by surplus).
     *  Measurement-only lever for the localized resume A/B ("do the 14/15 closed
     *  cells rise without touching sanity?"); the default keeps the v2 shape
     *  byte-identical. */
    static final String DONOR_ORDER_KNOB = "planner.construction.quota-fill.v2.donor-order";
    static final String DONOR_ORDER_SURPLUS = "surplus";
    static final String DONOR_ORDER_NEAREST = "nearest";

    static boolean donorOrderNearest() {
        return DONOR_ORDER_NEAREST.equalsIgnoreCase(System.getProperty(DONOR_ORDER_KNOB));
    }

    /** Safety cap: this pass moves whole days' worth of hours one at a time. */
    private static final int MAX_MOVES_PER_PASS = 500;

    private QuotaFillRepair() {
    }

    /** {@code movesApplied} = hours moved surplus->deficit; {@code cellsClosed} =
     *  (group, date) days brought to quota; {@code conflictsAdded} must be 0. */
    public record Result(int movesApplied, int cellsClosed, long conflictsAdded) {

        private static final Result NOOP = new Result(0, 0, 0);

        public static Result noop() {
            return NOOP;
        }
    }

    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                Consumer<Lesson> nullRoom) {
        if (schedule.getTimeslots() == null) {
            return Result.noop();
        }
        // Same post-RELEASE contract as SurplusRebalance: the donor pool reads the raw
        // pinned flag, which is authoritative only once the promotion is released.
        if (!SchoolSchedule.getPromotedLessonIdsStatic().isEmpty()) {
            throw new IllegalStateException("QuotaFillRepair.repair called while "
                    + SchoolSchedule.getPromotedLessonIdsStatic().size()
                    + " promoted pin(s) are held - post-release grid only");
        }

        // Live day view (same one-lesson-per-(group, date, slot) contract as HoleRepair)
        Map<String, TreeMap<Integer, Lesson>> dayLessons = HoleRepair.buildDayLessons(schedule.getLessons());
        if (dayLessons.isEmpty()) {
            return Result.noop();
        }
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(schedule.getLessons());
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = BoxRepair.buildGroupDaySlots(schedule.getLessons());
        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot =
                StudentDayCompaction.buildTimeslotsByDateAndSlot(schedule.getTimeslots());

        long conflictsBefore = StudentDayCompaction.countConflicts(schedule);

        // Classify every (group, date) with lessons: quota from the same accessor the
        // constraint reads (the school's own declaration), hours = placed lessons.
        List<DayCell> cells = new ArrayList<>();
        for (var e : dayLessons.entrySet()) {
            String key = e.getKey(); // HoleRepair day key: groupId@date
            int at = key.lastIndexOf('@');
            if (at <= 0) {
                continue;
            }
            Lesson any = e.getValue().firstEntry().getValue();
            if (any.course == null || any.course.studentGroup == null) {
                continue;
            }
            LocalDate date = LocalDate.parse(key.substring(at + 1));
            int quota = declaredQuota(any.course.studentGroup.id, date);
            if (quota <= 0) {
                continue; // quota-0 day: donor-legal but never a target
            }
            // H32 counts NON-Stage hours only (the pseudo teacher's hours are the
            // stageHours bucket the sheet tracks separately): the cell classification
            // must match the constraint's, or the pass targets the wrong days.
            int hours = 0;
            for (Lesson l : e.getValue().values()) {
                var t = l.getTeacher();
                if (t == null || !t.pseudo) {
                    hours++;
                }
            }
            cells.add(new DayCell(any.course.studentGroup.name, any.course.studentGroup.id,
                    date, quota, hours, key));
        }

        // TASK-556 v2: the classification's structural blind spot is the EMPTY expected day:
        // cells come from dayLessons (days WITH lessons), so a declared day with zero lessons
        // is never a deficit and can never be filled. Add those cells from the quota rows.
        if (v2Enabled() && schedule.getStudentGroups() != null) {
            for (StudentGroup g2 : schedule.getStudentGroups()) {
                if (g2 == null || g2.id == null) {
                    continue;
                }
                for (var row : SchoolSchedule.getExpectedHoursForGroup(g2.id).entrySet()) {
                    int q2 = row.getValue().aulaHours + row.getValue().labHours;
                    if (q2 <= 0) {
                        continue;
                    }
                    String key2 = g2.id + "@" + row.getKey();
                    if (dayLessons.containsKey(key2)) {
                        continue;
                    }
                    cells.add(new DayCell(g2.name, g2.id, row.getKey(), q2, 0, key2));
                }
            }
        }

        // H32 scope exclusions (spec): the FC pairs' per-day target is the TASK-497
        // forma-b pool-cap, and G14 is the import-pinned special programme: their
        // under-quota cells are expected and must not drive moves here either.
        cells.removeIf(c -> "G04".equals(c.groupName) || "G08".equals(c.groupName)
                || "G14".equals(c.groupName));

        // Target order: most-under-quota first (deterministic ties: group name, date)
        List<DayCell> deficits = new ArrayList<>();
        Map<Long, List<DayCell>> surplusByGroup = new HashMap<>();
        for (DayCell c : cells) {
            if (c.hours < c.quota) {
                deficits.add(c);
            } else if (c.hours > c.quota) {
                surplusByGroup.computeIfAbsent(c.groupId, k -> new ArrayList<>()).add(c);
            }
        }
        deficits.sort(Comparator.comparingInt((DayCell c) -> c.quota - c.hours).reversed()
                .thenComparing(c -> c.groupName).thenComparing(c -> c.date));
        if (Boolean.getBoolean("qfr.v2.debug")) {
            LOG.infof("[QFR-v2 debug] cells=%d deficits=%d surplusGroups=%d",
                    cells.size(), deficits.size(), surplusByGroup.size());
        }
        for (List<DayCell> l : surplusByGroup.values()) {
            l.sort(Comparator.comparing((DayCell c) -> c.hours - c.quota).reversed()
                    .thenComparing(c -> c.date));
        }

        int moves = 0;
        int cellsClosed = 0;
        boolean budgetReached = false;
        Set<String> closedKeys = new HashSet<>();
        // TASK-556 v2: re-sweep while the pass progresses (a moved hour frees donor capacity
        // and changes H1 occupancy, so a second sweep sees options the first could not).
        int sweeps = 0;
        boolean v2 = v2Enabled();
        boolean progress = true;
        while (progress && !budgetReached && (sweeps++ == 0 || v2 && sweeps <= 10)) {
            progress = false;
            if (sweeps > 1) {
                // re-classify: hours moved across sweeps change deficit/surplus membership
                deficits = new ArrayList<>();
                surplusByGroup = new HashMap<>();
                for (DayCell c : cells) {
                    if (c.hours < c.quota) {
                        deficits.add(c);
                    } else if (c.hours > c.quota) {
                        surplusByGroup.computeIfAbsent(c.groupId, k -> new ArrayList<>()).add(c);
                    }
                }
                deficits.sort(Comparator.comparingInt((DayCell c) -> c.quota - c.hours).reversed()
                        .thenComparing(c -> c.groupName).thenComparing(c -> c.date));
                for (List<DayCell> l : surplusByGroup.values()) {
                    l.sort(Comparator.comparing((DayCell c) -> c.hours - c.quota).reversed()
                            .thenComparing(c -> c.date));
                }
            }
            for (DayCell target : deficits) {
                if (budgetReached) {
                    break;
                }
                // TASK-556 v2 gate (c): a PARTIALLY filled formerly-empty day is WORSE than
                // an empty one for H32 (the constraint only sees days WITH lessons), so an
                // empty cell is filled atomically: if the donors cannot bring it all the way
                // to quota, every move into it is rolled back (the localized banco measured
                // -2 -> -11 H32 before this gate).
                boolean atomicEmptyFill = v2 && target.hours == 0;
                List<Lesson> atomicMovers = atomicEmptyFill ? new ArrayList<>() : null;
                List<Timeslot> atomicOrigins = atomicEmptyFill ? new ArrayList<>() : null;
                List<DayCell> atomicDonors = atomicEmptyFill ? new ArrayList<>() : null;
                List<DayCell> donors = donorOrderNearest()
                        ? donorsNearestFirst(surplusByGroup.getOrDefault(target.groupId, List.of()), target)
                        : surplusByGroup.getOrDefault(target.groupId, List.of());
                for (DayCell donor : donors) {
                    // TASK-556 v2: one donor may yield SEVERAL hours to one target in the
                    // same visit (the atomic empty-fill needs the whole quota from however
                    // few donors there are; the v1 one-move-per-visit shape thrashes with
                    // the rollback: it moves one hour, undoes it, moves it again...).
                    // Gate (e) day-floor-aware: the donor stops at max(quota, PRACTICED
                    // floor) - draining a short-quota day to its H32 quota left days under
                    // the golden-practiced minimum (the confirmation cycle's sanity reds).
                    int floor = floorHours(donor);
                    while (target.hours < target.quota
                            && donor.hours > Math.max(donor.quota, floor)
                            && !budgetReached) {
                    // The legality is per (lesson, slot): a lesson's VRP may exclude a
                    // slot the group's calendar leaves open (quota-bound lab subjects,
                    // teacher unavailability). The live-run first firing hit exactly
                    // this: a move onto a VRP-illegal timeslot aborted the cycle with
                    // Timefold's value-range assertion. Gate EVERY candidate pair.
                    List<Timeslot> open = v2
                            ? findBorderAwareSlots(target, groupDaySlots, tsByDateSlot)
                            : findOpenSlots(target, groupDaySlots, tsByDateSlot);
                    if (open.isEmpty()) {
                        if (Boolean.getBoolean("qfr.v2.debug")) {
                            LOG.infof("[QFR-v2 debug] open EMPTY: target=%s", target.key);
                        }
                        break; // no open slot on the target day: cell stays (residual)
                    }
                    // TASK-556 v2 gate (d): a formerly-EMPTY day must take its FIRST hour at
                    // the day's FIRST open slot. If that slot is blocked (H1/VRP/room) the
                    // fill must NOT slide to a later slot: a late-starting day is a HARD H31
                    // cell the empty day never was (the iter-2 residual, H31 0 -> -3).
                    if (v2 && target.hours == 0) {
                        open = List.of(open.get(0));
                    }
                    Move mv = pickMove(dayLessons.get(donor.key), open, dayLessons, v2);
                    if (mv == null) {
                        if (Boolean.getBoolean("qfr.v2.debug")) {
                            LOG.infof("[QFR-v2 debug] no move: target=%s open=%d donor=%s",
                                    target.key, open.size(), donor.key);
                        }
                        break; // this donor has nothing legal left: next donor
                    }
                    Lesson candidate = mv.lesson();
                    Timeslot targetTs = mv.ts();
                    int donorSlot = TimeslotSequence.slotIndexOf(candidate.timeslot);
                    Timeslot donorTs = candidate.timeslot;
                    moveTimeslot.accept(candidate, targetTs);
                    // keep byTs live on BOTH sides of the move (the stale donor entry
                    // would over-block later H1 checks and hide the freed capacity)
                    List<Lesson> atDonor = byTs.get(donorTs);
                    if (atDonor != null) {
                        atDonor.remove(candidate);
                    }
                    // live-update the shared views so later iterations see the new grid
                    dayLessons.get(donor.key).remove(donorSlot);
                    // TASK-556 v2: the target may be a formerly-EMPTY day with no map yet
                    dayLessons.computeIfAbsent(target.key, k -> new TreeMap<>())
                            .put(TimeslotSequence.slotIndexOf(targetTs), candidate);
                    groupDaySlots.computeIfAbsent(target.key, k -> new TreeMap<>())
                            .put(TimeslotSequence.slotIndexOf(targetTs), true);
                    TreeMap<Integer, Boolean> donorSlots = groupDaySlots.get(donor.key);
                    if (donorSlots != null) {
                        donorSlots.remove(donorSlot);
                    }
                    byTs.computeIfAbsent(targetTs, k -> new ArrayList<>()).add(candidate);
                    target.hours++;
                    donor.hours--;
                    moves++;
                    progress = true;
                    if (atomicEmptyFill) {
                        atomicMovers.add(candidate);
                        atomicOrigins.add(donorTs);
                        atomicDonors.add(donor);
                    }
                    if (moves >= MAX_MOVES_PER_PASS) {
                        budgetReached = true;
                        break;
                    }
                    }
                }
                if (atomicEmptyFill && target.hours > 0 && target.hours < target.quota) {
                    // roll the whole partial fill back: an half-empty day is an H32 cell the
                    // empty day never was
                    for (int i = atomicMovers.size() - 1; i >= 0; i--) {
                        Lesson m = atomicMovers.get(i);
                        Timeslot origin = atomicOrigins.get(i);
                        List<Lesson> atTarget = byTs.get(m.timeslot);
                        if (atTarget != null) {
                            atTarget.remove(m);
                        }
                        dayLessons.get(target.key).remove(TimeslotSequence.slotIndexOf(m.timeslot));
                        TreeMap<Integer, Boolean> tSlots = groupDaySlots.get(target.key);
                        if (tSlots != null) {
                            tSlots.remove(TimeslotSequence.slotIndexOf(m.timeslot));
                        }
                        moveTimeslot.accept(m, origin);
                        dayLessons.get(atomicDonors.get(i).key)
                                .put(TimeslotSequence.slotIndexOf(origin), m);
                        groupDaySlots.computeIfAbsent(atomicDonors.get(i).key, k -> new TreeMap<>())
                                .put(TimeslotSequence.slotIndexOf(origin), true);
                        byTs.computeIfAbsent(origin, k -> new ArrayList<>()).add(m);
                        atomicDonors.get(i).hours++;
                        target.hours--;
                        moves--;
                    }
                }
                if (target.hours >= target.quota && closedKeys.add(target.key)) {
                    cellsClosed++;
                }
            }
        }

        long conflictsAfter = StudentDayCompaction.countConflicts(schedule);
        long conflictsAdded = conflictsAfter - conflictsBefore;
        if (conflictsAdded != 0) {
            LOG.warnf("[QuotaFillRepair] TASK-533: %d moves, %d cells closed, CONFLICTS ADDED %d"
                    + " (caller will revert via improve-or-noop)", moves, cellsClosed, conflictsAdded);
        } else {
            LOG.infof("[QuotaFillRepair] TASK-533: %d moves, %d cells closed to declared quota,"
                    + " 0 conflicts added", moves, cellsClosed);
        }
        return new Result(moves, cellsClosed, conflictsAdded);
    }

    /** The school's declared aula+lab quota for (group, date), same source H32 reads. */
    private static int declaredQuota(Long groupId, LocalDate date) {
        var row = SchoolSchedule.getExpectedHoursRow(groupId, date);
        if (row == null) {
            return 0;
        }
        return row.aulaHours + row.labHours;
    }

    /** TASK-556 v2 gate (e): the group's PRACTICED day floor (H33's own source), 0 if none. */
    private static int floorHours(DayCell donor) {
        Integer floor = SchoolSchedule.getDayFloor(donor.groupId);
        return floor != null ? floor : 0;
    }

    /** TASK-556 item-4 knob: the group's donors re-ordered by DATE PROXIMITY to the
     *  target (ties: bigger surplus, then date), so the fill drains the day NEXT
     *  DOOR before importing hours from far away. Copy, never mutates the shared
     *  per-group list the re-sweep re-sorts. */
    private static List<DayCell> donorsNearestFirst(List<DayCell> donors, DayCell target) {
        List<DayCell> out = new ArrayList<>(donors);
        out.sort(Comparator
                .comparingLong((DayCell c) -> Math.abs(c.date.toEpochDay() - target.date.toEpochDay()))
                .thenComparing(c -> c.hours - c.quota, Comparator.reverseOrder())
                .thenComparing(c -> c.date));
        return out;
    }

    /** Every still-free slot on the target day (no lesson of the group there). */
    private static List<Timeslot> findOpenSlots(DayCell target,
                                                Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                                Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot) {
        List<Timeslot> out = new ArrayList<>();
        Map<Integer, Timeslot> slots = tsByDateSlot.getOrDefault(target.date, Map.of());
        TreeMap<Integer, Boolean> occupied = groupDaySlots.getOrDefault(target.key, new TreeMap<>());
        for (int slot : slots.keySet()) {
            // buildGroupDaySlots maps slot -> isAuthoritativePin for EVERY placed
            // lesson: the key's PRESENCE is occupancy (the boolean says whether the
            // occupant is an authoritative pin, not whether the slot is free).
            if (!occupied.containsKey(slot)) {
                Timeslot ts = slots.get(slot);
                if (ts != null) {
                    out.add(ts);
                }
            }
        }
        return out;
    }

    /**
     * TASK-556 v2: the border-aware target slots, in the order the fill must happen to stay
     * H17/H31-clean. On an EMPTY day: ascending from the day's first open slot (the day fills
     * contiguously and starts at its own calendar opening). On a partial day: interior gaps
     * first, then the slot right after the last lesson (the only tail extension that opens
     * no hole). Any other free slot (a beyond-tail+1 jump) is exactly the H17 violation the
     * v1 unordered HashMap iteration could pick.
     */
    private static List<Timeslot> findBorderAwareSlots(DayCell target,
                                                       Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                                       Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot) {
        Map<Integer, Timeslot> slots = new TreeMap<>(tsByDateSlot.getOrDefault(target.date, Map.of()));
        TreeMap<Integer, Boolean> occupied = groupDaySlots.getOrDefault(target.key, new TreeMap<>());
        List<Timeslot> out = new ArrayList<>();
        if (occupied.isEmpty()) {
            for (int slot : slots.keySet()) {
                Timeslot ts = slots.get(slot);
                if (ts != null) {
                    out.add(ts);
                }
            }
            return out;
        }
        int first = occupied.firstKey();
        int last = occupied.lastKey();
        for (int slot : slots.keySet()) {
            if (occupied.containsKey(slot)) {
                continue;
            }
            boolean interiorGap = slot > first && slot < last;
            boolean tailExtend = slot == last + 1;
            if (interiorGap || tailExtend) {
                Timeslot ts = slots.get(slot);
                if (ts != null) {
                    out.add(ts);
                }
            }
        }
        return out;
    }

    /** A (lesson, target slot) pair that is legal for BOTH: VRP membership of the
     *  lesson (null set = unrestricted) plus H1 (teacher free at the slot, live view). */
    private record Move(Lesson lesson, Timeslot ts) {
    }

    private static Move pickMove(TreeMap<Integer, Lesson> donorDay, List<Timeslot> open,
                                 Map<String, TreeMap<Integer, Lesson>> dayLessons, boolean tailFirst) {
        if (donorDay == null) {
            return null;
        }
        // TASK-556 v2: the donor yields from its TAIL (descending slot order): vacating the
        // last slot keeps the run contiguous AND the day start at its own first slot (H31).
        // v1 keeps the ascending order (legacy behavior untouched).
        Iterable<Lesson> donorOrder = tailFirst ? donorDay.descendingMap().values() : donorDay.values();
        for (Timeslot targetTs : open) {
            // H1: every teacher already sitting at targetTs across ALL groups (live view)
            java.util.Set<Long> busyTeachers = new java.util.HashSet<>();
            for (var day : dayLessons.values()) {
                for (Lesson l : day.values()) {
                    if (l.timeslot != null && l.timeslot.id.equals(targetTs.id)
                            && l.getTeacher() != null && !l.getTeacher().pseudo) {
                        busyTeachers.add(l.getTeacher().id);
                    }
                }
            }
            donorLoop: for (Lesson l : donorOrder) {
                if (l.pinned || l.timeslot == null) {
                    continue;
                }
                // TASK-556 v2 gate (b): tail-first means the CURRENT LAST lesson only -
                // extracting a mid-run lesson opens an H17 hole at its slot (the localized
                // banco measured -49 H17 before this gate).
                if (tailFirst && TimeslotSequence.slotIndexOf(l.timeslot) != donorDay.lastKey()) {
                    break; // descending order: everything below is even further from the tail
                }
                var lt = l.getTeacher();
                if (lt != null && lt.pseudo) {
                    continue;
                }
                if (lt != null && busyTeachers.contains(lt.id)) {
                    continue;
                }
                // TASK-556 v2 gate (a): a mover KEEPS its room (this pass never touches
                // rooms), so it may only land where its own room is free - or it has none
                // (the localized banco measured -57000 H2 before this gate).
                if (l.room != null) {
                    for (var day : dayLessons.values()) {
                        for (Lesson o : day.values()) {
                            if (RoomAvailability.conflicts(l, l.room, targetTs, o, null)) {
                                continue donorLoop; // room taken at target: next donor lesson
                            }
                        }
                    }
                }
                // TASK-578: per-slot legality (stale-cache crash class; see
                // Lesson.isSlotLegalFresh: this gate fires per (target, donor) pair)
                if (!l.isSlotLegalFresh(targetTs)) {
                    continue; // VRP: this lesson may not sit at this slot
                }
                return new Move(l, targetTs);
            }
        }
        return null;
    }

    /** One (group, date) day: declared quota vs placed hours, live through the pass. */
    private static final class DayCell {
        final String groupName;
        final Long groupId;
        final LocalDate date;
        final int quota;
        final String key;
        int hours;

        DayCell(String groupName, Long groupId, LocalDate date, int quota, int hours, String key) {
            this.groupName = groupName;
            this.groupId = groupId;
            this.date = date;
            this.quota = quota;
            this.hours = hours;
            this.key = key;
        }
    }
}
