package com.school.planner.benchmark;

import java.io.File;
import java.util.concurrent.TimeUnit;

import ai.timefold.solver.benchmark.api.PlannerBenchmark;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Minimal termination test: 1 solver, 5-minute hard limit.
 * Verifies Timefold respects termination with current constraint set (M22, M20, etc.).
 */
@QuarkusIntegrationTest
public class TerminationVerificationBenchmark {

    @Test
    void runTerminationTest() throws Exception {
        System.out.println("============================================================");
        System.out.println("  Termination Verification Test");
        System.out.println("  Expected max: 5 minutes per solver");
        System.out.println("============================================================");

        File configFile = new File("src/main/resources/solverBenchmarkConfigTerminationTest.xml");
        if (!configFile.exists()) {
            throw new RuntimeException("Config not found: " + configFile.getAbsolutePath());
        }

        long startTime = System.currentTimeMillis();

        // Correttivo 28/08: the room-tier regime knob is honored here (banner either way)
        PlannerBenchmark benchmark = BenchmarkRoomTierOverride.buildBenchmark(configFile);
        benchmark.benchmark();

        long duration = System.currentTimeMillis() - startTime;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60;

        System.out.println("============================================================");
        System.out.printf("  Completed in %dm %ds%n", minutes, seconds);
        System.out.println("  Expected: < 5 minutes (300s hard limit)");
        System.out.println("  PASS: " + (duration < 360_000) + " (with 60s margin)");
        System.out.println("============================================================");

        if (duration > 360_000) {
            throw new RuntimeException("Termination NOT respected! Took " + minutes + "m " + seconds
                    + "s, expected < 6 minutes");
        }
    }
}
