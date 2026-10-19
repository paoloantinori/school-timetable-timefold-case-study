package com.school.planner.solver.neighborhood;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.SurplusHarvest;
import com.school.planner.solver.TimeslotSequence;

/**
 * TASK-589: the SINGLE SOURCE of the harvest cell/donor enumeration, shared by
 * the deterministic pass ({@link SurplusHarvest}) and the LS move stream
 * ({@link HarvestMoveListFactory}). Every gate lives here ONCE: a change to the
 * pass's notion of "which donors may feed which cells" propagates to the stream
 * with zero duplication (the guard written into the spike's design).
 *
 * <p>The stream consumes the pairs directly: the LS's own score evaluation
 * replaces the pass's per-move oracle (H7/H33/H17 are score-visible there),
 * so only the GENERATION-quality gates are needed: over-quota donor day,
 * non-pinned, non-pseudo, and the landing's fresh-range legality.
 */
public final class HarvestNeighborhood {

    private HarvestNeighborhood() {
    }

    /** A declared-empty (group, date) cell with its aula+lab quota (v12.3 base). */
    public record Cell(StudentGroup group, LocalDate date, int quota) {}

    /** A gated (donor lesson, landing slot) pair: one potential harvest move. */
    public record DonorLanding(Lesson lesson, Timeslot landing) {}

    /**
     * The declared-empty in-scope cells (H34's own population, v12.1+v12.3:
     * pool-cap/G14 groups excluded, aula+lab base, stage-only rows skipped).
     * Deduplicated per (group, date).
     */
    public static List<Cell> emptyDeclaredCells(SchoolSchedule schedule) {
        List<Cell> cells = new ArrayList<>();
        if (schedule.getTimeslots() == null) return cells;
        Map<Long, Map<LocalDate, Integer>> hoursByGroupDate = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null
                    || l.course.teacher != null && l.course.teacher.pseudo) continue;
            hoursByGroupDate
                    .computeIfAbsent(l.course.studentGroup.id, k -> new HashMap<>())
                    .merge(l.timeslot.getDate(), 1, Integer::sum);
        }
        for (StudentGroup group : schedule.getStudentGroups()) {
            if (group == null || group.id == null) continue;
            if (SchoolScheduleConstraintProvider.isQuotaFillExcluded(group.name)) continue;
            for (Timeslot ts : schedule.getTimeslots()) {
                LocalDate date = ts.getDate();
                // v12.3: aula+lab base, the SAME one H34/H32/M33 and every deterministic
                // sibling read (getExpectedHours sums stageHours too: phantom targets).
                var row = SchoolSchedule.getExpectedHoursRow(group.id, date);
                if (row == null || row.aulaHours + row.labHours <= 0) continue;
                int quota = row.aulaHours + row.labHours;
                int placed = hoursByGroupDate
                        .getOrDefault(group.id, Map.of())
                        .getOrDefault(date, 0);
                if (placed == 0
                        && !cells.stream().anyMatch(c -> c.group().id.equals(group.id) && c.date().equals(date))) {
                    cells.add(new Cell(group, date, quota));
                }
            }
        }
        return cells;
    }

    /**
     * The donor lessons of a group: unpinned, non-pseudo, on days that are
     * OVER their effective (net-of-pins, aula+lab) quota. Any date distance.
     */
    public static List<Lesson> donorLessons(SchoolSchedule schedule, StudentGroup group) {
        Map<LocalDate, Integer> hoursOn = new HashMap<>();
        List<Lesson> candidates = new ArrayList<>();
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot == null || l.pinned || l.course == null
                    || l.course.studentGroup == null || !l.course.studentGroup.id.equals(group.id)) continue;
            if (l.course.teacher != null && l.course.teacher.pseudo) continue;
            hoursOn.merge(l.timeslot.getDate(), 1, Integer::sum);
            candidates.add(l);
        }
        List<Lesson> donors = new ArrayList<>();
        for (Lesson l : candidates) {
            LocalDate date = l.timeslot.getDate();
            int quota = effectiveAulaLabQuota(group.id, date);
            if (quota > 0 && hoursOn.getOrDefault(date, 0) > quota) {
                donors.add(l);
            }
        }
        return donors;
    }

    /** The target day's first calendar-open slot (H31's own anchor) or null.
     *  Chronological order (the pass's openSlots are startDateTime-sorted: review
     *  I2 - an unsorted scan would land past the first slot and pay H31). */
    public static Timeslot firstWindowOpenSlot(SchoolSchedule schedule, StudentGroup group, LocalDate date) {
        int[] window = SchoolSchedule.getGroupCalendarWindow(group.id, date.getDayOfWeek(), date);
        int minOpen = window.length > 1 ? window[1] : 0;
        List<Timeslot> daySlots = new ArrayList<>();
        for (Timeslot ts : schedule.getTimeslots()) {
            if (ts.getDate().equals(date)) daySlots.add(ts);
        }
        daySlots.sort(Comparator.comparing(ts -> ts.startDateTime));
        for (Timeslot ts : daySlots) {
            if (TimeslotSequence.slotIndexOf(ts) >= minOpen
                    && !groupBusyAt(schedule, group, ts)) {
                return ts;
            }
        }
        return null;
    }

    /**
     * The gated (donor, landing) pairs the stream offers the LS: for every
     * empty declared cell, every gated donor lesson of the same group paired
     * with the cell's first-open landing, filtered to fresh-range legality
     * (the VRP's own verdict: the LS must never receive a move it would undo).
     */
    public static List<DonorLanding> gatedDonorLandings(SchoolSchedule schedule) {
        List<DonorLanding> pairs = new ArrayList<>();
        for (Cell cell : emptyDeclaredCells(schedule)) {
            Timeslot landing = firstWindowOpenSlot(schedule, cell.group(), cell.date());
            if (landing == null) continue;
            for (Lesson donor : donorLessons(schedule, cell.group())) {
                if (isInValueRange(donor.getAvailableTimeslotsFresh(), landing)) {
                    pairs.add(new DonorLanding(donor, landing));
                }
            }
        }
        return pairs;
    }

    /** The pass's own VRP-guard semantics: an empty fresh range (statics not
     *  published) means UNRESTRICTED, not nothing. */
    private static boolean isInValueRange(List<Timeslot> range, Timeslot landing) {
        if (range.isEmpty()) return true;
        return range.contains(landing);
    }

    private static boolean groupBusyAt(SchoolSchedule schedule, StudentGroup group, Timeslot ts) {
        for (Lesson l : schedule.getLessons()) {
            if (l.timeslot != null && l.timeslot.equals(ts) && l.course != null
                    && l.course.studentGroup != null && l.course.studentGroup.id.equals(group.id)) {
                return true;
            }
        }
        return false;
    }

    /** The aula+lab quota NET of the day's authoritative pinned hours (TASK-424). */
    private static int effectiveAulaLabQuota(Long groupId, LocalDate date) {
        var row = SchoolSchedule.getExpectedHoursRow(groupId, date);
        if (row == null) return -1;
        int quota = row.aulaHours + row.labHours;
        if (quota <= 0) return quota;
        return Math.max(0, quota - SchoolSchedule.getAuthoritativePinnedHours(groupId, date));
    }

}
