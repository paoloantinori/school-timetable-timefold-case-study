package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;

import io.quarkus.test.junit.QuarkusTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for GroupCalendarService (TASK-342): per-group, per-weekday
 * slot-level calendar opening pattern.
 *
 * The pure resolution algorithm (effectiveRange/isOpen) is exercised directly
 * against in-memory (unpersisted) rules — no DB needed, mirrors how the
 * solver's static snapshot (SchoolSchedule.isGroupCalendarOpen /
 * getGroupCalendarWindow) consults the same static methods. A handful of
 * @Transactional tests at the bottom cover the CDI instance API
 * (isOpen/openSlotCount), which queries GroupCalendarRule from the DB.
 */
@QuarkusTest
class GroupCalendarServiceTest {

    @Inject
    GroupCalendarService service;

    // GroupCalendarRule FKs into StudentGroup - other test classes' setup deletes
    // StudentGroup rows without expecting this new child table, so any row left
    // behind here breaks their cleanup with a FK violation. Only delete
    // GroupCalendarRule (the table only this test class writes to) - a blanket
    // StudentGroup.deleteAll() would collide with unrelated leftover rows from
    // other test classes sharing the same H2 instance.
    @AfterEach
    @Transactional
    void cleanupDb() {
        GroupCalendarRule.deleteAll();
    }

    private static Timeslot ts(LocalDate date, int hour, int minute) {
        Timeslot t = new Timeslot();
        t.startDateTime = LocalDateTime.of(date, LocalTime.of(hour, minute));
        t.endDateTime = t.startDateTime.plusHours(1);
        return t;
    }

    // ─── AC #1: default-open (no matching rule) ────────────────────────────

    @Test
    void isOpen_noRules_defaultsToFullyOpen() {
        Timeslot lastSlot = ts(LocalDate.of(2026, 9, 14), 15, 30); // slot 7
        assertTrue(GroupCalendarService.isOpen(List.of(), lastSlot));
    }

    @Test
    void effectiveRange_noRules_isFullDay() {
        int[] range = GroupCalendarService.effectiveRange(List.of(), DayOfWeek.MONDAY, LocalDate.of(2026, 9, 21));
        assertEquals(0, range[0]);
        assertEquals(GroupCalendarService.TOTAL_SLOTS - 1, range[1]);
    }

    // ─── AC #1: plain weekly range ─────────────────────────────────────────

    @Test
    void isOpen_withinRange_open() {
        GroupCalendarRule rule = new GroupCalendarRule(null, DayOfWeek.MONDAY, 0, 5);
        Timeslot inRange = ts(LocalDate.of(2026, 9, 21), 9, 0); // slot 1
        assertTrue(GroupCalendarService.isOpen(List.of(rule), inRange));
    }

    @Test
    void isOpen_outsideRange_closed() {
        GroupCalendarRule rule = new GroupCalendarRule(null, DayOfWeek.MONDAY, 0, 5);
        Timeslot afternoon = ts(LocalDate.of(2026, 9, 21), 14, 30); // slot 6
        assertFalse(GroupCalendarService.isOpen(List.of(rule), afternoon));
    }

    @Test
    void isOpen_wrongWeekday_ignoresRule() {
        // Rule only applies Monday; querying a Tuesday slot must fall back to default-open.
        GroupCalendarRule rule = new GroupCalendarRule(null, DayOfWeek.MONDAY, 0, 3);
        Timeslot tuesdayAfternoon = ts(LocalDate.of(2026, 9, 22), 14, 30); // slot 6, Tuesday
        assertTrue(GroupCalendarService.isOpen(List.of(rule), tuesdayAfternoon));
    }

    @Test
    void fullyClosedDay_maxLessThanMin_neverOpen() {
        // maxSlot < minSlot represents a fully closed weekday (G14 Mon/Tue/Wed).
        GroupCalendarRule closed = new GroupCalendarRule(null, DayOfWeek.MONDAY, 0, -1);
        for (int hour : new int[] {8, 9, 10, 11, 12, 13}) {
            assertFalse(GroupCalendarService.isOpen(List.of(closed), ts(LocalDate.of(2026, 9, 21), hour, 0)),
                    "slot at " + hour + ":00 should be closed");
        }
    }

    // ─── AC #1: date-scoped override precedence (most specific wins) ──────

    @Test
    void dateScopedOverride_narrowerRuleWinsOverUnboundedRule() {
        LocalDate rampDay = LocalDate.of(2026, 9, 14);
        GroupCalendarRule yearRound = new GroupCalendarRule(null, DayOfWeek.MONDAY, 0, 7);
        GroupCalendarRule rampOverride = new GroupCalendarRule(null, DayOfWeek.MONDAY, 1, 4, rampDay, rampDay);

        Timeslot slot0 = ts(rampDay, 8, 0); // slot 0 - closed by the narrow override
        assertFalse(GroupCalendarService.isOpen(List.of(yearRound, rampOverride), slot0));

        // Any other Monday: the narrow override's date range excludes it, so the
        // unbounded rule applies and slot 0 is open again.
        Timeslot otherMonday = ts(LocalDate.of(2026, 9, 21), 8, 0);
        assertTrue(GroupCalendarService.isOpen(List.of(yearRound, rampOverride), otherMonday));
    }

    @Test
    void dateScopedOverride_tieBrokenByLatestEffectiveFrom() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        // Two equally-narrow (single-day) rules for the same date is a degenerate
        // case, but two overlapping-but-different-span rules must resolve by span
        // first; when spans tie, the later effectiveFrom wins.
        GroupCalendarRule older = new GroupCalendarRule(null, DayOfWeek.THURSDAY, 0, 7,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
        GroupCalendarRule newer = new GroupCalendarRule(null, DayOfWeek.THURSDAY, 0, 2,
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 10, 2));

        int[] range = GroupCalendarService.effectiveRange(List.of(older, newer), DayOfWeek.THURSDAY, day);
        assertEquals(0, range[0]);
        assertEquals(2, range[1], "the rule with the later effectiveFrom (same span) must win");
    }

    // TASK-342 Z4: identical span + identical effectiveFrom (duplicate rules) must
    // resolve deterministically regardless of list order, via a final id tie-break.
    @Test
    void identicalSpanAndEffectiveFrom_tieBrokenByHigherRuleId_orderIndependent() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        GroupCalendarRule lowerId = new GroupCalendarRule(null, DayOfWeek.THURSDAY, 0, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
        lowerId.id = 10L;
        GroupCalendarRule higherId = new GroupCalendarRule(null, DayOfWeek.THURSDAY, 0, 2,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
        higherId.id = 20L;

        int[] rangeInOrder = GroupCalendarService.effectiveRange(List.of(lowerId, higherId), DayOfWeek.THURSDAY, day);
        int[] rangeReversed = GroupCalendarService.effectiveRange(List.of(higherId, lowerId), DayOfWeek.THURSDAY, day);

        assertEquals(2, rangeInOrder[1], "the higher-id rule must win the tie");
        assertEquals(rangeInOrder[1], rangeReversed[1], "resolution must not depend on list order");
    }

    // ─── AC #2: seeded shapes, verified against lessons.csv-derived expectations ───
    // (claudedocs/spalmone-2026-27/analysis.md section 2; see DataGenerator.GROUP_CALENDAR_PATTERNS
    // for the groups actually seeded — G14 itself is not seeded yet, see DataGenerator javadoc)

    @Test
    void G14Shape_onlyThursdayAndFridayOpen() {
        List<GroupCalendarRule> G14Rules = List.of(
                new GroupCalendarRule(null, DayOfWeek.MONDAY, 0, -1),
                new GroupCalendarRule(null, DayOfWeek.TUESDAY, 0, -1),
                new GroupCalendarRule(null, DayOfWeek.WEDNESDAY, 0, -1),
                new GroupCalendarRule(null, DayOfWeek.THURSDAY, 0, 4),
                new GroupCalendarRule(null, DayOfWeek.FRIDAY, 0, 2));

        LocalDate mon = LocalDate.of(2026, 9, 21);
        LocalDate thu = LocalDate.of(2026, 9, 24);
        LocalDate fri = LocalDate.of(2026, 9, 25);

        assertFalse(GroupCalendarService.isOpen(G14Rules, ts(mon, 8, 0)), "G14 Monday must be fully closed");
        assertTrue(GroupCalendarService.isOpen(G14Rules, ts(thu, 8, 0)), "G14 Thursday slot 0 open");
        assertTrue(GroupCalendarService.isOpen(G14Rules, ts(thu, 12, 0)), "G14 Thursday slot 4 (last) open");
        assertFalse(GroupCalendarService.isOpen(G14Rules, ts(thu, 13, 0)), "G14 Thursday slot 5 must be closed");
        assertTrue(GroupCalendarService.isOpen(G14Rules, ts(fri, 8, 0)), "G14 Friday slot 0 open");
        assertFalse(GroupCalendarService.isOpen(G14Rules, ts(fri, 11, 0)), "G14 Friday slot 3 must be closed (only 0-2 open)");
    }

    @Test
    void firstYearShape_noAfternoonAndShortFriday() {
        // G03 / G02 / G01 / G05 shape: Mon-Thu 0-5 (no afternoon), Fri 0-4 (short).
        List<GroupCalendarRule> firstYearRules = List.of(
                new GroupCalendarRule(null, DayOfWeek.MONDAY, 0, 5),
                new GroupCalendarRule(null, DayOfWeek.FRIDAY, 0, 4));

        LocalDate mon = LocalDate.of(2026, 9, 21);
        LocalDate fri = LocalDate.of(2026, 9, 25);

        assertTrue(GroupCalendarService.isOpen(firstYearRules, ts(mon, 13, 0)), "Monday slot 5 (13:00) is the last open slot");
        assertFalse(GroupCalendarService.isOpen(firstYearRules, ts(mon, 14, 30)), "Monday afternoon (slot 6) must be closed");
        assertTrue(GroupCalendarService.isOpen(firstYearRules, ts(fri, 12, 0)), "Friday slot 4 (12:00) is the last open slot");
        assertFalse(GroupCalendarService.isOpen(firstYearRules, ts(fri, 13, 0)), "Friday slot 5 must be closed (short Friday)");
    }

    // ─── CDI instance API (isOpen/openSlotCount), backed by the DB ─────────

    @Test
    @Transactional
    void instanceIsOpen_matchesPersistedRule() {
        StudentGroup group = persistGroup("Test Group");
        new GroupCalendarRule(group, DayOfWeek.MONDAY, 0, 4).persist();

        Timeslot open = ts(LocalDate.of(2026, 9, 21), 8, 0); // slot 0
        Timeslot closed = ts(LocalDate.of(2026, 9, 21), 14, 30); // slot 6

        assertTrue(service.isOpen(group, open));
        assertFalse(service.isOpen(group, closed));
    }

    @Test
    @Transactional
    void instanceOpenSlotCount_noRule_returnsAllSlots() {
        StudentGroup group = persistGroup("No Calendar Rules Group");
        int count = service.openSlotCount(group, DayOfWeek.MONDAY, LocalDate.of(2026, 9, 21));
        assertEquals(GroupCalendarService.TOTAL_SLOTS, count);
    }

    @Test
    @Transactional
    void instanceOpenSlotCount_withRule_countsRange() {
        StudentGroup group = persistGroup("Five Slot Group");
        new GroupCalendarRule(group, DayOfWeek.FRIDAY, 0, 4).persist();
        int count = service.openSlotCount(group, DayOfWeek.FRIDAY, LocalDate.of(2026, 9, 25));
        assertEquals(5, count);
    }

    private StudentGroup persistGroup(String name) {
        StudentGroup group = new StudentGroup();
        group.name = name;
        group.size = 10;
        group.persist();
        return group;
    }

    // ─── resolveRange (TASK-358 code review N2/N3) ─────────────────────────

    @Test
    @Transactional
    void resolveRange_noRulesNoMaxHours_fullyOpenEveryWeekday() {
        StudentGroup group = persistGroup("Resolve No Rules");

        List<GroupCalendarService.ResolvedSlotRange> resolved =
                service.resolveRange(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18)); // Mon-Fri

        List<GroupCalendarService.ResolvedSlotRange> forGroup = resolved.stream()
                .filter(r -> r.groupId.equals(group.id)).toList();
        assertEquals(5, forGroup.size(), "one entry per weekday, weekends excluded");
        for (GroupCalendarService.ResolvedSlotRange r : forGroup) {
            assertEquals(0, r.minSlot);
            assertEquals(GroupCalendarService.TOTAL_SLOTS - 1, r.maxSlot);
            assertFalse(r.closed);
        }
    }

    // N3: legacy profile fallback - maxHoursPerDay caps weekdays with zero calendar rules.
    @Test
    @Transactional
    void resolveRange_noRulesWithMaxHoursPerDay_capsToMaxHours() {
        StudentGroup group = persistGroup("Resolve Legacy Cap");
        group.maxHoursPerDay = 5;

        List<GroupCalendarService.ResolvedSlotRange> resolved =
                service.resolveRange(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)); // Monday

        GroupCalendarService.ResolvedSlotRange r = resolved.stream()
                .filter(x -> x.groupId.equals(group.id)).findFirst().orElseThrow();
        assertEquals(0, r.minSlot);
        assertEquals(4, r.maxSlot, "maxHoursPerDay=5 -> slots 0-4 open");
        assertFalse(r.closed);
    }

    @Test
    @Transactional
    void resolveRange_withBaseRule_appliesToEveryMatchingWeekday() {
        StudentGroup group = persistGroup("Resolve Base Rule");
        new GroupCalendarRule(group, DayOfWeek.MONDAY, 0, 4).persist();

        // Two Mondays, no date-scoping on the rule - both must resolve identically.
        List<GroupCalendarService.ResolvedSlotRange> resolved =
                service.resolveRange(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21));

        List<GroupCalendarService.ResolvedSlotRange> mondays = resolved.stream()
                .filter(r -> r.groupId.equals(group.id) && r.date.getDayOfWeek() == DayOfWeek.MONDAY).toList();
        assertEquals(2, mondays.size());
        for (GroupCalendarService.ResolvedSlotRange r : mondays) {
            assertEquals(4, r.maxSlot);
        }
    }

    // "First-Monday ramp": a date-scoped override narrows just the first day, base rule applies after.
    @Test
    @Transactional
    void resolveRange_dateScopedOverride_onlyAffectsScopedDate() {
        StudentGroup group = persistGroup("Resolve Ramp Group");
        LocalDate rampDay = LocalDate.of(2026, 9, 14); // Monday
        new GroupCalendarRule(group, DayOfWeek.MONDAY, 0, 7).persist();
        new GroupCalendarRule(group, DayOfWeek.MONDAY, 1, 3, rampDay, rampDay).persist();

        List<GroupCalendarService.ResolvedSlotRange> resolved =
                service.resolveRange(rampDay, rampDay.plusWeeks(1)); // ramp Monday .. next Monday

        GroupCalendarService.ResolvedSlotRange rampMonday = resolved.stream()
                .filter(r -> r.groupId.equals(group.id) && r.date.equals(rampDay)).findFirst().orElseThrow();
        assertEquals(1, rampMonday.minSlot);
        assertEquals(3, rampMonday.maxSlot);

        GroupCalendarService.ResolvedSlotRange nextMonday = resolved.stream()
                .filter(r -> r.groupId.equals(group.id) && r.date.equals(rampDay.plusWeeks(1))).findFirst().orElseThrow();
        assertEquals(0, nextMonday.minSlot);
        assertEquals(7, nextMonday.maxSlot, "outside the ramp override's date scope, the base rule applies");
    }

    @Test
    @Transactional
    void resolveRange_closedDaySentinel_setsClosedTrue() {
        StudentGroup group = persistGroup("Resolve Closed Day");
        new GroupCalendarRule(group, DayOfWeek.WEDNESDAY, 0, -1).persist();

        List<GroupCalendarService.ResolvedSlotRange> resolved =
                service.resolveRange(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 16)); // Wednesday

        GroupCalendarService.ResolvedSlotRange r = resolved.stream()
                .filter(x -> x.groupId.equals(group.id)).findFirst().orElseThrow();
        assertTrue(r.closed);
    }

    @Test
    @Transactional
    void resolveRange_excludesWeekends() {
        persistGroup("Resolve Weekend Check");
        // Sat 2026-09-19, Sun 2026-09-20
        List<GroupCalendarService.ResolvedSlotRange> resolved =
                service.resolveRange(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 20));
        assertEquals(0, resolved.size());
    }
}
