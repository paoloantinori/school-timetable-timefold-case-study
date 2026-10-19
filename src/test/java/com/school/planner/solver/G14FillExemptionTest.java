package com.school.planner.solver;

import static com.school.planner.TestDataBuilder.dailyActivityQuota;
import static com.school.planner.TestDataBuilder.group;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;

/**
 * TASK-496: the fill-family G14 exemption flag. Default (unset) keeps the ratified
 * behaviour (exempt); an explicit {@code false} includes G14 in the day-fill family.
 * Verified at the quota level (the single target source) with the same fixture shape
 * MarginalDeficitCostTest uses: G14 quota reads 0 when exempt, its real quota when
 * included, and a non-G14 group is untouched in both modes.
 */
class G14FillExemptionTest {

    private static final LocalDate THU = LocalDate.of(2026, 9, 17);

    private StudentGroup g14;
    private StudentGroup regular;

    @BeforeEach
    void setUp() {
        g14 = group(1, "G14", 15);
        regular = group(2, "G08", 20);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                dailyActivityQuota(g14, THU, 5, 0, 0, false),
                dailyActivityQuota(regular, THU, 6, 0, 0, false)));
        System.clearProperty("planner.constraints.g14-fill-exempt.enabled");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("planner.constraints.g14-fill-exempt.enabled");
        SchoolSchedule.clearStaticState();
    }

    @Test
    void defaultIncludesG14SinceTheRemoval() {
        assertFalse(SchoolScheduleConstraintProvider.G14FillExemptEnabled(),
                "TASK-496 removal (2026-08-16): unset property reads NOT exempt (G14 in the fill family)");
        assertEquals(5, SchoolScheduleConstraintProvider.m20Quota(g14, THU),
                "the real (TASK-462) quota is now the target by default");
        assertEquals(6, SchoolScheduleConstraintProvider.m20Quota(regular, THU));
    }

    @Test
    void explicitTrueRestoresTheLegacyExemption() {
        System.setProperty("planner.constraints.g14-fill-exempt.enabled", "true");
        assertTrue(SchoolScheduleConstraintProvider.G14FillExemptEnabled());
        assertEquals(0, SchoolScheduleConstraintProvider.m20Quota(g14, THU),
                "rollback path: explicit true re-enables the exemption");
        assertEquals(6, SchoolScheduleConstraintProvider.m20Quota(regular, THU));
    }
}
