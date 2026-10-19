package com.school.planner.solver.checkpoint;

/**
 * TASK-542 stage 1: one lesson's placement in the checkpoint grid. Null ids mean exactly what
 * they mean in the domain (timeslot null = boxed/unassigned, room null = roomless). The
 * {@code promoted} flag carries the promotedLessonIds static (TASK-419 promotion output);
 * {@code pinned} is the authoritative-pin flag (input, mirrored for grid completeness).
 */
public record LessonPlacement(
        long lessonId,
        Long timeslotId,
        Long roomId,
        boolean pinned,
        boolean promoted) {
}
