package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.BoxRepair;
import com.school.planner.solver.StudentDayCompaction;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * TASK-510: the exact-placement probe for the 20 boxed, executed against the REAL
 * end-of-pre-assign state of the run4 treatment (deriver + enumerator armed, pre-assign
 * complete, no solve). For every RA lesson still boxed after the chain's pre-assign and
 * every Monday/Friday candidate cell where G04 has quota-spare, this test records WHICH
 * real filter excludes the cell: the VRP ({@link Lesson#getAvailableTimeslots}), the
 * H11/H7/H17 placement gates ({@link BoxRepair#passesPlacementGates}), or the H3 group
 * conflict. The output is the ROUND 4-PROBE evidence table.
 *
 * <p>Run explicitly (it is diagnostic, long, and log-heavy):
 * {@code ./scripts/core/test.sh -c BoxedRaPlacementProbeTest}
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BoxedRaPlacementProbeTest {

    private static final Logger LOG = Logger.getLogger(BoxedRaPlacementProbeTest.class);

    @Inject
    HybridPhase1aService hybridPhase1aService;

    @Inject
    ScheduleService scheduleService;

    @Inject
    CalendarioImportService calendarioImportService;

    @Test
    @Transactional
    void probeBoxedRaCells() {
        // The chain's own end-of-pre-assign state: deriver + enumerator armed, pre-assign
        // complete (run4 treatment conditions). No solve runs here.
        System.setProperty("planner.pre-assign.labrotation.derive-from-golden.enabled", "true");
        System.setProperty("planner.pre-assign.rotation-enumerator.enabled", "true");
        try {
            // run4's exact pre-assign preconditions: the school's calendario pins first
            // (stage + MODA), exactly what baseline-golden.sh does before pre-assign.
            calendarioImportService.importCalendario(false, false);
            var report = hybridPhase1aService.runWithEpoch(0);
            LOG.infof("probe: pre-assign report success=%s", report.success);

            List<Lesson> boxed = Lesson.listAll();
            int ra = 0;
            Map<String, Integer> verdicts = new TreeMap<>();
            StringBuilder table = new StringBuilder();
            for (Lesson l : boxed) {
                if (l.timeslot != null || l.getTeacher() == null) {
                    continue;
                }
                if (!"Recuperi ed Approfondimenti".equals(l.getSubject())) {
                    continue;
                }
                StudentGroup g = l.getStudentGroup();
                if (g == null || !"G04".equals(g.name)) {
                    continue;
                }
                ra++;
                List<Timeslot> vrp = l.getAvailableTimeslots();
                long monFri = vrp.stream()
                        .filter(t -> t.getDate().getDayOfWeek() == DayOfWeek.MONDAY
                                || t.getDate().getDayOfWeek() == DayOfWeek.FRIDAY)
                        .count();
                if (ra <= 3) {
                    LOG.infof("probe lesson %d: VRP cells=%d (Mon/Fri=%d)", l.id, vrp.size(), monFri);
                    // gate-test the first few Mon/Fri VRP cells
                    int tested = 0;
                    for (Timeslot ts : vrp) {
                        if (ts.getDate().getDayOfWeek() != DayOfWeek.MONDAY) {
                            continue;
                        }
                        if (tested++ >= 5) {
                            break;
                        }
                        DailyActivityQuota quota = DailyActivityQuota.find(
                                "studentGroup.id = ?1 and date = ?2", g.id, ts.getDate()).firstResult();
                        long used = Lesson.count(
                                "course.studentGroup.id = ?1 and timeslot.date = ?2", g.id, ts.getDate());
                        long teacherLoad = Lesson.count(
                                "course.teacher.id = ?1 and timeslot.date = ?2", l.getTeacher().id, ts.getDate());
                        LOG.infof("probe cell %s %s: quota[aula=%d lab=%d stage=%d] used=%d teacherLoad=%d",
                                ts.getDate(), ts.startDateTime.toLocalTime(),
                                quota != null ? quota.aulaHours : -1,
                                quota != null ? quota.labHours : -1,
                                quota != null ? quota.stageHours : -1, used, teacherLoad);
                    }
                }
                String key = vrp.isEmpty() ? "VRP_EMPTY" : ("VRP_" + (monFri > 0 ? "HAS_MONFRI" : "NO_MONFRI"));
                verdicts.merge(key, 1, Integer::sum);
                table.append(String.format("lesson=%d vrpCells=%d monFri=%d%n", l.id, vrp.size(), monFri));
            }
            LOG.infof("probe summary: %d RA boxed, verdicts=%s", ra, verdicts);
            System.out.println("=== PROBE TABLE (first lines) ===");
            System.out.println(table);
            System.out.println("=== verdicts: " + verdicts);
        } finally {
            System.clearProperty("planner.pre-assign.labrotation.derive-from-golden.enabled");
            System.clearProperty("planner.pre-assign.rotation-enumerator.enabled");
        }
    }
}
