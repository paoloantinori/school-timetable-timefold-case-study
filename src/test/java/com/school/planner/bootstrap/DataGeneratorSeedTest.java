package com.school.planner.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the 2026/27 seed CSVs (TASK-343, src/main/resources/seed/) against
 * exact entity counts and per-group hour totals that DataGenerator's real
 * parsing logic (generateTeachers/generateStudentGroups/generateRooms/
 * generateCourses) must produce on a fresh seed.
 *
 * <p>Calls DataGenerator.loadSeedCsv()/PSEUDO_COURSE_TEACHERS directly
 * (package-private, same package) instead of re-implementing the parsing
 * rules, so this test can't silently drift from the parser it verifies.</p>
 *
 * <p>Plain JUnit (no @QuarkusTest), matching the pattern already established
 * by DataGeneratorCalendarTest/GroupCalendarSeedTest for exactly this reason:
 * an earlier attempt at a full @QuarkusTest + @TestProfile integration test
 * that actually ran DataGenerator.onStartup() against a real H2 instance
 * caused the Quarkus app-restart-on-profile-switch to leak GroupCalendarRule/
 * StudentGroup rows across the shared H2 in-memory database
 * (jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1) into unrelated test classes,
 * breaking ~40 other tests' cleanup routines with FK violations. Verifying
 * the seed data at the CSV-parsing level gets the same confidence without
 * that instability. A true end-to-end onStartup() integration test is left
 * as a follow-up (would need e.g. a dedicated Postgres/Testcontainers
 * profile that doesn't share the H2 instance).</p>
 */
class DataGeneratorSeedTest {

    @Test
    void teachersCsvHas26Teachers() {
        // TASK-368: teachers.csv gained 2 optional columns (maxEarlyStartDaysPerWeek,
        // requiredFreeDaysPerWeek), so the expected column count is now 6, not 4.
        assertEquals(26, DataGenerator.loadSeedCsv("teachers.csv", 6).size());
    }

    @Test
    void groupsCsvHas14GroupsIncludingG14() {
        List<String[]> rows = DataGenerator.loadSeedCsv("groups.csv", 3);
        assertEquals(14, rows.size());
        assertEquals(1, rows.stream().filter(r -> r[0].trim().equals("G14")).count(),
                "G14 must be present (re-added, TASK-343)");
    }

    @Test
    void roomsCsvHas18RoomsR1722R180() {
        // 18 rooms. R17 has real capacity 22 (referente 29/07 via maintainer: usata solo per
        // saldatura, 22 postazioni - era 0); R18 stays cap 0 (referente: non usata,
        // fuori dal parco aule). The extractor originally dropped R17 and R18
        // (blank/zero capacity) due to a falsy-value bug, fixed 2026-07-16; verified directly
        // against the source xlsx (see DataGenerator.generateRooms() javadoc).
        List<String[]> rows = DataGenerator.loadSeedCsv("rooms.csv", 2);
        assertEquals(18, rows.size());
        assertEquals(1, rows.stream().filter(r -> r[0].trim().equals("R17") && r[1].trim().equals("22")).count());
        assertEquals(1, rows.stream().filter(r -> r[0].trim().equals("R18") && r[1].trim().equals("0")).count());
    }

    @Test
    void incarichiCsvSkipsExactly28PseudoRows() {
        // TASK-371 golden-grid correction: 29 - 1 = 28 pseudo-rows (dropped
        // the stale G04 RA pool row), 268 - 2 = 266 real courses (that same
        // RA row plus a dropped real row). Provenance in seed-csv-format.md.
        // TASK-434 (2026-07-28): +G04 Stage row (240h golden-faithful) -> 29 pseudo-rows.
        // TASK-592 cure (11/09): the G08 RA pool row is REPLACED by 9 real
        // per-teacher rows (the golden's own split, 41h) -> 28 pseudo-rows, 275 real courses.
        List<String[]> rows = DataGenerator.loadSeedCsv("incarichi.csv", 4);
        long pseudoCount = rows.stream().filter(r -> DataGenerator.PSEUDO_COURSE_TEACHERS.contains(r[0].trim())).count();
        assertEquals(28, pseudoCount, "28 pseudo-rows (Stage/Esame/Totale ore corso; the RA row became 9 real rows)");
        assertEquals(275, rows.size() - pseudoCount, "275 real courses after skipping pseudo-rows");
    }

    @Test
    void pseudoCourseTeachersMatchesDocumentedSet() {
        // Drift guard (TASK-374 code review E4): DataGenerator.PSEUDO_COURSE_TEACHERS
        // is duplicated by hand in scripts/analyze/shared.py's PSEUDO_TEACHERS
        // (a cross-language runtime check isn't feasible for a Python
        // script, so this test can't verify shared.py's copy directly). This
        // pins the Java side by hard-listing the expected values: an edit to
        // PSEUDO_COURSE_TEACHERS that changes what's covered here fails this
        // test, which is a deliberate prompt to also update shared.py's copy
        // by hand - not an automated cross-language guarantee.
        assertEquals(Set.of("Stage", "Esame", "RA", "Totale ore corso"), DataGenerator.PSEUDO_COURSE_TEACHERS);
    }

    @Test
    void perGroupCourseHourTotalsMatchIncarichiCsv() {
        // 3 groups incl. one FC. TASK-371 golden-grid correction: G04
        // drops 1040 -> 750 and no longer matches "Totale ore corso" (990) -
        // an expected residual, not forced to reconcile. TASK-592 cure (11/09):
        // G08 626 -> 667 (the 41h RA pool distributed per the golden's own split,
        // matching the golden count exactly). G14 drops 120 -> 115. Full provenance and per-group
        // residual table in seed-csv-format.md; see also design doc section
        // A/C for the G04 DUALE stage exception.
        List<String[]> rows = DataGenerator.loadSeedCsv("incarichi.csv", 4);
        assertGroupTotal(rows, "G04", 750);
        assertGroupTotal(rows, "G08", 667);
        assertGroupTotal(rows, "G14", 115);
    }

    private void assertGroupTotal(List<String[]> rows, String groupName, int expectedHours) {
        int total = rows.stream()
                .filter(r -> !DataGenerator.PSEUDO_COURSE_TEACHERS.contains(r[0].trim()))
                .filter(r -> r[2].trim().equals(groupName))
                .mapToInt(r -> (int) Math.round(Double.parseDouble(r[3].trim())))
                .sum();
        assertEquals(expectedHours, total, "course-hour total for " + groupName);
    }

    @Test
    void aggregateCourseHours_mergesDuplicateKeysSummingHours() {
        // Synthetic duplicate row (TASK-343 code review, Y1): two incarichi.csv
        // rows sharing the same (teacher, subject, group) key must merge into
        // one Course with summed hours, not become two duplicate Courses.
        List<String[]> rows = List.of(
                new String[]{"Rossi", "Matematica", "G04", "2"},
                new String[]{"Rossi", "Matematica", "G04", "3"}
        );
        Map<DataGenerator.CourseKey, Integer> aggregated = DataGenerator.aggregateCourseHours(rows).hoursByKey();
        assertEquals(1, aggregated.size(), "duplicate (teacher,subject,group) key must merge into one entry");
        assertEquals(5, aggregated.get(new DataGenerator.CourseKey("Rossi", "Matematica", "G04")),
                "hours must be summed across the merged rows");
    }

    @Test
    void aggregateCourseHours_realIncarichiCsvHasNoDuplicateKeys() {
        // 275 real courses (TASK-592 cure: +9 RA-distributed rows) have unique (teacher,subject,group) keys after the
        // TASK-371 golden-grid correction (verified against the 2026/27
        // seed) - the merge in aggregateCourseHours() is dormant on current
        // data, only activating if a future seed update introduces a real
        // duplicate.
        List<String[]> rows = DataGenerator.loadSeedCsv("incarichi.csv", 4);
        DataGenerator.CourseAggregation aggregation = DataGenerator.aggregateCourseHours(rows);
        assertEquals(275, aggregation.hoursByKey().size(), "275 unique (teacher,subject,group) keys, no merges on today's data");
        assertEquals(28, aggregation.skippedPseudoRows(), "28 pseudo-rows skipped, matching incarichiCsvSkipsExactly28PseudoRows");
    }

    @Test
    void availabilityRulesCsvHas42RowsCoveringTwelveTeachers() {
        // TASK-345/374/368: transcription of the "Vincoli Docenti" sheet's expressible
        // clauses, golden-derived rules added/narrowed 2026-07-17 (TASK-374), plus the
        // 2026-07-17 docx revision (TASK-368: T04/T05 extended to 14:00
        // Mon-Thu, T05's Tue-afternoon asymmetry dropped, T11's daily slot-0
        // block removed in favor of the weekly M24 cap) - see
        // claudedocs/spalmone-2026-27/availability-not-yet-modeled.md.
        // Q22 (2026-07-24, maintainer's assumption pending school confirmation): 19 new
        // golden-derived fixed-day rows for teachers whose Vincoli Docenti cells were
        // EMPTY yet whose golden hours are day-concentrated (T01 100% Thu,
        // T07 100% Wed, T19/T24 100% Mon+Tue, T22 0h Wed/Fri,
        // T12 0h Wed/Thu, T17 0h Mon) - only zero-golden-hours days are
        // blocked, so the golden guard stays clean by construction. See TASK-412
        // and domande-brevi.md Q22.
        List<String[]> rows = DataGenerator.loadSeedCsv("availability_rules.csv", 8);
        assertEquals(42, rows.size());
        assertEquals(5, rows.stream().filter(r -> r[0].trim().equals("T04")).count());
        assertEquals(5, rows.stream().filter(r -> r[0].trim().equals("T05")).count());
        assertEquals(5, rows.stream().filter(r -> r[0].trim().equals("T11")).count());
        assertEquals(1, rows.stream().filter(r -> r[0].trim().equals("T10")).count());
        assertEquals(7, rows.stream().filter(r -> r[0].trim().equals("T12")).count());
        assertEquals(1, rows.stream().filter(r -> r[0].trim().equals("T16")).count());
        assertEquals(4, rows.stream().filter(r -> r[0].trim().equals("T24")).count());
        assertEquals(4, rows.stream().filter(r -> r[0].trim().equals("T01")).count());
        assertEquals(4, rows.stream().filter(r -> r[0].trim().equals("T07")).count());
        assertEquals(3, rows.stream().filter(r -> r[0].trim().equals("T19")).count());
        assertEquals(2, rows.stream().filter(r -> r[0].trim().equals("T22")).count());
        assertEquals(1, rows.stream().filter(r -> r[0].trim().equals("T17")).count());
        assertEquals(19, rows.stream().filter(r -> r[7] != null && r[7].trim().startsWith("Q22")).count());
    }

    @Test
    void availabilityRulesCsvHasTheGoldenDerivedTask374Values() {
        // TASK-374 code review E7: availabilityRulesCsvHas27RowsCoveringSevenTeachers
        // only checks per-teacher row COUNTS, which would stay green even if
        // a row's dayOfWeek/slots/type were edited to something wrong. This
        // pins the actual VALUES of the three golden-derived rules, per
        // availability-not-yet-modeled.md sections 4-6.
        List<String[]> rows = DataGenerator.loadSeedCsv("availability_rules.csv", 8);

        assertRecurringVacationRow(rows, "T10", "THURSDAY", 0, 3);
        assertRecurringVacationRow(rows, "T12", "MONDAY", 0, 1);
        assertRecurringVacationRow(rows, "T12", "TUESDAY", 0, 1);
        assertRecurringVacationRow(rows, "T16", "TUESDAY", 0, 2);
    }

    /** Asserts exactly one recurring (no effectiveFrom/To) VACATION row exists
     * for (teacher, dayOfWeek) with the given [minSlot, maxSlot] range. */
    private void assertRecurringVacationRow(List<String[]> rows, String teacher, String dayOfWeek,
            int expectedMinSlot, int expectedMaxSlot) {
        List<String[]> matches = rows.stream()
                .filter(r -> r[0].trim().equals(teacher) && r[1].trim().equals(dayOfWeek)
                        && r[5].trim().isEmpty() && r[6].trim().isEmpty())
                .toList();
        assertEquals(1, matches.size(),
                teacher + "/" + dayOfWeek + " must have exactly one recurring (undated) rule");
        String[] row = matches.get(0);
        assertEquals(expectedMinSlot, Integer.parseInt(row[2].trim()), teacher + "/" + dayOfWeek + " minSlot");
        assertEquals(expectedMaxSlot, Integer.parseInt(row[3].trim()), teacher + "/" + dayOfWeek + " maxSlot");
        assertEquals("VACATION", row[4].trim(), teacher + "/" + dayOfWeek + " type");
    }

    @Test
    void seedCsvsAreByteIdenticalToClaudedocsCounterparts() throws IOException {
        // TASK-343 code review (Y8): src/main/resources/seed/*.csv is a
        // hand-copied duplicate of claudedocs/spalmone-2026-27/data/*.csv (the
        // extractor's raw output). Guards against the two silently drifting
        // apart (e.g. someone patching the runtime seed copy without updating
        // the traceable claudedocs source, or vice versa).
        Path repoRoot = Path.of(System.getProperty("user.dir"));
        Path claudedocsDir = repoRoot.resolve("claudedocs/spalmone-2026-27/data");
        Assumptions.assumeTrue(Files.isDirectory(claudedocsDir),
                "claudedocs/spalmone-2026-27/data is absent (e.g. a CI checkout without claudedocs) - skipping byte-identical check");

        for (String name : List.of("teachers.csv", "groups.csv", "rooms.csv", "incarichi.csv", "lessons.csv", "availability_rules.csv",
                "calendario_moda.csv", "calendario_fc.csv", "calendario_class_days.csv")) {
            Path seedPath = repoRoot.resolve("src/main/resources/seed/" + name);
            Path claudedocsPath = claudedocsDir.resolve(name);
            assertArrayEquals(Files.readAllBytes(claudedocsPath), Files.readAllBytes(seedPath),
                    name + ": src/main/resources/seed/ copy must be byte-identical to claudedocs/spalmone-2026-27/data/");
        }
    }

    @Test
    void lessonsCsvHas12979Rows() {
        // TASK-346: the golden hand-made timetable ("Orario in righe" sheet).
        // 12,979 data rows (header excluded), see analysis.md section 1 and
        // GoldenScheduleImportService.
        assertEquals(12979, DataGenerator.loadSeedCsv("lessons.csv", 7).size());
    }

}
