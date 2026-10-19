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
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-419: unit tests for {@link HoleRepair}'s three levels on a synthetic (group, date)
 * fixture - a hole closed by importing a free hour from another date, a hole closed by the
 * min-slide, and a hole no level can close (strict no-op). Pure in-memory JUnit (no
 * Quarkus/DB): every input flows through the {@link SchoolSchedule} statics, ids by hand.
 *
 * <p>The invariants every case asserts, whatever the outcome: the pass never adds a conflict
 * and never leaves MORE penalized hole slots than it found (improve-or-noop).
 */
class HoleRepairTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 14);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 15);
    private static final int SLOTS = 5;

    private long nextId = 1;
    private StudentGroup group;
    private Teacher dayTeacher;
    private Teacher visitingTeacher;
    private Course dayCourse;
    private Course visitingCourse;
    private final List<Timeslot> timeslots = new ArrayList<>();
    private final Map<String, Timeslot> byKey = new HashMap<>();

    @BeforeEach
    void setUp() {
        group = TestDataBuilder.group(nextId++, "TEST", 20);
        dayTeacher = TestDataBuilder.teacher(nextId++, "Prof Giorno");
        visitingTeacher = TestDataBuilder.teacher(nextId++, "Prof Ospite");
        dayCourse = TestDataBuilder.course(nextId++, "Materia", dayTeacher, group);
        visitingCourse = TestDataBuilder.course(nextId++, "Altra Materia", visitingTeacher, group);

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
        // No template published: every lesson reads as OFF-template (level 1 / level 3 is
        // selected per test by publishing one where it matters).
        SchoolSchedule.setWeeklyTemplateStatic(Map.of(), Map.of());
        // The suite shares one JVM: a promotion registry left behind by another test could
        // collide with these hand-assigned ids and make a pinned lesson read as non-authoritative
        // (isAuthoritativePin), which is exactly what decides whether a break is a hole here.
        SchoolSchedule.setPromotedLessonIdsStatic(Set.of());
    }

    @AfterEach
    void tearDown() {
        SchoolSchedule.clearStaticState();
    }

    /**
     * Monday 0,1,_,3 with a spare Tuesday hour: the hole is closed by IMPORTING that hour
     * (level 1). The import is legal on every gate - the Tuesday day empties, so its
     * departure opens nothing.
     */
    @Test
    void closesTheHoleByImportingAFreeHourFromAnotherDay() {
        Lesson mon0 = placed(dayCourse, MON, 0, false);
        Lesson mon1 = placed(dayCourse, MON, 1, false);
        Lesson mon3 = placed(dayCourse, MON, 3, false);
        Lesson spare = placed(visitingCourse, TUE, 0, false);

        HoleRepair.Result r = repair(mon0, mon1, mon3, spare);

        assertEquals(1, r.gapSlotsBefore(), "Monday slot 2 is the one penalized hole");
        assertEquals(0, r.gapSlotsAfter());
        assertEquals(1, r.closedByFreeImport(), "closed at level 1 (free import)");
        assertEquals(0, r.closedByMinSlide());
        assertEquals(0, r.closedByTemplateImport());
        assertEquals(0, r.conflictsAdded());
        assertSame(byKey.get(MON + "@2"), spare.timeslot, "the spare hour landed in the hole");
    }

    /**
     * Monday _,1,2,3 with the day's first lesson at slot 0 and NO importable hour anywhere:
     * the min-slide (level 2) moves that first lesson up into the hole, which deletes the
     * leading slot and fills the hole in one move.
     */
    @Test
    void closesTheHoleBySlidingTheDaysFirstLessonUp() {
        Lesson mon0 = placed(dayCourse, MON, 0, false);
        Lesson mon2 = placed(dayCourse, MON, 2, false);
        Lesson mon3 = placed(dayCourse, MON, 3, false);

        HoleRepair.Result r = repair(mon0, mon2, mon3);

        assertEquals(1, r.gapSlotsBefore(), "Monday slot 1 is the one penalized hole");
        assertEquals(0, r.gapSlotsAfter());
        assertEquals(0, r.closedByFreeImport(), "nothing to import: every lesson is on the hole's own day");
        assertEquals(1, r.closedByMinSlide(), "closed at level 2 (min-slide)");
        assertEquals(0, r.closedByTemplateImport());
        assertEquals(0, r.conflictsAdded());
        assertSame(byKey.get(MON + "@1"), mon0.timeslot, "the day's first lesson slid up into the hole");
    }

    /**
     * The same shape, but the day's first lesson is an AUTHORITATIVE pin and there is still
     * nothing to import: no level applies, so the pass must leave the grid byte-identical
     * rather than shift the hole around (improve-or-noop).
     */
    @Test
    void leavesTheGridUntouchedWhenNoLevelCanCloseTheHole() {
        Lesson mon0 = placed(dayCourse, MON, 0, true);   // pinned: the min-slide cannot move it
        Lesson mon2 = placed(dayCourse, MON, 2, false);
        Lesson mon3 = placed(dayCourse, MON, 3, false);

        HoleRepair.Result r = repair(mon0, mon2, mon3);

        assertEquals(1, r.gapSlotsBefore());
        assertEquals(1, r.gapSlotsAfter(), "the hole survives - and is not merely displaced");
        assertEquals(0, r.closed(), "no level applied");
        assertEquals(0, r.conflictsAdded());
        assertSame(byKey.get(MON + "@0"), mon0.timeslot);
        assertSame(byKey.get(MON + "@2"), mon2.timeslot);
        assertSame(byKey.get(MON + "@3"), mon3.timeslot);
    }

    /**
     * A break between two AUTHORITATIVE pins is not a hole at all (task-350's break-level
     * exemption, which H17 itself applies): the pass must not spend an import on it, even
     * with a perfectly legal spare hour available.
     */
    @Test
    void ignoresABreakBetweenTwoAuthoritativePins() {
        Lesson mon0 = placed(dayCourse, MON, 0, true);
        Lesson mon3 = placed(dayCourse, MON, 3, true);
        Lesson spare = placed(visitingCourse, TUE, 0, false);

        HoleRepair.Result r = repair(mon0, mon3, spare);

        assertEquals(0, r.gapSlotsBefore(), "both bounding lessons are pinned: H17 charges nothing");
        assertEquals(0, r.closed());
        assertSame(byKey.get(TUE + "@0"), spare.timeslot, "the spare hour was not spent");
    }

    /**
     * With a free (off-template) hour AND an on-template one both able to fill the hole, the
     * free one is taken: level 1 costs no M32, level 3 costs the mover's course weight.
     */
    @Test
    void prefersTheFreeHourOverTheOnTemplateOneForTheSameHole() {
        Lesson mon0 = placed(dayCourse, MON, 0, false);
        Lesson mon1 = placed(dayCourse, MON, 1, false);
        Lesson mon3 = placed(dayCourse, MON, 3, false);
        // visitingCourse is templated on Tuesday slot 0, so its Tuesday hour is ON-template;
        // dayCourse is not templated at all, so the Tuesday hour below is free.
        SchoolSchedule.setWeeklyTemplateStatic(
                Map.of(visitingCourse.id, Set.of(SchoolSchedule.encodeTemplateCell(TUE.getDayOfWeek(), 0))),
                Map.of(visitingCourse.id, 400));
        Lesson onTemplate = placed(visitingCourse, TUE, 0, false);
        Lesson free = placed(dayCourse, TUE, 2, false);

        HoleRepair.Result r = repair(mon0, mon1, mon3, onTemplate, free);

        assertEquals(0, r.gapSlotsAfter());
        assertEquals(1, r.closedByFreeImport());
        assertEquals(0, r.closedByTemplateImport());
        assertSame(byKey.get(MON + "@2"), free.timeslot, "the off-template hour moved");
        assertSame(byKey.get(TUE + "@0"), onTemplate.timeslot, "the template cell was left alone");
    }

    /**
     * TASK-419 review F4: the MIN-SLIDE is the LAST resort, below both imports. Here the hole
     * is reachable BOTH by sliding the day's own first lesson up and by importing an
     * on-template hour from another date - the import must win. The slide fills nothing (it
     * only re-shapes the day, leaving the group an hour shorter at the front) and can cost M18
     * by de-anchoring the day, while the import is priced at a bounded M32 weight and adds the
     * hour the day was missing.
     */
    @Test
    void prefersTheOnTemplateImportOverTheMinSlide() {
        Lesson mon0 = placed(dayCourse, MON, 0, false);
        Lesson mon2 = placed(dayCourse, MON, 2, false);
        Lesson mon3 = placed(dayCourse, MON, 3, false);
        // The only importable hour is ON its course's template cell (Tuesday slot 0), so the
        // free-import level finds nothing and the choice is exactly import-vs-slide.
        SchoolSchedule.setWeeklyTemplateStatic(
                Map.of(visitingCourse.id, Set.of(SchoolSchedule.encodeTemplateCell(TUE.getDayOfWeek(), 0))),
                Map.of(visitingCourse.id, 400));
        Lesson onTemplate = placed(visitingCourse, TUE, 0, false);

        HoleRepair.Result r = repair(mon0, mon2, mon3, onTemplate);

        assertEquals(1, r.gapSlotsBefore(), "Monday slot 1 is the one penalized hole");
        assertEquals(0, r.gapSlotsAfter());
        assertEquals(1, r.closedByTemplateImport(), "closed at level 2 (on-template import)");
        assertEquals(0, r.closedByMinSlide(), "the min-slide is now the LAST resort, not the second");
        assertSame(byKey.get(MON + "@1"), onTemplate.timeslot, "the imported hour landed in the hole");
        assertSame(byKey.get(MON + "@0"), mon0.timeslot, "the day's first lesson stayed anchored");
    }

    /**
     * TASK-576 F7-A: with the pair-cell guard armed and the pair template published, a
     * donor sitting on one of its pair's cells pays the exit surcharge, so between two
     * equally-priced free donors the pass spends the one carrying no form. The in-cell
     * donor holds the LOWER id, so pool order alone would pick IT - the assert is about
     * the guard flipping that choice, not about ids.
     */
    @Test
    void prefersTheOffPairCellDonorWhenTheGuardIsArmed() {
        Lesson mon0 = placed(dayCourse, MON, 0, false);
        Lesson mon1 = placed(dayCourse, MON, 1, false);
        Lesson mon3 = placed(dayCourse, MON, 3, false);
        Lesson inCell = placed(dayCourse, TUE, 0, false);  // lower id: wins by pool order alone
        Lesson offCell = placed(dayCourse, TUE, 2, false);
        System.setProperty(PairCellGuard.FLAG, "true");
        PairCellGuard.publish(Map.of(group, Map.of(TUE.getDayOfWeek(),
                List.of(new WeeklyTemplateBuilder.TemplateCell(dayCourse, 0, 1)))));
        try {
            HoleRepair.Result r = repair(mon0, mon1, mon3, inCell, offCell);
            assertEquals(0, r.gapSlotsAfter());
            assertEquals(1, r.closedByFreeImport());
            assertSame(byKey.get(MON + "@2"), offCell.timeslot, "the off-cell donor moved into the hole");
            assertSame(byKey.get(TUE + "@0"), inCell.timeslot, "the in-cell donor kept its pair cell");
        } finally {
            System.clearProperty(PairCellGuard.FLAG);
            PairCellGuard.clear();
        }
    }

    // ─── fixture helpers ───────────────────────────────────────────────────

    private HoleRepair.Result repair(Lesson... lessons) {
        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(group), List.of(dayTeacher, visitingTeacher),
                List.of(dayCourse, visitingCourse), List.of(lessons), List.of());
        HoleRepair.Result r = HoleRepair.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);
        assertEquals(0, r.conflictsAdded(), "the pass is conflict-free by construction");
        assertTrue(r.gapSlotsAfter() <= r.gapSlotsBefore(), "improve-or-noop");
        return r;
    }

    private Lesson placed(Course course, LocalDate date, int slot, boolean pinned) {
        return TestDataBuilder.lesson(nextId++, course, byKey.get(date + "@" + slot), null, pinned);
    }
}
