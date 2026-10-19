package com.school.planner.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the StudentGroup CRUD REST API, focused on the
 * maxHoursPerDay persistence fix (TASK-358) and its code-review follow-up
 * (N1): {@code update()} originally never copied {@code updated.maxHoursPerDay}
 * onto the managed entity at all, then a first fix unconditionally copied it -
 * which wiped the cap on every partial-payload edit (StudentGroupsAdmin sends
 * name/size/allowProf only; SchoolSettingsAdmin sends startDate only, both
 * omitting maxHoursPerDay so it deserializes to null). The current semantics:
 * a null/omitted maxHoursPerDay in the PUT body means "leave the existing cap
 * alone", not "clear it".
 */
@QuarkusTest
class StudentGroupResourceTest {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        clearTables();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        clearTables();
    }

    private void clearTables() {
        // Full FK chain (Lesson -> Course -> StudentGroup), like TeacherResourceTest -
        // other resource tests can leave Course/Lesson rows referencing a StudentGroup,
        // and this class isn't guaranteed to run before them.
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
    }

    @Test
    void testUpdateMaxHoursPerDayPersists() {
        Long id = given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"G03\",\"size\":20}")
            .when()
            .post("/api/student-groups")
            .then()
            .statusCode(200)
            .extract().jsonPath().getLong("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"G03\",\"size\":20,\"maxHoursPerDay\":5}")
            .when()
            .put("/api/student-groups/" + id)
            .then()
            .statusCode(200)
            .body("maxHoursPerDay", equalTo(5));

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/student-groups")
            .then()
            .statusCode(200)
            .body("find { it.id == " + id + " }.maxHoursPerDay", equalTo(5));

        // An explicit null in the payload means "leave it alone", not "clear it" (N1) -
        // the cap must survive.
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"G03\",\"size\":20,\"maxHoursPerDay\":null}")
            .when()
            .put("/api/student-groups/" + id)
            .then()
            .statusCode(200)
            .body("maxHoursPerDay", equalTo(5));
    }

    @Test
    void testPartialUpdateDoesNotWipeMaxHoursPerDay() {
        // Reproduces the N1 bug: a name-only payload (like StudentGroupsAdmin.tsx
        // sends) must not silently null out a previously-set cap.
        Long id = given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"G10\",\"size\":18,\"maxHoursPerDay\":5}")
            .when()
            .post("/api/student-groups")
            .then()
            .statusCode(200)
            .extract().jsonPath().getLong("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"G10 (renamed)\",\"size\":18}")
            .when()
            .put("/api/student-groups/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("G10 (renamed)"))
            .body("maxHoursPerDay", equalTo(5));
    }

    @Test
    void testUpdateRejectsMaxHoursPerDayOutOfRange() {
        Long id = given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"G12\",\"size\":18}")
            .when()
            .post("/api/student-groups")
            .then()
            .statusCode(200)
            .extract().jsonPath().getLong("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"G12\",\"size\":18,\"maxHoursPerDay\":0}")
            .when()
            .put("/api/student-groups/" + id)
            .then()
            .statusCode(400);

        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"G12\",\"size\":18,\"maxHoursPerDay\":9}")
            .when()
            .put("/api/student-groups/" + id)
            .then()
            .statusCode(400);
    }
}
