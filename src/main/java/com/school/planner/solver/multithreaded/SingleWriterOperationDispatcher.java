package com.school.planner.solver.multithreaded;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * TASK-543 FASE 2 (approved maintainer 24/08 07:50): the SINGLE-WRITER operation dispatch -
 * one PRIVATE channel per move thread instead of the shared operation queue.
 *
 * <p><b>Why this closes two defect classes by construction</b> (the three-defect census
 * is in iterations-542.md, iter-31/31-bis): with ONE producer (the decider thread) and
 * ONE consumer (the owning runner) per channel,
 * <ul>
 * <li>an {@code ApplyStepOperation} can never be <b>stolen</b> by a faster sibling (the
 *     shared-queue desync: a thread consumes two apply copies, another loses its step:
 *     "stepIndex mismatch thread=14 operation=16"), and</li>
 * <li>no channel is ever cleared: an apply-step can never be dropped either (the
 *     clear-first variant - even per-channel - was caught by the protocol test dropping
 *     an unconsumed apply). Backpressure is blocking put(), loss is zero, and stale
 *     evaluation results are drained by the decider's start-of-step drainStale.</li>
 * </ul>
 * The shared result queue stays (its pill TOCTOU was already closed in iter-31 by the
 * post-poll null-move recheck) with its single consumer: the decider, the ONLY writer
 * of the working solution - unchanged from the ratified architecture.
 *
 * <p><b>Scope honesty</b> (declared in the iter-FASE-2 prediction): this closes the
 * RACE classes; it does NOT make runs byte-reproducible (the ORDER results arrive from
 * the threads is still scheduler-decided). Result ordering is a possible follow-up,
 * declared, not promised.

 * <p><b>Flag</b> {@code planner.multithreaded-ls.single-writer.enabled}, RATIFIED
 * DEFAULT ON (maintainer 24/08 12:12, A/B wall closed: LS ~62min vs ~70 legacy, zero
 * mismatch/stalls): only the explicit literal "false" reverts to the legacy
 * shared-queue decider.
 */
final class SingleWriterOperationDispatcher<Solution_> {

    /**
     * TASK-543 stall cure (31/08, thread dump 83min@0.1%CPU): every park in this class
     * is BOUNDED. A bare put() blocked the decider forever when its channel map diverged
     * from the runners' (the stall's PUT monitor 0x...a348 differed from every runner's
     * TAKE monitor 0x...8a98: different queue objects). The bounded offer turns any such
     * divergence into a loud IllegalStateException within seconds.
     */
    static final long PUT_TIMEOUT_MS = 5_000L;

    private final List<BlockingQueue<MoveThreadOperation<Solution_>>> channels;

    /** The shared bounded-offer: timeout + loud ISE naming the channel and liveness. */
    private void offerLoud(BlockingQueue<MoveThreadOperation<Solution_>> ch,
                           MoveThreadOperation<Solution_> op, int channelIndex, String what)
            throws InterruptedException {
        if (!ch.offer(op, PUT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("MT-LS " + what + ": channel " + channelIndex
                    + " did not accept the " + what + " in " + PUT_TIMEOUT_MS
                    + "ms (full or diverged). Runners alive: " + runnersAlive.getAsBoolean()
                    + " - failing loud instead of parking forever (TASK-543 stall cure)");
        }
    }

    private final java.util.function.BooleanSupplier runnersAlive;

    SingleWriterOperationDispatcher(int moveThreadCount, int perThreadCapacity,
                                    java.util.function.BooleanSupplier runnersAlive) {
        this.channels = new ArrayList<>(moveThreadCount);
        for (int i = 0; i < moveThreadCount; i++) {
            channels.add(new ArrayBlockingQueue<>(perThreadCapacity));
        }
        this.runnersAlive = runnersAlive;
    }

    /** The private channel of move thread {@code index} (the runner's only input). */
    BlockingQueue<MoveThreadOperation<Solution_>> channel(int index) {
        return channels.get(index);
    }

    int threadCount() {
        return channels.size();
    }

    /** One setup per thread, in ITS own channel: nobody can take another's setup. */
    void setupAll(ai.timefold.solver.core.impl.score.director.InnerScoreDirector<Solution_, ?> scoreDirector)
            throws InterruptedException {
        for (int i = 0; i < channels.size(); i++) {
            offerLoud(channels.get(i), new SetupOperation<>(scoreDirector), i, "setup");
        }
    }

    private int rrCursor = 0;

    /** Round-robin evaluation dispatch with blocking backpressure (put, never offer).
     * Any thread may evaluate any move; the step discipline lives in the op's stepIndex,
     * identical on every channel. The decider thread is the ONLY caller (single writer).
     * Bounded offer (TASK-543 stall cure): a full channel fails loud, never parks. */
    void dispatchEvaluation(MoveEvaluationOperation<Solution_> op) throws InterruptedException {
        int idx = rrCursor++ % channels.size();
        offerLoud(channels.get(idx), op, idx, "evaluation");
    }

    /** TASK-543 FASE 2 drain (per-step role moved to the decider barrier, TASK-639): true iff every channel has been fully consumed.
     * The writer waits for this after each apply broadcast (draining stale results while
     * waiting): every step then STARTS with empty channels, so the step's evals can never
     * saturate a channel and no put can ever block against the result-queue cycle. */
    boolean allChannelsEmpty() {
        for (BlockingQueue<MoveThreadOperation<Solution_>> ch : channels) {
            if (!ch.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The end-of-step broadcast: exactly ONE apply-step per channel, and NEVER a clear.
     * The protocol test caught the subtle flaw of the clear-first variant: the writer
     * does not wait for consumption, so a clear can drop an UNCONSUMED apply-step of the
     * previous step (the very "thread=14 operation=16" desync the legacy queue exhibits)
     * - mono-producer/mono-consumer alone does not make clear-then-put safe, only
     * theft-proof. The correct single-writer protocol: leftover evaluation ops of the
     * previous step simply stay in the channel (their stepIndex still matches the
     * owner's local step, so the runner's discipline accepts them; their results are
     * already handled by the decider's start-of-step drainStale), and a full channel
     * fails the writer's bounded offer loud (TASK-543 stall cure) - the step barrier
     * (awaitChannelsDrained) prevents accumulation in the healthy path.
     */
    void applyStepAll(ApplyStepOperation<Solution_> step) throws InterruptedException {
        for (int i = 0; i < channels.size(); i++) {
            offerLoud(channels.get(i), step, i, "step");
        }
    }

    /**
     * TASK-543 FASE 2 drain (per-step role moved to the decider barrier, TASK-639), BOUNDED (review gate 24/08, the TASK-541 shape):
     * wait until every channel is empty, for at most {@code timeoutMs}. Each iteration
     * drains the result queue's stale entries AND consults its recorded exception: a
     * dead runner leaves its channel non-empty forever, and draining alone would both
     * park forever and swallow the poison pill - so an exception fails loud immediately
     * (the caller decides what to do). Returns true iff every channel drained; false on
     * timeout or interruption (the caller must NOT proceed to a blocking deliver).
     */
    boolean awaitChannelsDrained(MoveResultQueue<?> resultQueue, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (!allChannelsEmpty()) {
            Throwable ex = resultQueue.exceptionOrNull();
            if (ex != null) {
                throw new IllegalStateException("MT-LS step barrier: a move thread died"
                        + " with its channel non-empty - failing loud (TASK-541 shape)", ex);
            }
            resultQueue.drainStale();
            for (int i = 0; i < 100 && !allChannelsEmpty(); i++) {
                Thread.onSpinWait();
            }
            if (allChannelsEmpty()) {
                return true;
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /** One destroy per channel at phase end. */
    void destroyAll() throws InterruptedException {
        for (int i = 0; i < channels.size(); i++) {
            if (!channels.get(i).offer(new DestroyOperation<>(),
                    PUT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                // destroy is best-effort: shutdownNow() below will interrupt the stragglers
            }
        }
    }
}
