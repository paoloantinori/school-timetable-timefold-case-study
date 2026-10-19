package com.school.planner.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.SolutionManager;
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
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.CompositeMoveFilter;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.TimeslotSequence;

/**
 * TASK-419 stage-1 SPIKE (localized, offline). Measures the two things the proposed
 * staged solve ("promote the template cells to pins, then selectively degrade") needs to
 * know BEFORE any laptop run: (1) how much of the construction-end grid can be pinned by the
 * "on its M32 template cell" rule, and what phase-2 LS does to the score/recurrence with
 * that large pinned core; and (2) the FEASIBILITY of the residual. For every free lesson
 * left in conflict after the smoke, does a VRP-legal conflict-free cell still exist
 * (hypothesis A: stage 2 has little to do) or has the pinned template deadlocked it
 * (hypothesis B: stage 2's selective degrade has real work).
 *
 * <p><b>Input:</b> the ENTRY-of-phase-2 dump of val408-run1 (construction end, recurrence
 * 74.3% unpinned) copied into {@code target/} as {@code lessons.txt} (the {@link
 * SnapshotFixture} contract; no {@code boxed.txt}, so this snapshot has zero boxed, an
 * accepted comparability caveat since the pipe dump never captures the construction box).
 *
 * <p><b>Pinning rule</b> mirrors M32's own {@code deviatesFromTemplate} exactly (the
 * negation): a lesson is promoted to {@code pinned=true} (IN MEMORY ONLY) iff its course is
 * templated, its slot is canonical, and its (weekday, slotIndex) cell is one of the course's
 * template cells. Pseudo-teacher and untemplated-course lessons are never promoted (same
 * exemptions M32 applies).
 *
 * <p><b>Residual discriminator</b> reuses {@link BoxVrpFeasibilityDiscriminatorTest}'s exact
 * methodology (real VRP gate via {@link Lesson#getAvailableTimeslots()} intersected with
 * conflict-free vs the final grid's own occupancy, self-contribution subtracted), applied to
 * the lessons the smoke leaves in an H1/H3 conflict. A = free cells exist (LS coverage miss);
 * B = zero free cells (pinning deadlock). It is the same fixed-occupancy approximation the box
 * discriminator uses (occupancy frozen, one probe moves), so it does NOT model other free
 * lessons also moving; B is therefore a conservative upper bound on the true deadlock set.
 *
 * <p><b>TREND / diagnosis only, NEVER adoption</b> (TASK-401): a snapshot fixes one
 * trajectory. This spike decides how much work stage 2 of TASK-419 is, not whether to adopt.
 *
 * <p>Gated OFF from the suite via {@code PLANNER_TEST_419_STAGE1} (a 120s solve is far over
 * the localized budget); an env var, not {@code -D} ({@code test.sh} rejects {@code -D} and
 * does not forward MAVEN_OPTS). Optional {@code PLANNER_TEST_419_STAGE1_BUDGET} overrides seconds.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_419_STAGE1", matches = ".+")
class Task419Stage1SpikeTest {

    private static final long DEFAULT_BUDGET_SECONDS = 120;
    private static final Path PH2_ENTRY_DUMP = Path.of("claudedocs", "spalmone-2026-27",
            "task408-results", "val408-run1", "phase-transitions", "phase-2-2-ls-compactness.txt");

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject EntityManager em;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    @Transactional
    void promoteTemplateThenSmokeAndDiscriminateResidual() throws Exception {
        long budget = envBudget();

        // Copy the phase-2-entry dump into a target/ work dir as lessons.txt (SnapshotFixture contract).
        Path workDir = Path.of("target", "task419-ph2entry");
        Files.createDirectories(workDir);
        Files.copy(PH2_ENTRY_DUMP, workDir.resolve("lessons.txt"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(workDir, availabilityExpansionService, true);
        // Seed quota/expected-hours + publish the M32 template map (getWeeklyTemplateCells).
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);

        SchoolSchedule problem = snap.problem;

        // Stage 1: promote to pinned every unpinned lesson sitting ON its template cell.
        // TASK-419 step 1: the promoted ids ALSO go into SchoolSchedule's promoted-pin
        // registry, so the pinned-exempt constraint family keeps scoring them exactly as
        // it scored them one moment earlier, while they were unpinned (see
        // BaseConstraintProvider#isAuthoritativePin). Without the registry M20 counted the
        // frozen hours as absent and started at -3.23M instead of -1.07M for the identical
        // grid, paying the LS to re-fill days that were already full. The @BeforeEach/
        // @AfterEach clean() clears the registry again via SnapshotFixture.resetStatics.
        int alreadyPinned = 0;
        int promoted = 0;
        int freeLeft = 0;
        List<Long> promotedIds = new ArrayList<>();
        for (Lesson l : snap.lessons) {
            if (l.timeslot == null) continue;
            if (l.pinned) { alreadyPinned++; continue; }
            if (onTemplateCell(l)) { l.pinned = true; promoted++; promotedIds.add(l.id); }
            else freeLeft++;
        }
        SchoolSchedule.setPromotedLessonIdsStatic(promotedIds);

        solutionManager.update(problem);
        BendableScore startScore = (BendableScore) problem.getScore();
        double startRecurrence = SnapshotFixture.weeklyRecurrence(snap.lessons).percent();
        long startConflicts = conflictingUnpinned(snap.lessons).size();

        System.out.println("=== TASK-419 stage-1 spike: " + PH2_ENTRY_DUMP.getFileName()
                + " (budget " + budget + "s) ===");
        System.out.printf("  population: placed=%d  already-pinned=%d  promoted-to-pinned=%d  free-left=%d%n",
                snap.lessons.stream().filter(x -> x.timeslot != null).count(),
                alreadyPinned, promoted, freeLeft);
        System.out.printf("  promoted-pin registry: %d id(s) published (pinned-exempt family sees them as normal hours)%n",
                SchoolSchedule.getPromotedLessonIdsStatic().size());
        System.out.printf("  start: hard=%d medium=%d soft=%d  recurrence=%.1f%%  unpinned-conflicts=%d%n",
                ScoreCompat.hardOf(startScore), ScoreCompat.mediumOf(startScore), ScoreCompat.softOf(startScore),
                startRecurrence, startConflicts);
        System.out.println("  -- start breakdown (principal) --");
        printBreakdown(problem);

        // Phase-2 Compactness LS with the pinned core, short budget.
        Solver<SchoolSchedule> solver =
                SolverFactory.<SchoolSchedule>create(phase2LocalSearch(budget)).buildSolver();
        SchoolSchedule solved = solver.solve(problem);

        BendableScore endScore = (BendableScore) solved.getScore();
        double endRecurrence = SnapshotFixture.weeklyRecurrence(solved.getLessons()).percent();
        List<Lesson> endConflicts = conflictingUnpinned(solved.getLessons());
        long endUnassigned = solved.getLessons().stream().filter(l -> !l.pinned && l.timeslot == null).count();

        System.out.printf("  end:   hard=%d medium=%d soft=%d  recurrence=%.1f%%%n",
                ScoreCompat.hardOf(endScore), ScoreCompat.mediumOf(endScore), ScoreCompat.softOf(endScore), endRecurrence);
        System.out.printf("  delta: hard %+d  medium %+d  recurrence %+.1f pt%n",
                ScoreCompat.hardOf(endScore) - ScoreCompat.hardOf(startScore),
                ScoreCompat.mediumOf(endScore) - ScoreCompat.mediumOf(startScore),
                endRecurrence - startRecurrence);
        System.out.println("  -- end breakdown (principal) --");
        printBreakdown(solved);

        // Residual feasibility: discriminate every unpinned lesson left in conflict / unassigned.
        System.out.printf("  residual: unpinned-in-conflict=%d  unassigned=%d%n",
                endConflicts.size(), endUnassigned);
        List<Lesson> residual = new ArrayList<>(endConflicts);
        for (Lesson l : solved.getLessons()) {
            if (!l.pinned && l.timeslot == null) residual.add(l);
        }
        discriminateResidual(solved.getLessons(), residual);

        System.out.println("  (TREND/diagnosis only, a snapshot fixes one trajectory; NEVER adoption. laptop A/B x2 gate.)");
        org.junit.jupiter.api.Assertions.assertNotNull(endScore, "the LS must produce a score");
    }

    private long envBudget() {
        String b = System.getenv("PLANNER_TEST_419_STAGE1_BUDGET");
        return (b == null || b.isBlank()) ? DEFAULT_BUDGET_SECONDS : Long.parseLong(b.trim());
    }

    /** Mirror of M32's {@code deviatesFromTemplate} (negated): lesson is ON its template cell. */
    private static boolean onTemplateCell(Lesson l) {
        if (l.getTeacher() == null || l.getTeacher().pseudo) return false;
        if (l.course == null || l.course.id == null) return false;
        Set<Integer> cells = SchoolSchedule.getWeeklyTemplateCells(l.course.id);
        if (cells == null) return false;
        int slotIndex = TimeslotSequence.slotIndexOf(l.timeslot);
        if (slotIndex < 0) return false;
        int cell = SchoolSchedule.encodeTemplateCell(l.timeslot.getDate().getDayOfWeek(), slotIndex);
        return cells.contains(cell);
    }

    /** Unpinned lessons currently stacked in an H1 (real teacher) or H3 (group) same-cell conflict. */
    private static List<Lesson> conflictingUnpinned(List<Lesson> lessons) {
        Map<String, Integer> groupCell = new HashMap<>();
        Map<String, Integer> teacherCell = new HashMap<>();
        for (Lesson l : lessons) {
            if (l.timeslot == null) continue;
            String cell = l.timeslot.getDate() + "@" + l.timeslot.startDateTime.getHour();
            if (l.getStudentGroup() != null) groupCell.merge(l.getStudentGroup().id + "@" + cell, 1, Integer::sum);
            if (l.getTeacher() != null && !l.getTeacher().pseudo)
                teacherCell.merge(l.getTeacher().name + "@" + cell, 1, Integer::sum);
        }
        List<Lesson> out = new ArrayList<>();
        for (Lesson l : lessons) {
            if (l.pinned || l.timeslot == null) continue;
            String cell = l.timeslot.getDate() + "@" + l.timeslot.startDateTime.getHour();
            boolean g = l.getStudentGroup() != null && groupCell.getOrDefault(l.getStudentGroup().id + "@" + cell, 0) > 1;
            boolean t = l.getTeacher() != null && !l.getTeacher().pseudo
                    && teacherCell.getOrDefault(l.getTeacher().name + "@" + cell, 0) > 1;
            if (g || t) out.add(l);
        }
        return out;
    }

    /**
     * For each residual lesson, count VRP-legal conflict-free cells vs the final grid occupancy
     * (self-contribution subtracted). A = has free cells; B = zero (deadlock). Same gate the
     * box discriminator applies.
     */
    private void discriminateResidual(List<Lesson> allLessons, List<Lesson> residual) {
        if (residual.isEmpty()) {
            System.out.println("  discriminator: no residual (box clear + conflict-free), nothing to classify");
            return;
        }
        // Global occupancy counts from the whole final grid.
        Map<String, Integer> groupBusy = new HashMap<>();
        Map<String, Integer> teacherBusy = new HashMap<>();
        Map<String, Integer> groupDaily = new HashMap<>();
        Map<String, Integer> teacherDaily = new HashMap<>();
        for (Lesson l : allLessons) {
            if (l.timeslot == null) continue;
            LocalDate d = l.timeslot.getDate();
            int hour = l.timeslot.startDateTime.getHour();
            if (l.getStudentGroup() != null) {
                groupBusy.merge(l.getStudentGroup().name + "@" + d + "@" + hour, 1, Integer::sum);
                groupDaily.merge(l.getStudentGroup().name + "@" + d, 1, Integer::sum);
            }
            if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                teacherBusy.merge(l.getTeacher().name + "@" + d + "@" + hour, 1, Integer::sum);
                teacherDaily.merge(l.getTeacher().name + "@" + d, 1, Integer::sum);
            }
        }

        Map<String, int[]> perLessonAB = new LinkedHashMap<>(); // key=group|teacher|subject -> {aCount,bCount}
        int classA = 0;
        int classB = 0;
        for (Lesson probe : residual) {
            StudentGroup group = probe.getStudentGroup();
            Teacher teacher = probe.getTeacher();
            if (group == null || teacher == null) continue;
            int teacherCap = teacher.getEffectiveMaxDailyHours();
            Integer groupCap = group.maxHoursPerDay;
            boolean tPseudo = teacher.pseudo;
            LocalDate curDate = probe.timeslot != null ? probe.timeslot.getDate() : null;
            int curHour = probe.timeslot != null ? probe.timeslot.startDateTime.getHour() : -1;
            int free = 0;
            for (Timeslot ts : probe.getAvailableTimeslots()) {
                LocalDate d = ts.getDate();
                int hour = ts.startDateTime.getHour();
                boolean probeHere = d.equals(curDate) && hour == curHour;
                int gB = groupBusy.getOrDefault(group.name + "@" + d + "@" + hour, 0) - (probeHere ? 1 : 0);
                if (gB > 0) continue;
                int tB = tPseudo ? 0 : teacherBusy.getOrDefault(teacher.name + "@" + d + "@" + hour, 0) - (probeHere ? 1 : 0);
                if (tB > 0) continue;
                boolean probeThatDay = d.equals(curDate);
                int gDaily = groupDaily.getOrDefault(group.name + "@" + d, 0) - (probeThatDay ? 1 : 0);
                if (groupCap != null && groupCap > 0 && gDaily >= groupCap) continue;
                int tDaily = tPseudo ? 0 : teacherDaily.getOrDefault(teacher.name + "@" + d, 0) - (probeThatDay ? 1 : 0);
                if (teacherCap > 0 && tDaily >= teacherCap) continue;
                free++;
            }
            String key = group.name + "|" + teacher.name + "|" + probe.getSubject();
            int[] ab = perLessonAB.computeIfAbsent(key, k -> new int[2]);
            if (free > 0) { classA++; ab[0]++; }
            else { classB++; ab[1]++; }
        }
        System.out.printf("  DISCRIMINATOR: residual=%d  A(free cells exist)=%d  B(deadlock, 0 free)=%d%n",
                residual.size(), classA, classB);
        System.out.println("  VERDICT: " + (classB == 0
                ? "A - every residual lesson has a legal free cell (stage-2 selective-degrade has LITTLE work; a repair pass clears it)"
                : "B - " + classB + " residual lesson(s) deadlocked by the pinned template (stage-2 selective-degrade has real work)"));
        if (classB > 0) {
            System.out.println("  B breakdown (group|teacher|subject : #deadlocked):");
            perLessonAB.entrySet().stream()
                    .filter(e -> e.getValue()[1] > 0)
                    .sorted(Comparator.comparingInt((Map.Entry<String, int[]> e) -> e.getValue()[1]).reversed())
                    .limit(25)
                    .forEach(e -> System.out.printf("    %-55s : %d%n", e.getKey(), e.getValue()[1]));
        }
    }

    /** Per-constraint hard/medium/soft breakdown (top contributors), same pattern as ScoreASnapshotTest. */
    private void printBreakdown(SchoolSchedule schedule) {
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
                List<Row> rows = sd.getConstraintMatchTotalMap().values().stream()
                        .map(total -> {
                            BendableScore s = (BendableScore) total.getScore();
                            return new Row(total.getConstraintRef().id(), ScoreCompat.hardOf(s),
                                    ScoreCompat.mediumOf(s), ScoreCompat.softOf(s), total.getConstraintMatchCount());
                        })
                        .filter(r -> r.hard != 0 || r.medium != 0 || r.soft != 0)
                        .sorted(Comparator
                                .comparingLong((Row r) -> Math.abs(r.hard) + Math.abs(r.medium) + Math.abs(r.soft))
                                .reversed())
                        .limit(12)
                        .collect(Collectors.toList());
                System.out.printf("  %-46s %10s %12s %12s %10s%n", "constraint", "hard", "medium", "soft", "matches");
                for (Row r : rows) {
                    System.out.printf("  %-46s %10d %12d %12d %10d%n", r.id, r.hard, r.medium, r.soft, r.matches);
                }
            }
        } catch (Exception e) {
            System.out.println("  (breakdown unavailable: " + e + ")");
        }
    }

    private record Row(String id, long hard, long medium, long soft, long matches) {
    }

    /** Phase-2 Compactness LS, replicated field-for-field from solverConfig.xml phase 2 (see
     *  {@link Phase2SmokeSnapshotTest#phase2LocalSearch}); single budget cap for the smoke. */
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
