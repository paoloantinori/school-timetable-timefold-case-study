package com.school.planner.solver.multithreaded;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;
import ai.timefold.solver.core.impl.solver.thread.ChildThreadType;
import ai.timefold.solver.core.preview.api.move.Move;

/**
 * Child thread that pulls move-evaluation operations, evaluates them on its own isolated
 * InnerScoreDirector (step chasing). Pinned to Timefold impl internals (verified 2.3.0 through 2.7.0-rc-1).
 */
public class MoveThreadRunner<Solution_> implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MoveThreadRunner.class);

    private final BlockingQueue<MoveThreadOperation<Solution_>> operationQueue;
    private final MoveResultQueue<Solution_> resultQueue;
    private final CyclicBarrier barrier;
    private final InnerScoreDirector<Solution_, ?> parentScoreDirector;
    private final int threadIndex;

    private InnerScoreDirector<Solution_, ?> childScoreDirector;
    private final AtomicLong calculationCount = new AtomicLong(-1);
    /**
     * Set by {@link #markShuttingDown()} before the threads are interrupted, so that the
     * {@link BrokenBarrierException}/{@link InterruptedException} storms caused by the EXPECTED
     * {@code shutdownNow()} at phase end are recognized as normal shutdown and logged at DEBUG,
     * not propagated as solver errors (the Fase 3 A/B logged 58 ERROR lines per run for this).
     */
    private volatile boolean shuttingDown;

    public MoveThreadRunner(
            BlockingQueue<MoveThreadOperation<Solution_>> operationQueue,
            MoveResultQueue<Solution_> resultQueue,
            CyclicBarrier barrier,
            InnerScoreDirector<Solution_, ?> parentScoreDirector,
            int threadIndex) {
        this.operationQueue = operationQueue;
        this.resultQueue = resultQueue;
        this.barrier = barrier;
        this.parentScoreDirector = parentScoreDirector;
        this.threadIndex = threadIndex;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        int stepIndex = -1;
        try {
            while (true) {
                MoveThreadOperation<Solution_> operation;
                try {
                    // TASK-543 stall cure: BOUNDED take. A bare take() parked a runner forever
                    // when the decider's channel map diverged (the stall's TAKE monitors were
                    // on different queue objects than the PUT monitor). The poll turns any
                    // divergence into a loud exit within the barrier timeout.
                    operation = operationQueue.poll(BARRIER_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (operation == null) {
                        if (shuttingDown) {
                            break;
                        }
                        throw new IllegalStateException("Move thread " + threadIndex
                                + " received no operation for 60s - the decider's dispatch map"
                                + " may have diverged from this runner's channel"
                                + " (TASK-543 stall cure: failing loud instead of parking)");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (shuttingDown) {
                        break;
                    }
                    throw new RuntimeException("Move thread " + threadIndex + " interrupted mid-take", e);
                }

                if (operation instanceof SetupOperation<Solution_> setup) {
                    childScoreDirector = setup.parentScoreDirector()
                            .createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
                    stepIndex = 0;
                    childScoreDirector.calculateScore();
                    LOGGER.trace("Move thread {} setup: stepIndex={}", threadIndex, stepIndex);
                    barrierAwait();

                } else if (operation instanceof DestroyOperation<Solution_>) {
                    if (childScoreDirector != null) {
                        calculationCount.set(childScoreDirector.getCalculationCount());
                    }
                    break;

                } else if (operation instanceof ApplyStepOperation<Solution_> applyStep) {
                    if (stepIndex + 1 != applyStep.stepIndex()) {
                        throw new IllegalStateException("stepIndex mismatch: thread=" + stepIndex
                                + " operation=" + applyStep.stepIndex());
                    }
                    stepIndex = applyStep.stepIndex();
                    if (applyStep.step() != null) {
                        Move<Solution_> rebasedStep = applyStep.step().rebase(childScoreDirector);
                        childScoreDirector.executeMove(rebasedStep);
                    }
                    LOGGER.trace("Move thread {} step chasing: stepIndex={}", threadIndex, stepIndex);
                    // Two awaits: the first syncs after applying the step, the second completes
                    // the barrier cycle (all threads must pass both tripped parties consistently).
                    barrierAwait();
                    barrierAwait();

                } else if (operation instanceof MoveEvaluationOperation<Solution_> evalOp) {
                    if (stepIndex != evalOp.stepIndex()) {
                        throw new IllegalStateException("stepIndex mismatch: thread=" + stepIndex
                                + " eval=" + evalOp.stepIndex());
                    }
                    Move<Solution_> rebasedMove = evalOp.move().rebase(childScoreDirector);
                    var innerScore = childScoreDirector.executeTemporaryMove(rebasedMove, false);
                    try {
                        resultQueue.addMove(evalOp.moveIndex(), rebasedMove, innerScore);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        if (shuttingDown) {
                            break;
                        }
                        throw new RuntimeException("Move thread " + threadIndex + " interrupted", ie);
                    }
                    LOGGER.trace("Move thread {} eval: stepIndex={}, moveIndex={}",
                            threadIndex, stepIndex, evalOp.moveIndex());
                }
            }
            LOGGER.trace("Move thread {} finished", threadIndex);
        } catch (ShutdownExitException e) {
            // Expected phase-end exit: the decider's interrupt interrupted this thread
            // (directly, or by breaking the barrier for the remaining waiters). DEBUG only,
            // never propagated: the solver terminates the phase correctly.
            LOGGER.debug("Move thread {} exited during shutdown ({})", threadIndex, e.getMessage());
        } catch (RuntimeException e) {
            // A REAL error, even if it lands in the shutdown window: identity, not the
            // shuttingDown flag, decides (a genuine bug during shutdown must still surface).
            LOGGER.error("Move thread {} exception (propagating to solver)", threadIndex, e);
            resultQueue.addExceptionThrown(e);
        } finally {
            if (childScoreDirector != null) {
                childScoreDirector.close();
            }
        }
    }

    /**
     * TASK-541 (22/08 bench thread dump): the await is BOUNDED. A bare await() parked move
     * threads forever when a barrier party was lost (a runner that exited between rounds while
     * the decider had already consumed the round's results): 2h+ at 0% CPU. The timeout turns
     * the lost party into a loud error (or, during shutdown, the expected exit) within a minute.
     */
    void barrierAwait(long timeoutMs) {
        try {
            barrier.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException | BrokenBarrierException e) {
            Thread.currentThread().interrupt();
            if (shuttingDown) {
                // Normal phase-end exit: the decider's interrupt interrupted this thread.
                throw new ShutdownExitException(e.getClass().getSimpleName());
            }
            throw new RuntimeException("Move thread " + threadIndex + " barrier broken", e);
        } catch (java.util.concurrent.TimeoutException e) {
            if (shuttingDown) {
                throw new ShutdownExitException("barrier timeout during shutdown");
            }
            throw new IllegalStateException("Move thread " + threadIndex
                    + " barrier timed out after " + timeoutMs + "ms: a barrier party was lost"
                    + " (a move thread exited between rounds?) - failing loud instead of"
                    + " parking forever", e);
        }
    }

    private void barrierAwait() {
        barrierAwait(BARRIER_TIMEOUT_MS);
    }

    /** The bounded barrier wait (see {@link #barrierAwait(long)}). */
    static final long BARRIER_TIMEOUT_MS = 60_000;

    /**
     * Control-flow exit, not an error: a stack-free per-throw exception (no shared instance to
     * keep immutable by convention; the message records which interrupt family ended the wait).
     */
    /** Visible for the bounded-wait contract test (same package). */
    static final class ShutdownExitException extends RuntimeException {
        ShutdownExitException(String interruptMode) {
            super("move-thread shutdown exit via " + interruptMode);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    /** Called by the decider (same package) before the decider interrupts: marks the storm expected. */
    void markShuttingDown() {
        shuttingDown = true;
    }

    public long getCalculationCount() {
        long count = calculationCount.get();
        return count == -1 ? 0 : count;
    }
}
