package com.school.planner.rest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * TASK-660: the feasibility endpoint is the ONE binary verdict (hard == 0) and
 * its message field carries a STABLE CODE the frontend localizes. This pins the
 * contract shape on the empty-DB path (not_scored): a regression back to
 * English prose on this path fails here instead of surfacing as English text
 * in the school's UI. The feasible/hard_violated paths are exercised live.
 */
@QuarkusTest
class FeasibilityEndpointContractTest {

    @Test
    void emptyDatabaseReportsTheStableNotScoredCode() {
        // An empty DB still materializes a schedule shell (score null), so the
        // stable code here is not_scored; no_solution only fires if the service
        // ever hands back a null schedule.
        RestAssured.given()
                .when().get("/api/schedule/feasibility")
                .then()
                .statusCode(200)
                .body("feasible", equalTo(false))
                .body("message", equalTo("not_scored"))
                .body("score", nullValue());
    }
}
