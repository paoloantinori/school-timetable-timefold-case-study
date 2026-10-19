package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.util.SubjectNormalization;

/**
 * TASK-613 v0.6 (strada verso 0, figlio di TASK-612): the H17 teacher-locked
 * residual. v0.1 mechanized the round-1 census import-relay and the round-2 laptop
 * census measured it DEAD (every candidate eaten by H1/VRP); the round-2 blind
 * solution (21 moves, Java-arbitrated -3 -> 0) shows the real family:
 * ROTATION-WITH-PERMISSION. Per hole: the day's LAST lesson slides DOWN into the
 * hole (contiguity restored, count unchanged); when its teacher is busy at the
 * hole hour, the CONTENDER (that teacher's lesson in another group at that hour)
 * is freed by a CONTIGUOUS ROTATION of its own day (the insertion form: contender
 * to another hour of its day, the lessons between shift by one; span and count
 * preserved). The blind found its slack preferentially on the exempt groups
 * (1/G08, G14, outside the H32 scope) and needed one depth-4 cascade for the
 * G11 hole (v0.2 covers depth 1: rotation + slide; cascades are counted, not
 * attempted).
 *
 * <p>v0.6 (15/09): the TEACHER-AWARE symbolic DFS, ported from the bench-proven
 * prototype (task613-results/PROTOTIPO-CURA-teacher-aware-2026-09-15.md, -3 -> 0
 * m39-off / -6 -> -4 m39-on in banco): SYM-HOLE (no mover with the tail's teacher
 * lands on the hole cell), SYM-CODEST (no same-teacher co-destinations among
 * movers), true-blocker recursion (blanket batch exemption removed: an insertion
 * can DEPOSIT a lesson into the critical hour), real backtracking. The composite
 * still verifies with the full conflict scan at the end (the belt).
 *
 * <p>FLAG {@code planner.construction.hole-relay.enabled}, RATIFIED DEFAULT ON
 * (maintainer, 15/09: gate A/B ab-gate, criteri a-e VERDI).
 * Position: delivery boundary, after CompositeSwapFinisher and the EDF
 * extensions, once per delivery. Oracle: the caller's improve-or-noop on the
 * FULL hard (rooms move: H2/H29 in play). The landing gate per moved lesson is
 * the fresh per-slot VRP (H5/H6/H23/H26/M32 inside), teacher free, H7 cap, room
 * of the family available.
 */
public final class HoleRelayFinisher {

    public static final String FLAG = "planner.construction.hole-relay.enabled";

    /** RATIFIED DEFAULT ON (maintainer, 15/09, gate A/B ab-gate: A3 -14 vs B3 -9,
     *  criteri a-e TUTTI VERDI, crash-control VERDE, no-op sicuro sul live come
     *  pre-registrato): null/absent -> ON, only the literal "false" disarms. */
    public static boolean enabled() {
        String v = System.getProperty(FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    /** TASK-619 (import cross-data stesso-gruppo, the finisher's THIRD arm),
     *  RATIFIED DEFAULT ON (maintainer, 16/09: banco A3 -11 -> -10 / B3 -9 -> -8 PULITi,
     *  gate /code-review high con 3 fix applicati, re-certifica 5/5 identica).
     *  Standard opt-OUT form: null/absent -> ON, only the literal "false" disarms. */
    public static boolean importCrossDateEnabled() {
        String v = System.getProperty("planner.construction.hole-relay.import-cross-date.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /** TASK-620 (H7-cap arm: teacher-day overcap cure), RATIFIED DEFAULT ON
     *  (maintainer, 16/09 'quello che pensi sia meglio' + '1 e 2 okay': banco -1 -> 0
     *  PULITO su reab-cura arm-a, doppia gate /code-review + /simplify con i 2
     *  gate classe-616 applicati, re-cert round-2 4/4 veridi dopo la cura
     *  branch-aware del SOURCE-YIELD). Standard opt-OUT form: null/absent -> ON,
     *  only the literal "false" disarms. Read LIVE. */
    public static boolean h7CapEnabled() {
        String v = System.getProperty("planner.construction.hole-relay.h7cap.enabled");
        return v == null || Boolean.parseBoolean(v);
    }


    /** TASK-621ext depth-3 (H7 multi-group overcap chain cure), RATIFIED DEFAULT
     *  ON (bounded-no-op directive + night authorization, 17/09): banco -4 -> -1,
     *  quality cures 2b2c4c87, re-cert 4/4 (3233bd2b). Standard opt-OUT form. */
    public static boolean h7Depth3Enabled() {
        String v = System.getProperty("planner.construction.hole-relay.h7-depth3.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /** TASK-632 PROTOTYPE (swap-for-overcap): the x-swap 1-per-1 cell-exchange
     *  grammar driven by the H7 overcap enumeration instead of an H17 hole. Born
     *  from the T13 blind puzzle 20/09 (integrato-1, 7h/cap6 on 4 groups in
     *  a quota-tight region: every export+donor pair is dead because no donor day
     *  can give an hour without opening its own H32; the blind found the legal
     *  closure as ONE swap with a same-group lesson on another day, and the
     *  arbitration cleared it on the full dump). PROTOTYPE opt-IN: only the
     *  literal "true" arms it; ratifica (and the opt-out flip) comes after the
     *  banco. Census HRF-H7SWAP-*. */
    public static boolean h7SwapEnabled() {
        return "true".equals(System.getProperty("planner.construction.hole-relay.h7swap.enabled"));
    }

    /** TASK-621ext (cross-date-swap stesso-gruppo 1-per-1, the H17 variant where
     *  the backfill IS the expelled lesson), RATIFIED DEFAULT ON (bounded-no-op
     *  directive + night authorization, 17/09): banco -4 -> -2, doppia gate con
     *  le cure 616/H28 (fb007377), transfer 4/4. Standard opt-OUT form. */
    public static boolean xSwapEnabled() {
        String v = System.getProperty("planner.construction.hole-relay.x-swap.enabled");
        return v == null || Boolean.parseBoolean(v);
    }
    /** blind-live-r1 lever A (compact-swap intra-classe), RATIFIED DEFAULT ON (maintainer,
     *  16/09, endgame-bundle re-A/B 258ac6cbe: A = B = [-1/0], full no-op on the live
     *  basin with the pass armed; positive on three measured basins: B3 -9 -> -8, A3
     *  contributes to -14 -> -11, banco twins). Standard opt-OUT form: null/absent ->
     *  ON, only the literal "false" disarms. Read LIVE. */
    public static final String COMPACT_SWAP_FLAG = "planner.construction.hole-relay.compact-swap.enabled";

    public static boolean compactSwapEnabled() {
        String v = System.getProperty(COMPACT_SWAP_FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    private HoleRelayFinisher() {
    }

    /**
     * {@code targets} = internal holes seen; {@code slides} = holes closed, by the
     * plain down-slide, by the slide that completes a rotation composite, or by a
     * compact-swap pair (lever A; counted as one slide, separated only under
     * -Dhrf.debug; every
     * composite closes with exactly one slide, so this IS holesClosed);
     * {@code rotations} = permission-rotation moves applied (the census shape:
     * 3 rotations + 1 slide per freed hole); {@code movesApplied} = lessons moved,
     * both kinds.
     */
    public record Result(int targets, int slides, int rotations, int movesApplied) {
        public int holesClosed() {
            return slides;
        }
    }

    public static Result finish(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot) {
        if (!enabled() || schedule.getTimeslots() == null) {
            return new Result(0, 0, 0, 0);
        }
        Live live = Live.capture(schedule);
        List<Hole> targets = enumerateHoles(live);
        int slides = 0;
        int rotations = 0;
        int moves = 0;
        for (Hole hole : targets) {
            // Re-validation (v0.1 F3): earlier commits may have shrunk the day's
            // span; filling a slot no longer internal would OPEN a new hole.
            TreeMap<Integer, Lesson> day = live.dayOf(hole.dayKey());
            if (day.size() < 2 || hole.slot() <= day.firstKey()
                    || hole.slot() >= day.lastKey() || day.containsKey(hole.slot())) {
                continue;
            }
            Timeslot holeTs = live.tsAt(hole.date(), hole.slot());
            if (holeTs == null) {
                continue; // grid hole (no Timeslot): not ours to fill
            }
            Lesson tail = day.lastEntry().getValue();
            if (!movable(tail)) {
                census("HRF-TAIL-MOVABLE");
                continue;
            }
            if (rejectReason(live, tail, holeTs) == null) {
                live.move(tail, holeTs, roomFor(live, tail, holeTs));
                moveTimeslot.accept(tail, holeTs);
                slides++;
                moves++;
                continue;
            }
            // blind-live-r1 lever A (RATIFIED DEFAULT ON 16/09, endgame bundle): the
            // COMPACT-SWAP INTRA-CLASSE, tried BEFORE paying the cross-class DFS - the
            // family the blind round found outside this pass's alphabet (bench twin:
            // CompactSwapFinisherDiff, -9 -> -8 on the live B3 with the exact blind
            // pair).
            if (compactSwapEnabled()) {
                int csMoves = tryCompactSwap(live, moveTimeslot, holeTs, day, hole);
                if (csMoves > 0) {
                    slides++;
                    moves += csMoves;
                    continue;
                }
            }
            // TASK-621ext, the CROSS-DATE-SWAP 1-per-1 (opt-in): import the donor
            // day's TAIL into the hole and land the hole-day's expelled tail on the
            // donor's just-vacated cell - the backfill IS the expelled lesson (the
            // 619 variant instead searches a tolerant donor). Bank-proven on the
            // integrato-1 [-4]: 2 swaps close both G13 06-10 holes.
            if (xSwapEnabled()) {
                int xsMoves = tryCrossDateSwap(live, moveTimeslot, hole, holeTs, day);
                if (xsMoves > 0) {
                    slides++;
                    moves += xsMoves;
                    continue;
                }
            }
            // TASK-619, the THIRD arm (porting of the bank-proven HoleRelayFinisherImport
            // iter3b): import a SAME-GROUP lesson from ANOTHER date into the hole (the
            // retired v0.1 family - its death was BASIN-SPECIFIC, the A3 blind round
            // revived it). Bank order note: iter3b measured the arm as a pass AFTER the
            // levers; in-pass it sits here - after compact-swap, BEFORE the cross-class
            // DFS - so the cheapest arms are paid first. The transfer test arbitrates.
            if (importCrossDateEnabled()) {
                int impMoves = tryImportCrossDate(live, moveTimeslot, hole, holeTs);
                if (impMoves > 0) {
                    slides++;
                    moves += impMoves;
                    continue;
                }
            }
            // The teacher-contention path: WHO holds tail's teacher at the hole hour?
            Lesson contender = live.teacherOf(tail.getTeacher(), holeTs);
            if (contender == null || !movable(contender) || contender == tail) {
                census("HRF-NO-CONTENDER");
                continue;
            }
            int applied = tryRotationThenSlide(live, moveTimeslot, holeTs, tail, contender,
                    // depth 5 (experiment 15/09: the v0.3 census fired HRF-DEPTH=66
                    // on the D1 cascade - the blind chain went 4 groups deep; a cap
                    // raise discriminates cap-bound vs gate-bound)
                    5, new java.util.HashSet<>(List.of(hole.dayKey())));
            if (applied > 0) {
                slides++;
                rotations += applied - 1;
                moves += applied;
            }
        }
        // TASK-620 + TASK-621ext, the H7 arms (TASK-617 consolidation: ONE overcap
        // enumeration for both - the pair first, then the chain, on the same target
        // list; the previous two sweeps scanned teacher-hours twice when both flags
        // were ON). The target set is orthogonal to the hole arms (teacher-day
        // OVERCAP, not holes), so it runs AFTER them: the hole arms may themselves
        // resolve an overcap and the sweep then sees the settled state.
        if (h7CapEnabled() || h7Depth3Enabled() || h7SwapEnabled()) {
            List<Object[]> overcaps = enumerateOvercaps(live);
            if (h7CapEnabled()) {
                for (Object[] t : overcaps) {
                    moves += tryH7CapPair(live, moveTimeslot, (String) t[0], (LocalDate) t[1]);
                }
            }
            if (h7Depth3Enabled()) {
                for (Object[] t : overcaps) {
                    moves += tryH7Depth3Pair(live, moveTimeslot, (String) t[0], (LocalDate) t[1]);
                }
            }
            if (h7SwapEnabled()) {
                for (Object[] t : overcaps) {
                    moves += tryH7SwapPair(live, moveTimeslot, (String) t[0], (LocalDate) t[1]);
                }
            }
        }
        if (Boolean.getBoolean("hrf.debug") && !HRF_REJECTS.isEmpty()) {
            System.out.println("[HRF-debug] reject census: " + HRF_REJECTS);
            HRF_REJECTS.clear();
        }
        return new Result(targets.size(), slides, rotations, moves);
    }

    /** TASK-617: the ONE overcap enumeration both H7 arms consume (the former
     *  sweepH7Overcaps was TreeMap-ordered, sweepH7Depth3 HashMap-ordered: the
     *  merged list is TreeMap so the h7cap arm's declared order survives; the
     *  depth3 arm re-checks liveness at entry since the snapshot predates the
     *  h7cap arm's settling). Each entry is {teacherName, date}; the TARGET
     *  labels print per-arm under -Dhrf.debug. */
    private static List<Object[]> enumerateOvercaps(Live live) {
        // review fix 17/09: TreeMap restores the h7cap arm's DECLARED alphabetical
        // target order (the old sweepH7Overcaps was a TreeMap; the depth3 sweep's
        // HashMap was nondeterministic, so this is a strict improvement, and the
        // single list now serves both arms deterministically).
        Map<String, Map<LocalDate, int[]>> tHours = new TreeMap<>();
        for (var e : live.days.entrySet()) {
            LocalDate date = live.dateOf(e.getKey());
            for (Lesson l : e.getValue().values()) {
                if (l.getTeacher() == null || l.getTeacher().pseudo) {
                    continue;
                }
                tHours.computeIfAbsent(l.getTeacher().name, k -> new HashMap<>())
                        .computeIfAbsent(date,
                                k -> new int[] { 0, l.getTeacher().getEffectiveMaxDailyHours() })[0]++;
            }
        }
        List<Object[]> out = new ArrayList<>();
        for (var te : tHours.entrySet()) {
            for (var de : te.getValue().entrySet()) {
                if (de.getValue()[0] > de.getValue()[1]) {
                    if (Boolean.getBoolean("hrf.debug")) {
                        if (h7CapEnabled()) {
                            System.out.printf("[HRF-H7CAP-TARGET] %s %s: %d ore (cap %d)%n",
                                    te.getKey(), de.getKey(), de.getValue()[0], de.getValue()[1]);
                        }
                        if (h7Depth3Enabled()) {
                            System.out.printf("[HRF-H7D3-TARGET] %s %s: %d ore (cap %d)%n",
                                    te.getKey(), de.getKey(), de.getValue()[0], de.getValue()[1]);
                        }
                    }
                    out.add(new Object[] { te.getKey(), de.getKey() });
                }
            }
        }
        return out;
    }

    /**
     * TASK-621ext depth-3 (ported from the bank twin {@code Combo621ExtDiff}
     * depth-3, 7e9a4ed3): the H7 multi-group overcap cure as a chain. For a
     * hinge-teacher day in overcap: (1) the HINGE lesson (one of the teacher's
     * parallels in a HOST group, never the host day's first hour) moves to a
     * legal cell on another date of the HOST group; (2) if the vacated slot
     * leaves the host day short (tolerant yield) or starting late (H31), a
     * PARTNER (same-host-group TAIL lesson from a near date whose origin can
     * tolerate losing one hour) fills the vacated cell: quota and H31 are
     * restored and NO day loses hours; (3) the partner's origin yields its tail
     * (the tolerant-yield check IS the third level). H28 pair-safe via the
     * shared before/after gate; H7 checked on EVERY moved lesson's landing
     * (the TASK-616 lesson: every new arm forgets it). Census HRF-H7D3-*.
     */
    private static int tryH7Depth3Pair(Live live, BiConsumer<Lesson, Timeslot> moveTimeslot,
            String hingeName, LocalDate overcapDate) {
        List<Lesson> parallels = teacherDayExportables(live, hingeName, overcapDate);
        for (Lesson cand : parallels) {
            String hostGroup = cand.course.studentGroup.name;
            // review fix 17/09: the old sweep re-scanned teacher hours at its own
            // call time (AFTER the h7cap arm had settled); the shared enumeration
            // snapshots BEFORE both arms, so re-check here and skip targets the
            // h7cap pair already cured (the extra attempts were one-directional
            // but silently changed the delivered schedule on multi-overcap basins).
            if (live.teacherHoursOn(cand.getTeacher(), overcapDate)
                    < cand.getTeacher().getEffectiveMaxDailyHours()) {
                census("HRF-H7D3-ALREADY-CURED");
                continue;
            }
            TreeMap<Integer, Lesson> hostDay = live.days.get(hostGroup + "|" + overcapDate);
            int candSlot = TimeslotSequence.slotIndexOf(cand.timeslot);
            if (candSlot == hostDay.firstKey()) {
                census("HRF-H7D3-H31-FIRST");
                continue;
            }
            List<Object[]> dests = new ArrayList<>();
            for (var e : live.days.entrySet()) {
                if (!e.getKey().startsWith(hostGroup + "|")
                        || e.getKey().equals(hostGroup + "|" + overcapDate) || e.getValue().isEmpty()) {
                    continue;
                }
                LocalDate d2 = live.dateOf(e.getKey());
                int last = e.getValue().lastKey();
                // the 620 twin's landing-quota filter (review parity finding): the
                // landing must not overshoot the declared quota (M33 medium tier,
                // invisible to the hard-only oracle - the sibling guards it, so this
                // arm does too).
                Map<LocalDate, Integer> quota = quotaMapFor(live, hostGroup);
                // TASK-671: pool-cap-aware landing quota (was raw sheet: for 1/G08
                // the sheet overstates the obligation since TASK-577)
                int q2 = SchoolSchedule.poolCappedQuotaForDate(
                        groupIdOf(live, hostGroup), d2, quota.getOrDefault(d2, 0));
                if (q2 > 0 && nonStageHours(e.getValue()) + 1 > q2) {
                    continue;
                }
                for (int s : h7d3CandidateSlots(e.getValue(), last)) {
                    Timeslot ts = live.tsAt(d2, s);
                    if (ts != null) {
                        dests.add(new Object[] {
                                Math.abs(d2.toEpochDay() - overcapDate.toEpochDay()), d2, ts });
                    }
                }
            }
            dests.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
            for (Object[] d : dests) {
                Timeslot landTs = (Timeslot) d[2];
                LocalDate d2 = (LocalDate) d[1];
                if (!cand.isSlotLegalFresh(landTs)) {
                    census("HRF-H7D3-VRP");
                    continue;
                }
                if (live.teacherOf(cand.getTeacher(), landTs) != null) {
                    census("HRF-H7D3-H1");
                    continue;
                }
                if (live.teacherHoursOn(cand.getTeacher(), d2)
                        >= cand.getTeacher().getEffectiveMaxDailyHours()) {
                    census("HRF-H7D3-H7-LAND");
                    continue;
                }
                // the pair state BEFORE any move (the H28 pair-safe gate's baseline)
                final int h28Cand = h28ReturnsIn(live, hostGroup, cand.course.subject, overcapDate);
                final int h28Land = h28ReturnsIn(live, hostGroup, cand.course.subject, d2);
                CompositeRecorder rec = new CompositeRecorder(moveTimeslot);
                Room room = roomFor(live, cand, landTs);
                if (room == null) {
                    census("HRF-H7D3-ROOM");
                    continue;
                }
                rec.apply(live, cand, landTs, room);
                // depth-3 partner: repair the host day if short or late-starting
                Lesson partner = null;
                TreeMap<Integer, Lesson> hostAfter = live.days.get(hostGroup + "|" + overcapDate);
                int tolG = SchoolScheduleConstraintProvider.quotaFillTolerance(hostGroup);
                int floorG = floorOfGroup(live, hostGroup);
                boolean hostShort = hostAfter == null || hostAfter.isEmpty()
                        || nonStageHours(hostAfter) < Math.max(quotaOfGroup(live, hostGroup,
                                overcapDate) - tolG, floorG)
                        || hostAfter.firstKey() != 0;
                if (hostShort) {
                    Timeslot vac = live.tsAt(overcapDate, candSlot);
                    if (vac == null) {
                        census("HRF-H7D3-NO-VAC-CELL");
                        rec.revert(live);
                        continue;
                    }
                    partner = findTailOnlyTolerantDonor(live, hostGroup, overcapDate, vac);
                    if (partner == null) {
                        census("HRF-H7D3-NO-PARTNER");
                        rec.revert(live);
                        continue;
                    }
                    Room rP = roomFor(live, partner, vac);
                    if (rP == null) {
                        census("HRF-H7D3-PARTNER-ROOM");
                        rec.revert(live);
                        continue;
                    }
                    // partner's own H28 baselines (its vacated origin and its landing)
                    final int h28POrigin = h28ReturnsIn(live, hostGroup, partner.course.subject,
                            partner.timeslot.getDate());
                    final int h28PLand = h28ReturnsIn(live, hostGroup, partner.course.subject,
                            overcapDate);
                    rec.apply(live, partner, vac, rP);
                    boolean pairSafe = h28ReturnsIn(live, hostGroup, cand.course.subject,
                            overcapDate) <= h28Cand
                            && h28ReturnsIn(live, hostGroup, cand.course.subject, d2) <= h28Land
                            && h28ReturnsIn(live, hostGroup, partner.course.subject,
                                    rec.originOf(partner).getDate()) <= h28POrigin
                            && h28ReturnsIn(live, hostGroup, partner.course.subject,
                                    overcapDate) <= h28PLand; // bespoke 4-clause: the 2 extra
                            // clauses (partner tuple on both days) are monotone-safe and
                            // cover tuples h28PairSafe(a,b) does not see; left as-is
                            // (TASK-617: forcing the shared 2-clause form would WEAKEN it)
                    if (!pairSafe) {
                        census("HRF-H7D3-H28");
                        rec.revert(live);
                        continue;
                    }
                }
                boolean ok = verifyNoNewHoles(live, hostGroup, overcapDate)
                        && verifyNoNewHoles(live, hostGroup, d2)
                        && (partner == null || verifyNoNewHoles(live, hostGroup,
                                rec.originOf(partner).getDate()))
                        && verifyDayStartOf(live, hostGroup, overcapDate)
                        && verifyDayStartOf(live, hostGroup, d2)
                        && (partner == null || verifyDayStartOf(live, hostGroup,
                                rec.originOf(partner).getDate()))
                        && verifyComposite(live, rec.lessons()) == null;
                if (!ok) {
                    census("HRF-H7D3-BELT");
                    rec.revert(live);
                    continue;
                }
                if (Boolean.getBoolean("hrf.debug")) {
                    System.out.printf("[HRF-H7D3-applied] %s %s overcap: %s(%s) @%d -> %s@%d%s%n",
                            hingeName, overcapDate, cand.course.subject, hingeName, candSlot, d2,
                            TimeslotSequence.slotIndexOf(landTs),
                            partner == null ? "" : " + partner " + partner.course.subject);
                }
                return rec.size();
            }
        }
        census("HRF-H7D3-NO-CANDIDATE");
        return 0;
    }

    /** landing slots: internal holes first (filling is quota-legal), then tail+1. */
    private static List<Integer> h7d3CandidateSlots(TreeMap<Integer, Lesson> day, int last) {
        List<Integer> out = new ArrayList<>();
        for (int s = day.firstKey(); s < last; s++) {
            if (!day.containsKey(s)) {
                out.add(s);
            }
        }
        out.add(last + 1);
        return out;
    }

    /** the depth-3 partner: same-host-group TAIL from a near date; its origin day
     *  can tolerate losing one hour (the third level's yield check). NOTE (review):
     *  the "chain" is a hardcoded 2-move composite (hinge exit + partner fill)
     *  whose third level is this selection predicate, NOT a recursion - a depth-4
     *  variant would need the finder rewritten recursively. */
    /** TASK-617: the ONE tail-only tolerant donor finder (the former
     *  findTailOnlyTolerantDonor int-signature variant and findH7D3Partner were
     *  the same ladder). Near dates first; the donor day must tolerate losing
     *  its tail: non-Stage - 1 >= max(quota - tol, floor). */
    private static Lesson findTailOnlyTolerantDonor(Live live, String group, LocalDate targetDate,
            Timeslot vac) {
        int tol = SchoolScheduleConstraintProvider.quotaFillTolerance(group);
        int floor = floorOfGroup(live, group);
        List<Map.Entry<String, TreeMap<Integer, Lesson>>> byDay =
                sameGroupDaysByDistance(live, group, group + "|" + targetDate, targetDate);
        for (var e : byDay) {
            if (!e.getKey().startsWith(group + "|") || e.getKey().equals(group + "|" + targetDate)
                    || e.getValue().isEmpty()) {
                continue;
            }
            LocalDate d3 = live.dateOf(e.getKey());
            if (nonStageHours(e.getValue()) - 1
                    < Math.max(quotaOfGroup(live, group, d3) - tol, floor)) {
                continue;
            }
            Lesson y = e.getValue().lastEntry().getValue();
            if (!movable(y) || !y.isSlotLegalFresh(vac)) {
                continue;
            }
            if (y.getTeacher() != null && !y.getTeacher().pseudo
                    && live.teacherOf(y.getTeacher(), vac) != null) {
                continue;
            }
            if (y.getTeacher() != null && !y.getTeacher().pseudo
                    && live.teacherHoursOn(y.getTeacher(), targetDate)
                            >= y.getTeacher().getEffectiveMaxDailyHours()) {
                continue;
            }
            return y;
        }
        return null;
    }

    private static int quotaOfGroup(Live live, String group, LocalDate date) {
        return live.quotaByName.getOrDefault(group, Map.of()).getOrDefault(date, 0);
    }

    private static int floorOfGroup(Live live, String group) {
        for (var e : live.days.entrySet()) {
            if (e.getKey().startsWith(group + "|")) {
                Integer f = SchoolSchedule.getDayFloor(
                        e.getValue().firstEntry().getValue().course.studentGroup.id);
                return f == null ? 0 : f;
            }
        }
        return 0;
    }

    /** H31 final-state: a day that still has lessons must start at slot 0. */
    private static boolean verifyDayStartOf(Live live, String group, LocalDate date) {
        TreeMap<Integer, Lesson> day = live.days.get(group + "|" + date);
        return day == null || day.isEmpty() || day.firstKey() == 0;
    }


    private static int tryH7CapPair(Live live, BiConsumer<Lesson, Timeslot> moveTimeslot,
            String teacherName, LocalDate overcapDate) {
        // /simplify cure: the heavy debug lines below guard their holeDetail scans
        // behind this flag so the flag-off path pays no day scans (the helper and
        // census still re-read the flag, which is a cheap property get).
        boolean dbg = Boolean.getBoolean("hrf.debug");
        List<Lesson> exportable = teacherDayExportables(live, teacherName, overcapDate);
        for (Lesson cand : exportable) {
            String group = cand.course.studentGroup.name;
            Map<LocalDate, Integer> quota = quotaMapFor(live, group);
            Integer floorObj = SchoolSchedule.getDayFloor(cand.course.studentGroup.id);
            int floor = floorObj == null ? 0 : floorObj;
            int tol = SchoolScheduleConstraintProvider.quotaFillTolerance(group);
            int candSlot = TimeslotSequence.slotIndexOf(cand.timeslot);
            TreeMap<Integer, Lesson> srcDay = live.days.get(group + "|" + overcapDate);
            List<Object[]> dests = new ArrayList<>();
            for (var e : live.days.entrySet()) {
                if (!e.getKey().startsWith(group + "|") || e.getKey().equals(group + "|" + overcapDate)) {
                    continue;
                }
                LocalDate d2 = live.dateOf(e.getKey());
                if (e.getValue().isEmpty()) {
                    continue;
                }
                Timeslot tailTs = live.tsAt(d2, e.getValue().lastKey() + 1);
                if (tailTs == null) {
                    continue;
                }
                int after = e.getValue().size() + 1;
                // TASK-671: pool-cap-aware landing quota (was raw sheet)
                int q2 = SchoolSchedule.poolCappedQuotaForDate(
                        cand.course.studentGroup.id, d2, quota.getOrDefault(d2, 0));
                if (q2 > 0 && after > q2) {
                    continue; // landing would overshoot the declared quota
                }
                dests.add(new Object[] { Math.abs(d2.toEpochDay() - overcapDate.toEpochDay()), d2, tailTs });
            }
            dests.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
            if (dbg) {
                h7capDebug("CAND", teacherName, overcapDate, candSlot, null,
                        "group=" + group + " src[" + holeDetail(live, group, overcapDate)
                                + "] dests=" + dests.size());
            }
            for (Object[] d : dests) {
                LocalDate d2 = (LocalDate) d[1];
                Timeslot tailTs = (Timeslot) d[2];
                if (!cand.isSlotLegalFresh(tailTs)) {
                    census("HRF-H7CAP-EXPORT-VRP");
                    h7capDebug("EXPORT-VRP", teacherName, overcapDate, candSlot, d2, null);
                    continue;
                }
                if (live.teacherOf(cand.getTeacher(), tailTs) != null) {
                    census("HRF-H7CAP-EXPORT-H1");
                    h7capDebug("EXPORT-H1", teacherName, overcapDate, candSlot, d2, null);
                    continue;
                }
                // review gate 16/09 (616-class, inherited from the bank twin): the export
                // ADDS a teacher hour on the landing day exactly as an import does - if the
                // teacher is already at cap there, the pair merely RELOCATES the overcap at
                // net-zero hard, which the wrapper oracle keeps as a tie.
                if (cand.getTeacher() != null && !cand.getTeacher().pseudo && live.teacherHoursOn(
                        cand.getTeacher(), d2) >= cand.getTeacher().getEffectiveMaxDailyHours()) {
                    census("HRF-H7CAP-EXPORT-H7");
                    h7capDebug("EXPORT-H7", teacherName, overcapDate, candSlot, d2,
                            "teacher already at cap on dest day");
                    continue;
                }
                Lesson donor = null;
                // TASK-629 v3+v4 (bench evidence 20/09 + gate review 20/09): the donor
                // is REQUIRED whenever the pair must restore more than the hour count.
                // Three disjuncts: (1) midRunExport - a mid-run export from a
                // contiguous day opens an internal hole no count arithmetic sees
                // (T08 26/05: day [0..4] contiguous, 5 hours vs quota 4, the @2
                // export was count-safe donor-less and the belt correctly reverted
                // it, missing=2 appeared only post-pair); (2) firstHourExport - the
                // H31-sensitive shape (gate finding E1: this belt has no day-start
                // leg and the wrapper oracle is NET-hard, so a donor-less first-hour
                // export trades the H7 cure for a same-size H31 and ships at net
                // zero, the trade class the bounded-no-op directive forbids); the
                // donor lands on the exact vacated first slot and preserves the day
                // start; (3) the v2 count invariant - the source day cannot drop
                // below H32's own acceptance. A tail-edge export from a day with
                // count slack exports donor-less (the v1 regression lesson).
                boolean midRunExport = !srcDay.isEmpty()
                        && candSlot > srcDay.firstKey() && candSlot < srcDay.lastKey();
                boolean firstHourExport = !srcDay.isEmpty()
                        && candSlot == srcDay.firstKey();
                // TASK-671: pool-cap-aware backfill yield line (was raw sheet)
                boolean needsBackfill = midRunExport || firstHourExport
                        || nonStageHours(srcDay) - 1
                        < poolAwareYieldLine(cand.course.studentGroup.id, overcapDate,
                                quota.getOrDefault(overcapDate, 0) - tol, floor);
                if (needsBackfill) {
                    donor = findTailOnlyTolerantDonor(live, group, overcapDate,
                            live.tsAt(overcapDate, candSlot));
                    if (donor == null) {
                        census("HRF-H7CAP-DONOR-TOL");
                        h7capDebug("DONOR-TOL", teacherName, overcapDate, candSlot, d2,
                                "group=" + group + " needsBackfill, no tolerant donor");
                        continue;
                    }
                }
                // If !needsBackfill: the source day has slack (over quota or under
                // floor), export without donor is safe (H32 and H33 will not fire).
                CompositeRecorder rec = new CompositeRecorder(moveTimeslot);
                // Gate review 20/09 (finding C2 = the deferred F2): the H28
                // transversal belt both siblings carry (x-swap h28PairSafe, the h7d3
                // bespoke gate). A donor landing mid-day on the overcap day, and a
                // cand landing at the dest tail, can each be a leg of a (group,
                // subject, day) return; the wrapper oracle is NET-hard and would keep
                // the pair at a same-size H28 trade. Touched tuples must not grow.
                int h28BeforeDest = h28ReturnsIn(live, group, cand.course.subject, d2);
                int h28BeforeSrcDonor = donor == null ? 0
                        : h28ReturnsIn(live, group, donor.course.subject, overcapDate);
                Room roomExport = roomFor(live, cand, tailTs);
                if (roomExport == null) {
                    census("HRF-H7CAP-EXPORT-ROOM");
                    h7capDebug("EXPORT-ROOM", teacherName, overcapDate, candSlot, d2, null);
                    continue;
                }
                rec.apply(live, cand, tailTs, roomExport);
                if (donor != null) {
                    Timeslot vacated = live.tsAt(overcapDate, candSlot);
                    Room roomDonor = roomFor(live, donor, vacated);
                    if (roomDonor == null) {
                        census("HRF-H7CAP-DONOR-ROOM");
                        h7capDebug("DONOR-ROOM", teacherName, overcapDate, candSlot, d2,
                                "donor " + donor.course.subject + " has no room at vacated slot");
                        rec.revert(live);
                        continue;
                    }
                    rec.apply(live, donor, vacated, roomDonor);
                }
                // TASK-629 follow-up (bench attribution): the hole and H28 legs are
                // evaluated eagerly so the failing one is named in the census instead
                // of the aggregate BELT the banco could only attribute by code
                // reading; verifyComposite stays lazy (it censuses on failure).
                boolean srcHoles = verifyNoNewHoles(live, group, overcapDate);
                boolean landHoles = verifyNoNewHoles(live, group, cand.timeslot.getDate());
                boolean donorHoles = donor == null
                        || verifyNoNewHoles(live, group, rec.originOf(donor).getDate());
                boolean h28Ok = h28ReturnsIn(live, group, cand.course.subject, d2) <= h28BeforeDest
                        && (donor == null || h28ReturnsIn(live, group, donor.course.subject,
                                overcapDate) <= h28BeforeSrcDonor);
                boolean holesOk = srcHoles && landHoles && donorHoles;
                String comp = holesOk && h28Ok ? verifyComposite(live, rec.lessons()) : null;
                if (!holesOk || !h28Ok || comp != null) {
                    census("HRF-H7CAP-BELT");
                    String leg = !srcHoles ? "SRC-HOLES" : !landHoles ? "LAND-HOLES"
                            : !donorHoles ? "DONOR-HOLES" : !h28Ok ? "H28" : comp;
                    census("HRF-H7CAP-BELT-" + leg);
                    if (dbg) {
                        h7capDebug("BELT-" + leg, teacherName, overcapDate, candSlot,
                                cand.timeslot.getDate(),
                                " group=" + group + " src[" + holeDetail(live, group, overcapDate) + "]"
                                        + " land[" + holeDetail(live, group, cand.timeslot.getDate()) + "]"
                                        + (donor == null ? "" : " donorOrigin["
                                                + holeDetail(live, group, rec.originOf(donor).getDate()) + "]"));
                    }
                    rec.revert(live);
                    continue;
                }
                if (Boolean.getBoolean("hrf.debug")) {
                    System.out.printf("[HRF-H7CAP-applied] %s %s overcap: export %s(%s) @%d ->"
                                    + " %s@%d%s%n",
                            teacherName, overcapDate, cand.course.subject, teacherName,
                            candSlot, cand.timeslot.getDate(),
                            TimeslotSequence.slotIndexOf(cand.timeslot),
                            donor == null ? "" : " + tail-only tolerant donor "
                                    + donor.course.subject);
                }
                return rec.size();
            }
        }
        census("HRF-H7CAP-NO-CANDIDATE");
        h7capDebug("NO-CANDIDATE", teacherName, overcapDate, -1, null,
                "exportable=" + exportable.size());
        return 0;
    }

    /** TASK-629 follow-up: one line per candidate/gate under -Dhrf.debug, so the
     * banco attributes each kill to a specific gate with teacher, date and
     * destination instead of inferring it from the code. */
    private static void h7capDebug(String gate, String teacher, LocalDate overcapDate,
            int candSlot, LocalDate dest, String detail) {
        if (!Boolean.getBoolean("hrf.debug")) {
            return;
        }
        System.out.printf("[HRF-H7CAP-cand] %s: %s %s@%d -> %s%s%n", gate, teacher,
                overcapDate, candSlot, dest, detail == null ? "" : " " + detail);
    }

    /** TASK-629 follow-up: the internal-hole shape of one (group, date) as a compact
     * debug string (first/last slot plus the missing slots between them). */
    private static String holeDetail(Live live, String group, LocalDate date) {
        TreeMap<Integer, Lesson> day = live.dayOf(dayKeyOf(group, date));
        if (day == null || day.isEmpty()) {
            return "empty";
        }
        int lo = day.firstKey();
        int hi = day.lastKey();
        StringBuilder sb = new StringBuilder("first=").append(lo)
                .append(" last=").append(hi).append(" missing=");
        boolean any = false;
        for (int s = lo + 1; s < hi; s++) {
            if (!day.containsKey(s)) {
                sb.append(s).append(',');
                any = true;
            }
        }
        if (!any) {
            sb.append('-');
        }
        return sb.toString();
    }

    /**
     * TASK-632 prototype: SWAP-FOR-OVERCAP, the x-swap 1-per-1 cell-exchange
     * grammar driven by the H7 overcap enumeration instead of an H17 hole. Born
     * from the T13 blind puzzle (20/09, integrato-1: 7h/cap6 across 4
     * groups in a quota-tight region where every export+donor pair is dead: no
     * donor day can give an hour without opening its own H32); the blind's legal
     * closure was ONE swap with a same-group lesson on another day, arbitration
     * clean on the full dump. A 1-for-1 cell exchange leaves both group-days'
     * occupancy sets and counts identical, so H17/H31/H32 hold BY CONSTRUCTION;
     * only per-item constraints can reject (VRP both ways, H1 both ways with the
     * partner exemption, H7 arrival both ways - the 616-class gate, H28 not-grow
     * both touched tuples, rooms). Exchange sequencing mirrors x-swap: the first
     * landing goes in with a pre-checked room (conservative by one occupant),
     * the second checks roomFor against the now-freed cell (accurate); the Live
     * index's put/remove dance converges for the two-cycle (verified move by
     * move, apply and revert). Census HRF-H7SWAP-*.
     */
    private static int tryH7SwapPair(Live live, BiConsumer<Lesson, Timeslot> moveTimeslot,
            String teacherName, LocalDate overcapDate) {
        List<Lesson> exportable = teacherDayExportables(live, teacherName, overcapDate);
        if (exportable.isEmpty()) {
            census("HRF-H7SWAP-NO-CANDIDATE");
            return 0;
        }
        // Liveness re-check (the F6 lesson): the snapshot predates the h7cap and
        // h7d3 arms, either of which may have settled this target already.
        Teacher t0 = exportable.get(0).getTeacher();
        if (live.teacherHoursOn(t0, overcapDate) <= t0.getEffectiveMaxDailyHours()) {
            census("HRF-H7SWAP-ALREADY-CURED");
            return 0;
        }
        for (Lesson cand : exportable) {
            String group = cand.course.studentGroup.name;
            Timeslot candTs = cand.timeslot;
            int candSlot = TimeslotSequence.slotIndexOf(candTs);
            List<Map.Entry<String, TreeMap<Integer, Lesson>>> partnerDays =
                    sameGroupDaysByDistance(live, group, group + "|" + overcapDate, overcapDate);
            for (var de : partnerDays) {
                if (!de.getKey().startsWith(group + "|")
                        || de.getKey().equals(group + "|" + overcapDate) || de.getValue().isEmpty()) {
                    continue;
                }
                LocalDate partnerDate = live.dateOf(de.getKey());
                // snapshot: the swap below mutates this very TreeMap mid-iteration
                // (apply removes the partner, revert re-inserts), so iterate a copy.
                for (Lesson partner : new ArrayList<>(de.getValue().values())) {
                    if (partner == cand || !movable(partner)) {
                        continue;
                    }
                    int partnerSlot = TimeslotSequence.slotIndexOf(partner.timeslot);
                    if (partner.getTeacher() != null && teacherName.equals(partner.getTeacher().name)) {
                        census("HRF-H7SWAP-SAME-TEACHER");
                        continue;
                    }
                    Timeslot partnerTs = partner.timeslot;
                    if (!cand.isSlotLegalFresh(partnerTs)) {
                        census("HRF-H7SWAP-CAND-VRP");
                        h7swapDebug("CAND-VRP", group, candSlot, partnerDate, partnerSlot);
                        continue;
                    }
                    if (!partner.isSlotLegalFresh(candTs)) {
                        census("HRF-H7SWAP-PARTNER-VRP");
                        h7swapDebug("PARTNER-VRP", group, candSlot, partnerDate, partnerSlot);
                        continue;
                    }
                    Lesson candHolder = live.teacherOf(cand.getTeacher(), partnerTs);
                    if (candHolder != null && candHolder != partner) {
                        census("HRF-H7SWAP-CAND-H1");
                        h7swapDebug("CAND-H1", group, candSlot, partnerDate, partnerSlot);
                        continue;
                    }
                    Lesson partnerHolder = live.teacherOf(partner.getTeacher(), candTs);
                    if (partnerHolder != null && partnerHolder != cand) {
                        census("HRF-H7SWAP-PARTNER-H1");
                        h7swapDebug("PARTNER-H1", group, candSlot, partnerDate, partnerSlot,
                                "holder " + partnerHolder.getTeacher().name);
                        continue;
                    }
                    // 616-class H7 arrivals: both landings ADD a teacher hour on
                    // the arrival date; at cap the swap only relocates the overcap
                    // at net-zero hard, which the wrapper oracle keeps as a tie.
                    if (partner.getTeacher() != null && !partner.getTeacher().pseudo && live.teacherHoursOn(
                            partner.getTeacher(), overcapDate) >= partner.getTeacher()
                                    .getEffectiveMaxDailyHours()) {
                        census("HRF-H7SWAP-H7-PARTNER-ARR");
                        h7swapDebug("H7-PARTNER-ARR", group, candSlot, partnerDate, partnerSlot);
                        continue;
                    }
                    if (live.teacherHoursOn(cand.getTeacher(), partnerDate)
                            >= cand.getTeacher().getEffectiveMaxDailyHours()) {
                        census("HRF-H7SWAP-H7-CAND-ARR");
                        h7swapDebug("H7-CAND-ARR", group, candSlot, partnerDate, partnerSlot);
                        continue;
                    }
                    final int h28BeforePartnerArr = h28ReturnsIn(live, group,
                            partner.course.subject, overcapDate);
                    final int h28BeforeCandArr = h28ReturnsIn(live, group,
                            cand.course.subject, partnerDate);
                    CompositeRecorder rec = new CompositeRecorder(moveTimeslot);
                    Room rPartner = roomFor(live, partner, candTs);
                    if (rPartner == null) {
                        census("HRF-H7SWAP-ROOM");
                        h7swapDebug("ROOM", group, candSlot, partnerDate, partnerSlot,
                                "partner " + partner.course.subject);
                        continue;
                    }
                    rec.apply(live, partner, candTs, rPartner);
                    Room rCand = roomFor(live, cand, partnerTs);
                    if (rCand == null) {
                        census("HRF-H7SWAP-ROOM-CAND");
                        h7swapDebug("ROOM-CAND", group, candSlot, partnerDate, partnerSlot,
                                "cand " + cand.course.subject);
                        rec.revert(live);
                        continue;
                    }
                    rec.apply(live, cand, partnerTs, rCand);
                    // Index-convergence assertion (the /simplify altitude finding):
                    // the two-cycle Live dance resolves by last-writer-wins and the
                    // collision-scan belts below read the same index, so a lost
                    // entry would surface as FEWER collisions, not more. Two map
                    // lookups turn the javadoc's convergence argument into a
                    // per-apply runtime fact.
                    boolean indexOk = live.groupAt(group, candTs) == partner
                            && live.groupAt(group, partnerTs) == cand;
                    if (!indexOk) {
                        census("HRF-H7SWAP-INDEX");
                    }
                    boolean ok = indexOk
                            && verifyNoNewHoles(live, group, overcapDate)
                            && verifyNoNewHoles(live, group, partnerDate)
                            && h28PairSafe(live, partner, cand,
                                    h28BeforePartnerArr, h28BeforeCandArr)
                            && verifyComposite(live, rec.lessons()) == null;
                    if (!ok) {
                        census("HRF-H7SWAP-BELT");
                        h7swapDebug("BELT", group, candSlot, partnerDate, partnerSlot,
                                (!indexOk ? "INDEX" : "HOLES/H28/COMP"));
                        rec.revert(live);
                        continue;
                    }
                    if (Boolean.getBoolean("hrf.debug")) {
                        System.out.printf("[HRF-H7SWAP-applied] %s %s overcap: swap %s(%s) @%d"
                                        + " <-> %s(%s) %s@%d%n",
                                teacherName, overcapDate, cand.course.subject, teacherName,
                                TimeslotSequence.slotIndexOf(candTs), partner.course.subject,
                                partner.getTeacher().name, partnerDate,
                                TimeslotSequence.slotIndexOf(partnerTs));
                    }
                    return rec.size();
                }
            }
        }
        census("HRF-H7SWAP-NO-PARTNER");
        return 0;
    }

    /** TASK-632b (the 632b banco lesson): one line per REJECTED pair under
     *  -Dhrf.debug, the [HRF-H7CAP-cand] pattern applied to the swap arm. The
     *  aggregate census could not discriminate grammar-vs-fixture (thousands of
     *  pairs, zero applied, H1 real but ROOM contaminated by greedy room
     *  quality); the per-pair line names the gate that kills each specific
     *  candidate, the blind's included. Volume is bench-scale (thousands of
     *  lines): grep, do not read raw. */
    private static void h7swapDebug(String gate, String group, int candSlot,
            LocalDate partnerDate, int partnerSlot, String... detail) {
        if (!Boolean.getBoolean("hrf.debug")) {
            return;
        }
        System.out.printf("[HRF-H7SWAP-cand] %s %s@%d <-> %s@%d%s%n", gate, group, candSlot,
                partnerDate, partnerSlot, detail.length > 0 ? " " + detail[0] : "");
    }

    /** The teacher's movable lessons on the given date across ALL groups (the
     *  shared exportable-candidate scan of the three H7 arms: h7cap, h7d3 and
     *  h7swap; extracted by /simplify 20/09 when the third copy landed). */
    private static List<Lesson> teacherDayExportables(Live live, String teacherName, LocalDate date) {
        List<Lesson> out = new ArrayList<>();
        for (var e : live.days.entrySet()) {
            if (!e.getKey().endsWith("|" + date)) {
                continue;
            }
            for (Lesson l : e.getValue().values()) {
                if (l.getTeacher() != null && teacherName.equals(l.getTeacher().name) && movable(l)) {
                    out.add(l);
                }
            }
        }
        return out;
    }

    /** The tolerant donor, TAIL-ONLY (the iteration-1 lesson: a donor giving its
     *  head opens on its own day the H31 this pair cures on the target). */

    /** The group's declared aula+lab quota map from the live snapshot. */
    private static Map<LocalDate, Integer> quotaMapFor(Live live, String group) {
        return live.quotaByName.getOrDefault(group, Map.of());
    }

    /**
     * TASK-619 (the THIRD arm, ported from the bank twin {@code HoleRelayFinisherImport}
     * iter3b, {@code a5c95473} on wt/blind-live-r1): for an internal hole every other arm
     * left, import a SAME-GROUP lesson from ANOTHER date that lands in the hole cell.
     * Gates are the house set plus the three the bank iterations earned: per-lesson VRP
     * first, H1 final-state, H7 (the import ADDS a teacher hour on the hole's date), the
     * TASK-616 room gate at apply; H31-day-start-preservation on EVERY export (a day's
     * first occupied hour is never exported); the no-new-holes belt reads the BACKFILL
     * DONOR'S ORIGIN day (the iter3 dead-code lesson: reading it post-move always saw
     * the destination); depth-1 backfill with NON-STAGE surplus arithmetic (the iter1
     * lesson: Stage hours occupy cells but do not count toward the quota).
     */
    private static int tryImportCrossDate(Live live, BiConsumer<Lesson, Timeslot> moveTimeslot,
            Hole hole, Timeslot holeTs) {
        String group = hole.groupName();
        // quota/floor maps (the GATE-E semantics: max(quota, floor) as the yield line)
        Map<String, Map<LocalDate, Integer>> quotaByGroup = new HashMap<>();
        for (com.school.planner.domain.DailyActivityQuota q : live.quotas) {
            if (q.studentGroup == null || q.date == null) {
                continue;
            }
            quotaByGroup.computeIfAbsent(q.studentGroup.name, k -> new HashMap<>())
                    .put(q.date, q.aulaHours + q.labHours);
        }
        Map<LocalDate, Integer> quota = quotaByGroup.getOrDefault(group, Map.of());
        Long gid = groupIdOf(live, group);
        Integer floorObj = SchoolSchedule.getDayFloor(gid);
        int floor = floorObj == null ? 0 : floorObj;
        // candidate importers: same-group lessons on OTHER dates; same-hour first,
        // then nearest date first (the bank's deterministic order)
        List<Lesson> candidates = new ArrayList<>();
        for (var e : live.days.entrySet()) {
            if (!e.getKey().startsWith(group + "|") || e.getKey().equals(hole.dayKey())) {
                continue;
            }
            candidates.addAll(e.getValue().values());
        }
        candidates.sort((a, b) -> {
            // review F2 16/09: hour-of-day (8..15) vs slotIndexOf (0..7) were never
            // equal, so the same-hour tier was dead code inherited from the bench twin;
            // compare the canonical slot index (the idiom used 10 lines below).
            boolean aSame = TimeslotSequence.slotIndexOf(a.timeslot) == hole.slot();
            boolean bSame = TimeslotSequence.slotIndexOf(b.timeslot) == hole.slot();
            if (aSame != bSame) {
                return aSame ? -1 : 1;
            }
            long da = Math.abs(a.timeslot.getDate().toEpochDay() - hole.date().toEpochDay());
            long db = Math.abs(b.timeslot.getDate().toEpochDay() - hole.date().toEpochDay());
            return Long.compare(da, db);
        });
        for (Lesson cand : candidates) {
            if (!movable(cand) || !cand.isSlotLegalFresh(holeTs)) {
                census("HRF-IMP-VRP");
                continue;
            }
            if (cand.getTeacher() != null && !cand.getTeacher().pseudo
                    && live.teacherOf(cand.getTeacher(), holeTs) != null) {
                census("HRF-IMP-H1");
                continue;
            }
            if (cand.getTeacher() != null && !cand.getTeacher().pseudo
                    && live.teacherHoursOn(cand.getTeacher(), holeTs.getDate())
                            >= cand.getTeacher().getEffectiveMaxDailyHours()) {
                census("HRF-IMP-H7");
                continue;
            }
            LocalDate srcDate = cand.timeslot.getDate();
            int srcSlot = TimeslotSequence.slotIndexOf(cand.timeslot);
            TreeMap<Integer, Lesson> srcDay = live.dayOf(dayKeyOf(group, srcDate));
            // the iter3 guard: a day's FIRST occupied hour is never exported (H31)
            if (srcSlot == srcDay.firstKey()) {
                census("HRF-IMP-H31-SRC");
                continue;
            }
            // NON-STAGE hours: Stage rows occupy the group's cells but do not count
            // toward the quota (the iter1 lesson, proven by the three damaged cells)
            int srcHours = nonStageHours(srcDay);
            int sheet = quota.getOrDefault(srcDate, 0);
            int yieldLine = poolAwareYieldLine(gid, srcDate, sheet, floor);
            // TASK-671 census: distinguish donors the POOL-CAP line admitted from
            // those the raw sheet line would have (the observable signal that the
            // coherence fix actually revived the arm on pool groups).
            if (yieldLine < sheet) {
                census("HRF-IMP-POOL-CAP-LINE");
            }
            Timeslot srcTs = cand.timeslot;
            Room srcRoom = cand.room;
            // depth-1 backfill, by KIND: the SAME-DAY tail slide only REARRANGES (the
            // source nets -1) so it is the surplus path's anti-hole device; the
            // CROSS-DAY backfill RESTORES the count and is the only valid device when
            // the source is not surplus (the iter1 arithmetic bug, fixed in iter2)
            Lesson backfill = null;
            boolean surplusDay = srcHours - 1 >= yieldLine;
            boolean vacatedInternal = srcSlot < srcDay.lastKey();
            if (!surplusDay) {
                outer: for (var e : live.days.entrySet()) {
                    if (!e.getKey().startsWith(group + "|")
                            || e.getKey().equals(hole.dayKey())
                            || e.getKey().equals(dayKeyOf(group, srcDate))) {
                        continue;
                    }
                    LocalDate d3 = LocalDate.parse(e.getKey().substring(e.getKey().indexOf('|') + 1));
                    if (nonStageHours(e.getValue()) - 1 < poolAwareYieldLine(gid, d3,
                            quota.getOrDefault(d3, 0), floor)) {
                        continue;
                    }
                    for (Lesson y : e.getValue().values()) {
                        if (!movable(y) || !y.isSlotLegalFresh(srcTs)) {
                            continue;
                        }
                        if (TimeslotSequence.slotIndexOf(y.timeslot) == e.getValue().firstKey()) {
                            census("HRF-IMP-H31-BF"); // the donor's first hour is not exportable either
                            continue;
                        }
                        // review gate 16/09 (F1, scorer 75, 3 reviewer concordi): the
                        // backfill ADDS a teacher hour on the source date exactly as the
                        // import does on the hole's date - without this cap the composite
                        // can trade the closed H17 for a new H7 at net-zero hard, which
                        // the wrapper's improve-or-noop accepts as a tie (TASK-616 class).
                        if (y.getTeacher() != null && !y.getTeacher().pseudo && live.teacherHoursOn(
                                y.getTeacher(), srcTs.getDate()) >= y.getTeacher()
                                        .getEffectiveMaxDailyHours()) {
                            census("HRF-IMP-H7-BF");
                            continue;
                        }
                        if (y.getTeacher() != null && !y.getTeacher().pseudo
                                && live.teacherOf(y.getTeacher(), srcTs) != null) {
                            continue;
                        }
                        backfill = y;
                        break outer;
                    }
                }
                if (backfill == null) {
                    census("HRF-IMP-SOURCE-GATE");
                    continue;
                }
            } else if (vacatedInternal) {
                // surplus day, but the vacated cell sits inside the span: a same-day
                // tail slide (if legal) prevents the hole; without one, reject
                Lesson tail = srcDay.lastEntry().getValue();
                // review F4 16/09: the twin's NET-form allowance is restored - when the
                // cell's occupant IS the candidate itself, the import vacates it by
                // construction (the f3265c69 lesson: an occupant that moves is the
                // permission, not the blocker); dropping it made the port strictly
                // narrower than the banco evidence that justified it.
                if (tail != cand && movable(tail) && tail.isSlotLegalFresh(srcTs)
                        && (tail.getTeacher() == null || tail.getTeacher().pseudo
                                || live.teacherOf(tail.getTeacher(), srcTs) == null
                                || live.teacherOf(tail.getTeacher(), srcTs) == cand)) {
                    backfill = tail;
                } else {
                    census("HRF-IMP-TAIL-BF");
                    continue;
                }
            }
            // apply: import first, then the backfill into the vacated cell
            CompositeRecorder rec = new CompositeRecorder(moveTimeslot);
            Room roomHole = roomFor(live, cand, holeTs);
            if (roomHole == null) {
                census("HRF-IMP-ROOM");
                continue;
            }
            rec.apply(live, cand, holeTs, roomHole);
            if (backfill != null) {
                Room roomBf = roomFor(live, backfill, srcTs);
                if (roomBf == null) {
                    census("HRF-IMP-ROOM-BF");
                    rec.revert(live);
                    continue;
                }
                rec.apply(live, backfill, srcTs, roomBf);
            }
            // belt: the composite scan over the touched dates (the pass's own) AND the
            // no-new-holes check reading the donor's ORIGIN day (iter3's dead-code fix)
            boolean ok = verifyNoNewHoles(live, group, hole.date())
                    && (backfill == null || verifyNoNewHoles(live, group, srcDate))
                    && (backfill == null || rec.originOf(backfill).getDate().equals(srcDate)
                            || verifyNoNewHoles(live, group, rec.originOf(backfill).getDate()))
                    && verifyComposite(live, rec.lessons()) == null;
            if (!ok) {
                census("HRF-IMP-BELT");
                rec.revert(live);
                continue;
            }
            if (Boolean.getBoolean("hrf.debug")) {
                System.out.printf("[HRF-IMP-applied] %s hole %s@%d <- %s(%s) from %s@%d%s%n",
                        group, hole.date(), hole.slot(), cand.course.subject,
                        cand.getTeacher().name, srcDate, srcSlot,
                        backfill == null ? " (surplus source, no backfill)"
                                : " + backfill " + backfill.course.subject);
            }
            return rec.size();
        }
        census("HRF-IMP-NO-CANDIDATE");
        return 0;
    }

    /** Non-Stage hours of a group-day: Stage rows occupy cells but not the quota. */
    private static int nonStageHours(TreeMap<Integer, Lesson> day) {
        int n = 0;
        for (Lesson l : day.values()) {
            if (l.getTeacher() == null || !l.getTeacher().pseudo) {
                n++;
            }
        }
        return n;
    }

    /** An import must not OPEN a hole on any touched day (shell-game guard). */
    private static boolean verifyNoNewHoles(Live live, String group, LocalDate date) {
        TreeMap<Integer, Lesson> day = live.dayOf(dayKeyOf(group, date));
        if (day == null || day.size() < 2) {
            return true;
        }
        for (int s = day.firstKey() + 1; s < day.lastKey(); s++) {
            if (!day.containsKey(s)) {
                return false;
            }
        }
        return true;
    }

    private static String dayKeyOf(String group, LocalDate date) {
        return group + "|" + date;
    }


    /**
     * TASK-656: the GATE-E yield line goes pool-cap-aware. The GATE-E semantics
     * stay "max(target, floor)" but the TARGET for the over-capacity groups is
     * the RATIFIED TASK-497 per-date pool target, not the sheet quota: the
     * construction stopped following the sheet for those groups on TASK-577
     * (9/09) while this gate kept evaluating donors against it, so for G04
     * the import arm was structurally dead (zero surplus days out of 140 in
     * the integrato-7 delivery: every day sat below the sheet line BY DESIGN).
     * Groups without a pool target keep the sheet quota (13 of 14 unchanged).
     * Mirror of expectedHoursCapFor's TASK-577 min() on the repair side.
     */
    private static int poolAwareYieldLine(Long groupId, LocalDate date, int sheet, int floor) {
        // TASK-671: the min() now lives in SchoolSchedule.poolCappedQuotaForDate
        // (single source for capSupply, expectedHoursCapFor and every repair site);
        // this wrapper just adds the max(floor) the GATE-E semantics require.
        return Math.max(SchoolSchedule.poolCappedQuotaForDate(groupId, date, sheet), floor);
    }

    private static Long groupIdOf(Live live, String groupName) {
        for (var e : live.days.entrySet()) {
            if (e.getKey().startsWith(groupName + "|")) {
                return e.getValue().firstEntry().getValue().course.studentGroup.id;
            }
        }
        return null;
    }

    /**
     * TASK-621ext (the CROSS-DATE-SWAP 1-per-1, ported from the bank twin
     * Combo621ExtDiff iter3): import the donor day's TAIL lesson into the hole,
     * and the hole-day's own TAIL lands on the donor's just-vacated tail cell.
     * Belt is HOLES-MONOTONE (holes_after <= holes_before on the hole day; zero
     * new holes on the donor day), plus the H28 transversal gate. Census
     * HRF-XSWAP-*.
     */
    private static int tryCrossDateSwap(Live live, BiConsumer<Lesson, Timeslot> moveTimeslot,
            Hole hole, Timeslot holeTs, TreeMap<Integer, Lesson> day) {
        Lesson expelled = day.lastEntry().getValue();
        if (!movable(expelled)) {
            census("HRF-XSWAP-TAIL-MOVABLE");
            return 0;
        }
        final int holesBefore = holeCountOf(live, hole.groupName(), hole.date());
        List<Map.Entry<String, TreeMap<Integer, Lesson>>> donors =
                sameGroupDaysByDistance(live, hole.groupName(), hole.dayKey(), hole.date());
        for (var de : donors) {
            if (!de.getKey().startsWith(hole.groupName() + "|")
                    || de.getKey().equals(hole.dayKey()) || de.getValue().isEmpty()) {
                continue;
            }
            Lesson donorTail = de.getValue().lastEntry().getValue();
            LocalDate donorDate = live.dateOf(de.getKey());
            if (!movable(donorTail) || !donorTail.isSlotLegalFresh(holeTs)) {
                census("HRF-XSWAP-DONOR-VRP");
                continue;
            }
            Lesson donorHolder = donorTail.getTeacher() == null || donorTail.getTeacher().pseudo
                    ? null : live.teacherOf(donorTail.getTeacher(), holeTs);
            if (donorHolder != null && donorHolder != expelled) {
                census("HRF-XSWAP-DONOR-H1");
                continue;
            }
            Timeslot vacated = live.tsAt(donorDate, de.getValue().lastKey());
            if (vacated == null) {
                continue;
            }
            if (!expelled.isSlotLegalFresh(vacated)) {
                census("HRF-XSWAP-EXP-VRP");
                continue;
            }
            Lesson expHolder = expelled.getTeacher() == null || expelled.getTeacher().pseudo
                    ? null : live.teacherOf(expelled.getTeacher(), vacated);
            if (expHolder != null && expHolder != donorTail) {
                census("HRF-XSWAP-EXP-H1");
                continue;
            }
            // review gate 17/09 (616-class, third occurrence): both landings ADD a
            // teacher hour on their arrival date - without these caps the swap can
            // trade the closed H17 for a new H7 at net-zero hard (oracle keeps ties).
            if (donorTail.getTeacher() != null && !donorTail.getTeacher().pseudo && live.teacherHoursOn(
                    donorTail.getTeacher(), hole.date()) >= donorTail.getTeacher()
                            .getEffectiveMaxDailyHours()) {
                census("HRF-XSWAP-H7");
                continue;
            }
            if (expelled.getTeacher() != null && !expelled.getTeacher().pseudo && live.teacherHoursOn(
                    expelled.getTeacher(), donorDate) >= expelled.getTeacher()
                            .getEffectiveMaxDailyHours()) {
                census("HRF-XSWAP-H7-EXP");
                continue;
            }
            final int h28BeforeHoleSide = h28ReturnsIn(live, hole.groupName(),
                    donorTail.course.subject, hole.date());
            final int h28BeforeDonorSide = h28ReturnsIn(live, hole.groupName(),
                    expelled.course.subject, donorDate);
            CompositeRecorder rec = new CompositeRecorder(moveTimeslot);
            Room r1 = roomFor(live, donorTail, holeTs);
            if (r1 == null) {
                census("HRF-XSWAP-ROOM");
                continue;
            }
            rec.apply(live, donorTail, holeTs, r1);
            Room r2 = roomFor(live, expelled, vacated);
            if (r2 == null) {
                census("HRF-XSWAP-ROOM-EXP");
                rec.revert(live);
                continue;
            }
            rec.apply(live, expelled, vacated, r2);
            boolean ok = holeCountOf(live, hole.groupName(), hole.date()) <= holesBefore
                    && verifyNoNewHoles(live, hole.groupName(), donorDate)
                    && h28PairSafe(live, donorTail, expelled, h28BeforeHoleSide, h28BeforeDonorSide)
                    && verifyComposite(live, rec.lessons()) == null;
            if (!ok) {
                census("HRF-XSWAP-BELT");
                rec.revert(live);
                continue;
            }
            if (Boolean.getBoolean("hrf.debug")) {
                System.out.printf("[HRF-XSWAP-applied] %s hole %s@%d: donor tail %s <- %s,"
                                + " expelled %s -> %s@%d%n",
                        hole.groupName(), hole.date(), hole.slot(), donorTail.course.subject,
                        donorDate, expelled.course.subject, donorDate,
                        TimeslotSequence.slotIndexOf(vacated));
            }
            return 2;
        }
        census("HRF-XSWAP-NO-DONOR");
        return 0;
    }

    /** Internal-gap count of a group-day (the holes-monotone belt's measure). */
    private static int holeCountOf(Live live, String group, LocalDate date) {
        TreeMap<Integer, Lesson> d = live.days.get(group + "|" + date);
        if (d == null || d.size() < 2) {
            return 0;
        }
        int n = 0;
        for (int s = d.firstKey() + 1; s < d.lastKey(); s++) {
            if (!d.containsKey(s)) {
                n++;
            }
        }
        return n;
    }

    /**
     * The H28 transversal belt (the blind-r1 -4 -> -5 lesson, cured 17/09 review):
     * the pair is legal only if the return count of every (group, subject, date)
     * tuple its two movers touch does not INCREASE. The count is TUPLE-level and
     * teacher-agnostic on the between-lesson (the review finding: a mover landing
     * on an internal slot can be the B of ANOTHER teacher's A..B..A, which the
     * mover-teacher-centric first cut could not see); the before/after comparison
     * keeps pre-existing returns (the delivered H28 residual) from blocking
     * unrelated cures.
     */
    private static int h28ReturnsIn(Live live, String group, String subject, LocalDate date) {
        TreeMap<Integer, Lesson> d = live.days.get(group + "|" + date);
        if (d == null) {
            return 0;
        }
        // TASK-617: the day map is already keyed group|date, so the group name
        // re-check was redundant; d.values() is TreeMap-ordered by slot (the
        // filter-first, no-re-sort shape the consolidation called for)
        List<Lesson> tuple = new ArrayList<>();
        for (Lesson x : d.values()) {
            if (x.course != null && subject.equals(x.course.subject)) {
                tuple.add(x);
            }
        }
        int returns = 0;
        for (int i = 0; i < tuple.size(); i++) {
            var ti = tuple.get(i).getTeacher();
            if (ti == null || ti.pseudo) {
                continue;
            }
            for (int k = i + 1; k < tuple.size(); k++) {
                var tk = tuple.get(k).getTeacher();
                if (tk == null || !ti.name.equals(tk.name)) {
                    continue;
                }
                for (int j = i + 1; j < k; j++) {
                    var tj = tuple.get(j).getTeacher();
                    if (tj == null || !ti.name.equals(tj.name)) {
                        returns++;
                        break;
                    }
                }
            }
        }
        return returns;
    }

    /** The gate form for a swap pair: both touched tuples' counts must not grow. */
    private static boolean h28PairSafe(Live live, Lesson a, Lesson b,
            int beforeA, int beforeB) {
        return h28ReturnsIn(live, a.course.studentGroup.name, a.course.subject,
                a.timeslot.getDate()) <= beforeA
                && h28ReturnsIn(live, b.course.studentGroup.name, b.course.subject,
                        b.timeslot.getDate()) <= beforeB;
    }

    /** TASK-633: true when mover is a Sicurezza lesson sitting at a date at or
     *  past its group's earliest StagePattern start (the H30 firing edge, same
     *  subject normalization as the provider's sicurezzaStagePairs join). */
    private static boolean h30Unsafe(Lesson mover, Live live) {
        if (mover.course == null || mover.course.studentGroup == null
                || mover.timeslot == null || mover.getSubject() == null) {
            return false;
        }
        LocalDate stageStart = live.stageStartByGroup.get(
                mover.course.studentGroup.name);
        return stageStart != null
                && !mover.timeslot.getDate().isBefore(stageStart)
                && BaseConstraintProvider.SICUREZZA_SUBJECT.equals(
                        SubjectNormalization.normalize(mover.getSubject()));
    }

    /**
     * blind-live-r1 lever A (RATIFIED DEFAULT ON 16/09, endgame bundle): the COMPACT-SWAP
     * INTRA-CLASSE.
     * For an internal hole whose plain tail-slide is blocked, try the two-lesson
     * permutation INSIDE the same class-day: the lesson BELOW the hole steps UP
     * into it and the day's TOP lesson steps DOWN into the freed cell (span and
     * count preserved; the down-stepper must BE the last lesson, so the vacancy
     * lands above the new span and no new hole can be born). Gates are the pass's
     * own: per-lesson VRP first (conflict-independent), H1 final-state per landing
     * cell IGNORING the pair partner (a same-teacher pair lands on two cells),
     * TASK-616 room gate at apply (roomFor null reverts), verifyComposite as the
     * belt. Rejection census HRF-CS-*. Returns the moves applied (2) or 0.
     */
    private static int tryCompactSwap(Live live, BiConsumer<Lesson, Timeslot> moveTimeslot,
                                      Timeslot holeTs, TreeMap<Integer, Lesson> day,
                                      Hole hole) {
        Lesson top = day.lastEntry().getValue();
        boolean topMovable = movable(top);
        for (int p = hole.slot() - 1; p >= day.firstKey(); p--) {
            if (!day.containsKey(p)) {
                continue;
            }
            Lesson below = day.get(p);
            if (below == top || !movable(below) || !topMovable) {
                census("HRF-CS-MOVABLE");
                continue;
            }
            Timeslot belowTs = live.tsAt(hole.date(), p);
            if (belowTs == null) {
                continue;
            }
            if (!below.isSlotLegalFresh(holeTs) || !top.isSlotLegalFresh(belowTs)) {
                census("HRF-CS-VRP");
                continue;
            }
            if (below.getTeacher() != null && !below.getTeacher().pseudo) {
                Lesson holder = live.teacherOf(below.getTeacher(), holeTs);
                if (holder != null && holder != top) {
                    census("HRF-CS-H1-BELOW");
                    continue;
                }
            }
            if (top.getTeacher() != null && !top.getTeacher().pseudo) {
                Lesson holder = live.teacherOf(top.getTeacher(), belowTs);
                if (holder != null && holder != below) {
                    census("HRF-CS-H1-TOP");
                    continue;
                }
            }
            CompositeRecorder rec = new CompositeRecorder(moveTimeslot);
            Room roomBelow = roomFor(live, below, holeTs);
            if (roomBelow == null) {
                census("HRF-CS-ROOM-BELOW");
                continue;
            }
            rec.apply(live, below, holeTs, roomBelow);
            Room roomTop = roomFor(live, top, belowTs);
            if (roomTop == null) {
                census("HRF-CS-ROOM-TOP");
                rec.revert(live);
                continue;
            }
            rec.apply(live, top, belowTs, roomTop);
            if (verifyComposite(live, List.of(below, top)) != null) {
                rec.revert(live);
                continue;
            }
            if (Boolean.getBoolean("hrf.debug")) {
                System.out.printf("[HRF-CS-applied] %s %s hole@%d: %s(%s) %d->%d + %s(%s) %d->%d%n",
                        hole.groupName(), hole.date(), hole.slot(),
                        below.course.subject, below.getTeacher().name,
                        rec.originOf(below).startDateTime.getHour(), hole.slot(),
                        top.course.subject, top.getTeacher().name,
                        rec.originOf(top).startDateTime.getHour(), p);
            }
            return 2;
        }
        return 0;
    }

    /** TASK-617: same-group days (excluding excludeKey), sorted by |date - target|:
     *  the four arms' destination/donor iteration order, extracted once. */
    private static List<Map.Entry<String, TreeMap<Integer, Lesson>>> sameGroupDaysByDistance(
            Live live, String group, String excludeKey, LocalDate target) {
        List<Map.Entry<String, TreeMap<Integer, Lesson>>> out = new ArrayList<>(live.days.entrySet());
        out.removeIf(e -> !e.getKey().startsWith(group + "|") || e.getKey().equals(excludeKey));
        out.sort((a, b) -> Long.compare(
                Math.abs(live.dateOf(a.getKey()).toEpochDay() - target.toEpochDay()),
                Math.abs(live.dateOf(b.getKey()).toEpochDay() - target.toEpochDay())));
        return out;
    }

    /**
     * TASK-617: the shared COMPOSITE RECORDER (extraction 1). The origTs/origRoom/
     * order + revertPair pattern was copy-pasted in five arms; this record carries
     * the same three pieces and owns apply (move + callback + bookkeeping) and
     * revert (strict reverse order). Every arm now uses this, so the class-616
     * checklist (record BEFORE the first move, revert in reverse, never reuse the
     * recorder across candidates) lives in exactly one place.
     */
    private static final class CompositeRecorder {
        private final Map<Lesson, Timeslot> origTs = new HashMap<>();
        private final Map<Lesson, Room> origRoom = new HashMap<>();
        private final List<Lesson> order = new ArrayList<>();
        private final BiConsumer<Lesson, Timeslot> moveTimeslot;

        CompositeRecorder(BiConsumer<Lesson, Timeslot> moveTimeslot) {
            this.moveTimeslot = moveTimeslot;
        }

        /** Apply one move and remember the origin: call BEFORE moving the lesson. */
        void apply(Live live, Lesson l, Timeslot to, Room room) {
            origTs.put(l, l.timeslot);
            origRoom.put(l, l.room);
            order.add(l);
            live.move(l, to, room);
            moveTimeslot.accept(l, to);
        }

        /** Revert everything in strict reverse application order. */
        void revert(Live live) {
            for (int i = order.size() - 1; i >= 0; i--) {
                Lesson l = order.get(i);
                live.move(l, origTs.get(l), origRoom.get(l));
                moveTimeslot.accept(l, origTs.get(l));
            }
        }

        int size() {
            return order.size();
        }

        /** The lesson's ORIGIN timeslot as recorded before its move (the H28 and
         *  no-new-holes checks on a partner's origin day read this). */
        Timeslot originOf(Lesson l) {
            return origTs.get(l);
        }

        List<Lesson> lessons() {
            return order;
        }
    }

    /**
     * The ROTATION-WITH-PERMISSION shape, FINAL-STATE SEMANTICS (v0.4, the
     * depth-cap experiment's verdict): the blind's puzzle rules bind the FINAL
     * state only, and its D1 cascade passes through intermediate H1s that the
     * composition resolves - demanding per-step legality (v0.2-v0.3) made the
     * chain gate-bound with H1NET dominant. So the chain is built SYMBOLICALLY
     * (no live application, no per-batch occupancy gates: only the per-lesson
     * VRP, which is conflict-independent), then the whole composite is applied
     * at once (rooms resolved and GATED per member at apply time, TASK-616) and
     * verified with a REAL conflict scan over the affected dates
     * (teacher/group double-bookings), reverting everything on failure.
     */
    private static int tryRotationThenSlide(Live live, BiConsumer<Lesson, Timeslot> moveTimeslot,
                                            Timeslot holeTs, Lesson tail, Lesson contender,
                                            int depth, java.util.Set<String> visited) {
        java.util.List<Lesson> chainLessons = new ArrayList<>();
        java.util.List<Timeslot> chainDests = new ArrayList<>();
        // TASK-616: the tail's own VRP was only gated on the plain-slide branch;
        // a composite must not land it on a slot its course cannot occupy (the
        // date-keyed families would trade the closed H17 for a new violation).
        // The check is conflict-independent, so it runs BEFORE the symbolic DFS:
        // fail fast, no chain built to discard.
        if (!tail.isSlotLegalFresh(holeTs)) {
            census("HRF-COMP-VRP-TAIL");
            return 0;
        }
        // the TAIL is a known mover (the slide vacates it): a dest whose occupant
        // IS the tail is not a blocker, it is the permission itself (the v0.4 stall:
        // the builder recursed to vacate the tail from its own slide destination)
        java.util.List<Lesson> knownMovers = new ArrayList<>();
        knownMovers.add(tail);
        String tailTeacher = tail.getTeacher() == null ? null : tail.getTeacher().name;
        if (!buildChain(live, contender, depth, visited, knownMovers,
                chainLessons, chainDests, holeTs, tailTeacher)) {
            return 0;
        }
        // the slide completes the composite
        chainLessons.add(tail);
        chainDests.add(holeTs);
        if (Boolean.getBoolean("hrf.debug")) {
            StringBuilder b = new StringBuilder("[DIFF-built] target " + tail.course.studentGroup.name
                    + " " + holeTs.getDate() + " chain:");
            for (int i = 0; i < chainLessons.size(); i++) {
                b.append(' ').append(chainLessons.get(i).course.studentGroup.name).append('#')
                        .append(chainLessons.get(i).id).append(' ')
                        .append(chainLessons.get(i).timeslot.startDateTime.getHour()).append("->")
                        .append(chainDests.get(i).startDateTime.getHour());
            }
            System.out.println(b);
        }
        // composite apply (rooms resolved sequentially at apply time, so a member
        // sees the rooms the earlier members just took), full verification after.
        // TASK-616: roomFor null (no family room free at the landing hour) REVERTS
        // the composite: a room-null lesson would be an H29 hard that can tie the
        // closed H17 at net-zero hard, which the wrapper's improve-or-noop accepts.
        List<Timeslot> origs = new ArrayList<>();
        List<Room> origRooms = new ArrayList<>();
        for (int i = 0; i < chainLessons.size(); i++) {
            Lesson l = chainLessons.get(i);
            origs.add(l.timeslot);
            origRooms.add(l.room);
            Room room = roomFor(live, l, chainDests.get(i));
            if (room == null) {
                census("HRF-COMP-ROOM");
                for (int j = i - 1; j >= 0; j--) {
                    live.move(chainLessons.get(j), origs.get(j), origRooms.get(j));
                }
                return 0;
            }
            live.move(l, chainDests.get(i), room);
        }
        String reject = verifyComposite(live, chainLessons);
        if (reject != null) {
            census("HRF-COMP-" + reject);
            for (int i = chainLessons.size() - 1; i >= 0; i--) {
                live.move(chainLessons.get(i), origs.get(i), origRooms.get(i));
            }
            return 0;
        }
        if (Boolean.getBoolean("hrf.debug")) {
            StringBuilder tr = new StringBuilder("[HRF-trace] ");
            for (int i = 0; i < chainLessons.size(); i++) {
                tr.append(chainLessons.get(i).course.studentGroup.name).append('#')
                        .append(chainLessons.get(i).id).append(' ')
                        .append(origs.get(i).startDateTime.getHour()).append("->")
                        .append(chainDests.get(i).startDateTime.getHour()).append(' ');
            }
            System.out.println(tr);
        }
        for (int i = 0; i < chainLessons.size(); i++) {
            moveTimeslot.accept(chainLessons.get(i), chainDests.get(i));
        }
        return chainLessons.size();
    }

    /** Drop everything appended after {@code mark}: a failed branch unwinds. */
    private static void truncate(List<?> list, int mark) {
        list.subList(mark, list.size()).clear();
    }

    /**
     * Symbolic chain builder: insertion-rotate {@code mover} to another hour of
     * its day (lessons between shift by one), recursing on
     * same-teacher blockers WITHOUT applying anything (final-state semantics:
     * the per-step occupancy is irrelevant because the composite applies
     * atomically and the belt is {@link #verifyComposite}; the VRP gate stays
     * per-lesson because it is conflict-independent). An occupant that is
     * already a KNOWN MOVER of the composite (the tail, or a lesson already in
     * the chain) is not a blocker: it vacates by construction. Returns false
     * when no chain completes within depth/visited. The teacher-aware checks
     * need the hole cell and the tail's teacher: the bench prototype held them
     * in throwaway statics, the src port threads them as parameters (no
     * cross-target leakage, no state).
     */
    private static boolean buildChain(Live live, Lesson mover, int depth,
                                      java.util.Set<String> visited, java.util.List<Lesson> knownMovers,
                                      List<Lesson> chainLessons, List<Timeslot> chainDests,
                                      Timeslot holeTs, String tailTeacher) {
        if (depth <= 0) {
            census("HRF-DEPTH");
            return false;
        }
        String rotKey = live.dayKeyOf(mover);
        if (!visited.add(rotKey)) {
            census("HRF-VISITED");
            return false;
        }
        try {
            TreeMap<Integer, Lesson> rotDay = live.dayOf(rotKey);
            LocalDate rotDate = live.dateOf(rotKey);
            int from = TimeslotSequence.slotIndexOf(mover.timeslot);
            for (int to : new ArrayList<>(rotDay.keySet())) {
                if (to == from) {
                    continue;
                }
                List<Lesson> tryLessons = new ArrayList<>();
                List<Timeslot> tryDests = new ArrayList<>();
                boolean ok = true;
                for (int s : rotDay.keySet()) {
                    Lesson l = rotDay.get(s);
                    if (!movable(l)) {
                        ok = false;
                        census("HRF-ROT-MOVABLE");
                        break;
                    }
                    int target = s == from ? to
                            : (from < to && s > from && s <= to) ? s - 1
                            : (from > to && s < from && s >= to) ? s + 1 : s;
                    if (target == s) {
                        continue;
                    }
                    Timeslot dest = live.tsAt(rotDate, target);
                    if (dest == null || !l.isSlotLegalFresh(dest)) {
                        ok = false;
                        census(dest == null ? "HRF-ROT-GRID" : "HRF-ROT-VRP");
                        break;
                    }
                    tryLessons.add(l);
                    tryDests.add(dest);
                }
                if (!ok || tryLessons.isEmpty()) {
                    continue;
                }
                // v0.5 branch-mark: a sub-chain that succeeds in a branch later
                // abandoned must NOT stay in the composite (the v0.4/v0.4b 3-H1
                // signature: moves from dead branches accumulated in the chain,
                // exactly the composition defect the spike pointed at). Every
                // failing exit below truncates back to this mark.
                int mark = chainLessons.size();
                int moversMark = knownMovers.size();
                // same-teacher blockers at the destinations: an occupant that is
                // already moving in this composite is the permission, not a block
                // PROTOTYPE CURA (b): teacher-aware. The pre-state same-teacher
                // occupant of a dest is a permission ONLY if it is itself moving
                // (in the batch or already in the chain: its origin IS the cell it
                // vacates); anything else is a TRUE blocker to recurse on. Blanket
                // batch exemption was the v0.4/v0.5 defect (a batch lesson can be
                // DEPOSITED into the critical hour by the insertion itself).
                boolean blocked = false;
                for (int i = 0; i < tryLessons.size() && !blocked; i++) {
                    Lesson m = tryLessons.get(i);
                    Lesson occ = live.teacherOf(m.getTeacher(), tryDests.get(i));
                    if (occ != null && occ != m
                            && !tryLessons.contains(occ) && !knownMovers.contains(occ)) {
                        if (!buildChain(live, occ, depth - 1, visited,
                                knownMovers, chainLessons, chainDests, holeTs, tailTeacher)) {
                            blocked = true;
                        } else {
                            knownMovers.add(occ);
                        }
                    }
                    if (blocked) {
                        break;
                    }
                    // CURA (b2): no same-teacher mover may share a destination with m
                    // (batch or already-chained parents, the tail included via (a)).
                    for (int j = 0; j < tryLessons.size() && !blocked; j++) {
                        if (j != i && tryLessons.get(j).getTeacher() != null
                                && tryLessons.get(j).getTeacher().equals(m.getTeacher())
                                && tryDests.get(j).equals(tryDests.get(i))) {
                            census("HRF-SYM-CODEST");
                            blocked = true;
                        }
                    }
                    for (int j = 0; j < chainLessons.size() && !blocked; j++) {
                        if (chainLessons.get(j).getTeacher() != null
                                && chainLessons.get(j).getTeacher().equals(m.getTeacher())
                                && chainDests.get(j).equals(tryDests.get(i))) {
                            census("HRF-SYM-CODEST-PARENT");
                            blocked = true;
                        }
                    }
                    // CURA (a): nobody with the TAIL's teacher may land on the hole
                    // (the slide puts the tail there).
                    if (tailTeacher != null && m.getTeacher() != null
                            && tailTeacher.equals(m.getTeacher().name)
                            && tryDests.get(i).equals(holeTs)) {
                        census("HRF-SYM-HOLE");
                        blocked = true;
                    }
                }
                if (blocked) {
                    truncate(chainLessons, mark);
                    truncate(chainDests, mark);
                    truncate(knownMovers, moversMark);
                    continue;
                }
                chainLessons.addAll(tryLessons);
                chainDests.addAll(tryDests);
                knownMovers.addAll(tryLessons);
                return true;
            }
            return false;
        } finally {
            // nothing was applied: release the day for sibling branches
            visited.remove(rotKey);
        }
    }

    /**
     * The belt behind the symbolic gates: a REAL conflict scan (putIfAbsent, so
     * CREATED collisions are caught, unlike a single-entry map) over every
     * (group, date) day the composite touches, checking H1 (teacher@hour) and
     * H3 (group@hour) on the final state, plus the H30 leg (TASK-633: no chain
     * lesson may END as a Sicurezza lesson at or past its group's stage start).
     * Rooms are resolved at apply time by {@link #roomFor} and are NOT
     * re-checked here.
     */
    private static String verifyComposite(Live live, List<Lesson> chain) {
        // TASK-633 (code-review high 632, reviewer B): the H30 leg, the shared
        // belt point for EVERY cross-date arm. The chain's lessons are at their
        // FINAL cells here; a Sicurezza lesson left at or past its group's
        // stage start would trade the composite's cured hard for a same-size
        // H30 at net-zero, which the wrapper's NET-hard oracle keeps as a tie.
        // Family-strict by design (the house belt convention): a composite is
        // rejected whenever it LEAVES a chain lesson unsafe (final-state check,
        // even one already unsafe before the move); a composite that moves an
        // already-unsafe Sicurezza lesson to a safe date passes.
        for (Lesson l : chain) {
            if (h30Unsafe(l, live)) {
                return census("HRF-COMP-H30");
            }
        }
        java.util.Set<LocalDate> dates = new java.util.HashSet<>();
        for (Lesson l : chain) {
            dates.add(l.timeslot.getDate());
        }
        for (LocalDate d : dates) {
            Map<String, Lesson> teacherCell = new HashMap<>();
            Map<String, Lesson> groupCell = new HashMap<>();
            for (var e : live.days.entrySet()) {
                if (!live.dateOf(e.getKey()).equals(d)) {
                    continue;
                }
                for (Lesson l : e.getValue().values()) {
                    if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                        String tk = l.getTeacher().name + "@" + l.timeslot.startDateTime.getHour();
                        if (teacherCell.putIfAbsent(tk, l) != null) {
                            if (Boolean.getBoolean("hrf.debug")) {
                                System.out.println("[HRF-verify] H1 collision " + tk + " on " + d
                                        + ": " + teacherCell.get(tk).course.studentGroup.name + "#"
                                        + teacherCell.get(tk).id + " vs " + l.course.studentGroup.name
                                        + "#" + l.id);
                            }
                            return census("HRF-COMP-H1");
                        }
                    }
                    String gk = e.getKey().substring(0, e.getKey().indexOf('|'))
                            + "@" + l.timeslot.startDateTime.getHour();
                    if (groupCell.putIfAbsent(gk, l) != null) {
                        return census("HRF-COMP-H3");
                    }
                }
            }
        }
        return null;
    }

    /**
     * The movable guard for lessons drawn from the live day index (placed by
     * construction): unpinned, real teacher.
     */
    private static boolean movable(Lesson l) {
        if (l.pinned) {
            return false;
        }
        var t = l.getTeacher();
        return t != null && !t.pseudo;
    }

    /**
     * The landing gate: per-slot VRP (H14/calendar/H5 inside), teacher free AND
     * under cap (H1/H7), group free at the cell, and a room of the mover's family
     * available. Censused (HRF-*) under -Dhrf.debug.
     */
    private static String rejectReason(Live live, Lesson mover, Timeslot cand) {
        if (live.groupAt(mover.course.studentGroup.name, cand) != null) {
            return census("HRF-GROUP");
        }
        if (!mover.isSlotLegalFresh(cand)) {
            return census("HRF-VRP");
        }
        if (live.teacherBusy(mover.getTeacher(), cand)) {
            return census("HRF-H1");
        }
        if (live.teacherHoursOn(mover.getTeacher(), cand.getDate())
                >= mover.getTeacher().getEffectiveMaxDailyHours()) {
            return census("HRF-H7");
        }
        if (roomFor(live, mover, cand) == null) {
            return census("HRF-ROOM");
        }
        return null;
    }

    /**
     * Keep the own room when free (and assignable) at the landing, else any free
     * assignable room of the mover's tag family (any room for untagged courses).
     */
    private static Room roomFor(Live live, Lesson mover, Timeslot cand) {
        if (mover.room != null && mover.room.isAssignable()
                && !live.roomTaken(mover.room, cand)) {
            return mover.room;
        }
        for (Room r : live.rooms) {
            boolean familyOk = mover.course.requiredRoomTags.isEmpty()
                    || (r.tags != null && r.tags.stream()
                            .anyMatch(mover.course.requiredRoomTags::contains));
            if (familyOk && r.isAssignable() && !live.roomTaken(r, cand)) {
                return r;
            }
        }
        return null;
    }

    /**
     * An internal H17 hole: a slot strictly between the day's first and last
     * occupied slots, unoccupied, H17's own view (every lesson of the group). v0.2
     * deliberately does NOT reproduce H17's both-neighbors-pinned exemption, so a
     * pinned-excused gap can appear as a target: closing it is hard-neutral and the
     * wrapper oracle arbitrates the batch.
     */
    private record Hole(String groupName, LocalDate date, int slot, String dayKey) {
    }

    private static List<Hole> enumerateHoles(Live live) {
        List<Hole> holes = new ArrayList<>();
        for (var e : live.days.entrySet()) {
            TreeMap<Integer, Lesson> day = e.getValue();
            if (day.size() < 2) {
                continue;
            }
            String dayKey = e.getKey();
            String groupName = dayKey.substring(0, dayKey.indexOf('|'));
            LocalDate date = LocalDate.parse(dayKey.substring(dayKey.lastIndexOf('|') + 1));
            for (int slot = day.firstKey() + 1; slot < day.lastKey(); slot++) {
                if (!day.containsKey(slot)) {
                    holes.add(new Hole(groupName, date, slot, dayKey));
                }
            }
        }
        return holes; // days is a TreeMap: key order is free and reproducible
    }

    /** Debug-only reject census (read at pass end under -Dhrf.debug=true). */
    static final Map<String, Integer> HRF_REJECTS = new HashMap<>();

    private static String census(String key) {
        if (Boolean.getBoolean("hrf.debug")) {
            HRF_REJECTS.merge(key, 1, Integer::sum);
        }
        return key;
    }

    /**
     * The live occupancy view, kept honest through every committed move. Same shape
     * as CompositeSwapFinisher's Live, keyed by group NAME (its dayLessons idiom).
     */
    private static final class Live {
        final List<Room> rooms;
        final List<com.school.planner.domain.DailyActivityQuota> quotas;
        final TreeMap<String, TreeMap<Integer, Lesson>> days = new TreeMap<>();
        final Map<String, Lesson> byGroupCell = new HashMap<>();
        final Map<String, Lesson> byTeacherCell = new HashMap<>();
        final Map<String, Map<Long, Integer>> teacherHoursByDate = new HashMap<>();
        final Map<String, List<Lesson>> roomCell = new HashMap<>();
        final Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot;

        final Map<String, Map<LocalDate, Integer>> quotaByName = new HashMap<>();

        /** TASK-633: per-group EARLIEST StagePattern start (H30 fires from that
         *  date inclusive). Empty for groups without a pattern (11 of 14 on the
         *  2026/27 seed): structurally exempt. */
        final Map<String, LocalDate> stageStartByGroup = new HashMap<>();

        private Live(SchoolSchedule schedule) {
            this.rooms = schedule.getRooms() == null ? List.of() : schedule.getRooms();
            this.quotas = schedule.getDailyActivityQuotas() == null
                    ? List.of() : schedule.getDailyActivityQuotas();
            // TASK-617: the per-group quota map built ONCE (the former quotaOfGroup
            // linear scan ran per candidate; quotaMapFor already existed for one arm)
            for (var q : this.quotas) {
                if (q.studentGroup != null && q.date != null) {
                    quotaByName.computeIfAbsent(q.studentGroup.name, k -> new HashMap<>())
                            .put(q.date, q.aulaHours + q.labHours);
                }
            }
            if (schedule.getStagePatterns() != null) {
                for (var sp : schedule.getStagePatterns()) {
                    if (sp.studentGroup != null && sp.startDate != null) {
                        stageStartByGroup.merge(sp.studentGroup.name, sp.startDate,
                                (a, b) -> a.isBefore(b) ? a : b);
                    }
                }
            }
            this.tsByDateSlot = StudentDayCompaction
                    .buildTimeslotsByDateAndSlot(schedule.getTimeslots());
        }

        static Live capture(SchoolSchedule schedule) {
            Live live = new Live(schedule);
            for (Lesson l : schedule.getLessons()) {
                if (l.timeslot == null || l.course == null
                        || l.course.studentGroup == null) {
                    continue;
                }
                live.place(l, l.timeslot, l.room);
            }
            return live;
        }

        void place(Lesson l, Timeslot t, Room room) {
            days.computeIfAbsent(dayKey(l.course.studentGroup.name, t.getDate()), k -> new TreeMap<>())
                    .put(TimeslotSequence.slotIndexOf(t), l);
            byGroupCell.put(cell(l.course.studentGroup.name, t), l);
            if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                byTeacherCell.put(cell(l.getTeacher().name, t), l);
                teacherHoursByDate.computeIfAbsent(l.getTeacher().name, k -> new HashMap<>())
                        .merge(t.getDate().toEpochDay(), 1, Integer::sum);
            }
            if (room != null) {
                roomCell.computeIfAbsent(room.id + "@" + cell("", t), k -> new ArrayList<>()).add(l);
            }
        }

        void move(Lesson l, Timeslot to, Room room) {
            removeFrom(l, l.timeslot);
            l.timeslot = to;
            l.room = room;
            place(l, to, room);
        }

        private void removeFrom(Lesson l, Timeslot from) {
            if (from == null) {
                return;
            }
            String key = dayKey(l.course.studentGroup.name, from.getDate());
            TreeMap<Integer, Lesson> day = days.get(key);
            if (day != null) {
                day.remove(TimeslotSequence.slotIndexOf(from), l);
                if (day.isEmpty()) {
                    days.remove(key);
                }
            }
            byGroupCell.remove(cell(l.course.studentGroup.name, from), l);
            if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                byTeacherCell.remove(cell(l.getTeacher().name, from), l);
                Map<Long, Integer> hours = teacherHoursByDate.get(l.getTeacher().name);
                if (hours != null) {
                    hours.merge(from.getDate().toEpochDay(), -1, Integer::sum);
                }
            }
            if (l.room != null) {
                List<Lesson> inRoom = roomCell.get(l.room.id + "@" + cell("", from));
                if (inRoom != null) {
                    inRoom.remove(l);
                }
            }
        }

        Timeslot tsAt(LocalDate date, int slot) {
            return tsByDateSlot.getOrDefault(date, Map.of()).get(slot);
        }

        Lesson groupAt(String group, Timeslot t) {
            return byGroupCell.get(cell(group, t));
        }

        /** The lesson some OTHER group's cell holds with this teacher at t, if any. */
        Lesson teacherOf(Teacher teacher, Timeslot t) {
            return byTeacherCell.get(cell(teacher.name, t));
        }

        boolean teacherBusy(Teacher teacher, Timeslot t) {
            return byTeacherCell.containsKey(cell(teacher.name, t));
        }

        int teacherHoursOn(Teacher teacher, LocalDate date) {
            return teacherHoursByDate.getOrDefault(teacher.name, Map.of())
                    .getOrDefault(date.toEpochDay(), 0);
        }

        boolean roomTaken(Room room, Timeslot t) {
            List<Lesson> inRoom = roomCell.get(room.id + "@" + cell("", t));
            return inRoom != null && !inRoom.isEmpty();
        }

        TreeMap<Integer, Lesson> dayOf(String dayKey) {
            TreeMap<Integer, Lesson> day = days.get(dayKey);
            return day == null ? new TreeMap<>() : day;
        }

        String dayKeyOf(Lesson l) {
            return dayKey(l.course.studentGroup.name, l.timeslot.getDate());
        }

        LocalDate dateOf(String dayKey) {
            return LocalDate.parse(dayKey.substring(dayKey.lastIndexOf('|') + 1));
        }
        private static String dayKey(String groupName, LocalDate date) {
            return groupName + "|" + date;
        }

        /** SCHEMA INVARIANT (v0.1 F5): the hour-keyed cell below assumes the slot
         *  schema keeps start hours distinct (SLOT_START_TIMES 8..15); two slots
         *  sharing an hour would silently conflate cells in these maps. The days
         *  map above uses slotIndexOf and does not share the assumption. */
        private static String cell(String key, Timeslot t) {
            return key + "|" + t.getDate() + "@" + t.startDateTime.getHour();
        }
    }
}
