package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.solver.AdaptiveMoveWeights;

/**
 * TASK-500 forma B wiring: arming the lever touches NO Timefold selector config (the
 * OOM'd forma-A selector rewiring is retired). What arming does: reset the registry
 * (statistics + weights) when the MT-LS statistics source exists, and nothing otherwise.
 */
class AdaptiveMoveWeightsWiringTest {

    @AfterEach
    void disarm() {
        AdaptiveMoveWeights.arm(false, AdaptiveMoveWeights.Config.fromProperties());
    }

    @Test
    void flagDefaultsToOnSinceTheRatification() {
        assertTrue(ScheduleService.adaptiveWeightsEnabled(),
                "TASK-500 opt-OUT since the ratification (2026-08-16): unset property reads ENABLED");
        System.setProperty("planner.ls.adaptive-weights.enabled", "false");
        assertFalse(ScheduleService.adaptiveWeightsEnabled(),
                "only the explicit literal false opts out");
    }

    @Test
    void armingResetsTheRegistryToTheRatifiedBases() {
        AdaptiveMoveWeights.arm(true, AdaptiveMoveWeights.Config.fromProperties());
        assertEquals(5.0, AdaptiveMoveWeights.weight(AdaptiveMoveWeights.Stream.TS_CHANGE));
        assertEquals(1.0, AdaptiveMoveWeights.weight(AdaptiveMoveWeights.Stream.ROOM_SWAP));
        assertEquals(2.0, AdaptiveMoveWeights.weight(AdaptiveMoveWeights.Stream.TS_PILLAR_CHANGE),
                "pillar streams keep their declared base (outside the learning)");
        AdaptiveMoveWeights.arm(false, AdaptiveMoveWeights.Config.fromProperties());
        assertEquals(5.0, AdaptiveMoveWeights.weight(AdaptiveMoveWeights.Stream.TS_CHANGE),
                "disarm restores the bases");
    }

    @Test
    void inertRegistryDispatchesEverything() {
        AdaptiveMoveWeights.arm(false, AdaptiveMoveWeights.Config.fromProperties());
        assertTrue(AdaptiveMoveWeights.shouldEvaluate(null),
                "inert (never armed or disarmed): every move is dispatched");
    }
}
