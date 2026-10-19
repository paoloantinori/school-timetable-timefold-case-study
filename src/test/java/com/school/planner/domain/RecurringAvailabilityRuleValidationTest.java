package com.school.planner.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit tests (no {@code @QuarkusTest}) for
 * {@link RecurringAvailabilityRule#validateDateCoherence} and
 * {@link RecurringAvailabilityRule#overlaps} — both are pure, DB-free static
 * methods, following the same directly-unit-testable style as
 * {@code DataGenerator.parseCalendarCell} (see {@code GroupCalendarSeedTest}).
 *
 * Code-review fixes for TASK-345 (R1, R2).
 */
class RecurringAvailabilityRuleValidationTest {

    // ─── validateDateCoherence: effectiveFrom <= effectiveTo ────────────

    @Test
    void validateDateCoherence_invertedRange_isRejected() {
        String error = RecurringAvailabilityRule.validateDateCoherence(
                DayOfWeek.MONDAY, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 14));
        assertNotNull(error, "effectiveFrom after effectiveTo must be rejected");
    }

    @Test
    void validateDateCoherence_equalBounds_matchingWeekday_isAccepted() {
        // 2026-09-14 is a Monday.
        String error = RecurringAvailabilityRule.validateDateCoherence(
                DayOfWeek.MONDAY, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14));
        assertNull(error);
    }

    @Test
    void validateDateCoherence_openRange_neverRejectedOnOrdering() {
        // Only effectiveFrom set, or only effectiveTo set - nothing to compare.
        assertNull(RecurringAvailabilityRule.validateDateCoherence(
                DayOfWeek.FRIDAY, LocalDate.of(2026, 9, 14), null));
        assertNull(RecurringAvailabilityRule.validateDateCoherence(
                DayOfWeek.FRIDAY, null, LocalDate.of(2026, 9, 14)));
        assertNull(RecurringAvailabilityRule.validateDateCoherence(DayOfWeek.FRIDAY, null, null));
    }

    // ─── validateDateCoherence: single-date rule dayOfWeek must match ────

    @Test
    void validateDateCoherence_singleDate_weekdayMismatch_isRejected() {
        // 2026-09-14 is a Monday, not a Tuesday.
        String error = RecurringAvailabilityRule.validateDateCoherence(
                DayOfWeek.TUESDAY, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14));
        assertNotNull(error, "single-date rule with mismatched dayOfWeek must be rejected"
                + " - it would silently expand to zero rows");
    }

    @Test
    void validateDateCoherence_rangedRule_weekdayMismatchAllowed() {
        // A multi-day window (not effectiveFrom == effectiveTo) needs no weekday match -
        // the bucket-then-filter expansion logic handles any combination correctly.
        String error = RecurringAvailabilityRule.validateDateCoherence(
                DayOfWeek.TUESDAY, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21));
        assertNull(error);
    }

    // ─── overlaps: dayOfWeek must match ──────────────────────────────────

    @Test
    void overlaps_differentDayOfWeek_neverOverlaps() {
        RecurringAvailabilityRule a = rule(DayOfWeek.MONDAY, 0, 7, null, null);
        RecurringAvailabilityRule b = rule(DayOfWeek.TUESDAY, 0, 7, null, null);
        assertFalse(RecurringAvailabilityRule.overlaps(a, b));
    }

    // ─── overlaps: slot ranges must intersect ────────────────────────────

    @Test
    void overlaps_sameDaySlotRangesIntersect_isOverlap() {
        RecurringAvailabilityRule a = rule(DayOfWeek.MONDAY, 0, 4, null, null);
        RecurringAvailabilityRule b = rule(DayOfWeek.MONDAY, 4, 7, null, null);
        assertTrue(RecurringAvailabilityRule.overlaps(a, b), "slot 4 is shared by both ranges");
    }

    @Test
    void overlaps_sameDaySlotRangesDisjoint_isNotOverlap() {
        RecurringAvailabilityRule a = rule(DayOfWeek.MONDAY, 0, 3, null, null);
        RecurringAvailabilityRule b = rule(DayOfWeek.MONDAY, 4, 7, null, null);
        assertFalse(RecurringAvailabilityRule.overlaps(a, b));
    }

    // ─── overlaps: effective windows must also intersect ─────────────────

    @Test
    void overlaps_sameDaySameSlots_disjointEffectiveWindows_isNotOverlap() {
        // Same weekday and slots, but one is a dated absence in September and
        // the other in November (mirrors T12's 3 dated Monday rules).
        RecurringAvailabilityRule a = rule(DayOfWeek.MONDAY, 0, 7,
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14));
        RecurringAvailabilityRule b = rule(DayOfWeek.MONDAY, 0, 7,
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 16));
        assertFalse(RecurringAvailabilityRule.overlaps(a, b));
    }

    @Test
    void overlaps_sameDaySameSlots_intersectingEffectiveWindows_isOverlap() {
        RecurringAvailabilityRule a = rule(DayOfWeek.MONDAY, 0, 7, null, null); // always
        RecurringAvailabilityRule b = rule(DayOfWeek.MONDAY, 0, 7,
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)); // one dated Monday
        assertTrue(RecurringAvailabilityRule.overlaps(a, b), "an 'always' rule overlaps any dated instance");
    }

    @Test
    void overlaps_isTypeAgnostic_vacationAndPreferenceStillOverlap() {
        RecurringAvailabilityRule vacation = rule(DayOfWeek.TUESDAY, 0, 5, null, null);
        vacation.type = AvailabilityType.VACATION;
        RecurringAvailabilityRule preference = rule(DayOfWeek.TUESDAY, 3, 6, null, null);
        preference.type = AvailabilityType.PREFERENCE;
        assertTrue(RecurringAvailabilityRule.overlaps(vacation, preference),
                "overlap check must not special-case type - a VACATION+PREFERENCE clash on the same hour is"
                + " equally incoherent policy");
    }

    private static RecurringAvailabilityRule rule(DayOfWeek dayOfWeek, int minSlot, int maxSlot,
                                                    LocalDate effectiveFrom, LocalDate effectiveTo) {
        RecurringAvailabilityRule rule = new RecurringAvailabilityRule();
        rule.dayOfWeek = dayOfWeek;
        rule.minSlot = minSlot;
        rule.maxSlot = maxSlot;
        rule.effectiveFrom = effectiveFrom;
        rule.effectiveTo = effectiveTo;
        rule.type = AvailabilityType.VACATION;
        return rule;
    }
}
