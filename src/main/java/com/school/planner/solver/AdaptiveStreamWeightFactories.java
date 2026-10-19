package com.school.planner.solver;

import com.school.planner.domain.SchoolSchedule;

import ai.timefold.solver.core.impl.heuristic.selector.common.decorator.SelectionProbabilityWeightFactory;
import ai.timefold.solver.core.impl.score.director.ScoreDirector;
import ai.timefold.solver.core.preview.api.move.Move;

/**
 * TASK-500: the six per-stream probability weight factories the adaptive wiring sets on
 * the union's children (in place of {@code fixedProbabilityWeight}). One nested class
 * per stream because the config API takes a CLASS with a no-arg constructor (Timefold
 * instantiates it at {@code buildSolver}); each returns {@link AdaptiveMoveWeights#weight}
 * for its stream, a single volatile read.
 */
public final class AdaptiveStreamWeightFactories {

    private AdaptiveStreamWeightFactories() {
    }

    private static double weight(AdaptiveMoveWeights.Stream stream) {
        return AdaptiveMoveWeights.weight(stream);
    }

    public static final class TsChange
            implements SelectionProbabilityWeightFactory<SchoolSchedule, Move<SchoolSchedule>> {
        @Override
        public double createProbabilityWeight(ScoreDirector<SchoolSchedule> scoreDirector, Move<SchoolSchedule> selection) {
            return weight(AdaptiveMoveWeights.Stream.TS_CHANGE);
        }
    }

    public static final class TsSwap
            implements SelectionProbabilityWeightFactory<SchoolSchedule, Move<SchoolSchedule>> {
        @Override
        public double createProbabilityWeight(ScoreDirector<SchoolSchedule> scoreDirector, Move<SchoolSchedule> selection) {
            return weight(AdaptiveMoveWeights.Stream.TS_SWAP);
        }
    }

    public static final class TsPillarChange
            implements SelectionProbabilityWeightFactory<SchoolSchedule, Move<SchoolSchedule>> {
        @Override
        public double createProbabilityWeight(ScoreDirector<SchoolSchedule> scoreDirector, Move<SchoolSchedule> selection) {
            return weight(AdaptiveMoveWeights.Stream.TS_PILLAR_CHANGE);
        }
    }

    public static final class TsPillarSwap
            implements SelectionProbabilityWeightFactory<SchoolSchedule, Move<SchoolSchedule>> {
        @Override
        public double createProbabilityWeight(ScoreDirector<SchoolSchedule> scoreDirector, Move<SchoolSchedule> selection) {
            return weight(AdaptiveMoveWeights.Stream.TS_PILLAR_SWAP);
        }
    }

    public static final class RoomChange
            implements SelectionProbabilityWeightFactory<SchoolSchedule, Move<SchoolSchedule>> {
        @Override
        public double createProbabilityWeight(ScoreDirector<SchoolSchedule> scoreDirector, Move<SchoolSchedule> selection) {
            return weight(AdaptiveMoveWeights.Stream.ROOM_CHANGE);
        }
    }

    public static final class RoomSwap
            implements SelectionProbabilityWeightFactory<SchoolSchedule, Move<SchoolSchedule>> {
        @Override
        public double createProbabilityWeight(ScoreDirector<SchoolSchedule> scoreDirector, Move<SchoolSchedule> selection) {
            return weight(AdaptiveMoveWeights.Stream.ROOM_SWAP);
        }
    }
}
