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

import com.school.planner.domain.SchoolHoliday;

@Path("/api/holidays")
@Produces(MediaType.APPLICATION_JSON)
public class HolidayResource {

    @GET
    public List<SchoolHoliday> list() {
        return SchoolHoliday.listAll();
    }

    @POST
    @Transactional
    public SchoolHoliday create(SchoolHoliday holiday) {
        holiday.persist();
        return holiday;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        SchoolHoliday.deleteById(id);
    }
}
