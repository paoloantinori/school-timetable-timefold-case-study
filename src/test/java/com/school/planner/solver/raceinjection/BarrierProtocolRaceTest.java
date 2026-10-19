package com.school.planner.solver.raceinjection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * TASK-639: regression test deterministica del protocollo barrier MT-LS (il
 * bug 2.6.0: il decider spediva valutazioni mentre un runner era fra la prima
 * e la seconda await). Le regole Byteman iniettano le finestre sullo
 * {@link BarrierProtocolStandIn}, che rispecchia la coreografia di produzione
 * (decider parte N+1, trip espliciti, dispatch solo a trip completi).
 *
 * <p>Gira SOLO con l'agente ({@code ./scripts/core/test.sh -mva "-Pbyteman" -c
 * BarrierProtocolRaceTest}); senza agente e' disabilitato perche' le finestre
 * sarebbero una lotteria dello scheduler.</p>
 */
@EnabledIfSystemProperty(named = "byteman.agent", matches = "true")
class BarrierProtocolRaceTest {

    /**
     * PREDIZIONE (registrata prima dell'esecuzione): con un runner la cui
     * seconda await e' congelata 8s (la finestra che il cadence 2.6.0 ha
     * esposto), la cura impone che la PRIMA valutazione del decider parta
     * DOPO che l'ultimo runner ha completato la seconda await. Il protocollo
     * vecchio (dispatch a "canali vuoti") avrebbe sparato durante la finestra:
     * questo test fallisce se qualcuno torna alla sincronizzazione a timing.
     */
    @Test
    void slowSecondAwaitDoesNotBreakTheDispatchInvariant() throws Exception {
        BarrierProtocolStandIn standIn = new BarrierProtocolStandIn(4);
        standIn.raceMode = true;
        standIn.start();
        // setup trip anche per il decider
        standIn.deciderBarrierJoinPublic("setup");
        try {
            standIn.runStep();
            long dispatch = standIn.firstDispatchNanos.get();
            assertTrue(standIn.secondAwaitDelayed,
                    "la regola Byteman deve aver armato il ritardo"
                            + " (secondAwaitDelayed false = finestra mai aperta)");
            assertNotEquals(0L, dispatch, "il decider deve aver spedito la valutazione");
            // NOTA: i timestamp di COMPLETAMENTO dei runner non sono leggibili
            // subito dopo runStep (il decider torna dal trip appena arriva
            // l'ultima parte, i runner stampano nel loro seguito, schedulato
            // dopo): solo il confronto strutturale qui sotto e' deterministico.
            // confronto strutturale (immune alla micro-race del risveglio
            // simultaneo): il ritardo di 8s sul runner 0 deve avere CESSELLATO
            // il dispatch: almeno 7s fra l'ingresso del runner 0 nella seconda
            // await e la prima valutazione. Il protocollo a timing avrebbe
            // sparato dentro la finestra (sub-secondo).
            long enter = standIn.runner0SecondAwaitEnterNanos.get();
            assertNotEquals(0L, enter, "il runner 0 deve essere entrato nella seconda await");
            assertTrue(dispatch - enter >= 7_000_000_000L,
                    "INVARIANTE ROTTO: dispatch a " + ((dispatch - enter) / 1_000_000)
                            + "ms dall'ingresso del runner 0 nella seconda await:"
                            + " il ritardo non ha gating = protocollo tornato a timing");
        } finally {
            standIn.shutdown();
        }
    }

    /**
     * PREDIZIONE: un runner che MUORE fra la prima e la seconda await (la
     * regola inietta l'eccezione) deve produrre un fallimento LOUD e BOUNDED
     * del join del decider via TIMEOUT del trip (il runner morto non puo'
     * spezzare una CyclicBarrier morendo TRA i trip: il trip 2 resta senza
     * una parte e scatta il bound 60s), non un parcheggio: la semantica
     * TASK-541 preservata dalla cura.
     */
    @Test
    void runnerDeathMidStepFailsLoudAndBounded() throws Exception {
        BarrierProtocolStandIn standIn = new BarrierProtocolStandIn(3);
        standIn.raceMode = true;
        standIn.start();
        standIn.deciderBarrierJoinPublic("setup");
        try {
            // inietta la morte: il canale 0 porta il marcatore speciale
            standIn.injectDeathOnRunner0();
            long t0 = System.currentTimeMillis();
            standIn.runStepExpectingFailure();
            long wall = System.currentTimeMillis() - t0;
            assertNotNull(standIn.deciderFailure.get(),
                    "il decider deve fallire loud (BrokenBarrier), non parcheggiare");
            assertTrue(wall < 70_000,
                    "il fallimento deve essere bounded (wall " + wall + "ms)");
            assertEquals(true, standIn.deathInjected,
                    "la regola deve aver armato la morte (deathInjected false)");
        } finally {
            standIn.shutdown();
        }
    }
}
