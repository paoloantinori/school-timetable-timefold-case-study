package com.school.planner.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.school.planner.rest.dto.ValidationResult;
import com.school.planner.service.ValidationService;

/**
 * Solution-legality watchdog endpoints (TASK-354).
 *
 * <p>The watchdog itself is triggered automatically (debounced) by
 * {@link DataMutationTrackingFilter} after mutating CRUD calls; these endpoints expose
 * the latest result and allow forcing an immediate (non-debounced) validation.
 */
@Path("/api/schedule/validation")
@Produces(MediaType.APPLICATION_JSON)
public class ValidationResource {

    @Inject
    ValidationService validationService;

    /** Latest validation snapshot, or 204 if no validation has run yet in this session/history. */
    @GET
    @Path("/latest")
    public Response getLatest() {
        ValidationResult latest = validationService.getLatest();
        if (latest == null) {
            return Response.noContent().build();
        }
        return Response.ok(latest).build();
    }

    /** Force an immediate validation, bypassing the debounce window. */
    @POST
    @Path("/run")
    public Response run() {
        ValidationResult result = validationService.runNow("manual (POST /api/schedule/validation/run)");
        return Response.ok(result).build();
    }
}
