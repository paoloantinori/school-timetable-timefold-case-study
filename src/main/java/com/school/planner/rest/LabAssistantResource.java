package com.school.planner.rest;

import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.school.planner.domain.LabAssistant;
import com.school.planner.domain.Teacher;

@Path("/api/lab-assistants")
@Produces(MediaType.APPLICATION_JSON)
public class LabAssistantResource {

    @GET
    public List<LabAssistant> list() {
        return LabAssistant.listAll();
    }

    @POST
    @Transactional
    public LabAssistant create(LabAssistantRequest request) {
        LabAssistant la = new LabAssistant();
        if (request.teacherId != null) {
            la.teacher = Teacher.findById(request.teacherId);
        }
        la.roomTag = request.roomTag;
        la.persist();
        return la;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        LabAssistant.deleteById(id);
    }

    public static class LabAssistantRequest {
        public Long teacherId;
        public String roomTag;
    }
}
