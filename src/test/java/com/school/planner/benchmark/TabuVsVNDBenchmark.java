package com.school.planner.benchmark;

import java.io.File;

import ai.timefold.solver.benchmark.api.PlannerBenchmark;


import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Metaheuristics Benchmark — Full 5-Phase Production Solver.
 * <p>
 * Compares 6 metaheuristic configurations using the complete 5-phase production
 * architecture with updated move selectors from TASK-289/290:
 * <ol>
 *   <li>Tabu-Entity-Size5 (production baseline)</li>
 *   <li>VND-HillClimbing (only improving moves)</li>
 *   <li>Tabu-Entity-Size7 (larger tabu list)</li>
 *   <li>Late-Acceptance (lateAcceptanceSize=400, acceptedCountLimit=1)</li>
 *   <li>SA-Tabu-Hybrid (SA exploration + Tabu memory)</li>
 *   <li>LA-Phase3-Tabu-Phase5 (multi-algorithm approach)</li>
 * </ol>
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 * mvn test -Dtest=TabuVsVNDBenchmark
 * }</pre>
 */
@QuarkusIntegrationTest
public class TabuVsVNDBenchmark {

    @Test
    void runTabuVsVND() throws Exception {
        System.out.println("============================================================");
        System.out.println("  Metaheuristics Benchmark — Full 5-Phase Production Solver");
        System.out.println("============================================================");

        File configFile = new File("src/main/resources/solverBenchmarkConfigTabuVsVND.xml");
        if (!configFile.exists()) {
            throw new RuntimeException("Config file not found: " + configFile.getAbsolutePath());
        }

        System.out.println("Config: " + configFile.getName());
        System.out.println("Configs: 9 (Tabu-5, VND, Tabu-7, LA, SA-Tabu, LA+Tabu, VND-FI, VND-DR, VND-CP)");
        System.out.println("Est. duration: ~135 minutes");
        System.out.println();

        // Correttivo 28/08: the room-tier regime knob is honored here (banner either way);
        // this class was the August vehicle of record for the TabuVsVND family
        PlannerBenchmark benchmark = BenchmarkRoomTierOverride.buildBenchmark(configFile);

        System.out.println("Starting benchmark...");
        benchmark.benchmark();

        System.out.println();
        System.out.println("============================================================");
        System.out.println("  Benchmark Complete");
        System.out.println("  Report: local/benchmarkReport");
        System.out.println("============================================================");
    }
}
