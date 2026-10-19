package com.school.planner.solver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.api.solver.phase.PhaseCommandContext;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningSolutionMetaModel;
import ai.timefold.solver.core.preview.api.move.Move;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-419: light unit test of {@link PhaseTransitionMarker}'s new box-dump branch (the
 * {@code writeBoxedTransitionDump} sibling added next to the TASK-415 {@code
 * writeTransitionDump}). Plain JUnit, no {@code @QuarkusTest}: {@link
 * PhaseTransitionMarker#changeWorkingSolution} already degrades gracefully without a CDI
 * container (its {@code PhaseProgressionService} lookup is wrapped in a try/catch that only
 * logs), so this exercises the real production method end to end - flag ON, a minimal
 * {@link PhaseCommandContext} double, one placed + one boxed + one (excluded) pinned-boxed
 * lesson - and asserts both files land with the documented format.
 */
class PhaseTransitionMarkerBoxDumpTest {

    @TempDir
    Path dumpDir;

    @BeforeEach
    void enableFlag() {
        System.setProperty("planner.phase-transition-dump.enabled", "true");
        System.setProperty("planner.phase-transition-dump.dir", dumpDir.toString());
    }

    @AfterEach
    void clearFlag() {
        System.clearProperty("planner.phase-transition-dump.enabled");
        System.clearProperty("planner.phase-transition-dump.dir");
    }

    @Test
    void writesBoxedSiblingAlongsidePlacedDump() throws IOException {
        Teacher placedTeacher = new Teacher("Rossi");
        Teacher boxedTeacher = new Teacher("Bianchi");
        StudentGroup group = new StudentGroup("1 TEST", 20);

        Course placedCourse = new Course("Matematica", placedTeacher, group, 1);
        Course boxedCourse = new Course("Scienze", boxedTeacher, group, 1);

        Timeslot timeslot = new Timeslot(LocalDateTime.of(2026, 9, 14, 8, 0),
                LocalDateTime.of(2026, 9, 14, 9, 0));
        timeslot.id = 1L;

        Lesson placed = new Lesson(placedCourse);
        placed.id = 1L;
        placed.timeslot = timeslot;
        placed.pinned = false;

        Lesson boxed = new Lesson(boxedCourse);
        boxed.id = 2L;
        // timeslot stays null: this is the unassigned (box) lesson under test.

        // A pinned lesson with a null timeslot must NOT appear in the boxed dump (defensive
        // filter, mirrors every other box-counting site in the codebase).
        Lesson pinnedBoxed = new Lesson(boxedCourse);
        pinnedBoxed.id = 3L;
        pinnedBoxed.pinned = true;

        SchoolSchedule solution = new SchoolSchedule(List.of(timeslot), List.of(), List.of(), List.of(),
                List.of(), List.of(group), List.of(placedTeacher, boxedTeacher),
                List.of(placedCourse, boxedCourse), List.of(placed, boxed, pinnedBoxed), List.of());

        PhaseTransitionMarker<SchoolSchedule> marker = new PhaseTransitionMarker<>();
        marker.setPhaseNumber(2);
        marker.setPhaseName("2. Test Phase");

        marker.changeWorkingSolution(new FakePhaseCommandContext(solution));

        Path placedFile = dumpDir.resolve("phase-2-2-test-phase.txt");
        Path boxedFile = dumpDir.resolve("phase-2-2-test-phase-boxed.txt");
        Path idFile = dumpDir.resolve("phase-2-2-test-phase-id.txt");
        assertTrue(Files.exists(placedFile), "placed dump must be written: " + placedFile);
        assertTrue(Files.exists(boxedFile), "boxed dump must be written alongside it: " + boxedFile);
        assertTrue(Files.exists(idFile), "id-tagged dump must be written alongside it: " + idFile);

        List<String> placedLines = Files.readAllLines(placedFile);
        assertEquals("SET", placedLines.get(0));
        assertEquals("1 TEST|2026-09-14|8|Matematica|Rossi|false", placedLines.get(1));

        List<String> boxedLines = Files.readAllLines(boxedFile);
        assertEquals("SET", boxedLines.get(0));
        assertEquals(List.of("SET", "1 TEST|Bianchi|Scienze"), boxedLines);

        // The id-tagged dump pairs row-for-row with the placed dump, prefixed by the lesson id
        // and suffixed by promotedPin, so two phase dumps from one solve match lesson-by-lesson.
        List<String> idLines = Files.readAllLines(idFile);
        assertEquals("SET", idLines.get(0));
        assertEquals("1|1 TEST|2026-09-14|8|Matematica|Rossi|false|false", idLines.get(1));
    }

    /** Minimal {@link PhaseCommandContext} test double: only {@link #getWorkingSolution()} and
     *  {@link #isPhaseTerminated()} are ever called by {@code changeWorkingSolution}; every
     *  other method is unused by the marker and throws if invoked. */
    private static final class FakePhaseCommandContext implements PhaseCommandContext<SchoolSchedule> {
        private final SchoolSchedule solution;

        FakePhaseCommandContext(SchoolSchedule solution) {
            this.solution = solution;
        }

        @Override
        public SchoolSchedule getWorkingSolution() {
            return solution;
        }

        @Override
        public boolean isPhaseTerminated() {
            return true;
        }

        @Override
        public PlanningSolutionMetaModel<SchoolSchedule> getSolutionMetaModel() {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public void execute(Move<SchoolSchedule> move) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public <Score_ extends Score<Score_>> Score_ executeAndCalculateScore(Move<SchoolSchedule> move) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public <Result_> Result_ executeTemporarilyAndCalculateScore(Move<SchoolSchedule> move,
                Function<SchoolSchedule, Result_> resultFunction,
                Function<SchoolSchedule, Result_> cleanupFunction) {
            // Timefold 2.7.0-rc-1 migration: PhaseCommandContext gained this
            // abstract method; the marker never calls it (see the class javadoc).
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public <Result_> Result_ executeTemporarily(Move<SchoolSchedule> move,
                Function<SchoolSchedule, Result_> temporarySolutionConsumer) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public <Result_> Result_ executeTemporarily(Move<SchoolSchedule> move,
                Function<SchoolSchedule, Result_> temporarySolutionConsumer,
                Function<SchoolSchedule, Result_> cleanupFunction) {
            // Timefold 2.7.0-rc-1: the 3-arg overload (with cleanup); never called here.
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public <Score_ extends Score<Score_>> Score_ executeTemporarily(Move<SchoolSchedule> move) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public <Score_ extends Score<Score_>> Score_ executeTemporarilyAndCalculateScore(Move<SchoolSchedule> move) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public <Result_> Result_ executeTemporarilyAndCalculateScore(Move<SchoolSchedule> move,
                Function<SchoolSchedule, Result_> temporarySolutionConsumer) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public <T> T lookUpWorkingObject(T problemFactOrPlanningEntity) {
            throw new UnsupportedOperationException("not used by this test");
        }
    }
}
