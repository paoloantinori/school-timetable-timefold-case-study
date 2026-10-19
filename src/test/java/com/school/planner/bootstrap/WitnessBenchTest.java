package com.school.planner.bootstrap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.solver.SolutionManager;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.H28RotationFinisher;
import com.school.planner.solver.TimeslotSequence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-593 WITNESS BENCH (m-14): legal-repair search for the CURRENT delivered
 * residual on the frozen delivery fixtures (content-addressed, id-independent:
 * lesson-id seeding is NOT deterministic cross-machine, TASK-588).
 *
 * <p>Fixture A = the slotretry-9sep delivery (derived from its export: 12509
 * placed, the G14 Confezione/T16 lesson deliberately unplaced - the
 * ledger's H0 unit). Fixture B = the v132-7sep delivery (12510 placed - that
 * same lesson IS placed there: the existence witness). Both load through
 * content keys (group|subject|teacher|date|startTime) and must reproduce their
 * provider hard vectors EXACTLY at load (entry gate; a mismatch is a harness
 * failure, not a solver diagnosis).
 *
 * <p>Per-unit searches, cheapest first; every accepted witness must improve its
 * target family with NO regression in any other hard family (provider recount
 * is the oracle). Bounds are recorded with each verdict; failure at a bound is
 * LOCAL_NO_WITNESS, never global infeasibility.
 *
 * <p>Gated by {@code PLANNER_TEST_WITNESS_BENCH=1}.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_WITNESS_BENCH", matches = ".+")
class WitnessBenchTest {

    private static final String FIXTURE_A = "claudedocs/spalmone-2026-27/task593-results/fixture-content-slotretry.txt";
    private static final String FIXTURE_B = "claudedocs/spalmone-2026-27/task593-results/fixture-content-v132.txt";
    /** recount cap per unit: the provider recount is the legality oracle, each run rescores ~12.5k lessons. */
    private static final int RECOUNT_CAP = 56;
    /** Every REACHABLE hard family (the CLAUDE.md canonical table minus H5/H6/
     * H4.3, structurally unreachable here: the VRP filter excludes
     * VACATION/SICK slots, holiday dates carry no timeslots, H4.3 is inert on
     * this seed). The acceptance claim "no regression in any hard family" is
     * only honest against the full set - the first 6-family oracle accepted
     * H11-violating placements as witnesses. */
    private static final String[] FAMILIES = {"H0", "H1", "H2", "H3", "H4", "H7", "H11", "H14",
            "H17", "H20", "H23", "H24", "H25", "H26", "H28", "H29", "H30", "H31",
            "H32", "H33", "H34", "H-M2", "H-COPRESENCE"};

    @Inject EntityManager em;
    @Inject com.school.planner.service.LessonGenerator lessonGenerator;
    @Inject DataGenerator dataGenerator;
    @Inject com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;
    @Inject com.school.planner.service.SubjectRegistry subjectRegistry;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    private int recounts;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
        System.clearProperty("planner.construction.pool-cap-fill.enabled");
        System.clearProperty(H28RotationFinisher.FLAG);
    }

    @Test
    @Timeout(value = 3600)
    // NO @Transactional on the method (the PinDrainBench JTA-timeout lesson)
    void witnessBench() {
        System.out.println("=== TASK-593 WITNESS BENCH (delivery fixtures, provider-oracle moves) ===");
        seedRealSeedFixture();
        var wires = wireStatics();

        // ── entry gates: both fixtures must reproduce their delivered vectors ──
        loadContentFixture(FIXTURE_A);
        publishPinnedStatic();
        List<Lesson> state = freshLessons();
        var a0 = recount(wires, state);
        System.out.printf("FIXTURE A load-verify: %s%n", a0);
        // hard POINTS semantics (H0 weighs 2/match: 1 unplaced lesson = 2 points)
        assertVec(a0, 4, 3, 3, 2, 1, 0, "fixture A must reproduce the slotretry delivery [-13/0]");

        // ── unit 1: H0 (lesson 8244 family: G14 Confezione T16 unplaced) ──
        Map<String, Integer> running = h0G14Transplant(wires, state, a0);

        // ── unit 2: H28 (T03/T22 A..B..A on 2026-09-24 and 2026-10-01) ──
        running = h28DayReorder(wires, state, running);

        // ── unit 3: G12 2027-04-16 (census NEVER_ATTEMPTED, slot 13 free) ──
        running = donorImport(wires, state, running, "G12", "2027-04-16", "H34");

        // ── units 4-6: the remaining cells, same depth-1 donor-import shape ──
        // June H34 cells (census: allowance-dead; the census's own alternatives
        // name Gestione aziendale/Servizio stilistico for G06 and Educazione
        // Civica for G09), G14 H33 Fridays, G13 H32
        running = donorImport(wires, state, running, "G06", "2027-06-10", "H34");
        running = donorImport(wires, state, running, "G09", "2027-06-03", "H34");
        running = donorImport(wires, state, running, "G14", "2027-01-29", "H33");
        running = donorImport(wires, state, running, "G14", "2027-02-05", "H33");
        running = donorImport(wires, state, running, "G14", "2027-03-05", "H33");
        running = donorImport(wires, state, running, "G13", "2026-11-06", "H32");

        // ── DEPTH-2 PHASE: the recorded escalations from the depth-1 negatives ──
        // H0 compound: move the conflicting blocker lesson first, then place the
        // G14 Confezione lesson in the freed slot (the report's escalation)
        int d2Budget = recounts + DEPTH2_BUDGET;
        running = h0Depth2Compound(wires, state, running, d2Budget);
        // June cell compound: import a donor hour into the empty declared day,
        // then patch the donor's day from a surplus date (import + give-back)
        running = juneDepth2Swap(wires, state, running, "G06", "2027-06-10", d2Budget);
        running = juneDepth2Swap(wires, state, running, "G09", "2027-06-03", d2Budget);

        // ── fixture B gate + cross-fixture notes ──
        reloadFixture(FIXTURE_B);
        publishPinnedStatic();
        state = freshLessons();
        var b0 = recount(wires, state);
        System.out.printf("FIXTURE B load-verify: %s%n", b0);
        assertVec(b0, 0, 2, 3, 0, 0, 1, "fixture B must reproduce the v132 delivery [-6/0]");
        // full-family zero check: the delivery must be CLEAN on every other hard family
        for (var e : b0.entrySet()) {
            if (!List.of("H28", "H33", "H34", "H0", "H32", "H29").contains(e.getKey())) {
                assertEquals(0, e.getValue(), "fixture B clean family regressed: " + e.getKey());
            }
        }
        // the H0 transplant's origin IS fixture B (v132 places the same lesson):
        // show where, as the recorded witness slot
        fixtureBG14ConfezioneOrigin();
        // AC#5 second-fixture replay: the G14 donor-import PATTERN on v132's own
        // deficient Fridays (03-12, 04-30) - the witness mechanism, not the same cells
        int replayBudget = recounts + REPLAY_BUDGET;
        assert replayBudget >= 0;
        Map<String, Integer> bRun = b0;
        bRun = donorImportB(wires, state, bRun, "G14", "2027-03-12", "H33", replayBudget);
        bRun = donorImportB(wires, state, bRun, "G14", "2027-04-30", "H33", replayBudget);
        System.out.printf("%nTOTAL provider recounts used: %d (cap %d + replay %d + depth2 %d)%n",
                recounts, RECOUNT_CAP, REPLAY_BUDGET, DEPTH2_BUDGET);
        assertTrue(recounts <= RECOUNT_CAP + REPLAY_BUDGET + DEPTH2_BUDGET,
                "bound: each phase within its reserved line");
    }

    /**
     * TASK-595 near-delivery gate for the H28 rotation finisher, a SEPARATE test from
     * {@link #witnessBench()} (whose units mutate state for ~10 minutes before its
     * tail): iterating on the finisher must not pay for the whole bench. Fixture A
     * loaded pristine must reproduce the delivered vector, then the finisher must
     * improve H28 with no regression on any of the 23 families; fixture B (H28=0) must
     * read inert with no family moving at all.
     */
    @Test
    @Timeout(value = 1800)
    // NO @Transactional on the method (the PinDrainBench JTA-timeout lesson)
    void h28FinisherGate() {
        System.out.println("=== TASK-595 H28 FINISHER GATE (delivery fixtures) ===");
        seedRealSeedFixture();
        var wires = wireStatics();

        reloadFixture(FIXTURE_A);
        publishPinnedStatic();
        List<Lesson> state = freshLessons();
        var a0 = recount(wires, state);
        System.out.printf("FIXTURE A load-verify: %s%n", a0);
        assertVec(a0, 4, 3, 3, 2, 1, 0, "fixture A must reproduce the slotretry delivery [-13/0]");

        var fin = runFinisherEnabled(wires, state);
        var a1 = recount(wires, state);
        System.out.printf("H28 FINISHER on fixture A: %s%n  before=%s%n  after =%s%n", fin, a0, a1);
        assertTrue(a1.getOrDefault("H28", -1) < a0.getOrDefault("H28", -1),
                "the finisher must improve H28 on the delivery");
        for (var e : a1.entrySet()) {
            assertTrue(e.getValue() <= a0.getOrDefault(e.getKey(), 0),
                    "no hard family may regress: " + e.getKey());
        }
        assertTrue(a1.get("H28") <= 2,
                "witness floor: at least the 10-01 day closes (09-24 may need its compound)");

        reloadFixture(FIXTURE_B);
        publishPinnedStatic();
        state = freshLessons();
        var b0 = recount(wires, state);
        assertEquals(0, runFinisherEnabled(wires, state).cellsViolating(),
                "fixture B (H28=0): the pass must be inert");
        assertEquals(b0, recount(wires, state), "fixture B: no family may move at all");
        assertTrue(recounts <= FINISHER_BUDGET, "dedicated budget: reload+post per fixture");
        System.out.printf("H28 FINISHER GATE: recounts=%d (budget %d)%n", recounts, FINISHER_BUDGET);
    }

    /** The pass under its own flag, flag restored either way (clean() also guards). */
    private H28RotationFinisher.Result runFinisherEnabled(Wires wires, List<Lesson> state) {
        System.setProperty(H28RotationFinisher.FLAG, "true");
        try {
            return H28RotationFinisher.repair(problem(wires, state),
                    (lesson, ts) -> lesson.timeslot = ts);
        } finally {
            System.clearProperty(H28RotationFinisher.FLAG);
        }
    }

    // ────────────────────────── units ──────────────────────────

    /** Unit 1: place the unplaced G14 Confezione/T16 lesson. Candidates:
     *  every G14 calendar-open in-grid timeslot (bounded by the recount cap),
     *  cheapest-first = the v132 origin slot if legal here. */
    private Map<String, Integer> h0G14Transplant(Wires w, List<Lesson> all, Map<String, Integer> baseline) {
        Lesson target = all.stream()
                .filter(l -> l.timeslot == null && "G14".equals(groupName(l))
                        && "Confezione".equals(subjectName(l)) && "T16".equals(teacherName(l)))
                .findFirst().orElse(null);
        if (target == null) {
            System.out.println("UNIT H0: no unplaced G14 Confezione/T16 lesson found (fixture drift?)");
            return baseline;
        }
        // candidate slots: G14 lessons' own dates first (the programme's shape),
        // then any in-grid open slot for the group; each candidate = apply+recount+revert
        List<Timeslot> candidates = candidateSlotsFor(target, w, all);
        // rooms to try per slot: the v132-origin rooms first (fixture B shows the
        // Confezione practice in R06/10/4/8...), then any room; H29 fires on a
        // roomless placement, so the witness carries BOTH slot and room
        List<Room> rooms = Room.listAll();
        List<Room> roomOrder = new ArrayList<>();
        for (String name : new String[]{"R06", "R09", "R03", "R07"}) {
            for (Room r : rooms) {
                if (r.name.equals(name)) roomOrder.add(r);
            }
        }
        for (Room r : rooms) {
            if (!r.isAssignable()) continue; // TASK-456 rule (R18 cap 0)
            if (!roomOrder.contains(r)) roomOrder.add(r);
        }
        int tried = 0;
        for (Timeslot ts : candidates) {
            if (tried >= 12 || recounts >= RECOUNT_CAP - 12) break; // bound: 12 slot-room pairs
            for (Room room : roomOrder) {
                target.timeslot = ts;
                target.room = room;
                var v = recount(w, all);
                tried++;
                if (v.getOrDefault("H0", 99) == 0 && v.getOrDefault("H29", 99) == 0
                        && noRegression(baseline, v)) {
                    System.out.printf("UNIT H0: WITNESS - lesson %d at %s %s room=%s (tried=%d): %s%n",
                            target.id, ts.getDate(), ts.startDateTime.toLocalTime(), room.name, tried, v);
                    return v; // improved vector carried forward (state mutates in place)
                }
                if (!v.isEmpty() && !v.equals(baseline)) {
                    System.out.printf("UNIT H0: candidate %s %s room=%s regressed: %s%n",
                            ts.getDate(), ts.startDateTime.toLocalTime(), room.name, v);
                }
                target.timeslot = null;
                target.room = null;
                if (recounts >= RECOUNT_CAP - 10) break;
            }
        }
        System.out.printf("UNIT H0: LOCAL_NO_WITNESS at bound (tried=%d slot-room pairs)%n", tried);
        return baseline;
    }

    /** Unit 2: the A..B..A day reorder. The day is B,B,T,T,T,B (slots 8-13); the
     *  witness candidate is the rotation making B,B,B,T,T,T (4-lesson cyclic
     *  shift, day stays fully packed = H17-safe by construction). */
    private Map<String, Integer> h28DayReorder(Wires w, List<Lesson> all, Map<String, Integer> current) {
        for (String date : new String[]{"2026-09-24", "2026-10-01"}) {
            List<Lesson> day = new ArrayList<>();
            for (Lesson l : all) {
                if (l.timeslot != null && date.equals(l.timeslot.getDate().toString())
                        && "G03".equals(groupName(l))
                        && "Laboratorio Termoidraulico".equals(subjectName(l))) {
                    day.add(l);
                }
            }
            day.sort((x, y) -> Long.compare(x.timeslot.startDateTime.toLocalTime().toSecondOfDay(),
                    y.timeslot.startDateTime.toLocalTime().toSecondOfDay()));
            if (day.size() != 6) {
                System.out.printf("UNIT H28 %s: unexpected day shape (%d lessons), skipping%n", date, day.size());
                continue;
            }
            // rotation: the 13:00 T03 (index 5) to slot of index 2; T lessons 2,3,4 shift right
            List<Timeslot> slots = new ArrayList<>();
            for (Lesson l : day) slots.add(l.timeslot);
            var T03Last = day.get(5);
            if (!"T03".equals(teacherName(T03Last))) {
                System.out.printf("UNIT H28 %s: shape drift (last is %s not T03)%n", date, teacherName(T03Last));
                continue;
            }
            // the 4-lesson rotation on the OBJECTS: B@13 -> slot10, T lessons shift right
            Timeslot t10 = day.get(2).timeslot, t11 = day.get(3).timeslot, t12 = day.get(4).timeslot, b13 = day.get(5).timeslot;
            day.get(5).timeslot = t10;
            day.get(4).timeslot = b13;
            day.get(3).timeslot = t12;
            day.get(2).timeslot = t11;
            var v = recount(w, all);
            if (v.getOrDefault("H28", 99) < current.getOrDefault("H28", 99)
                    && noRegression(current, v)) {
                System.out.printf("UNIT H28 %s: WITNESS - 4-lesson rotation (B@13->10, T shift): %s%n",
                        date, v);
                current = new HashMap<>(v); // the day's improvement carries to the next day
            } else {
                System.out.printf("UNIT H28 %s: rotation REJECTED by provider (%s) - reverting%n", date, v);
                day.get(2).timeslot = t10;
                day.get(3).timeslot = t11;
                day.get(4).timeslot = t12;
                day.get(5).timeslot = b13;
            }
        }
        return current;
    }

    /** Units 4-6: depth-1 donor import for a deficit cell - move ONE lesson of
     *  the target group from another date into the cell's free slots; accept if
     *  the family improves with no regression anywhere (the provider is the
     *  oracle; a move that opens a hole at the donor is rejected and depth-2
     *  give-back remains the recorded unexplored escalation). */
    private Map<String, Integer> donorImport(Wires w, List<Lesson> all,
            Map<String, Integer> baseline, String group, String date, String family) {
        return donorImportB(w, all, baseline, group, date, family, RECOUNT_CAP - 2);
    }

    private Map<String, Integer> donorImportB(Wires w, List<Lesson> all,
            Map<String, Integer> baseline, String group, String date, String family, int budget) {
        List<Timeslot> free = new ArrayList<>();
        java.util.Set<Long> occupied = new java.util.HashSet<>();
        for (Lesson l : all) {
            if (l.timeslot != null && date.equals(l.timeslot.getDate().toString())
                    && group.equals(groupName(l))) {
                occupied.add(l.timeslot.id);
            }
        }
        for (Timeslot ts : w.timeslots()) {
            if (date.equals(ts.getDate().toString()) && !occupied.contains(ts.id)) free.add(ts);
        }
        if (free.isEmpty()) {
            System.out.printf("UNIT %s %s: no free slot on the date (cell already full?)%n", group, date);
            return baseline;
        }
        List<Lesson> donors = new ArrayList<>();
        for (Lesson l : all) {
            if (l.timeslot != null && !date.equals(l.timeslot.getDate().toString())
                    && group.equals(groupName(l)) && !l.pinned) donors.add(l);
        }
        int tried = 0;
        for (Timeslot ts : free) {
            if (tried >= 6 || recounts >= budget) break;
            for (Lesson pick : donors) {
                if (tried >= 6 || recounts >= budget) break;
                if (!pick.isSlotLegalFresh(ts)) continue; // VRP-illegal (TASK-578 O(1))
                Timeslot back = pick.timeslot;
                pick.timeslot = ts;
                var v = recount(w, all);
                tried++;
                if (v.getOrDefault(family, 99) < baseline.getOrDefault(family, 99)
                        && noRegression(baseline, v)) {
                    System.out.printf("UNIT %s %s [%s]: WITNESS - move lesson %d (%s/%s) from %s to %s: %s%n",
                            group, date, family, pick.id, subjectName(pick), teacherName(pick),
                            back.getDate(), ts.startDateTime, v);
                    return new HashMap<>(v); // the improved vector is the new baseline
                }
                pick.timeslot = back;
            }
        }
        System.out.printf("UNIT %s %s [%s]: LOCAL_NO_WITNESS at depth 1 (tried=%d donor moves)%n",
                group, date, family, tried);
        return baseline;
    }


    /** DEPTH-2 for H0: for each VRP-legal candidate slot of the unplaced
     *  G14 lesson, move the occupying blocker lesson (any group, unpinned)
     *  to one of ITS legal alternatives first, then place the target in the
     *  freed slot. Two-move compound, full-oracle verified. */
    private Map<String, Integer> h0Depth2Compound(Wires w, List<Lesson> all,
            Map<String, Integer> baseline, int budget) {
        Lesson target = all.stream()
                .filter(l -> l.timeslot == null && "G14".equals(groupName(l))
                        && "Confezione".equals(subjectName(l)) && "T16".equals(teacherName(l)))
                .findFirst().orElse(null);
        if (target == null) {
            System.out.println("D2-H0: target already placed (depth-1 witness carried)?");
            return baseline;
        }
        // candidate slots: G14-active dates first (the programme shape)
        List<Timeslot> candidates = candidateSlotsFor(target, w, all);
        int tried = 0;
        for (Timeslot slot : candidates) {
            if (tried >= 10 || recounts >= budget - 1) break;
            // blockers: lessons of OTHER groups occupying the slot (the H1/H2
            // conflicts the depth-1 run hit); unpinned only
            List<Lesson> blockers = new ArrayList<>();
            for (Lesson l : all) {
                if (l.timeslot != null && java.util.Objects.equals(l.timeslot.id, slot.id)
                        && !"G14".equals(groupName(l)) && !l.pinned) {
                    blockers.add(l);
                }
            }
            for (Lesson blocker : blockers) {
                if (tried >= 10 || recounts >= budget - 1) break;
                // the blocker's own legal alternatives (its group's free cells)
                List<Timeslot> alts = candidateSlotsFor(blocker, w, all);
                int altTries = 0;
                for (Timeslot alt : alts) {
                    if (altTries >= 3 || recounts >= budget - 1) break;
                    if (!blocker.isSlotLegalFresh(alt)) continue;
                    Room free = firstFreeRoomAt(all, slot);
                    if (free == null) break; // slot's rooms all taken: no H29-legal placement
                    Timeslot backB = blocker.timeslot;
                    blocker.timeslot = alt;
                    target.timeslot = slot;
                    target.room = free;
                    var v = recount(w, all);
                    tried++;
                    altTries++;
                    if (v.getOrDefault("H0", 99) < baseline.getOrDefault("H0", 99)
                            && noRegression(baseline, v)) {
                        System.out.printf("D2-H0: WITNESS - blocker %d (%s/%s) %s -> %s, target -> %s: %s%n",
                                blocker.id, subjectName(blocker), teacherName(blocker),
                                backB.getDate() + "T" + backB.startDateTime.toLocalTime(),
                                alt.getDate() + "T" + alt.startDateTime.toLocalTime(),
                                slot.getDate() + "T" + slot.startDateTime.toLocalTime(), v);
                        return new HashMap<>(v);
                    }
                    blocker.timeslot = backB;
                    target.timeslot = null;
                }
            }
        }
        System.out.printf("D2-H0: LOCAL_NO_WITNESS at depth 2 (tried=%d compounds)%n", tried);
        return h0Depth3Chain(w, all, baseline, budget);
    }

    /** DEPTH-3 for H0: the recorded escalation of the depth-2 negative. When the
     *  blocker's move to its alternative itself trips a conflict, free the
     *  ALTERNATIVE's own occupant first (any group, unpinned, to one of ITS
     *  legal cells), then blocker -> alt, then target -> slot. Three-move chain,
     *  full-oracle verified, bounded. */
    private Map<String, Integer> h0Depth3Chain(Wires w, List<Lesson> all,
            Map<String, Integer> baseline, int budget) {
        Lesson target = all.stream()
                .filter(l -> l.timeslot == null && "G14".equals(groupName(l))
                        && "Confezione".equals(subjectName(l)) && "T16".equals(teacherName(l)))
                .findFirst().orElse(null);
        if (target == null) {
            return baseline;
        }
        List<Timeslot> candidates = candidateSlotsFor(target, w, all);
        int tried = 0;
        for (Timeslot slot : candidates) {
            if (tried >= 10 || recounts >= budget - 1) break;
            for (Lesson blocker : all) {
                if (tried >= 10 || recounts >= budget - 1) break;
                if (blocker.timeslot == null || !java.util.Objects.equals(blocker.timeslot.id, slot.id)
                        || "G14".equals(groupName(blocker)) || blocker.pinned) {
                    continue;
                }
                for (Timeslot alt : candidateSlotsFor(blocker, w, all)) {
                    if (tried >= 10 || recounts >= budget - 1) break;
                    if (!blocker.isSlotLegalFresh(alt)) continue;
                    // who occupies the blocker's destination?
                    for (Lesson occupant : all) {
                        if (tried >= 10 || recounts >= budget - 1) break;
                        if (occupant.timeslot == null || !java.util.Objects.equals(occupant.timeslot.id, alt.id)
                                || occupant == blocker || occupant.pinned) {
                            continue;
                        }
                        for (Timeslot free : candidateSlotsFor(occupant, w, all)) {
                            if (tried >= 10 || recounts >= budget - 1) break;
                            if (!occupant.isSlotLegalFresh(free)) continue;
                            Room room = firstFreeRoomAt(all, slot);
                            if (room == null) break;
                            Timeslot backOcc = occupant.timeslot;
                            Timeslot backBlk = blocker.timeslot;
                            occupant.timeslot = free;
                            blocker.timeslot = alt;
                            target.timeslot = slot;
                            target.room = room;
                            var v = recount(w, all);
                            tried++;
                            if (v.getOrDefault("H0", 99) < baseline.getOrDefault("H0", 99)
                                    && noRegression(baseline, v)) {
                                System.out.printf("D3-H0: WITNESS - occupant %d (%s/%s) %s -> %s, blocker %d (%s) %s -> %s, target -> %s: %s%n",
                                        occupant.id, subjectName(occupant), teacherName(occupant),
                                        backOcc.getDate(), free.getDate(),
                                        blocker.id, subjectName(blocker),
                                        backBlk.getDate(), alt.getDate(),
                                        slot.getDate() + "T" + slot.startDateTime.toLocalTime(), v);
                                return new HashMap<>(v);
                            }
                            occupant.timeslot = backOcc;
                            blocker.timeslot = backBlk;
                            target.timeslot = null;
                            target.room = null;
                        }
                    }
                }
            }
        }
        System.out.printf("D3-H0: LOCAL_NO_WITNESS at depth 3 (tried=%d chains)%n", tried);
        return baseline;
    }

    /** First assignable room with no lesson on it at the given slot (H2/H29
     *  pre-filter for compound placements; null = all rooms taken). */
    private Room firstFreeRoomAt(List<Lesson> all, Timeslot slot) {
        List<Room> rooms = Room.listAll();
        java.util.Set<Long> used = new java.util.HashSet<>();
        for (Lesson l : all) {
            if (l.timeslot != null && java.util.Objects.equals(l.timeslot.id, slot.id) && l.room != null) {
                used.add(l.room.id);
            }
        }
        for (Room r : rooms) {
            if (r.isAssignable() && !used.contains(r.id)) return r;
        }
        return null;
    }

    /** DEPTH-2 for an empty declared day (June cells): import a donor hour of
     *  the group from another date, then patch the donor's day from a third
     *  date (import + give-back). The oracle rejects chains that open a new
     *  deficit anywhere (H32/H33/H34 at the patch source). */
    private Map<String, Integer> juneDepth2Swap(Wires w, List<Lesson> all,
            Map<String, Integer> baseline, String group, String date, int budget) {
        // free slots on the empty date
        List<Timeslot> free = new ArrayList<>();
        java.util.Set<Long> occupied = new java.util.HashSet<>();
        for (Lesson l : all) {
            if (l.timeslot != null && date.equals(l.timeslot.getDate().toString())
                    && group.equals(groupName(l))) occupied.add(l.timeslot.id);
        }
        for (Timeslot ts : w.timeslots()) {
            if (date.equals(ts.getDate().toString()) && !occupied.contains(ts.id)) free.add(ts);
        }
        if (free.isEmpty()) {
            System.out.printf("D2-%s-%s: no free slot (already filled?)%n", group, date);
            return baseline;
        }
        // donors: group lessons on OTHER dates, unpinned
        List<Lesson> donors = new ArrayList<>();
        for (Lesson l : all) {
            if (l.timeslot != null && !date.equals(l.timeslot.getDate().toString())
                    && group.equals(groupName(l)) && !l.pinned) donors.add(l);
        }
        int tried = 0;
        for (Timeslot slot : free) {
            if (tried >= 20 || recounts >= budget - 1) break;
            for (Lesson donor : donors) {
                if (tried >= 20 || recounts >= budget - 1) break;
                if (!donor.isSlotLegalFresh(slot)) continue;
                Timeslot donorBack = donor.timeslot;
                // patch candidates: group lessons from third dates, legal into
                // the donor's vacated slot
                for (Lesson patch : donors) {
                    if (patch == donor || tried >= 20 || recounts >= budget - 1) break;
                    if (patch.timeslot == null || patch.timeslot.id == donorBack.id) continue;
                    if (!patch.isSlotLegalFresh(donorBack)) continue;
                    Timeslot patchBack = patch.timeslot;
                    donor.timeslot = slot;
                    patch.timeslot = donorBack;
                    var v = recount(w, all);
                    tried++;
                    if (v.getOrDefault("H34", 99) < baseline.getOrDefault("H34", 99)
                            && noRegression(baseline, v)) {
                        System.out.printf("D2-%s-%s: WITNESS - donor %d (%s) %s -> %s, patch %d (%s) %s -> %s: %s%n",
                                group, date, donor.id, subjectName(donor),
                                donorBack.getDate(), slot.startDateTime.toLocalTime(),
                                patch.id, subjectName(patch),
                                patchBack.getDate(), donorBack.startDateTime.toLocalTime(), v);
                        return new HashMap<>(v);
                    }
                    donor.timeslot = donorBack;
                    patch.timeslot = patchBack;
                }
            }
        }
        System.out.printf("D2-%s-%s: LOCAL_NO_WITNESS at depth 2 (tried=%d compounds)%n",
                group, date, tried);
        return baseline;
    }

    private void fixtureBG14ConfezioneOrigin() {
        for (String line : fixtureLines(FIXTURE_B)) {
            if (line.startsWith("G14|Confezione|T16|")) {
                System.out.printf("FIXTURE B origin of the H0 witness: %s%n", line);
            }
        }
    }

    // ────────────────────────── helpers ──────────────────────────

    private void assertVec(Map<String, Integer> v, int h28, int h33, int h34, int h0, int h32, int h29, String msg) {
        assertEquals(h28, v.getOrDefault("H28", -1), msg + " (H28)");
        assertEquals(h33, v.getOrDefault("H33", -1), msg + " (H33)");
        assertEquals(h34, v.getOrDefault("H34", -1), msg + " (H34)");
        assertEquals(h0, v.getOrDefault("H0", -1), msg + " (H0)");
        assertEquals(h32, v.getOrDefault("H32", -1), msg + " (H32)");
        assertEquals(h29, v.getOrDefault("H29", -1), msg + " (H29)");
    }

    private boolean noRegression(Map<String, Integer> before, Map<String, Integer> after) {
        for (var e : before.entrySet()) {
            if (after.getOrDefault(e.getKey(), 0) > e.getValue()) return false;
        }
        return true;
    }

    private List<String> fixtureLines(String path) {
        try {
            return java.nio.file.Files.readAllLines(java.nio.file.Path.of(path));
        } catch (Exception e) {
            throw new IllegalStateException("cannot read fixture " + path, e);
        }
    }

    private String groupName(Lesson l) { return l.course.studentGroup.name; }
    private String subjectName(Lesson l) { return l.course.subject; }
    private String teacherName(Lesson l) { return l.course.teacher.name; }

    /** candidate slots for a placement: the group's calendar-open in-grid slots,
     *  cheapest-first = dates where the group already has lessons (shape-aware). */
    private List<Timeslot> candidateSlotsFor(Lesson target, Wires w, List<Lesson> all) {
        String group = groupName(target);
        java.util.Set<java.time.LocalDate> groupDates = new java.util.HashSet<>();
        for (Lesson l : all) {
            if (l.timeslot != null && group.equals(groupName(l))) groupDates.add(l.timeslot.getDate());
        }
        java.util.Set<String> occupied = new java.util.HashSet<>();
        for (Lesson l : all) {
            if (l.timeslot != null && groupName(l).equals(group)) {
                occupied.add(l.timeslot.getDate() + "T" + l.timeslot.startDateTime.toLocalTime());
            }
        }
        // the lesson's OWN value range: a slot outside it is VRP-illegal by
        // construction (rescore would throw), so it is not a candidate at all
        List<Timeslot> own = new ArrayList<>();
        List<Timeslot> other = new ArrayList<>();
        for (Timeslot ts : w.timeslots()) {
            if (!target.isSlotLegalFresh(ts)) continue; // VRP (TASK-578 fresh, never the stale cache)
            String cell = ts.getDate() + "T" + ts.startDateTime.toLocalTime();
            if (occupied.contains(cell)) continue; // H3-sure death: never try occupied cells
            if (groupDates.contains(ts.getDate())) own.add(ts); else other.add(ts);
        }
        own.addAll(other);
        return own;
    }

    @Transactional
    void reloadFixture(String path) {
        loadContentFixture(path); // its loader already clears every placement first
    }

    /** apply the content-addressed fixture: one line per placed lesson,
     *  group|subject|teacher|date|startTime|pinned; matched onto H2 lessons by
     *  course content and consumed one per (course, cell). */
    @Transactional
    void loadContentFixture(String path) {
        Map<String, ArrayDeque<Lesson>> byCourse = new HashMap<>();
        for (Lesson l : freshLessons()) {
            String key = groupName(l) + "|" + subjectName(l) + "|" + teacherName(l);
            byCourse.computeIfAbsent(key, k -> new ArrayDeque<>()).add(l);
            l.timeslot = null;
            l.room = null;
            l.pinned = false;
            l.promotedPin = false;
        }
        Map<String, Room> roomsByName = new HashMap<>();
        List<Room> allRooms = Room.listAll();
        for (Room r : allRooms) roomsByName.put(r.name, r);
        Map<String, Timeslot> byCell = new HashMap<>();
        List<Timeslot> allTimeslots = Timeslot.listAll();
        for (Timeslot ts : allTimeslots) {
            // HH:mm:ss, matching the fixture's raw startDateTime time part
            byCell.put(ts.getDate() + "T" + ts.startDateTime.toLocalTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")), ts);
        }
        int applied = 0;
        int unmatched = 0;
        for (String line : fixtureLines(path)) {
            String[] p = line.split("\\|");
            String key = p[0] + "|" + p[1] + "|" + p[2];
            Timeslot ts = byCell.get(p[3] + "T" + p[4]);
            ArrayDeque<Lesson> pool = byCourse.get(key);
            Lesson l = pool != null ? pool.pollFirst() : null;
            if (l == null || ts == null) {
                unmatched++;
                continue;
            }
            l.timeslot = ts;
            l.pinned = "1".equals(p[5]);
            Room room = roomsByName.get(p[6]);
            if (room != null) {
                l.room = room;
            } else if (!"-".equals(p[6])) {
                unmatched++;
                continue;
            }
            applied++;
        }
        System.out.printf("fixture %s: applied=%d unmatched=%d%n",
                java.nio.file.Path.of(path).getFileName(), applied, unmatched);
        assertEquals(0, unmatched, "fixture lines must all match the seed (content addressing broken otherwise)");
    }

    @Transactional
    void publishPinnedStatic() {
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(freshLessons());
    }

    @Transactional
    List<Lesson> freshLessons() {
        em.clear();
        return em.createQuery("SELECT l FROM Lesson l ORDER BY l.id", Lesson.class).getResultList();
    }

    private SchoolSchedule problem(Wires w, List<Lesson> lessons) {
        List<Room> rooms = Room.listAll();
        // the 3rd ctor arg is the StagePattern problem-fact list: H4/H30 join it
        // with no static fallback, so List.of() here would keep both families dead
        List<com.school.planner.domain.StagePattern> stagePatterns =
                com.school.planner.domain.StagePattern.listAll();
        SchoolSchedule p = new SchoolSchedule(w.timeslots(), rooms, stagePatterns,
                w.availabilities(), List.of(), w.groups(), w.teachers(), w.courses(), lessons, List.of());
        p.setDailyActivityQuotas(w.quotas());
        return p;
    }

    /** one bounded recount: FAMILIES prefixes, budget counted. */
    private Map<String, Integer> recount(Wires w, List<Lesson> all) {
        recounts++;
        return SnapshotFixture.providerHardPoints(solutionManager, problem(w, all), FAMILY_SET);
    }

    /** dedicated budget line for the AC#5 fixture-B replay: the phase-A units
     * can always spend past the shared cap-tail, structurally starving the
     * replay units to tried=0 (found by review; the reserved line is the fix). */
    private static final int REPLAY_BUDGET = 12;

    /** dedicated budget for the depth-2 compound phase (the recorded
     * escalations): each compound costs 2 recounts of wall per candidate. */
    private static final int DEPTH2_BUDGET = 120;

    /** dedicated budget for the TASK-595 finisher section: reload-verify + post-pass
     * recount on fixture A, reload + inertness recount on fixture B. */
    private static final int FINISHER_BUDGET = 4;

    private static final java.util.Set<String> FAMILY_SET = java.util.Set.of(FAMILIES);

    /** ONE match-enabled rescore answering every hard family with its
     * HARD-POINTS total (hard level 0 sum), NOT the match count:
     * groupBy-penalize constraints (H7/H11) emit zero-score matches for every
     * compliant group, so match counts read compliant days as violations
     * (the first full-oracle run read H7=3316 on the clean delivery). Exact
     * ID-token matching only: "H3" as a prefix absorbed H30-H34. */

    private record Wires(List<Timeslot> timeslots, List<StudentGroup> groups,
                         List<Teacher> teachers, List<Course> courses,
                         List<com.school.planner.domain.TeacherAvailability> availabilities,
                         List<DailyActivityQuota> quotas) {
    }

    @Transactional
    Wires wireStatics() {
        List<Timeslot> timeslots = Timeslot.listAll();
        List<StudentGroup> groups = StudentGroup.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
        List<com.school.planner.domain.TeacherAvailability> availabilities =
                com.school.planner.domain.TeacherAvailability.listAll();
        // H-COPRESENCE facts, same construction as ScheduleService.refreshSolverStatics
        var subjectToTag = com.school.planner.service.ConstrainedTeacherAllocator
                .buildSubjectToSingleTagMap(lessons.stream().map(l -> l.course)
                        .filter(java.util.Objects::nonNull).distinct().toList());
        var coPresenceDepth = com.school.planner.service.ConstrainedTeacherAllocator
                .loadGoldenCoPresenceDepth(subjectToTag);
        var w = SnapshotFixture.wireConstructionStatics(timeslots, groups, lessons, teachers,
                courses, availabilities, com.school.planner.domain.Room.listAll(),
                com.school.planner.domain.SchoolHoliday.listAll(),
                com.school.planner.domain.StagePattern.listAll(), coPresenceDepth, true, true);
        return new Wires(w.timeslots(), w.groups(), w.teachers(), w.courses(),
                w.availabilities(), w.quotas());
    }

    /** Mirror of DataGenerator.generateSchoolHolidays (private): H6 facts. */
    @Transactional
    void seedSchoolHolidays() {
        for (java.time.LocalDate date : DataGenerator.CLOSURE_DATES_2026_27) {
            if (date.isBefore(TimeslotSequence.SCHOOL_YEAR_START)
                    || date.isAfter(TimeslotSequence.SCHOOL_YEAR_END)) continue;
            new com.school.planner.domain.SchoolHoliday(date, "closure (TASK-593 fixture)").persist();
        }
    }

    /** Mirror of DataGenerator.generateStagePatterns (private): H30/H4 facts. */
    @Transactional
    void seedStagePatterns() {
        List<StudentGroup> groups = StudentGroup.listAll();
        addStagePattern(groups, "G07", java.time.LocalDate.of(2027, 3, 23), java.time.LocalDate.of(2027, 5, 28),
                java.util.Set.of(java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
                        java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY));
        addStagePattern(groups, "G11", java.time.LocalDate.of(2027, 1, 13), java.time.LocalDate.of(2027, 3, 19),
                java.util.Set.of(java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
                        java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY));
        addStagePattern(groups, "G12", java.time.LocalDate.of(2026, 11, 20), java.time.LocalDate.of(2027, 4, 17),
                java.util.Set.of(java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY));
    }

    private void addStagePattern(List<StudentGroup> groups, String groupName,
            java.time.LocalDate start, java.time.LocalDate end, java.util.Set<java.time.DayOfWeek> mask) {
        StudentGroup group = findGroupByNameLocal(groups, groupName);
        if (group != null) {
            new com.school.planner.domain.StagePattern(group, start, end, mask).persist();
        }
    }

    private StudentGroup findGroupByNameLocal(List<StudentGroup> groups, String name) {
        for (StudentGroup g : groups) {
            if (g.name.equals(name)) return g;
        }
        return null;
    }

    @Transactional
    void seedRealSeedFixture() {
        RealSeedFixture.seedRooms();
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers =
                RealSeedFixture.seedTeachersAndAvailabilityRules(availabilityExpansionService);
        // SubjectRegistry classification included (H-M2/H23/H24 live)
        SnapshotFixture.seedCoursesFromIncarichi(teachers, groups, lessonGenerator, subjectRegistry);
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
        seedSchoolHolidays();
        seedStagePatterns();
        dataGenerator.generateStageCourses();
    }
}
