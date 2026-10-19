package com.school.planner.rest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

import static org.hamcrest.Matchers.hasKey;

/**
 * TASK-667: the light status payload carries the authoritative cycle outcome
 * and the score-restore in-flight flag. This pins their PRESENCE (and that of
 * the pre-existing cycle fields) so a future payload refactor cannot silently
 * drop them: the UI's end-of-cycle classification depends on exactly these
 * fields instead of its old stage-edge inferences. Values are deliberately
 * not pinned: QuarkusTest classes share one application context per fork, so
 * another test that drives a cycle or trips the restore latch would turn any
 * rest-value assertion into an order-dependent flake; the semantics are
 * covered by the live lane verification.
 */
@QuarkusTest
class StatusPayloadContractTest {

    @Test
    void statusPayloadCarriesOutcomeAndRestoreFlag() {
        RestAssured.given()
                .when().get("/api/schedule/status")
                .then()
                .statusCode(200)
                .body("$", hasKey("stagedOutcome"))
                .body("$", hasKey("scoreRestorePending"))
                .body("$", hasKey("stagedStage"))
                .body("$", hasKey("refiningForDelivery"));
    }
}
