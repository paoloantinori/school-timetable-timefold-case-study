package com.school.planner.benchmark;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.benchmark.api.PlannerBenchmark;


import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * A/B Test: DayClusteredConstructionPhase (Phase 1) vs Standard CH only.
 *
 * <p>Compares two solver configurations:
 * <ul>
 *   <li><b>A-Phase1-ON</b>: Current production config with custom DayClusteredConstructionPhase</li>
 *   <li><b>B-Phase1-OFF</b>: Standard FIRST_FIT_DECREASING CH with VRP filtering only</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * # Run via Maven
 * mvn test -Dtest=Phase1ComparisonBenchmark
 * }</pre>
 *
 * <p><b>Expected runtime:</b> ~30 min (2 configs)
 * <p><b>Output:</b> {@code local/benchmarkReport-phase1-test/}
 */
@QuarkusIntegrationTest
public class Phase1ComparisonBenchmark {

    @Test
    void runPhase1ComparisonBenchmark() throws Exception {
        System.out.println("============================================================");
        System.out.println("  A/B Test: Phase 1 (DayClustered) vs Standard CH");
        System.out.println("============================================================");
        System.out.println("Config: solverBenchmarkConfigPhase1Test.xml");
        System.out.println("Configs: 2 (Phase1-ON vs Phase1-OFF)");
        System.out.println("Environment Mode: NON_REPRODUCIBLE");
        System.out.println("Output: local/benchmarkReport-phase1-test/");
        System.out.println();

        File configFile = new File("src/main/resources/solverBenchmarkConfigPhase1Test.xml");
        if (!configFile.exists()) {
            throw new RuntimeException("Config file not found: " + configFile.getAbsolutePath());
        }

        System.out.println("Config file size: " + (configFile.length() / 1024) + " KB");
        System.out.println();

        long startTime = System.currentTimeMillis();

        // Correttivo 28/08: the room-tier regime knob is honored here (banner either way)
        PlannerBenchmark benchmark = BenchmarkRoomTierOverride.buildBenchmark(configFile);

        System.out.println("============================================================");
        System.out.println("  Starting Benchmark Run");
        System.out.println("  Total solvers: 2");
        System.out.println("  Est. duration: 30 minutes");
        System.out.println("============================================================");
        System.out.println();

        benchmark.benchmark();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60;

        System.out.println();
        System.out.println("============================================================");
        System.out.println("  Benchmark Complete");
        System.out.println("============================================================");
        System.out.println("Duration: " + minutes + "m " + seconds + "s");
        System.out.println("Report directory: local/benchmarkReport-phase1-test/");
        System.out.println();
        System.out.println("To view the report, open in a browser:");
        System.out.println("  open local/benchmarkReport-phase1-test/index.html");
        System.out.println("============================================================");
    }
}
