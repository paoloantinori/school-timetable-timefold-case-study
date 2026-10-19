package com.school.planner.solver.neighborhood;

import java.util.List;

import ai.timefold.solver.core.impl.heuristic.selector.move.factory.MoveListFactory;

import com.school.planner.domain.SchoolSchedule;

/**
 * TASK-589: the gated harvest moves as a stream INSIDE phase 2's local search
 * union. The diagnosis that motivates it: the LS never samples the long-range
 * jump (TASK-577: the strictly-improving 16/09 -> 9/06 move, ~15Mx the budget
 * for the concatenated move), so the deterministic pass is the only thing that
 * closes H34 cells, and only after the LS is done. This stream hands the SAME
 * gated pairs to the LS mid-flight: score acceptance (medium included: the
 * pass's oracle is hard-only), tabu protection and the adaptive per-stream
 * weights (TASK-500) come for free.
 *
 * <p><b>Flag</b>: {@code planner.ls.harvest-stream.enabled}, DEFAULT OFF (same
 * opt-in form as the pass's own flag: null/absent -> off, only "true" arms).
 * When off the factory yields an EMPTY list: the union child is inert and the
 * solver behaves exactly as before (no config branching needed).
 *
 * <p>cacheType is STEP (the MoveListFactory default): the gated pairs are few
 * (the gate work is the point), so the per-step regeneration is the pass's own
 * enumeration cost, not the "scales badly" of an ungated list.
 */
public final class HarvestMoveListFactory implements MoveListFactory<SchoolSchedule> {

    public static final String FLAG = "planner.ls.harvest-stream.enabled";

    static boolean enabled() {
        String v = System.getProperty(FLAG);
        return v != null && Boolean.parseBoolean(v);
    }

    @Override
    public List<HarvestMove> createMoveList(SchoolSchedule schedule) {
        if (!enabled()) {
            return List.of();
        }
        return HarvestNeighborhood.gatedDonorLandings(schedule).stream()
                .map(pair -> new HarvestMove(pair.lesson(), pair.landing()))
                .toList();
    }
}
