package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.school.planner.service.RotationTestRunner.BlockKey;
import com.school.planner.solver.RotationEnumerator;

/**
 * TASK-467 AC#4 wiring: the pure pieces of the enumerator-to-greedy bridge.
 * <ul>
 * <li>{@link GreedyRotationAssigner#restrictValidDays}: the plan restriction is a pure
 *     PER-PAIR intersection. A pair the plan contradicts (non-empty before, empty after)
 *     is RETURNED so the caller fails the candidate instead of letting the backtracking
 *     silently skip the pair (shell-game guard). A pair absent from the plan is untouched;
 *     a pair already empty stays the baseline's own pre-existing skip.</li>
 * <li>{@code HybridPhase1aService.restrictionFromTimeline}: the per-PAIR restriction one
 *     ranked timeline contributes (weekdays where the timeline hosts each group). Two
 *     ranked timelines of the SAME teacher use the same weekday union, so only the
 *     group-to-weekday mapping distinguishes plan j from plan j-1: the test pins exactly
 *     that (the union-based restriction the review found mechanically inert).</li>
 * <li>The DB-bound halves (buildEnumeratorTeachers, the plan loop, the pin path) need JPA
 *     entities; they are covered by the wiring banner, the A/B run, and
 *     {@code RotationEnumeratorTest} on the enumerator core itself.</li>
 * </ul>
 */
class RotationEnumeratorWiringTest {

    private static final Map<String, Long> GROUP_IDS =
            Map.of("G04", 10L, "G08", 20L, "G13", 30L);

    @Test
    void restrictionIntersectsPerPairAndLeavesOthersUntouched() {
        BlockKey t1g1 = new BlockKey(1L, 10L);
        BlockKey t1g2 = new BlockKey(1L, 20L);
        BlockKey t2g3 = new BlockKey(2L, 30L);
        Map<BlockKey, List<DayOfWeek>> valid = new LinkedHashMap<>();
        valid.put(t1g1, List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY));
        valid.put(t1g2, List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY));
        valid.put(t2g3, List.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));

        // The plan hosts G04 on Thursday only and says nothing about the same teacher's
        // pair with group 2 (unrestricted); teacher 2 is entirely absent (unrestricted).
        Set<BlockKey> contradicted = GreedyRotationAssigner.restrictValidDays(valid,
                Map.of(t1g1, EnumSet.of(DayOfWeek.THURSDAY)));

        assertEquals(List.of(DayOfWeek.THURSDAY), valid.get(t1g1),
                "restricted pair keeps only the plan's weekday");
        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                valid.get(t1g2), "same teacher, pair the plan ignores: unconstrained");
        assertEquals(List.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), valid.get(t2g3),
                "teacher absent from the plan: unconstrained (current behaviour)");
        assertTrue(contradicted.isEmpty(), "a satisfiable plan contradicts nobody");
    }

    @Test
    void contradictedPairIsReportedNotSilentlyDropped() {
        BlockKey satisfied = new BlockKey(7L, 70L);
        BlockKey killed = new BlockKey(7L, 71L);
        Map<BlockKey, List<DayOfWeek>> valid = new LinkedHashMap<>();
        valid.put(satisfied, List.of(DayOfWeek.MONDAY));
        valid.put(killed, List.of(DayOfWeek.MONDAY));

        // The plan hosts 70 on Monday but 71 only on Friday, which the data excludes.
        Set<BlockKey> contradicted = GreedyRotationAssigner.restrictValidDays(valid,
                Map.of(satisfied, EnumSet.of(DayOfWeek.MONDAY),
                        killed, EnumSet.of(DayOfWeek.FRIDAY)));

        assertTrue(valid.get(killed).isEmpty(), "the intersection empties the pair");
        assertEquals(Set.of(killed), contradicted,
                "the emptied pair is REPORTED: the candidate fails fast and the next ranked"
                        + " plan is tried, the pair is never silently skipped");
    }

    @Test
    void alreadyEmptyPairIsNotBlamedOnThePlan() {
        BlockKey dead = new BlockKey(9L, 90L);
        Map<BlockKey, List<DayOfWeek>> valid = new LinkedHashMap<>();
        valid.put(dead, List.of());

        Set<BlockKey> contradicted = GreedyRotationAssigner.restrictValidDays(valid,
                Map.of(dead, EnumSet.of(DayOfWeek.FRIDAY)));

        assertTrue(valid.get(dead).isEmpty());
        assertTrue(contradicted.isEmpty(),
                "the pair was dead before the plan: pre-existing baseline skip, not a"
                        + " candidate rejection");
    }

    @Test
    void noRestrictionIsANoOp() {
        BlockKey key = new BlockKey(1L, 10L);
        Map<BlockKey, List<DayOfWeek>> valid = new LinkedHashMap<>();
        valid.put(key, List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

        Set<BlockKey> contradicted = GreedyRotationAssigner.restrictValidDays(valid, Map.of());

        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), valid.get(key),
                "empty plan map (no rotations built) leaves the greedy exactly as today");
        assertTrue(contradicted.isEmpty());
    }

    @Test
    void timelineYieldsPerPairWeekdays() {
        // Teacher 1 hosts G04 on Monday+Tuesday (rotating across periods) and G13 on
        // Thursday; G08 never appears (stays unrestricted).
        RotationEnumerator.RotationTimeline timeline = new RotationEnumerator.RotationTimeline(List.of(
                new RotationEnumerator.PeriodAssignment(Map.of(
                        cell("T1", DayOfWeek.MONDAY, 0), "G04",
                        cell("T1", DayOfWeek.THURSDAY, 0), "G13")),
                new RotationEnumerator.PeriodAssignment(Map.of(
                        cell("T1", DayOfWeek.TUESDAY, 0), "G04",
                        cell("T1", DayOfWeek.THURSDAY, 0), "G13"))));

        Map<BlockKey, Set<DayOfWeek>> restriction =
                HybridPhase1aService.restrictionFromTimeline(1L, timeline, GROUP_IDS);

        assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                restriction.get(new BlockKey(1L, 10L)),
                "G04 unions its weekdays across periods");
        assertEquals(Set.of(DayOfWeek.THURSDAY), restriction.get(new BlockKey(1L, 30L)));
        assertFalse(restriction.containsKey(new BlockKey(1L, 20L)),
                "a group the timeline never hosts is absent = unrestricted, not dropped");
        assertEquals(2, restriction.size());
    }

    @Test
    void twoRankedCandidatesProduceDifferentRestrictions() {
        // Same teacher, same weekday UNION in both timelines (the invariant that made the
        // union-based restriction mechanically inert): Mon+Tue+Thu all used, only WHO sits
        // where differs. The per-pair restrictions must differ - this IS the
        // multi-candidate mechanism.
        RotationEnumerator.RotationTimeline plan1 = new RotationEnumerator.RotationTimeline(List.of(
                new RotationEnumerator.PeriodAssignment(Map.of(
                        cell("T1", DayOfWeek.MONDAY, 0), "G04",
                        cell("T1", DayOfWeek.TUESDAY, 0), "G08",
                        cell("T1", DayOfWeek.THURSDAY, 0), "G13"))));
        RotationEnumerator.RotationTimeline plan2 = new RotationEnumerator.RotationTimeline(List.of(
                new RotationEnumerator.PeriodAssignment(Map.of(
                        cell("T1", DayOfWeek.MONDAY, 0), "G08",
                        cell("T1", DayOfWeek.TUESDAY, 0), "G13",
                        cell("T1", DayOfWeek.THURSDAY, 0), "G04"))));

        Map<BlockKey, Set<DayOfWeek>> r1 =
                HybridPhase1aService.restrictionFromTimeline(1L, plan1, GROUP_IDS);
        Map<BlockKey, Set<DayOfWeek>> r2 =
                HybridPhase1aService.restrictionFromTimeline(1L, plan2, GROUP_IDS);

        assertEquals(Set.of(DayOfWeek.MONDAY), r1.get(new BlockKey(1L, 10L)),
                "plan 1 hosts G04 on Monday only (same union, different mapping)");
        assertNotEquals(r1, r2,
                "rank 1 and rank 2 must restrict DIFFERENT pairs: G04 moves to Thursday,"
                        + " G08 to Monday");
        assertEquals(Set.of(DayOfWeek.THURSDAY), r2.get(new BlockKey(1L, 10L)));
        assertEquals(Set.of(DayOfWeek.MONDAY), r2.get(new BlockKey(1L, 20L)));
    }

    @Test
    void unresolvableGroupNameIsSkipped() {
        RotationEnumerator.RotationTimeline timeline = new RotationEnumerator.RotationTimeline(List.of(
                new RotationEnumerator.PeriodAssignment(Map.of(
                        cell("T1", DayOfWeek.MONDAY, 0), "G09"))));

        Map<BlockKey, Set<DayOfWeek>> restriction =
                HybridPhase1aService.restrictionFromTimeline(1L, timeline, GROUP_IDS);

        assertTrue(restriction.isEmpty(),
                "a group that is not one of this phase's pairs contributes no restriction");
    }

    @Test
    void wiringFlagDefaultsToOff() {
        System.clearProperty("planner.pre-assign.rotation-enumerator.enabled");
        assertFalse(Boolean.parseBoolean(
                System.getProperty("planner.pre-assign.rotation-enumerator.enabled", "false")),
                "TASK-467 wiring is opt-in: unset property reads disabled");
        assertFalse(RotationEnumerator.preAssignEnabled(),
                "the production gate reads the same default");
    }

    private static RotationEnumerator.Cell cell(String teacher, DayOfWeek weekday, int slot) {
        return new RotationEnumerator.Cell(teacher, weekday, slot);
    }
}
