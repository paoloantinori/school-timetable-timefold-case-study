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
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-463 (ADOPTED, flag default ON since the 2026-08-01 ratification): deterministic cross-group teacher-day coordinator that
 * closes a lab-day H17 gap left by the staged cycle. Sibling of {@link HoleRepair} and {@link
 * AnchorRepair}, running post-HoleRepair / pre-AnchorRepair in the staged cycle.
 *
 * <p><b>The verified root cause (run1 lessons.txt, 2026-07-31).</b> The 18 H17 gaps are one
 * pattern: G13 Friday, slot 11, 5h meccanica lab. The gap is NOT a same-day split a local move
 * can close. It exists because the lab day's anchor teacher (T10, plurality at 110h vs
 * T15 30h) is SATURATED at the gap slot by ANOTHER group's lesson (G04 Tecnologia
 * Meccanica@11). Both meccanica teachers are claimed by FC groups at slot 11, so no G13 lab
 * lesson can fill it without an H1 conflict. The golden solves this by coordinating T10's
 * Friday across groups; the run does not. (G13 Mon-Thu has a contiguous block with no gap: only
 * Friday splits, because that is where the cross-group teacher scarcity bites.)
 *
 * <p><b>Teacher is not a planning variable.</b> It is fixed per {@link com.school.planner.domain
 * .Course}. "Consolidate the lab day onto the anchor" therefore cannot be a same-day teacher
 * reassignment: it is a CROSS-GROUP, CROSS-DATE re-coordination. For each penalized lab-day gap:
 * <ol>
 * <li><b>Anchor</b> = the plurality lab teacher of the day (T10), read off the run state, no
 *     golden read (the 110h-vs-30h plurality is owned by incarichi input).</li>
 * <li><b>Donor</b> = the OTHER group's lesson that occupies the anchor at the gap slot (G04
 *     Tecnologia Meccanica@11), found in the shared occupancy index.</li>
 * <li><b>Relocate the donor</b> to a legal slot (conflict-free, {@link BoxRepair#passesPlacementGates},
 *     H17-safe departure), refusing it if the move would DE-ANCHOR the donor's own day
 *     ({@link SurplusRebalance#keepsDayAnchored}, the M18 anti-shell-game guard, mirror of
 *     AnchorRepair): closing G13's gap must not cost G04 a late start.</li>
 * <li><b>Import an anchor-course lesson</b> into the freed gap slot via {@link
 *     HoleRepair#findImport} over the anchor-teacher pool, committed through {@link
 *     HoleRepair#commitCrossDayMove} so all five live indexes stay honest.</li>
 * </ol>
 * The donor relocation and the anchor import are a TWO-MOVE TRANSACTION: if the import has no
 * legal donor lesson, the donor relocation is REVERTED, so the pass never moves a lesson without
 * closing the gap it targeted.
 *
 * <p><b>Improve-or-noop by construction.</b> Every committed move passes the shared gate chain
 * (H14 + H11 + H7 + H17-safe arrival, H17-safe departure, M18 anchor, VRP membership), the donor
 * pool excludes pinned lessons, and the runner reverts the pass if it ever worsens the
 * timeslot-tier hard ({@code ScheduleService.ensureImproveOrNoop}). If no donor has a legal relocation the pass is a
 * clean no-op and the gaps persist: REPORTED honestly, not papered over. The feasibility
 * measurement (legal donor relocations exist?) is precisely what the prototype exists to answer.
 *
 * <p><b>Generic scope, not mono-case.</b> The mechanism is written on {@code (lab tag, anchor
 * teacher, day)}: it is the repair-time expression of the spec's teacher-centric {@code (docente,
 * giorno)} joint scarcity, sibling of the room-tag scarcity of {@code M-TC}/TASK-449. G13-Friday
 * is the driver and the measure, not a hardcoded case.
 *
 * <p><b>Flag default ON, opt-out.</b> {@link SchoolSchedule#isLabDayConsolidationEnabled()}
 * (read LIVE per build, {@code planner.construction.lab-day-consolidation.enabled}, default true
 * since the 2026-08-01 ratification) is checked as the first line of {@link #repair}: opted out,
 * the pass returns a zero Result and mutates nothing (byte-identical).
 */
public final class LabDayConsolidation {

    /** A lab day is a day with at least this many lab lessons (the G13 Friday has 5). */
    static final int MIN_LAB_LESSONS_FOR_LAB_DAY = 4;

    /** Safety belt on committed transactions per pass. */
    static final int MAX_MOVES_PER_PASS = 200;

    private static final Logger LOG = Logger.getLogger(LabDayConsolidation.class);

    private LabDayConsolidation() {
    }

    /**
     * {@code labGapsBefore/After} = penalized H17 gap slots in lab days (days with at least
     * {@link #MIN_LAB_LESSONS_FOR_LAB_DAY} lab lessons); {@link #gapsClosed()} = before minus after
     * (derived, not stored, mirroring {@link HoleRepair.Result#closed()}); {@code donorsRelocated}
     * = committed transactions (each relocates one donor AND imports one anchor lesson, so the two
     * halves are one count); {@code donorsSkippedShellGame} = donors refused by the M18 anti-shell-
     * game guard (would de-anchor the donor group); {@code donorsSkippedNoMove} = donors with no
     * legal relocation, or whose anchor import had no legal lesson (reverted); {@code conflictsAdded}
     * must be 0.
     */
    public record Result(int labGapsBefore, int labGapsAfter,
                         int donorsRelocated, int donorsSkippedShellGame, int donorsSkippedNoMove,
                         long conflictsAdded) {

        /** Lab-day gaps closed this pass (before minus after), derived like {@link HoleRepair.Result#closed()}. */
        public int gapsClosed() {
            return labGapsBefore - labGapsAfter;
        }
    }

    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                Consumer<Lesson> nullRoom) {
        if (!SchoolSchedule.isLabDayConsolidationEnabled()) {
            return new Result(0, 0, 0, 0, 0, 0);
        }
        if (schedule.getTimeslots() == null) {
            return new Result(0, 0, 0, 0, 0, 0);
        }
        // Post-smarcatura only, exactly like HoleRepair/AnchorRepair: the donor pool reads the raw
        // pinned flag, which equals authoritative-pin only once every promoted pin has been released.
        if (!SchoolSchedule.getPromotedLessonIdsStatic().isEmpty()) {
            throw new IllegalStateException("LabDayConsolidation.repair called while " +
                    SchoolSchedule.getPromotedLessonIdsStatic().size()
                    + " promoted pin(s) are held - this pass is designed for the post-release grid only");
        }

        // Live indexes, built once and kept honest through every commit (commitCrossDayMove updates
        // byTs/groupDaySlots/teacherDaily/dayLessons in place).
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(schedule.getLessons());
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = BoxRepair.buildGroupDaySlots(schedule.getLessons());
        Map<String, Integer> teacherDaily = BoxRepair.buildTeacherDaily(schedule.getLessons());
        Map<String, TreeMap<Integer, Lesson>> dayLessons = HoleRepair.buildDayLessons(schedule.getLessons());
        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot =
                StudentDayCompaction.buildTimeslotsByDateAndSlot(schedule.getTimeslots());
        Map<Long, List<Lesson>> candidatesByGroup = HoleRepair.buildCandidatesByGroup(schedule.getLessons());

        List<LabGap> gaps = collectLabDayGaps(dayLessons);
        int before = gaps.size();
        if (before == 0) {
            return new Result(0, 0, 0, 0, 0, 0);
        }
        // Deterministic order (group, date, slot) so the pass is reproducible across re-seeded profiles.
        gaps.sort(Comparator
                .comparing((LabGap g) -> g.group.name)
                .thenComparing(g -> g.date)
                .thenComparing(g -> g.slot));

        long conflictsBefore = StudentDayCompaction.countConflicts(schedule);
        int donorsRelocated = 0;
        int donorsSkippedShellGame = 0;
        int donorsSkippedNoMove = 0;

        for (LabGap gap : gaps) {
            if (donorsRelocated >= MAX_MOVES_PER_PASS) {
                LOG.warnf("LabDayConsolidation hit MAX_MOVES_PER_PASS (%d); remaining lab-day gaps left untouched",
                        MAX_MOVES_PER_PASS);
                break;
            }
            TreeMap<Integer, Lesson> bySlot = dayLessons.get(gap.group.id + "@" + gap.date);
            Teacher anchor = anchorTeacherOfDay(bySlot);
            if (anchor == null) {
                continue; // no lab teacher to consolidate onto: not our pattern
            }
            Timeslot gapTs = tsByDateSlot.getOrDefault(gap.date, Map.of()).get(gap.slot);
            if (gapTs == null) {
                continue; // off-grid gap slot: structurally unfillable
            }
            Lesson donor = findDonor(byTs, gapTs, anchor, gap.group);
            if (donor == null) {
                continue; // no cross-group lesson blocks the anchor here: HoleRepair's territory, not ours
            }
            // Anti-shell-game first (target-independent): refuse a donor whose removal de-anchors its day.
            if (!SurplusRebalance.keepsDayAnchored(donor, groupDaySlots)) {
                donorsSkippedShellGame++;
                continue;
            }
            if (!BoxRepair.isDepartureSafe(donor, groupDaySlots)) {
                donorsSkippedNoMove++;
                continue; // the donor's departure would open a new penalized gap at its origin
            }
            Timeslot dest = findRelocationSlot(donor, groupDaySlots, teacherDaily, byTs);
            if (dest == null) {
                donorsSkippedNoMove++;
                continue; // the anchor is blocked but the blocker has no legal relocation: leave it
            }

            // TWO-MOVE TRANSACTION: relocate the donor (free the anchor at the gap slot), then import
            // an anchor-course lesson into it. If the import has no legal lesson, REVERT the donor so
            // the pass never moves a lesson without closing the gap it targeted.
            Timeslot donorOrigin = donor.timeslot;
            HoleRepair.commitCrossDayMove(donor, dest, byTs, groupDaySlots, teacherDaily, dayLessons,
                    moveTimeslot, nullRoom);
            List<Lesson> anchorPool = anchorCandidates(candidatesByGroup.getOrDefault(gap.group.id, List.of()), anchor);
            Lesson pick = findAnchorImport(anchorPool, gapTs, gap.date, byTs, groupDaySlots, teacherDaily);
            if (pick == null) {
                HoleRepair.commitCrossDayMove(donor, donorOrigin, byTs, groupDaySlots, teacherDaily, dayLessons,
                        moveTimeslot, nullRoom); // revert: the relocation was 0-hard, but it closed nothing
                donorsSkippedNoMove++;
                continue;
            }
            HoleRepair.commitCrossDayMove(pick, gapTs, byTs, groupDaySlots, teacherDaily, dayLessons,
                    moveTimeslot, nullRoom);
            donorsRelocated++;
        }

        int after = countLabDayGaps(dayLessons);
        long conflictsAdded = StudentDayCompaction.countConflicts(schedule) - conflictsBefore;
        LOG.infof("LabDayConsolidation (TASK-463 prototype): lab-day gaps %d->%d (closed %d); "
                        + "donors relocated %d, skipped shell-game %d / no-move %d, conflicts added %d",
                before, after, before - after, donorsRelocated,
                donorsSkippedShellGame, donorsSkippedNoMove, conflictsAdded);
        return new Result(before, after, donorsRelocated, donorsSkippedShellGame,
                donorsSkippedNoMove, conflictsAdded);
    }

    /**
     * The penalized H17 gap slots of every lab day (a day with at least {@link
     * #MIN_LAB_LESSONS_FOR_LAB_DAY} lab lessons), in day-view iteration order. Each caller sorts
     * the result into the order its enumeration needs.
     */
    private static List<LabGap> collectLabDayGaps(Map<String, TreeMap<Integer, Lesson>> dayLessons) {
        List<LabGap> gaps = new ArrayList<>();
        for (Map.Entry<String, TreeMap<Integer, Lesson>> entry : dayLessons.entrySet()) {
            TreeMap<Integer, Lesson> bySlot = entry.getValue();
            if (!isLabDay(bySlot)) continue;
            Lesson any = bySlot.firstEntry().getValue();
            StudentGroup group = any.getStudentGroup();
            if (group == null) continue;
            LocalDate date = any.timeslot.getDate();
            for (int slot : HoleRepair.penalizedHoleSlots(bySlot)) {
                gaps.add(new LabGap(group, date, slot));
            }
        }
        return gaps;
    }

    /** Total penalized lab-day gap slots on the (possibly live) day view, the honest "after" number. */
    private static int countLabDayGaps(Map<String, TreeMap<Integer, Lesson>> dayLessons) {
        int total = 0;
        for (TreeMap<Integer, Lesson> bySlot : dayLessons.values()) {
            if (!isLabDay(bySlot)) continue;
            total += HoleRepair.penalizedHoleSlots(bySlot).size();
        }
        return total;
    }

    /**
     * A day qualifies as a lab day for this pass iff it has at least {@link
     * #MIN_LAB_LESSONS_FOR_LAB_DAY} lab lessons. One predicate shared by the before-snapshot
     * ({@link #collectLabDayGaps}) and the after-tally ({@link #countLabDayGaps}) so the two can
     * never drift on what a lab day is.
     */
    private static boolean isLabDay(TreeMap<Integer, Lesson> bySlot) {
        return bySlot != null && !bySlot.isEmpty() && labLessonCount(bySlot) >= MIN_LAB_LESSONS_FOR_LAB_DAY;
    }

    /** Number of lab lessons ({@link Lesson#isLab()}) in a day view. */
    private static int labLessonCount(TreeMap<Integer, Lesson> bySlot) {
        int n = 0;
        for (Lesson l : bySlot.values()) {
            if (l.isLab()) n++;
        }
        return n;
    }

    /**
     * The plurality lab teacher of the day (the one to consolidate onto). Ties broken by teacher id
     * for determinism. Read off the run state, so it tracks input (incarichi plurality) without a
     * golden read.
     */
    private static Teacher anchorTeacherOfDay(TreeMap<Integer, Lesson> bySlot) {
        if (bySlot == null || bySlot.isEmpty()) return null;
        Map<Long, int[]> counts = new HashMap<>();
        Map<Long, Teacher> teacherById = new HashMap<>();
        for (Lesson l : bySlot.values()) {
            if (!l.isLab()) continue;
            Teacher t = l.getTeacher();
            if (t == null || t.pseudo || t.id == null) continue;
            counts.computeIfAbsent(t.id, k -> new int[]{0})[0]++;
            teacherById.put(t.id, t);
        }
        Teacher best = null;
        int bestCount = 0;
        long bestId = Long.MAX_VALUE;
        for (Map.Entry<Long, int[]> e : counts.entrySet()) {
            int c = e.getValue()[0];
            if (c > bestCount || (c == bestCount && e.getKey() < bestId)) {
                bestCount = c;
                bestId = e.getKey();
                best = teacherById.get(e.getKey());
            }
        }
        return best;
    }

    /**
     * The cross-group lesson that occupies {@code anchor} at the gap slot: the donor whose
     * relocation frees the anchor there. Pinned lessons are immovable and skipped (post-smarcatura,
     * pinned == authoritative).
     */
    private static Lesson findDonor(Map<Timeslot, List<Lesson>> byTs, Timeslot gapTs, Teacher anchor,
                                    StudentGroup target) {
        List<Lesson> atSlot = byTs.get(gapTs);
        if (atSlot == null) return null;
        for (Lesson l : atSlot) {
            if (l.pinned) continue;
            Teacher t = l.getTeacher();
            if (t == null || t.id == null || !t.id.equals(anchor.id)) continue;
            StudentGroup g = l.getStudentGroup();
            if (g == null || g.id == null || g.id.equals(target.id)) continue;
            return l;
        }
        return null;
    }

    /**
     * The first legal relocation slot for {@code donor}: conflict-free (H1/H3), {@link
     * BoxRepair#passesPlacementGates} (H14 + H11 + H7 + H17-safe arrival), in the donor's VRP
     * value range. The M18 anchor and H17-safe departure are target-independent and checked by the
     * caller first. First-legal in value-range order is deterministic.
     */
    private static Timeslot findRelocationSlot(Lesson donor,
                                               Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                               Map<String, Integer> teacherDaily,
                                               Map<Timeslot, List<Lesson>> byTs) {
        // TASK-578: FRESH range, never the cached one (stale-cache crash class)
        List<Timeslot> range = donor.getAvailableTimeslotsFresh();
        for (Timeslot target : range) {
            if (target == null || target.equals(donor.timeslot)) continue;
            if (!StudentDayCompaction.isSlotConflictFree(donor, target, byTs)) continue;
            if (!BoxRepair.passesPlacementGates(donor, target, groupDaySlots, teacherDaily)) continue;
            return target;
        }
        return null;
    }

    /**
     * The anchor-course import: off-template (free, no M32) before on-template (M32-charged), the
     * same two-level order {@link AnchorRepair} and {@link HoleRepair} apply. An anchor lesson
     * typically sits on its template cell on the real seed, so trying the on-template level too is
     * what actually closes the gap (a free-only import would leave it on the table). The whole gate
     * chain lives in {@link HoleRepair#findImport}; only the pool is narrowed to the anchor
     * teacher's own course ({@link #anchorCandidates}).
     */
    private static Lesson findAnchorImport(List<Lesson> pool, Timeslot target, LocalDate holeDate,
                                           Map<Timeslot, List<Lesson>> byTs,
                                           Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                           Map<String, Integer> teacherDaily) {
        for (boolean onTemplate : new boolean[] { false, true }) {
            Lesson pick = HoleRepair.findImport(pool, target, holeDate, onTemplate, byTs, groupDaySlots,
                    teacherDaily, false);
            if (pick != null) return pick;
        }
        return null;
    }

    /** The group's candidate pool narrowed to the anchor teacher's own course lessons. */
    private static List<Lesson> anchorCandidates(List<Lesson> pool, Teacher anchor) {
        List<Lesson> filtered = new ArrayList<>();
        for (Lesson l : pool) {
            Teacher t = l.getTeacher();
            if (t != null && t.id != null && t.id.equals(anchor.id)) filtered.add(l);
        }
        return filtered;
    }

    private record LabGap(StudentGroup group, LocalDate date, int slot) {
    }
}
