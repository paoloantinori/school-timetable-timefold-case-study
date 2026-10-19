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

import com.school.planner.domain.StudentGroup;
import com.school.planner.solver.TimeslotSequence;

@Path("/api/student-groups")
@Produces(MediaType.APPLICATION_JSON)
public class StudentGroupResource {

    @GET
    public List<StudentGroup> list() {
        return StudentGroup.listAll();
    }

    @POST
    @Transactional
    public StudentGroup create(StudentGroup group) {
        group.persist();
        return group;
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public StudentGroup update(@PathParam("id") Long id, StudentGroup updated) {
        StudentGroup group = StudentGroup.findById(id);
        if (group == null) {
            throw new jakarta.ws.rs.NotFoundException("Student group not found: " + id);
        }
        // Only update non-null fields (partial update pattern)
        if (updated.name != null) {
            group.name = updated.name;
        }
        if (updated.size > 0) {
            group.size = updated.size;
        }
        // TASK-272: Update per-group professionalizzanti during stage setting
        group.allowProfessionalizzantiDuringStage = updated.allowProfessionalizzantiDuringStage;
        // startDate can be explicitly set to null to clear it, so we always update it
        group.startDate = updated.startDate;
        // TASK-358 code review (N1): null-keeps semantics, not null-clears. Several
        // admin forms (StudentGroupsAdmin: name/size/allowProf only,
        // SchoolSettingsAdmin: startDate only) send partial payloads that omit
        // maxHoursPerDay entirely, which deserializes to null - unconditionally
        // copying it here silently wiped the cap on every ordinary edit. A missing
        // (null) value now means "leave the existing cap alone"; explicit clearing
        // needs a dedicated affordance (not yet built) rather than overloading null.
        if (updated.maxHoursPerDay != null) {
            if (updated.maxHoursPerDay < 1 || updated.maxHoursPerDay > TimeslotSequence.SLOT_START_TIMES.length) {
                throw new BadRequestException("maxHoursPerDay must be between 1 and "
                        + TimeslotSequence.SLOT_START_TIMES.length);
            }
            group.maxHoursPerDay = updated.maxHoursPerDay;
        }
        return group;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        StudentGroup.deleteById(id);
    }
}
