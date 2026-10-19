package com.school.planner.service;

import static com.school.planner.service.ConstrainedTeacherAllocator.loadGoldenProfile;
import static com.school.planner.service.ConstrainedTeacherAllocator.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.school.planner.service.ConstrainedTeacherAllocator.Plan;

/**
 * L0 unit tests for {@link ConstrainedTeacherAllocator} (TASK-435 checkpoint-first step 2).
 *
 * <p>Covers the pure pin-planning logic: golden profile parsing, H5/H7 respect, one-group-per-date
 * disjointness, course-budget exhaustion (residual = explicit exception), and determinism
 * (idempotent pure function). No JPA, no Timefold, no Quarkus, runs in milliseconds.
 *
 * <p>The golden-profile parse reads the real classpath golden ({@code seed/lessons.csv}), so the
 * "T01 = 231h across 4 groups on 35 disjoint Thursdays" invariant is pinned against the
 * authoritative school data, not a hand-rolled fixture.
 */
class ConstrainedTeacherAllocatorTest {

    private static final Map<String, Integer> T01_BUDGETS = Map.of(
            "G02", 60, "G06", 60, "G10", 65, "G12", 46);

    // --- profile parsing (the authoritative golden reference) ---

    @Test
    void goldenProfileForT01SumsToCourseBudgetsAcrossDisjointThursdays() {
        Map<String, Map<LocalDate, Integer>> profile = loadGoldenProfile("T01");

        // 4 groups, each summing to its course budget hour-for-hour (231h total).
        assertEquals(Set.of("G02", "G06", "G10", "G12"), profile.keySet());
        assertEquals(60, hours(profile, "G02"));
        assertEquals(60, hours(profile, "G06"));
        assertEquals(65, hours(profile, "G10"));
        assertEquals(46, hours(profile, "G12"));

        // 35 Thursdays total (10 + 10 + 9 + 6), one group per Thursday (disjoint).
        Set<LocalDate> allDates = profile.values().stream()
                .flatMap(m -> m.keySet().stream()).collect(Collectors.toSet());
        assertEquals(35, allDates.size());
        assertTrue(allDates.stream().allMatch(d -> d.getDayOfWeek() == DayOfWeek.THURSDAY),
                "every T01 golden date must be a Thursday (H5 Thursday-only)");
    }

    @Test
    void unknownTeacherHasEmptyProfile() {
        assertTrue(loadGoldenProfile("No Such Teacher").isEmpty());
    }

    // --- plan: golden-faithful (the decisive happy path) ---

    @Test
    void planForT01IsGoldenFaithfulNoWarningsNoResidual() {
        Map<String, Map<LocalDate, Integer>> profile = loadGoldenProfile("T01");
        Plan p = plan(profile, T01_BUDGETS, 8);

        assertEquals(35, p.targets().size(), "one target per golden Thursday");
        assertTrue(p.warnings().isEmpty(), "real golden must be H5/H7/disjoint/budget-clean: " + p.warnings());
        assertEquals(T01_BUDGETS, p.pinnedHoursByGroup(), "pinned hours exhaust every course budget");
        assertTrue(p.residualHoursByGroup().values().stream().allMatch(r -> r == 0),
                "no residual on the real profile: " + p.residualHoursByGroup());

        // H7 respected by construction: no target exceeds the 8h cap.
        assertTrue(p.targets().stream().allMatch(t -> t.hours() <= 8));
    }

    // --- H7 guard ---

    @Test
    void h7CapClampsAndWarns() {
        LocalDate thu = LocalDate.of(2027, 1, 7); // a Thursday
        Map<String, Map<LocalDate, Integer>> profile = Map.of("G10", Map.of(thu, 9));
        Plan p = plan(profile, Map.of("G10", 65), 8);

        assertEquals(8, p.targets().get(0).hours(), "9h day clamped to the 8h H7 cap");
        assertTrue(anyWarningContains(p, "H7"));
    }

    // --- disjointness guard ---

    @Test
    void oneGroupPerDateSecondClaimantSkipped() {
        LocalDate thu = LocalDate.of(2026, 10, 29); // a Thursday
        Map<String, Map<LocalDate, Integer>> profile = new java.util.LinkedHashMap<>();
        profile.put("G02", Map.of(thu, 6));
        profile.put("G06", Map.of(thu, 6)); // same Thursday, different group
        Plan p = plan(profile, Map.of("G02", 60, "G06", 60), 8);

        assertEquals(1, p.targets().size(), "one group per date: the second claimant is skipped");
        assertEquals("G02", p.targets().get(0).groupName(), "first claimant wins (golden-faithful)");
        assertTrue(anyWarningContains(p, "Disjointness"));
    }

    // --- budget residual (pattern-exhaustion explicit exception) ---

    @Test
    void budgetResidualLeftToSolverIsReported() {
        LocalDate thu = LocalDate.of(2026, 10, 29);
        Map<String, Map<LocalDate, Integer>> profile = Map.of("G02", Map.of(thu, 6));
        Plan p = plan(profile, Map.of("G02", 60), 8); // budget 60, plan pins 6

        assertEquals(6, p.pinnedHoursByGroup().get("G02"));
        assertEquals(54, p.residualHoursByGroup().get("G02"), "54h left to the solver");
        assertTrue(anyWarningContains(p, "Residual"));
    }

    @Test
    void overBudgetEntriesAreTrimmed() {
        LocalDate thu = LocalDate.of(2026, 10, 29);
        // budget 10, two Thursdays of 6h each = 12h would exceed; second is trimmed/skipped
        Map<String, Map<LocalDate, Integer>> profile = Map.of(
                "G02", Map.of(thu, 6, thu.plusDays(7), 6));
        Plan p = plan(profile, Map.of("G02", 10), 8);

        assertEquals(10, p.pinnedHoursByGroup().get("G02"), "never pin beyond the course budget");
        assertTrue(anyWarningContains(p, "Budget"));
    }

    // --- determinism / idempotency ---

    @Test
    void planIsDeterministic() {
        Map<String, Map<LocalDate, Integer>> profile = loadGoldenProfile("T01");
        Plan a = plan(profile, T01_BUDGETS, 8);
        Plan b = plan(profile, T01_BUDGETS, 8);
        assertEquals(a.targets(), b.targets(), "same input -> identical plan (pure function)");
    }

    @Test
    void emptyProfileIsAnEmptyPlan() {
        Plan p = plan(Map.of(), T01_BUDGETS, 8);
        assertTrue(p.targets().isEmpty());
        // With nothing pinned, the whole budget is residual (left to the solver).
        assertEquals(T01_BUDGETS, p.residualHoursByGroup());
    }

    // --- helpers ---

    private static int hours(Map<String, Map<LocalDate, Integer>> profile, String group) {
        return profile.getOrDefault(group, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
    }

    private static boolean anyWarningContains(Plan p, String needle) {
        return p.warnings().stream().anyMatch(w -> w.contains(needle));
    }

    @Test
    void targetKeyIsStable() {
        LocalDate thu = LocalDate.of(2026, 10, 29);
        List<ConstrainedTeacherAllocator.PinTarget> t = plan(
                Map.of("G02", Map.of(thu, 6)), Map.of("G02", 60), 8).targets();
        assertEquals("G02|" + thu, t.get(0).key());
    }
}
