package com.school.planner.solver;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static com.school.planner.TestDataBuilder.course;
import static com.school.planner.TestDataBuilder.group;
import static com.school.planner.TestDataBuilder.lesson;
import static com.school.planner.TestDataBuilder.room;
import static com.school.planner.TestDataBuilder.teacher;
import static com.school.planner.TestDataBuilder.timeslot;

/**
 * TASK-464 co-presenza ospite: ratified DEFAULT-ON lever (2026-08-01, combined with TASK-463)
 * that legalizes the guest co-presenza the golden practices on acconciatura cells, via (a) a
 * pairwise H2 exemption and (b) a count-based hard cap (H-COPRESENCE) at the golden depth.
 *
 * <p>Both the flag ({@code planner.co-presenza.enabled}) and the golden depth map
 * ({@link SchoolSchedule#goldenCoPresenceDepth}) are read at constraint BUILD time
 * (M-LF/M-TC build-once pattern), so each test builds a fresh {@link ConstraintVerifier}
 * with the system property set or cleared BEFORE the build.
 *
 * <p>Locks five load-bearing behaviors: the OPTED-OUT score is byte-identical (H2 still
 * penalizes a guest pair), the ON path exempts the guest pair ONLY on a golden co-presence
 * cell (AC#4: never generalized), and the cap penalizes over-depth while tolerating
 * within-depth and counting pinned demand (H7-style accounting, like M-TC). Since the
 * default flipped to ON, the OFF path is exercised by setting the property to "false"
 * explicitly (the opt-out value), not by clearing it.
 */
class CoPresenceConstraintTest {

    // Thursday 2026-09-17 08:00-09:00: a golden co-presenza cell (acconciatura depth>=2).
    private static final Timeslot TS_THU_08 = timeslot(1L, LocalDate.of(2026, 9, 17), 8, 0, 9, 0);
    // Tuesday 2026-09-15 08:00-09:00: acconciatura tag, but NOT a golden co-presenza cell.
    private static final Timeslot TS_TUE_08 = timeslot(2L, LocalDate.of(2026, 9, 15), 8, 0, 9, 0);

    private static final Room ACC_ROOM = room(1L, "R11", 22, List.of("acconciatura"));
    private static final Teacher T1 = teacher(1L, "T18");
    private static final Teacher T2 = teacher(2L, "T26");
    private static final StudentGroup G1 = group(1L, "G02", 15);
    private static final StudentGroup G2 = group(2L, "G12", 14);

    private ConstraintVerifier<SchoolScheduleConstraintProvider, SchoolSchedule> build(boolean enabled) {
        if (enabled) {
            System.setProperty("planner.co-presenza.enabled", "true");
        } else {
            // The default is ON since the 2026-08-01 ratification, so the OFF path is the
            // explicit opt-out ("false"), not a cleared property (clear would resolve to ON).
            System.setProperty("planner.co-presenza.enabled", "false");
        }
        return ConstraintVerifier.build(
                new SchoolScheduleConstraintProvider(), SchoolSchedule.class, Lesson.class);
    }

    /** An acconciatura-tagged lab course (single requiredRoomTag, the seed's only shape). */
    private Course accCourse(long id, String subject, Teacher t, StudentGroup g) {
        Course c = course(id, subject, t, g, true);
        c.requiredRoomTags = List.of("acconciatura");
        return c;
    }

    /** Mark the acconciatura cell at (weekday, slotStart) as a golden co-presenza cell of given depth. */
    private void publishDepth(DayOfWeek weekday, LocalTime slotStart, int depth) {
        SchoolSchedule.setGoldenCoPresenceDepthStatic(
                Map.of(SchoolSchedule.coPresenceCell("acconciatura", weekday, slotStart), depth));
    }

    @AfterEach
    void reset() {
        System.clearProperty("planner.co-presenza.enabled");
        SchoolSchedule.setGoldenCoPresenceDepthStatic(Map.of());
    }

    // ─── H2 pairwise exemption (BaseConstraintProvider.roomConflict) ──────────────

    @Test
    void h2_flagOff_guestPairInCoPresenceCell_stillPenalized() {
        // Byte-identical guard (AC#2): with the flag OPTED OUT a guest pair in a depth>=2
        // cell is STILL an H2 conflict, exactly as production-before-ratification. The opt-out
        // path must change no score relative to the pre-ratification default.
        publishDepth(DayOfWeek.THURSDAY, LocalTime.of(8, 0), 2);
        var v = build(false);
        Course cA = accCourse(1L, "Lavaggio", T1, G1);
        Course cB = accCourse(2L, "Servizio stilistico", T2, G2);
        v.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(lesson(1L, cA, TS_THU_08, ACC_ROOM), lesson(2L, cB, TS_THU_08, ACC_ROOM))
                .penalizesBy(1);
    }

    @Test
    void h2_flagOn_guestPairInCoPresenceCell_exempt() {
        // The prototype's effect: with the flag ON, the guest pair on a golden co-presenza
        // cell no longer fires H2, so the guest may share the host's room (legal co-presenza).
        publishDepth(DayOfWeek.THURSDAY, LocalTime.of(8, 0), 2);
        var v = build(true);
        Course cA = accCourse(1L, "Lavaggio", T1, G1);
        Course cB = accCourse(2L, "Servizio stilistico", T2, G2);
        v.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(lesson(1L, cA, TS_THU_08, ACC_ROOM), lesson(2L, cB, TS_THU_08, ACC_ROOM))
                .penalizesBy(0);
    }

    @Test
    void h2_flagOn_pairOutsideCoPresenceCell_stillPenalized() {
        // AC#4 generalization guard: the exemption fires ONLY on a golden co-presenza cell.
        // Same tag, same room, same slot, but Tuesday 08:00 has no golden depth -> not exempt.
        publishDepth(DayOfWeek.THURSDAY, LocalTime.of(8, 0), 2); // only Thu is co-presenza
        var v = build(true);
        Course cA = accCourse(1L, "Lavaggio", T1, G1);
        Course cB = accCourse(2L, "Servizio stilistico", T2, G2);
        v.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(lesson(1L, cA, TS_TUE_08, ACC_ROOM), lesson(2L, cB, TS_TUE_08, ACC_ROOM))
                .penalizesBy(1);
    }

    @Test
    void h2_flagOn_nonTaggedPair_stillPenalized() {
        // AC#4 generalization guard: a pair whose lessons carry no single co-presenza tag
        // is never exempted, even on a co-presenza cell. Only golden-practiced tags qualify.
        publishDepth(DayOfWeek.THURSDAY, LocalTime.of(8, 0), 2);
        var v = build(true);
        Course cA = course(1L, "Matematica", T1, G1); // no requiredRoomTags
        Course cB = course(2L, "Storia", T2, G2); // no requiredRoomTags
        v.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(lesson(1L, cA, TS_THU_08, ACC_ROOM), lesson(2L, cB, TS_THU_08, ACC_ROOM))
                .penalizesBy(1);
    }

    // ─── H-COPRESENCE count cap (SchoolScheduleConstraintProvider.coPresenceCap) ───

    @Test
    void cap_flagOff_inertEvenWhenOverDepth() {
        // Byte-identical guard (AC#2): flag OPTED OUT -> the cap is inert (0 matches) even
        // when demand exceeds the golden depth. The opt-out path must change no score.
        publishDepth(DayOfWeek.THURSDAY, LocalTime.of(8, 0), 2);
        var v = build(false);
        Course c = accCourse(1L, "Lavaggio", T1, G1);
        v.verifyThat(SchoolScheduleConstraintProvider::coPresenceCap)
                .given(lesson(1L, c, TS_THU_08, null), lesson(2L, c, TS_THU_08, null),
                        lesson(3L, c, TS_THU_08, null))
                .penalizesBy(0);
    }

    @Test
    void cap_flagOn_overDepth_penalizesExcessAtHardWeight1() {
        // demand 3 in a depth-2 cell -> excess 1. penalizesBy sums the impact (count-depth),
        // so it is the total excess, here 1. Weight via getScore: hard weight 1 x excess 1 = -1 hard.
        publishDepth(DayOfWeek.THURSDAY, LocalTime.of(8, 0), 2);
        var v = build(true);
        Course c = accCourse(1L, "Lavaggio", T1, G1);
        v.verifyThat(SchoolScheduleConstraintProvider::coPresenceCap)
                .given(lesson(1L, c, TS_THU_08, null), lesson(2L, c, TS_THU_08, null),
                        lesson(3L, c, TS_THU_08, null))
                .penalizesBy(1);
        BendableScore impact = v
                .verifyThat(SchoolScheduleConstraintProvider::coPresenceCap)
                .given(lesson(1L, c, TS_THU_08, null), lesson(2L, c, TS_THU_08, null),
                        lesson(3L, c, TS_THU_08, null))
                .getScore();
        assertEquals(ScoreCompat.ofHard(-1), impact,
                "3 acconciatura lessons in a depth-2 cell cost -1 hard (excess 1 x weight 1)");
    }

    @Test
    void cap_flagOn_withinDepth_noPenalty() {
        // demand 2, depth 2 -> within the golden ceiling -> 0.
        publishDepth(DayOfWeek.THURSDAY, LocalTime.of(8, 0), 2);
        var v = build(true);
        Course c = accCourse(1L, "Lavaggio", T1, G1);
        v.verifyThat(SchoolScheduleConstraintProvider::coPresenceCap)
                .given(lesson(1L, c, TS_THU_08, null), lesson(2L, c, TS_THU_08, null))
                .penalizesBy(0);
    }

    @Test
    void cap_flagOn_pinnedCountsTowardDemand() {
        // H7-style accounting (mirrors M-TC) on a real co-presenza cell (depth 2): 1 pinned
        // + 2 unpinned -> the pinned lab session occupies its slot, demand 3 > depth 2 ->
        // excess 1. (A depth-1 cell is governed by H2 alone, since the cap backstops the
        // H2 exemption, which only exists on depth>=2 cells.)
        publishDepth(DayOfWeek.THURSDAY, LocalTime.of(8, 0), 2);
        var v = build(true);
        Course c = accCourse(1L, "Lavaggio", T1, G1);
        v.verifyThat(SchoolScheduleConstraintProvider::coPresenceCap)
                .given(lesson(1L, c, TS_THU_08, null, true),
                        lesson(2L, c, TS_THU_08, null, false),
                        lesson(3L, c, TS_THU_08, null, false))
                .penalizesBy(1);
    }
}
