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
 * Tests {@link DataGenerator#generateDailyActivityQuotas()} (TASK-381
 * "L1-bis") directly against a minimal (StudentGroup-only) fixture - same
 * rationale as {@code CalendarioImportServiceTest}'s real-CSV-driven tests:
 * {@code calendario_fc.csv} is fixed on the classpath (no test-injectable
 * variant, see {@code SeedCsv#load}), so the real data does the work; only
 * the 3 target StudentGroup rows need seeding. Counts independently verified
 * against the raw CSV in the TASK-381 task brief (Python re-derivation).
 */
@QuarkusTest
class DataGeneratorDailyActivityQuotaSeedTest {

    @Inject
    DataGenerator dataGenerator;

    @Inject
    EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        em.createQuery("DELETE FROM DailyActivityQuota").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
    }

    @Transactional
    void seedTargetGroups() {
        new StudentGroup("G04", 20).persist();
        new StudentGroup("G08", 20).persist();
        new StudentGroup("G13", 20).persist();
    }

    @Test
    @Transactional
    void persists513InHorizonRows() {
        seedTargetGroups();

        dataGenerator.generateDailyActivityQuotas();

        assertEquals(520, DailyActivityQuota.count(), // TASK-550 ext: +5 golden-mirrored (06-11/14/15)
                "any-hours>0 rows within the Timeslot horizon (2026-09-14..2027-06-10)");
    }

    @Test
    @Transactional
    void perGroupInHorizonTotalsMatchTheSheet() {
        seedTargetGroups();

        dataGenerator.generateDailyActivityQuotas();

        assertGroupTotals("G04", 628, 212, 240); // TASK-550 ext: +5 aula (06-15), +16 stage (06-11/14)
        assertGroupTotals("G08", 569, 107, 300); // TASK-550 ext: +10 aula (06-14/15 RA golden-mirrored); 2026-09-01 golden-first fix, 4 sheet typos on the stage calendar: 03-01/02 said aula/lab where the year's stage starts (golden: 8h stage) and 04-23/26 said stage where the school ran normal lessons (golden: 5h aula / 5h lab). Net: -1 aula +1 lab, stage back to exactly 300
        assertGroupTotals("G13", 473, 210, 295);
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
        // No StudentGroup seeded at all - generateDailyActivityQuotas must not
        // throw, just find no group to attach quota rows to.
        dataGenerator.generateDailyActivityQuotas();

        assertEquals(0, DailyActivityQuota.count());
    }
}
