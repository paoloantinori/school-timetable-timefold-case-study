package com.school.planner.benchmark;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.benchmark.api.PlannerBenchmark;
import ai.timefold.solver.benchmark.api.PlannerBenchmarkFactory;
import ai.timefold.solver.benchmark.config.PlannerBenchmarkConfig;
import ai.timefold.solver.benchmark.config.SolverBenchmarkConfig;
import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.domain.SchoolSchedule;
import com.school.planner.solver.RoomTierOverrides;

/**
 * Correttivo 28/08 (maintainer via Fedora): arm the HISTORIC metaheuristic battery under the
 * clean room-tier regime, driven by the SAME knob as the staged cycle
 * ({@code planner.room-tier.override}, vocabulary and parsing shared through
 * {@link RoomTierOverrides#fromKnob}). The benchmarker's solver runs never pass through
 * {@code ScheduleService.withRoomTierOverrideIfKnobbed} (staged-cycle-only hook), so the
 * flag must be read HERE, on the benchmark path, and applied to the problem instance.
 *
 * <p>DECLARED DEVIATION (the harness cannot represent the staged cycle's scoping):
 * problem-level {@link ConstraintWeightOverrides} apply to EVERY phase of every arm,
 * including the room CH/LS phases, which therefore run with the six families silent too
 * (the timeslot-only scoping would require rewriting the historic configs, which the
 * mandate forbids). The ITC caution is unchanged by construction: M-TC and H-COPRESENCE
 * are not in {@link RoomTierOverrides}'s map, so they stay at full weight in every arm.
 *
 * <p>Mechanics (public API only, verified on the 2.3.0 sources): the config is parsed
 * with {@link PlannerBenchmarkConfig#createFromXmlFile}, the problem wiring is taken off
 * the parsed object ({@link #takeProblemWiringOff}: Timefold SUMS programmatic problems
 * with the XML's {@code <inputSolutionFile>} list and rejects a
 * {@code solutionFileIOClass} with an empty list, so both must go), the problem is
 * loaded with the same {@link SchoolScheduleSolutionFileIO} the file path would use,
 * given the overrides, and passed to {@link PlannerBenchmarkFactory#create}. The input
 * instance is safe to share across parallel arms: {@code Solver.solve} planning-clones
 * its input and never mutates it. Cosmetic: the armed report names the problem
 * {@code Problem_0} (programmatic instance) instead of the file name.
 */
public final class BenchmarkRoomTierOverride {

    /** Same literal as the staged cycle's knob (ScheduleService.withRoomTierOverrideIfKnobbed). */
    public static final String FLAG = "planner.room-tier.override";

    private BenchmarkRoomTierOverride() {
    }

    /**
     * Build the battery from {@code configFile}, honoring {@link #FLAG}. Unarmed: the
     * plain factory path (identical to the historic runs). Armed: the overridden problem
     * on the stripped config, with a loud banner either way (a silently inert knob is
     * the exact failure mode this hook exists to prevent).
     */
    public static PlannerBenchmark buildBenchmark(File configFile) {
        String knob = System.getProperty(FLAG, "none");
        ConstraintWeightOverrides<BendableScore> overrides = RoomTierOverrides.fromKnob(knob);
        if (overrides == null) {
            System.out.println("ROOM-TIER OVERRIDE (benchmark battery): " + knob
                    + " (full weights on every phase of every arm)");
            return PlannerBenchmarkFactory.createFromXmlFile(configFile).buildPlannerBenchmark();
        }
        PlannerBenchmarkConfig config = PlannerBenchmarkConfig.createFromXmlFile(configFile);
        File inputFile = takeProblemWiringOff(config);
        SchoolSchedule problem = new SchoolScheduleSolutionFileIO().read(inputFile);
        problem.setConstraintWeightOverrides(overrides);
        System.out.println("ROOM-TIER OVERRIDE (benchmark battery): " + knob.trim() + " ARMED"
                + " (six room families silent on EVERY phase of every arm: declared deviation, see"
                + " this class's javadoc; M-TC + H-COPRESENCE at full weight); problem=" + inputFile);
        return PlannerBenchmarkFactory.create(config).buildPlannerBenchmark(problem);
    }

    /**
     * Read the (single) declared input file off the PARSED config and neutralize the
     * XML's problem wiring, both in the inherited block and in any per-solver override
     * (the historic configs only use the inherited one; the sweep is defensive). The
     * input path itself comes from the JAXB parse: no hand-rolled tag parser to drift.
     * Package-private: the unit test guards the historic configs' input wiring.
     */
    static File takeProblemWiringOff(PlannerBenchmarkConfig config) {
        List<File> inputs = new ArrayList<>();
        neutralize(config.getInheritedSolverBenchmarkConfig(), inputs);
        if (config.getSolverBenchmarkConfigList() != null) {
            for (SolverBenchmarkConfig solverBenchmark : config.getSolverBenchmarkConfigList()) {
                neutralize(solverBenchmark, inputs);
            }
        }
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException(FLAG
                    + " armed but the config declares no <inputSolutionFile>: nothing to load the problem from");
        }
        if (inputs.size() > 1) {
            throw new IllegalArgumentException(FLAG + " armed but the config declares " + inputs.size()
                    + " inputSolutionFile(s): arm one problem per run or extend this helper");
        }
        File input = inputs.get(0);
        if (!input.exists()) {
            throw new IllegalArgumentException("inputSolutionFile does not exist: " + input.getAbsolutePath());
        }
        return input;
    }

    private static void neutralize(SolverBenchmarkConfig solverBenchmark, List<File> inputs) {
        if (solverBenchmark == null || solverBenchmark.getProblemBenchmarksConfig() == null) {
            return;
        }
        var problemBenchmarks = solverBenchmark.getProblemBenchmarksConfig();
        if (problemBenchmarks.getInputSolutionFileList() != null) {
            inputs.addAll(problemBenchmarks.getInputSolutionFileList());
        }
        problemBenchmarks.setInputSolutionFileList(null);
        problemBenchmarks.setSolutionFileIOClass(null);
    }
}
