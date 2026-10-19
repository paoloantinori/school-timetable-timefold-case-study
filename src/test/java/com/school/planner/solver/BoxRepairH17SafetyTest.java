package com.school.planner.solver;

import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-401: unit tests for {@link BoxRepair}'s H17 gate, pinned around the exact
 * counterexample the review's skeptic constructed against the first (adjacency-based)
 * version of the gate: H17's break-level pinned exemption (task-350) means a gap
 * bounded by two PINNED lessons scores 0, so dropping an unpinned lesson INSIDE it
 * flips the break from exempt to penalized — a width-3 both-pinned gap turns H0 +1
 * into H17 -2, a net hard regression the gate must refuse.
 */
class BoxRepairH17SafetyTest {

    private static TreeMap<Integer, Boolean> day(Object... slotPinnedPairs) {
        TreeMap<Integer, Boolean> m = new TreeMap<>();
        for (int i = 0; i < slotPinnedPairs.length; i += 2) {
            m.put((Integer) slotPinnedPairs[i], (Boolean) slotPinnedPairs[i + 1]);
        }
        return m;
    }

    @Test
    void penaltyMirrorsH17BreakLevelPinnedExemption() {
        assertEquals(0, BoxRepair.h17DayPenalty(day()), "empty day");
        assertEquals(0, BoxRepair.h17DayPenalty(day(0, false, 1, false, 2, false)), "contiguous run");
        assertEquals(2, BoxRepair.h17DayPenalty(day(0, false, 3, false)), "unpinned-bounded gap costs its width");
        assertEquals(0, BoxRepair.h17DayPenalty(day(0, true, 4, true)), "both-pinned gap is exempt (task-350)");
        assertEquals(3, BoxRepair.h17DayPenalty(day(0, true, 4, false)), "one-side-pinned gap is penalized");
    }

    @Test
    void refusesFillingABothPinnedExemptGap() {
        // The skeptic's counterexample: pinned at 0 and 4, empty 1-3 (H17 = 0). Placing the
        // unpinned lesson at slot 1 would create the penalized break 1->4 (width 2): refuse.
        assertFalse(BoxRepair.isH17Safe(day(0, true, 4, true), 1));
        assertFalse(BoxRepair.isH17Safe(day(0, true, 4, true), 2));
        assertFalse(BoxRepair.isH17Safe(day(0, true, 4, true), 3));
        // Extending OUTSIDE the exempt gap adds no break: allowed.
        assertTrue(BoxRepair.isH17Safe(day(0, true, 4, true), 5));
    }

    @Test
    void allowsPenaltyNeutralOrImprovingPlacements() {
        assertTrue(BoxRepair.isH17Safe(day(), 3), "empty day is always safe");
        assertTrue(BoxRepair.isH17Safe(day(2, false, 3, false), 1), "run-start extension");
        assertTrue(BoxRepair.isH17Safe(day(2, false, 3, false), 4), "run-end extension");
        assertTrue(BoxRepair.isH17Safe(day(0, false, 2, false), 1), "filling an unpinned-bounded hole improves");
        assertFalse(BoxRepair.isH17Safe(day(0, false, 1, false), 3), "a detached cell opens a new break: refuse");
        // Width-2 both-pinned gap: filling one slot converts exempt 0 into two adjacent
        // pairs — penalty stays 0 only if the filled slot bridges both sides completely.
        assertTrue(BoxRepair.isH17Safe(day(0, true, 2, true), 1), "fully bridging a width-1 exempt gap is neutral");
    }

    // ── TASK-414: the departure gate (eviction's new safety check). The mirror of
    //    isH17Safe for a REMOVAL: evicting an occupant is safe iff removing its slot
    //    does not grow the day's H17 penalty. Edge lessons shrink the run (safe); a
    //    mid-run lesson opens a new penalized break (refused); emptying a day is safe. ──

    @Test
    void departureOfAnEdgeLessonIsSafe() {
        // Contiguous run [0,1,2]: removing either GLOBAL edge (min or max slot) shrinks
        // the run, opening no new break.
        assertTrue(BoxRepair.isH17SafeDeparture(day(0, false, 1, false, 2, false), 2), "remove the global-max slot");
        assertTrue(BoxRepair.isH17SafeDeparture(day(0, false, 1, false, 2, false), 0), "remove the global-min slot");
        // A global-max removal is safe even when an exempt both-pinned gap sits elsewhere
        // in the day: that gap is untouched, only the trailing slot is shed.
        assertTrue(BoxRepair.isH17SafeDeparture(day(0, true, 4, true, 5, false), 5),
                "remove the global-max slot with a both-pinned gap elsewhere in the day");
    }

    @Test
    void departureOfAMiddleLessonIsRefused() {
        // Removing slot 1 from [0,1,2] opens the width-1 break 0->2 (both unpinned): refuse.
        assertFalse(BoxRepair.isH17SafeDeparture(day(0, false, 1, false, 2, false), 1), "mid-run removal opens a penalized hole");
        // Removing the middle of a wider penalized gap grows it: [0,_,2] (penalty 1) -> [0,_,_,2] wait
        // here removing slot 2 from [0,1,2,4] merges into a width-2 break: penalty grows 1 -> 2: refuse.
        assertFalse(BoxRepair.isH17SafeDeparture(day(0, false, 1, false, 2, false, 4, false), 2), "removing a lesson that widens an existing gap");
    }

    @Test
    void departureEmptyingTheDayIsSafe() {
        assertTrue(BoxRepair.isH17SafeDeparture(day(0, false), 0), "removing the only lesson empties the day");
        assertTrue(BoxRepair.isH17SafeDeparture(day(3, true), 3), "removing the only (pinned) lesson empties the day");
    }

    @Test
    void departureLeavingABothPinnedGapIsAllowed() {
        // Unpinned O between two PINNED lessons: before has two penalized breaks (0->2, 2->4,
        // each width 1) = penalty 2; after removing slot 2 the single both-pinned break 0->4
        // is task-350 exempt = penalty 0. Non-increasing, so allowed, that residual gap is
        // H17-legal, never a hard violation, and the eviction's job is to clear H0, not to
        // compact the historical pinned record.
        assertTrue(BoxRepair.isH17SafeDeparture(day(0, true, 2, false, 4, true), 2),
                "removing an unpinned lesson whose neighbors are both pinned leaves an exempt (H17-legal) gap");
    }
}
