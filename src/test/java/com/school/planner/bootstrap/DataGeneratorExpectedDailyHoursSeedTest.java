package com.school.planner.bootstrap;

import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.StudentGroup;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link DataGenerator#generateExpectedDailyHours()} (TASK-385)
 * directly against a minimal (StudentGroup-only) fixture - same rationale as
 * {@link DataGeneratorDailyActivityQuotaSeedTest}: {@code
 * calendario_class_days.csv} is fixed on the classpath (no test-injectable
 * variant), so the real data does the work; only the target StudentGroup
 * rows need seeding. Per-group totals independently re-derived from the raw
 * CSV via a standalone Python pass (not just re-reading the Java parser).
 */
@QuarkusTest
class DataGeneratorExpectedDailyHoursSeedTest {

    @Inject
    DataGenerator dataGenerator;

    @Inject
    EntityManager em;

    private static final String[] ALL_GROUP_NAMES = {
            "G01", "G02", "G03", "G04", "G05", "G06", "G07", "G08",
            "G09", "G10", "G11", "G12", "G13", "G14"
    };

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        em.createQuery("DELETE FROM DailyActivityQuota").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
    }

    @Transactional
    void seedAllGroups() {
        for (String name : ALL_GROUP_NAMES) {
            new StudentGroup(name, 20).persist();
        }
    }

    @Test
    @Transactional
    void persists1737InHorizonRowsAcrossTheElevenNonFCGroups() {
        seedAllGroups();

        dataGenerator.generateExpectedDailyHours();

        // 1739 until the TASK-550-family G09 cure (27/08): the sheet declared 670h
        // against a 666h pool; the 4h phantom came off the year-end rows the golden
        // itself leaves empty (04/06 5->4, 07/06 row dropped) - one entity fewer.
        // 1738 until the golden-first cure F1 (28/08): the remaining 04/06 quota-4 row
        // went too (the golden leaves that whole day empty: assume the golden, fix the
        // sheet, notify referente a posteriori) - one more entity fewer.
        assertEquals(1737, DailyActivityQuota.count(),
                "any-hours>0 (group, date) entities within the Timeslot horizon (2026-09-14..2027-06-10), 11 groups"
                        + " (FC excluded). 1,880 until TASK-462: G14 then carried G01's copied 171-day calendar"
                        + " (990h); the real 'G14 2' sheet has 30 in-horizon days (120h), 5 of them split aula+lab"
                        + " and aggregated into one entity each");
    }

    @Test
    @Transactional
    void FCGroupsAreNeverSeededByThisMethodEvenWhenPresent() {
        seedAllGroups();

        dataGenerator.generateExpectedDailyHours();

        assertEquals(0, DailyActivityQuota.count("studentGroup.name", "G04"), "G04: already covered by calendario_fc.csv");
        assertEquals(0, DailyActivityQuota.count("studentGroup.name", "G08"), "G08: already covered by calendario_fc.csv");
        assertEquals(0, DailyActivityQuota.count("studentGroup.name", "G13"), "G13: already covered by calendario_fc.csv");
    }

    @Test
    @Transactional
    void everyPersistedRowIsNotFlaggedAsAnFCQuotaRow() {
        seedAllGroups();

        dataGenerator.generateExpectedDailyHours();

        long flaggedFC = DailyActivityQuota.<DailyActivityQuota>listAll().stream().filter(q -> q.FCQuota).count();
        assertEquals(0, flaggedFC,
                "TASK-385 rows must carry FCQuota=false - H23/H24/M30/M31/VRP must stay scoped to the original 3 FC groups");
    }

    @Test
    @Transactional
    void perGroupInHorizonTotalsMatchTheSheet() {
        seedAllGroups();

        dataGenerator.generateExpectedDailyHours();

        assertGroupTotals("G01", 990, 0, 0);
        assertGroupTotals("G02", 753, 237, 0);
        assertGroupTotals("G03", 739, 230, 0);
        assertGroupTotals("G05", 990, 0, 0);
        assertGroupTotals("G06", 681, 309, 0);
        assertGroupTotals("G07", 665, 30, 295); // 660/300 until the 18/09 cure: the sheet
        // declared 09/06 as (5,S) but the golden runs that day as ordinary hours (T23
        // Ed.Civica x3 + T25 Scienze x2), so the 5h move stage->aula (golden-first,
        // notify referente; one-line revert if the stage marking was real).
        // 666 until the cure F1 (28/08): the golden leaves 04/06 entirely empty, so its
        // quota-4 aula row went the way of 07/06 (golden-first: assume the practiced
        // pool, correct the sheet, notify referente; one-line revert if the 4h were real).
        assertGroupTotals("G09", 662, 0, 300); // 670 until the 27/08 cure: sheet 670 vs pool 666,
        // the 4h phantom cut from the year-end rows the golden leaves empty (04/06 5->4, 07/06 dropped).
        // The 14-15/06 rows (8+8) cut on 06/09 (TASK-586) were OUT of this horizon: invisible here.
        assertGroupTotals("G10", 446, 220, 300);
        assertGroupTotals("G11", 616, 55, 295);
        assertGroupTotals("G12", 584, 91, 295);
        assertGroupTotals("G14", 70, 50, 0); // 'G14 2' sheet (TASK-462), no longer G01's copied 990
    }

    private void assertGroupTotals(String groupName, int expectedAulaHours, int expectedLabHours, int expectedStageHours) {
        List<DailyActivityQuota> rows = DailyActivityQuota.list("studentGroup.name", groupName);
        int aulaHours = rows.stream().mapToInt(r -> r.aulaHours).sum();
        int labHours = rows.stream().mapToInt(r -> r.labHours).sum();
        int stageHours = rows.stream().mapToInt(r -> r.stageHours).sum();
        assertEquals(expectedAulaHours, aulaHours, groupName + " aula hours");
        assertEquals(expectedLabHours, labHours, groupName + " lab hours");
        assertEquals(expectedStageHours, stageHours, groupName + " stage hours");
    }

    @Test
    @Transactional
    void groupsMissingFromDbAreSilentlySkipped() {
        // No StudentGroup seeded at all - generateExpectedDailyHours must not
        // throw, just find no group to attach rows to.
        dataGenerator.generateExpectedDailyHours();

        assertEquals(0, DailyActivityQuota.count());
    }
}
