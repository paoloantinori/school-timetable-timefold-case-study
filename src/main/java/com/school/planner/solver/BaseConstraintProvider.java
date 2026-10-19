package com.school.planner.solver;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.PrecomputeFactory;
import ai.timefold.solver.core.api.score.stream.bi.BiConstraintStream;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;

import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.count;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.countBi;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.toList;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.ProfessionalizzantiDay;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.constraints.RoomConflictJustification;
import com.school.planner.domain.constraints.StudentGroupConflictJustification;
import com.school.planner.domain.constraints.TeacherConflictJustification;
import com.school.planner.util.SubjectNormalization;

/**
 * Abstract base class containing sparse-compatible constraints.
 *
 * These constraints evaluate per-entity or per-pair and produce correct results
 * even when only a subset of planning entities is present (sparse placement).
 * Used by both Phase 1 (pre-assignment) and Phase 2 (full solving).
 *
 * Note: This class does NOT implement ConstraintProvider directly to avoid
 * Timefold Quarkus auto-detection conflicts when multiple providers exist.
 * Concrete subclasses implement ConstraintProvider.
 *
 * SIMPLIFIED (TASK-228): LabBlocks are now represented as pinned Lessons.
 * All LabBlock-specific constraints have been removed.
 *
 * Constraints included:
 * - H1: Teacher conflict (Lesson-Lesson only)
 * - H2: Room conflict (Lesson-Lesson only)
 * - H3: Student group conflict (Lesson-Lesson only, same course included)
 * - H4: Stage blackout (Lesson only)
 * - H5: Teacher unavailability (Lesson only)
 * - H6: School holiday (Lesson only)
 * - H14: Student group start date (Lesson only)
 * - H-M2: Room suitability (Lesson only)
 * - H20: Group calendar closed slot (Lesson only, TASK-342)
 * - H23/H24: Daily activity quota safety net (Lesson only, TASK-381 "L1-bis")
 * - H25: Stage lesson outside stage days safety net (Lesson only, TASK-384)
 * - M28: Afternoon slots preferably reserved for stage (Lesson only; was H21, TASK-372/379)
 * - M-LA.3: Lab assistant conflict (Lesson-Lesson only)
 * - M3: Room capacity (Lesson only)
 * - M5: Reward Informatica after stage
 * - M12: Overflow beyond target end date (Lesson only)
 * - H30 (was M26): Sicurezza before stage (Lesson only, TASK-369)
 * - M30/M31: Daily activity quota fill + contiguity (TASK-381 "L1-bis")
 */
public abstract class BaseConstraintProvider {

    // M5 target groups (MODA and FA groups for Informatica after stage)
    private static final Set<String> M5_TARGET_GROUPS = Set.of(
            "G01", "G05", "G09",
            "G02", "G06", "G10", "G12");

    // H30 (was M26) subject constant (T03, "tutte le ore della materia 'Sicurezza'
    // prima dell'inizio dello stage della classe", vincoli-in-parole.md
    // TASK-369). A single hardcoded subject, not a generic
    // subject-before-stage rule table - generalize by widening this to a
    // Set<String>/Map<String, LocalDate-comparator> if a second "subject X
    // before stage" rule is ever needed.
    // package-private: deterministic repairs (FinalClosingCampaign's donor
    // filter) reuse the same constant instead of restating the subject name
    static final String SICUREZZA_SUBJECT = "Sicurezza";

    // ─── Shared stream sources (TASK-377) ─────────────────────────────────────

    // Single lambda instances so BAVET node sharing collapses every use of the
    // same predicate/key into one stream node (distinct call-site lambdas never
    // compare equal, so inlining these 6x would build 6 redundant filter nodes).
    private static final java.util.function.Predicate<Lesson> HAS_TIMESLOT =
            l -> l.timeslot != null;
    private static final java.util.function.Predicate<Lesson> REAL_TEACHER =
            l -> l.getTeacher() != null && !l.getTeacher().pseudo;
    // L1-bis (TASK-381): lesson belongs to a quota group (G04/G08/G13).
    // Single instance shared by H23/H24's prefix and M31.
    private static final java.util.function.Predicate<Lesson> QUOTA_GROUP =
            l -> l.getStudentGroup() != null && SchoolSchedule.isQuotaGroup(l.getStudentGroup().id);
    // L1-bis (TASK-381): quota fact for a day with lab hours budgeted.
    // Single instance so M30.1/M30.2 share one forEach(DailyActivityQuota) filter node.
    // TASK-385: forEach(DailyActivityQuota.class) reads EVERY row in the
    // problem facts list (the new all-groups expected-hours rows included) -
    // the FCQuota guard keeps M30.1/M30.2 scoped to the original 3 FC
    // groups exactly as before that data was added (see DailyActivityQuota#FCQuota).
    private static final java.util.function.Predicate<DailyActivityQuota> POSITIVE_LAB_HOURS =
            q -> q.FCQuota && q.labHours > 0;
    protected static final java.util.function.Function<Lesson, LocalDate> LESSON_DATE =
            l -> l.timeslot.getDate();

    /**
     * Lessons that have a timeslot, regardless of room assignment.
     *
     * ALWAYS use this (never forEach) as the source of a timeslot-based Lesson
     * constraint: forEach(Lesson.class) iterates only fully initialized entities,
     * and the 5-phase pipeline assigns rooms only in phase 4, so a forEach-based
     * timeslot constraint is silently inert for every timeslot phase (TASK-377 —
     * this exact mistake let the first 2026/27 solve stack 6,343 same-slot
     * lessons unpenalized). forEach remains legitimate only for room constraints
     * (H2, H16b), where a room-null lesson has nothing to conflict over.
     */
    protected UniConstraintStream<Lesson> timeslottedLessons(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(HAS_TIMESLOT);
    }

    /**
     * Timeslotted lessons taught by a real (non-pseudo) teacher — H1's source.
     * Protected (not private) so subclasses (e.g. S17 in
     * SchoolScheduleConstraintProvider, TASK-381 "L3") reuse this instead of
     * re-filtering on an inline lambda that would build a redundant BAVET
     * filter node alongside {@link #REAL_TEACHER}'s shared instance.
     */
    protected UniConstraintStream<Lesson> realTeacherTimeslottedLessons(ConstraintFactory constraintFactory) {
        return timeslottedLessons(constraintFactory).filter(REAL_TEACHER);
    }

    /**
     * TASK-419 - single source of truth for the pinned-exemption family: is this
     * lesson an AUTHORITATIVE pin, i.e. a placement the SCHOOL fixed (golden
     * import, calendario/MODA pins, stage pins, lab rotations) rather than one the
     * solve itself froze?
     *
     * <p>The staged solve (TASK-419) promotes every unpinned lesson already sitting
     * on its own M32 template cell to {@code pinned=true} for stage 1, and releases
     * those pins again at the end of the cycle. Such a PROMOTED pin is immobile for
     * Timefold but carries no statement from the school, so every constraint that
     * exempts pinned lessons must keep scoring it exactly as it scored it one moment
     * earlier, when it was unpinned. Two failures follow from not doing this, both
     * measured or reasoned:
     * <ul>
     *   <li>OCCUPANCY GOES BLIND. M20's day-fill count and M22's used-day count are
     *       built from {@code !pinned} lessons; promoting 6,959 lessons made M20 start
     *       at -3.23M instead of the -1.07M the identical grid scores unpinned, i.e.
     *       the LS was actively pushed to re-fill days that were already full
     *       (TASK-419 spike E1, checkpoint-experiments-2026-07-25 §E1). The same
     *       blindness at day level (M29/M31) would make a template-covered day exempt
     *       until the first free lesson lands on it, then penalize the WHOLE day's
     *       shape - a cliff that repels the tail from exactly the days it should fill.</li>
     *   <li>THE SCORE JUMPS AT SMARCATURA. Any exemption granted to a promoted pin is
     *       silently withdrawn when the pin is released, so a stage-1 "hard = 0" would
     *       stop being true after the release - the class of pin-accounting bug
     *       TASK-383 already cost us once.</li>
     * </ul>
     *
     * <p>Hence: {@code pinned && !promoted}. With the registry empty (production
     * today, and every non-staged solve) this is exactly {@code l.pinned}, so all of
     * the callers below are byte-identical to their pre-TASK-419 form.
     *
     * <p>Also consulted by {@code ScheduleService.countPinnedExemptGaps} (the UI
     * drill-down mirror of M15/H17's break-level exemption), hence {@code public}:
     * the drill-down must never disagree with the score it explains.
     *
     * <p>NOT applied to M32 itself ({@code templateDeviation}, {@code
     * SchoolScheduleConstraintProvider}): a promoted lesson is ON its template cell
     * by the very definition of the promotion rule, so {@code deviatesFromTemplate}
     * is false for it and M32 scores 0 whether it is exempted or evaluated. Keeping
     * the plain {@code !l.pinned} filter there avoids pushing thousands of provably
     * non-matching lessons through the deviation lookup for no semantic gain.
     */
    public static boolean isAuthoritativePin(Lesson lesson) {
        return lesson.pinned && !SchoolSchedule.isPromoted(lesson.id);
    }

    /**
     * Unpinned (or TASK-419-promoted, see {@link #isAuthoritativePin}), real-teacher
     * lessons for a quota group (G04/G08/G13) - the shared prefix of H23/H24
     * (TASK-381 "L1-bis"), which differ only in which half of the daily quota (lab vs
     * aula) they check via {@link DailyActivityQuota#permits}.
     */
    private UniConstraintStream<Lesson> quotaGroupUnpinnedLessons(ConstraintFactory constraintFactory) {
        return realTeacherTimeslottedLessons(constraintFactory)
                .filter(l -> !isAuthoritativePin(l))
                .filter(QUOTA_GROUP);
    }

    // ─── Precompute helpers (cached entity-fact pairs on static join keys) ────

    // H4 - Lesson × StagePattern pairs for non-professionalizzanti courses
    // TASK-272 - Now respects per-group allowProfessionalizzantiDuringStage setting
    // TASK-366 - Excludes pseudo-teacher lessons (e.g. the synthetic Stage course
    // itself): a pinned Stage lesson trivially falls inside its own group's stage
    // window/day-mask, so without this exclusion every pinned Stage lesson
    // self-violates its own blackout (694 spurious H4 hits in the golden audit).
    // Same intent as the pseudo-teacher exclusions H1/H7/S1 already apply, but
    // deliberately keeps (rather than drops) a null-teacher lesson: unlike those
    // three, H4 has nothing to do with teacher identity, so a real non-prof
    // lesson that happens to lack a teacher must still respect the blackout,
    // not be silently exempted.
    private BiConstraintStream<Lesson, StagePattern> nonProfLessonStagePairs(
            PrecomputeFactory precomputeFactory) {
        return precomputeFactory.forEachUnfiltered(Lesson.class)
                .filter(l -> l.course != null && (
                    !l.course.professionalizzanti ||
                    !Boolean.TRUE.equals(l.getStudentGroup().allowProfessionalizzantiDuringStage)
                ))
                .filter(l -> l.getTeacher() == null || !l.getTeacher().pseudo)
                .join(StagePattern.class,
                        Joiners.equal(Lesson::getStudentGroup, sp -> sp.studentGroup));
    }

    // M5 - Informatica Lesson × StagePattern pairs for target groups
    private BiConstraintStream<Lesson, StagePattern> informaticaStagePairs(
            PrecomputeFactory precomputeFactory) {
        return precomputeFactory.forEachUnfiltered(Lesson.class)
                .join(StagePattern.class,
                        Joiners.equal(Lesson::getStudentGroup, sp -> sp.studentGroup))
                .filter((lesson, sp) -> {
                    String subject = lesson.getSubject();
                    return subject != null
                            && subject.equals("Informatica")
                            && M5_TARGET_GROUPS.contains(lesson.getStudentGroup().name);
                });
    }

    // H30 (was M26) - Sicurezza Lesson x StagePattern pairs (same precompute-join shape as M5 above).
    // Course.subject has no NOT NULL constraint (CourseResource copies the REST payload
    // verbatim, same caveat as M21's own null-subject guard) - the null check must
    // short-circuit before normalize(), which NPEs on a null key (Map.of() rejects it).
    private BiConstraintStream<Lesson, StagePattern> sicurezzaStagePairs(
            PrecomputeFactory precomputeFactory) {
        return precomputeFactory.forEachUnfiltered(Lesson.class)
                .filter(l -> l.getSubject() != null
                        && SICUREZZA_SUBJECT.equals(SubjectNormalization.normalize(l.getSubject())))
                .join(StagePattern.class,
                        Joiners.equal(Lesson::getStudentGroup, sp -> sp.studentGroup));
    }

    /**
     * Returns all sparse-compatible constraints.
     * Subclasses call this to include the base constraint set.
     */
    protected Constraint[] getBaseConstraints(ConstraintFactory constraintFactory) {
        List<Constraint> constraints = new ArrayList<>();

        // ── Hard constraints ──
        // H0 - Unassigned lesson — every lesson MUST have a timeslot
        constraints.add(unassignedLesson(constraintFactory));
        // H1 - Teacher conflict (Lesson-Lesson)
        constraints.add(teacherConflict(constraintFactory));
        // H2 - Room conflict (Lesson-Lesson)
        constraints.add(roomConflict(constraintFactory));
        // H3 - Student group conflict (Lesson-Lesson, same course included)
        constraints.add(studentGroupConflict(constraintFactory));

        // H4 - Stage blackout (Lesson only)
        constraints.add(stageBlackout(constraintFactory));
        // H4.1 - Professionalizzanti constant single-day during stage
        constraints.add(profSingleDayDuringStage(constraintFactory));
        // H5 - Teacher unavailability (Lesson only)
        constraints.add(teacherUnavailability(constraintFactory));
        // H6 - School holiday (Lesson only)
        constraints.add(schoolHoliday(constraintFactory));
        // H14 - Student group cannot have lessons before their start date
        constraints.add(studentGroupStartDate(constraintFactory));
        // H-M2 - Lab course must be in correct lab room
        constraints.add(roomSuitability(constraintFactory));
        // H20 - Group calendar closed slot (TASK-342, safety net — VRP filtering
        // already excludes closed slots, this catches any pinned/legacy lesson
        // that slipped through)
        constraints.add(groupCalendarClosedSlot(constraintFactory));
        // H23/H24 - Daily activity quota safety net (TASK-381 "L1-bis") — same
        // VRP-prunes/constraint-catches relationship to Lesson.getAvailableTimeslots()'s
        // quota filter that H20 has to its own GroupCalendarRule VRP filter.
        constraints.add(quotaBoundLabLessonOnZeroLabQuotaDay(constraintFactory));
        constraints.add(aulaLessonOnZeroAulaQuotaDay(constraintFactory));
        // H25 - Stage lesson outside stage days safety net (TASK-384) — same
        // VRP-prunes/constraint-catches relationship to Lesson.getAvailableTimeslots()'s
        // Stage confinement filter that H20/H23/H24 have to their own VRP filters.
        constraints.add(stageLessonOutsideStageDays(constraintFactory));
        // H26 - Lesson on no-session day safety net (TASK-408) — same
        // VRP-prunes/constraint-catches relationship to Lesson.getAvailableTimeslots()'s
        // no-session filter that H20/H23/H24/H25 have to their own VRP filters.
        constraints.add(lessonOnNoSessionDay(constraintFactory));
        // ── Medium constraints (sparse-compatible) ──
        // Excluded in pre-assignment mode: Phase 1 only needs hard feasibility.
        // Medium score can distract the solver from hard score progress.
        if (!SchoolScheduleConstraintProvider.isPreAssignmentMode()) {
            // M-LA.3 - Lab assistant conflict (Lesson-Lesson only)
            constraints.add(labAssistantConflictLessonLesson(constraintFactory));
            // M3 - Room capacity (Lesson only)
            constraints.add(roomCapacity(constraintFactory));
            // M5 - Reward Informatica after stage
            constraints.add(rewardInformaticaAfterStage(constraintFactory));
            // M12 - Overflow beyond ideal end date (Lesson only)
            constraints.add(overflowBeyondTargetEndDate(constraintFactory));
            // M26 - Sicurezza before stage (T03, TASK-369)
            constraints.add(sicurezzaBeforeStage(constraintFactory));
            // M28 - Afternoon slots preferably reserved for stage (was H21, TASK-379)
            constraints.add(afternoonSlotsReservedForStage(constraintFactory));
            // M39 - Ordinary lesson on a stage-only day (TASK-604): the residual
            // [-11] classification's preference - see its own javadoc.
            constraints.add(ordinaryOnStageOnlyDay(constraintFactory));
            // M30/M31 - Daily activity quota fill + single contiguous run (TASK-381 "L1-bis")
            constraints.add(labDayQuotaFillPresent(constraintFactory));
            constraints.add(labDayQuotaFillAbsent(constraintFactory));
            constraints.add(labDaySingleContiguousRun(constraintFactory));
        }

        return constraints.toArray(new Constraint[0]);
    }

    // ─── H1 Teacher Conflict ────────────────────────────────────────

    /**
     * H1 - Teacher conflict (Lesson-Lesson only).
     * A teacher cannot hold two lessons at once — including two hours of the
     * same course stacked into one slot (TASK-377 removed the lab-session
     * allowance: with 1-hour half-open slots it only ever matched same-slot
     * stacking, never legitimate consecutive block hours).
     *
     * Pseudo teachers (TASK-346, e.g. "Stage") are excluded: different groups'
     * pinned pseudo-lessons legitimately overlap in the same slot without any
     * real person being double-booked. Both sides of the join share the same
     * teacher, so checking one side is sufficient.
     *
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; un docente non puo' essere in due aule contemporaneamente (fisico, introdotto fin dalle origini).
     */
    Constraint teacherConflict(ConstraintFactory constraintFactory) {
        // TASK-377: sourced from timeslottedLessons (see its javadoc — forEach
        // would silently disable this for all timeslot phases). The pseudo-teacher
        // exclusion is pushed into the source: every group's pinned Stage lessons
        // share ONE pseudo Teacher entity, so filtering post-join would index
        // O(K^2) doomed pairs per (Stage, date). The date joiner keeps the pair
        // count per (teacher, day) instead of per teacher-year.
        return realTeacherTimeslottedLessons(constraintFactory)
                .join(realTeacherTimeslottedLessons(constraintFactory),
                        Joiners.equal(Lesson::getTeacher),
                        Joiners.equal(LESSON_DATE),
                        Joiners.lessThan(l -> l.id))
                .filter((a, b) -> timeslotsOverlap(a, b))
                .penalize(ScoreCompat.of(1000, 0, 0))
                .justifyWith((a, b, score) -> new TeacherConflictJustification(
                        a.course.teacher.id,
                        a.id,
                        b.id
                ))
                .asConstraint("H1 - Teacher conflict");
    }

    // ─── H2 Room Conflict ───────────────────────────────────────────

    /**
     * H2 - Room conflict (Lesson-Lesson only).
     *
     * Deliberately stays on forEach() (initialized entities only, TASK-377):
     * a lesson without a room cannot conflict over one, so skipping room-null
     * lessons is vacuous here — unlike H1/H3, where it silenced real conflicts.
     *
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; un'aula ospita una sola classe alla volta (fisico, introdotto fin dalle origini).
     */
    Constraint roomConflict(ConstraintFactory constraintFactory) {
        // TASK-464: capture once per build, not per pair. isCoPresenceEnabled() reads an
        // uncached System.getProperty, and H2 is the hottest hard stream (Lesson x Lesson
        // join), so reading it per pair would call System.getProperty on that stream for no
        // score benefit. Capturing mirrors M-LF/M-TC/H-COPRESENCE, and lets the JIT fold the
        // opted-out case (!false && ...) to eliminate the filter entirely.
        boolean coPresenceEnabled = SchoolSchedule.isCoPresenceEnabled();
        return constraintFactory.forEach(Lesson.class)
                .join(Lesson.class,
                        Joiners.equal(l -> l.room),
                        Joiners.equal(LESSON_DATE),
                        Joiners.lessThan(l -> l.id))
                .filter((a, b) -> !a.getStudentGroup().equals(b.getStudentGroup()))
                .filter((a, b) -> timeslotsOverlap(a, b))
                // TASK-464 co-presenza ospite: exempt a golden-practiced guest pair from
                // H2 so the guest may legally share the host's room. Default ON since the
                // 2026-08-01 ratification; opted out (`-Dplanner.co-presenza.enabled=false`)
                // `!(false && ...)` is `true`, so the filter passes every pair (byte-identical
                // to the pre-ratification behavior). With the flag ON the pair is exempt ONLY
                // when both lessons carry the same single co-presenza tag AND the
                // (tag, weekday, slot) cell is golden-practiced (depth >= 2, AC#4 guard).
                // The separate H-COPRESENCE count cap backstops this: exempting the pairwise
                // penalty alone would authorize 6+ in a room, so the cap re-imposes the
                // golden depth as the ceiling. See SchoolScheduleConstraintProvider.coPresenceCap.
                .filter((a, b) -> !(coPresenceEnabled && isCoPresenceGuestPair(a, b)))
                .penalize(ScoreCompat.of(1000, 0, 0))
                .justifyWith((a, b, score) -> new RoomConflictJustification(
                        a.room.id,
                        a.id,
                        b.id
                ))
                .asConstraint("H2 - Room conflict");
    }

    /**
     * TASK-464: true iff the (a, b) pair is a golden-practiced co-presenza guest pair that
     * H2 should exempt - both lessons share one single co-presenza tag and the cell
     * {@code (tag, weekday, slotStart)} is golden-practiced (depth {@code >= 2}). The pair
     * shares weekday + slotStart by construction: H2 only joins lessons with equal room and
     * equal date whose timeslots overlap, and 1-hour half-open slots overlap iff same slot.
     */
    boolean isCoPresenceGuestPair(Lesson a, Lesson b) {
        String tag = singleCoPresenceTag(a);
        if (tag == null) {
            return false;
        }
        if (!tag.equals(singleCoPresenceTag(b))) {
            return false;
        }
        // a.timeslot is non-null here: the LESSON_DATE joiner reads it, so a lesson with a
        // null timeslot never enters the H2 stream.
        return SchoolSchedule.isCoPresenceCell(tag, a.timeslot.getDayOfWeek(), a.timeslot.startDateTime.toLocalTime());
    }

    /** TASK-464: the single co-presenza room tag of a lesson's course, or {@code null} if
     *  the course carries zero or multiple tags (only single-tag lab courses - the seed's
     *  only shape - can be co-presenza, matching the H-COPRESENCE cap's filter). */
    private String singleCoPresenceTag(Lesson l) {
        if (l.course == null || l.course.requiredRoomTags == null || l.course.requiredRoomTags.size() != 1) {
            return null;
        }
        return l.course.requiredRoomTags.get(0);
    }

    // ─── H3 Student Group Conflict ──────────────────────────────────

    /**
     * H3 - Student group conflict (Lesson-Lesson only).
     * A group cannot attend two lessons at once — including two hours of the
     * SAME course: with 1-hour half-open timeslots, two lessons only ever
     * overlap when they sit in the same slot, so the old same-course and
     * same-lab-session allowances exempted exactly the stacking exploit
     * (36 G14 hours piled into 3 slots, first 2026/27 solve) and no
     * legitimate pair. See TASK-377.
     *
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; una classe non puo' essere in due luoghi contemporaneamente (fisico, introdotto fin dalle origini).
     */
    Constraint studentGroupConflict(ConstraintFactory constraintFactory) {
        return timeslottedLessons(constraintFactory)
                .join(timeslottedLessons(constraintFactory),
                        Joiners.equal(l -> l.course.studentGroup),
                        Joiners.equal(LESSON_DATE),
                        Joiners.lessThan(l -> l.id))
                .filter((a, b) -> timeslotsOverlap(a, b))
                .penalize(ScoreCompat.of(1000, 0, 0))
                .justifyWith((a, b, score) -> new StudentGroupConflictJustification(
                        a.getStudentGroup().id,
                        a.id,
                        b.id
                ))
                .asConstraint("H3 - Student group conflict");
    }

    /**
     * Shared helper: checks if two lessons have overlapping timeslots.
     * Uses half-open intervals [start, end) — touching timeslots (e.g. 08:00-09:00
     * and 09:00-10:00) do NOT overlap. A teacher finishing at 09:00 can start at 09:00.
     * Returns false if either timeslot is null (null-safe).
     */
    private boolean timeslotsOverlap(Lesson a, Lesson b) {
        if (a.timeslot == null || b.timeslot == null) return false;
        return a.timeslot.startDateTime.isBefore(b.timeslot.endDateTime)
                && b.timeslot.startDateTime.isBefore(a.timeslot.endDateTime);
    }

    // ─── H4 Stage Blackout ──────────────────────────────────────────

    /**
     * <p><b>Origine.</b> Vincolo strutturale dello stage (studenti fuori sede): le materie fondamentali non possono correre nei giorni di stage. I giorni di stage vengono dal calendario per-gruppo (seed/calendario_*.csv); introdotto TASK-381 (stage on the grid).
     */
    Constraint stageBlackout(ConstraintFactory constraintFactory) {
        // Precomputed: Lesson × StagePattern join on studentGroup (static).
        // Only the timeslot-dependent date check runs per move.
        return constraintFactory.precompute(this::nonProfLessonStagePairs)
                .filter((lesson, sp) -> lesson.timeslot != null)
                .filter((lesson, sp) -> {
                    LocalDate d = lesson.timeslot.getDate();
                    return !d.isBefore(sp.startDate) && !d.isAfter(sp.endDate)
                            && sp.dayOfWeekMask.contains(d.getDayOfWeek());
                })
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H4 - Stage blackout");
    }

    // ─── H4.3 Professionalizzanti Single-Day During Stage ────────────────

    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; derivato dalla struttura dello stage (una classe in stage tiene le professionalizzanti in un solo giorno), introdotto TASK-381.
     */
    Constraint profSingleDayDuringStage(ConstraintFactory constraintFactory) {
        // H4.3 - During stage periods, professionalizzanti courses should be on the
        // group's designated professionalizzanti day-of-week, read from the
        // ProfessionalizzantiDay entity (generalized 2026-07-15 from the hardcoded
        // "G07 only, Monday" special-case). Groups without a ProfessionalizzantiDay
        // record are not enforced (no clear pattern in spalmone). The stage period
        // still comes from StagePattern. O(n) via indexed equal-joiners (no pair join).
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)
                .filter(l -> l.course != null && l.course.professionalizzanti)
                .join(ProfessionalizzantiDay.class,
                        Joiners.equal(Lesson::getStudentGroup, pd -> pd.studentGroup))
                .join(StagePattern.class,
                        Joiners.equal((Lesson l, ProfessionalizzantiDay pd) -> l.getStudentGroup(),
                                sp -> sp.studentGroup))
                .filter((l, pd, sp) -> {
                    // Only enforce within the group's stage period, on stage days
                    LocalDate d = l.timeslot.getDate();
                    return !d.isBefore(sp.startDate) && !d.isAfter(sp.endDate)
                            && sp.dayOfWeekMask.contains(d.getDayOfWeek());
                })
                // Penalize if NOT on the group's designated professionalizzanti day
                .filter((l, pd, sp) -> !l.timeslot.getDate().getDayOfWeek().equals(pd.dayOfWeek))
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H4.3 - Professionalizzanti constant single-day during stage");
    }

    // ─── H5 Teacher Unavailability ──────────────────────────────────

    /**
     * <p><b>Origine.</b> Vincolo strutturale (un docente non e' schedulabile nei suoi slot non disponibili); i dati di disponibilita' vengono dal workbook vincoli Spalmone, tab "Vincoli Docenti" (es. riga 72: "T10 | Libero il Govedì  mattina per LES e G14  9,00-17,00"), seed come TeacherAvailability VACATION.
     */
    Constraint teacherUnavailability(ConstraintFactory constraintFactory) {
        // H5 constraint uses indexed Joiners.overlapping() (TASK-181 benchmarked 2026-03-06).
        // Compared precompute() cache vs overlapping(): scores were virtually identical
        // (within 1-6% variance = statistically insignificant for 10min runs).
        // Kept overlapping() for being the simpler, idiomatic Timefold approach.
        // See - claudedocs/benchmark-h5-20260306-1317.md
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)
                .join(TeacherAvailability.class,
                        Joiners.equal(Lesson::getTeacher, ta -> ta.teacher),
                        Joiners.overlapping(l -> l.timeslot.startDateTime, l -> l.timeslot.endDateTime,
                                ta -> ta.startDateTime, ta -> ta.endDateTime))
                .filter((l, ta) -> ta.type == AvailabilityType.SICK || ta.type == AvailabilityType.VACATION)
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H5 - Teacher unavailability");
    }

    // ─── H6 School Holiday ──────────────────────────────────────────

    /**
     * <p><b>Origine.</b> Vincolo strutturale (nessuna lezione nei giorni di chiusura); le date vengono dal calendario scolastico (DataGenerator.CLOSURES_2026_27: Natale, Pasqua, Carnevale, festivita' nazionali), seed come SchoolHoliday.
     */
    Constraint schoolHoliday(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)
                .join(SchoolHoliday.class,
                        Joiners.equal(l -> l.timeslot.getDate(), h -> h.date))
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H6 - School holiday");
    }

    // ─── H14 Student Group Start Date ───────────────────────────────

    // H14 - Lesson cannot be scheduled before student group's start date
    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; niente lezioni prima della data di inizio del gruppo (gruppi che partono dopo), introdotto fin dalle origini.
     */
    Constraint studentGroupStartDate(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> lesson.getStudentGroup() != null
                        && lesson.getStudentGroup().startDate != null
                        && lesson.timeslot != null
                        && lesson.timeslot.getDate().isBefore(lesson.getStudentGroup().startDate))
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H14 - Student group start date");
    }

    /**
     * H0 - Unassigned lesson penalty.
     * Every lesson MUST have a timeslot assigned. Without this constraint,
     * the solver has zero incentive to assign lessons and actually benefits
     * from leaving them unassigned (no teacher/room/student conflicts).
     *
     * <p>Weight 2 (not {@link ScoreCompat#ONE_HARD}) since TASK-447: leaving a lesson
     * UNASSIGNED must be a strictly WORSE hard move than leaving it PLACED-but-room-null
     * ({@code H29}, weight 1). Otherwise the solver would de-assign a lesson (escape every
     * room/teacher conflict) for the same hard price as letting it sit room-null, which is the
     * arbitrage that emptied the schedule when room-required was hard at parity (the bocciato
     * H27, see SchoolScheduleConstraintProvider). {@code -2 (H0) > -1 (H29)} removes it: a room
     * is always worth assigning. BREAKS the historical invariant {@code hard == boxed-count}
     * (now {@code hard == boxed*2 + room-null*1 + conflicts}); the staged cycle's
     * {@code timeslotTierHardScore} excludes the room-tier families (H2/H-M2/H29) so its
     * timeslot-phase score stays about placement, not room assignment.
     *
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; una lezione non collocata e' la violazione piu' elementare (introdotto fin dalle origini del modello).
     */
    protected Constraint unassignedLesson(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> lesson.timeslot == null)
                .penalize(ScoreCompat.ofHard(2))
                .asConstraint("H0 - Unassigned lesson");
    }

    // ─── H-M2 Room Suitability ──────────────────────────────────────

    // H-M2 - Lab course must be assigned to a room with matching tags
    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; un corso di laboratorio va in un'aula col tag corrispondente (fisico). Assunzione ancora da confermare con la scuola: Disegno meccanico (meccanica) e Disegno edile (informatica) trattati come lab. Introdotto fin dalle origini.
     */
    Constraint roomSuitability(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.course != null && l.course.requiredRoomTags != null
                        && !l.course.requiredRoomTags.isEmpty()
                        && l.room != null && !l.room.tags.containsAll(l.course.requiredRoomTags))
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H-M2 - Room suitability");
    }

    // ─── H20 Group Calendar Closed Slot ─────────────────────────────

    // H20 - Lesson placed in a slot closed by its student group's calendar (TASK-342).
    // GroupCalendarRule VRP filtering (Lesson.getAvailableTimeslots()) already keeps
    // the solver from proposing these placements; this is the hard-constraint safety
    // net for pinned lessons or any other path that bypasses the value range.
    /**
     * <p><b>Origine.</b> Calendario per-gruppo della scuola (GroupCalendarRule: slot aperti/chiusi per weekday); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo, seed calendar. Rete di sicurezza (il solver evita gia' gli slot chiusi per costruzione); introdotto TASK-342.
     */
    Constraint groupCalendarClosedSlot(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> lesson.timeslot != null && lesson.getStudentGroup() != null)
                .filter(lesson -> !SchoolSchedule.isGroupCalendarOpen(lesson.getStudentGroup().id, lesson.timeslot))
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H20 - Group calendar closed slot");
    }

    // ─── H23/H24 Daily Activity Quota Safety Net (TASK-381 "L1-bis") ────

    /**
     * H23 - Quota-bound lab lesson on a zero-lab-quota day.
     *
     * Safety net twin of {@code Lesson.getAvailableTimeslots()}'s L1-bis VRP
     * filter (same VRP-prunes/constraint-catches relationship H20 has to its
     * own GroupCalendarRule VRP filter): the VRP already keeps the solver
     * from PROPOSING these placements, this hard constraint catches any
     * pinned lesson or other path that bypasses the value range.
     * Pinned-exempt per-entity (same shape as M20/M28) - the golden's own 8
     * (G04) / 8 (G08) / 4 (G13) "stray" bound-lab lessons on zero-quota
     * days are all pinned (historical import), so this measures 0/0 against
     * {@code GoldenSolutionRegressionTest}.
     *
     * <p>{@link DailyActivityQuota#isQuotaBoundLabSubject} and {@link
     * SchoolSchedule#isQuotaGroup}/{@link SchoolSchedule#getDailyQuota} are
     * the exact same predicates the VRP filter uses - single source of
     * truth, see {@code DailyActivityQuota}'s class javadoc.</p>
     *
     * <p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota.labHours per G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Rete di sicurezza; introdotto TASK-381 (L1-bis).
     */
    Constraint quotaBoundLabLessonOnZeroLabQuotaDay(ConstraintFactory constraintFactory) {
        return quotaGroupUnpinnedLessons(constraintFactory)
                .filter(l -> DailyActivityQuota.isQuotaBoundLabSubject(l.getStudentGroup().name, l.getSubject()))
                .filter(l -> !DailyActivityQuota.permits(
                        SchoolSchedule.getDailyQuota(l.getStudentGroup().id, l.timeslot.getDate()), true))
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H23 - Quota-bound lab lesson on zero-lab-quota day");
    }

    /**
     * H24 - Aula (ordinary, non-quota-bound-lab) lesson on a zero-aula-quota day.
     *
     * Same safety-net/pinned-exemption shape as H23 above, for the OTHER half
     * of the L1-bis VRP filter: any lesson for a quota group that ISN'T a
     * quota-bound lab subject, placed on a day whose quota row has
     * {@code aulaHours == 0} (or no row at all).
     *
     * <p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota.aulaHours per G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Rete di sicurezza; introdotto TASK-381 (L1-bis).
     */
    Constraint aulaLessonOnZeroAulaQuotaDay(ConstraintFactory constraintFactory) {
        return quotaGroupUnpinnedLessons(constraintFactory)
                .filter(l -> !DailyActivityQuota.isQuotaBoundLabSubject(l.getStudentGroup().name, l.getSubject()))
                .filter(l -> !DailyActivityQuota.permits(
                        SchoolSchedule.getDailyQuota(l.getStudentGroup().id, l.timeslot.getDate()), false))
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H24 - Aula lesson on zero-aula-quota day");
    }

    // ─── H25 Stage Lesson Outside Stage Days Safety Net (TASK-384) ──────

    /**
     * H25 - Stage lesson outside stage days.
     *
     * Safety net twin of {@code Lesson.getAvailableTimeslots()}'s Stage
     * confinement VRP filter (same VRP-prunes/constraint-catches
     * relationship H20/H23/H24 have to their own VRP filters): the VRP
     * already keeps the solver from PROPOSING these placements for an
     * unpinned pseudo "Stage" lesson, this hard constraint catches any
     * pinned lesson or other path that bypasses the value range.
     * Pinned-exempt per-entity (same shape as H23/H24/M20/M28) - the golden
     * import pins every Stage lesson, so this measures 0/0 against {@code
     * GoldenSolutionRegressionTest}.
     *
     * <p>{@link Lesson#isStageConfined()} and {@link DailyActivityQuota#isStageDate}
     * are the exact same predicates the VRP filter uses - single source of
     * truth, see their own javadocs.</p>
     *
     * <p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota.stageHours: le date con stage); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Confinamento dello stage; introdotto TASK-384.
     */
    Constraint stageLessonOutsideStageDays(ConstraintFactory constraintFactory) {
        return timeslottedLessons(constraintFactory)
                .filter(l -> !isAuthoritativePin(l))
                .filter(Lesson::isStageConfined)
                .filter(l -> !DailyActivityQuota.isStageDate(
                        SchoolSchedule.getExpectedHoursRow(l.getStudentGroup().id, l.timeslot.getDate())))
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H25 - Stage lesson outside stage days");
    }

    // ─── H26 Lesson On No-Session Day Safety Net (TASK-408) ─────────────

    /**
     * H26 - Lesson on a no-session day.
     *
     * Safety net twin of {@code Lesson.getAvailableTimeslots()}'s no-session
     * VRP filter (same VRP-prunes/constraint-catches relationship
     * H20/H23/H24/H25 have to their own VRP filters): for a sheet-covered group
     * a school date with NO {@link DailyActivityQuota} row means the group is
     * not in session that day, and the VRP already keeps the solver from
     * PROPOSING those placements - this hard constraint catches any pinned
     * lesson or other path that bypasses the value range. Pinned-exempt
     * per-entity (same shape as H23/H24/M20/M28) and pseudo-teacher-exempt via
     * {@code realTeacherTimeslottedLessons} (mirrors the VRP filter's own pseudo
     * exemption - Stage placement is confined separately by H25).
     *
     * <p>{@link SchoolSchedule#isNoSessionDate} is the exact same predicate the
     * VRP filter uses - single source of truth, see its javadoc. Golden: the
     * golden places 0 hours on every no-session date (the empirical basis of
     * TASK-408's discovery), so this measures 0/0 against {@code
     * GoldenSolutionRegressionTest} - if any golden pin surfaces here it is a
     * real sheet-vs-golden discrepancy to investigate, not a spurious hit.
     *
     * <p><b>Origine.</b> Calendario per-gruppo della scuola (una data scolastica senza riga DailyActivityQuota = gruppo non in sessione); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Rete di sicurezza; introdotto TASK-408.
     */
    Constraint lessonOnNoSessionDay(ConstraintFactory constraintFactory) {
        return realTeacherTimeslottedLessons(constraintFactory)
                .filter(l -> !isAuthoritativePin(l))
                .filter(l -> l.getStudentGroup() != null
                        && SchoolSchedule.isNoSessionDate(l.getStudentGroup().id, l.timeslot.getDate()))
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H26 - Lesson on no-session day");
    }

    // ─── M28 Afternoon Slots Reserved For Stage (was H21, TASK-379) ─────

    // M28 - Afternoon slots (14:30-16:30) are preferably reserved for STAGE
    // (referente, 17/07, Q17: "i pomeriggi vanno usati solo per lo STAGE").
    // GroupCalendarRule (H20) deliberately keeps these slots OPEN as stage
    // capacity - M28 is the narrower preference on top governing lesson-type
    // usage of that open capacity.
    // DEMOTED from hard H21 (TASK-379, golden-first directive): the golden
    // itself places 575 real afternoon lessons across 9 groups - the school's
    // own relief valve when morning capacity runs out. A hard tier plus the
    // old VRP afternoon exclusion made our problem strictly harder than the
    // one the school actually solved. Weight 100: afternoons are a genuine
    // last resort (dwarfs the 1-weight preference mediums, well under M20's
    // 1000/sparse-day); calibration note: the golden's own usage would score
    // -57,500 medium.
    // See CLAUDE.md "Afternoon slots reserved for stage" for the full rationale.
    //
    // EXEMPTIONS (same pinned-exemption pattern as M20/horizontallyCompact):
    // pinned lessons (historical/golden import, TASK-355) and pseudo-teacher
    // lessons (the synthetic "Stage" course itself, TASK-346).
    // Null-teacher lessons are DELIBERATELY penalized (H4's include-null precedent,
    // not H1/H7/S1's exclude-null): an explicitly-unassigned lesson must not park in
    // the reserved afternoon. Non-canonical timeslots (slotIndexOf == -1) are exempt
    // by construction — acceptable while all generated slots are canonical.
    /**
     * <p><b>Origine.</b> Convenzione strutturale (il pomeriggio e' riservato allo stage), nessun documento scuola. Demoted da hard (H21) TASK-379: il golden usa 575 lezioni pomeridiane come sfogo di capacita'.
     */
    Constraint afternoonSlotsReservedForStage(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> lesson.timeslot != null)
                .filter(lesson -> !isAuthoritativePin(lesson))
                .filter(lesson -> {
                    Teacher teacher = lesson.getTeacher();
                    return teacher == null || !teacher.pseudo;
                })
                .filter(lesson -> TimeslotSequence.slotIndexOf(lesson.timeslot) >= TimeslotSequence.AFTERNOON_START_SLOT_INDEX)
                .penalize(ScoreCompat.ofMedium(100))
                .asConstraint("M28 - Afternoon slots reserved for stage");
    }

    /**
     * M39 - Ordinary lesson on a stage-only day (TASK-604). A day whose quota row
     * declares stage hours and NO ordinary hours (aula = lab = 0) is the school's
     * stage day: ordinary lessons may legally sit there (the quota VRP filter is
     * FC-only, TASK-385), but the school's own practice almost never does it (the
     * golden holds 29 ordinary hours on stage-only days ALL YEAR, two mixed days
     * total). When the pool runs out, the search has no reason to prefer a declared
     * ordinary day over a stage day's morning for the marginal hour, so declared days
     * go empty (H34) while their hours sit on stage days - the [-11] residual's whole
     * H34 family. This preference prices that trade: ~100/lesson, the M28 scale
     * (both are "this legal cell is the wrong cell" preferences), pinned and
     * pseudo-teacher exempt like M28. NOT hard: the golden's 29h/year of exceptions
     * would violate it (golden-first). Weight knob planner.m39.weight, default 100,
     * read inline per-build like M37/M-LF.
     *
     * <p>FLAG {@value #M39_FLAG}: opt-in prototype, DEFAULT OFF until the TASK-604
     * bench (checkpoint replay) and adoption gate. Read LIVE per-build like the
     * sibling construction flags, so a -D on the java -jar command line arms it.</p>
     */
    static final String M39_FLAG = "planner.constraints.m39-stage-day.enabled";

    public static boolean m39StageDayEnabled() {
        return Boolean.getBoolean(M39_FLAG);
    }

    /** The M39 banner's weight read (same value the constraint itself reads). */
    public static int m39Weight() {
        return weightKnob("planner.m39.weight", 100);
    }

    Constraint ordinaryOnStageOnlyDay(ConstraintFactory constraintFactory) {
        boolean enabled = m39StageDayEnabled();
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> enabled && lesson.timeslot != null)
                .filter(lesson -> !isAuthoritativePin(lesson))
                .filter(lesson -> {
                    Teacher teacher = lesson.getTeacher();
                    return teacher == null || !teacher.pseudo;
                })
                .filter(lesson -> DailyActivityQuota.isStageOnlyDay(SchoolSchedule.getExpectedHoursRow(
                        lesson.getStudentGroup() != null ? lesson.getStudentGroup().id : null,
                        lesson.timeslot.getDate())))
                .penalize(ScoreCompat.ofMedium(weightKnob("planner.m39.weight", 100)))
                .asConstraint("M39 - Ordinary lesson on a stage-only day");
    }

    /** Inline per-build weight knob (the readCadenceKnobProperty idiom): -D on the
     * java -jar command line reaches the app JVM; blank or malformed falls to default.
     * Public static: the subclass's readCadenceKnobProperty mirrors this exactly and
     * may delegate (kept private there to avoid touching its 10 call sites' shape). */
    public static int weightKnob(String key, int defaultValue) {
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

    // ─── M-LA Lab Assistant Conflicts ───────────────────────────────
    // The school defines doppio (co-teaching) as teacher↔room, not teacher↔course.
    // Lab assistants should be free when a lab session runs in "their" room, but
    // this is MEDIUM — the solver decides which sessions the assistant can attend.
    // The early filter(teacher is assistant) restricts forEach to ~4 teachers'
    // entities (out of ~7000 total), keeping join performance manageable.

    // M-LA.3 - Assistant's Lesson overlaps a lab Lesson in their assigned room
    /**
     * <p><b>Origine.</b> Doppio/assistente: "T14 | Libera per doppio in 2°(100 ore)  e 3° moda (100 ore) in R16" e "T20 | Libero per doppio in 2° (100 ore)  e 3° moda  (85 ore) in R16" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", righe 96-97). L'assistente non entra in conflitto con un lab nella sua stanza; vive in M-LA a tier medium.
     */
    Constraint labAssistantConflictLessonLesson(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(l -> l.timeslot != null)
                .filter(l -> l.course != null && l.getTeacher() != null
                        && SchoolSchedule.isLabAssistant(l.getTeacher().id))
                .join(Lesson.class,
                        Joiners.equal(l -> l.timeslot))
                .filter((assistantLesson, labLesson) -> {
                    if (assistantLesson.id.equals(labLesson.id)) return false;
                    String roomTag = SchoolSchedule.getLabAssistantRoomTag(assistantLesson.getTeacher().id);
                    return labLesson.room != null && labLesson.room.tags != null
                            && labLesson.room.tags.contains(roomTag);
                })
                .penalize(ScoreCompat.ONE_MEDIUM)
                .asConstraint("M-LA - Lab assistant conflict");
    }

    // ─── M3 Room Capacity ───────────────────────────────────────────

    // M3 - Room capacity must accommodate the student group size
    /**
     * <p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; l'aula deve contenere la classe (capacita' vs numerosita'), introdotto fin dalle origini.
     */
    Constraint roomCapacity(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> lesson.room != null
                        && lesson.getStudentGroup() != null
                        && lesson.room.capacity < lesson.getStudentGroup().size)
                .penalize(ScoreCompat.ONE_MEDIUM)
                .asConstraint("M3 - Room capacity");
    }

    // ─── M5 Reward Informatica After Stage ──────────────────────────

    // M5 - Reward Informatica lessons scheduled after stage ends
    // For MODA and FA groups, prefer Informatica in post-stage period for thesis work
    /**
     * <p><b>Origine.</b> Pratica della scuola per le tesine (MODA/FA), nessun documento scuola citato: le ore di Informatica preferibilmente dopo lo stage. Preferenza non requisito; introdotto TASK-015.
     */
    Constraint rewardInformaticaAfterStage(ConstraintFactory constraintFactory) {
        // Precomputed: Lesson × StagePattern join on studentGroup (static) + subject/group filter.
        // Only the timeslot-dependent date check runs per move.
        return constraintFactory.precompute(this::informaticaStagePairs)
                .filter((lesson, sp) -> lesson.timeslot != null)
                .reward(ScoreCompat.ONE_MEDIUM,
                        (lesson, sp) -> lesson.timeslot.getDate().isAfter(sp.endDate) ? 1 : 0)
                .asConstraint("M5 - Reward Informatica after stage");
    }

    // ─── H30 Sicurezza Before Stage (was M26) ──────────────────────────

    /**
     * H30 (was M26) - Sicurezza before stage.
     *
     * T03 ("tutte le ore della materia 'Sicurezza' prima dell'inizio
     * dello stage della classe", vincoli-in-parole.md TASK-369): every
     * Sicurezza lesson for a group must be dated strictly before that
     * group's {@link StagePattern#startDate} - a lesson ON the start date
     * itself is not "before the start", so it's penalized too (matches
     * {@code stageBlackout}'s own inclusive-of-startDate boundary a few
     * methods above, which is the only reason this is even reachable:
     * Sicurezza is professionalizzante, so H4 doesn't already block it during
     * stage). Groups without a seeded StagePattern (every 2026/27 group
     * except G07, G11, G12) have nothing to join against, so this is
     * a structural no-op for them. {@code sp.startDate} is nullable (see
     * {@link StagePattern#expandDates}'s own null guard) - the null check
     * here mirrors that, since a null startDate has no "before" to violate.
     *
     * <p>Measured against the golden 2026/27 fixture (TASK-355): G07 is
     * the only StagePattern-seeded group (of the 3 seeded: G07, G11, 4
     * FD) that has any Sicurezza hours at all in the golden data - 3
     * FB/G12 simply don't teach Sicurezza. G07's 12 golden Sicurezza
     * lessons run 2026-09-15..2026-12-02, entirely before its stage starts
     * 2027-03-23, so this scores 0/0 on the golden (T03 already applied
     * this rule by hand - see GoldenSolutionRegressionTest).</p>
     *
     * <p><b>Promoted to HARD (maintainer, 2026-08-15).</b> The broader TASK-472
     * classification measured the golden PRACTICE (not just the coded join):
     * all 6 groups with both Sicurezza and stage lessons place every
     * Sicurezza hour strictly before the first stage date, zero violations -
     * the H28 precedent (crystallize a rule the golden itself never breaks).
     * Renamed M26 -> H30 at promotion time (ID prefix = tier, same
     * convention as the M36 -> H29 promotion). referente confirmation a
     * posteriori (domande-brevi rev 15/08), not blocking: golden-first.
     * Before the promotion this was ONE_MEDIUM (TASK-369); the written rule
     * says "tutte le ore... prima", the golden honors it 100%, the solver
     * stops trading it against mediums.</p>
     *
     * <p><b>Origine.</b> "SICUREZZA | Ore con le 3° (+ 2° OIT) prima di Stage | 2 gg di sicurezza  4 ore con le 1°" (workbook vincoli Spalmone, tab "Vincoli Docenti", riga 42). Le Sicurezze precedono lo stage del gruppo; introdotto TASK-369.
     */
    Constraint sicurezzaBeforeStage(ConstraintFactory constraintFactory) {
        return constraintFactory.precompute(this::sicurezzaStagePairs)
                .filter((lesson, sp) -> lesson.timeslot != null && sp.startDate != null)
                .filter((lesson, sp) -> !lesson.timeslot.getDate().isBefore(sp.startDate))
                .penalize(ScoreCompat.ONE_HARD)
                .asConstraint("H30 - Sicurezza before stage");
    }

    // ─── M30/M31 Daily Activity Quota Fill + Contiguity (TASK-381 "L1-bis") ─

    /**
     * M30 - Lab day quota fill (M30.1 the day-has-lessons half below; M30.2
     * is the zero-lessons complement further down). Dot-notation sub-id of a
     * single parent code, same naming convention {@link #profSingleDayDuringStage}'s
     * {@code H4.3} already uses in this file - here it names TWO separate
     * {@code Constraint} methods (M30.1/M30.2) instead of one, because
     * Timefold's {@code ConstraintCollectors} has no "groupBy including
     * zero-count keys" operator: a Lesson-sourced stream can never surface a
     * quota-date with ZERO matching lessons on its own (see M30.2's own
     * javadoc for why it's driven from the {@link DailyActivityQuota} fact
     * instead).
     *
     * ONE_MEDIUM x |placed - labHours| per (group, quota-date with labHours
     * &gt; 0), counting ALL matching lessons INCLUDING pinned - this checks
     * the SCHOOL's own historical accuracy against its own sheet (same
     * measured-not-just-enforced intent as M24/M25), not only the solver's
     * future placements. M30.1 covers days with at least one matching
     * bound-lab lesson via a plain join + {@code groupBy}; a lesson-sourced
     * stream can never surface a day with ZERO matches (there's no Lesson
     * row to group by), so M30.2 below drives its stream from the {@link
     * DailyActivityQuota} fact instead, via {@code ifNotExists}.
     *
     * <p>Golden measurement ({@code GoldenSolutionRegressionTest}): -43
     * medium / 12 matches total (G04 35/10, G08 4/1, G13 4/1 - matches
     * split across M30.1/M30.2 depending on whether that mismatch day has
     * any bound-lab lesson at all). G04's residual carries a &ge;23h
     * structural floor - the demand-vs-quota derivation is owned by {@link
     * DailyActivityQuota#QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP}'s javadoc, not
     * repeated here.</p>
     *
     * <p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota.labHours per G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Conta anche i pin (verifica la pratiche storiche della scuola); introdotto TASK-381 (L1-bis).
     */
    Constraint labDayQuotaFillPresent(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(DailyActivityQuota.class)
                .filter(POSITIVE_LAB_HOURS)
                .join(timeslottedLessons(constraintFactory),
                        Joiners.equal(q -> q.studentGroup, Lesson::getStudentGroup),
                        Joiners.equal(q -> q.date, LESSON_DATE))
                .filter((q, l) -> DailyActivityQuota.isQuotaBoundLabSubject(q.studentGroup.name, l.getSubject()))
                .groupBy((q, l) -> q, countBi())
                // Exact matches (placed == labHours) must not become a zero-score
                // ConstraintMatch (same "only report genuine violations" shape M20's
                // horizontallyCompact filters before penalize) - without this filter
                // every one of the golden's 134 quota-lab-days shows up as a "match"
                // (score 0 for the 122 exact ones), not just the 12 real mismatches.
                .filter((q, placed) -> placed != q.labHours)
                .penalize(ScoreCompat.ONE_MEDIUM, (q, placed) -> Math.abs(placed - q.labHours))
                .asConstraint("M30.1 - Lab day quota fill (days with a matching lesson)");
    }

    /**
     * M30.2 - the zero-matching-lessons complement of {@link #labDayQuotaFillPresent} above (see its javadoc).
     *
     * <p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota.labHours per G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Conta anche i pin (verifica la pratiche storiche della scuola); introdotto TASK-381 (L1-bis).
     */
    Constraint labDayQuotaFillAbsent(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(DailyActivityQuota.class)
                .filter(POSITIVE_LAB_HOURS)
                .ifNotExists(timeslottedLessons(constraintFactory),
                        Joiners.equal(q -> q.studentGroup, Lesson::getStudentGroup),
                        Joiners.equal(q -> q.date, LESSON_DATE),
                        Joiners.filtering((q, l) -> DailyActivityQuota.isQuotaBoundLabSubject(q.studentGroup.name, l.getSubject())))
                .penalize(ScoreCompat.ONE_MEDIUM, q -> q.labHours)
                .asConstraint("M30.2 - Lab day quota fill (days with no matching lesson)");
    }

    /**
     * M31 - Lab day single contiguous run.
     *
     * ONE_MEDIUM x (runs - 1) per (group, date) over that day's quota-bound
     * lab lessons (plain slot-index adjacency, {@link
     * TimeslotSequence#slotIndexOf}). DAY-LEVEL pinned-exempt, the same shape
     * as M29 ({@code professionalizzantiAtDayEdges} in {@code
     * SchoolScheduleConstraintProvider}) - see that constraint's own javadoc
     * for the full day-level-exemption rationale: a day is exempt only when
     * EVERY quota-bound lab lesson that day is pinned, so the solver can
     * never CREATE a split run of its own, whether placing from scratch or
     * extending next to an existing pin.
     *
     * <p>Golden measurement: vacuous 0/0 via the day-level pin exemption
     * (100%-pinned golden import, same structural reason as M20/M28/M29),
     * and also genuinely near-clean on the raw data itself - the golden's
     * own quota-bound lab placements are contiguous on the overwhelming
     * majority of their non-empty days, with one confirmed exception (G13
     * 2026-11-03, slots {0-indexed} 1 and 4).</p>
     *
     * <p><b>Origine.</b> Calendario per-gruppo della scuola + pratica del blocco lab compatto; "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Day-level pinned-exempt; introdotto TASK-381 (L1-bis).
     */
    Constraint labDaySingleContiguousRun(ConstraintFactory constraintFactory) {
        return timeslottedLessons(constraintFactory)
                .filter(QUOTA_GROUP)
                .filter(l -> DailyActivityQuota.isQuotaBoundLabSubject(l.getStudentGroup().name, l.getSubject()))
                .groupBy(Lesson::getStudentGroup, LESSON_DATE, toList())
                .filter((group, date, lessons) -> dayHasUnpinnedLesson(lessons))
                // expand() computes the run count once per (group, date) tuple and
                // carries it into both filter and penalize below, instead of calling
                // countContiguousRuns() twice (it re-sorts/re-streams the lesson list each time).
                .expand((group, date, lessons) -> countContiguousRuns(lessons))
                .filter((group, date, lessons, runs) -> runs > 1)
                .penalize(ScoreCompat.ONE_MEDIUM, (group, date, lessons, runs) -> runs - 1)
                .asConstraint("M31 - Lab day single contiguous run");
    }

    /**
     * DAY-LEVEL pinned exemption shared by M29 ({@code
     * professionalizzantiAtDayEdges}) and M31 ({@link
     * #labDaySingleContiguousRun}): a day's lesson SET is evaluated only when
     * at least one contributing lesson is NOT an authoritative pin - see M29's
     * javadoc for the full rationale. One helper so the exemption rule can't drift
     * between the two constraints.
     *
     * <p>TASK-419: a promoted pin counts as non-authoritative here, so a day the staged
     * solve froze stays evaluated instead of becoming exempt until the tail's first free
     * lesson lands on it. See {@link #isAuthoritativePin}'s javadoc.
     */
    protected static boolean dayHasUnpinnedLesson(List<Lesson> lessons) {
        return lessons.stream().anyMatch(l -> !isAuthoritativePin(l));
    }

    /**
     * Collapse a day's lessons to their sorted, de-duplicated, valid
     * (&ge; 0) canonical slot indexes - shared by M31's {@link
     * #countContiguousRuns} and M29's {@code isLegalProfEdgeRun} in {@code
     * SchoolScheduleConstraintProvider}, so the slot-index derivation (incl.
     * the negative-sentinel handling) lives in one place.
     */
    protected static List<Integer> sortedDistinctSlotIndexes(List<Lesson> lessons) {
        return lessons.stream()
                .map(l -> TimeslotSequence.slotIndexOf(l.timeslot))
                .filter(idx -> idx >= 0)
                .distinct()
                .sorted()
                .toList();
    }

    /** Number of contiguous slot-index runs across {@code lessons} (all same group/date) - 1 means a single unbroken block. */
    private static int countContiguousRuns(List<Lesson> lessons) {
        List<Integer> slotIndexes = sortedDistinctSlotIndexes(lessons);
        if (slotIndexes.isEmpty()) return 0;
        int runs = 1;
        for (int i = 1; i < slotIndexes.size(); i++) {
            if (slotIndexes.get(i) != slotIndexes.get(i - 1) + 1) runs++;
        }
        return runs;
    }

    // ─── M12 Overflow Beyond VRP-Buffered Ideal End Date ────────────

    /**
     * M12 - Overflow beyond VRP-buffered ideal end date.
     *
     * Uses idealEndDateVrpBuffered from SchoolSchedule; since TASK-378 removed
     * the value-range horizon this is the only consumer of those dates.
     *
     * RECALIBRATED to ONE_MEDIUM/day (was 1000/day) by the golden-first
     * directive (2026-07-19): the school's own timetable places 29.3% of ALL
     * hours (3,677) beyond these computed targets - every group runs to June,
     * G14 is 100% beyond (its real window is Jan-May). "Finish early" is our
     * legacy assumption, not a school rule: lo spalmone IS the spreading, and
     * S12 (+5,623 on golden) rewards exactly the year-long continuity these
     * targets fight. At 1000/day M12 was 80% of run-9's medium score
     * (-8,065,000 of -10.2M), bending the whole search toward an anti-golden
     * goal. Weight 1 keeps a gentle earliness gradient; whether the target
     * model itself should survive is Q20 for referente (domande-brevi.md).
     *
     * <p><b>Origine.</b> Calendario per-gruppo della scuola: la data fine attesa cumula le ore attese per-data (DailyActivityQuota expected-hours) al totale del corso; "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Fact-based dal TASK-394; introdotto TASK-385.
     */
    Constraint overflowBeyondTargetEndDate(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Lesson.class)
                .filter(lesson -> !isAuthoritativePin(lesson)
                        && lesson.getStudentGroup() != null
                        && lesson.timeslot != null)
                // Get VRP-buffered end date from static map (aligns with VRP filtering)
                .filter(lesson -> {
                    LocalDate vrpBufferedEndDate = SchoolSchedule.getIdealEndDateVrpBuffered(lesson.getStudentGroup().id);
                    return vrpBufferedEndDate != null && lesson.timeslot.getDate().isAfter(vrpBufferedEndDate);
                })
                .penalize(ScoreCompat.ONE_MEDIUM,
                        lesson -> {
                            LocalDate vrpBufferedEndDate = SchoolSchedule.getIdealEndDateVrpBuffered(lesson.getStudentGroup().id);
                            if (vrpBufferedEndDate == null) return 0;
                            return (int) Math.max(0, ChronoUnit.DAYS.between(
                                    vrpBufferedEndDate, lesson.timeslot.getDate()));
                        })
                .asConstraint("M12 - Overflow beyond target end date");
    }
}
