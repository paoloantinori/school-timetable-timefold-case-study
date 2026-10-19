package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Hybrid Phase 1a data classes.
 * These are simple POJO/record tests that don't require Quarkus or database.
 *
 * <p>Pattern: Follows {@code TimeslotSequenceTest} - plain JUnit 5 test without @QuarkusTest.</p>
 */
class HybridPhase1aDataClassesTest {

    // === RotationCandidate Tests ===

    @Test
    void rotationCandidate_createsDefensiveCopy() {
        Map<RotationTestRunner.BlockKey, DayOfWeek> map = new HashMap<>();
        map.put(new RotationTestRunner.BlockKey(1L, 2L), DayOfWeek.THURSDAY);

        RotationCandidate candidate = new RotationCandidate(map, 1);
        map.put(new RotationTestRunner.BlockKey(3L, 4L), DayOfWeek.FRIDAY);  // Modify original

        assertEquals(1, candidate.assignments().size());  // Should not see modification
    }

    @Test
    void rotationCandidate_nullAssignmentsBecomesEmptyMap() {
        RotationCandidate candidate = new RotationCandidate(null, 1);
        assertNotNull(candidate.assignments());
        assertTrue(candidate.assignments().isEmpty());
        assertEquals(0, candidate.size());
    }

    @Test
    void rotationCandidate_blockKey_equality() {
        var key1 = new RotationTestRunner.BlockKey(1L, 2L);
        var key2 = new RotationTestRunner.BlockKey(1L, 2L);
        var key3 = new RotationTestRunner.BlockKey(1L, 3L);

        assertEquals(key1, key2);
        assertNotEquals(key1, key3);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void rotationCandidate_blockKey_stringRepresentation() {
        var key = new RotationTestRunner.BlockKey(1L, 2L);
        String str = key.toString();
        assertTrue(str.contains("1"));
        assertTrue(str.contains("2"));
    }

    @Test
    void rotationCandidate_getDayFor() {
        var key = new RotationTestRunner.BlockKey(1L, 2L);
        RotationCandidate candidate = new RotationCandidate(
            Map.of(key, DayOfWeek.THURSDAY), 1);

        assertEquals(DayOfWeek.THURSDAY, candidate.getDayFor(1L, 2L));
        assertNull(candidate.getDayFor(99L, 99L));
    }

    @Test
    void rotationCandidate_size() {
        RotationCandidate empty = new RotationCandidate(Map.of(), 0);
        RotationCandidate twoAssignments = new RotationCandidate(
            Map.of(
                new RotationTestRunner.BlockKey(1L, 2L), DayOfWeek.MONDAY,
                new RotationTestRunner.BlockKey(3L, 4L), DayOfWeek.TUESDAY
            ), 1);

        assertEquals(0, empty.size());
        assertEquals(2, twoAssignments.size());
    }

    @Test
    void rotationCandidate_comparable() {
        var c1 = new RotationCandidate(Map.of(), 1);
        var c2 = new RotationCandidate(Map.of(), 2);
        var c3 = new RotationCandidate(Map.of(), 1);

        assertTrue(c1.compareTo(c2) < 0);
        assertTrue(c2.compareTo(c1) > 0);
        assertEquals(0, c1.compareTo(c3));
    }

    @Test
    void rotationCandidate_stringRepresentation() {
        RotationCandidate candidate = new RotationCandidate(Map.of(), 5);
        String str = candidate.toString();
        assertTrue(str.contains("5"));  // Contains candidate number
    }

    // === HybridPhase1aReport Tests ===

    @Test
    void hybridPhase1aReport_success() {
        var phaseResult = new HybridPhase1aReport.PhaseResult(
            "1-ACCONCIATURA", true, 5, Duration.ofSeconds(10), 12, "acconciatura");

        HybridPhase1aReport report = new HybridPhase1aReport(
            true, "COMPLETE", Duration.ofSeconds(30), 5,
            new RotationCandidate(Map.of(), 5), null, List.of(phaseResult));

        assertTrue(report.success);
        assertEquals("COMPLETE", report.phase);
        assertEquals(Duration.ofSeconds(30), report.totalDuration);
        assertEquals(5, report.totalCandidatesTried);
        assertNotNull(report.winningCandidate);
        assertEquals(5, report.winningCandidate.candidateNumber());
        assertEquals(1, report.phaseResults.size());
        assertNull(report.failureAnalysis);
    }

    @Test
    void hybridPhase1aReport_failure() {
        var failure = new FailureAnalysis("1-ACCONCIATURA", "No valid slots",
            List.of(), Set.of(), Set.of(), "Check teacher availability");

        HybridPhase1aReport report = new HybridPhase1aReport(
            false, "1-ACCONCIATURA", Duration.ofSeconds(30), 100,
            null, failure, List.of());

        assertFalse(report.success);
        assertEquals("1-ACCONCIATURA", report.phase);
        assertNull(report.winningCandidate);
        assertNotNull(report.failureAnalysis);
        assertEquals("Check teacher availability", report.failureAnalysis.suggestion);
    }

    @Test
    void hybridPhase1aReport_nullPhaseResultsBecomesEmptyList() {
        HybridPhase1aReport report = new HybridPhase1aReport(
            true, "COMPLETE", Duration.ZERO, 0, null, null, null);

        assertNotNull(report.phaseResults);
        assertTrue(report.phaseResults.isEmpty());
    }

    @Test
    void hybridPhase1aReport_defensiveCopy() {
        List<HybridPhase1aReport.PhaseResult> list = new ArrayList<>();
        list.add(new HybridPhase1aReport.PhaseResult("test", true, 0, Duration.ZERO, 0, null));

        HybridPhase1aReport report = new HybridPhase1aReport(
            true, "COMPLETE", Duration.ZERO, 0, null, null, list);

        list.add(new HybridPhase1aReport.PhaseResult("test2", true, 0, Duration.ZERO, 0, null));

        assertEquals(1, report.phaseResults.size());  // Should not see modification
    }

    @Test
    void phaseResult_allFields() {
        var result = new HybridPhase1aReport.PhaseResult(
            "2-LABS-meccanica", false, 25, Duration.ofMinutes(5), 0, "meccanica");

        assertEquals("2-LABS-meccanica", result.phaseName);
        assertFalse(result.success);
        assertEquals(25, result.candidatesEvaluated);
        assertEquals(Duration.ofMinutes(5), result.duration);
        assertEquals(0, result.entitiesPinned);
        assertEquals("meccanica", result.roomTag);
    }

    // === FailureAnalysis Tests ===

    @Test
    void failureAnalysis_withConflictPatterns() {
        var pattern = new FailureAnalysis.ConflictPattern(
            "TEACHER_UNAVAILABLE", "Teacher T28 unavailable on non-Thursday",
            5, List.of("2026-02-02 (Monday)", "2026-02-03 (Tuesday)"));

        FailureAnalysis analysis = new FailureAnalysis(
            "1-ACCONCIATURA", "All candidates blocked by teacher unavailability",
            List.of(pattern), Set.of(1L), Set.of(2L, 3L),
            "Review T28's availability or reduce rotation count");

        assertEquals("1-ACCONCIATURA", analysis.phase);
        assertEquals("All candidates blocked by teacher unavailability", analysis.reason);
        assertEquals(1, analysis.conflictPatterns.size());
        assertEquals(1, analysis.conflictingTeacherIds.size());
        assertTrue(analysis.conflictingTeacherIds.contains(1L));
        assertEquals(2, analysis.conflictingGroupIds.size());
        assertTrue(analysis.suggestion.contains("T28"));
    }

    @Test
    void failureAnalysis_emptyCollections() {
        FailureAnalysis analysis = new FailureAnalysis(
            "TEST", "reason", null, null, null, null);

        assertNotNull(analysis.conflictPatterns);
        assertNotNull(analysis.conflictingTeacherIds);
        assertNotNull(analysis.conflictingGroupIds);
        assertTrue(analysis.conflictPatterns.isEmpty());
        assertTrue(analysis.conflictingTeacherIds.isEmpty());
        assertTrue(analysis.conflictingGroupIds.isEmpty());
        assertNull(analysis.suggestion);
    }

    @Test
    void failureAnalysis_defensiveCopy_conflictPatterns() {
        List<FailureAnalysis.ConflictPattern> patterns = new ArrayList<>();
        patterns.add(new FailureAnalysis.ConflictPattern("TYPE", "desc", 1, List.of()));

        FailureAnalysis analysis = new FailureAnalysis(
            "TEST", "reason", patterns, Set.of(), Set.of(), null);

        patterns.add(new FailureAnalysis.ConflictPattern("TYPE2", "desc2", 2, List.of()));

        assertEquals(1, analysis.conflictPatterns.size());
    }

    @Test
    void failureAnalysis_defensiveCopy_teacherIds() {
        Set<Long> teacherIds = new java.util.HashSet<>();
        teacherIds.add(1L);

        FailureAnalysis analysis = new FailureAnalysis(
            "TEST", "reason", List.of(), teacherIds, Set.of(), null);

        teacherIds.add(2L);

        assertEquals(1, analysis.conflictingTeacherIds.size());
    }

    @Test
    void failureAnalysis_defensiveCopy_groupIds() {
        Set<Long> groupIds = new java.util.HashSet<>();
        groupIds.add(1L);

        FailureAnalysis analysis = new FailureAnalysis(
            "TEST", "reason", List.of(), Set.of(), groupIds, null);

        groupIds.add(2L);

        assertEquals(1, analysis.conflictingGroupIds.size());
    }

    // === ConflictPattern Tests ===

    @Test
    void conflictPattern_allFields() {
        var pattern = new FailureAnalysis.ConflictPattern(
            "ROOM_CONFLICT", "Lab room already occupied",
            3, List.of("Lab Info 1 at 10:00", "Lab Acconciatura at 14:00"));

        assertEquals("ROOM_CONFLICT", pattern.type);
        assertEquals("Lab room already occupied", pattern.description);
        assertEquals(3, pattern.occurrenceCount);
        assertEquals(2, pattern.examples.size());
    }

    @Test
    void conflictPattern_nullExamplesBecomesEmptyList() {
        var pattern = new FailureAnalysis.ConflictPattern("TYPE", "desc", 1, null);

        assertNotNull(pattern.examples);
        assertTrue(pattern.examples.isEmpty());
    }

    @Test
    void conflictPattern_examplesDefensiveCopy() {
        List<String> examples = new ArrayList<>();
        examples.add("example1");

        var pattern = new FailureAnalysis.ConflictPattern(
            "TYPE", "desc", 1, examples);

        examples.add("example2");

        assertEquals(1, pattern.examples.size());
    }

    // === Integration-style Tests ===

    @Test
    void fullReport_cycle() {
        // Simulate a full hybrid cycle with multiple phases

        var phase1 = new HybridPhase1aReport.PhaseResult(
            "1-ACCONCIATURA", true, 1, Duration.ofSeconds(15), 18, "acconciatura");

        var phase2a = new HybridPhase1aReport.PhaseResult(
            "2-LABS-acconciatura", true, 1, Duration.ofSeconds(10), 12, "acconciatura");

        var phase2b = new HybridPhase1aReport.PhaseResult(
            "2-LABS-meccanica", true, 1, Duration.ofSeconds(8), 8, "meccanica");

        var winner = new RotationCandidate(
            Map.of(
                new RotationTestRunner.BlockKey(1L, 2L), DayOfWeek.THURSDAY,
                new RotationTestRunner.BlockKey(3L, 4L), DayOfWeek.TUESDAY
            ), 1);

        HybridPhase1aReport report = new HybridPhase1aReport(
            true, "COMPLETE", Duration.ofSeconds(33), 1,
            winner, null, List.of(phase1, phase2a, phase2b));

        assertTrue(report.success);
        assertEquals(3, report.phaseResults.size());
        assertEquals(2, report.winningCandidate.size());
        assertEquals(DayOfWeek.THURSDAY, report.winningCandidate.getDayFor(1L, 2L));
    }

    @Test
    void fullReport_failureWithAnalysis() {
        // Simulate a failure with detailed analysis

        var conflict = new FailureAnalysis.ConflictPattern(
            "STAGE_BLACKOUT", "Stage days conflict with lab rotation",
            50, List.of("MODA group stage overlaps with rotation period"));

        var failure = new FailureAnalysis(
            "2-LABS-acconciatura", "No candidate achieved 0hard due to stage conflicts",
            List.of(conflict), Set.of(5L, 6L), Set.of(1L, 2L, 3L),
            "Adjust stage patterns or reduce lab block size");

        var failedPhase = new HybridPhase1aReport.PhaseResult(
            "2-LABS-acconciatura", false, 100, Duration.ofMinutes(5), 0, "acconciatura");

        HybridPhase1aReport report = new HybridPhase1aReport(
            false, "2-LABS-acconciatura", Duration.ofMinutes(5), 100,
            null, failure, List.of(failedPhase));

        assertFalse(report.success);
        assertNotNull(report.failureAnalysis);
        assertEquals(1, report.failureAnalysis.conflictPatterns.size());
        assertEquals("STAGE_BLACKOUT", report.failureAnalysis.conflictPatterns.get(0).type);
        assertEquals(2, report.failureAnalysis.conflictingTeacherIds.size());
        assertEquals(3, report.failureAnalysis.conflictingGroupIds.size());
    }
}
