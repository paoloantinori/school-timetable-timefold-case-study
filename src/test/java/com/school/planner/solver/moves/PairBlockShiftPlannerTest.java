package com.school.planner.solver.moves;

import static com.school.planner.TestDataBuilder.course;
import static com.school.planner.TestDataBuilder.group;
import static com.school.planner.TestDataBuilder.lesson;
import static com.school.planner.TestDataBuilder.teacher;
import static com.school.planner.TestDataBuilder.timeslot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.PairCellGuard;
import com.school.planner.solver.WeeklyTemplateBuilder;

/**
 * TASK-576 F8: the pure planning logic of the pair-block shift. No Timefold coupling
 * here (the WeeklyTemplateBuilder discipline): the move/factory integration is covered
 * by the F8 bench on the laptop; these tests pin the mechanics the bench assumes.
 */
class PairBlockShiftPlannerTest {

    // Thursday of the school year's first week (school starts Mon 2026-09-14)
    private static final LocalDate THU = LocalDate.of(2026, 9, 17);
    private static final Teacher T = teacher(1L, "T16");
    private static final Teacher OTHER = teacher(2L, "T20");
    private static final StudentGroup G = group(1L, "G01", 15);

    private final Course pairCourse = course(1L, "Tecnologia", T, G, false);
    private final Course otherCourse = course(2L, "Merceologia", OTHER, G, false);

    @AfterEach
    void reset() {
        PairCellGuard.clear();
    }

    /** The pair's form: Thursday slots 2,3 (the block's home). */
    private void publishForm() {
        PairCellGuard.publish(Map.of(G, Map.of(DayOfWeek.THURSDAY, List.of(
                new WeeklyTemplateBuilder.TemplateCell(pairCourse, 2, 1),
                new WeeklyTemplateBuilder.TemplateCell(pairCourse, 3, 1)))));
    }

    private Timeslot slot(int id, int hour) {
        return timeslot(id, THU, hour, 0, hour + 1, 0);
    }

    // ── blockOf ──

    @Test
    void blockOf_collectsThePairsUnpinnedLessonsOfThatDateSlotOrdered() {
        Lesson a = lesson(1L, pairCourse, slot(1, 10), null);   // slot 2
        Lesson b = lesson(2L, pairCourse, slot(2, 11), null);   // slot 3
        Lesson c = lesson(3L, pairCourse, slot(3, 12), null);   // slot 4
        Lesson pinned = lesson(4L, pairCourse, slot(4, 8), null, true);
        Lesson otherPair = lesson(5L, otherCourse, slot(5, 13), null);
        List<Lesson> block = PairBlockShiftPlanner.blockOf(a, List.of(c, b, pinned, otherPair));
        // only the seed's pair, only that date, unpinned, slot-ordered (10,11,12 -> 2,3,4)
        assertEquals(List.of(a, b, c), block);
    }

    // ── targetsOf ──

    @Test
    void targetsOf_uniformSlideBackToTheForm_landsEveryMember() {
        publishForm();
        // the pair's block slid +1 (slots 3,4 instead of the form's 2,3). The SEED is the
        // off-form member (slot 4): its nearest form cell is 3, so k=+1, and every member
        // lands back one slot earlier, member a (slot 3) onto form cell 2.
        Lesson a = lesson(1L, pairCourse, slot(4, 11), null);   // slot 3 (still on a form cell)
        Lesson b = lesson(2L, pairCourse, slot(5, 12), null);   // slot 4 (off form: the seed)
        List<Timeslot> targets = PairBlockShiftPlanner.targetsOf(b, List.of(a, b),
                PairCellGuard.formCellsOf(b), PairBlockShiftPlanner.gridOf(List.of(slot(1, 8), slot(2, 9),
                        slot(3, 10), slot(4, 11), slot(5, 12), slot(6, 13))));
        assertEquals(2, targets.size());
        assertEquals(slot(3, 10), targets.get(0), "a lands back on form slot 2 (10:00)");
        assertEquals(slot(4, 11), targets.get(1), "b lands back on form slot 3 (11:00)");
    }

    @Test
    void targetsOf_nonUniformSlide_rejectsThePlan() {
        publishForm();
        // a slid +1 (slot 4, its form is 3): k=+1; but b sits on slot 6 whose landing
        // (6-1=5) is NOT a form cell: the block is not a uniform slide onto the form
        Lesson a = lesson(1L, pairCourse, slot(4, 12), null);   // slot 4
        Lesson b = lesson(2L, pairCourse, slot(6, 14), null);   // slot 6
        assertNull(PairBlockShiftPlanner.targetsOf(a, List.of(a, b), PairCellGuard.formCellsOf(a),
                PairBlockShiftPlanner.gridOf(List.of(slot(1, 8), slot(2, 9), slot(3, 10), slot(4, 11),
                        slot(5, 12), slot(6, 13), slot(7, 14)))));
    }

    @Test
    void targetsOf_landingOffTheGrid_rejectsThePlan() {
        // form on slots 4,5 (13:00,14:00) but the day's grid only opens 8..12 (slots 0..4):
        // the block slid to 2,3 and the shift back needs slot 5, which the calendar never opens
        PairCellGuard.publish(Map.of(G, Map.of(DayOfWeek.THURSDAY, List.of(
                new WeeklyTemplateBuilder.TemplateCell(pairCourse, 4, 1),
                new WeeklyTemplateBuilder.TemplateCell(pairCourse, 5, 1)))));
        Lesson a = lesson(1L, pairCourse, slot(1, 10), null);   // slot 2
        Lesson b = lesson(2L, pairCourse, slot(2, 11), null);   // slot 3
        assertNull(PairBlockShiftPlanner.targetsOf(a, List.of(a, b), PairCellGuard.formCellsOf(a),
                PairBlockShiftPlanner.gridOf(List.of(slot(1, 8), slot(2, 9), slot(3, 10), slot(4, 11),
                        slot(5, 12)))));
    }

    @Test
    void targetsOf_seedAlreadyOnForm_rejectsThePlan() {
        publishForm();
        Lesson a = lesson(1L, pairCourse, slot(3, 10), null);   // slot 2: ON the form
        assertNull(PairBlockShiftPlanner.targetsOf(a, List.of(a), PairCellGuard.formCellsOf(a),
                PairBlockShiftPlanner.gridOf(List.of(slot(3, 10), slot(4, 11)))));
    }

    // ── isCandidate ──

    @Test
    void isCandidate_requiresFormOffFormUnpinnedPlaced() {
        publishForm();
        Lesson off = lesson(1L, pairCourse, slot(5, 12), null);   // slot 4: off the {2,3} form
        assertTrue(PairBlockShiftPlanner.isCandidate(off));
        Lesson on = lesson(2L, pairCourse, slot(3, 10), null);    // slot 2: ON the form
        assertFalse(PairBlockShiftPlanner.isCandidate(on), "on-form is not this move's business");
        Lesson pinnedOff = lesson(3L, pairCourse, slot(5, 12), null, true);
        assertFalse(PairBlockShiftPlanner.isCandidate(pinnedOff));
        Lesson noForm = lesson(4L, otherCourse, slot(5, 12), null);
        assertFalse(PairBlockShiftPlanner.isCandidate(noForm), "T20's pair carries no cells here");
    }

    @Test
    void formCellsOf_emptyGuardMeansNoForm() {
        Lesson off = lesson(1L, pairCourse, slot(4, 11), null);
        assertTrue(PairCellGuard.formCellsOf(off).isEmpty());
        assertFalse(PairBlockShiftPlanner.isCandidate(off), "doubly inert without the builder");
    }
}
