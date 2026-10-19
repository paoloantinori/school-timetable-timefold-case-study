package com.school.planner.solver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.SchoolSchedule;
import com.school.planner.solver.MultiStartRunner.AttemptOutcome;
import com.school.planner.solver.MultiStartRunner.AttemptStateListener;
import com.school.planner.solver.MultiStartRunner.AttemptRunner;
import com.school.planner.solver.MultiStartRunner.Config;
import com.school.planner.solver.MultiStartRunner.Result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MultiStartRunner}: the deterministic core of the staged-cycle multi-start
 * retry (TASK-483). Plain JUnit (no {@code @QuarkusTest}) so it runs without booting Quarkus and
 * without a live solve: the attempt-runner is a fake that returns canned {@link SchoolSchedule}
 * outcomes, exactly as the task's test plan asks ("simula l'esito").
 */
class MultiStartRunnerTest {

    private static SchoolSchedule sched(int hard, int medium) {
        SchoolSchedule schedule = new SchoolSchedule();
        schedule.setScore(ScoreCompat.of(hard, medium, 0));
        return schedule;
    }

    /** A fake runner that hands back a pre-set outcome per attempt and counts how often it ran. */
    private static final class ScriptedRunner implements AttemptRunner {
        final List<SchoolSchedule> outcomes;
        final long[] tiers;
        final int discardFromAttempt;
        int calls = 0;

        ScriptedRunner(SchoolSchedule... outcomes) {
            // Equal tiers for every attempt: the full score decides, the pre-21/08 semantics.
            this(new long[outcomes.length], outcomes);
        }

        ScriptedRunner(long[] tiers, SchoolSchedule... outcomes) {
            this(0, tiers, outcomes);
        }

        /** Attempts >= discardFromAttempt come back profileDiscarded (the predictor path). */
        ScriptedRunner(int discardFromAttempt, long[] tiers, SchoolSchedule... outcomes) {
            this.outcomes = Arrays.asList(outcomes);
            this.tiers = tiers;
            this.discardFromAttempt = discardFromAttempt;
        }

        @Override
        public AttemptOutcome run(int attempt) {
            calls++;
            // Attempts are 1-based; clamp to the last scripted outcome if the loop runs further.
            int idx = Math.min(attempt, outcomes.size()) - 1;
            // discardFromAttempt <= 0 means "never discard" (the pre-cap semantics every
            // legacy test relies on): with the naive `attempt >= discardFromAttempt` a
            // default of 0 discarded EVERY attempt, and with Config(3,0) (cap off, the
            // 2-arg constructor) the wrapper looped forever allocating - the OOM zai's
            // round 4 caught (multistart-oom-1.txt).
            boolean discard = discardFromAttempt > 0 && attempt >= discardFromAttempt;
            return new AttemptOutcome(attempt, outcomes.get(idx), tiers[idx], discard);
        }
    }

    /** Records every (attempt, maxAttempts) publication so a test can assert the UI state. */
    private static final class RecordingListener implements AttemptStateListener {
        final List<int[]> published = new ArrayList<>();

        @Override
        public void onAttempt(int attempt, int maxAttempts) {
            published.add(new int[] { attempt, maxAttempts });
        }
    }

    @Test
    void maxAttemptsOneRunsExactlyOneAttemptAndNeedsNoRestore() {
        ScriptedRunner runner = new ScriptedRunner(sched(-6, -1000));
        RecordingListener listener = new RecordingListener();
        // The degenerate cap: maxAttempts=1 (reachable as planner.multistart.max-attempts=1) runs a
        // single attempt and never needs a restore. The "flag OFF -> single cycle" guarantee itself
        // lives in the caller's gate (tested in ScheduleServiceMultiStartTest), not here.
        Result result = MultiStartRunner.run(new Config(1, 0), runner, listener, Instant::now);

        assertEquals(1, runner.calls, "maxAttempts=1 must run exactly one attempt");
        assertEquals(1, result.attemptsRun());
        assertEquals(-6, ScoreCompat.hardOf(result.best().solution().getScore()));
        assertFalse(result.restoreNeeded(), "single attempt is already the current grid");
        assertEquals(1, listener.published.size(), "state published once");
    }

    @Test
    void keepsBestAcrossNonMonotonicAttemptsAndFlagsRestore() {
        // Attempt 1: hard -6. Attempt 2: hard -3 (new best). Attempt 3: hard -5 (worse than 2).
        // Best stays attempt 2, but attempt 3 is the one in the DB -> caller must restore.
        ScriptedRunner runner = new ScriptedRunner(sched(-6, 0), sched(-3, 0), sched(-5, 0));
        RecordingListener listener = new RecordingListener();
        Result result = MultiStartRunner.run(new Config(3, 0), runner, listener, Instant::now);

        assertEquals(3, runner.calls, "all three attempts run (none reached 0-hard)");
        assertEquals(3, result.attemptsRun());
        assertSame(runner.outcomes.get(1), result.best().solution(), "best is attempt 2 (hard -3)");
        assertEquals(-3, ScoreCompat.hardOf(result.best().solution().getScore()));
        assertTrue(result.restoreNeeded(), "best (attempt 2) is not the last (attempt 3) -> restore");
        assertEquals(3, listener.published.size());
    }

    @Test
    void stopsAtZeroHardKeepingThatAttempt() {
        // Attempt 1: hard -6 (residual). Attempt 2: hard 0 (clean). Must stop here, no attempt 3.
        ScriptedRunner runner = new ScriptedRunner(sched(-6, 0), sched(0, -500), sched(0, -1));
        Result result = MultiStartRunner.run(new Config(3, 0), runner, new RecordingListener(), Instant::now);

        assertEquals(2, runner.calls, "0-hard on attempt 2 must stop the loop before attempt 3");
        assertEquals(2, result.attemptsRun());
        assertEquals(0, ScoreCompat.hardOf(result.best().solution().getScore()));
        assertFalse(result.restoreNeeded(), "the 0-hard attempt is the last run -> no restore");
    }

    @Test
    void quasiCarryingAttemptDoesNotTriggerEarlyStop() {
        // Quasi-tier flag ON shape: attempt 1 reaches pure-hard 0 but still carries 12 quasi
        // cells (H34/H32 on hard level 1). The early-stop must NOT fire: a later attempt may
        // deliver both levels clean, and nothing downstream can arbitrate the quasi residual
        // once the loop has stopped. Attempt 2 is clean on both levels and stops the loop.
        // With the flag OFF the quasi level is constantly 0, so the classic 0-hard stop of
        // stopsAtZeroHardKeepingThatAttempt is unaffected by the same code.
        SchoolSchedule quasiDirty = new SchoolSchedule();
        quasiDirty.setScore(ScoreCompat.of(0, -12, -500, 0));
        ScriptedRunner runner = new ScriptedRunner(quasiDirty, sched(0, -480), sched(-6, 0));
        Result result = MultiStartRunner.run(new Config(3, 0), runner, new RecordingListener(), Instant::now);

        assertEquals(2, runner.calls, "pure-0 with a quasi residual must not stop the loop");
        assertEquals(2, result.attemptsRun());
        assertEquals(0, ScoreCompat.quasiOf(result.best().solution().getScore()),
                "the both-levels-clean attempt wins the pick");
        assertFalse(result.restoreNeeded());
    }

    @Test
    void firstAttemptCleanRunsOnlyOnce() {
        // The good-basin case: the deterministic first attempt already closes at 0-hard. Cost = one solve.
        ScriptedRunner runner = new ScriptedRunner(sched(0, -500));
        Result result = MultiStartRunner.run(new Config(3, 0), runner, new RecordingListener(), Instant::now);

        assertEquals(1, runner.calls, "a clean first attempt costs nothing extra");
        assertEquals(1, result.attemptsRun());
        assertFalse(result.restoreNeeded());
    }

    @Test
    void budgetStopsTheLoopBeforeMaxAttempts() {
        // budgetSeconds=100, clock advances 50s per read. Attempt 1 always runs; the budget is
        // checked AFTER each attempt (an advisory cap between attempts, never a mid-solve kill).
        // Reads: deadline-setup (t=0), after attempt 1 (t=50 < 100 -> continue), after attempt 2
        // (t=100 >= 100 -> stop). So a third never starts despite maxAttempts=5 and no 0-hard.
        ScriptedRunner runner = new ScriptedRunner(sched(-6, 0), sched(-6, 0), sched(-6, 0));
        long[] callCount = { 0 };
        Supplier<Instant> clock = () -> Instant.ofEpochSecond(callCount[0]++ * 50L);
        Result result = MultiStartRunner.run(new Config(5, 100), runner, new RecordingListener(), clock);

        assertEquals(2, runner.calls, "budget must stop the loop after attempt 2, before attempt 3");
        assertEquals(2, result.attemptsRun());
        assertFalse(result.restoreNeeded(), "best (attempt 2) == last run (attempt 2) -> no restore");
    }

    @Test
    void allDoomedRunPicksByComparableBoundaryTierNotByRoomNoise() {
        // The h36-verify night shape (21/08, TASK-498 corner-b): attempt 1 doomed at the
        // post-RELEASE boundary (tier -23, the run's best; pre-TASK-635 threshold - a
        // CH-only arm needs tier < -40 today, the ranking itself is threshold-independent)
        // ships CH-only rooms, so its FULL
        // score is -9451 (~9,400 room-tier H29 noise); the LAST attempt (tier -2681) keeps
        // its room refinement and scores -118. The pre-fix full-score ranking picked the
        // last attempt by construction in every all-doomed run; the boundary tier is the
        // only comparable axis, so attempt 1 must win and the caller must restore + refine
        // its rooms (the corner-b path exercised right after the loop).
        ScriptedRunner runner = new ScriptedRunner(
                new long[] { -23, -2681 }, sched(-9451, -1113484), sched(-118, -1490025));
        Result result = MultiStartRunner.run(new Config(2, 0), runner, new RecordingListener(), Instant::now);

        assertSame(runner.outcomes.get(0), result.best().solution(),
                "the better boundary tier wins despite the ~9,400-point room-noise disadvantage");
        assertTrue(result.restoreNeeded(), "best (attempt 1) is not the last -> the wrapper restores and corner-b-refines it");
    }

    @Test
    void equalTiersFallBackToTheFullScoreMediumTieBreak() {
        // Tiers equal -> the full score decides (pre-21/08 semantics preserved for the
        // comparable case): same hard, better medium wins regardless of attempt order.
        ScriptedRunner runner = new ScriptedRunner(sched(-6, 0), sched(-3, -100), sched(-3, -50));
        Result result = MultiStartRunner.run(new Config(3, 0), runner, new RecordingListener(), Instant::now);

        assertSame(runner.outcomes.get(2), result.best().solution(),
                "equal tier + equal hard -> the better medium wins");
    }

    @Test
    void reseedCapEndsTheCycleInsteadOfSpinningForever() {
        // TASK-538 finding (run1 29/08): with budget 0 the profile-predictor reseed loop
        // is unbounded (a discard consumes neither an attempt nor the null deadline; 20
        // observed). The cap makes the cycle END on the best real attempt so far once
        // maxDiscards seeds in a row are rejected: attempt 1 runs, then 3 discards, stop.
        ScriptedRunner runner = new ScriptedRunner(2, new long[] { 0, 0, 0, 0 },
                sched(-6, 0), sched(-6, 0), sched(-6, 0), sched(-6, 0));
        Result result = MultiStartRunner.run(new Config(5, 0, 3), runner, new RecordingListener(), Instant::now);

        assertEquals(4, runner.calls, "one real attempt + 3 discards, then the cap stops the loop");
        assertSame(runner.outcomes.get(0), result.best().solution(),
                "the real attempt 1 is the delivery; discards never become best");
    }

    @Test
    void publishesAttemptAndMaxAttemptsForUi() {
        ScriptedRunner runner = new ScriptedRunner(sched(-6, 0), sched(0, -1));
        RecordingListener listener = new RecordingListener();
        MultiStartRunner.run(new Config(3, 0), runner, listener, Instant::now);

        // Each published pair is (attempt, maxAttempts); maxAttempts is the effective cap (3 here).
        assertEquals(2, listener.published.size());
        assertEquals(1, listener.published.get(0)[0]);
        assertEquals(3, listener.published.get(0)[1]);
        assertEquals(2, listener.published.get(1)[0]);
    }
}
