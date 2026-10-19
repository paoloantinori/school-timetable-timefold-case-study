package com.school.planner.domain.constraints;

import java.util.List;

/**
 * Interface for custom justifications that reference Lesson entities by ID
 * rather than holding direct entity references.
 *
 * <p>This allows {@link com.school.planner.service.ScheduleService#analyzeLessonViolations}
 * to map constraint matches back to Lesson entities after solving, even though
 * the justification itself only stores IDs (avoiding entity-reference retention
 * and supporting ID-based lookups after solution deserialization).
 *
 * <p>Implementations include:
 * <ul>
 *   <li>{@link TeacherConflictJustification} (H1)</li>
 *   <li>{@link RoomConflictJustification} (H2)</li>
 *   <li>{@link StudentGroupConflictJustification} (H3)</li>
 * </ul>
 */
public interface LessonBearingJustification {
    /**
     * IDs of the Lesson entities involved in this constraint violation.
     * Used to look up the Lesson entities in the schedule for message formatting.
     */
    List<Long> getLessonIds();
}
