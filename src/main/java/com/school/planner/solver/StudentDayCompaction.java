package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;

/**
 * TASK-390/393: deterministic student-day gap compaction, shared by the
 * construction phase's tail ({@code DayClusteredConstructionPhase}, TASK-390)
 * and the post-local-search {@code CompactionPhase} (TASK-393). Closes internal
 * (group, date) H17 gaps the way a human planner packs a day tight.
 *
 * <p><b>Improve-or-noop.</b> A gap is only ever closed by moving the day's
 * LAST (max-slot) lesson down into it: that removes a trailing slot AND fills a
 * hole, so it strictly REDUCES the day's internal-gap count. Moving any
 * non-max lesson would merely SHIFT the gap elsewhere (same count) - never
 * committed, so as a final phase this never trades recurrence/soft for nothing.
 * If the max lesson is pinned or can reach no gap legally, the day is left as
 * is (the "forced" case, for the local search / ruin).
 *
 * <p><b>Conflict-free by construction.</b> A move is committed only when the gap
 * slot is in the mover's VRP value range ({@link Lesson#getAvailableTimeslots})
 * AND no OTHER lesson already at that slot shares the mover's teacher (H1) or
 * group (H3) - checked against the ACTUAL placements via an authoritative
 * timeslot->lessons index, NOT any multiplicity-losing Set map. One H3 (-1000)
 * would dwarf the H17 (-1) gaps it closes, so {@code Result.conflictsAdded} must
 * always be 0. Pinned lessons are never moved.
 *
 * <p><b>Move mechanics are the caller's.</b> The phase-specific
 * {@code Moves.change(...)} execution is passed in as {@code moveTimeslot} +
 * {@code nullRoom} callbacks, so this class stays free of any Timefold phase
 * context and is unit-testable. The room is nulled on each move so the room
 * construction heuristic reassigns it conflict-free.
 *
 * <p><b>Deterministic:</b> groups processed in name order, dates in natural
 * order - a move in one group can occupy a teacher slot that changes which gaps
 * another can close, so a reproducible order is required (the solver demands
 * reproducible construction).
 */
public final class StudentDayCompaction {

    private StudentDayCompaction() {
    }

    /**
     * {@code gapsBefore/gapsAfter} = internal student-day gaps; {@code moved} = lessons
     * relocated to close gaps; {@code anchored} = left-anchor relocations (TASK-405);
     * {@code conflictsAdded} must be 0.
     */
    public record Result(int gapsBefore, int gapsAfter, int moved, int anchored, long conflictsAdded) {
    }

    public static Result compact(SchoolSchedule schedule,
                                 BiConsumer<Lesson, Timeslot> moveTimeslot,
                                 Consumer<Lesson> nullRoom) {
        if (schedule.getTimeslots() == null) {
            return new Result(0, 0, 0, 0, 0);
        }
        long conflictsBefore = countConflicts(schedule);
        int gapsBefore = countGaps(schedule);

        // (date, slotIndex) -> Timeslot for resolving a gap slot to its Timeslot.
        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot =
                buildTimeslotsByDateAndSlot(schedule.getTimeslots());
        // Authoritative occupancy index + deterministic (group name, date) grouping.
        Map<Timeslot, List<Lesson>> byTs = buildOccupancyIndex(schedule.getLessons());
        Map<String, Map<LocalDate, List<Lesson>>> byGroupDate = new TreeMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null) continue;
            if (l.getStudentGroup() == null || l.timeslot.getDate() == null) continue;
            String groupKey = l.getStudentGroup().name != null
                    ? l.getStudentGroup().name : String.valueOf(l.getStudentGroup().id);
            byGroupDate.computeIfAbsent(groupKey, k -> new TreeMap<>())
                    .computeIfAbsent(l.timeslot.getDate(), k -> new ArrayList<>()).add(l);
        }

        int moved = 0;
        int anchored = 0;
        for (Map<LocalDate, List<Lesson>> byDate : byGroupDate.values()) {
            for (Map.Entry<LocalDate, List<Lesson>> de : byDate.entrySet()) {
                List<Lesson> dayLessons = de.getValue();
                Map<Integer, Timeslot> slotTs = tsByDateSlot.getOrDefault(de.getKey(), Map.of());
                for (int iter = 0; iter < TimeslotSequence.SLOTS_PER_DAY; iter++) {
                    TreeMap<Integer, Lesson> bySlot = new TreeMap<>();
                    for (Lesson l : dayLessons) bySlot.put(TimeslotSequence.slotIndexOf(l.timeslot), l);
                    int lo = bySlot.firstKey(), hi = bySlot.lastKey();
                    if (hi - lo + 1 == bySlot.size()) break; // contiguous, no internal gap
                    // Only the max-slot lesson can net-reduce the gap count (moving any
                    // lower lesson merely shifts the gap). If it's pinned, no legal
                    // single move improves this day.
                    Lesson maxLesson = bySlot.get(hi);
                    if (maxLesson == null || maxLesson.pinned) break;
                    // Move it into the LOWEST internal gap it can legally reach.
                    boolean movedThis = false;
                    for (int g = lo + 1; g < hi; g++) {
                        if (bySlot.containsKey(g)) continue; // occupied, not a gap
                        Timeslot gapTs = slotTs.get(g);
                        if (gapTs == null) continue;
                        if (!maxLesson.isSlotLegalFresh(gapTs)) continue; // (TASK-578: per-slot, stale-cache class)
                        if (!isSlotConflictFree(maxLesson, gapTs, byTs)) continue;
                        commitMove(maxLesson, gapTs, byTs, moveTimeslot, nullRoom);
                        moved++;
                        movedThis = true;
                        break;
                    }
                    if (!movedThis) break; // max lesson can't reach any gap -> forced
                }
                anchored += anchorRunLeft(dayLessons, de.getKey(), slotTs, byTs, moveTimeslot, nullRoom);
            }
        }

        int gapsAfter = countGaps(schedule);
        long conflictsAfter = countConflicts(schedule);
        return new Result(gapsBefore, gapsAfter, moved, anchored, conflictsAfter - conflictsBefore);
    }

    /**
     * TASK-405 left-anchor: shift a (group, date)'s contiguous run toward the group's
     * first calendar-open slot, one slot at a time, using the SAME move-max-only trick
     * as the gap compaction applied to the slot BEFORE the run: moving the day's LAST
     * lesson to {@code lo - 1} turns run [lo..hi] into [lo-1..hi-1] - contiguity is
     * preserved (no gap ever created, H17 neutral), the date is unchanged (H7/H11/H14
     * neutral), and the target slot passes the same VRP + conflict gates as every other
     * move here. Stops at the first open slot, at a pinned max lesson, or at an illegal
     * target. Golden measurement behind the rule: the school anchors 99.3% of its
     * group-days at the first open slot; our solutions floated at 81% (TASK-405).
     */
    private static int anchorRunLeft(List<Lesson> dayLessons, LocalDate date,
                                     Map<Integer, Timeslot> slotTs,
                                     Map<Timeslot, List<Lesson>> byTs,
                                     BiConsumer<Lesson, Timeslot> moveTimeslot,
                                     Consumer<Lesson> nullRoom) {
        if (dayLessons.isEmpty()) return 0;
        StudentGroup group = dayLessons.get(0).getStudentGroup();
        if (group == null) return 0;
        int[] window = SchoolSchedule.getGroupCalendarWindow(group.id, date.getDayOfWeek(), date);
        int firstOpenSlot = window[1];
        if (firstOpenSlot < 0) return 0; // closed-day sentinel: nothing to anchor on
        int anchored = 0;
        for (int iter = 0; iter < TimeslotSequence.SLOTS_PER_DAY; iter++) {
            TreeMap<Integer, Lesson> bySlot = new TreeMap<>();
            for (Lesson l : dayLessons) bySlot.put(TimeslotSequence.slotIndexOf(l.timeslot), l);
            int lo = bySlot.firstKey(), hi = bySlot.lastKey();
            if (hi - lo + 1 != bySlot.size()) break; // not contiguous (forced gap) - leave as is
            if (lo <= firstOpenSlot) break;          // already anchored
            Lesson maxLesson = bySlot.get(hi);
            if (maxLesson == null || maxLesson.pinned) break;
            Timeslot targetTs = slotTs.get(lo - 1);
            if (targetTs == null) break;
            if (!maxLesson.isSlotLegalFresh(targetTs)) break; // (TASK-578: per-slot, stale-cache class)
            if (!isSlotConflictFree(maxLesson, targetTs, byTs)) break;
            commitMove(maxLesson, targetTs, byTs, moveTimeslot, nullRoom);
            anchored++;
        }
        return anchored;
    }

    /**
     * TASK-419: the SYMMETRIC counterpart of {@link #compact}'s max-slide - move the day's
     * FIRST (min-slot) lesson UP into the internal gap at {@code gapSlot}, returning true iff
     * the move was committed. Like the max-slide it is improve-or-noop by construction and
     * for the mirror-image reason: the move deletes the day's LEADING slot and fills a hole,
     * so with the lesson count unchanged the day's internal-gap total strictly drops (by
     * {@code min(gapSlot, secondSlot) - firstSlot} &gt;= 1, whichever of the two becomes the
     * new first slot). Same gates as every move here: never a pinned lesson, the target must
     * be in the mover's VRP value range and conflict-free (H1/H3); H7/H11/H14 need no check
     * because the date does not change.
     *
     * <p><b>Only the STAGED cycle uses it</b> ({@link HoleRepair}'s level 3, its last resort).
     * {@link #compact}
     * itself deliberately keeps the max-slide as its ONLY rule: it runs inside the ordinary
     * pipeline as a final phase, where every extra relocation it commits is recurrence (M32/
     * S13) and soft score spent, and the max-slide alone already reaches every day whose gap
     * a single move can close from the tail. Adding a second branch there would change the
     * ordinary solve's output for no measured gain - out of scope, and the ordinary
     * CompactionPhase is deliberately left untouched.
     *
     * @param bySlot the day's live {@code slotIndex -> lesson} map, UPDATED in place on a
     *               committed move (the caller keeps iterating over it)
     */
    static boolean slideMinIntoGap(TreeMap<Integer, Lesson> bySlot, int gapSlot, Timeslot gapTs,
                                   Map<Timeslot, List<Lesson>> byTs,
                                   BiConsumer<Lesson, Timeslot> moveTimeslot, Consumer<Lesson> nullRoom) {
        if (bySlot.size() < 2 || gapTs == null) return false;
        int lo = bySlot.firstKey();
        int hi = bySlot.lastKey();
        // Strictly INTERNAL target only: at or below the first slot it is not a gap fill, at
        // or above the last it would extend the day instead of shortening it.
        if (gapSlot <= lo || gapSlot >= hi || bySlot.containsKey(gapSlot)) return false;
        Lesson minLesson = bySlot.get(lo);
        if (minLesson == null || minLesson.pinned) return false;
        // TASK-578: per-slot legality (stale-cache class; see SurplusHarvest)
        if (!minLesson.isSlotLegalFresh(gapTs)) return false;
        if (!isSlotConflictFree(minLesson, gapTs, byTs)) return false;
        commitMove(minLesson, gapTs, byTs, moveTimeslot, nullRoom);
        bySlot.remove(lo);
        bySlot.put(gapSlot, minLesson);
        return true;
    }

    /** Commit a relocation: execute the move, null the room, keep the occupancy index live. */
    private static void commitMove(Lesson lesson, Timeslot target, Map<Timeslot, List<Lesson>> byTs,
                                   BiConsumer<Lesson, Timeslot> moveTimeslot, Consumer<Lesson> nullRoom) {
        Timeslot oldTs = lesson.timeslot;
        moveTimeslot.accept(lesson, target);
        nullRoom.accept(lesson);
        List<Lesson> oldList = byTs.get(oldTs);
        if (oldList != null) oldList.remove(lesson);
        byTs.computeIfAbsent(target, k -> new ArrayList<>()).add(lesson);
    }

    /**
     * True iff no OTHER placed lesson at {@code ts} {@link #conflicts} with {@code mover}
     * (shares its non-pseudo teacher, H1, or its group, H3). Canonical cell test shared
     * with {@link BoxRepair} (TASK-401 unified two near-copies) and TASK-419's staged
     * deadlock analysis.
     */
    static boolean isSlotConflictFree(Lesson mover, Timeslot ts, Map<Timeslot, List<Lesson>> byTs) {
        List<Lesson> here = byTs.get(ts);
        if (here == null) return true;
        for (Lesson other : here) {
            if (other == mover) continue;
            if (conflicts(mover, other)) return false;
        }
        return true;
    }

    /**
     * True iff two placements at the SAME timeslot would collide: they share a student
     * group (H3) or a non-pseudo teacher (H1). Symmetric, and the single source of truth
     * for that pair test - {@link #isSlotConflictFree}, {@link BoxRepair}'s single-blocker
     * search and TASK-419's {@code StagedPinPolicy} blocker search all delegate here, so
     * "what counts as a conflict" cannot drift between them (TASK-419 code review).
     *
     * <p>Pseudo teachers are exempt on BOTH sides, matching the real H1 constraint's
     * scoping ({@code realTeacherTimeslottedLessons}: every group's Stage lessons share ONE
     * pseudo Teacher entity, so pseudo co-occupancy is not an H1 violation) - the group-side
     * H3 check still applies to pseudo lessons.
     */
    static boolean conflicts(Lesson a, Lesson b) {
        return sharesRealTeacher(a, b) || sharesGroup(a, b);
    }

    /**
     * The H1 half of {@link #conflicts}: the two lessons share a non-pseudo teacher. Split out
     * (TASK-419) because a SWAP is count-neutral for the teacher's H7 daily total only when the
     * lesson leaving the cell is the arriving lesson's own teacher's - see {@code
     * BoxRepair#passesSwapAwarePlacementGates}, which needs the two halves separately while
     * every conflict test needs their disjunction.
     */
    static boolean sharesRealTeacher(Lesson a, Lesson b) {
        Long teacherId = a.getTeacher() != null && !a.getTeacher().pseudo ? a.getTeacher().id : null;
        return teacherId != null && b.getTeacher() != null && !b.getTeacher().pseudo
                && teacherId.equals(b.getTeacher().id);
    }

    /** The H3 half of {@link #conflicts}: the two lessons share a student group. */
    static boolean sharesGroup(Lesson a, Lesson b) {
        Long groupId = a.getStudentGroup() != null ? a.getStudentGroup().id : null;
        return groupId != null && b.getStudentGroup() != null
                && groupId.equals(b.getStudentGroup().id);
    }

    /**
     * date -&gt; slotIndex -&gt; {@link Timeslot}, the lookup every gap-filling pass needs to
     * turn "the hole is at slot 3 of this date" into the actual grid cell. Shared by {@link
     * #compact} and {@link HoleRepair} (TASK-419), which built the identical map inline.
     * Dateless timeslots are skipped: they can never be the target of a day-level move.
     */
    public static Map<LocalDate, Map<Integer, Timeslot>> buildTimeslotsByDateAndSlot(List<Timeslot> timeslots) {
        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot = new HashMap<>();
        for (Timeslot ts : timeslots) {
            if (ts.getDate() == null) continue;
            tsByDateSlot.computeIfAbsent(ts.getDate(), k -> new HashMap<>())
                    .put(TimeslotSequence.slotIndexOf(ts), ts);
        }
        return tsByDateSlot;
    }

    /**
     * Authoritative timeslot -&gt; placed lessons occupancy index, shared by {@link
     * #compact}, {@link BoxRepair} and TASK-419's staged analysis (which has a bare lesson
     * list, no solution object).
     */
    static Map<Timeslot, List<Lesson>> buildOccupancyIndex(List<Lesson> lessons) {
        Map<Timeslot, List<Lesson>> byTs = new HashMap<>();
        for (Lesson l : lessons) {
            if (l.timeslot == null) continue;
            byTs.computeIfAbsent(l.timeslot, k -> new ArrayList<>()).add(l);
        }
        return byTs;
    }

    /** Total internal student-day gaps (empty slots between a group's first and last lesson each date). */
    public static int countGaps(SchoolSchedule schedule) {
        Map<String, TreeSet<Integer>> byGroupDate = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.getStudentGroup() == null || l.timeslot.getDate() == null) continue;
            byGroupDate.computeIfAbsent(l.getStudentGroup().id + "@" + l.timeslot.getDate(), k -> new TreeSet<>())
                    .add(TimeslotSequence.slotIndexOf(l.timeslot));
        }
        int gaps = 0;
        for (TreeSet<Integer> slots : byGroupDate.values()) {
            gaps += (slots.last() - slots.first() + 1) - slots.size();
        }
        return gaps;
    }

    /**
     * The day's first slot that BOTH exists on the grid AND is open in the group's
     * calendar window (the CONVEX ENVELOPE of the GroupCalendarRule rows for that
     * weekday; no rules = the whole grid). Shared by the passes that land a
     * formerly-EMPTY day (EmptyDayFinisher gate (d), DeclaredDayPreBooker): H31
     * says such a day never starts late. Deliberately NOT
     * SchoolSchedule.getGroupCalendarWindow (that resolves the most-specific dated
     * rule; this envelope is date-blind, and preserves EDF's historical behavior).
     * Returns -1 when the window holds no grid slot.
     */
    public static int firstCalendarOpenSlot(Long groupId, java.time.LocalDate date,
                                            Map<Integer, Timeslot> slots) {
        if (slots == null || slots.isEmpty()) {
            return -1;
        }
        var rules = SchoolSchedule.getGroupCalendarRulesForGroup(groupId);
        int windowMin = Integer.MAX_VALUE;
        int windowMax = Integer.MIN_VALUE;
        boolean any = false;
        if (rules != null) {
            for (var r : rules) {
                if (r.dayOfWeek == date.getDayOfWeek()) {
                    windowMin = Math.min(windowMin, r.minSlot);
                    windowMax = Math.max(windowMax, r.maxSlot);
                    any = true;
                }
            }
        }
        if (!any) {
            windowMin = 0;
            windowMax = TimeslotSequence.SLOTS_PER_DAY - 1;
        }
        for (int s = Math.max(windowMin, 0); s <= Math.min(windowMax, TimeslotSequence.SLOTS_PER_DAY - 1); s++) {
            if (slots.containsKey(s)) {
                return s;
            }
        }
        return -1;
    }

    /** Teacher (H1, non-pseudo) + group (H3) same-timeslot collision count, for the conflict-neutral invariant. */
    public static long countConflicts(SchoolSchedule schedule) {
        Map<String, Integer> at = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null) continue;
            if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                at.merge("t" + l.getTeacher().id + "@" + l.timeslot.id, 1, Integer::sum);
            }
            if (l.getStudentGroup() != null) {
                at.merge("g" + l.getStudentGroup().id + "@" + l.timeslot.id, 1, Integer::sum);
            }
        }
        return at.values().stream().filter(c -> c > 1).count();
    }
}
