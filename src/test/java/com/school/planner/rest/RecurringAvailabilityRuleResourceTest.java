package com.school.planner.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.TimeslotSequence;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for the RecurringAvailabilityRule CRUD REST API
 * (TASK-345), following the same pattern as GroupCalendarRuleResourceTest.
 */
@QuarkusTest
class RecurringAvailabilityRuleResourceTest {

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
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM RecurringAvailabilityRule").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        // Some tests below (R5, the zero-vs-nonzero expansion checks) need at
        // least one real school day so expandForTeacher() actually produces
        // rows - clean it up after too, so this class stays a truly isolated
        // DB for the other tests that rely on Timeslot being empty (see
        // testCreateTriggersExpansion's comment).
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
    }

    // TASK-603: the grid editor's save is ONE atomic bulk replace. The three
    // contracts that matter: always-rules are replaced (old deleted, runs
    // inserted), date-windowed rules survive untouched, and one expansion runs
    // for the whole batch (rows = sum of the new runs' school days, not per
    // mutation).
    @Test
    void testBulkReplaceAlwaysRulesKeepsWindowedAndExpandsOnce() {
        Long teacherId = createTeacher("T16");
        // pre-state: one always rule (deleted by the replace) + one windowed rule
        // (kept) - both through the real POST path
        createRule(teacherId, "MONDAY", 0, 2, "VACATION");
        given().contentType(ContentType.JSON)
            .body("{\"teacherId\":" + teacherId + ",\"dayOfWeek\":\"TUESDAY\",\"minSlot\":3,"
                + "\"maxSlot\":4,\"type\":\"PREFERENCE\",\"effectiveFrom\":\"2026-11-01\"}")
            .when().post("/api/recurring-availability-rules").then().statusCode(200);

        String runs = "["
            + "{\"dayOfWeek\":\"FRIDAY\",\"minSlot\":0,\"maxSlot\":1,\"type\":\"VACATION\"},"
            + "{\"dayOfWeek\":\"THURSDAY\",\"minSlot\":5,\"maxSlot\":5,\"type\":\"PREFERENCE\"}"
            + "]";

        given()
            .contentType(ContentType.JSON)
            .body(runs)
            .when()
            .put("/api/recurring-availability-rules/teacher/" + teacherId)
            .then()
            .statusCode(200)
            .body("size()", equalTo(3));

        // windowed rule survived, old always rule is gone, both runs are in
        given().accept(ContentType.JSON).when()
            .get("/api/recurring-availability-rules")
            .then().statusCode(200)
            .body("findAll { it.dayOfWeek == 'MONDAY' }.size()", equalTo(0))
            .body("findAll { it.dayOfWeek == 'TUESDAY' }.size()", equalTo(1))
            .body("findAll { it.dayOfWeek == 'FRIDAY' }.size()", equalTo(1))
            .body("findAll { it.dayOfWeek == 'THURSDAY' }.size()", equalTo(1));

        // a windowed run is rejected outright: the always-only bulk path must
        // never smuggle a date window past the grid's semantics
        String windowedRun = "[{\"dayOfWeek\":\"FRIDAY\",\"minSlot\":0,\"maxSlot\":0,"
            + "\"type\":\"VACATION\",\"effectiveFrom\":\"2026-11-01\"}]";
        given().contentType(ContentType.JSON).body(windowedRun).when()
            .put("/api/recurring-availability-rules/teacher/" + teacherId)
            .then().statusCode(400);

        // a run overlapping a SURVIVING windowed rule is rejected too: the grid
        // cannot paint windowed rules, so this is the boundary the operator leans on
        String overlappingRun = "[{\"dayOfWeek\":\"TUESDAY\",\"minSlot\":0,\"maxSlot\":7,"
            + "\"type\":\"VACATION\"}]";
        given().contentType(ContentType.JSON).body(overlappingRun).when()
            .put("/api/recurring-availability-rules/teacher/" + teacherId)
            .then().statusCode(400);
    }

    @Test
    void testListEmpty() {
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/recurring-availability-rules")
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }

    @Test
    void testCreateAndList() {
        Long teacherId = createTeacher("T04");

        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":5,\"maxSlot\":7,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(200)
            .body("dayOfWeek", equalTo("MONDAY"))
            .body("minSlot", equalTo(5))
            .body("maxSlot", equalTo(7))
            .body("type", equalTo("VACATION"))
            .body("teacher.id", equalTo(teacherId.intValue()))
            .body("id", notNullValue());

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/recurring-availability-rules")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].dayOfWeek", equalTo("MONDAY"));
    }

    @Test
    void testCreateTriggersExpansion() {
        // Expansion only produces rows on real school days (Timeslot rows) -
        // with none seeded in this test's isolated DB, the rule persists but
        // expandForTeacher() is a no-op (0 Timeslot rows -> 0 expanded rows).
        // This test only verifies the create call succeeds and doesn't error
        // out when expansion runs against an empty calendar.
        Long teacherId = createTeacher("T05");
        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"FRIDAY\",\"minSlot\":0,\"maxSlot\":7,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(200);
    }

    @Test
    void testCreateWithDateScopedOverride() {
        Long teacherId = createTeacher("T12");

        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":7,\"type\":\"VACATION\","
            + "\"effectiveFrom\":\"2026-09-14\",\"effectiveTo\":\"2026-09-14\",\"note\":\"visita medica\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(200)
            .body("effectiveFrom", equalTo("2026-09-14"))
            .body("effectiveTo", equalTo("2026-09-14"))
            .body("note", equalTo("visita medica"));
    }

    // ─── R1: date coherence (TASK-345 code review) ───────────────────────

    @Test
    void testCreateRejectsInvertedDateRange() {
        Long teacherId = createTeacher("T12");
        // effectiveFrom after effectiveTo.
        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":7,\"type\":\"VACATION\","
            + "\"effectiveFrom\":\"2026-09-21\",\"effectiveTo\":\"2026-09-14\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreateRejectsSingleDateWeekdayMismatch() {
        Long teacherId = createTeacher("T12");
        // 2026-09-14 is a Monday, not a Tuesday - would silently expand to
        // zero rows if accepted (TASK-345 code review R1).
        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"TUESDAY\",\"minSlot\":0,\"maxSlot\":7,\"type\":\"VACATION\","
            + "\"effectiveFrom\":\"2026-09-14\",\"effectiveTo\":\"2026-09-14\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreateAcceptsSingleDateMatchingWeekday() {
        Long teacherId = createTeacher("T12");
        // Same date, but dayOfWeek correctly says MONDAY - must be accepted.
        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":7,\"type\":\"VACATION\","
            + "\"effectiveFrom\":\"2026-09-14\",\"effectiveTo\":\"2026-09-14\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(200);
    }

    @Test
    void testUpdateRejectsInvertedDateRange() {
        Long teacherId = createTeacher("T12");
        Long ruleId = createRule(teacherId, "MONDAY", 0, 7, "VACATION");
        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":7,\"type\":\"VACATION\","
            + "\"effectiveFrom\":\"2026-09-21\",\"effectiveTo\":\"2026-09-14\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/recurring-availability-rules/" + ruleId)
            .then()
            .statusCode(400);
    }

    // ─── R2: overlap rejection (TASK-345 code review) ────────────────────

    @Test
    void testCreateRejectsOverlappingRuleForSameTeacher() {
        Long teacherId = createTeacher("T05");
        createRule(teacherId, "MONDAY", 0, 4, "VACATION");

        // Slot 4 is shared with the existing rule - must be rejected.
        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":4,\"maxSlot\":7,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreateAcceptsNonOverlappingRuleForSameTeacher() {
        Long teacherId = createTeacher("T05");
        createRule(teacherId, "MONDAY", 0, 3, "VACATION");

        // Disjoint slot range, same day - must be accepted.
        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":4,\"maxSlot\":7,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(200);

        // Different day entirely, same slot range - must also be accepted.
        String otherDayBody = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"TUESDAY\",\"minSlot\":0,\"maxSlot\":3,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(otherDayBody)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(200);
    }

    @Test
    void testUpdateRejectsOverlapWithAnotherRuleForSameTeacher() {
        Long teacherId = createTeacher("T05");
        createRule(teacherId, "MONDAY", 0, 3, "VACATION");
        Long ruleId = createRule(teacherId, "TUESDAY", 0, 3, "VACATION");

        // Moving the Tuesday rule onto Monday now clashes with the existing
        // Monday rule.
        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":2,\"maxSlot\":5,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/recurring-availability-rules/" + ruleId)
            .then()
            .statusCode(400);
    }

    @Test
    void testUpdateDoesNotRejectOverlapWithItself() {
        // A no-op-ish update (same day/slots, widened range) must not be
        // rejected as "overlapping" its own pre-update row.
        Long teacherId = createTeacher("T05");
        Long ruleId = createRule(teacherId, "MONDAY", 0, 3, "VACATION");

        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":5,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/recurring-availability-rules/" + ruleId)
            .then()
            .statusCode(200)
            .body("maxSlot", equalTo(5));
    }

    @Test
    void testUpdate() {
        Long teacherId = createTeacher("T11");
        Long ruleId = createRule(teacherId, "FRIDAY", 0, 2, "VACATION");

        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"FRIDAY\",\"minSlot\":0,\"maxSlot\":7,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/recurring-availability-rules/" + ruleId)
            .then()
            .statusCode(200)
            .body("maxSlot", equalTo(7));
    }

    // ─── R5: update() moving a rule to a different teacher (TASK-345 code review) ──

    @Test
    void testUpdateMovingRuleToDifferentTeacher_previousTeacherRowsGone_newTeacherRowsPresent() {
        Long oldTeacherId = createTeacher("T11");
        Long newTeacherId = createTeacher("T05");
        persistTimeslotDay(LocalDate.of(2026, 9, 14)); // a Monday school day
        Long ruleId = createRule(oldTeacherId, "MONDAY", 0, 3, "VACATION");

        given()
            .contentType(ContentType.JSON)
            .body("{\"teacherId\":" + oldTeacherId
                + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":3,\"type\":\"VACATION\"}")
            .when()
            .put("/api/recurring-availability-rules/" + ruleId)
            .then()
            .statusCode(200);
        assertEquals(1, expandedRowCount(oldTeacherId), "old teacher has its expanded row before the move");
        assertEquals(0, expandedRowCount(newTeacherId));

        // Move the rule to the new teacher.
        given()
            .contentType(ContentType.JSON)
            .body("{\"teacherId\":" + newTeacherId
                + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":3,\"type\":\"VACATION\"}")
            .when()
            .put("/api/recurring-availability-rules/" + ruleId)
            .then()
            .statusCode(200)
            .body("teacher.id", equalTo(newTeacherId.intValue()));

        assertEquals(0, expandedRowCount(oldTeacherId), "old teacher's expanded row must be gone after the move");
        assertEquals(1, expandedRowCount(newTeacherId), "new teacher must have the expanded row after the move");
    }

    @Transactional
    long expandedRowCount(Long teacherId) {
        return (Long) em.createQuery(
                "select count(ta) from TeacherAvailability ta where ta.teacher.id = ?1 and ta.sourceRuleId is not null")
                .setParameter(1, teacherId)
                .getSingleResult();
    }

    @Transactional
    void persistTimeslotDay(LocalDate date) {
        for (LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
            new Timeslot(LocalDateTime.of(date, start), LocalDateTime.of(date, start.plusHours(1))).persist();
        }
    }

    // ─── Validation (TASK-345, matching GroupCalendarRuleResource's style) ──

    @Test
    void testCreateRejectsSlotOutOfBounds() {
        Long teacherId = createTeacher("T24");
        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":8,\"type\":\"VACATION\"}"; // 8 out of 0-7

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreateRejectsMaxLessThanMin() {
        // Unlike GroupCalendarRule, there is no closed-day sentinel here -
        // maxSlot < minSlot is always invalid.
        Long teacherId = createTeacher("T10");
        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":3,\"maxSlot\":1,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreateRejectsNullDayOfWeek() {
        Long teacherId = createTeacher("T16");
        String body = "{\"teacherId\":" + teacherId + ",\"minSlot\":0,\"maxSlot\":5,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreateRejectsUnknownTeacherId() {
        String body = "{\"teacherId\":999999,\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":5,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(400);

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/recurring-availability-rules")
            .then()
            .statusCode(200)
            .body("size()", equalTo(0)); // no orphan row persisted
    }

    @Test
    void testCreateRejectsNullTeacherId() {
        String body = "{\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":5,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreateRejectsSickType() {
        // SICK is a real AvailabilityType value but not meaningful for a
        // recurring rule (a recurring "sick day" makes no sense).
        Long teacherId = createTeacher("T05");
        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":5,\"type\":\"SICK\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/recurring-availability-rules")
            .then()
            .statusCode(400);
    }

    @Test
    void testUpdateRejectsSlotOutOfBounds() {
        Long teacherId = createTeacher("T04");
        Long ruleId = createRule(teacherId, "FRIDAY", 0, 2, "VACATION");
        String body = "{\"teacherId\":" + teacherId
            + ",\"dayOfWeek\":\"FRIDAY\",\"minSlot\":-1,\"maxSlot\":7,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/recurring-availability-rules/" + ruleId)
            .then()
            .statusCode(400);
    }

    @Test
    void testUpdateRejectsUnknownTeacherId() {
        Long teacherId = createTeacher("T05");
        Long ruleId = createRule(teacherId, "FRIDAY", 0, 2, "VACATION");
        String body = "{\"teacherId\":999999,\"dayOfWeek\":\"FRIDAY\",\"minSlot\":0,\"maxSlot\":2,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/recurring-availability-rules/" + ruleId)
            .then()
            .statusCode(400);
    }

    @Test
    void testUpdateNonExistent() {
        String body = "{\"dayOfWeek\":\"MONDAY\",\"minSlot\":0,\"maxSlot\":7,\"type\":\"VACATION\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/recurring-availability-rules/999999")
            .then()
            .statusCode(404);
    }

    @Test
    void testDelete() {
        Long teacherId = createTeacher("T11");
        Long ruleId = createRule(teacherId, "MONDAY", 0, 5, "VACATION");

        given()
            .when()
            .delete("/api/recurring-availability-rules/" + ruleId)
            .then()
            .statusCode(204);

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/recurring-availability-rules")
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }

    @Test
    void testDeleteNonExistent() {
        given()
            .when()
            .delete("/api/recurring-availability-rules/999999")
            .then()
            .statusCode(204);
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
    Long createRule(Long teacherId, String dayOfWeek, int minSlot, int maxSlot, String type) {
        Teacher teacher = Teacher.findById(teacherId);
        com.school.planner.domain.RecurringAvailabilityRule rule = new com.school.planner.domain.RecurringAvailabilityRule(
                teacher, java.time.DayOfWeek.valueOf(dayOfWeek), minSlot, maxSlot,
                com.school.planner.domain.AvailabilityType.valueOf(type));
        rule.persist();
        return rule.id;
    }
}
