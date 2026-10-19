package com.school.planner.solver.raceinjection;

import java.util.function.BooleanSupplier;

/**
 * TASK-543: banco per l'iniezione deterministica di race-conditions via Byteman.
 *
 * <p>Lo {@code StandInDirtyCell} riproduce la FORMA della race del SimpleFieldTracker
 * Hibernate documentata nella ricerca del 22-23/08/2026 (claudedocs/research_hibernate-enhancement-mtls-race_2026-08-22.md):
 * uno scrittore pubblica una struttura con un valore mancante e la riempie in un secondo tempo
 * ({@code publish -> beginFill -> fill}); un lettore concorrente che legga nella finestra
 * osserva lo stato parziale e produce una valutazione sbagliata.</p>
 *
 * <p>Senza orchestrazione l'interleaving e' una lotteria dello scheduler (semi-impossibile
 * da catturare live). Le regole Byteman in {@code src/test/resources/byteman/race-force.btm}
 * congelano lo scrittore dentro {@link #beginFill()} e sincronizzano il lettore, rendendo la
 * corruzione DETERMINISTICA. Il test gemello {@code serialized} dimostra che valutando solo
 * a mutazione completata (la promessa del single-writer) la stessa regola non puo' produrre
 * il danno: e' il pattern di prevalidazione del fix.</p>
 */
public final class RaceInjectionHarness {

    private RaceInjectionHarness() {
    }

    /** Timeout dei join writer/reader (il freeze Byteman dura 3-5s: margine ampio). */
    public static final long JOIN_TIMEOUT_MS = 30_000;

    /** Bail-out dello spin del reader: se la regola non ha armato la finestra, fallisci chiaro. */
    public static final long SPIN_BAILOUT_MS = 10_000;

    /**
     * Orchestrazione writer/reader condivisa dai banchi race (TASK-543/544): il reader
     * spinna su {@code frozenFlag} (volatile settato dalla regola PRIMA del blocco) con
     * bail-out, poi esegue {@code readerAction} DENTRO la finestra congelata.
     */
    public static void runWithFrozenWriter(Runnable writerAction, Runnable readerAction,
            BooleanSupplier frozenFlag) throws InterruptedException {
        Thread writer = new Thread(writerAction, "race-writer");
        Thread reader = new Thread(() -> {
            long deadline = System.nanoTime() + SPIN_BAILOUT_MS * 1_000_000;
            while (!frozenFlag.getAsBoolean()) {
                if (System.nanoTime() > deadline) {
                    return; // regola non armata: il chiamante fallisce con diagnosi
                }
                Thread.onSpinWait();
            }
            readerAction.run();
        }, "race-reader");
        writer.start();
        reader.start();
        writer.join(JOIN_TIMEOUT_MS);
        reader.join(JOIN_TIMEOUT_MS);
    }

    /** Cell con finestra publish-then-fill: la forma del tracker. */
    public static final class StandInDirtyCell {
        private volatile String[] attributes;

        /**
         *TASK-543: attivato dal test per dichiarare che la cella e' condivisa con un
         * lettore concorrente. Le regole Byteman congelano lo scrittore SOLO se raceMode:
         * nel test serializzato (single-writer) la stessa regola non puo' mordere.
         */
        public volatile boolean raceMode;

        /** Settato dalla regola Byteman quando lo scrittore e' congelato nella finestra. */
        public volatile boolean writerFrozen;

        /** Pubblica l'array NON ancora riempito (stato parziale visibile). */
        public void publish() {
            attributes = new String[1]; // [null]: pubblicato prima del riempimento
        }

        /** Inizio del riempimento: Byteman congela il thread QUI (solo in raceMode). */
        public void beginFill() {
            // punto di iniezione: la finestra e' aperta
        }

        public void fill(String value) {
            attributes[0] = value;
        }

        /** Lettura concorrente: Byteman sincronizza il lettore dentro la finestra. */
        public String readAttribute() {
            String[] snapshot = attributes;
            return snapshot == null ? null : snapshot[0];
        }
    }
}
