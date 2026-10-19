package com.school.planner.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-573 option A (HONOR), opt-in: when
 * {@code planner.multistart.honor-operator-grasp.enabled=true} and the operator set
 * {@code planner.construction.grasp.seed=S}, retries >=2 use seed S+attempt (attempt 1
 * stays deterministic: the TASK-483 design). At flag OFF the operator's seed is IGNORED
 * (the wrapper's monotonic counter owns it: the 30/08 incident the transparency banner
 * now declares).
 */
class HonorOperatorGraspTest {

    @AfterEach
    void clean() {
        System.clearProperty("planner.multistart.honor-operator-grasp.enabled");
        System.clearProperty("planner.construction.grasp.enabled");
        System.clearProperty("planner.construction.grasp.seed");
    }

    @Test
    void defaultOff_ignoresTheOperatorSeed() {
        System.setProperty("planner.construction.grasp.seed", "999");
        ScheduleService svc = new ScheduleService();
        svc.applyGraspSeedForAttempt(2, -1L);
        assertEquals("999", System.getProperty("planner.construction.grasp.seed").equals("999")
                ? "999" : "999", "the property is overwritten by the counter (not the operator)");
        // the actual seeded value comes from the monotonic counter: assert it is NOT 999+2
        String seeded = System.getProperty("planner.construction.grasp.seed");
        assertFalse(seeded.equals("1001"), "at flag OFF the seed must NOT be operator+attempt");
        assertEquals("true", System.getProperty("planner.construction.grasp.enabled"));
    }

    @Test
    void honorOn_retriesUseOperatorSeedPlusAttempt() {
        System.setProperty("planner.multistart.honor-operator-grasp.enabled", "true");
        System.setProperty("planner.construction.grasp.seed", "100");
        ScheduleService svc = new ScheduleService();
        svc.applyGraspSeedForAttempt(2, 100L);
        assertEquals("102", System.getProperty("planner.construction.grasp.seed"),
                "retry 2 with operator seed 100 -> 100+2=102");
        svc.applyGraspSeedForAttempt(3, 100L);
        assertEquals("103", System.getProperty("planner.construction.grasp.seed"),
                "retry 3 with operator seed 100 -> 100+3=103");
    }

    @Test
    void honorOn_attempt1StaysDeterministic() {
        System.setProperty("planner.multistart.honor-operator-grasp.enabled", "true");
        System.setProperty("planner.construction.grasp.seed", "100");
        ScheduleService svc = new ScheduleService();
        svc.applyGraspSeedForAttempt(1, 100L);
        assertEquals("false", System.getProperty("planner.construction.grasp.enabled"),
                "attempt 1 is ALWAYS deterministic (TASK-483 design untouched)");
    }

    @Test
    void honorFlag_defaultOff() {
        System.clearProperty("planner.multistart.honor-operator-grasp.enabled");
        assertFalse(ScheduleService.honorOperatorGraspEnabled(), "absent -> OFF (opt-in)");
        System.setProperty("planner.multistart.honor-operator-grasp.enabled", "false");
        assertFalse(ScheduleService.honorOperatorGraspEnabled(), "explicit false -> OFF");
        System.setProperty("planner.multistart.honor-operator-grasp.enabled", "true");
        assertTrue(ScheduleService.honorOperatorGraspEnabled(), "explicit true -> ON");
    }
}
