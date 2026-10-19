package com.school.planner.bootstrap;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.service.ScheduleService;
import com.school.planner.solver.TimeslotSequence;

/** TASK-631 premise probe (micro-mandate 20/09): G13's Thursdays 2027-06-03/10/17
 * at POST_CONSTRUCTION. Prints each checkpoint's G13 rows (slot, subject, teacher)
 * so the contiguity question reads at a glance. Gated -Dg13.probe=<path>[,<path>]. */
@QuarkusTest
class G13ThursdayProbeTest {

    @Inject ScheduleService scheduleService;
    @Inject DataGenerator dataGenerator;
    @Inject EntityManager em;
    @Inject UserTransaction userTransaction;
    @Inject com.school.planner.service.LessonGenerator lessonGenerator;
    @Inject com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;

    @BeforeEach
    @AfterEach
    @jakarta.transaction.Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    void probesG13ThursdaysAtPostConstruction() throws Exception {
        String which = System.getProperty("g13.probe");
        Assumptions.assumeTrue(which != null && !which.isBlank(), "-Dg13.probe=<ckpt>[,<ckpt>]");
        seedLiveOrderFixture();
        for (String ck : which.split(",")) {
            userTransaction.begin();
            try {
                scheduleService.applyStagedCheckpoint(Path.of(ck.trim()));
            } finally {
                userTransaction.commit();
            }
            SchoolSchedule problem = scheduleService.getFullScheduleForExport();
            for (LocalDate d : List.of(LocalDate.of(2027, 6, 3), LocalDate.of(2027, 6, 10),
                    LocalDate.of(2027, 6, 17))) {
                System.out.printf("[G13-PROBE] %s %s:%n", ck, d);
                problem.getLessons().stream()
                        .filter(l -> l.timeslot != null && l.getStudentGroup() != null
                                && "G13".equals(l.getStudentGroup().name) && d.equals(l.timeslot.getDate()))
                        .sorted(java.util.Comparator.comparingInt(l -> TimeslotSequence.slotIndexOf(l.timeslot)))
                        .forEach(l -> System.out.printf("[G13-PROBE]   slot %d wall %d  %-30s %s%n",
                                TimeslotSequence.slotIndexOf(l.timeslot),
                                l.timeslot.startDateTime.getHour(),
                                l.course == null ? "?" : l.course.subject,
                                l.getTeacher() == null ? "?" : l.getTeacher().name));
            }
            clean();
            seedLiveOrderFixture();
        }
    }

    @jakarta.transaction.Transactional
    void seedLiveOrderFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers =
                RealSeedFixture.seedTeachersAndAvailabilityRules(availabilityExpansionService, true);
        RealSeedFixture.seedRooms();
        com.school.planner.service.SubjectRegistry registry = new com.school.planner.service.SubjectRegistry();
        registry.load();
        SnapshotFixture.seedCoursesFromIncarichi(teachers, groups, lessonGenerator, registry);
        dataGenerator.generateStageCourses();
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
        org.junit.jupiter.api.Assertions.assertTrue(Room.count() > 0, "rooms seeded");
    }
}
