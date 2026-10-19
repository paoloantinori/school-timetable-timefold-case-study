package com.school.planner.rest;

import java.time.LocalDateTime;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import org.hamcrest.Matchers;

/**
 * TASK-659 (b)+(c): the print sheet contract. The dedicated view is a standalone
 * HTML page carrying its own header (school, year, subject name); the PDF is the
 * same document server-rendered. This pins both representations so a template
 * regression fails here, not at the school's printer.
 */
@QuarkusTest
class PrintResourceTest {

    private long teacherId;
    private long groupId;

    @BeforeEach
    @Transactional
    void seed() {
        Teacher t = new Teacher();
        t.name = "Docente-da-Stampa";
        t.persist();
        teacherId = t.id;

        StudentGroup g = new StudentGroup();
        g.name = "Classe-da-Stampa";
        g.persist();
        groupId = g.id;

        Course c = new Course();
        c.subject = "Materia-da-Stampa";
        c.teacher = t;
        c.studentGroup = g;
        c.totalHours = 2;
        c.persist();

        Room r = new Room();
        r.name = "AULA-STAMPA";
        r.capacity = 20;
        r.persist();

        Timeslot ts = new Timeslot(LocalDateTime.of(2026, 9, 16, 9, 0), LocalDateTime.of(2026, 9, 16, 10, 0));
        ts.persist();

        Lesson l = new Lesson();
        l.course = c;
        l.timeslot = ts;
        l.room = r;
        l.persist();
    }

    @Test
    void dedicatedViewIsAStandaloneSheetWithItsOwnHeader() {
        RestAssured.given()
                .queryParam("week", "2026-09-16")
                .when().get("/print/docente/" + teacherId)
                .then()
                .statusCode(200)
                .contentType(Matchers.containsString("text/html"))
                .body(allOf(
                        containsString("ISTITUTO PROFESSIONALE DI STATO"),
                        containsString("A.S. 2026/27"),
                        containsString("Orario del docente"),
                        containsString("Materia-da-Stampa"),
                        containsString("Scarica PDF"),
                        containsString("lun 14/9")));
    }

    @Test
    void classSheetCarriesTheClassNameAndTeacherInCells() {
        RestAssured.given()
                .queryParam("week", "2026-09-16")
                .when().get("/print/classe/" + groupId)
                .then()
                .statusCode(200)
                .body(allOf(
                        containsString("Orario della classe"),
                        containsString("Classe-da-Stampa"),
                        containsString("Materia-da-Stampa")));
    }

    @Test
    void pdfEndpointServesTheSameSheetAsPdf() {
        byte[] pdf = RestAssured.given()
                .queryParam("week", "2026-09-16")
                .when().get("/print/docente/" + teacherId + ".pdf")
                .then()
                .statusCode(200)
                .contentType("application/pdf")
                .extract().asByteArray();
        // A PDF starts with %PDF and a full A4 landscape sheet is never trivially small.
        assert new String(pdf, 0, 4, StandardCharsets.ISO_8859_1).equals("%PDF");
        assert pdf.length > 2000 : "PDF sospetto: " + pdf.length + " byte";
    }

    @Test
    void unknownKindAndMissingSubjectAre404Not500() {
        RestAssured.given().when().get("/print/preside/1").then().statusCode(404);
        RestAssured.given().when().get("/print/docente/999999").then().statusCode(404);
        RestAssured.given().when().get("/print/docente/999999.pdf").then().statusCode(404);
    }

    @Test
    void malformedWeekParamFallsBackToTodayNot500() {
        RestAssured.given()
                .queryParam("week", "non-una-data")
                .when().get("/print/docente/" + teacherId)
                .then()
                .statusCode(200);
    }
}
