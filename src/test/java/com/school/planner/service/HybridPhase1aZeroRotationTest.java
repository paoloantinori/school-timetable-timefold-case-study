package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.school.planner.domain.LabRotation;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TASK-367: an empty {@code LabRotation} table (the 2026/27 seed - rotations
 * are deferred to TASK-364, see {@code DataGenerator.generateLabRotations()})
 * must no longer make {@link HybridPhase1aService#run()} report status ERROR.
 * It should complete as a clean, informative no-op: zero candidates, zero
 * pinned, zero failures.
 */
@QuarkusTest
class HybridPhase1aZeroRotationTest {

    @org.junit.jupiter.api.AfterEach
    void clearDeriverFlag() {
        // The deriver's opt-in flag is system-global: a previous test that armed it
        // would otherwise make THIS test's empty-rotation premise false.
        System.clearProperty(LabRotationDeriver.FLAG);
    }


    @Inject
    HybridPhase1aService hybridPhase1aService;

    @BeforeEach
    @Transactional
    void resetState() {
        hybridPhase1aService.resetStatus();
        // Other test classes (e.g. GreedyRotationAssignerTest) persist LabRotation
        // rows that outlive their own test methods (no rollback), so explicitly
        // clear the table rather than assuming it starts empty.
        LabRotation.deleteAll();
        // TASK-435 (ratified 2026-07-30): constrained-teacher is default ON, so run() would execute
        // Phase 0 (constrained-teacher) even with zero rotations, which changes the no-op message
        // this test asserts. Opt it out here to isolate the LabRotation-zero no-op path; @AfterEach
        // restores the production default-on so nothing leaks to sibling test classes.
        System.setProperty(HybridPhase1aService.CONSTRAINED_TEACHER_FLAG, "false");
    }

    @AfterEach
    void restoreConstrainedTeacherDefault() {
        System.clearProperty(HybridPhase1aService.CONSTRAINED_TEACHER_FLAG);
    }

    @Test
    @Transactional
    void run_withNoLabRotations_completesAsNoOp() {
        assertEquals(0, LabRotation.count(), "LabRotation should be empty after the @BeforeEach cleanup");

        HybridPhase1aReport report = hybridPhase1aService.run();

        assertTrue(report.success, "zero-rotation run must be reported as success, not a failure");
        assertEquals("COMPLETE", report.phase);
        assertEquals(0, report.totalCandidatesTried);
        assertEquals(0, report.phaseResults.stream().mapToInt(r -> r.entitiesPinned).sum());
        assertTrue(report.phaseResults.isEmpty(), "no phase should have executed");
        assertEquals(HybridPhase1aService.HybridStatus.COMPLETE, hybridPhase1aService.getStatus());
        // currentPhase only ever holds a phase code ("1-ACCONCIATURA") or null - the
        // informative message lives in the dedicated completionNote field instead.
        assertNull(hybridPhase1aService.getCurrentPhase());
        assertEquals("no rotations configured - nothing to pre-assign", hybridPhase1aService.getCompletionNote());
    }

    @Test
    void constrainedTeacherMaxWeekdays_defaultOne_flagOverridesToTwo() {
        // TASK-451/Q22: the constrained-teacher weekday threshold is LIVE-configurable. Default 1
        // (only single-weekday teachers like T01); setting
        // planner.pre-assign.constrained-teacher.max-weekdays=2 includes 2-weekday Q22 teachers
        // (T19 lun+mar) via the SAME Phase-0 mechanism (no new allocator). The full T19-pinning
        // checkpoint is the full-solve A/B (deferred, blocked on the school's Q22 answer): the L2
        // real-seed fixture cannot pin Barberia (no requiredRoomTags, so findRoomForLesson finds no
        // room and every T19 pin fails canPinHardConstraints), so the robust contract test lives
        // here at the threshold gate (the 1-line mechanism this task adds).
        System.clearProperty(HybridPhase1aService.CONSTRAINED_TEACHER_MAX_WEEKDAYS_FLAG);
        try {
            assertEquals(1, HybridPhase1aService.constrainedTeacherMaxWeekdays(),
                    "default threshold is 1 (only single-weekday teachers like T01; T19 2-weekday excluded)");
            System.setProperty(HybridPhase1aService.CONSTRAINED_TEACHER_MAX_WEEKDAYS_FLAG, "2");
            assertEquals(2, HybridPhase1aService.constrainedTeacherMaxWeekdays(),
                    "the flag raises the threshold to 2 (T19 lun+mar now in scope)");
        } finally {
            // clear even if an assertEquals throws, so the =2 override cannot leak to sibling tests
            System.clearProperty(HybridPhase1aService.CONSTRAINED_TEACHER_MAX_WEEKDAYS_FLAG);
        }
    }
}
