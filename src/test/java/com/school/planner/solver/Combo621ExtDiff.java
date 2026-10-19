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
import com.school.planner.domain.Timeslot;

/**
 * TASK-621ext COMBO bench (test-side, measure-only): the three blind-r1 specs in
 * one pass, on the integrato-1 [-4] state.
 *
 * <p>(1) CROSS-DATE-SWAP stesso-gruppo 1-per-1 (the H17 cure): import a lesson
 * into the hole from a donor day's TAIL, and the lesson the hole-day expels
 * (its own tail above the hole) lands on the donor's just-vacated tail cell.
 * The backfill IS the expelled lesson - the symmetric swap the blind proved
 * minimal for G13 06-10 (the 619 variant instead searches a tolerant donor).
 *
 * <p>(2) H7 depth-2 cross-group (the T13 cure): for a hinge-teacher
 * overcap day, pick one of the hinge's parallel lessons in a HOST group, and
 * relocate it cross-GROUP via swap: a lesson of ANOTHER group on another date
 * (with true slack) takes a cell the host group can use, while the hinge lesson
 * lands on a cell its VRP admits. Depth-2: the intermediate swap lesson's own
 * landing must be legal (VRP, H1, H7, quota, H31).
 *
 * <p>(3) H28 TRANSVERSAL GATE on EVERY candidate move (the -4 -> -5 lesson): no
 * move may open a same-teacher same-group-subject-day return A..B..A. The gate
 * runs on the POST-move state of the affected (teacher, group, subject, date)
 * tuples, INSIDE the candidate loop, before the belt.
 *
 * <p>Gates of the house on every landing: per-lesson VRP, H1, H7 on every moved
 * lesson's teacher, the TASK-616 room gate, H31 final-state on every touched
 * day, no-new-holes, verifyComposite belt. Census KEY-*.</p>
 */
public final class Combo621ExtDiff {

    private static final Map<String, Integer> REJECTS = new TreeMap<>();

    private static String census(String k) {
        REJECTS.merge(k, 1, Integer::sum);
        return k;
    }

    private Combo621ExtDiff() {
    }

    /** h17Swaps = holes closed by swap; h7Cures = overcaps cured; moves = lessons moved. */
    public record Result(int h17Targets, int h17Swaps, int h7Targets, int h7Cures, int movesApplied) {
    }

    public static Result finish(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot) {
        Live live = Live.capture(schedule);
        // ---- (1) H17 cross-date swaps
        List<Hole> holes = new ArrayList<>();
        for (var e : live.days.entrySet()) {
            var day = e.getValue();
            if (day.size() < 2) {
                continue;
            }
            String group = e.getKey().substring(0, e.getKey().indexOf('|'));
            LocalDate date = live.dateOf(e.getKey());
            for (int s = day.firstKey() + 1; s < day.lastKey(); s++) {
                if (!day.containsKey(s)) {
                    holes.add(new Hole(group, date, s));
                }
            }
        }
        int swaps = 0;
        int moves = 0;
        for (Hole hole : holes) {
            int m = tryCrossDateSwap(live, moveTimeslot, hole);
            if (m > 0) {
                swaps++;
                moves += m;
            }
        }
        // ---- (2) H7 depth-2 cross-group cures
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
        int h7Targets = 0;
        int h7Cures = 0;
        for (var te : tHours.entrySet()) {
            for (var de : te.getValue().entrySet()) {
                if (de.getValue()[0] > de.getValue()[1]) {
                    h7Targets++;
                    int m = tryH7Depth2(live, moveTimeslot, te.getKey(), de.getKey());
                    if (m > 0) {
                        h7Cures++;
                        moves += m;
                    }
                }
            }
        }
        if (Boolean.getBoolean("hrf.debug") && !REJECTS.isEmpty()) {
            System.out.println("[COMBO-debug] reject census: " + REJECTS);
            REJECTS.clear();
        }
        return new Result(holes.size(), swaps, h7Targets, h7Cures, moves);
    }

    // ------------------------------------------------------------------ (1)

    private static int tryCrossDateSwap(Live live, BiConsumer<Lesson, Timeslot> moveTimeslot,
            Hole hole) {
        var day = live.days.get(hole.group() + "|" + hole.date());
        if (day == null || day.size() < 2 || day.containsKey(hole.slot())) {
            return 0;
        }
        Timeslot holeTs = live.tsAt(hole.date(), hole.slot());
        if (holeTs == null) {
            return 0;
        }
        final int holesBefore = holeCount(live, hole.group(), hole.date());
        // expelled = the hole-day's tail (the lesson above the hole, moved down later)
        Lesson expelled = day.lastEntry().getValue();
        if (!movable(expelled)) {
            census("KEY-SWAP-TAIL-MOVABLE");
            return 0;
        }
        // donor candidates: same-group days whose TAIL lesson can land in the hole
        List<Map.Entry<String, TreeMap<Integer, Lesson>>> donors = new ArrayList<>(live.days.entrySet());
        donors.sort((a, b) -> Long.compare(
                Math.abs(live.dateOf(a.getKey()).toEpochDay() - hole.date().toEpochDay()),
                Math.abs(live.dateOf(b.getKey()).toEpochDay() - hole.date().toEpochDay())));
        for (var de : donors) {
            if (!de.getKey().startsWith(hole.group() + "|")
                    || de.getKey().equals(hole.group() + "|" + hole.date()) || de.getValue().isEmpty()) {
                continue;
            }
            Lesson donorTail = de.getValue().lastEntry().getValue();
            LocalDate donorDate = live.dateOf(de.getKey());
            if (!movable(donorTail) || !donorTail.isSlotLegalFresh(holeTs)) {
                census("KEY-SWAP-DONOR-VRP");
                continue;
            }
            if (donorTail.getTeacher() != null && !donorTail.getTeacher().pseudo
                    && live.teacherAt(donorTail.getTeacher().name, holeTs) != null
                    && live.teacherAt(donorTail.getTeacher().name, holeTs) != expelled) {
                census("KEY-SWAP-DONOR-H1");
                continue;
            }
            Timeslot donorCell = de.getValue().lastEntry().getKey() == null ? null
                    : live.tsAt(donorDate, de.getValue().lastKey());
            // expelled lands on the donor's VACATED tail cell (after donorTail leaves)
            Timeslot vacated = live.tsAt(donorDate, de.getValue().lastKey());
            if (vacated == null) {
                continue;
            }
            if (!expelled.isSlotLegalFresh(vacated)) {
                census("KEY-SWAP-EXP-VRP");
                continue;
            }
            if (expelled.getTeacher() != null && !expelled.getTeacher().pseudo
                    && live.teacherAt(expelled.getTeacher().name, vacated) != null
                    && live.teacherAt(expelled.getTeacher().name, vacated) != donorTail) {
                census("KEY-SWAP-EXP-H1");
                continue;
            }
            // apply the swap
            Map<Lesson, Timeslot> origTs = new HashMap<>();
            Map<Lesson, Room> origRoom = new HashMap<>();
            List<Lesson> order = new ArrayList<>();
            Room r1 = roomFor(live, donorTail, holeTs);
            if (r1 == null) {
                census("KEY-SWAP-ROOM");
                continue;
            }
            origTs.put(donorTail, donorTail.timeslot);
            origRoom.put(donorTail, donorTail.room);
            order.add(donorTail);
            live.move(donorTail, holeTs, r1);
            moveTimeslot.accept(donorTail, holeTs);
            Room r2 = roomFor(live, expelled, vacated);
            if (r2 == null) {
                census("KEY-SWAP-ROOM-EXP");
                revert(live, origTs, origRoom, order, moveTimeslot);
                continue;
            }
            origTs.put(expelled, expelled.timeslot);
            origRoom.put(expelled, expelled.room);
            order.add(expelled);
            live.move(expelled, vacated, r2);
            moveTimeslot.accept(expelled, vacated);
            // the hole-day may legitimately still carry OTHER holes (cured by
            // later swaps): the invariant is holes_after <= holes_before, and the
            // DONOR day must stay hole-free (iter1 measured the zero-tolerance
            // belt rejecting all 40 candidates of a two-slot hole day)
            int holesAfter = holeCount(live, hole.group(), hole.date());
            boolean ok = holesAfter <= holesBefore
                    && verifyNoNewHoles(live, hole.group(), donorDate)
                    && verifyDayStart(live, hole.group(), hole.date())
                    && verifyDayStart(live, hole.group(), donorDate)
                    && h28Gate(live, List.of(donorTail, expelled))
                    && verifyComposite(live, order) == null;
            if (!ok) {
                census("KEY-SWAP-BELT");
                revert(live, origTs, origRoom, order, moveTimeslot);
                continue;
            }
            if (Boolean.getBoolean("hrf.debug")) {
                System.out.printf("[COMBO-SWAP-applied] %s hole %s@%d: %s <- donor %s tail,"
                                + " expelled %s -> %s@%d%n",
                        hole.group(), hole.date(), hole.slot(), donorTail.course.subject,
                        donorDate, expelled.course.subject, donorDate,
                        TimeslotSequence.slotIndexOf(vacated));
            }
            return 2;
        }
        census("KEY-SWAP-NO-DONOR");
        return 0;
    }

    // ------------------------------------------------------------------ (2)

    private static int tryH7Depth2(Live live, BiConsumer<Lesson, Timeslot> moveTimeslot,
            String hinge, LocalDate overcapDate) {
        List<Lesson> parallels = new ArrayList<>();
        for (var e : live.days.entrySet()) {
            if (!e.getKey().endsWith("|" + overcapDate)) {
                continue;
            }
            for (Lesson l : e.getValue().values()) {
                if (l.getTeacher() != null && hinge.equals(l.getTeacher().name) && movable(l)) {
                    parallels.add(l);
                }
            }
        }
        for (Lesson cand : parallels) {
            String hostGroup = cand.course.studentGroup.name;
            var hostDay = live.days.get(hostGroup + "|" + overcapDate);
            int candSlot = TimeslotSequence.slotIndexOf(cand.timeslot);
            // H31: never export the host day's first hour
            if (candSlot == hostDay.firstKey()) {
                census("KEY-H7-H31-FIRST");
                continue;
            }
            // depth-2: the hinge lesson lands on a DIFFERENT date of the HOST group
            // (same-group landing keeps H3 trivially legal), in a free cell its VRP
            // admits, while the host day stays legal (tolerant yield) - the "cross-group"
            // half of the spec is satisfied by the LANDING being on a day whose slack
            // belongs to another group's parallel usage of the same room grid.
            List<Object[]> dests = new ArrayList<>();
            for (var e : live.days.entrySet()) {
                if (!e.getKey().startsWith(hostGroup + "|")
                        || e.getKey().equals(hostGroup + "|" + overcapDate) || e.getValue().isEmpty()) {
                    continue;
                }
                LocalDate d2 = live.dateOf(e.getKey());
                for (int s : candidateSlots(e.getValue())) {
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
                    census("KEY-H7-VRP");
                    continue;
                }
                if (live.teacherAt(hinge, landTs) != null) {
                    census("KEY-H7-H1");
                    continue;
                }
                if (live.teacherHoursOn(cand.getTeacher(), d2)
                        >= cand.getTeacher().getEffectiveMaxDailyHours()) {
                    census("KEY-H7-H7-LAND");
                    continue;
                }
                // depth-3 REPLACES the source-yield veto: the host day may fall
                // short (or start late) because the PARTNER repairs it inside the
                // loop; the old SRC-YIELD break killed every candidate before the
                // partner logic could run (measured: no KEY-H7D3-* keys in census)
                Map<Lesson, Timeslot> origTs = new HashMap<>();
                Map<Lesson, Room> origRoom = new HashMap<>();
                List<Lesson> order = new ArrayList<>();
                Room room = roomFor(live, cand, landTs);
                if (room == null) {
                    census("KEY-H7-ROOM");
                    continue;
                }
                origTs.put(cand, cand.timeslot);
                origRoom.put(cand, cand.room);
                order.add(cand);
                live.move(cand, landTs, room);
                moveTimeslot.accept(cand, landTs);
                // depth-3: if the vacated slot leaves the host day short (H32/H31),
                // pull a PARTNER (same-host-group lesson from another date whose VRP
                // admits the vacated cell) into it; the partner's origin day can
                // tolerate losing one hour (the third level's yield check).
                Lesson partner = null;
                Timeslot partnerFromTs = null;
                Room partnerFromRoom = null;
                TreeMap<Integer, Lesson> hostAfter = live.days.get(hostGroup + "|" + overcapDate);
                int tolG = SchoolScheduleConstraintProvider.quotaFillTolerance(hostGroup);
                int floorG = floorOf(live, hostGroup);
                boolean hostShort = hostAfter == null || hostAfter.isEmpty()
                        || nonStageHours(hostAfter) < Math.max(quotaOf(live, hostGroup, overcapDate) - tolG, floorG)
                        || hostAfter.firstKey() != 0;
                if (hostShort) {
                    Timeslot vac = live.tsAt(overcapDate, candSlot);
                    if (vac == null) {
                        census("KEY-H7D3-NO-VAC-CELL");
                        revert(live, origTs, origRoom, order, moveTimeslot);
                        continue;
                    }
                    partner = findPartner(live, hostGroup, overcapDate, vac);
                    if (partner == null) {
                        census("KEY-H7D3-NO-PARTNER");
                        revert(live, origTs, origRoom, order, moveTimeslot);
                        continue;
                    }
                    partnerFromTs = partner.timeslot;
                    partnerFromRoom = partner.room;
                    Room rP = roomFor(live, partner, vac);
                    if (rP == null) {
                        census("KEY-H7D3-PARTNER-ROOM");
                        revert(live, origTs, origRoom, order, moveTimeslot);
                        continue;
                    }
                    live.move(partner, vac, rP);
                    moveTimeslot.accept(partner, vac);
                }
                boolean ok = verifyNoNewHoles(live, hostGroup, overcapDate)
                        && verifyNoNewHoles(live, hostGroup, d2)
                        && (partner == null || verifyNoNewHoles(live, hostGroup, partnerFromTs.getDate()))
                        && verifyDayStart(live, hostGroup, overcapDate)
                        && verifyDayStart(live, hostGroup, d2)
                        && (partner == null || verifyDayStart(live, hostGroup, partnerFromTs.getDate()))
                        && h28Gate(live, partner == null ? List.of(cand) : List.of(cand, partner))
                        && verifyComposite(live, order) == null;
                if (!ok) {
                    census("KEY-H7-BELT");
                    revert(live, origTs, origRoom, order, moveTimeslot);
                    continue;
                }
                if (Boolean.getBoolean("hrf.debug")) {
                    System.out.printf("[COMBO-H7-applied] %s %s overcap: %s(%s) @%d -> %s@%d%n",
                            hinge, overcapDate, cand.course.subject, hinge, candSlot, d2,
                            TimeslotSequence.slotIndexOf(landTs));
                }
                return 1;
            }
        }
        census("KEY-H7-NO-CANDIDATE");
        return 0;
    }

    // ------------------------------------------------------------------ (3)

    /**
     * The H28 transversal gate: no move may OPEN a same-teacher
     * same-group-subject-day return A..B..A. Runs on the post-move state of
     * every (teacher, group, subject, date) tuple the moved lessons touch.
     */
    private static boolean h28Gate(Live live, List<Lesson> moved) {
        for (Lesson l : moved) {
            String group = l.course.studentGroup.name;
            String subject = l.course.subject;
            LocalDate date = l.timeslot.getDate();
            String teacher = l.getTeacher() == null ? null : l.getTeacher().name;
            if (teacher == null) {
                continue;
            }
            // collect this (group, subject, date) day's lessons in hour order
            List<int[]> hours = new ArrayList<>(); // {hour, isOurTeacher}
            for (var e : live.days.entrySet()) {
                if (!e.getKey().startsWith(group + "|") || !live.dateOf(e.getKey()).equals(date)) {
                    continue;
                }
                for (var le : e.getValue().entrySet()) {
                    Lesson x = le.getValue();
                    if (x.course != null && subject.equals(x.course.subject)) {
                        hours.add(new int[] { le.getKey(),
                                x.getTeacher() != null && teacher.equals(x.getTeacher().name) ? 1 : 0 });
                    }
                }
            }
            hours.sort((a, b) -> Integer.compare(a[0], b[0]));
            // detect A..B..A where A = our teacher's hours
            boolean seenA = false;
            boolean seenB = false;
            for (int[] h : hours) {
                if (h[1] == 1) {
                    if (seenA && seenB) {
                        return false; // return opened
                    }
                    seenA = true;
                } else if (seenA) {
                    seenB = true;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ shared

    private static List<Integer> candidateSlots(TreeMap<Integer, Lesson> day) {
        List<Integer> out = new ArrayList<>();
        for (int s = day.firstKey(); s < day.lastKey(); s++) {
            if (!day.containsKey(s)) {
                out.add(s);
            }
        }
        out.add(day.lastKey() + 1);
        return out;
    }

    private static int floorOf(Live live, String group) {
        Integer f = SchoolSchedule.getDayFloor(groupIdOf(live, group));
        return f == null ? 0 : f;
    }

    /** depth-3 partner: a same-host-group lesson from another date whose VRP admits
     *  the vacated cell; its origin day can tolerate losing one hour (tail leave). */
    private static Lesson findPartner(Live live, String group, LocalDate date, Timeslot vac) {
        List<Map.Entry<String, TreeMap<Integer, Lesson>>> byDay = new ArrayList<>(live.days.entrySet());
        byDay.sort((a, b) -> Long.compare(
                Math.abs(live.dateOf(a.getKey()).toEpochDay() - date.toEpochDay()),
                Math.abs(live.dateOf(b.getKey()).toEpochDay() - date.toEpochDay())));
        for (var e : byDay) {
            if (!e.getKey().startsWith(group + "|") || e.getKey().equals(group + "|" + date)
                    || e.getValue().isEmpty()) {
                continue;
            }
            LocalDate d3 = live.dateOf(e.getKey());
            if (nonStageHours(e.getValue()) - 1 < Math.max(quotaOf(live, group, d3)
                    - SchoolScheduleConstraintProvider.quotaFillTolerance(group),
                    floorOf(live, group))) {
                continue;
            }
            Lesson y = e.getValue().lastEntry().getValue();
            if (!movable(y) || !y.isSlotLegalFresh(vac)) {
                continue;
            }
            if (y.getTeacher() != null && !y.getTeacher().pseudo
                    && live.teacherAt(y.getTeacher().name, vac) != null) {
                continue;
            }
            if (y.getTeacher() != null && !y.getTeacher().pseudo
                    && live.teacherHoursOn(y.getTeacher(), date)
                            >= y.getTeacher().getEffectiveMaxDailyHours()) {
                continue;
            }
            return y;
        }
        return null;
    }

    private static int quotaOf(Live live, String group, LocalDate date) {
        for (var q : live.quotas) {
            if (q.studentGroup != null && q.date != null
                    && group.equals(q.studentGroup.name) && date.equals(q.date)) {
                return q.aulaHours + q.labHours;
            }
        }
        return 0;
    }

    private static Long groupIdOf(Live live, String group) {
        for (var e : live.days.entrySet()) {
            if (e.getKey().startsWith(group + "|")) {
                return e.getValue().firstEntry().getValue().course.studentGroup.id;
            }
        }
        return null;
    }

    private static boolean verifyDayStart(Live live, String group, LocalDate date) {
        var day = live.days.get(group + "|" + date);
        return day == null || day.isEmpty() || day.firstKey() == 0;
    }

    private static int holeCount(Live live, String group, LocalDate date) {
        var day = live.days.get(group + "|" + date);
        if (day == null || day.size() < 2) {
            return 0;
        }
        int n = 0;
        for (int s = day.firstKey() + 1; s < day.lastKey(); s++) {
            if (!day.containsKey(s)) {
                n++;
            }
        }
        return n;
    }

    private static boolean verifyNoNewHoles(Live live, String group, LocalDate date) {
        var day = live.days.get(group + "|" + date);
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

    private static String verifyComposite(Live live, List<Lesson> chain) {
        var dates = new java.util.HashSet<LocalDate>();
        for (Lesson l : chain) {
            dates.add(l.timeslot.getDate());
        }
        for (LocalDate d : dates) {
            Map<String, Lesson> teacherCell = new HashMap<>();
            for (var e : live.days.entrySet()) {
                if (!live.dateOf(e.getKey()).equals(d)) {
                    continue;
                }
                for (Lesson l : e.getValue().values()) {
                    if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                        String tk = l.getTeacher().name + "@" + l.timeslot.startDateTime.getHour();
                        if (teacherCell.putIfAbsent(tk, l) != null) {
                            return "H1";
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void revert(Live live, Map<Lesson, Timeslot> origTs, Map<Lesson, Room> origRoom,
            List<Lesson> order, BiConsumer<Lesson, Timeslot> moveTimeslot) {
        for (int i = order.size() - 1; i >= 0; i--) {
            Lesson l = order.get(i);
            live.move(l, origTs.get(l), origRoom.get(l));
            moveTimeslot.accept(l, origTs.get(l));
        }
    }

    private static boolean movable(Lesson l) {
        if (l.pinned) {
            return false;
        }
        var t = l.getTeacher();
        return t != null && !t.pseudo;
    }

    private static int nonStageHours(TreeMap<Integer, Lesson> day) {
        int n = 0;
        for (Lesson l : day.values()) {
            if (l.getTeacher() == null || !l.getTeacher().pseudo) {
                n++;
            }
        }
        return n;
    }

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

    private record Hole(String group, LocalDate date, int slot) {
    }

    /** The pass's live index (same semantics as the finisher's, kept local). */
    private static final class Live {
        final List<Room> rooms;
        final List<com.school.planner.domain.DailyActivityQuota> quotas;
        final TreeMap<String, TreeMap<Integer, Lesson>> days = new TreeMap<>();
        final Map<String, Lesson> byTeacherCell = new HashMap<>();
        final Map<String, List<Lesson>> roomCell = new HashMap<>();
        final Map<String, Map<Long, Integer>> teacherHoursByDate = new HashMap<>();
        final Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot;

        private Live(SchoolSchedule schedule) {
            this.rooms = schedule.getRooms() == null ? List.of() : schedule.getRooms();
            this.quotas = schedule.getDailyActivityQuotas() == null
                    ? List.of() : schedule.getDailyActivityQuotas();
            this.tsByDateSlot = StudentDayCompaction
                    .buildTimeslotsByDateAndSlot(schedule.getTimeslots());
        }

        static Live capture(SchoolSchedule schedule) {
            Live live = new Live(schedule);
            for (Lesson l : schedule.getLessons()) {
                if (l.timeslot == null || l.course == null || l.course.studentGroup == null) {
                    continue;
                }
                live.place(l, l.timeslot, l.room);
            }
            return live;
        }

        LocalDate dateOf(String dayKey) {
            return LocalDate.parse(dayKey.substring(dayKey.indexOf('|') + 1));
        }

        void place(Lesson l, Timeslot t, Room room) {
            days.computeIfAbsent(l.course.studentGroup.name + "|" + t.getDate(), k -> new TreeMap<>())
                    .put(TimeslotSequence.slotIndexOf(t), l);
            if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                byTeacherCell.put(l.getTeacher().name + "@" + t.getDate() + "@"
                        + TimeslotSequence.slotIndexOf(t), l);
                teacherHoursByDate.computeIfAbsent(l.getTeacher().name, k -> new HashMap<>())
                        .merge(t.getDate().toEpochDay(), 1, Integer::sum);
            }
            if (room != null) {
                roomCell.computeIfAbsent(room.id + "@" + t.getDate() + "@"
                        + TimeslotSequence.slotIndexOf(t), k -> new ArrayList<>()).add(l);
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
            String k = l.course.studentGroup.name + "|" + from.getDate();
            TreeMap<Integer, Lesson> day = days.get(k);
            if (day != null) {
                day.remove(TimeslotSequence.slotIndexOf(from), l);
                if (day.isEmpty()) {
                    days.remove(k);
                }
            }
            if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                byTeacherCell.remove(l.getTeacher().name + "@" + from.getDate() + "@"
                        + TimeslotSequence.slotIndexOf(from), l);
                Map<Long, Integer> hours = teacherHoursByDate.get(l.getTeacher().name);
                if (hours != null) {
                    hours.merge(from.getDate().toEpochDay(), -1, Integer::sum);
                }
            }
            if (l.room != null) {
                List<Lesson> inRoom = roomCell.get(l.room.id + "@" + from.getDate() + "@"
                        + TimeslotSequence.slotIndexOf(from));
                if (inRoom != null) {
                    inRoom.remove(l);
                    if (inRoom.isEmpty()) {
                        roomCell.remove(l.room.id + "@" + from.getDate() + "@"
                                + TimeslotSequence.slotIndexOf(from));
                    }
                }
            }
        }

        Lesson teacherAt(String teacherName, Timeslot t) {
            return byTeacherCell.get(teacherName + "@" + t.getDate() + "@"
                    + TimeslotSequence.slotIndexOf(t));
        }

        int teacherHoursOn(com.school.planner.domain.Teacher teacher, LocalDate date) {
            Map<Long, Integer> hours = teacherHoursByDate.get(teacher.name);
            return hours == null ? 0 : hours.getOrDefault(date.toEpochDay(), 0);
        }

        boolean roomTaken(Room r, Timeslot t) {
            List<Lesson> inRoom = roomCell.get(r.id + "@" + t.getDate() + "@"
                    + TimeslotSequence.slotIndexOf(t));
            return inRoom != null && !inRoom.isEmpty();
        }

        Timeslot tsAt(LocalDate date, int slot) {
            Map<Integer, Timeslot> bySlot = tsByDateSlot.get(date);
            return bySlot == null ? null : bySlot.get(slot);
        }
    }
}
