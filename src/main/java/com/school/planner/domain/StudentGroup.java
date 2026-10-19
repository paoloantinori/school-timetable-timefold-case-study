package com.school.planner.domain;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import ai.timefold.solver.core.api.domain.common.PlanningId;

@Entity
public class StudentGroup extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    public Long id;

    public String name;
    public int size;

    /**
     * Maximum hours this student group can attend per day.
     * null = unlimited (default, standard 6-hour day).
     * Used for groups with reduced schedule (e.g., G14 groups).
     */
    public Integer maxHoursPerDay;

    /**
     * Date when this student group starts attending school.
     * null = starts from the beginning of the school year (default).
     * Used for groups that join later in the year (task-009).
     */
    public LocalDate startDate;

    /**
     * VRP-buffered ideal end date for this student group's schedule.
     * Derived from idealEndDateInfo.idealEndDate() with a buffer for solver flexibility.
     * Not persisted - used by ValueRangeProvider and M12 constraint to limit timeslot options.
     * This implements horizontal compactness via search space reduction
     * instead of hard constraint competition.
     *
     * The derivation chain is: idealEndDate (tight) → +buffer → idealEndDateVrpBuffered.
     * The UI uses idealEndDateInfo.idealEndDate() for cell coloring.
     */
    @Transient
    public LocalDate idealEndDateVrpBuffered;

    /**
     * Extended information about the ideal end date calculation.
     * Populated by TargetEndDateCalculator for UI display.
     * Contains breakdown: total hours, excluded days, available days.
     * TASK-109: Show ideal last day of school for student groups.
     */
    @Transient
    public IdealEndDateInfo idealEndDateInfo;

    /**
     * Whether this student group allows professionalizzanti courses during stage periods.
     * Default: false (professionalizzanti courses are blocked during stage).
     * TASK-272: Per-group setting to control professionalizzanti scheduling during stage.
     */
    @jakarta.persistence.Column(name = "allow_professionalizzanti_during_stage")
    public Boolean allowProfessionalizzantiDuringStage = true;


    /**
     * Completion information for this student group.
     * Computed dynamically when schedule is loaded.
     * Contains the last lesson date and compaction metrics.
     * Not persisted - calculated from actual lesson assignments.
     */
    @Transient
    public CompletionInfo completionInfo;
    public StudentGroup() {
    }

    public StudentGroup(String name, int size) {
        this.name = name;
        this.size = size;
    }

    public StudentGroup(String name, int size, Integer maxHoursPerDay) {
        this.name = name;
        this.size = size;
        this.maxHoursPerDay = maxHoursPerDay;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * ID-based equality for use in Timefold Joiners.
     * Falls back to identity comparison for transient entities (null id).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StudentGroup other)) return false;
        return id != null && id.equals(other.id);
    }

    /**
     * Consistent hashCode based on id.
     * Uses class hashCode for transient entities to satisfy JPA requirements.
     */
    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
