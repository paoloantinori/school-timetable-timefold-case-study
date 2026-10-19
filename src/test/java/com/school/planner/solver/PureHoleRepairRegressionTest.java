package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-542 iter-27 TDD: the PORTED pure hole repairer must close the SAME percentages as
 * the validated offline prototype on the SAME fixtures - the scalability curve's points
 * (iterations-542.md iter-24-ter) turned into regression pins:
 *
 * <pre>
 *   N= 63 (attempt1) -> 6 residual holes  (90% closed, 49 moves, 0.7s)
 *   N=243 (attempt5) -> 41 residual holes (83% closed, 192 moves, 20s)
 *   N=365 (attempt9) -> 86 residual holes (76% closed, 241 moves, 25s)
 * </pre>
 *
 * <p>Fixtures: the night run's POST_CONSTRUCTION checkpoint grids joined to the winner
 * export's metadata (room=null, the offline prototype's exact inputs). The pin below is
 * {@code <= residual + slack}: the port's family (3) is a single cross-day exchange
 * (O fills the hole, mover takes O's cell) where the prototype did swap+pull, so the
 * port may close MORE; it must never close dramatically LESS. The VRP gate is inert here
 * by construction (no statics published -> every timeslot is in range).
 */
class PureHoleRepairRegressionTest {

    @BeforeEach
    @AfterEach
    void flagOff() {
        System.clearProperty(PureHoleRepair.FLAG);
        SchoolSchedule.setAllTimeslotsStatic(List.of());
    }

    private void check(String resource, int expectedHolesBefore, int prototypeResidual, int slack) {
        Fixture fx = load(resource);
        SchoolSchedule schedule = fx.schedule;
        SchoolSchedule.setAllTimeslotsStatic(schedule.getTimeslots());
        System.setProperty(PureHoleRepair.FLAG, "true");
        PureHoleRepair.Result r = PureHoleRepair.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts,
                (lesson, room) -> lesson.room = room);
        System.out.println("PURE-HOLE " + resource + ": " + r.holesBefore() + " -> "
                + r.holesAfter() + " in " + r.moves() + " move-set(s)"
                + " (prototype residual " + prototypeResidual + ")");
        assertTrue(r.holesBefore() >= expectedHolesBefore - 5,
                "fixture must carry the expected hole volume, got " + r.holesBefore());
        assertTrue(r.holesAfter() <= prototypeResidual + slack,
                "the port must match the prototype's closure (residual "
                        + r.holesAfter() + " > prototype " + prototypeResidual + " + " + slack + ")");
        assertTrue(r.holesAfter() < r.holesBefore(), "must close something");
        // no H1/H3 conflict introduced: re-scan the mutated grid
        assertNoConflicts(schedule);
    }

    @Test
    void inertWhenFlagOff() {
        Fixture fx = load("task542/holes63-attempt1.json.gz");
        PureHoleRepair.Result r = PureHoleRepair.repair(fx.schedule,
                (lesson, ts) -> lesson.timeslot = ts,
                (lesson, room) -> lesson.room = room);
        assertTrue(r.moves() == 0 && r.holesAfter() == 0,
                "flag OFF (the default) must leave the grid untouched");
    }

    @Test
    void closesCurvePointN63() {
        check("task542/holes63-attempt1.json.gz", 63, 6, 4);
    }

    @Test
    void closesCurvePointN243() {
        check("task542/holes243-attempt5.json.gz", 243, 41, 12);
    }

    @Test
    void closesCurvePointN365() {
        check("task542/holes365-attempt9.json.gz", 365, 86, 22);
    }

    private static void assertNoConflicts(SchoolSchedule schedule) {
        Map<String, Integer> teacherCell = new HashMap<>();
        Map<String, Integer> groupCell = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null) continue;
            String k1 = (l.getTeacher() != null ? l.getTeacher().name : "?") + "|"
                    + l.timeslot.getDate() + "|" + l.timeslot.startDateTime;
            String k2 = (l.getStudentGroup() != null ? l.getStudentGroup().name : "?") + "|"
                    + l.timeslot.getDate() + "|" + l.timeslot.startDateTime;
            if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                teacherCell.merge(k1, 1, Integer::sum);
            }
            groupCell.merge(k2, 1, Integer::sum);
        }
        int h1 = teacherCell.values().stream().mapToInt(v -> v - 1).filter(v -> v > 0).sum();
        int h3 = groupCell.values().stream().mapToInt(v -> v - 1).filter(v -> v > 0).sum();
        assertTrue(h1 == 0 && h3 == 0, "the pass must add no H1/H3 conflict (H1=" + h1 + ", H3=" + h3 + ")");
    }

    /** Rebuilds a domain SchoolSchedule from an export-shaped fixture (plain POJOs, no DB). */
    private record Fixture(SchoolSchedule schedule) {
    }

    private static Fixture load(String resource) {
        InputStream stream = PureHoleRepairRegressionTest.class.getResourceAsStream("/" + resource);
        // clone pubblico: le fixture .gz sono espulse (regola zero-binari,
        // TASK-684) e il test si salta; nel repo PRIVATO (publish-prep
        // presente) una fixture assente e' una PERDITA e fallisce forte
        if (stream == null) {
            if (java.nio.file.Files.exists(java.nio.file.Path.of("publish-prep"))) {
                org.junit.jupiter.api.Assertions.fail(
                        "fixture persa nel repo privato: " + resource);
            }
            Assumptions.assumeTrue(false, "fixture assente (clone pubblico): " + resource);
        }
        try (InputStream in = new GZIPInputStream(stream)) {
            JsonNode root = new ObjectMapper().readTree(in);
            List<Timeslot> timeslots = new ArrayList<>();
            Map<Long, Timeslot> tsById = new HashMap<>();
            for (JsonNode n : root.get("timeslots")) {
                Timeslot ts = new Timeslot();
                ts.id = n.get("id").asLong();
                ts.startDateTime = LocalDateTime.parse(n.get("startDateTime").asText());
                timeslots.add(ts);
                tsById.put(ts.id, ts);
            }
            Map<String, Teacher> teachers = new HashMap<>();
            Map<String, StudentGroup> groups = new HashMap<>();
            List<Lesson> lessons = new ArrayList<>();
            for (JsonNode n : root.get("lessons")) {
                JsonNode c = n.get("course");
                JsonNode tn = c.get("teacher");
                Teacher t = teachers.computeIfAbsent(tn.get("name").asText(), k -> {
                    Teacher x = new Teacher(k);
                    x.pseudo = tn.path("pseudo").asBoolean(false);
                    return x;
                });
                JsonNode gn = c.get("studentGroup");
                StudentGroup g = groups.computeIfAbsent(gn.get("name").asText(), k -> new StudentGroup(k, 20));
                Course course = new Course(c.get("subject").asText(), t, g,
                        c.path("totalHours").asInt(0));
                Lesson l = new Lesson();
                l.id = n.get("id").asLong();
                l.course = course;
                l.pinned = n.path("pinned").asBoolean(false);
                l.promotedPin = n.path("promotedPin").asBoolean(false);
                if (n.hasNonNull("timeslot")) {
                    l.timeslot = tsById.get(n.get("timeslot").get("id").asLong());
                }
                lessons.add(l);
            }
            SchoolSchedule schedule = new SchoolSchedule(timeslots, List.<Room>of(), List.of(),
                    List.of(), List.of(), new ArrayList<>(groups.values()),
                    new ArrayList<>(teachers.values()), List.of(), lessons, List.of());
            return new Fixture(schedule);
        } catch (Exception e) {
            throw new IllegalStateException("fixture " + resource + " unreadable", e);
        }
    }
}
