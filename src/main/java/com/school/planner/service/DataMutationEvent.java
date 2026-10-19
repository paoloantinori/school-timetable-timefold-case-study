package com.school.planner.service;

/**
 * CDI event fired after a successful mutating (POST/PUT/DELETE/PATCH) call to a
 * constraint-relevant CRUD endpoint (TASK-354). Fired by
 * {@code com.school.planner.rest.DataMutationTrackingFilter} and observed by
 * {@link ValidationService} to trigger a debounced solution-legality re-validation.
 *
 * <p><b>Non-HTTP triggers:</b> {@code method = "INTERNAL"} marks an event fired by
 * something other than the HTTP filter — currently only {@code
 * ScheduleService#saveFinalSolution}'s solve-completion hook. In that case {@code path}
 * is already the human-readable trigger string on its own (e.g. {@code "solve-completed"}),
 * so {@link ValidationService#onMutation} uses it verbatim instead of formatting it as
 * {@code "method path"}. A future second non-HTTP trigger source can reuse this same
 * convention rather than inventing a new one.
 *
 * @param path   the request path that was mutated (e.g. "/api/holidays/3"), or the
 *               trigger string itself when {@code method} is {@code "INTERNAL"}
 * @param method the HTTP method (e.g. "POST"), or {@code "INTERNAL"} for a non-HTTP trigger
 */
public record DataMutationEvent(String path, String method) {
}
