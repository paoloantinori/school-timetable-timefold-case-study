package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
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
 * TASK-550 TDD: the room-tail finisher must close the -83 banco's tail. The fixture is the
 * task550-cure-congiunte delivery export (score -83hard, the run whose entire hard residue was
 * 83 room-null moda labs: Modello/Moulage 42 + Confezione 41). The prediction written BEFORE the
 * code (iterations-542.md, 25/08 13:10) is {@code H29 83 -> <20}; the pin is {@code <= 25}
 * (prediction + slack) and strictly better than entry. The pass must also add no H1/H3 teacher or
 * group conflict and no H2 room double-booking.
 */
class RoomTailFinisherRegressionTest {

    @BeforeEach
    @AfterEach
    void flagAndStaticsOff() {
        System.clearProperty(RoomTailFinisher.FLAG);
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setAllRoomsStatic(List.of());
    }

    @Test
    void inertWhenFlagOff() {
        Fixture fx = load("task550/roomnull83-cure-congiunte.json.gz");
        publishStatics(fx);
        // ratified DEFAULT ON (25/08): the explicit literal "false" is the only opt-out
        System.setProperty(RoomTailFinisher.FLAG, "false");
        RoomTailFinisher.Result r = RoomTailFinisher.repair(fx.schedule,
                (lesson, ts) -> lesson.timeslot = ts,
                (lesson, room) -> lesson.room = room);
        assertEquals(0, r.nullBefore() + r.nullAfter() + r.freeCellAssignments() + r.evictions()
                + r.pullLastMoves() + r.swaps(), "flag OFF (the default) must leave the grid untouched");
    }

    @Test
    void closesTheMinus83BancoTail() {
        Fixture fx = load("task550/roomnull83-cure-congiunte.json.gz");
        publishStatics(fx);
        int[] baseline = conflicts(fx.schedule); // H2 carries the banco's 121 legal guest pairs
        int holesBaseline = interiorHoles(fx.schedule);
        System.setProperty(RoomTailFinisher.FLAG, "true");
        RoomTailFinisher.Result r = RoomTailFinisher.repair(fx.schedule,
                (lesson, ts) -> lesson.timeslot = ts,
                (lesson, room) -> lesson.room = room);
        System.out.println("ROOM-TAIL banco: " + r.nullBefore() + " -> " + r.nullAfter()
                + " (free-cell " + r.freeCellAssignments() + ", evictions " + r.evictions()
                + ", pull-last " + r.pullLastMoves() + ", swaps " + r.swaps() + ")");
        assertEquals(83, r.nullBefore(), "the fixture must carry the banco's 8G09-lab nulls");
        assertTrue(r.nullAfter() < r.nullBefore(), "must close part of the tail");
        assertTrue(r.nullAfter() <= 25,
                "prediction was <20 (pin 25 with slack), got " + r.nullAfter());
        int[] after = conflicts(fx.schedule);
        assertTrue(after[0] <= baseline[0] && after[1] <= baseline[1] && after[2] <= baseline[2],
                "the pass must add no H1/H3/H2 conflict (H1 " + baseline[0] + "->" + after[0]
                        + ", H3 " + baseline[1] + "->" + after[1]
                        + ", H2 " + baseline[2] + "->" + after[2] + ")");
        int holesAfter = interiorHoles(fx.schedule);
        assertEquals(holesBaseline, holesAfter,
                "the moves are pull-last/swaps: no hole may open at a vacated cell ("
                        + holesBaseline + " -> " + holesAfter + ")");
        // cellOk's gate chain covers H1/H3/H5/VRP but NOT H7 (teacher daily cap): the pass's
        // cross-day swaps could overload a teacher's day; pin that they don't (baseline compare,
        // the banco itself must be clean at entry).
        assertEquals(0, h7Violations(fx.schedule), "no teacher may exceed the H7 daily cap");
    }

    /** Teachers over their effective maxDailyHours (H7), real teachers only. */
    private static int h7Violations(SchoolSchedule schedule) {
        Map<String, Integer> perTeacherDay = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.getTeacher() == null || l.getTeacher().pseudo) {
                continue;
            }
            perTeacherDay.merge(l.getTeacher().name + "|" + l.timeslot.getDate(), 1, Integer::sum);
        }
        int violations = 0;
        for (var e : perTeacherDay.entrySet()) {
            Teacher t = findTeacher(schedule, e.getKey().substring(0, e.getKey().indexOf('|')));
            if (e.getValue() > t.getEffectiveMaxDailyHours()) {
                violations++;
            }
        }
        return violations;
    }

    private static Teacher findTeacher(SchoolSchedule schedule, String name) {
        for (var t : schedule.getTeachers()) {
            if (t.name.equals(name)) {
                return t;
            }
        }
        throw new IllegalStateException("teacher not in schedule: " + name);
    }

    /** Interior-gap (H17-shaped) count per (group, date), slot keys in POSITIONAL rank (the
     * solver's H17 semantics: a lunch break between slot ranks is adjacency, not a hole). */
    private static int interiorHoles(SchoolSchedule schedule) {
        // per-date slot sequence: distinct start times sorted, mapped to 0..n-1
        Map<java.time.LocalDate, java.util.TreeMap<Integer, Integer>> rankByDate = new java.util.HashMap<>();
        for (Timeslot ts : schedule.getTimeslots()) {
            rankByDate.computeIfAbsent(ts.getDate(), k -> new java.util.TreeMap<>())
                    .put(ts.startDateTime.getHour() * 60 + ts.startDateTime.getMinute(), 0);
        }
        for (var e : rankByDate.entrySet()) {
            int i = 0;
            for (var it : e.getValue().keySet()) {
                e.getValue().put(it, i++);
            }
        }
        Map<String, java.util.TreeMap<Integer, Lesson>> day = new java.util.HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.getStudentGroup() == null) {
                continue;
            }
            int minute = l.timeslot.startDateTime.getHour() * 60 + l.timeslot.startDateTime.getMinute();
            int rank = rankByDate.get(l.timeslot.getDate()).get(minute);
            day.computeIfAbsent(l.getStudentGroup().name + "|" + l.timeslot.getDate(),
                    k -> new java.util.TreeMap<>()).put(rank, l);
        }
        int holes = 0;
        for (var perDay : day.values()) {
            if (perDay.size() < 2) {
                continue;
            }
            int prev = -1;
            for (int slot : perDay.keySet()) {
                if (prev >= 0 && slot > prev + 1) {
                    holes += (slot - prev - 1);
                }
                prev = slot;
            }
        }
        return holes;
    }

    private static void publishStatics(Fixture fx) {
        SchoolSchedule.setAllTimeslotsStatic(fx.schedule.getTimeslots());
        SchoolSchedule.setAllRoomsStatic(fx.rooms);
    }

    /** {H1, H3, H2} raw pair-overlap counts; H2 is nonzero at baseline (guest pairs are H2-exempt). */
    private static int[] conflicts(SchoolSchedule schedule) {
        Map<String, Integer> teacherCell = new HashMap<>();
        Map<String, Integer> groupCell = new HashMap<>();
        Map<String, Integer> roomCell = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null) {
                continue;
            }
            String cell = l.timeslot.getDate() + "|" + l.timeslot.startDateTime;
            if (l.getTeacher() != null && !l.getTeacher().pseudo) {
                teacherCell.merge(l.getTeacher().name + "|" + cell, 1, Integer::sum);
            }
            if (l.getStudentGroup() != null) {
                groupCell.merge(l.getStudentGroup().name + "|" + cell, 1, Integer::sum);
            }
            if (l.room != null) {
                roomCell.merge(l.room.id + "|" + cell, 1, Integer::sum);
            }
        }
        int h1 = teacherCell.values().stream().mapToInt(v -> v - 1).filter(v -> v > 0).sum();
        int h3 = groupCell.values().stream().mapToInt(v -> v - 1).filter(v -> v > 0).sum();
        int h2 = roomCell.values().stream().mapToInt(v -> v - 1).filter(v -> v > 0).sum();
        return new int[] { h1, h3, h2 };
    }

    private record Fixture(SchoolSchedule schedule, List<Room> rooms) {
    }

    /** Rebuilds a domain SchoolSchedule from an export-shaped fixture (plain POJOs, no DB). */
    private static Fixture load(String resource) {
        InputStream stream = RoomTailFinisherRegressionTest.class.getResourceAsStream("/" + resource);
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
            List<Room> rooms = new ArrayList<>();
            Map<Long, Room> roomById = new HashMap<>();
            for (JsonNode n : root.get("rooms")) {
                Room r = new Room();
                r.id = n.get("id").asLong();
                r.name = n.get("name").asText();
                r.capacity = n.path("capacity").asInt(0);
                if (n.has("tags")) {
                    for (JsonNode t : n.get("tags")) {
                        r.tags.add(t.asText());
                    }
                }
                rooms.add(r);
                roomById.put(r.id, r);
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
                    x.maxDailyHours = tn.path("maxDailyHours").isInt() ? tn.get("maxDailyHours").asInt() : null;
                    return x;
                });
                JsonNode gn = c.get("studentGroup");
                StudentGroup g = groups.computeIfAbsent(gn.get("name").asText(), k -> new StudentGroup(k, 20));
                Course course = new Course(c.get("subject").asText(), t, g,
                        c.path("totalHours").asInt(0));
                if (c.has("requiredRoomTags")) {
                    for (JsonNode tag : c.get("requiredRoomTags")) {
                        course.requiredRoomTags.add(tag.asText());
                    }
                }
                Lesson l = new Lesson();
                l.id = n.get("id").asLong();
                l.course = course;
                l.pinned = n.path("pinned").asBoolean(false);
                l.promotedPin = n.path("promotedPin").asBoolean(false);
                if (n.hasNonNull("timeslot")) {
                    l.timeslot = tsById.get(n.get("timeslot").get("id").asLong());
                }
                if (n.hasNonNull("room")) {
                    l.room = roomById.get(n.get("room").get("id").asLong());
                }
                lessons.add(l);
            }
            SchoolSchedule schedule = new SchoolSchedule(timeslots, rooms, List.of(),
                    List.of(), List.of(), new ArrayList<>(groups.values()),
                    new ArrayList<>(teachers.values()), List.of(), lessons, List.of());
            return new Fixture(schedule, rooms);
        } catch (Exception e) {
            throw new IllegalStateException("fixture " + resource + " unreadable", e);
        }
    }
}
