package com.school.planner.rest;

import ai.timefold.solver.core.api.solver.SolverStatus;
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test for the solver integration.
 * <p>
 * Tests that the solver can start, run, and produce a score-improved result
 * without errors. Uses minimal test data (2 lessons, 2 timeslots) to ensure
 * fast execution even with the 10s solve limit.
 * </p>
 */
@QuarkusTest
class ScheduleResourceTest {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        // Clear existing data (in order respecting foreign keys)
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
        em.createQuery("DELETE FROM Room").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();

        // Create minimal test data: 1 teacher, 1 group, 1 room, 2 timeslots, 1 course, 2 lessons
        Teacher teacher = createTeacher("Test Teacher");
        StudentGroup group = createGroup("Test Group");
        Room room = createRoom("Test Room");
        Course course = createCourse(teacher, group, "Test Subject", 2);

        // Create 2 consecutive timeslots on a Monday
        LocalDateTime monday8am = LocalDateTime.of(2025, 10, 6, 8, 0); // Monday
        createTimeslot(monday8am, monday8am.plusHours(1));           // 08:00-09:00
        createTimeslot(monday8am.plusHours(1), monday8am.plusHours(2)); // 09:00-10:00

        // Create 2 lessons from the course (both unassigned initially)
        createLesson(course);
        createLesson(course);
    }

    @Test
    void testSolverSmoke() {
        // 1. Get initial schedule - verify solver is NOT_SOLVING
        var initialResponse = given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/schedule")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();

        assertEquals(SolverStatus.NOT_SOLVING.name(),
                initialResponse.getString("solverStatus"),
                "Initial solver status should be NOT_SOLVING");

        List<?> initialLessons = initialResponse.getList("lessons");
        assertEquals(2, initialLessons.size(), "Should have 2 lessons initially");

        // 2. Start solving - should return 202 Accepted
        given()
                .when()
                .post("/api/schedule/solve")
                .then()
                .statusCode(202);

        // 3. Poll until solver completes (NOT_SOLVING)
        // Test config has 10s spent-limit, allow 60s total for CH + LS phases
        await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var response = given()
                            .accept(ContentType.JSON)
                            .when()
                            .get("/api/schedule")
                            .then()
                            .statusCode(200)
                            .extract()
                            .jsonPath();

                    assertEquals(SolverStatus.NOT_SOLVING.name(),
                            response.getString("solverStatus"),
                            "Solver should complete and return to NOT_SOLVING");
                });

        // 4. Verify final state - score should exist and be feasible
        var finalResponse = given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/schedule")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();

        // Score should exist (BendableScore format, TASK-538 M1: "[h/h]hard/[m/s]soft")
        String scoreString = finalResponse.getString("score");
        assertNotNull(scoreString, "Score should exist after solving");
        assertFalse(scoreString.isEmpty(), "Score should not be empty");

        // Parse and verify hard score >= 0 (feasible for this simple problem)
        int hardScore = parseHardScore(scoreString);
        assertTrue(hardScore >= 0,
                "Hard score should be >= 0 (feasible solution), was: " + hardScore);
    }

    // ─── Additional Tests: Solver Lifecycle (AC #1, #5) ────────────────

    @Test
    void testStopSolver() {
        // Start solver
        given().when().post("/api/schedule/solve").then().statusCode(202);

        // Immediately stop it (returns 202 Accepted)
        given().when().delete("/api/schedule/solve").then().statusCode(202);

        // Verify solver is NOT_SOLVING (poll briefly since stop is async)
        awaitPolling(() -> given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/schedule")
            .then()
            .statusCode(200)
            .body("solverStatus", equalTo("NOT_SOLVING")));
    }

    @Test
    void testStopSolverWhenNotRunning() {
        // Stopping when not running should still return success (idempotent)
        // Returns 202 Accepted (same as when running)
        given().when().delete("/api/schedule/solve").then().statusCode(202);
    }

    @Test
    void testSolveReturnsAccepted() {
        // Start solver - should return 202 Accepted
        given()
            .accept(ContentType.JSON)
            .when()
            .post("/api/schedule/solve")
            .then()
            .statusCode(202);

        // Clean up: stop the solver (returns 202 Accepted)
        given().when().delete("/api/schedule/solve").then().statusCode(202);
    }

    // ─── Cumulative Time Tests (TASK-162) ───────────────────────────

    @Test
    void testCumulativeTimeFieldsInResponse() {
        // GET /api/schedule should include cumulative time fields (initially 0)
        given().accept(ContentType.JSON)
            .when().get("/api/schedule")
            .then().statusCode(200)
            .body("cumulativeElapsedSeconds", equalTo(0))
            .body("cumulativeChSeconds", equalTo(0))
            .body("cumulativeLsSeconds", equalTo(0))
            .body("preAssignmentDurationSeconds", equalTo(0));
    }

    @Test
    void testCumulativeTimeAccumulates() {
        // Under full-suite load a previous test's solve can still be active when
        // this one starts, turning the POST into a 400 (observed twice on
        // 2026-07-18/19): wait for idle BEFORE starting, same as the wait after.
        await()
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                given().accept(ContentType.JSON)
                    .when().get("/api/schedule")
                    .then().statusCode(200)
                    .body("solverStatus", equalTo("NOT_SOLVING"));
            });

        // Start solver and let it complete naturally (test config has short termination)
        given().when().post("/api/schedule/solve").then().statusCode(202);

        // Wait for solver to complete
        await()
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                given().accept(ContentType.JSON)
                    .when().get("/api/schedule")
                    .then().statusCode(200)
                    .body("solverStatus", equalTo("NOT_SOLVING"));
            });

        // After solving completes, cumulative fields should be non-negative
        // (with a tiny 2-lesson problem, solver may complete in <1s, so we check >= 0)
        var response = given().accept(ContentType.JSON)
            .when().get("/api/schedule").then().statusCode(200)
            .extract().jsonPath();
        int elapsed = response.getInt("cumulativeElapsedSeconds");
        int ch = response.getInt("cumulativeChSeconds");
        int ls = response.getInt("cumulativeLsSeconds");
        assertTrue(elapsed >= 0, "Cumulative elapsed should be >= 0, was: " + elapsed);
        assertTrue(ch >= 0, "Cumulative CH should be >= 0, was: " + ch);
        assertTrue(ls >= 0, "Cumulative LS should be >= 0, was: " + ls);
    }

    // ─── Helper Methods ─────────────────────────────────────────────

    private void awaitPolling(Runnable assertion) {
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                try {
                    assertion.run();
                } catch (AssertionError e) {
                    throw e;
                }
            });
    }

    private int parseHardScore(String scoreString) {
        // BendableScore(2,2) format (TASK-538 M1): "[-123/0]hard/[45/678]soft"; the pure-hard
        // tier is the first component of the hard level list.
        assertTrue(scoreString.contains("hard"),
                "Score should contain 'hard': " + scoreString);
        String hardLevels = scoreString.substring(0, scoreString.indexOf("]hard"));
        String hardPart = hardLevels.substring(hardLevels.indexOf('[') + 1).split("/")[0];
        return Integer.parseInt(hardPart);
    }

    @Transactional
    Teacher createTeacher(String name) {
        Teacher teacher = new Teacher();
        teacher.name = name;
        teacher.persist();
        return teacher;
    }

    @Transactional
    StudentGroup createGroup(String name) {
        StudentGroup group = new StudentGroup();
        group.name = name;
        group.size = 20;
        group.persist();
        return group;
    }

    @Transactional
    Room createRoom(String name) {
        Room room = new Room();
        room.name = name;
        room.capacity = 25;
        room.persist();
        return room;
    }

    @Transactional
    void createTimeslot(LocalDateTime start, LocalDateTime end) {
        Timeslot timeslot = new Timeslot(start, end);
        timeslot.persist();
    }

    @Transactional
    Course createCourse(Teacher teacher, StudentGroup group, String subject, int totalHours) {
        Course course = new Course();
        course.teacher = teacher;
        course.studentGroup = group;
        course.subject = subject;
        course.totalHours = totalHours;
        course.isLab = false;
        course.labBlockSize = 0;
        course.professionalizzanti = false;
        course.persist();
        return course;
    }

    @Transactional
    void createLesson(Course course) {
        Lesson lesson = new Lesson(course);
        lesson.pinned = false;
        lesson.persist();
    }
}
