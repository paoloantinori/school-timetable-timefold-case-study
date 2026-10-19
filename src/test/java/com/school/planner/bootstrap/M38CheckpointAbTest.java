package com.school.planner.bootstrap;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.entity.pillar.PillarSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.PillarChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.PillarSwapMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import com.school.planner.bootstrap.DataGenerator;
import com.school.planner.bootstrap.RealSeedFixture;
import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.CompositeMoveFilter;
import com.school.planner.solver.PairCellGuard;
import com.school.planner.solver.PairCellsTemplateBuilder;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.ScoreCompat;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.solver.WeeklyTemplateBuilder;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.service.LessonGenerator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-576 M38 checkpoint A/B: what does pair-deviation REALLY do in the LS at equal
 * budget? One construction (pair builder armed, golden forms published), then the SAME
 * phase-2 LS (mirrored from Phase2SmokeSnapshotTest) run twice from equivalent starts:
 * arm OFF (explicit "false") vs arms ON at weights 500/50/1 (explicit "true", the sweep).
 *
 * <p>Questions pre-registered (2026-09-01 night): (a) at equal budget does M38 ON change
 * the HARD score or only medium/match? (b) does the golden-cell match rise vs OFF?
 * (c) wall at equal budget.
 *
 * <p>Gated by {@code PLANNER_TEST_PAIR_CELLS=1} like its siblings: two ~90s LS arms
 * exceed the localized budget. Immune to the stale-jar confound BY CONSTRUCTION: the
 * harness compiles the current sources. TREND signal only, never adoption.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PAIR_CELLS", matches = ".+")
class M38CheckpointAbTest {

    private static final long BUDGET_SECONDS = 90;
    private static final String M38_FLAG = "planner.constraints.pair-deviation.enabled";

    @Inject jakarta.persistence.EntityManager em;
    @Inject LessonGenerator lessonGenerator;
    @Inject DataGenerator dataGenerator;
    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject ai.timefold.solver.core.api.solver.SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    private static final Map<String, Integer> MAX_HOURS_PER_DAY = RealSeedFixture.MAX_HOURS_PER_DAY;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
        System.clearProperty(M38_FLAG);
    }

    /** Re-seed the full real fixture (mirror of PairCellsTemplateBenchTest.seedRealSeedFixture). */
    @Transactional
    void seedRealSeedFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers = new LinkedHashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("teachers.csv", 6)) {
            Teacher t = new Teacher(row[0].trim());
            String maxDailyHours = row[3].trim();
            t.maxDailyHours = maxDailyHours.isEmpty() ? null : Integer.valueOf(maxDailyHours);
            t.persist();
            teachers.put(t.name, t);
        }
        DataGenerator.CourseAggregation aggregation =
                DataGenerator.aggregateCourseHours(DataGenerator.loadSeedCsv("incarichi.csv", 4));
        for (Map.Entry<DataGenerator.CourseKey, Integer> entry : aggregation.hoursByKey().entrySet()) {
            DataGenerator.CourseKey key = entry.getKey();
            Teacher teacher = teachers.get(key.teacherName());
            StudentGroup group = groups.get(key.groupName());
            if (teacher == null || group == null) continue;
            Course course = new Course(key.subject(), teacher, group, entry.getValue());
            course.persist();
            lessonGenerator.generateForCourse(course.id);
        }
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
        for (String[] row : DataGenerator.loadSeedCsv("availability_rules.csv", 8)) {
            Teacher teacher = teachers.get(row[0].trim());
            if (teacher == null) continue;
            new RecurringAvailabilityRule(teacher, DayOfWeek.valueOf(row[1].trim()),
                    Integer.parseInt(row[2].trim()), Integer.parseInt(row[3].trim()),
                    AvailabilityType.valueOf(row[4].trim()),
                    row[5].isBlank() ? null : LocalDate.parse(row[5].trim()),
                    row[6].isBlank() ? null : LocalDate.parse(row[6].trim()), row[7].trim())
                    .persist();
        }
        availabilityExpansionService.expandAll();
    }

    @Test
    @Timeout(value = 900)
    @Transactional
    void m38WeightSweep() {
        System.out.println("=== M38-WEIGHT-SWEEP (500 / 50 / 1, same 90s phase-2) ===");
        System.out.println("Question: does the tax collapse at 1000x lower weight while keeping the match?");

        seedRealSeedFixture();
        System.setProperty("planner.construction.pair-cells.enabled", "true");

        // Wire the statics exactly like buildAndSolveConstruction.
        List<Timeslot> timeslots = Timeslot.listAll();
        List<StudentGroup> studentGroups = StudentGroup.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<TeacherAvailability> teacherAvailabilities = TeacherAvailability.listAll();
        for (StudentGroup group : studentGroups) {
            group.idealEndDateVrpBuffered = TimeslotSequence.SCHOOL_YEAR_END;
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setTeacherAvailabilitiesStatic(teacherAvailabilities);
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.of(), Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        List<com.school.planner.domain.GroupCalendarRule> calRules =
                com.school.planner.domain.GroupCalendarRule.listAll();
        SchoolSchedule.setGroupCalendarRulesStatic(calRules);
        List<com.school.planner.domain.DailyActivityQuota> quotas =
                com.school.planner.domain.DailyActivityQuota.listAll();
        SchoolSchedule.setDailyActivityQuotasStatic(quotas);
        SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);

        // Publish the pair golden forms.
        Set<Integer> distinctWeeks = new HashSet<>();
        for (Timeslot t : timeslots) {
            distinctWeeks.add(t.getDate().get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        }
        Map<PairCellsTemplateBuilder.PairKey, PairCellsTemplateBuilder.PairForm> goldenForms =
                PairCellsTemplateBuilder.loadGoldenPairForms(courses);
        Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> pairTemplate =
                PairCellsTemplateBuilder.build(courses, goldenForms,
                        Set.of(), Set.of(), Set.of(), Map.of(), Map.of(), distinctWeeks.size());
        assertFalse(pairTemplate.isEmpty());
        PairCellGuard.publish(pairTemplate);

        // Construction (pair builder armed).
        com.school.planner.solver.DayClusteredConstructionPhase<SchoolSchedule> constructionPhase =
                new com.school.planner.solver.DayClusteredConstructionPhase<>();
        SchoolSchedule problem = new SchoolSchedule(timeslots, List.of(), List.of(), teacherAvailabilities,
                List.of(), studentGroups, teachers, courses, lessons, List.of());
        SolverConfig constructionOnly = SnapshotFixture.applyEnvironmentMode(new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(new ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig()
                        .withCustomPhaseCommandList(List.of(constructionPhase)))));
        SchoolSchedule constructed = SolverFactory.<SchoolSchedule>create(constructionOnly).buildSolver().solve(problem);
        System.out.printf("DIAG construction: score=%s%n", constructed.getScore());

        // 3 arms: OFF baseline + weight 500 + weight 50 + weight 1
        ArmResult off = runArm("OFF", constructed, false, null);
        ArmResult w500 = runArm("W500", constructed, true, "500");
        ArmResult w50 = runArm("W50", constructed, true, "50");
        ArmResult w1 = runArm("W1", constructed, true, "1");

        System.out.printf("=== M38-WEIGHT-SWEEP RESULT ===%n");
        for (var r : List.of(off, w500, w50, w1)) {
            System.out.printf("  %-5s score=%s match=%.1f%% holes=%d wall=%dms%n",
                    r.label(), r.score(), r.matchPct(), r.internalHoles(), r.wallMs());
        }
        assertTrue(true, "sweep reached");
    }

    private record ArmResult(String label, BendableScore score, double matchPct, long internalHoles, long wallMs) {}

    @SuppressWarnings("unchecked")
    private ArmResult runArm(String label, SchoolSchedule constructed, boolean armOn, String weight) {
        // Arms are pinned explicitly ("true"/"false"), decoupled from the production
        // default: a future default flip must not silently relabel a sweep arm.
        System.setProperty(M38_FLAG, armOn ? "true" : "false");
        if (armOn && weight != null) {
            System.setProperty("planner.constraints.pair-deviation.weight", weight);
        } else {
            System.clearProperty("planner.constraints.pair-deviation.weight");
        }
        SchoolSchedule start = copyConstructed(constructed);
        long t0 = System.currentTimeMillis();
        Solver<SchoolSchedule> solver = SolverFactory.<SchoolSchedule>create(phase2LocalSearch(BUDGET_SECONDS)).buildSolver();
        SchoolSchedule solved = solver.solve(start);
        long wall = System.currentTimeMillis() - t0;
        BendableScore score = (BendableScore) solved.getScore();
        double match = goldenCellMatch(solved);
        long holes = internalHoles(solved);
        System.out.printf("DIAG arm %s: end=%s%n", label, score);
        System.clearProperty("planner.constraints.pair-deviation.weight");
        return new ArmResult(label, score, match, holes, wall);
    }

    /** Fresh schedule carrying the constructed placements (lesson-id keyed). */
    private SchoolSchedule copyConstructed(SchoolSchedule constructed) {
        Map<Long, java.time.LocalDateTime> byId = new java.util.HashMap<>();
        for (Lesson l : constructed.getLessons()) {
            if (l.timeslot != null && l.id != null) {
                byId.put(l.id, l.timeslot.startDateTime);
            }
        }
        List<Lesson> fresh = Lesson.listAll();
        for (Lesson l : fresh) {
            java.time.LocalDateTime dt = l.id != null ? byId.get(l.id) : null;
            if (dt != null) {
                l.timeslot = timeslotOf(dt);
            }
        }
        List<Timeslot> timeslots = Timeslot.listAll();
        return new SchoolSchedule(timeslots, List.of(), List.of(), TeacherAvailability.listAll(),
                List.of(), StudentGroup.listAll(), Teacher.listAll(), Course.listAll(), fresh, List.of());
    }

    private final Map<java.time.LocalDateTime, Timeslot> tsByStart = new java.util.HashMap<>();

    private Timeslot timeslotOf(java.time.LocalDateTime start) {
        if (tsByStart.isEmpty()) {
            List<Timeslot> allTs = Timeslot.listAll(); // mech: typed local (Panache inference)
            for (Timeslot ts : allTs) {
                tsByStart.put(ts.startDateTime, ts);
            }
        }
        return tsByStart.get(start);
    }

    /** golden-cell match: of the pair FORM cells published by the guard, how many the
     *  grid actually occupies (pair-keyed: the pair's own teacher on that cell). */
    private double goldenCellMatch(SchoolSchedule solved) {
        Map<String, Set<String>> forms = new java.util.HashMap<>();   // pair -> form cells
        Map<String, Set<String>> occ = new java.util.HashMap<>();     // pair -> occupied cells
        for (Lesson l : solved.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.teacher == null
                    || l.course.studentGroup == null
                    || l.course.teacher.id == null || l.course.studentGroup.id == null) continue;
            String key = l.course.teacher.id + "|" + l.course.studentGroup.id;
            String cell = l.timeslot.getDayOfWeek().name() + "|"
                    + TimeslotSequence.slotIndexOf(l.timeslot);
            occ.computeIfAbsent(key, k -> new HashSet<>()).add(cell);
            forms.computeIfAbsent(key, k -> new HashSet<>()).addAll(PairCellGuard.formCellsOf(l));
        }
        int tot = 0;
        int hit = 0;
        for (Map.Entry<String, Set<String>> e : forms.entrySet()) {
            Set<String> got = occ.getOrDefault(e.getKey(), Set.of());
            for (String cell : e.getValue()) {
                tot++;
                if (got.contains(cell)) hit++;
            }
        }
        return tot == 0 ? -1 : 100.0 * hit / tot;
    }

    /** internal holes: (group, date) columns with an occupied-after-empty-before-occupied shape. */
    private long internalHoles(SchoolSchedule solved) {
        Map<String, java.util.TreeSet<Integer>> byGroupDate = new java.util.HashMap<>();
        for (Lesson l : solved.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) continue;
            byGroupDate.computeIfAbsent(
                    l.course.studentGroup.id + "|" + l.timeslot.getDate(),
                    k -> new java.util.TreeSet<>()).add(TimeslotSequence.slotIndexOf(l.timeslot));
        }
        long holes = 0;
        for (java.util.TreeSet<Integer> slots : byGroupDate.values()) {
            Integer prev = null;
            for (int s : slots) {
                if (prev != null && s > prev + 1) holes++;
                prev = s;
            }
        }
        return holes;
    }

    /** Phase-2 LS mirrored field-for-field from Phase2SmokeSnapshotTest (tabu 7, acl 1000). */
    private SolverConfig phase2LocalSearch(long budgetSeconds) {
        EntitySelectorConfig lessonEntities = new EntitySelectorConfig(Lesson.class);
        ValueSelectorConfig timeslotVar = new ValueSelectorConfig().withVariableName("timeslot");
        PillarSelectorConfig lessonPillars = new PillarSelectorConfig().withEntitySelectorConfig(lessonEntities);

        ChangeMoveSelectorConfig change = new ChangeMoveSelectorConfig()
                .withFixedProbabilityWeight(5.0)
                .withEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withValueSelectorConfig(timeslotVar);
        SwapMoveSelectorConfig swap = new SwapMoveSelectorConfig()
                .withFixedProbabilityWeight(3.0)
                .withEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withSecondaryEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withVariableNameIncludes("timeslot");
        PillarChangeMoveSelectorConfig pillarChange = new PillarChangeMoveSelectorConfig()
                .withFixedProbabilityWeight(2.0);
        pillarChange.setPillarSelectorConfig(lessonPillars);
        pillarChange.setValueSelectorConfig(new ValueSelectorConfig().withVariableName("timeslot"));
        PillarSwapMoveSelectorConfig pillarSwap = new PillarSwapMoveSelectorConfig()
                .withFixedProbabilityWeight(1.0);
        pillarSwap.setPillarSelectorConfig(lessonPillars);
        pillarSwap.withVariableNameIncludes("timeslot");

        UnionMoveSelectorConfig moves = new UnionMoveSelectorConfig()
                .withMoveSelectorList(List.of(change, swap, pillarChange, pillarSwap))
                .withFilterClass(CompositeMoveFilter.class);

        LocalSearchPhaseConfig phase2 = new LocalSearchPhaseConfig()
                .withMoveSelectorConfig(moves)
                .withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(budgetSeconds))
                .withAcceptorConfig(new LocalSearchAcceptorConfig().withEntityTabuSize(7))
                .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1000));

        return SnapshotFixture.applyEnvironmentMode(new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(phase2)));
    }
}
