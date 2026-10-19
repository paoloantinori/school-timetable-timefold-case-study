package com.school.planner.solver.multithreaded;

import java.lang.reflect.Field;
import java.util.List;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import ai.timefold.solver.core.impl.localsearch.decider.LocalSearchDecider;
import ai.timefold.solver.core.impl.phase.Phase;
import ai.timefold.solver.core.impl.solver.AbstractSolver;
import ai.timefold.solver.core.impl.solver.DefaultSolver;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;

/**
 * Wires {@link MultiThreadedLocalSearchDecider} into the LS phases of an ALREADY-BUILT solver
 * (the Quarkus-initialized one, whose solutionClass/entityClass come from domain discovery).
 *
 * <p>RATIFIED DEFAULT ON (maintainer, 2026-08-14), opt-out {@code -Dplanner.multithreaded-ls.enabled=false}.
 *
 * <p><b>Why reflection, and what exactly is fragile.</b> The swap replaces the {@code decider}
 * field of every {@code DefaultLocalSearchPhase} on a built solver. Timefold (2.3.0 through 2.7.0) offers NO
 * public seam for this, verified against the shipped jar:
 * <ul>
 *   <li>{@code DefaultLocalSearchPhase.decider} is {@code protected final}, no setter;</li>
 *   <li>{@code DefaultLocalSearchPhaseFactory.buildDecider(...)} is private, not overridable;</li>
 *   <li>{@code LocalSearchPhaseConfig} exposes acceptor/forager/moveSelector configs only,
 *       no custom-decoder hook;</li>
 *   <li>{@code AbstractSolver.phaseList} is {@code protected final} (read-only via getter).</li>
 * </ul>
 * The clean alternative (rebuild each phase ourselves) means replicating the factory's private
 * construction tree (move selectors, termination, assertion regime) out of tree: more fragile
 * than the field swap, not less. So the reflection stays, PINNED to Timefold impl internals (verified 2.3.0 through 2.7.0)
 * exactly like {@link MultiThreadedLocalSearchDecider}: a Timefold upgrade must re-check
 * {@code DefaultLocalSearchPhase.decider} (field name, {@code final}ness) and the decider
 * constructor signature. {@link #wireMultiThreaded} fails fast with IllegalStateException if
 * the field disappears or becomes inaccessible, so an upgrade breaks loudly at wiring time,
 * not silently mid-solve.
 */
public class MultiThreadedLsWiring {

    /**
     * The reflected swap target, resolved PER CALL (not in a static initializer: a static-init
     * failure would surface later uses as bare NoClassDefFoundError, burying the curated
     * message below. Per-call lookup is one reflection hit per solver build, free).
     */
    private static Field deciderField() {
        try {
            Field f = DefaultLocalSearchPhase.class.getDeclaredField("decider");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Timefold internals changed? DefaultLocalSearchPhase"
                    + ".decider no longer exists (pinned to the verified internals line (2.3.0 through 2.7.0, see class javadoc))", e);
        }
    }

    /**
     * Swap every LS decider of an ALREADY-BUILT solver with a
     * MultiThreadedLocalSearchDecider (which derives its own backpressure buffer).
     *
     * @return how many LS phases were swapped (0 for a construction-only config, where
     *         there is no LS phase to swap; the CALLER decides whether that is expected).
     */
    @SuppressWarnings("unchecked")
    public static int wireMultiThreaded(
            Solver<SchoolSchedule> existingSolver, int moveThreadCount) {
        if (!(existingSolver instanceof AbstractSolver<SchoolSchedule> abstractSolver)) {
            throw new IllegalStateException("Solver is not an AbstractSolver: " + existingSolver.getClass());
        }

        int swapped = 0;
        try {
            Field deciderField = deciderField();
            List<Phase<SchoolSchedule>> phases = abstractSolver.getPhaseList();

            for (Phase<SchoolSchedule> phase : phases) {
                if (phase instanceof DefaultLocalSearchPhase<SchoolSchedule> lsPhase) {
                    LocalSearchDecider<SchoolSchedule> original =
                            (LocalSearchDecider<SchoolSchedule>) deciderField.get(lsPhase);

                    MultiThreadedLocalSearchDecider<SchoolSchedule> mtDecider =
                            new MultiThreadedLocalSearchDecider<>(
                                    "",
                                    (ai.timefold.solver.core.impl.solver.termination.PhaseTermination<SchoolSchedule>)
                                            original.getTermination(),
                                    original.getMoveRepository(),
                                    original.getAcceptor(),
                                    original.getForager(),
                                    moveThreadCount);
                    mtDecider.enableAssertions(
                            ai.timefold.solver.core.config.solver.EnvironmentMode.NON_REPRODUCIBLE);

                    deciderField.set(lsPhase, mtDecider);
                    swapped++;
                }
            }
        } catch (IllegalAccessException e) {
            // Fail fast at wiring time on a Timefold upgrade that changes the internals
            // this class is pinned to (see the class javadoc).
            throw new IllegalStateException("Failed to wire MultiThreadedLocalSearchDecider"
                    + " (Timefold internals changed? pinned to the verified internals line (2.3.0 through 2.7.0, see class javadoc))", e);
        }

        return swapped;
    }
}
