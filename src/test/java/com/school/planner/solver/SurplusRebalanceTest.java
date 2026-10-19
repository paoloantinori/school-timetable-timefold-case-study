package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.TestDataBuilder;
import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-422: unit tests for {@link SurplusRebalance} on a synthetic two-day fixture - one
 * above-plan Monday (the donor) and one below-plan Tuesday (the target), with the day's
 * expected hours supplied as real {@code DailyActivityQuota} facts so M20's own quota rules
 * decide which day is which. Pure in-memory JUnit (no Quarkus/DB), same shape as {@link
 * HoleRepairTest}: every input flows through the {@link SchoolSchedule} statics, ids by hand.
 *
 * <p>Each case isolates ONE gate by leaving it as the only reason a move can be refused. The
 * invariants every case asserts, whatever the outcome: the pass never adds a conflict and
 * never raises the M20 day-fill cost (improve-or-noop).
 */
class SurplusRebalanceTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 14);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 15);
    private static final int SLOTS = 5;

    private long nextId = 1;
    private StudentGroup group;
    private final List<Timeslot> timeslots = new ArrayList<>();
    private final Map<String, Timeslot> byKey = new HashMap<>();
    private final List<Teacher> teachers = new ArrayList<>();
    private final List<Course> courses = new ArrayList<>();
    private final List<DailyActivityQuota> quotaRows = new ArrayList<>();
    private final List<TeacherAvailability> availabilities = new ArrayList<>();

    @BeforeEach
    void setUp() {
        group = TestDataBuilder.group(nextId++, "TEST", 20);

        for (LocalDate date : List.of(MON, TUE)) {
            for (int slot = 0; slot < SLOTS; slot++) {
                Timeslot ts = TestDataBuilder.timeslot(nextId++, date, 8 + slot, 0, 9 + slot, 0);
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
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        // No template published: every M32 delta is 0, so the mover ranking is decided by the
        // (donor date, id, slot) tie-breaks alone and the tests read deterministically.
        SchoolSchedule.setWeeklyTemplateStatic(Map.of(), Map.of());
        // The suite shares one JVM: a promotion registry left behind by another test would make
        // this pass throw at entry (it is designed for the post-release grid only).
        SchoolSchedule.setPromotedLessonIdsStatic(Set.of());
    }

    @AfterEach
    void tearDown() {
        SchoolSchedule.clearStaticState();
    }

    /**
     * Monday is planned for 1 hour but holds 2, Tuesday is planned for 4 and holds 1: the pass
     * lends one Monday hour to Tuesday. The mover is Monday's SECOND hour, not its first - the
     * M18 guard never lets the day's start slide later (see {@link #refusesToDeAnchorTheDonorDay}
     * for that gate in isolation) - and it lands at Tuesday's slot 1, the only arrival that
     * leaves the day gap-free.
     */
    @Test
    void movesASurplusHourToADeficitDay() {
        expect(MON, 1);
        expect(TUE, 4);
        Course monday = newCourse("Materia");
        Lesson mon0 = placed(monday, MON, 0, false);
        Lesson mon1 = placed(monday, MON, 1, false);
        Lesson tue0 = placed(newCourse("Altra Materia"), TUE, 0, false);

        SurplusRebalance.Result r = rebalance(mon0, mon1, tue0);

        assertEquals(1, r.movesApplied());
        assertSame(byKey.get(TUE + "@1"), mon1.timeslot, "the surplus hour landed next to Tuesday's own hour");
        assertSame(byKey.get(MON + "@0"), mon0.timeslot, "Monday's first hour is its anchor and stays");
        assertSame(byKey.get(TUE + "@0"), tue0.timeslot);
        // Tuesday goes from 3 hours short to 2: the marginal value of the deepest missing hour
        // (m20DeficitCost(3) - m20DeficitCost(2) = 4500 - 2500), and Monday's surplus is free.
        assertEquals(2000, r.m20Saved());
    }

    /**
     * The M18 gate in isolation: Monday's only OTHER movable hour is out of reach (its teacher
     * is on vacation all Tuesday, so the VRP offers it no cell there), leaving just the day's
     * FIRST hour - moving it would start Monday one slot later, which M18 charges at 50 x
     * offset. The pass declines and leaves the grid byte-identical.
     */
    @Test
    void refusesToDeAnchorTheDonorDay() {
        expect(MON, 1);
        expect(TUE, 4);
        Course anchored = newCourse("Materia");
        Course grounded = newCourse("Altra Materia");
        blockTeacherOn(grounded.teacher, TUE);
        Lesson mon0 = placed(anchored, MON, 0, false);
        Lesson mon1 = placed(grounded, MON, 1, false);
        Lesson tue0 = placed(newCourse("Terza Materia"), TUE, 0, false);

        SurplusRebalance.Result r = rebalance(mon0, mon1, tue0);

        assertEquals(0, r.movesApplied(), "the day's first hour is the only candidate left, and M18 protects it");
        assertSame(byKey.get(MON + "@0"), mon0.timeslot);
        assertSame(byKey.get(MON + "@1"), mon1.timeslot);
    }

    /**
     * H17-safe ARRIVAL in isolation: Tuesday's grid has no cell adjacent to the hour already
     * there (slot 1 does not exist that day), so every reachable cell would open a student-day
     * hole. Nothing is moved, rather than trading an M20 hour for a hard H17 point.
     */
    @Test
    void refusesAnArrivalThatWouldOpenAHole() {
        expect(MON, 1);
        expect(TUE, 4);
        dropTimeslot(TUE, 1);
        Course monday = newCourse("Materia");
        Lesson mon0 = placed(monday, MON, 0, false);
        Lesson mon1 = placed(monday, MON, 1, false);
        Lesson tue0 = placed(newCourse("Altra Materia"), TUE, 0, false);

        SurplusRebalance.Result r = rebalance(mon0, mon1, tue0);

        assertEquals(0, r.movesApplied(), "slots 2-4 all leave a hole after Tuesday's slot 0");
        assertSame(byKey.get(MON + "@1"), mon1.timeslot);
        assertEquals(0, r.m20Saved());
    }

    /**
     * H17-safe DEPARTURE in isolation: Monday's two unpinned hours both sit strictly inside the
     * day's run, each with an unpinned neighbour on one side, so taking either would open a
     * penalized break behind it. The day's two authoritative pins cannot move at all.
     */
    @Test
    void refusesToTakeAnHourThatWouldOpenAHoleAtTheDonor() {
        expect(MON, 1);
        expect(TUE, 4);
        Lesson mon0 = placed(newCourse("Prima"), MON, 0, true);    // authoritative pin
        Lesson mon1 = placed(newCourse("Seconda"), MON, 1, false);
        Lesson mon2 = placed(newCourse("Terza"), MON, 2, false);
        Lesson mon3 = placed(newCourse("Quarta"), MON, 3, true);   // authoritative pin
        Lesson tue0 = placed(newCourse("Quinta"), TUE, 0, false);

        SurplusRebalance.Result r = rebalance(mon0, mon1, mon2, mon3, tue0);

        assertEquals(0, r.movesApplied());
        assertSame(byKey.get(MON + "@1"), mon1.timeslot);
        assertSame(byKey.get(MON + "@2"), mon2.timeslot);
        assertSame(byKey.get(TUE + "@0"), tue0.timeslot);
    }

    /**
     * The M15 guard in isolation, on the one shape ONLY it can refuse. Monday teaches the same
     * subject at slots 0, 2 and 4, with authoritative pins of other subjects at 1 and 3:
     * <ul>
     * <li>slot 0 is the day's anchor, M18 protects it;</li>
     * <li>slot 4's teacher is on vacation all Tuesday, so the VRP offers it no cell;</li>
     * <li>slot 2 passes every other gate - its H17 departure is EXEMPT, because the break it
     *     would leave is bounded by two authoritative pins (task-350) - but taking it merges
     *     two 1-hour same-subject splits into one 3-hour split, which M15 charges more for.</li>
     * </ul>
     * So the pass declines: it never widens (or opens) a same-day same-subject split to fill a
     * day.
     */
    @Test
    void refusesToWidenASameSubjectSplitAtTheDonor() {
        expect(MON, 1);
        expect(TUE, 4);
        Course first = newCourse("Materia");
        Course second = newCourse("Materia");    // same SUBJECT, different teacher: one M15 slice
        Course third = newCourse("Materia");
        blockTeacherOn(third.teacher, TUE);
        Lesson mon0 = placed(first, MON, 0, false);
        Lesson mon1 = placed(newCourse("Pinnata A"), MON, 1, true);
        Lesson mon2 = placed(second, MON, 2, false);
        Lesson mon3 = placed(newCourse("Pinnata B"), MON, 3, true);
        Lesson mon4 = placed(third, MON, 4, false);
        Lesson tue0 = placed(newCourse("Altra Materia"), TUE, 0, false);

        SurplusRebalance.Result r = rebalance(mon0, mon1, mon2, mon3, mon4, tue0);

        assertEquals(0, r.movesApplied());
        assertSame(byKey.get(MON + "@2"), mon2.timeslot, "the middle hour of the split block stayed put");
        assertSame(byKey.get(MON + "@0"), mon0.timeslot);
        assertSame(byKey.get(MON + "@4"), mon4.timeslot);
    }

    /**
     * Neither an authoritative pin nor a pseudo-teacher (Stage) hour is ever lent. Asserted on
     * {@link SurplusRebalance#isMovable} DIRECTLY, not only through the pass: on any fixture
     * that makes such an hour the sole candidate, the VRP already refuses it for reasons of its
     * own (a pinned lesson's value range is its own cell; a Stage stub is stage-confined), so a
     * pass-level "0 moves" would hold even with both exclusions deleted - a vacuous test. The
     * ordinary hour is asserted movable in the same breath, so the two refusals cannot pass by
     * the predicate being false for everything.
     */
    @Test
    void neverMovesPinnedOrPseudoTeacherHours() {
        expectStage(MON, 8);
        expect(TUE, 4);
        Teacher pseudo = TestDataBuilder.teacher(nextId++, "Stage", true);
        teachers.add(pseudo);
        Course stageCourse = TestDataBuilder.course(nextId++, "Stage", pseudo, group);
        courses.add(stageCourse);
        Lesson pinnedHour = placed(newCourse("Materia"), MON, 0, true);
        Lesson stageHour = placed(stageCourse, MON, 1, false);
        Lesson ordinaryHour = placed(newCourse("Terza Materia"), MON, 2, false);
        Lesson tue0 = placed(newCourse("Altra Materia"), TUE, 0, false);

        assertFalse(SurplusRebalance.isMovable(pinnedHour), "an authoritative pin is never lent");
        assertFalse(SurplusRebalance.isMovable(stageHour), "a pseudo-teacher (Stage) hour is never lent");
        assertTrue(SurplusRebalance.isMovable(ordinaryHour), "an ordinary placed unpinned hour is lendable");

        SurplusRebalance.Result r = rebalance(pinnedHour, stageHour, ordinaryHour, tue0);

        // Only the ordinary hour can move, and it does: the two excluded ones stay where they are.
        assertEquals(1, r.movesApplied());
        assertSame(byKey.get(MON + "@0"), pinnedHour.timeslot);
        assertSame(byKey.get(MON + "@1"), stageHour.timeslot);
        assertSame(byKey.get(TUE + "@1"), ordinaryHour.timeslot);
    }

    /**
     * The M15 ARRIVAL guard in isolation - the subtler half of the split guard, whose departure
     * half is pinned by {@link #refusesToWidenASameSubjectSplitAtTheDonor}. Tuesday teaches
     * "Materia" at slot 0 and another subject at slot 1, so the only H17-safe arrival for
     * Monday's spare "Materia" hour is slot 2: legal for the whole DAY (it extends the run) but
     * it would leave that subject's own slice split 0 / 2, a new M15 break. The pass declines
     * rather than create one.
     */
    @Test
    void refusesAnArrivalThatWouldSplitASameSubjectBlockAtTheTarget() {
        expect(MON, 1);
        expect(TUE, 4);
        Course shared = newCourse("Materia");
        Lesson mon0 = placed(newCourse("Prima"), MON, 0, false);       // Monday's anchor, M18 protects it
        Lesson mon1 = placed(shared, MON, 1, false);                   // the only mover
        Lesson tue0 = placed(newCourse("Materia"), TUE, 0, false);     // same SUBJECT, one M15 slice
        Lesson tue1 = placed(newCourse("Altra Materia"), TUE, 1, false);

        SurplusRebalance.Result r = rebalance(mon0, mon1, tue0, tue1);

        assertEquals(0, r.movesApplied(), "slot 2 is the only H17-safe cell and it splits the subject's block");
        assertSame(byKey.get(MON + "@1"), mon1.timeslot);
        assertSame(byKey.get(TUE + "@0"), tue0.timeslot);
        assertSame(byKey.get(TUE + "@1"), tue1.timeslot);
    }

    /**
     * Two runs of the identical fixture (same ids, same statics) must pick the same mover and
     * the same target cell - the pass decides a move order the solver depends on being
     * reproducible, exactly like {@link StudentDayCompaction}'s own.
     */
    @Test
    void isDeterministic() {
        expect(MON, 1);
        expect(TUE, 4);
        long[] first = runFixedFixture();
        long[] second = runFixedFixture();

        assertArrayEquals(first, second, "the move count and every lesson's final cell must be identical");
        assertEquals(2, first[0], "the fixture does have legal moves to be deterministic about");
        // Monday lends its two tail hours in tail-first order (each is the day's last hour when
        // it is taken), and Tuesday fills upward from the hour it already had.
        assertEquals(byKey.get(MON + "@0").id, first[1], "Monday keeps its anchor hour");
        assertEquals(byKey.get(TUE + "@2").id, first[2]);
        assertEquals(byKey.get(TUE + "@1").id, first[3]);
    }

    /**
     * The {@link #movesASurplusHourToADeficitDay} fixture with one more surplus hour and FIXED
     * lesson ids, rebuilt from scratch on every call: {@code {moves, then each Monday lesson's
     * final timeslot id}}.
     */
    private long[] runFixedFixture() {
        Course monday = newCourse("Materia");
        Course tuesday = newCourse("Altra Materia");
        Lesson mon0 = placedWithId(9001, monday, MON, 0, false);
        Lesson mon1 = placedWithId(9002, monday, MON, 1, false);
        Lesson mon2 = placedWithId(9003, monday, MON, 2, false);
        Lesson tue0 = placedWithId(9004, tuesday, TUE, 0, false);

        SurplusRebalance.Result r = rebalance(mon0, mon1, mon2, tue0);

        assertSame(byKey.get(TUE + "@0"), tue0.timeslot);
        return new long[] { r.movesApplied(), mon0.timeslot.id, mon1.timeslot.id, mon2.timeslot.id };
    }

    // ─── fixture helpers ───────────────────────────────────────────────────

    private SurplusRebalance.Result rebalance(Lesson... lessons) {
        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), availabilities,
                List.of(), List.of(group), teachers, courses, List.of(lessons), List.of());
        SurplusRebalance.DayFill before = SurplusRebalance.measure(schedule.getLessons());
        SurplusRebalance.Result r = SurplusRebalance.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);
        SurplusRebalance.DayFill after = SurplusRebalance.measure(schedule.getLessons());
        assertEquals(0, r.conflictsAdded(), "the pass is conflict-free by construction");
        assertEquals(before, r.before(), "the pass and the standalone measurement must agree");
        assertEquals(after, r.after());
        assertTrue(r.after().m20Cost() <= r.before().m20Cost(), "improve-or-noop on the M20 axis");
        return r;
    }

    /** A course with its own teacher; two calls with the same subject share ONE M15 slice. */
    private Course newCourse(String subject) {
        Teacher teacher = TestDataBuilder.teacher(nextId++, "Prof " + nextId);
        teachers.add(teacher);
        Course course = TestDataBuilder.course(nextId++, subject, teacher, group);
        courses.add(course);
        return course;
    }

    private Lesson placed(Course course, LocalDate date, int slot, boolean pinned) {
        return placedWithId(nextId++, course, date, slot, pinned);
    }

    private Lesson placedWithId(long id, Course course, LocalDate date, int slot, boolean pinned) {
        return TestDataBuilder.lesson(id, course, byKey.get(date + "@" + slot), null, pinned);
    }

    /** The school's own expected aula hours for that date - M20's quota, via the real fact. */
    private void expect(LocalDate date, int aulaHours) {
        quotaRows.add(TestDataBuilder.dailyActivityQuota(group, date, aulaHours, 0, 0, false));
        SchoolSchedule.setDailyActivityQuotasStatic(quotaRows);
    }

    /** A stage-only expected day: no aula/lab hours at all, so its quota is 0 (every hour is surplus). */
    private void expectStage(LocalDate date, int stageHours) {
        quotaRows.add(TestDataBuilder.dailyActivityQuota(group, date, 0, 0, stageHours, false));
        SchoolSchedule.setDailyActivityQuotasStatic(quotaRows);
    }

    /** Put a teacher out of reach for a whole date (H5), so the VRP offers its lessons no cell there. */
    private void blockTeacherOn(Teacher teacher, LocalDate date) {
        availabilities.add(new TeacherAvailability(teacher, date.atStartOfDay(),
                date.plusDays(1).atStartOfDay(), AvailabilityType.VACATION));
        SchoolSchedule.setTeacherAvailabilitiesStatic(availabilities);
    }

    /** Remove one cell from the grid entirely (no {@link Timeslot} row at all for that date/slot). */
    private void dropTimeslot(LocalDate date, int slot) {
        Timeslot ts = byKey.remove(date + "@" + slot);
        timeslots.remove(ts);
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
    }
}
