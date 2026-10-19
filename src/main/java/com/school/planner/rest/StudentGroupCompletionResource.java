package com.school.planner.rest;

import com.school.planner.domain.StudentGroup;
import com.school.planner.rest.dto.SlotUtilization;
import com.school.planner.service.StudentGroupCompletionService;
import com.school.planner.service.StudentGroupCompletionService.StudentGroupCompletionStats;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST endpoint for student group completion analysis.
 * Provides per-student-group statistics on curriculum completion, actual vs ideal end dates.
 *
 * Used for verifying compaction requirements and identifying groups that finish too early/late.
 */
@Path("/api/completion")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StudentGroupCompletionResource {

    @Inject
    StudentGroupCompletionService completionService;

    /**
     * Get completion statistics for all student groups.
     *
     * Example response:
     * <pre>
     * {
     *   "groups": [
     *     {
     *       "groupName": "G09",
     *       "totalCurriculumHours": 666,
     *       "stageDaysCount": 40,
     *       "hoursBeforeStage": 586,
     *       "hoursDuringStage": 42,
     *       "hoursAfterStage": 38,
     *       "actualDaysWithLessons": 132,
     *       "actualEndDate": "2026-06-09",
     *       "idealEndDate": "2026-04-30",
     *       "daysDifference": 40
     *     }
     *   ],
     *   "slotUtilization": [
     *     {
     *       "groupId": 1,
     *       "groupName": "G03",
     *       "totalValidSlots": 990,
     *       "assignedSlots": 950,
     *       "unassignedSlots": 40,
     *       "utilizationPercentage": 95.96
     *     }
     *   ],
     *   "totalGroups": 14
     * }
     * </pre>
     *
     * @return List of completion statistics for all student groups, including slot utilization
     */
    @GET
    public Response getAllCompletionStats() {
        List<StudentGroupCompletionStats> stats = completionService.calculateStatsForAllGroups();
        List<SlotUtilization> slotUtilization = completionService.calculateSlotUtilizationForAllGroups();

        List<Map<String, Object>> result = stats.stream()
                .map(StudentGroupCompletionStats::toMap)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("groups", result);
        response.put("slotUtilization", slotUtilization);
        response.put("totalGroups", result.size());

        return Response.ok(response).build();
    }

    /**
     * Get completion statistics for a specific student group.
     *
     * @param groupName The name of the student group (e.g., "G09", "G02")
     * @return Completion statistics for the specified group
     */
    @GET
    @Path("/{groupName}")
    public Response getCompletionStats(@PathParam("groupName") String groupName) {
        // Find the student group
        StudentGroup group = (StudentGroup) com.school.planner.domain.StudentGroup.find("name", groupName).firstResult();
        if (group == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Student group not found: " + groupName))
                    .build();
        }

        StudentGroupCompletionStats stats = completionService.calculateStats(group);

        return Response.ok(stats.toMap()).build();
    }

    /**
     * Get completion summary for a specific group.
     * Lightweight version with key metrics only.
     *
     * @param groupName The name of the student group
     * @return Summary with groupName, actualEndDate, idealEndDate, daysDifference
     */
    @GET
    @Path("/{groupName}/summary")
    public Response getCompletionSummary(@PathParam("groupName") String groupName) {
        StudentGroup group = (StudentGroup) com.school.planner.domain.StudentGroup.find("name", groupName).firstResult();
        if (group == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Student group not found: " + groupName))
                    .build();
        }

        StudentGroupCompletionStats stats = completionService.calculateStats(group);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("groupName", stats.groupName);
        summary.put("actualEndDate", stats.actualEndDate);
        summary.put("idealEndDate", stats.idealEndDate);
        summary.put("daysDifference", stats.daysDifference);
        summary.put("status", getStatusLabel(stats.daysDifference));

        return Response.ok(summary).build();
    }

    /**
     * Get a human-readable status label based on days difference.
     * Negative = finished early, 0 = on time, Positive = finished late.
     */
    private String getStatusLabel(int daysDifference) {
        if (daysDifference < -14) {
            return "FINISHED TOO EARLY (" + daysDifference + " days)";
        } else if (daysDifference < 0) {
            return "Finished early (" + daysDifference + " days)";
        } else if (daysDifference == 0) {
            return "On time";
        } else if (daysDifference <= 7) {
            return "Finished late (" + daysDifference + " days)";
        } else {
            return "FINISHED TOO LATE (" + daysDifference + " days)";
        }
    }
}
