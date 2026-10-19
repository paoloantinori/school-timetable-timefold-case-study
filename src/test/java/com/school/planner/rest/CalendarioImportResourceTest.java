package com.school.planner.rest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.TimeslotSequence;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * REST Assured tests for {@code POST /api/schedule/import-calendario} (TASK-364).
 *
 * <p>Same full-school-year fixture rationale as
 * {@link com.school.planner.service.CalendarioImportServiceTest} - the real,
 * fixed calendario_moda.csv (312 rows) always contains exactly one genuine
 * same-day mixed-shift conflict (2027-06-09, G05), so a fully-seeded
 * profile deterministically produces {@code persisted=false} on a non-dry-run
 * call; that's the "REFUSED" case asserted below, not a fixture bug.</p>
 */
@QuarkusTest
class CalendarioImportResourceTest {

    @Inject
    EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void cleanDatabase() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM LabAssistant").executeUpdate();
        em.createQuery("DELETE FROM LabRotation").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM ProfessionalizzantiDay").executeUpdate();
        em.createQuery("DELETE FROM StagePattern").executeUpdate();
        em.createQuery("DELETE FROM DailyActivityQuota").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
    }

    @Transactional
    void seedFullYearFixture() {
        seedFullYearFixture(Set.of());
    }

    /** Overload excluding specific dates' Timeslots (TASK-364 code review K2) - see the analogous overload in {@code CalendarioImportServiceTest} for why. */
    @Transactional
    void seedFullYearFixture(Set<LocalDate> excludedDates) {
        LocalDate date = TimeslotSequence.SCHOOL_YEAR_START;
        while (!date.isAfter(TimeslotSequence.SCHOOL_YEAR_END)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !excludedDates.contains(date)) {
                for (var startTime : TimeslotSequence.SLOT_START_TIMES) {
                    LocalDateTime start = LocalDateTime.of(date, startTime);
                    new Timeslot(start, start.plusHours(1)).persist();
                }
            }
            date = date.plusDays(1);
        }

        StudentGroup moda1 = new StudentGroup("G01", 20);
        moda1.persist();
        StudentGroup moda2 = new StudentGroup("G05", 20);
        moda2.persist();
        StudentGroup moda3 = new StudentGroup("G09", 20);
        moda3.persist();

        Teacher t17 = new Teacher("T17");
        t17.persist();
        Teacher t16 = new Teacher("T16");
        t16.persist();
        Teacher t07 = new Teacher("T07");
        t07.persist();

        // Real incarichi.csv hours (TASK-379): totalHours is load-bearing for
        // the per-course "Orario wins" budget cap, same fixture convention as
        // CalendarioImportServiceTest.
        int[] moulageHours = {128, 150, 80};
        int[] confezioneHours = {169, 166, 105};
        StudentGroup[] modaGroups = {moda1, moda2, moda3};
        for (int i = 0; i < modaGroups.length; i++) {
            Course moulage = new Course("Modello (Moulage)", t17, modaGroups[i], moulageHours[i]);
            moulage.isLab = true;
            moulage.persist();
            Course confezione = new Course("Confezione", t16, modaGroups[i], confezioneHours[i]);
            confezione.isLab = true;
            confezione.persist();
            Course tecnologia = new Course("Tecnologia e creatività", t07, modaGroups[i], 24);
            tecnologia.isLab = false;
            tecnologia.persist();
        }

        Set<DayOfWeek> weekdays = new HashSet<>(Set.of(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        for (StudentGroup group : new StudentGroup[] {moda1, moda2, moda3}) {
            for (DayOfWeek dow : weekdays) {
                new GroupCalendarRule(group, dow, 0, TimeslotSequence.SLOTS_PER_DAY - 1).persist();
            }
        }
    }

    @Test
    void dryRunReturns200WithFullReportAndNeverPersists() {
        seedFullYearFixture();

        given().queryParam("dryRun", true).when().post("/api/schedule/import-calendario")
                .then().statusCode(200)
                .body("dryRun", equalTo(true))
                .body("persisted", equalTo(false))
                .body("modaRows", equalTo(312))
                .body("modaPlanned", equalTo(293))
                .body("modaSkippedNoTimeslot", equalTo(0)) // TASK-550 extension: 06-14/15 have timeslots now
                .body("modaSkippedBeyondCourseHours", equalTo(19)) // TASK-550 extension: +2 formerly beyond-horizon
                .body("modaFailures.size()", equalTo(0));
    }

    @Test
    void nonDryRunPersistsOnCleanData() {
        // The real seed data is clean since the 2027-06-09 divergence-row exclusion
        // (school divergence #8, 'Orario wins'); the 409-on-validation-failure path
        // is covered at the service layer (isOpenValidationFailure_blocksWholeBatch).
        seedFullYearFixture();

        given().queryParam("dryRun", false).when().post("/api/schedule/import-calendario")
                .then().statusCode(200)
                .body("persisted", equalTo(true))
                .body("modaFailures.size()", equalTo(0));
    }

    @Test
    void reRunWithoutForceRefuses409_forceReRunSucceeds() {
        // TASK-364 code review K2 - REST-level wiring check for the `force`
        // query param; the full idempotent-final-counts assertion lives in
        // CalendarioImportServiceTest (easier to inspect Lesson counts there).
        seedFullYearFixture(Set.of(LocalDate.of(2027, 6, 9)));

        given().queryParam("dryRun", false).when().post("/api/schedule/import-calendario")
                .then().statusCode(200)
                .body("persisted", equalTo(true));

        given().queryParam("dryRun", false).queryParam("force", false).when().post("/api/schedule/import-calendario")
                .then().statusCode(409);

        given().queryParam("dryRun", false).queryParam("force", true).when().post("/api/schedule/import-calendario")
                .then().statusCode(200)
                .body("persisted", equalTo(true));
    }
}
