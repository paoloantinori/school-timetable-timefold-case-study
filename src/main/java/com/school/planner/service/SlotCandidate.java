package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.school.planner.domain.Timeslot;
import com.school.planner.solver.TimeslotSequence;

/**
 * Represents a candidate slot for lab block assignment.
 * Used in exhaustive combination search with priority ordering.
 *
 * Priority order:
 * 1. Preferred day + preferred slot (exact match)
 * 2. Preferred day + alternative slot
 * 3. Alternative day + preferred slot
 * 4. Alternative day + alternative slot
 */
public record SlotCandidate(
    Timeslot startSlot,      // The starting timeslot
    int blockSize,           // Number of consecutive slots needed
    boolean preferredDay,    // Matches rotation.dayOfWeek?
    boolean preferredSlot,   // Matches rotation.slotStart?
    int priority             // Lower = higher priority (1 = exact preference)
) implements Comparable<SlotCandidate> {

    @Override
    public int compareTo(SlotCandidate other) {
        // Sort by priority ascending (lower priority value = higher preference)
        int priorityCompare = Integer.compare(this.priority, other.priority);
        if (priorityCompare != 0) return priorityCompare;

        // Secondary sort by date for deterministic ordering
        if (this.startSlot != null && other.startSlot != null) {
            return this.startSlot.startDateTime.compareTo(other.startSlot.startDateTime);
        }
        return 0;
    }

    /**
     * Get the date of this candidate's start slot.
     */
    public LocalDate getDate() {
        return startSlot != null ? startSlot.getDate() : null;
    }

    /**
     * Get the day of week of this candidate's start slot.
     */
    public DayOfWeek getDayOfWeek() {
        return startSlot != null ? startSlot.getDayOfWeek() : null;
    }

    /**
     * Get the slot index (0-based hour) of this candidate's start slot.
     */
    public int getSlotIndex() {
        if (startSlot == null) return -1;
        return startSlot.startDateTime.getHour() - 8; // 08:00 = slot 0
    }

    /**
     * Calculate priority based on preference match.
     */
    public static int calculatePriority(boolean preferredDay, boolean preferredSlot) {
        if (preferredDay && preferredSlot) return 1;  // Exact preference
        if (preferredDay) return 2;                    // Same day, alt slot
        if (preferredSlot) return 3;                   // Alt day, preferred slot
        return 4;                                      // Alt day, alt slot
    }

    /**
     * Check if a block of given size fits on the day starting from this candidate's slot.
     * Uniform 8 slots every day (TASK-343) - no Friday-specific reduction.
     */
    public boolean blockFitsOnDay() {
        if (startSlot == null) return false;

        LocalTime[] slotTimes = getSlotTimes(startSlot.getDayOfWeek());
        int startSlotIndex = getSlotIndex();

        // Check if block fits within available slots
        return startSlotIndex >= 0 && startSlotIndex + blockSize <= slotTimes.length;
    }

    /**
     * Get all timeslots occupied by a block at this candidate position.
     * This queries the database for each consecutive slot.
     */
    public List<Timeslot> getOccupiedSlots() {
        List<Timeslot> occupied = new ArrayList<>();
        if (startSlot == null) return occupied;

        LocalDate date = startSlot.getDate();
        LocalTime[] slotTimes = getSlotTimes(startSlot.getDayOfWeek());
        int startSlotIndex = getSlotIndex();

        if (startSlotIndex < 0) return occupied;

        for (int i = 0; i < blockSize && startSlotIndex + i < slotTimes.length; i++) {
            LocalDateTime start = LocalDateTime.of(date, slotTimes[startSlotIndex + i]);
            Timeslot ts = Timeslot.find("startDateTime", start).firstResult();
            if (ts != null) {
                occupied.add(ts);
            }
        }
        return occupied;
    }

    /**
     * Get slot times for a given day of week. Uniform 8 slots every day
     * (TASK-343, single source of truth: TimeslotSequence.SLOT_START_TIMES) -
     * the {@code day} parameter is kept for call-site compatibility but no
     * longer changes the result.
     */
    public static LocalTime[] getSlotTimes(DayOfWeek day) {
        return TimeslotSequence.SLOT_START_TIMES;
    }

    @Override
    public String toString() {
        return String.format("SlotCandidate[date=%s, slotIndex=%d, blockSize=%d, priority=%d, preferredDay=%s, preferredSlot=%s]",
                getDate(), getSlotIndex(), blockSize, priority, preferredDay, preferredSlot);
    }
}
