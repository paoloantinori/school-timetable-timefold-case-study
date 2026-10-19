package com.school.planner.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TASK-394: the ideal end date must come from the school's own per-date plan
 * (DailyActivityQuota expected-hours facts) where facts exist — cumulate the plan's
 * hours until they cover the group's course demand — and fall back to the legacy
 * flat fill-rate model only for groups with no facts. The legacy model credited
 * min(8, maxHoursPerDay) to EVERY school day, which for 8h-cap groups computed the
 * target ~2 months before the school's own plan (G06: 31/03 vs the plan's 10/06,
 * 272h of structural "overflow" that was never lateness).
 */
@QuarkusTest
class TargetEndDateCalculatorFactBasedTest {

    @Inject TargetEndDateCalculator calculator;
    @Inject EntityManager em;

    // 2 weeks of Mon-Fri school days starting Monday 2026-09-14, 8 slots each.
    private static final LocalDate START = LocalDate.of(2026, 9, 14);

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM DailyActivityQuota").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM RecurringAvailabilityRule").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StagePattern").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
    }

    private List<Timeslot> seedTwoWeeksOfTimeslots() {
        for (int week = 0; week < 2; week++) {
            for (int d = 0; d < 5; d++) {
                LocalDate date = START.plusWeeks(week).plusDays(d);
                for (int s = 0; s < 8; s++) {
                    new Timeslot(LocalDateTime.of(date, LocalTime.of(8 + s, 0)),
                            LocalDateTime.of(date, LocalTime.of(9 + s, 0))).persist();
                }
            }
        }
        return Timeslot.listAll();
    }

    @Test
    @Transactional
    void factBasedGroupGetsThePlanDateNotTheFillRateDate() {
        List<Timeslot> timeslots = seedTwoWeeksOfTimeslots();
        StudentGroup group = new StudentGroup("TEST FACTS", 20);
        group.maxHoursPerDay = 8;
        group.persist();
        Teacher t = new Teacher("Prof");
        t.persist();
        new Course("Materia", t, group, 16).persist();

        // The school plans 4h/day: 16h are covered on the 4th plan day (Thu 17/09).
        // The legacy 8h/day model would claim the 2nd school day (Tue 15/09) instead.
        for (int d = 0; d < 5; d++) {
            new DailyActivityQuota(group, START.plusDays(d), 4, 0, 0, false).persist();
        }

        calculator.computeTargetEndDates(List.of(group), List.of(), List.of(), timeslots);

        // TASK-527 BUG C: the ideal end is the PLAN horizon (last positive day),
        // not the crossing of the course pool: the plan is the authority on when
        // the year ends (16h demand over a 20h plan still rides to day 5).
        assertEquals(START.plusDays(4), group.idealEndDateInfo.idealEndDate(),
                "ideal end must be the plan horizon (last positive plan day)");
        assertEquals(START.plusDays(4), group.idealEndDateVrpBuffered,
                "buffered target must follow the fact-based ideal (enough slots exist, no extension)");
    }

    @Test
    @Transactional
    void groupWithoutFactsKeepsTheLegacyFillRateModel() {
        List<Timeslot> timeslots = seedTwoWeeksOfTimeslots();
        StudentGroup group = new StudentGroup("TEST LEGACY", 20);
        group.maxHoursPerDay = 8;
        group.persist();
        Teacher t = new Teacher("Prof");
        t.persist();
        new Course("Materia", t, group, 16).persist();

        calculator.computeTargetEndDates(List.of(group), List.of(), List.of(), timeslots);

        assertEquals(START.plusDays(1), group.idealEndDateInfo.idealEndDate(),
                "no facts -> legacy model: 16h at 8h/day = 2nd school day");
    }

    /**
     * TASK-527 BUG C: the crossing threshold must be the IN-GRID pool (raw
     * totalHours minus expectedUnscheduledHours). G08 shape: raw pool 926
     * (626 real + 300 Stage pseudo), 24h off-grid, school plan 666 aula+lab
     * (+300 stage) riding to 07/06. With the raw pool the marker crossed at the
     * 626th planned hour (21/05); with the in-grid pool it rides toward the
     * plan's end.
     */
    @Test
    @Transactional
    void offGridHoursDoNotPullTheIdealEndEarly() {
        List<Timeslot> timeslots = seedTwoWeeksOfTimeslots();
        StudentGroup group = new StudentGroup("TEST OFFGRID", 20);
        group.maxHoursPerDay = 8;
        group.persist();
        Teacher t = new Teacher("Prof O");
        t.persist();
        Course c = new Course("Materia O", t, group, 10);
        c.expectedUnscheduledHours = 6; // 6 of the 10 hours are off-grid (Saturdays)
        c.persist();
        // The school plans 4h/day for 5 days (20h): the in-grid demand is 4h
        // (10 - 6), so the crossing must NOT stop at day 1... but the plan
        // itself only has to COVER the in-grid pool: crossing at 4h = day 1.
        // The assertion that matters: with the RAW pool (10h) the crossing
        // would be day 3; with in-grid (4h) it is day 1 - off-grid hours must
        // not consume plan days.
        for (int d = 0; d < 5; d++) {
            new DailyActivityQuota(group, START.plusDays(d), 4, 0, 0, false).persist();
        }
        calculator.computeTargetEndDates(List.of(group), List.of(), List.of(), timeslots);
        assertEquals(START.plusDays(4), group.idealEndDateInfo.idealEndDate(),
                "off-grid hours do not extend the plan: horizon is still the last plan day");
    }
}
