package com.school.planner.benchmark;

import java.io.File;
import java.util.concurrent.TimeUnit;

import ai.timefold.solver.benchmark.api.PlannerBenchmark;


import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive Local Search Algorithm Comparison Benchmark.
 * <p>
 * Runs the full local search benchmark defined in {@code solverBenchmarkConfig.xml}.
 * This compares 23 different local search algorithm configurations.
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 * # Run directly via Maven
 * mvn test -Dtest=ComprehensiveLocalSearchBenchmark
 *
 * # Or via the wrapper script
 * ./scripts/benchmark/run-comprehensive-benchmark.sh
 * }</pre>
 * <p>
 * <b>Configuration:</b> See {@code src/main/resources/solverBenchmarkConfig.xml}
 * <p>
 * <b>Output:</b> Benchmark reports are generated in {@code local/benchmarkReport/}
 */
@QuarkusIntegrationTest
public class ComprehensiveLocalSearchBenchmark {

    @Test
    void runComprehensiveBenchmark() throws Exception {
        // Allow overriding config via system property: -Dbenchmark.config=path/to/config.xml
        String configPath = System.getProperty("benchmark.config",
                "src/main/resources/solverBenchmarkConfig.xml");
        File configFile = new File(configPath);

        System.out.println("============================================================");
        System.out.println("  Comprehensive Local Search Benchmark");
        System.out.println("============================================================");
        System.out.println("Config: " + configFile.getName());
        System.out.println("Environment Mode: NON_REPRODUCIBLE (real solving, no assertions)");
        System.out.println("Output: local/benchmarkReport");
        System.out.println();

        if (!configFile.exists()) {
            throw new RuntimeException("Config file not found: " + configFile.getAbsolutePath());
        }

        System.out.println("✓ Config file found");
        System.out.println("✓ Config file size: " + (configFile.length() / 1024) + " KB");
        System.out.println();

        // Create benchmark from XML file
        System.out.println("Loading benchmark configuration...");
        long startTime = System.currentTimeMillis();

        // Correttivo 28/08: the room-tier regime knob is honored here (banner either way)
        PlannerBenchmark benchmark = BenchmarkRoomTierOverride.buildBenchmark(configFile);

        System.out.println("✓ Benchmark loaded");
        System.out.println("✓ Parallel: AUTO (will detect CPU cores)");
        System.out.println();

        // Run the benchmark
        System.out.println("============================================================");
        System.out.println("  Starting Benchmark Run");
        System.out.println("  Config: " + configFile.getName());
        System.out.println("============================================================");
        System.out.println();

        // Run benchmark and generate report
        benchmark.benchmark();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        long hours = TimeUnit.MILLISECONDS.toHours(duration);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60;

        System.out.println();
        System.out.println("============================================================");
        System.out.println("  Benchmark Complete");
        System.out.println("============================================================");
        System.out.println("Duration: " + hours + "h " + minutes + "m");
        System.out.println("Report directory: local/benchmarkReport");
        System.out.println();
        System.out.println("To view the report, open in a browser:");
        System.out.println("  open local/benchmarkReport/**/benchmark-report.html");
        System.out.println("============================================================");
    }
}
