package com.school.planner.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fail-loud, no-quoting CSV loader for hand-verified seed data files on the
 * classpath under {@code /seed/}. Extracted out of DataGenerator (TASK-343
 * code review) so later seeding tasks (TASK-345/360/364) can reuse the same
 * parser without depending on the DataGenerator bootstrap bean.
 */
public final class SeedCsv {

    private SeedCsv() {
        // Utility class - prevent instantiation
    }

    /**
     * Load a seed CSV from the classpath ({@code /seed/<resourceName>}) and
     * parse it into raw {@code String[]} rows (header skipped). Fails loudly
     * (RuntimeException, with file/line context wherever a line is involved)
     * on:
     * <ul>
     *   <li>a missing file</li>
     *   <li>an empty file (no header)</li>
     *   <li>an interior blank line - a blank line followed by at least one more
     *       non-blank data line skews every caller's file-line math (TASK-364
     *       code review, K5); only blank lines trailing the very last data line
     *       (a plain trailing newline, or extra blank lines right before EOF)
     *       are tolerated and silently dropped</li>
     *   <li>a line containing a double-quote character - {@code split(",", -1)}
     *       has no CSV-quoting support, so a quoted field containing a literal
     *       comma would silently misalign columns instead of erroring; rather
     *       than writing a quoting-aware parser for data that has never needed
     *       one, such a line fails loud instead</li>
     *   <li>any row whose column count doesn't match {@code expectedColumns}</li>
     * </ul>
     * There is no silent skip-and-continue for malformed data: these files are
     * hand-verified extracts, and any shape mismatch indicates a real bug (bad
     * copy, wrong extractor version).
     */
    public static List<String[]> load(String resourceName, int expectedColumns) {
        String path = "/seed/" + resourceName;
        InputStream is = SeedCsv.class.getResourceAsStream(path);
        if (is == null) {
            throw new RuntimeException("Seed CSV not found on classpath: " + path);
        }
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new RuntimeException("Seed CSV is empty (no header): " + path);
            }

            // Buffer every remaining line first so a trailing blank line (or
            // several) can be told apart from an interior one: only lines
            // strictly after the last non-blank line are a "trailing newline",
            // everything else is real row data and a blank among them is a bug.
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            int lastNonBlank = lines.size() - 1;
            while (lastNonBlank >= 0 && lines.get(lastNonBlank).isBlank()) {
                lastNonBlank--;
            }

            for (int i = 0; i <= lastNonBlank; i++) {
                line = lines.get(i);
                int lineNumber = i + 2; // 1-based; header is line 1
                if (line.isBlank()) {
                    throw new RuntimeException("Interior blank line in " + path + " at line " + lineNumber
                            + " - only a trailing blank line is tolerated");
                }
                if (line.indexOf('"') >= 0) {
                    throw new RuntimeException("Quoted CSV is not supported in " + path
                            + " at line " + lineNumber + ": " + line);
                }
                String[] cells = line.split(",", -1);
                if (cells.length != expectedColumns) {
                    throw new RuntimeException("Malformed row in " + path + " at line " + lineNumber
                            + ": expected " + expectedColumns + " columns, got " + cells.length
                            + " (" + line + ")");
                }
                rows.add(cells);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read seed CSV: " + path, e);
        }
        return rows;
    }
}
