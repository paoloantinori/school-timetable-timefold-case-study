package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.solver.AdaptiveMoveWeights;
import com.school.planner.solver.AdaptiveMoveWeights.Config;
import com.school.planner.solver.AdaptiveMoveWeights.Stream;

/**
 * TASK-504 (SPECULATIVE, DEFAULT OFF): the warm-start primitive's contract, pure.
 * The poison-basin DECISION (which attempt inherits) needs a live solve: it is the
 * wrapper's unit and stays honest here as the documented AC #2 shape, not asserted.
 */
class WarmStartWeightsTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("planner.multistart.warm-start-weights.enabled");
        AdaptiveMoveWeights.arm(false, Config.fromProperties());
    }

    @Test
    void snapshotRefusedWhenInertOrCorrupted() {
        AdaptiveMoveWeights.arm(false, Config.fromProperties());
        assertNull(AdaptiveMoveWeights.snapshotForWarmStart(),
                "an inert registry has nothing learned to inherit");

        AdaptiveMoveWeights.arm(true, new Config(1000, 10, 0.25, 0.5, 2.5));
        double[] snap = AdaptiveMoveWeights.snapshotForWarmStart();
        assertArrayEquals(bases(), snap, "a freshly armed registry snapshots at base");
    }

    @Test
    void seedClampsToTheLearningEnvelope() {
        AdaptiveMoveWeights.arm(true, new Config(1000, 10, 0.25, 0.5, 2.5));
        // an absurd out-of-range snapshot (1000x base): seed must clamp into [LO*base, HI*base]
        double[] absurd = new double[Stream.values().length];
        for (int i = 0; i < absurd.length; i++) {
            absurd[i] = 1_000_000.0;
        }
        assertTrue(AdaptiveMoveWeights.seed(absurd));
        for (Stream s : Stream.values()) {
            double w = AdaptiveMoveWeights.weight(s);
            assertTrue(w <= 2.5 * s.base + 1e-9,
                    "seed can never out-range what segment learning could reach: " + s + "=" + w);
        }
    }

    @Test
    void seedRefusesMalformedSnapshots() {
        AdaptiveMoveWeights.arm(true, new Config(1000, 10, 0.25, 0.5, 2.5));
        double[] before = { AdaptiveMoveWeights.weight(Stream.TS_CHANGE),
                AdaptiveMoveWeights.weight(Stream.TS_SWAP) };
        assertFalse(AdaptiveMoveWeights.seed(null));
        assertFalse(AdaptiveMoveWeights.seed(new double[2]), "wrong length refused");
        double[] bad = bases();
        bad[0] = Double.NaN;
        assertFalse(AdaptiveMoveWeights.seed(bad), "NaN refused");
        assertArrayEquals(before,
                new double[] { AdaptiveMoveWeights.weight(Stream.TS_CHANGE),
                        AdaptiveMoveWeights.weight(Stream.TS_SWAP) },
                "a refused seed touches nothing");
    }

    @Test
    void roundTripSnapshotSeed() {
        AdaptiveMoveWeights.arm(true, new Config(1000, 10, 0.25, 0.5, 2.5));
        double[] snap = AdaptiveMoveWeights.snapshotForWarmStart();
        AdaptiveMoveWeights.arm(true, new Config(1000, 10, 0.25, 0.5, 2.5)); // fresh registry
        assertTrue(AdaptiveMoveWeights.seed(snap));
        assertArrayEquals(snap, AdaptiveMoveWeights.snapshotForWarmStart(),
                "snapshot->seed->snapshot is the identity (within envelope)");
    }

    @Test
    void flagDefaultsToOff() {
        System.clearProperty("planner.multistart.warm-start-weights.enabled");
        assertFalse(ScheduleService.warmStartWeightsEnabled(),
                "TASK-504 is speculative opt-in: unset property reads disabled");
    }

    private static double[] bases() {
        double[] b = new double[Stream.values().length];
        for (Stream s : Stream.values()) {
            b[s.ordinal()] = s.base;
        }
        return b;
    }
}
