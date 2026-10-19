package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.CompositeSwapFinisher;
import com.school.planner.solver.EmptyDayFinisher;
import com.school.planner.solver.ScoreCompat;
import com.school.planner.solver.HoleRelayFinisher;
import com.school.planner.solver.ViolationScopedSteepestFinisher;
import com.school.planner.solver.checkpoint.StagedCheckpoint;
import com.school.planner.solver.checkpoint.StagedCheckpointSerializer;
import com.school.planner.service.ScheduleService;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * TASK-635 falsification bench: invoke the DELIVERY CHAIN (CSF, then EDF delivery
 * extensions, then HRF, then violation-steepest; same order and knobs as the
 * pre-save wrapper but SHELL-LESS: the per-move improve-or-noop inside each pass
 * replaces the wrapper's whole-pass belt, the precedent of the ratified
 * {@code DeliveryTrioBenchTest} evidence) on a DOOMED-range state captured by a
 * real cycle.
 *
 * <p>ESITO AXIS (the task defines it on the TIMESLOT TIER, where the TASK-635
 * accept/doom threshold lives; its recalibration -2 to -40 rests on this bench): every
 * stage reports {@code tsh}, the sum of hard families EXCLUDING the
 * room tier ({@code H2/H-M2/H29}, the {@code ROOM_TIER_HARD_FAMILIES} set of
 * {@code ScheduleService}). The POST_RELEASE checkpoint legitimately carries ~12k
 * room-null H29 (rooms are released at that boundary; B-ROOM comes after), so the
 * full-hard total can barely move under timeslot passes and is reported only as
 * context.
 *
 * <p>Input: {@code staged-checkpoints/attempt1/POST_RELEASE.json} of the 03/10
 * night cycle B2 ({@code timeslotTierHard = -29}), captured against the CURRENT
 * tree's seed. Cross-profile checkpoints of OLDER trees manufacture id-incoherent
 * placements (the overnight H3-mass lesson), which the fidelity gate below must
 * catch: the recomputed hard AND tsh of the restored state must EQUAL the values
 * recorded in the checkpoint, at the number.
 *
 * <p>Statics note: {@code applyStagedCheckpoint} publishes the pre-solve statics
 * from the PRE-restore grid; {@code analyzeScore()} right after the restore
 * re-publishes them from the restored grid (the {@code RoomTierMassRescoreTest}
 * precedent), so pin-derived exemptions (H31/H32) are armed as at capture time.
 *
 * <p>Env and launch: {@code PLANNER_TEST_DOOM_FALSIFY=1} plus the seeding flag
 * the test profile disables by default:
 * {@code PLANNER_TEST_DOOM_FALSIFY=1 ./scripts/core/test.sh -mva
 * "-Dsurefire.heap=3g -Dplanner.datagenerator.enabled=true" (heap 3g e la regola fedora-box; il laptop scarico lo tollera a 8g) -c DoomTrioFalsifyBenchTest}
 * (optional {@code PLANNER_TEST_DOOM_INPUT=<path>} for another checkpoint).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_DOOM_FALSIFY", matches = ".+")
class DoomTrioFalsifyBenchTest {

    /** Mirror of ScheduleService.ROOM_TIER_HARD_FAMILIES with its FAMILY match
     * (equals or dotted sub-family, {@code isRoomTierHardConstraint}): an exact
     * Set.contains would misclassify a future "H29.x" as timeslot-tier. */
    private static final Set<String> ROOM_TIER_FAMILIES = Set.of("H2", "H-M2", "H29");

    private static final java.util.function.BiConsumer<Lesson, Timeslot> MOVE_TIMESLOT =
            (lesson, ts) -> lesson.timeslot = ts;

    @Inject ScheduleService scheduleService;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    @Test
    @Timeout(3600)
    void deliveryChainOnDoomedState() throws Exception {
        // Frozen evidence (the -40 threshold bench): pin the pre-flip H33 form so
        // the 04/10 default cannot drift the recorded outcomes (review finding).
        System.setProperty(EmptyDayFinisher.H33_FLAG, "false");
        Path checkpointPath = Path.of(System.getenv().getOrDefault("PLANNER_TEST_DOOM_INPUT",
                "staged-checkpoints/attempt1/POST_RELEASE.json"));
        assertTrue(Files.isRegularFile(checkpointPath), "checkpoint missing: " + checkpointPath);
        assertTrue(Timeslot.count() > 0, "empty DB: the test profile disables DataGenerator;"
                + " launch with -Dplanner.datagenerator.enabled=true (see the class javadoc)");
        StagedCheckpoint checkpoint =
                StagedCheckpointSerializer.fromJson(Files.readString(checkpointPath));
        long recordedHard = hardOfRecorded(checkpoint.score());
        long recordedTsh = checkpoint.timeslotTierHard();
        System.out.printf("[DOOM] checkpoint recorded hard=%d tsh=%d (threshold axis)%n",
                recordedHard, recordedTsh);
        System.out.printf("[DOOM] flags: csf=%b edfExt=%b hrf=%b steepest=%b roomAware=%b%n",
                CompositeSwapFinisher.enabled(),
                EmptyDayFinisher.extensionsArmed(),
                HoleRelayFinisher.enabled(),
                ViolationScopedSteepestFinisher.enabled(),
                ViolationScopedSteepestFinisher.roomAwareEnabled());

        scheduleService.applyStagedCheckpoint(checkpointPath);
        scheduleService.analyzeScore();

        SchoolSchedule problem = scheduleService.getFullScheduleForExport();
        solutionManager.update(problem);
        Map<String, long[]> restored = PureOneOffCheckpointTest.printHardDiag(
                solutionManager, problem, "RESTORED (fidelity gate)");
        long restoredHard = totalHard(restored);
        long restoredTsh = tsh(restored);
        System.out.printf("[DOOM] restored hard=%d tsh=%d (recorded %d/%d)%n",
                restoredHard, restoredTsh, recordedHard, recordedTsh);

        // Fidelity gate, exact equality: a same-seed deterministic restore must
        // reproduce the capture at the number; anything else is the stale-seed class.
        assertEquals(recordedHard, restoredHard, "restored hard diverges from checkpoint");
        assertEquals(recordedTsh, restoredTsh, "restored timeslot-tier diverges from checkpoint");

        long tshBefore = restoredTsh;

        CompositeSwapFinisher.Result csf = CompositeSwapFinisher.finish(problem, MOVE_TIMESLOT);
        long tshCsf = stage("CSF",
                "targets=" + csf.targets() + " relay2=" + csf.relay2() + " relay3=" + csf.relay3(),
                problem, tshBefore);

        EmptyDayFinisher.Result edf = EmptyDayFinisher.finish(problem, MOVE_TIMESLOT,
                EmptyDayFinisher.Boundary.DELIVERY);
        long tshEdf = stage("EDF[delivery]",
                "moves=" + edf.movesApplied() + " cells=" + edf.cellsClosed(),
                problem, tshCsf);

        HoleRelayFinisher.Result hrf = HoleRelayFinisher.finish(problem, MOVE_TIMESLOT);
        long tshHrf = stage("HRF",
                "targets=" + hrf.targets() + " slides=" + hrf.slides()
                        + " rotations=" + hrf.rotations(),
                problem, tshEdf);

        DefaultSolutionManager<SchoolSchedule, BendableScore> defaultManager =
                (DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager;
        var steepest = ViolationScopedSteepestFinisher.finish(problem,
                defaultManager.getScoreDirectorFactory(),
                Integer.getInteger("planner.construction.violation-steepest.max-iterations", 10),
                Integer.getInteger("planner.construction.violation-steepest.max-candidates", 100_000));
        long tshSteepest = stage("STEEPEST",
                "applied=" + steepest.movesApplied() + " (hard " + steepest.hardBefore()
                        + " -> " + steepest.hardAfter() + ")",
                problem, tshHrf);

        System.out.printf("[DOOM] VERDICT (timeslot-tier axis): tsh %d -> %d (%+d)%n",
                tshBefore, tshSteepest, tshSteepest - tshBefore);
        PureOneOffCheckpointTest.printHardDiag(solutionManager, problem, "AFTER CHAIN");
    }

    @org.junit.jupiter.api.AfterEach
    void clearH33() {
        System.clearProperty(EmptyDayFinisher.H33_FLAG);
    }

    /** One chain stage: re-score, report the delta on the threshold axis with the
     * full-hard as context, and warn loudly when a pass regresses (production's
     * whole-pass belt would revert it, so later stages would run on a state
     * production never sees). */
    private long stage(String name, String stats, SchoolSchedule problem, long tshBefore) {
        // Shadow variables + problem.score refresh, the sibling-bench pattern: the
        // finishers' internal directors keep entities consistent, but update() is the
        // cheap guarantee that the ENABLED pass below scores the state production sees.
        solutionManager.update(problem);
        Map<String, long[]> families = hardFamilies(problem);
        long tsh = tsh(families);
        System.out.printf("[DOOM] %s: %s -> tsh %d (%+d), full-hard %d%n",
                name, stats, tsh, tsh - tshBefore, totalHard(families));
        if (tsh < tshBefore) {
            System.out.printf("[DOOM] WARN %s regressed tsh (%+d): the wrapper belt would"
                    + " revert this pass; later stages run on a state production never sees%n",
                    name, tsh - tshBefore);
        }
        return tsh;
    }

    /** Constraint-match families via the ENABLED director (silent: null label). */
    private Map<String, long[]> hardFamilies(SchoolSchedule problem) {
        return PureOneOffCheckpointTest.printHardDiag(solutionManager, problem, null);
    }

    private static long totalHard(Map<String, long[]> families) {
        return families.values().stream().mapToLong(v -> v[1]).sum();
    }

    /** Timeslot-tier hard: every hard family except the room tier (H2/H-M2/H29). */
    private static long tsh(Map<String, long[]> families) {
        return families.entrySet().stream()
                .filter(e -> !isRoomTier(e.getKey()))
                .mapToLong(e -> e.getValue()[1])
                .sum();
    }

    private static boolean isRoomTier(String familyId) {
        for (String family : ROOM_TIER_FAMILIES) {
            if (familyId.equals(family) || familyId.startsWith(family + ".")) {
                return true;
            }
        }
        return false;
    }

    /** The recorded hard via the house parse (BendableScore.parseScore), never a
     * regex over the toString rendering. */
    private static long hardOfRecorded(String score) {
        return ScoreCompat.hardOf(ai.timefold.solver.core.api.score.BendableScore.parseScore(score));
    }
}
