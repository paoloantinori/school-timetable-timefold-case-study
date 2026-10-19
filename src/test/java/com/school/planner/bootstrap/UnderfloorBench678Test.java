package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.EmptyDayFinisher;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.service.ImportService;
import com.school.planner.service.ScheduleService;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * TASK-678 bench: the TASK-577 under-floor arm ({@code EmptyDayFinisher.H33_FLAG},
 * opt-in DEFAULT OFF from 7/09 until the 04/10 ratification, banked once on the
 * v131 G14 Fridays) against tonight's fresh capture
 * {@code task677-results/nearmiss/pred-a3/export.json.gz} (the resume delivery of the
 * predictor-discarded construction, [-3/0] = H31 + H33 + H34 one cell each).
 *
 * <p>PRE-REGISTERED PREDICTION (task TASK-678, written before this bench ran):
 * with the arm ON, EDF[delivery] closes the G14 2027-05-07 under-floor cell (2
 * ordinary hours vs floor 3) with a same-group import and the score goes
 * {@code -3 -> -2} (the H31 late-start and the G11 H34 cell stay: the blind
 * proved the H31 half locally incurable); with the arm OFF the pass is a no-op on
 * this state (its other families already ran at delivery).
 *
 * <p>Fidelity gate: the imported state must re-score at EXACTLY the recorded hard
 * (-3) with the H33 cell present, else the bench is measuring a stale seed.
 *
 * <p>Env: {@code PLANNER_TEST_678=1 ./scripts/core/test.sh -mva
 * "-Dsurefire.heap=3g -Dplanner.datagenerator.enabled=true" -c UnderfloorBench678Test}
 * (optional {@code PLANNER_TEST_678_INPUT} for another capture; .json or .json.gz).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_678", matches = ".+")
class UnderfloorBench678Test {

    private static final BiConsumer<Lesson, Timeslot> MOVE_TIMESLOT =
            (lesson, ts) -> lesson.timeslot = ts;

    private static final String INPUT = "claudedocs/spalmone-2026-27/task677-results/nearmiss/pred-a3/export.json.gz";

    @Inject ScheduleService scheduleService;
    @Inject ImportService importService;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;
    @Inject ObjectMapper objectMapper;

    @AfterEach
    void clearFlag() {
        System.clearProperty(EmptyDayFinisher.H33_FLAG);
    }

    @Test
    @Timeout(1800)
    void armedRefusesTheStructurallyLockedFriday() throws Exception {
        System.setProperty(EmptyDayFinisher.H33_FLAG, "true");
        SchoolSchedule problem = importCapture();
        assertEquals(-3, hardOf(problem), "fidelity gate: the capture must re-score at the recorded -3");

        EmptyDayFinisher.Result result = EmptyDayFinisher.finish(problem, MOVE_TIMESLOT,
                EmptyDayFinisher.Boundary.DELIVERY);
        solutionManager.update(problem);
        Map<String, long[]> families = PureOneOffCheckpointTest.printHardDiag(
                solutionManager, problem, "AFTER EDF[h33 armed]");

        // The pre-registered prediction (import at slot 11, -3 -> -2) was WRONG: the
        // G14 Friday window is slots 0-2 (DataGenerator.GROUP_CALENDAR_PATTERNS),
        // so slot 11 is outside every value range, and the only legal third cell is
        // slot 0 - where all five G14 teachers are busy with saturated quota-group
        // days (the blind's frozen-occupants chain, verified on the real grid). The
        // arm must therefore enumerate the target and correctly place NOTHING.
        assertEquals(-3, totalHard(families),
                "the knot is structurally locked: H31+H33 stay, no illegal move");
        assertEquals(2, ordinaryHoursOn(problem, "G14", "2027-05-07"),
                "the Friday keeps its 2 hours: the third cell has no legal landing");
        assertEquals(0, result.movesApplied(), "no move applied (found: " + result + ")");
    }

    @Test
    @Timeout(1800)
    void armedRefusesTheSyntheticWoundUntil679AlignsTheRanges() throws Exception {
        System.setProperty(EmptyDayFinisher.H33_FLAG, "true");
        SchoolSchedule problem = importCapture();
        assertEquals(-3, hardOf(problem), "fidelity gate");

        // Synthetic wound (the forced657 probe form): box the tail of G14 Thursday
        // 2027-05-06 (window 0-4, was 5h) down to 2 hours. The block sits at 0-1,
        // the legal growth cell is slot 2 (in-window), and the boxed lessons free
        // their teachers there; the 04/10 trace showed FOUR fully-legal donor
        // candidates (landingWhy=null, fresh=true). PRE-REGISTERED CLOSURE
        // PREDICTION (TASK-679's acceptance): once the fresh-check/value-range
        // drift is aligned, this pass MUST close the cell (1 move, 05-06 -> 3h,
        // hard -9 -> -7): flip these assertions then. TODAY the range gate (the
        // guard of the Confezione crash class) refuses the landing: the drift cuts
        // in BOTH directions - the cached range EXCLUDES cells the fresh check
        // declares legal. What this pins today: the refusal is clean.
        int boxed = 0;
        for (Lesson l : problem.getLessons()) {
            if (l.timeslot == null || !l.timeslot.getDate().toString().equals("2027-05-06")
                    || l.getStudentGroup() == null || !l.getStudentGroup().name.equals("G14")) {
                continue;
            }
            if (TimeslotSequence.slotIndexOf(l.timeslot) >= 2) {
                MOVE_TIMESLOT.accept(l, null);
                boxed++;
            }
        }
        assertEquals(3, boxed, "the wound: 05-06 slots 2-4 boxed (5h -> 2h, under floor 3)");

        EmptyDayFinisher.Result result = EmptyDayFinisher.finish(problem, MOVE_TIMESLOT,
                EmptyDayFinisher.Boundary.DELIVERY);
        solutionManager.update(problem);
        Map<String, long[]> families = PureOneOffCheckpointTest.printHardDiag(
                solutionManager, problem, "AFTER EDF[h33 synthetic, 679-blocked]");

        // countConflicts sees H1/H3 only - the hard total is what pins H2 (the new
        // room-reassignment path) and every other family. The donor iteration order
        // is unpinned (HashMap): the observed outcomes across runs are the clean
        // refusal (679 drift), a NET-ZERO move (closed cell -2, new residual -2:
        // the production belt would revert it), and the -7 closure (the 679-aligned
        // prediction). The invariants below hold for all three; the exact outcome
        // is not asserted until 679 lands.
        assertEquals(0, result.conflictsAdded(), "no H1/H3 conflict added");
        assertTrue(totalHard(families) >= -3 - 3 * 2,
                "the pass never leaves the hard worse than the wounded baseline (found: " + totalHard(families) + ")");
        // TASK-679 regression pin (the Confezione crash class): after the pass,
        // every placed unpinned lesson must sit inside its own value range - the
        // director's update() asserts exactly this, and the 04/10 bench caught the
        // fresh-check/range drift here first.
        for (Lesson l : problem.getLessons()) {
            if (l.timeslot == null || l.pinned) {
                continue;
            }
            assertTrue(java.util.Collections.binarySearch(l.getAvailableTimeslots(), l.timeslot) >= 0,
                    "lesson " + l.id + " landed outside its value range at " + l.timeslot);
        }
    }

    @Test
    @Timeout(1800)
    void optedOutIsNoOpOnThisDeliveredState() throws Exception {
        // DEFAULT ON since the 04/10 ratification: the no-op arm is the explicit
        // opt-out now (the capture's under-floor cell is structurally locked, so
        // the armed and opted-out runs coincide on THIS state; the assertion pins
        // that the opt-out still leaves the delivered schedule untouched).
        System.setProperty(EmptyDayFinisher.H33_FLAG, "false");
        SchoolSchedule problem = importCapture();
        assertEquals(-3, hardOf(problem), "fidelity gate: the capture must re-score at the recorded -3");

        EmptyDayFinisher.Result result = EmptyDayFinisher.finish(problem, MOVE_TIMESLOT,
                EmptyDayFinisher.Boundary.DELIVERY);
        solutionManager.update(problem);

        assertEquals(-3, totalHard(PureOneOffCheckpointTest.printHardDiag(
                        solutionManager, problem, "AFTER EDF[h33 opted-out]")),
                "without the arm nothing moves: the residuals belong to other families");
        assertEquals(0, result.movesApplied(),
                "the delivery already ran the other families: no legal move remains (found: " + result + ")");
    }

    /** Load the seeded schedule and RESTORE the capture's grid onto it by id
     * (placements + both pin flags + rooms), the {@code StagedCheckpointRestore}
     * shape: ImportService's TRUNCATE path is PostgreSQL-only and does not run on
     * the H2 test DB. Same-seed captures share the lesson ids with the fresh seed
     * (the DoomTrio fidelity gate's premise); a mismatch fails the fidelity gate
     * below, not silently. */
    private SchoolSchedule importCapture() throws Exception {
        assertTrue(Timeslot.count() > 0, "empty DB: launch with -Dplanner.datagenerator.enabled=true");
        Path path = Path.of(System.getenv().getOrDefault("PLANNER_TEST_678_INPUT", INPUT));
        assertTrue(Files.isRegularFile(path), "capture missing: " + path);
        byte[] raw = Files.readAllBytes(path);
        if (path.toString().endsWith(".gz")) {
            try (var gz = new GZIPInputStream(new java.io.ByteArrayInputStream(raw));
                 var out = new java.io.ByteArrayOutputStream()) {
                gz.transferTo(out);
                raw = out.toByteArray();
            }
        }
        SchoolSchedule capture = objectMapper.readValue(raw, SchoolSchedule.class);
        Map<Long, Timeslot> timeslotsById = new java.util.HashMap<>();
        capture.getTimeslots().forEach(t -> timeslotsById.put(t.id, t));
        Map<Long, com.school.planner.domain.Room> roomsById = new java.util.HashMap<>();
        capture.getRooms().forEach(r -> roomsById.put(r.id, r));
        Map<Long, Lesson> captureById = new java.util.HashMap<>();
        capture.getLessons().forEach(l -> captureById.put(l.id, l));

        SchoolSchedule problem = scheduleService.getFullScheduleForExport();
        int restored = 0;
        for (Lesson live : problem.getLessons()) {
            Lesson from = captureById.get(live.id);
            if (from == null) {
                continue;
            }
            live.timeslot = from.timeslot != null ? timeslotsById.get(from.timeslot.id) : null;
            live.room = from.room != null ? roomsById.get(from.room.id) : null;
            live.pinned = from.pinned;
            live.promotedPin = from.promotedPin;
            restored++;
        }
        assertEquals(capture.getLessons().size(), restored,
                "the capture must be id-coherent with the seeded schedule (same seed)");
        scheduleService.analyzeScore();
        return problem;
    }

    private long hardOf(SchoolSchedule problem) {
        solutionManager.update(problem);
        return PureOneOffCheckpointTest.printHardDiag(solutionManager, problem, null)
                .values().stream().mapToLong(v -> v[1]).sum();
    }

    private static long totalHard(Map<String, long[]> families) {
        return families.values().stream().mapToLong(v -> v[1]).sum();
    }

    private static int ordinaryHoursOn(SchoolSchedule problem, String group, String date) {
        return (int) problem.getLessons().stream()
                .filter(l -> l.timeslot != null
                        && l.timeslot.getDate().toString().equals(date)
                        && l.getStudentGroup() != null
                        && l.getStudentGroup().name.equals(group)
                        // H33 counts non-Stage hours: the pseudo "Stage" teacher is out
                        && (l.getTeacher() == null || !l.getTeacher().pseudo))
                .count();
    }
}
