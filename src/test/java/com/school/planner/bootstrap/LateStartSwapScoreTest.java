package com.school.planner.bootstrap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;

/**
 * TASK-450/451 follow-up: WHY does the solver not anchor G11's late-start Fridays? The Fridays
 * are exactly full (4-4 or 5-5) just shifted one slot right (9-13 vs 8-12); the fix is a cross-date
 * swap inside G11 that fills the empty Friday slot-0 with another day's tail and sends the
 * Friday tail to the freed slot. This harness measures the per-constraint SCORE DELTA of that move
 * on the committed cert, scored by the REAL engine (SolutionManager + ConstraintMatchPolicy), not
 * by hand.
 *
 * <p>The move is TWO changeMoves (NOT a swapMove): A (the Friday tail, e.g. T08 Matematica
 * 2026-11-20@12) moves to B's old slot (B-date@B-hour); B (the partner tail, e.g. T04
 * 2027-02-15@13) moves to A-date@8 (the empty Friday slot-0). There is no lesson at A-date@8 to
 * swap with, so a swapMove cannot represent this fix; the greedy LS would have to take a 2-move
 * sequence through a worse/illegal intermediate. That algorithmic angle is separate from the price
 * delta this harness measures.
 *
 * <p>Loads the cert EXPORT (real placements) via {@link SnapshotFixture#loadFromExport}, seeds the
 * medium-tier facts ({@link SnapshotFixture#seedMediumTierFacts}: quota + expected-hours + the M32
 * template via {@code WeeklyTemplatePlanner.buildReference}), then scores BEFORE, applies the two
 * changeMoves, scores AFTER, and prints the per-constraint DELTA plus each moved lesson's template-
 * cell status ({@link SchoolSchedule#isOnTemplateCell}). Default OFF; opt-in via
 * {@code PLANNER_TEST_LATE_SWAP=1}. Verdict class: TREND on one trajectory, never adoption.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_LATE_SWAP", matches = ".+")
class LateStartSwapScoreTest {

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;
    @Inject jakarta.persistence.EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    @Transactional
    void scoreSwapDelta() throws Exception {
        Path exportPath = Path.of(System.getenv("PLANNER_TEST_LATE_SWAP_EXPORT"));
        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadFromExport(exportPath, availabilityExpansionService, true);
        SchoolSchedule schedule = snap.problem;
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);

        // A-date = the late-start day (Friday). lessonA = its TAIL (last lesson by slot, moves OUT
        // to the anchor's old slot). lessonB = a partner from another day that can legally sit at
        // A-date@8 (slot0). SCANNED, not hardcoded: the concrete candidate Fedora named (T04)
        // turned out OUTSIDE the value range for slot0, so we find one that is actually in-range.
        String aDate = System.getenv().getOrDefault("PLANNER_TEST_LATE_SWAP_ADATE", "2026-11-20");
        Timeslot slot0 = findTimeslot(snap.timeslots, aDate, 8);
        org.junit.jupiter.api.Assertions.assertNotNull(slot0, "no 08:00 timeslot for " + aDate);
        List<Lesson> onA = snap.lessons.stream()
                .filter(l -> l.timeslot != null && l.getStudentGroup() != null
                        && "G11".equals(l.getStudentGroup().name)
                        && l.timeslot.getDate().toString().equals(aDate))
                .sorted(Comparator.comparingInt(l -> slotIdx(l.timeslot)))
                .toList();
        org.junit.jupiter.api.Assertions.assertFalse(onA.isEmpty(), "no G11 lessons on " + aDate);
        Lesson lessonA = onA.get(onA.size() - 1); // tail

        System.out.println("=== late-start anchor delta (cert " + exportPath + ") ===");
        System.out.printf("  A-date %s: %d lessons, slots %s; tail A=%s [%s] @slot%d%n", aDate, onA.size(),
                onA.stream().map(l -> String.valueOf(slotIdx(l.timeslot))).toList(),
                label(lessonA), lessonA.getSubject(), slotIdx(lessonA.timeslot));

        // SCAN G11 lessons for an anchor: (1) available at slot0, (2) other date, real teacher,
        // not pinned; fully-legal also requires lessonA available at the anchor's old slot;
        // conflict-free adds no H1/H2/H3 clash at either moved slot (the cheap pre-filter that
        // isolates the pure M32/M18/S13 price from the hard-conflict block).
        List<Lesson> canAnchor = new ArrayList<>();
        List<Lesson> fullyLegal = new ArrayList<>();
        List<Lesson> conflictFree = new ArrayList<>();
        for (Lesson cand : snap.lessons) {
            if (cand.timeslot == null || cand == lessonA || cand.pinned) continue;
            if (cand.getStudentGroup() == null || !"G11".equals(cand.getStudentGroup().name)) continue;
            if (cand.getTeacher() == null || cand.getTeacher().pseudo) continue;
            if (cand.timeslot.getDate().toString().equals(aDate)) continue;
            List<Timeslot> av = cand.getAvailableTimeslots();
            if (av == null || !av.contains(slot0)) continue;
            canAnchor.add(cand);
            if (!lessonA.getAvailableTimeslots().contains(cand.timeslot)) continue;
            fullyLegal.add(cand);
            if (conflictFree(snap.lessons, lessonA, cand, slot0)) conflictFree.add(cand);
        }
        System.out.printf("  candidates AVAILABLE at slot0=%d ; FULLY-legal=%d ; CONFLICT-FREE=%d%n",
                canAnchor.size(), fullyLegal.size(), conflictFree.size());
        if (!conflictFree.isEmpty()) {
            System.out.println("  conflict-free sample: " + conflictFree.stream().limit(6).map(
                    l -> l.getSubject() + "/" + l.timeslot.getDate() + "/s" + slotIdx(l.timeslot)).toList());
        }
        if (conflictFree.isEmpty()) {
            System.out.println("  => NO conflict-free anchor (canAnchor=" + canAnchor.size() + " reach slot0, "
                    + fullyLegal.size() + " value-range-legal, but ALL clash on room/teacher at slot0 or A's dest). "
                    + "The Friday cannot be anchored at slot0 without a HARD conflict: the block is room/teacher "
                    + "CAPACITY, not the M32 price. (Measuring the first value-range-legal candidate below to show "
                    + "the hard-explosion, not the price.)");
        }

        // Measure the first CONFLICT-FREE anchor (pure price); fall back to the first value-range-
        // legal one (shows the hard block) only if none is conflict-free.
        Lesson lessonB = !conflictFree.isEmpty() ? conflictFree.get(0) : fullyLegal.get(0);
        Timeslot bOldSlot = lessonB.timeslot;
        System.out.printf("  USING anchor B=%s [%s @%s tsId=%d slot%d] -> A-date slot0 ; A -> B's slot%d%n",
                label(lessonB), lessonB.getSubject(), lessonB.timeslot.getDate(), lessonB.timeslot.id,
                slotIdx(lessonB.timeslot), slotIdx(bOldSlot));

        Breakdown before = breakdown(schedule);
        System.out.printf("  BEFORE total: hard=%d medium=%d soft=%d%n",
                before.hard, before.medium, before.soft);
        System.out.printf("    A onTemplateCell=%s  B onTemplateCell=%s%n",
                SchoolSchedule.isOnTemplateCell(lessonA.course.id, lessonA.timeslot),
                SchoolSchedule.isOnTemplateCell(lessonB.course.id, lessonB.timeslot));

        // --- apply the two changeMoves (B -> A-date slot0; A -> B's old slot) ---
        Timeslot aOldSlot = lessonA.timeslot;
        lessonB.timeslot = slot0;
        lessonA.timeslot = bOldSlot;

        Breakdown after = breakdown(schedule);
        System.out.printf("  AFTER  total: hard=%d medium=%d soft=%d%n",
                after.hard, after.medium, after.soft);
        System.out.printf("    A onTemplateCell=%s  B onTemplateCell=%s%n",
                SchoolSchedule.isOnTemplateCell(lessonA.course.id, lessonA.timeslot),
                SchoolSchedule.isOnTemplateCell(lessonB.course.id, lessonB.timeslot));

        // --- per-constraint DELTA (only constraints that moved) ---
        System.out.printf("  %-46s %12s %12s %12s%n", "constraint (delta after-before)", "hard", "medium", "soft");
        TreeMap<String, long[]> all = new TreeMap<>();
        before.byConstraint.forEach((k, v) -> all.computeIfAbsent(k, x -> new long[3]));
        after.byConstraint.forEach((k, v) -> all.computeIfAbsent(k, x -> new long[3]));
        for (var e : all.entrySet()) {
            long[] bv = before.byConstraint.getOrDefault(e.getKey(), new long[3]);
            long[] av = after.byConstraint.getOrDefault(e.getKey(), new long[3]);
            long dh = av[0] - bv[0], dm = av[1] - bv[1], ds = av[2] - bv[2];
            e.getValue()[0] = dh; e.getValue()[1] = dm; e.getValue()[2] = ds;
        }
        all.values().removeIf(v -> v[0] == 0 && v[1] == 0 && v[2] == 0);
        all.entrySet().stream()
                .sorted(Comparator.comparingLong(
                        (Map.Entry<String, long[]> e) -> Math.abs(e.getValue()[0]) + Math.abs(e.getValue()[1]) + Math.abs(e.getValue()[2]))
                        .reversed())
                .forEach(e -> System.out.printf("  %-46s %12d %12d %12d%n",
                        e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]));
        System.out.printf("  DELTA total: hard=%d medium=%d soft=%d%n",
                after.hard - before.hard, after.medium - before.medium, after.soft - before.soft);
        System.out.println("  (snapshot fixes ONE trajectory, TREND only, never adoption)");

        // restore (defensive; test is single-shot but keeps the schedule honest for any later assertion)
        lessonA.timeslot = aOldSlot;
        lessonB.timeslot = bOldSlot;
    }

    private static String label(Lesson l) {
        return l.getStudentGroup() != null ? l.getStudentGroup().name : "?";
    }

    private static int slotIdx(Timeslot t) {
        return com.school.planner.solver.TimeslotSequence.slotIndexOf(t);
    }

    private static Lesson findLesson(List<Lesson> lessons, Spec s) {
        for (Lesson l : lessons) {
            if (l.timeslot == null || l.getStudentGroup() == null) continue;
            if (!"G11".equals(l.getStudentGroup().name)) continue;
            if (!l.timeslot.getDate().toString().equals(s.date)) continue;
            if (hour(l.timeslot) != s.hour) continue;
            return l;
        }
        return null;
    }

    private static Timeslot findTimeslot(List<Timeslot> timeslots, String date, int hour) {
        for (Timeslot t : timeslots) {
            if (t.getDate() != null && t.getDate().toString().equals(date) && hour(t) == hour) return t;
        }
        return null;
    }

    /** Cheap pre-filter: would the swap (B -> slot0, A -> B's old slot) create any H1/H2/H3 clash at
     *  either moved slot? B carries its teacher+room to slot0; A carries its teacher+room to B's
     *  old slot (which B has vacated). Returns true if NO clash -> the swap's only cost is the
     *  medium/soft price (M32/M18/S13...), isolating Fedora's hypothesis from the hard block. */
    private static boolean conflictFree(List<Lesson> lessons, Lesson a, Lesson b, Timeslot slot0) {
        Timeslot bDest = slot0;
        Timeslot aDest = b.timeslot;
        Long aT = teacherId(a), bT = teacherId(b);
        com.school.planner.domain.Room aRoom = a.room, bRoom = b.room;
        Long grp = a.getStudentGroup() != null ? a.getStudentGroup().id : null;
        for (Lesson l : lessons) {
            if (l.timeslot == null || l == a || l == b) continue;
            Long lt = teacherId(l), lg = l.getStudentGroup() != null ? l.getStudentGroup().id : null;
            if (l.timeslot.equals(bDest)) {
                if (bT != null && bT.equals(lt)) return false;
                if (bRoom != null && bRoom.equals(l.room)) return false;
                if (grp != null && grp.equals(lg)) return false;
            }
            if (l.timeslot.equals(aDest)) {
                if (aT != null && aT.equals(lt)) return false;
                if (aRoom != null && aRoom.equals(l.room)) return false;
                if (grp != null && grp.equals(lg)) return false;
            }
        }
        return true;
    }

    private static Long teacherId(Lesson l) {
        return l.getTeacher() != null ? l.getTeacher().id : null;
    }

    private static int hour(Timeslot t) {
        return t.startDateTime.toLocalTime().getHour();
    }

    /** Per-constraint hard/medium/soft: solutionManager.update for the total, raw ScoreDirector
     *  (ConstraintMatchPolicy.ENABLED) for the per-constraint map (same pattern as ScoreASnapshotTest). */
    private Breakdown breakdown(SchoolSchedule schedule) {
        solutionManager.update(schedule);
        BendableScore total = (BendableScore) schedule.getScore();
        var factory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager)
                .getScoreDirectorFactory();
        Map<String, long[]> by = new java.util.HashMap<>();
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

    private record Spec(String date, int hour) {
        static Spec parse(String s) {
            String[] p = s.split("@");
            return new Spec(p[0].trim(), Integer.parseInt(p[1].trim()));
        }
    }

    private record Breakdown(long hard, long medium, long soft, Map<String, long[]> byConstraint) {
    }
}
