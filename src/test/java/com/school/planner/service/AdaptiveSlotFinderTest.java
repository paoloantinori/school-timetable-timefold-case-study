package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.LabRotation;
import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Unit tests for AdaptiveSlotFinder.
 */
@QuarkusTest
class AdaptiveSlotFinderTest {

    @Inject
    AdaptiveSlotFinder slotFinder;

    private Teacher testTeacher;
    private StudentGroup testGroup;
    private Room testRoom;
    private Set<LocalDate> holidays;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test entities
        testTeacher = new Teacher();
        testTeacher.name = "Test Teacher";
        testTeacher.persist();

        testGroup = new StudentGroup();
        testGroup.name = "Test Group";
        testGroup.size = 20;
        testGroup.persist();

        testRoom = new Room();
        testRoom.name = "Test Lab";
        testRoom.capacity = 25;
        testRoom.tags = List.of("testlab");
        testRoom.persist();

        holidays = new HashSet<>();
    }

    @Test
    @Transactional
    void testFindBestDayForRotation_withNoAvailabilityRestrictions() {
        // Given a rotation with no teacher availability restrictions
        LabRotation rotation = new LabRotation(
                testTeacher, testGroup, null,  // dayOfWeek = null for adaptive selection
                0, 6,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                "testlab");

        // When finding the best day
        AdaptiveSlotFinder.BestDayResult result = slotFinder.findBestDayForRotation(
                rotation, testRoom, holidays, List.of());

        // Then a day should be selected
        assertNotNull(result.bestDay);
        assertNotEquals(DayOfWeek.SATURDAY, result.bestDay);
        assertNotEquals(DayOfWeek.SUNDAY, result.bestDay);
        assertTrue(result.availableWeeks > 0);
    }

    @Test
    @Transactional
    void testFindBestDayForRotation_withVacationBlockingWednesday() {
        // Given a teacher with vacation every Wednesday in February
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);

        // Add VACATION for every Wednesday
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.WEDNESDAY) {
                TeacherAvailability ta = new TeacherAvailability(
                        testTeacher,
                        date.atStartOfDay(),
                        date.atTime(LocalTime.MAX),
                        AvailabilityType.VACATION);
                ta.persist();
            }
        }

        LabRotation rotation = new LabRotation(
                testTeacher, testGroup, null,
                0, 6,
                start, end,
                "testlab");

        // When finding the best day
        AdaptiveSlotFinder.BestDayResult result = slotFinder.findBestDayForRotation(
                rotation, testRoom, holidays, List.of());

        // Then Wednesday should NOT be selected
        assertNotNull(result.bestDay);
        assertNotEquals(DayOfWeek.WEDNESDAY, result.bestDay);
    }

    @Test
    @Transactional
    void testIsTeacherUnavailableAllDay_withVacation() {
        LocalDate testDate = LocalDate.of(2026, 2, 4); // Wednesday

        // Initially available
        assertFalse(slotFinder.isTeacherUnavailableAllDay(testTeacher, testDate));

        // Add vacation for this day
        TeacherAvailability ta = new TeacherAvailability(
                testTeacher,
                testDate.atStartOfDay(),
                testDate.atTime(LocalTime.MAX),
                AvailabilityType.VACATION);
        ta.persist();

        // Now unavailable
        assertTrue(slotFinder.isTeacherUnavailableAllDay(testTeacher, testDate));
    }

    @Test
    @Transactional
    void testIsTeacherUnavailableAllDay_withSick() {
        LocalDate testDate = LocalDate.of(2026, 2, 5); // Thursday

        // Initially available
        assertFalse(slotFinder.isTeacherUnavailableAllDay(testTeacher, testDate));

        // Add sick leave for this day
        TeacherAvailability ta = new TeacherAvailability(
                testTeacher,
                testDate.atStartOfDay(),
                testDate.atTime(LocalTime.MAX),
                AvailabilityType.SICK);
        ta.persist();

        // Now unavailable
        assertTrue(slotFinder.isTeacherUnavailableAllDay(testTeacher, testDate));
    }

    @Test
    @Transactional
    void testCountAvailableWeeks_withHolidays() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);

        // Add a holiday on a Tuesday
        holidays.add(LocalDate.of(2026, 2, 10)); // Tuesday

        // Count Tuesdays (should be 4 Tuesdays in Feb 2026, minus 1 holiday = 3)
        int count = slotFinder.countAvailableWeeks(testTeacher, DayOfWeek.TUESDAY, start, end, holidays);

        // February 2026 has 4 Tuesdays: 3, 10, 17, 24
        // Minus holiday on 10th = 3 available
        assertEquals(3, count);
    }

    @Test
    @Transactional
    void testComputeValidDates_withDayOfWeekSet() {
        // Given a rotation with dayOfWeek explicitly set
        LabRotation rotation = new LabRotation(
                testTeacher, testGroup, DayOfWeek.THURSDAY,
                0, 6,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                "testlab");

        // When computing valid dates
        List<LocalDate> validDates = slotFinder.computeValidDates(
                rotation, DayOfWeek.THURSDAY, holidays, List.of());

        // Then should return all Thursdays in the period
        assertEquals(4, validDates.size()); // Feb 2026 has 4 Thursdays: 5, 12, 19, 26
        assertTrue(validDates.stream().allMatch(d -> d.getDayOfWeek() == DayOfWeek.THURSDAY));
    }

    @Test
    @Transactional
    void testComputeValidDates_withHolidayExclusion() {
        // Given a holiday on a specific Thursday
        holidays.add(LocalDate.of(2026, 2, 12)); // Thursday

        LabRotation rotation = new LabRotation(
                testTeacher, testGroup, DayOfWeek.THURSDAY,
                0, 6,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                "testlab");

        // When computing valid dates
        List<LocalDate> validDates = slotFinder.computeValidDates(
                rotation, DayOfWeek.THURSDAY, holidays, List.of());

        // Then should exclude the holiday
        assertEquals(3, validDates.size());
        assertFalse(validDates.contains(LocalDate.of(2026, 2, 12)));
    }

    @Test
    @Transactional
    void testFindBestDaysForOverflow_excludesPrimaryDay() {
        LabRotation rotation = new LabRotation(
                testTeacher, testGroup, null,
                0, 6,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                "testlab");

        // Find overflow days (should exclude primary day)
        List<AdaptiveSlotFinder.DayWithScore> overflowDays = slotFinder.findBestDaysForOverflow(
                rotation, testRoom, DayOfWeek.MONDAY, holidays, List.of());

        // Should not include Monday
        assertTrue(overflowDays.stream().noneMatch(d -> d.day == DayOfWeek.MONDAY));
        // Should not include weekends
        assertTrue(overflowDays.stream().noneMatch(d -> d.day == DayOfWeek.SATURDAY));
        assertTrue(overflowDays.stream().noneMatch(d -> d.day == DayOfWeek.SUNDAY));
    }

    @Test
    @Transactional
    void testBackwardsCompatibility_withDayOfWeekSet() {
        // Given a rotation with dayOfWeek explicitly set (legacy behavior)
        LabRotation rotation = new LabRotation(
                testTeacher, testGroup, DayOfWeek.FRIDAY,
                0, 5,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                "testlab");

        // When using computeValidDates with explicit day
        List<LocalDate> validDates = slotFinder.computeValidDates(
                rotation, DayOfWeek.FRIDAY, holidays, List.of());

        // Then should return Fridays (backwards compatible)
        assertEquals(4, validDates.size()); // Feb 2026 has 4 Fridays: 6, 13, 20, 27
        assertTrue(validDates.stream().allMatch(d -> d.getDayOfWeek() == DayOfWeek.FRIDAY));
    }

    // ─── Multi-Option Selection Tests ─────────────────────────────────

    @Test
    @Transactional
    void testGenerateAllDayCandidates_returnsCandidatesForAllWeekdays() {
        // Use blockSize=5 so it can fit on all weekdays including Friday (which has 5 slots)
        LabRotation rotation = new LabRotation(
                testTeacher, testGroup, null,
                0, 5,  // blockSize=5 to fit on all weekdays
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                "testlab");

        Map<DayOfWeek, AdaptiveSlotFinder.DayCandidate> candidates =
                slotFinder.generateAllDayCandidates(rotation, testRoom, holidays, List.of());

        // Should have candidates for all weekdays (MON-FRI)
        assertEquals(5, candidates.size());
        assertTrue(candidates.containsKey(DayOfWeek.MONDAY));
        assertTrue(candidates.containsKey(DayOfWeek.TUESDAY));
        assertTrue(candidates.containsKey(DayOfWeek.WEDNESDAY));
        assertTrue(candidates.containsKey(DayOfWeek.THURSDAY));
        assertTrue(candidates.containsKey(DayOfWeek.FRIDAY));
    }

    @Test
    @Transactional
    void testGenerateAllDayCandidates_withTeacherVacation() {
        // Block every Wednesday with vacation
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.WEDNESDAY) {
                TeacherAvailability ta = new TeacherAvailability(
                        testTeacher,
                        date.atStartOfDay(),
                        date.atTime(LocalTime.MAX),
                        AvailabilityType.VACATION);
                ta.persist();
            }
        }

        LabRotation rotation = new LabRotation(
                testTeacher, testGroup, null,
                0, 6, start, end, "testlab");

        Map<DayOfWeek, AdaptiveSlotFinder.DayCandidate> candidates =
                slotFinder.generateAllDayCandidates(rotation, testRoom, holidays, List.of());

        // Wednesday should have blocked weeks
        AdaptiveSlotFinder.DayCandidate wedCandidate = candidates.get(DayOfWeek.WEDNESDAY);
        assertTrue(wedCandidate.blockedWeeks > 0);
        assertFalse(wedCandidate.isConflictFree());
        assertEquals(0, wedCandidate.availableWeeks);
    }

    @Test
    @Transactional
    void testSelectBestConflictFreeDay_picksConflictFreeOption() {
        LabRotation rotation = new LabRotation(
                testTeacher, testGroup, null,
                0, 6,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                "testlab");

        Map<DayOfWeek, AdaptiveSlotFinder.DayCandidate> candidates =
                slotFinder.generateAllDayCandidates(rotation, testRoom, holidays, List.of());

        DayOfWeek bestDay = slotFinder.selectBestConflictFreeDay(candidates);

        // Should pick a conflict-free day
        assertNotNull(bestDay);
        AdaptiveSlotFinder.DayCandidate selected = candidates.get(bestDay);
        assertTrue(selected.isConflictFree());
        assertTrue(selected.availableWeeks > 0);
    }

    @Test
    @Transactional
    void testSelectBestConflictFreeDay_returnsNullWhenAllHaveConflicts() {
        // Block all weekdays with vacation
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                TeacherAvailability ta = new TeacherAvailability(
                        testTeacher,
                        date.atStartOfDay(),
                        date.atTime(LocalTime.MAX),
                        AvailabilityType.VACATION);
                ta.persist();
            }
        }

        LabRotation rotation = new LabRotation(
                testTeacher, testGroup, null,
                0, 6, start, end, "testlab");

        Map<DayOfWeek, AdaptiveSlotFinder.DayCandidate> candidates =
                slotFinder.generateAllDayCandidates(rotation, testRoom, holidays, List.of());

        DayOfWeek bestDay = slotFinder.selectBestConflictFreeDay(candidates);

        // No conflict-free day should be available
        assertNull(bestDay);
    }

    @Test
    @Transactional
    void testGetSortedCandidates_returnsBestFirst() {
        // Block Monday with vacation
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.MONDAY) {
                TeacherAvailability ta = new TeacherAvailability(
                        testTeacher,
                        date.atStartOfDay(),
                        date.atTime(LocalTime.MAX),
                        AvailabilityType.VACATION);
                ta.persist();
            }
        }

        LabRotation rotation = new LabRotation(
                testTeacher, testGroup, null,
                0, 6, start, end, "testlab");

        Map<DayOfWeek, AdaptiveSlotFinder.DayCandidate> candidates =
                slotFinder.generateAllDayCandidates(rotation, testRoom, holidays, List.of());

        List<AdaptiveSlotFinder.DayCandidate> sorted = slotFinder.getSortedCandidates(candidates);

        // Should return at least some candidates
        assertFalse(sorted.isEmpty());

        // Sorted by conflict score (ascending), then available weeks (descending)
        // First element should have lowest conflict score
        int minScore = sorted.stream().mapToInt(AdaptiveSlotFinder.DayCandidate::getConflictScore).min().orElse(0);
        assertEquals(minScore, sorted.get(0).getConflictScore());

        // Monday should not be first (has vacation conflicts)
        if (sorted.size() > 1) {
            // If Monday is in the list, it shouldn't be first unless all have conflicts
            int mondayIndex = -1;
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).day == DayOfWeek.MONDAY) {
                    mondayIndex = i;
                    break;
                }
            }
            // If Monday exists and has conflicts, it should be after conflict-free days
            if (mondayIndex >= 0 && sorted.get(mondayIndex).getConflictScore() > 0) {
                // There should be at least one conflict-free day before Monday
                assertTrue(sorted.stream()
                        .limit(mondayIndex)
                        .anyMatch(c -> c.isConflictFree()));
            }
        }
    }

    @Test
    @Transactional
    void testDayCandidate_conflictScoring() {
        // Block one week of Tuesday
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);

        TeacherAvailability ta = new TeacherAvailability(
                testTeacher,
                LocalDate.of(2026, 2, 3).atStartOfDay(), // One Tuesday
                LocalDate.of(2026, 2, 3).atTime(LocalTime.MAX),
                AvailabilityType.VACATION);
        ta.persist();

        LabRotation rotation = new LabRotation(
                testTeacher, testGroup, null,
                0, 6, start, end, "testlab");

        Map<DayOfWeek, AdaptiveSlotFinder.DayCandidate> candidates =
                slotFinder.generateAllDayCandidates(rotation, testRoom, holidays, List.of());

        // Tuesday should have 1 blocked week, conflict score = 1 * 10 = 10
        AdaptiveSlotFinder.DayCandidate tuesday = candidates.get(DayOfWeek.TUESDAY);
        assertEquals(1, tuesday.blockedWeeks);
        assertEquals(10, tuesday.conflictScore);
        assertEquals(3, tuesday.availableWeeks); // 4 Tuesdays - 1 blocked = 3

        // Other days should be conflict-free
        AdaptiveSlotFinder.DayCandidate wednesday = candidates.get(DayOfWeek.WEDNESDAY);
        assertTrue(wednesday.isConflictFree());
    }
}
