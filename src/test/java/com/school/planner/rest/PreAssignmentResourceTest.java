package com.school.planner.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the Pre-Assignment REST API.
 * <p>
 * Tests the async pre-assignment pipeline endpoints:
 * - POST /api/schedule/pre-assign (start, returns 202)
 * - GET /api/schedule/pre-assign/status (get status)
 * - POST /api/schedule/pre-assign/reset (reset to IDLE)
 * </p>
 *
 * <p>Note: Pre-assignment with minimal data will likely complete quickly or fail gracefully
 * since it requires specific data patterns (LabRotation, StagePattern, etc.).</p>
 */
@QuarkusTest
class PreAssignmentResourceTest {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        // TASK-435 (ratified 2026-07-30): constrained-teacher is default ON, so on this empty DB
        // Phase 0 would run (0 pinned) and change the no-op message the zero-rotation test asserts.
        // Opt it out to isolate the empty-DB no-op path; @AfterEach restores the production default-on.
        System.setProperty("planner.pre-assign.constrained-teacher.enabled", "false");
        // Drain any still-executing async run from a previous test BEFORE
        // resetting. resetStatus() bumps the epoch guard, but a run that is
        // queued (not yet executing) at reset time adopts the NEW epoch when
        // it starts (HybridPhase1aService.run() reads statusEpoch at execution
        // time) and flips the status back to RUNNING - making this test's
        // POST /pre-assign return 409 instead of 202 (observed flake,
        // 2026-07-17). Any queued run shows status=RUNNING immediately
        // (runAsync sets it before submitting), so waiting for != RUNNING
        // guarantees the executor is idle.
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> !"RUNNING".equals(given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/schedule/pre-assign/status")
                .jsonPath()
                .getString("status")));

        // Clear existing data (in order respecting foreign keys)
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
        em.createQuery("DELETE FROM Room").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM LabRotation").executeUpdate();
        em.createQuery("DELETE FROM StagePattern").executeUpdate();

        // Reset the pre-assignment service to IDLE state before each test
        given()
            .when()
            .post("/api/schedule/pre-assign/reset")
            .then()
            .statusCode(204);
    }

    @AfterEach
    void restoreConstrainedTeacherDefault() {
        System.clearProperty("planner.pre-assign.constrained-teacher.enabled");
    }

    // ─── AC #2: Pre-assignment start returns 202 ───────────────────────

    @Test
    void testPreAssignStartsAndReturns202() {
        given()
            .accept(ContentType.JSON)
            .when()
            .post("/api/schedule/pre-assign")
            .then()
            .statusCode(202)
            .body("status", notNullValue());
    }

    @Test
    void testPreAssignReturns409WhenAlreadyRunning() {
        // Start first run
        given()
            .accept(ContentType.JSON)
            .when()
            .post("/api/schedule/pre-assign")
            .then()
            .statusCode(202);

        // Try to start again immediately - with empty data, pre-assignment
        // may complete very fast, so we might get 409 (RUNNING) or 202 (already COMPLETE)
        // Either response is valid depending on timing
        given()
            .accept(ContentType.JSON)
            .when()
            .post("/api/schedule/pre-assign")
            .then()
            .statusCode(anyOf(equalTo(409), equalTo(202)));
    }

    // ─── AC #3: Status endpoint and reset ──────────────────────────────

    @Test
    void testGetStatus() {
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/schedule/pre-assign/status")
            .then()
            .statusCode(200)
            .body("status", notNullValue());
    }

    @Test
    void testResetStatus() {
        given()
            .when()
            .post("/api/schedule/pre-assign/reset")
            .then()
            .statusCode(204);
    }

    @Test
    void testResetClearsRunningStatus() {
        // Start pre-assignment
        given()
            .accept(ContentType.JSON)
            .when()
            .post("/api/schedule/pre-assign")
            .then()
            .statusCode(202);

        // Verify status is RUNNING or COMPLETE (might finish fast with empty data)
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/schedule/pre-assign/status")
            .then()
            .statusCode(200)
            .body("status", anyOf(equalTo("RUNNING"), equalTo("COMPLETE"), equalTo("ERROR")));

        // Reset status
        given()
            .when()
            .post("/api/schedule/pre-assign/reset")
            .then()
            .statusCode(204);

        // Verify status is now IDLE
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/schedule/pre-assign/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("IDLE"));
    }

    // ─── TASK-367: zero-rotation no-op ──────────────────────────────────

    @Test
    void testPreAssignWithNoLabRotations_completesAsNoOpNotError() {
        // setup() already cleared LabRotation (and all other data), so this
        // run exercises the zero-rotation guard: it must report COMPLETE with
        // an informative message, never ERROR.
        given()
            .accept(ContentType.JSON)
            .when()
            .post("/api/schedule/pre-assign")
            .then()
            .statusCode(202);

        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> "COMPLETE".equals(given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/schedule/pre-assign/status")
                .jsonPath()
                .getString("status")));

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/schedule/pre-assign/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("COMPLETE"))
            .body("candidatesTried", equalTo(0))
            .body("message", containsStringIgnoringCase("nothing to pre-assign"));
    }

    // ─── Response structure tests ──────────────────────────────────────

    @Test
    void testStatusResponseHasRequiredFields() {
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/schedule/pre-assign/status")
            .then()
            .statusCode(200)
            .body("message", notNullValue())
            .body("status", notNullValue())
            .body("candidatesTried", notNullValue());
    }
}
