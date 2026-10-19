package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.school.planner.bootstrap.RealSeedFixture;
import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.solver.WeeklyTemplateBuilder.TemplateCell;
import com.school.planner.solver.WeeklyTemplatePlanner;

import io.quarkus.test.junit.QuarkusTest;

/**
 * TASK-480 independence gate (research_multistart-vs-alternative_2026-08-02.md, finding 4a).
 *
 * <p>Gated ({@code PLANNER_TEST_GATE=1}) so it never runs in the normal suite: it seeds the full
 * real fixture (~minutes) to answer ONE question with ZERO solves: does GRASP-style randomization
 * of the construction tie-breaks DIVERSIFY the @10 occupancy profile (Wednesday slot 2, the
 * G11 hole slot) across N=5 independent templates, or is the cell CONSTRAINT-FORCED
 * (tie-break-independent)?
 *
 * <p><b>Why template-only (no solver, no LS):</b> the construction's repair-tail can only ADD @10
 * lessons (it fills free cells), never remove a template @10 lesson, so a saturated template @10
 * stays saturated in the full construction. The template @10 profile is therefore the SUFFICIENT
 * and cheapest gate signal. Built via {@link WeeklyTemplatePlanner#buildReference} on the real
 * INPUT fixture (incarichi.csv), which is the forward-looking solo-input representation; golden
 * pins would only ADD constraints (force the cell harder, never diversify it), so input-only is
 * the conservative test for the STOP verdict.
 *
 * <p><b>Verdict (read off the printed raw, not asserted):</b>
 * <ul>
 *   <li>Any group's @10 teacher changing across the 5 seeds, OR the @10 teacher-multiset changing
 *       => the cell is diversifiable => multi-start has distinct basins to select from => PROCEED
 *       to the N-process A/B (TASK-480 Fase 3).</li>
 *   <li>5 byte-identical profiles => the @10 cell is constraint-forced (tie-break-independent) =>
 *       N runs re-enter the same basin => multi-start ARCHIVED with evidence, TASK-482 Kempe-chain
 *       (the canonical connectivity cure for a saturated slot).</li>
 * </ul>
 * The only hard assertion is reproducibility: two GRASP-off builds must be byte-identical (the
 * deterministic default is untouched), so a non-diversifying verdict cannot be a GRASP bug.
 */
@QuarkusTest
class ConstructionIndependenceGateTest {

    private static final int N = 5;
    /** TimeslotSequence slot 2 = 10:00-11:00 (the G11 Wednesday hole slot). */
    private static final int AT_TEN_SLOT = 2;
    private static final DayOfWeek HOLE_DAY = DayOfWeek.WEDNESDAY;
    private static final String GRASP_FLAG = "planner.construction.grasp.enabled";
    private static final String GRASP_SEED = "planner.construction.grasp.seed";

    @Inject
    com.school.planner.bootstrap.DataGenerator dataGenerator;
    @Inject
    com.school.planner.service.LessonGenerator lessonGenerator;
    @Inject
    com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;
    @Inject
    EntityManager em;

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
        System.clearProperty(GRASP_FLAG);
        System.clearProperty(GRASP_SEED);
        // Construction is measured alone: the constrained-teacher pin is a pre-assignment input,
        // not a construction property (same isolation as WeeklyTemplateConstructionRealSeedTest).
        System.setProperty(com.school.planner.service.HybridPhase1aService.CONSTRAINED_TEACHER_FLAG, "false");
    }

    @Test
    @Timeout(value = 600)
    void wednesdayAtTenOccupancyDiversifiesUnderGrasp() {
        assumeTrue("1".equals(System.getenv("PLANNER_TEST_GATE")),
                "gated: set PLANNER_TEST_GATE=1 to run the TASK-480 independence gate (seeds the real fixture)");

        seedRealSeedFixture();

        List<Timeslot> timeslots = Timeslot.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<TeacherAvailability> teacherAvailabilities = TeacherAvailability.listAll();
        assertTrueRealSeedVolume(lessons);

        // DayClusteredConstructionPhase.getIdealEndDate() reads this @Transient field directly and
        // falls back to a pre-school-year default when null, cutting off the repair tail.
        for (StudentGroup group : StudentGroup.<StudentGroup>listAll()) {
            group.idealEndDateVrpBuffered = TimeslotSequence.SCHOOL_YEAR_END;
        }

        // Wire the static VRP snapshots exactly like ScheduleService.refreshSolverStatics() and
        // WeeklyTemplateConstructionRealSeedTest.buildAndSolveConstruction (rooms empty: the gate
        // measures the timeslot/template @10 profile, not the room-aware feature).
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(new HashMap<>());
        SchoolSchedule.setTeacherAvailabilitiesStatic(teacherAvailabilities);
        SchoolSchedule.setSchoolHolidaysStatic(List.<com.school.planner.domain.SchoolHoliday>of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), java.util.Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(GroupCalendarRule.listAll());
        SchoolSchedule.setDailyActivityQuotasStatic(DailyActivityQuota.listAll());
        SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);

        // Reproducibility sanity: two GRASP-off builds must be byte-identical (the deterministic
        // default is untouched by the GRASP knob). A non-diversifying verdict must not be a bug.
        System.clearProperty(GRASP_FLAG);
        Map<String, String> baseline1 = wedAtTenProfile(timeslots, lessons);
        Map<String, String> baseline2 = wedAtTenProfile(timeslots, lessons);
        assertEquals(baseline1, baseline2, "GRASP-off baseline must be reproducible (deterministic default untouched)");

        System.out.println("=== TASK-480 INDEPENDENCE GATE: Wednesday @10 (slot " + AT_TEN_SLOT + ") occupancy ===");
        System.out.println("BASELINE (GRASP off): " + render(baseline1));

        // N GRASP seeds.
        List<Map<String, String>> profiles = new ArrayList<>();
        System.setProperty(GRASP_FLAG, "true");
        try {
            for (int seed = 1; seed <= N; seed++) {
                System.setProperty(GRASP_SEED, String.valueOf(seed));
                Map<String, String> profile = wedAtTenProfile(timeslots, lessons);
                profiles.add(profile);
                System.out.println("GRASP seed=" + seed + " : " + render(profile));
            }
        } finally {
            System.clearProperty(GRASP_FLAG);
            System.clearProperty(GRASP_SEED);
        }

        // ── Diversification analysis ──
        Set<String> allGroups = new TreeSet<>(baseline1.keySet());
        for (Map<String, String> p : profiles) allGroups.addAll(p.keySet());

        Map<String, Set<String>> groupsThatVary = new TreeMap<>();
        for (String g : allGroups) {
            Set<String> vals = new TreeSet<>();
            for (Map<String, String> p : profiles) vals.add(String.valueOf(p.get(g)));
            if (vals.size() > 1) groupsThatVary.put(g, vals);
        }

        // The @10 teacher-multiset across groups, per seed: the saturation signature. Identical
        // across seeds = same teachers booked @10 = same LS basin (the multi-start-killer).
        List<String> multisets = new ArrayList<>();
        for (Map<String, String> p : profiles) multisets.add(teacherMultiset(p));
        Set<String> distinctMultisets = new TreeSet<>(multisets);

        // How many groups actually occupy @10 on Wednesday (saturation breadth), per seed.
        System.out.println("--- @10 teacher-multiset (Wed) per seed ---");
        for (int i = 0; i < profiles.size(); i++) {
            long occupied = profiles.get(i).values().stream().filter(v -> !"null".equals(v)).count();
            System.out.printf("seed=%d : %d groups occupy @10 ; teachers=%s%n", i + 1, occupied, multisets.get(i));
        }

        System.out.println("--- groups whose @10 placement VARIES across the 5 GRASP seeds ---");
        if (groupsThatVary.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (var e : groupsThatVary.entrySet()) {
                System.out.println("  " + e.getKey() + " -> " + e.getValue());
            }
        }

        System.out.printf("--- SUMMARY --- distinct teacher-multisets=%d/%d ; groups varying=%d/%d%n",
                distinctMultisets.size(), N, groupsThatVary.size(), allGroups.size());

        boolean diversified = distinctMultisets.size() > 1 || !groupsThatVary.isEmpty();
        System.out.println(diversified
                ? "VERDICT: DIVERSIFIED (@10 cell is tie-break-sensitive) -> PROCEED to N-process multi-start A/B (TASK-480 Fase 3)"
                : "VERDICT: CONSTRAINT-FORCED (@10 cell is tie-break-independent, 5 identical profiles) -> "
                        + "multi-start ARCHIVED, TASK-482 Kempe-chain (connectivity cure for the saturated slot)");
        // The gate's verdict is machine-checked, not just printed: if a future change makes the @10
        // cell tie-break-independent (5 identical profiles), this fails and points at TASK-482.
        assertTrue(diversified,
                "TASK-480 independence gate FAILED: @10 cell is tie-break-independent (distinct multisets="
                        + distinctMultisets.size() + "/" + N + ", groups varying=" + groupsThatVary.size()
                        + ") -> multi-start archived, open TASK-482 Kempe-chain");
    }

    /** One group's @10 placement on the hole weekday, as "teacher/subject" (or null = no @10 cell = a gap). */
    private Map<String, String> wedAtTenProfile(List<Timeslot> timeslots, List<Lesson> lessons) {
        Set<LocalDate> holidays = SchoolSchedule.getSchoolHolidaysStatic();
        List<StagePattern> stagePatterns = StagePattern.listAll();
        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> template =
                WeeklyTemplatePlanner.buildReference(lessons, timeslots, holidays, stagePatterns);
        Map<String, String> profile = new TreeMap<>();
        for (var e : template.entrySet()) {
            StudentGroup g = e.getKey();
            List<TemplateCell> dayCells = e.getValue().getOrDefault(HOLE_DAY, List.of());
            String at = null;
            for (TemplateCell c : dayCells) {
                if (c.slotIndex() <= AT_TEN_SLOT && AT_TEN_SLOT < c.slotIndex() + c.length()) {
                    at = c.course().teacher.name + "/" + c.course().subject;
                    break;
                }
            }
            profile.put(g.name, at);
        }
        return profile;
    }

    private static String teacherMultiset(Map<String, String> profile) {
        // Only the teacher half of "teacher/subject": the saturation signal is WHO is booked @10.
        Map<String, Integer> counts = new TreeMap<>();
        for (String v : profile.values()) {
            if (v == null || "null".equals(v)) continue;
            String teacher = v.contains("/") ? v.substring(0, v.indexOf('/')) : v;
            counts.merge(teacher, 1, Integer::sum);
        }
        return counts.toString();
    }

    private static String render(Map<String, String> profile) {
        return profile.entrySet().stream()
                .map(e -> e.getKey() + "=" + (e.getValue() == null ? "<gap>" : e.getValue()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("(empty)");
    }

    private void assertTrueRealSeedVolume(List<Lesson> lessons) {
        if (lessons.size() <= 1000) {
            throw new IllegalStateException("expected the real incarichi.csv volume, got " + lessons.size());
        }
    }

    @Transactional
    Map<String, StudentGroup> seedRealSeedFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, MAX_HOURS_PER_DAY);

        // Teachers + availability_rules + expandAll are the shared RealSeedFixture helper (the same
        // one WeeklyTemplateConstructionRealSeedTest uses), not a third hand-copy of the block.
        Map<String, Teacher> teachers =
                RealSeedFixture.seedTeachersAndAvailabilityRules(availabilityExpansionService);

        com.school.planner.bootstrap.DataGenerator.CourseAggregation aggregation =
                com.school.planner.bootstrap.DataGenerator.aggregateCourseHours(
                        com.school.planner.bootstrap.DataGenerator.loadSeedCsv("incarichi.csv", 4));
        for (var entry : aggregation.hoursByKey().entrySet()) {
            com.school.planner.bootstrap.DataGenerator.CourseKey key = entry.getKey();
            Teacher teacher = teachers.get(key.teacherName());
            StudentGroup group = groups.get(key.groupName());
            if (teacher == null || group == null) continue;
            Course course = new Course(key.subject(), teacher, group, entry.getValue());
            course.persist();
            lessonGenerator.generateForCourse(course.id);
        }

        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();

        return groups;
    }
}
