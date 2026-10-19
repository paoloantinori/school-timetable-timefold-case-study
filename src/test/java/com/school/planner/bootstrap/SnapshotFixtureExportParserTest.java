package com.school.planner.bootstrap;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight, offline unit test of {@link SnapshotFixture#parseExport}: NO Quarkus, NO DB,
 * NO real committed export artifact, just a minimal inline JSON fixture gzipped to a temp
 * file. Exercises the parser's core room/pinned/id round-trip, deliberately covering the
 * three shapes {@link SnapshotFixture#loadFromExport} must distinguish: a placed lesson WITH
 * a room, a placed lesson with a NULL room (timeslot present, room genuinely unassigned), and
 * a fully unassigned lesson (timeslot AND room both null, an H0 boxed lesson). The third
 * lesson's course also omits every optional field (isLab/requiredRoomTags/labBlockSize/
 * professionalizzanti) to exercise {@code JsonNode.path}'s defaulting, distinct from the
 * mandatory fields (id/subject/teacher/studentGroup) that fail loudly if missing.
 *
 * <p>Runs in the default suite (fast, no {@code @EnabledIfEnvironmentVariable} gate): unlike
 * the full snapshot harnesses ({@code ScoreASnapshotTest}, {@code Phase2SmokeSnapshotTest},
 * {@code RoomPhaseSnapshotTest}), this test never boots Quarkus or reconstructs a population,
 * so it carries none of their runtime cost.
 */
class SnapshotFixtureExportParserTest {

    @Test
    void parsesRoomsAndLessonsWithRoomAndPinnedRoundTrip(@TempDir Path tempDir) throws Exception {
        String json = """
                {
                  "rooms": [
                    {"id": 1, "name": "R01", "capacity": 24, "tags": []},
                    {"id": 2, "name": "R12", "capacity": 20, "tags": ["informatica"]}
                  ],
                  "lessons": [
                    {
                      "id": 100,
                      "pinned": true,
                      "course": {
                        "id": 10, "subject": "Matematica",
                        "teacher": {"name": "Rossi"},
                        "studentGroup": {"name": "G01"},
                        "isLab": false, "requiredRoomTags": [], "labBlockSize": 0,
                        "professionalizzanti": false
                      },
                      "timeslot": {"date": "2026-09-14", "startDateTime": "2026-09-14T08:00:00"},
                      "room": {"id": 1, "name": "R01"}
                    },
                    {
                      "id": 101,
                      "pinned": false,
                      "course": {
                        "id": 11, "subject": "Informatica",
                        "teacher": {"name": "Bianchi"},
                        "studentGroup": {"name": "G01"},
                        "isLab": true, "requiredRoomTags": ["informatica"], "labBlockSize": 2,
                        "professionalizzanti": false
                      },
                      "timeslot": {"date": "2026-09-14", "startDateTime": "2026-09-14T09:00:00"},
                      "room": null
                    },
                    {
                      "id": 102,
                      "pinned": false,
                      "course": {
                        "id": 12, "subject": "Scienze",
                        "teacher": {"name": "Verdi"},
                        "studentGroup": {"name": "G01"}
                      },
                      "timeslot": null,
                      "room": null
                    }
                  ]
                }
                """;
        Path exportGz = gzip(tempDir, json);

        SnapshotFixture.ParsedExport export = SnapshotFixture.parseExport(exportGz);

        assertEquals(List.of(
                new SnapshotFixture.ExportRoomRow(1L, "R01", 24, List.of()),
                new SnapshotFixture.ExportRoomRow(2L, "R12", 20, List.of("informatica"))
        ), export.rooms());

        assertEquals(3, export.lessons().size());

        SnapshotFixture.ExportLessonRow withRoom = export.lessons().get(0);
        assertEquals(new SnapshotFixture.ExportLessonRow(100L, 10L, "G01", "Rossi", "Matematica",
                LocalDate.of(2026, 9, 14), 8, true, 1L, "R01",
                false, List.of(), 0, false), withRoom);

        SnapshotFixture.ExportLessonRow roomNull = export.lessons().get(1);
        assertEquals(101L, roomNull.id());
        assertEquals(LocalDate.of(2026, 9, 14), roomNull.date());
        assertEquals(9, roomNull.hour());
        assertNull(roomNull.roomId(), "placed lesson with a genuinely unassigned room must parse roomId as null");
        assertNull(roomNull.roomName());
        assertTrue(roomNull.isLab());
        assertEquals(List.of("informatica"), roomNull.requiredRoomTags());
        assertEquals(2, roomNull.labBlockSize());

        SnapshotFixture.ExportLessonRow unassigned = export.lessons().get(2);
        assertEquals(102L, unassigned.id());
        assertNull(unassigned.date(), "an H0 unassigned lesson (no timeslot) must parse date as null");
        assertNull(unassigned.hour());
        assertNull(unassigned.roomId());
        assertNull(unassigned.roomName());
        assertEquals(false, unassigned.pinned());
        // Optional course fields omitted entirely from the fixture: JsonNode.path defaults kick in.
        assertEquals(false, unassigned.isLab());
        assertEquals(List.of(), unassigned.requiredRoomTags());
        assertEquals(0, unassigned.labBlockSize());
        assertEquals(false, unassigned.professionalizzanti());
    }

    private static Path gzip(Path dir, String json) throws Exception {
        Path file = dir.resolve("export.json.gz");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(buffer)) {
            gz.write(json.getBytes(StandardCharsets.UTF_8));
        }
        Files.write(file, buffer.toByteArray());
        return file;
    }
}
