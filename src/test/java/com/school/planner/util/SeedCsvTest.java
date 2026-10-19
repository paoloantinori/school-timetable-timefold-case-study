package com.school.planner.util;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SeedCsv} (TASK-343 code review, Y3/Y9): the fail-loud
 * parser extracted out of DataGenerator. Fixtures live under
 * src/test/resources/seed/ with names distinct from the real production
 * CSVs, so they never collide with them on the test classpath.
 */
class SeedCsvTest {

    @Test
    void loadsValidCsvIntoRows() {
        List<String[]> rows = SeedCsv.load("seedcsvtest-valid.csv", 3);
        assertEquals(2, rows.size());
        assertEquals("1", rows.get(0)[0]);
        assertEquals("6", rows.get(1)[2]);
    }

    @Test
    void quotedFieldFailsLoudWithFileAndLine() {
        // split(",", -1) has no CSV-quoting support: a quoted field containing
        // a literal comma would otherwise silently misalign columns instead
        // of erroring, so any double-quote character fails loud.
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> SeedCsv.load("seedcsvtest-quoted.csv", 3));
        assertTrue(ex.getMessage().contains("Quoted CSV is not supported"), ex.getMessage());
        assertTrue(ex.getMessage().contains("seedcsvtest-quoted.csv"), ex.getMessage());
        assertTrue(ex.getMessage().contains("line 2"), ex.getMessage());
    }

    @Test
    void malformedColumnCountFailsLoudWithFileAndLine() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> SeedCsv.load("seedcsvtest-malformed.csv", 3));
        assertTrue(ex.getMessage().contains("Malformed row"), ex.getMessage());
        assertTrue(ex.getMessage().contains("seedcsvtest-malformed.csv"), ex.getMessage());
        assertTrue(ex.getMessage().contains("line 3"), ex.getMessage());
    }

    @Test
    void interiorBlankLineFailsLoud() {
        // TASK-364 code review K5: an interior blank line skews every caller's
        // file-line math, so it must fail loud rather than be silently skipped.
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> SeedCsv.load("seedcsvtest-interior-blank.csv", 3));
        assertTrue(ex.getMessage().contains("Interior blank line"), ex.getMessage());
        assertTrue(ex.getMessage().contains("seedcsvtest-interior-blank.csv"), ex.getMessage());
        assertTrue(ex.getMessage().contains("line 3"), ex.getMessage());
    }

    @Test
    void trailingBlankLineIsTolerated() {
        // A blank line (or several) after the last real data row - just a
        // trailing newline in file-editor terms - must not throw or be counted as a row.
        List<String[]> rows = SeedCsv.load("seedcsvtest-trailing-blank.csv", 3);
        assertEquals(2, rows.size());
        assertEquals("4", rows.get(1)[0]);
    }

    @Test
    void emptyFileFailsLoud() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> SeedCsv.load("seedcsvtest-empty.csv", 3));
        assertTrue(ex.getMessage().contains("empty"), ex.getMessage());
    }

    @Test
    void missingFileFailsLoud() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> SeedCsv.load("seedcsvtest-does-not-exist.csv", 3));
        assertTrue(ex.getMessage().contains("not found"), ex.getMessage());
    }
}
