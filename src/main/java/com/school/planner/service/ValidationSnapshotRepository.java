package com.school.planner.service;

import java.util.List;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.ValidationSnapshot;
import com.school.planner.rest.dto.ValidationResult;

/**
 * Persistence for {@link ValidationSnapshot} rows (TASK-354).
 *
 * <p>Kept as its own bean (rather than methods on {@link ValidationService}) so its
 * {@code @Transactional} methods are invoked through the CDI proxy. Note
 * (TASK-587, verified 6/09): ArC intercepts self-invoked {@code @Transactional}
 * methods too - the separate bean is defensive structure, not an interceptor-bypass
 * necessity, and it keeps the single-writer invariant below explicit.
 *
 * <p><b>Single-writer invariant (code review B9):</b> {@link #persist} is only ever
 * called from {@code ValidationService.validateNow(String)}, which is {@code
 * synchronized} — so at most one thread is ever writing a snapshot (and pruning old
 * ones) at a time. {@link #pruneOldSnapshots()} relies on this: it assumes at most one
 * row is over {@link #MAX_SNAPSHOTS} when it runs, which only holds if writes can't
 * race each other. If a second writer were ever added (e.g. a REST endpoint calling
 * {@code persist} directly instead of going through the synchronized service method),
 * concurrent inserts could push the count arbitrarily far over the cap before either
 * prune loop catches up.
 */
@ApplicationScoped
public class ValidationSnapshotRepository {

    private static final Logger LOG = Logger.getLogger(ValidationSnapshotRepository.class);

    /** Keep only the latest N snapshots (TASK-354 spec). */
    private static final int MAX_SNAPSHOTS = 20;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public boolean hasScheduledLessons() {
        return Lesson.count("timeslot is not null") > 0;
    }

    @Transactional
    public void persist(ValidationResult result) {
        ValidationSnapshot snapshot = new ValidationSnapshot();
        snapshot.timestamp = result.timestamp();
        snapshot.trigger = result.trigger();
        snapshot.scoreString = result.score();
        snapshot.hardScore = result.hard();
        snapshot.mediumScore = result.medium();
        snapshot.softScore = result.soft();
        try {
            snapshot.countsJson = objectMapper.writeValueAsString(result.perConstraint());
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to serialize per-constraint counts for ValidationSnapshot; storing empty array", e);
            snapshot.countsJson = "[]";
        }
        snapshot.persist();
        pruneOldSnapshots();
    }

    /**
     * Drop the oldest row(s) beyond {@link #MAX_SNAPSHOTS}. Since {@link #persist} adds
     * exactly one row per call, at most one row is ever over the cap in practice; the
     * loop is just as simple and stays correct if that invariant ever changes.
     */
    private void pruneOldSnapshots() {
        while (ValidationSnapshot.count() > MAX_SNAPSHOTS) {
            ValidationSnapshot oldest = ValidationSnapshot.find("order by timestamp asc").firstResult();
            if (oldest == null) break;
            oldest.delete();
        }
    }

    /**
     * Restore the latest persisted snapshot on startup so the "newly-illegal" diff
     * is meaningful even across an app restart.
     */
    @Transactional
    public ValidationResult loadLatestFromDb() {
        ValidationSnapshot latest = ValidationSnapshot.find("order by timestamp desc").firstResult();
        if (latest == null) {
            return null;
        }
        List<ValidationResult.ConstraintCount> counts;
        try {
            counts = objectMapper.readValue(latest.countsJson,
                    new TypeReference<List<ValidationResult.ConstraintCount>>() {});
        } catch (Exception e) {
            LOG.warn("Failed to deserialize per-constraint counts from ValidationSnapshot id=" + latest.id, e);
            counts = List.of();
        }
        // baseline=false: this row is being restored to serve as the *previous* snapshot
        // for the next diff, not as "a run happening right now" - whether the row was
        // itself originally a baseline run doesn't matter for that role.
        return new ValidationResult(latest.timestamp, latest.trigger, latest.scoreString,
                latest.hardScore, 0, latest.mediumScore, latest.softScore, counts, List.of(), false);
    }
}
