package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * TASK-556 sprint QFR v2 TDD: the synthetic banco of the 21 empty expected cells. One group,
 * one empty declared day (quota 2) fed by one above-quota donor day (4 lessons on a quota-2
 * day). The v1 move choice takes the donor's FIRST lesson and may land the target's first
 * hour anywhere among the day's free slots: the measured live revert ("quota fill repair
 * REVERTED, H31 0 -> -3") is exactly this. v2 pins the SHAPE:
 * <ul>
 * <li>target fills CONTIGUOUSLY FROM the day's first open slot (H31/H17-safe by construction);</li>
 * <li>the donor yields from its TAIL (slot 0 stays: the donor's start never moves);</li>
 * <li>retry loop: the pass re-sweeps while it progresses.</li>
 * </ul>
 */
class QuotaFillRepairV2Test {

    private static final LocalDate TARGET_DAY = LocalDate.of(2027, 5, 10);
    private static final LocalDate DONOR_DAY = LocalDate.of(2027, 5, 11);

    private SchoolSchedule schedule;
    private List<Lesson> lessons;

    @BeforeEach
    void setUp() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quota(TARGET_DAY, 2), quota(DONOR_DAY, 2)));
        schedule = build();
        lessons = schedule.getLessons();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(QuotaFillRepair.V2_FLAG);
        System.clearProperty(QuotaFillRepair.DONOR_ORDER_KNOB);
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setPromotedLessonIdsStatic(List.of());
        SchoolSchedule.setDayFloorsStatic(java.util.Map.of());
    }

    @Test
    void v2FillsTheEmptyDayContiguouslyAndKeepsTheDonorStart() {
        System.setProperty(QuotaFillRepair.V2_FLAG, "true");
        QuotaFillRepair.Result r = QuotaFillRepair.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts,
                lesson -> lesson.room = null);
        System.out.println("QFR-v2: " + r.movesApplied() + " moves, " + r.cellsClosed()
                + " cells closed, conflicts " + r.conflictsAdded());
        assertEquals(0, r.conflictsAdded(), "no H1/H3 conflict may be added");
        assertTrue(r.cellsClosed() >= 1, "the empty declared day must be closed");
        assertTrue(r.movesApplied() >= 2, "2 hours must move to close a quota-2 empty day");
        // shape: the target day now runs contiguously from slot 0
        List<Integer> targetSlots = slotsAt(TARGET_DAY);
        assertEquals(List.of(0, 1), targetSlots, "target must fill slots 0,1 (start at 8:00)");
        // shape: the donor kept its morning (slot 0) and lost from the tail
        List<Integer> donorSlots = slotsAt(DONOR_DAY);
        assertEquals(0, donorSlots.get(0), "donor start (slot 0) must not move (H31)");
        assertEquals(2, donorSlots.size(), "donor back at quota 2");
        assertEquals(StudentDayCompaction.countConflicts(schedule), 0, "grid stays conflict-free");
    }

    @Test
    void v2NeverFillsAnEmptyDayFromALateSlot() {
        // the H31 residual of iter-2: when the target's FIRST slot is H1-blocked (another
        // group holds the only movable teacher there), the fill must NOT slide to slot 1:
        // a formerly-empty day starting late is a HARD H31 cell the empty day never was.
        // Pin: donor lessons' teachers are all busy at the target day's slot 0.
        Teacher shared = new Teacher("Shared");
        for (Lesson l : lessons) {
            l.course = new Course(l.course.subject, shared, group(), 10);
        }
        // an other-group lesson with the same teacher at TARGET slot 0
        StudentGroup other = new StudentGroup("OTHER", 20);
        other.id = 2L;
        Lesson blocker = new Lesson();
        blocker.id = 99L;
        blocker.course = new Course("X", shared, other, 10);
        blocker.timeslot = schedule.getTimeslots().get(0); // TARGET slot 0
        lessons.add(blocker);
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quota(TARGET_DAY, 1), quota(DONOR_DAY, 2)));

        System.setProperty(QuotaFillRepair.V2_FLAG, "true");
        QuotaFillRepair.Result r = QuotaFillRepair.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts,
                lesson -> lesson.room = null);
        System.out.println("QFR-v2-late: " + r.movesApplied() + " moves, "
                + r.cellsClosed() + " closed, target=" + slotsAt(TARGET_DAY));
        assertEquals(0, r.conflictsAdded());
        List<Integer> targetSlots = slotsAt(TARGET_DAY);
        assertTrue(targetSlots.isEmpty() || targetSlots.get(0) == 0,
                "an empty day may only be filled starting at its FIRST slot, got " + targetSlots);
    }

    @Test
    void v2DonorNeverDropsBelowTheDayFloor() {
        // the sanity reds of the confirmation cycle: donors were drained to their H32 QUOTA
        // (2 on short declared days) while the group's PRACTICED floor (H33/DayFloorDeriver)
        // is 4: the moved-from days ended under the golden-practiced minimum. The gate: a
        // donor may only yield hours it holds ABOVE max(quota, floor).
        SchoolSchedule.setDayFloorsStatic(Map.of(1L, 4));
        // donor: 5 lessons on a quota-1 day (surplus 4 vs quota, but only 1 above the floor)
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quota(TARGET_DAY, 5), quota(DONOR_DAY, 1)));
        for (int slot = 4; slot < 8; slot++) { // slots 4..7 of the donor date are free
            Lesson l = new Lesson();
            l.id = 100L + slot;
            Teacher t = new Teacher("TF" + slot);
            l.course = new Course("SF" + slot, t, group(), 10);
            l.timeslot = schedule.getTimeslots().get(8 + slot);
            lessons.add(l);
        }
        System.setProperty(QuotaFillRepair.V2_FLAG, "true");
        QuotaFillRepair.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts,
                lesson -> lesson.room = null);
        List<Integer> donorSlots = slotsAt(DONOR_DAY);
        System.out.println("QFR-v2-floor: donor=" + donorSlots + " target=" + slotsAt(TARGET_DAY));
        assertTrue(donorSlots.size() >= 4,
                "donor must keep at least its practiced FLOOR of 4 hours, got " + donorSlots);
    }

    @Test
    void flagOffKeepsV1Behavior() {
        // RATIFIED default ON (27/08): absent -> v2, so the legacy-path pin must opt out
        // with the literal "false". No assertion on the SHAPE (that is v1's own freedom):
        // the pin is that the opt-out keeps the legacy path alive and conflict-free.
        System.setProperty(QuotaFillRepair.V2_FLAG, "false");
        QuotaFillRepair.Result r = QuotaFillRepair.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts,
                lesson -> lesson.room = null);
        assertEquals(0, r.conflictsAdded(), "legacy path must stay conflict-free");
    }

    /** TASK-556 item-4 donor-order sweep: TARGET empty (quota 2), NEAR donor one day
     * later (quota 1, 3 hours = surplus 2), FAR donor eleven days later (quota 1,
     * 4 hours = surplus 3). Default order visits the BIGGEST surplus first (FAR);
     * the knob "nearest" visits the day NEXT DOOR first. */
    private static final LocalDate SWEEP_FAR_DAY = LocalDate.of(2027, 5, 21);

    /** 8 slots per date, consecutive ids: date index i owns timeslot indexes 8i..8i+7. */
    private static List<Timeslot> buildTimeslots(List<LocalDate> dates) {
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalDate d : dates) {
            for (java.time.LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
                Timeslot ts = new Timeslot();
                ts.id = (long) (timeslots.size() + 1);
                ts.startDateTime = LocalDateTime.of(d, start);
                timeslots.add(ts);
            }
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        return timeslots;
    }

    private SchoolSchedule buildThreeDays() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quota(TARGET_DAY, 2), quota(DONOR_DAY, 1), quota(SWEEP_FAR_DAY, 1)));
        List<Timeslot> timeslots = buildTimeslots(List.of(TARGET_DAY, DONOR_DAY, SWEEP_FAR_DAY));
        StudentGroup g = group();
        List<Lesson> out = new ArrayList<>();
        long id = 1;
        for (int slot = 0; slot < 3; slot++) { // NEAR (date index 1): 3 hours on quota 1
            Lesson l = new Lesson();
            l.id = id++;
            l.course = new Course("N" + slot, new Teacher("TN" + slot), g, 10);
            l.timeslot = timeslots.get(8 + slot);
            out.add(l);
        }
        for (int slot = 0; slot < 4; slot++) { // FAR (date index 2): 4 hours on quota 1 (bigger surplus)
            Lesson l = new Lesson();
            l.id = id++;
            l.course = new Course("F" + slot, new Teacher("TF" + slot), g, 10);
            l.timeslot = timeslots.get(16 + slot);
            out.add(l);
        }
        return new SchoolSchedule(timeslots, List.<Room>of(), List.of(), List.of(), List.of(),
                List.of(g), List.of(), List.of(), out, List.of());
    }

    @Test
    void v2DonorOrderNearestDrainsTheClosestDayFirst() {
        System.setProperty(QuotaFillRepair.V2_FLAG, "true");
        System.setProperty(QuotaFillRepair.DONOR_ORDER_KNOB, "nearest");
        schedule = buildThreeDays();
        lessons = schedule.getLessons();
        QuotaFillRepair.Result r = QuotaFillRepair.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts,
                lesson -> lesson.room = null);
        assertEquals(0, r.conflictsAdded());
        assertTrue(r.cellsClosed() >= 1, "the empty target must close");
        assertEquals(List.of(0, 1), slotsAt(TARGET_DAY), "target fills slots 0,1");
        assertEquals(1, slotsAt(DONOR_DAY).size(), "NEAR donor drained to its quota");
        assertEquals(4, slotsAt(SWEEP_FAR_DAY).size(), "FAR donor untouched (nearest wins)");
    }

    @Test
    void v2DonorOrderDefaultStillPrefersTheBiggestSurplus() {
        System.setProperty(QuotaFillRepair.V2_FLAG, "true");
        schedule = buildThreeDays();
        lessons = schedule.getLessons();
        QuotaFillRepair.Result r = QuotaFillRepair.repair(schedule,
                (lesson, ts) -> lesson.timeslot = ts,
                lesson -> lesson.room = null);
        assertEquals(0, r.conflictsAdded());
        assertTrue(r.cellsClosed() >= 1, "the empty target must close");
        assertEquals(2, slotsAt(SWEEP_FAR_DAY).size(), "FAR donor (surplus 3) drained first");
        assertEquals(3, slotsAt(DONOR_DAY).size(), "NEAR donor (surplus 2) not reached");
    }

    private List<Integer> slotsAt(LocalDate date) {
        List<Integer> out = new ArrayList<>();
        for (Lesson l : lessons) {
            if (l.timeslot != null && l.timeslot.getDate().equals(date)
                    && l.getStudentGroup() != null && l.getStudentGroup().id != null
                    && l.getStudentGroup().id.equals(group().id)) {
                out.add(TimeslotSequence.slotIndexOf(l.timeslot));
            }
        }
        out.sort(null);
        return out;
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

    private static StudentGroup groupRef;

    private static StudentGroup group() {
        if (groupRef == null) {
            groupRef = new StudentGroup("G", 20);
            groupRef.id = 1L;
        }
        return groupRef;
    }

    private SchoolSchedule build() {
        List<Timeslot> timeslots = buildTimeslots(List.of(TARGET_DAY, DONOR_DAY));
        StudentGroup g = group();
        List<Lesson> out = new ArrayList<>();
        long id = 1;
        for (int slot = 0; slot < 4; slot++) {
            Teacher t = new Teacher("T" + slot);
            Course c = new Course("S" + slot, t, g, 10);
            Lesson l = new Lesson();
            l.id = id++;
            l.course = c;
            l.timeslot = timeslots.get(8 + slot); // DONOR_DAY is the second date: index 8+slot
            out.add(l);
        }
        return new SchoolSchedule(timeslots, List.<Room>of(), List.of(), List.of(), List.of(),
                List.of(g), List.of(), List.of(), out, List.of());
    }
}
