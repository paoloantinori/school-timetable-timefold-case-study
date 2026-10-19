package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintMetadata;
import ai.timefold.solver.core.api.score.stream.ConstraintRef;

import com.school.planner.util.ConstraintIds;

/**
 * Guards the {@code planner.constraints.diag.exclude} leave-one-out filter (E1, 2026-07-31).
 * Pure-logic, no Quarkus, no solve: it confirms the filter is INERT when the property is unset
 * (the default solve stays byte-identical) and that canonical-ID matching is exact (not prefix),
 * so e.g. {@code "M20"} drops only {@code "M20 - ..."} and spares {@code "M20.2 - ..."}. The
 * integration (defineConstraints actually applies it) is exercised by
 * {@code ConstraintProfilingLeaveOneOutTest}.
 */
class ConstraintDiagExcludeFilterTest {

    /**
     * A Constraint stub carrying only a ref id (the only field the filter reads). Built from the
     * bare NAME ("H17 - ..."), not the full "{package}/{name}": {@code ConstraintRef.of} runs
     * {@code AbstractConstraintBuilder.sanitize}, which rejects the slash (Timefold 2.0+). In
     * production the package prefix is attached internally after sanitize, and
     * {@link ConstraintIds#canonicalId} strips it (slash-tolerant); that production-format path is
     * covered by {@link #canonicalIdStripsPackageAndDescription()}.
     */
    private static Constraint fake(String name) {
        ConstraintRef ref = ConstraintRef.of(name);
        return new Constraint() {
            @Override
            public ConstraintRef getConstraintRef() {
                return ref;
            }

            @Override
            public ConstraintMetadata getConstraintMetadata() {
                return null;
            }

            @Override
            public <Score_ extends Score<Score_>> Score_ getConstraintWeight() {
                return null;
            }
        };
    }

    /** Canonical IDs of the surviving constraints, in build order (the filter preserves order). */
    private static List<String> canonicalIds(Constraint[] cs) {
        return Arrays.stream(cs)
                .map(c -> ConstraintIds.canonicalId(c.getConstraintRef().id()))
                .toList();
    }

    private static Constraint[] sample() {
        return new Constraint[] {
                fake("H1 - Teacher conflict"),
                fake("H17 - Vertically compact"),
                fake("H29 - Assigned lesson must have a room"),
                fake("M20 - Daily quota fill"),
                fake("M20.2 - Empty expected day"),
                fake("H-M2 - Room suitability"),
        };
    }

    @Test
    void inertWhenPropertyNull() {
        Constraint[] r = SchoolScheduleConstraintProvider.applyDiagExclude(sample(), null);
        assertEquals(6, r.length, "null property must return all constraints unchanged");
    }

    /** The production format carries a "{package}/" prefix; canonicalId must strip it + the " - desc". */
    @Test
    void canonicalIdStripsPackageAndDescription() {
        assertEquals("H6", ConstraintIds.canonicalId("com.school.planner.domain/H6 - School holiday"));
        assertEquals("H17", ConstraintIds.canonicalId("com.school.planner.solver/H17 - Vertically compact"));
        assertEquals("M20.2", ConstraintIds.canonicalId("com.school.planner.solver/M20.2 - Empty expected day"));
    }

    @Test
    void inertWhenPropertyBlank() {
        assertEquals(6, SchoolScheduleConstraintProvider.applyDiagExclude(sample(), "   ").length);
        assertEquals(6, SchoolScheduleConstraintProvider.applyDiagExclude(sample(), "").length);
    }

    @Test
    void dropsSingleCanonicalId() {
        Constraint[] r = SchoolScheduleConstraintProvider.applyDiagExclude(sample(), "H17");
        assertEquals(5, r.length);
        assertFalse(canonicalIds(r).contains("H17"));
    }

    @Test
    void exactIdNotPrefixSoM20SparesM20_2() {
        // "M20" must drop only "M20 - Daily quota fill", NOT "M20.2 - Empty expected day".
        Constraint[] r = SchoolScheduleConstraintProvider.applyDiagExclude(sample(), "M20");
        List<String> ids = canonicalIds(r);
        assertTrue(ids.contains("M20.2"), "M20.2 must survive excluding M20");
        assertFalse(ids.contains("M20"));
    }

    @Test
    void dropsMultipleAndKeepsRestInOrder() {
        Constraint[] r = SchoolScheduleConstraintProvider.applyDiagExclude(sample(), "M20,H-M2");
        assertEquals(List.of("H1", "H17", "H29", "M20.2"), canonicalIds(r));
    }

    @Test
    void unmatchedIdDropsNothingButMatchedStillDrops() {
        // A listed ID that matches nothing is a no-op for it; the matched one still drops.
        Constraint[] r = SchoolScheduleConstraintProvider.applyDiagExclude(sample(), "H17,NOPE");
        assertEquals(5, r.length);
        assertFalse(canonicalIds(r).contains("H17"));
    }

    @Test
    void emptyAndBlankTokensAreIgnored() {
        Constraint[] r = SchoolScheduleConstraintProvider.applyDiagExclude(sample(), ", H29 ,,");
        assertEquals(5, r.length);
        assertFalse(canonicalIds(r).contains("H29"));
    }
}
