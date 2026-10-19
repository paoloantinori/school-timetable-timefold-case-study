package com.school.planner.solver;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.school.planner.domain.Timeslot;

/**
 * Utility for ordered timeslot lookups within a day.
 * Slots per day (indices 0-7):
 *   0: 08:00-09:00  1: 09:00-10:00  2: 10:00-11:00  3: 11:00-12:00
 *   4: 12:00-13:00  5: 13:00-14:00  6: 14:30-15:30  7: 15:30-16:30
 */
public class TimeslotSequence {

    // ─── School-year bounds (single source of truth, TASK-343 code review) ──
    // Moved out of DataGenerator so services (e.g. HybridPhase1aService,
    // StudentGroupCompletionService) don't need a dependency on the bootstrap
    // package just to know the school-year bounds. DataGenerator references
    // these too, instead of declaring its own copy.
    public static final LocalDate SCHOOL_YEAR_START = LocalDate.of(2026, 9, 14);
    /**
     * TASK-550 grid extension (maintainer 25/08 07:50): the horizon now includes the three
     * golden-practiced recovery days (Fri 2027-06-11, Mon 06-14, Tue 06-15) - the
     * golden HOLDS hours there (G04 stage 8h x2, G04 Scienze 5h, G08 RA 5h x2:
     * closed=false in lessons.csv), and before this extension those 26 held hours were
     * structurally unplaceable. The quota side mirrors the golden exactly (see
     * DataGenerator's golden-mirror seeding) so no group without golden practice
     * gains capacity (H26 keeps them out).
     */
    public static final LocalDate SCHOOL_YEAR_END = LocalDate.of(2027, 6, 15);
    /** The pre-extension horizon: quota rows past this line are seeded ONLY where the golden holds. */
    public static final LocalDate PRE_EXTENSION_YEAR_END = LocalDate.of(2027, 6, 10);

    // ─── Static slot-schema utilities (2026/27 calendar, TASK-341) ───────────
    // Single source of truth for slot-adjacency semantics, shared by M15/H16/H17
    // (and any other constraint needing to know whether two timeslots are "next
    // to each other" for gap-detection purposes). Slot 5 (13:00-14:00) and slot 6
    // (14:30-15:30) are separated only by the 14:00-14:30 lunch break: they are
    // treated as adjacent (lunch is not a scheduling gap), but a real missing slot
    // (e.g. slot 4 -> slot 6, skipping slot 5) is still a gap.

    // Single source of truth for the 8 slot start times (each slot is exactly
    // 1 hour long). DataGenerator.STANDARD_SLOTS derives its start/end pairs
    // from this array instead of re-declaring the same literals.
    public static final LocalTime[] SLOT_START_TIMES = {
            LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0),
            LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(14, 30), LocalTime.of(15, 30)
    };

    /**
     * Number of slots in a school day (uniform every weekday, TASK-343 - no
     * Friday-specific reduction). Derived from SLOT_START_TIMES so callers
     * don't each independently read {@code SLOT_START_TIMES.length} as an
     * ad-hoc "slots per day" value.
     */
    public static final int SLOTS_PER_DAY = SLOT_START_TIMES.length;

    /**
     * Public so REST-facing schema endpoints (SlotConfigResource, TASK-358) can
     * derive the lunch-break window from the same source instead of
     * re-declaring the literal 14:00-14:30 boundary.
     */
    public static final LocalTime LUNCH_BREAK_START = LocalTime.of(14, 0);
    public static final LocalTime LUNCH_BREAK_END = LocalTime.of(14, 30);

    /**
     * First afternoon slot index - the boundary M28 (BaseConstraintProvider,
     * TASK-372) uses to reserve afternoon slots for stage. DERIVED as the first
     * slot whose start is at or after {@link #LUNCH_BREAK_END} (14:30), so a
     * change to the slot schema or lunch window can't silently desync it. Was a
     * literal 6 before TASK-436 (audit #4): correct today only because slot 6
     * happens to be 14:30. Defined after LUNCH_BREAK_END so the derivation sees
     * an initialized boundary.
     */
    public static final int AFTERNOON_START_SLOT_INDEX = firstSlotAtOrAfter(LUNCH_BREAK_END);

    private static int firstSlotAtOrAfter(LocalTime boundary) {
        for (int i = 0; i < SLOT_START_TIMES.length; i++) {
            if (!SLOT_START_TIMES[i].isBefore(boundary)) {
                return i;
            }
        }
        return SLOT_START_TIMES.length;
    }

    /**
     * Single source of truth for the lunch-break duration (14:00-14:30), used
     * wherever a gap calculation needs to subtract the structural break instead
     * of counting it as teaching-gap minutes (M15/S1 in SchoolScheduleConstraintProvider).
     */
    public static final int LUNCH_BREAK_MINUTES =
            (int) Duration.between(LUNCH_BREAK_START, LUNCH_BREAK_END).toMinutes();

    // O(1) reverse lookup for slotIndexOf(), built once from SLOT_START_TIMES.
    private static final Map<LocalTime, Integer> SLOT_INDEX_BY_START = buildSlotIndexByStart();

    private static Map<LocalTime, Integer> buildSlotIndexByStart() {
        Map<LocalTime, Integer> map = new HashMap<>();
        for (int i = 0; i < SLOT_START_TIMES.length; i++) {
            map.put(SLOT_START_TIMES[i], i);
        }
        return map;
    }

    /**
     * Canonical slot index (0-7) for a timeslot, derived from its start time.
     * Used as the classifier for {@code toConsecutiveSequences()} in M15/H17/S1
     * so that gap detection is schema-driven instead of duplicated hour arithmetic.
     * Returns -1 if the start time doesn't match a known slot start (defensive).
     *
     * <p>Static, schema-absolute: this is the slot's position (0-7) in the
     * canonical 8-slot-per-day schema above, independent of any particular day's
     * actual timeslot list. Not to be confused with the instance method
     * {@link #getSlotIndex(Timeslot)}, which returns the timeslot's position
     * within one day's actual (possibly partial) timeslot list.
     */
    public static int slotIndexOf(Timeslot ts) {
        if (ts == null || ts.startDateTime == null) return -1;
        Integer index = SLOT_INDEX_BY_START.get(ts.startDateTime.toLocalTime());
        return index != null ? index : -1;
    }

    /**
     * True if two timeslots on the same day are adjacent for gap-detection purposes:
     * either literally back-to-back, or separated only by the 14:00-14:30 lunch
     * break (slot 5 -> slot 6). Lunch is not a scheduling gap.
     */
    public static boolean isAdjacent(Timeslot a, Timeslot b) {
        if (a == null || b == null || a.startDateTime == null || b.startDateTime == null) return false;
        Timeslot first = a.startDateTime.isBefore(b.startDateTime) ? a : b;
        Timeslot second = (first == a) ? b : a;
        if (first.endDateTime.equals(second.startDateTime)) return true;
        // Exact lunch-break transition only (slot 5 end -> slot 6 start), NOT any
        // larger gap that merely contains lunch (that would wrongly treat a real
        // missing slot, e.g. slot 4 -> slot 6, as adjacent). Full LocalDateTime
        // equality on the second check keeps this date-safe on its own, without
        // needing a separate same-day guard (a cross-date pair can never satisfy
        // both the LocalTime match on the break start AND the exact datetime match
        // on second.startDateTime).
        return first.endDateTime.toLocalTime().equals(LUNCH_BREAK_START)
                && second.startDateTime.equals(first.endDateTime.plusMinutes(LUNCH_BREAK_MINUTES));
    }

    /**
     * True if two timeslots have a real scheduling gap between them (same day,
     * not adjacent, and not merely separated by the lunch break). Exact negation
     * of {@link #isAdjacent}, exposed as its own method (TASK-341 spec) so callers
     * that reason in terms of "is there a gap" don't have to double-negate.
     */
    public static boolean isGap(Timeslot a, Timeslot b) {
        if (a == null || b == null) return false;
        if (!a.getDate().equals(b.getDate())) return false;
        return !isAdjacent(a, b);
    }

    // WeekFields.ISO (not WeekFields.of(Locale.getDefault())) deliberately:
    // the latter varies by JVM default locale (e.g. en_US uses a Sunday-first
    // week with a 1-day minimum in the first week, unlike true ISO-8601's
    // Monday-first/4-day-minimum rule) - that difference only shows up at a
    // year boundary, which every school year here straddles (Sep-Jun), and
    // would otherwise make week-grouped constraints (M24/M25/S12) and this
    // class's own callers environment-dependent instead of deterministic.
    // WeekFields.ISO also matches Python's date.isocalendar(), which the
    // golden-data cross-check scripts use. Hoisted to a constant (not built
    // per call) since callers may invoke this from a hot constraint-stream
    // evaluation path.
    private static final java.time.temporal.WeekFields ISO_WEEK_FIELDS = java.time.temporal.WeekFields.ISO;

    /**
     * ISO week key combining week-based-year and week-of-year into a single
     * comparable int (year*100+week - ISO weeks never exceed 53, so this
     * never collides), so a groupBy doesn't collide weeks across a year
     * boundary (e.g. week 1 of 2027 vs week 1 of 2026 both reporting "week 1"
     * if only weekOfWeekBasedYear() were used). Single source of truth for
     * every ISO-week grouping in the solver (constraint streams and CH
     * phases alike) so the year-boundary handling above only exists once.
     */
    public static int isoWeekKey(LocalDate date) {
        return date.get(ISO_WEEK_FIELDS.weekBasedYear()) * 100 + date.get(ISO_WEEK_FIELDS.weekOfWeekBasedYear());
    }

    /**
     * True if the given time range [gapStart, gapEnd) fully contains the
     * 14:00-14:30 lunch break — i.e. the elapsed real-time gap includes lunch
     * and should have the 30 structural minutes subtracted before being reported
     * as a teaching gap (used by M15/S1 minute-based gap penalties).
     */
    public static boolean isLunchBreakWithinGap(LocalDateTime gapStart, LocalDateTime gapEnd) {
        LocalTime s = gapStart.toLocalTime();
        LocalTime e = gapEnd.toLocalTime();
        return !s.isAfter(LUNCH_BREAK_START) && !e.isBefore(LUNCH_BREAK_END);
    }

    private final Map<LocalDate, List<Timeslot>> slotsByDay;

    public TimeslotSequence(List<Timeslot> allTimeslots) {
        slotsByDay = new HashMap<>();
        for (Timeslot ts : allTimeslots) {
            slotsByDay.computeIfAbsent(ts.getDate(), k -> new ArrayList<>()).add(ts);
        }
        // Sort each day's slots by startDateTime
        for (List<Timeslot> daySlots : slotsByDay.values()) {
            daySlots.sort(Comparator.comparing(ts -> ts.startDateTime));
        }
    }

    /**
     * Get the Nth slot after a base timeslot on the same day.
     * @param base the starting timeslot
     * @param offset how many slots forward (1 = next slot, 2 = two slots forward, etc.)
     * @return the timeslot at the offset, or null if out of range
     */
    public Timeslot getTimeslotAtOffset(Timeslot base, int offset) {
        if (base == null) return null;
        List<Timeslot> daySlots = slotsByDay.get(base.getDate());
        if (daySlots == null) return null;

        int baseIndex = indexOf(daySlots, base);
        if (baseIndex < 0) return null;

        int targetIndex = baseIndex + offset;
        if (targetIndex < 0 || targetIndex >= daySlots.size()) return null;
        return daySlots.get(targetIndex);
    }

    /**
     * Get a list of N consecutive timeslots starting from the given timeslot.
     * @param start the starting timeslot
     * @param count how many timeslots (including start)
     * @return list of consecutive timeslots, or null if not enough slots available
     */
    public List<Timeslot> getConsecutiveTimeslots(Timeslot start, int count) {
        if (start == null || count <= 0) return null;
        List<Timeslot> daySlots = slotsByDay.get(start.getDate());
        if (daySlots == null) return null;

        int startIndex = indexOf(daySlots, start);
        if (startIndex < 0) return null;
        if (startIndex + count > daySlots.size()) return null;

        return daySlots.subList(startIndex, startIndex + count);
    }

    /**
     * Get the 0-based index of a timeslot within its day's actual timeslot list
     * (instance method, distinct from the static schema-absolute {@link #slotIndexOf}).
     * @return index (0-7 typically), or -1 if not found
     */
    public int getSlotIndex(Timeslot ts) {
        if (ts == null) return -1;
        List<Timeslot> daySlots = slotsByDay.get(ts.getDate());
        if (daySlots == null) return -1;
        return indexOf(daySlots, ts);
    }

    /**
     * Check if a given slot index is a valid start position for a block of the given size.
     * E.g., with 8 slots per day: blockSize 4 → valid starts 0-4, blockSize 5 → valid starts 0-3
     */
    public boolean isValidBlockStart(Timeslot ts, int blockSize) {
        if (ts == null || blockSize <= 0) return false;
        List<Timeslot> daySlots = slotsByDay.get(ts.getDate());
        if (daySlots == null) return false;
        int idx = indexOf(daySlots, ts);
        return idx >= 0 && idx + blockSize <= daySlots.size();
    }

    private int indexOf(List<Timeslot> daySlots, Timeslot ts) {
        for (int i = 0; i < daySlots.size(); i++) {
            if (daySlots.get(i).id.equals(ts.id)) {
                return i;
            }
        }
        return -1;
    }
}
