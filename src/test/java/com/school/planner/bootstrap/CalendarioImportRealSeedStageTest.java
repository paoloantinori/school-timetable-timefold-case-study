package com.school.planner.bootstrap;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.StudentGroup;
import com.school.planner.service.CalendarioImportReport;
import com.school.planner.service.CalendarioImportService;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.testsupport.ModaCourseFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration-style test of TASK-381 "stage on the grid" against the REAL
 * seed CSVs (not a hand-picked fixture): {@code incarichi.csv} (via {@link
 * DataGenerator#generateStageCourses}), {@code calendario_fc.csv} (via
 * {@link DataGenerator#generateDailyActivityQuotas}), and {@code
 * calendario_class_days.csv}/{@code calendario_moda.csv} (via {@link
 * CalendarioImportService#importCalendario}) - the three real sheets
 * together drive the full stage-pinning plan for all 7 Stage-course groups
 * (G07, G11, G08, G10, G12, G09, G13).
 *
 * <p>Placed in {@code com.school.planner.bootstrap} (not {@code
 * com.school.planner.service}, where {@code CalendarioImportServiceTest}
 * lives) because it needs package-private access to {@link
 * DataGenerator#generateStageCourses}/{@link
 * DataGenerator#generateDailyActivityQuotas}/{@code loadSeedCsv} (called
 * directly by this class) without risking the H2 cross-test contamination a
 * real {@code onStartup()} call causes (see {@code DataGeneratorSeedTest}'s
 * own javadoc). The other package-private replication this fixture needs to
 * faithfully mirror {@code generateTimeslots}/{@code
 * generateGroupCalendarRules} ({@code computeSchoolDays}/{@code
 * STANDARD_SLOTS}/{@code CLOSURE_DATES_2026_27}/{@code
 * GROUP_CALENDAR_PATTERNS}/{@code parseCalendarCell}, both real methods being
 * {@code private} and off-limits per {@code DataGenerator.java}'s protected-
 * seed-data rule) now flows through the shared {@link RealSeedFixture}
 * instead of being called by this class directly.</p>
 *
 * <p>MODA lab courses (T17/T16/T07) are seeded with the exact
 * real incarichi.csv hours already established by {@code
 * CalendarioImportServiceTest#seedFullYearFixture} (needed only so {@code
 * planModaPins} succeeds with zero failures - the all-or-nothing persist
 * gate in {@code importCalendario} would otherwise refuse the WHOLE import,
 * Stage pins included, if the MODA plan alone failed). 2027-06-09 is
 * excluded from the Timeslot generation for the same reason that fixture
 * excludes it (the one known T16/T17 mixed-shift conflict) -
 * mechanically verified (see this class's own {@code
 * excludingJune9DoesNotAffectAnyStageGroup} test) to have zero effect on any
 * of the 7 Stage groups' numbers below: post CURA INPUT 18/09 (4f9dbc07) NO
 * group carries stage demand that day at all (G07's row became a 5h
 * A-row, the golden's ordinary day), and G07's whole sheet stage already
 * totals exactly its 295h incarichi budget regardless of whether
 * 2027-06-09 has a real Timeslot or not.</p>
 *
 * <p>Expected numbers independently derived (Python re-derivation over the
 * raw CSVs, cross-checked against this test's own live JUnit assertions -
 * see the task brief this test implements): pins G07 295 / G11 287 / 3
 * FA 228 / G12 112 / G09 292 / G08 276 / G13 295 = 1,785; off-grid
 * (expectedUnscheduledHours) 0/8/72/183/8/24/0 = 295; beyond-budget skip 0
 * (post-cura 4f9dbc07: G07's sheet stage is 295h = the incarichi budget,
 * the old 5h gap is closed).</p>
 *
 * <p><b>TASK-408 (2026-07-25):</b> the 2027-01-22 closure typo fix
 * (DataGenerator, 2026-01-22 -&gt; 2027-01-22, golden 100% closed that Friday)
 * removed that date's Timeslots, so its two stage S-rows (G11 8h + G12 7h
 * in calendario_class_days.csv) moved from on-grid PINS to OFF-GRID hours
 * (the same closure-date accounting the Vacanze di Natale dates already use):
 * G11 295/0 -&gt; 287/8, G12 119/176 -&gt; 112/183, aggregate pinned
 * 1,800 -&gt; 1,785 / off-grid 280 -&gt; 295. The conservation identity
 * (pinned + offGrid == min(sheet, budget)) is unchanged (295 each).</p>
 *
 * <p><b>Bug this test caught (fixed before this test was finalized):</b> an
 * earlier version of {@code CalendarioImportService#planStagePins} applied
 * the on-grid {@code GroupCalendarRule} window cap to off-grid (no-Timeslot)
 * asks too - for G12, 3 Vacanze di Natale closure dates
 * (2026-12-23/29/30, Tue/Wed) ask 7h each against its 5-slot Mon-Wed
 * calendar pattern, so the buggy code lost 6h to {@code
 * stageSkippedCalendarClosed} instead of drawing them as off-grid (274h
 * total off-grid, not 280h) and left 6 stub Lessons permanently unconsumed.
 * This test's first run against the real seed failed on exactly that 274-vs
 * -280 mismatch, which is what led to the day-level {@code
 * dateHasAnyTimeslot} branch in {@code planStagePins} - see that method's
 * "off-grid hours draw budget too" javadoc.</p>
 */
@QuarkusTest
class CalendarioImportRealSeedStageTest {

    @Inject
    DataGenerator dataGenerator;

    @Inject
    CalendarioImportService importService;

    @Inject
    EntityManager em;

    private static final LocalDate EXCLUDED_DATE = LocalDate.of(2027, 6, 9);

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM DailyActivityQuota").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
    }

    @Transactional
    Map<String, StudentGroup> seedRealSeedFixture() {
        // Real school-year Timeslots, all 14 real StudentGroups (groups.csv),
        // and GroupCalendarRule (with the Monday ramp) - shared core extracted
        // to RealSeedFixture (see its own javadoc), minus the one known MODA
        // mixed-shift conflict date (see class javadoc).
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(EXCLUDED_DATE, null);

        // MODA lab courses (T17/T16/T07) - real incarichi.csv
        // hours, shared with CalendarioImportServiceTest#seedFullYearFixture
        // via ModaCourseFixtures; needed only so planModaPins succeeds (see
        // class javadoc).
        ModaCourseFixtures.persistRealModaLabCourses(
                groups.get("G01"), groups.get("G05"), groups.get("G09"));

        // The one authorized DataGenerator addition (TASK-381, maintainer
        // 2026-07-20): seeds all 7 Stage pseudo-courses from incarichi.csv.
        dataGenerator.generateStageCourses();
        // DailyActivityQuota for G04/G08/G13 from calendario_fc.csv.
        dataGenerator.generateDailyActivityQuotas();

        return groups;
    }

    @Test
    @Transactional
    void realSeed_stagePinsMatchExpectedNumbersAndConservationIdentity() {
        Map<String, StudentGroup> groups = seedRealSeedFixture();

        CalendarioImportReport report = importService.importCalendario(false, false);
        assertTrue(report.persisted, "expected a clean MODA import (Stage pins ride on the same all-or-nothing gate): "
                + report.modaFailures);

        // ── Aggregate report counters ──
        // TASK-550 cure (a) golden-open density (maintainer 24/08): the stub budget per group
        // is now the count the golden HELD, so the phantom share of the old 335 off-grid
        // (311 hours the golden itself marks closed=true = not held) is no longer seeded
        // at all - it never reaches the import. What remains off-grid is the GENUINE
        // closure/beyond-horizon residue INSIDE the held set: G04's 24 closure dates
        // within the horizon. Pinned is unchanged (1,985: every held hour pins).
        assertEquals(2001, report.stagePinned, "sum of all 8 groups' pinned stage-hours (golden-held set incl. the TASK-550 extension days)");
        assertEquals(8, report.stageOffGridHours, "G04 only: the 8 genuine closure hours (the 16 beyond-horizon ones now pin, TASK-550 extension)");
        assertEquals(0, report.stageSkippedCalendarClosed, "no group's stage ask is ever calendar-window-capped on the real seed");
        // MEASURED (24/08): was G07's 5h sheet/budget gap. The CURA INPUT 18/09
        // (4f9dbc07) migrated G07's 2027-06-09 row (5,S) -> (5,A): the sheet stage
        // now sums to exactly the 295h incarichi budget, so the no-budget skip is gone.
        // The phantom hours generate NO import demand at all: the pin driver is the
        // quota sheets, whose stage rows sum to the held set. The conservation
        // identity closes with no residue.
        assertEquals(0, report.stageSkippedNoBudget, "post-cura 4f9dbc07: sheet stage 295h == incarichi budget 295h");

        // ── Per-group exact numbers (independently derived, see class javadoc) ──
        // TASK-434 (2026-07-28) + fix-16: G04 stage window added (240h golden-faithful,
        // 30 dates 2027-04-26..06-14, pure-stage). The pinning lands 200 on-grid + 40
        // off-grid (pinned+offGrid 240 = the full 240h budget: 24 closure/Saturday within
        // horizon + 16 beyond-horizon on 2027-06-11/06-14, which fix-16 routes through
        // expectedUnscheduledHours instead of leaving them as orphaned H0 stubs).
        // TASK-550: pinned = the golden-held hours (unchanged). expectedUnscheduledHours
        // per group = the SEED-time phantom (sheet - held: 8/72/183/8/24/16/0/0) + the
        // import's genuine off-grid (G04's 24 closure dates within horizon) - which
        // lands on exactly the OLD per-group numbers, now with a different composition:
        // the phantom share was never seeded as stubs, it is declared demand awaiting
        // the school's answer (referente question, cure (c)).
        assertStageCourse("G07", 295, 0);
        assertStageCourse("G11", 287, 8);
        assertStageCourse("G10", 228, 72);
        assertStageCourse("G12", 112, 183);
        assertStageCourse("G09", 292, 8);
        assertStageCourse("G08", 276, 24);
        assertStageCourse("G04", 216, 24); // TASK-550 ext: 16 recovery hours pin on-grid, 24 genuine closures stay off-grid
        assertStageCourse("G13", 295, 0);

        // ── Conservation identity, computed live from the raw CSVs/DB (not
        // from the hardcoded numbers above) - "pinned + offGrid == min(sheet
        // hours, incarichi budget)" for every one of the 7 groups (the
        // min() already accounts for G07's 5h budget shortfall - see the
        // aggregate stageSkippedNoBudget assertion above). ──
        assertConservationIdentity("G07", sheetStageHoursFromClassDays("G07"));
        assertConservationIdentity("G11", sheetStageHoursFromClassDays("G11"));
        assertConservationIdentity("G10", sheetStageHoursFromClassDays("G10"));
        assertConservationIdentity("G12", sheetStageHoursFromClassDays("G12"));
        assertConservationIdentity("G09", sheetStageHoursFromClassDays("G09"));
        assertConservationIdentity("G08", sheetStageHoursFromFCCsv(groups.get("G08")));
        assertConservationIdentity("G04", sheetStageHoursFromFCCsv(groups.get("G04")));
        assertConservationIdentity("G13", sheetStageHoursFromFCCsv(groups.get("G13")));
    }

    private void assertStageCourse(String groupName, int expectedPinned, int expectedOffGrid) {
        Course course = Course.find(
                "teacher.pseudo = true and subject = 'Stage' and studentGroup.name = ?1", groupName).firstResult();
        assertNotNull(course, "expected a Stage course for " + groupName);
        assertEquals(expectedPinned, Lesson.count("course = ?1 and pinned = true", course), groupName + " pinned");
        int offGrid = course.expectedUnscheduledHours == null ? 0 : course.expectedUnscheduledHours;
        assertEquals(expectedOffGrid, offGrid, groupName + " off-grid (expectedUnscheduledHours)");
    }

    /**
     * {@code pinned + offGrid == min(sheetHours, incarichi budget)} - holds
     * for all 7 groups on the real seed since {@code
     * stageSkippedCalendarClosed} is 0 everywhere (asserted above): the only
     * loss mechanism that ever reduces {@code pinned+offGrid} below the raw
     * sheet total is the budget cap itself, which {@code min()} already
     * captures.
     */
    private void assertConservationIdentity(String groupName, int sheetHoursComputedFromCsv) {
        Course course = Course.find(
                "teacher.pseudo = true and subject = 'Stage' and studentGroup.name = ?1", groupName).firstResult();
        assertNotNull(course, groupName);
        long pinned = Lesson.count("course = ?1 and pinned = true", course);
        int offGrid = course.expectedUnscheduledHours == null ? 0 : course.expectedUnscheduledHours;
        // TASK-550: the FULL sheet ask is now the conservation anchor: the course's
        // totalHours is the golden-HELD subset and expectedUnscheduledHours carries the
        // rest (seed-time phantom + import off-grid), so pinned+offGrid must equal the
        // sheet ask (still min()-capped at the incarici sheet row for G07's gap).
        int sheetAsk = Math.min(sheetHoursComputedFromCsv, sheetIncariciStageHours(groupName));
        assertEquals(sheetAsk, pinned + offGrid,
                groupName + ": pinned+offGrid must equal the FULL sheet ask (held on-grid + phantom + genuine off-grid)");
    }

    /** The group's raw incarichi.csv Stage row hours (the uncapped sheet ask). */
    private int sheetIncariciStageHours(String groupName) {
        for (String[] row : DataGenerator.loadSeedCsv("incarichi.csv", 4)) {
            if ("Stage".equals(row[0].trim()) && groupName.equals(row[2].trim())) {
                return (int) Math.round(Double.parseDouble(row[3].trim())); // "295.0" decimals live in the raw sheet
            }
        }
        return Integer.MAX_VALUE;
    }

    /** Sums calendario_class_days.csv's in-horizon modality=S rows for one non-quota group. */
    private int sheetStageHoursFromClassDays(String groupName) {
        int total = 0;
        for (String[] row : DataGenerator.loadSeedCsv("calendario_class_days.csv", 4)) {
            if (!groupName.equals(row[0].trim()) || !"S".equals(row[3].trim())) continue;
            LocalDate date = LocalDate.parse(row[1].trim());
            if (date.isBefore(TimeslotSequence.SCHOOL_YEAR_START) || date.isAfter(TimeslotSequence.SCHOOL_YEAR_END)) continue;
            total += Integer.parseInt(row[2].trim());
        }
        return total;
    }

    /**
     * Sums calendario_fc.csv's stage_hours for one quota group across ALL dates
     * (including the beyond-horizon rows DataGenerator drops), so the conservation
     * identity uses the group's TRUE sheet demand (240h for G04, not the 224h
     * horizon-filtered DailyActivityQuota subset) - the same parity the golden import's
     * own beyond-horizon accounting already establishes.
     */
    private int sheetStageHoursFromFCCsv(StudentGroup group) {
        int total = 0;
        for (String[] row : DataGenerator.loadSeedCsv("calendario_fc.csv", 5)) {
            if (!group.name.equals(row[0].trim())) continue;
            total += (int) Math.round(Double.parseDouble(row[4].trim()));
        }
        return total;
    }

    @Test
    void excludingJune9DoesNotAffectAnyStageGroup() {
        // Mechanical justification for the class javadoc's claim, post CURA INPUT
        // 18/09 (4f9dbc07): 2027-06-09's only G07 row is now an A-row (the 5h
        // ordinary day the golden practices there, T23/T25), so NO group
        // carries stage demand on that date at all, and G07's S-hours over the
        // whole sheet total exactly its 295h incarichi budget - the date's
        // timeslot presence cannot change any stage number.
        List<String[]> rows = DataGenerator.loadSeedCsv("calendario_class_days.csv", 4);
        int totalStageHours = 0;
        boolean june9HasStageRow = false;
        boolean june9HasOrdinaryRow = false;
        for (String[] row : rows) {
            if (!"G07".equals(row[0].trim())) continue;
            LocalDate date = LocalDate.parse(row[1].trim());
            if (date.equals(EXCLUDED_DATE) && "A".equals(row[3].trim())) {
                june9HasOrdinaryRow = true;
                continue;
            }
            if (!"S".equals(row[3].trim())) continue;
            if (date.equals(EXCLUDED_DATE)) {
                june9HasStageRow = true;
                continue;
            }
            totalStageHours += Integer.parseInt(row[2].trim());
        }
        assertTrue(!june9HasStageRow, "post-cura 4f9dbc07: G07's 2027-06-09 row is A, not S");
        assertTrue(june9HasOrdinaryRow, "2027-06-09 carries G07's 5h ordinary row (the cure's A)");
        assertEquals(295, totalStageHours, "G07's whole sheet stage now equals the 295h incarichi budget (the old 5h gap is closed)");

        // No OTHER stage group (G11/G10/G12/G09/G08/G13) has any
        // modality=S (or calendario_fc.csv stage_hours>0) row on 2027-06-09.
        for (String groupName : List.of("G11", "G10", "G12", "G09")) {
            boolean hasRow = rows.stream().anyMatch(r -> groupName.equals(r[0].trim())
                    && "2027-06-09".equals(r[1].trim()) && "S".equals(r[3].trim()));
            assertTrue(!hasRow, groupName + " must have no 2027-06-09 stage row");
        }
        List<String[]> FCRows = DataGenerator.loadSeedCsv("calendario_fc.csv", 5);
        for (String groupName : List.of("G08", "G13")) {
            boolean hasStage = FCRows.stream().anyMatch(r -> groupName.equals(r[0].trim())
                    && "2027-06-09".equals(r[1].trim()) && Integer.parseInt(r[4].trim()) > 0);
            assertTrue(!hasStage, groupName + " must have zero stage_hours on 2027-06-09");
        }
    }
}
