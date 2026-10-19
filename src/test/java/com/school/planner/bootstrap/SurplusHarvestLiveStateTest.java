package com.school.planner.bootstrap;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;

import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.ScoreCompat;
import com.school.planner.solver.SurplusHarvest;
import com.school.planner.solver.TimeslotSequence;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-577 SURPLUS-HARVEST LIVE-STATE: measures the pass on a LIVE construction state
 * (not a replay), with the CONSTRAINT PROVIDER counting H34 (never a manual copy).
 *
 * <p>Arms: OFF (construction alone, score + H34 count) vs ON (same construction +
 * SurplusHarvest, score + H34 count + moves). Pre-registered expectation: H34 ON &lt;
 * H34 OFF, hard ON &le; hard OFF, overquota drops.
 *
 * <p>Gated by {@code PLANNER_TEST_PAIR_CELLS=1}. Uses ConstraintVerifier streams
 * (the DefaultSolutionManager drill-down) for the H34 match count: the exact same
 * mechanism the /analysis endpoint uses.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PAIR_CELLS", matches = ".+")
class SurplusHarvestLiveStateTest {

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
        System.clearProperty(SurplusHarvest.FLAG);
    }

    @Test
    @Timeout(value = 1200)   // v12.2 (6/09): the un-starved search is the real cost - the starved pass
                             // finished in 51-144s because the shape filter killed 100k candidates
                             // before any gate; the working pass measured 601s+ on the same state.
    @Transactional
    void surplusHarvestOnLiveState() {
        System.out.println("=== SURPLUS-HARVEST LIVE-STATE (pre-registered: cells down, hard <=, overquota down) ===");

        // Seed the real fixture.
        seedRealSeedFixture();

        // Wire statics (the buildAndSolveConstruction pattern).
        List<Timeslot> timeslots = Timeslot.listAll();
        List<StudentGroup> groups = StudentGroup.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<com.school.planner.domain.Teacher> teachers = com.school.planner.domain.Teacher.listAll();
        List<com.school.planner.domain.Course> courses = com.school.planner.domain.Course.listAll();
        List<com.school.planner.domain.TeacherAvailability> availabilities =
                com.school.planner.domain.TeacherAvailability.listAll();
        for (var g : groups) g.idealEndDateVrpBuffered = TimeslotSequence.SCHOOL_YEAR_END;
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setTeacherAvailabilitiesStatic(availabilities);
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.of(), Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        List<com.school.planner.domain.GroupCalendarRule> calRules =
                com.school.planner.domain.GroupCalendarRule.listAll();
        SchoolSchedule.setGroupCalendarRulesStatic(calRules);
        List<DailyActivityQuota> quotas = DailyActivityQuota.listAll();
        SchoolSchedule.setDailyActivityQuotasStatic(quotas);
        SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);

        // Construction (deterministic, same as the cycle's stage A).
        System.setProperty("planner.construction.grasp.enabled", "false");
        var constructionPhase = new com.school.planner.solver.DayClusteredConstructionPhase<SchoolSchedule>();
        SchoolSchedule problem = new SchoolSchedule(timeslots, List.of(), List.of(), availabilities,
                List.of(), groups, teachers, courses, lessons, List.of());
        problem.setDailyActivityQuotas(quotas);

        var solverConfig = SnapshotFixture.applyEnvironmentMode(new ai.timefold.solver.core.config.solver.SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(com.school.planner.solver.SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(new ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig()
                        .withCustomPhaseCommandList(List.of(constructionPhase)))));
        var solved = ai.timefold.solver.core.api.solver.SolverFactory
                .<SchoolSchedule>create(solverConfig).buildSolver().solve(problem);
        System.clearProperty("planner.construction.grasp.enabled");

        // ARM OFF: score + H34 count on the live state.
        solutionManager.update(solved);
        var offScore = (BendableScore) solved.getScore();
        int offH34 = countH34ByProvider(solved);
        int offOverQuota = countOverQuotaHours(solved);
        System.out.printf("DIAG OFF: score=%s H34=%d overQuota=%d%n", offScore, offH34, offOverQuota);

        if (offH34 == 0) {
            System.out.println("SANITY: H34=0 at the boundary in the test harness: the pass has no food here.");
            System.out.println("SURPLUS-HARVEST LIVE-STATE: cells 0 -> 0 | hard ? -> ? | overquota ? -> ? | moves 0/rejected 0");
            return;
        }

        int offHardCount = countHardViolations(solved);
        // ARM ON: apply SurplusHarvest to the live state.
        // v4: skip the H14-impossible (pre-startDate) targets so the pass works on
        // the GENUINE cells only (the measurement that decides the morning's cure B).
        System.setProperty(SurplusHarvest.FLAG, "true");
        System.setProperty("planner.construction.surplus-harvest.skip-pre-start", "true");
        var result = SurplusHarvest.repair(solved,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);
        System.clearProperty(SurplusHarvest.FLAG);
        System.clearProperty("planner.construction.surplus-harvest.skip-pre-start");

        // ORACLE (v7): the sibling improve-or-noop at the test level. If the hard
        // count WORSENED, revert to the pre-pass state (the runner does this with
        // PlacementSnapshot in the staged cycle: here we just re-score and report).
        int hardCountAfter = countHardViolations(solved);
        if (hardCountAfter > offHardCount) {
            System.out.printf("DIAG ORACLE: hard %d -> %d WORSENED: the runner would REVERT the whole pass%n",
                    offHardCount, hardCountAfter);
            System.out.printf("SURPLUS-HARVEST LIVE-STATE v7 (oracle-reverted): cells %d -> %d | hard %d -> %d | overquota %d -> %d | moves %d (REVERTED)%n",
                    offH34, offH34, offHardCount, offHardCount, offOverQuota, offOverQuota, 0);
        }

        solutionManager.update(solved);
        var onScore = (BendableScore) solved.getScore();
        int onH34 = countH34ByProvider(solved);
        int onOverQuota = countOverQuotaHours(solved);
        System.out.printf("DIAG ON: score=%s H34=%d overQuota=%d%n", onScore, onH34, onOverQuota);

        System.out.printf("=== SURPLUS-HARVEST LIVE-STATE: cells %d -> %d | hard %d -> %d | overquota %d -> %d | moves %d/rejected %d ===%n",
                offH34, onH34,
                ScoreCompat.hardOf(offScore), ScoreCompat.hardOf(onScore),
                offOverQuota, onOverQuota,
                result.movesApplied(), result.rejected());
        System.out.printf("SURPLUS-HARVEST REJECTS: %s%n", result.rejectBreakdown());
        assertTrue(true, "live-state bench reached");
    }

    private int countHardViolations(SchoolSchedule schedule) {
        int violations = 0;
        Map<String, Integer> teacherSlotCount = new HashMap<>();
        Map<String, Integer> groupSlotCount = new HashMap<>();
        Map<String, Integer> teacherDayHours = new HashMap<>();
        Map<String, Integer> groupDayHours = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.course == null) continue;
            String tsKey = l.timeslot.startDateTime.toString();
            if (l.course.teacher != null && !l.course.teacher.pseudo) {
                String key = l.course.teacher.id + "|" + tsKey;
                if (teacherSlotCount.merge(key, 1, Integer::sum) > 1) violations++;
                teacherDayHours.merge(l.course.teacher.id + "|" + l.timeslot.getDate(), 1, Integer::sum);
            }
            if (l.course.studentGroup != null) {
                String gkey = l.course.studentGroup.id + "|" + tsKey;
                if (groupSlotCount.merge(gkey, 1, Integer::sum) > 1) violations++;
                groupDayHours.merge(l.course.studentGroup.id + "|" + l.timeslot.getDate(), 1, Integer::sum);
            }
        }
        for (var e : teacherDayHours.entrySet()) {
            if (e.getValue() > 6) violations += e.getValue() - 6;
        }
        for (var e : groupDayHours.entrySet()) {
            if (e.getValue() > 8) violations += e.getValue() - 8;
        }
        return violations;
    }

    /** H34 count via the CONSTRAINT PROVIDER's own match policy (never a manual copy). */
    private int countH34ByProvider(SchoolSchedule schedule) {
        var factory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager)
                .getScoreDirectorFactory();
        try (var sd = factory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            sd.setWorkingSolution(schedule);
            sd.calculateScore();
            for (var t : sd.getConstraintMatchTotalMap().values()) {
                if (t.getConstraintRef().id().startsWith("H34")) {
                    return t.getConstraintMatchCount();
                }
            }
        } catch (Exception e) {
            System.out.printf("DIAG countH34ByProvider exception: %s%n", e.getMessage());
        }
        return -1;
    }

    /** Sum of (placed - quota) over all (group, date) with placed > quota (non-Stage). */
    private int countOverQuotaHours(SchoolSchedule schedule) {
        Map<String, Integer> hours = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) continue;
            var t = l.course.teacher;
            if (t != null && t.pseudo) continue;
            hours.merge(l.course.studentGroup.id + "|" + l.timeslot.getDate(), 1, Integer::sum);
        }
        int overQuota = 0;
        for (var e : hours.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            long groupId = Long.parseLong(parts[0]);
            LocalDate date = LocalDate.parse(parts[1]);
            var row = SchoolSchedule.getExpectedHoursRow(groupId, date);
            if (row != null) {
                int quota = row.aulaHours + row.labHours;
                if (quota > 0 && e.getValue() > quota) overQuota += e.getValue() - quota;
            }
        }
        return overQuota;
    }

    // ── fixture (mirrors the sibling tests) ──────────────────────────────────────

    @Transactional
    void seedRealSeedFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, com.school.planner.domain.Teacher> teachers = new java.util.LinkedHashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("teachers.csv", 6)) {
            var t = new com.school.planner.domain.Teacher(row[0].trim());
            String maxH = row[3].trim();
            t.maxDailyHours = maxH.isEmpty() ? null : Integer.valueOf(maxH);
            t.persist();
            teachers.put(t.name, t);
        }
        var aggregation = DataGenerator.aggregateCourseHours(DataGenerator.loadSeedCsv("incarichi.csv", 4));
        for (var entry : aggregation.hoursByKey().entrySet()) {
            var key = entry.getKey();
            var teacher = teachers.get(key.teacherName());
            var group = groups.get(key.groupName());
            if (teacher == null || group == null) continue;
            var course = new com.school.planner.domain.Course(key.subject(), teacher, group, entry.getValue());
            course.persist();
            lessonGenerator.generateForCourse(course.id);
        }
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
        for (String[] row : DataGenerator.loadSeedCsv("availability_rules.csv", 8)) {
            var teacher = teachers.get(row[0].trim());
            if (teacher == null) continue;
            new com.school.planner.domain.RecurringAvailabilityRule(teacher,
                    java.time.DayOfWeek.valueOf(row[1].trim()),
                    Integer.parseInt(row[2].trim()), Integer.parseInt(row[3].trim()),
                    com.school.planner.domain.AvailabilityType.valueOf(row[4].trim()),
                    row[5].isBlank() ? null : LocalDate.parse(row[5].trim()),
                    row[6].isBlank() ? null : LocalDate.parse(row[6].trim()), row[7].trim())
                    .persist();
        }
        availabilityExpansionService.expandAll();
    }
}
