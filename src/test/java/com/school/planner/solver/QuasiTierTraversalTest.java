package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.api.score.BendableScore;

/**
 * TASK-566 (milestone-2 quasi-hard): proves that a quasi-hard violation on
 * hard[1] does NOT block the comparison the way a pure-hard violation on
 * hard[0] does. A solution with quasi violations should be preferred over one
 * with pure-hard violations, but less preferred than one with none.
 */
class QuasiTierTraversalTest {

    private static final int HARD = 0;
    private static final int QUASI = 1;

    @Test
    void quasiViolationIsWorseThanNoViolationButBetterThanPureHard() {
        // Clean: no violations
        BendableScore clean = ScoreCompat.of(0, -500, 0);

        // Quasi violation: 3 empty cells (H34 on hard[1])
        BendableScore withQuasi = BendableScore.of(
                new long[] { 0, -3 },  // hard[0]=0 pure, hard[1]=-3 quasi
                new long[] { -500, 0 });

        // Pure hard violation: 1 conflict
        BendableScore withPureHard = ScoreCompat.of(-1, -500, 0);

        // Clean beats quasi (quasi level is compared before soft)
        assertTrue(clean.compareTo(withQuasi) > 0,
                "clean must beat quasi-violating solution");

        // Quasi beats pure-hard (hard[0] compared first: 0 > -1)
        assertTrue(withQuasi.compareTo(withPureHard) > 0,
                "quasi violation must beat pure-hard violation (this is the traversal property)");

        // And the reverse: pure-hard loses to quasi
        assertTrue(withPureHard.compareTo(withQuasi) < 0,
                "pure-hard must lose to quasi");
    }

    @Test
    void manyQuasiStillBetterThanOnePureHard() {
        // Even 100 quasi violations don't outweigh 1 pure-hard
        BendableScore manyQuasi = BendableScore.of(
                new long[] { 0, -100 },
                new long[] { 0, 0 });
        BendableScore oneHard = BendableScore.of(
                new long[] { -1, 0 },
                new long[] { 0, 0 });

        assertTrue(oneHard.compareTo(manyQuasi) < 0,
                "1 pure-hard must beat 100 quasi (lexicographic: hard[0] first)");
    }

    @Test
    void quasiAccessor() {
        BendableScore score = BendableScore.of(
                new long[] { -5, -7 },
                new long[] { -100, -200 });
        assertEquals(-5, ScoreCompat.hardOf(score), "hard[0]");
        assertEquals(-7, ScoreCompat.quasiOf(score), "hard[1]");
        assertEquals(-100, ScoreCompat.mediumOf(score), "soft[0]");
        assertEquals(-200, ScoreCompat.softOf(score), "soft[1]");
    }
}
