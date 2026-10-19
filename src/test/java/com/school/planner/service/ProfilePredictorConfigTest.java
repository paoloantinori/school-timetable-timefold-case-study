package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.school.planner.service.ScheduleService;

/**
 * Config contract since TASK-677 (04/10): the early-doom predictor is DISARMED -
 * default OFF, only the explicit literal true re-arms it (research form); the
 * threshold stays at the historical 150 because the metric is retired, not
 * recalibrated (TASK-624: non-monotone per seed). The falsification evidence lives
 * on {@code ScheduleService.profilePredictorEnabled()}.
 */
class ProfilePredictorConfigTest {

    @org.junit.jupiter.api.AfterEach
    void clearFlag() {
        System.clearProperty("planner.multistart.profile-predictor.enabled");
    }

    @Test
    void predictorIsDefaultOffSinceThe677Disarm() {
        System.clearProperty("planner.multistart.profile-predictor.enabled");
        assertFalse(ScheduleService.profilePredictorEnabled(),
                "DISARMED 04/10: unset property must NOT discard GRASP retries");
        System.setProperty("planner.multistart.profile-predictor.enabled", "true");
        assertTrue(ScheduleService.profilePredictorEnabled(),
                "only the explicit literal true re-arms it (research form)");
        System.clearProperty("planner.multistart.profile-predictor.enabled");
    }

    @Test
    void thresholdKeptAtTheHistorical150ForOptInResearchArms() {
        assertEquals(150, ScheduleService.PROFILE_DOOM_HOLES_THRESHOLD);
    }
    /**
     * TASK-542 iter-17-ter (hot bug 23/08 02:30): after a profile-discard the wrapper reruns
     * the SAME attempt number - the GRASP seed must still CHANGE, else the reseed redraws the
     * identical doomed construction forever (~9s loop observed). The seed derives from a
     * monotonic attempt-START counter, so two consecutive applications of the same attempt
     * number must produce two different seeds.
     */
    @Test
    void reseedAfterDiscardProducesAFreshSeed() {
        var svc = new com.school.planner.service.ScheduleService();
        System.clearProperty("planner.multistart.test.force-grasp-seed");
        svc.applyGraspSeedForAttempt(2, -1L); // mech: TASK-573 arity (honor off = -1)
        String seed1 = System.getProperty("planner.construction.grasp.seed");
        svc.applyGraspSeedForAttempt(2, -1L); // mech: TASK-573 arity (honor off = -1) // same attempt number: the discard-reseed case
        String seed2 = System.getProperty("planner.construction.grasp.seed");
        org.junit.jupiter.api.Assertions.assertNotEquals(seed1, seed2,
                "the reseed after a discard must draw a DIFFERENT seed (hot bug 23/08)");
        System.clearProperty("planner.construction.grasp.enabled");
        System.clearProperty("planner.construction.grasp.seed");
    }
}
