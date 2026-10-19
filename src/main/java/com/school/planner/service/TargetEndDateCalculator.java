package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.IdealEndDateInfo;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.TimeslotSequence;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;

/**
 * Computes target end dates for student groups based on their total hours
 * and available school days.
 *
 * This replaces the H12 hard constraint approach with value-range pre-filtering.
 * Instead of penalizing late lessons, we compute a target end date and only
 * offer timeslots up to that date as options to the solver.
 *
 * Algorithm:
 * 1. Sum total hours for each student group from their courses
 * 2. Count available days (excluding weekends, holidays, stage blackout)
 * 3. Find the earliest date where enough slots exist
 * 4. Add buffer weeks for flexibility
 */
@ApplicationScoped
public class TargetEndDateCalculator {

    private static final Logger LOG = Logger.getLogger(TargetEndDateCalculator.class);

    /**
     * Buffer weeks added after computing minimum end date.
     * Set to 0 for tight targets that force aggressive horizontal compactness.
     * The solver's progressive extension will add weeks only if needed.
     */
    private static final int BUFFER_WEEKS = 0;

    /**
     * Instructional hours per day (standard Italian school day).
     */
    private static final int HOURS_PER_DAY = 6;

    /**
     * Slots per day, uniform for every school day (TASK-341) - no Friday-specific
     * reduction. Derived from TimeslotSequence.SLOT_START_TIMES, the single source
     * of truth for the slot schema.
     */
    private static final int SLOTS_PER_DAY = TimeslotSequence.SLOT_START_TIMES.length;

    /**
     * Fallback end date when ideal end date cannot be computed.
     * Used as a safety net for groups with missing curriculum data.
     */
    public static final LocalDate FALLBACK_END_DATE = TimeslotSequence.SCHOOL_YEAR_END;

    /**
     * Compute target end dates for all student groups.
     * Must be called before constructing SchoolSchedule for solving.
     *
     * @param groups All student groups
     * @param holidays School holidays (non-instructional days)
     * @param stagePatterns Stage/internship patterns (partial availability)
     * @param allTimeslots All available timeslots in the planning horizon
     */
    public void computeTargetEndDates(List<StudentGroup> groups,
                                       List<SchoolHoliday> holidays,
                                       List<StagePattern> stagePatterns,
                                       List<Timeslot> allTimeslots) {
        if (groups == null || groups.isEmpty()) {
            LOG.info("No student groups to compute target end dates for");
            return;
        }

        Set<LocalDate> holidayDates = holidays.stream()
                .map(h -> h.date)
                .collect(Collectors.toSet());

        // Determine planning horizon from timeslots
        LocalDate yearStart = allTimeslots.stream()
                .map(Timeslot::getDate)
                .min(LocalDate::compareTo)
                .orElse(TimeslotSequence.SCHOOL_YEAR_START);
        LocalDate yearEnd = allTimeslots.stream()
                .map(Timeslot::getDate)
                .max(LocalDate::compareTo)
                .orElse(TimeslotSequence.SCHOOL_YEAR_END);

        LOG.infof("Computing target end dates for %d groups (horizon: %s to %s)",
                groups.size(), yearStart, yearEnd);

        for (StudentGroup group : groups) {
            LocalDate targetDate = computeGroupTargetDate(
                    group, yearStart, yearEnd, holidayDates, stagePatterns, allTimeslots);
            group.idealEndDateVrpBuffered = targetDate;
            // Always log at INFO level to verify implementation
            LOG.infof("Group %s (id=%d): idealEndDateVrpBuffered=%s", group.name, group.id, targetDate);
        }
    }

    /**
     * Compute target end date for a single student group.
     * Also populates idealEndDateInfo with extended calculation data.
     */
    private LocalDate computeGroupTargetDate(StudentGroup group,
                                              LocalDate yearStart,
                                              LocalDate yearEnd,
                                              Set<LocalDate> holidays,
                                              List<StagePattern> allPatterns,
                                              List<Timeslot> allTimeslots) {

        // Sum total hours for this group from courses
        int totalHours = Course.<Course>find("studentGroup", group)
                .stream()
                .mapToInt(c -> c.totalHours)
                .sum();

        if (totalHours == 0) {
            LOG.debugf("Group %s has no hours, using year end %s", group.name, yearEnd);
            group.idealEndDateInfo = new IdealEndDateInfo(yearEnd, 0, 0, 0);
            return yearEnd;
        }

        // TASK-394: where the school's own per-date plan exists (DailyActivityQuota
        // expected-hours facts, seeded for all 14 groups in 2026/27 by TASK-385/381),
        // the ideal end is NOT estimated from a daily fill-rate model — it is the date
        // the plan itself reaches totalHours. The old flat model below credited
        // min(8, maxHoursPerDay) to every school day, which for 8h-cap groups computed
        // targets ~2 months before the school's own plan (G06: 31/03 vs the plan's
        // and the golden's 10/06, 272h "overflow" that was structure, not lateness).
        // Off-grid fact rows (Saturday/closure stage days) are included on purpose:
        // totalHours includes those course hours too (TASK-344/381 accounting).
        FactIdeal factIdeal = computeFactBasedIdealEnd(group, totalHours, yearEnd);
        if (factIdeal != null) {
            // Empty stageDates is deliberate, not a copy-paste gap from the legacy call
            // below: the plan being cumulated already INCLUDES stage hours (and
            // totalHours includes the Stage pseudo-course), so stage days are genuine
            // capacity here - excluding them, as the legacy estimate conservatively
            // does, would be the inconsistent choice.
            LocalDate factTarget = ensureEnoughSlots(factIdeal.date(), yearEnd, group, holidays,
                    Set.of(), allTimeslots, totalHours);
            group.idealEndDateInfo = new IdealEndDateInfo(
                    factIdeal.date(), totalHours, 0, factIdeal.daysCounted());
            LOG.infof("Group %s: %dh against the expected-hours plan (%d plan days) -> ideal %s, target %s (fact-based, TASK-394)",
                    group.name, totalHours, factIdeal.daysCounted(), factIdeal.date(), factTarget);
            return factTarget;
        }

        // Get stage dates for this group (these days are blocked for regular courses)
        Set<LocalDate> stageDates = allPatterns.stream()
                .filter(sp -> sp.studentGroup != null && sp.studentGroup.id.equals(group.id))
                .flatMap(sp -> expandStagePattern(sp, holidays).stream())
                .collect(Collectors.toSet());

        // Effective start date: group's startDate or year start
        LocalDate effectiveStart = group.startDate != null ? group.startDate : yearStart;

        // Scan forward to find minimum end date with enough slots
        // Track excluded days for IdealEndDateInfo
        int excludedDays = 0;
        int availableDays = 0;
        int cumulativeSlots = 0;
        LocalDate idealEndDate = null;

        // Pre-compute group's daily limit (doesn't change during iteration)
        Integer groupMaxHours = group.maxHoursPerDay;

        LocalDate cursor = effectiveStart;
        while (cursor.isBefore(yearEnd) || cursor.isEqual(yearEnd)) {
            DayOfWeek dow = cursor.getDayOfWeek();

            // Skip weekends (not counted in excluded or available)
            if (dow == SATURDAY || dow == SUNDAY) {
                cursor = cursor.plusDays(1);
                continue;
            }

            // Skip holidays (counted in excluded)
            if (holidays.contains(cursor)) {
                excludedDays++;
                cursor = cursor.plusDays(1);
                continue;
            }

            // Skip stage days for this group (counted in excluded)
            // Stage days are completely blocked for regular (non-professionalizzanti) courses
            if (stageDates.contains(cursor)) {
                excludedDays++;
                cursor = cursor.plusDays(1);
                continue;
            }

            // This is an available school day
            availableDays++;

            // Respect group's maxHoursPerDay if set (for 5-hour groups like G10, 4 FA)
            // Note: T28 external teacher exception allows 6h on Thursdays, but that's
            // handled by H11 constraint exemption. This calculation uses the conservative
            // maxHoursPerDay value to ensure target end date is not too early.
            int slotsToday = (groupMaxHours != null)
                    ? Math.min(SLOTS_PER_DAY, groupMaxHours)
                    : SLOTS_PER_DAY;
            cumulativeSlots += slotsToday;

            // Check if we've accumulated enough slots
            if (cumulativeSlots >= totalHours && idealEndDate == null) {
                idealEndDate = cursor;
            }

            cursor = cursor.plusDays(1);
        }

        // If not enough slots found, use year end
        if (idealEndDate == null) {
            idealEndDate = yearEnd;
        }

        // Add buffer weeks for solver flexibility
        LocalDate targetDate = idealEndDate.plusWeeks(BUFFER_WEEKS);

        // Don't exceed year end
        if (targetDate.isAfter(yearEnd)) {
            targetDate = yearEnd;
        }

        // Progressive extension: verify enough slots exist, extend if needed
        targetDate = ensureEnoughSlots(targetDate, yearEnd, group, holidays,
                stageDates, allTimeslots, totalHours);

        // Populate the extended info for UI display
        group.idealEndDateInfo = new IdealEndDateInfo(
                idealEndDate, totalHours, excludedDays, availableDays);

        LOG.infof("Group %s: %dh over %d available days (%d excluded) -> ideal %s, target %s",
                group.name, totalHours, availableDays, excludedDays, idealEndDate, targetDate);

        return targetDate;
    }

    /** Fact-based ideal end: the date the plan reaches totalHours + how many plan days it took. */
    private record FactIdeal(LocalDate date, int daysCounted) {
    }

    /**
     * TASK-394: cumulate the group's per-date expected-hours facts
     * ({@code DailyActivityQuota}: aula+lab+stage, ANY row regardless of
     * {@code FCQuota} — same all-rows basis as
     * {@code SchoolSchedule.getExpectedHours}) in date order until they cover
     * {@code totalHours}. Returns null when the group has no facts at all (the
     * caller falls back to the legacy fill-rate model — no 2026/27 group today).
     * If the facts exist but never reach totalHours (a plan/demand mismatch,
     * e.g. the known G14 990-vs-115 discrepancy in the opposite direction is
     * fine, but a short plan is not), the ideal is the LAST plan date, capped
     * at {@code yearEnd} — the plan's own horizon is the best available answer.
     */
    private FactIdeal computeFactBasedIdealEnd(StudentGroup group, int totalHours, LocalDate yearEnd) {
        List<DailyActivityQuota> facts = DailyActivityQuota
                .<DailyActivityQuota>list("studentGroup = ?1 order by date", group);
        if (facts.isEmpty()) {
            return null;
        }
        // TASK-527 BUG C (19/08, maintainer's inspection): the crossing used the RAW
        // course totalHours, which counts the Stage pseudo-course at its FULL sheet
        // hours even where a large part is deliberately OFF-GRID (G12: 183 of
        // 295 stage hours are Saturdays/closures the grid never sees; G08: 24).
        // The plan's cumulative only contains IN-GRID hours, so the crossing fired
        // early (G08: the 626th planned hour = 21/05 instead of riding toward the
        // plan's end; the golden places 667h, 50 past 21/05). Fix per the AC: the
        // crossing threshold is the IN-GRID pool (totalHours minus the group's
        // expectedUnscheduledHours), and the residual pool-vs-plan divergence is
        // INPUT information: logged loudly, never absorbed.
        // TASK-527 BUG C, second measure (verified live 19/08: the in-grid-pool
        // crossing moved the marker EARLIER, 17/05, the opposite of the intent):
        // when the school's own plan exists, the ideal end IS the plan's horizon
        // (its last positive day). The plan is the authority on WHEN the year
        // ends; the course pool only says how much work there is, and where the
        // two disagree (G08: plan 666h aula+lab vs pool 626) that divergence
        // is INPUT information: logged loudly, never absorbed into the date.
        int unscheduled = Course.<Course>find("studentGroup", group)
                .stream().mapToInt(c -> c.expectedUnscheduledHours == null ? 0 : c.expectedUnscheduledHours).sum();
        int inGridPool = totalHours - unscheduled;
        int planTotal = 0;
        int daysCounted = 0;
        LocalDate last = null;
        for (DailyActivityQuota f : facts) {
            int hours = f.aulaHours + f.labHours + f.stageHours;
            if (hours <= 0) continue;
            daysCounted++;
            planTotal += hours;
            last = f.date;
        }
        if (last == null) {
            return null; // rows exist but all zero-hour: nothing to anchor on
        }
        if (planTotal != inGridPool) {
            LOG.warnf("[IdealEnd] Group %s: the school's plan totals %dh but the IN-GRID"
                    + " course pool is %dh (%+d; raw pool %dh minus %dh off-grid): ideal"
                    + " end anchored to the PLAN horizon %s. The divergence is input"
                    + " (calendar vs incarichi), report it, do not absorb it",
                    group.name, planTotal, inGridPool, planTotal - inGridPool,
                    totalHours, unscheduled, last);
        }
        LocalDate ideal = last.isAfter(yearEnd) ? yearEnd : last;
        return new FactIdeal(ideal, daysCounted);
    }

    /**
     * Expand a stage pattern into individual dates.
     */
    private Set<LocalDate> expandStagePattern(StagePattern pattern, Set<LocalDate> holidays) {
        return pattern.expandDates(holidays);
    }

    /**
     * Ensure enough slots exist between group start and target date.
     * Progressive extension: add weeks until we have enough.
     */
    private LocalDate ensureEnoughSlots(LocalDate computedEnd,
                                         LocalDate yearEnd,
                                         StudentGroup group,
                                         Set<LocalDate> holidays,
                                         Set<LocalDate> stageDates,
                                         List<Timeslot> allTimeslots,
                                         int slotsNeeded) {

        LocalDate effectiveStart = group.startDate != null ? group.startDate :
                allTimeslots.stream()
                        .map(Timeslot::getDate)
                        .min(LocalDate::compareTo)
                        .orElse(TimeslotSequence.SCHOOL_YEAR_START);

        long availableSlots = countAvailableSlots(effectiveStart, computedEnd,
                holidays, stageDates, allTimeslots);

        // Need 10% buffer for constraint flexibility
        long neededWithBuffer = (long) (slotsNeeded * 1.1);

        int extensions = 0;
        while (availableSlots < neededWithBuffer && computedEnd.isBefore(yearEnd)) {
            computedEnd = computedEnd.plusWeeks(1);
            availableSlots = countAvailableSlots(effectiveStart, computedEnd,
                    holidays, stageDates, allTimeslots);
            extensions++;
        }

        if (extensions > 0) {
            LOG.debugf("Group %s: extended target by %d weeks to %s for slot availability",
                    group.name, extensions, computedEnd);
        }

        return computedEnd.isAfter(yearEnd) ? yearEnd : computedEnd;
    }

    /**
     * Count total available slots in a date range.
     */
    private long countAvailableSlots(LocalDate start,
                                      LocalDate end,
                                      Set<LocalDate> holidays,
                                      Set<LocalDate> stageDates,
                                      List<Timeslot> allTimeslots) {
        return allTimeslots.stream()
                .filter(ts -> {
                    LocalDate d = ts.getDate();
                    if (d.isBefore(start) || d.isAfter(end)) return false;
                    if (d.getDayOfWeek() == SATURDAY || d.getDayOfWeek() == SUNDAY) return false;
                    if (holidays.contains(d)) return false;
                    // Stage days count as 50% availability
                    if (stageDates.contains(d)) return false; // Conservative: exclude for now
                    return true;
                })
                .count();
    }

    /**
     * Build a map of group ID to ideal end date for pre-assignment filtering.
     * This is a convenience method that combines computeTargetEndDates() with
     * extraction of the ideal end dates into a Map for efficient lookup.
     *
     * Pre-assignment phases must never place lessons after a group's ideal end date.
     *
     * @param groupIds Set of group IDs to compute ideal end dates for
     * @return Map of groupId -> ideal end date (uses FALLBACK_END_DATE if computation fails)
     */
    public java.util.Map<Long, LocalDate> buildIdealEndDatesMap(java.util.Set<Long> groupIds) {
        // Load required data
        List<StudentGroup> groups = StudentGroup.list("id in ?1", groupIds);
        List<SchoolHoliday> holidays = SchoolHoliday.listAll();
        List<StagePattern> patterns = StagePattern.listAll();
        List<Timeslot> timeslots = Timeslot.listAll();

        // Compute ideal end dates (populates idealEndDateInfo on each group)
        computeTargetEndDates(groups, holidays, patterns, timeslots);

        // Extract ideal end dates into a map for efficient lookup
        java.util.Map<Long, LocalDate> idealEndDates = new java.util.LinkedHashMap<>();
        for (StudentGroup group : groups) {
            if (group.idealEndDateInfo != null && group.idealEndDateInfo.idealEndDate() != null) {
                idealEndDates.put(group.id, group.idealEndDateInfo.idealEndDate());
            } else {
                // Use fallback if ideal end date computation failed
                idealEndDates.put(group.id, FALLBACK_END_DATE);
                LOG.warnf("Group %s: no idealEndDateInfo, using fallback %s",
                        group.name, FALLBACK_END_DATE);
            }
        }

        return idealEndDates;
    }
}
