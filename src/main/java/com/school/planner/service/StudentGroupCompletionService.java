package com.school.planner.service;

import com.school.planner.domain.*;
import com.school.planner.rest.dto.SlotUtilization;
import com.school.planner.solver.TimeslotSequence;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.jboss.logging.Logger;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service to analyze per-student-group completion statistics.
 * Calculates actual vs ideal end dates, hour distribution, and compaction metrics.
 *
 * "Scheduled" throughout this class means a lesson with a non-null
 * timeslot (see calculateHoursDistribution, calculateActualEndDate and
 * calculateSlotUtilizationForAllGroups below) - the same definition
 * ReconciliationService.fetchScheduledCounts uses for its per-course
 * "Ore Prg." figure, computed independently at a different granularity.
 * If "scheduled" ever changes meaning, update both.
 */
@ApplicationScoped
public class StudentGroupCompletionService {

    @Inject
    EntityManager em;

    private static final Logger LOG = Logger.getLogger(StudentGroupCompletionService.class);

    /**
     * Get holidays set, falling back to DB query when static cache is empty
     * (i.e., no schedule has been loaded yet).
     */
    private Set<LocalDate> getHolidays() {
        Set<LocalDate> holidays = SchoolSchedule.getSchoolHolidaysStatic();
        if (holidays != null && !holidays.isEmpty()) {
            return holidays;
        }
        // Fallback: query from DB when no schedule is loaded
        holidays = new HashSet<>();
        for (SchoolHoliday h : SchoolHoliday.<SchoolHoliday>listAll()) {
            holidays.add(h.date);
        }
        return holidays;
    }

    /**
     * Calculate completion statistics for a specific student group.
     * Convenience overload that fetches holidays internally.
     */
    public StudentGroupCompletionStats calculateStats(StudentGroup group) {
        return calculateStats(group, getHolidays());
    }

    /**
     * Calculate completion statistics for a specific student group.
     * @param holidays Pre-fetched holidays set (use SchoolSchedule.getSchoolHolidaysStatic() when calling directly)
     */
    public StudentGroupCompletionStats calculateStats(StudentGroup group, Set<LocalDate> holidays) {
        if (group == null) {
            throw new IllegalArgumentException("Student group cannot be null");
        }

        // Get stage pattern for this group (if any)
        List<LocalDate> stageDates = getStageDates(group, holidays);

        // Calculate curriculum hours
        int totalCurriculumHours = getTotalCurriculumHours(group);

        // Calculate actual hours distribution
        HoursDistribution distribution = calculateHoursDistribution(group, stageDates, holidays);

        // Calculate actual end date
        LocalDate actualEndDate = calculateActualEndDate(group);

        // Calculate ideal end date (when SHOULD they finish?)
        LocalDate idealEndDate = calculateIdealEndDate(group, totalCurriculumHours, stageDates, holidays);

        // Calculate days difference (null-safe)
        int daysDifference = 0;
        if (actualEndDate != null && idealEndDate != null) {
            daysDifference = (int) ChronoUnit.DAYS.between(idealEndDate, actualEndDate);
        }

        return new StudentGroupCompletionStats(
            group.name,
            totalCurriculumHours,
            stageDates.size(),
            distribution.hoursBeforeStage,
            distribution.hoursDuringStage,
            distribution.hoursAfterStage,
            distribution.actualDaysWithLessons,
            actualEndDate,
            idealEndDate,
            daysDifference
        );
    }

    /**
     * Calculate completion statistics for ALL student groups.
     * Optimized to fetch shared data (holidays) once instead of per-group.
     */
    public List<StudentGroupCompletionStats> calculateStatsForAllGroups() {
        List<StudentGroupCompletionStats> stats = new ArrayList<>();
        List<StudentGroup> groups = StudentGroup.listAll();

        // Fetch holidays once (cached by SchoolSchedule) instead of per-group
        Set<LocalDate> holidays = getHolidays();

        for (StudentGroup group : groups) {
            stats.add(calculateStats(group, holidays));
        }

        return stats;
    }

    /**
     * Calculate slot utilization for ALL student groups.
     *
     * For each group, counts valid slots WITHIN the group's planning horizon
     * (school start → ideal end date) and how many have lessons assigned.
     *
     * Valid slots pass these filters:
     * - Not after the group's ideal end date (bounded planning horizon)
     * - Not a weekend
     * - Not a school holiday
     * - Not a stage blackout day for this group
     * - Slot index within group's maxHoursPerDay
     *
     * A perfectly compact schedule has unassignedSlots = 0 to maxHoursPerDay-1
     * (the remainder on the last day if total hours don't fill it completely).
     */
    public List<SlotUtilization> calculateSlotUtilizationForAllGroups() {
        List<SlotUtilization> result = new ArrayList<>();
        List<StudentGroup> groups = StudentGroup.listAll();

        List<Timeslot> allTimeslots = SchoolSchedule.getAllTimeslots();
        if (allTimeslots == null || allTimeslots.isEmpty()) {
            return result;
        }

        Set<LocalDate> holidays = getHolidays();

        for (StudentGroup group : groups) {
            // Compute ideal end date to bound the planning horizon
            int totalHours = getTotalCurriculumHours(group);
            List<LocalDate> groupStageDates = getStageDates(group, holidays);
            LocalDate idealEnd = calculateIdealEndDate(group, totalHours, groupStageDates, holidays);

            int maxSlots = (group.maxHoursPerDay != null) ? group.maxHoursPerDay : TimeslotSequence.SLOT_START_TIMES.length;
            Set<LocalDate> stageDateSet = new HashSet<>(groupStageDates);

            // Build the set of valid timeslot start times for this group
            // Both totalValidSlots and assignedSlots must use the EXACT same set
            Set<java.time.LocalDateTime> validSlotStarts = new HashSet<>();
            for (Timeslot ts : allTimeslots) {
                LocalDate date = ts.getDate();
                DayOfWeek dow = ts.getDayOfWeek();

                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue;
                if (holidays != null && holidays.contains(date)) continue;
                if (stageDateSet.contains(date)) continue;
                int slotIndex = TimeslotSequence.slotIndexOf(ts);
                if (slotIndex >= maxSlots) continue;
                if (date.isAfter(idealEnd)) continue;

                validSlotStarts.add(ts.startDateTime);
            }

            int totalValidSlots = validSlotStarts.size();

            // Count DISTINCT timeslots that have at least one lesson for this group
            // (H3 violations can cause multiple lessons per timeslot — count slots, not lessons)
            Long assigned = (Long) em.createQuery(
                    "SELECT COUNT(DISTINCT l.timeslot.startDateTime) FROM Lesson l " +
                    "WHERE l.timeslot IS NOT NULL" +
                    "  AND l.course IS NOT NULL" +
                    "  AND l.course.studentGroup.id = :groupId" +
                    "  AND l.timeslot.startDateTime IN :validSlotStarts")
                    .setParameter("groupId", group.id)
                    .setParameter("validSlotStarts", validSlotStarts)
                    .getSingleResult();
            int assignedSlots = assigned != null ? assigned.intValue() : 0;

            SlotUtilization util = new SlotUtilization(group.id, group.name, totalValidSlots, assignedSlots);
            result.add(util);
        }

        return result;
    }

    /**
     * Get the stage dates for a student group.
     * Returns a sorted list of dates when the group is on stage.
     * @param holidays Pre-fetched holidays set
     */
    private List<LocalDate> getStageDates(StudentGroup group, Set<LocalDate> holidays) {
        List<LocalDate> stageDates = new ArrayList<>();

        StagePattern pattern = StagePattern.find("studentGroup", group).firstResult();
        if (pattern == null || pattern.startDate == null || pattern.endDate == null) {
            return stageDates; // Empty list
        }

        // Use StagePattern's expandDates method to get all stage dates
        Set<LocalDate> dates = pattern.expandDates(holidays);
        stageDates.addAll(dates);

        return stageDates;
    }

    /**
     * Get all school holidays as a set of dates.
     */
    private Set<LocalDate> getSchoolHolidays() {
        Set<LocalDate> holidays = new HashSet<>();
        List<SchoolHoliday> allHolidays = SchoolHoliday.listAll();

        for (SchoolHoliday holiday : allHolidays) {
            if (holiday.date != null) {
                holidays.add(holiday.date);
            }
        }

        return holidays;
    }

    /**
     * Calculate total curriculum hours for a student group.
     * Sum of course.totalHours for the group's REAL courses. Pseudo-teacher
     * courses (Stage/Esame - seeded at startup since TASK-381 "stage on the
     * grid") are excluded: calculateIdealEndDate() walks calendar days
     * EXCLUDING stage dates from its accumulation, so counting stage hours
     * in this numerator while skipping stage days in the denominator would
     * push every stage group's ideal end ~40-60 school days late.
     */
    private int getTotalCurriculumHours(StudentGroup group) {
        Long result = (Long) em.createQuery(
                "SELECT COALESCE(SUM(c.totalHours), 0) FROM Course c WHERE c.studentGroup = :group"
                        + " AND c.teacher.pseudo = false")
                .setParameter("group", group)
                .getSingleResult();
        return result != null ? result.intValue() : 0;
    }

    /**
     * Calculate how hours are distributed: before/during/after stage period.
     */
    private HoursDistribution calculateHoursDistribution(StudentGroup group, List<LocalDate> stageDates, Set<LocalDate> holidays) {
        int hoursBeforeStage = 0;
        int hoursDuringStage = 0;
        int hoursAfterStage = 0;
        Set<LocalDate> daysWithLessons = new HashSet<>();

        // Process regular lessons
        List<Lesson> lessons = Lesson.list("course.studentGroup", group);
        for (Lesson lesson : lessons) {
            if (lesson.timeslot == null) continue;

            LocalDate date = lesson.timeslot.getDate();
            daysWithLessons.add(date);

            if (stageDates.contains(date)) {
                hoursDuringStage += 1;
            } else if (date.isBefore(stageDates.isEmpty() ? LocalDate.MAX : stageDates.get(0))) {
                hoursBeforeStage += 1;
            } else {
                hoursAfterStage += 1;
            }
        }

        // Note: LabBlocks were deprecated in TASK-228. All labs are now pinned Lessons.
        // The Lessons processing above now includes lab lesson blocks (isLabHour=true).

        return new HoursDistribution(hoursBeforeStage, hoursDuringStage, hoursAfterStage, daysWithLessons.size());
    }

    /**
     * Calculate the actual end date (when the last lesson is scheduled).
     * Note: LabBlocks were deprecated in TASK-228 - all labs are now Lessons.
     */
    private LocalDate calculateActualEndDate(StudentGroup group) {
        LocalDate maxDate = null;

        // Check lessons (includes lab lessons with isLabHour=true)
        List<Lesson> lessons = Lesson.list("course.studentGroup", group);
        for (Lesson lesson : lessons) {
            if (lesson.timeslot != null) {
                LocalDate date = lesson.timeslot.getDate();
                if (maxDate == null || date.isAfter(maxDate)) {
                    maxDate = date;
                }
            }
        }

        return maxDate;
    }

    /**
     * Calculate the ideal end date.
     *
     * Algorithm: Walk through the calendar starting from school start
     * (TimeslotSequence.SCHOOL_YEAR_START), accumulating hours per school day
     * (excluding holidays and stage dates). Every school day has 8 uniform
     * hours (TASK-343 - no Friday-specific reduction; see
     * TimeslotSequence.SLOT_START_TIMES, the single source of truth for the
     * slot schema). When accumulated hours >= totalHours, that's the ideal
     * end date.
     *
     * This accounts for the actual school schedule, not just day counting.
     */
    private LocalDate calculateIdealEndDate(StudentGroup group, int totalCurriculumHours,
                                           List<LocalDate> stageDates, Set<LocalDate> holidays) {
        LocalDate schoolStart = TimeslotSequence.SCHOOL_YEAR_START;

        int hoursAccumulated = 0;
        LocalDate current = schoolStart;

        while (hoursAccumulated < totalCurriculumHours) {
            // Skip weekends
            if (current.getDayOfWeek() == DayOfWeek.SATURDAY || current.getDayOfWeek() == DayOfWeek.SUNDAY) {
                current = current.plusDays(1);
                continue;
            }

            // Skip holidays
            if (holidays.contains(current)) {
                current = current.plusDays(1);
                continue;
            }

            // Skip stage days (these don't count as school days)
            if (stageDates.contains(current)) {
                current = current.plusDays(1);
                continue;
            }

            // This is a school day - add its hours. Uniform 8 slots/day (TASK-343).
            int hoursToday = TimeslotSequence.SLOTS_PER_DAY;
            hoursAccumulated += hoursToday;

            if (hoursAccumulated >= totalCurriculumHours) {
                return current;
            }

            current = current.plusDays(1);
        }

        return current;
    }

    /**
     * Hour distribution before/during/after stage.
     */
    private record HoursDistribution(
        int hoursBeforeStage,
        int hoursDuringStage,
        int hoursAfterStage,
        int actualDaysWithLessons
    ) {}

    /**
     * Completion statistics for a single student group.
     */
    public static class StudentGroupCompletionStats {
        public final String groupName;
        public final int totalCurriculumHours;
        public final int stageDaysCount;
        public final int hoursBeforeStage;
        public final int hoursDuringStage;
        public final int hoursAfterStage;
        public final int actualDaysWithLessons;
        public final LocalDate actualEndDate;
        public final LocalDate idealEndDate;
        public final int daysDifference;

        // Formatter for JSON serialization
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        public StudentGroupCompletionStats(String groupName, int totalCurriculumHours, int stageDaysCount,
                                          int hoursBeforeStage, int hoursDuringStage, int hoursAfterStage,
                                          int actualDaysWithLessons, LocalDate actualEndDate,
                                          LocalDate idealEndDate, int daysDifference) {
            this.groupName = groupName;
            this.totalCurriculumHours = totalCurriculumHours;
            this.stageDaysCount = stageDaysCount;
            this.hoursBeforeStage = hoursBeforeStage;
            this.hoursDuringStage = hoursDuringStage;
            this.hoursAfterStage = hoursAfterStage;
            this.actualDaysWithLessons = actualDaysWithLessons;
            this.actualEndDate = actualEndDate;
            this.idealEndDate = idealEndDate;
            this.daysDifference = daysDifference;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("groupName", groupName);
            map.put("totalCurriculumHours", totalCurriculumHours);
            map.put("stageDaysCount", stageDaysCount);
            map.put("hoursBeforeStage", hoursBeforeStage);
            map.put("hoursDuringStage", hoursDuringStage);
            map.put("hoursAfterStage", hoursAfterStage);
            map.put("actualDaysWithLessons", actualDaysWithLessons);
            map.put("actualEndDate", actualEndDate != null ? actualEndDate.format(DATE_FORMATTER) : null);
            map.put("idealEndDate", idealEndDate != null ? idealEndDate.format(DATE_FORMATTER) : null);
            map.put("daysDifference", daysDifference);
            return map;
        }
    }
}
