package com.school.planner.domain;

import java.util.Comparator;

/**
 * Strength comparator for timeslot values in CH Phase 2 value sorting.
 * Earlier timeslots have higher strength (are preferred).
 *
 * Used by {@link TimeslotStrengthComparatorFactory} (via comparatorFactoryClass)
 * on {@code @PlanningVariable} to enable sorterManner=ASCENDING in solverConfig.xml.
 */
public class TimeslotStrengthComparator implements Comparator<Timeslot> {

    @Override
    public int compare(Timeslot a, Timeslot b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;  // nulls last
        if (b == null) return -1;
        return a.compareTo(b);  // Use Timeslot's natural ordering (by startDateTime)
    }
}
