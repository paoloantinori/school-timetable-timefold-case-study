package com.school.planner.solver.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-542 stage 2: the file writer the cycle's capture hook calls at every boundary. One file
 * per boundary, path scheme {@code attempt<N>/<BOUNDARY>[-<iteration>].json} so a multi-start
 * run's attempts never overwrite each other and the latest boundary wins per attempt.
 */
class StagedCheckpointWriterTest {

    @TempDir
    Path dir;

    @Test
    void writesOneFilePerBoundaryAndCanReadItBack() throws IOException {
        StagedCheckpointWriter writer = StagedCheckpointWriter.to(dir);
        StagedCheckpoint checkpoint = new StagedCheckpoint(
                StagedCheckpoint.CURRENT_VERSION,
                new StagedCheckpointCursor(StagedPhaseBoundary.POST_B_TIMESLOT, 2, 1, 0L),
                "[-642/0]hard/[0/0]soft", 0L, -642L,
                List.of(new LessonPlacement(1L, 100L, 7L, false, false)));
        Path file = writer.write(checkpoint);
        assertTrue(Files.exists(file), "the boundary capture must land on disk immediately");
        assertEquals(Path.of("attempt1").resolve("POST_B_TIMESLOT-2.json"),
                dir.relativize(file));
        assertEquals(checkpoint, StagedCheckpointSerializer.fromJson(Files.readString(file)));
    }

    @Test
    void attemptsDoNotOverwriteEachOtherAndLaterIterationsWin() throws IOException {
        StagedCheckpointWriter writer = StagedCheckpointWriter.to(dir);
        writer.write(new StagedCheckpoint(StagedCheckpoint.CURRENT_VERSION,
                new StagedCheckpointCursor(StagedPhaseBoundary.POST_B_TIMESLOT, 1, 1, 0L),
                "a", 0L, 0L, List.of()));
        writer.write(new StagedCheckpoint(StagedCheckpoint.CURRENT_VERSION,
                new StagedCheckpointCursor(StagedPhaseBoundary.POST_B_TIMESLOT, 1, 2, 2L),
                "b", 0L, 0L, List.of()));
        writer.write(new StagedCheckpoint(StagedCheckpoint.CURRENT_VERSION,
                new StagedCheckpointCursor(StagedPhaseBoundary.POST_RELEASE, 0, 2, 2L),
                "c", 0L, 0L, List.of()));
        assertEquals(3, Files.walk(dir).filter(p -> p.toString().endsWith(".json")).count(),
                "three distinct (attempt, boundary, iteration) captures = three files");
        // the same (attempt, boundary, iteration) recaptured overwrites in place: latest wins
        writer.write(new StagedCheckpoint(StagedCheckpoint.CURRENT_VERSION,
                new StagedCheckpointCursor(StagedPhaseBoundary.POST_B_TIMESLOT, 1, 2, 2L),
                "b2", 0L, 0L, List.of()));
        Path recaptured = dir.resolve("attempt2").resolve("POST_B_TIMESLOT-1.json");
        assertTrue(StagedCheckpointSerializer.fromJson(Files.readString(recaptured))
                .score().equals("b2"));
    }

}
