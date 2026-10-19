package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.TestDataBuilder;
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-478 AC#3: unit tests for {@link ExactFinisher} on a synthetic grid fixture. Pure
 * in-memory JUnit (no Quarkus/DB): every input flows through the {@link SchoolSchedule} statics,
 * ids by hand, the same fixture style {@link HoleRepairTest} uses.
 *
 * <p>Coverage:
 * <ul>
 * <li><b>Flag OFF = noop</b>: with {@code planner.exact-finisher.enabled=false} (the opt-out; the
 *     pass is default ON since the 2026-08-02 ratification), {@code repair} returns a zero Result
 *     and mutates nothing (the boxed lesson stays boxed).</li>
 * <li><b>Depth-0 completion</b>: with abundant free cells and the flag ON (the default), the pass
 *     places every boxed lesson at a 0-hard-additive cell (the workhorse case AC#1 measured).</li>
 * </ul>
 *
 * <p>The runner's improve-or-noop revert contract (the {@code shouldRevert} four-quadrant table)
 * is already pinned by {@code RepairGuardRevertTest} in the service package, so it is not
 * duplicated here.
 */
class ExactFinisherTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 14);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 15);
    private static final int SLOTS = 5;

    private static final String FLAG = "planner.exact-finisher.enabled";
    private static final String BUDGET = "planner.exact-finisher.max-candidates";

    private long nextId = 1;
    private StudentGroup group;
    private Teacher teacherA;
    private Teacher teacherB;
    private Course courseA;
    private Course courseB;
    private final List<Timeslot> timeslots = new ArrayList<>();
    private final Map<String, Timeslot> byKey = new HashMap<>();
    private String priorFlag;
    private String priorBudget;

    @BeforeEach
    void setUp() {
        group = TestDataBuilder.group(nextId++, "TEST", 20, 5);
        teacherA = TestDataBuilder.teacher(nextId++, "Prof A");
        teacherB = TestDataBuilder.teacher(nextId++, "Prof B");
        courseA = TestDataBuilder.course(nextId++, "Materia A", teacherA, group);
        courseB = TestDataBuilder.course(nextId++, "Materia B", teacherB, group);

        for (LocalDate date : List.of(MON, TUE)) {
            for (int slot = 0; slot < SLOTS; slot++) {
                Timeslot ts = TestDataBuilder.timeslot(nextId++, date, 8 + slot, 0, 9 + slot, 0);
                timeslots.add(ts);
                byKey.put(date + "@" + slot, ts);
            }
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setTeacherAvailabilitiesStatic(List.of());
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        SchoolSchedule.setWeeklyTemplateStatic(Map.of(), Map.of());
        SchoolSchedule.setPromotedLessonIdsStatic(Set.of());
        // Save the flag and budget-knob state so the test can restore it (the suite shares one JVM).
        priorFlag = System.getProperty(FLAG);
        System.clearProperty(FLAG);
        priorBudget = System.getProperty(BUDGET);
        System.clearProperty(BUDGET);
    }

    @AfterEach
    void tearDown() {
        if (priorFlag != null) {
            System.setProperty(FLAG, priorFlag);
        } else {
            System.clearProperty(FLAG);
        }
        if (priorBudget != null) {
            System.setProperty(BUDGET, priorBudget);
        } else {
            System.clearProperty(BUDGET);
        }
        SchoolSchedule.clearStaticState();
    }

    /**
     * Flag OFF (opt-out): with {@code planner.exact-finisher.enabled=false}, {@code repair} returns
     * a zero Result (movesApplied=0, conflictsAdded=0) and the boxed lesson's timeslot stays null.
     * The pass is default ON (ratified 2026-08-02); this is the explicit opt-out path.
     */
    @Test
    void flagOffIsNoop() {
        System.setProperty(FLAG, "false");
        Lesson boxed = boxed(courseB);
        SchoolSchedule schedule = scheduleWith(boxed);

        ExactFinisher.Result r = ExactFinisher.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);

        assertEquals(0, r.movesApplied(), "flag off: no moves");
        assertEquals(0, r.conflictsAdded(), "flag off: zero Result");
        assertNull(boxed.timeslot, "the boxed lesson stays boxed");
    }

    /**
     * Depth-0 completion: with the flag ON and abundant free cells, the pass places every boxed
     * lesson at a 0-hard-additive cell. AC#1 showed this is the workhorse case (4x-75x margin),
     * so no bump chain is needed.
     *
     * <p>Fixture: MON has lessons at slots 0 and 1 (contiguous). The boxed lesson (different
     * teacher, same group) can land at slot 2: H3-clean (no same-group lesson there), H17-safe
     * (keeps the day contiguous [0,1,2], no gap opened), H11-ok (3 of 5). Slots 3 and 4 would open
     * a gap and are H17-refused, so slot 2 is the one depth-0 cell.
     */
    @Test
    void depth0PlacesBoxedLessonAtFreeCell() {
        Lesson mon0 = placed(courseA, MON, 0, false);
        Lesson mon1 = placed(courseA, MON, 1, false);
        Lesson boxed = boxed(courseB);

        ExactFinisher.Result r = repair(mon0, mon1, boxed);

        assertEquals(1, r.movesApplied(), "the boxed lesson was placed");
        assertEquals(0, r.residualAfter(), "no residual");
        assertEquals(0, r.conflictsAdded(), "0-hard-additive by construction");
        assertSame(byKey.get(MON + "@2"), boxed.timeslot, "depth-0: landed at the contiguous free slot");
    }

    /**
     * Depth-0 completion with TWO boxed lessons: both are placed at the free contiguous cells.
     * MON [0,1] + boxed1 at slot 2 + boxed2 at slot 3 would open a gap at slot 2 if boxed1 is NOT
     * placed first. With both boxed, MCV ordering and the live map update let the pass fill the
     * day contiguously.
     *
     * <p>Actually MON slots 0,1 placed; boxed1 (courseB) and boxed2 (a third course) both need
     * placement. Slot 2 is H17-safe for the first one; after the first lands at slot 2, slot 3
     * becomes H17-safe for the second (day [0,1,2,3], contiguous). This tests the sequential
     * commit + live map update.
     */
    @Test
    void depth0PlacesMultipleBoxedLessonsContiguously() {
        Teacher teacherC = TestDataBuilder.teacher(nextId++, "Prof C");
        Course courseC = TestDataBuilder.course(nextId++, "Materia C", teacherC, group);
        Lesson mon0 = placed(courseA, MON, 0, false);
        Lesson mon1 = placed(courseA, MON, 1, false);
        Lesson boxed1 = boxed(courseB);
        Lesson boxed2 = boxed(courseC);

        ExactFinisher.Result r = repair(mon0, mon1, boxed1, boxed2);

        assertEquals(2, r.movesApplied(), "both boxed lessons placed");
        assertEquals(0, r.residualAfter());
        assertEquals(0, r.conflictsAdded(), "0-hard-additive");
        // The first placed fills slot 2 (contiguous with [0,1]); the second fills slot 3
        // (contiguous with [0,1,2]). Both H17-safe by construction.
        assertSame(byKey.get(MON + "@0"), mon0.timeslot, "placed lessons stay put");
        assertSame(byKey.get(MON + "@1"), mon1.timeslot);
        assertTrue(boxed1.timeslot != null && boxed2.timeslot != null, "both boxed now placed");
        assertNotEquals(boxed1.timeslot, boxed2.timeslot, "distinct cells");
    }

    /**
     * More than {@link ExactFinisher#MAX_K} boxed lessons: the pass returns a zero Result without
     * attempting any placement (a large residual is structural, not a completion problem).
     */
    @Test
    void exceedsMaxKReturnsZeroResult() {
        List<Lesson> manyBoxed = new ArrayList<>();
        // MAX_K + 1 distinct boxed lessons, each on its own course to avoid H1/H3 conflicts.
        for (int i = 0; i <= ExactFinisher.MAX_K; i++) {
            Teacher t = TestDataBuilder.teacher(nextId++, "T" + i);
            Course c = TestDataBuilder.course(nextId++, "S" + i, t, group);
            manyBoxed.add(boxed(c));
        }
        SchoolSchedule schedule = scheduleWith(manyBoxed.toArray(new Lesson[0]));

        ExactFinisher.Result r = ExactFinisher.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);

        assertEquals(0, r.movesApplied(), "over MAX_K: structural, not attempted");
        assertEquals(ExactFinisher.MAX_K + 1, r.residualAfter(), "all stay boxed");
        assertEquals(0, r.conflictsAdded());
    }

    /**
     * Depth-1 regression (the confirmed bug): a boxed lesson L whose only VRP cell is occupied by
     * a cross-group same-teacher blocker O, where O's only same-day relocation cell would widen an
     * H17 gap on O's OWN group day. The fix (using {@code BoxRepair.isLegalRelocation} instead of
     * the fresh-placement gate) must REJECT the chain: L stays boxed.
     *
     * <p><b>Why the old code shipped this.</b> {@code passesPlacementGates} read O's live group-day
     * map (which still had O at its origin) and simulated an ADD at the destination. For a same-day
     * relocation the simulation saw O at BOTH slots, so a contiguous {0,1} day accepted a move to
     * slot 2 (simulated {0,1,2} = contiguous, penalty 0). Reality after commit: {0,2} break width 1,
     * penalty 1. The H0 credit from placing L (hard +2) outweighed the H17 debit (hard -1), so net
     * hard improved and the runner's {@code shouldRevert} never fired. {@code isLegalRelocation}
     * reads the origin-VACATED map ({0} with slot 1 removed), so the arrival simulation correctly
     * sees {0,2} = penalty 1 > 0 and rejects.
     *
     * <p>Fixture: MON slots 0,2,3,4 blocked by pinned TEST lessons (H3 for L). MON slot 0 also has
     * a pinned OTHER lesson (creates O's day shape {0,1}). MON slot 1 has O (teacher T = L's
     * teacher, group OTHER). TUE slots 0-4 all have pinned teacher-T lessons (block O from
     * cross-day relocation via H1). L (teacher T, group TEST) is boxed. The ONLY cell for L is MON
     * slot 1 (evict O); O's ONLY non-H17-widening relocation would be cross-day to TUE, but every
     * TUE slot has an H1 conflict. So the chain is correctly rejected.
     */
    @Test
    void depth1RejectsSameDayRelocationThatWidensH17() {
        StudentGroup testGroup = TestDataBuilder.group(nextId++, "TEST", 20, 5);
        StudentGroup otherGroup = TestDataBuilder.group(nextId++, "OTHER", 20, 5);
        Teacher shared = TestDataBuilder.teacher(nextId++, "Shared");
        Teacher fillA = TestDataBuilder.teacher(nextId++, "FillA");
        Teacher fillB = TestDataBuilder.teacher(nextId++, "FillB");
        Course courseL = TestDataBuilder.course(nextId++, "SubL", shared, testGroup);
        Course courseO = TestDataBuilder.course(nextId++, "SubO", shared, otherGroup);
        Course courseFillTest = TestDataBuilder.course(nextId++, "FillTest", fillA, testGroup);
        Course courseFillOther = TestDataBuilder.course(nextId++, "FillOther", fillB, otherGroup);
        Course courseFillTue = TestDataBuilder.course(nextId++, "FillTue", shared, testGroup);

        List<Lesson> all = new ArrayList<>();
        // MON: TEST fill at 0,2,3,4 (blocks L via H3). OTHER fill at 0 (creates O's day {0,1}).
        for (int slot : new int[] { 0, 2, 3, 4 }) all.add(placed(courseFillTest, MON, slot, true));
        all.add(placed(courseFillOther, MON, 0, true));
        // O at MON slot 1 (unpinned, teacher T, group OTHER).
        Lesson o = placed(courseO, MON, 1, false);
        all.add(o);
        // TUE: teacher-T fill at all slots (blocks O from cross-day relocation via H1).
        for (int slot = 0; slot < SLOTS; slot++) all.add(placed(courseFillTue, TUE, slot, true));
        // L boxed (teacher T, group TEST).
        Lesson l = boxed(courseL);
        all.add(l);

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(testGroup, otherGroup),
                List.of(shared, fillA, fillB),
                List.of(courseL, courseO, courseFillTest, courseFillOther, courseFillTue),
                all, List.of());

        ExactFinisher.Result r = ExactFinisher.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);

        assertEquals(0, r.movesApplied(), "depth-1 chain rejected: O's relocation would widen H17");
        assertEquals(1, r.residualAfter(), "L stays boxed");
        assertEquals(0, r.conflictsAdded(), "no moves committed");
        assertNull(l.timeslot, "L was not placed");
    }

    /**
     * Depth-1 SAME-group acceptance (confirmed false negative, code-review 2026-08-02): a boxed L
     * whose only viable cell is occupied by a SAME-group blocker O sitting mid-run, where O's
     * simulated removal would open an H17 gap. {@code BoxRepair.isDepartureSafe} reads the pre-commit
     * map and cannot see that L refills O's exact slot ({@code chain.headCell() == bump.origin() ==
     * ts}), so calling it unconditionally strands L. The fix gates the departure check on {@code
     * !sameGroup} (mirroring {@code BoxRepair.findEviction}): a same-group departure is gap-neutral,
     * the chain is accepted, L lands at O's slot and O relocates same-day.
     *
     * <p>Fixture: TEST@MON = {0,1,2} (slots 0,2 UNPINNED TEST fill - so O's mid-run removal opens a
     * real, non-exempt gap, not a both-authoritative-pins break; slot 1 = O, unpinned TEST/tO).
     * MON slots 3,4 hold tL/OTHER lessons (H1-block L there, but free for O's relocation). TUE is
     * filled with pinned TEST (H3-blocks L on every TUE cell). So L's only path is the depth-1 bump
     * of O at MON slot 1; O relocates same-day to MON slot 3 (same-group relocation reads the full
     * origin day, H17-safe). TEST@MON ends contiguous {0,1,2,3}, 0-hard-additive.
     */
    @Test
    void depth1AcceptsSameGroupBlockerWhoseSlotLRefills() {
        StudentGroup testGroup = TestDataBuilder.group(nextId++, "TEST", 20, 5);
        StudentGroup otherGroup = TestDataBuilder.group(nextId++, "OTHER", 20, 5);
        Teacher tL = TestDataBuilder.teacher(nextId++, "TL");
        Teacher tO = TestDataBuilder.teacher(nextId++, "TO");
        Course courseL = TestDataBuilder.course(nextId++, "SubL", tL, testGroup);
        Course courseO = TestDataBuilder.course(nextId++, "SubO", tO, testGroup);
        Course courseFillTest = TestDataBuilder.course(nextId++, "FillTest", tO, testGroup);
        Course courseH1ForL = TestDataBuilder.course(nextId++, "H1L", tL, otherGroup);
        Course courseFillTue = TestDataBuilder.course(nextId++, "FillTue", tO, testGroup);

        List<Lesson> all = new ArrayList<>();
        // TEST@MON = {0,1,2}: UNPINNED TEST fill at 0,2 bounds O's run (and being unpinned, O's
        // mid-run removal opens a REAL penalized gap, not a both-authoritative-pins-exempt break).
        // Each of slots 0,2 ALSO hosts an unpinned tL/OTHER lesson: L conflicts with BOTH occupants
        // (group via the TEST fill, teacher tL via the OTHER lesson), so singleUnpinnedBlocker
        // returns null (two unpinned blockers) and L cannot bump slots 0,2 - forcing L to the
        // slot-1 bump of O (the isolated same-group case the fix targets).
        all.add(placed(courseFillTest, MON, 0, false));
        all.add(placed(courseH1ForL, MON, 0, false));
        all.add(placed(courseFillTest, MON, 2, false));
        all.add(placed(courseH1ForL, MON, 2, false));
        Lesson o = placed(courseO, MON, 1, false);
        all.add(o);
        // MON slots 3,4: pinned tL/OTHER (H1-blocks L at depth-0; free for O's same-day relocation).
        all.add(placed(courseH1ForL, MON, 3, true));
        all.add(placed(courseH1ForL, MON, 4, true));
        // TUE all slots: pinned TEST (H3-blocks L everywhere on TUE).
        for (int slot = 0; slot < SLOTS; slot++) all.add(placed(courseFillTue, TUE, slot, true));
        // L boxed (TEST/tL): only MON slot 1 is viable, via the same-group bump of O.
        Lesson l = boxed(courseL);
        all.add(l);

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(testGroup, otherGroup),
                List.of(tL, tO),
                List.of(courseL, courseO, courseFillTest, courseH1ForL, courseFillTue),
                all, List.of());

        ExactFinisher.Result r = ExactFinisher.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);

        assertEquals(1, r.movesApplied(), "same-group bump accepted: L placed");
        assertEquals(0, r.residualAfter(), "no residual");
        assertEquals(0, r.conflictsAdded(), "0-hard-additive: L refills O's slot, no gap opens");
        assertSame(byKey.get(MON + "@1"), l.timeslot, "L took O's origin slot");
        assertSame(byKey.get(MON + "@3"), o.timeslot, "O relocated same-day to slot 3");
    }

    /**
     * The candidate-evaluation budget is the LIVE -Dplanner.exact-finisher.max-candidates knob
     * (raised 2026-08-02 from a hardcoded 10000 to a default of 100000): with the budget set BELOW
     * the number of cells the scan must evaluate to reach the free cell, a PLACEABLE residual is left
     * boxed (the early-abort fires first); with the default budget the SAME fixture places it. This
     * is the non-vacuous guard that the knob is read LIVE and controls the search bound.
     *
     * <p>Fixture (the {@code depth0PlacesBoxedLessonAtFreeCell} shape): MON slots 0,1 hold courseA
     * (same group as the boxed courseB lesson, so H3 blocks it there); MON slot 2 is the free,
     * H17-safe cell. The boxed lesson's chronological VRP scan evaluates MON0 (blocked), MON1
     * (blocked), then MON2 (free), so a budget of 2 aborts AT MON2 before its gates are evaluated,
     * while the default budget reaches and commits it.
     */
    @Test
    void candidateBudgetKnobControlsTheScanBound() {
        Lesson mon0 = placed(courseA, MON, 0, false);
        Lesson mon1 = placed(courseA, MON, 1, false);
        Lesson boxed = boxed(courseB);

        // Budget 2: the scan aborts at MON2 (the 3rd evaluated cell) before the free cell is reached.
        System.setProperty(BUDGET, "2");
        ExactFinisher.Result capped = repair(mon0, mon1, boxed);
        assertEquals(0, capped.movesApplied(), "budget 2 aborts the scan before the free cell at MON2");
        assertEquals(1, capped.residualAfter(), "boxed left boxed by the cap, not by infeasibility");
        assertNull(boxed.timeslot, "not placed");

        // Default budget: the same fixture reaches MON2 and places it (the placeable case).
        System.clearProperty(BUDGET);
        ExactFinisher.Result placedResult = repair(mon0, mon1, boxed);
        assertEquals(1, placedResult.movesApplied(), "default budget reaches the free cell and places it");
        assertEquals(0, placedResult.residualAfter());
        assertSame(byKey.get(MON + "@2"), boxed.timeslot, "placed at the free contiguous slot");
    }

    // ─── fixture helpers ───────────────────────────────────────────────────

    private ExactFinisher.Result repair(Lesson... lessons) {
        SchoolSchedule schedule = scheduleWith(lessons);
        ExactFinisher.Result r = ExactFinisher.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts, lesson -> lesson.room = null);
        assertTrue(r.conflictsAdded() <= 0, "the pass is 0-hard-additive by construction");
        return r;
    }

    private SchoolSchedule scheduleWith(Lesson... lessons) {
        return new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(group), List.of(teacherA, teacherB),
                List.of(courseA, courseB), List.of(lessons), List.of());
    }

    private Lesson placed(Course course, LocalDate date, int slot, boolean pinned) {
        return TestDataBuilder.lesson(nextId++, course, byKey.get(date + "@" + slot), null, pinned);
    }

    private Lesson boxed(Course course) {
        return TestDataBuilder.lesson(nextId++, course, null, null, false);
    }
}
