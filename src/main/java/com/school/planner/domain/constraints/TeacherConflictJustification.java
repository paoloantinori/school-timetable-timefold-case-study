package com.school.planner.domain.constraints;

import java.util.List;

import ai.timefold.solver.core.api.score.stream.ConstraintJustification;

/**
 * Justification for H1: Teacher conflict constraint.
 * <p>
 * Stores entity IDs for teacher conflicts. String details (names, subjects, times)
 * are resolved on-demand by {@link com.school.planner.service.ConstraintMessageFormatter}
 * to avoid hot-path string allocation during solver runs.
 *
 * @param teacherId      ID of the teacher with the conflict
 * @param lesson1Id       ID of the first conflicting lesson
 * @param lesson2Id       ID of the second conflicting lesson
 */
public record TeacherConflictJustification(
        long teacherId,
        long lesson1Id,
        long lesson2Id
) implements Justification, ConstraintJustification, LessonBearingJustification {

    @Override
    public List<Long> getLessonIds() {
        return List.of(lesson1Id, lesson2Id);
    }
}
