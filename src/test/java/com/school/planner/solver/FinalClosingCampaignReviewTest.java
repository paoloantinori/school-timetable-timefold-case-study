package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-537 code-review pins (7 findings, 20/08): the cross-day donor import
 * must (1) only take donors at the LAST slot of their day (removing an
 * interior lesson would open a NEW true H17 hole in the donor day, and taking
 * the last slot also keeps the day start fixed, finding 6); (2) survive a
 * donor day emptied by the trade (no NPE on the empty day view); (3) refuse
 * imports that would create an H28 A..B..A pattern on the target day; (4)
 * charge quasi costs with the REAL constraint semantics (Stage pseudo hours
 * not counted, per-group floor from SchoolSchedule.getDayFloor, H32 scope
 * exclusions and the G09 tolerance); (5) never use Stage/Sicurezza lessons
 * as donors; (7) never move into a slot outside the group's calendar window
 * (H20). Synthetic fixtures, no Quarkus boot: each test injects quotas and
 * (when needed) publishes/resets the SchoolSchedule static snapshots.
 */
class FinalClosingCampaignReviewTest {

    private static Timeslot ts(LocalDate d, int hour) {
        return new Timeslot(d.atTime(hour, 0), d.atTime(hour + 1, 0));
    }

    private static final LocalDate MON = LocalDate.of(2026, 9, 21);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 22);

    /** Scenes share this shape: MON has an H17 hole at 9 that only a
     * cross-day donor can close (the pull-up and any tb import are H1-blocked:
     * teacher tb teaches another group at 9:00). */
    private static final class Scene {
        final SchoolSchedule schedule;
        final List<Lesson> lessons;
        final Teacher tb = new Teacher("Bloccante");
        final StudentGroup g;

        Scene(String groupName, long groupId) {
            tb.id = 11L;
            List<Timeslot> timeslots = new ArrayList<>();
            for (int h = 8; h <= 14; h++) {
                timeslots.add(ts(MON, h));
                timeslots.add(ts(TUE, h));
            }
            g = new StudentGroup(groupName, 18);
            g.id = groupId;
            StudentGroup other = new StudentGroup("G08", 17);
            other.id = 8L;

            Course cOther = new Course("Scienze", tb);
            cOther.studentGroup = other;
            Course cTb1 = new Course("Storia", tb);
            cTb1.studentGroup = g;
            Course cTb2 = new Course("Geografia", tb);
            cTb2.studentGroup = g;

            Lesson blocker = new Lesson(cOther);
            blocker.timeslot = ts(MON, 9);
            Lesson a = new Lesson(cTb1);
            a.timeslot = ts(MON, 8);
            Lesson b = new Lesson(cTb2);
            b.timeslot = ts(MON, 10);

            schedule = new SchoolSchedule();
            schedule.setTimeslots(timeslots);
            // TASK-614 fixture fix: publish the STATIC grid the cure's
            // isSlotLegalFresh gates read (empty static -> every slot rejected).
            SchoolSchedule.setAllTimeslotsStatic(timeslots);
            lessons = new ArrayList<>(List.of(blocker, a, b));
            schedule.setLessons(lessons);
        }

        Lesson addDonor(String subject, Teacher t, int hour) {
            Course c = new Course(subject, t);
            c.studentGroup = g;
            Lesson l = new Lesson(c);
            l.timeslot = ts(TUE, hour);
            lessons.add(l);
            return l;
        }

        Lesson monDonor() {
            return lessons.stream()
                    .filter(l -> l.course.studentGroup.id == g.id
                            && l.timeslot.startDateTime.toLocalDate().equals(MON)
                            && TimeslotSequence.slotIndexOf(l.timeslot) == TimeslotSequence.slotIndexOf(ts(MON, 9)))
                    .findFirst().orElseThrow();
        }
    }

    private static BiFunction<Long, LocalDate, Integer> quotas(long gid, int mon, int tue) {
        Map<Long, Map<LocalDate, Integer>> q = new HashMap<>();
        q.put(gid, Map.of(MON, mon, TUE, tue));
        return (g, d) -> q.getOrDefault(g, Map.of()).get(d);
    }

    @AfterEach
    void resetStatics() {
        SchoolSchedule.setDayFloorsStatic(Map.of());
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setAllTimeslotsStatic(List.of());
    }

    @Test
    void donorMustBeAtTheLastSlotOfItsDay() {
        Scene sc = new Scene("G03", 7L);
        Teacher free = new Teacher("Ferrari");
        free.id = 22L;
        sc.addDonor("EdA", free, 8);
        sc.addDonor("EdB", free, 9);
        Lesson last = sc.addDonor("EdC", free, 10);

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(sc.schedule,
                (l, nt) -> l.timeslot = nt, quotas(7L, 3, 3), 1, 1);

        assertEquals(1, r.trueH17Closed(), "the hole closes via the LAST-slot donor");
        Lesson imported = sc.monDonor();
        assertEquals("EdC", imported.course.subject,
                "only the last slot of the donor day may be taken: removing an interior lesson would open a new H17 hole there");
        assertEquals(MON, last.timeslot.startDateTime.toLocalDate(), "sanity: 'last' is the moved lesson");
    }

    @Test
    void emptyingTheDonorDayDoesNotThrow() {
        Scene sc = new Scene("G03", 7L);
        Teacher free = new Teacher("Ferrari");
        free.id = 22L;
        sc.addDonor("EdA", free, 8); // the ONLY TUE lesson: the day empties

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(sc.schedule,
                (l, nt) -> l.timeslot = nt, quotas(7L, 3, 5), 1, 4);

        assertEquals(1, r.trueH17Closed(), "the trade is legal (budgets cover quota+floor) and must not NPE");
        assertEquals(1, r.quasiH32Paid());
        assertTrue(sc.lessons.stream().noneMatch(l -> l.timeslot == null));
    }

    @Test
    void h28GuardRefusesTheAbaImport() {
        Scene sc = new Scene("G03", 7L);
        // MON already has Storia by tb at 8 and Geografia... we need the SAME
        // subject on both sides of the hole: rebuild the MON pair as one subject.
        Teacher x = sc.tb;
        Course storia = new Course("Storia", x);
        storia.studentGroup = sc.g;
        sc.lessons.get(2).course = storia; // MON 10 also Storia by tb now
        // TUE donor: SAME subject, DIFFERENT teacher -> importing it into the
        // MON 9 hole creates the H28 A..B..A pattern (tb@8, other@9, tb@10).
        Teacher y = new Teacher("T20");
        y.id = 33L;
        sc.addDonor("Storia", y, 8);

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(sc.schedule,
                (l, nt) -> l.timeslot = nt, quotas(7L, 3, 5), 1, 4);

        assertEquals(0, r.trueH17Closed(), "the import would create a TRUE H28 return: must be refused");
        assertEquals(0, sc.lessons.stream().filter(l -> l.course.studentGroup.id == 7L
                && l.timeslot.startDateTime.toLocalDate().equals(MON)
                && TimeslotSequence.slotIndexOf(l.timeslot) == 1).count(),
                "the MON 9:00 hole stays open");
    }

    @Test
    void h32ExcludedGroupsPayNoQuasiH32() {
        // "G04" is out of H32's scope (TASK-497 pool-cap owns its targets):
        // dropping its donor day 5 -> 4 against quota 5 must charge NOTHING.
        Scene sc = new Scene("G04", 7L);
        Teacher free = new Teacher("Ferrari");
        free.id = 22L;
        for (int h = 8; h <= 12; h++) {
            sc.addDonor("Ed" + h, free, h);
        }

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(sc.schedule,
                (l, nt) -> l.timeslot = nt, quotas(7L, 3, 5), 0, 4);

        assertEquals(1, r.trueH17Closed(), "the trade is free in quasi terms for an H32-excluded group");
        assertEquals(0, r.quasiH32Paid());
        assertEquals(0, r.quasiH33Paid(), "4 remaining hours is AT the fallback 4h floor");
    }

    @Test
    void modaToleranceForgivesOneQuasiHour() {
        // G09 carries a declared 1h tolerance in H32: 5 -> 4 against quota 5
        // is INSIDE tolerance, so the trade pays no quasi H32.
        Scene sc = new Scene("G09", 7L);
        Teacher free = new Teacher("Ferrari");
        free.id = 22L;
        for (int h = 8; h <= 12; h++) {
            sc.addDonor("Ed" + h, free, h);
        }

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(sc.schedule,
                (l, nt) -> l.timeslot = nt, quotas(7L, 3, 5), 0, 4);

        assertEquals(1, r.trueH17Closed());
        assertEquals(0, r.quasiH32Paid(), "after=4 is at quota-tolerance (5-1): no H32 charge");
    }

    @Test
    void stagePseudoHoursAreNotCountedByTheBudget() {
        // TUE: two Stage (pseudo) hours at 8/9 plus two real hours at 10/11,
        // quota 4. The REAL constraint axes count 2 hours: taking the last
        // slot leaves 1 -> pays BOTH quasi H32 (1 < 4) and H33 (1 < 4).
        Scene sc = new Scene("G03", 7L);
        Teacher stage = new Teacher("Stage");
        stage.id = 99L;
        stage.pseudo = true;
        Teacher free = new Teacher("Ferrari");
        free.id = 22L;
        sc.addDonor("Stage", stage, 8);
        sc.addDonor("Stage", stage, 9);
        sc.addDonor("EdA", free, 10);
        sc.addDonor("EdB", free, 11); // last slot: the donor

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(sc.schedule,
                (l, nt) -> l.timeslot = nt, quotas(7L, 3, 4), 1, 4);

        assertEquals(1, r.trueH17Closed(), "the pseudo lessons are never donors; the last real slot is");
        assertEquals("EdB", sc.monDonor().course.subject);
        assertEquals(1, r.quasiH32Paid());
        assertEquals(1, r.quasiH33Paid(), "counted hours drop 2 -> 1, under the floor: H33 charged");
    }

    @Test
    void sicurezzaLessonIsNeverADonor() {
        // The Sicurezza hour sits at the LAST slot of the donor day: without
        // the filter it would be the chosen donor (it passes the edge rule).
        Scene sc = new Scene("G03", 7L);
        Teacher free = new Teacher("Ferrari");
        free.id = 22L;
        sc.addDonor("EdA", free, 8);
        sc.addDonor("Sicurezza", free, 9); // last slot, but H30-protected

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(sc.schedule,
                (l, nt) -> l.timeslot = nt, quotas(7L, 3, 2), 1, 4);

        assertEquals(0, r.trueH17Closed(),
                "the only last-slot donor is a Sicurezza lesson: no legal donor, hole stays");
        assertTrue(sc.lessons.stream().filter(l -> "Sicurezza".equals(l.course.subject))
                .allMatch(l -> l.timeslot.startDateTime.toLocalDate().equals(TUE)),
                "the Sicurezza hour is not moved");
    }

    @Test
    void floorComesFromTheDerivedPerGroupFloor() {
        // Published floor 2 for group 7: dropping the donor day 3 -> 2 leaves
        // it AT the floor, so the trade must be accepted even with an H33
        // budget of ZERO (a hardcoded 4h floor would refuse it).
        Scene sc = new Scene("G03", 7L);
        Teacher free = new Teacher("Ferrari");
        free.id = 22L;
        sc.addDonor("EdA", free, 8);
        sc.addDonor("EdB", free, 9);
        sc.addDonor("EdC", free, 10); // last slot
        SchoolSchedule.setDayFloorsStatic(Map.of(7L, 2));

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(sc.schedule,
                (l, nt) -> l.timeslot = nt, quotas(7L, 3, 3), 1, 0);

        assertEquals(1, r.trueH17Closed(), "2 remaining hours meets the derived floor 2: no H33 charge");
        assertEquals(0, r.quasiH33Paid());
    }

    @Test
    void targetSlotOutsideTheGroupCalendarWindowIsRefused() {
        // Group 7's Monday window is slot 0 only (08:00): the hole at slot 1
        // (09:00) is OUTSIDE the window (H20), so neither the pull-up nor the
        // donor import may fill it.
        Scene sc = new Scene("G03", 7L);
        Teacher free = new Teacher("Ferrari");
        free.id = 22L;
        sc.addDonor("EdA", free, 8);
        GroupCalendarRule rule = new GroupCalendarRule(sc.g, DayOfWeek.MONDAY, 0, 0);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(rule));

        FinalClosingCampaign.Result r = FinalClosingCampaign.close(sc.schedule,
                (l, nt) -> l.timeslot = nt, quotas(7L, 3, 2), 1, 4);

        assertEquals(0, r.trueH17Closed(), "the hole cell is closed by the group calendar (H20): refuse");
        assertTrue(sc.lessons.stream().noneMatch(l -> l.course.studentGroup.id == 7L
                        && l.timeslot.startDateTime.toLocalDate().equals(MON)
                        && TimeslotSequence.slotIndexOf(l.timeslot) == TimeslotSequence.slotIndexOf(ts(MON, 9))),
                "nothing lands in the out-of-window MON 9:00 cell");
    }
}
