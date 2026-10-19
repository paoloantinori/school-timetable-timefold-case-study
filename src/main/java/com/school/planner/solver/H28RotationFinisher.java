package com.school.planner.solver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import org.jboss.logging.Logger;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;

/**
 * TASK-595 H28 rotation finisher: the deterministic pass that reorders the lessons of an
 * A..B..A cell into teacher-contiguous blocks. Sister of {@link RoomTailFinisher} /
 * {@link OneOffFinisher}: same post-room boundary, same in-memory
 * {@link PureHoleRepair.Grid} gate chain, same caller-wrapped improve-or-noop oracle.
 *
 * <p><b>Provenance (TASK-593 witness bench).</b> The only positively supported remedy
 * family for the delivered residual was the H28 day rotation: G03 / Laboratorio
 * Termoidraulico, the delivered days run T03,T03,T22,T22,T22,
 * T03 and the witness rotation to B,B,B,T,T,T moved H28 4 -&gt; 2 with every other
 * hard family unchanged. This pass generalizes the single cyclic rotation to EVERY
 * teacher-block ordering (k! bounded), so a day the one-rotation probe lost may still
 * close via another ordering.
 *
 * <p><b>Structural invariants, by construction.</b> A move permutes the cell's lessons
 * over the cell's OWN slot set: the (group, day) slot multiset is unchanged, so H17
 * compaction, H32/H33/H34 day counts and H7 teacher-day hours cannot move; every lesson
 * keeps its date, so every date-keyed family (H6/H14/H20/H23/H24/H25/H26/H30) is inert.
 * The families actually in play: H1 and H5 (teacher cell / vacation) and H3 via
 * {@link PureHoleRepair.Grid#cellOk}, H2 (each lesson keeps its room, now at a different
 * slot), the VRP ({@link Lesson#isSlotLegalFresh}, TASK-578), H28 itself (removed by the
 * block contiguity), and H-COPRESENCE: a permutation redistributes the per-(tag,
 * startDateTime) counts of the cell's single-tag lessons (the witness subject IS one),
 * so every candidate also passes a dedicated in-pass depth gate mirroring
 * {@code coPresenceCap} exactly (the aggregate improve-or-noop oracle would happily
 * trade an H28 point for a new H-COPRESENCE point, both weight 1).
 *
 * <p><b>Sweeps.</b> A repair can change another same-day cell's H1 landscape (a shared
 * teacher), so after any progress the violating cells are re-derived and previously
 * locked cells are retried; only pin-blocked cells are terminally skipped (pins never
 * move). Each negative outcome is counted once per cell key.
 *
 * <p><b>Pins.</b> H28 has no pinned exemption (the golden, all pinned, respects the
 * invariant), but this pass never moves a pinned or promoted lesson: a violating cell
 * containing one is skipped and counted {@code pinBlocked}, the honest terminal outcome.
 *
 * <p><b>Flag</b> {@value #FLAG}: opt-in prototype, DEFAULT OFF (only a truthy
 * value arms). Unproven mechanism per the m-14 discipline; ratification is TASK-596's gate,
 * not this task's.
 */
public final class H28RotationFinisher {

    private static final Logger LOG = Logger.getLogger(H28RotationFinisher.class);

    /** RATIFIED DEFAULT ON (maintainer's bounded-no-op directive, 17/09): oracle-bound,
     *  improve-or-noop in the delivery wrapper, sweep-cap census; the integrato-1
     *  audit (c2ee51e5) measured no-op-on-this-basin (cellsViolating=1,
     *  cellsRepaired=0) while the pass closes the cells where the internal
     *  permutation suffices (its TASK-595 bank). Standard opt-OUT form. */
    public static final String FLAG = "planner.construction.h28-finisher.enabled";

    public static boolean enabled() {
        String v = System.getProperty(FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    private H28RotationFinisher() {
    }

    /** The pass's own accounting (the legality oracle is the caller's snapshot pair). */
    public record Result(int cellsViolating, int cellsRepaired, int pinBlocked, int locked) {

        static Result noop() {
            return new Result(0, 0, 0, 0);
        }
    }

    /** Most teachers per cell whose block orderings we enumerate (k! bound; 4 -&gt; 24). */
    private static final int MAX_TEACHERS = 4;

    /** Rescan guard: every repair changes global occupancy, so cells are re-derived per sweep. */
    private static final int SWEEP_CAP = 200;

    public static Result repair(SchoolSchedule schedule, BiConsumer<Lesson, Timeslot> moveTimeslot) {
        if (!enabled() || schedule.getTimeslots() == null || schedule.getLessons() == null) {
            return Result.noop();
        }
        PureHoleRepair.Grid grid = new PureHoleRepair.Grid(schedule, moveTimeslot, (l, r) -> {
        });
        Map<CoKey, Integer> coCounts = SchoolSchedule.isCoPresenceEnabled()
                ? coPresenceCounts(schedule) : null;
        Set<String> pinTerminal = new HashSet<>();
        Set<String> lockedKeys = new HashSet<>();
        int repaired = 0;
        int pinBlocked = 0;
        int locked = 0;
        // the violating-cell universe is fixed at entry (a move permutes one cell's own
        // lessons over its own slots: membership never changes), so the first sweep's
        // count is THE count
        int cellsViolating = -1;
        for (int sweep = 0; sweep < SWEEP_CAP; sweep++) {
            Map<String, List<Lesson>> cells = violatingCells(grid);
            if (cellsViolating < 0) {
                cellsViolating = cells.size();
            }
            cells.keySet().removeAll(pinTerminal);
            if (cells.isEmpty()) {
                break;
            }
            boolean progressed = false;
            for (var e : cells.entrySet()) {
                List<Lesson> cell = e.getValue();
                if (cell.stream().anyMatch(l -> !grid.movable(l))) {
                    pinTerminal.add(e.getKey());
                    pinBlocked++;
                    continue;
                }
                // duplicate-slot cells (an H3-violating entry shape) degenerate the
                // permutation into two lessons on one slot, which the Grid's maps would
                // silently overwrite (TreeMap.put): skip the cell whole, counted locked
                if (hasDuplicateSlots(cell)) {
                    if (lockedKeys.add(e.getKey())) {
                        locked++;
                    }
                    continue;
                }
                Map<String, List<Lesson>> blocks = blocksByTeacher(cell);
                // isViolating guarantees >= 2 distinct teachers, so only the k! bound applies
                if (blocks.size() > MAX_TEACHERS) {
                    if (lockedKeys.add(e.getKey())) {
                        locked++;
                    }
                    continue;
                }
                if (applyFirstLegalOrdering(grid, cell, blocks, coCounts)) {
                    repaired++;
                    // a cell locked in an earlier sweep may repair after a foreign repair
                    // freed its H1 landscape: un-count the stale lock (per-key outcomes
                    // stay disjoint, so repaired+locked+pinBlocked == cellsViolating)
                    if (lockedKeys.remove(e.getKey())) {
                        locked--;
                    }
                    progressed = true;
                } else if (lockedKeys.add(e.getKey())) {
                    locked++;
                }
            }
            if (!progressed) {
                break;
            }
        }
        if (cellsViolating <= 0) {
            LOG.info("H28 rotation finisher (TASK-595): 0 violating cells at entry, inert");
        } else {
            LOG.infof("H28 rotation finisher (TASK-595): violating cells %d -> repaired %d"
                            + " (pinBlocked %d, locked %d)",
                    cellsViolating, repaired, pinBlocked, locked);
        }
        return new Result(cellsViolating, repaired, pinBlocked, locked);
    }

    /**
     * The violating cells keyed group|date|subject, each lesson list sorted by canonical
     * slot index. Detection shares the provider's own H28 gates
     * ({@link SchoolScheduleConstraintProvider#h28ReturnPair}/{@code h28Between}), so the
     * stream and this pass cannot drift apart on the A..B..A semantics.
     */
    private static Map<String, List<Lesson>> violatingCells(PureHoleRepair.Grid grid) {
        Map<String, List<Lesson>> byCell = new HashMap<>();
        for (Lesson l : grid.schedule.getLessons()) {
            if (l.timeslot == null || l.getStudentGroup() == null) {
                continue;
            }
            byCell.computeIfAbsent(cellKey(l), k -> new ArrayList<>()).add(l);
        }
        Map<String, List<Lesson>> out = new LinkedHashMap<>();
        for (var e : byCell.entrySet()) {
            List<Lesson> cell = e.getValue();
            cell.sort(Comparator.comparingInt(l -> TimeslotSequence.slotIndexOf(l.timeslot)));
            if (isViolating(cell)) {
                out.put(e.getKey(), cell);
            }
        }
        return out;
    }

    private static String cellKey(Lesson l) {
        return l.getStudentGroup().name + "|" + l.timeslot.getDate() + "|" + l.getSubject();
    }

    /** True iff the cell (sorted by canonical slot index) contains an H28 return: a
     * same-teacher pair with a different teacher's lesson strictly between, evaluated
     * with the provider's own pair/between gates. */
    static boolean isViolating(List<Lesson> cellSortedBySlot) {
        for (int i = 0; i < cellSortedBySlot.size(); i++) {
            for (int j = i + 2; j < cellSortedBySlot.size(); j++) {
                if (!SchoolScheduleConstraintProvider.h28ReturnPair(cellSortedBySlot.get(i), cellSortedBySlot.get(j))) {
                    continue;
                }
                for (int k = i + 1; k < j; k++) {
                    if (SchoolScheduleConstraintProvider.h28Between(cellSortedBySlot.get(i),
                            cellSortedBySlot.get(k), cellSortedBySlot.get(j))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** True iff two cell lessons share a canonical slot index (sorted input, so equal
     * indices are adjacent). */
    private static boolean hasDuplicateSlots(List<Lesson> cellSortedBySlot) {
        for (int i = 1; i < cellSortedBySlot.size(); i++) {
            if (TimeslotSequence.slotIndexOf(cellSortedBySlot.get(i).timeslot)
                    == TimeslotSequence.slotIndexOf(cellSortedBySlot.get(i - 1).timeslot)) {
                return true;
            }
        }
        return false;
    }

    /** Teacher blocks keyed by name (deterministic TreeMap order; null teacher sorts first),
     * each block keeping its lessons in current slot order. */
    private static Map<String, List<Lesson>> blocksByTeacher(List<Lesson> cellSortedBySlot) {
        Map<String, List<Lesson>> blocks = new TreeMap<>();
        for (Lesson l : cellSortedBySlot) {
            String t = l.getTeacher() != null ? l.getTeacher().name : "";
            blocks.computeIfAbsent(t, k -> new ArrayList<>()).add(l);
        }
        return blocks;
    }

    /**
     * Deindexes the whole cell (so its own lessons never shadow a candidate), then tries
     * the block orderings in lexicographic teacher order; the first ordering whose every
     * lesson passes {@link PureHoleRepair.Grid#cellOk} at its target slot AND the
     * H-COPRESENCE depth gate is applied. On failure every lesson is re-indexed at its
     * unchanged original slot.
     *
     * @return true if an ordering was applied
     */
    private static boolean applyFirstLegalOrdering(PureHoleRepair.Grid grid, List<Lesson> cell,
            Map<String, List<Lesson>> blocks, Map<CoKey, Integer> coCounts) {
        List<Timeslot> slots = new ArrayList<>(cell.size());
        for (Lesson l : cell) {
            slots.add(l.timeslot);
        }
        for (Lesson l : cell) {
            grid.deindex(l);
        }
        for (List<Lesson> ordering : orderings(blocks)) {
            boolean legal = true;
            for (int i = 0; i < ordering.size() && legal; i++) {
                legal = grid.cellOk(ordering.get(i), slots.get(i), null, null);
            }
            Map<CoKey, Integer> coDelta = legal && coCounts != null
                    ? coPresenceDelta(ordering, slots) : null;
            if (legal && coDelta != null && !coPresenceOk(coDelta, coCounts)) {
                legal = false;
            }
            if (legal) {
                for (int i = 0; i < ordering.size(); i++) {
                    grid.moveTo(ordering.get(i), slots.get(i));
                }
                if (coDelta != null) {
                    for (var e : coDelta.entrySet()) {
                        coCounts.merge(e.getKey(), e.getValue(), Integer::sum);
                    }
                }
                return true;
            }
        }
        for (Lesson l : cell) {
            grid.index(l);
        }
        return false;
    }

    // ── the H-COPRESENCE in-pass gate (the one timeslot-tier family a within-cell
    //    permutation can move that the Grid chain has no gate for; mirrors coPresenceCap
    //    exactly: count ALL assigned single-tag real-teacher lessons per (tag,
    //    startDateTime), cap at the golden depth where that depth is >= 2) ──

    private record CoKey(String tag, Timeslot ts) {
    }

    private static boolean singleTaggedRealTeacher(Lesson l) {
        return l.course != null && l.course.requiredRoomTags != null
                && l.course.requiredRoomTags.size() == 1
                && l.getTeacher() != null && !l.getTeacher().pseudo;
    }

    /** The accounting base: (tag, startDateTime) -> assigned lesson count. */
    private static Map<CoKey, Integer> coPresenceCounts(SchoolSchedule schedule) {
        Map<CoKey, Integer> counts = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || !singleTaggedRealTeacher(l)) {
                continue;
            }
            counts.merge(new CoKey(l.course.requiredRoomTags.get(0), l.timeslot), 1, Integer::sum);
        }
        return counts;
    }

    /** The count deltas a candidate ordering would cause (only movers contribute). */
    private static Map<CoKey, Integer> coPresenceDelta(List<Lesson> ordering, List<Timeslot> slots) {
        Map<CoKey, Integer> delta = new HashMap<>();
        for (int i = 0; i < ordering.size(); i++) {
            Lesson l = ordering.get(i);
            if (!singleTaggedRealTeacher(l) || slots.get(i) == l.timeslot) {
                continue;
            }
            String tag = l.course.requiredRoomTags.get(0);
            delta.merge(new CoKey(tag, slots.get(i)), 1, Integer::sum);
            delta.merge(new CoKey(tag, l.timeslot), -1, Integer::sum);
        }
        return delta;
    }

    /** True iff every touched cell stays within its golden depth (unknown cells: depth 0,
     * and the cap never fires below depth 2 - the constraint's own filter). */
    private static boolean coPresenceOk(Map<CoKey, Integer> delta, Map<CoKey, Integer> counts) {
        for (var e : delta.entrySet()) {
            int newCount = counts.getOrDefault(e.getKey(), 0) + e.getValue();
            if (newCount <= 1) {
                continue; // below the depth >= 2 gate the constraint itself requires
            }
            int depth = SchoolSchedule.goldenCoPresenceDepth(e.getKey().tag(),
                    e.getKey().ts().startDateTime.getDayOfWeek(),
                    e.getKey().ts().startDateTime.toLocalTime());
            if (depth >= 2 && newCount > depth) {
                return false;
            }
        }
        return true;
    }

    /** Every concatenation of the teacher blocks over the distinct teacher keys, in
     * lexicographic key-permutation order (next-permutation over the sorted key list). */
    static List<List<Lesson>> orderings(Map<String, List<Lesson>> blocks) {
        List<String> keys = new ArrayList<>(blocks.keySet());
        List<List<Lesson>> out = new ArrayList<>();
        do {
            List<Lesson> flat = new ArrayList<>();
            for (String k : keys) {
                flat.addAll(blocks.get(k));
            }
            out.add(flat);
        } while (nextPermutation(keys));
        return out;
    }

    /** In-place lexicographic next permutation over a sorted-start list. */
    private static boolean nextPermutation(List<String> list) {
        int i = list.size() - 2;
        while (i >= 0 && list.get(i).compareTo(list.get(i + 1)) >= 0) {
            i--;
        }
        if (i < 0) {
            return false;
        }
        int j = list.size() - 1;
        while (list.get(j).compareTo(list.get(i)) <= 0) {
            j--;
        }
        Collections.swap(list, i, j);
        Collections.reverse(list.subList(i + 1, list.size()));
        return true;
    }
}
