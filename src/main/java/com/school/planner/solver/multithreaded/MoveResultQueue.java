package com.school.planner.solver.multithreaded;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import ai.timefold.solver.core.impl.score.director.InnerScore;
import ai.timefold.solver.core.preview.api.move.Move;

/**
 * Result queue for multi-threaded LS move evaluation. Thread-safe FIFO with exception
 * propagation. Results arrive in completion order (not move-index order); the forager
 * consumes them as they arrive. Reproducibility is preserved by the parent thread doing
 * all PRNG-dependent selection/acceptance; score calculation is deterministic.
 *
 * <p>Note: if strict move-index ordering is needed for reproducibility (e.g. with
 * order-sensitive acceptors like late-acceptance/tabu), port the backlog-map ordering
 * from OptaPlanner 8.x's OrderByMoveIndexBlockingQueue. For now FIFO is sufficient
 * because the acceptor sees results in completion order, which varies by thread scheduling
 * but produces equivalent scores on average (NON_REPRODUCIBLE mode).
 */
public class MoveResultQueue<Solution_> {

    private final BlockingQueue<MoveResult<Solution_>> delegate;
    private final AtomicReference<Throwable> exceptionRef = new AtomicReference<>();

    public MoveResultQueue(int capacity) {
        this.delegate = new ArrayBlockingQueue<>(capacity);
    }

    public void addMove(int moveIndex, Move<Solution_> move, InnerScore<?> score)
            throws InterruptedException {
        delegate.put(new MoveResult<>(moveIndex, move, score));
    }

    public void addExceptionThrown(Throwable throwable) {
        exceptionRef.set(throwable);
        // Enqueue a poison-pill result so take() unblocks immediately
        delegate.offer(new MoveResult<>(-1, null, null));
    }

    public MoveResult<Solution_> take() throws InterruptedException {
        Throwable ex = exceptionRef.get();
        if (ex != null) {
            throw new RuntimeException("Move thread exception propagated to solver", ex);
        }
        return delegate.take();
    }

    /**
     * TASK-541 (22/08 bench thread dump): the BOUNDED take. A bare take() parked the decider
     * forever once every move thread was stuck on a lost barrier party. Returns null at the
     * timeout (same exception-propagation contract as take); the caller decides whether null
     * means shutdown or a loud failure.
     */
    public MoveResult<Solution_> poll(long timeoutMs) throws InterruptedException {
        Throwable ex = exceptionRef.get();
        if (ex != null) {
            throw new RuntimeException("Move thread exception propagated to solver", ex);
        }
        MoveResult<Solution_> result = delegate.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        // TASK-542 medium-run NPE (24/08 06:20): the pre-poll check above has a TOCTOU
        // window - the exception can land (and its poison pill be offered) WHILE the
        // decider is already parked inside delegate.poll, so the parked poller wakes on
        // the pill and returns it as a normal result (move() == null -> NPE at rebase).
        // Re-check on the pill's signature (null move AND negative index: the pill is
        // the only legal null-move entry, tests use index-carrying placeholders).
        if (result != null && result.move() == null && result.moveIndex() < 0) {
            Throwable late = exceptionRef.get();
            if (late != null) {
                throw new RuntimeException("Move thread exception propagated to solver", late);
            }
            throw new IllegalStateException("MT-LS result queue delivered a null-move entry"
                    + " with no exception recorded - treat as watchdog failure (TASK-542 NPE guard)");
        }
        return result;
    }

    /**
     * TASK-543 FASE 2 (review gate 24/08): the recorded move-thread exception, if any.
     * The step barrier consults this EVERY iteration: drainStale() clears the queue
     * (pill included) but never this reference, so a barrier that only drained would
     * park forever on a dead runner's never-emptying channel - the exact TASK-541
     * park-forever shape. Fail loud instead.
     */
    public Throwable exceptionOrNull() {
        return exceptionRef.get();
    }

    public void drainStale() {
        delegate.clear();
    }

    public record MoveResult<Solution_>(
            int moveIndex,
            Move<Solution_> move,
            InnerScore<?> score
    ) {}
}
