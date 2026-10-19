package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.school.planner.TestDataBuilder;
import com.school.planner.domain.Course;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;

/**
 * TASK-542 iter-20 TDD: the day-compact fabricator's CORE contract, pure Java (no Timefold,
 * no DB), same style as WeeklyTemplateBuilderTest. The fabricator builds each (group,
 * weekday) as ONE CONTIGUOUS BLOCK from the group's first calendar-open slot, with a menu
 * that is a LIST OF BLOCKS (subject, hours) - golden-basin-analysis.md section 4.
 */
class DayCompactBuilderTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 14);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 15);
    private static final int SLOTS = 8;

    private long nextId = 1;

    /**
     * THE shape: a group whose Monday menu is [(Lav,4h),(Storia,2h)] over a 0-5 window
     * builds ONE contiguous run 0..5 (zero H17 holes, zero H31 late-start) with the blocks
     * in menu order; Tuesday's menu [(Matematica,3h)] builds 0..2.
     */
    @Test
    void buildsEachGroupWeekdayAsOneContiguousBlockFromFirstOpenSlot() {
        StudentGroup g = group("G04");
        Teacher T10 = teacher("T10");
        Teacher t02 = teacher("T02");
        Teacher t08 = teacher("T08");
        Course lav = course("Lavorazioni", T10, g, 4);
        Course storia = course("Storia", t02, g, 2);
        Course mate = course("Matematica", t08, g, 3);

        Map<StudentGroup, Map<DayOfWeek, List<DayCompactBuilder.MenuBlock>>> menu = new LinkedHashMap<>();
        menu.put(g, Map.of(
                DayOfWeek.MONDAY, List.of(new DayCompactBuilder.MenuBlock(lav, 4),
                        new DayCompactBuilder.MenuBlock(storia, 2)),
                DayOfWeek.TUESDAY, List.of(new DayCompactBuilder.MenuBlock(mate, 3))));

        // Monday window slots 0-5, Tuesday window 0-7
        Map<StudentGroup, Map<DayOfWeek, int[]>> windows = Map.of(g, Map.of(
                DayOfWeek.MONDAY, new int[] {0, 5},
                DayOfWeek.TUESDAY, new int[] {0, 7}));
        // weekly repetition: 3 Mondays, 3 Tuesdays
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            dates.add(MON.plusDays(7L * i));
            dates.add(TUE.plusDays(7L * i));
        }

        DayCompactBuilder.Result r = DayCompactBuilder.build(menu, windows, dates);

        assertEquals(3 * (4 + 2) + 3 * 3, r.placements().size(),
                "every menu hour is placed exactly once per date");
        for (var day : r.dayViews().entrySet()) {
            var daySlots = day.getValue().keySet();
            int first = day.getValue().firstKey(), last = day.getValue().lastKey();
            assertEquals(daySlots.size(), last - first + 1,
                    "day " + day.getKey() + " is ONE contiguous run (no H17 hole)");
            if (day.getKey().contains("MONDAY")) {
                assertEquals(0, first, "Monday starts at the first open slot (no H31)");
            }
        }
    }

    /**
     * A menu too big for the window: the overflowing block is DECLINED WHOLE, not squeezed
     * and not shipped truncated (F8, /simplify 23/08: a partial block is a non-golden shape).
     */
    @Test
    void declinesOverflowBlocksInsteadOfSqueezingThem() {
        StudentGroup g = group("G13");
        Course big = course("Lavorazioni", teacher("T10"), g, 8);
        Map<StudentGroup, Map<DayOfWeek, List<DayCompactBuilder.MenuBlock>>> menu = Map.of(
                g, Map.of(DayOfWeek.MONDAY, List.of(new DayCompactBuilder.MenuBlock(big, 8))));
        Map<StudentGroup, Map<DayOfWeek, int[]>> windows = Map.of(
                g, Map.of(DayOfWeek.MONDAY, new int[] {0, 5}));

        DayCompactBuilder.Result r = DayCompactBuilder.build(menu, windows, List.of(MON));

        assertEquals(0, r.placements().size(), "the block that does not fit is rolled back whole");
        assertEquals(0, r.holes(), "whatever is placed stays contiguous (no hole created)");
    }

    /**
     * F8 (/simplify 23/08): a block that fails MID-BLOCK (second block overruns the window)
     * must roll back its already-placed hours - the day keeps only the COMPLETE blocks and
     * stays one contiguous run. The old behavior shipped the truncated block silently.
     */
    @Test
    void rollsBackABlockThatCannotCompleteMidway() {
        StudentGroup g = group("G08");
        Course a = course("Lavorazioni", teacher("T10"), g, 3);
        Course b = course("Storia", teacher("T02"), g, 4);
        // window 0..4: block A (3h) completes at 0..2; block B (4h) would need 3..6 -> rolls back
        Map<StudentGroup, Map<DayOfWeek, List<DayCompactBuilder.MenuBlock>>> menu = Map.of(
                g, Map.of(DayOfWeek.MONDAY, List.of(new DayCompactBuilder.MenuBlock(a, 3),
                        new DayCompactBuilder.MenuBlock(b, 4))));
        Map<StudentGroup, Map<DayOfWeek, int[]>> windows = Map.of(
                g, Map.of(DayOfWeek.MONDAY, new int[] {0, 4}));

        DayCompactBuilder.Result r = DayCompactBuilder.build(menu, windows, List.of(MON));

        assertEquals(3, r.placements().size(), "only the complete block A is placed");
        var day = r.dayViews().values().iterator().next();
        assertEquals(0, (int) day.firstKey());
        assertEquals(2, (int) day.lastKey(), "the day ends where block A ended: no truncated tail");
        assertEquals(0, r.holes());
    }

    /** Stage dates are the PROVIDED case: the menu of the day becomes Stage x N. */
    @Test
    void stageDatesGetTheStageMenuAsTheProvidedCase() {
        StudentGroup g = group("G10");
        Course stage = course("Stage", teacher("Stage"), g, 6);
        Map<StudentGroup, Map<DayOfWeek, List<DayCompactBuilder.MenuBlock>>> menu = Map.of(
                g, Map.of(DayOfWeek.TUESDAY, List.of(new DayCompactBuilder.MenuBlock(stage, 6))));
        Map<StudentGroup, Map<DayOfWeek, int[]>> windows = Map.of(
                g, Map.of(DayOfWeek.TUESDAY, new int[] {0, 7}));

        DayCompactBuilder.Result r = DayCompactBuilder.build(menu, windows, List.of(TUE));

        assertEquals(6, r.placements().size());
        assertEquals(0, r.holes());
    }

    // ---- fixtures ----

    private StudentGroup group(String name) {
        return TestDataBuilder.group(nextId++, name, 20);
    }

    private Teacher teacher(String name) {
        return TestDataBuilder.teacher(nextId++, name);
    }

    private Course course(String subject, Teacher t, StudentGroup g, int weeklyHours) {
        return TestDataBuilder.course(nextId++, subject, t, g);
    }
}
