package com.school.planner.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import ai.timefold.solver.core.api.domain.common.PlanningId;

@Entity
public class Room extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    public Long id;

    public String name;
    public int capacity;

    @ElementCollection(fetch = FetchType.EAGER)
    public List<String> tags = new ArrayList<>();

    public Room() {
    }

    public Room(String name, int capacity, List<String> tags) {
        this.name = name;
        this.capacity = capacity;
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    /** True if this room carries a lab/workshop tag (the 7 tagged rooms: R14,
     *  R15, R11, LAB.INFO 1/2, R16, R17). Untagged rooms are
     *  ordinary aulas (plus the cap-0 R18 overflow). Single source for the "is this
     *  a lab room" check shared by {@code Lesson.getAvailableRooms}' value range and the M-LF/S10
     *  discouragements. Null-safe: {@code tags} is normally non-null (field-init + constructor-
     *  normalized) but is public/mutable, so the guard stays. */
    @JsonIgnore
    public boolean isTagged() {
        return tags != null && !tags.isEmpty();
    }

    /** True when this room can host a lesson. A room with capacity <= 0 has no seats and is not
     *  assignable: it disappears from the value domain ({@code Lesson.getAvailableRooms} filters
     *  on this). General rule on capacity, never a room name (TASK-456): today the only such room
     *  is R18 (cap 0). Named alongside {@link #isTagged()} so the two "is this room
     *  a candidate" predicates share one home. */
    public boolean isAssignable() {
        return capacity > 0;
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
        if (!(o instanceof Room other)) return false;
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
