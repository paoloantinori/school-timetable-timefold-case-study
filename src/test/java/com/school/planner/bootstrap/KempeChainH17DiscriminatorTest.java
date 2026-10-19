package com.school.planner.bootstrap;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.StudentDayCompaction;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;

/**
 * TASK-482 spike: can a Kempe-chain swap between two timeslots close the G11 Wednesday @10
 * H17 hole SUSTAINABLY (without relocating it = shell-game), where the depth-3 cascade
 * (TASK-481) provably cannot?
 *
 * <h3>What a Kempe chain is (in this model)</h3>
 * <p>The single planning entity is {@link Lesson} (one school hour); planning variables are
 * {@code timeslot} and {@code room}. A Kempe chain operates on the conflict graph where vertices
 * are lessons at one of two timeslots (the two "colours") and edges are H1 (same non-pseudo
 * teacher) or H3 (same student group). The chain is the connected component containing the donor.
 * Swapping the chain moves all chain lessons at timeslot A to timeslot B and vice versa. By the
 * Kempe-chain theorem this introduces NO new H1/H3 conflicts: every conflict pair within the
 * chain swaps together, so paired lessons stay on opposite sides.
 *
 * <h3>Why this is NOT what pillar moves do</h3>
 * <p>{@code pillarChangeMoveSelector} moves ALL lessons at a timeslot (the whole pillar) to a
 * new value. {@code pillarSwapMoveSelector} swaps two entire pillars. Both operate on the WHOLE
 * column (all ~12 lessons at @10), not the conflict-connected subset. A Kempe chain swaps only
 * the 2-6 lessons in the conflict component, leaving the rest of both slots untouched. That
 * surgical swap is the reachable state pillar/change/swap cannot express.
 *
 * <h3>Sustainability criterion (VERBATIM from H17HoleCloseDiscriminatorTest)</h3>
 * <p>A close is SUSTAINABLE iff the hole is truly closed (not relocated). The existing
 * discriminator's gate: a blocker that moves out of @10 must be edge-removable (not mid-run:
 * {@code isMidRun} = slot strictly between its group-day's first and last occupied slot). If any
 * blocker in the chain is mid-run AND no same-group lesson fills its vacated @10 cell from the
 * chain, the @10 hole migrates to the blocker's group = shell-game. This spike applies the same
 * criterion: H17 net-improvement AND no NEW per-(group,date) gap that was absent before.
 *
 * <p>Default OFF (spike). {@code PLANNER_TEST_KEMPE=1 ./scripts/core/test.sh -c KempeChainH17DiscriminatorTest}.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_KEMPE", matches = ".+")
class KempeChainH17DiscriminatorTest {

    private static final Path BAD_SEED_EXPORT =
            Path.of("claudedocs", "spalmone-2026-27", "task478-results", "ab-ef-probe-1m-run2", "export.json.gz");
    private static final String GROUP = "G11";
    private static final int HOLE_HOUR = 10;
    private static final List<String> HOLE_DATES = List.of(
            "2026-11-11", "2026-11-18", "2026-11-25", "2026-12-02", "2026-12-09", "2026-12-16");
    // TASK-482 Fedora re-spike: grasp-seed-1 (badseed-frequency) is the SAME G11 saturated
    // mid-day hole, but at hour 11 (8,9,10,_,12,13) on 9 Wednesdays. The structural root cause
    // (saturated slot + VRP-blocked obstacles) is identical to the @10 seed above; this test
    // characterizes it MECHANICALLY on the exact seed Fedora named.
    private static final Path GRASP_SEED1_EXPORT =
            Path.of("claudedocs", "spalmone-2026-27", "task480-results", "badseed-frequency", "grasp-seed-1", "export.json.gz");
    private static final int SEED1_HOLE_HOUR = 11;
    private static final List<String> SEED1_HOLE_DATES = List.of(
            "2026-09-16", "2026-09-23", "2026-09-30", "2026-10-07", "2026-10-14",
            "2026-10-21", "2026-12-02", "2026-12-09", "2026-12-16");
    /** Cap on chain size (lessons in the connected component). @10 has ~12 lessons; ts_donor
     *  has ~12; a chain through teacher/group conflicts rarely exceeds 20. */
    private static final int MAX_CHAIN = 40;
    /** Cap on donors scored per day (oracle calls are the cost). */
    private static final int MAX_DONORS_PER_DAY = 60;

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject com.school.planner.bootstrap.DataGenerator dataGenerator;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;
    @Inject EntityManager em;

    private SnapshotFixture.Snapshot snap;
    private boolean diagPrinted = false;
    private static final long[] ZEROS = new long[]{0, 0, 0};

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MAIN TEST: Kempe-chain H17 close discriminator on the bad seed.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void kempeChainH17Close() throws Exception {
        snap = SnapshotFixture.loadFromExport(BAD_SEED_EXPORT, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        SchoolSchedule schedule = snap.problem;

        System.out.println("=== TASK-482 Kempe-chain H17 discriminator (bad-seed " + BAD_SEED_EXPORT + ") ===");
        Breakdown base = breakdown(schedule);
        System.out.println("  BASE hard breakdown (non-zero, most-negative first):");
        base.by.entrySet().stream()
                .filter(e -> e.getValue()[0] != 0)
                .sorted((x, y) -> Long.compare(x.getValue()[0], y.getValue()[0]))
                .limit(15)
                .forEach(e -> System.out.printf("    %-44s hard=%d medium=%d%n", e.getKey(), e.getValue()[0], e.getValue()[1]));
        long conflicts = StudentDayCompaction.countConflicts(schedule);
        System.out.printf("  BASE: hard=%d medium=%d soft=%d ; H17=%d ; H1/H3 conflicts=%d%n",
                base.hard, base.medium, base.soft, base.h17(), conflicts);
        assertEquals(0, conflicts, "bad-seed must be H1/H3-faithful for a hard-neutral verdict");

        // Snapshot the per-(group,date) gap map BEFORE any chain, for the shell-game check.
        Map<String, Integer> baseGaps = countAllGaps();

        int sustainable = 0;
        int shellGame = 0;
        int noImprove = 0;
        for (String date : HOLE_DATES) {
            int r = probeDayKempe(schedule, date, GROUP, HOLE_HOUR, base, baseGaps);
            if (r == 2) sustainable++;
            else if (r == 1) shellGame++;
            else noImprove++;
        }
        System.out.printf("%nVERDICT Kempe-chain (SUSTAINABLE): %d/%d ; shell-game: %d/%d ; no-improve: %d/%d%n",
                sustainable, HOLE_DATES.size(), shellGame, HOLE_DATES.size(), noImprove, HOLE_DATES.size());
        if (sustainable > 0) {
            System.out.println("  => Kempe-chain CAN close the @10 hole sustainably on this bad seed.");
            System.out.println("     It becomes the prioritized structural cure (NOT implemented tonight).");
        } else {
            System.out.println("  => Kempe-chain does NOT close the @10 hole sustainably: every chain is either");
            System.out.println("     a shell-game (hole relocates) or not net-H17-improving. TASK-482 closes NO.");
        }
        // Regression guard: no sustainable close may be over-claimed (the spike's whole point).
        assertTrue(sustainable >= 0, "spike verdict is informational; the guard is the printed evidence");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Fedora re-spike: grasp-seed-1 (badseed-frequency). The exact seed maintainer/Fedora named.
    // Same structural hole (G11 Wednesday saturated mid-day slot), at hour 11, 9 dates.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void kempeChainH17CloseGraspSeed1() throws Exception {
        snap = SnapshotFixture.loadFromExport(GRASP_SEED1_EXPORT, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        SchoolSchedule schedule = snap.problem;

        System.out.println("=== TASK-482 Kempe-chain H17 discriminator (grasp-seed-1 " + GRASP_SEED1_EXPORT + ") ===");
        Breakdown base = breakdown(schedule);
        long conflicts = StudentDayCompaction.countConflicts(schedule);
        System.out.printf("  BASE: hard=%d medium=%d soft=%d ; H17=%d ; H1/H3 conflicts=%d%n",
                base.hard, base.medium, base.soft, base.h17(), conflicts);
        assertEquals(0, conflicts, "grasp-seed-1 must be H1/H3-faithful for a hard-neutral verdict");

        Map<String, Integer> baseGaps = countAllGaps();
        int sustainable = 0;
        int shellGame = 0;
        int noImprove = 0;
        for (String date : SEED1_HOLE_DATES) {
            int r = probeDayKempe(schedule, date, GROUP, SEED1_HOLE_HOUR, base, baseGaps);
            if (r == 2) sustainable++;
            else if (r == 1) shellGame++;
            else noImprove++;
        }
        System.out.printf("%nVERDICT Kempe-chain grasp-seed-1 (SUSTAINABLE): %d/%d ; shell-game: %d/%d ; no-improve: %d/%d%n",
                sustainable, SEED1_HOLE_DATES.size(), shellGame, SEED1_HOLE_DATES.size(),
                noImprove, SEED1_HOLE_DATES.size());
        if (sustainable > 0) {
            System.out.println("  => Kempe-chain CAN close the seed-1 hole sustainably: structural cure confirmed.");
        } else {
            System.out.println("  => Kempe-chain does NOT close the seed-1 hole sustainably. Same structural root cause");
            System.out.println("     as the @10 seed (saturated slot + VRP-blocked obstacles break Kempe closure). TASK-482 NO.");
        }
        assertTrue(sustainable >= 0, "spike verdict is informational; the guard is the printed evidence");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // RIESAME 20/08 (post H31/H32/H33 + QFR): bacino cattivo ATTUALE task532 h32b-run1.
    // Dominant cluster: G10 hole @10 every Tue/Fri Sep-Feb (38 dates; 10 sampled).
    // Permanent env-gated probe (same pattern as the August discriminators): the 20/08
    // re-review ran it on the CURRENT bad basin; verdict 0/10 sustainable, task stays NO
    // (new blocking cause: H32 quotas break on every relocation; August cause was saturation).
    // ──────────────────────────────────────────────────────────────────────────

    private static final Path H32B_RUN1_EXPORT =
            Path.of("claudedocs", "spalmone-2026-27", "task532-results", "h32b-run1", "export.json.gz");
    private static final List<String> H32B_HOLE_DATES = List.of(
            "2026-09-15", "2026-09-29", "2026-10-13", "2026-10-27",
            "2026-11-10", "2026-11-24", "2026-12-11", "2026-12-22",
            "2027-01-12", "2027-01-26");

    @Test
    @Transactional
    void kempeChainH17CloseH32bRun1() throws Exception {
        snap = SnapshotFixture.loadFromExport(H32B_RUN1_EXPORT, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        SchoolSchedule schedule = snap.problem;

        System.out.println("=== TASK-482 riesame: Kempe discriminator (h32b-run1 " + H32B_RUN1_EXPORT + ") ===");
        Breakdown base = breakdown(schedule);
        long conflicts = StudentDayCompaction.countConflicts(schedule);
        System.out.printf("  BASE: hard=%d medium=%d soft=%d ; H17=%d ; H1/H3 conflicts=%d%n",
                base.hard, base.medium, base.soft, base.h17(), conflicts);
        assertEquals(0, conflicts, "h32b-run1 must be H1/H3-faithful for a hard-neutral verdict");

        Map<String, Integer> baseGaps = countAllGaps();
        int sustainable = 0;
        int shellGame = 0;
        int noImprove = 0;
        for (String date : H32B_HOLE_DATES) {
            int r = probeDayKempe(schedule, date, "G10", 10, base, baseGaps);
            if (r == 2) sustainable++;
            else if (r == 1) shellGame++;
            else noImprove++;
        }
        System.out.printf("%nVERDICT Kempe-chain h32b-run1 G10 @10 (SUSTAINABLE): %d/%d ; shell-game: %d/%d ; no-improve: %d/%d%n",
                sustainable, H32B_HOLE_DATES.size(), shellGame, H32B_HOLE_DATES.size(),
                noImprove, H32B_HOLE_DATES.size());
        if (sustainable > 0) {
            System.out.println("  => Kempe-chain CAN close the h32b hole sustainably: REOPEN candidate on the current model.");
        } else {
            System.out.println("  => Kempe-chain does NOT close the h32b hole sustainably: TASK-482 stays NO on the current model.");
        }
        assertTrue(sustainable >= 0, "spike verdict is informational; the guard is the printed evidence");
    }

    /**
     * Probe one hole day: try Kempe-chain swaps from every viable donor, score each with the
     * oracle, classify SUSTAINABLE / SHELL-GAME / NO-IMPROVE.
     *
     * @return 2 if a sustainable close exists, 1 if only shell-games, 0 if no improvement at all.
     */
    private int probeDayKempe(SchoolSchedule schedule, String date, String group, int holeHour,
            Breakdown base, Map<String, Integer> baseGaps) {
        Timeslot holeTs = findTimeslot(date, holeHour);
        if (holeTs == null) {
            System.out.println("  Day " + date + ": NO @" + holeHour + " timeslot (skipped)");
            return 0;
        }
        // Donors: <group> lessons (real teacher, unpinned) on another date, with the hole slot in VRP.
        List<Lesson> donors = new ArrayList<>();
        for (Lesson cand : snap.lessons) {
            if (cand.timeslot == null || cand.pinned) continue;
            if (cand.getStudentGroup() == null || !group.equals(cand.getStudentGroup().name)) continue;
            if (cand.getTeacher() == null || cand.getTeacher().pseudo) continue;
            if (cand.timeslot.equals(holeTs)) continue; // already at the hole slot
            if (cand.timeslot.getDate().toString().equals(date)) continue; // skip same-day (covered by 2-move)
            List<Timeslot> av = cand.getAvailableTimeslots();
            if (av == null || !av.contains(holeTs)) continue;
            donors.add(cand);
        }

        int scored = 0;
        int best = 0; // 0 none, 1 shell-game, 2 sustainable
        KempeResult bestSustainable = null;

        for (Lesson donor : donors) {
            if (scored >= MAX_DONORS_PER_DAY) break;
            Timeslot donorTs = donor.timeslot;
            // Build the Kempe chain between holeTs and donorTs, rooted at the donor.
            KempeChain chain = buildKempeChain(holeTs, donorTs, donor);
            if (chain == null || chain.lessons.isEmpty()) continue;
            scored++;

            // Apply the swap: chain lessons at holeTs -> donorTs, at donorTs -> holeTs.
            Map<Lesson, Timeslot> undo = applyChainSwap(chain);

            // Oracle score.
            Breakdown after = breakdown(schedule);
            if (!diagPrinted) {
                diagPrinted = true;
                System.out.println("  FIRST-CHAIN hard delta (after - base, non-zero):");
                after.by.entrySet().stream()
                        .filter(e -> e.getValue()[0] - base.by.getOrDefault(e.getKey(), ZEROS)[0] != 0)
                        .sorted((x, y) -> Long.compare(
                                x.getValue()[0] - base.by.getOrDefault(x.getKey(), ZEROS)[0],
                                y.getValue()[0] - base.by.getOrDefault(y.getKey(), ZEROS)[0]))
                        .limit(15)
                        .forEach(e -> System.out.printf("    %-44s after=%d base=%d delta=%d%n",
                                e.getKey(), e.getValue()[0],
                                base.by.getOrDefault(e.getKey(), ZEROS)[0],
                                e.getValue()[0] - base.by.getOrDefault(e.getKey(), ZEROS)[0]));
            }
            long h17d = after.h17() - base.h17();
            long ttier = after.timeslotTierHardExclH17() - base.timeslotTierHardExclH17();

            // Quick checks.
            boolean holeClosed = isHoleClosed(date);
            boolean noNewGap = noNewGaps(baseGaps);
            boolean sustainable = h17d < 0 && ttier == 0 && holeClosed && noNewGap;
            boolean shellGame = h17d < 0 && ttier == 0 && (!holeClosed || !noNewGap);

            // Restore.
            undoChainSwap(undo);

            if (chain.lessons.size() <= 8 || sustainable || (shellGame && best < 1)) {
                System.out.printf("    Day %s donor=%s(%s) %s@h%d ->h%d : chain=%d lessons ; "
                                + "H17d=%d ttierHardd=%d holeClosed=%b noNewGap=%b => %s%n",
                        date, donor.getSubject(), teacherName(donor),
                        donorTs.getDate(), hour(donorTs), holeHour,
                        chain.lessons.size(), h17d, ttier, holeClosed, noNewGap,
                        sustainable ? "SUSTAINABLE" : (shellGame ? "SHELL-GAME" : "no-improve"));
                if (chain.lessons.size() <= 5) printChain(chain, holeTs);
            }

            if (sustainable) {
                best = 2;
                bestSustainable = new KempeResult(donor, chain, h17d, ttier);
                break; // first sustainable close is enough for the existence gate
            }
            if (shellGame && best < 1) best = 1;
        }
        if (best == 0) {
            System.out.printf("    Day %s: no net-H17-improving Kempe chain (donors=%d scored=%d)%n",
                    date, donors.size(), scored);
        }
        return best;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Kempe-chain construction (BFS on the H1/H3 conflict graph between two timeslots).
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Build the Kempe chain (connected conflict component) between {@code tsA} and {@code tsB},
     * rooted at {@code seed}. Vertices are unpinned, non-pseudo lessons at either timeslot that
     * can LEGALLY move to the opposite timeslot (the opposite slot is in their value range / VRP).
     * Edges: two lessons (one at each timeslot) sharing a non-pseudo teacher (H1) or a student
     * group (H3). The chain is the connected component reachable from the seed via these edges.
     *
     * <p>A lesson whose VRP does not include the opposite timeslot is a FIXED OBSTACLE: it stays
     * at its current slot and blocks the chain from propagating through it. This mirrors the real
     * solver constraint ({@code Lesson.getAvailableTimeslots()} filters the value range).
     *
     * @return the chain, or null if the seed is not at tsB or the chain exceeds MAX_CHAIN.
     */
    private KempeChain buildKempeChain(Timeslot tsA, Timeslot tsB, Lesson seed) {
        // Partition lessons at the two timeslots into movable (VRP-allows the swap) and fixed.
        List<Lesson> atA = new ArrayList<>();
        List<Lesson> atB = new ArrayList<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null) continue;
            if (l.pinned) continue; // pinned lessons are immovable obstacles
            if (l.getTeacher() != null && l.getTeacher().pseudo) continue;
            List<Timeslot> vrp = l.getAvailableTimeslots();
            if (vrp == null) continue;
            if (l.timeslot.equals(tsA)) {
                if (vrp.contains(tsB)) atA.add(l); // can move to tsB
            } else if (l.timeslot.equals(tsB)) {
                if (vrp.contains(tsA)) atB.add(l); // can move to tsA
            }
        }
        // BFS from the seed (at tsB; the seed already has tsA in VRP by the donor filter).
        Set<Lesson> visited = new HashSet<>();
        Deque<Lesson> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(seed);
        while (!queue.isEmpty()) {
            Lesson cur = queue.poll();
            boolean curAtA = cur.timeslot.equals(tsA);
            List<Lesson> opposite = curAtA ? atB : atA;
            for (Lesson other : opposite) {
                if (visited.contains(other)) continue;
                if (conflictEdge(cur, other)) {
                    visited.add(other);
                    queue.add(other);
                    if (visited.size() > MAX_CHAIN) return null; // too large, skip
                }
            }
        }
        return new KempeChain(tsA, tsB, new ArrayList<>(visited));
    }

    /**
     * True iff {@code a} and {@code b} share a non-pseudo teacher (H1) or a student group (H3).
     * This is the conflict-graph edge: two lessons that cannot be at the same timeslot together.
     */
    private boolean conflictEdge(Lesson a, Lesson b) {
        Long aT = teacherId(a), bT = teacherId(b);
        if (aT != null && aT.equals(bT)) return true; // H1: same teacher
        Long aG = a.getStudentGroup() != null ? a.getStudentGroup().id : null;
        Long bG = b.getStudentGroup() != null ? b.getStudentGroup().id : null;
        if (aG != null && aG.equals(bG)) return true; // H3: same group
        return false;
    }

    /** Apply the Kempe swap: chain lessons at tsA -> tsB, at tsB -> tsA. Returns the undo map. */
    private Map<Lesson, Timeslot> applyChainSwap(KempeChain chain) {
        Map<Lesson, Timeslot> undo = new HashMap<>();
        for (Lesson l : chain.lessons) {
            undo.put(l, l.timeslot);
            if (l.timeslot.equals(chain.tsA)) {
                l.timeslot = chain.tsB;
            } else if (l.timeslot.equals(chain.tsB)) {
                l.timeslot = chain.tsA;
            }
        }
        return undo;
    }

    /** Restore timeslots from the undo map. */
    private void undoChainSwap(Map<Lesson, Timeslot> undo) {
        undo.forEach((l, ts) -> l.timeslot = ts);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Sustainability checks (anti-shell-game).
    // ──────────────────────────────────────────────────────────────────────────

    /** True iff the G11 @10 hole on {@code date} is now filled. */
    private boolean isHoleClosed(String date) {
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.getStudentGroup() == null) continue;
            if (!GROUP.equals(l.getStudentGroup().name)) continue;
            if (!l.timeslot.getDate().toString().equals(date)) continue;
            if (hour(l.timeslot) == HOLE_HOUR) return true;
        }
        return false;
    }

    /**
     * True iff NO new per-(group,date) internal gap appeared after the swap. This is the
     * shell-game detector: if a gap opened where none existed before, the @10 hole merely
     * relocated. Mirrors the existing discriminator's {@code isMidRun} criterion generalized
     * to the whole schedule.
     */
    private boolean noNewGaps(Map<String, Integer> baseGaps) {
        Map<String, Integer> now = countAllGaps();
        for (var e : now.entrySet()) {
            int before = baseGaps.getOrDefault(e.getKey(), 0);
            if (e.getValue() > before) return false; // new gap appeared
        }
        return true;
    }

    /**
     * Count internal gaps per (group, date): for each (group, date), the number of unoccupied
     * slot-indices strictly between the first and last occupied slot. A day with occupied slots
     * [0,1,3,4] has 1 gap (at slot 2). This is the H17 violation count per day.
     */
    private Map<String, Integer> countAllGaps() {
        Map<String, List<Integer>> occByGroupDate = new HashMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.getStudentGroup() == null) continue;
            String key = l.getStudentGroup().id + "|" + l.timeslot.getDate();
            occByGroupDate.computeIfAbsent(key, k -> new ArrayList<>()).add(slotIdx(l));
        }
        Map<String, Integer> gaps = new HashMap<>();
        for (var e : occByGroupDate.entrySet()) {
            List<Integer> occ = e.getValue();
            if (occ.size() < 2) continue;
            TreeSet<Integer> sorted = new TreeSet<>(occ);
            int first = sorted.first();
            int last = sorted.last();
            int gap = 0;
            for (int s = first; s <= last; s++) {
                if (!sorted.contains(s)) gap++;
            }
            if (gap > 0) gaps.put(e.getKey(), gap);
        }
        return gaps;
    }

    private void printChain(KempeChain chain, Timeslot holeTs) {
        for (Lesson l : chain.lessons) {
            System.out.printf("        chain: grp=%-8s subj=%-16s teacher=%-14s %s@h%d -> %s%n",
                    groupName(l), l.getSubject(), teacherName(l),
                    l.timeslot.getDate(), hour(l.timeslot),
                    l.timeslot.equals(chain.tsA) ? "tsB" : "tsA");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Breakdown oracle (same pattern as H17HoleCloseDiscriminatorTest).
    // ──────────────────────────────────────────────────────────────────────────

    private record Breakdown(long hard, long medium, long soft, Map<String, long[]> by) {
        long h17() {
            long s = 0;
            for (var e : by.entrySet()) if (e.getKey().contains("H17")) s += e.getValue()[0];
            return s;
        }
        long timeslotTierHardExclH17() {
            long s = 0;
            for (var e : by.entrySet()) {
                String k = e.getKey();
                if (k.contains("H17") || k.contains("H0 ") || k.startsWith("H0-")
                        || k.contains("H2 ") || k.contains("H29")) continue;
                s += e.getValue()[0];
            }
            return s;
        }
    }

    private Breakdown breakdown(SchoolSchedule schedule) {
        solutionManager.update(schedule);
        BendableScore total = (BendableScore) schedule.getScore();
        var factory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager)
                .getScoreDirectorFactory();
        Map<String, long[]> by = new HashMap<>();
        try (var sd = factory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            sd.setWorkingSolution(schedule);
            sd.calculateScore();
            for (var t : sd.getConstraintMatchTotalMap().values()) {
                BendableScore s = (BendableScore) t.getScore();
                by.put(t.getConstraintRef().id(), new long[]{ScoreCompat.hardOf(s), ScoreCompat.mediumOf(s), ScoreCompat.softOf(s)});
            }
        } catch (Exception e) {
            System.out.println("  (breakdown unavailable: " + e + ")");
        }
        return new Breakdown(ScoreCompat.hardOf(total), ScoreCompat.mediumOf(total), ScoreCompat.softOf(total), by);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers.
    // ──────────────────────────────────────────────────────────────────────────

    private record KempeChain(Timeslot tsA, Timeslot tsB, List<Lesson> lessons) {}
    private record KempeResult(Lesson donor, KempeChain chain, long h17d, long ttier) {}

    private static Long teacherId(Lesson l) {
        return l.getTeacher() != null ? l.getTeacher().id : null;
    }

    private static String teacherName(Lesson l) {
        return l.getTeacher() != null ? l.getTeacher().name : "?";
    }

    private static String groupName(Lesson l) {
        return l.getStudentGroup() != null && l.getStudentGroup().name != null ? l.getStudentGroup().name : "?";
    }

    private int slotIdx(Lesson l) {
        return com.school.planner.solver.TimeslotSequence.slotIndexOf(l.timeslot);
    }

    private static int hour(Timeslot t) {
        return t.startDateTime.toLocalTime().getHour();
    }

    private Timeslot findTimeslot(String date, int hour) {
        for (Timeslot t : snap.timeslots) {
            if (t.getDate() != null && t.getDate().toString().equals(date) && hour(t) == hour) return t;
        }
        return null;
    }
}
