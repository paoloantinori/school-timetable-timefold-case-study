package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.TimeslotSequence;

/**
 * Resolves the per-group, per-weekday slot-level calendar (TASK-342): which
 * slots are "open" for a student group on a given day.
 *
 * The resolution algorithm (which rule wins when several apply to the same
 * date) is exposed as pure static methods so it can be reused without a CDI
 * container / DB access — the solver's constraint provider and
 * {@code Lesson.getAvailableTimeslots()} cannot inject a CDI service, so they
 * consult a static snapshot on {@link com.school.planner.domain.SchoolSchedule}
 * (populated by {@code ScheduleService.loadSchedule()}) that calls back into
 * these same static methods. This keeps the resolution logic in exactly one
 * place.
 */
@ApplicationScoped
public class GroupCalendarService {

    /** Total slots per day in the current schema (TimeslotSequence.SLOT_START_TIMES). */
    public static final int TOTAL_SLOTS = TimeslotSequence.SLOT_START_TIMES.length;

    /** School weekdays, single source of truth for {@link #resolveRange}'s per-day iteration. */
    private static final List<DayOfWeek> SCHOOL_WEEKDAYS = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    /**
     * True if the given timeslot is open for the group. No matching rule at all
     * (for that group+weekday) means every slot is open — backward compatible
     * with the pre-TASK-342 model.
     */
    public boolean isOpen(StudentGroup group, Timeslot timeslot) {
        if (group == null || group.id == null || timeslot == null) return true;
        return isOpen(fetchRules(group.id, timeslot.getDate().getDayOfWeek()), timeslot);
    }

    /**
     * Number of open slots for the group on the given weekday/date (0 if the
     * day is fully closed for that group, {@link #TOTAL_SLOTS} if no rule applies).
     */
    public int openSlotCount(StudentGroup group, DayOfWeek dayOfWeek, LocalDate date) {
        if (group == null || group.id == null || dayOfWeek == null || date == null) return TOTAL_SLOTS;
        int[] range = effectiveRange(fetchRules(group.id, dayOfWeek), dayOfWeek, date);
        return openSlotCountFromRange(range);
    }

    private List<GroupCalendarRule> fetchRules(Long groupId, DayOfWeek dayOfWeek) {
        return GroupCalendarRule.list("studentGroup.id = ?1 and dayOfWeek = ?2", groupId, dayOfWeek);
    }

    /**
     * One resolved [minSlot, maxSlot] range for one (group, date) pair, as
     * returned by {@link #resolveRange}.
     */
    public static class ResolvedSlotRange {
        public final Long groupId;
        public final LocalDate date;
        public final int minSlot;
        public final int maxSlot;
        public final boolean closed;

        public ResolvedSlotRange(Long groupId, LocalDate date, int minSlot, int maxSlot, boolean closed) {
            this.groupId = groupId;
            this.date = date;
            this.minSlot = minSlot;
            this.maxSlot = maxSlot;
            this.closed = closed;
        }
    }

    /**
     * Resolves the effective [minSlot, maxSlot] range for every (student group,
     * school-weekday date) pair in {@code [from, to]} (inclusive), replacing
     * the frontend's TS port of this same resolution (TASK-358 code review N2)
     * so the "most-specific-rule-wins" algorithm exists in exactly one place.
     *
     * <p>Efficiency: rules and groups are fetched once (both small tables), not
     * per date. For a given (group, weekday) that has no date-scoped rule
     * (every candidate has {@code effectiveFrom == effectiveTo == null}), the
     * resolved range is identical for every date sharing that weekday, so it's
     * computed once and reused; only weekdays with at least one date-scoped
     * rule are resolved per-date.
     *
     * <p>Legacy-profile fallback (N3): a group with zero rules at all for a
     * weekday (the pre-TASK-342 case) falls back to
     * {@code [0, maxHoursPerDay - 1]} when {@code maxHoursPerDay} is set,
     * instead of the pure {@link #effectiveRange}'s "fully open" default - this
     * reproduces the old frontend maxHoursPerDay-based graying heuristic for
     * groups that were never migrated to explicit calendar rules. This fallback
     * is deliberately local to this resolved view; the solver-facing
     * {@link #effectiveRange}/{@link #isOpen} stay untouched since H11 already
     * enforces maxHoursPerDay as its own hard constraint.
     */
    public List<ResolvedSlotRange> resolveRange(LocalDate from, LocalDate to) {
        List<GroupCalendarRule> allRules = GroupCalendarRule.listAll();
        Map<Long, List<GroupCalendarRule>> rulesByGroup = allRules.stream()
                .filter(r -> r.studentGroup != null)
                .collect(Collectors.groupingBy(r -> r.studentGroup.id));

        List<StudentGroup> groups = StudentGroup.listAll();
        List<ResolvedSlotRange> result = new ArrayList<>();

        for (StudentGroup group : groups) {
            List<GroupCalendarRule> groupRules = rulesByGroup.getOrDefault(group.id, List.of());

            // Per-weekday cache: populated for weekdays whose range doesn't vary by date
            // (no rule at all -> legacy fallback; rule(s) but none date-scoped -> effectiveRange).
            Map<DayOfWeek, int[]> cache = new EnumMap<>(DayOfWeek.class);
            for (DayOfWeek dow : SCHOOL_WEEKDAYS) {
                boolean hasAnyRule = false;
                boolean hasDateScopedRule = false;
                for (GroupCalendarRule r : groupRules) {
                    if (r.dayOfWeek != dow) continue;
                    hasAnyRule = true;
                    if (r.effectiveFrom != null || r.effectiveTo != null) {
                        hasDateScopedRule = true;
                        break; // both flags settled - no need to keep scanning this weekday
                    }
                }
                if (!hasAnyRule) {
                    cache.put(dow, group.maxHoursPerDay != null
                            ? new int[] {0, group.maxHoursPerDay - 1}
                            : new int[] {0, TOTAL_SLOTS - 1});
                } else if (!hasDateScopedRule) {
                    cache.put(dow, effectiveRange(groupRules, dow, from));
                }
                // else: left uncached, resolved per-date below.
            }

            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                DayOfWeek dow = date.getDayOfWeek();
                if (!SCHOOL_WEEKDAYS.contains(dow)) continue;
                int[] range = cache.containsKey(dow) ? cache.get(dow) : effectiveRange(groupRules, dow, date);
                result.add(new ResolvedSlotRange(group.id, date, range[0], range[1], range[1] < range[0]));
            }
        }

        return result;
    }

    // ─── Pure, DB-free resolution (reused by SchoolSchedule's solver-time static snapshot) ───

    /**
     * True if the timeslot's slot index falls within the effective rule's
     * [minSlot, maxSlot] range for its date. {@code candidates} must already be
     * restricted to a single (group, weekday) — see {@link #effectiveRange}.
     */
    public static boolean isOpen(List<GroupCalendarRule> candidatesForGroupDay, Timeslot timeslot) {
        if (timeslot == null) return true;
        int slotIndex = TimeslotSequence.slotIndexOf(timeslot);
        if (slotIndex < 0) return true; // defensive: unrecognized slot, don't block placement
        LocalDate date = timeslot.getDate();
        int[] range = effectiveRange(candidatesForGroupDay, date.getDayOfWeek(), date);
        return slotIndex >= range[0] && slotIndex <= range[1];
    }

    /**
     * Resolves the effective [minSlot, maxSlot] range for a (group, weekday, date)
     * out of a candidate rule list. Rules not matching the weekday or whose
     * effective date range excludes {@code date} are ignored. Among the
     * remaining rules, the most specific (narrowest effective date span) wins;
     * ties are broken by the latest {@code effectiveFrom}. No matching rule
     * means "fully open" ([0, TOTAL_SLOTS - 1]) for backward compatibility.
     */
    public static int[] effectiveRange(List<GroupCalendarRule> candidates, DayOfWeek dayOfWeek, LocalDate date) {
        GroupCalendarRule best = null;
        if (candidates != null) {
            for (GroupCalendarRule rule : candidates) {
                if (rule.dayOfWeek != dayOfWeek) continue;
                if (rule.effectiveFrom != null && date.isBefore(rule.effectiveFrom)) continue;
                if (rule.effectiveTo != null && date.isAfter(rule.effectiveTo)) continue;
                if (best == null || isMoreSpecific(rule, best)) {
                    best = rule;
                }
            }
        }
        return best == null ? new int[] {0, TOTAL_SLOTS - 1} : new int[] {best.minSlot, best.maxSlot};
    }

    /**
     * Number of open slots represented by an [minSlot, maxSlot] range as returned
     * by {@link #effectiveRange}: 0 for the closed-day sentinel (maxSlot &lt; minSlot),
     * otherwise the inclusive slot count. Shared by the CDI instance API
     * ({@link #openSlotCount}) and the solver's static snapshot
     * ({@link com.school.planner.domain.SchoolSchedule#getGroupCalendarOpenSlotCount})
     * so the arithmetic lives in exactly one place.
     */
    public static int openSlotCountFromRange(int[] range) {
        return Math.max(0, range[1] - range[0] + 1);
    }

    /**
     * Narrower effective-date span wins; ties broken by the latest effectiveFrom;
     * remaining ties (identical span + effectiveFrom - duplicate rules) broken by
     * the higher rule id, so resolution is deterministic regardless of the
     * incidental order candidates arrive in (list/iteration order isn't guaranteed
     * by the DB query or the static snapshot's grouping).
     */
    private static boolean isMoreSpecific(GroupCalendarRule candidate, GroupCalendarRule current) {
        long candidateSpan = effectiveSpanDays(candidate);
        long currentSpan = effectiveSpanDays(current);
        if (candidateSpan != currentSpan) {
            return candidateSpan < currentSpan;
        }
        LocalDate candidateFrom = candidate.effectiveFrom;
        LocalDate currentFrom = current.effectiveFrom;
        if (Objects.equals(candidateFrom, currentFrom)) {
            // Identical span + effectiveFrom (duplicate rules): break the tie by rule id.
            return candidate.id != null && current.id != null && candidate.id > current.id;
        }
        if (candidateFrom == null) return false; // unbounded start is never more specific
        if (currentFrom == null) return true;
        return candidateFrom.isAfter(currentFrom);
    }

    private static long effectiveSpanDays(GroupCalendarRule rule) {
        LocalDate from = rule.effectiveFrom != null ? rule.effectiveFrom : LocalDate.MIN;
        LocalDate to = rule.effectiveTo != null ? rule.effectiveTo : LocalDate.MAX;
        return ChronoUnit.DAYS.between(from, to);
    }
}
