package com.school.planner.solver.checkpoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TASK-542 stage 2: writes boundary checkpoints to disk, one file per (attempt, boundary,
 * iteration). Path scheme {@code <dir>/attempt<N>/<BOUNDARY>[-<iteration>].json}: a multi-start
 * run's attempts never overwrite each other, and recapturing the same boundary overwrites in
 * place (the latest capture of a boundary is the one a resume wants).
 *
 * <p>The capture flag ({@code planner.checkpoint.capture.enabled}, default OFF, read LIVE like
 * the cycle's other flags) is handled by the caller's own guard: a disabled capture never
 * reaches this class.
 */
public final class StagedCheckpointWriter {

    private final Path dir;

    private StagedCheckpointWriter(Path dir) {
        this.dir = dir;
    }

    /** An enabled writer capturing under {@code dir} (created on first write). */
    public static StagedCheckpointWriter to(Path dir) {
        return new StagedCheckpointWriter(dir);
    }

    /** Writes the checkpoint and returns its path. */
    public Path write(StagedCheckpoint checkpoint) {
        StagedCheckpointCursor cursor = checkpoint.cursor();
        StringBuilder name = new StringBuilder(cursor.boundary().name());
        if (cursor.boundary().isIterated()) {
            name.append('-').append(cursor.iteration());
        }
        name.append(".json");
        try {
            Path attemptDir = dir.resolve("attempt" + Math.max(cursor.attempt(), 1));
            Files.createDirectories(attemptDir);
            Path file = attemptDir.resolve(name.toString());
            Files.writeString(file, StagedCheckpointSerializer.toJson(checkpoint));
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("staged checkpoint write failed under " + dir, e);
        }
    }
}
