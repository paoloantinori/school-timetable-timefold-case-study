package com.school.planner.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import ai.timefold.solver.core.api.domain.common.PlanningId;

@Entity
public class Timeslot extends PanacheEntityBase implements Comparable<Timeslot> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    public Long id;

    public LocalDateTime startDateTime;
    public LocalDateTime endDateTime;

    public Timeslot() {
    }

    public Timeslot(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
    }

    public LocalDate getDate() {
        return startDateTime.toLocalDate();
    }

    public DayOfWeek getDayOfWeek() {
        return startDateTime.getDayOfWeek();
    }

    @Override
    public String toString() {
        return startDateTime + " - " + endDateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Timeslot other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }

    @Override
    public int compareTo(Timeslot other) {
        if (other == null) return 1;
        if (this.startDateTime == null && other.startDateTime == null) return 0;
        if (this.startDateTime == null) return -1;
        if (other.startDateTime == null) return 1;
        return this.startDateTime.compareTo(other.startDateTime);
    }
}
