package com.school.planner.bootstrap;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.HybridPhase1aService;
import com.school.planner.solver.DeclaredDayPreBooker;
import com.school.planner.solver.SchoolScheduleConstraintProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-590 RESERVE-PATH REJECTION CENSUS (m-14). The live declared-day reserve
 * pass (DayClusteredConstructionPhase.reserveDeclaredDayPlacements) now records
 * a first-walk-stopping classification per declared tail date into
 * {@link DeclaredDayPreBooker#RESERVE_CENSUS}. This bench reproduces the
 * MEASURED protocol's pre-construction state in-test (real seed + production
 * pre-assignment, NO import-calendario: the v-series start state TASK-539.01
 * froze as m14-precon-b, 274 pins), then runs construction twice: the
 * deterministic arm (attempt-1 equivalent) and a GRASP arm (basin challenge,
 * AC#7). Deliverables: per-class census, the four ledger target cells'
 * classification, and the empty/under-quota declared cells after construction.
 *
 * <p>Fixture gate: the pre-assignment must pin exactly 274 lessons (the
 * m14-precon-b / v132 CYCLE_START count). The printed content sha (over
 * group|subject|teacher|date|slot tuples, id-independent) is cross-checked
 * against the laptop fixture at analysis time, not asserted here: raw lesson ids
 * are not portable across H2 and the production Postgres.
 *
 * <p>Gated by {@code PLANNER_TEST_RESERVE_CENSUS=1}.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_RESERVE_CENSUS", matches = ".+")
class ReserveCensusBenchTest {

    /** The four ledger cells (TASK-539.01 residual) the census must speak to. */
    private static final String[] LEDGER_CELLS = {
            "G06|2027-06-10|", "G09|2027-06-03|", "G12|2027-04-16|", "G13|2026-11-06|"
    };

    @Inject EntityManager em;
    @Inject com.school.planner.service.LessonGenerator lessonGenerator;
    @Inject DataGenerator dataGenerator;
    @Inject com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;
    @Inject HybridPhase1aService hybridPhase1aService;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
        DeclaredDayPreBooker.RESERVE_CENSUS.clear();
        DeclaredDayPreBooker.ALLOWANCE_SNAPSHOT.clear();
        DeclaredDayPreBooker.RESERVE_JOURNAL.clear();
        System.clearProperty("planner.construction.pool-cap-fill.enabled");
        System.clearProperty("planner.construction.grasp.enabled");
        System.clearProperty("planner.construction.grasp.seed");
        System.clearProperty(DeclaredDayPreBooker.SUBSTITUTION_FLAG);
    }

    @Test
    @Timeout(value = 3600)
    // NO @Transactional on the method (the PinDrainBench JTA-timeout lesson)
    void reserveCensusBench() {
        System.out.println("=== TASK-590 RESERVE CENSUS BENCH (measured protocol, 274-pin start) ===");
        seedRealSeedFixture();
        var wires = wireStatics();

        // production pre-assignment on the fresh seed (constrained-teacher default ON,
        // LabRotation structurally empty on the 2026/27 seed: the no-op phases skip)
        preparePreAssignment();
        var report = hybridPhase1aService.run();
        assertTrue(report.success, "pre-assign must succeed: " + report.failureAnalysis);
        assertEquals(HybridPhase1aService.HybridStatus.COMPLETE, hybridPhase1aService.getStatus());

        List<Lesson> allLessons = freshLessons();
        System.out.printf("PRE-ASSIGN DIAG: total lessons=%d pinned=%d pinnedAndPlaced=%d%n",
                allLessons.size(),
                allLessons.stream().filter(l -> l.pinned).count(),
                allLessons.stream().filter(l -> l.pinned && l.timeslot != null).count());
        List<Lesson> pinned = allLessons.stream()
                .filter(l -> l.pinned && l.timeslot != null).collect(Collectors.toList());
        System.out.printf("PRE-ASSIGN: pinned+placed=%d content-sha=%s%n",
                pinned.size(), contentSha(pinned));
        assertEquals(274, pinned.size(),
                "fixture gate: the v-series cycle start pins 274 lessons (m14-precon-b)");

        // PRODUCTION FIDELITY (review finding): refreshSolverStatics republishes the
        // pinned-cells static AFTER pre-assign in production (ScheduleService:4128);
        // wireStatics published it while zero lessons were pinned, so without this
        // republish the arms run with the H3 pinned-cell VRP exclusion disarmed and
        // the census measures a protocol variant
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(freshLessons());

        var det = solveConstruction(wires, null);
        var offCells = reportArm(det, wires, "DETERMINISTIC (attempt-1 equivalent)");

        var grasp = solveConstruction(wires, 1);
        var graspOffCells = reportArm(grasp, wires, "GRASP seed=1 (basin challenge)");

        // Phase E prototype arm: substitution ON, same deterministic seed, same input
        System.setProperty(DeclaredDayPreBooker.SUBSTITUTION_FLAG, "true");
        var subst = solveConstruction(wires, null);
        System.clearProperty(DeclaredDayPreBooker.SUBSTITUTION_FLAG);
        var onCells = reportArm(subst, wires, "SUBSTITUTION ON (deterministic)");

        // AC#5/#6 OFF-vs-ON comparison: target cells closed, cells CREATED, pins kept
        var closed = new java.util.TreeSet<>(offCells);
        closed.removeAll(onCells);
        var created = new java.util.TreeSet<>(onCells);
        created.removeAll(offCells);
        System.out.printf("SUBSTITUTION OFF->ON: closed=%d %s%n", closed.size(), closed);
        System.out.printf("SUBSTITUTION OFF->ON: CREATED=%d %s (kill criterion if non-empty)%n",
                created.size(), created);
        long pinsAfter = freshLessons().stream().filter(l -> l.pinned).count();
        System.out.printf("SUBSTITUTION OFF->ON: pinned after=%d (must stay 274)%n", pinsAfter);
        assertEquals(274, pinsAfter, "AC#6: the substitution must not touch pins");
        var substEntries = DeclaredDayPreBooker.RESERVE_CENSUS.stream()
                .filter(l -> l.contains("substitution "))
                .sorted().toList();
        System.out.printf("SUBSTITUTION entries=%d%n", substEntries.size());
        substEntries.forEach(l -> System.out.println("  " + l));
        System.out.printf("AC#6 KILL CRITERION: %s - first-fit substitution %s%n",
                created.isEmpty() ? "PASS" : "TRIPPED",
                created.isEmpty() ? "improves targets without new deficits"
                        : "creates new deficits; rejected in this form (flag stays OFF)");

        // AC#7: the same challenge on the second seed (GRASP basin)
        System.setProperty(DeclaredDayPreBooker.SUBSTITUTION_FLAG, "true");
        var substG = solveConstruction(wires, 1);
        System.clearProperty(DeclaredDayPreBooker.SUBSTITUTION_FLAG);
        var onGCells = reportArm(substG, wires, "SUBSTITUTION ON (GRASP seed=1)");
        var closedG = new java.util.TreeSet<>(graspOffCells);
        closedG.removeAll(onGCells);
        var createdG = new java.util.TreeSet<>(onGCells);
        createdG.removeAll(graspOffCells);
        System.out.printf("SUBSTITUTION GRASP OFF->ON: closed=%d %s CREATED=%d %s%n",
                closedG.size(), closedG, createdG.size(), createdG);
    }

    /**
     * Fresh persistence-context read: the phase's merge commits in ITS transaction, so
     * managed entities held by the test's request-scoped EM keep the pre-pin state
     * (measured: listAll showed pinned=0 while JPQL counted 274). Clear first.
     */
    @Transactional
    List<Lesson> freshLessons() {
        em.clear();
        return em.createQuery("SELECT l FROM Lesson l ORDER BY l.id", Lesson.class).getResultList();
    }

    @Transactional
    void preparePreAssignment() {
        hybridPhase1aService.resetStatus();
        // sibling tests may leave LabRotation rows that survive their own rollback
        com.school.planner.domain.LabRotation.deleteAll();
    }

    /** @return the empty declared cells as group|date strings (the OFF/ON delta base). */
    private java.util.Set<String> reportArm(SchoolSchedule schedule, Wires w, String label) {
        List<String> census = List.copyOf(DeclaredDayPreBooker.RESERVE_CENSUS);
        Map<String, Long> byClass = census.stream()
                .collect(Collectors.groupingBy(l -> l.split("\\|")[2], TreeMap::new, Collectors.counting()));
        System.out.printf("%n%s: census entries=%d by-outcome=%s%n", label, census.size(), byClass);
        System.out.println("--- rejection entries (full) ---");
        census.stream().filter(l -> !l.split("\\|")[2].startsWith("BOOKED"))
                .sorted().forEach(l -> System.out.println("  " + l));
        System.out.println("--- ledger target cells ---");
        for (String cell : LEDGER_CELLS) {
            census.stream().filter(l -> l.startsWith(cell)).findAny()
                    .ifPresentOrElse(l -> System.out.println("  " + l),
                            () -> System.out.println("  " + cell + " -> NOT IN CENSUS (window or walk)"));
        }
        System.out.println("--- allowance snapshots at ledger cells (AC#4 gate) ---");
        for (String cell : LEDGER_CELLS) {
            DeclaredDayPreBooker.ALLOWANCE_SNAPSHOT.stream()
                    .filter(l -> l.startsWith(cell))
                    .forEach(l -> System.out.println("  " + l));
        }
        // post-construction deficit view. NOT the PoolCapFill population: this
        // recount drops the isQuotaFillExcluded groups (1/G08, G14) and runs
        // WITH the 274 pre-assign pins; PoolCapFill's empties are pin-less and
        // unfiltered, so the two counts are different populations by design
        Set<java.time.LocalDate> grid = new HashSet<>();
        for (Timeslot t : w.timeslots()) grid.add(t.getDate());
        Map<Long, Set<java.time.LocalDate>> placedByGroup = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) continue;
            var t = l.getTeacher();
            if (t == null || t.pseudo) continue;
            placedByGroup.computeIfAbsent(l.course.studentGroup.id, k -> new HashSet<>())
                    .add(l.timeslot.getDate());
        }
        java.util.Set<String> emptyCells = new java.util.TreeSet<>();
        Map<String, Integer> emptiesByGroup = new TreeMap<>(
                SnapshotFixture.emptyDeclaredCellsByGroup(placedByGroup, w.quotas(), grid, true));
        int empties = emptiesByGroup.values().stream().mapToInt(Integer::intValue).sum();
        for (DailyActivityQuota q : w.quotas()) {
            if (q.aulaHours + q.labHours <= 0 || !grid.contains(q.date)) continue;
            if (SchoolScheduleConstraintProvider.isQuotaFillExcluded(q.studentGroup.name)) continue;
            var placed = placedByGroup.get(q.studentGroup.id);
            if (placed == null || !placed.contains(q.date)) {
                emptyCells.add(q.studentGroup.name + "|" + q.date);
            }
        }
        // match counts (days), one rescore: same units as the committed census artifacts
        var hCounts = SnapshotFixture.providerMatchCounts(solutionManager, schedule,
                java.util.Set.of("H34", "H33", "H32"));
        System.out.printf("%s: empties(H34-scope)=%d breakdown=%s H34=%d H33=%d H32=%d%n",
                label, empties, emptiesByGroup,
                hCounts.getOrDefault("H34", -1), hCounts.getOrDefault("H33", -1),
                hCounts.getOrDefault("H32", -1));
        assertTrue(!census.isEmpty(), "the census must see declared dates");
        try {
            java.nio.file.Files.write(java.nio.file.Path.of("target/reserve-census-"
                    + label.replaceAll("[^A-Za-z0-9]", "") + ".txt"), census);
        } catch (Exception e) {
            System.out.println("census file write failed: " + e.getMessage());
        }
        return emptyCells;
    }

    /** id-independent content hash over the pinned placements. */
    private String contentSha(List<Lesson> pinned) {
        try {
            List<String> tuples = pinned.stream()
                    .map(l -> String.join("|",
                            l.course.studentGroup.name,
                            l.course.subject != null ? l.course.subject : "?",
                            l.course.teacher != null ? l.course.teacher.name : "?",
                            l.timeslot.getDate().toString(),
                            String.valueOf(l.timeslot.startDateTime)))
                    .sorted().collect(Collectors.toList());
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", tuples).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return "sha-error:" + e.getMessage();
        }
    }

    private SchoolSchedule solveConstruction(Wires w, Integer graspSeed) {
        return SnapshotFixture.solveConstructionOnly(freshLessons(),
                new SnapshotFixture.BenchWires(w.timeslots(), w.groups(), w.teachers(),
                        w.courses(), w.availabilities(), w.quotas(), List.of()),
                true, graspSeed);
    }

    /**
     * ONE match-enabled rescore per arm answering all requested prefixes: a
     * per-prefix countByProvider would rebuild a score director and rescore the
     * whole ~12.5k-lesson schedule once per constraint (review finding).
     */

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
                courses, availabilities, com.school.planner.domain.Room.listAll(),
                List.of(), List.of(),  // holidays/stage never seeded here: inert, as this bench always ran
                null, true, true);
        return new Wires(w.timeslots(), w.groups(), w.teachers(), w.courses(),
                w.availabilities(), w.quotas());
    }

    @Transactional
    void seedRealSeedFixture() {
        // the 18 real rooms: Phase 0's pinning needs assignable rooms (the first bench
        // run pinned 0 with "hard conflict / no room" on every target without them)
        RealSeedFixture.seedRooms();
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers =
                RealSeedFixture.seedTeachersAndAvailabilityRules(availabilityExpansionService);
        SnapshotFixture.seedCoursesFromIncarichi(teachers, groups, lessonGenerator, null);
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
        // the 8 Stage pseudo-courses (2009 lessons): the measured protocol never ran
        // import-calendario, so stage lessons are ordinary VRP-confined lessons the
        // construction must place - without them the group-slot occupancy (H3) is
        // ~2009 cells lighter and the census would under-report CONFLICT rejections
        dataGenerator.generateStageCourses();
    }
}
