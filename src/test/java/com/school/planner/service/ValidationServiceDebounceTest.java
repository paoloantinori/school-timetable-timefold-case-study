package com.school.planner.service;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import com.school.planner.domain.Lesson;
import com.school.planner.testsupport.LessonFixtures;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that a burst of {@link DataMutationEvent}s collapses into a single
 * debounced validation run (TASK-354), instead of one run per event.
 *
 * <p>Lives in the same package as {@link ValidationService} to reach its
 * package-private test hooks ({@code onMutation}, {@code pendingTaskCountForTest},
 * {@code validationRunCountForTest}) without widening the production API surface.
 */
@QuarkusTest
class ValidationServiceDebounceTest {

    @Inject
    ValidationService validationService;

    @Inject
    ScheduleService scheduleService;

    @Inject
    EntityManager em;

    /**
     * Code review B5: the debounced run must exercise the REAL {@code analyzeScore()}
     * path at least once, not just the "no scheduled lessons yet" fast path in
     * {@code ValidationService.validateNow} - that fast path never calls
     * {@code ScheduleService.analyzeScore()} at all, so a burst test running against an
     * empty schedule would collapse-and-fire without ever touching the score director.
     * Own fixture (rather than relying on fixture leftovers from other test classes)
     * so this holds regardless of test execution order.
     */
    @BeforeEach
    @Transactional
    void ensureAtLeastOneScheduledLesson() {
        if (Lesson.count("timeslot is not null") > 0) return;

        LessonFixtures.persistScheduledLesson("Debounce Test", LocalDate.of(2026, 9, 21));
        em.flush();
    }

    @Test
    void burstOfMutationsCollapsesIntoOneValidationRun() {
        // TASK-660: the score-restore epoch trigger legitimately schedules one
        // extra validation run in this SHARED Quarkus context (an earlier test's
        // heavy GET armed the async restore; its fire lands whenever the recompute
        // finishes, which on the full H2 seed takes far longer than any fixed
        // stability window). Await the restore's OWN quiescence flag (the public
        // TASK-667 signal) first, THEN a debounce window of silence, so the burst's
        // collapsed run is the only new one in the assertion window.
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> org.junit.jupiter.api.Assertions.assertFalse(
                        scheduleService.isScoreRestorePending(),
                        "async score restore still in flight: its validation fire would race the burst"));
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertEquals(0, validationService.pendingTaskCountForTest()));
        int stable = validationService.validationRunCountForTest();
        await().atMost(4, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertEquals(stable, validationService.validationRunCountForTest(),
                            "baseline taken during a validation run: quiesce harder");
                    assertEquals(0, validationService.pendingTaskCountForTest());
                });
        int runsBefore = validationService.validationRunCountForTest();

        // Simulate a burst of 5 mutation events arriving within the 2s debounce window.
        for (int i = 0; i < 5; i++) {
            validationService.onMutation(new DataMutationEvent("/api/holidays/" + i, "POST"));
        }

        // Immediately after the burst, exactly one debounced run should be pending;
        // each new event must have cancelled the previously scheduled one.
        assertEquals(1, validationService.pendingTaskCountForTest(),
                "A burst of events should collapse into a single pending debounced run");

        // Wait past the debounce window for the collapsed run to fire. Check both the run
        // count AND the pending-task flag in the same poll to avoid a race between the
        // counter (incremented at the start of validateNow()) and the ScheduledFuture
        // flipping to done (only once validateNow() fully returns).
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertEquals(runsBefore + 1, validationService.validationRunCountForTest(),
                            "Exactly one validation run should have executed after the debounce window");
                    assertEquals(0, validationService.pendingTaskCountForTest(),
                            "No task should remain pending once the debounced run has fired");
                });
    }
}
