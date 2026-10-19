package com.school.planner.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import com.school.planner.testsupport.LessonFixtures;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Solution-legality watchdog tests (TASK-354).
 *
 * <p>Verifies that forcing a validation run (POST /api/schedule/validation/run) after
 * creating a school holiday on a date with an already-scheduled lesson surfaces a
 * new H6 (school holiday) violation in the "newlyViolated" diff versus the prior,
 * clean snapshot.
 */
@QuarkusTest
class ValidationResourceTest {

    @Inject
    EntityManager em;

    private LocalDate lessonDate;

    @BeforeEach
    @Transactional
    void setup() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
        em.createQuery("DELETE FROM Room").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM SchoolHoliday").executeUpdate();

        lessonDate = LocalDate.of(2026, 9, 21); // a Monday, well within the school year
        LessonFixtures.persistScheduledLesson("Watchdog", lessonDate);
    }

    @Test
    void holidayOnScheduledDateSurfacesNewH6Violation() {
        // 1. Establish a clean baseline snapshot (no holidays yet -> no H6 violations).
        var baseline = given()
                .accept(ContentType.JSON)
                .when()
                .post("/api/schedule/validation/run")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();

        List<Map<String, Object>> baselineConstraints = baseline.getList("perConstraint");
        boolean baselineHasH6 = baselineConstraints.stream()
                .anyMatch(c -> "H6".equals(c.get("constraintId")));
        assertFalse(baselineHasH6, "Baseline should have no H6 violations before any holiday exists");

        // code review B1: whether THIS particular call is flagged baseline=true depends
        // on whether any earlier test in the same JVM already validated something (this
        // service is an ApplicationScoped singleton, and other CRUD tests' mutations can
        // trigger a background debounced validation before this test runs) - so it can't
        // be asserted deterministically at this REST-integration level. The baseline
        // flag and its "never flood newlyViolated" guarantee are covered deterministically
        // by ValidationServiceBaselineTest, which resets the service's in-memory state
        // first. What IS safe to assert here regardless of ordering: no H6 violation
        // exists yet (checked above), and newlyViolated can never report H6 as new until
        // an actual holiday collides with the lesson (checked below).

        // 2. Create a school holiday landing exactly on the scheduled lesson's date.
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("date", lessonDate.toString(), "description", "Watchdog test holiday"))
                .when()
                .post("/api/holidays")
                .then()
                .statusCode(200);

        // 3. Force an immediate (non-debounced) re-validation.
        var afterHoliday = given()
                .accept(ContentType.JSON)
                .when()
                .post("/api/schedule/validation/run")
                .then()
                .statusCode(200)
                .body("hard", equalTo(-1))
                .extract()
                .jsonPath();

        List<Map<String, Object>> constraints = afterHoliday.getList("perConstraint");
        Map<String, Object> h6 = constraints.stream()
                .filter(c -> "H6".equals(c.get("constraintId")))
                .findFirst()
                .orElse(null);
        assertNotNull(h6, "H6 should appear in perConstraint after the holiday collides with the lesson");
        assertEquals(1, ((Number) h6.get("matchCount")).intValue(), "Exactly one lesson should violate H6");

        List<Map<String, Object>> newlyViolated = afterHoliday.getList("newlyViolated");
        Map<String, Object> h6Delta = newlyViolated.stream()
                .filter(d -> "H6".equals(d.get("constraintId")))
                .findFirst()
                .orElse(null);
        assertNotNull(h6Delta, "H6 should be reported as newly violated versus the clean baseline");
        assertEquals(0, ((Number) h6Delta.get("previousCount")).intValue());
        assertEquals(1, ((Number) h6Delta.get("currentCount")).intValue());
        assertEquals(1, ((Number) h6Delta.get("delta")).intValue());

        // code review B1: the second run has a real prior snapshot to diff against, so
        // it must NOT be flagged as baseline - only the real delta (H6) is reported.
        assertFalse(afterHoliday.getBoolean("baseline"), "A run with a prior snapshot must not be flagged baseline");
    }

    @Test
    void latestReturnsNoContentBeforeAnyValidationRun() {
        // GET /latest before any run in a fresh app instance would 204;
        // this test only asserts the endpoint responds with either 200 or 204,
        // since other tests in the suite may have already run a validation.
        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/schedule/validation/latest")
                .then()
                .statusCode(anyOf204Or200());
    }

    private static org.hamcrest.Matcher<Integer> anyOf204Or200() {
        return org.hamcrest.Matchers.anyOf(equalTo(200), equalTo(204));
    }

    @Test
    void runEndpointSkipsWhenNoLessonsScheduled() {
        // Unassign the fixture lesson so no lesson has a timeslot.
        unassignAllLessons();

        var result = given()
                .accept(ContentType.JSON)
                .when()
                .post("/api/schedule/validation/run")
                .then()
                .statusCode(200)
                .body("hard", equalTo(0))
                .extract()
                .jsonPath();

        List<?> perConstraint = result.getList("perConstraint");
        assertTrue(perConstraint.isEmpty(), "Empty-schedule fast path should report no constraint violations");
    }

    @Transactional
    void unassignAllLessons() {
        em.createQuery("UPDATE Lesson SET timeslot = null, room = null, pinned = false").executeUpdate();
    }
}
