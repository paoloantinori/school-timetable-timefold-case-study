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

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;

/**
 * TASK-560 (26/08, comitato 2 dopo il VERDICT H34): the deterministic FINAL pass
 * for H34's residual empty declared days - sister of QuotaFillRepair v2 and
 * ExactFinisher, born from the offline discriminant probe (10/10 residual cells
 * had first-hour H31-legal candidates, yet QFR v2 closed none: its TAIL-only
 * extraction is the blocker, because those candidate lessons sit in the MIDDLE
 * of their donor days).
 *
 * <p>What this pass lifts, relative to QFR v2's empty-day path: the donor hour
 * may come from the MIDDLE of the donor day, and the donor's lessons ABOVE the
 * extraction slot slide down one slot to close the hole (each slide is itself
 * gate-checked: H1 live view, VRP value range, own-room free at the new slot).
 * Everything else keeps QFR v2's contract:
 * <ul>
 * <li>gate (d): the target's FIRST hour lands at the day's first slot with a
 *     Timeslot, then contiguously upward (a formerly-empty day never starts late);</li>
 * <li>gate (c) atomicity: the target fills ALL the way to its goal (the H34
 *     family the declared quota, the H33 family the floor gap - a partially
 *     filled cell is strictly worse: H32 charges quota-hours while H34 only
 *     forgives one flat unit, and an under-floor cell keeps its H33 deficit);</li>
 * <li>gate (e): a donor yields only hours it holds above max(own quota, practiced
 *     floor);</li>
 * <li>gate (a) room-safety: every moved lesson keeps its room and may only land
 *     where that room is free (or it has none);</li>
 * <li>the donor day's START is never moved: extraction may take any slot (the
 *     relay family may even take the first), and H31 + H17 contiguity are held
 *     by the slide plan ({@link #planSlides}) and the relay's exact refill, not
 *     by a first-slot extraction ban.</li>
 * </ul>
 *
 * <p>v0.2 (27/08, anello 2): the cross-group swap-compound move family
 * (see {@link #V2_SWAP_FLAG}) - measured INERT on every basin ever probed (0
 * compounds: every H1 blocker sits on a day already at quota/floor), RATIFIED
 * DEFAULT ON 16/09 as censused insurance (endgame bundle).
 *
 * <p>Optional families since then (the class name is historical, it describes only
 * the original H34 family): the H33 under-floor targets ({@link #H33_FLAG},
 * TASK-577, RATIFIED DEFAULT ON 04/10), the H32 partial under-quota targets
 * ({@link #H32_FLAG}, TASK-612) and the IMPORT-RELAY move family
 * ({@link #RELAY_FLAG}, TASK-612: at-quota donors whose vacated cell is refilled
 * exactly, terminal sources always pay gate (e)); the TASK-612 pair is RATIFIED
 * DEFAULT ON since 14/09 (gate A/B appaiabile ab-gate2). Branch order per target
 * hour is load-bearing: direct extraction first, then the relay, then the v0.2
 * compound. When the TASK-612 extensions are armed the pass also runs at the
 * delivery boundary (post CompositeSwapFinisher, pre-save) via
 * {@code ScheduleService.runEdfExtensionsOnDelivery}.
 *
 * <p>FLAG {@code planner.construction.empty-day-finisher.enabled}, DEFAULT ON since the
 * ratification (maintainer, 2026-08-27, handoff mandato mattina 27/08): standard opt-OUT form,
 * null/absent -> on, only the explicit literal {@code false} disarms (same form as H34).
 * Evidence (pack {@code task560-results/gate-adozione-edf-v01-2026-08-27.md}): 4/12 residual
 * cells closed on the winner cycle, 49 moves, 0 conflicts added, 0 improve-or-noop reverts;
 * cross-basin curve 4/0/3/2/1/0 (basin-sensitive, average ~1.7 cells, downside = no-op).
 */
public final class EmptyDayFinisher {

    /** TASK-560 flag, RATIFIED DEFAULT ON (maintainer, 2026-08-27): null/absent -> on,
     *  only the literal {@code false} disarms. */
    public static final String FLAG = "planner.construction.empty-day-finisher.enabled";

    /** TASK-560 v0.2 flag (27/08 anello 2): the cross-group swap-compound move family.
     *  RATIFIED DEFAULT ON (maintainer, 16/09, endgame-bundle re-A/B 258ac6cbe: measured
     *  inert on every basin ever probed - 0 compounds, every H1 blocker sits on a day
     *  already at quota/floor - so under the bounded-no-op directive it ships ON as
     *  censused insurance: "0 swap-compound(s)" in the [delivery] log). Standard
     *  opt-OUT form: null/absent -> ON, only the literal "false" disarms. */
    public static final String V2_SWAP_FLAG = "planner.construction.empty-day-finisher.v2-swap.enabled";

    /** Public for ScheduleService's wiring and banner (same form as the sibling passes). */
    public static boolean enabled() {
        String v = System.getProperty(FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    /** Public for ScheduleService's banner. Ratified: absent -> ON. */
    public static boolean v2SwapEnabled() {
        String v = System.getProperty(V2_SWAP_FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    /** TASK-577 (7/09) flag: under-floor cells (H33's partial underfill, invisible
     *  to the empty-day family whose gate is hours == 0) as a SECOND target family,
     *  filled to the group's floor. Includes the quota-fill-excluded groups (G14):
     *  H32/H34 exclude them, H33 does not. RATIFIED DEFAULT ON (maintainer, 04/10 sera,
     *  "accetta e deploya", after the TASK-678 amendments made the arm live on
     *  delivered states: GATE-E floor-form for excluded donors, room-aware landing,
     *  both edges - banco UnderfloorBench678Test). Standard opt-OUT form: null or
     *  any value but the literal "false" leaves it armed. DECLARED TRADE, accepted
     *  with the ratification WITHOUT the pre-flip A/B (maintainer's explicit order): the
     *  floor-form drains AT-QUOTA donors of excluded groups, which M20/M33 still
     *  read at the medium tier (~1000 per deficit hour, belt-invisible); measure it
     *  in the next campaign reading.
     *  <p>KNOWN INTERACTION (code-review 7/09, low): with v2-swap ALSO armed,
     *  the compound's eviction landing may overshoot an H33 cell to floor+1
     *  (M33 medium surplus, no hard; the H34 cells are immune because a
     *  compound landing day must be non-empty). */
    public static final String H33_FLAG = "planner.construction.edf-h33.enabled";

    /** TASK-612 (14/09, figlio TASK-609, "strada verso 0") flag:
     * the IMPORT-RELAY move family. The TASK-608 blind census of the winning
     * 40-move solution showed the H34/H32 cells close via RELAY-2 x11 (the
     * workhorse): the donor is an AT-QUOTA day whose vacated cell is refilled
     * EXACTLY from a true-surplus source (chains up to import + 2 refills).
     * The TASK-609 AC#2 reject census proved why this pass cannot do that
     * unaided: GATE-E (extractive-pure donors only) rejects 88% of the
     * candidates the dances use. The relay keeps GATE-E for every chain
     * TERMINAL (the live hours ledger arbitrates), so no donor ends the pass
     * in deficit. RATIFIED DEFAULT ON (maintainer, 14/09, gate A/B appaiabile
     * ab-gate2: -18 vs -8, criteri a-d + crash-control tutti VERDI): null ->
     * ON, only the literal "false" disarms. */
    public static final String RELAY_FLAG = "planner.construction.edf-relay.enabled";

    /** TASK-612 flag: H32 partial under-quota days (non-empty, hours below
     * quota minus the G09 1h tolerance) as a THIRD target family. H32's own
     * scope (isQuotaFillExcluded for 1/G08 and G14). RATIFIED DEFAULT ON
     * with {@link #RELAY_FLAG} (same gate). */
    public static final String H32_FLAG = "planner.construction.edf-h32.enabled";

    /** blind-live-r1 lever B (tolerance-aware donor gate): see {@link #tolDonorEnabled()}
     *  for the 16/09 ratification. Amends the GATE-E arithmetic (not a pass boundary),
     *  arms only within armed relay/h32 runs at the delivery boundary. */
    public static final String TOL_DONOR_FLAG = "planner.construction.edf-relay.tol-donor.enabled";

    /** Ratified DEFAULT ON (maintainer, 16/09, endgame-bundle re-A/B 258ac6cbe): the
     *  Boundary.DELIVERY scoping plus the no-op-proof A/B (A = B = [-1/0], zero
     *  TOL-DONOR-UNLOCK mid-cycle) close the divergence objection; positive on the
     *  measured basins (B3 -9 -> -8 closing the tolerance-group empty day). Arms
     *  ONLY at the delivery boundary (see {@link Boundary}). Standard opt-OUT form:
     *  null/absent -> ON, only the literal "false" disarms. */
    public static boolean tolDonorEnabled() {
        String v = System.getProperty(TOL_DONOR_FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    /** RATIFIED DEFAULT ON since 04/10 (maintainer): null/absent or "true" -> armed,
     *  any other literal (including "false", "0", "no") disarms - the standard
     *  opt-OUT form shared by every ratified flag (Boolean.parseBoolean
     *  semantics). Public for the banner/tests/wiring. */
    public static boolean h33Enabled() {
        String v = System.getProperty(H33_FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    /** Public for the banner/tests/wiring. RATIFIED DEFAULT ON (maintainer, 14/09,
     *  gate A/B appaiabile ab-gate2: A2 -18 vs B2 -8, criteri a-d tutti VERDI,
     *  controllo crash VERDE): null/absent -> ON, only the literal "false"
     *  disarms (standard opt-out form, like the base FLAG). */
    public static boolean relayEnabled() {
        String v = System.getProperty(RELAY_FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    /** Public for the banner/tests/wiring. RATIFIED DEFAULT ON (maintainer, 14/09,
     *  same gate as {@link #relayEnabled()}): null/absent -> ON, only the literal
     *  "false" disarms. */
    public static boolean h32Enabled() {
        String v = System.getProperty(H32_FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    /** Single access point for "any TASK-612 extension family is armed": the
     *  delivery-boundary call site and the wrapper log derive from this, so a
     *  future family lands in ONE place instead of uncoupled sites (the wiring
     *  rule's "missed one" class). The refreshSolverStatics banner echoes the
     *  individual flags DELIBERATELY (per-flag echo is the silent-flag-drop
     *  mechanism the banner protocol exists for): extend THAT line too when a
     *  family is added. */
    /** The delivery-boundary invocation gate: true when ANY extension family is
     *  armed (relay/h32 TASK-612, h33 TASK-577+678). */
    public static boolean extensionsArmed() {
        return relayEnabled() || h32Enabled() || h33Enabled();
    }

    private EmptyDayFinisher() {
    }

    /** {@code movesApplied} = lessons moved (extractions + slides, both sides);
     *  {@code cellsClosed} = cells closed to their goal, ALL families (an
     *  empty declared day to quota (H34), a partial under-quota day to quota
     *  (H32, RATIFIED 14/09), an under-floor day to floor (H33, RATIFIED 04/10) - the [EDF-H32]
     *  and [EDF-H33] log lines carry the target-count split);
     *  {@code conflictsAdded} must stay 0;
     *  {@code swapCompounds} = v0.2 cross-group evictions that enabled an hour. */
    public record Result(int movesApplied, int cellsClosed, long conflictsAdded, int swapCompounds) {

        private static final Result NOOP = new Result(0, 0, 0, 0);

        public static Result noop() {
            return NOOP;
        }
    }

    /** {@code need} = hours still to place into the cell: the full quota for an
     *  empty declared day (H34 family), the quota deficit for a partial
     *  under-quota day (H32 family), the floor gap for an under-floor day
     *  (H33 family). Drives the fill loop; also the scarcity sort key. */
    private record Cell(String groupName, Long groupId, LocalDate date, String key, int need, boolean tolDonor) {
    }

    /** The mutable state every move family of the pass shares: the day grid, the
     *  slot index, the live H1/room occupancy views, the live per-day hour counts
     *  (gate e) and the current target's undo trail. One per target, built in
     *  finish() - the undo list is per-target because a failed cell discards its
     *  own moves while a compound's failed eviction discards only its own tail. */
    private record Pass(Map<String, TreeMap<Integer, Lesson>> dayLessons,
                        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot,
                        Map<Long, Set<Long>> teachersByTs, Map<Long, Set<Long>> roomsByTs,
                        Map<String, int[]> hoursByKey, List<Object[]> undo,
                        BiConsumer<Lesson, Timeslot> moveTimeslot,
                        Map<String, LocalDate> stageStartByGroup) {
    }

    /** landingRejectReason's vocabulary: census keys AND (v0.2) branch decisions -
     *  a bare string compare on a literal would silently die on a rename. */
    private static final String REJECT_H1 = "H1";
    private static final String REJECT_ROOM = "ROOM";
    private static final String REJECT_VRP = "VRP";

    /**
     * The two boundaries this pass runs at. The lever flags
     * ({@link #TOL_DONOR_FLAG}, {@link #V2_SWAP_FLAG}) arm ONLY at
     * {@link Boundary#DELIVERY}: the A/B endgame attribution (16/09, branch
     * wt/endgame-bundle, claudedocs/spalmone-2026-27/endgame-bundle/attribuzione/)
     * measured them active MID-CYCLE as deterministic trajectory divergence
     * (7 tolerance-unlocked imports mid-cycle, delivery EDF 0 moves, arm B
     * carrying new H7/H28 families), while applied to a live delivered state
     * they are inert and harmless (ATTR-A: -2 invariato). So the cure scopes
     * them to the delivery boundary where the evidence lives.
     */
    public enum Boundary {
        MID_CYCLE, DELIVERY
    }

    /** Test-compat default: DELIVERY (the armed form). Production callers must
     *  pass the boundary explicitly - a new production call site must not
     *  silently inherit the armed behavior. */
    public static Result finish(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot) {
        return finish(schedule, moveTimeslot, Boundary.DELIVERY);
    }

    public static Result finish(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot, Boundary boundary) {
        if (schedule.getTimeslots() == null || !enabled()) {
            return Result.noop();
        }
        boolean tolDonorArmed = boundary == Boundary.DELIVERY && tolDonorEnabled();
        boolean v2SwapArmed = boundary == Boundary.DELIVERY && v2SwapEnabled();
        parseTraceProperty();
        Map<String, TreeMap<Integer, Lesson>> dayLessons = HoleRepair.buildDayLessons(schedule.getLessons());
        if (dayLessons.isEmpty()) {
            return Result.noop();
        }
        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot =
                StudentDayCompaction.buildTimeslotsByDateAndSlot(schedule.getTimeslots());
        long conflictsBefore = StudentDayCompaction.countConflicts(schedule);

        // live per-timeslot occupancy for the H1/room gates (kept in sync by apply/undo)
        Map<Long, Set<Long>> teachersByTs = new HashMap<>();
        Map<Long, Set<Long>> roomsByTs = new HashMap<>();
        for (var day : dayLessons.values()) {
            for (Lesson l : day.values()) {
                index(l, teachersByTs, roomsByTs, true);
            }
        }

        List<Cell> targets = new ArrayList<>();
        int h33Targets = 0;
        int h32Targets = 0;
        boolean relayOn = relayEnabled();
        Map<String, int[]> hoursByKey = new HashMap<>(); // key -> {hours} live through the pass
        // TASK-678 residual (H30 leg): per-group EARLIEST StagePattern start, the
        // HRF Live shape - built once, empty for the 11/14 groups without a pattern.
        Map<String, LocalDate> stageStartByGroup = new HashMap<>();
        if (schedule.getStagePatterns() != null) {
            for (var sp : schedule.getStagePatterns()) {
                if (sp.studentGroup != null && sp.startDate != null) {
                    stageStartByGroup.merge(sp.studentGroup.name, sp.startDate,
                            (a, b) -> a.isBefore(b) ? a : b);
                }
            }
        }
        if (schedule.getStudentGroups() != null) {
            boolean h33On = h33Enabled();
            boolean h32On = h32Enabled();
            for (var g : schedule.getStudentGroups()) {
                if (g == null || g.id == null) {
                    continue;
                }
                // hoursByKey covers EVERY group: the H33 family targets the
                // quota-fill-excluded ones too (H32/H34 exclude them, H33 does
                // not - its constraint scopes purely by the floor map), and
                // their donor days must live in the same hour ledger. Do NOT
                // hoist isQuotaFillExcluded to this loop head: that silently
                // kills the H33 family.
                boolean h34Eligible = !SchoolScheduleConstraintProvider.isQuotaFillExcluded(g.name);
                int floor = floorOrZero(g.id);
                boolean h33Armed = h33On && floor > 0;
                for (var row : SchoolSchedule.getExpectedHoursForGroup(g.id).entrySet()) {
                    // TASK-671: pool-cap-aware quota (was raw sheet for all groups;
                    // for 1/G08 the ratified target is the pool cap since TASK-577)
                    int quota = SchoolSchedule.poolCappedQuotaForDate(
                            g.id, row.getKey(), row.getValue().aulaHours + row.getValue().labHours);
                    if (quota <= 0) {
                        continue;
                    }
                    String key = keyOf(g.id, row.getKey());
                    int hours = nonStageHours(dayLessons.get(key));
                    hoursByKey.put(key, new int[] {hours});
                    if (!tsByDateSlot.containsKey(row.getKey())) {
                        continue;
                    }
                    // TASK-612: H32's own scope and tolerance (shared accessor), so the
                    // partial under-quota days become targets too. When both this and
                    // H33 are armed, an under-quota day lands here (filling to quota
                    // reaches the floor as well).
                    int h32Need = h32On && h34Eligible && hours > 0
                            ? quota - SchoolScheduleConstraintProvider.quotaFillTolerance(g.name) - hours
                            : 0;
                    if (h34Eligible && hours == 0) {
                        // blind-live-r1 lever B (flag-gated): no constraint demands
                        // the FULL hour on an empty day (H34 closes with any lesson,
                        // H32 accepts quota-tolerance), so for tolerance groups the
                        // target needs quota - tol hours - the blind round's arbitrated
                        // shape (bench: EmptyDayFinisherTol, -9 -> -8 on live B3). With
                        // the flag OFF (or tol = 0) the need and the donors stay STRICT:
                        // byte-identical to today.
                        int h34Tol = tolDonorArmed
                                ? SchoolScheduleConstraintProvider.quotaFillTolerance(g.name) : 0;
                        targets.add(new Cell(g.name, g.id, row.getKey(), key,
                                Math.max(1, quota - h34Tol), h34Tol > 0));
                    } else if (h32Need > 0) {
                        // lever B: this target's donors may yield down to quota-tolerance
                        targets.add(new Cell(g.name, g.id, row.getKey(), key, h32Need,
                                tolDonorArmed));
                        h32Targets++;
                    } else if (h33Armed && hours > 0 && hours < floor) {
                        targets.add(new Cell(g.name, g.id, row.getKey(), key, floor - hours, false));
                        h33Targets++;
                    }
                }
            }
        }
        targets.sort(Comparator.comparingInt((Cell c) -> c.need)
                .thenComparing(c -> c.groupName).thenComparing(c -> c.date));

        int moves = 0;
        int cellsClosed = 0;
        int swapCompounds = 0;
        for (Cell target : targets) {
            Map<String, int[]> hoursSnapshot = new HashMap<>();
            hoursByKey.forEach((k, v) -> hoursSnapshot.put(k, new int[] {v[0]}));
            Pass pass = new Pass(dayLessons, tsByDateSlot, teachersByTs, roomsByTs,
                    hoursByKey, new ArrayList<>(), moveTimeslot, stageStartByGroup);
            int placed = 0;
            while (placed < target.need) {
                TreeMap<Integer, Lesson> tDay = dayLessons.get(target.key);
                // TASK-678 both edges: a partial day can grow UP (lastKey+1, the
                // v0.1 shape) or DOWN (firstKey-1). A window-capped day whose block
                // already sits at the window top (the 04/10 G14 Friday: slots 0-2,
                // block 1-2) has NO legal cell above - the only legal landing is the
                // gap below, which lastKey+1 never tried. NOTE: this widens the move
                // set of EVERY target family, the ratified default-ON H32 included,
                // not just the H33 family; the up-edge is always tried first, so
                // v0.1-identical states keep their v0.1 landings.
                int[] candidateSlots = tDay == null || tDay.isEmpty()
                        ? new int[] {firstOpenSlotFor(target, tsByDateSlot)}
                        : new int[] {tDay.lastKey() + 1, tDay.firstKey() - 1};
                boolean placedHour = false;
                for (int nextSlot : candidateSlots) {
                    Timeslot targetTs = tsByDateSlot.getOrDefault(target.date, Map.of()).get(nextSlot);
                    if (targetTs == null) {
                        continue;
                    }
                    placedHour = extractionFor(pass, target, targetTs);
                    if (!placedHour && relayOn) {
                        // TASK-612: the direct class is exhausted - try the import-relay
                        // (at-quota donor + exact-cell refill) before the v0.2 compound.
                        placedHour = relayFor(pass, target, targetTs);
                    }
                    if (!placedHour && v2SwapArmed) {
                        // v0.2: the same-group extraction is stuck - try the cross-group
                        // swap-compound (evict the H1 blocker, then retry the extraction).
                        placedHour = compoundFor(pass, target, targetTs);
                        if (placedHour) {
                            swapCompounds++;
                        }
                    }
                    if (placedHour) {
                        break;
                    }
                }
                if (!placedHour) {
                    rollback(pass, pass.undo());
                    // rollback restores hoursByKey move by move; the snapshot restore is
                    // v0.1's belt-and-braces (a failed target leaves the hour counts
                    // exactly as the snapshot took them)
                    hoursByKey.clear();
                    hoursSnapshot.forEach((k, v) -> hoursByKey.put(k, v));
                    break;
                }
                placed++;
            }
            if (placed == target.need && placed > 0) {
                cellsClosed++;
                moves += pass.undo().size();
            }
        }
        long conflictsAfter = StudentDayCompaction.countConflicts(schedule);
        if (Boolean.getBoolean("edf.debug") && !EDF_REJECTS.isEmpty()) {
            System.out.println("[EDF-debug] reject census: " + EDF_REJECTS);
            EDF_REJECTS.clear();
        }
        if (h33Targets > 0) {
            System.out.println("[EDF-H33] under-floor targets=" + h33Targets);
        }
        if (h32Targets > 0) {
            System.out.println("[EDF-H32] under-quota targets=" + h32Targets);
        }
        return new Result(moves, cellsClosed, conflictsAfter - conflictsBefore, swapCompounds);
    }

    /**
     * One hour into {@code targetTs}: find a same-group lesson (unpinned, non-Stage) on a
     * surplus donor day whose landing is gate-legal, extract it (slides closing the donor
     * hole), and record the undo trail. Returns false when no legal hour exists.
     */
    private static boolean extractionFor(Pass pass, Cell target, Timeslot targetTs) {
        String groupPrefix = target.groupId + "@";
        for (var donorEntry : new ArrayList<>(pass.dayLessons().entrySet())) {
            String donorKey = donorEntry.getKey();
            if (!donorKey.startsWith(groupPrefix) || donorKey.equals(target.key)) {
                continue;
            }
            if (!donorDayYields(pass, donorKey, target, "GATE-E")) {
                continue;
            }
            TreeMap<Integer, Lesson> donorDay = pass.dayLessons().get(donorKey);
            LocalDate donorDate = donorDate(donorKey);
            for (int slot : new ArrayList<>(donorDay.keySet())) {
                // ANY slot is extractable: the slide-down below restores the donor's
                // contiguity AND its first occupied slot (the k+1 lesson lands at k),
                // so the day start never moves (H31) even extracting slot 0.
                Lesson l = donorDay.get(slot);
                if (!relayMovable(l)) {
                    continue;
                }
                // TASK-680: the shared triplet (range + H30 + H28) lives in
                // crossDateLandingSafe now - same gates, one home.

                // TASK-678 room-aware landing: in a DELIVERED state every room is
                // assigned, and a timeslot-only import drags the donor's room into
                // the target slot where it is usually taken (the 04/10 bench: ~60
                // ROOM rejects, zero moves, the under-floor target left open). The
                // carried-room conflict (REJECT_ROOM) is therefore NOT final here:
                // it defers to the reassignment scan below - the TASK-641 composite
                // grammar (move + room reassign) at this landing. H1 and VRP stay
                // hard prerequisites.
                String landingWhy = landingRejectReason(l, targetTs, pass.teachersByTs(), pass.roomsByTs());
                if (!crossDateLandingSafe(pass, l, targetTs)) {
                    continue; // cheap-first: H1/ROOM/VRP already refused, now the triplet
                }
                if (REJECT_ROOM.equals(landingWhy) && !l.isSlotLegalFresh(targetTs)) {
                    // review F3: landingRejectReason returned early on the ROOM hit,
                    // so the fresh VRP check never ran in this deferred branch.
                    census(REJECT_VRP);
                    continue;
                }
                if (landingWhy != null && !REJECT_ROOM.equals(landingWhy)) {
                    census(landingWhy);
                    if (Boolean.getBoolean("edf.debug") && TRACED.contains(l.id)) {
                        System.out.println("[EDF-trace] lesson=" + l.id + " target=" + target.key
                                + " ownLanding rejected " + landingWhy);
                    }
                    continue;
                }
                Room landingRoom = landingRoomFor(l, targetTs, pass.roomsByTs());
                if (l.room != null && landingRoom == null) {
                    census("ROOM-FREE-SCAN");
                    continue;
                }
                // TASK-678 residual legs (mirroring HRF's, belt-covered until now):
                // H30 - a Sicurezza lesson may not LAND at or past its group's
                // stage start; H28 - the landing may not create an A..B..A return
                // inside the (group, subject, date) cell (the donor side is
                // structurally safe: extraction+slides preserve the day's
                // contiguity, and removing a lesson can only close gaps).

                // the donor hours above the hole slide down one slot each (v0.1's
                // same-teacher-block fix lives inside planSlides: the extracted lesson
                // is dropped from the H1/room indexes for the checks and restored after).
                List<Object[]> slides = planSlides(pass, donorDay, slot, donorDate, l);
                if (slides == null) {
                    census("SLIDES");
                    if (Boolean.getBoolean("edf.debug") && TRACED.contains(l.id)) {
                        System.out.println("[EDF-trace] lesson=" + l.id + " target=" + target.key
                                + " ownLanding=OK but SLIDES failed");
                    }
                    continue;
                }
                if (TRACED.contains(l.id)) {
                    System.out.println("[EDF-trace] lesson=" + l.id + " ACCEPTED for target="
                            + target.key + " ts=" + targetTs.id + " (+" + slides.size() + " slides)");
                }
                applyMoveWithSlides(pass, l, targetTs, slides, landingRoom);
                return true;
            }
        }
        return false;
    }

    /**
     * TASK-612 (14/09) opt-in: the IMPORT-RELAY family mechanized from the TASK-608
     * blind census (RELAY-2 x11, the workhorse; chains to import + 2 refills). GATE-E's
     * extractive-pure donor model is the documented blocker ({@code GATE-E} rejected
     * 88% of the candidates in the AC#2 census while the blind dances replenish); the
     * relay lifts it WITHOUT opening a donor deficit: any same-group lesson may leave
     * for the target, provided its vacated cell is refilled EXACTLY - either directly
     * from a true-surplus day (GATE-E on the live ledger) or through one more relay
     * hop whose terminal always pays GATE-E. Tail slots scan first: the census shape
     * (the exact refill restores the donor day as-was, no slides needed). Every
     * attempt is atomic on the shared undo trail ({@link #rollbackFrom}).
     */
    private static boolean relayFor(Pass pass, Cell target, Timeslot targetTs) {
        String groupPrefix = target.groupId + "@";
        // The GATE-E verdicts are hoisted ONCE per call: failed candidates leave
        // the hours ledger untouched (terminal moves only fire on success, the
        // intermediate hops roll back), and the only ledger change before the
        // first refill (the import itself) touches the target (excluded from the
        // list here) and the donor (whose verdict may go stale, but the refill's
        // visited set excludes it). The bench measured this as the dominant cost
        // of the family (~141k re-evaluations collapsing to one sweep per
        // relayFor); one acceptance ends the call, so no invalidation is needed
        // within it.
        List<String> yielding = new ArrayList<>();
        for (var e : pass.dayLessons().entrySet()) {
            if (e.getKey().startsWith(groupPrefix) && !e.getKey().equals(target.key)
                    && donorDayYields(pass, e.getKey(), target, "RELAY-GATE-E")) {
                yielding.add(e.getKey());
            }
        }
        // The copy of the entry set stays: the destination can be the (empty)
        // target day, and the first placed hour adds its key mid-iteration.
        for (var donorEntry : new ArrayList<>(pass.dayLessons().entrySet())) {
            String donorKey = donorEntry.getKey();
            if (!donorKey.startsWith(groupPrefix) || donorKey.equals(target.key)) {
                continue;
            }
            TreeMap<Integer, Lesson> donorDay = pass.dayLessons().get(donorKey);
            for (int slot : new ArrayList<>(donorDay.descendingKeySet())) {
                Lesson l = donorDay.get(slot);
                if (!relayMovable(l)) {
                    continue;
                }
                if (!legalLanding(l, targetTs, pass.teachersByTs(), pass.roomsByTs())) {
                    continue; // legalLanding already censuses H1/ROOM/VRP
                }
                if (!crossDateLandingSafe(pass, l, targetTs)) {
                    continue; // TASK-680: the relay import pays the same triplet
                }
                int mark = pass.undo().size();
                Timeslot vacated = l.timeslot;
                applyMove(pass, l, targetTs);
                if (refillAt(pass, vacated, target, 2, Set.of(target.key, donorKey), yielding)) {
                    census("RELAY-ACCEPTED");
                    return true;
                }
                rollbackFrom(pass, mark);
                census("RELAY-NO-REFILL");
            }
        }
        return false;
    }

    /**
     * Fill {@code cell} (just vacated by a relay hop) so the losing day keeps its
     * hours AND its shape: a lesson from a TRUE-surplus day lands exactly there
     * (GATE-E on the live ledger), or an intermediate same-group lesson takes the
     * cell and its own vacated cell recurses at {@code depth - 1}. {@code visited}
     * carries the days already on the chain (target + every hop's source): it kills
     * cycles and the intra-day pseudo-refill that would just move the hole.
     * {@code yielding} is the caller's hoisted GATE-E verdict list, valid only for
     * the ledger state the caller computed it under ({@code null} recomputes: the
     * recursion after an intermediate move changed the ledger).
     */
    private static boolean refillAt(Pass pass, Timeslot cell, Cell target, int depth,
                                    Set<String> visited, List<String> yielding) {
        String groupPrefix = target.groupId + "@";
        // terminal hop: a yielding day pays GATE-E on the LIVE ledger, so no donor
        // ends the pass in deficit. The lesson taken is the day's LAST occupied
        // slot ONLY (the census shape, "tail extractions"): GATE-E is hour-based
        // and blind to shape, so an interior departure would pass the gate yet
        // open an H17 hole, and a first-hour departure would move the day's start
        // (H31). The bench pinned exactly this leak (-8 -> -14 with free slots).
        // Plain iteration is safe here: neither branch below can add a map key
        // (the destination day just vacated a lesson, so its key exists), and
        // removeFromDay never removes keys.
        if (yielding == null) {
            yielding = new ArrayList<>();
            for (var e : pass.dayLessons().entrySet()) {
                if (e.getKey().startsWith(groupPrefix) && !e.getKey().equals(target.key)
                        && donorDayYields(pass, e.getKey(), target, "RELAY-GATE-E")) {
                    yielding.add(e.getKey());
                }
            }
        }
        for (String srcKey : yielding) {
            if (visited.contains(srcKey)) {
                continue;
            }
            TreeMap<Integer, Lesson> srcDay = pass.dayLessons().get(srcKey);
            Lesson m = srcDay.get(srcDay.lastKey());
            if (relayMovable(m)
                    && legalLanding(m, cell, pass.teachersByTs(), pass.roomsByTs())
                    && crossDateLandingSafe(pass, m, cell)) {
                applyMove(pass, m, cell);
                census("RELAY-REFILL-TERMINAL");
                return true;
            }
        }
        if (depth <= 1) {
            return false;
        }
        // intermediate hop: the source needs no gate (its cell is refilled by the
        // recursion), but the chain must terminate in a yielding day within depth
        for (var srcEntry : pass.dayLessons().entrySet()) {
            String srcKey = srcEntry.getKey();
            if (!srcKey.startsWith(groupPrefix) || visited.contains(srcKey)) {
                continue;
            }
            for (Lesson m : new ArrayList<>(srcEntry.getValue().values())) {
                if (!relayMovable(m)
                        || !legalLanding(m, cell, pass.teachersByTs(), pass.roomsByTs())
                        || !crossDateLandingSafe(pass, m, cell)) {
                    continue;
                }
                int mark = pass.undo().size();
                Timeslot vacated = m.timeslot;
                Set<String> nextVisited = new HashSet<>(visited);
                nextVisited.add(srcKey);
                applyMove(pass, m, cell);
                if (refillAt(pass, vacated, target, depth - 1, nextVisited, null)) {
                    census("RELAY-REFILL-CHAIN");
                    return true;
                }
                rollbackFrom(pass, mark);
            }
        }
        return false;
    }

    /** The shared movable-lesson guard (unpinned, placed, real teacher): the
     *  extraction's donor scan and both relay hops. NOT a fit for the v0.2
     *  compound's G-side, which deliberately requires a non-null teacher for
     *  its H1 blocker test. */
    private static boolean relayMovable(Lesson l) {
        if (l == null || l.pinned || l.timeslot == null) {
            return false;
        }
        var t = l.getTeacher();
        return t == null || !t.pseudo;
    }

    /** Gate (e), the load-bearing H32/H33 donor-safety invariant shared by v0.1's
     *  donor scan and both sides of the v0.2 compound: a day may yield an hour only
     *  above max(its declared quota, the group's practiced floor). A day with no
     *  published hours entry passes (v0.1 semantics: the gate governs declared days).
     *  {@code censusKey} keeps the debug census per move family. */
    private static boolean donorDayYields(Pass pass, String donorKey, Long groupId, String censusKey) {
        return donorDayYieldsStrict(pass, donorKey, groupId, censusKey);
    }

    /** The original STRICT GATE-E (also used by the cross-group compound's H side,
     *  where the donor group is not the target's group - lever B's territory is the
     *  same-group import paths only). NOTE (TASK-678): this overload keeps
     *  max(quota, floor) even for quota-fill-EXCLUDED donor groups - conservative
     *  (under-fires, never illegal); the floor-form lives in the same-group Cell
     *  overload only, per the per-target-relaxations-are-same-group convention. */
    private static boolean donorDayYieldsStrict(Pass pass, String donorKey, Long groupId,
            String censusKey) {
        return donorDayYieldsAtLeast(pass, donorKey, groupId,
                Math.max(declaredQuota(groupId, donorDate(donorKey)), floorOrZero(groupId)),
                censusKey, -1);
    }

    /**
     * blind-live-r1 lever B (flag-gated via the target's {@code tolDonor}): when the
     * target carries the flag, the donor floor relaxes to quota minus the group's H32
     * tolerance (G09: 1h) - the extraction leaves the donor inside the tolerance
     * H32 itself accepts, so it is neutral, not a new deficit. TOL-DONOR-UNLOCK counts
     * the donors the tolerance alone admitted (the strict gate would have rejected).
     * Unpublished donor days pass, as in the strict gate (v0.1 semantics).
     */
    private static boolean donorDayYields(Pass pass, String donorKey, Cell target, String censusKey) {
        int donorQuota = declaredQuota(target.groupId(), donorDate(donorKey));
        int floor = floorOrZero(target.groupId());
        // TASK-678 floor-form: for the quota-fill-EXCLUDED groups (G14, 1/G08)
        // NO HARD quota family reads the donor day (H32/H34 scope them out), so the
        // binding HARD extraction floor is H33's alone and the strict max(quota,
        // floor) rejects every AT-QUOTA donor - while post-delivery states converge
        // to quota, so the H33 family could never fire on them (the 04/10 bench:
        // [EDF-H33] targets=1, zero moves). DECLARED TRADE (review F2/F5): M20/M33
        // DO read these groups at the MEDIUM tier (the TASK-496 G14 exemption was
        // REMOVED 16/08), so draining an at-quota donor below its quota pays ~1000
        // medium per deficit hour, invisible to the full-hard belt: acceptable
        // under the 0-hard doctrine, to be measured in any future H33 A/B before a
        // default flip. Mirrors the TASK-619 branch-aware SOURCE-YIELD precedent.
        boolean quotaBlind = SchoolScheduleConstraintProvider.isQuotaFillExcluded(target.groupName());
        int strict = quotaBlind ? floor : Math.max(donorQuota, floor);
        int minHours = strict;
        if (target.tolDonor()) {
            minHours = Math.max(donorQuota - SchoolScheduleConstraintProvider
                    .quotaFillTolerance(target.groupName()), floor);
        }
        return donorDayYieldsAtLeast(pass, donorKey, target.groupId(), minHours, censusKey,
                minHours < strict ? strict : -1);
    }

    /** The single GATE-E body: the donor yields iff its day, losing one hour, still
     *  holds {@code minHours}; an {@code unlockAbove} >= 0 censuses the admission as
     *  tolerance-unlocked when the strict floor would have rejected it. */
    private static boolean donorDayYieldsAtLeast(Pass pass, String donorKey, Long groupId,
            int minHours, String censusKey, int unlockAbove) {
        TreeMap<Integer, Lesson> donorDay = pass.dayLessons().get(donorKey);
        if (donorDay == null || donorDay.isEmpty()) {
            return false;
        }
        int[] donorHours = pass.hoursByKey().get(donorKey);
        if (donorHours != null && donorHours[0] - 1 < minHours) {
            census(censusKey);
            return false;
        }
        if (unlockAbove >= 0 && donorHours != null && donorHours[0] - 1 < unlockAbove) {
            census("TOL-DONOR-UNLOCK");
        }
        return true;
    }

    /** The group's practiced day floor (H33/DayFloorDeriver), 0 when unpublished. */
    private static int floorOrZero(Long groupId) {
        Integer floor = SchoolSchedule.getDayFloor(groupId);
        return floor != null ? floor : 0;
    }

    /**
     * v0.2 (TASK-560 anello 2, handoff 27/08): the cross-group swap-compound. The
     * same-group extraction just failed for {@code targetTs}; when the failure is an
     * H1 block (the only kind an eviction can cure - ROOM/VRP are the lesson's own),
     * find the CROSS-GROUP lesson X that holds the target slot with the donor's
     * teacher, relocate X to a same-group day with quota headroom (H32/H33-aware on
     * BOTH sides, H17/H31-safe slides on X's day, border-aware landing on the
     * receiving day), then retry the same-group extraction. Every X move is undone
     * when the retry still fails (a relocation that unblocked nothing is damage).
     */
    private static boolean compoundFor(Pass pass, Cell target, Timeslot targetTs) {
        String groupPrefix = target.groupId + "@";
        for (var donorEntry : new ArrayList<>(pass.dayLessons().entrySet())) {
            String donorKey = donorEntry.getKey();
            if (!donorKey.startsWith(groupPrefix) || donorKey.equals(target.key)) {
                continue;
            }
            if (!donorDayYields(pass, donorKey, target, "V2-GATE-E")) {
                continue; // gate (e) on the G side, same as v0.1
            }
            TreeMap<Integer, Lesson> donorDay = pass.dayLessons().get(donorKey);
            for (int slot : new ArrayList<>(donorDay.keySet())) {
                Lesson lg = donorDay.get(slot);
                if (lg == null || lg.pinned || lg.timeslot == null) {
                    continue;
                }
                var tg = lg.getTeacher();
                if (tg == null || tg.pseudo) {
                    continue;
                }
                // the compound only cures H1 blocks: test teacher-busy directly (the
                // full reject chain would pay a VRP range scan to conclude NOT-H1)
                if (!pass.teachersByTs().getOrDefault(targetTs.id, Set.of()).contains(tg.id)) {
                    census("V2-NOT-H1");
                    continue;
                }
                Lesson x = crossGroupBlockerAt(target, TimeslotSequence.slotIndexOf(targetTs),
                        tg.id, pass.dayLessons());
                if (x == null) {
                    census("V2-NO-BLOCKER");
                    continue;
                }
                if (evictAndFill(pass, x, target, targetTs)) {
                    census("V2-ACCEPTED");
                    return true;
                }
            }
        }
        return false;
    }

    /** The cross-group lesson sitting at (target.date, targetSlot) taught by
     *  {@code teacherId}: unique by H1 itself (no two lessons share a teacher in
     *  one timeslot). */
    private static Lesson crossGroupBlockerAt(Cell target, int targetSlot, Long teacherId,
                                              Map<String, TreeMap<Integer, Lesson>> dayLessons) {
        String groupPrefix = target.groupId + "@";
        String dateSuffix = "@" + target.date;
        for (var e : dayLessons.entrySet()) {
            if (e.getKey().startsWith(groupPrefix) || !e.getKey().endsWith(dateSuffix)) {
                continue;
            }
            Lesson x = e.getValue().get(targetSlot);
            if (x == null || x.pinned || x.timeslot == null) {
                continue;
            }
            var xt = x.getTeacher();
            if (xt != null && !xt.pseudo && teacherId.equals(xt.id)) {
                return x;
            }
        }
        return null;
    }

    /** Gate X's eviction (H-side quota/floor, slides) and landing (headroom day,
     *  border-aware slot, H1/ROOM/VRP); apply the eviction, retry the same-group
     *  extraction, undo the eviction when the retry still fails. */
    private static boolean evictAndFill(Pass pass, Lesson x, Cell target, Timeslot targetTs) {
        var h = x.course != null ? x.course.studentGroup : null;
        if (h == null || h.id == null
                || SchoolScheduleConstraintProvider.isQuotaFillExcluded(h.name)) {
            census("V2-X-EXCLUDED");
            return false;
        }
        String srcKey = keyOf(h.id, target.date);
        // stricter than the G side: an unpublished-hours source day is not evictable
        if (pass.hoursByKey().get(srcKey) == null) {
            census("V2-H-SRC-GATE-E");
            return false;
        }
        if (!donorDayYields(pass, srcKey, h.id, "V2-H-SRC-GATE-E")) {
            return false; // H's day keeps its own quota/floor: no new deficit on H
        }
        int xSlot = TimeslotSequence.slotIndexOf(x.timeslot);
        List<Object[]> slides = planSlides(pass, pass.dayLessons().get(srcKey), xSlot,
                target.date, x);
        if (slides == null) {
            census("V2-H-SLIDES");
            return false;
        }
        for (String landKey : landingDaysOf(pass, h.id, srcKey)) {
            TreeMap<Integer, Lesson> landDay = pass.dayLessons().get(landKey);
            LocalDate landDate = donorDate(landKey);
            for (int s3 : landingSlotsOf(landDay)) {
                Timeslot ts3 = pass.tsByDateSlot().getOrDefault(landDate, Map.of()).get(s3);
                // NOTE (TASK-678, deliberate): the v2-H landing keeps the STRICT gate
                // (any reject reason is final, the carried room is kept as-is): the
                // room-reassignment grammar lives in the same-group extraction only.
                // Extending it here changed ratified v2-swap behavior and polluted
                // the shared bench state (04/10 night): reverted.
                if (ts3 == null
                        || landingRejectReason(x, ts3, pass.teachersByTs(), pass.roomsByTs()) != null) {
                    census("V2-H-LANDING");
                    continue;
                }
                if (!crossDateLandingSafe(pass, x, ts3)) {
                    continue; // TASK-680: the X landing pays the triplet (interior
                              // gaps make the B-position of H28 reachable here)
                }
                int mark = pass.undo().size();
                applyMoveWithSlides(pass, x, ts3, slides, x.room);
                if (extractionFor(pass, target, targetTs)) {
                    return true;
                }
                census("V2-RETRY-FAIL");
                rollbackFrom(pass, mark);
            }
        }
        return false;
    }

    /** The same-group days of {@code groupId} that may absorb one hour: non-empty
     *  (never partially fill another group's empty declared day), still under quota,
     *  most under quota first (the hour lands where H32 hurts most), ties by date. */
    private static List<String> landingDaysOf(Pass pass, Long groupId, String excludeKey) {
        record Land(String key, int deficit, LocalDate date) {
        }
        String groupPrefix = groupId + "@";
        List<Land> out = new ArrayList<>();
        for (var e : pass.dayLessons().entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(groupPrefix) || key.equals(excludeKey) || e.getValue().isEmpty()) {
                continue;
            }
            int[] hours = pass.hoursByKey().get(key);
            if (hours == null || hours[0] == 0) {
                continue;
            }
            LocalDate date = donorDate(key);
            int quota = declaredQuota(groupId, date);
            if (hours[0] + 1 > quota) {
                continue; // headroom: the landing must not create M33 surplus
            }
            out.add(new Land(key, quota - hours[0], date));
        }
        out.sort(Comparator.comparingInt((Land l) -> l.deficit).reversed()
                .thenComparing(l -> l.date));
        List<String> keys = new ArrayList<>();
        for (Land l : out) {
            keys.add(l.key);
        }
        return keys;
    }

    /** Border-aware landing slots on a partially-filled day: interior gaps ascending
     *  (filling a hole is H17-positive), then last+1 (extends the block). Never a
     *  slot above last+1 (would open a gap) and never before the day's start. Same
     *  invariant as QFR v2's border-aware landing (QuotaFillRepair.findBorderAwareSlots,
     *  the ratified form this local copy deliberately does not generalize). */
    private static List<Integer> landingSlotsOf(TreeMap<Integer, Lesson> day) {
        List<Integer> out = new ArrayList<>();
        int first = day.firstKey();
        int last = day.lastKey();
        for (int s = first; s < last; s++) {
            if (!day.containsKey(s)) {
                out.add(s);
            }
        }
        out.add(last + 1);
        return out;
    }

    /** v0.1's slide plan, factored for reuse by the compound: the lessons ABOVE
     *  {@code fromSlot} each slide down one slot. The extracted lesson is dropped
     *  from the H1/room indexes for the checks (the 26/08 same-teacher-block fix)
     *  and restored right after. Returns null (rejected) or the slide list. */
    private static List<Object[]> planSlides(Pass pass, TreeMap<Integer, Lesson> day, int fromSlot,
                                             LocalDate date, Lesson extracted) {
        index(extracted, pass.teachersByTs(), pass.roomsByTs(), false);
        List<Object[]> slides = new ArrayList<>();
        boolean allLegal = true;
        for (int j : day.tailMap(fromSlot, false).keySet()) {
            Lesson mover = day.get(j);
            Timeslot down = pass.tsByDateSlot().getOrDefault(date, Map.of()).get(j - 1);
            if (mover == null || down == null
                    || landingRejectReason(mover, down, pass.teachersByTs(), pass.roomsByTs()) != null
                    // TASK-680: the slide pays the RANGE membership too (the shared
                    // helper, same census key). H30 is a no-op here (same-date
                    // move) and H28 cannot fire (the slide-down CLOSES the gap the
                    // extraction opened: compaction never creates an A..B..A
                    // window).
                    || !cachedRangeContains(mover, down)) {
                allLegal = false;
                break;
            }
            slides.add(new Object[] {mover, down});
        }
        index(extracted, pass.teachersByTs(), pass.roomsByTs(), true);
        return allLegal ? slides : null;
    }

    /** Undo only the moves appended at/after {@code mark} (a compound's eviction
     *  must not take the cell's earlier successful hours with it). */
    private static void rollbackFrom(Pass pass, int mark) {
        List<Object[]> tail = new ArrayList<>(pass.undo().subList(mark, pass.undo().size()));
        rollback(pass, tail);
        pass.undo().subList(mark, pass.undo().size()).clear();
    }

    private static int declaredQuota(Long groupId, LocalDate date) {
        var row = SchoolSchedule.getExpectedHoursRow(groupId, date);
        int sheet = row == null ? 0 : row.aulaHours + row.labHours;
        // TASK-671: pool-cap-aware (was raw sheet: the donor yield check for
        // pool groups evaluated against an obligation nobody chases since 577)
        return SchoolSchedule.poolCappedQuotaForDate(groupId, date, sheet);
    }

    private static LocalDate donorDate(String donorKey) {
        return LocalDate.parse(donorKey.substring(donorKey.lastIndexOf('@') + 1));
    }

    /** TASK-680: the CACHED-range membership - the one gate the director itself
     *  will enforce. Belt PERMANENT (TASK-679 closed 05/10: the two VRP chains
     *  are predicate-identical, 17.5M-check pin; the 04/10 crash class was the
     *  CACHE - the grandfather binds to the slot at cache time, a moved lesson
     *  falls outside its own range and update() throws). INVARIANT: natural
     *  Timeslot.compareTo (startDateTime) must match the range's
     *  (date, slotIndex) sort - true on this grid's unique sequential starts; a
     *  duplicate-start timeslot silently breaks the binarySearch. */
    static boolean cachedRangeContains(Lesson mover, Timeslot ts) {
        if (java.util.Collections.binarySearch(mover.getAvailableTimeslots(), ts) < 0) {
            census("VRP-RANGE");
            return false;
        }
        return true;
    }

    /** TASK-680: the shared cross-date landing gate - the SAME triplet the
     *  extraction pays (value-range membership, H30, H28) - factored so every
     *  family's landings cannot drift apart. Callers still run their own H1/room
     *  checks (cheap-first: legalLanding before this); this adds only what the
     *  04/10 review found missing on the slide/refill/compound paths. */
    private static boolean crossDateLandingSafe(Pass pass, Lesson mover, Timeslot ts) {
        if (!cachedRangeContains(mover, ts)) {
            return false;
        }
        if (h30Unsafe(mover, ts, pass.stageStartByGroup())) {
            census("H30");
            return false;
        }
        if (!h28LandingSafe(mover, ts, pass)) {
            census("H28");
            return false;
        }
        return true;
    }

    /** TASK-678 residual (HRF h30Unsafe mirror): true when the mover is a
     *  Sicurezza lesson landing at a date at or past its group's earliest
     *  StagePattern start - the H30 firing edge, same subject normalization as
     *  the provider's sicurezzaStagePairs join and HRF's leg. */
    private static boolean h30Unsafe(Lesson l, Timeslot targetTs, Map<String, LocalDate> stageStartByGroup) {
        if (l.course == null || l.course.studentGroup == null || l.getSubject() == null) {
            return false;
        }
        LocalDate stageStart = stageStartByGroup.get(l.course.studentGroup.name);
        return stageStart != null
                && !targetTs.getDate().isBefore(stageStart)
                && BaseConstraintProvider.SICUREZZA_SUBJECT.equals(
                        com.school.planner.util.SubjectNormalization.normalize(l.getSubject()));
    }

    /** TASK-678 residual (HRF's belt leg, landing-shaped): adding {@code l} at
     *  {@code targetTs} must not create a new A..B..A return inside the
     *  (group, subject, date) cell. EXACT by construction: the landing cell (the
     *  day's same-subject lessons plus l at its landing slot, sorted) goes through
     *  the SHARED gate {@link H28RotationFinisher#isViolating} - the same
     *  h28ReturnPair/h28Between the provider reads - so the pass and the stream
     *  cannot drift (the first hand-rolled version missed the far-side window and
     *  the B-position; review 05/10). The donor side needs no check: extraction
     *  plus slide-down keep the donor day contiguous and removing a lesson can
     *  only close gaps. */
    private static boolean h28LandingSafe(Lesson l, Timeslot targetTs, Pass pass) {
        if (l.course == null || l.course.studentGroup == null) {
            return true;
        }
        TreeMap<Integer, Lesson> day = pass.dayLessons().get(keyOf(l.course.studentGroup.id, targetTs.getDate()));
        List<Lesson> cell = new ArrayList<>();
        String subject = l.getSubject();
        if (day != null) {
            for (var e : day.entrySet()) {
                Lesson x = e.getValue();
                if (x.getSubject() != null && x.getSubject().equals(subject)) {
                    cell.add(x);
                }
            }
        }
        if (cell.isEmpty()) {
            return true;
        }
        Timeslot own = l.timeslot;
        l.timeslot = targetTs; // the temporary view isViolating reads; restored below
        try {
            cell.add(l);
            cell.sort(java.util.Comparator.comparingInt(x -> TimeslotSequence.slotIndexOf(x.timeslot)));
            return !H28RotationFinisher.isViolating(cell);
        } finally {
            l.timeslot = own;
        }
    }

    /** TASK-678: the room the landing lesson will occupy. Null when the lesson
     *  carries no room (the room-less worlds - CH boundaries, room-less unit
     *  fixtures: the pre-678 semantics, no room axis to manage). The carried room
     *  when it is free at {@code ts}; otherwise the first FREE room of the lesson's
     *  own {@link Lesson#getAvailableRooms()} range (the single-sourced suitability
     *  policy: assignable, tag-legal, capacity-ordered), minus the tagged rooms for
     *  untagged courses: an ordinary lesson in a tagged room pays M-LF's 1000, and
     *  the belt arbitrates hard, not medium. Null when no legal room is free. */
    private static Room landingRoomFor(Lesson l, Timeslot ts, Map<Long, Set<Long>> roomsByTs) {
        if (l.room == null) {
            return null; // room-less world (CH boundaries, room-less unit fixtures):
                         // the pre-678 semantics - no room axis to manage at the landing
        }
        Set<Long> occupied = roomsByTs.getOrDefault(ts.id, Set.of());
        if (!occupied.contains(l.room.id)) {
            return l.room;
        }
        boolean taggedCourse = l.course != null && l.course.requiredRoomTags != null
                && !l.course.requiredRoomTags.isEmpty();
        int groupSize = l.getStudentGroup() != null ? l.getStudentGroup().size : 0;
        // review F6: PREFER a room that fits the group (M3 is medium, invisible to
        // the full-hard belt), but fall back to a smaller one: closing the hard
        // cell is the doctrine, the M3 unit is the accepted price when nothing
        // bigger is free (the 04/10 synthetic: only sub-size rooms free).
        Room subSizeFallback = null;
        for (Room r : l.getAvailableRooms()) {
            if (occupied.contains(r.id)) {
                continue;
            }
            if (!taggedCourse && r.isTagged()) {
                continue;
            }
            if (groupSize > 0 && r.capacity < groupSize) {
                if (subSizeFallback == null) {
                    subSizeFallback = r;
                }
                continue;
            }
            return r;
        }
        return subSizeFallback;
    }

    /** H1 (teacher free), VRP (fresh check AND the director's value range) - the
     *  room axis is NOT judged here: a carried-room conflict defers to the
     *  TASK-678 reassignment scan at the call sites. */
    private static boolean legalLanding(Lesson l, Timeslot ts,
                                        Map<Long, Set<Long>> teachersByTs, Map<Long, Set<Long>> roomsByTs) {
        String why = landingRejectReason(l, ts, teachersByTs, roomsByTs);
        if (why == null) {
            return true;
        }
        census(why);
        return false;
    }

    /** The reject reason ({@link #REJECT_H1}/{@link #REJECT_ROOM}/{@link #REJECT_VRP})
     *  or null when the landing is legal. */
    private static String landingRejectReason(Lesson l, Timeslot ts,
                                              Map<Long, Set<Long>> teachersByTs, Map<Long, Set<Long>> roomsByTs) {
        var t = l.getTeacher();
        if (t != null && teachersByTs.getOrDefault(ts.id, Set.of()).contains(t.id)) {
            return REJECT_H1;
        }
        if (l.room != null && roomsByTs.getOrDefault(ts.id, Set.of()).contains(l.room.id)) {
            return REJECT_ROOM;
        }
        // TASK-578: per-slot legality (stale-cache crash class, TDD-proven on
        // SurplusHarvest; O(1) via Lesson.isSlotLegalFresh: this guard fires up to
        // ~10^4 times per pass across extraction retries)
        if (!l.isSlotLegalFresh(ts)) {
            if (Boolean.getBoolean("edf.debug") && TRACED.contains(l.id)) {
                // the decisive test for the stale-cache hypothesis (TASK-560): the
                // cached size vs the FRESH one (the guard itself now reads per-slot,
                // so this trace is where the staleness question stays observable)
                var freshRange = l.getAvailableTimeslotsFresh();
                System.out.println("[EDF-trace] lesson=" + l.id + " VRP-reject ts=" + ts.id
                        + " (" + ts.getDate() + ") cachedSize=" + l.getAvailableTimeslots().size()
                        + " freshSize=" + freshRange.size()
                        + (freshRange.contains(ts) ? " => CACHE STALE (fresh would accept)"
                                                   : " => true VRP reject"));
            }
            return REJECT_VRP;
        }
        return null;
    }

    /** Debug-only rejection census (read once at pass end under -Dedf.debug=true). */
    static final Map<String, Integer> EDF_REJECTS = new HashMap<>();

    private static void census(String key) {
        if (Boolean.getBoolean("edf.debug")) {
            EDF_REJECTS.merge(key, 1, Integer::sum);
        }
    }

    /** -Dedf.trace=12,34: full per-lesson decision log through every gate. */
    static final Set<Long> TRACED = new HashSet<>();

    private static void parseTraceProperty() {
        TRACED.clear();
        String ids = System.getProperty("edf.trace");
        if (ids == null) {
            return;
        }
        for (String s : ids.split(",")) {
            try {
                TRACED.add(Long.parseLong(s.trim()));
            } catch (NumberFormatException ignored) {
                // malformed token: skip it
            }
        }
    }

    /** The compound unit of application: the extraction plus its slide-down tail. */
    private static void applyMoveWithSlides(Pass pass, Lesson l, Timeslot ts, List<Object[]> slides,
            Room landingRoom) {
        applyMoveWithRoom(pass, l, ts, landingRoom);
        for (Object[] s : slides) {
            applyMove(pass, (Lesson) s[0], (Timeslot) s[1]);
        }
    }

    /** applyMove plus the TASK-678 room reassignment: the undo entry carries the
     *  ORIGINAL room (3-slot trail; the 2-slot entries are the pre-678 shape and
     *  keep their meaning), so the belt's rollback restores both axes. */
    private static void applyMoveWithRoom(Pass pass, Lesson l, Timeslot ts, Room room) {
        pass.undo().add(new Object[] {l, l.timeslot, l.room});
        index(l, pass.teachersByTs(), pass.roomsByTs(), false);
        removeFromDay(pass.dayLessons(), l);
        bump(pass.hoursByKey(), keyOf(l), -1);
        pass.moveTimeslot().accept(l, ts);
        l.timeslot = ts;
        l.room = room;
        insertIntoDay(pass.dayLessons(), l);
        index(l, pass.teachersByTs(), pass.roomsByTs(), true);
        bump(pass.hoursByKey(), keyOf(l), +1);
    }

    private static void applyMove(Pass pass, Lesson l, Timeslot ts) {
        applyMoveWithRoom(pass, l, ts, l.room);
    }

    /** The exact mirror of applyMove, hoursByKey included: a rolled-back move must
     *  leave NO phantom hour count (a phantom +1 could later license an under-quota
     *  extraction - the H32 regression class this pass exists to prevent). */
    private static void rollback(Pass pass, List<Object[]> undo) {
        for (int i = undo.size() - 1; i >= 0; i--) {
            Object[] u = undo.get(i);
            Lesson l = (Lesson) u[0];
            Timeslot old = (Timeslot) u[1];
            // TASK-678 review F1: de-index FIRST with the CURRENT timeslot AND room
            // (the move may have reassigned the room: restoring it before the
            // remove would corrupt the live index - a phantom occupant left on the
            // new room, the erased mark of the old room's legitimate blocker).
            index(l, pass.teachersByTs(), pass.roomsByTs(), false);
            removeFromDay(pass.dayLessons(), l);
            bump(pass.hoursByKey(), keyOf(l), -1);
            if (u.length > 2) {
                l.room = (Room) u[2];
            }
            pass.moveTimeslot().accept(l, old);
            l.timeslot = old;
            insertIntoDay(pass.dayLessons(), l);
            index(l, pass.teachersByTs(), pass.roomsByTs(), true);
            bump(pass.hoursByKey(), keyOf(l), +1);
        }
        undo.clear();
    }

    private static void bump(Map<String, int[]> hoursByKey, String key, int delta) {
        int[] h = hoursByKey.get(key);
        if (h != null) {
            h[0] += delta;
        }
    }

    private static void index(Lesson l, Map<Long, Set<Long>> teachersByTs,
                              Map<Long, Set<Long>> roomsByTs, boolean add) {
        if (l.timeslot == null) {
            return;
        }
        var t = l.getTeacher();
        if (t != null && !t.pseudo) {
            var s = teachersByTs.computeIfAbsent(l.timeslot.id, k -> new HashSet<>());
            if (add) {
                s.add(t.id);
            } else {
                s.remove(t.id);
            }
        }
        if (l.room != null) {
            var s = roomsByTs.computeIfAbsent(l.timeslot.id, k -> new HashSet<>());
            if (add) {
                s.add(l.room.id);
            } else {
                s.remove(l.room.id);
            }
        }
    }

    private static void removeFromDay(Map<String, TreeMap<Integer, Lesson>> dayLessons, Lesson l) {
        if (l.timeslot == null) {
            return;
        }
        TreeMap<Integer, Lesson> day = dayLessons.get(keyOf(l));
        if (day != null) {
            day.remove(TimeslotSequence.slotIndexOf(l.timeslot));
        }
    }

    private static void insertIntoDay(Map<String, TreeMap<Integer, Lesson>> dayLessons, Lesson l) {
        if (l.timeslot == null || l.course == null || l.course.studentGroup == null) {
            return;
        }
        dayLessons.computeIfAbsent(keyOf(l), k -> new TreeMap<>())
                .put(TimeslotSequence.slotIndexOf(l.timeslot), l);
    }

    private static String keyOf(Lesson l) {
        return keyOf(l.course.studentGroup.id, l.timeslot.getDate());
    }

    private static String keyOf(Long groupId, LocalDate date) {
        return groupId + "@" + date;
    }

    private static int nonStageHours(TreeMap<Integer, Lesson> day) {
        if (day == null) {
            return 0;
        }
        int n = 0;
        for (Lesson l : day.values()) {
            var t = l.getTeacher();
            if (t == null || !t.pseudo) {
                n++;
            }
        }
        return n;
    }

    /** Gate (d) refined: the first hour lands at the day's first slot that BOTH
     *  exists on the grid AND is open in the group's calendar window (union of the
     *  GroupCalendarRule rows for that weekday; no rules = the whole grid). The
     *  first bench measured the grid-first variant dead on the real bank (reject
     *  census VRP=38 H1=150 ROOM=199: every aim at the 8:00 slot died because the
     *  residual cells' windows often open later). The computation itself is the
     *  shared {@link StudentDayCompaction#firstCalendarOpenSlot} (single source
     *  with DeclaredDayPreBooker). */
    private static int firstOpenSlotFor(Cell target, Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot) {
        return StudentDayCompaction.firstCalendarOpenSlot(
                target.groupId, target.date, tsByDateSlot.get(target.date));
    }
}
