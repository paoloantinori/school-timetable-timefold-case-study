package com.school.planner.solver;

import java.util.Collection;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.domain.SchoolSchedule;

/**
 * The deterministic core of the B-TIMESLOT-1 portfolio multi-start (DRAFT-005): given N
 * independently-solved schedules, pick the winner. Isolated from the orchestration (distinct
 * seeds, executor) so it is unit-testable without Quarkus or a live solve.
 *
 * <p>Ranking follows the campaign's verdict order: higher {@link BendableScore} wins, which
 * orders by hard first, then medium, then soft. The B-TIMESLOT-1 stage runs before B-ROOM assigns
 * rooms, so the score the LS converges to here is the timeslot-grid quality the portfolio is meant
 * to maximize. A schedule with a null score (a solve that produced nothing) ranks last. The
 * adoption gate (flag default OFF, laptop A/B x2) lives at the orchestration site in
 * {@code ScheduleService.runTimeslotSolveStage} and in {@code portfolio-draft-005.md}.
 */
public final class PortfolioPickBest {

    private PortfolioPickBest() {
    }

    /**
     * @return the highest-scoring schedule in {@code candidates}. Ties keep the first found.
     *     Returns {@code null} only for null/empty input; if every candidate is unsolved (null
     *     score) the first non-null candidate is returned as a non-null fallback, so the caller
     *     can always hand back a grid.
     */
    public static SchoolSchedule pickBest(Collection<SchoolSchedule> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        SchoolSchedule best = null;
        BendableScore bestScore = null;
        for (SchoolSchedule candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            BendableScore score = (BendableScore) candidate.getScore();
            if (score == null) {
                // Unsolved ranks last; if we have no scored winner yet, keep it only as a fallback.
                if (best == null) {
                    best = candidate;
                }
                continue;
            }
            if (bestScore == null || score.compareTo(bestScore) > 0) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}
