package com.school.planner.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-655: the lifecycle JSONL contract. The file is the interface with the
 * readout script, so the format pins are load-bearing: one JSON object per
 * line, ts+event always present, kv values JSON-safe (numbers unquoted,
 * strings escaped).
 */
class LifecycleLogTest {

    @TempDir
    Path dir;

    @Test
    void writesOneJsonObjectPerEventWithEscaping() throws IOException {
        Path file = dir.resolve("lifecycle.jsonl");
        System.setProperty("planner.lifecycle.file", file.toString());
        try {
            LifecycleLog.event("SOLVE_START", "maxAttempts", 5);
            LifecycleLog.event("STAGE_ENTER", "stage", "B-TIMESLOT-1", "attempt", 2);
            LifecycleLog.event("ATTEMPT_DONE", "attempt", 1, "boundaryTierHard", -2,
                    "fullHard", "unsolved", "note", "quote \" and newline\nhere");

            var lines = Files.readAllLines(file);
            assertTrue(lines.size() == 3, "expected 3 lines, got " + lines.size());
            assertTrue(lines.get(0).startsWith("{\"ts\":\"") && lines.get(0).contains("\"event\":\"SOLVE_START\"")
                    && lines.get(0).contains("\"maxAttempts\":5"), "line 0: " + lines.get(0));
            assertTrue(lines.get(1).contains("\"stage\":\"B-TIMESLOT-1\"")
                    && lines.get(1).contains("\"attempt\":2"), "line 1: " + lines.get(1));
            assertTrue(lines.get(2).contains("\"fullHard\":\"unsolved\"")
                    && lines.get(2).contains("quote \\\" and newline\\nhere"), "line 2: " + lines.get(2));
            // every line parses as JSON
            for (String line : lines) {
                try (var parser = new com.fasterxml.jackson.core.JsonFactory()
                        .createParser(line)) {
                    while (parser.nextToken() != null) {
                        // consumes the whole document or throws
                    }
                }
            }
        } finally {
            System.clearProperty("planner.lifecycle.file");
        }
    }

    @Test
    void unreadablePathCountsDropsInsteadOfThrowing() {
        Path bad = dir.resolve("no-such-dir").resolve("lifecycle.jsonl");
        System.setProperty("planner.lifecycle.file", bad.toString());
        try {
            long before = LifecycleLog.dropCount();
            LifecycleLog.event("SOLVE_START");
            assertTrue(LifecycleLog.dropCount() == before + 1,
                    "a failed write must be counted, never thrown");
        } finally {
            System.clearProperty("planner.lifecycle.file");
        }
    }
}
