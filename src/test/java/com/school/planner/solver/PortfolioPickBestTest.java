package com.school.planner.solver;

import java.util.List;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.domain.SchoolSchedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link PortfolioPickBest}: the deterministic core of the B-TIMESLOT-1 portfolio
 * (DRAFT-005). Plain JUnit (no {@code @QuarkusTest}) so it runs without booting Quarkus and without
 * touching the running production app; it only exercises {@link SchoolSchedule}'s score field.
 */
class PortfolioPickBestTest {

    private static SchoolSchedule sched(int hard, int medium, int soft) {
        SchoolSchedule schedule = new SchoolSchedule();
        schedule.setScore(ScoreCompat.of(hard, medium, soft));
        return schedule;
    }

    @Test
    void picksHigherHardScore() {
        SchoolSchedule worse = sched(-20, -1000, 0);
        SchoolSchedule better = sched(-10, -5000, 0);
        // -10 hard beats -20 hard even though its medium is worse: hard is the primary key.
        assertSame(better, PortfolioPickBest.pickBest(List.of(worse, better)));
    }

    @Test
    void tiebreaksOnMediumWhenHardEqual() {
        SchoolSchedule worse = sched(-10, -5000, 100);
        SchoolSchedule better = sched(-10, -1000, 0);
        assertSame(better, PortfolioPickBest.pickBest(List.of(worse, better)));
    }

    @Test
    void tiebreaksOnSoftWhenHardAndMediumEqual() {
        SchoolSchedule worse = sched(-10, -1000, 0);
        SchoolSchedule better = sched(-10, -1000, 50);
        assertSame(better, PortfolioPickBest.pickBest(List.of(worse, better)));
    }

    @Test
    void unsolvedRanksLast() {
        SchoolSchedule unsolved = new SchoolSchedule(); // null score
        SchoolSchedule solved = sched(-50, -9000, 0);
        // An unsolved schedule never wins over a scored one, however bad the score.
        assertSame(solved, PortfolioPickBest.pickBest(List.of(unsolved, solved)));
    }

    @Test
    void allUnsolvedReturnsAFallbackNotNull() {
        SchoolSchedule a = new SchoolSchedule();
        SchoolSchedule b = new SchoolSchedule();
        // No winner to declare, but the portfolio must hand back a schedule, not a null grid.
        assertEquals(a, PortfolioPickBest.pickBest(List.of(a, b)));
    }

    @Test
    void nullAndEmptyInputReturnNull() {
        assertNull(PortfolioPickBest.pickBest(null));
        assertNull(PortfolioPickBest.pickBest(List.of()));
    }
}
