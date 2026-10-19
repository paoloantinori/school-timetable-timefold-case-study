package com.school.planner.solver;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
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
 * M-TC lab tag-capacity (TASK-455, scarcity-aware spec 2026-07-30): flag-gated
 * DEFAULT OFF prototype. These tests lock the four load-bearing behaviors so the
 * flag default-OFF never silently changes a score and the ON path penalizes the
 * joint-scarcity overcapacity the 30e pre-measure isolated.
 *
 * <p>The flag ({@code planner.tag-capacity.enabled}) and the per-tag room capacity
 * ({@link SchoolSchedule#roomCapacityForTag}) are both read at constraint BUILD time
 * (M-LF build-once pattern), so each test builds a fresh {@link ConstraintVerifier}
 * with the system property set or cleared BEFORE the build, rather than reusing a
 * single injected verifier that captured the default-OFF flag at startup.
 */
class LabTagCapacityConstraintTest {

    private static final Timeslot TS = timeslot(1L, java.time.LocalDate.of(2026, 9, 17), 9, 0, 10, 0);
    private static final Teacher T = teacher(1L, "T18");
    private static final StudentGroup G = group(1L, "G06", 15);

    private ConstraintVerifier<SchoolScheduleConstraintProvider, SchoolSchedule> build(boolean enabled) {
        if (enabled) {
            System.setProperty("planner.tag-capacity.enabled", "true");
        } else {
            System.clearProperty("planner.tag-capacity.enabled");
        }
        return ConstraintVerifier.build(
                new SchoolScheduleConstraintProvider(), SchoolSchedule.class, Lesson.class);
    }

    private Course acconciaturaLab() {
        Course c = course(1L, "Lavaggio", T, G, true);
        c.requiredRoomTags = List.of("acconciatura"); // single-tag lab, the seed's only shape
        return c;
    }

    @AfterEach
    void reset() {
        System.clearProperty("planner.tag-capacity.enabled");
        SchoolSchedule.setRoomCapacityByTagStatic(Map.of());
        SchoolSchedule.setGoldenCoPresenceDepthStatic(Map.of());
    }

    @Test
    void flagOff_isInertEvenWhenOverCapacity() {
        SchoolSchedule.setRoomCapacityByTagStatic(Map.of("acconciatura", 1));
        var v = build(false);
        Course c = acconciaturaLab();
        // 3 acconciatura lessons in one cell, cap 1: would be over capacity, but the
        // flag is OFF so the constraint is inert (0 matches). This is the regression
        // guard: the default must not change any score.
        v.verifyThat(SchoolScheduleConstraintProvider::labTagCapacity)
                .given(lesson(1L, c, TS, null), lesson(2L, c, TS, null), lesson(3L, c, TS, null))
                .penalizesBy(0);
    }

    @Test
    void flagOn_overCapacity_penalizesExcessAtDefaultWeight() {
        SchoolSchedule.setRoomCapacityByTagStatic(Map.of("acconciatura", 1));
        var v = build(true);
        Course c = acconciaturaLab();
        // demand 3 in one cell, cap 1 -> excess 2. penalizesBy sums the impact
        // function (count - cap) across matches, so it is the total excess, here 2.
        v.verifyThat(SchoolScheduleConstraintProvider::labTagCapacity)
                .given(lesson(1L, c, TS, null), lesson(2L, c, TS, null), lesson(3L, c, TS, null))
                .penalizesBy(2);
        // Weight check via the score (community Timefold supports getScore, not the
        // impact breakdown): default weight 1000 x excess 2 = -2000 medium.
        BendableScore impact = v
                .verifyThat(SchoolScheduleConstraintProvider::labTagCapacity)
                .given(lesson(1L, c, TS, null), lesson(2L, c, TS, null), lesson(3L, c, TS, null))
                .getScore();
        assertEquals(ScoreCompat.ofMedium(-2000), impact,
                "3 acconciatura lessons in a cap-1 cell cost -2000 medium (excess 2 x weight 1000)");
    }

    @Test
    void flagOn_withinCapacity_noPenalty() {
        SchoolSchedule.setRoomCapacityByTagStatic(Map.of("acconciatura", 1));
        var v = build(true);
        Course c = acconciaturaLab();
        // demand 1, cap 1 -> not over capacity -> 0.
        v.verifyThat(SchoolScheduleConstraintProvider::labTagCapacity)
                .given(lesson(1L, c, TS, null))
                .penalizesBy(0);
    }

    @Test
    void flagOn_pinnedCountsTowardDemand() {
        SchoolSchedule.setRoomCapacityByTagStatic(Map.of("acconciatura", 1));
        var v = build(true);
        Course c = acconciaturaLab();
        // 1 pinned + 1 unpinned in one cell, cap 1: the pinned lab session occupies the
        // single slot (H7-style accounting), so demand 2 > cap 1 -> 1 match. This is the
        // honest "pinned fills the room" semantics documented on the constraint.
        v.verifyThat(SchoolScheduleConstraintProvider::labTagCapacity)
                .given(lesson(1L, c, TS, null, true), lesson(2L, c, TS, null, false))
                .penalizesBy(1);
    }

    @Test
    void flagOn_practicedCoPresenceCell_isExempt() {
        // TASK-485: mirror H-COPRESENCE. A cell the golden itself practices (depth>=2 distinct
        // groups) is exempt from M-TC, so the school's accepted co-presence (acconciatura/moda) is
        // not taxed as if it were scarcity. This is the -120000 false-penalty fix from the seed-6
        // A/B (120 match = acconciatura 107 + moda 13, all practiced). REUSES the same published
        // depth map and isCoPresenceCell predicate as H-COPRESENCE: no second definition.
        SchoolSchedule.setRoomCapacityByTagStatic(Map.of("acconciatura", 1));
        SchoolSchedule.setGoldenCoPresenceDepthStatic(Map.of(
                SchoolSchedule.coPresenceCell("acconciatura", DayOfWeek.THURSDAY, LocalTime.of(9, 0)), 2));
        var v = build(true);
        Course c = acconciaturaLab();
        // 3 acconciatura at the practiced Thursday-09:00 cell, cap 1: would be excess 2 without
        // the exemption (see flagOn_overCapacity_penalizesExcessAtDefaultWeight), but the cell is
        // practiced (depth 2) -> exempt -> 0. Non-practiced cells (depth<2) still penalize normally.
        v.verifyThat(SchoolScheduleConstraintProvider::labTagCapacity)
                .given(lesson(1L, c, TS, null), lesson(2L, c, TS, null), lesson(3L, c, TS, null))
                .penalizesBy(0);
    }
}
