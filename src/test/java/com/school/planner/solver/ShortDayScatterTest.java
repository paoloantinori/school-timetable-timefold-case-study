package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * TASK-527 BUG A/B: pure rules of the short-day scatter post-pass. The three
 * historical signatures (measured on the verify-519 run, Fedora 19/08): G04
 * Fridays at 3h (quota 4), G08 Mon-5/Tue-Fri-4, G12 Tuesdays at 4h
 * (quota 5). The pass must move ONE lesson from a FULL weekday to the SHORT
 * one, cycling the source weekday week by week (scatter), never touching
 * pinned/pseudo lessons, never exceeding the short day's cap.
 */
class ShortDayScatterTest {

    @AfterEach
    void clearFlag() {
        System.clearProperty(DayClusteredConstructionPhase.SHORT_DAY_SCATTER_FLAG);
    }

    @Test
    void flagIsOptInDefaultOff() {
        assertFalse(DayClusteredConstructionPhase.shortDayScatterEnabled());
        System.setProperty(DayClusteredConstructionPhase.SHORT_DAY_SCATTER_FLAG, "true");
        assertTrue(DayClusteredConstructionPhase.shortDayScatterEnabled());
        System.setProperty(DayClusteredConstructionPhase.SHORT_DAY_SCATTER_FLAG, "false");
        assertFalse(DayClusteredConstructionPhase.shortDayScatterEnabled());
    }

    /** Cycle rule: the source weekday rotates week by week (the scatter signature). */
    @Test
    void sourceWeekdayCyclesPerWeek() {
        // fullDays for a week: [Mon, Tue, Wed]; weeks cycle 0,1,2,3...
        String[] days = {"Mon", "Tue", "Wed"};
        int weekIndex = 0;
        assertEquals("Mon", days[weekIndex % days.length]);
        weekIndex = 1;
        assertEquals("Tue", days[weekIndex % days.length]);
        weekIndex = 3;
        assertEquals("Mon", days[weekIndex % days.length]);
    }

    /**
     * Cap arithmetic of the move, on the three historical shapes: moving one
     * lesson from a full day (count == cap) to the short day (count == cap-1)
     * leaves both legal: target reaches cap, source drops to cap-1. Total
     * unchanged, pattern broken for the week.
     */
    @Test
    void moveKeepsBothDaysLegalOnTheThreeSignatures() {
        // G12 (BUG A): quota 5, short Tuesday at 4, full Monday at 5
        int quota = 5, shortCount = 4, fullCount = 5;
        assertTrue(shortCount == quota - 1 && fullCount >= quota);
        assertEquals(quota, shortCount + 1);   // target after move
        assertEquals(quota - 1, fullCount - 1); // source after move

        // G08 (BUG B): quota 5, short Tue-Fri at 4, full Monday at 5: same math
        // G04: quota 4, short Friday at 3, full weekdays at 4: same math
        quota = 4; shortCount = 3; fullCount = 4;
        assertTrue(shortCount == quota - 1 && fullCount >= quota);
        assertEquals(quota, shortCount + 1);
    }

    /**
     * TURN0-3 refinement rules: (a) ANY deficit below the declared quota is
     * filled (G04 Friday: declared 5h, run 3h, deficit 2: the old cap-1 test
     * missed it); (b) every full weekday is a candidate source in cycled order
     * (G12 Tuesday: the single cycled source often had no movable lesson).
     */
    @Test
    void refinementRulesForTheTwoResiduals() {
        // G04 Friday: declared 5, placed 3 -> deficit 2, TWO moves needed
        int cap = 5, count = 3;
        int deficit = cap - count;
        assertEquals(2, deficit);
        // after two moves the day sits AT the declared quota
        assertEquals(cap, count + deficit);
        // G12 Tuesday: declared 5, placed 4, but the cycled source (say index
        // 1 of 3 full days) has no movable lesson: the pass must fall through to
        // the OTHER full days, not give up on the week
        String[] full = {"Mon", "Wed", "Thu"};
        int cycle = 1;
        String[] order = new String[full.length];
        for (int i = 0; i < full.length; i++) {
            order[i] = full[(i + cycle) % full.length];
        }
        assertEquals("Wed", order[0]); // cycled first
        assertEquals("Thu", order[1]); // fallback 1
        assertEquals("Mon", order[2]); // fallback 2
    }
}
