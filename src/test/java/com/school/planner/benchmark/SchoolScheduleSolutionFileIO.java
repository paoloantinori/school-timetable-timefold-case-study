package com.school.planner.benchmark;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.school.planner.domain.Course;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.TimeslotSequence;

import ai.timefold.solver.core.api.domain.solution.SolutionFileIO;

/**
 * Solution file I/O for SchoolSchedule using JSON serialization.
 * Initializes static state required by Lesson value range providers.
 *
 * <p>Replicates TargetEndDateCalculator logic to compute per-group idealEndDateVrpBuffered
 * from the exported data (courses, holidays, stage patterns, timeslots).
 * This ensures VRP filtering works correctly during benchmarks.
 */
public class SchoolScheduleSolutionFileIO implements SolutionFileIO<SchoolSchedule> {

    private static final int SLOTS_PER_DAY_MON_THU = 6;
    private static final int SLOTS_PER_DAY_FRIDAY = 5;

    private final ObjectMapper objectMapper;

    public SchoolScheduleSolutionFileIO() {
        objectMapper = createObjectMapper();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    @Override
    public SchoolSchedule read(File inputSolutionFile) {
        SchoolSchedule schedule;
        try {
            schedule = objectMapper.readValue(inputSolutionFile, SchoolSchedule.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read solution file: " + inputSolutionFile, e);
        }
        if (schedule == null) {
            return null;
        }

        // Initialize static state required by Lesson value range providers
        SchoolSchedule.setAllTimeslotsStatic(schedule.getTimeslots());
        SchoolSchedule.setAllRoomsStatic(schedule.getRooms());
        SchoolSchedule.setAllLessonsStatic(schedule.getLessons());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(schedule.getLessons());
        SchoolSchedule.setTeacherAvailabilitiesStatic(schedule.getTeacherAvailabilities());
        SchoolSchedule.setSchoolHolidaysStatic(schedule.getSchoolHolidays());
        // TASK-454: the export payload now carries the group calendar rules - publish
        // them or the offline solve runs H20/VRP-calendar blind on the same payload
        // the app solves correctly (refreshSolverStatics does this on the live path).
        SchoolSchedule.setGroupCalendarRulesStatic(schedule.getGroupCalendarRules());

        Set<LocalDate> holidayDates = schedule.getSchoolHolidays() != null
                ? schedule.getSchoolHolidays().stream().map(sh -> sh.date).collect(Collectors.toSet())
                : Set.of();

        SchoolSchedule.setStageBlackoutDatesStatic(schedule.getStagePatterns(), holidayDates);
        SchoolSchedule.setLabAssistantsStatic(schedule.getLabAssistants());

        // Compute per-group target end dates (mirrors TargetEndDateCalculator logic)
        Map<Long, LocalDate> targetDateMap = computeTargetEndDates(
                schedule.getStudentGroups(), schedule.getCourses(),
                schedule.getSchoolHolidays(), schedule.getStagePatterns(),
                schedule.getTimeslots());
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(targetDateMap);

        // Initialize timeslot sequence
        if (schedule.getTimeslots() != null && !schedule.getTimeslots().isEmpty()) {
            SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(schedule.getTimeslots()));
        }

        return schedule;
    }

    @Override
    public void write(SchoolSchedule schedule, File file) {
        try {
            objectMapper.writeValue(file, schedule);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write solution file: " + file, e);
        }
    }

    @Override
    public String getInputFileExtension() {
        return "json";
    }

    /**
     * Compute target end dates for each student group, mirroring the production
     * TargetEndDateCalculator logic. Uses courses, holidays, stage patterns,
     * and timeslots from the exported JSON to produce realistic VRP boundaries.
     */
    private Map<Long, LocalDate> computeTargetEndDates(
            List<StudentGroup> groups, List<Course> courses,
            List<SchoolHoliday> holidays, List<StagePattern> stagePatterns,
            List<Timeslot> timeslots) {

        Map<Long, LocalDate> result = new HashMap<>();
        if (groups == null || timeslots == null || timeslots.isEmpty()) {
            return result;
        }

        Set<LocalDate> holidayDates = holidays != null
                ? holidays.stream().map(h -> h.date).collect(Collectors.toSet())
                : Set.of();

        LocalDate yearStart = timeslots.stream()
                .map(Timeslot::getDate).min(LocalDate::compareTo)
                .orElse(LocalDate.of(2025, 9, 10));
        LocalDate yearEnd = timeslots.stream()
                .map(Timeslot::getDate).max(LocalDate::compareTo)
                .orElse(LocalDate.of(2026, 6, 10));

        for (StudentGroup group : groups) {
            LocalDate targetDate = computeGroupTargetDate(
                    group, courses, yearStart, yearEnd, holidayDates, stagePatterns, timeslots);
            group.idealEndDateVrpBuffered = targetDate;
            result.put(group.id, targetDate);
        }

        return result;
    }

    private LocalDate computeGroupTargetDate(
            StudentGroup group, List<Course> courses,
            LocalDate yearStart, LocalDate yearEnd,
            Set<LocalDate> holidays, List<StagePattern> stagePatterns,
            List<Timeslot> timeslots) {

        // Sum total hours for this group from courses
        int totalHours = courses.stream()
                .filter(c -> c.studentGroup != null && c.studentGroup.id.equals(group.id))
                .mapToInt(c -> c.totalHours)
                .sum();

        if (totalHours == 0) {
            return yearEnd;
        }

        // Get stage blackout dates for this group
        Set<LocalDate> stageDates = new HashSet<>();
        if (stagePatterns != null) {
            for (StagePattern sp : stagePatterns) {
                if (sp.studentGroup != null && sp.studentGroup.id.equals(group.id)) {
                    stageDates.addAll(expandStagePattern(sp, holidays));
                }
            }
        }

        LocalDate effectiveStart = group.startDate != null ? group.startDate : yearStart;
        Integer groupMaxHours = group.maxHoursPerDay;

        // Scan forward to find minimum end date with enough slots
        int cumulativeSlots = 0;
        LocalDate idealEndDate = null;

        LocalDate cursor = effectiveStart;
        while (!cursor.isAfter(yearEnd)) {
            DayOfWeek dow = cursor.getDayOfWeek();

            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                cursor = cursor.plusDays(1);
                continue;
            }
            if (holidays.contains(cursor)) {
                cursor = cursor.plusDays(1);
                continue;
            }
            if (stageDates.contains(cursor)) {
                cursor = cursor.plusDays(1);
                continue;
            }

            int standardSlots = (dow == DayOfWeek.FRIDAY) ? SLOTS_PER_DAY_FRIDAY : SLOTS_PER_DAY_MON_THU;
            int slotsToday = (groupMaxHours != null)
                    ? Math.min(standardSlots, groupMaxHours)
                    : standardSlots;
            cumulativeSlots += slotsToday;

            if (cumulativeSlots >= totalHours && idealEndDate == null) {
                idealEndDate = cursor;
            }

            cursor = cursor.plusDays(1);
        }

        if (idealEndDate == null) {
            idealEndDate = yearEnd;
        }

        // Progressive extension: ensure 10% buffer of slots
        LocalDate targetDate = idealEndDate;
        while (targetDate.isBefore(yearEnd)) {
            long availableSlots = countAvailableSlots(effectiveStart, targetDate,
                    holidays, stageDates, timeslots, groupMaxHours);
            long needed = (long) (totalHours * 1.1);
            if (availableSlots >= needed) {
                break;
            }
            targetDate = targetDate.plusWeeks(1);
        }

        return targetDate.isAfter(yearEnd) ? yearEnd : targetDate;
    }

    private Set<LocalDate> expandStagePattern(StagePattern pattern, Set<LocalDate> holidays) {
        Set<LocalDate> dates = new HashSet<>();
        if (pattern.dayOfWeekMask == null) {
            return dates;
        }
        // dayOfWeekMask is already a Set<DayOfWeek> after Jackson deserialization
        Set<DayOfWeek> days = pattern.dayOfWeekMask;
        LocalDate cursor = pattern.startDate;
        while (!cursor.isAfter(pattern.endDate)) {
            if (days.contains(cursor.getDayOfWeek()) && !holidays.contains(cursor)) {
                dates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    private long countAvailableSlots(LocalDate start, LocalDate end,
                                          Set<LocalDate> holidays, Set<LocalDate> stageDates,
                                          List<Timeslot> timeslots, Integer maxHours) {
        return timeslots.stream()
                .filter(ts -> {
                    LocalDate d = ts.getDate();
                    if (d.isBefore(start) || d.isAfter(end)) return false;
                    if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) return false;
                    if (holidays.contains(d)) return false;
                    if (stageDates.contains(d)) return false;
                    return true;
                })
                .count();
    }
}
