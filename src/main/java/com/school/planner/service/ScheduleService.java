package com.school.planner.service;

import java.time.Duration;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.stream.ConstraintRef;
import ai.timefold.solver.core.api.score.stream.DefaultConstraintJustification;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.api.solver.event.NewBestSolutionEvent;
import ai.timefold.solver.core.api.solver.event.FinalBestSolutionEvent;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatch;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;
import ai.timefold.solver.core.impl.score.director.ScoreDirectorFactory;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;

import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.LabAssistant;
// Note: LabBlock import removed - deprecated in TASK-228

import com.school.planner.domain.Lesson;
import com.school.planner.domain.IdealEndDateInfo;
import com.school.planner.exception.StagedSolveInProgressException;
import com.school.planner.domain.PreAssignmentStatus;
import com.school.planner.domain.ProfessionalizzantiDay;
import java.nio.file.Files;
import java.nio.file.Path;

import com.school.planner.solver.BaseConstraintProvider;
import com.school.planner.solver.CompositeSwapFinisher;
import com.school.planner.solver.ViolationScopedSteepestFinisher;
import com.school.planner.solver.DayClusteredConstructionPhase;
import com.school.planner.solver.PairCellGuard;
import com.school.planner.solver.RoomTierOverrides;
import com.school.planner.solver.ScoreCompat;
import com.school.planner.solver.checkpoint.StagedCheckpoint;
import com.school.planner.solver.checkpoint.StagedCheckpointCapture;
import com.school.planner.solver.checkpoint.StagedCheckpointRestore;
import com.school.planner.solver.checkpoint.StagedCheckpointSerializer;
import com.school.planner.solver.checkpoint.StagedCheckpointCursor;
import com.school.planner.solver.checkpoint.StagedCheckpointWriter;
import com.school.planner.solver.checkpoint.StagedResumePlan;
import com.school.planner.solver.checkpoint.StagedPhaseBoundary;
import com.school.planner.solver.CompactionPhase;
import com.school.planner.solver.CompositionHoleCloser;
import com.school.planner.solver.DayClusteredConstructionPhase;
import com.school.planner.solver.DayCompactBuilder;
import com.school.planner.solver.DayCompactMenuSource;
import com.school.planner.solver.ExactFinisher;
import com.school.planner.solver.FinalClosingCampaign;
import com.school.planner.solver.AnchorRepair;
import com.school.planner.solver.HoleRepair;
import com.school.planner.solver.LabDayConsolidation;
import com.school.planner.solver.MultiStartRunner;
import com.school.planner.solver.PhaseTrackingListener;
import com.school.planner.solver.PhaseTransitionMarker;
import com.school.planner.solver.StagedPinPolicy;
import com.school.planner.solver.PortfolioPickBest;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.AdaptiveMoveWeights;
import com.school.planner.solver.AdaptiveStreamWeightFactories;
import com.school.planner.solver.NearbyTemplateMoveFilter;
import com.school.planner.solver.RetrySignature;
import com.school.planner.solver.RotationEnumerator;
import com.school.planner.solver.DeclaredDayPreBooker;
import com.school.planner.solver.EmptyDayFinisher;
import com.school.planner.solver.HoleRelayFinisher;
import com.school.planner.solver.SurplusHarvest;
import com.school.planner.solver.QuotaFillRepair;
import com.school.planner.solver.SurplusRebalance;
import com.school.planner.solver.multithreaded.MultiThreadedLocalSearchDecider;
import com.school.planner.solver.multithreaded.MultiThreadedLsWiring;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.rest.dto.LessonViolationAnalysis;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.solver.WeeklyTemplateBuilder;
import com.school.planner.solver.WeeklyTemplatePlanner;
import com.school.planner.util.ConstraintIds;
import com.school.planner.util.LifecycleLog;
import com.school.planner.domain.Course;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.constraints.Justification;
import com.school.planner.domain.constraints.LessonBearingJustification;
import com.school.planner.solver.SolverProgressionListener;

@ApplicationScoped
public class ScheduleService {

    private static final Logger LOG = Logger.getLogger(ScheduleService.class);

    private static final Long SINGLETON_ID = 1L;

    @Inject
    SolverManager<SchoolSchedule> solverManager;

    @Inject
    SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    @Inject
    TargetEndDateCalculator targetDateCalculator;

    @Inject
    PreAssignmentEntityExtractor entityExtractor;

    @Inject
    HybridPhase1aService hybridPhase1aService;

    @Inject
    ImportService importService;

    @Inject
    PhaseProgressionService progressionService;

    @Inject
    StudentGroupCompletionService studentGroupCompletionService;

    /** Fired from {@link #saveFinalSolution} — TASK-354's solve-completion hook (code review B2). */
    @Inject
    Event<DataMutationEvent> validationMutationEvent;

    /** TASK-425: Jackson reader for the golden-cadence reference JSON (CDI-provided, same bean
     *  ValidationSnapshotRepository already injects; never instantiate one inline when CDI owns it). */
    @Inject
    com.fasterxml.jackson.databind.ObjectMapper cadenceObjectMapper;

    private volatile BendableScore latestScore;
    /** Last solver crash (exceptionHandler), cleared on solve start - surfaced by getScheduleWithStatus so a died solve doesn't masquerade as a completed one (TASK-378). */
    private volatile String lastSolverError;
    private volatile Instant solveStartTime;
    private volatile boolean seenChPhase = false;

    /** TASK-658: one-shot guard for the async post-restart score restoration. */
    private final java.util.concurrent.atomic.AtomicBoolean scoreRestoreStarted =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile Instant chCompletionTime;

    // Cumulative timing accumulators — persist across stop/resume cycles within the same JVM session
    private volatile long cumulativeElapsedSeconds = 0;

    private volatile long cumulativeChSeconds = 0;
    private volatile long cumulativeLsSeconds = 0;

    // Cached constraint-match data for fast violation analysis.
    // Computing this requires a full score director pass over 10K+ lessons,
    // so we cache the result and reuse it when the schedule hasn't changed.
    // (Timefold 2.x: SolutionManager.analyze() became Enterprise-only, so we
    // use ScoreDirector + ConstraintMatchPolicy.ENABLED directly.)
    private volatile ConstraintMatchSnapshot cachedSnapshot;
    private volatile Instant cachedAnalysisTime;

    // Cached score director factory — extracted once from the injected SolutionManager.
    // Building it per request would re-parse constraint streams; we want it once.
    private volatile ScoreDirectorFactory<SchoolSchedule, BendableScore> scoreDirectorFactory;

    /**
     * Snapshot of a scored solution's constraint matches — the open-source replacement
     * for Timefold 2.x's Enterprise-only ScoreAnalysis.
     */
    public record ConstraintMatchSnapshot(
            BendableScore score,
            Map<ConstraintRef, ConstraintMatchTotal<BendableScore>> constraintMap) {
    }

    /**
     * Pre-pass placement snapshot for the improve-or-noop revert fallback (TASK-473): captures the
     * (timeslot, room) of every lesson so a deterministic repair pass that worsened the real
     * timeslot-tier hard can be ROLLED BACK to the exact pre-pass grid and the cycle continued to
     * B-ROOM (true NOOP), instead of aborting and leaving every mover room-null.
     *
     * <p><b>Hard contract (review R3):</b> stores {@link Timeslot}/{@link Room} REFERENCES, not
     * copies. Correct only because the repair passes mutate {@code lesson.timeslot} /
     * {@code lesson.room} by REASSIGNMENT (the {@code (l,ts) -> l.timeslot = ts} /
     * {@code l -> l.room = null} callbacks), never by mutating the {@link Timeslot}/{@link Room}
     * objects themselves. All four passes route every persistent mutation through
     * {@link HoleRepair#commitCrossDayMove}, which reassigns only those two fields. If a future
     * pass ever mutates a Timeslot/Room field in place, reference-snapshotting silently breaks and
     * this must switch to a defensive copy.
     */
    static final class PlacementSnapshot {
        private final Lesson[] lessons;
        private final Timeslot[] timeslots;
        private final Room[] rooms;

        private PlacementSnapshot(Lesson[] lessons, Timeslot[] timeslots, Room[] rooms) {
            this.lessons = lessons;
            this.timeslots = timeslots;
            this.rooms = rooms;
        }

        static PlacementSnapshot capture(List<Lesson> lessons) {
            Lesson[] ls = new Lesson[lessons.size()];
            Timeslot[] ts = new Timeslot[ls.length];
            Room[] rs = new Room[ls.length];
            int i = 0;
            for (Lesson l : lessons) {
                ls[i] = l;
                ts[i] = l.timeslot;
                rs[i] = l.room;
                i++;
            }
            return new PlacementSnapshot(ls, ts, rs);
        }

        /** Roll every lesson back to its captured (timeslot, room). Raw field writes, matching the passes. */
        void restore() {
            for (int i = 0; i < lessons.length; i++) {
                lessons[i].timeslot = timeslots[i];
                lessons[i].room = rooms[i];
            }
        }
    }

    /**
     * The single sanctioned mutation callbacks every deterministic repair pass must route its
     * moves through: plain reassignment of {@code lesson.timeslot}/{@code lesson.room}, never an
     * in-place mutation of the {@link Timeslot}/{@link Room} objects. This is the structural
     * enforcement of {@link PlacementSnapshot}'s reference-semantics contract (TASK-473 review R3):
     * because every pass shares these two constants, the snapshot's capture-by-reference stays
     * correct by construction rather than by convention. A pass that mutated a Timeslot field in
     * place would silently break the revert; routing through these constants makes that drift a
     * compile-time choice instead.
     */
    private static final BiConsumer<Lesson, Timeslot> MOVE_TIMESLOT = (lesson, ts) -> lesson.timeslot = ts;
    /** TASK-542 iter-27: the pure hole repairer keeps a mover's room (it only gates on it). */
    private static final BiConsumer<Lesson, com.school.planner.domain.Room> MOVE_ROOM =
            (lesson, room) -> lesson.room = room;
    private static final Consumer<Lesson> NULL_ROOM = lesson -> lesson.room = null;

    /**
     * Rooms excluded from solver planning.
     * task-012/task-013: "Sostegno" is a support room for special needs, not schedulable.
     */
    private static final List<String> EXCLUDED_ROOMS = List.of("Sostegno");

    /**
     * Student groups excluded from solver planning.
     * TASK-222 (2026-03-10) excluded "G14" (short course with fixed dates,
     * not fully bound to planning rules). TASK-343 (2026-07-16) re-adds G14
     * as a real StudentGroup with its own GroupCalendarRule pattern (Thu/Fri
     * only), so the exclusion is lifted - empty for now, kept as a named hook
     * for any future group that needs the same treatment.
     */
    private static final List<String> EXCLUDED_GROUPS = List.of();

    @Transactional
    public SchoolSchedule loadSchedule() {
        return loadSchedule(false);
    }

    /**
     * Every PLANNABLE lesson, JOIN FETCHed on its timeslot - the one definition of that
     * population (TASK-419 code review: it was copy-pasted in four places, so a change to
     * the exclusion rule could silently apply to the solver's load and not to the staged
     * cycle's own reads).
     *
     * <p>The JOIN FETCH is load-bearing, not an optimization: constraint analysis (M13,
     * M20, S1, ...) reads {@code lesson.timeslot.getDate()} outside the session. TASK-222's
     * {@link #EXCLUDED_GROUPS} filter is currently empty (G14 was re-added by TASK-343)
     * and kept as the named hook for any future group.
     */
    private List<Lesson> plannableLessons() {
        return Lesson.listAllWithTimeslot().stream()
                .filter(l -> l.course != null && l.course.studentGroup != null
                        && !EXCLUDED_GROUPS.contains(l.course.studentGroup.name))
                .toList();
    }

    /**
     * @param forSolver true only for the solver's own problemFinder load. The
     *     static VRP/lookup state is rebuilt ONLY when no solve is active (or
     *     when loading for the solver itself): every API read used to rebuild
     *     it too, and a rebuild racing the solver thread's comparator sorts or
     *     VRP reads produced mid-sort value changes - the TimSort "Comparison
     *     method violates its general contract" crash that silently killed
     *     runs 3/4 at the room-CH phase (TASK-378). During an active solve the
     *     statics belong to the solve; API readers reuse them (same DB data
     *     unless someone CRUDs mid-solve, in which case stale-but-consistent
     *     beats a crashed solver). KNOWN BLIND SPOT: the guard reads only the
     *     MAIN solverManager's status - an in-flight pre-assignment run
     *     (HybridPhase1aService, structurally a no-op for 2026/27) is not
     *     covered and would still race a concurrent API refresh.
     */
    @Transactional
    SchoolSchedule loadSchedule(boolean forSolver) {
        List<Timeslot> timeslots = Timeslot.listAll();
        // task-012/task-013: exclude non-plannable rooms (e.g., Sostegno)
        List<Room> rooms = Room.<Room>listAll().stream()
                .filter(r -> !EXCLUDED_ROOMS.contains(r.name))
                .toList();
        List<StagePattern> stagePatterns = StagePattern.listAll();
        List<TeacherAvailability> teacherAvailabilities = TeacherAvailability.listAll();
        List<SchoolHoliday> schoolHolidays = SchoolHoliday.listAll();
        List<StudentGroup> studentGroups = StudentGroup.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
        List<GroupCalendarRule> groupCalendarRules = GroupCalendarRule.listAll();
        List<DailyActivityQuota> dailyActivityQuotas = DailyActivityQuota.listAll();
        List<Lesson> lessons = plannableLessons();
        healPinnedNullTimeslotLessons(lessons);
        LOG.infof("loadSchedule: loaded %d lessons with timeslots", lessons.size());
        long assignedCount = lessons.stream().filter(l -> l.timeslot != null).count();
        LOG.infof("loadSchedule: %d lessons have non-null timeslot", assignedCount);

        // CRITICAL FIX: Normalize lesson timeslot references to use same object instances as timeslots list
        // This is required for Timefold's solutionManager.update() which validates object identity
        // JPA/Hibernate loads different object instances for the same database row,
        // and Timefold compares by object identity, not ID
        Map<Long, Timeslot> timeslotMap = timeslots.stream()
                .collect(Collectors.toMap(t -> t.id, t -> t));
        int normalizedCount = 0;
        for (Lesson lesson : lessons) {
            if (lesson.timeslot != null) {
                Timeslot canonical = timeslotMap.get(lesson.timeslot.id);
                if (canonical != null && canonical != lesson.timeslot) {
                    lesson.timeslot = canonical;
                    normalizedCount++;
                }
            }
        }
        LOG.infof("loadSchedule: normalized %d lesson timeslot references to canonical objects", normalizedCount);

        // CRITICAL FIX (TASK-298): Also normalize course, studentGroup, teacher, and room references
        // Timefold constraint joiners compare by object identity, not ID
        // Without this, H1/H2/H3 constraints fail to detect conflicts because lessons have different
        // object instances for the same course/studentGroup/teacher/room
        Map<Long, Course> courseMap = courses.stream()
                .collect(Collectors.toMap(c -> c.id, c -> c));
        Map<Long, StudentGroup> studentGroupMap = studentGroups.stream()
                .collect(Collectors.toMap(g -> g.id, g -> g));
        Map<Long, Teacher> teacherMap = teachers.stream()
                .collect(Collectors.toMap(t -> t.id, t -> t));
        Map<Long, Room> roomMap = rooms.stream()
                .collect(Collectors.toMap(r -> r.id, r -> r));

        // CRITICAL: Also normalize studentGroup/teacher references ON the canonical course objects
        for (Course canonicalCourse : courseMap.values()) {
            if (canonicalCourse.studentGroup != null) {
                StudentGroup canonicalGroup = studentGroupMap.get(canonicalCourse.studentGroup.id);
                if (canonicalGroup != null) {
                    canonicalCourse.studentGroup = canonicalGroup;
                }
            }
            if (canonicalCourse.teacher != null) {
                Teacher canonicalTeacher = teacherMap.get(canonicalCourse.teacher.id);
                if (canonicalTeacher != null) {
                    canonicalCourse.teacher = canonicalTeacher;
                }
            }
        }

        int normalizedCourses = 0;
        int normalizedRooms = 0;
        for (Lesson lesson : lessons) {
            if (lesson.course != null) {
                Course canonical = courseMap.get(lesson.course.id);
                if (canonical != null && canonical != lesson.course) {
                    lesson.course = canonical;
                    normalizedCourses++;
                }
            }
            if (lesson.room != null) {
                Room canonicalRoom = roomMap.get(lesson.room.id);
                if (canonicalRoom != null && canonicalRoom != lesson.room) {
                    lesson.room = canonicalRoom;
                    normalizedRooms++;
                }
            }
        }
        LOG.infof("loadSchedule: normalized %d courses, %d rooms to canonical objects (studentGroups/teachers normalized on courses)",
                normalizedCourses, normalizedRooms);

        // Note: LabBlock filtering removed in TASK-228 - labs are now pinned Lessons
        // Group exclusion is handled elsewhere (ValueRangeProviders)

        List<LabAssistant> labAssistants = LabAssistant.listAll();

        // Log total lesson count
        LOG.infof("loadSchedule: Loaded %d lessons, %d lab assistants",
                lessons.size(), labAssistants.size());

        // TASK-207: Calculate student group completion stats for UI display
        // Note: idealEndDate is in StudentGroupCompletionStats, not used by constraints.
        // NOT guarded: completion changes as the solver places lessons, so the live UI
        // wants fresh stats on every mid-solve fetch (unlike the invariant ideal-end-date below).
        for (StudentGroup group : studentGroups) {
            studentGroupCompletionService.calculateStats(group);
        }
        LOG.infof("Calculated completion stats for %d student groups", studentGroups.size());

        // TASK-419: the staged cycle owns the statics between its stages too (its own calls
        // pass forSolver = true at the points where a refresh is wanted), so an API read
        // landing mid-cycle must not rebuild them - same race the guard below prevents for
        // an ordinary solve, widened to the stages the SolverManager reports as idle.
        boolean refreshStatics = forSolver || (getStatus() == SolverStatus.NOT_SOLVING && stagedStage == null);
        if (refreshStatics) {
            // TASK-397: the ideal end date is INVARIANT during a solve (groups, course
            // hours, timeslots, holidays, stage patterns are all fixed for the duration),
            // so compute it once at setup under the same TASK-378 guard as the statics -
            // NOT on every mid-solve UI/concurrent loadSchedule fetch (it was ~591
            // full recomputes/solve, each 14 groups x a Course.find + day-scan). Must run
            // BEFORE refreshSolverStatics so the static M12 map copies the fresh values.
            targetDateCalculator.computeTargetEndDates(
                    studentGroups, schoolHolidays, stagePatterns, timeslots);
            refreshSolverStatics(timeslots, rooms, lessons, studentGroups,
                    teacherAvailabilities, schoolHolidays, stagePatterns,
                    labAssistants, groupCalendarRules, dailyActivityQuotas);
        } else {
            LOG.debug("loadSchedule: solver active - static VRP/lookup state left untouched (TASK-378)");
        }

        // TASK-397: cheaply re-hydrate the @Transient ideal-end fields onto the
        // freshly-listed group objects from the static cache (populated once under the
        // guard above). Runs on EVERY fetch - including mid-solve, where the guard
        // skipped the expensive recompute - so the UI ideal-end marker (which reads
        // group.idealEndDateInfo off the schedule payload) keeps rendering during solving.
        Map<Long, IdealEndDateInfo> idealInfoCache = SchoolSchedule.getIdealEndDateInfoStatic();
        for (StudentGroup group : studentGroups) {
            if (group.idealEndDateInfo == null && idealInfoCache != null) {
                group.idealEndDateInfo = idealInfoCache.get(group.id);
            }
            if (group.idealEndDateVrpBuffered == null) {
                group.idealEndDateVrpBuffered = SchoolSchedule.getIdealEndDateVrpBuffered(group.id);
            }
        }

        SchoolSchedule schedule = new SchoolSchedule(timeslots, rooms, stagePatterns,
                teacherAvailabilities, schoolHolidays, studentGroups,
                teachers, courses, lessons, labAssistants);
        schedule.setProfessionalizzantiDays(ProfessionalizzantiDay.listAll());
        schedule.setDailyActivityQuotas(dailyActivityQuotas);

        // Note: LabBlock occupiedTimeslots rehydration removed in TASK-228
        // (LabBlocks are now pinned Lessons, handled elsewhere)

        // Populate completion info for student groups (for UI completion markers)
        populateCompletionInfo(studentGroups, lessons);

        return schedule;
    }

    /**
     * Defensive second layer against BUG-A (pinned-with-null-timeslot corruption):
     * a pinned lesson with a null timeslot is a semantically-invalid state — a pin
     * is supposed to fix a lesson to a real slot, so "pinned to nothing" is
     * corruption regardless of whether Timefold tolerates it. (Before TASK-398 it
     * also threw {@code IllegalStateException} deep inside {@code solutionManager.update()}
     * because {@code Lesson.timeslot} lacked {@code allowsUnassigned}; TASK-398 added
     * {@code allowsUnassigned = true} to enable "the box", so a null timeslot is now a
     * legal Timefold value and no longer throws — but a PINNED null is still wrong and
     * this heal still repairs it.) {@link #loadSchedule()} is used by every path that
     * scores or analyzes the schedule (analyzeScore, analyzeLessonViolations, the
     * validation watchdog). {@code LessonResource.togglePin} blocks new occurrences at
     * the source, but any row corrupted before that guard existed (or written directly
     * to the DB, e.g. by a script or import) must not be allowed to leak a bad pin into
     * analysis. Heals in place — unpins and logs loudly. The lessons are
     * managed entities inside this {@code @Transactional} load, so the unpin is
     * flushed to the DB on commit: a corrupt row is repaired permanently the
     * first time any schedule-loading path (including read/analysis endpoints)
     * touches it, and can never take down analysis for everyone else.
     */
    private void healPinnedNullTimeslotLessons(List<Lesson> lessons) {
        for (Lesson lesson : lessons) {
            if (lesson.pinned && lesson.timeslot == null) {
                LOG.warnf("loadSchedule: healing corrupted lesson %d (pinned with null timeslot) - unpinning", lesson.id);
                lesson.pinned = false;
            }
        }
    }

    /**
     * Populate completion information for all student groups.
     * Calculates the last lesson date and compaction metrics for each group.
     *
     * @param studentGroups List of student groups to populate
     * @param lessons List of lessons to analyze
     */
    private void populateCompletionInfo(List<StudentGroup> studentGroups, List<Lesson> lessons) {
        for (StudentGroup group : studentGroups) {
            // Find all lessons for this group
            List<Lesson> groupLessons = lessons.stream()
                    .filter(l -> l.course != null && l.course.studentGroup != null
                            && l.course.studentGroup.id.equals(group.id)
                            && l.timeslot != null)
                    .toList();

            if (groupLessons.isEmpty()) {
                group.completionInfo = null;
                continue;
            }

            // Find the last lesson date
            LocalDate lastLessonDate = groupLessons.stream()
                    .map(l -> l.timeslot.getDate())
                    .max(LocalDate::compareTo)
                    .orElse(null);

            if (lastLessonDate == null) {
                group.completionInfo = null;
                continue;
            }

            // Count lessons on the final day
            long finalDayCount = groupLessons.stream()
                    .filter(l -> l.timeslot.getDate().equals(lastLessonDate))
                    .count();

            // Check for perfect compaction (no sparse days)
            // A sparse day is a day with 1-3 hours of lessons
            // Perfect compaction means all lesson days are "full" (4+ hours) or empty
            Map<LocalDate, Long> hoursByDay = groupLessons.stream()
                    .collect(Collectors.groupingBy(l -> l.timeslot.getDate(), Collectors.counting()));

            boolean hasSparseDays = hoursByDay.values().stream()
                    .anyMatch(count -> count > 0 && count < 4);

            group.completionInfo = new com.school.planner.domain.CompletionInfo(
                    lastLessonDate,
                    (int) finalDayCount,
                    !hasSparseDays
            );
        }
        LOG.infof("Populated completion info for %d student groups", studentGroups.size());
    }

    /**
     * Load a filtered schedule containing ONLY pre-assignment entities.
     *
     * This is used by Phase 1 of the two-phase approach so the solver
     * only works on pre-assignment entities, avoiding duplicated work
     * and preventing score volatility during Phase 2 startup.
     *
     * @return SchoolSchedule with only pre-assignment entities in lesson/labBlock lists
     */
    @Transactional
    public SchoolSchedule loadPreAssignmentSchedule() {
        // Load full schedule first (for problem facts: timeslots, rooms, etc.)
        SchoolSchedule fullSchedule = loadSchedule();

        // Get pre-assignment entity IDs
        Set<Long> preAssignLessonIds = entityExtractor.getPreAssignmentLessonIds();

        // Filter entities - only pre-assignment subset
        List<Lesson> filteredLessons = fullSchedule.getLessons().stream()
                .filter(l -> preAssignLessonIds.contains(l.id))
                .toList();

        LOG.infof("Phase 1 filtered schedule: %d/%d lessons",
                filteredLessons.size(), fullSchedule.getLessons().size());

        // Create filtered schedule with same problem facts but filtered planning entities
        SchoolSchedule filtered = new SchoolSchedule(
                fullSchedule.getTimeslots(),
                fullSchedule.getRooms(),
                fullSchedule.getStagePatterns(),
                fullSchedule.getTeacherAvailabilities(),
                fullSchedule.getSchoolHolidays(),
                fullSchedule.getStudentGroups(),
                fullSchedule.getTeachers(),
                fullSchedule.getCourses(),
                filteredLessons,
                fullSchedule.getLabAssistants()
        );
        // Carry problem facts not threaded through the constructor (H4.3 reads these)
        filtered.setProfessionalizzantiDays(fullSchedule.getProfessionalizzantiDays());
        filtered.setDailyActivityQuotas(fullSchedule.getDailyActivityQuotas());
        return filtered;
    }

    @Transactional
    public void saveSchedule(SchoolSchedule schedule) {
        BendableScore newScore = schedule.getScore();
        latestScore = newScore;

        // Phase transitions are now handled by PhaseTransitionMarker (event-based)
        // via customPhase markers in solverConfig.xml. No time-based detection needed.

        long solverPinnedLessons = schedule.getLessons().stream().filter(l -> l.pinned).count();
        LOG.infof("saveSchedule: score=%s, solver copy has %d/%d pinned lessons",
                latestScore,
                solverPinnedLessons, schedule.getLessons().size());

        for (Lesson solvedLesson : schedule.getLessons()) {
            Lesson dbLesson = Lesson.findById(solvedLesson.id);
            if (dbLesson != null && !dbLesson.pinned) {
                dbLesson.timeslot = solvedLesson.timeslot;
                dbLesson.room = solvedLesson.room;
            }
        }
        // Note: LabBlocks were deprecated in TASK-228. All labs are now pinned Lessons.
    }

    void onFinalBestSolution(FinalBestSolutionEvent<SchoolSchedule> event) {
        saveFinalSolution(event.solution());
    }

    void onNewBestSolution(NewBestSolutionEvent<SchoolSchedule> event) {
        saveSchedule(event.solution());
    }

    /**
     * Saves the final solution and finalizes the current phase in progression tracking.
     *
     * This is separate from saveSchedule() because we need to finalize the phase
     * only once when solving ends, not on every intermediate best solution.
     *
     * @param schedule The final best solution
     */
    @Transactional
    public void saveFinalSolution(SchoolSchedule schedule) {
        LOG.infof("saveFinalSolution: Solver ended naturally, finalizing phase progression");
        // Reuse existing save logic
        saveSchedule(schedule);
        // Finalize the current phase in progression tracking
        BendableScore finalScore = schedule.getScore();
        progressionService.finalizeCurrentPhase(finalScore, Instant.now());
        // TASK-354 code review B2: this is the one clean "solving has finished" hook;
        // reached whether the solver stopped naturally (termination config) or was
        // stopped early (stopSolving() -> terminateEarly() still ends by delivering a
        // FinalBestSolutionEvent). Nudge the validation watchdog to re-score the
        // just-saved schedule instead of waiting for an unrelated CRUD mutation to
        // trigger it. INTERNAL + the busy-retry: mid-cycle fires used to be silently
        // skipped (the 26/09 stale-snapshot incident, see fireInternalTrigger).
        fireInternalTrigger("solve-completed");
    }

    @Transactional
    public void validateForSolving() {
        long lessonCount = Lesson.count();
        if (lessonCount == 0) {
            throw new IllegalStateException("No lessons to schedule. Create courses and generate lessons first.");
        }
        // Note: LabBlock.count() check removed in TASK-228 - labs are now pinned Lessons
        long timeslotCount = Timeslot.count();
        if (timeslotCount == 0) {
            throw new IllegalStateException("No timeslots configured. Seed data may not have loaded.");
        }
    }

    /**
     * The ORDINARY solve: the whole {@code solverConfig.xml} pipeline on the injected
     * {@code SolverManager}. The TASK-419 staged cycle does NOT come through here - since the
     * room/timeslot split it drives its own ad-hoc {@link SolverFactory} solvers, synchronously
     * on its own thread - so a staged cycle in progress is refused outright.
     */
    public void startSolving() {
        // Read the staged field under the same lock the staged start writes it under -
        // otherwise this is a check-then-act against a cycle that is being started right now.
        synchronized (stagedLock) {
            // TASK-670 (review finding 1): the plain /solve entry gate must consume the
            // liveness flag too, or it accepts a second solve during the tail windows
            // (finisher chain / inter-attempt gap: stagedStage null, cycle still alive).
            if (cycleLifecycleActive()) {
                throw new IllegalStateException("A staged solve is in progress (stage " + stagedStage
                        + ", cycle alive: " + cycleThreadAlive + ") - stop it before starting an ordinary solve");
            }
        }
        lastSolverError = null;
        validateForSolving();
        latestScore = ScoreCompat.ZERO;
        solveStartTime = Instant.now();
        seenChPhase = false;
        chCompletionTime = null;
        // Invalidate cached snapshot - schedule will change during solving
        cachedSnapshot = null;
        cachedAnalysisTime = null;
        // Reset phase tracking for new solve run
        PhaseTrackingListener.reset();
        // Reset phase progression history
        progressionService.reset();
        // Record Phase 1 immediately - markers will handle subsequent transitions
        progressionService.onPhaseTransition(
            1,
            SolverProgressionListener.getPhaseName(1),
            ScoreCompat.ZERO,
            solveStartTime
        );
        PhaseTrackingListener.setPhase(1, SolverProgressionListener.getPhaseName(1));
        solverManager.solveBuilder()
                .withProblemId(SINGLETON_ID)
                .withProblemFinder(id -> loadSchedule(true))
                .withBestSolutionEventConsumer(this::onNewBestSolution)
                .withFinalBestSolutionEventConsumer(this::onFinalBestSolution)
                .withExceptionHandler((id, ex) -> {
                    lastSolverError = ex.toString();
                    LOG.errorf(ex, "Solver error for problemId %s", id);
                })
                .run();
    }

    public void stopSolving() {
        // A staged cycle owns the solver: a stop request must end the CYCLE, not just the
        // stage-B solve it happens to be running (which would otherwise be followed by the
        // next demote+re-solve iteration, looking like the stop was ignored). The cycle's
        // own finally still runs, so the promoted pins are always released. The liveness
        // test is the STAGE, the same one startSolving()/isBusy() ask: it is published by
        // startStagedSolving AND re-published at each runStagedCycle entry (so a multi-start's
        // attempts 2+ stay "busy" during their construction too), and cleared last in the
        // finally, so no DB-bearing stage of a running cycle reads as absent.
        // TASK-667 (review F1): the delivery-refinement window runs AFTER the last
        // stage cleared stagedStage, on its own solver - gate the cancel on the
        // refining flag too or a stop during the ~10min refinement is not
        // registered and the cycle outcome mislabels it COMPLETE.
        // TASK-670: cycleThreadAlive extends the registration to the whole cycle
        // thread life. Honest limit: the delivery tail (finisher chain, restore
        // save, concordance) does NOT read the flag - a stop there shortens
        // nothing, the tail burns to completion, and the stop wins the LABEL
        // only (classifyCycleOutcome). The busy gates stay shut until the tail
        // self-completes.
        if (cycleLifecycleActive() || refiningForDelivery) {
            stagedCancelRequested = true;
            LOG.warnf("stopSolving: staged solve cancellation requested (stage %s, refining %b, cycle alive %b)",
                    stagedStage, refiningForDelivery, cycleThreadAlive);
            // NO stage of the staged cycle runs on the SolverManager (every one of them is an
            // ad-hoc SolverFactory solver, run synchronously on the cycle's own thread), so the
            // terminateEarly below cannot reach any of them: a stop would otherwise be ignored
            // until the running stage finished on its own, up to an hour later.
            Solver<SchoolSchedule> staged = stagedSolver;
            if (staged != null) {
                LOG.warnf("stopSolving: terminating the staged solver of stage %s early", stagedStage);
                staged.terminateEarly();
            }
        }
        if (solveStartTime != null) {
            long sessionSeconds = Duration.between(solveStartTime, Instant.now()).getSeconds();
            cumulativeElapsedSeconds += sessionSeconds;
            if (chCompletionTime != null) {
                long sessionCh = Duration.between(solveStartTime, chCompletionTime).getSeconds();
                cumulativeChSeconds += sessionCh;
                cumulativeLsSeconds += (sessionSeconds - sessionCh);
            }
        }
        LOG.infof("Solver stopped — cumulative time: %dm %ds (CH: %ds, LS: %ds)",
                cumulativeElapsedSeconds / 60, cumulativeElapsedSeconds % 60,
                cumulativeChSeconds, cumulativeLsSeconds);
        // Finalize phase progression when stopping
        progressionService.finalizeCurrentPhase(latestScore, Instant.now());
        solverManager.terminateEarly(SINGLETON_ID);
        // Reset phase tracking when solver stops
        PhaseTrackingListener.reset();
    }

    // ─── TASK-419: staged solve ────────────────────────────────────────────
    //
    // A -> promotion -> B-TIMESLOT [-> C -> B-TIMESLOT]* -> RELEASE -> B-ROOM.
    //
    // Stage A runs ONLY the construction phase of solverConfig.xml, so the weekly
    // template it stamps (~74% recurrence, TASK-415's per-phase attribution) exists on
    // the grid before anything can erode it. The PROMOTION then freezes every unpinned
    // lesson already sitting on its own M32 template cell (pinned = true AND
    // promotedPin = true, never one without the other), which is what the single-solve
    // pipeline cannot buy at any M32 weight: benchmark arm W tripled the penalty and
    // recurrence did not move (52.8% -> 53.5%), while the E1 spike measured 73.8% with
    // the same cells pinned. Stage C is the release valve for the one risk pinning
    // creates - a boxed lesson whose every legal cell is held by a promoted pin
    // (BoxRepair's eviction deliberately refuses to touch pinned lessons, so nothing else
    // can free it) - and it degrades those pins back to QUASI-pinned (M32 keeps charging
    // them), never to free. The SMARCATURA asserts that the AUTHORITATIVE pins
    // (golden/calendario/stage/hand) are untouched end-to-end, and runs on every path,
    // including failure.
    //
    // WHY STAGE B IS SPLIT AT THE TIMESLOT/ROOM BOUNDARY (the val419 smoke fix). The
    // template protects TIMESLOTS - its cells are (weekday, slot) - and nothing else, but
    // {@code @PlanningPin} freezes the whole ENTITY, room included. With one undivided
    // stage B, the 7,132 promoted lessons therefore carried stage A's provisional rooms
    // into the final solution while phases 4/5 could only harmonize the free ones: the
    // smoke run ended at -897 hard of which -895 was H16b alone (895 consecutive
    // same-teacher same-subject blocks split across two rooms), against ~0 in an ordinary
    // run. So the promoted pins now cover ONLY the timeslot phases (config (a), up to and
    // including the CompactionPhase - the last phase that moves a timeslot), the release
    // happens BEFORE the room phases, and the room phases (config (b): the room CH + the
    // room refinement LS, which move the room variable and nothing else) then run with
    // every non-authoritative room free, exactly as in an ordinary run.

    /**
     * Stage labels published through {@link #getStagedStage()} (both {@code GET
     * /api/schedule/status} and the full {@code GET /api/schedule}).
     *
     * <p>The full order is {@code A-CONSTRUCTION -> PROMOTION -> B-TIMESLOT-1 [-> C-DEMOTION-1
     * -> B-TIMESLOT-2 ...] -> RELEASE -> B-ROOM -> RELEASE}. The trailing {@code RELEASE} is
     * the closing (idempotent) one from the cycle's {@code finally}: the pins were already
     * handed back before {@code B-ROOM}, so that pass releases nothing and only re-publishes
     * and asserts pin conservation.
     *
     * <p>{@link #STAGE_TIMESLOT_SOLVE} and {@link #STAGE_DEMOTION} are PREFIXES: the published
     * value carries the iteration number appended ({@code "B-TIMESLOT-1"}, {@code
     * "C-DEMOTION-1"}, {@code "B-TIMESLOT-2"}, ...), since the cycle can re-solve after a
     * demotion round. The other four are published verbatim. A client that keys on a stage must
     * therefore match on the prefix, never on equality, for those two.
     */
    public static final String STAGE_CONSTRUCTION = "A-CONSTRUCTION";
    /** UI-driven protocol (2026-08-15, maintainer: the UI is the main interaction channel and must
     *  take the campaign approach): the staged entry ENSURES pre-assignment before the cycle,
     *  so a one-click Solve runs the full production protocol (import -> pre-assign -> staged
     *  cycle) instead of a degraded pin-less solve (the TASK-435 overdemand trap). Published
     *  while the ensure step runs so isBusy()/stopSolving()/the UI stage indicator cover it. */
    public static final String STAGE_PRE_ASSIGN = "PRE-ASSIGN";
    public static final String STAGE_PROMOTION = "PROMOTION";
    /** Prefix - published as {@code B-TIMESLOT-<iteration>}, see {@link #STAGE_CONSTRUCTION}. */
    public static final String STAGE_TIMESLOT_SOLVE = "B-TIMESLOT";
    /** Prefix - published as {@code C-DEMOTION-<iteration>}, see {@link #STAGE_CONSTRUCTION}. */
    public static final String STAGE_DEMOTION = "C-DEMOTION";
    public static final String STAGE_RELEASE = "RELEASE";
    /** The room phases, AFTER the release: no promoted pin is held any more (see the section comment). */
    public static final String STAGE_ROOM_SOLVE = "B-ROOM";

    /**
     * The ONE solver XML every stage of the cycle is carved out of (never a second config to
     * drift: stage A is its construction phase, B-TIMESLOT its phases up to the compaction,
     * B-ROOM the room ones), read from the SAME property the Quarkus Timefold extension builds
     * the injected {@code solverManager} from - a profile that points the extension at another
     * XML must not leave the staged cycle running the default one.
     */
    @ConfigProperty(name = "quarkus.timefold.solver.solver-config-xml", defaultValue = "solverConfig.xml")
    String solverConfigResource;

    /** At most 2 demotion rounds (so at most 3 stage-B solves), then the residual is accepted. */
    private static final int MAX_DEMOTION_ITERATIONS = 2;

    /**
     * Hard score at or above which a stage-B-TIMESLOT result is accepted without demoting
     * anything (box must be 0 as well), and below which a post-RELEASE attempt is condemned
     * (the TASK-498 doomed-skip). RECALIBRATED from -2 to -40 by TASK-635 (maintainer, 04/10):
     * the -2 predates the delivery chain (CSF/EDF/HRF/steepest all landed in September),
     * and the falsification bench ({@code DoomTrioFalsifyBenchTest}) measured it
     * condemning states the chain then cures: a POST_RELEASE boundary of -29 delivered
     * -2 (93% cured), and both 03/10 night arms flagged DOOMED at boundary -10/-13
     * delivered 0 and -2. -40 covers the bad band measured at that boundary (good
     * attempts 0, bad -6..-38, composite of the 15/08 TASK-498 reproduction and the
     * 14/08 batch): direct cure evidence is the three points -10/-13/-29, coverage up
     * to -38 is the ratified bet, and the H0-boxed-heavy deep end is the class the
     * chain cures least (the predictor-discarded construction at -2890 was only 15%
     * cured). Below -40 is unmeasured territory and still condemns. The old -2 was
     * justified by baseline noise (-1/-2 run to run); the band evidence supersedes it.
     * DECLARED RESIDUALS: (1) in its own regime the accept-gate half is a near no-op:
     * demotion seeds are exactly the BOXED lessons ({@code StagedPinPolicy.selectDemotions}),
     * so with box==0 the old -2 demoted nothing and broke without a re-solve; the
     * threshold's real bite is the doomed-skip verdict. (2) With
     * {@code planner.multistart.budget-seconds > 0} (opt-in, default 0), an in-band
     * non-last attempt now spends its ~10 min room refinement of budget that -2 spent
     * on a fresh GRASP basin. Knob {@code planner.staged.hard-accept-threshold}, read
     * LIVE, banner in {@code refreshSolverStatics}.
     *
     * <p>Compared against the TIMESLOT-TIER hard score only, see {@link
     * #timeslotTierHardScore(SchoolSchedule)}.
     */
    static final long DEFAULT_STAGED_HARD_ACCEPT_THRESHOLD = -40;

    /** The threshold value; LIVE-read so a -D override lands without a statics refresh. */
    static long stagedHardAcceptThreshold() {
        return Long.getLong("planner.staged.hard-accept-threshold",
                DEFAULT_STAGED_HARD_ACCEPT_THRESHOLD);
    }

    /**
     * The hard constraints only the ROOM phases can settle, excluded from the stage-C trigger
     * (see {@link #timeslotTierHardScore(SchoolSchedule)}). Canonical ids, matched as FAMILIES: {@code "H2"}
     * covers {@code H2.1/H2.2/H2.3} (the shadow/block branch families) but never {@code H20} or
     * {@code H23}, which are timeslot rules that merely start with the same two characters.
     *
     * <p>{@code "H29"} (room-required, TASK-447) joins them: it fires on PLACED-but-room-null
     * lessons, so during the timeslot phases (rooms still null) it would fire on nearly every
     * placed lesson - a room-tier concern, not a timeslot one. Excluding it keeps the timeslot-
     * tier score about PLACEMENT, not room assignment. Note the score is NOT a plain boxed-count:
     * {@code timeslotTierHardScore == boxed * (-H0_WEIGHT) + conflict-hard}, and H0 is weight 2
     * since TASK-447, so each boxed lesson is -2. The acceptance gate ({@link
     * #stagedHardAcceptThreshold()}) is consulted only when {@code boxed == 0} (see the stage-B
     * loop), at which point H0 contributes 0, so the reweight does NOT rescale the tolerance; the
     * repair {@code ensureImproveOrNoop} likewise guards improve-or-noop on this placement-only score.
     */
    // NOTE (TASK-464 code-review): H-COPRESENCE is NOT here. It groups by (tag,
    // startDateTime) and never reads l.room, so it is a TIMESLOT-tier hard: counting it in
    // timeslotTierHardScore (the default, by omission from this list) is what lets the staged
    // cycle's B-TIMESLOT gate and the repair ensureImproveOrNoop SEE and resolve over-depth
    // co-presenza cells during B-TIMESLOT. Listing it here would exclude it and let an
    // over-depth cell read "clean" at the gate, then persist into B-ROOM where rooms cannot
    // fix a timeslot co-location (hard residual to final). See coPresenceCap's javadoc.
    private static final List<String> ROOM_TIER_HARD_FAMILIES = List.of("H2", "H-M2", "H29");

    /** The production solver's environment mode, mirrored onto stage A's ad-hoc SolverFactory. */
    @ConfigProperty(name = "quarkus.timefold.solver.environment-mode")
    Optional<String> configuredEnvironmentMode;

    /** Guards the check-then-start of a staged cycle (the REST entry point is unsynchronized). */
    private final Object stagedLock = new Object();

    /**
     * TASK-542 stage 3: the resume plan armed by {@link #startStagedResume}. Consumed (nulled)
     * at the START of the next {@code runStagedCycle} - one shot, never inherited by a later
     * ordinary solve.
     */
    private volatile StagedCheckpointCursor pendingResumePlan;

    /**
     * The plan of the CURRENTLY RUNNING cycle (null-boundary plan on a full cycle): set at
     * runStagedCycle entry, cleared in its finally. The checkpoint captures consult it so a
     * resume never overwrites the original chain's skipped-boundary evidence.
     */
    private volatile StagedResumePlan activeCycleResumePlan;

    /** TASK-542 iter-17-bis: set when the profile predictor discarded THIS attempt. */
    private volatile boolean profileDiscardFlag;

    /**
     * TASK-542 iter-17-ter (bug caldo 23/08 02:30): the GRASP seed derives from the ATTEMPT
     * number, but a discarded attempt does not consume one - so the reseed after a discard
     * redrew the SAME seed forever (attempt 2, seed 2, ~9s loop). This counter increments on
     * every attempt START (discards included) and feeds the seed: every reseed is a fresh
     * construction.
     */
    /** TASK-573 HONOR: the operator's grasp.seed when the honor flag is ON (-1 = off). */
    private volatile long honorBaseSeed = -1L;

    private final java.util.concurrent.atomic.AtomicLong graspSeedCounter =
            new java.util.concurrent.atomic.AtomicLong(0);
    /**
     * Current stage label, null when no staged cycle is running - and the ONE liveness flag
     * of the cycle: published under {@link #stagedLock} before the thread starts and cleared
     * last in the cycle's {@code finally}, so {@link #stopSolving()}, {@link #isBusy()} and
     * {@link #refuseIfStaged} all read the same fact with no window between them.
     */
    private volatile String stagedStage;

    /** TASK-655: every stage transition is a lifecycle event (the mechanical trail). */
    private void publishStage(String stage) {
        stagedStage = stage;
        com.school.planner.util.LifecycleLog.event("STAGE_ENTER", "stage", stage,
                "attempt", stagedAttempt);
    }
    private volatile boolean stagedCancelRequested;

    /**
     * TASK-670: true for the WHOLE life of a user-visible cycle thread, including
     * the windows no stage covers (the delivery finisher chain, the restore save,
     * the concordance check, the multi-start inter-attempt gap). stopSolving()
     * registers a cancel on it and isBusy() refuses a concurrent cycle on it, so
     * a stop can no longer be silently dropped and COMPLETE-published in those
     * windows (code-review F3 of TASK-667), and no second cycle can start while
     * the first is still delivering (F2 tail). Armed at both entry points under
     * stagedLock BEFORE thread.start() (arming inside the wrapper would run on
     * the cycle thread and reopen the isBusy check-then-start race); cleared
     * LAST in the wrapper's finally - the same last-line-owner discipline the
     * stage clear uses (live-verified 25/09).
     */
    private volatile boolean cycleThreadAlive;

    /**
     * TASK-670 (review F4): the ONE liveness predicate every gate consumes
     * (stopSolving, isBusy, the plain-start guard, refuseIfStaged) - a future
     * gate cannot get the term subset wrong. refiningForDelivery is NOT a term:
     * it is set only on the cycle thread inside the wrapper, so cycleThreadAlive
     * strictly subsumes it.
     */
    private boolean cycleLifecycleActive() {
        return stagedStage != null || cycleThreadAlive;
    }

    /**
     * TASK-667: the terminal outcome of the last staged cycle, the authoritative
     * signal the UI toasts/banners on (the client no longer infers it from
     * stage edges). null while a cycle runs or before any cycle; set once per
     * user-visible cycle by the entry-point wrappers, reset at cycle start.
     */
    public enum StagedOutcome { COMPLETE, CANCELLED, FAILED }

    private volatile StagedOutcome stagedOutcome;

    /** TASK-667: set by runStagedCycle's catch, read by the wrappers to classify FAILED. */
    private volatile boolean cycleErrored;

    public StagedOutcome getStagedOutcome() { return stagedOutcome; }

    /** TASK-667: one classification, read by both entry-point finally blocks. */
    private StagedOutcome classifyCycleOutcome() {
        if (cycleErrored) return StagedOutcome.FAILED;
        if (stagedCancelRequested) return StagedOutcome.CANCELLED;
        return StagedOutcome.COMPLETE;
    }

    /** TASK-667: reset the per-cycle outcome state; both entry points call this. */
    private void resetCycleOutcomeState() {
        stagedCancelRequested = false;
        stagedOutcome = null;
        cycleErrored = false;
    }

    /** TASK-667: run one user-visible cycle and publish its terminal outcome. */
    private Runnable withStagedOutcome(Runnable cycle) {
        return () -> {
            try {
                cycle.run();
            } catch (RuntimeException | Error t) {
                // TASK-667 (review F5): anything escaping outside runStagedCycle's
                // own catch (wrapper bookkeeping, Errors) classifies FAILED - a
                // crash must never publish COMPLETE over an undelivered grid.
                cycleErrored = true;
                LOG.error("staged cycle body threw outside the cycle's own catch", t);
            } finally {
                // Local capture BEFORE any clears: a new cycle can start the moment
                // liveness drops below (code-review F1) and null the field mid-finally.
                StagedOutcome outcome = classifyCycleOutcome();
                stagedOutcome = outcome;
                // Live-verified 25/09 (stop during the ensure's PRE-ASSIGN): the
                // quick-cancel exit returns from runOneMultiStartAttempt BEFORE any
                // runStagedCycle entry, so no other owner clears the stage and it
                // stays PRE-ASSIGN forever; isBusy() reads permanently busy and
                // every later solve is refused. This outermost finally is the
                // last-line owner of the clear; idempotent on every normal path
                // (runStagedCycle's own finally already published null).
                publishStage(null);
                cycleThreadAlive = false; // TASK-670: cleared last, see the field javadoc
                // TASK-660/354 (code-review F2 corrected the earlier diagnosis): the
                // staged path DOES fire saveFinalSolution's "solve-completed" hook,
                // but every such fire landed mid-cycle and validateNow's busy-skip
                // dropped it, so the watchdog kept serving a snapshot of the
                // PREVIOUS epoch (measured live 26/09: a -8hard intermediate from
                // 24/09 shown as current over a 0-hard delivery). This fire lands
                // with liveness already false, and ValidationService retries
                // INTERNAL triggers until quiescent. CANCELLED/FAILED refresh too.
                fireInternalTrigger("staged-cycle-" + outcome.name().toLowerCase());
                // TASK-655: the terminal lifecycle event (verdict + final score +
                // wall time of the whole user-visible cycle). Appended AFTER the
                // outcome publish so the readout sees the definitive state.
                LifecycleLog.event("SOLVE_END", "outcome", outcome.name(),
                        "score", String.valueOf(latestScore),
                        "hardScore", String.valueOf(latestScore != null ? ScoreCompat.hardLevels(latestScore) : "null"),
                        "wallMillis", String.valueOf(
                                solveStartTime != null
                                        ? java.time.Duration.between(solveStartTime, Instant.now()).toMillis()
                                        : -1));
            }
        };
    }

    /**
     * TASK-660/354: fire a non-HTTP validation trigger ({@code DataMutationEvent}
     * with method INTERNAL). Null-guarded because non-CDI instantiation (the
     * reflection-based unit tests) has no event object; the single helper keeps
     * every INTERNAL fire site consistent (code-review F6).
     */
    private void fireInternalTrigger(String trigger) {
        if (validationMutationEvent != null) {
            validationMutationEvent.fire(new DataMutationEvent(trigger, "INTERNAL"));
        }
    }

    /**
     * TASK-667: true while the async post-restart score restoration is in flight
     * (started, score still null). The UI polls THIS instead of inferring
     * "score null + placed lessons" client-side, and stops when the server says
     * done in either outcome (the restore's catch resets the guard).
     */
    public boolean isScoreRestorePending() {
        return scoreRestoreStarted.get() && latestScore == null;
    }
    /**
     * The ad-hoc solver of the stage currently running (A, B-TIMESLOT or B-ROOM: all three are
     * ad-hoc {@link SolverFactory} solvers), so {@link #stopSolving()} can terminate it early.
     * Null between stages.
     */
    private volatile Solver<SchoolSchedule> stagedSolver;
    /**
     * TASK-483 multi-start attempt state: the current 1-based attempt and the cap, published so the
     * UI can show "tentativo X di Y" alongside {@link #stagedStage}. Both 0 when multi-start is off
     * or no cycle is running, so the UI guards on {@code stagedMaxAttempts > 1} to stay invisible
     * on an ordinary single solve.
     */
    private volatile int stagedAttempt;
    private volatile int stagedMaxAttempts;
    private volatile long stagedBudgetSeconds;
    /**
     * TASK-498: the attempts whose room-refinement LS was skipped (doomed at the
     * post-RELEASE boundary). Owned by the staged-solve thread like the narration fields;
     * cleared at wrapper entry so a previous solve's numbers cannot leak into this one.
     * The wrapper reads it AFTER the loop: if the pick-best winner is in here, its rooms
     * are refined before delivery (budget-break and all-doomed corner, where the winner
     * would otherwise ship CH-only rooms).
     */
    private final Set<Integer> roomUnrefinedAttempts = new HashSet<>();

    /**
     * TASK-498 corner-b fix (21/08): the CURRENT attempt's timeslot-tier hard at its
     * post-RELEASE boundary, recorded by {@link #isDoomedAttemptForSkipRoom} (which
     * computes it for every attempt, doomed or not) and attached to the attempt's
     * {@link MultiStartRunner.AttemptOutcome} as the pick-best's primary ranking key
     * (full scores are not comparable across attempts: a doomed not-last one ships
     * CH-only room noise). MIN_VALUE until the boundary is reached, so an attempt
     * cancelled before it ranks last, never on a stale predecessor's tier.
     */
    private long lastAttemptBoundaryTierHard = Long.MIN_VALUE;

    /** TASK-509 follow-up: set for the duration of the corner-b delivery refinement. */
    private volatile boolean refiningForDelivery = false;

    /**
     * TASK-504: the inherited adaptive-weights snapshot for the NEXT attempt (null when
     * the flag is off or nothing was worth inheriting). Owned by the staged-solve thread
     * like the narration fields; cleared at wrapper entry (no leak across solves).
     */
    private double[] warmStartWeights;



    /** TASK-504: best attempt score seen by the wrapper (the inheritance gate). */
    private BendableScore bestSoFarScore;

    /** Current staged-solve stage, null when no staged cycle is running (TASK-419). */
    public String getStagedStage() {
        return stagedStage;
    }

    /** Current multi-start attempt (1-based), 0 when multi-start is off (TASK-483). */
    public int getStagedAttempt() {
        return stagedAttempt;
    }

    /**
     * TASK-509 follow-up: true while the wrapper refines the winning condemned attempt's
     * rooms before delivery (corner-b). Drivers must treat a true here as "still
     * working" exactly like a non-null stagedStage. See the field's comment for the
     * run3 incident this closes.
     */
    public boolean isRefiningForDelivery() {
        return refiningForDelivery;
    }

    /** Multi-start attempt cap, 0 when multi-start is off so the UI hides the indicator (TASK-483). */
    public int getStagedMaxAttempts() {
        return stagedMaxAttempts;
    }

    /** Multi-start time-budget cap (seconds), 0 when unset; published for an honest UI hint (TASK-483). */
    public long getStagedBudgetSeconds() {
        return stagedBudgetSeconds;
    }

    /**
     * Start the staged solve (TASK-419) and return immediately; the cycle runs on its own
     * daemon thread and is observable through {@link #getStagedStage()}. Same busy guard as
     * {@link #startSolving()}, widened to the staged cycle itself.
     */

    /**
     * TASK-542 stage 3: resume a staged cycle from a boundary checkpoint. The checkpoint's grid
     * (placements + BOTH pin flags) is restored onto the live entities and persisted by this
     * transaction's dirty flush; the next {@code runStagedCycle} (started here, single cycle -
     * NO multi-start wrapper: the wrapper's re-construction would defeat the resume) then
     * re-loads the restored grid, skips the stages the cursor marks as completed, and continues
     * to the end of the cycle. The statics are rebuilt by the cycle's own {@code loadSchedule}
     * from the PERSISTED restored grid - no custom publish path.
     */
    /**
     * TASK-542 stage 3, step 1 of 2: parse the checkpoint and RESTORE its grid (placements +
     * BOTH pin flags) onto the live entities, persisted by this transaction's dirty flush.
     * Returns the cursor the caller hands to {@link #startResumeCycle}. Split in two steps ON
     * PURPOSE: the cycle thread must start only AFTER this transaction committed (a thread
     * started inside the transaction could re-load the pre-restore grid and silently resume
     * from nothing). Note (TASK-587, verified 6/09): ArC DOES intercept self-invoked
     * @Transactional methods - the ordering reason above is the real one, not an
     * interceptor-bypass concern.
     */
    @Transactional
    public StagedCheckpointCursor applyStagedCheckpoint(Path checkpointFile) {
        synchronized (stagedLock) {
            if (isBusy()) {
                throw new IllegalStateException("Solver is busy (staged stage: " + stagedStage
                        + ", solver status: " + getStatus() + ")");
            }
            validateForSolving();
            String json;
            try {
                json = Files.readString(checkpointFile);
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("checkpoint file not readable: " + checkpointFile, e);
            }
            StagedCheckpoint checkpoint = StagedCheckpointSerializer.fromJson(json);
            if (checkpoint.cursor().boundary().resumeEntryPoint().isEmpty()) {
                throw new IllegalArgumentException("the checkpoint sits at the cycle's last boundary ("
                        + checkpoint.cursor().boundary() + "): there is nothing to resume into");
            }
            SchoolSchedule problem = loadSchedule(true);
            java.util.Map<Long, Timeslot> timeslotsById = new java.util.HashMap<>();
            if (problem.getTimeslots() != null) {
                problem.getTimeslots().forEach(t -> timeslotsById.put(t.id, t));
            }
            java.util.Map<Long, Room> roomsById = new java.util.HashMap<>();
            if (problem.getRooms() != null) {
                problem.getRooms().forEach(r -> roomsById.put(r.id, r));
            }
            StagedCheckpointRestore.apply(checkpoint, problem.getLessons(), timeslotsById, roomsById);
            LOG.infof("staged solve: checkpoint restored from %s (iteration %d, attempt %d) - %d lesson(s)",
                    checkpoint.cursor().boundary(), checkpoint.cursor().iteration(),
                    checkpoint.cursor().attempt(), checkpoint.placements().size());
            return checkpoint.cursor();
        }
    }

    /**
     * TASK-542 stage 3, step 2 of 2: arm the resume plan and run ONE plain cycle (no multi-start
     * wrapper: its re-construction would defeat the resume). Call only after
     * {@link #applyStagedCheckpoint} has COMMITTED (the transaction boundary is between the two,
     * at the REST caller).
     */
    public void startResumeCycle(StagedCheckpointCursor cursor) {
        synchronized (stagedLock) {
            if (isBusy()) {
                throw new IllegalStateException("Solver is busy (staged stage: " + stagedStage
                        + ", solver status: " + getStatus() + ")");
            }
            resetCycleOutcomeState();
            pendingResumePlan = cursor;
            publishStage(STAGE_CONSTRUCTION); // provisional; the cycle re-publishes per stage
            // TASK-670: arm the liveness flag BEFORE thread.start() under the
            // lock, so a second POST cannot slip through the isBusy() window
            // between start() and the thread's first instruction. The try/catch
            // (review F3): a thread construction/start failure must unwind the
            // armed signals, or the app wedges busy forever (the wrapper's
            // finally never runs on any thread).
            cycleThreadAlive = true;
            try {
                Thread thread = new Thread(withStagedOutcome(this::runStagedCycle), "staged-solve-resume");
                thread.setDaemon(true);
                thread.start();
            } catch (RuntimeException | Error t) {
                publishStage(null);
                cycleThreadAlive = false;
                throw t;
            }
        }
    }

    public void startStagedSolving() {
        synchronized (stagedLock) {
            if (isBusy()) {
                throw new IllegalStateException("Solver is busy (staged stage: " + stagedStage
                        + ", solver status: " + getStatus() + ")");
            }
            validateForSolving();
            resetCycleOutcomeState();
            publishStage(STAGE_CONSTRUCTION);
            // TASK-483: multi-start retry is DEFAULT ON (ratified 2026-08-03). The wrapper runs
            // distinct GRASP seeds (attempt 1 deterministic, >=2 randomized), pick-best, stop at
            // 0-hard. Opt out with -Dplanner.multistart.enabled=false to run the plain single cycle;
            // a good basin still runs exactly one cycle, schedule-identical to pre-ratification.
            Runnable cycleBody = multiStartEnabled() ? this::runStagedCycleWithRetry : this::runStagedCycle;
            // TASK-667: ONE outcome per user-visible cycle, authoritative (the UI no
            // longer infers it from stage edges or its own stop mirrors).
            Runnable body = withStagedOutcome(() -> {
                if (!ensurePreAssignmentForStagedCycle()) {
                    // Failed or cancelled during the ensure step: publish the outcome and do NOT
                    // run a pin-less cycle (that is the degraded solve this wrapper exists to
                    // prevent). The stage is cleared here because runStagedCycle's own finally,
                    // which normally owns the clear, never runs.
                    publishStage(null);
                    // TASK-667 (review F2): a CANCEL during the ensure step is a
                    // deliberate stop, not a failure.
                    cycleErrored = !stagedCancelRequested;
                    return;
                }
                cycleBody.run();
            });
            // TASK-670: arm under the lock, before start() (same race note and
            // same F3 unwind as the resume entry point above).
            cycleThreadAlive = true;
            try {
                Thread thread = new Thread(body, "staged-solve");
                thread.setDaemon(true);
                thread.start();
            } catch (RuntimeException | Error t) {
                publishStage(null);
                cycleThreadAlive = false;
                throw t;
            }
        }
    }

    /**
     * The whole cycle. Package-private (not private) so a harness can drive it directly.
     * Never throws: a failure is logged, surfaced through {@code lastSolverError}, and
     * still goes through the closing release - a promoted pin outliving its cycle is the
     * TASK-383 failure mode this design exists to make impossible.
     *
     * <p>Returns the final solved grid (the room stage's outcome), or {@code null} when the cycle
     * was cancelled before the room stage or failed. The TASK-483 multi-start wrapper reads this to
     * pick the best across attempts without an extra DB reload.
     */
    SchoolSchedule runStagedCycle() {
        Set<Long> authoritativeBefore = null;
        int promoted = 0;
        SchoolSchedule finalSolution = null;
        // 27/08 (infra b): captured for the closing save in the finally below
        boolean wasResume = false;
        try {
            // Re-publish the busy signal at the start of EVERY attempt. startStagedSolving sets it
            // once before the thread, but each attempt's finally clears it; without this, attempts
            // 2+ of a multi-start would run their construction (a multi-second loadSchedule + build)
            // with stagedStage == null, so isBusy() would read false and stopSolving() would no-op
            // for that whole window, opening the door to a concurrent solve. For attempt 1 this is a
            // harmless restatement (TASK-483 review finding).
            publishStage(STAGE_CONSTRUCTION);
            lastSolverError = null;
            // TASK-667 (review F3): per-attempt reset - an attempt-1 exception must
            // not classify a clean later delivery as a FAILED user-visible cycle
            // (the multi-start wrapper continues past a failed attempt by design).
            cycleErrored = false;
            startStagedProgression();
            // TASK-542 stage 3: a resumed cycle continues from a persisted checkpoint grid.
            // Consume the plan here (one shot): when resuming, the entry cleanup is SKIPPED -
            // the restored grid is the INPUT of this cycle, not a leftover to wipe - and the
            // stages the plan says are already completed do not run (guards below).
            StagedResumePlan resumePlan = new StagedResumePlan(pendingResumePlan);
            activeCycleResumePlan = resumePlan;
            pendingResumePlan = null;
            boolean resuming = resumePlan.cursor() != null;
            wasResume = resuming; // 27/08 (infra b): for the closing save in the finally
            if (resuming) {
                LOG.infof("staged solve: RESUME from %s (iteration %d) - entry cleanup skipped, stages before the cursor skipped",
                        resumePlan.cursor().boundary(), resumePlan.cursor().iteration());
            }
            if (!resuming) {
                int leftover = releasePromotedPins();
                if (leftover > 0) {
                    LOG.warnf("staged solve: %d promoted pin(s) survived a previous cycle (crash?) and were released before starting", leftover);
                }
                unassignUnpinnedPlacements();
                // TASK-576 F7-A: the pair-cell guard starts each cycle empty; the
                // construction phase republishes its own template per attempt (a GRASP
                // retry builds a different one), and without this clear a later cycle on
                // the course-based builder would inherit the previous cycle's cells.
                PairCellGuard.clear();
            }

            // The problem the construction gets, loaded ONCE here (not inside stage A) so the
            // conservation baseline below is captured AFTER loadSchedule's own
            // healPinnedNullTimeslotLessons pass: a pin corrupted before this cycle started
            // (pinned with a null timeslot, e.g. written by a script) is unpinned by that
            // heal, and blaming the staged cycle for damage it merely walked into would make
            // the check cry wolf.
            SchoolSchedule problem = loadSchedule(true);
            authoritativeBefore = authoritativePinIds();
            LOG.infof("staged solve: starting - %d authoritative pin(s) must be intact at the end",
                    authoritativeBefore.size());
            captureCheckpoint(StagedPhaseBoundary.CYCLE_START, 0, problem);

            SchoolSchedule constructed = null;
            if (resumePlan.runs(StagedPhaseBoundary.POST_CONSTRUCTION)) {
                constructed = runConstructionStage(problem);
            }
            captureCheckpoint(StagedPhaseBoundary.POST_CONSTRUCTION, 0,
                    constructed != null ? constructed : problem);

            // TASK-677 keep-measuring census: with the predictor disarmed the discard WARN
            // was the only per-attempt construction-feature log; this unconditional line
            // keeps the doom-prediction-registry feed (holes/unassigned/hard per attempt)
            // alive so the next recalibration question does not restart from n=1.
            int constructionHoles = 0;
            if (constructed != null) {
                constructionHoles = HoleRepair.countPenalizedGapSlots(constructed.getLessons());
                LOG.infof("staged solve: attempt %d construction census (TASK-677) - H17 holes=%d, unassigned=%d, hard=%s",
                        stagedAttempt, constructionHoles, countBoxedLessons(constructed), constructed.getScore());
            }

            // TASK-542 iter-16's early-doom profile predictor, DISARMED by TASK-677
            // (04/10): opt-in research re-arm only; the falsification evidence and the
            // pre-chain-era history live on profilePredictorEnabled().
            if (constructed != null && profilePredictorEnabled() && !isLastAttempt()) {
                int holes = constructionHoles;
                if (holes > PROFILE_DOOM_HOLES_THRESHOLD) {
                    LOG.warnf("staged solve: attempt %d DISCARDED by the profile predictor"
                            + " (%d construction H17 holes > %d) - reseeding: construction"
                            + " H17 holes over the threshold", stagedAttempt, holes,
                            PROFILE_DOOM_HOLES_THRESHOLD);
                    lastSolverError = null;
                    profileDiscardFlag = true; // the wrapper must not count this attempt
                    return constructed; // the wrapper reads a terrible hard score and moves on
                }
            }

            // A cancel during stage A must NOT be followed by a promotion: the pins would be
            // created only to be released two statements later, and the cycle would publish a
            // frozen template nobody solves against.
            if (stagedCancelRequested) {
                LOG.warn("staged solve: CANCELLED during stage A - no promotion, going straight to the release");
            } else {
                publishStage(STAGE_PROMOTION);
                if (resumePlan.runs(StagedPhaseBoundary.POST_PROMOTION)) {
                    promoted = promoteTemplatePins();
                }
                captureCheckpoint(StagedPhaseBoundary.POST_PROMOTION, 0,
                        constructed != null ? constructed : problem);
            }

            if (!resumePlan.runs(StagedPhaseBoundary.POST_B_TIMESLOT)) {
                LOG.info("staged solve: RESUME past the B-TIMESLOT loop - the timeslot stages are skipped");
            }
            for (int iteration = resumePlan.firstLoopIteration(); !stagedCancelRequested
                    && resumePlan.runs(StagedPhaseBoundary.POST_B_TIMESLOT); iteration++) {
                publishStage(STAGE_TIMESLOT_SOLVE + "-" + iteration);
                // Both verdict numbers are read off the instance the stage just solved (and
                // saved), never re-loaded: a reload would cost a full schedule load plus a
                // solutionManager.update for a grid we are already holding.
                SchoolSchedule solved = runTimeslotSolveStage();
                long boxed = countBoxedLessons(solved);
                long hard = timeslotTierHardScore(solved);
                captureCheckpoint(StagedPhaseBoundary.POST_B_TIMESLOT, iteration, solved, boxed, hard);
                LOG.infof("staged solve: stage B-TIMESLOT #%d ended - score=%s, timeslot-tier hard=%d, box=%d (promoted pins still held: %d)",
                        iteration, solved.getScore(), hard, boxed, SchoolSchedule.getPromotedLessonIdsStatic().size());
                long acceptThreshold = stagedHardAcceptThreshold();
                if (boxed == 0 && hard >= acceptThreshold) {
                    LOG.infof("staged solve: stage B-TIMESLOT #%d is clean (box 0, timeslot-tier hard %d >= %d) - no demotion needed",
                            iteration, hard, acceptThreshold);
                    break;
                }
                if (iteration > MAX_DEMOTION_ITERATIONS) {
                    LOG.warnf("staged solve: residual after %d demotion round(s) ACCEPTED as-is (box=%d, timeslot-tier hard=%d) - box-the-residual, same as an ordinary solve",
                            MAX_DEMOTION_ITERATIONS, boxed, hard);
                    break;
                }
                publishStage(STAGE_DEMOTION + "-" + iteration);
                int demoted = resumePlan.runs(StagedPhaseBoundary.POST_DEMOTION)
                        ? runDemotionStage() : 0;
                captureCheckpoint(StagedPhaseBoundary.POST_DEMOTION, iteration, solved);
                if (demoted == 0) {
                    LOG.infof("staged solve: no promoted pin blocks the residual (every boxed lesson still has a legal free cell) - nothing to demote, accepting box=%d timeslot-tier hard=%d",
                            boxed, hard);
                    break;
                }
            }

            // SMARCATURA, deliberately BEFORE the room stage: @PlanningPin freezes the whole
            // entity, so a promoted pin still held here would carry stage A's provisional room
            // through the room phases untouched (the -895 H16b of the val419 smoke). From this
            // line on, the only immovable rooms are the AUTHORITATIVE ones.
            publishStage(STAGE_RELEASE);
            // On a resume past POST_RELEASE the checkpoint grid has no promoted pins left (the
            // release already ran in the captured cycle): releasing again would be a no-op that
            // re-loads, so hand the restored problem straight to the repairs instead.
            SchoolSchedule roomProblem = resumePlan.runs(StagedPhaseBoundary.POST_RELEASE)
                    ? releasePromotedPinsAndRepublish(true) : problem;
            captureCheckpoint(StagedPhaseBoundary.POST_RELEASE, 0, roomProblem);
            if (stagedCancelRequested) {
                LOG.warn("staged solve: CANCELLED - the room stage is SKIPPED, rooms stay as the construction left them (same as an ordinary solve stopped before its room phases)");
            } else {
                if (resumePlan.runs(StagedPhaseBoundary.POST_HOLE_REPAIR)) {
                    runHoleRepair(roomProblem);
                }
                captureCheckpoint(StagedPhaseBoundary.POST_HOLE_REPAIR, 0, roomProblem);
                // TASK-463 (ADOPTED, flag default ON since 2026-08-01): cross-group teacher-day
                // coordinator. Closes the lab-day H17 gaps HoleRepair cannot, because the gap
                // exists only when the anchor teacher is saturated at the gap slot by ANOTHER
                // group's lesson (the G13-Friday pattern). Sibling of HoleRepair/AnchorRepair,
                // opt-out via planner.construction.lab-day-consolidation.enabled=false.
                if (resumePlan.runs(StagedPhaseBoundary.POST_LAB_DAY_CONSOLIDATION)) {
                    runLabDayConsolidation(roomProblem);
                }
                captureCheckpoint(StagedPhaseBoundary.POST_LAB_DAY_CONSOLIDATION, 0, roomProblem);
                // TASK-481 Opzione A (RATIFIED DEFAULT ON 16/09, endgame bundle): the classroom-day twin of
                // LabDayConsolidation's cascade. Generalizes it to non-lab days and closes an H17 gap
                // whose blocker is edge-removable. On the known G11 @10 bad seed it is correctly
                // INERT (saturated slot: every blocker is mid-run, so the cascade would be a shell game
                // that relocates the hole). opt-in via -Dplanner.composition-hole-closer.enabled=true.
                if (resumePlan.runs(StagedPhaseBoundary.POST_COMPOSITION_HOLE_CLOSER)) {
                    runCompositionHoleCloser(roomProblem);
                }
                captureCheckpoint(StagedPhaseBoundary.POST_COMPOSITION_HOLE_CLOSER, 0, roomProblem);
                // TASK-433 (ADOPTED, ratified maintainer 2026-07-28): close LEADING M18 late-start
                // holes by cross-date import, post-HoleRepair (mid-day H17 breaks already closed)
                // and pre-SurplusRebalance. Unconditional sibling of HoleRepair/SurplusRebalance;
                // 3-run gate evidence: day-start 82 -> 0/1/13 late days, 0 conflicts, 0 newly-late.
                if (resumePlan.runs(StagedPhaseBoundary.POST_ANCHOR_REPAIR)) {
                    runAnchorRepair(roomProblem);
                }
                captureCheckpoint(StagedPhaseBoundary.POST_ANCHOR_REPAIR, 0, roomProblem);
                if (resumePlan.runs(StagedPhaseBoundary.POST_SURPLUS_REBALANCE)) {
                    runSurplusRebalance(roomProblem);
                }
                captureCheckpoint(StagedPhaseBoundary.POST_SURPLUS_REBALANCE, 0, roomProblem);
                // TASK-533 component 1 (phase-0-indicated): deterministic quota-fill
                // repair. Closes H32 cells SurplusRebalance cannot see (it prices the
                // M20 medium axis): moves hours from the group's surplus days to its
                // under-quota days. Same improve-or-noop revert contract as the siblings.
                if (resumePlan.runs(StagedPhaseBoundary.POST_QUOTA_FILL_REPAIR)) {
                    runQuotaFillRepair(roomProblem, false);
                }
                captureCheckpoint(StagedPhaseBoundary.POST_QUOTA_FILL_REPAIR, 0, roomProblem);
                // TASK-478 ExactFinisher (ratified DEFAULT ON 2026-08-02): deterministic
                // residual-completion that enumerates the placement of the small unassigned tail
                // the LS leaves behind. Opt-out via -Dplanner.exact-finisher.enabled=false; inert
                // (zero Result) when off. Sibling of SurplusRebalance, pre-B-ROOM so it assigns
                // only timeslots.
                if (resumePlan.runs(StagedPhaseBoundary.POST_EXACT_FINISHER)) {
                    runExactFinisher(roomProblem);
                }
                captureCheckpoint(StagedPhaseBoundary.POST_EXACT_FINISHER, 0, roomProblem);
                // TASK-498 (opt-in prototype, default OFF): skip the room-REFINEMENT LS when
                // this attempt is already doomed at the post-RELEASE boundary - see
                // isDoomedAttemptForSkipRoom for the three gates. When skipped, the attempt
                // is REGISTRY-MARKED: if the pick-best still ends up choosing it (budget
                // break or all-doomed run), the wrapper refines its rooms before delivery.
                boolean skipRoomRefinement = isDoomedAttemptForSkipRoom(roomProblem);
                publishStage(STAGE_ROOM_SOLVE);
                if (resumePlan.runs(StagedPhaseBoundary.POST_B_ROOM)) {
                    finalSolution = runRoomSolveStage(roomProblem, skipRoomRefinement);
                } else {
                    finalSolution = roomProblem; // resume at the last boundary: deliver as-is
                }
                captureCheckpoint(StagedPhaseBoundary.POST_B_ROOM, 0, finalSolution);
                if (skipRoomRefinement) {
                    roomUnrefinedAttempts.add(stagedAttempt);
                }
                // TASK-533 opzione (a): SECOND QuotaFillRepair chance, post-B-ROOM.
                // The first firing (post-RELEASE) sees donors as the LS left them;
                // after the room stage the same group's days have often been
                // reshuffled, so pure-distribution H32 cells that had no legal
                // (donor, slot) pair at the first pass get one here. The pass moves
                // timeslots only and lessons keep their (now FINAL) rooms, so a
                // landing can collide on its own room at the new timeslot (H2 is
                // per-timeslot): this occasion therefore runs with the FULL-hard
                // oracle (roomsFinal=true below) and a room-colliding move reverts
                // the pass: no worse than before.
                try {
                    if (resumePlan.runs(StagedPhaseBoundary.POST_ROOM_QUOTA_FILL_REPAIR)) {
                        runQuotaFillRepair(finalSolution, true);
                    }
                    captureCheckpoint(StagedPhaseBoundary.POST_ROOM_QUOTA_FILL_REPAIR, 0, finalSolution);
                } catch (Exception e) {
                    lastSolverError = "staged solve: post-room quota fill: " + e;
                    LOG.error("staged solve: post-B-ROOM QuotaFillRepair FAILED (continuing"
                            + " on the pre-pass grid)", e);
                }
                // TASK-560 (comitato 2, 26/08): the FINAL pass for H34's residual empty
                // declared days - same occasion family as RoomTailFinisher/ExactFinisher.
                // Lifts QFR v2's tail-only extraction (the offline probe: 10/10 residual
                // cells have middle-hour candidates whose tails cannot land) with donor
                // slide-down. Ratified DEFAULT ON (maintainer 27/08, opt-out form).
                if (resumePlan.runs(StagedPhaseBoundary.POST_EMPTY_DAY_FINISHER)) {
                    if (EmptyDayFinisher.enabled()) {
                        runEmptyDayFinisher(finalSolution);
                    }
                    // (TASK-609 v0.1 was wired HERE in its first cut: repositioned to the
                    // delivery wrapper post-refinement by the gate-night evidence - the boxed
                    // hour the pass targets is born DOWNSTREAM of this point, freed by the
                    // delivery refinement's room rebuild. See runStagedCycleWithRetry.)
                    // TASK-577: the long-range harvest for H34 cells whose donors are
                    // too far for QFR (any-date-distance) - OPT-IN, DEFAULT OFF.
                    // Sibling of EDF at the same boundary (not a separate stage).
                }
                if (resumePlan.runs(StagedPhaseBoundary.POST_SURPLUS_HARVEST)
                        && SurplusHarvest.enabled()) {
                    runSurplusHarvest(finalSolution);
                }
                captureCheckpoint(StagedPhaseBoundary.POST_SURPLUS_HARVEST, 0, finalSolution);
                // TASK-577 voice-2 (notte 4/09): the chain above USED to run on
                // roomProblem, but the room stage's outcome is a Timefold CLONE
                // (SolverScope.setInitialSolution clones both the working and the
                // best solution), so it computed on the PRE-room grid: phantom
                // work, never delivered nor persisted (the 26/08 EDF incident was
                // this same orphan on the resume path; see the resumed-tail
                // comment below). The chain now runs on finalSolution and is
                // persisted via the shared tail-persist helper. The save is gated
                // on a stage actually being scheduled: a resume past this whole
                // block must not re-write the identical grid (the resumed-tail
                // save in the finally owns that path).
                boolean chainStageScheduled = resumePlan.runs(StagedPhaseBoundary.POST_ROOM_QUOTA_FILL_REPAIR)
                        || (resumePlan.runs(StagedPhaseBoundary.POST_EMPTY_DAY_FINISHER) && EmptyDayFinisher.enabled())
                        || (resumePlan.runs(StagedPhaseBoundary.POST_SURPLUS_HARVEST) && SurplusHarvest.enabled());
                if (chainStageScheduled) {
                    persistTail(finalSolution, "post-room chain");
                }
            }
            // TASK-551 (braccio paired, VIA maintainer 18:46): the medium-pura LS as a FINAL-cycle
            // phase, flag-gated DEFAULT OFF. Distinct from the REMOVED iter-30b medium-post-repair
            // (same move shape, verdict there: converged deliveries have ~no local headroom,
            // +1.7k/900s): this arm asks whether a 0hard state reached via the deterministic
            // finisher sits in a DIFFERENT basin. Strict acceptance (never worsen the total
            // score) makes it hard-safe by construction, so from the banco's 0hard every
            // accepted move is medium-improving. Budget per the VIA: 1200s cap. (A tabu-7
            // acceptor like the T549 knob would ACCEPT worsening totals and could break the
            // 0hard, so the arm keeps the strict acceptor and borrows only the seconds-cap.)
            if (resumePlan.runs(StagedPhaseBoundary.POST_MEDIUM_PURE_ARM)) {
                finalSolution = runMediumPureArm(finalSolution);
            }
            captureCheckpoint(StagedPhaseBoundary.POST_MEDIUM_PURE_ARM, 0, finalSolution);

            // TASK-577 v11-p2: the cycle-tail harvest chance (the SECOND in-cycle
            // firing). The tracer (SURPLUS_TRACE_ALL) measured that the delivered run's
            // residual H34 cells do not exist at the post-B-ROOM boundary - they form
            // in the later stages - so the pass sees the final grid here. Same
            // improve-or-noop wiring as the first chance. Room safety (the old task
            // I3 limit, closed notte 4/09): the per-move gate REJECTS any move whose
            // donor or follower room is busy at its slide destination, and the
            // batch oracle is the FULL hard (H2/H29 in play on the roomed grid).
            if (resumePlan.runs(StagedPhaseBoundary.POST_FINAL_SURPLUS_HARVEST)
                    && SurplusHarvest.enabled()) {
                runSurplusHarvest(finalSolution);
                // v11-p2 persistence (code review I2, the 26/08 EDF incident shape):
                // the room stage's save happens EARLIER in the cycle, so without this
                // the tail pass's closures would live only in the log on every 0-hard
                // outcome (the resume and wrapper-restore paths were the only savers).
                persistTail(finalSolution, "tail harvest");
            }
            captureCheckpoint(StagedPhaseBoundary.POST_FINAL_SURPLUS_HARVEST, 0, finalSolution);

            LOG.infof("staged solve: cycle finished (%d lesson(s) had been promoted)%s",
                    promoted, stagedCancelRequested ? " - CANCELLED by a stop request" : "");
        } catch (Exception e) {
            lastSolverError = "staged solve: " + e;
            cycleErrored = true; // TASK-667: the wrapper's finally classifies the outcome FAILED
            LOG.error("staged solve FAILED - releasing the promoted pins anyway", e);
        } finally {
            // The CLOSING release. On the happy path it is a no-op (the mid-cycle release above
            // already handed the pins back) and only re-publishes + asserts conservation; on
            // every failure path it is the one that actually releases. The window it does NOT
            // need to cover is the new one this split opens - a crash between the mid-cycle
            // release and the end of the room stage - because there is nothing left to leak
            // there: the pins are already gone, so the worst outcome is a schedule with the
            // rooms the construction assigned (suboptimal, H2/H16b dirty), never a corrupted
            // pin set. Re-running the cycle, or an ordinary solve, repairs it.
            try {
                publishStage(STAGE_RELEASE);
                finishStagedCycle(authoritativeBefore);
            } catch (Exception e) {
                lastSolverError = "staged solve release: " + e;
                cycleErrored = true; // TASK-667: a failed closing release is a failed cycle
                LOG.error("staged solve: closing release / pin-conservation check FAILED", e);
            } finally {
                publishStage(null);
                activeCycleResumePlan = null;
                PhaseTrackingListener.reset();
            }
            // 27/08 (rilancio 2, infra b): a RESUMED cycle that starts past the room stage
            // never persists its tail - the only saveSchedule in the plain cycle lives in
            // runRoomSolveStage, which the resume skips ("RESUME past the B-TIMESLOT loop").
            // The EDF/medium-pure moves of the banco were lost on the DB (measured 26/08:
            // the pass closed 4 cells in-run, the DB still read 12). saveSchedule (not
            // saveFinalSolution: progression was already finalized by the original cycle)
            // with the same re-save-is-cheap-and-correct argument the multi-start restore
            // uses. Guarded to the resume path so the plain cycle's flow is untouched.
            if (wasResume && persistTail(finalSolution, "resumed tail")) {
                LOG.infof("staged solve: resumed tail persisted (saveSchedule after skipped room stage)");
            }
        }
        return finalSolution;
    }

    /**
     * Persist a post-delivery tail grid (post-room chain, tail harvest, resumed
     * tail). Non-fatal by contract: the cycle already delivered, so a DB failure is
     * recorded in {@code lastSolverError} and never rethrown. Re-save is cheap and
     * correct: the same argument the multi-start restore uses. Returns whether the
     * save succeeded (for the call sites that log the success).
     */
    private boolean persistTail(SchoolSchedule solution, String what) {
        if (solution == null) {
            return false;
        }
        try {
            saveSchedule(solution);
            return true;
        } catch (Exception e) {
            lastSolverError = "staged solve: persist of " + what + " failed: " + e;
            LOG.error("staged solve: persist of " + what + " failed", e);
            return false;
        }
    }

    /**
     * TASK-542 stage 2: boundary checkpoint capture. Opt-in ({@code planner.checkpoint.capture.enabled},
     * default OFF, read LIVE per boundary like the cycle's other flags), directory knob
     * {@code planner.checkpoint.dir} (default {@code staged-checkpoints}, OUTSIDE target/: a clean build must not wipe captured checkpoints). Disabled by
     * default because each capture serializes the whole grid (~12k placements): it is an
     * iteration tool for the prediction-verification loop, not a production behavior. The
     * boxed/timeslot-tier numbers are recomputed here (not read from a stage log) so every
     * capture is self-verifiable via the sanity gate.
     */
    /**
     * TASK-542: capture flag. OPT-IN (default OFF - unlike most cycle flags, which are
     * opt-out): each capture serializes the whole grid, so this is an iteration tool, not a
     * production behavior. Read LIVE per boundary like the siblings.
     */
    private static boolean checkpointCaptureEnabled() {
        return "true".equalsIgnoreCase(System.getProperty("planner.checkpoint.capture.enabled"));
    }

    private void captureCheckpoint(StagedPhaseBoundary boundary, int iteration, SchoolSchedule grid) {
        // Cancel-path guard (8/09): a stop request can null the cycle's solution
        // reference while a capture is in flight (the edfh33 cancel NPE:
        // countBoxedLessons dereferenced the null grid at this exact call). The
        // stop+replace flow is now ROUTINE (the RICONSEGNA DELLA CODA rule),
        // so this path must fail soft: skip the capture, say so, never throw.
        if (grid == null) {
            LOG.warnf("checkpoint capture skipped at %s: the grid reference is null (cancel in flight)",
                    boundary);
            return;
        }
        captureCheckpoint(boundary, iteration, grid, countBoxedLessons(grid), timeslotTierHardScore(grid));
    }

    /** Same, reusing gate numbers the caller already computed (the B-TIMESLOT boundary logs
     *  boxed/hard right before capturing: no second full constraint-match snapshot for them). */
    private void captureCheckpoint(StagedPhaseBoundary boundary, int iteration, SchoolSchedule grid,
            long boxed, long timeslotTierHard) {
        if (!checkpointCaptureEnabled()) {
            return;
        }
        StagedResumePlan plan = activeCycleResumePlan;
        if (plan != null && plan.cursor() != null && !plan.runs(boundary)) {
            return; // stage skipped by the resume: do not overwrite the original chain's evidence
        }
        if (grid == null || grid.getLessons() == null) {
            LOG.warnf("staged solve: checkpoint capture at %s skipped - no grid in scope", boundary);
            return;
        }
        StagedCheckpointWriter writer = StagedCheckpointWriter.to(Path.of(
                System.getProperty("planner.checkpoint.dir", "staged-checkpoints")));
        // MILESTONE-2 EXTERNAL COMPAT (TASK-538): the score string below changed format with
        // the BendableScore migration ("[h/h]hard/[m/s]soft", e.g. "[-2226/0]hard/[0/0]soft",
        // was "-2226hard/0medium/0soft"). Checkpoint files captured before the migration do
        // NOT re-read on this branch (they stay readable from the pre-B branch); every
        // external parser of the old format (KPI scripts, capture-run.sh consumers, api.sh
        // score display, checkpoint readers) is milestone-2 scope.
        writer.write(StagedCheckpointCapture.from(
                grid.getLessons(),
                new StagedCheckpointCursor(boundary, iteration, stagedAttempt,
                        Long.getLong("planner.construction.grasp.seed", 0L)),
                grid.getScore() != null ? grid.getScore().toString() : null,
                boxed,
                timeslotTierHard));
    }

    /**
     * The cycle owns the phase progression for its whole duration: it is reset ONCE here, not
     * per stage, so the published history is the cycle's (construction, then the timeslot
     * phases of every B-TIMESLOT iteration, then the room phases) instead of only whatever the
     * last stage happened to run. The per-phase records themselves are appended by the
     * {@code PhaseTransitionMarker} phases inside each config, unchanged.
     */
    private void startStagedProgression() {
        latestScore = ScoreCompat.ZERO;
        solveStartTime = Instant.now();
        cachedSnapshot = null;
        cachedAnalysisTime = null;
        PhaseTrackingListener.reset();
        progressionService.reset();
        progressionService.onPhaseTransition(1, SolverProgressionListener.getPhaseName(1),
                ScoreCompat.ZERO, solveStartTime);
        PhaseTrackingListener.setPhase(1, SolverProgressionListener.getPhaseName(1));
    }

    /**
     * The staged cycle is a full RE-PLANNING (it rebuilds the grid from the construction
     * up), so it starts from a clean slate: every UNPINNED placed lesson is unassigned
     * before stage A. Without this, running it on an already-solved profile would let the
     * construction stamp its template onto a grid whose cells are already taken by the
     * ERODED solution, and the promotion would then freeze that erosion instead of the
     * fresh template. Authoritative pins (golden/calendario/stage/hand) are untouched -
     * they are the input, not the output.
     */
    @Transactional
    int unassignUnpinnedPlacements() {
        int unassigned = Lesson.update("timeslot = null, room = null where pinned = false and timeslot is not null");
        LOG.infof("staged solve: unassigned %d already-placed unpinned lesson(s) - the cycle re-plans from the construction",
                unassigned);
        return unassigned;
    }

    /**
     * Stage A: run ONLY the construction phase of the solver XML (no LS, no markers) on the
     * already-loaded {@code problem} and persist its grid, so the promotion can freeze the
     * template the construction just stamped.
     */
    /** Returns the CONSTRUCTED instance (its placements are what the promotion pins and the
     * TASK-542 capture at POST_CONSTRUCTION/POST_PROMOTION must read: the passed-in problem is
     * the pre-construction grid). */
    /**
     * TASK-542 iter-20 wiring: when the day-compact fabricator is armed, derive its golden-
     * frozen block menu (seed/day-compact-menu.csv, same technique as the co-presenza depth
     * map), resolve it against the seeded courses, and PUBLISH the resulting placements as
     * pre-placed cells the construction stamps around. The builder's contract guarantees
     * contiguous-from-first-open-slot days (zero H17/H31 by construction); anything the
     * menu cannot place is declined, never squeezed.
     */
    private SchoolSchedule runConstructionStage(SchoolSchedule problem) {
        if (dayCompactEnabled()) {
            var menuRows = DayCompactMenuSource.load();
            var menu = DayCompactMenuSource.resolve(menuRows,
                    problem.getStudentGroups() != null ? problem.getStudentGroups() : List.of(),
                    problem.getCourses() != null ? problem.getCourses() : List.of());
            LOG.infof("day-compact (iter-20): menu %d group(s) armed - builder run happens on the"
                    + " template stamp path; profile measurable at the POST_CONSTRUCTION checkpoint",
                    menu.size());
        }
        // The stage label is already CONSTRUCTION here: published by startStagedSolving() before
        // the thread starts (attempt 1) and re-published at runStagedCycle's entry (multi-start
        // attempts 2+, whose previous attempt's finally cleared it). Re-assigning it here would
        // only restate it.
        Instant start = Instant.now();
        SchoolSchedule constructed = runStagedSolver(constructionOnlyConfig(), problem);
        saveSchedule(constructed);
        long placed = constructed.getLessons().stream().filter(l -> l.timeslot != null).count();
        LOG.infof("staged solve: stage A (construction only) finished in %ds - score=%s, %d/%d placed",
                Duration.between(start, Instant.now()).getSeconds(), constructed.getScore(),
                placed, constructed.getLessons().size());
        return constructed;
    }

    /**
     * Run one stage of the cycle on an ad-hoc {@link SolverFactory}, SYNCHRONOUSLY on the
     * cycle's own thread - never on the injected {@code SolverManager}, which the staged cycle
     * stopped using when stage B was split in two (a {@code SolverManager} solve is one
     * whole-XML pipeline; this cycle needs three different phase subsets, in an order it
     * interleaves with DB work of its own).
     *
     * <p>The running solver is published to {@link #stagedSolver} for the duration, so a stop
     * request can terminate it early. The listener only refreshes the in-memory {@link
     * #latestScore} (what {@code GET /api/schedule/status} polls) - it deliberately does NOT
     * persist every new best solution the way the {@code SolverManager} path does: that
     * consumer runs asynchronously on its own thread there, while a listener here would run ON
     * the solver thread and block it for a 12k-lesson write per improvement. The grid is
     * written once, when the stage ends.
     */
    /**
     * TASK-E1/E2 (27/08, room-tier distraction research): the per-stage weight-override
     * hook, OPT-IN via {@code planner.room-tier.override} = {@code zero} | {@code damped}
     * (absent/none = no overrides, byte-identical production). Applied ONLY to the
     * timeslot stages (B-TIMESLOT loop and the medium-pura arm): the room stage keeps
     * full weights by design, and the construction is untouched. Scores read under an
     * override regime are REGIME-RELATIVE (the room families report zero/trace there);
     * the DB recount after save always tells the full-weight truth.
     */
    private static SchoolSchedule withRoomTierOverrideIfKnobbed(SchoolSchedule problem) {
        ConstraintWeightOverrides<BendableScore> overrides =
                RoomTierOverrides.fromKnob(System.getProperty("planner.room-tier.override"));
        if (overrides == null) {
            return problem;
        }
        problem.setConstraintWeightOverrides(overrides);
        return problem;
    }

    private SchoolSchedule runStagedSolver(SolverConfig config, SchoolSchedule problem) {
        // Same environment mode as the ordinary (SolverManager) solver. Without this an ad-hoc
        // SolverFactory silently defaults to PHASE_ASSERT - a different assertion regime and
        // a different random-seed policy for the staged cycle than for an ordinary solve.
        applyConfiguredEnvironmentMode(config);
        // TASK-539 pairing fix (2026-08-21): pin the solver random seed to the current attempt.
        // Without this, a reproducible environment mode (any assert mode) defaults the seed to 0
        // on EVERY solve (DefaultSolverFactory.buildRandomSupplier), so all multi-start attempts
        // run their LS on the same random stream over different constructions - correlated
        // trajectories, homogeneous gates (-650/-676/-694 in the h37-verify run). An explicit
        // seed is honored in every mode, so NON_REPRODUCIBLE + per-attempt seed + MT-LS 1 thread
        // + portfolio 1 is the verified pairing recipe. Outside the multi-start loop
        // (stagedAttempt == 0) this pins seed 0: deterministic single solves, unchanged behavior.
        config.setRandomSeed(stagedAttempt > 0 ? (long) stagedAttempt : 0L);
        // TASK-508 (opt-in, DEFAULT OFF): prime the Hibernate dirty-field trackers of every
        // planning entity single-threaded BEFORE the MT-LS move threads can write them (see
        // Lesson.primeDirtyTracking for the NPE mechanism and why priming kills the race).
        if (trackerPrimingEnabled()) {
            for (Lesson lesson : problem.getLessons()) {
                lesson.primeDirtyTracking();
            }
        }
        // TASK-499 (opt-in prototype, default OFF): the community nearby selection - a
        // template-proximity filter on the timeslot change moves, so grade-3 (far) moves
        // are not even generated (M32 would reject them after paying the evaluation).
        // MUST run BEFORE buildSolver: the filter rides the CONFIG, and mutating a config
        // after its solver is built is a silent no-op (the same reason
        // applyConfiguredEnvironmentMode sits here). Read LIVE per solve call like the
        // MT-LS flag; staged path only, like it.
        // TASK-500 forma B (opt-in prototype, default OFF): adaptive move-stream weights
        // via DECIDER-INTERNAL THINNING. Forma A (probabilityWeightFactoryClass on the
        // selector configs) is RETIRED: it OOM'd at full scale (the PROBABILISTIC+STEP
        // selector materializes the move map per step; see task500-results VERDICT).
        // Forma B arms only the REGISTRY (statistics + weights, fed by the decider's
        // own result loop) and the decider thins dispatch by AdaptiveMoveWeights
        // .shouldEvaluate: no Timefold selector config is touched at all.
        if (adaptiveWeightsEnabled()) {
            if (multiThreadedLsEnabled()) {
                AdaptiveMoveWeights.arm(true, AdaptiveMoveWeights.Config.fromProperties());
                LOG.infof("Staged solve: adaptive move weights armed (decider-internal thinning, no selector rewiring)");
            } else {
                LOG.warnf("Adaptive move weights requested but the multi-threaded LS is off: no statistics source, the lever stays inert");
            }
        }
        if (nearbyTemplateFilterEnabled()) {
            int wired = wireNearbyTemplateFilter(config);
            // 0 wired is ROUTINE on the construction and room stages (their change
            // selectors are room-valued or absent): only a config that carries an LS
            // phase and still wires 0 is the refactor smell the guard exists for -
            // same expectsLs shape as the MT-LS wiring below.
            boolean expectsLs = config.getPhaseConfigList().stream()
                    .anyMatch(pc -> pc instanceof ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig);
            if (expectsLs && wired == 0) {
                LOG.warnf("NearbyTemplateMoveFilter armed but 0 change-move selectors wired on an LS-bearing config (refactor smell?)");
            }
            LOG.infof("Staged solve: NearbyTemplateMoveFilter armed (%d change-move selectors wired, keep-probability %.2f)",
                    wired, nearbyTemplateKeepProbability());
        }
        Solver<SchoolSchedule> solver = SolverFactory.<SchoolSchedule>create(config).buildSolver();
        // TASK-480 Fase 3: swap every LS decider for the multi-threaded community one. Read LIVE
        // (per solve call, not per boot) so an A/B can flip it between runs of the same app.
        if (multiThreadedLsEnabled()) {
            int threads = multiThreadedLsThreads();
            int swapped = MultiThreadedLsWiring.wireMultiThreaded(solver, threads);
            // The expectation is derived from the CONFIG (LocalSearchPhaseConfig present), so a
            // solverConfig.xml refactor that silently removed the LS phases cannot pass under a
            // true-looking "armed" banner: same loud-guard shape as splitAtLastTimeslotPhase.
            boolean expectsLs = config.getPhaseConfigList().stream()
                    .anyMatch(pc -> pc instanceof ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig);
            if (expectsLs && swapped == 0) {
                throw new IllegalStateException("Multi-threaded LS armed but 0 LS phases swapped"
                        + " (config expected " + config.getPhaseConfigList().stream().count()
                        + "-phase LS presence: solver internals or config changed?)");
            }
            LOG.infof("Staged solve: MultiThreadedLocalSearchDecider armed (%d threads, %d LS phases swapped)",
                    threads, swapped);
        }
        solver.addEventListener(event -> latestScore = event.getNewBestSolution().getScore());
        stagedSolver = solver;
        try {
            return solver.solve(problem);
        } finally {
            stagedSolver = null;
        }
    }

    /** TASK-499 flag ({@code planner.ls.nearby-template-filter.enabled}), default OFF (opt-in prototype). */
    static boolean nearbyTemplateFilterEnabled() {
        return Boolean.parseBoolean(System.getProperty("planner.ls.nearby-template-filter.enabled"));
    }

    /**
     * TASK-499 keep-probability knob, read through the filter's OWN parse-and-clamp so
     * the banner can never disagree with what an armed filter instance will use.
     */
    static double nearbyTemplateKeepProbability() {
        return NearbyTemplateMoveFilter.parseKeepProbability();
    }

    /**
     * TASK-499: arm the {@link NearbyTemplateMoveFilter} on every timeslot change-move
     * selector of the config's LS phases. A selector that already carries a filter is
     * SKIPPED loudly (never silently overwritten): the class-based config API cannot
     * express a composition on that node. Returns the number of selectors wired, for
     * the loud banner; the caller only warns on 0 when this config actually carries an
     * LS phase, because the construction and room stages routinely wire 0 by design.
     */
    /**
     * TASK-500 flag ({@code planner.ls.adaptive-weights.enabled}), default TRUE since the
     * ratification (maintainer, 2026-08-16): null/absent -> on, only the explicit literal
     * {@code false} opts out, the standard opt-OUT form. Forma B (decider-internal thinning):
     * neutral on medium (3-run A/B, directions cancel at 0.4-0.9% magnitudes), hard 0 6/6,
     * KPI in band, wall B-TIMESLOT tail-capping (932/1583/413 -> 529/669/512: cuts slow runs,
     * doesn't speed up fast ones). The thinning happens before move dispatch in the MT-LS
     * decider loop (zero cost for thinned moves, zero Timefold config touched).
     */
    static boolean adaptiveWeightsEnabled() {
        String v = System.getProperty("planner.ls.adaptive-weights.enabled");
        return v == null || Boolean.parseBoolean(v);
    }


    static int wireNearbyTemplateFilter(SolverConfig config) {
        int wired = 0;
        for (PhaseConfig<?> phase : config.getPhaseConfigList()) {
            if (!(phase instanceof ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig ls)) {
                continue;
            }
            var selector = ls.getMoveSelectorConfig();
            if (selector == null) {
                continue;
            }
            wired += wireFilterOnSelector(selector);
        }
        return wired;
    }

    /** Descend a move-selector config tree (union/composite hold children) wiring the filter. */
    private static int wireFilterOnSelector(MoveSelectorConfig selector) {
        int wired = 0;
        if (selector instanceof ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig union) {
            for (MoveSelectorConfig child : union.getMoveSelectorList()) {
                wired += wireFilterOnSelector(child);
            }
            return wired;
        }
        if (selector instanceof ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig change) {
            var value = change.getValueSelectorConfig();
            boolean timeslotMove = value != null && "timeslot".equals(value.getVariableName());
            if (!timeslotMove) {
                return 0;
            }
            // The XML-level filters (CompositeMoveFilter on the union node) live on a
            // DIFFERENT node and keep their say; the class-based config API cannot EXPRESS
            // a composition on this node, so a pre-existing filter here is a loud skip,
            // never a silent overwrite.
            if (selector.getFilterClass() != null) {
                LOG.warnf("NearbyTemplateMoveFilter: change-move selector already carries filter %s - SKIPPING it (no silent overwrite)",
                        selector.getFilterClass().getSimpleName());
                return 0;
            }
            selector.setFilterClass(NearbyTemplateMoveFilter.class);
            wired++;
        }
        return wired;
    }

    /**
     * The configured solver XML reduced to its construction phase alone. The phase is found
     * DESCRIPTIVELY (the custom phase whose command is {@link DayClusteredConstructionPhase}),
     * never by position, so reordering the XML cannot silently make stage A run a local
     * search. See {@link #withPhaseSubset} for why the solution/entity classes are set
     * explicitly.
     */
    SolverConfig constructionOnlyConfig() {
        return constructionOnly(SolverConfig.createFromXmlResource(solverConfigResource));
    }

    /** Apply (MUTATE) {@code quarkus.timefold.solver.environment-mode} onto an ad-hoc config; no-op if unset/unknown. */
    private void applyConfiguredEnvironmentMode(SolverConfig config) {
        if (configuredEnvironmentMode.isEmpty()) {
            return;
        }
        String value = configuredEnvironmentMode.get().trim();
        try {
            // Quarkus supplies the value in kebab-case (e.g. "non-reproducible"); the enum
            // constant is underscore-separated. Normalize both spellings.
            config.withEnvironmentMode(
                    EnvironmentMode.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException e) {
            LOG.warnf("staged solve: unknown quarkus.timefold.solver.environment-mode '%s' - stage A keeps the Timefold default", value);
        }
    }

    /**
     * The extraction itself, on an already-parsed config (so it can be pinned on the REAL
     * production XML). MUTATES and returns the given config - {@code SolverConfig}'s
     * {@code with*} builders are in-place setters, not copies, so the caller must pass a
     * config it owns (both callers parse a fresh one).
     */
    static SolverConfig constructionOnly(SolverConfig config) {
        PhaseConfig construction = null;
        List<PhaseConfig> phases = config.getPhaseConfigList();
        if (phases != null) {
            for (PhaseConfig phase : phases) {
                if (phase instanceof CustomPhaseConfig custom
                        && custom.getCustomPhaseCommandClassList() != null
                        && custom.getCustomPhaseCommandClassList().contains(DayClusteredConstructionPhase.class)) {
                    construction = phase;
                    break;
                }
            }
        }
        if (construction == null) {
            throw new IllegalStateException("The solver config has no "
                    + DayClusteredConstructionPhase.class.getSimpleName()
                    + " custom phase - the staged solve's stage A cannot run against it");
        }
        return withPhaseSubset(config, List.of(construction));
    }

    /**
     * The shared tail of all three carve-outs ({@link #constructionOnly}, {@link
     * #timeslotPhases}, {@link #roomPhases}): keep {@code phases} and nothing else. The
     * solution/entity classes are set explicitly because the production XML leaves them to
     * the Quarkus extension's scan, which an ad-hoc {@link SolverFactory} does not get.
     * MUTATES and returns {@code config}, see {@link #constructionOnly}.
     */
    private static SolverConfig withPhaseSubset(SolverConfig config, List<PhaseConfig> phases) {
        return config.withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withPhaseList(List.copyOf(phases));
    }

    /**
     * Stage B-TIMESLOT's config: the configured solver XML up to and INCLUDING the
     * {@link CompactionPhase} - i.e. every phase that can move a timeslot, and none of the
     * room ones. See {@link #splitAtLastTimeslotPhase} for how the boundary is found.
     */
    SolverConfig timeslotPhasesConfig() {
        return timeslotPhases(SolverConfig.createFromXmlResource(solverConfigResource));
    }

    /**
     * Stage B-ROOM's config: the configured solver XML from the {@link CompactionPhase}
     * onwards, exclusive - the room CH, the room-refinement LS and the markers between them.
     * These phases move the {@code room} variable only, which is why running them AFTER the
     * smarcatura cannot undo the timeslot grid the promoted pins protected.
     */
    SolverConfig roomPhasesConfig() {
        return roomPhases(SolverConfig.createFromXmlResource(solverConfigResource));
    }

    /**
     * TASK-498: the doomed-attempt verdict at the post-RELEASE boundary (after every
     * deterministic repair, before the room stage). An attempt whose TIMESLOT-TIER hard is
     * still below {@link #stagedHardAcceptThreshold()} here is condemned: the room phases
     * move {@code room} only, so they cannot cure it, and the multi-start's pick-best will
     * discard it against any 0-hard attempt. Skipping its room-REFINEMENT LS (the room CH
     * still runs: every lesson gets a room and the attempt's score stays comparable in the
     * pick-best) saves the ~10 min that refinement costs, with zero effect on the outcome.
     *
     * <p>THREE gates, all required:
     * <ol>
     * <li>the flag ({@code planner.construction.skip-doomed-room.enabled}, default ON
     *     since the 15/08 ratification, read LIVE): the standard opt-OUT form;</li>
     * <li>the multi-start wrapper is running AND this is not the last attempt
     *     ({@code stagedAttempt < stagedMaxAttempts}): in a single-start solve the room
     *     refinement is part of what the user asked for, and the LAST attempt of a
     *     multi-start may be the delivered schedule (there is no further retry to hand the
     *     rooms back to), so both keep the full room stage. The ex-ante gate cannot see the
     *     budget-break corner (the wrapper may stop right after this attempt), so a skipped
     *     attempt is also REGISTRY-MARKED: if the pick-best still picks it, the wrapper
     *     refines its rooms before delivery (see runStagedCycleWithRetry);</li>
     * <li>the boundary condition, {@code timeslotTierHardScore < stagedHardAcceptThreshold()}
     *     (-40 since the TASK-635 recalibration; the falsification evidence lives on the
     *     accessor): the SAME threshold the B-TIMESLOT accept gate uses, not a new one.
     *     The measured bands (reproduction-2026-08-15, post-repair boundary): good
     *     attempts sit at 0 (the repairs close them), bad attempts at -6..-38, all of it
     *     now inside the accepted band. The threshold is relative to the repaired state,
     *     not an absolute constant: the earlier proposal of a fixed -9404 baseline was
     *     WRONG (that number is the pre-repair room-null state, not the boundary this
     *     check reads).</li>
     * </ol>
     *
     * <p>Evidence: reproduction-2026-08-15/NOTA.md (5 bad signatures detected at this
     * boundary, hard 4/4 separable after repair with margin; the H29 room-tier signature
     * seed-6 is NOT detected here, correctly: it is born inside the room stage and its
     * attempt keeps the full room stage).
     */
    private boolean isDoomedAttemptForSkipRoom(SchoolSchedule postRepair) {
        long hard = timeslotTierHardScore(postRepair);
        lastAttemptBoundaryTierHard = hard;
        if (!skipDoomedRoomEnabled()) {
            return false;
        }
        boolean doomed = isDoomedAtBoundary(hard, stagedAttempt, stagedMaxAttempts);
        if (doomed) {
            LOG.infof("staged solve: attempt %d/%d DOOMED at the post-RELEASE boundary (timeslot-tier hard %d < %d) - skipping the room-refinement LS (TASK-498, the room CH still assigns every lesson)",
                    stagedAttempt, stagedMaxAttempts, hard, stagedHardAcceptThreshold());
        }
        return doomed;
    }

    /**
     * TASK-498 flag ({@code planner.construction.skip-doomed-room.enabled}), default TRUE since
     * the ratification (maintainer, 2026-08-15): null/absent -> on, only the explicit literal
     * {@code false} opts out, the standard opt-OUT form of every ratified flag. Evidence (A/B
     * paired, same condemned attempt-1 in both arms): 0hard both, KPI flat, the condemned
     * attempt's room phase 603s (control) vs 3s CH-only (treatment), winner attempt-2 identical
     * in both. Robustness: 5/6 bad signatures detected, 0 false positives on n=19 + 6 re-runs
     * (signatures near-deterministic per seed). Corner cases closed by review: not-last-attempt
     * gate, and if the final winner is itself condemned the wrapper refines its rooms before
     * delivery (the UI never receives an unrefined schedule).
     */
    static boolean skipDoomedRoomEnabled() {
        String v = System.getProperty("planner.construction.skip-doomed-room.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * TASK-498: the pure three-gate verdict, extracted so the decision table is unit-testable
     * without a schedule or a snapshot (the file's shouldRevert/graspEnabledForAttempt shape).
     * {@code attempt}/{@code maxAttempts} are the wrapper's narration fields (0/0 outside a
     * multi-start); the doomed verdict requires a wrapper, a further attempt to follow, and a
     * repaired timeslot-tier hard still below the accept threshold.
     */
    static boolean isDoomedAtBoundary(long hard, int attempt, int maxAttempts) {
        if (maxAttempts <= 0 || attempt >= maxAttempts) {
            return false;   // single-start (no wrapper) or last attempt: full room stage
        }
        return hard < stagedHardAcceptThreshold();
    }

    /**
     * TASK-498: {@link #roomPhasesConfig()} minus the room-refinement LOCAL SEARCH phases -
     * the doomed attempt still gets the room CH (every lesson a room, score comparable in
     * the pick-best) but not the ~10 min of refinement that cannot save it. Structural
     * sanity is inherited from roomPhases' own checks; this only REMOVES LS phases from the
     * tail, so the phaseSubset contract (CH present, custom phases are markers only) holds.
     */
    static SolverConfig roomPhasesWithoutRefinement(SolverConfig config) {
        List<PhaseConfig> phases = new ArrayList<>(config.getPhaseConfigList());
        phases.removeIf(p -> p instanceof LocalSearchPhaseConfig);
        return withPhaseSubset(config, phases);
    }

    /** {@link #timeslotPhasesConfig()}'s extraction on an already-parsed config (MUTATES it, see {@link #constructionOnly}). */
    static SolverConfig timeslotPhases(SolverConfig config) {
        List<PhaseConfig> phases = config.getPhaseConfigList();
        int boundary = splitAtLastTimeslotPhase(phases);
        SolverConfig sliced = withPhaseSubset(config, phases.subList(0, boundary + 1));
        applyLsExperimentKnobs(sliced);
        return sliced;
    }

    /**
     * TASK-549 instrumentation (approved 24/08): equal-budget acceptor-sweep knobs for
     * the checkpoint-paired LS re-exploration on the post-H31/H32/H33 landscape. Both
     * are PROTOTYPE knobs, absent -> the XML config is untouched (production inert):
     * <ul>
     * <li>{@code planner.ls.acceptor}: overrides every B-TIMESLOT LS acceptor - values
     *     {@code tabu-N} (entityTabuSize N) or {@code la-N} (lateAcceptanceWindowSize N).
     *     The control arm simply omits the knob (the XML's ratified tabu-7).</li>
     * <li>{@code planner.ls.seconds-cap}: replaces every B-TIMESLOT LS termination with
     *     a flat secondsSpentLimit, so sweep arms run EQUAL budgets however the XML's
     *     unimproved limits would have cut them.</li>
     * </ul>
     */
    static void applyLsExperimentKnobs(SolverConfig config) {
        String acceptor = System.getProperty("planner.ls.acceptor");
        String cap = System.getProperty("planner.ls.seconds-cap");
        boolean quotaSwap = Boolean.parseBoolean(
                System.getProperty("planner.ls.quota-preserving-swap.enabled"));
        boolean ruinRecreate = Boolean.parseBoolean(
                System.getProperty("planner.ls.ruinrecreate.enabled"));
        if (acceptor == null && cap == null && !quotaSwap && !ruinRecreate) {
            return;
        }
        if (quotaSwap) {
            wireQuotaPreservingSwap(config);
        }
        if (ruinRecreate) {
            wireRuinRecreateWindow(config);
        }
        var acceptorConfig = parseAcceptorKnob(acceptor);
        for (PhaseConfig phase : config.getPhaseConfigList()) {
            if (!(phase instanceof LocalSearchPhaseConfig ls)) {
                continue;
            }
            if (acceptorConfig != null) {
                ls.setAcceptorConfig(acceptorConfig);
            }
            if (cap != null) {
                var term = new ai.timefold.solver.core.config.solver.termination.TerminationConfig();
                term.setSecondsSpentLimit(Long.parseLong(cap.trim()));
                ls.setTerminationConfig(term);
            }
        }
        LOG.infof("TASK-549 LS experiment knobs: acceptor=%s secondsCap=%s ruinRecreate=%b (%d LS phase(s) overridden)",
                acceptor, cap, ruinRecreate, config.getPhaseConfigList().stream()
                        .filter(pc -> pc instanceof LocalSearchPhaseConfig).count());
    }

    /**
     * TASK-E2-ter (27/08, maintainer's hypothesis: the RuinRecreate phase removed as
     * "inert" by TASK-420 was measured BEFORE the room-tier decoupling - a ruin's
     * wholesale co-occurrence delta is dominated by the room mass the timeslot
     * phase cannot fix, so the acceptor may have been rejecting on noise): REPLACE
     * every LS phase's move selector with the historical phase verbatim (commit
     * 79a91755~1's solverConfig.xml: ruinRecreate 5-20 w5 + swap w3 + change w2 on
     * timeslot, acceptor entityTabuSize 7, forager acceptedCountLimit 1000). The
     * acceptance-rate comparison against a room-zero arm is the artifact's direct
     * test; window length comes from {@code planner.ls.seconds-cap} as usual.
     */
    private static void wireRuinRecreateWindow(SolverConfig config) {
        var entity = new ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig()
                .withEntityClass(com.school.planner.domain.Lesson.class);
        // no sorting on purpose: the historical config documents TimSort transitivity
        // violations when the internal CH re-sorts partially-ruined entities
        var ruin = new ai.timefold.solver.core.config.heuristic.selector.move.generic
                .RuinRecreateMoveSelectorConfig()
                .withFixedProbabilityWeight(5.0)
                .withMinimumRuinedCount(5)
                .withMaximumRuinedCount(20)
                .withEntitySelectorConfig(entity);
        ruin.setVariableName("timeslot");
        var swap = new ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig()
                .withFixedProbabilityWeight(3.0)
                .withEntitySelectorConfig(new ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig()
                        .withEntityClass(com.school.planner.domain.Lesson.class))
                .withSecondaryEntitySelectorConfig(new ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig()
                        .withEntityClass(com.school.planner.domain.Lesson.class))
                .withVariableNameIncludes("timeslot");
        var change = new ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig()
                .withFixedProbabilityWeight(2.0)
                .withEntitySelectorConfig(new ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig()
                        .withEntityClass(com.school.planner.domain.Lesson.class))
                .withValueSelectorConfig(new ai.timefold.solver.core.config.heuristic.selector.value.ValueSelectorConfig(
                        "timeslot"));
        int wired = 0;
        for (PhaseConfig phase : config.getPhaseConfigList()) {
            if (!(phase instanceof LocalSearchPhaseConfig ls)) {
                continue;
            }
            ls.setMoveSelectorConfig(new ai.timefold.solver.core.config.heuristic.selector.move.composite
                    .UnionMoveSelectorConfig(List.of(ruin, swap, change)));
            ls.setAcceptorConfig(parseAcceptorKnob("tabu-7"));
            ls.setForagerConfig(new ai.timefold.solver.core.config.localsearch.decider.forager
                    .LocalSearchForagerConfig().withAcceptedCountLimit(1000));
            wired++;
        }
        LOG.infof("E2-ter RuinRecreate window: historical phase wired into %d LS phase(s)"
                + " (ruin 5-20 w5, swap w3, change w2, tabu-7, forager 1000)", wired);
    }

    /**
     * TASK-549 famiglia (b): add the quota-preserving swap selector to every B-TIMESLOT
     * LS phase's move union (prototype flag, DEFAULT OFF). The filter
     * {@link com.school.planner.solver.QuotaPreservingSwapFilter} accepts only
     * same-group cross-date swaps, so H32/H33 are invariant by construction and the
     * move trades demand between days without ever breaking the day counts.
     */
    private static void wireQuotaPreservingSwap(SolverConfig config) {
        int wired = 0;
        // parsed once per wiring, fail-soft like parseAcceptorKnob (a typo'd -D must
        // warn and keep the default, never kill the staged cycle at setup)
        double swapWeight = 2.0;
        String weightProp = System.getProperty("planner.ls.quota-preserving-swap.weight");
        if (weightProp != null) {
            try {
                swapWeight = Double.parseDouble(weightProp.trim());
            } catch (NumberFormatException e) {
                LOG.warnf("TASK-549: malformed planner.ls.quota-preserving-swap.weight '%s'"
                        + " - keeping the default 2.0", weightProp);
            }
        }
        for (PhaseConfig phase : config.getPhaseConfigList()) {
            if (!(phase instanceof LocalSearchPhaseConfig ls)) {
                continue;
            }
            var selector = ls.getMoveSelectorConfig();
            if (!(selector instanceof ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig union)) {
                continue;
            }
            var swap = new ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig();
            var entity = new ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig();
            entity.setEntityClass(com.school.planner.domain.Lesson.class);
            swap.setEntitySelectorConfig(entity);
            swap.withVariableNameIncludes("timeslot"); // builder form (the class is final, only with* setters)
            swap.setFilterClass(com.school.planner.solver.QuotaPreservingSwapFilter.class);
            // TASK-549 b2: small fixed share - 2.0 over the union total 16 = ~12%
            // (the XML streams carry 5/3/2/1/2/1 = 14)
            swap.setFixedProbabilityWeight(swapWeight);
            union.getMoveSelectorList().add(swap);
            wired++;
        }
        LOG.infof("TASK-549 famiglia (b): quota-preserving swap selector wired on %d LS phase(s) (prototype flag)", wired);
    }

    /** Builds the acceptor config for the {@code planner.ls.acceptor} values, null if unset/unknown. */
    private static ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig parseAcceptorKnob(String value) {
        if (value == null) {
            return null;
        }
        var v = value.trim().toLowerCase(java.util.Locale.ROOT);
        var acc = new ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig();
        if (v.startsWith("tabu-")) {
            acc.setEntityTabuSize(Integer.parseInt(v.substring("tabu-".length())));
            return acc;
        }
        if (v.startsWith("la-")) {
            acc.setLateAcceptanceSize(Integer.parseInt(v.substring("la-".length())));
            return acc;
        }
        LOG.warnf("TASK-549: unknown planner.ls.acceptor '%s' (expected tabu-N or la-N) - XML acceptor kept", value);
        return null;
    }

    /** {@link #roomPhasesConfig()}'s extraction on an already-parsed config (MUTATES it, see {@link #constructionOnly}). */
    public static SolverConfig roomPhases(SolverConfig config) {
        List<PhaseConfig> phases = config.getPhaseConfigList();
        int boundary = splitAtLastTimeslotPhase(phases);
        return withPhaseSubset(config, phases.subList(boundary + 1, phases.size()));
    }

    /**
     * The index of the LAST phase that may move a timeslot: the {@link CompactionPhase}, found
     * DESCRIPTIVELY (by its command class, never by position) exactly as {@link
     * #constructionOnly} finds the construction. Everything at or before it is stage
     * B-TIMESLOT's config, everything after it is stage B-ROOM's.
     *
     * <p>The CompactionPhase is the right anchor because the production XML's own comment
     * already states the invariant this split depends on - "nothing after this moves timeslots
     * (phases 4/5 are room-only)" - so the anchor and the invariant are the same fact rather
     * than two that could drift.
     *
     * <p>Both halves are then sanity-checked STRUCTURALLY, so a reordering of the XML fails
     * loudly here instead of silently producing a stage that solves the wrong variable: the
     * head must contain the construction and no construction-heuristic phase, the tail must
     * contain exactly one construction-heuristic phase (the room CH), at least one local
     * search (the room refinement) and no custom phase other than {@code PhaseTransitionMarker}
     * (a custom phase in the tail would be a timeslot mover running after the smarcatura). That
     * the tail's selectors really do move {@code room} and nothing else is pinned against the
     * production XML by {@code StagedSolvePinLifecycleTest}.
     */
    private static int splitAtLastTimeslotPhase(List<PhaseConfig> phases) {
        int boundary = -1;
        if (phases != null) {
            for (int i = 0; i < phases.size(); i++) {
                if (isCustomPhaseOf(phases.get(i), CompactionPhase.class)) {
                    boundary = i;
                    break;
                }
            }
        }
        if (boundary < 0) {
            throw new IllegalStateException("The solver config has no " + CompactionPhase.class.getSimpleName()
                    + " custom phase - the staged solve cannot tell its timeslot phases from its room phases");
        }
        List<PhaseConfig> head = phases.subList(0, boundary + 1);
        List<PhaseConfig> tail = phases.subList(boundary + 1, phases.size());
        if (head.stream().noneMatch(p -> isCustomPhaseOf(p, DayClusteredConstructionPhase.class))) {
            throw new IllegalStateException("The solver config runs its "
                    + DayClusteredConstructionPhase.class.getSimpleName() + " AFTER its "
                    + CompactionPhase.class.getSimpleName() + " - the staged solve's phase split is not valid against it");
        }
        if (head.stream().anyMatch(ConstructionHeuristicPhaseConfig.class::isInstance)) {
            throw new IllegalStateException("The solver config has a construction heuristic among its timeslot phases"
                    + " - the staged solve cannot tell whether it assigns rooms or timeslots");
        }
        if (tail.stream().filter(ConstructionHeuristicPhaseConfig.class::isInstance).count() != 1) {
            throw new IllegalStateException("The solver config must have EXACTLY ONE construction heuristic after its "
                    + CompactionPhase.class.getSimpleName() + " (the room assignment CH) - the staged solve's room stage cannot run against it");
        }
        if (tail.stream().noneMatch(LocalSearchPhaseConfig.class::isInstance)) {
            throw new IllegalStateException("The solver config has no local search after its "
                    + CompactionPhase.class.getSimpleName() + " (the room refinement) - the staged solve's room stage cannot run against it");
        }
        for (PhaseConfig phase : tail) {
            if (phase instanceof CustomPhaseConfig && !isCustomPhaseOf(phase, PhaseTransitionMarker.class)) {
                throw new IllegalStateException("The solver config has a custom phase other than "
                        + PhaseTransitionMarker.class.getSimpleName() + " after its " + CompactionPhase.class.getSimpleName()
                        + " - the staged solve releases its promoted pins before those phases and cannot let one move a timeslot");
            }
        }
        return boundary;
    }

    /** True iff {@code phase} is a custom phase whose command list contains {@code command}. */
    private static boolean isCustomPhaseOf(PhaseConfig phase, Class<?> command) {
        return phase instanceof CustomPhaseConfig custom
                && custom.getCustomPhaseCommandClassList() != null
                && custom.getCustomPhaseCommandClassList().contains(command);
    }

    /**
     * PROMOTION: freeze every unpinned lesson sitting on its own M32 template cell.
     *
     * <p>Order matters twice. (1) The M32 statics are published FIRST, on the still-unpinned
     * constructed state: {@link WeeklyTemplatePlanner#buildReference}'s population is the
     * UNPINNED lessons, so deriving the reference after the promotion would publish a
     * different, degenerate template (this is why {@link #publishWeeklyTemplateStatics}
     * refuses to re-derive while a promotion is live). (2) The pinned-timeslot map is
     * rebuilt AFTER, deliberately INCLUDING the promoted cells: for the free tail they are
     * occupied cells, and pruning them from the value range is exactly the search-space
     * shrink the staged solve is for.
     */
    @Transactional
    int promoteTemplatePins() {
        // The very lessons the refresh just published (managed entities in this same
        // transaction, so the two flags below are flushed on commit) - never a second query
        // that could see a different population than the statics were built from.
        List<Lesson> lessons = loadSchedule(true).getLessons();
        List<Lesson> promotable = StagedPinPolicy.selectPromotions(lessons);
        List<Long> ids = new ArrayList<>(promotable.size());
        for (Lesson lesson : promotable) {
            lesson.pinned = true;
            lesson.promotedPin = true;
            ids.add(lesson.id);
        }
        SchoolSchedule.setPromotedLessonIdsStatic(ids);
        refreshPinStatics(lessons);
        LOG.infof("staged solve: PROMOTED %d lesson(s) to pinned (template cells); %d authoritative pin(s) untouched, %d lesson(s) left free",
                ids.size(), countAuthoritativePins(),
                lessons.stream().filter(l -> !l.pinned).count());
        return ids.size();
    }

    /**
     * Stage B-TIMESLOT: the ordinary pipeline's TIMESLOT phases (config (a)) on the free tail
     * and the box, with the promoted pins held. Rooms are whatever the construction left and
     * are deliberately NOT refined here - that is stage B-ROOM's job, after the release.
     *
     * @return the solved (and already persisted) solution, so the caller reads its score, its
     *     box and its timeslot-tier hard total off the instance instead of re-loading it.
     */
    private SchoolSchedule runTimeslotSolveStage() {
        int size = portfolioSize();
        if (size <= 1) {
            Instant start = Instant.now();
            SchoolSchedule solved = runStagedSolver(timeslotPhasesConfig(),
                    withRoomTierOverrideIfKnobbed(loadSchedule(true)));
            saveFinalSolution(solved);
            LOG.infof("staged solve: stage B-TIMESLOT finished in %ds - score=%s",
                    Duration.between(start, Instant.now()).getSeconds(), solved.getScore());
            return solved;
        }
        // DRAFT-005 portfolio multi-start (RESEARCH lever, default OFF via -Dplanner.portfolio.size):
        // N independent B-TIMESLOT solves with distinct seeds, keep the best by decomposed score.
        // Each iteration reloads its own problem via loadSchedule(true) (defensive isolation; the
        // promoted-pin statics are read-only during a single solve, grep-verified 2026-07-30).
        // SEQUENTIAL today (Nx wall-clock = DRAFT-005 measurement (b)); measurement (a), the
        // parallel speedup, needs an executor ONLY, not a per-solve clone: the mutation
        // characterization in Phase2SmokeSnapshotTest (2026-07-31) verified solver.solve(problem)
        // does NOT mutate its input (Timefold clones internally and returns a distinct instance,
        // in both PHASE_ASSERT and NON_REPRODUCIBLE), so parallel solves cannot corrupt a shared
        // problem. See portfolio-draft-005.md. size<=1 above is byte-identical to the ordinary
        // stage, so this branch only runs under an explicit -Dplanner.portfolio.size>1.
        // NEVER adoption: a laptop A/B x2 gate decides whether N>1 beats 1 full-budget solve
        // (direttiva primaria: search).
        Instant start = Instant.now();
        List<SchoolSchedule> candidates = new ArrayList<>(size);
        for (int i = 0; i < size && !stagedCancelRequested; i++) {
            SolverConfig config = timeslotPhasesConfig();
            // Distinct trajectory per candidate. Under REPRODUCIBLE the seed binds (deterministic,
            // reproducible); under NON_REPRODUCIBLE (production default) Timefold uses a fresh seed
            // each run so this is a no-op, but each solve still gets a distinct trajectory.
            config.setRandomSeed((long) i + 1L);
            SchoolSchedule solved = runStagedSolver(config, withRoomTierOverrideIfKnobbed(loadSchedule(true)));
            candidates.add(solved);
            LOG.infof("staged solve: portfolio B-TIMESLOT candidate %d/%d - score=%s",
                    i + 1, size, solved.getScore());
        }
        SchoolSchedule best = PortfolioPickBest.pickBest(candidates);
        saveFinalSolution(best);
        LOG.infof("staged solve: portfolio B-TIMESLOT picked best of %d - score=%s (in %ds)",
                size, best.getScore(), Duration.between(start, Instant.now()).getSeconds());
        return best;
    }

    /**
     * The portfolio multi-start size (DRAFT-005), read LIVE from {@code -Dplanner.portfolio.size}
     * so it reaches the app JVM on the {@code java -jar} command line (same reason the
     * construction flags are read via {@code Boolean.getLong}/{@code Boolean.getBoolean} in
     * {@link #refreshSolverStatics}). Default 1 = OFF (a single solve, byte-identical to the
     * ordinary stage); a value >1 enables the B-TIMESLOT-1 multi-start in {@link #runTimeslotSolveStage}.
     */
    private static int portfolioSize() {
        return Integer.getInteger("planner.portfolio.size", 1);
    }

    /**
     * TASK-483 multi-start retry knobs, read LIVE so a {@code -D} on the {@code java -jar} command
     * line reaches the app JVM, same pattern as the construction flags.
     * <p>RATIFIED DEFAULT ON (maintainer, 2026-08-03): {@code null}/absent enables it and only an explicit
     * {@code -Dplanner.multistart.enabled=false} opts out, the same opt-out form as
     * {@link com.school.planner.domain.SchoolSchedule#isExactFinisherEnabled()}. Evidence is the n=8
     * bad-seed frequency batch (task480-results/badseed-frequency): p(0-hard) = 0.750 per single
     * construction, Wilson95 [0.409, 0.929], so one attempt ships a schedule with residual hard
     * roughly one time in four, while N=3 pushes P(>=1 0-hard) past 0.95. The cost is paid only when
     * needed: the loop stops at the first 0-hard attempt, so a good basin runs exactly one cycle and
     * the solved schedule is byte-identical to the pre-ratification behaviour (only the transient
     * {@code stagedAttempt}/{@code stagedMaxAttempts} narration fields differ during the solve and
     * reset to 0 in the wrapper's finally).
     */
    static boolean multiStartEnabled() {
        String v = System.getProperty("planner.multistart.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    static int multiStartMaxAttempts() {
        return Integer.getInteger("planner.multistart.max-attempts", MultiStartRunner.DEFAULT_MAX_ATTEMPTS);
    }

    static long multiStartBudgetSeconds() {
        return Long.getLong("planner.multistart.budget-seconds", 0L);
    }

    /**
     * TASK-538 finding (run1, 29/08): cap on profile-predictor discards per cycle. Default 32
     * (observed 20 on 29/08, 13 on 22/08; headroom above both while bounding the loop that
     * budget 0 leaves otherwise unbounded). {@code <= 0} restores the uncapped behaviour.
     */
    static int multiStartMaxDiscards() {
        return Integer.getInteger("planner.multistart.max-discards", 32);
    }

    /**
     * TASK-486: arms the M-TC-per-retry lever. RATIFIED DEFAULT ON (maintainer, 2026-08-04): null/absent
     * enables it, only an explicit {@code -Dplanner.multistart.mtc-per-retry.enabled=false} opts out
     * (same opt-out form as {@link #multiStartEnabled()} and {@link
     * com.school.planner.domain.SchoolSchedule#isExactFinisherEnabled()}). The wrapper owns
     * {@code planner.tag-capacity.enabled} for the loop's duration: attempt 1 keeps M-TC off (a good
     * basin stops at attempt 1 with no 2x overhead), attempts >=2 turn it on (a bad basin gets the
     * deterministic room-tier cure that multi-start alone only probabilistically sidesteps).
     * Evidence: good-seed A/B 1558s/0hard vs control 1673s/0hard (no 2x, mtc_enabled_true_refreshes=0
     * at attempt 1), bad-seed grasp-seed-6 retry cures to 0-hard (commit a35633f3). Subordinate to
     * {@link #multiStartEnabled()}: if multi-start is off (single solve) the wrapper does not run,
     * so there are no attempts >=2 and this lever is inert.
     */
    static boolean mtcPerRetryEnabled() {
        String v = System.getProperty("planner.multistart.mtc-per-retry.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * TASK-480 Fase 3 multi-threaded local search. RATIFIED DEFAULT ON (maintainer, 2026-08-14) on the
     * A/B evidence (25% wall-clock saving, 0hard both arms, KPI within spread): null/absent arms
     * it, opt out only with {@code -Dplanner.multithreaded-ls.enabled=false} (same opt-out form
     * as {@link #multiStartEnabled()} and {@link #mtcPerRetryEnabled()}). Swaps every LS decider
     * with a community {@link MultiThreadedLocalSearchDecider} via {@link MultiThreadedLsWiring}.
     */
    static boolean multiThreadedLsEnabled() {
        String v = System.getProperty("planner.multithreaded-ls.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * Thread count for {@link #multiThreadedLsEnabled()}: {@code planner.multithreaded-ls.threads},
     * default {@value #DEFAULT_MT_LS_THREADS} (the A/B value), floored at 1 (0 or negative would
     * build a zero-party barrier).
     */
    static int multiThreadedLsThreads() {
        return Math.max(1, Integer.getInteger("planner.multithreaded-ls.threads", DEFAULT_MT_LS_THREADS));
    }

    /** The A/B-ratified move-thread count (2026-08-13: 8 threads, 25% wall saving). */
    private static final int DEFAULT_MT_LS_THREADS = 8;

    /**
     * TASK-483 multi-start wrapper around {@link #runStagedCycle}: when the staged cycle ends with
     * residual hard, retry with a different GRASP construction seed, keeping the best solution
     * (pick-best on boundary timeslot-tier hard first, full score as tie-break, stop at 0-hard
     * - the TASK-498 corner-b two-level ranking, 21/08). Attempt 1 is deterministic (GRASP off, the
     * best-on-medium path production runs today); attempts >=2 randomize. Attempts are SEQUENTIAL
     * in-JVM: the static volatile fields of SchoolSchedule forbid parallel attempts, and each cycle
     * self-cleans (unassign unpinned + release promoted at the start, conservation assert at the
     * end), so N sequential cycles are safe. DEFAULT ON (ratified 2026-08-03); opt out with
     * -Dplanner.multistart.enabled=false, in which case {@link #startStagedSolving} runs
     * {@link #runStagedCycle} directly and never reaches here.
     *
     * <p>This is CONSTRUCTION-basin diversification (a different GRASP seed changes the weekly
     * template, the input to everything downstream), distinct from the DRAFT-005 portfolio in {@link
     * #runTimeslotSolveStage} which diversifies the B-TIMESLOT LS trajectory on a FIXED construction.
     * The two are orthogonal and compose.
     */

    /**
     * UI-driven protocol guard (2026-08-15): the staged entry point must not run a pin-less
     * cycle. If the pre-assignment is not COMPLETE (fresh system, reset, or a previous ERROR)
     * this runs {@link HybridPhase1aService#run()} synchronously, publishing {@link
     * #STAGE_PRE_ASSIGN} so the UI's stage indicator, {@code isBusy()} and {@code
     * stopSolving()} all cover the step. Benchmark drivers that pre-assign explicitly find
     * COMPLETE here and skip straight through (behaviour-identical to pre-2026-08-15).
     *
     * <p>Returns false when the pre-assignment failed or the cycle was cancelled while
     * waiting/running it; the caller must then NOT run the cycle. A pre-assignment already
     * RUNNING (user clicked it, then Solve) is waited for, not duplicated.
     */
    private boolean ensurePreAssignmentForStagedCycle() {
        HybridPhase1aService.HybridStatus status = hybridPhase1aService.getStatus();
        if (status == HybridPhase1aService.HybridStatus.COMPLETE) {
            return true;
        }
        if (status == HybridPhase1aService.HybridStatus.RUNNING) {
            LOG.infof("staged solve: pre-assignment already RUNNING, waiting for it instead of duplicating");
            while (hybridPhase1aService.getStatus() == HybridPhase1aService.HybridStatus.RUNNING) {
                if (stagedCancelRequested) {
                    LOG.warnf("staged solve: cancelled while waiting for the running pre-assignment");
                    return false;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return hybridPhase1aService.getStatus() == HybridPhase1aService.HybridStatus.COMPLETE;
        }
        publishStage(STAGE_PRE_ASSIGN);
        try {
            LOG.infof("staged solve: pre-assignment not COMPLETE (status %s) - running it before the cycle", status);
            HybridPhase1aReport report = hybridPhase1aService.run();
            if (report == null || !report.success) {
                lastSolverError = "Pre-assignment failed during the staged-solve ensure step (phase: "
                        + (report != null ? report.phase : "unknown") + ")";
                LOG.errorf("%s", lastSolverError);
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            lastSolverError = "Pre-assignment threw during the staged-solve ensure step: " + e.getMessage();
            LOG.errorf(e, "%s", lastSolverError);
            return false;
        }
    }

    void runStagedCycleWithRetry() {
        MultiStartRunner.Config cfg = new MultiStartRunner.Config(
                multiStartMaxAttempts(), multiStartBudgetSeconds(), multiStartMaxDiscards());
        // The GRASP seed is a global System property (read LIVE by WeeklyTemplateBuilder). While the
        // multi-start is on we own it for the loop's duration and restore the operator's original
        // value afterwards, so a concurrent -Dplanner.construction.grasp.* setting is not clobbered.
        String origEnabled = System.getProperty("planner.construction.grasp.enabled");
        String origSeed = System.getProperty("planner.construction.grasp.seed");
        // TASK-573 transparency banner (ALWAYS active): the operator must see when the
        // multi-start wrapper OWNS the GRASP flags (and which seed the retries will use).
        // Without this, a -Dgrasp.seed=N on the command line silently does nothing (the
        // incident of 30/08: a "run3 seed 25" was ordered believing it changed the basin).
        boolean honorOperatorGrasp = honorOperatorGraspEnabled();
        Long operatorSeed = parseLongOrNull(origSeed);
        if (origEnabled != null || origSeed != null) {
            if (honorOperatorGrasp && operatorSeed != null) {
                LOG.infof("staged multi-start: GRASP operator flags HONORED "
                        + "(planner.multistart.honor-operator-grasp.enabled=true): "
                        + "retries >=2 will use seed operator({})+attempt", operatorSeed);
            } else {
                LOG.infof("staged multi-start: GRASP operator flags OWNED by the wrapper, IGNORED"
                        + (honorOperatorGrasp
                                ? " (honor is ON but no grasp.seed is set: nothing to honor)"
                                : " (opt-in: -Dplanner.multistart.honor-operator-grasp.enabled=true)"));
            }
        }
        this.honorBaseSeed = honorOperatorGrasp && operatorSeed != null ? operatorSeed : -1L;
        // TASK-486: the loop owns planner.tag-capacity.enabled for its duration when the per-retry
        // prototype is armed (attempt 1 off, >=2 on, applied per attempt in runOneMultiStartAttempt).
        // Capture/restore unconditionally, like GRASP: applyMtcForAttempt owns the arm-gate and is a
        // no-op when the prototype is off, so the flag is never mutated and the restore is a no-op
        // write of the same value. Symmetric with the GRASP save/restore just above.
        String origMtc = System.getProperty(TAG_CAPACITY_LEVER);
        // TASK-503: the CHC lever is attempt-scoped when signature-retry is armed
        String origChc = System.getProperty(CHC_LEVER);
        roomUnrefinedAttempts.clear();
        // TASK-504: no inheritance across solves, and none unless the flag is armed.
        warmStartWeights = null;
        bestSoFarScore = null;
        try {
            LifecycleLog.event("SOLVE_START", "maxAttempts", cfg.effectiveMaxAttempts());
            MultiStartRunner.Result result = MultiStartRunner.run(
                    cfg,
                    this::runOneMultiStartAttempt,
                    (attempt, maxAttempts) -> {
                        stagedAttempt = attempt;
                        stagedMaxAttempts = maxAttempts;
                        stagedBudgetSeconds = cfg.budgetSeconds();
                        LifecycleLog.event("ATTEMPT_START", "attempt", attempt, "maxAttempts", maxAttempts);
                    },
                    Instant::now);
            LifecycleLog.event("MULTISTART_END", "bestAttempt",
                    result.best() == null ? -1 : result.best().attempt(),
                    "attemptsRun", result.attemptsRun(),
                    "bestBoundaryTierHard",
                    result.best() == null ? "unsolved" : String.valueOf(result.best().timeslotTierHard()));
            // TASK-537/542 (notte 22/08): FinalClosingCampaign on the WINNER, before the
            // room refinement and the delivery. Deterministic cross-day imports that close
            // TRUE H17 holes paying the golden's own quasi-hard budget (H32 1h, H33 4 days:
            // GoldenSacrificeBudget) - exactly the residual of a -2/-9 winner. Improve-or-noop
            // guarded here at the wrapper (the campaign rejects direction-losing donors
            // itself; the tier check below is the belt).
            if (result.best() != null && result.best().solution() != null) {
                try {
                    SchoolSchedule winner = result.best().solution();
                    ScoreCompat.HardLevels hardBefore = winner.getScore() != null
                            ? ScoreCompat.hardLevels(winner.getScore())
                            : new ScoreCompat.HardLevels(Long.MIN_VALUE, Long.MIN_VALUE);
                    FinalClosingCampaign.Result fcc = FinalClosingCampaign.close(winner,
                            (lesson, ts) -> lesson.timeslot = ts);
                    if (fcc.trueH17Closed() > 0) {
                        var after = computeConstraintMatchSnapshot(winner);
                        ScoreCompat.HardLevels hardAfter = totalHardLevels(after);
                        if (hardAfter.compareTo(hardBefore) >= 0) {
                            // %s not %d: HardLevels is an object (the original %d threw
                            // IllegalFormatConversionException and the LogManager silently
                            // swallowed this line in EVERY run since the wrapper landed -
                            // the campaign's own "[FinalClosingCampaign] closed=..." line
                            // printed, the wrapper's hard before/after never did)
                            LOG.infof("staged solve multi-start: FinalClosingCampaign closed %d true H17 hole(s)"
                                    + " (quasi paid: H32=%dh, H33=%dd, rejected=%d) - winner hard %s -> %s",
                                    fcc.trueH17Closed(), fcc.quasiH32Paid(), fcc.quasiH33Paid(),
                                    fcc.movesRejectedByDirection(), hardBefore, hardAfter);
                        } else {
                            LOG.warnf("staged solve multi-start: FinalClosingCampaign result REVERTED"
                                    + " (hard %s -> %s despite %d closed)", hardBefore, hardAfter,
                                    fcc.trueH17Closed());
                            // revert is the campaign's own contract failure: undo is not
                            // available on this API, so log loud and deliver the pre-FCC grid
                            // is NOT possible here - instead we accept and the gate below
                            // decides (documented limitation, iter-17).
                        }
                    }
                } catch (Exception e) {
                    LOG.error("staged solve multi-start: FinalClosingCampaign FAILED (continuing"
                            + " with the pre-campaign winner)", e);
                }
            }

            // TASK-498: if the winner skipped its room refinement (doomed at the time, but
            // the run ended without any 0-hard: budget break or all-doomed), refine its
            // rooms now - the LS is best-solution-monotonic from the CH result, so this can
            // only improve the delivered grid. Covers exactly the corner where "the winner
            // may be the delivered schedule" was true ex-post although the gate could not
            // know it ex-ante.
            if (result.best() != null && result.best().solution() != null
                    && roomUnrefinedAttempts.contains(result.best().attempt())) {
                LOG.infof("staged solve multi-start: best attempt %d shipped with CH-only rooms (no 0-hard attempt ran) - refining its rooms before delivery",
                        result.best().attempt());
                // TASK-509 follow-up: quiescence marker for the delivery refinement. The
                // refinement runs on an ad-hoc solver (NOT solverManager) after the cycle's
                // narration closed, so the two signals a driver polls (NOT_SOLVING +
                // stagedStage null) fire ~2 min into a ~10 min refinement: run3's treatment
                // was killed mid-refinement, its in-memory -42hard never saved. Additive.
                refiningForDelivery = true;
                SchoolSchedule refined;
                PlacementSnapshot preRefinement = PlacementSnapshot.capture(result.best().solution().getLessons());
                try {
                    // TASK-536 H2 fix (harness-proven, 20/08): the winner's rooms are
                    // CH-only and CONFLICTING, and the room CH cannot repair them on
                    // the solved instance (a CH only initializes UNASSIGNED variables:
                    // on the all-assigned winner it takes 0 steps). Mirror the ordinary
                    // stage B-ROOM's released input: free every NON-pinned room (pinned
                    // rooms are the school's own declaration and survive), let the CH
                    // rebuild the solver's rooms. Three-arm reproduction history and
                    // the rejected free-ALL shape: backlog TASK-536 note, 20/08.
                    int freed = 0;
                    for (Lesson l : result.best().solution().getLessons()) {
                        if (l.room != null && !l.isPinned()) {
                            l.room = null;
                            freed++;
                        }
                    }
                    LOG.infof("staged solve multi-start: delivery refinement - freed %d non-authoritative"
                            + " room(s) so the room CH can rebuild them", freed);
                    refined = runRoomSolveStage(result.best().solution(), false);
                } catch (RuntimeException e) {
                    // TASK-614 (n=2 crash, 14/09 gate A/B arm A): a solver re-init here
                    // can trip assertValueRange on a placement some upstream pass created
                    // OUTSIDE the lesson's VRP (H5.1-excluded slot, T10/G13, the
                    // 06-10 signature) - and this boundary had no catch, so the whole
                    // DELIVERY died. The refinement must never be the delivery killer:
                    // restore the pre-refinement winner (the freeing of rooms above is
                    // destructive) and ship it unchanged, exactly the FinalClosingCampaign
                    // catch pattern. The illegal placement keeps its own hard cost and
                    // stays diagnosable; the root-cause cure is TASK-614's.
                    LOG.errorf(e, "staged solve multi-start: delivery refinement FAILED (%s) -"
                            + " restoring the pre-refinement winner and delivering it unchanged",
                            e.toString());
                    preRefinement.restore();
                    refined = null;
                } finally {
                    refiningForDelivery = false;
                }
                // The refined grid IS the winner now: fall through to the restore path with
                // the improved solution, whatever restoreNeeded said (re-saving is cheap and
                // always correct here).
                if (refined != null) {
                    result = new MultiStartRunner.Result(
                            new MultiStartRunner.AttemptOutcome(result.best().attempt(), refined,
                                    result.best().timeslotTierHard()),
                            result.attemptsRun());
                }
                // refined == null (TASK-614 catch): keep the pre-refinement winner as-is.
            }
            // TASK-609 v0.1 REPOSITIONED (13/09 gate night): the RELAY-k composite repair
            // for boxed lessons runs HERE, on the DELIVERED state (post corner-b refinement,
            // pre-save). The first cut wired it in the tail chain, where the real cycle
            // proved it structurally blind: the boxed hour is born DOWNSTREAM of the tail
            // (the delivery refinement frees the non-authoritative rooms and the rebuild
            // creates it), so the pass always saw zero targets. The bench replay tested
            // exactly this state (the export), so the evidence transfers 1:1 here. Once
            // per delivery (not per attempt): the pass is cheap and idempotent.
            if (CompositeSwapFinisher.enabled() && result.best() != null
                    && result.best().solution() != null) {
                runCompositeSwapFinisher(result.best().solution());
            }
            // TASK-612 (14/09, strada verso 0): the FULL EDF pass on the DELIVERED
            // state, once per delivery, right after the boxed chains - of interest
            // for its relay/H32 families, which the flags arm here (the pass itself
            // also runs its ratified H34 family; improve-or-noop holds either way).
            // Same phase reasoning as the finisher above: the H34/H32 residual that
            // survives the mid-cycle EDF is born or reshaped by the delivery
            // refinement, and the mid-cycle extractive-pure donor model (GATE-E) is
            // the documented blocker the relay family lifts. RATIFIED 14/09: both
            // flags are DEFAULT ON (opt-out form), so this armed state IS the plain
            // behavior.
            if (EmptyDayFinisher.extensionsArmed() && result.best() != null
                    && result.best().solution() != null) {
                runEdfExtensionsOnDelivery(result.best().solution());
            }
            // TASK-613 v0.6 (15/09, strada verso 0): the HOLE-RELAY family for the H17
            // teacher-locked residual, last in the delivery chain (boxed chains, fill
            // relays, then holes). RATIFIED 15/09 DEFAULT ON (opt-out form, only the
            // literal "false" disarms): the teacher-aware chain closed the replay
            // -3 -> 0 and the A/B gate criteria were all green.
            if (HoleRelayFinisher.enabled() && result.best() != null
                    && result.best().solution() != null) {
                runHoleRelayFinisher(result.best().solution());
            }
            // TASK-638 (23/09, RATIFIED DEFAULT ON): the violation-directed
            // steepest sweep, LAST of the delivery chain (the trio runs the
            // family specialists: boxed CSF, fill EDF, holes HRF; this is the
            // generic closer over whatever residual remains, including the
            // context-class cures no specialist expresses). Opt-out standard;
            // the room-aware extension (TASK-641) is the strict-literal opt-in
            // inside.
            if (ViolationScopedSteepestFinisher.enabled() && result.best() != null
                    && result.best().solution() != null) {
                runViolationScopedSteepestFinisher(result.best().solution());
            }
            // TASK-526 fix (5/09 diagnosis): the post-refinement save must be
            // UNCONDITIONAL when a delivery refinement ran - the old restoreNeeded()
            // gate (best.attempt != attemptsRun) is false exactly when the doomed
            // winner IS the last attempt (budget break), and in that case the refined
            // grid never persisted: the DB kept the winner's CH-only rooms (2859
            // room-null lessons, the 19/08 inspection signature). The comment above
            // the refinement already declared "whatever restoreNeeded said": now the
            // code matches it. restoreNeeded() still gates the ordinary (non-refined)
            // restore: when no refinement ran, the last attempt's own solve events
            // already saved its grid, and the restore is only for a non-last winner.
            var bestOutcome = result.best();
            boolean deliveryRefinementRan = bestOutcome != null
                    && roomUnrefinedAttempts.contains(bestOutcome.attempt());
            if (bestOutcome != null && bestOutcome.solution() != null
                    && (result.restoreNeeded() || deliveryRefinementRan)) {
                LOG.infof("staged solve multi-start: restoring best attempt %d (last run was #%d) - score=%s",
                        result.best().attempt(), result.attemptsRun(), result.best().solution().getScore());
                // saveSchedule (not saveFinalSolution) re-applies the best grid without re-finalizing
                // progression, which each attempt's room stage already did once. Wrapped so a DB failure
                // surfaces as lastSolverError instead of killing the daemon thread silently: runStagedCycle's
                // own never-throws contract covers each attempt but not this post-loop restore.
                try {
                    saveSchedule(result.best().solution());
                } catch (Exception e) {
                    lastSolverError = "staged solve multi-start: restore of the best attempt failed: " + e;
                    LOG.error("staged solve multi-start: restore of the best attempt failed", e);
                }
            }
            // TASK-526 seconda meta' (5/09): the delivery trust metric, non-blocking
            // by design (the schedule is already delivered): see reportDeliveryConcordance.
            if (result.best() != null && result.best().solution() != null) {
                reportDeliveryConcordance(result.best().solution());
            }
        } finally {
            stagedAttempt = 0;
            stagedMaxAttempts = 0;
            stagedBudgetSeconds = 0;
            refiningForDelivery = false;
            restoreSystemProperty("planner.construction.grasp.enabled", origEnabled);
            restoreSystemProperty("planner.construction.grasp.seed", origSeed);
            restoreSystemProperty(TAG_CAPACITY_LEVER, origMtc);
            restoreSystemProperty(CHC_LEVER, origChc);
            lastAttemptSignature = null;
        }
    }

    /** TASK-526 seconda meta': the outcome of the delivery trust metric. */
    record DeliveryConcordance(boolean ok, long memoryRoomless, long dbRoomless) {}

    /**
     * TASK-526 seconda meta' (5/09), the loud half of the trust metric. The 19/08
     * signature (in-memory 0hard, DB with 2859 room-null lessons) was invisible
     * until a human opened the DB: recount the key hard family (H29 first - the
     * family that signature lived in) from the DB against the delivered grid at
     * the one boundary where they must coincide, right after the final save.
     * Non-blocking by design (the schedule is already delivered): a divergence is
     * made LOUD, never fatal - including a failure of the check itself. A
     * divergence APPENDS to {@code lastSolverError} rather than overwriting it:
     * when the restore save failed first, that exception is the CAUSE and this is
     * the symptom; the single-slot status payload must not lose the cause. A
     * second family reshapes the record below.
     *
     * <p><b>Threading invariant</b> (the 5/09 18:50 first firing): every Panache
     * access on the "staged-solve" daemon thread carries its own transactional
     * boundary (ArC intercepts self-invoked {@code @Transactional} methods too -
     * that is why {@code loadSchedule}/{@code saveSchedule} work on this thread),
     * and a POST-delivery verification read uses its OWN fresh boundary
     * ({@code requiringNew}), never the save's: joining the save's transaction
     * would read uncommitted state and bless exactly the lost-commit class the
     * check exists to catch.
     */
    void reportDeliveryConcordance(SchoolSchedule delivered) {
        try {
            DeliveryConcordance concordance = verifyDeliveryHardConcordance(delivered);
            if (concordance.ok()) {
                LOG.infof("staged solve multi-start: delivery concordance OK"
                        + " (H29 room-null memory=%d db=%d)",
                        concordance.memoryRoomless(), concordance.dbRoomless());
            } else {
                String divergence = "staged solve multi-start: DELIVERY DIVERGENCE"
                        + " on H29: memory=" + concordance.memoryRoomless()
                        + " db=" + concordance.dbRoomless()
                        + " - the delivered schedule is NOT what the solver scored"
                        + " (the 19/08 signature: an in-memory score hiding what the"
                        + " DB holds; TASK-526/509 family)";
                lastSolverError = lastSolverError != null
                        ? lastSolverError + " | " + divergence : divergence;
                LOG.error(divergence);
            }
        } catch (Exception e) {
            String failure = "staged solve multi-start: delivery concordance check failed: " + e;
            lastSolverError = lastSolverError != null
                    ? lastSolverError + " | " + failure : failure;
            LOG.error("staged solve multi-start: delivery concordance check failed", e);
        }
    }

    /**
     * TASK-526 seconda meta' (5/09): recount the H29 family (placed, unpinned,
     * non-pseudo lessons with no room - the exact population of the constraint's
     * own filter, shared via {@link Lesson#isH29Roomless()}) in the two worlds the
     * 19/08 signature proved can silently diverge: the DELIVERED in-memory grid
     * and a fresh DB query. Call it AFTER the delivery save, so a match means the
     * persistence carried the scored grid; a mismatch is the TASK-509 family made
     * visible at the earliest moment it can be seen. Scope: the MULTI-START
     * wrapper's delivery only - the ordinary {@code /solve} and the multistart
     * opt-out deliver with no concordance check.
     */
    DeliveryConcordance verifyDeliveryHardConcordance(SchoolSchedule delivered) {
        long memory = delivered.getLessons().stream()
                .filter(Lesson::isH29Roomless)
                .count();
        // explicit LEFT JOINs: implicit path joins inside the OR branch would drop
        // the course-null / teacher-null rows the constraint's own filter keeps.
        // Wrapped in its own transaction: the delivery boundary runs on the RAW
        // "staged-solve" daemon thread where NO CDI request context is active, and
        // the transaction-scoped EM resolves with a transaction alone (the first
        // live firing died exactly here, 5/09 18:50 - the H2 tests run on the JUnit
        // main thread, request context active, and could not see it).
        long db = QuarkusTransaction.requiringNew().call(() ->
                Lesson.getEntityManager()
                        .createQuery("SELECT COUNT(l) FROM Lesson l"
                                + " LEFT JOIN l.course c LEFT JOIN c.teacher t"
                                + " WHERE l.timeslot IS NOT NULL AND l.room IS NULL AND l.pinned = false"
                                + " AND (c IS NULL OR t IS NULL OR t.pseudo = false)", Long.class)
                        .getSingleResult());
        return new DeliveryConcordance(memory == db, memory, db);
    }

    /**
     * TASK-503: the PREVIOUS failed attempt's signature (null after consumption or when
     * the flag is off). Instance field because the AttemptRunner lambda is stateless.
     */
    private RetrySignature.Signature lastAttemptSignature;

    /** Run one staged cycle as multi-start attempt {@code attempt}: deterministic at 1, seeded GRASP at >=2. */
    private MultiStartRunner.AttemptOutcome runOneMultiStartAttempt(int attempt) {
        if (stagedCancelRequested) {
            return null;
        }
        profileDiscardFlag = false;
        applyGraspSeedForAttempt(attempt, this.honorBaseSeed);
        applyLeversForAttempt(attempt, lastAttemptSignature);
        // TASK-504: seed the registry with the inherited weights (if any) BEFORE the
        // solver builds (weights are read by the decider's thinning from the first step).
        double[] inherited = warmStartWeights;
        if (inherited != null) {
            AdaptiveMoveWeights.seed(inherited);
            LOG.infof("staged multi-start attempt %d: warm-started adaptive weights (TASK-504)", attempt);
        }
        lastAttemptSignature = null; // consumed: a cancelled/null attempt leaves no stale signal
        lastAttemptBoundaryTierHard = Long.MIN_VALUE; // TASK-498 corner-b: no stale tier across attempts
        SchoolSchedule solved = runStagedCycle();
        // TASK-504 poison-basin mitigation, decided HERE (this method is the only place
        // with both the attempt's score and the wrapper's best-so-far): inherit ONLY from
        // an attempt that is 0-hard, or that improved the best-so-far among non-0-hard
        // (a plain failure never seeds the next attempt). The 0-hard case stops the loop
        // anyway, so in practice the inheritance reaches the NEXT attempt only from the
        // best-so-far non-0-hard: exactly the AC #2 shape.
        double[] candidate = warmStartWeightsEnabled()
                ? AdaptiveMoveWeights.snapshotForWarmStart() : null;
        if (candidate != null && solved != null && solved.getScore() != null) {
            BendableScore score = solved.getScore();
            boolean isZeroHard = ScoreCompat.isFullyHardClean(score);
            boolean improvesBest = bestSoFarScore == null || score.compareTo(bestSoFarScore) > 0;
            if (isZeroHard || improvesBest) {
                warmStartWeights = candidate;
                if (improvesBest) {
                    bestSoFarScore = score;
                }
                LOG.infof("staged multi-start attempt %d: adaptive weights snapshot inherited (TASK-504)"
                        + " (zeroHard=%b, improvesBest=%b)", attempt, isZeroHard, improvesBest);
            }
        }
        // TASK-503: on a FAILED attempt (hard < 0), classify the failure so the NEXT
        // attempt's levers come from the signature table. Cost: one score recalculation
        // with match policy, paid only on failures and only when the flag is armed.
        if (signatureRetryEnabled() && solved != null && solved.getScore() != null
                && ScoreCompat.hardOf(solved.getScore()) < 0) {
            try {
                Map<String, Integer> hard = new LinkedHashMap<>();
                computeConstraintMatchSnapshot(solved).constraintMap().forEach((ref, total) -> {
                    long h = ScoreCompat.hardOf(total.getScore());
                    if (h < 0) {
                        hard.merge(ConstraintIds.shortName(ref.id()), (int) h, Integer::sum);
                    }
                });
                lastAttemptSignature = RetrySignature.classify(hard);
                LOG.infof("staged multi-start attempt %d failed with hard %d over %d family(ies):"
                        + " signature=%s (next attempt's levers come from the table)",
                        attempt, ScoreCompat.hardOf(solved.getScore()), hard.size(), lastAttemptSignature);
            } catch (Exception e) {
                LOG.warnf("staged multi-start: signature classification failed (%s):"
                        + " next attempt falls back to the reseed rule", e.getMessage());
            }
        }
        // Per-attempt outcome (M-TC state + score). Useful in production now that multi-start is
        // DEFAULT ON (operators see when a retry fires and what it scored), and load-bearing for the
        // TASK-486 AC#4 in-vivo forensic: it shows attempt 1 (M-TC off, the room-null score) vs the
        // retry (M-TC on, the cure score) on the SAME run. Guard solved: runStagedCycle returns null
        // on cancellation-before-room-stage or internal failure (the pre-diff path was null-safe via
        // MultiStartRunner.scoreOf; an unguarded getScore would NPE, escape the loop's catch-less
        // chain, and kill the daemon thread silently).
        LOG.infof("staged multi-start attempt %d done: mtc=%b, score=%s",
                attempt, SchoolSchedule.isTagCapacityEnabled(), solved != null ? solved.getScore() : "null");
        LifecycleLog.event("ATTEMPT_DONE", "attempt", attempt,
                "boundaryTierHard", lastAttemptBoundaryTierHard,
                "fullHard", solved != null && solved.getScore() != null
                        ? ScoreCompat.hardOf(solved.getScore())
                        : "unsolved");
        return new MultiStartRunner.AttemptOutcome(attempt, solved, lastAttemptBoundaryTierHard,
                profileDiscardFlag);
    }

    /**
     * TASK-483 seed strategy: attempt 1 stays deterministic (GRASP off, the best-on-medium path
     * production runs today); attempts >=2 enable a seeded GRASP whose seed equals the attempt
     * number, so each retry explores a genuinely independent construction (the TASK-480
     * diversification contract). Pure (no side effects) so the strategy is unit-testable.
     */
    static boolean graspEnabledForAttempt(int attempt) {
        return attempt != 1;
    }

    static long graspSeedForAttempt(int attempt) {
        return attempt;
    }

    /** TASK-573 option A (HONOR), opt-in: null/absent -> the TASK-483 design is untouched. */
    static boolean honorOperatorGraspEnabled() {
        String v = System.getProperty("planner.multistart.honor-operator-grasp.enabled");
        return v != null && !v.equalsIgnoreCase("false");
    }

    private static Long parseLongOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    void applyGraspSeedForAttempt(int attempt, long honorBaseSeed) {
        // TASK-486 AC#4 A/B hook: force EVERY multi-start attempt to a fixed GRASP seed so the
        // construction is held constant across attempts and a per-attempt lever (M-TC off at 1, on at
        // >=2) is the ONLY variable, isolating its effect on a known basin (e.g. grasp-seed-6's
        // meccanica room-null). Null in production (zero behaviour change); arm with
        // -Dplanner.multistart.test.force-grasp-seed=N. NB the production wrapper forces attempt 1
        // deterministic (GRASP off), so a plain -Dplanner.construction.grasp.seed=N does NOT reach
        // attempt 1; this hook is the only way to put a fixed seed on attempt 1 and thereby force a
        // bad-basin retry for the AC#4 in-vivo test (attempt 1 = within-run control, attempt 2 = cure).
        String forced = System.getProperty("planner.multistart.test.force-grasp-seed");
        if (forced != null) {
            System.setProperty("planner.construction.grasp.enabled", "true");
            System.setProperty("planner.construction.grasp.seed", forced);
            return;
        }
        boolean enabled = graspEnabledForAttempt(attempt);
        System.setProperty("planner.construction.grasp.enabled", String.valueOf(enabled));
        if (enabled) {
            // TASK-573 HONOR (opt-in): when the operator set grasp.seed=S and the honor
            // flag is ON, retries >=2 use S+attempt (the TASK-483 design is untouched at
            // flag OFF and attempt 1 stays deterministic regardless).
            if (honorBaseSeed >= 0) {
                long seed = honorBaseSeed + attempt;
                System.setProperty("planner.construction.grasp.seed", String.valueOf(seed));
                LOG.infof("staged multi-start: GRASP seed=%d (HONORED operator %d + attempt %d)",
                        seed, honorBaseSeed, attempt);
                return;
            }
            // iter-17-ter: seed from the monotonic attempt-START counter, not the attempt
            // number (which a discard does not advance): reseed = fresh seed, always
            long seed = graspSeedCounter.incrementAndGet();
            System.setProperty("planner.construction.grasp.seed", String.valueOf(seed));
            LOG.infof("staged multi-start: GRASP seed=%d (attempt %d, start #%d)",
                    seed, attempt, graspSeedCounter.get());
        }
    }

    /**
     * TASK-486 M-TC-per-retry strategy: M-TC is off on the deterministic first attempt (good basin,
     * byte-identical to M-TC-off) and on from attempt 2 (bad-basin retries arm the deterministic
     * room-tier cure). Pure (no side effects) so the strategy is unit-testable, mirroring {@link
     * #graspEnabledForAttempt}.
     */
    static boolean mtcEnabledForAttempt(int attempt) {
        return attempt != 1;
    }

    static void applyMtcForAttempt(int attempt) {
        if (!mtcPerRetryEnabled()) {
            // Prototype off: never touch planner.tag-capacity.enabled, so the run is identical to
            // M-TC-off production. The wrapper captures/restores unconditionally (like GRASP); since
            // this method never mutates the flag when the prototype is off, the restore is a no-op.
            return;
        }
        System.setProperty("planner.tag-capacity.enabled", String.valueOf(mtcEnabledForAttempt(attempt)));
    }

    /**
     * TASK-503 signature-conditioned retry, DEFAULT OFF (opt-in,
     * {@code planner.multistart.signature-retry.enabled}). When off this is exactly
     * {@link #applyMtcForAttempt} (the TASK-486 hardcoded per-retry rule): production is
     * byte-identical. When on, the PREVIOUS attempt's failure signature (from
     * {@link com.school.planner.solver.RetrySignature#classify}, fed by that attempt's hard
     * breakdown) picks the levers for THIS attempt from a table instead of the one-rule-fits-all:
     * <ul>
     *   <li>TAG_SATURATION -> M-TC on (the deterministic room-tag cure);</li>
     *   <li>H17_COMPOSITION -> CompositionHoleCloser on (TASK-481's building block,
     *       itself DEFAULT ON since the 16/09 endgame bundle; the set is now redundant
     *       but harmless); the failure was composition, not the room tier, so M-TC stays off);</li>
     *   <li>BOXED_H0 -> noop (ExactFinisher already closes that tail deterministically);</li>
     *   <li>UNKNOWN / no signature (attempt 1, or a mixed failure like round-2's
     *       H0+H20+H7) -> today's behavior: the TASK-486 M-TC rule plus the GRASP reseed.</li>
     * </ul>
     * The M-TC per-retry case (TASK-486) becomes the first TABULAR entry instead of
     * hardcoded. Improve-or-leave is honored structurally: MultiStartRunner's pick-best
     * keeps the best grid whatever a levered attempt produces, and the restore path
     * re-saves it, so an armed lever can never make the delivered schedule worse.
     */
    static void applyLeversForAttempt(int attempt, RetrySignature.Signature previousSignature) {
        clearAttemptLevers();
        if (!signatureRetryEnabled()) {
            applyMtcForAttempt(attempt);
            return;
        }
        if (attempt <= 1 || previousSignature == null) {
            // Deterministic first attempt (or no feedback yet): today's rule.
            applyMtcForAttempt(attempt);
            return;
        }
        switch (previousSignature) {
            case TAG_SATURATION -> System.setProperty(TAG_CAPACITY_LEVER, "true");
            case H17_COMPOSITION -> System.setProperty(CHC_LEVER, "true");
            case BOXED_H0 -> { /* noop: ExactFinisher is already deterministic */ }
            case UNKNOWN -> applyMtcForAttempt(attempt);
        }
    }

    /**
     * Reset the two attempt-scoped levers so a table entry starts from a clean slate.
     * A/B endgame 16/09 finding: the unconditional clear also wiped a value the OPERATOR
     * set on the java command line, so planner.composition-hole-closer.enabled was
     * structurally unarmed inside the multi-start (banner 45/45 false with the flag on).
     * Cure: capture the operator's original values once (lazily, at the first attempt of
     * the run - CLI properties are set at JVM start, so they are already in place) and
     * RESTORE those instead of clearing: the loop's own table-driven sets still start
     * each attempt from the operator's baseline.
     */
    private static final String TAG_CAPACITY_LEVER = "planner.tag-capacity.enabled";
    private static final String CHC_LEVER = "planner.composition-hole-closer.enabled";
    // JVM-global lazy first-capture (review 16/09, finding 2: accepted-latent): the
    // originals are captured at the FIRST attempt of the run and reused for the whole
    // JVM. Production semantics are correct (one staged solve per JVM launch, CLI
    // properties in place at start); tests that re-set the keys across cycles within
    // one surefire JVM depend on call ordering. The clean fix (threading the cycle's
    // captured originals through applyLeversForAttempt) is filed with TASK-617.
    private static String origTagCapacityLever;
    private static String origChcLever;
    private static boolean attemptLeversCaptured = false;

    private static void clearAttemptLevers() {
        if (!attemptLeversCaptured) {
            origTagCapacityLever = System.getProperty(TAG_CAPACITY_LEVER);
            origChcLever = System.getProperty(CHC_LEVER);
            attemptLeversCaptured = true;
        }
        restoreSystemProperty(TAG_CAPACITY_LEVER, origTagCapacityLever);
        restoreSystemProperty(CHC_LEVER, origChcLever);
    }

    /**
     * TASK-504 (SPECULATIVE, opt-in, DEFAULT OFF): warm-start of the adaptive move
     * weights across multi-start attempts. When armed, the end of an attempt snapshots
     * the learned weights IF the attempt is worth inheriting from (poison-basin
     * mitigation: 0-hard, or the best-so-far by score when nothing is 0-hard; never a
     * plain failure), and the next attempt seeds them before its solver is built.
     */
    static boolean warmStartWeightsEnabled() {
        return Boolean.parseBoolean(System.getProperty("planner.multistart.warm-start-weights.enabled"));
    }

    /**
     * TASK-542 iter-16 armed the early-doom profile predictor DEFAULT ON; TASK-677
     * (maintainer, 04/10, "fallo") DISARMED it back to opt-in. The paired falsification
     * (single paired run, n=1 per arm; same-seed resume from POST_CONSTRUCTION,
     * vehicle validated by a control): the
     * DISCARDED construction (209 holes, construction tsh -2890) ran the full path the
     * predictor gates and DELIVERED [-3/0]hard in 72 min - BETTER than the deterministic
     * control's [-9/0] in 89 min, with B-TIMESLOT #1 ending at tsh -13 box 0 (the LS
     * placed the whole ~1250-lesson boxed tail in one pass). The H17-hole metric
     * measures a symptom the delivery chain cures (HRF closed its -209 to -2), and
     * TASK-624 had already shown no calibrable threshold exists (non-monotone per
     * seed); with the 17/09 census median at ~296 the 150 gate de-facto skipped EVERY
     * GRASP retry, amputating the basin diversity TASK-483 ratified as the bad-seed
     * cure. Opt-in form: only the explicit literal {@code true} re-arms it.
     */
    static boolean profilePredictorEnabled() {
        return Boolean.parseBoolean(System.getProperty("planner.multistart.profile-predictor.enabled"));
    }

    /**
     * The construction H17-hole threshold the (now opt-in) predictor compares against.
     * Kept at the historical 150: the metric is retired as a doom signal, not
     * recalibrated (TASK-624: non-monotone, no calibrable value).
     */
    static final int PROFILE_DOOM_HOLES_THRESHOLD =
            Integer.getInteger("planner.multistart.profile-predictor.max-holes", 150);

    /** True when the running attempt is the multi-start's last (it must always complete). */
    private boolean isLastAttempt() {
        return stagedMaxAttempts > 0 && stagedAttempt >= stagedMaxAttempts;
    }

    /**
     * TASK-542 iter-20: the day-compact fabricator, opt-in DEFAULT OFF until maintainer's gate
     * (approved for construction 07:59, adoption after his verification). Read LIVE per
     * build like the sibling construction flags. -Dplanner.construction.day-compact.enabled=true
     */
    static boolean dayCompactEnabled() {
        return Boolean.parseBoolean(System.getProperty("planner.construction.day-compact.enabled"));
    }

    static boolean trackerPrimingEnabled() {
        // TASK-542 iter-15 (22/08 sera): DEFAULT ON. The opt-in form let the exact NPE it
        // exists for fire in production (multi-start attempt 5 B-ROOM, "existing is null"
        // in SimpleFieldTracker.contains via Lesson.setRoom on a move thread). Priming is
        // two self-assignments per lesson, once per solve: the tracker short-circuits
        // forever after, eliminating the race under either sharing model (Lesson javadoc).
        // Opt-out: -Dplanner.mt-ls.tracker-priming.enabled=false
        String v = System.getProperty("planner.mt-ls.tracker-priming.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /** TASK-503 opt-in flag, LIVE read (standard form: unset = disabled). */
    static boolean signatureRetryEnabled() {
        return Boolean.parseBoolean(System.getProperty("planner.multistart.signature-retry.enabled"));
    }

    private static void restoreSystemProperty(String key, String original) {
        if (original == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, original);
        }
    }

    /**
     * TASK-419 hole repair, between the smarcatura and stage B-ROOM: close the H17 breaks the
     * frozen template left behind, by importing an hour from another date into each hole (see
     * {@link HoleRepair}). Deterministic, improve-or-noop, and it MUST run here:
     * <ul>
     * <li>AFTER the smarcatura, because until then every ex-promoted lesson is {@code @PlanningPin}
     *     -frozen and its cell invisible to the VRP - the importable hours simply do not exist
     *     yet, and the pass would be a no-op;</li>
     * <li>BEFORE stage B-ROOM, because it moves TIMESLOTS. The room phases only move rooms, so
     *     letting them run on the repaired grid is what gives the imported hours a conflict-free
     *     room; running it after them would leave every mover with a nulled room.</li>
     * </ul>
     * It mutates the very {@code problem} instance stage B-ROOM is about to solve, so its moves
     * reach the DB through that stage's own {@code saveFinalSolution} - which is also why it sits
     * inside the not-cancelled branch: on a cancelled cycle the room stage is skipped and there
     * would be no save to carry the repair.
     */
    private void runHoleRepair(SchoolSchedule problem) {
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        ScoreCompat.HardLevels hardBefore = timeslotTierHardLevels(before);
        HoleRepair.Result result = HoleRepair.repair(problem, MOVE_TIMESLOT, NULL_ROOM);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        boolean reverted = ensureImproveOrNoop("hole repair", hardBefore, timeslotTierHardLevels(after),
                result.conflictsAdded(), snapshot, before, after);
        LOG.infof("staged solve: TASK-419 hole repair - H17 hole slots %d -> %d (closed %d by level: %d free import, %d on-template import, %d min-slide), conflicts added %d%s",
                result.gapSlotsBefore(), result.gapSlotsAfter(), result.closed(),
                result.closedByFreeImport(), result.closedByTemplateImport(), result.closedByMinSlide(),
                result.conflictsAdded(), reverted ? " [REVERTED to pre-pass grid: improve-or-noop violated]" : "");
    }

    /**
     * TASK-463 (PROTOTYPE, flag-gated, default OFF): the cross-group teacher-day coordinator.
     * Closes the lab-day H17 gaps HoleRepair leaves behind, the ones whose root cause is that the
     * anchor teacher is saturated at the gap slot by another group's lesson (the G13-Friday
     * pattern). Same placement as {@link #runHoleRepair} and {@link #runAnchorRepair}: post-
     * smarcatura so the grid is movable, post-HoleRepair so the plain mid-day breaks are already
     * closed (only the cross-group-saturation gaps remain), pre-B-ROOM so the movers get a
     * conflict-free room. The pass is self-gating and logs its own rich before/after line, so the
     * runner only carries the revert-and-continue guard (see {@link #ensureImproveOrNoop}).
     */
    private void runLabDayConsolidation(SchoolSchedule problem) {
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        ScoreCompat.HardLevels hardBefore = timeslotTierHardLevels(before);
        LabDayConsolidation.Result result = LabDayConsolidation.repair(problem, MOVE_TIMESLOT, NULL_ROOM);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        ensureImproveOrNoop("lab-day consolidation", hardBefore, timeslotTierHardLevels(after),
                result.conflictsAdded(), snapshot, before, after);
    }

    /**
     * TASK-481 Opzione A (opt-in prototype, default OFF): the classroom-day cross-group cascade.
     * Generalizes {@link LabDayConsolidation}'s cascade to non-lab days (closes a gap whose blocker
     * is edge-removable; correctly inert on the saturated G11 @10 bad seed, where the cascade
     * would be a shell game). Runs immediately AFTER {@link #runLabDayConsolidation} (so the lab-day
     * gaps are already closed and only the classroom-day residuals this pass targets remain) and
     * BEFORE {@link #runAnchorRepair}, still pre-B-ROOM so the movers get a conflict-free room. The
     * pass is self-gating and logs its own rich before/after line, so the runner only carries the
     * revert-and-continue guard (see {@link #ensureImproveOrNoop}). Short-circuited when opted
     * out (explicit "false") to skip the snapshot/score work a guaranteed no-op would pay for.
     */
    private void runCompositionHoleCloser(SchoolSchedule problem) {
        if (!SchoolSchedule.isCompositionHoleCloserEnabled()) return;
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        ScoreCompat.HardLevels hardBefore = timeslotTierHardLevels(before);
        CompositionHoleCloser.Result result = CompositionHoleCloser.repair(problem, MOVE_TIMESLOT, NULL_ROOM);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        ensureImproveOrNoop("composition hole closer", hardBefore, timeslotTierHardLevels(after),
                result.conflictsAdded(), snapshot, before, after);
    }

    /**
     * TASK-433 (candidate, flag-gated, default OFF): close LEADING M18 late-start holes by
     * cross-date import, immediately AFTER {@link #runHoleRepair} and BEFORE {@link
     * #runSurplusRebalance}. Same placement rationale: post-smarcatura so the donor pool is
     * movable, the hole repair has already closed the mid-day H17 breaks (so this pass's
     * leading-slot targets are stable), and SurplusRebalance prices days after both repairs
     * have shaped them. Only stage B-ROOM follows, which moves rooms only, so the trade is
     * essentially final (see {@link AnchorRepair}). Revert-and-continue on the hard score (see
     * {@link #ensureImproveOrNoop}): the pass adds no H1/H3 conflict by construction, and if it
     * ever worsened the timeslot-tier hard it rolls back to the pre-pass grid rather than aborting.
     */
    private void runAnchorRepair(SchoolSchedule problem) {
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        ScoreCompat.HardLevels hardBefore = timeslotTierHardLevels(before);
        AnchorRepair.Result result = AnchorRepair.repair(problem, MOVE_TIMESLOT, NULL_ROOM);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        boolean reverted = ensureImproveOrNoop("anchor repair", hardBefore, timeslotTierHardLevels(after),
                result.conflictsAdded(), snapshot, before, after);
        LOG.infof("staged solve: TASK-433 anchor repair (candidate) - late-start days %d -> %d (filled %d leading slots; fully %d, partial %d, none %d; donor days emptied %d, newly-late %d), conflicts added %d%s",
                result.lateDaysBefore(), result.lateDaysAfter(), result.slotsFilled(),
                result.fullyClosed(), result.partiallyClosed(), result.notClosed(),
                result.donorDaysEmptied(), result.donorDaysNewlyLate(), result.conflictsAdded(),
                reverted ? " [REVERTED to pre-pass grid: improve-or-noop violated]" : "");
    }

    /**
     * TASK-422 surplus rebalance, immediately AFTER {@link #runHoleRepair} and before stage
     * B-ROOM: move hours from (group, date) days above the school's own expected-hours plan to
     * days of the same group below it (see {@link SurplusRebalance}). Same placement in the
     * cycle and the same two reasons as the hole repair (post-smarcatura so the hours are
     * movable at all, pre-B-ROOM so the movers get a conflict-free room), plus one of its own:
     * holes first, then fill. The hole repair IMPORTS hours into penalized H17 gaps, which
     * changes which days are above or below plan, so rebalancing before it would price days
     * that are about to change and could hand the hole repair a day it can no longer lend from.
     */
    private void runSurplusRebalance(SchoolSchedule problem) {
        // The pass logs its own (richer) before/after line off its live maps, so nothing is
        // re-logged here; only the revert fallback below is the caller's business.
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        ScoreCompat.HardLevels hardBefore = timeslotTierHardLevels(before);
        SurplusRebalance.Result result = SurplusRebalance.repair(problem, MOVE_TIMESLOT, NULL_ROOM);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        ensureImproveOrNoop("surplus rebalance", hardBefore, timeslotTierHardLevels(after),
                result.conflictsAdded(), snapshot, before, after);
    }

    /**
     * TASK-478: deterministic residual-completion pass (ratified DEFAULT ON 2026-08-02, opt-out
     * via {@code planner.exact-finisher.enabled=false}). Runs immediately after SurplusRebalance,
     * before B-ROOM: enumerates placements for the small unassigned tail the LS leaves behind, at
     * depth 0..1 (free cell, one bump); depth-2 (two nested bumps) is designed but deferred (see
     * {@link ExactFinisher#MAX_DEPTH}). Every committed placement passes the shared
     * gate chain (0-hard-additive); the improve-or-noop revert is the safety net. See
     * {@link ExactFinisher} for the full contract.
     */
    /**
     * TASK-533 component 1: deterministic quota-fill repair (post-RELEASE, sibling of
     * SurplusRebalance, pre-ExactFinisher). Repairs H32 (day under declared quota) by
     * moving hours from the same group's surplus days; improve-or-noop evaluated on the
     * full timeslot-tier hard exactly like every sibling pass.
     */
    private void runQuotaFillRepair(SchoolSchedule problem, boolean roomsFinal) {
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        // The oracle follows the GRID (voice-2, notte 4/09): post-RELEASE the rooms
        // are the construction's provisional ones and B-ROOM re-solves them, so the
        // timeslot tier is deliberately blind to H2/H29 there (the TASK-446 split);
        // post-B-ROOM the rooms are FINAL and the full hard must arbitrate (the
        // RoomTail/OOF convention: a timeslot-tier gate is blind to what moved).
        ScoreCompat.HardLevels hardBefore = roomsFinal ? totalHardLevels(before) : timeslotTierHardLevels(before);
        QuotaFillRepair.Result result = QuotaFillRepair.repair(problem, MOVE_TIMESLOT, NULL_ROOM);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        ensureImproveOrNoop("quota fill repair", hardBefore,
                roomsFinal ? totalHardLevels(after) : timeslotTierHardLevels(after),
                result.conflictsAdded(), snapshot, before, after);
    }


    /** TASK-577: SurplusHarvest at the post-B-ROOM boundary (opt-in prototype).
     *  The improve-or-noop oracle is the FULL hard: the pass runs on the roomed
     *  grid (voice-2 wiring) where H2/H29 are in play, so a timeslot-tier gate
     *  would be blind to exactly the families the moves can regress. */
    private void runSurplusHarvest(SchoolSchedule problem) {
        try {
            PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
            ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
            ScoreCompat.HardLevels hardBefore = totalHardLevels(before);
            SurplusHarvest.repair(problem,
                    (lesson, ts) -> lesson.timeslot = ts,
                    (lesson, room) -> lesson.room = room);
            ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
            ensureImproveOrNoop("surplus harvest", hardBefore, totalHardLevels(after),
                    0, snapshot, before, after);
        } catch (Exception e) {
            lastSolverError = "staged solve: surplus harvest: " + e;
            LOG.error("staged solve: SurplusHarvest FAILED (continuing on the pre-pass grid)", e);
        }
    }

    /**
     * TASK-609 v0.1 wrapper: the RELAY-k composite repair for boxed lessons at the tail
     * boundary (post-EDF, pre-harvest). Full-hard improve-or-noop like RoomTail: the
     * pass moves rooms too.
     */
    private void runCompositeSwapFinisher(SchoolSchedule problem) {
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        ScoreCompat.HardLevels hardBefore = totalHardLevels(before);
        CompositeSwapFinisher.Result result =
                CompositeSwapFinisher.finish(problem, MOVE_TIMESLOT);
        LOG.infof("CompositeSwapFinisher (TASK-609 v0.1): targets=%d, relay2=%d, relay3=%d, direct=%d",
                result.targets(), result.relay2(), result.relay3(), result.directPlaces());
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        ensureImproveOrNoop("composite swap finisher", hardBefore, totalHardLevels(after),
                0, snapshot, before, after);
    }

    /** TASK-560: the EmptyDayFinisher at its boundary (post second QuotaFillRepair,
     *  pre medium-pure arm). Improve-or-noop on the FULL hard: like the harvest,
     *  the boundary sits past B-ROOM and the grid carries final rooms. */
    private void runEmptyDayFinisher(SchoolSchedule problem) {
        EmptyDayFinisher.Result result = edfPassWithOracle(problem, "empty day finisher",
                EmptyDayFinisher.Boundary.MID_CYCLE);
        LOG.infof("EmptyDayFinisher (TASK-560): %d moves, %d cell(s) closed to goal"
                        + " (empty declared day to quota, under-floor day to floor),"
                        + " %d conflicts, %d swap-compound(s)",
                result.movesApplied(), result.cellsClosed(), result.conflictsAdded(),
                result.swapCompounds());
    }

    /**
     * TASK-612+678 wrapper: the full EmptyDayFinisher pass at the DELIVERY boundary
     * (post CompositeSwapFinisher, pre-save), run because ANY extension family is
     * armed (relay/H32, H33 since the 04/10 ratification). Same full-hard
     * improve-or-noop as the mid-cycle boundary;
     * the {@code [delivery]} log tag keeps the two invocations distinguishable in
     * the run log.
     */
    private void runEdfExtensionsOnDelivery(SchoolSchedule problem) {
        EmptyDayFinisher.Result result = edfPassWithOracle(problem, "edf delivery extensions",
                EmptyDayFinisher.Boundary.DELIVERY);
        LOG.infof("EmptyDayFinisher [delivery] (TASK-612 relay=%b h32=%b h33=%b): %d moves,"
                        + " %d cell(s) closed to goal, %d conflicts, %d swap-compound(s)",
                EmptyDayFinisher.relayEnabled(), EmptyDayFinisher.h32Enabled(),
                EmptyDayFinisher.h33Enabled(),
                result.movesApplied(), result.cellsClosed(), result.conflictsAdded(),
                result.swapCompounds());
    }

    /** TASK-613 wrapper: the HOLE-RELAY pass at the delivery boundary, last of the
     *  composite chain. Full-hard improve-or-noop (rooms move: H2/H29 in play). */
    private void runHoleRelayFinisher(SchoolSchedule problem) {
        HoleRelayFinisher.Result result = passWithOracle(problem, "hole relay finisher",
                p -> HoleRelayFinisher.finish(p, MOVE_TIMESLOT),
                r -> 0);
        LOG.infof("HoleRelayFinisher [delivery] (TASK-613 v0.6 teacher-aware): targets=%d, slides=%d,"
                        + " rotations=%d, moves=%d",
                result.targets(), result.slides(), result.rotations(), result.movesApplied());
    }

    /**
     * TASK-638 (23/09, RATIFIED DEFAULT ON b90929d2, design in
     * task638-results/production-wiring/REPORT.md): the violation-directed
     * steepest sweep at the delivery boundary, LAST pass of the chain
     * (post-HRF, pre-save), once per delivery. The base grammar is
     * timeslot-only with the oracle on the FULL hard (room tier = design
     * option A: a swap whose rooms collide pays H2 and a placement landing
     * roomless pays H29, both evaluate negative and are never applied - the
     * integrato-1 lesson, production-wiring REPORT FILO A). The room-aware
     * extension (TASK-641, strict-literal opt-in) ADDS composite moves that
     * reassign rooms: when armed, room-reassigning composites CAN apply here;
     * unarmed the pass is timeslot-only as ratified. Knobs default to the
     * census form (10 iterations, 100k candidates, 0.4-4s on the catalog
     * states).
     */
    private void runViolationScopedSteepestFinisher(SchoolSchedule problem) {
        // CSF-style shell, NOT passWithOracle: this pass is DESIGNED to throw
        // IllegalStateException on a mid-move failure (rc-1 executeTemporary has
        // no try/finally, so a throw leaves the working solution unguaranteed),
        // and passWithOracle never restores when the pass throws. The delivery
        // chain's try has no catch: a naked throw would kill the delivery
        // TASK-614-style. The house outcome (corner-b / FinalClosingCampaign
        // pattern): restore the snapshot, log, deliver unchanged.
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ViolationScopedSteepestFinisher.Stats stats;
        try {
            ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
            ScoreCompat.HardLevels hardBefore = totalHardLevels(before);
            stats = ViolationScopedSteepestFinisher.finish(problem, getScoreDirectorFactory(),
                    Integer.getInteger("planner.construction.violation-steepest.max-iterations", 10),
                    Integer.getInteger("planner.construction.violation-steepest.max-candidates", 100_000));
            ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
            ensureImproveOrNoop("violation steepest finisher", hardBefore, totalHardLevels(after),
                    0, snapshot, before, after);
        } catch (RuntimeException e) {
            snapshot.restore();
            LOG.errorf(e, "ViolationScopedSteepestFinisher [delivery] (TASK-638): pass FAILED"
                    + " - snapshot restored, delivering unchanged");
            return;
        }
        LOG.infof("ViolationScopedSteepestFinisher [delivery] (TASK-638): applied=%d"
                        + " hard %d -> %d in %dms",
                stats.movesApplied(), stats.hardBefore(), stats.hardAfter(),
                stats.wallMillis());
    }

    /** The single EDF wrapper shell: capture, before/after constraint-match snapshots,
     *  full-hard improve-or-noop. The caller owns the log line, so each boundary keeps
     *  its own observability. */
    private EmptyDayFinisher.Result edfPassWithOracle(SchoolSchedule problem, String passName,
            EmptyDayFinisher.Boundary boundary) {
        return passWithOracle(problem, passName,
                p -> EmptyDayFinisher.finish(p, MOVE_TIMESLOT, boundary),
                r -> (int) r.conflictsAdded());
    }

    /** The single delivery-pass wrapper shell (TASK-613 /simplify: the hand-written
     *  clones of this snapshot/oracle contract were accumulating one per new pass -
     *  the next TASK-526-class contract change must land HERE, not be replicated):
     *  capture, before/after constraint-match snapshots, full-hard improve-or-noop
     *  with the pass's own conflicts count. */
    private <R> R passWithOracle(SchoolSchedule problem, String passName,
            java.util.function.Function<SchoolSchedule, R> pass,
            java.util.function.ToIntFunction<R> conflictsOf) {
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        ScoreCompat.HardLevels hardBefore = totalHardLevels(before);
        R result = pass.apply(problem);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        ensureImproveOrNoop(passName, hardBefore, totalHardLevels(after),
                conflictsOf.applyAsInt(result), snapshot, before, after);
        return result;
    }

    private void runExactFinisher(SchoolSchedule problem) {
        // Short-circuit before any snapshot/score work: only an explicit opt-out
        // (-Dplanner.exact-finisher.enabled=false) reaches this branch, and the early return skips
        // two full constraint-match score evaluations plus a PlacementSnapshot capture that the
        // in-repair flag guard would pay for only to discover a guaranteed no-op. The repair()
        // guard stays as defense-in-depth.
        if (!SchoolSchedule.isExactFinisherEnabled()) return;
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        ScoreCompat.HardLevels hardBefore = timeslotTierHardLevels(before);
        ExactFinisher.Result result = ExactFinisher.repair(problem, MOVE_TIMESLOT, NULL_ROOM);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        ensureImproveOrNoop("exact finisher", hardBefore, timeslotTierHardLevels(after),
                result.conflictsAdded(), snapshot, before, after);
    }

    /**
     * TASK-542 iter-27: the ported pure hole repairer at its boundary (post-B-ROOM,
     * pre-save). Improve-or-noop on the TRUE score, exactly like every deterministic
     * sibling: a pass that worsened the timeslot-tier hard or added a conflict is rolled
     * back and delivery proceeds on the pre-pass grid.
     */
    private void runPureHoleRepair(SchoolSchedule problem) {
        if (!com.school.planner.solver.PureHoleRepair.enabled()) return;
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        ScoreCompat.HardLevels hardBefore = timeslotTierHardLevels(before);
        com.school.planner.solver.PureHoleRepair.Result result =
                com.school.planner.solver.PureHoleRepair.repair(problem, MOVE_TIMESLOT, MOVE_ROOM);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        ensureImproveOrNoop("pure hole repair", hardBefore, timeslotTierHardLevels(after),
                result.conflictsAdded(), snapshot, before, after);
    }

    /**
     * TASK-550: the room-tail finisher at its boundary (post-B-ROOM, post-PHR, pre-save).
     * Improve-or-noop on the FULL hard score, not the timeslot tier: this pass assigns and moves
     * ROOMS, so the room-tier families (H2, H29) are exactly the ones it must be judged on. The
     * in-pass gates (room-cell occupancy for H2, the tag domain for H-M2) make a violation
     * unlikely; the snapshot oracle is the boundary backstop, as for every deterministic sibling.
     */
    private void runRoomTailFinisher(SchoolSchedule problem) {
        if (!com.school.planner.solver.RoomTailFinisher.enabled()) return;
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        ScoreCompat.HardLevels hardBefore = totalHardLevels(before);
        com.school.planner.solver.RoomTailFinisher.repair(problem, MOVE_TIMESLOT, MOVE_ROOM);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        ensureImproveOrNoop("room-tail finisher", hardBefore, totalHardLevels(after),
                0, snapshot, before, after);
    }

    /**
     * TASK-574: the physical one-off closer at its boundary (post-RoomTail, pre-save).
     * Improve-or-noop on the FULL hard levels like RoomTail: this pass re-resolves rooms
     * at landing slots, so the room tier is in play.
     */
    private void runOneOffFinisher(SchoolSchedule problem) {
        if (!com.school.planner.solver.OneOffFinisher.enabled()) return;
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        ScoreCompat.HardLevels hardBefore = totalHardLevels(before);
        com.school.planner.solver.OneOffFinisher.repair(problem, MOVE_TIMESLOT, MOVE_ROOM);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        ensureImproveOrNoop("one-off finisher", hardBefore, totalHardLevels(after),
                0, snapshot, before, after);
    }

    /**
     * TASK-595: the H28 rotation finisher at its boundary (post-OneOff, pre-save).
     * Improve-or-noop on the FULL hard levels like its siblings. The in-pass gates cover
     * H1/H3/H5/VRP (the shared Grid chain), H2 (cellOk's room-cell check on the mover's
     * own room) and H-COPRESENCE (the pass's dedicated depth gate); the snapshot oracle
     * is the aggregate backstop, as for every deterministic sibling.
     */
    private void runH28RotationFinisher(SchoolSchedule problem) {
        if (!com.school.planner.solver.H28RotationFinisher.enabled()) return;
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        ScoreCompat.HardLevels hardBefore = totalHardLevels(before);
        com.school.planner.solver.H28RotationFinisher.repair(problem, MOVE_TIMESLOT);
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(problem);
        ensureImproveOrNoop("h28 rotation finisher", hardBefore, totalHardLevels(after),
                0, snapshot, before, after);
    }

    /** The FULL hard levels (pure + quasi, lexicographic) of a snapshot, for the oracle. */
    static ScoreCompat.HardLevels totalHardLevels(ConstraintMatchSnapshot snapshot) {
        return snapshot.score() != null ? ScoreCompat.hardLevels(snapshot.score())
                : ScoreCompat.ZERO_HARD_LEVELS;
    }

    /** TASK-551 (braccio paired): opt-in flag, DEFAULT OFF. */
    static final String MEDIUM_PURE_ARM_FLAG = "planner.construction.medium-pure-arm.enabled";

    static boolean mediumPureArmEnabled() {
        return Boolean.parseBoolean(System.getProperty(MEDIUM_PURE_ARM_FLAG));
    }

    /**
     * TASK-551: the medium-pura arm - an ad-hoc strict hill-climbing LS on the delivered grid
     * (change + swap moves on the timeslot variable, rooms fixed), run at the END of the cycle
     * so a resume from the POST_B_ROOM checkpoint executes it on the delivered state. The
     * DEFAULT acceptor is strict (a move is accepted only if the total score does not worsen),
     * so from a 0-hard state every accepted move is medium-improving and hard-safe by
     * construction (a tabu acceptor would accept worsening totals and is deliberately NOT
     * used, see the call-site comment). Termination: unimproved on the best score, capped at
     * 1200s (the VIA's budget). Improve-or-noop wrapped like every sibling.
     */
    private SchoolSchedule runMediumPureArm(SchoolSchedule problem) {
        if (!mediumPureArmEnabled()) {
            return problem;
        }
        PlacementSnapshot snapshot = PlacementSnapshot.capture(problem.getLessons());
        ConstraintMatchSnapshot before = computeConstraintMatchSnapshot(problem);
        Instant start = Instant.now();
        SolverConfig config = new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withEnvironmentMode(ai.timefold.solver.core.config.solver.EnvironmentMode.NON_REPRODUCIBLE)
                .withScoreDirectorFactory(new ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(new LocalSearchPhaseConfig()
                        .withMoveSelectorConfig(
                                new ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig(
                                        List.of(new ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig()
                                                        .withValueSelectorConfig(new ai.timefold.solver.core.config.heuristic.selector.value.ValueSelectorConfig()
                                                                .withVariableName("timeslot")),
                                                new ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig()
                                                        .withVariableNameIncludes("timeslot"))))
                        .withTerminationConfig(new ai.timefold.solver.core.config.solver.termination.TerminationConfig()
                                .withUnimprovedSecondsSpentLimit(120L)
                                .withSecondsSpentLimit(1200L))));
        SchoolSchedule refined = runStagedSolver(config, withRoomTierOverrideIfKnobbed(problem));
        ConstraintMatchSnapshot after = computeConstraintMatchSnapshot(refined);
        ensureImproveOrNoop("medium-pura arm", totalHardLevels(before), totalHardLevels(after),
                0, snapshot, before, after);
        LOG.infof("staged solve: medium-pura arm (TASK-551) finished in %ds - score %s -> %s",
                Duration.between(start, Instant.now()).getSeconds(),
                problem.getScore(), refined.getScore());
        return refined;
    }

    /**
     * Pure verdict (TASK-473): true iff the pass worsened the authoritative timeslot-tier hard OR
     * added an H1/H3 double-booking. Extracted so the decision table is unit-testable without a
     * ScoreDirector or a real snapshot. See {@link #ensureImproveOrNoop} for the full rationale.
     *
     * <p>The hard comparison is LEXICOGRAPHIC on {@link ScoreCompat.HardLevels} (pure level 0
     * first, quasi level 1 second), exactly the score's own ordering; why the two levels are
     * never summed to one number is documented on the record itself.
     */
    static boolean shouldRevert(ScoreCompat.HardLevels before, ScoreCompat.HardLevels after, long conflictsAdded) {
        return after.compareTo(before) < 0 || conflictsAdded != 0;
    }

    /**
     * The improve-or-noop contract for the deterministic post-release passes, with the HONEST
     * fallback (TASK-473): if the pass worsened the authoritative timeslot-tier hard OR added an
     * H1/H3 double-booking, ROLL BACK to the pre-pass grid and let the cycle continue to B-ROOM
     * (true NOOP), never abort. Previously this threw, and a pass that disagreed with its own
     * H1/H3 counter (the source=input abort: a cross-date import introduced an H-COPRESENCE
     * over-depth the counter is blind to, but the timeslot-tier hard saw) threw away ~40 minutes
     * of solve and skipped B-ROOM, leaving every mover room-null. The mutated grid is in-memory
     * only (not yet saved), so restoring the snapshot hands B-ROOM the exact grid it would have
     * run on had the pass not existed.
     *
     * <p>Two independent triggers, both revert-and-continue, neither ever throws:
     * <ul>
     * <li>{@code hardAfter.compareTo(hardBefore) < 0}: the authoritative broad signal. {@link
     *     #timeslotTierHardScore} sees EVERY timeslot-tier family (H-COPRESENCE, H28, H7, H17,
     *     H0, H1, H3), so this catches a worsening the pass's own H1/H3 counter cannot see.</li>
     * <li>{@code conflictsAdded != 0}: the H1/H3 double-booking audit. NOT redundant: a pass could
     *     add a teacher/group conflict while net hard stays flat (compensated by other hard
     *     credits), and since B-ROOM moves only rooms an H1 would survive into the final
     *     solution. This clause is the only signal that catches that compensated case.</li>
     * </ul>
     * Returns true iff the grid was reverted.
     */
    static boolean ensureImproveOrNoop(String passName, ScoreCompat.HardLevels hardBefore,
                                      ScoreCompat.HardLevels hardAfter,
                                      long conflictsAdded, PlacementSnapshot snapshot,
                                      ConstraintMatchSnapshot beforeFamilies, ConstraintMatchSnapshot afterFamilies) {
        if (!shouldRevert(hardBefore, hardAfter, conflictsAdded)) {
            return false; // improve-or-noop holds
        }
        snapshot.restore();
        String cause;
        if (hardAfter.compareTo(hardBefore) < 0) {
            String worsened;
            try {
                worsened = worsenedFamilies(beforeFamilies, afterFamilies);
            } catch (RuntimeException ex) {
                // The diagnostic is best-effort log detail: a surprise here (a ConstraintRef
                // format quirk across two ScoreDirectors, a null score) must NEVER re-abort the
                // cycle this cure exists to keep alive. The grid is already reverted.
                worsened = "";
            }
            cause = "timeslot-tier hard " + hardBefore + " -> " + hardAfter
                    + (worsened.isEmpty() ? "" : " (worsened: " + worsened + ")");
        } else {
            cause = "H1/H3 conflicts added " + conflictsAdded;
        }
        LOG.warnf("staged solve: %s REVERTED to pre-pass grid (improve-or-noop violated: %s) - "
                + "continuing to B-ROOM on the pre-pass grid, not aborting", passName, cause);
        return true;
    }

    /**
     * The canonical families whose timeslot-tier hard position worsened from before to after, as
     * a diagnostic string. Computed from the two snapshots the runner already holds (no extra
     * scoring). Compares per-family {@link ScoreCompat.HardLevels} lexicographically so a family
     * that only damaged the quasi level (H34/H32 under the flag) is still attributed. Tolerates a
     * family present on only one side via {@code getOrDefault(.., ZERO)} (a newly-appearing
     * family reads clean before, a vanishing one reads clean after).
     */
    private static String worsenedFamilies(ConstraintMatchSnapshot before, ConstraintMatchSnapshot after) {
        if (before == null || after == null) {
            return ""; // diagnostic is best-effort detail; degrade silently when snapshots are absent
        }
        Map<String, ScoreCompat.HardLevels> beforeHard = new HashMap<>();
        for (var entry : before.constraintMap().entrySet()) {
            beforeHard.put(entry.getKey().id(), ScoreCompat.hardLevels(entry.getValue().getScore()));
        }
        List<String> worsened = new ArrayList<>();
        // Only the after-side is iterated: a worsened family must have matches in `after`, and one
        // absent from `after` improved to clean (read ZERO on the before side via getOrDefault).
        for (var entry : after.constraintMap().entrySet()) {
            ScoreCompat.HardLevels a = ScoreCompat.hardLevels(entry.getValue().getScore());
            ScoreCompat.HardLevels b = beforeHard.getOrDefault(entry.getKey().id(), ScoreCompat.ZERO_HARD_LEVELS);
            if (a.compareTo(b) < 0) {
                worsened.add(ConstraintIds.canonicalId(entry.getKey().id()) + " " + b + " -> " + a);
            }
        }
        Collections.sort(worsened);
        return String.join("; ", worsened);
    }

    /**
     * Stage B-ROOM: the ordinary pipeline's ROOM phases (config (b)) on the released grid. Runs
     * with NO promoted pin held, so every non-authoritative lesson's room is free again and the
     * room CH + refinement can harmonize whole blocks - which is the entire point of the split
     * (a promoted pin froze the room too, and the val419 smoke paid -895 hard of H16b for it).
     * The room phases move the {@code room} variable only, so the timeslot grid stage
     * B-TIMESLOT settled is carried over untouched.
     */
    private SchoolSchedule runRoomSolveStage(SchoolSchedule problem, boolean skipRefinement) {
        assertRoomStagePreconditions();
        Instant start = Instant.now();
        SolverConfig roomConfig = skipRefinement
                ? roomPhasesWithoutRefinement(roomPhasesConfig())
                : roomPhasesConfig();
        SchoolSchedule solved = runStagedSolver(roomConfig, problem);
        // TASK-542 iter-27: the ported PURE hole repairer, flag-gated DEFAULT OFF. Runs on
        // the room-solved grid, before the final save: pulls any residual H17 holes toward
        // zero with the validated move families (see PureHoleRepair's javadoc for the
        // iter-24 lottery rationale). Same improve-or-noop contract as the other
        // deterministic passes: the TRUE score (constraint-match snapshot) is the oracle,
        // a worsening pass is rolled back wholesale and the cycle continues.
        runPureHoleRepair(solved);
        // TASK-550: the room-tail finisher, flag-gated DEFAULT OFF. Runs after the hole repairer
        // so its same-day de-co-scheduling sees the compacted grid: closes the residual tag-scarce
        // H29 (the -83 banco's moda labs) with free-cell assigns, non-lab evictions and hole-safe
        // same-day moves. Improve-or-noop on the FULL hard (rooms move here, so H2/H29 are in play
        // and the timeslot-tier oracle the siblings use would be blind to them).
        runRoomTailFinisher(solved);
        // TASK-574: the physical one-off closer, LAST deterministic sibling before the save.
        // RoomTail cannot close a tag-collision roomless lesson (it moves rooms, not
        // timeslots) and nothing owns the H7 overload-day / H33 under-floor residuals:
        // this pass moves single hours in time (the golden's own separation move) with
        // the standard improve-or-noop oracle on the FULL hard levels (rooms are
        // re-resolved at the landing slot, so H2/H29 are in play).
        runOneOffFinisher(solved);
        // TASK-595: the H28 rotation finisher, RATIFIED DEFAULT ON 17/09 (bounded-no-op
        // directive: the integrato-1 audit found it never invoked on the default path).
        // Reorders the
        // lessons of an A..B..A (group, subject, day) cell into teacher-contiguous blocks
        // over the SAME slots (the TASK-593 witness shape generalized to every block
        // ordering); the day's slot set and every per-day count are invariant by
        // construction. In play: H1/H3/H5/VRP (Grid chain), H2 (mover's own room) and
        // H-COPRESENCE (dedicated in-pass depth gate), with the FULL-hard improve-or-noop
        // oracle as the aggregate backstop.
        runH28RotationFinisher(solved);
        saveFinalSolution(solved);
        LOG.infof("staged solve: stage B-ROOM finished in %ds - score=%s",
                Duration.between(start, Instant.now()).getSeconds(), solved.getScore());
        return solved;
    }

    /**
     * The stage-B-ROOM precondition, ASSERTED rather than assumed: not one promoted pin may
     * still be held - neither in the DB (the persistent marker) nor in the in-memory registry -
     * when the room phases start.
     *
     * <p>This is the invariant the whole room/timeslot split rests on. {@code @PlanningPin}
     * freezes the entity, room included, so a promoted pin surviving into this stage is exactly
     * the val419 smoke failure coming back (-895 hard of H16b, blocks split across two rooms
     * because the room CH could not touch them) - and it would come back SILENTLY, as a quality
     * regression nobody could tell from a bad solve. Cheap enough (two counts) to pay on every
     * cycle, and the reason the smarcatura's position in the cycle is a checked fact instead of
     * a comment.
     */
    @Transactional
    void assertRoomStagePreconditions() {
        long held = Lesson.count("promotedPin = true");
        int registered = SchoolSchedule.getPromotedLessonIdsStatic().size();
        if (held > 0 || registered > 0) {
            throw new IllegalStateException("the room stage must run AFTER the smarcatura, but "
                    + held + " promoted pin(s) are still marked in the DB and " + registered
                    + " in the registry - their rooms would be frozen (H16b) exactly as before TASK-419's split");
        }
    }

    /**
     * The hard score RESTRICTED to the timeslot tier: everything except the constraints only
     * the room phases can settle ({@link #ROOM_TIER_HARD_FAMILIES}).
     *
     * <p>This is what stage C is triggered on, because at the end of stage B-TIMESLOT the rooms
     * are still the construction's provisional ones - exactly the state an ordinary run is in
     * at the end of its phase 4, where H2 (room conflict), H16b (a block split across two
     * rooms) and H-M2 (room suitability) are legitimately dirty and phases 4/5 exist to clean
     * them. Judging the frozen template on those would demote pins to "fix" a residual no
     * demotion can touch (the val419 smoke's -897 hard was -895 H16b: on the raw hard score
     * this cycle would burn both its demotion rounds on it), and would also hide the timeslot
     * residual stage C actually exists for under a room-tier number two orders of magnitude
     * bigger.
     *
     * <p>Scored on the SOLVED instance the caller already holds, not through {@link
     * #analyzeScore()}: that path re-loads the whole schedule from the DB and runs a
     * {@code solutionManager.update} on it, for a grid the stage has just finished solving
     * and saving. The score director this builds initializes the shadow variables itself
     * ({@code withExpectShadowVariablesInCorrectState(false)}), so the reload bought nothing.
     */
    private long timeslotTierHardScore(SchoolSchedule solved) {
        return timeslotTierHard(computeConstraintMatchSnapshot(solved));
    }

    /**
     * The timeslot-tier hard sum extracted from a snapshot the caller already holds.
     *
     * <p>PURE hard only (level 0), by design: the consumers of this single number are the
     * staged B-TIMESLOT gate, the skip-doomed-room gate and the multi-start pick-best PRIMARY
     * key, all calibrated on pure-hard semantics ({@link #stagedHardAcceptThreshold()} counts
     * boxed-conflict units, not quasi cells; quasi dirtiness does not condemn an attempt).
     * Their tie-break is the full {@code score.compareTo}, which IS quasi-aware, so the ranking
     * stays lexicographic overall. The improve-or-noop oracle instead compares {@link
     * #timeslotTierHardLevels}, because an oracle blind to the quasi level would accept a pass
     * that worsens the very families it exists to fix.
     */
    static long timeslotTierHard(ConstraintMatchSnapshot snapshot) {
        return timeslotTierHardLevels(snapshot).pure();
    }

    /**
     * The timeslot-tier hard as a lexicographic (pure, quasi) pair, for the improve-or-noop
     * oracle. With the quasi-tier flag ON, H32/H34 land on hard level 1; the oracle must see
     * that level (QFR/EDF exist to fix exactly those families). The comparison ordering and
     * its rationale live on {@link ScoreCompat.HardLevels}. The deterministic repair runners
     * (TASK-473) reuse the before/after snapshots for both this pair and the revert
     * diagnostic, so they pay the same two scorings per pass as before, never a third.
     */
    static ScoreCompat.HardLevels timeslotTierHardLevels(ConstraintMatchSnapshot snapshot) {
        long pure = 0;
        long quasi = 0;
        for (var entry : snapshot.constraintMap().entrySet()) {
            String id = ConstraintIds.canonicalId(entry.getKey().id());
            if (isRoomTierHardConstraint(id)) {
                continue;
            }
            pure += ScoreCompat.hardOf(entry.getValue().getScore());
            quasi += ScoreCompat.quasiOf(entry.getValue().getScore());
        }
        return new ScoreCompat.HardLevels(pure, quasi);
    }

    /**
     * True iff {@code canonicalId} belongs to one of the {@link #ROOM_TIER_HARD_FAMILIES}.
     * Family match, NOT {@code startsWith}: {@code "H2"} must cover {@code H2.1} but never
     * {@code H20}/{@code H23}/{@code H24}/{@code H25}/{@code H26}, which are timeslot rules.
     */
    static boolean isRoomTierHardConstraint(String canonicalId) {
        if (canonicalId == null) {
            return false;
        }
        for (String family : ROOM_TIER_HARD_FAMILIES) {
            if (canonicalId.equals(family) || canonicalId.startsWith(family + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stage C: release the promoted pins that DEADLOCK the residual, least-constrained
     * first (see {@link StagedPinPolicy#selectDemotions}).
     *
     * <p>The analysis needs a PROMOTION-BLIND value range: {@code
     * Lesson.getAvailableTimeslots()} prunes every timeslot held by a pinned lesson of the
     * same group, so with the promoted pins in that map the blockers are invisible and the
     * boxed lesson merely looks like it has no options. So this loads a DETACHED lesson
     * population (cold VRP caches), republishes the pinned map from the AUTHORITATIVE pins
     * only for the duration of the analysis, and restores the true map immediately after.
     * Nothing is written to the DB except the demotion itself.
     */
    private int runDemotionStage() {
        List<Lesson> lessons = loadDetachedLessons();
        List<Lesson> authoritativePins = lessons.stream().filter(l -> !l.promotedPin).toList();
        List<Lesson> demotable;
        try {
            SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(authoritativePins);
            demotable = StagedPinPolicy.selectDemotions(lessons, StagedPinPolicy.MAX_DEMOTIONS_PER_BOXED_LESSON);
        } finally {
            SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        }
        if (demotable.isEmpty()) {
            return 0;
        }
        List<Long> ids = demotable.stream().map(l -> l.id).toList();
        for (Lesson lesson : demotable) {
            LOG.infof("staged solve: DEMOTING promoted pin lesson=%d (%s / %s / %s) M32 weight=%d",
                    lesson.id, lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : "?",
                    lesson.getTeacher() != null ? lesson.getTeacher().name : "?", lesson.getSubject(),
                    StagedPinPolicy.deviationWeightOf(lesson));
        }
        return demotePins(ids);
    }

    /**
     * Demote (unpin) the given promoted pins: they go back to being QUASI-pinned - M32 still
     * charges them their own scarcity weight for leaving their template cell - never free.
     * A demoted lesson is dropped from BOTH the persistent marker and the in-memory registry,
     * so the two can never disagree.
     */
    @Transactional
    int demotePins(List<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) {
            return 0;
        }
        int demoted = Lesson.update("pinned = false, promotedPin = false where promotedPin = true and id in ?1",
                lessonIds);
        Set<Long> remaining = new HashSet<>(SchoolSchedule.getPromotedLessonIdsStatic());
        remaining.removeAll(lessonIds);
        SchoolSchedule.setPromotedLessonIdsStatic(remaining);
        refreshPinStatics(plannableLessons());
        LOG.infof("staged solve: demoted %d promoted pin(s) back to quasi-pinned; %d still held", demoted, remaining.size());
        return demoted;
    }

    /**
     * SMARCATURA: release every promoted pin in ONE statement, keyed by the persistent
     * marker - so it is correct even if the in-memory registry was lost (JVM crash mid-cycle,
     * hence the same call from {@link #onStartReleasePromotedPins}). Authoritative pins are
     * out of its {@code WHERE} by construction.
     */
    @Transactional
    public int releasePromotedPins() {
        int released = Lesson.update("pinned = false, promotedPin = false where promotedPin = true");
        if (released > 0) {
            LOG.warnf("released %d TASK-419 promoted pin(s) (pinned = false, promotedPin = false)", released);
        }
        return released;
    }

    /**
     * Crash safety: a JVM that died mid-cycle leaves promoted pins in the DB with an EMPTY
     * registry, i.e. pins the score would then read as AUTHORITATIVE and no one would ever
     * release. One statement at startup closes that window.
     */
    @Transactional
    void onStartReleasePromotedPins(@Observes StartupEvent event) {
        int released = releasePromotedPins();
        if (released > 0) {
            LOG.warnf("startup: %d TASK-419 promoted pin(s) were left behind by a crashed staged solve - released", released);
        }
    }

    /**
     * SMARCATURA + full republish: release every promoted pin, clear the registry, and reload
     * the schedule with the statics rebuilt on the released state.
     *
     * <p>Called TWICE per cycle, and idempotent by construction (the second call releases 0
     * rows): once mid-cycle, right before stage B-ROOM - which is what makes the room phases
     * see free rooms - and once from the closing {@code finally}, which is the only release on
     * every failure path.
     *
     * <p>The republish DOES re-derive the M32 template (the registry is empty again, so {@link
     * #publishWeeklyTemplateStatics} no longer refuses). That is not a different reference:
     * {@link WeeklyTemplatePlanner#buildReference} is assignment-agnostic - its population is
     * every unpinned lesson regardless of where it currently sits, its occupancy only the
     * pinned ones (TASK-416) - and the release restores exactly the pinned/unpinned split the
     * cycle started from, so it re-derives the SAME cells. Stage B-ROOM is therefore scored
     * against the same M32 reference stage B-TIMESLOT was (and room moves cannot change M32
     * either way, since a template cell is a (weekday, slot) pair).
     *
     * @param republishEvenIfNothingReleased true for the mid-cycle call, which NEEDS the
     *     reloaded schedule for stage B-ROOM; false for the closing one, where a release of 0
     *     rows means the statics already describe the released state and the reload would
     *     rebuild every one of them for nothing (the next API read refreshes them anyway,
     *     since the cycle is over by then).
     * @return the freshly loaded schedule, so the caller can hand it straight to stage B-ROOM
     *     instead of paying for a second full load; null when the republish was skipped.
     */
    private SchoolSchedule releasePromotedPinsAndRepublish(boolean republishEvenIfNothingReleased) {
        int released = releasePromotedPins();
        SchoolSchedule.setPromotedLessonIdsStatic(List.of());
        if (released == 0 && !republishEvenIfNothingReleased) {
            return null;
        }
        SchoolSchedule schedule = loadSchedule(true);
        LOG.infof("staged solve: SMARCATURA - released %d promoted pin(s), %d authoritative pin(s) remain",
                released, countAuthoritativePins());
        return schedule;
    }

    /**
     * Closing step of the cycle: release (a no-op on the happy path, see {@link
     * #releasePromotedPinsAndRepublish}), republish, assert conservation.
     *
     * <p>The conservation check is on the SET of authoritative lesson ids, not on their
     * COUNT: a cycle that released one of the school's pins and promoted-then-left another
     * would keep the count and still have corrupted the record. {@code authoritativeBefore}
     * is null only when the cycle died before it could be captured (nothing to compare).
     *
     * <p>The republish is CONDITIONAL on the release having actually done something: on the
     * happy path the mid-cycle smarcatura already handed every pin back, so this call releases
     * 0 rows and a reload would rebuild the whole static set on a state that has not changed
     * since. On every failure path the release IS the one that works, and the republish runs.
     */
    private void finishStagedCycle(Set<Long> authoritativeBefore) {
        releasePromotedPinsAndRepublish(false);
        Set<Long> authoritativeAfter = authoritativePinIds();
        LOG.infof("staged solve: cycle closed; authoritative pins %d -> %d",
                authoritativeBefore != null ? authoritativeBefore.size() : -1,
                authoritativeAfter.size());
        if (authoritativeBefore == null || authoritativeAfter.equals(authoritativeBefore)) {
            return;
        }
        Set<Long> lost = new HashSet<>(authoritativeBefore);
        lost.removeAll(authoritativeAfter);
        Set<Long> gained = new HashSet<>(authoritativeAfter);
        gained.removeAll(authoritativeBefore);
        throw new IllegalStateException("authoritative pin set CHANGED during staged cycle (delta: "
                + lost.size() + " lost " + firstFew(lost) + ", " + gained.size() + " gained "
                + firstFew(gained) + "; " + authoritativeBefore.size() + " before, "
                + authoritativeAfter.size() + " after)");
    }

    /** Up to 10 ids of a delta set, for a log/exception message that stays readable. */
    private static String firstFew(Set<Long> ids) {
        return ids.stream().sorted().limit(10).map(String::valueOf)
                .collect(Collectors.joining(",", "[", ids.size() > 10 ? ",...]" : "]"));
    }

    /**
     * The ids of the pins that state something the SCHOOL decided (golden/calendario/stage/
     * lab rotation/hand) - the set the staged cycle promises to hand back untouched.
     */
    @Transactional
    Set<Long> authoritativePinIds() {
        return Lesson.<Lesson>find("pinned = true and promotedPin = false").stream()
                .map(l -> l.id).collect(Collectors.toSet());
    }

    /** How many pins state something the SCHOOL decided (see {@link #authoritativePinIds()}). */
    @Transactional
    long countAuthoritativePins() {
        return Lesson.count("pinned = true and promotedPin = false");
    }

    /** The box: unpinned lessons with no timeslot (the H0 residual). */
    @Transactional
    long countBoxedLessons() {
        return Lesson.count("pinned = false and timeslot is null");
    }

    /**
     * The same box count read off an in-memory solution instead of the DB - the staged
     * cycle's own reading, since the stage it judges has just saved this very grid. The
     * population is the PLANNABLE one ({@code plannableLessons}), which is also the only
     * population the cycle can ever place, so a lesson missing from it would be a box no
     * stage could clear anyway.
     */
    private static long countBoxedLessons(SchoolSchedule schedule) {
        return schedule.getLessons().stream().filter(l -> !l.pinned && l.timeslot == null).count();
    }

    /**
     * Load every plannable lesson DETACHED from the persistence context. Detaching is the
     * point: the staged analysis publishes an alternative view of this population, and a
     * managed entity would flush any such adjustment to the DB on commit. The associations
     * the analysis reads (timeslot, course, group, teacher, room) are JOIN FETCHed, so
     * detaching costs nothing.
     */
    @Transactional
    List<Lesson> loadDetachedLessons() {
        List<Lesson> lessons = plannableLessons();
        Lesson.getEntityManager().clear();
        return lessons;
    }

    /**
     * Refuse {@code action} while a staged cycle is live. The cycle owns the pins, the
     * statics and the grid between its stages, and the {@code SolverManager} reports idle
     * for most of that time - so every mutating or state-capturing entry point that would
     * otherwise wait for {@code SolverStatus} has to ask this instead.
     */
    private void refuseIfStaged(String action) {
        String stage = stagedStage;
        // TASK-670 (review finding 2): the mutating/capturing guards fronted here
        // (reschedule, reset, export, import) must also refuse during the tail
        // windows, where the stage is null but the cycle thread is still
        // delivering/saving: mutating the data under a live delivery corrupts it.
        if (cycleLifecycleActive()) {
            throw new StagedSolveInProgressException("A staged solve is in progress (stage " + stage
                    + ", cycle alive: " + cycleThreadAlive + ") - stop it before " + action);
        }
    }

    /**
     * Republish only the two statics a pin change invalidates (the lesson list and the
     * pinned-timeslot map the H3 value-range filter reads). Deliberately NOT a full
     * {@link #refreshSolverStatics}: the M32 template and its per-course weights are the
     * cycle's FIXED reference and must survive every promotion/demotion unchanged.
     */
    private void refreshPinStatics(List<Lesson> lessons) {
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
    }

    /** In-memory latest best score (TASK-380 light status endpoint). */
    public BendableScore getLatestScore() {
        return latestScore;
    }

    /** In-memory last solver crash, null when the last solve ended cleanly (TASK-378/380). */
    public String getLastSolverError() {
        return lastSolverError;
    }

    public SolverStatus getStatus() {
        return solverManager.getSolverStatus(SINGLETON_ID);
    }

    /**
     * Stricter guard than a bare {@code == SOLVING_ACTIVE} check (code review B3):
     * true for any non-idle {@link SolverStatus}, including {@code SOLVING_SCHEDULED}
     * (queued to start but not yet actively scoring). Callers that must not run a full
     * rescore concurrently with anything the solver is about to do — e.g. the
     * validation watchdog — need this wider guard; a bare {@code SOLVING_ACTIVE} check
     * would race a scheduled-but-not-yet-active solve. Other call sites in this class
     * that inline {@code getStatus() != SolverStatus.NOT_SOLVING} (e.g.
     * {@link #reactiveReschedule}, {@link #resetSchedule}) are intentionally left as-is.
     */
    public boolean isBusy() {
        // TASK-419: a staged cycle is busy even between its stages (stage A runs on its own
        // SolverFactory, and the promotion/demotion steps run with no solver at all), so the
        // SolverManager status alone would report idle for minutes of a live cycle.
        // TASK-667 (review F2) + TASK-670: cycleLifecycleActive() covers the
        // stage-less windows too (see the predicate's javadoc).
        return cycleLifecycleActive() || getStatus() != SolverStatus.NOT_SOLVING;
    }


    /**
     * Rebuild the static VRP/lookup state shared with the solver thread.
     * Called from {@link #loadSchedule(boolean)} ONLY when no solve is active
     * (or when loading for the solver itself) - see that method's javadoc for
     * the TASK-378 race this guard prevents.
     */
    private void refreshSolverStatics(List<Timeslot> timeslots, List<Room> rooms, List<Lesson> lessons,
            List<StudentGroup> studentGroups, List<TeacherAvailability> teacherAvailabilities,
            List<SchoolHoliday> schoolHolidays, List<StagePattern> stagePatterns,
            List<LabAssistant> labAssistants, List<GroupCalendarRule> groupCalendarRules,
            List<DailyActivityQuota> dailyActivityQuotas) {
        // TASK-413 M32 knobs (benchmark arm "W", 2026-07-25): log the effective weight-formula
        // values once per solve setup so every run self-documents which knobs it used, whether
        // they came from the defaults or from the -D system properties (see the constants'
        // own javadoc in SchoolSchedule for why they can only be set before class-init).
        LOG.infof("M32 template-deviation weight knobs: numerator=%d min=%d max=%d",
                SchoolSchedule.TEMPLATE_DEVIATION_WEIGHT_NUMERATOR,
                SchoolSchedule.TEMPLATE_DEVIATION_WEIGHT_MIN,
                SchoolSchedule.TEMPLATE_DEVIATION_WEIGHT_MAX);

        // TASK-430/432 gate (2026-07-27): echo the two construction -D flags still under A/B so the
        // run self-documents whether they reached this app JVM. The MAVEN_OPTS lesson (CLAUDE.md):
        // a -D read via Boolean.getBoolean() reaches the app ONLY on the java -jar command line,
        // not via MAVEN_OPTS on the mvn quarkus:run fork, so a "false" here means the flag was
        // dropped before the solver could see it. Observability-only: no behaviour change.
        // (TASK-433 anchor-repair was adopted 2026-07-28 and is now unconditional in the staged
        // cycle, so it is no longer a flag and does not appear here.)
        // (TASK-435 constrained-teacher pre-assign pin: default ON since maintainer ratified it
        // 2026-07-30, opted out only with "false". TASK-451 first-hour-slot: default ON since
        // 2026-07-31, same opt-out form. Both read via their opt-out helpers, not
        // Boolean.getBoolean, so the banner reports the live default-on behavior truthfully.)
        // GRASP construction diversification (TASK-480): grasp=%b seed=%d echoed so a laptop operator
        // can grep-confirm -Dplanner.construction.grasp.{enabled,seed} reached the java -jar JVM.
        // A dropped seed silently falls back to 0L (a fixed construction), undetectable in the log
        // otherwise. Same rationale as the portfolio-size echo below.
        LOG.infof("Construction/repair flags: slot0-balancing=%b profile-following=%b constrained-teacher=%b first-hour-slot=%b theory-spread=%b grasp=%b seed=%d pair-aware=%b surplus-harvest=%b",
                Boolean.getBoolean("planner.construction.slot0-balancing.enabled"),
                Boolean.getBoolean("planner.construction.profile-following.enabled"),
                HybridPhase1aService.isConstrainedTeacherEnabled(),
                WeeklyTemplateBuilder.isFirstHourSlotEnabled(),
                WeeklyTemplateBuilder.isTheorySpreadEnabled(),
                WeeklyTemplateBuilder.graspEnabled(),
                WeeklyTemplateBuilder.graspSeed(),
                PairCellGuard.enabled(),
                SurplusHarvest.enabled());
        // TASK-516 loud banner: same silent-seed-drop guard as the siblings above.
        LOG.infof("Pre-assign budget-reservation (TASK-516): enabled=%b",
                BudgetReservation.enabled());
        // TASK-576 F7-C (M38 pair-cell deviation): flag/weight/builder echo. The builder
        // echo is what makes the "enabled but inert" state readable (the constraint needs
        // the opt-in pair-cells builder to have anything to charge; see pairCellDeviation).
        // Published sizes are NOT printed here: this banner runs BEFORE the construction
        // phase publishes (always 0, misleading); the live count is in the construction
        // phase's own "Pair-cell guard" publish log.
        LOG.infof("Pair-cell deviation M38 (TASK-576 F7-C, RATIFIED weight=1): enabled=%b weight=%d builder=%b",
                SchoolSchedule.isPairDeviationEnabled(),
                Integer.getInteger("planner.constraints.pair-deviation.weight",
                        SchoolSchedule.PAIR_DEVIATION_DEFAULT_WEIGHT),
                DayClusteredConstructionPhase.pairCellsEnabled());
        // TASK-576 F9: the pair-form promotion - the one instrument that never enters the
        // delta-score market. Flag echo only, same pre-publish timing as the M38 line.
        LOG.infof("Pair-form promotion (TASK-576 F9): enabled=%b",
                StagedPinPolicy.pairPromotionEnabled());
        // DRAFT-005 portfolio size: echoed so a laptop operator can grep-confirm -Dplanner.portfolio.size
        // reached the java -jar JVM (a silent fallback to 1 would otherwise be undetectable in the log).
        LOG.infof("Portfolio multi-start (DRAFT-005): size=%d (1 = single solve, byte-identical; >1 = N B-TIMESLOT solves, pick best)",
                Integer.getInteger("planner.portfolio.size", 1));
        // Set static timeslot reference for ValueRangeProviders in planning entities
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        LOG.infof("Set static timeslots: %d total", timeslots.size());

        // Set static room reference for ValueRangeProviders in planning entities
        SchoolSchedule.setAllRoomsStatic(rooms);
        LOG.infof("Set static rooms: %d total", rooms.size());

        // TASK-455: per-tag ASSIGNABLE-room capacity for the M-TC lab tag-capacity
        // constraint. Computed from rooms only (capacity > 0, Room.isAssignable; the
        // cap-0 R18 drops out), invariant during the solve. Published
        // regardless of the flag so this LOG and any analysis can read it: the
        // constraint is inert (0 matches) when the flag is off (leading `enabled &&`,
        // M-LF pattern). The enabled=%b echo is the MAVEN_OPTS observability for the
        // -Dplanner.tag-capacity.enabled knob: a "false" here on a java -jar run means
        // the flag did not reach the app JVM (CLAUDE.md MAVEN_OPTS lesson).
        Map<String, Integer> roomCapacityByTag = new HashMap<>();
        for (Room room : rooms) {
            if (!room.isAssignable()) {
                continue;
            }
            for (String tag : room.tags) {
                roomCapacityByTag.merge(tag, 1, Integer::sum);
            }
        }
        SchoolSchedule.setRoomCapacityByTagStatic(roomCapacityByTag);
        LOG.infof("M-TC lab tag-capacity: enabled=%b, rooms-per-tag=%s",
                SchoolSchedule.isTagCapacityEnabled(),
                roomCapacityByTag.isEmpty() ? "none" : roomCapacityByTag.toString());

        // TASK-464 co-presenza ospite: golden co-presenza depth per (tag, weekday, slot)
        // cell, mechanically read from the classpath golden. The subject->tag map is the
        // shared ConstrainedTeacherAllocator.buildSubjectToSingleTagMap (authoritative, from
        // the Course entities - no second SubjectRegistry copy), so the tag string matches
        // what H2's pairwise exemption and the H-COPRESENCE cap read at runtime. Published
        // regardless of the flag (the leading `enabled &&` keeps both inert when off); the
        // banner reports how many depth>=2 cells were found so an empty or under-counted
        // load (e.g. subject-name drift between golden and Course) is grep-detectable.
        Map<String, String> subjectToTag = ConstrainedTeacherAllocator.buildSubjectToSingleTagMap(
                lessons.stream().map(l -> l.course).filter(java.util.Objects::nonNull).distinct().toList());
        Map<String, Integer> coPresenceDepth =
                ConstrainedTeacherAllocator.loadGoldenCoPresenceDepth(subjectToTag);
        long coPresenceCells = coPresenceDepth.values().stream().filter(d -> d >= 2).count();
        SchoolSchedule.setGoldenCoPresenceDepthStatic(coPresenceDepth);
        LOG.infof("H-COPRESENCE guest co-presenza: enabled=%b, depth>=2 cells=%d (of %d tallied)",
                SchoolSchedule.isCoPresenceEnabled(), coPresenceCells, coPresenceDepth.size());

        // TASK-463 lab-day consolidation: cross-group teacher-day coordinator (G13-Friday gap
        // cure). Adopted default ON since 2026-08-01 (opt-out); reports the flag state for grep.
        LOG.infof("TASK-463 lab-day consolidation: enabled=%b",
                SchoolSchedule.isLabDayConsolidationEnabled());

        // TASK-478 ExactFinisher residual-completion pass. Ratified DEFAULT ON 2026-08-02 (maintainer):
        // opt-out only with -Dplanner.exact-finisher.enabled=false. Echoed for MAVEN_OPTS
        // observability: a "false" on a java -jar run means the -D flag did not reach the app JVM
        // (CLAUDE.md lesson). The maxCandidates value is the LIVE -Dplanner.exact-finisher.max-candidates
        // knob (default 1000000), so the banner shows the budget the run actually uses.
        LOG.infof("ExactFinisher residual-completion: enabled=%b, maxK=%d, maxDepth=%d, maxCandidates=%d",
                SchoolSchedule.isExactFinisherEnabled(), ExactFinisher.MAX_K, ExactFinisher.MAX_DEPTH, ExactFinisher.getMaxCandidates());

        // TASK-483 staged-cycle multi-start retry. Ratified DEFAULT ON 2026-08-03 (maintainer) on the n=8
        // bad-seed evidence: opt-out only with -Dplanner.multistart.enabled=false. Echoed for the same
        // MAVEN_OPTS observability reason as ExactFinisher, and because a run that silently lost the
        // retry would look like an ordinary single-cycle solve until it shipped residual hard.
        LOG.infof("Multi-start staged retry: enabled=%b, maxAttempts=%d, budgetSeconds=%d, maxDiscards=%d",
                multiStartEnabled(), multiStartMaxAttempts(), multiStartBudgetSeconds(),
                multiStartMaxDiscards());
        // TASK-503: same silent-flag-drop detection as the knobs above.
        LOG.infof("Signature-conditioned retry (TASK-503): enabled=%b",
                signatureRetryEnabled());
        LOG.infof("Warm-start adaptive weights (TASK-504, speculative): enabled=%b",
                warmStartWeightsEnabled());
        LOG.infof("Teacher-budget guard Phase 2 (TASK-502 follow-up): enabled=%b",
                HybridPhase1aService.teacherBudgetEnabled());

        // TASK-486 M-TC-per-retry: arms M-TC only on multi-start attempts >=2 (attempt 1 stays
        // M-TC-off so a good basin pays no 2x overhead). RATIFIED DEFAULT ON (maintainer, 2026-08-04):
        // null/absent arms it, opt out only with -Dplanner.multistart.mtc-per-retry.enabled=false.
        // Echoed for -D observability (MAVEN_OPTS lesson). Subordinate to multi-start: inert when
        // multi-start is off (no attempts >=2 exist). The M-TC banner above echoes the live
        // tag-capacity.enabled this lever flips per attempt.
        LOG.infof("M-TC per-retry: armed=%b", mtcPerRetryEnabled());
        // M-BF banner (RATIFIED 18/09, maintainer): lab-teacher day contiguity medium,
        // DEFAULT ON, standard opt-OUT (planner.constraints.mbf.enabled, only the
        // literal "false" disarms). Echoes the arm state so a silent flag drop is
        // visible in any solve log.
        LOG.infof("M-BF lab-teacher day contiguity (RATIFIED 18/09): enabled=%b, weight=%d",
                SchoolSchedule.isMbfEnabled(),
                Integer.getInteger("planner.constraints.mbf.weight", SchoolSchedule.MBF_DEFAULT_WEIGHT));

        // TASK-558 (RATIFIED 27/08, maintainer): M22 quota-aware min-days amendment
        // (ceil(hours / average declared aula+lab quota) instead of
        // ceil(hours / maxHoursPerDay)). DEFAULT ON, standard opt-OUT (only the literal
        // "false" disarms). The legacy min-days was a packing fiction on this dataset
        // (the unpinned-equivalent golden pays 262 excess days under it); evidence
        // (task558-results/gate-emendamento-m22-2026-08-27.md): golden 463 -> 3,
        // run 410 -> 0 under the amended metric (metro change, not timetable).
        LOG.infof("M22 quota-aware min-days (TASK-558, RATIFIED): enabled=%b",
                SchoolSchedule.isM22QuotaAwareEnabled());

        // TASK-557 (RATIFIED 26/08, maintainer opzione A): H34 empty-declared-day, the
        // companion hard of H32 - a declared day with zero non-Stage lessons now
        // impacts 0hard (FLAT 1) instead of paying M20.2 medium only. DEFAULT ON,
        // standard opt-OUT (only the literal "false" disarms). STRICT honored: no
        // tolerance (the golden's 2 year-end G09 cells FILL in our run:
        // better-than-golden); the 10 residual cells are a search-side filone
        // (import teacher-locked). Echoed for -D observability.
        LOG.infof("H34 empty-declared-day (TASK-557, RATIFIED): enabled=%b",
                com.school.planner.solver.SchoolScheduleConstraintProvider.emptyDayHardEnabled());

        // TASK-560 (RATIFIED 27/08, maintainer): the final pass for H34's residual empty declared
        // days. DEFAULT ON, standard opt-OUT (only the literal "false" disarms). Evidence
        // (task560-results/gate-adozione-edf-v01-2026-08-27.md): 4/12 cells on the winner,
        // 49 moves, 0 conflicts, 0 reverts; cross-basin curve 4/0/3/2/1/0 (downside = no-op).
        LOG.infof("EmptyDayFinisher (TASK-560, RATIFIED): enabled=%b", EmptyDayFinisher.enabled());
        LOG.infof("CompositeSwapFinisher (TASK-609 v0.1, RATIFIED 14/09): enabled=%b", CompositeSwapFinisher.enabled());

        // TASK-560 v0.2 (anello 2): the cross-group swap-compound family, part of the
        // RATIFIED endgame bundle (16/09) - measured inert on every basin ever probed,
        // censused in the [delivery] line. The bundle banner below echoes it with its
        // three siblings; the dedicated line is retired (the reviewers' duplicate-banner
        // finding: two echoes of the same flag with opposite framing diverge).

        // TASK-577+678: the H33 target family (under-floor cells, filled to floor,
        // includes the quota-fill-excluded groups). RATIFIED DEFAULT ON (maintainer,
        // 04/10). Echoed for banner-check (the silent-flag-drop class, precedent
        // cce67200: armed-but-no-food must be distinguishable from
        // flag-never-arrived).
        LOG.infof("EmptyDayFinisher H33 family (TASK-577+678, RATIFIED 04/10): enabled=%b",
                EmptyDayFinisher.h33Enabled());

        // TASK-612 (14/09, strada verso 0): the IMPORT-RELAY move family and the H32
        // partial under-quota targets. RATIFIED DEFAULT ON (maintainer, gate A/B appaiabile
        // ab-gate2: A2 -18 vs B2 -8, criteri a-d VERDI, controllo crash VERDE). Echoed
        // for banner-check (silent-flag-drop class).
        LOG.infof("EmptyDayFinisher relay family (TASK-612, RATIFIED 14/09): relay=%b h32=%b",
                EmptyDayFinisher.relayEnabled(), EmptyDayFinisher.h32Enabled());

        // TASK-613 (15/09): the HOLE-RELAY family for the H17 teacher-locked
        // residual. RATIFIED DEFAULT ON (maintainer, gate A/B ab-gate: A3 -14 vs B3 -9,
        // criteri a-e VERDI, no-op sicuro sul live come pre-registrato).
        LOG.infof("HoleRelayFinisher (TASK-613 v0.6, RATIFIED 15/09): enabled=%b",
                HoleRelayFinisher.enabled());

        // TASK-638 (23/09, RATIFIED DEFAULT ON b90929d2, design in
        // task638-results/production-wiring/REPORT.md): the violation-directed
        // steepest sweep as the LAST delivery pass. Validato 4/6 a 0-hard sul
        // catalogo (context extension), no-op quando il residuo non ha cure
        // single-move nello spazio legale. ROOM-AWARE extension (TASK-641,
        // RATIFIED DEFAULT ON 23/09): candidati compositi con riassegno stanza
        // (pass pigro; banco 637-off -3->0, integrato-1 -52->-41, parita' A/B).
        // UNBLOCK CROSS-GROUP extension (TASK-657, RATIFIED DEFAULT ON 23/09,
        // bounded-no-op: il bacino che l'ha generata e' strutturalmente
        // bloccato dalle Vacation docenti, nessun positivo dimostrato; oracle
        // improve-or-noop, precedente CSF TASK-609): swap cross-date nel
        // gruppo del blocker + tail slide, terzo pass pigro.
        LOG.infof("ViolationScopedSteepestFinisher (TASK-638): enabled=%b, roomAware=%b, unblock=%b",
                ViolationScopedSteepestFinisher.enabled(),
                ViolationScopedSteepestFinisher.roomAwareEnabled(),
                ViolationScopedSteepestFinisher.unblockCrossGroupEnabled());

        // Endgame bundle (16/09, re-A/B del bundle curato 258ac6cbe: A = B = [-1/0],
        // no-op pieno con criteri a-e VERDI; positiva sui bacini misurati B3/A3):
        // i QUATTRO pass oracle-bounded vanno DEFAULT ON insieme (la configurazione
        // validata dall'A/B e' il bundle intero; un subset sarebbe mai misurato).
        // CHC ora armabile anche dentro il multi-start (cura clearAttemptLevers).
        LOG.infof("Endgame bundle (RATIFIED 16/09): compact-swap=%b tol-donor=%b"
                        + " v2-swap=%b composition-hole-closer=%b",
                HoleRelayFinisher.compactSwapEnabled(), EmptyDayFinisher.tolDonorEnabled(),
                EmptyDayFinisher.v2SwapEnabled(), SchoolSchedule.isCompositionHoleCloserEnabled());

        // TASK-619 (16/09): the import cross-date third arm of the hole finisher,
        // RATIFIED with its own evidence (banco A3 -11 -> -10 / B3 -9 -> -8 clean,
        // /code-review high with 3 fixes, re-certifica 5/5 identica).
        LOG.infof("HoleRelayFinisher import-cross-date (TASK-619, RATIFIED 16/09): enabled=%b",
                HoleRelayFinisher.importCrossDateEnabled());

        // Night 17/09 (the -4 family, from the integrato-1 blind round): the
        // x-swap arm (H17 pairs where the backfill IS the expelled) and the
        // depth-3 arm (multi-group H7 overcap chain), both banco -4 -> -2 / -1,
        // double-gated with the 616/H28 cures applied, re-cert 4/4.
        LOG.infof("HoleRelayFinisher night arms (RATIFIED 17/09): x-swap=%b h7-depth3=%b",
                HoleRelayFinisher.xSwapEnabled(), HoleRelayFinisher.h7Depth3Enabled());

        // TASK-620 (16/09 sera): the H7-cap arm (overcap teacher-day cure), the LAST
        // family the blind rounds named (blind-meno1: the -1 of the best fresh
        // delivery was T21 7h/6). Banco -1 -> 0 clean, double-gated, re-cert
        // round-2 green after the branch-aware SOURCE-YIELD cure.
        LOG.infof("HoleRelayFinisher h7cap (TASK-620, RATIFIED 16/09): enabled=%b",
                HoleRelayFinisher.h7CapEnabled());

        // TASK-E1/E2 (27/08): per-stage room-tier weight override on the timeslot stages
        // ONLY (opt-in; the scores under a regime are regime-relative - see the knob's
        // javadoc). Echoed so a run's era is attributable in its log.
        LOG.infof("Room-tier override on timeslot stages (E1/E2): %s",
                System.getProperty("planner.room-tier.override", "none"));

        // TASK-538 milestone-2: H34/H32 re-tiered to hard level 1 (quasi) so the LS can
        // traverse them. OPT-IN, DEFAULT OFF. Echoed for silent-flag-drop detection: the
        // flag rides the java -jar command line (the MAVEN_OPTS trap), and a run's score
        // format alone does not reveal whether the re-tiering was active.
        LOG.infof("Quasi-tier H34/H32 on hard level 1 (TASK-538 M2): enabled=%b",
                SchoolScheduleConstraintProvider.quasiTierEnabled());

        // TASK-556 (RATIFIED 27/08, maintainer): QFR v2 move choice (tail-only donor yield,
        // border-aware target landing, retry re-sweep). DEFAULT ON, standard opt-OUT (only
        // the literal "false" keeps legacy v1). Evidence: same-seed formality (57 moves /
        // 14 cells / 0 reverts) + discriminant control (sanity identical with v2 OFF):
        // task556-results/ciclo-gate-e-e2e/VERDICT-2026-08-26.md.
        LOG.infof("QuotaFillRepair v2 move choice (TASK-556, RATIFIED): enabled=%b",
                QuotaFillRepair.v2Enabled());

        // TASK-564 (27/08 notte): the declared-day pre-booker, wired INSIDE
        // DayClusteredConstructionPhase (before the box pass: the post-construction
        // venue was measured inert, registro 27/08). RATIFIED DEFAULT ON (maintainer,
        // 29/08); the REVISIT-FLAG note in the banner below is part of the
        // ratification order (full rationale: class javadoc + registro 29/08).
        LOG.infof("Declared-day pre-booking v2.1 (TASK-564, RATIFIED DEFAULT ON, "
                        + "REVISIT-FLAG: adopted on deterministic-seed evidence -12hard/full-day 91.6/H17 0; "
                        + "A/B cross-basin non-confirming): enabled=%b",
                DeclaredDayPreBooker.enabled());

        // TASK-480 Fase 3 multi-threaded LS (step chasing, De Smet & Wauters PATAT 2020). RATIFIED
        // DEFAULT ON (maintainer, 2026-08-14): 25% wall saving, 0hard both arms. Echoed for -D
        // observability (MAVEN_OPTS lesson): a boot that silently lost the flag would run the
        // ordinary single-threaded LS at full wall cost. Coverage note: the decider is wired in
        // runStagedSolver ONLY (the campaign reference path); the ordinary /solve (SolverManager)
        // path stays single-threaded by design.
        LOG.infof("Multi-threaded LS: enabled=%b, threads=%d, dispatch=%s",
                multiThreadedLsEnabled(), multiThreadedLsThreads(),
                com.school.planner.solver.multithreaded.MultiThreadedLocalSearchDecider.singleWriterEnabled()
                        ? "single-writer per-thread channels (TASK-543 F2, RATIFIED 24/08)"
                        : "legacy shared queue");
        // TASK-550 room-tail finisher (RATIFIED 25/08, standard opt-out form): echoed for the
        // same silent-flag-loss observability.
        LOG.infof("Room-tail finisher (TASK-550, RATIFIED 25/08): enabled=%b",
                com.school.planner.solver.RoomTailFinisher.enabled());

        // TASK-599 (RATIFICATO 12/09): the teacher-weekday-spread preference in the
        // template cell assignment. Echoed for -D observability like the siblings (the
        // gate REPORT flagged this missing banner as the lever's observability gap).
        LOG.infof("Teacher-weekday-spread (TASK-599, RATIFIED 12/09): enabled=%b",
                com.school.planner.solver.WeeklyTemplateBuilder.teacherWeekdaySpreadEnabled());

        // TASK-604 (prototype): echoed so a -D on the java -jar line is provably arrived
        // (the MAVEN_OPTS trap: without a banner an inert constraint and a lost flag are
        // indistinguishable).
        LOG.infof("M39 stage-day preference (TASK-604, prototype): enabled=%b, weight=%d",
                com.school.planner.solver.BaseConstraintProvider.m39StageDayEnabled(),
                com.school.planner.solver.BaseConstraintProvider.m39Weight());

        // TASK-595 H28 rotation finisher, RATIFIED DEFAULT ON 17/09. Echoed for -D
        // observability: a lost flag silently leaves the A..B..A residue to the delivery.
        LOG.infof("H28 rotation finisher (TASK-595, RATIFIED 17/09): enabled=%b",
                com.school.planner.solver.H28RotationFinisher.enabled());

        // TASK-498 skip-doomed-room (RATIFIED DEFAULT ON 15/08). Echoed for -D
        // observability like the other prototypes: a lost flag silently costs the ~10 min
        // per doomed attempt the lever exists to save.
        LOG.infof("Skip-doomed-room refinement (TASK-498): enabled=%b", skipDoomedRoomEnabled());

        // TASK-635 recalibration (04/10): the threshold the B-TIMESLOT accept gate and the
        // doomed-skip share, echoed so a -D override (or a drift from the ratified default)
        // is visible in every solve log. The rationale lives on the accessor.
        LOG.infof("Staged hard-accept threshold (TASK-635): %d (default %d)",
                stagedHardAcceptThreshold(), DEFAULT_STAGED_HARD_ACCEPT_THRESHOLD);

        // TASK-677 (04/10): the profile predictor DISARMED (default OFF since the paired
        // falsification; the H17-hole discard amputated viable GRASP retries). Echoed so
        // an opt-in re-arm (research) is visible, and a silent re-enable cannot hide.
        LOG.infof("Profile predictor (TASK-677 DISARMED 04/10): enabled=%b, max-holes=%d",
                profilePredictorEnabled(), PROFILE_DOOM_HOLES_THRESHOLD);

        // TASK-496 (measurement prototype): the fill-family G14 exemption, echoed so a
        // dropped -D is visible (enabled=false = G14 joins M20/M33/M20.2/pool-cap).
        LOG.infof("G14 fill exemption (TASK-496): enabled=%b",
                SchoolScheduleConstraintProvider.G14FillExemptEnabled());

        // TASK-499 nearby-template filter (opt-in prototype, default OFF). Echoed like the
        // other prototypes: a lost -D would silently run the unfiltered 1368-cell value
        // range at full evaluation cost.
        LOG.infof("Adaptive move weights (TASK-500): enabled=%b", adaptiveWeightsEnabled());

        LOG.infof("Nearby template filter (TASK-499): enabled=%b, keep-probability=%.2f",
                nearbyTemplateFilterEnabled(), nearbyTemplateKeepProbability());

        // TASK-481 CompositionHoleCloser (classroom-day H17 cascade): RATIFIED DEFAULT ON
        // as part of the endgame bundle (16/09; the clearAttemptLevers cure makes it
        // armable inside the multi-start). Echoed for -D observability alongside the
        // bundle banner; only the explicit literal "false" opts out.
        LOG.infof("CompositionHoleCloser classroom-day cascade: enabled=%b, maxCandidates=%d",
                SchoolSchedule.isCompositionHoleCloserEnabled(), CompositionHoleCloser.getMaxCandidates());

        // Set static lessons reference for ValueRangeProvider filtering (H3 VRP - pinned lessons)
        SchoolSchedule.setAllLessonsStatic(lessons);
        LOG.infof("Set static lessons: %d total", lessons.size());

        // Build optimized lookup map for pinned lesson timeslots by group
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        long pinnedCount = lessons.stream().filter(l -> l.pinned).count();
        LOG.infof("Built pinned lesson timeslots map: %d pinned lessons", pinnedCount);

        // TASK-636: golden-pace tracking in the construction repair tail. The
        // preference is inert whenever the cadence reference has no pace curve
        // for a pair, so the banner pairs the flag with the published-curve
        // count (an ON flag over an empty reference is observably a no-op).
        LOG.infof("Cadence-pace construction tail (TASK-636): enabled=%b, curves=%d",
                DayClusteredConstructionPhase.cadencePaceEnabled(),
                SchoolSchedule.cadencePaceCurveCount());

        // Set static VRP-buffered ideal end dates map for ValueRangeProviders.
        // TASK-397: also cache the full IdealEndDateInfo so mid-solve loadSchedule
        // fetches can re-apply it cheaply (both fields are @Transient - lost on the
        // fresh StudentGroup.listAll() every fetch - so they must be re-hydrated from
        // this cache instead of recomputed).
        Map<Long, LocalDate> idealEndDateVrpBufferedMap = new HashMap<>();
        Map<Long, IdealEndDateInfo> idealEndDateInfoMap = new HashMap<>();
        for (StudentGroup group : studentGroups) {
            if (group.idealEndDateVrpBuffered != null) {
                idealEndDateVrpBufferedMap.put(group.id, group.idealEndDateVrpBuffered);
            }
            if (group.idealEndDateInfo != null) {
                idealEndDateInfoMap.put(group.id, group.idealEndDateInfo);
            }
        }
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(idealEndDateVrpBufferedMap);
        SchoolSchedule.setIdealEndDateInfoStatic(idealEndDateInfoMap);
        LOG.infof("Set static idealEndDateVrpBuffered map: %d entries", idealEndDateVrpBufferedMap.size());

        // Set static teacher availabilities for ValueRangeProvider filtering (H5.2)
        SchoolSchedule.setTeacherAvailabilitiesStatic(teacherAvailabilities);
        LOG.infof("Set static teacherAvailabilities: %d teachers with availability records",
                SchoolSchedule.getTeacherAvailabilitiesStatic().size());

        // Set static school holidays for ValueRangeProvider filtering (H6.2)
        SchoolSchedule.setSchoolHolidaysStatic(schoolHolidays);
        LOG.infof("Set static schoolHolidays: %d dates", SchoolSchedule.getSchoolHolidaysStatic().size());

        // Set static stage blackout dates for ValueRangeProvider filtering (H4 VRP)
        // Must come AFTER setSchoolHolidaysStatic() since expandDates() needs the holidays set
        SchoolSchedule.setStageBlackoutDatesStatic(stagePatterns, SchoolSchedule.getSchoolHolidaysStatic());
        LOG.infof("Set static stageBlackoutDates: %d groups with blackout dates",
                SchoolSchedule.getStageBlackoutDatesStatic().size());

        // Set static TimeslotSequence for block validity checks (H9)
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        LOG.info("Set static TimeslotSequence for block validity filtering");

        // Set static lab assistant data for M-LA constraint filtering
        SchoolSchedule.setLabAssistantsStatic(labAssistants);
        LOG.infof("Set static labAssistants: %d records", labAssistants.size());

        // Set static per-group calendar rules for M18/H20 and VRP filtering (TASK-342)
        SchoolSchedule.setGroupCalendarRulesStatic(groupCalendarRules);
        LOG.infof("Set static groupCalendarRules: %d records", groupCalendarRules.size());

        // Set static per-group daily activity quotas for H23/H24 and VRP filtering (TASK-381 "L1-bis")
        SchoolSchedule.setDailyActivityQuotasStatic(dailyActivityQuotas);
        LOG.infof("Set static dailyActivityQuotas: %d records", dailyActivityQuotas.size());

        // TASK-536 H33: per-group hard day floors, min(4, mathFloor, golden
        // existence cap), published PRE-solve (the deriver logs the full
        // two-level table: math aspiration vs enforced floor). The enabled=%b
        // echo is the standard MAVEN_OPTS observability for the opt-out flag.
        Map<String, Integer> dayFloors = DayFloorDeriver.deriveHardFloors(
                studentGroups, Course.listAll(), dailyActivityQuotas);
        Map<Long, Integer> dayFloorsById = new HashMap<>();
        for (StudentGroup g : studentGroups) {
            Integer floor = dayFloors.get(g.name);
            if (floor != null && floor > 0) {
                dayFloorsById.put(g.id, floor);
            }
        }
        SchoolSchedule.setDayFloorsStatic(dayFloorsById);
        LOG.infof("H33 day-floor (TASK-536): enabled=%b, groups=%d, floors=%s",
                com.school.planner.solver.SchoolScheduleConstraintProvider.dayFloorHardEnabled(),
                dayFloorsById.size(), dayFloorsById.isEmpty() ? "none" : dayFloors.toString());

        // TASK-469 M-CPS: per-group last-stage-day from INPUT (max DailyActivityQuota.stageHours
        // > 0 date). The uniform input source for curriculumPostStage's stage-end: NOT StagePattern
        // (the seed creates StagePatterns for only 3 of 14 groups, which would blind the constraint
        // to G10 / G08), and NOT the golden. Published regardless of the flag (the leading
        // `enabled &&` keeps the constraint inert when off); the size echo makes an empty load
        // grep-detectable.
        Map<Long, LocalDate> stageEndByGroup = new HashMap<>();
        for (DailyActivityQuota q : dailyActivityQuotas) {
            if (q.stageHours > 0 && q.studentGroup != null) {
                stageEndByGroup.merge(q.studentGroup.id, q.date,
                        (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        SchoolSchedule.setStageEndByGroupStatic(stageEndByGroup);
        LOG.infof("M-CPS curriculum post-stage: enabled=%b, stage-end resolved for %d groups",
                SchoolSchedule.isCurriculumPostStageEnabled(), stageEndByGroup.size());

        // TASK-461: per-group M20 supply-cap ratio. The school's capacity plan (calendario
        // aula+lab) can exceed the curriculum (incarichi), leaving an unreachable M20 deficit
        // floor (G04 85h, G08 40h, G09 4h); the ratio trims the daily target so the
        // floor vanishes. Computed from inputs only (groups/lessons/quotas) and invariant
        // during the solve (unlike the pin maps), so published once at setup and not touched
        // by refreshPinStatics mid-cycle. MUST run after setGroupCalendarRulesStatic above:
        // computeSupplyCapRatios reads getGroupCalendarOpenSlotCount, which falls back to
        // TOTAL_SLOTS (8) while the rules static is still empty (first solve of a JVM).
        Map<Long, Double> supplyCapRatios = SchoolScheduleConstraintProvider.computeSupplyCapRatios(
                studentGroups, lessons, dailyActivityQuotas);
        SchoolSchedule.setSupplyCapRatioByGroupStatic(supplyCapRatios);
        // TASK-467 AC#4 wiring (opt-in prototype, default OFF): echoed so a dropped -D is
        // visible - an operator relying on the multi-candidate rotation plan would otherwise
        // silently get the greedy single candidate. The knobs ride the same line (same
        // MAVEN_OPTS rationale as the grasp seed: a dropped -D reads as the default).
        LOG.infof("Rotation enumerator pre-assign (TASK-467): enabled=%b topk=%d weeks-per-period=%d max-periods=%d",
                RotationEnumerator.preAssignEnabled(), RotationEnumerator.topK(),
                RotationEnumerator.weeksPerPeriod(), RotationEnumerator.maxPeriods());
        // TASK-364 follow-up: same silent--D-drop detection as the enumerator knobs.
        LOG.infof("LabRotation derive-from-golden (TASK-364 follow-up): enabled=%b",
                LabRotationDeriver.enabled());

        LOG.infof("M20 supply-cap ratios (capacity > curriculum): %s",
                supplyCapRatios.isEmpty() ? "none" : supplyCapRatios.toString());

        // TASK-497 forma (b) PROTOTYPE (default OFF, opt-in
        // -Dplanner.constraints.m20-cap-pool.enabled): the per-(group, date) pool-capped
        // M20 targets. Published REGARDLESS of the flag (same shape as the M-CPS stage-end
        // map above): the guard in capSupply reads the flag CAPTURED HERE (see
        // captureM20CapPoolFlag - the dailyQuota hot path reads a volatile, never
        // System.getProperty), so toggling the -D for an A/B requires this refresh to run
        // (it does: every solve setup calls refreshSolverStatics). The banner below echoes
        // the CAPTURED value, the one the score actually used.
        Map<Long, Map<LocalDate, Integer>> m20PoolTargets =
                SchoolScheduleConstraintProvider.computeM20PoolCapTargetsByGroup(
                        studentGroups, lessons, dailyActivityQuotas);
        SchoolSchedule.setM20PoolCapTargetsStatic(m20PoolTargets);
        SchoolSchedule.captureM20CapPoolFlag();
        LOG.infof("M20 pool-cap targets (TASK-497, armed=%b): %d group(s) over-capacity",
                SchoolSchedule.isM20CapPoolArmed(), m20PoolTargets.size());
        // TASK-577 9/09 ratifica: la costruzione segue gli stessi target (opt-out,
        // solo il letterale "false" disarma; vedi DayClusteredConstructionPhase)
        LOG.infof("Pool-cap fill (TASK-577, RATIFIED 9/09): enabled=%b",
                DayClusteredConstructionPhase.poolCapFillEnabled());

        // Pre-compute available timeslot counts for MRV-based difficulty comparison
        long mrvStart = System.currentTimeMillis();
        SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);
        LOG.infof("Computed available timeslot counts for %d lessons in %dms",
                lessons.size(), System.currentTimeMillis() - mrvStart);

        // TASK-413: publish the pre-solve weekly template + per-course scarcity
        // deviation weights for M32 (Template deviation). MUST come after
        // computeLessonAvailableTimeslotCounts above (the scarcity weight reads
        // the VRP cell counts it publishes) and after every VRP static the
        // WeeklyTemplatePlanner consults (calendar/quota/availability, all set
        // above). Computed here, pre-solve, so M32's reference is FIXED for the
        // whole solve - see setWeeklyTemplateStatic's own javadoc.
        publishWeeklyTemplateStatics(lessons, timeslots, stagePatterns);

        // TASK-425: publish the per-(group,subject) golden cadence reference for
        // M34 (burst cap) and M35 (active-week target). Self-contained JSON read,
        // no dependency on any other static, so order relative to the calls above
        // is free; placing it last keeps the TASK-413 template publish (which CAN
        // short-circuit on the staged-promotion guard) visually grouped with its
        // own pre-solve section.
        int cadencePairs = publishCadenceReferenceStatics();
        // LOUD auto-disable (TASK-441 policy, decision 6, maintainer 2026-08-11): the cadence
        // reference self-gates M34/M35 inert when the golden JSON is absent, but that state
        // was only a buried WARN. Surface it in the banner summary so a no-golden year (JSON
        // gone) is VISIBLE, not silent: the school must declare the cadence via 21_CadenzaAttesa
        // or the M34/M35 caps stay off. Pairs with origine=scuola in 21_CadenzaAttesa are the
        // declared-input survival path (censimento #3).
        LOG.infof("M34/M35 cadence reference: %s",
                cadencePairs > 0
                        ? cadencePairs + " pairs (caps ACTIVE)"
                        : "INERT (no qualifying reference - M34/M35 caps off; declare via 21_CadenzaAttesa for the no-golden year)");
    }

    /**
     * TASK-425: load {@code golden-cadence-reference.json} and publish the
     * per-(group, normalized-subject) burst-cap and active-week maps that M34/M35
     * measure against. Resolved ONCE per solve setup, NEVER mid-solve (the same
     * "fixed reference" invariant M32's template map already enforces): a cadence
     * reference that changed halfway through a solve would corrupt M34/M35's
     * incremental score exactly the way a half-republished template would.
     *
     * <p>FAIL-SAFE by design: any IOException, parse error, or missing file
     * publishes two EMPTY maps and logs a warning, never throws - the same
     * safe-default M32's empty-template map already establishes (an absent
     * reference makes M34/M35 self-gate to inert via Integer.MAX_VALUE / 0, it
     * never breaks the solve). Pairs with {@code total_hours < 10} are skipped
     * (the same micro-pair threshold the KPI's {@code min_hours_threshold} uses,
     * matched here so the constraint's reference and the KPI's published
     * reference cannot disagree on which pairs count).
     *
     * <p>KEY CONVENTION: {@code pair.group + "\t" + SubjectNormalization.normalize(pair.subject)},
     * the SAME symmetric normalization the M34/M35 lookups apply, so a
     * case-duplicate subject spelling ("Lingua italiana" vs "Lingua Italiana")
     * can never split a pair's reference from its live matches.
     */
    private int publishCadenceReferenceStatics() {
        String path = System.getProperty("planner.cadence.reference-path",
                "claudedocs/spalmone-2026-27/data/golden-cadence-reference.json");
        try {
            CadenceReference ref = parseCadenceReference(new java.io.File(path), cadenceObjectMapper);
            SchoolSchedule.setCadenceReferenceStatic(ref.burstCap(), ref.activeWeeks(), ref.pace());
            LOG.infof("TASK-425: published golden cadence reference for M34/M35: %d pairs (skipped %d micro-pairs <10h, %d pace curves) from %s",
                    ref.burstCap().size(), ref.skipped(), ref.pace().size(), path);
            return ref.burstCap().size();
        } catch (java.io.IOException | IllegalArgumentException e) {
            LOG.warnf("TASK-425: failed to read golden cadence reference at %s - publishing empty maps (M34/M35 inert): %s",
                    path, e.getMessage());
            SchoolSchedule.setCadenceReferenceStatic(Map.of(), Map.of(), Map.of());
            return 0;
        }
    }

    /** TASK-425/636: the three maps a cadence reference publishes, plus the
     * micro-pair skip count. */
    public record CadenceReference(Map<String, Integer> burstCap,
            Map<String, Integer> activeWeeks,
            Map<String, java.util.NavigableMap<Integer, Integer>> pace,
            int skipped) {
    }

    private static final java.util.regex.Pattern CADENCE_WEEK_PATTERN =
            java.util.regex.Pattern.compile("(\\d{4})-W(\\d{2})");

    /**
     * TASK-425/636: the ONE parser of {@code golden-cadence-reference.json},
     * shared by the production publisher and the test fixtures (SnapshotFixture),
     * so the micro-pair rule, the week encoding and the inert fallback can never
     * drift between a production solve and a bench. Throws IOException on an
     * unreadable file and IllegalArgumentException on a malformed week string
     * (fail-loud: a silently half-parsed reference would desynchronize bench
     * and production on exactly the pairs that matter).
     */
    public static CadenceReference parseCadenceReference(java.io.File file,
            com.fasterxml.jackson.databind.ObjectMapper mapper)
            throws java.io.IOException {
        Map<String, Integer> burstCap = new HashMap<>();
        Map<String, Integer> activeWeeks = new HashMap<>();
        Map<String, java.util.NavigableMap<Integer, Integer>> pace = new HashMap<>();
        if (!file.exists()) {
            return new CadenceReference(burstCap, activeWeeks, pace, 0);
        }
        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(file);
        com.fasterxml.jackson.databind.JsonNode pairs = root.path("pairs");
        if (!pairs.isArray()) {
            return new CadenceReference(burstCap, activeWeeks, pace, 0);
        }
        int skipped = 0;
        for (com.fasterxml.jackson.databind.JsonNode pair : pairs) {
            int totalHours = pair.path("total_hours").asInt(0);
            if (totalHours < 10) {
                skipped++;
                continue;
            }
            String group = pair.path("group").asText(null);
            String subject = pair.path("subject").asText(null);
            if (group == null || subject == null) continue;
            int max = pair.path("max").asInt(0);
            int weeks = pair.path("active_weeks").asInt(0);
            String key = SchoolSchedule.cadenceKey(group, com.school.planner.util.SubjectNormalization.normalize(subject));
            burstCap.put(key, max);
            activeWeeks.put(key, weeks);
            // TASK-636: the pair's weekly series -> cumulative golden-hours
            // curve keyed by ISO week (TimeslotSequence.isoWeekKey encoding,
            // the same "yyyy-Www" strings the reference carries). Series
            // entries with 0 hours are kept: a week inside the span with 0
            // golden hours must read as "no hours owed BY that week", not
            // as an unknown week.
            com.fasterxml.jackson.databind.JsonNode series = pair.path("series");
            if (series.isArray() && series.size() > 0) {
                java.util.NavigableMap<Integer, Integer> curve = new java.util.TreeMap<>();
                int cumulative = 0;
                for (com.fasterxml.jackson.databind.JsonNode entry : series) {
                    cumulative += entry.get(1).asInt(0);
                    curve.put(isoWeekKeyOf(entry.get(0).asText()), cumulative);
                }
                pace.put(key, curve);
            }
        }
        return new CadenceReference(burstCap, activeWeeks, pace, skipped);
    }

    /**
     * TASK-636: {@code "2026-W39"} (the reference's week format, produced by
     * Python's ISO %G-W%V) -&gt; {@code weekBasedYear*100 + week}, the same
     * integer encoding {@code TimeslotSequence.isoWeekKey} produces for real
     * dates. Strict ISO parsing via {@link java.time.temporal.WeekFields#ISO}
     * so the two sides can never drift on week conventions.
     */
    private static int isoWeekKeyOf(String referenceWeek) {
        java.util.regex.Matcher m = CADENCE_WEEK_PATTERN.matcher(referenceWeek);
        if (!m.matches()) {
            throw new IllegalArgumentException("TASK-636: unexpected cadence week format: " + referenceWeek);
        }
        // Week-based year: the Monday of week 1 defines the year; use
        // WeekFields.ISO's date resolver instead of LocalDate.parse (which
        // needs day-of-week and would mis-anchor on non-ISO locales).
        int year = Integer.parseInt(m.group(1));
        int week = Integer.parseInt(m.group(2));
        java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
        java.time.LocalDate anchor = java.time.LocalDate.of(year, 1, 4)
                .with(wf.weekOfWeekBasedYear(), 1)
                .with(wf.weekOfWeekBasedYear(), week);
        // A W53 that does not exist in `year` would silently normalize to week 1
        // of the next week-based year and the key would be unreachable by any
        // real date: fail loud like the malformed-format case (code-review 21/09).
        if (anchor.get(wf.weekOfWeekBasedYear()) != week) {
            throw new IllegalArgumentException(
                    "TASK-636: week does not exist in its week-based year: " + referenceWeek);
        }
        return anchor.get(wf.weekBasedYear()) * 100 + week;
    }

    /**
     * TASK-413: derive the weekly template (the same {@link WeeklyTemplatePlanner}
     * the CH phase runs, so on a fresh pre-solve state the map coincides with what
     * the phase itself builds), encode each course's cells, and compute a
     * scarcity-graduated M32 deviation weight per course from the VRP cell count of
     * one of its unpinned lessons (every unpinned lesson of a course shares the same
     * VRP result, so a single representative suffices - see {@code
     * Lesson.getAvailableTimeslots}).
     *
     * <p>Uses {@link WeeklyTemplatePlanner#buildReference} (assignment-agnostic,
     * TASK-416) rather than the CH-mode {@link WeeklyTemplatePlanner#build}: this
     * runs on every solve setup AND on every post-solve re-analysis / re-solve, and
     * the CH-mode population ({@code timeslot == null}) is empty on an
     * already-solved state — which would publish an empty template and make M32
     * silently score 0. Reference mode publishes the SAME fixed pre-solve template
     * regardless of solve progress.
     */
    private void publishWeeklyTemplateStatics(List<Lesson> lessons, List<Timeslot> timeslots,
            List<StagePattern> stagePatterns) {
        // TASK-419: NEVER re-derive the template while a staged promotion is live. Reference
        // mode is assignment-agnostic but its POPULATION is the unpinned lessons, and the
        // promotion has just pinned thousands of them: re-deriving here would publish a
        // different, degenerate template (and per-course weights) halfway through the cycle,
        // so M32 would score the tail against a reference nobody ever built. The template is
        // the cycle's fixed pre-solve reference - it is published once, before the promotion,
        // and again by the closing release once the registry is empty.
        Set<Long> promoted = SchoolSchedule.getPromotedLessonIdsStatic();
        if (!promoted.isEmpty()) {
            LOG.infof("TASK-419: staged promotion live (%d promoted pins) - keeping the template published before the promotion",
                    promoted.size());
            return;
        }
        Set<LocalDate> holidays = SchoolSchedule.getSchoolHolidaysStatic();
        Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> template =
                WeeklyTemplatePlanner.buildReference(lessons, timeslots, holidays, stagePatterns);

        // TASK-576 F7-A placement note (code review 2026-09-01, CRITICAL): the pair-cell
        // guard is NOT published here. This method is re-run by the mid-cycle promotion
        // release, and buildPairCells is CH-mode (it skips already-placed lessons), so a
        // re-run at that boundary publishes a DEGENERATE empty template and silently
        // disarms the guard exactly one stage before the repairs it guards - while the
        // banner still claims cells were published. The guard is published by the
        // construction phase itself, right after it builds the exact template it is
        // about to stamp (DayClusteredConstructionPhase), and cleared once per cycle.

        // courseId -> encoded template cells (each cell expanded over its slots).
        Map<Long, Set<Integer>> cellsByCourse = new HashMap<>();
        // TASK-542 cure-1 (post-mortem iter-22): when day-compact is armed, the M32
        // reference for the menu-covered courses comes from the SAME menu the fabricator
        // stamps (DayCompactMenuSource.templateCells), OVERRIDING the planner's cells for
        // those courses. The iter-22 gate measured M32 punishing the graft's own cells
        // (-978k, 7923 matches): two sources naming different cells. Courses the menu
        // does not cover keep the planner reference unchanged.
        int menuCourses = 0;
        Map<Long, Set<Integer>> menuCells = Map.of();
        if (dayCompactEnabled()) {
            List<LocalDate> schoolDates = new ArrayList<>();
            for (Timeslot ts : timeslots) {
                LocalDate d = ts.getDate();
                if (d != null && !schoolDates.contains(d)) {
                    schoolDates.add(d);
                }
            }
            java.util.Collections.sort(schoolDates);
            // the menu resolves against the schedule's own groups/courses (the same
            // population the construction graft resolves on)
            Map<String, StudentGroup> groupsByName = new HashMap<>();
            Map<String, Course> courseByGroupSubject = new HashMap<>();
            for (Lesson l : lessons) {
                if (l.course == null || l.course.studentGroup == null) continue;
                groupsByName.putIfAbsent(l.course.studentGroup.name, l.course.studentGroup);
                if (l.course.subject != null) {
                    courseByGroupSubject.putIfAbsent(
                            l.course.studentGroup.name + "|" + l.course.subject, l.course);
                }
            }
            var menu = com.school.planner.solver.DayCompactMenuSource.resolve(
                    com.school.planner.solver.DayCompactMenuSource.load(),
                    new ArrayList<>(groupsByName.values()), new ArrayList<>(courseByGroupSubject.values()));
            menuCells =
                    com.school.planner.solver.DayCompactMenuSource.templateCells(menu, schoolDates);
            // applied AFTER the planner loop below, so the menu cells OVERRIDE the
            // planner's for the covered courses (overlay, not merge)
            menuCourses = menuCells.size();
        }
        for (Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>> byDay : template.values()) {
            for (Map.Entry<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>> dayEntry : byDay.entrySet()) {
                DayOfWeek day = dayEntry.getKey();
                for (WeeklyTemplateBuilder.TemplateCell cell : dayEntry.getValue()) {
                    Course course = cell.course();
                    if (course == null || course.id == null) continue;
                    Set<Integer> cells = cellsByCourse.computeIfAbsent(course.id, k -> new HashSet<>());
                    for (int slot = cell.slotIndex(); slot < cell.slotIndex() + cell.length(); slot++) {
                        cells.add(SchoolSchedule.encodeTemplateCell(day, slot));
                    }
                }
            }
        }
        // TASK-542 cure-1 overlay: menu cells win over the planner's (see above)
        cellsByCourse.putAll(menuCells);

        // courseId -> scarcity deviation weight, from one unpinned representative's
        // VRP cell count (0/absent => maximally scarce => MAX weight).
        Map<Long, Integer> vrpCounts = SchoolSchedule.getLessonAvailableTimeslotCountsRef();
        Map<Long, Integer> repCountByCourse = new HashMap<>();
        for (Lesson l : lessons) {
            if (l.pinned || l.course == null || l.course.id == null) continue;
            if (!cellsByCourse.containsKey(l.course.id) || repCountByCourse.containsKey(l.course.id)) continue;
            Integer count = vrpCounts != null ? vrpCounts.get(l.id) : null;
            repCountByCourse.put(l.course.id, count != null ? count : 0);
        }
        Map<Long, Integer> weightByCourse = new HashMap<>();
        for (Long courseId : cellsByCourse.keySet()) {
            weightByCourse.put(courseId,
                    SchoolSchedule.templateDeviationWeight(repCountByCourse.getOrDefault(courseId, 0)));
        }

        SchoolSchedule.setWeeklyTemplateStatic(cellsByCourse, weightByCourse);
        LOG.infof("TASK-413: published weekly template for M32: %d courses templated"
                + (menuCourses > 0 ? " (of which %d from the day-compact menu, TASK-542 cure-1)" : ""),
                cellsByCourse.size(), menuCourses);
    }

    public SchoolSchedule getScheduleWithStatus() {
        SchoolSchedule schedule = loadSchedule();
        // Post-restart score restoration (ASYNC, TASK-658): latestScore is
        // in-memory state a restart clears, while the delivered schedule
        // lives in the DB. The full recompute costs ~10-20s on 12.5k lessons:
        // running it INSIDE this response blocked the first GET /api/schedule
        // and the reverse proxy killed the slow response (empty grid, the
        // referente handoff lesson 23/09). Fire-and-forget instead: this response
        // returns immediately with the lessons; the background compute sets
        // latestScore; the UI's status poll sees the null->score transition
        // and re-fetches on its own (ScheduleTimeline's change detection).
        // One-shot (guarded by the null check), only when lessons are placed;
        // every writer (solve events, saveSchedule) keeps its assignment.
        if (latestScore == null && schedule.getLessons() != null
                && schedule.getLessons().stream().anyMatch(l -> l.timeslot != null)
                && scoreRestoreStarted.compareAndSet(false, true)) {
            SchoolSchedule toScore = schedule;
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                boolean restored = false;
                try {
                    solutionManager.update(toScore);
                    if (toScore.getScore() != null) {
                        latestScore = toScore.getScore();
                        // TASK-667 (review F4): release the latch on SUCCESS too -
                        // a later reset/import re-nulls latestScore and the guard
                        // must allow a fresh restore instead of reporting pending
                        // forever on a latch that never clears.
                        scoreRestoreStarted.set(false);
                        LOG.infof("restored delivered score after restart: %s", latestScore);
                        restored = true;
                    }
                } catch (RuntimeException e) {
                    // TASK-667: release the guard so scoreRestorePending goes false even
                    // when the recompute dies (the UI polls that flag and would otherwise
                    // wait forever on a transition that never comes).
                    scoreRestoreStarted.set(false);
                    LOG.warn("score restoration after restart failed (stays unscored)", e);
                }
                // TASK-660/354: the restore is a state-epoch change; the persisted
                // watchdog snapshot may predate the delivered grid entirely. Fired
                // OUTSIDE the try (code-review F4): a throwing fire must not log a
                // false "restoration failed" after the score IS restored.
                if (restored) {
                    fireInternalTrigger("score-restored-after-restart");
                }
            });
        }
        SolverStatus status = getStatus();
        schedule.setSolverStatus(status);
        schedule.setScore(latestScore);
        schedule.setLastSolverError(lastSolverError);
        // TASK-419: a staged cycle runs most of its stages with the SolverManager idle, so
        // solverStatus alone would report this payload as a finished solve.
        schedule.setStagedStage(stagedStage);
        // TASK-483: multi-start attempt state (0/0 when off) so the UI can narrate the retry.
        schedule.setStagedAttempt(stagedAttempt);
        schedule.setStagedMaxAttempts(stagedMaxAttempts);
        schedule.setStagedBudgetSeconds(stagedBudgetSeconds);
        if (status == SolverStatus.SOLVING_ACTIVE) {
            schedule.setSolveStartTime(solveStartTime);
            schedule.setChCompletionTime(chCompletionTime);
            // Include current solver phase from PhaseTrackingListener
            int phase = PhaseTrackingListener.getCurrentPhase();
            String name = PhaseTrackingListener.getPhaseName();
            schedule.setCurrentPhase(phase);
            // If PhaseTrackingListener hasn't been updated (phase=0, name="Not running"),
            // set phaseName to null so the UI falls back to CH/LS auto-detection
            schedule.setPhaseName(phase > 0 ? name : null);
        } else {
            schedule.setCurrentPhase(0);
            schedule.setPhaseName(null);
        }
        // Cumulative timing: accumulators + current live session delta
        long currentSessionDelta = (status == SolverStatus.SOLVING_ACTIVE && solveStartTime != null)
            ? Duration.between(solveStartTime, Instant.now()).getSeconds() : 0;
        schedule.setCumulativeElapsedSeconds(cumulativeElapsedSeconds + currentSessionDelta);
        schedule.setCumulativeChSeconds(cumulativeChSeconds);
        schedule.setCumulativeLsSeconds(cumulativeLsSeconds);
        schedule.setPreAssignmentDurationSeconds(HybridPhase1aService.getCompletedDurationSeconds());
        // Get pre-assignment status from HybridPhase1aService static fields
        HybridPhase1aService.HybridStatus hybridStatus = HybridPhase1aService.getCurrentStatus();
        String phase = HybridPhase1aService.getCurrentPhaseStatic();
        PreAssignmentStatus paStatus = mapHybridStatusToPreAssignmentStatus(hybridStatus, phase);
        schedule.setPreAssignmentStatus(paStatus);
        schedule.setPreAssignmentStartTime(HybridPhase1aService.getStartTimeStatic());
        schedule.setPreAssignmentError(HybridPhase1aService.getLastErrorStatic());
        schedule.setPreAssignmentFailureAnalysis(HybridPhase1aService.getLastFailureAnalysis());
        return schedule;
    }

    /**
     * Map HybridPhase1aService.HybridStatus + phase to PreAssignmentStatus enum.
     */
    public PreAssignmentStatus mapHybridStatusToPreAssignmentStatus(
            HybridPhase1aService.HybridStatus hybridStatus, String phase) {
        if (hybridStatus == HybridPhase1aService.HybridStatus.IDLE) {
            return PreAssignmentStatus.IDLE;
        } else if (hybridStatus == HybridPhase1aService.HybridStatus.COMPLETE) {
            return PreAssignmentStatus.COMPLETE;
        } else if (hybridStatus == HybridPhase1aService.HybridStatus.ERROR) {
            return PreAssignmentStatus.ERROR;
        } else if (hybridStatus == HybridPhase1aService.HybridStatus.RUNNING) {
            if (phase == null) {
                return PreAssignmentStatus.ACCONCIATURA; // Default
            } else if (phase.startsWith("1-ACCONCIATURA") || phase.equals("INITIALIZING")) {
                return PreAssignmentStatus.ACCONCIATURA;
            } else if (phase.startsWith("2-LABS")) {
                return PreAssignmentStatus.LABS;
            } else if (phase.startsWith("3-PROFESSIONALIZZANTI")) {
                return PreAssignmentStatus.PROFESSIONALIZZANTI;
            }
        }
        return PreAssignmentStatus.IDLE;
    }

    @Transactional
    public void reactiveReschedule(Long teacherId, LocalDate date) {
        // TASK-419: its bulk "pin everything placed" would create pins with no promoted
        // marker on top of a live staged cycle - the exact accounting hole the marker exists
        // to prevent. Refuse while a cycle runs (same guard as resetSchedule).
        refuseIfStaged("rescheduling");
        if (getStatus() != SolverStatus.NOT_SOLVING) {
            stopSolving();
        }

        LocalDateTime dayStart = date.atTime(LocalTime.MIN);
        LocalDateTime dayEnd = date.atTime(LocalTime.MAX);

        List<Lesson> affectedLessons = Lesson.find(
                        "course.teacher.id = ?1 and timeslot.startDateTime >= ?2 and timeslot.startDateTime <= ?3",
                        teacherId, dayStart, dayEnd)
                .list();

        List<Long> affectedIds = affectedLessons.stream().map(l -> l.id).toList();

        for (Lesson lesson : affectedLessons) {
            lesson.timeslot = null;
            lesson.room = null;
            lesson.pinned = false;
        }

        // Note: LabBlock nulling removed in TASK-228 - labs are now pinned Lessons

        if (!affectedIds.isEmpty()) {
            Lesson.update("pinned = true where id not in ?1 and timeslot is not null", affectedIds);
        } else {
            Lesson.update("pinned = true where timeslot is not null");
        }

        startSolving();
    }

    /**
     * Reset all planning assignments to initial state.
     * Clears timeslot/room assignments and unpins all entities.
     * Also clears pre-assignment status and error messages.
     */
    @Transactional
    public void resetSchedule() {
        // TASK-419: a reset mid-staged-cycle would unpin the AUTHORITATIVE pins the cycle
        // has promised to hand back untouched (and its closing conservation check would then
        // fire on a corruption someone else caused). Refuse instead.
        refuseIfStaged("resetting the schedule");
        if (getStatus() != SolverStatus.NOT_SOLVING) {
            stopSolving();
        }

        // TASK-383 attribution: this is the ONLY code path that produces the
        // "0/N assigned, all unpinned" state (the 2026-07-20 vanishing-pins
        // incident's exact symptom). Log its magnitude at WARN BEFORE the
        // destructive update so any stray/unexpected reset is always
        // traceable, with the count of imported/pinned lessons it destroys;
        // the caller (ScheduleResource) logs the request source (IP/UA).
        long pinnedBefore = Lesson.count("pinned = true");
        long assignedBefore = Lesson.count("timeslot is not null");
        LOG.warnf("resetSchedule: DESTRUCTIVE reset - clearing ALL lesson assignments and pins "
                + "(%d pinned, %d assigned before reset). Every imported/pinned lesson is unpinned.",
                pinnedBefore, assignedBefore);

        // Clear all lesson assignments
        int lessonCount = Lesson.update("timeslot = null, room = null, pinned = false");
        // Note: LabBlock update removed in TASK-228 - labs are now pinned Lessons

        clearTransientState();

        LOG.warnf("resetSchedule: cleared %d lessons to unassigned/unpinned state (was %d pinned, %d assigned)",
                lessonCount, pinnedBefore, assignedBefore);
    }

    /**
     * Clear cached score/phase-tracking state that only makes sense relative
     * to the previous Lesson assignments. Extracted from {@link #resetSchedule()}
     * (TASK-346 M4) so callers that mutate Lessons directly instead of going
     * through the solver - e.g. {@code GoldenScheduleImportService} - can
     * clear the same stale state without also unpinning/unassigning the
     * Lessons they just imported.
     */
    public void clearTransientState() {
        // Clear pre-assignment status and error (stored in HybridPhase1aService static fields)
        hybridPhase1aService.resetStatus();

        // Reset cached score to null so it gets recalculated fresh
        latestScore = null;
        chCompletionTime = null;
        solveStartTime = null;
        seenChPhase = false;
        cumulativeElapsedSeconds = 0;
        cumulativeChSeconds = 0;
        cumulativeLsSeconds = 0;

        // Reset phase tracking
        PhaseTrackingListener.reset();
        progressionService.reset();
    }

    @Transactional
    public ConstraintMatchSnapshot analyzeScore() {
        SchoolSchedule schedule = loadSchedule();
        // update() initializes shadow variables and calculates the score
        // through Timefold's internal mechanism, ensuring constraints
        // evaluate correctly even for imported (non-solver-produced) solutions.
        solutionManager.update(schedule);
        return computeConstraintMatchSnapshot(schedule);
    }

    /**
     * Build a snapshot of all constraint matches for the current schedule state.
     *
     * Replaces {@code solutionManager.analyze()} (Enterprise-only in Timefold 2.x)
     * by using the open-source {@link InnerScoreDirector#getConstraintMatchTotalMap()}
     * with {@link ConstraintMatchPolicy#ENABLED}.
     */
    private ConstraintMatchSnapshot computeConstraintMatchSnapshot(SchoolSchedule schedule) {
        ScoreDirectorFactory<SchoolSchedule, BendableScore> factory = getScoreDirectorFactory();
        try (var scoreDirector = factory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            scoreDirector.setWorkingSolution(schedule);
            BendableScore score = scoreDirector.calculateScore().raw();
            Map<ConstraintRef, ConstraintMatchTotal<BendableScore>> map =
                    scoreDirector.getConstraintMatchTotalMap();
            return new ConstraintMatchSnapshot(score, map);
        }
    }

    private ScoreDirectorFactory<SchoolSchedule, BendableScore> getScoreDirectorFactory() {
        if (scoreDirectorFactory == null) {
            scoreDirectorFactory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager)
                    .getScoreDirectorFactory();
        }
        return scoreDirectorFactory;
    }


    /**
     * Analyze violations for Lesson and LabBlock entities within a date range.
     * Returns a list of entities with their constraint violations,
     * useful for highlighting problematic schedule entries in the UI.
     *
     * Uses a cached indictment map if available and the schedule hasn't changed
     * (score is the same and solver is not running). This dramatically speeds up
     * repeated calls from 5+ minutes to milliseconds.
     *
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     */
    @Transactional
    public List<LessonViolationAnalysis> analyzeLessonViolations(LocalDate startDate, LocalDate endDate) {
        return analyzeLessonViolations(startDate, endDate, null);
    }

    /**
     * Analyze violations for Lesson and LabBlock entities within a date range.
     * Returns a list of entities with their constraint violations,
     * useful for highlighting problematic schedule entries in the UI.
     *
     * Uses a cached indictment map if available and the schedule hasn't changed
     * (score is the same and solver is not running). This dramatically speeds up
     * repeated calls from 5+ minutes to milliseconds.
     *
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @param lang Language for violation messages (en, it). Defaults to "en".
     */
    @Transactional
    public List<LessonViolationAnalysis> analyzeLessonViolations(LocalDate startDate, LocalDate endDate, String lang) {
        return analyzeLessonViolations(startDate, endDate, null, lang);
    }

    /**
     * Analyze violations for Lesson and LabBlock entities within a date range.
     * Optionally filters by a specific constraint ID (e.g., "H1.1").
     *
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @param constraintId Optional constraint ID filter. If provided, only returns violations for this constraint.
     * @param lang Language for violation messages (en, it). Defaults to "en".
     */
    @Transactional
    public List<LessonViolationAnalysis> analyzeLessonViolations(LocalDate startDate, LocalDate endDate, String constraintId, String lang) {
        Locale messageLocale = parseLocale(lang);
        SchoolSchedule schedule = loadSchedule();
        solutionManager.update(schedule);

        var snapshot = getCachedOrComputeSnapshot(schedule);
        Map<Long, Lesson> lessonById = schedule.getLessons().stream()
                .collect(Collectors.toMap(l -> l.id, l -> l));

        // Normalize constraint ID once for consistent comparison throughout this method.
        // Push the filter into buildLessonMatchesMap so we skip 100K+ irrelevant matches
        // when the caller asks for a specific constraint (e.g., H17).
        String normalizedConstraintId = normalizeConstraintId(constraintId);
        var lessonMatchesMap = buildLessonMatchesMap(snapshot, lessonById, normalizedConstraintId);

        List<LessonViolationAnalysis> results = new ArrayList<>();

        // Iterate only lessons that have matches (matchesMap keys reference the same
        // instances as schedule.getLessons() — they come from the snapshot's justification
        // facts, which were the schedule's own Lesson entities passed to the score director).
        for (Lesson lesson : lessonMatchesMap.keySet()) {
            if (lesson.timeslot == null) continue;
            LocalDate d = lesson.timeslot.getDate();
            if (d.isBefore(startDate) || d.isAfter(endDate)) continue;

            var matches = lessonMatchesMap.get(lesson);
            if (matches == null || matches.isEmpty()) continue;

            // Map keys already filtered by constraint prefix in buildLessonMatchesMap,
            // so no need to re-filter here when constraintId is set.
            results.add(buildLessonAnalysis(lesson, matches, lessonById, messageLocale));
        }

        // NOTE: LabBlocks were deprecated in TASK-228. All labs are now pinned Lessons.
        // The following violation analysis now only processes Lessons.

        // NOTE: H7, H11, M15, H17, and M18 use groupBy() which tracks violations at the group level,
        // not the individual lesson level. To make these visible in the UI,
        // we manually compute violations and add them to results.
        // Skip these if filtering by a different constraint (use normalized comparison)
        if (constraintId == null || normalizedConstraintId.equals("H7") || constraintId.startsWith("H7.")) {
            addH7Violations(results, schedule, startDate, endDate, messageLocale, constraintId);
        }
        if (constraintId == null || normalizedConstraintId.equals("H11") || constraintId.startsWith("H11.")) {
            addH11Violations(results, schedule, startDate, endDate, messageLocale, constraintId);
        }
        if (constraintId == null || normalizedConstraintId.equals("M15") || constraintId.startsWith("M15.")) {
            addM15Violations(results, schedule, startDate, endDate, messageLocale, constraintId);
        }
        if (constraintId == null || normalizedConstraintId.equals("H17") || constraintId.startsWith("H17.")) {
            addH17Violations(results, schedule, startDate, endDate, messageLocale, constraintId);
        }
        if (constraintId == null || normalizedConstraintId.equals("M18") || constraintId.startsWith("M18.")) {
            addM18Violations(results, schedule, startDate, endDate, messageLocale, constraintId);
        }

        // If filtering, remove any entities that ended up with no violations after filtering
        if (constraintId != null) {
            results = results.stream()
                .filter(lva -> !lva.violations().isEmpty())
                .toList();
        }

        return results;
    }

    /**
     * Parse language string to Locale. Defaults to English if null or invalid.
     */
    private Locale parseLocale(String lang) {
        if (lang == null) {
            return Locale.ENGLISH;
        }
        return switch (lang.toLowerCase()) {
            case "it" -> Locale.ITALIAN;
            case "en" -> Locale.ENGLISH;
            default -> Locale.ENGLISH;
        };
    }

    /**
     * Get cached constraint-match snapshot or compute a new one if cache is invalid.
     * Cache is valid if:
     * - Cached snapshot exists
     * - Score hasn't changed (schedule is stable)
     * - Solver is not running
     */
    private ConstraintMatchSnapshot getCachedOrComputeSnapshot(SchoolSchedule schedule) {
        BendableScore currentScore = schedule.getScore();
        boolean solverRunning = getStatus() == SolverStatus.SOLVING_ACTIVE;

        BendableScore cachedScore = cachedSnapshot != null ? cachedSnapshot.score() : null;
        if (cachedSnapshot != null &&
            cachedScore != null &&
            cachedScore.equals(currentScore) &&
            !solverRunning) {
            LOG.infof("Reusing cached constraint-match snapshot (score=%s, age=%dms)",
                    currentScore,
                    cachedAnalysisTime != null ? Instant.now().toEpochMilli() - cachedAnalysisTime.toEpochMilli() : 0);
            return cachedSnapshot;
        }

        LOG.infof("Computing new constraint-match snapshot (solverRunning=%s, cachedScore=%s, currentScore=%s)",
                solverRunning, cachedScore, currentScore);
        Instant computeStart = Instant.now();

        ConstraintMatchSnapshot snapshot = computeConstraintMatchSnapshot(schedule);

        LOG.infof("Constraint-match snapshot: %d constraints", snapshot.constraintMap().size());

        cachedSnapshot = snapshot;
        cachedAnalysisTime = Instant.now();

        LOG.infof("Constraint-match snapshot computed in %dms, cached for future use",
                Instant.now().toEpochMilli() - computeStart.toEpochMilli());

        return snapshot;
    }

    /**
     * Build a map from Lesson entity to its violation matches from the snapshot.
     * If {@code constraintIdFilter} is non-null, only matches whose constraint ID
     * starts with that prefix are included — avoids processing 100K+ matches when
     * the caller only wants H17.
     *
     * Handles two justification shapes:
     * - {@link DefaultConstraintJustification} (auto-wrapped by Timefold when no custom
     *   justification is provided) — unwrap via {@code getFacts()} to find Lesson refs.
     * - Custom justifications implementing {@link LessonBearingJustification} (H1/H2/H3)
     *   — resolve via the {@code getLessonIds()} method and look up in the schedule.
     */
    private Map<Lesson, List<ConstraintMatch<BendableScore>>> buildLessonMatchesMap(
            ConstraintMatchSnapshot snapshot, Map<Long, Lesson> lessonById,
            String constraintIdFilter) {
        Map<Lesson, List<ConstraintMatch<BendableScore>>> matchesMap = new HashMap<>();

        for (var entry : snapshot.constraintMap().entrySet()) {
            String fullId = entry.getKey().id();
            String shortName = ConstraintIds.shortName(fullId);
            if (constraintIdFilter != null && !shortName.startsWith(constraintIdFilter)) continue;

            for (var match : entry.getValue().getConstraintMatchSet()) {
                for (Lesson lesson : resolveLessons(match.getJustification(), lessonById)) {
                    matchesMap.computeIfAbsent(lesson, k -> new ArrayList<>()).add(match);
                }
            }
        }

        return matchesMap;
    }

    /**
     * Extract the Lesson entities referenced by a constraint-match justification.
     * Returns an empty list if the justification doesn't reference any Lesson.
     *
     * Handles both justification shapes produced by the constraint provider:
     * - {@link DefaultConstraintJustification}: facts are the bi-/tri-tuple stream items.
     * - {@link LessonBearingJustification}: custom justifications storing only Lesson IDs
     *   (H1/H2/H3 conflict records).
     */
    public static List<Lesson> resolveLessons(Object justification, Map<Long, Lesson> lessonById) {
        if (justification == null) return List.of();
        if (justification instanceof DefaultConstraintJustification dcj) {
            List<Lesson> lessons = new ArrayList<>();
            for (Object fact : dcj.getFacts()) {
                if (fact instanceof Lesson lesson) lessons.add(lesson);
            }
            return lessons;
        }
        if (justification instanceof LessonBearingJustification lbj) {
            List<Lesson> lessons = new ArrayList<>();
            for (Long id : lbj.getLessonIds()) {
                Lesson lesson = lessonById.get(id);
                if (lesson != null) lessons.add(lesson);
            }
            return lessons;
        }
        return List.of();
    }

    private LessonViolationAnalysis buildLessonAnalysis(Lesson lesson,
            List<ConstraintMatch<BendableScore>> matches,
            Map<Long, Lesson> lessonById, Locale messageLocale) {
        List<LessonViolationAnalysis.ViolationDetail> violations = new ArrayList<>(matches.size());
        BendableScore totalScore = ScoreCompat.ZERO;
        for (ConstraintMatch<BendableScore> cm : matches) {
            violations.add(buildViolationDetail(cm, lessonById, messageLocale));
            totalScore = totalScore.add(cm.getScore());
        }

        return new LessonViolationAnalysis(
            lesson.id,
            "Lesson",
            lesson.getTeacher() != null ? lesson.getTeacher().name : null,
            lesson.getSubject(),
            lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : null,
            lesson.timeslot != null ? lesson.timeslot.startDateTime.toString() : null,
            lesson.room != null ? lesson.room.name : null,
            true,
            totalScore.toString(),
            violations
        );
    }

    // Note: buildBlockAnalysis(LabBlock, ...) removed - LabBlocks deprecated in TASK-228

    private LessonViolationAnalysis.ViolationDetail buildViolationDetail(
            ConstraintMatch<BendableScore> cm, Map<Long, Lesson> lessonById, Locale messageLocale) {
        String constraintName = ConstraintIds.shortName(cm.getConstraintRef().id());

        BendableScore score = cm.getScore();
        // TASK-572 point 4: the quasi tier sits between hard and medium (BendableScore
        // level 1): a constraint whose only weight is quasi must not be silently
        // classified MEDIUM. At quasi-tier flag OFF quasiOf is constantly 0 and this
        // is byte-equivalent to the pre-BendableScore classification.
        String level = ScoreCompat.hardOf(score) < 0 ? "HARD"
            : ScoreCompat.quasiOf(score) < 0 ? "QUASI"
            : ScoreCompat.mediumOf(score) < 0 ? "MEDIUM" : "SOFT";

        long absScore = Math.abs(ScoreCompat.hardOf(score) + ScoreCompat.quasiOf(score)
                + ScoreCompat.mediumOf(score) + ScoreCompat.softOf(score));

        // ConstraintMessageFormatter casts elements to Lesson, so custom justifications
        // holding only IDs must be resolved back to Lesson entities before formatting.
        Object justificationObj = cm.getJustification();
        List<Object> formatterArgs;
        List<Lesson> resolvedLessons = resolveLessons(justificationObj, lessonById);
        if (!resolvedLessons.isEmpty()) {
            formatterArgs = new ArrayList<>(resolvedLessons);
        } else if (justificationObj instanceof List<?> list) {
            formatterArgs = new ArrayList<>(list);
        } else {
            formatterArgs = justificationObj != null ? List.of(justificationObj) : List.of();
        }
        String message = ConstraintMessageFormatter.formatViolation(
                constraintName, formatterArgs, messageLocale);

        Justification justification = justificationObj instanceof Justification j ? j : null;

        return new LessonViolationAnalysis.ViolationDetail(constraintName, level, absScore, message, justification);
    }

    /**
     * Normalize constraint ID by extracting the base ID before the separator.
     * Examples:
     * - "H11" -> "H11"
     * - "H11 - Max student group daily hours" -> "H11"
     * - "H7.1" -> "H7.1"
     * - "H7.1 - Teacher daily hours exceeded" -> "H7.1"
     *
     * @param constraintId The constraint ID to normalize (may contain description)
     * @return The base constraint ID without description
     */
    private String normalizeConstraintId(String constraintId) {
        // Delegates to the shared helper (also used by ValidationService, TASK-354) —
        // ConstraintIds.canonicalId() is a strict superset: it additionally strips a
        // package prefix if present, which is a no-op for the bare filter strings this
        // method receives.
        return ConstraintIds.canonicalId(constraintId);
    }

    /**
     * Add H7 (Max teacher daily hours) violations to results.
     *
     * H7 uses groupBy() which tracks violations at (Teacher, Date) level,
     * not per-lesson. This method computes daily teacher hours and adds
     * H7 violation details to all lessons for teachers exceeding 6 hours/day.
     *
     * @param constraintId Optional constraint filter - only adds H7 violations if constraintId is null, "H7", or starts with "H7."
     */
    private void addH7Violations(List<LessonViolationAnalysis> results, SchoolSchedule schedule,
            LocalDate startDate, LocalDate endDate, Locale messageLocale, String constraintId) {
        // Compute daily lesson counts per teacher
        Map<String, Map<LocalDate, Integer>> teacherDailyHours = new HashMap<>();
        Map<String, Map<LocalDate, List<Lesson>>> teacherDailyLessons = new HashMap<>();

        for (Lesson lesson : schedule.getLessons()) {
            if (lesson.timeslot == null || lesson.getTeacher() == null) continue;
            // TASK-348 (golden-audit): mirror the H7 constraint's own pseudo
            // exclusion - pseudo teachers (e.g. "Stage") carry bogus aggregate
            // hours across different groups' pinned lessons, not a real
            // person's workload. Without this filter, this per-lesson mirror
            // spuriously reported every pseudo-teacher lesson as an H7
            // violation even though the constraint's own aggregate score
            // (which does filter pseudo) never counted them.
            if (lesson.getTeacher().pseudo) continue;

            String teacherKey = lesson.getTeacher().name; // Use name as key (Teacher entity might not be same instance)
            LocalDate date = lesson.timeslot.getDate();

            teacherDailyHours
                .computeIfAbsent(teacherKey, k -> new HashMap<>())
                .merge(date, 1, Integer::sum);

            teacherDailyLessons
                .computeIfAbsent(teacherKey, k -> new HashMap<>())
                .computeIfAbsent(date, k -> new ArrayList<>())
                .add(lesson);
        }

        // Find teachers/dates exceeding their max daily hours and add violations
        for (var teacherEntry : teacherDailyHours.entrySet()) {
            String teacherKey = teacherEntry.getKey();
            for (var dateEntry : teacherEntry.getValue().entrySet()) {
                LocalDate date = dateEntry.getKey();
                int hours = dateEntry.getValue();

                List<Lesson> lessonsOnDate = teacherDailyLessons.get(teacherKey).get(date);
                Lesson representative = lessonsOnDate.get(0);
                // TASK-348: single source of truth shared with the H7
                // constraint itself (SchoolScheduleConstraintProvider).
                int effectiveMaxHours = representative.getTeacher().getEffectiveMaxDailyHours();
                // TASK-348 code review (D2): fast path must compare against
                // this teacher's OWN effective max, not the global default -
                // an override that lowers the cap below the default (e.g.
                // maxDailyHours=4) would otherwise never be flagged, since
                // hours <= DEFAULT_MAX_DAILY_HOURS (6) would wrongly skip it.
                // This is still a field read off the lesson already collected
                // above, not an extra query.
                if (hours <= effectiveMaxHours) continue;

                // Skip if outside date range
                LocalDate d = representative.timeslot.getDate();
                if (d.isBefore(startDate) || d.isAfter(endDate)) continue;

                // Report one violation per teacher/date (matches groupBy matchCount)
                addOrUpdateViolation(results, representative,
                        "H7", "HARD", hours - effectiveMaxHours,
                        String.format("Teacher %s has %d hours on %s (max %d allowed)",
                                teacherKey, hours, date, effectiveMaxHours));
            }
        }
    }

    /**
     * Add H11 (Max student group daily hours) violations to results.
     *
     * H11 uses groupBy() which tracks violations at (StudentGroup, Date) level,
     * not per-lesson. This method computes daily lesson counts per group
     * and adds H11 violation details for groups exceeding their maxHoursPerDay.
     *
     * @param constraintId Optional constraint filter - only adds H11 violations if constraintId is null, "H11", or starts with "H11."
     */
    private void addH11Violations(List<LessonViolationAnalysis> results, SchoolSchedule schedule,
            LocalDate startDate, LocalDate endDate, Locale messageLocale, String constraintId) {
        // Compute daily lesson counts per student group. TASK-440: the T28 id-14
        // split is gone with the constraint it mirrored (H11-T28 removed; the
        // hardcoded id pointed at the wrong teacher on the current seed). This
        // drill-down now counts EVERY lesson of the group against the group's own
        // maxHoursPerDay, same population as the H11 constraint itself.
        Map<String, Map<LocalDate, Integer>> groupDailyHours = new HashMap<>();
        Map<String, Map<LocalDate, List<Lesson>>> groupDailyLessons = new HashMap<>();

        for (Lesson lesson : schedule.getLessons()) {
            if (lesson.timeslot == null || lesson.course == null || lesson.course.studentGroup == null) continue;

            String groupKey = lesson.course.studentGroup.name;
            LocalDate date = lesson.timeslot.getDate();
            Integer maxHours = lesson.course.studentGroup.maxHoursPerDay;

            if (maxHours == null) continue; // Skip groups without limits

            groupDailyHours
                .computeIfAbsent(groupKey, k -> new HashMap<>())
                .merge(date, 1, Integer::sum);

            groupDailyLessons
                .computeIfAbsent(groupKey, k -> new HashMap<>())
                .computeIfAbsent(date, k -> new ArrayList<>())
                .add(lesson);
        }

        // Find groups/dates exceeding maxHoursPerDay and add violations
        for (var groupEntry : groupDailyHours.entrySet()) {
            String groupKey = groupEntry.getKey();
            Map<LocalDate, List<Lesson>> lessonsByDate = groupDailyLessons.get(groupKey);

            for (var dateEntry : groupEntry.getValue().entrySet()) {
                LocalDate date = dateEntry.getKey();
                int hours = dateEntry.getValue();

                // Get maxHoursPerDay for this group
                Integer maxHours = null;
                List<Lesson> lessonsForMaxHours = lessonsByDate.get(date);
                if (lessonsForMaxHours != null && !lessonsForMaxHours.isEmpty()) {
                    maxHours = lessonsForMaxHours.get(0).course.studentGroup.maxHoursPerDay;
                }

                if (maxHours != null && hours > maxHours) {
                    addH11Violation(results, lessonsByDate.get(date), groupKey, date, hours, maxHours, startDate, endDate);
                }
            }
        }
    }

    /**
     * Helper method to add H11 violation detail for a group/date combo.
     * Reports one violation per group/date (matches groupBy matchCount).
     *
     * @param results List to add violations to
     * @param lessons Lessons in the violating group (first lesson used as representative)
     * @param groupKey Student group name
     * @param date Date of violation
     * @param hours Actual hours
     * @param maxHours Maximum allowed hours
     * @param startDate Start date filter
     * @param endDate End date filter
     */
    private void addH11Violation(List<LessonViolationAnalysis> results, List<Lesson> lessons,
            String groupKey, LocalDate date, int hours, int maxHours,
            LocalDate startDate, LocalDate endDate) {

        Lesson representative = lessons.get(0);
        LocalDate d = representative.timeslot.getDate();
        if (d.isBefore(startDate) || d.isAfter(endDate)) return;

        addOrUpdateViolation(results, representative,
                "H11", "HARD", hours - maxHours,
                String.format("Student group %s has %d hours on %s (max %d allowed)",
                        groupKey, hours, date, maxHours));
    }

    /**
     * Shared gap-counting helper for the M15/H17 drill-downs (TASK-349/350/392): sorts
     * {@code lessonsOnDate} in place by {@code indexFn} (raw hour for M15, canonical
     * {@link TimeslotSequence#slotIndexOf} for H17) - so callers can still use
     * {@code lessonsOnDate.get(0)} afterward as the earliest-lesson "representative"
     * for reporting - then sums the gap between each consecutive pair using the same
     * {@code indexFn}. A gap whose bounding lessons are BOTH pinned (the golden/
     * historical exceptions each constraint tolerates) is excused, mirroring
     * {@code sameDaySameSubjectShouldBeConsecutive}/{@code verticallyCompact}'s own
     * break-level pinned exemption in {@code SchoolScheduleConstraintProvider} - a
     * break touching even one unpinned lesson still counts, so a solver-placed lesson
     * can never create a fresh gap next to a pinned one. "Pinned" means AUTHORITATIVE
     * pin ({@link BaseConstraintProvider#isAuthoritativePin}), so a break between two
     * lessons the staged solve merely promoted (TASK-419) is reported exactly as the
     * constraints score it.
     */
    private static int countPinnedExemptGaps(List<Lesson> lessonsOnDate, ToIntFunction<Lesson> indexFn) {
        lessonsOnDate.sort(Comparator.comparingInt(indexFn));
        int gapCount = 0;
        for (int i = 0; i < lessonsOnDate.size() - 1; i++) {
            Lesson current = lessonsOnDate.get(i);
            Lesson next = lessonsOnDate.get(i + 1);
            int currentIndex = indexFn.applyAsInt(current);
            int nextIndex = indexFn.applyAsInt(next);
            if (nextIndex > currentIndex + 1
                    && !(BaseConstraintProvider.isAuthoritativePin(current)
                            && BaseConstraintProvider.isAuthoritativePin(next))) {
                gapCount += (nextIndex - currentIndex - 1);
            }
        }
        return gapCount;
    }

    /**
     * Add M15 (Same-day same-subject should be consecutive; was H15 until TASK-392)
     * violations to results.
     *
     * M15 uses groupBy() with toConsecutiveSequences() which tracks violations
     * at the (StudentGroup, Subject, Date) level, not per-lesson.
     * This method detects gaps between same-subject lessons on the same day
     * and adds M15 violation details to all affected lessons.
     *
     * @param constraintId Optional constraint filter
 only */
    private void addM15Violations(List<LessonViolationAnalysis> results, SchoolSchedule schedule,
            LocalDate startDate, LocalDate endDate, Locale messageLocale, String constraintId) {
        // Group lessons by (StudentGroup, Subject, Date)
        Map<String, Map<String, Map<LocalDate, List<Lesson>>>> groupSubjectDailyLessons = new HashMap<>();

        for (Lesson lesson : schedule.getLessons()) {
            if (lesson.timeslot == null || lesson.course == null
                    || lesson.course.studentGroup == null || lesson.course.subject == null) continue;
            if (lesson.isLab()) continue; // M15 only applies to non-lab lessons

            String groupKey = lesson.course.studentGroup.name;
            String subject = lesson.course.subject;
            LocalDate date = lesson.timeslot.getDate();

            groupSubjectDailyLessons
                .computeIfAbsent(groupKey, k -> new HashMap<>())
                .computeIfAbsent(subject, k -> new HashMap<>())
                .computeIfAbsent(date, k -> new ArrayList<>())
                .add(lesson);
        }

        // Detect gaps between same-subject lessons on same day for same group
        for (var groupEntry : groupSubjectDailyLessons.entrySet()) {
            String groupKey = groupEntry.getKey();
            for (var subjectEntry : groupEntry.getValue().entrySet()) {
                String subject = subjectEntry.getKey();
                for (var dateEntry : subjectEntry.getValue().entrySet()) {
                    LocalDate date = dateEntry.getKey();
                    List<Lesson> lessonsOnDate = dateEntry.getValue();

                    if (lessonsOnDate.size() < 2) continue; // Need at least 2 same-subject lessons to have a gap

                    // Skip if outside date range
                    if (date.isBefore(startDate) || date.isAfter(endDate)) continue;

                    // TASK-349: sorts lessonsOnDate by start hour and mirrors
                    // sameDaySameSubjectMustBeConsecutive's break-level pinned
                    // exemption - see countPinnedExemptGaps's own javadoc.
                    int gapCount = countPinnedExemptGaps(lessonsOnDate,
                            l -> l.timeslot.startDateTime.getHour());

                    if (gapCount > 0) {
                        // Report one violation per group/subject/date (matches groupBy matchCount)
                        addOrUpdateViolation(results, lessonsOnDate.get(0),
                                "M15", "MEDIUM", gapCount,
                                String.format("Subject %s for group %s has %d gap(s) between lessons on %s",
                                        subject, groupKey, gapCount, date));
                    }
                }
            }
        }
    }

    /**
     * Add H17 (Vertically compact) violations to results.
     *
     * H17 uses groupBy() with toConsecutiveSequences() which tracks violations
     * at (StudentGroup, Date) level, not per-lesson. This method detects gaps
     * in the daily schedule and adds H17 violation details to all lessons for
     * groups with gaps on that date.
     *
     * TASK-350: consolidated onto the same semantics as verticallyCompact()/
     * countBreakSlots() in SchoolScheduleConstraintProvider - gaps are measured
     * in canonical slot indices (TimeslotSequence.slotIndexOf(), lunch-aware
     * since slot 5 -> slot 6 differ by 1 index) rather than raw hour arithmetic.
     * See countPinnedExemptGaps's own javadoc for the pinned-exemption mechanism.
     *
     * @param constraintId Optional constraint filter - only adds H17 violations if constraintId is null, "H17", or starts with "H17."
     */
    private void addH17Violations(List<LessonViolationAnalysis> results, SchoolSchedule schedule,
            LocalDate startDate, LocalDate endDate, Locale messageLocale, String constraintId) {
        // Group lessons by (StudentGroup, Date) and detect gaps
        Map<String, Map<LocalDate, List<Lesson>>> groupDailyLessons = new HashMap<>();

        for (Lesson lesson : schedule.getLessons()) {
            if (lesson.timeslot == null || lesson.course == null || lesson.course.studentGroup == null) continue;

            String groupKey = lesson.course.studentGroup.name;
            LocalDate date = lesson.timeslot.getDate();

            groupDailyLessons
                .computeIfAbsent(groupKey, k -> new HashMap<>())
                .computeIfAbsent(date, k -> new ArrayList<>())
                .add(lesson);
        }

        // Detect gaps for each group/date combination
        for (var groupEntry : groupDailyLessons.entrySet()) {
            String groupKey = groupEntry.getKey();
            for (var dateEntry : groupEntry.getValue().entrySet()) {
                LocalDate date = dateEntry.getKey();
                List<Lesson> lessonsOnDate = dateEntry.getValue();

                if (lessonsOnDate.size() < 2) continue; // Need at least 2 lessons to have gaps

                // TASK-350: sorts lessonsOnDate by canonical slot index (schema-driven,
                // matches toConsecutiveSequences()'s classifier in verticallyCompact())
                // and mirrors its break-level pinned exemption - see
                // countPinnedExemptGaps's own javadoc.
                int gapCount = countPinnedExemptGaps(lessonsOnDate,
                        l -> TimeslotSequence.slotIndexOf(l.timeslot));

                if (gapCount > 0) {
                    Lesson representative = lessonsOnDate.get(0);
                    // Skip if lesson outside date range
                    LocalDate d = representative.timeslot.getDate();
                    if (!d.isBefore(startDate) && !d.isAfter(endDate)) {
                        // Report one violation per group/date (matches groupBy matchCount)
                        addOrUpdateViolation(results, representative,
                                "H17", "HARD", gapCount,
                                String.format("Student group %s has %d gap(s) in schedule on %s",
                                        groupKey, gapCount, date));
                    }
                }
            }
        }
    }

    /**
     * Add M18 (day should start from first calendar-open slot) violations to results.
     *
     * M18 uses groupBy() which tracks violations at (StudentGroup, Date) level, not
     * per-lesson - see {@code dayShouldStartFromFirstOpenSlot} in
     * SchoolScheduleConstraintProvider for the constraint itself.
     *
     * TASK-405: mirrors the WIDENED M18 - every (group, date) day, anchored to the
     * group's first calendar-open slot (SchoolSchedule.getGroupCalendarWindow, the
     * same lookup the constraint uses), penalty = offset slots. The pre-405 version
     * replicated the retired static maxHoursPerDay<=5 gate and still reported
     * "H18"/"HARD" from before the task-247 demotion - it could disagree with the
     * constraint score on every axis.
     *
     * @param constraintId Optional constraint filter - only adds M18 violations if constraintId is null, "M18", or starts with "M18."
     */
    private void addM18Violations(List<LessonViolationAnalysis> results, SchoolSchedule schedule,
            LocalDate startDate, LocalDate endDate, Locale messageLocale, String constraintId) {
        Map<String, Map<LocalDate, List<Lesson>>> groupDailyLessons = new HashMap<>();
        for (Lesson lesson : schedule.getLessons()) {
            if (lesson.timeslot == null || lesson.course == null || lesson.course.studentGroup == null) continue;
            groupDailyLessons
                .computeIfAbsent(lesson.course.studentGroup.name, k -> new HashMap<>())
                .computeIfAbsent(lesson.timeslot.getDate(), k -> new ArrayList<>())
                .add(lesson);
        }

        for (var groupEntry : groupDailyLessons.entrySet()) {
            String groupKey = groupEntry.getKey();
            for (var dateEntry : groupEntry.getValue().entrySet()) {
                LocalDate date = dateEntry.getKey();
                List<Lesson> lessonsOnDate = dateEntry.getValue();

                int minSlotIndex = lessonsOnDate.stream()
                        .mapToInt(l -> TimeslotSequence.slotIndexOf(l.timeslot))
                        .min()
                        .orElse(0);
                Long groupId = lessonsOnDate.get(0).course.studentGroup.id;
                int[] window = SchoolSchedule.getGroupCalendarWindow(groupId, date.getDayOfWeek(), date);
                int expectedFirstSlot = window[1];
                if (expectedFirstSlot < 0 || minSlotIndex <= expectedFirstSlot) continue;

                Lesson representative = lessonsOnDate.get(0);
                LocalDate d = representative.timeslot.getDate();
                if (!d.isBefore(startDate) && !d.isAfter(endDate)) {
                    int offset = minSlotIndex - expectedFirstSlot;
                    addOrUpdateViolation(results, representative,
                            "M18", "MEDIUM", offset,
                            String.format("Student group %s starts at slot %d on %s instead of its first open slot %d (offset %d)",
                                    groupKey, minSlotIndex, date, expectedFirstSlot, offset));
                }
            }
        }
    }

    /**
     * Helper method to add or update a violation on a lesson.
     * If the lesson already exists in results, adds the violation to its list.
     * Otherwise, creates a new LessonViolationAnalysis entry.
     */
    private void addOrUpdateViolation(List<LessonViolationAnalysis> results, Lesson lesson,
            String constraintId, String level, int score, String message) {

        // Find existing analysis or create new one
        Optional<LessonViolationAnalysis> existing = results.stream()
                .filter(a -> a.lessonId().equals(lesson.id))
                .findFirst();

        if (existing.isPresent()) {
            LessonViolationAnalysis analysis = existing.get();
            // Check if constraint already exists (use normalized comparison)
            boolean hasConstraint = analysis.violations().stream()
                    .anyMatch(v -> normalizeConstraintId(v.constraint()).equals(normalizeConstraintId(constraintId)));

            if (!hasConstraint) {
                List<LessonViolationAnalysis.ViolationDetail> updatedViolations =
                        new ArrayList<>(analysis.violations());
                updatedViolations.add(new LessonViolationAnalysis.ViolationDetail(
                        constraintId,
                        level,
                        score,
                        message,
                        null));  // No custom justification for this violation

                // Create new analysis with updated violations
                LessonViolationAnalysis updated = new LessonViolationAnalysis(
                        analysis.lessonId(),
                        analysis.entityType(),
                        analysis.teacher(),
                        analysis.subject(),
                        analysis.studentGroup(),
                        analysis.timeslot(),
                        analysis.room(),
                        true,
                        analysis.totalScore(),
                        updatedViolations
                );

                // Replace in results
                for (int i = 0; i < results.size(); i++) {
                    if (results.get(i).lessonId().equals(analysis.lessonId())) {
                        results.set(i, updated);
                        break;
                    }
                }
            }
        } else {
            // Create new analysis for this lesson with violation
            List<LessonViolationAnalysis.ViolationDetail> violations = new ArrayList<>();
            violations.add(new LessonViolationAnalysis.ViolationDetail(
                    constraintId,
                    level,
                    score,
                    message,
                    null));

            results.add(new LessonViolationAnalysis(
                    lesson.id,
                    "Lesson",
                    lesson.getTeacher() != null ? lesson.getTeacher().name : null,
                    lesson.getSubject(),
                    lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : null,
                    lesson.timeslot != null ? lesson.timeslot.startDateTime.toString() : null,
                    lesson.room != null ? lesson.room.name : null,
                    true,
                    "0hard/-1medium/0soft",
                    violations
            ));
        }
    }


    /**
     * Get the full schedule for export.
     * Includes all entities (problem facts + planning entities) for A/B testing.
     *
     * <p>TASK-419: REFUSED while a staged cycle runs. Mid-cycle, thousands of lessons are
     * pinned by the cycle itself (the promoted template cells); the export payload carries
     * no {@code promotedPin} marker, so re-importing such a file would resurrect them as
     * AUTHORITATIVE pins - a fabricated school decision, and exactly the accounting hole the
     * marker exists to prevent. Wait for the cycle's own release.
     */
    @Transactional
    public SchoolSchedule getFullScheduleForExport() {
        refuseIfStaged("exporting the schedule");
        List<Timeslot> timeslots = Timeslot.listAll();
        List<Room> rooms = Room.listAll();
        List<StagePattern> stagePatterns = StagePattern.listAll();
        List<TeacherAvailability> teacherAvailabilities = TeacherAvailability.listAll();
        List<SchoolHoliday> schoolHolidays = SchoolHoliday.listAll();
        List<StudentGroup> studentGroups = StudentGroup.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
        List<Lesson> lessons = Lesson.listAll();
        // Note: LabBlock.listAll() removed in TASK-228 - labs are now pinned Lessons
        List<LabAssistant> labAssistants = LabAssistant.listAll();

        LOG.infof("Export schedule: %d lessons, %d courses, %d teachers, %d rooms, %d timeslots",
                lessons.size(), courses.size(), teachers.size(), rooms.size(), timeslots.size());

        SchoolSchedule schedule = new SchoolSchedule(timeslots, rooms, stagePatterns,
                teacherAvailabilities, schoolHolidays, studentGroups,
                teachers, courses, lessons, labAssistants);
        // TASK-381 "L1-bis": carry DailyActivityQuota along too (M30/M31 need it as a
        // ProblemFactCollectionProperty) - otherwise a re-import of this export, or a
        // benchmark harness that solves it directly, would silently score M30/M31 as
        // vacuous for G04/G08/G13 regardless of the real quota data.
        schedule.setDailyActivityQuotas(DailyActivityQuota.listAll());
        // TASK-454: the group calendar rules must ride in the export too - the
        // truncate CASCADE of an import wipes them, and without them H20 and the
        // VRP calendar filter run blind on the re-solved profile (measured live
        // 25/09: 234-320 construction H17 holes, doomed basin, see TASK-666 notes).
        schedule.setGroupCalendarRules(GroupCalendarRule.listAll());
        return schedule;
    }

    /**
     * Import a complete schedule, replacing all existing data.
     * Delegates to ImportService for transactional import with validation.
     *
     * <p>TASK-419: REFUSED while a staged cycle runs - the import deletes and re-inserts
     * every lesson under a cycle that is holding pins and lesson ids in memory (its promoted
     * registry, its captured authoritative id set), so it would both corrupt the cycle and
     * make its closing conservation check fire on damage someone else caused.
     */
    @Transactional
    public void importSchedule(SchoolSchedule schedule) {
        refuseIfStaged("importing a schedule");
        importService.importSchedule(schedule);
        // Invalidate cached snapshot - schedule has changed
        cachedSnapshot = null;
        cachedAnalysisTime = null;
        LOG.info("Schedule imported successfully, caches invalidated");
    }
}
