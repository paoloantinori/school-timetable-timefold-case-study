package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;

import io.quarkus.test.junit.QuarkusTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-454: the export payload must carry the GroupCalendarRule rows (and the
 * quota rows it already carried since TASK-381). The import side re-inserts
 * exactly what the payload carries, so "present in the export" is the
 * load-bearing half of the round trip. The test DB has no DataGenerator seed,
 * so the fixture seeds its own rows and asserts they land in the export.
 */
@QuarkusTest
class GroupCalendarRuleExportCarriageTest {

    @Inject
    ScheduleService scheduleService;

    @Test
    @Transactional
    void exportCarriesEveryGroupCalendarRuleRow() {
        StudentGroup group = new StudentGroup();
        group.name = "TASK-454 probe group";
        group.persist();
        GroupCalendarRule rule = new GroupCalendarRule(group, DayOfWeek.THURSDAY, 0, 4);
        rule.persist();

        SchoolSchedule export = scheduleService.getFullScheduleForExport();

        assertTrue(export.getGroupCalendarRules().stream().anyMatch(r -> r.id != null && r.id.equals(rule.id)),
                "a persisted GroupCalendarRule must ride in the export payload (the TASK-454 bug was this list being always empty)");
        assertEquals(GroupCalendarRule.count(), export.getGroupCalendarRules().size(),
                "export must carry every row in the table, not a filtered subset");
    }

    @Test
    @Transactional
    void exportStillCarriesTheQuotaRows() {
        StudentGroup group = new StudentGroup();
        group.name = "TASK-454 quota probe group";
        group.persist();
        new DailyActivityQuota(group, LocalDate.of(2026, 9, 16), 5, 0, 0).persist();

        SchoolSchedule export = scheduleService.getFullScheduleForExport();

        assertTrue(export.getDailyActivityQuotas().size() >= 1,
                "regression pin: the quota carriage added by TASK-381 must stay");
    }
}
