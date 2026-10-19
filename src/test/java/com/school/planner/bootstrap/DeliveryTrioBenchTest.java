package com.school.planner.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.solver.CompositeSwapFinisher;
import com.school.planner.solver.HoleRelayFinisher;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.ScoreCompat;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;

/**
 * DELIVERY TRIO BENCH (20/09 mattina, mandato Fedora->laptop): two lenses, one
 * harness. Load a DELIVERED state, invoke the wrapper's delivery trio in the
 * wrapper's order (CSF -> EDF[relay+h32, the delivery shape] -> HRF, the
 * CompositeRepairReplay609 direct-write idiom), re-score with families.
 *
 * <p>Bench 1 (TASK-629 v2 confirm): integrato-5's export (delivered [-1], the
 * single H7 = T08 Matematica G01 2027-05-26 wall 13, the LAST hour of
 * the day, teacher at 7h over the 6 cap). The live trio ran on this exact
 * state with the v1 code (donor searched for the first hour only) and left the
 * cell: that is the delivered -1. Prediction (pre-registered): with v2's
 * invariant-based form the cell CLOSES and the state reaches full 0-hard.
 *
 * <p>Bench 2 (LS-variance follow-up): same trio on integrato-1's delivered
 * state ([-4]: G13 2027-06-10 H17 x2 STRUCTURAL per the 630 diagnosis - De
 * Lisi's Thursday 0-3 vacation makes the slide VRP-illegal - plus H7
 * T13 and the rest). Prediction: -4 -> -2, only the structural H17
 * survives. Declared: the 8 in-memory states of the LS-variance bench are
 * gone; these two on-disk states are the whole sample, so the result reads as
 * "the trio's value on the known residue classes", not a rate.
 *
 * <p>Gated by {@code -Dtrio.run=true} and SELF-SKIPPING when the export fixture
 * is absent (the 111MB integrato-5 export is NOT committed: the test reads it from its local path, the PLANNER_TEST_REPLAY_609 pattern), so the normal suite never changes outcome.
 */
@QuarkusTest
class DeliveryTrioBenchTest {

    @Inject DataGenerator dataGenerator;
    @Inject EntityManager em;
    @Inject com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;

    @BeforeEach
    @AfterEach
    @jakarta.transaction.Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
        System.clearProperty("hrf.debug");
        System.clearProperty(HoleRelayFinisher.FLAG);
    }

    @Test
    @Timeout(value = 3600)
    void deliveryTrioOnDeliveredStates() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("trio.run"), "delivery-trio bench: -Dtrio.run=true");
        // -Dtrio.export=<path> overrides the base (default: integrato-5); any
        // room-faithful export works (m39, task604-results, ...)
        Path export = Path.of(System.getProperty("trio.export",
                "claudedocs/spalmone-2026-27/integrato-5/export.json.gz"));
        Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(export),
                "integrato-5 export not present locally (not committed): bench 1 skipped");
        catalogSeedGuard(export);
        System.out.println("[TRIO] === bench 1: 629 v2 confirm on integrato-5 export ===");
        bench1Integrato5(export);
        if ("refined".equals(System.getProperty("trio.pipe"))) {
            clean();
            System.out.println("[TRIO] === bench 4: pipe-roomed + ROOM-ONLY refinement"
                    + " (production room phases, zero timeslot moves), trio + h7swap ===");
            bench4Integrato1RoomedRefined();
            return;
        }
        if (Boolean.getBoolean("trio.pipe")) {
            // isolate bench 3 from bench 1's DB rows and statics (the first take
            // inherited them: phantom H34 -1648, vanished H25/H32, poisoned census)
            clean();
            System.out.println("[TRIO] === bench 3: trio + h7swap on integrato-1 PIPE"
                    + " made room-faithful (CH-only room pass, no timeslot moves) ===");
            bench3Integrato1Roomed();
            return;
        }
        if (Boolean.getBoolean("trio.only1")) {
            System.out.println("[TRIO] bench 2 SKIPPED (-Dtrio.only1)");
            return;
        }
        clean();
        System.out.println("[TRIO] === bench 2: trio on integrato-1 delivered ===");
        bench2Integrato1();
    }

    /**
     * TASK-634: the catalog seed guard, Java side. A catalogued export whose
     * seedHash diverges from the working-tree seed must not replay silently:
     * stale-seed replay manufactures phantom families (the H25 lesson, 20/09).
     * Default DENY: the bench FAILS unless -Dtrio.allow-stale=true declares the
     * divergence intentional. An un-catalogued path only warns (the harness
     * accepts any room-faithful export by design; the guard protects the
     * catalogued states). Hash domain identical to
     * scripts/analyze/post_ls_catalog.py: sha256 over every seed CSV sorted by
     * repo-relative posix path, path bytes then file bytes.
     */
    private static void catalogSeedGuard(Path export) throws Exception {
        Path index = Path.of("claudedocs/spalmone-2026-27/post-ls-catalog/index.json");
        if (!Files.isRegularFile(index)) {
            System.out.println("[TRIO] catalog: index.json absent (guard silent)");
            return;
        }
        String want = export.toAbsolutePath().normalize().toString();
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree(Files.readAllBytes(index));
        String currentSeed = currentSeedHash();
        for (var entry : root.path("entries")) {
            var payload = entry.path("payloads").path("exportJsonGz");
            if (!payload.isTextual()) {
                continue;
            }
            Path entryPath = Path.of(payload.asText()).toAbsolutePath().normalize();
            if (!entryPath.toString().equals(want)) {
                continue;
            }
            String label = entry.path("label").asText("?");
            String seedHash = entry.path("seedHash").asText("");
            if (seedHash.equals(currentSeed)) {
                System.out.println("[TRIO] catalog: " + label + " VALID (seed match)");
                return;
            }
            String msg = "[TRIO] catalog: " + label + " is STALE-SEED (entry seed " + seedHash
                    + " vs working tree " + currentSeed + "): replaying it against the current"
                    + " seed manufactures phantom hard families (the H25 lesson). Declare the"
                    + " divergence with -Dtrio.allow-stale=true if this replay is intentional.";
            if (Boolean.getBoolean("trio.allow-stale")) {
                System.out.println("[TRIO] WARN " + msg);
                return;
            }
            throw new AssertionError(msg);
        }
        System.out.println("[TRIO] WARN catalog: export not catalogued (" + export
                + "): seed freshness UNKNOWN; catalog it (post_ls_catalog.py add) or verify"
                + " its seed by hand.");
    }

    /** The working-tree seed hash, same domain as the catalog tool. */
    private static String currentSeedHash() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/seed"))) {
            files.filter(p -> p.toString().endsWith(".csv"))
                    .map(p -> Map.entry(
                            Path.of("").toAbsolutePath().relativize(p.toAbsolutePath())
                                    .toString().replace('\\', '/'),
                            p))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        try {
                            md.update(e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            md.update(Files.readAllBytes(e.getValue()));
                        } catch (java.io.IOException ex) {
                            throw new java.io.UncheckedIOException(ex);
                        }
                    });
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private void bench1Integrato5(Path export) throws Exception {
        SnapshotFixture.Snapshot snap = loadExport(export);
        printT08Cell(snap.problem, "before");
        Result before = score(snap.problem);
        print(before, "before");
        runTrio(snap.problem);
        printT08Cell(snap.problem, "after ");
        Result after = score(snap.problem);
        print(after, "after ");
        SnapshotFixture.printResidualCells("bench1 post-terna", snap.problem);
        System.out.printf("[TRIO] bench1 delta: hard %d -> %d%n", before.hard, after.hard);
    }

    private void bench2Integrato1() throws Exception {
        SnapshotFixture.Snapshot snap = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            SnapshotFixture.Snapshot s = SnapshotFixture
                    .loadSnapshotNoClean(Path.of("claudedocs/spalmone-2026-27/integrato-1/run"),
                            availabilityExpansionService, true);
            SnapshotFixture.seedMediumTierFacts(dataGenerator, s);
            return s;
        });
        Result before = score(snap.problem);
        print(before, "before");
        runTrio(snap.problem);
        Result after = score(snap.problem);
        print(after, "after ");
        SnapshotFixture.printResidualCells("bench2 post-terna", snap.problem);
        System.out.printf("[TRIO] bench2 delta: hard %d -> %d%n", before.hard, after.hard);
    }

    /**
     * TASK-632b: integrato-1's delivered PIPE state (no rooms in the format) made
     * room-faithful by a CH-only pass: every placed lesson gets a legal family room
     * (Lesson.getAvailableRooms: H-M2 tags + M-LF widening + cap-0 exclusion), greedy
     * deterministic by lesson id, first room free in the cell (H2 clean by
     * construction), ZERO timeslot moves. No refinement LS: M3 (capacity, medium)
     * stays declared base noise, like the pipe statics' phantom H4/H25 families.
     */
    private void bench3Integrato1Roomed() throws Exception {
        // one transaction for load + room pass: seedRooms persists too
        record Base(SnapshotFixture.Snapshot snap, int assigned) {
        }
        Base base = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            SnapshotFixture.Snapshot s = SnapshotFixture
                    .loadSnapshotNoClean(Path.of("claudedocs/spalmone-2026-27/integrato-1/run"),
                            availabilityExpansionService, true);
            SnapshotFixture.seedMediumTierFacts(dataGenerator, s);
            return new Base(s, assignRoomsChOnly(s));
        });
        SnapshotFixture.Snapshot snap = base.snap();
        int assigned = base.assigned();
        long roomNull = snap.problem.getLessons().stream()
                .filter(l -> l.timeslot != null && l.room == null).count();
        System.out.printf("[TRIO] room CH-only pass: assigned %d, room-null residual %d%n",
                assigned, roomNull);
        org.junit.jupiter.api.Assertions.assertEquals(0L, roomNull,
                "the fixture pass must leave zero room-null (mandate's verification)");
        Result before = score(snap.problem);
        print(before, "before");
        runTrio(snap.problem);
        Result after = score(snap.problem);
        print(after, "after ");
        SnapshotFixture.printResidualCells("bench3 post-terna", snap.problem);
        System.out.printf("[TRIO] bench3 delta: hard %d -> %d%n", before.hard, after.hard);
    }

    /**
     * TASK-632 final: bench3's room-faithful base plus a ROOM-ONLY refinement (the
     * production room phases sliced from the guarded main-config copy: the room CH is
     * a no-op on already-initialized rooms, the room LS refines - both phases'
     * selectors move room only, zero timeslot moves by construction). Cost cap: the
     * XML's own 600s wall. Then the standard trio with h7swap armed by the launcher.
     */
    private void bench4Integrato1RoomedRefined() throws Exception {
        record Base(SnapshotFixture.Snapshot snap, int assigned) {
        }
        Base base = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            SnapshotFixture.Snapshot s = SnapshotFixture
                    .loadSnapshotNoClean(Path.of("claudedocs/spalmone-2026-27/integrato-1/run"),
                            availabilityExpansionService, true);
            SnapshotFixture.seedMediumTierFacts(dataGenerator, s);
            return new Base(s, assignRoomsChOnly(s));
        });
        SnapshotFixture.Snapshot snap = base.snap();
        long roomNull = snap.problem.getLessons().stream()
                .filter(l -> l.timeslot != null && l.room == null).count();
        System.out.printf("[TRIO] CH-only pass: assigned %d, room-null %d%n", base.assigned(), roomNull);
        org.junit.jupiter.api.Assertions.assertEquals(0L, roomNull, "fixture pass must leave zero room-null");
        SchoolSchedule refined = refineRoomsOnly(snap.problem);
        Result before = score(refined);
        print(before, "before");
        runTrio(refined);
        Result after = score(refined);
        print(after, "after ");
        System.out.printf("[TRIO] bench4 delta: hard %d -> %d%n", before.hard, after.hard);
    }

    /** Room-only refinement: the production room phases (CH+LS) sliced off the
     * guarded main-config copy. Rooms are initialized, so the CH is a no-op and the
     * LS does the refining under its own 600s wall. */
    SchoolSchedule refineRoomsOnly(SchoolSchedule problem) {
        ai.timefold.solver.core.config.solver.SolverConfig full =
                ai.timefold.solver.core.config.solver.SolverConfig.createFromXmlResource(
                        "lsvar-live-solver-config.xml");
        var phases = full.getPhaseConfigList();
        int boundary = -1;
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i) instanceof ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig cpc
                    && cpc.getCustomPhaseCommandClassList() != null
                    && cpc.getCustomPhaseCommandClassList().stream()
                            .anyMatch(n -> n.getSimpleName().contains("CompactionPhase"))) {
                boundary = i;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(boundary >= 0, "CompactionPhase anchor found");
        full.setPhaseConfigList(phases.subList(boundary + 1, phases.size()));
        full.setEnvironmentMode(ai.timefold.solver.core.config.solver.EnvironmentMode.NON_REPRODUCIBLE);
        full.setSolutionClass(SchoolSchedule.class);
        full.setEntityClassList(java.util.List.of(com.school.planner.domain.Lesson.class));
        long t0 = System.currentTimeMillis();
        ai.timefold.solver.core.api.solver.Solver<SchoolSchedule> solver =
                ai.timefold.solver.core.api.solver.SolverFactory.<SchoolSchedule>create(full).buildSolver();
        SchoolSchedule refined = solver.solve(problem);
        System.out.printf("[TRIO] room-only refinement done in %ds (returned score %s;"
                + " the caller's instance is NOT mutated: the trio must run on the RETURN)%n",
                (System.currentTimeMillis() - t0) / 1000, refined.getScore());
        return refined;
    }

    /** Greedy deterministic room assignment (the fixture pass, see bench3's javadoc).
     * Scarcest-first: tagged lessons (labs) in ascending tag-room-count order, THEN
     * untagged (which can shelter anywhere via the M-LF widening): a plain id-order
     * lets an ordinary lesson waste the only R16 cell and starves the moda lab
     * that needs it (274 room-null on the first try, 0 with this order). */
    int assignRoomsChOnly(SnapshotFixture.Snapshot snap) {
        java.util.List<com.school.planner.domain.Room> rooms = RealSeedFixture.seedRooms();
        SchoolSchedule.setAllRoomsStatic(rooms);
        java.util.Map<String, Long> roomsPerTag = new java.util.HashMap<>();
        for (com.school.planner.domain.Room r : rooms) {
            if (r.tags != null) {
                for (String tag : r.tags) {
                    roomsPerTag.merge(tag, 1L, Long::sum);
                }
            }
        }
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (Lesson l : snap.problem.getLessons()) {
            if (l.room != null && l.timeslot != null) {
                taken.add(l.room.id + "@" + l.timeslot.id);
            }
        }
        java.util.Comparator<Lesson> scarcestFirst = java.util.Comparator
                .<Lesson, Long>comparing(l -> {
                    if (l.course == null || l.course.requiredRoomTags == null
                            || l.course.requiredRoomTags.isEmpty()) {
                        return Long.MAX_VALUE; // untagged last: they fit anywhere
                    }
                    return l.course.requiredRoomTags.stream()
                            .mapToLong(t -> roomsPerTag.getOrDefault(t, 0L)).min().orElse(Long.MAX_VALUE);
                })
                .thenComparing(l -> l.id);
        java.util.List<Lesson> ordered = new java.util.ArrayList<>(snap.problem.getLessons());
        ordered.sort(scarcestFirst);
        int assigned = 0;
        for (Lesson l : ordered) {
            if (l.timeslot == null || l.room != null) {
                continue;
            }
            // pinned lessons short-circuit getAvailableRooms to List.of() when the room
            // is null (the pipe format carries no room column): compute the family
            // candidates directly for them, same rule as the non-pinned branch
            java.util.List<com.school.planner.domain.Room> candidates;
            if (l.pinned) {
                candidates = rooms.stream()
                        .filter(com.school.planner.domain.Room::isAssignable)
                        .filter(r -> l.course == null || l.course.requiredRoomTags == null
                                || l.course.requiredRoomTags.isEmpty()
                                ? true
                                : r.tags != null && r.tags.containsAll(l.course.requiredRoomTags))
                        .sorted(java.util.Comparator.comparing(r -> r.capacity))
                        .toList();
            } else {
                candidates = l.getAvailableRooms();
            }
            for (com.school.planner.domain.Room r : candidates) {
                String cell = r.id + "@" + l.timeslot.id;
                if (taken.add(cell)) {
                    l.room = r;
                    assigned++;
                    break;
                }
            }
        }
        return assigned;
    }

    private SnapshotFixture.Snapshot loadExport(Path export) {
        return io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            SnapshotFixture.Snapshot snap = SnapshotFixture
                    .loadFromExport(export, availabilityExpansionService, true);
            SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);
            SchoolSchedule.setGoldenCoPresenceDepthStatic(
                    CompositeRepairReplay609Test.depthFromSnapshotCourses(snap));
            // TASK-671 fidelity: publish the pool-cap map so the HRF/EDF gates
            // see the same targets production sees (without this, every replay
            // on a pool-cap basin runs with the gate un-armed: the 'identical
            // regression' numbers are byte-identical by construction).
            SchoolSchedule.setM20PoolCapTargetsStatic(
                    com.school.planner.solver.SchoolScheduleConstraintProvider.computeM20PoolCapTargetsByGroup(
                            snap.problem.getStudentGroups(), snap.problem.getLessons(),
                            snap.problem.getDailyActivityQuotas()));
            SchoolSchedule.captureM20CapPoolFlag();
            return snap;
        });
    }

    /** The wrapper's pre-save order: CSF, then EDF (relay+h32), then HRF. */



    void runTrio(SchoolSchedule problem) {
        System.out.println("[TRIO] --- CSF ---");
        System.setProperty(CompositeSwapFinisher.FLAG, "true");
        try {
            CompositeSwapFinisher.finish(problem, (lesson, ts) -> lesson.timeslot = ts);
        } finally {
            System.clearProperty(CompositeSwapFinisher.FLAG);
        }
        System.out.println("[TRIO] --- EDF (relay + h32) ---");
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG, "true");
        System.setProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG, "true");
        try {
            System.setProperty(com.school.planner.solver.EmptyDayFinisher.H33_FLAG, "false"); // frozen bank
            var edf = com.school.planner.solver.EmptyDayFinisher.finish(
                    problem, (lesson, ts) -> lesson.timeslot = ts);
            System.out.printf("[TRIO] EDF result: %s%n", edf);
        } finally {
            System.clearProperty(com.school.planner.solver.EmptyDayFinisher.RELAY_FLAG);
            System.clearProperty(com.school.planner.solver.EmptyDayFinisher.H32_FLAG);
        }
        System.out.println("[TRIO] --- HRF (hrf.debug census) ---");
        System.setProperty("hrf.debug", "true");
        System.setProperty(HoleRelayFinisher.FLAG, "true");
        try {
            var hrf = HoleRelayFinisher.finish(problem, (lesson, ts) -> lesson.timeslot = ts);
            System.out.printf("[TRIO] HRF result: targets=%d slides=%d rotations=%d moves=%d%n",
                    hrf.targets(), hrf.slides(), hrf.rotations(), hrf.movesApplied());
        } finally {
            System.clearProperty("hrf.debug");
            System.clearProperty(HoleRelayFinisher.FLAG);
        }
    }

    private void printT08Cell(SchoolSchedule problem, String label) {
        int t08 = 0;
        int day = 0;
        for (Lesson l : problem.getLessons()) {
            if (l.timeslot == null || l.getStudentGroup() == null || l.getTeacher() == null) {
                continue;
            }
            if (!"G01".equals(l.getStudentGroup().name) || !"T08".equals(l.getTeacher().name)) {
                continue;
            }
            if (l.timeslot.getDate().toString().equals("2027-05-26")) {
                t08++;
            }
            day++;
        }
        System.out.printf("[TRIO] %s T08 2027-05-26 on G01: %d lesson(s); T08 total that date-window day counter=%d%n",
                label, t08, day);
    }

    record Result(long hard, long medium, Map<String, long[]> families) {
    }

    void print(Result r, String label) {
        System.out.printf("[TRIO] %s hard=%d medium=%d%n", label, r.hard, r.medium);
        r.families.forEach((id, v) -> System.out.printf("[TRIO] %s   %s: h=%d matches=%d%n",
                label, id, v[0], v[1]));
    }

    Result score(SchoolSchedule problem) {
        SolverFactory<SchoolSchedule> factory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class)));
        SolutionManager<SchoolSchedule, BendableScore> manager = SolutionManager.create(factory);
        manager.update(problem);
        var directorFactory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) manager)
                .getScoreDirectorFactory();
        try (var director = directorFactory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            director.setWorkingSolution(problem);
            BendableScore total = director.calculateScore().raw();
            Map<String, long[]> families = new TreeMap<>();
            for (var entry : director.getConstraintMatchTotalMap().entrySet()) {
                ConstraintMatchTotal<BendableScore> t = entry.getValue();
                long h = ScoreCompat.hardOf(t.getScore());
                if (h < 0) {
                    families.put(entry.getKey().id(), new long[] { h, t.getConstraintMatchCount() });
                }
            }
            return new Result(ScoreCompat.hardOf(total), ScoreCompat.mediumOf(total), families);
        }
    }
}
