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
 * TASK-478 AC#3: deterministic residual-completion pass that ENUMERATES the placement of the
 * small unassigned tail the local search leaves behind on the {@code source=input} path, instead
 * of relying on the LS's stochastic move selection to stumble into it. Sibling of
 * {@link SurplusRebalance} and {@link BoxRepair}, running post-SurplusRebalance / pre-B-ROOM in
 * the staged cycle.
 *
 * <p><b>Why enumeration, not smarter moves</b> (AC#1's feasibility measurement,
 * {@code ac1-feasibility-residuo-run5-2026-08-01.md}): on the run5 residual (16 boxed lessons)
 * every single one has abundant depth-0 cells (0-hard-additive, no bump needed), with a margin of
 * 4x-75x (T08/G03: 45h of free legal cells for 11h needed; T19/G06: 377h for 5h). The
 * residual is NOT infeasible, it is a local-optimum trap: the LS move selector reliably misses
 * cells a direct scan trivially finds. This is the same finding {@link BoxRepair} was built on
 * (each boxed lesson has ~3-7 feasible cells among ~1,000), promoted to a deeper search that also
 * handles the case where the free cells are saturated and a bump chain is needed.
 *
 * <p><b>Adopted DEFAULT ON (opt-out, ratified 2026-08-02).</b> {@link
 * SchoolSchedule#isExactFinisherEnabled()} ({@code planner.exact-finisher.enabled}) is read LIVE
 * at the top of {@link #repair}: an explicit {@code =false} is the only opt-out, and opted out the
 * pass returns a zero Result and mutates nothing. Ratified after the evidence chain (offline
 * discriminator uncapped on the run4 worst case + probe bad-seed placed 18/18 boxed + A/B final
 * 2/2 hard-0 with EF inert on good seed): the pass converts the input path's hard-0 from
 * probabilistic to deterministic, unblocking the m-13 flip. This is a complement of the portfolio
 * multi-start, not a substitute.
 *
 * <p><b>Bounded enumeration, depth 0..1.</b> The decision variables are the {@code k} residuals
 * ({@code <= MAX_K = 30}: a larger residual is a structural problem the LS must handle, not a
 * completion pass). Values are candidate cells at increasing bump depth:
 * <ul>
 * <li><b>Depth-0</b> (the workhorse, per AC#1): a free VRP cell passing the full gate chain, no
 *     bump. Abundant capacity was measured, so the first complete completion is found fast here.</li>
 * <li><b>Depth-1</b>: the target cell is occupied by a SINGLE unpinned blocker O; O is relocated
 *     to a free legal cell (the template is {@link BoxRepair}'s eviction pass). The full
 *     {@code (ts, O, ts2)} triple is validated BEFORE either move is committed.</li>
 * </ul>
 * Depth-2 (two nested evictions) is designed but deferred: O1's arrival at an O2-occupied cell
 * needs a combined bump+relocation day-map gate (the bump cap-waiver for O2 leaving AND the
 * relocation H17 handling for O1's origin vacated) that does not exist in
 * {@code BoxRepair.findEviction} (depth-1 only), plus a depth-2 regression test. AC#1 measured
 * depth-0 alone has 4x-75x margin, so the prototype ships with depth 0..1 and depth-2 is a
 * follow-up gated on the A/B (AC#4) proving depth-0/1 is insufficient.
 * The search reactivates the FORM of {@code DayAssignmentCsp.enumeratePairsRecursive}: DFS,
 * most-constrained-variable ordering (fewest depth-0 candidates first), first-fit commit, and an
 * early-abort at the {@link #getMaxCandidates()} budget (default {@link #DEFAULT_MAX_CANDIDATES}). The cap hit is logged at WARN so a truncation is never
 * silent (the {@link RotationEnumerator} discipline).
 *
 * <p><b>Gate chain, cheapest first, VRP last</b> (the {@link SurplusRebalance}/{@link BoxRepair}
 * cost ordering). Every candidate, at every depth, passes all four gates BEFORE it is committed:
 * <ol>
 * <li><b>H1/H3 conflict</b>: for a bump cell, {@link BoxRepair#singleUnpinnedBlocker} verifies the single
 *     occupant is the only conflict (it will leave); for a free cell,
 *     {@link StudentDayCompaction#isSlotConflictFree} is the full check.</li>
 * <li><b>H14/H11/H7/H17-arrival</b>: {@link BoxRepair#passesPlacementGates} for a fresh placement,
 *     or {@link #passesPlacementGatesForBump} for a bump arrival (cap waived precisely when the
 *     evicted occupant relocates cross-day, mirroring {@code BoxRepair.findEviction}'s own
 *     {@code sameGroup && crossDay} logic).</li>
 * <li><b>H17-departure</b> for each bumped lesson: {@link BoxRepair#isDepartureSafe}, so an
 *     eviction never opens a penalized gap behind it.</li>
 * <li><b>VRP membership</b> ({@link Lesson#getAvailableTimeslots}): evaluated LAST because it is a
 *     linear scan of up to ~1,368 timeslots, while the other gates are O(1) map reads.</li>
 * </ol>
 * Because every committed placement passes the full gate chain, the pass is 0-hard-additive:
 * {@code conflictsAdded} (the H1/H3 double-booking audit) will be 0, and the runner's
 * improve-or-noop is the safety net for any hard worsening the pass's own gates cannot see (e.g.
 * H-COPRESENCE over-depth).
 *
 * <p><b>Objective: maximize residuals placed.</b> The first valid placement for each residual (in
 * MCV order) is committed; the pass does not seek a global optimum. This mirrors {@link BoxRepair}'s
 * proven greedy-per-boxed-lesson structure, extended with MCV ordering. The
 * improve-or-noop runner ({@code ScheduleService.ensureImproveOrNoop}) is the guarantee.
 *
 * <p><b>Determinism.</b> Residuals are MCV-ordered (fewest depth-0 candidates first, stable
 * tiebreak on group/teacher/subject/id). Candidate cells are enumerated in
 * {@code getAvailableTimeslots()} chronological order. Every collection iterated for an ordering
 * decision is a sorted {@code List} or {@code TreeMap}, never a raw {@code HashMap}/{@code HashSet}
 * iteration (the solver demands reproducible construction).
 *
 * <p><b>Staged cycle only, post-release.</b> {@code ScheduleService.runExactFinisher} places it
 * immediately after {@code runSurplusRebalance}, between the smarcatura and stage B-ROOM, where
 * only room variables still move. Like {@link HoleRepair} and {@link SurplusRebalance}, the pass
 * fails fast if called while promoted pins are held (the raw {@code pinned} flag the donor pool
 * reads equals authoritative-pin only once every promotion has been released).
 */
public final class ExactFinisher {

    /** Maximum number of boxed lessons the pass attempts. A larger residual is structural. */
    public static final int MAX_K = 30;

    /**
     * Maximum eviction-chain depth (0 = direct, 1 = one bump). Depth-2 (two nested bumps) is
     * designed but deferred: O1's arrival at an O2-occupied cell needs a combined bump+relocation
     * day-map gate not present in {@code BoxRepair.findEviction}, plus a depth-2 regression test.
     */
    public static final int MAX_DEPTH = 1;

    /**
     * Default candidate-evaluation budget for the early-abort cap (reactivated from
     * {@code DayAssignmentCsp}): the total cell evaluations across all residuals before the search
     * aborts. The LIVE value is the {@link #getMaxCandidates()} knob
     * ({@code -Dplanner.exact-finisher.max-candidates}), which defaults to this constant. Raised
     * 2026-08-02 from 10000 (AC#4 A/B budget-starved: 3 of 15 on a bad seed), then to 100000, and
     * to 1000000 at ratification (maintainer): the probe @ 1M placed 18/18 boxed on a fresh bad seed
     * where 100000 placed only 13/18, so 1000000 is the default that closes the worst-case boxed
     * tail.
     * <p><b>This IS a calibration knob, not a structural constant.</b> A constrained residual scans
     * the whole ~1,368-cell VRP value range when no depth-0 cell exists, so 15 constrained residuals
     * need well over 10k evaluations; the original 10000 was miscalibrated, not a fundamental limit.
     */
    public static final int DEFAULT_MAX_CANDIDATES = 1000000;

    /**
     * The live candidate-evaluation budget. System property
     * {@code planner.exact-finisher.max-candidates}, default {@link #DEFAULT_MAX_CANDIDATES}: the
     * calibration knob for the bounded enumeration, read LIVE at each {@link #repair} invocation
     * (repair-pass discipline, like {@link SchoolSchedule#isExactFinisherEnabled()}) so the budget
     * can be recalibrated without a rebuild. Floored at 1; a blank or malformed value falls back to
     * the default.
     */
    public static int getMaxCandidates() {
        String raw = System.getProperty("planner.exact-finisher.max-candidates");
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
     * Cap on how many depth-0 cells the MCV sort-key precompute counts per lesson. The key only
     * needs to rank the CONSTRAINED tail (few free cells) ahead of the abundant head, so the exact
     * count past this point is irrelevant to the ordering. Decoupled from {@link #getMaxCandidates()}
     * (2026-08-02): the old {@code maxCandidates / MAX_K} formula went vacuous when the default rose
     * to 100000 (3333 exceeds the ~1,368-cell VRP pool, so the cap could never fire).
     */
    private static final int MCV_PROBE_CAP = 64;

    private static final Logger LOG = Logger.getLogger(ExactFinisher.class);

    private ExactFinisher() {
    }

    /**
     * {@code movesApplied} = boxed lessons placed (direct or via bump chain);
     * {@code residualAfter} = boxed lessons still unassigned after the pass;
     * {@code conflictsAdded} = H1/H3 double-bookings introduced (must be 0 by the gate chain).
     */
    public record Result(int movesApplied, int residualAfter, long conflictsAdded) {
    }

    /**
     * Deterministic residual-completion: place as many boxed lessons as possible at 0-hard-additive,
     * committing moves only through the two shared callbacks (never touching a ScoreDirector).
     */
    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                Consumer<Lesson> nullRoom) {
        if (!SchoolSchedule.isExactFinisherEnabled()) {
            return new Result(0, 0, 0);
        }
        if (schedule.getTimeslots() == null) {
            return new Result(0, 0, 0);
        }
        // Post-smarcatura only, exactly like HoleRepair/SurplusRebalance: the donor/blocker pool
        // reads the raw pinned flag, which equals authoritative-pin only once every promoted pin
        // has been released.
        if (!SchoolSchedule.getPromotedLessonIdsStatic().isEmpty()) {
            throw new IllegalStateException("ExactFinisher.repair called while "
                    + SchoolSchedule.getPromotedLessonIdsStatic().size()
                    + " promoted pin(s) are held - this pass is designed for the post-release grid only");
        }

        List<Lesson> boxed = new ArrayList<>();
        for (Lesson l : schedule.getLessons()) {
            if (!l.pinned && l.timeslot == null) boxed.add(l);
        }
        if (boxed.isEmpty() || boxed.size() > MAX_K) {
            return new Result(0, boxed.size(), 0);
        }

        // Indices built ONCE and kept live through every commit (shared builders, same contract
        // as BoxRepair/HoleRepair/SurplusRebalance).
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(schedule.getLessons());
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = BoxRepair.buildGroupDaySlots(schedule.getLessons());
        Map<String, Integer> teacherDaily = BoxRepair.buildTeacherDaily(schedule.getLessons());

        long conflictsBefore = StudentDayCompaction.countConflicts(schedule);

        // The candidate-evaluation budget is read LIVE once per pass (the
        // -Dplanner.exact-finisher.max-candidates calibration knob), then wrapped in a
        // CandidateBudget holder that travels through every scan below so each depth-0/depth-1
        // bound honors the same resolved limit.
        CandidateBudget budget = new CandidateBudget(getMaxCandidates());

        // MCV ordering: fewest depth-0 candidates first (the residual most at risk of having no
        // free cell gets first pick). Stable tiebreak on (group name, teacher name, subject, id).
        // The depth-0 count is pre-computed once per lesson (decorate-sort-undecorate) so the
        // comparator's per-comparison key access is an O(1) field read, never a re-scan of the
        // ~1,368-cell VRP value range (Comparator.comparingInt does not cache its extractor).
        record Keyed(Lesson lesson, int depth0Count) {}
        List<Keyed> ordered = new ArrayList<>(boxed.size());
        for (Lesson l : boxed) {
            ordered.add(new Keyed(l, countDepth0Candidates(l, byTs, groupDaySlots, teacherDaily)));
        }
        ordered.sort(Comparator
                .comparingInt((Keyed k) -> k.depth0Count())
                .thenComparing(k -> k.lesson().getStudentGroup() != null && k.lesson().getStudentGroup().name != null
                        ? k.lesson().getStudentGroup().name : "")
                .thenComparing(k -> k.lesson().getTeacher() != null && k.lesson().getTeacher().name != null ? k.lesson().getTeacher().name : "")
                .thenComparing(k -> k.lesson().getSubject() != null ? k.lesson().getSubject() : "")
                .thenComparing(k -> k.lesson().id != null ? k.lesson().id : Long.MAX_VALUE));

        boolean capped = false;
        int placed = 0;
        for (Keyed keyed : ordered) {
            if (budget.exhausted()) {
                capped = true;
                break;
            }
            if (tryPlace(keyed.lesson(), byTs, groupDaySlots, teacherDaily, moveTimeslot, nullRoom, budget)) {
                placed++;
            }
        }

        long conflictsAfter = StudentDayCompaction.countConflicts(schedule);
        // Every placed lesson had its timeslot set by commitPlace/commitBumpChain, the pass never
        // unplaces, and only the pass mutates these boxed lessons - so the residual is determined
        // by the commit counter, no re-scan needed.
        int residual = boxed.size() - placed;
        if (capped) {
            LOG.warnf("TASK-478 ExactFinisher: candidate-evaluation budget of %d reached - %d of %d residual(s) "
                            + "left boxed. The budget IS a calibration knob (-Dplanner.exact-finisher.max-candidates, "
                            + "default %d): a constrained residual scans the whole ~1,368-cell VRP value range, so raise "
                            + "it before concluding the residual is structural rather than a placement problem.",
                    budget.limit(), residual, boxed.size(), DEFAULT_MAX_CANDIDATES);
        }
        LOG.infof("TASK-478 ExactFinisher: placed %d of %d boxed lesson(s) at depth<=%d (residual %d, conflicts added %d)",
                placed, boxed.size(), MAX_DEPTH, residual, conflictsAfter - conflictsBefore);
        return new Result(placed, residual, conflictsAfter - conflictsBefore);
    }

    // ─── placement search (depth 0, then 1) ───────────────────────────────

    /**
     * Try to place {@code lesson} at the cheapest legal depth. Returns true iff a placement was
     * committed. Depth-0 is tried first (free cell, no bump); depth-1 (one eviction) next. The
     * budget counter tracks total candidate evaluations and triggers the
     * {@link #getMaxCandidates()} early-abort.
     */
    private static boolean tryPlace(Lesson lesson, Map<Timeslot, List<Lesson>> byTs,
                                    Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                    Map<String, Integer> teacherDaily,
                                    BiConsumer<Lesson, Timeslot> moveTimeslot, Consumer<Lesson> nullRoom,
                                    CandidateBudget budget) {
        // Depth-0: free cell passing the full gate chain.
        Timeslot direct = findDepth0Cell(lesson, byTs, groupDaySlots, teacherDaily, budget);
        if (direct != null) {
            commitPlace(lesson, direct, byTs, groupDaySlots, teacherDaily, moveTimeslot, nullRoom);
            return true;
        }
        // Depth-1 (gated by MAX_DEPTH): evict the single unpinned blocker at an otherwise-legal cell.
        // The constant is the real bound the docs/banner claim: setting MAX_DEPTH=0 disables depth-1,
        // and a future findDepth2Chain call would gate on MAX_DEPTH >= 2 the same way.
        if (MAX_DEPTH >= 1) {
            BumpChain chain = findDepth1Chain(lesson, byTs, groupDaySlots, teacherDaily, budget);
            if (chain != null) {
                commitBumpChain(lesson, chain, byTs, groupDaySlots, teacherDaily, moveTimeslot, nullRoom);
                return true;
            }
        }
        return false;
    }

    /**
     * Depth-0: the first chronological VRP cell of {@code lesson} that is conflict-free (H1/H3),
     * passes the placement gates (H14/H11/H7/H17-arrival), and is in the VRP value range. The
     * cheapest-legal cell: no bump, no departure to check.
     */
    private static Timeslot findDepth0Cell(Lesson lesson, Map<Timeslot, List<Lesson>> byTs,
                                           Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                           Map<String, Integer> teacherDaily, CandidateBudget budget) {
        // TASK-578: FRESH range - the placed lesson's current slot cannot mask a
        // stale placement here (stale-cache crash class; see SurplusHarvest)
        for (Timeslot ts : lesson.getAvailableTimeslotsFresh()) {
            if (!budget.acquire()) return null;
            if (!StudentDayCompaction.isSlotConflictFree(lesson, ts, byTs)) continue;
            if (!BoxRepair.passesPlacementGates(lesson, ts, groupDaySlots, teacherDaily)) continue;
            return ts;
        }
        return null;
    }

    /**
     * Depth-1: find a cell {@code ts} for {@code lesson} that is occupied by exactly one unpinned
     * blocker O, where O can be relocated to a free legal cell {@code ts2}. The full triple is
     * validated BEFORE either move is committed: L's conditional gates at {@code ts} (cap waived
     * for O only if O's relocation is cross-day), O's departure safety, and O's full gate chain at
     * {@code ts2} (conflict-free, placement gates, VRP). Returns the validated {@code (ts, O, ts2)}
     * chain, or null.
     *
     * <p>The cap waiver is PRECISE (unlike {@link BoxRepair#passesSwapAwarePlacementGates}, which
     * always waives for demotion analysis where the destination is unknown): here O's destination
     * {@code ts2} IS known, so the cap is waived iff O shares L's group/teacher AND relocates to a
     * different date (same-date is count-neutral; cross-date frees the unit). This mirrors the
     * {@code sameGroup && crossDay} logic in {@code BoxRepair.findEviction}'s own validation.
     */
    private static BumpChain findDepth1Chain(Lesson lesson, Map<Timeslot, List<Lesson>> byTs,
                                             Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                             Map<String, Integer> teacherDaily, CandidateBudget budget) {
        // TASK-578: FRESH range - the placed lesson's current slot cannot mask a
        // stale placement here (stale-cache crash class; see SurplusHarvest)
        for (Timeslot ts : lesson.getAvailableTimeslotsFresh()) {
            if (!budget.acquire()) return null;
            Lesson blocker = BoxRepair.singleUnpinnedBlocker(lesson, ts, byTs);
            if (blocker == null) continue;
            // O's departure gate, MIRRORED from BoxRepair.findEviction: only a DIFFERENT-group
            // blocker's removal can open an H17 gap on its own day (L does not refill O's slot
            // there). A SAME-group blocker's slot is refilled by L - L lands at chain.headCell(),
            // which equals bump.origin() == ts (O's current cell), so the post-commit group-day
            // map at that slot is byte-identical (occupied by L instead of O) and the departure is
            // gap-neutral. isDepartureSafe reads the pre-commit map and cannot see the refiller, so
            // gating it on !sameGroup avoids a false negative that strands a placeable residual.
            boolean sameGroup = StudentDayCompaction.sharesGroup(lesson, blocker);
            if (!sameGroup && !BoxRepair.isDepartureSafe(blocker, groupDaySlots)) continue;
            // O's relocation to a free legal cell. The refiller is L (the boxed lesson takes O's
            // origin cell at ts), so sameGroup = sharesGroup(L, O) [recomputed inside findRelocationCell].
            Timeslot ts2 = findRelocationCell(blocker, ts, lesson, byTs, groupDaySlots, teacherDaily, budget);
            if (ts2 == null) continue;
            // L's conditional gates at ts: cap waived for O only if O relocates cross-day.
            if (!passesPlacementGatesForBump(lesson, ts, blocker, ts2, groupDaySlots, teacherDaily)) continue;
            return new BumpChain(ts, new Bump(blocker, ts, ts2));
        }
        return null;
    }

    /**
     * The placement gate for a BUMP arrival: H14 (group start date), H11 (group daily cap), H7
     * (teacher daily cap, pseudo exempt) and H17-safe ARRIVAL, where the cap is waived for the
     * {@code occupant} that is about to leave {@code ts} ONLY when the occupant's known relocation
     * destination {@code occupantDest} is on a DIFFERENT date (cross-day = the unit is freed;
     * same-day = the occupant stays on the date, count unchanged). Mirrors the {@code crossDay}
     * logic in {@code BoxRepair.findEviction}'s own cap check.
     *
     * <p>The H1/H3 conflict check is NOT here: for a bump cell, the single occupant IS the conflict
     * (verified by {@link BoxRepair#singleUnpinnedBlocker} before this gate is called). What this
     * gate answers is "once that occupant leaves, are the non-conflict gates clean?". The gate
     * arithmetic (H14/H11/H7/H17-arrival) is delegated to {@link
     * BoxRepair#passesNonConflictPlacementGates} - the single place those caps are evaluated - so
     * this pass cannot drift from {@link BoxRepair#findEviction}; only the two {@code *CountNeutral}
     * flags (the bump-specific cap waiver) are computed here.
     */
    private static boolean passesPlacementGatesForBump(Lesson mover, Timeslot ts,
                                                       Lesson occupant, Timeslot occupantDest,
                                                       Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                                       Map<String, Integer> teacherDaily) {
        StudentGroup group = mover.getStudentGroup();
        Teacher teacher = mover.getTeacher();
        LocalDate date = ts.getDate();
        if (date == null) return false;
        Integer groupCap = group != null ? group.maxHoursPerDay : null;
        int teacherCap = teacher != null && !teacher.pseudo ? teacher.getEffectiveMaxDailyHours() : 0;
        TreeMap<Integer, Boolean> daySlots = group != null ? groupDaySlots.get(group.id + "@" + date) : null;
        // Cap waived iff occupant shares mover's group/teacher AND relocates cross-day.
        boolean crossDay = occupantDest != null && !date.equals(occupantDest.getDate());
        boolean groupCountNeutral = occupant != null && crossDay && StudentDayCompaction.sharesGroup(mover, occupant);
        boolean teacherCountNeutral = occupant != null && crossDay && StudentDayCompaction.sharesRealTeacher(mover, occupant);
        return BoxRepair.passesNonConflictPlacementGates(group, teacher, groupCap, teacherCap, ts,
                daySlots, teacherDaily, groupCountNeutral, teacherCountNeutral);
    }

    // ─── shared helpers ───────────────────────────────────────────────────
    // singleUnpinnedBlocker and passesNonConflictPlacementGates are NOT re-defined here: they
    // live in BoxRepair (package-private, widened TASK-478 AC#3) so "what counts as a blocker"
    // and the H11/H7/H17-arrival gate arithmetic cannot drift between this pass and BoxRepair.

    /**
     * The first chronological VRP cell of {@code mover} (relocating from {@code origin}) that passes
     * the RELOCATION-AWARE gate {@link BoxRepair#isLegalRelocation}: H14, conflict-free (H1/H3),
     * H11/H7 caps on cross-day moves only (same-day is count-neutral), and H17-safe ARRIVAL computed
     * against the origin-aware day map (the origin slot is removed before the arrival simulation for
     * a different-group refiller, or left in place for a same-group refiller that refills it). Used
     * to find a legal destination for a bumped blocker.
     *
     * <p><b>Why not {@link BoxRepair#passesPlacementGates}</b> (the confirmed bug this fixes): that
     * is the FRESH-placement gate, which reads the live group-day map still containing the mover at
     * its origin and simulates an ADD at the destination. For a same-day relocation the simulation
     * sees the mover at BOTH origin and destination, which can accept an H17-widening move the runner
     * cannot catch (the H0 credit from placing the boxed lesson outweighs the H17 debit, so net hard
     * still improves and {@code shouldRevert} stays silent). It also falsely charges H11/H7 caps on
     * count-neutral same-day relocations. {@link BoxRepair#isLegalRelocation} is the correct
     * relocation gate: it branches same-day vs cross-day, reads {@code originDay} (refilled) vs
     * {@code originVacated} (slot removed) for the H17 check, and skips caps for same-day moves.
     *
     * @param refiller the lesson that takes the mover's origin cell after the relocation (the boxed
     *                 lesson L for a depth-1 bump). Determines {@code sameGroup}: if the refiller shares the mover's
     *                 group, it refills the origin slot and the arrival H17 check reads the full
     *                 {@code originDay}; otherwise the slot is vacated and the check reads
     *                 {@code originVacated}.
     */
    private static Timeslot findRelocationCell(Lesson mover, Timeslot origin, Lesson refiller,
                                               Map<Timeslot, List<Lesson>> byTs,
                                               Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                               Map<String, Integer> teacherDaily, CandidateBudget budget) {
        LocalDate originDate = origin.getDate();
        StudentGroup mGroup = mover.getStudentGroup();
        int originSlot = TimeslotSequence.slotIndexOf(origin);
        boolean sameGroup = StudentDayCompaction.sharesGroup(refiller, mover);
        TreeMap<Integer, Boolean> originDay = mGroup != null && originDate != null
                ? groupDaySlots.get(mGroup.id + "@" + originDate) : null;
        TreeMap<Integer, Boolean> originVacated = null;
        if (!sameGroup && originDay != null) {
            originVacated = new TreeMap<>(originDay);
            originVacated.remove(originSlot);
        }
        // TASK-578: FRESH range (stale-cache crash class)
        for (Timeslot cand : mover.getAvailableTimeslotsFresh()) {
            if (!budget.acquire()) return null;
            if (BoxRepair.isLegalRelocation(mover, cand, origin, originDate, sameGroup,
                    originDay, originVacated, groupDaySlots, teacherDaily, byTs)) {
                return cand;
            }
        }
        return null;
    }

    // ─── index maintenance (mirror BoxRepair.place / commitEviction) ──────

    /**
     * Commit a depth-0 placement: move, null the room, keep byTs / groupDaySlots / teacherDaily
     * live. Maps only grow here (a boxed lesson has no origin to shrink).
     */
    private static void commitPlace(Lesson lesson, Timeslot ts, Map<Timeslot, List<Lesson>> byTs,
                                    Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                    Map<String, Integer> teacherDaily,
                                    BiConsumer<Lesson, Timeslot> moveTimeslot, Consumer<Lesson> nullRoom) {
        moveTimeslot.accept(lesson, ts);
        nullRoom.accept(lesson);
        byTs.computeIfAbsent(ts, k -> new ArrayList<>()).add(lesson);
        addToDayMaps(lesson, ts, groupDaySlots, teacherDaily);
    }

    /**
     * Commit a depth-1 bump chain: relocate the blocker O to its validated free cell first (frees
     * the head cell), then place the boxed lesson at the head cell. The relocation mirrors
     * {@link BoxRepair}'s {@code commitEviction}: remove the origin entries from all three maps,
     * add the destination entries, then the boxed lesson's placement grows the maps.
     */
    private static void commitBumpChain(Lesson lesson, BumpChain chain,
                                        Map<Timeslot, List<Lesson>> byTs,
                                        Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                        Map<String, Integer> teacherDaily,
                                        BiConsumer<Lesson, Timeslot> moveTimeslot, Consumer<Lesson> nullRoom) {
        // Relocate the blocker first (its destination is a FREE cell, validated against the live maps).
        Bump bump = chain.bump();
        relocate(bump.lesson(), bump.origin(), bump.destination(),
                byTs, groupDaySlots, teacherDaily, moveTimeslot, nullRoom);
        // Place the boxed lesson at the head cell (the blocker's origin, now vacated).
        commitPlace(lesson, chain.headCell(), byTs, groupDaySlots, teacherDaily, moveTimeslot, nullRoom);
    }

    /**
     * Relocate {@code lesson} from {@code origin} to {@code dest}, keeping all three maps honest:
     * remove origin entries (the shrink case), add destination entries. Same-day moves apply
     * remove-then-add to the same day map and the same teacher count (net count unchanged). Mirrors
     * {@code BoxRepair.commitEviction}'s relocation half byte-for-byte.
     */
    private static void relocate(Lesson lesson, Timeslot origin, Timeslot dest,
                                 Map<Timeslot, List<Lesson>> byTs,
                                 Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                 Map<String, Integer> teacherDaily,
                                 BiConsumer<Lesson, Timeslot> moveTimeslot, Consumer<Lesson> nullRoom) {
        moveTimeslot.accept(lesson, dest);
        nullRoom.accept(lesson);
        List<Lesson> originList = byTs.get(origin);
        if (originList != null) originList.remove(lesson);
        byTs.computeIfAbsent(dest, k -> new ArrayList<>()).add(lesson);

        StudentGroup group = lesson.getStudentGroup();
        if (group != null) {
            LocalDate originDate = origin.getDate();
            LocalDate destDate = dest.getDate();
            int originSlot = TimeslotSequence.slotIndexOf(origin);
            int destSlot = TimeslotSequence.slotIndexOf(dest);
            TreeMap<Integer, Boolean> originDay = originDate != null
                    ? groupDaySlots.get(group.id + "@" + originDate) : null;
            if (originDay != null) originDay.remove(originSlot);
            if (originDate != null && originDate.equals(destDate)) {
                if (originDay != null) originDay.put(destSlot, BaseConstraintProvider.isAuthoritativePin(lesson));
            } else if (destDate != null) {
                groupDaySlots.computeIfAbsent(group.id + "@" + destDate, k -> new TreeMap<>())
                        .put(destSlot, BaseConstraintProvider.isAuthoritativePin(lesson));
            }
        }
        Teacher teacher = lesson.getTeacher();
        if (teacher != null && !teacher.pseudo) {
            LocalDate originDate = origin.getDate();
            LocalDate destDate = dest.getDate();
            if (originDate != null && !originDate.equals(destDate)) {
                BoxRepair.decrement(teacherDaily, teacher.id + "@" + originDate);
                if (destDate != null) teacherDaily.merge(teacher.id + "@" + destDate, 1, Integer::sum);
            }
        }
    }

    /** Add {@code lesson}'s entries at {@code ts} to the group-day and teacher-daily maps. */
    private static void addToDayMaps(Lesson lesson, Timeslot ts,
                                     Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                     Map<String, Integer> teacherDaily) {
        StudentGroup group = lesson.getStudentGroup();
        LocalDate date = ts.getDate();
        if (group != null && date != null) {
            groupDaySlots.computeIfAbsent(group.id + "@" + date, k -> new TreeMap<>())
                    .put(TimeslotSequence.slotIndexOf(ts), BaseConstraintProvider.isAuthoritativePin(lesson));
        }
        Teacher teacher = lesson.getTeacher();
        if (teacher != null && !teacher.pseudo && date != null) {
            teacherDaily.merge(teacher.id + "@" + date, 1, Integer::sum);
        }
    }

    /**
     * The number of depth-0 (free, gate-passing) cells for {@code lesson}: the MCV key. Counted
     * with a hard stop at {@link #MCV_PROBE_CAP} so the sort key itself never blows the budget (the
     * sort runs once, before any placement, and does NOT consume the shared placement budget - it
     * uses this isolated cap, not the {@link CandidateBudget}).
     */
    private static int countDepth0Candidates(Lesson lesson, Map<Timeslot, List<Lesson>> byTs,
                                             Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                             Map<String, Integer> teacherDaily) {
        int count = 0;
        int probeBudget = MCV_PROBE_CAP;
        // TASK-578: counting key reads FRESH too (a warm-stale count would mix
        // incommensurable MCV keys on a resumed instance; cold path, once per lesson)
        for (Timeslot ts : lesson.getAvailableTimeslotsFresh()) {
            if (count >= probeBudget) break;
            if (StudentDayCompaction.isSlotConflictFree(lesson, ts, byTs)
                    && BoxRepair.passesPlacementGates(lesson, ts, groupDaySlots, teacherDaily)) {
                count++;
            }
        }
        return count;
    }

    /** A validated relocation of one lesson from {@code origin} to {@code destination}. */
    private record Bump(Lesson lesson, Timeslot origin, Timeslot destination) {
    }

    /**
     * A validated depth-1 bump chain: {@code headCell} is where the boxed lesson lands;
     * {@code bump} is the single blocker relocation that frees it.
     */
    private record BumpChain(Timeslot headCell, Bump bump) {
    }

    /**
     * Mutable candidate-evaluation counter and its fixed limit, replacing the {@code int[1]}
     * array-as-counter hack. {@link #acquire()} is the inner-scan idiom (increment, abort the scan
     * once the limit is exceeded); {@link #exhausted()} is the outer-loop guard (do not start a new
     * residual once the budget is spent). The limit is fixed at construction from the live
     * {@link #getMaxCandidates()} knob and exposed read-only via {@link #limit()} for diagnostics.
     */
    private static final class CandidateBudget {
        private final int limit;
        private int count;

        CandidateBudget(int limit) {
            this.limit = limit;
        }

        /** Count one evaluation; false once the limit is exceeded (the scan must abort). */
        boolean acquire() {
            count++;
            return count <= limit;
        }

        /** True once no new residual scan should start (the budget is spent). */
        boolean exhausted() {
            return count >= limit;
        }

        /** The fixed evaluation limit (for diagnostics / the capped WARN). */
        int limit() {
            return limit;
        }
    }
}
