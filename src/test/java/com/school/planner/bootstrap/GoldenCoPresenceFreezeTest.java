package com.school.planner.bootstrap;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;

import com.school.planner.domain.Course;
import com.school.planner.service.ConstrainedTeacherAllocator;

/**
 * TASK-523 closure: freeze the golden co-presence depth map ONCE as static input
 * (the single congelamento the control path needs without the golden). This test
 * is the GENERATOR (run it, copy target/golden-copresence-depth.csv into
 * src/main/resources/seed/) AND the identity gate (once the resource exists, it
 * must equal the live loader, the java-reimplementation-diverges lesson: never
 * trust a derived copy without an equality gate). @QuarkusTest so DataGenerator
 * seeds the REAL courses and buildSubjectToSingleTagMap sees the production
 * subject-to-tag mapping, exactly like refreshSolverStatics does at runtime.
 */
@QuarkusTest
@io.quarkus.test.junit.TestProfile(GoldenCoPresenceFreezeTest.SeedEnabled.class)
class GoldenCoPresenceFreezeTest {

    /** The generator is disabled in tests; the freeze needs the REAL courses. */
    public static class SeedEnabled implements QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of("planner.datagenerator.enabled", "true");
        }
    }


    @Test
    void freezeAndGate() throws Exception {
        Map<String, String> subjectToTag = ConstrainedTeacherAllocator
                .buildSubjectToSingleTagMap(Course.<Course>listAll());
        Map<String, Integer> live = ConstrainedTeacherAllocator
                .loadGoldenCoPresenceDepth(subjectToTag);
        org.junit.jupiter.api.Assertions.assertFalse(live.isEmpty(),
                "golden unreadable or no tagged courses: cannot freeze");

        StringBuilder sb = new StringBuilder();
        live.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey()).append(',').append(e.getValue()).append('\n'));
        java.nio.file.Path out = java.nio.file.Path.of("target/golden-copresence-depth.csv");
        java.nio.file.Files.writeString(out, sb.toString());

        // Identity gate: when the frozen resource is on the classpath (after the
        // copy into seed/), it must match the live map exactly.
        Map<String, Integer> frozen = ConstrainedTeacherAllocator.loadFrozenCoPresenceDepth();
        if (!frozen.isEmpty()) {
            org.junit.jupiter.api.Assertions.assertEquals(live, frozen,
                    "frozen co-presence map diverged from the golden loader: regenerate it");
        }
    }
}
