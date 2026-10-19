package com.school.planner.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.school.planner.service.FailureAnalysis;
import com.school.planner.service.GroupCalendarService;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.solver.SolverStatus;

import com.school.planner.solver.BaseConstraintProvider;
import com.school.planner.solver.TimeslotSequence;

@PlanningSolution
public class SchoolSchedule {

    /**
     * Static reference to all timeslots for ValueRangeProviders in planning entities.
     * Set by ScheduleService before solving, used by Lesson getAvailableTimeslots().
     */
    private static volatile List<Timeslot> allTimeslotsStatic = new ArrayList<>();

    /**
     * Static reference to all rooms for ValueRangeProviders in planning entities.
     * Set by ScheduleService before solving, used by Lesson getAvailableRooms().
     */
    private static volatile List<Room> allRoomsStatic = new ArrayList<>();

    // TASK-455 lab tag-capacity (scarcity-aware spec, 2026-07-30): per-tag count of
    // ASSIGNABLE rooms (capacity > 0, i.e. Room.isAssignable; R18 cap-0
    // excluded). Published pre-solve from ScheduleService.refreshSolverStatics (which
    // holds the rooms) so the M-TC constraint can penalize tag demand beyond physical
    // capacity per (date, hour) cell. A single static fact, invariant during the solve
    // (rooms do not move), published whether or not the flag is on.
    private static volatile Map<String, Integer> roomCapacityByTagStatic = new HashMap<>();

    // TASK-464 co-presenza ospite: golden co-presenza depth per (tag, weekday, slotStart)
    // cell, i.e. how many DISTINCT groups the golden itself co-locates there (the practice
    // to replicate, mechanically read from seed/lessons.csv by ConstrainedTeacherAllocator.
    // loadGoldenCoPresenceDepth). Published pre-solve from refreshSolverStatics whether or
    // not the flag is on; the H2 pairwise exemption and the H-COPRESENCE count cap both read
    // it, and both short-circuit to inert via the leading `enabled &&` when the flag is off.
    // Key format: {@link #coPresenceCell}; a cell absent from the map reads depth 0, so only
    // golden-practiced cells (depth >= 2) ever unlock the exemption (AC#4: never generalized).
    private static volatile Map<String, Integer> goldenCoPresenceDepthStatic = new HashMap<>();

    /**
     * Static reference to all lessons for ValueRangeProvider access.
     * Set by ScheduleService before solving, used by Lesson.getAvailableTimeslots()
     * to filter out timeslots occupied by pinned lessons for the same student group.
     */
    private static volatile List<Lesson> allLessonsStatic = new ArrayList<>();

    /**
     * Static map of student group ID -> set of occupied timeslots by pinned lessons.
     * Pre-computed for O(1) lookup in Lesson.getAvailableTimeslots() to avoid
     * iterating through all lessons during CH phase.
     */
    private static volatile Map<Long, Set<Timeslot>> pinnedLessonTimeslotsByGroupStatic = new HashMap<>();

    /**
     * TASK-424: student group ID -&gt; (date -&gt; how many AUTHORITATIVE real-teacher
     * pinned hours that day already carries) - the day-fill family's netting basis
     * ({@code SchoolScheduleConstraintProvider#effectiveQuota} subtracts it from the
     * day's expected-hours target, so M20/M20.2/M33 and the repair pricers compare
     * the SOLVER's own occupancy against the capacity the solver actually still owns).
     * Published in the same pass as {@link #pinnedLessonTimeslotsByGroupStatic}, from
     * the same lesson list.
     *
     * <p>WHY A PRE-SOLVE STATIC IS SOUND (staleness invariant). A score stream cannot
     * scan the lesson population per match, so this has to be a map - and it may only
     * be a map because its content cannot change while the solve runs: an AUTHORITATIVE
     * pin is a placement the SCHOOL fixed (golden/calendario/stage/lab-rotation), it is
     * immobile for Timefold and no move can create, move or destroy one. The only pins
     * that DO appear mid-cycle are the TASK-419 PROMOTED ones, and those are excluded
     * here by {@code BaseConstraintProvider.isAuthoritativePin} exactly as they are
     * excluded from every other pinned exemption: a promoted lesson keeps counting as
     * ordinary OCCUPANCY, so netting it would double-count it (once out of the target,
     * once into the count) and the score would jump at smarcatura.
     *
     * <p>PSEUDO-TEACHER (Stage) PINS ARE NOT NETTED. The target this is subtracted from
     * is aula+lab only ({@code dailyQuota}, TASK-385): Stage hours were never part of
     * that basis, so netting them would shrink a target they never inflated. Same
     * aula+lab-only reasoning the construction side already applies by netting
     * {@code pseudoDailyLessonCount} out of its own comparison count.
     */
    private static volatile Map<Long, Map<LocalDate, Integer>> authoritativePinnedHoursByGroupDateStatic =
            new HashMap<>();

    /**
     * TASK-461 supply cap: per-group ratio ({@code <=1}) that scales M20's daily target
     * down where the school's capacity plan (calendario aula+lab) exceeds the hours the
     * curriculum actually assigns the group (incarichi). M20 targets capacity, so where
     * capacity > curriculum the target is partly unreachable and the solver pays a deficit
     * it cannot fill (G04 85h, G08 40h, G09 4h, ~129k medium floor). The ratio =
     * min(1, curriculum-hours / capacity-hours) trims the plan to the curriculum so the
     * floor vanishes. Applied in SchoolScheduleConstraintProvider#dailyQuota (the single
     * source M20/M33/M20.2 and the repairs all derive from), so every consumer inherits
     * it. {@code null} (or a missing group) means no cap: the getter returns 1.0.
     * Published once per solve setup by ScheduleService.refreshSolverStatics.
     */
    private static volatile Map<Long, Double> supplyCapRatioByGroupStatic = null;

    /**
     * Static map of student group IDs to VRP-buffered ideal end dates.
     * Set by ScheduleService before solving, used by ValueRangeProviders.
     */
    private static volatile Map<Long, LocalDate> idealEndDateVrpBufferedStatic = new HashMap<>();

    /**
     * TASK-397: static cache of the full {@link IdealEndDateInfo} per group, computed
     * ONCE under the same guard as the other statics (the ideal end date is invariant
     * during a solve). Every {@code loadSchedule} - including mid-solve UI fetches -
     * cheaply re-applies this onto the freshly-listed (transient-field) group objects so
     * the UI's ideal-end marker keeps rendering during the solve, WITHOUT the ~591
     * expensive recomputes/solve the unguarded call used to do.
     */
    private static volatile Map<Long, IdealEndDateInfo> idealEndDateInfoStatic = new HashMap<>();

    public static Map<Long, IdealEndDateInfo> getIdealEndDateInfoStatic() {
        return idealEndDateInfoStatic;
    }

    public static void setIdealEndDateInfoStatic(Map<Long, IdealEndDateInfo> map) {
        idealEndDateInfoStatic = map != null ? map : new HashMap<>();
    }

    /**
     * Static reference to teacher availabilities grouped by teacher ID.
     * Set by ScheduleService before solving, used by ValueRangeProviders to filter
     * timeslots where teacher is unavailable (VACATION/SICK).
     */
    private static volatile Map<Long, List<TeacherAvailability>> teacherAvailabilitiesStatic = new HashMap<>();

    /**
     * Static set of school holiday dates.
     * Set by ScheduleService before solving, used by ValueRangeProviders to filter
     * timeslots on holidays.
     */
    private static volatile Set<LocalDate> schoolHolidaysStatic = new HashSet<>();

    /**
     * Static map of student group ID to stage blackout dates.
     * Set by ScheduleService before solving, used by ValueRangeProviders to filter
     * timeslots on stage days for non-professionalizzanti courses (H4 VRP).
     */
    private static volatile Map<Long, Set<LocalDate>> stageBlackoutDatesStatic = new HashMap<>();

    /**
     * Static reference to TimeslotSequence for block validity checks.
     * Set by ScheduleService before solving, used by Lesson ValueRangeProvider.
     * to filter start timeslots where the block can fit within the day.
     */
    private static volatile TimeslotSequence timeslotSequenceStatic = null;

    /**
     * Static set of teacher IDs who are lab assistants.
     * Set by ScheduleService before solving, used by M-LA constraints to quickly
     * filter entities in forEach() clauses (only ~4 teachers out of ~28).
     */
    private static volatile Set<Long> labAssistantTeacherIdsStatic = new HashSet<>();

    /**
     * Static map of lab assistant teacher ID → room tag.
     * Used by M-LA constraints for O(1) lookup of which room a teacher assists in.
     */
    private static volatile Map<Long, String> labAssistantRoomTagByTeacherIdStatic = new HashMap<>();

    /**
     * Static map of lesson ID → available timeslot count.
     * Pre-computed at solve start for O(1) MRV-based difficulty comparison.
     * Survives Timefold entity cloning because lessons retain their id.
     */
    private static volatile Map<Long, Integer> lessonAvailableTimeslotCountsStatic;

    /**
     * Static map of student group ID -> that group's GroupCalendarRules (TASK-342).
     * Set by ScheduleService before solving. Consulted by Lesson.getAvailableTimeslots()
     * (VRP filtering) and by M18/H20 in the constraint providers, since constraints and
     * planning entities cannot inject the CDI GroupCalendarService. Resolution logic
     * itself lives in GroupCalendarService's static helpers so it isn't duplicated here.
     */
    private static volatile Map<Long, List<GroupCalendarRule>> groupCalendarRulesByGroupStatic = new HashMap<>();

    /**
     * Static map of student group ID -> (date -> that group's {@link
     * DailyActivityQuota} row) (TASK-381 "L1-bis"). Set by ScheduleService
     * before solving. Consulted by {@code Lesson.getAvailableTimeslots()}
     * (VRP filtering) and by H23/H24 in {@code BaseConstraintProvider}, for
     * the same CDI-unreachability reason {@link #groupCalendarRulesByGroupStatic}
     * is static. A group with no entry at all here is unrestricted (see
     * {@link #isQuotaGroup}).
     */
    private static volatile Map<Long, Map<LocalDate, DailyActivityQuota>> dailyActivityQuotasByGroupStatic = new HashMap<>();

    /**
     * Static map of student group ID -&gt; (date -&gt; that group's {@link
     * DailyActivityQuota} row), covering EVERY row regardless of {@link
     * DailyActivityQuota#FCQuota} (TASK-385) - backs the universal
     * expected-hours fact ({@link #getExpectedHours}). Unlike {@link
     * #dailyActivityQuotasByGroupStatic} above (FC-scoped, backs {@link
     * #isQuotaGroup}/{@link #getDailyQuota}/{@link
     * #getDailyActivityQuotasForGroup}), both maps are (re)built together
     * from the SAME input list by {@link #setDailyActivityQuotasStatic} -
     * single source of truth for which rows count as which.
     */
    private static volatile Map<Long, Map<LocalDate, DailyActivityQuota>> expectedHoursByGroupStatic = new HashMap<>();

    /**
     * Static set of student group IDs that have at least one {@link
     * DailyActivityQuota} row with {@code stageHours > 0} (TASK-384). Built
     * alongside {@link #expectedHoursByGroupStatic} in the same {@link
     * #setDailyActivityQuotasStatic} pass - single source of truth for "does
     * this group have any stage-day fact at all", consulted by {@link
     * #groupHasStageFacts} (the Lesson VRP confinement filter and its H-tier
     * safety net both hoist this once per lesson rather than scanning the
     * group's whole date map).
     */
    private static volatile Set<Long> groupsWithStageFactsStatic = new HashSet<>();

    /**
     * TASK-536: pre-solve per-group HARD day floor (group id -> floor hours),
     * derived by {@code service.DayFloorDeriver} as {@code min(4, mathFloor,
     * goldenExistenceMin)} - the math floor is the documented 5h aspiration
     * (referente question), the enforced value is the two-level spec (4h for 12
     * groups, 2h for G08/G09, 3h G14). Published ONCE per solve setup by
     * {@code ScheduleService.refreshSolverStatics}; read by the H33 constraint.
     * Empty map = floor inert (derivation degraded), never blocks a solve.
     */
    private static volatile Map<Long, Integer> dayFloorsStatic = new HashMap<>();

    /** TASK-536: publish the per-group hard day floors (see {@link #dayFloorsStatic}). */
    public static synchronized void setDayFloorsStatic(Map<Long, Integer> floors) {
        dayFloorsStatic = floors == null ? new HashMap<>() : new HashMap<>(floors);
    }

    /** TASK-536: the group's hard day floor in hours, or null/0 = no floor (H33 inert for it). */
    public static Integer getDayFloor(Long groupId) {
        return dayFloorsStatic.get(groupId);
    }

    /**
     * TASK-558 spike: per-group average DECLARED ordinary quota (mean of
     * aula+lab over the {@link DailyActivityQuota} rows with aula+lab &gt; 0),
     * built in the same {@link #setDailyActivityQuotasStatic} pass from the
     * SAME date-deduped all-rows map - single source with {@link
     * #expectedHoursByGroupStatic}. The M22 quota-aware min-days amendment
     * reads it so the "minimum number of days" derives from the school's own
     * declared daily plan instead of a maxHoursPerDay packing assumption.
     * Absent group (synthetic fixtures, non-sheet datasets) = the constraint
     * falls back to the legacy form.
     */
    private static volatile Map<Long, Double> averageDeclaredOrdinaryQuotaByGroupStatic = new HashMap<>();

    /** TASK-558: the group's average declared aula+lab quota, or null = no declared sheet. */
    public static Double getAverageDeclaredOrdinaryQuota(Long groupId) {
        if (groupId == null) return null;
        return averageDeclaredOrdinaryQuotaByGroupStatic.get(groupId);
    }


    /**
     * TASK-413: pre-solve weekly template for M32 (Template deviation), keyed
     * by course id -&gt; the set of that course's template cells, each cell
     * encoded as {@link #encodeTemplateCell(DayOfWeek, int)}. Published ONCE
     * per solve setup by {@code ScheduleService.refreshSolverStatics} from the
     * same pre-solve state the CH phase's {@code WeeklyTemplatePlanner}
     * derives its template from, and NEVER republished mid-solve (the {@code
     * refreshStatics} guard in {@code loadSchedule}), so M32's reference stays
     * FIXED for the whole solve - a static map that changed halfway through
     * would corrupt the incremental score (see
     * claudedocs/design/task-413-quasi-pinning-graduato-2026-07-24.md). Empty
     * by default =&gt; M32 is inert (no course templated, e.g. a fully-pinned
     * import).
     */
    private static volatile Map<Long, Set<Integer>> weeklyTemplateCellsByCourseStatic = new HashMap<>();

    /**
     * TASK-413: per-course M32 deviation weight (scarcity-graduated - see
     * {@link #templateDeviationWeight(int)}), keyed by course id. Published
     * alongside {@link #weeklyTemplateCellsByCourseStatic} in the same
     * pre-solve pass. A course absent here (or with no template cells) is not
     * penalized by M32.
     */
    private static volatile Map<Long, Integer> weeklyTemplateDeviationWeightByCourseStatic = new HashMap<>();

    /**
     * TASK-419: ids of the lessons the staged solve PROMOTED to {@code
     * pinned=true} (stage 1 freezes every unpinned lesson already sitting on its
     * own M32 template cell, so phase 2's move space shrinks to the tail+box).
     * A promoted lesson is pinned for Timefold (native {@code @PlanningPin}
     * immobility) but is NOT an AUTHORITATIVE pin: it carries no statement from
     * the school, it is a search-space device the closing "smarcatura" step
     * removes again. This registry is the only thing that tells the two classes
     * apart - {@code pinned=true} naked, without a marker, is exactly the
     * accounting hole TASK-383 (vanished pins) came out of.
     *
     * <p>The score function reads it through {@code
     * BaseConstraintProvider.isAuthoritativePin}: every pinned-exempt constraint
     * treats a PROMOTED lesson exactly as it treated it before the promotion
     * (i.e. as unpinned), so (a) freezing the template can never make an
     * occupied day look empty to M20/M22 and push the LS to re-fill it, and (b)
     * the score does not jump when the promoted pins are released at the end of
     * the cycle. Only AUTHORITATIVE pins (golden/calendario/stage/lab-rotation)
     * keep their exemptions.
     *
     * <p>Build-locally-assign-last (same TASK-378 rationale as {@link
     * #computeLessonAvailableTimeslotCounts}), published pre-solve and never
     * mid-solve. EMPTY BY DEFAULT =&gt; {@code isAuthoritativePin} degenerates to
     * {@code pinned}, i.e. behavior byte-identical to before this registry existed.
     */
    private static volatile Set<Long> promotedLessonIdsStatic = Set.of();

    /**
     * TASK-425: per-(group, normalized-subject) golden cadence BURST CAP - the
     * school's own max weekly hours for that pair, published ONCE per solve
     * setup by {@code ScheduleService.refreshSolverStatics} from
     * {@code claudedocs/spalmone-2026-27/data/golden-cadence-reference.json}
     * (the same KPI reference the cadence Python tooling produces). Key is
     * {@code groupName + "\t" + SubjectNormalization.normalize(subject)} - the
     * SAME symmetric normalization M34 itself applies, so the two sides can
     * never drift on case-duplicate spellings ("Lingua italiana" vs "Lingua
     * Italiana"). Empty by default =&gt; M34 is inert (every lookup returns
     * {@link Integer#MAX_VALUE}, and {@code hours > MAX_VALUE} is always false),
     * the same safe-fail shape M32's empty template map already establishes.
     */
    private static volatile Map<String, Integer> cadenceBurstCapStatic = new HashMap<>();

    /**
     * TASK-425: per-(group, normalized-subject) golden cadence ACTIVE-WEEK
     * target - the school's own distinct active ISO-week count for that pair,
     * published alongside {@link #cadenceBurstCapStatic} from the same JSON.
     * {@link #getCadenceActiveWeeks} returns 0 when the pair is absent, so M35's
     * {@code target > 0 && activeWeeks < target} gate short-circuits to inert
     * for any pair the reference does not cover (the same self-gating shape M34
     * uses via {@link Integer#MAX_VALUE}).
     */
    private static volatile Map<String, Integer> cadenceActiveWeeksStatic = new HashMap<>();

    /**
     * TASK-636: per-(group, normalized-subject) golden CUMULATIVE-HOURS pace
     * curve, published alongside the M34/M35 maps from the same JSON's
     * per-pair weekly {@code series}: key {@link #cadenceKey}, value an
     * ordered map of ISO week key ({@code weekBasedYear*100 + week}, the same
     * encoding {@code TimeslotSequence.isoWeekKey} in the solver package
     * produces, which is also what the
     * reference's {@code "2026-W39"} strings parse to) to the golden's total
     * hours for the pair by the END of that week. Consumed by the
     * construction repair tail's golden-pace preference (TASK-636): a course
     * BEHIND its golden cumulative curve keeps round-robin top priority
     * (dense-first), one at or ahead of pace falls back to the plain
     * round-robin aging. Absent pair =&gt; the preference is inert for that
     * course (the same self-gating shape as the other two maps).
     */
    private static volatile Map<String, java.util.NavigableMap<Integer, Integer>> cadencePaceStatic = new java.util.HashMap<>();

    // ── TASK-413 M32 weight knobs (benchmark-tunable, NOT ground truth) ──
    // 2026-07-25 (benchmark arm "W"): each knob is read ONCE, at class-init time, from a system
    // property, falling back to the original fixed default when the property is unset/blank/
    // unparsable. Being `static final`, they are resolved when this class first loads (JVM class
    // loader), so a property MUST be supplied before that (e.g. -D on the JVM command line via
    // MAVEN_OPTS/surefire argLine); calling System.setProperty from within an already-running
    // test has no effect on these fields - see SchoolScheduleM32KnobDefaultsTest's own javadoc
    // for why only the defaults are exercisable in-suite. The defaults themselves never change:
    // a caller that never sets a property gets byte-identical behavior to before this knob existed.
    /** Numerator of the scarcity-graduated M32 deviation weight: {@code clamp(K / vrpCells, MIN, MAX)}.
     *  System property {@code planner.m32.numerator} (default 100000). */
    public static final int TEMPLATE_DEVIATION_WEIGHT_NUMERATOR =
            readM32KnobProperty("planner.m32.numerator", 100_000);
    /** Floor of the M32 deviation weight - a course with thousands of legal cells is cheap to relocate.
     *  System property {@code planner.m32.min} (default 20). */
    public static final int TEMPLATE_DEVIATION_WEIGHT_MIN =
            readM32KnobProperty("planner.m32.min", 20);
    /** Ceiling of the M32 deviation weight - a maximally-constrained course (few cells) is near-immobile,
     *  but still below M20/M22's 1000/day. System property {@code planner.m32.max} (default 500). */
    public static final int TEMPLATE_DEVIATION_WEIGHT_MAX =
            readM32KnobProperty("planner.m32.max", 500);

    /** Parse an M32 knob system property, falling back to {@code defaultValue} when unset, blank,
     *  or not a valid integer (never throws - a malformed property must not crash startup). */
    private static int readM32KnobProperty(String key, int defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ── TASK-456 lab-fallback value-domain + discouragement flag (domanda 21) ──
    // Deliberately a LIVE read (System.getProperty, NOT a class-init static final like the M32
    // knobs above): Lesson.getAvailableRooms reads it at cache-build time and the M-LF constraint
    // reads it per-build at defineConstraints, and BOTH must honor an in-harness
    // System.setProperty("planner.room.lab-fallback.enabled","false") set between runs to opt out
    // (the M32 static-final pattern would freeze it at class load and ignore the harness override).
    // The weight knob planner.room.lab-fallback.weight is parsed inline by the M-LF constraint
    // (readCadenceKnobProperty in SchoolScheduleConstraintProvider), same live-read discipline.
    // Default ON since maintainer ratified TASK-456 (2026-07-29): the lab fallback is the production
    // behavior, not an opt-in. Set -Dplanner.room.lab-fallback.enabled=false to opt back out.
    /** True when the lab-fallback value domain is open (non-lab lessons may sit in tagged rooms).
     *  System property {@code planner.room.lab-fallback.enabled}, default TRUE (ratified TASK-456);
     *  any value other than absent/"true" opts out. */
    public static boolean isLabFallbackEnabled() {
        String v = System.getProperty("planner.room.lab-fallback.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /** True when the M-TC lab tag-capacity constraint (TASK-455, scarcity-aware spec
     *  2026-07-30) is active. System property {@code planner.tag-capacity.enabled},
     *  default FALSE: an opt-IN prototype, so production is unchanged until a checkpoint
     *  run proves the lever helps without degrading the three guard KPIs. Read LIVE per
     *  build (same discipline as {@link #isLabFallbackEnabled}) so the harness toggles it
     *  with one System.setProperty; the leading {@code enabled &&} in the constraint
     *  short-circuits to 0 matches when off. See task455-tag-capacity-30e-2026-07-30.md. */
    public static boolean isTagCapacityEnabled() {
        return Boolean.parseBoolean(System.getProperty("planner.tag-capacity.enabled"));
    }

    /** True when the TASK-558 M22 quota-aware min-days amendment is active.
     *  System property {@code planner.constraints.m22.quota-aware.enabled},
     *  DEFAULT TRUE since the ratification (maintainer, 2026-08-27, handoff mandato mattina
     *  27/08): standard opt-OUT form, null/absent -> on, only the explicit literal
     *  {@code false} restores the legacy thin-packing metric. Evidence
     *  (task558-results/gate-emendamento-m22-2026-08-27.md): the golden's
     *  unpinned-equivalent excess days drop 463 -> 3 under the amended metric, the
     *  run's 410 -> 0 (the legacy min-days was a packing fiction on this dataset;
     *  the amendment changes the METRE, not the timetable). Read PER CALL
     *  deliberately, not captured at solve setup: ConstraintVerifier reuses streams
     *  built by earlier sessions in the same JVM and a captured value would freeze
     *  whatever the first session saw (red test under the harness, lesson
     *  m22MinDays); the refreshSolverStatics capture pattern refreshes only on the
     *  service path, so the per-call read stays the honest form for a
     *  constraint-stream flag. */
    public static boolean isM22QuotaAwareEnabled() {
        String v = System.getProperty("planner.constraints.m22.quota-aware.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /** True when the TASK-497 M20 pool-cap (forma (b)) is active. System
     *  property {@code planner.constraints.m20-cap-pool.enabled}, default TRUE since
     *  the ratification (maintainer, 2026-08-15): null/absent -> on, only the explicit
     *  literal {@code false} opts out, same opt-OUT form as every other ratified flag
     *  (multiStart, M-TC per-retry, multi-threaded LS). When on, the day-fill family's
     *  target for a capacity-exceeds-curriculum group is the published per-day
     *  pool-capped target (sum over the year = the group's curriculum pool) instead of
     *  the TASK-461 ratio-scaled quota. Ratification evidence (A/B run1+run2 paired,
     *  4/4 arms hard 0, KPI flat, placement = pool in every run): the ~50k medium cost
     *  is the metric counting REAL spreading deficits the ratio hid behind
     *  unplaceable targets (G08 40h floor eliminated); the golden's own practice is
     *  the pool semantics ({1h: 90 days} on G04). referente confirmation of the short-day
     *  semantics pending (domande-brevi rev 14/08, courtesy not blocker). */
    public static boolean isM20CapPoolEnabled() {
        String v = System.getProperty("planner.constraints.m20-cap-pool.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * TASK-497: the {@link #isM20CapPoolEnabled()} flag, captured ONCE per solve setup by
     * {@code ScheduleService.refreshSolverStatics} (which owns every other pre-solve
     * static this prototype publishes alongside). capSupply sits on the dailyQuota hot
     * path (M20/M20.2/M33 and the repair pricers call it per score evaluation), where a
     * per-call {@code System.getProperty} is a synchronized Hashtable lookup the codebase's
     * own flag discipline forbids (see BaseConstraintProvider's TASK-464 capture-once
     * note); a volatile read replaces it. An A/B toggles the property between solves and
     * each solve re-runs refreshSolverStatics, so the capture stays honest.
     */
    private static volatile boolean m20CapPoolArmed = false;

    /** TASK-497: capture {@link #isM20CapPoolEnabled()} once, at solve setup. */
    public static void captureM20CapPoolFlag() {
        m20CapPoolArmed = isM20CapPoolEnabled();
    }

    /** TASK-497: the captured {@link #isM20CapPoolEnabled()} (hot-path read, see above). */
    public static boolean isM20CapPoolArmed() {
        return m20CapPoolArmed;
    }

    /**
     * TASK-497: clear the whole M20-cap family's statics - the pool-cap target map, the
     * captured armed flag, and the TASK-461 ratio map (the FALLBACK capSupply reads when no
     * pool target is published; leaving it across a clear lets the next harness's groups
     * inherit the previous harness's ratios, a gap the TASK-497 review surfaced). One
     * method so {@link #clearStaticState} and the offline-harness partial reset
     * (SnapshotFixture.resetStatics) cannot drift apart.
     */
    public static void clearM20CapPoolStatics() {
        m20PoolCapTargetByGroupDateStatic = null;
        m20CapPoolArmed = false;
        supplyCapRatioByGroupStatic = null;
    }

    /** True when the TASK-464 co-presenza-ospite lever is active. System property
     *  {@code planner.co-presenza.enabled}, default TRUE since the 2026-08-01 ratification
     *  (combined with TASK-463). Evidence: the full-solve A/B n=2 (task464-results) converted
     *  the 94 acconciatura room-null into legal guest co-presenza (hard -112 -> -17/-18,
     *  lowest ever; 0 over-depth cap; no regression on full-day/ricorrenza/medium), and the
     *  combined run3 closed hard -6 (the 3 TASK-435 residual, 0 room-null/H20/holes). Opt-out
     *  semantics ({@code null} or anything but "false" enables), same form as {@link
     *  #isLabFallbackEnabled()}: set {@code -Dplanner.co-presenza.enabled=false} to opt back
     *  out. Read LIVE per build so the property reaches the app JVM on the {@code java -jar}
     *  command line (MAVEN_OPTS lesson, CLAUDE.md); the leading {@code enabled &&} in both the
     *  H2 exemption filter and the H-COPRESENCE cap short-circuits to inert (0 change) when
     *  opted out. */
    public static boolean isCoPresenceEnabled() {
        String v = System.getProperty("planner.co-presenza.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /** True when the TASK-468 theory-adjacent prototype (tab "Vincoli DA ANALIZZARE":
     *  "non ci devono essere 2 ore vicine della stessa materia teorica") is active.
     *  System property {@code planner.constraints.theory-adjacent.enabled}, default
     *  FALSE: an opt-IN prototype (like {@link #isTagCapacityEnabled()}), so production
     *  is byte-identical until an A/B gate proves the medium lever helps without
     *  degrading the guard KPIs (hard, full-day, ricorrenza, M15/M20/M21). Read LIVE
     *  per build so the harness toggles it with one System.setProperty; the leading
     *  {@code enabled &&} in the constraint short-circuits to 0 matches when off.
     *  Weight knob {@code planner.constraints.theory-adjacent.weight} (default 1) is
     *  parsed inline by the M-TR constraint. See task468-results/AC2-design-2026-07-31.md. */
    public static boolean isTheoryAdjacentEnabled() {
        return Boolean.parseBoolean(System.getProperty("planner.constraints.theory-adjacent.enabled"));
    }

    /** True when M-BF (lab-teacher day contiguity) is active. RATIFICATO
     *  DEFAULT ON (maintainer, 18/09) sul pacchetto completo: banco appaiato -60
     *  giorni interleaved netti, A/B ciclo completo 4 criteri/5 PASS con
     *  ricorrenza intatta (69,7 vs 69,8) e sanity senza rosse nuove, re-cert
     *  delle cure identica al numero, golden 0 match (self-gating vivo).
     *  Standard opt-OUT form: only the literal "false" on {@code
     *  planner.constraints.mbf.enabled} disarms (null/absent/garbage -> ON).
     *  Read LIVE per build. Weight knob {@code planner.constraints.mbf.weight}
     *  (default 20, per gap-minute: M15 parity, 1200 per gap-hour) is parsed
     *  inline by the constraint. */
    public static boolean isMbfEnabled() {
        return !"false".equals(System.getProperty("planner.constraints.mbf.enabled"));
    }

    /** Default of {@code planner.constraints.mbf.weight} (per gap-minute, M15 parity:
     *  1200 per gap-hour). Shared by the constraint (which charges it) and the startup
     *  banner (which echoes it), so a re-tune edits exactly one place (the
     *  PAIR_DEVIATION_DEFAULT_WEIGHT pattern ten lines below). */
    public static final int MBF_DEFAULT_WEIGHT = 20;

    /** Default of {@code planner.constraints.pair-deviation.weight} since the 2026-09-02
     *  ratification. Shared by the constraint (which charges it) and the startup banner
     *  (which echoes it), so a re-tune edits exactly one place (M32 single-constant pattern). */
    public static final int PAIR_DEVIATION_DEFAULT_WEIGHT = 1;

    /** True when the M38 pair-cell deviation constraint (TASK-576 F7-C) is active. System
     *  property {@code planner.constraints.pair-deviation.enabled}, DEFAULT TRUE since the
     *  2026-09-02 ratification at weight 1 (maintainer): standard opt-OUT form, only the literal
     *  "false" disarms. Doubly self-gated: without the pair-cells builder the published set
     *  is empty and the constraint matches nothing whatever this flag says. Tier, weight
     *  rationale and sweep evidence live in {@code pairCellDeviation} (TASK-576 register). */
    public static boolean isPairDeviationEnabled() {
        String v = System.getProperty("planner.constraints.pair-deviation.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /** True when the TASK-469 curriculum-post-stage lever is active. System property
     *  {@code planner.constraints.curriculum-poststage.enabled}, default TRUE since the
     *  2026-08-01 ratification (maintainer): an opt-OUT (set the property to "false"), same form
     *  as {@link #isCoPresenceEnabled()} / {@link #isLabDayConsolidationEnabled()}. Evidence:
     *  A/B ab-cps-on-run1 hard 0, curriculum post-stage converges toward the 6h target
     *  (G10 12->9, G08 7->6, G11 24->21 with the asymmetric caution), zero cost on the
     *  guard KPIs (full-day +0.4pp, recurrence flat, holes 0), medium neutral. Read LIVE per
     *  build so the harness toggles it with one System.setProperty; the leading {@code enabled &&}
     *  in the constraint short-circuits to 0 matches when opted out. Weight knobs
     *  {@code planner.constraints.curriculum-poststage.full-weight} (default 100) and
     *  {@code ...weak-weight} (default 1) are parsed inline by the M-CPS constraint.
     *  See TASK-469 planSet + VERDICT ab-cps-on-run1. */
    public static boolean isCurriculumPostStageEnabled() {
        String v = System.getProperty("planner.constraints.curriculum-poststage.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    // M-CPS (TASK-469) per-group stage-end: the last school date with
    // DailyActivityQuota.stageHours > 0, published PRE-solve from INPUT (the calendario sheets
    // calendario_fc.csv / calendario_class_days.csv modality S), NOT from StagePattern (the
    // seed creates StagePatterns for only 3 of 14 groups - see DataGenerator.generateStagePatterns)
    // and NOT from the golden. This is the uniform input source that covers all perimeter groups;
    // see curriculumPostStage's javadoc for the full rationale and the G11 off-by-one note.
    private static Map<Long, LocalDate> stageEndByGroup = Map.of();

    /** Publish the per-group last-stage-day map (INPUT-derived). Called by
     *  ScheduleService.refreshSolverStatics once per solve setup. */
    public static void setStageEndByGroupStatic(Map<Long, LocalDate> map) {
        stageEndByGroup = map != null ? map : Map.of();
    }

    /** M-CPS: the last stage day for a group (max DailyActivityQuota.stageHours > 0 date), or
     *  null if the group has no stage in the input. */
    public static LocalDate lastStageDateFor(Long groupId) {
        return groupId == null ? null : stageEndByGroup.get(groupId);
    }

    /** True when the TASK-463 lab-day-consolidation lever (cross-group teacher-day
     *  coordinator) is active. System property {@code planner.construction.lab-day-consolidation
     *  .enabled}, default TRUE since the 2026-08-01 ratification (combined with TASK-464):
     *  the gate A/B proved it closes the G13-Friday lab-day H17 gaps the staged cycle leaves
     *  behind, without degrading the guard KPIs (the combined run3 with both ON closed hard -6
     *  = the 3 TASK-435 residual). Opt-out semantics ({@code null} or anything but "false"
     *  enables), same form as {@link #isLabFallbackEnabled()}: set {@code
     *  -Dplanner.construction.lab-day-consolidation.enabled=false} to opt back out. Read LIVE
     *  at each {@code repair} invocation (unlike the constraint flags, which are read at
     *  {@code defineConstraints} per build) so the harness toggles it with one System.setProperty;
     *  {@code repair} returns a zero Result (no mutation) when opted out. Same {@code
     *  planner.construction.*} namespace as the staged-cycle repair levers (AnchorRepair
     *  precedent). See task463-lab-day-consolidation-design-2026-07-31.md. */
    public static boolean isLabDayConsolidationEnabled() {
        String v = System.getProperty("planner.construction.lab-day-consolidation.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /** True when the ExactFinisher deterministic residual-completion pass (TASK-478) is active.
     *  System property {@code planner.exact-finisher.enabled}, default TRUE (opt-OUT): an explicit
     *  {@code -Dplanner.exact-finisher.enabled=false} is the only way to disable it. Ratified
     *  DEFAULT ON 2026-08-02 (maintainer) after the evidence chain (offline discriminator uncapped on
     *  the run4 worst case + probe bad-seed placed 18/18 boxed + A/B final 2/2 hard-0 with EF
     *  inert on good seed): the pass converts the input path's hard-0 from probabilistic to
     *  deterministic, unblocking the m-13 flip. The {@code -6hard} H17 residual (G11 mercoledi)
     *  is orthogonal (DRAFT-006) and does not block the pass. Read LIVE at each repair invocation
     *  (repair-pass discipline, like {@link #isLabDayConsolidationEnabled()}); repair returns a
     *  zero Result when off. */
    public static boolean isExactFinisherEnabled() {
        String v = System.getProperty("planner.exact-finisher.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /** True when the CompositionHoleCloser deterministic classroom-day H17 cascade (TASK-481 Opzione A)
     *  is active. System property {@code planner.composition-hole-closer.enabled}, RATIFIED DEFAULT ON
     *  (maintainer, 16/09, endgame-bundle re-A/B 258ac6cbe: A = B = [-1/0] full no-op with the pass armed,
     *  banner 45/45 enabled=true proving the multi-start cure): standard opt-OUT form, null/absent ->
     *  ON, only the literal "false" disarms. The pass is a verified-correct scaffold that closes
     *  classroom-day H17 gaps whose blocker is edge-removable; on the known G11 @10 bad seed it is
     *  correctly INERT (the offline depth-3 gate refused the shell game), which under the bounded-no-op
     *  directive makes it insurance at censused cost. Read LIVE at each repair invocation (repair-pass
     *  discipline, like {@link #isExactFinisherEnabled()}); repair returns a zero Result when off. */
    public static boolean isCompositionHoleCloserEnabled() {
        String v = System.getProperty("planner.composition-hole-closer.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * Get the static list of all timeslots for ValueRangeProvider access.
     * Used by planning entities' ValueRangeProviders (the idealEndDate horizon filter was removed by TASK-378; M12 carries that pressure at medium tier).
     */
    public static List<Timeslot> getAllTimeslots() {
        return allTimeslotsStatic;
    }

    /**
     * Set the static list of all timeslots. Called by ScheduleService.loadSchedule().
     */
    public static void setAllTimeslotsStatic(List<Timeslot> timeslots) {
        allTimeslotsStatic = timeslots != null ? timeslots : new ArrayList<>();
    }

    /**
     * Get the static list of all rooms for ValueRangeProvider access.
     * Used by Lesson to filter rooms based on required room tags.
     */
    public static List<Room> getAllRooms() {
        return allRoomsStatic;
    }

    /**
     * Set the static list of all rooms. Called by ScheduleService.loadSchedule().
     */
    public static void setAllRoomsStatic(List<Room> rooms) {
        allRoomsStatic = rooms != null ? rooms : new ArrayList<>();
    }

    /**
     * Set the per-tag assignable-room count for the M-TC lab tag-capacity constraint
     * (TASK-455). Called by ScheduleService.refreshSolverStatics(); a {@code null} arg
     * resets to empty (every tag then reads capacity 0, so the constraint only fires
     * when real rooms back a tag).
     */
    public static void setRoomCapacityByTagStatic(Map<String, Integer> capByTag) {
        roomCapacityByTagStatic = capByTag != null ? capByTag : new HashMap<>();
    }

    /** Assignable-room count for a lab tag (0 if the tag is unknown to the room set). */
    public static int roomCapacityForTag(String tag) {
        return roomCapacityByTagStatic.getOrDefault(tag, 0);
    }

    // ─── TASK-464 co-presenza depth (golden practice) ──────────────────────────

    /**
     * Publish the golden co-presenza depth map (TASK-464). Called by
     * {@code ScheduleService.refreshSolverStatics()} after the
     * {@link ConstrainedTeacherAllocator#loadGoldenCoPresenceDepth} single-pass read of
     * the classpath golden; a {@code null} arg resets to empty (every cell then reads
     * depth 0, so only the empty map is published until the loader runs).
     */
    public static void setGoldenCoPresenceDepthStatic(Map<String, Integer> depthByCell) {
        goldenCoPresenceDepthStatic = depthByCell != null ? depthByCell : new HashMap<>();
    }

    /** The stable co-presenza cell key: {@code tag|weekday|slotStart} (the weekly practice
     *  cell, independent of the specific date - the golden depth is a weekly pattern). */
    public static String coPresenceCell(String tag, DayOfWeek weekday, LocalTime slotStart) {
        return tag + "|" + weekday + "|" + slotStart;
    }

    /**
     * Golden co-presenza depth of a {@code (tag, weekday, slotStart)} cell: how many
     * distinct groups the school itself co-locates there. 0 when the cell is unknown to
     * the golden (not a practiced co-presenza cell). The H-COPRESENCE cap treats this as
     * the ceiling; the H2 pairwise exemption treats {@code depth >= 2} as "co-presenza is
     * practiced here, guest pairs are legal".
     */
    public static int goldenCoPresenceDepth(String tag, DayOfWeek weekday, LocalTime slotStart) {
        if (tag == null || weekday == null || slotStart == null) {
            return 0;
        }
        return goldenCoPresenceDepthStatic.getOrDefault(coPresenceCell(tag, weekday, slotStart), 0);
    }

    /** True iff the golden practices co-presenza on this cell (depth {@code >= 2}). The
     *  AC#4 guard: only golden-practiced cells unlock the H2 guest exemption, never a
     *  blanket per-tag allowance. */
    public static boolean isCoPresenceCell(String tag, DayOfWeek weekday, LocalTime slotStart) {
        return goldenCoPresenceDepth(tag, weekday, slotStart) >= 2;
    }

    /**
     * Get the static list of all lessons for ValueRangeProvider access.
     * Used by Lesson to filter timeslots that have pinned lessons for the same student group.
     */
    public static List<Lesson> getAllLessonsStatic() {
        return allLessonsStatic;
    }

    /**
     * Set the static list of all lessons. Called by ScheduleService.loadSchedule().
     */
    public static void setAllLessonsStatic(List<Lesson> lessons) {
        allLessonsStatic = lessons != null ? lessons : new ArrayList<>();
    }

    /**
     * Get the map of student group ID -> occupied timeslots by pinned lessons.
     * Used by Lesson.getAvailableTimeslots() for O(1) lookup.
     */
    public static Map<Long, Set<Timeslot>> getPinnedLessonTimeslotsByGroupStatic() {
        return pinnedLessonTimeslotsByGroupStatic;
    }

    /**
     * Build BOTH pin-derived lookups from the SAME lesson list: the occupied-timeslot
     * map above (every pin, promoted included - for the free tail they are occupied
     * cells) and {@link #authoritativePinnedHoursByGroupDateStatic} (TASK-424, the
     * day-fill family's netting basis, authoritative real-teacher pins only). One
     * input, one pass, so the two can never be published from different populations.
     * Called by ScheduleService.loadSchedule()/refreshSolverStatics()/refreshPinStatics().
     */
    public static void setPinnedLessonTimeslotsByGroupStatic(List<Lesson> lessons) {
        Map<Long, Set<Timeslot>> map = new HashMap<>();
        Map<Long, Map<LocalDate, Integer>> pinnedHours = new HashMap<>();
        if (lessons != null) {
            for (Lesson lesson : lessons) {
                if (lesson.pinned && lesson.timeslot != null && lesson.course != null && lesson.course.studentGroup != null) {
                    Long groupId = lesson.course.studentGroup.id;
                    map.computeIfAbsent(groupId, k -> new HashSet<>()).add(lesson.timeslot);
                    // TASK-424: the netted hours are the AUTHORITATIVE real-teacher ones only -
                    // see authoritativePinnedHoursByGroupDateStatic's javadoc for both exclusions.
                    if (BaseConstraintProvider.isAuthoritativePin(lesson)
                            && lesson.getTeacher() != null && !lesson.getTeacher().pseudo
                            && lesson.timeslot.getDate() != null) {
                        pinnedHours.computeIfAbsent(groupId, k -> new HashMap<>())
                                .merge(lesson.timeslot.getDate(), 1, Integer::sum);
                    }
                }
            }
        }
        pinnedLessonTimeslotsByGroupStatic = map;
        authoritativePinnedHoursByGroupDateStatic = pinnedHours;
    }

    /** Test-only (TASK-577 v9): publish the authoritative pinned-hours map directly,
     * the same shape {@code setPinnedLessonTimeslotsByGroupStatic} derives from the
     * lesson population. Unit fixtures have no pins to derive from. */
    public static void setAuthoritativePinnedHoursForTest(Map<Long, Map<LocalDate, Integer>> hours) {
        authoritativePinnedHoursByGroupDateStatic = hours != null ? hours : Map.of();
    }

    /**
     * TASK-424: how many AUTHORITATIVE, real-teacher pinned hours the school itself
     * already booked on {@code (groupId, date)} - the amount the day-fill family
     * ({@code SchoolScheduleConstraintProvider#effectiveQuota}) nets out of that day's
     * expected-hours target. 0 when unknown (no map published, no pin that day).
     */
    public static int getAuthoritativePinnedHours(Long groupId, LocalDate date) {
        if (groupId == null || date == null) {
            return 0;
        }
        Map<Long, Map<LocalDate, Integer>> byGroup = authoritativePinnedHoursByGroupDateStatic;
        if (byGroup == null) {
            return 0;
        }
        Map<LocalDate, Integer> byDate = byGroup.get(groupId);
        if (byDate == null) {
            return 0;
        }
        Integer hours = byDate.get(date);
        return hours == null ? 0 : hours;
    }

    /** TASK-461: publish the per-group M20 supply-cap ratio ({@code null} clears it). */
    public static void setSupplyCapRatioByGroupStatic(Map<Long, Double> ratio) {
        supplyCapRatioByGroupStatic = ratio;
    }

    /**
     * TASK-497: publish the per-(group, date) M20 pool-capped targets ({@code null} clears
     * them). The FORMA (b) complement of the TASK-461 ratio: the exact per-day target so
     * that each group's yearly target SUM equals its curriculum pool. Published once per
     * solve setup by ScheduleService.refreshSolverStatics, read by {@link #getM20PoolCapTarget}.
     */
    private static volatile Map<Long, Map<LocalDate, Integer>> m20PoolCapTargetByGroupDateStatic = null;

    /** TASK-497: see {@link #m20PoolCapTargetByGroupDateStatic}. */
    public static void setM20PoolCapTargetsStatic(Map<Long, Map<LocalDate, Integer>> targets) {
        m20PoolCapTargetByGroupDateStatic = targets;
    }

    /**
     * TASK-497 forma (b): the pool-capped M20 target for one (group, date), or {@code -1}
     * when no capped target is published for that cell (the caller falls back to the
     * TASK-461 ratio path). Semantics: a group whose calendar capacity exceeds its
     * curriculum (G04 835 vs 750) must not be asked to fill hours that do not exist;
     * the school's own practice (the golden) treats the obligation as the target and
     * spreads the shortfall -1h at a time ({1h: 90 days} for G04), never in one tail.
     */
    public static int getM20PoolCapTarget(Long groupId, LocalDate date) {
        Map<Long, Map<LocalDate, Integer>> byGroup = m20PoolCapTargetByGroupDateStatic;
        if (byGroup == null || groupId == null || date == null) {
            return -1;
        }
        Map<LocalDate, Integer> byDate = byGroup.get(groupId);
        if (byDate == null) {
            return -1;
        }
        Integer t = byDate.get(date);
        return t == null ? -1 : t;
    }

    /**
     * TASK-671: the ONE pool-cap-aware quota accessor every repair/finisher site
     * must go through when comparing actual hours against the group's declared
     * target for a date. For pool-cap groups (1/G08, TASK-497) the ratified
     * target is the per-date pool cap, not the sheet quota the construction
     * stopped following on TASK-577: reading the sheet directly for those
     * groups evaluates donors and landings against an obligation nobody chases
     * anymore, structurally killing the repair arms (TASK-656 diagnosed this
     * for the SOURCE-GATE; TASK-671 extends it to all remaining sites).
     * Returns min(sheet, poolTarget) when capped, sheet otherwise.
     */
    public static int poolCappedQuotaForDate(Long groupId, LocalDate date, int sheetQuota) {
        if (isM20CapPoolEnabled()) {
            int poolTarget = getM20PoolCapTarget(groupId, date);
            if (poolTarget >= 0) {
                return Math.min(sheetQuota, poolTarget);
            }
        }
        return sheetQuota;
    }

    /**
     * TASK-461: the factor ({@code <=1}) M20's daily target is scaled by for this group, so
     * the yearly target never exceeds the group's curriculum hours. 1.0 (no cap) when the
     * map is unset or the group is absent.
     */
    public static double getSupplyCapRatio(Long groupId) {
        Map<Long, Double> map = supplyCapRatioByGroupStatic;
        if (map == null || groupId == null) {
            return 1.0d;
        }
        Double r = map.get(groupId);
        return r == null ? 1.0d : r;
    }

    /**
     * Get the VRP-buffered ideal end date for a student group.
     */
    public static LocalDate getIdealEndDateVrpBuffered(Long studentGroupId) {
        return idealEndDateVrpBufferedStatic.get(studentGroupId);
    }

    /**
     * Set the VRP-buffered ideal end dates map. Called by ScheduleService.loadSchedule().
     */
    public static void setIdealEndDateVrpBufferedStatic(Map<Long, LocalDate> idealEndDates) {
        idealEndDateVrpBufferedStatic = idealEndDates != null ? idealEndDates : new HashMap<>();
    }

    /**
     * Get the static map of teacher availabilities.
     */
    public static Map<Long, List<TeacherAvailability>> getTeacherAvailabilitiesStatic() {
        return teacherAvailabilitiesStatic;
    }

    /**
     * Set the static teacher availabilities map. Called by ScheduleService.loadSchedule().
     * Thread-safe: synchronized to prevent ConcurrentModificationException when multiple
     * threads call loadSchedule() concurrently (e.g., when multiple UI components fetch
     * schedule data simultaneously on page load).
     */
    public static synchronized void setTeacherAvailabilitiesStatic(List<TeacherAvailability> availabilities) {
        Map<Long, List<TeacherAvailability>> map = new HashMap<>();
        if (availabilities != null) {
            for (TeacherAvailability ta : availabilities) {
                if (ta.teacher != null && ta.teacher.id != null) {
                    map.computeIfAbsent(ta.teacher.id, k -> new ArrayList<>()).add(ta);
                }
            }
        }
        teacherAvailabilitiesStatic = map;
    }

    /**
     * Get the static set of school holiday dates.
     */
    public static Set<LocalDate> getSchoolHolidaysStatic() {
        return schoolHolidaysStatic;
    }

    /**
     * Set the static school holidays set. Called by ScheduleService.loadSchedule().
     * Thread-safe: synchronized to prevent race conditions.
     */
    public static synchronized void setSchoolHolidaysStatic(List<SchoolHoliday> holidays) {
        Set<LocalDate> set = new HashSet<>();
        if (holidays != null) {
            for (SchoolHoliday h : holidays) {
                if (h.date != null) {
                    set.add(h.date);
                }
            }
        }
        schoolHolidaysStatic = set;
    }

    /**
     * Get the static map of stage blackout dates per student group.
     */
    public static Map<Long, Set<LocalDate>> getStageBlackoutDatesStatic() {
        return stageBlackoutDatesStatic;
    }

    /**
     * Build the stage blackout dates map from StagePatterns.
     * Merges multiple StagePatterns per group (a group can have multiple stage periods).
     * Must be called AFTER setSchoolHolidaysStatic() since expandDates() excludes holidays.
     * Thread-safe: synchronized to prevent ConcurrentModificationException.
     */
    public static synchronized void setStageBlackoutDatesStatic(List<StagePattern> stagePatterns, Set<LocalDate> holidays) {
        Map<Long, Set<LocalDate>> map = new HashMap<>();
        if (stagePatterns != null) {
            for (StagePattern sp : stagePatterns) {
                if (sp.studentGroup != null && sp.studentGroup.id != null) {
                    Set<LocalDate> dates = sp.expandDates(holidays != null ? holidays : Set.of());
                    map.merge(sp.studentGroup.id, dates, (existing, newDates) -> {
                        Set<LocalDate> merged = new HashSet<>(existing);
                        merged.addAll(newDates);
                        return merged;
                    });
                }
            }
        }
        stageBlackoutDatesStatic = map;
    }

    /**
     * Get the static TimeslotSequence for block validity checks.
     */
    public static TimeslotSequence getTimeslotSequenceStatic() {
        return timeslotSequenceStatic;
    }

    /**
     * Set the static TimeslotSequence. Called by ScheduleService.loadSchedule().
     */
    public static void setTimeslotSequenceStatic(TimeslotSequence seq) {
        timeslotSequenceStatic = seq;
    }

    /**
     * Check if a teacher is a lab assistant.
     */
    public static boolean isLabAssistant(Long teacherId) {
        return teacherId != null && labAssistantTeacherIdsStatic.contains(teacherId);
    }

    /**
     * Get the room tag a lab assistant is assigned to.
     * Returns null if the teacher is not a lab assistant.
     */
    public static String getLabAssistantRoomTag(Long teacherId) {
        return teacherId != null ? labAssistantRoomTagByTeacherIdStatic.get(teacherId) : null;
    }

    /**
     * Set the static lab assistant data. Called by ScheduleService.loadSchedule().
     */
    /**
     * Set the static lab assistant data. Called by ScheduleService.loadSchedule().
     * Thread-safe: synchronized to prevent race conditions.
     */
    public static synchronized void setLabAssistantsStatic(List<LabAssistant> labAssistants) {
        Set<Long> teacherIds = new HashSet<>();
        Map<Long, String> roomTagByTeacherId = new HashMap<>();
        if (labAssistants != null) {
            for (LabAssistant la : labAssistants) {
                if (la.teacher != null && la.teacher.id != null && la.roomTag != null) {
                    teacherIds.add(la.teacher.id);
                    roomTagByTeacherId.put(la.teacher.id, la.roomTag);
                }
            }
        }
        labAssistantTeacherIdsStatic = teacherIds;
        labAssistantRoomTagByTeacherIdStatic = roomTagByTeacherId;
    }

    /**
     * Set the static per-group GroupCalendarRule snapshot (TASK-342). Called by
     * ScheduleService.loadSchedule().
     */
    public static synchronized void setGroupCalendarRulesStatic(List<GroupCalendarRule> rules) {
        Map<Long, List<GroupCalendarRule>> map = new HashMap<>();
        if (rules != null) {
            for (GroupCalendarRule rule : rules) {
                if (rule.studentGroup != null && rule.studentGroup.id != null) {
                    map.computeIfAbsent(rule.studentGroup.id, k -> new ArrayList<>()).add(rule);
                }
            }
        }
        groupCalendarRulesByGroupStatic = map;
    }

    /**
     * True if the timeslot is open for the group's calendar. No rule at all for
     * that group means every slot is open (backward compatible with the
     * pre-TASK-342 model).
     */
    public static boolean isGroupCalendarOpen(Long groupId, Timeslot timeslot) {
        if (groupId == null || timeslot == null) return true;
        Map<Long, List<GroupCalendarRule>> rulesByGroup = groupCalendarRulesByGroupStatic;
        if (rulesByGroup == null) return true;
        return GroupCalendarService.isOpen(rulesByGroup.get(groupId), timeslot);
    }

    /**
     * Resolves the effective [minSlot, maxSlot] range for a group's calendar on
     * the given weekday/date, or null if any argument is missing or no static
     * snapshot has been set (both getGroupCalendarOpenSlotCount() and
     * getGroupCalendarWindow() share this lookup, applying their own
     * "backward compatible" default when it returns null).
     */
    private static int[] resolveGroupCalendarRange(Long groupId, DayOfWeek dayOfWeek, LocalDate date) {
        if (groupId == null || dayOfWeek == null || date == null) return null;
        Map<Long, List<GroupCalendarRule>> rulesByGroup = groupCalendarRulesByGroupStatic;
        if (rulesByGroup == null) return null;
        return GroupCalendarService.effectiveRange(rulesByGroup.get(groupId), dayOfWeek, date);
    }

    /**
     * Number of calendar-open slots for the group on the given weekday/date, or
     * {@link GroupCalendarService#TOTAL_SLOTS} if no rule applies (fully open,
     * backward compatible). A closed-day sentinel rule (maxSlot &lt; minSlot)
     * yields 0. Used by M20 (horizontallyCompact) to cap the daily quota at
     * what the group's calendar can actually offer that day — without this,
     * groups with a null maxHoursPerDay and a calendar shorter than the
     * DEFAULT_DAILY_QUOTA (e.g. a 5-slot Friday) would be penalized every
     * single day they're used, since they can never reach the fallback quota.
     */
    public static int getGroupCalendarOpenSlotCount(Long groupId, DayOfWeek dayOfWeek, LocalDate date) {
        int[] range = resolveGroupCalendarRange(groupId, dayOfWeek, date);
        return range == null ? GroupCalendarService.TOTAL_SLOTS : GroupCalendarService.openSlotCountFromRange(range);
    }

    /**
     * Combined {@code {openSlotCount, minOpenSlot, maxOpenSlot}} lookup for a
     * caller that needs multiple values for the same (group, weekday, date) -
     * e.g. M18 (dayShouldStartFromFirstOpenSlot), which gates on the
     * open-slot count and then anchors on the min open slot ("start of day" -
     * the group's actual first calendar-open slot, not a hardcoded 0); or H22
     * (professionalizzantiAtDayEdges, TASK-370), which anchors on BOTH ends of
     * the day. Resolves the group's calendar range once instead of separate
     * calls each re-resolving it (unlike {@link #getGroupCalendarOpenSlotCount},
     * which still exists standalone for the single-value caller, M20's {@code
     * dailyQuota}). Same defaults as {@link #getGroupCalendarOpenSlotCount}:
     * index 0 is {@link GroupCalendarService#TOTAL_SLOTS} / index 1 is 0 /
     * index 2 is {@code TOTAL_SLOTS - 1} when no rule applies. minOpenSlot and
     * maxOpenSlot are both the closed-day sentinel -1 (not a real slot index -
     * callers must treat it as "skip / don't penalize") when the effective
     * rule has maxSlot &lt; minSlot, i.e. the day has no open slots at all.
     */
    public static int[] getGroupCalendarWindow(Long groupId, DayOfWeek dayOfWeek, LocalDate date) {
        int[] range = resolveGroupCalendarRange(groupId, dayOfWeek, date);
        if (range == null) return new int[] { GroupCalendarService.TOTAL_SLOTS, 0, GroupCalendarService.TOTAL_SLOTS - 1 };
        int openSlotCount = GroupCalendarService.openSlotCountFromRange(range);
        boolean closed = range[1] < range[0];
        int minOpenSlot = closed ? -1 : range[0];
        int maxOpenSlot = closed ? -1 : range[1];
        return new int[] { openSlotCount, minOpenSlot, maxOpenSlot };
    }

    /**
     * Raw GroupCalendarRule list for one group (all weekdays), or empty if none.
     * Exposed so Lesson.getAvailableTimeslots() can hoist the per-group lookup
     * once before filtering candidate timeslots, instead of calling
     * isGroupCalendarOpen() (which re-does this same map lookup) once per candidate.
     */
    public static List<GroupCalendarRule> getGroupCalendarRulesForGroup(Long groupId) {
        if (groupId == null) return List.of();
        Map<Long, List<GroupCalendarRule>> rulesByGroup = groupCalendarRulesByGroupStatic;
        if (rulesByGroup == null) return List.of();
        return rulesByGroup.getOrDefault(groupId, List.of());
    }

    /**
     * Set the static per-group DailyActivityQuota snapshot (TASK-381
     * "L1-bis"). Called by ScheduleService.loadSchedule()/refreshSolverStatics().
     */
    public static synchronized void setDailyActivityQuotasStatic(List<DailyActivityQuota> quotas) {
        Map<Long, Map<LocalDate, DailyActivityQuota>> FCMap = new HashMap<>();
        Map<Long, Map<LocalDate, DailyActivityQuota>> allMap = new HashMap<>();
        Set<Long> stageFactGroups = new HashSet<>();
        if (quotas != null) {
            for (DailyActivityQuota quota : quotas) {
                if (quota.studentGroup != null && quota.studentGroup.id != null && quota.date != null) {
                    allMap.computeIfAbsent(quota.studentGroup.id, k -> new HashMap<>()).put(quota.date, quota);
                    // TASK-385: H23/H24/M30/M31's "quota group" machinery stays
                    // scoped to the original FC-sourced rows only - see
                    // dailyActivityQuotasByGroupStatic's own javadoc above.
                    if (quota.FCQuota) {
                        FCMap.computeIfAbsent(quota.studentGroup.id, k -> new HashMap<>()).put(quota.date, quota);
                    }
                    // TASK-384: any row that DailyActivityQuota.isStageDate() itself would
                    // accept marks its group as stage-confined - see groupsWithStageFactsStatic's
                    // own javadoc. Delegates to the same predicate isStageDate's other callers
                    // use instead of restating "stageHours > 0" inline (anti-drift).
                    if (DailyActivityQuota.isStageDate(quota)) {
                        stageFactGroups.add(quota.studentGroup.id);
                    }
                }
            }
        }
        dailyActivityQuotasByGroupStatic = FCMap;
        expectedHoursByGroupStatic = allMap;
        groupsWithStageFactsStatic = stageFactGroups;
        // TASK-558: same input list, same date dedup - the average declared
        // ordinary quota per group (rows with aula+lab > 0 only: pure-stage and
        // closed days are not "ordinary packing" days and would drag the mean
        // toward zero, i.e. toward MORE minimum days, the opposite of the
        // sheet's own shape).
        Map<Long, Double> averages = new HashMap<>();
        for (Map.Entry<Long, Map<LocalDate, DailyActivityQuota>> byGroup : allMap.entrySet()) {
            long sum = 0;
            int ordinaryDays = 0;
            for (DailyActivityQuota quota : byGroup.getValue().values()) {
                int ordinary = quota.aulaHours + quota.labHours;
                if (ordinary > 0) {
                    sum += ordinary;
                    ordinaryDays++;
                }
            }
            if (ordinaryDays > 0) {
                averages.put(byGroup.getKey(), (double) sum / ordinaryDays);
            }
        }
        averageDeclaredOrdinaryQuotaByGroupStatic = averages;
    }

    /**
     * The group's {@link DailyActivityQuota} row for {@code date}, or null if
     * the group has no row for that date (a fully closed/zero day - see the
     * entity's own javadoc) or isn't a quota group at all.
     */
    public static DailyActivityQuota getDailyQuota(Long groupId, LocalDate date) {
        if (date == null) return null;
        return getDailyActivityQuotasForGroup(groupId).get(date);
    }

    /**
     * True if {@code groupId} has at least one {@link DailyActivityQuota} row
     * at all (i.e. is one of the sheet-covered groups - G04/G08/G13 for
     * 2026/27). Every other group is unrestricted by L1-bis: the VRP filter
     * and H23/H24 short-circuit to "no restriction" for it.
     */
    public static boolean isQuotaGroup(Long groupId) {
        return !getDailyActivityQuotasForGroup(groupId).isEmpty();
    }

    /**
     * Raw date-&gt;{@link DailyActivityQuota} map for one group, or empty if
     * none. Exposed so {@code Lesson.getAvailableTimeslots()} can hoist the
     * per-group lookup once before filtering candidate timeslots, instead of
     * calling {@link #getDailyQuota} (which re-does this same two-level map
     * lookup) once per candidate - same rationale as {@link
     * #getGroupCalendarRulesForGroup}.
     */
    public static Map<LocalDate, DailyActivityQuota> getDailyActivityQuotasForGroup(Long groupId) {
        if (groupId == null) return Map.of();
        Map<Long, Map<LocalDate, DailyActivityQuota>> byGroup = dailyActivityQuotasByGroupStatic;
        if (byGroup == null) return Map.of();
        return byGroup.getOrDefault(groupId, Map.of());
    }

    /**
     * TASK-385: the group's raw {@link DailyActivityQuota} row for {@code
     * date} from ANY row (regardless of {@link DailyActivityQuota#FCQuota}),
     * or {@code null} if absent - the unscoped sibling of {@link
     * #getDailyQuota} (which is FC-only). Exposed for a caller that needs
     * the aula/lab/stage BREAKDOWN rather than {@link #getExpectedHours}'s
     * pre-summed total - e.g. M20's own target (see {@code
     * SchoolScheduleConstraintProvider#dailyQuota}), which must exclude
     * {@code stageHours}: M20 counts only UNPINNED lessons, and Stage hours
     * are always placed as PINNED lessons (see {@code
     * CalendarioImportService#planStagePins}), so a day's stage hours can
     * never appear in M20's own count and must not inflate its target either
     * (a real, data-verified mismatch on G08/2027-04-01: aula=2, stage=4).
     */
    public static DailyActivityQuota getExpectedHoursRow(Long groupId, LocalDate date) {
        if (date == null) return null;
        return getExpectedHoursForGroup(groupId).get(date);
    }

    /**
     * TASK-385: the group's total expected hours (aula+lab+stage) for {@code
     * date} from ANY {@link DailyActivityQuota} row (regardless of {@link
     * DailyActivityQuota#FCQuota}), or -1 if no row exists for that (group,
     * date) - the universal per-date fact the weekly template/the stamp
     * target against (TASK-385), distinct from {@link #getDailyQuota}
     * (FC-only, H23/H24/M30/M31's L1-bis semantics). This is the RAW sheet
     * fact only - a caller needing the Q16-safe "never exceed what the
     * calendar can actually offer" value (e.g. G14, whose sheet gives it
     * days its Thu-Fri calendar doesn't have) must cap the result itself
     * against {@link #getGroupCalendarOpenSlotCount}; a caller needing only
     * the non-stage portion (M20, see {@link #getExpectedHoursRow}'s own
     * javadoc) must read the row directly instead of this pre-summed total.
     */
    public static int getExpectedHours(Long groupId, LocalDate date) {
        DailyActivityQuota row = getExpectedHoursRow(groupId, date);
        return row == null ? -1 : row.aulaHours + row.labHours + row.stageHours;
    }

    /**
     * TASK-384: raw date-&gt;{@link DailyActivityQuota} map for one group from
     * the universal (any-{@link DailyActivityQuota#FCQuota}) snapshot, or
     * empty if none - the unscoped sibling of {@link
     * #getDailyActivityQuotasForGroup} (FC-only). Exposed so {@code
     * Lesson.getAvailableTimeslots()}'s Stage confinement filter can hoist
     * the per-group lookup once before filtering candidate timeslots,
     * instead of calling {@link #getExpectedHoursRow} (which re-does this
     * same two-level map lookup) once per candidate - same rationale as
     * {@link #getDailyActivityQuotasForGroup}/{@link #getGroupCalendarRulesForGroup}.
     */
    public static Map<LocalDate, DailyActivityQuota> getExpectedHoursForGroup(Long groupId) {
        if (groupId == null) return Map.of();
        Map<Long, Map<LocalDate, DailyActivityQuota>> byGroup = expectedHoursByGroupStatic;
        if (byGroup == null) return Map.of();
        return byGroup.getOrDefault(groupId, Map.of());
    }

    /**
     * TASK-384: true if {@code groupId} has at least one {@link
     * DailyActivityQuota} row (any {@link DailyActivityQuota#FCQuota} value)
     * with {@code stageHours > 0} - i.e. is one of the 7 stage-eligible
     * groups (G07/G11/G10/G12/G09 via {@code
     * DataGenerator#generateExpectedDailyHours}'s class-days rows, G08/G13
     * via {@code generateDailyActivityQuotas}'s FC rows - G04 has no
     * Stage). Every other group has no stage-day fact at all, so {@link
     * Lesson#isStageConfined()} (the VRP filter and its H-tier safety net,
     * {@code BaseConstraintProvider}) is a structural no-op for it - a
     * pseudo Stage lesson can only exist for a stage-eligible group in the
     * first place ({@code CalendarioImportService#buildStageAsks}), so this
     * guard only ever matters for a group whose Stage course was seeded but
     * whose stage-day fact hasn't been persisted (or has been misconfigured)
     * yet, keeping the confinement fail-open rather than fail-closed in that
     * edge case.
     */
    public static boolean groupHasStageFacts(Long groupId) {
        if (groupId == null) return false;
        Set<Long> groups = groupsWithStageFactsStatic;
        return groups != null && groups.contains(groupId);
    }

    /**
     * TASK-408: true if {@code date} is a "no-session" date for {@code groupId} -
     * the group is sheet-covered (has at least one expected-hours fact, i.e. all
     * 14 groups in 2026/27, {@link #getExpectedHoursForGroup} non-empty) AND has
     * NO {@link DailyActivityQuota} row for this specific date. For a covered
     * group the absence of a row means the group is simply not in session that
     * day: the golden places 0 hours on every such date (26/26 phantom-day pairs -
     * the empirical basis of TASK-408's discovery), so the day must be treated as
     * CLOSED, not fall back to {@code maxHoursPerDay} (which let M20 perversely
     * reward filling stage/off-sheet days the golden always leaves empty). A
     * stage date always HAS a row ({@code stageHours > 0}) so is never no-session;
     * off-grid stage dates (closures/Saturdays) have no {@link Timeslot} and never
     * reach the VRP. A group with no fact coverage at all returns false
     * (unrestricted / fail-open, same shape as {@link #isQuotaGroup}/{@link
     * #groupHasStageFacts}). Single source of truth for both {@code
     * Lesson.getAvailableTimeslots()}'s VRP filter (which hoists the per-group map
     * for the hot loop, checking membership inline against the same map) and
     * H26's hard-tier safety net ({@code BaseConstraintProvider}).
     */
    public static boolean isNoSessionDate(Long groupId, LocalDate date) {
        if (groupId == null || date == null) return false;
        Map<LocalDate, DailyActivityQuota> byDate = getExpectedHoursForGroup(groupId);
        return !byDate.isEmpty() && !byDate.containsKey(date);
    }

    /**
     * TASK-413: publish the pre-solve weekly template + per-course scarcity
     * deviation weights for M32 (Template deviation). Build-locally-assign-last
     * (same TASK-378 rationale as {@link #computeLessonAvailableTimeslotCounts}):
     * the caller builds both maps fully, this only swaps the volatile refs, so a
     * concurrent constraint read on the solver thread can never observe a
     * half-filled map. Null args publish empty maps (M32 inert).
     */
    public static void setWeeklyTemplateStatic(Map<Long, Set<Integer>> cellsByCourse, Map<Long, Integer> weightByCourse) {
        weeklyTemplateCellsByCourseStatic = cellsByCourse != null ? cellsByCourse : new HashMap<>();
        weeklyTemplateDeviationWeightByCourseStatic = weightByCourse != null ? weightByCourse : new HashMap<>();
    }

    /**
     * TASK-413: the encoded template cells for a course (see {@link
     * #encodeTemplateCell(DayOfWeek, int)}), or {@code null} if the course is
     * not templated - M32 leaves such a course alone (no reference to deviate
     * from). Read once per lesson by the M32 constraint.
     */
    public static Set<Integer> getWeeklyTemplateCells(Long courseId) {
        if (courseId == null) return null;
        return weeklyTemplateCellsByCourseStatic.get(courseId);
    }

    /** TASK-413: M32 deviation weight for a course, 0 if untemplated (see {@link #templateDeviationWeight(int)}). */
    public static int getTemplateDeviationWeight(Long courseId) {
        if (courseId == null) return 0;
        return weeklyTemplateDeviationWeightByCourseStatic.getOrDefault(courseId, 0);
    }

    /**
     * TASK-419: does a template REFERENCE exist at all for this (course, timeslot)? True
     * only when the course is templated AND the timeslot has a canonical slot index - the
     * two cases M32 exempts (nothing to deviate from) and the staged promotion skips
     * (nothing to freeze onto).
     *
     * <p>Paired with {@link #isOnTemplateCell} so the two questions "is there a reference"
     * and "is this lesson ON it" are each asked in exactly one place; see that method's
     * javadoc for why the pair is NOT a plain negation.
     */
    public static boolean hasTemplateReference(Long courseId, Timeslot timeslot) {
        Set<Integer> cells = getWeeklyTemplateCells(courseId);
        if (cells == null || timeslot == null) return false;
        return TimeslotSequence.slotIndexOf(timeslot) >= 0;
    }

    /**
     * TASK-419: single source of truth for "does this (course, timeslot) sit ON one of the
     * course's M32 template cells" - shared by the M32 constraint ({@code
     * SchoolScheduleConstraintProvider.deviatesFromTemplate}) and the staged solve's
     * promotion rule ({@code StagedPinPolicy.isPromotable}), so the two can never drift on
     * how a cell is keyed or looked up.
     *
     * <p><b>NOT the exact negation of "deviates".</b> When there is NO reference (the
     * course is untemplated, or the timeslot has no canonical slot index) this returns
     * false - "not on a cell" - while M32 must read the same situation as "not a
     * deviation" (it exempts both cases). A caller that needs the distinction asks {@link
     * #hasTemplateReference} first; the promotion rule does not need it, since "no
     * reference" and "off cell" both mean "do not freeze this lesson".
     */
    public static boolean isOnTemplateCell(Long courseId, Timeslot timeslot) {
        Set<Integer> cells = getWeeklyTemplateCells(courseId);
        if (cells == null || cells.isEmpty() || timeslot == null || timeslot.getDate() == null) return false;
        int slotIndex = TimeslotSequence.slotIndexOf(timeslot);
        if (slotIndex < 0) return false;
        return cells.contains(encodeTemplateCell(timeslot.getDate().getDayOfWeek(), slotIndex));
    }

    /**
     * TASK-419: the LESSON form of {@link #isOnTemplateCell(Long, Timeslot)} - the same
     * question with the lesson-level null guards (no lesson, no course, no course id, no
     * timeslot all mean "not on a cell") carried here once, instead of once per caller
     * ({@code StagedPinPolicy.isPromotable} and {@code HoleRepair}'s import level split).
     */
    public static boolean isOnTemplateCell(Lesson lesson) {
        return lesson != null && lesson.course != null && lesson.course.id != null
                && isOnTemplateCell(lesson.course.id, lesson.timeslot);
    }

    /**
     * TASK-419: publish the ids of the lessons stage 1 promoted to {@code
     * pinned=true} (see {@link #promotedLessonIdsStatic}). Build-locally-assign-last:
     * the defensive copy is filled fully before the volatile ref is swapped, so a
     * concurrent constraint read can never observe a half-filled set. Null/empty
     * restores the default (no promotions =&gt; every pin is authoritative).
     */
    public static void setPromotedLessonIdsStatic(java.util.Collection<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) {
            promotedLessonIdsStatic = Set.of();
            return;
        }
        Set<Long> ids = new HashSet<>(lessonIds.size() * 2);
        for (Long id : lessonIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        promotedLessonIdsStatic = ids;
    }

    /** TASK-419: the currently promoted lesson ids (never null; empty when no staged solve is running). */
    public static Set<Long> getPromotedLessonIdsStatic() {
        return promotedLessonIdsStatic;
    }

    /**
     * TASK-419: is this lesson a PROMOTED pin (stage 1) rather than an
     * AUTHORITATIVE one? Read once per lesson by {@code
     * BaseConstraintProvider.isAuthoritativePin} on the hot scoring path, so the
     * empty-registry fast path (the only state production has today) costs one
     * volatile read plus an {@code isEmpty} check, never a hash lookup.
     */
    public static boolean isPromoted(Long lessonId) {
        Set<Long> ids = promotedLessonIdsStatic;
        return lessonId != null && !ids.isEmpty() && ids.contains(lessonId);
    }

    /**
     * TASK-425: publish the per-(group, normalized-subject) golden cadence
     * references for M34 (burst cap) and M35 (active-week target).
     * Build-locally-assign-last (same TASK-378 rationale as {@link
     * #setWeeklyTemplateStatic}): the caller passes fully-built maps, this only
     * swaps the volatile refs, so a concurrent constraint read on the solver
     * thread can never observe a half-filled map. Null args publish empty maps
     * (M34/M35 inert - the same safe default M32's empty template establishes).
     * The key convention ({@code groupName + "\t" + normalizedSubject}) is owned
     * by the publisher ({@code ScheduleService.publishCadenceReferenceStatics});
     * the constraint-side lookups use the same convention via {@link
     * #cadenceKey(String, String)}.
     */
    public static void setCadenceReferenceStatic(Map<String, Integer> burstCap,
            Map<String, Integer> activeWeeks) {
        setCadenceReferenceStatic(burstCap, activeWeeks, null);
    }

    /**
     * TASK-636: three-map publish (M34 burst cap, M35 active weeks, and the
     * cumulative golden pace curve the construction repair tail tracks).
     * Same build-locally-assign-last invariant as the two-map form: null pace
     * publishes an empty map (the preference self-gates inert).
     */
    public static void setCadenceReferenceStatic(Map<String, Integer> burstCap,
            Map<String, Integer> activeWeeks,
            Map<String, java.util.NavigableMap<Integer, Integer>> pace) {
        cadenceBurstCapStatic = burstCap != null ? burstCap : new HashMap<>();
        cadenceActiveWeeksStatic = activeWeeks != null ? activeWeeks : new HashMap<>();
        cadencePaceStatic = pace != null ? pace : new java.util.HashMap<>();
    }

    /**
     * TASK-636: the golden's cumulative hours for {@code (groupName,
     * normalizedSubject)} by the END of ISO week {@code isoWeekKey}
     * ({@code weekBasedYear*100 + week}, {@code TimeslotSequence.isoWeekKey}
     * encoding), or {@link Integer#MAX_VALUE} when the pair is absent from the
     * reference - the sentinel that makes the tail's behind-pace test
     * {@code placed < expected} always false (self-gating, same shape as the
     * M34/M35 sentinels). Weeks before the pair's first active week yield 0;
     * weeks after the last yield the pair's total (a course can never be
     * behind an exhausted curve, so the preference relaxes to plain
     * round-robin after the golden window closes). One floorEntry on the
     * published TreeMap answers exact-hit, before-first and after-last alike;
     * O(log n) per call on the construction hot path.
     */
    public static int getCadenceGoldenCumulative(String groupName, String normalizedSubject,
            int isoWeekKey) {
        if (groupName == null || normalizedSubject == null) return Integer.MAX_VALUE;
        Map<String, java.util.NavigableMap<Integer, Integer>> map = cadencePaceStatic;
        java.util.NavigableMap<Integer, Integer> curve =
                map.get(cadenceKey(groupName, normalizedSubject));
        if (curve == null || curve.isEmpty()) return Integer.MAX_VALUE;
        Map.Entry<Integer, Integer> floor = curve.floorEntry(isoWeekKey);
        return floor == null ? 0 : floor.getValue();
    }

    /**
     * TASK-425: the golden's max weekly hours for {@code (groupName, normalizedSubject)},
     * or {@link Integer#MAX_VALUE} when the pair is absent from the reference - the
     * sentinel that makes M34's {@code hours > cap} filter always false for an
     * untemplated pair (self-gating: a pair the reference doesn't cover is never a
     * burst violation), the same fail-safe shape {@link #getTemplateDeviationWeight}
     * uses (0 = no penalty) for an untemplated course.
     */
    public static int getCadenceBurstCap(String groupName, String normalizedSubject) {
        if (groupName == null || normalizedSubject == null) return Integer.MAX_VALUE;
        Map<String, Integer> map = cadenceBurstCapStatic;
        Integer cap = map.get(cadenceKey(groupName, normalizedSubject));
        return cap == null ? Integer.MAX_VALUE : cap;
    }

    /**
     * TASK-425: the golden's distinct active ISO-week count for {@code (groupName,
     * normalizedSubject)}, or 0 when the pair is absent - the sentinel that makes
     * M35's {@code target > 0 && activeWeeks < target} gate short-circuit to inert
     * for any pair the reference doesn't cover (a pair with no reference cannot be
     * in spread deficit), the same self-gating shape M34 uses via {@link
     * Integer#MAX_VALUE}.
     */
    public static int getCadenceActiveWeeks(String groupName, String normalizedSubject) {
        if (groupName == null || normalizedSubject == null) return 0;
        Map<String, Integer> map = cadenceActiveWeeksStatic;
        Integer weeks = map.get(cadenceKey(groupName, normalizedSubject));
        return weeks == null ? 0 : weeks;
    }

    /**
     * TASK-425: single source of truth for the (group, normalized-subject) map
     * key shared by the publisher ({@code ScheduleService}) and the M34/M35
     * constraint lookups - so the two sides can never drift on how a key is
     * built (same anti-drift rationale as {@link #encodeTemplateCell} for M32).
     */
    public static String cadenceKey(String groupName, String normalizedSubject) {
        return groupName + "\t" + normalizedSubject;
    }

    /**
     * TASK-636: how many pace curves are published - banner observability so
     * an armed flag over an empty reference is visibly a no-op.
     */
    public static int cadencePaceCurveCount() {
        return cadencePaceStatic.size();
    }

    /**
     * TASK-413: single source of truth for the (weekday, slotIndex) -&gt; int
     * cell encoding shared by the pre-solve template publisher
     * ({@code ScheduleService}) and the M32 constraint - so both sides can
     * never drift on how a cell is keyed. Weekly semantics: only the weekday
     * and slot matter (any DATE with that weekday/slot is the same cell), so
     * moving a lesson to another week at the same (weekday, slot) is NOT a
     * deviation.
     */
    public static int encodeTemplateCell(DayOfWeek dayOfWeek, int slotIndex) {
        return dayOfWeek.getValue() * TimeslotSequence.SLOTS_PER_DAY + slotIndex;
    }

    /**
     * TASK-413: scarcity-graduated M32 deviation weight for a course with
     * {@code vrpCells} legal (weekday, slot) placements: {@code clamp(K /
     * vrpCells, MIN, MAX)} with K/MIN/MAX the named {@code
     * TEMPLATE_DEVIATION_WEIGHT_*} knobs. A course with few legal cells (e.g.
     * a single-availability teacher) must be near-immobile; one with thousands
     * stays cheap to relocate. {@code vrpCells <= 0} (count missing, or zero
     * legal cells = maximally scarce) returns MAX.
     */
    public static int templateDeviationWeight(int vrpCells) {
        if (vrpCells <= 0) {
            return TEMPLATE_DEVIATION_WEIGHT_MAX;
        }
        int raw = TEMPLATE_DEVIATION_WEIGHT_NUMERATOR / vrpCells;
        return Math.max(TEMPLATE_DEVIATION_WEIGHT_MIN, Math.min(TEMPLATE_DEVIATION_WEIGHT_MAX, raw));
    }

    /**
     * Get pre-computed available timeslot count for a lesson by ID.
     * Returns null if not computed (e.g. during tests without full setup).
     */

    /**
     * Pre-compute available timeslot counts for all lessons.
     * Must be called AFTER all other static maps are set, because
     * {@link Lesson#getAvailableTimeslots()} depends on them.
     */
    public static void computeLessonAvailableTimeslotCounts(List<Lesson> lessons) {
        // Build locally, assign last (TASK-378): this map takes seconds to fill
        // (10.5k VRP evaluations) and is read per-compare by the difficulty
        // comparators on the SOLVER thread - publishing the empty map first and
        // populating it in place let an API-triggered rebuild race a mid-sort
        // read, tripping TimSort's "Comparison method violates its general
        // contract" and killing the solve (run 3/4, 2026-07-18).
        Map<Long, Integer> counts = new HashMap<>();
        for (Lesson lesson : lessons) {
            // TASK-578: FRESH - this method's own contract (call AFTER all static
            // maps are set) was silently defeated by a warm cache returning the
            // pre-republish size; the counts feed the MRV comparators and M32 weights
            counts.put(lesson.id, lesson.getAvailableTimeslotsFresh().size());
        }
        lessonAvailableTimeslotCountsStatic = counts;
    }

    /**
     * Whole-map accessor for comparators that must SNAPSHOT the reference once
     * per sort (TASK-378) - per-compare reads of the static can observe a
     * mid-sort replacement and violate comparator transitivity.
     */
    public static Map<Long, Integer> getLessonAvailableTimeslotCountsRef() {
        return lessonAvailableTimeslotCountsStatic;
    }

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<Timeslot> timeslots;

    @ProblemFactCollectionProperty
    // Room VRP moved to entity-level: Lesson.getAvailableRooms()
    private List<Room> rooms;

    @ProblemFactCollectionProperty
    private List<StagePattern> stagePatterns;

    @ProblemFactCollectionProperty
    private List<TeacherAvailability> teacherAvailabilities;

    @ProblemFactCollectionProperty
    private List<SchoolHoliday> schoolHolidays;

    @ProblemFactCollectionProperty
    private List<StudentGroup> studentGroups;

    @ProblemFactCollectionProperty
    private List<Teacher> teachers;

    @ProblemFactCollectionProperty
    private List<Course> courses;

    @ProblemFactCollectionProperty
    private List<LabAssistant> labAssistants;

    @ProblemFactCollectionProperty
    private List<ProfessionalizzantiDay> professionalizzantiDays = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<DailyActivityQuota> dailyActivityQuotas = new ArrayList<>();

    /**
     * TASK-454: export/import carriage for the group calendar rules. Plain data field,
     * deliberately NOT a {@code @ProblemFactCollectionProperty}: the solver consumes
     * them via {@link #setGroupCalendarRulesStatic} (same pattern as the quota static
     * maps), so this list exists purely so Jackson can carry them in the export
     * payload and {@code ImportService} can re-insert them.
     */
    private List<GroupCalendarRule> groupCalendarRules = new ArrayList<>();

    @PlanningEntityCollectionProperty
    private List<Lesson> lessons;

    // LabBlocks are deprecated - labs are now represented as pinned Lessons with isLabHour=true
    // The field has been removed to avoid Timefold configuration errors
    // (@ProblemFactCollectionProperty containing @PlanningEntity is invalid)

    /**
     * TASK-538 ramo B, milestone 1 (28/08): the score type is {@link BendableScore} with
     * {@code 2 hard levels + 2 soft levels} - hard[0] is the pure hard tier every former
     * {@code HardMediumSoftScore.ofHard} penalty moved to, hard[1] is RESERVED for the
     * quasi-hard tier (the H32/H33-family split, milestone 2) and is written by NO
     * constraint yet, soft[0] is the former medium tier, soft[1] the former soft tier.
     * All tokens and level reads flow through {@link com.school.planner.solver.ScoreCompat},
     * the single place declaring the indices.
     *
     * <p><b>Parity proof (semantics identical to {@code HardMediumSoftScore}).</b>
     * {@code BendableScore.compareTo} compares ALL hard levels in index order first,
     * then ALL soft levels in index order, returning at the first differing level
     * ({@code BendableScore.java} in timefold-solver-core 2.3.0). So for two scores of
     * this shape, {@code [h0, 0, s0, s1]} vs {@code [h0', 0, s0', s1']}: level hard[1]
     * is {@code 0 == 0} in milestone 1 (no constraint writes it), hence never the first
     * differing level, and the comparison reduces to {@code h0 vs h0'}, then
     * {@code s0 vs s0'}, then {@code s1 vs s1'} - exactly the lexicographic order of
     * {@code HardMediumSoftScore(h0, s0, s1)}. The official docs state the same for the
     * dual case: a BendableScore with one hard level and two soft levels is equivalent
     * to HardMediumSoftScore. A fortiori, adding a constantly-zero hard level preserves
     * the ordering. The numeric tier values are unchanged too (same weights, same
     * match counts), so every pinned expectation of the test suite is a parity guard.
     *
     * <p>The bendable level counts are fixed at compile time by the annotation below;
     * Timefold's {@code validateCompatible} then rejects any score of a different shape
     * loudly at runtime (which is why no score may be constructed ad hoc instead of via
     * {@code ScoreCompat}). String serialization changes format
     * ({@code "[h/h]hard/[m/s]soft"} instead of {@code "hhard/mmedium/ssoft"}): see the
     * serialization note at the checkpoint capture site in {@code ScheduleService}.
     */
    @PlanningScore(bendableHardLevelsSize = 2, bendableSoftLevelsSize = 2)
    private BendableScore score;

    /**
     * TASK-E1/E2 (27/08, room-tier distraction research): per-solve constraint weight
     * overrides, the Timefold 2.3 {@code ConstraintWeightOverrides} member discovered by
     * type on the {@code @PlanningSolution}. NEVER null (Timefold's validate() requires a
     * value on every working solution): {@link ConstraintWeightOverrides#none()} means no
     * overrides, byte-identical scoring to before this field existed. The staged cycle's
     * per-stage room-tier schema and the E1/E2 benches set this right before each solve;
     * set-once-per-solve only (mid-solve mutation needs a ProblemChange, Timefold #1131).
     * {@code @JsonIgnore}: not part of the export/import payload.
     */
    @JsonIgnore
    private ConstraintWeightOverrides<BendableScore> constraintWeightOverrides =
            ConstraintWeightOverrides.none();

    @JsonIgnore
    public ConstraintWeightOverrides<BendableScore> getConstraintWeightOverrides() {
        return constraintWeightOverrides;
    }

    public void setConstraintWeightOverrides(ConstraintWeightOverrides<BendableScore> overrides) {
        this.constraintWeightOverrides =
                overrides != null ? overrides : ConstraintWeightOverrides.none();
    }

    // Not planning properties — informational only for REST responses
    private SolverStatus solverStatus;
    private Instant solveStartTime;
    private Instant chCompletionTime;

    // Current solver phase tracking (0=not started, 1-5=active phase)
    private int currentPhase;
    private String phaseName;

    // TASK-419: current staged-solve stage, null when no staged cycle runs. Carried on the
    // FULL schedule payload too (not just the light /status one): the staged cycle's own
    // stages run with the SolverManager idle, so a reader that only sees solverStatus would
    // read a live cycle's half-promoted grid as a finished solve.
    private String stagedStage;

    // TASK-483: multi-start attempt state, published so the UI can show "tentativo X di Y"
    // alongside stagedStage. Both 0 when multi-start is off (the UI hides the indicator then).
    private int stagedAttempt;
    private int stagedMaxAttempts;
    // TASK-483: multi-start time budget (planner.multistart.budget-seconds), 0 when unset/no cap.
    // Published so the UI can show an honest "fino a ~Z min" cap (never a fabricated default time).
    private long stagedBudgetSeconds;

    // Cumulative timing across stop/resume cycles (in seconds)
    private long cumulativeElapsedSeconds;
    private long cumulativeChSeconds;
    private long cumulativeLsSeconds;
    private long preAssignmentDurationSeconds;

    // Pre-assignment status tracking
    private PreAssignmentStatus preAssignmentStatus;
    private Instant preAssignmentStartTime;
    private String preAssignmentError;
    private FailureAnalysis preAssignmentFailureAnalysis;

    @JsonIgnore
    private transient TimeslotSequence timeslotSequence;

    public SchoolSchedule() {
    }

    public SchoolSchedule(List<Timeslot> timeslots, List<Room> rooms,
                          List<StagePattern> stagePatterns,
                          List<TeacherAvailability> teacherAvailabilities,
                          List<SchoolHoliday> schoolHolidays,
                          List<StudentGroup> studentGroups,
                          List<Teacher> teachers,
                          List<Course> courses,
                          List<Lesson> lessons,
                          List<LabAssistant> labAssistants) {
        this.timeslots = timeslots;
        this.rooms = rooms;
        this.stagePatterns = stagePatterns;
        this.teacherAvailabilities = teacherAvailabilities;
        this.schoolHolidays = schoolHolidays;
        this.studentGroups = studentGroups;
        this.teachers = teachers != null ? teachers : new ArrayList<>();
        this.courses = courses != null ? courses : new ArrayList<>();
        this.lessons = lessons;
        this.labAssistants = labAssistants != null ? labAssistants : new ArrayList<>();
    }

    public TimeslotSequence getTimeslotSequence() {
        if (timeslotSequence == null && timeslots != null) {
            timeslotSequence = new TimeslotSequence(timeslots);
        }
        return timeslotSequence;
    }

    public List<Timeslot> getTimeslots() { return timeslots; }
    public void setTimeslots(List<Timeslot> timeslots) {
        this.timeslots = timeslots;
        this.timeslotSequence = null;
    }

    public List<Room> getRooms() { return rooms; }
    public void setRooms(List<Room> rooms) { this.rooms = rooms; }

    public List<StagePattern> getStagePatterns() { return stagePatterns; }
    public void setStagePatterns(List<StagePattern> stagePatterns) { this.stagePatterns = stagePatterns; }

    public List<TeacherAvailability> getTeacherAvailabilities() { return teacherAvailabilities; }
    public void setTeacherAvailabilities(List<TeacherAvailability> teacherAvailabilities) { this.teacherAvailabilities = teacherAvailabilities; }

    public List<SchoolHoliday> getSchoolHolidays() { return schoolHolidays; }
    public void setSchoolHolidays(List<SchoolHoliday> schoolHolidays) { this.schoolHolidays = schoolHolidays; }

    public List<StudentGroup> getStudentGroups() { return studentGroups; }
    public void setStudentGroups(List<StudentGroup> studentGroups) { this.studentGroups = studentGroups; }

    public List<Teacher> getTeachers() { return teachers; }
    public void setTeachers(List<Teacher> teachers) { this.teachers = teachers; }

    public List<Course> getCourses() { return courses; }
    public void setCourses(List<Course> courses) { this.courses = courses; }

    public List<LabAssistant> getLabAssistants() { return labAssistants; }
    public void setLabAssistants(List<LabAssistant> labAssistants) { this.labAssistants = labAssistants; }

    public List<ProfessionalizzantiDay> getProfessionalizzantiDays() { return professionalizzantiDays; }
    public void setProfessionalizzantiDays(List<ProfessionalizzantiDay> professionalizzantiDays) { this.professionalizzantiDays = professionalizzantiDays; }

    public List<DailyActivityQuota> getDailyActivityQuotas() { return dailyActivityQuotas; }
    public void setDailyActivityQuotas(List<DailyActivityQuota> dailyActivityQuotas) { this.dailyActivityQuotas = dailyActivityQuotas; }

    public List<GroupCalendarRule> getGroupCalendarRules() { return groupCalendarRules; }
    public void setGroupCalendarRules(List<GroupCalendarRule> groupCalendarRules) { this.groupCalendarRules = groupCalendarRules; }

    public List<Lesson> getLessons() { return lessons; }
    public void setLessons(List<Lesson> lessons) { this.lessons = lessons; }

    public BendableScore getScore() { return score; }
    public void setScore(BendableScore score) { this.score = score; }

    public SolverStatus getSolverStatus() { return solverStatus; }
    public void setSolverStatus(SolverStatus solverStatus) { this.solverStatus = solverStatus; }

    /** Non-null when the last solve DIED (exception) instead of terminating - see ScheduleService.lastSolverError. */
    private String lastSolverError;
    public String getLastSolverError() { return lastSolverError; }
    public void setLastSolverError(String lastSolverError) { this.lastSolverError = lastSolverError; }

    public Instant getSolveStartTime() { return solveStartTime; }
    public void setSolveStartTime(Instant solveStartTime) { this.solveStartTime = solveStartTime; }

    public Instant getChCompletionTime() { return chCompletionTime; }
    public void setChCompletionTime(Instant chCompletionTime) { this.chCompletionTime = chCompletionTime; }

    public int getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(int currentPhase) { this.currentPhase = currentPhase; }

    public String getPhaseName() { return phaseName; }
    public void setPhaseName(String phaseName) { this.phaseName = phaseName; }

    /** TASK-419: staged-solve stage label, null when no staged cycle is running. */
    public String getStagedStage() { return stagedStage; }
    public void setStagedStage(String stagedStage) { this.stagedStage = stagedStage; }

    /** TASK-483: current multi-start attempt (1-based), 0 when multi-start is off. */
    public int getStagedAttempt() { return stagedAttempt; }
    public void setStagedAttempt(int stagedAttempt) { this.stagedAttempt = stagedAttempt; }

    /** TASK-483: multi-start attempt cap, 0 when multi-start is off (UI hides the indicator then). */
    public int getStagedMaxAttempts() { return stagedMaxAttempts; }
    public void setStagedMaxAttempts(int stagedMaxAttempts) { this.stagedMaxAttempts = stagedMaxAttempts; }

    /** TASK-483: multi-start time-budget cap (seconds), 0 when unset. */
    public long getStagedBudgetSeconds() { return stagedBudgetSeconds; }
    public void setStagedBudgetSeconds(long stagedBudgetSeconds) { this.stagedBudgetSeconds = stagedBudgetSeconds; }

    public long getCumulativeElapsedSeconds() { return cumulativeElapsedSeconds; }
    public void setCumulativeElapsedSeconds(long v) { this.cumulativeElapsedSeconds = v; }

    public long getCumulativeChSeconds() { return cumulativeChSeconds; }
    public void setCumulativeChSeconds(long v) { this.cumulativeChSeconds = v; }

    public long getCumulativeLsSeconds() { return cumulativeLsSeconds; }
    public void setCumulativeLsSeconds(long v) { this.cumulativeLsSeconds = v; }

    public long getPreAssignmentDurationSeconds() { return preAssignmentDurationSeconds; }
    public void setPreAssignmentDurationSeconds(long v) { this.preAssignmentDurationSeconds = v; }

    public PreAssignmentStatus getPreAssignmentStatus() { return preAssignmentStatus; }
    public void setPreAssignmentStatus(PreAssignmentStatus preAssignmentStatus) { this.preAssignmentStatus = preAssignmentStatus; }

    public Instant getPreAssignmentStartTime() { return preAssignmentStartTime; }
    public void setPreAssignmentStartTime(Instant preAssignmentStartTime) { this.preAssignmentStartTime = preAssignmentStartTime; }

    public String getPreAssignmentError() { return preAssignmentError; }
    public void setPreAssignmentError(String preAssignmentError) { this.preAssignmentError = preAssignmentError; }

    public FailureAnalysis getPreAssignmentFailureAnalysis() { return preAssignmentFailureAnalysis; }
    public void setPreAssignmentFailureAnalysis(FailureAnalysis preAssignmentFailureAnalysis) { this.preAssignmentFailureAnalysis = preAssignmentFailureAnalysis; }

    /**
     * Clear all static state (for testing and benchmarking).
     * Must be called between benchmark runs to prevent state pollution.
     */
    public static void clearStaticState() {
        allTimeslotsStatic = null;
        allRoomsStatic = null;
        allLessonsStatic = null;
        pinnedLessonTimeslotsByGroupStatic = null;
        idealEndDateVrpBufferedStatic = null;
        idealEndDateInfoStatic = null;
        teacherAvailabilitiesStatic = null;
        schoolHolidaysStatic = null;
        stageBlackoutDatesStatic = null;
        timeslotSequenceStatic = null;
        labAssistantTeacherIdsStatic = null;
        labAssistantRoomTagByTeacherIdStatic = null;
        lessonAvailableTimeslotCountsStatic = null;
        groupCalendarRulesByGroupStatic = null;
        dailyActivityQuotasByGroupStatic = null;
        expectedHoursByGroupStatic = null;
        groupsWithStageFactsStatic = null;
        // TASK-558: same family as the two maps above (rebuilt by the same
        // setDailyActivityQuotasStatic pass) - must not survive a full reset with
        // the previous dataset's averages.
        averageDeclaredOrdinaryQuotaByGroupStatic = new HashMap<>();
        weeklyTemplateCellsByCourseStatic = new HashMap<>();
        weeklyTemplateDeviationWeightByCourseStatic = new HashMap<>();
        // TASK-419: cleared to the EMPTY set (not null, like the fields above):
        // isPromoted reads it on every score evaluation and must never NPE, and
        // "no promotions" is the correct post-clear state anyway.
        promotedLessonIdsStatic = Set.of();
        // TASK-424: same reasoning as the registry above - the day-fill target reads this
        // on every score evaluation, and "no pins known" is the correct post-clear state.
        authoritativePinnedHoursByGroupDateStatic = new HashMap<>();
        // TASK-425: same reasoning as M32's template maps above - the cadence
        // references are read on every score evaluation, and "no reference known"
        // is the correct post-clear state (both M34 and M35 self-gate to inert).
        cadenceBurstCapStatic = new HashMap<>();
        cadenceActiveWeeksStatic = new HashMap<>();
        // TASK-636: same family as the two maps above - the pace curve must not
        // survive a full reset with the previous dataset's reference.
        cadencePaceStatic = new java.util.HashMap<>();
        // TASK-464: same reasoning - the H2 exemption and H-COPRESENCE cap read this on
        // every score evaluation, and "no golden co-presenza known" is the correct
        // post-clear state (both self-gate to inert via the leading `enabled &&`).
        goldenCoPresenceDepthStatic = new HashMap<>();
        // TASK-497: the whole M20-cap family (pool targets, captured flag, TASK-461 ratio
        // fallback) clears together - see clearM20CapPoolStatics.
        clearM20CapPoolStatics();
    }
}
