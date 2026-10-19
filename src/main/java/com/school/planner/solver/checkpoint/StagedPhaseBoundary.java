package com.school.planner.solver.checkpoint;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * TASK-542 stage 1: the census of the staged cycle's phase boundaries, as an ordered enum.
 * Each constant maps 1:1 to a boundary in {@code ScheduleService.runStagedCycle} (the linear
 * ones) or inside its B-TIMESLOT loop (the iterated ones). The constants MUST be declared in
 * execution order: {@link #linearOrder()} is derived from the declaration (minus the iterated
 * pair) and {@link #resumeEntryPoint()} is derived by adjacency, so the declaration IS the
 * census. The declaration's lockstep with {@code runStagedCycle} itself is guarded by
 * {@code StagedCheckpointFormatTest.cycleSourceIsTheBoundaryOracle}, which reads the cycle's
 * source and fails if a boundary-relevant stage call appears out of order or is missing.
 *
 * <p>Census (code order in {@code runStagedCycle}):
 * <ol>
 *   <li>{@link #CYCLE_START} - after {@code loadSchedule(true)}: clean grid + authoritative pins</li>
 *   <li>{@link #POST_CONSTRUCTION} - after {@code runConstructionStage} (stage A's template stamped)</li>
 *   <li>{@link #POST_PROMOTION} - after {@code promoteTemplatePins()}</li>
 *   <li>{@link #POST_B_TIMESLOT} - after each {@code runTimeslotSolveStage()} iteration (n = 1..)</li>
 *   <li>{@link #POST_DEMOTION} - after each {@code runDemotionStage()} iteration (n = 1..2)</li>
 *   <li>{@link #POST_RELEASE} - after {@code releasePromotedPinsAndRepublish(true)} (SMARCATURA)</li>
 *   <li>{@link #POST_HOLE_REPAIR} .. {@link #POST_EXACT_FINISHER} - after each repair pass</li>
 *   <li>{@link #POST_B_ROOM} - after {@code runRoomSolveStage} (the attempt's final grid)</li>
 *   <li>{@link #POST_ROOM_QUOTA_FILL_REPAIR} - after the second QuotaFillRepair chance</li>
 *   <li>{@link #POST_EMPTY_DAY_FINISHER} - after {@code runEmptyDayFinisher} (TASK-560,
 *       flag-gated DEFAULT OFF; boundary exists regardless, like the medium-pure arm)</li>
 *   <li>{@link #POST_SURPLUS_HARVEST} - the first SurplusHarvest chance, alongside the
 *       EmptyDayFinisher at the post-room chain (TASK-577, flag-shaped)</li>
 *   <li>{@link #POST_MEDIUM_PURE_ARM} - after {@code runMediumPureArm} (TASK-551, flag-gated
 *       DEFAULT OFF; the boundary exists regardless - the arm no-ops at default, the cycle
 *       still crosses it - so the census and the capture stay cycle-shaped, not flag-shaped)</li>
 *   <li>{@link #POST_FINAL_SURPLUS_HARVEST} - the third SurplusHarvest chance on the final
 *       grid (TASK-577 v11-p2; flag-shaped like its siblings: the boundary always crosses,
 *       the pass runs only when armed)</li>
 * </ol>
 */
public enum StagedPhaseBoundary {
    // declaration order == execution order of runStagedCycle's non-loop body (see class javadoc)
    CYCLE_START(false),
    POST_CONSTRUCTION(false),
    POST_PROMOTION(false),
    POST_B_TIMESLOT(true),
    POST_DEMOTION(true),
    POST_RELEASE(false),
    POST_HOLE_REPAIR(false),
    POST_LAB_DAY_CONSOLIDATION(false),
    POST_COMPOSITION_HOLE_CLOSER(false),
    POST_ANCHOR_REPAIR(false),
    POST_SURPLUS_REBALANCE(false),
    POST_QUOTA_FILL_REPAIR(false),
    POST_EXACT_FINISHER(false),
    POST_B_ROOM(false),
    POST_ROOM_QUOTA_FILL_REPAIR(false),
    POST_EMPTY_DAY_FINISHER(false),

    /** TASK-577: after {@code SurplusHarvest} (the long-range H34 harvest). */
    POST_SURPLUS_HARVEST(false),
    POST_MEDIUM_PURE_ARM(false),
    /** TASK-577 v11-p2: the THIRD SurplusHarvest chance, at the cycle's tail (after the
     * medium-pure arm, before the closing release). The tracer measured that the
     * delivered run's residual H34 cells (G08 May-June) do NOT exist yet at the
     * post-B-ROOM boundary - they form in the later stages - so the pass gets one
     * more occasion on the final grid. Same improve-or-noop sibling as every chance. */
    POST_FINAL_SURPLUS_HARVEST(false);

    /** Derived once from the declaration: the linear boundaries, minus the iterated pair. */
    private static final List<StagedPhaseBoundary> LINEAR_ORDER =
            Arrays.stream(values()).filter(b -> !b.iterated).toList();

    private final boolean iterated;

    StagedPhaseBoundary(boolean iterated) {
        this.iterated = iterated;
    }

    /** The linear (non-loop) boundaries in execution order; excludes the iterated pair. */
    public static List<StagedPhaseBoundary> linearOrder() {
        return LINEAR_ORDER;
    }

    /** True for the boundaries inside the B-TIMESLOT loop (identified with an iteration number). */
    public boolean isIterated() {
        return iterated;
    }

    /**
     * The boundary the cycle continues with after a capture here - never this boundary itself
     * (re-running a completed stage would double-apply promotion/release semantics). The linear
     * cases are derived by adjacency in {@link #linearOrder()}; the loop pair is special-cased
     * (B-TIMESLOT falls through to C-DEMOTION, C-DEMOTION loops back to B-TIMESLOT - the
     * resume driver decides the actual branch from the grid verdict). Empty for the cycle's
     * last boundary: there is nothing to resume into.
     */
    public Optional<StagedPhaseBoundary> resumeEntryPoint() {
        if (this == POST_B_TIMESLOT) {
            return Optional.of(POST_DEMOTION);
        }
        if (this == POST_DEMOTION) {
            return Optional.of(POST_B_TIMESLOT);
        }
        List<StagedPhaseBoundary> order = linearOrder();
        int next = order.indexOf(this) + 1;
        return next < order.size() ? Optional.of(order.get(next)) : Optional.empty();
    }
}
