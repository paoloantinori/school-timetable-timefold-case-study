package com.school.planner.solver;

import static com.school.planner.TestDataBuilder.course;
import static com.school.planner.TestDataBuilder.group;
import static com.school.planner.TestDataBuilder.lesson;
import static com.school.planner.TestDataBuilder.teacher;
import static com.school.planner.TestDataBuilder.timeslot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-576 F7-C: the M38 pair-cell deviation constraint, the LS-side defender of the
 * pair-cells form (E2 measured the erosion living in the LS phases, not the repairs).
 * Same ConstraintVerifier shape as {@code TheoryAdjacentConstraintTest}: the flag and
 * the weight knob are read at constraint BUILD time, so each case builds a fresh
 * verifier with the properties set first; the PairCellGuard's published set is the
 * second gate and is published/cleared per case (the constraint must be doubly inert:
 * flag OFF, or no published pairs).
 */
class PairCellDeviationConstraintTest {

    private static final String FLAG = "planner.constraints.pair-deviation.enabled";
    private static final String WEIGHT = "planner.constraints.pair-deviation.weight";
    private static final LocalDate DAY = LocalDate.of(2026, 9, 17);
    private static final Teacher T = teacher(1L, "T16");
    private static final StudentGroup G = group(1L, "G01", 15);

    @AfterEach
    void reset() {
        System.clearProperty(FLAG);
        System.clearProperty(WEIGHT);
        PairCellGuard.clear();
    }

    private ConstraintVerifier<SchoolScheduleConstraintProvider, SchoolSchedule> build(boolean enabled) {
        return build(enabled, null);
    }

    private ConstraintVerifier<SchoolScheduleConstraintProvider, SchoolSchedule> build(boolean enabled, Integer weight) {
        // Opt-out since the 2026-09-02 ratification (weight 1): true means the DEFAULT
        // (cleared property), false means the explicit "false". Testing both paths matters.
        if (enabled) {
            System.clearProperty(FLAG);
        } else {
            System.setProperty(FLAG, "false");
        }
        if (weight != null) {
            System.setProperty(WEIGHT, String.valueOf(weight));
        } else {
            System.clearProperty(WEIGHT);
        }
        return ConstraintVerifier.build(
                new SchoolScheduleConstraintProvider(), SchoolSchedule.class, Lesson.class);
    }

    /** The pair form: one cell on DAY slot 0 (hour 8) for (T, G)'s course. */
    private void publishOneCell(Course c) {
        PairCellGuard.publish(Map.of(G, Map.of(DayOfWeek.THURSDAY,
                List.of(new WeeklyTemplateBuilder.TemplateCell(c, 0, 1)))));
    }

    private Timeslot slot(long id, int hour) {
        return timeslot(id, DAY, hour, 0, hour + 1, 0);
    }

    // ── doubly inert: explicitly opted out (even with the form published), or no published pairs ──

    @Test
    void explicitOptOut_isInertEvenWithTheFormPublished() {
        // Pins the opt-OUT form of the 2026-09-02 ratification (weight 1, default ON):
        // only the literal "false" disarms; the form being published changes nothing
        // when the constraint is explicitly off.
        PairCellGuard.publish(Map.of(G, Map.of(DayOfWeek.THURSDAY,
                List.of(new WeeklyTemplateBuilder.TemplateCell(course(1L, "Tecnologia", T, G, false), 0, 1)))));
        var v = build(false);
        Course c = course(1L, "Tecnologia", T, G, false);
        v.verifyThat(SchoolScheduleConstraintProvider::pairCellDeviation)
                .given(lesson(1L, c, slot(2, 10), null))   // off-form, but the flag is explicitly "false" (opt-out)
                .penalizesBy(0);
    }

    @Test
    void flagOn_noPublishedPairs_isInert() {
        var v = build(true);
        Course c = course(1L, "Tecnologia", T, G, false);
        v.verifyThat(SchoolScheduleConstraintProvider::pairCellDeviation)
                .given(lesson(1L, c, slot(2, 10), null))   // off-form, but no pair owns cells
                .penalizesBy(0);
    }

    // ── the charge itself ──

    @Test
    void flagOn_offCellLessonOfAPairWithForm_paysTheDefaultWeight() {
        Course c = course(1L, "Tecnologia", T, G, false);
        publishOneCell(c);
        var v = build(true);
        BendableScore gotDefault = v.verifyThat(SchoolScheduleConstraintProvider::pairCellDeviation)
                .given(lesson(1L, c, slot(2, 10), null))   // slot 2: NOT the pair's cell 0
                .getScore();
        // mech (laptop, house pattern): community Timefold penalizesBy is match-COUNT;
        // weights assert via getScore (LabTagCapacityConstraintTest precedent).
        assertEquals(ScoreCompat.ofMedium(-1), gotDefault, "default weight 1 (2026-09-02 ratification) x 1 off-cell lesson");
    }

    @Test
    void flagOn_inCellLesson_paysNothing() {
        Course c = course(1L, "Tecnologia", T, G, false);
        publishOneCell(c);
        var v = build(true);
        v.verifyThat(SchoolScheduleConstraintProvider::pairCellDeviation)
                .given(lesson(1L, c, slot(1, 8), null))    // hour 8 = slot 0: the pair's own cell
                .penalizesBy(0);
    }

    @Test
    void flagOn_pairWithoutCells_isNeverCharged() {
        // the form belongs to (T, G) via course 1; a DIFFERENT course's pair has no cells,
        // so its off-form lesson is not this constraint's business (self-gating (b))
        Course withForm = course(1L, "Tecnologia", T, G, false);
        publishOneCell(withForm);
        Teacher other = teacher(2L, "T20");
        Course noForm = course(2L, "Merceologia", other, G, false);
        var v = build(true);
        v.verifyThat(SchoolScheduleConstraintProvider::pairCellDeviation)
                .given(lesson(2L, noForm, slot(2, 10), null))
                .penalizesBy(0);
    }

    @Test
    void flagOn_otherPairsCellInTheSameGroup_paysTheSwap() {
        // PAIR-keyed vs group-keyed (code review 2026-09-01 MEDIUM, the discriminating
        // case): (T, G) owns the DAY-slot-0 cell; a lesson of ANOTHER teacher's pair of
        // the SAME group sitting on that cell is OFF its own form - the intra-group swap
        // is exactly the LS move class M38 defends, so it must pay.
        Course withForm = course(1L, "Tecnologia", T, G, false);
        publishOneCell(withForm);
        Teacher other = teacher(2L, "T20");
        Course otherPair = course(2L, "Merceologia", other, G, false);
        // give the other pair its own form too, so hasForm passes and only protects decides
        PairCellGuard.publish(Map.of(G, Map.of(DayOfWeek.THURSDAY, List.of(
                new WeeklyTemplateBuilder.TemplateCell(withForm, 0, 1),
                new WeeklyTemplateBuilder.TemplateCell(otherPair, 2, 1)))));
        var v = build(true);
        // T20's lesson on T16's cell 0: off ITS form (its cell is slot 2) -> pays.
        // mech (laptop, house pattern): penalizesBy is match-COUNT in community Timefold;
        // weights assert via getScore, same as the sibling tests above.
        BendableScore t20 = v.verifyThat(SchoolScheduleConstraintProvider::pairCellDeviation)
                .given(lesson(2L, otherPair, slot(1, 8), null))
                .getScore();
        assertEquals(ScoreCompat.ofMedium(-1), t20, "T20 on T16's cell pays the swap");
        // and T16's lesson on T20's cell 2 pays symmetrically
        BendableScore t16 = v.verifyThat(SchoolScheduleConstraintProvider::pairCellDeviation)
                .given(lesson(1L, withForm, slot(3, 10), null))
                .getScore();
        assertEquals(ScoreCompat.ofMedium(-1), t16, "T16 on T20's cell pays the swap");
    }

    @Test
    void flagOn_weightKnobScalesTheScore() {
        Course c = course(1L, "Tecnologia", T, G, false);
        publishOneCell(c);
        var v = build(true, 2000);
        BendableScore gotKnob = v.verifyThat(SchoolScheduleConstraintProvider::pairCellDeviation)
                .given(lesson(1L, c, slot(2, 10), null))
                .getScore();
        // mech (laptop, house pattern): same as above, knob 2000.
        assertEquals(ScoreCompat.ofMedium(-2000), gotKnob, "knob weight 2000 x 1 off-cell lesson");
    }
}
