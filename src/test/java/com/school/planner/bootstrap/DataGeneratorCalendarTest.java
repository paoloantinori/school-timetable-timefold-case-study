package com.school.planner.bootstrap;

import java.time.LocalDate;
import java.util.List;

import com.school.planner.solver.TimeslotSequence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the 2026/27 calendar computation (TASK-341).
 * Plain JUnit (no @QuarkusTest) - computeSchoolDays() is a pure function,
 * exercised directly to avoid depending on the disabled-in-tests DataGenerator
 * startup path (planner.datagenerator.enabled=false).
 */
class DataGeneratorCalendarTest {

    private List<LocalDate> schoolDays;

    @BeforeEach
    void setUp() {
        schoolDays = DataGenerator.computeSchoolDays(
                TimeslotSequence.SCHOOL_YEAR_START, TimeslotSequence.SCHOOL_YEAR_END, DataGenerator.CLOSURE_DATES_2026_27);
    }

    @Test
    void schoolYear2026_27_has171SchoolDays() {
        assertEquals(174, schoolDays.size(),
                "2026-09-14..2027-06-10 minus weekends minus the 23 in-range weekday closures "
                        + "must be exactly 174 school days (verified against claudedocs/spalmone-2026-27/data/closures.csv). "
                        + "+ TASK-550 extension (maintainer 25/08 07:50): 174 with the 3 golden recovery days (06-11/14/15);"
                        + " TASK-408 history: 172 -> 171 after the 2026-01-22 -> 2027-01-22 closure typo fix");
    }

    @Test
    void schoolYear2026_27_produces1368Timeslots() {
        int totalTimeslots = schoolDays.size() * DataGenerator.STANDARD_SLOTS.length;

        assertEquals(8, DataGenerator.STANDARD_SLOTS.length, "8 slots/day (6 morning + 2 afternoon)");
        assertEquals(1392, totalTimeslots, "174 school days x 8 slots/day = 1392 timeslots (TASK-550 extension; was 171/1368, TASK-408 172/1376)");
    }

    @Test
    void closureTypoFix_2027_01_22_isNotASchoolDay() {
        // TASK-408: the closure was mistranscribed as 2026-01-22 (out of range, inert);
        // the golden is 100% closed on 2027-01-22 (Friday). After the fix the correct
        // in-range date is excluded from the school-day set.
        LocalDate correctedClosure = LocalDate.of(2027, 1, 22);
        assertEquals(true, DataGenerator.CLOSURE_DATES_2026_27.contains(correctedClosure));
        assertEquals(false, schoolDays.contains(correctedClosure));
    }

    @Test
    void weekendClosuresDoNotAffectCount() {
        // Weekends are already excluded regardless of the closures set; removing
        // the weekend closure dates from the set must not change the count.
        java.util.Set<LocalDate> weekdayOnlyClosures = DataGenerator.CLOSURE_DATES_2026_27.stream()
                .filter(d -> d.getDayOfWeek().getValue() < 6)
                .collect(java.util.stream.Collectors.toSet());
        List<LocalDate> withoutWeekendClosures = DataGenerator.computeSchoolDays(
                TimeslotSequence.SCHOOL_YEAR_START, TimeslotSequence.SCHOOL_YEAR_END, weekdayOnlyClosures);

        assertEquals(schoolDays.size(), withoutWeekendClosures.size());
    }
}
