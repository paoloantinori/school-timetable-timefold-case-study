package com.school.planner.solver;

import java.time.LocalDate;

import com.school.planner.domain.DailyActivityQuota;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.neighborhood.HarvestNeighborhood;

/**
 * TASK-577: the long-range deterministic pass for H34 cells whose donors are too FAR
 * for {@link QuotaFillRepair} (which works within a bounded neighborhood) and whose
 * tails cannot be lifted by {@link EmptyDayFinisher} (which slides within the day).
 *
 * <p><b>The move</b> (one hour at a time): for every declared-empty day (quota aula+lab
 * &gt; 0, zero non-Stage lessons placed) of a group, harvest from a day of the SAME
 * group that is OVER quota (hours &gt; declared quota) at <b>any</b> date distance (v13:
 * plus the CHAIN fallback - when the plain pool is exhausted, the lesson blocking the
 * landing slot donates its own hour to ITS group, freeing the slot; see tryChain). The
 * donor yields from the BORDER of its block (never an interior hour: the donor's H17
 * contiguity is preserved). The landing is the first open slot of the empty day's
 * calendar window (the day is empty: contiguity is trivially preserved). The
 * preferred donor lesson is the one whose (weekday, slot) matches the target cell
 * (M32-neutral: the diagnosis shows the type-move {@code 16/09 slot 8-9 -> 9/06 slot
 * 8-9} is strictly improving). Gates: H1 (teacher conflict) and H3 (group conflict)
 * via live-index, H14 (start date), H11 (max daily hours), H7 (max teacher hours).
 *
 * <p><b>Flag</b>: {@code planner.construction.surplus-harvest.enabled}, DEFAULT OFF
 * (opt-in: {@code null}/absent -&gt; off, only the literal {@code "true"} arms).
 *
 * <p><b>Increase-or-noop on the tier hard</b>: every applied move must not increase
 * the hard score (the pass works on H34 cells whose hard is already paid; closing one
 * strictly decreases it). If a move would open a new hard violation, it is discarded.
 */
public final class SurplusHarvest {

    private static final Logger LOG = Logger.getLogger(SurplusHarvest.class.getName());

    public static final String FLAG = "planner.construction.surplus-harvest.enabled";
    /** TEST-ONLY knob (live-state v4): skip targets whose date is before the group's
     *  startDate (H14-impossible cells: the measurement isolates the genuine targets).
     *  The production scope decision belongs to Fedora/maintainer, NOT to this knob. */
    public static final String SKIP_PRE_START_FLAG = "planner.construction.surplus-harvest.skip-pre-start";

    /** TASK-577 8/09 notte: extend targets to H32 under-quota cells (not just H34
     *  empty). The cure's big box (280 vs 140) creates H32 violations the LS
     *  can't fix (cycle boxdef-8sep: H32 -5 at delivery). For H32 targets any
     *  additional hour is strictly improving, so the per-cell atomicity is
     *  SKIPPED (unlike H34 where partial fill is net-negative). */
    public static final String H32_TARGETS_FLAG = "planner.construction.surplus-harvest.h32-targets.enabled";

    /** Standard opt-in form: null/absent -> OFF, only "true" (case-insensitive) arms. */
    public static boolean enabled() {
        String v = System.getProperty(FLAG);
        return v != null && Boolean.parseBoolean(v);
    }

    /** Safety cap: this pass can in principle move one hour per cell. */
    private static final int MAX_MOVES_PER_PASS = 200;

    private SurplusHarvest() {
    }

    /** {@code cellsClosed} = declared-empty days that received at least one hour;
     *  {@code movesApplied} = hours moved surplus -> empty; {@code rejected} = candidate
     *  moves discarded by a gate or the improve-or-noop oracle. */
    public record Result(int movesApplied, int cellsClosed, int rejected, String rejectBreakdown) {
        private static final Result NOOP = new Result(0, 0, 0, "");
        public static Result noop() { return NOOP; }
    }

    /** Mutable per-cause reject counters (TASK-577 voice A: the diagnosis instrument).
     *  The chainBlk* triple decomposes the v13 blocker loop's silent continues:
     *  a chainNearMiss with chainFail=0 (the v131 winner's 96/0/0 signature) is
     *  otherwise uninterpretable - the blockers died in filters no counter saw. */
    static final class Rejects {
        int border, slideH1, h14, h7, h11, teacherBusy, groupBusy, noDonors, noSlots, oracleRevert, h31, h33, shape, roomBusy, chain, chainNearMiss, chainFail, chainBlkSameGroup, chainBlkSlide, chainBlkSlideH1;
        int total() {
            // simplify-A #1: chain counts SUCCESSES - a rejected total must not lie
            return border + slideH1 + h14 + h7 + h11 + teacherBusy + groupBusy + noDonors + noSlots
                    + oracleRevert + h31 + h33 + shape + roomBusy;
        }
        String breakdown() {
            return String.format(
                    "border=%d slideH1=%d h14=%d h7=%d h11=%d teacherBusy=%d groupBusy=%d noDonors=%d noSlots=%d oracleRevert=%d h31=%d h33=%d shape=%d roomBusy=%d chain=%d chainNearMiss=%d chainFail=%d chainBlkSameGroup=%d chainBlkSlide=%d chainBlkSlideH1=%d",
                    border, slideH1, h14, h7, h11, teacherBusy, groupBusy, noDonors, noSlots, oracleRevert, h31, h33, shape, roomBusy, chain, chainNearMiss, chainFail, chainBlkSameGroup, chainBlkSlide, chainBlkSlideH1);
        }
    }

    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                BiConsumer<Lesson, Room> moveRoom) {
        if (!enabled() || schedule.getTimeslots() == null) {
            return Result.noop();
        }

        // Live per-slot occupancy (rebuilt after each applied move).
        Occupancy occ = new Occupancy(schedule);
        Rejects rej = new Rejects();
        int moves = 0;
        int reverted = 0;
        int cells = 0;

        // Target cells: declared-empty days (any date distance).
        List<Target> targets = findEmptyDeclaredDays(schedule);
        // trace: the discovered targets (the group filter may match nothing in THIS
        // state: the empty cells of the delivered run are not necessarily the
        // boundary state's cells)
        String traceAll = System.getenv("SURPLUS_TRACE_ALL");
        if (traceAll != null) {
            for (Target t : targets) {
                System.out.println("[SH-trace] TARGET " + t.group.name + " " + t.date + " quota=" + t.quota);
            }
        }
        // v9: the per-group H33 hard floors (the 22:40 cycle reverted with H33 0->-10:
        // donors giving up their last floor hour)
        Map<String, Integer> floors = com.school.planner.service.DayFloorDeriver.deriveHardFloors(
                schedule.getStudentGroups(), schedule.getCourses(), schedule.getDailyActivityQuotas());
        for (Target target : targets) {
            if (moves >= MAX_MOVES_PER_PASS) break;
            int quota = target.quota;
            int placed = 0;
            // v10 C1 per-cell atomicity: every move's affected placements are kept so
            // the WHOLE cell can be rolled back. A partial fill (placed < quota -
            // tolerance) is WORSE than the flat H34 cell it replaces: H34 charges a
            // flat -1 for the empty day, but a 1h fill of a quota-5 day makes H32
            // charge (quota-placed) and H33 (floor-placed) = -6 net (the 22:40
            // cycle's whole-pass revert came from exactly this channel).
            List<Lesson> cellAffected = new ArrayList<>();
            List<SavedPlacement> cellSaved = new ArrayList<>();
            // v12: donors whose move PASSED the search gates but failed at APPLY
            // (room-busy guard) or in the oracle. The search is deterministic: without
            // the exclusion the next hour re-picked the same doomed best donor and
            // burned the whole cell on it (notte-4sep: moves=0 on every occasion
            // while viable donors were measured on the delivered state). Coupled with
            // the slideH1 search gate: the gate keeps the exclusions rare, the
            // exclusion is the safety net for every OTHER apply-time failure.
            // Per-CELL scope on purpose: a donor doomed for this landing may serve
            // another cell (pinned by test on_crossCellDonorReuse).
            Set<Lesson> excluded = new HashSet<>();
            hourLoop:
            for (int h = 0; h < quota && moves < MAX_MOVES_PER_PASS; h++) {
                Move move;
                while ((move = bestMove(schedule, occ, target, floors, rej, excluded)) != null) {
                    // v8 PER-MOVE ORACLE: snapshot the move's affected lessons, apply,
                    // check the hard-conflict count, revert if it worsened.
                    long hardBefore = occ.hardConflictCount();
                    var affected = affectedLessons(move, occ);
                    var savedPlacements = savePlacements(affected);
                    if (!applyMove(move, occ, moveTimeslot)) {
                        rej.roomBusy++;
                    } else if (occ.hardConflictCount() > hardBefore) {
                        restorePlacements(affected, savedPlacements, occ);
                        reverted++;
                        rej.oracleRevert++;
                    } else {
                        cellAffected.addAll(affected);
                        cellSaved.addAll(savedPlacements);
                        moves++;
                        placed++;
                        continue hourLoop;
                    }
                    excluded.add(move.donor());
                }
                // v13 chain (the measured wall's cure, three-for-three samples 6/09):
                // when the plain search found no viable donor, the near-miss pool is
                // donors that pass every gate except teacherBusy-at-landing. The
                // blocker sitting in the landing slot (another group's lesson with the
                // donor's teacher) donates its own hour to ITS group's quota-headroom
                // block-end, freeing the slot; the donor lands there. Inert wherever
                // the plain path works (trigger is the plain pool's exhaustion).
                // Review C2: the compound's affected placements join the per-cell
                // atomicity ledger - a partial cell fill rolls the chain back too.
                if (tryChain(schedule, occ, target, floors, rej, moveTimeslot,
                        cellAffected, cellSaved)) {
                    moves++;
                    placed++;
                    continue hourLoop;
                }
                break;   // donor pool exhausted for this cell
            }
            int tolerance = SchoolScheduleConstraintProvider.quotaFillTolerance(target.group.name);   // H32's own
            if (!target.partial() && placed > 0 && placed < quota - tolerance) {
                restorePlacements(cellAffected, cellSaved, occ);
                moves -= placed;
                placed = 0;
            }
            if (placed > 0) cells++;
        }
        LOG.info("SurplusHarvest (TASK-577): moves=" + moves + " cells=" + cells
                + " reverted=" + reverted
                + " REJECTS: " + rej.breakdown());
        return new Result(moves, cells, rej.total() + reverted, rej.breakdown());
    }

    /** The lessons touched by a move (the donor + its slide followers). */
    private static List<Lesson> affectedLessons(Move move, Occupancy occ) {
        List<Lesson> affected = new ArrayList<>();
        affected.add(move.donor());
        affected.addAll(followersOf(occ, move.donor()));
        return affected;
    }

    /**
     * v13 chain: the two-level harvest. Returns true when a compound (blocker
     * relocation + donor landing) APPLIED and passed the oracle. The near-miss
     * scan reuses the plain path's gate PRIMITIVES in the same order (border,
     * slide, slideH1, h14, h7, h33 floor, fresh range, groupBusy) minus the
     * teacherBusy gate, which is the wall this cures: any new plain gate must be
     * mirrored here (the primitives are shared; the composition is duplicated by
     * design and pinned by the chain tests).
     */
    private static boolean tryChain(SchoolSchedule schedule, Occupancy occ, Target target,
            Map<String, Integer> floors, Rejects rej, BiConsumer<Lesson, Timeslot> moveTimeslot,
            List<Lesson> cellAffected, List<SavedPlacement> cellSaved) {
        Timeslot landing = firstWindowOpenSlot(schedule, target,
                openSlotsOf(schedule, occ, target));
        if (landing == null) return false;
        // H11 gate (review N1: mirror the plain path's hoisted check)
        int targetDayHours = occ.groupHoursOn(target.group, target.date);
        if (target.group.maxHoursPerDay != null
                && targetDayHours + 1 > target.group.maxHoursPerDay) return false;
        List<Lesson> donors = donorCandidates(schedule, occ, target.group);
        DailyActivityQuota targetRow = quotaRowOf(schedule, target);
        if (targetRow != null) {
            donors.removeIf(d -> d.course != null && d.course.studentGroup != null
                    && !DailyActivityQuota.permits(targetRow, DailyActivityQuota.isQuotaBoundLabSubject(
                            d.course.studentGroup.name, d.course.subject)));
        }
        for (Lesson donor : donors) {
            if (donor.course == null || donor.course.teacher == null
                    || donor.course.studentGroup == null) continue;
            if (TimeslotSequence.slotIndexOf(donor.timeslot) == 0) continue;
            List<Lesson> followers = followersOf(occ, donor);
            if (!canSlideUp(occ, donor, followers)) continue;
            if (!followerSlideH1Free(occ, donor, followers)) continue;
            if (target.group.startDate != null && target.date.isBefore(target.group.startDate)) continue;
            int tHours = occ.teacherHoursOn(donor.course.teacher, target.date);
            Integer maxTRaw = donor.course.teacher.maxDailyHours;
            int maxT = maxTRaw != null ? maxTRaw : 6;
            if (maxT > 0 && tHours + 1 > maxT) continue;
            Integer floor = floors.get(donor.course.studentGroup.name);
            if (floor != null && floor > 0
                    && occ.nonPseudoGroupHoursOn(donor.course.studentGroup, donor.timeslot.getDate()) - 1 < floor) continue;
            if (!isInValueRange(donor.getAvailableTimeslotsFresh(), landing)) continue;
            if (occ.groupBusy(target.group, landing)) continue;
            if (!occ.teacherBusy(donor.course.teacher, landing)) continue;   // not blocked: the plain path owns it
            rej.chainNearMiss++;
            // Review C3: iterate a copy - chainApply mutates the live slot list on
            // its failure path (blocker moved then reverted), which would CME here.
            for (Lesson blocker : List.copyOf(occ.lessonsAt(landing))) {
                if (blocker.course == null || blocker.course.teacher == null
                        || blocker.course.studentGroup == null
                        || !blocker.course.teacher.id.equals(donor.course.teacher.id)) continue;
                if (blocker.course.studentGroup.id.equals(target.group.id)) {
                    rej.chainBlkSameGroup++;
                    continue;
                }
                // Review C1: the blocker extraction runs the SAME slide discipline
                // as a donor (its followers slide up: the day keeps its start and
                // contiguity; the raw move would open H17/H31 that the compound
                // oracle - H1/H3 only - cannot see).
                List<Lesson> bFollowers = followersOf(occ, blocker);
                if (!canSlideUp(occ, blocker, bFollowers)) {
                    rej.chainBlkSlide++;
                    continue;
                }
                if (!followerSlideH1Free(occ, blocker, bFollowers)) {
                    rej.chainBlkSlideH1++;
                    continue;
                }
                if (chainApply(schedule, occ, target, donor, blocker, floors, landing, moveTimeslot,
                        cellAffected, cellSaved)) {
                    rej.chain++;
                    return true;
                }
                rej.chainFail++;
            }
        }
        return false;
    }

    /**
     * The compound apply: relocate the blocker to its own group's quota-headroom
     * block-end (H17/H31-safe by construction: adjacent extension, never the day
     * start; H1 by the free-teacher check; H2 by the room guard; H32 because the
     * blocker's day is over-quota - it donates - and the landing day has
     * headroom), then land the donor through the plain apply. Improve-or-noop on
     * the FULL compound; either both stick or both revert.
     */
    private static boolean chainApply(SchoolSchedule schedule, Occupancy occ, Target target,
            Lesson donor, Lesson blocker, Map<String, Integer> floors, Timeslot landing,
            BiConsumer<Lesson, Timeslot> moveTimeslot,
            List<Lesson> cellAffected, List<SavedPlacement> cellSaved) {
        var hGroup = blocker.course.studentGroup;
        Timeslot blockerLanding = null;
        // v13.1 SAME-DATE TAIL-SWAP first (the attempt-3 cure: slide-clean blockers
        // with no cross-date headroom): the blocker exits the landing slot, its
        // followers slide up, and the blocker takes the day's TAIL - the day's
        // lesson SET is identical, so H17/H31/H32/H33 of H are preserved BY
        // CONSTRUCTION (no hour counts change: the quota/floor gates below apply
        // only to the cross-date donation). The set invariance needs the day
        // CONTIGUOUS from the blocker's slot down: on a gapped day the slide
        // would shift the whole block and change the set (H31), so gap days go
        // straight to the cross-date family.
        Set<Lesson> movers = new HashSet<>();
        movers.add(blocker);
        movers.addAll(followersOf(occ, blocker));
        Timeslot tail = null;
        int blockerSlot = TimeslotSequence.slotIndexOf(blocker.timeslot);
        boolean contiguous = true;
        for (Lesson l : occ.lessonsOf(hGroup, blocker.timeslot.getDate())) {
            if (l == blocker || l.timeslot == null) continue;
            int slot = TimeslotSequence.slotIndexOf(l.timeslot);
            if (tail == null || slot > TimeslotSequence.slotIndexOf(tail)) tail = l.timeslot;
        }
        if (tail != null) {
            // every slot between the blocker's and the tail must carry an H lesson
            Set<Integer> occupied = new HashSet<>();
            for (Lesson l : occ.lessonsOf(hGroup, blocker.timeslot.getDate())) {
                if (l == blocker || l.timeslot == null) continue;
                occupied.add(TimeslotSequence.slotIndexOf(l.timeslot));
            }
            for (int s = blockerSlot + 1; s < TimeslotSequence.slotIndexOf(tail); s++) {
                if (!occupied.contains(s)) { contiguous = false; break; }
            }
        }
        if (contiguous && tail != null
                && isInValueRange(blocker.getAvailableTimeslotsFresh(), tail)
                && (blocker.room == null || !occ.roomBusy(blocker.room, tail))
                && !occ.teacherBusyExcept(blocker.course.teacher, tail, movers)) {
            blockerLanding = tail;
        }
        if (blockerLanding == null) {
            // v13 cross-date donation: the blocker's own day must be over-quota
            // (it donates an hour of H)...
            if (occ.nonPseudoGroupHoursOn(hGroup, blocker.timeslot.getDate())
                    <= effectiveQuota(hGroup.id, blocker.timeslot.getDate())) return false;
            // ...and must stay at or above its H33 floor after the donation (review I1:
            // the compound oracle counts H1/H3 only - H33 would ship silently)
            Integer hFloor = floors.get(hGroup.name);
            if (hFloor != null && hFloor > 0
                    && occ.nonPseudoGroupHoursOn(hGroup, blocker.timeslot.getDate()) - 1 < hFloor) return false;
            // v13 extend-only: the blocker lands right after the LAST lesson of one of
        // H's OTHER days with quota headroom (adjacent extension: H17 keeps the
        // day contiguous, H31 keeps the start, the hour count moves within H).
        // Simplify-B hoist: iterate the group's OWN dates with lessons (the live
        // hours map), chronological order preserved - the grid scan paid 2xO(N)
        // per candidate date including every lesson-less date.
        List<LocalDate> candidateDates = new ArrayList<>(occ.datesWithLessons(hGroup));
        candidateDates.sort(Comparator.naturalOrder());
        for (LocalDate d2 : candidateDates) {
            if (d2.equals(blocker.timeslot.getDate())) continue;
            if (occ.nonPseudoGroupHoursOn(hGroup, d2) >= effectiveQuota(hGroup.id, d2)) continue;
            // review I1: the blocker's teacher must stay within H7 on the landing date
            int bHours = occ.teacherHoursOn(blocker.course.teacher, d2);
            Integer bMaxRaw = blocker.course.teacher.maxDailyHours;
            int bMax = bMaxRaw != null ? bMaxRaw : 6;
            if (bMax > 0 && bHours + 1 > bMax) continue;
            Lesson last = null;
            for (Lesson l : occ.lessonsOf(hGroup, d2)) {
                if (l.timeslot == null) continue;
                if (last == null || TimeslotSequence.slotIndexOf(l.timeslot)
                        > TimeslotSequence.slotIndexOf(last.timeslot)) last = l;
            }
            if (last == null) continue;
            Timeslot next = occ.nextSlotOfSameDate(last.timeslot);
            if (next == null) continue;
            if (occ.groupBusy(hGroup, next) || occ.teacherBusy(blocker.course.teacher, next)) continue;
            if (!isInValueRange(blocker.getAvailableTimeslotsFresh(), next)) continue;
            if (blocker.room != null && occ.roomBusy(blocker.room, next)) continue;
            blockerLanding = next;
            break;
        }
        }
        if (blockerLanding == null) return false;
        long hardBefore = occ.hardConflictCount();
        // Review C1: the blocker goes through the SAME applyMove as any donor: its
        // followers slide up (the day keeps start and contiguity), rooms guarded.
        Move blockerMove = new Move(blocker, blocker.timeslot, blockerLanding);
        var bAffected = affectedLessons(blockerMove, occ);
        var bSaved = savePlacements(bAffected);
        if (!applyMove(blockerMove, occ, moveTimeslot)) return false;
        // simplify-A #3: the landing tryChain already gate-checked - the blocker
        // vacating it belongs to ANOTHER group, so the target's open-slot set is
        // unchanged; recomputing could only diverge from the gated slot.
        Move donorMove = new Move(donor, donor.timeslot, landing);
        var affected = affectedLessons(donorMove, occ);
        var saved = savePlacements(affected);
        if (!applyMove(donorMove, occ, moveTimeslot) || occ.hardConflictCount() > hardBefore) {
            // compound revert: the donor (if applied) and the blocker both go back
            restorePlacements(affected, saved, occ);
            restorePlacements(bAffected, bSaved, occ);
            return false;
        }
        // review C2: the compound joins the per-cell atomicity ledger
        cellAffected.addAll(bAffected);
        cellSaved.addAll(bSaved);
        cellAffected.addAll(affected);
        cellSaved.addAll(saved);
        return true;
    }

    private record SavedPlacement(Lesson lesson, Timeslot timeslot) {}

    private static List<SavedPlacement> savePlacements(List<Lesson> lessons) {
        List<SavedPlacement> saved = new ArrayList<>();
        for (Lesson l : lessons) {
            saved.add(new SavedPlacement(l, l.timeslot));
        }
        return saved;
    }

    private static void restorePlacements(List<Lesson> lessons, List<SavedPlacement> saved,
                                           Occupancy occ) {
        for (int i = 0; i < saved.size(); i++) {
            SavedPlacement sp = saved.get(i);
            if (sp.lesson().timeslot != null && !sp.lesson().timeslot.equals(sp.timeslot())) {
                // Restore the lesson's timeslot in the occupancy index.
                occ.move(sp.lesson(), sp.lesson().timeslot, sp.timeslot());
                sp.lesson().timeslot = sp.timeslot();
            }
        }
    }

    // ── internal types ───────────────────────────────────────────────────────────

    /** A declared-empty (group, date) cell that needs filling. */
    record Target(StudentGroup group, LocalDate date, int quota, boolean partial) {}

    /** One harvest move: a specific donor lesson to a specific target slot. The
     *  donor's followers (same group, same date) slide UP one slot each in
     *  applyMove, closing the hole (the EDF discipline of EmptyDayFinisher). */
    record Move(Lesson donor, Timeslot from, Timeslot to) {}

    // ── target discovery ─────────────────────────────────────────────────────────

    private static List<Target> findEmptyDeclaredDays(SchoolSchedule schedule) {
        // TASK-589: the cell SEMANTIC (scope exclusions, aula+lab base) lives ONCE
        // in HarvestNeighborhood, shared with the LS stream. This wrapper keeps the
        // pass's historical per-timeslot emission (a cell appears once per grid slot
        // of its date) and the TEST-ONLY skip-pre-start knob.
        List<Target> targets = new ArrayList<>();
        for (var cell : HarvestNeighborhood.emptyDeclaredCells(schedule)) {
            if ("true".equalsIgnoreCase(System.getProperty(SKIP_PRE_START_FLAG))
                    && cell.group().startDate != null && cell.date().isBefore(cell.group().startDate)) {
                continue;
            }
            for (Timeslot ts : schedule.getTimeslots()) {
                if (ts.getDate().equals(cell.date())) {
                    targets.add(new Target(cell.group(), cell.date(), cell.quota(), false));
                }
            }
        }
        // TASK-577 8/09 notte: H32 under-quota cells (placed > 0 but < quota):
        // the cure's box creates deficits the LS leaves behind. Any fill is
        // strictly improving, so these targets skip the atomicity rollback.
        if (Boolean.getBoolean(H32_TARGETS_FLAG)) {
            Map<Long, Map<LocalDate, Integer>> hoursByGroupDate = new HashMap<>();
            for (Lesson l : schedule.getLessons()) {
                if (l.timeslot == null || l.course == null || l.course.studentGroup == null
                        || l.course.teacher != null && l.course.teacher.pseudo) continue;
                hoursByGroupDate
                        .computeIfAbsent(l.course.studentGroup.id, k -> new HashMap<>())
                        .merge(l.timeslot.getDate(), 1, Integer::sum);
            }
            for (StudentGroup group : schedule.getStudentGroups()) {
                if (group == null || group.id == null
                        || SchoolScheduleConstraintProvider.isQuotaFillExcluded(group.name)) continue;
                for (Timeslot ts : schedule.getTimeslots()) {
                    var row = SchoolSchedule.getExpectedHoursRow(group.id, ts.getDate());
                    if (row == null) continue;
                    int quota = row.aulaHours + row.labHours;
                    if (quota <= 0) continue;
                    int placed = hoursByGroupDate
                            .getOrDefault(group.id, Map.of())
                            .getOrDefault(ts.getDate(), 0);
                    if (placed > 0 && placed < quota) {
                        targets.add(new Target(group, ts.getDate(), quota - placed, true));
                    }
                }
            }
        }
        targets.sort(Comparator.comparing(Target::date, Comparator.nullsLast(Comparator.naturalOrder())));
        return targets;
    }

    // ── donor + landing search ───────────────────────────────────────────────────

    private static Move bestMove(SchoolSchedule schedule, Occupancy occ, Target target,
            Map<String, Integer> floors, Rejects rej, Set<Lesson> excluded) {
        // All UNPINNED lessons of the group on OVER-quota days (any distance).
        List<Lesson> donors = donorCandidates(schedule, occ, target.group);
        if (donors.isEmpty()) { rej.noDonors++; return null; }

        // Landing history: v0.1 pre-computed ONE slot (donors whose teacher was busy
        // there were silently discarded: 2480/2480 rejections); v0.2 widened to ALL
        // the day's open slots; v9/v10 (03/09) narrowed back to the window's FIRST
        // open slot only - a landing shifted past the day start opens an H31
        // violation on the freshly-filled day (the 22:40 cycle reverted with H31
        // 0->-26), so a blocked donor now leaves the cell to the next hour's search.
        List<Timeslot> openSlots = openSlotsOf(schedule, occ, target);
        if (openSlots.isEmpty()) { rej.noSlots++; return null; }

        // H31 anchor, hoisted before the donor loop: the window's first open slot
        // does not depend on the donor (one resolve per bestMove call, not per donor)
        Timeslot windowOpenLanding = firstWindowOpenSlot(schedule, target, openSlots);

        // H11 is slot-independent (counted per day, not per slot).
        int targetDayHours = occ.groupHoursOn(target.group, target.date);
        if (target.group.maxHoursPerDay != null
                && targetDayHours + 1 > target.group.maxHoursPerDay) { rej.h11++; return null; }

        Lesson best = null;
        Timeslot bestFrom = null;
        Timeslot bestLanding = null;
        int bestScore = Integer.MIN_VALUE;
        // v11 QUOTA-SHAPE pre-filter (cheap, first): the diagnosis of the v10 cycle
        // showed most rejections burning the whole gate chain on a mismatch the
        // quota row alone decides: G08's surplus is mostly LAB lessons and its
        // empty May-June days declare R04 / lab 0 - L1-bis forbids the landing
        // per-slot, but only AFTER border/slide/h7/h33/floor have all passed.
        // Same decision method as the VRP: DailyActivityQuota.permits.
        // v12.2 (review find, 6/09): the quota map is FC-sheet-only - for the
        // in-scope NON-quota groups the row is NULL and permits(null) rejects
        // EVERY donor (the shape-starved in-scope cells of the bench: the pass
        // was de facto G13-only). The VRP cortocircuits empty-map to
        // UNRESTRICTED (Lesson.isSlotLegalFresh's own shape gate runs only when
        // the group's quota map is non-empty): mirror it - no row, no shape gate.
        DailyActivityQuota targetRow = quotaRowOf(schedule, target);
        if (targetRow != null) {
            donors.removeIf(donor -> {
                if (donor.course == null || donor.course.studentGroup == null) return false;
                if (!DailyActivityQuota.permits(targetRow, DailyActivityQuota.isQuotaBoundLabSubject(
                        donor.course.studentGroup.name, donor.course.subject))) {
                    rej.shape++;
                    return true;
                }
                return false;
            });
        }
        if (donors.isEmpty()) { return null; }

        // space-insensitive match; read via ENV too: system properties do not cross
        // surefire's forked JVM in this harness (PLANNER_TEST_* envs do)
        String traceGroup = System.getProperty("surplus.trace");
        if (traceGroup == null) traceGroup = System.getenv("SURPLUS_TRACE");
        boolean trace = traceGroup != null && target.group != null && target.group.name != null
                && target.group.name.replace(" ", "").equalsIgnoreCase(traceGroup.replace(" ", ""));
        int traceShown = 0; final int traceCap = Integer.getInteger("surplus.trace.cap", 3);

        for (Lesson donor : donors) {
            if (excluded.contains(donor)) continue;   // v12: apply-failed for this cell
            Timeslot from = donor.timeslot;
            // v0.3: border check REPLACED by the EDF slide discipline: a mid-block donor
            // is eligible if its followers can each slide UP one slot (closing the hole).
            // The donor's H17 is preserved by the slide, not by position. The FIRST hour
            // of the donor day never moves (H31-safe: only lessons AFTER the donor shift).
            if (TimeslotSequence.slotIndexOf(from) == 0) {
                rej.border++; if (trace && traceShown++ < traceCap) traceRej("slot0", donor, target);
                continue;
            }
            List<Lesson> followers = followersOf(occ, donor);
            if (!canSlideUp(occ, donor, followers)) {
                rej.border++; if (trace && traceShown++ < traceCap) traceRej("slide", donor, target);
                continue;
            }
            // v12 slide-companion H1: canSlideUp proves the destination group-free and
            // VRP-fresh, but the follower's TEACHER may teach another group in the slot
            // it slides INTO; nobody rejected that before the apply, the oracle caught
            // it after (reverted=208-736 per occasion on the notte-4sep cycle), and the
            // doomed best donor got re-picked forever. Same H1 semantics as the oracle:
            // pseudo teachers never conflict, movers vacate their own cells.
            if (!followerSlideH1Free(occ, donor, followers)) {
                rej.slideH1++; if (trace && traceShown++ < traceCap) traceRej("slide-h1", donor, target);
                continue;
            }
            // H14: start date. v0.5 fix (TASK-577): null startDate = NO restriction
            // (the test fixture doesn't populate it; the production DataGenerator does).
            // Only reject when BOTH startDate is set AND the target date is before it.
            if (target.date == null || target.group == null) { rej.h14++; continue; }
            if (target.group.startDate != null
                    && target.date.isBefore(target.group.startDate)) { rej.h14++; continue; }
            // H7: max teacher daily hours at the landing date (same date for all slots).
            if (donor.course.teacher != null) {
                int tHours = occ.teacherHoursOn(donor.course.teacher, target.date);
                Integer maxTRaw = donor.course.teacher.maxDailyHours;
                int maxT = maxTRaw != null ? maxTRaw : 6;
                if (maxT > 0 && tHours + 1 > maxT) {
                    rej.h7++; if (trace && traceShown++ < traceCap) traceRej("h7", donor, target);
                    continue;
                }
            }
            // v9/v10 H33: the donor must stay at or above its hard day floor after
            // giving up this hour, counted on H33's OWN population (non-Stage hours:
            // pseudo-teacher hours are excluded by the constraint, and the plain
            // group count would let a Stage-carrying day donate below the floor)
            Integer floor = floors.get(donor.course.studentGroup.name);
            if (floor != null && floor > 0
                    && occ.nonPseudoGroupHoursOn(donor.course.studentGroup, donor.timeslot.getDate()) - 1 < floor) {
                rej.h33++; if (trace && traceShown++ < traceCap) traceRej("h33-floor", donor, target);
                continue;
            }
            int surplusAfter = surplusAfter(occ, donor);
            int fromWeekday = from.getDayOfWeek().getValue();
            int fromSlot = TimeslotSequence.slotIndexOf(from);
            // Hoisted once per donor (E1): the range does not depend on the landing.
            // Scope discipline: per-donor-per-call ONLY - never memoize further,
            // the fresh range gains the lesson's current slot after a move.
            List<Timeslot> freshRange = donor.getAvailableTimeslotsFresh();
            // v9/v10 H31: the landing must be the target day's FIRST CALENDAR-OPEN
            // slot (H31 anchors the day start to the window's first open slot, not to
            // grid slot 0: on a window-[1..7] day, 9:00 IS the compliant start). The
            // first open slot at or after the window start; if that slot is not legal
            // for this lesson the CELL is rejected - the hour is never shifted to a
            // later slot to dodge a VRP rule (the 22:40 revert: H31 0->-26).
            Timeslot landing = windowOpenLanding;
            if (landing == null) { rej.h31++; continue; }
            // VRP guard (TASK-577 v6): FRESH range, never the cached one (stale-cache
            // crash of the 2026-09-02 cycle).
            if (!isInValueRange(freshRange, landing)) {
                rej.h31++; if (trace && traceShown++ < traceCap) traceRej("h31-vrp-first-slot", donor, target);
                continue;
            }
            // H1/H3: the teacher and the group must be free at THIS landing.
            if (donor.course.teacher != null
                    && occ.teacherBusy(donor.course.teacher, landing)) {
                rej.teacherBusy++; if (trace && traceShown++ < traceCap) traceRej("teacherBusy-first-slot", donor, target);
                continue;
            }
            if (occ.groupBusy(target.group, landing)) { rej.groupBusy++; continue; }
            boolean sameCell = fromWeekday == landing.getDayOfWeek().getValue()
                    && fromSlot == TimeslotSequence.slotIndexOf(landing);
            int score = (sameCell ? 1_000_000 : 0) + surplusAfter;
            if (score > bestScore) {
                bestScore = score;
                best = donor;
                bestFrom = from;
                bestLanding = landing;
            }
        }
        if (best == null) return null;
        return new Move(best, bestFrom, bestLanding);
    }

    /** The donor's followers: same group, same date, in slots AFTER the donor. */
    private static List<Lesson> followersOf(Occupancy occ, Lesson donor) {
        List<Lesson> followers = new ArrayList<>();
        if (donor.timeslot == null || donor.course == null
                || donor.course.studentGroup == null) return followers;
        LocalDate date = donor.timeslot.getDate();
        int donorSlot = TimeslotSequence.slotIndexOf(donor.timeslot);
        List<Lesson> dayLessons = occ.lessonsOf(donor.course.studentGroup, date);
        for (Lesson l : dayLessons) {
            if (l == donor || l.timeslot == null) continue;
            int slot = TimeslotSequence.slotIndexOf(l.timeslot);
            if (slot > donorSlot) followers.add(l);
        }
        followers.sort(Comparator.comparing(l -> TimeslotSequence.slotIndexOf(l.timeslot)));
        return followers;
    }

    /** True when every follower can slide UP one slot (each target slot is free for the group). */
    private static boolean canSlideUp(Occupancy occ, Lesson donor, List<Lesson> followers) {
        if (donor.timeslot == null) return false;
        LocalDate date = donor.timeslot.getDate();
        for (Lesson f : followers) {
            if (f.timeslot == null) return false;
            Timeslot up = occ.previousSlotOfSameDate(f.timeslot);
            if (up == null) return false;
            // VRP guard for the follower's slide target (TASK-577 v6): the follower
            // must be able to legally sit in the slot above its current position
            // (FRESH range: stale-cache crash of the 2026-09-02 cycle).
            if (!isInValueRange(f.getAvailableTimeslotsFresh(), up)) return false;
            // The follower slides into the PREVIOUS slot: it must be free for the group
            // (it's the donor's old slot for the first follower, or the previous
            // follower's old slot for the rest: always inside the day, always free
            // after the extraction). We check the CURRENT state: the slot above each
            // follower must be either the donor (about to leave) or another follower.
            if (!occ.groupBusy(donor.course.studentGroup, up)
                    && !isSameGroupAt(occ, donor.course.studentGroup, up, f)
                    && !isDonorAt(up, donor)) {
                // The slot above is NOT the donor and NOT a follower: it's either free
                // (fine: the slide creates a gap that closes) or another group (fine).
                // For simplicity: any slot that's not occupied by a DIFFERENT group is OK.
                continue; // OK
            }
        }
        return true;
    }

    /**
     * v12: every follower's teacher must be free at the slot it slides INTO. The
     * moving lessons (donor + followers) vacate their cells as the slide runs, so
     * their current presence at a destination is not a conflict; any OTHER lesson
     * with the follower's teacher there is the H1 the oracle used to catch late.
     * Runs AFTER canSlideUp, which has already rejected the null-destination
     * shapes this loop tolerates. This is the same "H1 in the post-move state,
     * movers excluded" invariant as {@link EmptyDayFinisher} planSlides and
     * {@link OneOffFinisher} teacherBusyExcludingAll (per-pass local copies by
     * repo policy: change all three together).
     */
    private static boolean followerSlideH1Free(Occupancy occ, Lesson donor, List<Lesson> followers) {
        Set<Lesson> moving = new HashSet<>();
        moving.add(donor);
        moving.addAll(followers);
        for (Lesson f : followers) {
            if (f.course == null || f.course.teacher == null) continue;
            Timeslot up = occ.previousSlotOfSameDate(f.timeslot);
            if (up == null) continue;   // canSlideUp already rejected this shape
            if (occ.teacherBusyExcept(f.course.teacher, up, moving)) return false;
        }
        return true;
    }

    private static boolean isSameGroupAt(Occupancy occ, StudentGroup group, Timeslot ts, Lesson exclude) {
        List<Lesson> lessons = occ.lessonsAt(ts);
        for (Lesson l : lessons) {
            if (l == exclude) continue;
            if (l.course != null && l.course.studentGroup != null
                    && l.course.studentGroup.id != null
                    && group.id != null
                    && l.course.studentGroup.id.equals(group.id)) return true;
        }
        return false;
    }

    private static boolean isDonorAt(Timeslot ts, Lesson donor) {
        return donor.timeslot != null && donor.timeslot.equals(ts);
    }

    /** True when the landing is in the given FRESH value range (VRP guard). The
     * range must be computed via getAvailableTimeslotsFresh(), never the cached
     * getter: the cache is populated once and never invalidated, so after the
     * statics are republished (windows, quotas, pins) it can permit cells the live
     * range forbids; a move accepted on the stale cache crashes the NEXT solver
     * start with assertValueRange (cycle 2026-09-02, twice). */
    private static boolean isInValueRange(List<Timeslot> range, Timeslot landing) {
        if (landing == null) return false;
        // Empty VRP = no restriction (the simple test fixture doesn't populate the
        // statics; in production they always are). The real check stays the
        // constraint provider's H26/H20 when the solution is scored.
        if (range.isEmpty()) return true;
        return range.contains(landing);
    }

    /** v11-p2 diagnostic (surplus.trace=<groupName>): which gate rejects the donor. */
    private static void traceRej(String gate, Lesson donor, Target target) {
        System.out.println("[SH-trace] " + target.group.name + " " + target.date
                + " donor=" + (donor.course != null && donor.course.subject != null ? donor.course.subject : "?")
                + "/" + (donor.course != null && donor.course.teacher != null ? donor.course.teacher.name : "?")
                + " from=" + donor.timeslot.getDate() + " REJECTED-BY " + gate);
    }

    /** v11: the target day's quota row (null when the group has none). */
    private static DailyActivityQuota quotaRowOf(SchoolSchedule schedule, Target target) {
        if (target == null || target.group == null || target.date == null || target.group.id == null) return null;
        return SchoolSchedule.getDailyActivityQuotasForGroup(target.group.id).get(target.date);
    }

    /** The first target-day slot at or after the group's calendar window start
     * (H31's own anchor), or null when none exists (closed day / all before it). */
    private static Timeslot firstWindowOpenSlot(SchoolSchedule schedule, Target target, List<Timeslot> openSlots) {
        int[] window = SchoolSchedule.getGroupCalendarWindow(
                target.group.id, target.date.getDayOfWeek(), target.date);
        int minOpen = window.length > 1 ? window[1] : 0;   // [totalOpen, minOpen, maxOpen]
        for (Timeslot ts : openSlots) {
            if (TimeslotSequence.slotIndexOf(ts) >= minOpen) return ts;
        }
        return null;
    }

    /** All the target day's open slots (group free + calendar open). */
    private static List<Timeslot> openSlotsOf(SchoolSchedule schedule, Occupancy occ, Target target) {
        List<Timeslot> daySlots = new ArrayList<>();
        for (Timeslot ts : schedule.getTimeslots()) {
            if (ts.getDate().equals(target.date)) daySlots.add(ts);
        }
        daySlots.sort(Comparator.comparing(t -> t.startDateTime));
        List<Timeslot> open = new ArrayList<>();
        for (Timeslot ts : daySlots) {
            if (!occ.groupBusy(target.group, ts)) open.add(ts);
        }
        return open;
    }

    private static List<Lesson> donorCandidates(SchoolSchedule schedule, Occupancy occ, StudentGroup group) {
        // TASK-589 review I2: delegated to the shared enumeration (fonte unica with
        // the LS stream: the drift risk was real - the copies had already diverged
        // in landing-order discipline before this delegation).
        return HarvestNeighborhood.donorLessons(schedule, group);
    }

    /** The first open slot of the target group's calendar window on the target date. */
    private static int surplusAfter(Occupancy occ, Lesson donor) {
        if (donor.timeslot == null || donor.course == null || donor.course.studentGroup == null) return 0;
        LocalDate date = donor.timeslot.getDate();
        int hours = occ.groupHoursOn(donor.course.studentGroup, date);
        // v9: NET quota (TASK-424): the raw sheet quota minus the day's authoritative
        // pinned hours - the same target H32 reads. The 22:40 cycle reverted with
        // H32 0->-4 exactly because this guard counted the raw quota.
        int quota = effectiveQuota(donor.course.studentGroup.id, date);
        return hours - 1 - quota; // after removing this lesson
    }

    /** The sheet quota NET of the day's authoritative pinned hours (TASK-424 shape:
     * the raw quota keeps the scope decision, the net one decides the deficit).
     * v12.3: aula+lab base like the target side (getExpectedHours sums stage: a
     * mixed day above its H32 quota must still read over-quota and donate). */
    private static int effectiveQuota(Long groupId, LocalDate date) {
        var row = SchoolSchedule.getExpectedHoursRow(groupId, date);
        if (row == null) return -1;
        int quota = row.aulaHours + row.labHours;
        if (quota <= 0) return quota;
        return Math.max(0, quota - SchoolSchedule.getAuthoritativePinnedHours(groupId, date));
    }

    private static boolean applyMove(Move move, Occupancy occ,
                                     BiConsumer<Lesson, Timeslot> moveTimeslot) {
        Lesson donor = move.donor();
        // voice-2 (notte 4/09): both in-cycle firings run on the ROOMED grid
        // (post-B-ROOM), so the rooms travel with their lessons and NO room stage
        // follows to repair them. A busy room at any slide destination can no
        // longer be nulled away (that shipped H29); the move is REJECTED instead
        // and the room-free candidates still apply. Over-rejects the golden-
        // practiced guest pairs that legally share a room (the safe direction).
        List<Lesson> followers = followersOf(occ, donor);
        if (donor.room != null && occ.roomBusy(donor.room, move.to())) {
            return false;
        }
        for (Lesson f : followers) {
            Timeslot up = occ.previousSlotOfSameDate(f.timeslot);
            if (f.room != null && up != null && occ.roomBusy(f.room, up)) {
                return false;
            }
        }
        // v0.3: slide the donor's followers UP one slot each BEFORE moving the donor
        // (the first follower takes the donor's old slot, the second takes the first's
        // old slot, etc: the block compacts and the hole closes).
        for (int i = 0; i < followers.size(); i++) {
            Lesson f = followers.get(i);
            Timeslot up = occ.previousSlotOfSameDate(f.timeslot);
            if (up != null) {
                Timeslot oldTs = f.timeslot;
                moveTimeslot.accept(f, up);
                occ.move(f, oldTs, up);
            }
        }
        moveTimeslot.accept(donor, move.to());
        occ.move(donor, move.from(), move.to());
        return true;
    }

    // ── live occupancy index ─────────────────────────────────────────────────────

    static final class Occupancy {
        private final Map<LocalDate, Map<Integer, Timeslot>> slotByDateIndex = new HashMap<>();
        private final Map<Timeslot, List<Lesson>> bySlot = new HashMap<>();
        private final Map<Long, Map<LocalDate, Integer>> groupHoursByDate = new HashMap<>();
        private final Map<Long, Map<LocalDate, Integer>> teacherHoursByDate = new HashMap<>();

        Occupancy(SchoolSchedule schedule) {
            for (Timeslot ts : schedule.getTimeslots()) {
                slotByDateIndex
                        .computeIfAbsent(ts.getDate(), k -> new TreeMap<>())
                        .put(TimeslotSequence.slotIndexOf(ts), ts);
            }
            for (Lesson l : schedule.getLessons()) {
                if (l.timeslot == null || l.course == null) continue;
                bySlot.computeIfAbsent(l.timeslot, k -> new ArrayList<>()).add(l);
                if (l.course.studentGroup != null) {
                    groupHoursByDate
                            .computeIfAbsent(l.course.studentGroup.id, k -> new HashMap<>())
                            .merge(l.timeslot.getDate(), 1, Integer::sum);
                }
                if (l.course.teacher != null) {
                    teacherHoursByDate
                            .computeIfAbsent(l.course.teacher.id, k -> new HashMap<>())
                            .merge(l.timeslot.getDate(), 1, Integer::sum);
                }
            }
        }

        boolean teacherBusy(com.school.planner.domain.Teacher t, Timeslot ts) {
            return teacherBusyExcept(t, ts, Set.of());
        }

        /** v12: {@link #teacherBusy} ignoring the lessons that are about to move away
         *  (the donor and the sliding followers each vacate a slide destination);
         *  pseudo teachers never conflict (the per-move oracle's own semantics - the
         *  only current caller with pseudo teachers filters them at the source). */
        boolean teacherBusyExcept(com.school.planner.domain.Teacher t, Timeslot ts, Set<Lesson> moving) {
            if (t == null || t.pseudo) return false;
            for (Lesson l : bySlot.getOrDefault(ts, List.of())) {
                if (moving.contains(l)) continue;
                if (l.course != null && l.course.teacher != null
                        && l.course.teacher.id != null && l.course.teacher.id.equals(t.id)) {
                    return true;
                }
            }
            return false;
        }

        boolean groupBusy(StudentGroup g, Timeslot ts) {
            if (g == null) return false;
            List<Lesson> lessons = bySlot.getOrDefault(ts, List.of());
            for (Lesson l : lessons) {
                if (l.course != null && l.course.studentGroup != null
                        && l.course.studentGroup.id != null && l.course.studentGroup.id.equals(g.id)) {
                    return true;
                }
            }
            return false;
        }

        boolean roomBusy(Room r, Timeslot ts) {
            return RoomAvailability.occupied(r, ts, bySlot.getOrDefault(ts, List.of()));
        }

        int groupHoursOn(StudentGroup g, LocalDate date) {
            if (g == null || g.id == null) return 0;
            return groupHoursByDate.getOrDefault(g.id, Map.of()).getOrDefault(date, 0);
        }

        /** v10 I1: the group's NON-pseudo (non-Stage/Esame) hours on the date -
         * H33's own population (the plain count includes pseudo hours the
         * constraint excludes, letting a Stage-carrying day donate below floor). */
        int nonPseudoGroupHoursOn(StudentGroup g, LocalDate date) {
            if (g == null || g.id == null) return 0;
            int count = 0;
            for (Lesson l : lessonsOf(g, date)) {
                if (l.course != null && l.course.teacher != null && l.course.teacher.pseudo) continue;
                count++;
            }
            return count;
        }

        int teacherHoursOn(com.school.planner.domain.Teacher t, LocalDate date) {
            if (t == null || t.id == null) return 0;
            return teacherHoursByDate.getOrDefault(t.id, Map.of()).getOrDefault(date, 0);
        }

        List<Lesson> lessonsOf(StudentGroup group, LocalDate date) {
            List<Lesson> result = new ArrayList<>();
            if (group == null || group.id == null) return result;
            for (Lesson l : bySlot.values().stream().flatMap(List::stream).toList()) {
                if (l.timeslot == null || l.course == null || l.course.studentGroup == null) continue;
                if (!l.course.studentGroup.id.equals(group.id)) continue;
                if (!l.timeslot.getDate().equals(date)) continue;
                result.add(l);
            }
            return result;
        }

        long hardConflictCount() {
            long count = 0;
            for (var entry : bySlot.entrySet()) {
                List<Lesson> lessons = entry.getValue();
                if (lessons.size() <= 1) continue;
                Map<Long, Integer> teacherCount = new HashMap<>();
                Map<Long, Integer> groupCount = new HashMap<>();
                for (Lesson l : lessons) {
                    if (l.course == null) continue;
                    if (l.course.teacher != null && l.course.teacher.id != null && !l.course.teacher.pseudo) {
                        teacherCount.merge(l.course.teacher.id, 1, Integer::sum);
                    }
                    if (l.course.studentGroup != null && l.course.studentGroup.id != null) {
                        groupCount.merge(l.course.studentGroup.id, 1, Integer::sum);
                    }
                }
                for (var tc : teacherCount.values()) {
                    if (tc > 1) count += tc - 1;
                }
                for (var gc : groupCount.values()) {
                    if (gc > 1) count += gc - 1;
                }
            }
            return count;
        }

        List<Lesson> lessonsAt(Timeslot ts) {
            return bySlot.getOrDefault(ts, List.of());
        }

        Timeslot previousSlotOfSameDate(Timeslot ts) {
            Map<Integer, Timeslot> index = slotByDateIndex.get(ts.getDate());
            if (index == null) return null;
            int current = TimeslotSequence.slotIndexOf(ts);
            return index.get(current - 1);
        }

            /** v13 chain hoist: the group's dates that carry lessons (live map). */
        java.util.Set<LocalDate> datesWithLessons(StudentGroup g) {
            if (g == null || g.id == null) return java.util.Set.of();
            return groupHoursByDate.getOrDefault(g.id, Map.of()).keySet();
        }

    /** v13 chain: the slot right AFTER ts on its date (the block-end extension). */
        Timeslot nextSlotOfSameDate(Timeslot ts) {
            Map<Integer, Timeslot> index = slotByDateIndex.get(ts.getDate());
            if (index == null) return null;
            int current = TimeslotSequence.slotIndexOf(ts);
            return index.get(current + 1);
        }

        void move(Lesson lesson, Timeslot from, Timeslot to) {
            List<Lesson> fromList = bySlot.get(from);
            if (fromList != null) fromList.remove(lesson);
            bySlot.computeIfAbsent(to, k -> new ArrayList<>()).add(lesson);
            if (lesson.course != null) {
                if (lesson.course.studentGroup != null && lesson.course.studentGroup.id != null) {
                    groupHoursByDate
                            .computeIfAbsent(lesson.course.studentGroup.id, k -> new HashMap<>())
                            .merge(from.getDate(), -1, Integer::sum);
                    groupHoursByDate
                            .computeIfAbsent(lesson.course.studentGroup.id, k -> new HashMap<>())
                            .merge(to.getDate(), 1, Integer::sum);
                }
                if (lesson.course.teacher != null && lesson.course.teacher.id != null) {
                    teacherHoursByDate
                            .computeIfAbsent(lesson.course.teacher.id, k -> new HashMap<>())
                            .merge(from.getDate(), -1, Integer::sum);
                    teacherHoursByDate
                            .computeIfAbsent(lesson.course.teacher.id, k -> new HashMap<>())
                            .merge(to.getDate(), 1, Integer::sum);
                }
            }
        }
    }
}
