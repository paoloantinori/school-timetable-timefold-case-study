package com.school.planner.solver;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import ai.timefold.solver.core.api.solver.phase.PhaseCommand;
import ai.timefold.solver.core.api.solver.phase.PhaseCommandContext;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel;
import ai.timefold.solver.core.preview.api.move.builtin.Moves;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;

/**
 * TASK-393/401: final deterministic repair + student-day gap compaction, run AFTER
 * the timeslot local search and BEFORE the room phases. Since TASK-420 removed the
 * inert RuinRecreate local search, this is phase 3 of a 5-phase pipeline and the
 * local search before it is phase 2 (Compactness).
 *
 * <p>Why a separate phase and not just the construction-tail compaction
 * (TASK-390): the timeslot local search is the last phase that moves timeslots and
 * can legitimately RE-OPEN an H17 gap when doing so resolves a bigger hard
 * violation (trade one H17 -1 for one H3 -1000 = +999 net hard, so the acceptor
 * takes it). Nothing after it (the room phases) re-closes that gap. This phase
 * re-runs the exact same {@link StudentDayCompaction} the construction tail uses
 * - deterministic and improve-or-noop - to close the avoidable ones the
 * stochastic local search left, without relying on the search to rediscover the
 * tail single-lesson relocations. If the local search re-opened nothing, this is
 * a cheap deterministic no-op (a permanent safety net). The compaction is
 * conflict-free by construction (see StudentDayCompaction), so it can only ever
 * improve the hard score here, never regress it.
 *
 * <p>TASK-401 adds {@link BoxRepair} around the compaction: the LS reliably leaves
 * a few boxed lessons (TASK-398's H0 residual) whose ~3-7 feasible cells among
 * ~1,000 VRP-legal ones stochastic search misses (TASK-399's measurement) - the
 * repair finds them by direct enumeration. It runs BEFORE the compaction (filling
 * an internal hole beats relocating a lesson into it) and again AFTER it (the
 * compaction's relocations free cells a first pass couldn't use). Both passes are
 * improve-only: each placement removes one H0 hard and is VRP-legal,
 * conflict-free, cap-checked and H17-safe by BoxRepair's gates.
 */
public class CompactionPhase<Solution_> implements PhaseCommand<Solution_> {

    private static final Logger LOG = Logger.getLogger(CompactionPhase.class);

    @SuppressWarnings("unchecked")
    @Override
    public void changeWorkingSolution(PhaseCommandContext<Solution_> context) {
        PlanningVariableMetaModel<Solution_, Lesson, Timeslot> timeslotVariable =
                (PlanningVariableMetaModel<Solution_, Lesson, Timeslot>)
                        context.getSolutionMetaModel().entity(Lesson.class).variable("timeslot", Timeslot.class);
        PlanningVariableMetaModel<Solution_, Lesson, Room> roomVariable =
                (PlanningVariableMetaModel<Solution_, Lesson, Room>)
                        context.getSolutionMetaModel().entity(Lesson.class).variable("room", Room.class);

        SchoolSchedule schedule = (SchoolSchedule) context.getWorkingSolution();
        BiConsumer<Lesson, Timeslot> moveTimeslot =
                (lesson, ts) -> context.execute(Moves.change(timeslotVariable, lesson, ts));
        Consumer<Lesson> nullRoom =
                lesson -> { if (lesson.room != null) context.execute(Moves.change(roomVariable, lesson, null)); };

        BoxRepair.Result repair1 = BoxRepair.repair(schedule, moveTimeslot, nullRoom);
        LOG.infof("TASK-401/414 box repair (pre-compaction): boxed %d -> placed %d (evicted %d), still boxed %d, conflicts added %d",
                repair1.boxedBefore(), repair1.placed(), repair1.evicted(), repair1.boxedAfter(), repair1.conflictsAdded());

        StudentDayCompaction.Result r = StudentDayCompaction.compact(schedule, moveTimeslot, nullRoom);
        LOG.infof("TASK-393/405 final compaction: student-day gaps %d->%d (moved %d), left-anchored %d, conflicts added %d",
                r.gapsBefore(), r.gapsAfter(), r.moved(), r.anchored(), r.conflictsAdded());

        if (repair1.boxedAfter() > 0) {
            BoxRepair.Result repair2 = BoxRepair.repair(schedule, moveTimeslot, nullRoom);
            LOG.infof("TASK-401/414 box repair (post-compaction): boxed %d -> placed %d (evicted %d), still boxed %d, conflicts added %d",
                    repair2.boxedBefore(), repair2.placed(), repair2.evicted(), repair2.boxedAfter(), repair2.conflictsAdded());
        }
    }
}
