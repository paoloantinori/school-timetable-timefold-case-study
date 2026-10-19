package com.school.planner.rest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import jakarta.enterprise.event.Event;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.UriInfo;

import com.school.planner.service.DataMutationEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plain-JUnit unit test (no {@code @QuarkusTest} / CDI container needed) for
 * {@link DataMutationTrackingFilter}'s exclusion logic (TASK-354 code review B2): the
 * watchdog's own validation endpoints must never re-trigger themselves, even if a
 * future edit to {@code WATCHED_PATH_PREFIXES} accidentally widens to cover them (e.g.
 * a blanket {@code "/api/schedule"} entry replacing the specific subpaths it lists today).
 *
 * <p>Request/response contexts are JDK dynamic proxies over the JAX-RS interfaces
 * (this project has no Mockito dependency) — minimal stand-ins that only answer the
 * three calls {@link DataMutationTrackingFilter#filter} actually makes.
 */
class DataMutationTrackingFilterSelfExclusionTest {

    @Test
    void validationRunEndpointDoesNotFireAMutationEvent() {
        DataMutationTrackingFilter filter = new DataMutationTrackingFilter();
        AtomicInteger fired = new AtomicInteger();
        filter.mutationEvent = fakeEvent(fired);

        filter.filter(fakeRequest("POST", "/api/schedule/validation/run"), fakeResponse(200));

        assertEquals(0, fired.get(), "POST /api/schedule/validation/run must not fire a DataMutationEvent");
    }

    @Test
    void genuinelyWatchedPathStillFires() {
        // Sanity check for the proxy fakes above: a real watched-and-not-excluded path
        // must still fire, so the assertion in the test above is actually meaningful.
        DataMutationTrackingFilter filter = new DataMutationTrackingFilter();
        AtomicInteger fired = new AtomicInteger();
        filter.mutationEvent = fakeEvent(fired);

        filter.filter(fakeRequest("POST", "/api/holidays"), fakeResponse(200));

        assertEquals(1, fired.get(), "A genuinely watched mutating path should fire exactly once");
    }

    @SuppressWarnings("unchecked")
    private static Event<DataMutationEvent> fakeEvent(AtomicInteger counter) {
        return (Event<DataMutationEvent>) Proxy.newProxyInstance(
                DataMutationTrackingFilterSelfExclusionTest.class.getClassLoader(),
                new Class<?>[]{Event.class},
                (proxy, method, args) -> {
                    if ("fire".equals(method.getName())) {
                        counter.incrementAndGet();
                    }
                    return null;
                });
    }

    private static ContainerRequestContext fakeRequest(String method, String path) {
        UriInfo uriInfo = (UriInfo) Proxy.newProxyInstance(
                DataMutationTrackingFilterSelfExclusionTest.class.getClassLoader(),
                new Class<?>[]{UriInfo.class},
                (proxy, m, args) -> "getPath".equals(m.getName()) ? path : null);

        InvocationHandler handler = (proxy, m, args) -> switch (m.getName()) {
            case "getMethod" -> method;
            case "getUriInfo" -> uriInfo;
            default -> null;
        };
        return (ContainerRequestContext) Proxy.newProxyInstance(
                DataMutationTrackingFilterSelfExclusionTest.class.getClassLoader(),
                new Class<?>[]{ContainerRequestContext.class}, handler);
    }

    private static ContainerResponseContext fakeResponse(int status) {
        return (ContainerResponseContext) Proxy.newProxyInstance(
                DataMutationTrackingFilterSelfExclusionTest.class.getClassLoader(),
                new Class<?>[]{ContainerResponseContext.class},
                (proxy, m, args) -> "getStatus".equals(m.getName()) ? status : null);
    }
}
