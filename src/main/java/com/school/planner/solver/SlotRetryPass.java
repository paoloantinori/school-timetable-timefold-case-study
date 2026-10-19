package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;

/**
 * TASK-577 9/09: the pre-booking slot retry. The analysis of the 8 remaining
 * H34 cells showed the block-guard F3 stops the entire date on a single
 * conflict in the starting slot, even when later slots are free (June dates
 * with 6-11 groups busy per slot). This pass runs AFTER the main
 * {@code reserveDeclaredDayPlacements} and retries the template cells of
 * (group, date) pairs that have ZERO placed lessons, shifting the starting
 * slot forward (up to {@link #MAX_SHIFT}) to find a conflict-free window.
 *
 * <p>Dry-run: the ENTIRE shifted block must be conflict-free before any
 * placement happens. H17-safe by construction (the cell stays contiguous).
 *
 * <p>Flag: {@code planner.construction.prebooking.slot-retry.enabled},
 * opt-in DEFAULT OFF.
 */
public final class SlotRetryPass {

    static final int MAX_SHIFT = 4;
    static final String FLAG = "planner.construction.prebooking.slot-retry.enabled";

    private SlotRetryPass() {}

    public record Result(int cellsShifted, int datesRecovered) {}

    public static boolean enabled() {
        return Boolean.getBoolean(FLAG);
    }

    @SuppressWarnings("unused")
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(SlotRetryPass.class.getName());

    /**
     * @param template the weekly template (group -> weekday-value -> cells)
     * @param timeslotsByDateSlot date -> slot-array
     * @param unplacedByCourse still-unplaced lessons per course
     * @param legalByCourse VRP range per course
     * @param phase the construction phase (for canPlaceLesson/exceedsDailyCap/
     *        assignVariables/trackAssignment, all package-private)
     */
    public static Result retry(SchoolSchedule schedule,
                               Map<StudentGroup, Map<java.time.DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> template,
                               Map<LocalDate, Timeslot[]> timeslotsByDateSlot,
                               Map<Course, ArrayDeque<Lesson>> unplacedByCourse,
                               Map<Course, Set<Timeslot>> legalByCourse,
                               DayClusteredConstructionPhase phase) {
        if (!enabled() || schedule.getStudentGroups() == null) return new Result(0, 0);

        // Index: (groupId, date) -> placed non-Stage count
        Map<Long, Set<LocalDate>> datesWithLessons = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) continue;
            if (l.course.teacher != null && l.course.teacher.pseudo) continue;
            datesWithLessons
                    .computeIfAbsent(l.course.studentGroup.id, k -> new HashSet<>())
                    .add(l.timeslot.getDate());
        }

        int shifted = 0;
        Set<LocalDate> recovered = new HashSet<>();

        for (var groupEntry : template.entrySet()) {
            StudentGroup group = groupEntry.getKey();
            if (group == null || group.id == null) continue;
            Set<LocalDate> hasLessons = datesWithLessons.getOrDefault(group.id, Set.of());

            for (Timeslot anyTs : schedule.getTimeslots()) {
                LocalDate date = anyTs.getDate();
                if (hasLessons.contains(date)) continue;

                Timeslot[] daySlots = timeslotsByDateSlot.get(date);
                if (daySlots == null || daySlots.length == 0) continue;

                // Quota check: only retry dates with a declared quota
                var row = SchoolSchedule.getExpectedHoursRow(group.id, date);
                if (row == null || row.aulaHours + row.labHours <= 0) continue;

                List<WeeklyTemplateBuilder.TemplateCell> cells =
                        groupEntry.getValue().get(date.getDayOfWeek());
                if (cells == null || cells.isEmpty()) continue;

                boolean dateRecovered = false;
                for (WeeklyTemplateBuilder.TemplateCell cell : cells) {
                    Course course = cell.course();
                    ArrayDeque<Lesson> queue = unplacedByCourse.get(course);
                    Set<Timeslot> legal = legalByCourse.get(course);
                    if (queue == null || queue.isEmpty()) continue;

                    for (int shift = 0; shift <= MAX_SHIFT; shift++) {
                        int start = cell.slotIndex() + shift;
                        int end = start + cell.length();
                        if (end > daySlots.length) break;

                        boolean clear = true;
                        for (int s = start; s < end && clear; s++) {
                            Timeslot ts = daySlots[s];
                            Lesson next = queue.peekFirst();
                            if (next == null || ts == null
                                    || (legal != null && !legal.contains(ts))
                                    || !phase.canPlaceLesson(next, ts)
                                    || phase.exceedsDailyCap(next, ts)) {
                                clear = false;
                            }
                        }
                        if (!clear) continue;

                        for (int s = start; s < end; s++) {
                            Lesson next = queue.pollFirst();
                            if (next == null) break;
                            phase.assignVariables(next, daySlots[s]);
                            phase.trackAssignment(next, daySlots[s]);
                        }
                        shifted++;
                        dateRecovered = true;
                        break;
                    }
                }
                if (dateRecovered) recovered.add(date);
            }
        }
        if (shifted > 0) {
            LOG.info("SlotRetryPass (TASK-577 9/09): " + shifted + " cell(s) shifted, "
                    + recovered.size() + " date(s) recovered from zero");
        }
        return new Result(shifted, recovered.size());
    }
}
