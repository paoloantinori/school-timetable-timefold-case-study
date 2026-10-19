package com.school.planner.solver;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.LabAssistant;
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;

import static com.school.planner.TestDataBuilder.*;

@QuarkusTest
class SchoolScheduleConstraintProviderTest {

    private static final LocalDate MON = LocalDate.of(2025, 10, 6);
    private static final LocalDate TUE = LocalDate.of(2025, 10, 7);

    private static final Timeslot TS_MON_08 = timeslot(1L, MON, 8, 0, 9, 0);
    private static final Timeslot TS_MON_09 = timeslot(2L, MON, 9, 0, 10, 0);
    private static final Timeslot TS_MON_10 = timeslot(3L, MON, 10, 0, 11, 0);
    private static final Timeslot TS_MON_11 = timeslot(5L, MON, 11, 0, 12, 0);
    private static final Timeslot TS_MON_12 = timeslot(6L, MON, 12, 0, 13, 0);
    private static final Timeslot TS_MON_13 = timeslot(7L, MON, 13, 0, 14, 0);
    private static final Timeslot TS_MON_1430 = timeslot(8L, MON, 14, 30, 15, 30);
    private static final Timeslot TS_MON_1530 = timeslot(9L, MON, 15, 30, 16, 30);
    private static final Timeslot TS_MON_1630 = timeslot(14L, MON, 16, 30, 17, 30);
    private static final Timeslot TS_TUE_08 = timeslot(4L, TUE, 8, 0, 9, 0);
    private static final Timeslot TS_TUE_09 = timeslot(10L, TUE, 9, 0, 10, 0);
    private static final Timeslot TS_TUE_10 = timeslot(11L, TUE, 10, 0, 11, 0);

    // Additional timeslots for overlap boundary tests
    private static final Timeslot TS_MON_0830 = timeslot(12L, MON, 8, 30, 9, 30); // overlaps TS_MON_08
    private static final Timeslot TS_MON_0930 = timeslot(13L, MON, 9, 30, 10, 30); // overlaps TS_MON_09, TS_MON_10

    private static final Room ROOM_A = room(1L, "Room A", 30);
    private static final Room ROOM_B = room(2L, "Room B", 30);
    private static final Room ROOM_C = room(6L, "Room C", 30);
    private static final Room ROOM_LAB = room(3L, "Lab", 20, List.of("LAB"));
    private static final Room ROOM_MECCANICA = room(4L, "R15", 20, List.of("meccanica"));
    private static final Room ROOM_MODA = room(5L, "R16", 20, List.of("moda"));

    private static final Teacher TEACHER1 = teacher(1L, "Teacher One");
    private static final Teacher TEACHER2 = teacher(2L, "Teacher Two");

    private static final StudentGroup GROUP1 = group(1L, "G03", 20);
    private static final StudentGroup GROUP2 = group(2L, "G07", 20);
    private static final StudentGroup GROUP_G14 = group(3L, "G14", 15);

    @Inject
    ConstraintVerifier<SchoolScheduleConstraintProvider, SchoolSchedule> constraintVerifier;

    @Inject
    AvailabilityExpansionService availabilityExpansionService;

    /**
     * TASK-424/TASK-598: the solver statics are JVM-global, and in a shared surefire JVM
     * a @QuarkusTest that booted the app EARLIER (or any test class that ran before)
     * leaves them populated - clear BEFORE each test too, not just after, or these
     * fixtures' targets depend on the suite's order (the measured case: the app boot
     * publishes the real 2026/27 quota map, the synthetic group id 4 = "G03" then
     * inherits real quotas and a 2025 synthetic date reads as quota-0, M20 1000 -> 0).
     */
    @BeforeEach
    void clearSolverStaticsBefore() {
        clearSolverStatics();
    }

    @AfterEach
    void clearStaticState() {
        clearSolverStatics();
    }

    private void clearSolverStatics() {
        // TASK-424: also clears the authoritative-pinned-hours map the day-fill family
        // nets its target against (published in the same pass, see the setter).
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setLabAssistantsStatic(List.of());
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        // TASK-557: the H34 fixture grid - same family as the clears above (a
        // static a fixture sets must not leak into later tests' VRP paths).
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        // TASK-413: M32 template map is a global static - clear it so the manual
        // fixtures the M32 tests below set never leak into other constraints' tests.
        SchoolSchedule.setWeeklyTemplateStatic(Map.of(), Map.of());
        // TASK-425: same for the cadence reference maps M34/M35 read - a burst cap
        // or active-week target left behind would silently fire in later tests.
        SchoolSchedule.setCadenceReferenceStatic(Map.of(), Map.of());
        // TASK-419: same for the promoted-pin registry - a promotion left behind
        // would silently strip the pinned exemptions in every later test.
        SchoolSchedule.setPromotedLessonIdsStatic(List.of());
        // TASK-598: the day-fill target chain's CAP layer (supply-cap ratios + pool-cap
        // targets/armed flag, TASK-461/497) is published by any earlier @QuarkusTest's
        // app boot: a synthetic group inheriting a real ratio sees its calendar-capped
        // quota scaled below the placed hours and M20 reads 0 (the measured case).
        SchoolSchedule.clearM20CapPoolStatics();
    }

    // TASK-345: the only test in this file that touches the DB (the rest use
    // unpersisted TestDataBuilder fixtures). Cleanup MUST run in its own
    // @AfterEach transaction, not inline via try/finally inside the test's own
    // @Transactional method: a bulk JPQL delete (Panache's delete("field = ?1", ...))
    // removes the DB row but does NOT evict the still-managed Java entity from
    // the session's persistence context. Deleting the RecurringAvailabilityRule
    // row via bulk delete, then removing its `teacher` via Teacher.deleteById()
    // (a proper entity-level remove), leaves a stale managed
    // RecurringAvailabilityRule instance whose `teacher` reference now points
    // at a REMOVED entity — Hibernate's final pre-commit flush then throws
    // TransientPropertyValueException ("references an unsaved transient
    // instance") because the two removal paths disagree within one session.
    // A separate @AfterEach transaction starts a fresh persistence context, so
    // this never happens - matches the row is scoped by id (not a blanket
    // deleteAll()) because that would FK-violate against Lesson/Timeslot rows
    // other @QuarkusTest classes leave behind on the shared H2 instance.
    private Long expansionTestTeacherId;
    private final List<Long> expansionTestTimeslotIds = new java.util.ArrayList<>();

    @AfterEach
    @Transactional
    void clearAvailabilityExpansionDbState() {
        if (expansionTestTeacherId != null) {
            TeacherAvailability.delete("teacher.id = ?1", expansionTestTeacherId);
            RecurringAvailabilityRule.delete("teacher.id = ?1", expansionTestTeacherId);
        }
        if (!expansionTestTimeslotIds.isEmpty()) {
            Timeslot.delete("id in ?1", expansionTestTimeslotIds);
        }
        if (expansionTestTeacherId != null) {
            Teacher.deleteById(expansionTestTeacherId);
        }
        expansionTestTeacherId = null;
        expansionTestTimeslotIds.clear();
    }

    // ─── H1: Teacher Conflict (L-L) ───────────────────────────────────────

    @Test
    void teacherConflict_penalized() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER1, GROUP2);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_08, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void teacherConflict_notPenalized() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER2, GROUP2);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_08, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    // TASK-346 M1: a pseudo teacher (e.g. "Stage") is shared across different
    // groups' pinned lessons - same-slot overlap under it is not a real conflict.
    @Test
    void teacherConflict_notPenalized_pseudoTeacher() {
        Teacher pseudoStage = teacher(3L, "Stage", true);
        Course courseA = course(1L, "Stage", pseudoStage, GROUP1);
        Course courseB = course(2L, "Stage", pseudoStage, GROUP2);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, null);
        Lesson l2 = lesson(2L, courseB, TS_MON_08, null);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    // The same same-slot overlap under a REAL (non-pseudo) teacher must still
    // fire H1 - see teacherConflict_penalized() above (TEACHER1 is not pseudo).

    // ─── H2: Room Conflict (L-L) ──────────────────────────────────────────

    @Test
    void roomConflict_penalized() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER2, GROUP2);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(l1, l2)
                .penalizesBy(1);
    }

    // ── H0 - Unassigned lesson (TASK-447 reweight to hard 2, the anti-arbitrage with H29) ──
    // L0 invariant: leaving a lesson UNASSIGNED (-2, H0) is strictly worse than leaving it
    // PLACED-but-room-null (-1, H29), so the solver never de-assigns to escape a room/conflict.
    // NOTE: ConstraintVerifier.penalizesBy(int) is MATCH-COUNT in community Timefold (multi-tier
    // score-analysis needs Enterprise), so the WEIGHT is asserted via getScore(), not penalizesBy().
    @Test
    void h0_penalizesUnassignedByTwoHardWeight() {
        Course c = course(1L, "Math", TEACHER1, GROUP1);
        Lesson boxed = lesson(1L, c, null, null);
        BendableScore impact = constraintVerifier
                .verifyThat(SchoolScheduleConstraintProvider::unassignedLesson)
                .given(boxed)
                .getScore();
        assertEquals(ScoreCompat.ofHard(-2), impact,
                "one unassigned lesson costs -2 hard (H0 weight 2)");
    }

    @Test
    void l0_antiArbitrage_deassignIsStrictlyWorseThanRoomNull() {
        // The actual contract: de-assigning a lesson (H0) must score strictly worse than leaving
        // it placed-but-room-null (H29), else the solver de-assigns to escape a room/conflict
        // (the arbitrage that emptied the schedule when room-required was hard at parity).
        Course c = course(1L, "Math", TEACHER1, GROUP1);
        BendableScore unassigned = constraintVerifier
                .verifyThat(SchoolScheduleConstraintProvider::unassignedLesson)
                .given(lesson(1L, c, null, null))
                .getScore();
        BendableScore roomNull = constraintVerifier
                .verifyThat(SchoolScheduleConstraintProvider::assignedLessonMustHaveRoom)
                .given(lesson(2L, c, TS_MON_08, null))
                .getScore();
        assertTrue(unassigned.compareTo(roomNull) < 0,
                "unassigned " + unassigned + " must score strictly worse than room-null " + roomNull
                        + " (the anti-arbitrage: H0=2 > H29=1)");
    }

    @Test
    void h0_exemptPlacedLesson() {
        Course c = course(1L, "Math", TEACHER1, GROUP1);
        Lesson placed = lesson(1L, c, TS_MON_08, ROOM_A);
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::unassignedLesson)
                .given(placed)
                .penalizesBy(0);
    }

    // ── H29 - Assigned lesson must have a room (TASK-447: promoted from M36 medium 1000 to HARD 1) ──

    @Test
    void h29_penalizesRoomNullOneHardWeight() {
        Course c = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l = lesson(1L, c, TS_MON_08, null);
        BendableScore impact = constraintVerifier
                .verifyThat(SchoolScheduleConstraintProvider::assignedLessonMustHaveRoom)
                .given(l)
                .getScore();
        assertEquals(ScoreCompat.ofHard(-1), impact,
                "one placed-but-room-null lesson costs -1 hard (H29 weight 1)");
    }

    @Test
    void h29_exemptPinnedRoomNull() {
        Course c = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l = lesson(1L, c, TS_MON_08, null);
        l.pinned = true;
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::assignedLessonMustHaveRoom)
                .given(l)
                .penalizesBy(0);
    }

    @Test
    void h29_exemptPseudoRoomNull() {
        Teacher stage = teacher(99L, "Stage");
        stage.pseudo = true;
        Course c = course(2L, "Stage", stage, GROUP1);
        Lesson l = lesson(3L, c, TS_MON_08, null);
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::assignedLessonMustHaveRoom)
                .given(l)
                .penalizesBy(0);
    }

    @Test
    void h29_exemptBoxedAndHasRoom() {
        Course c = course(1L, "Math", TEACHER1, GROUP1);
        Lesson boxed = lesson(1L, c, null, null);
        Lesson placed = lesson(2L, c, TS_MON_08, ROOM_A);
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::assignedLessonMustHaveRoom)
                .given(boxed, placed)
                .penalizesBy(0);
    }

    @Test
    void roomConflict_notPenalized_differentRoom() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER2, GROUP2);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_08, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    // ─── H3: Student Group Conflict (L-L) ─────────────────────────────────

    @Test
    void studentGroupConflict_penalized() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER2, GROUP1);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_08, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupConflict)
                .given(l1, l2)
                .penalizesBy(1);
    }

    // ─── H1: Null-safety & Overlap Boundary Tests ────────────────────────

    @Test
    void teacherConflict_notPenalized_nullTimeslotA() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER1, GROUP2);
        Lesson l1 = lesson(1L, courseA, null, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_08, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void teacherConflict_notPenalized_nullTimeslotB() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER1, GROUP2);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, null, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void teacherConflict_notPenalized_bothNullTimeslot() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER1, GROUP2);
        Lesson l1 = lesson(1L, courseA, null, ROOM_A);
        Lesson l2 = lesson(2L, courseB, null, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void teacherConflict_notPenalized_adjacentSlots() {
        // 08:00-09:00 and 09:00-10:00: touching but NOT overlapping (half-open intervals)
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER1, GROUP2);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void teacherConflict_penalized_partialOverlap() {
        // 08:00-09:00 and 08:30-09:30: 30-minute overlap
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER1, GROUP2);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_0830, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void teacherConflict_penalized_contained() {
        // 08:00-10:00 contains 08:30-09:30 (TS_MON_0830)
        // TS_MON_08 is 08:00-09:00, TS_MON_10 is 10:00-11:00
        // We need a custom timeslot: 08:00-10:00 containing TS_MON_0830 (08:30-09:30)
        Timeslot wide = timeslot(50L, MON, 8, 0, 10, 0);
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER1, GROUP2);
        Lesson l1 = lesson(1L, courseA, wide, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_0830, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void teacherConflict_penalized_labSessionSameSlot() {
        // TASK-377: same lab session in the SAME slot is stacking, not a block —
        // consecutive block hours sit in different slots and never overlap, so
        // the old isSameLabSession exemption protected nothing legitimate.
        Course labCourse = course(1L, "Acconciatura", TEACHER1, GROUP1, true);
        Lesson l1 = lesson(1L, labCourse, TS_MON_08, ROOM_A);
        l1.isLabHour = true;
        l1.labTotalHours = 2;
        l1.sessionIndex = 0;
        Lesson l2 = lesson(2L, labCourse, TS_MON_08, ROOM_B);
        l2.isLabHour = true;
        l2.labTotalHours = 2;
        l2.sessionIndex = 0;

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void teacherConflict_notPenalized_labSession_differentDay() {
        // Same course, different day, both isLabHour → different lab sessions
        Course labCourse = course(1L, "Acconciatura", TEACHER1, GROUP1, true);
        Lesson l1 = lesson(1L, labCourse, TS_MON_08, ROOM_A);
        l1.isLabHour = true;
        Lesson l2 = lesson(2L, labCourse, TS_TUE_08, ROOM_B);
        l2.isLabHour = true;

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(0); // Different day → no overlap → no conflict
    }

    @Test
    void teacherConflict_penalized_labSession_differentCourse() {
        // Same teacher, different course, same day, both isLabHour → conflict
        Course labCourseA = course(1L, "Acconciatura", TEACHER1, GROUP1, true);
        Course labCourseB = course(2L, "Modellistica", TEACHER1, GROUP2, true);
        Lesson l1 = lesson(1L, labCourseA, TS_MON_08, ROOM_A);
        l1.isLabHour = true;
        Lesson l2 = lesson(2L, labCourseB, TS_MON_08, ROOM_B);
        l2.isLabHour = true;

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void teacherConflict_notPenalized_differentDay() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER1, GROUP2);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_TUE_08, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void teacherConflict_penalized_multipleConflicts() {
        // 3 lessons, same teacher, all overlapping same timeslot → 3 unique pairs
        Course c1 = course(1L, "Math", TEACHER1, GROUP1);
        Course c2 = course(2L, "Science", TEACHER1, GROUP2);
        Course c3 = course(3L, "History", TEACHER1, GROUP_G14);
        Lesson l1 = lesson(1L, c1, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, c2, TS_MON_08, ROOM_B);
        Lesson l3 = lesson(3L, c3, TS_MON_08, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2, l3)
                .penalizesBy(3);
    }

    // ─── H2: Room Conflict Additional Tests ──────────────────────────────

    @Test
    void roomConflict_notPenalized_sameGroup() {
        // Same room, same group → no conflict (group can share room)
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER2, GROUP1);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void roomConflict_notPenalized_nullRoom() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER2, GROUP2);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, null);
        Lesson l2 = lesson(2L, courseB, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void roomConflict_notPenalized_nullTimeslot() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Science", TEACHER2, GROUP2);
        Lesson l1 = lesson(1L, courseA, null, ROOM_A);
        Lesson l2 = lesson(2L, courseB, null, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void roomConflict_penalized_differentGroupOverlap() {
        // 3 lessons, same room, 3 different groups, all overlapping → 3 pairs
        StudentGroup group3 = group(4L, "G11", 25);
        Course c1 = course(1L, "Math", TEACHER1, GROUP1);
        Course c2 = course(2L, "Science", TEACHER2, GROUP2);
        Course c3 = course(3L, "History", TEACHER1, group3);
        Lesson l1 = lesson(1L, c1, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, c2, TS_MON_08, ROOM_A);
        Lesson l3 = lesson(3L, c3, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(l1, l2, l3)
                .penalizesBy(3);
    }

    @Test
    void roomConflict_notPenalized_labSession() {
        // Same room, same lab session → no conflict
        Course labCourse = course(1L, "Acconciatura", TEACHER1, GROUP1, true);
        Lesson l1 = lesson(1L, labCourse, TS_MON_08, ROOM_A);
        l1.isLabHour = true;
        Lesson l2 = lesson(2L, labCourse, TS_MON_08, ROOM_A);
        l2.isLabHour = true;

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(l1, l2)
                .penalizesBy(0); // Same group (from same course) → already filtered
    }

    @Test
    void roomConflict_penalized_partialOverlap() {
        // Same room, different groups, 30-min overlap
        Course c1 = course(1L, "Math", TEACHER1, GROUP1);
        Course c2 = course(2L, "Science", TEACHER2, GROUP2);
        Lesson l1 = lesson(1L, c1, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, c2, TS_MON_0830, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomConflict)
                .given(l1, l2)
                .penalizesBy(1);
    }

    // ─── H3: Student Group Conflict Additional Tests ─────────────────────

    @Test
    void studentGroupConflict_penalized_sameCourseSameSlot() {
        // TASK-377: two hours of the SAME course stacked into one slot are a
        // conflict. The old same-course allowance only ever matched same-slot
        // stacking (1h half-open slots never overlap across slots) and let the
        // first 2026/27 solve pile 36 G14 hours into 3 slots.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_08, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupConflict)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void studentGroupConflict_penalized_roomsUnassigned() {
        // TASK-377 regression: rooms are assigned only in phase 4 of the 5-phase
        // pipeline; H3 must fire on same-slot conflicts even while room is null
        // (the old forEach() silently skipped room-null lessons).
        Course c1 = course(1L, "Math", TEACHER1, GROUP1);
        Course c2 = course(2L, "Science", TEACHER2, GROUP1);
        Lesson l1 = lesson(1L, c1, TS_MON_08, null);
        Lesson l2 = lesson(2L, c2, TS_MON_08, null);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupConflict)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void teacherConflict_penalized_roomsUnassigned() {
        // TASK-377 regression: same as above for H1 — a teacher double-booked
        // across two groups must be caught during the timeslot phases too.
        Course c1 = course(1L, "Math", TEACHER1, GROUP1);
        Course c2 = course(2L, "Science", TEACHER1, GROUP2);
        Lesson l1 = lesson(1L, c1, TS_MON_08, null);
        Lesson l2 = lesson(2L, c2, TS_MON_08, null);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void studentGroupConflict_notPenalized_nullTimeslot() {
        Course c1 = course(1L, "Math", TEACHER1, GROUP1);
        Course c2 = course(2L, "Science", TEACHER2, GROUP1);
        Lesson l1 = lesson(1L, c1, null, ROOM_A);
        Lesson l2 = lesson(2L, c2, TS_MON_08, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void studentGroupConflict_penalized_labSessionSameSlot() {
        // TASK-377: same lab session in the SAME slot is stacking, not a block —
        // consecutive block hours sit in different slots and never overlap, so
        // the old isSameLabSession exemption protected nothing legitimate.
        Course labCourse = course(1L, "Acconciatura", TEACHER1, GROUP1, true);
        Lesson l1 = lesson(1L, labCourse, TS_MON_08, ROOM_A);
        l1.isLabHour = true;
        Lesson l2 = lesson(2L, labCourse, TS_MON_08, ROOM_B);
        l2.isLabHour = true;

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupConflict)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void studentGroupConflict_penalized_multipleConflicts() {
        // Same group, 3 different courses, all overlapping → 3 pairs
        Course c1 = course(1L, "Math", TEACHER1, GROUP1);
        Course c2 = course(2L, "Science", TEACHER2, GROUP1);
        Course c3 = course(3L, "History", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, c1, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, c2, TS_MON_08, ROOM_B);
        Lesson l3 = lesson(3L, c3, TS_MON_08, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupConflict)
                .given(l1, l2, l3)
                .penalizesBy(3);
    }

    @Test
    void studentGroupConflict_notPenalized_adjacentSlots() {
        // Same group, different courses, touching slots → no overlap (half-open)
        Course c1 = course(1L, "Math", TEACHER1, GROUP1);
        Course c2 = course(2L, "Science", TEACHER2, GROUP1);
        Lesson l1 = lesson(1L, c1, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, c2, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupConflict)
                .given(l1, l2)
                .penalizesBy(0);
    }

    // ─── Cross-Cutting / Edge Case Tests ─────────────────────────────────

    @Test
    void overlapping_boundaryExact_notConflicting() {
        // 09:00-10:00 vs 10:00-11:00: exactly touching → NOT overlapping for H1/H2/H3
        Course math = course(1L, "Math", TEACHER1, GROUP1);
        Course science = course(2L, "Science", TEACHER1, GROUP2);
        Course history = course(3L, "History", TEACHER2, GROUP1);

        Lesson l1 = lesson(1L, math, TS_MON_09, ROOM_A);    // 09:00-10:00
        Lesson l2 = lesson(2L, science, TS_MON_10, ROOM_B);  // 10:00-11:00

        // H1: same teacher, touching slots → no conflict
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherConflict)
                .given(l1, l2)
                .penalizesBy(0);

        // H3: same group, different courses, touching slots → no conflict
        Lesson l3 = lesson(3L, history, TS_MON_10, ROOM_C);  // 10:00-11:00
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupConflict)
                .given(l1, l3)
                .penalizesBy(0);
    }

    // ─── H4: Stage Blackout (L) ─────────────────────────────────────────

    // Shared by every H4 test below (code review: simplify) - the exact same
    // Monday stage window was independently re-constructed in each test.
    private static StagePattern mondayStagePattern() {
        return new StagePattern(
                GROUP1,
                LocalDate.of(2025, 10, 1),
                LocalDate.of(2025, 12, 31),
                Set.of(DayOfWeek.MONDAY));
    }

    @Test
    void stageBlackout_penalized() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        course.professionalizzanti = false;
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::stageBlackout)
                .given(lesson, mondayStagePattern())
                .penalizesBy(1);
    }

    @Test
    void stageBlackout_notPenalized_exemptProfessionalizzanti() {
        Course course = course(1L, "Lab Work", TEACHER1, GROUP1);
        course.professionalizzanti = true;
        GROUP1.allowProfessionalizzantiDuringStage = true; // TASK-272: Group allows prof during stage
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::stageBlackout)
                .given(lesson, mondayStagePattern())
                .penalizesBy(0);
    }

    // TASK-272: Per-group setting for professionalizzanti during stage
    @Test
    void stageBlackout_penalized_professionalizzanti_groupDisallows() {
        Course course = course(1L, "Lab Work", TEACHER1, GROUP1);
        course.professionalizzanti = true;
        GROUP1.allowProfessionalizzantiDuringStage = false; // Default - group disallows prof during stage
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::stageBlackout)
                .given(lesson, mondayStagePattern())
                .penalizesBy(1);
    }

    // TASK-366: a pinned pseudo-teacher lesson (e.g. the synthetic "Stage" course
    // itself) cannot violate its own group's blackout - it trivially falls inside
    // the stage window/day-mask it represents, so without this exclusion every
    // pinned Stage lesson self-violated H4 (694 spurious hits in the golden audit).
    @Test
    void stageBlackout_notPenalized_pseudoTeacher() {
        Teacher pseudoStage = teacher(3L, "Stage", true);
        Course course = course(1L, "Stage", pseudoStage, GROUP1);
        course.professionalizzanti = false;
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A, true);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::stageBlackout)
                .given(lesson, mondayStagePattern())
                .penalizesBy(0);
    }

    // Companion to the pseudo-teacher exemption above: a REAL teacher's non-prof
    // lesson genuinely scheduled during the stage window must still fire H4 -
    // existing behavior preserved (mirrors stageBlackout_penalized()).
    @Test
    void stageBlackout_penalized_realTeacherDuringStage() {
        Course course = course(1L, "Scienze", TEACHER1, GROUP1);
        course.professionalizzanti = false;
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A, true);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::stageBlackout)
                .given(lesson, mondayStagePattern())
                .penalizesBy(1);
    }

    // TASK-366 code review (D6): the pseudo-teacher exclusion filter is
    // `l.getTeacher() == null || !l.getTeacher().pseudo` - a null teacher
    // (never actually produced by GoldenScheduleImportService, but not ruled
    // out by the domain model either) passes straight through and is NOT
    // excluded, unlike a pseudo teacher. This is a deliberate asymmetry (only
    // a genuinely pseudo teacher self-violates its own blackout - a lesson
    // with no teacher at all has no such excuse) and this test locks it in
    // so a future refactor of the filter can't silently widen the exclusion
    // to null teachers too.
    @Test
    void stageBlackout_penalized_nullTeacherDuringStage() {
        Course course = course(1L, "Scienze", null, GROUP1);
        course.professionalizzanti = false;
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A, true);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::stageBlackout)
                .given(lesson, mondayStagePattern())
                .penalizesBy(1);
    }

    // ─── H5: Teacher Unavailability (L) ─────────────────────────────────

    @Test
    void teacherUnavailability_penalized() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

        TeacherAvailability ta = new TeacherAvailability(
                TEACHER1,
                LocalDateTime.of(2025, 10, 6, 0, 0),
                LocalDateTime.of(2025, 10, 6, 23, 59),
                AvailabilityType.VACATION);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherUnavailability)
                .given(lesson, ta)
                .penalizesBy(1);
    }

    // TASK-345: a RecurringAvailabilityRule expanded via AvailabilityExpansionService
    // (not hand-built) must produce a TeacherAvailability row H5 fires on -
    // proves the expansion pipeline is H5-visible end to end, not just that
    // teacherUnavailability() works on a hand-built row (already covered above).
    @Test
    @Transactional
    void teacherUnavailability_penalized_forExpandedRecurringRule() {
        Teacher teacher = new Teacher();
        teacher.name = "ExpansionTestTeacher";
        teacher.persist();
        expansionTestTeacherId = teacher.id;

        LocalDate monday = LocalDate.of(2026, 9, 14);
        for (LocalTime start : com.school.planner.solver.TimeslotSequence.SLOT_START_TIMES) {
            Timeslot ts = new Timeslot(LocalDateTime.of(monday, start), LocalDateTime.of(monday, start.plusHours(1)));
            ts.persist();
            expansionTestTimeslotIds.add(ts.id);
        }

        // T04-shaped rule: unavailable slots 5-7 (13:00-16:30, spans the lunch break).
        new RecurringAvailabilityRule(teacher, DayOfWeek.MONDAY, 5, 7, AvailabilityType.VACATION).persist();
        availabilityExpansionService.expandForTeacher(teacher);

        // Filtered by this test's own teacher AND date - AvailabilityExpansionService
        // reads the entire Timeslot table, so on a shared H2 instance other test
        // classes' leftover Mondays could otherwise produce extra rows for this
        // same (brand-new) teacher.
        TeacherAvailability expanded = TeacherAvailability.<TeacherAvailability>list("teacher.id = ?1", teacher.id)
                .stream()
                .filter(ta -> ta.startDateTime.toLocalDate().equals(monday))
                .findFirst()
                .orElseThrow();

        Course course = course(1L, "Math", teacher, GROUP1);
        Lesson afternoonLesson = lesson(1L, course, timeslot(1L, monday, 13, 0, 14, 0), ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherUnavailability)
                .given(afternoonLesson, expanded)
                .penalizesBy(1);
    }

    // ─── H14: Student Group Start Date (L) ───────────────────────────────

    @Test
    void studentGroupStartDate_penalized() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        GROUP1.startDate = LocalDate.of(2025, 10, 10);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupStartDate)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void studentGroupStartDate_notPenalized_afterStartDate() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        GROUP1.startDate = LocalDate.of(2025, 10, 1);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupStartDate)
                .given(lesson)
                .penalizesBy(0);
    }

    // ─── M15: Same-day Same-subject Should Be Consecutive (was H15, TASK-392) ──────────────────

    @Test
    void sameDaySameSubjectShouldBeConsecutive_penalized() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_A);
        Lesson l3 = lesson(3L, course, TS_MON_11, ROOM_A); // Gap at 10 = 60 minutes

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sameDaySameSubjectShouldBeConsecutive)
                .given(l1, l2, l3)
                .penalizesBy(60); // 60 minutes gap (10:00-11:00)
    }

    @Test
    void sameDaySameSubjectShouldBeConsecutive_notPenalized_consecutive() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_A);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sameDaySameSubjectShouldBeConsecutive)
                .given(l1, l2, l3)
                .penalizesBy(0);
    }

    @Test
    void sameDaySameSubjectShouldBeConsecutive_notPenalized_acrossLunchBreak() {
        // TASK-341: slot 5 (13:00-14:00) -> slot 6 (14:30-15:30). Lunch is not a gap.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_13, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_1430, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sameDaySameSubjectShouldBeConsecutive)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void sameDaySameSubjectShouldBeConsecutive_notPenalized_bothSidesPinned() {
        // TASK-349: a break is exempt when BOTH the previous and next lesson are pinned
        // (the golden/historical import case - the school's own record tolerates rare
        // splits like this one).
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_A, true);
        Lesson l3 = lesson(3L, course, TS_MON_11, ROOM_A, true); // Gap at 10, both sides of break pinned

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sameDaySameSubjectShouldBeConsecutive)
                .given(l1, l2, l3)
                .penalizesBy(0);
    }

    @Test
    void sameDaySameSubjectShouldBeConsecutive_penalized_unpinnedSplit() {
        // TASK-349: an all-unpinned split (the solver placing two same-day
        // same-subject lessons non-adjacently) must still be a violation (medium
        // since TASK-392) - the pinned exemption never weakens the constraint for
        // the solver's own future placements. penalizesBy(long) checks the match
        // weight (60), tier-agnostic, so it is unchanged by the hard->medium move.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, false);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_A, false);
        Lesson l3 = lesson(3L, course, TS_MON_11, ROOM_A, false); // Gap at 10 = 60 minutes

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sameDaySameSubjectShouldBeConsecutive)
                .given(l1, l2, l3)
                .penalizesBy(60);
    }

    @Test
    void sameDaySameSubjectShouldBeConsecutive_penalized_oneSidePinned() {
        // TASK-349: only ONE side of the break pinned - not exempt. A hand-pinned
        // lesson must not let the solver create a fresh split against it.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_A, true);
        Lesson l3 = lesson(3L, course, TS_MON_11, ROOM_A, false); // Gap at 10, only l2 (pinned) side of break pinned

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sameDaySameSubjectShouldBeConsecutive)
                .given(l1, l2, l3)
                .penalizesBy(60);
    }

    @Test
    void sameDaySameSubjectShouldBeConsecutive_penalized_otherSidePinned() {
        // TASK-349: the symmetric case of oneSidePinned above - previous side unpinned,
        // next side pinned. The exemption's AND is commutative in principle, but exercise
        // both operand orders directly rather than relying on that.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, false);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_A, false);
        Lesson l3 = lesson(3L, course, TS_MON_11, ROOM_A, true); // Gap at 10, only l3 (next) side pinned

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sameDaySameSubjectShouldBeConsecutive)
                .given(l1, l2, l3)
                .penalizesBy(60);
    }

    // ─── H16/M37: Consecutive Same-subject Same Teacher-room (S16 teacher / M37 room, was H16b hard) ──────────────────

    @Test
    void consecutiveSameSubjectSameTeacher_penalized() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Math", TEACHER2, GROUP1); // Same subject, different teacher
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_09, ROOM_B); // Consecutive

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::consecutiveSameSubjectSameTeacher)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void consecutiveSameSubjectSameTeacher_notPenalized_sameTeacher() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B); // Same teacher

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::consecutiveSameSubjectSameTeacher)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void consecutiveSameSubjectSameTeacher_notPenalized_notConsecutive() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Math", TEACHER2, GROUP1);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_10, ROOM_B); // Not consecutive (1 hour gap)

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::consecutiveSameSubjectSameTeacher)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void consecutiveSameSubjectSameTeacher_penalized_acrossLunchBreak() {
        // TASK-341: slot 5 (13:00-14:00) -> slot 6 (14:30-15:30) is still "consecutive"
        // for H16 purposes - lunch doesn't exempt a same-subject/different-teacher pair.
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Math", TEACHER2, GROUP1);
        Lesson l1 = lesson(1L, courseA, TS_MON_13, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_1430, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::consecutiveSameSubjectSameTeacher)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void consecutiveSameSubjectSameTeacher_notPenalized_gapAdjacentToLunch() {
        // Slot 4 (12:00-13:00) -> slot 6 (14:30-15:30): slot 5 is skipped, a real gap,
        // not merely the lunch break - must NOT be treated as consecutive.
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Math", TEACHER2, GROUP1);
        Lesson l1 = lesson(1L, courseA, TS_MON_12, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_1430, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::consecutiveSameSubjectSameTeacher)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void consecutiveSameSubjectSameTeacherRoom_penalized() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B); // Consecutive, different room

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::consecutiveSameSubjectSameTeacherSameRoom)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void consecutiveSameSubjectSameTeacherRoom_notPenalized_sameRoom() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_A); // Consecutive, same room

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::consecutiveSameSubjectSameTeacherSameRoom)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void consecutiveSameSubjectSameTeacherRoom_penalized_acrossLunchBreak() {
        // TASK-341: same teacher, different room, across the lunch break - still
        // "consecutive" for H16 purposes (a block may cross lunch, but two separate
        // lessons of the same subject/teacher must still agree on room).
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_13, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_1430, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::consecutiveSameSubjectSameTeacherSameRoom)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void consecutiveSameSubjectSameTeacherRoom_notPenalized_eitherRoomNull() {
        // M37 trap-solved L0 signal: SILENT quando una delle due stanze e' null (filtro
        // a.room!=null && b.room!=null). Da H16b hard il -1 hard su stanza diversa batteva
        // il null silente (room-null trap); ora H29 hard(1) sul room-null domina (TASK-447),
        // quindi la LS room assegna sempre una stanza quando esiste; M37 medium(100) resta
        // solo come "non cambiare aula a meta' blocco". Tier/magnitudo non assertibili via
        // ConstraintVerifier (penalizesBy e' match-count): provati dal codice + RoomPhaseSnapshotTest.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, null);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::consecutiveSameSubjectSameTeacherSameRoom)
                .given(l1, l2)
                .penalizesBy(0);
    }

    // ─── H28: No teacher return within (group, subject, day) (TASK-448, golden-first guard) ──

    @Test
    void h28_penalized_teacherReturn() {
        // A-B-A: l1(T1, slot8), l2(T2, slot9), l3(T1, slot10). Same group/subject/date.
        // Pair (l1,l3) same teacher T1, l2 strictly between with teacher T2 != T1 -> 1 return.
        Course cA = course(1L, "Math", TEACHER1, GROUP1);
        Course cB = course(2L, "Math", TEACHER2, GROUP1); // same subject+group, different teacher
        Lesson l1 = lesson(1L, cA, TS_MON_08, null);
        Lesson l2 = lesson(2L, cB, TS_MON_09, null);
        Lesson l3 = lesson(3L, cA, TS_MON_10, null);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherNoReturnWithinGroupSubjectDay)
                .given(l1, l2, l3)
                .penalizesBy(1);
    }

    @Test
    void h28_silent_singleHandoff() {
        // A-B: l1(T1, slot8), l2(T2, slot9). No same-teacher pair -> no return.
        Course cA = course(1L, "Math", TEACHER1, GROUP1);
        Course cB = course(2L, "Math", TEACHER2, GROUP1);
        Lesson l1 = lesson(1L, cA, TS_MON_08, null);
        Lesson l2 = lesson(2L, cB, TS_MON_09, null);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherNoReturnWithinGroupSubjectDay)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void h28_silent_monoTeacherDay() {
        // A-A-A: all T1, slots 8/9/10. Same-teacher pairs exist but no DIFFERENT-teacher c between.
        Course cA = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, cA, TS_MON_08, null);
        Lesson l2 = lesson(2L, cA, TS_MON_09, null);
        Lesson l3 = lesson(3L, cA, TS_MON_10, null);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherNoReturnWithinGroupSubjectDay)
                .given(l1, l2, l3)
                .penalizesBy(0);
    }

    @Test
    void h28_silent_twoContiguousBlocks() {
        // A-A-B-B: l1,l2 T1 (slots 8,9); l3,l4 T2 (slots 10,11). The golden pattern: two
        // contiguous single-handoff blocks, no return. Same-teacher pairs (l1,l2)/(l3,l4) have
        // no different-teacher c strictly between (adjacent slots).
        Course cA = course(1L, "Math", TEACHER1, GROUP1);
        Course cB = course(2L, "Math", TEACHER2, GROUP1);
        Lesson l1 = lesson(1L, cA, TS_MON_08, null);
        Lesson l2 = lesson(2L, cA, TS_MON_09, null);
        Lesson l3 = lesson(3L, cB, TS_MON_10, null);
        Lesson l4 = lesson(4L, cB, TS_MON_11, null);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherNoReturnWithinGroupSubjectDay)
                .given(l1, l2, l3, l4)
                .penalizesBy(0);
    }

    // ─── M29 (was H22): Professionalizzanti At Day Edges (TASK-370) ──────────────────

    private static final StudentGroup GROUP_FD = group(4L, "G12", 20);
    private static final LocalDate WED = LocalDate.of(2025, 10, 8); // Wednesday
    private static final LocalDate THU = LocalDate.of(2025, 10, 9); // Thursday
    private static final LocalDate FRI = LocalDate.of(2025, 10, 10); // Friday - unrestricted for G12

    private static final Timeslot TS_WED_08 = timeslot(101L, WED, 8, 0, 9, 0);   // slot 0
    private static final Timeslot TS_WED_09 = timeslot(102L, WED, 9, 0, 10, 0);  // slot 1
    private static final Timeslot TS_WED_10 = timeslot(103L, WED, 10, 0, 11, 0); // slot 2
    private static final Timeslot TS_WED_11 = timeslot(104L, WED, 11, 0, 12, 0); // slot 3
    private static final Timeslot TS_WED_12 = timeslot(105L, WED, 12, 0, 13, 0); // slot 4
    private static final Timeslot TS_THU_08 = timeslot(106L, THU, 8, 0, 9, 0);     // slot 0
    private static final Timeslot TS_THU_11 = timeslot(108L, THU, 11, 0, 12, 0);   // slot 3 - mid-day
    private static final Timeslot TS_THU_1430 = timeslot(109L, THU, 14, 30, 15, 30); // slot 6
    private static final Timeslot TS_THU_1530 = timeslot(110L, THU, 15, 30, 16, 30); // slot 7
    private static final Timeslot TS_FRI_10 = timeslot(107L, FRI, 10, 0, 11, 0); // slot 2 - mid-day

    // G12's real Wed calendar is 0-4 (5 open slots, DataGenerator.GROUP_CALENDAR_PATTERNS)
    // - not the test class's default fully-open 0-7 - so "last hour" here is slot 4, the
    // same non-literal-slot-5 case the task brief calls out.
    private static GroupCalendarRule FDWedCalendarRule() {
        return groupCalendarRule(GROUP_FD, DayOfWeek.WEDNESDAY, 0, 4);
    }

    // G12's real Thursday calendar is 0-7 (all 8 slots, DataGenerator.GROUP_CALENDAR_PATTERNS)
    // - UNLIKE Wednesday's 0-4, so "the last hour" here is slot 7, not slot 4. Slots 6-7 are
    // also H21's stage-reserved afternoon (TimeslotSequence.AFTERNOON_START_SLOT_INDEX=6):
    // H22 alone (isolated by ConstraintVerifier.verifyThat) still legally anchors there, but a
    // real solve additionally has to satisfy H21, which blocks any UNPINNED lesson at slot >=6 -
    // see the constraint's own javadoc.
    private static GroupCalendarRule FDThuCalendarRule() {
        return groupCalendarRule(GROUP_FD, DayOfWeek.THURSDAY, 0, 7);
    }

    private static Course profCourse(long id, StudentGroup group) {
        Course c = course(id, "Consulenza d'immagine", TEACHER1, group);
        c.professionalizzanti = true;
        return c;
    }

    @Test
    void professionalizzantiAtDayEdges_notPenalized_prefixOfOne() {
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(FDWedCalendarRule()));
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_WED_08, ROOM_A); // slot 0 only

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1)
                .penalizesBy(0);
    }

    @Test
    void professionalizzantiAtDayEdges_notPenalized_prefixOfTwo() {
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(FDWedCalendarRule()));
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_WED_08, ROOM_A); // slot 0
        Lesson l2 = lesson(2L, course, TS_WED_09, ROOM_A); // slot 1

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void professionalizzantiAtDayEdges_notPenalized_suffixOfOne() {
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(FDWedCalendarRule()));
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_WED_12, ROOM_A); // slot 4, the group's last open slot

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1)
                .penalizesBy(0);
    }

    @Test
    void professionalizzantiAtDayEdges_notPenalized_suffixOfTwo() {
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(FDWedCalendarRule()));
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_WED_11, ROOM_A); // slot 3
        Lesson l2 = lesson(2L, course, TS_WED_12, ROOM_A); // slot 4

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void professionalizzantiAtDayEdges_penalized_middleHour() {
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(FDWedCalendarRule()));
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_WED_10, ROOM_A); // slot 2, touches neither edge

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1)
                .penalizesBy(1);
    }

    @Test
    void professionalizzantiAtDayEdges_penalized_hourTwoWithoutHourOne() {
        // Docx: "se la 2a ora e' professionalizzante lo deve essere anche la 1a" -
        // slot 1 alone (the day's second open slot) without slot 0 is illegal.
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(FDWedCalendarRule()));
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_WED_09, ROOM_A); // slot 1 only

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1)
                .penalizesBy(1);
    }

    @Test
    void professionalizzantiAtDayEdges_penalized_runOfThreeAtEdge() {
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(FDWedCalendarRule()));
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_WED_08, ROOM_A); // slot 0
        Lesson l2 = lesson(2L, course, TS_WED_09, ROOM_A); // slot 1
        Lesson l3 = lesson(3L, course, TS_WED_10, ROOM_A); // slot 2 - touches the start edge, but run > 2

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1, l2, l3)
                .penalizesBy(1);
    }

    @Test
    void professionalizzantiAtDayEdges_notPenalized_allPinned() {
        // Day-level pinned exemption: the whole day's illegal shape is exempt only
        // when EVERY contributing lesson is pinned (historical/golden data).
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(FDWedCalendarRule()));
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_WED_10, ROOM_A, true); // slot 2, illegal shape, but pinned

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1)
                .penalizesBy(0);
    }

    @Test
    void professionalizzantiAtDayEdges_penalized_mixedPinnedAndUnpinned() {
        // Only ONE lesson in the day's set is unpinned - the whole day is still
        // evaluated in full, so the solver can't hide behind an existing pin.
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(FDWedCalendarRule()));
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_WED_08, ROOM_A, true);  // slot 0, pinned
        Lesson l2 = lesson(2L, course, TS_WED_10, ROOM_A, false); // slot 2, unpinned - gap at slot 1

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1, l2)
                .penalizesBy(1);
    }

    // Thursday's own tests, using the REAL production 0-7 window (not Wednesday's 0-4) -
    // closes the gap where every other test above only ever exercised Wednesday's shorter
    // calendar and could never catch a Thursday-specific edge-anchor mistake.

    @Test
    void professionalizzantiAtDayEdges_notPenalized_thursdaySuffixAtRealLastSlot() {
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(FDThuCalendarRule()));
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_THU_1430, ROOM_A); // slot 6
        Lesson l2 = lesson(2L, course, TS_THU_1530, ROOM_A); // slot 7, the group's REAL last open slot

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void professionalizzantiAtDayEdges_penalized_thursdayMiddleHourNotAtRealEdge() {
        // Slot 3 is neither Thursday's real first slot (0) nor its real last slot (7) - it
        // would be a legal edge on a hypothetical shorter day, but Thursday's calendar is
        // 0-7, so this must still be penalized: the anchor is resolved per-date/per-weekday
        // from the group's actual calendar, not hardcoded to Wednesday's shorter shape.
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(FDThuCalendarRule()));
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_THU_11, ROOM_A); // slot 3

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1)
                .penalizesBy(1);
    }

    @Test
    void professionalizzantiAtDayEdges_notPenalized_fridayUnrestricted() {
        // Friday is absent from H22_RESTRICTED_DAYS_BY_GROUP - the docx leaves it
        // unrestricted for G12.
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_FRI_10, ROOM_A); // mid-day, would be illegal on Wed/Thu

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1)
                .penalizesBy(0);
    }

    @Test
    void professionalizzantiAtDayEdges_notPenalized_groupNotInRuleTable() {
        // GROUP1 ("G03") isn't in the rule table at all - a mid-day professionalizzanti
        // lesson on Wed/Thu is unrestricted for it.
        Course course = course(1L, "Consulenza d'immagine", TEACHER1, GROUP1);
        course.professionalizzanti = true;
        Lesson l1 = lesson(1L, course, TS_THU_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_WED_10, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void professionalizzantiAtDayEdges_notPenalized_nonProfessionalizzantiSubject() {
        // A regular (non-professionalizzanti) subject is never restricted, even
        // mid-day on G12's Wed/Thu.
        Course course = course(1L, "Scienze", TEACHER1, GROUP_FD);
        course.professionalizzanti = false;
        Lesson l1 = lesson(1L, course, TS_WED_10, ROOM_A); // slot 2, would be illegal if professionalizzanti

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1)
                .penalizesBy(0);
    }

    // ─── H-M2: Room Suitability (hard) ─────────────────────────────────────

    @Test
    void roomSuitability_penalized() {
        Course courseA = course(1L, "Lab Work", TEACHER1, GROUP1);
        courseA.requiredRoomTags = List.of("LAB");
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomSuitability)
                .given(l1)
                .penalizesBy(1);
    }

    @Test
    void roomSuitability_notPenalized() {
        Course courseA = course(1L, "Lab Work", TEACHER1, GROUP1);
        courseA.requiredRoomTags = List.of("LAB");
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_LAB);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomSuitability)
                .given(l1)
                .penalizesBy(0);
    }

    // ─── M3: Room Capacity (L) ────────────────────────────────────────────

    @Test
    void roomCapacity_penalized() {
        Room smallRoom = room(99L, "Small Room", 15);
        Course courseA = course(1L, "Math", TEACHER1, GROUP1); // GROUP1 size = 20
        Lesson lesson1 = lesson(1L, courseA, TS_MON_08, smallRoom); // 20 > 15

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomCapacity)
                .given(lesson1)
                .penalizesBy(1);
    }

    @Test
    void roomCapacity_notPenalized_fits() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1); // size 20
        Lesson lesson1 = lesson(1L, courseA, TS_MON_08, ROOM_A); // capacity 30

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomCapacity)
                .given(lesson1)
                .penalizesBy(0);
    }

    @Test
    void roomCapacity_notPenalized_exact() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1); // size 20
        Lesson lesson1 = lesson(1L, courseA, TS_MON_08, ROOM_LAB); // capacity 20

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomCapacity)
                .given(lesson1)
                .penalizesBy(0);
    }

    @Test
    void roomCapacity_notPenalized_nullRoom() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson1 = lesson(1L, courseA, TS_MON_08, null);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::roomCapacity)
                .given(lesson1)
                .penalizesBy(0);
    }

    // ─── M-LA: Lab Assistant Conflict (L-L) ─────────────────────────────────

    @Test
    void labAssistantConflict_penalized_concurrentLabs() {
        // Teacher1 is a lab assistant for "LAB" tag rooms
        LabAssistant assistant = labAssistant(1L, TEACHER1, "LAB");
        SchoolSchedule.setLabAssistantsStatic(List.of(assistant));

        // Teacher1 has their own lesson during a lab session in "their" room
        Course assistantCourse = course(1L, "Physics", TEACHER1, GROUP1);
        Lesson assistantLesson = lesson(1L, assistantCourse, TS_MON_08, ROOM_A);

        Course labCourse = course(2L, "Lab Work", TEACHER2, GROUP2);
        labCourse.isLab = true;
        Lesson labLesson = lesson(2L, labCourse, TS_MON_08, ROOM_LAB); // LAB tag room

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labAssistantConflictLessonLesson)
                .given(assistantLesson, labLesson)
                .penalizesBy(1);
    }

    @Test
    void labAssistantConflict_notPenalized_differentTimes() {
        // Teacher1 is a lab assistant for "LAB" tag rooms
        LabAssistant assistant = labAssistant(1L, TEACHER1, "LAB");
        SchoolSchedule.setLabAssistantsStatic(List.of(assistant));

        // Teacher1 has their own lesson at a different time than the lab session
        Course assistantCourse = course(1L, "Physics", TEACHER1, GROUP1);
        Lesson assistantLesson = lesson(1L, assistantCourse, TS_MON_08, ROOM_A);

        Course labCourse = course(2L, "Lab Work", TEACHER2, GROUP2);
        labCourse.isLab = true;
        Lesson labLesson = lesson(2L, labCourse, TS_MON_09, ROOM_LAB); // Different time

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labAssistantConflictLessonLesson)
                .given(assistantLesson, labLesson)
                .penalizesBy(0);
    }

    @Test
    void labAssistantConflict_notPenalized_differentRoomTags() {
        // Teacher1 is a lab assistant for "LAB" tag rooms
        LabAssistant assistant = labAssistant(1L, TEACHER1, "LAB");
        SchoolSchedule.setLabAssistantsStatic(List.of(assistant));

        // Lab lesson is in a room without the assistant's tag
        Course assistantCourse = course(1L, "Physics", TEACHER1, GROUP1);
        Lesson assistantLesson = lesson(1L, assistantCourse, TS_MON_08, ROOM_A);

        Course labCourse = course(2L, "Lab Work", TEACHER2, GROUP2);
        labCourse.isLab = true;
        Lesson labLesson = lesson(2L, labCourse, TS_MON_08, ROOM_MECCANICA); // meccanica tag, not LAB

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labAssistantConflictLessonLesson)
                .given(assistantLesson, labLesson)
                .penalizesBy(0);
    }

    @Test
    void labAssistantConflict_notPenalized_notAnAssistant() {
        // No lab assistants configured
        SchoolSchedule.setLabAssistantsStatic(List.of());

        Course course1 = course(1L, "Physics", TEACHER1, GROUP1);
        Lesson lesson1 = lesson(1L, course1, TS_MON_08, ROOM_A);

        Course course2 = course(2L, "Lab Work", TEACHER2, GROUP2);
        course2.isLab = true;
        Lesson lesson2 = lesson(2L, course2, TS_MON_08, ROOM_LAB);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labAssistantConflictLessonLesson)
                .given(lesson1, lesson2)
                .penalizesBy(0);
    }

    // ─── M5: Reward Informatica After Stage ────────────────────────────────

    @Test
    void rewardInformaticaAfterStage_rewards() {
        Course course = course(1L, "Informatica", TEACHER1, GROUP1);
        GROUP1.name = "G01"; // Target group for M5
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_LAB);

        StagePattern stagePattern = new StagePattern(
                GROUP1,
                LocalDate.of(2025, 10, 1),
                LocalDate.of(2025, 10, 5), // Stage ends Friday
                Set.of());

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardInformaticaAfterStage)
                .given(lesson, stagePattern)
                .rewardsWith(1); // Monday is after stage ends
    }

    @Test
    void rewardInformaticaAfterStage_noReward_duringStage() {
        Course course = course(1L, "Informatica", TEACHER1, GROUP1);
        GROUP1.name = "G01"; // Target group for M5
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_LAB);

        StagePattern stagePattern = new StagePattern(
                GROUP1,
                LocalDate.of(2025, 10, 6), // Stage starts Monday
                LocalDate.of(2025, 12, 31),
                Set.of());

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardInformaticaAfterStage)
                .given(lesson, stagePattern)
                .rewardsWith(0); // Monday is during stage
    }

    // ─── M12: Overflow Beyond Target End Date (L) ───────────────────────────

    @Test
    void overflowBeyondTarget_penalized_lessonAfterTarget() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_TUE_08, ROOM_A); // TUE (Oct 7)

        // Set target date to MON (Oct 6), so TUE is 1 day over
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of(GROUP1.id, MON));

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::overflowBeyondTargetEndDate)
                .given(lesson)
                .penalizesBy(1); // 1 day over = 1000 MEDIUM
    }

    @Test
    void overflowBeyondTarget_penalized_multipleDaysOver() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_TUE_08, ROOM_A); // TUE (Oct 7)

        // Set target date to Sep 30, so TUE Oct 7 is 7 days over
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of(GROUP1.id, LocalDate.of(2025, 9, 30)));

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::overflowBeyondTargetEndDate)
                .given(lesson)
                .penalizesBy(7); // 7 days over = 7000 MEDIUM
    }

    @Test
    void overflowBeyondTarget_notPenalized_beforeTarget() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A); // MON (Oct 6)

        // Set target date to TUE (Oct 7), so MON is before target
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of(GROUP1.id, TUE));

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::overflowBeyondTargetEndDate)
                .given(lesson)
                .penalizesBy(0); // Before target = no penalty
    }

    @Test
    void overflowBeyondTarget_notPenalized_onTarget() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A); // MON (Oct 6)

        // Set target date to MON (Oct 6), so lesson is exactly on target
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of(GROUP1.id, MON));

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::overflowBeyondTargetEndDate)
                .given(lesson)
                .penalizesBy(0); // On target date = no penalty
    }

    @Test
    void overflowBeyondTarget_notPenalized_pinned() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_TUE_08, ROOM_A, true); // Pinned

        // Set target date to MON (Oct 6), so TUE is 1 day over (but pinned)
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of(GROUP1.id, MON));

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::overflowBeyondTargetEndDate)
                .given(lesson)
                .penalizesBy(0); // Pinned lessons are exempt
    }

    @Test
    void overflowBeyondTarget_notPenalized_noTargetDate() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_TUE_08, ROOM_A);

        // No target date set for this group
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::overflowBeyondTargetEndDate)
                .given(lesson)
                .penalizesBy(0); // No target date = no penalty
    }

    // ─── M13: Vertically Compact (medium) ─────────────────────────────────
    // TASK-243: Tests to verify toConsecutiveSequences() produces correct
    // gap penalties equivalent to the original toList() approach.

    @Test
    void verticallyCompact_penalized_gapInConsecutiveDays() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_TUE_08, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::verticallyCompact)
                .given(l1, l2)
                .penalizesBy(0); // Monday to Tuesday = different days = no penalty
    }

    @Test
    void verticallyCompact_penalized_gapWithinSameDay() {
        // Lessons at 08:00, 10:00, 12:00 (gaps between them)
        // Slot indices: 0, 2, 4
        // Gaps: between 0-2 (1 slot), 2-4 (1 slot) = 2 gaps
        // Weight: 500 per gap = 1000 medium penalty
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_10, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_12, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::verticallyCompact)
                .given(l1, l2, l3)
                .penalizesBy(2); // 2 gaps (raw count, weight applied separately = 1000)
    }

    @Test
    void verticallyCompact_notPenalized_consecutiveLessons() {
        // Lessons at 08:00, 09:00, 10:00 (consecutive slots)
        // Slot indices: 0, 1, 2
        // No gaps expected
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::verticallyCompact)
                .given(l1, l2, l3)
                .penalizesBy(0); // Consecutive = no gaps
    }

    @Test
    void verticallyCompact_penalized_largerGap() {
        // Lessons at 08:00, 09:00, 13:00
        // Slot indices: 0, 1, 5
        // Gap between 1 and 5: missing slots 2,3,4 = 3 gaps
        // Weight: 500 per gap = 1500 medium penalty
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_13, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::verticallyCompact)
                .given(l1, l2, l3)
                .penalizesBy(3); // 3 gaps (raw count, weight applied separately = 1500)
    }

    @Test
    void verticallyCompact_notPenalized_acrossLunchBreak() {
        // TASK-341: slot 5 (13:00-14:00) -> slot 6 (14:30-15:30). Lunch is not a gap.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_13, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_1430, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::verticallyCompact)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void verticallyCompact_penalized_gapAdjacentToLunch() {
        // Slot 4 (12:00-13:00) -> slot 6 (14:30-15:30): slot 5 is skipped, a real
        // 1-slot gap, even though it sits right next to the lunch break.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_12, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_1430, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::verticallyCompact)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void verticallyCompact_notPenalized_bothSidesPinned() {
        // TASK-350: a break is exempt when BOTH the previous and next lesson are pinned
        // (the golden/historical import case - e.g. T01/G10/2026-12-17's 13:00
        // hole across lunch, a genuine documented afternoon-contiguity exception).
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, course, TS_MON_11, ROOM_B, true); // gap at 9, 10, both sides pinned

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::verticallyCompact)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void verticallyCompact_penalized_unpinnedGap() {
        // TASK-350: an all-unpinned gap (the solver leaving its own hole in a
        // student group's day) must still be a hard violation - the pinned
        // exemption never weakens the constraint for the solver's own placements.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, false);
        Lesson l2 = lesson(2L, course, TS_MON_11, ROOM_B, false); // gap at 9, 10 = 2 slots

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::verticallyCompact)
                .given(l1, l2)
                .penalizesBy(2);
    }

    @Test
    void verticallyCompact_penalized_oneSidePinned() {
        // TASK-350: only ONE side of the break pinned - not exempt. The solver must
        // not be allowed to create a fresh gap against a pinned lesson.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, course, TS_MON_11, ROOM_B, false); // gap at 9, 10, only l1 pinned

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::verticallyCompact)
                .given(l1, l2)
                .penalizesBy(2);
    }

    @Test
    void verticallyCompact_penalized_otherSidePinned() {
        // TASK-350: the symmetric case of oneSidePinned above - previous side
        // unpinned, next side pinned.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, false);
        Lesson l2 = lesson(2L, course, TS_MON_11, ROOM_B, true); // gap at 9, 10, only l2 pinned

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::verticallyCompact)
                .given(l1, l2)
                .penalizesBy(2);
    }

    // ─── H6: School Holiday (L) ────────────────────────────────────────────

    @Test
    void schoolHoliday_penalized() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

        SchoolHoliday holiday = holiday(1L, MON); // 2025-10-06

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::schoolHoliday)
                .given(lesson, holiday)
                .penalizesBy(1);
    }

    @Test
    void schoolHoliday_notPenalized_regularDay() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

        SchoolHoliday holiday = holiday(1L, TUE); // Different day

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::schoolHoliday)
                .given(lesson, holiday)
                .penalizesBy(0);
    }

    @Test
    void schoolHoliday_notPenalized_noHolidays() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::schoolHoliday)
                .given(lesson)
                .penalizesBy(0);
    }

    // ─── H7: Max Teacher Daily Hours ───────────────────────────────────────

    @Test
    void maxTeacherDailyHours_penalized_7lessons() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);
        Lesson l6 = lesson(6L, course, TS_MON_13, ROOM_C);
        Lesson l7 = lesson(7L, course, TS_MON_1430, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxTeacherDailyHours)
                .given(l1, l2, l3, l4, l5, l6, l7)
                .penalizesBy(1);
    }

    @Test
    void maxTeacherDailyHours_notPenalized_6lessons() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);
        Lesson l6 = lesson(6L, course, TS_MON_13, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxTeacherDailyHours)
                .given(l1, l2, l3, l4, l5, l6)
                .penalizesBy(0);
    }

    // TASK-346 M1: a pseudo teacher (e.g. "Stage") has no real daily-hours cap -
    // 8h in one day (above Teacher.DEFAULT_MAX_DAILY_HOURS) must not be penalized.
    @Test
    void maxTeacherDailyHours_notPenalized_pseudoTeacherEightHours() {
        Teacher pseudoStage = teacher(3L, "Stage", true);
        Course course = course(1L, "Stage", pseudoStage, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, null);
        Lesson l2 = lesson(2L, course, TS_MON_09, null);
        Lesson l3 = lesson(3L, course, TS_MON_10, null);
        Lesson l4 = lesson(4L, course, TS_MON_11, null);
        Lesson l5 = lesson(5L, course, TS_MON_12, null);
        Lesson l6 = lesson(6L, course, TS_MON_13, null);
        Lesson l7 = lesson(7L, course, TS_MON_1430, null);
        Lesson l8 = lesson(8L, course, TS_MON_1530, null);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxTeacherDailyHours)
                .given(l1, l2, l3, l4, l5, l6, l7, l8)
                .penalizesBy(0);
    }

    // TASK-348: per-teacher H7 override - T01's real 8h/day full-day lab
    // (analysis.md section 4.2) must not be penalized when maxDailyHours=8.
    @Test
    void maxTeacherDailyHours_notPenalized_8lessons_forTeacherWithMaxDailyHours8() {
        Teacher t01 = teacherWithMaxDailyHours(3L, "T01", 8);
        Course course = course(1L, "Acconciatura", t01, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);
        Lesson l6 = lesson(6L, course, TS_MON_13, ROOM_C);
        Lesson l7 = lesson(7L, course, TS_MON_1430, ROOM_A);
        Lesson l8 = lesson(8L, course, TS_MON_1530, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxTeacherDailyHours)
                .given(l1, l2, l3, l4, l5, l6, l7, l8)
                .penalizesBy(0);
    }

    // Even a per-teacher override has a ceiling: 9h in one day exceeds
    // maxDailyHours=8 and must still be penalized (by the excess of 1).
    @Test
    void maxTeacherDailyHours_penalized_9lessons_forTeacherWithMaxDailyHours8() {
        Teacher t01 = teacherWithMaxDailyHours(3L, "T01", 8);
        Course course = course(1L, "Acconciatura", t01, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);
        Lesson l6 = lesson(6L, course, TS_MON_13, ROOM_C);
        Lesson l7 = lesson(7L, course, TS_MON_1430, ROOM_A);
        Lesson l8 = lesson(8L, course, TS_MON_1530, ROOM_B);
        Lesson l9 = lesson(9L, course, TS_MON_1630, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxTeacherDailyHours)
                .given(l1, l2, l3, l4, l5, l6, l7, l8, l9)
                .penalizesBy(1);
    }

    @Test
    void maxTeacherDailyHours_notPenalized_differentDays() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_TUE_08, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_TUE_09, ROOM_B);
        Lesson l6 = lesson(6L, course, TS_TUE_10, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxTeacherDailyHours)
                .given(l1, l2, l3, l4, l5, l6)
                .penalizesBy(0);
    }

    // ─── H11: Max Student Group Daily Hours ─────────────────────────────────

    @Test
    void maxStudentGroupDailyHours_penalized_exceedsLimit() {
        StudentGroup groupMax4 = group(3L, "G01", 20, 4);
        Course course = course(1L, "Math", TEACHER1, groupMax4);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxStudentGroupDailyHours)
                .given(l1, l2, l3, l4, l5)
                .penalizesBy(1); // 5 hours - 4 limit = 1 penalty
    }

    @Test
    void maxStudentGroupDailyHours_notPenalized_withinLimit() {
        StudentGroup groupMax4 = group(3L, "G01", 20, 4);
        Course course = course(1L, "Math", TEACHER1, groupMax4);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxStudentGroupDailyHours)
                .given(l1, l2, l3, l4)
                .penalizesBy(0);
    }

    @Test
    void maxStudentGroupDailyHours_notPenalized_nullLimit() {
        // GROUP1 has maxHoursPerDay = null (unlimited)
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);
        Lesson l6 = lesson(6L, course, TS_MON_13, ROOM_C);
        Lesson l7 = lesson(7L, course, TS_MON_1430, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::maxStudentGroupDailyHours)
                .given(l1, l2, l3, l4, l5, l6, l7)
                .penalizesBy(0); // No limit = no penalty
    }

    // ─── M18: 5-hour Groups Must Start From First Slot ────────────────────────

    // TASK-350 moved M18's gate from the static maxHoursPerDay field to the day's
    // calendar-open-slot count; TASK-405 removed the gate entirely (every group-day
    // anchors to its first calendar-open slot, penalty = offset slots x ofMedium(250)).
    // With no GroupCalendarRule at all, a day's window defaults to minOpenSlot 0.

    @Test
    void dayShouldStartFromFirstOpenSlot_penalized_startsAtSlot1() {
        StudentGroup groupMax5 = group(3L, "G01", 20, 5);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(
                groupCalendarRule(groupMax5, DayOfWeek.MONDAY, 0, 4))); // 5-slot day, gated
        Course course = course(1L, "Math", TEACHER1, groupMax5);
        Lesson l1 = lesson(1L, course, TS_MON_09, ROOM_A); // first lesson at slot 1, not slot 0

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::dayShouldStartFromFirstOpenSlot)
                .given(l1)
                .penalizesBy(1);
    }

    @Test
    void dayShouldStartFromFirstOpenSlot_notPenalized_startsAtSlot0() {
        StudentGroup groupMax5 = group(3L, "G01", 20, 5);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(
                groupCalendarRule(groupMax5, DayOfWeek.MONDAY, 0, 4))); // 5-slot day, gated
        Course course = course(1L, "Math", TEACHER1, groupMax5);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A); // first lesson at slot 0
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::dayShouldStartFromFirstOpenSlot)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void dayShouldStartFromFirstOpenSlot_penalized_sixHourGroupAfterWidening() {
        // TASK-405: the <=5-open-slots gate is GONE - every group-day anchors to its
        // first open slot now (golden anchors 99.3% of days; the floating concentrated
        // exactly in the wide-window groups the old gate excluded). GROUP1's Monday has
        // no calendar rule, so the window defaults to minOpenSlot 0; starting at slot 1
        // is offset 1.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_09, ROOM_A); // starts late -> now penalized

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::dayShouldStartFromFirstOpenSlot)
                .given(l1)
                .penalizesBy(1);
    }

    @Test
    void dayShouldStartFromFirstOpenSlot_penalized_longCalendarDayAfterWidening() {
        // TASK-405: the mixed-calendar long day (G12's Thursday shape) used to be
        // gated OUT (task-350); with the widening it anchors like every other day.
        StudentGroup groupMax5 = group(3L, "G01", 20, 5);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(
                groupCalendarRule(groupMax5, DayOfWeek.MONDAY, 0, 7))); // 8-slot day
        Course course = course(1L, "Math", TEACHER1, groupMax5);
        Lesson l1 = lesson(1L, course, TS_MON_09, ROOM_A); // starts at slot 1 -> offset 1

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::dayShouldStartFromFirstOpenSlot)
                .given(l1)
                .penalizesBy(1);
    }

    @Test
    void dayShouldStartFromFirstOpenSlot_gradient_offsetSlotsNotFlat() {
        // TASK-405: the penalty is a GRADIENT (offset slots), not flat-per-day - a day
        // starting at slot 3 against an open-at-0 window weighs 3, giving the local
        // search a descent direction instead of a cliff.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_11, ROOM_A); // first lesson at slot 3

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::dayShouldStartFromFirstOpenSlot)
                .given(l1)
                .penalizesBy(3);
    }

    // TASK-342: "start of day" is now the group's calendar-open slot, not a
    // hardcoded slot 0 — these two tests cover a group whose calendar is
    // date-scoped to open at slot 1 (the first-school-day ramp scenario).

    @Test
    void dayShouldStartFromFirstOpenSlot_notPenalized_startsAtCalendarOpenSlot() {
        StudentGroup groupMax5 = group(3L, "G01", 20, 5);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(
                groupCalendarRule(groupMax5, DayOfWeek.MONDAY, 1, 4, MON, MON)));
        Course course = course(1L, "Math", TEACHER1, groupMax5);
        Lesson l1 = lesson(1L, course, TS_MON_09, ROOM_A); // slot 1 - the group's actual first open slot

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::dayShouldStartFromFirstOpenSlot)
                .given(l1)
                .penalizesBy(0);
    }

    @Test
    void dayShouldStartFromFirstOpenSlot_penalized_startsAfterCalendarOpenSlot() {
        StudentGroup groupMax5 = group(3L, "G01", 20, 5);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(
                groupCalendarRule(groupMax5, DayOfWeek.MONDAY, 1, 4, MON, MON)));
        Course course = course(1L, "Math", TEACHER1, groupMax5);
        Lesson l1 = lesson(1L, course, TS_MON_10, ROOM_A); // slot 2, later than the group's open slot 1

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::dayShouldStartFromFirstOpenSlot)
                .given(l1)
                .penalizesBy(1);
    }

    // TASK-342 Z3: a fully closed calendar day (maxSlot < minSlot sentinel) has no "first
    // open slot" to anchor on. getGroupCalendarWindow()'s minOpenSlot is -1 for this
    // degenerate case, and M18 must not penalize against it (a lesson placed there is
    // H20's problem).
    @Test
    void dayShouldStartFromFirstOpenSlot_notPenalized_closedDaySentinel() {
        StudentGroup groupMax5 = group(3L, "G01", 20, 5);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(
                groupCalendarRule(groupMax5, DayOfWeek.MONDAY, 0, -1))); // closed-day sentinel
        Course course = course(1L, "Math", TEACHER1, groupMax5);
        Lesson l1 = lesson(1L, course, TS_MON_10, ROOM_A); // should never exist here, but must not crash/penalize

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::dayShouldStartFromFirstOpenSlot)
                .given(l1)
                .penalizesBy(0);
    }

    // ─── H20: Group Calendar Closed Slot (TASK-342) ────────────────────────

    @Test
    void groupCalendarClosedSlot_notPenalized_noRuleDefaultsOpen() {
        // No GroupCalendarRule at all for this group -> every slot is open
        // (backward compatible with the pre-TASK-342 model).
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_1530, ROOM_A); // slot 7 (last slot)

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::groupCalendarClosedSlot)
                .given(l1)
                .penalizesBy(0);
    }

    @Test
    void groupCalendarClosedSlot_notPenalized_slotInsideRange() {
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(
                groupCalendarRule(GROUP1, DayOfWeek.MONDAY, 0, 7)));
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::groupCalendarClosedSlot)
                .given(l1)
                .penalizesBy(0);
    }

    @Test
    void groupCalendarClosedSlot_penalized_slotOutsideRange() {
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(
                groupCalendarRule(GROUP1, DayOfWeek.MONDAY, 0, 4))); // closed from slot 5 onward
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_1430, ROOM_A); // slot 6, outside 0-4

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::groupCalendarClosedSlot)
                .given(l1)
                .penalizesBy(1);
    }

    @Test
    void groupCalendarClosedSlot_dateScopedOverride_mostSpecificWins() {
        // A wide-open year-round Monday rule, plus a narrow single-date override
        // (the first-school-day-ramp shape) — the narrow rule must win.
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(
                groupCalendarRule(GROUP1, DayOfWeek.MONDAY, 0, 7),
                groupCalendarRule(GROUP1, DayOfWeek.MONDAY, 1, 4, MON, MON)));
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A); // slot 0, closed by the narrow override

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::groupCalendarClosedSlot)
                .given(l1)
                .penalizesBy(1);
    }

    // ─── M28 (was H21): Afternoon Slots Reserved For Stage (TASK-372) ─────────────────

    @Test
    void afternoonSlotsReservedForStage_penalized_nullTeacherSlot6() {
        // include-null semantics (H4 precedent): an explicitly-unassigned lesson
        // must not park in the reserved afternoon either.
        Course course = course(1L, "Math", null, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_1430, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::afternoonSlotsReservedForStage)
                .given(l1)
                .penalizesBy(1);
    }

    @Test
    void afternoonSlotsReservedForStage_penalized_unpinnedRealTeacherSlot6() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_1430, ROOM_A); // slot 6, unpinned, real teacher

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::afternoonSlotsReservedForStage)
                .given(l1)
                .penalizesBy(1);
    }

    @Test
    void afternoonSlotsReservedForStage_notPenalized_pinnedSlot6() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_1430, ROOM_A, true); // pinned - golden/historical import

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::afternoonSlotsReservedForStage)
                .given(l1)
                .penalizesBy(0);
    }

    @Test
    void afternoonSlotsReservedForStage_notPenalized_pseudoStageTeacherSlot6() {
        Teacher stageTeacher = teacher(9L, "Stage", true); // pseudo accounting teacher (TASK-346)
        Course course = course(1L, "Stage", stageTeacher, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_1430, ROOM_A); // slot 6, unpinned, pseudo teacher

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::afternoonSlotsReservedForStage)
                .given(l1)
                .penalizesBy(0);
    }

    @Test
    void afternoonSlotsReservedForStage_notPenalized_slot5() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_13, ROOM_A); // slot 5 (13:00-14:00), before the afternoon

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::afternoonSlotsReservedForStage)
                .given(l1)
                .penalizesBy(0);
    }

    // ─── M20: No Sparse Student Days (horizontal compaction) ─────────────────

    @Test
    void horizontallyCompact_penalized_1hour() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(l1)
                .penalizesBy(10_000); // deficit 5 (quota 6 - 1h) -> m20DeficitCost(5)
    }

    @Test
    void horizontallyCompact_penalized_2hours() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(l1, l2)
                .penalizesBy(7_000); // deficit 4 (quota 6 - 2h) -> m20DeficitCost(4)
    }

    @Test
    void horizontallyCompact_penalized_3hours() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(l1, l2, l3)
                .penalizesBy(4_500); // deficit 3 (quota 6 - 3h) -> m20DeficitCost(3)
    }

    @Test
    void horizontallyCompact_penalized_4hours() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(l1, l2, l3, l4)
                .penalizesBy(2_500); // deficit 2 (quota 6 - 4h) -> m20DeficitCost(2)
    }

    @Test
    void horizontallyCompact_notPenalized_6hours() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);
        Lesson l6 = lesson(6L, course, TS_MON_13, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(l1, l2, l3, l4, l5, l6)
                .penalizesBy(0); // quota 6 - 6 = 0 (meets default quota)
    }

    @Test
    void horizontallyCompact_notPenalized_pinned() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, true); // Pinned

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(l1)
                .penalizesBy(0); // Pinned lessons are exempt
    }

    // TASK-496 removal (2026-08-16): G14 is no longer exempt from the fill family.
    // The G14-specific exemption tests were removed; the general fill tests cover the
    // constraint logic for ALL groups including G14 (same quota, same pricing).

    // TASK-342 Z1: a group with no maxHoursPerDay falls back to DEFAULT_DAILY_QUOTA (6),
    // but its calendar may close well before slot 6 on a given weekday (e.g. a 5-slot
    // Friday). Without capping the quota at the calendar's open-slot count, such a day
    // would be penalized every time it's used, even when completely full.
    @Test
    void horizontallyCompact_notPenalized_calendarCappedQuota_fullyUsedShortFriday() {
        StudentGroup groupNoMax = group(4L, "G03", 20); // maxHoursPerDay left null
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(
                groupCalendarRule(groupNoMax, DayOfWeek.FRIDAY, 0, 4))); // 5 open slots (0-4)
        LocalDate friday = LocalDate.of(2025, 10, 10); // Friday of the MON/TUE test week
        Course course = course(1L, "Math", TEACHER1, groupNoMax);
        Lesson l1 = lesson(1L, course, timeslot(20L, friday, 8, 0, 9, 0), ROOM_A);
        Lesson l2 = lesson(2L, course, timeslot(21L, friday, 9, 0, 10, 0), ROOM_B);
        Lesson l3 = lesson(3L, course, timeslot(22L, friday, 10, 0, 11, 0), ROOM_C);
        Lesson l4 = lesson(4L, course, timeslot(23L, friday, 11, 0, 12, 0), ROOM_A);
        Lesson l5 = lesson(5L, course, timeslot(24L, friday, 12, 0, 13, 0), ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(l1, l2, l3, l4, l5)
                .penalizesBy(0); // 5 hours fills the calendar-capped quota of 5, not the fallback of 6
    }

    @Test
    void horizontallyCompact_penalized_calendarCappedQuota_shortOfCalendarQuota() {
        StudentGroup groupNoMax = group(4L, "G03", 20); // maxHoursPerDay left null
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(
                groupCalendarRule(groupNoMax, DayOfWeek.FRIDAY, 0, 4))); // 5 open slots (0-4)
        LocalDate friday = LocalDate.of(2025, 10, 10);
        Course course = course(1L, "Math", TEACHER1, groupNoMax);
        Lesson l1 = lesson(1L, course, timeslot(20L, friday, 8, 0, 9, 0), ROOM_A);
        Lesson l2 = lesson(2L, course, timeslot(21L, friday, 9, 0, 10, 0), ROOM_B);
        Lesson l3 = lesson(3L, course, timeslot(22L, friday, 10, 0, 11, 0), ROOM_C);
        Lesson l4 = lesson(4L, course, timeslot(23L, friday, 11, 0, 12, 0), ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(l1, l2, l3, l4)
                // deficit 1 -> 1000, the UNCHANGED first-missing-hour price (calendar-capped
                // quota 5 - 4 hours = 1, not the fallback quota's 2)
                .penalizesBy(1_000);
    }

    // ─── M33: Surplus beyond expected day hours (TASK-423) ───────────────────
    // M20's mirror image on the SAME population and the SAME dailyQuota target:
    // GROUP1 leaves maxHoursPerDay null, so its quota is the fallback 6 here.

    @Test
    void m33_penalized_twoHoursAboveQuota() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);
        Lesson l6 = lesson(6L, course, TS_MON_13, ROOM_C);
        Lesson l7 = lesson(7L, course, TS_MON_1430, ROOM_A);
        Lesson l8 = lesson(8L, course, TS_MON_1530, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::surplusBeyondExpectedHours)
                .given(l1, l2, l3, l4, l5, l6, l7, l8)
                // 8 hours on a quota-6 day -> 2 surplus hours x 100/hour.
                // Hardcoded like the neighbouring M20 cases: the weigher is what is
                // under test, so re-deriving it from the constant would make the
                // assertion true by construction for any future weight.
                .penalizesBy(200);
    }

    @Test
    void m33_notPenalized_exactlyAtQuota() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);
        Lesson l6 = lesson(6L, course, TS_MON_13, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::surplusBeyondExpectedHours)
                .given(l1, l2, l3, l4, l5, l6)
                .penalizesBy(0); // a full day is not a surplus day
    }

    @Test
    void m33_notPenalized_authoritativePinsDoNotCount() {
        // The school's own pins are not the solver's doing: a day 2 hours above
        // quota entirely by pinned lessons is exempt, exactly as it is for M20's
        // deficit count (isAuthoritativePin, the shared population rule). Since
        // TASK-424 the netted target floors at 0 (quota 6 - 8 pinned hours), and
        // the counted occupancy is 0 too, so the school's own overfill stays free.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B, true);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C, true);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A, true);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B, true);
        Lesson l6 = lesson(6L, course, TS_MON_13, ROOM_C, true);
        Lesson l7 = lesson(7L, course, TS_MON_1430, ROOM_A, true);
        Lesson l8 = lesson(8L, course, TS_MON_1530, ROOM_B, true);
        publishAuthoritativePins(l1, l2, l3, l4, l5, l6, l7, l8);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::surplusBeyondExpectedHours)
                .given(l1, l2, l3, l4, l5, l6, l7, l8)
                .penalizesBy(0);
    }

    // ─── TASK-424: the day-fill target is NET of the school's own pinned hours ───
    // The 267 mixed days of val419-train-run1 all reported a deficit that does not
    // exist, because the occupancy was pinned-exempt while the target was the FULL
    // day plan. Each case below is one of the two dominant patterns.

    /**
     * THE 137-DAY PATTERN: expected 6, 3 authoritative pins, 3 solver hours - a day
     * exactly on plan. Before TASK-424 M20 charged {@code m20DeficitCost(3)} = 4,500
     * of phantom deficit here and paid 2,000 of marginal gain for a 4th unpinned hour;
     * netted (quota 6 - 3 pins = 3) the day is at target and costs nothing.
     */
    @Test
    void m20_notPenalized_mixedPinnedDayExactlyOnPlan() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson p1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson p2 = lesson(2L, course, TS_MON_09, ROOM_B, true);
        Lesson p3 = lesson(3L, course, TS_MON_10, ROOM_C, true);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);
        Lesson l6 = lesson(6L, course, TS_MON_13, ROOM_C);
        publishAuthoritativePins(p1, p2, p3);

        assertEquals(3, SchoolScheduleConstraintProvider.effectiveQuota(GROUP1, MON),
                "quota 6 net of the 3 authoritative pinned hours");
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(p1, p2, p3, l4, l5, l6)
                .penalizesBy(0);
    }

    /**
     * The other half of the same fix: on that day a 4th unpinned hour is no longer
     * PAID for by M20 (it is not a deficit day at all any more), and M33 charges it
     * as the surplus it is - the incentive that produced the above-plan days is gone.
     */
    @Test
    void m20_mixedPinnedDayPaysNothingForAnHourBeyondPlan_m33ChargesIt() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson p1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson p2 = lesson(2L, course, TS_MON_09, ROOM_B, true);
        Lesson p3 = lesson(3L, course, TS_MON_10, ROOM_C, true);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);
        Lesson l6 = lesson(6L, course, TS_MON_13, ROOM_C);
        Lesson l7 = lesson(7L, course, TS_MON_1430, ROOM_A);   // the 4th unpinned hour
        publishAuthoritativePins(p1, p2, p3);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(p1, p2, p3, l4, l5, l6, l7)
                .penalizesBy(0);   // nothing gained by adding it (was: 4,500 -> 2,500, a 2,000 gain)
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::surplusBeyondExpectedHours)
                .given(p1, p2, p3, l4, l5, l6, l7)
                .penalizesBy(100);  // occupancy 4 against an effective quota of 3
    }

    /**
     * THE 31-DAY PATTERN: expected 6, 6 authoritative pins, nothing of the solver's.
     * M20 used to charge {@code m20DeficitCost(6)} = 13,500 and would have paid to
     * DOUBLE the day (only H11/openSlots capped it). Netted, the day owes nothing and
     * every solver hour landing on it is pure M33 surplus.
     */
    @Test
    void m20_notPenalized_fullyPinnedDayOwesNothing() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson[] pins = new Lesson[6];
        Timeslot[] slots = {TS_MON_08, TS_MON_09, TS_MON_10, TS_MON_11, TS_MON_12, TS_MON_13};
        for (int i = 0; i < slots.length; i++) {
            pins[i] = lesson(i + 1L, course, slots[i], ROOM_A, true);
        }
        publishAuthoritativePins(pins);

        assertEquals(0, SchoolScheduleConstraintProvider.effectiveQuota(GROUP1, MON),
                "a day whose whole plan the school already pinned owes the solver nothing");
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given((Object[]) pins)
                .penalizesBy(0);

        Lesson extra = lesson(7L, course, TS_MON_1430, ROOM_A);
        Object[] withExtra = {pins[0], pins[1], pins[2], pins[3], pins[4], pins[5], extra};
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(withExtra)
                .penalizesBy(0);
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::surplusBeyondExpectedHours)
                .given(withExtra)
                // the raw quota is still 6, so the day is in SCOPE (m20Quota > 0) and the
                // single solver hour is charged against the effective quota of 0
                .penalizesBy(100);
    }

    /**
     * Pinned STAGE hours are never netted: the target is aula+lab only (TASK-385), so
     * subtracting pseudo-teacher hours would shrink a target they never inflated. The
     * day below is 2 pinned Stage hours + 3 solver hours on a quota-6 day, so the
     * deficit stays 3 (4,500) - it would read 1 (1,000) if Stage were netted.
     */
    @Test
    void m20_pinnedStageHoursAreNotNettedFromTheQuota() {
        Teacher stageTeacher = teacher(9L, "Stage", true);
        Course stageCourse = course(2L, "Stage", stageTeacher, GROUP1);
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson s1 = lesson(1L, stageCourse, TS_MON_08, ROOM_A, true);
        Lesson s2 = lesson(2L, stageCourse, TS_MON_09, ROOM_B, true);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);
        publishAuthoritativePins(s1, s2, l3, l4, l5);

        assertEquals(0, SchoolSchedule.getAuthoritativePinnedHours(GROUP1.id, MON),
                "pseudo-teacher (Stage) pins never enter the netting basis");
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given(s1, s2, l3, l4, l5)
                .penalizesBy(4_500);   // deficit 3 against the full quota of 6
    }

    /**
     * A PROMOTED pin (TASK-419) is not the school's statement, so it is never netted:
     * it keeps counting as ordinary occupancy instead. Netting it too would take the
     * same hour off both sides of the comparison, and the score would jump the moment
     * the smarcatura released it.
     */
    @Test
    void m20_promotedPinsAreNotNettedFromTheQuota() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson[] day = fullMondayFor(course, true);   // 5 pinned + 1 free
        promote(1L, 2L, 3L, 4L, 5L);                  // ... but those 5 pins are PROMOTED
        publishAuthoritativePins(day);

        assertEquals(0, SchoolSchedule.getAuthoritativePinnedHours(GROUP1.id, MON),
                "promoted pins carry no statement from the school - never netted");
        assertEquals(6, SchoolScheduleConstraintProvider.effectiveQuota(GROUP1, MON));
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given((Object[]) day)
                .penalizesBy(0);   // occupancy 6 (all six count) against the full quota 6
    }

    /**
     * The netting map is published from the same lesson list as the pinned-timeslot
     * map ({@code SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic}) - this is the
     * test-side mirror of {@code ScheduleService.refreshSolverStatics}/{@code
     * refreshPinStatics}, the only two places production publishes it from.
     */
    private static void publishAuthoritativePins(Lesson... lessons) {
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of(lessons));
    }

    /**
     * TASK-424 - the mixed-day blindness this test used to PIN as known is now FIXED.
     *
     * <p>A day of 3 authoritative pins + 5 solver hours is really at 8 hours, i.e. 2
     * above its quota of 6. Before TASK-424 M33 charged 0 for it (the shared population
     * counts only the 5 non-pinned hours, comfortably under the FULL quota of 6) while
     * M20 read the very same day as 1 hour SHORT and paid the local search to fill it
     * further - 29 of the 112 above-plan days of train-run1 were invisible for exactly
     * this reason. The target is now netted of the school's own pinned hours
     * ({@code effectiveQuota} = 6 - 3 = 3), so the counted occupancy 5 is measured
     * against the capacity the solver actually owns: 2 hours of genuine surplus.
     *
     * <p>The fix stayed where the blindness was - in the TARGET, shared by both sides -
     * so M33 still counts exactly what M20 counts and the solver has nothing to
     * arbitrage between them.
     */
    @Test
    void m33_penalized_mixedPinnedDayIsMeasuredAgainstTheNettedQuota() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        // 3 authoritative pins - exempt from the shared occupancy count, netted OUT of the target
        Lesson p1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson p2 = lesson(2L, course, TS_MON_09, ROOM_B, true);
        Lesson p3 = lesson(3L, course, TS_MON_10, ROOM_C, true);
        // 5 solver-placed hours - the only ones M20/M33 count
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);
        Lesson l5 = lesson(5L, course, TS_MON_12, ROOM_B);
        Lesson l6 = lesson(6L, course, TS_MON_13, ROOM_C);
        Lesson l7 = lesson(7L, course, TS_MON_1430, ROOM_A);
        Lesson l8 = lesson(8L, course, TS_MON_1530, ROOM_B);
        publishAuthoritativePins(p1, p2, p3);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::surplusBeyondExpectedHours)
                .given(p1, p2, p3, l4, l5, l6, l7, l8)
                // occupancy 5 against an effective quota of 3 -> 2 surplus hours x 100
                .penalizesBy(200);
    }


    @Test
    void m33_notPenalized_noSessionDayIsOutOfScope() {
        // GROUP_FC is covered by the sheet (it has a Tuesday row), so Monday is a
        // no-session date: dailyQuota is 0 there and every hour would read as
        // surplus. H26 already forbids those lessons at the HARD tier, so M33 stays
        // out of it rather than charging medium noise on top (quota > 0 gate).
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, TUE, 5, 0, 0)));
        Course course = FCAulaCourse(1L, GROUP_FC);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::surplusBeyondExpectedHours)
                .given(l1, l2)
                .penalizesBy(0);
    }

    // ─── M20 progressive weigher (2026-07-26 golden-first recalibration) ─────

    /**
     * The whole point of the progression, on ONE fixture: the same group, the
     * same 6-hour quota, days of 5/4/3/2 placed hours. Flat 1000/hour would give
     * 1000/2000/3000/4000; the golden shaves (98 of its 99 partial days are
     * exactly -1h) and never digs, so the deeper the hole the dearer the hour.
     */
    @Test
    void m20_deficitCostIsProgressiveInTheDepthOfTheHole() {
        assertEquals(0, SchoolScheduleConstraintProvider.m20DeficitCost(0), "a full day is free");
        assertEquals(1_000, SchoolScheduleConstraintProvider.m20DeficitCost(1),
                "the first missing hour keeps costing exactly what it did before the progression");
        assertEquals(2_500, SchoolScheduleConstraintProvider.m20DeficitCost(2));
        assertEquals(4_500, SchoolScheduleConstraintProvider.m20DeficitCost(3));
        assertEquals(7_000, SchoolScheduleConstraintProvider.m20DeficitCost(4));
        assertEquals(10_000, SchoolScheduleConstraintProvider.m20DeficitCost(5));
        // marginal cost of the n-th missing hour: 1000, 1500, 2000, 2500, ...
        for (int d = 2; d <= 7; d++) {
            assertEquals(SchoolScheduleConstraintProvider.M20_FIRST_HOUR_WEIGHT
                            + (long) SchoolScheduleConstraintProvider.M20_PROGRESSION_STEP * (d - 1),
                    SchoolScheduleConstraintProvider.m20DeficitCost(d)
                            - SchoolScheduleConstraintProvider.m20DeficitCost(d - 1),
                    "marginal cost of missing hour " + d);
        }
    }

    /**
     * Same-shape end-to-end check through the constraint itself (quota 6, GROUP1):
     * one 5h day costs 1000 while five 5h days across the week cost 5x1000, but a
     * single day missing 5 hours costs 10,000 - i.e. concentrating the same
     * deficit is now strictly more expensive than spreading it.
     */
    @Test
    void m20_spreadingADeficitIsCheaperThanConcentratingIt() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        // Monday 5h + Tuesday 5h: two days at -1h each = 2 x 1000
        Lesson[] spread = {
                lesson(1L, course, TS_MON_08, ROOM_A), lesson(2L, course, TS_MON_09, ROOM_B),
                lesson(3L, course, TS_MON_10, ROOM_C), lesson(4L, course, TS_MON_11, ROOM_A),
                lesson(5L, course, TS_MON_12, ROOM_B),
                lesson(6L, course, TS_TUE_08, ROOM_A), lesson(7L, course, TS_TUE_09, ROOM_B),
                lesson(8L, course, TS_TUE_10, ROOM_C),
                lesson(9L, course, timeslot(30L, TUE, 11, 0, 12, 0), ROOM_A),
                lesson(10L, course, timeslot(31L, TUE, 12, 0, 13, 0), ROOM_B),
        };
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given((Object[]) spread)
                .penalizesBy(2_000);

        // The same 10 hours' worth of demand landing as one 9h... impossible here;
        // the concentrated counterpart is a single 1h day: -5h on one day.
        Lesson[] concentrated = {lesson(11L, course, TS_MON_08, ROOM_A)};
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given((Object[]) concentrated)
                .penalizesBy(10_000);
    }

    /**
     * M20 and M20.2 are ONE cost curve read at different depths, pinned as a test
     * so they cannot silently drift apart again: emptying a day must never be
     * cheaper than leaving hours on it, at any quota and any depth. M20.2 charges
     * {@code m20DeficitCost(quota)} (full depth) precisely so this holds; while it
     * charged the linear {@code 1000 x quota} against this progression, every
     * quota >= 4 paid the solver to evacuate (17,500 vs 8,000 at quota 8), which
     * the golden never does (2 empty days in 2,213 expected group-days).
     */
    @Test
    void m20_2_evacuationIsNeverCheaperThanKeepingHoursOnTheDay() {
        for (int quota = 1; quota <= 8; quota++) {
            int evacuatedDay = SchoolScheduleConstraintProvider.m20DeficitCost(quota); // M20.2's weigher
            for (int placed = 1; placed < quota; placed++) {
                int partialDay = SchoolScheduleConstraintProvider.m20DeficitCost(quota - placed);
                assertTrue(evacuatedDay >= partialDay,
                        "quota " + quota + ": evacuating (" + evacuatedDay + ") must cost at least as much as "
                                + "keeping " + placed + "h on the day (" + partialDay + ")");
            }
        }
        // Spot-check the case the linear weigher got backwards: quota 8, one hour left.
        // Cumulative marginals 1000,1500,...,4500: cost(8) = 22,000 (the original
        // 20,000 expectation was a hand-summing slip caught by the first run).
        assertEquals(22_000, SchoolScheduleConstraintProvider.m20DeficitCost(8), "M20.2 at quota 8");
        assertEquals(17_500, SchoolScheduleConstraintProvider.m20DeficitCost(7), "1h day at quota 8");
    }

    // ─── M21: Max 2h Per Subject Per Day (threshold raised TASK-381 "L3") ────

    @Test
    void m21MaxHoursPerSubjectPerDay_notPenalized_2hours() {
        // TASK-381: threshold raised 1h -> 2h (golden-first - the school routinely
        // teaches ordinary-subject 2h blocks despite the docx wording).
        Course course = course(1L, "Math", TEACHER1, GROUP1, false); // Not a lab
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::m21MaxHoursPerSubjectPerDay)
                .given(l1, l2)
                .penalizesBy(0); // 2h is now the free threshold
    }

    @Test
    void m21MaxHoursPerSubjectPerDay_penalized_3hours() {
        Course course = course(1L, "Math", TEACHER1, GROUP1, false);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::m21MaxHoursPerSubjectPerDay)
                .given(l1, l2, l3)
                .penalizesBy(1); // 3 - 2 = 1 penalty
    }

    @Test
    void m21MaxHoursPerSubjectPerDay_penalized_4hours() {
        Course course = course(1L, "Math", TEACHER1, GROUP1, false);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);
        Lesson l4 = lesson(4L, course, TS_MON_11, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::m21MaxHoursPerSubjectPerDay)
                .given(l1, l2, l3, l4)
                .penalizesBy(2); // 4 - 2 = 2 penalties
    }

    @Test
    void m21MaxHoursPerSubjectPerDay_notPenalized_labLessons() {
        Course course = course(1L, "Lab Work", TEACHER1, GROUP1, true); // Is a lab
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_LAB);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_LAB);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_LAB);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::m21MaxHoursPerSubjectPerDay)
                .given(l1, l2, l3)
                .penalizesBy(0); // Lab lessons are exempt
    }

    @Test
    void m21MaxHoursPerSubjectPerDay_notPenalized_1hour() {
        Course course = course(1L, "Math", TEACHER1, GROUP1, false);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::m21MaxHoursPerSubjectPerDay)
                .given(l1)
                .penalizesBy(0);
    }

    @Test
    void m21MaxHoursPerSubjectPerDay_notPenalized_differentDays() {
        Course course = course(1L, "Math", TEACHER1, GROUP1, false);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_TUE_08, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::m21MaxHoursPerSubjectPerDay)
                .given(l1, l2)
                .penalizesBy(0); // Different days = OK
    }

    // TASK-365: Q18 school answer whitelist (Competenze Digitali + both Disegno
    // variants) - not lab-tagged, but exempt from M21 the same as labs (the
    // whitelist filter runs before the threshold check, so it short-circuits
    // regardless of hour count).
    // TASK-381: 3 lessons (not 2) - a volume that WOULD trigger a penalty for
    // a non-exempt subject under the new 2h threshold, confirming the
    // whitelist still holds at that volume.
    @Test
    void m21MaxHoursPerSubjectPerDay_notPenalized_whitelistedSubject() {
        Course course = course(1L, "Disegno", TEACHER1, GROUP1, false); // Not a lab, but whitelisted
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::m21MaxHoursPerSubjectPerDay)
                .given(l1, l2, l3)
                .penalizesBy(0); // Disegno is on the M21_EXEMPT_SUBJECTS whitelist
    }

    // TASK-365: a pinned pseudo-teacher lesson (e.g. the synthetic "Stage" course)
    // is one long accounting block split across daily timeslots, not a genuine
    // same-subject repeat - same exemption class H4/H7/S1 already apply.
    @Test
    void m21MaxHoursPerSubjectPerDay_notPenalized_pseudoTeacher() {
        Teacher pseudoStage = teacher(3L, "Stage", true);
        Course course = course(1L, "Stage", pseudoStage, GROUP1, false);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::m21MaxHoursPerSubjectPerDay)
                .given(l1, l2)
                .penalizesBy(0); // Pseudo-teacher lessons are exempt
    }

    // TASK-365 code review: Course.subject has no NOT NULL constraint (CourseResource
    // copies the REST payload verbatim), and SubjectNormalization.normalize() NPEs on a
    // null key (backed by Map.of(), which rejects null). A null subject can never match
    // the whitelist, so it must fall through to being counted normally, not crash.
    // TASK-381: 3 lessons (not 2) so this still exercises the null-subject path beyond
    // the now-free 2h threshold.
    @Test
    void m21MaxHoursPerSubjectPerDay_penalized_nullSubjectDoesNotThrow() {
        Course course = course(1L, null, TEACHER1, GROUP1, false);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::m21MaxHoursPerSubjectPerDay)
                .given(l1, l2, l3)
                .penalizesBy(1); // Null subject isn't whitelisted - counted like any other subject
    }

    // ─── M24: At Most One Early Start Day Per Week (TASK-368, T11) ───────

    @Test
    void atMostOneEarlyStartDayPerWeek_penalized_twoEarlyStartDaysSameWeek() {
        Teacher t11 = teacherWithMaxEarlyStartDaysPerWeek(10L, "T11", 1);
        Course course = course(1L, "Math", t11, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A); // MON slot 0
        Lesson l2 = lesson(2L, course, TS_TUE_08, ROOM_B); // TUE slot 0, same ISO week

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::atMostOneEarlyStartDayPerWeek)
                .given(l1, l2)
                .penalizesBy(1); // 2 early-start days - cap 1 = 1 penalty
    }

    @Test
    void atMostOneEarlyStartDayPerWeek_notPenalized_oneEarlyStartDay() {
        Teacher t11 = teacherWithMaxEarlyStartDaysPerWeek(10L, "T11", 1);
        Course course = course(1L, "Math", t11, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B); // same day, not slot 0

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::atMostOneEarlyStartDayPerWeek)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void atMostOneEarlyStartDayPerWeek_notPenalized_differentWeeks() {
        Teacher t11 = teacherWithMaxEarlyStartDaysPerWeek(10L, "T11", 1);
        Course course = course(1L, "Math", t11, GROUP1);
        Timeslot nextWeekMon08 = timeslot(600L, MON.plusDays(7), 8, 0, 9, 0);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);       // week N, slot 0
        Lesson l2 = lesson(2L, course, nextWeekMon08, ROOM_B);   // week N+1, slot 0

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::atMostOneEarlyStartDayPerWeek)
                .given(l1, l2)
                .penalizesBy(0); // 1 early-start day per week, in each of 2 separate weeks
    }

    @Test
    void atMostOneEarlyStartDayPerWeek_notPenalized_noOverrideConfigured() {
        // TEACHER1 has no maxEarlyStartDaysPerWeek override - constraint is inert for him.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_TUE_08, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::atMostOneEarlyStartDayPerWeek)
                .given(l1, l2)
                .penalizesBy(0);
    }

    // ─── M25: Required Free Day Per Week (TASK-368, T05) ───────────────────

    @Test
    void requiredFreeDayPerWeek_penalized_allFiveDaysUsed() {
        Teacher t05 = teacherWithRequiredFreeDaysPerWeek(11L, "T05", 1);
        Course course = course(1L, "Math", t05, GROUP1);
        Timeslot wed = timeslot(601L, MON.plusDays(2), 8, 0, 9, 0);
        Timeslot thu = timeslot(602L, MON.plusDays(3), 8, 0, 9, 0);
        Timeslot fri = timeslot(603L, MON.plusDays(4), 8, 0, 9, 0);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_TUE_08, ROOM_A);
        Lesson l3 = lesson(3L, course, wed, ROOM_A);
        Lesson l4 = lesson(4L, course, thu, ROOM_A);
        Lesson l5 = lesson(5L, course, fri, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::requiredFreeDayPerWeek)
                .given(l1, l2, l3, l4, l5)
                .penalizesBy(1); // 5 used days, 0 free, required 1 -> shortfall 1
    }

    @Test
    void requiredFreeDayPerWeek_notPenalized_oneFreeDayMeetsRequirement() {
        Teacher t05 = teacherWithRequiredFreeDaysPerWeek(11L, "T05", 1);
        Course course = course(1L, "Math", t05, GROUP1);
        Timeslot wed = timeslot(601L, MON.plusDays(2), 8, 0, 9, 0);
        Timeslot thu = timeslot(602L, MON.plusDays(3), 8, 0, 9, 0);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_TUE_08, ROOM_A);
        Lesson l3 = lesson(3L, course, wed, ROOM_A);
        Lesson l4 = lesson(4L, course, thu, ROOM_A);
        // Friday unused -> 1 free day, meets requirement of 1.

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::requiredFreeDayPerWeek)
                .given(l1, l2, l3, l4)
                .penalizesBy(0);
    }

    @Test
    void requiredFreeDayPerWeek_penalized_forT05sActualSeededRequirementOfTwo() {
        // Mirrors the real seeded value (Teacher.requiredFreeDaysPerWeek=2 for
        // T05, see its javadoc for why 2 and not the docx's literal 1).
        Teacher t05 = teacherWithRequiredFreeDaysPerWeek(11L, "T05", 2);
        Course course = course(1L, "Math", t05, GROUP1);
        Timeslot wed = timeslot(601L, MON.plusDays(2), 8, 0, 9, 0);
        Timeslot thu = timeslot(602L, MON.plusDays(3), 8, 0, 9, 0);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_TUE_08, ROOM_A);
        Lesson l3 = lesson(3L, course, wed, ROOM_A);
        Lesson l4 = lesson(4L, course, thu, ROOM_A);
        // Friday unused -> 1 free day, short of the requirement of 2 (only 1 shy).

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::requiredFreeDayPerWeek)
                .given(l1, l2, l3, l4)
                .penalizesBy(1);
    }

    @Test
    void requiredFreeDayPerWeek_notPenalized_noOverrideConfigured() {
        // TEACHER1 has no requiredFreeDaysPerWeek override - constraint is inert for him.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Timeslot wed = timeslot(601L, MON.plusDays(2), 8, 0, 9, 0);
        Timeslot thu = timeslot(602L, MON.plusDays(3), 8, 0, 9, 0);
        Timeslot fri = timeslot(603L, MON.plusDays(4), 8, 0, 9, 0);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_TUE_08, ROOM_A);
        Lesson l3 = lesson(3L, course, wed, ROOM_A);
        Lesson l4 = lesson(4L, course, thu, ROOM_A);
        Lesson l5 = lesson(5L, course, fri, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::requiredFreeDayPerWeek)
                .given(l1, l2, l3, l4, l5)
                .penalizesBy(0);
    }

    // ─── S1: Minimize Teacher Gaps ───────────────────────────────────────────

    // TASK-346 M1: a pseudo teacher (e.g. "Stage") has no real gaps to minimize.
    @Test
    void minimizeTeacherGaps_notPenalized_pseudoTeacher() {
        Teacher pseudoStage = teacher(3L, "Stage", true);
        Course course = course(1L, "Stage", pseudoStage, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, null);
        Lesson l2 = lesson(2L, course, TS_MON_10, null); // 1-hour gap, would penalize a real teacher

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::minimizeTeacherGaps)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void minimizeTeacherGaps_penalized_gapBetweenLessons() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_10, ROOM_B); // 1-hour gap

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::minimizeTeacherGaps)
                .given(l1, l2)
                .penalizesBy(60); // 60 minutes gap (2 SOFT per minute = 120 SOFT)
    }

    @Test
    void minimizeTeacherGaps_penalized_multipleGaps() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_10, ROOM_B); // 1-hour gap
        Lesson l3 = lesson(3L, course, TS_MON_11, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::minimizeTeacherGaps)
                .given(l1, l2, l3)
                .penalizesBy(60); // 60 minutes total gaps
    }

    @Test
    void minimizeTeacherGaps_notPenalized_consecutive() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::minimizeTeacherGaps)
                .given(l1, l2)
                .penalizesBy(0); // No gap
    }

    @Test
    void minimizeTeacherGaps_penalized_structuralBreakExcluded() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_13, ROOM_A); // 13:00-14:00
        Lesson l2 = lesson(2L, course, TS_MON_1430, ROOM_B); // 14:30-15:30

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::minimizeTeacherGaps)
                .given(l1, l2)
                .penalizesBy(0); // 30-minute gap = structural break (14:00-14:30)
    }

    @Test
    void minimizeTeacherGaps_penalized_realGapSpanningLunchBreak() {
        // 12:00-13:00 then 15:30-16:30: real elapsed gap is 150 minutes, of which
        // 30 are the 14:00-14:30 lunch break -> 120 minutes of actual teacher gap.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_12, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_1530, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::minimizeTeacherGaps)
                .given(l1, l2)
                .penalizesBy(120);
    }

    // ─── S17: Reward Same-Subject Block Pair (TASK-381 "L3") ──────────────────

    @Test
    void rewardSameSubjectBlockPair_rewards_adjacentPair() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardSameSubjectBlockPair)
                .given(l1, l2)
                .rewardsWith(1); // 1 adjacent pair = 5 SOFT reward
    }

    @Test
    void rewardSameSubjectBlockPair_rewards_threeLessons() {
        // 3 contiguous hours = 2 adjacent pairs: (l1,l2) and (l2,l3), NOT (l1,l3)
        // (not adjacent) - same "adjacent pairs, not all pairs" shape as S7.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardSameSubjectBlockPair)
                .given(l1, l2, l3)
                .rewardsWith(2); // 2 adjacent pairs = 10 SOFT reward
    }

    @Test
    void rewardSameSubjectBlockPair_noReward_notAdjacent() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_10, ROOM_B); // gap at slot 1

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardSameSubjectBlockPair)
                .given(l1, l2)
                .rewardsWith(0);
    }

    @Test
    void rewardSameSubjectBlockPair_noReward_differentSubject() {
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Italiano", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardSameSubjectBlockPair)
                .given(l1, l2)
                .rewardsWith(0);
    }

    @Test
    void rewardSameSubjectBlockPair_noReward_pseudoTeacher() {
        // TASK-381: an 8h Stage day is one long accounting block, not a genuine
        // subject repeat - same exemption class M21/S12/S13 already apply.
        Teacher pseudoStage = teacher(3L, "Stage", true);
        Course course = course(1L, "Stage", pseudoStage, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardSameSubjectBlockPair)
                .given(l1, l2)
                .rewardsWith(0);
    }

    @Test
    void rewardSameSubjectBlockPair_rewards_differentTeacher() {
        // Deliberately does NOT require the same teacher (S16 already penalizes
        // a teacher handoff softly) - requiring it here would double-count.
        Course courseA = course(1L, "Math", TEACHER1, GROUP1);
        Course courseB = course(2L, "Math", TEACHER2, GROUP1);
        Lesson l1 = lesson(1L, courseA, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, courseB, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardSameSubjectBlockPair)
                .given(l1, l2)
                .rewardsWith(1);
    }

    // ─── S3: Teacher Preference ──────────────────────────────────────────────

    @Test
    void teacherPreference_penalized() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

        TeacherAvailability ta = new TeacherAvailability(
                TEACHER1,
                LocalDateTime.of(2025, 10, 6, 8, 0),
                LocalDateTime.of(2025, 10, 6, 9, 0),
                AvailabilityType.PREFERENCE);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherPreference)
                .given(lesson, ta)
                .penalizesBy(1);
    }

    @Test
    void teacherPreference_notPenalized_vacationType() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

        TeacherAvailability ta = new TeacherAvailability(
                TEACHER1,
                LocalDateTime.of(2025, 10, 6, 8, 0),
                LocalDateTime.of(2025, 10, 6, 9, 0),
                AvailabilityType.VACATION);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherPreference)
                .given(lesson, ta)
                .penalizesBy(0); // VACATION type, not PREFERENCE
    }

    @Test
    void teacherPreference_notPenalized_noOverlap() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

        TeacherAvailability ta = new TeacherAvailability(
                TEACHER1,
                LocalDateTime.of(2025, 10, 6, 10, 0),
                LocalDateTime.of(2025, 10, 6, 11, 0),
                AvailabilityType.PREFERENCE);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::teacherPreference)
                .given(lesson, ta)
                .penalizesBy(0); // No overlap
    }

    // ─── S7: Reward Consecutive Student Lessons ─────────────────────────────

    @Test
    void rewardConsecutiveStudentLessons_rewards_adjacent() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardConsecutiveStudentLessons)
                .given(l1, l2)
                .rewardsWith(1); // 1 pair = 3 SOFT reward
    }

    @Test
    void rewardConsecutiveStudentLessons_rewards_30minGap() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_13, ROOM_A); // 13:00-14:00
        Lesson l2 = lesson(2L, course, TS_MON_1430, ROOM_B); // 14:30-15:30, 30min gap

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardConsecutiveStudentLessons)
                .given(l1, l2)
                .rewardsWith(1); // 30min gap = adjacent
    }

    @Test
    void rewardConsecutiveStudentLessons_noReward_notAdjacent() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_10, ROOM_B); // 1-hour gap

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardConsecutiveStudentLessons)
                .given(l1, l2)
                .rewardsWith(0); // Not adjacent (gap > 30min)
    }

    @Test
    void rewardConsecutiveStudentLessons_rewards_threeLessons() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardConsecutiveStudentLessons)
                .given(l1, l2, l3)
                .rewardsWith(2); // 2 adjacent pairs: (1,2) and (2,3)
    }

    @Test
    void rewardConsecutiveStudentLessons_rewards_acrossLunchBreak() {
        // TASK-341: slot 5 (13:00-14:00) -> slot 6 (14:30-15:30) via the shared
        // TimeslotSequence.isAdjacent() utility - lunch is not a gap.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_13, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_1430, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardConsecutiveStudentLessons)
                .given(l1, l2)
                .rewardsWith(1);
    }

    @Test
    void rewardConsecutiveStudentLessons_rewards_acrossLunchBreak_reverseIdOrder() {
        // Same lunch-crossing pair as above, but with the lower id assigned to the
        // chronologically LATER lesson - isAdjacent() must be order-independent.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_1430, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_13, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardConsecutiveStudentLessons)
                .given(l1, l2)
                .rewardsWith(1);
    }

    @Test
    void rewardConsecutiveStudentLessons_noReward_gapAdjacentToLunch() {
        // Slot 4 (12:00-13:00) -> slot 6 (14:30-15:30): slot 5 is skipped, a real
        // gap, not merely the lunch break - must NOT be rewarded as consecutive.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_12, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_1430, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::rewardConsecutiveStudentLessons)
                .given(l1, l2)
                .rewardsWith(0);
    }

    // ─── S8: Isolated Student Lesson ─────────────────────────────────────────

    @Test
    void penalizeIsolatedStudentLessons_penalized_singleLesson() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::penalizeIsolatedStudentLessons)
                .given(l1)
                .penalizesBy(1); // No adjacent lessons = isolated
    }

    @Test
    void penalizeIsolatedStudentLessons_notPenalized_twoAdjacent() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::penalizeIsolatedStudentLessons)
                .given(l1, l2)
                .penalizesBy(0); // Each has adjacent lesson
    }

    @Test
    void penalizeIsolatedStudentLessons_penalized_oneIsolated() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_13, ROOM_C); // Isolated (4-hour gap)

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::penalizeIsolatedStudentLessons)
                .given(l1, l2, l3)
                .penalizesBy(1); // l3 is isolated
    }

    @Test
    void penalizeIsolatedStudentLessons_notPenalized_acrossLunchBreak() {
        // TASK-341: slot 5 (13:00-14:00) and slot 6 (14:30-15:30) count as adjacent
        // via the shared TimeslotSequence.isAdjacent() utility - neither is isolated.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_13, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_1430, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::penalizeIsolatedStudentLessons)
                .given(l1, l2)
                .penalizesBy(0);
    }

    // ─── S9: Dilute Hours Over Year ─────────────────────────────────────────
    // DISABLED (task-273): S9 is commented out in defineConstraints(). Tests removed.
    // The method diluteHoursOverYear() still exists in the provider but is not registered.

    // ─── S10: Discourage Generic Lessons in Labs ────────────────────────────

    @Test
    void labRoomReserved_penalized_genericInLab() {
        Course course = course(1L, "Math", TEACHER1, GROUP1); // No required tags
        course.requiredRoomTags = List.of();
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_LAB); // LAB-tagged room

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labRoomReserved)
                .given(lesson)
                .penalizesBy(1); // Generic lesson in lab room (50 SOFT)
    }

    @Test
    void labRoomReserved_notPenalized_courseRequiresLab() {
        Course course = course(1L, "Lab Work", TEACHER1, GROUP1);
        course.requiredRoomTags = List.of("LAB");
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_LAB);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labRoomReserved)
                .given(lesson)
                .penalizesBy(0); // Course requires LAB tag
    }

    @Test
    void labRoomReserved_notPenalized_genericInRegularRoom() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        course.requiredRoomTags = List.of();
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A); // No tags

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labRoomReserved)
                .given(lesson)
                .penalizesBy(0); // Regular room
    }

    @Test
    void labRoomReserved_notPenalized_courseRequiresDifferentTag() {
        Course course = course(1L, "Mechanics", TEACHER1, GROUP1);
        course.requiredRoomTags = List.of("meccanica");
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_LAB); // LAB tag, not meccanica

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labRoomReserved)
                .given(lesson)
                .penalizesBy(1); // LAB tag not in required tags
    }

    // ─── S11: Student Group Room Affinity ───────────────────────────────────

    @Test
    void studentGroupRoomAffinity_penalized_twoRooms() {
        Course course = course(1L, "Math", TEACHER1, GROUP1, false);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupRoomAffinity)
                .given(l1, l2)
                .penalizesBy(1); // 2 rooms - 1 = 1 penalty (10 SOFT)
    }

    @Test
    void studentGroupRoomAffinity_penalized_threeRooms() {
        Course course = course(1L, "Math", TEACHER1, GROUP1, false);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupRoomAffinity)
                .given(l1, l2, l3)
                .penalizesBy(2); // 3 rooms - 1 = 2 penalties
    }

    @Test
    void studentGroupRoomAffinity_notPenalized_oneRoom() {
        Course course = course(1L, "Math", TEACHER1, GROUP1, false);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupRoomAffinity)
                .given(l1, l2)
                .penalizesBy(0); // Same room
    }

    @Test
    void studentGroupRoomAffinity_notPenalized_labLessons() {
        Course course = course(1L, "Lab Work", TEACHER1, GROUP1, true); // Is a lab
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupRoomAffinity)
                .given(l1, l2)
                .penalizesBy(0); // Lab lessons are exempt
    }

    @Test
    void studentGroupRoomAffinity_notPenalized_pinned() {
        Course course = course(1L, "Math", TEACHER1, GROUP1, false);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, true); // Pinned
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B, true); // Pinned

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupRoomAffinity)
                .given(l1, l2)
                .penalizesBy(0); // Pinned lessons are exempt
    }

    // ─── M26: Sicurezza Before Stage (TASK-369, T03) ───────────────────

    private static final StagePattern STAGE_STARTS_MON = new StagePattern(GROUP1, MON, LocalDate.of(2025, 12, 31), Set.of());

    @Test
    void sicurezzaBeforeStage_penalized_afterStageStart() {
        Course course = course(1L, "Sicurezza", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_TUE_08, ROOM_A); // TUE (Oct 7)

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sicurezzaBeforeStage)
                .given(lesson, STAGE_STARTS_MON)
                .penalizesBy(1); // TUE is after the MON stage start
    }

    @Test
    void sicurezzaBeforeStage_notPenalized_beforeStageStart() {
        Course course = course(1L, "Sicurezza", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A); // MON
        StagePattern stagePattern = new StagePattern(GROUP1, TUE, LocalDate.of(2025, 12, 31), Set.of());

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sicurezzaBeforeStage)
                .given(lesson, stagePattern)
                .penalizesBy(0); // MON is before the TUE stage start
    }

    @Test
    void sicurezzaBeforeStage_penalized_onStageStartDate() {
        Course course = course(1L, "Sicurezza", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A); // MON

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sicurezzaBeforeStage)
                .given(lesson, STAGE_STARTS_MON)
                .penalizesBy(1); // On the start date itself is not "before" the stage
    }

    @Test
    void sicurezzaBeforeStage_notPenalized_nullStartDate() {
        Course course = course(1L, "Sicurezza", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_TUE_08, ROOM_A);
        StagePattern stagePattern = new StagePattern(GROUP1, null, LocalDate.of(2025, 12, 31), Set.of());

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sicurezzaBeforeStage)
                .given(lesson, stagePattern)
                .penalizesBy(0); // Null startDate short-circuits before the isBefore() null-deref
    }

    @Test
    void sicurezzaBeforeStage_notPenalized_differentSubject() {
        Course course = course(1L, "Matematica", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_TUE_08, ROOM_A); // TUE, after stage start

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sicurezzaBeforeStage)
                .given(lesson, STAGE_STARTS_MON)
                .penalizesBy(0); // Only Sicurezza is subject to this rule
    }

    @Test
    void sicurezzaBeforeStage_notPenalized_noStagePattern() {
        Course course = course(1L, "Sicurezza", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_TUE_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sicurezzaBeforeStage)
                .given(lesson) // No StagePattern fact - group has none seeded
                .penalizesBy(0);
    }

    @Test
    void sicurezzaBeforeStage_notPenalized_nullSubjectDoesNotThrow() {
        Course course = course(1L, null, TEACHER1, GROUP1, false);
        Lesson lesson = lesson(1L, course, TS_TUE_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sicurezzaBeforeStage)
                .given(lesson, STAGE_STARTS_MON)
                .penalizesBy(0); // Null subject short-circuits before SubjectNormalization.normalize()
    }

    // ─── M27: T20 Subject Start-Period Preference (TASK-369) ──────────────
    //
    // Dates below are relative to the real TimeslotSequence.SCHOOL_YEAR_START
    // (2026-09-14, a Monday) - unlike the rest of this file's MON/TUE
    // constants (2025-10-06/07), which are arbitrary and irrelevant to this
    // constraint's week-offset computation.

    private static final Timeslot TS_WEEK0 = timeslot(700L, LocalDate.of(2026, 9, 14), 8, 0, 9, 0);
    private static final Timeslot TS_WEEK3 = timeslot(701L, LocalDate.of(2026, 10, 5), 8, 0, 9, 0); // first day of week 3
    private static final Timeslot TS_WEEK4 = timeslot(702L, LocalDate.of(2026, 10, 12), 8, 0, 9, 0); // first day of week 4
    private static final Timeslot TS_WEEK5 = timeslot(703L, LocalDate.of(2026, 10, 19), 8, 0, 9, 0);

    @Test
    void T20SubjectStartPeriod_penalized_earlySubjectStartsTooLate() {
        Course course = course(1L, "Merceologia", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_WEEK4, ROOM_A); // week 4 > T20_EARLY_MAX_START_WEEK=3

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::T20SubjectStartPeriod)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void T20SubjectStartPeriod_notPenalized_earlySubjectWithinWindow() {
        Course course = course(1L, "Arredi Tessili", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_WEEK3, ROOM_A); // week 3 == max, still in window

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::T20SubjectStartPeriod)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void T20SubjectStartPeriod_penalized_lateSubjectStartsTooEarly() {
        Course course = course(1L, "Tecnologia tessile", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_WEEK3, ROOM_A); // week 3 < T20_LATE_MIN_START_WEEK=4

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::T20SubjectStartPeriod)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void T20SubjectStartPeriod_notPenalized_lateSubjectWithinWindow() {
        Course course = course(1L, "Storia della moda", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_WEEK4, ROOM_A); // week 4 == min, in window

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::T20SubjectStartPeriod)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void T20SubjectStartPeriod_notPenalized_unconstrainedSubject() {
        Course course = course(1L, "Figurino", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_WEEK5, ROOM_A); // "quando si vuole" - never evaluated

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::T20SubjectStartPeriod)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void T20SubjectStartPeriod_usesEarliestLesson_notLatest() {
        Course course = course(1L, "Merceologia", TEACHER1, GROUP1);
        Lesson early = lesson(1L, course, TS_WEEK0, ROOM_A); // in window
        Lesson late = lesson(2L, course, TS_WEEK5, ROOM_B); // would be out of window alone

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::T20SubjectStartPeriod)
                .given(early, late)
                .penalizesBy(0); // min(date) is week 0, well within the window
    }

    // ─── S12: Weekly Subject Presence (TASK-369) ─────────────────────────────

    @Test
    void weeklySubjectPresence_rewards_onceForSameWeek() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_TUE_08, ROOM_B); // same ISO week as MON

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklySubjectPresence)
                .given(l1, l2)
                .rewardsWith(1); // Both lessons collapse into one (group, subject, week) match
    }

    @Test
    void weeklySubjectPresence_rewards_twiceForTwoDistinctWeeks() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Timeslot nextWeekMon = timeslot(710L, MON.plusDays(7), 8, 0, 9, 0);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, nextWeekMon, ROOM_B);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklySubjectPresence)
                .given(l1, l2)
                .rewardsWith(2);
    }

    @Test
    void weeklySubjectPresence_notRewarded_pseudoTeacher() {
        Teacher pseudoStage = teacher(3L, "Stage", true);
        Course course = course(1L, "Stage", pseudoStage, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_08, null);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklySubjectPresence)
                .given(lesson)
                .rewardsWith(0);
    }

    @Test
    void weeklySubjectPresence_rewards_onlyOnce_forCaseDuplicateSubjectSpellings() {
        // Mirrors SubjectNormalization.SUBJECT_CASE_NORMALIZATION ("Lingua italiana" -> "Lingua Italiana").
        Course course1 = course(1L, "Lingua italiana", TEACHER1, GROUP1);
        Course course2 = course(2L, "Lingua Italiana", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course1, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course2, TS_TUE_08, ROOM_B); // same week, case-duplicate subject

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklySubjectPresence)
                .given(l1, l2)
                .rewardsWith(1); // Normalized to the same subject before grouping
    }

    // ─── S13: Weekly Stability (TASK-375) ────────────────────────────────────

    private static final Timeslot NEXT_WEEK_MON =
            timeslot(720L, MON.plusDays(SchoolScheduleConstraintProvider.WEEKLY_STABILITY_WINDOW_DAYS), 8, 0, 9, 0);

    @Test
    void weeklyStability_rewardsMatchingPreviousWeekCell() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson previousWeek = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson current = lesson(2L, course, NEXT_WEEK_MON, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklyStability)
                .given(previousWeek, current)
                .rewardsWith(1);
    }

    @Test
    void weeklyStability_neutral_onTeacherAndSubjectMismatch() {
        Course courseMath = course(1L, "Math", TEACHER1, GROUP1);
        Course courseScience = course(2L, "Science", TEACHER2, GROUP1);
        Lesson previousWeek = lesson(1L, courseMath, TS_MON_08, ROOM_A);
        Lesson current = lesson(2L, courseScience, NEXT_WEEK_MON, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklyStability)
                .given(previousWeek, current)
                .rewardsWith(0);
    }

    @Test
    void weeklyStability_neutral_onTeacherMismatchOnly() {
        // Same subject, different teacher - isolates the Joiners.equal(Lesson::getTeacher)
        // clause from the subject one, so a regression that drops just the teacher
        // joiner can't hide behind weeklyStability_neutral_onTeacherAndSubjectMismatch.
        Course courseTeacher1 = course(1L, "Math", TEACHER1, GROUP1);
        Course courseTeacher2 = course(2L, "Math", TEACHER2, GROUP1);
        Lesson previousWeek = lesson(1L, courseTeacher1, TS_MON_08, ROOM_A);
        Lesson current = lesson(2L, courseTeacher2, NEXT_WEEK_MON, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklyStability)
                .given(previousWeek, current)
                .rewardsWith(0);
    }

    @Test
    void weeklyStability_neutral_onSubjectMismatchOnly() {
        // Same teacher, different subject - isolates the normalized-subject Joiners.equal
        // clause from the teacher one, for the same reason as the teacher-only case above.
        Course courseMath = course(1L, "Math", TEACHER1, GROUP1);
        Course courseScience = course(2L, "Science", TEACHER1, GROUP1);
        Lesson previousWeek = lesson(1L, courseMath, TS_MON_08, ROOM_A);
        Lesson current = lesson(2L, courseScience, NEXT_WEEK_MON, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklyStability)
                .given(previousWeek, current)
                .rewardsWith(0);
    }

    @Test
    void weeklyStability_neutral_whenPreviousWeekAbsent_closureGap() {
        // No lesson exists 7 days before "current" (e.g. the previous week was a
        // school closure, or this is the course's first-ever week) - reward-only,
        // so absence must be neutral, never a penalty.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson current = lesson(1L, course, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklyStability)
                .given(current)
                .rewardsWith(0);
    }

    @Test
    void weeklyStability_excludesPinnedLessonFromEarningReward() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson previousWeek = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson pinnedCurrent = lesson(2L, course, NEXT_WEEK_MON, ROOM_A, true); // pinned - excluded from earning

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklyStability)
                .given(previousWeek, pinnedCurrent)
                .rewardsWith(0);
    }

    @Test
    void weeklyStability_pinnedLessonCanServeAsPreviousWeekAnchor() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson pinnedAnchor = lesson(1L, course, TS_MON_08, ROOM_A, true); // pinned anchor - allowed
        Lesson current = lesson(2L, course, NEXT_WEEK_MON, ROOM_A); // unpinned earner

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklyStability)
                .given(pinnedAnchor, current)
                .rewardsWith(1);
    }

    // ========================================================================
    // H23/H24/M30/M31 Tests (TASK-381 "L1-bis" — Daily Activity Quota)
    // ========================================================================

    private static final StudentGroup GROUP_FC = group(5L, "G04", 20);

    private static Course FCLabCourse(long id, StudentGroup group) {
        return course(id, "Lavorazioni meccaniche", TEACHER1, group); // G04's quota-bound lab subject
    }

    private static Course FCAulaCourse(long id, StudentGroup group) {
        return course(id, "Matematica", TEACHER1, group); // not in QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP
    }

    @Test
    void h23_penalized_boundLabSubjectOnZeroLabQuotaDay() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 5, 0, 0)));
        Lesson lesson = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::quotaBoundLabLessonOnZeroLabQuotaDay)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void h23_penalized_noQuotaRowAtAllForThatDate() {
        // GROUP_FC IS a quota group (has a row on TUE), but MON has no row at
        // all - an absent row means fully closed (DailyActivityQuota's own javadoc).
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, TUE, 5, 5, 0)));
        Lesson lesson = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::quotaBoundLabLessonOnZeroLabQuotaDay)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void h23_notPenalized_positiveLabQuotaDay() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 0, 3, 0)));
        Lesson lesson = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::quotaBoundLabLessonOnZeroLabQuotaDay)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void h23_notPenalized_pinned() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 5, 0, 0)));
        Lesson lesson = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A, true);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::quotaBoundLabLessonOnZeroLabQuotaDay)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void h23_notPenalized_nonQuotaGroup() {
        // GROUP1 ("G03") has no DailyActivityQuota rows at all - unrestricted by L1-bis.
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        Lesson lesson = lesson(1L, FCLabCourse(1L, GROUP1), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::quotaBoundLabLessonOnZeroLabQuotaDay)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void h24_penalized_nonBoundSubjectOnZeroAulaQuotaDay() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 0, 5, 0)));
        Lesson lesson = lesson(1L, FCAulaCourse(1L, GROUP_FC), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::aulaLessonOnZeroAulaQuotaDay)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void h24_notPenalized_positiveAulaQuotaDay() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 4, 0, 0)));
        Lesson lesson = lesson(1L, FCAulaCourse(1L, GROUP_FC), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::aulaLessonOnZeroAulaQuotaDay)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void h24_notPenalized_boundLabSubjectIsH23sJobNotH24s() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 0, 5, 0)));
        Lesson lesson = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::aulaLessonOnZeroAulaQuotaDay)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void h24_notPenalized_pseudoTeacherExempt() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 0, 0, 5)));
        Teacher stageTeacher = teacher(99L, "Stage", true);
        Course stageCourse = course(1L, "Stage", stageTeacher, GROUP_FC);
        Lesson lesson = lesson(1L, stageCourse, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::aulaLessonOnZeroAulaQuotaDay)
                .given(lesson)
                .penalizesBy(0);
    }

    // ========================================================================
    // H25 Tests (TASK-384 — Stage Lesson Outside Stage Days Safety Net)
    // ========================================================================

    @Test
    void h25_penalized_unpinnedStageLessonOnNonStageDay() {
        // GROUP2 ("G07") is stage-eligible; MON has no stage-day fact (stageHours=0),
        // TUE is the group's real stage day.
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP2, TUE, 0, 0, 4, false)));
        Teacher stageTeacher = teacher(20L, "Stage", true);
        Course stageCourse = course(1L, "Stage", stageTeacher, GROUP2);
        Lesson lesson = lesson(1L, stageCourse, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::stageLessonOutsideStageDays)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void h25_notPenalized_unpinnedStageLessonOnItsOwnStageDay() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP2, MON, 0, 0, 4, false)));
        Teacher stageTeacher = teacher(20L, "Stage", true);
        Course stageCourse = course(1L, "Stage", stageTeacher, GROUP2);
        Lesson lesson = lesson(1L, stageCourse, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::stageLessonOutsideStageDays)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void h25_notPenalized_pinned() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP2, TUE, 0, 0, 4, false)));
        Teacher stageTeacher = teacher(20L, "Stage", true);
        Course stageCourse = course(1L, "Stage", stageTeacher, GROUP2);
        Lesson lesson = lesson(1L, stageCourse, TS_MON_08, ROOM_A, true);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::stageLessonOutsideStageDays)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void h25_notPenalized_nonStagePseudoLesson() {
        // "Esame" - a different pseudo-teacher accounting course, not confined.
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP2, TUE, 0, 0, 4, false)));
        Teacher esameTeacher = teacher(21L, "Esame", true);
        Course esameCourse = course(1L, "Esame", esameTeacher, GROUP2);
        Lesson lesson = lesson(1L, esameCourse, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::stageLessonOutsideStageDays)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void h25_notPenalized_groupWithNoStageFacts() {
        // GROUP1 ("G03") has no DailyActivityQuota rows at all - unconfined.
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        Teacher stageTeacher = teacher(20L, "Stage", true);
        Course stageCourse = course(1L, "Stage", stageTeacher, GROUP1);
        Lesson lesson = lesson(1L, stageCourse, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::stageLessonOutsideStageDays)
                .given(lesson)
                .penalizesBy(0);
    }

    // ========================================================================
    // H26 Tests (TASK-408 — Lesson On No-Session Day Safety Net)
    // ========================================================================

    @Test
    void h26_penalized_realLessonOnNoSessionDay() {
        // GROUP2 is sheet-covered (has a row on TUE); MON has no row at all, so
        // for a covered group MON is a no-session (closed) day.
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP2, TUE, 5, 0, 0, false)));
        Lesson lesson = lesson(1L, course(1L, "Matematica", TEACHER1, GROUP2), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::lessonOnNoSessionDay)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void h26_notPenalized_inSessionDay() {
        // MON has a row - the group IS in session that day.
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP2, MON, 5, 0, 0, false)));
        Lesson lesson = lesson(1L, course(1L, "Matematica", TEACHER1, GROUP2), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::lessonOnNoSessionDay)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void h26_notPenalized_pinned() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP2, TUE, 5, 0, 0, false)));
        Lesson lesson = lesson(1L, course(1L, "Matematica", TEACHER1, GROUP2), TS_MON_08, ROOM_A, true);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::lessonOnNoSessionDay)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void h26_notPenalized_pseudoTeacherExempt() {
        // A pseudo Stage lesson on a no-session date is confined by H25, not H26
        // (H26 mirrors the VRP's own pseudo exemption via realTeacherTimeslottedLessons).
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP2, TUE, 0, 0, 4, false)));
        Teacher stageTeacher = teacher(20L, "Stage", true);
        Lesson lesson = lesson(1L, course(1L, "Stage", stageTeacher, GROUP2), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::lessonOnNoSessionDay)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void h26_notPenalized_groupWithNoSheetCoverage() {
        // GROUP1 has no DailyActivityQuota rows at all - not sheet-covered, so
        // isNoSessionDate is false (fail-open) and every day is unrestricted.
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        Lesson lesson = lesson(1L, course(1L, "Matematica", TEACHER1, GROUP1), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::lessonOnNoSessionDay)
                .given(lesson)
                .penalizesBy(0);
    }

    @Test
    void m30_present_penalizesShortfall() {
        DailyActivityQuota quota = dailyActivityQuota(GROUP_FC, MON, 0, 3, 0);
        Lesson lesson = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labDayQuotaFillPresent)
                .given(quota, lesson)
                .penalizesBy(2); // |1 placed - 3 quota|
    }

    @Test
    void m30_present_notPenalized_exactMatch() {
        DailyActivityQuota quota = dailyActivityQuota(GROUP_FC, MON, 0, 1, 0);
        Lesson lesson = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labDayQuotaFillPresent)
                .given(quota, lesson)
                .penalizesBy(0);
    }

    @Test
    void m30_present_countsPinnedLessonsToo() {
        DailyActivityQuota quota = dailyActivityQuota(GROUP_FC, MON, 0, 1, 0);
        Lesson l1 = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, FCLabCourse(2L, GROUP_FC), TS_MON_09, ROOM_A, true);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labDayQuotaFillPresent)
                .given(quota, l1, l2)
                .penalizesBy(1); // |2 placed - 1 quota|
    }

    @Test
    void m30_absent_penalizesFullQuotaWhenNoMatchingLessonExists() {
        // The empty-day case: a quota fact with labHours > 0 but zero placed
        // bound-lab lessons - a plain groupBy over Lesson alone could never
        // surface this (no Lesson row to group by), which is exactly why
        // M30.2 drives its stream from the DailyActivityQuota fact instead.
        DailyActivityQuota quota = dailyActivityQuota(GROUP_FC, MON, 0, 4, 0);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labDayQuotaFillAbsent)
                .given(quota)
                .penalizesBy(4);
    }

    @Test
    void m30_absent_notPenalized_whenAMatchingLessonExists() {
        DailyActivityQuota quota = dailyActivityQuota(GROUP_FC, MON, 0, 4, 0);
        Lesson lesson = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labDayQuotaFillAbsent)
                .given(quota, lesson)
                .penalizesBy(0);
    }

    // ── M20.2 - Empty expected day (TASK-409) ──────────────────────────────

    @Test
    void m20_2_penalized_emptyExpectedDay() {
        // A day the school expects 5 aula hours on, with NO lessons at all:
        // M20 (Lesson-sourced, totalHours > 0) can never see it - M20.2 drives
        // from the fact and charges the deficit at FULL depth on M20's own
        // progressive curve, the exact limit of M20's partial-day gradient at
        // zero placed hours (no evacuation bonus at any quota).
        DailyActivityQuota quota = dailyActivityQuota(GROUP_FC, MON, 5, 0, 0);
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(quota));

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompactEmptyDay)
                .given(quota)
                .penalizesBy(10_000); // m20DeficitCost(5), was the linear 5 x 1000
    }

    @Test
    void m20_2_notPenalized_anyLessonCountsIncludingPinned() {
        // Unlike M20 (pinned-exempt count), for "is the day empty" ANY lesson
        // counts: a pinned lesson on the day means it is genuinely not empty.
        DailyActivityQuota quota = dailyActivityQuota(GROUP_FC, MON, 5, 0, 0);
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(quota));
        Lesson pinned = lesson(1L, FCAulaCourse(1L, GROUP_FC), TS_MON_08, ROOM_A);
        pinned.pinned = true;

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompactEmptyDay)
                .given(quota, pinned)
                .penalizesBy(0);
    }


    @Test
    void m20_2_notPenalized_stageOnlyDay() {
        // aula+lab = 0 (pure stage day): the group is on stage, no grid lessons
        // expected - same stage-exclusive basis dailyQuota itself uses (TASK-385).
        DailyActivityQuota quota = dailyActivityQuota(GROUP_FC, MON, 0, 0, 8);
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(quota));

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompactEmptyDay)
                .given(quota)
                .penalizesBy(0);
    }

    @Test
    void m30_absent_notPenalized_zeroLabQuotaDay() {
        DailyActivityQuota quota = dailyActivityQuota(GROUP_FC, MON, 5, 0, 0); // labHours == 0

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labDayQuotaFillAbsent)
                .given(quota)
                .penalizesBy(0);
    }

    @Test
    void m31_notPenalized_singleContiguousRun() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 0, 3, 0)));
        Lesson l1 = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A); // slot 0
        Lesson l2 = lesson(2L, FCLabCourse(2L, GROUP_FC), TS_MON_09, ROOM_A); // slot 1
        Lesson l3 = lesson(3L, FCLabCourse(3L, GROUP_FC), TS_MON_10, ROOM_A); // slot 2

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labDaySingleContiguousRun)
                .given(l1, l2, l3)
                .penalizesBy(0);
    }

    @Test
    void m31_penalized_splitRun() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 0, 2, 0)));
        Lesson l1 = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A); // slot 0
        Lesson l2 = lesson(2L, FCLabCourse(2L, GROUP_FC), TS_MON_11, ROOM_A); // slot 3 - gap at slots 1-2

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labDaySingleContiguousRun)
                .given(l1, l2)
                .penalizesBy(1); // 2 runs - 1
    }

    @Test
    void m31_notPenalized_allPinnedDayLevelExemption() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 0, 2, 0)));
        Lesson l1 = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, FCLabCourse(2L, GROUP_FC), TS_MON_11, ROOM_A, true); // split, but fully pinned

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labDaySingleContiguousRun)
                .given(l1, l2)
                .penalizesBy(0);
    }

    @Test
    void m31_penalized_mixedPinnedAndUnpinnedStillEvaluated() {
        // Only ONE lesson in the day's set is unpinned - the whole day is
        // still evaluated in full, same day-level exemption shape as M29.
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 0, 2, 0)));
        Lesson l1 = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A, true);  // pinned
        Lesson l2 = lesson(2L, FCLabCourse(2L, GROUP_FC), TS_MON_11, ROOM_A, false); // unpinned, split

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labDaySingleContiguousRun)
                .given(l1, l2)
                .penalizesBy(1);
    }

    @Test
    void m31_notPenalized_nonQuotaGroup() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of()); // GROUP1 not a quota group
        Lesson l1 = lesson(1L, FCLabCourse(1L, GROUP1), TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, FCLabCourse(2L, GROUP1), TS_MON_11, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labDaySingleContiguousRun)
                .given(l1, l2)
                .penalizesBy(0);
    }

    // ─── M32: Template deviation (TASK-413) ───────────────────────────────
    //
    // The template map is a global static; each test sets it manually and the
    // clearStaticState() @AfterEach resets it. M32_WEIGHT is the arbitrary
    // per-course weight these fixtures publish (the real weight is scarcity-
    // graduated - covered separately by templateDeviationWeight_scarcityGraduated).

    private static final int M32_WEIGHT = 300;

    /** Publishes a template with {@code courseId} anchored on Monday slot 0, weight {@link #M32_WEIGHT}. */
    private void setMonSlot0Template(long courseId) {
        SchoolSchedule.setWeeklyTemplateStatic(
                Map.of(courseId, Set.of(SchoolSchedule.encodeTemplateCell(DayOfWeek.MONDAY, 0))),
                Map.of(courseId, M32_WEIGHT));
    }

    @Test
    void templateDeviation_penalized_offCell() {
        setMonSlot0Template(1L);
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l = lesson(1L, course, TS_MON_09, ROOM_A); // Monday slot 1, not the template cell

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::templateDeviation)
                .given(l)
                .penalizesBy(M32_WEIGHT); // exactly the course's deviation weight
    }

    @Test
    void templateDeviation_notPenalized_onCell() {
        setMonSlot0Template(1L);
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l = lesson(1L, course, TS_MON_08, ROOM_A); // Monday slot 0 = the template cell

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::templateDeviation)
                .given(l)
                .penalizesBy(0);
    }

    @Test
    void templateDeviation_notPenalized_sameCellDifferentWeek() {
        // Weekly semantics: any DATE at the same (weekday, slot) is the template
        // cell, so moving to another Monday at slot 0 is NOT a deviation.
        setMonSlot0Template(1L);
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Timeslot nextMondaySlot0 = timeslot(100L, LocalDate.of(2025, 10, 13), 8, 0, 9, 0);
        Lesson l = lesson(1L, course, nextMondaySlot0, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::templateDeviation)
                .given(l)
                .penalizesBy(0);
    }

    @Test
    void templateDeviation_notPenalized_pinned() {
        setMonSlot0Template(1L);
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l = lesson(1L, course, TS_MON_09, ROOM_A, true); // off-cell but pinned

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::templateDeviation)
                .given(l)
                .penalizesBy(0);
    }

    @Test
    void templateDeviation_notPenalized_pseudoTeacher() {
        Teacher pseudoStage = teacher(3L, "Stage", true);
        setMonSlot0Template(1L);
        Course course = course(1L, "Stage", pseudoStage, GROUP1);
        Lesson l = lesson(1L, course, TS_MON_09, null); // off-cell but pseudo teacher

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::templateDeviation)
                .given(l)
                .penalizesBy(0);
    }

    @Test
    void templateDeviation_notPenalized_courseNotInTemplate() {
        setMonSlot0Template(1L); // only course 1 is templated
        Course course = course(2L, "Science", TEACHER1, GROUP1);
        Lesson l = lesson(1L, course, TS_MON_09, ROOM_A); // course 2 has no template cells

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::templateDeviation)
                .given(l)
                .penalizesBy(0);
    }

    @Test
    void templateDeviationWeight_scarcityGraduated() {
        // clamp(100_000 / vrpCells, 20, 500), integer division - the actual
        // formula (TASK-413 requirement 5c "verifica la formula intera effettiva").
        org.junit.jupiter.api.Assertions.assertEquals(462, SchoolSchedule.templateDeviationWeight(216));
        org.junit.jupiter.api.Assertions.assertEquals(76, SchoolSchedule.templateDeviationWeight(1300));
        org.junit.jupiter.api.Assertions.assertEquals(20, SchoolSchedule.templateDeviationWeight(5000));
        org.junit.jupiter.api.Assertions.assertEquals(20, SchoolSchedule.templateDeviationWeight(10000)); // 10 -> MIN 20
        org.junit.jupiter.api.Assertions.assertEquals(500, SchoolSchedule.templateDeviationWeight(100));   // 1000 -> MAX 500
        org.junit.jupiter.api.Assertions.assertEquals(500, SchoolSchedule.templateDeviationWeight(0));     // no cells -> MAX 500
    }

    /**
     * M32 knob defaults (2026-07-25, benchmark arm "W"): {@code TEMPLATE_DEVIATION_WEIGHT_
     * NUMERATOR/MIN/MAX} are now readable from system properties ({@code planner.m32.numerator}
     * / {@code planner.m32.min} / {@code planner.m32.max}), falling back to these exact values
     * when unset. This test can ONLY verify the no-property (default) path in-suite: the fields
     * are {@code public static final}, resolved once when {@link SchoolSchedule} first loads
     * (JVM class-init), so a property must be supplied BEFORE that moment - via {@code -D} on
     * the JVM command line (MAVEN_OPTS / surefire {@code argLine}), not via {@code
     * System.setProperty} called from a test method after the class has already loaded (which
     * has no effect on an already-initialized {@code static final int}). Exercising the
     * property-set path therefore requires an out-of-process run with the {@code -D} flags set
     * (e.g. a dedicated laptop benchmark arm), not an in-suite unit test; that constraint is
     * documented here and on the constants themselves, not worked around.
     */
    @Test
    void m32KnobDefaults_unchangedWhenNoPropertySet() {
        org.junit.jupiter.api.Assertions.assertEquals(100_000, SchoolSchedule.TEMPLATE_DEVIATION_WEIGHT_NUMERATOR);
        org.junit.jupiter.api.Assertions.assertEquals(20, SchoolSchedule.TEMPLATE_DEVIATION_WEIGHT_MIN);
        org.junit.jupiter.api.Assertions.assertEquals(500, SchoolSchedule.TEMPLATE_DEVIATION_WEIGHT_MAX);
    }

    // ─── M34: Weekly subject burst cap (TASK-425) ──────────────────────────
    //
    // The cadence reference is a global static; each test publishes a small
    // reference map via setCadenceReferenceStatic and the @AfterEach
    // clearStaticState resets it. M34_WEIGHT is the package-private knob default
    // (100, read once at class-init from planner.m34.weight), NOT pinned-exempt
    // (measures ALL real-teacher hours like S12, for golden-practice verification).

    /** Publishes a burst cap of {@code cap} hours for (GROUP1, "Math"). */
    private void setBurstCap(int cap) {
        SchoolSchedule.setCadenceReferenceStatic(
                Map.of(SchoolSchedule.cadenceKey(GROUP1.name, "Math"), cap),
                Map.of());
    }

    @Test
    void m34WeeklySubjectBurstCap_notPenalized_atCap() {
        setBurstCap(2);
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_TUE_08, ROOM_B); // same ISO week as MON, 2h total

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklySubjectBurstCap)
                .given(l1, l2)
                .penalizesBy(0); // 2h == cap, no excess
    }

    @Test
    void m34WeeklySubjectBurstCap_penalized_aboveCap() {
        setBurstCap(2);
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_TUE_08, ROOM_C); // same ISO week, 3h total

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklySubjectBurstCap)
                .given(l1, l2, l3)
                .penalizesBy(SchoolScheduleConstraintProvider.M34_WEIGHT); // (3 - 2) * M34_WEIGHT
    }

    @Test
    void m34WeeklySubjectBurstCap_notPenalized_pairAbsent() {
        // No reference published for this pair -> getCadenceBurstCap returns MAX_VALUE ->
        // hours > MAX_VALUE is always false -> constraint inert (self-gating).
        SchoolSchedule.setCadenceReferenceStatic(Map.of(), Map.of());
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B);
        Lesson l3 = lesson(3L, course, TS_MON_10, ROOM_C);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklySubjectBurstCap)
                .given(l1, l2, l3)
                .penalizesBy(0); // pair not in reference -> inert
    }

    @Test
    void m34WeeklySubjectBurstCap_notPenalized_pseudoTeacher() {
        setBurstCap(1); // tight cap so a real-teacher hour WOULD burst it
        Teacher pseudoStage = teacher(3L, "Stage", true);
        Course course = course(1L, "Stage", pseudoStage, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, null);
        Lesson l2 = lesson(2L, course, TS_MON_09, null);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklySubjectBurstCap)
                .given(l1, l2)
                .penalizesBy(0); // pseudo-teacher excluded (S12/M21 precedent)
    }

    // ─── M35: Weekly subject spread deficit (TASK-425) ──────────────────────
    //
    // Same reference-map setup shape as M34 above; activeWeeks target published
    // via the second arg to setCadenceReferenceStatic. M35_WEIGHT is the
    // package-private knob default (50, read from planner.m35.weight).

    /** Publishes an active-week target of {@code weeks} for (GROUP1, "Math"). */
    private void setActiveWeeksTarget(int weeks) {
        SchoolSchedule.setCadenceReferenceStatic(
                Map.of(),
                Map.of(SchoolSchedule.cadenceKey(GROUP1.name, "Math"), weeks));
    }

    @Test
    void m35WeeklySubjectSpreadDeficit_notPenalized_atTarget() {
        setActiveWeeksTarget(2);
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Timeslot nextWeekMon = timeslot(710L, MON.plusDays(7), 8, 0, 9, 0);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);   // week 1
        Lesson l2 = lesson(2L, course, nextWeekMon, ROOM_B); // week 2

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklySubjectSpreadDeficit)
                .given(l1, l2)
                .penalizesBy(0); // 2 active weeks == target
    }

    @Test
    void m35WeeklySubjectSpreadDeficit_penalized_belowTarget() {
        setActiveWeeksTarget(2);
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson l2 = lesson(2L, course, TS_TUE_08, ROOM_B); // same ISO week as MON, 1 active week

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklySubjectSpreadDeficit)
                .given(l1, l2)
                .penalizesBy(SchoolScheduleConstraintProvider.M35_WEIGHT); // (2 - 1) * M35_WEIGHT
    }

    @Test
    void m35WeeklySubjectSpreadDeficit_notPenalized_pairAbsent() {
        // No reference published -> getCadenceActiveWeeks returns 0 -> target > 0
        // gate short-circuits to false -> constraint inert (self-gating).
        SchoolSchedule.setCadenceReferenceStatic(Map.of(), Map.of());
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklySubjectSpreadDeficit)
                .given(l1)
                .penalizesBy(0); // pair not in reference -> inert
    }

    // ========================================================================
    // TASK-419: PROMOTED pins vs AUTHORITATIVE pins (staged solve, stage 1)
    // ========================================================================
    //
    // Stage 1 of the staged solve freezes every unpinned lesson already sitting on
    // its own M32 template cell (pinned=true) and registers its id in
    // SchoolSchedule.promotedLessonIdsStatic. Every pinned-exempt constraint must
    // then keep scoring that lesson exactly as it scored it while unpinned:
    // BaseConstraintProvider.isAuthoritativePin is the single gate, and each test
    // below is the same fixture in three states, unpinned (baseline), pinned+
    // PROMOTED (must match the baseline), pinned AUTHORITATIVE (exemption intact).
    //
    // Fixtures register ids explicitly; the @AfterEach clearStaticState() empties
    // the registry again, so no promotion ever leaks into another test.

    /** Register {@code ids} as TASK-419 promoted pins for the current test. */
    private static void promote(Long... ids) {
        SchoolSchedule.setPromotedLessonIdsStatic(List.of(ids));
    }

    @Test
    void promotedRegistry_emptyByDefault_everyPinIsAuthoritative() {
        org.junit.jupiter.api.Assertions.assertTrue(SchoolSchedule.getPromotedLessonIdsStatic().isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(SchoolSchedule.isPromoted(1L));
        org.junit.jupiter.api.Assertions.assertFalse(SchoolSchedule.isPromoted(null)); // null id (unpersisted fixture) never promoted
    }

    @Test
    void promotedRegistry_nullOrEmptyPublishRestoresDefault() {
        promote(1L, 2L);
        org.junit.jupiter.api.Assertions.assertTrue(SchoolSchedule.isPromoted(2L));
        SchoolSchedule.setPromotedLessonIdsStatic(null);
        org.junit.jupiter.api.Assertions.assertTrue(SchoolSchedule.getPromotedLessonIdsStatic().isEmpty());
        promote(1L);
        SchoolSchedule.setPromotedLessonIdsStatic(List.of());
        org.junit.jupiter.api.Assertions.assertTrue(SchoolSchedule.getPromotedLessonIdsStatic().isEmpty());
    }

    /**
     * clearStaticState() is the reset every harness/profile switch goes through
     * (SnapshotFixture.resetStatics, LessonStage*VrpTest): a promotion left behind
     * would silently re-classify the NEXT solve's authoritative pins.
     */
    @Test
    void promotedRegistry_clearedByClearStaticState() {
        promote(42L);
        org.junit.jupiter.api.Assertions.assertTrue(SchoolSchedule.isPromoted(42L));
        try {
            SchoolSchedule.clearStaticState();
            org.junit.jupiter.api.Assertions.assertTrue(SchoolSchedule.getPromotedLessonIdsStatic().isEmpty());
            org.junit.jupiter.api.Assertions.assertFalse(SchoolSchedule.isPromoted(42L));
        } finally {
            // clearStaticState() nulls every other static too; restore the empty
            // baselines the rest of this class's fixtures assume.
            clearStaticState();
        }
    }

    // ─── M20 (occupancy count): THE bug the spike found ───────────────────

    @Test
    void m20_promotedLessonsStillCountAsOccupiedHours() {
        // 6 hours on Monday, 5 of them frozen by stage 1: the day is FULL, so M20
        // must be silent. Before TASK-419 the 5 promoted hours vanished from
        // totalHours and M20 billed the day as a 1-hour day (penalty 5), the
        // measured -3.23M vs -1.07M start that pushed the LS to re-fill full days.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson[] day = fullMondayFor(course, true);
        promote(1L, 2L, 3L, 4L, 5L); // l6 stays unpinned (the free tail)

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given((Object[]) day)
                .penalizesBy(0);
    }

    @Test
    void m20_authoritativePinsStillExemptFromOccupancyCount() {
        // Same fixture, registry empty: the 5 pins are the school's own, so they stay
        // out of the occupancy count exactly as they did before TASK-419 - and since
        // TASK-424 they come out of the TARGET too (quota 6 - 5 = 1), so this day of
        // 5 school hours + 1 solver hour reads as what it is: exactly on plan.
        // Before that netting it was billed m20DeficitCost(5) = 10,000 of deficit that
        // does not exist, and paid 2,500 of marginal gain for a 7th hour.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson[] day = fullMondayFor(course, true);
        publishAuthoritativePins(day);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given((Object[]) day)
                .penalizesBy(0);
        // The exemption from the COUNT still carries its own weight: were the 5 pins
        // counted as occupancy while also netted from the target, the same day would
        // read 6 hours against an effective quota of 1 and M33 would charge 500.
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::surplusBeyondExpectedHours)
                .given((Object[]) day)
                .penalizesBy(0);
    }

    @Test
    void m20_allUnpinnedBaselineMatchesThePromotedCase() {
        // The baseline the promoted case must reproduce: same grid, nothing pinned.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson[] day = fullMondayFor(course, false);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::horizontallyCompact)
                .given((Object[]) day)
                .penalizesBy(0);
    }

    /** A full 6-hour Monday; when {@code freezeFirstFive}, lessons 1-5 are pinned and 6 is free. */
    private static Lesson[] fullMondayFor(Course course, boolean freezeFirstFive) {
        Timeslot[] slots = {TS_MON_08, TS_MON_09, TS_MON_10, TS_MON_11, TS_MON_12, TS_MON_13};
        Lesson[] day = new Lesson[slots.length];
        for (int i = 0; i < slots.length; i++) {
            day[i] = lesson(i + 1L, course, slots[i], ROOM_A, freezeFirstFive && i < 5);
        }
        return day;
    }

    // ─── M22 (occupancy count: days used + hours placed) ──────────────────

    @Test
    void m22_promotedLessonsStillCountAsAUsedDay() {
        // 1h Monday (frozen) + 1h Tuesday (free) = 2 hours over 2 days, minDays 1.
        // The frozen Monday is a genuinely occupied day, so spreading onto Tuesday
        // must still cost, otherwise the free tail opens new days for free.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson frozen = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson free = lesson(2L, course, TS_TUE_08, ROOM_B, false);
        promote(1L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::calendarCompact)
                .given(frozen, free)
                .penalizesBy(1); // 2 days used - ceil(2h/6) = 1 excess day
    }

    @Test
    void m22_authoritativePinStillExemptFromTheDayCount() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson pinned = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson free = lesson(2L, course, TS_TUE_08, ROOM_B, false);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::calendarCompact)
                .given(pinned, free)
                .penalizesBy(0); // only Tuesday counted: 1 day, ceil(1h/6) = 1
    }

    @Test
    void m22_allUnpinnedBaselineMatchesThePromotedCase() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson a = lesson(1L, course, TS_MON_08, ROOM_A, false);
        Lesson b = lesson(2L, course, TS_TUE_08, ROOM_B, false);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::calendarCompact)
                .given(a, b)
                .penalizesBy(1);
    }

    // ─── M22 quota-aware min-days amendment (TASK-558, RATIFIED DEFAULT ON 27/08) ──

    private static final String M22_QUOTA_AWARE_FLAG = "planner.constraints.m22.quota-aware.enabled";

    /** The TASK-558 flag is opt-in: run {@code body} with it armed, restore after. */
    private static void withM22QuotaAware(Runnable body) {
        String prev = System.getProperty(M22_QUOTA_AWARE_FLAG);
        System.setProperty(M22_QUOTA_AWARE_FLAG, "true");
        try {
            body.run();
        } finally {
            if (prev == null) System.clearProperty(M22_QUOTA_AWARE_FLAG);
            else System.setProperty(M22_QUOTA_AWARE_FLAG, prev);
        }
    }

    /** The sheet used by the amendment tests: GROUP1 declares 3h/day on MON/TUE/WED. */
    private static void setSheetThreeHourDays() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP1, MON, 3, 0, 0),
                dailyActivityQuota(GROUP1, TUE, 3, 0, 0),
                dailyActivityQuota(GROUP1, WED, 3, 0, 0)));
    }

    /** 9 free hours over 3 distinct days, GROUP1 (no maxHoursPerDay: default 6). */
    private static Lesson[] nineHoursOverThreeDays(Course course) {
        return new Lesson[] {
                lesson(1L, course, TS_MON_08, ROOM_A, false),
                lesson(2L, course, TS_MON_09, ROOM_B, false),
                lesson(3L, course, TS_MON_10, ROOM_C, false),
                lesson(4L, course, TS_TUE_08, ROOM_A, false),
                lesson(5L, course, TS_TUE_09, ROOM_B, false),
                lesson(6L, course, TS_TUE_10, ROOM_C, false),
                lesson(7L, course, TS_WED_08, ROOM_A, false),
                lesson(8L, course, TS_WED_09, ROOM_B, false),
                lesson(9L, course, TS_WED_10, ROOM_C, false)
        };
    }

    @Test
    void m22_quotaAwareAmendmentRecognizesTheSheetsOwnDayCount() {
        // The sheet declares 3h/day for GROUP1 (three ordinary rows): the amended
        // min-days for 9 hours is ceil(9/3) = 3 = the days actually used, so the
        // legacy thin-packing match (ceil(9/6) = 2 < 3 days) disappears. This is
        // the amendment's whole point: a solution faithful to the sheet's own
        // daily plan must not read as "spread too thin".
        setSheetThreeHourDays();
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        withM22QuotaAware(() -> constraintVerifier
                .verifyThat(SchoolScheduleConstraintProvider::calendarCompact)
                .given(nineHoursOverThreeDays(course))
                .penalizesBy(0));
    }

    @Test
    void m22_legacyThinPackingMatchSurvivesWhenTheFlagIsOff() {
        // Same fixture, flag OFF via the opt-OUT literal "false" (RATIFIED default ON
        // 27/08, absent -> amended metric): legacy min-days = ceil(9/6) = 2, so 3
        // used days still read as 1 excess day - the legacy metric survives only
        // through the explicit opt-out.
        setSheetThreeHourDays();
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        String prev = System.getProperty(M22_QUOTA_AWARE_FLAG);
        System.setProperty(M22_QUOTA_AWARE_FLAG, "false");
        try {
            constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::calendarCompact)
                    .given(nineHoursOverThreeDays(course))
                    .penalizesBy(1);
        } finally {
            if (prev == null) System.clearProperty(M22_QUOTA_AWARE_FLAG);
            else System.setProperty(M22_QUOTA_AWARE_FLAG, prev);
        }
    }

    @Test
    void m22_quotaAwareFallsBackToLegacyWithoutDeclaredRows() {
        // Flag ON but the group has no quota rows at all (synthetic fixtures,
        // non-sheet datasets): the legacy maxHoursPerDay packing still decides.
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        withM22QuotaAware(() -> constraintVerifier
                .verifyThat(SchoolScheduleConstraintProvider::calendarCompact)
                .given(nineHoursOverThreeDays(course))
                .penalizesBy(1)); // ceil(9/6) = 2 < 3 days
    }

    // ─── H34 - Empty declared day (TASK-557, companion hard of H32, flag DEFAULT OFF) ──

    private static final String EMPTY_DAY_FLAG = "planner.constraints.empty-day.enabled";

    /** The TASK-557 flag is opt-in in development: run {@code body} armed, restore after. */
    private static void withEmptyDayHard(Runnable body) {
        String prev = System.getProperty(EMPTY_DAY_FLAG);
        System.setProperty(EMPTY_DAY_FLAG, "true");
        try {
            body.run();
        } finally {
            if (prev == null) System.clearProperty(EMPTY_DAY_FLAG);
            else System.setProperty(EMPTY_DAY_FLAG, prev);
        }
    }

    @Test
    void h34_aFullEightSlotDayChargesExactlyOneFlatCell() {
        // The live-cycle alarm: construction under H34 opened ~6.2k hard, an ~8x
        // multiple of the plausible empty-cell count. The dedup under test: the
        // real grid has 8 timeslots per date, so ONE empty declared day must be
        // ONE match, not 8.
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP1, MON, 5, 0, 0)));
        List<Timeslot> fullMonday = new java.util.ArrayList<>();
        for (java.time.LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
            Timeslot ts = new Timeslot();
            ts.id = (long) (fullMonday.size() + 1);
            ts.startDateTime = java.time.LocalDateTime.of(MON, start);
            fullMonday.add(ts);
        }
        SchoolSchedule.setAllTimeslotsStatic(fullMonday);
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson elsewhere = lesson(1L, course, TS_TUE_08, ROOM_A, false);
        Object[] facts = new Object[2 + fullMonday.size()];
        facts[0] = elsewhere;
        facts[1] = GROUP1;
        for (int i = 0; i < fullMonday.size(); i++) {
            facts[2 + i] = fullMonday.get(i);
        }
        withEmptyDayHard(() -> constraintVerifier
                .verifyThat(SchoolScheduleConstraintProvider::emptyDeclaredDay)
                .given(facts)
                .penalizesBy(1)); // ONE cell for the whole empty 8-slot Monday
    }

    /** GROUP1 declares 2h on MON and TUE; only TUE is filled by the given lessons. */
    private static void setSheetTwoHourDays() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP1, MON, 2, 0, 0),
                dailyActivityQuota(GROUP1, TUE, 2, 0, 0)));
        SchoolSchedule.setAllTimeslotsStatic(List.of(TS_MON_08, TS_MON_09, TS_TUE_08, TS_TUE_09));
    }

    @Test
    void h34_emptyDeclaredDayPaysFlatHardWhenArmed() {
        // Monday is an expected day with ZERO non-Stage lessons: invisible to
        // H32's groupBy (which only sees days WITH lessons), M20.2's medium twin
        // until now. FLAT 1: presence is the defect, depth stays H32's business.
        // The given() carries the FACTS too (timeslots + groups): H34 anchors on
        // the Timeslot x StudentGroup problem-fact join, not on lessons alone.
        setSheetTwoHourDays();
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson tue = lesson(1L, course, TS_TUE_08, ROOM_A, false);
        withEmptyDayHard(() -> constraintVerifier
                .verifyThat(SchoolScheduleConstraintProvider::emptyDeclaredDay)
                .given(tue, GROUP1, TS_MON_08, TS_MON_09, TS_TUE_08, TS_TUE_09)
                .penalizesBy(1)); // the empty MON (8 slots-per-date deduped to ONE cell)
    }

    @Test
    void h34_partiallyFilledDayStaysH32sBusiness() {
        // 1 of 2 declared hours on MON: the day is not empty, so H34 stays
        // silent even armed - exactly the split documented in H32's javadoc.
        setSheetTwoHourDays();
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson mon = lesson(1L, course, TS_MON_08, ROOM_A, false);
        Lesson tue = lesson(2L, course, TS_TUE_08, ROOM_B, false);
        withEmptyDayHard(() -> constraintVerifier
                .verifyThat(SchoolScheduleConstraintProvider::emptyDeclaredDay)
                .given(mon, tue, GROUP1, TS_MON_08, TS_MON_09, TS_TUE_08, TS_TUE_09)
                .penalizesBy(0));
    }

    @Test
    void h34_excludedGroupsStayOutOfScopeAndOptOutDisarms() {
        // Armed (the DEFAULT since the 26/08 ratification): the G14 declared
        // cell (an H32-scope exclusion like 1/G08) never fires - only GROUP1's
        // empty MON charges. The explicit "false" opts out (standard form).
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP1, MON, 2, 0, 0),
                dailyActivityQuota(GROUP_G14, WED, 2, 0, 0)));
        SchoolSchedule.setAllTimeslotsStatic(List.of(TS_MON_08, TS_MON_09, TS_WED_08));
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson otherGroupDay = lesson(1L, course, TS_WED_08, ROOM_A, false);
        assertTrue(SchoolScheduleConstraintProvider.emptyDayHardEnabled(),
                "H34 is DEFAULT ON since the ratification: the absent flag must arm it");
        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::emptyDeclaredDay)
                .given(otherGroupDay, GROUP1, GROUP_G14, TS_MON_08, TS_MON_09, TS_WED_08)
                .penalizesBy(1); // GROUP1's empty MON only; G14 out of scope
        String prev = System.getProperty(EMPTY_DAY_FLAG);
        System.setProperty(EMPTY_DAY_FLAG, "false");
        try {
            constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::emptyDeclaredDay)
                    .given(otherGroupDay, GROUP1, GROUP_G14, TS_MON_08, TS_MON_09, TS_WED_08)
                    .penalizesBy(0); // explicit opt-out disarms
        } finally {
            if (prev == null) System.clearProperty(EMPTY_DAY_FLAG);
            else System.setProperty(EMPTY_DAY_FLAG, prev);
        }
    }

    // ─── M15 / H17 (break-level exemption) ────────────────────────────────

    @Test
    void m15_breakBetweenTwoPromotedLessonsStillPenalized() {
        // The split exists in the grid and a free lesson dropped at 10:00 still
        // closes it: only the SCHOOL's own historical splits are excused.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_A, true);
        Lesson l3 = lesson(3L, course, TS_MON_11, ROOM_A, true); // gap at 10:00
        promote(1L, 2L, 3L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sameDaySameSubjectShouldBeConsecutive)
                .given(l1, l2, l3)
                .penalizesBy(60);
    }

    @Test
    void m15_breakWithOnePromotedAndOneAuthoritativeSideStillPenalized() {
        // The exemption needs BOTH sides authoritative; one promoted side is enough
        // to keep the break billed (mirrors the pre-existing oneSidePinned tests).
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_A, true);
        Lesson l3 = lesson(3L, course, TS_MON_11, ROOM_A, true);
        promote(2L); // only the previous-sequence end is promoted

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::sameDaySameSubjectShouldBeConsecutive)
                .given(l1, l2, l3)
                .penalizesBy(60);
    }

    @Test
    void h17_gapBetweenTwoPromotedLessonsStillPenalized() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, course, TS_MON_11, ROOM_B, true); // gap at 9:00, 10:00
        promote(1L, 2L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::verticallyCompact)
                .given(l1, l2)
                .penalizesBy(2);
    }

    @Test
    void h17_gapBetweenTwoAuthoritativePinsStillExempt() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, course, TS_MON_11, ROOM_B, true);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::verticallyCompact)
                .given(l1, l2)
                .penalizesBy(0);
    }

    // ─── M29 / M31 (DAY-LEVEL exemption) ──────────────────────────────────

    @Test
    void m29_dayCoveredOnlyByPromotedPinsIsStillEvaluated() {
        // Without this, a template-frozen day would be exempt until the tail drops
        // its first free lesson on it and then be billed for the WHOLE day's shape.
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(FDWedCalendarRule()));
        Course course = profCourse(1L, GROUP_FD);
        Lesson l1 = lesson(1L, course, TS_WED_10, ROOM_A, true); // slot 2 = illegal shape
        promote(1L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::professionalizzantiAtDayEdges)
                .given(l1)
                .penalizesBy(1);
    }

    @Test
    void m31_dayCoveredOnlyByPromotedPinsIsStillEvaluated() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 0, 2, 0)));
        Lesson l1 = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, FCLabCourse(2L, GROUP_FC), TS_MON_11, ROOM_A, true); // split run
        promote(1L, 2L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labDaySingleContiguousRun)
                .given(l1, l2)
                .penalizesBy(1); // 2 runs - 1
    }

    // ─── Per-entity exemptions (M28 / M12 / S11 / S13) ────────────────────

    @Test
    void m28_promotedAfternoonLessonStillPenalized() {
        // Keeps the score honest across the closing smarcatura: releasing the pin
        // must not suddenly reveal an afternoon cost that was invisible in stage 1.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_MON_1430, ROOM_A, true);
        promote(1L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::afternoonSlotsReservedForStage)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void m12_promotedLessonBeyondTargetStillPenalized() {
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson lesson = lesson(1L, course, TS_TUE_08, ROOM_A, true);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of(GROUP1.id, MON));
        promote(1L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::overflowBeyondTargetEndDate)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void s11_promotedLessonsStillCountTowardTheDistinctRoomCount() {
        Course course = course(1L, "Math", TEACHER1, GROUP1, false);
        Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A, true);
        Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B, true);
        promote(1L, 2L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::studentGroupRoomAffinity)
                .given(l1, l2)
                .penalizesBy(1); // 2 distinct rooms - 1
    }

    @Test
    void s13_promotedLessonStillEarnsTheWeeklyStabilityReward() {
        // S13 excludes pins so a pre-fixed solution can't inflate the reward; a
        // promoted lesson was earning it a moment earlier and must keep earning it,
        // or the soft score swings purely with the staging.
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson previousWeek = lesson(1L, course, TS_MON_08, ROOM_A);
        Lesson promotedCurrent = lesson(2L, course, NEXT_WEEK_MON, ROOM_A, true);
        promote(2L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::weeklyStability)
                .given(previousWeek, promotedCurrent)
                .rewardsWith(1);
    }

    // ─── Hard-tier safety nets (H23 / H25 / H26) ──────────────────────────
    //
    // These matter most: a hard violation hidden behind a promoted pin would let
    // stage 1 declare hard=0 and have it stop being true at the smarcatura (the
    // TASK-383 class of pin-accounting bug). H23 and H24 share the same
    // quotaGroupUnpinnedLessons prefix, so H23 covers both.

    @Test
    void h23_promotedLessonOnZeroLabQuotaDayStillPenalized() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(dailyActivityQuota(GROUP_FC, MON, 5, 0, 0)));
        Lesson lesson = lesson(1L, FCLabCourse(1L, GROUP_FC), TS_MON_08, ROOM_A, true);
        promote(1L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::quotaBoundLabLessonOnZeroLabQuotaDay)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void h25_promotedStageLessonOutsideStageDaysStillPenalized() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP1, TUE, 0, 0, 8, false))); // stage day is TUE, not MON
        Teacher stageTeacher = teacher(21L, "Stage", true);
        Course stageCourse = course(1L, "Stage", stageTeacher, GROUP1);
        Lesson lesson = lesson(1L, stageCourse, TS_MON_08, ROOM_A, true);
        promote(1L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::stageLessonOutsideStageDays)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void h26_promotedLessonOnNoSessionDayStillPenalized() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(GROUP2, TUE, 5, 0, 0, false))); // MON has no row = no session
        Lesson lesson = lesson(1L, course(1L, "Matematica", TEACHER1, GROUP2), TS_MON_08, ROOM_A, true);
        promote(1L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::lessonOnNoSessionDay)
                .given(lesson)
                .penalizesBy(1);
    }

    // ─── M32: deliberately NOT narrowed (declared no-op) ──────────────────

    @Test
    void m32_promotedLessonOnItsTemplateCellIsNeverPenalizedEitherWay() {
        // The promotion rule IS "sits on its own template cell", so a promoted
        // lesson can't deviate: M32 = 0 whether it is exempted or evaluated. This
        // is why templateDeviation keeps the plain `pinned` filter.
        setMonSlot0Template(1L);
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson onCell = lesson(1L, course, TS_MON_08, ROOM_A, true); // Monday slot 0 = the cell
        promote(1L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::templateDeviation)
                .given(onCell)
                .penalizesBy(0);
    }

    @Test
    void m32_exemptionStaysWholesalePinnedByDesign_offCellPromotedNotPenalized() {
        // Documents the ONE deliberate divergence from isAuthoritativePin: even an
        // off-cell pinned lesson registered as promoted stays exempt. Unreachable
        // under the real promotion rule (which only ever promotes on-cell lessons);
        // pinned here so a future widening of that rule is a conscious decision.
        setMonSlot0Template(1L);
        Course course = course(1L, "Math", TEACHER1, GROUP1);
        Lesson offCell = lesson(1L, course, TS_MON_09, ROOM_A, true); // Monday slot 1
        promote(1L);

        constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::templateDeviation)
                .given(offCell)
                .penalizesBy(0);
    }

    // ========================================================================
    // Justification Tests (TASK-244 Phase 3)
    // ========================================================================
    //
    // Custom constraint justifications provide structured data about violations.
    // The ConstraintVerifier validates that constraints penalize correctly.
    //
    // For full justification structure validation (including entity IDs, dates),
    // integration tests are used that fetch from the REST API and verify
    // the JSON serialization with @type discriminators.
    //
    // See JustificationTest.java for integration tests that verify:
    // - TeacherConflictJustification (teacherId, lesson1Id, lesson2Id)
    // - RoomConflictJustification (roomId, lesson1Id, lesson2Id)
    // - StudentGroupConflictJustification (groupId, lesson1Id, lesson2Id)
    // - SparseDayJustification (groupId, date, actualHours, requiredHours)
    //
    // Note: H7 (TeacherDailyHours) and H11 (StudentGroupDailyHours) use the
    // indictment pattern (indictWith) rather than justifyWith, so their
    // violations are added post-hoc in ScheduleService. These are verified
    // through full solver integration tests.
}
