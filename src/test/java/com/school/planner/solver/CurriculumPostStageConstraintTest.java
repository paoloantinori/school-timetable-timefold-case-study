package com.school.planner.solver;

import java.time.LocalDate;
import java.util.Map;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static com.school.planner.TestDataBuilder.course;
import static com.school.planner.TestDataBuilder.group;
import static com.school.planner.TestDataBuilder.lesson;
import static com.school.planner.TestDataBuilder.teacher;
import static com.school.planner.TestDataBuilder.timeslot;

/**
 * M-CPS curriculum post-stage (TASK-469, bucket C3/T4): DEFAULT ON since the 2026-08-01
 * ratification (maintainer), opt-OUT via the literal "false". These tests lock the load-bearing
 * behaviours so the opt-out path stays inert (never silently changes a score), and the ON
 * path realises maintainer's decided semantics: asymmetric target ~6h, full weight on the deficit,
 * weak weight on the excess, with the G11 cautela built into the weak side.
 *
 * <p>The flag ({@code planner.constraints.curriculum-poststage.enabled}) and the two weight
 * knobs ({@code ...full-weight}, {@code ...weak-weight}) are all read at constraint BUILD
 * time (M-TR/M-TC build-once pattern), so each test builds a fresh {@link
 * ConstraintVerifier} with the system properties set or cleared BEFORE the build. The
 * per-group stage-end is the INPUT-derived {@link SchoolSchedule#lastStageDateFor} map,
 * published here via {@link SchoolSchedule#setStageEndByGroupStatic} (in production it is
 * published by ScheduleService.refreshSolverStatics from DailyActivityQuota.stageHours).
 *
 * <p>Asymmetric design note (why {@code penalizesBy} carries the weight here, unlike M-TR):
 * M-TR puts its single weight in {@code ofMedium(...)} and keeps the match weight weight-free,
 * so {@code penalizesBy} is the pure impact. M-CPS has TWO weights (full for deficit, weak for
 * excess) that a single {@code ofMedium} cannot express, so both live inside the match weighter.
 * {@code penalizesBy} therefore equals the weighted penalty (deficit x full-weight or excess x
 * weak-weight), and {@code getScore} mirrors it at {@code ONE_MEDIUM} (weight 1).
 *
 * <p>Known limitation locked by {@link #flagOn_zeroPostStageIsBlindDocumentedLimitation()}: the
 * groupBy is lesson-driven, so a perimeter group with ZERO post-stage curriculum hours produces
 * no match and its deficit stays invisible to the constraint. The golden always has &ge;1
 * post-stage hour per perimeter group, so the residual measurement (vs baseline 6/5/29) is valid;
 * the proper fix needs a per-group fact driver (M20/M30.2 shape) and is out of scope for the
 * prototype.
 */
class CurriculumPostStageConstraintTest {

    // Stage-end dates used as the INPUT-derived map values (the constraint only needs a cutoff
    // to define pre/post; the exact value is arbitrary for the unit test).
    private static final LocalDate STAGE_END_G10 = LocalDate.of(2027, 4, 23);
    private static final LocalDate STAGE_END_G08 = LocalDate.of(2027, 4, 26);
    private static final LocalDate STAGE_END_G11 = LocalDate.of(2027, 3, 19);
    private static final LocalDate POST = LocalDate.of(2027, 5, 10); // after every stage end
    private static final LocalDate PRE = LocalDate.of(2027, 3, 1);   // before every stage end

    // Perimeter groups (final-year cohorts) + a non-perimeter control.
    private static final StudentGroup G_G10 = group(1L, "G10", 20);
    private static final StudentGroup G_G08 = group(2L, "G08", 15);
    private static final StudentGroup G_G11 = group(3L, "G11", 20);
    private static final StudentGroup G_G02 = group(4L, "G02", 20);

    private static final Teacher T11 = teacher(10L, "T11");

    private ConstraintVerifier<SchoolScheduleConstraintProvider, SchoolSchedule> build(boolean enabled) {
        return build(enabled, null, null);
    }

    private ConstraintVerifier<SchoolScheduleConstraintProvider, SchoolSchedule> build(
            boolean enabled, Integer fullWeight, Integer weakWeight) {
        if (enabled) {
            System.setProperty("planner.constraints.curriculum-poststage.enabled", "true");
        } else {
            // Explicit opt-OUT: since the 2026-08-01 ratification the default is ON
            // (null/absent => enabled), so opting out requires the literal "false".
            System.setProperty("planner.constraints.curriculum-poststage.enabled", "false");
        }
        setOrClear("planner.constraints.curriculum-poststage.full-weight", fullWeight);
        setOrClear("planner.constraints.curriculum-poststage.weak-weight", weakWeight);
        // Publish the INPUT-derived per-group stage-end (mirrors what refreshSolverStatics does
        // from DailyActivityQuota.stageHours in production).
        SchoolSchedule.setStageEndByGroupStatic(Map.of(
                G_G10.id, STAGE_END_G10,
                G_G08.id, STAGE_END_G08,
                G_G11.id, STAGE_END_G11));
        return ConstraintVerifier.build(
                new SchoolScheduleConstraintProvider(), SchoolSchedule.class, Lesson.class);
    }

    private static void setOrClear(String key, Integer value) {
        if (value != null) {
            System.setProperty(key, String.valueOf(value));
        } else {
            System.clearProperty(key);
        }
    }

    @AfterEach
    void reset() {
        System.clearProperty("planner.constraints.curriculum-poststage.enabled");
        System.clearProperty("planner.constraints.curriculum-poststage.full-weight");
        System.clearProperty("planner.constraints.curriculum-poststage.weak-weight");
        SchoolSchedule.setStageEndByGroupStatic(Map.of());
    }

    private Timeslot slot(long id, LocalDate date, int hour) {
        return timeslot(id, date, hour, 0, hour + 1, 0);
    }

    private Lesson post(Course c, long id) {
        return lesson(id, c, slot(id, POST, 8), null);
    }

    private Lesson pre(Course c, long id) {
        return lesson(id, c, slot(id, PRE, 8), null);
    }

    // ── explicit opt-OUT (literal "false") must be inert even at deficit ──

    @Test
    void optedOut_isInertEvenAtDeficit() {
        var v = build(false);
        Course c = course(1L, "Comportamento sociale", T11, G_G10, false);
        // 4 post-stage Comportamento sociale lessons for G10: would cost deficit 2 x 100
        // = 200, but the constraint is explicitly opted out so it is inert (0 matches).
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(post(c, 1), post(c, 2), post(c, 3), post(c, 4))
                .penalizesBy(0);
    }

    // ── deficit: full weight (default 100) ──

    @Test
    void flagOn_deficitPenalizesFullWeight() {
        var v = build(true);
        Course c = course(1L, "Comportamento sociale", T11, G_G10, false);
        Lesson[] given = { post(c, 1), post(c, 2), post(c, 3), post(c, 4) };
        // 4 post-stage hours for G10 -> deficit (6-4)=2 x full-weight 100 = 200.
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(given).penalizesBy(200);
        BendableScore impact = v
                .verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(given).getScore();
        assertEquals(ScoreCompat.ofMedium(-200), impact,
                "4 post-stage hours: deficit 2 x full-weight 100 = -200 medium");
    }

    // ── excess: weak weight (default 1), the G11 cautela ──

    @Test
    void flagOn_excessPenalizesWeakWeight() {
        var v = build(true);
        Course c = course(1L, "Personalizzazione", T11, G_G11, false);
        Lesson[] given = { post(c, 1), post(c, 2), post(c, 3), post(c, 4),
                post(c, 5), post(c, 6), post(c, 7), post(c, 8) };
        // 8 post-stage hours for G11 -> excess (8-6)=2 x weak-weight 1 = 2 (the cautela:
        // G11 runs Personalizzazione to 29h post-stage, so the excess is barely penalized).
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(given).penalizesBy(2);
        BendableScore impact = v
                .verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(given).getScore();
        assertEquals(ScoreCompat.ofMedium(-2), impact,
                "8 post-stage hours: excess 2 x weak-weight 1 = -2 medium");
    }

    @Test
    void flagOn_atTargetNoPenalty() {
        var v = build(true);
        Course c = course(1L, "Comportamento sociale", T11, G_G10, false);
        // Exactly 6 post-stage hours -> the target centred (G10 golden = 6h exactly).
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(post(c, 1), post(c, 2), post(c, 3), post(c, 4), post(c, 5), post(c, 6))
                .penalizesBy(0);
    }

    // ── weight knobs drive each side of the asymmetry ──

    @Test
    void flagOn_fullWeightKnobScalesDeficit() {
        var v = build(true, 50, null);
        Course c = course(1L, "Comportamento sociale", T11, G_G10, false);
        Lesson[] given = { post(c, 1), post(c, 2), post(c, 3), post(c, 4) };
        // full-weight 50, deficit 2 -> 100. Proves the full-weight knob drives the deficit.
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(given).penalizesBy(100);
        BendableScore impact = v
                .verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(given).getScore();
        assertEquals(ScoreCompat.ofMedium(-100), impact,
                "full-weight 50 x deficit 2 = -100 medium");
    }

    @Test
    void flagOn_weakWeightKnobScalesExcess() {
        var v = build(true, null, 5);
        Course c = course(1L, "Personalizzazione", T11, G_G11, false);
        Lesson[] given = { post(c, 1), post(c, 2), post(c, 3), post(c, 4),
                post(c, 5), post(c, 6), post(c, 7), post(c, 8) };
        // weak-weight 5, excess 2 -> 10. Proves the weak-weight knob drives the excess
        // (the cautela lever: raise this if G11's volume distorts the solve).
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(given).penalizesBy(10);
        BendableScore impact = v
                .verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(given).getScore();
        assertEquals(ScoreCompat.ofMedium(-10), impact,
                "weak-weight 5 x excess 2 = -10 medium");
    }

    // ── G08 sums BOTH curriculum subjects per group ──

    @Test
    void flagOn_twoFCSumsBothCurriculumSubjects() {
        var v = build(true);
        Course competenze = course(1L, "Competenze di cittadinanza", T11, G_G08, false);
        Course civica = course(2L, "Educazione civica", T11, G_G08, false);
        // 3 + 2 = 5 post-stage curriculum hours for G08 -> deficit (6-5)=1 x 100 = 100.
        // Proves the per-group count sums the group's whole curriculum subject set, matching
        // the baseline (G08 golden = 5h across both subjects).
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(post(competenze, 1), post(competenze, 2), post(competenze, 3),
                        post(civica, 4), post(civica, 5))
                .penalizesBy(100);
    }

    // ── pre-stage lessons are excluded from the count ──

    @Test
    void flagOn_preStageLessonsExcludedFromCount() {
        var v = build(true);
        Course c = course(1L, "Comportamento sociale", T11, G_G10, false);
        // 4 post-stage + 5 PRE-stage Comportamento sociale: the 5 pre-stage hours are before
        // stage-end, so the post-stage count stays 4 (deficit 2 x 100 = 200). Proves the
        // date filter does not let pre-stage curriculum dilute the post-stage deficit.
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(post(c, 1), post(c, 2), post(c, 3), post(c, 4),
                        pre(c, 5), pre(c, 6), pre(c, 7), pre(c, 8), pre(c, 9))
                .penalizesBy(200);
    }

    @Test
    void flagOn_zeroPostStageIsBlindDocumentedLimitation() {
        var v = build(true);
        Course c = course(1L, "Comportamento sociale", T11, G_G10, false);
        // 0 post-stage + 4 PRE-stage: the intended deficit is 6 (0 placed post-stage), but the
        // lesson-driven groupBy surfaces no match for a group with zero post-stage hours, so the
        // constraint scores 0. This LOCKS the known blind spot documented on M-CPS (the golden
        // always has >=1 post-stage hour per perimeter group, so the residual measurement is
        // still valid; the per-group-fact cure is out of scope for the prototype).
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(pre(c, 1), pre(c, 2), pre(c, 3), pre(c, 4))
                .penalizesBy(0);
    }

    // ── subject-name normalization: Title-Case at runtime matches the lowercase seed literal ──

    @Test
    void flagOn_titleCaseSubjectMatchesViaNormalization() {
        var v = build(true);
        // The run carries Title-Case subjects ("Comportamento Sociale") while the seed-CSV
        // perimeter literal is lowercase ("Comportamento sociale"): both normalize to the
        // canonical form, so the perimeter matches either. Locks the SUBJECT_CASE_NORMALIZATION
        // fix - without it G10's Comportamento Sociale was blind in real runs (run3 measure).
        Course c = course(1L, "Comportamento Sociale", T11, G_G10, false);
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(post(c, 1), post(c, 2), post(c, 3), post(c, 4))
                .penalizesBy(200);
    }

    // ── exemptions: non-perimeter group, non-curriculum subject, pseudo-teacher ──

    @Test
    void flagOn_nonPerimeterGroupExempt() {
        var v = build(true);
        // G02 also has Comportamento sociale (T11), but only the final-year cohorts are
        // in scope (the note: "II FC E III OIT E III FA"). G02 is NOT a perimeter group.
        Course c = course(1L, "Comportamento sociale", T11, G_G02, false);
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(post(c, 1), post(c, 2), post(c, 3), post(c, 4))
                .penalizesBy(0);
    }

    @Test
    void flagOn_nonCurriculumSubjectExempt() {
        var v = build(true);
        // Matematica is not a curriculum subject for G10, even post-stage.
        Course c = course(1L, "Matematica", T11, G_G10, false);
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(post(c, 1), post(c, 2), post(c, 3), post(c, 4))
                .penalizesBy(0);
    }

    @Test
    void flagOn_pseudoTeacherExempt() {
        var v = build(true);
        Teacher stage = teacher(99L, "Stage", true); // pseudo
        Course c = course(1L, "Comportamento sociale", stage, G_G10, false);
        // Pseudo-teacher lessons (synthetic Stage) are excluded, M21/M-TR precedent.
        v.verifyThat(SchoolScheduleConstraintProvider::curriculumPostStage)
                .given(post(c, 1), post(c, 2), post(c, 3), post(c, 4))
                .penalizesBy(0);
    }
}
