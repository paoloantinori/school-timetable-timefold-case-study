package com.school.planner.rest;

import com.school.planner.service.PhaseProgressionService;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * REST endpoint for solver phase progression data.
 * Provides historical phase timing and score information.
 */
@Path("/api/phase-progression")
@Produces(MediaType.APPLICATION_JSON)
public class PhaseProgressionResource {

    @Inject
    PhaseProgressionService progressionService;

    /**
     * Get all phase records (completed + current).
     */
    @GET
    public java.util.List<PhaseProgressionService.PhaseRecord> getProgression() {
        return progressionService.getProgression();
    }

    /**
     * Get solver timeout configuration.
     * Returns overall time limit for the solver run.
     */
    @GET
    @Path("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("overallSecondsSpentLimit", progressionService.getOverallSecondsSpentLimit());
        return config;
    }

    /**
     * Export phase progression as JSON file download.
     */
    @GET
    @Path("/export")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response exportProgression() {
        String json = progressionService.exportJson();
        String filename = "phase-progression-" + java.time.Instant.now().toString().replace(":", "-") + ".json";
        return Response.ok(json)
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .build();
    }
}
