package com.school.planner.rest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.vertx.ext.web.RoutingContext;

import org.jboss.logging.Logger;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;

import com.school.planner.domain.SchoolSchedule;
import com.school.planner.exception.StagedSolveInProgressException;
import com.school.planner.rest.dto.FeasibilityReport;
import com.school.planner.rest.dto.LessonViolationAnalysis;
import com.school.planner.service.CalendarioImportReport;
import com.school.planner.service.CalendarioImportService;
import com.school.planner.service.GoldenImportReport;
import com.school.planner.service.GoldenScheduleImportService;
import com.school.planner.service.ScheduleService;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.util.ConstraintIds;

@Path("/api/schedule")
@Produces(MediaType.APPLICATION_JSON)
public class ScheduleResource {

    private static final Logger LOG = Logger.getLogger(ScheduleResource.class);

    @Inject
    ScheduleService scheduleService;

    @Inject
    GoldenScheduleImportService goldenScheduleImportService;

    @Inject
    CalendarioImportService calendarioImportService;

    @GET
    public SchoolSchedule getSchedule() {
        return scheduleService.getScheduleWithStatus();
    }

    /**
     * Lightweight status poll (TASK-380): everything the UI needs to drive
     * buttons/toasts/score displays, from in-memory state only - NO
     * loadSchedule, NO DB round-trip. The full GET /api/schedule costs ~9s on
     * the 2026/27 dataset (16MB), which made 2s/500ms polling pile up and lag
     * the UI by minutes (the root cause of the TASK-367 toast failures).
     */
    @GET
    @Path("/status")
    public Map<String, Object> getStatusLight() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        var status = scheduleService.getStatus();
        out.put("solverStatus", status.name());
        var score = scheduleService.getLatestScore();
        // MILESTONE-2 EXTERNAL COMPAT (TASK-538): BendableScore string format is
        // "[h/h]hard/[m/s]soft" (was "hhard/mmedium/ssoft"); external consumers of this
        // field (api.sh score, UI parsers, KPI scripts) are milestone-2 scope.
        out.put("score", score != null ? score.toString() : null);
        out.put("lastSolverError", scheduleService.getLastSolverError());
        var hybridStatus = com.school.planner.service.HybridPhase1aService.getCurrentStatus();
        out.put("preAssignmentStatus", scheduleService.mapHybridStatusToPreAssignmentStatus(
                hybridStatus, com.school.planner.service.HybridPhase1aService.getCurrentPhaseStatic()).name());
        out.put("preAssignmentError", com.school.planner.service.HybridPhase1aService.getLastErrorStatic());
        out.put("currentPhase", com.school.planner.solver.PhaseTrackingListener.getCurrentPhase());
        out.put("currentPhaseName", com.school.planner.solver.PhaseTrackingListener.getPhaseName());
        // TASK-419: null unless a staged solve is running. EVERY stage of the cycle runs on an
        // ad-hoc solver, so solverStatus reports NOT_SOLVING throughout and cannot report them.
        out.put("stagedStage", scheduleService.getStagedStage());
        // TASK-483: multi-start attempt X-of-Y (both 0 when off, so the UI hides the indicator).
        out.put("stagedAttempt", scheduleService.getStagedAttempt());
        out.put("stagedMaxAttempts", scheduleService.getStagedMaxAttempts());
        // TASK-509 follow-up: the delivery refinement (corner-b) runs on an ad-hoc solver
        // after the narration closed: drivers must ALSO wait this out (run3 was killed
        // mid-refinement). Additive field: existing consumers ignore what they don't know.
        out.put("refiningForDelivery", scheduleService.isRefiningForDelivery());
        out.put("stagedBudgetSeconds", scheduleService.getStagedBudgetSeconds());
        // TASK-667: the authoritative cycle outcome (null while running) and the
        // post-restart score-restore in-flight flag - both consumed by the UI
        // instead of its stage-edge inferences.
        var stagedOutcome = scheduleService.getStagedOutcome();
        out.put("stagedOutcome", stagedOutcome == null ? null : stagedOutcome.name());
        out.put("scoreRestorePending", scheduleService.isScoreRestorePending());
        return out;
    }

    @POST
    @Path("/solve")
    public Response solve() {
        try {
            scheduleService.startSolving();
            return Response.accepted().build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * TASK-419 staged solve: construction -&gt; promote the template cells to pins -&gt;
     * the pipeline's TIMESLOT phases on the tail+box -&gt; selective demotion of blocking pins
     * -&gt; release -&gt; the pipeline's ROOM phases. The release comes BEFORE the room phases
     * because a pin freezes the whole entity, room included: held through them, the promoted
     * pins would keep the construction's provisional rooms (H16b). Returns 202 immediately like
     * {@code POST /solve}; progress is the {@code stagedStage} field of {@code GET
     * /api/schedule/status} (see {@code ScheduleService}'s stage constants).
     */
    @POST
    @Path("/solve-staged")
    public Response solveStaged() {
        try {
            scheduleService.startStagedSolving();
            return Response.accepted(Map.of("message", "Staged solve started")).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }


    /**
     * TASK-542 checkpoint resume: continue a staged cycle from a boundary checkpoint captured
     * by {@code planner.checkpoint.capture.enabled}. Body: {@code {"path": "<checkpoint.json>"}}
     * (absolute, or relative to the app working directory). The grid and pin flags are restored
     * from the checkpoint, the completed stages are skipped, and the cycle runs to its end.
     * Progress is the usual {@code stagedStage} field of {@code GET /api/schedule/status}.
     */
    @POST
    @Path("/solve-staged-resume")
    public Response solveStagedResume(@QueryParam("path") String path) {
        if (path == null || path.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "query param 'path' (checkpoint json) is required"))
                    .build();
        }
        try {
            // two service calls ON PURPOSE: the restore transaction commits between them, so
            // the cycle thread (started by the second) can only ever see the restored grid
            scheduleService.startResumeCycle(
                    scheduleService.applyStagedCheckpoint(java.nio.file.Path.of(path)));
            return Response.accepted(Map.of("message", "Staged resume started", "path", path)).build();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/solve")
    public Response stopSolving() {
        // Terminate asynchronously - don't wait for solver to acknowledge
        new Thread(() -> {
            try {
                scheduleService.stopSolving();
            } catch (Exception e) {
                LOG.error("Error stopping solver", e);
            }
        }).start();
        return Response.accepted(Map.of("message", "Stopping solver...")).build();
    }

    @POST
    @Path("/reset")
    public Response resetSchedule(@Context RoutingContext ctx, @HeaderParam("User-Agent") String userAgent) {
        // TASK-383 attribution: the reset is destructive (unpins/unassigns
        // every lesson - see ScheduleService.resetSchedule). Log WHO/WHAT
        // triggered it so a stray click or errant caller (the 2026-07-20
        // vanishing-pins incident) is attributable in the log. The
        // User-Agent distinguishes a Playwright run from a browser from curl.
        String remote = ctx != null && ctx.request() != null && ctx.request().remoteAddress() != null
                ? ctx.request().remoteAddress().hostAddress() : "unknown";
        LOG.warnf("POST /api/schedule/reset from %s (User-Agent: %s) - DESTRUCTIVE, clears all pins/assignments",
                remote, userAgent != null ? userAgent : "unknown");
        scheduleService.resetSchedule();
        return Response.ok(Map.of("message", "Schedule reset to initial state")).build();
    }

    @GET
    @Path("/analysis")
    public Response getScoreAnalysis(@QueryParam("all") @DefaultValue("false") boolean includeAll) {
        ScheduleService.ConstraintMatchSnapshot snapshot = scheduleService.analyzeScore();

        List<Map<String, Object>> constraints = new ArrayList<>();
        snapshot.constraintMap().forEach((ref, total) -> {
            BendableScore score = (BendableScore) total.getScore();
            // Skip constraints with zero score unless ?all=true is requested
            if (!includeAll && score.equals(ScoreCompat.ZERO)) {
                return;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("constraint", ConstraintIds.shortName(ref.id()));
            entry.put("score", score.toString());
            entry.put("hard", ScoreCompat.hardOf(score));
            entry.put("quasi", ScoreCompat.quasiOf(score));
            entry.put("medium", ScoreCompat.mediumOf(score));
            entry.put("soft", ScoreCompat.softOf(score));
            entry.put("matches", total.getConstraintMatchCount());
            constraints.add(entry);
        });

        // Sort: hard violations first (most negative), then quasi, then medium, then soft
        // (the score's own level order). Timefold 2.x: score levels are long fields, so the
        // boxed map values are Long.
        constraints.sort(Comparator.<Map<String, Object>, Long>comparing(m -> (Long) m.get("hard"))
                .thenComparing(m -> (Long) m.get("quasi"))
                .thenComparing(m -> (Long) m.get("medium"))
                .thenComparing(m -> (Long) m.get("soft")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", snapshot.score().toString());
        result.put("hard", ScoreCompat.hardOf(snapshot.score()));
        result.put("quasi", ScoreCompat.quasiOf(snapshot.score()));
        result.put("medium", ScoreCompat.mediumOf(snapshot.score()));
        result.put("soft", ScoreCompat.softOf(snapshot.score()));
        result.put("constraints", constraints);

        return Response.ok(result).build();
    }


    /**
     * Get per-entity violation analysis for Lessons and LabBlocks within a date range.
     * Returns only entities that have violations (indictments).
     * Returns 409 CONFLICT if solver is actively running.
     *
     * @param startDate Start of date range (inclusive), defaults to 1900-01-01
     * @param endDate End of date range (inclusive), defaults to 2100-12-31
     * @param constraintId Optional constraint ID filter (e.g., "H1.1"). If provided, only returns violations for this constraint.
     * @param lang Language for violation messages (en, it). Defaults to "en".
     */
    @GET
    @Path("/analysis/lessons")
    public Response getLessonAnalysis(
            @QueryParam("startDate") LocalDate startDate,
            @QueryParam("endDate") LocalDate endDate,
            @QueryParam("constraint") String constraintId,
            @QueryParam("lang") String lang) {

        if (scheduleService.getStatus() == ai.timefold.solver.core.api.solver.SolverStatus.SOLVING_ACTIVE) {
            return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "Cannot analyze while solving"))
                .build();
        }

        // Use defaults if not provided (backward compatible)
        LocalDate start = startDate != null ? startDate : LocalDate.of(1900, 1, 1);
        LocalDate end = endDate != null ? endDate : LocalDate.of(2100, 12, 31);

        List<LessonViolationAnalysis> analysis = scheduleService.analyzeLessonViolations(start, end, constraintId, lang);
        return Response.ok(analysis).build();
    }

    /**
     * Check if the current solution is feasible: hard constraints satisfied
     * (both hard levels clean, pure and quasi). 0-hard is binary by house
     * directive, so this is the ONE verdict the UI surfaces; the medium/soft
     * values ride along as quality context, never as verdict. (TASK-660
     * dropped the old medium threshold; the rationale lives in rest-api.md.)
     *
     * The message field carries a STABLE CODE (no_solution, not_scored,
     * hard_violated, feasible) that the frontend localizes.
     */
    @GET
    @Path("/feasibility")
    public FeasibilityReport getFeasibility() {
        SchoolSchedule schedule = scheduleService.getScheduleWithStatus();
        if (schedule == null) {
            return new FeasibilityReport(false, "no_solution", null, null, null, null);
        }

        BendableScore score = schedule.getScore();
        if (score == null) {
            return new FeasibilityReport(false, "not_scored", null, null, null, null);
        }

        // Both hard levels must be clean (pure AND quasi): with the quasi tier
        // armed, level-1 damage (H34/H32) is hard damage too, and a verdict that
        // ignores it would call a wounded delivery valid.
        boolean isHardFeasible = ScoreCompat.hardLevels(score).equals(ScoreCompat.ZERO_HARD_LEVELS);
        if (!isHardFeasible) {
            return new FeasibilityReport(false, "hard_violated",
                    score.toString(), ScoreCompat.hardOf(score), ScoreCompat.mediumOf(score), ScoreCompat.softOf(score));
        }

        // No constraint-match breakdown here: the verdict needs only the score, and a
        // full match recompute costs minutes on this dataset (the old medium gate
        // early-returned before it on every real delivery). The breakdown lives in
        // GET /analysis, which the UI already fetches on explicit request (cached).
        return new FeasibilityReport(true, "feasible",
                score.toString(), ScoreCompat.hardOf(score), ScoreCompat.mediumOf(score), ScoreCompat.softOf(score));
    }

    @POST
    @Path("/reschedule")
    public Response reschedule(RescheduleRequest request) {
        scheduleService.reactiveReschedule(request.teacherId, request.date);
        return Response.accepted().build();
    }

    /**
     * Export the complete schedule state as JSON.
     * Used for A/B testing solver configurations with identical starting conditions.
     *
     * <p>Refuses (409) while a TASK-419 staged solve is running: mid-cycle the payload would
     * carry the cycle's own promoted template pins as ordinary {@code pinned} lessons, and
     * re-importing it would turn them into authoritative ones.
     */
    @GET
    @Path("/export")
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportSchedule() {
        try {
            SchoolSchedule schedule = scheduleService.getFullScheduleForExport();
            return Response.ok(schedule).build();
        } catch (IllegalStateException e) {
            return conflictError("Export", e.getMessage());
        }
    }

    /**
     * Import a complete schedule state from JSON.
     * Replaces all existing entities with the imported data in a single transaction.
     * Used for A/B testing solver configurations with identical starting conditions.
     *
     * <p>Refuses (409) while a TASK-419 staged solve is running - it would delete and
     * re-insert the very lessons the cycle is accounting for.
     */
    @POST
    @Path("/import")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response importSchedule(SchoolSchedule schedule) {
        try {
            LOG.infof("Import endpoint called with schedule: %s", schedule != null ? "non-null" : "null");
            scheduleService.importSchedule(schedule);
            return Response.ok(Map.of("message", "Schedule imported successfully")).build();
        } catch (StagedSolveInProgressException e) {
            return conflictError("Import", e.getMessage());
        } catch (IllegalStateException e) {
            LOG.error("Import failed (IllegalStateException): " + e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.error("Import failed (Exception): " + e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Import failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Import the hand-made golden 2026/27 timetable
     * (src/main/resources/seed/lessons.csv) as pinned Lessons (TASK-346).
     * Refuses (409) while the solver is running, and refuses (409) if any
     * Lesson already exists unless force=true, in which case ALL existing
     * Lessons (pinned and unpinned) are deleted first. Intended for a
     * dedicated, otherwise-empty profile (e.g. "golden") - see claudedocs/scripts.md.
     */
    @POST
    @Path("/import-golden")
    public Response importGolden(@QueryParam("force") @DefaultValue("false") boolean force) {
        try {
            GoldenImportReport report = goldenScheduleImportService.importGolden(force);
            return Response.ok(report).build();
        } catch (IllegalStateException e) {
            return conflictError("Golden import", e.getMessage());
        } catch (Exception e) {
            return internalError("Golden import", e.getMessage(), e);
        }
    }

    /**
     * Import the school's pre-set "Calendario scolastico 2026/27" (TASK-364)
     * as pinned MODA lab Lessons, plus report-only FC/class-days coherence
     * cross-checks - see {@code claudedocs/rest-api.md} and
     * {@link com.school.planner.service.CalendarioImportService}'s javadoc.
     *
     * <p>Always returns 200 with the full report when {@code dryRun=true}
     * (nothing is ever persisted). When {@code dryRun=false}: 200 if every
     * row validated cleanly (Lessons are persisted, {@code report.persisted}
     * is true), or 409 with the same full report body if any row failed
     * validation (all-or-nothing - nothing is persisted, {@code
     * report.persisted} is false). Refuses (409, no report body) while the
     * solver is running, mirroring {@code import-golden}; also refuses (409,
     * no report body) if pinned Lessons already exist for the MODA target
     * courses from a previous call, unless {@code force=true} (TASK-364 code
     * review K2), which clears and restores them before re-importing.</p>
     */
    @POST
    @Path("/import-calendario")
    public Response importCalendario(@QueryParam("dryRun") @DefaultValue("false") boolean dryRun,
                                     @QueryParam("force") @DefaultValue("false") boolean force) {
        try {
            CalendarioImportReport report = calendarioImportService.importCalendario(dryRun, force);
            if (!dryRun && !report.persisted) {
                LOG.warnf("Calendario import refused (validation failures): %d moda failure(s)", report.modaFailures.size());
                return Response.status(Response.Status.CONFLICT).entity(report).build();
            }
            return Response.ok(report).build();
        } catch (IllegalStateException e) {
            return conflictError("Calendario import", e.getMessage());
        } catch (Exception e) {
            return internalError("Calendario import", e.getMessage(), e);
        }
    }

    /**
     * Shared 409 error-response helper (TASK-364 code review K7) - both
     * {@code import-golden} and {@code import-calendario} refuse the same way
     * (via {@link IllegalStateException}) while the solver is running or when
     * a force-guarded precondition isn't met.
     */
    private Response conflictError(String actionLabel, String message) {
        LOG.warnf("%s refused: %s", actionLabel, message);
        return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", message))
                .build();
    }

    /** Shared 500 error-response helper (TASK-364 code review K7), paired with {@link #conflictError}. */
    private Response internalError(String actionLabel, String message, Exception cause) {
        LOG.error(actionLabel + " failed: " + message, cause);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", actionLabel + " failed: " + message))
                .build();
    }

    public static class RescheduleRequest {
        public Long teacherId;
        public LocalDate date;
    }

    // ─── Compaction-Only Mode Endpoints ──────────────────────────────────────

    /**
     * Set the compaction-only mode.
     * When enabled, ONLY H1, H2, H3, M13, M20 constraints are active.
     * This tests pure compaction behavior without other constraints interfering.
     *
     * Expected behavior: Calendar fills from September onwards, constrained
     * only by teacher/room/group conflicts and compaction preferences.
     *
     * @param enabled true to enable compaction-only mode, false to restore normal operation
     * @return Confirmation of the new mode state
     */
    @POST
    @Path("/constraints/compaction-only-mode")
    public Response setCompactionOnlyMode(@QueryParam("enabled") boolean enabled) {
        SchoolScheduleConstraintProvider.setCompactionOnlyMode(enabled);
        LOG.infof("Compaction-only mode set to: %s", enabled);
        return Response.ok()
            .entity(Map.of("compactionOnlyMode", enabled))
            .build();
    }

    /**
     * Get the current compaction-only mode state.
     *
     * @return Current state of compaction-only mode
     */
    @GET
    @Path("/constraints/compaction-only-mode")
    public Response getCompactionOnlyMode() {
        boolean enabled = SchoolScheduleConstraintProvider.isCompactionOnlyMode();
        return Response.ok()
            .entity(Map.of("compactionOnlyMode", enabled))
            .build();
    }

    // ─── Debug Mode Endpoints ────────────────────────────────────────────────

    /**
     * Set the debug constraints mode.
     * When enabled, ALL constraints are disabled for pure CH testing.
     * The resulting schedule will have zero score (0hard/0medium/0soft) but
     * may have conflicts (teacher double-booked, room double-booked, etc.).
     *
     * @param enabled true to disable all constraints, false to restore normal operation
     * @return Confirmation of the new debug mode state
     */
    @POST
    @Path("/constraints/debug-mode")
    public Response setDebugMode(@QueryParam("enabled") boolean enabled) {
        SchoolScheduleConstraintProvider.setDebugConstraintsDisabled(enabled);
        LOG.infof("Debug constraints mode set to: %s", enabled);
        return Response.ok()
            .entity(Map.of("debugConstraintsDisabled", enabled))
            .build();
    }

    /**
     * Get the current debug mode state.
     *
     * @return Current state of debug constraints mode
     */
    @GET
    @Path("/constraints/debug-mode")
    public Response getDebugMode() {
        boolean enabled = SchoolScheduleConstraintProvider.isDebugConstraintsDisabled();
        return Response.ok()
            .entity(Map.of("debugConstraintsDisabled", enabled))
            .build();
    }
}
