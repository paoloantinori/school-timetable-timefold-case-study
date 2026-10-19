package com.school.planner.rest;

import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import com.school.planner.service.DataMutationEvent;

/**
 * Fires a {@link DataMutationEvent} after any successful mutating (POST/PUT/DELETE/PATCH)
 * request to a constraint-relevant CRUD endpoint (TASK-354).
 *
 * <p>Least-invasive trigger pattern: a single response filter matching on method + path
 * prefix, instead of instrumenting every CRUD resource individually. Observed by
 * {@code ValidationService}, which debounces bursts into a single re-validation run.
 *
 * <p><b>Solve completion (code review B2):</b> this filter only sees HTTP request/response
 * pairs, so it cannot observe "the solver finished" — that's not a CRUD mutation, and by
 * the time a client's {@code POST /api/schedule/solve} response comes back the solve has
 * usually barely started. Solve completion is instead pushed from {@code
 * ScheduleService.saveFinalSolution}, the one clean hook reached whether the solver
 * stopped naturally or was stopped early (see that method's javadoc).
 *
 * <p><b>Exhaustiveness:</b> {@code DataMutationWatchedPathExhaustivenessTest} scans every
 * {@code com.school.planner.rest} class for POST/PUT/DELETE/PATCH-annotated methods and
 * asserts each one's full path is covered by {@link #WATCHED_PATH_PREFIXES} or explicitly
 * excluded via {@link #WATCHED_EXCLUSIONS} — so a newly added mutating endpoint can't
 * silently slip past the watchdog. Both lists are package-visible for that test.
 */
@Provider
public class DataMutationTrackingFilter implements ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(DataMutationTrackingFilter.class);

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    /**
     * Path prefixes for entities that affect constraint evaluation (TASK-354 scope).
     *
     * <p><b>Maintenance note:</b> this is a hand-maintained list, not derived from
     * anything (no such registry of "constraint-relevant entities" exists elsewhere in
     * the codebase). Adding a new CRUD resource whose data feeds the constraint provider
     * (e.g. a new domain entity referenced by {@code SchoolScheduleConstraintProvider})
     * requires adding its path prefix here too, or the watchdog silently won't notice
     * mutations to it. {@code DataMutationWatchedPathExhaustivenessTest} catches this for
     * anything under {@code com.school.planner.rest} at test time.
     */
    static final List<String> WATCHED_PATH_PREFIXES = List.of(
            "/api/teachers",
            "/api/teacher-availability",
            "/api/recurring-availability-rules",
            "/api/holidays",
            "/api/stage-patterns",
            "/api/group-calendar-rules",
            "/api/courses",
            "/api/rooms",
            "/api/student-groups",
            "/api/lessons",
            "/api/import",
            "/api/lab-assistants",
            "/api/professionalizzanti-days",
            "/api/schedule/reset",
            "/api/schedule/import-golden",
            "/api/schedule/import-calendario"
    );

    /**
     * Path prefixes deliberately NOT watched, with the reason each is not
     * constraint-relevant enough (or timed wrongly) to trigger the watchdog. Checked
     * before {@link #WATCHED_PATH_PREFIXES} so an exclusion always wins, even if a
     * future edit to the watched list accidentally widens to cover one of these too
     * (e.g. a blanket {@code "/api/schedule"} entry).
     */
    static final List<String> WATCHED_EXCLUSIONS = List.of(
            // Self-exclusion (code review B2): the watchdog's own force-validate
            // endpoint must never re-trigger itself.
            "/api/schedule/validation",
            // Solver start/stop: legality is tracked live via the solver's own
            // best-solution score callbacks, and solve completion fires its own
            // dedicated "solve-completed" trigger (ScheduleService.saveFinalSolution)
            // rather than routing through this generic path-based filter.
            "/api/schedule/solve",
            // Mutates Lesson assignments but synchronously calls startSolving() before
            // the response returns - the solver is already busy by the time this
            // filter runs, so ValidationService.isBusy() would skip anyway; the
            // eventual result is captured by the solve-completion hook above.
            "/api/schedule/reschedule",
            // Whole-schedule JSON replace used only for A/B-testing solver
            // configurations with identical starting conditions (see its javadoc) -
            // a developer/testing tool, not part of the school's real data-entry
            // workflow.
            "/api/schedule/import",
            // Starts an async CSP pipeline that pins Lessons directly from a
            // background thread (bypassing this filter entirely); the initiating
            // request itself only flips in-memory status flags, not
            // constraint-relevant entities.
            "/api/schedule/pre-assign",
            // Debug/dev-only constraint-provider mode toggles (disable or limit
            // active constraints for solver testing) - running the watchdog against
            // an artificially altered constraint set would produce misleading
            // legality signals unrelated to real data mutations.
            "/api/schedule/constraints",
            // LabRotation defines weekly patterns consumed only by the pre-assignment
            // CSP pipeline (HybridPhase1aService), not read by the constraint provider
            // directly. Mutating it doesn't change the legality of already-scheduled
            // Lessons until pre-assignment re-runs and re-pins them (which mutates
            // Lessons, already watched above).
            "/api/lab-rotations"
    );

    @Inject
    Event<DataMutationEvent> mutationEvent;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String method = requestContext.getMethod();
        if (!MUTATING_METHODS.contains(method)) return;

        int status = responseContext.getStatus();
        if (status < 200 || status >= 300) return;

        String path = requestContext.getUriInfo().getPath();
        String normalized = path.startsWith("/") ? path : "/" + path;

        if (WATCHED_EXCLUSIONS.stream().anyMatch(normalized::startsWith)) return;

        boolean watched = WATCHED_PATH_PREFIXES.stream().anyMatch(normalized::startsWith);
        if (!watched) return;

        LOG.debugf("DataMutationTrackingFilter: firing mutation event for %s %s", method, normalized);
        mutationEvent.fire(new DataMutationEvent(normalized, method));
    }
}
