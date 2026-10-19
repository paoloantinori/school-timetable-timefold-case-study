package com.school.planner.rest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.Teacher;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.TimeslotSequence;

/**
 * CRUD for {@link RecurringAvailabilityRule} (TASK-345), following the same
 * validation style as {@code GroupCalendarRuleResource}: a typo'd slot range,
 * a dangling teacher FK, an incoherent date window, or an overlapping rule
 * would otherwise silently alter H5/S3, so those checks return 400 instead of
 * persisting garbage. See {@link RecurringAvailabilityRule#validateDateCoherence}
 * and {@link RecurringAvailabilityRule#overlaps} for the date/overlap rules
 * (the same two checks run again, fail-loud, at seed time in
 * {@code DataGenerator.generateAvailabilityRules}).
 *
 * Every mutation re-runs {@link AvailabilityExpansionService#expandForTeacher}
 * for the affected teacher so the expanded {@code TeacherAvailability} rows
 * never go stale relative to the rule set.
 */
@Path("/api/recurring-availability-rules")
@Produces(MediaType.APPLICATION_JSON)
public class RecurringAvailabilityRuleResource {

    private static final int MIN_SLOT_BOUND = 0;
    private static final int MAX_SLOT_BOUND = TimeslotSequence.SLOTS_PER_DAY - 1;

    @Inject
    AvailabilityExpansionService expansionService;

    private static void validateSlots(int minSlot, int maxSlot) {
        if (minSlot < MIN_SLOT_BOUND || minSlot > MAX_SLOT_BOUND
                || maxSlot < MIN_SLOT_BOUND || maxSlot > MAX_SLOT_BOUND || maxSlot < minSlot) {
            throw new BadRequestException(
                    "minSlot/maxSlot must be within " + MIN_SLOT_BOUND + "-" + MAX_SLOT_BOUND
                    + " and maxSlot >= minSlot (unlike GroupCalendarRule, a recurring availability"
                    + " rule has no closed-day sentinel — omit the rule instead)");
        }
    }

    private static void validateType(AvailabilityType type) {
        if (type != AvailabilityType.VACATION && type != AvailabilityType.PREFERENCE) {
            throw new BadRequestException(
                    "type must be VACATION (unavailable) or PREFERENCE (soft avoid) - SICK is not"
                    + " meaningful for a recurring rule");
        }
    }

    private static Teacher resolveTeacher(Long teacherId) {
        if (teacherId == null) {
            throw new BadRequestException("teacherId is required");
        }
        Teacher teacher = Teacher.findById(teacherId);
        if (teacher == null) {
            throw new BadRequestException("Unknown teacherId: " + teacherId);
        }
        return teacher;
    }

    private static void validateDateCoherence(RecurringAvailabilityRuleRequest request) {
        String error = RecurringAvailabilityRule.validateDateCoherence(
                request.dayOfWeek, request.effectiveFrom, request.effectiveTo);
        if (error != null) {
            throw new BadRequestException(error);
        }
    }

    /**
     * Rejects a create/update whose resulting rule would overlap an existing
     * rule for the same teacher — see {@link RecurringAvailabilityRule#overlaps}
     * for why an additive model can't tolerate overlaps. {@code excludeRuleId}
     * lets update() skip comparing the rule against its own pre-update row.
     */
    private static void validateNoOverlap(Teacher teacher, Long excludeRuleId, RecurringAvailabilityRuleRequest request) {
        RecurringAvailabilityRule candidate = new RecurringAvailabilityRule();
        candidate.dayOfWeek = request.dayOfWeek;
        candidate.minSlot = request.minSlot;
        candidate.maxSlot = request.maxSlot;
        candidate.effectiveFrom = request.effectiveFrom;
        candidate.effectiveTo = request.effectiveTo;

        List<RecurringAvailabilityRule> existing = RecurringAvailabilityRule.listByTeacher(teacher);
        for (RecurringAvailabilityRule other : existing) {
            if (excludeRuleId != null && excludeRuleId.equals(other.id)) continue;
            if (RecurringAvailabilityRule.overlaps(candidate, other)) {
                throw new BadRequestException("Rule overlaps existing rule id=" + other.id + " (" + other
                        + ") for the same teacher — H5/S3 would double-count the overlapping hours");
            }
        }
    }

    @GET
    public List<RecurringAvailabilityRule> list() {
        return RecurringAvailabilityRule.listAll();
    }

    /**
     * TASK-603: atomic bulk replace of one teacher's ALWAYS-window weekly rules (the
     * red/yellow/green grid editor's save). Deletes the teacher's always-window rules
     * and inserts the given runs in ONE transaction, then expands ONCE: the
     * client-side delete-then-create alternative had a destructive window (a failure
     * during the create phase left the teacher with ZERO always-rules) and paid one
     * full re-expansion per mutation. Date-windowed rules are NOT touched (the grid
     * cannot express them); requests carrying a window are rejected so a caller can
     * never smuggle a windowed rule through the always-only bulk path.
     */
    @PUT
    @Path("/teacher/{teacherId}")
    @Transactional
    public List<RecurringAvailabilityRule> replaceAlwaysRules(@PathParam("teacherId") Long teacherId,
            List<RecurringAvailabilityRuleRequest> runs) {
        Teacher teacher = resolveTeacher(teacherId);
        for (RecurringAvailabilityRuleRequest r : runs) {
            if (r.dayOfWeek == null) {
                throw new BadRequestException("dayOfWeek is required");
            }
            if (r.effectiveFrom != null || r.effectiveTo != null) {
                throw new BadRequestException("bulk replace accepts only always-window rules "
                        + "(effectiveFrom/effectiveTo must be null)");
            }
            validateSlots(r.minSlot, r.maxSlot);
            validateType(r.type);
        }
        for (int i = 0; i < runs.size(); i++) {
            for (int j = i + 1; j < runs.size(); j++) {
                RecurringAvailabilityRule a = new RecurringAvailabilityRule();
                a.dayOfWeek = runs.get(i).dayOfWeek;
                a.minSlot = runs.get(i).minSlot;
                a.maxSlot = runs.get(i).maxSlot;
                RecurringAvailabilityRule b = new RecurringAvailabilityRule();
                b.dayOfWeek = runs.get(j).dayOfWeek;
                b.minSlot = runs.get(j).minSlot;
                b.maxSlot = runs.get(j).maxSlot;
                if (RecurringAvailabilityRule.overlaps(a, b)) {
                    throw new BadRequestException("Runs " + i + " and " + j
                            + " overlap: an additive model would double-count those hours");
                }
            }
        }
        // The grid cannot paint windowed rules, so a run can overlap a SURVIVING
        // windowed rule even though the grid showed those slots as green (the
        // measured case: the test's own TUESDAY 3-4 window under a new TUESDAY
        // run): the additive model would double-count those hours, so the bulk
        // path enforces the same overlap boundary the single create does.
        List<RecurringAvailabilityRule> windowed = RecurringAvailabilityRule.listByTeacher(teacher).stream()
                .filter(r -> r.effectiveFrom != null || r.effectiveTo != null)
                .toList();
        for (RecurringAvailabilityRuleRequest r : runs) {
            RecurringAvailabilityRule candidate = new RecurringAvailabilityRule();
            candidate.dayOfWeek = r.dayOfWeek;
            candidate.minSlot = r.minSlot;
            candidate.maxSlot = r.maxSlot;
            for (RecurringAvailabilityRule other : windowed) {
                if (RecurringAvailabilityRule.overlaps(candidate, other)) {
                    throw new BadRequestException("Run " + r.dayOfWeek + " [" + r.minSlot + "-" + r.maxSlot
                            + "] overlaps existing windowed rule id=" + other.id + " (" + other
                            + ") for the same teacher - H5/S3 would double-count the overlapping hours");
                }
            }
        }
        for (RecurringAvailabilityRule rule : RecurringAvailabilityRule.listByTeacher(teacher)) {
            if (rule.effectiveFrom == null && rule.effectiveTo == null) {
                rule.delete();
            }
        }
        for (RecurringAvailabilityRuleRequest r : runs) {
            RecurringAvailabilityRule rule = new RecurringAvailabilityRule();
            applyRequest(rule, teacher, r);
            rule.persist();
        }
        expansionService.expandForTeacher(teacher);
        return RecurringAvailabilityRule.listByTeacher(teacher);
    }

    @POST
    @Transactional
    public RecurringAvailabilityRule create(RecurringAvailabilityRuleRequest request) {
        if (request.dayOfWeek == null) {
            throw new BadRequestException("dayOfWeek is required");
        }
        validateSlots(request.minSlot, request.maxSlot);
        validateType(request.type);
        validateDateCoherence(request);
        Teacher teacher = resolveTeacher(request.teacherId);
        validateNoOverlap(teacher, null, request);

        RecurringAvailabilityRule rule = new RecurringAvailabilityRule();
        applyRequest(rule, teacher, request);
        rule.persist();

        expansionService.expandForTeacher(teacher);
        return rule;
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public RecurringAvailabilityRule update(@PathParam("id") Long id, RecurringAvailabilityRuleRequest request) {
        RecurringAvailabilityRule rule = RecurringAvailabilityRule.findById(id);
        if (rule == null) {
            throw new NotFoundException("RecurringAvailabilityRule not found: " + id);
        }
        if (request.dayOfWeek == null) {
            throw new BadRequestException("dayOfWeek is required");
        }
        validateSlots(request.minSlot, request.maxSlot);
        validateType(request.type);
        validateDateCoherence(request);
        Teacher teacher = resolveTeacher(request.teacherId);
        validateNoOverlap(teacher, id, request);

        // A rule can move to a different teacher - both old and new teacher's
        // expanded rows must be refreshed, or the old teacher would keep
        // stale expanded rows forever.
        Teacher previousTeacher = rule.teacher;

        applyRequest(rule, teacher, request);
        rule.persist();

        expansionService.expandForTeacher(teacher);
        if (previousTeacher != null && !previousTeacher.id.equals(teacher.id)) {
            expansionService.expandForTeacher(previousTeacher);
        }
        return rule;
    }

    private static void applyRequest(RecurringAvailabilityRule rule, Teacher teacher,
                                     RecurringAvailabilityRuleRequest request) {
        rule.teacher = teacher;
        rule.dayOfWeek = request.dayOfWeek;
        rule.minSlot = request.minSlot;
        rule.maxSlot = request.maxSlot;
        rule.type = request.type;
        rule.effectiveFrom = request.effectiveFrom;
        rule.effectiveTo = request.effectiveTo;
        rule.note = request.note;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        RecurringAvailabilityRule rule = RecurringAvailabilityRule.findById(id);
        if (rule == null) return;
        Teacher teacher = rule.teacher;
        rule.delete();
        if (teacher != null) {
            expansionService.expandForTeacher(teacher);
        }
    }

    public static class RecurringAvailabilityRuleRequest {
        public Long teacherId;
        public DayOfWeek dayOfWeek;
        public int minSlot;
        public int maxSlot;
        public AvailabilityType type;
        public LocalDate effectiveFrom;
        public LocalDate effectiveTo;
        public String note;
    }
}
