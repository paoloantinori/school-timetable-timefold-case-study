package com.school.planner.bootstrap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;

import com.school.planner.dev.HumanFaithfulConstruction;
import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.TimeslotSequence;

/**
 * TASK-396 SPIKE harness — runs {@link HumanFaithfulConstruction} (a standalone human-faithful
 * constructive solver) on the REAL seed CSVs and measures whether it scales, to decide go/no-go
 * BEFORE touching the live 5-phase pipeline. Dev/test only — not in the live solver config.
 *
 * <p>Fixture + static-VRP wiring mirrors {@link WeeklyTemplateConstructionRealSeedTest} (same
 * package, for {@code DataGenerator} package-private access via {@link RealSeedFixture}); the
 * constructive solver itself is plain Java in {@code com.school.planner.dev}. Scoring is done
 * after construction via {@link ScoreManager} (measurement only — placement decisions use the
 * solver's own feasibility checks, not the score).
 *
 * <p>Deliberate simplifications (matching the L2 test's documented scope): no golden/Calendario
 * import → no pinned lessons; no Stage pseudo-course. isLab/requiredRoomTags/professionalizzanti
 * classification is unreachable (private in DataGenerator) and out of scope for a
 * hard-feasibility spike (room assignment is also out of scope). These narrow the fixture vs a
 * production solve but do NOT change whether the constructive approach scales — the spike's question.
 */
@QuarkusTest
class HumanFaithfulConstructionSpikeTest {

    @Inject DataGenerator dataGenerator;
    @Inject com.school.planner.service.LessonGenerator lessonGenerator;
    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject EntityManager em;

    private static final Map<String, Integer> MAX_HOURS_PER_DAY = RealSeedFixture.MAX_HOURS_PER_DAY;

    @Inject
    SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM RecurringAvailabilityRule").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM DailyActivityQuota").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(List.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setTeacherAvailabilitiesStatic(List.of());
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.of(), java.util.Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(null);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
    }

    @Transactional
    Map<String, StudentGroup> seedRealSeedFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers = RealSeedFixture.seedTeachersAndAvailabilityRules(availabilityExpansionService);
        DataGenerator.CourseAggregation aggregation =
                DataGenerator.aggregateCourseHours(DataGenerator.loadSeedCsv("incarichi.csv", 4));
        for (Map.Entry<DataGenerator.CourseKey, Integer> e : aggregation.hoursByKey().entrySet()) {
            DataGenerator.CourseKey key = e.getKey();
            Teacher teacher = teachers.get(key.teacherName());
            StudentGroup group = groups.get(key.groupName());
            if (teacher == null || group == null) continue;
            Course course = new Course(key.subject(), teacher, group, e.getValue());
            course.persist();
            lessonGenerator.generateForCourse(course.id);
        }
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
        return groups;
    }

    /** Build the SchoolSchedule problem and wire the static VRP snapshots (mirrors the L2 test). */
    private SchoolSchedule buildAndWire() {
        seedRealSeedFixture();
        List<Timeslot> timeslots = Timeslot.listAll();
        List<Room> rooms = List.of();
        List<StudentGroup> studentGroups = StudentGroup.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<TeacherAvailability> teacherAvailabilities = TeacherAvailability.listAll();
        for (StudentGroup g : studentGroups) {
            g.idealEndDateVrpBuffered = TimeslotSequence.SCHOOL_YEAR_END;
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(rooms);
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(new HashMap<>());
        SchoolSchedule.setTeacherAvailabilitiesStatic(teacherAvailabilities);
        SchoolSchedule.setSchoolHolidaysStatic(List.<SchoolHoliday>of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), java.util.Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(GroupCalendarRule.listAll());
        SchoolSchedule.setDailyActivityQuotasStatic(DailyActivityQuota.listAll());
        SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);
        return new SchoolSchedule(timeslots, rooms, List.of(), teacherAvailabilities,
                List.of(), studentGroups, teachers, courses, lessons, List.of());
    }

    /** Score the constructed schedule and print the hard/medium/soft + box + placed-conflict counts. */
    private void scoreAndReport(String label, SchoolSchedule schedule, HumanFaithfulConstruction.Result r) {
        solutionManager.update(schedule);
        BendableScore score = (BendableScore) schedule.getScore();
        long hard = ScoreCompat.hardOf(score);
        long medium = ScoreCompat.mediumOf(score);
        long soft = ScoreCompat.softOf(score);
        // Direct conflict count on PLACED lessons (H1 teacher / H3 group) — should be ~0 by construction.
        Map<String, Integer> tCnt = new HashMap<>();
        Map<String, Integer> gCnt = new HashMap<>();
        long placed = 0;
        long boxed = 0;
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null) {
                boxed++;
                continue;
            }
            placed++;
            Teacher t = l.getTeacher();
            if (t != null && !t.pseudo) tCnt.merge(t.id + "@" + l.timeslot.id, 1, Integer::sum);
            StudentGroup g = l.getStudentGroup();
            if (g != null) gCnt.merge(g.id + "@" + l.timeslot.id, 1, Integer::sum);
        }
        long teacherConflicts = tCnt.values().stream().filter(c -> c > 1).count();
        long groupConflicts = gCnt.values().stream().filter(c -> c > 1).count();
        System.out.printf("=== SPIKE [%s] ===%n", label);
        System.out.printf("  solver:  %s%n", r);
        System.out.printf("  placed=%d boxed=%d  (boxed == H0/unassigned residual)%n", placed, boxed);
        System.out.printf("  placed-lesson conflicts: H1(teacher)=%d  H3(group)=%d  (target 0 by construction)%n",
                teacherConflicts, groupConflicts);
        System.out.printf("  score: hard=%d medium=%d soft=%d  (box=%d counted directly above; "
                + "live CH baseline hard=-44100 with 0 box)%n", hard, medium, soft, boxed);
        printHardBreakdown(schedule);
    }

    /** Print which hard constraints contribute (score hard < 0), to confirm the placed-lesson
     *  residual is H17 and not a constraint the feasibility checks miss. Mirrors the ScoreDirector
     *  pattern in SchoolScheduleSolverIntegrationTest (community constraint-totals workaround). */
    private void printHardBreakdown(SchoolSchedule schedule) {
        try {
            var factory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager)
                    .getScoreDirectorFactory();
            try (var sd = factory.createScoreDirectorBuilder()
                    .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                    .withLookUpEnabled(false)
                    .withExpectShadowVariablesInCorrectState(false)
                    .build()) {
                sd.setWorkingSolution(schedule);
                sd.calculateScore();
                sd.getConstraintMatchTotalMap().forEach((ref, total) -> {
                    long h = ScoreCompat.hardOf(total.getScore());
                    if (h < 0) {
                        System.out.printf("    HARD %-44s %d (%d matches)%n", ref.id(), h,
                                total.getConstraintMatchCount());
                    }
                });
            }
        } catch (Exception e) {
            System.out.println("    (hard breakdown unavailable: " + e + ")");
        }
    }

    /** DE-RISK: full greedy (D=0, no backtrack) on all lessons. Confirms the machinery
     * (fixture + VRP gate + feasibility + scoring with null timeslots) works end-to-end. */
    @Test
    @Timeout(value = 600)
    void deRisk_fullGreedyD0() {
        SchoolSchedule schedule = buildAndWire();
        int total = schedule.getLessons().size();
        System.out.printf("SPIKE de-risk: %d lessons, %d timeslots%n", total,
                schedule.getTimeslots().size());
        HumanFaithfulConstruction.Result r =
                HumanFaithfulConstruction.solve(schedule, 0, 0, null);
        scoreAndReport("de-risk D=0 greedy, ALL lessons", schedule, r);
        org.junit.jupiter.api.Assertions.assertTrue(r.placed > 0, "machinery must place some lessons");
    }

    /** Backtrack sweep on the FULL problem: how does the box shrink as D/U grow, and at what cost?
     *  Characterizes viable D/U (the spike's variables) and whether backtrack is even needed.
     *  Fresh fixture per config (solve mutates lessons in place; clean() avoids duplicate-persist). */
    @Test
    @Timeout(value = 900)
    void backtrackSweep_fullProblem() {
        int[][] configs = { {0, 0}, {20, 500}, {100, 2000}, {500, 10000} };
        System.out.printf("SPIKE backtrack sweep on full problem (D=maxUndoDepth, U=maxTotalUndos):%n");
        for (int[] cfg : configs) {
            clean();
            SchoolSchedule schedule = buildAndWire();
            HumanFaithfulConstruction.Result r =
                    HumanFaithfulConstruction.solve(schedule, cfg[0], cfg[1], null);
            scoreAndReport("D=" + cfg[0] + " U=" + cfg[1], schedule, r);
        }
    }

    /** Scaling curve: greedy (D=0) time + box% vs problem size. Each slice places only its groups'
     *  lessons together (so cross-group teacher contention is real within the slice). Confirms
     *  linear-ish scaling (no exponential wall) — the brief's A<B<C<D curve, at the maximal D
     *  already proven, so subsets are confirmation that smaller = proportionally easier. */
    @Test
    @Timeout(value = 600)
    void sliceScaling_time() {
        String[][] slices = {
                {"Slice A (tight core: G05 + G10)", "G05", "G10"},
                {"Slice C (6 groups w/ shared teachers)", "G05", "G10", "G06", "G09", "G02", "G12"},
                {"Slice D (full, all groups)", null}
        };
        System.out.println("SPIKE scaling curve (D=0 greedy, time + box% vs size):");
        for (String[] sl : slices) {
            clean();
            SchoolSchedule schedule = buildAndWire();
            java.util.function.Predicate<Lesson> filter;
            if (sl[1] == null) {
                filter = null;
            } else {
                java.util.Set<String> want = new java.util.HashSet<>();
                for (int i = 1; i < sl.length; i++) want.add(sl[i]);
                filter = l -> l.getStudentGroup() != null && want.contains(l.getStudentGroup().name);
            }
            HumanFaithfulConstruction.Result r =
                    HumanFaithfulConstruction.solve(schedule, 0, 0, filter);
            int subsetTotal = r.placed + r.boxed;
            double boxPct = subsetTotal == 0 ? 0 : 100.0 * r.boxed / subsetTotal;
            System.out.printf("  %-46s lessons=%-6d placed=%-6d boxed=%-4d (%.1f%%) %s%n",
                    sl[0], subsetTotal, r.placed, r.boxed, boxPct, r);
        }
    }
}
