package com.school.planner.bootstrap;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

import com.school.planner.domain.SchoolSchedule;
import com.school.planner.service.AvailabilityExpansionService;

/**
 * TASK-415 "score-a-snapshot" — answers "what does each constraint weigh on a committed
 * solve/run snapshot" OFFLINE in seconds, without a server or a re-solve. Loads a snapshot
 * (the committed {@code lessons.txt}/{@code boxed.txt} artifacts, via {@link SnapshotFixture}),
 * scores it in-memory with a {@link SolutionManager}, and prints the per-constraint
 * hard/medium/soft breakdown — turning "how much does M32 weigh on val410's final state?"
 * from a 30-minute server+import chore into a one-line {@code PLANNER_TEST_SCORE_SNAPSHOT=1} run.
 *
 * <p><b>Verdict class — TREND / diagnosis only, NEVER adoption.</b> Per the TASK-401
 * checkpoint protocol: a snapshot fixes ONE trajectory (the LS is stochastic), so a score
 * here is valid only for THAT committed snapshot, not evidence the model is right. Adoption
 * always passes through the laptop A/B x2 gate. This harness exists to attribute score to
 * constraints cheaply, and to catch "a new mechanism makes the medium tier explode on the
 * committed state" before burning a laptop run — not to validate anything.
 *
 * <p><b>Quota/expected-hours fidelity — the medium tier is comparable, including M32:</b>
 * unlike the box/conflict/gap harnesses (which leave DailyActivityQuota empty — faithful for
 * H0/H1/H3/H17), this one seeds the production quota + expected-hours facts
 * ({@code dataGenerator.generateDailyActivityQuotas()} / {@code generateExpectedDailyHours()},
 * the same two {@link GoldenSolutionRegressionTest#seedDailyActivityQuotas} calls) so M20/M12
 * score against the real per-date target instead of the calendar fallback. Without this, the
 * printed medium total would silently diverge from a live solve's; the warning printed when a
 * snapshot path is NOT one of the known committed ones is the second guardrail. <b>M32 (template
 * deviation) is now comparable too (TASK-416):</b> {@code WeeklyTemplatePlanner.buildReference}
 * builds the reference template in an assignment-agnostic way (population = every unpinned lesson,
 * not just the timeslot-null ones), so on a FINAL snapshot the template is the real pre-solve one
 * and M32's medium contribution matches a live solve instead of collapsing to ~0.
 *
 * <p><b>Default OFF from the suite</b> ({@code @EnabledIfEnvironmentVariable}): each run boots
 * Quarkus + reconstructs ~12k lessons (~15s), so it is opt-in via the env var {@code
 * PLANNER_TEST_SCORE_SNAPSHOT} to keep the default suite budget intact (localized-testing-guidelines
 * §5). An env var (not {@code -D}) because {@code scripts/core/test.sh} rejects explicit {@code -D}
 * args and does not forward MAVEN_OPTS to the surefire fork. Invoke:
 * {@code PLANNER_TEST_SCORE_SNAPSHOT=1 ./scripts/core/test.sh -c ScoreASnapshotTest} (or set it to a
 * snapshot path instead of {@code 1}).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_SCORE_SNAPSHOT", matches = ".+")
class ScoreASnapshotTest {

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;
    @Inject jakarta.persistence.EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        // clearTables deletes DailyActivityQuota too (FK-before-StudentGroup) since this
        // harness seeds quota facts via generateDailyActivityQuotas/generateExpectedDailyHours.
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    @Transactional
    void printScoreBreakdownForSnapshot() throws Exception {
        Path dir = SnapshotFixture.resolveSnapshotDir(System.getenv("PLANNER_TEST_SCORE_SNAPSHOT"));

        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(dir, availabilityExpansionService, true);
        SchoolSchedule schedule = snap.problem;

        // Seed the production quota + expected-hours facts so the medium tier (M20/M12) is
        // comparable to a live solve, not the calendar fallback. Also publishes the pre-solve weekly
        // template via WeeklyTemplatePlanner.buildReference (assignment-agnostic, TASK-416), so M32
        // is comparable on a final snapshot too — it scores against the real pre-solve template, not
        // an empty one.
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);

        solutionManager.update(schedule);
        BendableScore total = (BendableScore) schedule.getScore();
        long placed = snap.lessons.stream().filter(l -> l.timeslot != null).count();
        long boxed = snap.lessons.size() - placed;

        System.out.println("=== score-a-snapshot: " + dir + " ===");
        System.out.printf("  placed=%d boxed=%d (H0 unassigned=%d)%n", placed, boxed, boxed);
        System.out.printf("  TOTAL score: hard=%d medium=%d soft=%d%n",
                ScoreCompat.hardOf(total), ScoreCompat.mediumOf(total), ScoreCompat.softOf(total));
        System.out.println("  (snapshot fixes ONE trajectory — TREND/diagnosis only, never adoption)");
        printBreakdown(schedule);

        // No assertion on score values — the snapshot is a committed static artifact, so the
        // only structural guarantee is that it scores (no exception) and reports every
        // contributing constraint. A regression of the TOTAL hard/medium against the committed
        // number is a human review signal, not a pass/fail gate here.
        org.junit.jupiter.api.Assertions.assertTrue(ScoreCompat.hardOf(total) <= 0,
                "a committed solve's hard score is non-positive (0 or negative)");
    }

    /** Per-constraint hard/medium/soft breakdown via the raw ScoreDirector
     *  ({@link DefaultSolutionManager#getScoreDirectorFactory()} + ConstraintMatchPolicy.ENABLED),
     *  the same pattern {@link HumanFaithfulConstructionSpikeTest#printHardBreakdown} uses,
     *  generalized to all three tiers and sorted by absolute contribution. */
    private void printBreakdown(SchoolSchedule schedule) {
        try {
            var factory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager)
                    .getScoreDirectorFactory();
            try (var sd = factory.createScoreDirectorBuilder()
                    .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                    .withLookUpEnabled(false)
                    .withExpectShadowVariablesInCorrectState(false)
                    .build()) {
                sd.setWorkingSolution(schedule);
                sd.calculateScore();
                List<Row> rows = sd.getConstraintMatchTotalMap().values().stream()
                        .map(total -> {
                            BendableScore s = (BendableScore) total.getScore();
                            return new Row(total.getConstraintRef().id(), ScoreCompat.hardOf(s),
                                    ScoreCompat.mediumOf(s), ScoreCompat.softOf(s), total.getConstraintMatchCount());
                        })
                        .filter(r -> r.hard != 0 || r.medium != 0 || r.soft != 0)
                        .sorted(Comparator
                                .comparingLong((Row r) -> Math.abs((long) r.hard) + Math.abs((long) r.medium)
                                        + Math.abs((long) r.soft))
                                .reversed())
                        .collect(Collectors.toList());
                System.out.printf("  %-46s %10s %12s %12s %10s%n", "constraint", "hard", "medium", "soft", "matches");
                for (Row r : rows) {
                    System.out.printf("  %-46s %10d %12d %12d %10d%n",
                            r.id, r.hard, r.medium, r.soft, r.matches);
                }
            }
        } catch (Exception e) {
            System.out.println("  (breakdown unavailable: " + e + ")");
        }
    }

    private record Row(String id, long hard, long medium, long soft, long matches) {
    }
}
