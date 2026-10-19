package com.school.planner.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TASK-449 (SPIKE 2): pure, unit-testable planner for the <b>mono-room-tag pre-assignment
 * pin</b>. Mirror of {@link ConstrainedTeacherAllocator} in style and record shape, generalized
 * from a squeezed TEACHER to a squeezed ROOM.
 *
 * <p>A "mono-room tag" is a lab room tag backed by exactly ONE usable room (acconciatura,
 * termoidraulica, meccanica in the 2026/27 seed). The room is the scarce resource: several
 * groups' same-tag lab blocks all require that single room, so they collide on it. Left to the
 * solver, the over-subscription surfaces as room-null lab hours (the documented residual). This
 * allocator distributes each group's same-tag lab blocks onto DISJUNCT (weekday, slotIndex) cells
 * so the single room is never double-booked, following the golden's own weekly cadence.
 *
 * <p><b>Why disjunct cells:</b> the phase stamps each planned cell across every matching school
 * date (every Tuesday at slot 0, etc.). If two groups shared a (weekday, slotIndex) cell, EVERY
 * stamp date would carry a room conflict. So the allocator gives each (weekday, slotIndex) to at
 * most one group per tag. Disjunctness is tracked at the individual-SLOT level (a block of H hours
 * starting at slotIndex claims slots slotIndex..slotIndex+H-1), which is the precise room-sharing
 * invariant: one group per slot per weekday per tag.
 *
 * <p><b>Why the golden cadence:</b> the golden ({@code seed/lessons.csv}) is the school's own
 * hand-made timetable, so its (weekday, slotIndex, block-size) pattern for each (group, tag) is
 * the authoritative template. The allocator honors it, and when inter-group collisions force a
 * choice, the losing group's block becomes residual (the pattern-exhaustion "explicit exception"
 * left to the solver, never an invented cell).
 *
 * <p>Pure (no JPA, no Timefold, keyed by group NAME); the phase ({@link HybridPhase1aService})
 * consumes a {@link Plan} and does the DB writes, validating each pin with
 * {@code PreAssignmentValidator.canPinHardConstraints} before persisting.
 */
public final class MonoRoomTagAllocator {

    private MonoRoomTagAllocator() {
        // Pure utility - no instances
    }

    /** A golden-derived weekly block: {@code hours} consecutive slots starting at {@code slotIndex} on {@code weekday}. */
    public record Cell(DayOfWeek weekday, int slotIndex, int hours) {
    }

    /**
     * A single pin target: pin a block of {@code hours} lessons for group {@code groupName} on
     * every school date matching {@code weekday}, starting at {@code slotIndex}, in the room
     * carrying tag {@code roomTag}. The phase stamps it across dates until the group's lab-lesson
     * pool for the tag is exhausted.
     */
    public record PinTarget(String groupName, DayOfWeek weekday, int slotIndex, int hours, String roomTag) {
        /** Stable key for dedup / idempotency accounting. */
        public String key() {
            return groupName + "|" + weekday + "|" + slotIndex + "|" + roomTag;
        }
    }

    /**
     * Result of planning one mono-room tag's pin allocation.
     *
     * @param targets            validated, mutually-disjunct pin targets
     * @param warnings           human-readable diagnostics (collisions, H7 clamps, residuals)
     * @param weeklyHoursByGroup weekly block-hours this plan newly assigns per group (the sum of
     *                           accepted cell block-sizes, NOT the total stampable hours)
     * @param residualHoursByGroup estimated hours of each group's tag-demand the plan leaves to
     *                           the solver ({@code max(0, demand - weeklyAssigned * teachingWeeks)};
     *                           an estimate because the actual pinned total depends on how many
     *                           school dates the phase can stamp at 0hard)
     */
    public record Plan(
            List<PinTarget> targets,
            List<String> warnings,
            Map<String, Integer> weeklyHoursByGroup,
            Map<String, Integer> residualHoursByGroup) {
    }

    /**
     * Build the disjunct pin plan for one mono-room tag from the golden's weekly cadence.
     *
     * <p>Per group (sorted name), assigns cells from its cadence (caller-sorted, dominant first)
     * until the group's weekly demand rate is covered. Each cell must be fully slot-disjunct from
     * every previously accepted cell (across all groups for this tag); a colliding cell is skipped
     * with a warning, and the group tries its next-dominant cell. {@code hours} is clamped to
     * {@code h7Cap} (the per-teacher daily cap, a safety net; the real per-group H11 is enforced
     * by the phase's {@code canPinHardConstraints}).
     *
     * @param cadence       groupName &rarr; cadence cells (dominant-first), the golden's weekly
     *                      (weekday, startSlot, blockHours) pattern for that (group, tag)
     * @param demandByGroup groupName &rarr; total lab hours requiring this tag (the lesson pool)
     * @param h7Cap         max daily hours a teacher may teach (clamp ceiling)
     * @param roomTag       the mono-room tag these cells belong to
     * @param teachingWeeks the number of teaching weeks in the year (drives the weekly rate:
     *                      {@code ceil(demand / teachingWeeks)} hours/week the group needs)
     */
    public static Plan plan(
            Map<String, List<Cell>> cadence,
            Map<String, Integer> demandByGroup,
            int h7Cap,
            String roomTag,
            int teachingWeeks) {

        List<PinTarget> targets = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> weeklyHoursByGroup = new LinkedHashMap<>();
        // Slot-level disjunctness: one group per (weekday, slotIndex) per tag. A block claims
        // every slot in its range, so two blocks starting at different slots but overlapping in
        // range are correctly recognized as a room conflict.
        Set<String> claimedSlots = new HashSet<>();

        if (cadence == null || cadence.isEmpty()) {
            return new Plan(targets, warnings, weeklyHoursByGroup, residualAll(demandByGroup, Map.of(), 0));
        }

        int weeks = teachingWeeks > 0 ? teachingWeeks : 1;

        List<String> groups = new ArrayList<>(cadence.keySet());
        Collections.sort(groups);
        for (String group : groups) {
            int demand = demandByGroup == null ? 0 : demandByGroup.getOrDefault(group, 0);
            int weeklyNeeded = (int) Math.ceil((double) demand / weeks);
            int weeklyAssigned = 0;

            List<Cell> cells = cadence.get(group);
            if (cells == null) {
                weeklyHoursByGroup.put(group, 0);
                continue;
            }
            for (Cell cell : cells) {
                if (weeklyAssigned >= weeklyNeeded) {
                    break; // this group's weekly rate is covered
                }
                int hours = Math.min(cell.hours(), h7Cap);
                if (hours <= 0) {
                    continue;
                }

                // Slot-range disjunctness: the block occupies slots [slotIndex, slotIndex+hours-1].
                // Reject if ANY slot is already claimed by another group (room conflict).
                String clash = findClash(claimedSlots, cell.weekday(), cell.slotIndex(), hours);
                if (clash != null) {
                    warnings.add(String.format(
                            "Collision: %s cell %s slot %d-%d already claimed (%s); skipped",
                            group, cell.weekday(), cell.slotIndex(), cell.slotIndex() + hours - 1, clash));
                    continue;
                }

                // Claim every slot in the range.
                for (int s = cell.slotIndex(); s < cell.slotIndex() + hours; s++) {
                    claimedSlots.add(cellKey(cell.weekday(), s));
                }
                targets.add(new PinTarget(group, cell.weekday(), cell.slotIndex(), hours, roomTag));
                weeklyAssigned += hours;
            }
            weeklyHoursByGroup.put(group, weeklyAssigned);
            if (weeklyAssigned < weeklyNeeded) {
                warnings.add(String.format(
                        "Weekly shortfall: %s needs %dh/week but only %dh assigned (remaining cells collided)",
                        group, weeklyNeeded, weeklyAssigned));
            }
        }

        Map<String, Integer> residual = residualAll(demandByGroup, weeklyHoursByGroup, weeks);
        for (Map.Entry<String, Integer> e : residual.entrySet()) {
            if (e.getValue() > 0) {
                warnings.add(String.format(
                        "Residual: ~%dh of %s %s demand left to the solver (explicit exception)",
                        e.getValue(), e.getKey(), roomTag));
            }
        }

        return new Plan(targets, warnings, weeklyHoursByGroup, residual);
    }

    /** Returns the claimant key if any slot in [slotIndex, slotIndex+hours-1] is already claimed, else null. */
    private static String findClash(Set<String> claimed, DayOfWeek weekday, int slotIndex, int hours) {
        for (int s = slotIndex; s < slotIndex + hours; s++) {
            if (claimed.contains(cellKey(weekday, s))) {
                return cellKey(weekday, s);
            }
        }
        return null;
    }

    private static String cellKey(DayOfWeek weekday, int slotIndex) {
        return weekday + "|" + slotIndex;
    }

    private static Map<String, Integer> residualAll(
            Map<String, Integer> demand, Map<String, Integer> weeklyAssigned, int weeks) {
        Map<String, Integer> residual = new LinkedHashMap<>();
        if (demand != null) {
            for (Map.Entry<String, Integer> e : demand.entrySet()) {
                int assigned = weeklyAssigned.getOrDefault(e.getKey(), 0);
                int stampable = assigned * weeks;
                residual.put(e.getKey(), Math.max(0, e.getValue() - stampable));
            }
        }
        return residual;
    }
}
