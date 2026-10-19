package com.school.planner.domain;

import java.util.Comparator;

import ai.timefold.solver.core.api.domain.common.ComparatorFactory;

/**
 * Provides a strength comparator for timeslot values in CH Phase 2 value sorting.
 * Earlier timeslots have higher strength (are preferred).
 *
 * <p>Replaces the deprecated {@code strengthComparatorClass} attribute on
 * {@code @PlanningVariable} with the recommended {@code comparatorFactoryClass}.
 *
 * @see TimeslotStrengthComparator
 */
public class TimeslotStrengthComparatorFactory implements ComparatorFactory<SchoolSchedule, Timeslot> {

    private static final TimeslotStrengthComparator DELEGATE = new TimeslotStrengthComparator();

    @Override
    public Comparator<Timeslot> createComparator(SchoolSchedule solution) {
        return DELEGATE;
    }
}
