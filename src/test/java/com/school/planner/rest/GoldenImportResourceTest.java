package com.school.planner.rest;

import java.time.LocalDateTime;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

/**
 * REST Assured tests for {@code POST /api/schedule/import-golden} (TASK-346 M6).
 *
 * <p>Uses the same small, hand-built fixture strategy as
 * {@link com.school.planner.service.GoldenScheduleImportServiceTest} - real
 * rows cherry-picked from the fixed {@code src/main/resources/seed/lessons.csv}
 * plus every {@code StudentGroup} the real {@code incarichi.csv}'s Esame/RA
 * rows reference (M2/M3 accounting courses iterate that file unconditionally).</p>
 *
 * <p><b>Solver-running 409 is intentionally NOT covered here</b> (M6): making
 * it deterministic would need the solver to still be {@code SOLVING_ACTIVE}
 * when the second request lands, but {@code ScheduleResourceTest}'s own
 * comment ("with a tiny 2-lesson problem, solver may complete in less than 1s")
 * shows this test project's construction heuristic can finish before a second
 * synchronous HTTP call is even issued - asserting 409 here would be flaky.
 * The solver-running check itself is covered by inspection (same
 * {@code ScheduleService#getStatus()} source {@code resetSchedule()} uses,
 * checked before any mutation in {@code GoldenScheduleImportService#importGolden}).</p>
 */
@QuarkusTest
class GoldenImportResourceTest {

    @Inject
    EntityManager em;

    // Real row: 2026-09-14,Lunedì,09:00 10:00,False,G03,T02,Orientamento
    private static final LocalDateTime MATCHED_ROW_START = LocalDateTime.of(2026, 9, 14, 9, 0);
    // Real row: 2026-11-20,Venerdì,08:00 09:00,False,G12,Stage,Stage
    private static final LocalDateTime STAGE_ROW_START = LocalDateTime.of(2026, 11, 20, 8, 0);

    // Clean before AND after: this class imports the full golden CSV, leaving
    // thousands of Lesson/Course/Teacher rows in the shared H2 instance. Test
    // classes that run after this one (e.g. GroupCalendarRuleResourceTest,
    // alphabetically next in this package) wipe StudentGroup without expecting
    // Course leftovers, so anything left behind breaks their cleanup with FK
    // violations - same pattern as GroupCalendarRuleResourceTest's own cleanup().
    @BeforeEach
    @AfterEach
    @Transactional
    void cleanDatabase() {
        // Full FK-respecting wipe, same rationale as GoldenScheduleImportServiceTest:
        // the shared H2 instance can carry leftover rows from whatever test ran before this one.
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM LabAssistant").executeUpdate();
        em.createQuery("DELETE FROM LabRotation").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM ProfessionalizzantiDay").executeUpdate();
        em.createQuery("DELETE FROM StagePattern").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
    }

    @Transactional
    void seedFixture() {
        new Timeslot(MATCHED_ROW_START, MATCHED_ROW_START.plusHours(1)).persist();
        new Timeslot(STAGE_ROW_START, STAGE_ROW_START.plusHours(1)).persist();

        new StudentGroup("G03", 20).persist();
        new StudentGroup("G12", 20).persist();
        // Every group incarichi.csv's Esame/RA rows reference (M2/M3) - see
        // GoldenScheduleImportServiceTest.seedSmallFixture() for the same rationale.
        new StudentGroup("G11", 20).persist();
        new StudentGroup("G08", 20).persist();
        new StudentGroup("G10", 20).persist();
        new StudentGroup("G09", 20).persist();
        new StudentGroup("G13", 20).persist();
        new StudentGroup("G04", 20).persist();
        // Code review H4 (TASK-373): with only 2 Timeslots seeded, almost
        // every real Stage row across the whole year (not just closure-day
        // ones) has no matching Timeslot and now lands in
        // stageBeyondHorizonByGroup instead of the generic unmatchedNoTimeslot
        // bucket - findOrCreateStageCourse() is then called for every group
        // with a lessons.csv Stage row, including "G07" (see
        // GoldenScheduleImportServiceTest's identical fixture fix).
        new StudentGroup("G07", 20).persist();

        Teacher t02 = new Teacher("T02");
        t02.persist();
        Course course = new Course("Orientamento", t02, StudentGroup.<StudentGroup>find("name", "G03").firstResult(), 10);
        course.persist();

        // TASK-366: MATCHED_ROW_START (2026-09-14 09:00) is a global Timeslot,
        // not group-specific, so it also matches the real lessons.csv row
        // "2026-09-14,09:00,G08,T11,RA" - a real teacher with no Course
        // for that (subject, group), exercising the RA micro-course path
        // (see GoldenScheduleImportServiceTest's fixture for the full rationale).
        new Teacher("T11").persist();
    }

    @Test
    void refuses409WithoutForceWhenLessonsExist() {
        seedFixture();

        given().when().post("/api/schedule/import-golden")
                .then().statusCode(200)
                .body("imported", greaterThan(0));

        given().when().post("/api/schedule/import-golden")
                .then().statusCode(409)
                .body("error", org.hamcrest.Matchers.containsString("force=true"));
    }

    @Test
    void forcePathSucceedsAndReportsDeletedCounts() {
        seedFixture();

        given().when().post("/api/schedule/import-golden")
                .then().statusCode(200)
                .body("deletedPinned", equalTo(0))
                .body("deletedUnpinned", equalTo(0));

        given().queryParam("force", true).when().post("/api/schedule/import-golden")
                .then().statusCode(200)
                .body("imported", greaterThan(0))
                .body("deletedPinned", greaterThan(0))
                .body("deletedUnpinned", equalTo(0));
    }
}
