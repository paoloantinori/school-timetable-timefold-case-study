package com.school.planner.rest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.logging.Logger;

import com.school.planner.domain.Course;
import com.school.planner.domain.LabRotation;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.service.ExpectedDaysCalculator;

@Path("/api/lab-rotations")
@Produces(MediaType.APPLICATION_JSON)
public class LabRotationResource {

    private static final Logger LOG = Logger.getLogger(LabRotationResource.class);

    @GET
    public List<LabRotation> list() {
        return LabRotation.listAll();
    }

    @POST
    @Transactional
    public LabRotation create(LabRotationRequest request) {
        LabRotation rotation = new LabRotation();
        if (request.teacherId != null) {
            rotation.teacher = Teacher.findById(request.teacherId);
        }
        if (request.studentGroupId != null) {
            rotation.studentGroup = StudentGroup.findById(request.studentGroupId);
        }
        rotation.dayOfWeek = request.dayOfWeek;
        rotation.slotStart = request.slotStart;
        rotation.blockSize = request.blockSize;
        rotation.periodStart = request.periodStart;
        rotation.periodEnd = request.periodEnd;
        rotation.roomTag = request.roomTag;
        rotation.persist();
        return rotation;
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public LabRotation update(@PathParam("id") Long id, LabRotationRequest request) {
        LabRotation rotation = LabRotation.findById(id);
        if (rotation == null) {
            throw new jakarta.ws.rs.NotFoundException("LabRotation not found: " + id);
        }

        // Check if period dates are changing (triggers recalculation)
        boolean periodChanged = false;
        LocalDate oldPeriodStart = rotation.periodStart;
        LocalDate oldPeriodEnd = rotation.periodEnd;

        if (request.teacherId != null) {
            rotation.teacher = Teacher.findById(request.teacherId);
        }
        if (request.studentGroupId != null) {
            rotation.studentGroup = StudentGroup.findById(request.studentGroupId);
        }
        if (request.dayOfWeek != null) {
            rotation.dayOfWeek = request.dayOfWeek;
        }
        rotation.slotStart = request.slotStart;
        rotation.blockSize = request.blockSize;
        if (request.periodStart != null) {
            rotation.periodStart = request.periodStart;
            periodChanged = periodChanged || !rotation.periodStart.equals(oldPeriodStart);
        }
        if (request.periodEnd != null) {
            rotation.periodEnd = request.periodEnd;
            periodChanged = periodChanged || !rotation.periodEnd.equals(oldPeriodEnd);
        }
        if (request.roomTag != null) {
            rotation.roomTag = request.roomTag;
        }

        rotation.persist();

        // Recalculate expectedLabDaysPerWeek for affected courses if period changed
        if (periodChanged) {
            recalculateExpectedDaysForRotation(rotation);
        }

        return rotation;
    }

    /**
     * Recalculates expectedLabDaysPerWeek for all courses affected by this rotation.
     * Called when rotation period dates change.
     */
    private void recalculateExpectedDaysForRotation(LabRotation rotation) {
        // Find all lab courses for this teacher + group
        List<Course> courses = Course.find(
                "teacher = ?1 and studentGroup = ?2 and isLab = true",
                rotation.teacher, rotation.studentGroup).list();

        int updatedCount = 0;
        for (Course course : courses) {
            if (ExpectedDaysCalculator.updateCourseExpectedDays(course, rotation)) {
                updatedCount++;
            }
        }

        LOG.infof("Recalculated expectedLabDaysPerWeek for %d courses after LabRotation %d period change",
            updatedCount, rotation.id);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        LabRotation.deleteById(id);
    }

    public static class LabRotationRequest {
        public Long teacherId;
        public Long studentGroupId;
        public DayOfWeek dayOfWeek;
        public int slotStart;
        public int blockSize;
        public LocalDate periodStart;
        public LocalDate periodEnd;
        public String roomTag;
    }
}
