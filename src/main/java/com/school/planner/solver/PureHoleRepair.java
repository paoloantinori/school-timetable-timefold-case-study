package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
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

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-542 iter-27 (mandato maintainer 23/08): the PURE hole repairer PORTED INTO the cycle -
 * a deterministic post-B-ROOM pass, sibling of {@link ExactFinisher}/{@link
 * QuotaFillRepair}, running after the room solve and before the final save. The offline
 * prototype (scripts/repair/pure-hard-repairer.py, scalability curve in
 * iterations-542.md iter-24-ter) closes 76-90% of construction-profile H17 holes in
 * seconds; this port brings the same move families into the product so ANY multi-start
 * draw can be pulled toward 0 holes before delivery (iter-24: the MT-LS is a lottery,
 * -6..-715 on the same deterministic construction - this pass is the deterministic pull).
 *
 * <p><b>Move families (identical to the validated prototype v4):</b> a pull-up only
 * CLOSES a hole when the mover is the day's LAST lesson (the run compacts); pulling the
 * lesson right after the hole just migrates the hole right. So every strategy targets
 * filling the hole without ever opening one:
 * <ol>
 * <li><b>pull-last</b>: the day's LAST lesson moves into the hole (same day, same room,
 *     per-day hour count unchanged -> H32/H33 invariant by construction).</li>
 * <li><b>blocker-relocate + pull-last</b>: the lesson blocking the hole cell for the
 *     mover's teacher relocates WITHIN ITS OWN DAY, and only into a cell that FILLS an
 *     existing hole of that day or extends the run's tail by one (never opens a hole),
 *     then the pull-last runs.</li>
 * <li><b>cross-day exchange</b>: the day's last lesson L trades places with a same-group
 *     lesson O of another day whose (teacher/room/VRP) allows O at the HOLE cell and L at
 *     O's old cell: O fills the hole, L takes O's slot. Both days keep their hour counts
 *     (H32/H33 invariant), both moves land on previously-occupied or hole cells (no hole
 *     opened), and a day's first hour can never move (L is the day's last, O's cell is
 *     occupied so not before its day's first).</li>
 * </ol>
 *
 * <p><b>Gates, cheapest first, VRP last</b> (the ExactFinisher ordering): H1/H3 via
 * in-memory occupancy maps, H5 via the schedule's VACATION records, H2 via the room
 * occupancy (post-B-ROOM a mover carries its room: the room must be free at the target
 * cell or belong to the exchange partner), then {@link Lesson#getAvailableTimeslots}
 * membership last (linear scan). Pseudo teachers are exempt from H1/H5 (their
 * co-presence is legal - the offline prototype's first bug). Moves are committed only
 * through the shared callbacks, never direct field writes.
 *
 * <p><b>Oracle</b>: the caller wraps the pass in the standard improve-or-noop contract
 * evaluated on the TRUE score (constraint-match snapshot, timeslot-tier hard): a pass
 * that worsened the authoritative hard is rolled back wholesale, so any mini-gate
 * residual risk dies at the boundary.
 *
 * <p><b>Flag-gated DEFAULT OFF</b> ({@code planner.construction.pure-hole-repair.enabled},
 * opt-IN: only the literal "true" arms it) until maintainer's ratification.
 */
public final class PureHoleRepair {

    private static final Logger LOG = Logger.getLogger(PureHoleRepair.class);

    /** Opt-in flag (standard form, DEFAULT OFF until ratification). */
    public static final String FLAG = "planner.construction.pure-hole-repair.enabled";

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(FLAG));
    }

    private PureHoleRepair() {
    }

    /** holesBefore/holesAfter/moves - the pass's own accounting (the oracle is the caller's). */
    public record Result(int holesBefore, int holesAfter, int moves, int conflictsAdded) {
    }

    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                BiConsumer<Lesson, Room> moveRoom) {
        if (!enabled() || schedule.getTimeslots() == null || schedule.getLessons() == null) {
            return new Result(0, 0, 0, 0);
        }
        Grid grid = new Grid(schedule, moveTimeslot, moveRoom);
        int before = grid.holes().size();
        if (before == 0) {
            LOG.info("Pure hole repair (TASK-542 iter-27): 0 holes at entry, inert");
            return new Result(0, 0, 0, 0);
        }
        int moves = 0;
        while (moves < 5_000) {
            List<Grid.Hole> holes = grid.holes();
            if (holes.isEmpty()) {
                break;
            }
            boolean progressed = false;
            for (Grid.Hole hole : holes) {
                if (grid.repair(hole)) {
                    moves++;
                    progressed = true;
                    break;
                }
            }
            if (!progressed) {
                break; // every remaining hole is locked
            }
        }
        int after = grid.holes().size();
        LOG.infof("Pure hole repair (TASK-542 iter-27): holes %d -> %d in %d move-set(s)",
                before, after, moves);
        return new Result(before, after, moves, 0);
    }

    /** The in-memory occupancy grid - the python prototype's Grid, same semantics. */
    static final class Grid {
        final SchoolSchedule schedule;
        final BiConsumer<Lesson, Timeslot> moveTimeslot;
        final BiConsumer<Lesson, Room> moveRoom;
        final Map<String, List<Timeslot>> tsByDate = new TreeMap<>();
        final Map<Timeslot, Integer> slotOfTs = new HashMap<>();
        final Set<String> pseudo = new HashSet<>();
        final Map<String, Set<LocalDate>> vacations = new HashMap<>();
        final Map<String, Map<LocalDate, TreeMap<Integer, Lesson>>> dayByGroup = new LinkedHashMap<>();
        final Map<String, Lesson> teacherCell = new HashMap<>();  // t|date|slot -> lesson
        final Map<String, Lesson> roomCell = new HashMap<>();     // roomId|date|slot -> lesson
        final Map<Long, Lesson> byId = new HashMap<>();

        record Hole(String group, LocalDate date, int slot) {
        }

        Grid(SchoolSchedule schedule, BiConsumer<Lesson, Timeslot> moveTimeslot,
                BiConsumer<Lesson, Room> moveRoom) {
            this.schedule = schedule;
            this.moveTimeslot = moveTimeslot;
            this.moveRoom = moveRoom;
            for (Timeslot ts : schedule.getTimeslots()) {
                tsByDate.computeIfAbsent(ts.getDate().toString(), k -> new ArrayList<>()).add(ts);
            }
            for (var e : tsByDate.entrySet()) {
                List<Timeslot> list = e.getValue();
                list.sort(Comparator.comparing(t -> t.startDateTime));
                for (int i = 0; i < list.size(); i++) {
                    slotOfTs.put(list.get(i), i);
                }
            }
            for (Lesson l : schedule.getLessons()) {
                byId.put(l.id, l);
                Teacher t = l.getTeacher();
                if (t != null && t.pseudo) {
                    pseudo.add(t.name);
                }
            }
            if (schedule.getTeacherAvailabilities() != null) {
                for (var ta : schedule.getTeacherAvailabilities()) {
                    if (ta.type != AvailabilityType.VACATION || ta.teacher == null || ta.teacher.pseudo) {
                        continue;
                    }
                    Set<LocalDate> dates = vacations.computeIfAbsent(ta.teacher.name, k -> new HashSet<>());
                    LocalDate d = ta.startDateTime.toLocalDate();
                    while (!d.isAfter(ta.endDateTime.toLocalDate())) {
                        dates.add(d);
                        d = d.plusDays(1);
                    }
                }
            }
            for (Lesson l : schedule.getLessons()) {
                if (l.timeslot != null) {
                    index(l);
                }
            }
        }

        String group(Lesson l) {
            return l.getStudentGroup() != null ? l.getStudentGroup().name : null;
        }

        String teacher(Lesson l) {
            return l.getTeacher() != null ? l.getTeacher().name : null;
        }

        int slot(Timeslot ts) {
            return slotOfTs.get(ts);
        }

        LocalDate date(Timeslot ts) {
            return ts.getDate();
        }

        void index(Lesson l) {
            LocalDate d = date(l.timeslot);
            int s = slot(l.timeslot);
            String g = group(l);
            if (g != null) {
                dayByGroup.computeIfAbsent(g, k -> new TreeMap<>())
                        .computeIfAbsent(d, k -> new TreeMap<>()).put(s, l);
            }
            String t = teacher(l);
            if (t != null && !pseudo.contains(t)) {
                teacherCell.put(t + "|" + d + "|" + s, l);
            }
            if (l.room != null) {
                roomCell.put(l.room.id + "|" + d + "|" + s, l);
            }
        }

        void deindex(Lesson l) {
            LocalDate d = date(l.timeslot);
            int s = slot(l.timeslot);
            String g = group(l);
            if (g != null) {
                TreeMap<Integer, Lesson> day = dayByGroup.getOrDefault(g, Map.of()).get(d);
                if (day != null && day.get(s) == l) {
                    day.remove(s);
                }
            }
            String t = teacher(l);
            if (t != null && !pseudo.contains(t)) {
                teacherCell.remove(t + "|" + d + "|" + s, l);
            }
            if (l.room != null) {
                roomCell.remove(l.room.id + "|" + d + "|" + s, l);
            }
        }

        void moveTo(Lesson l, Timeslot ts) {
            deindex(l);
            moveTimeslot.accept(l, ts);
            index(l);
        }

        /**
         * ROOM-ONLY move through the shared callback with index maintenance (TASK-550: the
         * room-tail finisher needed a sanctioned way to move a room without poking
         * {@code roomCell} by hand). A lesson whose room was null simply gains an index entry.
         */
        void moveRoom(Lesson l, Room r) {
            deindex(l);
            moveRoom.accept(l, r);
            index(l);
        }

        List<Hole> holes() {
            List<Hole> out = new ArrayList<>();
            for (var ge : dayByGroup.entrySet()) {
                for (var de : ge.getValue().entrySet()) {
                    TreeMap<Integer, Lesson> day = de.getValue();
                    if (day.size() < 2) continue;
                    for (int s = day.firstKey() + 1; s < day.lastKey(); s++) {
                        if (!day.containsKey(s)) {
                            out.add(new Hole(ge.getKey(), de.getKey(), s));
                        }
                    }
                }
            }
            return out;
        }

        boolean movable(Lesson l) {
            return !l.pinned && !l.promotedPin;
        }

        /** The move gate chain, cheapest first, VRP last. */
        boolean cellOk(Lesson l, Timeslot ts, Lesson ignoreA, Lesson ignoreB) {
            LocalDate d = date(ts);
            int s = slot(ts);
            String t = teacher(l);
            String g = group(l);
            if (t != null && !pseudo.contains(t)) {
                Lesson holder = teacherCell.get(t + "|" + d + "|" + s);
                if (holder != null && holder != l && holder != ignoreA && holder != ignoreB) {
                    return false;
                }
                Set<LocalDate> vac = vacations.get(t);
                if (vac != null && vac.contains(d)) {
                    return false;
                }
            }
            if (g != null) {
                TreeMap<Integer, Lesson> day = dayByGroup.getOrDefault(g, Map.of()).get(d);
                Lesson holder = day != null ? day.get(s) : null;
                if (holder != null && holder != l && holder != ignoreA && holder != ignoreB) {
                    return false;
                }
            }
            if (l.room != null) {
                Lesson holder = roomCell.get(l.room.id + "|" + d + "|" + s);
                if (holder != null && holder != l && holder != ignoreA && holder != ignoreB) {
                    return false;
                }
            }
            // TASK-578: per-slot legality (stale-cache class)
            if (!l.isSlotLegalFresh(ts)) {
                return false; // VRP, evaluated last (per-slot check, TASK-578)
            }
            return true;
        }

        boolean repair(Hole hole) {
            Timeslot tsHole = timeslotAt(hole.date(), hole.slot());
            if (tsHole == null) {
                return false;
            }
            TreeMap<Integer, Lesson> day = dayByGroup.getOrDefault(hole.group(), Map.of()).get(hole.date());
            if (day == null || day.isEmpty()) {
                return false;
            }
            int last = day.lastKey();
            Lesson mover = day.get(last);
            if (!movable(mover)) {
                return false;
            }
            // (1) pull-last: the day's last lesson into the hole
            if (cellOk(mover, tsHole, null, null)) {
                moveTo(mover, tsHole);
                return true;
            }
            // (2) blocker-relocate + pull-last: free the hole cell for the mover's teacher
            String t = teacher(mover);
            if (t != null && !pseudo.contains(t)) {
                Lesson blocker = teacherCell.get(t + "|" + hole.date() + "|" + hole.slot());
                if (blocker != null && blocker != mover && movable(blocker)) {
                    LocalDate bd = date(blocker.timeslot);
                    int bs = slot(blocker.timeslot);
                    TreeMap<Integer, Lesson> bday = dayByGroup
                            .getOrDefault(group(blocker), Map.of()).get(bd);
                    if (bday != null && !bday.isEmpty()) {
                        int bmin = bday.firstKey();
                        int bmax = bday.lastKey();
                        for (int s = 0; s < 16; s++) {
                            if (s == bs || bday.containsKey(s)) continue;
                            Timeslot target = timeslotAt(bd, s);
                            if (target == null) continue;
                            boolean fillsHole = bday.size() >= 2 && bmin < s && s < bmax;
                            boolean tailExtend = bs == bmax && s == bmax + 1;
                            if (!fillsHole && !tailExtend) continue;
                            if (cellOk(blocker, target, null, null)) {
                                Timeslot oldTs = blocker.timeslot;
                                moveTo(blocker, target);
                                if (cellOk(mover, tsHole, null, null)) {
                                    moveTo(mover, tsHole);
                                    return true;
                                }
                                moveTo(blocker, oldTs); // revert
                            }
                        }
                    }
                }
            }
            // (3) cross-day exchange: mover trades with a same-group lesson O of another
            // day; O must be legal at the HOLE cell, mover at O's old cell
            for (var de : dayByGroup.getOrDefault(hole.group(), Map.of()).entrySet()) {
                LocalDate d2 = de.getKey();
                if (d2.equals(hole.date())) continue;
                for (Lesson other : new ArrayList<>(de.getValue().values())) {
                    if (!movable(other)) continue;
                    Timeslot tsOther = other.timeslot;
                    if (!cellOk(other, tsHole, mover, other)) continue;
                    if (!cellOk(mover, tsOther, mover, other)) continue;
                    Timeslot tsLast = timeslotAt(hole.date(), last);
                    if (tsLast == null) continue;
                    // commit: O -> hole cell (fills it), mover -> O's old cell
                    moveTo(mover, tsOther);
                    moveTo(other, tsHole);
                    return true;
                }
            }
            return false;
        }

        Timeslot timeslotAt(LocalDate d, int slot) {
            String ds = d.toString();
            for (Timeslot ts : tsByDate.getOrDefault(ds, List.of())) {
                if (slotOfTs.get(ts) == slot) {
                    return ts;
                }
            }
            return null;
        }
    }
}
