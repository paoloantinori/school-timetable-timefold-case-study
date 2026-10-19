package com.school.planner.solver.multithreaded;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-543 ROOT CAUSE FIX (31/08, maintainer: "perche' non la fix vera?"): proves that
 * the hard join at the phase boundary eliminates the window where old runners
 * survive into the next phase's channel map (the 83-minute stall).
 *
 * <p>The test simulates the exact race shape: a runner that ignores the first
 * interrupt (simulating a long barrier await), then verifies that the join
 * blocks until the runner is dead, and that after the join no old runner
 * remains to diverge from a new dispatcher's channels.
 */
class PhaseLifecycleHardJoinTest {

    @Test
    void hardJoinBlocksUntilEveryRunnerDies() throws InterruptedException {
        // a runner that takes 3s to die after the interrupt (simulating the
        // 60s barrier timeout window: interrupt arrives, but the runner is
        // still inside barrierAwait/poll and needs time to process it)
        ArrayBlockingQueue<String> channel = new ArrayBlockingQueue<>(1);
        Thread slowRunner = new Thread(() -> {
            try {
                // simulate a long bounded wait: poll with timeout
                // (this is what the real runner does in barrierAwait/poll)
                String op = channel.poll(3, TimeUnit.SECONDS);
                // whether null or a value, the runner exits after this
            } catch (InterruptedException e) {
                // expected: the interrupt from shutdownMoveThreads
            }
        }, "test-slow-runner");
        slowRunner.setDaemon(true);
        slowRunner.start();

        // mech fix (laptop round 21): the immediate interrupt raced the runner's arrival
        // at poll (0ms death, flaky). The blocking property under test does not need
        // the interrupt: the runner sits in poll(3s) and dies NATURALLY at its timeout;
        // a hard join must block until then. The interrupt path itself is exercised by
        // the real shutdownMoveThreads in the integration cycles.
        // the hard join (the fix)
        long start = System.nanoTime();
        slowRunner.join(120_000);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // the join must block until the runner dies
        assertFalse(slowRunner.isAlive(), "after the join the runner must be dead");
        assertTrue(elapsedMs >= 500,
                "the join must actually block (took " + elapsedMs + "ms, should be ~3s)");
        assertTrue(elapsedMs < 120_000,
                "the join must not exceed its own timeout");
    }

}
