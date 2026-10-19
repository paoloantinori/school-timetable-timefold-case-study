package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-542 iter-8: deterministic H17 repair for the BLOCKED tail - the hole shape where the
 * day's tail could slide left into the hole but the tail's teacher is busy teaching ANOTHER
 * GROUP exactly in the hole's slot (the G13-Friday anatomy: T10's block split at 11:00
 * because the pre-assign plan puts T10 with G04 there, and the golden never does).
 *
 * <p><b>Why a swap and not a move (the accounting that killed iter-6/7).</b> Every group's
 * placed hours equal its declared quota totals, so ANY move that changes a day's hour count
 * is zero-sum at the hard tier: it closes an H17 hole and opens an H32 deficit. The only
 * net-positive move family is WITHIN-day: swap the blocker with a same-day partner lesson of
 * the blocker's own group (day totals unchanged for both groups), then slide the blocked tail
 * left. Result: both groups' days stay at their totals and become contiguous.
 *
 * <p>Same contract as the {@link HoleRepair} siblings: pure enumeration, deterministic order,
 * {@code conflictsAdded} must stay 0, and the caller's improve-or-noop gate is the final
 * authority (a swap the raw teacher-occupancy checks mis-judged, e.g. an H1 co-presenza
 * exemption the swap breaks, reverts the whole pass).
 */
public final class H17BlockSwap {

    private static final Logger LOG = Logger.getLogger(H17BlockSwap.class);

    /** Same family budget as the siblings: a healthy grid needs a handful of swaps. */
    private static final int MAX_SWAPS_PER_PASS = 40;

    private H17BlockSwap() {
    }

    public record Result(int gapSlotsBefore, int gapSlotsAfter, int blockerSwaps, int conflictsAdded) {}

    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                Consumer<Lesson> nullRoom) {
        if (schedule.getTimeslots() == null) {
            return new Result(0, 0, 0, 0);
        }
        if (!SchoolSchedule.getPromotedLessonIdsStatic().isEmpty()) {
            throw new IllegalStateException("H17BlockSwap.repair called while "
                    + SchoolSchedule.getPromotedLessonIdsStatic().size()
                    + " promoted pin(s) are held - designed for the post-release grid only");
        }
        Map<String, TreeMap<Integer, Lesson>> dayLessons = HoleRepair.buildDayLessons(schedule.getLessons());
        int gapSlotsBefore = HoleRepair.countPenalizedGapSlots(schedule.getLessons());
        if (gapSlotsBefore == 0) {
            return new Result(0, 0, 0, 0);
        }
        long conflictsBefore = StudentDayCompaction.countConflicts(schedule);
        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot =
                StudentDayCompaction.buildTimeslotsByDateAndSlot(schedule.getTimeslots());
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(schedule.getLessons());

        int swaps = 0;
        List<String> dayKeys = new ArrayList<>(dayLessons.keySet());
        dayKeys.sort(Comparator.comparing(k -> k));
        dayLoop:
        for (String dayKey : dayKeys) {
            for (int guard = 0; guard < TimeslotSequence.SLOTS_PER_DAY; guard++) {
                // re-derive the day view from the ENTITIES each round: they are the single
                // source of truth once moves start landing (the hand-maintained index lost
                // a partner during a double swap - measured 22/08 iter-11 debugging)
                Map<String, TreeMap<Integer, Lesson>> live = HoleRepair.buildDayLessons(schedule.getLessons());
                TreeMap<Integer, Lesson> bySlot = live.get(dayKey);
                if (bySlot == null || bySlot.size() < 2) {
                    continue dayLoop;
                }
                List<Integer> holes = HoleRepair.penalizedHoleSlots(bySlot);
                if (holes.isEmpty()) {
                    continue dayLoop;
                }
                boolean closed = false;
                for (int hole : holes) {
                    if (swaps >= MAX_SWAPS_PER_PASS) {
                        break dayLoop;
                    }
                    if (trySwapAndSlide(dayKey, hole, schedule, tsByDateSlot, byTs, moveTimeslot)) {
                        swaps++;
                        closed = true;
                        break;
                    }
                }
                if (!closed) {
                    continue dayLoop;
                }
            }
        }
        // read the AFTER count off the live entities (the moveTimeslot consumer updated their
        // timeslot fields): the day-view maps are a working index, the lessons are the truth
        int gapSlotsAfter = HoleRepair.countPenalizedGapSlots(schedule.getLessons());
        long conflictsAfter = StudentDayCompaction.countConflicts(schedule);
        LOG.infof("TASK-542 H17 block-swap: hole slots %d -> %d, %d blocker swap(s)",
                gapSlotsBefore, gapSlotsAfter, swaps);
        return new Result(gapSlotsBefore, gapSlotsAfter, swaps,
                (int) (conflictsAfter - conflictsBefore));
    }

    /**
     * The one move family: find the tail run right of the hole; if its left slide is blocked
     * only by the first tail lesson's teacher being busy in the hole slot, swap that blocker
     * with a free same-day partner of the blocker's group and slide the tail.
     */
    private static boolean trySwapAndSlide(String dayKey, int hole, SchoolSchedule schedule,
                                           Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot,
                                           Map<Timeslot, List<Lesson>> byTs,
                                           BiConsumer<Lesson, Timeslot> moveTimeslot) {
        Map<String, TreeMap<Integer, Lesson>> live = HoleRepair.buildDayLessons(schedule.getLessons());
        TreeMap<Integer, Lesson> bySlot = live.get(dayKey);
        if (bySlot == null || bySlot.isEmpty()) {
            return false;
        }
        Lesson anchor = bySlot.firstEntry().getValue();
        LocalDate date = anchor.timeslot.getDate();
        Map<Integer, Timeslot> tsBySlot = tsByDateSlot.get(date);
        if (tsBySlot == null) {
            return false;
        }
        // the tail run: contiguous occupied slots starting right after the hole
        int tailStart = -1;
        for (int s = hole + 1; s < TimeslotSequence.SLOTS_PER_DAY; s++) {
            if (bySlot.containsKey(s)) {
                if (tailStart < 0) {
                    tailStart = s;
                }
            } else if (tailStart >= 0) {
                break;
            }
        }
        if (tailStart != hole + 1) {
            return false; // the hole is not adjacent to the tail: not this pass's shape
        }
        Lesson tailHead = bySlot.get(tailStart);
        Teacher tailTeacher = tailHead.getTeacher();
        if (tailTeacher == null) {
            return false;
        }
        // who blocks the hole slot for the tail teacher?
        Timeslot holeTs = tsBySlot.get(hole);
        if (holeTs == null) {
            return false;
        }
        Lesson blocker = teacherLessonAt(byTs, holeTs, tailTeacher, null);
        if (blocker == null || blocker.pinned) {
            return false; // not blocked by a movable foreign lesson
        }
        if (blocker.getStudentGroup() == null
                || blocker.getStudentGroup().equals(tailHead.getStudentGroup())) {
            return false; // same group: HoleRepair's own levels already own that shape
        }
        // a same-day partner of the blocker's group whose teacher is free in the hole slot,
        // and whose slot is free for the blocker's teacher
        TreeMap<Integer, Lesson> blockerDay = live.get(blockerDayKey(blocker, live));
        if (blockerDay == null) {
            return false;
        }
        LocalDate blockerDate = blocker.timeslot.getDate();
        if (!blockerDate.equals(date)) {
            return false; // paranoia: the blocker must share the tail's date (it sits in the hole slot)
        }
        for (Map.Entry<Integer, Lesson> pe : blockerDay.entrySet()) {
            int p = pe.getKey();
            Lesson partner = pe.getValue();
            if (partner == blocker || partner.pinned) {
                continue;
            }
            Teacher partnerTeacher = partner.getTeacher();
            if (partnerTeacher == null) {
                continue;
            }
            Timeslot partnerTs = tsBySlot.get(p);
            if (partnerTs == null) {
                continue;
            }
            // partner's teacher must be free in the hole slot (besides the blocker leaving it)
            if (teacherLessonAt(byTs, holeTs, partnerTeacher, blocker) != null) {
                continue;
            }
            // the blocker's teacher must be free in the partner slot (besides the partner leaving it)
            if (teacherLessonAt(byTs, partnerTs, tailTeacher, partner) != null) {
                continue;
            }
            // commit: the swap, kept live in every index
            move(blocker, partnerTs, moveTimeslot, byTs);
            move(partner, holeTs, moveTimeslot, byTs);
            // slide the tail left, LEFT-to-RIGHT: the leftmost enters the hole first and
            // vacates its cell for the next one (a right-to-left order would check the next
            // mover against a cell its own run-mate still occupies)
            Integer[] tailSlots = bySlot.tailMap(tailStart).keySet().toArray(new Integer[0]);
            for (int i = 0; i < tailSlots.length; i++) {
                Lesson l = bySlot.get(tailSlots[i]);
                if (l == null) {
                    continue;
                }
                Timeslot left = tsBySlot.get(tailSlots[i] - 1);
                if (left == null) {
                    LOG.debugf("H17 block-swap: slide abort, no timeslot at slot %d", tailSlots[i] - 1);
                    return false;
                }
                Lesson midBlock = teacherLessonAt(byTs, left, l.getTeacher(), l);
                if (midBlock != null) {
                    LOG.debugf("H17 block-swap: slide blocked at slot %d by lesson %d",
                            tailSlots[i] - 1, midBlock.id);
                    return false; // mid-slide block: the gate will revert if we committed partially
                }
                move(l, left, moveTimeslot, byTs);
            }
            return true;
        }
        return false;
    }

    /** The first lesson by {@code teacher} in {@code ts}'s occupancy, excluding {@code exclude}. */
    private static Lesson teacherLessonAt(Map<Timeslot, List<Lesson>> byTs, Timeslot ts,
                                          Teacher teacher, Lesson exclude) {
        List<Lesson> at = byTs.get(ts);
        if (at == null) {
            return null;
        }
        for (Lesson l : at) {
            if (l == exclude || l.timeslot == null) {
                continue;
            }
            Teacher t = l.getTeacher();
            if (t != null && t.equals(teacher)) {
                return l;
            }
        }
        return null;
    }

    private static String blockerDayKey(Lesson blocker, Map<String, TreeMap<Integer, Lesson>> dayLessons) {
        for (Map.Entry<String, TreeMap<Integer, Lesson>> e : dayLessons.entrySet()) {
            if (e.getValue().containsValue(blocker)) {
                return e.getKey();
            }
        }
        return null;
    }

    private static void move(Lesson l, Timeslot to, BiConsumer<Lesson, Timeslot> moveTimeslot,
                             Map<Timeslot, List<Lesson>> byTs) {
        Timeslot from = l.timeslot;
        moveTimeslot.accept(l, to);
        if (from != null) {
            List<Lesson> at = byTs.get(from);
            if (at != null) {
                at.remove(l);
            }
        }
        byTs.computeIfAbsent(to, k -> new ArrayList<>()).add(l);
    }

}
