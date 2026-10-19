package com.school.planner.solver.moves;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.random.RandomGenerator;

import ai.timefold.solver.core.impl.score.director.ScoreDirector; // mech (laptop): 2.3.0 has it in impl, not api
import ai.timefold.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningSolutionMetaModel;
import ai.timefold.solver.core.preview.api.move.Move;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.PairCellGuard;
import com.school.planner.solver.TimeslotSequence;

/**
 * TASK-576 F8: the {@link MoveIteratorFactory} feeding {@link PairBlockShiftMove} into
 * the Phase-2 local search (wired in solverConfig.xml behind
 * {@code planner.ls.pair-move.enabled}, DEFAULT OFF - prototype pending the F8 bench
 * and the E5 cycle).
 *
 * <p>Random-only (the original iterator throws, as the interface allows): each draw
 * samples lessons until one is an F8 candidate (a pair-form lesson sitting off its
 * form, {@link PairBlockShiftPlanner#isCandidate}), then plans the block shift. With
 * the flag off, the pair builder off, or no candidate found within the draw budget,
 * the iterator is simply empty: the LS sees no pair moves, byte-identical behavior to
 * before the wiring.</p>
 *
 * <p>State note: the meta-model, the population reference and the (date,slot) grid are
 * resolved once per phase; the working entities are the SAME instances the solver moves
 * (only their timeslot field changes), so the population list stays valid across steps
 * and each draw reads the CURRENT placement. The produced move rebases per-thread via
 * Lookup, so references captured here are safe for the multithreaded decider.</p>
 */
public final class PairBlockShiftMoveIteratorFactory
        implements MoveIteratorFactory<SchoolSchedule, Move<SchoolSchedule>> {

    /** Standard-form opt-in flag (DEFAULT OFF). */
    public static final String FLAG = "planner.ls.pair-move.enabled";

    /** How many lesson draws one next() may spend before giving up (empty iterator tick). */
    static final int DRAW_BUDGET = 200;

    private List<Lesson> population = List.of();
    private Map<java.time.LocalDate, Map<Integer, Timeslot>> grid = Map.of();
    // Phase-scoped (simplify/efficiency review 2026-09-01): the flag and the meta-model
    // are constant for the phase; resolving them per draw or per move was per-tick
    // property lookups and a cast chain in the move loop.
    private boolean phaseEnabled;
    private ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> timeslotVariable;

    static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(FLAG));
    }

    @Override
    public void phaseStarted(ScoreDirector<SchoolSchedule> scoreDirector) {
        SchoolSchedule working = scoreDirector.getWorkingSolution();
        this.population = working.getLessons();
        this.grid = PairBlockShiftPlanner.gridOf(working.getTimeslots());
        this.phaseEnabled = enabled();
        this.timeslotVariable = timeslotVariableModel(scoreDirector);
    }

    @Override
    public void phaseEnded(ScoreDirector<SchoolSchedule> scoreDirector) {
        this.population = List.of();
        this.grid = Map.of();
        this.phaseEnabled = false;
        this.timeslotVariable = null;
    }

    @Override
    public long getSize(ScoreDirector<SchoolSchedule> scoreDirector) {
        throw new UnsupportedOperationException("random-only factory");
    }

    @Override
    public Iterator<Move<SchoolSchedule>> createOriginalMoveIterator(ScoreDirector<SchoolSchedule> scoreDirector) {
        throw new UnsupportedOperationException("random-only factory");
    }

    @Override
    public Iterator<Move<SchoolSchedule>> createRandomMoveIterator(ScoreDirector<SchoolSchedule> scoreDirector,
            RandomGenerator workingRandom) {
        return new Iterator<>() {
            private PairBlockShiftMove next;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                if (!phaseEnabled || population.isEmpty() || PairCellGuard.publishedPairCount() == 0) return false;
                for (int draw = 0; draw < DRAW_BUDGET; draw++) {
                    Lesson seed = population.get(workingRandom.nextInt(population.size()));
                    if (!PairBlockShiftPlanner.isCandidate(seed)) continue;
                    List<Lesson> block = PairBlockShiftPlanner.blockOf(seed, population);
                    if (block.isEmpty()) continue;
                    List<Timeslot> targets = PairBlockShiftPlanner.targetsOf(seed, block,
                            PairCellGuard.formCellsOf(seed), grid);
                    if (targets == null) continue;
                    int k = TimeslotSequence.slotIndexOf(seed.timeslot)
                            - TimeslotSequence.slotIndexOf(targets.get(indexOfSeed(block, seed)));
                    next = new PairBlockShiftMove(
                            timeslotVariable, block, targets,
                            PairBlockShiftPlanner.describe(seed, k));
                    return true;
                }
                return false; // this tick found nothing: the LS keeps its other selectors
            }

            @Override
            public Move<SchoolSchedule> next() {
                if (!hasNext()) throw new NoSuchElementException("no pair-block candidate in budget");
                PairBlockShiftMove move = Objects.requireNonNull(next);
                next = null;
                return move;
            }
        };
    }

    private static int indexOfSeed(List<Lesson> block, Lesson seed) {
        for (int i = 0; i < block.size(); i++) {
            if (block.get(i) == seed) return i;
        }
        return 0;
    }

    private static ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> timeslotVariableModel(
            ScoreDirector<SchoolSchedule> scoreDirector) {
        // mech (laptop): the impl ScoreDirector interface has no getSolutionDescriptor();
        // the runtime director is an InnerScoreDirector which does (verified via javap).
        PlanningSolutionMetaModel<SchoolSchedule> metaModel =
                ((ai.timefold.solver.core.impl.score.director.InnerScoreDirector<SchoolSchedule, ?>) scoreDirector)
                        .getSolutionDescriptor().getMetaModel();
        // mech (laptop): the CompactionPhase pattern - genuineVariable() is generic-unfriendly
        // (no instance of Value_ conforms to the Timeslot-typed meta model).
        return (ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot>)
                metaModel.entity(Lesson.class).variable("timeslot", Timeslot.class);
    }
}
