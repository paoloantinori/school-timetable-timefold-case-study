package com.school.planner.solver;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.StudentGroup;

/**
 * TASK-576 F7-A: the repair-side guard for the pair-cells construction form.
 *
 * <p>The night of 2026-09-01 measured the erosion chain end to end: the pair-cells
 * builder places 72.7% of a pair's lessons on the cells the school's golden
 * timetable practices, but the staged cycle delivers ~40% - the same level as the
 * course-based builder - because the deterministic repairs run POST-RELEASE, when
 * the promoted pins have already been handed back (HoleRepair's own javadoc: "an
 * ex-promoted lesson is by then an ordinary unpinned lesson"), and closing the
 * construction holes moves exactly those in-form lessons off their cells.</p>
 *
 * <p>This guard is the minimum intervention: the repairs that PRICE their donor
 * candidates (today {@link HoleRepair#findImport}) add an exit surcharge when the
 * donor currently sits on one of its pair's template cells. A preference, never a
 * veto: the surcharge (50k) dominates every realistic M20/M32 term (removal cost
 * caps at 4500, deviation weight at 500) so an in-cell donor is chosen only when
 * no other donor can close the hole - the hole still closes, but the pass first
 * spends the lessons that carry no form.</p>
 *
 * <p>Opt-in flag {@code planner.repair.pair-aware.enabled} (DEFAULT OFF, standard
 * form: only the literal "true" arms it). Structurally inert without the pair
 * builder: the cell set is published only when
 * {@code planner.construction.pair-cells.enabled} is on, so with the course-based
 * builder {@link #exitSurcharge} is 0 for every lesson and the repairs' selection
 * is byte-identical.</p>
 */
public final class PairCellGuard {

    /** Standard-form opt-in flag (DEFAULT OFF). Public for the wiring tests. */
    public static final String FLAG = "planner.repair.pair-aware.enabled";

    /**
     * The exit surcharge for a donor sitting on one of its pair's cells. Sized to
     * dominate the M20/M32 terms findImport already prices (see class javadoc) but
     * stay finite: a hole with NO other donor still closes through an in-cell lesson.
     */
    static final int EXIT_SURCHARGE = 50_000;

    /**
     * "teacherId|groupId|weekday|slotIndex" of every pair-template cell, published
     * pre-solve. PAIR-keyed, not group-keyed (code review 2026-09-01, MEDIUM): a cell
     * belongs to the (teacher, group) pair the template built it for, so a lesson of
     * pair A sitting on pair B's cell of the same group is OFF-form and must pay -
     * the intra-group swap is exactly the LS move class this guard exists for.
     */
    private static volatile Set<String> pairCellsStatic = Set.of();

    /**
     * "teacherId|groupId" -> set of "weekday|slotIndex" cell keys, for the F8
     * pair-move factory: given a pair, which cells are its form. Same publication
     * lifecycle as the flat sets above.
     */
    private static volatile Map<String, Set<String>> pairCellsByPairStatic = Map.of();

    private PairCellGuard() {
    }

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(FLAG));
    }

    /**
     * Publish the pair template's cells for the guard. Called by the construction
     * phase itself, on the EXACT template it is about to stamp (seed included, on
     * GRASP attempts) - the only publish point. Publishing from the pre-solve
     * statics point instead is WRONG: that point is re-run by the promotion
     * release on an all-placed population, and the CH-mode builder returns an
     * empty template there, wiping the guard one stage before the repairs (code
     * review 2026-09-01, CRITICAL). Cleared once per cycle by the staged runner.
     */
    public static void publish(Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> template) {
        if (template == null || template.isEmpty()) {
            pairCellsStatic = Set.of();
            pairCellsByPairStatic = Map.of();
            return;
        }
        Map<String, Boolean> cells = new HashMap<>();
        Map<String, Map<String, Boolean>> byPair = new HashMap<>();
        for (Map.Entry<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> byGroup : template.entrySet()) {
            StudentGroup group = byGroup.getKey();
            if (group == null || group.id == null) continue;
            for (Map.Entry<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>> byDay : byGroup.getValue().entrySet()) {
                List<WeeklyTemplateBuilder.TemplateCell> dayCells = byDay.getValue();
                if (dayCells == null) continue;
                for (WeeklyTemplateBuilder.TemplateCell cell : dayCells) {
                    if (cell == null) continue;
                    Long teacherId = cell.course() != null && cell.course().teacher != null
                            ? cell.course().teacher.id : null;
                    for (int slot = cell.slotIndex(); slot < cell.slotIndex() + cell.length(); slot++) {
                        cells.put(key(teacherId, group.id, byDay.getKey(), slot), Boolean.TRUE);
                    }
                    if (teacherId != null) {
                        // mech (laptop): 'slot' was out of scope here (the for above owns it);
                        // mark every slot of the cell, mirroring the cells map above.
                        String pairKey = teacherId + "|" + group.id;
                        // mech (laptop, post-simplify): the pair key set derives from
                        // byPair.keySet() below - the removed 'pairs' local is not needed.
                        for (int ps = cell.slotIndex(); ps < cell.slotIndex() + cell.length(); ps++) {
                            byPair.computeIfAbsent(pairKey, k -> new HashMap<>())
                                    .put(cellKey(byDay.getKey(), ps), Boolean.TRUE);
                        }
                    }
                }
            }
        }
        pairCellsStatic = Set.copyOf(cells.keySet());
        Map<String, Set<String>> frozen = new HashMap<>();
        byPair.forEach((k, v) -> frozen.put(k, Set.copyOf(v.keySet())));
        pairCellsByPairStatic = Map.copyOf(frozen);
    }

    /** Drop the published cells (staged cycle entry, and the not-armed construction). */
    public static void clear() {
        pairCellsStatic = Set.of();
        pairCellsByPairStatic = Map.of();
    }

    /** How many cells are currently published (observability: the staged banner). */
    public static int publishedCellCount() {
        return pairCellsStatic.size();
    }

    /** How many (teacher, group) pairs currently carry at least one cell. */
    public static int publishedPairCount() {
        return pairCellsByPairStatic.size();
    }

    /**
     * True when the lesson's (teacher, group) pair carries at least one template cell -
     * the self-gating population of the M38 pair-deviation constraint. A pair with no
     * cells anywhere (no stable form) is never charged for being "off-form".
     */
    public static boolean hasForm(Lesson lesson) {
        if (pairCellsByPairStatic.isEmpty() || lesson == null) return false;
        var teacher = lesson.getTeacher();
        StudentGroup group = lesson.getStudentGroup();
        if (teacher == null || teacher.id == null || group == null || group.id == null) return false;
        return pairCellsByPairStatic.containsKey(teacher.id + "|" + group.id);
    }

    /**
     * The F8 pair-move population gate: the ("WEDNESDAY"|"3") cell keys of the given
     * pair's form, empty when the pair carries no cells. Read by
     * {@code PairBlockShiftMoveIteratorFactory} to know where a block should land.
     */
    public static Set<String> formCellsOf(Lesson lesson) {
        if (pairCellsByPairStatic.isEmpty() || lesson == null) return Set.of();
        var teacher = lesson.getTeacher();
        StudentGroup group = lesson.getStudentGroup();
        if (teacher == null || teacher.id == null || group == null || group.id == null) return Set.of();
        return pairCellsByPairStatic.getOrDefault(teacher.id + "|" + group.id, Set.of());
    }

    /** "WEDNESDAY|3" - the per-pair form cell key format (F8 consumer). */
    static String cellKey(DayOfWeek weekday, int slotIndex) {
        return (weekday == null ? "?" : weekday.name()) + "|" + slotIndex;
    }

    /**
     * True when {@code lesson} currently sits on one of its OWN pair's template cells.
     * PAIR-keyed (see {@link #pairCellsStatic}): the teacher must match the pair the
     * cell was built for, not merely the group - a lesson of pair A on pair B's cell
     * of the same group is off-form.
     */
    public static boolean protects(Lesson lesson) {
        if (pairCellsStatic.isEmpty() || lesson == null || lesson.timeslot == null) return false;
        var teacher = lesson.getTeacher();
        StudentGroup group = lesson.getStudentGroup();
        if (teacher == null || teacher.id == null || group == null || group.id == null) return false;
        return pairCellsStatic.contains(key(teacher.id, group.id, lesson.timeslot.getDate().getDayOfWeek(),
                TimeslotSequence.slotIndexOf(lesson.timeslot)));
    }

    /**
     * The donor-pricing surcharge: full weight for an in-cell lesson, 0 otherwise.
     * The single integration point the repairs call.
     */
    public static int exitSurcharge(Lesson lesson) {
        return enabled() && protects(lesson) ? EXIT_SURCHARGE : 0;
    }

    private static String key(Long teacherId, Long groupId, DayOfWeek weekday, int slotIndex) {
        return teacherId + "|" + groupId + "|" + (weekday == null ? "?" : weekday.name()) + "|" + slotIndex;
    }
}
