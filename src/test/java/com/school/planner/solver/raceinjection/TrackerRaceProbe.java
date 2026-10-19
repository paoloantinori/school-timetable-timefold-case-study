package com.school.planner.solver.raceinjection;

/**
 * TASK-544: sonda per la race-injection sul {@code SimpleFieldTracker} REALE di Hibernate
 * (org.hibernate.bytecode.enhance.internal.tracker, versione 7.2.4.Final del progetto).
 *
 * <p>Le regole Byteman in {@code race-force.btm} congelano il thread scrittore dentro la
 * finestra publish-then-fill del tracker (dopo il {@code putfield names} che pubblica
 * l'array esteso con ultima cella null, prima dell'{@code aastore} che la riempie) SOLO
 * quando {@link #raceMode} e' true: senza flag le regole non scattano e gli altri test
 * del profilo byteman non pagano nulla.</p>
 */
public final class TrackerRaceProbe {

    private TrackerRaceProbe() {
    }

    /** Attivato dal test: le regole tracker scattano solo se true. */
    public static volatile boolean raceMode;

    /** Settato dalla regola quando lo scrittore e' congelato nella finestra. */
    public static volatile boolean writerFrozen;
}
