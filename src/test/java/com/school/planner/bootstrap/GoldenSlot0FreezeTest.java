package com.school.planner.bootstrap;

import java.time.DayOfWeek;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.school.planner.service.ConstrainedTeacherAllocator;

/**
 * m-13 night (mandate 18/19-08): freeze the golden slot-0 anchor map ONCE as
 * static input, the second and last congelamento the control path needs without
 * the golden. Same shape as {@link GoldenCoPresenceFreezeTest}: this test is the
 * GENERATOR (copy target/golden-slot0-anchors.csv into src/main/resources/seed/)
 * AND the identity gate (once the resource exists it must equal the live loader;
 * the slot-0 loader needs no DB, a plain test sees the classpath golden).
 */
class GoldenSlot0FreezeTest {

    @Test
    void freezeAndGate() throws Exception {
        Map<String, Map<DayOfWeek, String>> live =
                ConstrainedTeacherAllocator.loadGoldenSlot0Anchors();
        org.junit.jupiter.api.Assertions.assertFalse(live.isEmpty(),
                "golden unreadable on the test classpath: cannot freeze");

        StringBuilder sb = new StringBuilder();
        live.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(ge -> {
            ge.getValue().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(we -> sb.append(ge.getKey()).append(',')
                            .append(we.getKey()).append(',')
                            .append(we.getValue()).append('\n'));
        });
        java.nio.file.Path out = java.nio.file.Path.of("target/golden-slot0-anchors.csv");
        java.nio.file.Files.writeString(out, sb.toString());

        Map<String, Map<DayOfWeek, String>> frozen =
                ConstrainedTeacherAllocator.loadFrozenSlot0Anchors();
        if (!frozen.isEmpty()) {
            org.junit.jupiter.api.Assertions.assertEquals(live, frozen,
                    "frozen slot-0 anchor map diverged from the golden loader: regenerate it");
        }
    }
}
