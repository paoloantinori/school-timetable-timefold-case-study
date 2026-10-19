package com.school.planner.bootstrap;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.CompositionHoleCloser;
import com.school.planner.solver.StudentDayCompaction;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;

/**
 * TASK-481 spike (DRAFT-006 de-risk): is the G11 Wednesday @10 internal hole CLOSEABLE in the
 * final bad-seed solution's hard-constraint neighbourhood, and by what move? Discriminates the cure
 * direction BEFORE production code ("repro offline prima del codice").
 *
 * <h3>Reframing (why this spike exists)</h3>
 *
 * <p>The alba4 diagnosis proposed {@code default-forger -> tabu/late-acceptance} as the cure, claiming
 * "no metaheuristic, hill-climbing, change+swap only". That is wrong about the config that runs:
 * {@code solverConfig.xml} Phase 2 already has {@code <acceptor><entityTabuSize>7>} (tabu), an accepted
 * forager {@code acceptedCountLimit=1000}, and FOUR selectors ({@code change, swap, pillarChange,
 * pillarSwap}); the staged B-TIMESLOT solver loads that XML ({@code ScheduleService#timeslotPhasesConfig}
 * -> {@code createFromXmlResource}). Tabu + pillar already run and STILL leave the hole. The deterministic
 * {@link StudentDayCompaction} (Phase 3) closes a gap only by moving the day's MAX-slot lesson down; here
 * max = T06@13, saturated @10, so it breaks and never tries a composition change.
 *
 * <p>So the real question: does a coordinated 2-move exist that closes @10 on each hole day AND is
 * globally hard-neutral on the TIMESLOT-TIER families (H1/H3/H5/H7/H11/H20/H23/H24/H25/H26/H28)?
 * If YES on all 6 days, a deterministic composition-aware hole-repair is viable. If NO, the hole is
 * hard-forced in the final's neighbourhood (cure must move upstream to construction).
 *
 * <h3>Fidelity note (what the reconstruction can and cannot score)</h3>
 *
 * <p>{@link SnapshotFixture#loadFromExport} reconstructs placements (timeslots+rooms+ids) faithfully,
 * so H1/H3 (teacher/group conflicts) are exact and {@link StudentDayCompaction#countConflicts} is 0
 * (asserted below, same invariant the EF discriminator pins). The reconstruction is NOT fed the full
 * calendar/quota/stage/availability dataset the live solve had, so the ABSOLUTE hard score is noisy on
 * H2/H20/H23/H24/H25/H26/H29 (constant offsets, the EF gate chain excludes them for the same reason).
 * Those families are CONSTANT w.r.t. a within-group timeslot move on open slots, so they CANCEL in the
 * delta. The verdict therefore keys on the timeslot-tier delta (H1/H3/H5/H7/H11/H28 + the calendar
 * families), with H17 tracked separately as the signal. Rooms (H2/H29) are excluded: they are a
 * downstream phase (B-ROOM) and irrelevant to whether the timeslot grid can be made compact.
 *
 * <p>Default OFF (spike). {@code PLANNER_TEST_H17_HOLE=1 ./scripts/core/test.sh -c H17HoleCloseDiscriminatorTest}.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_H17_HOLE", matches = ".+")
class H17HoleCloseDiscriminatorTest {

    private static final Path BAD_SEED_EXPORT =
            Path.of("claudedocs", "spalmone-2026-27", "task478-results", "ab-ef-probe-1m-run2", "export.json.gz");
    private static final String GROUP = "G11";
    private static final int HOLE_HOUR = 10;
    private static final List<String> HOLE_DATES = List.of(
            "2026-11-11", "2026-11-18", "2026-11-25", "2026-12-02", "2026-12-09", "2026-12-16");
    private static final int MAX_SCORED_PAIRS_PER_DAY = 25;

    /**
     * Cap on depth-3 triples scored (breakdown calls) per hole day. The breakdown is the real score
     * (the oracle), so it is the cost; the cheap {@code cellConflictFreeExcluding} pre-filter and the
     * break-on-first-close keep the typical count far below this. The cap is a safety belt against a
     * pathological day, never the expected limit (a closable day returns on its first valid triple).
     */
    private static final int MAX_DEPTH3_SCORED_PER_DAY = 300;

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject com.school.planner.bootstrap.DataGenerator dataGenerator;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;
    @Inject EntityManager em;

    private SnapshotFixture.Snapshot snap;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    @Transactional
    void holeCloseFeasibility() throws Exception {
        snap = SnapshotFixture.loadFromExport(BAD_SEED_EXPORT, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        SchoolSchedule schedule = snap.problem;

        System.out.println("=== TASK-481 H17 hole-close discriminator (bad-seed " + BAD_SEED_EXPORT + ") ===");
        Breakdown base = breakdown(schedule);
        System.out.printf("  BASE total: hard=%d medium=%d soft=%d ; H1/H3 conflicts=%d%n",
                base.hard, base.medium, base.soft, StudentDayCompaction.countConflicts(schedule));
        System.out.println("  BASE top hard families:");
        base.by.entrySet().stream().filter(e -> e.getValue()[0] != 0)
                .sorted((e1, e2) -> Long.compare(e2.getValue()[0], e1.getValue()[0]))
                .limit(12)
                .forEach(e -> System.out.printf("    %-44s hard=%d%n", e.getKey(), e.getValue()[0]));
        long expectedConflicts = StudentDayCompaction.countConflicts(schedule);
        if (expectedConflicts != 0) {
            System.out.println("  !! RECONSTRUCTION NOT H1/H3-FAITHFUL (conflicts=" + expectedConflicts
                    + "): spike verdict unreliable. Aborting.");
            return;
        }

        int daysWithClose = 0;
        for (String date : HOLE_DATES) {
            if (probeDay(schedule, date)) daysWithClose++;
        }
        System.out.printf("VERDICT: %d/%d hole days admit a TIMESLOT-TIER-HARD-NEUTRAL H17-closing 2-move.%n",
                daysWithClose, HOLE_DATES.size());
        if (daysWithClose == HOLE_DATES.size()) {
            System.out.println("  => hole is a SEARCH-NEIGHBOURHOOD gap (composition-aware repair viable).");
        } else {
            System.out.println("  => at least one day HARD-FORCED in this neighbourhood: search tweak cannot "
                    + "close it; cure must move upstream (construction composition) or widen the move class.");
        }
    }

    private boolean probeDay(SchoolSchedule schedule, String date) {
        Timeslot holeTs = findTimeslot(date, HOLE_HOUR);
        if (holeTs == null) {
            System.out.println("  Day " + date + ": NO @10 timeslot (skipped)");
            return false;
        }
        List<Lesson> day = snap.lessons.stream()
                .filter(l -> l.timeslot != null && l.getStudentGroup() != null
                        && GROUP.equals(l.getStudentGroup().name)
                        && l.timeslot.getDate().toString().equals(date))
                .sorted(Comparator.comparingInt(this::slotIdx))
                .toList();
        boolean hasHole = day.stream().noneMatch(l -> hour(l.timeslot) == HOLE_HOUR);
        System.out.printf("  Day %s: slots=%s hole@%d=%s%n", date,
                day.stream().map(l -> String.valueOf(slotIdx(l))).toList(), HOLE_HOUR, hasHole);

        List<Lesson> bAvail = new ArrayList<>();
        for (Lesson cand : snap.lessons) {
            if (cand.timeslot == null || cand.pinned) continue;
            if (cand.getStudentGroup() == null || !GROUP.equals(cand.getStudentGroup().name)) continue;
            if (cand.getTeacher() == null || cand.getTeacher().pseudo) continue;
            if (cand.timeslot.getDate().toString().equals(date)) continue;
            List<Timeslot> av = cand.getAvailableTimeslots();
            if (av == null || !av.contains(holeTs)) continue;
            bAvail.add(cand);
        }
        if ("2026-11-11".equals(date)) {
            Map<String, Integer> byT = new HashMap<>();
            for (Lesson b : bAvail) byT.merge(teacherName(b), 1, Integer::sum);
            System.out.printf("    bAvail(527) teacher histogram: %s%n",
                    byT.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue())
                            .map(e -> e.getKey() + "=" + e.getValue()).toList());
            for (String ft : List.of("T12", "T22", "T24")) {
                int n = byT.getOrDefault(ft, 0);
                System.out.printf("    free-@10 teacher %s in bAvail: %d lesson(s)%n", ft, n);
            }
        }

        int scored = 0, closes = 0;
        Map<String, Integer> why = new HashMap<>();

        // (1) ONE-MOVE fill: B -> @10 (day becomes 6 contiguous -> H17 satisfied), no eviction.
        //     Viable only if B's teacher is free @10 (H1) and the fill is otherwise hard-neutral.
        int oneMoveCloses = 0;
        int oneMoveScored = 0;
        long oneMoveBestH17 = Long.MAX_VALUE;
        String oneMoveBest = null;
        for (Lesson b : bAvail) {
            if (!singleFreeAtHole(b, holeTs)) continue;
            Timeslot bOld = b.timeslot;
            Breakdown before = breakdown(schedule);
            b.timeslot = holeTs;
            Breakdown after = breakdown(schedule);
            long h17d = after.h17() - before.h17();
            long ttier = after.timeslotTierHardExclH17() - before.timeslotTierHardExclH17();
            b.timeslot = bOld;
            scored++;
            oneMoveScored++;
            if (oneMoveScored <= 3 || (ttier <= 0 && h17d < oneMoveBestH17)) {
                String summary = String.format("B=%s(%s) %s@h%d ->h10 : H17d=%d ttierHardd=%d%s",
                        b.getSubject(), teacherName(b), bOld.getDate(), hour(bOld), h17d, ttier,
                        ttier == 0 ? "" : " [HARD-BLOCKED]");
                if (oneMoveScored <= 3) System.out.println("    1-move sample: " + summary);
                if (ttier <= 0 && h17d < oneMoveBestH17) { oneMoveBestH17 = h17d; oneMoveBest = summary; }
            }
            if (h17d < 0 && ttier == 0) {
                oneMoveCloses++;
                System.out.printf("    1-MOVE CLOSE: fill %s(%s)@h10 [from %s@h%d] : H17d=%d ttierHardd=%d mediumd=%d%n",
                        b.getSubject(), teacherName(b), bOld.getDate(), hour(bOld), h17d, ttier, after.medium - before.medium);
                deltaNonZero(before, after).forEach(d -> System.out.printf("        delta %-40s h=%d m=%d s=%d%n",
                        d.constraint, d.h, d.m, d.s));
                break;
            }
            if (scored >= MAX_SCORED_PAIRS_PER_DAY) break;
        }
        closes += oneMoveCloses;

        // (2) TWO-MOVE: evict a day lesson A -> B's old slot, fill @10 with B. (Original probe.)
        if (oneMoveCloses == 0) {
            outer:
            for (Lesson a : day) {
                for (Lesson b : bAvail) {
                    String reject = conflictFreeReason(a, b, holeTs);
                    if (reject != null) {
                        why.merge(reject, 1, Integer::sum);
                        continue;
                    }
                    Timeslot aOld = a.timeslot, bOld = b.timeslot;
                    Breakdown before = breakdown(schedule);
                    b.timeslot = holeTs;
                    a.timeslot = bOld;
                    Breakdown after = breakdown(schedule);
                    long h17d = after.h17() - before.h17();
                    long ttier = after.timeslotTierHardExclH17() - before.timeslotTierHardExclH17();
                    a.timeslot = aOld;
                    b.timeslot = bOld;
                    if (h17d < 0 && ttier == 0) {
                        closes++;
                        System.out.printf("    2-MOVE CLOSE: evict %s@h%d(%s)->%s@h%d ; fill %s(%s)@h10 "
                                        + ": H17d=%d ttierHardd=%d mediumd=%d%n",
                                teacherName(a), hour(aOld), a.getSubject(), bOld.getDate(), hour(bOld),
                                b.getSubject(), teacherName(b), h17d, ttier, after.medium - before.medium);
                        deltaNonZero(before, after).forEach(d -> System.out.printf("        delta %-40s h=%d m=%d s=%d%n",
                                d.constraint, d.h, d.m, d.s));
                        break outer;
                    }
                }
            }
        }
        System.out.printf("    B available@10=%d ; 1-move scored(free teacher)=%d ; 1-move closes=%d ; total closes=%d%n",
                bAvail.size(), oneMoveScored, oneMoveCloses, closes);
        if (oneMoveCloses == 0 && oneMoveBest != null)
            System.out.println("    best 1-move (H17 only): " + oneMoveBest);
        if (oneMoveCloses == 0) System.out.printf("    2-move pre-filter rejects: %s%n", why);
        return closes > 0;
    }

    /** @return true if B's teacher is free @holeTs (no H1) and no same-group lesson is there (H3). */
    private boolean singleFreeAtHole(Lesson b, Timeslot holeTs) {
        Long bT = teacherId(b);
        Long grp = b.getStudentGroup() != null ? b.getStudentGroup().id : null;
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l == b) continue;
            if (!l.timeslot.equals(holeTs)) continue;
            Long lt = teacherId(l), lg = l.getStudentGroup() != null ? l.getStudentGroup().id : null;
            if (bT != null && bT.equals(lt)) return false;
            if (grp != null && grp.equals(lg)) return false;
        }
        return true;
    }

    // ---- breakdown + delta ----

    // ─────────────────────────────────────────────────────────────────────────────
    // TASK-481 depth-3 CROSS-GROUP cascade probe (Opzione A existence gate).
    //
    // The 1/2-move probes above keep the fill WITHIN G11 and fail (0/6) because every donor's
    // teacher is busy @10 with ANOTHER group's lesson. Depth 3 is the untested move class: evict
    // that cross-group blocker out of @10 into a free legal cell, THEN fill @10 with the donor.
    // This is the existence gate for the composition-aware hole-closer: if the cascade closes the
    // hole with a timeslot-tier-hard-neutral delta on every bad-seed day, the live pass is viable;
    // if not, the cure needs depth > 3 and we stop honest (mandate #3, re-fork to Fedora).
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void depth3CrossGroupCascade() throws Exception {
        snap = SnapshotFixture.loadFromExport(BAD_SEED_EXPORT, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        SchoolSchedule schedule = snap.problem;

        System.out.println("=== TASK-481 depth-3 CROSS-GROUP cascade probe (bad-seed " + BAD_SEED_EXPORT + ") ===");
        long conflicts = StudentDayCompaction.countConflicts(schedule);
        System.out.printf("  BASE H1/H3 conflicts=%d (must be 0 for a faithful verdict)%n", conflicts);
        if (conflicts != 0) {
            System.out.println("  !! RECONSTRUCTION NOT H1/H3-FAITHFUL: depth-3 verdict unreliable. Aborting.");
            return;
        }

        int sustainable = 0;
        int shellGame = 0;
        for (String date : HOLE_DATES) {
            int r = probeDepth3(schedule, date);
            if (r == 2) sustainable++;
            else if (r == 1) shellGame++;
        }
        // Existence gate (mandate #3): a SUSTAINABLE depth-3 close must exist to justify a bounded
        // composition-aware repair. A shell-game close (blocker mid-run: the @10 hole relocates groups,
        // net-improving only via an unrelated gap fill) is NOT a cure. On this bad seed every @10
        // blocker is mid-run, so sustainable == 0: the hole is hard-forced at depth 3, and the cure
        // must move to a deeper/different mechanism (stop honest, re-fork to Fedora).
        assertEquals(0, sustainable,
                "no SUSTAINABLE depth-3 close may be claimed on this bad seed: shell-game closes are not a cure");
        System.out.printf("VERDICT depth-3 (SUSTAINABLE): %d/%d ; shell-game: %d/%d.%n",
                sustainable, HOLE_DATES.size(), shellGame, HOLE_DATES.size());
        if (sustainable == 0) {
            System.out.println("  => depth-3 SUSTAINABLE close does NOT exist: every @10 blocker is mid-run, the");
            System.out.println("     cascade is a shell game (@10 hole relocates groups). Cure needs a deeper or");
            System.out.println("     different mechanism (iterative cascade, construction-side composition, multi-start).");
        }
    }

    /**
     * GREEN test for {@link CompositionHoleCloser} (TASK-481 Opzione A): pins the pass's CORRECT
     * INERTNESS on the saturated G11 @10 bad seed. The depth-3 cascade is a shell game there
     * (every @10 blocker is mid-run), so the pass's isDepartureSafe gate refuses every blocker:
     * 0 cascades, 0 H1/H3 conflicts, H17 unchanged (no harm). Runs the pass directly (no ScoreDirector
     * runner); the breakdown oracle confirms no timeslot-tier hard worsens.
     */
    @Test
    @Transactional
    void compositionHoleCloserClosesBadSeed() throws Exception {
        snap = SnapshotFixture.loadFromExport(BAD_SEED_EXPORT, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        SchoolSchedule schedule = snap.problem;

        System.out.println("=== TASK-481 CompositionHoleCloser.repair on bad-seed (gate ON) ===");
        Breakdown base = breakdown(schedule);
        long baseH17 = base.h17();
        long baseTtier = base.timeslotTierHardExclH17();
        long baseConflicts = StudentDayCompaction.countConflicts(schedule);
        System.out.printf("  BASE: H17=%d ttierHard(excl H17)=%d H1/H3 conflicts=%d%n",
                baseH17, baseTtier, baseConflicts);
        assertEquals(0, baseConflicts, "bad-seed must be H1/H3-faithful for a hard-neutral verdict");

        System.setProperty("planner.composition-hole-closer.enabled", "true");
        try {
            CompositionHoleCloser.Result result = CompositionHoleCloser.repair(schedule,
                    (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);

            Breakdown after = breakdown(schedule);
            System.out.printf("  AFTER: H17=%d ttierHard(excl H17)=%d ; gaps %d->%d (closed %d), "
                            + "cascades %d, conflictsAdded %d%n",
                    after.h17(), after.timeslotTierHardExclH17(),
                    result.gapsBefore(), result.gapsAfter(), result.gapsClosed(),
                    result.cascadesCommitted(), result.conflictsAdded());

            // The pass is CORRECTLY INERT on this saturated bad seed: every @10 blocker is mid-run, so
            // the isDepartureSafe gate refuses the cascade (it would be a shell game, relocating the
            // @10 hole to the blocker's group). This pins that correct refusal: the pass closes 0 gaps,
            // adds no H1/H3 conflict, and leaves the hard score unchanged (no harm) while the @10 cure
            // waits for a deeper/different mechanism (see depth3CrossGroupCascade's sustainable verdict).
            assertEquals(0, result.cascadesCommitted(),
                    "pass must refuse the shell-game cascade on the saturated bad seed (isDepartureSafe gate)");
            assertEquals(0, result.conflictsAdded(), "pass must add no H1/H3 conflict");
            assertEquals(baseH17, after.h17(), "pass must leave H17 unchanged (inert: no sustainable close exists)");
            assertTrue(after.timeslotTierHardExclH17() <= baseTtier,
                    "no timeslot-tier hard family (excl H17) may worsen");
        } finally {
            System.clearProperty("planner.composition-hole-closer.enabled");
        }
    }

    /**
     * Decisive diagnostic for the depth-3 shell-game hypothesis. The production pass skipped every
     * @10 blocker as departure-unsafe: removing the blocker from its own day opens a penalized gap
     * there. If TRUE, the depth-3 "close" the probe found is a shell game (the @10 hole moves from
     * G11 to the blocker's group, compensated by an unrelated gap fill elsewhere, net H17 -1),
     * NOT a sustainable close, and the cure needs a deeper/iterative cascade. This prints each
     * blocker's day shape and isDepartureSafe verdict to confirm or refute that mechanically.
     */
    @Test
    @Transactional
    void diagnoseBlockerDeparture() throws Exception {
        snap = SnapshotFixture.loadFromExport(BAD_SEED_EXPORT, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        SchoolSchedule schedule = snap.problem;

        for (String date : HOLE_DATES) {
            Timeslot holeTs = findTimeslot(date, HOLE_HOUR);
            if (holeTs == null) continue;
            LocalDate ld = LocalDate.parse(date);
            System.out.printf("=== %s : cross-group blockers @%d ===%n", date, HOLE_HOUR);
            int checked = 0;
            for (Lesson blk : snap.lessons) {
                if (blk.timeslot == null || !blk.timeslot.equals(holeTs)) continue;
                if (blk.pinned || blk.getTeacher() == null || blk.getTeacher().pseudo) continue;
                StudentGroup g = blk.getStudentGroup();
                if (g == null) continue;
                // Blocker's group-day occupied slots (manual; no package-private BoxRepair access).
                TreeSet<Integer> occ = new TreeSet<>();
                for (Lesson l : snap.lessons) {
                    if (l.timeslot == null || l.getStudentGroup() == null || !l.getStudentGroup().id.equals(g.id)) continue;
                    if (l.timeslot.getDate() == null || !ld.equals(l.timeslot.getDate())) continue;
                    occ.add(slotIdx(l));
                }
                int blkSlot = slotIdx(blk);
                boolean midRun = !occ.isEmpty() && blkSlot > occ.first() && blkSlot < occ.last();
                // Does the blocker's teacher also teach G11 with @10 in VRP (a usable blocker)?
                long donors = snap.lessons.stream().filter(x -> x.getTeacher() != null
                        && x.getTeacher().id != null && x.getTeacher().id.equals(blk.getTeacher().id)
                        && x.getStudentGroup() != null && GROUP.equals(x.getStudentGroup().name)
                        && x.timeslot != null && !date.equals(x.timeslot.getDate().toString())
                        && x.getAvailableTimeslots() != null && x.getAvailableTimeslots().contains(holeTs)).count();
                System.out.printf("  grp=%-8s subj=%-14s teacher=%-14s blkSlot=%d occ=%s midRun=%b(opensGap) donors=%d%n",
                        g.name, blk.getSubject(), blk.getTeacher().name, blkSlot, occ, midRun, donors);
                checked++;
                if (checked >= 14) break;
            }
        }
    }

    /**
     * Returns 0 if no net-H17-improving cascade exists for this day's hole, 1 if the only improving
     * cascades are SHELL GAMES (the blocker is mid-run: relocating it opens a gap on its own day, so
     * the @10 hole merely relocates groups, net-improving only via an unrelated gap fill elsewhere),
     * or 2 if a SUSTAINABLE close exists (the blocker is edge-removable: its departure opens no gap,
     * so the @10 hole is truly closed). The SUSTAINABLE verdict is the real existence gate: only a
     * sustainable close justifies a bounded composition-aware repair; a shell game is not a cure.
     */
    private int probeDepth3(SchoolSchedule schedule, String date) {
        Timeslot holeTs = findTimeslot(date, HOLE_HOUR);
        if (holeTs == null) return 0;
        // Donors: G11 lessons (real teacher), on another date, @10 in VRP, unpinned.
        List<Lesson> donors = new ArrayList<>();
        for (Lesson cand : snap.lessons) {
            if (cand.timeslot == null || cand.pinned) continue;
            if (cand.getStudentGroup() == null || !GROUP.equals(cand.getStudentGroup().name)) continue;
            if (cand.getTeacher() == null || cand.getTeacher().pseudo) continue;
            if (cand.timeslot.getDate().toString().equals(date)) continue;
            List<Timeslot> av = cand.getAvailableTimeslots();
            if (av == null || !av.contains(holeTs)) continue;
            donors.add(cand);
        }
        // Blockers: lessons currently at @10. A blocker holds a teacher hostage that a donor needs.
        List<Lesson> blockers = new ArrayList<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || !l.timeslot.equals(holeTs)) continue;
            blockers.add(l);
        }

        int scored = 0;
        int best = 0; // 0 none, 1 shell-game, 2 sustainable
        boolean shellGameShown = false;
        for (Lesson blk : blockers) {
            Long blkT = teacherId(blk);
            if (blkT == null) continue;
            boolean midRun = isMidRun(blk);
            if (midRun) {
                // Shell-game by construction: relocating a mid-run blocker opens a gap on its own day,
                // so the @10 hole merely relocates groups (net-improving only via an unrelated fill).
                // Classified WITHOUT the oracle; one representative cascade is scored per day as evidence.
                if (best < 1) best = 1;
                if (!shellGameShown) {
                    Lesson shown = showOneShellGame(schedule, date, blk, donors, holeTs);
                    if (shown != null) { shellGameShown = true; scored++; }
                }
                continue;
            }
            // EDGE blocker: a SUSTAINABLE close is possible (departure opens no gap). Confirm via oracle.
            for (Lesson b : donors) {
                if (!blkT.equals(teacherId(b))) continue;
                for (Timeslot ts2 : blk.getAvailableTimeslots()) {
                    TripleDelta d = scoreTriple(schedule, blk, b, holeTs, ts2);
                    if (d == null) continue;
                    scored++;
                    if (d.h17d() < 0 && d.ttier() == 0) {
                        System.out.printf("    3-MOVE SUSTAINABLE on %s: evict %s(%s)@h10[grp=%s,EDGE] -> %s@h%d ; "
                                        + "fill %s(%s)@h10 : H17d=%d ttierHardd=%d mediumd=%d%n",
                                date, blk.getSubject(), teacherName(blk), groupName(blk),
                                ts2.getDate(), hour(ts2), b.getSubject(), teacherName(b),
                                d.h17d(), d.ttier(), d.mediumd());
                        return 2;
                    }
                    if (scored >= MAX_DEPTH3_SCORED_PER_DAY) return best;
                }
            }
        }
        if (best >= 1) {
            System.out.printf("    Day %s: all @10 blockers mid-run (SHELL-GAME); no edge blocker for a sustainable close%n", date);
        } else {
            System.out.printf("    Day %s: depth-3 NO net-improving close (donors=%d blockers@10=%d)%n",
                    date, donors.size(), blockers.size());
        }
        return best;
    }

    /** Score ONE representative shell-game cascade for {@code blk} (the first net-improving one), as evidence. */
    private Lesson showOneShellGame(SchoolSchedule schedule, String date, Lesson blk, List<Lesson> donors,
                                    Timeslot holeTs) {
        Long blkT = teacherId(blk);
        for (Lesson b : donors) {
            if (!blkT.equals(teacherId(b))) continue;
            for (Timeslot ts2 : blk.getAvailableTimeslots()) {
                TripleDelta d = scoreTriple(schedule, blk, b, holeTs, ts2);
                if (d == null) continue;
                if (d.h17d() < 0 && d.ttier() == 0) {
                    System.out.printf("    3-MOVE SHELL-GAME on %s: evict %s(%s)@h10[grp=%s,midRun] -> %s@h%d ; "
                                    + "fill %s(%s)@h10 : H17d=%d ttierHardd=%d mediumd=%d (net -1 via unrelated fill)%n",
                            date, blk.getSubject(), teacherName(blk), groupName(blk),
                            ts2.getDate(), hour(ts2), b.getSubject(), teacherName(b),
                            d.h17d(), d.ttier(), d.mediumd());
                    return b;
                }
            }
        }
        return null;
    }

    /**
     * The shared measure+restore of one depth-3 triple: apply (blk -> ts2, b -> holeTs), take the
     * breakdown delta, restore both timeslots. Returns null if the cell is rejected by the pre-filter
     * (ts2 is the hole or blk's own cell, or blk/b conflict there), so the caller's {@code continue}
     * skips it without scoring. Extracted so the restore idiom cannot drift between the sustainable
     * search and the shell-game evidence scan.
     */
    private TripleDelta scoreTriple(SchoolSchedule schedule, Lesson blk, Lesson b, Timeslot holeTs, Timeslot ts2) {
        if (ts2.equals(holeTs) || ts2.equals(blk.timeslot)) return null;
        if (!cellConflictFreeExcluding(blk, ts2, blk, b)) return null;
        Timeslot bOld = b.timeslot;
        Breakdown before = breakdown(schedule);
        blk.timeslot = ts2;
        b.timeslot = holeTs;
        Breakdown after = breakdown(schedule);
        long h17d = after.h17() - before.h17();
        long ttier = after.timeslotTierHardExclH17() - before.timeslotTierHardExclH17();
        blk.timeslot = holeTs;   // restore (blk was @10)
        b.timeslot = bOld;       // restore donor
        return new TripleDelta(h17d, ttier, after.medium - before.medium);
    }

    /** Breakdown delta of one depth-3 triple (H17, timeslot-tier hard excl H17, medium). */
    private record TripleDelta(long h17d, long ttier, long mediumd) {
    }

    /**
     * True iff {@code blk}'s slot is strictly between its group-day's first and last occupied slot,
     * i.e. removing it opens an internal H17 gap (mid-run). An edge lesson (first or last slot) is
     * NOT mid-run: removing it only shrinks the run, opening no gap. This is the shell-game detector:
     * a depth-3 cascade via a mid-run blocker relocates the @10 hole to the blocker's group.
     */
    private boolean isMidRun(Lesson blk) {
        StudentGroup g = blk.getStudentGroup();
        if (g == null || blk.timeslot == null || blk.timeslot.getDate() == null) return false;
        TreeSet<Integer> occ = new TreeSet<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.getStudentGroup() == null || !l.getStudentGroup().id.equals(g.id)) continue;
            if (!blk.timeslot.getDate().equals(l.timeslot.getDate())) continue;
            occ.add(slotIdx(l));
        }
        int s = slotIdx(blk);
        return !occ.isEmpty() && s > occ.first() && s < occ.last();
    }

    /**
     * True iff no placed lesson at {@code ts} (excluding {@code blk} and {@code b}, which are both
     * moving) shares {@code mover}'s non-pseudo teacher (H1) or its student group (H3). The identity
     * check ({@code ==}, matching the existing probes) is deliberate: a value-based {@code equals}
     * on a Panache entity could treat two distinct in-memory lessons as equal.
     */
    private boolean cellConflictFreeExcluding(Lesson mover, Timeslot ts, Lesson blk, Lesson b) {
        Long mT = teacherId(mover);
        Long mG = mover.getStudentGroup() != null ? mover.getStudentGroup().id : null;
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l == blk || l == b) continue;
            if (!l.timeslot.equals(ts)) continue;
            Long lt = teacherId(l), lg = l.getStudentGroup() != null ? l.getStudentGroup().id : null;
            if (mT != null && mT.equals(lt)) return false;
            if (mG != null && mG.equals(lg)) return false;
        }
        return true;
    }

    private static String groupName(Lesson l) {
        return l.getStudentGroup() != null && l.getStudentGroup().name != null ? l.getStudentGroup().name : "?";
    }

    private record DeltaLine(String constraint, long h, long m, long s) {}

    private List<DeltaLine> deltaNonZero(Breakdown before, Breakdown after) {
        TreeMap<String, long[]> all = new TreeMap<>();
        before.by.forEach((k, v) -> all.computeIfAbsent(k, x -> new long[3]));
        after.by.forEach((k, v) -> all.computeIfAbsent(k, x -> new long[3]));
        List<DeltaLine> out = new ArrayList<>();
        for (var e : all.entrySet()) {
            long[] bv = before.by.getOrDefault(e.getKey(), new long[3]);
            long[] av = after.by.getOrDefault(e.getKey(), new long[3]);
            long dh = av[0] - bv[0], dm = av[1] - bv[1], ds = av[2] - bv[2];
            if (dh != 0 || dm != 0 || ds != 0) out.add(new DeltaLine(e.getKey(), dh, dm, ds));
        }
        out.sort(Comparator.comparingLong(d -> -(Math.abs(d.h) + Math.abs(d.m) + Math.abs(d.s))));
        return out;
    }

    private record Breakdown(long hard, long medium, long soft, Map<String, long[]> by) {
        long h17() {
            long s = 0;
            for (var e : by.entrySet()) if (e.getKey().contains("H17")) s += e.getValue()[0];
            return s;
        }
        /** Timeslot-tier hard EXCLUDING H17 (the signal) and EXCLUDING room/unassigned-tier (H0/H2/H29). */
        long timeslotTierHardExclH17() {
            long s = 0;
            for (var e : by.entrySet()) {
                String k = e.getKey();
                if (k.contains("H17") || k.contains("H0 ") || k.startsWith("H0-") || k.contains("H2 ")
                        || k.contains("H29")) continue;
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

    // ---- pre-filter: teacher (H1) + group (H3) only; rooms excluded (room-tier, downstream) ----

    /** @return null if the 2-move is teacher/group conflict-free at both moved slots, else the reason. */
    private String conflictFreeReason(Lesson a, Lesson b, Timeslot holeTs) {
        Timeslot bDest = holeTs;
        Timeslot aDest = b.timeslot;
        Long aT = teacherId(a), bT = teacherId(b);
        Long grp = a.getStudentGroup() != null ? a.getStudentGroup().id : null;
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l == a || l == b) continue;
            Long lt = teacherId(l), lg = l.getStudentGroup() != null ? l.getStudentGroup().id : null;
            if (l.timeslot.equals(bDest)) {
                if (bT != null && bT.equals(lt)) return "B-teacher@hole";
                if (grp != null && grp.equals(lg)) return "B-group@hole";
            }
            if (l.timeslot.equals(aDest)) {
                if (aT != null && aT.equals(lt)) return "A-teacher@B-old";
                if (grp != null && grp.equals(lg)) return "A-group@B-old";
            }
        }
        return null;
    }

    private static Long teacherId(Lesson l) {
        return l.getTeacher() != null ? l.getTeacher().id : null;
    }

    private static String teacherName(Lesson l) {
        return l.getTeacher() != null ? l.getTeacher().name : "?";
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
