package com.school.planner.solver;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.school.planner.domain.Course;
import com.school.planner.domain.StudentGroup;

/**
 * TASK-576 F2 (prassi referente → euristica di costruzione): the PAIR-CELLS template
 * builder. The human planner's unit of planning is the (teacher, group) PAIR, not
 * the individual course: the pair's (weekday, slot) cells are decided once, then
 * the subjects fill them with SEQUENTIAL EXHAUSTION (all hours of course A, then
 * course B starts in the same cells). The golden shows this on ~25% of pairs
 * (the lab teachers: T16 1 switch in 179h, T10/G13 cells 100% stable).
 *
 * <p>Different from {@link WeeklyTemplateBuilder}, which places each course
 * independently into a group schedule (result: the same teacher teaching two
 * subjects to the same group ends up in DIFFERENT cells). This builder:
 * <ol>
 * <li>groups the courses by (teacher, group) pair</li>
 * <li>for each pair, allocates cells ONCE (respecting teacher/group availability
 *     and the pair's golden form when available)</li>
 * <li>within the pair's cells, sequences the courses with exhaustion (first course
 *     fills all its hours, then the next starts in the same cells)</li>
 * <li>emits the same output shape: {@code Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>>}</li>
 * </ol>
 *
 * <p>Pure Java, no Timefold coupling (unit-testable like the sibling). The
 * {@code goldenPairCells} input is OPTIONAL (from F1's golden-pair-cells.csv):
 * when present, the builder PREFERS the golden's cell form for that pair;
 * when absent, it falls back to the same greedy search the current builder uses.
 *
 * <p>PROTOTYPE: not wired into the staged cycle. The bench (F3) compares this
 * builder's output against the current one using pair-stability-kpi.py.
 */
public final class PairCellsTemplateBuilder {

    private PairCellsTemplateBuilder() {
    }

    /**
     * A pair's golden form: the cells it practices + whether it sequences subjects.
     * The cell keys follow the builder's keying (day.getValue()*100 + slotIndex) and the
     * set's ITERATION ORDER matters: the builder takes the first free cells up to the
     * pair's weekly demand, so pass a day-then-slot ordered set (e.g. TreeSet).
     */
    public record PairForm(Set<Integer> cellKeys) {
    }

    /** The pair identity: (teacher id, group). Courses of the same pair share cells. */
    public record PairKey(Long teacherId, StudentGroup group) {
    }

    /**
     * Loads the golden pair forms from the DECLARED seed CSV (TASK-576 F1:
     * {@code seed/golden-pair-cells.csv}, columns
     * {@code teacher,group,hours,weekday_conc,subjects,switches,cells,sequence}).
     * Cell tokens like {@code G1} decode to letter-day (L,M,T=Wed,G,V) + 1-based slot,
     * keyed the way the builder keys cells (day*100 + slotIndex, in a TreeSet so the
     * day-then-slot iteration order holds). Pairs whose teacher/group name resolves
     * to no course entity are skipped (the CSV carries names, the PairKey carries ids).
     * Called by the F4 wiring and by the bench; reads the DECLARED CSV, never the
     * golden solution at runtime (the adoption vehicle is the workbook, TASK-452 #8).
     */
    public static Map<PairKey, PairForm> loadGoldenPairForms(List<Course> courses) {
        Map<String, Long> teacherIdByName = new HashMap<>();
        Map<String, StudentGroup> groupByName = new HashMap<>();
        for (Course c : courses) {
            if (c.teacher != null && c.studentGroup != null) {
                teacherIdByName.putIfAbsent(c.teacher.name, c.teacher.id);
                groupByName.putIfAbsent(c.studentGroup.name, c.studentGroup);
            }
        }
        Map<PairKey, PairForm> forms = new HashMap<>();
        for (String[] row : com.school.planner.util.SeedCsv.load("golden-pair-cells.csv", 8)) {
            Long teacherId = teacherIdByName.get(row[0].trim());
            StudentGroup group = groupByName.get(row[1].trim());
            if (teacherId == null || group == null) continue;
            Set<Integer> cellKeys = new TreeSet<>(); // ordered day-then-slot
            for (String cell : row[6].split(";")) {
                String token = cell.trim();
                if (token.length() < 2) continue;
                int dayVal;
                switch (token.charAt(0)) {
                    case 'L' -> dayVal = 1;
                    case 'M' -> dayVal = 2;
                    case 'T' -> dayVal = 3; // terzo giorno: mercoledi'
                    case 'G' -> dayVal = 4;
                    case 'V' -> dayVal = 5;
                    default -> dayVal = 0;
                }
                if (dayVal == 0) continue;
                int slotIndex = Integer.parseInt(token.substring(1)) - 1; // CSV 1-based, builder 0-based
                if (slotIndex < 0 || slotIndex > 7) continue;
                cellKeys.add(dayVal * 100 + slotIndex);
            }
            if (cellKeys.isEmpty()) continue;
            // (the CSV's subjects/switches columns stay unconsumed here: the sequential
            // threshold they fed was write-only state, dropped in the 2026-09-01 simplify
            // pass; the KPI keeps using them for its own full-practice classification)
            forms.put(new PairKey(teacherId, group), new PairForm(cellKeys));
        }
        return forms;
    }

    /**
     * Builds a weekly template organised by PAIR (teacher, group).
     *
     * <p>Unit discipline (F3-v0 lesson): {@code totalHours} is ANNUAL; the weekly
     * demand of a course is {@code totalHours / activeWeeks}. The v0 prototype
     * treated annual hours as weekly (cap 40) and asked each pair for up to 20
     * cells, which the greedy necessarily scattered over 4-5 weekdays: the measured
     * 55.3% concentration was an artifact of that unit error, not a verdict on the
     * pair-cells concept. This v1 asks for the pair's true weekly hours.
     *
     * @param courses all courses to place
     * @param goldenPairCells optional golden form per pair (from F1); null entries fall back to greedy
     * @param unavailableTeacherSlots (teacherId, day, slot) cells the teacher cannot use
     * @param teachingTeacherSlots existing teaching commitments
     * @param blockedGroupSlots (groupId, day, slot) cells the group cannot use
     * @param maxHoursPerDay per-group daily cap
     * @param teacherDailyCap per-teacher daily cap
     * @param activeWeeks the school year's active week count (annual-to-weekly divisor)
     * @return the same output shape as WeeklyTemplateBuilder.build()
     */
    public static Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> build(
            List<Course> courses,
            Map<PairKey, PairForm> goldenPairCells,
            Set<WeeklyTemplateBuilder.TeacherSlot> unavailableTeacherSlots,
            Set<WeeklyTemplateBuilder.TeacherSlot> teachingTeacherSlots,
            Set<WeeklyTemplateBuilder.GroupSlot> blockedGroupSlots,
            Map<StudentGroup, Integer> maxHoursPerDay,
            Map<Long, Integer> teacherDailyCap,
            int activeWeeks) {

        // ── Phase 1: group courses by pair ────────────────────────────────────
        Map<PairKey, List<Course>> coursesByPair = new HashMap<>();
        for (Course c : courses) {
            if (c == null || c.teacher == null || c.studentGroup == null) continue;
            PairKey key = new PairKey(c.teacher.id, c.studentGroup);
            coursesByPair.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }

        // ── Phase 2: allocate cells per pair ─────────────────────────────────
        // Live occupancy shared across all pairs (same maps the current builder uses)
        Map<Long, TreeSet<Integer>> teacherOccupied = new HashMap<>();
        Map<Long, TreeSet<Integer>> groupOccupied = new HashMap<>();
        for (var ts : unavailableTeacherSlots) {
            teacherOccupied.computeIfAbsent(ts.teacherId(), k -> new TreeSet<>())
                    .add(slotKey(ts.day(), ts.slot()));
        }
        for (var ts : teachingTeacherSlots) {
            teacherOccupied.computeIfAbsent(ts.teacherId(), k -> new TreeSet<>())
                    .add(slotKey(ts.day(), ts.slot()));
        }
        for (var gs : blockedGroupSlots) {
            groupOccupied.computeIfAbsent(gs.groupId(), k -> new TreeSet<>())
                    .add(slotKey(gs.day(), gs.slot()));
        }

        // the pair-cell allocation: (pairKey -> ordered list of (day, slot) cells)
        Map<PairKey, List<CellRef>> pairCells = new HashMap<>();

        // order pairs by total hours descending (biggest first = hardest to place)
        List<Map.Entry<PairKey, List<Course>>> pairsBySize = coursesByPair.entrySet().stream()
                .sorted((a, b) -> Integer.compare(
                        b.getValue().stream().mapToInt(c -> c.totalHours).sum(),
                        a.getValue().stream().mapToInt(c -> c.totalHours).sum()))
                .toList();

        for (var entry : pairsBySize) {
            PairKey pair = entry.getKey();
            List<Course> pairCourses = entry.getValue();
            int weeklyHours = 0;
            for (Course c : pairCourses) {
                weeklyHours += weeklyHoursOf(c, activeWeeks);
            }
            // HOUR-CELLS (F4 fix): a cell is ONE hour and the pair asks for exactly its
            // weekly hours. The earlier ceil(weekly/2) presumed 2-hour cells but every
            // cell carried capacity=1, so phase 3 silently DROPPED the leftover hours
            // (invisible in the bench, lethal in a real cycle). 2h blocks now EMERGE
            // from TreeSet adjacency: consecutive hours of the same course land in
            // same-day consecutive slots because exhaustion walks day-then-slot order.
            int cellsNeeded = weeklyHours;

            // try golden form first
            PairForm golden = goldenPairCells != null ? goldenPairCells.get(pair) : null;
            List<CellRef> cells = new ArrayList<>();
            if (golden != null && golden.cellKeys() != null && !golden.cellKeys().isEmpty()) {
                List<CellRef> goldenCells = tryGoldenCells(golden, pair, teacherOccupied, groupOccupied,
                        cellsNeeded, maxHoursPerDay, teacherDailyCap);
                if (goldenCells != null) {
                    cells.addAll(goldenCells);
                    // commit the golden cells NOW so the completion greedy can neither
                    // re-take nor double-book them
                    for (CellRef cell : goldenCells) {
                        occupy(teacherOccupied, pair.teacherId(), cell);
                        occupy(groupOccupied, pair.group().id, cell);
                    }
                }
            }

            // completion: the greedy covers ONLY the weekly demand the golden form did
            // not (no form, rejected form, or partial form: the prassi keeps its cells,
            // the remainder falls back to the same first-fit search the current builder
            // uses). With hour-cells a golden form is almost always partial, so a
            // replace-on-partial rule here would discard the prassi nearly every time.
            if (cells.size() < cellsNeeded) {
                cells.addAll(greedyCellSearch(pair, teacherOccupied, groupOccupied,
                        cellsNeeded - cells.size(), maxHoursPerDay, teacherDailyCap));
            }

            if (!cells.isEmpty()) {
                pairCells.put(pair, cells);
                // commit occupancy
                for (CellRef cell : cells) {
                    occupy(teacherOccupied, pair.teacherId(), cell);
                    occupy(groupOccupied, pair.group().id, cell);
                }
            }
        }

        // ── Phase 3: sequence courses within each pair's cells (exhaustion) ──
        Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> result = new HashMap<>();
        for (var entry : pairCells.entrySet()) {
            PairKey pair = entry.getKey();
            List<CellRef> cells = entry.getValue();
            List<Course> pairCourses = coursesByPair.get(pair);

            // sort courses by WEEKLY hours descending: the biggest fills first (exhaustion)
            pairCourses.sort(Comparator.comparingInt((Course c) -> weeklyHoursOf(c, activeWeeks)).reversed());

            int cellIdx = 0;
            for (Course course : pairCourses) {
                int remaining = weeklyHoursOf(course, activeWeeks);
                while (remaining > 0 && cellIdx < cells.size()) {
                    CellRef cell = cells.get(cellIdx);
                    // the length in this cell: min(remaining weekly hours, cell capacity)
                    int len = Math.min(remaining, Math.max(1, cell.capacity));
                    var tc = new WeeklyTemplateBuilder.TemplateCell(course, cell.slot, len);
                    result.computeIfAbsent(pair.group(), g -> new HashMap<>())
                            .computeIfAbsent(cell.day, d -> new ArrayList<>())
                            .add(tc);
                    remaining -= len;
                    cellIdx++;
                }
                // if we ran out of cells the course is left out of the weekly template:
                // the annual exhaustion lives in the caller's chronological stamping,
                // but a pair whose weekly demand exceeds its cells is a signal to
                // revisit the allocation, not something to hide
            }
        }

        return result;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private record CellRef(DayOfWeek day, int slot, int capacity) {
    }

    private static int slotKey(DayOfWeek day, int slot) {
        return day.getValue() * 100 + slot;
    }

    /** The course's weekly demand: annual hours spread over the active weeks (floor 1). */
    private static int weeklyHoursOf(Course course, int activeWeeks) {
        return Math.max(1, Math.round((float) course.totalHours / activeWeeks));
    }

    private static void occupy(Map<Long, TreeSet<Integer>> map, Long id, CellRef cell) {
        map.computeIfAbsent(id, k -> new TreeSet<>()).add(slotKey(cell.day, cell.slot));
    }

    private static boolean isFree(Map<Long, TreeSet<Integer>> map, Long id, CellRef cell) {
        var occupied = map.get(id);
        return occupied == null || !occupied.contains(slotKey(cell.day, cell.slot));
    }

    /** Try the golden's cells: filter for availability, return null if too few survive. */
    private static List<CellRef> tryGoldenCells(PairForm golden, PairKey pair,
            Map<Long, TreeSet<Integer>> teacherOcc, Map<Long, TreeSet<Integer>> groupOcc,
            int needed, Map<StudentGroup, Integer> maxHoursPerDay,
            Map<Long, Integer> teacherDailyCap) {
        List<CellRef> cells = new ArrayList<>();
        Map<DayOfWeek, Integer> dayCount = new HashMap<>();
        for (Integer key : golden.cellKeys()) {
            if (cells.size() >= needed) break; // the weekly demand only: extra practiced cells stay free for other pairs
            int dayVal = key / 100;
            int slot = key % 100;
            DayOfWeek day = DayOfWeek.of(dayVal);
            CellRef cell = new CellRef(day, slot, 1);
            if (!isFree(teacherOcc, pair.teacherId(), cell)) continue;
            if (!isFree(groupOcc, pair.group().id, cell)) continue;
            int groupCap = maxHoursPerDay.getOrDefault(pair.group(), 8);
            int teacherCap = teacherDailyCap.getOrDefault(pair.teacherId(), 6);
            if (dayCount.getOrDefault(day, 0) + 1 > Math.min(groupCap, teacherCap)) continue;
            dayCount.merge(day, 1, Integer::sum);
            cells.add(cell);
        }
        return cells.size() >= Math.min(needed, 3) ? cells : null;
    }

    /** Greedy cell search: same shape as the current builder (first-fit). */
    private static List<CellRef> greedyCellSearch(PairKey pair,
            Map<Long, TreeSet<Integer>> teacherOcc, Map<Long, TreeSet<Integer>> groupOcc,
            int needed, Map<StudentGroup, Integer> maxHoursPerDay,
            Map<Long, Integer> teacherDailyCap) {
        List<CellRef> cells = new ArrayList<>();
        Map<DayOfWeek, Integer> dayCount = new HashMap<>();
        int groupCap = maxHoursPerDay.getOrDefault(pair.group(), 8);
        int teacherCap = teacherDailyCap.getOrDefault(pair.teacherId(), 6);
        int dailyCap = Math.min(groupCap, teacherCap);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) continue;
            for (int slot = 0; slot < 8; slot++) {
                if (cells.size() >= needed) break;
                CellRef cell = new CellRef(day, slot, 1);
                if (!isFree(teacherOcc, pair.teacherId(), cell)) continue;
                if (!isFree(groupOcc, pair.group().id, cell)) continue;
                if (dayCount.getOrDefault(day, 0) + 1 > dailyCap) continue;
                dayCount.merge(day, 1, Integer::sum);
                cells.add(cell);
            }
            if (cells.size() >= needed) break;
        }
        return cells;
    }
}
