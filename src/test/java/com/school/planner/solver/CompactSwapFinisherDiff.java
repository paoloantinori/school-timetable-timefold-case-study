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
 * blind-live-r1 LEVA A (bench prototype, measure-only, NO src/ counterpart yet):
 * the COMPACT-SWAP INTRA-CLASSE family the blind round found outside the HoleRelay
 * v0.6 alphabet. For an internal H17 hole whose plain tail-slide is teacher-blocked,
 * the family tries the two-lesson permutation INSIDE the same class-day: the lesson
 * BELOW the hole steps UP into the hole and the day's TOP lesson steps DOWN into the
 * freed cell (the blind's exact shape: G11 2026-10-30 Scienze 11-&gt;12 + Gestione
 * 13-&gt;11). Span and hour-count of the day are preserved, the hole closes, the top
 * vacancy lands ABOVE the new span: no new hole can be born (the pair gate requires
 * the down-stepper to BE the day's last lesson).
 *
 * <p>Gates mirror the house finisher set: per-lesson VRP ({@code isSlotLegalFresh},
 * conflict-independent, checked first), H1 final-state per landing cell (the
 * stepping lesson's teacher must hold NO other lesson there), H3 by construction,
 * H7/H31 unchanged (same-day moves, day start untouched), TASK-616 room gate
 * ({@code roomFor} null at apply time reverts the pair). The belt is a real
 * conflict scan (teacher + group double-booking) over the affected date after the
 * pair lands, mirroring {@code HoleRelayFinisher.verifyComposite}; any belt failure
 * reverts both moves. Census keys under {@code -Dcs.debug}: CS-*.</p>
 */
public final class CompactSwapFinisherDiff {

    private static final Map<String, Integer> CS_REJECTS = new TreeMap<>();

    private static String census(String k) {
        CS_REJECTS.merge(k, 1, Integer::sum);
        return k;
    }

    private CompactSwapFinisherDiff() {
    }

    /** targets = internal holes seen; swaps = holes closed by a pair; moves = lessons moved. */
    public record Result(int targets, int swaps, int movesApplied) {
    }

    public static Result finish(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot) {
        Live live = Live.capture(schedule);
        List<Hole> targets = enumerateHoles(live);
        int swaps = 0;
        int moves = 0;
        for (Hole hole : targets) {
            // revalidation (earlier pairs may have shrunk a day's span)
            TreeMap<Integer, Lesson> day = live.days.get(dayKey(hole.groupName(), hole.date()));
            if (day == null || day.size() < 2 || hole.slot() <= day.firstKey()
                    || hole.slot() >= day.lastKey() || day.containsKey(hole.slot())) {
                continue;
            }
            Timeslot holeTs = live.tsAt(hole.date(), hole.slot());
            if (holeTs == null) {
                continue; // grid hole: not ours to fill
            }
            Lesson top = day.lastEntry().getValue();
            // the pair: a lesson BELOW the hole steps UP into it, the TOP lesson
            // steps DOWN into the freed cell. The down-stepper MUST be the day's
            // last lesson (anything lower would open a fresh hole at its old slot).
            // Nearest-below first (the blind's own shape: p = hole - 1).
            for (int p = hole.slot() - 1; p >= day.firstKey(); p--) {
                if (!day.containsKey(p)) {
                    continue;
                }
                Lesson below = day.get(p);
                if (below == top || !movable(below) || !movable(top)) {
                    census("CS-MOVABLE");
                    continue;
                }
                Timeslot belowTs = live.tsAt(hole.date(), p);
                if (belowTs == null) {
                    continue;
                }
                // per-lesson VRP first (conflict-independent, fail fast)
                if (!below.isSlotLegalFresh(holeTs) || !top.isSlotLegalFresh(belowTs)) {
                    census("CS-VRP");
                    continue;
                }
                // H1 final-state: each landing cell must be free of the mover's
                // teacher, IGNORING the pair partner (it vacates its cell; a
                // same-teacher pair lands on two different cells and is legal)
                if (below.getTeacher() != null && !below.getTeacher().pseudo) {
                    Lesson holder = live.teacherAt(below.getTeacher().name, holeTs);
                    if (holder != null && holder != top) {
                        census("CS-H1-BELOW");
                        continue;
                    }
                }
                if (top.getTeacher() != null && !top.getTeacher().pseudo) {
                    Lesson holder = live.teacherAt(top.getTeacher().name, belowTs);
                    if (holder != null && holder != below) {
                        census("CS-H1-TOP");
                        continue;
                    }
                }
                // apply sequentially (the below-lesson's move frees the top's
                // landing cell and possibly its room), TASK-616 room gate per member
                java.util.Map<Lesson, Timeslot> origTs = new java.util.HashMap<>();
                java.util.Map<Lesson, Room> origRoom = new java.util.HashMap<>();
                List<Lesson> appliedOrder = new ArrayList<>();
                origTs.put(below, below.timeslot);
                origRoom.put(below, below.room);
                appliedOrder.add(below);
                Room roomBelow = roomFor(live, below, holeTs);
                if (roomBelow == null) {
                    census("CS-ROOM-BELOW");
                    continue;
                }
                live.move(below, holeTs, roomBelow);
                moveTimeslot.accept(below, holeTs);
                origTs.put(top, top.timeslot);
                origRoom.put(top, top.room);
                appliedOrder.add(top);
                Room roomTop = roomFor(live, top, belowTs);
                if (roomTop == null) {
                    census("CS-ROOM-TOP");
                    revert(live, origTs, origRoom, appliedOrder);
                    continue;
                }
                live.move(top, belowTs, roomTop);
                moveTimeslot.accept(top, belowTs);
                if (verifyDate(live, hole.date()) != null) {
                    revert(live, origTs, origRoom, appliedOrder);
                    continue;
                }
                swaps++;
                moves += 2;
                if (Boolean.getBoolean("cs.debug")) {
                    System.out.printf("[CS-applied] %s %s hole@%d: %s(%s) %d->%d + %s(%s) %d->%d%n",
                            hole.groupName(), hole.date(), hole.slot(),
                            below.course.subject, below.getTeacher().name,
                            origTs.get(below).startDateTime.getHour(), hole.slot(),
                            top.course.subject, top.getTeacher().name,
                            origTs.get(top).startDateTime.getHour(), p);
                }
                break; // hole closed: next target
            }
        }
        if (Boolean.getBoolean("cs.debug") && !CS_REJECTS.isEmpty()) {
            System.out.println("[CS-debug] reject census: " + CS_REJECTS);
            CS_REJECTS.clear();
        }
        return new Result(targets.size(), swaps, moves);
    }

    private static void revert(Live live, Map<Lesson, Timeslot> origTs, Map<Lesson, Room> origRoom,
            List<Lesson> appliedOrder) {
        // STRICT reverse application order: the last mover returns first (its
        // landing cell is an earlier mover's origin, so an out-of-order revert
        // would overwrite the live index and orphan an entry)
        for (int i = appliedOrder.size() - 1; i >= 0; i--) {
            Lesson l = appliedOrder.get(i);
            live.move(l, origTs.get(l), origRoom.get(l));
        }
    }

    // ------------------------------------------------------------------ Live

    /** The pass's own live index, mirroring HoleRelayFinisher.Live's semantics
     *  (kept local so this bench twin cannot drift the production class). */
    private static final class Live {
        final List<Room> rooms;
        final TreeMap<String, TreeMap<Integer, Lesson>> days = new TreeMap<>();
        final Map<String, Lesson> byGroupCell = new HashMap<>();
        final Map<String, Lesson> byTeacherCell = new HashMap<>();
        final Map<String, List<Lesson>> roomCell = new HashMap<>();
        final Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot;

        private Live(SchoolSchedule schedule) {
            this.rooms = schedule.getRooms() == null ? List.of() : schedule.getRooms();
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

        void place(Lesson l, Timeslot t, Room room) {
            days.computeIfAbsent(dayKey(l.course.studentGroup.name, t.getDate()), k -> new TreeMap<>())
                    .put(TimeslotSequence.slotIndexOf(t), l);
            byGroupCell.put(cell(l.course.studentGroup.name, t), l);
            if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                byTeacherCell.put(cell(l.getTeacher().name, t), l);
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
            TreeMap<Integer, Lesson> day = days.get(dayKey(l.course.studentGroup.name, from.getDate()));
            if (day != null) {
                day.remove(TimeslotSequence.slotIndexOf(from), l);
                if (day.isEmpty()) {
                    days.remove(dayKey(l.course.studentGroup.name, from.getDate()));
                }
            }
            byGroupCell.remove(cell(l.course.studentGroup.name, from), l);
            if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                byTeacherCell.remove(cell(l.getTeacher().name, from), l);
            }
            if (l.room != null) {
                List<Lesson> inRoom = roomCell.get(l.room.id + "@" + cell("", from));
                if (inRoom != null) {
                    inRoom.remove(l);
                    if (inRoom.isEmpty()) {
                        roomCell.remove(l.room.id + "@" + cell("", from));
                    }
                }
            }
        }

        Lesson teacherAt(String teacherName, Timeslot t) {
            return byTeacherCell.get(cell(teacherName, t));
        }

        boolean roomTaken(Room r, Timeslot t) {
            List<Lesson> inRoom = roomCell.get(r.id + "@" + cell("", t));
            return inRoom != null && !inRoom.isEmpty();
        }

        Timeslot tsAt(LocalDate date, int slot) {
            Map<Integer, Timeslot> bySlot = tsByDateSlot.get(date);
            return bySlot == null ? null : bySlot.get(slot);
        }
    }

    private static String dayKey(String groupName, LocalDate date) {
        return groupName + "|" + date;
    }

    private static String cell(String name, Timeslot t) {
        return name + "@" + t.getDate() + "@" + TimeslotSequence.slotIndexOf(t);
    }

    private record Hole(String groupName, LocalDate date, int slot) {
    }

    private static List<Hole> enumerateHoles(Live live) {
        List<Hole> holes = new ArrayList<>();
        for (var e : live.days.entrySet()) {
            TreeMap<Integer, Lesson> day = e.getValue();
            if (day.size() < 2) {
                continue;
            }
            int bar = e.getKey().indexOf('|');
            String group = e.getKey().substring(0, bar);
            LocalDate date = LocalDate.parse(e.getKey().substring(bar + 1));
            for (int slot = day.firstKey() + 1; slot < day.lastKey(); slot++) {
                if (!day.containsKey(slot)) {
                    holes.add(new Hole(group, date, slot));
                }
            }
        }
        return holes;
    }

    private static boolean movable(Lesson l) {
        if (l.pinned) {
            return false;
        }
        var t = l.getTeacher();
        return t != null && !t.pseudo;
    }

    /** Same semantics as the production finisher's roomFor (own room if free, else
     *  any free assignable room of the mover's tag family). */
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

    /** The belt: a real teacher+group double-booking scan over the affected date
     *  (the verifyComposite semantics specialized to one date). */
    private static String verifyDate(Live live, LocalDate date) {
        Map<String, Lesson> teacherCell = new HashMap<>();
        Map<String, Lesson> groupCell = new HashMap<>();
        for (var e : live.days.entrySet()) {
            if (!LocalDate.parse(e.getKey().substring(e.getKey().indexOf('|') + 1)).equals(date)) {
                continue;
            }
            for (Lesson l : e.getValue().values()) {
                if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                    String tk = l.getTeacher().name + "@" + l.timeslot.startDateTime.getHour();
                    if (teacherCell.putIfAbsent(tk, l) != null) {
                        return census("CS-BELT-H1");
                    }
                }
                String gk = e.getKey().substring(0, e.getKey().indexOf('|'))
                        + "@" + l.timeslot.startDateTime.getHour();
                if (groupCell.putIfAbsent(gk, l) != null) {
                    return census("CS-BELT-H3");
                }
            }
        }
        return null;
    }
}
