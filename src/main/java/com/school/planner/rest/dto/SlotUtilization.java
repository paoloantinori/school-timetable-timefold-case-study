package com.school.planner.rest.dto;

/**
 * Slot utilization data for a student group.
 * Shows how many valid slots are available vs how many are assigned.
 *
 * Used to identify sparse calendars at a glance - groups with many
 * unassigned valid slots may have compaction issues.
 */
public class SlotUtilization {
    /** Student group ID */
    public Long groupId;

    /** Student group name */
    public String groupName;

    /** Total number of valid timeslots for this group (respecting filters) */
    public int totalValidSlots;

    /** Number of timeslots with assigned lessons */
    public int assignedSlots;

    /** Number of valid timeslots without lessons (totalValidSlots - assignedSlots) */
    public int unassignedSlots;

    /** Utilization percentage (assigned / total * 100) */
    public double utilizationPercentage;

    public SlotUtilization(Long groupId, String groupName, int totalValidSlots, int assignedSlots) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.totalValidSlots = totalValidSlots;
        this.assignedSlots = assignedSlots;
        this.unassignedSlots = totalValidSlots - assignedSlots;
        this.utilizationPercentage = totalValidSlots > 0
            ? (assignedSlots * 100.0 / totalValidSlots)
            : 0.0;
    }
}
