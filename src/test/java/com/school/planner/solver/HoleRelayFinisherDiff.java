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

/**
 * TASK-613 v0.2 (strada verso 0, figlio di TASK-612): the H17 teacher-locked
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
 * <p>FLAG {@code planner.construction.hole-relay.enabled}, OPT-IN DEFAULT OFF.
 * Position: delivery boundary, after CompositeSwapFinisher and the EDF
 * extensions, once per delivery. Oracle: the caller's improve-or-noop on the
 * FULL hard (rooms move: H2/H29 in play). The landing gate per moved lesson is
 * the fresh per-slot VRP (H5/H6/H23/H26/M32 inside), teacher free, H7 cap, room
 * of the family available.
 */
public final class HoleRelayFinisherDiff {

    /** TASK-613 flag, OPT-IN prototype: absent -> OFF, only "true" arms. */
    public static final String FLAG = "hole-relay.diff";

    public static boolean enabled() {
        return true; // diagnostic copy: always armed
    }

    private HoleRelayFinisherDiff() {
    }

    /**
     * {@code targets} = internal holes seen; {@code slides} = holes closed by the
     * plain down-slide; {@code rotations} = permission-rotation moves applied (the
     * census shape: 3 rotations + 1 slide per freed hole); {@code movesApplied} =
     * lessons moved, both kinds.
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
        if (Boolean.getBoolean("hrf.debug") && !HRF_REJECTS.isEmpty()) {
            System.out.println("[HRF-debug] reject census: " + HRF_REJECTS);
            HRF_REJECTS.clear();
        }
        return new Result(targets.size(), slides, rotations, moves);
    }

    /**
     * The ROTATION-WITH-PERMISSION shape, FINAL-STATE SEMANTICS (v0.4, the
     * depth-cap experiment's verdict): the blind's puzzle rules bind the FINAL
     * state only, and its D1 cascade passes through intermediate H1s that the
     * composition resolves - demanding per-step legality (v0.2-v0.3) made the
     * chain gate-bound with H1NET dominant. So the chain is built SYMBOLICALLY
     * (no live application, no per-batch occupancy gates: only the per-lesson
     * VRP, which is conflict-independent), then the whole composite is applied
     * at once and verified with a REAL conflict scan over the affected dates
     * (teacher/group/room double-bookings), reverting everything on failure.
     */
    private static int tryRotationThenSlide(Live live, BiConsumer<Lesson, Timeslot> moveTimeslot,
                                            Timeslot holeTs, Lesson tail, Lesson contender,
                                            int depth, java.util.Set<String> visited) {
        java.util.List<Lesson> chainLessons = new ArrayList<>();
        java.util.List<Timeslot> chainDests = new ArrayList<>();
        // the TAIL is a known mover (the slide vacates it): a dest whose occupant
        // IS the tail is not a blocker, it is the permission itself (the v0.4 stall:
        // the builder recursed to vacate the tail from its own slide destination)
        java.util.List<Lesson> knownMovers = new ArrayList<>();
        knownMovers.add(tail);
        CUR_HOLE_TS = holeTs;
        CUR_TAIL_TEACHER = tail.getTeacher() == null ? null : tail.getTeacher().name;
        if (!buildChain(live, contender, holeTs, depth, visited, knownMovers,
                chainLessons, chainDests)) {
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
        // composite apply (rooms resolved at apply time), full verification after
        List<Timeslot> origs = new ArrayList<>();
        List<Room> origRooms = new ArrayList<>();
        for (int i = 0; i < chainLessons.size(); i++) {
            Lesson l = chainLessons.get(i);
            origs.add(l.timeslot);
            origRooms.add(l.room);
            live.move(l, chainDests.get(i), roomFor(live, l, chainDests.get(i)));
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

    /**
     * Symbolic chain builder: insertion-rotate {@code mover} off the hour of
     * {@code vacateTs} (mover to another hour of its day, lessons between shift),
     * recursing on same-teacher blockers WITHOUT applying anything (the per-step
     * occupancy is irrelevant under final-state semantics; the VRP gate stays
     * per-lesson because it is conflict-independent). An occupant that is already
     * a KNOWN MOVER of the composite (the tail, or a lesson already in the chain)
     * is not a blocker: it vacates by construction. Returns false when no chain
     * completes within depth/visited.
     */
    private static void truncate(List<?> list, int mark) {
        list.subList(mark, list.size()).clear();
    }

    private static void truncateDests(List<Timeslot> list, int mark) {
        list.subList(mark, list.size()).clear();
    }

    // PROTOTYPE CURA (test-side): the DFS needs the hole Ts and the tail's teacher
    // for the teacher-aware symbolic checks (a)/(b); throwaway statics, diagnostic copy only.
    static Timeslot CUR_HOLE_TS;
    static String CUR_TAIL_TEACHER;

    private static boolean buildChain(Live live, Lesson mover, Timeslot vacateTs, int depth,
                                      java.util.Set<String> visited, java.util.List<Lesson> knownMovers,
                                      List<Lesson> chainLessons, List<Timeslot> chainDests) {
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
                        if (!buildChain(live, occ, tryDests.get(i), depth - 1, visited,
                                knownMovers, chainLessons, chainDests)) {
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
                    if (CUR_TAIL_TEACHER != null && m.getTeacher() != null
                            && CUR_TAIL_TEACHER.equals(m.getTeacher().name)
                            && tryDests.get(i).equals(CUR_HOLE_TS)) {
                        census("HRF-SYM-HOLE");
                        blocked = true;
                    }
                }
                if (blocked) {
                    truncate(chainLessons, mark);
                    truncateDests(chainDests, mark);
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
     * The composite verification (final-state semantics): over every date touched
     * by the chain, scan ALL lessons for teacher and group double-bookings. The
     * per-lesson VRP and the room assignment ran at apply time; this catches
     * exactly what the single-entry live maps cannot represent.
     */        /**
     * The movable guard for lessons drawn from the live day index (placed by
     * construction): unpinned, real teacher.
     */
    /**
     * The composite verification (final-state semantics): over every date touched
     * by the chain, scan ALL lessons for teacher and group double-bookings. The
     * per-lesson VRP ran in the chain builder; rooms resolve at apply time via
     * the multi-entry roomCell (a taken family room is simply not offered twice).
     */
    private static String verifyComposite(Live live, List<Lesson> chain) {
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
                            System.out.println("[DIFF-verify] H1 collision " + tk + " on " + d
                                    + ": " + teacherCell.get(tk).course.studentGroup.name + "#"
                                    + teacherCell.get(tk).id + " vs " + l.course.studentGroup.name
                                    + "#" + l.id);
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
        final TreeMap<String, TreeMap<Integer, Lesson>> days = new TreeMap<>();
        final Map<String, Lesson> byGroupCell = new HashMap<>();
        final Map<String, Lesson> byTeacherCell = new HashMap<>();
        final Map<String, Map<Long, Integer>> teacherHoursByDate = new HashMap<>();
        final Map<String, List<Lesson>> roomCell = new HashMap<>();
        final Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot;
        final Map<String, Long> groupIdByName = new HashMap<>();

        private Live(SchoolSchedule schedule) {
            this.rooms = schedule.getRooms() == null ? List.of() : schedule.getRooms();
            this.tsByDateSlot = StudentDayCompaction
                    .buildTimeslotsByDateAndSlot(schedule.getTimeslots());
            if (schedule.getStudentGroups() != null) {
                schedule.getStudentGroups().forEach(g -> groupIdByName.put(g.name, g.id));
            }
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

        /** The group's day keys in TreeMap (lexicographic) order; a prefix scan
         *  over the sorted keys, no re-sort per call. */
        List<String> dayKeysOf(String groupName) {
            List<String> out = new ArrayList<>();
            String prefix = groupName + "|";
            for (String key : days.tailMap(prefix).keySet()) {
                if (!key.startsWith(prefix)) {
                    break;
                }
                out.add(key);
            }
            return out;
        }

        String dayKeyOf(Lesson l) {
            return dayKey(l.course.studentGroup.name, l.timeslot.getDate());
        }

        LocalDate dateOf(String dayKey) {
            return LocalDate.parse(dayKey.substring(dayKey.lastIndexOf('|') + 1));
        }

        Long groupIdOf(String groupName) {
            return groupIdByName.get(groupName);
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
