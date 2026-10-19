package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.service.HybridPhase1aReport;
import com.school.planner.service.HybridPhase1aService;
import com.school.planner.service.LessonGenerator;
import com.school.planner.service.SubjectRegistry;
import com.school.planner.solver.TimeslotSequence;

/**
 * TASK-449 (SPIKE 2): measurement test for the mono-room-tag pre-assignment allocator.
 *
 * <p>Seeds the real fixture WITH course classification ({@code requiredRoomTags} set, from the
 * single source of truth SubjectRegistry / seed/subjects.csv, same as production), then runs {@link
 * HybridPhase1aService#runMonoRoomTagPhase()} with the flag ON and PRINTS the per-tag verdict
 * table (pinned-with-room / skipped / residual / total). That table IS the spike's measurement:
 * how many of the room-null lab hours the allocator can pin with a valid room at 0hard.
 *
 * <p>In package {@code bootstrap} (not {@code service}) because the real-seed fixture machinery
 * ({@link RealSeedFixture}, {@code DataGenerator.loadSeedCsv}/{@code aggregateCourseHours}) is
 * package-private here - same precedent as {@link WeeklyTemplateConstructionRealSeedTest}'s own
 * constrained-teacher checkpoint test.
 *
 * <p>Asserts only smoke invariants (pinned lessons are complete: pinned + room + timeslot set;
 * no two same-tag pinned lessons share a (date, slot) on the single mono room); it does NOT
 * assert a specific pin count - the count is the measurement, reported not gated.
 */
@QuarkusTest
class MonoRoomTagAllocatorPhaseTest {

    @Inject
    DataGenerator dataGenerator;
    @Inject
    LessonGenerator lessonGenerator;
    @Inject
    AvailabilityExpansionService availabilityExpansionService;
    @Inject
    EntityManager em;
    @Inject
    HybridPhase1aService hybridPhase1aService;

    private static final Map<String, Integer> MAX_HOURS_PER_DAY = RealSeedFixture.MAX_HOURS_PER_DAY;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM RecurringAvailabilityRule").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM DailyActivityQuota").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
        em.createQuery("DELETE FROM Room").executeUpdate();

        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(List.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setTeacherAvailabilitiesStatic(List.of());
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.of(), java.util.Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(null);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        System.clearProperty(HybridPhase1aService.MONO_ROOM_TAG_FLAG);
    }

    /**
     * Mirror of {@code WeeklyTemplateConstructionRealSeedTest.seedRealSeedFixture}, but ALSO
     * classifies courses ({@code isLab}/{@code requiredRoomTags}) via SubjectRegistry /
     * seed/subjects.csv (the single source of truth, same as production - it does NOT extend the
     * mapping beyond what exists in {@code Course.requiredRoomTags}), and seeds the real 18 rooms so
     * {@code findRoomForLesson} resolves each mono-room tag to its single room.
     */
    @Transactional
    void seedClassifiedFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, MAX_HOURS_PER_DAY);

        Map<String, Teacher> teachers = new LinkedHashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("teachers.csv", 6)) {
            Teacher t = new Teacher(row[0].trim());
            String maxDailyHours = row[3].trim();
            t.maxDailyHours = maxDailyHours.isEmpty() ? null : Integer.valueOf(maxDailyHours);
            t.persist();
            teachers.put(t.name, t);
        }

        // Real Courses (incarichi.csv) WITH classification (requiredRoomTags/isLab) from
        // SubjectRegistry / subjects.csv, same as production.
        DataGenerator.CourseAggregation aggregation =
                DataGenerator.aggregateCourseHours(DataGenerator.loadSeedCsv("incarichi.csv", 4));
        for (Map.Entry<DataGenerator.CourseKey, Integer> entry : aggregation.hoursByKey().entrySet()) {
            DataGenerator.CourseKey key = entry.getKey();
            Teacher teacher = teachers.get(key.teacherName());
            StudentGroup group = groups.get(key.groupName());
            if (teacher == null || group == null) {
                continue;
            }
            Course course = new Course(key.subject(), teacher, group, entry.getValue());
            List<String> tags = roomTagsFor(key.subject());
            course.isLab = !tags.isEmpty();
            course.requiredRoomTags = tags;
            course.persist();
            lessonGenerator.generateForCourse(course.id);
        }

        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();

        for (String[] row : DataGenerator.loadSeedCsv("availability_rules.csv", 8)) {
            Teacher teacher = teachers.get(row[0].trim());
            if (teacher == null) {
                continue;
            }
            DayOfWeek dayOfWeek = DayOfWeek.valueOf(row[1].trim());
            int minSlot = Integer.parseInt(row[2].trim());
            int maxSlot = Integer.parseInt(row[3].trim());
            AvailabilityType type = AvailabilityType.valueOf(row[4].trim());
            LocalDate effectiveFrom = row[5].isBlank() ? null : LocalDate.parse(row[5].trim());
            LocalDate effectiveTo = row[6].isBlank() ? null : LocalDate.parse(row[6].trim());
            new RecurringAvailabilityRule(teacher, dayOfWeek, minSlot, maxSlot, type,
                    effectiveFrom, effectiveTo, row[7].trim()).persist();
        }
        availabilityExpansionService.expandAll();

        RealSeedFixture.seedRooms();
    }

    // Subject taxonomy from the single source of truth SubjectRegistry / seed/subjects.csv
    // (TASK-437, audit C1) - no hand-kept prefix copy. Same source production uses.
    private static final SubjectRegistry SUBJECTS = loadSubjects();

    private static SubjectRegistry loadSubjects() {
        SubjectRegistry registry = new SubjectRegistry();
        registry.load();
        return registry;
    }

    /**
     * Room tags for a subject, from SubjectRegistry / seed/subjects.csv. Does NOT extend the
     * mapping beyond what production classifies.
     */
    private static List<String> roomTagsFor(String subject) {
        return SUBJECTS.flagsFor(subject).roomTags();
    }

    private void wireStatics() {
        List<Timeslot> timeslots = Timeslot.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<StudentGroup> groups = StudentGroup.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
        List<TeacherAvailability> availabilities = TeacherAvailability.listAll();
        SnapshotFixture.wireConstructionStatics(timeslots, groups, lessons, teachers,
                courses, availabilities, com.school.planner.domain.Room.listAll(),
                List.of(), List.of(), null, false, false);
    }

    @Test
    @Timeout(value = 300)
    @Transactional
    void monoRoomTagPhase_pinsLabBlocksWithRoomAndPrintsVerdict() {
        seedClassifiedFixture();
        wireStatics();

        System.out.println("==== TASK-449 SPIKE 2 mono-room-tag allocator verdict ====");
        System.out.printf("%-18s | %s%n", "tag", "pinned-with-room / skipped / residual / total");
        int phasePinned;
        System.setProperty(HybridPhase1aService.MONO_ROOM_TAG_FLAG, "true");
        try {
            HybridPhase1aReport report = hybridPhase1aService.run();
            phasePinned = report.phaseResults.stream()
                    .filter(p -> "0b-MONO-ROOM-TAG".equals(p.phaseName))
                    .mapToInt(p -> p.entitiesPinned).sum();
        } finally {
            System.clearProperty(HybridPhase1aService.MONO_ROOM_TAG_FLAG);
        }
        System.out.printf("TASK-449 TOTAL pinned-with-room across mono tags: %d%n", phasePinned);

        // Smoke invariant 1: every pinned lesson is complete (pinned + room + timeslot).
        // @PlanningPin makes a pinned null room permanent, so the pin must set it.
        long incomplete = Lesson.<Lesson>find("pinned = true and (room is null or timeslot is null)").count();
        assertTrue(incomplete == 0,
                "every pinned lesson must have a room and timeslot, got " + incomplete + " incomplete");

        // Smoke invariant 2: no two same-tag pinned lessons share (date, slot) on the same room.
        // The mono room hosts one group at a time; a collision would be an H2 violation the pin
        // itself introduced (the phase's canPinHardConstraints should have prevented it).
        assertNoMonoRoomSlotCollision("acconciatura");
        assertNoMonoRoomSlotCollision("termoidraulica");
        assertNoMonoRoomSlotCollision("meccanica");
    }

    /** Asserts no two pinned lessons requiring {@code tag} share the same (date, slot, room). */
    private void assertNoMonoRoomSlotCollision(String tag) {
        List<Lesson> pinned = Lesson.find(
                "SELECT l FROM Lesson l JOIN l.course c WHERE l.pinned = true AND ?1 MEMBER OF c.requiredRoomTags",
                tag).list();
        Map<String, Integer> roomDateSlotCount = new HashMap<>();
        for (Lesson l : pinned) {
            if (l.timeslot == null || l.room == null) {
                continue;
            }
            String key = l.room.id + "@" + l.timeslot.id;
            roomDateSlotCount.merge(key, 1, Integer::sum);
        }
        List<String> collisions = new ArrayList<>();
        for (Map.Entry<String, Integer> e : roomDateSlotCount.entrySet()) {
            if (e.getValue() > 1) {
                collisions.add(e.getKey() + "=" + e.getValue());
            }
        }
        assertTrue(collisions.isEmpty(),
                "tag " + tag + ": mono room must host one group per (date, slot), collisions: " + collisions);
    }
}
