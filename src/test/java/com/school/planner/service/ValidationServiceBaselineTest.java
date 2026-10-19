package com.school.planner.service;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import com.school.planner.domain.SchoolHoliday;
import com.school.planner.rest.dto.ValidationResult;
import com.school.planner.testsupport.LessonFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic baseline-semantics test for TASK-354 code review B1.
 *
 * <p>{@link ValidationResourceTest} (in {@code com.school.planner.rest}) exercises the
 * same holiday/H6 scenario over HTTP, but can't deterministically assert {@code
 * baseline == true} on the first call - {@link ValidationService} is an
 * {@code ApplicationScoped} singleton shared across the whole test JVM, so an earlier
 * test's CRUD mutation may have already triggered a background debounced validation
 * before that test runs. This test lives in the same package as {@link ValidationService}
 * specifically to call its package-private {@code resetLatestForTest()} hook, so "fresh
 * state" is real and the baseline assertion is deterministic regardless of suite order.
 */
@QuarkusTest
class ValidationServiceBaselineTest {

    @Inject
    ValidationService validationService;

    @Inject
    EntityManager em;

    private LocalDate lessonDate;

    @BeforeEach
    @Transactional
    void setup() {
        // Other tests in this shared H2 instance may leave rows in tables that hold a
        // @ManyToOne FK to Teacher/StudentGroup (LabRotation, LabAssistant,
        // GroupCalendarRule, RecurringAvailabilityRule, ProfessionalizzantiDay,
        // TeacherAvailability, StagePattern) - delete those first, or the blanket
        // Teacher/StudentGroup wipe below can hit a referential-integrity violation
        // depending on suite execution order.
        em.createQuery("DELETE FROM LabRotation").executeUpdate();
        em.createQuery("DELETE FROM LabAssistant").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM RecurringAvailabilityRule").executeUpdate();
        em.createQuery("DELETE FROM ProfessionalizzantiDay").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM StagePattern").executeUpdate();
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
        em.createQuery("DELETE FROM Room").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM SchoolHoliday").executeUpdate();

        lessonDate = LocalDate.of(2026, 9, 22); // a Tuesday, distinct from ValidationResourceTest's fixture date
        LessonFixtures.persistScheduledLesson("Baseline Test", lessonDate);

        validationService.resetLatestForTest();
    }

    @Test
    void freshStateProducesBaselineWithNoFlood() {
        ValidationResult baseline = validationService.runNow("test-baseline");

        assertTrue(baseline.baseline(), "A run with no prior snapshot must be flagged baseline=true");
        assertTrue(baseline.newlyViolated().isEmpty(),
                "A baseline run must report an empty newlyViolated, never a flood of pre-existing matches");

        mutateScheduleWithHolidayOnLessonDate();

        ValidationResult afterMutation = validationService.runNow("test-after-holiday");

        assertFalse(afterMutation.baseline(), "A run with a real prior snapshot must not be flagged baseline");
        assertEquals(-1, afterMutation.hard());

        List<ValidationResult.ViolationDelta> newlyViolated = afterMutation.newlyViolated();
        assertEquals(1, newlyViolated.size(), "Only the real delta (H6) should be reported, not a flood");
        ValidationResult.ViolationDelta h6Delta = newlyViolated.get(0);
        assertEquals("H6", h6Delta.constraintId());
        assertEquals(0, h6Delta.previousCount());
        assertEquals(1, h6Delta.currentCount());
        assertEquals(1, h6Delta.delta());
    }

    @Transactional
    void mutateScheduleWithHolidayOnLessonDate() {
        SchoolHoliday holiday = new SchoolHoliday();
        holiday.date = lessonDate;
        holiday.description = "Baseline test holiday";
        holiday.persist();
    }
}
