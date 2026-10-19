package com.school.planner.domain;

import java.util.Comparator;

/**
 * Determines planning difficulty for lessons using MRV (Minimum Remaining Values).
 * Lessons with fewer available timeslots are harder to place and scheduled first
 * by FIRST_FIT_DECREASING.
 *
 * The available timeslot count subsumes external-teacher and professionalizzanti
 * signals (T28 has ~30 timeslots vs ~200+ for internal teachers; stage-blackout
 * groups have fewer timeslots), so no extra proxy levels are needed.
 *
 * Difficulty cascade (ascending — last = hardest = placed first):
 * 1. Pinned lessons last (already placed, trivially easy)
 * 2. Available timeslot count ascending (fewer = harder, MRV heuristic)
 * 3. Required room tags descending (more tags = fewer compatible rooms)
 */
public class LessonDifficultyComparator implements Comparator<Lesson> {

    // Snapshot at construction (TASK-378): same mid-sort-replacement hazard as
    // LessonDifficultyWeightFactory - see its comment. MUST be instantiated
    // fresh per sort (a cached instance would pin a stale snapshot and
    // reintroduce the transitivity crash); the sole caller
    // (DayClusteredConstructionPhase) news it right before sorting.
    private final java.util.Map<Long, Integer> counts =
            SchoolSchedule.getLessonAvailableTimeslotCountsRef();

    @Override
    public int compare(Lesson a, Lesson b) {
        // Level 1: Pinned lessons are easiest — put last
        if (a.pinned != b.pinned) {
            return a.pinned ? 1 : -1;
        }

        // Level 2: MRV — fewer available timeslots = harder to place = schedule first
        Integer aCount = counts == null ? null : counts.get(a.id);
        Integer bCount = counts == null ? null : counts.get(b.id);

        // Treat null as Integer.MAX_VALUE (uncomputed = assume easiest, sort last)
        // This preserves transitivity: null is always >= any real count
        int aVal = (aCount != null) ? aCount : Integer.MAX_VALUE;
        int bVal = (bCount != null) ? bCount : Integer.MAX_VALUE;
        int cmp = Integer.compare(aVal, bVal);
        if (cmp != 0) {
            return cmp;
        }

        // Level 3: More required room tags = fewer compatible rooms = harder
        Course ac = a.course;
        Course bc = b.course;
        int aTagCount = (ac != null && ac.requiredRoomTags != null) ? ac.requiredRoomTags.size() : 0;
        int bTagCount = (bc != null && bc.requiredRoomTags != null) ? bc.requiredRoomTags.size() : 0;
        return Integer.compare(aTagCount, bTagCount);
    }
}
