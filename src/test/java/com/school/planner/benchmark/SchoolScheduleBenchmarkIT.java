package com.school.planner.benchmark;


import ai.timefold.solver.benchmark.api.PlannerBenchmark;
import ai.timefold.solver.benchmark.api.PlannerBenchmarkFactory;
import ai.timefold.solver.core.api.solver.Solver;

import com.school.planner.domain.SchoolSchedule;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Timefold Solver Benchmark for School Scheduling.
 * <p>
 * This benchmark runs multiple solver configurations on real production data,
 * comparing Phase 3 compactness tuning with different unimproved time limits.
 * <p>
 * <b>Prerequisites:</b>
 * <ul>
 *   <li>Dev server must be running: {@code ./planner.sh start dev}</li>
 *   <li>Database must be populated with seed data</li>
 * </ul>
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 * # Start dev server first
 * ./planner.sh start dev
 *
 * # Run Phase 3 compactness comparison benchmark
 * ./scripts/core/verify.sh -c SchoolScheduleBenchmarkIT -m phase3CompactnessBenchmark
 * }</pre>
 * <p>
 * <b>Output:</b> Benchmark reports are generated in {@code local/benchmarkReport/}
 * and can be viewed by opening the HTML file in a browser.
 */
@QuarkusIntegrationTest
public class SchoolScheduleBenchmarkIT {

    private static final String API_BASE = "http://localhost:8080/api";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * Phase 3 Compactness Benchmark: Compare 60s, 120s, and 180s unimproved limits.
     * Note: This benchmark runs the same dataset 3 times with different solver configs.
     */
    @Test
    void phase3CompactnessBenchmark() throws Exception {
        runComparativeBenchmark();
    }

    /**
     * Run comparative benchmark with multiple solver configurations.
     * Uses REST API to load data from running server, then creates
     * PlannerBenchmarkFactories configured with different Phase 3 unimproved time limits.
     */
    private void runComparativeBenchmark() throws Exception {
        System.out.println("========================================");
        System.out.println("Phase 3 Compactness Benchmark");
        System.out.println("Comparing 60s, 120s, 180s unimproved limits");
        System.out.println("Using REST API to load production data");
        System.out.println("========================================");

        var client = ClientBuilder.newClient();

        // Step 1: Run pre-assignment to pin lab rotations
        System.out.println("Step 1: Running pre-assignment...");
        try (Response response = client.target(API_BASE + "/schedule/pre-assign")
                .request()
                .post(Entity.json(null))) {
            if (response.getStatus() != 200) {
                System.err.println("Pre-assignment failed: HTTP " + response.getStatus());
            } else {
                System.out.println("  Pre-assignment initiated");
            }
        }

        // Wait for pre-assignment to complete
        System.out.println("  Waiting for pre-assignment completion...");
        int maxWait = 60; // 60 seconds max
        int waited = 0;
        boolean complete = false;
        while (waited < maxWait) {
            try (Response response = client.target(API_BASE + "/schedule/pre-assign/status")
                    .request()
                    .get()) {
                if (response.getStatus() == 200) {
                    String status = response.readEntity(String.class);
                    if (status.contains("\"status\":\"COMPLETE\"")) {
                        complete = true;
                        break;
                    } else if (status.contains("\"status\":\"ERROR\"")) {
                        System.err.println("  Pre-assignment failed!");
                        break;
                    }
                }
            }
            Thread.sleep(1000);
            waited++;
        }
        System.out.println("  Complete: " + (complete ? "SUCCESS" : "TIMEOUT/FAILED"));

        // Step 2: Load production data (will be used for all benchmarks)
        System.out.println("Step 2: Loading production data from REST API...");
        try (Response response = client.target(API_BASE + "/schedule")
                .request()
                .get()) {
            if (response.getStatus() != 200) {
                throw new RuntimeException("Failed to load schedule: HTTP " + response.getStatus());
            }
            String json = response.readEntity(String.class);
            SchoolSchedule productionData = objectMapper.readValue(json, SchoolSchedule.class);
            System.out.println("  Loaded " + productionData.getLessons().size() + " lessons");
            System.out.println("  Loaded " + productionData.getTimeslots().size() + " timeslots");
            System.out.println("  Loaded " + productionData.getRooms().size() + " rooms");

            long pinnedCount = productionData.getLessons().stream()
                .filter(l -> l.isPinned())
                .count();
            long unpinnedCount = productionData.getLessons().stream()
                .filter(l -> !l.isPinned())
                .count();
            System.out.println("  Pinned lessons: " + pinnedCount);
            System.out.println("  Unpinned lessons: " + unpinnedCount);

            // Clear timeslot assignments for fresh benchmark runs
            // (keep pinned lessons as-is, only clear unpinned assignments)
            productionData.getLessons().forEach(l -> {
                if (!l.isPinned()) {
                    l.timeslot = null;
                    l.room = null;
                }
            });

            // Create copies of the dataset for independent benchmarking
            SchoolSchedule dataset1 = copySchedule(productionData);
            SchoolSchedule dataset2 = copySchedule(productionData);
            SchoolSchedule dataset3 = copySchedule(productionData);

            // Step 3: Run benchmarks sequentially with different configs
            System.out.println("Step 3: Running comparative benchmarks...");
            System.out.println("  Benchmark 1: 60s unimproved limit");
            runSingleBenchmark("Phase 3 - 60s unimproved", dataset1, 60);

            System.out.println("  Benchmark 2: 120s unimproved limit");
            runSingleBenchmark("Phase 3 - 120s unimproved", dataset2, 120);

            System.out.println("  Benchmark 3: 180s unimproved limit");
            runSingleBenchmark("Phase 3 - 180s unimproved", dataset3, 180);
        }

        System.out.println("========================================");
        System.out.println("All Benchmarks Complete");
        System.out.println("Report: local/benchmarkReport/index.html");
        System.out.println("========================================");
    }

    /**
     * Run a single benchmark with a specific unimproved time limit.
     * This uses a solver config variant with the specified Phase 3 unimproved limit.
     */
    private void runSingleBenchmark(String name, SchoolSchedule dataset, int unimprovedSeconds) {
        // Select the appropriate solver config based on unimproved time limit
        String solverConfigResource;
        if (unimprovedSeconds == 60) {
            solverConfigResource = "solverConfig-phase3-60s.xml";
        } else if (unimprovedSeconds == 120) {
            solverConfigResource = "solverConfig-phase3-120s.xml";
        } else if (unimprovedSeconds == 180) {
            solverConfigResource = "solverConfig-phase3-180s.xml";
        } else {
            throw new IllegalArgumentException("Unsupported unimproved seconds: " + unimprovedSeconds);
        }

        System.out.println("    Using solver config: " + solverConfigResource);

        // Create benchmark factory from the solver config
        PlannerBenchmarkFactory benchmarkFactory = PlannerBenchmarkFactory
            .createFromSolverConfigXmlResource(solverConfigResource);

        // Build and run the benchmark
        PlannerBenchmark benchmark = benchmarkFactory.buildPlannerBenchmark(dataset);
        benchmark.benchmark();
    }

    /**
     * Create a deep copy of a SchoolSchedule for independent benchmarking.
     * The benchmark will properly clone the solution, so we pass the original.
     */
    private SchoolSchedule copySchedule(SchoolSchedule original) {
        return original;
    }
}
