package com.school.planner.solver.multithreaded;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;

/**
 * Smoke test: build a solver with MultiThreadedLocalSearchDecider wired via reflection,
 * run a minimal solve, verify it completes without crash/hang/deadlock.
 *
 * This is NOT a performance test (empty solution, no real moves to evaluate).
 * It validates the wiring: decider swap, thread pool startup, phase lifecycle.
 * A real A/B solve (Fase 3 proper) runs via baseline-golden.sh against the live app.
 */
class MultiThreadedLsSmokeTest {

    @Test
    void solverWithMultiThreadedDecider_buildsAndSolvesWithoutCrash() {
        // Build the multi-threaded solver (moveThreadCount=2, minimal for validation)
        SolverConfig config = SolverConfig.createFromXmlResource("solverConfig.xml");
        config.setSolutionClass(SchoolSchedule.class);
        config.setEntityClassList(java.util.List.of(Lesson.class));
        Solver<SchoolSchedule> solver = SolverFactory.<SchoolSchedule>create(config).buildSolver();
        int swapped = MultiThreadedLsWiring.wireMultiThreaded(solver, 2);
        // solverConfig.xml has LS phases, so the full-config wiring must swap at least one
        // (a 0 here would mean the flag silently dropped, the MAVEN_OPTS-lesson failure).
        assertTrue(swapped >= 1, "Expected at least one LS phase swapped, got " + swapped);

        // Build a minimal valid solution (empty but non-null collections)
        SchoolSchedule solution = buildMinimalSolution();

        SchoolSchedule result = solver.solve(solution);

        assertNotNull(result, "Solver must return a result (not null)");
        assertNotNull(result.getScore(), "Result must have a score");
    }

    private SchoolSchedule buildMinimalSolution() {
        SchoolSchedule solution = new SchoolSchedule();
        solution.setScore(ScoreCompat.ZERO);
        solution.setLessons(new ArrayList<>());
        solution.setCourses(new ArrayList<>());
        solution.setTeachers(new ArrayList<>());
        solution.setTimeslots(new ArrayList<>());
        solution.setRooms(new ArrayList<>());
        solution.setStudentGroups(new ArrayList<>());
        solution.setStagePatterns(new ArrayList<>());
        solution.setTeacherAvailabilities(new ArrayList<>());
        solution.setSchoolHolidays(new ArrayList<>());
        solution.setLabAssistants(new ArrayList<>());
        return solution;
    }
}
