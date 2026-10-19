package com.school.planner.domain;

import java.util.Comparator;

import ai.timefold.solver.core.api.domain.common.ComparatorFactory;

/**
 * Provides a stable, transitive comparator for Lesson entities based on pre-computed
 * available timeslot counts. Used wherever Timefold sorts entities by difficulty, today
 * the room construction heuristic (phase 4, {@code sorterManner=DESCENDING}); it was
 * introduced for the internal CH of the RuinRecreate local search (removed in TASK-420).
 *
 * <p>A construction heuristic sorts ALL entities to determine placement order, including
 * entities whose variables are currently null. This comparator uses only static data
 * (pre-computed available timeslot counts + entity IDs), making it independent of current
 * variable state and guaranteeing transitive comparisons.
 *
 * <p>Entity ID is used as a tie-breaker to guarantee unique ordering, which trivially
 * satisfies TimSort's transitivity requirement.
 */
public class LessonDifficultyWeightFactory implements ComparatorFactory<SchoolSchedule, Lesson> {

    @Override
    public Comparator<Lesson> createComparator(SchoolSchedule solution) {
        // Snapshot ONCE per sort (TASK-378): per-compare static reads can see
        // an API-triggered map replacement mid-sort, breaking transitivity and
        // crashing TimSort (this killed runs 3/4 at the room-CH phase start).
        final java.util.Map<Long, Integer> counts = SchoolSchedule.getLessonAvailableTimeslotCountsRef();
        return (a, b) -> {
            Integer aCount = counts == null ? null : counts.get(a.id);
            Integer bCount = counts == null ? null : counts.get(b.id);
            int aVal = (aCount != null) ? aCount : Integer.MAX_VALUE;
            int bVal = (bCount != null) ? bCount : Integer.MAX_VALUE;
            if (aVal != bVal) {
                return Integer.compare(aVal, bVal);
            }
            // Tie-break by entity ID for guaranteed uniqueness → transitivity
            return Long.compare(a.id != null ? a.id : 0L, b.id != null ? b.id : 0L);
        };
    }
}
