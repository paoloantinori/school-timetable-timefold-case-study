package com.school.planner.solver.checkpoint;

import java.util.List;

import com.school.planner.domain.Lesson;

/**
 * TASK-542 stage 2: builds a {@link StagedCheckpoint} from the live grid at a phase boundary.
 * Pure (no DB, no solver): the wiring that calls this inside {@code runStagedCycle} is the
 * flag-gated hook in ScheduleService; the format contract is stage 1.
 *
 * <p>Stage-2 note (backlog #4): the promoted flag is read from {@code lesson.promotedPin},
 * NEVER from {@code lesson.pinned} - an authoritative pin is pinned=true/promotedPin=false and
 * must not be captured as promoted (the confusion would poison the resume's promotion set).
 */
public final class StagedCheckpointCapture {

    private StagedCheckpointCapture() {
    }

    public static StagedCheckpoint from(List<Lesson> lessons, StagedCheckpointCursor cursor,
            String score, long boxed, long timeslotTierHard) {
        return new StagedCheckpoint(
                StagedCheckpoint.CURRENT_VERSION,
                cursor,
                score,
                boxed,
                timeslotTierHard,
                lessons.stream()
                        .map(l -> new LessonPlacement(
                                l.id,
                                l.timeslot != null ? l.timeslot.id : null,
                                l.room != null ? l.room.id : null,
                                l.pinned,
                                l.promotedPin))
                        .toList());
    }
}
