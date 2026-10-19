package com.school.planner.service;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.LabRotation;

import io.quarkus.test.junit.QuarkusTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class ExpectedDaysCalculatorTest {

    @Test
    public void testSingleDayCourse_FullYearPeriod() {
        Course course = new Course();
        course.isLab = true;
        course.totalHours = 50;
        course.labBlockSize = 2; // 25 blocks
        course.subject = "Test Course";

        LabRotation rotation = new LabRotation();
        rotation.periodStart = LocalDate.of(2025, 9, 1);
        rotation.periodEnd = LocalDate.of(2026, 6, 15); // ~38 weeks

        int expected = ExpectedDaysCalculator.computeExpectedDays(course, rotation);

        // 25 blocks / 38 weeks = 0.66 → rounds to 1
        assertEquals(1, expected);
    }

    @Test
    public void testMultiDayCourse_ShortPeriod() {
        Course course = new Course();
        course.isLab = true;
        course.totalHours = 50;
        course.labBlockSize = 2; // 25 blocks
        course.subject = "Test Course";

        LabRotation rotation = new LabRotation();
        rotation.periodStart = LocalDate.of(2025, 9, 1);
        rotation.periodEnd = LocalDate.of(2025, 11, 15); // ~10.5 weeks

        int expected = ExpectedDaysCalculator.computeExpectedDays(course, rotation);

        // 25 blocks / 10.5 weeks = 2.38 → rounds to 2
        assertEquals(2, expected);
    }

    @Test
    public void testFullYearCourse() {
        Course course = new Course();
        course.isLab = true;
        course.totalHours = 300;
        course.labBlockSize = 4; // 75 blocks
        course.subject = "Test Course";

        LabRotation rotation = new LabRotation();
        rotation.periodStart = LocalDate.of(2025, 9, 1);
        rotation.periodEnd = LocalDate.of(2026, 6, 15); // ~38 weeks

        int expected = ExpectedDaysCalculator.computeExpectedDays(course, rotation);

        // 75 blocks / 38 weeks = 1.97 → rounds to 2
        assertEquals(2, expected);
    }

    @Test
    public void testNonLabCourse() {
        Course course = new Course();
        course.isLab = false;
        course.totalHours = 100;
        course.labBlockSize = 2;

        int expected = ExpectedDaysCalculator.computeExpectedDays(course, null);

        // Non-lab courses default to 1
        assertEquals(1, expected);
    }

    @Test
    public void testLabCourse_NoRotation() {
        Course course = new Course();
        course.isLab = true;
        course.totalHours = 50;
        course.labBlockSize = 2; // 25 blocks
        course.subject = "Test Course";

        int expected = ExpectedDaysCalculator.computeExpectedDays(course, null);

        // No rotation → uses 38 weeks default
        // 25 blocks / 38 weeks = 0.66 → rounds to 1
        assertEquals(1, expected);
    }

    @Test
    public void testLabCourse_ZeroBlockSize() {
        Course course = new Course();
        course.isLab = true;
        course.totalHours = 100;
        course.labBlockSize = 0; // Invalid

        int expected = ExpectedDaysCalculator.computeExpectedDays(course, null);

        // Invalid block size → default to 1
        assertEquals(1, expected);
    }

    @Test
    public void testLabCourse_NullCourse() {
        int expected = ExpectedDaysCalculator.computeExpectedDays(null, null);
        assertEquals(1, expected);
    }

    @Test
    public void testComputeExpectedDays_UsesFieldWhenSet() {
        Course course = new Course();
        course.isLab = true;
        course.totalHours = 50;
        course.labBlockSize = 2;
        course.expectedLabDaysPerWeek = 3; // Pre-computed

        int expected = ExpectedDaysCalculator.computeExpectedDays(course);

        // Should use the pre-computed field value
        assertEquals(3, expected);
    }

    @Test
    public void testComputeExpectedDays_FallbackWhenFieldZero() {
        Course course = new Course();
        course.isLab = true;
        course.totalHours = 50;
        course.labBlockSize = 2; // 25 blocks
        course.expectedLabDaysPerWeek = 0; // Not set

        int expected = ExpectedDaysCalculator.computeExpectedDays(course);

        // Should fallback to legacy computation
        // 25 blocks / 38 weeks = 0.66 → rounds to 1
        assertEquals(1, expected);
    }
}
