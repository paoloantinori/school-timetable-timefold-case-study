package com.school.planner.rest;

import com.school.planner.rest.dto.CourseLessonSummary;
import com.school.planner.rest.dto.ReconciliationReport;
import com.school.planner.service.ReconciliationService;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST endpoint for the reconciliation dashboard (TASK-353): replicates the
 * school's "Classi" Excel sheet, comparing assigned hours (Course.totalHours)
 * against scheduled hours (lessons with a placed timeslot) per
 * (teacher, subject, group), plus per-teacher/per-group rollups and a global
 * summary.
 */
@Path("/api/reconciliation")
@Produces(MediaType.APPLICATION_JSON)
public class ReconciliationResource {

    @Inject
    ReconciliationService reconciliationService;

    @GET
    public Response getReconciliation() {
        ReconciliationReport report = reconciliationService.buildReport();
        return Response.ok(report).build();
    }

    /**
     * Drill-down: the scheduled lessons behind a single Course row. Kept as
     * a separate endpoint so the main report payload stays light.
     */
    @GET
    @Path("/{courseId}/lessons")
    public Response getLessonsForCourse(@PathParam("courseId") Long courseId) {
        List<CourseLessonSummary> lessons = reconciliationService.getLessonsForCourse(courseId);
        return Response.ok(lessons).build();
    }
}
