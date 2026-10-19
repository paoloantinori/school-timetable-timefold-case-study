package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
 * TASK-609 v0.1 (RATIFIED DEFAULT ON 14/09, opt-out): the RELAY-k composite repair for
 * the boxed-H0 residual, mechanizing the winning family of the TASK-608 blind-LLM
 * experiment. The replay bench proved the delivered [-11] state admits NO single
 * improving move for the boxed lab hour (its whole VRP is occupied by its own group),
 * yet a chain of 2-3 dependent displacements closes it (-11 to -9 in the bench, and
 * the full 40-move blind solution takes the delivery to zero): the census of those
 * moves is RELAY-2 x11, RELAY-3 x2, plus direct fills this pass does not attempt.
 *
 * <p>What this pass does, per boxed lesson {@code b} (timeslot null, real teacher,
 * not pinned): for each allowed cell {@code T} of {@code b} where {@code b}'s teacher
 * is free and under cap, the cell is held by an unpinned same-group lesson {@code X};
 * the chain displaces {@code X} to a legal landing {@code L} (own VRP, group-free,
 * teacher-free, under cap, H17/H31-shaped day, a room of X's family free), and when
 * {@code L} is itself held by an unpinned lesson {@code W}, one more hop frees {@code L}
 * first (depth caps at 3, the census maximum). Rooms: moved lessons keep their room
 * when it is free at the landing, else take any free room of their family (any room
 * for untagged courses); the boxed lesson takes a free room of its own tag family.
 * No local search ever runs on the delivered state (the staged cycle's tail is purely
 * deterministic), so this pass is the only place these composites can fire.
 *
 * <p>Oracle: the caller's improve-or-noop on the FULL hard score (rooms move, so
 * H2/H29 are in play), same contract as {@link RoomTailFinisher}'s wrapper. The
 * internal gates are conservative (every hop is conflict-free by construction), so a
 * kept chain cannot add hard violations; the wrapper still arbitrates the batch.
 */
public final class CompositeSwapFinisher {

    public static final String FLAG = "planner.construction.composite-swap-finisher.enabled";

    /**
     * Standard RATIFIED opt-out form (maintainer, 14/09): null/absent -> ON, only the
     * explicit literal "false" disarms. The prototype ran opt-in ("true" only) from
     * its first commit (020bac90) through two real-cycle gates; the ratification
     * evidence is the replay bench (two basins +3 each, no-op proven on clean
     * states) plus the gates' zero-cost confirmation on counter=0 deliveries.
     */
    public static boolean enabled() {
        String v = System.getProperty(FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    private CompositeSwapFinisher() {
    }

    /** Census of one pass: what was attempted and what landed. */
    public record Result(int targets, int relay2, int relay3, int directPlaces) {
        public int chainsApplied() {
            return relay2 + relay3 + directPlaces;
        }
    }

    public static Result finish(SchoolSchedule schedule, BiConsumer<Lesson, Timeslot> moveTimeslot) {
        if (!enabled() || schedule.getTimeslots() == null) {
            return new Result(0, 0, 0, 0);
        }
        // Post-release grid only, like every tail sibling: the raw pinned flag must mean
        // authoritative pin by the time this pass runs.
        if (!SchoolSchedule.getPromotedLessonIdsStatic().isEmpty()) {
            throw new IllegalStateException("CompositeSwapFinisher.finish called while "
                    + SchoolSchedule.getPromotedLessonIdsStatic().size()
                    + " promoted pin(s) are held - this pass is designed for the post-release grid only");
        }

        Live live = Live.capture(schedule);
        List<Lesson> boxed = schedule.getLessons().stream()
                .filter(l -> l.timeslot == null && !l.pinned
                        && l.getTeacher() != null && !l.getTeacher().pseudo)
                .sorted(Comparator.comparingLong(l -> l.id))
                .toList();
        int relay2 = 0;
        int relay3 = 0;
        int direct = 0;
        for (Lesson b : boxed) {
            List<Timeslot> cells = new ArrayList<>(b.getAvailableTimeslotsFresh());
            cells.sort(Comparator.comparing(Timeslot::getDate).thenComparing(t -> t.startDateTime));
            for (Timeslot t : cells) {
                Lesson x = live.groupAt(b.course.studentGroup.name, t);
                if (x == null) {
                    // Free cell for the boxed lesson: same gates as any landing (H14/H11/
                    // shape included: a direct place without them can open an H17 hole or
                    // trip H11 at weight 1000 and sink the whole batch through the oracle).
                    if (teacherFreeAndUnderCap(live, b, t) && legalLanding(live, b, t)
                            && placeBoxed(live, moveTimeslot, b, t)) {
                        direct++;
                        break;
                    }
                    continue;
                }
                if (x.pinned || x.getTeacher() == null || x.getTeacher().pseudo || x == b) {
                    continue;
                }
                if (!teacherFreeAndUnderCap(live, b, t)) {
                    continue;
                }
                // Depth 2: X lands on a free legal cell, the boxed lesson takes T.
                Timeslot landing = findLanding(live, x, t);
                if (landing != null) {
                    live.move(x, landing, roomFor(live, x, landing));
                    placeBoxed(live, moveTimeslot, b, t);
                    relay2++;
                    break;
                }
                // Depth 3: free X's landing first by displacing its occupant W one more
                // hop. The bridge cell is BY CONSTRUCTION occupied by W, so it is gated
                // for X's teacher/cap and VRP only (a W->X swap at the same cell is
                // structurally neutral for the day's shape); X's room there is resolved
                // AFTER W's move, when W's room has freed.
                for (Timeslot via : live.cellsOf(x)) {
                    if (via.equals(t) || via.equals(x.timeslot)) {
                        continue;
                    }
                    if (live.teacherBusy(x.getTeacher(), via)
                            || live.teacherHoursOn(x.getTeacher(), via.getDate()) >= capOf(x.getTeacher())) {
                        continue;
                    }
                    Lesson w = live.groupAt(x.course.studentGroup.name, via);
                    if (w == null || w.pinned || w.getTeacher() == null || w.getTeacher().pseudo) {
                        continue;
                    }
                    Timeslot wLanding = findLanding(live, w, via);
                    if (wLanding == null) {
                        continue;
                    }
                    Timeslot wFrom = w.timeslot;
                    Room wRoom = w.room;
                    live.move(w, wLanding, roomFor(live, w, wLanding));
                    Room xRoom = roomFor(live, x, via);
                    if (xRoom != null && placeBoxed(live, moveTimeslot, b, t)) {
                        live.move(x, via, xRoom);
                        relay3++;
                        break;
                    }
                    live.move(w, wFrom, wRoom); // revert the bridge attempt, state intact
                    continue;
                }
                if (b.timeslot != null) {
                    break; // placed by the relay3 above: stop scanning cells for b
                }
            }
        }
        return new Result(boxed.size(), relay2, relay3, direct);
    }

    private static List<Timeslot> sortedCells(Lesson lesson) {
        List<Timeslot> cells = new ArrayList<>(lesson.getAvailableTimeslotsFresh());
        cells.sort(Comparator.comparing(Timeslot::getDate).thenComparing(t -> t.startDateTime));
        return cells;
    }

    private static boolean teacherFreeAndUnderCap(Live live, Lesson b, Timeslot t) {
        if (live.teacherBusy(b.getTeacher(), t)) {
            return false;
        }
        int cap = capOf(b.getTeacher());
        return live.teacherHoursOn(b.getTeacher(), t.getDate()) < cap;
    }

    private static int capOf(Teacher teacher) {
        return teacher.getEffectiveMaxDailyHours();
    }

    /**
     * A legal landing for {@code mover} on a cell it may occupy: inside its VRP, its
     * group free there, its teacher free and under cap, the day H17/H31-shaped
     * (contiguous tail/head extension, internal hole fill, or hour 8 into an empty
     * day), and a room of its family available. Deterministic order (date, hour).
     */
    private static Timeslot findLanding(Live live, Lesson mover, Timeslot avoid) {
        for (Timeslot cand : live.cellsOf(mover)) {
            if (cand.equals(avoid) || cand.equals(mover.timeslot)) {
                continue;
            }
            if (!legalLanding(live, mover, cand)) {
                continue;
            }
            return cand;
        }
        return null;
    }

    private static boolean legalLanding(Live live, Lesson mover, Timeslot cand) {
        if (live.groupAt(mover.course.studentGroup.name, cand) != null) {
            return false;
        }
        if (live.teacherBusy(mover.getTeacher(), cand)
                || live.teacherHoursOn(mover.getTeacher(), cand.getDate()) >= capOf(mover.getTeacher())) {
            return false;
        }
        // H14 (group start date) and H11 (group max daily hours): the shared gate chain
        // (BoxRepair.passesPlacementGates) carries both; this local chain had dropped
        // them (code-review finding), so they are explicit here.
        LocalDate start = mover.course.studentGroup.startDate;
        if (start != null && cand.getDate().isBefore(start)) {
            return false;
        }
        int dayCount = live.dayLessons.getOrDefault(
                dayKey(mover.course.studentGroup.name, cand.getDate()), new TreeMap<>()).size();
        Integer maxHours = mover.course.studentGroup.maxHoursPerDay;
        if (maxHours != null && dayCount + 1 > maxHours) {
            return false;
        }
        if (!shapedForGroup(live, mover, cand)) {
            return false;
        }
        return roomFor(live, mover, cand) != null;
    }

    /**
     * H17/H31 shape for the mover's group on the candidate day: contiguous tail
     * extension (max+1), head extension (min-1, never below hour 8), internal hole
     * fill, or hour 8 into an empty day. Learned in the replay bench: landings that
     * ignore this open H17/H31 holes and sink the chain's hard delta.
     */
    private static boolean shapedForGroup(Live live, Lesson mover, Timeslot cand) {
        String group = mover.course.studentGroup.name;
        LocalDate date = cand.getDate();
        int hour = cand.startDateTime.getHour();
        int firstHour = TimeslotSequence.SLOT_START_TIMES[0].getHour();
        TreeMap<Integer, Lesson> day = live.dayLessons.get(dayKey(group, date));
        if (day == null || day.isEmpty()) {
            return hour == firstHour;
        }
        int max = day.lastKey();
        int min = day.firstKey();
        return hour == max + 1 || (hour == min - 1 && hour >= firstHour)
                || (hour > min && hour < max && !day.containsKey(hour));
    }

    /** The room the lesson keeps at the landing: its own if free, else any free room of its family. */
    private static Room roomFor(Live live, Lesson mover, Timeslot cand) {
        if (mover.room != null && !live.roomTaken(mover.room, cand)) {
            return mover.room;
        }
        for (Room r : live.rooms) {
            boolean familyOk = mover.course.requiredRoomTags.isEmpty()
                    || (r.tags != null && r.tags.stream().anyMatch(mover.course.requiredRoomTags::contains));
            if (familyOk && r.isAssignable() && !live.roomTaken(r, cand)) {
                return r;
            }
        }
        return null;
    }

    private static boolean placeBoxed(Live live, BiConsumer<Lesson, Timeslot> moveTimeslot, Lesson b, Timeslot t) {
        Room room = roomFor(live, b, t);
        if (room == null) {
            return false;
        }
        moveTimeslot.accept(b, t);
        b.room = room;
        live.place(b, t, room);
        return true;
    }

    private static String dayKey(String group, LocalDate date) {
        return group + "|" + date;
    }

    /** Live occupancy indexes, kept honest through every committed move. */
    private static final class Live {
        final List<Room> rooms;
        final Map<String, Lesson> byGroupCell = new HashMap<>();
        final Map<String, Lesson> byTeacherCell = new HashMap<>();
        final Map<String, Map<Long, Integer>> teacherHoursByDate = new HashMap<>();
        final Map<String, TreeMap<Integer, Lesson>> dayLessons = new HashMap<>();
        final Map<String, List<Lesson>> roomCell = new HashMap<>();

        private Live(List<Room> rooms) {
            this.rooms = rooms;
        }

        /**
         * Per-lesson memo of the sorted VRP: getAvailableTimeslotsFresh re-runs the whole
         * 8-filter pipeline (~220us) per call and the failed-phase depth scans would rebuild
         * the same list once per target cell (the dominant cost the efficiency review
         * measured). Invalidated for the mover on every move: its own-slot grandfather
         * clause is the only VRP input a move changes.
         */
        private final Map<Long, List<Timeslot>> vrpMemo = new HashMap<>();

        List<Timeslot> cellsOf(Lesson lesson) {
            return vrpMemo.computeIfAbsent(lesson.id, k -> sortedCells(lesson));
        }

        static Live capture(SchoolSchedule schedule) {
            Live live = new Live(schedule.getRooms() == null ? List.of() : schedule.getRooms());
            for (Lesson l : schedule.getLessons()) {
                if (l.timeslot == null || l.getTeacher() == null) {
                    continue;
                }
                // Pseudo (Stage) lessons count for GROUP occupancy (H3/H11/H17 group them
                // in like any lesson) but never for teacher conflicts/caps (H1/H7 are
                // real-teacher-only): place indexes both, the teacher side self-guards.
                live.place(l, l.timeslot, l.room);
            }
            return live;
        }

        void place(Lesson l, Timeslot t, Room room) {
            byGroupCell.put(cell(l.course.studentGroup.name, t), l);
            if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                byTeacherCell.put(cell(l.getTeacher().name, t), l);
                teacherHoursByDate.computeIfAbsent(l.getTeacher().name, k -> new HashMap<>())
                        .merge(t.getDate().toEpochDay(), 1, Integer::sum);
            }
            dayLessons.computeIfAbsent(dayKey(l.course.studentGroup.name, t.getDate()), k -> new TreeMap<>())
                    .put(t.startDateTime.getHour(), l);
            if (room != null) {
                roomCell.computeIfAbsent(room.id + "@" + cell("", t), k -> new ArrayList<>()).add(l);
            }
        }

        void move(Lesson l, Timeslot to, Room room) {
            vrpMemo.remove(l.id);
            removeFrom(l, l.timeslot);
            l.timeslot = to;
            l.room = room;
            place(l, to, room);
        }

        private void removeFrom(Lesson l, Timeslot from) {
            if (from == null) {
                return;
            }
            byGroupCell.remove(cell(l.course.studentGroup.name, from), l);
            if (l.getTeacher() != null) {
                byTeacherCell.remove(cell(l.getTeacher().name, from), l);
                Map<Long, Integer> hours = teacherHoursByDate.get(l.getTeacher().name);
                if (hours != null) {
                    hours.merge(from.getDate().toEpochDay(), -1, Integer::sum);
                }
            }
            TreeMap<Integer, Lesson> day = dayLessons.get(dayKey(l.course.studentGroup.name, from.getDate()));
            if (day != null) {
                day.remove(from.startDateTime.getHour(), l);
                if (day.isEmpty()) {
                    dayLessons.remove(dayKey(l.course.studentGroup.name, from.getDate()));
                }
            }
            if (l.room != null) {
                List<Lesson> inRoom = roomCell.get(l.room.id + "@" + cell("", from));
                if (inRoom != null) {
                    inRoom.remove(l);
                }
            }
        }

        Lesson groupAt(String group, Timeslot t) {
            return byGroupCell.get(cell(group, t));
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

        private static String cell(String key, Timeslot t) {
            return key + "|" + t.getDate() + "@" + t.startDateTime.getHour();
        }
    }
}
