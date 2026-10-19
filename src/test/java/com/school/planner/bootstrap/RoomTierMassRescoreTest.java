package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.SchoolSchedule;
import com.school.planner.service.ScheduleService;
import com.school.planner.solver.RoomTierOverrides;
import com.school.planner.solver.SchoolScheduleConstraintProvider;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.score.stream.ConstraintRef;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.impl.score.director.ScoreDirectorFactory;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchTotal;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * TASK-E1 (27/08, room-tier distraction research, anello post-ciclo): re-score the
 * night's staged-cycle checkpoints under two constraint-weight regimes - full weights
 * vs {@link RoomTierOverrides#roomZero()} - to quantify the room-tier VARIABLE mass
 * per pipeline stage. The per-stage table this prints is E1's deliverable; E2 then
 * runs the LS arms on the same checkpoints.
 *
 * <p>Gated: runs only under {@code -De1.run=true} (like the other bench harnesses);
 * the default suite skips it. Input: the checkpoint files of the 26/08 multistart
 * cycle ({@code staged-checkpoints/attempt1..5}) plus the h34on root set - the 27/08
 * tuttoon cycle did not capture checkpoints (flag not in its launch, declared in the
 * registro). The H2 seed is the CURRENT one (G09 cured): the room families are
 * quota-insensitive, and both arms see the same quotas, so the MASS comparison is
 * unaffected; absolute H32/H34/M20 numbers may differ from the original runs.
 */
@QuarkusTest
class RoomTierMassRescoreTest {

    private static final List<String> ROOM_FAMILY_IDS = List.of(
            "H2 - Room conflict",
            "H-M2 - Room suitability",
            "H29 - Assigned lesson must have a room",
            "M3 - Room capacity",
            "M-LF - Non-lab lesson in a tagged room",
            "M37 - Consecutive same-subject same-teacher should keep the room");

    @Inject
    ScheduleService scheduleService;

    @Test
    void rescoresCheckpointsUnderBothWeightRegimes() {
        Assumptions.assumeTrue(Boolean.getBoolean("e1.run"), "E1 bench: -De1.run=true");
        String base = System.getProperty("e1.dir", "staged-checkpoints");
        List<String> checkpoints = List.of(
                base + "/attempt1/POST_B_TIMESLOT-1.json",
                base + "/attempt2/POST_B_TIMESLOT-1.json",
                base + "/attempt3/POST_B_TIMESLOT-1.json",
                base + "/attempt1/POST_RELEASE.json",
                base + "/attempt1/POST_EXACT_FINISHER.json",
                base + "/attempt1/POST_B_ROOM.json",
                base + "/POST_B_TIMESLOT-1.json",
                base + "/POST_RELEASE.json");
        for (String ck : checkpoints) {
            Path p = Path.of(ck);
            Assumptions.assumeTrue(Files.isRegularFile(p), "checkpoint missing: " + ck);
            scheduleService.applyStagedCheckpoint(p);
            // publishes the pre-solve statics the same way the app's analysis path does
            scheduleService.analyzeScore();
            SchoolSchedule problem = scheduleService.getFullScheduleForExport();

            Snapshot full = score(problem, null);
            Snapshot zero = score(problem, RoomTierOverrides.roomZero());

            System.out.println("[E1] === " + ck + " ===");
            System.out.println("[E1] full  hard=" + ScoreCompat.hardOf(full.score) + " medium=" + ScoreCompat.mediumOf(full.score));
            System.out.println("[E1] zero  hard=" + ScoreCompat.hardOf(zero.score) + " medium=" + ScoreCompat.mediumOf(zero.score));
            System.out.println("[E1] room-mass hard=" + (ScoreCompat.hardOf(full.score) - ScoreCompat.hardOf(zero.score))
                    + " medium=" + (ScoreCompat.mediumOf(full.score) - ScoreCompat.mediumOf(zero.score)));
            for (String id : ROOM_FAMILY_IDS) {
                ConstraintMatchTotal<BendableScore> t = full.matches.get(ConstraintRef.of(id));
                if (t != null && (ScoreCompat.hardOf(t.getScore()) != 0 || ScoreCompat.mediumOf(t.getScore()) != 0)) {
                    System.out.println("[E1]   " + id + ": h=" + ScoreCompat.hardOf(t.getScore())
                            + " m=" + ScoreCompat.mediumOf(t.getScore()) + " matches=" + t.getConstraintMatchCount());
                }
            }
        }
    }

    private record Snapshot(BendableScore score, Map<ConstraintRef, ConstraintMatchTotal<BendableScore>> matches) {
    }

    /** The service's own analysis sequence, on OUR problem instance with OUR weights:
     *  update() (shadow variables + score), then the match-enabled re-read. */
    private Snapshot score(SchoolSchedule problem, ConstraintWeightOverrides<BendableScore> overrides) {
        problem.setConstraintWeightOverrides(
                overrides != null ? overrides : ConstraintWeightOverrides.none());
        SolverFactory<SchoolSchedule> factory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(com.school.planner.domain.Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class)));
        SolutionManager<SchoolSchedule, BendableScore> manager = SolutionManager.create(factory);
        manager.update(problem);
        ScoreDirectorFactory<SchoolSchedule, BendableScore> directorFactory =
                ((DefaultSolutionManager<SchoolSchedule, BendableScore>) manager).getScoreDirectorFactory();
        try (var director = directorFactory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            director.setWorkingSolution(problem);
            BendableScore score = director.calculateScore().raw();
            return new Snapshot(score, director.getConstraintMatchTotalMap());
        }
    }

    @Test
    void sanityOverridesFieldExistsAndNoneIsDefault() {
        // cheap always-on guard: the member exists, defaults to none() (no overrides),
        // and the two regimes build maps over exactly the six room families
        SchoolSchedule s = new SchoolSchedule();
        assertNotNull(s.getConstraintWeightOverrides());
        org.junit.jupiter.api.Assertions.assertEquals(0, s.getConstraintWeightOverrides().getKnownConstraintIds().size());
        org.junit.jupiter.api.Assertions.assertEquals(6,
                RoomTierOverrides.roomZero().getKnownConstraintIds().size());
        org.junit.jupiter.api.Assertions.assertEquals(6,
                RoomTierOverrides.roomDamped().getKnownConstraintIds().size());
    }
}
