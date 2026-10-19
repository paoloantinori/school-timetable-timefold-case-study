package com.school.planner.domain.constraints;

import java.util.List;

import ai.timefold.solver.core.api.score.stream.ConstraintJustification;

/**
 * Justification for H2: Room conflict constraint.
 * <p>
 * Stores entity IDs for room conflicts. String details (room names, subjects, times)
 * are resolved on-demand by {@link com.school.planner.service.ConstraintMessageFormatter}
 * to avoid hot-path string allocation during solver runs.
 *
 * @param roomId         ID of the room with the conflict
 * @param lesson1Id      ID of the first conflicting lesson
 * @param lesson2Id      ID of the second conflicting lesson
 */
public record RoomConflictJustification(
        long roomId,
        long lesson1Id,
        long lesson2Id
) implements Justification, ConstraintJustification, LessonBearingJustification {

    @Override
    public List<Long> getLessonIds() {
        return List.of(lesson1Id, lesson2Id);
    }
}
