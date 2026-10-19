package com.school.planner.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for the slot schema endpoint (TASK-358). Verifies the
 * 8-slot/day schema (TASK-341) is exposed correctly for the frontend's
 * data-driven time-period rendering.
 */
@QuarkusTest
class SlotConfigResourceTest {

    @Test
    void testGetSlotSchema() {
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/config/slots")
            .then()
            .statusCode(200)
            .body("slots.size()", equalTo(8))
            .body("slots[0].index", equalTo(0))
            .body("slots[0].startTime", equalTo("08:00"))
            .body("slots[0].endTime", equalTo("09:00"))
            .body("slots[0].isAfternoon", equalTo(false))
            .body("slots[5].startTime", equalTo("13:00"))
            .body("slots[5].endTime", equalTo("14:00"))
            .body("slots[5].isAfternoon", equalTo(false))
            .body("slots[6].startTime", equalTo("14:30"))
            .body("slots[6].endTime", equalTo("15:30"))
            .body("slots[6].isAfternoon", equalTo(true))
            .body("slots[7].startTime", equalTo("15:30"))
            .body("slots[7].endTime", equalTo("16:30"))
            .body("slots[7].isAfternoon", equalTo(true))
            .body("lunchBreakStart", equalTo("14:00"))
            .body("lunchBreakEnd", equalTo("14:30"));
    }
}
