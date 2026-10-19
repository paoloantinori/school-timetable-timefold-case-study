package com.school.planner.bootstrap;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.service.GoldenScheduleImportService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-399 — the clean A-vs-B box-clearing discriminator. The TASK-398 laptop A/B left a
 * non-empty box at final solve (run 2: 6 un-taught lessons, hard -6). The laptop's partial
 * check ({@code scripts/analyze/box-feasibility.py}) tested only H1/H3/H7/H11 placement
 * conflicts (all 6 lean A with 109-126 free cells) but could not apply the VRP gate
 * (H20 calendar / H5 availability / H6 holidays / H4 stage / quota), which lives in Java
 * {@link Lesson#getAvailableTimeslots()}. This harness applies the REAL VRP gate plus the
 * placement-conflict checks against the run-2 final grid, answering: does each boxed
 * lesson have at least one VRP-legal, conflict-free cell in the final solution?
 *
 * <p><b>ROUTING:</b> free cells &gt; 0 for all → hypothesis A (LS coverage miss; a
 * single-hour targeted repair clears the box — TASK-401). Any lesson at 0 → hypothesis B
 * (placements/template deadlock — TASK-402).
 *
 * <p><b>Inputs are the committed laptop artifacts</b> (static files → deterministic forever):
 * {@code val398-run2/lessons.txt} (the full final grid, {@code group|date|startHour|
 * subject|teacher|pinned}, 12,295 rows incl. the 2,666 calendario pins) and
 * {@code val398-run2/boxed.txt} (the 6 boxed lessons, {@code group|teacher|subject|...}).
 *
 * <p><b>Fixture fidelity</b> (why no calendario import / no pins are seeded here): the
 * VRP's pinned-slot exclusion (H3 VRP) is a strict subset of this harness's occupancy
 * filter — every pinned lesson is a placed row in lessons.txt, so any cell the VRP would
 * have excluded for a pin is already excluded as group-occupied; the free-cell
 * intersection is identical. The six boxed lessons' groups (G03 / G05 / G02)
 * have no {@code StagePattern} (only 2/G11 + G12 are seeded) and are not FC
 * quota groups, so empty stage-blackout/quota statics are faithful for them. Holidays
 * are structurally absent (no Timeslot rows exist on closures). What IS seeded for real:
 * the full Timeslot horizon, group calendars (H20), teachers with per-teacher
 * {@code maxDailyHours} (H7), and the recurring availability rules expanded to concrete
 * {@code TeacherAvailability} (H5) — via {@link RealSeedFixture}.
 *
 * <p>Note the Python check's {@code tchCap=0} treated teacher caps as unrestricted;
 * this harness applies the real {@link Teacher#getEffectiveMaxDailyHours()} (default 6),
 * so its free-cell counts are stricter than (and supersede) the Python's 109-126.
 */
@QuarkusTest
class BoxVrpFeasibilityDiscriminatorTest {

    private static final Path RUN2_DIR =
            Path.of("claudedocs", "spalmone-2026-27", "task398-results", "val398-run2");

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    /**
     * TASK-410 A/B follow-up (2026-07-24): the cumulative run left 9-11 boxed lessons,
     * ~all T01/G06 (Q22-confined to Thursdays). Same discriminator against the
     * val410-run2 final grid, WITH the Q22 rules (that artifact's world): free cells
     * exist -> repair/search gap (extend BoxRepair, e.g. swaps); none -> the grid
     * saturated G06's Thursdays and eviction/swap is structurally required.
     */
    @Test
    @Timeout(value = 300)
    @Transactional
    void val410BoxedLessonsDiscriminator() throws Exception {
        runDiscriminator(Path.of("claudedocs", "spalmone-2026-27", "task410-results", "val410-run2"),
                12290, 11, true, false);
    }

    @Test
    @Timeout(value = 300)
    @Transactional
    void run2BoxedLessonsHaveVrpLegalConflictFreeCells() throws Exception {
        Path lessonsFile = RUN2_DIR.resolve("lessons.txt");
        Path boxedFile = RUN2_DIR.resolve("boxed.txt");
        List<SnapshotFixture.PlacedRow> grid = SnapshotFixture.parsePlacedRows(lessonsFile);
        assertEquals(12295, grid.size(), "run-2 final grid row count (static artifact)");
        List<SnapshotFixture.BoxedRow> boxed = SnapshotFixture.parseBoxedRows(boxedFile);
        assertEquals(6, boxed.size(), "run-2 box size (static artifact)");

        // ── Occupancy + daily counts from the final grid. Pseudo-teacher (Stage) rows
        // occupy the GROUP (H3/H11 count them) but not the teacher (H1/H7 exempt pseudo). ──
        Set<String> teacherBusy = new HashSet<>();
        Set<String> groupBusy = new HashSet<>();
        Map<String, Integer> teacherDaily = new HashMap<>();
        Map<String, Integer> groupDaily = new HashMap<>();
        for (SnapshotFixture.PlacedRow r : grid) {
            groupBusy.add(r.group() + "@" + r.date() + "@" + r.hour());
            groupDaily.merge(r.group() + "@" + r.date(), 1, Integer::sum);
            if (!GoldenScheduleImportService.STAGE_NAME.equals(r.teacher())) {
                teacherBusy.add(r.teacher() + "@" + r.date() + "@" + r.hour());
                teacherDaily.merge(r.teacher() + "@" + r.date(), 1, Integer::sum);
            }
        }

        SnapshotFixture.RealSeedStatics seeded = SnapshotFixture.seedAndWireForVrpProbe(availabilityExpansionService, false);
        Map<String, StudentGroup> groups = seeded.groups();
        Map<String, Teacher> teachers = seeded.teachers();
        List<Timeslot> timeslots = seeded.timeslots();

        // ── The discriminator: per boxed lesson, VRP range ∩ conflict-free vs the grid. ──
        Map<String, Integer> freeCellsByBoxed = new LinkedHashMap<>();
        Map<String, Set<LocalDate>> freeDaysByBoxed = new LinkedHashMap<>();
        System.out.println("=== TASK-399 VRP-filtered box-feasibility discriminator (run-2 final grid) ===");
        for (SnapshotFixture.BoxedRow b : boxed) {
            StudentGroup group = groups.get(b.group());
            Teacher teacher = teachers.get(b.teacher());
            assertTrue(group != null && teacher != null, "unknown group/teacher in boxed.txt: " + b);
            Course course = new Course(b.subject(), teacher, group, 1);
            course.persist();
            Lesson probe = new Lesson(course);

            List<Timeslot> vrp = probe.getAvailableTimeslots();
            int free = 0;
            Set<LocalDate> freeDays = new TreeSet<>();
            List<String> samples = new ArrayList<>();
            int teacherCap = teacher.getEffectiveMaxDailyHours();
            Integer groupCap = group.maxHoursPerDay;
            for (Timeslot ts : vrp) {
                LocalDate date = ts.getDate();
                int hour = ts.startDateTime.getHour();
                if (teacherBusy.contains(b.teacher() + "@" + date + "@" + hour)) continue;
                if (groupBusy.contains(b.group() + "@" + date + "@" + hour)) continue;
                if (groupCap != null && groupCap > 0
                        && groupDaily.getOrDefault(b.group() + "@" + date, 0) >= groupCap) continue;
                if (teacherCap > 0
                        && teacherDaily.getOrDefault(b.teacher() + "@" + date, 0) >= teacherCap) continue;
                free++;
                freeDays.add(date);
                if (samples.size() < 5) samples.add(date + "@" + hour);
            }
            String key = b.group() + "|" + b.teacher() + "|" + b.subject();
            freeCellsByBoxed.merge(key, free, Integer::min);
            freeDaysByBoxed.computeIfAbsent(key, k -> new TreeSet<>()).addAll(freeDays);
            System.out.printf("  %-8s | %-13s | %-25s | VRP-legal %4d | free %3d | days %3d | e.g. %s%n",
                    b.group(), b.teacher(), b.subject(), vrp.size(), free, freeDays.size(), samples);
        }

        // ── Verdict. The G02 T02/Storia course is boxed TWICE: the pair needs
        // two cells on distinct days (same-day pairs would contend for the same daily-cap
        // headroom; distinct days are independently placeable). ──
        boolean allHaveFree = freeCellsByBoxed.values().stream().allMatch(c -> c > 0);
        Set<LocalDate> T02Days = freeDaysByBoxed.get("G02|T02|Storia");
        boolean pairFeasible = T02Days != null && T02Days.size() >= 2;
        System.out.printf("VERDICT: %s (all boxed have free cells: %b; T02 pair has >=2 distinct free days: %b)%n",
                allHaveFree && pairFeasible ? "A - LS coverage miss (feasible cells exist)" : "B - deadlocked",
                allHaveFree, pairFeasible);

        // Pinned outcome (static artifacts → deterministic): every boxed lesson has
        // VRP-legal conflict-free cells in the final grid → hypothesis A (TASK-401).
        assertTrue(allHaveFree, "expected hypothesis A: every boxed lesson has a VRP-legal conflict-free cell");
        assertTrue(pairFeasible, "expected the duplicated T02/Storia pair to fit on >=2 distinct days");
    }

    /** Generalized discriminator body (exploratory: prints per-lesson free cells; asserts only artifact shape). */
    private void runDiscriminator(Path dir, int expectedRows, int expectedBoxed,
                                  boolean includeQ22Rules, boolean assertAllHaveFree) throws Exception {
        List<SnapshotFixture.PlacedRow> grid = SnapshotFixture.parsePlacedRows(dir.resolve("lessons.txt"));
        assertEquals(expectedRows, grid.size(), "final grid row count (static artifact)");
        List<SnapshotFixture.BoxedRow> boxed = SnapshotFixture.parseBoxedRows(dir.resolve("boxed.txt"));
        assertEquals(expectedBoxed, boxed.size(), "box size (static artifact)");

        Set<String> teacherBusy = new HashSet<>();
        Set<String> groupBusy = new HashSet<>();
        Map<String, Integer> teacherDaily = new HashMap<>();
        Map<String, Integer> groupDaily = new HashMap<>();
        for (SnapshotFixture.PlacedRow r : grid) {
            groupBusy.add(r.group() + "@" + r.date() + "@" + r.hour());
            groupDaily.merge(r.group() + "@" + r.date(), 1, Integer::sum);
            if (!GoldenScheduleImportService.STAGE_NAME.equals(r.teacher())) {
                teacherBusy.add(r.teacher() + "@" + r.date() + "@" + r.hour());
                teacherDaily.merge(r.teacher() + "@" + r.date(), 1, Integer::sum);
            }
        }
        SnapshotFixture.RealSeedStatics seeded =
                SnapshotFixture.seedAndWireForVrpProbe(availabilityExpansionService, includeQ22Rules);
        Map<String, StudentGroup> groups = seeded.groups();
        Map<String, Teacher> teachers = seeded.teachers();

        System.out.println("=== VRP-filtered box-feasibility discriminator (" + dir + ") ===");
        boolean allHaveFree = true;
        int idx = 0;
        for (SnapshotFixture.BoxedRow b : boxed) {
            StudentGroup group = groups.get(b.group());
            Teacher teacher = teachers.get(b.teacher());
            assertTrue(group != null && teacher != null, "unknown group/teacher in boxed.txt: " + b);
            Course course = new Course(b.subject(), teacher, group, 1);
            course.id = (long) (90_000 + idx++);
            Lesson probe = new Lesson(course);
            List<Timeslot> vrp = probe.getAvailableTimeslots();
            int free = 0;
            Set<LocalDate> freeDays = new TreeSet<>();
            List<String> samples = new ArrayList<>();
            int teacherCap = teacher.getEffectiveMaxDailyHours();
            Integer groupCap = group.maxHoursPerDay;
            for (Timeslot ts : vrp) {
                LocalDate date = ts.getDate();
                int hour = ts.startDateTime.getHour();
                if (teacherBusy.contains(b.teacher() + "@" + date + "@" + hour)) continue;
                if (groupBusy.contains(b.group() + "@" + date + "@" + hour)) continue;
                if (groupCap != null && groupCap > 0
                        && groupDaily.getOrDefault(b.group() + "@" + date, 0) >= groupCap) continue;
                if (teacherCap > 0
                        && teacherDaily.getOrDefault(b.teacher() + "@" + date, 0) >= teacherCap) continue;
                free++;
                freeDays.add(date);
                if (samples.size() < 6) samples.add(date + "@" + hour);
            }
            if (free == 0) allHaveFree = false;
            System.out.printf("  %-8s | %-13s | %-30s | VRP-legal %4d | free %3d | days %3d | e.g. %s%n",
                    b.group(), b.teacher(), b.subject(), vrp.size(), free, freeDays.size(), samples);
        }
        System.out.println("VERDICT: " + (allHaveFree ? "A - feasible cells exist (repair/search gap)"
                : "B - some lessons have NO free cell (swap/eviction structurally required)"));
        if (assertAllHaveFree) {
            assertTrue(allHaveFree, "expected hypothesis A for every boxed lesson");
        }
    }
}
