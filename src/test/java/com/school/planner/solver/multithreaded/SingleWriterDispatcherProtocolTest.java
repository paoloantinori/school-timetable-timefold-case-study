package com.school.planner.solver.multithreaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * TASK-543 FASE 2 TDD: the single-writer dispatch protocol, exercised WITHOUT a solver -
 * N consumer threads replicate {@code MoveThreadRunner}'s exact stepIndex discipline
 * (setup -> 0; an ApplyStepOperation k must be exactly stepIndex + 1; an evaluation op
 * must match the current stepIndex) against the dispatcher, while the single producer
 * runs the decider's emission pattern (random evals per step, then the apply broadcast).
 *
 * <p>The pin: ZERO mismatches over tens of thousands of steps with jittered consumers.
 * On the LEGACY shared queue the same discipline is racy by construction (an apply copy
 * can be stolen by a faster consumer) - reproducing that theft deterministically needs
 * the runner's real barriers, so the legacy model is NOT executed here; this test pins
 * the new protocol, and the desync incident (iter-31-bis, "thread=14 operation=16") is
 * the legacy evidence.
 */
class SingleWriterDispatcherProtocolTest {

    private static final int THREADS = 8;
    private static final int STEPS = 20_000;
    private static final int EVALS_PER_STEP = 12;

    @Test
    void perThreadChannelsNeverLoseAnApplyStep() throws Exception {
        SingleWriterOperationDispatcher<Object> d = new SingleWriterOperationDispatcher<>(THREADS, 16, () -> true);
        AtomicInteger mismatches = new AtomicInteger();
        AtomicInteger consumedApplies = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(THREADS);
        List<Thread> consumers = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            final int idx = t;
            Thread consumer = new Thread(() -> {
                int stepIndex = -1;
                try {
                    while (true) {
                        MoveThreadOperation<Object> op = d.channel(idx).take();
                        if (op instanceof SetupOperation<Object>) {
                            if (stepIndex != -1) {
                                mismatches.incrementAndGet(); // double setup: impossible on a private channel
                            }
                            stepIndex = 0;
                        } else if (op instanceof DestroyOperation) {
                            break;
                        } else if (op instanceof ApplyStepOperation<Object> apply) {
                            if (stepIndex + 1 != apply.stepIndex()) {
                                mismatches.incrementAndGet();
                                break;
                            }
                            stepIndex = apply.stepIndex();
                            consumedApplies.incrementAndGet();
                        } else if (op instanceof MoveEvaluationOperation<Object> eval) {
                            if (stepIndex != eval.stepIndex()) {
                                mismatches.incrementAndGet();
                                break;
                            }
                        }
                        // jitter: exercise the producer/consumer overlap, not lockstep
                        if ((idx + applyCounterJitter()) % 97 == 0) {
                            Thread.sleep(0, 200);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            consumer.start();
            consumers.add(consumer);
        }
        // the single writer: the decider's emission pattern
        d.setupAll(null);
        java.util.Random rnd = new java.util.Random(42);
        for (int step = 0; step < STEPS; step++) {
            int evals = rnd.nextInt(EVALS_PER_STEP);
            for (int e = 0; e < evals; e++) {
                d.dispatchEvaluation(new MoveEvaluationOperation<>(step, e, null));
            }
            // the step barrier below waits for these leftover evals to be consumed -
            // under the ratified protocol nothing is ever cleared, only drained
            d.applyStepAll(new ApplyStepOperation<>(step + 1, null, null));
        }
        d.destroyAll();
        assertTrue(done.await(60, java.util.concurrent.TimeUnit.SECONDS), "consumers must drain");
        assertEquals(0, mismatches.get(), "zero stepIndex mismatches on private channels");
        assertEquals(STEPS * THREADS, consumedApplies.get(),
                "every thread must receive EVERY apply step: no theft, no loss");
    }

    /**
     * LIVENESS GUARD #1 (stall 10:28: apply put vs result-queue cycle): the apply
     * broadcast must complete once channel capacity appears, never deadlock against
     * full channels. The writer-side drain loop is what creates that capacity in
     * production; here capacity is released by a delayed consumer to emulate it.
     */
    @Test
    void applyBroadcastCompletesOnceCapacityAppears() throws Exception {
        SingleWriterOperationDispatcher<Object> d = new SingleWriterOperationDispatcher<>(4, 4, () -> true);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(4);
        AtomicInteger delivered = new AtomicInteger();
        for (int t = 0; t < 4; t++) {
            final int idx = t;
            Thread consumer = new Thread(() -> {
                try {
                    consumeSetup(d.channel(idx));
                    Thread.sleep(150); // lag: the writer broadcasts while we are behind
                    // drain through the eval backlog until the apply arrives
                    while (true) {
                        MoveThreadOperation<Object> op = d.channel(idx).take();
                        if (op instanceof ApplyStepOperation<Object> a) {
                            if (a.stepIndex() == 1) {
                                delivered.incrementAndGet();
                            }
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            consumer.setDaemon(true); // a re-deadlock must fail the assert, not hang the JVM
            consumer.start();
        }
        d.setupAll(null);
        for (int i = 0; i < 16; i++) {
            d.dispatchEvaluation(new MoveEvaluationOperation<>(0, i, null));
        }
        for (int c = 0; c < 4; c++) {
            assertTrue(d.channel(c).remainingCapacity() == 0,
                    "precondition: every channel full (16 evals on 4x4)");
        }
        // release one slot per channel after 100ms (the consumers waking up); the
        // executor is a plain daemon thread so it can never hold the test JVM alive
        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(100);
                for (int c = 0; c < 4; c++) {
                    d.channel(c).poll();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        releaser.setDaemon(true);
        releaser.start();
        long t0 = System.nanoTime();
        d.applyStepAll(new ApplyStepOperation<>(1, null, null));
        assertTrue(System.nanoTime() - t0 < java.util.concurrent.TimeUnit.SECONDS.toNanos(10),
                "apply broadcast must complete once capacity appears, not deadlock");
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(4, delivered.get(), "every consumer received apply step 1");
    }

    /**
     * LIVENESS GUARD #2 (stall 10:41: cross-step backlog accumulation): the step
     * barrier's invariant - the writer does not start the next step until every channel
     * is empty - so a later dispatch can never saturate a channel mid-step.
     */
    @Test
    void stepBarrierLeavesChannelsEmptyBeforeTheNextStep() throws Exception {
        SingleWriterOperationDispatcher<Object> d = new SingleWriterOperationDispatcher<>(4, 8, () -> true);
        java.util.concurrent.CountDownLatch setups = new java.util.concurrent.CountDownLatch(4);
        for (int t = 0; t < 4; t++) {
            final int idx = t;
            Thread c = new Thread(() -> {
                try {
                    consumeSetup(d.channel(idx));
                    setups.countDown();
                    while (true) {
                        d.channel(idx).take(); // the owner consumes everything, forever
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            c.setDaemon(true);
            c.start();
        }
        d.setupAll(null);
        assertTrue(setups.await(5, java.util.concurrent.TimeUnit.SECONDS));
        for (int step = 0; step < 200; step++) {
            for (int e = 0; e < 12; e++) {
                d.dispatchEvaluation(new MoveEvaluationOperation<>(step, e, null));
            }
            d.applyStepAll(new ApplyStepOperation<>(step + 1, null, null));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!d.allChannelsEmpty()) {
                assertTrue(System.nanoTime() < deadline,
                        "step barrier violated: backlog not consumed within 5s at step " + step);
                Thread.sleep(1);
            }
        }
        assertTrue(d.allChannelsEmpty(), "200 steps: no loss, no accumulation, no deadlock");
    }

    /**
     * REGRESSION of the review-gate critical finding (24/08): a DEAD consumer with a
     * non-empty channel must fail the barrier LOUD and BOUNDED, never park forever.
     * This is the TASK-541 park-forever shape the old unbounded loop reintroduced: the
     * drain cleared the poison pill without ever consulting the exception reference.
     */
    @Test
    void deadConsumerFailsTheBarrierLoudAndBounded() throws Exception {
        SingleWriterOperationDispatcher<Object> d = new SingleWriterOperationDispatcher<>(2, 4, () -> true);
        MoveResultQueue<Object> results = new MoveResultQueue<>(8);
        // one consumer takes its setup then DIES with ops still queued on its channel
        Thread shortLived = new Thread(() -> {
            try {
                consumeSetup(d.channel(0));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        shortLived.start();
        shortLived.join(5_000);
        d.setupAll(null); // channel 1's setup (channel 0's was consumed above)
        d.channel(0).put(new MoveEvaluationOperation<>(0, 0, null)); // backlog the dead channel
        // (a) with the exception recorded: fail loud immediately
        results.addExceptionThrown(new IllegalStateException("runner died mid-eval"));
        long t0 = System.nanoTime();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> d.awaitChannelsDrained(results, 60_000));
        assertTrue(ex.getMessage().contains("move thread died"));
        assertTrue(System.nanoTime() - t0 < java.util.concurrent.TimeUnit.SECONDS.toNanos(5),
                "the exception path must fire immediately, not after the bound");
        // (b) with NO exception (dead consumer, silent): bounded return, not forever
        MoveResultQueue<Object> silent = new MoveResultQueue<>(8);
        long t1 = System.nanoTime();
        boolean drained = d.awaitChannelsDrained(silent, 300);
        assertTrue(!drained, "a dead consumer's channel cannot drain: bounded false, never a hang");
        assertTrue(System.nanoTime() - t1 < java.util.concurrent.TimeUnit.SECONDS.toNanos(5),
                "the barrier must respect its bound");
    }

    private static void consumeSetup(java.util.concurrent.BlockingQueue<MoveThreadOperation<Object>> ch)
            throws InterruptedException {
        MoveThreadOperation<Object> op = ch.take();
        assertTrue(op instanceof SetupOperation<Object>, "first op on a channel is its setup");
    }

    private static final java.util.concurrent.atomic.AtomicInteger JITTER =
            new java.util.concurrent.atomic.AtomicInteger();

    private static int applyCounterJitter() {
        return JITTER.getAndIncrement();
    }
}
