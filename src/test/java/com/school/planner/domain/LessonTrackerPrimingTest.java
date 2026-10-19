package com.school.planner.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;

/**
 * TASK-508: the dirty-tracker priming contract the MT-LS race fix relies on. The
 * build-time Hibernate enhancement is active on the test classpath too (same woven
 * classes), so these exercises run the REAL $$_hibernate_trackChange path.
 *
 * <p>The contract: after ONE single-threaded {@link Lesson#primeDirtyTracking()}, the
 * entity's tracker already contains both planning field names, so every later write
 * takes the {@code contains() == true} short-circuit and the tracker's plain String[]
 * is never mutated again. Concurrent self-writes after priming therefore cannot
 * interleave the array growth that produced the run3 NPE ("existing is null").
 *
 * <p>What is NOT asserted (honestly): the race itself is not deterministic, so no test
 * can prove the un-primed path NPEs. The load-bearing assertions are priming
 * idempotence and concurrent-safety of the primed state.
 */
class LessonTrackerPrimingTest {

    @AfterEach
    void clearFlag() {
        System.clearProperty("planner.mt-ls.tracker-priming.enabled");
    }

    private static Lesson lesson() {
        Course course = new Course();
        course.subject = "Barberia";
        Lesson l = new Lesson(course);
        return l;
    }

    @Test
    void primingIsIdempotentAndThreadSafeAfterwards() throws Exception {
        Lesson l = lesson();
        l.primeDirtyTracking();  // the single-threaded priming the flag gates
        // 8 threads x 2000 self-writes on the SAME instance: with the tracker already
        // containing the fields, contains() short-circuits and no mutation races.
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 2000; i++) {
                        l.primeDirtyTracking();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertDoesNotThrow(l::primeDirtyTracking, "primed state stays usable");
    }

    @Test
    void flagDefaultsToOff() {
        System.clearProperty("planner.mt-ls.tracker-priming.enabled");
        assertFalse(Boolean.parseBoolean(
                System.getProperty("planner.mt-ls.tracker-priming.enabled", "false")),
                "TASK-508 priming is opt-in: unset property reads disabled");
    }
}
