package com.school.planner.solver.checkpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * TASK-542 stage 1: JSON round-trip for {@link StagedCheckpoint}. Jackson records, no custom
 * schema: the record shape IS the format (version field guards it - an unknown future version
 * fails loud instead of silently misloading a changed layout).
 */
public final class StagedCheckpointSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StagedCheckpointSerializer() {
    }

    public static String toJson(StagedCheckpoint checkpoint) {
        try {
            return MAPPER.writeValueAsString(checkpoint);
        } catch (Exception e) {
            throw new IllegalArgumentException("checkpoint serialization failed", e);
        }
    }

    public static StagedCheckpoint fromJson(String json) {
        StagedCheckpoint checkpoint;
        try {
            checkpoint = MAPPER.readValue(json, StagedCheckpoint.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("checkpoint deserialization failed: " + e.getMessage(), e);
        }
        if (checkpoint.version() != StagedCheckpoint.CURRENT_VERSION) {
            throw new IllegalArgumentException("checkpoint version " + checkpoint.version()
                    + " is not supported (expected " + StagedCheckpoint.CURRENT_VERSION
                    + ") - the format changed since this checkpoint was captured");
        }
        return checkpoint;
    }
}
