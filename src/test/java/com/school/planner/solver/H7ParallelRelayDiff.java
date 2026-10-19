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
 * TASK-621 bench prototype (test-side, measure-only): the H7-PARALLEL-RELAY, the
 * family specified by the integrato-1 diagnosis (step41): an overcap of a
 * HINGE teacher spread across MULTIPLE groups in one day (T13 7h/6 on
 * three groups; the A3 twin T26). The 620 pair dies here because every
 * source day is at full quota and same-group tolerant donors are absent. The
 * missing move follows the TEACHER, not the group: relocate one of the
 * teacher's PARALLEL lessons (a lesson the hinge teacher holds in a HOST group)
 * to a legal cell in ANOTHER day/group with true slack, OR swap it within the
 * host day with a lesson of a DIFFERENT free teacher (the intra-day reorder).
 * The H7 equivalent of the ratified rotation-with-permission.
 *
 * <p>Gates: per-lesson VRP, H1, H7 on EVERY landing (the parallel lesson's own
 * teacher is the hinge teacher: its landing adds a hinge hour there), the
 * TASK-616 room gate, H31 final-state on every touched day (a moved-away hour
 * must not leave the day starting late unless the day keeps an earlier hour),
 * belt verifyComposite over the touched dates, and the wrapper's full-hard
 * improve-or-noop oracle arbitrates in production. Census HRF-H7PAR-*. Only
 * NON-first hours are exportable from the overcap day (the 8-9 first hours are
 * H31-protected: prediction's own constraint).</p>
 */
public final class H7ParallelRelayDiff {

    private static final Map<String, Integer> PAR_REJECTS = new TreeMap<>();

    private static String census(String k) {
        PAR_REJECTS.merge(k, 1, Integer::sum);
        return k;
    }

    private H7ParallelRelayDiff() {
    }

    /** targets = hinge-teacher days in overcap; cures = overcaps cured; moves = lessons moved. */
    public record Result(int targets, int cures, int movesApplied) {
    }

    public static Result finish(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot) {
        Live live = Live.capture(schedule);
        // enumerate (hinge teacher, day) in overcap by non-Stage hours
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
        List<Object[]> targets = new ArrayList<>();
        for (var te : tHours.entrySet()) {
            for (var de : te.getValue().entrySet()) {
                if (de.getValue()[0] > de.getValue()[1]) {
                    targets.add(new Object[] { te.getKey(), de.getKey() });
                    System.out.printf("[HRF-H7PAR-TARGET] %s %s: %d ore (cap %d)%n",
                            te.getKey(), de.getKey(), de.getValue()[0], de.getValue()[1]);
                }
            }
        }
        int cures = 0;
        int moves = 0;
        for (Object[] t : targets) {
            int applied = tryRelay(live, moveTimeslot, (String) t[0], (LocalDate) t[1]);
            if (applied > 0) {
                cures++;
                moves += applied;
            }
        }
        if (Boolean.getBoolean("hrf.debug") && !PAR_REJECTS.isEmpty()) {
            System.out.println("[HRF-H7PAR-debug] reject census: " + PAR_REJECTS);
            PAR_REJECTS.clear();
        }
        return new Result(targets.size(), cures, moves);
    }

    private static int tryRelay(Live live, BiConsumer<Lesson, Timeslot> moveTimeslot,
            String teacherName, LocalDate overcapDate) {
        // the hinge teacher's movable lessons on the overcap date, grouped by host group
        List<Lesson> parallels = new ArrayList<>();
        for (var e : live.days.entrySet()) {
            if (!e.getKey().endsWith("|" + overcapDate)) {
                continue;
            }
            for (Lesson l : e.getValue().values()) {
                if (l.getTeacher() != null && teacherName.equals(l.getTeacher().name) && movable(l)) {
                    parallels.add(l);
                }
            }
        }
        for (Lesson cand : parallels) {
            String hostGroup = cand.course.studentGroup.name;
            int candSlot = TimeslotSequence.slotIndexOf(cand.timeslot);
            TreeMap<Integer, Lesson> hostDay = live.days.get(hostGroup + "|" + overcapDate);
            // H31 guard: the day's FIRST hour is never the export (prediction's rule)
            if (candSlot == hostDay.firstKey()) {
                census("HRF-H7PAR-H31-FIRST");
                continue;
            }
            // SOURCE-side tolerant yield (the iteration-1 lesson measured live: the
            // host day losing one hour must stay inside max(quota - tol, floor) or the
            // relay trades the H7 cure for an H32 deficit on the host group)
            int tol = SchoolScheduleConstraintProvider.quotaFillTolerance(hostGroup);
            // tolerant line = max(quota - tol, floor): decompose strictYield's max
            int strictQ = strictYield(live, hostGroup, overcapDate);
            Integer floorObj = SchoolSchedule.getDayFloor(groupIdOf(live, hostGroup));
            int floor = floorObj == null ? 0 : floorObj;
            int srcYield = Math.max(strictQ - tol, floor);
            if (nonStageHours(hostDay) - 1 < srcYield) {
                census("HRF-H7PAR-SRC-YIELD");
                continue;
            }
            // (a) cross-day relocation of the parallel lesson: any same-host-group
            // day with TRUE slack (non-Stage - 1 >= max(quota, floor): the strict
            // line, the host group is the DONOR here) and a free tail/interior cell
            // the lesson's VRP admits; H7 on the landing (the hinge teacher gains
            // an hour there); nearest date first
            List<Object[]> dests = new ArrayList<>();
            for (var e : live.days.entrySet()) {
                if (!e.getKey().startsWith(hostGroup + "|")
                        || e.getKey().equals(hostGroup + "|" + overcapDate)) {
                    continue;
                }
                LocalDate d2 = live.dateOf(e.getKey());
                if (nonStageHours(e.getValue()) - 1 < strictYield(live, hostGroup, d2)) {
                    continue; // no true slack: the day cannot host one more hour
                }
                // candidate landing cells: the tail (last+1) and any INTERNAL free
                // slot (filling a hole is always quota-legal)
                int last = e.getValue().lastKey();
                for (int s : candidateSlots(e.getValue(), last)) {
                    Timeslot ts = live.tsAt(d2, s);
                    if (ts == null) {
                        continue;
                    }
                    dests.add(new Object[] { Math.abs(d2.toEpochDay() - overcapDate.toEpochDay()), d2, ts });
                }
            }
            dests.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
            for (Object[] d : dests) {
                Timeslot landTs = (Timeslot) d[2];
                LocalDate d2 = (LocalDate) d[1];
                if (!cand.isSlotLegalFresh(landTs)) {
                    census("HRF-H7PAR-VRP");
                    continue;
                }
                if (live.teacherAt(teacherName, landTs) != null) {
                    census("HRF-H7PAR-H1");
                    continue;
                }
                if (live.teacherHoursOn(cand.getTeacher(), d2)
                        >= cand.getTeacher().getEffectiveMaxDailyHours()) {
                    census("HRF-H7PAR-H7-LAND");
                    continue;
                }
                Map<Lesson, Timeslot> origTs = new HashMap<>();
                Map<Lesson, Room> origRoom = new HashMap<>();
                List<Lesson> order = new ArrayList<>();
                Room room = roomFor(live, cand, landTs);
                if (room == null) {
                    census("HRF-H7PAR-ROOM");
                    continue;
                }
                origTs.put(cand, cand.timeslot);
                origRoom.put(cand, cand.room);
                order.add(cand);
                live.move(cand, landTs, room);
                moveTimeslot.accept(cand, landTs);
                boolean ok = verifyNoNewHoles(live, hostGroup, overcapDate)
                        && verifyNoNewHoles(live, hostGroup, d2)
                        && verifyDayStart(live, hostGroup, overcapDate)
                        && verifyComposite(live, order) == null;
                if (!ok) {
                    census("HRF-H7PAR-BELT");
                    revert(live, origTs, origRoom, order, moveTimeslot);
                    continue;
                }
                if (Boolean.getBoolean("hrf.debug")) {
                    System.out.printf("[HRF-H7PAR-applied] %s %s overcap: parallel %s(%s) %s@%d"
                                    + " -> %s@%d (cross-day relay)%n",
                            teacherName, overcapDate, cand.course.subject, teacherName,
                            overcapDate, candSlot, d2, TimeslotSequence.slotIndexOf(landTs));
                }
                return order.size();
            }
        }
        census("HRF-H7PAR-NO-CANDIDATE");
        return 0;
    }

    /** landing slots on a day: internal free slots first (hole fill), then tail+1. */
    private static List<Integer> candidateSlots(TreeMap<Integer, Lesson> day, int last) {
        List<Integer> out = new ArrayList<>();
        for (int s = day.firstKey(); s < last; s++) {
            if (!day.containsKey(s)) {
                out.add(s);
            }
        }
        out.add(last + 1);
        return out;
    }

    private static int strictYield(Live live, String group, LocalDate date) {
        int quota = 0;
        for (var q : live.quotas) {
            if (q.studentGroup != null && q.date != null
                    && group.equals(q.studentGroup.name) && date.equals(q.date)) {
                quota = q.aulaHours + q.labHours;
                break;
            }
        }
        Integer floor = SchoolSchedule.getDayFloor(groupIdOf(live, group));
        return Math.max(quota, floor == null ? 0 : floor);
    }

    private static Long groupIdOf(Live live, String group) {
        for (var e : live.days.entrySet()) {
            if (e.getKey().startsWith(group + "|")) {
                return e.getValue().firstEntry().getValue().course.studentGroup.id;
            }
        }
        return null;
    }

    /** H31 final-state: a day that still HAS lessons must not start later than slot 0
     *  unless it already did (conservative bench form: first slot occupied or empty day). */
    private static boolean verifyDayStart(Live live, String group, LocalDate date) {
        TreeMap<Integer, Lesson> day = live.days.get(group + "|" + date);
        return day == null || day.isEmpty() || day.firstKey() == 0;
    }

    private static boolean verifyNoNewHoles(Live live, String group, LocalDate date) {
        TreeMap<Integer, Lesson> day = live.days.get(group + "|" + date);
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
        java.util.Set<LocalDate> dates = new java.util.HashSet<>();
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
