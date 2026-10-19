package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Course;
import com.school.planner.domain.LabAssistant;
import com.school.planner.domain.LabRotation;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit/integration tests for {@link AvailabilityExpansionService} (TASK-345):
 * expanding {@link RecurringAvailabilityRule} templates into dated
 * {@link TeacherAvailability} rows.
 *
 * DB-backed ({@code @QuarkusTest} + H2), following the same full-reset
 * {@code @BeforeEach} pattern as {@code GreedyRotationAssignerTest} - the
 * service's {@code schoolDaysByWeekday()} reads the entire {@code Timeslot}
 * table, so leftover rows from other test classes sharing the H2 instance
 * would otherwise pollute the expansion counts.
 */
@QuarkusTest
class AvailabilityExpansionServiceTest {

    @Inject
    AvailabilityExpansionService service;

    // Full-reset order (matches GreedyRotationAssignerTest's proven-safe FK
    // ordering): schoolDaysByWeekday() reads the entire Timeslot table, so a
    // stray leftover Timeslot row from another test class sharing this H2
    // instance would silently inflate an expansion's expected row count.
    @BeforeEach
    @Transactional
    void cleanDatabase() {
        resetDatabase();
    }

    // Also run after each test (not just before): this class must never leave
    // RecurringAvailabilityRule/TeacherAvailability rows behind, or an
    // unrelated test class's own cleanup (e.g. GreedyRotationAssignerTest,
    // which deletes Teacher without knowing about this task's new tables)
    // FK-violates on a dangling teacher_id it never expected to exist.
    @AfterEach
    @Transactional
    void cleanDatabaseAfter() {
        resetDatabase();
    }

    private void resetDatabase() {
        Lesson.deleteAll();
        Course.deleteAll();
        LabRotation.deleteAll();
        LabAssistant.deleteAll();
        TeacherAvailability.deleteAll();
        RecurringAvailabilityRule.deleteAll();
        SchoolHoliday.deleteAll();
        Timeslot.deleteAll();
        Teacher.deleteAll();
    }

    private Teacher persistTeacher(String name) {
        Teacher t = new Teacher();
        t.name = name;
        t.persist();
        return t;
    }

    /** Persists one Timeslot per slot for a given date, mirroring the real 8-slot day. */
    private void persistSchoolDay(LocalDate date) {
        LocalTime[] starts = com.school.planner.solver.TimeslotSequence.SLOT_START_TIMES;
        for (LocalTime start : starts) {
            Timeslot ts = new Timeslot(LocalDateTime.of(date, start), LocalDateTime.of(date, start.plusHours(1)));
            ts.persist();
        }
    }

    // ─── Basic expansion ────────────────────────────────────────────────

    @Test
    @Transactional
    void expandForTeacher_oneRowPerMatchingSchoolDay() {
        Teacher teacher = persistTeacher("T04");
        LocalDate mon1 = LocalDate.of(2026, 9, 14);
        LocalDate mon2 = LocalDate.of(2026, 9, 21);
        LocalDate tue = LocalDate.of(2026, 9, 15);
        persistSchoolDay(mon1);
        persistSchoolDay(mon2);
        persistSchoolDay(tue);

        RecurringAvailabilityRule rule = new RecurringAvailabilityRule(
                teacher, DayOfWeek.MONDAY, 0, 4, AvailabilityType.VACATION);
        rule.persist();

        service.expandForTeacher(teacher);

        List<TeacherAvailability> expanded = TeacherAvailability.listAll();
        assertEquals(2, expanded.size(), "one row per Monday, none for Tuesday");
        for (TeacherAvailability ta : expanded) {
            assertEquals(rule.id, ta.sourceRuleId);
            assertEquals(LocalTime.of(8, 0), ta.startDateTime.toLocalTime());
            assertEquals(LocalTime.of(13, 0), ta.endDateTime.toLocalTime(), "maxSlot=4 (12:00-13:00) -> end 13:00");
        }
    }

    // ─── Lunch-spanning range ───────────────────────────────────────────

    @Test
    @Transactional
    void expandRule_lunchSpanningRange_producesOneRowNotTwo() {
        Teacher teacher = persistTeacher("T11");
        LocalDate mon = LocalDate.of(2026, 9, 14);
        persistSchoolDay(mon);

        // slots 5-7: 13:00-14:00, 14:30-15:30, 15:30-16:30 - straddles the
        // 14:00-14:30 lunch break.
        new RecurringAvailabilityRule(teacher, DayOfWeek.MONDAY, 5, 7, AvailabilityType.VACATION).persist();

        service.expandForTeacher(teacher);

        List<TeacherAvailability> expanded = TeacherAvailability.listAll();
        assertEquals(1, expanded.size(), "one merged row, not two separate slot-aligned rows");
        TeacherAvailability ta = expanded.get(0);
        assertEquals(LocalDateTime.of(mon, LocalTime.of(13, 0)), ta.startDateTime);
        assertEquals(LocalDateTime.of(mon, LocalTime.of(16, 30)), ta.endDateTime);
    }

    // ─── Closures skipped ───────────────────────────────────────────────

    @Test
    @Transactional
    void expandForTeacher_datesWithoutATimeslotAreSkipped() {
        Teacher teacher = persistTeacher("T05");
        LocalDate schoolMonday = LocalDate.of(2026, 9, 14);
        LocalDate closureMonday = LocalDate.of(2026, 9, 21); // no Timeslot persisted -> simulates a closure
        persistSchoolDay(schoolMonday);

        new RecurringAvailabilityRule(teacher, DayOfWeek.MONDAY, 6, 7, AvailabilityType.VACATION).persist();

        service.expandForTeacher(teacher);

        List<TeacherAvailability> expanded = TeacherAvailability.listAll();
        assertEquals(1, expanded.size(), "only the real school day gets a row, the closure date is skipped");
        assertEquals(schoolMonday, expanded.get(0).startDateTime.toLocalDate());
    }

    // ─── effectiveFrom/To window ────────────────────────────────────────

    @Test
    @Transactional
    void expandForTeacher_effectiveWindow_restrictsToDatedRange() {
        Teacher teacher = persistTeacher("T12");
        LocalDate week1 = LocalDate.of(2026, 9, 14);
        LocalDate week2 = LocalDate.of(2026, 9, 21);
        LocalDate week3 = LocalDate.of(2026, 9, 28);
        persistSchoolDay(week1);
        persistSchoolDay(week2);
        persistSchoolDay(week3);

        new RecurringAvailabilityRule(teacher, DayOfWeek.MONDAY, 0, 7, AvailabilityType.VACATION,
                week2, week2, "dated absence").persist();

        service.expandForTeacher(teacher);

        List<TeacherAvailability> expanded = TeacherAvailability.listAll();
        assertEquals(1, expanded.size(), "single-date rule only expands to its one effective date");
        assertEquals(week2, expanded.get(0).startDateTime.toLocalDate());
    }

    // ─── Idempotency / re-expansion ─────────────────────────────────────

    @Test
    @Transactional
    void expandForTeacher_reRunning_doesNotDuplicateRows() {
        Teacher teacher = persistTeacher("T24");
        LocalDate tue = LocalDate.of(2026, 9, 15);
        persistSchoolDay(tue);

        new RecurringAvailabilityRule(teacher, DayOfWeek.TUESDAY, 0, 5, AvailabilityType.PREFERENCE).persist();

        service.expandForTeacher(teacher);
        service.expandForTeacher(teacher);
        service.expandForTeacher(teacher);

        assertEquals(1, TeacherAvailability.listAll().size(), "re-expanding must not accumulate duplicate rows");
    }

    @Test
    @Transactional
    void expandForTeacher_editedRule_replacesStaleRows() {
        Teacher teacher = persistTeacher("T10");
        LocalDate thu = LocalDate.of(2026, 9, 17);
        persistSchoolDay(thu);

        RecurringAvailabilityRule rule = new RecurringAvailabilityRule(
                teacher, DayOfWeek.THURSDAY, 0, 4, AvailabilityType.VACATION);
        rule.persist();
        service.expandForTeacher(teacher);
        assertEquals(LocalTime.of(13, 0), TeacherAvailability.<TeacherAvailability>listAll().get(0).endDateTime.toLocalTime());

        rule.maxSlot = 7;
        rule.persist();
        service.expandForTeacher(teacher);

        List<TeacherAvailability> expanded = TeacherAvailability.listAll();
        assertEquals(1, expanded.size(), "the stale narrower row must be gone, not additive");
        assertEquals(LocalTime.of(16, 30), expanded.get(0).endDateTime.toLocalTime());
    }

    @Test
    @Transactional
    void expandForTeacher_handEnteredAvailability_isNeverTouched() {
        Teacher teacher = persistTeacher("T16");
        LocalDate mon = LocalDate.of(2026, 9, 14);
        persistSchoolDay(mon);

        TeacherAvailability manual = new TeacherAvailability(teacher,
                LocalDateTime.of(mon, LocalTime.of(8, 0)), LocalDateTime.of(mon, LocalTime.of(9, 0)),
                AvailabilityType.SICK);
        manual.persist(); // sourceRuleId stays null - hand-entered

        new RecurringAvailabilityRule(teacher, DayOfWeek.MONDAY, 5, 7, AvailabilityType.VACATION).persist();
        service.expandForTeacher(teacher);
        service.expandForTeacher(teacher);

        List<TeacherAvailability> all = TeacherAvailability.listAll();
        assertEquals(2, all.size(), "one hand-entered row + one expanded row");
        assertTrue(all.stream().anyMatch(ta -> ta.id.equals(manual.id) && ta.sourceRuleId == null),
                "hand-entered row must survive re-expansion untouched");
    }

    // ─── Zero-expansion WARN (TASK-345 code review R1) ───────────────────

    @Test
    @Transactional
    void expandRule_zeroExpansionRows_logsWarning() {
        Teacher teacher = persistTeacher("T05");
        // A school day exists, but only on a Monday; the rule's effective
        // window is a dated range that predates it, so the rule is coherent
        // (a ranged window, not a single-date rule - R1's dedicated
        // dayOfWeek-mismatch check doesn't apply) yet still expands to zero
        // rows. This is exactly the class of case the WARN catch-all exists
        // for: a legitimate rule shape that happens to produce nothing.
        persistSchoolDay(LocalDate.of(2026, 9, 14));
        new RecurringAvailabilityRule(teacher, DayOfWeek.MONDAY, 0, 7, AvailabilityType.VACATION,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2), "predates every school day").persist();

        List<String> warnings = new ArrayList<>();
        Logger julLogger = Logger.getLogger(AvailabilityExpansionService.class.getName());
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record.getMessage());
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        julLogger.addHandler(handler);
        try {
            service.expandForTeacher(teacher);
        } finally {
            julLogger.removeHandler(handler);
        }

        assertEquals(0, TeacherAvailability.listAll().size(), "the rule really did expand to zero rows");
        assertTrue(warnings.stream().anyMatch(msg -> msg.contains("expanded to zero")),
                "expected a WARN log for the zero-expansion rule, got: " + warnings);
    }

    // Note: the H5 "an expanded row fires the real constraint" check lives in
    // SchoolScheduleConstraintProviderTest (com.school.planner.solver package)
    // as expandedUnavailabilityRow_firesH5ForAnOverlappingLesson - it needs
    // BaseConstraintProvider.teacherUnavailability(), which is package-private.
}
