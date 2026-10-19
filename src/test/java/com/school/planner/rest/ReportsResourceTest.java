package com.school.planner.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Guards the two things that can go wrong in {@link ReportsResource}: the listing
 * must show the generated reports, and the free-form path parameter must not let a
 * request escape the reports directory or read a non-HTML file.
 */
@QuarkusTest
class ReportsResourceTest {

    private static Path reportsRoot;

    @BeforeAll
    static void seedReports() throws IOException {
        // The test profile points planner.reports.dir at target/test-reports (see
        // src/test/resources/application.properties), so this is what the resource lists.
        reportsRoot = Path.of("target/test-reports");
        Files.createDirectories(reportsRoot.resolve("nested"));
        Files.writeString(reportsRoot.resolve("nested/confronto.html"), "<html>report</html>");
        Files.writeString(reportsRoot.resolve("segreto.txt"), "not a report");
    }

    @AfterAll
    static void cleanup() throws IOException {
        try (var walk = Files.walk(reportsRoot)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void indexListsGeneratedReports() {
        given().when().get("/reports")
                .then().statusCode(200)
                .body(containsString("nested/confronto.html"))
                .body(not(containsString("segreto.txt")));
    }

    @Test
    void servesAReportByPath() {
        given().when().get("/reports/nested/confronto.html")
                .then().statusCode(200).body(containsString("report"));
    }

    @Test
    void refusesNonHtmlAndPathTraversal() {
        given().when().get("/reports/segreto.txt").then().statusCode(404);
        // Encoded separators, so the traversal survives client-side normalisation and
        // actually reaches the resource: a plain "/reports/../../etc/passwd" is collapsed
        // to "/etc/passwd" before the request leaves, and never exercises the guard.
        given().urlEncodingEnabled(false).when().get("/reports/..%2F..%2Fpom.xml")
                .then().statusCode(404);
        given().urlEncodingEnabled(false).when().get("/reports/nested%2F..%2F..%2Fpom.xml")
                .then().statusCode(404);
    }
}
