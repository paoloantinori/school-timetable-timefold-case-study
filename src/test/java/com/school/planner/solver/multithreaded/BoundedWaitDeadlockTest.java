package com.school.planner.solver.multithreaded;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CyclicBarrier;

import org.junit.jupiter.api.Test;

/**
 * TASK-541 (22/08, thread dump of the 22/08 notte bench): the MT-LS never parks FOREVER.
 * The observed deadlock had the decider in MoveResultQueue.take() and all move threads on
 * the CyclicBarrier - neither wait had a timeout, so one lost barrier party (a runner that
 * exited between rounds) parked the whole phase for 2+ hours at 0% CPU. These tests pin the
 * bounded-wait contract every wait site must keep.
 */
class BoundedWaitDeadlockTest {

    @Test
    void barrierAwaitTimesOutInsteadOfParkingForever() {
        // a barrier of 2 parties with only THIS runner arriving: the other party never comes
        MoveThreadRunner<Object> runner = new MoveThreadRunner<>(
                new java.util.concurrent.LinkedBlockingQueue<>(),
                new MoveResultQueue<>(4), new CyclicBarrier(2), null, 0);
        long start = System.nanoTime();
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> runner.barrierAwait(200),
                "a missing barrier party must surface as an exception within the timeout,"
                        + " not park the thread forever");
        assertTrue((System.nanoTime() - start) < 5_000_000_000L, "must not hang");
        assertTrue(e.getMessage().contains("barrier"), e.getMessage());
    }

    @Test
    void barrierAwaitTimeoutDuringShutdownIsTheExpectedExit() {
        MoveThreadRunner<Object> runner = new MoveThreadRunner<>(
                new java.util.concurrent.LinkedBlockingQueue<>(),
                new MoveResultQueue<>(4), new CyclicBarrier(2), null, 0);
        runner.markShuttingDown();
        assertThrows(MoveThreadRunner.ShutdownExitException.class,
                () -> runner.barrierAwait(200),
                "during shutdown the same timeout is the normal exit, not an error");
    }

    @Test
    void resultQueuePollTimesOutToNullInsteadOfParkingForever() throws InterruptedException {
        MoveResultQueue<Object> queue = new MoveResultQueue<>(4);
        long start = System.nanoTime();
        assertNull(queue.poll(100),
                "an empty queue must return null at the timeout, not park the decider");
        assertTrue((System.nanoTime() - start) < 2_000_000_000L);
        assertNotNull(queue, "sanity: queue object alive");
    }

    @Test
    void resultQueuePollDeliversAQueuedResultImmediately() throws Exception {
        MoveResultQueue<Object> queue = new MoveResultQueue<>(4);
        queue.addMove(3, null, null);
        MoveResultQueue.MoveResult<Object> r = queue.poll(100);
        assertNotNull(r);
        org.junit.jupiter.api.Assertions.assertEquals(3, r.moveIndex());
    }
}
