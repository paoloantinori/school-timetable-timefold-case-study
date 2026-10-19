package com.school.planner.rest;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for the Teacher CRUD REST API.
 * <p>
 * Tests full CRUD lifecycle: GET, POST, PUT, DELETE.
 * Covers AC #4 (full CRUD) and AC #5 (404 error case).
 * </p>
 */
@QuarkusTest
class TeacherResourceTest {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        // Clear in order respecting foreign keys: lessons -> courses -> teachers.
        // RecurringAvailabilityRule/TeacherAvailability (TASK-345) also FK to
        // teacher, so they must go before the bulk Teacher delete too - the
        // cascade tested below only fires through the REST delete() path, not
        // this raw JPQL cleanup.
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM RecurringAvailabilityRule").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
    }

    // ─── AC #4: Full CRUD operations ───────────────────────────────────

    @Test
    void testListTeachersEmpty() {
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/teachers")
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }

    @Test
    void testListTeachers() {
        // Create a teacher directly in DB
        Long id = createTeacher("John Doe");

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/teachers")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].name", equalTo("John Doe"))
            .body("[0].id", notNullValue());
    }

    @Test
    void testCreateTeacher() {
        String body = "{\"name\":\"Jane Doe\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/teachers")
            .then()
            .statusCode(200)
            .body("name", equalTo("Jane Doe"))
            .body("id", notNullValue());

        // Verify it was actually created
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/teachers")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1));
    }

    @Test
    void testUpdateTeacher() {
        Long id = createTeacher("Old Name");

        String body = "{\"name\":\"New Name\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("New Name"))
            .body("id", equalTo(id.intValue()));

        // Verify the update persisted
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/teachers")
            .then()
            .statusCode(200)
            .body("[0].name", equalTo("New Name"));
    }

    @Test
    void testDeleteTeacher() {
        Long id = createTeacher("To Delete");

        given()
            .when()
            .delete("/api/teachers/" + id)
            .then()
            .statusCode(204);

        // Verify deleted
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/teachers")
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }

    @Test
    void testDeleteNonExistentTeacher() {
        // DELETE is idempotent - returns 204 even if entity doesn't exist
        given()
            .when()
            .delete("/api/teachers/999999")
            .then()
            .statusCode(204);
    }

    // ─── R4: cascade cleanup on delete (TASK-345 code review) ────────────

    @Test
    void testDeleteTeacherCascadesRecurringAvailabilityRuleAndTeacherAvailability() {
        Long id = createTeacher("Cascade Test");
        createRecurringRule(id);
        createTeacherAvailability(id);

        assertEquals(1, countRecurringRules(id));
        assertEquals(1, countTeacherAvailability(id));

        given()
            .when()
            .delete("/api/teachers/" + id)
            .then()
            .statusCode(204);

        assertEquals(0, countRecurringRules(id), "RecurringAvailabilityRule must be cascade-deleted with its teacher");
        assertEquals(0, countTeacherAvailability(id), "TeacherAvailability must be cascade-deleted with its teacher");
    }

    @Transactional
    void createRecurringRule(Long teacherId) {
        Teacher teacher = Teacher.findById(teacherId);
        new RecurringAvailabilityRule(teacher, DayOfWeek.MONDAY, 0, 3, AvailabilityType.VACATION).persist();
    }

    @Transactional
    void createTeacherAvailability(Long teacherId) {
        Teacher teacher = Teacher.findById(teacherId);
        LocalDateTime start = LocalDateTime.of(2026, 9, 14, 8, 0);
        new TeacherAvailability(teacher, start, start.plusHours(1), AvailabilityType.SICK).persist();
    }

    @Transactional
    long countRecurringRules(Long teacherId) {
        return (Long) em.createQuery("select count(r) from RecurringAvailabilityRule r where r.teacher.id = ?1")
                .setParameter(1, teacherId)
                .getSingleResult();
    }

    @Transactional
    long countTeacherAvailability(Long teacherId) {
        return (Long) em.createQuery("select count(ta) from TeacherAvailability ta where ta.teacher.id = ?1")
                .setParameter(1, teacherId)
                .getSingleResult();
    }

    // ─── AC #5: Error cases ────────────────────────────────────────────

    @Test
    void testUpdateNonExistentTeacher() {
        String body = "{\"name\":\"Test\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/teachers/999999")
            .then()
            .statusCode(404);
    }

    @Test
    void testCreateTeacherWithInvalidJson() {
        String invalidBody = "{invalid json}";

        given()
            .contentType(ContentType.JSON)
            .body(invalidBody)
            .when()
            .post("/api/teachers")
            .then()
            .statusCode(anyOf(equalTo(400), equalTo(500))); // Either bad request or server error
    }

    // ─── TASK-348: maxDailyHours (H7 per-teacher override) ──────────────

    @Test
    void testCreateTeacherWithMaxDailyHours() {
        String body = "{\"name\":\"T01\",\"maxDailyHours\":8}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/teachers")
            .then()
            .statusCode(200)
            .body("maxDailyHours", equalTo(8));
    }

    @Test
    void testCreateTeacherWithOutOfRangeMaxDailyHoursReturns400() {
        String body = "{\"name\":\"Bad\",\"maxDailyHours\":9}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/teachers")
            .then()
            .statusCode(400);
    }

    // TASK-348 code review (D1): 0 is the explicit "clear the override"
    // sentinel, not an out-of-range value - it maps to null (default).
    @Test
    void testCreateTeacherWithZeroMaxDailyHoursClearsToDefault() {
        String body = "{\"name\":\"Bad\",\"maxDailyHours\":0}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/teachers")
            .then()
            .statusCode(200)
            .body("maxDailyHours", nullValue());
    }

    @Test
    void testCreateTeacherWithNegativeMaxDailyHoursReturns400() {
        String body = "{\"name\":\"Bad\",\"maxDailyHours\":-1}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/teachers")
            .then()
            .statusCode(400);
    }

    @Test
    void testUpdateTeacherMaxDailyHours() {
        Long id = createTeacher("T01");

        given()
            .contentType(ContentType.JSON)
            .body("{\"maxDailyHours\":8}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(200)
            .body("maxDailyHours", equalTo(8));
    }

    @Test
    void testUpdateTeacherWithOutOfRangeMaxDailyHoursReturns400() {
        Long id = createTeacher("T01");

        given()
            .contentType(ContentType.JSON)
            .body("{\"maxDailyHours\":10}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(400);
    }

    // TASK-358-style null-keeps semantics: a partial PUT that omits
    // maxDailyHours must not wipe a previously set value (mirrors
    // StudentGroupResource's maxHoursPerDay pattern).
    @Test
    void testUpdateTeacherOmittingMaxDailyHoursKeepsExistingValue() {
        Long id = createTeacherWithMaxDailyHours("T01", 8);

        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"T01\"}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("T01"))
            .body("maxDailyHours", equalTo(8));
    }

    // TASK-348 code review (D1): the UI's blank input must be able to actually
    // reset an existing override back to the default - null-keeps semantics
    // alone can't do this (null already means "omit, keep unchanged"), so 0 is
    // a distinct clear sentinel that maps server-side to a real null.
    @Test
    void testUpdateTeacherClearsMaxDailyHoursWithZeroBackToDefault() {
        Long id = createTeacherWithMaxDailyHours("T01", 8);

        given()
            .contentType(ContentType.JSON)
            .body("{\"maxDailyHours\":0}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(200)
            .body("maxDailyHours", nullValue());

        assertEquals(Teacher.DEFAULT_MAX_DAILY_HOURS, effectiveMaxDailyHours(id),
                "clearing the override must fall back to the default (6)");
    }

    @Test
    void testUpdateTeacherWithNegativeMaxDailyHoursReturns400() {
        Long id = createTeacher("T01");

        given()
            .contentType(ContentType.JSON)
            .body("{\"maxDailyHours\":-1}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(400);
    }

    @Transactional
    int effectiveMaxDailyHours(Long teacherId) {
        Teacher teacher = Teacher.findById(teacherId);
        return teacher.getEffectiveMaxDailyHours();
    }

    // ─── Helper Methods ────────────────────────────────────────────────

    @Transactional
    Long createTeacher(String name) {
        Teacher teacher = new Teacher();
        teacher.name = name;
        teacher.persist();
        return teacher.id;
    }

    @Transactional
    Long createTeacherWithMaxDailyHours(String name, int maxDailyHours) {
        Teacher teacher = new Teacher();
        teacher.name = name;
        teacher.maxDailyHours = maxDailyHours;
        teacher.persist();
        return teacher.id;
    }

    // ─── TASK-368: maxEarlyStartDaysPerWeek (M24 per-teacher override) ──────

    @Test
    void testCreateTeacherWithMaxEarlyStartDaysPerWeek() {
        String body = "{\"name\":\"T11\",\"maxEarlyStartDaysPerWeek\":1}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/teachers")
            .then()
            .statusCode(200)
            .body("maxEarlyStartDaysPerWeek", equalTo(1));
    }

    @Test
    void testCreateTeacherWithOutOfRangeMaxEarlyStartDaysPerWeekReturns400() {
        String body = "{\"name\":\"Bad\",\"maxEarlyStartDaysPerWeek\":6}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/teachers")
            .then()
            .statusCode(400);
    }

    @Test
    void testUpdateTeacherOmittingMaxEarlyStartDaysPerWeekKeepsExistingValue() {
        Long id = createTeacher("T11");
        given()
            .contentType(ContentType.JSON)
            .body("{\"maxEarlyStartDaysPerWeek\":1}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(200)
            .body("maxEarlyStartDaysPerWeek", equalTo(1));

        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"T11\"}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("T11"))
            .body("maxEarlyStartDaysPerWeek", equalTo(1));
    }

    @Test
    void testUpdateTeacherClearsMaxEarlyStartDaysPerWeekWithZero() {
        Long id = createTeacher("T11");
        given()
            .contentType(ContentType.JSON)
            .body("{\"maxEarlyStartDaysPerWeek\":1}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(200);

        given()
            .contentType(ContentType.JSON)
            .body("{\"maxEarlyStartDaysPerWeek\":0}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(200)
            .body("maxEarlyStartDaysPerWeek", nullValue());
    }

    // ─── TASK-368: requiredFreeDaysPerWeek (M25 per-teacher override) ───────

    @Test
    void testCreateTeacherWithRequiredFreeDaysPerWeek() {
        String body = "{\"name\":\"T05\",\"requiredFreeDaysPerWeek\":2}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/teachers")
            .then()
            .statusCode(200)
            .body("requiredFreeDaysPerWeek", equalTo(2));
    }

    @Test
    void testCreateTeacherWithOutOfRangeRequiredFreeDaysPerWeekReturns400() {
        String body = "{\"name\":\"Bad\",\"requiredFreeDaysPerWeek\":6}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/teachers")
            .then()
            .statusCode(400);
    }

    @Test
    void testUpdateTeacherOmittingRequiredFreeDaysPerWeekKeepsExistingValue() {
        Long id = createTeacher("T05");
        given()
            .contentType(ContentType.JSON)
            .body("{\"requiredFreeDaysPerWeek\":2}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(200)
            .body("requiredFreeDaysPerWeek", equalTo(2));

        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"T05\"}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("T05"))
            .body("requiredFreeDaysPerWeek", equalTo(2));
    }

    @Test
    void testUpdateTeacherClearsRequiredFreeDaysPerWeekWithZero() {
        Long id = createTeacher("T05");
        given()
            .contentType(ContentType.JSON)
            .body("{\"requiredFreeDaysPerWeek\":2}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(200);

        given()
            .contentType(ContentType.JSON)
            .body("{\"requiredFreeDaysPerWeek\":0}")
            .when()
            .put("/api/teachers/" + id)
            .then()
            .statusCode(200)
            .body("requiredFreeDaysPerWeek", nullValue());
    }
}
