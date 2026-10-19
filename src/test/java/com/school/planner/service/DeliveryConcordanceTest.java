package com.school.planner.service;

import java.time.LocalDateTime;
import java.util.List;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * TASK-526 seconda meta' (5/09): the delivery trust metric. The 19/08 signature
 * (in-memory 0hard, DB with 2859 room-null lessons) was invisible until a human
 * opened the DB; {@code verifyDeliveryHardConcordance} recounts the H29 family in
 * both worlds at the delivery boundary. These tests pin the two outcomes on an H2
 * fixture that carries every exemption class of the constraint's own filter
 * (pinned roomless, pseudo-teacher roomless, unplaced) so the counts cannot
 * drift apart by predicate mismatch.
 */
@QuarkusTest
class DeliveryConcordanceTest {

    @Inject
    ScheduleService scheduleService;

    @Inject
    EntityManager em;

    Lesson placedWithRoom;      // real teacher, placed, roomed: not H29
    Lesson placedRoomless;      // real teacher, placed, NO room: the H29 population (1)
    Lesson pinnedRoomless;      // PINNED roomless: exempt by the constraint's filter
    Lesson pseudoRoomless;      // pseudo-teacher roomless: exempt
    Lesson unplaced;            // timeslot null: not H29 (not placed)
    Lesson courseNullRoomless;  // COURSE NULL, placed, unpinned, no room: the LEFT
                                // JOIN branch the production comment exists for

    // the FULL clear list (the pin-lifecycle set), run on BOTH sides: BEFORE
    // (previous classes may leave rows behind: a partial delete hits FKs) and
    // AFTER - this fixture's Room leaks into GoldenScheduleImportServiceTest
    // otherwise (it asserts no room exists for its import, hit on 5/09)
    private void clearDomainTables() {
        for (String entity : List.of("Lesson", "TeacherAvailability", "RecurringAvailabilityRule",
                "LabRotation", "LabAssistant", "ProfessionalizzantiDay", "GroupCalendarRule",
                "DailyActivityQuota", "StagePattern", "Course", "Teacher", "StudentGroup",
                "Timeslot", "Room")) {
            em.createQuery("DELETE FROM " + entity).executeUpdate();
        }
    }

    @AfterEach
    @Transactional
    void cleanup() {
        clearDomainTables();
    }

    @BeforeEach
    @Transactional
    void seed() {
        clearDomainTables();

        StudentGroup group = new StudentGroup("ConcGroup", 6);
        group.persist();
        Teacher real = new Teacher("ConcReal");
        real.persist();
        Teacher pseudo = new Teacher("ConcPseudo");
        pseudo.pseudo = true;
        pseudo.persist();
        Course realCourse = new Course("ConcSubject", real, group, 10);
        realCourse.persist();
        Course pseudoCourse = new Course("ConcPseudoSubject", pseudo, group, 10);
        pseudoCourse.persist();
        Timeslot ts = new Timeslot(LocalDateTime.of(2026, 9, 16, 8, 0),
                LocalDateTime.of(2026, 9, 16, 9, 0));
        ts.persist();
        Room room = new Room("CONC-AULA", 30, List.of());
        room.persist();

        placedWithRoom = lesson(realCourse, ts, room, false);
        placedRoomless = lesson(realCourse, ts, null, false);
        pinnedRoomless = lesson(realCourse, ts, null, true);
        pseudoRoomless = lesson(pseudoCourse, ts, null, false);
        unplaced = lesson(realCourse, null, null, false);
        courseNullRoomless = new Lesson();
        courseNullRoomless.course = null;
        courseNullRoomless.timeslot = ts;
        courseNullRoomless.room = null;
        courseNullRoomless.pinned = false;
        courseNullRoomless.persist();
    }

    private SchoolSchedule delivered() {
        return new SchoolSchedule(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(placedWithRoom, placedRoomless, pinnedRoomless, pseudoRoomless,
                        unplaced, courseNullRoomless),
                List.of());
    }

    /**
     * The production shape of the first live firing (5/09 18:50): the delivery
     * boundary runs on the RAW "staged-solve" daemon thread, where NO CDI request
     * context is active. The sibling tests run on the JUnit main thread (request
     * context active), which is exactly why the H2 suite stayed green while the
     * batch's first delivery logged ContextNotActiveException. The recount must
     * not depend on the caller's context.
     */
    @Test
    void onRawDaemonThread_theCheckStillCounts() throws Exception {
        java.util.concurrent.atomic.AtomicReference<ScheduleService.DeliveryConcordance> out =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> thrown =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread daemon = new Thread(() -> {
            try {
                out.set(scheduleService.verifyDeliveryHardConcordance(delivered()));
            } catch (Throwable t) {
                thrown.set(t);
            }
        }, "staged-solve-shape");
        daemon.setDaemon(true);
        daemon.start();
        daemon.join(10_000);

        assertNull(thrown.get(),
                "the check must not depend on a CDI request context: " + thrown.get());
        assertNotNull(out.get(),
                "no result: the daemon hung past the 10s join (timeout, not a discordance)");
        assertTrue(out.get().ok(),
                "the raw-thread recount must stay concordant: " + out.get());
    }

    @Test
    void concordantDelivery_reportsOk() {
        // outside any transaction, exactly like the wrapper's post-save boundary
        ScheduleService.DeliveryConcordance c =
                scheduleService.verifyDeliveryHardConcordance(delivered());

        assertTrue(c.ok(), "memory=" + c.memoryRoomless() + " db=" + c.dbRoomless());
        assertEquals(2, c.memoryRoomless(),
                "the real-teacher roomless one plus the course-null one");
        assertEquals(2, c.dbRoomless(), "the DB must see the same two lessons"
                + " (the LEFT JOIN keeps the course-null row)");
    }

    @Test
    void divergentDelivery_the1908Signature_isDetectedLoud() {
        SchoolSchedule delivered = delivered();

        // the 19/08 shape: the DB silently loses a room the scored grid still has
        // (here simulated by a direct JPQL update in its own transaction, the way
        // a lost/partial save would leave the persisted row behind the memory)
        QuarkusTransaction.requiringNew().run(() ->
                em.createQuery("UPDATE Lesson l SET l.room = null WHERE l.id = :id")
                        .setParameter("id", placedWithRoom.id)
                        .executeUpdate());

        scheduleService.reportDeliveryConcordance(delivered);

        String error = scheduleService.getLastSolverError();
        assertFalse(error == null || !error.contains("DELIVERY DIVERGENCE"),
                "the divergence must reach lastSolverError, got: " + error);
    }

    /**
     * The check's own failure must never block the delivery (the wrapper runs on
     * a daemon thread: a propagated exception would kill it silently), and the
     * error APPENDS to an existing lastSolverError - when the restore save failed
     * first, that exception is the cause and must survive the symptom.
     */
    @Test
    void checkFailure_neverBlocksAndAppends() {
        scheduleService.reportDeliveryConcordance(delivered());   // concordant: no error set

        // NOT an anonymous SchoolSchedule subclass: Timefold's Quarkus enhancer
        // scans those as solution candidates and refuses the captured-this ctor
        // ("must have a no-args constructor"). An unreadable LESSON LIST carries
        // the same failure into the recount without touching the domain hierarchy.
        SchoolSchedule exploding = new SchoolSchedule(List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new java.util.AbstractList<Lesson>() {
                    @Override
                    public Lesson get(int index) {
                        throw new IllegalStateException("boom");
                    }

                    @Override
                    public int size() {
                        throw new IllegalStateException("boom: the recount cannot read the grid");
                    }
                },
                List.of());
        scheduleService.reportDeliveryConcordance(exploding);     // must NOT throw

        String error = scheduleService.getLastSolverError();
        assertFalse(error == null || !error.contains("concordance check failed"),
                "the check failure must be loud in lastSolverError, got: " + error);
    }

    private static Lesson lesson(Course course, Timeslot ts, Room room, boolean pinned) {
        Lesson l = new Lesson();
        l.course = course;
        l.timeslot = ts;
        l.room = room;
        l.pinned = pinned;
        l.persist();
        return l;
    }
}
