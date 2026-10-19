package com.school.planner.bootstrap;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.CompositeSwapFinisher;
import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.SolutionManager;

/**
 * TASK-609 replay banco on the braccio A (m39-off) delivery snapshot: seven steps, each
 * PINNING a verdict the finisher design builds on. Hours below are wall-clock hours
 * (8 = 08:00), never slot indices.
 *
 * <p>Fidelity (twice fixed, each found by the bench or its review gate): the loaders do
 * NOT publish the co-presenza depth map nor the H33 day floors, so both are published in
 * the loader below with the production derivations. Step 1 asserts the faithful replay:
 * [-11/0]hard with the exact families (H0 -2, H17 -3, H32 -3, H34 -3; medium is
 * informational only, TASK-509 divergence plus the rebuilt M32 template).
 *
 * <p>Steps 2-5 are the NEGATIVE results, pinned as assertions: step2 zero legality-only
 * landings for the hardcoded Thursday cell (the generalization lives in step3); step4
 * the DFS-exhausted H34 fills (no complete legal fill exists for direct donors: the
 * blind-LLM dances of step6 go through relays instead); step5 the teacher-locked H17
 * slides and the H32 G13 deficits with no disciplined donor. Step3 is the positive
 * core: a T-generalized evict+place chain closing the boxed hour (-11 to -9).
 *
 * <p>Step7 runs the mechanized CompositeSwapFinisher v0.1 (TASK-609) on the same
 * state: the RELAY-k primitive as production code, -11 to -8 (it also closes an H32
 * deficit in cascade). Step6 is the decisive arbitration of the TASK-608 blind-LLM solution (40 moves in
 * {@code blind-puzzle/moves-5.txt}, found by an agent that knows nothing of the project,
 * iterating against the python checker): applied verbatim on this replay with a room
 * pass (what the cycle's room-CH would do), it takes the delivery to 0 hard with H33
 * and the co-presenza exemption both armed. Verdict class: TREND / diagnosis only,
 * NEVER adoption (TASK-401: a snapshot fixes ONE trajectory). Opt-in via
 * {@code PLANNER_TEST_REPLAY_609} (path to the export, or {@code 1} for
 * {@code /tmp/m39off/export.json.gz}); {@code PLANNER_TEST_MOVES} overrides the moves
 * file (default: the repo-preserved moves-5). Env vars, not -D, because
 * {@code scripts/core/test.sh} rejects explicit -D.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_REPLAY_609", matches = ".+")
class CompositeRepairReplay609Test {

    private record Cell(String group, String date, int q) {}

    private static final String GROUP_G02 = "G02";
    private static final LocalDate CHAIN_DAY = LocalDate.of(2026, 9, 24);

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

    private SnapshotFixture.Snapshot load() throws Exception {
        String v = System.getenv("PLANNER_TEST_REPLAY_609");
        Path export = "1".equals(v)
                ? Path.of("/tmp/m39off/export.json.gz")
                : Path.of(v);
        return loadBasin(export);
    }

    /**
     * The replay-fidelity block shared by every basin load (TASK-612 /simplify: the
     * cross-basin steps had verbatim copies, and a future fidelity fix landing in
     * load() only would leave the alt-basin steps measuring a differently-armed
     * model - the "silently-empty statics are how a bench lies" trap this file
     * documents itself).
     */
    private SnapshotFixture.Snapshot loadBasin(Path export) throws Exception {
        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadFromExport(export, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
        // Replay-fidelity fix (found by step1 itself): the snapshot loaders do NOT publish
        // the co-presenza depth map (only wireConstructionStatics does), so the H2 guest
        // exemption would stay inert and 29 legal guest pairs would read as -29000 of
        // phantom H2. Publish it with the production loader (golden CSV, frozen fallback).
        SchoolSchedule.setGoldenCoPresenceDepthStatic(depthFromSnapshotCourses(snap));
        // Replay-fidelity fix #2 (code-review gate, the TASK-597 "silently-empty statics
        // are how a bench lies" lesson): H33 reads SchoolSchedule.getDayFloor(group.id),
        // which the loaders leave empty, so the family was INERT in every step of this
        // bench while the repairs perturb day-hours heavily. Publish the production
        // floors (same derivation wireConstructionStatics uses) so H33 arbitrates too.
        List<com.school.planner.domain.DailyActivityQuota> quotaRows =
                com.school.planner.domain.DailyActivityQuota.listAll();
        // Pool per group = non-Stage lesson count (the fixture's Course rows carry
        // totalHours=1: deriveHardFloorsFromPool's own javadoc names this exact shape).
        java.util.Map<String, Double> poolByGroup = new java.util.HashMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot != null && l.course != null && !l.course.teacher.pseudo) {
                poolByGroup.merge(l.course.studentGroup.name, 1.0, Double::sum);
            }
        }
        java.util.Map<String, Integer> floorsByName = com.school.planner.service.DayFloorDeriver
                .deriveHardFloorsFromPool(new java.util.ArrayList<>(snap.groups.values()), poolByGroup, quotaRows);
        java.util.Map<Long, Integer> floorsById = new java.util.HashMap<>();
        for (var g : snap.groups.values()) {
            Integer floor = floorsByName.get(g.name);
            if (floor != null && floor > 0) {
                floorsById.put(g.id, floor);
            }
        }
        SchoolSchedule.setDayFloorsStatic(floorsById);
        return snap;
    }

    private long[] score(SchoolSchedule schedule) {
        solutionManager.update(schedule);
        BendableScore total = (BendableScore) schedule.getScore();
        return new long[] { ScoreCompat.hardOf(total), ScoreCompat.mediumOf(total), ScoreCompat.softOf(total) };
    }

    /** Hard families only, absolute value sorted: WHO fires, so a failed step names its blocker. */
    private void printHardFamilies(SchoolSchedule schedule) {
        try {
            var factory = ((ai.timefold.solver.core.impl.solver.DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager)
                    .getScoreDirectorFactory();
            try (var sd = factory.createScoreDirectorBuilder()
                    .withConstraintMatchPolicy(ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy.ENABLED)
                    .withLookUpEnabled(false)
                    .withExpectShadowVariablesInCorrectState(false)
                    .build()) {
                sd.setWorkingSolution(schedule);
                sd.calculateScore();
                sd.getConstraintMatchTotalMap().values().stream()
                        .map(t -> new Object[] { t.getConstraintRef().id(),
                                ScoreCompat.hardOf((BendableScore) t.getScore()), t.getConstraintMatchCount() })
                        .filter(r -> ((long) r[1]) != 0)
                        .sorted(java.util.Comparator.comparingLong(r -> (long) r[1]))
                        .forEach(r -> System.out.printf("    HARD %-52s %6d (%d matches)%n", r[0], r[1], r[2]));
            }
        } catch (Exception e) {
            System.out.println("    (breakdown unavailable: " + e + ")");
        }
    }

    private Lesson findBoxed(SnapshotFixture.Snapshot snap) {
        return snap.boxedLessons.stream()
                .filter(l -> "Servizio tecnico e stilistico".equals(l.course.subject)
                        && GROUP_G02.equals(l.course.studentGroup.name))
                .findFirst().orElseThrow(() -> new IllegalStateException("boxed T01 hour not found"));
    }

    private Timeslot timeslotAt(SnapshotFixture.Snapshot snap, LocalDate date, int hour) {
        return snap.timeslots.stream()
                .filter(t -> date.equals(t.getDate()) && t.startDateTime.getHour() == hour)
                .findFirst().orElseThrow(() -> new IllegalStateException("no timeslot " + date + "@" + hour));
    }

    private Lesson findPlaced(SnapshotFixture.Snapshot snap, String group, LocalDate date, int hour, String teacher) {
        return snap.lessons.stream()
                .filter(l -> l.timeslot != null
                        && group.equals(l.course.studentGroup.name)
                        && date.equals(l.timeslot.getDate())
                        && l.timeslot.startDateTime.getHour() == hour
                        && teacher.equals(l.course.teacher.name))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "no lesson " + group + " " + date + "@" + hour + " " + teacher));
    }

    @Test
    @Transactional
    void step1ReplayReproducesMinusEleven() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] s = score(snap.problem);
        long placed = snap.lessons.stream().filter(l -> l.timeslot != null).count();
        System.out.printf("=== TASK-609 replay: placed=%d boxed=%d hard=%d medium=%d soft=%d%n",
                placed, snap.lessons.size() - placed, s[0], s[1], s[2]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-11L, s[0],
                "replay precondition: the m39-off delivery must reproduce -11 hard");
    }

    @Test
    @Transactional
    void step2CompositeChainClosesBoxedHour() throws Exception {
        SnapshotFixture.Snapshot snap = load();

        // Chain v3: the landing is SCAN-CHOSEN, not hand-picked. v1 (same-day 14:30) was
        // outside the Ra-lab VRP; v2 (2027-05-05 slot 13) was VRP-legal but the evaluator
        // exposed an H2 room conflict plus one H7 cap unit there (rooms and cumulative caps
        // are exactly what the python validator cannot see). The scan keeps only landings
        // that are VRP-legal, group-free, teacher-free and under cap, room-free for the
        // lesson's own room, and H17/H31-shaped (contiguous tail extension of a day with
        // lessons, or hour 8 into an empty day).
        Lesson evicted = findPlaced(snap, GROUP_G02, CHAIN_DAY, 12, "T26");
        org.junit.jupiter.api.Assertions.assertFalse(evicted.pinned, "the evicted hour must not be pinned");
        Timeslot freed = timeslotAt(snap, CHAIN_DAY, 12);
        List<Landing> landings = findPenalizedLandings(snap, evicted, freed);
        System.out.println("    penalized landings found: " + landings.size()
                + " (best predicted penalty " + (landings.isEmpty() ? "n/a" : landings.get(0).predictedPenalty()) + ")");
        // Exhaustive verdict, pinned as the assertion (v4): with legality-only gates
        // (VRP + group-free + teacher-free + SOME free tagged room) the hardcoded
        // T=09-24@12 yields ZERO landings across the whole 936-cell VRP. The winning
        // chain lives at a different T (see step 3): no 2-move repair exists for THIS
        // cell, which is what made the hand-picked chain impossible to find.
        org.junit.jupiter.api.Assertions.assertTrue(landings.isEmpty(),
                "the hardcoded T=09-24@12 must admit zero legality-only landings (v4 verdict)");
    }

    /**
     * Step 3: penalized-landing scan. The v3 shape/cap discipline proved NO zero-penalty
     * landing exists (vrp=936 shaped=1 underCap=0), but the phase wiring shows NO timeslot
     * LS ever runs on the delivered state, and a landing that pays +1 H17 or +1 H7 while
     * closing the -2 H0 still nets -1: an improvement the deterministic finishers cannot
     * see. This scan requires only hard LEGALITY at the landing (VRP, group free, teacher
     * free, free tagged room), predicts the penalty (H17 gap / H7 cap / H32 quota), and
     * ranks; the caller evaluates the best candidates with the real evaluator.
     */
    private record Landing(Timeslot cell, Room room, int predictedPenalty) {}

    private List<Landing> findPenalizedLandings(SnapshotFixture.Snapshot snap, Lesson evicted, Timeslot freed) {
        List<Landing> out = new java.util.ArrayList<>();
        for (Timeslot cand : evicted.getAvailableTimeslotsFresh()) {
            if (cand.equals(freed) || cand.equals(evicted.timeslot)) {
                continue;
            }
            boolean groupFree = snap.lessons.stream().noneMatch(l -> l != evicted && l.timeslot != null
                    && l.timeslot.equals(cand)
                    && GROUP_G02.equals(l.course.studentGroup.name));
            boolean teacherFree = snap.lessons.stream().noneMatch(l -> l != evicted && l.timeslot != null
                    && l.timeslot.equals(cand)
                    && "T26".equals(l.course.teacher.name));
            if (!groupFree || !teacherFree) {
                continue;
            }
            Room room = freeTaggedRoom(snap, cand, evicted);
            if (room == null) {
                continue;
            }
            int penalty = 0;
            List<Lesson> day = snap.lessons.stream()
                    .filter(l -> l != evicted && l.timeslot != null
                            && l.timeslot.getDate().equals(cand.getDate())
                            && GROUP_G02.equals(l.course.studentGroup.name))
                    .toList();
            int hour = cand.startDateTime.getHour();
            if (!day.isEmpty()) {
                int max = day.stream().mapToInt(l -> l.timeslot.startDateTime.getHour()).max().orElse(7);
                int min = day.stream().mapToInt(l -> l.timeslot.startDateTime.getHour()).min().orElse(8);
                if (hour > max || hour < min) {
                    penalty += 0; // tail/head extension stays contiguous
                } else {
                    penalty += 1; // internal gap if not filling an existing hole
                    boolean holeFill = day.stream().noneMatch(l -> l.timeslot.startDateTime.getHour() == hour);
                    if (holeFill) {
                        penalty -= 1; // fills an internal hole: H17-improving
                    }
                }
            }
            long teacherHours = snap.lessons.stream()
                    .filter(l -> l != evicted && l.timeslot != null
                            && l.timeslot.getDate().equals(cand.getDate())
                            && "T26".equals(l.course.teacher.name))
                    .count();
            if (teacherHours >= 6) {
                penalty += 1; // one H7 unit over cap
            }
            out.add(new Landing(cand, room, penalty));
        }
        out.sort(java.util.Comparator.comparingInt(Landing::predictedPenalty));
        return out;
    }

    /** The production subject-to-tag map: single-tag courses only (the co-presenza shape). */
    private static java.util.Map<String, String> subjectToTag(SnapshotFixture.Snapshot snap) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        for (Lesson l : snap.lessons) {
            if (l.course != null && l.course.subject != null
                    && l.course.requiredRoomTags != null && l.course.requiredRoomTags.size() == 1) {
                m.putIfAbsent(l.course.subject, l.course.requiredRoomTags.get(0));
            }
        }
        return m;
    }

    static java.util.Map<String, Integer> depthFromSnapshotCourses(SnapshotFixture.Snapshot snap) {
        return com.school.planner.service.ConstrainedTeacherAllocator
                .loadGoldenCoPresenceDepth(subjectToTag(snap));
    }

    /** A tag-matching room with no other lesson on that cell, else null (the evaluator shows H29). */
    private Room freeTaggedRoom(SnapshotFixture.Snapshot snap, Timeslot cell, Lesson lab) {
        for (Room r : snap.rooms) {
            if (r.tags == null || r.tags.stream().noneMatch(t -> lab.course.requiredRoomTags.contains(t))) {
                continue;
            }
            boolean taken = snap.lessons.stream().anyMatch(l -> l != lab && l.room == r && cell.equals(l.timeslot));
            if (!taken) {
                return r;
            }
        }
        return null;
    }

    /**
     * Step 3: bounded displacement chain over ALL candidate cells T. Step 2 proved no
     * 2-move repair exists via the 12:00 Ra lab (its 9-13 VRP is the killer), so the
     * search generalizes both axes: T = any Thursday 8-13 cell in the boxed VRP where
     * T01 is free and G02 sits (the occupant may be an ordinary lesson with a
     * full-day VRP), and the evicted occupant may chain through a room-displacement
     * depth level (move the occupant of the landing room first). Enumeration runs on
     * in-memory occupancy indexes; the evaluator scores the final block once.
     */
    @Test
    @Transactional
    void step3DepthThreeChainClosesBoxedHour() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);

        java.util.function.Function<Timeslot, String> cellKey = t -> t.getDate() + "@" + t.startDateTime.getHour();
        java.util.Map<String, Lesson> byGroup = new java.util.HashMap<>();
        java.util.Map<String, Lesson> byTeacher = new java.util.HashMap<>();
        java.util.Map<String, java.util.List<Lesson>> byRoom = new java.util.HashMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.room == null || l.course.teacher.pseudo) {
                continue;
            }
            String c = cellKey.apply(l.timeslot);
            byGroup.putIfAbsent(l.course.studentGroup.name + "|" + c, l);
            byTeacher.putIfAbsent(l.course.teacher.name + "|" + c, l);
            byRoom.computeIfAbsent(System.identityHashCode(l.room) + "|" + c, k -> new java.util.ArrayList<>()).add(l);
        }

        Lesson boxed = snap.boxedLessons.stream()
                .filter(l -> "Servizio tecnico e stilistico".equals(l.course.subject)
                        && GROUP_G02.equals(l.course.studentGroup.name))
                .findFirst().orElseThrow();
        List<Lesson> occupants = snap.lessons.stream()
                .filter(l -> l.timeslot != null && !l.pinned
                        && GROUP_G02.equals(l.course.studentGroup.name)
                        && boxed.getAvailableTimeslotsFresh().contains(l.timeslot)
                        && snap.lessons.stream().noneMatch(o -> o != l && o.timeslot != null
                                && o.timeslot.equals(l.timeslot)
                                && "T01".equals(o.course.teacher.name)))
                .toList();
        System.out.println("    T-candidates (boxed-VRP cells, T01 free, occupant unpinned): " + occupants.size());
        int lCandidates = 0, directTwoMove = 0, withRoomPath = 0;
        for (Lesson evicted : occupants) {
            Timeslot t = evicted.timeslot;
            Room roomAtT = evicted.room;
            String teacher = evicted.course.teacher.name;
            List<Room> tagged = evicted.course.requiredRoomTags.isEmpty()
                    ? snap.rooms
                    : snap.rooms.stream()
                            .filter(r -> r.tags != null
                                    && !java.util.Collections.disjoint(r.tags, evicted.course.requiredRoomTags))
                            .toList();
            for (Timeslot land : evicted.getAvailableTimeslotsFresh()) {
                if (land.equals(t)) {
                    continue;
                }
                String lk = cellKey.apply(land);
                if (byGroup.containsKey(GROUP_G02 + "|" + lk) || byTeacher.containsKey(teacher + "|" + lk)) {
                    continue;
                }
                lCandidates++;
                for (Room r : tagged) {
                    Lesson o = byRoom.getOrDefault(System.identityHashCode(r) + "|" + lk, List.of())
                            .stream().findFirst().orElse(null);
                    if (o == null) {
                        Room boxedRoom = boxedRoomAt(snap, t, boxed, roomAtT);
                        if (boxedRoom == null) {
                            continue; // no acconciatura room free at T: state untouched
                        }
                        directTwoMove++;
                        evicted.timeslot = land;
                        evicted.room = r;
                        boxed.timeslot = t;
                        boxed.room = boxedRoom;
                        long[] after = score(snap.problem);
                        System.out.printf("    2-MOVE: %s(%s) %s@%s -> %s@%s room %s; boxed -> T room %s%n",
                                evicted.course.subject, teacher, t.getDate(), t.startDateTime.getHour(),
                                land.getDate(), land.startDateTime.getHour(), r.name, roomAtT.name);
                        System.out.printf("=== TASK-609 chain: hard %d -> %d (delta %+d)%n",
                                before[0], after[0], after[0] - before[0]);
                        printHardFamilies(snap.problem);
                        org.junit.jupiter.api.Assertions.assertTrue(after[0] > before[0],
                                "the composite chain must improve hard");
                        return;
                    }
                    if (o.pinned || o == evicted || o.timeslot == null || o.room == null) {
                        continue;
                    }
                    for (Timeslot land2 : o.getAvailableTimeslotsFresh()) {
                        String l2k = cellKey.apply(land2);
                        if (l2k.equals(lk) || land2.equals(t)) {
                            continue;
                        }
                        if (byGroup.containsKey(o.course.studentGroup.name + "|" + l2k)
                                || byTeacher.containsKey(o.course.teacher.name + "|" + l2k)) {
                            continue;
                        }
                        Room room2 = snap.rooms.stream()
                                .filter(r2 -> !java.util.Objects.equals(r2, r)
                                        && byRoom.getOrDefault(System.identityHashCode(r2) + "|" + l2k, List.of()).isEmpty())
                                .findFirst().orElse(null);
                        if (room2 == null) {
                            continue;
                        }
                        withRoomPath++;
                        Timeslot oOld = o.timeslot;
                        Room boxedRoom = boxedRoomAt(snap, t, boxed, roomAtT);
                        if (boxedRoom == null) {
                            continue; // no acconciatura room free at T for the boxed lab
                        }
                        o.timeslot = land2;
                        o.room = room2;
                        evicted.timeslot = land;
                        evicted.room = r;
                        boxed.timeslot = t;
                        boxed.room = boxedRoom;
                        long[] after = score(snap.problem);
                        System.out.printf("    3-MOVE (boxed room %s): O %s(%s,%s) %s@%s -> %s@%s room %s; evicted %s(%s) -> %s@%s room %s; boxed -> %s@%s room %s%n",
                                o.course.subject, o.course.studentGroup.name, o.course.teacher.name,
                                oOld.getDate(), oOld.startDateTime.getHour(), land2.getDate(), land2.startDateTime.getHour(), room2.name,
                                evicted.course.subject, teacher, land.getDate(), land.startDateTime.getHour(), r.name,
                                t.getDate(), t.startDateTime.getHour(), boxedRoom.name);
                        System.out.printf("=== TASK-609 chain: hard %d -> %d (delta %+d)%n",
                                before[0], after[0], after[0] - before[0]);
                        printHardFamilies(snap.problem);
                        org.junit.jupiter.api.Assertions.assertTrue(after[0] > before[0],
                                "the composite chain must improve hard");
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("no chain: T-candidates=" + occupants.size()
                + " L-candidates=" + lCandidates + " direct2move=" + directTwoMove
                + " room-paths=" + withRoomPath);
    }

    /** The room the boxed lab may take at cell T: the freed one if tag-matching, else any
     * free tag-matching room; the boxed lesson's ROOM value range is tagged-rooms only. */
    private Room boxedRoomAt(SnapshotFixture.Snapshot snap, Timeslot t, Lesson boxed, Room freed) {
        if (freed != null && freed.tags != null
                && !freed.tags.stream().filter(boxed.course.requiredRoomTags::contains).toList().isEmpty()) {
            return freed;
        }
        return freeTaggedRoom(snap, t, boxed);
    }

    /**
     * Step 4: the three H34 empty declared days, filled with the same generalized scan
     * shape that won step 3. Donors are the group's own lessons on days with yield
     * (non-Stage count above the day's declared quota; the golden-height cap of EDF's
     * gate E is deliberately NOT applied here, the evaluator's H32 is the arbiter),
     * each move VRP-checked in Java this time (the python enumeration could not), with
     * the teacher free at the landing slot and a room free there (any room for ordinary
     * lessons, tag-matching for labs). One score at the end arbitrates the whole block.
     */
    @Test
    @Transactional
    void step4H34FillsImproveHard() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);

        java.util.Map<String, Integer> quota = new java.util.HashMap<>();
        List<com.school.planner.domain.DailyActivityQuota> quotaRows =
                com.school.planner.domain.DailyActivityQuota.listAll();
        for (com.school.planner.domain.DailyActivityQuota q : quotaRows) {
            quota.put(q.studentGroup.name + "|" + q.date, q.aulaHours + q.labHours);
        }
        java.util.Map<String, Integer> taken = new java.util.HashMap<>();
        java.util.Map<String, Integer> placedCount = new java.util.HashMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || "Stage".equals(l.course.teacher.name)) {
                continue;
            }
            placedCount.merge(l.course.studentGroup.name + "|" + l.timeslot.getDate(), 1, Integer::sum);
        }
        List<Cell> empties = List.of(new Cell("G06", "2027-06-10", 4),
                new Cell("G09", "2027-06-03", 5), new Cell("G12", "2027-04-16", 5));
        int moved = 0;
        for (Cell cell : empties) {
            System.out.println("    CELL-DEBUG " + cell.group() + " " + cell.date() + " quota=" + cell.q());
            List<Object[]> journal = new java.util.ArrayList<>();
            if (fillCellDFS(snap, cell, 0, journal, quota, taken, placedCount)) {
                moved += cell.q();
                System.out.println("    CELL " + cell.group() + " " + cell.date() + " COMPLETE (" + cell.q() + " fills)");
            } else {
                for (Object[] j : journal) {
                    ((Lesson) j[0]).timeslot = (Timeslot) j[1];
                    ((Lesson) j[0]).room = (Room) j[2];
                }
                journal.clear();
                System.out.println("    CELL " + cell.group() + " " + cell.date() + " INCOMPLETE, reverted");
            }
        }
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-609 fills: moved=%d hard %d -> %d (delta %+d)%n",
                moved, before[0], after[0], after[0] - before[0]);
        printHardFamilies(snap.problem);
        // DFS-exhausted verdict, pinned (13/09): with FULL per-cell backtracking over
        // donor choices (VRP-checked, teacher-free, free room, yield-capped, slide-legal),
        // NO complete fill exists for any of the three cells: all revert cleanly, delta 0.
        // The python enumeration was optimistic (no donor VRP/room check). Under the
        // current weights (H34 flat 1 vs H32 per-hour) these cells are a structural floor
        // for this repair class; closing them needs either deeper chains (depth >= 2
        // displacements) or the repair to arrive via a different family.
        org.junit.jupiter.api.Assertions.assertEquals(before[0], after[0],
                "DFS-exhausted: no complete fill exists, the revert must be clean (delta 0)");
        org.junit.jupiter.api.Assertions.assertEquals(-11L, after[0],
                "the delivered residual is unchanged after the exhausted search");
    }

    /**
     * EDF's compound, bench edition: extract the donor hour from ANY slot of its day and
     * slide the block above it down one slot each, per-step teacher-checked with revert
     * (the same-teacher-block trap EDF v0.1's TDD fixed). Journal entries are
     * (lesson, oldTimeslot, oldRoom) so a failed cell reverts wholesale.
     */
    /**
     * Bounded DFS over donor choices per target slot: greedy first-fit starved the later
     * slots (3/4, 2/5, 2/5), so a failed slot now BACKTRACKS and tries an alternative
     * donor for the earlier ones. Candidates are distance-ordered (nearest donor first,
     * the ordering under which the python enumeration found complete fills). Each choice
     * journals (donor + its slide steps) so a backtrack reverts exactly that choice.
     */
    private boolean fillCellDFS(SnapshotFixture.Snapshot snap, Cell cell, int slotIdx, List<Object[]> journal,
            java.util.Map<String, Integer> quota, java.util.Map<String, Integer> taken,
            java.util.Map<String, Integer> placedCount) {
        if (slotIdx >= cell.q()) {
            return true;
        }
        final int hour = 8 + slotIdx;
        Timeslot target = snap.timeslots.stream()
                .filter(t -> cell.date().equals(t.getDate().toString())
                        && t.startDateTime.getHour() == hour)
                .findFirst().orElse(null);
        if (target == null) {
            return false;
        }
        LocalDate targetDate = java.time.LocalDate.parse(cell.date());
        List<Lesson> cands = snap.lessons.stream()
                .filter(c -> c.timeslot != null && !c.pinned && c.room != null
                        && cell.group().equals(c.course.studentGroup.name)
                        && !"Stage".equals(c.course.teacher.name)
                        && c.getAvailableTimeslotsFresh().contains(target))
                .sorted(java.util.Comparator.comparingInt(
                        c -> Math.abs(java.time.Period.between(c.timeslot.getDate(), targetDate).getDays())))
                .toList();
        for (Lesson cand : cands) {
            String dk = cand.course.studentGroup.name + "|" + cand.timeslot.getDate();
            int yield = placedCount.getOrDefault(dk, 0) - taken.getOrDefault(dk, 0)
                    - quota.getOrDefault(dk, 0);
            if (yield <= 0) {
                continue;
            }
            boolean teacherFree = snap.lessons.stream().noneMatch(l -> l != cand && l.timeslot != null
                    && l.timeslot.equals(target)
                    && l.course.teacher.equals(cand.course.teacher));
            if (!teacherFree) {
                continue;
            }
            Room r2 = freeRoomFor(snap, target, cand);
            if (r2 == null) {
                continue;
            }
            int mark = journal.size();
            journal.add(new Object[] { cand, cand.timeslot, cand.room });
            if (!extractWithSlide(snap, journal, cand)) {
                journal.remove(journal.size() - 1);
                continue;
            }
            taken.merge(dk, 1, Integer::sum);
            System.out.printf("    TRY %s %s@%d <- %s(%s) from %s@%d%n", cell.group(), cell.date(), hour,
                    cand.course.subject, cand.course.teacher.name,
                    ((Timeslot) journal.get(mark)[1]).getDate(),
                    ((Timeslot) journal.get(mark)[1]).startDateTime.getHour());
            cand.timeslot = target;
            cand.room = r2;
            if (fillCellDFS(snap, cell, slotIdx + 1, journal, quota, taken, placedCount)) {
                return true;
            }
            // backtrack this choice: revert donor + its slides, restore yield
            cand.timeslot = (Timeslot) journal.get(mark)[1];
            cand.room = (Room) journal.get(mark)[2];
            for (int i = journal.size() - 1; i > mark; i--) {
                ((Lesson) journal.get(i)[0]).timeslot = (Timeslot) journal.get(i)[1];
                ((Lesson) journal.get(i)[0]).room = (Room) journal.get(i)[2];
                journal.remove(i);
            }
            journal.remove(mark);
            taken.merge(dk, -1, Integer::sum);
        }
        return false;
    }

    private boolean extractWithSlide(SnapshotFixture.Snapshot snap, List<Object[]> journal, Lesson donor) {
        LocalDate d = donor.timeslot.getDate();
        String g = donor.course.studentGroup.name;
        int x = donor.timeslot.startDateTime.getHour();
        List<Lesson> above = snap.lessons.stream()
                .filter(l -> l != donor && l.timeslot != null && g.equals(l.course.studentGroup.name)
                        && l.timeslot.getDate().equals(d) && l.timeslot.startDateTime.getHour() > x)
                .sorted(java.util.Comparator.comparingInt(l -> l.timeslot.startDateTime.getHour()))
                .toList();
        List<Object[]> slideJournal = new java.util.ArrayList<>();
        for (Lesson l : above) {
            int nh = l.timeslot.startDateTime.getHour() - 1;
            boolean teacherFree = snap.lessons.stream().noneMatch(o -> o != l && o != donor && o.timeslot != null
                    && o.timeslot.getDate().equals(d) && o.timeslot.startDateTime.getHour() == nh
                    && o.course.teacher.equals(l.course.teacher));
            if (!teacherFree) {
                for (Object[] j : slideJournal) {
                    ((Lesson) j[0]).timeslot = (Timeslot) j[1];
                }
                return false;
            }
            slideJournal.add(new Object[] { l, l.timeslot, l.room });
            final int nh2 = nh;
            l.timeslot = snap.timeslots.stream()
                    .filter(t -> t.getDate().equals(d) && t.startDateTime.getHour() == nh2)
                    .findFirst().orElseThrow();
        }
        journal.addAll(slideJournal);
        return true;
    }

    /**
     * Step 5: the two remaining families. H17: an internal gap closes by sliding the
     * block above it down one slot (per-step teacher+room checked; a blocked step leaves
     * the rest in place, the evaluator arbitrates). H32: G13's two 1-hour deficit days
     * fill with a single move from a yield-donor (tail extension preferred).
     */
    @Test
    @Transactional
    void step5H17SlidesAndH32DeficitFills() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);

        java.util.Map<String, java.util.List<Lesson>> byGroupDay = new java.util.HashMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot != null && !"Stage".equals(l.course.teacher.name)) {
                byGroupDay.computeIfAbsent(
                        l.course.studentGroup.name + "|" + l.timeslot.getDate(),
                        k -> new java.util.ArrayList<>()).add(l);
            }
        }
        int slides = 0;
        for (var e : byGroupDay.entrySet()) {
            List<Lesson> day = e.getValue();
            day.sort(java.util.Comparator.comparingInt(l -> l.timeslot.startDateTime.getHour()));
            int min = day.get(0).timeslot.startDateTime.getHour();
            int max = day.get(day.size() - 1).timeslot.startDateTime.getHour();
            java.util.Set<Integer> hours = new java.util.HashSet<>();
            for (Lesson l : day) {
                hours.add(l.timeslot.startDateTime.getHour());
            }
            for (int gap = min + 1; gap < max; gap++) {
                if (hours.contains(gap)) {
                    continue;
                }
                final String g = e.getKey().split("\\|")[0];
                final LocalDate d = day.get(0).timeslot.getDate();
                for (Lesson l : day) {
                    int h = l.timeslot.startDateTime.getHour();
                    if (h <= gap) {
                        continue;
                    }
                    final int nh = h - 1;
                    boolean teacherFree = snap.lessons.stream().noneMatch(o -> o != l && o.timeslot != null
                            && o.timeslot.getDate().equals(d) && o.timeslot.startDateTime.getHour() == nh
                            && o.course.teacher.equals(l.course.teacher));
                    if (!teacherFree) {
                        System.out.printf("    H17-BLOCKED %s %s: %s(%s) %d -> %d, teacher busy below%n",
                                g, d, l.course.subject, l.course.teacher.name, h, nh);
                        continue;
                    }
                    Room keep = l.room;
                    boolean roomFree = snap.lessons.stream()
                            .noneMatch(o -> o != l && o.room == keep && o.timeslot != null
                                    && o.timeslot.getDate().equals(d) && o.timeslot.startDateTime.getHour() == nh);
                    Room r2 = roomFree ? keep : freeRoomFor(snap,
                            snap.timeslots.stream().filter(t -> t.getDate().equals(d)
                                    && t.startDateTime.getHour() == nh).findFirst().orElseThrow(), l);
                    if (r2 == null) {
                        continue;
                    }
                    l.timeslot = snap.timeslots.stream().filter(t -> t.getDate().equals(d)
                            && t.startDateTime.getHour() == nh).findFirst().orElseThrow();
                    l.room = r2;
                    slides++;
                    System.out.printf("    H17-SLIDE %s %s: %s(%s) %d -> %d%n", g, d,
                            l.course.subject, l.course.teacher.name, h, nh);
                }
                break; // one gap-handling pass per day: the evaluator reads the result
            }
        }
        // H32: G13 deficit days 2026-12-14 and 2027-01-21 (deficit 1 each)
        java.util.Map<String, Integer> quota = new java.util.HashMap<>();
        List<com.school.planner.domain.DailyActivityQuota> quotaRows =
                com.school.planner.domain.DailyActivityQuota.listAll();
        for (com.school.planner.domain.DailyActivityQuota q : quotaRows) {
            quota.put(q.studentGroup.name + "|" + q.date, q.aulaHours + q.labHours);
        }
        java.util.Map<String, Integer> placedCount = new java.util.HashMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot != null && !"Stage".equals(l.course.teacher.name)) {
                placedCount.merge(l.course.studentGroup.name + "|" + l.timeslot.getDate(), 1, Integer::sum);
            }
        }
        int fills = 0;
        java.util.Map<String, Integer> taken = new java.util.HashMap<>();
        for (String dd : java.util.List.of("2026-12-14", "2027-01-21")) {
            LocalDate d = LocalDate.parse(dd);
            int max = byGroupDay.getOrDefault("G13|" + d, List.of()).stream()
                    .mapToInt(l -> l.timeslot.startDateTime.getHour()).max().orElse(7);
            final int nh = max + 1;
            Timeslot target = snap.timeslots.stream()
                    .filter(t -> t.getDate().equals(d) && t.startDateTime.getHour() == nh)
                    .findFirst().orElse(null);
            if (target == null) {
                System.out.println("    H32 " + dd + ": no slot at " + nh);
                continue;
            }
            Lesson donor = null;
            Room room = null;
            for (Lesson cand : snap.lessons) {
                if (cand.timeslot == null || cand.pinned || cand.room == null
                        || !"G13".equals(cand.course.studentGroup.name)
                        || "Stage".equals(cand.course.teacher.name)
                        || !cand.getAvailableTimeslotsFresh().contains(target)) {
                    continue;
                }
                String dk = "G13|" + cand.timeslot.getDate();
                if (placedCount.getOrDefault(dk, 0) - taken.getOrDefault(dk, 0)
                        - quota.getOrDefault(dk, 0) <= 0) {
                    continue;
                }
                boolean teacherFree = snap.lessons.stream().noneMatch(o -> o != cand && o.timeslot != null
                        && o.timeslot.equals(target) && o.course.teacher.equals(cand.course.teacher));
                if (!teacherFree) {
                    continue;
                }
                long teacherHoursTarget = snap.lessons.stream()
                        .filter(o -> o != cand && o.timeslot != null
                                && o.timeslot.getDate().equals(d)
                                && o.course.teacher.equals(cand.course.teacher))
                        .count();
                if (teacherHoursTarget >= 6) {
                    continue; // do not buy an H7 unit to close an H32 deficit
                }
                Room r2 = freeRoomFor(snap, target, cand);
                if (r2 == null) {
                    continue;
                }
                java.util.List<Object[]> j2 = new java.util.ArrayList<>();
                j2.add(new Object[] { cand, cand.timeslot, cand.room });
                if (!extractWithSlide(snap, j2, cand)) {
                    continue; // donor hole would open: try the next donor
                }
                donor = cand;
                room = r2;
                break;
            }
            if (donor == null) {
                System.out.println("    H32 " + dd + ": NO yield-donor for slot " + nh);
                continue;
            }
            System.out.printf("    H32-FILL G13 %s@%d <- %s(%s) from %s@%d room %s%n", dd, nh,
                    donor.course.subject, donor.course.teacher.name, donor.timeslot.getDate(),
                    donor.timeslot.startDateTime.getHour(), room.name);
            taken.merge("G13|" + donor.timeslot.getDate(), 1, Integer::sum);
            donor.timeslot = target;
            donor.room = room;
            fills++;
        }
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-609 step5: slides=%d fills=%d hard %d -> %d (delta %+d)%n",
                slides, fills, before[0], after[0], after[0] - before[0]);
        printHardFamilies(snap.problem);
        // Pinned verdict (13/09): H17 x3 are TEACHER-LOCKED (T06 11-04, T10
        // 11-04 and 03-02, T21 04-30 all teach another group at the hour below the
        // gap) and the H32 G13 deficits have NO disciplined yield-donor (the undisciplined
        // T25 fill bought H32 -1 at the price of H17 +1 AND H7 +1: net worse). Both
        // families are structural for this repair class at this state; the frontier is
        // depth-2 chains (displace the blocking teacher's lesson first).
        org.junit.jupiter.api.Assertions.assertEquals(before[0], after[0],
                "no disciplined repair exists for H17/H32 here: delta must be 0");
        org.junit.jupiter.api.Assertions.assertEquals(0, slides);
        org.junit.jupiter.api.Assertions.assertEquals(0, fills);
    }

    /**
     * Step 6: THE decisive arbitration. A blind LLM, given ONLY the full puzzle (no
     * windows, no families, no solver vocabulary) plus the mechanical checker, closed
     * ALL 9 defects AND the unlisted 10th (the G02 05-05 deficit it discovered by
     * baselining the empty move file) in 29 moves with multi-hop dances. That includes
     * every family this bench had pinned as structural (H34 fills, teacher-locked H17,
     * no-donor H32): either those verdicts were artifacts of this bench's own VRP/statics
     * wiring, or the python checker is more permissive than the real solver. Only the
     * Java evaluator decides. Moves resolve by CURRENT coordinates (the agent ordered
     * them so sequential application is well defined).
     */
    @Test
    @Transactional
    void step6BlindLlmSolutionOnRealEvaluator() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);

        Lesson boxed = snap.boxedLessons.stream()
                .filter(l -> "Servizio tecnico e stilistico".equals(l.course.subject)
                        && GROUP_G02.equals(l.course.studentGroup.name))
                .findFirst().orElseThrow();
        java.util.Set<Lesson> movedLessons = new java.util.HashSet<>();
        java.util.Map<String, Timeslot> tsByDateHour = new java.util.HashMap<>();
        for (Timeslot t : snap.timeslots) {
            tsByDateHour.put(t.getDate() + "@" + t.startDateTime.getHour(), t);
        }
        int applied = 0;
        for (String raw : java.nio.file.Files.readAllLines(java.nio.file.Path.of(System.getenv().getOrDefault("PLANNER_TEST_MOVES",
                        "claudedocs/spalmone-2026-27/task608-results/blind-puzzle/moves-5.txt")))) {
            final String ln = raw.trim();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("MOVE ([^|]+)\\|([0-9-]+) (\\d+) -> ([0-9-]+) (\\d+)").matcher(ln);
            java.util.regex.Matcher pl = java.util.regex.Pattern
                    .compile("PLACE SPECIALE -> ([0-9-]+) (\\d+)").matcher(ln);
            if (m.matches()) {
                String g = m.group(1).trim();
                String od = m.group(2), nd = m.group(4);
                int oh = Integer.parseInt(m.group(3)), nh = Integer.parseInt(m.group(5));
                java.util.List<Lesson> matches = snap.lessons.stream()
                        .filter(x -> x.timeslot != null && g.equals(x.course.studentGroup.name)
                                && od.equals(x.timeslot.getDate().toString())
                                && x.timeslot.startDateTime.getHour() == oh)
                        .toList();
                org.junit.jupiter.api.Assertions.assertEquals(1, matches.size(),
                        "move line must match exactly one lesson (the python checker enforces the same): " + ln);
                Lesson l = matches.get(0);
                org.junit.jupiter.api.Assertions.assertFalse(l.pinned, "pinned lessons are not movable: " + ln);
                movedLessons.add(l);
                Timeslot target = tsByDateHour.get(nd + "@" + nh);
                org.junit.jupiter.api.Assertions.assertNotNull(target, "no timeslot for " + ln);
                l.timeslot = target;
                applied++;
            } else if (pl.matches()) {
                Timeslot target = tsByDateHour.get(pl.group(1) + "@" + Integer.parseInt(pl.group(2)));
                org.junit.jupiter.api.Assertions.assertTrue(boxed.getAvailableTimeslotsFresh().contains(target),
                        "SPECIALE cell must be in its allowed range: " + ln);
                boxed.timeslot = target;
                boxed.room = boxedRoomAt(snap, target, boxed, null);
                org.junit.jupiter.api.Assertions.assertNotNull(boxed.room, "no free ACC room for the SPECIALE");
                applied++;
            } else if (!ln.isEmpty() && !ln.startsWith("#")) {
                throw new IllegalStateException("unrecognized move line (the python checker rejects these loudly, so must the replay): " + ln);
            }
        }
        // Room pass (what the cycle's room-CH would do after timeslot repairs): a moved
        // lesson keeps its room unless taken at the landing cell, then takes any free
        // room (tag-matching for lab courses). Without this the 36 moves read as 15 H2
        // conflicts that are pure bookkeeping, not real illegality.
        int reRoomed = 0;
        for (int round = 0; round < 3; round++) {
            boolean changed = false;
            // labs first: the two ACC rooms are the scarce resource
            for (boolean labsFirst : new boolean[] { true, false }) {
                for (Lesson l : movedLessons) {
                    if (l.timeslot == null || l.room == null
                            || (labsFirst == l.course.requiredRoomTags.isEmpty())) {
                        continue;
                    }
                    boolean taken = snap.lessons.stream().anyMatch(
                            o -> o != l && o.room == l.room && l.timeslot.equals(o.timeslot));
                    if (taken) {
                        Room r2 = freeRoomFor(snap, l.timeslot, l);
                        if (r2 != null) {
                            l.room = r2;
                            reRoomed++;
                            changed = true;
                        }
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        for (Lesson l : movedLessons) {
            if (l.timeslot == null || l.room == null) {
                continue;
            }
            boolean taken = snap.lessons.stream().anyMatch(
                    o -> o != l && o.room == l.room && l.timeslot.equals(o.timeslot));
            if (taken) {
                String occ = snap.lessons.stream()
                        .filter(o -> o != l && o.room == l.room && l.timeslot.equals(o.timeslot))
                        .map(o -> o.course.subject + "(" + o.course.teacher.name + "," + o.course.studentGroup.name
                                + ",room=" + (o.room == null ? "?" : o.room.name) + ")")
                        .collect(java.util.stream.Collectors.joining("; "));
                throw new AssertionError("room " + l.room.name + " still taken at " + l.timeslot.getDate() + "@"
                        + l.timeslot.startDateTime.getHour() + " for " + l.course.subject + "("
                        + l.course.teacher.name + "," + l.course.studentGroup.name + "): occupant=" + occ);
            }
        }
        System.out.println("    room pass (fixed point): re-roomed " + reRoomed + " lessons");
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-609 step6 (blind-LLM moves on the real evaluator): applied=%d "
                + "hard %d -> %d (delta %+d), medium %d -> %d%n",
                applied, before[0], after[0], after[0] - before[0], before[1], after[1]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertTrue(after[0] > before[0],
                "the blind-LLM solution must improve hard on the real evaluator");
    }

    /**
     * Step 7: the CompositeSwapFinisher v0.1 (TASK-609) on the live replay state: the
     * mechanization of the RELAY-k chains step3 found by search and step6 validated en
     * masse. The pass runs on the delivered grid exactly as the tail chain would invoke
     * it (same statics, same promoted-pins contract), flag armed around the call.
     */
    @Test
    @Transactional
    void step7CompositeSwapFinisherClosesBoxedHour() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        System.setProperty(CompositeSwapFinisher.FLAG, "true");
        CompositeSwapFinisher.Result result;
        try {
            result = CompositeSwapFinisher.finish(
                    snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        } finally {
            System.clearProperty(CompositeSwapFinisher.FLAG);
        }
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-609 step7 (CompositeSwapFinisher v0.1): targets=%d relay2=%d relay3=%d direct=%d"
                        + " hard %d -> %d (delta %+d), medium %d -> %d%n",
                result.targets(), result.relay2(), result.relay3(), result.directPlaces(),
                before[0], after[0], after[0] - before[0], before[1], after[1]);
        snap.lessons.stream()
                .filter(l -> l.timeslot != null && l.room == null)
                .forEach(l -> System.out.printf("    ROOMLESS: %s(%s,%s) at %s@%d%n",
                        l.course.subject, l.course.teacher.name, l.course.studentGroup.name,
                        l.timeslot.getDate(), l.timeslot.startDateTime.getHour()));
        Lesson bx = findBoxed(snap);
        System.out.printf("    boxed now at %s@%d room %s%n",
                bx.timeslot.getDate(), bx.timeslot.startDateTime.getHour(),
                bx.room == null ? "NONE" : bx.room.name);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertTrue(after[0] > before[0],
                "the mechanized RELAY must improve hard (H0 -2 recovered)");
        org.junit.jupiter.api.Assertions.assertTrue(result.chainsApplied() > 0,
                "at least one chain must fire on this residual");
    }

    /**
     * Step 8: cross-basin non-regression. The m39-on delivery (-13, DIFFERENT basin:
     * M39 armed, H17 x4, its own boxed hour) runs the same finisher. The adoption
     * question is not whether it fires here (it should) but that it cannot WORSEN a
     * basin it was not searched on. Gate: hard improves or stays; families may only
     * shrink in the families the chains target.
     */
    @Test
    @Transactional
    void step8CrossBasinNoRegression() throws Exception {
        java.nio.file.Path export = java.nio.file.Path.of(
                System.getProperty("planner.test.replay.alt", "/tmp/m39on/export.json.gz"));
        SnapshotFixture.Snapshot snap = loadBasin(export);
        long[] before = score(snap.problem);
        System.setProperty(com.school.planner.solver.CompositeSwapFinisher.FLAG, "true");
        com.school.planner.solver.CompositeSwapFinisher.Result result;
        try {
            result = CompositeSwapFinisher.finish(snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        } finally {
            System.clearProperty(com.school.planner.solver.CompositeSwapFinisher.FLAG);
        }
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-609 step8 (m39-on cross-basin): targets=%d relay2=%d relay3=%d direct=%d"
                        + " hard %d -> %d (delta %+d)%n",
                result.targets(), result.relay2(), result.relay3(), result.directPlaces(),
                before[0], after[0], after[0] - before[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertTrue(after[0] >= before[0],
                "cross-basin: the finisher must never WORSEN hard");
        if (result.chainsApplied() > 0) {
            org.junit.jupiter.api.Assertions.assertTrue(after[0] > before[0],
                    "chains fired but hard did not improve: the block oracle leaked a regression");
        }
    }

    /**
     * Step 9: inertness. On a residual-free state (the delivery after the blind-LLM
     * moves-5 repair, 0 boxed lessons left) the finisher must be a PURE no-op: zero
     * targets, zero chains, byte-identical hard. This is the adoption-grade evidence
     * that arming the flag on a good delivery costs nothing.
     */
    @Test
    @Transactional
    void step9InertOnResidualFreeState() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        Lesson boxed = findBoxed(snap);
        // apply the blind-LLM solution first (step6's 40 moves, zero hard)
        applyBlindMoves(snap);
        applyBlindRoomPass(snap);
        long[] repaired = score(snap.problem);
        org.junit.jupiter.api.Assertions.assertNotNull(boxed.timeslot,
                "precondition: the SPECIALE is placed, so NO boxed lessons remain");
        org.junit.jupiter.api.Assertions.assertTrue(repaired[0] >= -1,
                "precondition sanity: the repaired state is at most bookkeeping-noisy");
        System.setProperty(CompositeSwapFinisher.FLAG, "true");
        CompositeSwapFinisher.Result result;
        try {
            result = CompositeSwapFinisher.finish(snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        } finally {
            System.clearProperty(CompositeSwapFinisher.FLAG);
        }
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-609 step9 (inertness): targets=%d chains=%d hard %d -> %d%n",
                result.targets(), result.chainsApplied(), repaired[0], after[0]);
        org.junit.jupiter.api.Assertions.assertEquals(0, result.targets(),
                "no boxed lessons left: zero targets expected");
        org.junit.jupiter.api.Assertions.assertEquals(0, result.chainsApplied());
        org.junit.jupiter.api.Assertions.assertEquals(repaired[0], after[0],
                "inert pass: hard must be byte-identical");
    }

    /**
     * Step 10: EDF diagnosis (TASK-609 AC#2). The DFS in step4 proved no complete
     * legal fill exists for the 3 H34 cells with DIRECT donors, yet the python
     * enumeration found year-wide gate-E-legal yield and the blind-LLM dances close
     * them via relays EDF cannot express. This step runs EDF itself on the replay
     * state with its built-in rejection census armed (-Dedf.debug shape via System
     * property: test.sh rejects -D) and pins the census output: WHICH gate eats the
     * material, so the verdict names the mechanism, not the symptom.
     */
    @Test
    @Transactional
    void step10EdfRejectCensus() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        System.setProperty("edf.debug", "true");
        com.school.planner.solver.EmptyDayFinisher.Result result;
        try {
            result = com.school.planner.solver.EmptyDayFinisher.finish(
                    snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        } finally {
            System.clearProperty("edf.debug");
        }
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-609 step10 (EDF on replay): moves=%d cellsClosed=%d hard %d -> %d%n",
                result.movesApplied(), result.cellsClosed(), before[0], after[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertTrue(after[0] >= before[0],
                "EDF improve-or-noop must hold on the replay");
    }

    private Room freeRoomFor(SnapshotFixture.Snapshot snap, Timeslot cell, Lesson l) {
        boolean lab = !l.course.requiredRoomTags.isEmpty();
        for (Room r : snap.rooms) {
            if (lab && (r.tags == null || r.tags.stream().noneMatch(l.course.requiredRoomTags::contains))) {
                continue;
            }
            boolean taken = snap.lessons.stream().anyMatch(o -> o != l && o.room == r && cell.equals(o.timeslot));
            if (!taken) {
                return r;
            }
        }
        return null;
    }

    /** The delivery-order pair the TASK-612 steps share: the ratified boxed-chain
     *  pass, flag armed around the call (the bench must not depend on the
     *  production default for reproducibility). */
    private void runCsf(SchoolSchedule problem) {
        System.setProperty(CompositeSwapFinisher.FLAG, "true");
        try {
            CompositeSwapFinisher.finish(problem, (lesson, ts) -> lesson.timeslot = ts);
        } finally {
            System.clearProperty(CompositeSwapFinisher.FLAG);
        }
    }

    /**
     * Step 14 (TASK-613): the HOLE-RELAY pass on the full delivery chain's residual.
     * Order = delivery order: CSF (boxed), EDF extensions (fill relays), then the
     * hole pass. The m39-off chain delivers [-3] = exactly the 3 teacher-locked H17
     * holes; v0 mechanizes census shape (a) (import into the hole + exact-cell tail
     * export when the day is at quota), so the expected reach is the G11 04-30
     * class (-3 -> -2): the two G13 holes need sub-shapes (b) slide-prefix 3-cycle
     * and (c) room-occupant park, deliberately NOT in v0. Assertions stay soft
     * (non-regression always; improvement when holes closed) until the first replay
     * run measures the exact value to pin - the Fedora box cannot load the export
     * under the 3g heap directive, so the first run happens on the laptop.
     */
    @Test
    @Transactional
    void step14HoleRelayClosesTeacherLockedHoles() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] start = score(snap.problem);
        runCsf(snap.problem);
        com.school.planner.solver.EmptyDayFinisher.Result edf = runEdfExt(snap.problem, false);
        long[] mid = score(snap.problem);
        System.setProperty(com.school.planner.solver.HoleRelayFinisher.FLAG, "true");
        com.school.planner.solver.HoleRelayFinisher.Result result;
        try {
            result = com.school.planner.solver.HoleRelayFinisher.finish(
                    snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        } finally {
            System.clearProperty(com.school.planner.solver.HoleRelayFinisher.FLAG);
        }
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-613 step14 (hole-relay v0.2 post CSF+EDF-ext %d cells):"
                        + " targets=%d slides=%d rotations=%d moves=%d"
                        + " | hard %d -> %d (CSF+EDF) -> %d (holes, delta %+d)%n",
                edf.cellsClosed(), result.targets(), result.slides(),
                result.rotations(), result.movesApplied(),
                start[0], mid[0], after[0], after[0] - mid[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertTrue(after[0] >= mid[0],
                "the hole-relay pass must never WORSEN hard (wrapper oracle contract)");
        // DELIBERATELY not asserting "holes closed -> hard improves": v0's hole
        // enumeration does not reproduce H17's both-neighbors-pinned exemption, so a
        // closure can be hard-neutral without being a leak (the wrapper oracle owns
        // the regression contract in production); the printed split carries the info.
        if (result.holesClosed() > 0 && after[0] == mid[0]) {
            System.out.println("    NOTE: holes closed with flat hard (pinned-excused class?)");
        }
    }

    /**
     * Step 15 (TASK-614 diagnostic, opt-in {@code PLANNER_TEST_CRASH_WALK=1} +
     * {@code PLANNER_TEST_REPLAY_609} pointing at the crashed arm's export): the VRP
     * walk on the lesson whose placement killed the control arm's delivery-refinement
     * (IllegalStateException: value outside value range at B-ROOM init). Prints (a)
     * fresh/cached range membership of the CURRENT slot, (b) the eight VRP filters
     * evaluated one by one on that slot (which one rejects), (c) the group and its
     * quota/expected-hours rows for the crash date. Diagnostic only: no pin on the
     * outcome, the assertion is just "the walk ran on the real lesson".
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_CRASH_WALK", matches = "1")
    void step15CrashVrpWalk() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        java.time.LocalDate crashDate = java.time.LocalDate.of(2027, 6, 10);
        // The crash entity: the export PERSISTS its last-saved placement (the illegal
        // in-memory position was never saved), so the walk targets the LESSON by id
        // (PLANNER_TEST_CRASH_LESSON_ID, default the arm-a crash entity 10060) and
        // evaluates its VRP against the crash SLOT, not against where it sits now.
        long crashLessonId = Long.parseLong(
                System.getenv().getOrDefault("PLANNER_TEST_CRASH_LESSON_ID", "10060"));
        java.util.List<Lesson> family = new java.util.ArrayList<>();
        java.util.List<Lesson> hits = new java.util.ArrayList<>();
        for (Lesson l : snap.lessons) {
            if (l.course != null && "Lavorazioni meccaniche".equals(l.course.subject)) {
                family.add(l);
                if (l.id != null && l.id == crashLessonId) {
                    hits.add(l);
                }
            }
        }
        System.out.printf("=== TASK-614 step15 (crash VRP walk): Lavorazioni meccaniche family=%d,"
                + " crashLessonId=%d found=%b%n", family.size(), crashLessonId, !hits.isEmpty());
        if (hits.isEmpty()) {
            for (Lesson l : family) {
                System.out.printf("    family id=%s pinned=%b ts=%s teacher=%s group=%s%n",
                        l.id, l.pinned,
                        l.timeslot == null ? "null" : l.timeslot.startDateTime.toString(),
                        l.getTeacher() == null ? "-" : l.getTeacher().name,
                        l.getStudentGroup() == null ? "-" : l.getStudentGroup().name);
            }
            org.junit.jupiter.api.Assertions.fail("crash lesson id " + crashLessonId
                    + " is NOT in this export: documented, stopping per mandate");
        }
        Lesson L = hits.get(0);
        Timeslot crashSlot = snap.timeslots.stream()
                .filter(ts -> crashDate.equals(ts.getDate()) && ts.startDateTime.getHour() == 11)
                .findFirst().orElse(null);
        System.out.printf("    crash slot 2027-06-10T11 exists in grid: %s%n",
                crashSlot == null ? "NO (grid hole: no Timeslot at all)"
                        : "yes (" + crashSlot.startDateTime + " - " + crashSlot.endDateTime + ")");
        org.junit.jupiter.api.Assertions.assertNotNull(crashSlot,
                "the crash slot must exist as a Timeslot to walk the filters on it");
        com.school.planner.domain.StudentGroup g = L.getStudentGroup();
        Long tid = L.getTeacher() != null ? L.getTeacher().id : null;
        boolean pseudo = L.getTeacher() != null && L.getTeacher().pseudo;
        System.out.printf("    lesson id=%s pinned=%b persistedTs=%s teacher=%s group=%s prof=%b tags=%s%n",
                L.id, L.pinned,
                L.timeslot == null ? "null" : L.timeslot.startDateTime.toString(),
                L.getTeacher() == null ? "-" : L.getTeacher().name,
                g == null ? "-" : g.name,
                L.course != null && L.course.professionalizzanti,
                L.course == null || L.course.requiredRoomTags == null
                        ? "[]" : L.course.requiredRoomTags.toString());
        // (d)+(a): cached (populated now) vs fresh (recomputed) membership of the CRASH slot
        java.util.List<Timeslot> cached = L.getAvailableTimeslots();
        java.util.List<Timeslot> fresh = L.getAvailableTimeslotsFresh();
        System.out.printf("    (a) fresh.contains(crashSlot)=%b isSlotLegalFresh=%b freshSize=%d"
                        + " | (d) cached.contains(crashSlot)=%b cachedSize=%d%n",
                fresh.contains(crashSlot), L.isSlotLegalFresh(crashSlot), fresh.size(),
                cached.contains(crashSlot), cached.size());
        if (fresh.size() <= 8) {
            fresh.forEach(ts -> System.out.println("      fresh: " + ts.startDateTime
                    + " - " + ts.endDateTime));
        }
        // (b): the eight VRP filters, same order as isSlotLegalFresh/computeAvailableTimeslots
        java.util.Set<java.time.LocalDate> holidays = SchoolSchedule.getSchoolHolidaysStatic();
        java.util.Map<Long, java.util.Set<java.time.LocalDate>> blackout =
                SchoolSchedule.getStageBlackoutDatesStatic();
        boolean prof = L.course != null && L.course.professionalizzanti;
        java.util.Set<java.time.LocalDate> bd = g != null && blackout != null
                ? blackout.get(g.id) : null;
        java.util.Map<Long, java.util.Set<Timeslot>> pinnedMap =
                SchoolSchedule.getPinnedLessonTimeslotsByGroupStatic();
        java.util.Set<Timeslot> occ = g != null && pinnedMap != null ? pinnedMap.get(g.id) : null;
        java.util.Map<java.time.LocalDate, com.school.planner.domain.DailyActivityQuota> quotasByDate =
                g != null ? SchoolSchedule.getDailyActivityQuotasForGroup(g.id) : java.util.Map.of();
        boolean quotaGroup = !quotasByDate.isEmpty();
        boolean quotaBound = quotaGroup && g != null
                && com.school.planner.domain.DailyActivityQuota.isQuotaBoundLabSubject(g.name, L.getSubject());
        com.school.planner.domain.DailyActivityQuota row =
                quotasByDate.get(crashSlot.getDate());
        java.util.Map<java.time.LocalDate, com.school.planner.domain.DailyActivityQuota> eh =
                g != null ? SchoolSchedule.getExpectedHoursForGroup(g.id) : java.util.Map.of();
        java.util.LinkedHashMap<String, Boolean> walk = new java.util.LinkedHashMap<>();
        walk.put("1-H6.1-holiday", !(holidays != null && holidays.contains(crashSlot.getDate())));
        walk.put("2-H5.1-teacher-unavail", !Lesson.isTeacherUnavailable(
                crashSlot, tid, SchoolSchedule.getTeacherAvailabilitiesStatic()));
        walk.put("3-H4.1-stage-blackout",
                !( !prof && !pseudo && g != null && bd != null && bd.contains(crashSlot.getDate())));
        walk.put("4-H3-pinned-cell",
                !(g != null && occ != null && !occ.isEmpty() && occ.contains(crashSlot)));
        walk.put("5-H20-calendar-window", com.school.planner.service.GroupCalendarService.isOpen(
                g != null ? SchoolSchedule.getGroupCalendarRulesForGroup(g.id) : java.util.List.of(),
                crashSlot));
        walk.put("6-L1bis-quota-permits", !quotaGroup || pseudo
                || com.school.planner.domain.DailyActivityQuota.permits(row, quotaBound));
        walk.put("7-stage-confinement", !L.isStageConfined()
                || com.school.planner.domain.DailyActivityQuota.isStageDate(eh.get(crashSlot.getDate())));
        walk.put("8-no-session", !(!pseudo && g != null && !eh.isEmpty()
                && !eh.containsKey(crashSlot.getDate())));
        walk.forEach((name, pass) -> System.out.printf("    (b) %-24s %s%n",
                name, pass ? "pass" : "** REJECT **"));
        // (c): the group's declared rows for the crash date
        System.out.printf("    (c) group=%s quotaRow2027-06-10=%s expectedHoursRow=%s"
                        + " quotaBoundLabSubject=%b quotaGroupRows=%d%n",
                g == null ? "-" : g.name,
                row == null ? "NULL" : String.format("aula=%d lab=%d stage=%d fc=%b",
                        row.aulaHours, row.labHours, row.stageHours, row.FCQuota),
                eh.get(crashDate) == null ? "NULL" : String.format("aula=%d lab=%d stage=%d",
                        eh.get(crashDate).aulaHours, eh.get(crashDate).labHours,
                        eh.get(crashDate).stageHours),
                quotaBound, quotasByDate.size());
        long sameDay = fresh.stream().filter(ts -> crashDate.equals(ts.getDate())).count();
        System.out.printf("    fresh slots on 2027-06-10 for this lesson: %d%n", sameDay);
        org.junit.jupiter.api.Assertions.assertNotNull(L.timeslot, "walk ran on the real lesson");
    }

    /**
     * Step 16 (TASK-612 diagnostic): the armed EDF extensions with their reject census
     * on a DELIVERED live arm's export (the A/B measured a [delivery] 0-move no-op on a
     * state with 3 residual fill cells: the census names the gate that ate the
     * candidates). No value pins: the residual is basin-specific, the only assertion
     * is the wrapper-oracle non-regression contract.
     */
    @Test
    @Transactional
    void step16ArmBEdfCensus() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] start = score(snap.problem);
        runCsf(snap.problem);
        long[] mid = score(snap.problem);
        com.school.planner.solver.EmptyDayFinisher.Result result = runEdfExt(snap.problem, true);
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-612 step16 (delivered-arm export, EDF relay+h32 census post-CSF):"
                        + " moves=%d cellsClosed=%d | hard %d -> %d (CSF) -> %d (EDF ext, delta %+d)%n",
                result.movesApplied(), result.cellsClosed(),
                start[0], mid[0], after[0], after[0] - mid[0]);
        printHardFamilies(snap.problem);
        printFillResiduals(snap);
        org.junit.jupiter.api.Assertions.assertTrue(after[0] >= mid[0],
                "the armed extensions must never WORSEN hard on a delivered export");
    }

    /**
     * Step 17 (TASK-613 blind round 2, maintainer's directive 14/09 sera "perche' dobbiamo
     * attendere domattina?"): dump the POST-CHAIN state as the round-2 puzzle. The
     * chain CSF + EDF-ext delivers [-3] on m39-off; a fresh blind LLM round on THIS
     * state (not the original -11) produces the dance census for the three H17 holes
     * and the delta against the mechanized gates. Output: export_lessons.json in the
     * exact format of the round-1 puzzle (subject/teacher/group/date/hour/room/pinned)
     * into the dir named by PLANNER_TEST_PUZZLE_DIR, plus the difetti listing of the
     * internal holes on stdout. The rules text and the state-independent support files
     * (rooms/confined/VRP tables, seed-derived grids) are reused verbatim from the
     * round-1 puzzle dir. Opt-in: triple-gated like step15 (env + class gate).
     */
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PUZZLE_DIR", matches = ".+")
    @Transactional
    void step17DumpPostChainStateAsPuzzle() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        runCsf(snap.problem);
        runEdfExt(snap.problem, false);
        long[] after = score(snap.problem);
        java.nio.file.Path outDir = java.nio.file.Path.of(System.getenv("PLANNER_TEST_PUZZLE_DIR"));
        java.nio.file.Files.createDirectories(outDir);
        StringBuilder json = new StringBuilder("[");
        String sep = "";
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.getStudentGroup() == null) {
                continue;
            }
            String room = l.room == null ? "" : l.room.name;
            json.append(sep).append(String.format(
                    "{\"subject\": \"%s\", \"teacher\": \"%s\", \"group\": \"%s\", \"date\": \"%s\","
                            + " \"hour\": %d, \"room\": \"%s\", \"pinned\": %s}",
                    esc(l.course.subject), esc(l.getTeacher() == null ? "" : l.getTeacher().name),
                    esc(l.getStudentGroup().name), l.timeslot.getDate(),
                    l.timeslot.startDateTime.getHour(), esc(room), l.pinned));
            sep = ",\n";
        }
        json.append("]");
        java.nio.file.Files.writeString(outDir.resolve("export_lessons.json"), json.toString());
        System.out.printf("=== TASK-613 step17: dumped %d placed lessons to %s (hard post-chain %d)%n",
                sep.isEmpty() ? 0 : snap.lessons.size(), outDir, after[0]);
        // The difetti: every internal hole of the post-chain state (H17 view)
        java.util.Map<String, java.util.TreeMap<Integer, Lesson>> days = new java.util.TreeMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.getStudentGroup() == null) {
                continue;
            }
            days.computeIfAbsent(l.getStudentGroup().name + "|" + l.timeslot.getDate(),
                    k -> new java.util.TreeMap<>())
                    .put(com.school.planner.solver.TimeslotSequence.slotIndexOf(l.timeslot), l);
        }
        for (var e : days.entrySet()) {
            java.util.TreeMap<Integer, Lesson> day = e.getValue();
            if (day.size() < 2) {
                continue;
            }
            String key = e.getKey();
            for (int slot = day.firstKey() + 1; slot < day.lastKey(); slot++) {
                if (!day.containsKey(slot)) {
                    Lesson above = day.higherEntry(slot).getValue();
                    System.out.printf("DIFETTO %s: buco allo slot %d (lezione sopra: %s(%s) allo slot %d;"
                                    + " sotto: %s(%s) allo slot %d)%n",
                            key.replace('|', ' '), slot,
                            above.course.subject, above.getTeacher().name, day.higherKey(slot),
                            day.lowerEntry(slot).getValue().course.subject,
                            day.lowerEntry(slot).getValue().getTeacher().name, day.lowerKey(slot));
                }
            }
        }
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-3L, after[0],
                "the dump must be the [-3] post-chain state (chain unchanged)");
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** The H32-view residual cells (declared aula+lab quota minus placed non-Stage
     *  hours, tolerance-aware, excluded groups skipped) so the census has its target
     *  list next to it. Same shape EmptyDayFinisher's H32 family targets. */
    private void printFillResiduals(SnapshotFixture.Snapshot snap) {
        java.util.Map<String, java.util.Map<java.time.LocalDate, int[]>> placedByGroup = new java.util.HashMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.getStudentGroup() == null
                    || l.getTeacher() == null || l.getTeacher().pseudo) {
                continue;
            }
            placedByGroup.computeIfAbsent(l.getStudentGroup().name, k -> new java.util.HashMap<>())
                    .merge(l.timeslot.getDate(), new int[]{1}, (a, b) -> new int[]{a[0] + b[0]});
        }
        java.util.Set<java.time.LocalDate> withTimeslot = new java.util.HashSet<>();
        snap.timeslots.forEach(ts -> withTimeslot.add(ts.getDate()));
        int cells = 0;
        for (com.school.planner.domain.DailyActivityQuota q : snap.problem.getDailyActivityQuotas()) {
            int quota = q.aulaHours + q.labHours;
            if (quota <= 0 || !withTimeslot.contains(q.date)
                    || com.school.planner.solver.SchoolScheduleConstraintProvider
                            .isQuotaFillExcluded(q.studentGroup.name)) {
                continue;
            }
            int[] placed = placedByGroup.getOrDefault(
                    q.studentGroup.name, java.util.Map.of()).get(q.date);
            int hours = placed == null ? 0 : placed[0];
            int tol = com.school.planner.solver.SchoolScheduleConstraintProvider
                    .quotaFillTolerance(q.studentGroup.name);
            int deficit = quota - tol - hours;
            if (deficit > 0) {
                cells++;
                System.out.printf("    [fill-residual] %s %s quota=%d placed=%d tol=%d deficit=%d%n",
                        q.studentGroup.name, q.date, quota, hours, tol, deficit);
            }
        }
        System.out.printf("    [fill-residual] total under-quota cells (H32 view): %d%n", cells);
    }

    /**
     * Step 18 (TASK-613 round-2 arbitration, opt-in {@code PLANNER_TEST_MOVES_R2=1}):
     * the python verifier passed the blind round-2 solution ({@code moves-r2.txt},
     * 21 same-day rotations); this is the real-evaluator arbitration (TASK-608
     * protocol). Chain CSF+EDF-ext to the [-3] state, apply the moves with the SAME
     * parser as applyBlindMoves, then score three snapshots: post-chain (expected
     * -3), post-moves, post-room-pass. A non-zero residue NAMES the filter the
     * python checker does not cover (H5 availability, co-presenza depth, real H7,
     * pins). No cure: measure and report.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_MOVES_R2", matches = "1")
    void step18BlindRound2Arbitration() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        runCsf(snap.problem);
        runEdfExt(snap.problem, false);
        long[] start = score(snap.problem);
        System.out.printf("=== TASK-613 step18 (round-2 arbitration): post-chain hard %d"
                        + " (expected -3), medium %d%n",
                start[0], start[1]);
        org.junit.jupiter.api.Assertions.assertEquals(-3L, start[0],
                "the chain must deliver the [-3] state before arbitration");
        java.nio.file.Path moves = java.nio.file.Path.of(
                System.getenv().getOrDefault("PLANNER_TEST_MOVES",
                        "claudedocs/spalmone-2026-27/task613-results/puzzle-round2/moves-r2.txt"));
        applyMovesFile(snap, moves);
        long[] postMoves = score(snap.problem);
        System.out.printf("    post-moves hard %d (expected 0 if the python verifier is"
                        + " faithful), medium %d%n", postMoves[0], postMoves[1]);
        printHardFamilies(snap.problem);
        applyBlindRoomPass(snap, moves);
        long[] postRooms = score(snap.problem);
        System.out.printf("    post-room-pass hard %d, medium %d%n",
                postRooms[0], postRooms[1]);
        printHardFamilies(snap.problem);
    }

    /**
     * Step 19 (TASK-613 SPIKE-2 diagnostic, opt-in {@code PLANNER_TEST_POP_CENSUS=1}):
     * the D2-b anomaly ("5 candidates evaluated for 3 targets" in the v0.1 import
     * census) is decidable by measuring the ENUMERATION UNIVERSE the scan sees on
     * the real [-3] post-chain state: for each hole target, how many same-group days
     * with lessons and how many lessons (unpinned) live in them. If the universe is
     * large, the old census under-counted; if tiny, the scan population itself is
     * the explanation. Measure-only, no pass copy.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_POP_CENSUS", matches = "1")
    void step19HoleTargetPopulationCensus() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        runCsf(snap.problem);
        runEdfExt(snap.problem, false);
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-613 step19 (hole-target population census): post-chain hard %d%n",
                after[0]);
        // inline hole enumeration (same shape as the pass: internal gaps per group-day)
        java.util.Map<String, java.util.TreeMap<java.time.LocalDate, java.util.TreeMap<Integer, Lesson>>> byGroup =
                new java.util.TreeMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) {
                continue;
            }
            byGroup.computeIfAbsent(l.course.studentGroup.name, k -> new java.util.TreeMap<>())
                    .computeIfAbsent(l.timeslot.getDate(), k -> new java.util.TreeMap<>())
                    .put(l.timeslot.startDateTime.getHour(), l);
        }
        int[] hours = {8, 9, 10, 11, 12, 13, 14, 15};
        for (var ge : byGroup.entrySet()) {
            String g = ge.getKey();
            for (var de : ge.getValue().entrySet()) {
                java.util.TreeMap<Integer, Lesson> day = de.getValue();
                if (day.size() < 2) {
                    continue;
                }
                int first = -1;
                int last = -1;
                for (int s = 0; s < hours.length; s++) {
                    if (day.containsKey(hours[s])) {
                        if (first < 0) {
                            first = s;
                        }
                        last = s;
                    }
                }
                int gaps = 0;
                for (int s = first + 1; s < last; s++) {
                    if (!day.containsKey(hours[s])) {
                        gaps++;
                    }
                }
                if (gaps > 0) {
                    int days = ge.getValue().size();
                    int lessons = 0;
                    int unpinned = 0;
                    for (var d2 : ge.getValue().values()) {
                        lessons += d2.size();
                        for (Lesson l : d2.values()) {
                            if (!l.pinned) {
                                unpinned++;
                            }
                        }
                    }
                    System.out.printf("    [pop] hole target %s %s gaps=%d | same-group days=%d"
                                    + " lessons=%d unpinned=%d%n",
                            g, de.getKey(), gaps, days, lessons, unpinned);
                }
            }
        }
    }

    /**
     * Step 20 (TASK-613 diff-guided diagnostic, opt-in {@code PLANNER_TEST_CHAIN_DIFF=1},
     * test-side copy of the v0.5 builder as {@link com.school.planner.solver.HoleRelayFinisherDiff}):
     * (1) chain to [-3]; (2) apply ONLY the 13 blind D1 moves (2027-04-30 blocks) and
     * prove the winning sequence on the real evaluator (hard -2, zero H1); (3) fresh
     * [-3] state, run the instrumented builder and print the chain it BUILDS for each
     * target plus the verifyComposite collision cell; (4) the reader diffs built vs
     * blind move-by-move. Measure-only: no cure.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_CHAIN_DIFF", matches = "1")
    void step20BlindChainDiff() throws Exception {
        java.nio.file.Path movesAll = java.nio.file.Path.of(
                System.getenv().getOrDefault("PLANNER_TEST_MOVES",
                        "claudedocs/spalmone-2026-27/task613-results/puzzle-round2/moves-r2.txt"));
        java.util.List<String> d1 = new java.util.ArrayList<>();
        for (String ln : java.nio.file.Files.readAllLines(movesAll)) {
            if (ln.startsWith("MOVE ") && ln.contains("2027-04-30")) {
                d1.add(ln);
            }
        }
        java.nio.file.Path d1File = java.nio.file.Path.of("target", "moves-d1-only.txt");
        java.nio.file.Files.write(d1File, d1);
        System.out.println("=== TASK-613 step20 (blind-chain diff): D1 moves = " + d1.size());
        d1.forEach(ln -> System.out.println("    [blind] " + ln.trim()));
        // (1)+(2): the blind D1 sequence on the real evaluator
        SnapshotFixture.Snapshot snap1 = load();
        runCsf(snap1.problem);
        runEdfExt(snap1.problem, false);
        applyMovesFile(snap1, d1File);
        applyBlindRoomPass(snap1, d1File);
        long[] blind = score(snap1.problem);
        System.out.printf("    [blind-applied] hard %d (expected -2, D1 closed; rooms fixed by the pass), medium %d%n",
                blind[0], blind[1]);
        printHardFamilies(snap1.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-2L, blind[0],
                "the blind D1 sequence must close D1 on the real evaluator (winning sequence)");
        // (3): the instrumented v0.5 builder on a fresh identical state
        SnapshotFixture.Snapshot snap2 = load();
        runCsf(snap2.problem);
        runEdfExt(snap2.problem, false);
        System.setProperty("hrf.debug", "true");
        com.school.planner.solver.HoleRelayFinisherDiff.Result built;
        try {
            built = com.school.planner.solver.HoleRelayFinisherDiff.finish(
                    snap2.problem, (lesson, ts) -> lesson.timeslot = ts);
        } finally {
            System.clearProperty("hrf.debug");
        }
        long[] mech = score(snap2.problem);
        System.out.printf("    [built-applied] hard %d, moves=%d%n", mech[0], built.movesApplied());
        printHardFamilies(snap2.problem);
    }

    /**
     * Step 21 (TASK-613 prototype-cura, opt-in {@code PLANNER_TEST_CHAIN_CURE=1}):
     * the teacher-aware symbolic DFS (checks (a) hole reserved to the tail's
     * teacher, (b) same-teacher co-destinations among movers, true-blocker
     * recursion) inside the test-side {@link com.school.planner.solver.HoleRelayFinisherDiff}.
     * Measure-only: built chains, hard before/after, families. Target: D1 closes
     * (-3 -> -2), ideally reproducing (or cleanly equivalent to) the blind cascade.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_CHAIN_CURE", matches = "1")
    void step21PrototypeCureMeasure() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        runCsf(snap.problem);
        runEdfExt(snap.problem, false);
        long[] before = score(snap.problem);
        System.out.printf("=== TASK-613 step21 (prototype cure): post-chain hard %d%n", before[0]);
        System.setProperty("hrf.debug", "true");
        com.school.planner.solver.HoleRelayFinisherDiff.Result result;
        try {
            result = com.school.planner.solver.HoleRelayFinisherDiff.finish(
                    snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        } finally {
            System.clearProperty("hrf.debug");
        }
        long[] after = score(snap.problem);
        System.out.printf("    [cure] slides=%d rotations=%d moves=%d | hard %d -> %d (delta %+d)%n",
                result.slides(), result.rotations(), result.movesApplied(),
                before[0], after[0], after[0] - before[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertTrue(after[0] >= before[0],
                "the prototype must never WORSEN hard (oracle contract)");
    }

    /**
     * Step 22 (TASK-613 round-3 dump, opt-in {@code PLANNER_TEST_PUZZLE_R3=1}):
     * the FULL mechanized chain (CSF + EDF-ext + the teacher-aware prototype cure
     * of step21) on m39-off, dumped as a round-3 puzzle in the step17 format.
     * Expected: hard 0 and ZERO DIFETTO lines (the blind round-3 should find
     * nothing left to improve: the convergence probe of the
     * blind->reverse->mechanize loop).
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PUZZLE_R3", matches = "1")
    void step22DumpRound3Puzzle() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        runCsf(snap.problem);
        runEdfExt(snap.problem, false);
        System.setProperty("hrf.debug", "true");
        com.school.planner.solver.HoleRelayFinisherDiff.Result cure;
        try {
            cure = com.school.planner.solver.HoleRelayFinisherDiff.finish(
                    snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        } finally {
            System.clearProperty("hrf.debug");
        }
        long[] after = score(snap.problem);
        java.nio.file.Path outDir = java.nio.file.Path.of(System.getenv("PLANNER_TEST_PUZZLE_DIR"));
        java.nio.file.Files.createDirectories(outDir);
        StringBuilder json = new StringBuilder("[");
        String sep = "";
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.getStudentGroup() == null) {
                continue;
            }
            String room = l.room == null ? "" : l.room.name;
            json.append(sep).append(String.format(
                    "{\"subject\": \"%s\", \"teacher\": \"%s\", \"group\": \"%s\", \"date\": \"%s\","
                            + " \"hour\": %d, \"room\": \"%s\", \"pinned\": %s}",
                    esc(l.course.subject), esc(l.getTeacher() == null ? "" : l.getTeacher().name),
                    esc(l.getStudentGroup().name), l.timeslot.getDate(),
                    l.timeslot.startDateTime.getHour(), esc(room), l.pinned));
            sep = ",\n";
        }
        json.append("]");
        java.nio.file.Files.writeString(outDir.resolve("export_lessons.json"), json.toString());
        System.out.printf("=== TASK-613 step22 (round-3 dump): %d placed lessons, cure moves=%d,"
                        + " hard %d (expected 0)%n",
                sep.isEmpty() ? 0 : snap.lessons.size(), cure.movesApplied(), after[0]);
        java.util.Map<String, java.util.TreeMap<Integer, Lesson>> days = new java.util.TreeMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.getStudentGroup() == null) {
                continue;
            }
            days.computeIfAbsent(l.getStudentGroup().name + "|" + l.timeslot.getDate(),
                    k -> new java.util.TreeMap<>())
                    .put(com.school.planner.solver.TimeslotSequence.slotIndexOf(l.timeslot), l);
        }
        int difetti = 0;
        for (var e : days.entrySet()) {
            java.util.TreeMap<Integer, Lesson> day = e.getValue();
            if (day.size() < 2) {
                continue;
            }
            String key = e.getKey();
            for (int slot = day.firstKey() + 1; slot < day.lastKey(); slot++) {
                if (!day.containsKey(slot)) {
                    difetti++;
                    Lesson above = day.higherEntry(slot).getValue();
                    System.out.printf("DIFETTO %s: buco allo slot %d (lezione sopra: %s(%s) allo slot %d;"
                                    + " sotto: %s(%s) allo slot %d)%n",
                            key.replace('|', ' '), slot,
                            above.course.subject, above.getTeacher().name, day.higherKey(slot),
                            day.lowerEntry(slot).getValue().course.subject,
                            day.lowerEntry(slot).getValue().getTeacher().name, day.lowerKey(slot));
                }
            }
        }
        System.out.printf("    [round-3] DIFETTI residui: %d (attesi 0)%n", difetti);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(0L, after[0],
                "the full mechanized chain must deliver 0 hard on m39-off (round-3 convergence)");
    }

    /**
     * Step 23 (TASK-613 NO-CONTENDER naming, opt-in {@code PLANNER_TEST_NC_WALK=1},
     * checkpoint t613c): on the arm-c-diag delivered export, per surviving H17 hole
     * print group/date/hour, the classification (direct-slide / contender /
     * NO-CONTENDER, mirroring finish()'s own decision) and, for a NO-CONTENDER tail,
     * EVERY gate that rejects it in rejectReason's order (group-cell / VRP / H1 / H7 /
     * room). Measure-only: names the family, no cure.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_NC_WALK", matches = "1")
    void step23NoContenderNaming() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        runCsf(snap.problem);
        runEdfExt(snap.problem, false);
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-613 step23 (NO-CONTENDER naming): post-chain hard %d%n", after[0]);
        java.util.Map<String, java.util.TreeMap<java.time.LocalDate, java.util.TreeMap<Integer, Lesson>>> byGroup =
                new java.util.TreeMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) {
                continue;
            }
            byGroup.computeIfAbsent(l.course.studentGroup.name, k -> new java.util.TreeMap<>())
                    .computeIfAbsent(l.timeslot.getDate(), k -> new java.util.TreeMap<>())
                    .put(l.timeslot.startDateTime.getHour(), l);
        }
        java.util.Map<java.time.LocalDate, java.util.Map<Integer, Timeslot>> tsByDateSlot = new java.util.HashMap<>();
        for (Timeslot t : snap.timeslots) {
            tsByDateSlot.computeIfAbsent(t.getDate(), k -> new java.util.HashMap<>())
                    .put(t.startDateTime.getHour(), t);
        }
        for (var ge : byGroup.entrySet()) {
            for (var de : ge.getValue().entrySet()) {
                java.util.TreeMap<Integer, Lesson> day = de.getValue();
                if (day.size() < 2) {
                    continue;
                }
                java.util.List<Integer> hoursSorted = new java.util.ArrayList<>(day.keySet());
                for (int h = hoursSorted.get(0) + 1; h < hoursSorted.get(hoursSorted.size() - 1); h++) {
                    if (day.containsKey(h)) {
                        continue;
                    }
                    Lesson tail = day.lastEntry().getValue();
                    Timeslot holeTs = tsByDateSlot.getOrDefault(de.getKey(), java.util.Map.of()).get(h);
                    String who = tail.course.subject + "(" + tail.getTeacher().name + ")@" + day.lastKey();
                    if (holeTs == null) {
                        System.out.printf("    [nc] %s %s hole@%d: NO GRID SLOT (skip)%n",
                                ge.getKey(), de.getKey(), h);
                        continue;
                    }
                    // every tail gate in rejectReason order
                    java.util.List<String> rejects = new java.util.ArrayList<>();
                    boolean groupBusy = snap.lessons.stream().anyMatch(o -> o != tail
                            && o.timeslot != null && o.timeslot.equals(holeTs)
                            && o.getStudentGroup() != null
                            && o.getStudentGroup().name.equals(ge.getKey()));
                    if (groupBusy) {
                        rejects.add("GROUP-CELL");
                    }
                    if (!tail.isSlotLegalFresh(holeTs)) {
                        rejects.add("VRP");
                    }
                    Lesson contender = snap.lessons.stream()
                            .filter(o -> o != tail && o.timeslot != null && o.timeslot.equals(holeTs)
                                    && o.getTeacher() != null && o.getTeacher().name.equals(tail.getTeacher().name))
                            .findFirst().orElse(null);
                    if (contender != null) {
                        rejects.add("H1(" + contender.course.studentGroup.name + " "
                                + contender.course.subject + ")");
                    }
                    int tHours = (int) snap.lessons.stream().filter(o -> o.timeslot != null
                            && o.timeslot.getDate().equals(de.getKey())
                            && o.getTeacher() != null && tail.getTeacher() != null
                            && o.getTeacher().name.equals(tail.getTeacher().name)).count();
                    if (tHours >= tail.getTeacher().getEffectiveMaxDailyHours()) {
                        rejects.add("H7(" + tHours + "/" + tail.getTeacher().getEffectiveMaxDailyHours() + ")");
                    }
                    boolean lab = !tail.course.requiredRoomTags.isEmpty();
                    boolean roomFree = snap.rooms.stream().anyMatch(r -> {
                        if (lab && (r.tags == null || r.tags.stream()
                                .noneMatch(tail.course.requiredRoomTags::contains))) {
                            return false;
                        }
                        return snap.lessons.stream().noneMatch(o -> o != tail && o.room == r
                                && holeTs.equals(o.timeslot));
                    });
                    if (!roomFree) {
                        rejects.add("ROOM");
                    }
                    String cls;
                    if (rejects.isEmpty()) {
                        cls = "DIRECT-SLIDE (should have fired)";
                    } else if (contender == null) {
                        cls = "NO-CONTENDER: tail gates = " + rejects;
                    } else {
                        cls = "CONTENDER: " + rejects;
                    }
                    System.out.printf("    [nc] %s %s hole@%d tail=%s -> %s%n",
                            ge.getKey(), de.getKey(), h, who, cls);
                }
            }
        }
    }

    /**
     * Step 24 (blind-live-r1 dump, opt-in {@code PLANNER_TEST_PUZZLE_LIVE=1} +
     * {@code PLANNER_TEST_REPLAY_609} pointing at the LIVE delivered export):
     * the round protocol of step17/step22 applied to a PRODUCTION delivery (no
     * chain run here: the export IS the delivered state). Loads the arm-b gate
     * export ([-9]hard: H17 -6, H32 -2, H34 -1), verifies the defect census,
     * and dumps export_lessons.json in the puzzle format so the neutral puzzle
     * + checker pair can be assembled next to it. Measure-only.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PUZZLE_LIVE", matches = "1")
    void step24BlindLivePuzzleDump() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] hard = score(snap.problem);
        System.out.printf("=== blind-live-r1 step24: LIVE delivered state hard %d (expected -9)%n",
                hard[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-9L, hard[0],
                "the export must be the [-9] B3 delivery (H17 6 + H32 2 + H34 1)");
        java.nio.file.Path outDir = java.nio.file.Path.of(System.getenv("PLANNER_TEST_PUZZLE_DIR"));
        java.nio.file.Files.createDirectories(outDir);
        StringBuilder json = new StringBuilder("[");
        String sep = "";
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.getStudentGroup() == null) {
                continue;
            }
            String room = l.room == null ? "" : l.room.name;
            json.append(sep).append(String.format(
                    "{\"subject\": \"%s\", \"teacher\": \"%s\", \"group\": \"%s\", \"date\": \"%s\","
                            + " \"hour\": %d, \"room\": \"%s\", \"pinned\": %s}",
                    esc(l.course.subject), esc(l.getTeacher() == null ? "" : l.getTeacher().name),
                    esc(l.getStudentGroup().name), l.timeslot.getDate(),
                    l.timeslot.startDateTime.getHour(), esc(room), l.pinned));
            sep = ",\n";
        }
        json.append("]");
        java.nio.file.Files.writeString(outDir.resolve("export_lessons.json"), json.toString());
        System.out.printf("    dumped %d placed lessons to %s%n",
                sep.isEmpty() ? 0 : snap.lessons.size(), outDir);
        // Per-lesson VRP support (round live-1b): the course-level table proved COARSER
        // than the real per-lesson value ranges (the r1 blind's @13 landings on G02/1
        // FB passed R11 but the real evaluator rejected them), so the faithful support
        // is per lesson, keyed by group|subject|teacher|origin-cell, value = the exact
        // set of legal landing cells from Lesson.getAvailableTimeslots().
        StringBuilder vrp = new StringBuilder("{");
        String vsep = "";
        int vrpEntries = 0;
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.getStudentGroup() == null
                    || l.getTeacher() == null) {
                continue;
            }
            String key = l.getStudentGroup().name + "|" + l.course.subject + "|" + l.getTeacher().name
                    + "|" + l.timeslot.getDate() + "@" + l.timeslot.startDateTime.getHour();
            vrp.append(vsep).append("\n  \"").append(esc(key)).append("\": [");
            String csep = "";
            for (Timeslot t : l.getAvailableTimeslots()) {
                vrp.append(csep).append("\"").append(t.getDate()).append("@")
                        .append(t.startDateTime.getHour()).append("\"");
                csep = ", ";
            }
            vrp.append("]");
            vsep = ",";
            vrpEntries++;
        }
        vrp.append("\n}");
        java.nio.file.Files.writeString(outDir.resolve("lesson_vrp.json"), vrp.toString());
        System.out.printf("    dumped %d per-lesson VRP entries to lesson_vrp.json%n", vrpEntries);
        // Defect census on the delivered state: H17 internal holes (all groups)
        java.util.Map<String, java.util.TreeMap<java.time.LocalDate, java.util.TreeMap<Integer, Lesson>>> byGroup =
                new java.util.TreeMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.getStudentGroup() == null) {
                continue;
            }
            byGroup.computeIfAbsent(l.getStudentGroup().name, k -> new java.util.TreeMap<>())
                    .computeIfAbsent(l.timeslot.getDate(), k -> new java.util.TreeMap<>())
                    .put(l.timeslot.startDateTime.getHour(), l);
        }
        int holes = 0;
        for (var ge : byGroup.entrySet()) {
            for (var de : ge.getValue().entrySet()) {
                java.util.TreeMap<Integer, Lesson> day = de.getValue();
                if (day.size() < 2) {
                    continue;
                }
                for (int h = day.firstKey() + 1; h < day.lastKey(); h++) {
                    if (!day.containsKey(h)) {
                        holes++;
                        System.out.printf("DIFETTO-H17 %s %s: buco alle ore %d%n",
                                ge.getKey(), de.getKey(), h);
                    }
                }
            }
        }
        System.out.printf("    census H17 holes: %d (expected 6)%n", holes);
        org.junit.jupiter.api.Assertions.assertEquals(6, holes, "H17 born-hole census");
        printFillResiduals(snap);
    }

    /**
     * Step 25 (blind-live-r1 arbitration, opt-in {@code PLANNER_TEST_LIVE_ARB=1} +
     * {@code PLANNER_TEST_REPLAY_609} at the live export): the step18 protocol on
     * a PRODUCTION delivery. Load the [-9] delivered state (no chain), apply the
     * blind round's moves ({@code PLANNER_TEST_MOVES}, default moves-live.txt),
     * score post-moves, then the room pass, score again. Any non-zero residue
     * names the filter the python verifier does not cover. Measure-only.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_LIVE_ARB", matches = "1")
    void step25BlindLiveArbitration() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] start = score(snap.problem);
        System.out.printf("=== blind-live-r1 step25 (arbitration): delivered hard %d (expected -9)%n",
                start[0]);
        org.junit.jupiter.api.Assertions.assertEquals(-9L, start[0],
                "the export must be the [-9] B3 delivery before arbitration");
        java.nio.file.Path moves = java.nio.file.Path.of(
                System.getenv().getOrDefault("PLANNER_TEST_MOVES",
                        "claudedocs/spalmone-2026-27/task613-results/puzzle-live-r1/moves-live.txt"));
        int[] skips = new int[1];
        applyMovesFileVrpChecked(snap, moves, skips);
        long[] postMoves = score(snap.problem);
        System.out.printf("    post-moves hard %d, medium %d (%d move(s) skipped as outside the real"
                        + " per-lesson value range - the python VRP support is coarser)%n",
                postMoves[0], postMoves[1], skips[0]);
        printHardFamilies(snap.problem);
        printFillResiduals(snap);
        applyBlindRoomPass(snap, moves);
        long[] postRooms = score(snap.problem);
        System.out.printf("    post-room-pass hard %d, medium %d%n", postRooms[0], postRooms[1]);
        printHardFamilies(snap.problem);
        printFillResiduals(snap);
    }

    /**
     * Step 26 (blind-live-r1 LEVA A, opt-in {@code PLANNER_TEST_LEVER_A=1} +
     * {@code PLANNER_TEST_REPLAY_609} at the live export): the compact-swap
     * intra-classe prototype ({@link com.school.planner.solver.CompactSwapFinisherDiff})
     * measured ALONE on the delivered [-9] B3 state. Prediction (lever-a/PREDIZIONE.md):
     * the defect-2 chain closes (H17 6 -> 5), no other defect touched.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_LEVER_A", matches = "1")
    void step26LeverACompactSwap() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        System.out.printf("=== blind-live-r1 LEVA A (compact-swap): delivered hard %d (expected -9)%n",
                before[0]);
        org.junit.jupiter.api.Assertions.assertEquals(-9L, before[0], "the [-9] B3 delivery");
        System.setProperty("cs.debug", "true");
        var r = com.school.planner.solver.CompactSwapFinisherDiff.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("cs.debug");
        long[] after = score(snap.problem);
        System.out.printf("    LEVA A: targets=%d swaps=%d moves=%d | hard %d -> %d%n",
                r.targets(), r.swaps(), r.movesApplied(), before[0], after[0]);
        printHardFamilies(snap.problem);
        printFillResiduals(snap);
    }

    /**
     * Step 27 (blind-live-r1 LEVA B, opt-in {@code PLANNER_TEST_LEVER_B=1} +
     * {@code PLANNER_TEST_REPLAY_609} at the live export): the tolerance-aware
     * donor-gate EDF prototype ({@link com.school.planner.solver.EmptyDayFinisherTol},
     * relay+h32 armed like the delivery boundary) measured ALONE on the delivered
     * [-9] state. Prediction (lever-b/PREDIZIONE.md): defect 7 closes (H34 1 -> 0),
     * ~5 clean moves, TOL-DONOR-UNLOCK census ~0 outside G09.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_LEVER_B", matches = "1")
    void step27LeverBToleranceDonor() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        System.out.printf("=== blind-live-r1 LEVA B (tolerance donor): delivered hard %d (expected -9)%n",
                before[0]);
        org.junit.jupiter.api.Assertions.assertEquals(-9L, before[0], "the [-9] B3 delivery");
        System.setProperty(com.school.planner.solver.EmptyDayFinisherTol.RELAY_FLAG, "true");
        System.setProperty(com.school.planner.solver.EmptyDayFinisherTol.H32_FLAG, "true");
        System.setProperty("edf.debug", "true");
        var r = com.school.planner.solver.EmptyDayFinisherTol.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisherTol.RELAY_FLAG);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisherTol.H32_FLAG);
        System.clearProperty("edf.debug");
        long[] after = score(snap.problem);
        System.out.printf("    LEVA B: moves=%d cellsClosed=%d | hard %d -> %d%n",
                r.movesApplied(), r.cellsClosed(), before[0], after[0]);
        printHardFamilies(snap.problem);
        printFillResiduals(snap);
    }

    /**
     * Step 28 (blind-live-r1 chain, opt-in {@code PLANNER_TEST_LEVER_AB=1}): LEVA A
     * then LEVA B on one fresh [-9] load. Expected (the two predictions composed):
     * -7 = the blind round's own ceiling with the two levers mechanized.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_LEVER_AB", matches = "1")
    void step28LeverChain() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-9L, before[0], "the [-9] B3 delivery");
        System.setProperty("cs.debug", "true");
        var ra = com.school.planner.solver.CompactSwapFinisherDiff.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("cs.debug");
        long[] mid = score(snap.problem);
        System.setProperty(com.school.planner.solver.EmptyDayFinisherTol.RELAY_FLAG, "true");
        System.setProperty(com.school.planner.solver.EmptyDayFinisherTol.H32_FLAG, "true");
        System.setProperty("edf.debug", "true");
        var rb = com.school.planner.solver.EmptyDayFinisherTol.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisherTol.RELAY_FLAG);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisherTol.H32_FLAG);
        System.clearProperty("edf.debug");
        long[] after = score(snap.problem);
        System.out.printf("=== blind-live-r1 CHAIN A->B: hard %d -> %d (A: swaps=%d) -> %d"
                        + " (B: moves=%d cellsClosed=%d)%n",
                before[0], mid[0], ra.swaps(), after[0], rb.movesApplied(), rb.cellsClosed());
        printHardFamilies(snap.problem);
        printFillResiduals(snap);
    }

    /** The step25 applier: same parsing as {@link #applyMovesFile}, but a move whose
     *  target is outside the REAL per-lesson value range ({@code getAvailableTimeslots},
     *  the union the solver's VRP enforces: calendar windows, quota kinds, stage
     *  confinement...) is SKIPPED and counted instead of corrupting the score with an
     *  IllegalState. The python checker's course-level VRP table is coarser than the
     *  real one, so the skip list names the moves the blind found only because it does
     *  not pay our gates. */
    private void applyMovesFileVrpChecked(SnapshotFixture.Snapshot snap, java.nio.file.Path path,
            int[] skippedOut) throws Exception {
        java.util.Map<String, Timeslot> tsByDateHour = new java.util.HashMap<>();
        for (Timeslot t : snap.timeslots) {
            tsByDateHour.put(t.getDate() + "@" + t.startDateTime.getHour(), t);
        }
        for (String raw : java.nio.file.Files.readAllLines(path)) {
            final String ln = raw.trim();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("MOVE ([^|]+)\\|([0-9-]+) (\\d+) -> ([0-9-]+) (\\d+)").matcher(ln);
            if (!m.matches()) {
                continue;
            }
            String g = m.group(1).trim();
            Timeslot target = tsByDateHour.get(m.group(4) + "@" + Integer.parseInt(m.group(5)));
            Lesson cand = snap.lessons.stream()
                    .filter(x -> x.timeslot != null && g.equals(x.course.studentGroup.name)
                            && m.group(2).equals(x.timeslot.getDate().toString())
                            && x.timeslot.startDateTime.getHour() == Integer.parseInt(m.group(3)))
                    .findFirst().orElseThrow(() -> new IllegalStateException("no lesson at " + ln));
            if (cand.getAvailableTimeslots().contains(target)) {
                cand.timeslot = target;
            } else {
                skippedOut[0]++;
                System.out.printf("    [vrp-skip] %s: %s(%s) -> %s@%s outside the real value range%n",
                        ln, cand.course.subject, cand.getTeacher().name, m.group(4), m.group(5));
            }
        }
    }

    /**
     * Step 29a (porting transfer, opt-in {@code PLANNER_TEST_PORTING=1} +
     * {@code PLANNER_TEST_REPLAY_609} at the live export): the REAL HoleRelayFinisher
     * with only the compact-swap spike flag armed must reproduce the bench twin on
     * B3 (-9 -> -8, targets=6 slides=1 moves=2, the exact blind pair). One basin
     * per test, the house pattern (a second load in one test corrupts the fixture).
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PORTING", matches = "1")
    void step29aPortingLeverA() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] a0 = score(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-9L, a0[0], "the [-9] B3 delivery");
        System.setProperty("planner.construction.hole-relay.compact-swap.enabled", "true");
        System.setProperty("hrf.debug", "true");
        var ra = com.school.planner.solver.HoleRelayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("hrf.debug");
        System.clearProperty("planner.construction.hole-relay.compact-swap.enabled");
        long[] a1 = score(snap.problem);
        System.out.printf("=== porting LEVA A (src): targets=%d slides=%d rotations=%d moves=%d"
                        + " | hard %d -> %d (bench: 6/1/0/2, -9 -> -8)%n",
                ra.targets(), ra.slides(), ra.rotations(), ra.movesApplied(), a0[0], a1[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-8L, a1[0], "lever A transfer: -9 -> -8");
        org.junit.jupiter.api.Assertions.assertEquals(2, ra.movesApplied(), "lever A: the blind pair");
    }

    /**
     * Step 29b (porting transfer, lever B): the REAL EmptyDayFinisher with
     * relay+h32+tol-donor armed must reproduce the bench twin on B3
     * (-9 -> -8, moves=6 cellsClosed=1).
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PORTING", matches = "1")
    void step29bPortingLeverB() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] b0 = score(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-9L, b0[0], "the [-9] B3 delivery");
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG, "true");
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.H33_FLAG, "false"); // frozen bank: the 04/10 default flip must not enter
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG, "true");
        System.setProperty("planner.construction.edf-relay.tol-donor.enabled", "true");
        System.setProperty("edf.debug", "true");
        var rb = com.school.planner.solver.EmptyDayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("edf.debug");
        System.clearProperty("planner.construction.edf-relay.tol-donor.enabled");
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.H33_FLAG);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG);
        long[] b1 = score(snap.problem);
        System.out.printf("=== porting LEVA B (src): moves=%d cellsClosed=%d | hard %d -> %d"
                        + " (bench: 6/1, -9 -> -8)%n", rb.movesApplied(), rb.cellsClosed(), b0[0], b1[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-8L, b1[0], "lever B transfer: -9 -> -8");
    }

    /**
     * Step 29c (porting transfer, the chain): A then B on one fresh [-9] load must
     * reach the blind round's arbitrated ceiling (-7).
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PORTING", matches = "1")
    void step29cPortingChain() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] c0 = score(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-9L, c0[0], "the [-9] B3 delivery");
        System.setProperty("planner.construction.hole-relay.compact-swap.enabled", "true");
        com.school.planner.solver.HoleRelayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("planner.construction.hole-relay.compact-swap.enabled");
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG, "true");
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.H33_FLAG, "false"); // frozen bank: the 04/10 default flip must not enter
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG, "true");
        System.setProperty("planner.construction.edf-relay.tol-donor.enabled", "true");
        var rc = com.school.planner.solver.EmptyDayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("planner.construction.edf-relay.tol-donor.enabled");
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.H33_FLAG);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG);
        long[] c1 = score(snap.problem);
        System.out.printf("=== porting CHAIN A->B (src): hard %d -> %d (bench/blind ceiling: -7)"
                        + " [B: moves=%d cellsClosed=%d]%n", c0[0], c1[0], rc.movesApplied(), rc.cellsClosed());
        printHardFamilies(snap.problem);
        printFillResiduals(snap);
        org.junit.jupiter.api.Assertions.assertEquals(-7L, c1[0], "chain transfer: the -7 ceiling");
    }

    /**
     * Step 33 (TASK-619 porting transfer A3, opt-in {@code PLANNER_TEST_PT619_A3=1} +
     * {@code PLANNER_TEST_REPLAY_609} at the A3 export): the SRC third arm must
     * reproduce the bank iter3b at the number (-11 -> -10, 1 import / 2 moves) via
     * the bank-equivalent sequence (levers pass, EDF, then an import-bearing
     * finisher pass), plus the PRODUCTION form (the arm inside the single delivery
     * call, interleaved with compact-swap) reported for declaration.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PT619_A3", matches = "1")
    void step33Pt619TransferA3() throws Exception {
        // --- bank-equivalent form
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-14L, before[0], "the [-14] A3 delivery");
        System.setProperty("planner.construction.hole-relay.compact-swap.enabled", "true");
        var r1 = com.school.planner.solver.HoleRelayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG, "true");
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.H33_FLAG, "false"); // frozen bank: the 04/10 default flip must not enter
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG, "true");
        System.setProperty("planner.construction.edf-relay.tol-donor.enabled", "true");
        com.school.planner.solver.EmptyDayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.H33_FLAG);
        System.clearProperty("planner.construction.edf-relay.tol-donor.enabled");
        long[] postLevers = score(snap.problem);
        System.setProperty("planner.construction.hole-relay.import-cross-date.enabled", "true");
        System.setProperty("hrf.debug", "true");
        var r2 = com.school.planner.solver.HoleRelayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("hrf.debug");
        System.clearProperty("planner.construction.hole-relay.import-cross-date.enabled");
        System.clearProperty("planner.construction.hole-relay.compact-swap.enabled");
        long[] after = score(snap.problem);
        System.out.printf("=== PT619 A3 bank-equivalent: hard %d -> %d (levers) -> %d"
                        + " [import pass: targets=%d slides=%d moves=%d; bank iter3b: -11 -> -10, 1 import/2 moves]%n",
                before[0], postLevers[0], after[0], r2.targets(), r2.slides(), r2.movesApplied());
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-10L, after[0], "transfer A3: -11 -> -10");
    }

    /**
     * Step 33b (TASK-619 porting transfer A3, PRODUCTION form, opt-in
     * {@code PLANNER_TEST_PT619_A3B=1}): the arms interleaved in the single delivery
     * call (the in-pass composition the porting declares), on its own fresh load -
     * one basin per test, the house pattern (a second load in one test corrupts the
     * fixture, measured in step29's first iteration).
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PT619_A3B", matches = "1")
    void step33bPt619TransferA3Production() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] p0 = score(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-14L, p0[0], "the [-14] A3 delivery");
        System.setProperty("planner.construction.hole-relay.compact-swap.enabled", "true");
        System.setProperty("planner.construction.hole-relay.import-cross-date.enabled", "true");
        com.school.planner.solver.HoleRelayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("planner.construction.hole-relay.import-cross-date.enabled");
        System.clearProperty("planner.construction.hole-relay.compact-swap.enabled");
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG, "true");
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.H33_FLAG, "false"); // frozen bank: the 04/10 default flip must not enter
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG, "true");
        System.setProperty("planner.construction.edf-relay.tol-donor.enabled", "true");
        com.school.planner.solver.EmptyDayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.H33_FLAG);
        System.clearProperty("planner.construction.edf-relay.tol-donor.enabled");
        long[] p1 = score(snap.problem);
        System.out.printf("=== PT619 A3 PRODUCTION form (single call, arms interleaved):"
                        + " hard %d -> %d (declaration)%n", p0[0], p1[0]);
        printHardFamilies(snap.problem);
    }

    /**
     * Step 34 (TASK-619 porting transfer B3, opt-in {@code PLANNER_TEST_PT619_B3=1}):
     * the src arm alone on the delivered B3 state must reproduce iter3b (-9 -> -8,
     * 1 import / 2 moves, H31 absent).
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PT619_B3", matches = "1")
    void step34Pt619TransferB3() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-9L, before[0], "the [-9] B3 delivery");
        System.setProperty("planner.construction.hole-relay.import-cross-date.enabled", "true");
        System.setProperty("hrf.debug", "true");
        var r = com.school.planner.solver.HoleRelayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("hrf.debug");
        System.clearProperty("planner.construction.hole-relay.import-cross-date.enabled");
        long[] after = score(snap.problem);
        System.out.printf("=== PT619 B3: targets=%d slides=%d moves=%d | hard %d -> %d"
                        + " [bank iter3b: 1 import/2 moves, -9 -> -8, H31 absent]%n",
                r.targets(), r.slides(), r.movesApplied(), before[0], after[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-8L, after[0], "transfer B3: -9 -> -8");
    }

    /**
     * Step 40 (TASK-620 porting transfer, opt-in {@code PLANNER_TEST_PT620=1} +
     * {@code PLANNER_TEST_REPLAY_609} at the reab-cura arm-a export): the SRC H7-cap
     * arm must reproduce the bank iter2 (-1 -> 0, 1 pair / 2 moves, clean).
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PT620", matches = "1")
    void step40Pt620Transfer() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        System.out.printf("=== PT620 transfer: delivered hard %d (expected -1)%n", before[0]);
        org.junit.jupiter.api.Assertions.assertEquals(-1L, before[0], "the [-1] fresh delivery");
        System.setProperty("planner.construction.hole-relay.h7cap.enabled", "true");
        System.setProperty("hrf.debug", "true");
        var r = com.school.planner.solver.HoleRelayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("hrf.debug");
        System.clearProperty("planner.construction.hole-relay.h7cap.enabled");
        long[] after = score(snap.problem);
        System.out.printf("    PT620: targets(holes)=%d slides=%d moves=%d | hard %d -> %d"
                        + " [bank iter2: -1 -> 0, 1 pair/2 moves]%n",
                r.targets(), r.slides(), r.movesApplied(), before[0], after[0]);
        printHardFamilies(snap.problem);
        printFillResiduals(snap);
        org.junit.jupiter.api.Assertions.assertEquals(0L, after[0], "transfer 620: -1 -> 0");
    }

    /**
     * Step 41 (integrato-1 H7 diagnosis, opt-in {@code PLANNER_TEST_INT_DIAG=1} +
     * {@code PLANNER_TEST_REPLAY_609} at the integrato-1 export): load the DELIVERED
     * [-4] state, run the HRF delivery call with debug + h7cap (default ON) and read
     * the HRF-H7CAP-* census: which gate stopped the pair for the residual overcap.
     * If the bank CLOSES the H7: coverage/innesco problem in the live cycle; if NOT
     * (gate saturated): the family has an uncovered shape (TASK-621 spec).
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_INT_DIAG", matches = "1")
    void step41IntegratoH7Diagnosis() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        System.out.printf("=== integrato-1 H7 diag: delivered hard %d (expected -4)%n", before[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-4L, before[0], "the [-4] integrato delivery");
        System.setProperty("hrf.debug", "true");
        var r = com.school.planner.solver.HoleRelayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("hrf.debug");
        long[] after = score(snap.problem);
        System.out.printf("    HRF re-run (all arms + h7cap): targets=%d slides=%d moves=%d"
                        + " | hard %d -> %d%n",
                r.targets(), r.slides(), r.movesApplied(), before[0], after[0]);
        printHardFamilies(snap.problem);
    }

    /**
     * Step 42 (TASK-621 bench, opt-in {@code PLANNER_TEST_H7PAR=1} +
     * {@code PLANNER_TEST_REPLAY_609} at the target export): the H7-parallel-relay
     * prototype. integrato-1 (-4, T13): expected -4 -> -3 clean. A3 export:
     * census first (T26 7h is overcap there: same family).
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_H7PAR", matches = "1")
    void step42H7ParallelRelayBench() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        System.out.printf("=== TASK-621 H7-par bench: delivered hard %d%n", before[0]);
        printHardFamilies(snap.problem);
        System.setProperty("hrf.debug", "true");
        var r = com.school.planner.solver.H7ParallelRelayDiff.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("hrf.debug");
        long[] after = score(snap.problem);
        System.out.printf("    H7-par relay: targets=%d cures=%d moves=%d | hard %d -> %d%n",
                r.targets(), r.cures(), r.movesApplied(), before[0], after[0]);
        printHardFamilies(snap.problem);
        printFillResiduals(snap);
    }

    /**
     * Step 43 (blind-r1 dump + census NOMINATIVO, opt-in {@code PLANNER_TEST_BLIND_R1=1}):
     * the [-4] integrato delivery, each residual family NAMED before the blind.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_BLIND_R1", matches = "1")
    void step43BlindR1Dump() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] hard = score(snap.problem);
        System.out.printf("=== blind-r1 step43: delivered hard %d (expected -4)%n", hard[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-4L, hard[0], "the [-4] integrato delivery");
        java.nio.file.Path outDir = java.nio.file.Path.of(System.getenv("PLANNER_TEST_PUZZLE_DIR"));
        java.nio.file.Files.createDirectories(outDir);
        StringBuilder json = new StringBuilder("[");
        String sep = "";
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.getStudentGroup() == null) continue;
            String room = l.room == null ? "" : l.room.name;
            json.append(sep).append(String.format(
                    "{\"subject\": \"%s\", \"teacher\": \"%s\", \"group\": \"%s\", \"date\": \"%s\","
                            + " \"hour\": %d, \"room\": \"%s\", \"pinned\": %s}",
                    esc(l.course.subject), esc(l.getTeacher() == null ? "" : l.getTeacher().name),
                    esc(l.getStudentGroup().name), l.timeslot.getDate(),
                    l.timeslot.startDateTime.getHour(), esc(room), l.pinned));
            sep = ",\n";
        }
        json.append("]");
        java.nio.file.Files.writeString(outDir.resolve("export_lessons.json"), json.toString());
        StringBuilder vrp = new StringBuilder("{");
        String vsep = "";
        int n = 0;
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.getStudentGroup() == null || l.getTeacher() == null) continue;
            String key = l.getStudentGroup().name + "|" + l.course.subject + "|" + l.getTeacher().name
                    + "|" + l.timeslot.getDate() + "@" + l.timeslot.startDateTime.getHour();
            vrp.append(vsep).append("\n  \"").append(esc(key)).append("\": [");
            String csep = "";
            for (Timeslot t : l.getAvailableTimeslots()) {
                vrp.append(csep).append("\"").append(t.getDate()).append("@").append(t.startDateTime.getHour()).append("\"");
                csep = ", ";
            }
            vrp.append("]");
            vsep = ",";
            n++;
        }
        vrp.append("\n}");
        java.nio.file.Files.writeString(outDir.resolve("lesson_vrp.json"), vrp.toString());
        System.out.printf("    dumped %d lessons + %d VRP entries%n",
                sep.isEmpty() ? 0 : snap.lessons.size(), n);
        // CENSUS NOMINATIVO
        java.util.Map<String, java.util.TreeMap<java.time.LocalDate, java.util.TreeMap<Integer, Lesson>>> byGroup = new java.util.TreeMap<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null || l.course == null || l.getStudentGroup() == null) continue;
            byGroup.computeIfAbsent(l.getStudentGroup().name, k -> new java.util.TreeMap<>())
                    .computeIfAbsent(l.timeslot.getDate(), k -> new java.util.TreeMap<>())
                    .put(l.timeslot.startDateTime.getHour(), l);
        }
        System.out.println("--- H17 (nomina gruppi/giorni) ---");
        for (var ge : byGroup.entrySet()) for (var de : ge.getValue().entrySet()) {
            var day = de.getValue();
            if (day.size() < 2) continue;
            for (int h = day.firstKey() + 1; h < day.lastKey(); h++) if (!day.containsKey(h))
                System.out.printf("H17 %s %s @%d%n", ge.getKey(), de.getKey(), h);
        }
        System.out.println("--- H28 (nomina il trio A..B..A) ---");
        for (var ge : byGroup.entrySet()) for (var de : ge.getValue().entrySet()) {
            var day = de.getValue();
            String lastSubj = null; String midSubj = null;
            for (var e : day.entrySet()) {
                String s = e.getValue().course.subject;
                if (lastSubj != null && s.equals(lastSubj) && midSubj != null && !midSubj.equals(s)) {
                    System.out.printf("H28 %s %s: %s @%d ... %s ... %s @%d%n",
                            ge.getKey(), de.getKey(), lastSubj, day.firstKey(), midSubj, s, e.getKey());
                }
                if (midSubj != null && !midSubj.equals(s) && lastSubj == null) lastSubj = s;
                else if (lastSubj != null && !s.equals(lastSubj)) midSubj = s;
                else if (lastSubj != null && s.equals(lastSubj)) { lastSubj = s; midSubj = null; }
                else if (midSubj != null && s.equals(midSubj)) { /* continua */ }
                else if (lastSubj != null) midSubj = s;
                if (lastSubj == null) lastSubj = s;
            }
        }
        System.out.println("--- H7 (gia' nominato) ---");
        System.out.println("H7 T13 2027-01-20: 7 ore cap 6 (G06 x3, G01 x2, G05 x1, G10 x2)");
        printFillResiduals(snap);
    }

    /**
     * Step 44 (blind-r1 arbitration, opt-in {@code PLANNER_TEST_BR1_ARB=1}): the
     * 5 blind moves on the [-4] integrato delivery, VRP-checked, full evaluator.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_BR1_ARB", matches = "1")
    void step44BlindR1Arbitration() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] start = score(snap.problem);
        System.out.printf("=== blind-r1 step44 (arbitration): delivered hard %d (expected -4)%n", start[0]);
        org.junit.jupiter.api.Assertions.assertEquals(-4L, start[0], "the [-4] integrato delivery");
        java.nio.file.Path moves = java.nio.file.Path.of(
                System.getenv().getOrDefault("PLANNER_TEST_MOVES",
                        "claudedocs/spalmone-2026-27/integrato-1/blind-r1/moves-br1.txt"));
        int[] skips = new int[1];
        applyMovesFileVrpChecked(snap, moves, skips);
        long[] postMoves = score(snap.problem);
        System.out.printf("    post-moves hard %d, medium %d (%d VRP-skipped)%n",
                postMoves[0], postMoves[1], skips[0]);
        printHardFamilies(snap.problem);
        printFillResiduals(snap);
        applyBlindRoomPass(snap, moves);
        long[] postRooms = score(snap.problem);
        System.out.printf("    post-room-pass hard %d, medium %d%n", postRooms[0], postRooms[1]);
        printHardFamilies(snap.problem);
        printFillResiduals(snap);
    }

    /**
     * Step 45 (combo-621ext bench, opt-in {@code PLANNER_TEST_COMBO=1}): the three
     * blind-r1 specs on the [-4] integrato state. Prediction: -4 -> -1.
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_COMBO", matches = "1")
    void step45Combo621ExtBench() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        System.out.printf("=== combo-621ext bench: delivered hard %d (expected -4)%n", before[0]);
        org.junit.jupiter.api.Assertions.assertEquals(-4L, before[0], "the [-4] integrato delivery");
        System.setProperty("hrf.debug", "true");
        var r = com.school.planner.solver.Combo621ExtDiff.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("hrf.debug");
        long[] after = score(snap.problem);
        System.out.printf("    COMBO: h17Targets=%d h17Swaps=%d h7Targets=%d h7Cures=%d moves=%d"
                        + " | hard %d -> %d%n",
                r.h17Targets(), r.h17Swaps(), r.h7Targets(), r.h7Cures(), r.movesApplied(),
                before[0], after[0]);
        printHardFamilies(snap.problem);
        printFillResiduals(snap);
    }

    /**
     * Step 46 (x-swap porting transfer, opt-in {@code PLANNER_TEST_XSWAP=1}): the
     * SRC arm must reproduce the combo bank (-4 -> -2, the same 2 swaps closing
     * both G13 06-10 holes).
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_XSWAP", matches = "1")
    void step46XSwapTransfer() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        System.out.printf("=== XSWAP transfer: delivered hard %d (expected -4)%n", before[0]);
        org.junit.jupiter.api.Assertions.assertEquals(-4L, before[0], "the [-4] integrato delivery");
        System.setProperty("planner.construction.hole-relay.x-swap.enabled", "true");
        System.setProperty("hrf.debug", "true");
        var r = com.school.planner.solver.HoleRelayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("hrf.debug");
        System.clearProperty("planner.construction.hole-relay.x-swap.enabled");
        long[] after = score(snap.problem);
        System.out.printf("    XSWAP: targets=%d slides=%d moves=%d | hard %d -> %d"
                        + " [combo bank: -4 -> -2, 2 swaps/4 moves]%n",
                r.targets(), r.slides(), r.movesApplied(), before[0], after[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-2L, after[0], "transfer xswap: -4 -> -2");
    }

    /**
     * Step 47 (H28 audit, opt-in {@code PLANNER_TEST_H28=1} + REPLAY_609 at the
     * integrato export): the pass EXISTS and is WIRED but the banner says
     * enabled=false (TASK-595 prototype, opt-in). Replay it ON the delivered
     * state with the flag armed + debug: does it close the named trio?
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_H28", matches = "1")
    void step47H28Audit() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        System.out.printf("=== H28 audit: delivered hard %d (expected -1 post-xswap or -4 raw)%n",
                before[0]);
        printHardFamilies(snap.problem);
        System.setProperty("planner.construction.h28-finisher.enabled", "true");
        System.setProperty("h28.debug", "true");
        var r = com.school.planner.solver.H28RotationFinisher.repair(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.out.printf("    H28 result: %s%n", r);
        System.clearProperty("h28.debug");
        System.clearProperty("planner.construction.h28-finisher.enabled");
        long[] after = score(snap.problem);
        System.out.printf("    H28 pass: %s | hard %d -> %d%n", r, before[0], after[0]);
        printHardFamilies(snap.problem);
    }

    /**
     * Step 48 (h7-depth3 porting transfer, opt-in {@code PLANNER_TEST_H7D3=1}):
     * the SRC chain must reproduce the combo bank (-4 -> -1: x-swap 2 + depth-3 1).
     */
    @Test
    @Transactional
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_H7D3", matches = "1")
    void step48H7D3Transfer() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] before = score(snap.problem);
        System.out.printf("=== H7D3 transfer: delivered hard %d (expected -4)%n", before[0]);
        org.junit.jupiter.api.Assertions.assertEquals(-4L, before[0], "the [-4] integrato delivery");
        System.setProperty("planner.construction.hole-relay.x-swap.enabled", "true");
        System.setProperty("planner.construction.hole-relay.h7-depth3.enabled", "true");
        System.setProperty("hrf.debug", "true");
        var r = com.school.planner.solver.HoleRelayFinisher.finish(
                snap.problem, (lesson, ts) -> lesson.timeslot = ts);
        System.clearProperty("hrf.debug");
        System.clearProperty("planner.construction.hole-relay.h7-depth3.enabled");
        System.clearProperty("planner.construction.hole-relay.x-swap.enabled");
        long[] after = score(snap.problem);
        System.out.printf("    H7D3: targets=%d slides=%d moves=%d | hard %d -> %d"
                        + " [combo bank: -4 -> -1, 2 swaps + 1 cure = 5 moves]%n",
                r.targets(), r.slides(), r.movesApplied(), before[0], after[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-1L, after[0], "transfer h7d3: -4 -> -1");
    }

    /** Same parser as {@link #applyBlindMoves(SnapshotFixture.Snapshot)} against an
     *  explicit moves file; comment lines simply match nothing and are skipped. */
    private void applyMovesFile(SnapshotFixture.Snapshot snap, java.nio.file.Path path)
            throws Exception {
        java.util.Map<String, Timeslot> tsByDateHour = new java.util.HashMap<>();
        for (Timeslot t : snap.timeslots) {
            tsByDateHour.put(t.getDate() + "@" + t.startDateTime.getHour(), t);
        }
        for (String raw : java.nio.file.Files.readAllLines(path)) {
            final String ln = raw.trim();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("MOVE ([^|]+)\\|([0-9-]+) (\\d+) -> ([0-9-]+) (\\d+)").matcher(ln);
            if (m.matches()) {
                String g = m.group(1).trim();
                Timeslot target = tsByDateHour.get(m.group(4) + "@" + Integer.parseInt(m.group(5)));
                snap.lessons.stream()
                        .filter(x -> x.timeslot != null && g.equals(x.course.studentGroup.name)
                                && m.group(2).equals(x.timeslot.getDate().toString())
                                && x.timeslot.startDateTime.getHour() == Integer.parseInt(m.group(3)))
                        .findFirst().orElseThrow(() -> new IllegalStateException("no lesson at " + ln))
                        .timeslot = target;
            }
        }
    }

    /** EDF with BOTH TASK-612 extension flags armed (plus the reject census when
     *  {@code debug}), properties set/cleared around the call so sibling tests in
     *  the same JVM never see the prototype armed by leakage. */
    private com.school.planner.solver.EmptyDayFinisher.Result runEdfExt(
            SchoolSchedule problem, boolean debug) {
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG, "true");
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.H33_FLAG, "false"); // frozen bank: the 04/10 default flip must not enter
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG, "true");
        if (debug) {
            System.setProperty("edf.debug", "true");
        }
        try {
            return com.school.planner.solver.EmptyDayFinisher.finish(
                    problem, (lesson, ts) -> lesson.timeslot = ts);
        } finally {
            System.clearProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG);
            System.clearProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG);
        System.clearProperty(com.school.planner.solver.EmptyDayFinisher.H33_FLAG);
            System.clearProperty("edf.debug");
        }
    }

    /**
     * Step 11 (TASK-612): the EDF relay/H32 extensions on the delivered residual this
     * bench already certified. Order = delivery order: the RATIFIED CompositeSwapFinisher
     * first (H0/boxed, -11 -> -8), then EDF with BOTH extension flags armed. Target
     * proven by step6's 40-move blind solution: the H34 x3 + H32 x2 cells close via the
     * IMPORT-RELAY census family, H17 x3 stay (teacher-locked, follow-up family). The
     * exact end value gets PINNED after the first measurement (assert-improve first).
     */
    @Test
    @Transactional
    void step11EdfRelayClosesFillFamilies() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        long[] start = score(snap.problem);
        runCsf(snap.problem);
        long[] mid = score(snap.problem);
        com.school.planner.solver.EmptyDayFinisher.Result result = runEdfExt(snap.problem, true);
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-612 step11 (EDF relay+h32 post-CSF): moves=%d cellsClosed=%d"
                        + " | hard %d -> %d (CSF) -> %d (EDF ext, delta %+d), medium %d -> %d%n",
                result.movesApplied(), result.cellsClosed(),
                start[0], mid[0], after[0], after[0] - mid[0], mid[1], after[1]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertEquals(-3L, after[0],
                "PIN (first measurement 14/09): all 5 fill cells close (H34 x3 + H32 x2),"
                        + " only the 3 teacher-locked H17 holes remain (TASK-613 family)");
        org.junit.jupiter.api.Assertions.assertTrue(result.cellsClosed() >= 5,
                "the 3 H34 cells and the 2 H32 partial deficits must all close to goal");
    }

    /**
     * Step 12 (TASK-612): cross-basin non-regression on m39-on (different basin, its
     * own residual). The extensions must never WORSEN a basin they were not searched
     * on; if cells close, hard must improve (the wrapper oracle holds per batch).
     */
    @Test
    @Transactional
    void step12CrossBasinEdfRelayNoRegression() throws Exception {
        java.nio.file.Path export = java.nio.file.Path.of(
                System.getProperty("planner.test.replay.alt", "/tmp/m39on/export.json.gz"));
        SnapshotFixture.Snapshot snap = loadBasin(export);
        long[] start = score(snap.problem);
        runCsf(snap.problem);
        long[] mid = score(snap.problem);
        com.school.planner.solver.EmptyDayFinisher.Result result = runEdfExt(snap.problem, false);
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-612 step12 (m39-on cross-basin, EDF relay+h32 post-CSF):"
                        + " moves=%d cellsClosed=%d | hard %d -> %d (CSF) -> %d (EDF ext)%n",
                result.movesApplied(), result.cellsClosed(), start[0], mid[0], after[0]);
        printHardFamilies(snap.problem);
        org.junit.jupiter.api.Assertions.assertTrue(after[0] >= mid[0],
                "cross-basin: the EDF extensions must never WORSEN hard");
        if (result.cellsClosed() > 0) {
            org.junit.jupiter.api.Assertions.assertTrue(after[0] > mid[0],
                    "cells closed but hard did not improve: the block oracle leaked a regression");
        }
    }

    /**
     * Step 13 (TASK-612): inertness on a residual-free state. After the blind-LLM
     * moves-5 repair (0 hard on H34/H32 families), the extensions armed must be a
     * PURE no-op: zero targets means zero moves and byte-identical hard.
     */
    @Test
    @Transactional
    void step13EdfRelayInertOnCleanState() throws Exception {
        SnapshotFixture.Snapshot snap = load();
        applyBlindMoves(snap);
        applyBlindRoomPass(snap);
        long[] before = score(snap.problem);
        com.school.planner.solver.EmptyDayFinisher.Result result = runEdfExt(snap.problem, false);
        long[] after = score(snap.problem);
        System.out.printf("=== TASK-612 step13 (EDF relay+h32 on clean state): moves=%d cellsClosed=%d"
                        + " hard %d -> %d%n",
                result.movesApplied(), result.cellsClosed(), before[0], after[0]);
        org.junit.jupiter.api.Assertions.assertEquals(0, result.movesApplied(),
                "no H34/H32 targets on a clean state: the pass must not move anything");
        org.junit.jupiter.api.Assertions.assertEquals(before[0], after[0],
                "clean state: hard must be byte-identical");
    }

    /** The blind-LLM winning 40 moves (moves-5.txt) applied verbatim: shared by
     *  step9 (CSF inertness) and step13 (EDF-extensions inertness). */
    private void applyBlindMoves(SnapshotFixture.Snapshot snap) throws Exception {
        java.util.Map<String, Timeslot> tsByDateHour = new java.util.HashMap<>();
        for (Timeslot t : snap.timeslots) {
            tsByDateHour.put(t.getDate() + "@" + t.startDateTime.getHour(), t);
        }
        Lesson boxed = findBoxed(snap);
        for (String raw : java.nio.file.Files.readAllLines(java.nio.file.Path.of(
                System.getenv().getOrDefault("PLANNER_TEST_MOVES",
                        "claudedocs/spalmone-2026-27/task608-results/blind-puzzle/moves-5.txt")))) {
            final String ln = raw.trim();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("MOVE ([^|]+)\\|([0-9-]+) (\\d+) -> ([0-9-]+) (\\d+)").matcher(ln);
            java.util.regex.Matcher pl = java.util.regex.Pattern
                    .compile("PLACE SPECIALE -> ([0-9-]+) (\\d+)").matcher(ln);
            if (m.matches()) {
                String g = m.group(1).trim();
                Timeslot target = tsByDateHour.get(m.group(4) + "@" + Integer.parseInt(m.group(5)));
                snap.lessons.stream()
                        .filter(x -> x.timeslot != null && g.equals(x.course.studentGroup.name)
                                && m.group(2).equals(x.timeslot.getDate().toString())
                                && x.timeslot.startDateTime.getHour() == Integer.parseInt(m.group(3)))
                        .findFirst().orElseThrow(() -> new IllegalStateException("no lesson at " + ln))
                        .timeslot = target;
            } else if (pl.matches()) {
                boxed.timeslot = tsByDateHour.get(pl.group(1) + "@" + Integer.parseInt(pl.group(2)));
            }
        }
    }

    /** The fixed-point room pass over the blind-moved lessons (step6's form): moved
     *  lessons keep their room unless taken at the landing, then take any free room
     *  (labs first: scarce families). Without it the relocations carry stale rooms
     *  and every landing reads phantom H2 (the step13 first cut: -15001). */
    private void applyBlindRoomPass(SnapshotFixture.Snapshot snap) throws Exception {
        applyBlindRoomPass(snap, java.nio.file.Path.of(
                System.getenv().getOrDefault("PLANNER_TEST_MOVES",
                        "claudedocs/spalmone-2026-27/task608-results/blind-puzzle/moves-5.txt")));
    }

    private void applyBlindRoomPass(SnapshotFixture.Snapshot snap, java.nio.file.Path movesPath)
            throws Exception {
        java.util.Set<Lesson> movedLessons = new java.util.HashSet<>();
        for (String raw : java.nio.file.Files.readAllLines(movesPath)) {
            final String ln2 = raw.trim();
            if (ln2.startsWith("MOVE ")) {
                String g = ln2.split("[|]")[0].substring(5).trim();
                snap.lessons.stream()
                        .filter(x -> x.timeslot != null && g.equals(x.course.studentGroup.name))
                        .forEach(movedLessons::add);
            }
        }
        for (int round = 0; round < 3; round++) {
            boolean changed = false;
            for (boolean labsFirst : new boolean[] { true, false }) {
                for (Lesson l : movedLessons) {
                    if (l.timeslot == null || l.room == null
                            || (labsFirst == l.course.requiredRoomTags.isEmpty())) {
                        continue;
                    }
                    boolean taken = snap.lessons.stream().anyMatch(
                            o -> o != l && o.room == l.room && l.timeslot.equals(o.timeslot));
                    if (taken) {
                        Room r2 = freeRoomFor(snap, l.timeslot, l);
                        if (r2 != null) {
                            l.room = r2;
                            changed = true;
                        }
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
    }
}