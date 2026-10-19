package com.school.planner.solver.multithreaded;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * TASK-542 (24/08 06:20 medium-run NPE): the medium-post-repair phase crashed because a
 * parked {@code poll()} woke on the poison pill enqueued by {@code addExceptionThrown}
 * and returned it as a NORMAL result - {@code move()} null, NPE at the rebase. The
 * pre-poll exception check has a TOCTOU window exactly one element wide: this test pins
 * both orderings.
 */
class MoveResultQueuePoisonPillTest {

    @Test
    void exceptionSetBeforePollPropagatesLoudly() {
        MoveResultQueue<Object> q = new MoveResultQueue<>(4);
        q.addExceptionThrown(new IllegalStateException("stepIndex mismatch"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> q.poll(50));
        assertTrue(ex.getMessage().contains("propagated"),
                "the wrapped move-thread exception must be the loud failure, not an NPE");
        assertNotNull(ex.getCause());
    }

    /**
     * THE race: the poller is already PARKED inside delegate.poll when the exception
     * lands and its pill is offered. The woken poll must re-check on the pill's null-move
     * signature and throw the wrapped exception - never hand the pill out as a result.
     */
    @Test
    void pillDeliveredToAParkedPollerThrowsNotReturns() throws Exception {
        MoveResultQueue<Object> q = new MoveResultQueue<>(4);
        CountDownLatch pollerParked = new CountDownLatch(1);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicReference<MoveResultQueue.MoveResult<Object>> returned = new AtomicReference<>();
        Thread poller = new Thread(() -> {
            try {
                pollerParked.countDown();
                returned.set(q.poll(10_000));
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        poller.start();
        // give the poller time to be INSIDE delegate.poll before the pill arrives
        assertTrue(pollerParked.await(1, TimeUnit.SECONDS));
        Thread.sleep(150);
        q.addExceptionThrown(new IllegalStateException("late stepIndex mismatch"));
        poller.join(5_000);
        assertTrue(poller.isAlive() == false, "poller must terminate");
        assertNotNull(thrown.get(), "the parked poller must THROW, not return the pill"
                + (returned.get() != null ? " (it returned a null-move result: the NPE bug)" : ""));
        assertTrue(thrown.get() instanceof RuntimeException
                && ((RuntimeException) thrown.get()).getMessage().contains("propagated"),
                "the wrapped move-thread exception must be the failure: " + thrown.get());
    }
}
