package com.school.planner.rest;

import com.school.planner.domain.Course;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
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
 * Integration tests for the Course CRUD REST API, including the TASK-356
 * batch endpoint that backs the course-hours grid editor.
 */
@QuarkusTest
class CourseResourceTest {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        // Clear in order respecting foreign keys: lessons -> courses -> teachers/groups
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
    }

    // ─── Basic CRUD (no prior coverage existed for this resource) ──────

    @Test
    void testCreateCourseWithColor() {
        Long teacherId = createTeacher("Rossi");
        Long groupId = createGroup("G03");

        String body = "{\"subject\":\"Matematica\",\"teacherId\":" + teacherId
            + ",\"studentGroupId\":" + groupId
            + ",\"totalHours\":66,\"isLab\":false,\"labBlockSize\":0,"
            + "\"requiredRoomTags\":[],\"color\":\"#3b82f6\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/courses")
            .then()
            .statusCode(200)
            .body("subject", equalTo("Matematica"))
            .body("color", equalTo("#3b82f6"))
            .body("teacher.id", equalTo(teacherId.intValue()))
            .body("studentGroup.id", equalTo(groupId.intValue()));
    }

    @Test
    void testDeleteNonExistentCourse() {
        given()
            .when()
            .delete("/api/courses/999999")
            .then()
            .statusCode(204);
    }

    // ─── BUG B: DELETE must cascade the course's generated Lessons ─────

    @Test
    void testDeleteCourseWithGeneratedLessonsCascades() {
        Long teacherId = createTeacher("Esposito");
        Long groupId = createGroup("G05");
        Long courseId = createCourse("Chimica", teacherId, groupId, 3);

        given()
            .when()
            .post("/api/courses/" + courseId + "/generate-lessons")
            .then()
            .statusCode(200)
            .body("lessonsGenerated", equalTo(3));

        assertEquals(3L, countLessonsForCourse(courseId));

        given()
            .when()
            .delete("/api/courses/" + courseId)
            .then()
            .statusCode(204);

        assertEquals(0L, countLessonsForCourse(courseId));
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/courses")
            .then()
            .statusCode(200)
            .body("find { it.id == " + courseId + " }", nullValue());
    }

    @Transactional
    long countLessonsForCourse(Long courseId) {
        return (long) em.createQuery("SELECT COUNT(l) FROM Lesson l WHERE l.course.id = ?1")
            .setParameter(1, courseId)
            .getSingleResult();
    }

    // ─── AC #2: batch update endpoint ───────────────────────────────────

    @Test
    void testBatchUpdateSavesAllRowsInOneCall() {
        Long teacher1 = createTeacher("Bianchi");
        Long teacher2 = createTeacher("Verdi");
        Long group1 = createGroup("G07");

        Long course1 = createCourse("Italiano", teacher1, group1, 99);
        Long course2 = createCourse("Storia", teacher1, group1, 33);

        String body = "["
            + "{\"id\":" + course1 + ",\"subject\":\"Italiano\",\"teacherId\":" + teacher2
            + ",\"studentGroupId\":" + group1 + ",\"totalHours\":120,\"isLab\":false,"
            + "\"labBlockSize\":0,\"requiredRoomTags\":[],\"color\":\"#ff0000\"},"
            + "{\"id\":" + course2 + ",\"subject\":\"Storia\",\"teacherId\":" + teacher1
            + ",\"studentGroupId\":" + group1 + ",\"totalHours\":40,\"isLab\":true,"
            + "\"labBlockSize\":2,\"requiredRoomTags\":[\"informatica\"],\"color\":null}"
            + "]";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/courses/batch")
            .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("find { it.id == " + course1 + " }.totalHours", equalTo(120))
            .body("find { it.id == " + course1 + " }.teacher.id", equalTo(teacher2.intValue()))
            .body("find { it.id == " + course1 + " }.color", equalTo("#ff0000"))
            .body("find { it.id == " + course2 + " }.isLab", equalTo(true))
            .body("find { it.id == " + course2 + " }.requiredRoomTags", hasItem("informatica"));

        // Verify persisted, not just returned
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/courses")
            .then()
            .statusCode(200)
            .body("find { it.id == " + course1 + " }.totalHours", equalTo(120))
            .body("find { it.id == " + course2 + " }.totalHours", equalTo(40));
    }

    @Test
    void testBatchUpdateCanUnassignTeacherAndGroup() {
        Long teacher = createTeacher("Neri");
        Long group = createGroup("G09");
        Long courseId = createCourse("Disegno", teacher, group, 50);

        String body = "[{\"id\":" + courseId + ",\"subject\":\"Disegno\",\"teacherId\":null,"
            + "\"studentGroupId\":null,\"totalHours\":50,\"isLab\":false,\"labBlockSize\":0,"
            + "\"requiredRoomTags\":[],\"color\":null}]";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/courses/batch")
            .then()
            .statusCode(200)
            .body("[0].teacher", nullValue())
            .body("[0].studentGroup", nullValue());
    }

    @Test
    void testBatchUpdateOmittingLabBlockSizePreservesExistingValue() {
        Long teacher = createTeacher("Fontana");
        Long group = createGroup("G08");
        Long courseId = createCourseWithLabBlockSize("Informatica", teacher, group, 60, 3);

        // No "labBlockSize" key at all in the JSON body - simulates any caller that
        // forgets to send it. Must NOT be silently coerced to 0.
        String body = "[{\"id\":" + courseId + ",\"subject\":\"Informatica\",\"teacherId\":" + teacher
            + ",\"studentGroupId\":" + group + ",\"totalHours\":66,\"isLab\":true,"
            + "\"requiredRoomTags\":[\"informatica\"],\"color\":null}]";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/courses/batch")
            .then()
            .statusCode(200)
            .body("[0].totalHours", equalTo(66))
            .body("[0].labBlockSize", equalTo(3));

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/courses")
            .then()
            .statusCode(200)
            .body("find { it.id == " + courseId + " }.labBlockSize", equalTo(3));
    }

    @Test
    void testBatchUpdateNonExistentCourseReturns404() {
        String body = "[{\"id\":999999,\"subject\":\"Ghost\",\"teacherId\":null,"
            + "\"studentGroupId\":null,\"totalHours\":1,\"isLab\":false,\"labBlockSize\":0,"
            + "\"requiredRoomTags\":[],\"color\":null}]";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/courses/batch")
            .then()
            .statusCode(404);
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
    Long createGroup(String name) {
        StudentGroup group = new StudentGroup();
        group.name = name;
        group.size = 20;
        group.persist();
        return group.id;
    }

    @Transactional
    Long createCourse(String subject, Long teacherId, Long groupId, int totalHours) {
        Course course = new Course();
        course.subject = subject;
        course.teacher = Teacher.findById(teacherId);
        course.studentGroup = StudentGroup.findById(groupId);
        course.totalHours = totalHours;
        course.persist();
        return course.id;
    }

    @Transactional
    Long createCourseWithLabBlockSize(String subject, Long teacherId, Long groupId, int totalHours, int labBlockSize) {
        Course course = new Course();
        course.subject = subject;
        course.teacher = Teacher.findById(teacherId);
        course.studentGroup = StudentGroup.findById(groupId);
        course.totalHours = totalHours;
        course.isLab = true;
        course.labBlockSize = labBlockSize;
        course.persist();
        return course.id;
    }
}
