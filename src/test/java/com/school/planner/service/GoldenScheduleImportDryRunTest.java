package com.school.planner.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.school.planner.service.GoldenScheduleImportService.CourseKey;
import com.school.planner.service.GoldenScheduleImportService.RowCategorization;
import com.school.planner.service.GoldenScheduleImportService.TimeslotKey;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.util.SeedCsv;
import com.school.planner.util.SubjectNormalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real 12,979-row golden CSV
 * ({@code claudedocs/spalmone-2026-27/data/lessons.csv}, byte-identical to
 * {@code src/main/resources/seed/lessons.csv}) through
 * {@link GoldenScheduleImportService#categorize} without a DB - plain JUnit,
 * matching {@code DataGeneratorSeedTest}'s rationale (a full
 * {@code onStartup()} @QuarkusTest against the shared H2 instance previously
 * leaked entities across test classes and broke ~40 unrelated tests).
 *
 * <p>The known-Timeslot predicate is derived from {@code TimeslotSequence}'s
 * public calendar constants plus the raw {@code closures.csv} extract (not on
 * the runtime classpath - {@code DataGenerator} hardcodes its own
 * transcription of it instead); the known-Course set is derived from
 * {@code incarichi.csv} the same way {@code GoldenScheduleImportService}
 * itself builds it from the real Course table. This exercises the exact same
 * production {@code categorize()} method against real data, so these numbers
 * can't silently drift from what a real golden import would report.</p>
 *
 * <p>Expected counts below were cross-checked against an independent Python
 * re-implementation of {@code categorize()} run directly against the real
 * CSVs (2026-07-17, TASK-346 code review M8) - see the "how to recompute"
 * comment block below for how to redo that check if the data changes.</p>
 */
class GoldenScheduleImportDryRunTest {

    /** Mirrors DataGenerator.PSEUDO_COURSE_TEACHERS (bootstrap package, package-private). */
    private static final Set<String> PSEUDO_COURSE_TEACHERS = Set.of("Stage", "Esame", "RA", "Totale ore corso");

    @Test
    void realCsvCategorization_matchesIndependentlyVerifiedCounts() throws IOException {
        Path repoRoot = Path.of(System.getProperty("user.dir"));
        Path closuresPath = repoRoot.resolve("claudedocs/spalmone-2026-27/data/closures.csv");
        Assumptions.assumeTrue(Files.isRegularFile(closuresPath),
                "claudedocs/spalmone-2026-27/data/closures.csv is absent (e.g. a CI checkout without claudedocs) - skipping");

        Set<LocalDate> closures = loadClosures(closuresPath);
        Set<TimeslotKey> knownTimeslots = buildKnownTimeslots(closures);
        Set<CourseKey> knownCourses = buildKnownCourseKeys();

        List<String[]> rows = SeedCsv.load("lessons.csv", 7);
        RowCategorization cat = GoldenScheduleImportService.categorize(rows, knownTimeslots::contains, knownCourses);

        int unmatchedNoCourseRows = cat.unmatchedNoCourseCounts().values().stream().mapToInt(Integer::intValue).sum();
        // TASK-366: rows with the raw subject abbreviation "RA" under a real
        // teacher and no matching Course are no longer unmatchedNoCourse -
        // categorize() routes them to raNoCourseRows instead, and the real
        // import (GoldenScheduleImportService#createRaMicroCoursesAndLessons)
        // turns each into its own per-teacher "Recuperi ed Approfondimenti"
        // micro-course and pins its Lessons, instead of dropping them (the
        // drop used to punch mid-day holes and fabricate 22 H17 violations -
        // see claudedocs/spalmone-2026-27/golden-audit.md).
        int raRowsBecomeMicroCourse = cat.raNoCourseRows().values().stream().mapToInt(List::size).sum();

        // Total rows, closed-count and stage-on-closure (M8/TASK-373): derived
        // here directly from the raw CSV bytes, independent of both
        // SeedCsv.load() and categorize() - so these assertions track
        // lessons.csv automatically (no literal to hand-update) if a future
        // re-extraction adds/removes rows, changes how many are closed, or
        // changes which stage rows fall on closure days. The other five
        // counts below CANNOT be derived this cheaply - they depend on the
        // Timeslot calendar (closures.csv) and the Course table
        // (incarichi.csv), not just lessons.csv alone - so they stay pinned
        // literals.
        List<String> rawLines = RawLessonsCsv.readRawLines();
        assertEquals(rawLines.size() - 1, rows.size(), "total data rows (raw line count of lessons.csv, minus the header)");
        Map<String, Integer> expectedStageOnClosure = RawLessonsCsv.stageOnClosureByGroup(rawLines);
        int expectedStageOnClosureTotal = expectedStageOnClosure.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(expectedStageOnClosure, cat.stageOnClosureByGroup(),
                "stage-on-closure rows per group (TASK-373: closed=True AND teacher=Stage), derived "
                        + "independently from lessons.csv's own header + raw column values");
        assertEquals(rawClosedOrBlankTeacherCount(rawLines), cat.skippedClosed() + expectedStageOnClosureTotal,
                "closed=True or blank-teacher rows now split between skippedClosed and stage-on-closure "
                        + "(derived from lessons.csv's own header + raw column values)");

        // Code review H4: open-day (closed=False) Stage rows dated beyond
        // TimeslotSequence.SCHOOL_YEAR_END, derived independently from
        // lessons.csv's own header + raw column values, same rationale as
        // stage-on-closure above.
        Map<String, Integer> expectedStageBeyondHorizon =
                RawLessonsCsv.stageBeyondHorizonByGroup(rawLines, TimeslotSequence.SCHOOL_YEAR_END);
        int expectedStageBeyondHorizonTotal = expectedStageBeyondHorizon.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(expectedStageBeyondHorizon, cat.stageBeyondHorizonByGroup(),
                "stage-beyond-horizon rows per group (code review H4: closed=False AND teacher=Stage AND no "
                        + "matching Timeslot), derived independently from lessons.csv's own header + raw column values");

        // How to recompute the six counts below if this ever needs to
        // change: categorize() IS the reference implementation - there is no
        // simpler formula. A change in any of them means either the data
        // changed (lessons.csv, incarichi.csv - re-run the extractor and
        // re-copy both csv locations per seed-csv-format.md) or the calendar
        // changed (TimeslotSequence.SCHOOL_YEAR_START/END/SLOT_START_TIMES,
        // or claudedocs/spalmone-2026-27/data/closures.csv). To regenerate:
        // re-run this test's own buildKnownTimeslots()/buildKnownCourseKeys()
        // helpers against the updated CSVs (they ARE the independent
        // derivation - built from incarichi.csv/closures.csv, not copied from
        // GoldenScheduleImportService) and read the new counts off the
        // failure message, or re-run categorize() directly in a REPL/test.
        assertEquals(10501, cat.realMatches().size(), "real-teacher rows matched to a Course (TASK-550 extension: +5 recovery-day rows)");
        assertEquals(2009, cat.stageMatches().size(), "Stage rows matched to a Timeslot (TASK-550 extension: the 16 recovery-day rows now match)");
        assertEquals(0, cat.skippedEsame(), "Esame rows (never scheduled in the golden grid)");
        assertEquals(117, cat.skippedClosed(), "closed=True or blank-teacher rows, excluding stage-on-closure");
        // TASK-550 extension: the horizon now includes 06-11..15, so every row in
        // that window (Stage and non-Stage) matches a Timeslot - nothing unmatched.
        assertEquals(0, cat.unmatchedNoTimeslot(), "rows beyond the Timeslot horizon, excluding Stage (TASK-550 extension: all in-horizon now)");
        // TASK-366: all 31 of the previously-unmatched rows are the G08 "RA"
        // rows - they now import via per-teacher micro-courses instead
        // (imported effectively rises by 31 once GoldenScheduleImportService
        // actually persists them; categorize() itself only reports the bucket shift).
        assertEquals(0, unmatchedNoCourseRows, "rows with no matching Course (RA rows now handled separately)");
        assertEquals(41, raRowsBecomeMicroCourse, "G08 'RA' rows now routed to per-teacher micro-courses (TASK-550 extension: +10 recovery-day rows)");

        // Every row must fall into exactly one bucket.
        assertEquals(rows.size(), cat.realMatches().size() + cat.stageMatches().size() + cat.skippedEsame()
                + cat.skippedClosed() + expectedStageOnClosureTotal + expectedStageBeyondHorizonTotal
                + cat.unmatchedNoTimeslot() + unmatchedNoCourseRows + raRowsBecomeMicroCourse,
                "bucket counts must partition all rows");

        // IMPORTANT for the orchestrator: distinct unmatched-no-course triples.
        List<CourseKey> triples = cat.unmatchedNoCourseCounts().entrySet().stream()
                .sorted(Map.Entry.<CourseKey, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        System.out.println("TASK-346 dry-run: " + triples.size() + " distinct unmatched-no-course triples ("
                + unmatchedNoCourseRows + " rows):");
        for (CourseKey key : triples) {
            System.out.println("  teacher='" + key.teacherName() + "' subject='" + key.subject()
                    + "' group='" + key.groupName() + "' rows=" + cat.unmatchedNoCourseCounts().get(key));
        }
        assertTrue(triples.size() <= 15,
                "expected <=15 distinct unmatched-no-course triples, got " + triples.size() + ": " + triples);
    }

    /**
     * Independently recomputes the closed/blank-teacher row count straight
     * from lessons.csv's own header + raw column values (M8) - column indices
     * looked up by name via {@link RawLessonsCsv}, so this can't silently
     * reuse (and thus never catch a drift in)
     * {@code GoldenScheduleImportService}'s private COL_CLOSED/COL_TEACHER indices.
     */
    private long rawClosedOrBlankTeacherCount(List<String> rawLines) {
        String[] header = rawLines.get(0).split(",", -1);
        int closedCol = RawLessonsCsv.columnIndex(header, "closed");
        int teacherCol = RawLessonsCsv.columnIndex(header, "teacher");
        return rawLines.stream().skip(1)
                .map(line -> line.split(",", -1))
                .filter(cols -> "True".equals(cols[closedCol].trim()) || cols[teacherCol].trim().isEmpty())
                .count();
    }

    private Set<LocalDate> loadClosures(Path closuresPath) throws IOException {
        List<String> lines = Files.readAllLines(closuresPath);
        Set<LocalDate> closures = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) { // skip header
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                closures.add(LocalDate.parse(line));
            }
        }
        return closures;
    }

    private Set<TimeslotKey> buildKnownTimeslots(Set<LocalDate> closures) {
        Set<TimeslotKey> timeslots = new HashSet<>();
        LocalDate date = TimeslotSequence.SCHOOL_YEAR_START;
        while (!date.isAfter(TimeslotSequence.SCHOOL_YEAR_END)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !closures.contains(date)) {
                for (LocalTime startTime : TimeslotSequence.SLOT_START_TIMES) {
                    timeslots.add(new TimeslotKey(date, startTime));
                }
            }
            date = date.plusDays(1);
        }
        return timeslots;
    }

    private Set<CourseKey> buildKnownCourseKeys() {
        Set<CourseKey> keys = new LinkedHashSet<>();
        for (String[] row : SeedCsv.load("incarichi.csv", 4)) {
            String teacher = row[0].trim();
            if (PSEUDO_COURSE_TEACHERS.contains(teacher)) continue;
            String subject = SubjectNormalization.normalize(row[1].trim());
            String group = row[2].trim();
            keys.add(new CourseKey(teacher, subject, group));
        }
        return keys;
    }
}
