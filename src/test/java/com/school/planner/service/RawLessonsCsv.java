package com.school.planner.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Raw-bytes access to the seed lessons.csv for tests that must derive
 * expected counts INDEPENDENTLY of both {@code SeedCsv.load()} and
 * {@code GoldenScheduleImportService}'s private column constants (M8,
 * TASK-373): column indices are looked up by header name, so these helpers
 * can't silently reuse (and thus never catch a drift in) the production
 * COL_CLOSED/COL_TEACHER/COL_GROUP indices. Extracted (TASK-373 /simplify
 * pass) from near-identical copies in {@link GoldenScheduleImportDryRunTest}
 * and {@link GoldenScheduleImportServiceTest}.
 */
final class RawLessonsCsv {

    private RawLessonsCsv() {}

    /** Raw lines of the lessons.csv classpath resource, header included. */
    static List<String> readRawLines() throws IOException {
        try (var is = RawLessonsCsv.class.getResourceAsStream("/seed/lessons.csv")) {
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.toList());
        }
    }

    /**
     * Independently recomputes stage-on-closure rows per group (closed=True
     * AND teacher=Stage) straight from lessons.csv's own header + raw column
     * values (TASK-373).
     */
    static Map<String, Integer> stageOnClosureByGroup(List<String> rawLines) {
        String[] header = splitLine(rawLines.get(0));
        int closedCol = columnIndex(header, "closed");
        int teacherCol = columnIndex(header, "teacher");
        return countMatchingByGroup(rawLines,
                cols -> "True".equals(cols[closedCol].trim()) && "Stage".equals(cols[teacherCol].trim()));
    }

    /**
     * Independently recomputes open-day (closed=False) Stage rows dated
     * after {@code horizonEnd} per group - the "beyond the Timeslot horizon"
     * bucket (TASK-373 code review H4), e.g. G04's 2027-06-11..14 rows.
     * Straight from lessons.csv's own header + raw column values, so it can't
     * silently reuse (and thus never catch a drift in) categorize()'s own
     * timeslot-existence check.
     */
    static Map<String, Integer> stageBeyondHorizonByGroup(List<String> rawLines, LocalDate horizonEnd) {
        String[] header = splitLine(rawLines.get(0));
        int dateCol = columnIndex(header, "date");
        int closedCol = columnIndex(header, "closed");
        int teacherCol = columnIndex(header, "teacher");
        return countMatchingByGroup(rawLines,
                cols -> "False".equals(cols[closedCol].trim()) && "Stage".equals(cols[teacherCol].trim())
                        && LocalDate.parse(cols[dateCol].trim()).isAfter(horizonEnd));
    }

    /**
     * Shared scan: splits every data line, counts by the "group" column
     * every row for which {@code matches} holds (TASK-373 /simplify pass -
     * {@link #stageOnClosureByGroup} and {@link #stageBeyondHorizonByGroup}
     * previously duplicated this loop, differing only in the row predicate).
     */
    private static Map<String, Integer> countMatchingByGroup(List<String> rawLines, Predicate<String[]> matches) {
        String[] header = splitLine(rawLines.get(0));
        int groupCol = columnIndex(header, "group");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String line : rawLines.subList(1, rawLines.size())) {
            String[] cols = splitLine(line);
            if (matches.test(cols)) {
                counts.merge(cols[groupCol].trim(), 1, Integer::sum);
            }
        }
        return counts;
    }

    static int columnIndex(String[] header, String columnName) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equals(columnName)) {
                return i;
            }
        }
        throw new IllegalStateException("lessons.csv header has no '" + columnName + "' column: " + String.join(",", header));
    }

    /**
     * Naive comma split with a fail-loud guard (TASK-373 code review H5,
     * mirrors {@code SeedCsv}'s quoting guard): lessons.csv has never needed
     * CSV quoting, so rather than writing a quoting-aware parser for data
     * that doesn't require one, a literal double-quote character - which
     * would otherwise silently misalign columns under a plain {@code split} -
     * fails loud instead.
     */
    private static String[] splitLine(String line) {
        if (line.indexOf('"') >= 0) {
            throw new IllegalStateException("Quoted CSV is not supported in lessons.csv: " + line);
        }
        return line.split(",", -1);
    }
}
