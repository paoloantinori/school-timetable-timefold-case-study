package com.school.planner.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * Professionalizzanti-specific days during stage periods.
 *
 * During stage (internship) periods, professionalizzanti courses should be
 * concentrated on a single constant day of the week. This entity marks
 * which specific days are designated for professionalizzanti lessons for each
 * student group.
 *
 * Example: For G07 stage (24 Mar - 29 May 2026), the admin would mark
 * Wednesday as a ProfessionalizzantiDay, meaning all professionalizzanti lessons
 * for G07 should be scheduled on Wednesday during this period.
 */
@Entity
public class ProfessionalizzantiDay extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public StudentGroup studentGroup;

    public LocalDate date;

    public DayOfWeek dayOfWeek;

    public String description;

    public ProfessionalizzantiDay() {
    }

    public ProfessionalizzantiDay(StudentGroup studentGroup, LocalDate date, DayOfWeek dayOfWeek, String description) {
        this.studentGroup = studentGroup;
        this.date = date;
        this.dayOfWeek = dayOfWeek;
        this.description = description;
    }

    @Override
    public String toString() {
        return studentGroup + ": " + date + " (" + dayOfWeek + ") - " + description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProfessionalizzantiDay other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
