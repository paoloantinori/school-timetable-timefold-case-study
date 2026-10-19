package com.school.planner.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * A weekly-recurring teacher availability constraint (TASK-345), e.g. "T05 is
 * unavailable Mon/Wed/Thu after 14:00" or "T24 prefers not to be scheduled
 * Tuesday mornings (equipe)". Shares {@link GroupCalendarRule}'s shape
 * (dayOfWeek + inclusive slot range + optional date-scoped window), applied to
 * a {@link Teacher} instead of a {@link StudentGroup} — but NOT its resolution
 * semantics. Rules here are ADDITIVE: every rule that matches a given
 * (teacher, date, slot) fires its own H5/S3 row independently of every other
 * matching rule — there is no "more specific rule wins" concept like
 * {@link GroupCalendarRule} has. Because an additive model has no way to
 * resolve two matching rules other than double-firing the constraint,
 * overlapping rules for the same teacher/day/slot-range/effective-window are
 * rejected outright at the API and seed boundaries (see {@link #overlaps}).
 *
 * <p>This entity is never read directly by the solver/constraints — H5
 * (teacher unavailability) and S3 (teacher preference) must stay unchanged,
 * so a rule is only a template. {@code AvailabilityExpansionService} expands
 * each rule into concrete dated {@link TeacherAvailability} rows (one per
 * matching school day), tagged via {@link TeacherAvailability#sourceRuleId}
 * so a re-expansion can find and replace exactly the rows it previously
 * generated. See {@code claudedocs/spalmone-2026-27/availability-not-yet-modeled.md}
 * for the mechanism write-up and the clauses that could NOT be expressed this
 * way.</p>
 */
@Entity
public class RecurringAvailabilityRule extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public Teacher teacher;

    public DayOfWeek dayOfWeek;

    /** Inclusive first slot index (0-7, see TimeslotSequence.SLOT_START_TIMES). */
    public int minSlot;

    /** Inclusive last slot index (0-7). A full day is minSlot=0, maxSlot=7. */
    public int maxSlot;

    /**
     * VACATION (unavailable, expands into H5-visible rows) or PREFERENCE
     * (soft avoid, expands into S3-visible rows). SICK is not meaningful for
     * a recurring rule and is rejected by the REST layer.
     */
    @Enumerated(EnumType.STRING)
    public AvailabilityType type;

    /** Null means "always" (no lower bound). */
    public LocalDate effectiveFrom;

    /** Null means "always" (no upper bound). */
    public LocalDate effectiveTo;

    /** Free-text provenance (e.g. "T04 works 8:00-13:00, analysis.md section 5"). */
    public String note;

    public RecurringAvailabilityRule() {
    }

    public RecurringAvailabilityRule(Teacher teacher, DayOfWeek dayOfWeek, int minSlot, int maxSlot,
                                     AvailabilityType type) {
        this(teacher, dayOfWeek, minSlot, maxSlot, type, null, null, null);
    }

    public RecurringAvailabilityRule(Teacher teacher, DayOfWeek dayOfWeek, int minSlot, int maxSlot,
                                     AvailabilityType type, LocalDate effectiveFrom, LocalDate effectiveTo,
                                     String note) {
        this.teacher = teacher;
        this.dayOfWeek = dayOfWeek;
        this.minSlot = minSlot;
        this.maxSlot = maxSlot;
        this.type = type;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.note = note;
    }

    /**
     * True if this rule spans the whole school day ({@code minSlot=0, maxSlot=7}), the only shape
     * that removes an entire weekday from a teacher's availability (a partial-day rule leaves the
     * uncovered slots teachable). The {@code 0..7} convention is documented at {@link #maxSlot};
     * centralized here so every consumer (H5 expansion, the TASK-452 constrained-teacher input
     * path, tests) shares one notion of "full day" instead of re-hardcoding the slot pair.
     */
    public boolean isFullDay() {
        return minSlot == 0 && maxSlot == 7;
    }

    @Override
    public String toString() {
        return teacher + " " + dayOfWeek + " [" + minSlot + "-" + maxSlot + "] " + type
                + (effectiveFrom != null || effectiveTo != null ? " (" + effectiveFrom + ".." + effectiveTo + ")" : "");
    }

    /**
     * Validates the two date-coherence invariants a rule must satisfy so it
     * never silently expands to zero rows or has an inverted window:
     * <ol>
     *   <li>if both bounds are set, {@code effectiveFrom} must not be after
     *       {@code effectiveTo};</li>
     *   <li>a single-date rule ({@code effectiveFrom.equals(effectiveTo)})
     *       must have {@code dayOfWeek} equal to that date's actual weekday —
     *       otherwise {@code AvailabilityExpansionService.schoolDaysByWeekday()}
     *       buckets the date under a weekday the rule never looks at, and the
     *       rule expands to nothing without any error.</li>
     * </ol>
     * Ranged rules (an open or multi-day window) need no such check — the
     * bucket-then-filter expansion logic already handles any weekday/window
     * combination correctly.
     *
     * <p>Public static, no DB access, directly unit-testable — see the class
     * javadoc for why this and {@link #overlaps} are both called from the
     * REST layer and {@code DataGenerator}'s seed loader.
     *
     * @return an error message if invalid, or {@code null} if the rule is coherent
     */
    public static String validateDateCoherence(DayOfWeek dayOfWeek, LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
            return "effectiveFrom (" + effectiveFrom + ") must not be after effectiveTo (" + effectiveTo + ")";
        }
        if (effectiveFrom != null && effectiveFrom.equals(effectiveTo) && dayOfWeek != effectiveFrom.getDayOfWeek()) {
            return "single-date rule (effectiveFrom=effectiveTo=" + effectiveFrom + ") must have dayOfWeek="
                    + effectiveFrom.getDayOfWeek() + " (that date's actual weekday), got " + dayOfWeek
                    + " instead — otherwise the rule silently expands to zero rows";
        }
        return null;
    }

    /**
     * True if {@code candidate} and {@code other} would both fire for the
     * same (teacher, date, slot) — i.e. same {@code dayOfWeek}, intersecting
     * slot ranges, and intersecting effective-date windows. Callers are
     * responsible for restricting the comparison to the same teacher (e.g. via
     * {@link #listByTeacher}) — this method itself doesn't look at
     * {@code teacher} so it stays pure and unit-testable without persisting
     * anything.
     *
     * <p>Deliberately type-agnostic (VACATION vs PREFERENCE): H5's and S3's
     * overlapping joins (see {@code BaseConstraintProvider.teacherUnavailability}
     * and {@code SchoolScheduleConstraintProvider.teacherPreference}) each
     * double-count when two rows of their own type overlap, and a VACATION
     * row overlapping a PREFERENCE row for the same hour is equally incoherent
     * policy — so every overlap is rejected regardless of type.
     */
    public static boolean overlaps(RecurringAvailabilityRule candidate, RecurringAvailabilityRule other) {
        if (candidate.dayOfWeek != other.dayOfWeek) return false;
        if (candidate.maxSlot < other.minSlot || other.maxSlot < candidate.minSlot) return false;
        if (candidate.effectiveTo != null && other.effectiveFrom != null
                && candidate.effectiveTo.isBefore(other.effectiveFrom)) return false;
        if (other.effectiveTo != null && candidate.effectiveFrom != null
                && other.effectiveTo.isBefore(candidate.effectiveFrom)) return false;
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecurringAvailabilityRule other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }

    /**
     * Every rule for one teacher — the shared lookup behind overlap
     * validation ({@code RecurringAvailabilityRuleResource.validateNoOverlap},
     * {@code DataGenerator.generateAvailabilityRules}) and re-expansion
     * ({@code AvailabilityExpansionService.expandForTeacher}), so all three
     * query the same way instead of re-typing the filter.
     */
    public static List<RecurringAvailabilityRule> listByTeacher(Teacher teacher) {
        return list("teacher = ?1", teacher);
    }

    /**
     * Every teacher id that has at least one rule, without loading a single
     * {@code RecurringAvailabilityRule} or {@code Teacher} entity —
     * {@code listAll().stream().map(rule -> rule.teacher.id)} would eagerly
     * initialize the {@code teacher} association for every row (N+1 selects
     * for N distinct teachers). The JPQL path expression below resolves
     * directly to the {@code teacher_id} FK column, no join or entity load
     * needed. Used by {@code AvailabilityExpansionService.expandAll()}.
     */
    public static List<Long> listDistinctTeacherIds() {
        return getEntityManager()
                .createQuery("select distinct r.teacher.id from RecurringAvailabilityRule r", Long.class)
                .getResultList();
    }
}
