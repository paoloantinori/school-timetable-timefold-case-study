package com.school.planner.bootstrap;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies DataGenerator.GROUP_CALENDAR_PATTERNS (TASK-342) against the
 * per-group weekday-slot ranges mechanically derived from
 * claudedocs/spalmone-2026-27/data/lessons.csv (union of occupied slot
 * indices per (group, weekday) across the full 2026/27 school year — see
 * analysis.md section 2 and the DataGenerator.generateGroupCalendarRules()
 * javadoc for the derivation method).
 *
 * Plain JUnit (no @QuarkusTest) — GROUP_CALENDAR_PATTERNS is a pure data
 * table, exercised directly like DataGeneratorCalendarTest does for the
 * school-day computation.
 */
class GroupCalendarSeedTest {

    private static Map<String, String[]> patternsByGroup() {
        Map<String, String[]> map = new HashMap<>();
        for (String[] row : DataGenerator.GROUP_CALENDAR_PATTERNS) {
            String[] days = new String[DataGenerator.GROUP_CALENDAR_WEEKDAYS.length];
            for (int i = 0; i < days.length; i++) {
                days[i] = row[i + 1];
            }
            map.put(row[0], days);
        }
        return map;
    }

    private static int dayIndex(DayOfWeek dow) {
        for (int i = 0; i < DataGenerator.GROUP_CALENDAR_WEEKDAYS.length; i++) {
            if (DataGenerator.GROUP_CALENDAR_WEEKDAYS[i] == dow) return i;
        }
        throw new IllegalArgumentException("not a school weekday: " + dow);
    }

    private static String rangeFor(Map<String, String[]> patterns, String group, DayOfWeek dow) {
        String[] days = patterns.get(group);
        assertNotNull(days, "no seeded pattern for group " + group);
        return days[dayIndex(dow)];
    }

    @Test
    void allFourteenSeededGroupsHaveAFullWeekPattern() {
        Map<String, String[]> patterns = patternsByGroup();
        assertEquals(14, patterns.size(), "14 groups exist in DataGenerator (G14 re-added, TASK-343)");
        for (String[] days : patterns.values()) {
            assertEquals(5, days.length);
        }
    }

    // First-year groups (G03, G02, G01, G05): no afternoons, short Friday.
    @Test
    void firstYearGroups_noAfternoons_shortFriday() {
        Map<String, String[]> patterns = patternsByGroup();
        for (String group : new String[] {"G03", "G02", "G01", "G05"}) {
            assertEquals("0-5", rangeFor(patterns, group, DayOfWeek.MONDAY), group + " Monday");
            assertEquals("0-5", rangeFor(patterns, group, DayOfWeek.TUESDAY), group + " Tuesday");
            assertEquals("0-5", rangeFor(patterns, group, DayOfWeek.WEDNESDAY), group + " Wednesday");
            assertEquals("0-5", rangeFor(patterns, group, DayOfWeek.THURSDAY), group + " Thursday");
            assertEquals("0-4", rangeFor(patterns, group, DayOfWeek.FRIDAY), group + " Friday (short)");
        }
    }

    // Groups with genuine afternoon usage reach slot 7 on at least one weekday.
    @Test
    void afternoonGroups_reachSlot7OnAtLeastOneWeekday() {
        Map<String, String[]> patterns = patternsByGroup();
        for (String group : new String[] {"G07", "G11", "G06", "G10", "G12", "G09", "G04", "G08", "G13"}) {
            String[] days = patterns.get(group);
            assertNotNull(days, group);
            boolean reachesSlot7 = false;
            for (String range : days) {
                if (range != null && range.endsWith("-7")) {
                    reachesSlot7 = true;
                    break;
                }
            }
            org.junit.jupiter.api.Assertions.assertTrue(reachesSlot7, group + " should reach slot 7 on at least one weekday");
        }
    }

    @Test
    void thirdYearAndFCGroups_fullWeekAfternoons() {
        // G09, G04, G08: afternoons every weekday (0-7 Mon-Fri).
        Map<String, String[]> patterns = patternsByGroup();
        for (String group : new String[] {"G09", "G04", "G08"}) {
            for (DayOfWeek dow : DataGenerator.GROUP_CALENDAR_WEEKDAYS) {
                assertEquals("0-7", rangeFor(patterns, group, dow), group + " " + dow);
            }
        }
    }

    @Test
    void FDGroup_shortMorningsExceptThursdayAndFriday() {
        // G12: Mon-Wed short (0-4), Thu full afternoon (0-7), Fri (0-6).
        Map<String, String[]> patterns = patternsByGroup();
        assertEquals("0-4", rangeFor(patterns, "G12", DayOfWeek.MONDAY));
        assertEquals("0-4", rangeFor(patterns, "G12", DayOfWeek.TUESDAY));
        assertEquals("0-4", rangeFor(patterns, "G12", DayOfWeek.WEDNESDAY));
        assertEquals("0-7", rangeFor(patterns, "G12", DayOfWeek.THURSDAY));
        assertEquals("0-6", rangeFor(patterns, "G12", DayOfWeek.FRIDAY));
    }

    // TASK-342 Z2: a null GROUP_CALENDAR_PATTERNS cell means "fully closed that
    // weekday" and must persist the closed-day sentinel (maxSlot < minSlot), not be
    // skipped entirely - skipping it leaves no rule at all, which GroupCalendarService
    // resolves as "fully open" (the opposite of the intended meaning).
    @Test
    void parseCalendarCell_nullCell_isClosedDaySentinel() {
        int[] slots = DataGenerator.parseCalendarCell(null);
        assertEquals(0, slots[0]);
        assertEquals(-1, slots[1], "null cell must produce maxSlot < minSlot, not be skipped");
    }

    @Test
    void parseCalendarCell_rangeCell_parsesMinAndMaxSlot() {
        int[] slots = DataGenerator.parseCalendarCell("0-4");
        assertEquals(0, slots[0]);
        assertEquals(4, slots[1]);
    }

    @Test
    void G14Seeded_closedMonToWed_openThuAndFri() {
        // G14 re-added as a real StudentGroup (TASK-343, ScheduleService.
        // EXCLUDED_GROUPS is now empty). Row shape matches
        // GroupCalendarServiceTest#G14Shape_onlyThursdayAndFridayOpen:
        // Mon/Tue/Wed fully closed (null cell), Thu 0-4, Fri 0-2.
        Map<String, String[]> patterns = patternsByGroup();
        assertEquals(null, rangeFor(patterns, "G14", DayOfWeek.MONDAY), "G14 Monday closed");
        assertEquals(null, rangeFor(patterns, "G14", DayOfWeek.TUESDAY), "G14 Tuesday closed");
        assertEquals(null, rangeFor(patterns, "G14", DayOfWeek.WEDNESDAY), "G14 Wednesday closed");
        assertEquals("0-4", rangeFor(patterns, "G14", DayOfWeek.THURSDAY));
        assertEquals("0-2", rangeFor(patterns, "G14", DayOfWeek.FRIDAY));
    }
}
