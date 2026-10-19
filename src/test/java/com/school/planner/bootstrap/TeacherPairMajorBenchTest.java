package com.school.planner.bootstrap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.solver.SolutionManager;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.TimeslotSequence;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-577 FASE A BENCH (teacher-centric, 9/09): the golden's shape is the
 * (teacher, group) PAIR's compact weekly cell package (170 pairs with >=20h:
 * median 7 (weekday, slot) cells holding 80% of the pair's hours). The flag
 * {@code planner.template.pair-major.enabled} re-keys the template builder's
 * course ordering AND weekday rotation to the pair, so the pair's courses
 * rotate as one unit. Pre-registered: ON raises the median pair top-3 cell
 * concentration by >= 5pp vs OFF; DEATH if H1 (note the trailing space in the
 * prefix: plain "H1" would also match H11) grows, or empty declared cells
 * exceed OFF + 5, or boxed exceeds OFF + 30. The golden's own median is
 * printed as the reference (expected ~0.80).
 *
 * <p>Gated by {@code PLANNER_TEST_PAIRMAJOR_BENCH=1}.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PAIRMAJOR_BENCH", matches = ".+")
class TeacherPairMajorBenchTest {

    @Inject EntityManager em;
    @Inject com.school.planner.service.LessonGenerator lessonGenerator;
    @Inject DataGenerator dataGenerator;
    @Inject com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
        System.clearProperty("planner.template.pair-major.enabled");
        System.clearProperty("planner.construction.grasp.enabled");
    }

    @Test
    @Timeout(value = 3600)
    // NO @Transactional on the method (the PinDrainBench JTA-timeout lesson).
    void pairMajorBench() {
        System.out.println("=== PAIR-MAJOR BENCH (pre-registered: median top-3 concentration +>=5pp; death on H1/empties+5/boxed+30) ===");
        seedRealSeedFixture();
        var wires = wireStatics();
        System.out.printf("GOLDEN reference: median top-3 concentration = %.2f (%d pairs >=20h)%n",
                goldenMedianTop3(), goldenPairs.size());

        System.clearProperty("planner.template.pair-major.enabled");
        var off = solveConstruction(wires);
        report(off, wires, "DIAG OFF");

        System.setProperty("planner.template.pair-major.enabled", "true");
        var on = solveConstruction(wires);
        report(on, wires, "DIAG ON");
    }

    /** Median share of a pair's hours held by its 3 busiest (weekday, slot) cells, pairs with >=20h. */
    private void pairConcentration(SchoolSchedule schedule) {
        Map<String, Map<String, int[]>> pairCells = new HashMap<>();
        Map<String, int[]> pairHours = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) continue;
            var t = l.getTeacher();
            if (t == null || t.pseudo) continue;
            String pair = t.name + "|" + l.course.studentGroup.name;
            String cell = l.timeslot.getDate().getDayOfWeek() + "|"
                    + TimeslotSequence.slotIndexOf(l.timeslot);
            pairCells.computeIfAbsent(pair, k -> new HashMap<>())
                    .computeIfAbsent(cell, k -> new int[]{0})[0]++;
            pairHours.computeIfAbsent(pair, k -> new int[]{0})[0]++;
        }
        List<Double> shares = new ArrayList<>();
        for (var e : pairHours.entrySet()) {
            if (e.getValue()[0] < 20) continue;
            List<Integer> counts = new ArrayList<>();
            pairCells.get(e.getKey()).values().forEach(c -> counts.add(c[0]));
            counts.sort(java.util.Comparator.reverseOrder());
            int top3 = counts.stream().limit(3).mapToInt(Integer::intValue).sum();
            shares.add(top3 / (double) e.getValue()[0]);
        }
        shares.sort(Double::compare);
        double median = shares.isEmpty() ? 0 : shares.get(shares.size() / 2);
        System.out.printf("    pair concentration: median=%.2f over %d pairs >=20h (golden ref ~0.80), distinte celle median=%s%n",
                median, shares.size(),
                shares.isEmpty() ? "n/a" : String.valueOf(medianCellCount(pairCells, pairHours)));
    }

    private int medianCellCount(Map<String, Map<String, int[]>> pairCells, Map<String, int[]> pairHours) {
        List<Integer> sizes = new ArrayList<>();
        for (var e : pairHours.entrySet()) {
            if (e.getValue()[0] >= 20) sizes.add(pairCells.get(e.getKey()).size());
        }
        sizes.sort(Integer::compare);
        return sizes.isEmpty() ? -1 : sizes.get(sizes.size() / 2);
    }

    private final List<Double> goldenPairs = new ArrayList<>();

    private double goldenMedianTop3() {
        Map<String, Map<String, int[]>> cells = new HashMap<>();
        Map<String, int[]> hours = new HashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("lessons.csv", 7)) {
            if ("Stage".equals(row[5].trim()) || "Fasullo".equals(row[5].trim())) continue;
            String pair = row[5].trim() + "|" + row[4].trim();
            cells.computeIfAbsent(pair, k -> new HashMap<>())
                    .computeIfAbsent(row[1].trim() + "|" + row[2].trim(), k -> new int[]{0})[0]++;
            hours.computeIfAbsent(pair, k -> new int[]{0})[0]++;
        }
        for (var e : hours.entrySet()) {
            if (e.getValue()[0] < 20) continue;
            List<Integer> counts = new ArrayList<>();
            cells.get(e.getKey()).values().forEach(c -> counts.add(c[0]));
            counts.sort(java.util.Comparator.reverseOrder());
            goldenPairs.add(counts.stream().limit(3).mapToInt(Integer::intValue).sum() / (double) e.getValue()[0]);
        }
        goldenPairs.sort(Double::compare);
        return goldenPairs.isEmpty() ? 0 : goldenPairs.get(goldenPairs.size() / 2);
    }

    private void report(SchoolSchedule schedule, Wires w, String label) {
        Map<Long, Set<java.time.LocalDate>> placedByGroup = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) continue;
            var t = l.getTeacher();
            if (t == null || t.pseudo) continue;
            placedByGroup.computeIfAbsent(l.course.studentGroup.id, k -> new HashSet<>())
                    .add(l.timeslot.getDate());
        }
        Set<java.time.LocalDate> grid = new HashSet<>();
        for (Timeslot t : w.timeslots()) grid.add(t.getDate());
        Map<String, Integer> emptiesByGroup = new TreeMap<>(
                SnapshotFixture.emptyDeclaredCellsByGroup(placedByGroup, w.quotas(), grid, false));
        int empties = emptiesByGroup.values().stream().mapToInt(Integer::intValue).sum();
        int declaredCells = 0;
        for (DailyActivityQuota q : w.quotas()) {
            if (q.aulaHours + q.labHours <= 0 || !grid.contains(q.date)) continue;
            declaredCells++;
        }
        long boxed = schedule.getLessons().stream().filter(l -> l.timeslot == null).count();
        System.out.printf("%s: empties=%d/%d boxed=%d H34=%d H33=%d H1=%d breakdown=%s%n",
                label, empties, declaredCells, boxed,
                SnapshotFixture.providerCount(solutionManager, schedule, "H34"), SnapshotFixture.providerCount(solutionManager, schedule, "H33"),
                SnapshotFixture.providerCount(solutionManager, schedule, "H1 "), emptiesByGroup);
        pairConcentration(schedule);
        assertTrue(declaredCells > 0, "the census must see declared cells");
    }

    private SchoolSchedule solveConstruction(Wires w) {
        return SnapshotFixture.solveConstructionOnly(Lesson.listAll(),
                new SnapshotFixture.BenchWires(w.timeslots(), w.groups(), w.teachers(),
                        w.courses(), w.availabilities(), w.quotas(), List.of()),
                false, null);
    }

    private record Wires(List<Timeslot> timeslots, List<StudentGroup> groups,
                         List<Teacher> teachers, List<Course> courses,
                         List<com.school.planner.domain.TeacherAvailability> availabilities,
                         List<DailyActivityQuota> quotas) {
    }

    private Wires wireStatics() {
        List<com.school.planner.domain.Timeslot> timeslots = com.school.planner.domain.Timeslot.listAll();
        List<com.school.planner.domain.StudentGroup> groups = com.school.planner.domain.StudentGroup.listAll();
        List<com.school.planner.domain.Lesson> lessons = com.school.planner.domain.Lesson.listAll();
        List<com.school.planner.domain.Teacher> teachers = com.school.planner.domain.Teacher.listAll();
        List<com.school.planner.domain.Course> courses = com.school.planner.domain.Course.listAll();
        List<com.school.planner.domain.TeacherAvailability> availabilities =
                com.school.planner.domain.TeacherAvailability.listAll();
        var w = SnapshotFixture.wireConstructionStatics(timeslots, groups, lessons, teachers,
                courses, availabilities, List.of(), List.of(), List.of(), null, true, true);
        return new Wires(w.timeslots(), w.groups(), w.teachers(), w.courses(),
                w.availabilities(), w.quotas());
    }


    @Transactional
    void seedRealSeedFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers = new java.util.LinkedHashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("teachers.csv", 6)) {
            var t = new Teacher(row[0].trim());
            String maxH = row[3].trim();
            t.maxDailyHours = maxH.isEmpty() ? null : Integer.valueOf(maxH);
            t.persist();
            teachers.put(t.name, t);
        }
        SnapshotFixture.seedCoursesFromIncarichi(teachers, groups, lessonGenerator, null);
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
        for (String[] row : DataGenerator.loadSeedCsv("availability_rules.csv", 8)) {
            var teacher = teachers.get(row[0].trim());
            if (teacher == null) continue;
            new com.school.planner.domain.RecurringAvailabilityRule(teacher,
                    java.time.DayOfWeek.valueOf(row[1].trim()),
                    Integer.parseInt(row[2].trim()), Integer.parseInt(row[3].trim()),
                    com.school.planner.domain.AvailabilityType.valueOf(row[4].trim()),
                    row[5].isBlank() ? null : java.time.LocalDate.parse(row[5].trim()),
                    row[6].isBlank() ? null : java.time.LocalDate.parse(row[6].trim()), row[7].trim())
                    .persist();
        }
        availabilityExpansionService.expandAll();
    }
}
