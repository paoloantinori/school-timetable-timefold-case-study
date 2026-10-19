package com.school.planner.rest;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for the reconciliation dashboard endpoint (TASK-353).
 * Fixture mirrors the school's "Classi" sheet scenarios documented in
 * claudedocs/spalmone-2026-27/analysis.md §6 and
 * claudedocs/design/stage-esame-ra-modeling-2026-07-16.md:
 * - a fully-scheduled real course (OK)
 * - a real course assigned but never placed on the calendar (UNSCHEDULED)
 * - a real course partially scheduled (MISMATCH)
 * - a pseudo (Stage) accounting course with a partial, expected shortfall
 */
@QuarkusTest
class ReconciliationResourceTest {

    @Inject
    EntityManager em;

    private Teacher rossi;
    private Teacher t13;
    private Teacher stagePseudo;
    private StudentGroup fb;
    private StudentGroup fc;

    @BeforeEach
    @Transactional
    void setup() {
        clearTables();

        rossi = persistTeacher("Rossi", false);
        t13 = persistTeacher("T13", false);
        stagePseudo = persistTeacher("Stage", true);
        fb = persistGroup("G03");
        fc = persistGroup("G08");
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // TASK-353's fixture creates Teacher/Course rows that other @QuarkusTest
        // classes sharing this JVM's H2 instance must not see - clean up after
        // every test, not just before (mirrors RecurringAvailabilityRuleResourceTest).
        clearTables();
    }

    private void clearTables() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
    }

    @Test
    void fullyScheduledCourseIsOk() {
        Course course = persistCourse(rossi, "Matematica", fb, 3);
        persistScheduledLessons(course, 3, true, LocalDateTime.of(2027, 3, 1, 8, 0));

        given()
            .queryParam("courseId", course.id)
            .accept(ContentType.JSON)
            .when()
            .get("/api/reconciliation")
            .then()
            .statusCode(200)
            .body("rows.find { it.courseId == " + course.id + " }.assignedHours", equalTo(3))
            .body("rows.find { it.courseId == " + course.id + " }.scheduledHours", equalTo(3))
            .body("rows.find { it.courseId == " + course.id + " }.delta", equalTo(0))
            .body("rows.find { it.courseId == " + course.id + " }.status", equalTo("OK"))
            .body("rows.find { it.courseId == " + course.id + " }.pseudo", equalTo(false));
    }

    @Test
    void assignedButNeverPlacedIsUnscheduled() {
        // Mirrors "T13 Orientamento" never scheduled (analysis.md §6).
        Course course = persistCourse(t13, "Orientamento", fb, 10);

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/reconciliation")
            .then()
            .statusCode(200)
            .body("rows.find { it.courseId == " + course.id + " }.assignedHours", equalTo(10))
            .body("rows.find { it.courseId == " + course.id + " }.scheduledHours", equalTo(0))
            .body("rows.find { it.courseId == " + course.id + " }.delta", equalTo(10))
            .body("rows.find { it.courseId == " + course.id + " }.status", equalTo("UNSCHEDULED"));
    }

    @Test
    void partiallyScheduledCourseIsMismatch() {
        Course course = persistCourse(rossi, "Italiano", fc, 20);
        persistScheduledLessons(course, 15, false, LocalDateTime.of(2027, 3, 1, 8, 0));

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/reconciliation")
            .then()
            .statusCode(200)
            .body("rows.find { it.courseId == " + course.id + " }.assignedHours", equalTo(20))
            .body("rows.find { it.courseId == " + course.id + " }.scheduledHours", equalTo(15))
            .body("rows.find { it.courseId == " + course.id + " }.delta", equalTo(5))
            .body("rows.find { it.courseId == " + course.id + " }.status", equalTo("MISMATCH"))
            .body("rows.find { it.courseId == " + course.id + " }.pinnedHours", equalTo(0));
    }

    @Test
    void pseudoStageCourseIsFlaggedAndCountsPinnedHours() {
        // Mirrors G08 Stage: assigned 300, scheduled 276 in the golden data;
        // scaled down here to keep the fixture small (30 assigned / 27 scheduled).
        Course stageCourse = persistCourse(stagePseudo, "Stage", fc, 30);
        persistScheduledLessons(stageCourse, 27, true, LocalDateTime.of(2027, 4, 1, 8, 0));

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/reconciliation")
            .then()
            .statusCode(200)
            .body("rows.find { it.courseId == " + stageCourse.id + " }.pseudo", equalTo(true))
            .body("rows.find { it.courseId == " + stageCourse.id + " }.status", equalTo("MISMATCH"))
            .body("rows.find { it.courseId == " + stageCourse.id + " }.delta", equalTo(3))
            .body("rows.find { it.courseId == " + stageCourse.id + " }.pinnedHours", equalTo(27));
    }

    @Test
    void expectedUnscheduledHoursDefaultsToZeroForOrdinaryCourses() {
        Course course = persistCourse(rossi, "Matematica", fb, 3);
        persistScheduledLessons(course, 3, true, LocalDateTime.of(2027, 3, 1, 8, 0));

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/reconciliation")
            .then()
            .statusCode(200)
            .body("rows.find { it.courseId == " + course.id + " }.expectedUnscheduledHours", equalTo(0));
    }

    // TASK-373 code review H1: the golden Stage course's own delta is, by
    // construction, exactly its closure-day + beyond-horizon hours (see
    // GoldenScheduleImportService#reconcileStageCourseHours) - a
    // structurally expected shortfall, not an actionable one. Mirrors G12
    // Stage: assigned 295, scheduled 1, of which 294 is expected -
    // actionable delta (delta - expectedUnscheduledHours) is exactly 0.
    @Test
    void pseudoStageCourseExposesExpectedUnscheduledHoursForActionableDeltaComputation() {
        Course stageCourse = persistCourse(stagePseudo, "Stage", fc, 295);
        setExpectedUnscheduledHours(stageCourse, 294);
        persistScheduledLessons(stageCourse, 1, true, LocalDateTime.of(2027, 4, 1, 8, 0));

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/reconciliation")
            .then()
            .statusCode(200)
            .body("rows.find { it.courseId == " + stageCourse.id + " }.delta", equalTo(294))
            .body("rows.find { it.courseId == " + stageCourse.id + " }.expectedUnscheduledHours", equalTo(294));
    }

    @Test
    void rollupsAndSummaryAggregateAcrossCourses() {
        Course ok = persistCourse(rossi, "Matematica", fb, 3);
        persistScheduledLessons(ok, 3, true, LocalDateTime.of(2027, 3, 1, 8, 0));

        persistCourse(t13, "Orientamento", fb, 10); // UNSCHEDULED

        Course mismatch = persistCourse(rossi, "Italiano", fc, 20);
        persistScheduledLessons(mismatch, 15, false, LocalDateTime.of(2027, 3, 2, 8, 0));

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/reconciliation")
            .then()
            .statusCode(200)
            // Rossi rollup: (3+20) assigned, (3+15) scheduled => delta 5
            .body("teacherRollups.find { it.name == 'Rossi' }.assignedHours", equalTo(23))
            .body("teacherRollups.find { it.name == 'Rossi' }.scheduledHours", equalTo(18))
            .body("teacherRollups.find { it.name == 'Rossi' }.delta", equalTo(5))
            // Rossi: Matematica is OK, Italiano is MISMATCH => 1 issue (not a summed-delta sign)
            .body("teacherRollups.find { it.name == 'Rossi' }.issueCount", equalTo(1))
            // G03 group rollup: 3 (Matematica, scheduled) + 10 (Orientamento, unscheduled) assigned
            .body("groupRollups.find { it.name == 'G03' }.assignedHours", equalTo(13))
            .body("groupRollups.find { it.name == 'G03' }.scheduledHours", equalTo(3))
            // G03: Matematica is OK, Orientamento is UNSCHEDULED => 1 issue
            .body("groupRollups.find { it.name == 'G03' }.issueCount", equalTo(1))
            .body("summary.okCount", equalTo(1))
            .body("summary.unscheduledCount", equalTo(1))
            .body("summary.mismatchCount", equalTo(1))
            .body("summary.totalCourses", equalTo(3));
    }

    @Test
    void rollupIssueCountExcludesPseudoRowsEvenWhenMismatched() {
        // Real mismatch: counts as an issue in the group rollup.
        Course real = persistCourse(rossi, "Italiano", fc, 20);
        persistScheduledLessons(real, 15, false, LocalDateTime.of(2027, 3, 1, 8, 0));

        // Pseudo mismatch on the same group: expected by design, must NOT inflate issueCount.
        Course stageCourse = persistCourse(stagePseudo, "Stage", fc, 30);
        persistScheduledLessons(stageCourse, 27, true, LocalDateTime.of(2027, 4, 1, 8, 0));

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/reconciliation")
            .then()
            .statusCode(200)
            .body("groupRollups.find { it.name == 'G08' }.issueCount", equalTo(1));
    }

    @Test
    void drillDownReturnsOnlyScheduledLessonsForThatCourse() {
        Course course = persistCourse(rossi, "Matematica", fb, 5);
        persistScheduledLessons(course, 3, true, LocalDateTime.of(2027, 3, 1, 8, 0));

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/reconciliation/" + course.id + "/lessons")
            .then()
            .statusCode(200)
            .body("size()", equalTo(3))
            .body("[0].pinned", equalTo(true));
    }

    // ─── Helper methods ─────────────────────────────────────────────────

    @Transactional
    Teacher persistTeacher(String name, boolean pseudo) {
        Teacher t = new Teacher(name);
        t.pseudo = pseudo;
        t.persist();
        return t;
    }

    @Transactional
    StudentGroup persistGroup(String name) {
        StudentGroup g = new StudentGroup();
        g.name = name;
        g.size = 20;
        g.persist();
        return g;
    }

    @Transactional
    Course persistCourse(Teacher teacher, String subject, StudentGroup group, int totalHours) {
        Course c = new Course(subject, teacher, group, totalHours);
        c.persist();
        return c;
    }

    @Transactional
    void setExpectedUnscheduledHours(Course course, int hours) {
        Course.<Course>findById(course.id).expectedUnscheduledHours = hours;
    }

    @Transactional
    void persistScheduledLessons(Course course, int count, boolean pinned, LocalDateTime start) {
        for (int i = 0; i < count; i++) {
            LocalDateTime s = start.plusHours(i);
            Timeslot ts = new Timeslot(s, s.plusHours(1));
            ts.persist();
            Lesson lesson = new Lesson(course);
            lesson.timeslot = ts;
            lesson.pinned = pinned;
            lesson.persist();
        }
    }
}
