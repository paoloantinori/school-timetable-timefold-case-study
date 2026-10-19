package com.school.planner.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * A lab assistant assignment: a teacher who assists in a specific lab room type.
 * The school defines "doppio" (co-teaching) as teacher-to-room, not teacher-to-course:
 * "assistenti di laboratorio, il secondo prof in un lab."
 *
 * Used by M-LA constraints (MEDIUM) to penalize scheduling the assistant's own
 * lessons/blocks at times when a lab session is running in their assigned room.
 * The solver decides which sessions the assistant can attend (soft preference),
 * rather than requiring attendance at all sessions (old HARD H-D1 approach).
 */
@Entity
public class LabAssistant extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public Teacher teacher;

    /**
     * The room tag this teacher assists in (e.g., "acconciatura", "moda",
     * "meccanica", "termoidraulica"). Matches Room.tags values.
     */
    public String roomTag;

    public LabAssistant() {
    }

    public LabAssistant(Teacher teacher, String roomTag) {
        this.teacher = teacher;
        this.roomTag = roomTag;
    }

    @Override
    public String toString() {
        return "LabAssistant(" + (teacher != null ? teacher.name : "?") + " → " + roomTag + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LabAssistant other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
