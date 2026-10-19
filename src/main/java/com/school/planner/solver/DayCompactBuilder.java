package com.school.planner.solver;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.school.planner.domain.Course;
import com.school.planner.domain.StudentGroup;

/**
 * TASK-542 iter-20 (worktree day-compact-542, approved maintainer 07:59): the DAY-COMPACT
 * fabricator. The golden's compactness is CONSTRUCTED, not repaired (golden-basin-analysis.md:
 * 3 holes over 2213 group-days): each (group, weekday) is ONE contiguous block from the
 * group's first calendar-open slot, composed of a menu that is a LIST OF BLOCKS
 * (subject, hours) - the golden's own structure - with stage dates as the provided case
 * ("Stage x N" menu). Zero H17 holes and zero H31 late-start BY CONSTRUCTION: the block
 * can only be declined, never squeezed (the "stamp can only decline, never force" house
 * pattern).
 *
 * <p>Pure Java (no Timefold, no DB): the caller ({@code ScheduleService} construction stage,
 * flag-gated {@code planner.construction.day-compact.enabled}, DEFAULT OFF until maintainer's
 * ratification) feeds the golden-derived menu (same technique as the H-COPRESENCE depth
 * map) and consumes the placements. Guard: the placement-parity check TASK-517 runs on the
 * resulting plan BEFORE any adoption.
 *
 * <p>The per-day slot walk ({@link #stampDay}) is the SHARED core (F1, /simplify 23/08):
 * both this pure builder and the live graft in {@code DayClusteredConstructionPhase}
 * consume it, so the window semantics and the decline/rollback behavior cannot drift
 * between the two.
 */
public final class DayCompactBuilder {

    private DayCompactBuilder() {
    }

    /** One menu entry: a (course, hours) block. The menu is a list of these, in day order. */
    public record MenuBlock(Course course, int hours) {
    }

    /** One placed hour: the course, the date, the slot index. */
    public record Placement(Course course, LocalDate date, int slot) {
    }

    /**
     * The build result. {@code dayViews} is the (group|weekday|date) -> (slot -> course) view
     * the tests (and the profile extractor) read; {@code holes} counts internal gaps of every
     * day view (contract: 0 unless the caller passes overlapping menus).
     */
    public record Result(List<Placement> placements,
                         Map<String, TreeMap<Integer, Course>> dayViews,
                         int holes) {
    }

    /**
     * Places ONE cell of a block. Returns an opaque undo handle on success, {@code null} when
     * the cell cannot be placed (window edge, VRP, exhausted queue): the walk then rolls the
     * whole block back via {@link #undo} on every handle it already accepted.
     */
    public interface CellPlacer {
        Object place(Course course, int slot);

        void undo(Object handle);
    }

    /**
     * F8 (/simplify 23/08): the outcome of one (group, weekday, date) walk. A block that
     * fails partway is rolled back WHOLE and counted in {@code truncatedBlocks} - a silently
     * truncated block is a non-golden shape, never an acceptable partial placement.
     */
    public record DayWalk(int placedCells, int declinedHours, int truncatedBlocks) {
    }

    /**
     * The shared per-day walk: places the menu's blocks contiguously starting at
     * {@code minSlot}. When a block cannot complete (window exhausted mid-block, a cell
     * declined by the placer, or the course queue running dry), the block's already-placed
     * cells are UNDONE and the whole block is declined - the next block is still attempted
     * from the slot where this one started.
     */
    public static DayWalk stampDay(List<MenuBlock> dayMenu, int minSlot, int maxSlotInclusive,
            CellPlacer placer) {
        int placed = 0;
        int declined = 0;
        int truncated = 0;
        int slot = minSlot;
        for (MenuBlock block : dayMenu) {
            List<Object> handles = new ArrayList<>(block.hours());
            boolean complete = true;
            for (int h = 0; h < block.hours(); h++) {
                if (slot > maxSlotInclusive) {
                    complete = false; // window exhausted: decline, never squeeze
                    break;
                }
                Object handle = placer.place(block.course(), slot);
                if (handle == null) {
                    complete = false; // placer declined (VRP / queue dry)
                    break;
                }
                handles.add(handle);
                slot++;
            }
            if (complete) {
                placed += handles.size();
            } else {
                for (int i = handles.size() - 1; i >= 0; i--) {
                    placer.undo(handles.get(i));
                }
                declined += block.hours();
                if (!handles.isEmpty()) {
                    truncated++;
                }
            }
        }
        return new DayWalk(placed, declined, truncated);
    }

    public static Result build(Map<StudentGroup, Map<DayOfWeek, List<MenuBlock>>> menu,
                               Map<StudentGroup, Map<DayOfWeek, int[]>> windows,
                               List<LocalDate> dates) {
        List<Placement> placements = new ArrayList<>();
        Map<String, TreeMap<Integer, Course>> dayViews = new LinkedHashMap<>();
        int holes = 0;
        for (StudentGroup group : sortedKeys(menu)) {
            Map<DayOfWeek, List<MenuBlock>> byWeekday = menu.get(group);
            for (LocalDate date : dates) {
                DayOfWeek wd = date.getDayOfWeek();
                List<MenuBlock> dayMenu = byWeekday.get(wd);
                if (dayMenu == null || dayMenu.isEmpty()) {
                    continue; // this group has no menu for this weekday: nothing to build
                }
                int[] window = windows.getOrDefault(group, Map.of()).get(wd);
                if (window == null || window[1] < window[0]) {
                    continue; // calendar closed: the day is declined, never forced
                }
                // ONE contiguous run from the first open slot: the menu blocks in order
                TreeMap<Integer, Course> day = new TreeMap<>();
                final LocalDate dayDate = date;
                DayWalk walk = stampDay(dayMenu, window[0], window[1], new CellPlacer() {
                    @Override
                    public Object place(Course course, int slot) {
                        day.put(slot, course);
                        placements.add(new Placement(course, dayDate, slot));
                        return placements.size() - 1; // index of the placement to undo
                    }

                    @Override
                    public void undo(Object handle) {
                        int idx = (Integer) handle;
                        Placement p = placements.remove(idx);
                        day.remove(p.slot());
                    }
                });
                if (walk.placedCells() > 0) {
                    dayViews.put(group.name + "|" + wd + "|" + date, day);
                }
                // contract: the run is contiguous by construction; count defensively
                if (!day.isEmpty()) {
                    int first = day.firstKey(), last = day.lastKey();
                    if (day.size() != last - first + 1) {
                        holes++;
                    }
                }
            }
        }
        return new Result(placements, dayViews, holes);
    }

    private static List<StudentGroup> sortedKeys(Map<StudentGroup, ?> menu) {
        List<StudentGroup> keys = new ArrayList<>(menu.keySet());
        keys.sort(Comparator.comparing(g -> g.name == null ? "" : g.name));
        return keys;
    }
}
