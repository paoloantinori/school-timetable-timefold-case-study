package com.school.planner.solver.raceinjection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TASK-639 stand-in del protocollo barrier MT-LS: rispecchia ESATTAMENTE la
 * forma di produzione (decider parte N+1 della {@link CyclicBarrier}, runner
 * con doppia await dopo l'ApplyStep, decider che NON spedisce valutazioni
 * prima di entrambi i trip) cosi' le regole Byteman possono iniettare le
 * finestre di concorrenza in forma deterministica (il pattern
 * {@code StandInDirtyCell} della TASK-543, applicato al protocollo).
 *
 * <p>Membri volontariamente idioti (String al posto delle mosse, canali di
 * lunghezza 1): quello che si testa e' la COREOGRAFIA, non il carico.</p>
 */
public class BarrierProtocolStandIn {

    /** Il gate delle regole Byteman (come raceMode nella 543). */
    public volatile boolean raceMode;

    /** Marca d'iniezione: la regola della seconda-await lenta la setta. */
    public volatile boolean secondAwaitDelayed;

    /** Marca d'iniezione: la regola della morte a meta' trip la setta. */
    public volatile boolean deathInjected;

    /** Il timestamp della prima valutazione spedita dal decider (0 = mai). */
    public final AtomicLong firstDispatchNanos = new AtomicLong(0);

    /** Il timestamp in cui l'ULTIMO runner completa la seconda await. */
    public final AtomicLong lastSecondAwaitDoneNanos = new AtomicLong(0);

    /** Il timestamp in cui il runner 0 ENTRA nella seconda await (pre-ritardo):
     *  il confronto strutturale del test (dispatch >= questo + ~7s) e' immune
     *  alla micro-race del risveglio simultaneo dal trip. */
    public final AtomicLong runner0SecondAwaitEnterNanos = new AtomicLong(0);

    /** L'eccezione loud del decider, se la cura scatta. */
    public final AtomicReferenceLike<IllegalStateException> deciderFailure =
            new AtomicReferenceLike<>();

    final int runnerCount;
    final CyclicBarrier barrier;
    final List<ArrayBlockingQueue<String>> channels;
    final List<Thread> threads = new ArrayList<>();

    /** Micro AtomicReference (stand-in package-local: niente dipendenze extra). */
    public static final class AtomicReferenceLike<T> {
        private volatile T value;

        public void set(T v) {
            value = v;
        }

        public T get() {
            return value;
        }
    }

    public BarrierProtocolStandIn(int runnerCount) {
        this.runnerCount = runnerCount;
        this.barrier = new CyclicBarrier(runnerCount + 1);
        this.channels = new ArrayList<>(runnerCount);
        for (int i = 0; i < runnerCount; i++) {
            channels.add(new ArrayBlockingQueue<>(2));
        }
    }

    /** Avvia i runner (il decider e' il thread chiamante, come in produzione). */
    public void start() {
        for (int i = 0; i < runnerCount; i++) {
            final int idx = i;
            Thread t = new Thread(() -> runnerLoop(idx), "standin-move-" + idx);
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
    }

    private void runnerLoop(int idx) {
        try {
            ArrayBlockingQueue<String> ch = channels.get(idx);
            // setup trip (la forma di produzione: una await dopo il setup)
            barrierAwait();
            while (true) {
                String op = ch.poll(10, TimeUnit.SECONDS);
                if (op == null || "DESTROY".equals(op)) {
                    return;
                }
                if ("DEATH_AFTER_FIRST_AWAIT".equals(op) && raceMode) {
                    // la regola della morte: esplode DOPO la prima await
                    barrierAwait();
                    throw new IllegalStateException("standin runner " + idx
                            + " died mid-protocol (injected)");
                }
                // ApplyStep: doppia await (i punti di aggancio delle regole).
                // Lo stamp dell'ingresso sta PRIMA della chiamata: la regola
                // del ritardo scatta all'ENTRY di secondAwait, quindi con lo
                // stamp qui il ritardo finisce DENTRO la finestra misurata.
                firstAwait();
                if (idx == 0) {
                    runner0SecondAwaitEnterNanos.compareAndSet(0, System.nanoTime());
                }
                secondAwait(idx);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Prima await del passo: aggancio Byteman non usato direttamente. */
    void firstAwait() {
        barrierAwait();
    }

    /** Seconda await del passo: la regola DELAY congela qui dentro. */
    void secondAwait(int idx) {
        barrierAwait();
        long now = System.nanoTime();
        lastSecondAwaitDoneNanos.updateAndGet(old -> Math.max(old, now));
    }

    void barrierAwait() {
        try {
            barrier.await(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("standin barrier broken: " + e, e);
        }
    }

    /**
     * IL PASSO del decider (la forma curata): applica lo step a tutti i canali,
     * entra in ENTRAMBI i trip, e solo dopo spedisce la prima valutazione.
     */
    public void runStep() {
        try {
            for (ArrayBlockingQueue<String> ch : channels) {
                ch.put("STEP");
            }
            deciderBarrierJoin("step/1");
            deciderBarrierJoin("step/2");
            // il punto della cura: il dispatch avviene SOLO a trip completi
            channels.get(0).put("EVAL");
            firstDispatchNanos.compareAndSet(0, System.nanoTime());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Ponti pubblici per il test (il decider e' il thread chiamante). */
    public void deciderBarrierJoinPublic(String trip) {
        deciderBarrierJoin(trip);
    }

    /** La regola della morte: il canale 0 riceve il marcatore. */
    public void injectDeathOnRunner0() throws InterruptedException {
        channels.get(0).put("DEATH_AFTER_FIRST_AWAIT");
    }

    /** Il passo che DEVE fallire loud (morte iniettata): cattura l'eccezione. */
    public void runStepExpectingFailure() {
        try {
            for (ArrayBlockingQueue<String> ch : channels) {
                if (ch.peek() == null) {
                    ch.put("STEP");
                }
            }
            deciderBarrierJoin("step/1");
            deciderBarrierJoin("step/2");
        } catch (IllegalStateException e) {
            deciderFailure.set(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Il join del decider (la stessa attesa unica 60s della produzione (semplificata: senza fast-fail ne' gate shutdown)). */
    void deciderBarrierJoin(String trip) {
        try {
            barrier.await(60, TimeUnit.SECONDS);
        } catch (java.util.concurrent.BrokenBarrierException e) {
            throw new IllegalStateException("standin decider barrier (" + trip
                    + ") broken: a runner left the barrier mid-protocol", e);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("standin decider barrier (" + trip
                    + ") timed out - failing loud instead of parking forever", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Fermata ordinata (i runner escono sul DESTROY o sul timeout della poll). */
    public void shutdown() throws InterruptedException {
        for (ArrayBlockingQueue<String> ch : channels) {
            ch.offer("DESTROY");
        }
        for (Thread t : threads) {
            t.join(2000);
        }
    }
}
