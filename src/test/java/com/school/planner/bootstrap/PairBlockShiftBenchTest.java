package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.PairCellGuard;
import com.school.planner.solver.PairCellsTemplateBuilder;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.solver.WeeklyTemplateBuilder;
import com.school.planner.solver.moves.PairBlockShiftPlanner;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * TASK-576 F8 early-validation bench (the task's pre-cycle gate): replay the ERODED
 * end-state of a real cycle (the E1bis final dump) on the real-seed fixture, then run
 * the pair-block shift GREEDILY (no local search, no score) and measure the golden-cell
 * match before/after plus internal holes before/after.
 *
 * <p>The question is narrow and must be answered BEFORE spending a cycle: does the move
 * even see the glass? If greedy shifts do not move the match on a real eroded state,
 * the LS will not adopt them either and E5 is wasted (the F4 lesson: bench first).</p>
 *
 * <p>Gated like {@link PairCellsTemplateBenchTest} ({@code PLANNER_TEST_PAIR_CELLS}):
 * measures print, asserts are sanity-only (population non-empty, match recomputed).
 * The verdict is read off the printed pair, not asserted here.</p>
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PAIR_CELLS", matches = ".+")
class PairBlockShiftBenchTest {

    /** The eroded end-state to replay (E1bis final dump, pushed with the cycle's capture). */
    private static final Path E1BIS_DUMP = Path.of(
            "claudodocs/spalmone-2026-27/task576-results/f6-diag/e1bis/lessons.txt");

    @Inject
    jakarta.persistence.EntityManager em;
    @Inject
    com.school.planner.service.LessonGenerator lessonGenerator;
    @Inject
    DataGenerator dataGenerator;
    @Inject
    AvailabilityExpansionService availabilityExpansionService;

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
        PairCellGuard.clear();
    }

    /** Mirrors PairCellsTemplateBenchTest.seedRealSeedFixture exactly. */
    @Transactional
    void seedRealSeedFixture() {
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

        DataGenerator.CourseAggregation aggregation =
                DataGenerator.aggregateCourseHours(DataGenerator.loadSeedCsv("incarichi.csv", 4));
        for (Map.Entry<DataGenerator.CourseKey, Integer> entry : aggregation.hoursByKey().entrySet()) {
            DataGenerator.CourseKey key = entry.getKey();
            Teacher teacher = teachers.get(key.teacherName());
            StudentGroup group = groups.get(key.groupName());
            if (teacher == null || group == null) continue;
            Course course = new Course(key.subject(), teacher, group, entry.getValue());
            course.persist();
            lessonGenerator.generateForCourse(course.id);
        }

        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();

        for (String[] row : DataGenerator.loadSeedCsv("availability_rules.csv", 8)) {
            Teacher teacher = teachers.get(row[0].trim());
            if (teacher == null) continue;
            new RecurringAvailabilityRule(teacher, DayOfWeek.valueOf(row[1].trim()),
                    Integer.parseInt(row[2].trim()), Integer.parseInt(row[3].trim()),
                    AvailabilityType.valueOf(row[4].trim()),
                    row[5].isBlank() ? null : LocalDate.parse(row[5].trim()),
                    row[6].isBlank() ? null : LocalDate.parse(row[6].trim()), row[7].trim())
                    .persist();
        }
        availabilityExpansionService.expandAll();
    }

    @Test
    @Timeout(value = 600)
    @Transactional
    void greedyPairShiftsOnTheErodedStateRaiseTheMatch() throws Exception {
        seedRealSeedFixture();

        List<Lesson> population = Lesson.listAll();
        List<Timeslot> timeslots = Timeslot.listAll();
        List<Course> courses = Course.listAll();
        assertTrue(population.size() > 1000, "real-seed population");
        assertTrue(Files.exists(E1BIS_DUMP), "the eroded E1bis dump must be in the repo: " + E1BIS_DUMP);

        // 1. Publish the GOLDEN pair forms to the guard (the F1 CSV, same source the
        // pair builder commits to in production; cellKeys day*100+1-based-slot).
        Map<PairCellsTemplateBuilder.PairKey, PairCellsTemplateBuilder.PairForm> forms =
                PairCellsTemplateBuilder.loadGoldenPairForms(courses);
        Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> template = new HashMap<>();
        for (Map.Entry<PairCellsTemplateBuilder.PairKey, PairCellsTemplateBuilder.PairForm> e : forms.entrySet()) {
            Course anyCourse = courses.stream()
                    .filter(c -> c.teacher != null && e.getKey().teacherId().equals(c.teacher.id)
                            && e.getKey().group().id.equals(c.studentGroup.id))
                    .findFirst().orElse(null);
            if (anyCourse == null) continue;
            Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>> byDay = template
                    .computeIfAbsent(e.getKey().group(), g -> new HashMap<>());
            for (int cellKey : e.getValue().cellKeys()) {
                int day = cellKey / 100;
                int slot1based = cellKey % 100;
                if (day < 1 || day > 5 || slot1based < 1) continue;
                byDay.computeIfAbsent(DayOfWeek.of(day), d -> new ArrayList<>())
                        .add(new WeeklyTemplateBuilder.TemplateCell(anyCourse, slot1based - 1, 1));
            }
        }
        PairCellGuard.publish(template);
        System.out.printf("F8-BENCH guard published: %d pairs, %d cells%n",
                PairCellGuard.publishedPairCount(), PairCellGuard.publishedCellCount());

        // 2. Replay the eroded end-state: (group|date|hour|subject|teacher) -> timeslot.
        Map<String, Timeslot> tsByDateHour = new HashMap<>();
        for (Timeslot ts : timeslots) {
            tsByDateHour.put(ts.getDate() + "@" + TimeslotSequence.slotIndexOf(ts), ts);
        }
        Map<String, List<Lesson>> unplacedByCourseKey = population.stream()
                .filter(l -> l.timeslot == null)
                .collect(Collectors.groupingBy(l -> courseKey(l)));
        int applied = 0;
        int unmatched = 0;
        for (String line : Files.readAllLines(E1BIS_DUMP)) {
            String[] p = line.split("\\|");
            if (p.length < 5 || p[0].isBlank() || p[0].equals("SET")) continue;
            String key = p[0].trim() + "|" + p[3].trim() + "|" + p[4].trim();
            Timeslot ts = tsByDateHour.get(p[1].trim() + "@" + (Integer.parseInt(p[2].trim()) - 8));
            List<Lesson> candidates = unplacedByCourseKey.get(key);
            if (ts == null || candidates == null || candidates.isEmpty()) {
                unmatched++;
                continue;
            }
            candidates.remove(candidates.size() - 1).timeslot = ts;
            applied++;
        }
        System.out.printf("F8-BENCH eroded replay: %d lessons placed, %d dump rows unmatched%n", applied, unmatched);
        assertTrue(applied > population.size() / 2, "the replay must cover most of the population");

        // 3. Match and holes BEFORE.
        int[] before = measure(population, timeslots);

        // 4. Greedy pair-block shifts, deterministic order, conflict-checked landings.
        Map<LocalDate, Map<Integer, Timeslot>> grid = PairBlockShiftPlanner.gridOf(timeslots);
        int moves = 0;
        for (Lesson seed : new ArrayList<>(population)) {
            if (!PairBlockShiftPlanner.isCandidate(seed)) continue;
            List<Lesson> block = PairBlockShiftPlanner.blockOf(seed, population);
            List<Timeslot> targets = PairBlockShiftPlanner.targetsOf(seed, block,
                    PairCellGuard.formCellsOf(seed), grid);
            if (targets == null || !landingsAreFree(block, targets, population)) continue;
            for (int i = 0; i < block.size(); i++) {
                block.get(i).timeslot = targets.get(i);
            }
            moves++;
        }

        // 5. Match and holes AFTER: the bench's verdict pair.
        int[] after = measure(population, timeslots);
        System.out.printf("F8-BENCH RESULT: moves=%d | match %d/%d = %.1f%% -> %d/%d = %.1f%% | internal holes %d -> %d%n",
                moves,
                before[0], before[1], 100.0 * before[0] / Math.max(1, before[1]),
                after[0], after[1], 100.0 * after[0] / Math.max(1, after[1]),
                before[2], after[2]);
        assertTrue(after[1] > 0, "the form population must be non-empty for the bench to mean anything");
    }

    /** [inFormLessons, formPopulationLessons, internalHoleSlots] on the current placement. */
    private static int[] measure(List<Lesson> population, List<Timeslot> timeslots) {
        int inForm = 0;
        int inPopulation = 0;
        for (Lesson lesson : population) {
            if (!PairCellGuard.hasForm(lesson) || lesson.timeslot == null) continue;
            inPopulation++;
            if (PairCellGuard.protects(lesson)) inForm++;
        }
        // H17-like internal holes per (group, date): slots between the day's min and max
        // that no lesson of the group uses.
        Map<String, TreeSet<Integer>> usedByGroupDate = new HashMap<>();
        for (Lesson lesson : population) {
            if (lesson.timeslot == null) continue;
            usedByGroupDate
                    .computeIfAbsent(lesson.getStudentGroup().id + "@" + lesson.timeslot.getDate(),
                            k -> new TreeSet<>())
                    .add(TimeslotSequence.slotIndexOf(lesson.timeslot));
        }
        int holes = 0;
        for (TreeSet<Integer> used : usedByGroupDate.values()) {
            if (used.size() < 2) continue;
            holes += used.last() - used.first() + 1 - used.size();
        }
        return new int[] { inForm, inPopulation, holes };
    }

    /** The landing cells must be free for the group (H3) and the teacher (H1). */
    private static boolean landingsAreFree(List<Lesson> block, List<Timeslot> targets, List<Lesson> population) {
        Map<String, List<Lesson>> occupants = new HashMap<>();
        for (Lesson l : population) {
            if (l.timeslot == null) continue;
            occupants.computeIfAbsent(l.timeslot.getDate() + "@" + TimeslotSequence.slotIndexOf(l.timeslot),
                    k -> new ArrayList<>()).add(l);
        }
        var movers = new java.util.HashSet<>(block);
        for (int i = 0; i < block.size(); i++) {
            Timeslot target = targets.get(i);
            int slot = TimeslotSequence.slotIndexOf(target);
            for (Lesson occupant : occupants.getOrDefault(target.getDate() + "@" + slot, List.of())) {
                if (movers.contains(occupant)) continue;
                if (occupant.getStudentGroup().id.equals(block.get(i).getStudentGroup().id)) return false;
                if (occupant.getTeacher() != null && block.get(i).getTeacher() != null
                        && occupant.getTeacher().id.equals(block.get(i).getTeacher().id)) return false;
            }
        }
        return true;
    }

    private static String courseKey(Lesson l) {
        return l.getStudentGroup().name + "|" + l.course.subject + "|" + l.course.teacher.name;
    }
}
