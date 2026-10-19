package com.school.planner.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.StagedPinPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-419 stage-C harness (localized, offline): on the PROMOTED state of the E1 spike -
 * the entry-of-phase-2 grid of val408-run1 with every on-template lesson frozen - inject a
 * DEADLOCKED boxed lesson and check that the selective degrade releases the LEAST-CONSTRAINED
 * promoted pins first (ascending M32 weight = descending VRP room for the pin's own course),
 * within its per-lesson budget.
 *
 * <p><b>Why a synthetic boxed lesson.</b> E1 itself measured hypothesis A on this trajectory:
 * after the promotion the LS left ZERO deadlocked residual, so the real snapshot offers stage
 * C no work to do (that is the good news; stage C is the insurance, not the plan). To exercise
 * the selector at all, this harness builds the deadlock explicitly and honestly: a new course
 * for a REAL group, taught by a synthetic teacher whose availability is blocked on every date
 * but one, placed on a date where that group's day is already FULL of promoted pins. Nothing
 * about the snapshot's own grid is faked - the blockers, their courses and their M32 weights
 * are the run's real ones.
 *
 * <p><b>Promotion-blind view</b> (the same contract {@code ScheduleService#runDemotionStage}
 * honours): the pinned-timeslot map is republished from the AUTHORITATIVE pins only for the
 * duration of the analysis, otherwise the H3 value-range filter hides exactly the promoted
 * cells the question is about.
 *
 * <p>TREND/diagnosis only, never adoption (TASK-401): a snapshot fixes one trajectory.
 * Gated OFF via {@code PLANNER_TEST_419_STAGE_C} (env var, not {@code -D}).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_419_STAGE_C", matches = ".+")
class Task419StageCDemotionHarnessTest {

    private static final Path PH2_ENTRY_DUMP = Path.of("claudedocs", "spalmone-2026-27",
            "task408-results", "val408-run1", "phase-transitions", "phase-2-2-ls-compactness.txt");

    /** Quota groups are skipped when choosing the victim day: their VRP is additionally
     *  restricted by the FC sheet, which would muddy "the day is full" as the only cause. */
    private static final Set<String> QUOTA_GROUPS = Set.of("G04", "G08", "G13");

    private static final long SYNTHETIC_ID_BASE = 900_000L;

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    @Transactional
    void demotesTheLeastConstrainedPromotedBlockersOfADeadlockedBoxedLesson() throws Exception {
        Path workDir = Path.of("target", "task419-stagec");
        Files.createDirectories(workDir);
        Files.copy(PH2_ENTRY_DUMP, workDir.resolve("lessons.txt"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(workDir, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);

        // ── stage 1: promote, exactly as ScheduleService does (both flags + the registry) ──
        List<Long> promotedIds = new ArrayList<>();
        for (Lesson lesson : StagedPinPolicy.selectPromotions(snap.lessons)) {
            lesson.pinned = true;
            lesson.promotedPin = true;
            promotedIds.add(lesson.id);
        }
        SchoolSchedule.setPromotedLessonIdsStatic(promotedIds);
        System.out.println("=== TASK-419 stage-C harness (promoted state of the E1 spike) ===");
        System.out.printf("  promoted %d lesson(s) of %d placed%n", promotedIds.size(),
                snap.lessons.stream().filter(l -> l.timeslot != null).count());

        // ── promotion-blind pinned map, for the whole analysis (restored in the finally) ──
        List<Lesson> authoritativeOnly = snap.lessons.stream().filter(l -> !l.promotedPin).toList();
        try {
            SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(authoritativeOnly);

            Deadlock deadlock = buildDeadlockedBoxedLesson(snap);
            assertTrue(deadlock != null,
                    "no fully-promoted day found to build the deadlock on - the harness cannot make its claim");

            List<Lesson> population = new ArrayList<>(snap.lessons);
            assertEquals(0, population.stream().filter(l -> !l.pinned && l.timeslot == null).count(),
                    "this dump carries no box of its own, so the injected lesson is the only boxed one "
                            + "and the budget assertion below is about it alone");
            population.add(deadlock.boxed());
            List<Lesson> demoted =
                    StagedPinPolicy.selectDemotions(population, StagedPinPolicy.MAX_DEMOTIONS_PER_BOXED_LESSON);

            System.out.printf("  deadlock: group=%s date=%s  candidates=%d (weights %d..%d)%n",
                    deadlock.group().name, deadlock.date(), deadlock.blockers().size(),
                    deadlock.blockers().get(0).weight(),
                    deadlock.blockers().get(deadlock.blockers().size() - 1).weight());
            for (Lesson l : demoted) {
                System.out.printf("    DEMOTE %-28s %-22s weight=%d%n", l.getSubject(),
                        l.getTeacher() != null ? l.getTeacher().name : "?",
                        StagedPinPolicy.deviationWeightOf(l));
            }

            assertEquals(StagedPinPolicy.MAX_DEMOTIONS_PER_BOXED_LESSON, demoted.size(),
                    "the per-boxed-lesson budget is the cap, and this deadlock has more blockers than that");
            int maxChosen = demoted.stream().mapToInt(StagedPinPolicy::deviationWeightOf).max().orElseThrow();
            int minRejected = deadlock.blockers().stream()
                    .filter(b -> !demoted.contains(b.lesson()))
                    .mapToInt(Blocker::weight).min().orElseThrow();
            assertTrue(maxChosen <= minRejected,
                    "least-constrained first: chosen weights (max " + maxChosen
                            + ") must not exceed any rejected blocker's (min " + minRejected + ")");
            assertTrue(deadlock.blockers().get(deadlock.blockers().size() - 1).weight()
                            > deadlock.blockers().get(0).weight(),
                    "the day must carry blockers of DIFFERENT M32 weight, else the ordering claim is vacuous");
            for (Lesson l : demoted) {
                assertTrue(l.promotedPin, "only pins the cycle created itself are ever released");
            }
            System.out.println("  (TREND/diagnosis only; NEVER adoption. laptop A/B x2 gate.)");
        } finally {
            SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(snap.lessons);
        }
    }

    /** One promoted occupant of the victim day, with its course's M32 deviation weight. */
    private record Blocker(Lesson lesson, int weight) {
    }

    /** The built scenario: the synthetic boxed lesson and the promoted pins that deadlock it. */
    private record Deadlock(StudentGroup group, LocalDate date, Lesson boxed, List<Blocker> blockers) {
    }

    /**
     * Find a (group, date) whose every open slot is held by promoted pins of at least two
     * differently-weighted courses, and build a boxed lesson that can ONLY go there: a
     * synthetic teacher blocked (VACATION) on every other date of the year. Returns null if
     * no such day exists in the snapshot.
     */
    private Deadlock buildDeadlockedBoxedLesson(SnapshotFixture.Snapshot snap) {
        Map<String, List<Lesson>> byGroupDate = new HashMap<>();
        for (Lesson lesson : snap.lessons) {
            if (lesson.timeslot == null || lesson.getStudentGroup() == null) continue;
            if (QUOTA_GROUPS.contains(lesson.getStudentGroup().name)) continue;
            byGroupDate.computeIfAbsent(lesson.getStudentGroup().id + "@" + lesson.timeslot.getDate(),
                    k -> new ArrayList<>()).add(lesson);
        }
        // Fullest days first: the more of the group's day is frozen, the likelier the deadlock.
        List<List<Lesson>> candidates = new ArrayList<>(byGroupDate.values());
        candidates.sort(Comparator.comparingInt((List<Lesson> day) -> day.size()).reversed());

        List<TeacherAvailability> baseAvailability = TeacherAvailability.listAll();
        int probed = 0;
        for (List<Lesson> day : candidates) {
            if (probed++ >= 40) break;
            StudentGroup group = day.get(0).getStudentGroup();
            LocalDate date = day.get(0).timeslot.getDate();
            Lesson boxed = syntheticBoxedLesson(group, date, baseAvailability, probed);
            List<Timeslot> valueRange = boxed.getAvailableTimeslots();
            if (valueRange.isEmpty() || !valueRange.stream().allMatch(ts -> occupied(ts, day))) {
                continue;  // still has a free cell: not a deadlock, nothing for stage C to do
            }
            // The candidate set the policy will see: promoted occupants OF THE VALUE RANGE.
            List<Blocker> blockers = new ArrayList<>();
            Set<Integer> weights = new HashSet<>();
            for (Lesson lesson : day) {
                if (!lesson.promotedPin || !valueRange.contains(lesson.timeslot)) continue;
                int weight = StagedPinPolicy.deviationWeightOf(lesson);
                blockers.add(new Blocker(lesson, weight));
                weights.add(weight);
            }
            if (blockers.size() < 3 || weights.size() < 2) continue;
            blockers.sort(Comparator.comparingInt(Blocker::weight).thenComparing(b -> b.lesson().id));
            return new Deadlock(group, date, boxed, blockers);
        }
        return null;
    }

    /** True if some lesson of the victim day sits at {@code ts} (so the cell is not free). */
    private static boolean occupied(Timeslot ts, List<Lesson> day) {
        for (Lesson lesson : day) {
            if (ts.equals(lesson.timeslot)) return true;
        }
        return false;
    }

    /**
     * A boxed lesson for {@code group} whose only possible date is {@code date}: a synthetic
     * real (non-pseudo) teacher with VACATION covering the whole school year except that day.
     * The availability statics are republished with these rows appended, never replacing the
     * snapshot's own.
     */
    private Lesson syntheticBoxedLesson(StudentGroup group, LocalDate date,
                                        List<TeacherAvailability> baseAvailability, int seq) {
        Teacher teacher = new Teacher("Stage-C probe " + seq);
        teacher.id = SYNTHETIC_ID_BASE + seq * 3L;
        Course course = new Course("Stage-C probe subject", teacher, group, 1);
        course.id = SYNTHETIC_ID_BASE + seq * 3L + 1;
        Lesson boxed = new Lesson(course);
        boxed.id = SYNTHETIC_ID_BASE + seq * 3L + 2;

        List<TeacherAvailability> availability = new ArrayList<>(baseAvailability);
        availability.add(new TeacherAvailability(teacher, LocalDateTime.of(2000, 1, 1, 0, 0),
                date.atStartOfDay(), AvailabilityType.VACATION));
        availability.add(new TeacherAvailability(teacher, date.plusDays(1).atStartOfDay(),
                LocalDateTime.of(2100, 1, 1, 0, 0), AvailabilityType.VACATION));
        SchoolSchedule.setTeacherAvailabilitiesStatic(availability);
        return boxed;
    }
}
