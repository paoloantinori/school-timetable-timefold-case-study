package com.school.planner.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
public class LabRotation extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public Teacher teacher;

    @ManyToOne
    public StudentGroup studentGroup;

    public DayOfWeek dayOfWeek;

    /** 0-based start slot index (0 = 08:00) */
    public int slotStart;

    /** Hours per session (e.g. 6 for acconciatura full-day labs) */
    public int blockSize;

    public LocalDate periodStart;
    public LocalDate periodEnd;

    /** Room tag to match (e.g. "acconciatura", "moda") */
    public String roomTag;

    /** Optional subject prefix filter. When set, pre-assigner only matches courses
     *  whose subject starts with this prefix. Needed to distinguish courses that share
     *  the same teacher + group + roomTag but belong to different rotation periods
     *  (e.g. Cad vs Controllo numerico). When null, matches by roomTag alone. */
    public String subjectPrefix;

    public LabRotation() {
    }

    public LabRotation(Teacher teacher, StudentGroup studentGroup, DayOfWeek dayOfWeek,
                       int slotStart, int blockSize, LocalDate periodStart, LocalDate periodEnd,
                       String roomTag) {
        this.teacher = teacher;
        this.studentGroup = studentGroup;
        this.dayOfWeek = dayOfWeek;
        this.slotStart = slotStart;
        this.blockSize = blockSize;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.roomTag = roomTag;
    }

    public LabRotation(Teacher teacher, StudentGroup studentGroup, DayOfWeek dayOfWeek,
                       int slotStart, int blockSize, LocalDate periodStart, LocalDate periodEnd,
                       String roomTag, String subjectPrefix) {
        this(teacher, studentGroup, dayOfWeek, slotStart, blockSize, periodStart, periodEnd, roomTag);
        this.subjectPrefix = subjectPrefix;
    }

    @Override
    public String toString() {
        return (teacher != null ? teacher.name : "?") + " → "
                + (studentGroup != null ? studentGroup.name : "?")
                + " " + dayOfWeek + " [" + periodStart + "–" + periodEnd + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LabRotation other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
