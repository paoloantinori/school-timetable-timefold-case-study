package com.school.planner.solver.multithreaded;

import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.timefold.solver.core.impl.localsearch.decider.LocalSearchDecider;
import ai.timefold.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.timefold.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.timefold.solver.core.impl.neighborhood.MoveRepository;
import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;
import ai.timefold.solver.core.impl.solver.termination.PhaseTermination;
import ai.timefold.solver.core.preview.api.move.Move;

import com.school.planner.solver.AdaptiveMoveWeights;

/**
 * Community replacement for Timefold Enterprise's multi-threaded LS decider.
 * Adapted from OptaPlanner 8.x's MultiThreadedLocalSearchDecider. Implements the
 * "step chasing" algorithm from De Smet & Wauters, PATAT 2020.
 *
 * <p>Pinned to Timefold {@code impl} internals (InnerScoreDirector,
 * LocalSearchDecider); last re-verified against 2.7.0-rc-1 (TASK-639: the barrier
 * protocol is OURS, the reflection surface survived 2.3.0 through 2.7.0-rc-1).
 * A Timefold upgrade still requires re-checking this class against the new internals.
 */
public class MultiThreadedLocalSearchDecider<Solution_> extends LocalSearchDecider<Solution_> {

    private static final Logger LOG = LoggerFactory.getLogger(MultiThreadedLocalSearchDecider.class);

    private final int moveThreadCount;
    private final int selectedMoveBufferSize;

    private BlockingQueue<MoveThreadOperation<Solution_>> operationQueue;
    /**
     * TASK-543 FASE 2: per-thread channels (single-writer dispatch). Null when the flag
     * is opted out; RATIFIED DEFAULT ON (maintainer 24/08 12:12).
     */
    private SingleWriterOperationDispatcher<Solution_> singleWriter;
    /**
     * TASK-543 FASE 2 flag, RATIFIED DEFAULT ON (maintainer 24/08 12:12, after the A/B wall:
     * LS ~62min vs ~70 legacy, zero mismatch/stalls, delivery in band). Standard opt-out
     * form: null/absent -> ON, only the explicit literal "false" reverts to the legacy
     * shared-queue decider.
     */
    static final String SINGLE_WRITER_FLAG = "planner.multithreaded-ls.single-writer.enabled";

    public static boolean singleWriterEnabled() {
        String v = System.getProperty(SINGLE_WRITER_FLAG);
        return v == null || !v.equalsIgnoreCase("false");
    }
    private MoveResultQueue<Solution_> resultQueue;
    private CyclicBarrier moveThreadBarrier;

    /** The decider's bounded result wait (see {@link #forageResult}). */
    private static final long RESULT_POLL_TIMEOUT_MS = 30_000;
    /**
     * TASK-543 ROOT CAUSE FIX (31/08, maintainer: "perche' non la fix vera?"): direct thread
     * management replaces the ExecutorService. The executor's shutdownNow() + bounded
     * awaitTermination(5s+10s) left a WINDOW where old runners survived into the next
     * phaseStarted, taking from OLD channels while the decider put to NEW ones (the
     * 83-minute stall: diverged channel maps). Thread.join() is a HARD guarantee: no
     * phaseStarted proceeds until every old runner is definitively dead.
     */
    private java.util.List<Thread> moveThreads;
    private java.util.List<MoveThreadRunner<Solution_>> moveThreadRunnerList;

    /**
     * @param moveThreadCount number of child move-evaluation threads; must be >= 1.
     *        The evaluation-queue backpressure buffer is FIXED at
     *        {@value #BUFFER_PER_THREAD} per move thread (a larger buffer would increase
     *        throughput at the cost of memory and of moves evaluated after the step was
     *        already decided; the A/B ran at this value and the knob is not exposed).
     */
    public MultiThreadedLocalSearchDecider(String logIndentation,
            PhaseTermination<Solution_> termination,
            MoveRepository<Solution_> moveRepository,
            Acceptor<Solution_> acceptor,
            LocalSearchForager<Solution_> forager,
            int moveThreadCount) {
        super(logIndentation, termination, moveRepository, acceptor, forager);
        if (moveThreadCount < 1) {
            throw new IllegalArgumentException("moveThreadCount must be >= 1, got " + moveThreadCount);
        }
        this.moveThreadCount = moveThreadCount;
        this.selectedMoveBufferSize = moveThreadCount * BUFFER_PER_THREAD;
    }

    /** Fixed backpressure buffer per move thread (see the constructor javadoc). */
    private static final int BUFFER_PER_THREAD = 10;

    /** Distinct thread names for readable dumps: JVM-global monotonic across LS phases. */
    private static final java.util.concurrent.atomic.AtomicInteger THREAD_NAME_INDEX =
            new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseStarted(phaseScope);
        operationQueue = new ArrayBlockingQueue<>(selectedMoveBufferSize + moveThreadCount * 2);
        resultQueue = new MoveResultQueue<>(selectedMoveBufferSize + moveThreadCount);
        // TASK-639 cure: the decider JOINS the barrier as the N+1th party in
        // SINGLE-WRITER mode (see deciderBarrierJoin for the full contract). LEGACY
        // keeps N parties BY DESIGN: its dispatch is a FIFO put on one shared queue,
        // it never infers "consumed" from channel emptiness (no awaitChannelsDrained,
        // no timed offer), the stepIndex mismatch discipline guards racing ahead, and
        // the queue capacity (bufferSize + 2N) strictly exceeds worst in-flight
        // content (bufferSize + N): the offerLoud race class cannot exist there. If
        // a refactor ever adds a TIMED OFFER to the legacy branch, or extends the
        // existing shared-queue clear() beyond its current step-boundary scope, THIS
        // comment is what must fail loudly first (the clear exists and is the
        // documented apply-drop class: it is safe ONLY because it runs at the step
        // boundary when all runners are parked at the poll).
        // The flag is captured ONCE for both the party count and the dispatcher
        // (the m22MinDays lesson: two live reads of the same property in one
        // protocol-critical method can diverge: N+1 barrier with null singleWriter).
        boolean sw = singleWriterEnabled();
        moveThreadBarrier = new CyclicBarrier(
                sw ? moveThreadCount + 1 : moveThreadCount);
        InnerScoreDirector<Solution_, ?> scoreDirector = phaseScope.getScoreDirector();
        moveThreads = new java.util.ArrayList<>(moveThreadCount);
        moveThreadRunnerList = new java.util.ArrayList<>(moveThreadCount);
        this.singleWriter = sw
                ? new SingleWriterOperationDispatcher<>(moveThreadCount, BUFFER_PER_THREAD + 2,
                        () -> !shuttingDown() && !Thread.currentThread().isInterrupted())
                : null;
        for (int i = 0; i < moveThreadCount; i++) {
            var runner = new MoveThreadRunner<>(
                    singleWriter != null ? singleWriter.channel(i) : operationQueue,
                    resultQueue, moveThreadBarrier,
                    scoreDirector, i);
            moveThreadRunnerList.add(runner);
            Thread t = new Thread(runner, "timefold-move-thread-" + THREAD_NAME_INDEX.incrementAndGet());
            t.setDaemon(true);
            moveThreads.add(t);
            t.start();
        }
        if (singleWriter != null) {
            // one setup per PRIVATE channel: nobody can take another thread's setup
            try {
                singleWriter.setupAll(scoreDirector);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while dispatching setups", e);
            }
            // TASK-639: join the runners' single setup trip (the +1 party contract)
            deciderBarrierJoin("setup");
        } else {
            for (int i = 0; i < moveThreadCount; i++) {
                operationQueue.add(new SetupOperation<>(scoreDirector));
            }
        }
        LOG.info("Multi-threaded LS phase started: {} move threads, buffer size {}, dispatch {}",
                moveThreadCount, selectedMoveBufferSize,
                singleWriter != null ? "SINGLE-WRITER per-thread channels (TASK-543 F2)" : "shared queue (legacy)");
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseEnded(phaseScope);
        // Do NOT call moveThreadBarrier.reset() here: on the ABORTED paths (interrupt
        // around applyStepAll, or the shutdown skip in deciderBarrierJoin) children may
        // still be mid-trip. The DestroyOperation + joinMoveThreadsHard path handles
        // cleanup (TASK-639: on the healthy path the decider joined both trips, so
        // runners are parked at the poll here).
        if (singleWriter != null) {
            try {
                if (!awaitChannelsDrained()) {
                    // aborted (timeout/interrupt): skip the blocking deliver, the
                    // shutdown path right below interrupts and stops every runner
                    LOG.warn("MT-LS phase-end drain aborted before destroy; forcing shutdown (TASK-639: the per-step barrier lives in deciderBarrierJoin)");
                } else {
                    singleWriter.destroyAll();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            for (int i = 0; i < moveThreadCount; i++) {
                operationQueue.add(new DestroyOperation<>());
            }
        }
        joinMoveThreadsHard();
        long childCalcCount = 0;
        for (var runner : moveThreadRunnerList) {
            childCalcCount += runner.getCalculationCount();
        }
        phaseScope.addChildThreadsScoreCalculationCount(childCalcCount);
        operationQueue = null;
        singleWriter = null;
        resultQueue = null;
        moveThreadRunnerList = null;
        moveThreads = null;
        LOG.info("Multi-threaded LS phase ended");
    }

    @Override
    public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
        super.solvingError(solverScope, exception);
        shutdownMoveThreads();
        joinMoveThreadsHard();
    }

    @Override
    public void decideNextStep(LocalSearchStepScope<Solution_> stepScope) {
        int stepIndex = stepScope.getStepIndex();
        resultQueue.drainStale();
        var scoreDirector = stepScope.getScoreDirector();
        scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(true);

        int selectMoveIndex = 0;
        int movesInPlay = 0;
        boolean thinnedLast = false;
        Iterator<Move<Solution_>> moveIterator = getMoveRepository().iterator();
        do {
            thinnedLast = false;   // reset per iteration: exhaustion must be able to exit the loop
            boolean hasNextMove = moveIterator.hasNext();
            if (movesInPlay > 0 && (selectMoveIndex >= selectedMoveBufferSize || !hasNextMove)) {
                if (forageResult(stepScope, stepIndex)) {
                    break;
                }
                movesInPlay--;
            }
            if (hasNextMove) {
                var move = moveIterator.next();
                // TASK-500 forma B: adaptive stream thinning. The union samples by the
                // RATIFIED static weights; reweighting happens HERE, before the dispatch,
                // so a thinned move costs nothing (no queue slot, no evaluation) and the
                // decider's result accounting stays exact (only dispatched moves are
                // expected back). thinnedLast keeps the loop pulling when a whole window
                // is thinned (otherwise do-while(movesInPlay>0) would end the step early).
                if (!AdaptiveMoveWeights.shouldEvaluate(move)) {
                    thinnedLast = true;
                    continue;
                }
                thinnedLast = false;
                try {
                    if (singleWriter != null) {
                        singleWriter.dispatchEvaluation(new MoveEvaluationOperation<>(stepIndex, selectMoveIndex, move));
                    } else {
                        operationQueue.put(new MoveEvaluationOperation<>(stepIndex, selectMoveIndex, move));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                selectMoveIndex++;
                movesInPlay++;
            }
        } while (movesInPlay > 0 || thinnedLast);

        if (singleWriter == null) {
            operationQueue.clear();
        }
        scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(false);
        pickMove(stepScope);

        // No winning move: still sync children to advance stepIndex (null step = no-op chase)
        ApplyStepOperation<Solution_> stepOperation = new ApplyStepOperation<>(
                stepIndex + 1, stepScope.getStep(), stepScope.getScore());
        if (singleWriter != null) {
            try {
                singleWriter.applyStepAll(stepOperation);
                // both trips of the runners' double await (the contract: deciderBarrierJoin)
                deciderBarrierJoin("step/1");
                deciderBarrierJoin("step/2");
            } catch (InterruptedException e) {
                // TASK-639 review F1: swallowing here strands the runners mid-trip
                // (N+1 parties, the decider never arrives: 60s lost-party storm,
                // misattributed). Fail loud unless truly shutting down so solvingError
                // marks, interrupts and produces the quiet ShutdownExitException path.
                Thread.currentThread().interrupt();
                failLoudUnlessShuttingDown("step", "interrupted between applyStepAll and the trips", e);
            }
        } else {
            for (int i = 0; i < moveThreadCount; i++) {
                operationQueue.add(stepOperation);
            }
        }
    }


    /**
     * PHASE-END drain (its ONLY role since TASK-639: the per-step barrier now lives
     * in deciderBarrierJoin, which replaced this call inside decideNextStep): wait
     * until every runner has consumed its channel before the destroy teardown, for
     * at most 60s. A move-thread exception fails loud; a timeout or interrupt
     * returns false and the caller must not issue a blocking deliver.
     */
    private boolean awaitChannelsDrained() {
        return singleWriter.awaitChannelsDrained(resultQueue, BARRIER_TIMEOUT_MS);
    }

    /**
     * TASK-639: the decider's bounded participation in a barrier trip. The JOIN
     * exists because a runner between its first and second await has an EMPTY
     * channel while NOT consuming: any "channels empty" inference (the old
     * awaitChannelsDrained protocol) raced the decider's next evaluation offer
     * into the 5s offerLoud. Explicit trips, no timing inference. ONE
     * full-timeout await: CyclicBarrier semantics FORBID slicing (a party's
     * timeout breaks the barrier for everyone: the first validation run died
     * exactly there, a 5s slice timed out on a slow setup trip and the re-await
     * got BrokenBarrierException). The 60s bound keeps the TASK-541 loud fail;
     * the recorded-exception fast-fail is checked once before the await.
     *
     * <p>SHUTDOWN CONTRACT (deliberate asymmetry with the runner side): during
     * shutdown the decider RETURNS SILENTLY, it does not throw the runner's
     * ShutdownExitException. The decider's owner is the solver phase, which
     * treats any exception as a solving error and would re-enter solvingError
     * with an interrupt storm for a purely control-flow situation; the phase
     * boundary that immediately follows (phaseEnded -> drain/destroy ->
     * joinMoveThreadsHard) performs the real teardown with its own logging.
     */
    private void deciderBarrierJoin(String trip) {
        Throwable ex = resultQueue.exceptionOrNull();
        if (ex != null) {
            throw new IllegalStateException("MT-LS step barrier: a move thread died"
                    + " mid-protocol (" + trip + "): " + ex, ex);
        }
        try {
            moveThreadBarrier.await(BARRIER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failLoudUnlessShuttingDown(trip, "interrupted outside shutdown", e);
        } catch (java.util.concurrent.BrokenBarrierException e) {
            failLoudUnlessShuttingDown(trip, "broken: a runner left the barrier mid-protocol", e);
        } catch (java.util.concurrent.TimeoutException e) {
            // TASK-639 review F3: a runner that died WHILE we were parked leaves its
            // real throwable in the result queue: chain it instead of guessing.
            Throwable dead = resultQueue.exceptionOrNull();
            failLoudUnlessShuttingDown(trip, "timed out after " + BARRIER_TIMEOUT_MS
                    + "ms: a runner never reached the trip"
                    + (dead != null ? "; runner died: " + dead : " (died mid-protocol?)")
                    + "; failing loud instead of parking forever (TASK-541 shape)",
                    (Exception) (dead != null ? dead : e));
        }
    }

    /** The three decider-side catch guards share this shape: shutdown exits the
     *  protocol silently (see deciderBarrierJoin's javadoc), anything else is loud. */
    private void failLoudUnlessShuttingDown(String trip, String detail, Exception e) {
        if (shuttingDown()) {
            LOG.debug("MT-LS decider barrier ({}) skipped during shutdown: {}", trip, detail);
            return;
        }
        throw new IllegalStateException("MT-LS decider barrier (" + trip + ") " + detail, e);
    }

    /** The TASK-541 family bound: every park has a deadline, none is forever. */
    private static final long BARRIER_TIMEOUT_MS = 60_000L;

    private boolean forageResult(LocalSearchStepScope<Solution_> stepScope, int stepIndex) {
        MoveResultQueue.MoveResult<Solution_> result;
        try {
            // TASK-541 (22/08 bench thread dump): BOUNDED take. A bare take() parked the
            // decider forever when every move thread was stuck on a lost barrier party
            // (2h+ at 0% CPU). Null at the timeout = either shutdown in progress or a loud
            // watchdog failure - never an infinite park.
            result = resultQueue.poll(RESULT_POLL_TIMEOUT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
        if (result == null) {
            if (shuttingDown()) {
                return true;
            }
            throw new IllegalStateException("MT-LS watchdog: no move result for "
                    + RESULT_POLL_TIMEOUT_MS + "ms - move threads stuck (lost barrier party?)."
                    + " Failing loud instead of parking forever (TASK-541 thread-dump incident)");
        }
        // Defensive in-depth (TASK-542 NPE 24/08): a null move is a poison pill, never a
        // legitimate result - fail LOUD with the signature instead of an opaque NPE.
        if (result.move() == null) {
            throw new IllegalStateException("MT-LS forageResult received a null-move result"
                    + " (poison pill from an exception path) - moveIndex=" + result.moveIndex());
        }
        var scoreDirector = stepScope.getScoreDirector();
        var foragingMove = result.move().rebase(scoreDirector);
        var moveScope = new LocalSearchMoveScope<>(stepScope, result.moveIndex(), foragingMove);
        moveScope.setScore(result.score());
        boolean accepted = acceptor.isAccepted(moveScope);
        moveScope.setAccepted(accepted);
        // TASK-500 adaptive move weights: the single-writer bookkeeping hook (this loop
        // is the ONLY consumer of the result queue, so counters and weights have no
        // contention by construction; see AdaptiveMoveWeights' thread story).
        AdaptiveMoveWeights.record(foragingMove, accepted);
        forager.addMove(moveScope);
        if (forager.isQuitEarly()) {
            return true;
        }
        stepScope.getPhaseScope().getSolverScope().checkYielding();
        return termination.isPhaseTerminated(stepScope.getPhaseScope());
    }

    @Override
    protected void pickMove(LocalSearchStepScope<Solution_> stepScope) {
        var winningMove = forager.pickMove(stepScope);
        if (winningMove != null) {
            stepScope.setStep(winningMove.getMove());
            stepScope.setScore(winningMove.getScore());
        }
    }

    private void shutdownMoveThreads() {
        // Mark + interrupt: the signal phase (fast). The HARD guarantee comes from
        // joinMoveThreadsHard() below, which the phase boundary always calls.
        if (moveThreadRunnerList != null) {
            for (var runner : moveThreadRunnerList) {
                runner.markShuttingDown();
            }
        }
        if (moveThreads != null) {
            for (Thread t : moveThreads) {
                t.interrupt();
            }
        }
    }

    /**
     * TASK-543 ROOT CAUSE FIX: HARD join. Blocks until every move thread is dead.
     * Called at EVERY phase boundary (phaseEnded, solvingError) BEFORE the next
     * phaseStarted can create a new dispatcher. This eliminates the window where
     * old runners survive into the new phase's channel map (the 83-min stall).
     * The bounded puts/takes (the stall cure already applied) act as the safety net
     * inside the join window: a stuck runner fails loud within its timeout instead
     * of surviving silently.
     */
    private void joinMoveThreadsHard() {
        if (moveThreads == null) {
            return;
        }
        // SHARED deadline (efficiency review: sequential per-thread 120s = 16min worst;
        // healthy threads die in parallel so the wall is max, not sum). 2x the family
        // bound (60s barrier/poll) as the pathological margin.
        long deadlineNanos = System.nanoTime() + 2 * BARRIER_TIMEOUT_MS * 1_000_000L;
        for (Thread t : moveThreads) {
            long remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000;
            if (remainingMs <= 0) {
                LOG.error("MT-LS ROOT FIX: join budget exhausted, {} leaked", t.getName());
                continue;
            }
            try {
                t.join(remainingMs);
                if (t.isAlive()) {
                    LOG.error("MT-LS ROOT FIX: move thread {} did not die within the join"
                            + " budget - leaked (this is the stall window the fix closes)",
                            t.getName());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        moveThreads = null;
    }

    /** True once the decider's shutdown path has begun (the bounded waits treat it as exit). */
    boolean shuttingDown() {
        return moveThreads == null;
    }
}
