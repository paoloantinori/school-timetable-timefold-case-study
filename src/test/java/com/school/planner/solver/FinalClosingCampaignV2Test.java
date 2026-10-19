package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-537 v2: cross-day trade moves. A TRUE H17 hole that cannot be closed
 * intra-day (the pull-up would add a TRUE H1) may be closed by IMPORTING an
 * hour from a donor lesson on another day of the same group, paying the QUASI
 * H32 deficit (donor day drops below its aula+lab quota) within the golden
 * budget. When the budget is exhausted the move must be refused and counted.
 */
class FinalClosingCampaignV2Test {

    private static Timeslot ts(LocalDate d, int hour) {
        return new Timeslot(d.atTime(hour, 0), d.atTime(hour + 1, 0));
    }

    @AfterEach
    void clearGridStatic() {
        SchoolSchedule.setAllTimeslotsStatic(List.of());
    }

    private static final LocalDate MON = LocalDate.of(2026, 9, 21);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 22);

    // Quotas (aula+lab, injected): MON 3 (8/9/10), TUE 5 (8..12). Removing one
    // TUE hour leaves 4 = AT the 4h floor but below the 5h quota, so the trade
    // pays exactly 1 quasi H32 hour and 0 H33 days.

    private static SchoolSchedule scene(Teacher donorTeacher, List<Lesson> lessonsOut) {
        List<Timeslot> timeslots = new ArrayList<>();
        for (int h = 8; h <= 14; h++) {
            timeslots.add(ts(MON, h));
            timeslots.add(ts(TUE, h));
        }
        Teacher tb = new Teacher("Bloccante"); tb.id = 11L;
        // in H32 scope: "G04"/"G08"/"G14" are excluded from the quota
        // charge (finding 4), so the fixture group must be a scoped one
        StudentGroup g = new StudentGroup("G03", 18); g.id = 7L;
        StudentGroup other = new StudentGroup("G08", 17); other.id = 8L;

        Course cOther = new Course("Scienze", tb); cOther.studentGroup = other;
        Course cTb1 = new Course("Storia", tb); cTb1.studentGroup = g;
        Course cTb2 = new Course("Geografia", tb); cTb2.studentGroup = g;
        Course cDonor = new Course("Educazione", donorTeacher); cDonor.studentGroup = g;

        // MON: 8:00 and 10:00 by tb, hole at 9. tb ALSO teaches the other
        // group at 9:00, so both the 10:00 pull-up and any tb import are
        // H1-blocked (a TRUE): the intra-day move set cannot close this hole.
        Lesson blocker = new Lesson(cOther); blocker.timeslot = ts(MON, 9);
        Lesson a = new Lesson(cTb1); a.timeslot = ts(MON, 8);
        Lesson b = new Lesson(cTb2); b.timeslot = ts(MON, 10);
        // TUE donor day: five contiguous hours by the free teacher.
        Lesson d1 = new Lesson(cDonor); d1.timeslot = ts(TUE, 8);
        Lesson d2 = new Lesson(cDonor); d2.timeslot = ts(TUE, 9);
        Lesson d3 = new Lesson(cDonor); d3.timeslot = ts(TUE, 10);
        Lesson d4 = new Lesson(cDonor); d4.timeslot = ts(TUE, 11);
        Lesson d5 = new Lesson(cDonor); d5.timeslot = ts(TUE, 12);

        SchoolSchedule s = new SchoolSchedule();
        s.setTimeslots(timeslots);
        // TASK-614 fixture fix: publish the STATIC grid the cure's isSlotLegalFresh
        // gates read (empty static -> every slot rejected -> FCC closed nothing).
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        List<Lesson> lessons = new ArrayList<>(List.of(blocker, a, b, d1, d2, d3, d4, d5));
        s.setLessons(lessons);
        lessonsOut.addAll(lessons);
        return s;
    }

    private static java.util.function.BiFunction<Long, LocalDate, Integer> quotas() {
        Map<Long, Map<LocalDate, Integer>> q = new HashMap<>();
        q.put(7L, Map.of(MON, 3, TUE, 5));
        return (gid, d) -> q.getOrDefault(gid, Map.of()).get(d);
    }

    @Test
    void crossDayTradeClosesHolePayingQuasiH32WithinBudget() {
        Teacher free = new Teacher("Ferrari"); free.id = 22L;
        List<Lesson> lessons = new ArrayList<>();
        SchoolSchedule s = scene(free, lessons);

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(s,
                (l, nt) -> l.timeslot = nt, quotas(),
                FinalClosingCampaign.H32_BUDGET_HOURS, FinalClosingCampaign.H33_BUDGET_DAYS);

        assertEquals(1, r.trueH17Closed(), "the 9:00 hole must close via the cross-day donor import");
        assertEquals(1, r.quasiH32Paid(), "one quasi H32 hour paid (TUE drops 5 -> 4 against quota 5)");
        assertEquals(0, r.quasiH33Paid(), "4 remaining hours is AT the 4h floor, not below it");
        Lesson donor = lessons.stream()
                .filter(l -> l.course.studentGroup.id == 7L
                        && l.timeslot.startDateTime.toLocalDate().equals(MON)
                        && TimeslotSequence.slotIndexOf(l.timeslot) == TimeslotSequence.slotIndexOf(ts(MON, 9)))
                .findFirst().orElseThrow();
        assertEquals("Ferrari", donor.getTeacher().name, "the imported hour is the free teacher's donor lesson");
        assertTrue(lessons.stream().noneMatch(l -> l.timeslot == null), "no lesson de-assigned");
    }

    @Test
    void exhaustedBudgetRejectsTheTrade() {
        Teacher free = new Teacher("Ferrari"); free.id = 22L;
        List<Lesson> lessons = new ArrayList<>();
        SchoolSchedule s = scene(free, lessons);

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(s,
                (l, nt) -> l.timeslot = nt, quotas(), 0, FinalClosingCampaign.H33_BUDGET_DAYS);

        assertEquals(0, r.trueH17Closed(), "with the H32 budget exhausted the trade must be refused");
        assertEquals(0, r.quasiH32Paid(), "nothing paid");
        assertTrue(r.rejectedByBudget() >= 1, "the refusal is counted as budget-exhausted");
        long monNine = lessons.stream().filter(l -> l.timeslot.startDateTime.toLocalDate().equals(MON)
                && TimeslotSequence.slotIndexOf(l.timeslot) == TimeslotSequence.slotIndexOf(ts(MON, 9))
                && l.course.studentGroup.id == 7L).count();
        assertEquals(0, monNine, "the MON 9:00 hole stays open for group 7");
    }
}
