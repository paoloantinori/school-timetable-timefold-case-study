package com.school.planner.bootstrap;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.PairCellsTemplateBuilder;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.solver.WeeklyTemplateBuilder;
import com.school.planner.solver.WeeklyTemplatePlanner;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-576 F3: the PAIR-CELLS bench. Runs BOTH template builders on the real seed
 * (same input, same constraints) and compares their outputs on the pair-stability
 * axes the golden practices (the prassi referente): does the pair-cells variant produce
 * more stable (teacher, group) schedules than the current builder?
 *
 * <p>Env: {@code PLANNER_TEST_PAIR_CELLS=1} (off by default: this is a bench, not
 * a regression test. The numbers are diagnostic, TREND-only, never adoption).
 *
 * <p>Fixture design lesson (31/08, laptop round 22): the H2 boot runs with DataGenerator
 * DISABLED, so a bench cannot read the population off the boot. It must seed the full
 * real fixture itself, mirroring WeeklyTemplateConstructionRealSeedTest exactly (the
 * same replication Task406RepairTailBoundDiagnosisTest practices). A partial copy
 * (only timeslots/groups/calRules) leaves courses/lessons/quotas empty and both
 * builders legitimately produce an empty template.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PAIR_CELLS", matches = ".+")
class PairCellsTemplateBenchTest {

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
    }

    /** Re-seed the full real fixture. Mirrors WeeklyTemplateConstructionRealSeedTest.seedRealSeedFixture exactly. */
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
            new RecurringAvailabilityRule(teacher, java.time.DayOfWeek.valueOf(row[1].trim()),
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
    void pairCellsBuilderProducesComparablePairs() {
        seedRealSeedFixture();

        List<Timeslot> timeslots = Timeslot.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<Course> courses = Course.listAll();
        List<StagePattern> stagePatterns = StagePattern.listAll();
        List<SchoolHoliday> holidays = SchoolHoliday.listAll(); // mech: typed local (Panache inference)
        Set<LocalDate> holidayDates = holidays.stream()
                .map(h -> h.date).collect(Collectors.toSet());

        assertTrue(lessons.size() > 1000,
                "expected the real incarichi.csv volume, got " + lessons.size());

        // Wire the static VRP snapshots exactly like the RealSeedTest build helper:
        // the template builders read calendar windows, quotas and availabilities
        // through them. Without this wiring every window reads closed.
        List<StudentGroup> allGroups = StudentGroup.listAll(); // mech: typed local (Panache inference)
        for (StudentGroup group : allGroups) {
            group.idealEndDateVrpBuffered = TimeslotSequence.SCHOOL_YEAR_END;
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.<Room>of());
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(new HashMap<>());
        SchoolSchedule.setTeacherAvailabilitiesStatic(TeacherAvailability.listAll());
        SchoolSchedule.setSchoolHolidaysStatic(List.<SchoolHoliday>of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(GroupCalendarRule.listAll());
        SchoolSchedule.setDailyActivityQuotasStatic(DailyActivityQuota.listAll());
        SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);

        System.out.printf("DIAG pre-build: lessons=%d timeslots=%d courses=%d calRules=%d quotas=%d%n",
                lessons.size(), timeslots.size(), courses.size(),
                GroupCalendarRule.listAll().size(), DailyActivityQuota.listAll().size());

        // Act: run the current builder (the production entry point)
        Map<StudentGroup, Map<java.time.DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> current =
                WeeklyTemplatePlanner.build(lessons, timeslots, holidayDates, stagePatterns);

        // Assert: the current builder must produce a non-empty template
        assertFalse(current.isEmpty(), "the current builder must produce a template");

        // Act: run the pair-cells builder (the F2 v1 prototype, greedy mode: no golden forms yet).
        // v1 unit discipline: the weekly demand is annual/activeWeeks, so the pair asks
        // for its true weekly cells (v0 treated annual as weekly and scattered 20 cells).
        java.util.Set<Integer> distinctWeeks = new java.util.HashSet<>(); // mech: explicit types (stream inference failed)
        for (Timeslot t : timeslots) {
            distinctWeeks.add(t.getDate().get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        }
        int activeWeeks = distinctWeeks.size();
        System.out.printf("DIAG activeWeeks=%d (v1 unit: weekly demand = annual/activeWeeks)%n", activeWeeks);
        Map<StudentGroup, Map<java.time.DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> pairCells =
                PairCellsTemplateBuilder.build(
                        courses, null,
                        Set.of(), Set.of(), Set.of(),
                        Map.of(), Map.of(), activeWeeks);

        assertFalse(pairCells.isEmpty(), "the pair-cells builder must produce a template");

        // Act (v2): the same builder FED with the golden pair forms (F1 CSV): the pairs
        // the golden practices get their practiced cells instead of the greedy's.
        // The loader now lives in the builder itself (F4 wiring reuses it in production).
        Map<PairCellsTemplateBuilder.PairKey, PairCellsTemplateBuilder.PairForm> goldenForms =
                PairCellsTemplateBuilder.loadGoldenPairForms(courses);
        System.out.printf("DIAG goldenForms loaded=%d (of %d pairs in the CSV scope)%n",
                goldenForms.size(), courses.size());
        Map<StudentGroup, Map<java.time.DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> pairCellsGolden =
                PairCellsTemplateBuilder.build(
                        courses, goldenForms,
                        Set.of(), Set.of(), Set.of(),
                        Map.of(), Map.of(), activeWeeks);

        assertFalse(pairCellsGolden.isEmpty(), "the pair-cells golden builder must produce a template");

        // Assert: compare pair stability (weekday concentration)
        double currentAvg = avgWeekdayConcentration(current);
        double pairCellsAvg = avgWeekdayConcentration(pairCells);
        double pairCellsGoldenAvg = avgWeekdayConcentration(pairCellsGolden);

        System.out.printf("=== pair-cells bench (TASK-576 F3) ===%n");
        System.out.printf("  current builder:    avg weekday concentration = %.1f%%%n", currentAvg * 100);
        System.out.printf("  pair-cells v1:      avg weekday concentration = %.1f%%%n", pairCellsAvg * 100);
        System.out.printf("  pair-cells v2:      avg weekday concentration = %.1f%%%n", pairCellsGoldenAvg * 100);
        System.out.printf("  improvement v1: %+.1f pp | v2: %+.1f pp%n",
                (pairCellsAvg - currentAvg) * 100, (pairCellsGoldenAvg - currentAvg) * 100);

        // The FIDELITY axis (the task's actual question): how close are each builder's
        // pair cells to the cells the golden PRACTICES, for the pairs the golden covers?
        // Concentration rewards mono-day packing (the v1 greedy over-concentrates past
        // the school's own practice); fidelity measures the prassi referente directly.
        FidelityStats currentFidelity = avgGoldenCellOverlap(current, goldenForms, courses);
        FidelityStats v1Fidelity = avgGoldenCellOverlap(pairCells, goldenForms, courses);
        FidelityStats v2Fidelity = avgGoldenCellOverlap(pairCellsGolden, goldenForms, courses);
        System.out.printf("  golden-cell jaccard:  current %.1f%% | v1 %.1f%% | v2 %.1f%%%n",
                currentFidelity.jaccard() * 100, v1Fidelity.jaccard() * 100, v2Fidelity.jaccard() * 100);
        // precision is the honest scale: the Jaccard denominator (union) grows with the
        // pair's practiced-cell count, so a builder choosing 3 cells against a 6-cell
        // golden form caps at 50% however perfect its choice; precision asks only
        // "of the cells you chose, how many does the school practice?"
        System.out.printf("  golden-cell precision: current %.1f%% | v1 %.1f%% | v2 %.1f%%%n",
                currentFidelity.precision() * 100, v1Fidelity.precision() * 100, v2Fidelity.precision() * 100);
        // the school's own practice, for scale: concentration is NOT "higher is
        // better" here; the golden averages far below what current/v1 produce
        double goldenPractice = avgGoldenConcentration();
        System.out.printf("  golden practice (F1 CSV): avg weekday concentration = %.1f%%%n",
                goldenPractice * 100);

        // The H17 axis (the metric the F4 cycle exposed as MISSING here): internal
        // gaps of each (group, weekday) column of the template. The current builder
        // packs the group's day (one subject after another), so its template is
        // compact by construction; a pair-cells template interleaves PAIR cells on
        // the group's day and shatters it, exactly the 200-holes construction
        // signature the profile predictor rejected on every F4 seed. This is the
        // number a future pair-cells variant (day-coordinated) must bring to ~0
        // BEFORE earning another full cycle.
        int currentHoles = templateGroupHoles(current);
        int v1Holes = templateGroupHoles(pairCells);
        int v2Holes = templateGroupHoles(pairCellsGolden);
        System.out.printf("  template H17 holes: current %d | v1 %d | v2 %d (weekly, all groups)%n",
                currentHoles, v1Holes, v2Holes);
        System.out.printf("  (TREND/diagnosis only, never adoption)%n");

        // Guard (F4 semantics): the CONCENTRATION guard is GONE on purpose. F3 taught
        // that concentration is the wrong axis for this builder: the school's own
        // practice averages ~62% weekday concentration (golden-pair-cells.csv) and the
        // F4 v2 sits at 73%, CLOSER to the practice than current's 84,5%. What the old
        // guard flagged as degradation is the builder moving TOWARD the school. The
        // metric that carries the task's question is PRECISION: v2 must not degrade it
        // against current. (v1 stays unguarded: it is the control arm, and its
        // below-current precision is the F3 finding, not a regression to catch.)
        assertTrue(v2Fidelity.precision() >= currentFidelity.precision() - 0.05,
                "the golden pair-cells builder should not degrade golden-cell precision"
                        + " (current=" + currentFidelity.precision() + ", v2=" + v2Fidelity.precision() + ")");
    }

    /**
     * Internal H17-style gaps of the template's (group, weekday) columns: for every
     * non-empty column, (last slot - first slot + 1) - cells present. The weekly
     * template stamps chronologically, so a gapped column becomes gapped days in the
     * construction, which is the hard-tier hole the F4 cycle died on.
     */
    private static int templateGroupHoles(
            Map<StudentGroup, Map<java.time.DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> template) {
        int holes = 0;
        for (var groupEntry : template.entrySet()) {
            for (var dayEntry : groupEntry.getValue().entrySet()) {
                List<WeeklyTemplateBuilder.TemplateCell> cells = dayEntry.getValue();
                if (cells.isEmpty()) continue;
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                int count = 0;
                for (var cell : cells) {
                    if (cell.course() == null) continue;
                    min = Math.min(min, cell.slotIndex());
                    max = Math.max(max, cell.slotIndex());
                    count++;
                }
                if (count == 0) continue;
                holes += (max - min + 1) - count;
            }
        }
        return holes;
    }

    /** The school's own average weekday concentration per pair, straight from the F1 CSV. */
    private static double avgGoldenConcentration() {
        double sum = 0;
        int count = 0;
        for (String[] row : com.school.planner.util.SeedCsv.load("golden-pair-cells.csv", 8)) {
            String token = row[3].trim();
            if (!token.endsWith("%")) continue;
            sum += Double.parseDouble(token.substring(0, token.length() - 1));
            count++;
        }
        return count == 0 ? 0.0 : sum / count / 100.0;
    }

    /** Both fidelity flavors averaged over the golden-covered pairs. */
    private record FidelityStats(double jaccard, double precision) {
    }

    /**
     * Average fidelity between the template's cells for a golden pair and the pair's
     * practiced golden cells, in two flavors. Jaccard = |intersection| / |union| (the
     * strict overlap, capped low when the golden practices more cells than the weekly
     * template asks for); precision = |intersection| / |template cells| (of the cells
     * the builder chose, how many the school practices). Both measure DIRECTLY how
     * closely the builder reproduces the prassi referente, unlike concentration which
     * rewards mono-day packing for its own sake.
     */
    private static FidelityStats avgGoldenCellOverlap(
            Map<StudentGroup, Map<java.time.DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> template,
            Map<PairCellsTemplateBuilder.PairKey, PairCellsTemplateBuilder.PairForm> goldenForms,
            List<Course> courses) {
        // build teacherId -> name map from courses (PairKey carries ids, template carries names)
        Map<Long, String> teacherNameById = new HashMap<>();
        for (Course c : courses) {
            if (c.teacher != null) {
                teacherNameById.putIfAbsent(c.teacher.id, c.teacher.name);
            }
        }

        // extract template's (day, slot) cells per pair: "teacherName|groupName" -> set of "day:slot"
        Map<String, Set<String>> templateCellsByPair = new HashMap<>();
        for (var groupEntry : template.entrySet()) {
            String groupName = groupEntry.getKey().name;
            for (var dayEntry : groupEntry.getValue().entrySet()) {
                int dayVal = dayEntry.getKey().getValue();
                for (var cell : dayEntry.getValue()) {
                    if (cell.course() == null || cell.course().teacher == null) continue;
                    String teacherName = cell.course().teacher.name;
                    String pairKey = teacherName + "|" + groupName;
                    templateCellsByPair.computeIfAbsent(pairKey, k -> new HashSet<>())
                            .add(dayVal + ":" + cell.slotIndex());
                }
            }
        }

        // compute Jaccard and precision for each golden pair and average
        double sum = 0.0;
        double precisionSum = 0.0;
        int count = 0;
        for (var entry : goldenForms.entrySet()) {
            PairCellsTemplateBuilder.PairKey pair = entry.getKey();
            PairCellsTemplateBuilder.PairForm form = entry.getValue();
            String teacherName = teacherNameById.get(pair.teacherId());
            String groupName = pair.group().name;
            if (teacherName == null) continue;

            // golden cells: decode key back to "day:slot"
            Set<String> goldenSet = new HashSet<>();
            for (Integer key : form.cellKeys()) {
                int day = key / 100;
                int slot = key % 100;
                goldenSet.add(day + ":" + slot);
            }

            String pairKey = teacherName + "|" + groupName;
            Set<String> templateSet = templateCellsByPair.getOrDefault(pairKey, Set.of());

            if (goldenSet.isEmpty() || templateSet.isEmpty()) continue;

            // Jaccard = |intersection| / |union|; precision = |intersection| / |template cells|
            Set<String> intersection = new HashSet<>(goldenSet);
            intersection.retainAll(templateSet);
            Set<String> union = new HashSet<>(goldenSet);
            union.addAll(templateSet);
            double jaccard = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
            double precision = templateSet.isEmpty() ? 0.0 : (double) intersection.size() / templateSet.size();

            sum += jaccard;
            precisionSum += precision;
            count++;
        }
        if (count == 0) return new FidelityStats(0.0, 0.0);
        return new FidelityStats(sum / count, precisionSum / count);
    }

    /** Average weekday concentration across all pairs with >=2 hours in the template. */
    private static double avgWeekdayConcentration(
            Map<StudentGroup, Map<java.time.DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> template) {
        Map<String, Map<java.time.DayOfWeek, Integer>> pairHours = new HashMap<>();
        for (var groupEntry : template.entrySet()) {
            for (var dayEntry : groupEntry.getValue().entrySet()) {
                for (var cell : dayEntry.getValue()) {
                    if (cell.course() == null || cell.course().teacher == null) continue;
                    String pair = cell.course().teacher.name + "|" + groupEntry.getKey().name;
                    pairHours.computeIfAbsent(pair, p -> new HashMap<>())
                            .merge(dayEntry.getKey(), cell.length(), Integer::sum);
                }
            }
        }
        if (pairHours.isEmpty()) return 0.0;
        double sum = 0;
        int count = 0;
        for (var hours : pairHours.values()) {
            int total = hours.values().stream().mapToInt(Integer::intValue).sum();
            if (total < 2) continue;
            int max = hours.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            sum += (double) max / total;
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }
}
