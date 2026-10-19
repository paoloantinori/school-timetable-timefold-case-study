package com.school.planner.integration;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;
import ai.timefold.solver.core.api.solver.event.NewBestSolutionEvent;
import com.school.planner.domain.*;
import com.school.planner.solver.TimeslotSequence;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive solver integration tests that verify feasibility (hardScore >= 0)
 * with progressively harder scenarios.
 * <p>
 * Unlike ScheduleResourceTest which uses REST API polling, these tests inject
 * SolverManager directly for faster, more controlled testing.
 * </p>
 * <p>
 * Key design principles for inherently feasible test data:
 * <ul>
 *   <li>Keep utilization low (~25-40%) to ensure solutions exist</li>
 *   <li>Each (teacher, group) pair gets ONE course (no multi-subject per pair)</li>
 *   <li>Hours per course ≤ timeslots per day to allow flexible daily distribution</li>
 *   <li>Total lessons ≤ rooms × timeslots × 50% (slack for solver flexibility)</li>
 * </ul>
 * </p>
 */
@QuarkusTest
class SchoolScheduleSolverIntegrationTest {

    private static final Long TEST_PROBLEM_ID = 999L;

    @Inject
    SolverManager<SchoolSchedule> solverManager;

    @Inject
    SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    @Inject
    EntityManager em;

    @AfterEach
    @Transactional
    void cleanup() {
        // Clear existing data (respecting foreign key order)
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        // Note: LabBlock DELETE removed in TASK-228 - LabBlocks are deprecated
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
        em.createQuery("DELETE FROM Room").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();

        // Clear static state
        SchoolSchedule.setAllTimeslotsStatic(Collections.emptyList());
        SchoolSchedule.setAllRoomsStatic(Collections.emptyList());
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Collections.emptyMap());
        SchoolSchedule.setTeacherAvailabilitiesStatic(Collections.emptyList());
        SchoolSchedule.setSchoolHolidaysStatic(Collections.emptyList());
        SchoolSchedule.setTimeslotSequenceStatic(null);
    }

    // ─── Scenario 1: Minimal (fast CI test) ───────────────────────────────────

    /**
     * Fast sanity check for CI.
     * <p>
     * Design: 2 teachers × 2 groups = 4 unique (teacher, group) pairs.
     * Each pair gets ONE course with 2 hours.
     * With 12 timeslots (2 days) and 3 rooms, capacity = 36 slots.
     * We create 4 courses × 2h = 8 lessons (22% utilization).
     * </p>
     * <p>
     * Very slack capacity ensures the solver can always find a feasible solution.
     * </p>
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testMinimalScenarioFeasibility() throws Exception {
        List<Teacher> teachers = createTeachers(2);
        List<StudentGroup> groups = createGroups(2);
        List<Room> rooms = createRooms(3);
        List<Timeslot> timeslots = createTimeslots(12); // 2 days

        // Create ONE course per (teacher, group) pair
        // 2 teachers × 2 groups = 4 courses, each with 2 hours = 8 lessons
        List<Course> courses = createOneToOneCourses(teachers, groups, 2);
        List<Lesson> lessons = createLessons(courses);

        // Solve
        SchoolSchedule solution = solve(timeslots, rooms, groups, lessons);

        // Assert feasibility
        assertFeasible(solution);
        assertEquals(8, solution.getLessons().size(), "Should have 8 lessons");
    }

    // ─── Scenario 2: Representative (~12 lessons) ──────────────────────────────

    /**
     * Realistic school scenario with moderate utilization.
     * <p>
     * Design: 3 teachers × 3 groups = 9 unique pairs.
     * Each pair gets ONE course with 1 hour (plus 3 extra hours for one pair).
     * With 18 timeslots (3 days) and 4 rooms, capacity = 72 slots.
     * We create 9 courses × 1h + 3h = 12 lessons (17% utilization).
     * </p>
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testRepresentativeScenarioFeasibility() throws Exception {
        List<Teacher> teachers = createTeachers(3);
        List<StudentGroup> groups = createGroups(3);
        List<Room> rooms = createRooms(4);
        List<Timeslot> timeslots = createTimeslots(18); // 3 days

        // Each (teacher, group) pair gets ONE course with 1 hour
        List<Course> courses = createOneToOneCourses(teachers, groups, 1);
        List<Lesson> lessons = createLessons(courses);

        // Solve
        SchoolSchedule solution = solve(timeslots, rooms, groups, lessons);

        // Assert feasibility
        assertFeasible(solution);
        assertEquals(9, solution.getLessons().size(), "Should have 9 lessons");
    }

    // ─── Scenario 3: Stress (~20 lessons) ──────────────────────────────────────

    /**
     * Stress test with more entities but still low utilization.
     * <p>
     * Design: 4 teachers × 5 groups = 20 unique pairs.
     * Each pair gets ONE course with 1 hour.
     * With 30 timeslots (5 days) and 5 rooms, capacity = 150 slots.
     * We create 20 courses × 1h = 20 lessons (13% utilization).
     * </p>
     * <p>
     * Tagged as "slow" for optional CI exclusion.
     * </p>
     */
    @Test
    @Tag("slow")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testStressScenarioFeasibility() throws Exception {
        List<Teacher> teachers = createTeachers(4);
        List<StudentGroup> groups = createGroups(5);
        List<Room> rooms = createRooms(5);
        List<Timeslot> timeslots = createTimeslots(30); // 5 days

        // Each (teacher, group) pair gets ONE course with 1 hour
        List<Course> courses = createOneToOneCourses(teachers, groups, 1);
        List<Lesson> lessons = createLessons(courses);

        // Solve
        SchoolSchedule solution = solve(timeslots, rooms, groups, lessons);

        // Assert feasibility
        assertFeasible(solution);
        assertEquals(20, solution.getLessons().size(), "Should have 20 lessons");
    }

    // ─── Solver Execution ────────────────────────────────────────────────────

    /**
     * Solve the problem using SolverManager directly.
     */
    private SchoolSchedule solve(List<Timeslot> timeslots, List<Room> rooms,
                                  List<StudentGroup> groups, List<Lesson> lessons) throws Exception {
        // Set static references required by ValueRangeProviders
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(rooms);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(new HashMap<>());
        SchoolSchedule.setTeacherAvailabilitiesStatic(Collections.emptyList());
        SchoolSchedule.setSchoolHolidaysStatic(Collections.emptyList());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));

        // Create problem instance
        SchoolSchedule problem = new SchoolSchedule(
                timeslots,
                rooms,
                Collections.emptyList(),  // stagePatterns
                Collections.emptyList(),  // teacherAvailabilities
                Collections.emptyList(),  // schoolHolidays
                groups,
                Collections.emptyList(),  // teachers
                Collections.emptyList(),  // courses
                lessons,
                Collections.emptyList()   // labAssistants
        );

        // Solve synchronously using solveAndListen
        SolverJob<SchoolSchedule> solverJob = solverManager.solveBuilder().withProblemId(TEST_PROBLEM_ID).withProblemFinder(id -> problem).withBestSolutionEventConsumer((NewBestSolutionEvent<SchoolSchedule> event) -> {
        }).run();

        // Wait for completion
        return solverJob.getFinalBestSolution();
    }

    /**
     * Assert that the solution is feasible (hardScore >= 0).
     */
    private void assertFeasible(SchoolSchedule solution) {
        assertNotNull(solution, "Solution should not be null");
        BendableScore score = solution.getScore();
        assertNotNull(score, "Score should not be null");

        long hardScore = ScoreCompat.hardOf(score);
        String explanation = "";
        if (hardScore < 0) {
            // Debug-only duplicate of ScheduleService.computeConstraintMatchSnapshot's
            // community-edition workaround (analyze() is Enterprise-only). Kept inline
            // to avoid exposing the service's private internals to tests; if the
            // DefaultSolutionManager cast breaks on a Timefold upgrade, fix both sites.
            try {
                var factory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager)
                        .getScoreDirectorFactory();
                try (var scoreDirector = factory.createScoreDirectorBuilder()
                        .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                        .withLookUpEnabled(false)
                        .withExpectShadowVariablesInCorrectState(false)
                        .build()) {
                    scoreDirector.setWorkingSolution(solution);
                    scoreDirector.calculateScore();
                    StringBuilder sb = new StringBuilder("\nConstraint totals:");
                    scoreDirector.getConstraintMatchTotalMap().forEach((ref, total) ->
                            sb.append("\n  ").append(ref.id())
                              .append(" -> ").append(total.getScore())
                              .append(" (").append(total.getConstraintMatchCount()).append(" matches)"));
                    explanation = sb.toString();
                }
            } catch (Exception e) {
                explanation = "\n(constraint totals unavailable: " + e + ")";
            }
        }
        assertTrue(hardScore >= 0,
                "Solution should be feasible (hardScore >= 0), but was: " + hardScore +
                ". Full score: " + score + explanation);

        // Additional verification: check that all lessons have timeslots assigned.
        // Note: rooms are assigned in Phase 5 (Room Refinement) and may be null
        // after short test runs. With allowsUnassigned=true, a solution can be feasible
        // even if rooms are not yet assigned.
        long assignedCount = solution.getLessons().stream()
                .filter(l -> l.timeslot != null)
                .count();
        assertEquals(solution.getLessons().size(), assignedCount,
                "All lessons should have timeslots assigned in a feasible solution");
    }

    // ─── Data Creation Helpers ────────────────────────────────────────────────

    @Transactional
    List<Teacher> createTeachers(int count) {
        List<Teacher> teachers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Teacher teacher = new Teacher();
            teacher.name = "Teacher-" + (i + 1);
            teacher.nickname = "T" + (i + 1);
            teacher.persist();
            teachers.add(teacher);
        }
        return teachers;
    }

    @Transactional
    List<StudentGroup> createGroups(int count) {
        List<StudentGroup> groups = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StudentGroup group = new StudentGroup();
            group.name = "Group-" + (i + 1);
            group.size = 20;
            // Do NOT set maxHoursPerDay or startDate to avoid triggering H11/H14
            group.persist();
            groups.add(group);
        }
        return groups;
    }

    @Transactional
    List<Room> createRooms(int count) {
        List<Room> rooms = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Room room = new Room();
            room.name = "Room-" + (i + 1);
            room.capacity = 25;
            // Do NOT set tags to avoid triggering H-M2
            room.persist();
            rooms.add(room);
        }
        return rooms;
    }

    /**
     * Create consecutive timeslots starting from a Monday.
     * Creates 6 slots per day (08:00-14:00), Monday through Friday.
     */
    @Transactional
    List<Timeslot> createTimeslots(int count) {
        List<Timeslot> timeslots = new ArrayList<>();

        // Start from a Monday (Oct 6, 2025 is a Monday)
        LocalDate startDate = LocalDate.of(2025, 10, 6);
        LocalDateTime currentStart = startDate.atTime(8, 0);

        int created = 0;
        int dayOffset = 0;

        while (created < count) {
            // Create 6 slots per day (08:00-09:00, 09:00-10:00, etc.)
            for (int slot = 0; slot < 6 && created < count; slot++) {
                LocalDateTime start = currentStart.plusDays(dayOffset).plusHours(slot);
                LocalDateTime end = start.plusHours(1);

                Timeslot timeslot = new Timeslot(start, end);
                timeslot.persist();
                timeslots.add(timeslot);
                created++;
            }
            dayOffset++;

            // Skip weekends (every 5 days of slots, advance 2 more days)
            if (dayOffset % 5 == 0) {
                dayOffset += 2;
            }
        }

        return timeslots;
    }

    /**
     * Create ONE course per (teacher, group) pair.
     * This ensures no teacher has multiple courses for the same group,
     * eliminating inherent conflicts in the problem definition.
     *
     * @param teachers      Available teachers
     * @param groups        Available student groups
     * @param hoursPerCourse Hours for each course
     * @return List of created courses (teachers.size() × groups.size() courses)
     */
    @Transactional
    List<Course> createOneToOneCourses(List<Teacher> teachers, List<StudentGroup> groups,
                                        int hoursPerCourse) {
        List<Course> courses = new ArrayList<>();
        int courseNum = 1;

        for (Teacher teacher : teachers) {
            for (StudentGroup group : groups) {
                Course course = new Course();
                course.teacher = teacher;
                course.studentGroup = group;
                course.subject = "Subject-" + courseNum++;
                course.totalHours = hoursPerCourse;
                course.isLab = false;
                course.labBlockSize = 0;
                course.professionalizzanti = false;
                // Do NOT set requiredRoomTags to avoid triggering H-M2
                course.persist();
                courses.add(course);
            }
        }

        return courses;
    }

    /**
     * Create lesson entities from courses (one lesson per hour).
     */
    @Transactional
    List<Lesson> createLessons(List<Course> courses) {
        List<Lesson> lessons = new ArrayList<>();

        for (Course course : courses) {
            for (int h = 0; h < course.totalHours; h++) {
                Lesson lesson = new Lesson(course);
                lesson.pinned = false;
                lesson.persist();
                lessons.add(lesson);
            }
        }

        return lessons;
    }
}
