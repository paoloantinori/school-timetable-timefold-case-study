package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import org.jboss.logging.Logger;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;

/**
 * TASK-550 room-tail finisher (mandato maintainer 25/08 13:05): the deterministic pass that closes the
 * residual H29 (assigned lesson without a room) the room phases leave behind when a tag is
 * scarce. Sister of {@link PureHoleRepair}: same in-memory grid, same cheapest-first gate chain,
 * same flag form (opt-in, DEFAULT OFF until ratification).
 *
 * <p><b>The phenomenon (measured on the -83 banco, task550-cure-congiunte):</b> the delivery's
 * entire hard residue was 83 room-null moda labs (Modello/Moulage 42 + Confezione 41, groups
 * 1/2/G09). The only moda-tagged room is R16, and at all 83 cells the occupant was
 * ANOTHER moda lab (94 cells carry two co-scheduled moda labs; the golden co-schedules ZERO all
 * year - 798h demand vs ~1392 R16 cells, packing, not saturation). So the free-cell branch
 * is mostly inert post-B-ROOM and the pass's real lever is de-co-scheduling.
 *
 * <p><b>Move families, cheapest first:</b>
 * <ol>
 * <li><b>free cell</b>: a tag-satisfying room is free at the lesson's cell - plain room assign.</li>
 * <li><b>evict non-lab</b>: the blocker is an ordinary lesson sheltering in the lab (the
 *     TASK-456 lab-fallback) - it relocates ROOM-ONLY to another free assignable room, untagged
 *     first (no timeslot touch, H17-trivially-safe; the blocker already sits in a tagged room,
 *     so a tagged fallback adds no new M-LF cost), then the lab takes the cell.</li>
 * <li><b>same-day de-co-schedule</b>: the blocker is another lab (the moda case) - the roomless
 *     lab itself moves WITHIN ITS OWN DAY with PULL-LAST semantics (the PureHoleRepair invariant):
 *     only the day's LAST lesson moves, and only into a cell that FILLS an existing interior hole
 *     of that day. Vacating the last slot cannot open a hole and only an existing hole is filled,
 *     so no hole migration (the TASK-481 shell-game) is possible; per-day hour count is unchanged
 *     (H32/H33 invariant by construction). Tail-EXTEND is deliberately excluded: {@code last ->
 *     last+1} leaves rank {@code last} empty, which is an interior hole.</li>
 * <li><b>swap with an ordinary same-group lesson</b> (the banco's workhorse, the PureHoleRepair
 *     cross-day-exchange shape generalized with a room swap): the roomless lab L trades cells
 *     with an ordinary lesson O of its own group sitting where a tag room is FREE; O slides into
 *     L's old cell taking any free ordinary room there, L takes the tag room at O's old cell.
 *     BOTH cells stay occupied, so no hole can open on either side (H17-safe by construction,
 *     stronger than pull-last); each day keeps its hour count whether the swap is same-day or
 *     cross-day (H32/H33 invariant).</li>
 * </ol>
 *
 * <p><b>Gates:</b> room-cell occupancy for H2, the lesson's own tag domain for H-M2, and the
 * shared {@link PureHoleRepair.Grid#cellOk} chain (H1/H3/H5/VRP) for timeslot moves - which is
 * why the caller's {@code conflictsAdded} is legitimately 0 for this pass (same argument as
 * PureHoleRepair's). Note the grid models ANY co-scheduled pair as occupying the cell, while H2
 * exempts golden-practiced guest pairs: the pass is therefore CONSERVATIVE on shareable cells
 * (never illegal, at most suboptimal) - do not "fix" this into an H2 violation.
 *
 * <p><b>Oracle</b>: the caller wraps the pass in the improve-or-noop contract evaluated on the
 * FULL hard score (unlike the timeslot-tier siblings, this pass moves rooms, so H2/H29 are in
 * play): a pass that worsened the authoritative hard is rolled back wholesale.
 *
 * <p><b>Flag form</b> ({@code planner.construction.room-tail-finisher.enabled}, RATIFICATO
 * maintainer 25/08 17:15, DEFAULT ON with the standard opt-out: null/absent -> ON, only the literal
 * "false" disarms). Evidence: banco del -83 (83 tag-required room-null -> 0 in vitro) and the
 * full cycle (76/76 -> 0, delivery 0hard end-to-end, KPI in banda, medium cost ~40k, DoD
 * completa: task550-roomtail/VERDICT-2026-08-25.md + ciclo-finisher-on/).
 */
public final class RoomTailFinisher {

    private static final Logger LOG = Logger.getLogger(RoomTailFinisher.class);

    /**
     * Opt-out flag (RATIFICATO maintainer 25/08 17:15): null/absent -> ON, only the literal "false"
     * disarms (same form as every ratified feature).
     */
    public static final String FLAG = "planner.construction.room-tail-finisher.enabled";

    public static boolean enabled() {
        String v = System.getProperty(FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    private RoomTailFinisher() {
    }

    /** nullBefore/nullAfter plus the pass's own move accounting (the oracle is the caller's). */
    public record Result(int nullBefore, int nullAfter, int freeCellAssignments, int evictions,
            int pullLastMoves, int swaps) {
    }

    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                BiConsumer<Lesson, Room> moveRoom) {
        if (!enabled() || schedule.getTimeslots() == null || schedule.getLessons() == null) {
            return new Result(0, 0, 0, 0, 0, 0);
        }
        PureHoleRepair.Grid grid = new PureHoleRepair.Grid(schedule, moveTimeslot, moveRoom);
        List<Lesson> targets = tailTargets(schedule, grid);
        int before = targets.size();
        if (before == 0) {
            LOG.info("Room-tail finisher (TASK-550): 0 tag-required room-null lessons at entry, inert");
            return new Result(0, 0, 0, 0, 0, 0);
        }
        int free = 0;
        int evictions = 0;
        int pullLast = 0;
        int swaps = 0;
        int guard = 0;
        while (guard++ < 5_000) {
            targets = tailTargets(schedule, grid);
            if (targets.isEmpty()) {
                break;
            }
            boolean progressed = false;
            for (Lesson l : targets) {
                int kind = place(grid, l);
                if (kind == 0) {
                    continue;
                }
                progressed = true;
                if (kind == 1) {
                    free++;
                } else if (kind == 2) {
                    evictions++;
                } else if (kind == 3) {
                    pullLast++;
                } else {
                    swaps++;
                }
                break; // re-scan: the freed/moved cells change every other target's options
            }
            if (!progressed) {
                break; // every remaining tail lesson is locked
            }
        }
        int after = tailTargets(schedule, grid).size();
        LOG.infof("Room-tail finisher (TASK-550): tag-required room-null %d -> %d"
                        + " (free-cell %d, evictions %d, pull-last %d, swaps %d)",
                before, after, free, evictions, pullLast, swaps);
        return new Result(before, after, free, evictions, pullLast, swaps);
    }

    /** Tag-required, placed, roomless, movable lessons - the pass's whole population. */
    private static List<Lesson> tailTargets(SchoolSchedule schedule, PureHoleRepair.Grid grid) {
        List<Lesson> out = new ArrayList<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.room != null || !grid.movable(l)) {
                continue;
            }
            if (l.course != null && !l.course.requiredRoomTags.isEmpty()) {
                out.add(l);
            }
        }
        return out;
    }

    /** @return 1 free-cell assign, 2 eviction, 3 same-day move, 0 locked. */
    private static int place(PureHoleRepair.Grid grid, Lesson l) {
        LocalDate d = grid.date(l.timeslot);
        int s = grid.slot(l.timeslot);
        List<Room> candidates = l.getAvailableRooms();
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        for (Room r : candidates) {
            Lesson blocker = grid.roomCell.get(r.id + "|" + d + "|" + s);
            if (blocker == null) {
                grid.moveRoom(l, r);
                return 1;
            }
            if (blocker == l) {
                continue; // stale index safety: never displace ourselves
            }
            // (2) evict a sheltering ordinary lesson, room-only
            if (blocker.course != null && blocker.course.requiredRoomTags.isEmpty()
                    && grid.movable(blocker)) {
                Room alt = freeAlternativeFor(grid, blocker, r, d, s);
                if (alt != null) {
                    grid.moveRoom(blocker, alt);
                    grid.moveRoom(l, r);
                    return 2;
                }
            }
        }
        // (3) same-day de-co-schedule: PULL-LAST only - the day's LAST lesson moves into an
        // interior HOLE of its own day where one of its candidate rooms is free. Vacating the
        // last slot cannot open a hole, and only an existing hole is filled, so H17 can only
        // improve. (Tail-EXTEND is NOT allowed: last -> last+1 leaves rank `last` empty, an
        // interior hole by definition - hole migration, the TASK-481 shell-game.)
        String g = grid.group(l);
        TreeMap<Integer, Lesson> day = grid.dayByGroup.getOrDefault(g, Map.of()).get(d);
        if (day != null && !day.isEmpty() && s == day.lastKey() && day.size() >= 2) {
            int last = day.lastKey();
            int first = day.firstKey();
            for (int s2 = first + 1; s2 < last; s2++) {
                if (day.containsKey(s2)) {
                    continue;
                }
                Timeslot target = grid.timeslotAt(d, s2);
                if (target == null || !grid.cellOk(l, target, null, null)) {
                    continue; // cell legality is room-independent
                }
                for (Room r : candidates) {
                    if (!grid.roomCell.containsKey(r.id + "|" + d + "|" + s2)) {
                        grid.moveTo(l, target);
                        grid.moveRoom(l, r);
                        return 3;
                    }
                }
            }
        }
        // (4) swap with an ordinary same-group lesson: L -> O's cell (tag room free there),
        // O -> L's cell (any free ordinary room there). Both cells stay occupied: no H17.
        for (var de : grid.dayByGroup.getOrDefault(g, Map.of()).entrySet()) {
            for (Lesson o : new ArrayList<>(de.getValue().values())) {
                if (o == l || !grid.movable(o)
                        || o.course == null || !o.course.requiredRoomTags.isEmpty()) {
                    continue;
                }
                LocalDate od = de.getKey();
                int os = grid.slot(o.timeslot);
                Timeslot tsO = o.timeslot;
                Room tagRoomAtO = freeTagRoomAt(grid, l, od, os);
                if (tagRoomAtO == null) {
                    continue;
                }
                if (!roomlessCellOk(grid, o, l.timeslot, l)) {
                    continue; // O's teacher/group/VRP at L's cell (its room changes anyway)
                }
                if (!grid.cellOk(l, tsO, l, o)) {
                    continue;
                }
                Room roomForO = freeOrdinaryRoomAt(grid, o, d, s, tagRoomAtO);
                if (roomForO == null) {
                    continue;
                }
                // commit: O -> L's old cell with its own room, L -> O's cell with the tag room
                grid.moveTo(o, l.timeslot);
                grid.moveRoom(o, roomForO);
                grid.moveTo(l, tsO);
                grid.moveRoom(l, tagRoomAtO);
                return 4;
            }
        }
        return 0;
    }

    /** A candidate tag room of {@code l} free at (d, s), or null. */
    private static Room freeTagRoomAt(PureHoleRepair.Grid grid, Lesson l, LocalDate d, int s) {
        for (Room r : l.getAvailableRooms()) {
            if (!grid.roomCell.containsKey(r.id + "|" + d + "|" + s)) {
                return r;
            }
        }
        return null;
    }

    /** A free assignable room for the ordinary swapper at (d, s), or null. */
    private static Room freeOrdinaryRoomAt(PureHoleRepair.Grid grid, Lesson o, LocalDate d, int s,
            Room exclude) {
        for (Room r : o.getAvailableRooms()) {
            if (r.id.equals(exclude.id)) {
                continue;
            }
            if (!grid.roomCell.containsKey(r.id + "|" + d + "|" + s)) {
                return r;
            }
        }
        return null;
    }

    /**
     * {@link PureHoleRepair.Grid#cellOk} for a lesson whose room is ABOUT to change: the room
     * clause of the shared gate would test the OLD room's occupancy, which is irrelevant for a
     * mover that re-rooms anyway. Evaluates the gate roomless (deindex, null, index), leaving the
     * grid consistent either way.
     */
    private static boolean roomlessCellOk(PureHoleRepair.Grid grid, Lesson o, Timeslot ts,
            Lesson ignore) {
        Room old = o.room;
        grid.deindex(o);
        o.room = null;
        grid.index(o); // index() skips roomCell for a null room
        boolean ok = grid.cellOk(o, ts, ignore, ignore);
        grid.deindex(o);
        o.room = old;
        grid.index(o);
        return ok;
    }

    /** Another free assignable room for an ordinary blocker, untagged rooms first (M-LF spirit). */
    private static Room freeAlternativeFor(PureHoleRepair.Grid grid, Lesson blocker, Room from,
            LocalDate d, int s) {
        List<Room> alts = blocker.getAvailableRooms();
        if (alts == null) {
            return null;
        }
        List<Room> sorted = new ArrayList<>(alts);
        sorted.sort(Comparator.comparingInt(r -> r.tags == null || r.tags.isEmpty() ? 0 : 1));
        for (Room r : sorted) {
            if (r.id.equals(from.id)) {
                continue;
            }
            if (!grid.roomCell.containsKey(r.id + "|" + d + "|" + s)) {
                return r;
            }
        }
        return null;
    }
}
