package com.school.planner.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import com.school.planner.util.SubjectNormalization;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Per-group, per-day activity hour budget (TASK-381 "L1-bis"; widened to all
 * 14 groups by TASK-385 - see below). Originally sourced ONLY from the
 * school's authoritative "DEF FC TUTTI" sheet ({@code
 * src/main/resources/seed/calendario_fc.csv}: group, date, aula_hours,
 * lab_hours, stage_hours) for G04 / G08 / G13 (990h each; {@link
 * #FCQuota} {@code == true} rows). Unlike {@link GroupCalendarRule} (which
 * of course only says a slot is OPEN or CLOSED), this entity carries the
 * sheet's actual per-activity-type hour split for that day - the
 * search-space restriction L1-bis adds on top of the calendar: a
 * quota-bound lab subject may only land on a day with {@code labHours &gt;
 * 0}, and every other subject needs {@code aulaHours &gt; 0} (see {@link
 * com.school.planner.domain.Lesson#getAvailableTimeslots()}'s VRP filter and
 * {@code H23}/{@code H24}/{@code M30}/{@code M31} in {@code
 * BaseConstraintProvider}, which all consult this same table via {@link
 * com.school.planner.domain.SchoolSchedule}'s static snapshot - single
 * source of truth, same anti-drift rationale as M15/H17's javadoc).
 *
 * <p>An absent row for an {@link #FCQuota} group's date means a fully
 * closed/zero day (no aula/lab/stage hours at all) - mirrors {@link
 * GroupCalendarRule}'s "no rule means fully open" EXCEPT inverted: here,
 * silence means zero, not unlimited. A group with zero {@code FCQuota ==
 * true} rows (every group except G04/G08/G13, see {@link
 * com.school.planner.domain.SchoolSchedule#isQuotaGroup}) is unrestricted by
 * L1-bis - the VRP filter and H23/H24/M30/M31 are no-ops for it.</p>
 *
 * <p><b>TASK-385 widening.</b> {@code DataGenerator#generateExpectedDailyHours}
 * seeds a SECOND kind of row, for every OTHER group (from {@code
 * calendario_class_days.csv}), flagged {@link #FCQuota} {@code == false}: a
 * plain per-date expected-TOTAL-hours fact (no lab/aula L1-bis restriction
 * semantics), read only via {@link
 * com.school.planner.domain.SchoolSchedule#getExpectedHours} (M20's daily
 * quota target, {@code WeeklyTemplateBuilder}'s day-shape target, {@code
 * DayClusteredConstructionPhase}'s stamp fill goal). So "this table has a
 * row for (group, date)" is no longer equivalent to "group is L1-bis
 * restricted" - every consumer that means the LATTER must filter on {@link
 * #FCQuota} (or go through {@code SchoolSchedule#isQuotaGroup}/{@code
 * getDailyQuota}, which already do). As of this writing the consumers that
 * must stay {@code FCQuota}-scoped are: {@code
 * BaseConstraintProvider}'s {@code POSITIVE_LAB_HOURS} predicate (M30.1/
 * M30.2, which read {@code forEach(DailyActivityQuota.class)} directly off
 * the problem facts - the ONLY two consumers that bypass {@code
 * SchoolSchedule}'s static maps entirely), {@code SchoolSchedule}'s own
 * {@code dailyActivityQuotasByGroupStatic} map (backs {@code isQuotaGroup}/
 * {@code getDailyQuota}/{@code getDailyActivityQuotasForGroup}, hence the
 * VRP filter/H23/H24/M31), and {@code CalendarioImportService#buildStageAsks}'s
 * {@code quotaGroupIds} derivation (decides whether a group's Stage hours
 * are read from this table or from {@code calendario_class_days.csv}'s
 * {@code modality=S} rows directly). Check this list before trusting "any
 * row exists" anywhere new.</p>
 */
@Entity
public class DailyActivityQuota extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public StudentGroup studentGroup;

    public LocalDate date;

    /** Ordinary classroom ("aula") hours the sheet budgets for this (group, date). */
    public int aulaHours;

    /** Lab ("laboratorio") hours the sheet budgets for this (group, date). */
    public int labHours;

    /** Stage (internship) hours the sheet budgets for this (group, date). */
    public int stageHours;

    /**
     * TASK-385: true for rows sourced from calendario_fc.csv (the ORIGINAL 3
     * groups this entity was built for - G04/G08/G13); false for rows
     * added later from calendario_class_days.csv (the other 11 groups' plain
     * per-date expected-hours facts, {@code DataGenerator#generateExpectedDailyHours}).
     * H23/H24/M30/M31 and the aula/lab VRP filter in {@code
     * Lesson#getAvailableTimeslots()} all stay scoped to {@code FCQuota ==
     * true} rows (via {@link com.school.planner.domain.SchoolSchedule#isQuotaGroup}/
     * {@code getDailyQuota} and {@code BaseConstraintProvider}'s own {@code
     * POSITIVE_LAB_HOURS} filter) - widening the raw row COUNT to all 14
     * groups must never widen what "quota group" means to those five
     * consumers. The universal per-(group,date) expected-hours fact (every
     * row, regardless of this flag) is read separately via {@link
     * com.school.planner.domain.SchoolSchedule#getExpectedHours}.
     *
     * <p>{@code columnDefinition} default TRUE (code-review fix; {@code ValidationSnapshot} is this codebase's precedent for
     * using the {@code columnDefinition} attribute, though for a type override
     * rather than a DEFAULT clause): under production's {@code
     * quarkus.hibernate-orm.schema-management.strategy=update} (never
     * drop-and-create, see CLAUDE.md), this new column is ADDED to any
     * already-existing profile's populated table - every row that already
     * existed before this field was introduced is, by definition, an
     * FC-sourced row (the only seeder before TASK-385), so a SQL-level
     * default of {@code true} backfills them correctly without requiring a
     * profile reset. Without it, existing rows would read back as either an
     * unmarshalling failure or a silent {@code false} (JDBC's {@code
     * getBoolean} returns {@code false} for a SQL NULL) - either way,
     * misclassifying G04/G08/G13's legacy rows out of H23/H24/M30/M31's
     * scope until reseeded.</p>
     */
    @Column(columnDefinition = "boolean default true")
    public boolean FCQuota;

    public DailyActivityQuota() {
    }

    /** Convenience for FC-sourced rows ({@code FCQuota = true}) - see {@link #FCQuota}'s own javadoc. */
    public DailyActivityQuota(StudentGroup studentGroup, LocalDate date, int aulaHours, int labHours, int stageHours) {
        this(studentGroup, date, aulaHours, labHours, stageHours, true);
    }

    public DailyActivityQuota(StudentGroup studentGroup, LocalDate date, int aulaHours, int labHours, int stageHours,
                               boolean FCQuota) {
        this.studentGroup = studentGroup;
        this.date = date;
        this.aulaHours = aulaHours;
        this.labHours = labHours;
        this.stageHours = stageHours;
        this.FCQuota = FCQuota;
    }

    /**
     * Golden-empirical QUOTA-BOUND lab subject sets per group (measured
     * against {@code src/main/resources/seed/lessons.csv}, the 2026/27
     * golden timetable): the sheet's "lab" hours do NOT correspond to {@code
     * Course.isLab} - these are the subjects whose demand the school
     * actually fills against the sheet's lab-hour budget, not every
     * lab-tagged course.
     *
     * <ul>
     *   <li><b>G04</b>: {@code {"Lavorazioni meccaniche", "Cad", "Recuperi ed
     *   Approfondimenti"}} - demand 152+20+50=222h &le; quota 245h. RA
     *   ("Recuperi ed Approfondimenti") = recupero hours at the TEACHER'S
     *   DISCRETION, typically used to extend that teacher's own other
     *   subjects (maintainer, 2026-07-20) - T15 is the officina teacher, so
     *   his RA hours extend Lavorazioni meccaniche in the workshop, which is
     *   why they track the sheet's lab days. Adding Informatica was measured
     *   WORSE (residual 61 vs 35, and breaks demand&le;quota).</li>
     *   <li><b>G08</b>: {@code {"Lavorazioni meccaniche"}} - demand 110h,
     *   quota 106h. Taglio Metalli/Cad/Informatica/Controllo numerico live on
     *   non-lab days in the golden - binding them would be infeasible (264h
     *   demand vs 106h quota).</li>
     *   <li><b>G13</b>: {@code {"Lavorazioni meccaniche", "Tecniche di
     *   saldatura", "Cad"}} - demand 110+30+40+30=210h = quota 210h exact.
     *   G13 has TWO "Lavorazioni meccaniche" courses (T10 110h + T15
     *   30h) - the set matches by SUBJECT string, so both are bound.</li>
     * </ul>
     *
     * <p>Golden quota-fill residual (M30, {@code GoldenSolutionRegressionTest}):
     * &Sigma;|placed&minus;N| over in-horizon lab days = G04 35 (10 mismatch
     * days, incl. 8 empty lab days), G08 4 (1 day), G13 4 (1 day) -
     * TOTAL -43 medium / 12 matches. G04's bound demand (222h) is below its
     * quota (245h) by construction, so at least 23h of that residual is a
     * structural floor no future solve can close (the school fills that gap
     * with Informatica/other non-bound hours - reported to the school, see
     * {@code claudedocs/spalmone-2026-27/domande-brevi.md}).</p>
     */
    public static final Map<String, Set<String>> QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP = Map.of(
            "G04", boundSet("Lavorazioni meccaniche", "Cad", "Recuperi ed Approfondimenti"),
            "G08", boundSet("Lavorazioni meccaniche"),
            "G13", boundSet("Lavorazioni meccaniche", "Tecniche di saldatura", "Cad"));

    /**
     * Normalizes the literals through the same {@link
     * SubjectNormalization#normalize} the lookup side applies, so a future
     * addition to the case-normalization table can never silently un-bind a
     * subject by making the normalized lookup key diverge from a raw literal.
     */
    private static Set<String> boundSet(String... subjects) {
        return java.util.Arrays.stream(subjects)
                .map(SubjectNormalization::normalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * True if {@code subject} is one of {@code groupName}'s quota-bound lab
     * subjects ({@link #QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP}). Subjects are
     * matched via {@link SubjectNormalization#normalize} (same normalization
     * M21/H30 already apply) so seed-data casing/whitespace variance can't
     * silently un-bind a subject. Shared by the VRP filter
     * ({@code Lesson.getAvailableTimeslots()}) and the H23/M30/M31
     * constraints - single source of truth, see class javadoc.
     */
    public static boolean isQuotaBoundLabSubject(String groupName, String subject) {
        if (groupName == null || subject == null) return false;
        Set<String> subjects = QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP.get(groupName);
        return subjects != null && subjects.contains(SubjectNormalization.normalize(subject));
    }

    /**
     * The ONE composed "does this day's quota permit this kind of lesson?"
     * decision: a quota-bound lab subject needs {@code labHours > 0}, every
     * other subject needs {@code aulaHours > 0}; an absent row ({@code quota
     * == null}) means a fully closed/zero day (see class javadoc). Called
     * positively by the VRP filter ({@code Lesson.getAvailableTimeslots()})
     * and negated by H23/H24 - same single-decision-method depth as
     * {@code GroupCalendarService.isOpen} in the H20 precedent, so the
     * search-space pruner and its safety-net constraints can never drift
     * apart on the threshold logic.
     */
    public static boolean permits(DailyActivityQuota quota, boolean quotaBoundLabSubject) {
        if (quota == null) return false;
        return quotaBoundLabSubject ? quota.labHours > 0 : quota.aulaHours > 0;
    }

    /**
     * True if {@code row} represents a stage-open day ({@code stageHours &gt;
     * 0}) - {@code null} (no row for that date) means a non-stage day, same
     * "absent row means zero" convention as {@link #permits}. TASK-384: the
     * ONE decision method shared by {@link
     * com.school.planner.domain.Lesson#isStageConfined()}'s VRP confinement
     * filter and its H-tier safety net ({@code BaseConstraintProvider}), so
     * the two can never drift on what counts as a stage day - same
     * single-decision-method precedent as {@link #permits}/{@code
     * GroupCalendarService.isOpen}.
     */
    public static boolean isStageDate(DailyActivityQuota row) {
        return row != null && row.stageHours > 0;
    }

    /**
     * True if {@code row} is a STAGE-ONLY day: stage hours and no ordinary hours
     * (aula = lab = 0). The "school's stage day" gate shared by TASK-604's M39
     * preference and the weekly-template planner's stage-weekday vote exclusion
     * (TASK-600), same single-decision-method discipline as {@link #isStageDate}.
     */
    public static boolean isStageOnlyDay(DailyActivityQuota row) {
        return isStageDate(row) && row.aulaHours == 0 && row.labHours == 0;
    }

    @Override
    public String toString() {
        return studentGroup + " " + date + " [aula=" + aulaHours + " lab=" + labHours + " stage=" + stageHours + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DailyActivityQuota other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
