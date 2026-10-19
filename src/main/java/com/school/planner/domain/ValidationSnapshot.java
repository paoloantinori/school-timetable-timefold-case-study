package com.school.planner.domain;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Persisted record of a solution-legality validation run (TASK-354).
 *
 * <p>Written by {@code ValidationSnapshotRepository} after every re-validation so the
 * "newly-illegal" diff survives an app restart. Only the latest {@code MAX_SNAPSHOTS}
 * rows are retained; older rows are pruned on each write.
 *
 * <p>{@link #countsJson} holds the serialized per-constraint match counts
 * (a JSON array of {@code ValidationResult.ConstraintCount}), since a full relational
 * shape isn't warranted for a small, append-only history table.
 */
@Entity
public class ValidationSnapshot extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Instant timestamp;

    public String trigger;

    public String scoreString;

    public long hardScore;

    public long mediumScore;

    public long softScore;

    @Column(columnDefinition = "TEXT")
    public String countsJson;

    public ValidationSnapshot() {
    }

    @Override
    public String toString() {
        return timestamp + " [" + trigger + "] " + scoreString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidationSnapshot other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
