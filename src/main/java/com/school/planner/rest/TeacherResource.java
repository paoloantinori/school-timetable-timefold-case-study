package com.school.planner.rest;

import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;

@Path("/api/teachers")
@Produces(MediaType.APPLICATION_JSON)
public class TeacherResource {

    @GET
    public List<Teacher> list() {
        return Teacher.listAll();
    }

    @POST
    @Transactional
    public Teacher create(Teacher teacher) {
        validateOptionalCount(teacher.maxDailyHours, 1, 8, "maxDailyHours");
        teacher.maxDailyHours = normalizeOptionalCount(teacher.maxDailyHours);
        validateOptionalCount(teacher.maxEarlyStartDaysPerWeek, 1, 5, "maxEarlyStartDaysPerWeek");
        teacher.maxEarlyStartDaysPerWeek = normalizeOptionalCount(teacher.maxEarlyStartDaysPerWeek);
        validateOptionalCount(teacher.requiredFreeDaysPerWeek, 1, 5, "requiredFreeDaysPerWeek");
        teacher.requiredFreeDaysPerWeek = normalizeOptionalCount(teacher.requiredFreeDaysPerWeek);
        teacher.persist();
        return teacher;
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Teacher update(@PathParam("id") Long id, Teacher updated) {
        Teacher teacher = Teacher.findById(id);
        if (teacher == null) {
            throw new jakarta.ws.rs.NotFoundException("Teacher not found: " + id);
        }
        teacher.name = updated.name;
        teacher.nickname = updated.nickname;
        teacher.color = updated.color;
        teacher.pattern = updated.pattern;
        // TASK-348: null-keeps semantics (same pattern as StudentGroupResource's
        // maxHoursPerDay) - partial payloads that omit one of these fields
        // deserialize to null, so a missing value must leave the existing
        // override alone rather than silently wiping it on every ordinary
        // edit. This means null alone cannot also mean "clear the override" -
        // see normalizeOptionalCount(). TASK-368 extends the same pattern to
        // maxEarlyStartDaysPerWeek/requiredFreeDaysPerWeek.
        if (updated.maxDailyHours != null) {
            validateOptionalCount(updated.maxDailyHours, 1, 8, "maxDailyHours");
            teacher.maxDailyHours = normalizeOptionalCount(updated.maxDailyHours);
        }
        if (updated.maxEarlyStartDaysPerWeek != null) {
            validateOptionalCount(updated.maxEarlyStartDaysPerWeek, 1, 5, "maxEarlyStartDaysPerWeek");
            teacher.maxEarlyStartDaysPerWeek = normalizeOptionalCount(updated.maxEarlyStartDaysPerWeek);
        }
        if (updated.requiredFreeDaysPerWeek != null) {
            validateOptionalCount(updated.requiredFreeDaysPerWeek, 1, 5, "requiredFreeDaysPerWeek");
            teacher.requiredFreeDaysPerWeek = normalizeOptionalCount(updated.requiredFreeDaysPerWeek);
        }
        return teacher;
    }

    /**
     * TASK-348 code review (D1): {@code 0} is the explicit "clear the
     * override" sentinel, distinct from {@code null} (which, per the
     * null-keeps semantics above, means "field omitted, leave unchanged").
     * Without a separate sentinel, a UI whose blank input serializes to
     * {@code null} could never reset a teacher back to the default - the PUT
     * would just be treated as omitting the field. Maps {@code 0} to the real
     * {@code null} (default) after validation; any other value passes through
     * unchanged. Shared (TASK-368) across all three optional per-teacher
     * count overrides (maxDailyHours, maxEarlyStartDaysPerWeek,
     * requiredFreeDaysPerWeek) instead of tripling this pair of methods.
     */
    private static Integer normalizeOptionalCount(Integer value) {
        return (value == null || value == 0) ? null : value;
    }

    private static void validateOptionalCount(Integer value, int min, int max, String fieldName) {
        // 0 is always the clear sentinel regardless of min - only a non-zero
        // value must actually fall within [min, max]. (Code review: an
        // earlier draft checked "< 0" here instead of "< min", which happened
        // to be equivalent only because every current caller passes min=1;
        // that made min a parameter in name only.)
        if (value != null && value != 0 && (value < min || value > max)) {
            throw new BadRequestException(fieldName + " must be 0 (clear override) or between " + min + " and " + max);
        }
    }

    /**
     * Deletes the teacher, cascading to its {@link RecurringAvailabilityRule}
     * templates and their expanded {@link TeacherAvailability} rows first
     * (TASK-345 widened the teacher FK graph with these two tables; without
     * this cleanup, deleting a teacher with rules would either FK-violate or
     * leave orphaned rows behind, depending on schema cascade settings).
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        Teacher teacher = Teacher.findById(id);
        if (teacher == null) return;
        RecurringAvailabilityRule.delete("teacher", teacher);
        TeacherAvailability.delete("teacher", teacher);
        teacher.delete();
    }
}
