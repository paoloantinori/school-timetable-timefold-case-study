package com.school.planner.solver;

import java.time.Instant;
import java.util.function.Supplier;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.domain.SchoolSchedule;

/**
 * The deterministic core of the staged-cycle multi-start retry (TASK-483): run up to N staged
 * cycles, keep the best attempt (two-level ranking, below), stop early at 0-hard. Isolated from {@code ScheduleService} (the
 * real cycle, the GRASP-seed plumbing, the DB) so it is unit-testable without Quarkus or a live
 * solve, mirroring {@link PortfolioPickBest}'s separation.
 *
 * <p>The caller supplies the cycle as an {@link AttemptRunner} (in production: set the GRASP seed,
 * run {@code ScheduleService.runStagedCycle}, read back the solved grid) and receives the
 * {@link Result}, whose {@link Result#restoreNeeded()} tells it whether the best attempt is NOT the
 * last one the DB now holds, so the best must be re-saved. Ranking is two-level (TASK-498
 * corner-b fix, 21/08): PRIMARY the attempt's timeslot-tier hard at the post-RELEASE boundary -
 * the only axis comparable across attempts, because a doomed not-last attempt ships CH-only
 * rooms whose room-tier noise swamps its full score, so a full-score ranking in an all-doomed
 * run picks the last attempt by construction; TIE-BREAK the full {@link
 * BendableScore#compareTo} (hard asc, then medium, then soft; milestone 1 parity, see
 * ScoreCompat), the campaign's verdict
 * order, among equally-tiered attempts. Reproduction history: backlog TASK-536 note, 21/08.
 *
 * <p>This class always multi-starts: the decision to use it at all (the "flag OFF -> run a single
 * cycle exactly as today" guarantee) lives in the caller's gate, not here, so there is no second
 * enabled-flag to keep in sync.
 */
public final class MultiStartRunner {

    /**
     * Default cap on attempts when the multi-start retry is enabled but no explicit cap is set.
     * Raised from 3 to 5 (maintainer, 2026-08-16) now that source=input is the DEFAULT: the
     * single-construction GRASP census (n=19, p(0-hard)=0.684) showed N=5 gives CI-robust
     * coverage (CI_lo 0.954) for the input path's bad-seed tail. Zero cost on the good
     * basin (stop-at-first-0-hard, measured by filone D: same wall as N=3 when attempt 1
     * succeeds). Override with {@code -Dplanner.multistart.max-attempts=N}.
     */
    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    private MultiStartRunner() {
    }

    /**
     * Multi-start configuration. {@code budgetSeconds <= 0} means "no time budget, only
     * maxAttempts". {@code maxDiscards <= 0} means "no reseed cap" (the historical
     * behaviour, kept available on the knob); a positive value caps how many
     * profile-predictor discards the reseed loop may burn before the cycle ends on
     * the best attempt so far (TASK-538 finding: with budget 0 the reseed loop is
     * otherwise unbounded - 20 discards observed on 29/08, 13 on 22/08 - because a
     * discard consumes neither an attempt nor the null deadline).
     */
    public record Config(int maxAttempts, long budgetSeconds, int maxDiscards) {
        public Config(int maxAttempts, long budgetSeconds) {
            this(maxAttempts, budgetSeconds, 0);
        }

        /** The effective attempt cap, floored at 1 so a misconfigured 0 still runs one attempt. */
        public int effectiveMaxAttempts() {
            return Math.max(1, maxAttempts);
        }
    }

    /**
     * One attempt's materialized outcome: its 1-based number, the grid it produced (null if it
     * failed/cancelled) and the timeslot-tier hard at the attempt's post-RELEASE boundary (the
     * comparable ranking axis, see the class javadoc; {@link Long#MIN_VALUE} when the attempt
     * never reached the boundary, ranking it last among solved attempts).
     */
    /**
     * TASK-542 iter-17-bis: {@code profileDiscarded} is true when the attempt was DISCARDED
     * by the profile predictor before any LS ran - the wrapper must not count it against
     * maxAttempts (measured 22/08 notte: 13 discards burned 13 of 15 attempts in seconds,
     * ending the run 2.5h under budget with only 2 real cycles). Since TASK-677 (04/10)
     * the predictor is DISARMED by default: this path is reachable only via the explicit
     * opt-in re-arm.
     */
    public record AttemptOutcome(int attempt, SchoolSchedule solution, long timeslotTierHard,
            boolean profileDiscarded) {

        public AttemptOutcome(int attempt, SchoolSchedule solution, long timeslotTierHard) {
            this(attempt, solution, timeslotTierHard, false);
        }
    }

    /**
     * The loop's outcome: the best {@link AttemptOutcome} seen and how many attempts actually ran.
     * {@link #restoreNeeded()} is true when the best is not the most recent attempt, so the caller
     * must re-save {@code best.solution()} to the DB (the last attempt's grid is what the DB holds).
     */
    public record Result(AttemptOutcome best, int attemptsRun) {
        public boolean restoreNeeded() {
            return best != null && best.attempt() != attemptsRun;
        }
    }

    /** Runs one staged cycle for the given 1-based attempt number and returns its outcome (or {@code null}). */
    @FunctionalInterface
    public interface AttemptRunner {
        AttemptOutcome run(int attempt);
    }

    /** Receives {@code (attempt, maxAttempts)} per attempt so the UI can show "tentativo X di Y". */
    @FunctionalInterface
    public interface AttemptStateListener {
        void onAttempt(int attempt, int maxAttempts);
    }

    /**
     * Pure orchestration. The {@code clock} is injected so the budget path is deterministic under test.
     * Never throws on a runner failure: a null outcome simply ranks last and the loop continues until
     * the cap, so a single botched attempt does not forfeit the multi-start.
     */
    public static Result run(Config cfg, AttemptRunner runner, AttemptStateListener state, Supplier<Instant> clock) {
        int maxAttempts = cfg.effectiveMaxAttempts();
        Instant deadline = cfg.budgetSeconds() > 0
                ? clock.get().plusSeconds(cfg.budgetSeconds())
                : null;

        AttemptOutcome best = null;
        BendableScore bestScore = null;
        int attempt = 0;
        int discards = 0;
        while (attempt < maxAttempts) {
            attempt++;
            state.onAttempt(attempt, maxAttempts);
            AttemptOutcome outcome = runner.run(attempt);
            if (outcome != null && outcome.profileDiscarded()) {
                // reseed: the discard consumed no LS budget, so it must not consume an
                // attempt either - the remaining bounds are the reseed deadline
                // (budget), maxAttempts re-counted on REAL cycles below, and the
                // explicit discard cap (Config.maxDiscards): with budget 0 the loop
                // would otherwise spin forever on seeds the predictor keeps rejecting.
                attempt--;
                discards++;
                if (cfg.maxDiscards() > 0 && discards >= cfg.maxDiscards()) {
                    break; // reseed cap: deliver the best attempt so far.
                }
                // A discard is NOT a candidate: it never enters the ranking (round-5
                // bug caught by the now-reachable cap test: the latest-wins tie-break
                // let the LAST DISCARD outrank the real attempt, because discards
                // carry scores too). Accounted above, then skipped entirely.
                if (deadline != null && !clock.get().isBefore(deadline)) {
                    break; // advisory budget still applies on discard iterations.
                }
                continue;
            }
            BendableScore score = scoreOf(outcome);
            if (score != null) {
                // Two-level ranking: boundary tier first (the comparable axis, class javadoc),
                // full score only among equals. ">=" (not ">") on the tie-break: on a full tie
                // keep the LATEST attempt, whose grid is already the one the DB holds, so a tie
                // never triggers a pointless restore. This diverges from PortfolioPickBest's
                // first-wins deliberately: there the list is static, here the latest attempt is
                // the cheapest to keep. A strictly-better outcome still wins outright.
                if (bestScore == null) {
                    bestScore = score;
                    best = outcome;
                } else {
                    int cmp = Long.compare(outcome.timeslotTierHard(), best.timeslotTierHard());
                    if (cmp > 0 || (cmp == 0 && score.compareTo(bestScore) >= 0)) {
                        bestScore = score;
                        best = outcome;
                    }
                }
                if (ScoreCompat.isFullyHardClean(score)) {
                    // 0-hard is the goal: stop as soon as a clean schedule appears. "Clean"
                    // means BOTH hard levels (see ScoreCompat.isFullyHardClean): with the
                    // quasi-tier flag ON (H34/H32 on hard level 1) a pure-hard-only check
                    // would stop at the first attempt that reached hard[0]=0 while still
                    // carrying quasi cells, and no later attempt could ever arbitrate them.
                    break;
                }
            } else if (best == null && outcome != null) {
                best = outcome; // unsolved fallback, like PortfolioPickBest: never hand back a null grid.
            }
            if (deadline != null && !clock.get().isBefore(deadline)) {
                break; // advisory budget exhausted after this attempt: do not start the next.
            }
        }
        return new Result(best, attempt);
    }

    private static BendableScore scoreOf(AttemptOutcome outcome) {
        if (outcome == null || outcome.solution() == null) {
            return null;
        }
        return (BendableScore) outcome.solution().getScore();
    }
}
