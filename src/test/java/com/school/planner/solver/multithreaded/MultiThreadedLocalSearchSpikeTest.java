package com.school.planner.solver.multithreaded;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;
import ai.timefold.solver.core.impl.score.director.ScoreDirectorFactory;
import ai.timefold.solver.core.impl.solver.DefaultSolverFactory;
import ai.timefold.solver.core.impl.solver.thread.ChildThreadType;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * Minimal spike: verify that createChildThreadScoreDirector(MOVE_THREAD) works with our domain.
 * If this passes, the full Fase 2 multi-threaded LS decider is mechanical adaptation.
 */
class MultiThreadedLocalSearchSpikeTest {

    @SuppressWarnings("unchecked")
    @Test
    void createChildThreadScoreDirector_producesWorkingScoreDirector() {
        SolverConfig config = SolverConfig.createFromXmlResource("solverConfig.xml");
        SolverFactory<SchoolSchedule> factory = SolverFactory.create(config);

        DefaultSolverFactory<SchoolSchedule> defaultFactory =
                (DefaultSolverFactory<SchoolSchedule>) factory;
        ScoreDirectorFactory<SchoolSchedule, ?> sdf = defaultFactory.getScoreDirectorFactory();
        assertNotNull(sdf);

        InnerScoreDirector<SchoolSchedule, ?> parentDirector = sdf.buildScoreDirector();
        assertNotNull(parentDirector);

        // Build a minimal but valid SchoolSchedule (all collections non-null)
        SchoolSchedule solution = buildMinimalSolution();

        parentDirector.setWorkingSolution(solution);

        // THE CRITICAL CALL: createChildThreadScoreDirector(MOVE_THREAD)
        InnerScoreDirector<SchoolSchedule, ?> childDirector =
                parentDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);

        assertNotNull(childDirector,
                "createChildThreadScoreDirector(MOVE_THREAD) must return a non-null director");
        assertNotSame(parentDirector, childDirector,
                "Child must be a separate instance with isolated constraint stream state");

        // Verify the child can evaluate the solution independently
        childDirector.setWorkingSolution(solution);
        var childScore = childDirector.calculateScore();
        assertNotNull(childScore, "Child director must calculate a score");

        parentDirector.close();
        childDirector.close();
    }

    @Test
    void childThreadTypeMoveThreadExists() {
        assertTrue(java.util.Arrays.asList(ChildThreadType.values()).contains(ChildThreadType.MOVE_THREAD),
                "ChildThreadType.MOVE_THREAD must exist");
    }

    /**
     * Build a minimal SchoolSchedule with empty (non-null) collections.
     * The score director requires all @ProblemFactCollection and @PlanningEntityCollection
     * fields to be non-null, even if empty.
     */
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
        // Other collection fields the SolutionDescriptor may visit
        return solution;
    }
}
