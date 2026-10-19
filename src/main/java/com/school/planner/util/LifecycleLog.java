package com.school.planner.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import org.jboss.logging.Logger;

/**
 * TASK-655: mechanical lifecycle event log. One JSON line per event, appended
 * to a single known file, so every "what is the system doing" question has a
 * one-command answer and lifecycle diagnosis never greps free-text logs again
 * (the 23/09 lesson: a stage label without timestamp or attempt read as a
 * stall and as an attempt-2 that never existed).
 *
 * <p>Events (emitted by ScheduleService): SOLVE_START, ATTEMPT_START{n,max},
 * STAGE_ENTER{stage}, DELIVERY{attempt,timeslotTierHard,fullHard,wallMillis},
 * SOLVE_END{bestAttempt,bestHard,attemptsRun}. The reader is
 * {@code scripts/api/lifecycle.sh}; the file is the contract, the log is not.
 *
 * <p>Failure posture: lifecycle logging must NEVER break a solve. Every write
 * is swallowed and counted; the counter surfaces in {@link #dropCount()} for
 * the readout script to display (a silent observability hole is the failure
 * this class exists to prevent, so it is a visible counter, not a hidden one).
 */
public final class LifecycleLog {

    private static final Logger LOG = Logger.getLogger(LifecycleLog.class);

    private static final Object LOCK = new Object();
    private static long drops = 0;

    private LifecycleLog() {
    }

    /**
     * Resolved LIVE per write, never captured at boot: the m22MinDays lesson
     * (a boot-cached read made every later property change invisible) and the
     * test harness both need the property to be honored at call time.
     */
    private static Path file() {
        return Path.of(System.getProperty("planner.lifecycle.file", "lifecycle.jsonl"));
    }

    /** Appends one event line. kvPairs are alternating key/value pairs. */
    public static void event(String event, Object... kvPairs) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"ts\":\"").append(Instant.now()).append("\",\"event\":\"")
                .append(event).append('"');
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            sb.append(',').append(quote(String.valueOf(kvPairs[i]))).append(':')
                    .append(quote(String.valueOf(kvPairs[i + 1])));
        }
        sb.append("}\n");
        String line = sb.toString();
        try {
            synchronized (LOCK) {
                Files.writeString(file(), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException | UncheckedIOException e) {
            drops++;
            LOG.debugf("lifecycle write failed (drops=%d): %s", drops, e.toString());
        }
    }

    /** Number of event writes lost to I/O failures since startup. */
    public static long dropCount() {
        return drops;
    }

    private static String quote(String v) {
        if (v == null || v.isEmpty()) {
            return "\"\"";
        }
        // numbers and booleans stay unquoted so the reader can compare them
        if (v.matches("-?\\d+(\\.\\d+)?") || "true".equals(v) || "false".equals(v)) {
            return v;
        }
        StringBuilder sb = new StringBuilder(v.length() + 2);
        sb.append('"');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
