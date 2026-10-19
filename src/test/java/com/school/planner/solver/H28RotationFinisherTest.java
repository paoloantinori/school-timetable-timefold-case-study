package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.TestDataBuilder;
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-595 unit coverage for {@link H28RotationFinisher}: detection sharing the
 * provider's own H28 gates, the block-ordering enumeration, and every gate the pass
 * promises (flag-off inertness, pin-blocked cells, H1-blocked cells with slot-set
 * preservation on revert, the lexicographic candidate preference). The near-delivery
 * validation lives in the witness bench (PLANNER_TEST_WITNESS_BENCH=1,
 * {@code h28FinisherGate}); this is the fast synthetic layer of the localized-testing
 * pyramid.
 */
class H28RotationFinisherTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    private Teacher alice;
    private Teacher bob;
    private StudentGroup g1;
    private List<Timeslot> timeslots;
    private long nextId;

    @BeforeEach
    @AfterEach
    void flagAndStaticsOff() {
        System.clearProperty(H28RotationFinisher.FLAG);
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setGoldenCoPresenceDepthStatic(java.util.Map.of());
    }

    // ── the pass's own gates ──────────────────────────────────────────────

    @Test
    void inertWhenFlagOff() {
        Fixture fx = witnessDay();
        // Ratified DEFAULT ON 17/09 (bounded-no-op directive): the is-off contract
        // pins the explicit opt-out, not the old default.
        System.setProperty(H28RotationFinisher.FLAG, "false");
        try {
            H28RotationFinisher.Result r = H28RotationFinisher.repair(fx.schedule,
                    (lesson, ts) -> lesson.timeslot = ts);
            assertEquals(H28RotationFinisher.Result.noop(), r,
                    "explicit opt-out must leave the schedule untouched");
            assertEquals("A A B B B A", order(fx.cellLessons),
                    "no lesson may move when opted out");
        } finally {
            System.clearProperty(H28RotationFinisher.FLAG);
        }
    }

    @Test
    void repairsTheWitnessPatternToContiguousBlocks() {
        Fixture fx = witnessDay();
        System.setProperty(H28RotationFinisher.FLAG, "true");
        H28RotationFinisher.Result r = H28RotationFinisher.repair(fx.schedule,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(1, r.cellsViolating(), "the witness day is one violating cell");
        assertEquals(1, r.cellsRepaired(), "one cell repaired");
        assertEquals(0, r.pinBlocked() + r.locked(), "no negatives expected here");
        // lexicographic preference: Alice's block first (the TASK-593 witness shape)
        assertEquals("A A A B B B", order(fx.cellLessons),
                "the A..B..A day must close into teacher-contiguous blocks");
        assertEquals(List.of(8, 9, 10, 11, 12, 13), occupiedHours(fx.cellLessons),
                "the cell still occupies exactly its own slot set (H17-safe by construction)");
    }

    @Test
    void pinInCellBlocksTheWholeCell() {
        Fixture fx = witnessDay();
        fx.cellLessons.get(5).pinned = true; // the trailing Alice lesson
        System.setProperty(H28RotationFinisher.FLAG, "true");
        H28RotationFinisher.Result r = H28RotationFinisher.repair(fx.schedule,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(1, r.cellsViolating());
        assertEquals(0, r.cellsRepaired());
        assertEquals(1, r.pinBlocked(), "a pin in the cell is the honest terminal outcome");
        assertEquals("A A B B B A", order(fx.cellLessons), "nothing may move");
    }

    @Test
    void h1BlockedDayLocksAndPreservesTheSlotSet() {
        // Both orderings die on H1: Alice cannot sit at slots 10 or 11 (another group's
        // lessons hold her there), and her block needs three consecutive cell slots.
        Fixture fx = witnessDay();
        fx.lessons.add(foreignLesson(alice, 10));
        fx.lessons.add(foreignLesson(alice, 11));
        System.setProperty(H28RotationFinisher.FLAG, "true");
        H28RotationFinisher.Result r = H28RotationFinisher.repair(fx.schedule,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(1, r.cellsViolating());
        assertEquals(0, r.cellsRepaired());
        assertEquals(1, r.locked(), "every ordering tripped H1: locked, counted once");
        assertEquals(0, r.pinBlocked());
        assertEquals("A A B B B A", order(fx.cellLessons), "the failed candidates revert");
        assertEquals(List.of(8, 9, 10, 11, 12, 13), occupiedHours(fx.cellLessons),
                "the cell still occupies exactly its own slot set (revert is total)");
    }

    @Test
    void secondOrderingWinsWhenTheFirstDiesOnH1() {
        // Alice's block would need slot 10, where she already teaches another group;
        // Bob-first (the second lexicographic ordering) is legal and must be applied.
        Fixture fx = witnessDay();
        fx.lessons.add(foreignLesson(alice, 10));
        System.setProperty(H28RotationFinisher.FLAG, "true");
        H28RotationFinisher.Result r = H28RotationFinisher.repair(fx.schedule,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(1, r.cellsRepaired(), "the pass must not stop at the first ordering");
        assertEquals("B B B A A A", order(fx.cellLessons),
                "Bob-first is the first legal ordering");
    }

    @Test
    void coPresenceDepthGateBlocksTheFirstOrderingOnly() {
        // Alice's course is single-tagged (the witness subject's own shape); Bob's is
        // untagged. Two foreign tagged lessons already sit at 10:00, exactly at the
        // golden depth: Alice-first (slots 8,9,10) would push the (tag, 10:00) count to
        // 3 > depth 2, Bob-first (Alice at 11,12,13, unknown cells) must win instead.
        Fixture fx = witnessDay();
        fx.cellLessons.get(0).course.requiredRoomTags.add("termoidraulica");
        fx.lessons.add(foreignTaggedLesson("F1", 10));
        fx.lessons.add(foreignTaggedLesson("F2", 10));
        SchoolSchedule.setGoldenCoPresenceDepthStatic(java.util.Map.of(
                SchoolSchedule.coPresenceCell("termoidraulica", DAY.getDayOfWeek(),
                        LocalTime.of(10, 0)), 2));
        System.setProperty(H28RotationFinisher.FLAG, "true");
        H28RotationFinisher.Result r = H28RotationFinisher.repair(fx.schedule,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(1, r.cellsRepaired(), "the co-presenza gate must skip, not lock");
        assertEquals("B B B A A A", order(fx.cellLessons),
                "the ordering pushing (tag, 10:00) past its golden depth is rejected");
    }

    @Test
    void coPresenceDepthGateLocksWhenEveryOrderingExceeds() {
        // Depth 2 already reached at BOTH 10:00 and 11:00: Alice's 3-lesson block needs
        // three consecutive cell slots and every window contains one of the two.
        Fixture fx = witnessDay();
        fx.cellLessons.get(0).course.requiredRoomTags.add("termoidraulica");
        fx.lessons.add(foreignTaggedLesson("F1", 10));
        fx.lessons.add(foreignTaggedLesson("F2", 10));
        fx.lessons.add(foreignTaggedLesson("F3", 11));
        fx.lessons.add(foreignTaggedLesson("F4", 11));
        SchoolSchedule.setGoldenCoPresenceDepthStatic(java.util.Map.of(
                SchoolSchedule.coPresenceCell("termoidraulica", DAY.getDayOfWeek(),
                        LocalTime.of(10, 0)), 2,
                SchoolSchedule.coPresenceCell("termoidraulica", DAY.getDayOfWeek(),
                        LocalTime.of(11, 0)), 2));
        System.setProperty(H28RotationFinisher.FLAG, "true");
        H28RotationFinisher.Result r = H28RotationFinisher.repair(fx.schedule,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(1, r.locked(), "every ordering exceeds a golden depth: locked");
        assertEquals("A A B B B A", order(fx.cellLessons), "the cell reverts untouched");
    }

    @Test
    void duplicateSlotCellIsSkippedLocked() {
        // A corrupt entry state (two cell lessons on one slot = H3 shape) must not reach
        // the permutation: the Grid maps overwrite on duplicate slots, so the cell is
        // skipped whole and nothing moves.
        Fixture fx = witnessDay();
        fx.cellLessons.get(1).timeslot = fx.cellLessons.get(0).timeslot; // 9:00 -> 8:00
        System.setProperty(H28RotationFinisher.FLAG, "true");
        H28RotationFinisher.Result r = H28RotationFinisher.repair(fx.schedule,
                (lesson, ts) -> lesson.timeslot = ts);
        assertEquals(1, r.locked(), "the duplicate-slot cell reads locked, not repaired");
        assertEquals(0, r.cellsRepaired());
    }

    // ── the pure helpers ──────────────────────────────────────────────────

    @Test
    void isViolatingMirrorsTheConstraintShape() {
        // A..B..A: violation; A..A..B and A..B..B: clean; single teacher: clean
        assertTrue(H28RotationFinisher.isViolating(List.of(lessonAt("A", 8), lessonAt("B", 9),
                lessonAt("A", 10))), "A..B..A must read as violating");
        assertFalse(H28RotationFinisher.isViolating(List.of(lessonAt("A", 8),
                lessonAt("A", 9), lessonAt("B", 10))), "A..A..B is clean");
        assertFalse(H28RotationFinisher.isViolating(List.of(lessonAt("A", 8),
                lessonAt("B", 9), lessonAt("B", 10))), "A..B..B is clean");
        assertFalse(H28RotationFinisher.isViolating(List.of(lessonAt("A", 8),
                lessonAt("A", 9), lessonAt("A", 10))), "single teacher is clean");
        assertTrue(H28RotationFinisher.isViolating(List.of(lessonAt("A", 8), lessonAt("A", 9),
                lessonAt("B", 10), lessonAt("B", 11), lessonAt("B", 12), lessonAt("A", 13))),
                "the delivered witness day must read as violating");
    }

    @Test
    void orderingsEnumerateEveryBlockPermutationLexicographically() {
        Lesson a1 = lessonAt("Alice", 8);
        Lesson a2 = lessonAt("Alice", 9);
        Lesson b1 = lessonAt("Bob", 10);
        Map<String, List<Lesson>> blocks = new TreeMap<>();
        blocks.put("Alice", List.of(a1, a2));
        blocks.put("Bob", List.of(b1));
        List<List<Lesson>> out = H28RotationFinisher.orderings(blocks);
        assertEquals(2, out.size(), "2 blocks -> 2 orderings");
        assertEquals(List.of(a1, a2, b1), out.get(0), "lexicographic first: Alice block");
        assertEquals(List.of(b1, a1, a2), out.get(1), "then Bob block");
    }

    // ── synthetic fixtures ────────────────────────────────────────────────

    private record Fixture(SchoolSchedule schedule, List<Lesson> lessons, List<Lesson> cellLessons) {
    }

    /** The delivered witness shape: one (group, subject, day) cell running A,A,B,B,B,A
     * over slots 8..13 of a single day (all canonical schema slots). */
    private Fixture witnessDay() {
        alice = new Teacher("Alice");
        bob = new Teacher("Bob");
        g1 = new StudentGroup("G03", 20);
        timeslots = new ArrayList<>();
        for (int hour = 8; hour <= 13; hour++) {
            timeslots.add(slot(hour));
        }
        // two incarichi rows, same subject, different teachers: the double-teacher lab
        // day the witness documented (Lesson.getTeacher() is the course's teacher, so a
        // single Course object could never produce a two-teacher cell)
        Course courseA = new Course("Laboratorio Termoidraulico", alice, g1, 3);
        Course courseB = new Course("Laboratorio Termoidraulico", bob, g1, 3);
        List<Lesson> cell = new ArrayList<>();
        cell.add(lesson(courseA, 8));
        cell.add(lesson(courseA, 9));
        cell.add(lesson(courseB, 10));
        cell.add(lesson(courseB, 11));
        cell.add(lesson(courseB, 12));
        cell.add(lesson(courseA, 13));
        List<Lesson> all = new ArrayList<>(cell);
        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(),
                List.of(), List.of(), List.of(g1), List.of(alice, bob), List.of(), all, List.of());
        // isSlotLegalFresh consults the static timeslot list; everything else stays empty
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        return new Fixture(schedule, all, cell);
    }

    /** A lesson of ANOTHER group at (teacher, hour): the H1 blocker. */
    private Lesson foreignLesson(Teacher t, int hour) {
        StudentGroup other = new StudentGroup("G11", 20);
        Course course = new Course("Altro corso", t, other, 2);
        return lesson(course, hour);
    }

    /** A single-tagged lesson of ANOTHER group/teacher at hour: the co-presenza
     * count contributor (rooms stay null: H2 reads rooms, these never enter it). */
    private Lesson foreignTaggedLesson(String teacherName, int hour) {
        Teacher t = teachersByName.computeIfAbsent(teacherName, Teacher::new);
        StudentGroup other = new StudentGroup("gruppo " + teacherName, 20);
        Course course = new Course("Lab ospite", t, other, 1);
        course.requiredRoomTags.add("termoidraulica");
        return lesson(course, hour);
    }

    private Lesson lesson(Course course, int hour) {
        return TestDataBuilder.lesson(++nextId, course, timeslots.get(hour - 8), null);
    }

    /** A detached lesson pair-building helper for the pure-predicate tests. Teacher
     * instances are cached by name: the constraint (and the shared gates) compare
     * teacher OBJECTS, so two "A" lessons built as separate instances would never pair. */
    private final Map<String, Teacher> teachersByName = new HashMap<>();

    private Lesson lessonAt(String teacherName, int hour) {
        Teacher t = teachersByName.computeIfAbsent(teacherName, Teacher::new);
        StudentGroup g = new StudentGroup("G", 20);
        Lesson l = TestDataBuilder.lesson(++nextId, new Course("S", t, g, 1), slot(hour), null);
        return l;
    }

    private Timeslot slot(int hour) {
        return TestDataBuilder.timeslot(++nextId, LocalDateTime.of(DAY, LocalTime.of(hour, 0)));
    }

    /** The cell's teacher sequence in slot order, e.g. "A A B B B A". */
    private static String order(List<Lesson> cell) {
        List<Lesson> sorted = new ArrayList<>(cell);
        sorted.sort((a, b) -> Integer.compare(a.timeslot.startDateTime.getHour(),
                b.timeslot.startDateTime.getHour()));
        StringBuilder sb = new StringBuilder();
        for (Lesson l : sorted) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(l.getTeacher().name.substring(0, 1));
        }
        return sb.toString();
    }

    /** The cell's occupied hours, sorted: the slot-set invariant in assertable form. */
    private static List<Integer> occupiedHours(List<Lesson> cell) {
        return cell.stream()
                .map(l -> l.timeslot.startDateTime.getHour()).sorted().toList();
    }
}
