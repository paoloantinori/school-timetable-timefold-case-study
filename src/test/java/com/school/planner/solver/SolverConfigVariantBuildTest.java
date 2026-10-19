package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.school.planner.domain.SchoolSchedule;

/**
 * Guard for the diagnostic solver-config variants: each XML on the classpath must
 * build a solver against the CURRENT Timefold version (the per-phase
 * environmentMode element, in particular, is validated when the phase pipeline is
 * instantiated, and a Timefold upgrade that drops it must fail HERE instead of at
 * diagnosis time, mid-run). No solve is started: buildSolver() constructs the
 * full phase tree without a problem instance. The solutionClass is set here the
 * same way the Quarkus extension sets it at boot (the XML stays extension-owned).
 */
class SolverConfigVariantBuildTest {

    private static void assertBuilds(String configResource) {
        SolverConfig config = SolverConfig.createFromXmlResource(configResource);
        config.setSolutionClass(SchoolSchedule.class);
        config.setEntityClassList(java.util.List.of(com.school.planner.domain.Lesson.class));
        Solver<SchoolSchedule> solver = SolverFactory.<SchoolSchedule>create(config).buildSolver();
        assertNotNull(solver, configResource + " must build the full phase pipeline");
    }

    @Test
    void defaultConfigBuilds() {
        assertBuilds("solverConfig.xml");
    }

    /**
     * The FULL_ASSERT-LS diagnostic variant (per-phase environment mode, Timefold
     * 2.7 #2595): if the element moves or gets stricter validation in a future
     * upgrade, this fails before anyone wastes a diagnostic run on it.
     */
    @Test
    void fullAssertLsVariantBuilds() {
        assertBuilds("solverConfigFullAssertLS.xml");
    }
}
