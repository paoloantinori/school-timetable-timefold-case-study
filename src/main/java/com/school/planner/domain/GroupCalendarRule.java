package com.school.planner.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Per-group, per-weekday slot-level calendar opening pattern (TASK-342).
 *
 * Some student groups never use the full 8-slot day (e.g. first-year groups
 * have no afternoons and a short Friday; G14 only runs Thu+Fri). This entity
 * expresses which slot indices [minSlot, maxSlot] (inclusive) are open for a
 * given student group on a given weekday. A day with no lessons at all (fully
 * closed) is represented as maxSlot &lt; minSlot (an empty range that never
 * matches any slot index) rather than a separate boolean flag.
 *
 * Date-scoped overrides (effectiveFrom/effectiveTo) let a single date deviate
 * from the group's normal weekly pattern (e.g. the first school day starting
 * at 09:00 instead of 08:00). When multiple rules for the same (group,
 * weekday) apply to a date, the most specific one wins — see
 * {@link com.school.planner.service.GroupCalendarService} for resolution
 * semantics. A group/weekday with no matching rule at all is fully open
 * (backward compatible with the pre-TASK-342 model).
 */
@Entity
public class GroupCalendarRule extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public StudentGroup studentGroup;

    public DayOfWeek dayOfWeek;

    /** Inclusive first open slot index (0-7, see TimeslotSequence.SLOT_START_TIMES). */
    public int minSlot;

    /** Inclusive last open slot index (0-7). maxSlot &lt; minSlot means the day is fully closed. */
    public int maxSlot;

    /** Null means "always" (no lower bound). */
    public LocalDate effectiveFrom;

    /** Null means "always" (no upper bound). */
    public LocalDate effectiveTo;

    public GroupCalendarRule() {
    }

    public GroupCalendarRule(StudentGroup studentGroup, DayOfWeek dayOfWeek, int minSlot, int maxSlot) {
        this(studentGroup, dayOfWeek, minSlot, maxSlot, null, null);
    }

    public GroupCalendarRule(StudentGroup studentGroup, DayOfWeek dayOfWeek, int minSlot, int maxSlot,
                             LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.studentGroup = studentGroup;
        this.dayOfWeek = dayOfWeek;
        this.minSlot = minSlot;
        this.maxSlot = maxSlot;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    @Override
    public String toString() {
        return studentGroup + " " + dayOfWeek + " [" + minSlot + "-" + maxSlot + "]"
                + (effectiveFrom != null || effectiveTo != null ? " (" + effectiveFrom + ".." + effectiveTo + ")" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupCalendarRule other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
