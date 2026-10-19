package com.school.planner.benchmark;

import java.io.File;

import ai.timefold.solver.benchmark.api.PlannerBenchmark;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Metaheuristics Benchmark — Full 5-Phase Production Solver (PINNED INPUT).
 * <p>
 * Same 9 configs as {@link TabuVsVNDBenchmark} but uses pre-assigned (pinned)
 * lessons from HybridPhase1aService. The solver starts with lab rotations
 * already pinned, reducing the search space.
 * <p>
 * <b>Prepare:</b> {@code ./scripts/benchmark/prepare-pinned-benchmark.sh}
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 * mvn test -Dtest=TabuVsVNDPinnedBenchmark
 * }</pre>
 */
@QuarkusIntegrationTest
public class TabuVsVNDPinnedBenchmark {

    @Test
    void runTabuVsVNDPinned() throws Exception {
        System.out.println("============================================================");
        System.out.println("  Metaheuristics Benchmark — PINNED INPUT");
        System.out.println("============================================================");

        File configFile = new File("src/main/resources/solverBenchmarkConfigTabuVsVNDPinned.xml");
        if (!configFile.exists()) {
            throw new RuntimeException("Config file not found: " + configFile.getAbsolutePath());
        }

        System.out.println("Config: " + configFile.getName());
        System.out.println("Input:  local/data/schedule-production-pinned.json (pre-assigned)");
        System.out.println("Configs: 9 (Tabu-5, VND, Tabu-7, LA, SA-Tabu, LA+Tabu, VND-FI, VND-DR, VND-CP)");
        System.out.println("Est. duration: ~135 minutes");
        System.out.println();

        // Correttivo 28/08: the room-tier regime knob is honored here (banner either way)
        PlannerBenchmark benchmark = BenchmarkRoomTierOverride.buildBenchmark(configFile);

        System.out.println("Starting benchmark...");
        benchmark.benchmark();

        System.out.println();
        System.out.println("============================================================");
        System.out.println("  Benchmark Complete");
        System.out.println("  Report: local/benchmarkReportPinned");
        System.out.println("============================================================");
    }
}
