package com.school.planner.solver.checkpoint;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TASK-542 stage 1: the serializable checkpoint payload - grid + statics + cursor + score.
 *
 * <p><b>Grid.</b> One {@link LessonPlacement} per lesson (id + timeslotId + roomId + pinned +
 * promoted). Null ids encode "unassigned"/"roomless" exactly like the domain.
 *
 * <p><b>Statics.</b> The ONLY cycle-derived, non-recomputable static is the promoted-pin set
 * ({@code SchoolSchedule.promotedLessonIdsStatic}); it is carried as the {@code promoted} flag
 * of each placement (single source, no duplicate section to drift). Everything else the cycle
 * publishes pre-solve (allTimeslots, calendars, quotas, availability, M32 reference, pool caps,
 * VRP cell counts...) is derived deterministically from the DB inputs by
 * {@code ScheduleService.refreshSolverStatics} and is RE-PUBLISHED at resume, not carried:
 * a checkpoint that shipped recomputable state would go stale the moment an input changes.
 *
 * <p><b>Cursor.</b> {@link StagedCheckpointCursor}: capture boundary (+ iteration for the
 * B-TIMESLOT loop) and the multi-start context (attempt number, GRASP seed) so a resumed
 * segment reproduces the attempt's seeding contract.
 *
 * <p><b>Score + verdict numbers.</b> The score string plus the two gate numbers the cycle
 * reads at the boundary (boxed lessons, timeslot-tier hard), so a resumed run starts from the
 * same decision state without rescoring the grid first.
 */
public record StagedCheckpoint(
        int version,
        StagedCheckpointCursor cursor,
        String score,
        long boxed,
        long timeslotTierHard,
        List<LessonPlacement> placements) {

    public static final int CURRENT_VERSION = 1;

    /** The promoted statics, reconstructed from the grid (single source: the promoted flag). */
    public Set<Long> promotedLessonIds() {
        return placements.stream()
                .filter(LessonPlacement::promoted)
                .map(LessonPlacement::lessonId)
                .collect(Collectors.toSet());
    }
}
