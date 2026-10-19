package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.TimeslotSequence;

/**
 * Expands {@link RecurringAvailabilityRule} templates into concrete dated
 * {@link TeacherAvailability} rows (TASK-345), one per matching school day.
 * H5 (teacher unavailability) and S3 (teacher preference) only ever read
 * {@code TeacherAvailability} — this keeps both constraints completely
 * unchanged; a rule is never consulted directly by the solver.
 *
 * <p><b>Closure handling:</b> "school day" here means "a date that has at
 * least one {@link Timeslot} row" — the actual generated calendar already
 * excludes weekends and closures (see {@code DataGenerator.generateTimeslots}),
 * so reusing it is simpler and can't drift from a second copy of the closure
 * list.</p>
 *
 * <p><b>Idempotency / re-expansion:</b> every row this service creates is
 * tagged with {@link TeacherAvailability#sourceRuleId}. Re-expanding a teacher
 * first deletes all of that teacher's previously-expanded rows (identified by
 * a non-null {@code sourceRuleId}), then regenerates from the teacher's
 * current rule set — so editing or deleting a rule and re-running expansion
 * never leaves stale rows behind. Hand-entered availability (sourceRuleId
 * null) is never touched.</p>
 *
 * <p><b>Lunch-spanning ranges:</b> a rule whose slot range straddles the
 * 13:00-14:00 / 14:30-15:30 lunch break (e.g. minSlot=5, maxSlot=7) expands
 * into a single {@code TeacherAvailability} row spanning 13:00-16:30, not two
 * separate rows. This is safe for H5/S3 because no {@link Timeslot} is ever
 * generated inside the 14:00-14:30 lunch gap (see
 * {@code TimeslotSequence.SLOT_START_TIMES}) — {@code Joiners.overlapping()}
 * can only ever match a real Lesson's timeslot, so a wider availability
 * window that happens to include the lunch gap cannot produce a different
 * match set than two separate slot-aligned rows would.</p>
 */
@ApplicationScoped
public class AvailabilityExpansionService {

    private static final Logger LOG = Logger.getLogger(AvailabilityExpansionService.class);

    /**
     * Re-expand every teacher that has at least one {@link RecurringAvailabilityRule}.
     * Called once at seed time (after teachers and rules exist) and safe to
     * call again any time (fully idempotent).
     */
    @Transactional
    public void expandAll() {
        // listDistinctTeacherIds() resolves straight to the teacher_id FK
        // column (single query, no entity load) instead of listAll().stream()
        // .map(rule -> rule.teacher.id), which would eagerly initialize a
        // Teacher entity per rule (N+1 selects).
        List<Long> teacherIds = RecurringAvailabilityRule.listDistinctTeacherIds();
        // Computed once and shared across every teacher in this pass - the
        // school calendar doesn't change per teacher, so re-scanning the
        // Timeslot table once per teacher would be pure waste.
        Map<DayOfWeek, List<LocalDate>> schoolDaysByWeekday = schoolDaysByWeekday();
        for (Long teacherId : teacherIds) {
            expandForTeacher(Teacher.findById(teacherId), schoolDaysByWeekday);
        }
    }

    /**
     * Re-expand one teacher's rules: delete every previously-expanded row for
     * this teacher, then regenerate from the teacher's current rule set. Call
     * this after any create/update/delete of a {@link RecurringAvailabilityRule}
     * so the expanded {@link TeacherAvailability} rows never go stale.
     */
    @Transactional
    public void expandForTeacher(Teacher teacher) {
        expandForTeacher(teacher, null);
    }

    // synchronized: cheap single-JVM guard only - prevents an interleaved
    // delete-then-insert race between concurrent CRUD calls for the same (or
    // different) teacher on one node; it does NOT protect a multi-node
    // deployment, which would need a DB-level lock instead.
    private synchronized void expandForTeacher(Teacher teacher, Map<DayOfWeek, List<LocalDate>> precomputedSchoolDaysByWeekday) {
        if (teacher == null) return;

        TeacherAvailability.delete("teacher = ?1 and sourceRuleId is not null", teacher);

        List<RecurringAvailabilityRule> rules = RecurringAvailabilityRule.listByTeacher(teacher);
        if (rules.isEmpty()) return;

        // Skip the full Timeslot scan entirely when the caller didn't already
        // pay for it (expandAll() passes its shared map; a lone REST-driven
        // call only computes it once it's known there's actually a rule set
        // to expand).
        Map<DayOfWeek, List<LocalDate>> schoolDaysByWeekday =
                precomputedSchoolDaysByWeekday != null ? precomputedSchoolDaysByWeekday : schoolDaysByWeekday();

        int generated = 0;
        for (RecurringAvailabilityRule rule : rules) {
            generated += expandRule(rule, schoolDaysByWeekday.getOrDefault(rule.dayOfWeek, List.of()));
        }
        LOG.debugf("Expanded %d TeacherAvailability rows for %s from %d rule(s)",
                (Object) generated, teacher.name, rules.size());
    }

    private int expandRule(RecurringAvailabilityRule rule, List<LocalDate> schoolDaysOnWeekday) {
        int slotCount = TimeslotSequence.SLOTS_PER_DAY;
        if (rule.minSlot < 0 || rule.maxSlot >= slotCount || rule.maxSlot < rule.minSlot) {
            throw new IllegalStateException("RecurringAvailabilityRule " + rule.id
                    + " has an invalid slot range [" + rule.minSlot + "-" + rule.maxSlot + "]");
        }
        LocalTime start = TimeslotSequence.SLOT_START_TIMES[rule.minSlot];
        LocalTime end = TimeslotSequence.SLOT_START_TIMES[rule.maxSlot].plusHours(1);

        int count = 0;
        for (LocalDate date : schoolDaysOnWeekday) {
            if (rule.effectiveFrom != null && date.isBefore(rule.effectiveFrom)) continue;
            if (rule.effectiveTo != null && date.isAfter(rule.effectiveTo)) continue;
            TeacherAvailability ta = new TeacherAvailability(
                    rule.teacher, LocalDateTime.of(date, start), LocalDateTime.of(date, end), rule.type);
            ta.sourceRuleId = rule.id;
            ta.persist();
            count++;
        }
        // Catch-all safety net: the API/seed boundary now rejects the one
        // known cause of a silent zero-expansion (a single-date rule whose
        // dayOfWeek doesn't match that date's weekday), but a future rule
        // shape or a closure swallowing an entire effective window could
        // still produce zero rows without either check catching it. This
        // WARN makes that visible instead of silent.
        if (count == 0) {
            LOG.warnf("RecurringAvailabilityRule %d (%s) expanded to zero TeacherAvailability rows",
                    rule.id, rule);
        }
        return count;
    }

    /**
     * Every generated school day (one Timeslot day = one school day), grouped
     * by day of week. Computed once per expansion pass and shared across all
     * rules instead of re-scanning Timeslot per rule.
     */
    private Map<DayOfWeek, List<LocalDate>> schoolDaysByWeekday() {
        return Timeslot.<Timeslot>listAll().stream()
                .map(Timeslot::getDate)
                .distinct()
                .sorted()
                .collect(Collectors.groupingBy(LocalDate::getDayOfWeek));
    }
}
