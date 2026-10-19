package com.school.planner.rest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;

@Path("/api/stage-patterns")
@Produces(MediaType.APPLICATION_JSON)
public class StagePatternResource {

    @GET
    public List<StagePattern> list() {
        return StagePattern.listAll();
    }

    @POST
    @Transactional
    public StagePattern create(StagePatternRequest request) {
        StagePattern pattern = new StagePattern();
        if (request.studentGroupId != null) {
            pattern.studentGroup = StudentGroup.findById(request.studentGroupId);
        }
        pattern.startDate = request.startDate;
        pattern.endDate = request.endDate;
        pattern.dayOfWeekMask = request.dayOfWeekMask != null ? request.dayOfWeekMask : Set.of();
        pattern.persist();
        return pattern;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        StagePattern.deleteById(id);
    }

    public static class StagePatternRequest {
        public Long studentGroupId;
        public LocalDate startDate;
        public LocalDate endDate;
        public Set<DayOfWeek> dayOfWeekMask;
    }
}
