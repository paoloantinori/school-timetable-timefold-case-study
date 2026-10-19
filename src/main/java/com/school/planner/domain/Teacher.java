package com.school.planner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import ai.timefold.solver.core.api.domain.common.PlanningId;

@Entity
public class Teacher extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    public Long id;

    public String name;

    /**
     * Optional display name / nickname for the teacher.
     * If set, this is shown instead of the full name in the UI.
     * Max 20 characters.
     */
    @Column(length = 20)
    public String nickname;

    /**
     * Custom color for teacher display in timeline (hex format, e.g., "#ec4899").
     * If null, the frontend uses subject-based coloring.
     */
    @Column(length = 7)
    public String color;

    /**
     * Visual pattern for teacher identity in timeline (e.g., "plain", "dotted", "hatched", "striped").
     * Combined with color, creates up to 40 unique visual identities for teachers.
     */
    @Column(length = 10)
    public String pattern;

    /**
     * External teacher flag (e.g., T28 who only teaches Thursdays).
     * External teachers' lessons are exempt from H11 daily hour limits.
     * See: claudedocs/t28-exception-plan.md
     */
    public boolean external = false;

    /**
     * Marks a synthetic accounting teacher (e.g. "Stage", "Esame", "RA" -
     * see {@code GoldenScheduleImportService}, TASK-346) that carries hours
     * for reconciliation but never actually teaches a conflicting slot.
     * H1 (teacher conflict), H7 (max teacher daily hours) and S1 (minimize
     * teacher gaps) filter out lessons whose teacher is pseudo, since two
     * different groups' pseudo-lessons (e.g. Stage) legitimately overlap in
     * the same real-world slot without any actual person being double-booked.
     * Group-side constraints (H3, H11, H17) are unaffected and must still see
     * these lessons.
     */
    public boolean pseudo = false;

    /**
     * Default H7 max daily hours cap, applied when {@link #maxDailyHours} is null.
     */
    public static final int DEFAULT_MAX_DAILY_HOURS = 6;

    /**
     * Per-teacher override of H7's max daily hours cap.
     * null = default ({@link #DEFAULT_MAX_DAILY_HOURS}, 6).
     * Used for teachers with a genuinely longer real-world day (e.g. T01's
     * 8h full-day lab crossing lunch, analysis.md section 4.2, TASK-348).
     *
     * <p><b>Known gap (pre-dates TASK-368, inherited by the two fields below):</b>
     * {@code ImportService.insertTeachers()}'s A/B-testing schedule
     * import/export UPSERT does not list this column (nor
     * {@link #maxEarlyStartDaysPerWeek}/{@link #requiredFreeDaysPerWeek}), so
     * a Teacher inserted for the first time via that path (not already seeded)
     * would get {@code null} regardless of the in-memory value. Existing rows
     * are unaffected (Postgres leaves unlisted columns alone on
     * {@code ON CONFLICT DO UPDATE}). Not fixed here - out of TASK-368's scope,
     * a pre-existing gap in a different feature (A/B import/export, not the
     * seed/CRUD path these three fields are wired through).</p>
     */
    public Integer maxDailyHours;

    /**
     * Per-teacher cap on distinct days/week the teacher may start at slot 0
     * (08:00). null = no cap (unconstrained). Models a "one day a week may
     * start early" choice-day clause (e.g. T11, "9-14 sempre e un giorno
     * alle 8.00" - {@code vincoli-in-parole.md}, TASK-368) that a static
     * {@link RecurringAvailabilityRule} can't express, since the day is the
     * SOLVER's choice, not a fixed weekday. Enforced by M24 in
     * {@code SchoolScheduleConstraintProvider}.
     */
    public Integer maxEarlyStartDaysPerWeek;

    /**
     * Per-teacher requirement: at least this many Mon-Fri days/week must have
     * zero lessons for the teacher. null = no requirement. Models a "must have
     * one day fully free" choice-day clause (e.g. T05, "un giorno a scelta
     * deve essere lasciato senza ore" - {@code vincoli-in-parole.md},
     * TASK-368). Enforced by M25 in {@code SchoolScheduleConstraintProvider}.
     *
     * <p><b>T05 is seeded 2, not the docx's literal 1:</b> T05's Friday is
     * already a hard full-day {@link RecurringAvailabilityRule} VACATION
     * block (H5), so Friday is unconditionally a zero-lesson day regardless
     * of anything M25 does - counting Mon-Fri with a requirement of 1 would
     * always be satisfied by Friday alone and never actually constrain Mon-Thu,
     * making the rule a no-op for him. 2 (Friday's guaranteed free day + one
     * more, solver-chosen, among Mon-Thu) is the value that actually enforces
     * the docx's intent ("un giorno a scelta ... per poter fare altro").</p>
     */
    public Integer requiredFreeDaysPerWeek;

    public Teacher() {
    }

    public Teacher(String name) {
        this.name = name;
    }

    /**
     * Returns {@link #maxDailyHours} if set, otherwise {@link #DEFAULT_MAX_DAILY_HOURS}.
     * Single source of truth for H7's per-teacher cap resolution - used both by
     * the constraint stream (SchoolScheduleConstraintProvider) and its per-lesson
     * UI mirror (ScheduleService.addH7Violations), so the two can't silently drift.
     */
    public int getEffectiveMaxDailyHours() {
        return maxDailyHours != null ? maxDailyHours : DEFAULT_MAX_DAILY_HOURS;
    }

    /**
     * Returns the nickname if set, otherwise the full name.
     * Used for display purposes in the UI.
     */
    public String getDisplayName() {
        return nickname != null && !nickname.isBlank() ? nickname : name;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * ID-based equality for use in Timefold Joiners.
     * Falls back to identity comparison for transient entities (null id).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Teacher other)) return false;
        return id != null && id.equals(other.id);
    }

    /**
     * Consistent hashCode based on id.
     * Uses class hashCode for transient entities to satisfy JPA requirements.
     */
    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
