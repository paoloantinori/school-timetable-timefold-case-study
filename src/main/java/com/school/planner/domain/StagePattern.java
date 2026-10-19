package com.school.planner.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
public class StagePattern extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public StudentGroup studentGroup;

    public LocalDate startDate;
    public LocalDate endDate;

    @ElementCollection(fetch = FetchType.EAGER)
    public Set<DayOfWeek> dayOfWeekMask = new HashSet<>();

    public StagePattern() {
    }

    public StagePattern(StudentGroup studentGroup, LocalDate startDate, LocalDate endDate,
                        Set<DayOfWeek> dayOfWeekMask) {
        this.studentGroup = studentGroup;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dayOfWeekMask = dayOfWeekMask != null ? dayOfWeekMask : new HashSet<>();
    }

    @Override
    public String toString() {
        return studentGroup + " stage " + startDate + " to " + endDate;
    }

    /**
     * Expand this stage pattern into a set of individual dates.
     * Excludes weekends and the provided holiday dates.
     *
     * @param holidays Set of holiday dates to exclude
     * @return Set of dates that fall within this stage pattern
     */
    public Set<LocalDate> expandDates(Set<LocalDate> holidays) {
        Set<LocalDate> dates = new HashSet<>();
        if (startDate == null || endDate == null || dayOfWeekMask.isEmpty()) {
            return dates;
        }

        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            // Include if day of week matches mask, not a weekend, not a holiday
            if (dayOfWeekMask.contains(dow)
                    && dow != DayOfWeek.SATURDAY
                    && dow != DayOfWeek.SUNDAY
                    && !holidays.contains(cursor)) {
                dates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StagePattern other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
