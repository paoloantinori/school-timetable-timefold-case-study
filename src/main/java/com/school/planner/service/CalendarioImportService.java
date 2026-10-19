package com.school.planner.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.GoldenScheduleImportService.TimeslotKey;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.util.SeedCsv;

/**
 * Imports the school's pre-set "Calendario scolastico 2026/27" (TASK-364,
 * binding reading key {@code claudedocs/spalmone-2026-27/calendario-scolastico-format.md})
 * as pinned pre-assignment input, replacing the heuristic LabRotation/HybridPhase1a
 * CSP path for the MODA lab teachers.
 *
 * <p>Three seed CSVs (byte-identical to {@code claudedocs/spalmone-2026-27/data/},
 * see {@code seed-csv-format.md}), extracted by {@code scripts/analyze/calendario2027-extract.py}:</p>
 * <ul>
 *   <li><b>calendario_moda.csv</b> (from {@code MODA TUTTI}'s 1/2/G09 PE/MU/BO
 *   columns) - PINNED as Lesson rows. Each row names a teacher by initial
 *   ({@link #INITIAL_TO_TEACHER}), a shift (first/second, decoded from the
 *   sheet's signed cell value), and hours. See {@link #computeSlotRange} for
 *   the shift-to-slot mapping.</li>
 *   <li><b>calendario_fc.csv</b> (DEF FC TUTTI's G04/G08/G13 Au/La/St
 *   split) and <b>calendario_class_days.csv</b> (the 14 per-class sheets'
 *   date-&gt;hours+modality) - REPORT-ONLY. Teacher resolution for FC/G13 lab
 *   hours is ambiguous (no teacher-initial columns, unlike MODA TUTTI), so
 *   these are never pinned; instead they cross-check {@code GroupCalendarRule}
 *   open-slot counts and {@link StagePattern} windows per (group, date), the
 *   coherence signal the school's process evolution needs.</li>
 * </ul>
 *
 * <p>Validation (mandatory, TASK-342 review): every planned MODA pin is checked
 * against {@link GroupCalendarService#isOpen} AND against every other planned/
 * already-pinned Lesson for teacher and student-group conflicts. The import is
 * all-or-nothing per invocation: if ANY row fails validation, nothing is
 * persisted (see {@link CalendarioImportReport#persisted}) unless
 * {@code dryRun=true}, in which case the full report is always returned
 * without ever touching the database. Refuses (via {@link IllegalStateException},
 * shared with {@link GoldenScheduleImportService} - TASK-364 code review K7)
 * while the solver is running.</p>
 *
 * <p>Re-run safety (TASK-364 code review K2): if any pinned Lesson already
 * exists for a MODA target course (teacher+group pair resolved above) from a
 * previous import, a plain re-run refuses (like {@code import-golden}'s
 * existing-Lesson guard) unless {@code force=true}, which deletes those prior
 * pins and immediately restores an equal number of stub Lessons (unpinned, no
 * timeslot) for the same courses before re-planning - so a failed or partial
 * force re-run never leaves a course short of its {@code totalHours}, and a
 * clean force re-run reproduces the exact same final Lesson counts (see
 * {@link #consumeStubs}, which then re-consumes those same restored stubs).</p>
 *
 * <p>Stub consumption (TASK-364 code review K1): {@code LessonGenerator}
 * pre-creates {@code Course.totalHours} unpinned "stub" Lessons per course
 * before any pre-assignment runs. Pinning N new Lessons for a MODA course
 * without removing N of its stubs would leave the solver free to ALSO place
 * the untouched stubs, silently doubling that course's scheduled hours. After
 * a successful (persisted) import, {@link #consumeStubs} deletes up to N
 * unpinned stubs per course (preferring ones with no timeslot yet) and
 * reports any shortfall in {@link CalendarioImportReport#stubConsumptions}.</p>
 *
 * <p>Efficiency note (TASK-364 code review): every DB-backed lookup this class
 * needs (student groups, calendar rules, stage patterns, MODA lab courses) is
 * small and bounded (14 groups, ~90 calendar rules, a handful of stage
 * patterns, 9 teacher/group course pairs) and is fetched exactly once up
 * front into a plain {@link Map}, then reused across all ~3,600 CSV rows -
 * mirroring {@code GoldenScheduleImportService}'s {@code buildTimeslotIndex}/
 * {@code buildCourseIndex} precedent instead of one query per row.</p>
 */
@ApplicationScoped
public class CalendarioImportService {

    private static final Logger LOG = Logger.getLogger(CalendarioImportService.class);

    @Inject
    ScheduleService scheduleService;

    /**
     * MODA TUTTI's teacher-initial columns, kept as a single named constant
     * (not scattered magic strings) so a future school-year re-transcription
     * with different initials only needs to change this map. Discovered from
     * incarichi.csv (TASK-364): T07's sole MODA subject is
     * "Tecnologia e creatività" (24h x 3 groups = 72h, matching the format
     * doc's independently-verified yearly total for her).
     */
    static final Map<String, String> INITIAL_TO_TEACHER = Map.of(
            "PE", "T17",
            "MU", "T16",
            "BO", "T07");

    private static final int MODA_CSV_COLUMNS = 5; // date,group,initial,hours,shift
    private static final int FC_CSV_COLUMNS = 5; // group,date,aula_hours,lab_hours,stage_hours
    private static final int CLASS_DAYS_CSV_COLUMNS = 4; // group,date,hours,modality

    /** Minimum whole hours a calendario_class_days.csv/calendario_fc.csv day must fall short by to be worth reporting (avoids 1h rounding noise). */
    private static final int CAPACITY_DISCREPANCY_THRESHOLD_HOURS = 1;

    @Transactional
    public CalendarioImportReport importCalendario(boolean dryRun, boolean force) {
        // Same guard the golden import applies (its own shared helper): an import
        // mid-solve would clear transient solver state and mutate pinned lessons
        // under the running solve (code review, TASK-378).
        GoldenScheduleImportService.refuseIfSolverRunning(scheduleService, "calendario import");

        List<String[]> modaRows = SeedCsv.load("calendario_moda.csv", MODA_CSV_COLUMNS);
        List<String[]> FCRows = SeedCsv.load("calendario_fc.csv", FC_CSV_COLUMNS);
        List<String[]> classDaysRows = SeedCsv.load("calendario_class_days.csv", CLASS_DAYS_CSV_COLUMNS);

        // Shared, DB-backed lookups - each fetched exactly once regardless of
        // how many of the ~3,600 CSV rows below consult them (see class javadoc).
        Map<String, StudentGroup> groupsByName = buildGroupIndex();
        Map<Long, List<GroupCalendarRule>> rulesByGroupId = buildCalendarRuleIndex();
        Map<Long, List<StagePattern>> stagePatternsByGroupId = buildStagePatternIndex();
        Map<CourseKey, CourseResolution> modaCourseIndex = buildModaCourseIndex(groupsByName);
        Set<LocalDate> holidays = buildHolidayDates();
        // Built once, shared by planModaPins and planStagePins below (both used to
        // build their own copy - same "fetched exactly once" principle this class
        // javadoc already states for every other shared lookup here).
        Map<TimeslotKey, Timeslot> timeslotIndex = GoldenScheduleImportService.buildTimeslotIndex();

        // K2 re-run guard: a dry run never persists, so it can't double-pin -
        // only a real (!dryRun) call needs to check/clear prior MODA pins.
        long deletedPriorPins = dryRun ? 0 : refuseOrClearPriorPins(modaCourseIndex, force);

        ModaPlan plan = planModaPins(modaRows, groupsByName, rulesByGroupId, modaCourseIndex, timeslotIndex);
        List<CalendarioImportReport.CalendarDiscrepancy> FCDiscrepancies =
                crossCheckFC(FCRows, groupsByName, rulesByGroupId, stagePatternsByGroupId);
        List<CalendarioImportReport.CalendarDiscrepancy> classDaysDiscrepancies =
                crossCheckClassDays(classDaysRows, groupsByName, rulesByGroupId, stagePatternsByGroupId, holidays);

        // TASK-381 "L1-bis" / "stage on the grid": the FC/G13 lab skeleton is
        // no longer pinned from the golden grid at all (superseded - see the
        // deleted planFCLabSkeletonPins' history). DailyActivityQuota (seeded
        // from this same calendario_fc.csv by DataGenerator) is now an
        // AUTHORITATIVE search-space restriction consulted directly by the VRP
        // filter and the H23/H24/M30/M31 constraints - the quotas drive
        // placement themselves, no separate golden-sourced pin plan is needed
        // for the lab hours. The one thing still planned here is STAGE: the
        // sheet's stageHours column (quota groups) plus calendario_class_days.csv's
        // modality=S rows (every other group - TASK-381 "stage on the grid")
        // drive contiguous Stage-course pins (see planStagePins below), which
        // the quota data alone can't express as a VRP/constraint restriction
        // the way lab/aula hours can (Stage isn't a subject choice, it's a
        // specific pseudo-course that needs actual Lesson rows).
        StagePlan stagePlan = planStagePins(groupsByName, rulesByGroupId, timeslotIndex, classDaysRows, FCRows);

        boolean persisted = !dryRun && plan.failures.isEmpty();
        List<CalendarioImportReport.StubConsumption> stubConsumptions = List.of();
        if (persisted) {
            Map<Course, Integer> pinnedCountByCourse = new LinkedHashMap<>();
            for (PlannedPin pin : plan.pins) {
                GoldenScheduleImportService.persistPinnedLesson(pin.course, pin.timeslot);
                pinnedCountByCourse.merge(pin.course, 1, Integer::sum);
            }
            // TASK-381 "L1-bis": quota/class-days-driven Stage pins consume
            // stubs exactly like the MODA shift pins - without this the
            // solver would double the hours.
            for (PlannedPin pin : stagePlan.pins) {
                GoldenScheduleImportService.persistPinnedLesson(pin.course, pin.timeslot);
                pinnedCountByCourse.merge(pin.course, 1, Integer::sum);
            }
            stubConsumptions = consumeStubs(pinnedCountByCourse);
            // TASK-381 "stage on the grid": off-grid stage hours never become
            // pinned Lessons (no Timeslot exists to pin them to) but are real
            // stage hours that already drew from the Stage course's stub
            // budget during planning - see consumeOffGridStageStubs' javadoc.
            consumeOffGridStageStubs(stagePlan.offGridHoursByCourse);
            scheduleService.clearTransientState();
        }

        LOG.infof("Calendario import (dryRun=%b force=%b): modaRows=%d rowsPlanned=%d lessonsPinned=%d skippedNoTimeslot=%d "
                + "failures=%d persisted=%b deletedPriorPins=%d FCRows=%d FCDiscrepancies=%d "
                + "classDaysRows=%d classDaysDiscrepancies=%d stagePinned=%d stageAlreadyPinned=%d "
                + "stageSkippedCalendarClosed=%d stageSkippedNoBudget=%d stageOffGridHours=%d",
                dryRun, force, modaRows.size(), plan.rowsPlanned, plan.pins.size(), plan.skippedNoTimeslot, plan.failures.size(),
                persisted, deletedPriorPins, FCRows.size(), FCDiscrepancies.size(), classDaysRows.size(),
                classDaysDiscrepancies.size(), stagePlan.stagePinned, stagePlan.stageAlreadyPinned,
                stagePlan.stageSkippedCalendarClosed, stagePlan.stageSkippedNoBudget, stagePlan.stageOffGridHours);
        for (CalendarioImportReport.ModaFailure f : plan.failures) {
            LOG.infof("  moda-failure: line=%d date=%s group=%s initial=%s reason=%s", f.line, f.date, f.group, f.initial, f.reason);
        }
        for (CalendarioImportReport.StubConsumption s : stubConsumptions) {
            if (s.stubShortfall > 0) {
                LOG.warnf("  stub-shortfall: course='%s' teacher=%s group=%s lessonsPinned=%d consumedStubs=%d shortfall=%d",
                        s.subject, s.teacher, s.group, s.lessonsPinned, s.consumedStubs, s.stubShortfall);
            }
        }

        return new CalendarioImportReport(dryRun, persisted, modaRows.size(), plan.rowsPlanned, plan.pins.size(),
                plan.skippedNoTimeslot, plan.skippedBeyondCourseHours, plan.failures, FCRows.size(), FCDiscrepancies,
                classDaysRows.size(), classDaysDiscrepancies, stubConsumptions, deletedPriorPins,
                stagePlan.stagePinned, stagePlan.stageSkippedCalendarClosed, stagePlan.stageSkippedNoBudget,
                stagePlan.stageOffGridHours, stagePlan.stageAlreadyPinned);
    }

    /**
     * K2 re-run guard: if any pinned Lesson already exists for a MODA target
     * course, refuses (unless {@code force=true}), otherwise deletes those
     * prior pins and restores an equal number of stub Lessons per course (see
     * the class javadoc's "re-run safety" section). Returns the number of
     * prior pins cleared (0 if none existed) - the restored-stub count is
     * always identical by construction, so it isn't tracked as a second value.
     */
    private long refuseOrClearPriorPins(Map<CourseKey, CourseResolution> modaCourseIndex, boolean force) {
        Set<Course> targetCourses = new LinkedHashSet<>();
        for (CourseResolution resolution : modaCourseIndex.values()) {
            if (resolution.course() != null) {
                targetCourses.add(resolution.course());
            }
        }
        if (targetCourses.isEmpty()) {
            return 0;
        }

        long existingPinnedCount = Lesson.count("course in ?1 and pinned = true", targetCourses);
        if (existingPinnedCount == 0) {
            return 0;
        }
        if (!force) {
            throw new IllegalStateException(
                "Refusing calendario import: " + existingPinnedCount + " pinned Lesson row(s) already exist for the "
                + "MODA target course(s), likely from a previous import-calendario call. Pass force=true to clear "
                + "them (and restore the equivalent stub Lessons) before re-importing, or use a fresh profile.");
        }

        long deletedPriorPins = 0;
        for (Course course : targetCourses) {
            long priorPinCount = Lesson.count("course = ?1 and pinned = true", course);
            if (priorPinCount == 0) continue;
            Lesson.delete("course = ?1 and pinned = true", course); // single bulk DELETE, not one per row
            for (long i = 0; i < priorPinCount; i++) {
                new Lesson(course).persist();
            }
            deletedPriorPins += priorPinCount;
        }
        LOG.infof("Calendario import: force=true, deleted %d prior pinned Lesson(s) and restored the same number "
                + "of stub(s) across the MODA target courses before re-import", deletedPriorPins);
        return deletedPriorPins;
    }

    /**
     * Consumes N stub Lessons per course after N Lessons are pinned for it
     * (TASK-364 code review K1, see class javadoc's "stub consumption"
     * section). Prefers stubs with no timeslot yet; falls back to any other
     * unpinned Lesson of the course if fewer null-timeslot stubs exist than
     * pins. Package-private (not private) so it's directly unit-testable
     * without a full CSV-driven plan: the real calendario_moda.csv always
     * contains its one known conflict (see {@link #computeSlotRange}'s
     * javadoc), so the persisted branch that calls this method in production
     * can never be reached by importing the full, real fixture.
     */
    List<CalendarioImportReport.StubConsumption> consumeStubs(Map<Course, Integer> pinnedCountByCourse) {
        List<CalendarioImportReport.StubConsumption> result = new ArrayList<>();
        for (Map.Entry<Course, Integer> entry : pinnedCountByCourse.entrySet()) {
            Course course = entry.getKey();
            int pinnedCount = entry.getValue();

            List<Long> idsToDelete = selectStubIdsToConsume(course, pinnedCount);
            if (!idsToDelete.isEmpty()) {
                Lesson.delete("id in ?1", idsToDelete); // single bulk DELETE, not one per stub
            }

            int consumed = idsToDelete.size();
            int shortfall = pinnedCount - consumed;
            result.add(new CalendarioImportReport.StubConsumption(
                    course.teacher.name, course.subject, course.studentGroup.name, pinnedCount, consumed, shortfall));
        }
        return result;
    }

    /**
     * Shared stub-selection logic (TASK-364 K1, extracted for TASK-381 "stage
     * on the grid" so {@link #consumeStubs} and {@link #consumeOffGridStageStubs}
     * can't drift apart on the preference order): prefers unpinned stubs with
     * no timeslot yet; falls back to any other unpinned Lesson of the course
     * if fewer null-timeslot stubs exist than {@code count}. Returns the ids
     * to delete (never more than {@code count}) without deleting them - the
     * caller deletes (bulk, not per-row) and reports/accounts the shortfall
     * differently depending on what the consumption is for.
     */
    private List<Long> selectStubIdsToConsume(Course course, int count) {
        // Prefer null-timeslot stubs; only query the timeslotted fallback
        // bucket if the preferred one alone doesn't cover count.
        List<Lesson> nullTimeslotStubs = Lesson.list("course = ?1 and pinned = false and timeslot is null", course);
        List<Long> idsToDelete = new ArrayList<>();
        int consumed = Math.min(count, nullTimeslotStubs.size());
        for (int i = 0; i < consumed; i++) {
            idsToDelete.add(nullTimeslotStubs.get(i).id);
        }
        if (consumed < count) {
            List<Lesson> timeslottedStubs = Lesson.list("course = ?1 and pinned = false and timeslot is not null", course);
            int extra = Math.min(count - consumed, timeslottedStubs.size());
            for (int i = 0; i < extra; i++) {
                idsToDelete.add(timeslottedStubs.get(i).id);
            }
        }
        return idsToDelete;
    }

    /**
     * TASK-381 "stage on the grid": off-grid stage hours (closures/Saturdays
     * the sheet books stage on anyway - see {@link #planStagePins}'s javadoc)
     * never become pinned Lessons - there is no Timeslot to pin them to - but
     * they ARE real stage hours that already drew from the Stage course's
     * stub budget during planning ({@link StagePlan#offGridHoursByCourse}).
     * Left alone, those stubs would remain as floating unpinned Lessons with
     * no timeslot, which the solver would then try (and fail) to place.
     * Instead, exactly that many stubs are deleted here (same selection
     * preference as {@link #consumeStubs}, via the shared {@link
     * #selectStubIdsToConsume}) and the same count is ADDED to {@link
     * Course#expectedUnscheduledHours} (null-safe) - the TASK-373 field.
     * The two-writer semantics of that field (golden assigns, this path
     * accumulates) and why both interleavings are safe are owned by {@link
     * #planStagePins}' javadoc, not repeated here.
     *
     * <p>Deliberately does NOT feed into {@link #stubConsumptions}/{@link
     * CalendarioImportReport.StubConsumption} - those rows report PINNED
     * counts, and an off-grid hour is never pinned (folding it in would
     * misrepresent {@code lessonsPinned}). Logs its own shortfall line
     * instead, mirroring that report's own warn-on-shortfall convention; a
     * shortfall should never actually occur (the plan phase already bounded
     * every off-grid hour by the course's live unpinned-stub count before
     * this method runs), so a non-zero shortfall here signals the plan and
     * persist phases have drifted apart, not routine behavior to expect.</p>
     *
     * <p>Package-private (not private): {@code CalendarioImportServiceTest}
     * calls this directly against a minimal fixture, same convention as
     * {@link #consumeStubs}/{@link #planStagePins}.</p>
     */
    void consumeOffGridStageStubs(Map<Course, Integer> offGridHoursByCourse) {
        for (Map.Entry<Course, Integer> entry : offGridHoursByCourse.entrySet()) {
            Course course = entry.getKey();
            int offGridHours = entry.getValue();
            if (offGridHours <= 0) continue;

            List<Long> idsToDelete = selectStubIdsToConsume(course, offGridHours);
            if (!idsToDelete.isEmpty()) {
                Lesson.delete("id in ?1", idsToDelete);
            }
            int consumed = idsToDelete.size();
            course.expectedUnscheduledHours = (course.expectedUnscheduledHours == null ? 0 : course.expectedUnscheduledHours) + consumed;

            int shortfall = offGridHours - consumed;
            if (shortfall > 0) {
                LOG.warnf("  off-grid-stub-shortfall: course='%s' teacher=%s group=%s offGridHours=%d consumedStubs=%d shortfall=%d",
                        course.subject, course.teacher.name, course.studentGroup.name, offGridHours, consumed, shortfall);
            }
        }
    }

    private Set<LocalDate> buildHolidayDates() {
        Set<LocalDate> dates = new HashSet<>();
        for (SchoolHoliday holiday : SchoolHoliday.<SchoolHoliday>listAll()) {
            if (holiday.date != null) {
                dates.add(holiday.date);
            }
        }
        return dates;
    }

    // ─── Shared index-building (each queried exactly once per import call) ──

    private Map<String, StudentGroup> buildGroupIndex() {
        Map<String, StudentGroup> index = new HashMap<>();
        for (StudentGroup group : StudentGroup.<StudentGroup>listAll()) {
            index.put(group.name, group);
        }
        return index;
    }

    private Map<Long, List<GroupCalendarRule>> buildCalendarRuleIndex() {
        Map<Long, List<GroupCalendarRule>> index = new HashMap<>();
        for (GroupCalendarRule rule : GroupCalendarRule.<GroupCalendarRule>listAll()) {
            if (rule.studentGroup == null) continue;
            index.computeIfAbsent(rule.studentGroup.id, k -> new ArrayList<>()).add(rule);
        }
        return index;
    }

    private Map<Long, List<StagePattern>> buildStagePatternIndex() {
        Map<Long, List<StagePattern>> index = new HashMap<>();
        for (StagePattern pattern : StagePattern.<StagePattern>listAll()) {
            if (pattern.studentGroup == null) continue;
            index.computeIfAbsent(pattern.studentGroup.id, k -> new ArrayList<>()).add(pattern);
        }
        return index;
    }

    /** (teacherName, studentGroupId) - MODA lab course lookup key, resolved once for all 3 teachers x 3 groups (9 pairs), not per CSV row. */
    private record CourseKey(String teacherName, Long studentGroupId) {}

    /** Either a resolved Course, or the reason it couldn't be resolved (see {@link #resolveModaCourse}) - exactly one is non-null. */
    private record CourseResolution(Course course, String error) {}

    private Map<CourseKey, CourseResolution> buildModaCourseIndex(Map<String, StudentGroup> groupsByName) {
        List<Course> candidates = Course.list("teacher.name in ?1", INITIAL_TO_TEACHER.values());
        Map<CourseKey, List<Course>> coursesByKey = new HashMap<>();
        for (Course course : candidates) {
            if (course.teacher == null || course.studentGroup == null) continue;
            coursesByKey.computeIfAbsent(new CourseKey(course.teacher.name, course.studentGroup.id), k -> new ArrayList<>()).add(course);
        }
        Map<CourseKey, CourseResolution> resolved = new HashMap<>();
        for (String teacherName : INITIAL_TO_TEACHER.values()) {
            for (StudentGroup group : groupsByName.values()) {
                CourseKey key = new CourseKey(teacherName, group.id);
                resolved.put(key, resolveModaCourse(teacherName, group.name, coursesByKey.getOrDefault(key, List.of())));
            }
        }
        return resolved;
    }

    // ─── MODA pin planning ────────────────────────────────────────────────

    // Package-private (not private): CalendarioImportServiceTest's planStagePins
    // tests read StagePlan.pins directly to verify the N=min(...) truncation logic.
    record PlannedPin(Course course, Timeslot timeslot) {}

    /** TASK-381 "stage on the grid" (formerly "L1-bis"): planned quota/class-days-driven Stage pins + report counters. */
    static final class StagePlan {
        final List<PlannedPin> pins = new ArrayList<>();
        int stagePinned = 0;
        int stageSkippedCalendarClosed = 0;
        int stageSkippedNoBudget = 0;
        /**
         * Stage-hour ask on dates with NO Timeslot rows at all (closures,
         * Saturdays - 280h across the 7 Stage groups): valid off-grid stage
         * that draws stub budget and is consumed into {@link
         * Course#expectedUnscheduledHours} at persist - the full mechanism is
         * owned by {@link #planStagePins}' "off-grid hours draw budget too"
         * javadoc. The five counters together partition every processed
         * stage-hour ask.
         */
        int stageOffGridHours = 0;
        /**
         * Off-grid stage hours (see {@link #stageOffGridHours}), PER COURSE -
         * drives {@link #consumeOffGridStageStubs}'s stub deletion + {@link
         * Course#expectedUnscheduledHours} add in {@code importCalendario}'s
         * persist phase. Keys are exactly the Stage courses that had at least
         * one off-grid hour this run; the sum of values always equals {@link
         * #stageOffGridHours}.
         */
        final Map<Course, Integer> offGridHoursByCourse = new LinkedHashMap<>();
        /**
         * Stage-hour ask already covered by a PINNED Stage lesson of the
         * same course on that date (a prior import run, or the golden
         * import's own Stage pins). Without this check a force re-run would
         * re-spend the leftover unpinned-stub budget onto the earliest
         * already-covered dates, stacking duplicate pinned Stage lessons -
         * a permanent pinned-vs-pinned H3 no solver can fix.
         */
        int stageAlreadyPinned = 0;
    }

    /**
     * One (group, date, hours) stage-hour ask, from either {@link
     * DailyActivityQuota}'s {@code stageHours} column (quota groups - 1
     * FC/G08/G13 for 2026/27, though G04's is always 0 and has no Stage
     * course) or {@code calendario_class_days.csv}'s {@code modality=S} rows
     * for every OTHER group with a seeded Stage course (G07, G11, 3
     * FA, G12, G09 - TASK-381 "stage on the grid"). Built by {@link
     * #buildStageAsks}, which guarantees the two sources are mutually
     * exclusive by group, so neither can double-count the same (group, date).
     */
    private record StageAsk(StudentGroup group, LocalDate date, int hours) {}

    /**
     * Merges the two stage-hour sources into one chronological, per-group
     * ordered list for {@link #planStagePins} to walk (TASK-381 "stage on the
     * grid"): {@link DailyActivityQuota#stageHours} for quota groups, plus
     * {@code calendario_class_days.csv}'s {@code modality=S} rows for every
     * other group that has a seeded Stage course. A group is read from
     * EXACTLY ONE source - any group with at least one {@link
     * DailyActivityQuota} row at all (aula/lab/stage, not just stage&gt;0) is
     * a quota group and is never also read from class-days, so the two
     * sources can never double-count the same (group, date).
     *
     * <p>Rows dated outside the Timeslot horizon are dropped: {@link
     * DailyActivityQuota} rows are already horizon-filtered at seed time
     * ({@code DataGenerator#generateDailyActivityQuotas}), but the raw
     * {@code classDaysRows} CSV is not, so this method applies the same
     * {@code [SCHOOL_YEAR_START, SCHOOL_YEAR_END]} filter explicitly - on the
     * current seed this never actually fires for the 5 class-days groups
     * (verified against the real CSV), but a future school-year sheet could
     * extend past the horizon the way {@code calendario_moda.csv}'s two rows
     * already do.</p>
     */
    private List<StageAsk> buildStageAsks(Map<String, StudentGroup> groupsByName, List<String[]> classDaysRows, List<String[]> FCRows) {
        // TASK-385: DailyActivityQuota now also carries a plain expected-hours
        // row for the other 11 groups (calendario_class_days.csv), so
        // "any row at all" no longer means "quota group" - scope to the
        // original FCQuota-sourced rows (G04/G08/G13) so this method's
        // group-vs-source split (and every other group's own S-modality
        // class-days ask below) stays byte-identical to before that data existed.
        List<DailyActivityQuota> allQuotaRows = DailyActivityQuota.list("FCQuota", true);
        Set<Long> quotaGroupIds = new HashSet<>();
        for (DailyActivityQuota q : allQuotaRows) {
            if (q.studentGroup != null) quotaGroupIds.add(q.studentGroup.id);
        }

        // (group, date) keyed so a duplicate row for the same day (a corrected/
        // re-exported sheet, or a hand-built fixture with two rows) MERGES by
        // summing hours instead of producing two independent asks - two asks on
        // the same (group, date) would each walk the same slot window against
        // the same persisted-pins snapshot and double-pin identical Timeslots
        // (planStagePins' alreadyPinnedSlots only reflects PRIOR-run pins, not
        // this run's in-flight plan). Zero duplicates exist on the real seed
        // (verified mechanically); WARN so new data drift is visible.
        Map<StageAskKey, Integer> hoursByGroupDate = new LinkedHashMap<>();
        Map<StageAskKey, StudentGroup> groupByKey = new HashMap<>();
        for (DailyActivityQuota quota : allQuotaRows) {
            if (quota.stageHours <= 0) continue;
            StudentGroup group = quota.studentGroup;
            if (group == null || !groupsByName.containsKey(group.name)) continue;
            mergeAsk(hoursByGroupDate, groupByKey, group, quota.date, quota.stageHours);
        }
        for (String[] row : classDaysRows) {
            if (!"S".equals(row[3].trim())) continue;
            StudentGroup group = groupsByName.get(row[0].trim());
            if (group == null || quotaGroupIds.contains(group.id)) continue; // quota groups: DailyActivityQuota only
            LocalDate date = LocalDate.parse(row[1].trim());
            if (date.isBefore(TimeslotSequence.SCHOOL_YEAR_START) || date.isAfter(TimeslotSequence.SCHOOL_YEAR_END)) continue;
            // Double-then-round, matching every other hours-column reader of the
            // seed CSVs (crossCheckClassDays, DataGenerator#generateStageCourses):
            // a re-exported sheet may format hours as "8.0".
            int hours = (int) Math.round(Double.parseDouble(row[2].trim()));
            if (hours <= 0) continue;
            mergeAsk(hoursByGroupDate, groupByKey, group, date, hours);
        }
        // TASK-434 fix-16 (2026-07-28): out-of-horizon Stage asks for QUOTA groups (dates outside
        // [SCHOOL_YEAR_START, SCHOOL_YEAR_END], e.g. G04's 2027-06-11/06-14, 16h). DataGenerator
        // drops these from DailyActivityQuota at seed time (no Timeslot can ever exist outside the
        // horizon), so the DailyActivityQuota loop above never sees them and they would stay as
        // orphaned stub Lessons the solver can never place (H0). Surface them from the raw
        // calendario_fc.csv so planStagePins' off-grid branch (dateHasAnyTimeslot = false) routes
        // them to expectedUnscheduledHours through the SAME cursor/idempotency path as a
        // closure/Saturday. Disjoint from the DailyActivityQuota asks by date range (those are all
        // in-horizon), and quota-group-only (non-quota groups read calendario_class_days.csv).
        for (String[] row : FCRows) {
            StudentGroup group = groupsByName.get(row[0].trim());
            if (group == null || !quotaGroupIds.contains(group.id)) continue;
            LocalDate date = LocalDate.parse(row[1].trim());
            // Outside the horizon only - in-horizon quota asks come from DailyActivityQuota above.
            if (!date.isBefore(TimeslotSequence.SCHOOL_YEAR_START) && !date.isAfter(TimeslotSequence.SCHOOL_YEAR_END)) continue;
            int hours = (int) Math.round(Double.parseDouble(row[4].trim()));
            if (hours <= 0) continue;
            mergeAsk(hoursByGroupDate, groupByKey, group, date, hours);
        }
        List<StageAsk> asks = new ArrayList<>();
        for (Map.Entry<StageAskKey, Integer> e : hoursByGroupDate.entrySet()) {
            asks.add(new StageAsk(groupByKey.get(e.getKey()), e.getKey().date(), e.getValue()));
        }
        asks.sort(Comparator.<StageAsk, Long>comparing(a -> a.group().id).thenComparing(StageAsk::date));
        return asks;
    }

    private record StageAskKey(Long groupId, LocalDate date) {}

    private static void mergeAsk(Map<StageAskKey, Integer> hoursByGroupDate, Map<StageAskKey, StudentGroup> groupByKey,
                                 StudentGroup group, LocalDate date, int hours) {
        StageAskKey key = new StageAskKey(group.id, date);
        Integer prior = hoursByGroupDate.putIfAbsent(key, hours);
        if (prior != null) {
            LOG.warnf("buildStageAsks: duplicate stage rows for (%s, %s) - merging %dh + %dh into one ask",
                    group.name, date, prior, hours);
            hoursByGroupDate.put(key, prior + hours);
        }
        groupByKey.putIfAbsent(key, group);
    }

    /**
     * True if AT LEAST ONE {@link Timeslot} exists for {@code date} -
     * {@link #planStagePins}' on-grid/off-grid day gate (checks every slot,
     * not just slot 0, so hand-built partial-day test fixtures behave too).
     */
    private static boolean dateHasAnyTimeslot(LocalDate date, Map<TimeslotKey, Timeslot> timeslotIndex) {
        for (LocalTime startTime : TimeslotSequence.SLOT_START_TIMES) {
            if (timeslotIndex.containsKey(new TimeslotKey(date, startTime))) return true;
        }
        return false;
    }

    /**
     * TASK-381 "stage on the grid" (formerly "L1-bis", quota-only) - pin each
     * Stage-seeded group's Stage pseudo-course lessons onto calendar-open
     * slots, driven by {@link #buildStageAsks}'s merged (group, date, hours)
     * list (the authoritative sheet-derived input - NOT the golden grid,
     * unlike the FC lab skeleton path the quota mechanism superseded).
     *
     * <p>For each in-horizon (group, date) ask, fills {@code N = min(hours,
     * that date's calendar-open slot count, the Stage course's remaining
     * unpinned stub budget)} slots contiguously from the group's
     * calendar-open window start ({@link GroupCalendarService#effectiveRange}) -
     * the golden convention (most full stage days start at slot 0). Requires
     * every stage-seeded group to already have a Stage {@link Course}
     * (seeded by {@code DataGenerator#generateStageCourses}, TASK-381 "stage
     * on the grid" - maintainer 2026-07-20 authorization): the {@code stageCourse
     * == null} branch below is now a safety net for a profile that skipped
     * seeding (e.g. a hand-built test fixture), not a normal production path
     * the way it was before that method existed. G04 has {@code
     * stageHours = 0} throughout and no Stage course at all, so it's a
     * structural no-op regardless.</p>
     *
     * <p><b>Off-grid hours draw budget too, WITHOUT the calendar-window cap
     * (TASK-381 "stage on the grid"):</b> a whole ask date with NO {@link
     * Timeslot} row at all ({@link #dateHasAnyTimeslot}) - a closure day, or
     * a Saturday the sheet books stage on anyway - is a day-level branch,
     * deliberately evaluated BEFORE {@link GroupCalendarService#effectiveRange}
     * is even consulted: {@code GroupCalendarRule}'s open-slot count is a
     * WEEKDAY-based concept (e.g. G12's Mon-Wed pattern is only 5 slots),
     * and a closure date inherits its weekday's normal range even though the
     * whole day is actually off-grid - applying that on-grid window cap to
     * an off-grid ask would silently lose hours to {@code
     * stageSkippedCalendarClosed} instead of drawing them as off-grid
     * (mechanically caught by {@code CalendarioImportRealSeedStageTest}: 3 of
     * G12's Vacanze di Natale closure dates ask 7h against a 5-slot
     * Mon/Wed pattern, losing 6h and leaving 6 stub Lessons permanently
     * unconsumed before this fix). An off-grid ask is instead bounded ONLY by
     * {@code remaining} - it is a real stage hour (ASSUMED VALID, maintainer
     * 2026-07-20 - workplaces don't close with the school), just one with no
     * Timeslot to attach a Lesson to, counted as {@link
     * StagePlan#stageOffGridHours} (per-course in {@link
     * StagePlan#offGridHoursByCourse}) INSTEAD of a pin. Left un-drawn, those
     * hours would leave that many stub Lessons permanently unpinned with no
     * timeslot - the solver would then try (and fail) to place them. The
     * persist-phase caller ({@code importCalendario} via {@link
     * #consumeOffGridStageStubs}) deletes that many stubs and adds the same
     * count to {@link Course#expectedUnscheduledHours}.</p>
     *
     * <p><b>No group/teacher-slot conflict validation against ORDINARY
     * lessons</b> (unlike {@link #planOneRow}'s MODA path): the sheet is the
     * standing input here, so a stage pin proceeds even where it would
     * collide with an already-pinned ordinary lesson for the same (group,
     * date) - this is deliberate, not an oversight. Two of the sheet's own
     * stage days (G08, 2027-04-23 and 2027-04-26, 8h each; G13's own
     * 2027-04-23 stage day matches the golden and is NOT in dispute)
     * CONTRADICT the golden grid, which teaches 5 ordinary lessons on those
     * dates instead of stage - pinning the sheet's Stage hours there anyway
     * surfaces the discrepancy as a genuine H3 (student group conflict)
     * violation if the golden's own pins for those dates are also present,
     * which is the intended signal (reported to the school, see {@code
     * claudedocs/spalmone-2026-27/domande-brevi.md}), not silently avoided.</p>
     *
     * <p><b>Same-course idempotency, for BOTH pinned and off-grid hours:</b>
     * hours already covered by a pinned Stage lesson of the same course on
     * the same date count as {@code stageAlreadyPinned} and are not
     * re-planned, and slots occupied by such pins are never re-used -
     * otherwise a force re-run (which clears/replans MODA pins only, never
     * Stage pins - deliberately, since the per-group Stage course is the
     * SAME entity the golden import pins, so bulk-clearing would destroy
     * golden data) would re-spend the leftover stub budget onto the earliest
     * already-covered dates as duplicate pinned lessons.
     *
     * <p>Off-grid hours have NO equivalent per-date DB marker (a consumed
     * off-grid stub is deleted, leaving no trace to detect on a later run,
     * unlike a pinned Lesson) - relying on {@code remaining} (the course's
     * live unpinned-stub count) alone is NOT sufficient: if a course's
     * incarichi.csv budget exceeds its actual sheet demand (a genuine surplus,
     * none observed on the current 2026/27 seed - every one of the 7 Stage
     * courses' budgets exactly match their sheet demand), {@code remaining}
     * stays nonzero after a first run even though every ask was already
     * fulfilled, and a naive re-run would re-draw that surplus against the
     * SAME already-covered off-grid dates, double-adding to {@link
     * Course#expectedUnscheduledHours}. Instead, {@link
     * Course#expectedUnscheduledHours} itself is read back as a cursor at the
     * start of this method (once per course) and consumed down as
     * off-grid asks are walked in the same deterministic chronological order
     * every run produces: the first N off-grid hours a re-run would otherwise
     * re-draw (N = whatever a prior run already persisted for that course)
     * are skipped before any budget is drawn, so a second run adds zero
     * additional {@code expectedUnscheduledHours} and deletes zero additional
     * stubs regardless of surplus - verified in {@code
     * CalendarioImportServiceTest}'s idempotency test.</p>
     *
     * <p>{@code expectedUnscheduledHours} thus has TWO writers with different
     * semantics - {@code GoldenScheduleImportService#reconcileStageCourseHours}
     * assigns an absolute value, this path accumulates - and BOTH
     * interleavings are safe, each for its own reason: calendario-then-golden
     * because a golden {@code force} import deletes every Lesson and
     * recomputes the field from scratch (never observing this path's addend);
     * golden-then-calendario because consumption here is stub-bounded and a
     * golden import leaves ZERO unpinned Stage stubs, so the cursor
     * misreading golden's absolute value as "hours a prior calendario run
     * drew" can only ever SKIP asks that had nothing to consume anyway - the
     * field itself is never modified. The one unsupported shape: regenerating
     * Stage stubs (CRUD generate-lessons) on a golden-imported profile and
     * then running this import - the cursor would misattribute golden's
     * count to calendario draws.</p>
     *
     * <p>Known limitation shared with the pinned case: if a revised sheet
     * REDUCES a date's hours below what a prior run already consumed, the
     * stale excess (pins, or the cursor's now-too-large {@code
     * expectedUnscheduledHours}) is not removed (no mechanized correction
     * path) - this cursor also assumes the CSV's ask ORDER doesn't change
     * between runs for a given course; a revised sheet that reorders dates
     * (without changing total hours) could skip the wrong dates. Neither
     * failure mode is reachable on today's fixed 2026/27 seed.</p>
     *
     * <p>Package-private (not private): {@code CalendarioImportServiceTest}
     * calls this directly against a minimal fixture (N=min(...) truncation
     * and the skip counters), same convention as {@link #consumeStubs}.</p>
     */
    /**
     * 4-arg overload for callers with no out-of-horizon Stage asks (the hand-built unit-test
     * fixtures in {@code CalendarioImportServiceTest}): delegates with an empty {@code FCRows},
     * so those fixtures stay byte-identical. Production ({@code importCalendario}) calls the
     * 5-arg form below with the real {@code calendario_fc.csv} rows so TASK-434 fix-16 can
     * surface out-of-horizon Stage hours for quota groups.
     */
    StagePlan planStagePins(Map<String, StudentGroup> groupsByName, Map<Long, List<GroupCalendarRule>> rulesByGroupId,
                            Map<TimeslotKey, Timeslot> timeslotIndex, List<String[]> classDaysRows) {
        return planStagePins(groupsByName, rulesByGroupId, timeslotIndex, classDaysRows, List.of());
    }

    StagePlan planStagePins(Map<String, StudentGroup> groupsByName, Map<Long, List<GroupCalendarRule>> rulesByGroupId,
                            Map<TimeslotKey, Timeslot> timeslotIndex, List<String[]> classDaysRows, List<String[]> FCRows) {
        StagePlan plan = new StagePlan();

        // Resolve each group's Stage pseudo-course once, and track its
        // remaining unpinned-stub budget as pins/off-grid draws are planned
        // (same intent as planModaPins' remainingByCourse, but keyed off the
        // LIVE unpinned-stub count: the Stage course's totalHours is
        // reconciled separately by GoldenScheduleImportService's own
        // post-import pass and isn't a safe stand-in for "how many stubs are
        // actually left" here).
        Map<Long, Course> stageCourseByGroupId = new HashMap<>();
        Map<Course, Long> remainingStubsByCourse = new HashMap<>();
        // Same-course idempotency (see javadoc): per course, the slots its
        // PINNED Stage lessons already occupy, keyed by date.
        Map<Course, Map<LocalDate, Set<LocalTime>>> pinnedStageSlotsByCourse = new HashMap<>();
        // Off-grid idempotency (see "off-grid hours draw budget too" javadoc):
        // off-grid hours leave no pinned Lesson to detect on a re-run, so
        // Course.expectedUnscheduledHours itself is read as a cursor - the
        // first N off-grid hours this run would otherwise re-draw (in the
        // same deterministic chronological order buildStageAsks always
        // produces) are skipped, N being however many a prior run already
        // persisted for that course. Consumed down to 0 as asks are walked.
        Map<Course, Integer> offGridAlreadyAccountedByCourse = new HashMap<>();

        for (StageAsk ask : buildStageAsks(groupsByName, classDaysRows, FCRows)) {
            StudentGroup group = ask.group();
            Course stageCourse = stageCourseByGroupId.computeIfAbsent(group.id, id -> Course.<Course>find(
                    "teacher.pseudo = true and subject = ?1 and studentGroup = ?2",
                    GoldenScheduleImportService.STAGE_NAME, group).firstResult());
            if (stageCourse == null) {
                plan.stageSkippedNoBudget += ask.hours();
                continue;
            }
            long remaining = remainingStubsByCourse.computeIfAbsent(stageCourse,
                    c -> Lesson.count("course = ?1 and pinned = false", c));
            Set<LocalTime> alreadyPinnedSlots = pinnedStageSlotsByCourse.computeIfAbsent(stageCourse,
                    c -> Lesson.<Lesson>list("course = ?1 and pinned = true and timeslot is not null", c).stream()
                            .collect(Collectors.groupingBy(l -> l.timeslot.getDate(),
                                    Collectors.mapping(l -> l.timeslot.startDateTime.toLocalTime(), Collectors.toSet()))))
                    .getOrDefault(ask.date(), Set.of());

            int alreadyCovered = Math.min(alreadyPinnedSlots.size(), ask.hours());
            plan.stageAlreadyPinned += alreadyCovered;
            int askRemaining = ask.hours() - alreadyCovered;
            if (askRemaining <= 0) continue;

            if (!dateHasAnyTimeslot(ask.date(), timeslotIndex)) {
                // Off-grid day (closure/Saturday, no Timeslot at all) - see
                // this method's own "off-grid hours draw budget too" javadoc
                // section for why the calendar-window cap must NOT apply
                // here.
                int alreadyAccounted = offGridAlreadyAccountedByCourse.computeIfAbsent(stageCourse,
                        c -> c.expectedUnscheduledHours == null ? 0 : c.expectedUnscheduledHours);
                int skippedNow = Math.min(askRemaining, alreadyAccounted);
                offGridAlreadyAccountedByCourse.put(stageCourse, alreadyAccounted - skippedNow);
                int askAfterSkip = askRemaining - skippedNow;
                if (askAfterSkip <= 0) continue; // this whole off-grid ask was already accounted for by a prior run

                // Only the remaining stub budget bounds an off-grid ask.
                int n = (int) Math.min(askAfterSkip, remaining);
                plan.stageOffGridHours += n;
                plan.stageSkippedNoBudget += askAfterSkip - n;
                if (n > 0) {
                    plan.offGridHoursByCourse.merge(stageCourse, n, Integer::sum);
                    remainingStubsByCourse.put(stageCourse, remaining - n);
                }
                continue;
            }

            int[] range = GroupCalendarService.effectiveRange(
                    rulesByGroupId.getOrDefault(group.id, List.of()), ask.date().getDayOfWeek(), ask.date());
            int openSlotCount = GroupCalendarService.openSlotCountFromRange(range);
            int calendarCapped = Math.min(askRemaining, openSlotCount);
            int n = (int) Math.min(calendarCapped, remaining);

            plan.stageSkippedCalendarClosed += askRemaining - calendarCapped;
            plan.stageSkippedNoBudget += calendarCapped - n;
            if (n <= 0) continue;

            int filled = 0;
            int considered = 0;
            for (int slot = range[0]; slot <= range[1] && considered < n; slot++) {
                LocalTime startTime = TimeslotSequence.SLOT_START_TIMES[slot];
                // Occupied by a prior run's (or the golden import's) own Stage
                // pin - not one of this run's n candidate hours.
                if (alreadyPinnedSlots.contains(startTime)) continue;
                considered++;
                Timeslot ts = timeslotIndex.get(new TimeslotKey(ask.date(), startTime));
                if (ts == null) {
                    // dateHasAnyTimeslot() (checked above) already confirmed
                    // this date has a Timeslot, and real Timeslot generation
                    // is day-level all-or-nothing (DataGenerator#generateTimeslots),
                    // so every slot in [range[0], range[1]] must resolve -
                    // fail loud rather than silently mis-accounting an hour
                    // if that invariant is ever violated (e.g. a future
                    // partial-day Timeslot model).
                    throw new IllegalStateException("planStagePins: date " + ask.date() + " has at least one Timeslot "
                            + "(dateHasAnyTimeslot) but not at slot " + slot
                            + " - Timeslot generation is expected to be day-level all-or-nothing");
                }
                plan.pins.add(new PlannedPin(stageCourse, ts));
                filled++;
            }
            // Window exhausted before all n candidates could be attempted
            // (prior pins crowd the open window) - account as calendar-shaped
            // shortage so the counters keep partitioning every asked hour.
            plan.stageSkippedCalendarClosed += n - considered;
            plan.stagePinned += filled;
            remainingStubsByCourse.put(stageCourse, remaining - filled);
        }
        if (plan.stagePinned > 0 || plan.stageSkippedCalendarClosed > 0 || plan.stageSkippedNoBudget > 0
                || plan.stageOffGridHours > 0 || plan.stageAlreadyPinned > 0) {
            LOG.infof("Quota/class-days-driven Stage pins (TASK-381 \"stage on the grid\"): %d pinned, %d already "
                    + "pinned (prior run/golden), %d skipped (calendar closed), %d skipped (no stub budget), %d "
                    + "off-grid (closures/Saturdays - draws budget, becomes expectedUnscheduledHours)",
                    plan.stagePinned, plan.stageAlreadyPinned, plan.stageSkippedCalendarClosed, plan.stageSkippedNoBudget, plan.stageOffGridHours);
        }
        return plan;
    }

    private static final class ModaPlan {
        /** Every individual Lesson pin planned (one calendario_moda.csv row spans {@code hours} pins, not 1). */
        final List<PlannedPin> pins = new ArrayList<>();
        final List<CalendarioImportReport.ModaFailure> failures = new ArrayList<>();
        int skippedNoTimeslot = 0;
        /** Rows skipped by the per-course stub budget - "Orario wins" cap (TASK-379). */
        int skippedBeyondCourseHours = 0;
        /** Row-level count (unlike {@code pins.size()}) - partitions {@code modaRows} together with {@link #skippedNoTimeslot}/{@link #failures}. */
        int rowsPlanned = 0;
    }

    /** (studentGroupId or teacherId, timeslotId) - shared key shape for the two independent reservation maps below. */
    private record ReservationKey(Long entityId, Long timeslotId) {}

    /**
     * Outcome of planning one calendario_moda.csv row. Exactly one of the
     * three is populated: {@code pins} (success), {@code skippedNoTimeslot}
     * (non-blocking - see {@link CalendarioImportReport#modaSkippedNoTimeslot}),
     * or {@code failureReason} (blocking - see {@link CalendarioImportReport#modaFailures}).
     */
    private record RowOutcome(List<PlannedPin> pins, boolean skippedNoTimeslot, String failureReason) {
        static RowOutcome success(List<PlannedPin> pins) { return new RowOutcome(pins, false, null); }
        static RowOutcome skip() { return new RowOutcome(null, true, null); }
        static RowOutcome fail(String reason) { return new RowOutcome(null, false, reason); }
    }

    private ModaPlan planModaPins(List<String[]> rows, Map<String, StudentGroup> groupsByName,
                                  Map<Long, List<GroupCalendarRule>> rulesByGroupId, Map<CourseKey, CourseResolution> courseIndex,
                                  Map<TimeslotKey, Timeslot> timeslotIndex) {
        ModaPlan plan = new ModaPlan();

        // Reservation maps seeded from already-pinned Lessons (idempotent
        // re-run support: a second import against a profile that already has
        // some pins reports pre-existing conflicts too), then updated as each
        // new pin is tentatively planned - this is what catches the one known
        // same-day mixed-shift overlap (2027-06-09, G05 T16/T17).
        Map<ReservationKey, Course> byGroupSlot = new HashMap<>();
        Map<ReservationKey, Course> byTeacherSlot = new HashMap<>();
        // "Orario wins" cap (TASK-379): a course may never receive more pins
        // than incarichi (Orario-aligned since TASK-371) assigns it -
        // Course.totalHours minus pins already in the DB (tallied from the
        // pinned list already loaded below - no extra COUNT queries). The
        // turni sheet books whole shifts to the lab course even on dates
        // where the orario taught other subjects in those hours; rows beyond
        // the budget are skipped. Based on totalHours, not the live stub
        // count, so it holds even on fixtures without LessonGenerator stubs.
        // KNOWN LIMITATION (altitude review): the cap fixes the TOTAL but
        // picks the dropped rows by date order (latest lose), while the true
        // turni/orario divergences are scattered - per-date reconciliation
        // belongs in the extractor when the school's revised sheets arrive
        // (TASK-364 note).
        Map<Course, Long> remainingByCourse = new HashMap<>();
        Map<Course, Long> alreadyPinnedByCourse = new HashMap<>();
        // Pins on the target MODA courses are excluded from the budget tally:
        // a real (force) import deletes exactly those pins before planning
        // (refuseOrClearPriorPins), so counting them here would make a DRY RUN
        // over an already-imported profile predict "all rows over budget"
        // instead of the force re-run's actual outcome (code review, TASK-379).
        Set<Course> targetCourses = new HashSet<>();
        for (CourseResolution resolution : courseIndex.values()) {
            if (resolution.course() != null) targetCourses.add(resolution.course());
        }
        for (Lesson existing : Lesson.<Lesson>list("pinned = true and course.studentGroup is not null and course.teacher is not null")) {
            byGroupSlot.put(new ReservationKey(existing.course.studentGroup.id, existing.timeslot.id), existing.course);
            byTeacherSlot.put(new ReservationKey(existing.course.teacher.id, existing.timeslot.id), existing.course);
            if (!targetCourses.contains(existing.course)) {
                alreadyPinnedByCourse.merge(existing.course, 1L, Long::sum);
            }
        }
        // The date-ordering the cap's "latest lose" semantics depend on is
        // enforced, not assumed: rows are planned in ascending date order via
        // a sorted view of the indexes, preserving original CSV line numbers
        // in failure reports (and never mutating the caller's list).
        List<Integer> visitOrder = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) visitOrder.add(i);
        visitOrder.sort(java.util.Comparator.comparing(i -> rows.get(i)[0].trim()));

        for (int i : visitOrder) {
            String[] row = rows.get(i);
            int lineNumber = i + 2; // 1-based; header is line 1
            RowOutcome outcome = planOneRow(row, groupsByName, rulesByGroupId, courseIndex, timeslotIndex, byGroupSlot, byTeacherSlot);
            if (outcome.skippedNoTimeslot()) {
                plan.skippedNoTimeslot++;
            } else if (outcome.failureReason() != null) {
                plan.failures.add(new CalendarioImportReport.ModaFailure(
                        lineNumber, row[0].trim(), row[1].trim(), row[2].trim(), outcome.failureReason()));
            } else {
                Course course = outcome.pins().get(0).course();
                long remaining = remainingByCourse.computeIfAbsent(course,
                        c -> c.totalHours - alreadyPinnedByCourse.getOrDefault(c, 0L));
                if (outcome.pins().size() > remaining) {
                    // planOneRow already wrote this row's reservations; a
                    // skipped row must not keep phantom slot claims that would
                    // fail later legitimate rows as false teacher/group
                    // conflicts (all-or-nothing would then block the batch).
                    for (PlannedPin pin : outcome.pins()) {
                        byGroupSlot.remove(new ReservationKey(pin.course().studentGroup.id, pin.timeslot().id));
                        byTeacherSlot.remove(new ReservationKey(pin.course().teacher.id, pin.timeslot().id));
                    }
                    plan.skippedBeyondCourseHours++;
                    LOG.warnf("  beyond-course-hours: line=%d date=%s group=%s initial=%s - course '%s' at its %d-hour incarichi budget (Orario wins, TASK-379)",
                            lineNumber, row[0].trim(), row[1].trim(), row[2].trim(), course.subject, course.totalHours);
                    continue;
                }
                remainingByCourse.put(course, remaining - outcome.pins().size());
                plan.pins.addAll(outcome.pins());
                plan.rowsPlanned++;
            }
        }
        return plan;
    }

    private RowOutcome planOneRow(String[] row, Map<String, StudentGroup> groupsByName,
                                  Map<Long, List<GroupCalendarRule>> rulesByGroupId, Map<CourseKey, CourseResolution> courseIndex,
                                  Map<TimeslotKey, Timeslot> timeslotIndex,
                                  Map<ReservationKey, Course> byGroupSlot, Map<ReservationKey, Course> byTeacherSlot) {
        String date = row[0].trim();
        String groupName = row[1].trim();
        String initial = row[2].trim();
        String shift = row[4].trim();

        int hours;
        try {
            hours = Integer.parseInt(row[3].trim());
        } catch (NumberFormatException e) {
            return RowOutcome.fail("invalid hours value '" + row[3].trim() + "'");
        }

        String teacherName;
        try {
            teacherName = resolveTeacherName(initial);
        } catch (IllegalArgumentException e) {
            return RowOutcome.fail(e.getMessage());
        }

        StudentGroup group = groupsByName.get(groupName);
        if (group == null) {
            return RowOutcome.fail("unknown student group '" + groupName + "'");
        }

        CourseResolution resolution = courseIndex.get(new CourseKey(teacherName, group.id));
        if (resolution == null || resolution.course() == null) {
            return RowOutcome.fail(resolution != null ? resolution.error()
                    : "no course index entry for teacher '" + teacherName + "' group '" + groupName + "'");
        }
        Course course = resolution.course();

        int[] slotRange;
        try {
            slotRange = computeSlotRange(shift, hours);
        } catch (IllegalArgumentException e) {
            return RowOutcome.fail(e.getMessage());
        }

        LocalDate localDate;
        try {
            localDate = LocalDate.parse(date);
        } catch (Exception e) {
            return RowOutcome.fail("invalid date '" + date + "'");
        }

        List<GroupCalendarRule> groupRules = rulesByGroupId.getOrDefault(group.id, List.of());
        List<PlannedPin> rowPins = new ArrayList<>();
        for (int slot = slotRange[0]; slot <= slotRange[1]; slot++) {
            LocalTime startTime = TimeslotSequence.SLOT_START_TIMES[slot];
            Timeslot timeslot = timeslotIndex.get(new TimeslotKey(localDate, startTime));
            if (timeslot == null) {
                // Non-blocking (TASK-364 code review): mirrors GoldenImportReport's
                // unmatchedNoTimeslot bucket - on the current seed this is exactly
                // the 2 rows dated after TimeslotSequence.SCHOOL_YEAR_END
                // (2027-06-14, 2027-06-15), a data-horizon fact rather than a
                // genuine placement problem, so it must not block the rest of
                // the batch (see also the class javadoc's testability rationale).
                return RowOutcome.skip();
            }
            if (!GroupCalendarService.isOpen(groupRules, timeslot)) {
                return RowOutcome.fail("group calendar closed for " + group.name + " on " + localDate + " slot " + slot);
            }
            ReservationKey groupKey = new ReservationKey(group.id, timeslot.id);
            Course groupConflict = byGroupSlot.get(groupKey);
            if (groupConflict != null && !groupConflict.equals(course)) {
                return RowOutcome.fail("student group conflict: " + group.name + " already has '" + groupConflict.subject
                        + "' (" + groupConflict.teacher.name + ") at " + localDate + " slot " + slot);
            }
            ReservationKey teacherKey = new ReservationKey(course.teacher.id, timeslot.id);
            Course teacherConflict = byTeacherSlot.get(teacherKey);
            if (teacherConflict != null && !teacherConflict.equals(course)) {
                return RowOutcome.fail("teacher conflict: " + teacherName + " already teaches '" + teacherConflict.subject
                        + "' to " + teacherConflict.studentGroup.name + " at " + localDate + " slot " + slot);
            }
            rowPins.add(new PlannedPin(course, timeslot));
        }

        // Only reserve once every slot in the row is known-good - a mid-row
        // failure must not leave partial reservations behind for this row.
        for (PlannedPin pin : rowPins) {
            byGroupSlot.put(new ReservationKey(group.id, pin.timeslot.id), pin.course);
            byTeacherSlot.put(new ReservationKey(course.teacher.id, pin.timeslot.id), pin.course);
        }
        return RowOutcome.success(rowPins);
    }

    /**
     * Resolves the MODA TUTTI teacher initial (PE/MU/BO) to a teacher name via
     * {@link #INITIAL_TO_TEACHER}. Extracted as its own small static method
     * (TASK-364 code review) so the mapping-failure path is unit-testable
     * without a database - calendario_moda.csv's own rows never contain an
     * unrecognized initial (verified against the real CSV), so this failure
     * mode can only be exercised directly.
     */
    static String resolveTeacherName(String initial) {
        String teacherName = INITIAL_TO_TEACHER.get(initial);
        if (teacherName == null) {
            throw new IllegalArgumentException(
                    "unknown teacher initial '" + initial + "' - expected one of " + INITIAL_TO_TEACHER.keySet());
        }
        return teacherName;
    }

    /**
     * Resolves the MODA lab Course for (teacherName, group) out of that
     * teacher's already-fetched candidate courses (see {@link #buildModaCourseIndex}
     * - no DB access here): prefers the teacher's lab-tagged course
     * (Course.isLab, e.g. T17's "Modello (Moulage)" / T16's
     * "Confezione"), falling back to the teacher's sole non-pseudo course for
     * that group when no lab-tagged course exists - T07's "Tecnologia e
     * creatività" isn't classified as a lab subject by DataGenerator.getRoomTags()
     * (it teaches no room-tagged lab), so her course is only found via this
     * fallback. Fails loud if either stage finds more than one candidate (a
     * real ambiguity, not silently guessed).
     */
    private CourseResolution resolveModaCourse(String teacherName, String groupName, List<Course> candidatesForTeacherGroup) {
        List<Course> labCandidates = candidatesForTeacherGroup.stream().filter(c -> c.isLab).toList();
        if (labCandidates.size() == 1) {
            return new CourseResolution(labCandidates.get(0), null);
        }
        if (labCandidates.size() > 1) {
            return new CourseResolution(null, "ambiguous lab course for teacher '" + teacherName + "' group '"
                    + groupName + "' - " + labCandidates.size() + " candidates: " + subjectsOf(labCandidates));
        }
        List<Course> nonPseudoCandidates = candidatesForTeacherGroup.stream()
                .filter(c -> c.teacher != null && !c.teacher.pseudo).toList();
        if (nonPseudoCandidates.size() != 1) {
            return new CourseResolution(null, "cannot resolve a unique course for teacher '" + teacherName
                    + "' group '" + groupName + "' - expected exactly 1 fallback candidate (no lab-tagged course), found "
                    + nonPseudoCandidates.size() + ": " + subjectsOf(nonPseudoCandidates));
        }
        return new CourseResolution(nonPseudoCandidates.get(0), null);
    }

    private String subjectsOf(List<Course> courses) {
        return courses.stream().map(c -> c.subject).toList().toString();
    }

    /**
     * Shift-to-slot mapping (TASK-364, verified against every distinct MODA
     * TUTTI cell magnitude - 1,2,3,5,6 - via the golden lessons.csv, see
     * claudedocs/spalmone-2026-27/calendario-scolastico-format.md section 3
     * and this class's package javadoc):
     * <ul>
     *   <li>first shift (negative cell value): anchored at slot 0 (08:00),
     *   growing forward for {@code hours} slots - there is no room to grow
     *   backward, so this is the only possible direction. Verified for
     *   hours=3 (typical) and hours=5 (2027-06-09, G05 T16).</li>
     *   <li>second shift (positive cell value), hours &lt;= 3: anchored at
     *   slot 3 (11:00), growing forward - "from the shift boundary outward",
     *   away from the first shift. Verified for hours=1,2,3.</li>
     *   <li>second shift, hours &gt; 3: a block that size cannot start at slot
     *   3 without running past the day's usable slots (3+hours-1 &gt;= 6), so it
     *   falls back to anchor 0 like the first shift - verified for hours=6
     *   (recurring G05 T16 rows, Apr-May 2027), which the golden grid
     *   places at 08:00-14:00 (slots 0-5), not 11:00-17:00. hours=4 takes the
     *   same &gt;3 branch (anchor 0, slots 0-3) by construction, but - unlike
     *   3/5/6 - it is NOT golden-verified: {@code calendario_moda.csv} never
     *   contains a magnitude-4 cell (TASK-364 code review K8, confirmed
     *   against every distinct (shift, hours) pair in the real 2026/27
     *   workbook), so this is fallback-rule semantics only, exercised by
     *   {@code computeSlotRange_secondShiftHoursFourFallsBackToZero} rather
     *   than by any real row.</li>
     * </ul>
     * The one calendario_moda.csv (date, group) pair where a first-shift and
     * second-shift row combine to more than 6 total hours (2027-06-09, G05:
     * T16 -5h + T17 +1h) produces overlapping ranges under this rule
     * (T16 claims slots 0-4, T17's naive slot 3 collides) - this is
     * caught as a student-group conflict by {@link #planOneRow}, not silently
     * resolved, matching the format doc's own "8 true divergences (all
     * T16 G05 mixed placements)" callout: the calendario's raw
     * prediction and the human-adjusted golden solution genuinely disagree
     * here, and the import surfaces that rather than picking one.
     */
    static int[] computeSlotRange(String shift, int hours) {
        boolean first;
        if ("first".equals(shift)) {
            first = true;
        } else if ("second".equals(shift)) {
            first = false;
        } else {
            throw new IllegalArgumentException("invalid shift '" + shift + "' - expected 'first' or 'second'");
        }
        if (hours <= 0) {
            throw new IllegalArgumentException("invalid hours " + hours + " - expected a positive integer");
        }
        int anchor = (first || hours > 3) ? 0 : 3;
        int endSlot = anchor + hours - 1;
        if (endSlot >= TimeslotSequence.SLOTS_PER_DAY) {
            throw new IllegalArgumentException("shift='" + shift + "' hours=" + hours + " does not fit in a "
                    + TimeslotSequence.SLOTS_PER_DAY + "-slot day (would need slots " + anchor + ".." + endSlot + ")");
        }
        return new int[] {anchor, endSlot};
    }

    // ─── Report-only cross-checks (never persisted) ──────────────────────

    private List<CalendarioImportReport.CalendarDiscrepancy> crossCheckFC(List<String[]> rows, Map<String, StudentGroup> groupsByName,
                                                                           Map<Long, List<GroupCalendarRule>> rulesByGroupId,
                                                                           Map<Long, List<StagePattern>> stagePatternsByGroupId) {
        List<CalendarioImportReport.CalendarDiscrepancy> discrepancies = new ArrayList<>();
        for (String[] row : rows) {
            String groupName = row[0].trim();
            String dateStr = row[1].trim();
            double aula = Double.parseDouble(row[2].trim());
            double lab = Double.parseDouble(row[3].trim());
            double stage = Double.parseDouble(row[4].trim());
            double total = aula + lab + stage;
            if (total <= 0) continue; // weekend/no-school day - nothing to cross-check

            crossCheckDay(discrepancies, groupsByName, rulesByGroupId, stagePatternsByGroupId, groupName, dateStr,
                    total, stage > 0, "calendario_fc.csv", "stage_hours=" + stage);
        }
        return discrepancies;
    }

    private List<CalendarioImportReport.CalendarDiscrepancy> crossCheckClassDays(List<String[]> rows, Map<String, StudentGroup> groupsByName,
                                                                                 Map<Long, List<GroupCalendarRule>> rulesByGroupId,
                                                                                 Map<Long, List<StagePattern>> stagePatternsByGroupId,
                                                                                 Set<LocalDate> holidays) {
        List<CalendarioImportReport.CalendarDiscrepancy> discrepancies = new ArrayList<>();
        Set<GroupDateKey> stageDaysReported = new HashSet<>();
        for (String[] row : rows) {
            String groupName = row[0].trim();
            String dateStr = row[1].trim();
            double hours = Double.parseDouble(row[2].trim());
            String modality = row[3].trim();
            if (hours <= 0) continue;

            boolean isStageDay = "S".equals(modality);
            crossCheckDay(discrepancies, groupsByName, rulesByGroupId, stagePatternsByGroupId, groupName, dateStr,
                    hours, isStageDay, "calendario_class_days.csv", "modality=S");
            if (isStageDay) {
                StudentGroup group = groupsByName.get(groupName);
                if (group != null) {
                    stageDaysReported.add(new GroupDateKey(group.id, LocalDate.parse(dateStr)));
                }
            }
        }
        crossCheckStagePatternReverse(discrepancies, groupsByName, stagePatternsByGroupId, stageDaysReported, holidays);
        return discrepancies;
    }

    /** (studentGroupId, date) - shared key for the StagePattern reverse check (K6) below. */
    private record GroupDateKey(Long studentGroupId, LocalDate date) {}

    /**
     * Reverse of {@link #crossCheckDay}'s stage check (TASK-364 code review
     * K6, see that method's javadoc for the overall forward/reverse scope):
     * for every {@link StagePattern} day-in-window (weekends and
     * {@link SchoolHoliday} dates already excluded by
     * {@link StagePattern#expandDates}), flags it if
     * {@code calendario_class_days.csv} reported zero stage hours
     * ({@code modality != 'S'}, or no row at all) for that (group, date) - the
     * calendario doesn't corroborate a stage day the schedule expects.
     *
     * <p>Sourced from {@code calendario_class_days.csv} only, not also
     * {@code calendario_fc.csv}: it covers every group that can have a
     * StagePattern, whereas the FC sheet only names G04/G08/G13, so
     * running the same reverse check a second time against the FC rows
     * would only ever find a strict subset of what this pass already finds.</p>
     */
    private void crossCheckStagePatternReverse(List<CalendarioImportReport.CalendarDiscrepancy> discrepancies,
                                               Map<String, StudentGroup> groupsByName,
                                               Map<Long, List<StagePattern>> stagePatternsByGroupId,
                                               Set<GroupDateKey> classDaysStageDays, Set<LocalDate> holidays) {
        for (StudentGroup group : groupsByName.values()) {
            for (StagePattern pattern : stagePatternsByGroupId.getOrDefault(group.id, List.of())) {
                for (LocalDate date : pattern.expandDates(holidays)) {
                    if (!classDaysStageDays.contains(new GroupDateKey(group.id, date))) {
                        discrepancies.add(new CalendarioImportReport.CalendarDiscrepancy(group.name, date.toString(),
                                "stage-pattern-without-calendario",
                                "StagePattern covers " + group.name + " on " + date
                                        + " but calendario_class_days.csv reports zero stage hours that day"));
                    }
                }
            }
        }
    }

    /**
     * Shared (group, date) coherence check (TASK-364 code review - unifies
     * what was two near-identical methods): flags a day whose calendario
     * hours exceed what {@code GroupCalendarRule} allows, and/or whose stage
     * indicator (stage_hours&gt;0 or modality=S, depending on {@code sourceCsv})
     * falls outside every {@link StagePattern} window for that group.
     *
     * <p><b>Scope (TASK-364 code review K6):</b> both checks here are
     * FORWARD-only - calendario row -&gt; does the modeled data agree. The two
     * possible reverse directions are handled differently, deliberately:</p>
     * <ul>
     *   <li><b>StagePattern -&gt; calendario</b> (a StagePattern day-in-window
     *   with zero reported calendario stage hours) IS checked, separately, by
     *   {@link #crossCheckStagePatternReverse} - cheap to add because every
     *   input it needs (the StagePattern windows, the per-(group,date)
     *   stage-day set) is already built for this forward pass.</li>
     *   <li><b>GroupCalendarRule -&gt; calendario</b> (an open day the
     *   calendario shows as unexpectedly empty) is NOT checked and stays out
     *   of scope: unlike {@link StagePattern}, {@code GroupCalendarRule}
     *   carries no per-day "this day must have hours" semantics, so an open
     *   day with low/no calendario hours is unremarkable (e.g. a genuine
     *   absence) rather than a coherence signal - there's no cheap, low-noise
     *   check to add on that side.</li>
     * </ul>
     */
    private void crossCheckDay(List<CalendarioImportReport.CalendarDiscrepancy> discrepancies, Map<String, StudentGroup> groupsByName,
                               Map<Long, List<GroupCalendarRule>> rulesByGroupId, Map<Long, List<StagePattern>> stagePatternsByGroupId,
                               String groupName, String dateStr, double totalHours, boolean isStageDay, String sourceCsv, String stageDetail) {
        StudentGroup group = groupsByName.get(groupName);
        if (group == null) {
            discrepancies.add(new CalendarioImportReport.CalendarDiscrepancy(
                    groupName, dateStr, "unknown group", sourceCsv + " references '" + groupName + "'"));
            return;
        }

        LocalDate date = LocalDate.parse(dateStr);
        int[] range = GroupCalendarService.effectiveRange(rulesByGroupId.getOrDefault(group.id, List.of()), date.getDayOfWeek(), date);
        int openSlots = GroupCalendarService.openSlotCountFromRange(range);
        if (totalHours - openSlots >= CAPACITY_DISCREPANCY_THRESHOLD_HOURS) {
            discrepancies.add(new CalendarioImportReport.CalendarDiscrepancy(groupName, dateStr,
                    "exceeds calendar capacity",
                    sourceCsv + " hours=" + totalHours + "h > GroupCalendarRule allows " + openSlots + "h"));
        }
        if (isStageDay && !withinAnyStagePattern(stagePatternsByGroupId.getOrDefault(group.id, List.of()), date)) {
            discrepancies.add(new CalendarioImportReport.CalendarDiscrepancy(groupName, dateStr,
                    "stage indicator outside StagePattern window",
                    sourceCsv + " " + stageDetail + " but no StagePattern covers this date/weekday"));
        }
    }

    private boolean withinAnyStagePattern(List<StagePattern> patterns, LocalDate date) {
        for (StagePattern pattern : patterns) {
            if (pattern.startDate == null || pattern.endDate == null) continue;
            if (date.isBefore(pattern.startDate) || date.isAfter(pattern.endDate)) continue;
            if (pattern.dayOfWeekMask.contains(date.getDayOfWeek())) {
                return true;
            }
        }
        return false;
    }
}
