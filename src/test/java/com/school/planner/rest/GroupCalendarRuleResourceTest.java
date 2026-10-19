package com.school.planner.rest;

import com.school.planner.domain.StudentGroup;
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
 * Integration tests for the GroupCalendarRule CRUD REST API (TASK-342),
 * following the same pattern as StagePatternResource/LabRotationResource.
 */
@QuarkusTest
class GroupCalendarRuleResourceTest {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        clearTables();
    }

    // GroupCalendarRule FKs into StudentGroup - other test classes' setup deletes
    // StudentGroup rows without expecting this new child table, so any row left
    // behind here breaks their cleanup with a FK violation regardless of test order.
    @AfterEach
    @Transactional
    void cleanup() {
        clearTables();
    }

    private void clearTables() {
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
    }

    @Test
    void testListEmpty() {
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/group-calendar-rules")
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }

    @Test
    void testCreateAndList() {
        Long groupId = createGroup("G03");

        String body = "{\"studentGroupId\":" + groupId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":5}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/group-calendar-rules")
            .then()
            .statusCode(200)
            .body("dayOfWeek", equalTo("MONDAY"))
            .body("minSlot", equalTo(0))
            .body("maxSlot", equalTo(5))
            .body("studentGroup.id", equalTo(groupId.intValue()))
            .body("id", notNullValue());

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/group-calendar-rules")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].dayOfWeek", equalTo("MONDAY"));
    }

    @Test
    void testCreateWithDateScopedOverride() {
        Long groupId = createGroup("G01");

        String body = "{\"studentGroupId\":" + groupId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":1,\"maxSlot\":4,"
            + "\"effectiveFrom\":\"2026-09-14\",\"effectiveTo\":\"2026-09-14\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/group-calendar-rules")
            .then()
            .statusCode(200)
            .body("minSlot", equalTo(1))
            .body("maxSlot", equalTo(4))
            .body("effectiveFrom", equalTo("2026-09-14"))
            .body("effectiveTo", equalTo("2026-09-14"));
    }

    @Test
    void testUpdate() {
        Long groupId = createGroup("G08");
        Long ruleId = createRule(groupId, "FRIDAY", 0, 2);

        String body = "{\"studentGroupId\":" + groupId
            + ",\"dayOfWeek\":\"FRIDAY\",\"minSlot\":0,\"maxSlot\":7}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/group-calendar-rules/" + ruleId)
            .then()
            .statusCode(200)
            .body("maxSlot", equalTo(7));
    }

    // TASK-342 Z5: minimal validation — inert/orphan calendar rules silently alter
    // solving, so these three cases return 400 instead of persisting.

    @Test
    void testCreateRejectsSlotOutOfBounds() {
        Long groupId = createGroup("G03");
        String body = "{\"studentGroupId\":" + groupId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":8}"; // 8 is out of 0-7

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/group-calendar-rules")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreateAllowsClosedDaySentinel() {
        Long groupId = createGroup("G14");
        String body = "{\"studentGroupId\":" + groupId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":-1}"; // documented closed-day sentinel

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/group-calendar-rules")
            .then()
            .statusCode(200)
            .body("minSlot", equalTo(0))
            .body("maxSlot", equalTo(-1));
    }

    @Test
    void testCreateAllowsNonCanonicalClosedDaySentinel() {
        // The closed-day rule is "any maxSlot < minSlot", not literally the canonical 0,-1 pair.
        Long groupId = createGroup("G04");
        String body = "{\"studentGroupId\":" + groupId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":3,\"maxSlot\":1}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/group-calendar-rules")
            .then()
            .statusCode(200)
            .body("minSlot", equalTo(3))
            .body("maxSlot", equalTo(1));
    }

    @Test
    void testCreateRejectsNullDayOfWeek() {
        Long groupId = createGroup("G08");
        String body = "{\"studentGroupId\":" + groupId + ",\"minSlot\":0,\"maxSlot\":5}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/group-calendar-rules")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreateRejectsUnknownStudentGroupId() {
        String body = "{\"studentGroupId\":999999,\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":5}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/group-calendar-rules")
            .then()
            .statusCode(400);

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/group-calendar-rules")
            .then()
            .statusCode(200)
            .body("size()", equalTo(0)); // no orphan row persisted
    }

    @Test
    void testUpdateRejectsSlotOutOfBounds() {
        Long groupId = createGroup("G10");
        Long ruleId = createRule(groupId, "FRIDAY", 0, 2);
        String body = "{\"studentGroupId\":" + groupId
            + ",\"dayOfWeek\":\"FRIDAY\",\"minSlot\":-1,\"maxSlot\":7}"; // -1 is out of bounds

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/group-calendar-rules/" + ruleId)
            .then()
            .statusCode(400);
    }

    @Test
    void testUpdateRejectsUnknownStudentGroupId() {
        Long groupId = createGroup("G12");
        Long ruleId = createRule(groupId, "FRIDAY", 0, 2);
        String body = "{\"studentGroupId\":999999,\"dayOfWeek\":\"FRIDAY\",\"minSlot\":0,\"maxSlot\":2}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/group-calendar-rules/" + ruleId)
            .then()
            .statusCode(400);
    }

    @Test
    void testUpdateNonExistent() {
        String body = "{\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":7}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/group-calendar-rules/999999")
            .then()
            .statusCode(404);
    }

    @Test
    void testDelete() {
        Long groupId = createGroup("G13");
        Long ruleId = createRule(groupId, "MONDAY", 0, 5);

        given()
            .when()
            .delete("/api/group-calendar-rules/" + ruleId)
            .then()
            .statusCode(204);

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/group-calendar-rules")
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }

    @Test
    void testDeleteNonExistent() {
        given()
            .when()
            .delete("/api/group-calendar-rules/999999")
            .then()
            .statusCode(204);
    }

    // ─── /resolved endpoint (TASK-358 code review N2) ──────────────────

    @Test
    void testResolvedRequiresFromAndTo() {
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/group-calendar-rules/resolved")
            .then()
            .statusCode(400);

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/group-calendar-rules/resolved?from=2026-09-14")
            .then()
            .statusCode(400);
    }

    @Test
    void testResolvedRejectsToBeforeFrom() {
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/group-calendar-rules/resolved?from=2026-09-18&to=2026-09-14")
            .then()
            .statusCode(400);
    }

    @Test
    void testResolvedRejectsMalformedDate() {
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/group-calendar-rules/resolved?from=not-a-date&to=2026-09-14")
            .then()
            .statusCode(400);
    }

    @Test
    void testResolvedNoRulesFullyOpen() {
        Long groupId = createGroup("G03");

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/group-calendar-rules/resolved?from=2026-09-14&to=2026-09-14") // Monday
            .then()
            .statusCode(200)
            .body("find { it.groupId == " + groupId + " }.minSlot", equalTo(0))
            .body("find { it.groupId == " + groupId + " }.maxSlot", equalTo(7))
            .body("find { it.groupId == " + groupId + " }.closed", equalTo(false));
    }

    @Test
    void testResolvedExcludesWeekends() {
        createGroup("G08");

        given()
            .accept(ContentType.JSON)
            .when()
            // 2026-09-19/20 is Sat/Sun
            .get("/api/group-calendar-rules/resolved?from=2026-09-19&to=2026-09-20")
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }

    // "First-Monday ramp": a date-scoped override on one Monday must not leak into other Mondays.
    @Test
    void testResolvedDateScopedOverrideAppliesOnlyToScopedDate() {
        Long groupId = createGroup("G01");
        createRule(groupId, "MONDAY", 0, 7); // base rule, every Monday
        String rampBody = "{\"studentGroupId\":" + groupId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":1,\"maxSlot\":3,"
            + "\"effectiveFrom\":\"2026-09-14\",\"effectiveTo\":\"2026-09-14\"}";
        given()
            .contentType(ContentType.JSON)
            .body(rampBody)
            .when()
            .post("/api/group-calendar-rules")
            .then()
            .statusCode(200);

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/group-calendar-rules/resolved?from=2026-09-14&to=2026-09-21")
            .then()
            .statusCode(200)
            .body("find { it.groupId == " + groupId + " && it.date == '2026-09-14' }.minSlot", equalTo(1))
            .body("find { it.groupId == " + groupId + " && it.date == '2026-09-14' }.maxSlot", equalTo(3))
            .body("find { it.groupId == " + groupId + " && it.date == '2026-09-21' }.minSlot", equalTo(0))
            .body("find { it.groupId == " + groupId + " && it.date == '2026-09-21' }.maxSlot", equalTo(7));
    }

    @Test
    void testResolvedLegacyMaxHoursPerDayFallback() {
        Long groupId = createGroup("G10");
        // No GroupCalendarRule rows at all for this group - legacy profile.
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"G10\",\"size\":20,\"maxHoursPerDay\":5}")
            .when()
            .put("/api/student-groups/" + groupId)
            .then()
            .statusCode(200);

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/group-calendar-rules/resolved?from=2026-09-14&to=2026-09-14")
            .then()
            .statusCode(200)
            .body("find { it.groupId == " + groupId + " }.minSlot", equalTo(0))
            .body("find { it.groupId == " + groupId + " }.maxSlot", equalTo(4));
    }

    // ─── Helper Methods ────────────────────────────────────────────────

    @Transactional
    Long createGroup(String name) {
        StudentGroup group = new StudentGroup();
        group.name = name;
        group.size = 20;
        group.persist();
        return group.id;
    }

    @Transactional
    Long createRule(Long groupId, String dayOfWeek, int minSlot, int maxSlot) {
        StudentGroup group = StudentGroup.findById(groupId);
        com.school.planner.domain.GroupCalendarRule rule = new com.school.planner.domain.GroupCalendarRule(
                group, java.time.DayOfWeek.valueOf(dayOfWeek), minSlot, maxSlot);
        rule.persist();
        return rule.id;
    }
}
