package com.school.planner.rest;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;

@Path("/api/teacher-availability")
@Produces(MediaType.APPLICATION_JSON)
public class TeacherAvailabilityResource {

    @GET
    public List<TeacherAvailability> list() {
        return TeacherAvailability.listAll();
    }

    @POST
    @Transactional
    public TeacherAvailability create(TeacherAvailabilityRequest request) {
        TeacherAvailability ta = new TeacherAvailability();
        if (request.teacherId != null) {
            ta.teacher = Teacher.findById(request.teacherId);
        }
        ta.startDateTime = request.startDateTime;
        ta.endDateTime = request.endDateTime;
        ta.type = request.type;
        ta.persist();
        return ta;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        TeacherAvailability.deleteById(id);
    }

    public static class TeacherAvailabilityRequest {
        public Long teacherId;
        public LocalDateTime startDateTime;
        public LocalDateTime endDateTime;
        public AvailabilityType type;
    }
}
