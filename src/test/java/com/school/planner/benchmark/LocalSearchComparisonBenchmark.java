package com.school.planner.benchmark;

import java.io.File;
import java.util.concurrent.TimeUnit;

import ai.timefold.solver.benchmark.api.PlannerBenchmark;


import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Local Search Algorithm Comparison Benchmark.
 * <p>
 * Runs the comprehensive local search benchmark defined in {@code solverBenchmarkConfig.xml}.
 * This compares 23 different local search algorithm configurations.
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 * # Run directly via Maven
 * mvn test -Dtest=LocalSearchComparisonBenchmark
 *
 * # Or via the wrapper script
 * ./scripts/benchmark/run-benchmarker.sh
 * }</pre>
 * <p>
 * <b>Configuration:</b> See {@code src/main/resources/solverBenchmarkConfig.xml}
 * <p>
 * <b>Output:</b> Benchmark reports are generated in {@code local/benchmarkReport/}
 */
@QuarkusIntegrationTest
public class LocalSearchComparisonBenchmark {

    @Test
    void runLocalSearchComparisonBenchmark() throws Exception {
        System.out.println("============================================================");
        System.out.println("  Local Search Algorithm Comparison Benchmark (TEST)");
        System.out.println("============================================================");
        System.out.println("Config: solverBenchmarkConfigTest.xml");
        System.out.println("Algorithms: 3 (Tabu, Simulated Annealing, Late Acceptance)");
        System.out.println("Environment Mode: NON_REPRODUCIBLE (real solving, no assertions)");
        System.out.println("Output: local/benchmarkReport");
        System.out.println();

        File configFile = new File("src/main/resources/solverBenchmarkConfigTest.xml");
        if (!configFile.exists()) {
            throw new RuntimeException("Config file not found: " + configFile.getAbsolutePath());
        }

        System.out.println("✓ Config file found");
        System.out.println("✓ Config file size: " + (configFile.length() / 1024) + " KB");
        System.out.println();

        // Create benchmark from XML file
        System.out.println("Loading benchmark configuration...");
        long startTime = System.currentTimeMillis();

        // Use the benchmark factory to load benchmark configuration XML
        // Note: createFromXmlFile() is for benchmark configs, not solver configs
        // Correttivo 28/08: the room-tier regime knob is honored here (banner either way)
        PlannerBenchmark benchmark = BenchmarkRoomTierOverride.buildBenchmark(configFile);

        System.out.println("✓ Benchmark loaded");
        System.out.println("✓ Parallel: AUTO (will detect CPU cores)");
        System.out.println();

        // Run the benchmark
        System.out.println("============================================================");
        System.out.println("  Starting Benchmark Run");
        System.out.println("  Total solvers: 3");
        System.out.println("  Per-solver timeout: 15m (3m no progress)");
        System.out.println("  Est. duration: 45-60 minutes");
        System.out.println("============================================================");
        System.out.println();

        // Run benchmark and generate report
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
        System.out.println("Report directory: local/benchmarkReport");
        System.out.println();
        System.out.println("To view the report, open in a browser:");
        System.out.println("  open local/benchmarkReport/index.html");
        System.out.println("============================================================");
    }
}
