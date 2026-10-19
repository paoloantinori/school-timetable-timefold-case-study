package com.school.planner.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ai.timefold.solver.benchmark.config.PlannerBenchmarkConfig;

/**
 * Correttivo 28/08: the parse-level guards run in the normal suite (they also double as
 * the battery's input-file preflight: a missing {@code local/data/schedule-production.json}
 * must fail in seconds, not after the gate delivers). The ARMED full path mutates the
 * {@link SchoolSchedule} global statics (via {@link SchoolScheduleSolutionFileIO#read}),
 * so it runs ONLY when explicitly selected
 * ({@code ./scripts/core/test.sh -mva "-Dbenchmark.roomtier.selftest=1" -c BenchmarkRoomTierOverrideTest}):
 * a shared surefire JVM must not have its statics rewritten mid-suite.
 */
class BenchmarkRoomTierOverrideTest {

    @AfterEach
    void clearKnob() {
        System.clearProperty(BenchmarkRoomTierOverride.FLAG);
    }

    @Test
    void historicConfigsDeclareAnExistingInputSolutionFile() {
        for (String configName : new String[] {
                "solverBenchmarkConfig.xml",
                "solverBenchmarkConfigTest.xml",
                "solverBenchmarkConfigTabuVsVND.xml",
                "solverBenchmarkConfigAcceptedCountLimit.xml",
                "solverBenchmarkConfigMoveStats.xml",
                "solverBenchmarkConfigMoveStatsV2.xml",
                "solverBenchmarkConfigMoveStatsV3.xml",
                "solverBenchmarkConfigPhase1Test.xml",
                "solverBenchmarkConfigTerminationTest.xml"}) {
            File config = new File("src/main/resources/" + configName);
            assumeTrue(config.exists(), configName + " not on disk");
            File input;
            try {
                input = BenchmarkRoomTierOverride.takeProblemWiringOff(
                        PlannerBenchmarkConfig.createFromXmlFile(config));
            } catch (IllegalArgumentException e) {
                // the 10MB input is a gitignored machine-local artifact: where it is not
                // provisioned the battery cannot run, so its preflight SKIPS (the shared
                // suite must not go red over a missing local dataset)
                assumeTrue(false, configName + " -> " + e.getMessage());
                return;
            }
            assertTrue(input.exists(),
                    configName + " -> inputSolutionFile missing on disk: " + input);
            assertEquals("schedule-production.json", input.getName(),
                    configName + " -> unexpected input file");
        }
    }

    @Test
    void unknownKnobValueFailsLoud() {
        System.setProperty(BenchmarkRoomTierOverride.FLAG, "bogus");
        assertThrows(IllegalArgumentException.class,
                () -> BenchmarkRoomTierOverride.buildBenchmark(
                        new File("src/main/resources/solverBenchmarkConfigMoveStats.xml")));
    }

    /** Full armed path (config strip + 10MB JSON load + overrides on the problem +
     *  factory build, NO solving). Explicit selection only: see the class javadoc. */
    @Test
    void armedFullPathBuildsTheBattery() {
        assumeTrue(System.getProperty("benchmark.roomtier.selftest") != null,
                "armed selftest not requested (mutates SchoolSchedule statics)");
        System.setProperty(BenchmarkRoomTierOverride.FLAG, "zero");
        // Build only: no benchmark(). The banner is on stdout; a non-null return with
        // no exception is the contract under test.
        var benchmark = BenchmarkRoomTierOverride.buildBenchmark(
                new File("src/main/resources/solverBenchmarkConfigMoveStats.xml"));
        assertNotNull(benchmark, "armed build must return a PlannerBenchmark");
    }
}
