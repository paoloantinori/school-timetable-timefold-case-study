package com.school.planner.bootstrap;

import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link DataGenerator#generateStageCourses()} (TASK-381 "stage on the
 * grid", maintainer 2026-07-20 authorization) directly against a minimal
 * (StudentGroup-only) fixture - same rationale as {@code
 * DataGeneratorDailyActivityQuotaSeedTest}: {@code incarichi.csv} is fixed on
 * the classpath (no test-injectable variant), so the real data does the work;
 * only the 7 target StudentGroup rows need seeding. Counts independently
 * verified against the raw CSV (7 Stage rows, 2,080h total - see the task's
 * own derivation, cross-checked with {@code grep '^Stage,' incarichi.csv}).
 */
@QuarkusTest
class DataGeneratorStageCoursesSeedTest {

    @Inject
    DataGenerator dataGenerator;

    @Inject
    EntityManager em;

    private static final List<String> STAGE_GROUPS = List.of(
            "G07", "G11", "G04", "G08", "G10", "G12", "G09", "G13");

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
    }

    @Transactional
    void seedStageGroups() {
        for (String name : STAGE_GROUPS) {
            new StudentGroup(name, 20).persist();
        }
    }

    @Test
    @Transactional
    void createsExactlyEightStageCoursesWithOnePseudoTeacher() {
        seedStageGroups();

        dataGenerator.generateStageCourses();

        assertEquals(8, Course.count("teacher.pseudo = true and subject = 'Stage'"));
        assertEquals(1, Teacher.count("name = 'Stage' and pseudo = true"));
    }

    /**
     * TASK-550 cure (a) golden-open density: the stub count per group is the count the
     * golden HELD (lessons.csv Stage rows with closed != true), not the sheet's ask.
     * The difference is declared on expectedUnscheduledHours, pending the school's
     * answer (referente question, cure (c)).
     */
    @Test
    @Transactional
    void createsExactlyGoldenHeldStubLessons() {
        seedStageGroups();

        dataGenerator.generateStageCourses();

        long totalStubs = 0;
        for (Course course : Course.<Course>list("teacher.pseudo = true and subject = 'Stage'")) {
            totalStubs += Lesson.count("course", course);
        }
        // 224+295+276+292+228+287+112+295, independently recounted from lessons.csv
        assertEquals(2009, totalStubs, "sum of the 8 groups' golden-held Stage hours");
        assertEquals(2009, Lesson.count(), "every seeded Lesson this run is a Stage stub (no other course seeded)");
    }

    @Test
    @Transactional
    void perGroupTotalHoursMatchIncarichiCsv() {
        seedStageGroups();

        dataGenerator.generateStageCourses();

        // TASK-550: expected = golden-HELD (closed != true in lessons.csv); unscheduled
        // = the sheet's ask minus held (the phantom demand deferred to the school)
        Map<String, Integer> held = Map.of(
                "G07", 295,
                "G11", 287,
                "G04", 224,
                "G08", 276,
                "G10", 228,
                "G12", 112,
                "G09", 292,
                "G13", 295);
        Map<String, Integer> sheet = Map.of(
                "G07", 295,
                "G11", 295,
                "G04", 240,
                "G08", 300,
                "G10", 300,
                "G12", 295,
                "G09", 300,
                "G13", 295);
        for (Map.Entry<String, Integer> entry : held.entrySet()) {
            String g = entry.getKey();
            Course course = Course.find("teacher.pseudo = true and subject = 'Stage' and studentGroup.name = ?1", g)
                    .firstResult();
            assertNotNull(course, "expected a Stage course for " + g);
            assertEquals(entry.getValue(), course.totalHours, g + " Stage totalHours = golden-held");
            assertEquals(entry.getValue().longValue(), Lesson.count("course", course), g + " Stage stub count = golden-held");
            int deferred = sheet.get(g) - entry.getValue();
            assertEquals(deferred == 0 ? null : deferred, course.expectedUnscheduledHours,
                    g + " phantom hours deferred to expectedUnscheduledHours");
        }
    }

    @Test
    @Transactional
    void stageCoursesAreNotProfessionalizzantiAndHaveNoRoomTags() {
        seedStageGroups();

        dataGenerator.generateStageCourses();

        for (Course course : Course.<Course>list("teacher.pseudo = true and subject = 'Stage'")) {
            assertTrue(!course.professionalizzanti, course.studentGroup.name + "'s Stage course must not be professionalizzanti");
            assertTrue(course.requiredRoomTags.isEmpty(), course.studentGroup.name + "'s Stage course must have no room tags");
        }
    }

    @Test
    @Transactional
    void reusesExistingPseudoTeacherInsteadOfDuplicating() {
        // Mirrors GoldenScheduleImportService's own find-or-create pattern:
        // a pre-existing pseudo "Stage" Teacher (e.g. from a prior partial
        // run) must be reused, not duplicated.
        Teacher existing = new Teacher("Stage");
        existing.pseudo = true;
        existing.persist();
        seedStageGroups();

        dataGenerator.generateStageCourses();

        assertEquals(1, Teacher.count("name = 'Stage' and pseudo = true"), "must reuse the existing pseudo Teacher, not create a second one");
    }

    @Test
    @Transactional
    void unknownGroupThrows() {
        // No StudentGroup seeded at all - unlike generateDailyActivityQuotas
        // (which silently skips unmatched rows), generateStageCourses mirrors
        // generateCourses' fail-loud behavior for an unresolvable incarichi.csv
        // row: a Stage row for a group that doesn't exist in the DB is a data
        // problem worth surfacing immediately, not a routine skip.
        RuntimeException ex = assertThrows(RuntimeException.class, () -> dataGenerator.generateStageCourses());
        assertTrue(ex.getMessage().contains("unknown group"), ex.getMessage());
    }
}
