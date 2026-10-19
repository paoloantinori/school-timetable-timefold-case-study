package com.school.planner.rest;

import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Lesson;
import com.school.planner.testsupport.LessonFixtures;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Integration tests for the pinned-with-null-timeslot guard (BUG A): pinning
 * an unassigned Lesson is an invalid Timefold state (the timeslot planning
 * variable has no {@code allowsUnassigned}) that later 500s the analysis
 * endpoint and the validation watchdog. See {@link LessonResource#togglePin}
 * and {@code ScheduleService#healPinnedNullTimeslotLessons} for the two-layer
 * fix (endpoint guard + defensive healing on load).
 */
@QuarkusTest
class LessonResourceTest {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
    }

    @Test
    void testPinUnassignedLessonReturns409() {
        Long lessonId = persist(() -> LessonFixtures.persistUnassignedLesson("PinGuard409"));

        given()
            .when()
            .put("/api/lessons/" + lessonId + "/pin")
            .then()
            .statusCode(409);

        // The 409 body carries no "pinned" field, so this is the only way to
        // confirm the rejected request didn't mutate state.
        assertPinned(lessonId, false);
    }

    @Test
    void testPinAssignedLessonSucceeds() {
        Long lessonId = persist(() -> LessonFixtures.persistAssignedLesson("PinGuardOk"));

        given()
            .when()
            .put("/api/lessons/" + lessonId + "/pin")
            .then()
            .statusCode(200)
            .body("pinned", equalTo(true));
    }

    @Test
    void testUnpinAlwaysAllowedEvenWithNullTimeslot() {
        // Simulates a row corrupted before the pin-endpoint guard existed
        // (e.g. written directly via a script/import): pinned=true, timeslot=null.
        Long lessonId = persist(() -> LessonFixtures.persistCorruptedPinnedLesson("UnpinGuard"));

        given()
            .when()
            .put("/api/lessons/" + lessonId + "/pin")
            .then()
            .statusCode(200)
            .body("pinned", equalTo(false));
    }

    @Test
    void testAnalysisEndpointToleratesCorruptedPinnedNullLesson() {
        // Direct entity manipulation - bypasses the endpoint guard entirely,
        // as a pre-existing corrupted row would.
        persist(() -> LessonFixtures.persistCorruptedPinnedLesson("AnalysisHeal"));

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/schedule/analysis/lessons")
            .then()
            .statusCode(200);
    }

    @Transactional
    Long persist(Supplier<Lesson> fixture) {
        return fixture.get().id;
    }

    @Transactional
    void assertPinned(Long lessonId, boolean expected) {
        Lesson lesson = Lesson.findById(lessonId);
        org.junit.jupiter.api.Assertions.assertEquals(expected, lesson.pinned);
    }
}
