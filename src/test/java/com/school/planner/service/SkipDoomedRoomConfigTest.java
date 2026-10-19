package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;

/**
 * TASK-498: the doomed-attempt lever, both halves.
 * <ol>
 * <li>{@code roomPhasesWithoutRefinement} removes ONLY the local-search phases from the
 *     room tail: the room construction heuristic (every lesson still gets a room, so the
 *     attempt's score stays comparable in the multi-start pick-best) and the
 *     phase-transition markers must survive. Verified against the REAL production
 *     solverConfig.xml (same loading path {@code StagedSolvePinLifecycleTest} uses), so
 *     an XML reordering that broke the assumption fails here first.</li>
 * <li>{@code isDoomedAtBoundary}, the pure three-gate verdict: the decision table
 *     (single-start inert, last attempt inert, clean attempt not doomed, condemned
 *     attempt doomed) and the flag default.</li>
 * </ol>
 */
class SkipDoomedRoomConfigTest {

    private static final File PRODUCTION_XML = new File("src/main/resources/solverConfig.xml");

    @AfterEach
    void clearFlag() {
        System.clearProperty("planner.construction.skip-doomed-room.enabled");
        System.clearProperty("planner.staged.hard-accept-threshold");
    }

    @Test
    void removesOnlyTheRoomRefinementLocalSearch() {
        SolverConfig room = ScheduleService.roomPhases(SolverConfig.createFromXmlFile(PRODUCTION_XML));
        int lsBefore = count(room, LocalSearchPhaseConfig.class);
        int chBefore = count(room, ConstructionHeuristicPhaseConfig.class);
        assertTrue(lsBefore >= 1, "the room tail must carry the refinement LS (production XML)");
        assertEquals(1, chBefore, "the room tail must carry exactly one room CH");

        SolverConfig pruned = ScheduleService.roomPhasesWithoutRefinement(room);

        assertEquals(0, count(pruned, LocalSearchPhaseConfig.class),
                "the doomed attempt skips the refinement LS");
        assertEquals(1, count(pruned, ConstructionHeuristicPhaseConfig.class),
                "the room CH stays: every lesson still gets a room, pick-best stays comparable");
        assertTrue(count(pruned, CustomPhaseConfig.class) >= 1,
                "phase-transition markers survive (phase bookkeeping)");
    }

    @Test
    void verdictTable() {
        // Single-start (the wrapper never sets the narration fields): never doomed,
        // whatever the hard - the room refinement is part of what the user asked for.
        // Deep hard so the structural guard is the ONLY thing making these false.
        assertFalse(ScheduleService.isDoomedAtBoundary(-2890, 0, 0), "single-start: full room stage");
        // Last attempt of a multi-start: its grid may be the delivered schedule.
        assertFalse(ScheduleService.isDoomedAtBoundary(-2890, 3, 3), "last attempt: full room stage");
        // A mid-loop attempt the repairs closed: not doomed, refinement runs.
        assertFalse(ScheduleService.isDoomedAtBoundary(0, 1, 3), "clean attempt: refine normally");
        // TASK-635 recalibration (04/10): the whole -6..-38 band TASK-498 measured as
        // "bad" no longer condemns (evidence: stagedHardAcceptThreshold() javadoc).
        assertFalse(ScheduleService.isDoomedAtBoundary(-2, 1, 3),
                "baseline-noise residual: inside the band, full room stage");
        assertFalse(ScheduleService.isDoomedAtBoundary(-38, 1, 3),
                "TASK-498's own bad-band ceiling: chain-curable, full room stage");
        assertFalse(ScheduleService.isDoomedAtBoundary(
                ScheduleService.DEFAULT_STAGED_HARD_ACCEPT_THRESHOLD, 1, 3),
                "at the threshold edge: inclusive, not doomed");
        // Beyond every measured curable point: still condemned.
        assertTrue(ScheduleService.isDoomedAtBoundary(-41, 1, 3),
                "below the recalibrated threshold: skip the refinement that cannot save it");
        assertTrue(ScheduleService.isDoomedAtBoundary(-2890, 2, 3),
                "unmeasured territory below the band");
    }

    @Test
    void thresholdKnobDefaultsToTheRatifiedValueAndRestoresTheLegacyDoom() {
        assertEquals(ScheduleService.DEFAULT_STAGED_HARD_ACCEPT_THRESHOLD,
                ScheduleService.stagedHardAcceptThreshold(),
                "unset property reads the ratified default");
        System.setProperty("planner.staged.hard-accept-threshold", "-2");
        assertEquals(-2L, ScheduleService.stagedHardAcceptThreshold(),
                "the -D override is read LIVE, no restart of the statics");
        assertTrue(ScheduleService.isDoomedAtBoundary(-6, 1, 3),
                "with the legacy -2 the -6 attempt is condemned again (rollback form)");
    }

    @Test
    void flagDefaultsToOnSinceTheRatification() {
        assertTrue(ScheduleService.skipDoomedRoomEnabled(),
                "TASK-497-style opt-OUT since the ratification (2026-08-15): unset property reads ENABLED");
        System.setProperty("planner.construction.skip-doomed-room.enabled", "false");
        assertFalse(ScheduleService.skipDoomedRoomEnabled(),
                "only the explicit literal false opts out");
    }

    private static int count(SolverConfig config, Class<? extends PhaseConfig> kind) {
        return (int) config.getPhaseConfigList().stream().filter(kind::isInstance).count();
    }
}
