package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.config.solver.SolverConfig;

/**
 * TASK-499 wiring: the loud-banner contract. wireNearbyTemplateFilter COUNTS the
 * change-move selectors it arms on the production XML's timeslot phases: a solverConfig
 * refactor that moved the timeslot moves elsewhere must surface as "0 wired" (the
 * banner), not as a silent no-op. Same PRODUCTION_XML loading shape as the neighboring
 * config tests.
 */
class NearbyTemplateWiringTest {

    private static final File PRODUCTION_XML = new File("src/main/resources/solverConfig.xml");

    @Test
    void wiringReachesTheTimeslotChangeSelectors() {
        SolverConfig config = ScheduleService.timeslotPhases(
                SolverConfig.createFromXmlFile(PRODUCTION_XML));
        int wired = ScheduleService.wireNearbyTemplateFilter(config);
        assertTrue(wired >= 1,
                "the production timeslot phase must expose change-move selectors, wired=" + wired);
    }
}
