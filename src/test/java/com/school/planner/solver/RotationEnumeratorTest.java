package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.school.planner.solver.RotationEnumerator.Cell;
import com.school.planner.solver.RotationEnumerator.EnumResult;
import com.school.planner.solver.RotationEnumerator.PeriodAssignment;
import com.school.planner.solver.RotationEnumerator.RotationGroup;
import com.school.planner.solver.RotationEnumerator.RotationTeacher;
import com.school.planner.solver.RotationEnumerator.RotationTimeline;

/**
 * Localized gate test for the TASK-467 fase-2 skeleton ({@link RotationEnumerator}).
 *
 * <p>Plain JUnit, no {@code @QuarkusTest}, no database, no solver: the enumerator is
 * pure Java fed by solo-input fixtures (incarichi + lab-days + availability + slot
 * schema) derived verbatim from the 2026/27 seed. This matches the gate mandated for
 * the night spike: the enumerator, on SOLO INPUT, (a) reproduces at least one known
 * valid rotation (the T01 Thursday) among its candidates and (b) reports the
 * candidate count for a sample period. No solve, no adoption, flag stays OFF.
 *
 * <p>Fixtures are the real acconciatura instance documented in
 * {@code claudedocs/spalmone-2026-27/task467-fase2-design-misurato-2026-08-01.md}
 * (sect. 1.2/1.3): the per-teacher per-period branching measured from solo input is
 * T01 4 / T19 4 / T18 36 / T26 120, all {@code < 10^4}.
 */
class RotationEnumeratorTest {

    // ─── Real 2026/27 input fixtures (solo input) ─────────────────────────

    /** Lab-days (modalita' L) per group per weekday, from calendario_class_days / modello parte 3. */
    private static Map<String, Set<DayOfWeek>> acconciaturaLabDays() {
        // Deterministic immutable sets.
        Map<String, Set<DayOfWeek>> m = new TreeMap<>();
        m.put("G02", Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        m.put("G06", Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        m.put("G10", Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY));
        m.put("G12", Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY));
        m.put("G14", Set.of(DayOfWeek.THURSDAY));
        return m;
    }

    /** T01: Servizio tecnico e stilistico, Thu-only (mono-day), 4 eligible groups. */
    private static RotationTeacher t01() {
        return new RotationTeacher("T01", "Servizio tecnico e stilistico",
                List.of(DayOfWeek.THURSDAY),
                List.of(new RotationGroup("G02", 60), new RotationGroup("G06", 60),
                        new RotationGroup("G10", 65), new RotationGroup("G12", 46)),
                6, 0);
    }

    /** T19: Barberia, Mon/Tue. */
    private static RotationTeacher t19() {
        return new RotationTeacher("T19", "Barberia",
                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                List.of(new RotationGroup("G02", 36), new RotationGroup("G06", 47),
                        new RotationGroup("G12", 45)),
                6, 0);
    }

    /** T18: Lavaggio {1,2,G10}, Mon-Fri. */
    private static RotationTeacher t18() {
        return new RotationTeacher("T18", "Lavaggio",
                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                List.of(new RotationGroup("G02", 50), new RotationGroup("G06", 55),
                        new RotationGroup("G10", 55)),
                6, 0);
    }

    /** T26: Servizio stilistico, Mon-Fri, 5 groups incl. G14. */
    private static RotationTeacher t26() {
        return new RotationTeacher("T26", "Servizio stilistico",
                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                List.of(new RotationGroup("G02", 40), new RotationGroup("G06", 50),
                        new RotationGroup("G10", 50), new RotationGroup("G12", 30),
                        new RotationGroup("G14", 30)),
                6, 0);
    }

    // ─── Gate (b): candidate count for a sample period = per-teacher branching ───

    @Test
    void perPeriodBranching_matchesDesignMeasuredFromSoloInput() {
        Map<String, Set<DayOfWeek>> labDays = acconciaturaLabDays();
        // The design's measured headline (sect. 1.3), reproduced here from solo input.
        assertEquals(4, RotationEnumerator.perPeriodBranching(t01(), labDays), "T01 Thu x 4 groups");
        assertEquals(4, RotationEnumerator.perPeriodBranching(t19(), labDays), "T19 Mon x2, Tue x2");
        assertEquals(36, RotationEnumerator.perPeriodBranching(t18(), labDays), "T18 1x3x2x3x2");
        assertEquals(120, RotationEnumerator.perPeriodBranching(t26(), labDays), "T26 2x3x2x5x2");
    }

    @Test
    void enumeratePeriodTeacher_T26ProducesBranchingCandidatesUnderCap() {
        // T26 is the largest per-teacher per-period space (branching 120); it must enumerate
        // fully and NOT hit the MAX_CANDIDATES=10000 early-abort (120 < 10000). This exercises the
        // reactivated DFS form (MCV ordering + early-abort wiring) on the 5-weekday-cell case.
        EnumResult<PeriodAssignment> result = RotationEnumerator.enumeratePeriodTeacher(t26(), acconciaturaLabDays());
        assertFalse(result.capped(), "120 < MAX_CANDIDATES, the cap must not fire");
        assertEquals(120, result.candidates().size(), "exhaustive per-period enumeration = branching");
        // Every candidate assigns exactly one eligible group per weekday-cell (5 cells).
        for (PeriodAssignment pa : result.candidates()) {
            assertEquals(5, pa.cellToGroup().size(), "T26 has 5 weekday-cells");
        }
    }

    // ─── Gate (a): reproduces the known T01 Thursday rotation ─────────

    @Test
    void enumeratePeriodTeacher_T01ReproducesThursdayRotationFragment() {
        EnumResult<PeriodAssignment> result = RotationEnumerator.enumeratePeriodTeacher(t01(), acconciaturaLabDays());
        assertFalse(result.capped());
        assertEquals(4, result.candidates().size());

        // The known structural fragment: T01's Thursday morning cell rotates over her 4 groups.
        Cell thursdayCell = new Cell("T01", DayOfWeek.THURSDAY, 0);
        Set<String> assignedGroups = new java.util.TreeSet<>();
        for (PeriodAssignment pa : result.candidates()) {
            assertEquals(thursdayCell, pa.cellToGroup().keySet().iterator().next(), "the only cell is Thu slot 0");
            assignedGroups.add(pa.cellToGroup().get(thursdayCell));
        }
        assertEquals(Set.of("G02", "G06", "G10", "G12"), assignedGroups,
                "the 4 candidates must be exactly T01's 4 eligible groups on Thursday");
    }

    @Test
    void enumeratePeriodJoint_twoTeachersNoSharedWeekday_isProductOfBranchings() {
        // T01 (Thu only) and T19 (Mon/Tue) share no weekday, so the joint count is the
        // product of their branchings with zero cross-teacher H3 pruning.
        EnumResult<PeriodAssignment> result = RotationEnumerator.enumeratePeriodJoint(
                List.of(t01(), t19()), acconciaturaLabDays());
        assertFalse(result.capped());
        assertEquals(16, result.candidates().size(), "4 (T01) x 4 (T19) = 16");
    }

    @Test
    void enumeratePeriodJoint_prunesCrossTeacherSameWeekdayGroupConflict() {
        // Synthetic: two teachers sharing one weekday (Thu) with overlapping groups. Without the
        // H3-weekday prune the joint count would be 2 x 2 = 4; the prune forbids the same group on
        // the same weekday across two teachers, leaving only the 2 off-diagonal assignments.
        RotationTeacher a = new RotationTeacher("A", "Sub",
                List.of(DayOfWeek.THURSDAY),
                List.of(new RotationGroup("g1", 30), new RotationGroup("g2", 30)), 6, 0);
        RotationTeacher b = new RotationTeacher("B", "Sub",
                List.of(DayOfWeek.THURSDAY),
                List.of(new RotationGroup("g1", 30), new RotationGroup("g2", 30)), 6, 0);
        Map<String, Set<DayOfWeek>> labDays = new TreeMap<>();
        labDays.put("g1", Set.of(DayOfWeek.THURSDAY));
        labDays.put("g2", Set.of(DayOfWeek.THURSDAY));

        EnumResult<PeriodAssignment> result = RotationEnumerator.enumeratePeriodJoint(List.of(a, b), labDays);
        assertFalse(result.capped());
        assertEquals(2, result.candidates().size(), "diagonal g1/g1 and g2/g2 pruned by cross-teacher H3");
    }

    // ─── Timeline layer: reproduces at least one valid T01 rotation ──

    @Test
    void enumerateTimelines_T01ReproducesAtLeastOneValidRotation() {
        // The timeline layer (strato nuovo) chains periods and accepts a rotation only when every
        // group's budget is covered. weeksPerPeriod is a test parameter (the period count is a
        // planner choice, not an input fact); 10 weeks x blockSize 6 = 60h cover a group in 1 period.
        EnumResult<RotationTimeline> result = RotationEnumerator.enumerateTimelines(
                t01(), acconciaturaLabDays(), 8, 10);

        assertTrue(result.candidates().size() >= 1, "at least one valid T01 rotation must exist");
        // Every returned timeline is structurally valid: covers all 4 budgets and every period is
        // the T01-Thursday fragment (mono-day + slot inheritance).
        for (RotationTimeline timeline : result.candidates()) {
            assertTrue(coversAllBudgets(timeline, t01(), 60),
                    "every returned timeline must cover all group budgets");
            for (PeriodAssignment pa : timeline.periods()) {
                assertEquals(1, pa.cellToGroup().size());
                Cell cell = pa.cellToGroup().keySet().iterator().next();
                assertEquals(DayOfWeek.THURSDAY, cell.weekday(), "T01 is mono-Thursday");
                assertEquals("T01", cell.teacher());
                assertTrue(Set.of("G02", "G06", "G10", "G12").contains(pa.cellToGroup().get(cell)),
                        "period group must be one of T01's eligible groups");
            }
        }
        // Minimal cover is 5 periods (G02 1, G06 1, G10 2, G12 1); at least those exist.
        assertTrue(result.candidates().stream().anyMatch(t -> t.periods().size() == 5),
                "a minimal 5-period cover must be among the candidates");
    }

    /** Budget coverage check: each group's periods x hoursPerPeriod >= its budget. */
    private static boolean coversAllBudgets(RotationTimeline timeline, RotationTeacher teacher, int hoursPerPeriod) {
        Map<String, Integer> placed = new TreeMap<>();
        for (RotationGroup g : teacher.groups()) {
            placed.put(g.name(), 0);
        }
        for (PeriodAssignment pa : timeline.periods()) {
            for (String group : pa.cellToGroup().values()) {
                placed.merge(group, hoursPerPeriod, Integer::sum);
            }
        }
        for (RotationGroup g : teacher.groups()) {
            if (placed.get(g.name()) < g.budgetHours()) {
                return false;
            }
        }
        return true;
    }

    // ─── Flag default OFF (opt-in, not wired) ─────────────────────────────

    @Test
    void isEnabled_defaultsFalseAndIsOptIn() {
        String previous = System.getProperty(RotationEnumerator.FLAG);
        try {
            System.clearProperty(RotationEnumerator.FLAG);
            assertFalse(RotationEnumerator.isEnabled(), "absent flag must mean OFF (opt-in)");
            System.setProperty(RotationEnumerator.FLAG, "false");
            assertFalse(RotationEnumerator.isEnabled(), "literal 'false' must mean OFF");
            System.setProperty(RotationEnumerator.FLAG, "true");
            assertTrue(RotationEnumerator.isEnabled(), "only literal 'true' activates");
        } finally {
            if (previous == null) {
                System.clearProperty(RotationEnumerator.FLAG);
            } else {
                System.setProperty(RotationEnumerator.FLAG, previous);
            }
        }
    }

    // ─── AC#2: multi-candidate ranking + top-k ─────────────────────────────

    /**
     * AC#2: enumerateTopK returns k DISTINCT ranked candidates on the canonical
     * T01 case, ordering by the input-driven terms (lab-day alignment first:
     * placements on declared lab days score higher than off-day placements).
     */
    @Test
    void enumerateTopK_returnsDistinctRankedCandidates() {
        // Same canonical setup as the timeline test: 10 weeks x blockSize 6 covers a
        // group per period, so full-budget timelines exist and get ranked.
        var res = RotationEnumerator.enumerateTopK(t01(), acconciaturaLabDays(), 8, 10, 3);
        assertTrue(res.candidates().size() <= 3, "top-k respects k");
        assertTrue(res.candidates().size() >= 1, "the canonical case has at least one timeline");
        // Distinctness: no two signatures equal.
        var sigs = res.candidates().stream().map(RotationEnumerator.ScoredTimeline::signature).toList();
        assertEquals(sigs.size(), sigs.stream().distinct().count(), "candidates are structurally distinct");
        // Ranking is descending by score.
        for (int i = 1; i < res.candidates().size(); i++) {
            assertTrue(res.candidates().get(i - 1).score() >= res.candidates().get(i).score(),
                    "ranking is descending");
        }
    }

    /**
     * AC#2: lab-day alignment dominates. Two single-period placements for the same group:
     * one on the group's declared lab day, one elsewhere - the aligned one ranks first.
     */
    @Test
    void rankTopK_prefersLabDayAlignedPlacements() {
        var aligned = new RotationEnumerator.RotationTimeline(List.of(
                new RotationEnumerator.PeriodAssignment(Map.of(
                        new RotationEnumerator.Cell("T", DayOfWeek.THURSDAY, 0), "G1"))));
        var offDay = new RotationEnumerator.RotationTimeline(List.of(
                new RotationEnumerator.PeriodAssignment(Map.of(
                        new RotationEnumerator.Cell("T", DayOfWeek.WEDNESDAY, 0), "G1"))));
        var labDays = Map.of("G1", Set.of(DayOfWeek.THURSDAY));
        var ranked = RotationEnumerator.rankTopK(List.of(offDay, aligned), labDays, 4, 2);
        assertEquals(aligned.periods().get(0).cellToGroup()
                        .get(new RotationEnumerator.Cell("T", DayOfWeek.THURSDAY, 0)),
                ranked.get(0).timeline().periods().get(0).cellToGroup()
                        .get(new RotationEnumerator.Cell("T", DayOfWeek.THURSDAY, 0)),
                "the lab-day-aligned timeline ranks first");
    }

    /**
     * AC#2: weekday economy. A timeline spreading one group over two weekdays ranks
     * below the same coverage kept on one weekday (mono-day rotation shape).
     */
    @Test
    void rankTopK_prefersMonoDayShape() {
        var mono = new RotationEnumerator.RotationTimeline(List.of(
                new RotationEnumerator.PeriodAssignment(Map.of(
                        new RotationEnumerator.Cell("T", DayOfWeek.THURSDAY, 0), "G1")),
                new RotationEnumerator.PeriodAssignment(Map.of(
                        new RotationEnumerator.Cell("T", DayOfWeek.THURSDAY, 0), "G1"))));
        var spread = new RotationEnumerator.RotationTimeline(List.of(
                new RotationEnumerator.PeriodAssignment(Map.of(
                        new RotationEnumerator.Cell("T", DayOfWeek.THURSDAY, 0), "G1")),
                new RotationEnumerator.PeriodAssignment(Map.of(
                        new RotationEnumerator.Cell("T", DayOfWeek.FRIDAY, 0), "G1"))));
        var labDays = Map.of("G1", Set.of(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        var ranked = RotationEnumerator.rankTopK(List.of(spread, mono), labDays, 4, 2);
        assertEquals(2, ranked.get(0).timeline().periods().size());
        assertTrue(ranked.get(0).timeline().periods().stream()
                .allMatch(pa -> pa.cellToGroup().containsKey(
                        new RotationEnumerator.Cell("T", DayOfWeek.THURSDAY, 0))),
                "the mono-day timeline ranks first");
    }

    /**
     * AC#2: determinism - the same candidate list ranks identically across calls
     * (tie-break on the structural signature, never HashMap iteration order).
     */
    @Test
    void rankTopK_isDeterministic() {
        var a = new RotationEnumerator.RotationTimeline(List.of(
                new RotationEnumerator.PeriodAssignment(Map.of(
                        new RotationEnumerator.Cell("T", DayOfWeek.THURSDAY, 0), "G1"))));
        var b = new RotationEnumerator.RotationTimeline(List.of(
                new RotationEnumerator.PeriodAssignment(Map.of(
                        new RotationEnumerator.Cell("T", DayOfWeek.TUESDAY, 0), "G1"))));
        var labDays = Map.of("G1", Set.of(DayOfWeek.THURSDAY, DayOfWeek.TUESDAY));
        var first = RotationEnumerator.rankTopK(java.util.List.of(a, b), labDays, 4, 2);
        var second = RotationEnumerator.rankTopK(java.util.List.of(b, a), labDays, 4, 2);
        assertEquals(first.stream().map(RotationEnumerator.ScoredTimeline::signature).toList(),
                second.stream().map(RotationEnumerator.ScoredTimeline::signature).toList(),
                "input order does not change the ranking");
    }
}
