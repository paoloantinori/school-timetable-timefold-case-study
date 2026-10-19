package com.school.planner.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.school.planner.util.SeedCsv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain-JUnit categorization test over the real
 * {@code src/main/resources/seed/calendario_moda.csv} (312 rows) - no DB,
 * matching {@code GoldenScheduleImportDryRunTest}'s rationale (a full
 * {@code @QuarkusTest} run against the shared H2 instance leaks entities
 * across test classes).
 *
 * <p>These counts are independently cross-checked (2026-07-17, TASK-364 code
 * review) against a standalone Python re-implementation of the same
 * per-teacher-day aggregation and against
 * {@code scripts/analyze/calendario2027-extract.py}'s own coherence report
 * (section [1]) run directly against the source xlsx - see that script's
 * output for the full reconciliation.</p>
 */
class CalendarioImportDryRunTest {

    private static final int MODA_CSV_COLUMNS = 5; // date,group,initial,hours,shift

    @Test
    void realCsv_totalTeacherDayCountMatchesFormatDoc() {
        List<String[]> rows = SeedCsv.load("calendario_moda.csv", MODA_CSV_COLUMNS);

        // 312 nonzero PE/MU/BO teacher-day cells (the 2027-06-09 G05 PE divergence row excluded — school divergence #8, Orario wins) across 1/2/G09 (MODA TUTTI
        // sheet) - see calendario-scolastico-format.md section 3 and this
        // repo's calendario2027-extract.py coherence report.
        assertEquals(312, rows.size());
    }

    @Test
    void realCsv_1ModaPerTeacherTotals_matchFormatDocReferenceRow() {
        List<String[]> rows = SeedCsv.load("calendario_moda.csv", MODA_CSV_COLUMNS);

        Map<String, Integer> hoursByTeacher = new HashMap<>();
        for (String[] row : rows) {
            if (!"G01".equals(row[1].trim())) continue;
            hoursByTeacher.merge(row[2].trim(), Integer.parseInt(row[3].trim()), Integer::sum);
        }

        // MODA TUTTI's own row-3 reference cells: G01 = PE 143 + MU 180 + BO 24 (347 total).
        assertEquals(143, hoursByTeacher.get("PE"));
        assertEquals(180, hoursByTeacher.get("MU"));
        assertEquals(24, hoursByTeacher.get("BO"));
    }

    @Test
    void realCsv_T07YearlyTotalMatches72Hours() {
        List<String[]> rows = SeedCsv.load("calendario_moda.csv", MODA_CSV_COLUMNS);

        int T07Total = rows.stream()
                .filter(r -> "BO".equals(r[2].trim()))
                .mapToInt(r -> Integer.parseInt(r[3].trim()))
                .sum();

        // 24h x 3 groups = 72h - T07's sole MODA subject is "Tecnologia e
        // creatività" (incarichi.csv), confirmed unambiguous by this exact total.
        assertEquals(72, T07Total);
    }

    @Test
    void realCsv_everyRowUsesAKnownTeacherInitialAndShift() {
        List<String[]> rows = SeedCsv.load("calendario_moda.csv", MODA_CSV_COLUMNS);

        for (String[] row : rows) {
            String initial = row[2].trim();
            String shift = row[4].trim();
            assertTrue(CalendarioImportService.INITIAL_TO_TEACHER.containsKey(initial),
                    "unexpected teacher initial: " + initial);
            assertTrue("first".equals(shift) || "second".equals(shift), "unexpected shift: " + shift);
        }
    }
}
