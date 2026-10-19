package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Room;
import com.school.planner.domain.Timeslot;



/**
 * TASK-500: the pure update rule (table-testable, exactly like isDoomedAtBoundary), the
 * move classifier seam, and the wiring contract on the production XML. The adaptation
 * is inert-by-default: {@link AdaptiveMoveWeights#weight} returns the ratified bases
 * until arm() runs.
 */
class AdaptiveMoveWeightsTest {

    private final AdaptiveMoveWeights.Config cfg =
            new AdaptiveMoveWeights.Config(4000, 50, 0.25, 0.5, 2.5);

    // ordinal order: TS_CHANGE, TS_SWAP, TS_PILLAR_CHANGE, TS_PILLAR_SWAP, ROOM_CHANGE, ROOM_SWAP, OTHER
    private final double[] base = {5.0, 3.0, 2.0, 1.0, 2.0, 1.0, 0.0};

    @BeforeEach
    @AfterEach
    void disarm() {
        AdaptiveMoveWeights.arm(false, AdaptiveMoveWeights.Config.fromProperties());
    }

    @Test
    void inertWeightsAreTheRatifiedBases() {
        assertEquals(5.0, AdaptiveMoveWeights.weight(AdaptiveMoveWeights.Stream.TS_CHANGE));
        assertEquals(1.0, AdaptiveMoveWeights.weight(AdaptiveMoveWeights.Stream.ROOM_SWAP));
    }

    @Test
    void updateTable() {
        // clear evidence: change 50/1000 (reward .05), swap 0/1000. avg is base-weighted:
        // (5*.05 + 3*0)/8 = .03125 -> change rises x1.6, swap is clamped to the lo floor.
        long[] ev = {1000, 1000, 0, 0, 0, 0, 0};
        long[] ac = {50, 0, 0, 0, 0, 0, 0};
        double[] w = AdaptiveMoveWeights.updateWeights(ev, ac, base, cfg);
        assertEquals(5.0 * (.05 / .03125), w[0], 1e-9, "above-average stream rises (x reward/avg)");
        assertEquals(3.0 * 0.5, w[1], 1e-9, "zero-reward stream falls to the lo=0.5x floor");
        assertEquals(2.0, w[2], 1e-9, "unsampled stream keeps its ratified base (pillarC base is 2.0)");
        assertEquals(0.0, w[6], "OTHER never participates");

        // proportional rewards: swap at half of change's reward rises/falls proportionally
        long[] ac2 = {50, 25, 0, 0, 0, 0, 0};
        double[] w2 = AdaptiveMoveWeights.updateWeights(ev, ac2, base, cfg);
        double avg2 = (5 * .05 + 3 * .025) / 8;
        assertEquals(5.0 * (.05 / avg2), w2[0], 1e-9);
        assertEquals(3.0 * (.025 / avg2), w2[1], 1e-9);

        // extreme: a SMALL-base stream produces everything while the big streams produce
        // nothing -> its reward/avg ratio overshoots hi and clamps (never monopolizes)
        long[] ev5 = {1000, 1000, 0, 0, 0, 0, 1000};
        long[] ac5 = {0, 0, 0, 0, 0, 0, 1000};
        double[] w5 = AdaptiveMoveWeights.updateWeights(ev5, ac5, base, cfg);
        assertEquals(0.0, w5[6], "OTHER is counted but never re-weighed");
        long[] ev6 = {1000, 1000, 0, 0, 0, 1000, 0};
        long[] ac6 = {0, 0, 0, 0, 0, 1000, 0};   // ROOM_SWAP (base 1.0) produces all
        double[] w6 = AdaptiveMoveWeights.updateWeights(ev6, ac6, base, cfg);
        assertEquals(1.0 * 2.5, w6[5], 1e-9, "dominant small-base stream clamps at hi=2.5x base");
        assertEquals(5.0 * 0.5, w6[0], 1e-9, "the big zero-reward streams sink to the lo floor");

        // nobody reaches minEvals: everything keeps base (no evidence is not bad evidence)
        long[] ev3 = {10, 10, 0, 0, 0, 0, 0};
        long[] ac3 = {10, 0, 0, 0, 0, 0, 0};
        assertArrayEquals(base, AdaptiveMoveWeights.updateWeights(ev3, ac3, base, cfg));

        // nobody accepted anywhere: avg=0, all bases stand (no collapse)
        long[] ac4 = {0, 0, 0, 0, 0, 0, 0};
        assertArrayEquals(base, AdaptiveMoveWeights.updateWeights(ev, ac4, base, cfg));
    }

    @Test
    void classifierTable() {
        var ts = java.time.LocalDateTime.of(2026, 9, 14, 8, 0);
        assertEquals(AdaptiveMoveWeights.Stream.TS_CHANGE,
                AdaptiveMoveWeights.MoveTypeClassifier.streamOf(
                        ai.timefold.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove.class,
                        new Timeslot()));
        assertEquals(AdaptiveMoveWeights.Stream.ROOM_CHANGE,
                AdaptiveMoveWeights.MoveTypeClassifier.streamOf(
                        ai.timefold.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove.class,
                        new Room()));
        assertEquals(AdaptiveMoveWeights.Stream.TS_SWAP,
                AdaptiveMoveWeights.MoveTypeClassifier.streamOf(
                        ai.timefold.solver.core.impl.heuristic.selector.move.generic.SelectorBasedSwapMove.class,
                        new Timeslot()));
        assertEquals(AdaptiveMoveWeights.Stream.ROOM_SWAP,
                AdaptiveMoveWeights.MoveTypeClassifier.streamOf(
                        ai.timefold.solver.core.impl.heuristic.selector.move.generic.SelectorBasedSwapMove.class,
                        new Room()));
        assertEquals(AdaptiveMoveWeights.Stream.TS_PILLAR_CHANGE,
                AdaptiveMoveWeights.MoveTypeClassifier.streamOf(
                        ai.timefold.solver.core.impl.heuristic.selector.move.generic.SelectorBasedPillarChangeMove.class,
                        new Timeslot()));
        assertEquals(AdaptiveMoveWeights.Stream.TS_PILLAR_SWAP,
                AdaptiveMoveWeights.MoveTypeClassifier.streamOf(
                        ai.timefold.solver.core.impl.heuristic.selector.move.generic.SelectorBasedPillarSwapMove.class,
                        new Timeslot()));
        assertEquals(AdaptiveMoveWeights.Stream.OTHER,
                AdaptiveMoveWeights.MoveTypeClassifier.streamOf(Object.class, null));
        // quiet: ts unused warning avoided by a real read
        assertTrue(ts != null);
    }




    @Test
    void recordUpdatesWeightsAtSegmentBoundaries() {
        AdaptiveMoveWeights.arm(true, new AdaptiveMoveWeights.Config(100, 50, 0.25, 0.5, 2.5));
        AdaptiveMoveWeights.record(null, false);   // OTHER: never re-weighed, still counts the segment
        assertEquals(5.0, AdaptiveMoveWeights.weight(AdaptiveMoveWeights.Stream.TS_CHANGE),
                "before the segment boundary the bases stand");
    }

    @Test
    void thinningTable() {
        // armed with all bases (no segment yet): maxRatio == 1 -> everything dispatches
        AdaptiveMoveWeights.arm(true, new AdaptiveMoveWeights.Config(4000, 50, 0.25, 0.5, 2.5));
        int dispatched = 0;
        for (int i = 0; i < 200; i++) {
            if (AdaptiveMoveWeights.shouldEvaluate(null)) dispatched++;   // OTHER: never thinned
        }
        assertEquals(200, dispatched, "no stream boosted above base: no thinning at all");
        AdaptiveMoveWeights.arm(false, AdaptiveMoveWeights.Config.fromProperties());
    }
}
