package com.school.planner.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;

import io.quarkus.runtime.StartupEvent;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import com.school.planner.rest.dto.ValidationResult;
import com.school.planner.util.ConstraintIds;

/**
 * Solution-legality watchdog (TASK-354).
 *
 * <p>Re-scores the CURRENT schedule via {@link ScheduleService#analyzeScore()} — the
 * same open-source ScoreDirector-based replacement for Timefold's Enterprise-only
 * {@code SolutionManager.analyze()} that the existing analysis endpoints already use —
 * WITHOUT running the solver, and diffs the per-constraint match counts against the
 * previous snapshot to surface newly-illegal violations.
 *
 * <p>Triggered by a debounced {@link DataMutationEvent} (fired by
 * {@code com.school.planner.rest.DataMutationTrackingFilter} after any mutating CRUD
 * call on constraint-relevant entities) so a burst of edits collapses into a single
 * validation run. Validation is skipped while the solver is actively running (its own
 * best-solution callbacks already keep the score current) and when the schedule has
 * no assigned lessons yet (nothing to validate).
 */
@ApplicationScoped
public class ValidationService {

    private static final Logger LOG = Logger.getLogger(ValidationService.class);

    private static final long DEBOUNCE_MILLIS = 2000;

    @Inject
    ScheduleService scheduleService;

    @Inject
    ValidationSnapshotRepository repository;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "validation-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<ScheduledFuture<?>> pendingTask = new AtomicReference<>();
    private volatile String lastTrigger = "unknown";
    /** True while the pending debounced run was armed by an INTERNAL (non-HTTP) trigger. */
    private final AtomicBoolean pendingInternal = new AtomicBoolean(false);
    /** Retry counter for the INTERNAL busy-retry loop (reset on every fresh schedule). */
    private final AtomicInteger internalRetries = new AtomicInteger(0);
    /** 6s apart, 600 retries: an INTERNAL validation survives ~1h of continuous solving. */
    private static final long INTERNAL_RETRY_MILLIS = 6000;
    private static final int MAX_INTERNAL_RETRIES = 600;
    private volatile ValidationResult latest;

    /**
     * Counts every {@link #validateNow(String)} invocation, including ones that hit a
     * skip path (solver running, no scheduled lessons) — it measures "did the debounced
     * timer fire", not "did a full rescore happen". Exposed for the debounce test to
     * verify a burst of events collapses into exactly one invocation.
     */
    private final AtomicInteger validationRunCount = new AtomicInteger(0);

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** Restore the latest persisted snapshot so the diff survives an app restart. */
    void onStartup(@Observes StartupEvent event) {
        try {
            ValidationResult persisted = repository.loadLatestFromDb();
            if (persisted != null) {
                latest = persisted;
                LOG.infof("ValidationService: restored latest snapshot from DB (trigger=%s, score=%s)",
                        persisted.trigger(), persisted.score());
            }
        } catch (Exception e) {
            LOG.warn("ValidationService: failed to restore snapshot from DB on startup", e);
        }
    }

    /** Entry point for {@link DataMutationEvent}: schedules a debounced re-validation. */
    void onMutation(@Observes DataMutationEvent event) {
        // "INTERNAL" marks non-HTTP triggers (e.g. ScheduleService's solve-completion
        // hook) whose path IS the human-readable trigger already; "METHOD path" framing
        // only makes sense for the HTTP-mutation case fired by DataMutationTrackingFilter.
        String trigger = "INTERNAL".equals(event.method()) ? event.path() : event.method() + " " + event.path();
        pendingInternal.set("INTERNAL".equals(event.method()));
        scheduleDebounced(trigger);
    }

    private void scheduleDebounced(String trigger) {
        lastTrigger = trigger;
        internalRetries.set(0);
        ScheduledFuture<?> task = executor.schedule(this::debouncedRun, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previous = pendingTask.getAndSet(task);
        if (previous != null) {
            // Collapse bursts: cancel the previously scheduled run, only the last one fires.
            previous.cancel(false);
        }
    }

    private void debouncedRun() {
        try {
            // TASK-660/354 (live 26/09): an INTERNAL trigger marks a state-epoch
            // boundary (cycle delivered, score restored). If the run lands while
            // the solver is busy, validateNow would SKIP it and the delivery's
            // snapshot would be silently dropped (the public site served a
            // two-day-old -8hard intermediate over a 0-hard delivery for exactly
            // this reason: every fire from the staged path landed mid-cycle).
            // INTERNAL triggers retry until quiescent instead of skipping.
            if (pendingInternal.get() && scheduleService.isBusy()) {
                if (internalRetries.incrementAndGet() <= MAX_INTERNAL_RETRIES) {
                    // Register the retry in pendingTask like scheduleDebounced does: an
                    // invisible retry (scheduled straight on the executor) breaks BOTH
                    // consumers of that reference - the quiescence test hooks AND the
                    // collapse semantics (a mutation arriving during the retry wait
                    // must cancel it, and it fired anyway in the 26/09 suite, racing
                    // the debounce test's assertion window).
                    ScheduledFuture<?> task = executor.schedule(this::debouncedRun, INTERNAL_RETRY_MILLIS, TimeUnit.MILLISECONDS);
                    ScheduledFuture<?> previous = pendingTask.getAndSet(task);
                    if (previous != null) {
                        previous.cancel(false);
                    }
                    LOG.infof("ValidationService: solver busy (trigger=%s), validation rescheduled (%d/%d)",
                            lastTrigger, internalRetries.get(), MAX_INTERNAL_RETRIES);
                    return;
                }
                LOG.warnf("ValidationService: INTERNAL validation (trigger=%s) gave up after %d retries",
                        lastTrigger, MAX_INTERNAL_RETRIES);
            }
            validateNow(lastTrigger);
        } catch (Exception e) {
            LOG.error("ValidationService: debounced validation failed", e);
        }
    }

    /**
     * Force an immediate validation, cancelling any pending debounce. Used by the REST
     * endpoint and tests.
     *
     * <p><b>Back-to-back calls (code review B4):</b> calling this twice in quick
     * succession runs {@link #validateNow(String)} twice, and the second call's diff is
     * computed against the first call's result (not against whatever was {@code latest}
     * before either ran) — that's correct, not a bug. Each run's job is "what changed
     * since the immediately preceding snapshot", and the preceding snapshot for the
     * second call genuinely is the first call's result, not some earlier state it never
     * saw. This is unrelated to the {@link #DEBOUNCE_MILLIS debounce} window, which only
     * collapses a *burst of pending* runs into one — it doesn't apply here since both
     * calls bypass the pending debounce entirely.
     */
    public ValidationResult runNow(String trigger) {
        ScheduledFuture<?> previous = pendingTask.getAndSet(null);
        if (previous != null) {
            previous.cancel(false);
        }
        return validateNow(trigger);
    }

    public ValidationResult getLatest() {
        return latest;
    }

    /**
     * Number of currently-pending debounced runs (0 or 1). Exposed for tests to
     * verify that a burst of mutation events collapses into a single scheduled run.
     */
    int pendingTaskCountForTest() {
        ScheduledFuture<?> task = pendingTask.get();
        return (task != null && !task.isDone()) ? 1 : 0;
    }

    /** How many times {@link #validateNow(String)} was invoked. Exposed for the debounce test. */
    int validationRunCountForTest() {
        return validationRunCount.get();
    }

    /**
     * Clears the in-memory {@code latest} snapshot so the next {@link #runNow(String)}
     * is deterministically a baseline run, regardless of what earlier tests in the same
     * suite may have validated (this bean is an {@code ApplicationScoped} singleton
     * shared across the whole test JVM). Exposed for {@code ValidationServiceBaselineTest}
     * (code review B1) — without it, "fresh state -> baseline" can't be asserted
     * deterministically since other tests' CRUD mutations may have already triggered a
     * background debounced validation before this test runs.
     */
    void resetLatestForTest() {
        latest = null;
    }

    private synchronized ValidationResult validateNow(String trigger) {
        validationRunCount.incrementAndGet();
        if (scheduleService.isBusy()) {
            LOG.infof("ValidationService: skipping validation (trigger=%s) - solver is busy", trigger);
            return latest;
        }

        if (!repository.hasScheduledLessons()) {
            LOG.infof("ValidationService: skipping validation (trigger=%s) - no scheduled lessons yet", trigger);
            // Deliberately NOT assigned to `latest` (code review B1): this is a
            // transient "nothing to validate" stub, not a real snapshot. If it were
            // stored as `latest`, the next real run (once lessons exist) would see a
            // non-null `latest` with an empty perConstraint list and be treated as a
            // normal diff against zero counts everywhere - flooding newlyViolated with
            // every pre-existing match, exactly the bug this fix targets. Leaving
            // `latest` untouched preserves whatever real baseline (or null) came before.
            return new ValidationResult(Instant.now(), trigger,
                    ScoreCompat.ZERO.toString(), 0, 0, 0, 0, List.of(), List.of(), false);
        }

        ScheduleService.ConstraintMatchSnapshot snapshot = scheduleService.analyzeScore();

        List<ValidationResult.ConstraintCount> counts = new ArrayList<>();
        snapshot.constraintMap().forEach((ref, total) -> {
            long matchCount = total.getConstraintMatchCount();
            if (matchCount == 0) return;
            BendableScore constraintScore = (BendableScore) total.getScore();
            counts.add(new ValidationResult.ConstraintCount(
                    ConstraintIds.canonicalId(ref.id()), matchCount,
                    ScoreCompat.hardOf(constraintScore), ScoreCompat.quasiOf(constraintScore),
                    ScoreCompat.mediumOf(constraintScore), ScoreCompat.softOf(constraintScore)));
        });
        counts.sort(Comparator.comparing(ValidationResult.ConstraintCount::constraintId));

        // code review B1: no previous snapshot means there is nothing legitimate to
        // diff against - this run establishes the baseline instead of reporting every
        // pre-existing match as "newly" violated.
        boolean baseline = latest == null;

        List<ValidationResult.ViolationDelta> newlyViolated = new ArrayList<>();
        if (!baseline) {
            Map<String, Long> previousCounts = latest.perConstraint().stream()
                    .collect(Collectors.toMap(ValidationResult.ConstraintCount::constraintId,
                            ValidationResult.ConstraintCount::matchCount));
            for (ValidationResult.ConstraintCount count : counts) {
                long previousCount = previousCounts.getOrDefault(count.constraintId(), 0L);
                if (count.matchCount() > previousCount) {
                    newlyViolated.add(new ValidationResult.ViolationDelta(
                            count.constraintId(), previousCount, count.matchCount(),
                            count.matchCount() - previousCount, count.hard()));
                }
            }
        }

        BendableScore score = snapshot.score();
        ValidationResult result = new ValidationResult(Instant.now(), trigger, score.toString(),
                ScoreCompat.hardOf(score), ScoreCompat.quasiOf(score),
                ScoreCompat.mediumOf(score), ScoreCompat.softOf(score), counts, newlyViolated, baseline);

        latest = result;
        repository.persist(result);

        LOG.infof("ValidationService: validated (trigger=%s, score=%s, newlyViolated=%d, baseline=%s)",
                trigger, score, newlyViolated.size(), baseline);

        return result;
    }
}
