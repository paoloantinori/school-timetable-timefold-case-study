package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.DayClusteredConstructionPhase;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.solver.SchoolScheduleConstraintProvider;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * TASK-542 iter-25 CHECKPOINT measure (mandato maintainer: verdetto in minuti, no full run):
 * construction-only on the canonical real-seed fixture, steering flag ON vs OFF, and the
 * profile features read straight off the delivered construction phase - concentration of
 * the 7 steered teachers on their golden weekday, H17-style holes, boxed, day-start.
 */
@QuarkusTest
class SteeringConstructionCheckpointTest {

    private static final String STEERING_FLAG = "planner.construction.teacher-weekday-steering.enabled";

    private static final Map<String, Integer> MAX_HOURS_PER_DAY = RealSeedFixture.MAX_HOURS_PER_DAY;

    @Inject
    DataGenerator dataGenerator;

    @Inject
    com.school.planner.service.LessonGenerator lessonGenerator;

    @Inject
    com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;

    @Inject
    EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        for (String e : new String[] {"Lesson", "TeacherAvailability", "RecurringAvailabilityRule",
                "GroupCalendarRule", "DailyActivityQuota", "Course", "Teacher", "StudentGroup",
                "Timeslot", "Room"}) {
            em.createQuery("DELETE FROM " + e).executeUpdate();
        }
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
        System.clearProperty(STEERING_FLAG);
        System.setProperty(com.school.planner.service.HybridPhase1aService.CONSTRAINED_TEACHER_FLAG, "false");
    }

    @Test
    @Timeout(value = 400)
    @Transactional
    void steeringCheckpointMeasure() {
        try {
            System.out.println("STEERING-REPORT arm=OFF");
            measureOnce();
            clean();
            System.out.println("STEERING-REPORT arm=ON");
            System.setProperty(STEERING_FLAG, "true");
            measureOnce();
        } finally {
            System.clearProperty(STEERING_FLAG);
        }
    }

    @Transactional
    void measureOnce() {
        seedCanonicalFixture();
        DayClusteredConstructionPhase<SchoolSchedule> phase = solveConstructionOnly();
        report(phase);
    }

    private SchoolSchedule lastSolved;

    private void report(DayClusteredConstructionPhase<SchoolSchedule> phase) {
        // steered teachers' weekday concentration + H17 holes + day-start, off the placed grid
        Map<String, int[]> steeredTarget = new LinkedHashMap<>(); // teacher -> [targetIdx, on, tot]
        for (var row : com.school.planner.solver.TeacherWeekdaySteeringSource.load()) {
            steeredTarget.put(row.teacher(), new int[] {row.weekday().getValue() % 7, 0, 0});
        }
        Map<String, Map<LocalDate, TreeMap<Integer, Lesson>>> dayByGroup = new HashMap<>();
        int holes = 0;
        int days = 0;
        int firstSlotDays = 0;
        for (Lesson l : lastSolved.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.teacher == null) {
                continue;
            }
            LocalDate d = l.timeslot.getDate();
            DayOfWeek wd = d.getDayOfWeek();
            String t = l.course.teacher.name;
            int[] st = steeredTarget.get(t);
            if (st != null) {
                st[2]++;
                if ((wd.getValue() % 7) == st[0]) {
                    st[1]++;
                }
            }
            dayByGroup.computeIfAbsent(l.course.studentGroup == null ? "?" : l.course.studentGroup.name,
                    k -> new HashMap<>()).computeIfAbsent(d, k -> new TreeMap<>())
                    .put(TimeslotSequence.slotIndexOf(l.timeslot), l);
        }
        for (var g : dayByGroup.values()) {
            for (TreeMap<Integer, Lesson> day : g.values()) {
                var s = new java.util.ArrayList<>(day.keySet());
                for (int i = 1; i < s.size(); i++) {
                    if (s.get(i) - s.get(i - 1) > 1) {
                        holes++;
                    }
                }
                days++;
                if (!s.isEmpty() && s.get(0) == 0) {
                    firstSlotDays++;
                }
            }
        }
        long placed = lastSolved.getLessons().stream().filter(l -> l.timeslot != null).count();
        long total = lastSolved.getLessons().size();
        StringBuilder sb = new StringBuilder("STEERING-REPORT steered:");
        for (var e : steeredTarget.entrySet()) {
            int[] st = e.getValue();
            sb.append(String.format(" %s=%d/%d", e.getKey(), st[1], st[2]));
        }
        System.out.println(sb);
        System.out.println("STEERING-REPORT holes=" + holes + " days=" + days
                + " daystart=" + (days == 0 ? 0 : String.format("%.3f", firstSlotDays / (double) days))
                + " placed=" + placed + "/" + total);
        assertTrue(placed > 1000, "construction must place the real volume");
    }

    /** Copy of the canonical fixture + construction-only solve (surgical test pattern). */
    @Transactional
    void seedCanonicalFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers = new LinkedHashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("teachers.csv", 6)) {
            Teacher t = new Teacher(row[0].trim());
            String mdh = row[3].trim();
            t.maxDailyHours = mdh.isEmpty() ? null : Integer.valueOf(mdh);
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

    private DayClusteredConstructionPhase<SchoolSchedule> solveConstructionOnly() {
        List<Timeslot> timeslots = Timeslot.listAll();
        List<StudentGroup> studentGroups = StudentGroup.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<TeacherAvailability> av = TeacherAvailability.listAll();
        for (StudentGroup group : studentGroups) {
            group.idealEndDateVrpBuffered = TimeslotSequence.SCHOOL_YEAR_END;
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(new HashMap<>());
        SchoolSchedule.setTeacherAvailabilitiesStatic(av);
        SchoolSchedule.setSchoolHolidaysStatic(List.<SchoolHoliday>of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), java.util.Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(GroupCalendarRule.listAll());
        SchoolSchedule.setDailyActivityQuotasStatic(DailyActivityQuota.listAll());
        SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);
        SchoolSchedule problem = new SchoolSchedule(timeslots, List.of(), List.of(), av, List.of(),
                studentGroups, teachers, courses, lessons, List.of());
        DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = new DayClusteredConstructionPhase<>();
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(new CustomPhaseConfig()
                        .withCustomPhaseCommandList(List.of(constructionPhase))));
        SolverFactory<SchoolSchedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<SchoolSchedule> solver = solverFactory.buildSolver();
        lastSolved = solver.solve(problem);
        return constructionPhase;
    }
}
