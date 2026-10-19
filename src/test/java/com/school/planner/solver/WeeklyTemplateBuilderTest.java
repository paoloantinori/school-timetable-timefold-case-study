package com.school.planner.solver;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.solver.WeeklyTemplateBuilder.GroupSlot;
import com.school.planner.solver.WeeklyTemplateBuilder.GroupWeeklyShape;
import com.school.planner.solver.WeeklyTemplateBuilder.TeacherSlot;
import com.school.planner.solver.WeeklyTemplateBuilder.TemplateCell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TASK-381 "L2" {@link WeeklyTemplateBuilder} — pure Java, no
 * Timefold/Quarkus context needed (see the class's own javadoc for why).
 * Covers the invariants the task brief calls out explicitly: cross-group
 * teacher consistency, per-day compactness, 2-cell adjacency for
 * &gt;=2h/week courses, and capacity (calendar window + H11 + quota)
 * respected. The construction-end recurrence % itself is verified
 * end-to-end against the real seed by
 * {@code WeeklyTemplateConstructionRealSeedTest} (bootstrap package).
 */
class WeeklyTemplateBuilderTest {

    private static long nextId = 1;

    private static StudentGroup group(String name) {
        StudentGroup g = new StudentGroup();
        g.id = nextId++;
        g.name = name;
        g.size = 20;
        return g;
    }

    private static Teacher teacher(String name) {
        Teacher t = new Teacher();
        t.id = nextId++;
        t.name = name;
        return t;
    }

    private static Course course(String subject, Teacher teacher, StudentGroup group, int totalHours) {
        Course c = new Course(subject, teacher, group, totalHours);
        c.id = nextId++;
        return c;
    }

    /** Full Mon-Fri, 08:00-16:30 (slots 0-7), no quota restriction. */
    private static GroupWeeklyShape fullWeekShape(StudentGroup group) {
        GroupWeeklyShape shape = new GroupWeeklyShape(group, false);
        for (DayOfWeek day : WeeklyTemplateBuilder.WEEKDAYS) {
            shape.setWindow(day, 0, 7);
        }
        return shape;
    }

    @Test
    void crossGroupTeacherConsistency_sameTeacherNeverDoubleBookedAcrossGroups() {
        StudentGroup g1 = group("G03");
        StudentGroup g2 = group("G07");
        Teacher shared = teacher("Shared");
        Course c1 = course("Matematica", shared, g1, 4);
        Course c2 = course("Scienze", shared, g2, 4);

        Map<Course, Integer> targets = new HashMap<>();
        targets.put(c1, 4);
        targets.put(c2, 4);

        List<GroupWeeklyShape> shapes = List.of(fullWeekShape(g1), fullWeekShape(g2));
        Map<StudentGroup, Integer> maxHoursPerDay = new HashMap<>();

        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template = WeeklyTemplateBuilder.build(
                shapes, targets, Set.of(), Set.of(), Set.of(), maxHoursPerDay, Map.of());

        // Collect every (weekday, slot) the shared teacher is used across BOTH groups.
        Set<String> usedCells = new HashSet<>();
        for (Map.Entry<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> groupEntry : template.entrySet()) {
            for (Map.Entry<DayOfWeek, List<TemplateCell>> dayEntry : groupEntry.getValue().entrySet()) {
                for (TemplateCell cell : dayEntry.getValue()) {
                    for (int s = cell.slotIndex(); s < cell.slotIndex() + cell.length(); s++) {
                        String key = dayEntry.getKey() + ":" + s;
                        assertTrue(usedCells.add(key),
                                "teacher cell " + key + " used twice across groups " + groupEntry.getKey().name);
                    }
                }
            }
        }
        assertEquals(8, usedCells.size(), "both courses' 4h/week should be fully placed with zero overlap");
    }

    @Test
    void compactness_dayCellsAreContiguousFromWindowStart() {
        StudentGroup g = group("G09");
        Teacher t1 = teacher("T1");
        Teacher t2 = teacher("T2");
        Course c1 = course("Storia", t1, g, 3);
        Course c2 = course("Arte", t2, g, 3);

        Map<Course, Integer> targets = Map.of(c1, 3, c2, 3);
        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template = WeeklyTemplateBuilder.build(
                List.of(fullWeekShape(g)), targets, Set.of(), Set.of(), Set.of(), new HashMap<>(), Map.of());

        for (Map.Entry<DayOfWeek, List<TemplateCell>> dayEntry : template.get(g).entrySet()) {
            List<TemplateCell> cells = dayEntry.getValue();
            int expectedNext = cells.stream().mapToInt(TemplateCell::slotIndex).min().orElse(0);
            // Sort by slotIndex and assert every cell starts exactly where the previous one ended.
            cells = cells.stream().sorted((a, b) -> Integer.compare(a.slotIndex(), b.slotIndex())).toList();
            for (TemplateCell cell : cells) {
                assertEquals(expectedNext, cell.slotIndex(),
                        "gap detected on " + dayEntry.getKey() + ": expected next slot " + expectedNext);
                expectedNext = cell.slotIndex() + cell.length();
            }
        }
    }

    @Test
    void adjacency_twoHourCourseGetsAContiguousBlockWhenCapacityAllows() {
        StudentGroup g = group("G06");
        Teacher t = teacher("T");
        Course c = course("Informatica", t, g, 2);

        Map<Course, Integer> targets = Map.of(c, 2);
        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template = WeeklyTemplateBuilder.build(
                List.of(fullWeekShape(g)), targets, Set.of(), Set.of(), Set.of(), new HashMap<>(), Map.of());

        boolean foundTwoHourBlock = template.get(g).values().stream()
                .flatMap(List::stream)
                .anyMatch(cell -> cell.course().equals(c) && cell.length() == 2);
        assertTrue(foundTwoHourBlock, "a 2h/week course with full capacity available should get one contiguous 2h block");
    }

    @Test
    void capacityRespected_neverExceedsGroupMaxHoursPerDay() {
        StudentGroup g = group("G14");
        Teacher t1 = teacher("T1");
        Teacher t2 = teacher("T2");
        Teacher t3 = teacher("T3");
        Course c1 = course("A", t1, g, 5);
        Course c2 = course("B", t2, g, 5);
        Course c3 = course("C", t3, g, 5);

        Map<Course, Integer> targets = Map.of(c1, 5, c2, 5, c3, 5);
        Map<StudentGroup, Integer> maxHoursPerDay = new HashMap<>();
        maxHoursPerDay.put(g, 3);

        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template = WeeklyTemplateBuilder.build(
                List.of(fullWeekShape(g)), targets, Set.of(), Set.of(), Set.of(), maxHoursPerDay, Map.of());

        for (Map.Entry<DayOfWeek, List<TemplateCell>> dayEntry : template.get(g).entrySet()) {
            int totalHours = dayEntry.getValue().stream().mapToInt(TemplateCell::length).sum();
            assertTrue(totalHours <= 3, dayEntry.getKey() + " has " + totalHours + "h, exceeds maxHoursPerDay=3");
        }
    }

    @Test
    void quotaGating_labBoundCourseOnlyLandsOnLabDays() {
        StudentGroup g = group("G04");
        Teacher t = teacher("T15");
        Course labCourse = course("Lavorazioni meccaniche", t, g, 4);

        GroupWeeklyShape shape = new GroupWeeklyShape(g, true);
        // Monday/Wednesday are lab days (labCap>0, aulaCap=0); Tuesday/Thursday/Friday are aula-only.
        shape.setWindow(DayOfWeek.MONDAY, 0, 5);
        shape.setQuotaCaps(DayOfWeek.MONDAY, 4, 0);
        shape.setWindow(DayOfWeek.WEDNESDAY, 0, 5);
        shape.setQuotaCaps(DayOfWeek.WEDNESDAY, 4, 0);
        shape.setWindow(DayOfWeek.TUESDAY, 0, 5);
        shape.setQuotaCaps(DayOfWeek.TUESDAY, 0, 6);
        shape.setWindow(DayOfWeek.THURSDAY, 0, 5);
        shape.setQuotaCaps(DayOfWeek.THURSDAY, 0, 6);
        shape.setWindow(DayOfWeek.FRIDAY, 0, 5);
        shape.setQuotaCaps(DayOfWeek.FRIDAY, 0, 6);

        Map<Course, Integer> targets = Map.of(labCourse, 4);
        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template = WeeklyTemplateBuilder.build(
                List.of(shape), targets, Set.of(), Set.of(), Set.of(), new HashMap<>(), Map.of());

        Map<DayOfWeek, List<TemplateCell>> byDay = template.getOrDefault(g, Map.of());
        assertTrue(byDay.getOrDefault(DayOfWeek.TUESDAY, List.of()).isEmpty(), "quota-bound lab subject must not land on an aula-only day");
        assertTrue(byDay.getOrDefault(DayOfWeek.THURSDAY, List.of()).isEmpty(), "quota-bound lab subject must not land on an aula-only day");
        int labDayHours = byDay.getOrDefault(DayOfWeek.MONDAY, List.of()).stream().mapToInt(TemplateCell::length).sum()
                + byDay.getOrDefault(DayOfWeek.WEDNESDAY, List.of()).stream().mapToInt(TemplateCell::length).sum();
        assertEquals(4, labDayHours, "all 4h should land on the two lab days");
    }

    @Test
    void blockedTeacherSlot_isNeverUsedByTheTemplate() {
        StudentGroup g = group("G10");
        Teacher t28 = teacher("T28");
        Course c = course("Acconciatura", t28, g, 2);

        // T28 is blocked Mon/Tue/Wed/Fri (external, Thursday-only) — every slot except Thursday.
        Set<TeacherSlot> blocked = new HashSet<>();
        for (DayOfWeek day : WeeklyTemplateBuilder.WEEKDAYS) {
            if (day == DayOfWeek.THURSDAY) continue;
            for (int slot = 0; slot < 8; slot++) {
                blocked.add(new TeacherSlot(t28.id, day, slot));
            }
        }

        Map<Course, Integer> targets = Map.of(c, 2);
        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template = WeeklyTemplateBuilder.build(
                List.of(fullWeekShape(g)), targets, blocked, Set.of(), Set.of(), new HashMap<>(), Map.of());

        Map<DayOfWeek, List<TemplateCell>> byDay = template.getOrDefault(g, Map.of());
        for (DayOfWeek day : WeeklyTemplateBuilder.WEEKDAYS) {
            if (day == DayOfWeek.THURSDAY) continue;
            assertFalse(byDay.containsKey(day) && !byDay.get(day).isEmpty(),
                    "T28 must never be templated on a blocked day: " + day);
        }
        int thursdayHours = byDay.getOrDefault(DayOfWeek.THURSDAY, List.of()).stream().mapToInt(TemplateCell::length).sum();
        assertEquals(2, thursdayHours, "both hours must land on T28's only open day");
    }

    @Test
    void capacityRespected_neverExceedsTeacherMaxDailyHours() {
        // Two groups sharing one teacher, each wanting 4h/week — with an H7
        // cap of 3h/day the teacher must never be templated for more than 3h
        // on any single weekday, even split across groups.
        StudentGroup g1 = group("G01");
        StudentGroup g2 = group("G05");
        Teacher t = teacher("Overloaded");
        Course c1 = course("Storia della moda", t, g1, 4);
        Course c2 = course("Merceologia", t, g2, 4);

        Map<Course, Integer> targets = Map.of(c1, 4, c2, 4);
        Map<Long, Integer> teacherDailyCap = Map.of(t.id, 3);

        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template = WeeklyTemplateBuilder.build(
                List.of(fullWeekShape(g1), fullWeekShape(g2)), targets, Set.of(), Set.of(), Set.of(), new HashMap<>(),
                teacherDailyCap);

        Map<DayOfWeek, Integer> teacherHoursByDay = new HashMap<>();
        for (Map<DayOfWeek, List<TemplateCell>> byDay : template.values()) {
            for (Map.Entry<DayOfWeek, List<TemplateCell>> entry : byDay.entrySet()) {
                int hours = entry.getValue().stream().mapToInt(TemplateCell::length).sum();
                teacherHoursByDay.merge(entry.getKey(), hours, Integer::sum);
            }
        }
        for (Map.Entry<DayOfWeek, Integer> entry : teacherHoursByDay.entrySet()) {
            assertTrue(entry.getValue() <= 3,
                    entry.getKey() + " has " + entry.getValue() + "h for the shared teacher, exceeds maxDailyHours=3");
        }
    }

    @Test
    void nextContiguousSlot_usesFreeSlotsBelowAMidDayPinnedBlock() {
        // Real case (review fix): a MODA second-shift pin occupies mid-day
        // slots 3-5, leaving 0-2 free. The old max(occupied)+1 logic
        // discarded those free slots and jumped straight to slot 6 (the
        // afternoon), even though a compact morning placement was available.
        StudentGroup g = group("G05");
        Teacher t = teacher("Shift teacher");
        Course c = course("Storia della moda", t, g, 2);

        // Only Monday open, so the course is forced onto the day carrying
        // the mid-day pinned block (no other weekday to escape to).
        GroupWeeklyShape shape = new GroupWeeklyShape(g, false);
        shape.setWindow(DayOfWeek.MONDAY, 0, 7);

        Set<GroupSlot> blockedGroupSlots = new HashSet<>();
        blockedGroupSlots.add(new GroupSlot(g.id, DayOfWeek.MONDAY, 3));
        blockedGroupSlots.add(new GroupSlot(g.id, DayOfWeek.MONDAY, 4));
        blockedGroupSlots.add(new GroupSlot(g.id, DayOfWeek.MONDAY, 5));

        Map<Course, Integer> targets = Map.of(c, 2);
        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template = WeeklyTemplateBuilder.build(
                List.of(shape), targets, Set.of(), Set.of(), blockedGroupSlots, new HashMap<>(), Map.of());

        List<TemplateCell> mondayCells = template.getOrDefault(g, Map.of()).getOrDefault(DayOfWeek.MONDAY, List.of());
        assertEquals(1, mondayCells.size(),
                "the 2h course must land as one contiguous block using the free slots below the mid-day pin");
        assertEquals(0, mondayCells.get(0).slotIndex(),
                "must use free slot 0 (adjacent to the 3-5 pin), not fall through to the afternoon slot 6");
        assertEquals(2, mondayCells.get(0).length());
    }

    @Test
    void teacherDailyCap_countsOnlyTeachingSlotsNotUnavailability() {
        // Review fix: a teacher's majority-unavailable (vacation/sick) cells
        // must never count toward the H7 daily-cap arithmetic - only cells
        // already occupied by real teaching should. Otherwise a
        // partial-day-vacation teacher is under-allocated.
        //
        // Slot selection for a NEW chunk is driven purely by the GROUP's own
        // occupancy (nextContiguousSlot), so with no group-side blocking the
        // course lands at slot 0 regardless of the teacher's cells - this
        // test isolates the H7 CAP check itself: with teacherDailyCap=3, one
        // pre-existing TEACHING hour (slot 5) plus 2 pre-existing
        // UNAVAILABLE hours (slots 6-7) must count as only 1 taught hour
        // toward the cap, not 3 - so the course's 1h target still fits
        // (1 + 1 <= 3). The old code, counting all 3 cells as taught, would
        // have rejected the placement (3 + 1 > 3) and left it unplaced.
        StudentGroup g = group("G04");
        Teacher t = teacher("PartialVacation");
        Course c = course("Scienze", t, g, 1);

        // Only Monday open, forcing the course onto the day carrying the
        // mixed unavailable/teaching cells.
        GroupWeeklyShape shape = new GroupWeeklyShape(g, false);
        shape.setWindow(DayOfWeek.MONDAY, 0, 7);

        Set<TeacherSlot> unavailable = Set.of(
                new TeacherSlot(t.id, DayOfWeek.MONDAY, 6),
                new TeacherSlot(t.id, DayOfWeek.MONDAY, 7));
        Set<TeacherSlot> teaching = Set.of(new TeacherSlot(t.id, DayOfWeek.MONDAY, 5));

        Map<Course, Integer> targets = Map.of(c, 1);
        Map<Long, Integer> teacherDailyCap = Map.of(t.id, 3);

        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template = WeeklyTemplateBuilder.build(
                List.of(shape), targets, unavailable, teaching, Set.of(), new HashMap<>(), teacherDailyCap);

        List<TemplateCell> mondayCells = template.getOrDefault(g, Map.of()).getOrDefault(DayOfWeek.MONDAY, List.of());
        assertEquals(1, mondayCells.size(),
                "must be placed: only the 1 pre-existing TEACHING hour, not the 2 unavailability hours, "
                        + "should count toward the teacherDailyCap of 3");
        assertEquals(0, mondayCells.get(0).slotIndex(), "the group's own free slot (0) is unaffected by teacher-side cells");
    }

    // ─── TASK-385: expected-hours day target + largest-remainder allocation ───

    @Test
    void dailyTargetHours_capsPlacementBelowTheCalendarWindow() {
        // A full 0-7 window (8 slots) would normally allow up to 8h/day, but
        // the school's own per-date expected-hours fact (e.g. a 3h Friday)
        // must cap the day BELOW the window when it's smaller.
        StudentGroup g = group("G03");
        Teacher t = teacher("T");
        Course c = course("Matematica", t, g, 6);

        GroupWeeklyShape shape = new GroupWeeklyShape(g, false);
        shape.setWindow(DayOfWeek.MONDAY, 0, 7);
        shape.setDailyTarget(DayOfWeek.MONDAY, 3);

        Map<Course, Integer> targets = Map.of(c, 6);
        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template = WeeklyTemplateBuilder.build(
                List.of(shape), targets, Set.of(), Set.of(), Set.of(), new HashMap<>(), Map.of());

        int mondayHours = template.getOrDefault(g, Map.of()).getOrDefault(DayOfWeek.MONDAY, List.of())
                .stream().mapToInt(TemplateCell::length).sum();
        assertTrue(mondayHours <= 3, "Monday has " + mondayHours + "h, exceeds the expected-hours target of 3");
    }

    @Test
    void dailyTargetHours_unsetLeavesTheWindowCapUnchanged() {
        // No setDailyTarget() call at all - the day's cap must fall back to
        // the window/H11 cap exactly as before this field existed.
        StudentGroup g = group("G03");
        Teacher t = teacher("T");
        Course c = course("Matematica", t, g, 6);
        GroupWeeklyShape shape = fullWeekShape(g);

        Map<Course, Integer> targets = Map.of(c, 6);
        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template = WeeklyTemplateBuilder.build(
                List.of(shape), targets, Set.of(), Set.of(), Set.of(), new HashMap<>(), Map.of());

        int totalPlaced = template.get(g).values().stream()
                .flatMap(List::stream).mapToInt(TemplateCell::length).sum();
        assertEquals(6, totalPlaced, "unset dailyTargetHours must not restrict placement at all");
    }

    @Test
    void allocateLargestRemainder_sumsExactlyToWeeklyTotalWhenDemandAllows() {
        StudentGroup g = group("G09");
        Teacher t = teacher("T");
        // hours/activeWeeks shares: 5/4=1.25, 3/4=0.75, 2/4=0.5 -> floors 1+0+0=1,
        // weeklyTotal=3 needs 2 more seats, awarded to the two largest remainders (0.75, 0.5).
        Course a = course("A", t, g, 5);
        Course b = course("B", t, g, 3);
        Course c = course("C", t, g, 2);
        Map<Course, Integer> hours = Map.of(a, 5, b, 3, c, 2);

        Map<Course, Integer> allocation = WeeklyTemplateBuilder.allocateLargestRemainder(hours, 4, 3);

        assertEquals(3, allocation.values().stream().mapToInt(Integer::intValue).sum(),
                "allocation must sum EXACTLY to the weekly total when demand allows it");
        assertEquals(1, allocation.get(a));
        assertEquals(1, allocation.get(b));
        assertEquals(1, allocation.get(c));
    }

    @Test
    void allocateLargestRemainder_insufficientDemandLeavesAShortfall() {
        StudentGroup g = group("G09");
        Teacher t = teacher("T");
        // Only one course, floor(4/4)=1 - each course can receive at most ONE
        // bonus seat per Hamilton pass, so a weeklyTotal of 5 (needing 4 extra
        // seats with only 1 candidate course) still falls short of 5
        // ("where demand allows" - never fabricate hours no course can carry).
        Course a = course("A", t, g, 4);
        Map<Course, Integer> hours = Map.of(a, 4);

        Map<Course, Integer> allocation = WeeklyTemplateBuilder.allocateLargestRemainder(hours, 4, 5);

        assertEquals(2, allocation.get(a), "floor(1) + at most one bonus seat, never more");
        assertEquals(2, allocation.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void allocateLargestRemainder_excessFloorsAreTrimmedDownToTheTotal() {
        StudentGroup g = group("G09");
        Teacher t1 = teacher("T1");
        Teacher t2 = teacher("T2");
        // floor(4/4)=1, floor(4/4)=1 -> floorSum=2 already exceeds a
        // weeklyTotal of 1 - the excess course (smallest remainder, both
        // exactly 0 here since 4/4 is a whole number, tie-broken by id) must
        // be trimmed down, never left over the requested total.
        Course a = course("A", t1, g, 4);
        Course b = course("B", t2, g, 4);
        Map<Course, Integer> hours = Map.of(a, 4, b, 4);

        Map<Course, Integer> allocation = WeeklyTemplateBuilder.allocateLargestRemainder(hours, 4, 1);

        assertEquals(1, allocation.values().stream().mapToInt(Integer::intValue).sum(),
                "allocation must never exceed the requested weekly total");
    }

    @Test
    void allocateLargestRemainder_skipsOverAFloorZeroCourseToFindARealTrimCandidate() {
        // Code-review regression: a floor-0 course can sort AHEAD of a
        // floor>=1 course in the ascending-remainder trim order (share=0.5
        // -> floor 0, remainder 0.5; share=1.6 -> floor 1, remainder 0.6 -
        // 0.5 < 0.6) even though only the floor>=1 course can actually be
        // decremented. An index-bounded loop that stops after `toRemove`
        // positions would waste its one trim slot on the un-decrementable
        // floor-0 course and leave the sum 1 OVER the requested total.
        StudentGroup g = group("G09");
        Teacher t1 = teacher("T1");
        Teacher t2 = teacher("T2");
        Course a = course("A", t1, g, 5);  // 5/10 = 0.5 -> floor 0
        Course b = course("B", t2, g, 16); // 16/10 = 1.6 -> floor 1
        Map<Course, Integer> hours = Map.of(a, 5, b, 16);

        Map<Course, Integer> allocation = WeeklyTemplateBuilder.allocateLargestRemainder(hours, 10, 0);

        assertEquals(0, allocation.values().stream().mapToInt(Integer::intValue).sum(),
                "must keep searching past the un-decrementable floor-0 course to find B, the real trim candidate");
    }

    @Test
    void allocateLargestRemainder_multiPassTrimHandlesExcessBeyondOneDeductionPerCourse() {
        // Code-review regression: a single pass over the ascending-remainder
        // list decrements each course AT MOST ONCE, which under-trims
        // whenever toRemove exceeds the number of floor>=1 courses. Two
        // courses at floor 5 each (floorSum=10) against weeklyTotal=2 needs
        // toRemove=8 deductions total, only 2 of which fit in a single pass -
        // the trim must repeat across multiple passes until the total is
        // reached.
        StudentGroup g = group("G09");
        Teacher t1 = teacher("T1");
        Teacher t2 = teacher("T2");
        Course a = course("A", t1, g, 5);  // 5/1 = 5.0 -> floor 5
        Course b = course("B", t2, g, 5);  // 5/1 = 5.0 -> floor 5
        Map<Course, Integer> hours = Map.of(a, 5, b, 5);

        Map<Course, Integer> allocation = WeeklyTemplateBuilder.allocateLargestRemainder(hours, 1, 2);

        assertEquals(2, allocation.values().stream().mapToInt(Integer::intValue).sum(),
                "allocation must sum EXACTLY to the weekly total, not stop after one deduction per course");
    }
    /**
     * TASK-542 iter-11: with the teacher-weekday-spread preference ON, a teacher's second,
     * SMALLER recurring course prefers a weekday the teacher does not already carry hours on.
     * Fixture: teacher Shared with a 5h block on one group and a 1h course on another group.
     * With first-fit the 1h course can land on the same weekday as part of the block's day;
     * with the spread preference the 1h course must land on a weekday where Shared has zero
     * template hours (the rotation order's first such day).
     */
    @Test
    void teacherWeekdaySpread_smallCourseAvoidsTheBlockWeekday() {
        StudentGroup blockGroup = group("G04");
        StudentGroup hourGroup = group("G08");
        Teacher shared = teacher("Shared Spread");
        Course block = course("Blocco", shared, blockGroup, 5);
        Course single = course("Ora Unica", shared, hourGroup, 1);

        Map<Course, Integer> targets = new HashMap<>();
        targets.put(block, 5);
        targets.put(single, 1);
        List<GroupWeeklyShape> shapes = List.of(fullWeekShape(blockGroup), fullWeekShape(hourGroup));

        try {
            System.setProperty("planner.construction.teacher-weekday-spread.enabled", "true");
            Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template = WeeklyTemplateBuilder.build(
                    shapes, targets, Set.of(), Set.of(), Set.of(), new HashMap<>(), Map.of());

            Set<DayOfWeek> blockDays = template.getOrDefault(blockGroup, Map.of()).keySet();
            Set<DayOfWeek> singleDays = template.getOrDefault(hourGroup, Map.of()).keySet();
            assertFalse(singleDays.isEmpty(), "the 1h course must be placed");
            for (DayOfWeek d : singleDays) {
                assertFalse(blockDays.contains(d),
                        "the 1h course landed on " + d + ", where the same teacher already"
                                + " carries the 5h block of another group (the G13-Friday shape)");
            }
        } finally {
            System.clearProperty("planner.construction.teacher-weekday-spread.enabled");
        }
    }
}
