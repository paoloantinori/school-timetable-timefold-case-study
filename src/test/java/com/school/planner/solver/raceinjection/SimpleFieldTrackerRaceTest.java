package com.school.planner.solver.raceinjection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.bytecode.enhance.internal.tracker.SimpleFieldTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * TASK-544: race-injection deterministica sul {@link SimpleFieldTracker} REALE di Hibernate
 * (la classe esatta del crash del 22/08, vedi
 * claudedocs/research_hibernate-enhancement-mtls-race_2026-08-22.md). La finestra
 * publish-then-fill del bytecode e' documentata nella regola Byteman
 * ({@code src/test/resources/byteman/race-force.btm}, verificata col javap su 7.2.4.Final).
 *
 * <p>Il test gemello serializzato e' la prevalidazione single-writer: senza concorrenza
 * la stessa sequenza non puo' produrre il danno.</p>
 */
@EnabledIfSystemProperty(named = "byteman.agent", matches = "true")
class SimpleFieldTrackerRaceTest {

    @AfterEach
    void resetProbe() {
        TrackerRaceProbe.raceMode = false;
        TrackerRaceProbe.writerFrozen = false;
    }

    /**
     * PREDIZIONE (registrata prima dell'esecuzione): con lo scrittore congelato dopo la
     * pubblicazione dell'array esteso, contains("teacher") dal lettore lancia NPE
     * ("existing is null": la firma esatta del crash del 22/08).
     */
    @Test
    void forcedInterleavingReproducesTrackerNpe() throws Exception {
        SimpleFieldTracker tracker = new SimpleFieldTracker();
        tracker.add("room"); // names = ["room"]: stato iniziale a cella singola
        TrackerRaceProbe.raceMode = true;
        TrackerRaceProbe.writerFrozen = false;
        AtomicReference<Throwable> readerFailure = new AtomicReference<>();

        RaceInjectionHarness.runWithFrozenWriter(
                () -> tracker.add("teacher"),
                () -> {
                    try {
                        tracker.contains("teacher"); // legge l'array con la cella null
                    } catch (Throwable t) {
                        readerFailure.set(t); // atteso: NPE dentro contains
                    }
                },
                () -> TrackerRaceProbe.writerFrozen);

        assertTrue(TrackerRaceProbe.writerFrozen,
                "la regola Byteman deve armare la finestra (altrimenti il test non prova nulla)");
        Throwable failure = readerFailure.get();
        assertNotNull(failure, "contains() deve fallire dentro la finestra forzata");
        assertEquals(NullPointerException.class, failure.getClass(),
                "la firma del crash reale e' NPE (existing is null)");
    }

    /** PREVALIDAZIONE SINGLE-WRITER: sequenza serializzata, stessa regola, nessun danno. */
    @Test
    void serializedAccessNeverFails() {
        TrackerRaceProbe.raceMode = false;
        SimpleFieldTracker tracker = new SimpleFieldTracker();
        tracker.add("room");
        tracker.add("teacher"); // completata prima di qualsiasi lettura
        assertTrue(tracker.contains("teacher"));
        assertFalse(tracker.contains("absent"));
        assertFalse(TrackerRaceProbe.writerFrozen, "la finestra non si apre senza raceMode");
    }
}
