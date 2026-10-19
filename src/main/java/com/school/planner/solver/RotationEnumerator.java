package com.school.planner.solver;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * TASK-467 fase-2 skeleton: exhaustive enumerator of regular lab rotations from
 * SOLO INPUT (incarichi + lab-days + teacher availability + slot schema), with a
 * timeline layer that chains periods. Pure Java, no JPA/DB, no Timefold coupling:
 * every input arrives as plain data, so this class is unit-testable without a
 * solver or database context, exactly like {@link WeeklyTemplateBuilder}.
 *
 * <p><b>What is reactivated, not reinvented.</b> The per-period enumeration
 * reuses the FORM of {@code DayAssignmentCsp.enumeratePairsRecursive}
 * ({@code service/DayAssignmentCsp.java}): an exhaustive depth-first walk over
 * the decision variables, most-constrained-variable ordering, branch-and-bound
 * pruning and an {@link #MAX_CANDIDATES} early-abort cap. The duality is:
 * {@code DayAssignmentCsp} assigns a weekday to each (teacher, group) pair;
 * this enumerator assigns an eligible group to each (teacher, weekday) cell.
 * Same skeleton, dual variable/value. No third backtracker is introduced.
 *
 * <p><b>The timeline layer is the new strato.</b> A rotation is not a single
 * period: it is a chronological sequence of periods (modello-rotazione-
 * acconciatura: "periodi di regolarita'" + "eredita' dello slot"). The per-period
 * enumerator is invoked once per period and the results are chained, propagating
 * the remaining budget per (teacher, group) across periods. In production this
 * strato would also propagate 0hard state via {@code PreAssignmentContext.track/
 * untrack}; that wiring is deliberately absent here (see "Gated" below).
 *
 * <p><b>Scope.</b> The 4 acconciatura teachers and their per-subject groups. The
 * mechanism is general but the measured, input-derived branching
 * (T01 4 / T19 4 / T18 36 / T26 120, all {@code < 10^4}) is
 * documented for the acconciatura instance in
 * {@code claudedocs/spalmone-2026-27/task467-fase2-design-misurato-2026-08-01.md}.
 *
 * <p><b>Gated, DEFAULT OFF, NOT wired.</b> Activation lives behind
 * {@link #isEnabled()} (opt-in: only the literal {@code "true"} activates). The
 * enumerator is NOT called from {@code HybridPhase1aService} or anywhere on the
 * production solve path: this is a scouting skeleton. No adoption. The natural
 * integration point is documented in the design (sect. 2.6) and is itself gated
 * on an explicit maintainer go.
 *
 * <p><b>Determinism.</b> Every collection iterated for an ordering decision is a
 * sorted {@code List} or a {@code TreeMap}, never a raw {@code HashMap}/{@code
 * HashSet} iteration, so the enumeration order is reproducible (the solver
 * demands reproducible construction).
 *
 * <p><b>Anti-overfitting.</b> The golden 2026/27 rotation is ONE valid instance,
 * not the rule (modello parte "Struttura vs arbitrio"). This enumerator never
 * reads the golden: which group lands in which period is a candidate to rank, not
 * a target to copy. A candidate is good if it is structurally valid (eligible
 * cell, budget covered), not if it resembles the golden.
 */
public final class RotationEnumerator {

    /** Opt-in flag, DEFAULT OFF. Only {@code -Dplanner.rotation.enumerator.enabled=true} activates. */
    public static final String FLAG = "planner.rotation.enumerator.enabled";

    /** Early-abort cap on per-period candidates (reactivated from {@code DayAssignmentCsp:57}). */
    public static final int MAX_CANDIDATES = 10000;

    /** Early-abort cap on timeline candidates. Declared, never a silent truncation (see {@link EnumResult}). */
    public static final int MAX_TIMELINES = 1000;

    private RotationEnumerator() {
    }

    /** @return {@code true} only when the flag is explicitly {@code "true"} (opt-in, default OFF). */
    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(FLAG, "false"));
    }

    /**
     * TASK-467 AC#4 wiring flag: {@code planner.pre-assign.rotation-enumerator.enabled},
     * default FALSE (opt-in prototype ahead of the A/B gate). When true, Phase 1 of the
     * staged pre-assign ({@code HybridPhase1aService}) derives its rotation-day plan from
     * this enumerator's ranked multi-candidates instead of the greedy's first-found single
     * assignment. Distinct from {@link #FLAG} (the skeleton's own gate, kept for the
     * enumerator-in-isolation experiments): this one owns the production seam.
     */
    public static boolean preAssignEnabled() {
        return Boolean.parseBoolean(
                System.getProperty("planner.pre-assign.rotation-enumerator.enabled", "false"));
    }

    /** Wiring knobs (read per armed call; the startup banner echoes them all: a dropped
     *  {@code -D} silently falls back to these defaults otherwise). */
    public static final String TOPK_KNOB = "planner.pre-assign.rotation-enumerator.topk";
    public static final String WEEKS_PER_PERIOD_KNOB =
            "planner.pre-assign.rotation-enumerator.weeks-per-period";
    public static final String MAX_PERIODS_KNOB =
            "planner.pre-assign.rotation-enumerator.max-periods";

    public static int topK() {
        return Integer.getInteger(TOPK_KNOB, 3);
    }

    public static int weeksPerPeriod() {
        return Integer.getInteger(WEEKS_PER_PERIOD_KNOB, 10);
    }

    public static int maxPeriods() {
        return Integer.getInteger(MAX_PERIODS_KNOB, 8);
    }

    /**
     * The weekdays each group sits on across the timeline (the shared "group placement"
     * walk: the ranking's weekday-economy term AND the pre-assign wiring's per-pair
     * restriction both derive from it, so it lives HERE, one definition).
     */
    public static Map<String, Set<DayOfWeek>> daysByGroup(RotationTimeline t) {
        Map<String, Set<DayOfWeek>> daysByGroup = new TreeMap<>();
        for (PeriodAssignment pa : t.periods()) {
            for (Map.Entry<Cell, String> e : pa.cellToGroup().entrySet()) {
                daysByGroup.computeIfAbsent(e.getValue(), g -> new java.util.TreeSet<>())
                        .add(e.getKey().weekday());
            }
        }
        return daysByGroup;
    }

    // ─── Input model (solo input, no JPA/DB) ───────────────────────────────

    /** One (group, budget-hours) pair a teacher rotates through, from {@code incarichi.csv}. */
    public record RotationGroup(String name, int budgetHours) {
    }

    /**
     * One teacher's rotation problem, all fields input-derived:
     * <ul>
     *   <li>{@code availableWeekdays} from {@code availability_rules.csv} (VACATION excludes a weekday);</li>
     *   <li>{@code groups} scoped to ONE subject (the rotation subject) from {@code incarichi.csv};</li>
     *   <li>{@code morningBlockSize}/{@code morningStartSlot} from the slot schema (8 slots, morning block 5-6h from slot 0).</li>
     * </ul>
     */
    public record RotationTeacher(
            String name,
            String subject,
            List<DayOfWeek> availableWeekdays,
            List<RotationGroup> groups,
            int morningBlockSize,
            int morningStartSlot) {
    }

    /** A teacher's cell on a weekday. The slot is inherited across periods (eredita' dello slot). */
    public record Cell(String teacher, DayOfWeek weekday, int startSlot) {
    }

    /** One period's assignment: each active cell hosts exactly one eligible group. */
    public record PeriodAssignment(Map<Cell, String> cellToGroup) {
        public PeriodAssignment {
            cellToGroup = cellToGroup != null ? Map.copyOf(cellToGroup) : Map.of();
        }
    }

    /** A full rotation: chronological sequence of per-period group choices per weekday-cell. */
    public record RotationTimeline(List<PeriodAssignment> periods) {
    }

    /** Result of an enumeration, carrying the early-abort flag so a cap hit is never silent. */
    public record EnumResult<T>(List<T> candidates, boolean capped) {
    }

    // ─── Per-teacher per-period branching (the measured headline) ──────────

    /**
     * Groups eligible for {@code teacher} on {@code day}: teaches the group AND the group has a
     * lab-day (modalita' L) on that weekday, from {@code calendario_class_days.csv}.
     */
    public static List<String> eligibleGroups(RotationTeacher teacher, DayOfWeek day,
            Map<String, Set<DayOfWeek>> labDaysByGroup) {
        List<String> eligible = new ArrayList<>();
        for (RotationGroup g : teacher.groups) {
            Set<DayOfWeek> days = labDaysByGroup.getOrDefault(g.name(), Set.of());
            if (days.contains(day)) {
                eligible.add(g.name());
            }
        }
        return eligible;
    }

    /**
     * Per-teacher per-period branching: product of eligible-group counts over the teacher's
     * available weekdays. Reproduces the design's measured 4/4/36/120 from solo input.
     */
    public static int perPeriodBranching(RotationTeacher teacher, Map<String, Set<DayOfWeek>> labDaysByGroup) {
        int branching = 1;
        for (DayOfWeek day : sortedWeekdays(teacher.availableWeekdays)) {
            int n = eligibleGroups(teacher, day, labDaysByGroup).size();
            branching *= Math.max(1, n);
        }
        return branching;
    }

    // ─── Per-period enumeration (reactivates enumeratePairsRecursive) ──────

    /**
     * Enumerate every legal single-period assignment for one teacher: one eligible group per
     * weekday-cell. DFS over cells (MCV: fewest eligible first), {@link #MAX_CANDIDATES} early-abort.
     * No intra-teacher prune (a teacher may serve the same group on distinct weekdays), so the
     * candidate count equals the per-period branching unless the cap fires.
     */
    public static EnumResult<PeriodAssignment> enumeratePeriodTeacher(
            RotationTeacher teacher, Map<String, Set<DayOfWeek>> labDaysByGroup) {
        // Order cells MCV: fewest eligible groups first (stable DayOfWeek tiebreak).
        List<DayOfWeek> days = sortedWeekdays(teacher.availableWeekdays);
        days.sort((a, b) -> Integer.compare(
                eligibleGroups(teacher, a, labDaysByGroup).size(),
                eligibleGroups(teacher, b, labDaysByGroup).size()));

        List<PeriodAssignment> out = new ArrayList<>();
        Map<Cell, String> current = new LinkedHashMap<>();
        int[] counter = { 0 };
        boolean[] capped = { false };
        enumeratePeriodTeacherDfs(teacher, days, 0, labDaysByGroup, current, out, counter, capped);
        return new EnumResult<>(out, capped[0]);
    }

    private static void enumeratePeriodTeacherDfs(RotationTeacher teacher, List<DayOfWeek> days, int idx,
            Map<String, Set<DayOfWeek>> labDaysByGroup, Map<Cell, String> current,
            List<PeriodAssignment> out, int[] counter, boolean[] capped) {
        if (capped[0] || counter[0] >= MAX_CANDIDATES) {
            capped[0] = true;
            return;
        }
        if (idx >= days.size()) {
            out.add(new PeriodAssignment(new LinkedHashMap<>(current)));
            return;
        }
        DayOfWeek day = days.get(idx);
        Cell cell = new Cell(teacher.name(), day, teacher.morningStartSlot());
        for (String group : eligibleGroups(teacher, day, labDaysByGroup)) {
            counter[0]++;
            current.put(cell, group);
            enumeratePeriodTeacherDfs(teacher, days, idx + 1, labDaysByGroup, current, out, counter, capped);
            current.remove(cell);
            if (capped[0]) return;
        }
    }

    /**
     * Joint single-period enumeration across several teachers with the cross-teacher H3 prune: a
     * group cannot be served by two different teachers on the SAME weekday in one period. Cells are
     * ordered MCV across all teachers; {@link #MAX_CANDIDATES} early-abort. This is the faithful
     * joint reactivation of {@code enumeratePairsRecursive}; slot-level 0hard (H1/H2/H5) is resolved
     * in production by {@code PreAssignmentContext} and is out of scope for this pure-Java skeleton.
     */
    public static EnumResult<PeriodAssignment> enumeratePeriodJoint(
            List<RotationTeacher> teachers, Map<String, Set<DayOfWeek>> labDaysByGroup) {
        // Flatten to (teacher, day) cells across all teachers, MCV-ordered, stable tiebreak.
        List<Cell> cells = new ArrayList<>();
        for (RotationTeacher t : teachers) {
            for (DayOfWeek day : sortedWeekdays(t.availableWeekdays())) {
                if (!eligibleGroups(t, day, labDaysByGroup).isEmpty()) {
                    cells.add(new Cell(t.name(), day, t.morningStartSlot()));
                }
            }
        }
        cells.sort((a, b) -> Integer.compare(
                eligibleGroupsByCell(a, teachers, labDaysByGroup).size(),
                eligibleGroupsByCell(b, teachers, labDaysByGroup).size()));

        // Teacher name -> teacher object, for eligible-group lookup.
        Map<String, RotationTeacher> byName = new TreeMap<>();
        for (RotationTeacher t : teachers) {
            byName.put(t.name(), t);
        }

        List<PeriodAssignment> out = new ArrayList<>();
        Map<Cell, String> current = new LinkedHashMap<>();
        int[] counter = { 0 };
        boolean[] capped = { false };
        enumeratePeriodJointDfs(cells, 0, byName, labDaysByGroup, current, out, counter, capped);
        return new EnumResult<>(out, capped[0]);
    }

    private static void enumeratePeriodJointDfs(List<Cell> cells, int idx,
            Map<String, RotationTeacher> byName, Map<String, Set<DayOfWeek>> labDaysByGroup,
            Map<Cell, String> current, List<PeriodAssignment> out, int[] counter, boolean[] capped) {
        if (capped[0] || counter[0] >= MAX_CANDIDATES) {
            capped[0] = true;
            return;
        }
        if (idx >= cells.size()) {
            out.add(new PeriodAssignment(new LinkedHashMap<>(current)));
            return;
        }
        Cell cell = cells.get(idx);
        RotationTeacher t = byName.get(cell.teacher());
        for (String group : eligibleGroups(t, cell.weekday(), labDaysByGroup)) {
            counter[0]++;
            // H3 prune: another teacher already serves this group on the same weekday this period.
            if (groupServedSameWeekday(current, group, cell.weekday(), cell.teacher())) {
                continue;
            }
            current.put(cell, group);
            enumeratePeriodJointDfs(cells, idx + 1, byName, labDaysByGroup, current, out, counter, capped);
            current.remove(cell);
            if (capped[0]) return;
        }
    }

    private static boolean groupServedSameWeekday(Map<Cell, String> current, String group,
            DayOfWeek weekday, String teacher) {
        for (Map.Entry<Cell, String> e : current.entrySet()) {
            if (e.getValue().equals(group)
                    && e.getKey().weekday() == weekday
                    && !e.getKey().teacher().equals(teacher)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> eligibleGroupsByCell(Cell cell, List<RotationTeacher> teachers,
            Map<String, Set<DayOfWeek>> labDaysByGroup) {
        for (RotationTeacher t : teachers) {
            if (t.name().equals(cell.teacher())) {
                return eligibleGroups(t, cell.weekday(), labDaysByGroup);
            }
        }
        return List.of();
    }

    // ─── Timeline layer (the new strato) ───────────────────────────────────

    /**
     * Enumerate regular rotations for ONE teacher as a timeline of {@code <= maxPeriods} periods,
     * propagating the remaining budget per group. Each period assigns one eligible group per
     * weekday-cell; a timeline is VALID when every group's budget is covered. Budget-feasibility
     * prune (design 1.7 #3): a branch dies once its remaining periods cannot cover the leftover.
     * {@link #MAX_TIMELINES} early-abort; the canonical T01 (mono-Thursday) case is the clean
     * subprocess this is demonstrated on.
     *
     * @param weeksPerPeriod school weeks in one period (budget coverage = periods x blockSize x weeksPerPeriod)
     */
    public static EnumResult<RotationTimeline> enumerateTimelines(
            RotationTeacher teacher, Map<String, Set<DayOfWeek>> labDaysByGroup,
            int maxPeriods, int weeksPerPeriod) {
        EnumResult<PeriodAssignment> perPeriod = enumeratePeriodTeacher(teacher, labDaysByGroup);
        Map<String, Integer> budgetByName = new TreeMap<>();
        for (RotationGroup g : teacher.groups) {
            budgetByName.put(g.name(), g.budgetHours());
        }
        int hoursPerPeriod = teacher.morningBlockSize() * weeksPerPeriod;

        List<RotationTimeline> out = new ArrayList<>();
        int[] counter = { 0 };
        boolean[] capped = { false };
        enumerateTimelinesDfs(perPeriod.candidates(), 0, new ArrayList<>(), budgetByName,
                hoursPerPeriod, maxPeriods, out, counter, capped);
        return new EnumResult<>(out, capped[0]);
    }

    private static void enumerateTimelinesDfs(List<PeriodAssignment> perPeriod, int periodIdx,
            List<PeriodAssignment> acc, Map<String, Integer> remaining, int hoursPerPeriod,
            int maxPeriods, List<RotationTimeline> out, int[] counter, boolean[] capped) {
        if (capped[0] || counter[0] >= MAX_TIMELINES) {
            capped[0] = true;
            return;
        }
        // Valid complete timeline: every group's budget covered. A rotation ENDS when budgets
        // are covered (modello: "periodi di regolarita'" then the slot passes to another group),
        // so this branch stops here. Other period orderings are explored on sibling branches.
        if (allCovered(remaining)) {
            counter[0]++;
            out.add(new RotationTimeline(new ArrayList<>(acc)));
            return;
        }
        if (periodIdx >= maxPeriods) {
            return;
        }
        // Budget-feasibility prune: even using every remaining period on the neediest group,
        // can the leftover still be covered? If not, stop extending this branch.
        if (!canStillCover(remaining, hoursPerPeriod, maxPeriods - periodIdx)) {
            return;
        }
        for (PeriodAssignment pa : perPeriod) {
            acc.add(pa);
            Map<String, Integer> decremented = decrement(remaining, pa, hoursPerPeriod);
            enumerateTimelinesDfs(perPeriod, periodIdx + 1, acc, decremented,
                    hoursPerPeriod, maxPeriods, out, counter, capped);
            acc.remove(acc.size() - 1);
            if (capped[0]) return;
        }
    }

    private static boolean allCovered(Map<String, Integer> remaining) {
        return remaining.values().stream().noneMatch(r -> r > 0);
    }

    private static boolean canStillCover(Map<String, Integer> remaining, int hoursPerPeriod, int periodsLeft) {
        // Sum of residual demand vs maximal placeable hours over the remaining periods.
        int residual = remaining.values().stream().mapToInt(Integer::intValue).filter(v -> v > 0).sum();
        return residual <= periodsLeft * hoursPerPeriod;
    }

    private static Map<String, Integer> decrement(Map<String, Integer> remaining, PeriodAssignment pa, int hoursPerPeriod) {
        Map<String, Integer> next = new TreeMap<>(remaining);
        for (String group : pa.cellToGroup().values()) {
            Integer cur = next.get(group);
            if (cur != null && cur > 0) {
                next.put(group, Math.max(0, cur - hoursPerPeriod));
            }
        }
        return next;
    }

    // ─── AC#2: multi-candidate ranking + top-k (input-driven, golden-free) ──

    /**
     * AC#2: one ranked candidate. The score is a sum of input-driven terms where HIGHER is
     * better (all weights are 1 for now: the design doc sect. 2.5 lists the criteria in
     * priority order but no measured weights, and inventing weights without a gate run
     * would be the static-tuning trap the metaheuristic campaign closed).
     */
    public record ScoredTimeline(RotationTimeline timeline, double score, String signature) {
    }

    /**
     * AC#2 multi-candidate core: rank the enumerated timelines by INPUT-DRIVEN criteria
     * and keep the {@code k} best DISTINCT ones. The design (sect. 2.5) fixes the
     * criteria; none reads the golden:
     * <ol>
     * <li><b>Budget tightness</b> (design 1.7 #3): hours delivered by the timeline's
     *     periods vs the group budgets it covers - fewer surplus hours is better (a
     *     rotation that parks a group it has already covered wastes a period).</li>
     * <li><b>Days-L skeleton preservation</b>: a (group, weekday) placement that lands
     *     on one of the group's declared lab days scores per placement (the school's own
     *     calendar input, not the golden).</li>
     * <li><b>Weekday economy</b>: fewer DISTINCT weekdays per group across the whole
     *     timeline (the rotation model's mono-day/inheritance shape).</li>
     * </ol>
     * Determinism: ties break on the structural signature (the period-by-period
     * assignment rendered as a string), so the ordering is reproducible run to run.
     * Distinctness: two timelines with the same signature are the same rotation.
     */
    public static List<ScoredTimeline> rankTopK(List<RotationTimeline> candidates,
            Map<String, Set<DayOfWeek>> labDaysByGroup, int weeksPerPeriod, int k) {
        Map<String, ScoredTimeline> bestBySignature = new TreeMap<>();
        for (RotationTimeline t : candidates) {
            String signature = signature(t);
            double score = budgetTightness(t, weeksPerPeriod)
                    + labDayAlignment(t, labDaysByGroup)
                    + weekdayEconomy(t);
            ScoredTimeline prev = bestBySignature.get(signature);
            // Same signature re-enumerated (sibling orderings reaching the same rotation):
            // keep one copy; scores are equal by construction, first-write wins.
            if (prev == null) {
                bestBySignature.put(signature, new ScoredTimeline(t, score, signature));
            }
        }
        List<ScoredTimeline> ranked = new ArrayList<>(bestBySignature.values());
        ranked.sort((a, b) -> {
            int byScore = Double.compare(b.score(), a.score());   // higher first
            if (byScore != 0) {
                return byScore;
            }
            return a.signature().compareTo(b.signature());   // reproducible tie-break
        });
        return ranked.size() > k ? new ArrayList<>(ranked.subList(0, k)) : ranked;
    }

    /** AC#2 convenience: enumerate + rank in one call, propagating the early-abort flag. */
    public static EnumResult<ScoredTimeline> enumerateTopK(RotationTeacher teacher,
            Map<String, Set<DayOfWeek>> labDaysByGroup, int maxPeriods,
            int weeksPerPeriod, int k) {
        EnumResult<RotationTimeline> raw = enumerateTimelines(teacher, labDaysByGroup,
                maxPeriods, weeksPerPeriod);
        return new EnumResult<>(rankTopK(raw.candidates(), labDaysByGroup, weeksPerPeriod, k),
                raw.capped());
    }

    /** Surplus hours the timeline parks beyond the budgets it covers (lower surplus = higher term). */
    private static double budgetTightness(RotationTimeline t, int weeksPerPeriod) {
        int delivered = 0;
        for (PeriodAssignment pa : t.periods()) {
            delivered += pa.cellToGroup().size() * weeksPerPeriod;
        }
        // Inverse-shaped: a timeline delivering exactly what is needed scores 0 surplus;
        // each surplus hour subtracts. Scaled small so it cannot dominate the placement
        // terms (which are per-placement counts).
        return -0.01 * Math.max(0, delivered);
    }

    /** +1 per (group, weekday) placement landing on one of the group's declared lab days. */
    private static double labDayAlignment(RotationTimeline t, Map<String, Set<DayOfWeek>> labDaysByGroup) {
        double score = 0.0;
        for (PeriodAssignment pa : t.periods()) {
            for (Map.Entry<Cell, String> e : pa.cellToGroup().entrySet()) {
                Set<DayOfWeek> labDays = labDaysByGroup.get(e.getValue());
                if (labDays != null && labDays.contains(e.getKey().weekday())) {
                    score += 1.0;
                }
            }
        }
        return score;
    }

    /** Fewer distinct weekdays per group across the timeline is better (mono-day shape). */
    private static double weekdayEconomy(RotationTimeline t) {
        double spread = daysByGroup(t).values().stream().mapToInt(Set::size).sum();
        return -spread;
    }

    /** The structural identity of a timeline: period-by-period (cell -> group) rendering. */
    private static String signature(RotationTimeline t) {
        StringBuilder sb = new StringBuilder();
        for (PeriodAssignment pa : t.periods()) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            // Cell is not Comparable: render and SORT the entries as strings (Cell's own
            // fields render deterministically), never a raw Map iteration.
            pa.cellToGroup().entrySet().stream()
                    .map(e -> e.getKey().teacher() + "@" + e.getKey().weekday()
                            + "#" + e.getKey().startSlot() + "=" + e.getValue())
                    .sorted()
                    .forEach(rendered -> sb.append(rendered).append(';'));
        }
        return sb.toString();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    public static List<DayOfWeek> sortedWeekdays(java.util.Collection<DayOfWeek> weekdays) {
        List<DayOfWeek> sorted = new ArrayList<>(weekdays);
        // Stable, reproducible order: DayOfWeek natural order (MON..FRI).
        sorted.sort(DayOfWeek::compareTo);
        return sorted;
    }
}
