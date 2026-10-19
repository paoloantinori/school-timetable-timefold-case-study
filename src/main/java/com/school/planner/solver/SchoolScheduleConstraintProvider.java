package com.school.planner.solver;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;
import ai.timefold.solver.core.api.score.stream.bi.BiConstraintStream;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.constraints.SparseDayJustification;
import com.school.planner.domain.constraints.StudentGroupDailyHoursJustification;
import com.school.planner.domain.constraints.TeacherDailyHoursJustification;
import com.school.planner.util.ConstraintIds;
import com.school.planner.util.SubjectNormalization;

import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.count;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.min;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.countDistinct;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.sum;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.toConsecutiveSequences;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.toList;

import ai.timefold.solver.core.api.score.stream.common.Break;
import ai.timefold.solver.core.api.score.stream.common.SequenceChain;
import ai.timefold.solver.core.api.score.stream.tri.TriConstraintStream;

/**
 * Full constraint provider for Phase 2 (main solving).
 *
 * Extends {@link BaseConstraintProvider} with aggregate and optimization constraints
 * that require all entities to be placed for correct evaluation.
 *
 * Full-solving constraints added here:
 * - H7: Max teacher daily hours (aggregate)
 * - H11: Max student group daily hours (aggregate)
 * - M13: Vertically compact (aggregate, medium weight=500)
 * - M15: Same-day same-subject consecutive (aggregate, medium since TASK-392; was H15)
 * - H16: Consecutive same-subject same teacher-room (pair)
 * - M29: Professionalizzanti at day edges (aggregate, TASK-370; was H22, demoted TASK-379)
 * - M4: Lab room-tag affinity (aggregate)
 * - S1-S10: All soft constraints
 */
public class SchoolScheduleConstraintProvider extends BaseConstraintProvider implements ConstraintProvider {

    private static final Logger LOG = Logger.getLogger(SchoolScheduleConstraintProvider.class);

    // M21 EXEMPTION WHITELIST (TASK-365, Q18 school answer 2026-07-17, referente):
    // "i LABORATORI, Informatica e Disegno possono avere due ore vicine, gli
    // altri POSSIBILMENTE no" - besides Course.isLab (handled by the method's
    // own filter), these non-lab subjects are also allowed multi-hour days.
    // Informatica itself needs no entry here: DataGenerator.getRoomTags()
    // already tags every "informatica*"-prefixed subject as a lab, so it's
    // covered by isLab. Disegno's two seed variants ("Disegno" and "Disegno
    // Edile ed Impiantistico") are NOT lab-tagged (DataGenerator leaves both
    // conservative/unclassified, see its getRoomTags javadoc) but the docx
    // names "Disegno" as a subject taught in blocks regardless of room, so
    // both variants are whitelisted here. Competenze Digitali comes from the
    // same docx clause 2 (vincoli-in-parole.md) verbatim.
    private static final Set<String> M21_EXEMPT_SUBJECTS = Set.of(
            "Competenze Digitali", "Disegno", "Disegno Edile ed Impiantistico");

    // M21 threshold (TASK-381 "L3", golden-first: raised from 1h to 2h - see
    // m21MaxHoursPerSubjectPerDay's own javadoc for the golden justification).
    private static final int M21_MAX_HOURS_PER_SUBJECT_PER_DAY = 2;

    // M-TR (TASK-468) - pratico-tecnico EXEMPT set for the theory-adjacent rule.
    // M-TR theory classification is single-sourced in SubjectNormalization.isTheoryForRule
    // (the exempt set + the !isLab convention), shared with the TASK-470 theory-spread
    // construction prototype in WeeklyTemplateBuilder so the constraint and the construction
    // agree on what "theory" means. See SubjectNormalization.THEORY_RULE_PRACTICAL_EXEMPT
    // for the rationale (432-day golden self-gating, authoritative DB spellings, the
    // Figurino/Personalizzazione borderline one-line flip open at AC#2). It used to live
    // here as a private THEORY_RULE_PRACTICAL_EXEMPT constant; moved out so a borderline
    // flip lands in both consumers at once instead of silently diverging.

    // M-TR (TASK-468) - T06 alias hook + the theory-adjacent grouping key now live in
    // SubjectNormalization (THEORY_RULE_SUBJECT_ALIAS + theoryRuleGroupingKey), single-sourced so
    // the M-TR constraint and the TASK-470 construction prototype share one notion of "same
    // subject". See there.

    /**
     * Debug mode flag - disables ALL constraints for pure CH testing.
     * When true, defineConstraints() returns ZERO constraints.
     *
     * Can be set via:
     * - System property: -Dsolver.constraints.debug.disabled=true
     * - Environment variable: SOLVER__CONSTRAINTS__DEBUG_DISABLED=true
     * - Runtime API: POST /api/schedule/constraints/debug-mode?enabled=true
     */
    private static volatile boolean debugConstraintsDisabled = false;
    private static final String DEBUG_MODE_PROPERTY = "solver.constraints.debug.disabled";

    /**
     * Diagnostic per-constraint EXCLUSION (E1 leave-one-out profiling, 2026-07-31). A
     * comma-separated list of canonical constraint IDs (e.g. {@code "H17,M32"}) to DROP from the
     * FULL constraint set, read LIVE at {@code defineConstraints} time so it reaches the app JVM
     * on the {@code java -jar} command line (MAVEN_OPTS lesson, CLAUDE.md). Inert when unset: the
     * FULL set is returned byte-identical. This is the per-ID scalpel the leave-one-out profiler
     * ({@code ConstraintProfilingLeaveOneOutTest}) uses to isolate each constraint's
     * score-calculation cost; it is NOT the all-or-nothing {@link #isDebugConstraintsDisabled()}
     * switch (which returns ZERO constraints). IDs match via
     * {@link ConstraintIds#canonicalId(String)} on each built constraint's ref, so the matcher
     * stays correct as constraints are added/renamed. Applied to the FULL path only.
     */
    public static final String DIAG_EXCLUDE_PROPERTY = "planner.constraints.diag.exclude";

    /**
     * Compaction-only mode flag - enables ONLY H1, H2, H3, M13, M20 constraints.
     * This allows testing pure compaction behavior without other constraints
     * interfering (availability, holidays, max hours, etc.).
     *
     * Expected behavior: Calendar fills from September onwards, constrained only
     * by teacher/room/group conflicts and compaction preferences.
     *
     * Can be set via:
     * - System property: -Dsolver.constraints.compaction.only=true
     * - Environment variable: SOLVER__CONSTRAINTS__COMPACTION__ONLY=true
     * - Runtime API: POST /api/schedule/constraints/compaction-only-mode?enabled=true
     */
    private static volatile boolean compactionOnlyMode = false;
    private static final String COMPACTION_ONLY_PROPERTY = "solver.constraints.compaction.only";

    /**
     * Pre-assignment mode flag. When true, defineConstraints() returns ONLY
     * the sparse-compatible base constraints (suitable for Phase 1 where most
     * entities are unassigned). When false (default), returns all constraints.
     */
    private static volatile boolean preAssignmentMode = false;

    public static void setDebugConstraintsDisabled(boolean disabled) {
        debugConstraintsDisabled = disabled;
        LOG.warnf("Debug constraints disabled: %s - CH will produce unconstrained (possibly invalid) schedules", disabled);
    }

    public static boolean isDebugConstraintsDisabled() {
        return debugConstraintsDisabled ||
               Boolean.parseBoolean(System.getProperty(DEBUG_MODE_PROPERTY, "false"));
    }

    public static void setCompactionOnlyMode(boolean enabled) {
        compactionOnlyMode = enabled;
        LOG.warnf("Compaction-only mode: %s - Only H1, H2, H3, M13, M20 constraints active", enabled);
    }

    public static boolean isCompactionOnlyMode() {
        return compactionOnlyMode ||
               Boolean.parseBoolean(System.getProperty(COMPACTION_ONLY_PROPERTY, "false"));
    }

    public static void setPreAssignmentMode(boolean mode) {
        preAssignmentMode = mode;
    }

    public static boolean isPreAssignmentMode() {
        return preAssignmentMode;
    }

    /** Delegates to the testable seam with the live system property. */
    private static Constraint[] applyDiagExclude(Constraint[] all) {
        return applyDiagExclude(all, System.getProperty(DIAG_EXCLUDE_PROPERTY));
    }

    /**
     * Testable seam: drops any built constraint whose canonical ID is in {@code rawExclude}
     * (comma-separated, blank-tolerant). {@code null}/blank {@code rawExclude} is a no-op (the
     * default solve is byte-identical). IDs match via {@link ConstraintIds#canonicalId(String)}
     * on each built constraint's ref. Logs the dropped count and warns on any listed ID that
     * matches no built constraint (typo / stale id), so a silent no-op can't hide a bad probe.
     */
    static Constraint[] applyDiagExclude(Constraint[] all, String rawExclude) {
        if (rawExclude == null || rawExclude.isBlank()) {
            return all;
        }
        Set<String> exclude = Arrays.stream(rawExclude.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (exclude.isEmpty()) {
            return all;
        }
        // Canonical ID per constraint computed ONCE (was two map passes: present-set + filter).
        List<String> ids = Arrays.stream(all)
                .map(c -> ConstraintIds.canonicalId(c.getConstraintRef().id()))
                .toList();
        Set<String> present = new HashSet<>(ids);
        Constraint[] filtered = IntStream.range(0, all.length)
                .filter(i -> !exclude.contains(ids.get(i)))
                .mapToObj(i -> all[i])
                .toArray(Constraint[]::new);
        List<String> unmatched = exclude.stream().filter(id -> !present.contains(id)).sorted().toList();
        LOG.warnf("DIAG exclude filter: %s -> dropped %d/%d constraints%s",
                exclude, all.length - filtered.length, all.length,
                unmatched.isEmpty() ? "" : " [WARN unmatched IDs (typo/stale): " + unmatched + "]");
        return filtered;
    }


    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        // Debug mode check FIRST - disables ALL constraints for pure CH testing
        if (isDebugConstraintsDisabled()) {
            LOG.warnf("defineConstraints called in DEBUG mode: returning ZERO constraints - pure CH behavior");
            return new Constraint[0];
        }
        // Compaction-only mode check SECOND - only H1, H2, H3, M13, M20
        if (isCompactionOnlyMode()) {
            Constraint[] compaction = getCompactionOnlyConstraints(constraintFactory);
            LOG.warnf("defineConstraints called in COMPACTION-ONLY mode: returning %d constraints (H1, H2, H3, M13, M20)", compaction.length);
            return compaction;
        }
        // Pre-assignment mode check THIRD - returns base constraints only
        if (preAssignmentMode) {
            Constraint[] base = getBaseConstraints(constraintFactory);
            LOG.infof("defineConstraints called in PRE-ASSIGNMENT mode: returning %d hard-only base constraints", base.length);
            return base;
        }
        // Normal mode - all constraints
        Constraint[] all = Stream.concat(
                Arrays.stream(getBaseConstraints(constraintFactory)),
                Arrays.stream(getFullSolvingConstraints(constraintFactory))
        ).toArray(Constraint[]::new);
        all = applyDiagExclude(all); // E1 leave-one-out profiling filter; inert when property unset
        LOG.infof("defineConstraints called in FULL mode: returning %d constraints", all.length);
        return all;
    }

    /**
     * Returns ONLY compaction-related constraints: H1, H2, H3, H7, H11, H17, M20, M33, M22.
     * Used for testing pure compaction behavior without other constraints interfering.
     *
     * H1, H2, H3 - Conflict constraints (from base) - force valid placement
     * H7, H11 - Daily hour limits (essential for spreading across days)
     * H17 - Vertically compact - eliminates gaps within days
     * M20 - Horizontally compact - no sparse days (1-3 hours)
     * M33 - Surplus beyond expected day hours - M20's own mirror image (TASK-423)
     * M22 - Calendar compactness - no empty days within calendar span (task-273)
     */
    private Constraint[] getCompactionOnlyConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                // H1, H2, H3 - Conflict constraints (essential for valid placement)
                teacherConflict(constraintFactory),
                roomConflict(constraintFactory),
                studentGroupConflict(constraintFactory),
                // H7, H11 - Daily hour limits (essential for spreading across days)
                maxTeacherDailyHours(constraintFactory),
                maxStudentGroupDailyHours(constraintFactory),
                // H17, M20, M22 - Compaction constraints
                verticallyCompact(constraintFactory),
                horizontallyCompact(constraintFactory),
                horizontallyCompactEmptyDay(constraintFactory),
                // M33 - the surplus side of the same day-fill target (TASK-423):
                // without it this debug mode prices only the deficit half of the
                // family and reads a day above plan as free, which is exactly the
                // behaviour M33 exists to correct.
                surplusBeyondExpectedHours(constraintFactory),
                calendarCompact(constraintFactory)
        };
    }

    /**
     * Returns constraints that require all entities to be placed for correct evaluation.
     * These are aggregate constraints (per-group/day), optimization constraints,
     * and soft constraints.
     */
    private Constraint[] getFullSolvingConstraints(ConstraintFactory constraintFactory) {
        return java.util.Arrays.stream(new Constraint[] {
                // ── Hard constraints (aggregate) ──
                // H7 - Max teacher daily hours (L + Block)
                maxTeacherDailyHours(constraintFactory),
                // H11 - Student group cannot exceed maxHoursPerDay
                maxStudentGroupDailyHours(constraintFactory),
                // M15 - Same-day same-subject lessons should be consecutive (task-017; medium since TASK-392)
                sameDaySameSubjectShouldBeConsecutive(constraintFactory),
                // S16 - Consecutive same-subject should use same teacher (was H16a, TASK-377)
                // M37 - Consecutive same-subject same-teacher should keep the room (was H16b hard; demoted TASK-446/447 notte)
                consecutiveSameSubjectSameTeacher(constraintFactory),
                consecutiveSameSubjectSameTeacherSameRoom(constraintFactory),
                // H29 - Assigned lesson must have a room (was M36 medium 1000 / bocciato H27;
                // promoted to HARD weight 1 by TASK-447, paired with H0 weight 2 anti-arbitrage).
                assignedLessonMustHaveRoom(constraintFactory),
                // H28 - No teacher return within (group, subject, day) (TASK-448, golden-first guard a delta zero)
                teacherNoReturnWithinGroupSubjectDay(constraintFactory),
                // M29 - Professionalizzanti at day edges (was H22; TASK-370/379)
                professionalizzantiAtDayEdges(constraintFactory),
                // ── Medium constraints (aggregate) ──
                // M4 removed: Lab room-tag affinity pre-validated by pre-assignment (TASK-228)
                // M23 - Block lesson pair togetherness (Disegno edile 2h blocks)
                blockLessonPairTogetherness(constraintFactory),
                // M18 - 5-hour groups should start from slot 0 (task-247)
                // Downgraded from hard to medium for solver flexibility - H18 made it
                // difficult to satisfy H11 simultaneously for 5-hour groups
                dayShouldStartFromFirstOpenSlot(constraintFactory),
                startOfDayHardEnabled() ? dayMustStartFromFirstOpenSlot(constraintFactory) : null,
                quotaFillHardEnabled() ? dayFillMeetsDeclaredQuota(constraintFactory) : null,
                dayFloorHardEnabled() ? dayFillMeetsHardFloor(constraintFactory) : null,
                // H34 - Empty declared day (TASK-557): H32's blind-spot companion,
                // RATIFIED DEFAULT ON 26/08. ALWAYS registered, armed PER CELL (see the
                // gate inside emptyDeclaredDay: the cached app-context factory makes
                // registration gating cache-hostile).
                emptyDeclaredDay(constraintFactory),
                // M20 - Horizontally compact — push lessons toward start of year (task-168)
                // Renamed from M12 to avoid collision with "Overflow beyond target end date"
                horizontallyCompact(constraintFactory),
                // M20.2 - Empty expected day (TASK-409)
                horizontallyCompactEmptyDay(constraintFactory),
                // M33 - Surplus beyond expected day hours (TASK-423): M20's mirror
                // image, so the LS cannot park hours above the day's plan for free
                surplusBeyondExpectedHours(constraintFactory),
                // M13 - Vertically compact — demoted from hard to medium(500) for solver flexibility
                verticallyCompact(constraintFactory),
                // ── Soft constraints ──
                minimizeTeacherGaps(constraintFactory),
                // S2 removed (TASK-381 "L3"): superseded by H15 (adjacency forced hard) +
                // M21 (volume capped) - see CLAUDE.md's "Shape recalibration (L3, TASK-381)".
                teacherPreference(constraintFactory),
                m21MaxHoursPerSubjectPerDay(constraintFactory),
                // M32 - Template deviation (TASK-413): scarcity-graduated
                // penalty for leaving the pre-solve weekly template cell
                templateDeviation(constraintFactory),
                // M24/M25 - Weekly choice-day constraints (TASK-368: T11's
                // "one 8am day/week", T05's "one free day/week")
                atMostOneEarlyStartDayPerWeek(constraintFactory),
                requiredFreeDayPerWeek(constraintFactory),
                // M27 - T20 subject start-period preference (TASK-369)
                T20SubjectStartPeriod(constraintFactory),
                // S5 removed - superseded by H13 (vertically compact)
                rewardConsecutiveStudentLessons(constraintFactory),
                // S17 - Reward same-subject block pair (TASK-381 "L3"): the
                // block-forming counterpart to M21's threshold-2 cap above
                rewardSameSubjectBlockPair(constraintFactory),
                penalizeIsolatedStudentLessons(constraintFactory),
                // S9 - Dilute hours over the year (task-014) — DISABLED (task-273)
                // Temporarily removed: penalizes >1 lesson/subject/week, which actively
                // opposes calendar compactness (M22). Will be restored as a max-per-week
                // cap (e.g., max 3h/subject/week) instead of penalizing >1h/week.
                // See task-274 for restoration plan.
                // diluteHoursOverYear(constraintFactory),
                // S12 - Weekly subject presence (TASK-369) - NOT a restoration of S9
                // above (see the constraint's own javadoc for why they're materially
                // different rules), a distinct new reward for the docx's separate
                // "possibilmente >=1h/week per subject" clause.
                weeklySubjectPresence(constraintFactory),
                // S13 - Weekly stability (TASK-375): reward unpinned lessons
                // matching their previous-week (group, slot, teacher, subject) cell
                weeklyStability(constraintFactory),
                // M34/M35 - Golden cadence burst cap + spread deficit (TASK-425):
                // self-gating per-(group,subject) reference published pre-solve from
                // golden-cadence-reference.json - M34 caps weekly burst, M35 charges
                // spread shortfall, both measure against the pair's OWN golden cadence.
                weeklySubjectBurstCap(constraintFactory),
                weeklySubjectSpreadDeficit(constraintFactory),
                // S10 - Discourage generic lessons in lab rooms (task-007)
                labRoomReserved(constraintFactory),
                // M-LF - Non-lab lesson in a tagged (lab) room (TASK-456 lab-fallback, domanda 21):
                // the medium-tier, flag-gated discouragement that keeps labs as last-resort shelter
                // once the value domain (Lesson.getAvailableRooms) opens tagged rooms to non-lab.
                // Inert (0 matches) at default flag OFF - see nonLabInTaggedRoom's own javadoc.
                nonLabInTaggedRoom(constraintFactory),
                // M38 - Pair-cell deviation (TASK-576 F7-C): the LS-side defender of the
                // pair-cells form. E2 (1/09) measured the repairs moving ~32 units against
                // ~574 missing cells: the erosion lives in the LS phases (compactness runs
                // BEFORE the promotion, everything unpinned), so the defense belongs where
                // the LS can SEE it - a medium charge in the delta score.
                // DEFAULT ON since 2026-09-02 (opt-out); inert (0 matches) while the
                // pair-cells builder stays opt-in. See pairCellDeviation.
                pairCellDeviation(constraintFactory),
                // S11 - Student group room affinity (task-008)
                studentGroupRoomAffinity(constraintFactory),
                // M-TC - Lab tag capacity (TASK-455, scarcity-aware spec 2026-07-30):
                // flag-gated DEFAULT OFF prototype. Penalizes, per (tag, date, hour)
                // cell, the lab lessons of a tag beyond its physical room capacity, the
                // downstream spreader consumer for the joint scarcity (tag, weekday)
                // the 30e pre-measure isolated (acconciatura + termoidraulica, 99% of
                // the 169 H29 room-null). Inert (0 matches) at default flag OFF.
                labTagCapacity(constraintFactory),
                // H-COPRESENCE - Guest co-presenza count cap (TASK-464): the hard
                // count-based backstop for the H2 pairwise exemption. flag-gated DEFAULT
                // OFF; inert (0 matches) at default. See coPresenceCap's own javadoc.
                coPresenceCap(constraintFactory),
                // M-TR - Theory subject repeated same day (TASK-468, tab "Vincoli DA
                // ANALIZZARE"): soglia-1 medium on (group, theory-subject, day) with
                // 2+ hours, calibrated on the golden's 432-day residual (AC#1).
                // flag-gated DEFAULT OFF; inert (0 matches) at default. See
                // theorySubjectRepeatedSameDay's own javadoc.
                theorySubjectRepeatedSameDay(constraintFactory),
                labTeacherDayContiguity(constraintFactory),
                // M-CPS - Curriculum post-stage target ~6h asymmetric (TASK-469, bucket
                // C3/T4): asymmetric medium on the post-stage curriculum hours of the three
                // final-year cohorts (G10 / G08 / G11, T11). DEFAULT ON since the
                // 2026-08-01 ratification (opt-out via planner.constraints.curriculum-poststage
                // .enabled=false); inert (0 matches) when opted out. See curriculumPostStage.
                curriculumPostStage(constraintFactory),
                // M22 - Calendar compactness — penalize empty days within span (task-273)
                calendarCompact(constraintFactory)
        }).filter(java.util.Objects::nonNull).toArray(Constraint[]::new);
    }

    // ─── H7 Max Teacher Daily Hours ─────────────────────────────────

    /**
     * <p><b>Origine.</b> "Non piu' di 6 ore di lezione al giorno per ogni docente" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", riga 108).
     */
    Constraint maxTeacherDailyHours(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)
                // TASK-346: pseudo teachers (e.g. "Stage") carry bogus aggregate
                // hours across different groups' pinned lessons - not a real person's workload.
                .filter(l -> l.getTeacher() != null && !l.getTeacher().pseudo)
                .groupBy(Lesson::getTeacher, l -> l.timeslot.getDate(), count())
                // TASK-348: Teacher.getEffectiveMaxDailyHours() resolves the
                // per-teacher override (e.g. T01's real 8h/day full-day
                // lab), falling back to the global default - single source of
                // truth shared with ScheduleService's per-lesson H7 mirror.
                .penalize(ScoreCompat.ONE_HARD,
                        (teacher, date, lessonCount) -> Math.max(0, lessonCount - teacher.getEffectiveMaxDailyHours()))
                .justifyWith((teacher, date, lessonCount, score) ->
                        new TeacherDailyHoursJustification(
                                teacher.id,
                                date,
                                lessonCount,
                                teacher.getEffectiveMaxDailyHours(),
                                Math.max(0, lessonCount - teacher.getEffectiveMaxDailyHours())
                        ))
                .asConstraint("H7 - Max teacher daily hours");
    }

    // H11 - Student group cannot exceed maxHoursPerDay (if set)
    // SIMPLIFIED (TASK-228): Labs are now pinned Lessons, so only count Lesson entities.
    // TASK-440: the T28 id-14 exception (H11-T28 + this constraint's exclusion filter)
    // removed - the hardcoded id pointed at the wrong teacher on the current seed
    // (id 14 = T09; T28 is NOT in the 2026/27 roster, and id 18 is T01, who carries a real 8h/day exception) and H7's per-teacher cap bites first, so the
    // exception was mute (verified: golden regression H11 stays 0).
    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; una classe non supera il suo monte-ore giornaliero (campo group.maxHoursPerDay, derivato dal curriculum), introdotto fin dalle origini.
     */
    Constraint maxStudentGroupDailyHours(ConstraintFactory constraintFactory) {
        // All hours are now Lessons (labs are pinned Lessons with isLabHour=true)
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> lesson.timeslot != null)
                .filter(lesson -> lesson.course != null
                        && lesson.course.studentGroup != null
                        && lesson.course.studentGroup.maxHoursPerDay != null)
                .groupBy(Lesson::getStudentGroup,
                        lesson -> lesson.timeslot.getDate(),
                        count())
                .filter((group, date, totalHours) -> {
                    if (group == null || group.maxHoursPerDay == null) return false;
                    return totalHours > group.maxHoursPerDay;
                })
                .penalize(ScoreCompat.of(1000, 0, 0),
                        (group, date, totalHours) -> totalHours - group.maxHoursPerDay)
                .justifyWith((group, date, totalHours, score) ->
                        new StudentGroupDailyHoursJustification(
                                group.id,
                                date,
                                totalHours,
                                group.maxHoursPerDay,
                                totalHours - group.maxHoursPerDay
                        ))
                .asConstraint("H11 - Max student group daily hours");
    }


    // M18 - Day should start from first open slot (task-247, widened TASK-405)
    // Every (group, date)'s first lesson should sit at the group's first
    // calendar-open slot that day. Historically gated to <=5-open-slot days
    // (task-247/350) with a flat ONE_MEDIUM weight; TASK-405 removed the gate
    // and made the penalty a per-offset gradient - see the inline comment on
    // the stream below for the golden measurement (99.3% anchored) and the
    // weight rationale.
    //
    // DOWNGRADED from hard (H18) to medium for solver flexibility: as hard it
    // conflicted with H11 for groups like G08, and a day whose teachers are
    // genuinely unavailable early must stay legal. Medium keeps the escape
    // valve while making floating runs cost something real.
    /**
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: il golden ancora il 99,3% dei giorni-gruppo al primo slot aperto (misure task405-results). Allargato a ogni giorno col gradiente per-offset TASK-405; era gated ai giorni corti (task-247/350).
     */
    Constraint dayShouldStartFromFirstOpenSlot(ConstraintFactory constraintFactory) {
        // OPTIMIZED (2026-03-19): Replaced toList() with min() collector.
        // toList() negates incremental score calculation — every entity placement
        // rebuilds the entire list and re-scans it. Using min(slotIndex) is O(1)
        // incremental and equivalent: if the min slot index is past the group's
        // first OPEN slot for that day, no lesson starts as early as it could.
        // Slot index comes from TimeslotSequence.slotIndexOf() (TASK-341) instead
        // of raw hour arithmetic, so it stays correct if the slot schema's start
        // hour ever changes.
        //
        // TASK-342: "start of day" is no longer hardcoded to slot 0 — it's the
        // group's first calendar-open slot for that weekday/date (e.g. a group
        // whose calendar starts at slot 1 on the first school day, or slot 0 any
        // other day). SchoolSchedule.getGroupCalendarWindow()'s minOpenSlot is 0
        // when no GroupCalendarRule applies, preserving the old hardcoded-0 behavior.
        //
        // TASK-350: the applicability gate moved from the group's static
        // maxHoursPerDay field to the day's actual calendar-open-slot count
        // (SchoolSchedule.getGroupCalendarWindow(), calendar-aware and resolved
        // once for both the gate and the anchor below - see its own javadoc).
        // A mixed-calendar group like G12
        // (Mon-Wed 5-slot, Thu 8-slot, Fri 7-slot) has a single static
        // maxHoursPerDay=8 that can never distinguish its own short days from its
        // long ones: the old field-based gate either excluded the group entirely
        // (8 > 5, as was the case here) or - for a group whose static field
        // happened to read <=5 - would have wrongly gated its long/open-calendar
        // days too. Gating per (group, date) fixes both directions at once.
        // TASK-405: WIDENED to every (group, date) day and given a per-offset gradient.
        // The old <=5-open-slots gate excluded exactly the groups that float (8-slot
        // windows with 5-6h expected days: G10, 2/G11, G08, G13...), and the
        // flat ONE_MEDIUM/day weight (-17 total in a real run) was invisible next to
        // M20/M22's 1000/day - the M15 lesson again. Measured 2026-07-23: the golden
        // anchors 99.3% of group-days at 8:00 (the rest are the ramp Monday, whose
        // calendar itself opens at slot 1 - i.e. ~0 violations vs the first OPEN
        // slot), ours floated at 81%. Weight RETUNED 250 -> ofMedium(50) by the
        // 2026-07-24 laptop A/B (task405-results): at 250 day-start hit 96.2-96.7%
        // but recurrence fell below its ~35% floor in both runs, full-day lost
        // ~1pt and medium +127k - the predicted "too strong" signature. The
        // deterministic left-anchor pass (StudentDayCompaction.anchorRunLeft,
        // 132 days/solve at 0 conflicts) carries the anchor regardless of this
        // weight; 50 only makes floating non-free for the ~70-80 residual
        // floaters (almost all offset-1) without competing at M20/M22 scale.
        // Deliberately NOT pinned-exempt (like before): a day whose pinned shift
        // starts late is still anchorable by placing unpinned lessons earlier.
        // The calendar window is resolved ONCE per (group, date) in the map step -
        // the filter and the weigher both read the precomputed offset instead of
        // each re-resolving getGroupCalendarWindow (the same single-resolution
        // property the pre-405 gate+anchor structure had).
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> lesson.timeslot != null)
                .filter(lesson -> lesson.course != null && lesson.course.studentGroup != null)
                .groupBy(
                        Lesson::getStudentGroup,
                        lesson -> lesson.timeslot.getDate(),
                        min((Lesson l) -> TimeslotSequence.slotIndexOf(l.timeslot)))
                .map((group, date, minSlotIndex) ->
                        new DayStartOffset(group, date, m18DayStartOffset(group, date, minSlotIndex)))
                .filter(dayStart -> dayStart.offsetSlots() > 0)
                // Weight knob (TASK-450, 2026-07-29): 50 is what TASK-405's A/B settled on
                // after 250 bought day-start but cost recurrence. That trade was measured on
                // a much earlier model (before M32's template pricing, the M20 netting and the
                // staged cycle), and 50 is now the only thing defending a day's leading slot
                // against constraints worth 1000, so the trade deserves re-measuring rather
                // than re-deciding: -Dplanner.m18.weight=N, default 50, no behaviour change.
                .penalize(ScoreCompat.ofMedium(readCadenceKnobProperty("planner.m18.weight", 50)),
                        DayStartOffset::offsetSlots)
                // TASK-376 train review: emit the public justification so the
                // i18n analysis path (ConstraintMessageFormatter case "M18",
                // TASK-103 pattern like H7/H11/M20) is reachable - without this
                // the formatter branch and its bundle keys were dead scaffolding.
                .justifyWith((dayStart, score) -> new com.school.planner.domain.constraints.DayStartOffsetJustification(
                        dayStart.group().id, dayStart.date(), dayStart.offsetSlots()))
                .asConstraint("M18 - Day should start from first open slot");
    }

    /** H32 opt-out flag (standard form): null/absent -> ON, only the literal false disarms. */
    static boolean quotaFillHardEnabled() {
        String v = System.getProperty("planner.constraints.quota-fill.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /** H31 opt-out flag (standard form): null/absent -> ON, only the literal false disarms. */
    static boolean startOfDayHardEnabled() {
        String v = System.getProperty("planner.constraints.start-of-day.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /** H33 opt-out flag (standard form): null/absent -> ON, only the literal false disarms. */
    public static boolean dayFloorHardEnabled() {
        String v = System.getProperty("planner.constraints.day-floor.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /** H34 flag (TASK-557): RATIFIED DEFAULT ON (maintainer, 26/08, opzione A:
     *  default ON + residui come cura search-side). Standard opt-OUT form like
     *  H32/H33: null/absent -> ON, only the literal "false" disarms. Public for
     *  ScheduleService's banner echo (same as dayFloorHardEnabled). */
    public static boolean emptyDayHardEnabled() {
        String v = System.getProperty("planner.constraints.empty-day.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * TASK-538 milestone 2 (TASK-566): when true, H34 scores on hard[1] (quasi)
     * instead of hard[0] (pure), letting the LS traverse empty-declared-day
     * violations at an intermediate price instead of an all-or-nothing wall.
     * Prototype flag: DEFAULT OFF (opt-in, like M-TC). Read PER MATCH (tuple
     * time, same as emptyDayHardEnabled) because the ConstraintVerifier reuses
     * streams across sessions (the m22MinDays lesson).
     */
    public static boolean quasiTierEnabled() {
        String v = System.getProperty("planner.constraints.quasi-tier.enabled");
        return v != null && Boolean.parseBoolean(v);
    }

    // ── H31 - Day must start from first calendar-open slot (TASK-527 turno 14:10) ──
    //
    // maintainer's inspection 19/08: a solution reading 0hard showed G04 starting
    // 15/09 at 10:00 - "non e' mai stato possibile questo". The golden anchors
    // 99.86% of group-days at the first open slot (measured: 1943 at 08:00, 13
    // exceptions all on the first school day 14/09 at 09:00, 3 scattered G08
    // 09:00, 1 at 14:00). Near-miss protocol (registered today): <=1-2h,
    // non-systematic -> the constraint is valid and the golden's exceptions are
    // the human planner's gaps. Promotion to HARD with the day-level exemption:
    // a day carrying AUTHORITATIVE pins (golden import, constrained-teacher,
    // calendario) is the school's own declaration and is exempt (the imported
    // golden is all-pinned, so it scores 0 by construction; our authoritative
    // pin days keep their declared shape). Opt-out flag, standard form:
    // planner.constraints.start-of-day.enabled, null/absent -> ON.
    Constraint dayMustStartFromFirstOpenSlot(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> lesson.timeslot != null)
                .filter(lesson -> lesson.course != null && lesson.course.studentGroup != null)
                .groupBy(
                        Lesson::getStudentGroup,
                        lesson -> lesson.timeslot.getDate(),
                        min((Lesson l) -> TimeslotSequence.slotIndexOf(l.timeslot)))
                .map((group, date, minSlotIndex) ->
                        new DayStartOffset(group, date, m18DayStartOffset(group, date, minSlotIndex)))
                .filter(dayStart -> dayStart.offsetSlots() > 0)
                // Day-level authoritative-pin exemption (the school declared this day)
                .filter(dayStart -> SchoolSchedule.getAuthoritativePinnedHours(
                        dayStart.group().id, dayStart.date()) == 0)
                .penalize(ScoreCompat.ofHard(1),
                        DayStartOffset::offsetSlots)
                .justifyWith((dayStart, score) -> new com.school.planner.domain.constraints.DayStartOffsetJustification(
                        dayStart.group().id, dayStart.date(), dayStart.offsetSlots()))
                .asConstraint("H31 - Day must start from first calendar-open slot");
    }

    // ── H32 - Day fill meets the declared quota (TASK-532, spec 19/08) ──
    //
    // maintainer's directive (input-first for DECLARATIONS): the calendar sheets are
    // the school's own statement of how many aula+lab hours a day should carry,
    // and the golden converges to them EXACTLY for 10/11 groups (Fedora's static
    // analysis: the only real exception is G09 01/10, quota 6 vs golden 5,
    // 1h of declared tolerance). The G12 "contradiction" is entirely in
    // 14-24/06/2027 quota rows PAST the year end: no Timeslot exists there, so
    // the scope below never sees them. HARD, tolerance ZERO, with these scopes:
    // - only dates that HAVE a Timeslot (phantom June rows are out by construction);
    // - G04 and G08 are EXCLUDED: their right target is the TASK-497 forma-b
    //   pool-cap (already ratified, exact-pool per-day targets), not the sheet
    //   quota (the 835-vs-750 / 666-vs-626 input divergences would double-count);
    // - G14 is EXCLUDED (import-pinned special programme, 30 quota rows);
    // - G09 carries a declared 1h tolerance (the one golden gap).
    // Non-Stage assigned hours only (Stage pseudo-teacher hours are a different
    // bucket the sheet tracks separately as stageHours). SCOPE SUBTLETY: the
    // groupBy only sees dates WITH lessons, so a TOTALLY-empty expected day is
    // invisible here and stays M20.2's complement (medium): H32 catches the
    // PARTIAL underfill (3h on a 5h declared day), which is the defect class
    // the run showed (7 days <=3h, 6 of them Fridays).
    Constraint dayFillMeetsDeclaredQuota(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> lesson.timeslot != null)
                .filter(lesson -> lesson.course != null && lesson.course.studentGroup != null)
                // non-Stage hours only (pseudo teacher = Stage bucket)
                .filter(lesson -> {
                    var t = lesson.getTeacher();
                    return t == null || !t.pseudo;
                })
                // scope: the group must not be in the excluded set
                .filter(lesson -> !isQuotaFillExcluded(lesson.course.studentGroup.name))
                .groupBy(Lesson::getStudentGroup,
                        lesson -> lesson.timeslot.getDate(),
                        count())
                .filter((group, date, totalHours) -> {
                    var row = SchoolSchedule.getExpectedHoursRow(group.id, date);
                    if (row == null) {
                        return false;
                    }
                    int quota = row.aulaHours + row.labHours;
                    if (quota <= 0) {
                        return false;
                    }
                    return totalHours < quota - quotaFillTolerance(group.name);
                })
                .penalize(quasiTierEnabled() ? ScoreCompat.ofQuasi(1) : ScoreCompat.ofHard(1),
                        (group, date, totalHours) -> {
                            var row = SchoolSchedule.getExpectedHoursRow(group.id, date);
                            int quota = row == null ? 0 : row.aulaHours + row.labHours;
                            return Math.max(0, (quota - quotaFillTolerance(group.name)) - totalHours);
                        })
                .asConstraint("H32 - Day fill meets the declared quota");
    }

    // ── H34 - Empty declared day (TASK-557, 26/08: the companion hard of H32) ──
    //
    // The blind spot documented in H32's scope note above: its groupBy only sees
    // days WITH lessons, so a declared day (quota aula+lab > 0) carrying ZERO
    // non-Stage lessons is invisible to the hard tier and pays M20.2 medium
    // only - the LS treats it as trade currency and the "empty declared day"
    // fingerprint never impacts 0hard (measured 26/08 by Fedora: delivered run
    // 12 cells ~58h April-June vs golden 2 cells, both G09 year-end). H34
    // is the ifNotExists companion: FLAT 1 per empty declared cell - presence
    // is the defect; depth stays H32's business the moment one lesson lands.
    //
    // Scope identical to H32 (1/G08 pool-cap and G14 excluded via
    // isQuotaFillExcluded, dates that carry a Timeslot, non-Stage lessons).
    // STRICT-FIRST (maintainer's 26/08 amendment): NO starting tolerance for the
    // golden's own 2 year-end cells (04/06 quota 5, 07/06 quota 3) - they may
    // be the human planner's year-end surrender, not the school's preference;
    // solver target is 0 cells (better-than-golden). A tolerance is admissible
    // only as a PROVEN impossibility, declared quasi-hard in the task/VERDICT,
    // never silently.
    //
    // Shape: the declared (group, date) cells come from the Timeslot x
    // StudentGroup problem-fact join (deduped with distinct()), NOT from a
    // build-time flatten over the quota statics: every static read below is
    // per match, because the ConstraintVerifier reuses streams across
    // sessions and build-time reads freeze whatever the first session saw
    // (the M22 flag lesson of this same day, documented in m22MinDays).
    Constraint emptyDeclaredDay(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Timeslot.class)
                .filter(ts -> ts.getDate() != null)
                .join(StudentGroup.class)
                // collapse the timeslot axis: one CELL per (group, date), not per slot
                // (8 slots per date would otherwise charge the empty day 8 times)
                .map((ts, group) -> new DeclaredCell(group, ts.getDate()))
                .distinct()
                // The flag gate is PER CELL (tuple time), not on registration: the
                // app-context score director factory is built ONCE at boot and cached,
                // so a registration gate would leave H34 absent from every later
                // analyzeScore/solve session no matter when the -D arrives (the M22
                // lesson of this same day, see m22MinDays). The left side here is
                // static problem facts - the gate runs once per cell per session.
                // RATIFIED 26/08: null/absent -> ON, only "false" disarms.
                .filter(cell -> emptyDayHardEnabled())
                .filter(cell -> cell.group() != null && cell.group().id != null
                        && !isQuotaFillExcluded(cell.group().name))
                .filter(cell -> {
                    var row = SchoolSchedule.getExpectedHoursRow(cell.group().id, cell.date());
                    return row != null && row.aulaHours + row.labHours > 0;
                })
                .ifNotExists(
                        constraintFactory.forEachIncludingUnassigned(Lesson.class)
                                .filter(l -> l.timeslot != null)
                                .filter(l -> l.course != null && l.course.studentGroup != null)
                                // non-Stage hours only (pseudo teacher = Stage bucket)
                                .filter(l -> {
                                    var t = l.getTeacher();
                                    return t == null || !t.pseudo;
                                }),
                        Joiners.equal(DeclaredCell::date, l -> l.timeslot.getDate()),
                        Joiners.equal(cell -> cell.group().id, l -> l.course.studentGroup.id))
                .penalize(quasiTierEnabled() ? ScoreCompat.ofQuasi(1) : ScoreCompat.ONE_HARD)
                .asConstraint("H34 - Empty declared day");
    }

    /** H34's deduplicated declared cell: one (group, date) with a quota row in scope. */
    private record DeclaredCell(StudentGroup group, java.time.LocalDate date) {
    }

    // ── H33 - Day fill meets the per-group hard floor (TASK-536, spec 20/08) ──
    //
    // maintainer's inspection 20/08: G04 carried 11 days below the golden's own
    // 4h minimum, two of them at 2h - days the eye rejects on sight and no KPI
    // flagged. The floor is a TWO-LEVEL spec (corrected same day): the math
    // floor(poolNonStage / sessionDaysInGrid) is the documented ASPIRATION
    // (5h/day for 12 groups, 4h G04, 3h G14 - a referente question, NOT a
    // constraint); the HARD floor is min(4, mathFloor, goldenExistenceMin):
    // 4h as proven existence, capped by the golden's own per-group minimum so
    // the constraint can never fire on the golden (G08 and G09 drop to
    // 2h, G14 to 3h). Derived in service.DayFloorDeriver (no group-name
    // hardcoding: it is a map), published pre-solve as
    // SchoolSchedule.setDayFloorsStatic, and read here per (group, date).
    //
    // Scope: in-session days only (DailyActivityQuota aula+lab > 0 - pure-stage
    // and closed days are other buckets), non-Stage hours only, pins counted
    // (a pinned day is still filled hours, same as H32). Same structural
    // subtlety as H32: the groupBy only sees dates WITH lessons, so a totally
    // empty expected day is M20.2's complement; H33 catches the PARTIAL
    // underfill (2h/3h days), which is the defect class maintainer found.
    // Opt-out flag, standard form: planner.constraints.day-floor.enabled.
    Constraint dayFillMeetsHardFloor(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> lesson.timeslot != null)
                .filter(lesson -> lesson.course != null && lesson.course.studentGroup != null)
                // non-Stage hours only (pseudo teacher = Stage bucket)
                .filter(lesson -> {
                    var t = lesson.getTeacher();
                    return t == null || !t.pseudo;
                })
                .groupBy(Lesson::getStudentGroup,
                        lesson -> lesson.timeslot.getDate(),
                        count())
                .filter((group, date, totalHours) -> {
                    Integer floor = SchoolSchedule.getDayFloor(group.id);
                    if (floor == null || floor <= 0) {
                        return false;
                    }
                    var row = SchoolSchedule.getExpectedHoursRow(group.id, date);
                    if (row == null || row.aulaHours + row.labHours <= 0) {
                        return false; // no-session / pure-stage day: other buckets own it
                    }
                    return totalHours < floor;
                })
                .penalize(ScoreCompat.ofHard(1),
                        (group, date, totalHours) ->
                                SchoolSchedule.getDayFloor(group.id) - totalHours)
                .asConstraint("H33 - Day fill meets the per-group hard floor");
    }

    /** TASK-532 scope exclusions: the FC pool-cap pairs (forma-b owns their
     *  per-day targets) and G14 (import-pinned special programme). Public for
     *  the TASK-560 Kempe bench test (bootstrap package). */
    public static boolean isQuotaFillExcluded(String groupName) {
        return "G04".equals(groupName) || "G08".equals(groupName) || "G14".equals(groupName);
    }

    /** TASK-532's one declared tolerance (G09's single golden gap, 1h), the
     *  second knob of the H32 scope beside {@link #isQuotaFillExcluded}. Shared
     *  accessor (TASK-612 /simplify): the literal was inlined here twice and in
     *  every deterministic consumer (EDF's H32 targets), and the tolerance is a
     *  pending-school-answer item - a policy change must land in ONE place or a
     *  pass silently closes a cell the constraint still charges. */
    public static int quotaFillTolerance(String groupName) {
        return "G09".equals(groupName) ? 1 : 0;
    }

    /** M18's mapped tuple: a (group, date) day's start offset vs its first calendar-open slot. */
    private record DayStartOffset(StudentGroup group, LocalDate date, int offsetSlots) {
    }

    /**
     * M18's own day-start offset for a (group, date) whose earliest occupied slot is
     * {@code minSlotIndex}: {@code max(0, minSlotIndex - the group's first calendar-open slot)}.
     * A closed-day calendar sentinel ({@code minOpenSlot < 0}, no open slot at all) yields 0 -
     * a lesson should not exist on such a day in the first place (H20 handles that), so M18 has
     * nothing sensible to anchor on and must not penalize.
     *
     * <p>Package-private and used by the constraint's own map step above, so the deterministic
     * repairs can ask M18's real question instead of re-deriving this arithmetic ({@link
     * SurplusRebalance}'s anchor guard and {@link HoleRepair}'s de-anchoring log) - the same
     * anti-drift rule {@link #m20DayCost} already applies for the day-fill curve.
     */
    static int m18DayStartOffset(StudentGroup group, LocalDate date, int minSlotIndex) {
        int expectedFirstSlot = SchoolSchedule.getGroupCalendarWindow(group.id, date.getDayOfWeek(), date)[1];
        return expectedFirstSlot < 0 ? 0 : Math.max(0, minSlotIndex - expectedFirstSlot);
    }

    // M15 - Same-day same-subject lessons should be consecutive (task-017; was H15)
    // If a student group has 2+ hours of the same subject on the same day,
    // those hours should be adjacent (no gaps).
    //
    // FIXED: The original implementation penalized ALL non-adjacent pairs, which
    // incorrectly penalized consecutive sequences (e.g., 6 lessons at 08:00-14:00
    // would have 10 "violations" for non-adjacent pairs like 08:00 vs 10:00).
    // The correct approach is to penalize GAPS between consecutive lessons.
    //
    // TASK-243 - Replaced toList() with toConsecutiveSequences() for incremental
    // score calculation compatibility. Breaks between consecutive sequences are
    // penalized instead of re-sorting all lessons.
    //
    // TASK-349 - PINNED-EXEMPT (both sides). The golden 2026/27 solution splits a
    // same-day same-subject pair 98 times - the school demonstrably tolerates rare
    // splits in its own historical record. A break is exempt only when BOTH the
    // previous and next lesson are pinned: that is exactly the golden/historical case
    // (100% pinned import), so the school's 98 splits stay legal. A break touching even
    // one unpinned lesson is still penalized. This is a break-level filter (not a
    // per-entity `!lesson.pinned` filter like M20/M28) because it evaluates pairs, not
    // individual lessons - excluding pinned lessons from the grouping entirely would
    // blind the constraint to a solver-placed lesson creating a new split next to a
    // pinned one.
    //
    // TASK-392 - DEMOTED H15 (hard) -> M15 (medium). The golden-practice evidence that
    // motivated the pinned-exemption (98 splits, 19 of them Lingua Italiana) proves the
    // school's rule is STRICTER than the school's own practice: a hard tier penalized
    // the solver for making ONE human-like split (the R1 benchmark residual was exactly
    // a Lingua Italiana 2h+1h split, a case the golden does 19x). Per the primary
    // directive (align the model to authoritative/golden practice) the "must be
    // consecutive" rule is logically a PREFERENCE, not a feasibility rule - medium. The
    // pinned-exemption and the per-gap-minute weight are unchanged; only the tier moves,
    // so the golden guard (100% pinned) still measures 0. See CLAUDE.md "H15->M15
    // demotion (TASK-392)".
    //
    // RAPPORTO CON M20 CAMBIATO dalla progressione (2026-07-26): il marginale di M20
    // sale fino a 4500/ora sui giorni profondi (m20DeficitCost), contro i ~1200 di M15
    // per uno split da 1h e i 500 max di M32. La gerarchia "M15 compete con M20" su cui
    // fu scelto il moltiplicatore 20 vale ancora sulla PRIMA ora di deficit (1000), non
    // sulle successive. WATCH-ITEM del gate: match di M15 e ricorrenza. Ritarare SOLO
    // su evidenza A/B, mai su questa osservazione da sola.
    /**
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: il golden stesso spezza 98 volte una coppia stesso-giorno stessa-materia, quindi la regola e' una preferenza forte non una feasibility. Demoted da hard (H15) TASK-392; esenzione break-level calibrata sul golden.
     */
    Constraint sameDaySameSubjectShouldBeConsecutive(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> !lesson.isLab())
                .filter(lesson -> lesson.timeslot != null)
                .groupBy(
                        Lesson::getStudentGroup,
                        Lesson::getSubject,
                        lesson -> lesson.timeslot.getDate(),
                        toConsecutiveSequences(l -> TimeslotSequence.slotIndexOf(l.timeslot)))
                .flattenLast(SequenceChain::getBreaks)
                // TASK-419: "pinned" here means AUTHORITATIVE pin, so a break between two
                // merely PROMOTED lessons stays penalized - it is still a fixable gap. See
                // BaseConstraintProvider#isAuthoritativePin's javadoc for the rationale.
                .filter((group, subject, date, break_) ->
                        !(isAuthoritativePin(break_.getPreviousSequenceEnd())
                                && isAuthoritativePin(break_.getNextSequenceStart())))
                // Weight: ofMedium(20) x gap-minutes = ~1200 medium for a typical 1h-gap
                // split, deliberately in the M20/M22 "logical hard medium" family (both
                // 1000/day) rather than the weak-preference group (M21/M24 = 1). M15 must
                // COMPETE with the mediums the solver trades it against: at ONE_MEDIUM a
                // split cost 60 while a M20 day-fill gain is 1000, so the solver would
                // scatter same-day repeats to fill days - exactly the task-349 scatter
                // fear. Medium (not hard) still never blocks 0-hard regardless of weight;
                // the weight only orders medium-tier tradeoffs. The exact multiplier (20)
                // is a benchmark-tuning knob against the M15-vs-M20 tension (too high
                // suppresses full-day fill, too low permits scatter) - TASK-392 validation.
                .penalize(ScoreCompat.ofMedium(20),
                        (group, subject, date, break_) -> calculateBreakGapMinutes(break_))
                .asConstraint("M15 - Same-day same-subject should be consecutive");
    }

    // S16 - Consecutive same-subject should use same teacher (task-016, TASK-183;
    // was H16a until TASK-377, which both unblinded and demoted it).
    // Unblinded: forEachUniquePair uses forEach() semantics (fully initialized
    // entities only), so with rooms assigned only in phase 4 the constraint was
    // inert for every timeslot phase AND for the room-less golden guard fixture —
    // TASK-347's "0 matches on golden" was vacuous.
    // Demoted: the unblinded golden shows 15 deliberate violations (RA/Recuperi
    // hours handed between teachers back-to-back; Scienze split T09→T25 on
    // G12 2026-11-11), and H17's forced contiguity makes a hard tier
    // structurally unsatisfiable for multi-teacher RA days. Soft = mild
    // preference for teacher continuity, matching actual school practice.
    // Single lambda instance for the same BAVET node-sharing reason as the
    // predicates in BaseConstraintProvider.
    private static final Predicate<Lesson> NON_LAB = l -> !l.isLab();

    /** S16's source: timeslotted non-lab lessons (labs never enter the join index). */
    private UniConstraintStream<Lesson> nonLabTimeslottedLessons(ConstraintFactory constraintFactory) {
        return timeslottedLessons(constraintFactory).filter(NON_LAB);
    }

    /**
     * Shared join shape for "adjacent same-(StudentGroup, Subject, Date)
     * lesson pair" - factored out (TASK-381 code review) so S16 ({@link
     * #consecutiveSameSubjectSameTeacher}) and S17 ({@link
     * #rewardSameSubjectBlockPair}) can never drift on what "adjacent" means,
     * since both their javadocs assert exactly that invariant. {@code
     * sourceBuilder} is invoked twice (once per join side) - build-time only,
     * and the static singleton predicates keep BAVET node sharing intact.
     * Adjacency routes through the same null-safe {@link #isAdjacent(Lesson,
     * Lesson)} wrapper S7/S8 use (an unassigned lesson has a null timeslot
     * while Bavet fires the joiner), so all four constraints share
     * ONE adjacency definition.
     */
    private BiConstraintStream<Lesson, Lesson> adjacentSameSubjectPairs(
            ConstraintFactory constraintFactory, Function<ConstraintFactory, UniConstraintStream<Lesson>> sourceBuilder) {
        return sourceBuilder.apply(constraintFactory)
                .join(sourceBuilder.apply(constraintFactory),
                        Joiners.equal(Lesson::getStudentGroup),
                        Joiners.equal(Lesson::getSubject),
                        Joiners.equal(LESSON_DATE),
                        Joiners.lessThan(l -> l.id))
                .filter(SchoolScheduleConstraintProvider::isAdjacent);
    }

    /**
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: il golden passa deliberatamente le ore RA/Recuperi tra docenti back-to-back (15 handoff), quindi il cambio e' ammesso, solo scoraggiato. Demoted da hard (H16a) TASK-377.
     */
    Constraint consecutiveSameSubjectSameTeacher(ConstraintFactory constraintFactory) {
        return adjacentSameSubjectPairs(constraintFactory, this::nonLabTimeslottedLessons)
                .filter((a, b) -> !java.util.Objects.equals(a.getTeacher(), b.getTeacher()))
                .penalize(ScoreCompat.ONE_SOFT)
                .asConstraint("S16 - Consecutive same-subject same teacher");
    }


    // M37 - Consecutive same-subject same-teacher should keep the room (was H16b hard;
    // demoted TASK-446/447 notte, vedi Design Decisions). Stesso corpo e stessa esenzione
    // lab del vecchio H16b: forEachUniquePair su (gruppo, materia, data), entrambe non-lab,
    // entrambe room!=null, timeslot adiacenti, stesso docente, stanza diversa.
    // Razionale del demotion: il golden NON specifica le stanze, quindi non difende il tier
    // hard su questo vincolo. Post-TASK-447 la room-null trap e' chiusa da H29 HARD (room-null
    // costa -1 hard): assegnare QUALUNQUE stanza valida rimuove -1 hard, che batte qualsiasi
    // costo medium di M37 sul cambio, quindi la LS room assegna sempre; H2/H-M2 hard vincolano
    // QUALE stanza. M37 medium (default 100) resta quindi puro obiettivo di raffinamento - "tra le
    // stanze valide, non cambiarla a meta' blocco" - e il suo peso e' il tuning target di FASE B.
    // A differenza di M34/M35 (static final, letti a class-init), M37 legge il knob INLINE nel
    // metodo (readCadenceKnobProperty, per-build a defineConstraints): e' il peso che si sta
    // attivamente tunando, e la lettura per-build lascia che l'harness RoomPhaseSnapshotTest
    // (che non inoltra -D) lo override-zi via System.setProperty tra un run e l'altro. Knob:
    // -Dplanner.m37.weight=N (default 100). Nessun cambio di comportamento a default.
    /**
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: il golden non registra le aule, quindi la regola non difende il tier hard, ma "ogni classe ha un'aula" (H29) e' fisicamente irrinunciabile. Demoted da hard (H16b) TASK-446/447; il peso non e' la leva (sweep piatto a ~106 cambi d'aula forzati dalla disponibilita').
     */
    Constraint consecutiveSameSubjectSameTeacherSameRoom(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getStudentGroup),
                        Joiners.equal(Lesson::getSubject),
                        Joiners.equal(l -> l.timeslot.getDate()))
                .filter((a, b) -> !a.isLab() && !b.isLab())
                .filter((a, b) -> a.room != null && b.room != null)
                .filter((a, b) -> a.timeslot != null && b.timeslot != null
                        && TimeslotSequence.isAdjacent(a.timeslot, b.timeslot))
                .filter((a, b) -> java.util.Objects.equals(a.getTeacher(), b.getTeacher()))
                .filter((a, b) -> !java.util.Objects.equals(a.room, b.room))
                .penalize(ScoreCompat.ofMedium(readCadenceKnobProperty("planner.m37.weight", 100)))
                .asConstraint("M37 - Consecutive same-subject same-teacher should keep the room");
    }

    // H28 - No teacher return within (group, subject, day) (TASK-448). Golden-first: il
    // golden non ha MAI ritorni (15/15 blocchi same-subject multi-docente sono A->B, zero
    // A..B..A; invariante verificata da Fedora) e il run e' gia' a 0, quindi e' un GUARD
    // hard a delta zero che cristallizza l'invariante. Penalizza, dentro (gruppo, materia,
    // data), la coppia di lezioni STESSO docente tra le quali esiste una lezione di docente
    // DIVERSO in slot strettamente intermedio (A-B-A). Complementare a S16 (premio
    // same-teacher adiacente) e M15 (contiguita' same-subject). timeslottedLessons (pattern
    // TASK-377: vede anche le room-null delle fasi timeslot), self-join per coppie ordinate
    // stesso-docente, ifExists una c strettamente tra a e b con docente diverso (TriJoiner
    // filtering). L'invariante vale sull'orario intero: niente esenzione pinned/pseudo
    // (il golden, tutto pinned, la rispetta).
    /**
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: il golden non presenta mai sequenze A..B..A nello stesso (gruppo, materia, giorno). Cristallizza l'invariante a delta zero (il run e' gia' a 0); introdotto TASK-448.
     */
    Constraint teacherNoReturnWithinGroupSubjectDay(ConstraintFactory constraintFactory) {
        return timeslottedLessons(constraintFactory)
                .join(timeslottedLessons(constraintFactory),
                        Joiners.equal(Lesson::getStudentGroup),
                        Joiners.equal(Lesson::getSubject),
                        Joiners.equal(LESSON_DATE),
                        Joiners.filtering((a, b) -> h28ReturnPair(a, b)))
                .ifExists(timeslottedLessons(constraintFactory),
                        Joiners.equal((a, b) -> a.getStudentGroup(), Lesson::getStudentGroup),
                        Joiners.equal((a, b) -> a.getSubject(), Lesson::getSubject),
                        Joiners.equal((a, b) -> a.timeslot.getDate(), l -> l.timeslot.getDate()),
                        Joiners.filtering((a, b, c) -> h28Between(a, c, b)))
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H28 - No teacher return within group subject day");
    }

    /**
     * H28 pair gate: an ordered same-teacher pair within the (group, subject, date) cell.
     * Package-visible and shared with {@link H28RotationFinisher}'s detection (the
     * {@code isQuotaFillExcluded} precedent): the stream and the deterministic pass must
     * read the same A..B..A semantics, or a future constraint change silently strands
     * the finisher's mirror.
     */
    static boolean h28ReturnPair(Lesson a, Lesson b) {
        return !a.id.equals(b.id)
                && TimeslotSequence.slotIndexOf(a.timeslot) < TimeslotSequence.slotIndexOf(b.timeslot)
                && java.util.Objects.equals(a.getTeacher(), b.getTeacher());
    }

    /**
     * H28 between gate: {@code c} sits strictly between the pair in slot order and is of
     * a different teacher. Shared with {@link H28RotationFinisher} like
     * {@link #h28ReturnPair(Lesson, Lesson)}.
     */
    static boolean h28Between(Lesson a, Lesson c, Lesson b) {
        return TimeslotSequence.slotIndexOf(c.timeslot) > TimeslotSequence.slotIndexOf(a.timeslot)
                && TimeslotSequence.slotIndexOf(c.timeslot) < TimeslotSequence.slotIndexOf(b.timeslot)
                && !java.util.Objects.equals(c.getTeacher(), a.getTeacher());
    }

    // H29 - Assigned lesson must have a room (was M36 medium 1000, before that the bocciato
    // hard H27 at parity with H0). TASK-447: PROMOTED to HARD weight 1. room is
    // @PlanningVariable(allowsUnassigned = true): null is a legal terminal value and every
    // room constraint (H2/H-M2 hard, M37/M3 medium) is silent on null, so assigning a room
    // can only ever cost and the solver rationally leaves it null (the room-null trap that
    // left 291 non-lab hours unassigned in the gate-l5 run). H29 hard weight 1 makes null
    // cost -1 hard; paired with H0 weight 2 (unassigned) the arbitrage that emptied the
    // schedule when room-required was hard at PARITY is gone: de-assigning (-2) is strictly
    // worse than leaving room-null (-1), so a room is always worth assigning when one exists.
    // H2/H-M2 hard then constrain WHICH room; M37 (medium) discourages changing it mid-block.
    // If no legal room exists for a slot the residual stays visible as -1 hard on that lesson
    // (honest, not fake-clean) and now also counts toward getFeasibility's hard==0 bar (see
    // ScheduleResource.getFeasibility and the VERDICT). Exempt: pinned (authoritative imports
    // without a room, like the baseline) and pseudo-teacher (Stage). forEachIncludingUnassigned
    // + explicit filter (TASK-377 pattern). Classed as a ROOM-TIER hard constraint (excluded
    // from timeslotTierHardScore via ROOM_TIER_HARD_FAMILIES) so the staged cycle still reads
    // "timeslot-tier hard == boxed" pre-B-ROOM.
    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; ogni lezione collocata deve avere un'aula (un orario senza aula non e' usabile). Promosso a hard (era M36) col TASK-447 per chiudere l'arbitraggio empty-the-schedule.
     */
    Constraint assignedLessonMustHaveRoom(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(Lesson::isH29Roomless)
                .penalize(ScoreCompat.ofHard(1))
                .asConstraint("H29 - Assigned lesson must have a room");
    }

    // S17 weight: between S13's 2 and S7's 10 by design intent, unverified
    // start-low-tune-later status (same as WEEKLY_STABILITY_WEIGHT) - see
    // CLAUDE.md's "Shape recalibration (L3, TASK-381)" section.
    private static final int SAME_SUBJECT_BLOCK_REWARD = 5;

    /**
     * S17 - Reward same-subject block pair (TASK-381 "L3 - shape constraints
     * golden-first").
     *
     * <p>Rewards every ADJACENT (StudentGroup, Subject, Date) pair of
     * lessons via the shared {@link #adjacentSameSubjectPairs} join (same
     * keys and adjacency test {@link #consecutiveSameSubjectSameTeacher}
     * (S16) uses, and the exact {@link TimeslotSequence#isAdjacent} that
     * S7's own {@link #isAdjacent(Lesson, Lesson)} wrapper delegates to) -
     * S16, S7 and S17 structurally cannot disagree about what "adjacent"
     * means, since S16 and S17 both route through the one join definition.
     * Deliberately does NOT require the same teacher: {@link
     * #consecutiveSameSubjectSameTeacher} (S16) already penalizes a teacher
     * handoff softly, so requiring same-teacher here would double-count that
     * same signal from the other direction.
     *
     * <p>Code-local invariants (the design narrative, weight rationale and
     * golden measurement are owned by CLAUDE.md's "Shape recalibration (L3,
     * TASK-381)" section, not repeated here):
     * LABS INCLUDED, unlike S16/M21 - deliberate: for any lab lesson that
     * reaches the solver unpinned, block cohesion is exactly what the school
     * wants (the motivating 1,299-block golden figure counts lab AND non-lab
     * blocks), and M21 exempts labs rather than capping them, so there is no
     * ceiling to fight. NO same-teacher requirement - S16 already penalizes
     * the teacher handoff softly; requiring it here would double-count.
     * PSEUDO-TEACHER EXCLUDED on both sides (via {@link
     * #realTeacherTimeslottedLessons}, M21/S12/S13 precedent) - an 8h Stage
     * day would otherwise earn 7 spurious rewards.
     *
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: la scuola costruisce blocchi same-day same-subject (premia la coppia adiacente, lab inclusi a differenza di M21/S16); introdotto TASK-381 (L3).
     */
    Constraint rewardSameSubjectBlockPair(ConstraintFactory constraintFactory) {
        return adjacentSameSubjectPairs(constraintFactory, this::realTeacherTimeslottedLessons)
                .reward(ScoreCompat.ofSoft(SAME_SUBJECT_BLOCK_REWARD))
                .asConstraint("S17 - Reward same-subject block pair");
    }

    // ─── M29 - Professionalizzanti At Day Edges (was H22; TASK-370, demoted TASK-379) ───

    /**
     * Group-generic rule table for {@link #professionalizzantiAtDayEdges}: on
     * these (group, weekday) pairs, professionalizzanti lessons are restricted
     * to the day's edges (see the constraint's own javadoc). Currently a
     * single row (G12, Wed+Thu) - Friday is deliberately absent, since the
     * docx leaves it unrestricted ("professionalizzanti solo il venerdì" reads
     * as "Friday is always fine", vincoli-in-parole.md). A second group/
     * weekday pair with the SAME shape (max-2-slot edge run) can be added as
     * a second map entry without touching {@link #isLegalProfEdgeRun},
     * mirroring H30/M27's single-row-today-but-generic precedent (TASK-369);
     * a group needing a different run-length/edge shape would still need
     * {@link #isLegalProfEdgeRun} itself edited, since the max-run-length
     * constant isn't part of this table.
     */
    private static final Map<String, Set<DayOfWeek>> M29_RESTRICTED_DAYS_BY_GROUP = Map.of(
            "G12", Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY));

    /**
     * M29 - Professionalizzanti at day edges (was hard H22 until TASK-379).
     *
     * <p>DEMOTED to medium by the golden-first directive: the golden's own 4
     * FD Wed/Thu professionalizzanti placements do NOT follow the docx's
     * edge-of-day rule (slot sets {0,4} and {2,3} - see the TASK-370 golden
     * measurement note below), so a hard tier was stricter than the practice
     * the school itself accepted. ONE_MEDIUM per violating day, matching the
     * M24/M25 preference scale.</p>
     *
     * G12 ("professionalizzanti solo il venerdì, oppure giovedì/mercoledì
     * alla prima o all'ultima ora, oppure alle prime due o alle ultime due
     * ore. Se la 2ª ora del giovedì è professionalizzante lo deve essere
     * anche la 1ª; se la 5ª lo è, anche la 6ª. Stesso schema per il
     * mercoledì.", vincoli-in-parole.md, TASK-370): on a group's restricted
     * weekday ({@link #M29_RESTRICTED_DAYS_BY_GROUP}), that day's NON-LAB
     * professionalizzanti lessons must form a single contiguous run of at
     * most 2 slots that touches either the FIRST or the LAST slot the
     * group's calendar has open THAT DAY - {@link
     * SchoolSchedule#getGroupCalendarWindow}, the same "effective open
     * range" anchor M18/H20 already use (TASK-350), not a literal hardcoded
     * slot 0/5. G12's own calendar (DataGenerator.GROUP_CALENDAR_PATTERNS)
     * is NOT identical on both restricted days: Wednesday is open 0-4 (5
     * slots, "the last hour" is slot 4), Thursday is open 0-7 (8 slots,
     * including the 2 slots {@link TimeslotSequence#AFTERNOON_START_SLOT_INDEX}
     * reserves for stage via H21) - so Thursday's LAST-edge branch is only
     * reachable by a PINNED lesson in practice, since H21 independently blocks
     * any unpinned lesson at slot &gt;= 6 regardless of what H22 allows. This
     * is expected multi-constraint composition (H21 is the stricter, universal
     * afternoon policy), not a bug in either constraint.
     *
     * <p>LAB EXCLUSION (same {@code !lesson.isLab()} filter M15
     * (sameDaySameSubjectShouldBeConsecutive) already applies): G12 has
     * professionalizzanti subjects that are ALSO lab-tagged (Servizio tecnico
     * e stilistico, Servizio stilistico - DataGenerator.getRoomTags's
     * "acconciatura" prefixes), legitimately delivered as long block sessions
     * (the golden data has real 8-slot Thursday blocks of these). Without this
     * exclusion H22 would forbid the solver from ever recreating such a block
     * on Wed/Thu - the docx's edge-of-day rule is about classroom-style
     * professionalizzanti theory hours, not block-scheduled lab practicals.</p>
     *
     * <p>DAY-LEVEL PINNED EXEMPTION - a third shape alongside M15/H17's
     * break-level (both sides of one break pinned) and M20/H21's per-entity
     * (!lesson.pinned) exemptions. This constraint judges the whole SET of a
     * (group, date)'s professionalizzanti placements together - a 3-lesson
     * illegal run isn't "one lesson's fault" more than the others - so the
     * natural exemption unit is the whole day's set, not one lesson or one
     * pair. A day is exempt only when EVERY professionalizzanti lesson
     * contributing to that day's shape is pinned; a day with even one
     * unpinned lesson is still evaluated in full, so the solver can never
     * CREATE an illegal shape of its own. See CLAUDE.md "Professionalizzanti
     * at day edges (H22, task-370)" for the full rationale and the golden
     * measurement (structurally 0/0, same reason as M20/H21).</p>
     *
     * <p><b>Origine.</b> "Professionalizzanti solo il giovedì e il venerdì" (G12; workbook vincoli Spalmone, tab "Vincoli Classi", riga 14). La forma "al bordo della giornata, blocco fino a 2h" e' modellata sulla pratica; demoted da hard (H22) TASK-379 perche' il golden non la segue strettamente.
     */
    Constraint professionalizzantiAtDayEdges(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> !l.isLab()) // lab professionalizzanti (e.g. G12's Servizio tecnico e
                                          // stilistico) are legitimately taught in long block sessions -
                                          // same exemption M15 (sameDaySameSubjectShouldBeConsecutive)
                                          // already applies for the same reason
                .filter(l -> l.timeslot != null && l.course != null && l.course.professionalizzanti
                        && l.getStudentGroup() != null && l.getStudentGroup().name != null)
                .filter(l -> {
                    Set<DayOfWeek> restrictedDays = M29_RESTRICTED_DAYS_BY_GROUP.get(l.getStudentGroup().name);
                    return restrictedDays != null && restrictedDays.contains(l.timeslot.getDate().getDayOfWeek());
                })
                .groupBy(Lesson::getStudentGroup, l -> l.timeslot.getDate(), toList())
                .filter((group, date, lessons) -> dayHasUnpinnedLesson(lessons))
                .filter((group, date, lessons) -> !isLegalProfEdgeRun(group, date, lessons))
                .penalize(ScoreCompat.ONE_MEDIUM)
                .asConstraint("M29 - Professionalizzanti at day edges");
    }

    /**
     * True if {@code lessons} (all professionalizzanti, all on {@code date}
     * for {@code group}) form a legal edge run: contiguous, at most 2 slots,
     * touching the group's calendar-open first or last slot that day.
     */
    private static boolean isLegalProfEdgeRun(StudentGroup group, LocalDate date, List<Lesson> lessons) {
        List<Integer> slotIndexes = sortedDistinctSlotIndexes(lessons);
        if (slotIndexes.isEmpty()) return true;
        int min = slotIndexes.get(0);
        int max = slotIndexes.get(slotIndexes.size() - 1);
        int span = max - min + 1;
        if (span != slotIndexes.size() || span > 2) return false; // gap inside the run, or run longer than 2
        int[] window = SchoolSchedule.getGroupCalendarWindow(group.id, date.getDayOfWeek(), date);
        int minOpenSlot = window[1];
        int maxOpenSlot = window[2];
        if (minOpenSlot < 0) return false; // closed-day sentinel: no open slot to anchor on
        return min == minOpenSlot || max == maxOpenSlot;
    }

    // M23 - Block lesson pair togetherness (Disegno edile, blockSize=2)
    // Non-lab block courses (labBlockSize>1, isLab=false) generate paired lessons
    // via LessonGenerator. These pairs should be on the same date in consecutive
    // slots. Penalizes pairs that are split across different days.
    //
    // Uses sessionIndex (set by LessonGenerator) to identify actual block
    // partners — two lessons share a sessionIndex iff they belong to the
    // same block session.
    //
    // Medium (not hard) because:
    // - No composite move selector exists to move pairs atomically
    // - Hard would cause move rejection storms in LS
    // - Medium weight of 5000 is strong enough to keep pairs together
    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; un blocco pluri-orario modellato come righe separate deve stare nello stesso giorno (integrita' del blocco, es. Disegno edile 2h). Introdotto TASK-183.
     */
    Constraint blockLessonPairTogetherness(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> lesson.sessionIndex >= 0 && !lesson.isLab())
                .filter(lesson -> lesson.timeslot != null)
                .join(Lesson.class,
                        Joiners.equal(l -> l.course),
                        Joiners.equal(l -> l.sessionIndex))
                .filter((a, b) -> a.id < b.id)
                .filter((a, b) -> !a.timeslot.getDate().equals(b.timeslot.getDate()))
                .penalize(ScoreCompat.ofMedium(5000))
                .asConstraint("M23 - Block lesson pair togetherness");
    }

    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; un orario non presenta buchi interni (compattezza verticale). L'esenzione break-level (gap scusato solo tra due lezioni pinnate) e' calibrata sul golden; introdotto TASK-227 (promosso da M13).
     */
    Constraint verticallyCompact(ConstraintFactory constraintFactory) {
        // TASK-225 - Use forEachIncludingUnassigned to bypass isInitializedPredicate
        // which was filtering out all Lesson entities. Manual null filter applied after.
        //
        // TASK-227 - Upgraded to HARD tier (2026-03-15) to enforce compaction.
        // Spalmone human schedule analysis shows 0 medium violations - human planners
        // naturally create compact schedules without gaps. Making M13 hard forces
        // the solver to match human behavior: no gaps in student daily schedules.
        //
        // TASK-350 - PINNED-EXEMPT (both sides), same pattern as H15 (TASK-349). The
        // golden 2026/27 solution has exactly 2 documented afternoon-contiguity
        // exceptions (analysis.md section 3, both fully-pinned historical days: T01
        // /G10/2026-12-17's 13:00 hole, G08/2027-04-01's stage+RA 12-14 hole) - the
        // school's own record crosses lunch with a genuine gap on rare occasions. A break
        // is exempt only when BOTH the previous and next lesson are pinned; a break
        // touching even one unpinned lesson still fires, so the solver can never CREATE a
        // vertical gap of its own. Break-level filter (not a per-entity `!lesson.pinned`
        // filter like M20/H21) for the same reason as H15: excluding pinned lessons from
        // the grouping entirely would blind the constraint to a solver-placed lesson
        // creating a new gap next to a pinned one.
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)  // Exclude unassigned lessons
                .groupBy(Lesson::getStudentGroup, l -> l.timeslot.getDate(),
                        toConsecutiveSequences(l -> TimeslotSequence.slotIndexOf(l.timeslot)))
                .flattenLast(SequenceChain::getBreaks)
                // TASK-419: AUTHORITATIVE pins only, same reasoning as M15 above
                // (BaseConstraintProvider#isAuthoritativePin) - a vertical hole between
                // two promoted lessons is a real hole the tail can still fill.
                .filter((group, date, break_) ->
                        !(isAuthoritativePin(break_.getPreviousSequenceEnd())
                                && isAuthoritativePin(break_.getNextSequenceStart())))
                .penalize(ScoreCompat.ONE_HARD,
                        (group, date, break_) -> countBreakSlots(break_))
                .asConstraint("H17 - Vertically compact");
    }

    // ─── Soft Constraints ───────────────────────────────────────────

    // TASK-243 - Replaced toList() with toConsecutiveSequences() for incremental
    // score calculation compatibility. Teacher gap penalties are now calculated
    // from breaks between consecutive sequences, avoiding re-sorting all lessons.
    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; minimizza i buchi di attesa del docente tra lezioni (qualita'), introdotto fin dalle origini.
     */
    Constraint minimizeTeacherGaps(ConstraintFactory constraintFactory) {
        // TASK-225 - Use forEachIncludingUnassigned to bypass isInitializedPredicate
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)  // Exclude unassigned lessons
                // TASK-346: pseudo teachers (e.g. "Stage") have no real gaps to minimize.
                .filter(l -> l.getTeacher() != null && !l.getTeacher().pseudo)
                .groupBy(Lesson::getTeacher, l -> l.timeslot.getDate(),
                        toConsecutiveSequences(l -> TimeslotSequence.slotIndexOf(l.timeslot)))
                .flattenLast(SequenceChain::getBreaks)
                .penalize(ScoreCompat.ofSoft(2),
                        (teacher, date, break_) -> calculateBreakGapMinutes(break_))
                .asConstraint("S1 - Minimize teacher gaps");
    }

    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; onora gli slot preferiti dichiarati dal docente (input), introdotto fin dalle origini.
     */
    Constraint teacherPreference(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)  // Exclude unassigned lessons (TASK-225)
                .join(TeacherAvailability.class,
                        Joiners.equal(Lesson::getTeacher, ta -> ta.teacher),
                        Joiners.overlapping(l -> l.timeslot.startDateTime, l -> l.timeslot.endDateTime,
                                ta -> ta.startDateTime, ta -> ta.endDateTime))
                .filter((l, ta) -> ta.type == AvailabilityType.PREFERENCE)
                .penalize(ScoreCompat.ONE_SOFT)
                .asConstraint("S3 - Teacher preference");
    }

    /**
     * M21 - Max 2 hours per subject per day (recalibrated TASK-365, threshold
     * raised TASK-381 "L3 - shape constraints golden-first").
     *
     * <p>Full intent reconstruction, the Q18 school answer, and the golden
     * before/after numbers for the original 1h threshold (867/-252,300 -&gt;
     * 452/-621) are documented in CLAUDE.md's "M21 recalibration (task-365)"
     * design-decision section - not repeated here. In short: three
     * exemptions apply before the count (lab lessons via {@link
     * Lesson#isLab()}, the school-named whitelist {@link
     * #M21_EXEMPT_SUBJECTS}, and pseudo-teacher lessons via {@link
     * Teacher#pseudo}, e.g. the synthetic "Stage" course), and the weight is
     * {@code ONE_MEDIUM} (1/excess-hour), matching the M24/M25 (TASK-368)
     * convention.
     *
     * <p>THRESHOLD 1 -&gt; 2 (TASK-381, golden-first). Q18's "possibilmente
     * no" is the school's stated PREFERENCE, but a mechanical count of the
     * golden's own 2026/27 practice (non-exempt subjects) shows 363
     * (group, subject, day) combinations with exactly 2h, 272 of them
     * adjacent - the school routinely teaches ordinary-subject 2h blocks
     * despite the docx wording. Per the golden-first directive (CLAUDE.md's
     * "golden-practice-legitimizes" rule - our rules must never be stricter
     * than the golden's own practice), the threshold moves from 1h to 2h:
     * penalize {@code totalHours - 2} once {@code totalHours > 2}, so a 2h
     * day is free, a 3h day costs 1, a 4h day costs 2. See CLAUDE.md's
     * "Shape recalibration (L3, TASK-381)" section for the exact
     * before/after golden pin.
     *
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: la scuola costruisce blocchi da 2h, quindi la soglia e' a 2h (alzata da 1h, TASK-381). Materie lab/Informatica/Competenze Digitali/Disegno esenti; pseudo-docente esente.
     */
    Constraint m21MaxHoursPerSubjectPerDay(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)  // Exclude unassigned lessons
                .filter(l -> !l.isLab())           // Exclude lab lessons
                .filter(l -> l.getTeacher() != null && !l.getTeacher().pseudo)  // Exclude pseudo-teacher lessons (e.g. Stage)
                // Course.subject has no NOT NULL constraint (CourseResource copies the REST
                // payload verbatim) - a null subject can never be on the whitelist, but must
                // short-circuit before normalize(), which NPEs on a null key (Map.of() rejects it).
                .filter(l -> l.getSubject() == null || !M21_EXEMPT_SUBJECTS.contains(SubjectNormalization.normalize(l.getSubject())))
                // Group by (StudentGroup, Subject, Date) and count hours
                .groupBy(Lesson::getStudentGroup, Lesson::getSubject, l -> l.timeslot.getDate(), sum(l -> 1))
                .filter((group, subject, date, totalHours) -> totalHours > M21_MAX_HOURS_PER_SUBJECT_PER_DAY)
                .penalize(ScoreCompat.ONE_MEDIUM,
                        // Penalize excess hours beyond the threshold: 3 hours = 1 penalty, 4 hours = 2, etc.
                        (group, subject, date, totalHours) -> totalHours - M21_MAX_HOURS_PER_SUBJECT_PER_DAY)
                .asConstraint("M21 - Max 2h per subject per day");
    }

    /**
     * M32 - Template deviation (TASK-413 "quasi-pinning graduato per scarsità").
     *
     * <p><b>Problem it solves:</b> the local search erodes the weekly template
     * the construction phase builds (measured recurrence ~77% at construction
     * end collapsing toward ~37% by termination), because moving a lesson off
     * its template cell only costs S13 = 2 soft (a weak, MOBILE
     * previous-week reference that can measure recurrence but cannot defend
     * it). M32 adds a FIXED reference (the pre-solve template) with a
     * scarcity-graduated medium weight, so the solver keeps a templated lesson
     * on its cell unless moving it repays a bigger problem (a hard violation,
     * or a full M20 day) - full rationale in CLAUDE.md / the design doc
     * claudedocs/design/task-413-quasi-pinning-graduato-2026-07-24.md.
     *
     * <p><b>Weekly semantics:</b> a "cell" is (weekday, slotIndex), NOT a
     * specific date ({@link SchoolSchedule#encodeTemplateCell}). Moving a
     * lesson to a DIFFERENT DATE at the same weekday/slot is NOT a deviation -
     * the weekly cadence is preserved. Only landing on a different
     * weekday/slot than any of the course's template cells is penalized.
     *
     * <p><b>Scarcity gradation:</b> the per-course weight is {@link
     * SchoolSchedule#getTemplateDeviationWeight} = {@code clamp(K / vrpCells,
     * MIN, MAX)} (published pre-solve from the course's VRP cell count). A
     * course with few legal cells (e.g. a single-availability teacher) is
     * near-immobile (weight near MAX=500, still below M20/M22's 1000/day); one
     * with thousands of cells is cheap to relocate (weight near MIN=20).
     *
     * <p><b>RAPPORTO CON M20 CAMBIATO dalla progressione (2026-07-26).</b> Il
     * marginale di M20 non e' piu' 1000/ora piatto: sale fino a 4500 sui giorni
     * profondi ({@link #m20DeficitCost}), quindi il tetto di M32 (MAX = 500, gia'
     * "sotto M20" per costruzione) e' ora molto piu' sotto di quanto fosse quando
     * il peso fu calibrato - riempire un giorno profondo puo' pagare piu' di una
     * deviazione anche per un corso scarso. WATCH-ITEM del gate (insieme ai match
     * di M15 e alla ricorrenza); ritarare SOLO su evidenza A/B.
     *
     * <p><b>Exemptions</b> (each with a reason M32 has no reference to act on):
     * <ul>
     *   <li>PINNED lessons - not movable by the solver anyway (golden import).
     *       DELIBERATELY the plain {@code pinned} flag, NOT {@code
     *       BaseConstraintProvider#isAuthoritativePin} like the rest of the
     *       pinned-exempt family - the one such divergence in the codebase, and
     *       score-neutral: see that method's javadoc ("NOT applied to M32
     *       itself").</li>
     *   <li>PSEUDO-teacher lessons (Stage/Esame) - never templated, and the
     *       M21/S12 precedent excludes them from shape rules.</li>
     *   <li>Courses ABSENT from the template map - no cell to deviate from
     *       (e.g. micro-courses the builder skipped, or a fully-pinned import
     *       where the map is empty and M32 is entirely inert).</li>
     *   <li>Non-canonical timeslots ({@code slotIndexOf == -1}) - no encodable
     *       cell, mirroring M28's own slotIndexOf exemption.</li>
     * </ul>
     *
     * <p>TASK-377 note: uses {@code forEachIncludingUnassigned} + a {@code
     * timeslot != null} filter (the H1/H3 pattern), NOT {@code
     * forEach(Lesson.class)} - the latter iterates only fully-initialized
     * (timeslot AND room) entities, so during the room-less timeslot phases
     * this constraint would be silently inert exactly where it must bite.
     *
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: il template settimanale per corso (cella weekday/slot ricorrente) e' derivato dal golden/stamp e pubblicato pre-solve (WeeklyTemplatePlanner.buildReference). Peso graduato per scarsezza; introdotto TASK-413.
     */
    Constraint templateDeviation(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)
                .filter(l -> !l.pinned)
                .filter(l -> l.getTeacher() != null && !l.getTeacher().pseudo)
                .filter(l -> l.course != null && l.course.id != null)
                .filter(SchoolScheduleConstraintProvider::deviatesFromTemplate)
                .penalize(ScoreCompat.ONE_MEDIUM,
                        l -> SchoolSchedule.getTemplateDeviationWeight(l.course.id))
                .asConstraint("M32 - Template deviation");
    }

    /**
     * True if {@code l}'s (weekday, slotIndex) cell is not one of its course's template
     * cells (see {@link #templateDeviation}). Both halves live in {@link SchoolSchedule}
     * (TASK-419 code review: the staged solve's promotion rule reads the same on-cell
     * test, and two copies of the cell encoding would drift): a deviation needs a
     * REFERENCE to deviate from ({@link SchoolSchedule#hasTemplateReference} - an
     * untemplated course or a non-canonical slot is exempt, never penalized, mirroring
     * M28) AND the lesson to be off it ({@link SchoolSchedule#isOnTemplateCell}). That
     * asymmetry is why the two are not each other's negation.
     */
    private static boolean deviatesFromTemplate(Lesson l) {
        return SchoolSchedule.hasTemplateReference(l.course.id, l.timeslot)
                && !SchoolSchedule.isOnTemplateCell(l.course.id, l.timeslot);
    }

    // ─── M27 - T20 Subject Start-Period Preference (TASK-369) ────────────
    //
    // T20 ("iniziare subito Merceologia e Arredi Tessili; Tecnologia
    // tessile e Storia della moda devono iniziare più tardi; quando si vuole
    // Figurino, Consulenza d'immagine e Sviluppo Sostenibile",
    // vincoli-in-parole.md): a per-subject window on the week offset of the
    // course's FIRST scheduled lesson, counted as a plain elapsed-time week
    // from the school year start (floor(daysSinceStart/7), 0-indexed) - NOT
    // an ISO calendar week like M24/M25's isoWeekKey below, since the docx
    // talks about "starting within the first N weeks of the year", not a
    // calendar-week boundary.
    //
    // Windows derived from the golden's actual first-lesson dates per
    // (group, subject) - see scripts/analyze/derive-golden-availability-checks.py's
    // TASK-369 section for the re-runnable derivation:
    //   - Merceologia/Arredi Tessili ("start immediately"): 5 of 6 golden
    //     group/subject pairs start at week 0, the latest at week 2 (3
    //     MODA/Arredi Tessili) - T20_EARLY_MAX_START_WEEK=3 leaves 1 week
    //     of margin above that latest observed start.
    //   - Tecnologia tessile/Storia della moda ("start later"): earliest
    //     golden start is week 6 (G01/Storia della moda), latest week 31
    //     (G09/Tecnologia tessile) - T20_LATE_MIN_START_WEEK=4 leaves 2
    //     weeks of margin below that earliest observed start.
    //   - Figurino/Consulenza d'immagine/Sviluppo Sostenibile ("quando si
    //     vuole" - anytime): deliberately absent from both sets below, so
    //     they never match this constraint.
    private static final Set<String> T20_EARLY_SUBJECTS = Set.of("Merceologia", "Arredi Tessili");
    private static final int T20_EARLY_MAX_START_WEEK = 3;
    private static final Set<String> T20_LATE_SUBJECTS = Set.of("Tecnologia tessile", "Storia della moda");
    private static final int T20_LATE_MIN_START_WEEK = 4;

    private static int weeksSinceSchoolYearStart(LocalDate date) {
        return (int) ChronoUnit.WEEKS.between(TimeslotSequence.SCHOOL_YEAR_START, date);
    }

    /**
     * <p><b>Origine.</b> "Subito merceologia in 1° e 2°" e "3° fa tecnologia e merceologia ultime" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", righe 98 e 101). Finestra sulla settimana della prima lezione per materie T20; introdotto TASK-369.
     */
    Constraint T20SubjectStartPeriod(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null && l.course != null && l.getSubject() != null)
                .filter(l -> {
                    String subject = SubjectNormalization.normalize(l.getSubject());
                    return T20_EARLY_SUBJECTS.contains(subject) || T20_LATE_SUBJECTS.contains(subject);
                })
                // Group by Course (already the unique (group, subject, teacher) key) and
                // find its earliest scheduled date - min() is O(1) incremental, unlike
                // collecting to a list and re-sorting on every move.
                .groupBy((Lesson l) -> l.course, min((Lesson l) -> l.timeslot.getDate()))
                .filter((course, firstLessonDate) -> {
                    String subject = SubjectNormalization.normalize(course.subject);
                    int week = weeksSinceSchoolYearStart(firstLessonDate);
                    if (T20_EARLY_SUBJECTS.contains(subject)) {
                        return week > T20_EARLY_MAX_START_WEEK;
                    }
                    return week < T20_LATE_MIN_START_WEEK;
                })
                .penalize(ScoreCompat.ONE_MEDIUM)
                .asConstraint("M27 - Subject start-period preference");
    }

    // ─── M24/M25 - Weekly Choice-Day Constraints (TASK-368) ────────────────
    //
    // The school's 2026-07-17 docx ("vincoli-in-parole.md") revises T11's
    // and T05's availability with CHOICE-DAY semantics that a static
    // RecurringAvailabilityRule (fixed day-of-week template) cannot express:
    // "one day a week, the solver's choice, may/must be different". Both are
    // implemented as weekly-quantified constraint streams grouped by
    // (teacher, ISO week) instead of (teacher, day-of-week).

    /**
     * ISO week key for every ISO-week groupBy in this file (M24/M25/S9's
     * diluteHoursOverYear/S12). Delegates to {@link TimeslotSequence#isoWeekKey}
     * - the single source of truth for the WeekFields.ISO-vs-locale-default
     * and year-boundary rationale (see that method's own javadoc) - instead
     * of keeping a second copy of the same formula in this file.
     */
    private static int isoWeekKey(LocalDate date) {
        return TimeslotSequence.isoWeekKey(date);
    }

    /**
     * Shared groupBy pipeline behind both M24 and M25: for teachers matching
     * {@code hasOverride} (i.e. configured with the relevant per-teacher
     * field), count distinct days/ISO-week on which they have a Lesson
     * matching {@code extraLessonFilter}. M24 narrows to slot-0 lessons
     * (early-start days); M25 leaves every lesson in (any day used at all).
     */
    private static TriConstraintStream<Teacher, Integer, Long> teacherWeekDistinctDayCounts(
            ConstraintFactory constraintFactory, Predicate<Teacher> hasOverride, Predicate<Lesson> extraLessonFilter) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null && l.getTeacher() != null && !l.getTeacher().pseudo
                        && hasOverride.test(l.getTeacher()))
                .filter(extraLessonFilter)
                .groupBy(Lesson::getTeacher,
                        (Lesson l) -> isoWeekKey(l.timeslot.getDate()),
                        countDistinct((Lesson l) -> l.timeslot.getDate()));
    }

    /**
     * M24 - At most one early start day per week.
     *
     * T11 ("9-14 sempre e un giorno alle 8.00", TASK-368): the daily
     * slot-0 VACATION block was removed from availability_rules.csv so slot 0
     * is generically open, and this constraint caps how many DISTINCT days a
     * week {@link Teacher#maxEarlyStartDaysPerWeek}-configured teacher may
     * actually use it - penalizing the 2nd+ such day/week.
     *
     * <p><b>Origine.</b> "T11 | 9-14 sempre e un giorno alle 8.00" (workbook vincoli Spalmone, tab "Vincoli Docenti", riga 49). Per-docente maxEarlyStartDaysPerWeek (T11=1); raggruppato per (docente, settimana ISO); introdotto TASK-368.
     */
    Constraint atMostOneEarlyStartDayPerWeek(ConstraintFactory constraintFactory) {
        return teacherWeekDistinctDayCounts(constraintFactory,
                        teacher -> teacher.maxEarlyStartDaysPerWeek != null,
                        l -> TimeslotSequence.slotIndexOf(l.timeslot) == 0)
                .filter((teacher, week, earlyStartDays) -> earlyStartDays > teacher.maxEarlyStartDaysPerWeek)
                .penalize(ScoreCompat.ONE_MEDIUM,
                        (teacher, week, earlyStartDays) -> earlyStartDays - teacher.maxEarlyStartDaysPerWeek)
                .asConstraint("M24 - At most one early start day per week");
    }

    /**
     * M25 - Required free day per week.
     *
     * T05 ("un giorno a scelta deve essere lasciato senza ore", TASK-368):
     * at least {@link Teacher#requiredFreeDaysPerWeek} of the Mon-Fri days in
     * each ISO week must have zero lessons for the teacher. Counts distinct
     * days actually used that week (from real Lesson placements) against a
     * flat 5-weekday span; penalizes the shortfall below the requirement.
     *
     * <p>{@code weekdaySpan} is a flat 5 (Mon-Fri), not calendar/closure-aware
     * - a week with a school closure has fewer than 5 real teaching days, so
     * this can slightly overcount "used" capacity in closure weeks. Accepted
     * as the same class of simplification already used by M20's daily quota
     * (medium tier, not zero-tolerance) rather than wiring in SchoolHoliday
     * lookups for a choice-day nicety.</p>
     *
     * <p>Only evaluated for weeks where the teacher has at least one placed
     * lesson (constraint streams can't iterate absent entities) - a week with
     * zero lessons for the teacher trivially has 5 free days already, so
     * skipping it changes nothing.</p>
     *
     * <p><b>Origine.</b> T05: "VENERDI' | Libero  a casa" e "No lezione in uno di questi giorni AL MASSIMO UN'ORA" (workbook vincoli Spalmone, tab "Vincoli Docenti", righe 45-46). Per-docente requiredFreeDaysPerWeek (T05=2); introdotto TASK-368.
     */
    Constraint requiredFreeDayPerWeek(ConstraintFactory constraintFactory) {
        final int weekdaySpan = 5;
        return teacherWeekDistinctDayCounts(constraintFactory,
                        teacher -> teacher.requiredFreeDaysPerWeek != null,
                        l -> true)
                .filter((teacher, week, usedDays) -> (weekdaySpan - usedDays) < teacher.requiredFreeDaysPerWeek)
                .penalize(ScoreCompat.ONE_MEDIUM,
                        (teacher, week, usedDays) -> teacher.requiredFreeDaysPerWeek - (weekdaySpan - usedDays))
                .asConstraint("M25 - Required free day per week");
    }

    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; premia lezioni studente consecutive (qualita'), introdotto fin dalle origini.
     */
    Constraint rewardConsecutiveStudentLessons(ConstraintFactory constraintFactory) {
        // TASK-225 - Use forEachIncludingUnassigned to bypass isInitializedPredicate
        //
        // RATIONALE: Strong soft constraint (2026-03-15) based on spalmone human schedule analysis.
        // Human schedule shows strong preference for compaction over hour distribution:
        // - S7 reward: +50,049 (16,683 consecutive matches)
        // - S9 penalty: -40,590 (2,293 concentrated week violations)
        // - Net: +7,457 (human prioritizes S7 over S9)
        //
        // Tier decision: Keep SOFT tier to avoid Phase 3 oscillation.
        // Medium tier conflicts with H1 conflict resolution (breaking consecutive patterns
        // is too expensive, causing solver to oscillate). By using soft tier with higher
        // weight, S7 guides behavior without blocking necessary conflict resolution.
        //
        // Weight: 10 soft (vs S9's 1 soft) = 10x stronger, matching human priorities.
        // See - Pareto optimum analysis in project memory ("gemini-framework-analysis-pareto-optimum")
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)
                .join(constraintFactory.forEachIncludingUnassigned(Lesson.class)
                        .filter(l -> l.timeslot != null),
                        Joiners.equal(Lesson::getStudentGroup),
                        Joiners.equal(l -> l.timeslot.getDate()),
                        Joiners.lessThan(l -> l.id))
                .filter((a, b) -> isAdjacent(a, b))
                .reward(ScoreCompat.ofSoft(10))  // Soft tier, 10x weight vs S9
                .asConstraint("S7 - Reward consecutive student lessons");
    }

    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; penalizza lezioni studente isolate (qualita'), introdotto fin dalle origini.
     */
    Constraint penalizeIsolatedStudentLessons(ConstraintFactory constraintFactory) {
        // TASK-225 - Use forEachIncludingUnassigned to bypass isInitializedPredicate
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)
                .ifNotExists(constraintFactory.forEachIncludingUnassigned(Lesson.class)
                        .filter(l -> l.timeslot != null),
                        Joiners.equal(Lesson::getStudentGroup),
                        Joiners.equal(l -> l.timeslot.getDate()),
                        Joiners.filtering((a, b) -> !a.id.equals(b.id) && isAdjacent(a, b)))
                .penalize(ScoreCompat.ofSoft(20))
                .asConstraint("S8 - Isolated student lesson");
    }

    // S9 - Dilute hours over the year (task-014)
    // Encourage ~1 lesson per subject per week to avoid long gaps
    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; diluire le ore sull'anno (qualita'). DISABILITATO dal task-273 (si oppone alla compattezza del calendario); vedi S12 per la continuita' settimanale distinta e attiva.
     */
    Constraint diluteHoursOverYear(ConstraintFactory constraintFactory) {
        // TASK-368 code review: was inlining weekOfWeekBasedYear() alone, which
        // collides week numbers across the Sep 2026-Jun 2027 school-year
        // boundary (see isoWeekKey's own javadoc) - now shares the same
        // year-safe week key as M24/M25 instead of carrying a second, subtly
        // narrower week-grouping mechanism in this same file.
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)
                .filter(lesson -> !lesson.pinned)
                .groupBy(
                        Lesson::getStudentGroup,
                        Lesson::getSubject,
                        (Lesson l) -> isoWeekKey(l.timeslot.getDate()),
                        count())
                .filter((group, subject, week, lessonCount) -> lessonCount > 1)
                .penalize(ScoreCompat.ofSoft(10),
                        (group, subject, week, lessonCount) -> lessonCount - 1)
                .asConstraint("S9 - Dilute hours over year");
    }

    /**
     * S12 - Weekly subject presence (TASK-369).
     *
     * "Possibilmente almeno 1 ora/settimana per materia (esclusi periodi di
     * stage) per continuità didattica" (vincoli-in-parole.md clause 9). This
     * is a DIFFERENT rule from S9 above, not a restoration of it: S9 (even
     * were it re-enabled) penalizes MORE than 1 lesson/subject/week - an
     * upper-bound anti-concentration rule that fights M22's calendar
     * compactness (see S9's own DISABLED comment, task-273). This clause is
     * the opposite direction, a LOWER bound: a subject shouldn't go a whole
     * week with zero hours while it's still actively being taught.
     *
     * <p>Detecting an explicit "this week had zero lessons" would require
     * enumerating the full domain of weeks a (group, subject) is "active"
     * for (first lesson to last lesson, minus stage weeks) - not expressible
     * as a per-entity/per-pair constraint stream the way the rest of this
     * file's constraints are. Instead this rewards each distinct (group,
     * subject, ISO week) that has at least one lesson, once per week
     * regardless of how many lessons land in it (plain {@code groupBy} with
     * no collector - Timefold treats each distinct key tuple as one match).
     * Maximizing this reward is equivalent to spreading a subject's hours
     * across as many weeks as possible, which achieves the same "no full
     * week goes dark" goal without needing an absence check. A stage week
     * naturally can't earn this reward (H4 keeps non-prof lessons off the
     * group's stage days), so the docx's "esclusi periodi di stage" carve-out
     * falls out for free - no explicit stage-window filter is needed.</p>
     *
     * <p>Uses the same ISO-week key as M24/M25 ({@link #isoWeekKey}) rather
     * than M27's plain elapsed-week key just above - this clause is about
     * real calendar weeks matching everyday usage ("una settimana"), not
     * "weeks since school start".</p>
     *
     * <p>MEASURED 2026-07-18 against the golden 2026/27 fixture (TASK-355):
     * soft score +5,623 (5,623 distinct (group, subject, week) matches,
     * ONE_SOFT/match) - re-derived independently in Python via
     * scripts/analyze/derive-golden-availability-checks.py's TASK-369
     * section (same isocalendar()-based grouping, same pseudo-teacher
     * exclusion, same 5,623). The naive Python count first read 5,625; the
     * gap traces to 15 {@code report.unmatchedNoTimeslot} rows (G04/Scienze
     * + G08/RA, dated 2027-06-14/15, past {@code SCHOOL_YEAR_END}
     * 2027-06-10) that never become pinned Lessons and were each the sole
     * lesson in their (group, subject, week) combo - excluding rows outside
     * the Timeslot horizon in the Python script reproduces 5,623 exactly (see
     * CLAUDE.md "Course-timing constraints (task-369)" for the full trace).
     * Deliberately NOT {@code !lesson.pinned}-exempt like S9 used to be (or
     * like M20/H21's pinned exemptions): unlike a hard constraint the golden
     * might organically violate, this is a reward, and measuring it directly
     * against the 100%-pinned golden import is exactly how "the golden was
     * hand-built with this continuity principle" gets verified - the same
     * reasoning M24/M25 already apply.</p>
     *
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; continuita' settimanale (almeno una lezione/settimana mentre la materia e' attiva, lower bound, NON ripristino di S9); introdotto TASK-369.
     */
    Constraint weeklySubjectPresence(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null && l.getSubject() != null)
                .filter(l -> l.getTeacher() != null && !l.getTeacher().pseudo)
                .groupBy(Lesson::getStudentGroup,
                        (Lesson l) -> SubjectNormalization.normalize(l.getSubject()),
                        (Lesson l) -> isoWeekKey(l.timeslot.getDate()))
                .reward(ScoreCompat.ONE_SOFT)
                .asConstraint("S12 - Weekly subject presence");
    }

    // ─── M34/M35 - Golden cadence burst cap + spread deficit (TASK-425) ──────
    //
    // The cadence KPI tooling (scripts/analyze/cadence-kpi.py + the pinned
    // reference claudedocs/spalmone-2026-27/data/golden-cadence-reference.json)
    // measures each (group, subject) pair's OWN golden weekly cadence: its max
    // weekly hours ("burst") and its distinct active ISO-week count ("spread").
    // M34/M35 read that per-pair reference, published pre-solve by
    // ScheduleService.publishCadenceReferenceStatics, and measure the LIVE
    // schedule against THAT pair's own golden cadence - self-gating by
    // construction (an already-good pair has hours <= its own golden max, so
    // deficit 0), not against a flat universal threshold.
    //
    // POPULATION (shared with S12 above): forEachIncludingUnassigned + a
    // timeslot/subject/real-teacher filter, the same S12/M21 pseudo-teacher
    // precedent (an 8h Stage day is one long accounting block, not a genuine
    // subject burst; a lab rotation block is the shape the school DOES build, so
    // labs stay IN - the same inclusion S12/M21 already apply). The subject is
    // normalized symmetrically with the publisher (same SubjectNormalization.normalize
    // on both sides, so a case-duplicate spelling can never split a pair's
    // reference from its live matches).
    //
    // WEIGHT KNOBS (benchmark-tunable, NOT ground truth): read ONCE at class-init
    // from a system property, falling back to the fixed default when unset/blank/
    // unparsable - same shape as SchoolSchedule.TEMPLATE_DEVIATION_WEIGHT_* and
    // M33_SURPLUS_WEIGHT. A benchmark arm supplies them via -D on the JVM command
    // line; the in-suite tests get the defaults.

    /** Default weight of one surplus burst hour beyond the pair's golden max weekly cadence.
     *  System property {@code planner.m34.weight} (default 100). {@code public} so the
     *  cross-package golden guard can reference the same knob the L0 verifier test does. */
    public static final int M34_WEIGHT = readCadenceKnobProperty("planner.m34.weight", 100);
    /** Default weight of one missing active week below the pair's golden spread.
     *  System property {@code planner.m35.weight} (default 50). {@code public} so the
     *  cross-package golden guard can reference the same knob the L0 verifier test does. */
    public static final int M35_WEIGHT = readCadenceKnobProperty("planner.m35.weight", 50);

    /**
     * Parse an M34/M35 weight-knob system property, falling back to
     * {@code defaultValue} when unset, blank, or not a valid integer (never
     * throws - a malformed property must not crash startup). Mirrors
     * {@code SchoolSchedule.readM32KnobProperty} exactly (same class-init
     * resolution, same fail-safe default) so the cadence knobs share the same
     * benchmark discipline M32's own numerator/min/max already established.
     */
    private static int readCadenceKnobProperty(String key, int defaultValue) {
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

    /**
     * M34 - Weekly subject hours above golden cadence (TASK-425 burst cap).
     *
     * <p>For each (group, subject, ISO week) cell, penalize the hours beyond
     * THAT pair's own golden max weekly cadence. A pair the reference does not
     * cover returns {@link Integer#MAX_VALUE} from {@link
     * SchoolSchedule#getCadenceBurstCap}, so {@code hours > MAX_VALUE} is always
     * false and the constraint is inert for it - the same self-gating shape M32
     * uses (a course absent from the template map is never a deviation), not a
     * flat universal cap that would penalize every pair the school chose to
     * concentrate.
     *
     * <p>POPULATION mirrors S12 exactly (the same filters, the same
     * pseudo-teacher exclusion) so the cap measures the SAME hours S12 rewards
     * spread for; the two constraints are complementary, not redundant, because
     * they optimize on different axes (S12 rewards week PRESENCE, M34 penalizes
     * weekly EXCESS). {@link java.util.function.Predicate} identity with
     * {@code weeklyStabilityEligible} would over-capture (it requires teacher
     * but S12 leaves subject-via-getSubject which can be null on a no-course
     * lesson), so this re-states the filter inline exactly as S12 does.
     *
     * <p>SELF-GATING RATIONALE: already-good groups (whose own golden cadence
     * never bursts above their max) have {@code hours <= cap} for every week,
     * so M34 scores 0 for them by construction - it bites only when the SOLVER
     * creates a burst the school's own accepted timetable does not contain, the
     * same golden-practice-legitimizes reasoning every medium constraint in the
     * "never stricter than practice" family (M21/M28/M29) already applies.
     *
     * <p>Golden measurement: 0/0, structural - the golden fixture is 100%
     * pinned, but M34 is deliberately NOT pinned-exempt (the cap measures ALL
     * real-teacher hours, the same non-exempt reasoning S12/M24/M25 already
     * apply for golden-practice verification): the reference itself is the
     * school's own golden cadence, so measuring the golden against its own max
     * is trivially within-cap by construction.
     *
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: il cap settimanale per (gruppo, materia) e' il massimo settimanale del golden (claudedocs/spalmone-2026-27/data/golden-cadence-reference.json). Self-gating; introdotto TASK-425.
     */
    Constraint weeklySubjectBurstCap(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null && l.getSubject() != null)
                .filter(l -> l.getTeacher() != null && !l.getTeacher().pseudo)
                .groupBy(Lesson::getStudentGroup,
                        (Lesson l) -> SubjectNormalization.normalize(l.getSubject()),
                        (Lesson l) -> isoWeekKey(l.timeslot.getDate()),
                        sum(l -> 1))
                .filter((group, subject, week, hours) -> {
                    if (group == null || group.name == null) return false;
                    // getCadenceBurstCap returns Integer.MAX_VALUE when the pair is absent
                    // from the reference (self-gating: no reference => no cap to enforce),
                    // and hours > MAX_VALUE can never hold, so an untemplated pair is inert.
                    return hours > SchoolSchedule.getCadenceBurstCap(group.name, subject);
                })
                .penalize(ScoreCompat.ONE_MEDIUM,
                        (group, subject, week, hours) -> {
                            int cap = SchoolSchedule.getCadenceBurstCap(group.name, subject);
                            // Clamp at cap (filter guarantees hours > cap, this is defensive
                            // against any future map mutation between filter and weigher).
                            return M34_WEIGHT * (int) (hours - cap);
                        })
                .asConstraint("M34 - Weekly subject hours above golden cadence");
    }

    /**
     * M35 - Weekly subject spread below golden cadence (TASK-425 active-week deficit).
     *
     * <p>For each (group, subject) pair, count the distinct active ISO weeks
     * (the SAME {@code countDistinct(isoWeekKey)} S12's reward implicitly
     * maximizes) and penalize the shortfall below THAT pair's own golden
     * active-week count. A pair the reference does not cover returns 0 from
     * {@link SchoolSchedule#getCadenceActiveWeeks}, so the {@code target > 0}
     * gate short-circuits to inert for it - the same self-gating shape M34 uses
     * via {@link Integer#MAX_VALUE}.
     *
     * <p>Complementary to S12 (which rewards ANY active week once, the lower
     * bound S12 already maximizes), not redundant: S12 rewards presence at all,
     * M35 charges weeks MISSING relative to the school's OWN spread for that
     * specific pair. A pair whose golden runs 10 active weeks and whose live
     * schedule covers only 7 pays 3 here regardless of how S12 already rewarded
     * those 7; a pair with no reference pays nothing (cannot be in deficit
     * against a target that doesn't exist).
     *
     * <p>POPULATION mirrors M34 exactly (the same filters, the same
     * pseudo-teacher exclusion, the same ISO-week key) so the spread counts the
     * SAME weeks M34 caps. {@code countDistinct} returns Long here (the
     * {@link ai.timefold.solver.core.api.score.stream.ConstraintCollectors#countDistinct}
     * convention already used by M24/M25's {@code teacherWeekDistinctDayCounts}),
     * so the weigher narrows with an explicit {@code (int)} cast matching M33's
     * own pattern.
     *
     * <p>Golden measurement: NOT a self-referential zero like M34. The reference's
     * active_weeks is computed from lessons.csv (the raw golden), while the guard
     * fixture imports via GoldenScheduleImportService which DROPS 15 beyond-horizon
     * rows past SCHOOL_YEAR_END 2027-06-10. G04/Scienze has its last active week
     * (2027-W24) beyond the Timeslot horizon, so the imported golden has 28 active
     * weeks vs the reference's 29 = a 1-week deficit, pinned in the guard as
     * -1*M35_WEIGHT / 1 match (the SAME 15-row gap S12's own 5,623-vs-5,625 pin
     * documents). If the horizon ever extends to W24 this moves to 0.
     *
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: penalizza le settimane attive mancanti rispetto ad active_weeks del golden per (gruppo, materia) (golden-cadence-reference.json). Self-gating; introdotto TASK-425.
     */
    Constraint weeklySubjectSpreadDeficit(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null && l.getSubject() != null)
                .filter(l -> l.getTeacher() != null && !l.getTeacher().pseudo)
                .groupBy(Lesson::getStudentGroup,
                        (Lesson l) -> SubjectNormalization.normalize(l.getSubject()),
                        countDistinct((Lesson l) -> isoWeekKey(l.timeslot.getDate())))
                .filter((group, subject, activeWeeks) -> {
                    if (group == null || group.name == null) return false;
                    // getCadenceActiveWeeks returns 0 when the pair is absent from the
                    // reference (self-gating: no reference => no target to fall short of),
                    // so target == 0 short-circuits the whole filter to false.
                    int target = SchoolSchedule.getCadenceActiveWeeks(group.name, subject);
                    return target > 0 && activeWeeks < target;
                })
                .penalize(ScoreCompat.ONE_MEDIUM,
                        (group, subject, activeWeeks) -> {
                            int target = SchoolSchedule.getCadenceActiveWeeks(group.name, subject);
                            return M35_WEIGHT * (int) (target - activeWeeks);
                        })
                .asConstraint("M35 - Weekly subject spread below golden cadence");
    }

    // ─── S13 - Weekly Stability (TASK-375) ─────────────────────────────────
    //
    // TASK-351's recurrence research (claudedocs/design/weekly-template-redesign-2026-07-17.md)
    // found NO-GO on a template-first pipeline (72% modal ceiling, ~16
    // distinct weekly grids/group) but a real 41.5-72% within-segment
    // recurrence worth capturing as a *preference* rather than a structural
    // template. S13 is that preference: reward an unpinned lesson whose
    // (StudentGroup, slotIndex) cell exactly 7 days earlier held the same
    // (teacher, subject).
    //
    // Weight: 2 soft, deliberately low ("start low", design doc §3) - between
    // S12's presence-only 1 and S1's per-gap-minute 2, well under S7/S8/S10's
    // 10/20/50. This is a nudge toward human-like regularity, not a rule the
    // solver should fight M20/M22/S9 to satisfy; the m-11 A/B protocol below
    // is how it gets tuned from here.
    //
    // DIRECTIONALITY (avoids a bidirectional double-count): the design doc's
    // prose ("matches the lesson in the same slot of the adjacent week(s)")
    // could be read as comparing both neighbours, but a symmetric
    // abs(dateDiff)==7 self-join would let ONE recurring pair (week N, week
    // N+1) earn TWO rewards - once as N's "next week matches", once as N+1's
    // "previous week matches" - double-counting one recurrence event. The
    // task's WHAT #1 is explicit that only the PREVIOUS week counts, so the
    // join key (`current.date - 7` equals `previousWeek.date`) is directional
    // and can only ever produce the (current, previousWeek) ordering: each
    // Lesson is "current" for at most one prior-week match. A 3rd week in the
    // same recurring run earns its own separate reward as ITS OWN "current"
    // against week N+1's anchor - intended (each week of recurrence is a
    // distinct data point), not a duplicate of the N/N+1 pair.
    //
    // PINNED SEMANTICS (asymmetric by design, per the task brief): the
    // "current" (rewarded) side excludes pinned lessons - pinned-heavy
    // solutions (e.g. anything close to the golden import) must not inflate
    // this score just by being pre-fixed, mirroring S9's original historical
    // pinned exemption. The "previousWeek" (anchor) side has NO such filter:
    // a pinned lesson (calendario/golden pin, or a lab rotation pin) is a
    // legitimate, arguably MORE authoritative template cell than a
    // solver-placed one, and an unpinned lesson that reproduces it deserves
    // the same reward as reproducing another solver-placed lesson. This also
    // means the golden fixture (100% pinned) structurally earns S13=0: every
    // Lesson fails the current-side !pinned filter regardless of how many
    // previous-week cells it would otherwise match - see the golden
    // regression test's own comment for the measured pin.
    //
    // CLOSURE-GAP NEUTRALITY: reward-only by construction, so an absent
    // previous week (school closure, stage window, or simply before the
    // course's first lesson) produces no join match and therefore no
    // reward - never a penalty. No explicit SchoolHoliday/StagePattern lookup
    // is needed; the join's date arithmetic already can't manufacture a match
    // against a Lesson that doesn't exist.
    //
    // Pseudo-teacher (Stage/Esame) lessons are excluded on both sides,
    // mirroring M21/S12's precedent - their "recurrence" is a scheduling
    // artifact of the stage window itself, not the human-planner regularity
    // this constraint is meant to capture.
    private static final int WEEKLY_STABILITY_WEIGHT = 2;

    // Package-private (not private) so the ConstraintVerifier test can build its
    // "previous week" fixture off the same source of truth instead of a second,
    // independently-drifting `plusDays(7)` literal.
    static final int WEEKLY_STABILITY_WINDOW_DAYS = 7;

    /** Shared eligibility gate for both sides of {@link #weeklyStability}'s self-join. */
    private static boolean weeklyStabilityEligible(Lesson l) {
        return l.timeslot != null && l.getTeacher() != null && !l.getTeacher().pseudo && l.getSubject() != null;
    }

    /**
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: e' una MISURA della ricorrenza settimanale (cella gruppo/weekday/slot uguale alla settimana precedente su docente e materia). La protezione del template e' M32; introdotto task-375.
     */
    Constraint weeklyStability(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                // TASK-419: authoritative pins only, so a promoted lesson keeps earning the
                // reward it earned unpinned (see isAuthoritativePin's javadoc).
                .filter(l -> weeklyStabilityEligible(l) && !isAuthoritativePin(l))
                .join(constraintFactory.forEachIncludingUnassigned(Lesson.class)
                                .filter(SchoolScheduleConstraintProvider::weeklyStabilityEligible),
                        Joiners.equal(Lesson::getStudentGroup),
                        Joiners.equal(l -> TimeslotSequence.slotIndexOf(l.timeslot)),
                        Joiners.equal((Lesson current) -> current.timeslot.getDate().minusDays(WEEKLY_STABILITY_WINDOW_DAYS),
                                (Lesson previousWeek) -> previousWeek.timeslot.getDate()),
                        Joiners.equal(Lesson::getTeacher),
                        Joiners.equal(l -> SubjectNormalization.normalize(l.getSubject())))
                .reward(ScoreCompat.ofSoft(WEEKLY_STABILITY_WEIGHT))
                .asConstraint("S13 - Weekly stability");
    }

    // S10 - Discourage generic lessons from using lab rooms (soft constraint)
    // Generic lessons CAN use lab rooms if free, but prefer regular rooms
    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; sconsiglia le materie ordinarie nelle aule-lab (qualita'), introdotto task-007.
     */
    Constraint labRoomReserved(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> lesson.room != null
                        && lesson.room.tags != null
                        && !lesson.room.tags.isEmpty()
                        && (lesson.course == null
                            || lesson.course.requiredRoomTags == null
                            || lesson.course.requiredRoomTags.isEmpty()
                            || lesson.room.tags.stream().noneMatch(
                                    lesson.course.requiredRoomTags::contains)))
                .penalize(ScoreCompat.ofSoft(50))
                .asConstraint("S10 - Discourage generic lessons in labs");
    }

    // M-LF - Non-lab lesson in a tagged (lab) room (TASK-456 lab-fallback, domanda 21). The
    // value-domain widening in Lesson.getAvailableRooms (flag planner.room.lab-fallback.enabled)
    // lets a non-lab lesson SIT in a tagged room; this medium penalty makes that a deliberate
    // last-resort SHELTER, not a convenience - so the solver does not park ordinary lessons in
    // the workshops and leave genuine lab sessions room-null. LAB priority is already enforced
    // structurally: H-M2 (hard) keeps a lab session in its tag-matching room, and a non-lab
    // sheltering there pays this penalty on top. Sibling of S10 (soft 50, always-on): S10 is
    // the standing soft nudge, M-LF is the stronger tunable nudge gated by the same flag - both
    // fire when the value domain is open. Their populations nearly coincide on seed data (where
    // Course.isLab is set from the same SubjectFlags as requiredRoomTags) but are NOT identical
    // by definition: M-LF keys off {@code !Lesson.isLab()}, S10 off {@code course.requiredRoomTags}
    // (a REST-created course with the two fields desynced would be counted by one and not the
    // other). M-LF follows the prevailing {@code !isLab()} convention shared by M15/M21/S11/S16.
    //
    // GATED by planner.room.lab-fallback.enabled, read LIVE per-build (SchoolSchedule.
    // isLabFallbackEnabled, same live read the VRP uses, so the harness toggles both with one
    // System.setProperty). DEFAULT ON since maintainer ratified TASK-456 (2026-07-29): the leading
    // `enabled &&` short-circuits to 0 matches only when the flag is explicitly "false" (opt-out).
    // On the golden M-LF contributes 0 anyway - the school never parks an ordinary lesson in a
    // workshop, so there are no non-lab-in-tagged placements to penalize - and
    // GoldenSolutionRegressionTest pins M-LF at 0 to guard that. Weight knob
    // planner.room.lab-fallback.weight (default 1000, decided TASK-456: at parity on every other
    // metric - 0 non-lab room-null, 182 lab room-null unchanged - 1000 is the weight that minimizes
    // ordinary lessons parked in labs (7 vs 10 at weight 1), expressing "the lab is the last
    // resort" with no measured downside; see task456-results/lab-fallback-measure-2026-07-29.md),
    // read INLINE per-build like M37's weight so the harness sweeps it via System.setProperty.
    /**
     * <p><b>Origine.</b> Vincolo strutturale di fallback delle stanze, nessun documento scuola; un'aula capienza<=0 non e' assegnabile (oggi R18 cap 0). Domanda 21 (referente, 29/07); introdotto TASK-456, ratificato maintainer 2026-07-29.
     */
    Constraint nonLabInTaggedRoom(ConstraintFactory constraintFactory) {
        boolean enabled = SchoolSchedule.isLabFallbackEnabled();
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> enabled
                        && lesson.room != null
                        && lesson.room.isTagged()
                        && !lesson.isLab())
                .penalize(ScoreCompat.ofMedium(
                        readCadenceKnobProperty("planner.room.lab-fallback.weight", 1000)))
                .asConstraint("M-LF - Non-lab lesson in a tagged room");
    }

    // M38 - Pair-cell deviation (TASK-576 F7-C, approved by maintainer 2026-09-01). The
    // LS-side defender of the pair-cells construction form: a real-teacher, unpinned
    // lesson whose (teacher, group) pair carries template cells (PairCellGuard.hasForm,
    // published by the pair construction phase itself) pays this medium penalty when
    // its current (weekday, slot) cell is NOT one of the pair's cells
    // (PairCellGuard.protects false). The local search sees the charge in every move's
    // delta score and spends off-form lessons first - the exact mirror of what
    // F7-A's repair surcharge does for the deterministic passes, aimed at the stage
    // E2 proved is the real eroder (the LS phases move ~574 cells; the repairs ~32).
    //
    // SELF-GATING, twice: (a) the pair set is only published when the pair-cells
    // builder is armed (with the course-based builder PairCellGuard is empty and
    // hasForm is always false: 0 matches, selection byte-identical); (b) only pairs
    // that OWNS cells are charged - a pair with no stable form is never punished for
    // being off-form. Pinned lessons (authoritative AND promoted) are exempt, the
    // M32 convention: while promoted they are unmovable anyway, and authoritative
    // pins are the school's own practice.
    //
    // TIER and WEIGHT: medium, knob planner.constraints.pair-deviation.weight default
    // 1 since the 2026-09-02 ratification (maintainer): the weight sweep
    // (f7a/m38-checkpoint/sweep-weight) showed the match is weight-INDEPENDENT
    // (91,3% at 500, 91,3% at 50, 91,3% at 1, OFF 91,0%) while the tax is not:
    // direction comes from the constraint's PRESENCE, magnitude is just tax, and at
    // weight 1 the tax is near zero with internal holes BETTER than OFF (58 vs 67).
    // NOT hard: the golden itself holds ~27% of its pair lessons OFF their practice
    // cells (the 72.7% match measured at construction), so hard would declare the
    // school's own timetable infeasible - the golden-first rule. Flag
    // planner.constraints.pair-deviation.enabled, opt-OUT form (only "false" disarms),
    // read LIVE per-build (M-LF pattern). DEFAULT ON since 2026-09-02.
    /**
     * <p><b>Origine.</b> TASK-575 opzione A (difensore della prassi referente, differita 'a F5 consegnata') + TASK-576 E2 (1/09): il carburante dell'erosione della forma vive nelle fasi LS, non nei repair. Approvato da maintainer 2026-09-01. Ratificato in forma weight=1 il 2026-09-02.
     */
    Constraint pairCellDeviation(ConstraintFactory constraintFactory) {
        boolean enabled = SchoolSchedule.isPairDeviationEnabled();
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> enabled
                        && lesson.timeslot != null
                        && !lesson.pinned
                        && lesson.getTeacher() != null
                        && !lesson.getTeacher().pseudo
                        && PairCellGuard.hasForm(lesson)
                        && !PairCellGuard.protects(lesson))
                .penalize(ScoreCompat.ofMedium(
                        readCadenceKnobProperty("planner.constraints.pair-deviation.weight",
                                SchoolSchedule.PAIR_DEVIATION_DEFAULT_WEIGHT)))
                .asConstraint("M38 - Pair-cell deviation");
    }

    // M-TC - Lab tag capacity (TASK-455, scarcity-aware spec 2026-07-30).
    //
    // PROTOTYPE, flag-gated DEFAULT OFF (planner.tag-capacity.enabled, read LIVE per
    // build via SchoolSchedule.isTagCapacityEnabled, M-LF pattern). Penalizes, per
    // (tag, startDateTime) cell, the lab lessons of a tag beyond the tag's physical
    // room capacity (SchoolSchedule.roomCapacityForTag, published pre-solve from the
    // assignable rooms). The downstream "spreader" consumer of the scarcity-aware
    // spec: the joint scarcity (tag, weekday) the 30e pre-measure isolated to two
    // single-room labs (acconciatura 144h on Tue/Wed/Thu + termoidraulica 24h on Tue,
    // 99% of the 169 H29 room-null).
    //
    // TIER: MEDIUM-high (ofMedium(weight), knob planner.tag-capacity.weight default
    // 1000, the M-LF/M22 "logical-hard medium" family), NOT hard. Hard would be
    // physically honest (two classes cannot share a single-room lab, referente 29/07) but
    // (a) it declares the GOLDEN infeasible, since the school accepts 44 of its own
    // acconciatura overloads (TASK-455), and (b) on the run it stacks about 200 hard
    // violations the solver must clear by spreading, risking the same M20/M15
    // regression the slot0-balancing hit. Medium-high discourages without forbidding,
    // tolerates the golden's 44, and pushes the run to spread. The trade-off is
    // MEASURED at the checkpoint, not assumed. See task455-tag-capacity-30e-2026-07-30.md.
    //
    // ACCOUNTING mirrors H7, the closest capacity idiom: it counts ALL assigned
    // lessons of the tag in the cell, PINNED included (a pinned lab session occupies
    // its slot, just as a pinned lesson counts toward H7's daily-hours total). The
    // solver can lower the penalty only by moving UNPINNED lessons, which is the
    // lever; cells overloaded only by pinned lessons (moda/R16 Thu, 33h on 30e)
    // carry a constant penalty the search cannot move, honest and not
    // gradient-distorting, a TASK-449 pre-assignment residue rather than a TASK-455
    // search defect. Pseudo (Stage) is excluded by the tag filter: pseudo courses
    // carry no requiredRoomTags. Cell = (tag, startDateTime); timeslot has no
    // slotIndex, and the historical aggregation artifact (spec sec 2.3) came from
    // pretending it did. Only single-tag courses are counted (the seed's only shape);
    // a hypothetical multi-tag course is skipped rather than mis-grouped.
    /**
     * <p><b>Origine.</b> Vincolo strutturale di scarsita' dei tag, nessun documento scuola; la domanda di lezioni di un tag oltre la capacita' aggregata delle stanze assignable per cella (tag, startDateTime). Prototipo flag planner.tag-capacity.enabled DEFAULT OFF; introdotto TASK-455.
     */
    Constraint labTagCapacity(ConstraintFactory constraintFactory) {
        boolean enabled = SchoolSchedule.isTagCapacityEnabled();
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> enabled
                        && l.timeslot != null
                        && l.course != null
                        && l.course.requiredRoomTags != null
                        && l.course.requiredRoomTags.size() == 1
                        && l.getTeacher() != null && !l.getTeacher().pseudo)
                .groupBy(
                        l -> l.course.requiredRoomTags.get(0),
                        l -> l.timeslot.startDateTime,
                        count())
                .filter((tag, start, lessonCount) ->
                        lessonCount > SchoolSchedule.roomCapacityForTag(tag)
                                // TASK-485: exempt golden-practiced co-presence cells (depth>=2), mirroring
                                // H-COPRESENCE. The school accepts co-locating >capacity lessons of a tag as
                                // practiced co-presence (acconciatura/moda), so taxing it was a false penalty
                                // (the seed-6 A/B's -120000 on 120 practiced matches). REUSES the same published
                                // isCoPresenceCell predicate and the same start->(weekday,slot) bridge as
                                // coPresenceCap: one definition of "practiced", not two.
                                && !SchoolSchedule.isCoPresenceCell(tag, start.getDayOfWeek(), start.toLocalTime()))
                .penalize(ScoreCompat.ofMedium(
                                readCadenceKnobProperty("planner.tag-capacity.weight", 1000)),
                        (tag, start, lessonCount) ->
                                lessonCount - SchoolSchedule.roomCapacityForTag(tag))
                .asConstraint("M-TC - Lab tag capacity (scarcity-aware TASK-455)");
    }

    // H-COPRESENCE - Guest co-presenza count cap (TASK-464).
    //
    // ADOPTED, flag default ON since the 2026-08-01 ratification (planner.co-presenza.enabled,
    // read LIVE per build via SchoolSchedule.isCoPresenceEnabled, opt-out M-LF pattern). The count-based backstop
    // for the H2 pairwise exemption: H2 now exempts golden-practiced guest pairs so an
    // ospite may share a host's room, but exempting the pairwise penalty alone would also
    // authorize 6+ classes in one room. This cap re-imposes the golden's OWN depth as the
    // ceiling: per (tag, startDateTime) cell, penalize HARD when the count of the tag's lab
    // lessons exceeds the golden depth goldenCoPresenceDepth(tag, weekday, slotStart).
    //
    // TIER: HARD (unlike M-TC's medium). M-TC tolerates the golden's 44 acconciatura
    // overloads because the school accepts them; here the cap IS the golden's own practice
    // ceiling, so exceeding it is the abuse (6+ in a room) and the golden respects its own
    // ceiling, hence 0 hard on a faithful run. Hard also keeps the cap honest against the
    // H2 exemption: a soft cap would let the solver stack beyond depth for a small price.
    //
    // TIER CLASSIFICATION (TASK-464 code-review): this is a TIMESLOT-tier hard, NOT room-tier.
    // It groups by (tag, startDateTime) and never reads l.room (the only "room" token is
    // requiredRoomTags, a course field), so it fires on timeslot co-location the moment lessons
    // have timeslots - during B-TIMESLOT, before rooms are assigned. That is desirable: it stops
    // construction stacking more groups into one slot than the golden depth allows before rooms
    // are even in play. So it is NOT in ScheduleService.ROOM_TIER_HARD_FAMILIES: counting it in
    // timeslotTierHardScore (the default) is what lets the staged cycle's B-TIMESLOT gate and
    // the repair ensureImproveOrNoop SEE and resolve over-depth cells; excluding it (listing it
    // room-tier) would let an over-depth cell read "clean" at the gate, then persist into B-ROOM
    // where moving rooms cannot fix a timeslot co-location.
    //
    // CELL = (tag, startDateTime), exactly like M-TC: two lessons on different Thursdays are
    // NOT co-present, so counting must NOT aggregate across dates (the design's weekday-only
    // grouping would). The CAP is the weekly golden depth, looked up by the weekday + slot
    // start derived from that startDateTime. ACCOUNTING mirrors H7/M-TC: counts ALL assigned
    // lessons of the tag in the cell, PINNED included (a pinned lab session occupies its
    // slot); the solver lowers the penalty only by moving UNPINNED lessons. Pseudo (Stage)
    // is excluded by the tag filter (pseudo courses carry no requiredRoomTags). Only
    // single-tag courses are counted (the seed's only shape). Weight 1 hard per excess unit,
    // the H29 idiom; depth 0 on an unknown cell means the cap never fires there (the cell is
    // not golden-practiced, so it is governed by H2 alone, as today). See task464 design.
    /**
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: il tetto di co-presenza di un ospite in un'aula e' la profondita' che il golden stesso pratica per quella cella (loadGoldenCoPresenceDepth). Lettura del golden su percorso di produzione, censita in TASK-452 #7; introdotto TASK-464.
     */
    Constraint coPresenceCap(ConstraintFactory constraintFactory) {
        boolean enabled = SchoolSchedule.isCoPresenceEnabled();
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> enabled
                        && l.timeslot != null
                        && l.course != null
                        && l.course.requiredRoomTags != null
                        && l.course.requiredRoomTags.size() == 1
                        && l.getTeacher() != null && !l.getTeacher().pseudo)
                .groupBy(
                        l -> l.course.requiredRoomTags.get(0),
                        l -> l.timeslot.startDateTime,
                        count())
                .filter((tag, start, lessonCount) -> {
                    int depth = SchoolSchedule.goldenCoPresenceDepth(
                            tag, start.getDayOfWeek(), start.toLocalTime());
                    return depth >= 2 && lessonCount > depth;
                })
                .penalize(ScoreCompat.ofHard(1),
                        (tag, start, lessonCount) -> {
                            int depth = SchoolSchedule.goldenCoPresenceDepth(
                                    tag, start.getDayOfWeek(), start.toLocalTime());
                            return lessonCount - depth;
                        })
                .asConstraint("H-COPRESENCE - Guest co-presenza beyond golden depth (TASK-464)");
    }

    // M-TR - Theory subject repeated same day (TASK-468, tab "Vincoli DA ANALIZZARE":
    // "non ci devono essere 2 ore vicine della stessa materia teorica; i laboratori,
    // compreso disegno o informatica, possono avere piu' ore vicine").
    //
    // PROTOTYPE, flag-gated DEFAULT OFF (planner.constraints.theory-adjacent.enabled,
    // read LIVE per build via SchoolSchedule.isTheoryAdjacentEnabled, M-LF/M-TC pattern).
    // Decisione maintainer 2026-07-31 sera: ASSOLUTAMENTE medium (non hard) - il golden
    // viola la regola 432 giorni (AC#1, definizione "stretta"), quindi un hard
    // vieterebbe il golden (golden-first violata). Pattern M24/M25: medium a residuo
    // calibrato, mai zero-tolerance. Design completo: task468-results/AC2-design-2026-07-31.md.
    //
    // FORMA: soglia-1 teorica (M21-style ma a soglia 1 sulle materie teoriche; M21 ha
    // soglia 2). Penalizza ONE_MEDIUM x (ore-1) per (gruppo, materia-teorica, giorno)
    // con 2+ ore. A parita' di residuo (~432) rispetto alla penalita'-coppia, la
    // soglia-1 si compone meglio con M15/S17 (che continuano a spingere il BLOCCO
    // quando la materia E' ripetuta, per pressione M20 di riempimento). L'effetto-reale
    // e' "non ripetere la materia teorica nello stesso giorno", non "separa le 2h".
    //
    // COMPOSIZIONE con M15/S17/M21 (il punto chiave del design, non tocca i vincoli
    // esistenti): se la teoria SI ripete same-day, M15 (medium 20/min-gap, ~1200 per
    // 1h di buco) forza l'adiacenza -> il repeat diventa blocco pulito 2h, non ore
    // sparse. M15 (molto piu' forte del nuovo M peso 1) domina la forma del repeat.
    // S17 (+5 soft/coppia adiacente, teoria inclusa) resta invariata: tier diverso, no
    // conflitto, no double-count (M conta per giorno, S17 per coppia). M21 (soglia 2)
    // convive: a 2h paga solo il nuovo M (-1), a 3h pagano entrambi coerentemente.
    //
    // PESO: ofMedium(planner.constraints.theory-adjacent.weight, default 1) per unita'
    // di eccesso (ore-1), letto INLINE per-build come M37/M-LF. Debole (1) di proposito:
    // e' una spinta che M20 (fill, medium fino a 4500/ora sui giorni profondi) e M15
    // possono sovrastare quando il riempimento o la quota lo impongono. Il residuo
    // golden (~432 unita' su 10.542 lezioni, ~4%) e' la calibrazione iniziale; l'A/B
    // (AC#4) lo accorda.
    //
    // ESENZIONI: lab (Lesson.isLab, gia esclusi), pseudo-docente (Stage, come M21), e
    // il set pratico-tecnico (single-source in SubjectNormalization.THEORY_RULE_PRACTICAL_EXEMPT,
    // condiviso col prototipo TASK-470). isTheoryForRule e' il predicato riusabile specchio
    // di M15/M21/S11/S16 (!isLab), NON requiredRoomTags come S10. PINNED: M21-style INCLUDE i
    // pinned (verifica della pratica, non focus-only sul lever); il counting e' per giorno su
    // tutte le lezioni di docente
    // reale. Self-gating: sul golden il residuo e' ~432 (inerte solo se il match fosse
    // perfetto, che non e' il caso - la scuola accetta quei blocchi come residuo).
    /**
     * <p><b>Origine.</b> "Non ci devono essere 2 ore vicine della stessa materia teorica" e "I laboratori (compreso disegno o informatica) possono avere più ore vicine" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", righe 104-105). Soglia-1 teorica (M21-style a soglia 2); prototipo flag planner.constraints.theory-adjacent.enabled DEFAULT OFF; introdotto TASK-468.
     */
    Constraint theorySubjectRepeatedSameDay(ConstraintFactory constraintFactory) {
        boolean enabled = SchoolSchedule.isTheoryAdjacentEnabled();
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> enabled
                        && l.timeslot != null
                        && l.getTeacher() != null && !l.getTeacher().pseudo)
                .filter(SchoolScheduleConstraintProvider::isTheoryForRule)
                // Group by (StudentGroup, theoryRuleGroupingKey(subject), Date) and count hours.
                // The grouping key is single-sourced in SubjectNormalization (T06 alias
                // hook, identity by default), shared with the TASK-470 construction prototype.
                .groupBy(Lesson::getStudentGroup,
                        l -> SubjectNormalization.theoryRuleGroupingKey(l.getSubject()),
                        l -> l.timeslot.getDate(),
                        sum(l -> 1))
                .filter((group, subject, date, totalHours) -> totalHours > 1)
                .penalize(ScoreCompat.ofMedium(
                                readCadenceKnobProperty("planner.constraints.theory-adjacent.weight", 1)),
                        (group, subject, date, totalHours) -> totalHours - 1)
                .asConstraint("M-TR - Theory subject repeated same day");
    }


    // M-BF - Lab-teacher day contiguity (TASK audit-g01 / blockform-623, 17/09):
    // the human planner's step 3 ("lab allocation is per teacher, not per subject":
    // Teacher + Lab + Group + consecutive hours) requires a lab day's hours, grouped
    // by teacher, to form CONTIGUOUS runs. The golden satisfies this on 140 of 141
    // mixed lab days (single violation: G05 2027-03-17); the delivered runs
    // interleave (audit: 54 multi-teacher days on integrato-1, LS-borne erosion
    // crystallized by S13 - the construction is BLOCK 91/109 for G01).
    //
    // RATIFICATO DEFAULT ON (maintainer, 18/09) sul pacchetto completo: banco
    // appaiato -60 interleaved netti (controllo flag-OFF 54->71 = la LS nuda
    // CREA frammentazione, armed 54->11), A/B ciclo completo 4/5 criteri PASS
    // (hard B 0 vs A -6, ricorrenza 69,7 vs 69,8 intatta col template vivo,
    // full-day in banda, sanity senza rosse nuove; (d) interleaved -12 vs -20
    // richiesto: G13 6->1 quasi chiuso, G01 42->38 resta la coda da sweep),
    // re-cert delle cure IDENTICA al numero (bill -142800, 79 giorni tutti
    // multi-docente), golden 0 match (self-gating vivo). Forma opt-out
    // standard (planner.constraints.mbf.enabled, solo il letterale "false"
    // disarma, read LIVE per build via SchoolSchedule.isMbfEnabled).
    //
    // FORMA: per (gruppo, giorno) con >=2 ore lab di docenti REALI e ALMENO DUE
    // docenti lab distinti (il MONO-FRAG single-teacher e' pratica della scuola:
    // 22 giorni G13 nel golden, NON colpito), penalizza i gap-minuti delle ore
    // per-docente NON contigue: peso knob planner.constraints.mbf.weight default
    // 20/min (M15 parity: 1200 per ora di buco), in famiglia con M15/M20 cosi' la
    // frammentazione smette di essere moneta di scambio gratuita per il fill.
    // Include i pinned (verifica della pratica, M21/M-TR style). Self-gating
    // calibrato sul golden: residuo atteso 1 giorno (1200), non per-pair.
    // DIVISIONE DEL LAVORO con M31 (il sibling quota-bound della stessa famiglia):
    // M31 conta i RUN contigui dell'intero giorno lab (una finestra sola, perimetro
    // quota-bound, day-level pinned-exempt); M-BF misura i buchi INTERNI di ogni
    // docente (perimetro isLab, conta i pinned). Divergono per design sui due bordi:
    // il giorno mono-docente frammentato paga M31 ma non M-BF (MONO-FRAG = pratica
    // scuola), il giorno a due blocchi A,A,B,B e' contiguo per-docente (M-BF muto)
    // ma son due run per M31. La calibrazione congiunta dei due pesi e' materia
    // del gate di ratifica, non del prototipo.
    /**
     * <p><b>Origine.</b> Pipeline del pianificatore umano passo 3 (claudedocs/human-planner-heuristics.md)
     * + pratica golden (audit blockform-623: 1 interleaved su 141 giorni misti). Prototipo flag
     * planner.constraints.mbf.enabled DEFAULT OFF su wt/mbf-proto; introdotto TASK audit-g01 17/09.</p>
     */
    Constraint labTeacherDayContiguity(ConstraintFactory constraintFactory) {
        boolean enabled = SchoolSchedule.isMbfEnabled();
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> enabled
                        && l.timeslot != null
                        && l.isLab()
                        && l.getTeacher() != null && !l.getTeacher().pseudo)
                .groupBy(Lesson::getStudentGroup, LESSON_DATE, toList())
                // expand() computes the gap meter once per (group, date) tuple and
                // carries it into both filter and penalize, instead of running the
                // per-teacher scan twice (M31's own documented idiom, one file over
                // in BaseConstraintProvider.labDaySingleContiguousRun).
                .expand((group, date, lessons) -> mbfDayGapMinutes(lessons))
                .filter((group, date, lessons, gapMinutes) -> gapMinutes > 0)
                .penalize(ScoreCompat.ofMedium(
                                readCadenceKnobProperty("planner.constraints.mbf.weight",
                                        SchoolSchedule.MBF_DEFAULT_WEIGHT)),
                        (group, date, lessons, gapMinutes) -> gapMinutes)
                .asConstraint("M-BF - Lab teacher day contiguity");
    }

    /**
     * M-BF meter AND scope gate in one pass: the day's total hole-minutes inside each
     * fragmented lab teacher's own span, or 0 when the day does not qualify. Qualifying
     * means >= 2 distinct real lab teachers with at least one fragmented run: a
     * single-teacher fragmented day is MONO-FRAG, the school's practiced shape (22 G13
     * days in the golden), out of scope by spec. Slots are collected per teacher as a
     * distinct, sorted set, so the span arithmetic can never go negative on a
     * double-booked (H1-violating) intermediate state and the {@code slotIndexOf}
     * negative sentinel (off-schema start time) is skipped rather than corrupting the
     * span. Merged from the gate/meter pair by the quality gate: the teacher set is
     * exactly the meter's own keySet, and the split carried a latent divergence on the
     * unplaced lesson (gate counted its teacher, meter skipped it).
     */
    public static int mbfDayGapMinutes(List<Lesson> lessons) {
        Map<Long, TreeSet<Integer>> slotsByTeacher = new HashMap<>();
        for (Lesson l : lessons) {
            if (l.getTeacher() == null || l.timeslot == null) {
                continue;
            }
            int idx = TimeslotSequence.slotIndexOf(l.timeslot);
            if (idx < 0) {
                continue;
            }
            slotsByTeacher.computeIfAbsent(l.getTeacher().id, k -> new TreeSet<>()).add(idx);
        }
        if (slotsByTeacher.size() < 2) {
            return 0;
        }
        int gapSlots = 0;
        for (TreeSet<Integer> slots : slotsByTeacher.values()) {
            if (slots.size() < 2) {
                continue;
            }
            gapSlots += (slots.last() - slots.first() + 1) - slots.size();
        }
        return gapSlots * 60;
    }

    /** M-TR: Lesson-typed adapter over the single-sourced {@link SubjectNormalization#isTheoryForRule}. */
    private static boolean isTheoryForRule(Lesson l) {
        return SubjectNormalization.isTheoryForRule(l.isLab(), l.getSubject());
    }

    // M-CPS - Curriculum post-stage target 6h asymmetric (TASK-469, bucket C3/T4):
    // "CON II FC E III OIT E III FA 6 ore dopo stage per curriculum (ANCHE 2
    // CONSECUTIVVE)" - vincoli-tabs-golden-xlsx-estratto-2026-07-31.txt riga 50,
    // docente T11.
    //
    // ADOPTED DEFAULT ON since 2026-08-01 (ratifica maintainer: opt-out con
    // planner.constraints.curriculum-poststage.enabled=false, stessa forma delle altre
    // ratifiche; letto LIVE per build via SchoolSchedule.isCurriculumPostStageEnabled).
    // maintainer (2026-08-01) ha DECISO la semantica: target ~6 ASIMMETRICO - penalita PIENA sotto
    // 6 (deficit), penalita DEBOLE oltre 6 (eccesso), con CAUTELA su G11.
    //
    // PERIMETRO (coorti finali, le uniche con obbligo curriculum post-stage; docente T11,
    // univoco per (gruppo, materia) nel seed incarichi.csv):
    //   - G10  -> Comportamento sociale              (golden: 6h post-stage, target centrato)
    //   - G08  -> Competenze di cittadinanza +        (golden: 5h post-stage, deficit -1)
    //              Educazione civica
    //   - G11 -> Personalizzazione                   (golden: 29h, cautela: corso 60h che
    //                                                    prosegue post-stage per volumi propri)
    //
    // STAGE-END: fatto per-gruppo da INPUT, pubblicato PRE-solve da
    // ScheduleService.refreshSolverStatics come SchoolSchedule.lastStageDateFor (ultima data
    // con DailyActivityQuota.stageHours > 0). NON dal StagePattern (seedato solo per
    // G07/G11/G12 - vedi DataGenerator.generateStagePatterns riga 704, gli altri
    // gruppi non hanno StagePattern): leggere StagePattern.endDate avrebbe reso il vincolo
    // CIECO su G10 e G08 (join senza match). NON dal golden: il calendario
    // (calendario_fc.csv / calendario_class_days.csv modality S) copre TUTTI i 14 gruppi ed
    // e' la sorgente uniforme. Date verificate (2026-08-01, last stage day): G10 2027-04-23
    // e G08 2027-04-26 (combaciano col baseline); G11 2027-03-18 (StagePattern.endDate
    // e' 2027-03-19: il calendario ha l'ultimo giorno di stage al 18, lo StagePattern data il
    // "return day"; il delta di 1 giorno su G11 e' da calibrazione A/B).
    //
    // FORMA: per ogni gruppo del perimetro conta le ore di materia-curriculum collocate DOPO
    // lo stage-end del gruppo; penalizza ONE_MEDIUM x peso-asimmetrico:
    //   - deficit (ore < 6): (6 - ore) x full-weight  (default 100, "piena")
    //   - eccesso (ore > 6): (ore - 6) x weak-weight  (default 1, "debole")
    //   - ore == 6: 0
    // Tier MEDIUM, mai hard: il golden stesso ha G08=5 (<6) e G11=29 (>>6), quindi un
    // hard vieterebbe il golden (golden-first violata).
    //
    // CAUTELA G11: 29h post-stage = 23h di eccesso. A weak-weight=1 sono 23 medium, basso
    // abbastanza da non combattere calendario/fill (da misurare nell'A/B AC#4 che non distorca).
    // Il deficit di G08 (1h x full-weight=100 = 100 medium) esprime preferenza per il target
    // ma cede di fronte a M20 (fill ~1000/giorno) e vincoli hard: proporzionato per un
    // prototipo ancora gated alla risposta referente (G08 e' 5h o 6h?). Entrambi i pesi sono knob.
    //
    // ESENZIONI: pseudo-docente (Stage, come M21/M-TR). Nessun filtro isLab: il perimetro e'
    // per (gruppo, materia), le materie curriculum non sono lab.
    //
    // SELF-GATING + BLIND SPOT: il leading `enabled &&` inertisce a 0 match a flag spento
    // (byte-identico a OFF, AC#3). Limitazione nota: il groupBy e' lesson-driven, quindi un
    // gruppo con 0 ore curriculum post-stage NON produce match e il suo deficit (6) resta
    // invisibile al vincolo. Il golden ha sempre >=1 ora post-stage per gruppo del perimetro,
    // quindi la MISURA del residuo (vs baseline 6/5/29) e' valida; il blind spot riguarda solo
    // stati di solve con 0 collocazioni post-stage (da verificare nell'A/B; la cura corretta
    // richiede un fatto per-gruppo driver tipo M20/M30.2, out-of-scope per il prototipo).
    private static final int CURRICULUM_POSTSTAGE_TARGET_HOURS = 6;
    // Perimeter literals are the seed-CSV forms; curriculumSubjectSet normalizes them to the
    // canonical Title-Case form (Comportamento Sociale / Educazione Civica) that lessons carry at
    // runtime, and the probe is normalized too - so a case variant cannot silently un-bind the
    // perimeter (QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP precedent). Confirmed necessary by the run3
    // confirmation measure: the raw lowercase literals missed "Comportamento Sociale" (G10) and
    // "Educazione Civica" (G08) entirely.
    private static final Map<String, Set<String>> CURRICULUM_POSTSTAGE_SUBJECTS = Map.of(
            "G10", curriculumSubjectSet("Comportamento sociale"),
            "G08", curriculumSubjectSet("Competenze di cittadinanza", "Educazione civica"),
            "G11", curriculumSubjectSet("Personalizzazione"));

    private static Set<String> curriculumSubjectSet(String... subjects) {
        return Arrays.stream(subjects)
                .map(SubjectNormalization::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * <p><b>Origine.</b> "CON II FC E III OIT E III FA 6 ore dopo stage per curriculum (ANCHE 2 CONSECUTIVVE)" (workbook vincoli Spalmone, tab "Vincoli Docenti", riga 50). Target 6h asimmetrico sulle coorti finali (G10 / G08 / G11, T11); introdotto TASK-469.
     *
     * <p><b>ADOZIONE (maintainer, 2026-08-01).</b> DEFAULT ON con opt-out
     * {@code -Dplanner.constraints.curriculum-poststage.enabled=false} (stessa forma delle altre
     * ratifiche: {@code SchoolSchedule.isCurriculumPostStageEnabled} e' {@code null/absent -> true},
     * solo il letterale "false" disattiva). Evidenza A/B ab-cps-on-run1 (source=golden + combined
     * ratified flags + M-CPS ON vs combined-run2 OFF): hard 0 (=baseline, M-CPS e' medium), il
     * curriculum post-stage converge verso il target 6h (G10 12->9, G08 7->6 centra il target,
     * G11 24->21 con la cautela asimmetrica rispettata), costo zero sui KPI di guardia (full-day
     * +0,4pp, ricorrenza unpinned flat, buchi 0, medium neutro entro varianza). Banner in
     * {@code ScheduleService.refreshSolverStatics}. Doc: {@code task469-results/ab-cps-on-run1/}.
     */
    Constraint curriculumPostStage(ConstraintFactory constraintFactory) {
        boolean enabled = SchoolSchedule.isCurriculumPostStageEnabled();
        int fullWeight = readCadenceKnobProperty("planner.constraints.curriculum-poststage.full-weight", 100);
        int weakWeight = readCadenceKnobProperty("planner.constraints.curriculum-poststage.weak-weight", 1);
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> enabled
                        && l.timeslot != null
                        && l.getTeacher() != null && !l.getTeacher().pseudo
                        && isPerimeterCurriculumPostStage(l))
                // One match per perimeter group: the count of its post-stage curriculum hours.
                .groupBy(Lesson::getStudentGroup, sum(l -> 1))
                .penalize(ScoreCompat.ONE_MEDIUM,
                        (group, placed) -> curriculumPostStagePenalty(placed.intValue(), fullWeight, weakWeight))
                .asConstraint("M-CPS - Curriculum post-stage target 6h asymmetric");
    }

    /** M-CPS predicate: a lesson of a perimeter (group, curriculum-subject) placed after the
     *  group's INPUT-derived stage-end ({@link SchoolSchedule#lastStageDateFor}). The subject is
     *  matched in its normalized form (see {@link #CURRICULUM_POSTSTAGE_SUBJECTS}). */
    private static boolean isPerimeterCurriculumPostStage(Lesson l) {
        Set<String> subjects = CURRICULUM_POSTSTAGE_SUBJECTS.get(l.getStudentGroup().name);
        if (subjects == null || l.getSubject() == null) {
            return false;
        }
        if (!subjects.contains(SubjectNormalization.normalize(l.getSubject()))) {
            return false;
        }
        LocalDate stageEnd = SchoolSchedule.lastStageDateFor(l.getStudentGroup().id);
        return stageEnd != null && l.timeslot.getDate().isAfter(stageEnd);
    }

    /** M-CPS asymmetric penalty around the 6h target: full weight on the deficit (school wants
     *  ~6h post-stage), weak weight on the excess (G11 cautela: the course runs on for its
     *  own volume). Exactly at target = 0. The two sides are mutually exclusive around the
     *  target, so a single Math.max sum is equivalent to if/else branches. */
    private static int curriculumPostStagePenalty(int postStageHours, int fullWeight, int weakWeight) {
        return Math.max(0, CURRICULUM_POSTSTAGE_TARGET_HOURS - postStageHours) * fullWeight
                + Math.max(0, postStageHours - CURRICULUM_POSTSTAGE_TARGET_HOURS) * weakWeight;
    }

    // S11 - Student group room affinity (task-008)
    // Penalize student groups using multiple rooms per day for non-lab lessons
    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; una classe preferibilmente resta nella stessa aula per le lezioni non-lab (qualita'), introdotto task-008.
     */
    Constraint studentGroupRoomAffinity(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)
                // Exclude lessons without room assignment
                .filter(lesson -> lesson.room != null)
                // Exclude lab lessons (they have fixed room requirements via H-M2)
                .filter(lesson -> !lesson.isLab())
                // Exclude pinned lessons (pre-assigned by human planners; TASK-419:
                // authoritative ones only - countDistinct(room) is an occupancy count)
                .filter(lesson -> !isAuthoritativePin(lesson))
                // Group by student group and date, count distinct rooms
                .groupBy(
                        Lesson::getStudentGroup,
                        lesson -> lesson.timeslot.getDate(),
                        countDistinct(lesson -> lesson.room))
                // Penalize each additional room beyond the first
                .filter((studentGroup, date, distinctRoomCount) -> distinctRoomCount > 1)
                .penalize(ScoreCompat.ofSoft(10),
                        (studentGroup, date, distinctRoomCount) -> distinctRoomCount - 1)
                .asConstraint("S11 - Student group room affinity");
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private static boolean isAdjacent(Lesson a, Lesson b) {
        // Null-safe: an unassigned lesson has a null timeslot (allowsUnassigned), and Bavet's
        // incremental retraction can fire this joiner before filters exclude null entities.
        if (a.timeslot == null || b.timeslot == null) return false;
        return TimeslotSequence.isAdjacent(a.timeslot, b.timeslot);
    }

    // calculateGapMinutes(List<Lesson>) removed (TASK-243): Replaced by calculateBreakGapMinutes
    // for incremental score calculation compatibility with toConsecutiveSequences()

    // calculateBlockGapMinutes removed (TASK-228): LabBlock entity deprecated

    // ─── M20 - Daily Quota Fill (task-230, updated task-273) ──────────────────────

    /**
     * M20 - Daily quota fill — each used day must meet the group's daily quota.
     *
     * RATIONALE: The human planner fills every valid school day to the group's
     * daily quota (5 or 6 hours). A day below quota means the calendar has
     * underutilized capacity, which forces the year to extend further.
     *
     * APPROACH - Group by (StudentGroup, Date), count lesson hours. If total > 0
     * but < group's maxHoursPerDay (or 6 if null/unlimited), penalize shortfall
     * with the PROGRESSIVE weigher {@link #m20DeficitCost} (see its javadoc for
     * the calibration).
     *
     * WEIGHT - 1000 for the first missing hour (heavy medium - logical hard
     * constraint, implemented as medium for solver performance), +500 more for
     * each further missing hour on the same day.
     *
     * EXEMPTIONS:
     * - G14 group (task-010)
     * - Authoritative pins (pre-assigned by human planners) - exempt from the COUNT, and
     *   since TASK-424 subtracted from the TARGET as well ({@link #effectiveQuota}), so a
     *   mixed day is measured on one population against one capacity.
     */
    private static final int DEFAULT_DAILY_QUOTA = 6;

    /**
     * Cost of the FIRST missing hour of a partial day - unchanged from the
     * pre-progression flat weight, so the school's own "shaving" practice keeps
     * costing exactly what it did.
     */
    static final int M20_FIRST_HOUR_WEIGHT = 1000;

    /**
     * How much MORE each further missing hour of the same day costs than the
     * previous one (marginal cost 1000, 1500, 2000, 2500, ... - see
     * {@link #m20DeficitCost}).
     */
    static final int M20_PROGRESSION_STEP = 500;

    /**
     * M20's progressive deficit weigher - the cost of leaving {@code deficit}
     * hours unfilled on ONE (group, date), triangular in the depth:
     * {@code 1000*d + 250*d*(d-1)}, i.e. marginal cost 1000, 1500, 2000, 2500,
     * 3000, 3500, 4000 for the 1st..7th missing hour (cumulative 1000, 2500,
     * 4500, 7000, 10000, 13500, 17500).
     *
     * <p>WHY PROGRESSIVE (golden-first, 2026-07-26). The flat {@code 1000 x d}
     * weigher was SHAPE-BLIND: 30 deficit-hours cost the same spread as -1h over
     * 30 days or concentrated as -4h over 7 days plus change. The school's own
     * practice is unambiguously the former - measured on {@code seed/lessons.csv}
     * with M20's exact own {@link #dailyQuota} definition, the golden's 99
     * partial days are 98 at -1h, 1 at -2h and ZERO at -3h or worse. Our runs
     * binge-and-starve instead: smoke2 62/48/18/16 (-1/-2/-3/-4h+), val408-run1
     * 66/41/20/7. (The looser uncapped sheet-based KPI used by
     * {@code scripts/analyze/daily-fill-kpi.py} tells the same story: golden
     * 113/15/1, smoke2 73/56/22/40.) A progressive weigher prices exactly that
     * difference: the school's tolerated shave stays cheap, our deep holes get
     * expensive.
     *
     * <p>CALIBRATION (2026-07-26, over the 3 dumps above). {@code d=1} must cost
     * 1000 and not a cent more - the shave is accepted practice, so the golden's
     * aggregate M20 must not move (it goes 100,000 -> 100,500, +0.5%). Subject to
     * that, the step was chosen for discrimination against our own runs vs the
     * escalation it forces on M20.2 (below):
     * <pre>
     *   weigher                  golden   smoke2   val408   smoke2/gold  M20.2 needed
     *   flat 1000*d (before)    100,000  277,000  236,000      2.77x       1000/h
     *   +250d(d-1)  (CHOSEN)    100,500  378,000  307,500      3.76x       2188/h
     *   +500d(d-1)              101,000  479,000  379,000      4.74x       3500/h
     *   step 1000/2500          101,500  476,500  389,000      4.69x       2000/h
     * </pre>
     * {@code +250d(d-1)} (STEP=500) raises the deep-day (>=3h) share of M20's own
     * mass on smoke2 from 43% to 52% - the shape signal becomes the majority of
     * the constraint without a handful of outlier days dominating the medium
     * tier. The step function was rejected as not actually progressive (its
     * marginal cost is constant at 2500 from the 2nd hour on, so -5h vs -4h reads
     * the same as -2h vs -1h) for nearly the same M20.2 escalation; STEP=1000 was
     * rejected because a single -7h day would then cost 28,000, more than the
     * golden's 28 accepted shave-days put together, and it needs a 3.5x M20.2.
     *
     * <p><b>M20.2 MOVES WITH THIS CURVE (applied 2026-07-26).</b>
     * {@link #horizontallyCompactEmptyDay} prices an empty expected day at
     * {@code m20DeficitCost(quota)}, the same curve at full depth, and NOT the
     * linear {@code 1000 x quota} it used while this weigher was flat. The two
     * are one continuous cost function evaluated at different deficits, so they
     * can only be changed together: left linear, M20.2 would be undercut by this
     * progression for every quota >= 4 (a 1-hour day costs
     * {@code m20DeficitCost(quota-1)} = 4,500 at quota 4 and 17,500 at quota 8,
     * against an evacuation price of 4,000 / 8,000), paying the solver to CLEAR
     * near-empty days - the very inversion M20.2 was added (TASK-409) to close,
     * and a behaviour the school never exhibits (2 of its 2,213 expected
     * group-days are empty). Priced on the same curve, evacuation at quota 8
     * costs {@code m20DeficitCost(8)} = 22,000 against 17,500 and is always a
     * loss. The coherence costs one calibrated golden-guard pin: M20.2 -8000 / 2
     * matches -> -14500 / 2 (the golden's two real empty-expected days are a
     * quota-3 and a quota-5 one, 4,500 + 10,000), match count unchanged.
     */
    static int m20DeficitCost(long deficit) {
        // long in, int out: the caller's deficit comes from a Timefold sum()
        // collector (long), while a match weigher is int - and the value is
        // bounded by the largest quota (8 -> 17,500), so the narrowing is safe.
        if (deficit <= 0) {
            return 0;
        }
        return (int) (M20_FIRST_HOUR_WEIGHT * deficit
                + M20_PROGRESSION_STEP * deficit * (deficit - 1) / 2);
    }

    /**
     * task-010/Q16: G14 is exempt from day-fill (M20/M33) because it is a part-time
     * group whose open days are under-filled BY NATURE (calendar: only THU slots 0-4
     * and FRI 0-2; Mon/Tue/Wed closed). Without the exemption M20 would penalize that
     * structural under-fill during local search and distort the solve.
     *
     * Pre-TASK-462 this was documented as "990h sheet expectation vs 115h real demand";
     * TASK-462 made G14 120h real, so the part-time shape (not the hours mismatch) is
     * the true reason the exemption exists. (S1, 2026-07-30: removing it is a no-op at
     * construction - the builder is pre-score, this is a local-search-only exemption -
     * and whether it can be dropped needs a full-solve A/B, not the construction gate.)
     */
    private static boolean isG14(StudentGroup group) {
        return group != null && "G14".equals(group.name);
    }

    /**
     * TASK-496 REMOVED (maintainer, 2026-08-16): the G14 fill exemption was a patch on the
     * WRONG DATA (990h read from sheet 'G14' instead of 'G14 2' 120h, TASK-462).
     * With the corrected data the A/B was clean (hard 0 both arms, G14 full-day in band,
     * no new M20/M33 residual on G14): the patch is obsolete. The exemption form was
     * flag-gated default-ON for one night of measurement; this is the removal. The
     * identity predicate {@link #isG14} stays (it feeds other consumers); the FILL
     * family simply no longer consults it. Opt-in to the old behaviour with
     * {@code -Dplanner.constraints.g14-fill-exempt.enabled=true} (kept for rollback).
     */
    private static boolean fillExempt(StudentGroup group) {
        return isG14(group) && G14FillExemptEnabled();
    }

    /**
     * TASK-496 flag: default FALSE since the removal (2026-08-16). Only the explicit
     * literal {@code true} re-enables the legacy exemption (rollback path).
     */
    public static boolean G14FillExemptEnabled() {
        return Boolean.parseBoolean(System.getProperty("planner.constraints.g14-fill-exempt.enabled"));
    }

    /**
     * Effective daily quota for the group on this date: the group's
     * maxHoursPerDay (or DEFAULT_DAILY_QUOTA if unset), capped at however many
     * slots that group's calendar (TASK-342) actually has open that
     * weekday/date. Without this cap, a group with no maxHoursPerDay whose
     * calendar closes early on some weekday (e.g. a 5-slot Friday) could never
     * reach the DEFAULT_DAILY_QUOTA fallback and would be penalized every time
     * it's used that day, regardless of how full its schedule actually is.
     * A fully-closed day (0 open slots) yields quota 0, but such a day never
     * has lessons on it so the caller's totalHours > 0 guard already excludes it.
     *
     * <p>This is the RAW plan for the day. No constraint scores against it directly
     * any more: the day-fill family reads {@link #effectiveQuota} (this, net of the
     * school's own authoritative pinned hours - TASK-424), and the scope gate reads
     * {@link #m20Quota} (this, plus the null/G14 guards).
     */
    private static int dailyQuota(StudentGroup group, LocalDate date) {
        int openSlots = SchoolSchedule.getGroupCalendarOpenSlotCount(group.id, date.getDayOfWeek(), date);
        // TASK-385: the school's own per-(group,date) expected-hours fact
        // (calendario_class_days.csv / calendario_fc.csv, all 14 groups)
        // replaces the maxHoursPerDay/DEFAULT_DAILY_QUOTA fallback wherever
        // it's known - still capped by the calendar (Q16: G14's sheet gives
        // it days its Thu-Fri calendar doesn't have).
        //
        // aula+lab ONLY, never stageHours (code-review fix): totalHours below
        // is grouped from lessons filtered to !lesson.pinned, but Stage hours
        // are always placed as PINNED lessons (CalendarioImportService#planStagePins),
        // so they can never appear in totalHours - including them in the
        // target would compare two different kinds of hours (a real,
        // data-verified case: G08/2027-04-01 has aula=2, stage=4 in
        // calendario_fc.csv). STILL TRUE after TASK-419 widened M20's source to
        // "not an AUTHORITATIVE pin": the promotion rule mirrors M32's own
        // pseudo-teacher exemption, so a Stage lesson is never promoted and can
        // still never reach totalHours.
        DailyActivityQuota row = SchoolSchedule.getExpectedHoursRow(group.id, date);
        if (row != null) {
            // capRawHours min-chains the H11 cap (a sheet expectation above a group's hard
            // maxHoursPerDay can't produce an unreachable M20 target); capSupply applies the
            // TASK-461 supply cap (or the TASK-497 pool-capped target) on top.
            return capSupply(group, capRawHours(group, row.aulaHours + row.labHours, openSlots), date);
        }
        // TASK-408: for a sheet-covered group (all 14 in 2026/27), a date with no
        // fact row is a no-session (closed) day - target 0, NEVER the
        // maxHoursPerDay fallback. Without this, M20 rewarded filling stage/off-sheet
        // "phantom" days the golden always leaves empty (26 pairs / ~140h, the
        // dominant driver of the full-day gap - TASK-408 discovery §4).
        if (SchoolSchedule.isNoSessionDate(group.id, date)) {
            return 0;
        }
        int quota = group.maxHoursPerDay != null ? group.maxHoursPerDay : DEFAULT_DAILY_QUOTA;
        return capSupply(group, Math.min(quota, openSlots), date);
    }

    /**
     * The school's aula+lab expectation for a day, capped by the calendar (open slots) and by
     * the group's hard H11 daily max: the RAW capacity target, before TASK-424 pin-netting and
     * before the TASK-461 supply cap. Shared by {@link #dailyQuota} (the live target) and
     * {@link #computeSupplyCapRatios} (the yearly capacity sum) so the two cannot drift apart
     * if a further cap factor is ever added.
     */
    private static int capRawHours(StudentGroup group, int aulaLabHours, int openSlots) {
        int capped = Math.min(aulaLabHours, openSlots);
        if (group.maxHoursPerDay != null && group.maxHoursPerDay > 0) {
            capped = Math.min(capped, group.maxHoursPerDay);
        }
        return capped;
    }

    /**
     * TASK-461: scale the day's RAW capacity target by the group's supply-cap ratio, so the
     * yearly target never exceeds the curriculum hours the group actually has lessons for.
     * Applied in {@link #dailyQuota} (the single source the whole day-fill family derives
     * from) so M20, M33, M20.2 and every repair pricer inherit the cap from one place. A
     * no-op (returns the input) when the ratio is 1.0 (group at/under capacity, or the static
     * unset).
     *
     * <p>TASK-497 forma (b) override: when the pool-cap prototype is armed
     * ({@code planner.constraints.m20-cap-pool.enabled}) and a capped target is published
     * for this (group, date), that EXACT target replaces the ratio scaling. The ratio's
     * per-day {@code Math.round} leaves a yearly drift in both directions (G08 ratio
     * 0.940: no day drops 6 to 5, so the whole 40h floor survives; G04 ratio 0.898:
     * every 5-quota day drops to 4, overshooting to a 691h yearly target against a 750h
     * pool). The published map's sum IS the pool, so both defects collapse. The map is
     * built by {@link #computeM20PoolCapTargets} from inputs only (no golden).
     */
    private static int capSupply(StudentGroup group, int rawQuota, LocalDate date) {
        if (rawQuota <= 0) {
            return rawQuota;
        }
        if (SchoolSchedule.isM20CapPoolArmed()) {
            int capped = SchoolSchedule.getM20PoolCapTarget(group.id, date);
            if (capped >= 0) {
                return Math.min(rawQuota, capped);
            }
        }
        double ratio = SchoolSchedule.getSupplyCapRatio(group.id);
        return ratio >= 1.0d ? rawQuota : (int) Math.round(rawQuota * ratio);
    }

    /**
     * TASK-497 forma (b): the exact per-day M20 targets for one capacity-exceeds-curriculum
     * group, such that the yearly SUM equals the group's curriculum pool. Pure function of
     * inputs (the group's capped raw quotas by date, and the pool): deterministic, so it is
     * computed once pre-solve and published as a static, never mid-solve.
     *
     * <p>Allocation: subtract the shortfall ({@code capacity - pool}) ONE hour per day per
     * cyclic pass, days ordered thickest-quota-first (date ascending as the deterministic
     * tiebreak; the order is fixed once, later passes cycle it, so a second hour leaves a
     * day only after every day with room has given one). The SPREAD shape is a heuristic
     * placeholder modeled on the only evidenced practice, G04 in the golden: obligation
     * as the target, shortfall distributed -1h at a time (90 deficit days there, 85 of them
     * on quota rows = exactly this group's 835-750), never a concentrated tail. Other
     * over-capacity groups (G08 24h, G09 4h) have no evidenced distribution: if the
     * school's practice differs, the distribution belongs in the workbook (TASK-445), not
     * here.
     *
     * <p>A day MAY reach 0 (the loop's floor is 0, not 1): a group with fewer pool hours
     * than quota days (reachable mid-import: rows imported, lessons not yet generated, or
     * pool 0) then has whole days M20 asks nothing of - exactly what the TASK-461 ratio
     * path already does at ratio 0.0 - instead of an unreachable 1h/day floor. With floor
     * 0 the allocation always converges to sum = pool for any {@code 0 <= pool <= capacity}.
     *
     * <p>{@code null} when the group is not over-capacity (no cap needed: the TASK-461
     * ratio path already returns the raw quota unchanged) - publishing only the capped
     * groups keeps the map and the banner honest about who is affected.
     */
    static Map<LocalDate, Integer> computeM20PoolCapTargets(Map<LocalDate, Integer> cappedRawQuotaByDate,
                                                            int pool) {
        int capacity = cappedRawQuotaByDate.values().stream().mapToInt(Integer::intValue).sum();
        int shortfall = capacity - pool;
        if (shortfall <= 0) {
            return null;
        }
        Map<LocalDate, Integer> targets = new HashMap<>(cappedRawQuotaByDate);
        List<LocalDate> order = new ArrayList<>(cappedRawQuotaByDate.keySet());
        order.sort(Comparator.comparingInt((LocalDate d) -> targets.get(d)).reversed()
                .thenComparing(Comparator.naturalOrder()));
        while (shortfall > 0) {
            int before = shortfall;
            for (int i = 0; i < order.size() && shortfall > 0; i++) {
                LocalDate d = order.get(i);
                if (targets.get(d) > 0) {
                    targets.merge(d, -1, Integer::sum);
                    shortfall--;
                }
            }
            if (shortfall == before) {
                break;   // unreachable with floor 0 (pool >= 0 always representable); guard anyway
            }
        }
        return targets;
    }

    /**
     * TASK-461: the per-group M20 supply-cap ratio, published once pre-solve. For each
     * group, {@code capacity} = the yearly sum of {@link #dailyQuota} (the school's aula+lab
     * plan, capped by the calendar and H11 exactly as M20 sees it); {@code curriculum} = the
     * group's non-stage, non-G14 lesson count (the hours that genuinely exist to place).
     * Where {@code capacity > curriculum} M20's target is partly unfillable and the deficit
     * has an unreachable floor (G04 85h, G08 40h, G09 4h); the ratio
     * {@code curriculum/capacity} trims the plan to the curriculum so the floor collapses
     * (to within a few hours of per-day rounding drift, an ~98% reduction from G04's 85h).
     *
     * <p>The deficit floor is {@code capacity - curriculum} regardless of authoritative pins
     * (pin-netting subtracts equally from target and from the countable occupancy), so the
     * cap is applied to the RAW capacity here and the existing pin-netting in
     * {@link #effectiveQuota} stays consistent. Groups at/under capacity are omitted (the
     * getter returns 1.0 for them). Computed from inputs only (groups/lessons/quotas): no
     * golden, so it survives the year the golden disappears.
     */
    public static Map<Long, Double> computeSupplyCapRatios(List<StudentGroup> groups,
                                                           List<Lesson> lessons,
                                                           List<DailyActivityQuota> quotas) {
        Map<Long, Integer> curriculum = curriculumPoolByGroup(lessons);
        Map<Long, Map<LocalDate, Integer>> cappedByGroupDate = cappedRawQuotaByGroupDate(quotas);
        Map<Long, Double> ratios = new HashMap<>();
        for (StudentGroup g : groups) {
            Map<LocalDate, Integer> byDate = cappedByGroupDate.get(g.id);
            if (byDate == null) {
                continue;
            }
            int cap = byDate.values().stream().mapToInt(Integer::intValue).sum();
            int curr = curriculum.getOrDefault(g.id, 0);
            if (cap > 0 && curr < cap) {
                ratios.put(g.id, curr / (double) cap);
            }
        }
        return ratios;
    }

    /**
     * The per-group CURRICULUM POOL: each group's non-pseudo, non-G14 lesson count, the
     * hours that genuinely exist to place. The ONE counting both the TASK-461 ratio and the
     * TASK-497 pool-cap targets derive from: a copy of this loop is exactly how the two cap
     * paths would later disagree about the pool (one filter tightened, the other not).
     */
    private static Map<Long, Integer> curriculumPoolByGroup(List<Lesson> lessons) {
        Map<Long, Integer> curriculum = new HashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson == null || lesson.course == null || lesson.course.studentGroup == null) {
                continue;
            }
            StudentGroup g = lesson.course.studentGroup;
            if (fillExempt(g)) {
                continue;
            }
            Teacher teacher = lesson.getTeacher();
            if (teacher != null && teacher.pseudo) {
                continue;   // Stage pseudo-teacher: off-site, never part of the aula+lab plan
            }
            curriculum.merge(g.id, 1, Integer::sum);
        }
        return curriculum;
    }

    /**
     * The per-(group, date) CAPPED RAW quotas, one pass over the rows: the school's aula+lab
     * plan for each date, capped by the calendar (open slots) and the group's hard H11 max
     * via {@link #capRawHours} - the SAME cap {@link #dailyQuota} applies, so the yearly
     * capacity a ratio sums from this map is exactly what M20 targets. The TASK-497
     * pool-cap allocator consumes the same map directly, which is what makes the two cap
     * paths structurally unable to disagree about capacity.
     */
    private static Map<Long, Map<LocalDate, Integer>> cappedRawQuotaByGroupDate(
            List<DailyActivityQuota> quotas) {
        Map<Long, Map<LocalDate, Integer>> byGroupDate = new HashMap<>();
        for (DailyActivityQuota q : quotas) {
            if (q == null || q.studentGroup == null || fillExempt(q.studentGroup)) {
                continue;
            }
            int openSlots = SchoolSchedule.getGroupCalendarOpenSlotCount(
                    q.studentGroup.id, q.date.getDayOfWeek(), q.date);
            int capped = capRawHours(q.studentGroup, q.aulaHours + q.labHours, openSlots);
            if (capped > 0) {
                // SUM, not last-wins: the quota table has no unique key on (group, date) and
                // a re-exported sheet can repeat a row, and the pre-TASK-497 capacity count
                // this replaces merged duplicates - a silent semantic flip here would change
                // the flag-OFF ratio on such data.
                byGroupDate.computeIfAbsent(q.studentGroup.id, id -> new HashMap<>())
                        .merge(q.date, capped, Integer::sum);
            }
        }
        return byGroupDate;
    }

    /**
     * TASK-497 forma (b): the per-(group, date) pool-capped M20 targets for every
     * over-capacity group, published once pre-solve. Derives from the SAME
     * {@link #curriculumPoolByGroup} pool and {@link #cappedRawQuotaByGroupDate} capacity
     * map {@link #computeSupplyCapRatios} reads, so the ratio path and the exact-targets
     * path structurally cannot disagree about which groups are over-capacity or by how
     * much. Groups at/under capacity are absent from the map (their dailyQuota is the raw
     * quota in both paths). Returns a possibly-empty map, never null.
     */
    public static Map<Long, Map<LocalDate, Integer>> computeM20PoolCapTargetsByGroup(
            List<StudentGroup> groups, List<Lesson> lessons, List<DailyActivityQuota> quotas) {
        Map<Long, Integer> curriculum = curriculumPoolByGroup(lessons);
        Map<Long, Map<LocalDate, Integer>> cappedByGroupDate = cappedRawQuotaByGroupDate(quotas);
        Map<Long, Map<LocalDate, Integer>> targets = new HashMap<>();
        for (StudentGroup g : groups) {
            Map<LocalDate, Integer> byDate = cappedByGroupDate.get(g.id);
            if (byDate == null) {
                continue;   // (G14 never reaches here: cappedRawQuotaByGroupDate skips it)
            }
            Map<LocalDate, Integer> capped = computeM20PoolCapTargets(
                    byDate, curriculum.getOrDefault(g.id, 0));
            if (capped != null) {
                targets.put(g.id, capped);
            }
        }
        return targets;
    }

    /**
     * The day-fill family's SCOPE gate: is this (group, date) a day the school plans hours on
     * at all? The school's own expected hours for that day, capped by the calendar and by H11,
     * and 0 wherever the day-fill constraints do not apply (G14's Q16 exemption, a no-session
     * date, a stage-only day, a fully closed day). Deliberately the RAW target, never {@link
     * #effectiveQuota}: a day whose whole plan is already covered by the school's own pins is
     * still very much a day in scope (an extra solver hour there IS surplus, M33 must see it) -
     * only a day the plan asks NOTHING of is out of scope. Exposed package-private for the
     * deterministic repairs' REPORTING - a day at quota 0 is not a genuine surplus day, it is a
     * day M20 never asked anything of (TASK-422 D5) - so a counter can tell the two apart
     * without re-deriving the quota rules.
     */
    static int m20Quota(StudentGroup group, LocalDate date) {
        if (group == null || group.id == null || date == null || fillExempt(group)) {
            return 0;
        }
        return dailyQuota(group, date);
    }

    /**
     * TASK-424 - the day-fill family's real target: the day's expected hours MINUS the
     * authoritative real-teacher hours the school already pinned on it, floored at 0. The
     * single source of truth for every target read by M20, M20.2, M33 and the repair pricers
     * ({@link #m20DayCost} and its two marginals), so the deficit side, the surplus side and
     * the repairs can never price the same day against different targets.
     *
     * <p>WHY (the pin-blindness this closes). The occupancy those constraints count is
     * pinned-EXEMPT by tradition ({@link BaseConstraintProvider#isAuthoritativePin}), but the
     * target used to be the day's FULL plan - so on a mixed day the two sides measured
     * different populations and the difference was pure incentive to overfill. Measured on
     * val419-train-run1: 267 (group, date) days carry at least one non-stage authoritative pin
     * and EVERY one of them reported an M20 deficit that does not exist; the dominant pattern
     * (137 days) is expected 6 / pinned 3 / unpinned 3, i.e. exactly on plan, yet M20 charged
     * {@code m20DeficitCost(3)} = 4,500 and PAID 2,000 of marginal gain for a 4th unpinned
     * hour. The mirror cost: 29 of the 112 genuinely above-plan days were invisible to M33.
     * Netting the pins makes "3 of 6, and 3 of them are the school's" read as what it is - a
     * day at target, with nothing owed and nothing free.
     *
     * <p>Not netted, by construction: PROMOTED pins (TASK-419) stay in the occupancy count, so
     * netting them too would double-count them, and PSEUDO-teacher (Stage) pins, which were
     * never part of this aula+lab-only target. Both exclusions live once, in the map this
     * reads: {@code SchoolSchedule#authoritativePinnedHoursByGroupDateStatic}.
     */
    static int effectiveQuota(StudentGroup group, LocalDate date) {
        int quota = m20Quota(group, date);
        if (quota <= 0) {
            return 0;
        }
        return Math.max(0, quota - SchoolSchedule.getAuthoritativePinnedHours(group.id, date));
    }

    /**
     * The M20 + M20.2 medium cost of ONE (group, date) at a given occupancy - the day-fill
     * COST MODEL, exposed package-private so the deterministic repairs ({@link BoxRepair},
     * {@link HoleRepair}) can price a move on the same curve the score charges it on, instead
     * of re-deriving the quota rules (the anti-drift rule this file applies everywhere).
     *
     * <p>The two constraints are ONE continuous function of the day's occupancy, see {@link
     * #m20DeficitCost}: M20 charges {@code m20DeficitCost(quota - occupancy)} on a day with at
     * least one countable hour, M20.2 charges the same curve at FULL depth on an expected day
     * with no lesson at all. The two guards that make this exact rather than approximate are
     * both mirrored here: M20's own {@code totalHours > 0} filter (a day whose only lessons are
     * authoritative pins is charged nothing) and M20.2's fact-driven {@code aula + lab > 0}
     * scope (a day with no {@link DailyActivityQuota} row cannot be an "empty expected day").
     *
     * @param occupancy   M20's own {@code totalHours}: the placed lessons of that (group, date)
     *                    that are NOT authoritative pins - the exact population
     *                    {@link #horizontallyCompact}'s {@code !isAuthoritativePin} filter counts
     * @param placedTotal every placed lesson of that (group, date), authoritative pins INCLUDED -
     *                    M20.2's deliberately different "is this day empty" basis
     */
    static int m20DayCost(StudentGroup group, LocalDate date, int occupancy, int placedTotal) {
        // TASK-424: the target is the day's plan NET of the school's own pinned hours -
        // the same target M20/M33 score against, so a repair prices a move exactly as the
        // score charges it. effectiveQuota carries the null/G14/out-of-scope guards.
        int quota = effectiveQuota(group, date);
        if (quota <= 0) {
            return 0;
        }
        if (occupancy <= 0) {
            if (placedTotal > 0) {
                return 0;   // M20's totalHours > 0 guard, M20.2's ifNotExists sees the pins
            }
            DailyActivityQuota row = SchoolSchedule.getExpectedHoursRow(group.id, date);
            return row != null && row.aulaHours + row.labHours > 0 ? m20DeficitCost(quota) : 0;
        }
        return m20DeficitCost(quota - occupancy);
    }

    /**
     * The medium SAVED by adding one countable hour to (group, date) - the marginal value of
     * the deepest missing hour of that day (0 on a day already at or above quota, 1000 on a day
     * one hour short; a 7-hours-short PARTIAL day gains m20DeficitCost(7)-m20DeficitCost(6) =
     * 4000, and the curve's true maximum of 4500 = 22000-17500 is the first hour of an EMPTY
     * quota-8 expected day, the M20.2 full-depth case below). The ranking key of {@link
     * BoxRepair}'s direct pass: with a progressive {@link #m20DeficitCost} the right target is
     * the DEEPEST hole, not merely the first day that has one.
     *
     * <p>Two consequences of the curve's real shape, both faithful and both deliberate: the
     * BEST target is an empty EXPECTED day (M20.2 prices it at full depth, so its first hour
     * is the dearest hour on the board), while an empty day with NO fact row scores a NEGATIVE
     * gain (M20.2 cannot see it, but the moment one hour lands M20 charges the whole fallback
     * quota's deficit) - i.e. opening an unplanned day really does cost medium, so the repair
     * tops up an open day instead. Both are the score talking, not an artifact of this helper.
     */
    static int m20MarginalFillGain(StudentGroup group, LocalDate date, int occupancy, int placedTotal) {
        return m20DayCost(group, date, occupancy, placedTotal)
                - m20DayCost(group, date, occupancy + 1, placedTotal + 1);
    }

    /**
     * The medium ADDED by taking one countable hour away from (group, date) - the mirror of
     * {@link #m20MarginalFillGain}: 0 when the day is above quota (a surplus hour is free to
     * lend), 1000 when it is exactly at quota, and up to 4500 when it is already deep. The
     * source-day term of {@link HoleRepair}'s donor pricing.
     */
    static int m20MarginalRemovalCost(StudentGroup group, LocalDate date, int occupancy, int placedTotal) {
        return m20DayCost(group, date, occupancy - 1, placedTotal - 1)
                - m20DayCost(group, date, occupancy, placedTotal);
    }

    // TASK-423 - single lambda instances for the day-fill population's own two
    // filters, so BAVET's node sharing can collapse M20's and M33's identical
    // prefix into ONE filter chain (distinct call-site lambdas never compare
    // equal, so inlining them twice would build the chain twice). The other two
    // pieces are already shared instances of BaseConstraintProvider's own:
    // HAS_TIMESLOT (via timeslottedLessons) and LESSON_DATE.
    private static final Predicate<Lesson> NOT_AUTHORITATIVE_PIN = lesson -> !isAuthoritativePin(lesson);
    private static final Predicate<Lesson> NOT_G14 = lesson -> !fillExempt(lesson.getStudentGroup());

    /**
     * The day-fill family's SHARED population: how many hours the solver itself
     * has parked on each (group, date) - the single occupancy count M20 reads
     * from below its target ({@link #horizontallyCompact}) and M33 reads from
     * above it ({@link #surplusBeyondExpectedHours}).
     *
     * <p>ONE stream, not two restatements, for two reasons that point the same
     * way. SEMANTICS: the two constraints price the same day from opposite
     * sides, so any divergence in who counts (a lesson that counts for the
     * deficit but not for the surplus) would be pure arbitrage for the solver -
     * sharing the prefix makes that divergence structurally impossible instead
     * of merely documented. PERFORMANCE: BAVET shares a node only when the
     * lambdas compare equal by identity, so building the chain from these shared
     * instances collapses the filters AND the incrementally-maintained groupBy
     * into a single node evaluated once per move rather than twice.
     *
     * <p>Who counts: every timeslotted lesson that is NOT an authoritative pin
     * (TASK-419 - a promoted pin is still an ordinary hour of occupancy, see
     * {@link BaseConstraintProvider#isAuthoritativePin}), excluding G14
     * (task-010/Q16). The target both sides compare against is {@link
     * #dailyQuota}, likewise a single source of truth.
     */
    private TriConstraintStream<StudentGroup, LocalDate, Long> dailyLessonOccupancy(
            ConstraintFactory constraintFactory) {
        // TASK-225 - timeslottedLessons uses forEachIncludingUnassigned to bypass
        // isInitializedPredicate, which was filtering out all Lesson entities.
        // The occupancy is a Long: sum's only single-argument overload takes a
        // ToLongFunction (which is also why m20DeficitCost's parameter is long).
        return timeslottedLessons(constraintFactory)
                .filter(NOT_AUTHORITATIVE_PIN)
                .filter(NOT_G14)
                .groupBy(Lesson::getStudentGroup, LESSON_DATE, sum(l -> 1));
    }

    /**
     * <p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota: ore attese aula+lab per (gruppo, data)); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Target netto dei pin autoritativi dal TASK-424; introdotto TASK-385.
     */
    Constraint horizontallyCompact(ConstraintFactory constraintFactory) {
        return dailyLessonOccupancy(constraintFactory)
                .filter((group, date, totalHours) -> {
                    if (group == null) return false;
                    // TASK-424: against the target NET of the school's own pinned hours -
                    // see effectiveQuota (a mixed day at plan is not a deficit day).
                    int quota = effectiveQuota(group, date);
                    return totalHours > 0 && totalHours < quota;
                })
                // TASK-419 follow-up: PROGRESSIVE in the depth of the hole, not
                // flat 1000/hour - the golden shaves (98 of its 99 partial days
                // are -1h), our runs binge-and-starve. See m20DeficitCost.
                .penalize(ScoreCompat.ONE_MEDIUM,
                        (group, date, totalHours) -> m20DeficitCost(effectiveQuota(group, date) - totalHours))
                .justifyWith((group, date, totalHours, score) -> new SparseDayJustification(
                        group.id,
                        date,
                        totalHours,
                        effectiveQuota(group, date)
                ))
                .asConstraint("M20 - Daily quota fill");
    }

    /**
     * M20.2 - Empty expected day (TASK-409, found by the golden-vs-run visual
     * comparator). M20 above is Lesson-sourced with {@code totalHours > 0}, so a
     * day the school EXPECTS hours on that is left with NO lessons at all was
     * structurally invisible - and perversely CHEAPER than a 1h day (a 1h day
     * costs (quota-1) x 1000, an evacuated day cost 0), rewarding the solver
     * for emptying near-empty days. M22 doesn't catch it either: its span is
     * derived from PLACED lessons, so tail days beyond a group's compressed
     * last placement are outside every net. The same structural hole was
     * already closed for FC lab days by M30.2's fact-driven ifNotExists; this
     * is that pattern applied to M20's own quota.
     *
     * <p>Weight: {@code m20DeficitCost(dailyQuota)} - the exact limit of M20's
     * partial-day gradient at zero placed hours, so the cost curve is continuous
     * and evacuating a day can never be cheaper than leaving one hour on it. An
     * empty day is not a small deficit: it is a deficit at FULL depth, so it must
     * be priced there. The two weighers are ONE curve evaluated at different
     * depths and can only be changed together - why, what a linear residue here
     * would re-open, and the golden-guard pin it costs are all documented once,
     * at {@link #m20DeficitCost}.
     *
     * <p>Semantics deliberately DIFFERENT from M20's pinned handling: M20 counts
     * unpinned lessons only (pinned-exempt), but for "is this day empty" ANY
     * lesson counts - a day fully covered by pinned lessons is genuinely not
     * empty ({@code timeslottedLessons} includes pinned). G14 is exempt like
     * M20 (task-010: its 990h sheet expectation vs 115h real demand would make
     * every G14 day "guilty"). Stage-only days (aula+lab = 0) are excluded on
     * the same stage-exclusive basis dailyQuota itself uses (TASK-385).
     *
     * <p>Golden measurement: the golden leaves only ~17 expected days empty in
     * the whole year (its own real gaps - exam days etc.), pinned as a
     * calibrated residual in GoldenSolutionRegressionTest, NOT zero-tolerance.
     *
     * <p><b>Origine.</b> Calendario per-gruppo della scuola (un giorno atteso con ore>0 non puo' restare vuoto); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Introdotto TASK-409.
     */
    Constraint horizontallyCompactEmptyDay(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(DailyActivityQuota.class)
                .filter(q -> q.studentGroup != null && !fillExempt(q.studentGroup))
                .filter(q -> q.aulaHours + q.labHours > 0)
                .ifNotExists(timeslottedLessons(constraintFactory),
                        Joiners.equal(q -> q.studentGroup, Lesson::getStudentGroup),
                        Joiners.equal(q -> q.date, LESSON_DATE))
                // 2026-07-26: the PROGRESSIVE extension of M20's own curve, not the
                // linear 1000 x quota - an empty day is a deficit at full depth, and
                // pricing it below m20DeficitCost(quota) would pay the solver to
                // evacuate. See m20DeficitCost and this method's javadoc.
                .penalize(ScoreCompat.ONE_MEDIUM,
                        // TASK-424: the same net target M20 uses, for one cost curve rather
                        // than two. A day with no lesson at all has no pin either, so this is
                        // numerically identical to the raw quota here (the golden's own two
                        // empty expected days keep costing 4,500 + 10,000) - it is written net
                        // so the two ends of the curve cannot drift apart if pins ever appear
                        // on a lesson-free day.
                        q -> m20DeficitCost(effectiveQuota(q.studentGroup, q.date)))
                .asConstraint("M20.2 - Empty expected day");
    }

    // ─── M33 - Surplus beyond expected day hours (TASK-423) ───────────────────

    /**
     * What ONE hour parked above a (group, date)'s expected hours costs.
     *
     * <p>CALIBRATION (start-low, TASK-423). The school's own practice IS to go
     * above the sheet sometimes: measured on the golden, 70 (group, date) days
     * sit above their expected hours - the physiological valve for the dates
     * where the sheet under-declares. Our runs park on 126-136 such days. So the
     * rule must DISCOURAGE surplus, never FORBID it (the golden-practice-
     * legitimizes directive: never stricter than the practice the school
     * demonstrates), which is what a light medium does and a hard tier could not.
     *
     * <p>100 puts a surplus hour at one TENTH of the FIRST deficit hour M20
     * charges (1000, {@link #M20_FIRST_HOUR_WEIGHT}): enough to break a tie
     * toward a legal deficit day when the solver is otherwise indifferent about
     * where to park an hour, not enough to fight a hard constraint or an M32
     * template pull (up to 500/lesson) - so a surplus hour that BUYS something
     * real is still taken. Knob for benchmark, like M32's own numerator.
     */
    static final int M33_SURPLUS_WEIGHT = 100;

    /**
     * M33 - Surplus beyond expected day hours (TASK-423) - the mirror image of
     * M20 above: M20 charges the hours MISSING from a day's expectation, M33
     * charges the hours parked ABOVE it.
     *
     * <p>RATIONALE (human-planner rule, maintainer): never above the day's capacity,
     * the rest waits on the bench. The CONSTRUCTION already obeys it (the stamp
     * caps each day at its expected hours, TASK-385), the LOCAL SEARCH did not,
     * because nothing in the score noticed a day above plan: M20 stops at zero
     * once the day is full, so an extra hour there was FREE while the day it came
     * from paid nothing back. Hence 126-136 days above expectation in our runs
     * against the golden's 70.
     *
     * <p>POPULATION - literally {@link #horizontallyCompact}'s, the SAME stream:
     * both read {@link #dailyLessonOccupancy}, one occupancy count priced on both
     * sides of the same target. Sharing it (rather than restating the filters
     * here) makes a divergence between the deficit's counters and the surplus's
     * counters structurally impossible - the solver has nothing to arbitrage -
     * and lets BAVET maintain ONE groupBy node incrementally for both. So
     * authoritative pins are exempt on both (see {@link #isAuthoritativePin}: a
     * promoted pin is still an ordinary hour), G14 is exempt on both
     * (task-010/Q16), and the target is {@link #effectiveQuota}, the SAME single
     * source of truth - never a re-derivation. Since TASK-424 that target is the
     * day's plan NET of the school's own authoritative pinned hours, so the two
     * sides finally measure the same population against the same capacity.
     *
     * <p>DAYS AT QUOTA 0 ARE OUT OF SCOPE ({@code m20Quota > 0} gate, the RAW
     * plan - never the net one, see the gate's own comment). A no-session
     * date, a fully closed day and a stage-only day all resolve to quota 0
     * (TASK-408/TASK-385), so without the gate EVERY hour on them would read as
     * surplus - and on a day M20 asks nothing of, "surplus" has no meaning to
     * begin with (the same reading {@link #m20Quota}'s javadoc already fixed for
     * TASK-422's reporting). The three cases are NOT equally covered elsewhere,
     * and the gate is deliberate rather than merely redundant:
     * <ul>
     *   <li>A ROWLESS no-session date is genuinely double-covered: H26 forbids a
     *       real-teacher lesson there at the HARD tier and the VRP filter never
     *       offers the timeslot, so charging medium on top would only add noise
     *       to a hard violation.</li>
     *   <li>A STAGE-ONLY row (aula+lab = 0, {@code stageHours > 0}) also resolves
     *       to quota 0 but has NO such guarantee: the date HAS a fact row, so
     *       {@code isNoSessionDate} is false and H26 says nothing about it.
     *       Ordinary hours parked there are exempted here with no hard backstop -
     *       measured 12 lesson-hours over 3 days on train-run1, which are the
     *       documented 2027-04-23/26 sheet-vs-golden conflict (the sheet books 2
     *       FC stage days the golden teaches through, CLAUDE.md "Daily activity
     *       quotas"). Small and explained, so left out of scope rather than
     *       priced blind; if that conflict is ever resolved the other way, this
     *       gate is where it surfaces.</li>
     * </ul>
     *
     * <p>Golden measurement: 0 / 0, structural - the golden fixture is 100%
     * pinned, so the population is empty exactly as it is for M20/M22/M28. The
     * calibration against the school's real 70 above-days lives in the daily-fill
     * KPI's {@code sopra} count, not in the guard.
     *
     * <p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota expected-hours, target netto dei pin TASK-424); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Il golden stesso sfora ~70 giorni/anno, quindi e' penalty leggera non ban; introdotto TASK-423.
     */
    Constraint surplusBeyondExpectedHours(ConstraintFactory constraintFactory) {
        return dailyLessonOccupancy(constraintFactory)
                .filter((group, date, totalHours) -> {
                    if (group == null) return false;
                    // TASK-424: TWO different quotas, deliberately. The SCOPE gate is the raw
                    // plan (m20Quota > 0 - "is this a day the school plans hours on at all"),
                    // while the surplus is measured against the NET target (effectiveQuota):
                    // on a day whose whole plan is already covered by the school's own pins the
                    // raw quota is positive and the net one is 0, and every solver hour parked
                    // there is surplus - gating on the net target instead would exempt exactly
                    // the 31 fully-pinned days this task set out to stop the LS from doubling.
                    return m20Quota(group, date) > 0 && totalHours > effectiveQuota(group, date);
                })
                .penalize(ScoreCompat.ONE_MEDIUM,
                        (group, date, totalHours) ->
                                M33_SURPLUS_WEIGHT * (int) (totalHours - effectiveQuota(group, date)))
                .asConstraint("M33 - Surplus beyond expected day hours");
    }

    // ─── M22 - Calendar Compactness (task-273) ────────────────────────────────

    /**
     * M22 - Calendar compactness — FORBID empty school days within each group's
     * used calendar span (first lesson date to last lesson date).
     *
     * RATIONALE: H17 ensures no gaps WITHIN a day, and M20 ensures each day meets
     * the daily quota. M22 ensures no empty days between the first and last
     * lesson — the calendar must be a solid contiguous block. This matches the
     * human planner's approach: fill every valid day from day 1 until all hours
     * are placed.
     *
     * APPROACH - For each student group, compute the calendar span (firstDate →
     * lastDate) and count distinct days actually used. Any empty day within
     * the span is a hard violation.
     *
     * TIER: MEDIUM since TASK-377 (was scored ONE_HARD despite the M-prefixed id —
     * the mismatch flagged in TASK-376). The old "spalmone confirms feasible"
     * hard-tier claim had no calibration evidence: this constraint exempts pinned
     * lessons, so on the all-pinned golden fixture it is vacuous, and in the first
     * real 2026/27 solve the solver simply absorbed -71 hard from it, meaning the
     * hard tier provided pressure without feasibility semantics. Weight 1000/excess
     * day matches M20's sparse-day scale (an empty day within the span is about as
     * bad as a sparse day).
     *
     * EXEMPTIONS:
     * - Pinned lessons (pre-assigned by human planners)
     * - G14 group (small group, many empty days expected)
     *
     * <p><b>Origine.</b> Pratica del golden, nessun documento scuola: la scuola costruisce un blocco compatto di giorni di lezione (dal primo all'ultimo, senza giorni vuoti in mezzo). Introdotto task-273.
     */
    Constraint calendarCompact(ConstraintFactory constraintFactory) {
        // SIMPLIFIED (2026-03-19): Replaced compose(min, max, countDistinct) with
        // countDistinct + count. The old approach was O(n) per entity placement during
        // CH (full group rescan for min/max/countDistinct), causing CH to take 300s+
        // and only place ~4,600 of 10,550 entities. It also had a weekend bug where
        // DAYS.between counted Sat/Sun as phantom empty days.
        //
        // New approach: sparseDays = actualDays - ceil(placedHours / dailyQuota).
        // Uses two simple incremental collectors instead of compose(3 complex ones).
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)
                // TASK-419: both collectors below are OCCUPANCY counts (days used, hours
                // placed), so promoted pins must keep counting - a day covered by the frozen
                // template IS occupied, and excluding it would let the tail open brand-new
                // days at no M22 cost (see isAuthoritativePin's javadoc). Authoritative pins
                // stay exempt as before.
                .filter(lesson -> !isAuthoritativePin(lesson))
                // G14 group exempt (small hours, many empty days expected)
                .filter(lesson -> !fillExempt(lesson.getStudentGroup()))
                .groupBy(Lesson::getStudentGroup,
                        countDistinct((Lesson l) -> l.timeslot.getDate()),
                        count())
                .filter((group, actualDays, placedHours) -> actualDays > m22MinDays(group, placedHours))
                .penalize(ScoreCompat.ofMedium(1000),
                        (group, actualDays, placedHours) -> actualDays - m22MinDays(group, placedHours))
                .asConstraint("M22 - Calendar compactness");
    }

    /**
     * M22's minimum number of days for {@code placedHours}. Legacy (TASK-558 flag
     * off, now opt-OUT only): {@code ceil(hours / maxHoursPerDay)} - a
     * packing assumption the 25/08 analysis proved a FICTION on this dataset
     * (the sheet's own declared quotas are thinner than maxHoursPerDay, so the
     * unpinned-equivalent golden itself would pay 262 excess days under it).
     * Amended (flag {@code planner.constraints.m22.quota-aware.enabled},
     * RATIFIED DEFAULT ON, maintainer 2026-08-27): {@code ceil(hours / average declared
     * aula+lab quota)} - when the placed hours match the sheet's pool this
     * degenerates to exactly the number of days the school declared, so a
     * solution faithful to the declared plan is no longer "too thin". Groups
     * with no quota rows (synthetic fixtures) keep the legacy form.
     *
     * <p>The flag is read PER CALL (not captured at constraint-build time):
     * ConstraintVerifier reuses streams built by earlier sessions in the same
     * JVM, so a build-time capture freezes whichever flag value the first
     * session saw (proven by a red test under the harness). The per-call
     * System.getProperty costs a synchronized Hashtable lookup per group-row
     * match (~0.1% wall, quantified in the TASK-558 /simplify review): accepted
     * permanently - the refreshSolverStatics capture pattern (m20CapPoolArmed)
     * refreshes only on the service path, so a captured read would go stale
     * under the verifier harness (see isM22QuotaAwareEnabled's javadoc).
     */
    static int m22MinDays(StudentGroup group, long placedHours) {
        int quota = group.maxHoursPerDay != null ? group.maxHoursPerDay : DEFAULT_DAILY_QUOTA;
        int legacy = (int) Math.ceil((double) placedHours / quota);
        if (!SchoolSchedule.isM22QuotaAwareEnabled()) {
            return legacy;
        }
        Double averageDeclared = SchoolSchedule.getAverageDeclaredOrdinaryQuota(group.id);
        if (averageDeclared == null || averageDeclared <= 0) {
            return legacy;
        }
        return (int) Math.ceil(placedHours / averageDeclared);
    }

    // countInternalGaps(List<Lesson>) removed (TASK-243): Replaced by countBreakSlots
    // for incremental score calculation compatibility with toConsecutiveSequences()

    // ─── Break-based Gap Calculation (incremental-friendly) ─────────────────
    // TASK-243 - These methods work with Break objects from toConsecutiveSequences()
    // to maintain the same behavior as the original toList() approach while
    // enabling incremental score calculation.

    /**
     * Calculate gap minutes from a break between two consecutive sequences.
     * This replaces calculateGapMinutes(List<Lesson>) for incremental compatibility.
     *
     * The break contains the last lesson of the previous sequence and the first
     * lesson of the next sequence. We calculate the gap between them.
     */
    private static int calculateBreakGapMinutes(Break<Lesson, Integer> break_) {
        Lesson prev = break_.getPreviousSequenceEnd();
        Lesson next = break_.getNextSequenceStart();

        // Null-safe: an unassigned lesson has a null timeslot (allowsUnassigned)
        if (prev.timeslot == null || next.timeslot == null) return 0;
        int gap = (int) Duration.between(prev.timeslot.endDateTime, next.timeslot.startDateTime).toMinutes();
        if (gap > 0) {
            if (TimeslotSequence.isLunchBreakWithinGap(prev.timeslot.endDateTime, next.timeslot.startDateTime)) {
                gap -= TimeslotSequence.LUNCH_BREAK_MINUTES;
            }
            if (gap > 0) {
                return gap;
            }
        }
        return 0;
    }

    /**
     * Count slot gaps from a break between two consecutive sequences.
     * This replaces countInternalGaps(List<Lesson>) for incremental compatibility.
     *
     * The break's length represents the difference in slot indices between
     * consecutive sequences. We subtract 1 because a difference of 1 means
     * consecutive slots (no gap).
     *
     * Note: This counts slot gaps only, not minutes. It does NOT handle
     * structural breaks (11:30-14:30 lunch) - those are handled in
     * calculateBreakGapMinutes for H15 and S1 which work with minutes.
     */
    private static int countBreakSlots(Break<Lesson, Integer> break_) {
        int length = break_.getLength();
        // Break length is always >= 1 for actual breaks (consecutive sequences
        // have no break between them). Subtract 1 to get gap count.
        return length - 1;
    }
}
