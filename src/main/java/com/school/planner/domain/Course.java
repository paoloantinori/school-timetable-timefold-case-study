package com.school.planner.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
public class Course extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String subject;

    @ManyToOne(fetch = FetchType.EAGER)
    public Teacher teacher;

    @ManyToOne(fetch = FetchType.EAGER)
    public StudentGroup studentGroup;

    public int totalHours;

    /**
     * Of {@link #totalHours}, how many are structurally unschedulable and
     * thus an expected, non-actionable part of any assigned-vs-scheduled
     * delta (TASK-373 code review H1/H4) - e.g. the golden Stage course's
     * closure-day rows and rows dated beyond the Timeslot horizon, neither of
     * which will ever get a pinned Lesson. Null/0 for ordinary courses.
     * Generic name (not Stage-specific) so any future accounting course with
     * a structurally-expected shortfall can reuse this field instead of
     * growing a new one. Set at golden import time
     * ({@code GoldenScheduleImportService#reconcileStageCourseHours}), read
     * by {@code ReconciliationService} to compute each row's actionable delta.
     */
    public Integer expectedUnscheduledHours;

    public boolean isLab = false;

    public int labBlockSize = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    public List<String> requiredRoomTags = new ArrayList<>();

    public boolean professionalizzanti = false;

    /**
     * Custom color for subject display in timeline (hex format, e.g., "#3b82f6").
     * If null, the frontend auto-generates a color from the palette.
     */
    @Column(length = 7)
    public String color;

    /**
     * Expected number of distinct days per week for this lab course.
     * Computed from totalHours, labBlockSize, and LabRotation period duration.
     * Defaults to 1 (single-day pattern) for non-lab courses or when not set.
     *
     * Used by M17/M17.2 constraints to classify single-day vs multi-day lab patterns.
     *
     * @see ExpectedDaysCalculator#computeExpectedDays(Course, LabRotation)
     */
    /**
     * Expected number of distinct days per week for this lab course.
     * Computed from totalHours, labBlockSize, and LabRotation period duration.
     * Defaults to 1 (single-day pattern) for non-lab courses or when not set.
     *
     * Used by M17/M17.2 constraints to classify single-day vs multi-day lab patterns.
     *
     * @see ExpectedDaysCalculator#computeExpectedDays(Course, LabRotation)
     */
    @Column(name = "expected_lab_days_per_week")
    public Integer expectedLabDaysPerWeek = 1;

    public Course() {
    }

    public Course(String subject, Teacher teacher) {
        this.subject = subject;
        this.teacher = teacher;
    }

    public Course(String subject, Teacher teacher, StudentGroup studentGroup, int totalHours) {
        this.subject = subject;
        this.teacher = teacher;
        this.studentGroup = studentGroup;
        this.totalHours = totalHours;
    }

    @Override
    public String toString() {
        return subject + " (" + (teacher != null ? teacher.name : "?") + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Course other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
