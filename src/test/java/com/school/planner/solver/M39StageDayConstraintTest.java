package com.school.planner.solver;

import static com.school.planner.TestDataBuilder.course;
import static com.school.planner.TestDataBuilder.group;
import static com.school.planner.TestDataBuilder.lesson;
import static com.school.planner.TestDataBuilder.teacher;
import static com.school.planner.TestDataBuilder.timeslot;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-604: the M39 stage-day preference verifier. Same ConstraintVerifier shape as
 * {@code PairCellDeviationConstraintTest}/{@code TheoryAdjacentConstraintTest}: the
 * flag and weight are read at constraint BUILD time, so each case builds a fresh
 * verifier with the properties set first, and the quota statics are published per
 * case (the constraint reads {@code getExpectedHoursRow}, whose map
 * {@code setDailyActivityQuotasStatic} rebuilds). The access-or-die case is the
 * /simplify finding: a NON-quota-flag group (FCQuota=false, e.g. G09) must be
 * VISIBLE to the constraint - the first draft read the FC-only map and lost 78% of
 * the target population.
 */
class M39StageDayConstraintTest {

    private static final String FLAG = "planner.constraints.m39-stage-day.enabled";
    private static final String WEIGHT = "planner.m39.weight";
    private static final LocalDate STAGE_DAY = LocalDate.of(2027, 5, 28);
    private static final LocalDate ORDINARY_DAY = LocalDate.of(2027, 5, 27);
    private static final Teacher T = teacher(1L, "T04");
    private static final StudentGroup G = group(7L, "G09", 15);

    @AfterEach
    void reset() {
        System.clearProperty(FLAG);
        System.clearProperty(WEIGHT);
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
    }

    private ConstraintVerifier<SchoolScheduleConstraintProvider, SchoolSchedule> build(
            boolean enabled, Integer weight, List<DailyActivityQuota> quotas) {
        if (enabled) {
            System.setProperty(FLAG, "true");
        } else {
            System.clearProperty(FLAG);
        }
        if (weight != null) {
            System.setProperty(WEIGHT, String.valueOf(weight));
        } else {
            System.clearProperty(WEIGHT);
        }
        SchoolSchedule.setDailyActivityQuotasStatic(quotas);
        return ConstraintVerifier.build(
                new SchoolScheduleConstraintProvider(), SchoolSchedule.class, Lesson.class);
    }

    /** The 2027 tail rows: STAGE_DAY is stage-only (7h stage, 0 ordinary), ORDINARY_DAY aula. */
    private List<DailyActivityQuota> tailRows() {
        return List.of(
                new DailyActivityQuota(G, STAGE_DAY, 0, 0, 7, false),
                new DailyActivityQuota(G, ORDINARY_DAY, 6, 0, 0, false));
    }

    @Test
    void inertWhenFlagOff() {
        Course c = course(1L, "Lingua italiana", T, G);
        Lesson onStage = lesson(1L, c, timeslot(1L, STAGE_DAY, 8, 0, 9, 0), null);
        build(false, null, tailRows())
                .verifyThat(BaseConstraintProvider::ordinaryOnStageOnlyDay)
                .given(onStage)
                .penalizesBy(0);
    }

    @Test
    void nonQuotaFlagGroupFiresOnStageOnlyDay() {
        // THE /simplify finding pinned: FCQuota=false group MUST be visible. The first
        // draft read getDailyQuota (FC-only) and this case would have scored 0.
        Course c = course(1L, "Lingua italiana", T, G);
        Lesson onStage = lesson(1L, c, timeslot(1L, STAGE_DAY, 8, 0, 9, 0), null);
        var v = build(true, null, tailRows());
        v.verifyThat(BaseConstraintProvider::ordinaryOnStageOnlyDay)
                .given(onStage)
                .penalizesBy(1);
        BendableScore impact = v
                .verifyThat(BaseConstraintProvider::ordinaryOnStageOnlyDay)
                .given(onStage)
                .getScore();
        assertEquals(ScoreCompat.ofMedium(-100), impact,
                "one ordinary hour on a stage-only day costs -100 medium (default weight)");
    }

    @Test
    void ordinaryDeclaredDayFree() {
        Course c = course(1L, "Lingua italiana", T, G);
        Lesson onOrdinary = lesson(1L, c, timeslot(1L, ORDINARY_DAY, 8, 0, 9, 0), null);
        build(true, null, tailRows())
                .verifyThat(BaseConstraintProvider::ordinaryOnStageOnlyDay)
                .given(onOrdinary)
                .penalizesBy(0);
    }

    @Test
    void authoritativePinExempt() {
        Course c = course(1L, "Lingua italiana", T, G);
        Lesson pinned = lesson(1L, c, timeslot(1L, STAGE_DAY, 8, 0, 9, 0), null);
        pinned.pinned = true;
        build(true, null, tailRows())
                .verifyThat(BaseConstraintProvider::ordinaryOnStageOnlyDay)
                .given(pinned)
                .penalizesBy(0);
    }

    @Test
    void pseudoTeacherExempt() {
        Teacher stage = teacher(2L, "Stage");
        stage.pseudo = true;
        Course c = course(2L, "Stage", stage, G);
        Lesson pseudoLesson = lesson(1L, c, timeslot(1L, STAGE_DAY, 8, 0, 9, 0), null);
        build(true, null, tailRows())
                .verifyThat(BaseConstraintProvider::ordinaryOnStageOnlyDay)
                .given(pseudoLesson)
                .penalizesBy(0);
    }

    @Test
    void weightKnobHonored() {
        Course c = course(1L, "Lingua italiana", T, G);
        Lesson onStage = lesson(1L, c, timeslot(1L, STAGE_DAY, 8, 0, 9, 0), null);
        var v = build(true, 500, tailRows());
        BendableScore impact = v
                .verifyThat(BaseConstraintProvider::ordinaryOnStageOnlyDay)
                .given(onStage)
                .getScore();
        assertEquals(ScoreCompat.ofMedium(-500), impact,
                "planner.m39.weight=500 multiplies the single match to -500 medium");
    }
}
