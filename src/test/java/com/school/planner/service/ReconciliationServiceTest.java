package com.school.planner.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-unit tests for {@link ReconciliationService#computeStatus} - the
 * delta/status logic behind the "Classi" reconciliation dashboard
 * (TASK-353). No Quarkus/DB context needed; see ReconciliationResourceTest
 * for the fixture-backed integration test of the full endpoint.
 */
class ReconciliationServiceTest {

    @Test
    void okWhenAssignedEqualsScheduled() {
        assertEquals("OK", ReconciliationService.computeStatus(66, 66));
    }

    @Test
    void okWhenBothZero() {
        assertEquals("OK", ReconciliationService.computeStatus(0, 0));
    }

    @Test
    void unscheduledWhenAssignedButNothingPlaced() {
        // T13 Orientamento, G12: 10 assigned, 0 scheduled.
        assertEquals("UNSCHEDULED", ReconciliationService.computeStatus(10, 0));
    }

    @Test
    void mismatchWhenPartiallyScheduled() {
        // G08 Stage: 300 assigned, 276 scheduled - a shortfall, not a total miss.
        assertEquals("MISMATCH", ReconciliationService.computeStatus(300, 276));
    }

    @Test
    void mismatchWhenOverScheduled() {
        assertEquals("MISMATCH", ReconciliationService.computeStatus(40, 55));
    }

    @Test
    void mismatchWhenAssignedZeroButSomehowScheduled() {
        // Defensive: assigned=0 but scheduled>0 is still a mismatch, not OK or UNSCHEDULED.
        assertEquals("MISMATCH", ReconciliationService.computeStatus(0, 5));
    }

    @Test
    void isIssueTrueForRealNonOkRow() {
        assertTrue(ReconciliationService.isIssue("MISMATCH", false));
        assertTrue(ReconciliationService.isIssue("UNSCHEDULED", false));
    }

    @Test
    void isIssueFalseForPseudoRowRegardlessOfStatus() {
        // Pseudo (Stage/Esame/RA) rows carry expected deltas by design - never an issue.
        assertFalse(ReconciliationService.isIssue("MISMATCH", true));
        assertFalse(ReconciliationService.isIssue("UNSCHEDULED", true));
    }

    @Test
    void isIssueFalseForOkRow() {
        assertFalse(ReconciliationService.isIssue("OK", false));
    }
}
