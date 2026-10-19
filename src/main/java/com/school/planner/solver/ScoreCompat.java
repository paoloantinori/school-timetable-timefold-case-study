package com.school.planner.solver;

import ai.timefold.solver.core.api.score.BendableScore;

/**
 * TASK-538 ramo B, milestone 1 (28/08): the single place that maps the pre-bendable
 * {@code HardMediumSoftScore} vocabulary onto the solution's {@link BendableScore}
 * {@code (2 hard levels, 2 soft levels)} shape. Every score construction and every
 * tier read in the codebase goes through here, so the level indices are declared
 * exactly once:
 *
 * <ul>
 * <li>hard level 0: the pure hard tier (every former {@code ofHard}/ONE_HARD)</li>
 * <li>hard level 1: RESERVED for the quasi-hard tier (TASK-538 milestone 2); written
 * by NO constraint in milestone 1, so it stays constantly zero here</li>
 * <li>soft level 0: the former medium tier ({@code ofMedium}/ONE_MEDIUM)</li>
 * <li>soft level 1: the former soft tier ({@code ofSoft}/ONE_SOFT)</li>
 * </ul>
 *
 * <p>Parity: {@link BendableScore#compareTo} walks all hard levels in order, then all
 * soft levels in order, long-comparing at the first difference. With hard level 1
 * constantly zero the ordering is identical to the old {@code HardMediumSoftScore}
 * (full proof on the {@code SchoolSchedule} score field). Timefold's
 * {@code validateCompatible} rejects mixed shapes loudly at runtime, so every factory
 * here bakes in the {@code (2, 2)} shape; never construct a bendable score ad hoc.
 *
 * <p>Naming mirrors the old API on purpose ({@code ONE_MEDIUM} is the weight that used
 * to be {@code HardMediumSoftScore.ONE_MEDIUM}, {@code ofMedium(w)} the old
 * {@code HardMediumSoftScore.ofMedium(w)}), so constraint-provider call sites keep
 * reading exactly as before the migration.
 */
public final class ScoreCompat {

    /** The solution's bendable shape: 2 hard levels (pure + quasi), 2 soft levels (medium + soft). */
    public static final int HARD_LEVELS = 2;
    public static final int SOFT_LEVELS = 2;

    /** The zero of the (2, 2) shape; the old {@code HardMediumSoftScore.ZERO}. */
    public static final BendableScore ZERO = BendableScore.zero(HARD_LEVELS, SOFT_LEVELS);

    /** One unit of pure hard: hard level 0. The old {@code HardMediumSoftScore.ONE_HARD}. */
    public static final BendableScore ONE_HARD = ofHard(1);

    /** One unit of medium: soft level 0. The old {@code HardMediumSoftScore.ONE_MEDIUM}. */
    public static final BendableScore ONE_MEDIUM = ofMedium(1);

    /** One unit of soft: soft level 1. The old {@code HardMediumSoftScore.ONE_SOFT}. */
    public static final BendableScore ONE_SOFT = ofSoft(1);

    private ScoreCompat() {
    }

    /** Pure-hard weight: hard level 0. */
    public static BendableScore ofHard(long value) {
        return BendableScore.ofHard(HARD_LEVELS, SOFT_LEVELS, 0, value);
    }

    /**
     * Quasi-hard weight: hard level 1 (TASK-538 milestone 2). Constraints scored here
     * are "almost hard": a violation is worse than any medium but better than any
     * pure-hard violation. The LS can traverse these paying an intermediate price
     * instead of hitting an all-or-nothing wall.
     */
    public static BendableScore ofQuasi(long value) {
        return BendableScore.ofHard(HARD_LEVELS, SOFT_LEVELS, 1, value);
    }

    /** The quasi-hard tier of a score: hard level 1. */
    public static long quasiOf(BendableScore score) {
        return score.hardScore(1);
    }

    /** Medium weight: soft level 0. */
    public static BendableScore ofMedium(long value) {
        return BendableScore.ofSoft(HARD_LEVELS, SOFT_LEVELS, 0, value);
    }

    /** Soft weight: soft level 1. */
    public static BendableScore ofSoft(long value) {
        return BendableScore.ofSoft(HARD_LEVELS, SOFT_LEVELS, 1, value);
    }

    /** Positional factory mirroring the old {@code HardMediumSoftScore.of(hard, medium, soft)}. */
    public static BendableScore of(long hard, long medium, long soft) {
        return BendableScore.of(new long[] { hard, 0 }, new long[] { medium, soft });
    }

    /** Positional factory with an explicit quasi level: {@code of(pure, quasi, medium, soft)}. */
    public static BendableScore of(long hard, long quasi, long medium, long soft) {
        return BendableScore.of(new long[] { hard, quasi }, new long[] { medium, soft });
    }

    /** The pure-hard tier of a score: hard level 0. The old {@code score.hardScore()}. */
    public static long hardOf(BendableScore score) {
        return score.hardScore(0);
    }

    /** The medium tier of a score: soft level 0. The old {@code score.mediumScore()}. */
    public static long mediumOf(BendableScore score) {
        return score.softScore(0);
    }

    /** The soft tier of a score: soft level 1. The old {@code score.softScore()}. */
    public static long softOf(BendableScore score) {
        return score.softScore(1);
    }

    /** The zero of {@link HardLevels}: both hard levels clean. */
    public static final HardLevels ZERO_HARD_LEVELS = new HardLevels(0, 0);

    /**
     * The two hard levels of a score as ONE lexicographically comparable pair: pure hard
     * (level 0) first, quasi (level 1) second. Any oracle that decides "did this pass make
     * the schedule worse" must compare exactly like the score itself does. Summing the two
     * levels into a single number would let quasi gains offset pure-hard damage, which the
     * BendableScore ordering forbids: one pure-hard violation outweighs any number of quasi
     * violations, and an oracle allowed to trade them would accept grids the solver itself
     * considers strictly worse.
     */
    public record HardLevels(long pure, long quasi) implements Comparable<HardLevels> {
        @Override
        public int compareTo(HardLevels other) {
            int byPure = Long.compare(pure, other.pure);
            return byPure != 0 ? byPure : Long.compare(quasi, other.quasi);
        }

        @Override
        public String toString() {
            return pure + "hard/" + quasi + "quasi";
        }
    }

    /** The two hard levels of a score as a lexicographic pair (see {@link HardLevels}). */
    public static HardLevels hardLevels(BendableScore score) {
        return new HardLevels(hardOf(score), quasiOf(score));
    }

    /**
     * True iff BOTH hard levels are clean. This is the ONE definition of a "0-hard
     * schedule/attempt": every verdict that gates on hard cleanliness (multi-start
     * early-stop, warm-start, feasibility) must consult this predicate, never re-spell
     * it inline - the codebase previously carried three divergent private definitions.
     */
    public static boolean isFullyHardClean(BendableScore score) {
        return hardOf(score) == 0 && quasiOf(score) == 0;
    }
}
