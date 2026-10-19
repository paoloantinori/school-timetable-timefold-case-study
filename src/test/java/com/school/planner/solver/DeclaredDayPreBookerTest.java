package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-564 TDD: the synthetic banco of the declared-day pre-booker. A group
 * whose construction left a declared day EMPTY while its pool sits unplaced:
 * the pass books the pool onto that very date, atomically (a cell the plan
 * cannot fill to quota stays EMPTY - one flat H34 beats H32 quota-hours).
 */
class DeclaredDayPreBookerTest {

    private static final LocalDate EMPTY_DAY = LocalDate.of(2027, 5, 10);
    private static final LocalDate FILLED_DAY = LocalDate.of(2027, 5, 11);

    private SchoolSchedule schedule;
    private List<Lesson> lessons;
    private Room room;

    @BeforeEach
    void setUp() {
        System.setProperty(DeclaredDayPreBooker.FLAG, "true");
        schedule = build(2, 3); // EMPTY_DAY quota 2; pool of 3 unplaced lessons
        lessons = schedule.getLessons();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(DeclaredDayPreBooker.FLAG);
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setPromotedLessonIdsStatic(List.of());
        SchoolSchedule.setDayFloorsStatic(Map.of());
        SchoolSchedule.setWeeklyTemplateStatic(null, null);
        DeclaredDayPreBooker.PREBOOK_REJECTS.clear();
    }

    @Test
    void fillsTheDeclaredDayToQuotaAtomically() {
        DeclaredDayPreBooker.Result r = DeclaredDayPreBooker.prebook(schedule,
                (l, ts) -> l.timeslot = ts);
        System.out.println("PREBOOK: " + r);
        assertEquals(1, r.cellsClosed(), "the empty declared day closes to quota");
        assertEquals(2, r.lessonsPlaced());
        assertEquals(0, r.cellsDeclined());
        assertEquals(0, r.conflictsAdded());
        assertEquals(List.of(0, 1), slotsAt(EMPTY_DAY),
                "the day fills contiguously from its first slot (H31 shape)");
        assertEquals(0, StudentDayCompaction.countConflicts(schedule));
        assertTrue(lessons.stream().filter(l -> l.timeslot != null && l.timeslot.getDate().equals(EMPTY_DAY))
                .allMatch(l -> l.room == null), "rooms stay null: phase 4 owns them (TASK-446 Alt-B)");
    }

    @Test
    void declinedCellStaysEmptyWhenThePlanCannotReachQuota() {
        // quota 3 but only 2 pool lessons: atomicity must leave the day EMPTY
        schedule = build(3, 2);
        lessons = schedule.getLessons();
        DeclaredDayPreBooker.Result r = DeclaredDayPreBooker.prebook(schedule,
                (l, ts) -> l.timeslot = ts);
        assertEquals(0, r.cellsClosed());
        assertEquals(1, r.cellsDeclined(), "the short plan is declined, not partially applied");
        assertEquals(0, r.lessonsPlaced());
        assertTrue(slotsAt(EMPTY_DAY).isEmpty(), "a partial fill would pay H32 quota-hours: worse than the flat H34");
        assertEquals(0, StudentDayCompaction.countConflicts(schedule));
    }

    @Test
    void flagOffIsANoop() {
        // RATIFIED DEFAULT ON (maintainer 29/08): the opt-out needs the literal "false".
        System.setProperty(DeclaredDayPreBooker.FLAG, "false");
        DeclaredDayPreBooker.Result r = DeclaredDayPreBooker.prebook(schedule,
                (l, ts) -> l.timeslot = ts);
        assertEquals(0, r.cellsClosed());
        assertEquals(0, r.lessonsPlaced());
        assertTrue(slotsAt(EMPTY_DAY).isEmpty());
    }

    @Test
    void absentFlagMeansOnSinceRatification() {
        // null/absent -> ON is the ratified default; a boot that lost the flag must
        // still run the pass (banner observability depends on the same predicate).
        System.clearProperty(DeclaredDayPreBooker.FLAG);
        assertTrue(DeclaredDayPreBooker.enabled());
        DeclaredDayPreBooker.Result r = DeclaredDayPreBooker.prebook(schedule,
                (l, ts) -> l.timeslot = ts);
        assertEquals(1, r.cellsClosed(), "absent flag behaves like ON");
    }

    @Test
    void h1BusyTeacherFallsToTheNextCandidate() {
        // another group holds T1 at EMPTY_DAY slot 0: the fill must use T2/T3
        StudentGroup other = new StudentGroup("OTHER", 20);
        other.id = 2L;
        Lesson blocker = lesson(90L, teacher(1L, "T1"), other, ts(EMPTY_DAY, 0));
        lessons.add(blocker);
        DeclaredDayPreBooker.Result r = DeclaredDayPreBooker.prebook(schedule,
                (l, ts) -> l.timeslot = ts);
        assertEquals(1, r.cellsClosed(), "the busy teacher's lesson is skipped, the day still closes");
        assertEquals(0, r.conflictsAdded());
        assertEquals(List.of(0, 1), slotsAt(EMPTY_DAY));
        // pin the FALLBACK MECHANISM, not just the shape: slot 0 must carry T2's
        // lesson (the first non-blocked candidate), not T1's
        Lesson atZero = lessons.stream()
                .filter(l -> l.timeslot != null && l.timeslot.getDate().equals(EMPTY_DAY)
                        && TimeslotSequence.slotIndexOf(l.timeslot) == 0
                        && l.getStudentGroup().id.equals(group().id))
                .findFirst().orElseThrow();
        assertEquals(31L, atZero.id, "the fallback lands T2's lesson at the blocked slot");
        assertEquals(0, StudentDayCompaction.countConflicts(schedule));
    }

    @Test
    void partialDayFillsTheInteriorGapFirst() {
        // FILLED_DAY starts with slots 0 and 2 occupied (a 1-slot HOLE at 1) and is
        // declared quota 3: the booking must land at the INTERIOR GAP (slot 1),
        // not at last+1 (which would leave an H17 hole). EMPTY_DAY carries NO
        // quota row in this test: it is not a target, so the ONE closed cell is
        // FILLED_DAY's (the review caught the fixture double-counting it).
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quota(FILLED_DAY, 3)));
        StudentGroup g = group();
        List<Lesson> out = new ArrayList<>();
        out.add(lesson(12L, teacher(12L, "G1"), g, timeslots.get(8)));  // slot 0
        out.add(lesson(13L, teacher(13L, "G2"), g, timeslots.get(10))); // slot 2
        for (int i = 0; i < 3; i++) {
            out.add(lesson(40L + i, teacher(20L + i, "P" + i), g, null));
        }
        schedule = new SchoolSchedule(timeslots, List.of(room), List.of(), List.of(), List.of(),
                List.of(g), List.of(), List.of(), out, List.of());
        lessons = schedule.getLessons();
        DeclaredDayPreBooker.Result r = DeclaredDayPreBooker.prebook(schedule,
                (l, ts) -> l.timeslot = ts);
        assertEquals(1, r.cellsClosed());
        assertEquals(List.of(0, 1, 2), slotsAt(FILLED_DAY),
                "the interior gap fills first: the day stays contiguous");
        assertEquals(0, r.conflictsAdded());
    }

    @Test
    void templateFaithfulCandidateWinsItsCell() {
        // P1's course is templated at (weekday, slot 0) of EMPTY_DAY: among the pool
        // it must be the one landing at slot 0
        int weekday = EMPTY_DAY.getDayOfWeek().getValue();
        int cell = weekday * TimeslotSequence.SLOTS_PER_DAY + 0;
        Lesson p1 = poolLesson(30L);
        SchoolSchedule.setWeeklyTemplateStatic(Map.of(p1.course.id, Set.of(cell)), Map.of());
        DeclaredDayPreBooker.Result r = DeclaredDayPreBooker.prebook(schedule,
                (l, ts) -> l.timeslot = ts);
        assertEquals(1, r.cellsClosed());
        Lesson atZero = lessons.stream()
                .filter(l -> l.timeslot != null && l.timeslot.getDate().equals(EMPTY_DAY)
                        && TimeslotSequence.slotIndexOf(l.timeslot) == 0)
                .findFirst().orElseThrow();
        assertEquals(30L, atZero.id, "the template-faithful candidate takes the template cell");
    }

    @Test
    void stagePoolLessonsAreNeverPlaced() {
        // a pseudo-teacher (Stage) lesson in the unplaced mass must stay unplaced
        Lesson stage = new Lesson();
        stage.id = 91L;
        Teacher pseudo = new Teacher("Stage");
        pseudo.id = 99L;
        pseudo.pseudo = true;
        stage.course = new Course("Stage", pseudo, group(), 100);
        lessons.add(stage);
        DeclaredDayPreBooker.Result r = DeclaredDayPreBooker.prebook(schedule,
                (l, ts) -> l.timeslot = ts);
        assertEquals(1, r.cellsClosed());
        assertTrue(stage.timeslot == null, "pseudo-teacher lessons are not bookable capacity");
    }

    @Test
    void h7CappedTeacherIsRefusedNotOverbooked() {
        // T1 carries maxDailyHours=1 and already teaches 1h on FILLED_DAY: the
        // pool lesson of T1 must be refused on any FILLED_DAY landing (the cap
        // gate), so the day's deficit is served by others and T1 stays unplaced
        Teacher capped = teacher(1L, "T1");
        capped.maxDailyHours = 1;
        Lesson existing = lesson(92L, capped, group(), timeslots.get(8)); // FILLED_DAY slot 0
        lessons.add(existing);
        DeclaredDayPreBooker.Result r = DeclaredDayPreBooker.prebook(schedule,
                (l, ts) -> l.timeslot = ts);
        Lesson t1Pool = poolLesson(30L);
        assertTrue(t1Pool.timeslot == null || !t1Pool.timeslot.getDate().equals(FILLED_DAY),
                "the capped teacher never exceeds the daily cap");
        assertEquals(0, r.conflictsAdded());
    }

    @Test
    void stageHoursDoNotMaskAnOrdinaryDeficit() {
        // a declared day carrying 4 placed STAGE hours and quota aula=2: Stage
        // hours must NOT count toward the quota (H32 counts non-Stage only), so
        // the day is a deficit-2 target and closes with 2 ordinary lessons
        // AFTER the Stage block (the grid is occupied, landing goes upward)
        LocalDate stageDay = LocalDate.of(2027, 5, 12);
        List<Timeslot> extended = new ArrayList<>(timeslots);
        for (java.time.LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
            Timeslot t = new Timeslot();
            t.id = (long) (extended.size() + 1);
            t.startDateTime = LocalDateTime.of(stageDay, start);
            extended.add(t);
        }
        timeslots = extended;
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        DailyActivityQuota mixed = new DailyActivityQuota();
        mixed.studentGroup = group();
        mixed.date = stageDay;
        mixed.aulaHours = 2;
        mixed.labHours = 0;
        mixed.stageHours = 4;
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(mixed));
        Teacher ps = new Teacher("Stage");
        ps.id = 99L;
        ps.pseudo = true;
        StudentGroup g = group();
        List<Lesson> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) { // the Stage block occupies slots 0..3
            Lesson s = new Lesson();
            s.id = 60L + i;
            s.course = new Course("Stage", ps, g, 100);
            s.timeslot = timeslots.get(16 + i);
            out.add(s);
        }
        for (int i = 0; i < 2; i++) {
            out.add(lesson(70L + i, teacher(30L + i, "Q" + i), g, null));
        }
        schedule = new SchoolSchedule(timeslots, List.of(room), List.of(), List.of(), List.of(),
                List.of(g), List.of(), List.of(), out, List.of());
        lessons = schedule.getLessons();
        DeclaredDayPreBooker.Result r = DeclaredDayPreBooker.prebook(schedule,
                (l, ts) -> l.timeslot = ts);
        assertEquals(1, r.cellsClosed(), "the ordinary deficit is NOT masked by Stage hours");
        assertEquals(List.of(4, 5), slotsAt(stageDay).subList(4, 6),
                "the ordinary hours land after the Stage block (contiguous upward)");
        assertEquals(2, r.lessonsPlaced());
    }

    @Test
    void h28SplitLandingIsRefused() {
        // the course C already sits at slots 0 and 2 of a day (with a DIFFERENT
        // course lesson at 1); landing another C at 4 (non-adjacent to the span
        // [0,2]) would split the block: refused. The landing at 3 (span[1]+1)
        // is legal and closes.
        LocalDate day = LocalDate.of(2027, 5, 13);
        List<Timeslot> extended = new ArrayList<>(timeslots);
        for (java.time.LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
            Timeslot t = new Timeslot();
            t.id = (long) (extended.size() + 1);
            t.startDateTime = LocalDateTime.of(day, start);
            extended.add(t);
        }
        timeslots = extended;
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(quota(day, 5)));
        StudentGroup g = group();
        Teacher tc = teacher(40L, "TC");
        Teacher to = teacher(41L, "TO");
        List<Lesson> out = new ArrayList<>();
        out.add(lesson(80L, tc, g, timeslots.get(16))); // C at slot 0
        out.add(lesson(81L, to, g, timeslots.get(17))); // other course at 1
        out.add(lesson(82L, tc, g, timeslots.get(18))); // C at slot 2 (span [0,2])
        // pool: one C lesson, one other-course lesson
        Lesson cPool = lesson(83L, tc, g, null);
        Lesson oPool = lesson(84L, teacher(42L, "TO2"), g, null);
        out.add(cPool);
        out.add(oPool);
        schedule = new SchoolSchedule(timeslots, List.of(room), List.of(), List.of(), List.of(),
                List.of(g), List.of(), List.of(), out, List.of());
        lessons = schedule.getLessons();
        DeclaredDayPreBooker.Result r = DeclaredDayPreBooker.prebook(schedule,
                (l, ts) -> l.timeslot = ts);
        // quota 5, hours 3, deficit 2: landings at 3 and 4. At 3 both candidates
        // are span-legal (span[1]+1); at 4 the C lesson must be refused (slot 4
        // is neither inside [0,2] nor adjacent), so the OTHER course fills it.
        assertEquals(1, r.cellsClosed());
        assertEquals(2, r.lessonsPlaced());
        assertEquals(0, r.conflictsAdded());
        int cSlot = cPool.timeslot != null ? TimeslotSequence.slotIndexOf(cPool.timeslot) : -1;
        assertTrue(cSlot == 3 || cSlot == -1,
                "the C lesson only lands adjacent to its span (slot 3), never at 4");
    }

    // ── fixture ──

    private static StudentGroup groupRef;

    private static StudentGroup group() {
        if (groupRef == null) {
            groupRef = new StudentGroup("G", 20);
            groupRef.id = 1L;
        }
        return groupRef;
    }

    private List<Timeslot> timeslots;

    private Timeslot ts(LocalDate date, int slot) {
        return timeslots.get(slot);
    }

    private Lesson poolLesson(long id) {
        return lessons.stream().filter(l -> l.id == id).findFirst().orElseThrow();
    }

    /** EMPTY_DAY: declared quota {@code emptyQuota}, ZERO lessons placed (the
     *  construction's outcome this banco reproduces). FILLED_DAY: quota 2, two
     *  placed lessons (slots 0,1). Pool: {@code poolSize} unplaced lessons of G
     *  with distinct teachers (T1, T2, T3...). */
    private SchoolSchedule build(int emptyQuota, int poolSize) {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quota(EMPTY_DAY, emptyQuota), quota(FILLED_DAY, 2)));
        timeslots = new ArrayList<>();
        for (LocalDate d : List.of(EMPTY_DAY, FILLED_DAY)) {
            for (java.time.LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
                Timeslot t = new Timeslot();
                t.id = (long) (timeslots.size() + 1);
                t.startDateTime = LocalDateTime.of(d, start);
                timeslots.add(t);
            }
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        room = new Room("R", 22, List.of());
        room.id = 50L;
        StudentGroup g = group();
        List<Lesson> out = new ArrayList<>();
        out.add(lesson(10L, teacher(10L, "F1"), g, timeslots.get(8)));
        out.add(lesson(11L, teacher(11L, "F2"), g, timeslots.get(9)));
        for (int i = 0; i < poolSize; i++) {
            out.add(lesson(30L + i, teacher(1L + i, "T" + (i + 1)), g, null));
        }
        return new SchoolSchedule(timeslots, List.of(room), List.of(), List.of(), List.of(),
                List.of(g), List.of(), List.of(), out, List.of());
    }

    private static DailyActivityQuota quota(LocalDate date, int hours) {
        DailyActivityQuota q = new DailyActivityQuota();
        q.studentGroup = group();
        q.date = date;
        q.aulaHours = hours;
        q.labHours = 0;
        q.stageHours = 0;
        return q;
    }

    private static Teacher teacher(long id, String name) {
        Teacher t = new Teacher(name);
        t.id = id;
        return t;
    }

    private static Lesson lesson(long id, Teacher t, StudentGroup g, Timeslot ts) {
        Lesson l = new Lesson();
        l.id = id;
        l.course = new Course("S" + id, t, g, 10);
        l.course.id = id * 10;
        if (ts != null) {
            l.timeslot = ts;
        }
        return l;
    }

    private List<Integer> slotsAt(LocalDate date) {
        List<Integer> out = new ArrayList<>();
        for (Lesson l : lessons) {
            if (l.timeslot != null && l.timeslot.getDate().equals(date)
                    && l.getStudentGroup() != null && l.getStudentGroup().id.equals(group().id)) {
                out.add(TimeslotSequence.slotIndexOf(l.timeslot));
            }
        }
        out.sort(null);
        return out;
    }
}
