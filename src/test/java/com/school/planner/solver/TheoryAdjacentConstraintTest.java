package com.school.planner.solver;

import java.time.LocalDate;

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
 * M-TR theory-adjacent (TASK-468, tab "Vincoli DA ANALIZZARE"): flag-gated DEFAULT
 * OFF prototype. These tests lock the load-bearing behaviours so the flag default-OFF
 * never silently changes a score, and the ON path penalizes the golden's measured
 * 432-day "stretta" residual (AC#1) at the soglia-1 shape the design chose.
 *
 * <p>The flag ({@code planner.constraints.theory-adjacent.enabled}) and the weight knob
 * ({@code planner.constraints.theory-adjacent.weight}) are both read at constraint BUILD
 * time (M-LF/M-TC build-once pattern), so each test builds a fresh {@link
 * ConstraintVerifier} with the system properties set or cleared BEFORE the build, rather
 * than reusing a single injected verifier that captured the default-OFF flag at startup.
 */
class TheoryAdjacentConstraintTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 17);
    private static final LocalDate OTHER_DAY = LocalDate.of(2026, 9, 18);
    private static final Teacher T = teacher(1L, "T03");
    private static final StudentGroup G = group(1L, "G04", 15);

    private ConstraintVerifier<SchoolScheduleConstraintProvider, SchoolSchedule> build(boolean enabled) {
        return build(enabled, null);
    }

    private ConstraintVerifier<SchoolScheduleConstraintProvider, SchoolSchedule> build(boolean enabled, Integer weight) {
        if (enabled) {
            System.setProperty("planner.constraints.theory-adjacent.enabled", "true");
        } else {
            System.clearProperty("planner.constraints.theory-adjacent.enabled");
        }
        if (weight != null) {
            System.setProperty("planner.constraints.theory-adjacent.weight", String.valueOf(weight));
        } else {
            System.clearProperty("planner.constraints.theory-adjacent.weight");
        }
        return ConstraintVerifier.build(
                new SchoolScheduleConstraintProvider(), SchoolSchedule.class, Lesson.class);
    }

    @AfterEach
    void reset() {
        System.clearProperty("planner.constraints.theory-adjacent.enabled");
        System.clearProperty("planner.constraints.theory-adjacent.weight");
    }

    private Timeslot slot(long id, LocalDate date, int hour) {
        return timeslot(id, date, hour, 0, hour + 1, 0);
    }

    // ── flag default-OFF must be byte-identical to OFF ──

    @Test
    void flagOff_isInertEvenWhenTheorySubjectRepeated() {
        var v = build(false);
        Course c = course(1L, "Scienze", T, G, false);
        // 3 Scienze lessons same group/day: would cost excess 2 at soglia-1, but the
        // flag is OFF so the constraint is inert (0 matches). Regression guard: the
        // default must not change any score.
        v.verifyThat(SchoolScheduleConstraintProvider::theorySubjectRepeatedSameDay)
                .given(lesson(1L, c, slot(1, DAY, 8), null),
                        lesson(2L, c, slot(2, DAY, 9), null),
                        lesson(3L, c, slot(3, DAY, 10), null))
                .penalizesBy(0);
    }

    // ── soglia-1 shape: excess = hours - 1, weight default 1 ──

    @Test
    void flagOn_twoTheorySameDay_penalizesOneUnitAtDefaultWeight() {
        var v = build(true);
        Course c = course(1L, "Scienze", T, G, false);
        // 2 Scienze same day -> excess (2-1)=1. penalizesBy is the impact sum (=1);
        // getScore pins the weight: default 1 x excess 1 = -1 medium.
        v.verifyThat(SchoolScheduleConstraintProvider::theorySubjectRepeatedSameDay)
                .given(lesson(1L, c, slot(1, DAY, 8), null),
                        lesson(2L, c, slot(2, DAY, 9), null))
                .penalizesBy(1);
        BendableScore impact = v
                .verifyThat(SchoolScheduleConstraintProvider::theorySubjectRepeatedSameDay)
                .given(lesson(1L, c, slot(1, DAY, 8), null),
                        lesson(2L, c, slot(2, DAY, 9), null))
                .getScore();
        assertEquals(ScoreCompat.ofMedium(-1), impact,
                "2 same-subject theory hours same day cost -1 medium (excess 1 x weight 1)");
    }

    @Test
    void flagOn_threeTheorySameDay_penalizesTwoUnits() {
        var v = build(true);
        Course c = course(1L, "Scienze", T, G, false);
        // 3 Scienze same day -> excess (3-1)=2 at soglia-1 (NOT 1 as M21's soglia-2 would).
        v.verifyThat(SchoolScheduleConstraintProvider::theorySubjectRepeatedSameDay)
                .given(lesson(1L, c, slot(1, DAY, 8), null),
                        lesson(2L, c, slot(2, DAY, 9), null),
                        lesson(3L, c, slot(3, DAY, 10), null))
                .penalizesBy(2);
    }

    @Test
    void flagOn_weightKnobScalesTheScoreNotTheImpact() {
        var v = build(true, 7);
        Course c = course(1L, "Scienze", T, G, false);
        // weight 7, 2 same-day theory hours: impact stays 1 (soglia-1 is weight-free),
        // score becomes 7 x 1 = -7. Proves the knob drives the weight.
        v.verifyThat(SchoolScheduleConstraintProvider::theorySubjectRepeatedSameDay)
                .given(lesson(1L, c, slot(1, DAY, 8), null),
                        lesson(2L, c, slot(2, DAY, 9), null))
                .penalizesBy(1);
        BendableScore impact = v
                .verifyThat(SchoolScheduleConstraintProvider::theorySubjectRepeatedSameDay)
                .given(lesson(1L, c, slot(1, DAY, 8), null),
                        lesson(2L, c, slot(2, DAY, 9), null))
                .getScore();
        assertEquals(ScoreCompat.ofMedium(-7), impact,
                "weight knob 7 x excess 1 = -7 medium, impact unaffected");
    }

    // ── single lesson / different subject / different day = no penalty ──

    @Test
    void flagOn_singleTheoryDay_noPenalty() {
        var v = build(true);
        Course c = course(1L, "Scienze", T, G, false);
        v.verifyThat(SchoolScheduleConstraintProvider::theorySubjectRepeatedSameDay)
                .given(lesson(1L, c, slot(1, DAY, 8), null))
                .penalizesBy(0);
    }

    @Test
    void flagOn_differentSubjectsSameDay_noPenalty() {
        var v = build(true);
        Course scienze = course(1L, "Scienze", T, G, false);
        Course mate = course(2L, "Matematica", T, G, false);
        // Two DIFFERENT theory subjects same day: the group key (subject) separates them,
        // so neither reaches the 2h threshold. "No 2 ore vicine della STESSA materia".
        v.verifyThat(SchoolScheduleConstraintProvider::theorySubjectRepeatedSameDay)
                .given(lesson(1L, scienze, slot(1, DAY, 8), null),
                        lesson(2L, mate, slot(2, DAY, 9), null))
                .penalizesBy(0);
    }

    @Test
    void flagOn_sameSubjectDifferentDays_noPenalty() {
        var v = build(true);
        Course c = course(1L, "Scienze", T, G, false);
        // Same subject, different DATE: the group key (date) separates them.
        v.verifyThat(SchoolScheduleConstraintProvider::theorySubjectRepeatedSameDay)
                .given(lesson(1L, c, slot(1, DAY, 8), null),
                        lesson(2L, c, slot(2, OTHER_DAY, 8), null))
                .penalizesBy(0);
    }

    // ── exemptions: lab, practical-technical, pseudo-teacher ──

    @Test
    void flagOn_labSubjectExempt() {
        var v = build(true);
        Course c = course(1L, "Informatica", T, G, true); // isLab=true
        // The tab: "i laboratori, compreso disegno o informatica, possono avere piu' ore
        // vicine". A lab subject repeated same day is exempt (Lesson.isLab filter).
        v.verifyThat(SchoolScheduleConstraintProvider::theorySubjectRepeatedSameDay)
                .given(lesson(1L, c, slot(1, DAY, 8), null),
                        lesson(2L, c, slot(2, DAY, 9), null),
                        lesson(3L, c, slot(3, DAY, 10), null))
                .penalizesBy(0);
    }

    @Test
    void flagOn_practicalTechnicalSubjectExempt() {
        var v = build(true);
        Course c = course(1L, "Disegno", T, G, false); // isLab=false but practical-technical
        // "Disegno" is in THEORY_RULE_PRACTICAL_EXEMPT (the tab's "compreso disegno"
        // clause): even though it is not lab-tagged, multi-hour same-day blocks are allowed.
        v.verifyThat(SchoolScheduleConstraintProvider::theorySubjectRepeatedSameDay)
                .given(lesson(1L, c, slot(1, DAY, 8), null),
                        lesson(2L, c, slot(2, DAY, 9), null))
                .penalizesBy(0);
    }

    @Test
    void flagOn_pseudoTeacherExempt() {
        var v = build(true);
        Teacher stage = teacher(2L, "Stage", true); // pseudo
        Course c = course(1L, "Scienze", stage, G, false);
        // Pseudo-teacher lessons (e.g. the synthetic Stage course) are excluded, M21 precedent.
        v.verifyThat(SchoolScheduleConstraintProvider::theorySubjectRepeatedSameDay)
                .given(lesson(1L, c, slot(1, DAY, 8), null),
                        lesson(2L, c, slot(2, DAY, 9), null))
                .penalizesBy(0);
    }
}
