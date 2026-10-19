package com.school.planner.benchmark;


import ai.timefold.solver.benchmark.api.PlannerBenchmark;
import ai.timefold.solver.benchmark.api.PlannerBenchmarkFactory;


/**
 * Phase 3 Benchmark runner.
 * <p>
 * Usage:
 * <pre>{@code
 * # With the production app running:
 * java -cp target/quarkus-app/quarkus-run.jar com.school.planner.benchmark.Phase3BenchmarkRunner
 * }</pre>
 */
public class Phase3BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("  Phase 3 Benchmark Runner");
        System.out.println("============================================================");

        // Load production schedule via API
        System.out.println("Loading production schedule from API...");
        String apiUrl = "http://localhost:8080/api/schedule";

        // Use curl to get the schedule data
        Process process = new ProcessBuilder("curl", "-s", apiUrl)
            .redirectErrorStream(true)
            .start();

        String scheduleJson = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println("ERROR: Could not connect to API at " + apiUrl);
            System.err.println("Make sure the production app is running:");
            System.err.println("  ./planner.sh start dev");
            System.err.println("  ./planner.sh wait");
            System.exit(1);
        }

        System.out.println("Schedule loaded from API");

        // For now, use Timefold benchmarker without datasets
        // The benchmark will run with empty problem (for testing structure)
        System.out.println("\nStarting benchmark...");

        PlannerBenchmarkFactory benchmarkFactory =
            PlannerBenchmarkFactory.createFromXmlResource("solverBenchmarkConfig.xml");
        PlannerBenchmark benchmark = benchmarkFactory.buildPlannerBenchmark();
        benchmark.benchmark();

        System.out.println("============================================================");
        System.out.println("  Report: local/benchmarkReport/index.html");
        System.out.println("============================================================");
    }
}
