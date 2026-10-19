package com.school.planner.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.school.planner.domain.SchoolSchedule;

/**
 * TASK-464: the golden co-presenza depth loader
 * ({@link ConstrainedTeacherAllocator#loadGoldenCoPresenceDepth}) is a Java re-implementation
 * of the spike's Python cross-tab, so this test pins that it reads the real classpath golden
 * ({@code seed/lessons.csv}) correctly: right column for the subject, right closed-row skip,
 * right slot-start parse, and the authoritative subject-to-tag map (the 4 acconciatura-tagged
 * subjects from {@code seed/subjects.csv}). The spike ({@code spike-room-null-copresenza-2026-07-31/VERDICT.md})
 * found acconciatura co-presenza on Mon AM (depth 2) and Thu (depth up to 5); the loader must
 * reproduce that headline, which is also the depth the H2 exemption and H-COPRESENCE cap consume.
 */
class CoPresenceDepthLoaderTest {

    /** The 4 acconciatura-tagged lab subjects per {@code seed/subjects.csv} (roomTags=acconciatura). */
    private static final List<String> ACCONCIATURA_SUBJECTS = List.of(
            "Barberia", "Lavaggio", "Servizio stilistico", "Servizio tecnico e stilistico");

    private static Map<String, String> acconciaturaSubjectToTag() {
        Map<String, String> map = new HashMap<>();
        for (String subject : ACCONCIATURA_SUBJECTS) {
            map.put(subject, "acconciatura");
        }
        return map;
    }

    @Test
    void goldenDepth_reproducesSpikeAcconciaturaCoPresenceCells() {
        Map<String, Integer> depth =
                ConstrainedTeacherAllocator.loadGoldenCoPresenceDepth(acconciaturaSubjectToTag());

        long coPresenceCells = depth.values().stream().filter(d -> d >= 2).count();
        int maxDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        // Spike headline: acconciatura practices co-presenza (the 4 acc subjects co-located
        // 2+ groups) on Mon AM + Thu, so the loader must find depth>=2 cells.
        assertTrue(coPresenceCells >= 2,
                "expected golden acconciatura co-presenza cells (depth>=2); full map: " + depth);
        // Spike: Thu 08-12 hosts 5 distinct groups (1/2/G10 + G12 + G14). The loader's
        // max depth must reach 5, confirming the subject/slot/column parse is not under-counting.
        assertTrue(maxDepth >= 5,
                "spike found Thu 08-12 depth 5; loader max depth was " + maxDepth + " (map: " + depth + ")");
        // OVER-COUNT guard: a broken closed-row skip or a theory subject leaking in would
        // inflate this to thousands. The real count is ~91 (the harness measurement), so an
        // upper bound catches over-counting that the lower-bound assertions above miss.
        assertTrue(coPresenceCells <= 150,
                "over-count guard: expected ~91 depth>=2 cells, got " + coPresenceCells + " (map: " + depth + ")");
        // EXACT pin on a known cell (spike: Thu 08:00 acconciatura depth 5). Guards silent
        // drift in the slot-start parse or weekday derivation that the bounds above would miss.
        assertEquals(5, depth.get(SchoolSchedule.coPresenceCell(
                        "acconciatura", DayOfWeek.THURSDAY, LocalTime.of(8, 0))),
                "spike: Thu 08:00 acconciatura depth 5 (1/2/G10 + G12 + G14); map: " + depth);
    }

    @Test
    void goldenDepth_emptySubjectMap_yieldsNoCoPresence() {
        // No subject mapped -> no lab hours counted -> every cell depth 0 -> no co-presenza.
        // Guard that the loader does not invent tags or count theory hours.
        Map<String, Integer> depth =
                ConstrainedTeacherAllocator.loadGoldenCoPresenceDepth(Map.of());
        long coPresenceCells = depth.values().stream().filter(d -> d >= 2).count();
        assertTrue(coPresenceCells == 0,
                "empty subjectToTag must yield no depth>=2 cells; got: " + depth);
    }
}
