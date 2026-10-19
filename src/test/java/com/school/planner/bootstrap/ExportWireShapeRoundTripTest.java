package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.planner.domain.Lesson;
import com.school.planner.service.AvailabilityExpansionService;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * TASK-674 regression pin: the export wire must stay lean. The 23/09 bloat shipped
 * because Jackson invoked {@code getAvailableTimeslotsFresh()} on every lesson while
 * serializing the schedule ({@code availableTimeslotsFresh}, ~100KB x 12551 lessons:
 * 1.23GB raw measured on the {@code GET /api/schedule} HAR, commit 5185e055; the
 * export shares the same Lesson graph, so every export was bloated too). The cure was
 * {@code @JsonIgnore} on that getter, with no test guarding it.
 *
 * <p>Mechanics, honestly: this serializes the fixture's {@code problem} entity graph
 * with the application's CDI {@link ObjectMapper} (the same mapper the export
 * endpoint uses, over the lesson/fact core the endpoint carries; the DB-backed
 * variant would add quota/calendar facts, none of which matter to the per-lesson
 * wire shape). No state preparation is needed: with {@code @JsonIgnore} removed,
 * Jackson itself invokes the getter during serialization and the key (and its bulk)
 * lands on the wire, which is exactly what the assertions catch. The round-trip leg
 * reloads the just-serialized bytes through {@link SnapshotFixture#loadFromExport}
 * (the H2-compatible consumer of export files, which rebuilds the VRP caches via
 * {@link AvailabilityExpansionService}) and asserts placement parity: the stripped
 * wire is complete for its real loader. The production REST import
 * ({@code ImportService}, PostgreSQL-only TRUNCATE ... CASCADE) cannot run on H2
 * and is covered live by the TASK-672/674 lane verifications. Ungated by choice:
 * the pin is the guard the 23/09 bloat lacked, and it costs ~3s of suite time
 * (measured), not the minutes the env-gated benches spend.</p>
 *
 * <p>Fixture provenance: the committed integrato-7 checkpoint is the 23/09 bloat
 * capture SCRUBBED (its per-lesson {@code availableTimeslotsFresh} arrays were
 * mechanically emptied to fit git, 837488b1). It is used here as dataset only,
 * NOT as a wire-shape reference; the post-cure wire carries no such key at all.</p>
 */
@QuarkusTest
class ExportWireShapeRoundTripTest {

    private static final String[] LEAKED_CACHE_KEYS = {
            "\"availableTimeslotsFresh\"",  // the non-memoizing getter (5185e055)
            "\"availableTimeslots\"",       // the memoized getter (both @JsonIgnore today)
    };
    /** Raw fixture wire measured ~21.5MB on this artifact (the pin's assert message
     * prints the live number). The bloat class was 1.23GB. A 30MB bound makes the
     * pin sensitive to any new per-lesson payload above roughly 0.7KB/lesson, with
     * headroom for benign wire drift. */
    private static final long RAW_EXPORT_BOUND = 30L * 1024 * 1024;
    private static final Path CHECKPOINT =
            Path.of("claudedocs/spalmone-2026-27/integrato-7/export.json.gz");

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject ObjectMapper objectMapper;
    @Inject jakarta.persistence.EntityManager em;

    @TempDir Path tempDir;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    @Transactional
    void exportWireCarriesNoVrpCacheAndRoundTrips() throws Exception {
        // clone pubblico: l'export .gz e' espulso (regola zero-binari,
        // TASK-684) e il banco si salta; nel repo PRIVATO (publish-prep
        // presente) un export assente e' una PERDITA e fallisce forte
        if (!Files.exists(CHECKPOINT)) {
            if (Files.exists(Path.of("publish-prep"))) {
                org.junit.jupiter.api.Assertions.fail(
                        "export perso nel repo privato: " + CHECKPOINT);
            }
            Assumptions.assumeTrue(false, "export assente (clone pubblico): " + CHECKPOINT);
        }
        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadFromExport(CHECKPOINT, availabilityExpansionService, true);
        assertFalse(snap.lessons.isEmpty(), "vacuous pin: checkpoint loaded no lessons");

        byte[] wire = objectMapper.writeValueAsBytes(snap.problem);
        String wireText = new String(wire, StandardCharsets.UTF_8);
        for (String key : LEAKED_CACHE_KEYS) {
            assertFalse(wireText.contains(key),
                    key + " leaked onto the export wire again (TASK-674)");
        }
        assertTrue(wire.length < RAW_EXPORT_BOUND,
                "export size class regressed: raw=" + (wire.length / (1024 * 1024)) + "MB");

        // Round-trip through the checkpoint loader. Its contract requires a clean
        // DB ("assumes the caller already cleared the tables"), so wipe between the
        // two loads instead of relying on the second seed being harmless on top of
        // the first.
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
        Path roundTripped = SnapshotFixture.gzipJson(tempDir.resolve("t674-roundtrip.json.gz"), wire);
        SnapshotFixture.Snapshot reloaded =
                SnapshotFixture.loadFromExport(roundTripped, availabilityExpansionService, true);
        assertEquals(placementSignatures(snap.lessons), placementSignatures(reloaded.lessons),
                "round-trip through the export wire lost or moved placements");
    }

    /**
     * Sorted signatures "subject@group@teacher|isLab -> date@hour|room|pinned":
     * content-keyed parity (the export preserves ids today, but keying on the
     * lesson's own content stays robust if a loader ever reassigns them) and
     * order-insensitive (the wire order is not a contract).
     */
    private List<String> placementSignatures(List<Lesson> lessons) {
        List<String> signatures = new ArrayList<>(lessons.size());
        for (Lesson lesson : lessons) {
            String who = lesson.getSubject() + "@" + (lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : "-")
                    + "@" + (lesson.getTeacher() != null ? lesson.getTeacher().name : "-")
                    + "|" + (lesson.course != null && lesson.course.isLab);
            String where = (lesson.timeslot != null
                    ? lesson.timeslot.getDate() + "@" + lesson.timeslot.startDateTime.getHour() : "unplaced")
                    + "|" + (lesson.room != null ? lesson.room.name : "-")
                    + "|" + lesson.pinned;
            signatures.add(who + "->" + where);
        }
        Collections.sort(signatures);
        return signatures;
    }
}
