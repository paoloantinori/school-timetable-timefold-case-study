package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.school.planner.TestDataBuilder;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * TASK-542 iter-12 regression: at pre-assign the calendar statics are EMPTY, and
 * openSlotsForGroupDay must still honor the group's REAL DB rules (the window read as
 * full-open and 8 authoritative pins landed on calendar-closed Thursday afternoon slots).
 */
@QuarkusTest
class OpenSlotsWindowFallbackTest {

    @Inject
    HybridPhase1aService service;

    @Test
    @Transactional
    void windowFallsBackToDbRulesWhenStaticsAreEmpty() {
        // statics deliberately NOT published: this is the pre-assign state
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());

        StudentGroup group = new StudentGroup();
        group.name = "WFB";

        group.persistAndFlush(); // no hand-set id: a detached id makes persist() throw
        GroupCalendarRule rule = new GroupCalendarRule(group, DayOfWeek.THURSDAY, 0, 5);
        rule.persist();

        LocalDate thu = LocalDate.of(2026, 10, 8); // a Thursday
        List<Timeslot> daySlots = new java.util.ArrayList<>();
        long id = 920_000L;
        for (int slot = 0; slot < 8; slot++) {
            daySlots.add(TestDataBuilder.timeslot(id++, thu, 8 + Math.min(slot, 5), slot < 6 ? 0 : 30,
                    9 + Math.min(slot, 5), slot < 6 ? 0 : 30));
        }
        // slots 6-7 are the afternoon 14:30/15:30: force their times explicitly
        daySlots.set(6, TestDataBuilder.timeslot(920_006L, thu, 14, 30, 15, 30));
        daySlots.set(7, TestDataBuilder.timeslot(id++, thu, 15, 30, 16, 30));

        List<Timeslot> open = service.openSlotsForGroupDay(group, thu, daySlots);

        assertEquals(6, open.size(), "Thursday window is 0-5: only six morning slots");
        assertTrue(open.stream().noneMatch(t -> t.startDateTime.getHour() >= 14),
                "the calendar-closed afternoon slots must never be offered to a pin");
    }
}
