package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
 * TASK-481 (Opzione A, ratified Fedora 2026-08-02): deterministic composition-aware H17 hole-closer
 * that generalizes {@link LabDayConsolidation}'s cross-group cascade to CLASSROOM (non-lab) days.
 * Sibling of {@link HoleRepair}/{@link LabDayConsolidation}/{@link AnchorRepair}, running
 * post-LabDayConsolidation / pre-AnchorRepair in the staged cycle.
 *
 * <p><b>Why this pass exists (the verified G11 @10 bad-seed gap).</b> The residual H17 holes on
 * the {@code source=input} bad seed are one pattern: G11 Wednesday @10, a CLASSROOM day
 * (Matematica / EduCiv / Storia / Informatica), where every G11 classroom teacher is saturated
 * @10 by ANOTHER group's lesson. {@link HoleRepair} imports same-group only and finds no donor whose
 * teacher is free @10 (none is); {@link StudentDayCompaction} slides the max lesson down
 * (T06@13, saturated @10, breaks); {@link LabDayConsolidation} skips the day entirely (its
 * scope is LAB days, {@code >= 4 lab lessons}, and this day has none). The hole is the
 * classroom-day twin of the G13-Friday lab-day gap TASK-463 closes, with a classroom teacher
 * (T03) in the anchor role T10 holds for G13.
 *
 * <p><b>The mechanism = LabDayConsolidation's cascade, generalized.</b> For each penalized H17 gap
 * left after HoleRepair + LabDayConsolidation:
 * <ol>
 * <li><b>Blocker</b> = a cross-group, unpinned lesson occupying the gap slot whose teacher also
 *     teaches the gap group (it is what saturates that teacher at the gap slot).</li>
 * <li><b>Relocate the blocker</b> to a legal cell (conflict-free, {@link BoxRepair#passesPlacementGates},
 *     H17-safe departure, M18 anti-shell-game via {@link SurplusRebalance#keepsDayAnchored}).</li>
 * <li><b>Import</b> a gap-group lesson of the SAME teacher into the freed gap slot via {@link
 *     HoleRepair#findImport}: its own H1 gate admits only that teacher's lessons (every other
 *     gap-group teacher is still saturated @gap), so the teacher selection is implicit, no anchor
 *     preselect needed.</li>
 * </ol>
 * A TWO-MOVE TRANSACTION with revert: if the import has no legal donor, the blocker relocation is
 * reverted, so the pass never moves a lesson without closing the gap it targeted (mirrors
 * LabDayConsolidation).
 *
 * <p><b>Existence gate REFUTED offline on the known bad seed (the cascade is a shell game).</b> On
 * the G11 @10 bad seed every @10 blocker is mid-run on its own day (the day is fully packed
 * [0..5]), so relocating it OPENS a gap there and the @10 hole merely relocates groups; the net H17
 * gain the cascade shows comes from the blocker's destination filling an UNRELATED gap elsewhere,
 * not a sustainable close. {@code H17HoleCloseDiscriminatorTest#depth3CrossGroupCascade} proves this:
 * SUSTAINABLE closes (an edge blocker whose departure opens no gap) = 0/6; SHELL-GAME closes (mid-run
 * blocker) = 6/6. Accordingly the live pass is CORRECTLY INERT on that bad seed: the
 * {@code isDepartureSafe} gate refuses every mid-run blocker, and {@code compositionHoleCloserClosesBadSeed}
 * pins 0 cascades / 0 conflicts / H17 unchanged. This class is a verified-correct SCAFFOLD: it closes
 * any classroom-day H17 gap whose blocker IS edge-removable, and refuses (does no harm) otherwise.
 * The @10 saturated-basin cure forks to Fedora (multi-start / construction-side). Doc:
 * {@code task481-results/FORK-shell-game-depth3-2026-08-02.md}.
 *
 * <p><b>Bounded search, EF-style.</b> A candidate-evaluation budget caps the total cell evaluations
 * across the pass ({@link #getMaxCandidates()} knob, {@code -Dplanner.composition-hole-closer.max-candidates},
 * default {@link #DEFAULT_MAX_CANDIDATES}); a cap hit is logged at WARN, never silent. {@link
 * #MAX_MOVES_PER_PASS} bounds committed transactions.
 *
 * <p><b>Improve-or-noop by construction.</b> Every committed move passes the shared gate chain; the
 * runner reverts the whole pass if it ever worsens the timeslot-tier hard ({@code
 * ScheduleService#ensureImproveOrNoop}). {@code conflictsAdded} must be 0.
 *
 * <p><b>Flag RATIFIED DEFAULT ON (maintainer, 16/09, endgame bundle).</b> {@link SchoolSchedule#isCompositionHoleCloserEnabled()}
 * ({@code planner.composition-hole-closer.enabled}): standard opt-OUT form (null/absent -> ON, only
 * the literal "false" disarms), like its lab-day sibling. The re-A/B of the cured bundle measured
 * A = B = [-1/0] with the cascade armed through the whole multi-start. Read LIVE per repair.
 */
public final class CompositionHoleCloser {

    /**
     * Default candidate-evaluation budget for the early-abort cap (EF-style calibration knob). The
     * dominant cost is {@link #findRelocationSlot}'s per-blocker scan of the blocker's VRP value
     * range (up to ~1,368 cells), so a generous default keeps the cap from firing prematurely on a
     * pathological grid; the cap is a safety belt, never the expected limit.
     */
    public static final int DEFAULT_MAX_CANDIDATES = 1000000;

    /** Safety belt on committed transactions per pass. */
    static final int MAX_MOVES_PER_PASS = 200;

    private static final Logger LOG = Logger.getLogger(CompositionHoleCloser.class);

    private CompositionHoleCloser() {
    }

    /**
     * The live candidate-evaluation budget. System property
     * {@code planner.composition-hole-closer.max-candidates}, default {@link #DEFAULT_MAX_CANDIDATES}:
     * the calibration knob for the bounded cascade search, read LIVE at each {@link #repair}
     * invocation (repair-pass discipline, like {@link SchoolSchedule#isCompositionHoleCloserEnabled()}).
     * Floored at 1; a blank or malformed value falls back to the default.
     */
    public static int getMaxCandidates() {
        String raw = System.getProperty("planner.composition-hole-closer.max-candidates");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_CANDIDATES;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_CANDIDATES;
        }
    }

    /**
     * {@code gapsBefore/After} = penalized H17 gap slots on the (group, date) day view;
     * {@code cascadesCommitted} = two-move transactions (each relocates one blocker AND imports one
     * gap-group lesson); the four {@code skipped*} counters break down the blockers the pass refused,
     * in the order the gates fire ({@code skippedShellGame} = M18 anti-shell-game, {@code
     * skippedDepartureUnsafe} = H17 departure, {@code skippedNoRelocation} = no legal relocation cell,
     * {@code skippedNoImport} = relocation succeeded but no same-teacher gap-group import, reverted);
     * {@code conflictsAdded} must be 0.
     */
    public record Result(int gapsBefore, int gapsAfter, int cascadesCommitted,
                         int skippedShellGame, int skippedDepartureUnsafe, int skippedNoRelocation,
                         int skippedNoImport, long conflictsAdded) {

        /** Penalized gaps closed this pass (before minus after). */
        public int gapsClosed() {
            return gapsBefore - gapsAfter;
        }
    }

    /**
     * Deterministic composition-aware hole closing: close as many penalized H17 gaps as possible via
     * the cross-group cascade, committing moves only through the two shared callbacks (never touching
     * a ScoreDirector).
     */
    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                Consumer<Lesson> nullRoom) {
        if (!SchoolSchedule.isCompositionHoleCloserEnabled()) {
            return new Result(0, 0, 0, 0, 0, 0, 0, 0);
        }
        if (schedule.getTimeslots() == null) {
            return new Result(0, 0, 0, 0, 0, 0, 0, 0);
        }
        // Post-smarcatura only, exactly like HoleRepair/LabDayConsolidation: the blocker pool reads
        // the raw pinned flag, which equals authoritative-pin only once every promoted pin is released.
        if (!SchoolSchedule.getPromotedLessonIdsStatic().isEmpty()) {
            throw new IllegalStateException("CompositionHoleCloser.repair called while "
                    + SchoolSchedule.getPromotedLessonIdsStatic().size()
                    + " promoted pin(s) are held - this pass is designed for the post-release grid only");
        }

        // Live indexes, built once and kept honest through every commit (commitCrossDayMove updates
        // byTs/groupDaySlots/teacherDaily/dayLessons in place). Same five indexes as LabDayConsolidation.
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(schedule.getLessons());
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = BoxRepair.buildGroupDaySlots(schedule.getLessons());
        Map<String, Integer> teacherDaily = BoxRepair.buildTeacherDaily(schedule.getLessons());
        Map<String, TreeMap<Integer, Lesson>> dayLessons = HoleRepair.buildDayLessons(schedule.getLessons());
        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot =
                StudentDayCompaction.buildTimeslotsByDateAndSlot(schedule.getTimeslots());
        Map<Long, List<Lesson>> candidatesByGroup = HoleRepair.buildCandidatesByGroup(schedule.getLessons());

        List<Gap> gaps = collectPenalizedGaps(dayLessons);
        int before = gaps.size();
        if (before == 0) {
            return new Result(0, 0, 0, 0, 0, 0, 0, 0);
        }
        // Deterministic order (group, date, slot) so the pass is reproducible across re-seeded profiles.
        gaps.sort(Comparator
                .comparing((Gap g) -> g.group.name)
                .thenComparing(g -> g.date)
                .thenComparing(g -> g.slot));

        long conflictsBefore = StudentDayCompaction.countConflicts(schedule);
        CandidateBudget budget = new CandidateBudget(getMaxCandidates());
        int cascadesCommitted = 0;
        int skippedShellGame = 0;
        int skippedDepartureUnsafe = 0;
        int skippedNoRelocation = 0;
        int skippedNoImport = 0;
        boolean capped = false;

        gapLoop:
        for (Gap gap : gaps) {
            if (cascadesCommitted >= MAX_MOVES_PER_PASS) {
                LOG.warnf("CompositionHoleCloser hit MAX_MOVES_PER_PASS (%d); remaining gap(s) left untouched",
                        MAX_MOVES_PER_PASS);
                break;
            }
            if (budget.exhausted()) {
                capped = true;
                break;
            }
            Timeslot gapTs = tsByDateSlot.getOrDefault(gap.date, Map.of()).get(gap.slot);
            if (gapTs == null) continue; // off-grid gap slot: structurally unfillable
            // Each cross-group occupant of the gap slot is a candidate blocker. The day view is live,
            // so a gap an earlier commit filled no longer resolves to a free slot's blockers in a way
            // that can close it (the filler is same-group, never a blocker), and the loop no-ops it.
            List<Lesson> blockers = crossGroupBlockers(byTs, gapTs, gap.group, budget);
            for (Lesson blocker : blockers) {
                if (budget.exhausted()) {
                    capped = true;
                    break gapLoop;
                }
                // Target-independent guards first (mirror LabDayConsolidation): refuse a blocker whose
                // removal de-anchors its own day (M18 shell game) or opens a penalized H17 gap behind it.
                if (!SurplusRebalance.keepsDayAnchored(blocker, groupDaySlots)) {
                    skippedShellGame++;
                    continue;
                }
                if (!BoxRepair.isDepartureSafe(blocker, groupDaySlots)) {
                    skippedDepartureUnsafe++;
                    continue;
                }
                Timeslot dest = findRelocationSlot(blocker, groupDaySlots, teacherDaily, byTs, budget);
                if (dest == null) {
                    skippedNoRelocation++;
                    continue; // the blocker holds the teacher hostage but has no legal relocation
                }
                Teacher freedTeacher = blocker.getTeacher();
                List<Lesson> pool = poolForTeacher(candidatesByGroup.getOrDefault(gap.group.id, List.of()), freedTeacher);
                // TWO-MOVE TRANSACTION: relocate the blocker (free its teacher at the gap slot), then
                // import a same-teacher gap-group lesson. If no import exists, revert the blocker so the
                // pass never moves a lesson without closing the gap it targeted (mirrors LabDayConsolidation).
                Timeslot blockerOrigin = blocker.timeslot;
                HoleRepair.commitCrossDayMove(blocker, dest, byTs, groupDaySlots, teacherDaily, dayLessons,
                        moveTimeslot, nullRoom);
                Lesson pick = HoleRepair.findImport(pool, gapTs, gap.date, false, byTs, groupDaySlots, teacherDaily, false);
                if (pick == null) {
                    pick = HoleRepair.findImport(pool, gapTs, gap.date, true, byTs, groupDaySlots, teacherDaily, false);
                }
                if (pick == null) {
                    HoleRepair.commitCrossDayMove(blocker, blockerOrigin, byTs, groupDaySlots, teacherDaily, dayLessons,
                            moveTimeslot, nullRoom); // revert: closed nothing
                    skippedNoImport++;
                    continue;
                }
                HoleRepair.commitCrossDayMove(pick, gapTs, byTs, groupDaySlots, teacherDaily, dayLessons,
                        moveTimeslot, nullRoom);
                cascadesCommitted++;
                break; // gap closed; next gap
            }
        }

        int after = countPenalizedGaps(dayLessons);
        long conflictsAdded = StudentDayCompaction.countConflicts(schedule) - conflictsBefore;
        if (capped) {
            LOG.warnf("CompositionHoleCloser: candidate-evaluation budget of %d reached - pass aborted "
                            + "with %d penalized gap(s) still open. The budget IS a calibration knob "
                            + "(-Dplanner.composition-hole-closer.max-candidates, default %d).",
                    budget.limit(), after, DEFAULT_MAX_CANDIDATES);
        }
        LOG.infof("CompositionHoleCloser (TASK-481): penalized gaps %d->%d (closed %d); cascades %d, "
                        + "skipped shell-game %d / departure-unsafe %d / no-relocation %d / no-import %d, conflicts added %d",
                before, after, before - after, cascadesCommitted, skippedShellGame,
                skippedDepartureUnsafe, skippedNoRelocation, skippedNoImport, conflictsAdded);
        return new Result(before, after, cascadesCommitted, skippedShellGame, skippedDepartureUnsafe,
                skippedNoRelocation, skippedNoImport, conflictsAdded);
    }

    // ─── gap + blocker enumeration ─────────────────────────────────────────

    /**
     * All penalized H17 gap slots on the day view (no lab-day filter: this pass is the
     * classroom-day complement of {@link LabDayConsolidation}, and it runs after that pass over the
     * live grid, so lab-day gaps that pass already closed are gone from the view).
     */
    private static List<Gap> collectPenalizedGaps(Map<String, TreeMap<Integer, Lesson>> dayLessons) {
        List<Gap> gaps = new ArrayList<>();
        for (Map.Entry<String, TreeMap<Integer, Lesson>> entry : dayLessons.entrySet()) {
            TreeMap<Integer, Lesson> bySlot = entry.getValue();
            if (bySlot == null || bySlot.isEmpty()) continue;
            Lesson any = bySlot.firstEntry().getValue();
            StudentGroup group = any.getStudentGroup();
            if (group == null) continue;
            LocalDate date = any.timeslot.getDate();
            for (int slot : HoleRepair.penalizedHoleSlots(bySlot)) {
                gaps.add(new Gap(group, date, slot));
            }
        }
        return gaps;
    }

    /** Total penalized gap slots on the (possibly live) day view, the honest "after" number. */
    private static int countPenalizedGaps(Map<String, TreeMap<Integer, Lesson>> dayLessons) {
        int total = 0;
        for (TreeMap<Integer, Lesson> bySlot : dayLessons.values()) {
            if (bySlot == null) continue;
            total += HoleRepair.penalizedHoleSlots(bySlot).size();
        }
        return total;
    }

    /**
     * The unpinned, real-teacher, CROSS-GROUP lessons occupying {@code gapTs}: each is a blocker whose
     * relocation could free its teacher at the gap slot for a gap-group import. Stable order
     * (group name, teacher name, id) so the pass is reproducible. Each occupant examined consumes one
     * budget unit.
     */
    private static List<Lesson> crossGroupBlockers(Map<Timeslot, List<Lesson>> byTs, Timeslot gapTs,
                                                   StudentGroup gapGroup, CandidateBudget budget) {
        List<Lesson> at = byTs.get(gapTs);
        List<Lesson> blockers = new ArrayList<>();
        if (at == null) return blockers;
        for (Lesson l : at) {
            if (!budget.acquire()) return blockers;
            if (l.pinned) continue;
            Teacher t = l.getTeacher();
            if (t == null || t.pseudo) continue;
            StudentGroup g = l.getStudentGroup();
            if (g == null || gapGroup == null || g.id.equals(gapGroup.id)) continue; // same group: not a blocker
            blockers.add(l);
        }
        blockers.sort(Comparator
                .comparing((Lesson l) -> l.getStudentGroup() != null && l.getStudentGroup().name != null
                        ? l.getStudentGroup().name : "")
                .thenComparing(l -> l.getTeacher() != null && l.getTeacher().name != null ? l.getTeacher().name : "")
                .thenComparing(l -> l.id != null ? l.id : Long.MAX_VALUE));
        return blockers;
    }

    /** The gap group's candidate pool narrowed to {@code teacher}'s own course lessons (mirror LabDayConsolidation.anchorCandidates). */
    private static List<Lesson> poolForTeacher(List<Lesson> pool, Teacher teacher) {
        if (teacher == null || teacher.id == null) return List.of();
        List<Lesson> filtered = new ArrayList<>();
        for (Lesson l : pool) {
            Teacher t = l.getTeacher();
            if (t != null && t.id != null && t.id.equals(teacher.id)) filtered.add(l);
        }
        return filtered;
    }

    // ─── relocation (mirror LabDayConsolidation.findRelocationSlot) ──────────

    /**
     * The first legal relocation slot for {@code blocker}: conflict-free (H1/H3),
     * {@link BoxRepair#passesPlacementGates} (H14 + H11 + H7 + H17-safe arrival), in the blocker's VRP
     * value range. Each candidate examined consumes one budget unit. First-legal in value-range order
     * is deterministic.
     */
    private static Timeslot findRelocationSlot(Lesson blocker,
                                               Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                               Map<String, Integer> teacherDaily,
                                               Map<Timeslot, List<Lesson>> byTs, CandidateBudget budget) {
        List<Timeslot> range = blocker.getAvailableTimeslotsFresh();    // (TASK-578: FRESH, stale-cache crash class)
        for (Timeslot target : range) {
            if (!budget.acquire()) return null;
            if (target == null || target.equals(blocker.timeslot)) continue;
            if (!StudentDayCompaction.isSlotConflictFree(blocker, target, byTs)) continue;
            if (!BoxRepair.passesPlacementGates(blocker, target, groupDaySlots, teacherDaily)) continue;
            return target;
        }
        return null;
    }

    private record Gap(StudentGroup group, LocalDate date, int slot) {
    }

    /**
     * Mutable candidate-evaluation counter and its fixed limit, mirror {@code ExactFinisher.CandidateBudget}.
     * {@link #acquire()} is the inner-scan idiom (increment, abort the scan once the limit is exceeded);
     * {@link #exhausted()} is the outer-loop guard (do not start a new gap once the budget is spent).
     */
    private static final class CandidateBudget {
        private final int limit;
        private int count;

        CandidateBudget(int limit) {
            this.limit = Math.max(1, limit);
        }

        /** Count one evaluation; false once the limit is exceeded (the scan must abort). */
        boolean acquire() {
            count++;
            return count <= limit;
        }

        /** True once no new gap scan should start (the budget is spent). */
        boolean exhausted() {
            return count >= limit;
        }

        /** The fixed evaluation limit (for diagnostics / the capped WARN). */
        int limit() {
            return limit;
        }
    }
}
