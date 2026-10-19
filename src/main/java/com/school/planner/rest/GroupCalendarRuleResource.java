package com.school.planner.rest;

import java.time.DateTimeException;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.service.GroupCalendarService;
import com.school.planner.service.GroupCalendarService.ResolvedSlotRange;
import com.school.planner.solver.TimeslotSequence;

@Path("/api/group-calendar-rules")
@Produces(MediaType.APPLICATION_JSON)
public class GroupCalendarRuleResource {

    @Inject
    GroupCalendarService groupCalendarService;

    // Deliberate small deviation from the no-validation house style: an inert or
    // orphaned GroupCalendarRule silently alters solving (a typo'd slot range or a
    // dangling FK to a deleted group is never surfaced anywhere else), so these
    // three checks are worth the extra code.
    private static final int MIN_SLOT_BOUND = 0;
    // Derived from TimeslotSequence (not a re-declared literal) - code review follow-up
    // (TASK-358 altitude finding): this file's own /resolved endpoint and StudentGroupResource's
    // maxHoursPerDay check already source their slot-count bound from TimeslotSequence.
    private static final int MAX_SLOT_BOUND = TimeslotSequence.SLOT_START_TIMES.length - 1;

    private static void validateSlots(int minSlot, int maxSlot) {
        // Closed-day sentinel per GroupCalendarRule's own definition: any maxSlot < minSlot
        // (not just the canonical 0,-1), so e.g. minSlot=3/maxSlot=1 is also valid.
        if (maxSlot < minSlot) return;
        if (minSlot < MIN_SLOT_BOUND || minSlot > MAX_SLOT_BOUND
                || maxSlot < MIN_SLOT_BOUND || maxSlot > MAX_SLOT_BOUND) {
            throw new BadRequestException(
                    "minSlot/maxSlot must be within " + MIN_SLOT_BOUND + "-" + MAX_SLOT_BOUND
                    + " (or a closed-day sentinel where maxSlot < minSlot)");
        }
    }

    private static StudentGroup resolveStudentGroup(Long studentGroupId) {
        if (studentGroupId == null) return null;
        StudentGroup group = StudentGroup.findById(studentGroupId);
        if (group == null) {
            throw new BadRequestException("Unknown studentGroupId: " + studentGroupId);
        }
        return group;
    }

    @GET
    public List<GroupCalendarRule> list() {
        return GroupCalendarRule.listAll();
    }

    /**
     * Resolved per-group, per-date opening pattern (TASK-358 code review N2):
     * replaces the frontend's client-side port of GroupCalendarService's
     * most-specific-rule-wins resolution, which had drifted risk and zero
     * tests of its own. One entry per (student group, school-weekday date) in
     * {@code [from, to]}; see {@link GroupCalendarService#resolveRange} for the
     * resolution algorithm and the legacy maxHoursPerDay fallback (N3).
     */
    @GET
    @Path("/resolved")
    public List<ResolvedSlotRange> resolved(@QueryParam("from") String fromStr, @QueryParam("to") String toStr) {
        // Deliberately String, not typed @QueryParam LocalDate (tried during code-review
        // cleanup to match ScheduleResource's startDate/endDate and drop this manual parsing -
        // reverted: RESTEasy Reactive here returns 404, not 400, when a typed LocalDate param
        // fails conversion, which silently breaks the "malformed date -> 400" contract this
        // endpoint promises. Manual parsing keeps that contract explicit and tested.
        if (fromStr == null || toStr == null) {
            throw new BadRequestException("from and to query params are required (yyyy-MM-dd)");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(fromStr);
            to = LocalDate.parse(toStr);
        } catch (DateTimeException e) {
            throw new BadRequestException("from/to must be valid ISO dates (yyyy-MM-dd): " + e.getMessage());
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("to must not be before from");
        }
        return groupCalendarService.resolveRange(from, to);
    }

    @POST
    @Transactional
    public GroupCalendarRule create(GroupCalendarRuleRequest request) {
        if (request.dayOfWeek == null) {
            throw new BadRequestException("dayOfWeek is required");
        }
        validateSlots(request.minSlot, request.maxSlot);
        GroupCalendarRule rule = new GroupCalendarRule();
        rule.studentGroup = resolveStudentGroup(request.studentGroupId);
        rule.dayOfWeek = request.dayOfWeek;
        rule.minSlot = request.minSlot;
        rule.maxSlot = request.maxSlot;
        rule.effectiveFrom = request.effectiveFrom;
        rule.effectiveTo = request.effectiveTo;
        rule.persist();
        return rule;
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public GroupCalendarRule update(@PathParam("id") Long id, GroupCalendarRuleRequest request) {
        GroupCalendarRule rule = GroupCalendarRule.findById(id);
        if (rule == null) {
            throw new NotFoundException("GroupCalendarRule not found: " + id);
        }
        validateSlots(request.minSlot, request.maxSlot);
        if (request.studentGroupId != null) {
            rule.studentGroup = resolveStudentGroup(request.studentGroupId);
        }
        if (request.dayOfWeek != null) {
            rule.dayOfWeek = request.dayOfWeek;
        }
        rule.minSlot = request.minSlot;
        rule.maxSlot = request.maxSlot;
        rule.effectiveFrom = request.effectiveFrom;
        rule.effectiveTo = request.effectiveTo;
        rule.persist();
        return rule;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        GroupCalendarRule.deleteById(id);
    }

    public static class GroupCalendarRuleRequest {
        public Long studentGroupId;
        public DayOfWeek dayOfWeek;
        public int minSlot;
        public int maxSlot;
        public LocalDate effectiveFrom;
        public LocalDate effectiveTo;
    }
}
