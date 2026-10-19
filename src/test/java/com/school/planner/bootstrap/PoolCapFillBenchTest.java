package com.school.planner.bootstrap;

import java.time.LocalDate;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-577 POOL-CAP FILL BENCH (9/09): the corrected census (post-artifact) on
 * the live cycle's POST_CONSTRUCTION found 40 fully-empty declared in-grid days:
 * 15 x G04 (all 9 declared Apr-May Fridays + 6 weekdays, golden serves each
 * at 5h), 10 x G08, 6 x G14 (input-locked, triangolo T16) and 9
 * in-scope H34 tail cells (teacher-locked mag-giu, the surplus-harvest
 * filone). The 1/G08 block is DISTRIBUTION: the totals are right (G04
 * places exactly its 750 pool), but the construction follows the SHEET quota
 * (G04 840 in-grid), front-loads, and starves the tail. The cure under
 * test: {@code planner.construction.pool-cap-fill.enabled} makes {@code
 * expectedHoursCapFor} follow the RATIFIED TASK-497 per-date pool targets
 * (input-derived, the same shape the score-side M20 family already uses).
 * Pre-registered against the LIVE census (40): G04 15 -> ~0, G08 10 -> 0,
 * placed totals unchanged, boxed within control +60. NOTE: this H2 bench runs
 * WITHOUT the pre-assignment pins, so its baseline is NOT the live 40 (the
 * pin-less G12 days inflate it); judge the arms on the pool-cap families
 * and the paired counters, not on the absolute empties total.
 *
 * <p>Gated by {@code PLANNER_TEST_POOLCAP_BENCH=1}.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_POOLCAP_BENCH", matches = ".+")
class PoolCapFillBenchTest {

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
        // clear, non set("false"): il default post-ratifica e' ON, e un residue
        // "false" avvelenerebbe i test successivi nello stesso fork
        System.clearProperty("planner.construction.pool-cap-fill.enabled");
        System.clearProperty("planner.construction.grasp.enabled");
    }

    @Test
    @Timeout(value = 3600)
    // NO @Transactional on the method (the PinDrainBench JTA-timeout lesson).
    void poolCapFillBench() {
        System.out.println("=== POOL-CAP FILL BENCH (post-ratifica: OFF arm e' esplicito, ON e' il default) ===");
        seedRealSeedFixture();
        var wires = wireStatics();

        System.setProperty("planner.construction.pool-cap-fill.enabled", "false");
        var off = solveConstruction(wires);
        report(off, wires, "DIAG OFF");

        System.setProperty("planner.construction.pool-cap-fill.enabled", "true");
        var on = solveConstruction(wires);
        report(on, wires, "DIAG ON");

        // Review 9/09: anche il DEFAULT null->ON va validato direttamente (non
        // solo il letterale "true"): il braccio sotto gira SENZA proprieta'.
        System.clearProperty("planner.construction.pool-cap-fill.enabled");
        var onDefault = solveConstruction(wires);
        report(onDefault, wires, "DIAG ON-default(null)");
    }

    private void report(SchoolSchedule schedule, Wires w, String label) {
        Set<LocalDate> grid = new HashSet<>();
        for (Timeslot t : w.timeslots()) grid.add(t.getDate());
        Map<Long, Set<LocalDate>> placedByGroup = new HashMap<>();
        Map<Long, Integer> placedTotals = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) continue;
            var t = l.getTeacher();
            if (t == null || t.pseudo) continue;
            placedByGroup.computeIfAbsent(l.course.studentGroup.id, k -> new HashSet<>())
                    .add(l.timeslot.getDate());
            placedTotals.merge(l.course.studentGroup.id, 1, Integer::sum);
        }
        Map<String, Integer> emptiesByGroup = new TreeMap<>(
                SnapshotFixture.emptyDeclaredCellsByGroup(placedByGroup, w.quotas(), grid, false));
        int empties = emptiesByGroup.values().stream().mapToInt(Integer::intValue).sum();
        int declaredCells = 0;
        for (DailyActivityQuota q : w.quotas()) {
            if (q.aulaHours + q.labHours <= 0 || !grid.contains(q.date)) continue;
            declaredCells++;
        }
        long boxed = schedule.getLessons().stream().filter(l -> l.timeslot == null).count();
        String fc = w.groups().stream()
                .filter(g -> "G04".equals(g.name) || "G08".equals(g.name))
                .map(g -> String.format("%s=%d", g.name, placedTotals.getOrDefault(g.id, 0)))
                .reduce((a, b) -> a + " " + b).orElse("?");
        System.out.printf("%s: empties=%d/%d boxed=%d H34=%d H33=%d placed[%s] breakdown=%s%n",
                label, empties, declaredCells, boxed,
                SnapshotFixture.providerCount(solutionManager, schedule, "H34"), SnapshotFixture.providerCount(solutionManager, schedule, "H33"), fc, emptiesByGroup);
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
        List<Timeslot> timeslots = Timeslot.listAll();
        List<StudentGroup> groups = StudentGroup.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
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
