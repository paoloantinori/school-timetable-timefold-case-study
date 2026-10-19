package com.school.planner.solver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static com.school.planner.TestDataBuilder.course;
import static com.school.planner.TestDataBuilder.dailyActivityQuota;
import static com.school.planner.TestDataBuilder.group;
import static com.school.planner.TestDataBuilder.lesson;
import static com.school.planner.TestDataBuilder.teacher;
import static com.school.planner.TestDataBuilder.timeslot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * TASK-419 follow-up A4: the deterministic repairs price a move on M20's PROGRESSIVE deficit
 * curve ({@link SchoolScheduleConstraintProvider#m20DeficitCost}, marginal 1000, 1500, ...
 * 4500 per missing hour), instead of treating every day-fill hole as worth the same.
 *
 * <p>Three things are covered here, in the order they compose:
 * <ol>
 * <li>the cost model itself ({@code m20DayCost} / the two marginal helpers), including the
 *     surplus day (lending an hour is free), the at-quota day (1000) and the deep day;</li>
 * <li>{@link BoxRepair}'s direct pass, which must place a boxed lesson on the DEEPEST day it
 *     can legally reach, not on the chronologically first day with any deficit;</li>
 * <li>{@link HoleRepair}'s donor ranking, which must take the hour from a day that can afford
 *     to lend it (surplus, M20 cost 0) rather than from an at-quota day (1000), at equal M32.</li>
 * </ol>
 *
 * <p>Pure in-memory JUnit, no Quarkus/DB and no ConstraintVerifier: every input flows through
 * the {@link SchoolSchedule} statics, ids by hand - the same fixture shape {@code HoleRepairTest}
 * and {@code StagedPinPolicyTest} use.
 */
class MarginalDeficitCostTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 14);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 15);
    private static final LocalDate WED = LocalDate.of(2026, 9, 16);
    /** A school date the group has NO expected-hours row for: a TASK-408 no-session day. */
    private static final LocalDate THU = LocalDate.of(2026, 9, 17);
    private static final int SLOTS = 5;

    private long nextId = 1;
    private StudentGroup testGroup;
    private Teacher monTeacher;
    private Teacher tueTeacher;
    private Teacher wedTeacher;
    private Course monCourse;
    private Course tueCourse;
    private Course wedCourse;
    private final List<Timeslot> timeslots = new ArrayList<>();
    private final Map<String, Timeslot> byKey = new HashMap<>();

    @BeforeEach
    void setUp() {
        testGroup = group(nextId++, "TEST", 20);   // maxHoursPerDay null: H11 never binds here
        monTeacher = teacher(nextId++, "Prof Lunedi");
        tueTeacher = teacher(nextId++, "Prof Martedi");
        wedTeacher = teacher(nextId++, "Prof Mercoledi");
        monCourse = course(nextId++, "Materia Lunedi", monTeacher, testGroup);
        tueCourse = course(nextId++, "Materia Martedi", tueTeacher, testGroup);
        wedCourse = course(nextId++, "Materia Mercoledi", wedTeacher, testGroup);

        for (LocalDate date : List.of(MON, TUE, WED, THU)) {
            for (int slot = 0; slot < SLOTS; slot++) {
                Timeslot ts = timeslot(nextId++, LocalDateTime.of(date, LocalTime.of(8 + slot, 0)));
                timeslots.add(ts);
                byKey.put(date + "@" + slot, ts);
            }
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setTeacherAvailabilitiesStatic(List.of());
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        // No template published: every lesson reads as OFF-template, so HoleRepair's import
        // level 1 is the one under test and every candidate's M32 term is 0 - the M20 source
        // term is then the ONLY thing that can separate two donors.
        SchoolSchedule.setWeeklyTemplateStatic(Map.of(), Map.of());
        SchoolSchedule.setPromotedLessonIdsStatic(Set.of());
        // Default expectation: 4 aula hours on Mon/Tue/Wed, nothing on Thu (no-session).
        publishQuotas(4, 4, 4);
    }

    @AfterEach
    void tearDown() {
        SchoolSchedule.clearStaticState();
    }

    // ─── (i) the cost model ────────────────────────────────────────────────

    /**
     * The curve itself, read through the day-level accessor: a day at or above its quota costs
     * nothing, and each further missing hour costs 1000, 1500, 2000, 2500 more than the last
     * (cumulative 1000, 2500, 4500, 7000).
     */
    @Test
    void pricesADayOnM20sProgressiveCurve() {
        assertEquals(0, SchoolScheduleConstraintProvider.m20DayCost(testGroup, MON, 5, 5),
                "above quota: M20 charges nothing");
        assertEquals(0, SchoolScheduleConstraintProvider.m20DayCost(testGroup, MON, 4, 4),
                "exactly at quota 4");
        assertEquals(1000, SchoolScheduleConstraintProvider.m20DayCost(testGroup, MON, 3, 3));
        assertEquals(2500, SchoolScheduleConstraintProvider.m20DayCost(testGroup, MON, 2, 2));
        assertEquals(4500, SchoolScheduleConstraintProvider.m20DayCost(testGroup, MON, 1, 1));
        assertEquals(7000, SchoolScheduleConstraintProvider.m20DayCost(testGroup, MON, 0, 0),
                "an EMPTY expected day is a deficit at full depth (M20.2, same curve)");
    }

    /**
     * The two guards that keep the model exact rather than merely triangular: M20's own
     * {@code totalHours > 0} filter (a day carrying only authoritative pins is charged
     * nothing, since the constraint's stream never sees it) and the TASK-408 no-session rule
     * (a sheet-covered group's date with no fact row has quota 0, so it is never a deficit).
     */
    @Test
    void chargesNothingOnAPinsOnlyDayNorOnANoSessionDay() {
        assertEquals(0, SchoolScheduleConstraintProvider.m20DayCost(testGroup, MON, 0, 3),
                "3 authoritative pins, 0 countable hours: M20's totalHours > 0 guard");
        assertEquals(0, SchoolScheduleConstraintProvider.m20DayCost(testGroup, THU, 0, 0),
                "no expected-hours row for a sheet-covered group: quota 0 (H26 no-session day)");
        assertEquals(0, SchoolScheduleConstraintProvider.m20DayCost(testGroup, THU, 2, 2));
    }

    /**
     * The removal marginal - {@link HoleRepair}'s donor term. Lending an hour is FREE from a
     * day above quota, costs the flat first-hour 1000 from a day exactly at quota, and gets
     * progressively dearer the thinner the day already is.
     */
    @Test
    void pricesLendingAnHourByTheDepthOfTheSourceDay() {
        assertEquals(0, SchoolScheduleConstraintProvider.m20MarginalRemovalCost(testGroup, MON, 5, 5),
                "surplus day: removing an hour leaves it at quota, still no deficit");
        assertEquals(1000, SchoolScheduleConstraintProvider.m20MarginalRemovalCost(testGroup, MON, 4, 4),
                "at-quota day: the first missing hour is the flat 1000");
        assertEquals(1500, SchoolScheduleConstraintProvider.m20MarginalRemovalCost(testGroup, MON, 3, 3));
        assertEquals(2000, SchoolScheduleConstraintProvider.m20MarginalRemovalCost(testGroup, MON, 2, 2));
        assertEquals(2500, SchoolScheduleConstraintProvider.m20MarginalRemovalCost(testGroup, MON, 1, 1),
                "the day's last countable hour: 4th missing hour of a quota-4 day");
    }

    /**
     * The fill marginal - {@link BoxRepair}'s ranking key - is the exact mirror: one hour is
     * worth 2.5x more to a day 4 hours short than to a day one hour short, and nothing at all
     * to a day already full.
     */
    @Test
    void valuesAnHourByTheDepthOfTheTargetDay() {
        assertEquals(0, SchoolScheduleConstraintProvider.m20MarginalFillGain(testGroup, MON, 4, 4));
        assertEquals(1000, SchoolScheduleConstraintProvider.m20MarginalFillGain(testGroup, MON, 3, 3));
        assertEquals(1500, SchoolScheduleConstraintProvider.m20MarginalFillGain(testGroup, MON, 2, 2));
        assertEquals(2000, SchoolScheduleConstraintProvider.m20MarginalFillGain(testGroup, MON, 1, 1));
        assertEquals(2500, SchoolScheduleConstraintProvider.m20MarginalFillGain(testGroup, MON, 0, 0),
                "filling the first hour of an empty expected day: 7000 -> 4500");
    }

    // TASK-496 removal (2026-08-16): G14 is no longer exempt; its marginal costs
    // are priced identically to any other group (same quota, same formula).

    // ─── (ii) BoxRepair day ranking ────────────────────────────────────────

    /**
     * Monday is one hour short (3 of 4) and Tuesday is three hours short (1 of 4). Both offer a
     * legal, gate-passing cell for the boxed lesson, and Monday's comes FIRST chronologically -
     * the cell the old "first day with any deficit" rule would have taken. The hour is worth
     * 2000 on Tuesday against 1000 on Monday, so the deeper day must win.
     */
    @Test
    void placesTheBoxedLessonOnTheDeeperDayNotTheEarlierOne() {
        publishQuotas(4, 4, null);   // Wednesday out of session: two candidate days, not three
        Lesson mon0 = placed(monCourse, MON, 0);
        Lesson mon1 = placed(monCourse, MON, 1);
        Lesson mon2 = placed(monCourse, MON, 2);
        Lesson tue0 = placed(tueCourse, TUE, 0);
        Lesson boxed = lesson(nextId++, wedCourse, null, null);

        BoxRepair.Result r = BoxRepair.repair(schedule(mon0, mon1, mon2, tue0, boxed),
                (l, ts) -> l.timeslot = ts, l -> l.room = null);

        assertEquals(1, r.placed());
        assertEquals(0, r.boxedAfter());
        assertEquals(0, r.conflictsAdded());
        assertSame(byKey.get(TUE + "@1"), boxed.timeslot,
                "the deepest reachable day wins (Tuesday -3h at 2000/h vs Monday -1h at 1000/h)");
    }

    /**
     * The tie-break is the existing chronological VRP order, unchanged: with both days equally
     * deep the earliest legal cell is taken, so the ranking is total and stable.
     */
    @Test
    void breaksTiesChronologicallyWhenBothDaysAreEquallyDeep() {
        publishQuotas(4, 4, null);   // Wednesday out of session: two candidate days, not three
        Lesson mon0 = placed(monCourse, MON, 0);
        Lesson tue0 = placed(tueCourse, TUE, 0);
        Lesson boxed = lesson(nextId++, wedCourse, null, null);

        BoxRepair.Result r = BoxRepair.repair(schedule(mon0, tue0, boxed),
                (l, ts) -> l.timeslot = ts, l -> l.room = null);

        assertEquals(1, r.placed());
        assertSame(byKey.get(MON + "@1"), boxed.timeslot,
                "equal gain (-3h on both): the chronologically first legal cell wins");
    }

    /**
     * The deepest day of all is an EXPECTED day with nothing on it: M20.2 charges it the same
     * curve at full depth, so the first hour placed there is worth more than any top-up (2500
     * against Monday's 1000 and Tuesday's 2000 here). The repair follows the score's own
     * gradient - and this is a real behavioural consequence of the progressive curve, not an
     * accident of the fixture, so it is pinned.
     */
    @Test
    void opensAnEmptyExpectedDayBeforeToppingUpAPartialOne() {
        Lesson mon0 = placed(monCourse, MON, 0);
        Lesson mon1 = placed(monCourse, MON, 1);
        Lesson mon2 = placed(monCourse, MON, 2);
        Lesson tue0 = placed(tueCourse, TUE, 0);
        Lesson boxed = lesson(nextId++, wedCourse, null, null);   // Wednesday is empty and expected

        BoxRepair.Result r = BoxRepair.repair(schedule(mon0, mon1, mon2, tue0, boxed),
                (l, ts) -> l.timeslot = ts, l -> l.room = null);

        assertEquals(1, r.placed());
        assertSame(byKey.get(WED + "@0"), boxed.timeslot,
                "an empty expected day is a deficit at full depth (M20.2), so it is filled first");
    }

    // ─── (iii) HoleRepair donor ranking ────────────────────────────────────

    /**
     * Monday 0,1,_,3 has one H17 hole. Two days can legally lend the missing hour: Tuesday sits
     * exactly at its quota of 2 (lending costs 1000) and Wednesday is one hour ABOVE its quota
     * of 2 (lending costs nothing). Both donors are off-template, so their M32 terms are equal
     * at 0 and the M20 source term is the only separator - and the Tuesday donor has the LOWER
     * id, i.e. it is the one the old "first legal candidate wins" rule would have taken.
     */
    @Test
    void importsTheHourFromTheSurplusDayNotFromTheAtQuotaOne() {
        publishQuotas(4, 2, 2);
        Lesson mon0 = placed(monCourse, MON, 0);
        Lesson mon1 = placed(monCourse, MON, 1);
        Lesson mon3 = placed(monCourse, MON, 3);
        Lesson tue0 = placed(tueCourse, TUE, 0);   // lower ids than every Wednesday lesson
        Lesson tue1 = placed(tueCourse, TUE, 1);
        Lesson wed0 = placed(wedCourse, WED, 0);
        Lesson wed1 = placed(wedCourse, WED, 1);
        Lesson wed2 = placed(wedCourse, WED, 2);

        HoleRepair.Result r = HoleRepair.repair(schedule(mon0, mon1, mon3, tue0, tue1, wed0, wed1, wed2),
                (l, ts) -> l.timeslot = ts, l -> l.room = null);

        assertEquals(1, r.gapSlotsBefore(), "Monday slot 2 is the one penalized hole");
        assertEquals(0, r.gapSlotsAfter());
        assertEquals(1, r.closedByFreeImport());
        assertEquals(0, r.conflictsAdded());
        assertSame(byKey.get(MON + "@2"), wed0.timeslot,
                "the surplus day lent the hour (M20 cost 0) over the at-quota day (1000)");
        assertSame(byKey.get(TUE + "@0"), tue0.timeslot, "the at-quota day was left alone");
        assertSame(byKey.get(TUE + "@1"), tue1.timeslot);
    }

    /**
     * The mirror case, so the previous test cannot be passing by accident of ordering: with
     * Tuesday the surplus day and Wednesday the at-quota one, the hour comes from Tuesday -
     * which here is ALSO the lower-id day, hence the id-order control above.
     */
    @Test
    void importsFromWhicheverDayHasTheSurplus() {
        publishQuotas(4, 2, 3);
        Lesson mon0 = placed(monCourse, MON, 0);
        Lesson mon1 = placed(monCourse, MON, 1);
        Lesson mon3 = placed(monCourse, MON, 3);
        Lesson tue0 = placed(tueCourse, TUE, 0);
        Lesson tue1 = placed(tueCourse, TUE, 1);
        Lesson tue2 = placed(tueCourse, TUE, 2);
        Lesson wed0 = placed(wedCourse, WED, 0);
        Lesson wed1 = placed(wedCourse, WED, 1);
        Lesson wed2 = placed(wedCourse, WED, 2);

        HoleRepair.Result r = HoleRepair.repair(schedule(mon0, mon1, mon3, tue0, tue1, tue2, wed0, wed1, wed2),
                (l, ts) -> l.timeslot = ts, l -> l.room = null);

        assertEquals(0, r.gapSlotsAfter());
        assertEquals(1, r.closedByFreeImport());
        assertSame(byKey.get(MON + "@2"), tue0.timeslot,
                "Tuesday is the day above quota now (3 of 2), so it is the one that lends");
        assertSame(byKey.get(WED + "@0"), wed0.timeslot, "the at-quota Wednesday was left alone");
    }

    // ─── fixture helpers ───────────────────────────────────────────────────

    /**
     * Publish the group's expected aula hours for Mon/Tue/Wed - a null means "no row at all",
     * i.e. a no-session date the VRP drops entirely. Thursday never gets one, so the group
     * always has a no-session date to price.
     */
    private void publishQuotas(Integer monHours, Integer tueHours, Integer wedHours) {
        List<DailyActivityQuota> rows = new ArrayList<>();
        if (monHours != null) rows.add(dailyActivityQuota(testGroup, MON, monHours, 0, 0, false));
        if (tueHours != null) rows.add(dailyActivityQuota(testGroup, TUE, tueHours, 0, 0, false));
        if (wedHours != null) rows.add(dailyActivityQuota(testGroup, WED, wedHours, 0, 0, false));
        SchoolSchedule.setDailyActivityQuotasStatic(rows);
    }

    private SchoolSchedule schedule(Lesson... lessons) {
        return new SchoolSchedule(timeslots, List.of(), List.of(), List.of(), List.of(),
                List.of(testGroup), List.of(monTeacher, tueTeacher, wedTeacher),
                List.of(monCourse, tueCourse, wedCourse), List.of(lessons), List.of());
    }

    private Lesson placed(Course course, LocalDate date, int slot) {
        return lesson(nextId++, course, byKey.get(date + "@" + slot), null);
    }
}
