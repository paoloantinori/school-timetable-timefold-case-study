package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * TASK-502 follow-up (round 3-BOXED, forma a): the ANNUAL teacher-budget guard's pure
 * rule. The rule mirrors the greedy's daily teacherCap but at year scale: a lab pin is
 * declined when the teacher's NON-lab pool on the same group would no longer fit in the
 * weekday slots the lab does not consume. The round-3 numbers are the calibration
 * fixture in the javadoc below.
 */
class TeacherBudgetGuardTest {

    @AfterEach
    void clearFlag() {
        System.clearProperty(HybridPhase1aService.TEACHER_BUDGET_FLAG);
    }

    private static Map<DayOfWeek, Integer> week(int mon, int tue, int wed, int thu, int fri) {
        Map<DayOfWeek, Integer> m = new EnumMap<>(DayOfWeek.class);
        m.put(DayOfWeek.MONDAY, mon);
        m.put(DayOfWeek.TUESDAY, tue);
        m.put(DayOfWeek.WEDNESDAY, wed);
        m.put(DayOfWeek.THURSDAY, thu);
        m.put(DayOfWeek.FRIDAY, fri);
        return m;
    }

    @Test
    void flagOffIsAlwaysAllow() {
        System.clearProperty(HybridPhase1aService.TEACHER_BUDGET_FLAG);
        // the round-3 T15 shape: huge lab pool, tiny free week, big non-lab pool
        assertFalse(HybridPhase1aService.teacherBudgetEnabled());
        assertTrue(HybridPhase1aService.teacherBudgetAllowsPin(436, 0, 50, 0,
                week(8, 8, 8, 8, 8)),
                "flag off: today's behavior, the pin goes through");
    }

    @Test
    void round3T15ShapeIsDeclined() {
        System.setProperty(HybridPhase1aService.TEACHER_BUDGET_FLAG, "true");
        // 436 lab hours to pin; the group's free capacity is ~4 slots on Wed/Thu only
        // (the other weekdays are stage/full): 8 slots/week x 34 weeks = 272 < 436+50.
        assertFalse(HybridPhase1aService.teacherBudgetAllowsPin(436, 0, 50, 0,
                week(0, 0, 4, 4, 0)),
                "pinning the lab pool strands the 50h non-lab: declined (the 20 boxed)");
    }

    /**
     * ROUND 4-PREVIEW follow-up: the INTERSECTION form with the run4-MEASURED numbers.
     * The caller passes min(group spare, teacher residual) per weekday. With the faithful
     * run4 measurements (G04 spare Mon 107 / Tue 92 / Wed 140 / Thu 46 / Fri 144;
     * T15 residual after his pins concentrated on Wed/Thu) the intersection still
     * sums to ~345 >= 50: the per-weekday intersection ALSO does not decline the case.
     * That is the honest pinned outcome: NO aggregate capacity test (annual in round 4,
     * per-weekday here) captures the blocker, which lives at exact-placement level.
     */
    @Test
    void intersectionWithRun4MeasuredNumbersIsAllowedHonestResult() {
        System.setProperty(HybridPhase1aService.TEACHER_BUDGET_FLAG, "true");
        // measured G04 weekday spare (annual slots) and the teacher's residual after
        // his lab pins landed on Wed(2h)/Thu(170h) with 6/day cap elsewhere committed
        boolean allowed = HybridPhase1aService.teacherBudgetAllowsPin(172, 0, 50, 0,
                week(107, 92, 0, 2, 144));
        // Wed/Thu residuals are ~0 after his own pins: the intersection is Mon+Tue+Fri
        assertTrue(allowed,
                "run4-faithful numbers: per-weekday intersection (107+92+0+2+144=345) still"
                        + " hosts the 50h RA: the aggregate family is REFUTED as the cure");
    }

    @Test
    void intersectionTightTeacherIsDeclined() {
        System.setProperty(HybridPhase1aService.TEACHER_BUDGET_FLAG, "true");
        // the rule CAN decline: group spare exists (100/weekday) but the teacher has
        // residual only on Monday (10 slots): 10 < 50 non-lab
        assertFalse(HybridPhase1aService.teacherBudgetAllowsPin(60, 0, 50, 0,
                week(10, 0, 0, 0, 0)));
    }

    @Test
    void teacherWithMarginIsAllowed() {
        System.setProperty(HybridPhase1aService.TEACHER_BUDGET_FLAG, "true");
        // intersection semantics: the caller passes the annual per-weekday minima. A pair
        // with ~100 intersection slots on each of 5 weekdays (500 total) hosts a 120h
        // non-lab pool with margin, whatever its lab pool.
        assertTrue(HybridPhase1aService.teacherBudgetAllowsPin(100, 0, 120, 0,
                week(100, 100, 100, 100, 100)));
    }

    @Test
    void groupWithoutNonLabIsAlwaysAllowed() {
        System.setProperty(HybridPhase1aService.TEACHER_BUDGET_FLAG, "true");
        // no non-lab pool: nothing to protect, however extreme the lab pool
        assertTrue(HybridPhase1aService.teacherBudgetAllowsPin(900, 0, 0, 0,
                week(1, 1, 1, 1, 1)));
    }

    @Test
    void alreadyPinnedHoursCount() {
        System.setProperty(HybridPhase1aService.TEACHER_BUDGET_FLAG, "true");
        // the caller folds the already-pinned hours into the per-weekday minima it
        // passes (the residual shrinks): 120 annual intersection slots host a 100h
        // non-lab pool, 40 do not.
        assertTrue(HybridPhase1aService.teacherBudgetAllowsPin(60, 0, 100, 0,
                week(120, 0, 0, 0, 0)));
        assertFalse(HybridPhase1aService.teacherBudgetAllowsPin(60, 0, 100, 0,
                week(40, 0, 0, 0, 0)));
    }
}
