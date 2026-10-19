package com.school.planner.rest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.school.planner.domain.ProfessionalizzantiDay;
import com.school.planner.domain.StudentGroup;

/**
 * REST API for managing ProfessionalizzantiDay entities.
 *
 * ProfessionalizzantiDay marks specific days during stage periods when
 * professionalizzanti courses should be scheduled. This helps enforce
 * the H4.1 constraint that professionalizzanti lessons should be on a
 * constant day of the week during stage periods.
 */
@Path("/api/professionalizzanti-days")
@Produces(MediaType.APPLICATION_JSON)
public class ProfessionalizzantiDayResource {

    @GET
    public List<ProfessionalizzantiDay> list() {
        return ProfessionalizzantiDay.listAll();
    }

    @POST
    @Transactional
    public ProfessionalizzantiDay create(ProfessionalizzantiDayRequest request) {
        ProfessionalizzantiDay day = new ProfessionalizzantiDay();
        if (request.studentGroupId != null) {
            day.studentGroup = StudentGroup.findById(request.studentGroupId);
        }
        day.date = request.date != null ? LocalDate.parse(request.date) : null;
        day.dayOfWeek = request.dayOfWeek;
        day.description = request.description;
        day.persist();
        return day;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        ProfessionalizzantiDay.deleteById(id);
    }

    public static class ProfessionalizzantiDayRequest {
        public Long studentGroupId;
        public String date;
        public DayOfWeek dayOfWeek;
        public String description;
    }
}
