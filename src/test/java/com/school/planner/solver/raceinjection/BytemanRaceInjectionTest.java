package com.school.planner.solver.raceinjection;

import static com.school.planner.solver.raceinjection.RaceInjectionHarness.JOIN_TIMEOUT_MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.school.planner.solver.raceinjection.RaceInjectionHarness.StandInDirtyCell;

/**
 * TASK-543: riproduce DETERMINISTICAMENTE la finestra publish-then-fill della race del
 * SimpleFieldTracker usando Byteman (4.0.27, verificato su JDK 21), e prevalida la cura
 * single-writer nello stesso banco.
 *
 * <p>Gira SOLO con l'agente attivo (profilo maven {@code byteman}):
 * {@code ./scripts/core/test.sh -mva "-Pbyteman" -c BytemanRaceInjectionTest}.
 * Senza agente il test e' disabilitato perche' l'interleaving sarebbe una lotteria dello
 * scheduler (la corruzione accadrebbe a caso, non deterministicamente: e' il motivo per
 * cui la race e' semi-impossibile da catturare live).</p>
 */
@EnabledIfSystemProperty(named = "byteman.agent", matches = "true")
class BytemanRaceInjectionTest {

    /**
     * PREDIZIONE (registrata prima dell'esecuzione): con l'agente che congela lo scrittore
     * dentro beginFill, il lettore legge l'array pubblicato ma NON riempito: osserva null.
     * La corruzione e' deterministica: 1'esecuzione su 1 la mostra.
     */
    @Test
    void forcedInterleavingShowsPartialState() throws Exception {
        StandInDirtyCell cell = new StandInDirtyCell();
        cell.raceMode = true;
        AtomicReference<String> readerSaw = new AtomicReference<>("unset");

        RaceInjectionHarness.runWithFrozenWriter(
                () -> {
                    cell.publish();
                    cell.beginFill(); // Byteman congela qui dentro
                    cell.fill("H17");
                },
                () -> readerSaw.set(cell.readAttribute()), // lettura DENTRO la finestra
                () -> cell.writerFrozen);

        // La lettura e' caduta DENTRO la finestra: stato parziale osservato.
        assertNull(readerSaw.get(),
                "il lettore deve vedere lo stato parziale (null) con l'interleaving forzato"
                        + "; se vale 'unset' la regola Byteman non ha armato la finestra");
        assertTrue(cell.writerFrozen, "la regola deve aver armato la finestra");
        // E lo scrittore, svegliato dal timeout del gate, completa lo stato.
        assertEquals("H17", cell.readAttribute(), "dopo il rilascio lo stato e' completo");
    }

    /**
     * PREVALIDAZIONE SINGLE-WRITER: se il lettore valuta solo a mutazione completata
     * (join prima della lettura, la promessa del modello single-writer), la STESSA
     * orchestrazione non puo' produrre il danno: il lettore vede sempre lo stato pieno.
     * Questo e' il pattern con cui prevalideremo il refactor del decider MT-LS.
     */
    @Test
    void serializedAccessNeverShowsPartialState() throws Exception {
        StandInDirtyCell cell = new StandInDirtyCell();
        cell.raceMode = false; // single-writer: la regola non puo' aprire la finestra

        Thread writer = new Thread(() -> {
            cell.publish();
            cell.beginFill();
            cell.fill("H17");
        }, "writer");

        writer.start();
        writer.join(JOIN_TIMEOUT_MS); // mutazione completata prima della lettura

        String saw = cell.readAttribute();
        assertEquals("H17", saw, "con accesso serializzato lo stato parziale non e' osservabile");
        assertFalse(cell.writerFrozen, "la finestra non si apre senza raceMode");
    }
}
