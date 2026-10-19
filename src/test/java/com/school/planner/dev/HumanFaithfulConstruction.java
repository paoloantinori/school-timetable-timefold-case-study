package com.school.planner.dev;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-396 SPIKE — standalone human-faithful constructive solver (NOT wired into the live
 * 5-phase pipeline; dev/test only). Places lessons one at a time into a HARD-FEASIBLE slot
 * only (VRP gate + H1/H3/H7/H11 in-memory checks, replicated from {@code
 * DayClusteredConstructionPhase}'s private {@code canPlaceLesson}/{@code exceedsDailyCap} —
 * rooms/H2 deliberately omitted, out of scope for the spike), BOXES a lesson (timeslot=null)
 * when no feasible slot exists, and applies BOUNDED chronological backtracking on stall.
 *
 * <p>Placement decisions are made by the feasibility checks, NOT by the score — the resulting
 * schedule is scored afterwards (by the harness via ScoreManager) only to MEASURE
 * hard-at-construction-end. This mirrors how the human planner works ("every placed piece is
 * feasible, the rest stay in the box") and how {@code BacktrackingPreAssigner} already works
 * for the pre-assignment subset.
 *
 * <p>{@code maxUndoDepth} (D) and {@code maxTotalUndos} (U) are the spike's variables: D=0
 * means pure greedy + box (no backtrack); larger D/U allow undoing recent placements to escape
 * dead-ends. The spike characterizes which D/U are viable before they hit Timefold's documented
 * backtracking scalability wall (Branch & Bound is rated Scalable 0/5).
 *
 * <p>Stats (placed / boxed / backtrackCount / maxDepthReached / timeMs) are returned in
 * {@link Result} for the harness to report per slice.
 */
public final class HumanFaithfulConstruction {

    private HumanFaithfulConstruction() {
    }

    /** Run the constructive solver on {@code schedule}'s lessons (filtered by {@code sliceFilter}). */
    public static Result solve(SchoolSchedule schedule, int maxUndoDepth, int maxTotalUndos,
            Predicate<Lesson> sliceFilter) {
        long start = System.currentTimeMillis();
        State s = new State(maxUndoDepth, maxTotalUndos);

        List<Lesson> targets = new ArrayList<>();
        for (Lesson l : schedule.getLessons()) {
            if (sliceFilter == null || sliceFilter.test(l)) targets.add(l);
        }

        // MRV ordering: fewest VRP-feasible slots first (most-constrained first); pinned/pseudo last.
        // getAvailableTimeslots() is placement-independent and cached on the entity, so safe to call
        // up front for the ordering key.
        targets.sort(Comparator.comparingInt(HumanFaithfulConstruction::mrvKey));

        // Placement + bounded chronological backtrack.
        List<Placement> stack = new ArrayList<>();
        Map<Lesson, Set<Timeslot>> exhausted = new HashMap<>(); // slots already tried & undone for a lesson
        int idx = 0;
        while (idx < targets.size()) {
            Lesson lesson = targets.get(idx);
            Timeslot slot = s.pickFeasibleSlot(lesson, exhausted.get(lesson));
            if (slot != null) {
                lesson.timeslot = slot;
                s.track(lesson, slot);
                stack.add(new Placement(lesson, slot));
                s.placed++;
                idx++;
            } else if (s.canBacktrack()) {
                // Undo recent placements (bounded by D per dead-end, U total) and retry from the undone lesson.
                Placement undone = s.undoOne(stack);
                if (undone != null) {
                    exhausted.computeIfAbsent(undone.lesson, k -> new HashSet<>()).add(undone.slot);
                    // Re-queue the undone lesson: rewind idx to it so it picks its NEXT slot this pass.
                    int undoneIdx = targets.indexOf(undone.lesson);
                    if (undoneIdx >= 0 && undoneIdx < idx) idx = undoneIdx;
                    s.backtrackCount++;
                    s.maxDepthReached = Math.max(s.maxDepthReached, stack.size());
                } else {
                    // Nothing left to undo: box this lesson and move on.
                    lesson.timeslot = null;
                    s.boxed++;
                    idx++;
                }
            } else {
                // Backtrack budget exhausted: box this lesson (timeslot=null), move on.
                lesson.timeslot = null;
                s.boxed++;
                idx++;
            }
        }
        s.timeMs = System.currentTimeMillis() - start;
        return new Result(s.placed, s.boxed, s.backtrackCount, s.maxDepthReached, s.timeMs,
                maxUndoDepth, maxTotalUndos);
    }

    /** MRV key: pseudo/pinned lessons sort last (Integer.MAX_VALUE); else #VRP-feasible slots. */
    private static int mrvKey(Lesson l) {
        Teacher t = l.getTeacher();
        if (t != null && t.pseudo) return Integer.MAX_VALUE;
        if (l.pinned) return Integer.MAX_VALUE - 1;
        List<Timeslot> avail = l.getAvailableTimeslots();
        int n = avail == null ? Integer.MAX_VALUE - 2 : avail.size();
        return n;
    }

    /** Per-solve mutable state (the in-memory feasibility trackers, replicated from the construction phase). */
    static final class State {
        private final Map<Long, Set<Timeslot>> teacherSlots = new HashMap<>();
        private final Map<Long, Set<Timeslot>> groupSlots = new HashMap<>();
        private final Map<Long, Map<LocalDate, Integer>> groupDaily = new HashMap<>();
        private final Map<Long, Map<LocalDate, Integer>> teacherDaily = new HashMap<>();
        private final int maxUndoDepth;
        private final int maxTotalUndos;
        int placed = 0, boxed = 0, backtrackCount = 0, maxDepthReached = 0;
        long timeMs = 0;

        State(int d, int u) {
            this.maxUndoDepth = d;
            this.maxTotalUndos = u;
        }

        /** First hard-feasible slot for {@code l} (VRP gate + H1/H3 + H7/H11), excluding {@code tried}. */
        Timeslot pickFeasibleSlot(Lesson l, Set<Timeslot> tried) {
            List<Timeslot> avail = l.getAvailableTimeslots();
            if (avail == null) return null;
            for (Timeslot ts : avail) {
                if (tried != null && tried.contains(ts)) continue;
                if (canPlace(l, ts) && !exceedsDailyCap(l, ts)) return ts;
            }
            return null;
        }

        /** H1 (teacher) + H3 (group) conflict check via in-memory sets. (H2 rooms out of scope.) */
        boolean canPlace(Lesson l, Timeslot ts) {
            Teacher t = l.getTeacher();
            if (t != null && !t.pseudo) {
                Set<Timeslot> occ = teacherSlots.get(t.id);
                if (occ != null && occ.contains(ts)) return false;
            }
            StudentGroup g = l.getStudentGroup();
            if (g != null) {
                Set<Timeslot> occ = groupSlots.get(g.id);
                if (occ != null && occ.contains(ts)) return false;
            }
            return true;
        }

        /** H11 (group maxHoursPerDay) + H7 (teacher effective max daily hours). Mirrors exceedsDailyCap. */
        boolean exceedsDailyCap(Lesson l, Timeslot ts) {
            StudentGroup g = l.getStudentGroup();
            if (g != null && g.maxHoursPerDay != null && g.maxHoursPerDay > 0
                    && daily(groupDaily, g.id, ts.getDate()) >= g.maxHoursPerDay) {
                return true;
            }
            Teacher t = l.getTeacher();
            if (t != null && !t.pseudo) {
                int cap = t.getEffectiveMaxDailyHours();
                if (cap > 0 && daily(teacherDaily, t.id, ts.getDate()) >= cap) return true;
            }
            return false;
        }

        void track(Lesson l, Timeslot ts) {
            Teacher t = l.getTeacher();
            if (t != null && !t.pseudo) {
                teacherSlots.computeIfAbsent(t.id, k -> new HashSet<>()).add(ts);
                teacherDaily.computeIfAbsent(t.id, k -> new HashMap<>()).merge(ts.getDate(), 1, Integer::sum);
            }
            StudentGroup g = l.getStudentGroup();
            if (g != null) {
                groupSlots.computeIfAbsent(g.id, k -> new HashSet<>()).add(ts);
                groupDaily.computeIfAbsent(g.id, k -> new HashMap<>()).merge(ts.getDate(), 1, Integer::sum);
            }
        }

        void untrack(Lesson l, Timeslot ts) {
            Teacher t = l.getTeacher();
            if (t != null && !t.pseudo) {
                Set<Timeslot> occ = teacherSlots.get(t.id);
                if (occ != null) occ.remove(ts);
                Map<LocalDate, Integer> td = teacherDaily.get(t.id);
                if (td != null) td.merge(ts.getDate(), -1, Integer::sum);
            }
            StudentGroup g = l.getStudentGroup();
            if (g != null) {
                Set<Timeslot> occ = groupSlots.get(g.id);
                if (occ != null) occ.remove(ts);
                Map<LocalDate, Integer> gd = groupDaily.get(g.id);
                if (gd != null) gd.merge(ts.getDate(), -1, Integer::sum);
            }
        }

        boolean canBacktrack() {
            return maxUndoDepth > 0 && backtrackCount < maxTotalUndos;
        }

        Placement undoOne(List<Placement> stack) {
            // Undo up to maxUndoDepth recent placements in one dead-end escape (here: one at a time,
            // bounded by U total via canBacktrack). Pop the most recent.
            if (stack.isEmpty()) return null;
            Placement p = stack.remove(stack.size() - 1);
            untrack(p.lesson, p.slot);
            p.lesson.timeslot = null;
            placed--;
            return p;
        }

        private static int daily(Map<Long, Map<LocalDate, Integer>> map, Long id, LocalDate date) {
            Map<LocalDate, Integer> d = map.get(id);
            return d == null ? 0 : d.getOrDefault(date, 0);
        }
    }

    private static final class Placement {
        final Lesson lesson;
        final Timeslot slot;
        Placement(Lesson lesson, Timeslot slot) {
            this.lesson = lesson;
            this.slot = slot;
        }
    }

    /** Outcome of one solve: placement stats + the D/U used. */
    public static final class Result {
        public final int placed;
        public final int boxed;
        public final int backtrackCount;
        public final int maxDepthReached;
        public final long timeMs;
        public final int maxUndoDepth;
        public final int maxTotalUndos;

        public Result(int placed, int boxed, int backtrackCount, int maxDepthReached, long timeMs,
                int maxUndoDepth, int maxTotalUndos) {
            this.placed = placed;
            this.boxed = boxed;
            this.backtrackCount = backtrackCount;
            this.maxDepthReached = maxDepthReached;
            this.timeMs = timeMs;
            this.maxUndoDepth = maxUndoDepth;
            this.maxTotalUndos = maxTotalUndos;
        }

        @Override
        public String toString() {
            return String.format(
                    "placed=%d boxed=%d backtracks=%d maxDepth=%d time=%dms (D=%d U=%d)",
                    placed, boxed, backtrackCount, maxDepthReached, timeMs, maxUndoDepth, maxTotalUndos);
        }
    }
}
