package com.school.planner.solver;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-419 (staged solve): unit tests for the two SELECTION rules, {@link StagedPinPolicy}.
 * Pure Java (no Quarkus/Timefold context) - the class is deliberately free of both, so the
 * rules can be pinned without a solve. The statics {@link SchoolSchedule} holds are set here
 * and cleared again in {@link #clearStatics()}, since the whole suite shares one JVM.
 *
 * <p>Covered: the promotion selector (on-template yes, off-template no, an AUTHORITATIVE pin
 * never, a pseudo-teacher lesson never, an untemplated course never) and the demotion
 * ordering (least-constrained first = ascending M32 weight, budget-capped, only for a boxed
 * lesson that is genuinely DEADLOCKED). The DB side of the cycle (promote, demote, release,
 * conservation of the authoritative pins) is pinned by
 * {@code com.school.planner.service.StagedSolvePinLifecycleTest}.
 */
class StagedPinPolicyTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);

    private long nextId = 1;
    private final List<Timeslot> timeslots = new ArrayList<>();
    private final Map<String, Timeslot> timeslotByKey = new HashMap<>();
    private final List<Long> promotedIds = new ArrayList<>();

    @BeforeEach
    void seedTimeslots() {
        for (LocalDate date : List.of(MONDAY, TUESDAY, MONDAY.plusDays(7), TUESDAY.plusDays(7))) {
            for (int slot = 0; slot < 4; slot++) {
                Timeslot ts = new Timeslot();
                ts.id = nextId++;
                ts.startDateTime = LocalDateTime.of(date, java.time.LocalTime.of(8 + slot, 0));
                ts.endDateTime = ts.startDateTime.plusHours(1);
                timeslots.add(ts);
                timeslotByKey.put(date + "@" + slot, ts);
            }
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        // The suite shares one JVM: start every case with an EMPTY promotion registry, so a
        // pin is authoritative here only until this test's own promoted() registers it.
        promotedIds.clear();
        SchoolSchedule.setPromotedLessonIdsStatic(List.of());
    }

    @AfterEach
    void clearStatics() {
        SchoolSchedule.clearStaticState();
        System.clearProperty("planner.construction.pair-promotion.enabled");
        PairCellGuard.clear();
    }

    // ─── TASK-576 F9: the pair-form promotion ──────────────────────────────

    @Test
    void pairFormLessonIsPromotableOnlyWhenTheF9FlagIsOn() {
        StudentGroup group = group("G01");
        Teacher teacher = teacher("T16", false);
        Course course = course("Tecnologia", teacher, group);
        // NO course template published: the lesson is off the M32 reference but ON a
        // published PAIR cell (Monday slot 2, pair-keyed to this teacher+group)
        Lesson onPairCell = lesson(course, MONDAY, 2, false);
        PairCellGuard.publish(Map.of(group, Map.of(DayOfWeek.MONDAY,
                List.of(new WeeklyTemplateBuilder.TemplateCell(course, 2, 1)))));

        assertFalse(StagedPinPolicy.isPromotable(onPairCell),
                "F9 default OFF: the pair cell alone must not promote");

        System.setProperty("planner.construction.pair-promotion.enabled", "true");
        assertTrue(StagedPinPolicy.isPromotable(onPairCell),
                "F9 ON: the pair cell promotes - the pin never enters the delta-score market");

        // and a lesson of ANOTHER pair sitting on the same (group, weekday, slot) does not:
        // the guard is pair-keyed, so that lesson is off ITS own form
        Teacher other = teacher("T20", false);
        Course otherCourse = course("Merceologia", other, group);
        Lesson wrongPair = lesson(otherCourse, MONDAY, 2, false);
        assertFalse(StagedPinPolicy.isPromotable(wrongPair),
                "the pair key must match: another teacher's lesson on the same cell is off-form");
    }

    @Test
    void pairFormLessonWithoutThePairBuilderIsNeverPromotable() {
        // the guard is empty (course-based builder): whatever the flag says there is no
        // pair form to freeze onto - doubly inert
        System.setProperty("planner.construction.pair-promotion.enabled", "true");
        StudentGroup group = group("G01");
        Teacher teacher = teacher("T16", false);
        Course course = course("Tecnologia", teacher, group);
        Lesson placed = lesson(course, MONDAY, 2, false);
        assertFalse(StagedPinPolicy.isPromotable(placed));
    }

    // ─── promotion selector ────────────────────────────────────────────────

    @Test
    void promotesOnlyTheUnpinnedLessonSittingOnItsOwnTemplateCell() {
        StudentGroup group = group("G03");
        Teacher teacher = teacher("Rossi", false);
        Course course = course("Matematica", teacher, group);
        publishTemplate(Map.of(course.id, Set.of(cell(DayOfWeek.MONDAY, 1))), Map.of(course.id, 100));

        Lesson onCell = lesson(course, MONDAY, 1, false);
        Lesson offCell = lesson(course, MONDAY, 2, false);
        Lesson otherWeekSameCell = lesson(course, MONDAY.plusDays(7), 1, false);

        assertTrue(StagedPinPolicy.isPromotable(onCell), "on its template cell");
        assertFalse(StagedPinPolicy.isPromotable(offCell), "same day, wrong slot");
        assertTrue(StagedPinPolicy.isPromotable(otherWeekSameCell),
                "the template is a WEEKLY cell - any date with that weekday+slot is on-template");
        assertEquals(List.of(onCell, otherWeekSameCell),
                StagedPinPolicy.selectPromotions(List.of(onCell, offCell, otherWeekSameCell)));
    }

    @Test
    void neverPromotesAnAuthoritativePinAPseudoLessonOrAnUntemplatedCourse() {
        StudentGroup group = group("G03");
        Teacher real = teacher("Rossi", false);
        Teacher pseudo = teacher("Stage", true);
        Course templated = course("Matematica", real, group);
        Course pseudoCourse = course("Stage", pseudo, group);
        Course untemplated = course("Recuperi ed Approfondimenti", real, group);
        publishTemplate(
                Map.of(templated.id, Set.of(cell(DayOfWeek.MONDAY, 1)),
                        pseudoCourse.id, Set.of(cell(DayOfWeek.MONDAY, 1))),
                Map.of(templated.id, 100, pseudoCourse.id, 100));

        Lesson authoritative = lesson(templated, MONDAY, 1, true);
        Lesson pseudoLesson = lesson(pseudoCourse, MONDAY, 1, false);
        Lesson untemplatedLesson = lesson(untemplated, MONDAY, 1, false);
        Lesson boxedLesson = lesson(templated, null, -1, false);

        assertFalse(StagedPinPolicy.isPromotable(authoritative), "an authoritative pin is already pinned");
        assertFalse(StagedPinPolicy.isPromotable(pseudoLesson), "pseudo-teacher lessons are never templated");
        assertFalse(StagedPinPolicy.isPromotable(untemplatedLesson), "no template cell to sit on");
        assertFalse(StagedPinPolicy.isPromotable(boxedLesson), "an unplaced lesson sits on nothing");
        assertTrue(StagedPinPolicy.selectPromotions(
                List.of(authoritative, pseudoLesson, untemplatedLesson, boxedLesson)).isEmpty());
    }

    // ─── demotion ordering ─────────────────────────────────────────────────

    /**
     * The group's whole (promotion-blind) value range is held by promoted pins, so the boxed
     * lesson is DEADLOCKED: the two least-constrained blockers (lowest M32 weight = most VRP
     * room for their own course) are released, the scarce one is kept.
     */
    @Test
    void demotesTheLeastConstrainedBlockersFirstUpToTheBudget() {
        StudentGroup group = group("G03");
        Teacher teacher = teacher("Rossi", false);
        Course scarce = course("Laboratorio", teacher, group);
        Course middling = course("Scienze", teacher, group);
        Course free = course("Storia", teacher, group);
        Course boxedCourse = course("Matematica", teacher, group);
        publishTemplate(Map.of(), Map.of(scarce.id, 500, middling.id, 120, free.id, 20));

        List<Lesson> population = new ArrayList<>();
        // Every one of the 16 cells is taken by a promoted pin of one of the three courses.
        int index = 0;
        List<Course> occupants = List.of(scarce, middling, free);
        for (Timeslot ts : timeslots) {
            Lesson occupant = promoted(occupants.get(index % occupants.size()), ts);
            population.add(occupant);
            index++;
        }
        Lesson boxed = lesson(boxedCourse, null, -1, false);
        population.add(boxed);

        List<Lesson> demoted = StagedPinPolicy.selectDemotions(population, 2);

        assertEquals(2, demoted.size(), "the per-boxed-lesson budget caps the release");
        assertEquals(List.of(free.id, free.id),
                demoted.stream().map(l -> l.course.id).toList(),
                "least-constrained first: the weight-20 course is released before the weight-120 one");
    }

    @Test
    void leavesAlonePromotedPinsWhenTheBoxedLessonStillHasAFreeCell() {
        StudentGroup group = group("G03");
        Teacher teacher = teacher("Rossi", false);
        Course occupantCourse = course("Storia", teacher, group);
        Course boxedCourse = course("Matematica", teacher, group);
        publishTemplate(Map.of(), Map.of(occupantCourse.id, 20));

        List<Lesson> population = new ArrayList<>();
        for (Timeslot ts : timeslots.subList(0, timeslots.size() - 1)) {
            population.add(promoted(occupantCourse, ts));
        }
        population.add(lesson(boxedCourse, null, -1, false));

        assertTrue(StagedPinPolicy.selectDemotions(population, 2).isEmpty(),
                "one free cell is enough: this is a repair/LS coverage miss, not a pinning deadlock");
    }

    @Test
    void ignoresBlockersThatAreNotPromotedPins() {
        StudentGroup group = group("G03");
        Teacher teacher = teacher("Rossi", false);
        Course occupantCourse = course("Storia", teacher, group);
        Course boxedCourse = course("Matematica", teacher, group);
        publishTemplate(Map.of(), Map.of(occupantCourse.id, 20));

        List<Lesson> population = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            // AUTHORITATIVE pins (pinned, no promoted marker) and free lessons alike: neither
            // is this cycle's to release - the authoritative ones are the school's.
            Lesson occupant = lesson(occupantCourse, ts.getDate(), ts.startDateTime.getHour() - 8,
                    timeslots.indexOf(ts) % 2 == 0);
            population.add(occupant);
        }
        population.add(lesson(boxedCourse, null, -1, false));

        assertTrue(StagedPinPolicy.selectDemotions(population, 2).isEmpty(),
                "only pins this cycle created itself may be demoted");
    }

    /**
     * TASK-419 (2026-07-26), the A' class of the box discriminator: the boxed lesson DOES have
     * a conflict-free cell, so the old deadlock test (conflict-freedom alone) declared it "not
     * deadlocked" and released nothing - but that cell is H17-UNSAFE, so {@link BoxRepair},
     * the pass that would actually take it, refuses it and the lesson stays boxed forever.
     * Measured exactly like this on the val419 staged smoke: G12 / T01, 1 conflict-free
     * cell of 168, 0 of them H17-safe, and 49 H17-safe cells held by ex-promoted pins. With the
     * gate aligned to BoxRepair's own, the lesson reads as deadlocked and one of those blockers
     * is released.
     */
    @Test
    void demotesWhenEveryConflictFreeCellIsRefusedByBoxRepairsH17Gate() {
        StudentGroup group = group("G12");
        Teacher teacher = teacher("T01", false);
        StudentGroup otherGroup = group("G03");
        Course occupantCourse = course("Servizio stilistico", teacher, group);
        Course freeCourse = course("Storia", teacher, group);
        Course elsewhere = course("Tecnologia", teacher, otherGroup);
        Course boxedCourse = course("Servizio tecnico e stilistico", teacher, group);
        publishTemplate(Map.of(), Map.of(occupantCourse.id, 200));

        List<Lesson> population = new ArrayList<>();
        // Every cell of the three OTHER dates is held by a promoted pin: those days are
        // contiguous, so each of their (already occupied) cells is H17-SAFE for the boxed
        // lesson - they are the blockers a demotion can open.
        List<Lesson> blockers = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            if (MONDAY.equals(ts.getDate())) continue;
            Lesson blocker = promoted(occupantCourse, ts);
            blockers.add(blocker);
            population.add(blocker);
        }
        // Monday: the group has ONE lesson at slot 0; slot 1 is taken by the same teacher in
        // ANOTHER group (an H1 conflict, and not a promoted pin, so never demotable). That
        // leaves slots 2 and 3 conflict-free but DETACHED from the group's slot-0 run: placing
        // there turns a gapless day into a broken one, exactly what BoxRepair's H17 gate exists
        // to refuse.
        population.add(lesson(freeCourse, MONDAY, 0, false));
        population.add(lesson(elsewhere, MONDAY, 1, false));
        Lesson boxed = lesson(boxedCourse, null, -1, false);
        population.add(boxed);

        // The premise of the regression, asserted rather than assumed: conflict-freedom ALONE
        // still finds cells here, which is why the old gate demoted nothing.
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(population);
        assertTrue(boxed.getAvailableTimeslots().stream()
                        .anyMatch(ts -> StudentDayCompaction.isSlotConflictFree(boxed, ts, byTs)),
                "premise: a conflict-free cell exists (Monday slots 2-3)");

        List<Lesson> demoted = StagedPinPolicy.selectDemotions(population, 1);

        assertEquals(1, demoted.size(), "the deadlock is now seen, and the budget of 1 releases one blocker");
        assertTrue(blockers.contains(demoted.get(0)),
                "the released pin is one holding an H17-SAFE cell, never a Monday occupant");
        assertEquals(occupantCourse.id, demoted.get(0).course.id);
    }

    /**
     * The complement of the case above, so the gate cannot be over-tightened into always
     * demoting: a conflict-free cell that is ALSO H17-safe and cap-legal still means "not
     * deadlocked", and nothing is released.
     */
    @Test
    void leavesAlonePromotedPinsWhenAConflictFreeCellIsAlsoH17Safe() {
        StudentGroup group = group("G12");
        Teacher teacher = teacher("T01", false);
        Course occupantCourse = course("Servizio stilistico", teacher, group);
        Course freeCourse = course("Storia", teacher, group);
        Course boxedCourse = course("Servizio tecnico e stilistico", teacher, group);
        publishTemplate(Map.of(), Map.of(occupantCourse.id, 200));

        List<Lesson> population = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            if (MONDAY.equals(ts.getDate())) continue;
            population.add(promoted(occupantCourse, ts));
        }
        // Monday holds a contiguous run 0-1, so slot 2 EXTENDS it: conflict-free and H17-safe.
        population.add(lesson(freeCourse, MONDAY, 0, false));
        population.add(lesson(freeCourse, MONDAY, 1, false));
        population.add(lesson(boxedCourse, null, -1, false));

        assertTrue(StagedPinPolicy.selectDemotions(population, 2).isEmpty(),
                "a genuinely placeable cell is a repair/LS coverage miss, not a pinning deadlock");
    }

    /**
     * TASK-419 review F1: the H17 primitives must read AUTHORITATIVE pins, not the raw
     * {@code pinned} flag. A width-2 break between two EX-PROMOTED (promoted, hence
     * non-authoritative) pins is a break the score genuinely charges, so dropping the boxed
     * lesson into it REDUCES the day's H17 penalty and the cell must pass the gate. Read
     * through the raw flag the day would look "exempt at 0" before the placement and
     * "penalized at 1" after it, so the gate would refuse a cell that strictly improves the
     * very constraint it is protecting - and the lesson would be reported as deadlocked,
     * spending demotions on a box that was placeable all along.
     */
    @Test
    void acceptsTheCellInsideABreakBetweenTwoExPromotedPins() {
        StudentGroup group = group("G12");
        Teacher teacher = teacher("T01", false);
        Course occupantCourse = course("Servizio stilistico", teacher, group);
        Course boxedCourse = course("Servizio tecnico e stilistico", teacher, group);
        publishTemplate(Map.of(), Map.of(occupantCourse.id, 200));

        List<Lesson> population = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            // Monday keeps slots 1-2 free between two promoted pins: a REAL (non-exempt)
            // width-2 H17 break. Every other date is fully held, so Monday is the only hope.
            if (MONDAY.equals(ts.getDate()) && List.of(1, 2).contains(ts.startDateTime.getHour() - 8)) continue;
            population.add(promoted(occupantCourse, ts));
        }
        Lesson boxed = lesson(boxedCourse, null, -1, false);
        population.add(boxed);

        Timeslot hole = timeslotByKey.get(MONDAY + "@1");
        assertTrue(BoxRepair.passesPlacementGates(boxed, hole,
                        BoxRepair.buildGroupDaySlots(population), BoxRepair.buildTeacherDaily(population)),
                "filling a break between two EX-PROMOTED pins lowers the day's H17 penalty (2 -> 1)");
        assertTrue(StagedPinPolicy.selectDemotions(population, 2).isEmpty(),
                "the lesson is placeable, so nothing is deadlocked and no pin is spent");
    }

    /**
     * TASK-419 review F2: the blocker search must be SWAP-aware on the daily caps. Here every
     * date is held at the group's own {@code maxHoursPerDay} by promoted pins of that same
     * group, which is exactly the shape a frozen template produces. Asked as a DIRECT
     * placement ({@link BoxRepair#passesPlacementGates}) every cell is cap-refused - correctly,
     * the day is full - so the old blocker search mined nothing and the deadlock was reported
     * with no candidate to release. But the blocker IS the hour that would be freed: its unit
     * of the cap belongs to the swap, not to the arriving lesson.
     */
    @Test
    void demotesOnADayAlreadyAtMaxHoursPerDayWhenTheBlockerSharesTheGroup() {
        StudentGroup group = group("G12");
        group.maxHoursPerDay = 4;   // every seeded date holds exactly 4 slots: all at the cap
        Teacher teacher = teacher("T01", false);
        Course occupantCourse = course("Servizio stilistico", teacher, group);
        Course boxedCourse = course("Servizio tecnico e stilistico", teacher, group);
        publishTemplate(Map.of(), Map.of(occupantCourse.id, 200));

        List<Lesson> population = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            population.add(promoted(occupantCourse, ts));
        }
        Lesson boxed = lesson(boxedCourse, null, -1, false);
        population.add(boxed);

        // The premise, asserted rather than assumed: as a direct placement every cell is
        // refused by H11, which is what made the old search come back empty-handed.
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = BoxRepair.buildGroupDaySlots(population);
        Map<String, Integer> teacherDaily = BoxRepair.buildTeacherDaily(population);
        assertTrue(boxed.getAvailableTimeslots().stream()
                        .noneMatch(ts -> BoxRepair.passesPlacementGates(boxed, ts, groupDaySlots, teacherDaily)),
                "premise: the group is at maxHoursPerDay on every date, so no cell takes a direct placement");

        List<Lesson> demoted = StagedPinPolicy.selectDemotions(population, 1);

        assertEquals(1, demoted.size(), "the saturated day's own blocker is now seen and released");
        assertTrue(demoted.get(0).promotedPin, "only pins this cycle created itself are ever released");
    }

    // ─── fixture helpers ───────────────────────────────────────────────────

    private void publishTemplate(Map<Long, Set<Integer>> cells, Map<Long, Integer> weights) {
        SchoolSchedule.setWeeklyTemplateStatic(cells, weights);
    }

    private static int cell(DayOfWeek day, int slot) {
        return SchoolSchedule.encodeTemplateCell(day, slot);
    }

    private StudentGroup group(String name) {
        return TestDataBuilder.group(nextId++, name, 20);
    }

    private Teacher teacher(String name, boolean pseudo) {
        return TestDataBuilder.teacher(nextId++, name, pseudo);
    }

    private Course course(String subject, Teacher teacher, StudentGroup group) {
        return TestDataBuilder.course(nextId++, subject, teacher, group);
    }

    private Lesson lesson(Course course, LocalDate date, int slot, boolean pinned) {
        Timeslot ts = date != null ? timeslotByKey.get(date + "@" + slot) : null;
        return TestDataBuilder.lesson(nextId++, course, ts, null, pinned);
    }

    /**
     * A promoted pin, registered on BOTH sides the way {@code ScheduleService} does it: the
     * persistent {@code promotedPin} marker (what {@link StagedPinPolicy} reads) AND the
     * in-memory registry {@link com.school.planner.solver.BaseConstraintProvider#isAuthoritativePin}
     * reads. Populating only the marker (as this fixture did until the TASK-419 review) would
     * leave every H17 primitive treating these lessons as AUTHORITATIVE pins, so the
     * authoritative-vs-promoted distinction the gates depend on would never be exercised.
     *
     * <p>{@link TestDataBuilder} has no notion of a promoted pin (a TASK-419-only concept), so
     * this stays a thin wrapper around {@link TestDataBuilder#lesson} rather than a full copy.
     */
    private Lesson promoted(Course course, Timeslot ts) {
        Lesson l = TestDataBuilder.lesson(nextId++, course, ts, null, true);
        l.promotedPin = true;
        promotedIds.add(l.id);
        SchoolSchedule.setPromotedLessonIdsStatic(promotedIds);
        return l;
    }
}
