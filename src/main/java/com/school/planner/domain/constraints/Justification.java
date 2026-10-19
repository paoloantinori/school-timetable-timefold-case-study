package com.school.planner.domain.constraints;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Marker interface for constraint justification records.
 * <p>
 * Justifications store entity IDs for constraint violations. String details
 * (names, subjects, times) are resolved on-demand by
 * {@link com.school.planner.service.ConstraintMessageFormatter} to avoid
 * hot-path string allocation during solver runs.
 * <p>
 * This is a sealed interface permitting specific justification types.
 * During implementation (Phase 1), only {@link TeacherConflictJustification} is
 * implemented. Additional justification types (H2, H3, H7, H11, M20) will be
 * added in subsequent phases.
 * <p>
 * All justification records must also implement Timefold's
 * {@code ai.timefold.solver.core.api.score.stream.ConstraintJustification}
 * interface for compatibility with the constraint stream API.
 *
 * @see ai.timefold.solver.core.api.score.stream.ConstraintJustification
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TeacherConflictJustification.class, name = "TeacherConflict"),
    @JsonSubTypes.Type(value = RoomConflictJustification.class, name = "RoomConflict"),
    @JsonSubTypes.Type(value = StudentGroupConflictJustification.class, name = "StudentGroupConflict"),
    @JsonSubTypes.Type(value = TeacherDailyHoursJustification.class, name = "TeacherDailyHours"),
    @JsonSubTypes.Type(value = StudentGroupDailyHoursJustification.class, name = "StudentGroupDailyHours"),
    @JsonSubTypes.Type(value = SparseDayJustification.class, name = "SparseDay"),
    @JsonSubTypes.Type(value = VerticallyCompactJustification.class, name = "VerticallyCompact"),
    @JsonSubTypes.Type(value = DayStartOffsetJustification.class, name = "DayStartOffset")
})
public sealed interface Justification permits
        TeacherConflictJustification,
        RoomConflictJustification,
        StudentGroupConflictJustification,
        TeacherDailyHoursJustification,
        StudentGroupDailyHoursJustification,
        SparseDayJustification,
        VerticallyCompactJustification,
        DayStartOffsetJustification {
}
