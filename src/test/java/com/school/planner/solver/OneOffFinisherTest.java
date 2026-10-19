package com.school.planner.solver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.school.planner.TestDataBuilder;
import com.school.planner.bootstrap.SnapshotFixture;
import com.school.planner.domain.Course;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-577 fifth placement path (cycle 2026-09-04, two IllegalStateException
 * assertValueRange): {@code extractAndCompact} slides the same-group followers
 * of the extracted lesson back one grid slot, and NOTHING checked the per-cell
 * legality of those slide destinations (the plan loop verified grid existence,
 * the post-state loop teacher/group/room - never the follower's own value
 * range). A follower whose fresh window excludes the slot it is slid into lands
 * out of range, and the next solver start (cache-free clone) explodes. The
 * fourth path (chainFreeTheTagRoom block translate) got the per-slot
 * {@code isSlotLegalFresh} guard in TASK-578; this test pins the fifth.
 */
class OneOffFinisherTest {

    private static final LocalDate BLOCK_DATE = LocalDate.of(2026, 9, 16);  // Wednesday
    private static final LocalDate LANDING_DATE = LocalDate.of(2026, 9, 17); // Thursday

    @BeforeEach
    @AfterEach
    void resetStatics() {
        // defensive on BOTH sides (the sibling convention): isSlotLegalFresh decides
        // on six published statics, and this suite shares one JVM - a static leaked
        // by another class would fail HERE pointing at the wrong suspect
        System.clearProperty(OneOffFinisher.FLAG);
        SnapshotFixture.resetStatics();
    }

    /**
     * The regression: with a fresh window [0..1] on the block's weekday, the
     * slot-3 follower's slide destination (slot 2) is OUT of range. The whole
     * extraction plan must be rejected - applying it would place that follower
     * outside its value range (the crash class).
     */
    @Test
    void on_followerSlideDestinationOutOfFreshWindow_thePlanIsRejected() {
        Fixture f = build();
        // window on the BLOCK weekday: slots 0..1 only
        SchoolSchedule.setGroupCalendarRulesStatic(List.of(new GroupCalendarRule(
                f.group, BLOCK_DATE.getDayOfWeek(), 0, 1)));

        // fixture sanity: the discriminating facts must hold
        assertFalse(f.followerLate.isSlotLegalFresh(f.slot(BLOCK_DATE, 2)),
                "fixture: window [0..1] must make slot 2 illegal for the late follower");
        assertTrue(f.followerEarly.isSlotLegalFresh(f.slot(BLOCK_DATE, 1)),
                "fixture: slot 1 stays legal for the early follower (window [0..1])");

        OneOffFinisher.repair(f.schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        // the plan is ATOMIC: when any follower cannot slide legally, NOBODY moves.
        // Pinning every mover's position (not just the blocked follower) is what
        // catches a future regression of the guard from plan-atomic to per-follower
        // (slide the legal followers, leave the illegal one: still a crash class).
        assertEquals(f.slot(BLOCK_DATE, 3), f.followerLate.timeslot,
                "a follower whose slide destination is outside its fresh window must not move");
        assertEquals(f.slot(BLOCK_DATE, 2), f.followerEarly.timeslot,
                "the plan is atomic: the legal follower must not slide either");
        assertEquals(f.slot(BLOCK_DATE, 1), f.extracted.timeslot,
                "the plan is atomic: the extraction itself must not happen");
    }

    /**
     * Positive control: same fixture, no window - the extraction is fully legal
     * and MUST apply. Pins that the guard rejects only the illegal plan, not the
     * whole path (and keeps this fixture honestly alive).
     */
    @Test
    void on_legalSlide_theExtractionApplies() {
        Fixture f = build();

        OneOffFinisher.repair(f.schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        assertEquals(LANDING_DATE, f.extracted.timeslot.getDate(),
                "the roomless mid-block lesson must extract to the other day");
        assertEquals(f.slot(BLOCK_DATE, 1), f.followerEarly.timeslot,
                "the early follower must slide back one slot");
        assertEquals(f.slot(BLOCK_DATE, 2), f.followerLate.timeslot,
                "the late follower must slide back one slot");
    }

    // ── fixture: one group with a 4-lesson Wednesday block (slots 0..3); the
    //    slot-1 lesson is ROOMLESS (family B target, mid-block so only
    //    extractAndCompact can serve it); Thursday carries one existing lesson
    //    at slot 0 (a PARTIAL day: landingQuotaSafe forbids declared-empty days
    //    by design, so the landing day must have hours > 0; slot 1 is the
    //    adjacent free edge). ──

    private static final class Fixture {
        SchoolSchedule schedule;
        StudentGroup group;
        Lesson extracted;      // slot 1, room == null
        Lesson followerEarly;  // slot 2 -> would slide to 1
        Lesson followerLate;   // slot 3 -> would slide to 2

        Timeslot slot(LocalDate d, int idx) {
            return schedule.getTimeslots().stream()
                    .filter(ts -> ts.getDate().equals(d))
                    .skip(idx).findFirst().orElseThrow();
        }
    }

    private Fixture build() {
        Teacher teacher = new Teacher("OofTeacher");
        teacher.id = 1L;
        StudentGroup group = new StudentGroup("OofGroup", 6);
        group.id = 1L;
        group.startDate = LocalDate.of(2026, 1, 1); // before both dates (H14)
        Course course = new Course("OofSubject", teacher, group, 100);
        course.id = 1L;
        Room room = new Room("AULA", 30, List.of());

        // the canonical school grid (single source of truth: the schema itself)
        List<Timeslot> timeslots = new ArrayList<>();
        long id = 1;
        for (LocalDate d : List.of(BLOCK_DATE, LANDING_DATE)) {
            for (LocalTime start : TimeslotSequence.SLOT_START_TIMES) {
                Timeslot ts = TestDataBuilder.timeslot(id++, LocalDateTime.of(d, start));
                timeslots.add(ts);
            }
        }

        // quotas: block day 3 (4 placed = over by one, may give an hour away),
        // landing day 4 (1 placed, under quota: receives one, H32 improves)
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                new com.school.planner.domain.DailyActivityQuota(group, BLOCK_DATE, 3, 0, 0),
                new com.school.planner.domain.DailyActivityQuota(group, LANDING_DATE, 4, 0, 0)));

        List<Lesson> lessons = new ArrayList<>();
        Lesson first = TestDataBuilder.lesson(1L, course, timeslots.get(0), room);
        Lesson extracted = TestDataBuilder.lesson(2L, course, timeslots.get(1), null); // roomless, mid-block
        Lesson followerEarly = TestDataBuilder.lesson(3L, course, timeslots.get(2), room);
        Lesson followerLate = TestDataBuilder.lesson(4L, course, timeslots.get(3), room);
        // the landing day's own lesson: makes Thursday a PARTIAL day (legal edge
        // landing) and keeps the teacher busy at its slot 0
        Lesson landingAnchor = TestDataBuilder.lesson(5L, course, timeslots.get(8), room); // LANDING slot 0
        lessons.add(first);
        lessons.add(extracted);
        lessons.add(followerEarly);
        lessons.add(followerLate);
        lessons.add(landingAnchor);

        SchoolSchedule schedule = new SchoolSchedule(timeslots, List.of(room), List.of(),
                List.of(), List.of(), List.of(group), List.of(teacher), List.of(course),
                lessons, List.of());

        // the static grid the FRESH range computes from (unpublished = empty range)
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        // resolveRoom iterates the STATIC rooms list (unpublished = no room = no move)
        SchoolSchedule.setAllRoomsStatic(List.of(room));

        Fixture f = new Fixture();
        f.schedule = schedule;
        f.group = group;
        f.extracted = extracted;
        f.followerEarly = followerEarly;
        f.followerLate = followerLate;
        return f;
    }
}
