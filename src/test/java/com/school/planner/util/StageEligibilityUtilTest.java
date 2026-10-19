package com.school.planner.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Unit tests for StageEligibilityUtil.
 */
@QuarkusTest
public class StageEligibilityUtilTest {

    @Test
    public void testIsStageBlackout_NullGroup_ReturnsFalse() {
        LocalDate date = LocalDate.of(2026, 3, 20); // Thursday
        assertFalse(StageEligibilityUtil.isStageBlackout(date, null, List.of()));
    }

    @Test
    public void testIsStageBlackout_NullStagePatterns_ReturnsFalse() {
        StudentGroup group = new StudentGroup();
        group.id = 1L;
        LocalDate date = LocalDate.of(2026, 3, 20);
        assertFalse(StageEligibilityUtil.isStageBlackout(date, group, null));
    }

    @Test
    public void testIsStageBlackout_EmptyStagePatterns_ReturnsFalse() {
        StudentGroup group = new StudentGroup();
        group.id = 1L;
        LocalDate date = LocalDate.of(2026, 3, 20);
        assertFalse(StageEligibilityUtil.isStageBlackout(date, group, List.of()));
    }

    @Test
    public void testIsStageBlackout_NullDayOfWeekMask_ReturnsFalse() {
        StudentGroup group = new StudentGroup();
        group.id = 1L;

        StagePattern sp = new StagePattern();
        sp.studentGroup = group;
        sp.startDate = LocalDate.of(2026, 3, 1);
        sp.endDate = LocalDate.of(2026, 3, 31);
        sp.dayOfWeekMask = null; // Null mask

        LocalDate date = LocalDate.of(2026, 3, 20); // Thursday
        assertFalse(StageEligibilityUtil.isStageBlackout(date, group, List.of(sp)));
    }

    @Test
    public void testIsStageBlackout_HappyPath_ReturnsTrue() {
        StudentGroup group = new StudentGroup();
        group.id = 1L;

        StagePattern sp = new StagePattern();
        sp.studentGroup = group;
        sp.startDate = LocalDate.of(2026, 3, 1);
        sp.endDate = LocalDate.of(2026, 3, 31);
        Set<DayOfWeek> mask = new HashSet<>();
        mask.add(DayOfWeek.THURSDAY);
        sp.dayOfWeekMask = mask;

        LocalDate date = LocalDate.of(2026, 3, 19); // Thursday (2026-03-19)
        assertTrue(StageEligibilityUtil.isStageBlackout(date, group, List.of(sp)));
    }

    @Test
    public void testIsStageBlackout_WrongGroup_ReturnsFalse() {
        StudentGroup group1 = new StudentGroup();
        group1.id = 1L;
        StudentGroup group2 = new StudentGroup();
        group2.id = 2L;

        StagePattern sp = new StagePattern();
        sp.studentGroup = group1; // Pattern for group1
        sp.startDate = LocalDate.of(2026, 3, 1);
        sp.endDate = LocalDate.of(2026, 3, 31);
        Set<DayOfWeek> mask = new HashSet<>();
        mask.add(DayOfWeek.THURSDAY);
        sp.dayOfWeekMask = mask;

        LocalDate date = LocalDate.of(2026, 3, 19); // Thursday
        assertFalse(StageEligibilityUtil.isStageBlackout(date, group2, List.of(sp))); // Check group2
    }

    @Test
    public void testIsStageBlackout_WrongDayOfWeek_ReturnsFalse() {
        StudentGroup group = new StudentGroup();
        group.id = 1L;

        StagePattern sp = new StagePattern();
        sp.studentGroup = group;
        sp.startDate = LocalDate.of(2026, 3, 1);
        sp.endDate = LocalDate.of(2026, 3, 31);
        Set<DayOfWeek> mask = new HashSet<>();
        mask.add(DayOfWeek.MONDAY); // Monday, not Thursday
        sp.dayOfWeekMask = mask;

        LocalDate date = LocalDate.of(2026, 3, 19); // Thursday
        assertFalse(StageEligibilityUtil.isStageBlackout(date, group, List.of(sp)));
    }

    @Test
    public void testIsStageBlackout_OutsideDateRange_ReturnsFalse() {
        StudentGroup group = new StudentGroup();
        group.id = 1L;

        StagePattern sp = new StagePattern();
        sp.studentGroup = group;
        sp.startDate = LocalDate.of(2026, 4, 1); // April
        sp.endDate = LocalDate.of(2026, 4, 30);
        Set<DayOfWeek> mask = new HashSet<>();
        mask.add(DayOfWeek.THURSDAY);
        sp.dayOfWeekMask = mask;

        LocalDate date = LocalDate.of(2026, 3, 19); // March - outside range
        assertFalse(StageEligibilityUtil.isStageBlackout(date, group, List.of(sp)));
    }

    @Test
    public void testShouldSkipStageDay_NullCourse_ReturnsTrue() {
        StudentGroup group = new StudentGroup();
        group.allowProfessionalizzantiDuringStage = true;
        assertTrue(StageEligibilityUtil.shouldSkipStageDay(null, group));
    }

    @Test
    public void testShouldSkipStageDay_NonProfCourse_ReturnsTrue() {
        StudentGroup group = new StudentGroup();
        group.allowProfessionalizzantiDuringStage = true;

        Course course = new Course();
        course.professionalizzanti = false;

        assertTrue(StageEligibilityUtil.shouldSkipStageDay(course, group));
    }

    @Test
    public void testShouldSkipStageDay_ProfCourse_AllowDuringStageTrue_ReturnsFalse() {
        StudentGroup group = new StudentGroup();
        group.allowProfessionalizzantiDuringStage = true;

        Course course = new Course();
        course.professionalizzanti = true;

        assertFalse(StageEligibilityUtil.shouldSkipStageDay(course, group));
    }

    @Test
    public void testShouldSkipStageDay_ProfCourse_AllowDuringStageFalse_ReturnsTrue() {
        StudentGroup group = new StudentGroup();
        group.allowProfessionalizzantiDuringStage = false;

        Course course = new Course();
        course.professionalizzanti = true;

        assertTrue(StageEligibilityUtil.shouldSkipStageDay(course, group));
    }

    @Test
    public void testShouldSkipStageDay_ProfCourse_AllowDuringStageNull_ReturnsTrue() {
        StudentGroup group = new StudentGroup();
        group.allowProfessionalizzantiDuringStage = null; // Null = false

        Course course = new Course();
        course.professionalizzanti = true;

        assertTrue(StageEligibilityUtil.shouldSkipStageDay(course, group));
    }

    @Test
    public void testShouldSkipDate_CombinedCheck_TrueWhenBothConditionsMet() {
        StudentGroup group = new StudentGroup();
        group.id = 1L;
        group.allowProfessionalizzantiDuringStage = false;

        Course course = new Course();
        course.professionalizzanti = true;

        StagePattern sp = new StagePattern();
        sp.studentGroup = group;
        sp.startDate = LocalDate.of(2026, 3, 1);
        sp.endDate = LocalDate.of(2026, 3, 31);
        Set<DayOfWeek> mask = new HashSet<>();
        mask.add(DayOfWeek.THURSDAY);
        sp.dayOfWeekMask = mask;

        LocalDate date = LocalDate.of(2026, 3, 19); // Thursday
        assertTrue(StageEligibilityUtil.shouldSkipDate(course, group, date, List.of(sp)));
    }

    @Test
    public void testShouldSkipDate_CombinedCheck_FalseWhenNotStageDay() {
        StudentGroup group = new StudentGroup();
        group.id = 1L;
        group.allowProfessionalizzantiDuringStage = false;

        Course course = new Course();
        course.professionalizzanti = true;

        StagePattern sp = new StagePattern();
        sp.studentGroup = group;
        sp.startDate = LocalDate.of(2026, 3, 1);
        sp.endDate = LocalDate.of(2026, 3, 31);
        Set<DayOfWeek> mask = new HashSet<>();
        mask.add(DayOfWeek.THURSDAY);
        sp.dayOfWeekMask = mask;

        LocalDate date = LocalDate.of(2026, 3, 16); // Monday - not in mask (2026-03-16)
        assertFalse(StageEligibilityUtil.shouldSkipDate(course, group, date, List.of(sp)));
    }

    @Test
    public void testShouldSkipDate_CombinedCheck_TrueWhenNonProfCourse() {
        StudentGroup group = new StudentGroup();
        group.id = 1L;
        group.allowProfessionalizzantiDuringStage = false;

        Course course = new Course();
        course.professionalizzanti = false; // Non-prof course

        StagePattern sp = new StagePattern();
        sp.studentGroup = group;
        sp.startDate = LocalDate.of(2026, 3, 1);
        sp.endDate = LocalDate.of(2026, 3, 31);
        Set<DayOfWeek> mask = new HashSet<>();
        mask.add(DayOfWeek.THURSDAY);
        sp.dayOfWeekMask = mask;

        LocalDate date = LocalDate.of(2026, 3, 19); // Thursday
        // Non-prof courses skip stage days: shouldSkipStageDay=true, isStageBlackout=true
        // So shouldSkipDate returns true (true && true)
        assertTrue(StageEligibilityUtil.shouldSkipDate(course, group, date, List.of(sp)));
    }
}
