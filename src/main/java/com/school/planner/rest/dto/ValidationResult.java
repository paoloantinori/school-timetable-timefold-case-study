package com.school.planner.rest.dto;

import java.time.Instant;
import java.util.List;

/**
 * Result of a solution-legality validation run (TASK-354) — a re-score of the
 * current schedule WITHOUT solving, plus the diff of newly-illegal constraints
 * versus the previous snapshot. The {@code quasi} fields mirror /analysis
 * (TASK-572 point 2): at quasi-tier flag OFF they are constantly 0.
 *
 * @param baseline true when this run had no previous snapshot to diff against (fresh
 *                 state, or the first real run after an app restart with an empty DB).
 *                 {@code newlyViolated} is always empty on a baseline run — there is
 *                 nothing to compare it to, so pre-existing matches must never be
 *                 reported as "newly" violated (code review B1).
 */
public record ValidationResult(
        Instant timestamp,
        String trigger,
        String score,
        long hard,
        long quasi,
        long medium,
        long soft,
        List<ConstraintCount> perConstraint,
        List<ViolationDelta> newlyViolated,
        boolean baseline
) {
    /** Match count and score contribution for a single constraint. */
    public record ConstraintCount(String constraintId, long matchCount, long hard, long quasi, long medium, long soft) {
    }

    /**
     * A constraint whose matchCount increased versus the previous snapshot.
     *
     * @param hard the constraint's hard-score contribution (from the matching
     *             {@link ConstraintCount#hard()}), carried here so consumers (e.g. the
     *             frontend badge) don't need to re-join against {@code perConstraint}
     *             just to tell hard violations from medium/soft ones.
     */
    public record ViolationDelta(String constraintId, long previousCount, long currentCount, long delta, long hard) {
    }
}
