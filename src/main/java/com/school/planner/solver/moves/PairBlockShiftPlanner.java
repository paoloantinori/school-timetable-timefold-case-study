package com.school.planner.solver.moves;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.PairCellGuard;
import com.school.planner.solver.StudentDayCompaction;
import com.school.planner.solver.TimeslotSequence;

/**
 * TASK-576 F8: the pure selection logic behind {@link PairBlockShiftMove}, kept free of
 * Timefold coupling so it is unit-testable (the WeeklyTemplateBuilder discipline).
 *
 * <p>Given a seed lesson (off its pair's form) and the working population, compute the
 * block (the pair's unpinned lessons on the seed's date) and the common shift {@code k}
 * that lands the block back on the form's cells of that weekday. Returns null when no
 * coherent shift exists (the block is not a uniform slide, or the landing cells are not
 * on the grid): the factory then draws another seed.</p>
 */
public final class PairBlockShiftPlanner {

    private PairBlockShiftPlanner() {
    }

    /** (date, slotIndex) -> Timeslot, the grid the targets must land on. */
    public static Map<LocalDate, Map<Integer, Timeslot>> gridOf(List<Timeslot> timeslots) {
        return StudentDayCompaction.buildTimeslotsByDateAndSlot(timeslots);
    }

    /**
     * The pair's block on the seed's date: every UNPINNED lesson of the same (teacher,
     * group) pair placed on that date, slot-ordered. Pinned lessons are left alone
     * (authoritative practice and promoted pins are not this move's business).
     */
    public static List<Lesson> blockOf(Lesson seed, List<Lesson> population) {
        Teacher teacher = seed.getTeacher();
        var group = seed.getStudentGroup();
        if (teacher == null || group == null || seed.timeslot == null) return List.of();
        LocalDate date = seed.timeslot.getDate();
        TreeSet<Integer> slots = new TreeSet<>();
        Map<Integer, List<Lesson>> bySlot = new HashMap<>();
        // mech (laptop): the seed is itself a block member (the test documents the intent:
        // population may not repeat it) - scan seed + population.
        List<Lesson> scan = new ArrayList<>(population.size() + 1);
        scan.add(seed);
        scan.addAll(population);
        for (Lesson lesson : scan) {
            if (lesson.pinned || lesson.timeslot == null || !lesson.timeslot.getDate().equals(date)) continue;
            var lt = lesson.getTeacher();
            var lg = lesson.getStudentGroup();
            if (lt == null || lg == null || lt.id == null || lg.id == null) continue;
            if (!lt.id.equals(teacher.id) || !lg.id.equals(group.id)) continue;
            int slot = TimeslotSequence.slotIndexOf(lesson.timeslot);
            slots.add(slot);
            bySlot.computeIfAbsent(slot, s -> new ArrayList<>()).add(lesson);
        }
        List<Lesson> block = new ArrayList<>();
        for (int slot : slots) {
            block.addAll(bySlot.get(slot));
        }
        return block;
    }

    /**
     * The common shift k that lands the block on the form's cells of the seed's weekday:
     * k is the offset of the seed from the NEAREST form cell of that weekday, and the
     * plan is only valid when every block member's (slot - k) is a form cell of the same
     * weekday AND exists on the grid. Returns the targets list (aligned with the block)
     * or null.
     */
    public static List<Timeslot> targetsOf(Lesson seed, List<Lesson> block, Set<String> formCells,
            Map<LocalDate, Map<Integer, Timeslot>> grid) {
        if (block.isEmpty() || seed.timeslot == null) return null;
        DayOfWeek weekday = seed.timeslot.getDate().getDayOfWeek();
        LocalDate date = seed.timeslot.getDate();
        int seedSlot = TimeslotSequence.slotIndexOf(seed.timeslot);

        Set<Integer> weekdayFormSlots = new HashSet<>();
        for (String cell : formCells) {
            String[] parts = cell.split("\\|");
            if (parts.length == 2 && weekday.name().equals(parts[0])) {
                weekdayFormSlots.add(Integer.parseInt(parts[1]));
            }
        }
        if (weekdayFormSlots.isEmpty()) return null;

        // nearest form cell -> the candidate shift
        int bestDistance = Integer.MAX_VALUE;
        int k = 0;
        for (int formSlot : weekdayFormSlots) {
            int distance = Math.abs(seedSlot - formSlot);
            if (distance < bestDistance) {
                bestDistance = distance;
                k = seedSlot - formSlot;
            }
        }
        if (k == 0) return null; // seed already on a form cell: not this move's business

        var dayGrid = grid.get(date);
        if (dayGrid == null) return null;
        List<Timeslot> targets = new ArrayList<>(block.size());
        for (Lesson lesson : block) {
            int slot = TimeslotSequence.slotIndexOf(lesson.timeslot);
            int targetSlot = slot - k;
            if (!weekdayFormSlots.contains(targetSlot)) return null; // not a uniform slide onto the form
            Timeslot target = dayGrid.get(targetSlot);
            if (target == null) return null; // off the grid (window edge)
            targets.add(target);
        }
        return targets;
    }

    /** The move's description line, for logs and toString. */
    public static String describe(Lesson seed, int k) {
        var teacher = seed.getTeacher();
        var group = seed.getStudentGroup();
        return "PairBlockShift[" + (teacher == null ? "?" : teacher.name) + " x "
                + (group == null ? "?" : group.name) + " " + seed.timeslot.getDate()
                + " shift " + (-k) + "]";
    }

    /** Gate helper: the seed must belong to a pair with a form and be off it. */
    public static boolean isCandidate(Lesson lesson) {
        return lesson != null && !lesson.pinned && lesson.timeslot != null
                && PairCellGuard.hasForm(lesson)
                && !PairCellGuard.protects(lesson);
    }
}
