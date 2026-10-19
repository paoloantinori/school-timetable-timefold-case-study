package com.school.planner.rest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;

/**
 * Regression guard for the published-site CORS failure (2026-08-17): Chrome rendered a blank
 * page on https://planner.example.org/ while Firefox worked. Root cause: the Quarkus
 * CORS filter 403s any request bearing an Origin header not in {@code quarkus.http.cors.origins},
 * and that allowlist contained only the Vite dev server (localhost:3000). Chrome sends Origin
 * on same-origin module scripts (crossorigin attribute) and cors-mode CSS; Firefox omits it on
 * same-origin, which is why only Chrome broke.
 *
 * <p>This test pins the BEHAVIOR, not the property value: any change that removes the published
 * origin from the allowlist (a "cleanup" back to dev-only, a domain change without updating the
 * list) turns the public site blank for Chromium users again and must fail here.
 *
 * <p>The negative case (an unlisted origin is rejected) is pinned too: that rejection is
 * intentional filter semantics, so this test also documents that widening the list is the fix,
 * not disabling the filter.
 */
@QuarkusTest
class CorsPublishedOriginRegressionTest {

    private static final String PUBLISHED_ORIGIN = "https://planner.example.org";

    /**
     * DB-independent probe endpoint: the slot schema is static config, so its status code
     * isolates the CORS filter's decision (200 vs 403) from any QuarkusTest database state.
     * (Hitting {@code /} instead made this test order-dependent: other tests leave the shared
     * H2 in states where the index handler 500s regardless of CORS.)
     */
    private static final String PROBE_PATH = "/api/config/slots";

    @Test
    void requestsWithPublishedOriginAreServed() {
        given().header("Origin", PUBLISHED_ORIGIN)
                .when().get(PROBE_PATH)
                .then().statusCode(200);
    }

    @Test
    void unlistedOriginsAreStillRejected() {
        given().header("Origin", "https://example.attacker.test")
                .when().get(PROBE_PATH)
                .then().statusCode(403);
    }
}
