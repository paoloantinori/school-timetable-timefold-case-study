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
 * <p>v0.2 (27/08, anello 2): the optional cross-group swap-compound move family
 * (see {@link #V2_SWAP_FLAG}) - measured INERT on the winner's bank (0 compounds:
 * every H1 blocker sits on a day already at quota/floor, see the registro's sesta
 * tratta), kept as a correct building block behind its opt-in flag.
 *
 * <p>Optional families since then (the class name is historical, it describes only
 * the original H34 family): the H33 under-floor targets ({@link #H33_FLAG},
 * TASK-577, still opt-in DEFAULT OFF), the H32 partial under-quota targets
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
 * FORK NOTE (04/10): this class intentionally FREEZES the pre-ratification flag
 * form (opt-in: unset = OFF) and the pre-678 landing grammar - it is the frozen
 * v131-era bank CompositeRepairReplay609Test replays against; do NOT port the
 * production h33Enabled() form or the 678 amendments here without re-freezing
 * the bank's recorded outcomes.
 */
public final class EmptyDayFinisherTol {

    /** TASK-560 flag, RATIFIED DEFAULT ON (maintainer, 2026-08-27): null/absent -> on,
     *  only the literal {@code false} disarms. */
    public static final String FLAG = "planner.construction.empty-day-finisher.enabled";

    /** TASK-560 v0.2 opt-in prototype flag (handoff 27/08 anello 2): the cross-group
     *  swap-compound move family. DEFAULT OFF, opt-in {@code Boolean.parseBoolean}
     *  form like every pre-ratification prototype: the localized bench measures it,
     *  adoption is maintainer's call, never silent. */
    public static final String V2_SWAP_FLAG = "planner.construction.empty-day-finisher.v2-swap.enabled";

    /** Public for ScheduleService's wiring and banner (same form as the sibling passes). */
    public static boolean enabled() {
        String v = System.getProperty(FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    /** Public for ScheduleService's banner. Opt-in prototype: absent -> OFF. */
    public static boolean v2SwapEnabled() {
        return Boolean.parseBoolean(System.getProperty(V2_SWAP_FLAG));
    }

    /** TASK-577 (7/09) opt-in prototype flag: under-floor cells (H33's partial
     *  underfill, invisible to the empty-day family whose gate is hours == 0) as
     *  a SECOND target family, filled to the group's floor. Includes the
     *  quota-fill-excluded groups (G14): H32/H34 exclude them, H33 does not.
     *  DEFAULT OFF, same Boolean.parseBoolean form as V2_SWAP. The motivating
     *  bank is the v131 delivery's two G14 Fridays (2h vs floor 3, the only
     *  legal third slot free, a +2 surplus Thursday as gate-E-legal donor): the
     *  compound (mid-block extraction + same-day slide + landing) is a
     *  3-lesson move no LS move stream composes.
     *  <p>KNOWN INTERACTION (code-review 7/09, low): with v2-swap ALSO armed,
     *  the compound's eviction landing may overshoot an H33 cell to floor+1
     *  (M33 medium surplus, no hard; the H34 cells are immune because a
     *  compound landing day must be non-empty). Needs both opt-in flags. */
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

    /** Public for the banner/tests. Opt-in prototype: absent -> OFF. */
    public static boolean h33Enabled() {
        return Boolean.parseBoolean(System.getProperty(H33_FLAG));
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
    public static boolean extensionsArmed() {
        return relayEnabled() || h32Enabled();
    }

    private EmptyDayFinisherTol() {
    }

    /** {@code movesApplied} = lessons moved (extractions + slides, both sides);
     *  {@code cellsClosed} = cells closed to their goal, ALL families (an
     *  empty declared day to quota (H34), a partial under-quota day to quota
     *  (H32, opt-in), an under-floor day to floor (H33, opt-in) - the [EDF-H32]
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
                        BiConsumer<Lesson, Timeslot> moveTimeslot) {
    }

    /** landingRejectReason's vocabulary: census keys AND (v0.2) branch decisions -
     *  a bare string compare on a literal would silently die on a rename. */
    private static final String REJECT_H1 = "H1";
    private static final String REJECT_ROOM = "ROOM";
    private static final String REJECT_VRP = "VRP";

    public static Result finish(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot) {
        if (schedule.getTimeslots() == null || !enabled()) {
            return Result.noop();
        }
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
                    int quota = row.getValue().aulaHours + row.getValue().labHours;
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
                        // LEVA B amendment (documented deviation from the mandate's
                        // parenthetical "non H34: la cella vuota vuole l'ora piena"):
                        // no constraint demands the FULL hour on an empty day - H34
                        // closes with any lesson and H32 accepts quota-tolerance, so
                        // the target's need is quota - tol for tolerance groups (the
                        // blind round's arbitrated shape: 5 hours on G09 2027-05-25,
                        // -9 -> -7 on the real evaluator). tol = 0 groups: need = quota,
                        // strict donors - byte-identical to today's behavior.
                        int h34Tol = SchoolScheduleConstraintProvider.quotaFillTolerance(g.name);
                        targets.add(new Cell(g.name, g.id, row.getKey(), key,
                                Math.max(1, quota - h34Tol), h34Tol > 0));
                    } else if (h32Need > 0) {
                        // LEVA B: this target's donors may yield down to quota-tolerance
                        targets.add(new Cell(g.name, g.id, row.getKey(), key, h32Need, true));
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
                    hoursByKey, new ArrayList<>(), moveTimeslot);
            int placed = 0;
            while (placed < target.need) {
                TreeMap<Integer, Lesson> tDay = dayLessons.get(target.key);
                int nextSlot = tDay == null || tDay.isEmpty()
                        ? firstOpenSlotFor(target, tsByDateSlot)
                        : tDay.lastKey() + 1;
                Timeslot targetTs = tsByDateSlot.getOrDefault(target.date, Map.of()).get(nextSlot);
                boolean placedHour = targetTs != null && extractionFor(pass, target, targetTs);
                if (!placedHour && targetTs != null && relayOn) {
                    // TASK-612: the direct class is exhausted - try the import-relay
                    // (at-quota donor + exact-cell refill) before the v0.2 compound.
                    placedHour = relayFor(pass, target, targetTs);
                }
                if (!placedHour && targetTs != null && v2SwapEnabled()) {
                    // v0.2: the same-group extraction is stuck - try the cross-group
                    // swap-compound (evict the H1 blocker, then retry the extraction).
                    placedHour = compoundFor(pass, target, targetTs);
                    if (placedHour) {
                        swapCompounds++;
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
            System.out.println("[EDF-TOL-debug] reject census: " + EDF_REJECTS);
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
                if (!legalLanding(l, targetTs, pass.teachersByTs(), pass.roomsByTs())) {
                    if (Boolean.getBoolean("edf.debug") && TRACED.contains(l.id)) {
                        System.out.println("[EDF-trace] lesson=" + l.id + " target=" + target.key
                                + " ownLanding rejected " + landingRejectReason(l, targetTs,
                                        pass.teachersByTs(), pass.roomsByTs()));
                    }
                    continue;
                }
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
                applyMoveWithSlides(pass, l, targetTs, slides);
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
                    && legalLanding(m, cell, pass.teachersByTs(), pass.roomsByTs())) {
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
                        || !legalLanding(m, cell, pass.teachersByTs(), pass.roomsByTs())) {
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
    /**
     * blind-live-r1 LEVA B (bench prototype): when the target is an H32 DEFICIT
     * cell (tolDonor), the donor floor relaxes to quota minus the group's H32
     * tolerance (G09: 1h) - the extraction leaves the donor inside the
     * tolerance H32 itself accepts, so it is neutral, not a new deficit. H34 and
     * H33 targets keep the strict floor (an empty day wants the FULL hour, and
     * the floor is H33's own metric). TOL-DONOR-UNLOCK counts the donors the
     * tolerance alone admitted (the old strict gate would have rejected).
     */
    private static boolean donorDayYields(Pass pass, String donorKey,
            com.school.planner.solver.EmptyDayFinisherTol.Cell target, String censusKey) {
        TreeMap<Integer, Lesson> donorDay = pass.dayLessons().get(donorKey);
        if (donorDay == null || donorDay.isEmpty()) {
            return false;
        }
        Long groupId = target != null ? target.groupId() : null;
        if (groupId == null) {
            groupId = donorGroupId(donorKey);
        }
        int donorQuota = declaredQuota(groupId, donorDate(donorKey));
        int strict = Math.max(donorQuota, floorOrZero(groupId));
        int minHours = strict;
        if (target != null && target.tolDonor()) {
            int tol = SchoolScheduleConstraintProvider.quotaFillTolerance(target.groupName());
            minHours = Math.max(donorQuota - tol, floorOrZero(groupId));
        }
        int[] donorHours = pass.hoursByKey().get(donorKey);
        if (donorHours != null && donorHours[0] - 1 < minHours) {
            census(censusKey);
            return false;
        }
        if (donorHours != null && donorHours[0] - 1 < strict) {
            census("TOL-DONOR-UNLOCK");
        }
        return true;
    }

    /** The original STRICT GATE-E (used by the cross-group compound's H side,
     *  where the donor group is not the target's group). */
    private static boolean donorDayYieldsStrict(Pass pass, String donorKey, Long groupId,
            String censusKey) {
        TreeMap<Integer, Lesson> donorDay = pass.dayLessons().get(donorKey);
        if (donorDay == null || donorDay.isEmpty()) {
            return false;
        }
        int donorQuota = declaredQuota(groupId, donorDate(donorKey));
        int[] donorHours = pass.hoursByKey().get(donorKey);
        if (donorHours != null
                && donorHours[0] - 1 < Math.max(donorQuota, floorOrZero(groupId))) {
            census(censusKey);
            return false;
        }
        return true;
    }

    private static Long donorGroupId(String donorKey) {
        int at = donorKey.indexOf('@');
        try {
            return Long.parseLong(donorKey.substring(0, at));
        } catch (NumberFormatException e) {
            return null;
        }
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
        if (!donorDayYieldsStrict(pass, srcKey, h.id, "V2-H-SRC-GATE-E")) {
            return false; // H's day keeps its own quota/floor: no new deficit on H
            // (strict on purpose: the donor here belongs to ANOTHER group - the
            // cross-group compound is not the tolerance lever's territory)
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
                if (ts3 == null
                        || landingRejectReason(x, ts3, pass.teachersByTs(), pass.roomsByTs()) != null) {
                    census("V2-H-LANDING");
                    continue;
                }
                int mark = pass.undo().size();
                applyMoveWithSlides(pass, x, ts3, slides);
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
                    || landingRejectReason(mover, down, pass.teachersByTs(), pass.roomsByTs()) != null) {
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
        return row == null ? 0 : row.aulaHours + row.labHours;
    }

    private static LocalDate donorDate(String donorKey) {
        return LocalDate.parse(donorKey.substring(donorKey.lastIndexOf('@') + 1));
    }

    /** H1 (teacher free), room-safe (own room free or none), VRP (value range). */
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
    private static void applyMoveWithSlides(Pass pass, Lesson l, Timeslot ts, List<Object[]> slides) {
        applyMove(pass, l, ts);
        for (Object[] s : slides) {
            applyMove(pass, (Lesson) s[0], (Timeslot) s[1]);
        }
    }

    private static void applyMove(Pass pass, Lesson l, Timeslot ts) {
        pass.undo().add(new Object[] {l, l.timeslot});
        index(l, pass.teachersByTs(), pass.roomsByTs(), false);
        removeFromDay(pass.dayLessons(), l);
        bump(pass.hoursByKey(), keyOf(l), -1);
        pass.moveTimeslot().accept(l, ts);
        l.timeslot = ts;
        insertIntoDay(pass.dayLessons(), l);
        index(l, pass.teachersByTs(), pass.roomsByTs(), true);
        bump(pass.hoursByKey(), keyOf(l), +1);
    }

    /** The exact mirror of applyMove, hoursByKey included: a rolled-back move must
     *  leave NO phantom hour count (a phantom +1 could later license an under-quota
     *  extraction - the H32 regression class this pass exists to prevent). */
    private static void rollback(Pass pass, List<Object[]> undo) {
        for (int i = undo.size() - 1; i >= 0; i--) {
            Object[] u = undo.get(i);
            Lesson l = (Lesson) u[0];
            Timeslot old = (Timeslot) u[1];
            index(l, pass.teachersByTs(), pass.roomsByTs(), false);
            removeFromDay(pass.dayLessons(), l);
            bump(pass.hoursByKey(), keyOf(l), -1);
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
