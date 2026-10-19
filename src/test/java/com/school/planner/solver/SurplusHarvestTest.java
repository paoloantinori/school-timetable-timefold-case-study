package com.school.planner.solver;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.school.planner.domain.GroupCalendarRule;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TASK-577: unit tests of the SurplusHarvest mechanics (pure pass, no solver). */
class SurplusHarvestTest {

    @AfterEach
    void clean() {
        System.clearProperty(SurplusHarvest.FLAG);
        SnapshotTestSupport.resetQuotaStatics();
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        // JVM-global static mutated by the net-quota test: without this reset it
        // nets the donor quota of EVERY later test in the class (order-dependent landmine)
        SchoolSchedule.setAuthoritativePinnedHoursForTest(null);
    }

    /**
     * Stale-VRP-cache regression (cycle 2026-09-02, two IllegalStateException
     * assertValueRange crashes): the pass's isInValueRange guard reads
     * Lesson.getAvailableTimeslots(), which is a NEVER-INVALIDATED volatile cache.
     * When the statics change after the cache was populated (here: a group calendar
     * window published after the first read), a guard trusting the cache would slide
     * followers into cells the FRESH range forbids; the next solver start (which
     * recomputes on a clone, cache-free) explodes. The guard must read the FRESH
     * range, not the cached one.
     */
    @Test
    void on_staleVrpCache_theGuardReadsFreshWindows() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        SchoolSchedule schedule = buildSchedule();
        StudentGroup group = schedule.getStudentGroups().get(0);

        // 0. Publish the timeslot static the FRESH range computes from (the plain
        //    fixture does not: an empty static yields an empty range, which the
        //    guard's empty-means-unrestricted branch would mask).
        SchoolSchedule.setAllTimeslotsStatic(schedule.getTimeslots());

        // 1. Populate every lesson's VRP cache under the PERMISSIVE statics (no window).
        for (Lesson l : schedule.getLessons()) {
            l.getAvailableTimeslots();
        }

        // 2. Now publish a window that closes the donor weekday after slot 1. The
        //    cache is never invalidated (volatile field, no resetter anywhere), so a
        //    cache-trusting guard still sees the whole day as legal.
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(new GroupCalendarRule(
                group, SnapshotTestSupport.DONOR_DATE.getDayOfWeek(), 0, 1)));

        // 3. Positions before the pass.
        Map<Long, Integer> slotBefore = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot != null) {
                slotBefore.put(l.id, TimeslotSequence.slotIndexOf(l.timeslot));
            }
        }

        SurplusHarvest.Result r = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        // Hardening: the fresh guard must not over-block into a full no-op; the two
        // follower-less donors (end-of-day lessons) can still move to the target.
        assertTrue(r.movesApplied() > 0,
                "the fresh guard must still allow the follower-less donors to move");

        // 4. Every lesson that MOVED on the donor date must sit inside the FRESH
        //    window (slot <= 1): a slide into slot >= 2 means the guard trusted the
        //    stale cache (the fresh range forbids it and the crash follows at the
        //    next solver start).
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || !l.timeslot.getDate().equals(SnapshotTestSupport.DONOR_DATE)) continue;
            Integer before = slotBefore.get(l.id);
            int now = TimeslotSequence.slotIndexOf(l.timeslot);
            boolean moved = before != null && before != now;
            if (moved) {
                assertTrue(now <= 1,
                        "lesson " + l.id + " slid to slot " + now + " outside the fresh window [0..1]"
                                + " (stale-cache guard)");
            }
        }
    }

    @Test
    void flag_defaultOff() {
        System.clearProperty(SurplusHarvest.FLAG);
        assertFalse(SurplusHarvest.enabled());
        System.setProperty(SurplusHarvest.FLAG, "true");
        assertTrue(SurplusHarvest.enabled());
        System.setProperty(SurplusHarvest.FLAG, "false");
        assertFalse(SurplusHarvest.enabled());
    }

    @Test
    void off_noMoves() {
        System.clearProperty(SurplusHarvest.FLAG);
        SchoolSchedule schedule = buildSchedule();
        SurplusHarvest.Result r = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);
        assertEquals(0, r.movesApplied());
        assertEquals(0, r.cellsClosed());
    }

    @Test
    void on_closesTheEmptyDayFromADistantOverQuotaDonor() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        SchoolSchedule schedule = buildSchedule();
        SurplusHarvest.Result r = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);
        // The donor (over-quota on 2026-09-16) must yield at least one hour to the
        // empty declared day (2026-06-09: months away = long range).
        assertTrue(r.movesApplied() > 0, "expected at least one move, got " + r.movesApplied());
        assertTrue(r.cellsClosed() > 0, "expected the empty day to receive hours");
        // The target day must now have at least one non-Stage lesson.
        long placed = schedule.getLessons().stream()
                .filter(l -> l.timeslot != null && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE))
                .count();
        assertTrue(placed > 0, "the empty day must have at least one placed lesson");
    }

    @Test
    void on_donorLosesOneHour_staysAtOrAboveQuota() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        SchoolSchedule schedule = buildSchedule();
        SurplusHarvest.repair(schedule, (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);
        // Donor day hours after the harvest (started at 8, quota 6).
        long donorHours = schedule.getLessons().stream()
                .filter(l -> l.timeslot != null && l.timeslot.getDate().equals(SnapshotTestSupport.DONOR_DATE))
                .count();
        assertTrue(donorHours >= 6, "donor must stay at or above quota (6), got " + donorHours);
    }

    @Test
    void on_donorBlockedAtFirstSlot_theCellIsRejected_v9H31() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        // Build a schedule where the target day's FIRST slot has the donor's teacher
        // busy (a lesson of another group), but the SECOND slot is free.
        Teacher teacher = new Teacher("DonorTeacher");
        teacher.id = 1L;
        StudentGroup group = new StudentGroup("TargetGroup", 6);
        group.id = 1L;
        group.startDate = LocalDate.of(2026, 1, 1);
        StudentGroup otherGroup = new StudentGroup("OtherGroup", 6);
        otherGroup.id = 2L;
        otherGroup.startDate = LocalDate.of(2026, 1, 1);

        Course donorCourse = new Course("DonorSubject", teacher, group, 100);
        donorCourse.id = 1L;
        Course otherCourse = new Course("OtherSubject", teacher, otherGroup, 100);
        otherCourse.id = 2L;

        java.util.List<Timeslot> timeslots = grid(SnapshotTestSupport.DONOR_DATE, SnapshotTestSupport.TARGET_DATE);

        // Quotas: donor 6 (over-quota with 7), target 5 (empty).
        SchoolSchedule.setDailyActivityQuotasStatic(java.util.Arrays.asList(
                quotaFixture(group, SnapshotTestSupport.DONOR_DATE, 6),
                quotaFixture(group, SnapshotTestSupport.TARGET_DATE, 2)));

        java.util.List<Lesson> lessons = new java.util.ArrayList<>();
        int lessonId = 1;
        // 7 donor lessons on the donor date, one per distinct slot (no pre-existing
        // H1/H3 conflicts: the per-move oracle must see hard not worsen, and with
        // pre-existing conflicts the count is non-zero and the semantics get muddy).
        for (int i = 0; i < 7; i++) {
            Lesson l = new Lesson();
            l.id = (long) lessonId++;
            l.course = donorCourse;
            l.pinned = false;
            l.timeslot = timeslots.get(i); // one per slot: distinct, no overlap
            lessons.add(l);
        }
        // One lesson of ANOTHER group with the SAME teacher at the TARGET's first slot.
        // This makes the teacher busy at slot 0 of the target day, but free at slot 1.
        Lesson blocker = new Lesson();
        blocker.id = (long) lessonId++;
        blocker.course = otherCourse;
        blocker.pinned = false;
        blocker.timeslot = timeslots.get(8); // TARGET_DATE slot 0 (DONOR occupies indices 0-7)
        lessons.add(blocker);

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(group, otherGroup), List.of(teacher),
                List.of(donorCourse, otherCourse), lessons, List.of());

        var result = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);
        // v9 H31: the FIRST slot is the only legal landing; its teacher being busy
        // REJECTS the cell (an hour shifted to slot 1 opens a day-start violation on
        // the freshly-filled day - the 22:40 cycle reverted with H31 0->-26).
        assertEquals(0, result.movesApplied(),
                "v9: a donor blocked at the target's first slot must NOT shift to the second");
    }

    // ── fixture: one group, an over-quota donor day (2026-09-16, 8h vs quota 6),
    //    an empty declared day (2026-06-09, quota 5, zero placed). ──

    private static final class SnapshotTestSupport {
        static final LocalDate DONOR_DATE = LocalDate.of(2026, 9, 16);
        static final LocalDate TARGET_DATE = LocalDate.of(2026, 6, 9);
        static final LocalDate TARGET2_DATE = LocalDate.of(2026, 6, 10);

        static void resetQuotaStatics() {
            SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        }
    }

    /** The 8-slot/day grid of the given dates (ids 1..8*dates.length): the shared
     *  fixture of every test in this class (four verbatim copies consolidated). */
    private static List<Timeslot> grid(LocalDate... dates) {
        List<Timeslot> timeslots = new java.util.ArrayList<>();
        int id = 1;
        for (LocalDate d : dates) {
            for (int h = 8; h < 16; h++) {
                Timeslot ts = new Timeslot(
                        java.time.LocalDateTime.of(d, LocalTime.of(h, 0)),
                        java.time.LocalDateTime.of(d, LocalTime.of(h + 1, 0)));
                ts.id = (long) id++;
                timeslots.add(ts);
            }
        }
        return timeslots;
    }

    private long nextLessonId = 1;

    private Lesson lesson(List<Lesson> into, Course course, Timeslot ts) {
        Lesson l = new Lesson();
        l.id = nextLessonId++;
        l.course = course;
        l.timeslot = ts;
        into.add(l);
        return l;
    }

    private SchoolSchedule buildSchedule() {
        return buildSchedule("TestGroup");
    }

    private SchoolSchedule buildSchedule(String groupName) {
        Teacher teacher = new Teacher("TestTeacher");
        teacher.id = 1L;
        StudentGroup group = new StudentGroup(groupName, 6);
        group.id = 1L;
        group.startDate = LocalDate.of(2026, 1, 1); // before both dates (H14)
        Course course = new Course("TestSubject", teacher, group, 100);
        course.id = 1L;

        // Timeslots: 8 per day for the donor and target dates (Wednesday for both).
        java.util.List<Timeslot> timeslots = grid(SnapshotTestSupport.DONOR_DATE, SnapshotTestSupport.TARGET_DATE);

        // Quotas: donor 6, target 5.
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quotaFixture(group, SnapshotTestSupport.DONOR_DATE, 6),
                quotaFixture(group, SnapshotTestSupport.TARGET_DATE, 2)));

        // Donor lessons: 8h on the donor date (over-quota).
        java.util.List<Lesson> lessons = new java.util.ArrayList<>();
        int lessonId = 1;
        for (int i = 0; i < 8; i++) {
            Lesson l = new Lesson();
            l.id = (long) lessonId++;
            l.course = course;
            l.pinned = false;
            l.timeslot = timeslots.get(i);
            lessons.add(l);
        }
        // Target lessons: NONE (the day is declared and empty).

        return new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(group), List.of(teacher), List.of(course), lessons, List.of());
    }

    /**
     * v9 H31: the landing must be the target day's FIRST open slot. Build a target
     * day whose first slot is VRP-illegal for the donors (a calendar window that
     * opens at slot 1): the cell must NOT be filled by shifting the hour to the
     * second slot - that is exactly the day-start violation the 22:40 cycle's
     * revert showed (H31 0->-26).
     */
    @Test
    void on_firstLandingSlotIllegal_theCellIsRejectedNotShifted() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        SchoolSchedule schedule = buildSchedule();
        // window on the TARGET weekday: opens at slot 1 (slot 0 closed for the group)
        StudentGroup group = schedule.getStudentGroups().get(0);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(new GroupCalendarRule(
                group, SnapshotTestSupport.TARGET_DATE.getDayOfWeek(), 1, 7)));
        // fixture sanity: publish the static grid (an UNPUBLISHED grid yields EMPTY
        // fresh ranges and the guard's empty-means-unrestricted branch would mask the
        // window entirely), then prove the window bites: slot 0 out of the range
        SchoolSchedule.setAllTimeslotsStatic(schedule.getTimeslots());
        Lesson probe = schedule.getLessons().get(0);
        Timeslot tueSlot0 = schedule.getTimeslots().stream()
                .filter(ts -> ts.getDate().equals(SnapshotTestSupport.TARGET_DATE))
                .findFirst().orElseThrow();
        assertFalse(probe.getAvailableTimeslotsFresh().isEmpty(),
                "fixture: the fresh range must be populated (static grid published)");
        assertFalse(probe.getAvailableTimeslotsFresh().contains(tueSlot0),
                "fixture: window [1..7] on the target weekday must exclude slot 0 from the fresh range");
        SurplusHarvest.Result r = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);
        long onTarget = schedule.getLessons().stream()
                .filter(l -> l.timeslot != null && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE))
                .count();
        // v10/I4: the landing must go to the WINDOW's first open slot (slot 1 here),
        // never to grid slot 0 (H31 anchors the day start to the window, and slot 0
        // is VRP-illegal for these donors anyway)
        assertTrue(onTarget > 0, "the window-compliant first slot must be used");
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot != null && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE)) {
                assertTrue(TimeslotSequence.slotIndexOf(l.timeslot) >= 1,
                        "no lesson may sit at grid slot 0 of a window-[1..n] day");
            }
        }
    }

    /**
     * v9 H33: a donor at its hard day floor must not donate its last floor hour.
     * Floor for the fixture group derives > 0 (pool/giorni capped at 4); the donor
     * day is at net quota exactly (not over), so the only way to donate would be
     * below the floor: forbidden. The 22:40 revert showed H33 0->-10.
     */
    @Test
    void on_donorAtHardFloor_theHourIsNotTaken() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        // REAL group name: the floor derivation includes the golden existence cap,
        // which only exists for real school groups ("G01" floors at 4)
        SchoolSchedule schedule = buildSchedule("G01");
        // donor quota 6 with 6 lessons placed: NOT over-quota anymore -> no donors at
        // all; the guard under test is the floor, so make the donor day over-quota by
        // ONE hour but at the floor: quota 4 (net), 5 lessons, floor 4 -> the day can
        // give 1 hour and stay AT the floor: allowed; the -1 below would not be.
        // Simplest discriminating setup: quota 4, 5 placed lessons, floor 4.
        StudentGroup group = schedule.getStudentGroups().get(0);
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quotaFixture(group, SnapshotTestSupport.DONOR_DATE, 3),
                quotaFixture(group, SnapshotTestSupport.TARGET_DATE, 2)));
        // DISCRIMINATING setup: net quota 3, FOUR lessons on the donor day, floor 4.
        // The quota gate alone PERMITS the donation (4 > 3); only the H33 floor guard
        // forbids it (4 - 1 = 3 < 4). With the guard deleted this test fails.
        for (int i = 0; i < 4 && i < schedule.getLessons().size(); i++) {
            schedule.getLessons().get(i).timeslot = schedule.getTimeslots().get(i);
        }
        for (int i = 4; i < schedule.getLessons().size(); i++) {
            schedule.getLessons().get(i).timeslot = null;   // extra lessons unassigned
        }
        schedule.setDailyActivityQuotas(java.util.Arrays.asList(
                quotaFixture(group, SnapshotTestSupport.DONOR_DATE, 3),
                quotaFixture(group, SnapshotTestSupport.TARGET_DATE, 2)));
        var floors = com.school.planner.service.DayFloorDeriver.deriveHardFloors(
                schedule.getStudentGroups(), schedule.getCourses(), schedule.getDailyActivityQuotas());
        int floor = floors.getOrDefault(group.name, 0);
        assertTrue(floor > 3, "fixture: the derived floor must exceed the net quota for the guard to bind (got " + floor + ")");
        SurplusHarvest.Result r = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);
        long donorHours = schedule.getLessons().stream()
                .filter(l -> l.timeslot != null && l.timeslot.getDate().equals(SnapshotTestSupport.DONOR_DATE))
                .count();
        assertTrue(donorHours >= floor,
                "donor must stay at or above its hard floor " + floor + ", got " + donorHours);
    }

    /**
     * v9 net quota: the day's AUTHORITATIVE pinned hours shrink the real target
     * (TASK-424 effectiveQuota). A day with raw quota 6 and 3 pinned hours has net
     * quota 3: 5 unpinned hours on it are over-net-quota by 2 and may donate; with
     * the raw quota the pass would see 5 < 6 and never touch the day (the H32
     * 0->-4 of the 22:40 revert).
     */
    @Test
    void on_dayWithAuthoritativePins_theNetQuotaDecides() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        SchoolSchedule schedule = buildSchedule();
        StudentGroup group = schedule.getStudentGroups().get(0);
        // 5 lessons on the donor date, raw quota 6 -> raw view says under quota
        for (int i = 0; i < 5 && i < schedule.getLessons().size(); i++) {
            schedule.getLessons().get(i).timeslot = schedule.getTimeslots().get(i);
        }
        for (int i = 5; i < schedule.getLessons().size(); i++) {
            schedule.getLessons().get(i).timeslot = null;
        }
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                quotaFixture(group, SnapshotTestSupport.DONOR_DATE, 6),
                quotaFixture(group, SnapshotTestSupport.TARGET_DATE, 2)));
        SchoolSchedule.setAuthoritativePinnedHoursForTest(
                Map.of(group.id, Map.of(SnapshotTestSupport.DONOR_DATE, 4)));
        SurplusHarvest.Result r = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);
        long onTarget = schedule.getLessons().stream()
                .filter(l -> l.timeslot != null && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE))
                .count();
        assertTrue(onTarget > 0, "net quota 2 (6 raw - 4 pinned) makes a 5h day over-quota: it must donate");
    }

    /**
     * v12 slide-companion H1 (diagnosis of the notte-4sep delivered state: moves=0
     * on every occasion while viable donors existed). canSlideUp proves the
     * follower's destination group-free and VRP-fresh, but nobody checks the
     * follower's TEACHER free at the slide destination: on a full donor day the
     * slide puts the follower next to another group's lesson of the same teacher,
     * the per-move oracle reverts the applied move (the reverted=208-736/occasion
     * signature), and the deterministic re-search picks the same doomed donor
     * again. The clean donor later in the pool never gets tried.
     */
    @Test
    void on_followerTeacherBusyAtSlideDestination_theCleanDonorFillsTheCell() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        Teacher t1 = new Teacher("DonorTeacher");
        t1.id = 1L;
        Teacher t2 = new Teacher("FollowerTeacher");
        t2.id = 2L;
        StudentGroup group = new StudentGroup("SlideGroup", 6);
        group.id = 1L;
        group.startDate = LocalDate.of(2026, 1, 1);
        StudentGroup other = new StudentGroup("OtherGroup", 6);
        other.id = 2L;
        other.startDate = LocalDate.of(2026, 1, 1);
        Course donorCourse = new Course("DonorSubject", t1, group, 100);
        donorCourse.id = 1L;
        Course followerCourse = new Course("FollowerSubject", t2, group, 100);
        followerCourse.id = 2L;
        Course otherCourse = new Course("OtherSubject", t2, other, 100);
        otherCourse.id = 3L;

        java.util.List<Timeslot> timeslots = grid(SnapshotTestSupport.DONOR_DATE, SnapshotTestSupport.TARGET_DATE);

        SchoolSchedule.setDailyActivityQuotasStatic(java.util.Arrays.asList(
                quotaFixture(group, SnapshotTestSupport.DONOR_DATE, 2),
                quotaFixture(group, SnapshotTestSupport.TARGET_DATE, 1)));

        java.util.List<Lesson> lessons = new java.util.ArrayList<>();
        lesson(lessons, donorCourse, timeslots.get(0));
        lesson(lessons, donorCourse, timeslots.get(1));
        lesson(lessons, followerCourse, timeslots.get(2));
        // The H1 source: ANOTHER group's lesson with the follower's teacher sits in
        // the slot the follower would slide INTO (slot 1 of the donor date).
        lesson(lessons, otherCourse, timeslots.get(1));

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(group, other), List.of(t1, t2),
                List.of(donorCourse, followerCourse, otherCourse), lessons, List.of());

        var result = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        assertTrue(result.rejectBreakdown().contains("slideH1=1"),
                "the follower-teacher conflict must be REJECTED IN SEARCH (counted), got: "
                        + result.rejectBreakdown());
        assertEquals(1, result.movesApplied(),
                "the clean donor (slot 2, no followers) must fill the cell after the "
                        + "slide-conflicted donor is rejected");
        long onTarget = lessons.stream()
                .filter(l -> l.timeslot != null
                        && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE))
                .count();
        assertEquals(1, onTarget, "the target day must hold exactly the moved hour");
    }

    /**
     * v12 donor-exclusion retry (same diagnosis, the apply-time channel): when the
     * BEST donor passes every search gate but fails at APPLY (here: its room is
     * busy at the landing, the voice-2 guard), the hour must fall through to the
     * NEXT donor. Before v12 the h-loop consumed the whole cell on the one doomed
     * deterministic re-pick (moves=0 on the notte-4sep cells).
     */
    @Test
    void on_bestDonorRejectedAtApply_theNextDonorFillsTheCell() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        Teacher t1 = new Teacher("DonorTeacher");
        t1.id = 1L;
        Teacher tb = new Teacher("RoomHolderTeacher");
        tb.id = 2L;
        StudentGroup group = new StudentGroup("RetryGroup", 6);
        group.id = 1L;
        group.startDate = LocalDate.of(2026, 1, 1);
        StudentGroup holder = new StudentGroup("RoomHolderGroup", 6);
        holder.id = 2L;
        holder.startDate = LocalDate.of(2026, 1, 1);
        Course course = new Course("DonorSubject", t1, group, 100);
        course.id = 1L;
        Course holderCourse = new Course("HolderSubject", tb, holder, 100);
        holderCourse.id = 2L;
        Room busyRoom = new Room("BusyRoom", 20, List.of());

        java.util.List<Timeslot> timeslots = grid(SnapshotTestSupport.DONOR_DATE, SnapshotTestSupport.TARGET_DATE);

        SchoolSchedule.setDailyActivityQuotasStatic(java.util.Arrays.asList(
                quotaFixture(group, SnapshotTestSupport.DONOR_DATE, 2),
                quotaFixture(group, SnapshotTestSupport.TARGET_DATE, 1)));

        java.util.List<Lesson> lessons = new java.util.ArrayList<>();
        lesson(lessons, course, timeslots.get(0));
        Lesson doomed = lesson(lessons, course, timeslots.get(1));
        doomed.room = busyRoom;
        Lesson clean = lesson(lessons, course, timeslots.get(2));
        // The apply-time blocker: ANOTHER group's lesson sits in the doomed donor's
        // room at the TARGET's first slot. The search gates see none of this (teacher
        // free, group free): only applyMove's roomBusy guard rejects it.
        Lesson roomHolder = lesson(lessons, holderCourse, timeslots.get(8));   // TARGET slot 0
        roomHolder.room = busyRoom;

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(busyRoom), List.of(), List.of(),
                List.of(), List.of(group, holder), List.of(t1, tb),
                List.of(course, holderCourse), lessons, List.of());

        var result = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        assertEquals(1, result.movesApplied(),
                "after the room-blocked best donor is excluded, the clean donor must move");
        Lesson onTarget = lessons.stream()
                .filter(l -> l.timeslot != null
                        && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE)
                        && l.course == course)
                .findFirst().orElseThrow();
        assertEquals(clean.id, onTarget.id, "the moved hour must be the CLEAN donor, not the room-blocked one");
    }

    /**
     * v12 per-CELL exclusion scope (altitude review pin): a donor whose move failed
     * for cell A (room busy at A's landing) must stay eligible for cell B. A leaky
     * per-PASS exclusion would starve the sister cells and read as "noDonors" in
     * production while this test stays green.
     */
    @Test
    void on_crossCellDonorReuse_aDonorExcludedForOneCellServesTheNext() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        Teacher t1 = new Teacher("DonorTeacher");
        t1.id = 1L;
        Teacher tb = new Teacher("RoomHolderTeacher");
        tb.id = 2L;
        StudentGroup group = new StudentGroup("ReuseGroup", 6);
        group.id = 1L;
        group.startDate = LocalDate.of(2026, 1, 1);
        StudentGroup holder = new StudentGroup("RoomHolderGroup", 6);
        holder.id = 2L;
        holder.startDate = LocalDate.of(2026, 1, 1);
        Course course = new Course("DonorSubject", t1, group, 100);
        course.id = 1L;
        Course holderCourse = new Course("HolderSubject", tb, holder, 100);
        holderCourse.id = 2L;
        Room busyRoom = new Room("BusyRoom", 20, List.of());

        java.util.List<Timeslot> timeslots = grid(SnapshotTestSupport.DONOR_DATE,
                SnapshotTestSupport.TARGET_DATE, SnapshotTestSupport.TARGET2_DATE);

        SchoolSchedule.setDailyActivityQuotasStatic(java.util.Arrays.asList(
                quotaFixture(group, SnapshotTestSupport.DONOR_DATE, 1),
                quotaFixture(group, SnapshotTestSupport.TARGET_DATE, 1),
                quotaFixture(group, SnapshotTestSupport.TARGET2_DATE, 1)));

        java.util.List<Lesson> lessons = new java.util.ArrayList<>();
        lesson(lessons, course, timeslots.get(0));   // slot 0: border, never a donor
        Lesson donor = lesson(lessons, course, timeslots.get(1));   // the ONLY donor
        donor.room = busyRoom;
        // Cell A (TARGET_DATE): another group holds the donor's room at the landing.
        Lesson holderA = lesson(lessons, holderCourse, timeslots.get(8));   // A slot 0
        holderA.room = busyRoom;
        // Cell B (TARGET2_DATE): clean, nothing at the landing.

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(busyRoom), List.of(), List.of(),
                List.of(), List.of(group, holder), List.of(t1, tb),
                List.of(course, holderCourse), lessons, List.of());

        var result = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        assertEquals(1, result.movesApplied(),
                "the donor excluded for cell A must still serve cell B");
        assertEquals(SnapshotTestSupport.TARGET2_DATE, donor.timeslot.getDate(),
                "the donor must land on the CLEAN cell B, not stay on the donor day");
        long onA = lessons.stream()
                .filter(l -> l.timeslot != null
                        && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE))
                .count();
        assertEquals(1, onA, "cell A keeps the room holder's lesson only");
    }

    /**
     * v12.1 scope alignment (bench 6/09: moves spent on cells the constraint never
     * counts): H34 excludes 1/G08 (pool-cap form-b) and G14 via
     * isQuotaFillExcluded, but the pass's target enumeration filled every group's
     * empty declared day. The pass must target the constraint's own population:
     * an excluded group's cell stays empty even when a donor exists, so the move
     * budget and the saturated cap go where the hard score counts.
     */
    @Test
    void on_h34ExcludedGroups_theirEmptyDaysAreNotTargets() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        Teacher t1 = new Teacher("DonorTeacher");
        t1.id = 1L;
        StudentGroup inScope = new StudentGroup("FillGroup", 6);
        inScope.id = 1L;
        inScope.startDate = LocalDate.of(2026, 1, 1);
        StudentGroup g14 = new StudentGroup("G14", 6);
        g14.id = 2L;
        g14.startDate = LocalDate.of(2026, 1, 1);
        Course inScopeCourse = new Course("InScopeSubject", t1, inScope, 100);
        inScopeCourse.id = 1L;
        Course G14Course = new Course("G14Subject", t1, g14, 100);
        G14Course.id = 2L;

        java.util.List<Timeslot> timeslots = grid(SnapshotTestSupport.DONOR_DATE,
                SnapshotTestSupport.TARGET_DATE, SnapshotTestSupport.TARGET2_DATE);

        SchoolSchedule.setDailyActivityQuotasStatic(java.util.Arrays.asList(
                quotaFixture(inScope, SnapshotTestSupport.DONOR_DATE, 1),
                quotaFixture(inScope, SnapshotTestSupport.TARGET_DATE, 1),
                quotaFixture(g14, SnapshotTestSupport.DONOR_DATE, 1),
                quotaFixture(g14, SnapshotTestSupport.TARGET2_DATE, 1)));

        java.util.List<Lesson> lessons = new java.util.ArrayList<>();
        lesson(lessons, inScopeCourse, timeslots.get(0));   // DONOR slot 0 (border, never moves)
        lesson(lessons, inScopeCourse, timeslots.get(1));   // DONOR slot 1: the in-scope donor
        lesson(lessons, G14Course, timeslots.get(2));     // DONOR slot 2 (border)
        lesson(lessons, G14Course, timeslots.get(3));     // DONOR slot 3: the G14 donor

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(inScope, g14), List.of(t1),
                List.of(inScopeCourse, G14Course), lessons, List.of());

        var result = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        long onInScopeTarget = lessons.stream()
                .filter(l -> l.timeslot != null
                        && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE))
                .count();
        long onG14Target = lessons.stream()
                .filter(l -> l.timeslot != null
                        && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET2_DATE))
                .count();
        assertTrue(onInScopeTarget > 0, "the in-scope cell must receive the hour");
        assertEquals(0, onG14Target,
                "an H34-excluded group's empty day is not a target (isQuotaFillExcluded)");
    }

    /**
     * v12.2 (review find, 6/09): the quota map the shape pre-filter reads is
     * FC-sheet-only. A non-quota group's target (FCQuota=false rows: class_days
     * groups) has NO row there, and permits(null) rejected every donor - the pass
     * was de facto G13-only, the in-scope cells shape-starved with the pass armed.
     * The VRP itself runs no shape gate for non-quota groups (its own filter is
     * gated on the group's quota map being non-empty): the pre-filter must mirror
     * that, not be stricter than the constraint it serves.
     */
    @Test
    void on_nonQuotaGroupTarget_theShapeFilterDoesNotStarveTheDonors() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        Teacher t1 = new Teacher("DonorTeacher");
        t1.id = 1L;
        StudentGroup group = new StudentGroup("ClassDaysGroup", 6);
        group.id = 1L;
        group.startDate = LocalDate.of(2026, 1, 1);
        Course course = new Course("ClassDaysSubject", t1, group, 100);
        course.id = 1L;

        java.util.List<Timeslot> timeslots = grid(SnapshotTestSupport.DONOR_DATE,
                SnapshotTestSupport.TARGET_DATE);

        com.school.planner.domain.DailyActivityQuota donorQuota =
                quotaFixture(group, SnapshotTestSupport.DONOR_DATE, 1);
        com.school.planner.domain.DailyActivityQuota targetQuota =
                quotaFixture(group, SnapshotTestSupport.TARGET_DATE, 1);
        donorQuota.FCQuota = false;   // class_days shape: universal map only
        targetQuota.FCQuota = false;
        SchoolSchedule.setDailyActivityQuotasStatic(java.util.Arrays.asList(donorQuota, targetQuota));

        java.util.List<Lesson> lessons = new java.util.ArrayList<>();
        lesson(lessons, course, timeslots.get(0));   // DONOR slot 0 (border)
        lesson(lessons, course, timeslots.get(1));   // DONOR slot 1: the donor

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(group), List.of(t1), List.of(course), lessons, List.of());

        SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        long onTarget = lessons.stream()
                .filter(l -> l.timeslot != null
                        && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE))
                .count();
        assertTrue(onTarget > 0,
                "a non-quota group's empty declared day must be fillable (VRP semantics: "
                        + "no quota map, no shape gate)");
    }

    /**
     * v12.3 (review find, 6/09): the pass was the ONLY family consumer reading the
     * quota with stage hours in it. A PURE-STAGE declared day (aula=0, lab=0) is
     * not an H34 cell (the provider requires aula+lab > 0) and must not become a
     * target: ordinary hours parked there are surplus the hard tier never asked
     * for. With donors available the stage day must stay empty.
     */
    @Test
    void on_pureStageDay_notATarget_staysEmpty() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        Teacher t1 = new Teacher("DonorTeacher");
        t1.id = 1L;
        StudentGroup group = new StudentGroup("StageDayGroup", 6);
        group.id = 1L;
        group.startDate = LocalDate.of(2026, 1, 1);
        Course course = new Course("StageDaySubject", t1, group, 100);
        course.id = 1L;

        java.util.List<Timeslot> timeslots = grid(SnapshotTestSupport.DONOR_DATE,
                SnapshotTestSupport.TARGET_DATE);
        // donor day: quota 1 aula, 3 lessons = surplus 2; target day: STAGE ONLY.
        // class_days shape (FCQuota=false: NO shape gate) - the discriminating
        // case: a quota-group stage row would be shape-protected by accident.
        com.school.planner.domain.DailyActivityQuota stageOnly =
                new com.school.planner.domain.DailyActivityQuota(
                        group, SnapshotTestSupport.TARGET_DATE, 0, 0, 4);
        stageOnly.FCQuota = false;
        com.school.planner.domain.DailyActivityQuota donorRow =
                quotaFixture(group, SnapshotTestSupport.DONOR_DATE, 1);
        donorRow.FCQuota = false;
        SchoolSchedule.setDailyActivityQuotasStatic(java.util.Arrays.asList(donorRow, stageOnly));

        java.util.List<Lesson> lessons = new java.util.ArrayList<>();
        lesson(lessons, course, timeslots.get(0));   // border
        lesson(lessons, course, timeslots.get(1));   // donor
        lesson(lessons, course, timeslots.get(2));   // donor

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(group), List.of(t1), List.of(course), lessons, List.of());

        var result = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        long onStageDay = lessons.stream()
                .filter(l -> l.timeslot != null
                        && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE))
                .count();
        assertEquals(0, onStageDay, "a pure-stage day is not an H34 target");
        assertEquals(0, result.movesApplied());
    }

    /**
     * v12.3, the mixed-day half: a target with aula=2 AND stage=3 must fill to the
     * AULA+LAB quota (2), not the stage-inflated 5: with the inflated quota the v10
     * atomicity rolled the fill back and the genuine H34 cell stayed empty.
     */
    @Test
    void on_mixedStageDay_theQuotaIsAulaLabNotStage() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        Teacher t1 = new Teacher("DonorTeacher");
        t1.id = 1L;
        StudentGroup group = new StudentGroup("MixedDayGroup", 6);
        group.id = 1L;
        group.startDate = LocalDate.of(2026, 1, 1);
        Course course = new Course("MixedDaySubject", t1, group, 100);
        course.id = 1L;

        java.util.List<Timeslot> timeslots = grid(SnapshotTestSupport.DONOR_DATE,
                SnapshotTestSupport.TARGET_DATE);
        // donor day: quota 1 aula, 3 lessons = surplus 2; target: aula 2 + stage 3
        com.school.planner.domain.DailyActivityQuota mixed =
                new com.school.planner.domain.DailyActivityQuota(
                        group, SnapshotTestSupport.TARGET_DATE, 2, 0, 3);
        mixed.FCQuota = true;
        SchoolSchedule.setDailyActivityQuotasStatic(java.util.Arrays.asList(
                quotaFixture(group, SnapshotTestSupport.DONOR_DATE, 1), mixed));

        java.util.List<Lesson> lessons = new java.util.ArrayList<>();
        lesson(lessons, course, timeslots.get(0));   // border
        lesson(lessons, course, timeslots.get(1));   // donor
        lesson(lessons, course, timeslots.get(2));   // donor

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(group), List.of(t1), List.of(course), lessons, List.of());

        SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        long onTarget = lessons.stream()
                .filter(l -> l.timeslot != null
                        && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE))
                .count();
        assertEquals(2, onTarget, "the cell fills to its aula+lab quota (2), stage excluded");
    }

    /**
     * v13 chain (the measured wall's cure: donors that pass every gate except
     * teacherBusy-at-landing, three-for-three samples 6/09). The blocker B (group H,
     * donor's teacher, sitting in the target's first-open slot) donates ITS hour to
     * group H's own quota-headroom landing, freeing the slot; the donor lands there.
     * Trigger: ONLY where the plain search found no viable donor (inert in good
     * basins by construction). B keeps its teacher, so H1 is fixed if B's landing
     * slot is T1-free; H32 counts move within H (over-quota day loses one,
     * headroom day gains one); H17/H31 safe because B lands adjacent to H's block.
     */
    @Test
    void on_teacherBlockedLanding_theChainUnblocksTheCell() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        Teacher t1 = new Teacher("ChainTeacher");
        t1.id = 1L;
        Teacher tb = new Teacher("OtherTeacher");
        tb.id = 2L;
        StudentGroup g = new StudentGroup("ChainGroup", 6);
        g.id = 1L;
        g.startDate = LocalDate.of(2026, 1, 1);
        StudentGroup h = new StudentGroup("BlockerGroup", 6);
        h.id = 2L;
        h.startDate = LocalDate.of(2026, 1, 1);
        Course gCourse = new Course("ChainSubject", t1, g, 100);
        gCourse.id = 1L;
        Course hCourse = new Course("BlockerSubject", t1, h, 100);
        hCourse.id = 2L;
        Course hOther = new Course("OtherSubject", tb, h, 100);
        hOther.id = 3L;

        // three dates: G's donor day, the shared target day, H's headroom day
        java.util.List<Timeslot> timeslots = grid(SnapshotTestSupport.DONOR_DATE,
                SnapshotTestSupport.TARGET_DATE, SnapshotTestSupport.TARGET2_DATE);

        SchoolSchedule.setDailyActivityQuotasStatic(java.util.Arrays.asList(
                quotaFixture(g, SnapshotTestSupport.DONOR_DATE, 1),
                quotaFixture(g, SnapshotTestSupport.TARGET_DATE, 1),
                quotaFixture(h, SnapshotTestSupport.TARGET_DATE, 1),
                quotaFixture(h, SnapshotTestSupport.TARGET2_DATE, 3)));   // headroom: 2 placed, B makes 3/3

        java.util.List<Lesson> lessons = new java.util.ArrayList<>();
        lesson(lessons, gCourse, timeslots.get(0));   // G donor day slot 0 (border)
        lesson(lessons, gCourse, timeslots.get(1));   // G donor day slot 1: the donor
        Lesson blocker = lesson(lessons, hCourse, timeslots.get(8));   // TARGET slot 0: THE BLOCKER (T1) at the landing slot
        lesson(lessons, hOther, timeslots.get(10));   // H slot 2: GAPPED day - the tail-swap is gated off, this pins the CROSS-DATE family
        lesson(lessons, hOther, timeslots.get(16));   // H headroom day slot 0
        lesson(lessons, hOther, timeslots.get(17));   // H headroom day slot 1: B lands adjacent at slot 2

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(g, h), List.of(t1, tb),
                List.of(gCourse, hCourse, hOther), lessons, List.of());

        var result = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        long onTarget = lessons.stream()
                .filter(l -> l.timeslot != null
                        && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE))
                .count();
        assertTrue(result.movesApplied() > 0, "the chain must close the teacher-blocked cell");
        assertEquals(2, onTarget, "target day: the donor's landing + H's slot-1 lesson (B left)");
        assertEquals(SnapshotTestSupport.TARGET2_DATE, blocker.timeslot.getDate(),
                "the blocker donated to H's headroom day");
    }

    /**
     * v13 chain, inertness side: without headroom for B (H's other day at quota
     * already), the chain must NOT fire destructively - the cell stays empty and
     * nothing moves. The trigger contract: the chain only rides on a legal
     * two-level move, never on a partial one.
     */
    @Test
    void on_noHeadroomForBlocker_theChainStaysInert() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        Teacher t1 = new Teacher("ChainTeacher");
        t1.id = 1L;
        Teacher tb = new Teacher("OtherTeacher");
        tb.id = 2L;
        StudentGroup g = new StudentGroup("ChainGroup", 6);
        g.id = 1L;
        g.startDate = LocalDate.of(2026, 1, 1);
        StudentGroup h = new StudentGroup("BlockerGroup", 6);
        h.id = 2L;
        h.startDate = LocalDate.of(2026, 1, 1);
        Course gCourse = new Course("ChainSubject", t1, g, 100);
        gCourse.id = 1L;
        Course hCourse = new Course("BlockerSubject", t1, h, 100);
        hCourse.id = 2L;
        Course hOther = new Course("OtherSubject", tb, h, 100);
        hOther.id = 3L;

        java.util.List<Timeslot> timeslots = grid(SnapshotTestSupport.DONOR_DATE,
                SnapshotTestSupport.TARGET_DATE, SnapshotTestSupport.TARGET2_DATE);

        // H's TARGET2 day is AT quota with 2 lessons: no headroom for B
        SchoolSchedule.setDailyActivityQuotasStatic(java.util.Arrays.asList(
                quotaFixture(g, SnapshotTestSupport.DONOR_DATE, 1),
                quotaFixture(g, SnapshotTestSupport.TARGET_DATE, 1),
                quotaFixture(h, SnapshotTestSupport.TARGET_DATE, 1),
                quotaFixture(h, SnapshotTestSupport.TARGET2_DATE, 2)));

        java.util.List<Lesson> lessons = new java.util.ArrayList<>();
        lesson(lessons, gCourse, timeslots.get(0));
        lesson(lessons, gCourse, timeslots.get(1));
        Lesson blocker = lesson(lessons, hCourse, timeslots.get(8));   // blocker AT the landing slot
        lesson(lessons, hOther, timeslots.get(10));   // H slot 2: gapped day - tail-swap gated, cross-date has no headroom -> inert
        lesson(lessons, hOther, timeslots.get(16));
        lesson(lessons, hOther, timeslots.get(17));   // headroom day at quota (2/2)

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(g, h), List.of(t1, tb),
                List.of(gCourse, hCourse, hOther), lessons, List.of());

        var result = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        assertEquals(0, result.movesApplied(), "no legal two-level move: nothing may move");
        assertEquals(SnapshotTestSupport.TARGET_DATE, blocker.timeslot.getDate());
        assertTrue(result.rejectBreakdown().contains("chainNearMiss=8"),
                "the chain must FIND the near-miss (pin the reason: no landing, "
                        + "not no-scan), got: " + result.rejectBreakdown());
    }

    /**
     * v13.1 same-date tail-swap: the blocker whose slide is clean but has NO
     * cross-date headroom (the attempt-3 sample: 24 chainFail) swaps to the TAIL
     * of its own day - the day's lesson SET is identical (only which lesson sits
     * where changes), so H17/H31/H32 of H are preserved BY CONSTRUCTION while the
     * teacher frees the landing slot the donor needs.
     */
    @Test
    void on_noCrossDateHeadroom_theSameDateTailSwapUnblocksTheCell() {
        System.setProperty(SurplusHarvest.FLAG, "true");
        Teacher t1 = new Teacher("ChainTeacher");
        t1.id = 1L;
        Teacher tb = new Teacher("OtherTeacher");
        tb.id = 2L;
        StudentGroup g = new StudentGroup("ChainGroup", 6);
        g.id = 1L;
        g.startDate = LocalDate.of(2026, 1, 1);
        StudentGroup h = new StudentGroup("BlockerGroup", 6);
        h.id = 2L;
        h.startDate = LocalDate.of(2026, 1, 1);
        Course gCourse = new Course("ChainSubject", t1, g, 100);
        gCourse.id = 1L;
        Course hCourse = new Course("BlockerSubject", t1, h, 100);
        hCourse.id = 2L;
        Course hOther = new Course("OtherSubject", tb, h, 100);
        hOther.id = 3L;

        // two dates only: NO third day exists, so cross-date headroom is impossible
        java.util.List<Timeslot> timeslots = grid(SnapshotTestSupport.DONOR_DATE,
                SnapshotTestSupport.TARGET_DATE);

        SchoolSchedule.setDailyActivityQuotasStatic(java.util.Arrays.asList(
                quotaFixture(g, SnapshotTestSupport.DONOR_DATE, 1),
                quotaFixture(g, SnapshotTestSupport.TARGET_DATE, 1),
                quotaFixture(h, SnapshotTestSupport.TARGET_DATE, 2)));

        java.util.List<Lesson> lessons = new java.util.ArrayList<>();
        lesson(lessons, gCourse, timeslots.get(0));   // border
        lesson(lessons, gCourse, timeslots.get(1));   // the donor
        Lesson blocker = lesson(lessons, hCourse, timeslots.get(8));   // TARGET slot 0: THE BLOCKER
        lesson(lessons, hOther, timeslots.get(9));    // H slot 1 (slides into 0)
        // H's day on TARGET: {blocker@0, other@1}, quota 2 = AT quota: cross-date
        // donation would drop it to 1/2 (H32); the tail-sap keeps it 2/2.

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(g, h), List.of(t1, tb),
                List.of(gCourse, hCourse, hOther), lessons, List.of());

        var result = SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        long onTarget = lessons.stream()
                .filter(l -> l.timeslot != null
                        && l.timeslot.getDate().equals(SnapshotTestSupport.TARGET_DATE))
                .count();
        assertTrue(result.movesApplied() > 0, "the tail-swap must close the cell");
        assertEquals(3, onTarget, "donor landed + H's two (blocker now at the tail)");
        assertEquals(TimeslotSequence.slotIndexOf(blocker.timeslot), 1,
                "the blocker swapped to the tail (its follower took slot 0)");
    }

    private com.school.planner.domain.DailyActivityQuota quotaFixture(
            StudentGroup group, LocalDate date, int hours) {
        com.school.planner.domain.DailyActivityQuota q =
                new com.school.planner.domain.DailyActivityQuota(group, date, hours, 0, 0);
        return q;
    }
}
