package com.school.planner.domain;

/**
 * Status of the pre-assignment pipeline.
 * Used to track progress and display status in the UI.
 */
public enum PreAssignmentStatus {
    /** No pre-assignment is running */
    IDLE,
    /** Currently running acconciatura rotation assignment */
    ACCONCIATURA,
    /** Currently running lab block assignment */
    LABS,
    /** Currently running professionalizzanti assignment */
    PROFESSIONALIZZANTI,
    /** Pre-assignment completed successfully */
    COMPLETE,
    /** Pre-assignment failed with an error */
    ERROR
}
