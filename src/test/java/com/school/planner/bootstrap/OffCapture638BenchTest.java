package com.school.planner.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.school.planner.bootstrap.DataGenerator;
import com.school.planner.domain.SchoolSchedule;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * TASK-638 input benches (maintainer 21/09: "i due approcci puoi gia' iniziare ora"):
 * the A/B 636 OFF-arm delivery [-2/0] (two H7 cells: T21 2027-01-27,
 * T23 2026-12-02, both 7h on cap 6) replayed through the two candidate
 * cures WITHOUT a cycle and WITHOUT the laptop (which must stay undisturbed while
 * arm B runs: unimprovedSecondsSpentLimit is wall-based, CPU contention would
 * bias the A/B).
 *
 * <p>Fixture: the state dir's {@code export.json.gz} when present (room/id
 * FAITHFUL, the real delivered state), the pipe {@code lessons.txt} otherwise
 * (integrato-1; its reconstruction carries H4/H25/H32 noise); selected by
 * {@code offrep.fixture} (auto|txt|export, default auto), plus
 * {@code RealSeedFixture#seedRooms}, the golden co-presence depth and a greedy
 * CH-only room pass (the bench3 pattern; rooms do not change the timeslot
 * tier, medium is declared base noise). Modes, all self-skipping:
 * <ul>
 * <li>{@code offrep.mode=trio}: the delivery trio with h7swap ARMED
 *     (TASK-632's 1-per-1 grammar, default OFF in production runs) - the
 *     deterministic-cure prediction: closes both cells.</li>
 * <li>{@code offrep.mode=ls}: a short full-LS pass (change+swap, REPRODUCIBLE
 *     seed 0, unimproved cap) on the delivered state - maintainer's statistical
 *     ceiling prediction: 3.373 legal swaps exist, the LS meets one in
 *     seconds.</li>
 * </ul>
 * Both print before/after score, the residual cells, and wall. Gated by
 * {@code -Doffrep.run=true}; {@code offrep.dir} overrides the capture dir.
 */
@QuarkusTest
class OffCapture638BenchTest {

    @Inject
    DataGenerator dataGenerator;

    @Inject
    com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;

    private static final String OFF_DIR = "claudedocs/spalmone-2026-27/task636-results/ab-adoption/off";

    /** The bench members live on the trio bench instance (package-visible); none
     *  of the four uses CDI injection, so a plain construction is faithful. */
    private final DeliveryTrioBenchTest bench = new DeliveryTrioBenchTest();

    @Test
    void replayOffCaptureThroughCandidateCures() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Boolean.getBoolean("offrep.run"), "off-capture bench: -Doffrep.run=true");
        Path dir = Path.of(System.getProperty("offrep.dir", OFF_DIR));
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.isRegularFile(dir.resolve("lessons.txt"))
                        || Files.isRegularFile(dir.resolve("export.json.gz")),
                "capture missing: " + dir);
        String mode = System.getProperty("offrep.mode", "trio");
        DeliveryTrioBenchTest.Result before;
        DeliveryTrioBenchTest.Result after;

        // Fixture: export.json.gz when present (room/id-FAITHFUL state, see
        // SnapshotFixture.loadFromExport; the pipe dump's reconstruction carries
        // H4 noise and no rooms), pipe lessons.txt otherwise (integrato-1 is
        // pipe-only in the catalog). assignRoomsChOnly is additive (fills
        // room-null only), so faithful rooms survive it.
        Path export = dir.resolve("export.json.gz");
        String fixtureMode = System.getProperty("offrep.fixture", "auto");
        boolean useExport = switch (fixtureMode) {
            case "txt" -> false;
            case "export" -> {
                if (!Files.isRegularFile(export)) {
                    throw new IllegalArgumentException(
                            "offrep.fixture=export but no export.json.gz in " + dir);
                }
                yield true;
            }
            case "auto" -> Files.isRegularFile(export);
            default -> throw new IllegalArgumentException(
                    "offrep.fixture=" + fixtureMode + " (use auto|txt|export)");
        };
        SnapshotFixture.Snapshot snap = QuarkusTransaction.requiringNew().call(() -> {
            SnapshotFixture.Snapshot s = useExport
                    ? SnapshotFixture.loadFromExport(export, availabilityExpansionService, true)
                    : SnapshotFixture.loadSnapshotNoClean(dir,
                            availabilityExpansionService, true);
            SnapshotFixture.seedMediumTierFacts(dataGenerator, s);
            // production-faithful co-presence depth (the DeliveryTrioBenchTest
            // loadExport sequence; omitting it leaves H-COPRESENCE/H2 exemptions
            // scoring against an empty depth map)
            SchoolSchedule.setGoldenCoPresenceDepthStatic(
                    CompositeRepairReplay609Test.depthFromSnapshotCourses(s));
            // TASK-656 banco fidelity: the HRF import gate's pool-aware yield line reads
            // the TASK-497 static map, published per solve setup in production by
            // refreshSolverStatics. Without this publish the banco runs the gate un-armed
            // (getM20PoolCapTarget -> -1) and the pool-cap coherence fix is invisible.
            SchoolSchedule.setM20PoolCapTargetsStatic(
                    com.school.planner.solver.SchoolScheduleConstraintProvider.computeM20PoolCapTargetsByGroup(
                            com.school.planner.domain.StudentGroup.listAll(),
                            s.problem.getLessons(),
                            com.school.planner.domain.DailyActivityQuota.listAll()));
            SchoolSchedule.captureM20CapPoolFlag();
            // TASK-657 synthetic validation: the fixture seeds availability from
            // the REAL seed (RealSeedFixture), not from the export rows, so
            // mutating the export JSON is invisible by construction. The
            // synthetic input is built DB-side: drop T22's Friday
            // VACATIONs so the cieco-v3 chain becomes legal. Before any VRP
            // access (the room pass and the finisher come after).
            if (Boolean.getBoolean("offrep.synth.dropT22Fri")) {
                var all = com.school.planner.domain.TeacherAvailability.<com.school.planner.domain.TeacherAvailability>listAll();
                int dropped = 0;
                var kept = new java.util.ArrayList<com.school.planner.domain.TeacherAvailability>();
                for (com.school.planner.domain.TeacherAvailability ta : all) {
                    if (ta.teacher != null && "T22".equals(ta.teacher.name)
                            && ta.type == com.school.planner.domain.AvailabilityType.VACATION
                            && ta.startDateTime.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) {
                        ta.delete();
                        dropped++;
                    } else {
                        kept.add(ta);
                    }
                }
                // THREE availability views to keep coherent post-deletion: the
                // VRP static (isSlotLegalFresh), the score's FACT LIST (H5
                // joins the TeacherAvailability entities in the working
                // solution, collected at fixture build BEFORE this drop),
                // and the DB rows themselves
                SchoolSchedule.setTeacherAvailabilitiesStatic(kept);
                var problemAvail = s.problem.getTeacherAvailabilities();
                if (problemAvail != null) {
                    problemAvail.removeIf(ta -> ta.teacher != null
                            && "T22".equals(ta.teacher.name)
                            && ta.type == com.school.planner.domain.AvailabilityType.VACATION
                            && ta.startDateTime.getDayOfWeek() == java.time.DayOfWeek.FRIDAY);
                }
                System.out.printf("[OFFREP] synth: dropped %d T22 Friday VACATIONs (DB + static + facts)%n", dropped);
            }
            // inside the tx: the CH room pass persists rooms (the bench3 pattern)
            bench.assignRoomsChOnly(s);
            return s;
        });
        System.out.printf("[OFFREP] fixture: %s (mode %s)%n", useExport ? export : dir, fixtureMode);
        long roomNull = snap.problem.getLessons().stream()
                .filter(l -> l.timeslot != null && l.room == null).count();
        System.out.printf("[OFFREP] fixture ready: %d lessons, room-null %d (CH-only pass)%n",
                snap.problem.getLessons().size(), roomNull);

        before = bench.score(snap.problem);
        System.out.printf("[OFFREP] before: hard=%d medium=%d%n", before.hard(), before.medium());
        bench.print(before, "before");

        long t0 = System.currentTimeMillis();
        SchoolSchedule measured = snap.problem;
        if ("forced657".equals(mode)) {
            // TASK-657 probe: apply the cieco-v3 3-move chain RAW (raw writes
            // are invisible to bavet but bench.score is a full recompute) and
            // report the residual by family. Throwaway diagnostic.
            java.util.function.BiFunction<String, String, com.school.planner.domain.Lesson> find =
                    (subj, key) -> snap.problem.getLessons().stream()
                            .filter(l -> l.timeslot != null && l.course != null
                                    && l.course.subject != null && l.course.subject.startsWith(subj)
                                    && key.equals(l.timeslot.getDate() + "@"
                                            + l.timeslot.startDateTime.toLocalTime()))
                            .findFirst().orElseThrow(() -> new IllegalStateException(
                                    "forced657: not found " + subj + " " + key));
            var gestione = find.apply("Gestione azien", "2026-11-20@11:00");
            var labTe = find.apply("Laboratorio Termoidraulico", "2026-11-19@13:00");
            var informatica = find.apply("Informatica", "2026-11-20@13:00");
            var holeTs = snap.problem.getTimeslots().stream()
                    .filter(t -> "2026-11-20".equals(t.getDate().toString())
                            && "11:00".equals(t.startDateTime.toLocalTime().toString()))
                    .findFirst().orElseThrow();
            var gestioneOld = gestione.timeslot;
            gestione.timeslot = labTe.timeslot;   // ven@11 -> gio@13
            labTe.timeslot = gestioneOld;         // gio@13 -> ven@11
            informatica.timeslot = holeTs;        // G04 tail @13 -> hole @11
            System.out.printf("[forced657] rooms unchanged: gestione=%s labTe=%s informatica=%s%n",
                    gestione.room == null ? "-" : gestione.room.name,
                    labTe.room == null ? "-" : labTe.room.name,
                    informatica.room == null ? "-" : informatica.room.name);
            after = bench.score(snap.problem);
            bench.print(after, "after ");
            SnapshotFixture.printResidualCells("forced657", snap.problem);
            System.out.printf("[OFFREP] delta: hard %d -> %d%n", before.hard(), after.hard());
            return;
        }
        if ("steepest".equals(mode)) {
            // TASK-638 bench form: the ViolationScopedSteepestFinisher directly.
            // Knobs: the 3.373 legal swaps around the two H7 cells only enter the
            // round-robin budget when it is large enough (at 100 the H4 fixture
            // noise's 35 targets consume the budget before the H7 partners show).
            var stats = com.school.planner.solver.ViolationScopedSteepestFinisher
                    .finish(snap.problem,
                            Integer.getInteger("offrep.iterations", 10),
                            Integer.getInteger("offrep.candidates", 100));
            System.out.printf("[OFFREP] steepest stats: applied=%d evaluated=%d hard %d->%d in %dms%n",
                    stats.movesApplied(), stats.movesEvaluated(),
                    stats.hardBefore(), stats.hardAfter(), stats.wallMillis());
            after = bench.score(snap.problem);
            bench.print(after, "after ");
            SnapshotFixture.printResidualCells("offrep steepest", snap.problem);
            System.out.printf("[OFFREP] delta: hard %d -> %d%n", before.hard(), after.hard());
            // NOTE: the finisher is timeslot-tier; a lesson it places lands
            // roomless (H29) and the real chain's room phases own that. A second
            // CH pass here was tried and REMOVED (code-review): seedRooms()
            // persists fresh Room ids, so the fill would juggle three id-spaces
            // and could double-book a physical room invisibly.
            return;
        }
        if ("trio-refined".equals(mode)) {
            // TASK-638a iter2: the room-REFINED base (bench4 form) before the trio:
            // h7swap's ROOM-CAND gates die on greedy CH rooms (iter1 no-op)
            measured = bench.refineRoomsOnly(snap.problem);
        } else if ("ls".equals(mode)) {
            // solve() RETURNS the best: the working instance holds the last
            // state, not the best (the first take measured the wrong one)
            measured = runShortLs(snap.problem);
        } else {
            runTrioWithH7Swap(measured);
        }
        long wall = (System.currentTimeMillis() - t0) / 1000;

        after = bench.score(measured);
        System.out.printf("[OFFREP] after (%s, %ds): hard=%d medium=%d%n",
                mode, wall, after.hard(), after.medium());
        bench.print(after, "after ");
        SnapshotFixture.printResidualCells("offrep " + mode, measured);
        System.out.printf("[OFFREP] delta: hard %d -> %d%n", before.hard(), after.hard());
    }

    /** The wrapper's delivery trio with h7swap armed (the TASK-632 launcher form). */
    private void runTrioWithH7Swap(SchoolSchedule problem) {
        System.setProperty("planner.construction.hole-relay.h7swap.enabled", "true");
        try {
            bench.runTrio(problem);
        } finally {
            System.clearProperty("planner.construction.hole-relay.h7swap.enabled");
        }
    }

    /** maintainer's bench: a short vanilla LS on the delivered state. Full constraint
     *  set, change+swap moves, REPRODUCIBLE + seed 0, unimproved 60s cap and a
     *  300s wall cap: measures the statistical ceiling (3.373 winners exist). */
    private SchoolSchedule runShortLs(SchoolSchedule problem) {
        ai.timefold.solver.core.config.solver.SolverConfig cfg =
                new ai.timefold.solver.core.config.solver.SolverConfig()
                        .withSolutionClass(SchoolSchedule.class)
                        .withEntityClasses(com.school.planner.domain.Lesson.class)
                        .withScoreDirectorFactory(new ai.timefold.solver.core.config.score.director
                                .ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(
                                        com.school.planner.solver.SchoolScheduleConstraintProvider.class))
                        // NO_ASSERT keeps the default REPRODUCIBLE random (fixed
                        // seed 0): deterministic run, no assert overhead.
                        .withEnvironmentMode(ai.timefold.solver.core.config.solver.EnvironmentMode.NO_ASSERT)
                        .withPhases(new ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig()
                                .withMoveSelectorConfig(
                                        new ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig(
                                                java.util.List.of(
                                                        new ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig(),
                                                        new ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig())))
                                .withTerminationConfig(new ai.timefold.solver.core.config.solver.termination.TerminationConfig()
                                        .withUnimprovedSecondsSpentLimit(60L)
                                        .withSecondsSpentLimit(300L)));
        ai.timefold.solver.core.api.solver.Solver<SchoolSchedule> solver =
                ai.timefold.solver.core.api.solver.SolverFactory.<SchoolSchedule>create(cfg).buildSolver();
        SchoolSchedule out = solver.solve(problem);
        System.out.printf("[OFFREP] short LS finished, best=%s (the RETURN is the best;"
                + " the working instance holds the last state)%n", out.getScore());
        return out;
    }
}
