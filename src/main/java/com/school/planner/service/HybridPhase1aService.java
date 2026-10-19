package com.school.planner.service;

import com.school.planner.domain.*;
import com.school.planner.domain.ProfessionalizzantiDay;
import com.school.planner.service.RotationTestRunner.BlockKey;
import com.school.planner.solver.RotationEnumerator;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.util.StageEligibilityUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Orchestrates the hybrid Phase 1a pre-assignment.
 *
 * <p>Implements sequential phases with CSP backtracking that mimics
 * human planner behavior:</p>
 *
 * <h3>PHASE 1: Acconciatura</h3>
 * <ul>
 *   <li>Use GreedyRotationAssigner for CSP backtracking</li>
 *   <li>External teachers (T28) constrained to single day</li>
 *   <li>Pin successful assignment</li>
 *   <li>Proceed to Phase 2</li>
 * </ul>
 *
 * <h3>PHASE 2: Other Labs</h3>
 * <p>For each roomTag in order (moda, termoidraulica, meccanica, informatica):</p>
 * <ul>
 *   <li>Use GreedyRotationAssigner respecting previous phase pins</li>
 *   <li>Pin successful assignment</li>
 * </ul>
 *
 * <h3>PHASE 3: Professionalizzanti</h3>
 * <ul>
 *   <li>Place professional subjects on stage days</li>
 *   <li>Respect all Phase 1+2 pins</li>
 * </ul>
 *
 * <p><strong>BLOCKING ERROR:</strong> If any phase fails to find a valid assignment,
 * return failure analysis (no fallback to solver).</p>
 *
 * <p><strong>Performance:</strong> CSP backtracking explores 12-25 candidates
 * (was 390,625 with Cartesian product). Phase 1a resolves in &lt;1s.</p>
 *
 * @see GreedyRotationAssigner
 * @see TeacherDayAssignment
 * @see HybridPhase1aReport
 */
@ApplicationScoped
public class HybridPhase1aService {

    private static final Logger LOG = Logger.getLogger(HybridPhase1aService.class);


    /**
     * TASK-435: constrained-teacher pre-assignment pin (human-planner move for the most
     * squeezed variable, e.g. T01 Thursday-only / 4 groups / 231h). Default ON since
     * maintainer ratified TASK-435 (2026-07-30): without the pin the G06 / Servizio tecnico e
     * stilistico / T01 triangle runs 30h over demand and 35 lessons stay unplaced, with
     * the pin the slack is +5h and only 3 stay (task459-slack-triangolo-g06-t01). Read
     * LIVE via {@link #isConstrainedTeacherEnabled()} (opt-out: set the property to "false"),
     * same form as {@code SchoolSchedule#isLabFallbackEnabled()}, so it reaches the app JVM on
     * the {@code java -jar} command line (MAVEN_OPTS lesson, CLAUDE.md). The phase method
     * {@link #runConstrainedTeacherPhase()} is public so the checkpoint harness can exercise
     * it directly without the flag.
     */
    public static final String CONSTRAINED_TEACHER_FLAG = "planner.pre-assign.constrained-teacher.enabled";
    /** A teacher whose golden placement is squeezed to at most this many weekdays is "constrained"
     *  (default; TASK-451/Q22 raises the LIVE threshold via {@value #CONSTRAINED_TEACHER_MAX_WEEKDAYS_FLAG}). */
    static final int CONSTRAINED_TEACHER_MAX_WEEKDAYS = 1;
    /** TASK-451/Q22: live override of the constrained-teacher weekday threshold. Default keeps
     *  {@value #CONSTRAINED_TEACHER_MAX_WEEKDAYS} (single-weekday teachers like T01); set to 2 to
     *  also pin 2-weekday Q22 teachers (T19 lun+mar). Read live by {@link #constrainedTeacherMaxWeekdays()}. */
    public static final String CONSTRAINED_TEACHER_MAX_WEEKDAYS_FLAG =
            "planner.pre-assign.constrained-teacher.max-weekdays";

    /**
     * TASK-435: true when the constrained-teacher pre-assignment pin is active. Default TRUE
     * (ratified 2026-07-30); opted out only by setting {@value CONSTRAINED_TEACHER_FLAG} to
     * "false". Same opt-out shape as {@link SchoolSchedule#isLabFallbackEnabled()} (default-on
     * behavior, not an opt-in) and read LIVE (System.getProperty, not a class-init static) so an
     * in-harness {@code System.setProperty(...,"false")} or a {@code -D...=false} on the java -jar
     * line both take effect.
     */
    public static boolean isConstrainedTeacherEnabled() {
        String v = System.getProperty(CONSTRAINED_TEACHER_FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * TASK-451/Q22: the live max-weekday threshold for "constrained" teachers. Default
     * {@value #CONSTRAINED_TEACHER_MAX_WEEKDAYS} (1: only single-weekday teachers like T01).
     * Raise via {@value #CONSTRAINED_TEACHER_MAX_WEEKDAYS_FLAG} to 2 to also pin 2-weekday Q22 teachers
     * (T19 lun+mar): the SAME coordinated pre-assignment mechanism (loadAllGoldenProfiles + plan +
     * persistPinnedLesson + canPinHardConstraints), NOT a new allocator. Read live (Integer.getInteger)
     * so a {@code -D...=2} on the java -jar line or an in-harness {@code System.setProperty(...,"2")}
     * both take effect. Non-adopted: stays 1 until the school confirms Q22 (T19 lun+mar) is a real
     * constraint, not an organizational preference (see spike-g06-barberia-t19).
     */
    static int constrainedTeacherMaxWeekdays() {
        return Integer.getInteger(CONSTRAINED_TEACHER_MAX_WEEKDAYS_FLAG, CONSTRAINED_TEACHER_MAX_WEEKDAYS);
    }

    /** TASK-452: source of the constrained-teacher profile. {@value #CONSTRAINED_TEACHER_SOURCE_GOLDEN}
     *  (default) reads the golden, byte-identical with the pre-452 behavior; {@value
     *  #CONSTRAINED_TEACHER_SOURCE_INPUT} builds the profile from INPUTS only (incarichi budgets +
     *  H5 available weekdays + school calendar via {@link ConstrainedTeacherAllocator#buildInputDerivedProfile}),
     *  the census ESITO 1 reconstruction. Read LIVE so an in-harness {@code System.setProperty} or a
     *  {@code -D...=input} on the java -jar line both take effect. Non-adopted: default stays golden
     *  (the input path is crystallized + equivalence-tested, not flipped). */
    public static final String CONSTRAINED_TEACHER_SOURCE_FLAG =
            "planner.pre-assign.constrained-teacher.source";
    static final String CONSTRAINED_TEACHER_SOURCE_GOLDEN = "golden";
    static final String CONSTRAINED_TEACHER_SOURCE_INPUT = "input";

    /**
     * TASK-452: true when the constrained-teacher profile is built from INPUTS (not the golden).
     * DEFAULT INPUT since the ratification (maintainer, 2026-08-16): the input-path rehearsal
     * (e103d8f3) confirmed 0hard first-attempt with the full ratified stack. The rollback
     * is explicit: {@code -Dplanner.pre-assign.constrained-teacher.source=golden} restores
     * the previous default. The golden FILE stays present (H-COPRESENCE depth map reads
     * it independently of this flag: its survival path is the workbook co-presenza sheet,
     * TASK-452 #7, not this flip).
     */
    static boolean isConstrainedTeacherSourceInput() {
        return CONSTRAINED_TEACHER_SOURCE_INPUT.equalsIgnoreCase(
                System.getProperty(CONSTRAINED_TEACHER_SOURCE_FLAG, CONSTRAINED_TEACHER_SOURCE_INPUT));
    }

    /**
     * TASK-449 (SPIKE 2): mono-room-tag pre-assignment pin (CHECKPOINT PROTOTYPE, default OFF).
     * For each lab room tag backed by exactly ONE usable room (acconciatura, termoidraulica,
     * meccanica), pins same-tag lab blocks WITH their room onto disjunct (weekday, slot) cells
     * derived from the golden's own weekly cadence, so they don't end up room-null. Measured not
     * adopted. Read via {@code Boolean.getBoolean} (same flag mechanism as the constrained-teacher
     * pin). The phase method {@link #runMonoRoomTagPhase()} is public so the checkpoint harness
     * can exercise it directly without the flag.
     */
    public static final String MONO_ROOM_TAG_FLAG = "planner.pre-assign.mono-room-tag.enabled";
    /** Mono-room tags: exactly one usable room each (skip moda, already pinned by the calendario
     *  importer; skip informatica, it has two rooms so no single-room squeeze). */
    static final List<String> MONO_ROOM_TAGS = List.of("acconciatura", "termoidraulica", "meccanica");

    @Inject
    RotationTestRunner testRunner;

    @Inject
    PreAssignmentContext context;

    @Inject
    GreedyRotationAssigner greedyAssigner;

    @Inject
    LabRotationDeriver labRotationDeriver;

    @Inject
    ScheduleService scheduleService;

    @Inject
    TargetEndDateCalculator targetEndDateCalculator;

    /**
     * EntityManager for native SQL queries (H11 daily limit enforcement).
     */
    @Inject
    EntityManager entityManager;

    /**
     * TASK-435: read-only 0hard validator used by the constrained-teacher phase to vet each
     * pin before persisting (H1/H2/H3/H5/H11/H-M2/M15/H16). {@link #canPinInMemory} reads the
     * {@link PreAssignmentContext} cache, consistent with the phase's incremental tracking.
     */
    @Inject
    PreAssignmentValidator validator;

    /**
     * Self-injection for proxy access to call @ActivateRequestContext methods.
     * Note (TASK-587, verified 6/09): ArC intercepts self-invoked @Transactional
     * too - this proxy is defensive redundancy, kept because removing it buys
     * nothing and the explicit route documents the request-context need.
     */
    @Inject
    Instance<HybridPhase1aService> self;

    /**
     * Maximum candidates to test per phase before giving up.
     * Configurable via application property.
     */
    @ConfigProperty(name = "planner.phase1a.max-candidates", defaultValue = "100000")
    int maxCandidatesPerPhase;

    /**
     * Executor for async hybrid execution.
     */
    private final ExecutorService hybridExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hybrid-phase1a");
        t.setDaemon(true);
        return t;
    });

    /**
     * Current hybrid status (for async tracking).
     * These are static to allow access from ScheduleService without CDI injection issues.
     */
    private static volatile HybridStatus currentStatus = HybridStatus.IDLE;
    private static volatile String currentPhase = null;
    private static volatile int currentCandidatesTried = 0;
    private static volatile String lastError = null;
    private static volatile Instant startTime = null;

    /**
     * Human-readable note for special-case COMPLETE outcomes (e.g. the
     * zero-rotation no-op). Kept separate from currentPhase, which callers
     * (ScheduleService.mapHybridStatusToPreAssignmentStatus(), the REST layer)
     * expect to hold a phase code ("1-ACCONCIATURA") or null - never prose.
     */
    private static volatile String completionNote = null;

    /**
     * Last failure analysis (for detailed error reporting).
     * Populated when a phase fails, cleared on reset or new run.
     */
    private static volatile FailureAnalysis lastFailureAnalysis = null;

    /**
     * Duration of the last completed pre-assignment run, in seconds.
     * Set when status transitions to COMPLETE; cleared on reset.
     */
    private static volatile long completedDurationSeconds = 0;

    /**
     * Last completed pre-assignment report (for phase history).
     * Set when status transitions to COMPLETE; cleared on reset.
     */
    private static volatile HybridPhase1aReport lastCompletedReport = null;

    /**
     * Currently running async task (for cancellation on reset).
     */
    private static volatile CompletableFuture<HybridPhase1aReport> runningTask = null;

    /**
     * Room tags for Phase 2, in processing order.
     * Order determined by human planner priority: moda, termoidraulica, meccanica, informatica.
     */
    private static final List<String> PHASE2_ROOM_TAGS = List.of(
        "moda",
        "termoidraulica",
        "meccanica",
        "informatica"
    );

    /**
     * Hybrid execution status.
     */
    public enum HybridStatus {
        IDLE,
        RUNNING,
        COMPLETE,
        ERROR
    }

    /**
     * Get current hybrid status.
     */
    public HybridStatus getStatus() {
        return currentStatus;
    }

    /**
     * Get current phase being processed.
     */
    public String getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Get the human-readable note for a special-case COMPLETE outcome
     * (e.g. the zero-rotation no-op), or null for a normal completed run.
     */
    public String getCompletionNote() {
        return completionNote;
    }

    /**
     * Get candidates tried so far.
     */
    public int getCurrentCandidatesTried() {
        return currentCandidatesTried;
    }

    /**
     * Get last error message.
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Get pre-assignment start time.
     */
    public Instant getStartTime() {
        return startTime;
    }

    /**
     * Get elapsed seconds for UI display.
     * Returns current elapsed time if running, or completed duration if done.
     */
    public long getElapsedSeconds() {
        if (startTime == null) {
            return 0;
        }
        if (currentStatus == HybridStatus.RUNNING) {
            return Duration.between(startTime, Instant.now()).getSeconds();
        }
        return completedDurationSeconds;
    }

    // Static getters for ScheduleService to access without CDI injection issues
    public static HybridStatus getCurrentStatus() { return currentStatus; }
    public static String getCurrentPhaseStatic() { return currentPhase; }
    public static String getLastErrorStatic() { return lastError; }
    public static Instant getStartTimeStatic() { return startTime; }
    public static FailureAnalysis getLastFailureAnalysis() { return lastFailureAnalysis; }
    public static long getCompletedDurationSeconds() { return completedDurationSeconds; }
    public static HybridPhase1aReport getLastCompletedReport() { return lastCompletedReport; }

    /**
     * Check if hybrid is currently running.
     */
    public boolean isRunning() {
        return currentStatus == HybridStatus.RUNNING;
    }

    /**
     * Reset status to IDLE.
     */
    /**
     * Reset status to IDLE.
     * Cancels any running task and clears all state.
     */
    public void resetStatus() {
        // Cancel running task if any
        CompletableFuture<HybridPhase1aReport> task = runningTask;
        if (task != null) {
            task.cancel(true);
            runningTask = null;
        }
        statusEpoch++;
        currentStatus = HybridStatus.IDLE;
        currentPhase = null;
        completionNote = null;
        currentCandidatesTried = 0;
        lastError = null;
        lastFailureAnalysis = null;
        startTime = null;
        completedDurationSeconds = 0;
        lastCompletedReport = null;
    }

    /**
     * Epoch guard: resetStatus() bumps the epoch; a worker thread records the epoch
     * it started under. CompletableFuture.cancel(true) cannot interrupt a thread
     * already inside supplyAsync, so without this guard a cancelled ("zombie") run
     * keeps flipping the status back to RUNNING at phase boundaries after a reset.
     */
    private volatile long statusEpoch = 0;
    private static final ThreadLocal<Long> WORKER_EPOCH = new ThreadLocal<>();

    /**
     * Update status and log the change.
     * Status is stored in static fields so ScheduleService can access it.
     */
    private void updateStatus(HybridStatus status, String phase) {
        Long workerEpoch = WORKER_EPOCH.get();
        if (workerEpoch != null && workerEpoch != statusEpoch) {
            LOG.infof("[HybridPhase1a] Ignoring status update from stale run (epoch %d != %d): %s/%s",
                workerEpoch, statusEpoch, status, phase);
            return;
        }
        currentStatus = status;
        currentPhase = phase;
        if (status == HybridStatus.RUNNING && startTime == null) {
            startTime = Instant.now();
        }
        if (status == HybridStatus.COMPLETE && startTime != null) {
            completedDurationSeconds = Duration.between(startTime, Instant.now()).getSeconds();
        }
        LOG.infof("[HybridPhase1a] Status update: %s, phase=%s, startTime=%s", status, phase, startTime);
    }

    /**
     * Set error status.
     */
    private void setErrorStatus(String phase, String error) {
        Long workerEpoch = WORKER_EPOCH.get();
        if (workerEpoch != null && workerEpoch != statusEpoch) {
            LOG.infof("[HybridPhase1a] Ignoring error status from stale run (epoch %d != %d): %s",
                workerEpoch, statusEpoch, phase);
            return;
        }
        currentStatus = HybridStatus.ERROR;
        currentPhase = phase;
        lastError = error;
        LOG.infof("[HybridPhase1a] Error status: phase=%s, error=%s", phase, error);
    }

    /**
     * Set the completion note for a special-case COMPLETE outcome. Same
     * stale-run epoch guard as updateStatus()/setErrorStatus().
     */
    private void setCompletionNote(String note) {
        Long workerEpoch = WORKER_EPOCH.get();
        if (workerEpoch != null && workerEpoch != statusEpoch) {
            return;
        }
        completionNote = note;
    }

    /**
     * Run the full hybrid Phase 1a pipeline asynchronously.
     * Returns immediately, check status via getStatus().
     *
     * <p>Uses CompletableFuture.supplyAsync() with hybridExecutor to run in background.
     * The run() method has @ActivateRequestContext to ensure CDI request context
     * is available in the worker thread.</p>
     */
    public CompletableFuture<HybridPhase1aReport> runAsync() {
        if (isRunning()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Hybrid already running: " + currentStatus));
        }
        // Set status to RUNNING before submitting to executor so HTTP response shows correct status
        startTime = Instant.now();
        updateStatus(HybridStatus.RUNNING, "STARTING");
        // Use CDI proxy for @ActivateRequestContext to work
        HybridPhase1aService proxy = self.get();
        final long submittedEpoch = statusEpoch;
        CompletableFuture<HybridPhase1aReport> task = CompletableFuture.supplyAsync(() -> proxy.runWithEpoch(submittedEpoch), hybridExecutor)
            .handle((result, ex) -> {
                if (ex != null) {
                    LOG.errorf("[HybridPhase1a] Pre-assignment FAILED with exception: %s",
                        ex.getMessage(), ex);
                    setErrorStatus("EXECUTION_ERROR", "Exception during pre-assignment: " + ex.getMessage());
                    // Return a failure report instead of propagating exception
                    return buildFailureReportFromException(ex, Instant.now());
                }
                return result;
            });
        runningTask = task;  // Store for potential cancellation
        return task;
    }

    /**
     * Run the full hybrid Phase 1a pipeline.
     *
     * <p><strong>NOT transactional:</strong> This method runs for extended periods
     * (potentially 10+ minutes). Individual DB operations use their own transactions
     * via @Transactional on pinAssignments() and pinLesson().</p>
     *
     * <p>When called via runAsync(), context propagation is handled by
     * VertxContextSupport.executeBlocking(). When called directly, this method
     * requires the be called from a thread with proper CDI context.</p>
     *
     * @return Report with success/failure and analysis
     */
    /**
     * Run the full hybrid Phase 1a pipeline (public entry point).
     * Sets status to RUNNING before starting.
     *
     * @return Report with success/failure and analysis
     */
    @ActivateRequestContext
    public HybridPhase1aReport run() {
        WORKER_EPOCH.set(statusEpoch);
        return run(true);
    }

    /**
     * Entry point for runAsync(): adopts the epoch captured at SUBMISSION time,
     * not at execution time — a queued run that starts after a reset must not
     * adopt the post-reset epoch, or the stale-run guard in updateStatus() is
     * bypassed and the cancelled run resurrects RUNNING.
     */
    @ActivateRequestContext
    public HybridPhase1aReport runWithEpoch(long submittedEpoch) {
        WORKER_EPOCH.set(submittedEpoch);
        return run(true);
    }

    /**
     * Internal run method with option to skip initialization.
     *
     * @param initializeStatus If true, set status to RUNNING and clear previous state.
     *                         Should be false when called from runAsync() which already set the status.
     * @return Report with success/failure and analysis
     */
    private HybridPhase1aReport run(boolean initializeStatus) {
        Instant start = Instant.now();
        List<HybridPhase1aReport.PhaseResult> phaseResults = new ArrayList<>();

        // Clear previous run state and set status (unless called from runAsync which already did this)
        if (initializeStatus) {
            startTime = Instant.now();
            currentCandidatesTried = 0;
            lastError = null;
            lastFailureAnalysis = null;
            completionNote = null;
            updateStatus(HybridStatus.RUNNING, "INITIALIZING");
        }

        LOG.infof("[HybridPhase1a] Starting hybrid Phase 1a pipeline (maxCandidates=%d)", maxCandidatesPerPhase);

        // Initialize context (preload pinned entities) and clear the room cache BEFORE any
        // phase runs. TASK-435 moved this above the LabRotation short-circuit so the
        // constrained-teacher phase (Phase 0) runs with an initialized context even on the
        // 2026/27 seed, where LabRotations are absent (deferred to TASK-364) and the
        // short-circuit below skips only Phase 1/2/3.
        // PHASE 0 (TASK-435): constrained-teacher pre-assignment pin - the human-planner move
        // for the most squeezed variable (e.g. T01, Thursday-only / 4 groups / 231h).
        // Runs INDEPENDENTLY of LabRotations, gated by planner.pre-assign.constrained-teacher.enabled
        // (default ON since TASK-435 ratification, opted out only with "false"). Called via the self
        // proxy so its @Transactional fires (the pin persists even though run() itself is not
        // transactional). context.initialize() is gated here so the opted-out + no-rotations path
        // below stays a pure no-op (byte-identical to baseline).
        if (isConstrainedTeacherEnabled()) {
            context.initialize();
            testRunner.clearCapacityCache();
            updateStatus(HybridStatus.RUNNING, "0-CONSTRAINED-TEACHER");
            phaseResults.add(self.get().runConstrainedTeacherPhase());
        }

        // PHASE 0b (TASK-449 SPIKE 2): mono-room-tag pre-assignment pin - pins same-tag lab
        // blocks WITH their room onto disjunct golden-cadence cells so the single mono room is
        // never double-booked (acconciatura/termoidraulica/meccanica). CHECKPOINT PROTOTYPE,
        // default OFF. Sibling of Phase 0: independent of the LabRotation short-circuit below,
        // called via the self proxy so its @Transactional fires. context.initialize() is
        // idempotent, safe even if Phase 0 already ran it. With the flag OFF this block is a
        // pure no-op (baseline byte-identical path).
        if (Boolean.getBoolean(MONO_ROOM_TAG_FLAG)) {
            context.initialize();
            testRunner.clearCapacityCache();
            updateStatus(HybridStatus.RUNNING, "0b-MONO-ROOM-TAG");
            phaseResults.add(self.get().runMonoRoomTagPhase());
        }

        // No LabRotation rows configured (2026/27 seed: rotations deferred to TASK-364,
        // see DataGenerator.generateLabRotations() javadoc). Phase 1/2 rely entirely on
        // LabRotation data to place acconciatura/lab sessions on the right day, so with
        // none configured they can never pin anything and would otherwise fail the whole
        // pipeline (status ERROR) even though there is genuinely nothing to pre-assign.
        // Report this as a clean, informative no-op instead - skipping Phase 3 too
        // (professionalizzanti doesn't actually need LabRotation) is intentional: it
        // matches today's behavior, where a Phase 1 failure already short-circuits
        // the pipeline before Phase 2/3 ever run, and keeps this report's "zero
        // pinned, zero failures" guarantee simple rather than partially-true.
        //
        // TASK-435: this short-circuit now skips ONLY Phase 1/2/3; Phase 0 above has already
        // run, so the report carries those pins (buildSuccessReport(phaseResults, ...) not List.of()).
        //
        // CAVEAT: this can't distinguish "rotations intentionally not configured yet"
        // from "rotations existed but were wiped" - e.g. ImportService deletes ALL
        // LabRotation rows on every import and DataGenerator only reseeds when
        // Timeslot.count() == 0, so a post-import wipe leaves this table empty
        // indefinitely too (see CLAUDE.md's ImportService note). Both cases produce
        // this same silent no-op; if that ambiguity becomes a real problem, the fix
        // is a dedicated signal for "rotations were wiped", not a LabRotation.count()
        // check.
        // TASK-364 follow-up (opt-in, DEFAULT OFF): when armed, derive golden-validated
        // rotation rows for the acconciatura family BEFORE the empty check, making Phase
        // 1/2/3 (and the TASK-467 enumerator) reachable on the 2026/27 seed. Golden
        // proposes the modal weekday/shape, the school's own calendar sheets dispose
        // (only confirmed lab days are seeded); provenance banner in the deriver.
        if (LabRotationDeriver.enabled()) {
            labRotationDeriver.deriveAndPersistIfEmpty();
        }

        if (LabRotation.count() == 0) {
            LOG.infof("[HybridPhase1a] No LabRotation rows configured - skipping Phase 1/2/3");
            setCompletionNote(phaseResults.isEmpty()
                ? "no rotations configured - nothing to pre-assign"
                : "no rotations configured - constrained-teacher pins applied, Phase 1/2/3 skipped");
            updateStatus(HybridStatus.COMPLETE, null);
            return buildSuccessReport(phaseResults, start);
        }

        // LabRotations exist: initialize the context for Phase 1/2/3 (Phase 0 above already
        // initialized + ran when the flag was on; this covers the flag-OFF + rotations case).
        context.initialize();
        testRunner.clearCapacityCache();

        // H11: Build cache of existing pinned lesson counts BEFORE Phase 1 and AFTER Phase 0,
        // so Phase 1/2/3 see the constrained-teacher pins in their daily-limit accounting.
        // This applies to ALL phases (not just Phase 3) to prevent daily limit violations.
        Set<Long> allGroupIds = getAllGroupIdsWithLessons();
        Map<Long, Map<LocalDate, Integer>> globalExistingLessonCounts = buildExistingLessonCountsCache(allGroupIds);
        Map<Long, Map<LocalDate, Integer>> globalNewLessonDates = new HashMap<>();

        // PHASE 1: Acconciatura
        updateStatus(HybridStatus.RUNNING, "1-ACCONCIATURA");
        var phase1Result = runPhase1(globalExistingLessonCounts, globalNewLessonDates);
        phaseResults.add(phase1Result);
        if (!phase1Result.success) {
            LOG.errorf("[HybridPhase1a] Pipeline failed at Phase 1");
            setErrorStatus("1-ACCONCIATURA", "Phase 1 (Acconciatura) failed");
            return buildFailureReport(phase1Result, phaseResults, start);
        }

        // PHASE 2: Other Labs (moda, termoidraulica, meccanica, informatica)
        for (String roomTag : PHASE2_ROOM_TAGS) {
            updateStatus(HybridStatus.RUNNING, "2-LABS-" + roomTag);
            var phase2Result = runPhase2ForRoomTag(roomTag, globalExistingLessonCounts, globalNewLessonDates);
            phaseResults.add(phase2Result);
            if (!phase2Result.success) {
                LOG.errorf("[HybridPhase1a] Pipeline failed at Phase 2 (%s)", roomTag);
                setErrorStatus("2-LABS-" + roomTag, "Phase 2 (" + roomTag + ") failed");
                return buildFailureReport(phase2Result, phaseResults, start);
            }
        }

        // PHASE 3: Professionalizzanti
        updateStatus(HybridStatus.RUNNING, "3-PROFESSIONALIZZANTI");
        var phase3Result = runPhase3();
        phaseResults.add(phase3Result);
        if (!phase3Result.success) {
            LOG.errorf("[HybridPhase1a] Pipeline failed at Phase 3");
            setErrorStatus("3-PROFESSIONALIZZANTI", "Phase 3 (Professionalizzanti) failed");
            return buildFailureReport(phase3Result, phaseResults, start);
        }

        updateStatus(HybridStatus.COMPLETE, null);
        LOG.infof("[HybridPhase1a] Pipeline completed successfully in %s",
            Duration.between(start, Instant.now()));
        return buildSuccessReport(phaseResults, start);
    }

    /**
     * TASK-452: build every constrained teacher's profile from INPUTS only (the census ESITO 1
     * reconstruction), keyed by teacher NAME (same shape as {@link
     * ConstrainedTeacherAllocator#loadAllGoldenProfiles}). A teacher is included when their H5
     * available weekdays (Mon-Fri minus full-day VACATION, from {@link RecurringAvailabilityRule})
     * number at most {@link #constrainedTeacherMaxWeekdays()}; for each, the profile is
     * {@link ConstrainedTeacherAllocator#buildInputDerivedProfile} fed by their incarichi budgets
     * (Course hours) and the school calendar (Timeslot dates). Never reads the golden. Used only
     * when {@code source=input}.
     *
     * <p><b>H5 approximation (latent, opt-in).</b> Available weekdays derive from full-day VACATION
     * rules only ({@link RecurringAvailabilityRule#isFullDay}). This ignores recurring SICK and the
     * {@code effectiveFrom}/{@code effectiveTo} date windows: a future full-day recurring SICK, or a
     * date-scoped full-day VACATION, would make this profile diverge from what H5 actually enforces
     * on a given date. Neither exists on the 2026/27 seed, and {@code PreAssignmentValidator#canPinHardConstraints}
     * vetoes any H5-violating pin regardless, so the divergence is latent, not active. Refining this
     * (per-date H5) is m-13 work, recorded with the census.
     */
    private Map<String, Map<String, Map<LocalDate, Integer>>> buildAllInputDerivedProfiles(
            Map<String, StudentGroup> groupByName,
            Map<Long, Map<Long, Integer>> courseHoursByTeacherGroup,
            Map<LocalDate, List<Timeslot>> slotsByDate) {
        // H5 full-day VACATION weekdays per teacher (a full-day rule removes a whole weekday).
        Map<Long, Set<DayOfWeek>> fullDayVacationByTeacher = new HashMap<>();
        for (RecurringAvailabilityRule r : RecurringAvailabilityRule.<RecurringAvailabilityRule>listAll()) {
            if (r.type == AvailabilityType.VACATION && r.isFullDay()
                    && r.teacher != null && r.dayOfWeek != null) {
                fullDayVacationByTeacher
                        .computeIfAbsent(r.teacher.id, t -> EnumSet.noneOf(DayOfWeek.class))
                        .add(r.dayOfWeek);
            }
        }
        // Budgets are keyed by group id; the profile is keyed by group NAME (golden-faithful shape).
        Map<Long, String> groupNameById = new HashMap<>();
        for (StudentGroup g : groupByName.values()) {
            groupNameById.put(g.id, g.name);
        }
        List<LocalDate> schoolDates = new ArrayList<>(slotsByDate.keySet());
        // TASK-452 cura: index the REAL GroupCalendarRule open-slot counts here (the static
        // SchoolSchedule map is empty at pre-assign; see ConstrainedTeacherAllocator.buildInputDerivedProfile).
        Map<String, Map<LocalDate, Integer>> openSlotsByGroupDate = indexOpenSlotsByGroupDate(groupByName, schoolDates);
        // TASK-452 hard-0 cure: per-group stage dates from INPUT (DailyActivityQuota.stageHours > 0,
        // the SAME source M-CPS reads via SchoolSchedule.setStageEndByGroupStatic, NEVER the golden),
        // so the profile skips a group's own stage window instead of burning budget on off-grid days.
        Map<String, Set<LocalDate>> stageDatesByGroup = indexStageDatesByGroup(groupNameById);
        // Density axis (maintainer 20/08 sera, anti-pattern piano-sopra-quota): the DECLARED
        // aula+lab quota per (group, date) from the same DailyActivityQuota rows H32 reads,
        // so the input-derived profile never pins beyond a day's own quota.
        Map<String, Map<LocalDate, Integer>> quotaByGroupDate = indexQuotaByGroupDate(groupNameById);
        int maxWd = constrainedTeacherMaxWeekdays();
        Map<String, Map<String, Map<LocalDate, Integer>>> all = new LinkedHashMap<>();
        for (Teacher teacher : Teacher.<Teacher>listAll()) {
            if (teacher.external || teacher.pseudo) {
                continue;
            }
            Set<DayOfWeek> available = ConstrainedTeacherAllocator.availableWeekdays(
                    fullDayVacationByTeacher.getOrDefault(teacher.id, Set.of()));
            if (available.size() > maxWd) {
                // Optimization: skip non-constrained teachers so we don't build a profile the
                // source-agnostic pin loop below would drop anyway (it re-checks maxWd on the
                // profile's own weekdays; for the input path the two agree by construction).
                continue;
            }
            Map<Long, Integer> hoursByGroupId = courseHoursByTeacherGroup.get(teacher.id);
            if (hoursByGroupId == null || hoursByGroupId.isEmpty()) {
                continue;
            }
            Map<String, Integer> budgetByName = new LinkedHashMap<>();
            for (Map.Entry<Long, Integer> e : hoursByGroupId.entrySet()) {
                String name = groupNameById.get(e.getKey());
                if (name != null) {
                    budgetByName.merge(name, e.getValue(), Integer::sum);
                }
            }
            int h7 = teacher.getEffectiveMaxDailyHours();
            Map<String, Map<LocalDate, Integer>> profile = ConstrainedTeacherAllocator
                    .buildInputDerivedProfile(budgetByName, available, schoolDates, h7, openSlotsByGroupDate,
                            stageDatesByGroup, quotaByGroupDate);
            if (profile != null && !profile.isEmpty()) {
                all.put(teacher.name, profile);
            }
        }
        LOG.infof("[HybridPhase1a] TASK-452 source=input: built input-derived profiles for %d constrained teacher(s)",
                all.size());
        return all;
    }

    /**
     * TASK-452 cura: index the calendar-open slot count for every (group name, school date) straight
     * from the REAL {@link GroupCalendarRule} rows, via the shared resolution primitive
     * ({@link GroupCalendarService#effectiveRange} + {@link GroupCalendarService#openSlotCountFromRange}).
     * Date-aware so a date-scoped rule override is honoured. A group with no rule for a weekday resolves
     * fully open ({@link GroupCalendarService#TOTAL_SLOTS}), which the profile cap then treats as
     * "no cap beyond h7" (matches the pre-cure behaviour for groups without a calendar rule). Uses
     * {@code effectiveRange} directly rather than {@link GroupCalendarService#resolveRange}: the latter
     * applies the legacy {@code maxHoursPerDay} fallback for groups with no rule (an H11 concern),
     * which would diverge from H20's effectiveRange default this cap must mirror.
     */
    private static Map<String, Map<LocalDate, Integer>> indexOpenSlotsByGroupDate(
            Map<String, StudentGroup> groupByName, List<LocalDate> schoolDates) {
        Map<Long, List<GroupCalendarRule>> rulesByGroup = GroupCalendarRule.<GroupCalendarRule>listAll().stream()
                .filter(r -> r.studentGroup != null)
                .collect(Collectors.groupingBy(r -> r.studentGroup.id));
        Map<String, Map<LocalDate, Integer>> out = new LinkedHashMap<>();
        for (Map.Entry<String, StudentGroup> e : groupByName.entrySet()) {
            StudentGroup g = e.getValue();
            List<GroupCalendarRule> rules = (g == null || g.id == null) ? List.of()
                    : rulesByGroup.getOrDefault(g.id, List.of());
            Map<LocalDate, Integer> byDate = new LinkedHashMap<>();
            for (LocalDate d : schoolDates) {
                int[] range = GroupCalendarService.effectiveRange(rules, d.getDayOfWeek(), d);
                byDate.put(d, GroupCalendarService.openSlotCountFromRange(range));
            }
            out.put(e.getKey(), byDate);
        }
        return out;
    }

    /**
     * TASK-452 hard-0 cure: index every group's stage dates straight from the REAL
     * {@link DailyActivityQuota} rows ({@code stageHours > 0}, via the shared
     * {@link DailyActivityQuota#isStageDate} predicate the VRP filter and H25/H26 also use, so
     * this can never drift from what counts as a stage day elsewhere), keyed by group NAME.
     * INPUT-derived (the calendario sheets), the SAME source M-CPS reads via
     * {@code SchoolSchedule.setStageEndByGroupStatic}; never the golden. A constrained
     * teacher's ordinary lesson cannot share a stage day (the group is off-grid on
     * internship, H25 stage-confinement / H26 no-session for the non-stage quota), so
     * the input-derived profile must skip these dates for that group instead of burning
     * budget on cells the pin loop can never fill (the run3 hard-0 root cause).
     *
     * @param groupNameById the caller's id-to-name map (already built by
     *                      {@code buildAllInputDerivedProfiles}); passed in rather than re-queried,
     *                      matching the {@link #indexOpenSlotsByGroupDate} pattern
     */
    private static Map<String, Set<LocalDate>> indexStageDatesByGroup(Map<Long, String> groupNameById) {
        Map<String, Set<LocalDate>> out = new LinkedHashMap<>();
        for (DailyActivityQuota q : DailyActivityQuota.<DailyActivityQuota>listAll()) {
            if (q.studentGroup != null && DailyActivityQuota.isStageDate(q)) {
                String name = groupNameById.get(q.studentGroup.id);
                if (name != null) {
                    out.computeIfAbsent(name, n -> new LinkedHashSet<>()).add(q.date);
                }
            }
        }
        return out;
    }

    /**
     * Density axis (anti-pattern piano-sopra-quota, 20/08): groupName -> date -> DECLARED
     * aula+lab quota, from the same {@link DailyActivityQuota} rows H32 reads (INPUT-derived,
     * never the golden). Rows with aula+lab &le; 0 are omitted (closed/pure-stage days: the
     * profile's quota cap treats them as "nothing to pin toward").
     */
    private static Map<String, Map<LocalDate, Integer>> indexQuotaByGroupDate(Map<Long, String> groupNameById) {
        Map<String, Map<LocalDate, Integer>> out = new LinkedHashMap<>();
        for (DailyActivityQuota q : DailyActivityQuota.<DailyActivityQuota>listAll()) {
            if (q.studentGroup == null || q.date == null) {
                continue;
            }
            int quota = q.aulaHours + q.labHours;
            if (quota <= 0) {
                continue;
            }
            String name = groupNameById.get(q.studentGroup.id);
            if (name != null) {
                out.computeIfAbsent(name, n -> new LinkedHashMap<>()).put(q.date, quota);
            }
        }
        return out;
    }

    /**
     * TASK-435: Constrained-teacher pre-assignment pin (Phase 0).
     *
     * <p>For each internal, non-pseudo teacher whose golden placement is squeezed to at most
     * {@link #CONSTRAINED_TEACHER_MAX_WEEKDAYS} weekdays (the observable consequence of H5
     * squeezing his availability, e.g. T01 Thursday-only), this pins his lessons' TIMESLOTS to
     * the golden's per-(group, date, hours) allocation BEFORE the solver runs (room left null for
     * the room phase). The golden is the authoritative reference for <i>which dates</i> are his;
     * the sheet cross-checks the hour count (verified concordant). This is the human-planner "pin
     * them, only move what is left out" move applied to the single most constrained variable.
     *
     * <p>Package-private: invoked only from {@code run()} (gated by
     * {@code planner.pre-assign.constrained-teacher.enabled}); the checkpoint harness sets that
     * system property and calls {@code run()}. Best-effort: every pin is vetted by
     * {@link PreAssignmentValidator#canPinHardConstraints} (hard timeslot constraints only); a
     * lesson that would violate 0hard is left for the solver (counted as skipped) rather than
     * forced. Idempotent: only currently-unpinned lessons are touched, so a re-run is a no-op.
     * Pins are logged per group/date and visible in the report's {@code entitiesPinned}.
     *
     * <p><b>TASK-452 profile source.</b> The profile this phase pins comes from
     * {@link ConstrainedTeacherAllocator#loadAllGoldenProfiles} by default ({@code source=golden},
     * byte-identical with pre-452), or from {@link #buildAllInputDerivedProfiles} when
     * {@code planner.pre-assign.constrained-teacher.source=input} (the census ESITO 1 input-derived
     * reconstruction). The rest of this phase (constrained detection via profile weekdays, plan(),
     * pinning) is source-agnostic.
     *
     * @return Phase result with the count of pinned lessons (success is always true: the phase
     *         is best-effort; the harness is the verdict on whether it eliminated the residual)
     */
    @ActivateRequestContext
    @Transactional
    public HybridPhase1aReport.PhaseResult runConstrainedTeacherPhase() {
        Instant start = Instant.now();
        LOG.infof("[HybridPhase1a] Phase 0 (constrained-teacher pin, TASK-435, max-weekdays=%d): starting",
                constrainedTeacherMaxWeekdays());

        int pinned = 0;
        int skipped = 0;
        int teachersHandled = 0;

        // Hoist all static lookups ONCE (the per-teacher/per-target path used to re-parse the
        // golden CSV and re-query StudentGroup/Course/Timeslot per item).
        Map<String, StudentGroup> groupByName = new HashMap<>();
        for (StudentGroup g : StudentGroup.<StudentGroup>listAll()) {
            groupByName.put(g.name, g);
        }
        Map<Long, Map<Long, Integer>> courseHoursByTeacherGroup = new HashMap<>();
        for (Course c : Course.<Course>listAll()) {
            if (c.teacher != null && c.studentGroup != null) {
                // merge (not put): a teacher can have several Course rows for one group
                // (different subjects); put would silently keep only the last and under-cap pins.
                courseHoursByTeacherGroup
                        .computeIfAbsent(c.teacher.id, t -> new HashMap<>())
                        .merge(c.studentGroup.id, c.totalHours, Integer::sum);
            }
        }
        Map<LocalDate, List<Timeslot>> slotsByDate = new HashMap<>();
        for (Timeslot ts : Timeslot.<Timeslot>listAll()) {
            slotsByDate.computeIfAbsent(ts.getDate(), d -> new ArrayList<>()).add(ts);
        }
        // TASK-452: profile source. DEFAULT golden = byte-identical with the pre-452 behavior;
        // source=input builds the profile from INPUTS (incarichi + H5 + calendar), the census
        // ESITO 1 reconstruction (ConstrainedTeacherAllocator.buildInputDerivedProfile). The input
        // branch needs the hoisted maps above, so the load sits after them. The rest of this phase
        // (constrained detection via profile weekdays, plan(), pinning) is source-agnostic.
        Map<String, Map<String, Map<LocalDate, Integer>>> allProfiles =
                isConstrainedTeacherSourceInput()
                        ? buildAllInputDerivedProfiles(groupByName, courseHoursByTeacherGroup, slotsByDate)
                        : ConstrainedTeacherAllocator.loadAllGoldenProfiles();

        for (Teacher teacher : Teacher.<Teacher>listAll()) {
            if (teacher.external || teacher.pseudo) {
                continue; // internal, non-pseudo only (external teachers like T28 keep H5-only handling)
            }
            Map<String, Map<LocalDate, Integer>> profile = allProfiles.get(teacher.name);
            if (profile == null || profile.isEmpty()) {
                continue;
            }

            // Constrained = golden placement squeezed to <= MAX_WEEKDAYS weekdays. This is the
            // observable consequence of H5 squeezing availability (AC #7 notes "H5 VACATION-derived";
            // the golden's weekday count is used because it is robust to partial-day VACATION rules
            // and is consistent with a golden-faithful pin: no golden -> nothing to pin faithfully).
            Set<DayOfWeek> weekdays = profile.values().stream()
                    .flatMap(m -> m.keySet().stream())
                    .map(LocalDate::getDayOfWeek)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
            if (weekdays.size() > constrainedTeacherMaxWeekdays()) {
                continue;
            }

            // Resolve this teacher's groups + budgets from the hoisted maps (no per-group query).
            Map<String, StudentGroup> teacherGroups = new LinkedHashMap<>();
            Map<String, Integer> budgets = new LinkedHashMap<>();
            for (String groupName : profile.keySet()) {
                StudentGroup group = groupByName.get(groupName);
                if (group == null) {
                    LOG.warnf("[HybridPhase1a] constrained-teacher: group '%s' (golden) not found in DB, skipping it",
                            groupName);
                    continue;
                }
                teacherGroups.put(groupName, group);
                budgets.put(groupName, courseHoursByTeacherGroup
                        .getOrDefault(teacher.id, Map.of()).getOrDefault(group.id, 0));
            }
            if (teacherGroups.isEmpty()) {
                continue;
            }

            // Plan only over groups that resolved to a DB StudentGroup: plan() iterates its
            // profile's keySet, so an unresolved golden group name would otherwise yield a target
            // whose teacherGroups.get() is null and NPE in openSlotsForGroupDay.
            Map<String, Map<LocalDate, Integer>> resolvedProfile = new LinkedHashMap<>();
            for (String groupName : teacherGroups.keySet()) {
                resolvedProfile.put(groupName, profile.get(groupName));
            }
            ConstrainedTeacherAllocator.Plan plan = ConstrainedTeacherAllocator.plan(
                    resolvedProfile, budgets, teacher.getEffectiveMaxDailyHours());
            for (String w : plan.warnings()) {
                LOG.warnf("[HybridPhase1a] constrained-teacher %s: %s", teacher.name, w);
            }
            teachersHandled++;
            LOG.infof("[HybridPhase1a] Constrained teacher %s (%d weekday%s, %s): %d pin targets",
                    teacher.name, weekdays.size(), weekdays.size() == 1 ? "" : "s", weekdays,
                    plan.targets().size());

            // One unpinned-lesson pool per (teacher, group), consumed across that group's targets.
            Map<String, List<Lesson>> poolByGroup = new HashMap<>();
            int residual = 0;
            for (ConstrainedTeacherAllocator.PinTarget target : plan.targets()) {
                StudentGroup group = teacherGroups.get(target.groupName());
                List<Timeslot> openSlots = openSlotsForGroupDay(group, target.date(),
                        slotsByDate.getOrDefault(target.date(), List.of()));
                List<Lesson> pool = poolByGroup.computeIfAbsent(target.groupName(), g -> Lesson.find(
                        "course.teacher = ?1 and course.studentGroup = ?2 and pinned = false and timeslot is null",
                        teacher, group).list());
                // Iterate slots; for each, take the next unconsumed lesson. On a slot-specific
                // hard conflict (H1/H11), advance the slot and retry the same lesson - a single
                // occupied slot must not stall every remaining lesson on that same slot.
                int placed = 0;
                int poolIdx = 0;
                for (int slotIdx = 0; slotIdx < openSlots.size() && placed < target.hours() && poolIdx < pool.size(); slotIdx++) {
                    while (poolIdx < pool.size() && (pool.get(poolIdx).pinned || pool.get(poolIdx).timeslot != null)) {
                        poolIdx++; // skip lessons consumed by an earlier target this run
                    }
                    if (poolIdx >= pool.size()) {
                        break;
                    }
                    Lesson lesson = pool.get(poolIdx);
                    Timeslot ts = openSlots.get(slotIdx);
                    // Assign the course's room (findRoomForLesson) so the pinned lesson is complete:
                    // @PlanningPin makes room immovable, so a null room here would leave the lesson
                    // permanently room-less (the room phase can't assign to a pinned entity). The
                    // room is shared across the day's block, satisfying H16b by construction.
                    Room room = findRoomForLesson(lesson);
                    if (room == null || !validator.canPinHardConstraints(lesson, ts, room)) {
                        LOG.warnf("[HybridPhase1a] cannot pin %s %s slot#%d (hard conflict / no room), advancing slot",
                                teacher.name, target.date(), slotIdx);
                        continue;
                    }
                    persistPinnedLesson(lesson, ts, room);
                    pinned++;
                    placed++;
                    poolIdx++;
                }
                if (placed < target.hours()) {
                    LOG.warnf("[HybridPhase1a] %s %s: pinned %dh of %dh target (open slots=%d, pool=%d)",
                            teacher.name, target.date(), placed, target.hours(), openSlots.size(), pool.size());
                    skipped += target.hours() - placed;
                    residual += target.hours() - placed;
                } else {
                    LOG.infof("[HybridPhase1a] %s %s %s: pinned %dh", teacher.name, group.name, target.date(), placed);
                }
            }
            if (residual > 0) {
                LOG.warnf("[HybridPhase1a] constrained teacher %s: %dh left for the solver (explicit exception)",
                        teacher.name, residual);
            }
        }

        LOG.infof("[HybridPhase1a] Phase 0 done: %d teachers, %d pinned, %d skipped",
                teachersHandled, pinned, skipped);
        // success is always true: the phase is best-effort (pin what the golden says, leave the
        // residual for the solver); the harness asserts the residual directly.
        return new HybridPhase1aReport.PhaseResult(
                "0-CONSTRAINED-TEACHER", true, 0, Duration.between(start, Instant.now()), pinned, null);
    }

    /**
     * TASK-449 (SPIKE 2): mono-room-tag pre-assignment pin (Phase 0b).
     *
     * <p>For each mono-room tag (acconciatura, termoidraulica, meccanica - exactly one usable room
     * each), this pins same-tag lab blocks WITH their room onto disjunct (weekday, slot) cells
     * derived from the golden's own weekly cadence, BEFORE the solver runs. The room is the scarce
     * resource: multiple groups' same-tag labs collide on it, and left to the solver the
     * over-subscription surfaces as room-null lab hours. Pinning the golden's disjunct cadence
     * eliminates the room contention for the pinned share; the residual stays for the solver.
     *
     * <p>Mirrors {@link #runConstrainedTeacherPhase()}: hoist lookups once, build the golden
     * cadence per tag, plan via {@link MonoRoomTagAllocator}, then pin each target with the EXACT
     * {@code findRoomForLesson}/{@code validator.canPinHardConstraints}/{@code persistPinnedLesson}
     * path (stamped across the matching school dates at the planned weekday/slot). Each pin is
     * validated at 0hard; a hard conflict or missing room skips the pin (never forced).
     *
     * <p>The demand query filters on {@code requiredRoomTags} membership (not {@code isLabHour}):
     * in the 2026/27 seed {@code labBlockSize} defaults to 0, so {@code isLabHour} is always false
     * and the {@code loadLabSessionsByRoomTag} shape would select nothing; tag membership is the
     * semantically correct filter for "lessons needing this room".
     *
     * <p>Public + {@code @ActivateRequestContext @Transactional} so the checkpoint harness can call
     * it directly (via the CDI proxy). Best-effort: success is always true; the printed per-tag
     * verdict table (pinned / skipped / residual / total) IS the spike's measurement.
     */
    @ActivateRequestContext
    @Transactional
    public HybridPhase1aReport.PhaseResult runMonoRoomTagPhase() {
        Instant start = Instant.now();
        LOG.info("[HybridPhase1a] Phase 0b (mono-room-tag pin, TASK-449): starting");

        // Hoist static lookups ONCE (per-tag queries would otherwise re-scan the whole DB).
        Map<String, StudentGroup> groupByName = new HashMap<>();
        for (StudentGroup g : StudentGroup.<StudentGroup>listAll()) {
            groupByName.put(g.name, g);
        }
        Map<LocalDate, List<Timeslot>> slotsByDate = new HashMap<>();
        Set<Integer> isoWeeks = new HashSet<>();
        for (Timeslot ts : Timeslot.<Timeslot>listAll()) {
            slotsByDate.computeIfAbsent(ts.getDate(), d -> new ArrayList<>()).add(ts);
            isoWeeks.add(TimeslotSequence.isoWeekKey(ts.getDate()));
        }
        int teachingWeeks = Math.max(1, isoWeeks.size());

        int totalPinned = 0;
        int totalSkipped = 0;

        for (String tag : MONO_ROOM_TAGS) {
            // Resolve the single mono room for this tag.
            Room room = Room.find("SELECT r FROM Room r JOIN r.tags t WHERE t = ?1", tag).firstResult();
            if (room == null) {
                LOG.warnf("[HybridPhase1a] mono-room-tag: no room carries tag '%s', skipping tag", tag);
                System.out.printf("TASK-449 %-16s | NO ROOM (tag unresolved) - skipped%n", tag);
                continue;
            }

            // The tag's subject set, from the DB (Course.requiredRoomTags). This is the caller's
            // subject->tag filter, sourced from the same Course rows the constraint provider sees.
            Set<String> tagSubjects = new HashSet<>();
            for (Course c : Course.<Course>listAll()) {
                if (c.requiredRoomTags != null && c.requiredRoomTags.contains(tag)) {
                    tagSubjects.add(c.subject);
                }
            }
            if (tagSubjects.isEmpty()) {
                LOG.warnf("[HybridPhase1a] mono-room-tag: no Course requires tag '%s', skipping tag", tag);
                System.out.printf("TASK-449 %-16s | NO SUBJECTS (no Course requires tag) - skipped%n", tag);
                continue;
            }

            // Golden cadence per group for this tag, parsed ONCE from the classpath golden.
            Map<String, List<MonoRoomTagAllocator.Cell>> cadence = buildMonoRoomCadence(tag, tagSubjects);

            // Demand per group: count of unpinned lessons whose course requires this tag.
            // Tag membership (not isLabHour) because labBlockSize=0 in the 2026/27 seed.
            Map<String, Integer> demandByGroup = new LinkedHashMap<>();
            for (String groupName : cadence.keySet()) {
                StudentGroup group = groupByName.get(groupName);
                if (group == null) {
                    continue;
                }
                long count = Lesson.find(
                        "SELECT COUNT(l) FROM Lesson l JOIN l.course c "
                                + "WHERE l.pinned = false AND ?1 MEMBER OF c.requiredRoomTags AND c.studentGroup = ?2",
                        tag, group).count();
                demandByGroup.put(groupName, (int) count);
            }

            MonoRoomTagAllocator.Plan plan = MonoRoomTagAllocator.plan(
                    cadence, demandByGroup, 8, tag, teachingWeeks);
            for (String w : plan.warnings()) {
                LOG.debugf("[HybridPhase1a] mono-room-tag %s: %s", tag, w);
            }

            // Stamp: group the targets by group so each group's pool is queried once and shared
            // across its cells (the pool self-limits: once drained, further cells pin nothing).
            Map<String, List<MonoRoomTagAllocator.PinTarget>> byGroup = new LinkedHashMap<>();
            for (MonoRoomTagAllocator.PinTarget t : plan.targets()) {
                byGroup.computeIfAbsent(t.groupName(), g -> new ArrayList<>()).add(t);
            }

            int pinnedTag = 0;
            int skippedTag = 0;
            for (Map.Entry<String, List<MonoRoomTagAllocator.PinTarget>> entry : byGroup.entrySet()) {
                StudentGroup group = groupByName.get(entry.getKey());
                if (group == null) {
                    continue;
                }
                List<Lesson> pool = Lesson.find(
                        "SELECT l FROM Lesson l JOIN l.course c "
                                + "WHERE l.pinned = false AND ?1 MEMBER OF c.requiredRoomTags AND c.studentGroup = ?2 "
                                + "ORDER BY l.id",
                        tag, group).list();
                int poolIdx = 0;
                for (MonoRoomTagAllocator.PinTarget target : entry.getValue()) {
                    // School dates matching the target weekday (session window filtered by
                    // openSlotsForGroupDay, which respects the group calendar H20).
                    List<LocalDate> dates = new ArrayList<>();
                    for (LocalDate d : slotsByDate.keySet()) {
                        if (d.getDayOfWeek() == target.weekday()) {
                            dates.add(d);
                        }
                    }
                    Collections.sort(dates);

                    int placed = 0;
                    for (LocalDate date : dates) {
                        if (poolIdx >= pool.size()) {
                            break; // pool drained
                        }
                        List<Timeslot> openSlots = openSlotsForGroupDay(group, date,
                                slotsByDate.getOrDefault(date, List.of()));
                        // Stamp a consecutive block of `hours` slots starting at slotIndex.
                        for (int h = 0; h < target.hours() && poolIdx < pool.size(); h++) {
                            int wantSlot = target.slotIndex() + h;
                            Timeslot ts = null;
                            for (Timeslot s : openSlots) {
                                if (TimeslotSequence.slotIndexOf(s) == wantSlot) {
                                    ts = s;
                                    break;
                                }
                            }
                            if (ts == null) {
                                break; // slot closed/unavailable: block can't continue this date
                            }
                            // Skip lessons consumed by an earlier target/date this run.
                            while (poolIdx < pool.size()
                                    && (pool.get(poolIdx).pinned || pool.get(poolIdx).timeslot != null)) {
                                poolIdx++;
                            }
                            if (poolIdx >= pool.size()) {
                                break;
                            }
                            Lesson lesson = pool.get(poolIdx);
                            Room lessonRoom = findRoomForLesson(lesson);
                            if (lessonRoom == null || !validator.canPinHardConstraints(lesson, ts, lessonRoom)) {
                                skippedTag++;
                                break; // hard conflict: abandon this date's block (contiguity)
                            }
                            persistPinnedLesson(lesson, ts, lessonRoom);
                            pinnedTag++;
                            placed++;
                            poolIdx++;
                        }
                    }
                    LOG.infof("[HybridPhase1a] mono-room-tag %s %s %s slot %d: pinned %dh across dates",
                            tag, group.name, target.weekday(), target.slotIndex(), placed);
                }
            }

            int totalDemand = demandByGroup.values().stream().mapToInt(Integer::intValue).sum();
            int residualTag = Math.max(0, totalDemand - pinnedTag);
            System.out.printf("TASK-449 %-16s | pinned-with-room=%-5d skipped=%-5d residual=%-5d total=%d%n",
                    tag, pinnedTag, skippedTag, residualTag, totalDemand);
            LOG.infof("[HybridPhase1a] mono-room-tag %s: pinned=%d, skipped=%d, residual=%d, total=%d",
                    tag, pinnedTag, skippedTag, residualTag, totalDemand);
            totalPinned += pinnedTag;
            totalSkipped += skippedTag;
        }

        LOG.infof("[HybridPhase1a] Phase 0b done: %d pinned, %d skipped", totalPinned, totalSkipped);
        return new HybridPhase1aReport.PhaseResult(
                "0b-MONO-ROOM-TAG", true, 0, Duration.between(start, Instant.now()), totalPinned, null);
    }

    /**
     * Build the golden weekly cadence per group for one mono-room tag: parse {@code seed/lessons.csv},
     * keep only rows whose subject is in {@code tagSubjects}, and for each (group, weekday) derive
     * the modal block (the most common (startSlot, hours) shape across the golden's dates). Cells
     * are returned dominant-first (highest week-count, then weekday, then startSlot).
     */
    private Map<String, List<MonoRoomTagAllocator.Cell>> buildMonoRoomCadence(
            String tag, Set<String> tagSubjects) {
        // Columns: 0=date 1=day 2=slot 3=closed 4=group 5=teacher 6=subject
        List<String[]> rows = com.school.planner.util.SeedCsv.load(
                ConstrainedTeacherAllocator.GOLDEN_RESOURCE, ConstrainedTeacherAllocator.GOLDEN_COLUMNS);
        // Per (group, weekday): (startSlot, hours) -> week-count.
        Map<String, Map<DayOfWeek, Map<String, Integer>>> shapeCounts = new LinkedHashMap<>();
        // Track per (group, weekday, date) the sorted slot list, to compute block shapes.
        Map<String, Map<DayOfWeek, Map<LocalDate, List<Integer>>>> slotsByGroupWeekdayDate = new LinkedHashMap<>();
        for (String[] row : rows) {
            String subject = row[6];
            if (!tagSubjects.contains(subject)) {
                continue;
            }
            String group = row[4];
            LocalDate date = LocalDate.parse(row[0]);
            DayOfWeek weekday = date.getDayOfWeek();
            // Parse slot start "08:00 09:00" -> slotIndex via the shared slot-start table.
            String start = row[2].split(" ")[0];
            int slotIndex = slotIndexFromStart(start);
            if (slotIndex < 0) {
                continue;
            }
            slotsByGroupWeekdayDate
                    .computeIfAbsent(group, g -> new LinkedHashMap<>())
                    .computeIfAbsent(weekday, w -> new LinkedHashMap<>())
                    .computeIfAbsent(date, d -> new ArrayList<>())
                    .add(slotIndex);
        }
        // Collapse each (group, weekday, date) slot list into a block shape (startSlot, hours).
        for (Map.Entry<String, Map<DayOfWeek, Map<LocalDate, List<Integer>>>> groupEntry
                : slotsByGroupWeekdayDate.entrySet()) {
            String group = groupEntry.getKey();
            for (Map.Entry<DayOfWeek, Map<LocalDate, List<Integer>>> wdEntry
                    : groupEntry.getValue().entrySet()) {
                DayOfWeek weekday = wdEntry.getKey();
                for (List<Integer> slots : wdEntry.getValue().values()) {
                    Collections.sort(slots);
                    int startSlot = slots.get(0);
                    int hours = slots.size();
                    String shape = startSlot + "|" + hours;
                    shapeCounts
                            .computeIfAbsent(group, g -> new LinkedHashMap<>())
                            .computeIfAbsent(weekday, w -> new LinkedHashMap<>())
                            .merge(shape, 1, Integer::sum);
                }
            }
        }
        // Pick the modal shape per (group, weekday), emit cells dominant-first.
        Map<String, List<MonoRoomTagAllocator.Cell>> cadence = new LinkedHashMap<>();
        for (Map.Entry<String, Map<DayOfWeek, Map<String, Integer>>> groupEntry
                : shapeCounts.entrySet()) {
            String group = groupEntry.getKey();
            List<MonoRoomTagAllocator.Cell> cells = new ArrayList<>();
            for (Map.Entry<DayOfWeek, Map<String, Integer>> wdEntry
                    : groupEntry.getValue().entrySet()) {
                DayOfWeek weekday = wdEntry.getKey();
                Map<String, Integer> shapes = wdEntry.getValue();
                String modalShape = null;
                int modalCount = -1;
                for (Map.Entry<String, Integer> se : shapes.entrySet()) {
                    if (se.getValue() > modalCount) {
                        modalCount = se.getValue();
                        modalShape = se.getKey();
                    }
                }
                String[] parts = modalShape.split("\\|");
                int startSlot = Integer.parseInt(parts[0]);
                int hours = Integer.parseInt(parts[1]);
                cells.add(new MonoRoomTagAllocator.Cell(weekday, startSlot, hours));
            }
            // Dominant-first: highest block hours, then weekday ordinal, then startSlot.
            cells.sort((a, b) -> {
                int c = Integer.compare(b.hours(), a.hours());
                if (c != 0) {
                    return c;
                }
                c = a.weekday().compareTo(b.weekday());
                if (c != 0) {
                    return c;
                }
                return Integer.compare(a.slotIndex(), b.slotIndex());
            });
            cadence.put(group, cells);
        }
        return cadence;
    }

    /** Map a slot start time string ("08:00") to its 0-based slotIndex, or -1 if unknown. */
    private static int slotIndexFromStart(String start) {
        for (int i = 0; i < TimeslotSequence.SLOT_START_TIMES.length; i++) {
            if (TimeslotSequence.SLOT_START_TIMES[i].toString().equals(start)) {
                return i;
            }
        }
        return -1;
    }

    /** Core pin: set timeslot/room/pinned, persist, track in context. Relies on the caller's tx. */
    private void persistPinnedLesson(Lesson lesson, Timeslot timeslot, Room room) {
        lesson.timeslot = timeslot;
        lesson.room = room;
        lesson.pinned = true;
        if (lesson.id != null) {
            Lesson.getEntityManager().merge(lesson);
        } else {
            lesson.persist();
        }
        context.trackLesson(lesson);
    }

    /**
     * The calendar-open timeslots for a group on a given date (from a pre-bucketed day list, no DB
     * query), sorted earliest-first (slot 0 upward), respecting the group's
     * {@link SchoolSchedule#getGroupCalendarWindow} so a pin never lands on an H20 closed slot.
     * Empty if the day is closed for the group.
     */
    /** Package-private for the iter-12 regression test (statics-empty window fallback). */
    List<Timeslot> openSlotsForGroupDay(StudentGroup group, LocalDate date, List<Timeslot> daySlots) {
        // getGroupCalendarWindow returns [openSlotCount, minOpenSlot, maxOpenSlot];
        // a closed day uses the minOpenSlot<0 sentinel (see SchoolScheduleConstraintProvider
        // :789-792 and AnchorRepair :203-204 for the same convention).
        int[] window = SchoolSchedule.getGroupCalendarWindow(group.id, date.getDayOfWeek(), date);
        // TASK-542 iter-12: the calendar statics are EMPTY at pre-assign (the "SchoolSchedule
        // map is empty at pre-assign" note above openSlotsForGroupDay's callers), so the
        // window above silently reads as FULL (open slots=8) and pins landed on slots the
        // real calendar closes - measured: 8 authoritative pins T01/G06 on Thursday
        // 14:30-15:30 against a 0-5 window (H20 x8 in the -13 run). Resolve the group's
        // rules straight from the DB when the static map has nothing for it.
        if (SchoolSchedule.getGroupCalendarRulesForGroup(group.id).isEmpty() && group.id != null) {
            List<GroupCalendarRule> dbRules = GroupCalendarRule.find("studentGroup", group).list();
            if (!dbRules.isEmpty()) {
                int[] range = GroupCalendarService.effectiveRange(dbRules, date.getDayOfWeek(), date);
                if (range != null) {
                    boolean closed = range[1] < range[0];
                    window = new int[] {
                            GroupCalendarService.openSlotCountFromRange(range),
                            closed ? -1 : range[0],
                            closed ? -1 : range[1] };
                }
            }
        }
        int minSlot = window[1];
        int maxSlot = window[2];
        if (minSlot < 0) {
            return List.of(); // closed day for this group
        }
        return daySlots.stream()
                .filter(t -> {
                    int si = TimeslotSequence.slotIndexOf(t);
                    return si >= minSlot && si <= maxSlot;
                })
                .sorted(Comparator.comparingInt(TimeslotSequence::slotIndexOf))
                .collect(Collectors.toList());
    }

    /**
     * Run Phase 1 only (Acconciatura).
     * Useful for testing and incremental debugging.
     *
     * @return Phase result
     */
    public HybridPhase1aReport.PhaseResult runPhase1Only() {
        context.initialize();
        updateStatus(HybridStatus.RUNNING, "1-ACCONCIATURA");

        // H11: Build cache for this phase
        Set<Long> allGroupIds = getAllGroupIdsWithLessons();
        Map<Long, Map<LocalDate, Integer>> existingLessonCounts = buildExistingLessonCountsCache(allGroupIds);
        Map<Long, Map<LocalDate, Integer>> newLessonDates = new HashMap<>();

        var result = runPhase1(existingLessonCounts, newLessonDates);
        if (result.success) {
            updateStatus(HybridStatus.COMPLETE, null);
        } else {
            setErrorStatus("1-ACCONCIATURA", "Phase 1 failed");
        }
        return result;
    }

    /**
     * Run Phase 2 for a specific room tag only.
     * Useful for testing and incremental debugging.
     *
     * @param roomTag The room tag to process
     * @return Phase result
     */
    public HybridPhase1aReport.PhaseResult runPhase2Only(String roomTag) {
        context.initialize();
        updateStatus(HybridStatus.RUNNING, "2-LABS-" + roomTag);

        // H11: Build cache for this phase
        Set<Long> allGroupIds = getAllGroupIdsWithLessons();
        Map<Long, Map<LocalDate, Integer>> existingLessonCounts = buildExistingLessonCountsCache(allGroupIds);
        Map<Long, Map<LocalDate, Integer>> newLessonDates = new HashMap<>();

        var result = runPhase2ForRoomTag(roomTag, existingLessonCounts, newLessonDates);
        if (result.success) {
            updateStatus(HybridStatus.COMPLETE, null);
        } else {
            setErrorStatus("2-LABS-" + roomTag, "Phase 2 (" + roomTag + ") failed");
        }
        return result;
    }

    /**
     * Phase 1: Acconciatura lab rotation.
     *
     * <p>Each (teacher, group) pair has its own fixed day from LabRotation data.
     * External teachers (T28) are constrained by VACATION; internal teachers
     * (T26, T18) can have different days per group.</p>
     *
     * <p>Uses GreedyRotationAssigner with per-pair CSP backtracking.</p>
     *
     * <p>IMPORTANT: Supports PARTIAL success. If rotation-day capacity is exceeded,
     * as many blocks as possible are pinned; overflow blocks are left for the solver
     * to place on other days. This matches human planner behavior - rotation days
     * are for the primary lab component, not necessarily all hours.</p>
     */
    private HybridPhase1aReport.PhaseResult runPhase1(
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            Map<Long, Map<LocalDate, Integer>> newLessonDates) {
        String roomTag = "acconciatura";
        Instant start = Instant.now();
        LOG.infof("[HybridPhase1a] Starting Phase 1: %s (using Lessons with isLabHour=true)", roomTag);

        // Load LabSessions directly from Lessons (TASK-228: LabBlocks no longer exist)
        Map<BlockKey, List<LabSession>> sessionsByKey = loadLabSessionsByRoomTag(roomTag);
        if (sessionsByKey.isEmpty()) {
            LOG.infof("[HybridPhase1a] No lab lessons found for roomTag=%s, skipping", roomTag);
            return createEmptyPhaseResult("1-ACCONCIATURA", roomTag, start);
        }

        LOG.infof("[HybridPhase1a] Loaded %d session groups for Phase 1", sessionsByKey.size());

        // Build test configuration
        RotationTestRunner.TestConfig config = buildTestConfig(roomTag);

        // TASK-502 follow-up (round 3-BOXED) forma (a), Phase 2 teacher-budget guard
        // (ANNUAL, opt-in prototype): drop this phase's lab blocks of any (teacher, group)
        // pair whose pinning would strand the teacher's NON-lab pool on the same group in
        // the weekdays the lab consumes. The solver keeps placing what we decline.
        sessionsByKey = applyTeacherBudgetGuard(roomTag, sessionsByKey);

        // TASK-467 AC#4 wiring (opt-in, default OFF): when armed, the rotation-day PLAN
        // comes from the multi-candidate enumerator (input-driven ranking, top-k) instead
        // of the greedy's first-found assignment: k ranked plans are tried in order, each
        // as a restriction of the SAME backtracking, and the first feasible one wins.
        // Slot finding, validation and pinning are UNCHANGED (the restricted overload only
        // intersects the per-pair valid days). Phase 1 only: the enumerator's measured
        // scope is the acconciatura family (Phase 2 labs are a different, unmeasured
        // rotation structure). On the 2026/27 seed Phase 1 is structurally skipped
        // upstream (LabRotation.count()==0, TASK-364), so the lever fires only when
        // rotations are configured and is inert otherwise (logged, not silent).
        // TASK-516: publish reservations ONCE before either path (idempotent, but
        // hoisted so an enumerator-miss fallback does not re-run the DB scan).
        sessionsByKey = applyBudgetReservations(sessionsByKey);
        GreedyRotationAssigner.AssignmentResult result = null;
        if (RotationEnumerator.preAssignEnabled()) {
            result = assignWithEnumeratorPlans(roomTag, sessionsByKey, config,
                    existingLessonCounts, newLessonDates);
        }
        if (result == null) {
            result = greedyAssigner.assignTeachers(
                roomTag, sessionsByKey, config, existingLessonCounts, newLessonDates);
        }
        currentCandidatesTried = result.candidatesTried();

        // Pin pre-computed slot assignments (no redundant re-search needed)
        int pinned = pinTeacherDayAssignment(result);

        // Count total sessions to pin
        int totalSessions = sessionsByKey.values().stream().mapToInt(List::size).sum();

        // Consider success if:
        // 1. We pinned at least SOME sessions, OR
        // 2. There were no sessions to assign
        // This allows partial success - overflow sessions go to solver
        boolean phaseSuccess = pinned > 0 || totalSessions == 0;

        if (phaseSuccess) {
            if (pinned < totalSessions) {
                LOG.infof("[HybridPhase1a] Phase 1 PARTIAL success: pinned %d/%d sessions after %d candidates (overflow to solver)",
                    pinned, totalSessions, result.candidatesTried());
            } else {
                LOG.infof("[HybridPhase1a] Phase 1 succeeded with CSP backtracking after %d candidates, pinned %d entities",
                    result.candidatesTried(), pinned);
            }

            return new HybridPhase1aReport.PhaseResult(
                "1-ACCONCIATURA", true, result.candidatesTried(),
                Duration.between(start, Instant.now()), pinned, roomTag
            );
        }

        // Complete failure - couldn't pin ANY blocks
        LOG.errorf("[HybridPhase1a] Phase 1 failed after %d candidates - could not pin any blocks", result.candidatesTried());
        if (result.analysis() != null) {
            lastFailureAnalysis = result.analysis();  // Store for API access
            LOG.errorf("[HybridPhase1a] %s", result.analysis().toFormattedString());
        } else {
            LOG.errorf("[HybridPhase1a] No failure analysis available");
        }
        return new HybridPhase1aReport.PhaseResult(
            "1-ACCONCIATURA", false, result.candidatesTried(),
            Duration.between(start, Instant.now()), 0, roomTag
        );
    }

    /**
     * Phase 2: Other labs (moda, termoidraulica, meccanica, informatica).
     *
     * <p>Each roomTag is processed sequentially. Phase 1 pins are already
     * in the context, so conflicts will be detected.</p>
     *
     * <p>Uses GreedyRotationAssigner with CSP backtracking instead of
     * Cartesian product enumeration.</p>
     *
     * <p>IMPORTANT: Phase 2 is MORE LENIENT than Phase 1. For moda/meccanica/etc.,
     * teachers don't always have fixed rotation days. We try to pin what we can,
     * but if nothing fits, we DON'T fail - the solver will handle all blocks.</p>
     */
    private HybridPhase1aReport.PhaseResult runPhase2ForRoomTag(
            String roomTag,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            Map<Long, Map<LocalDate, Integer>> newLessonDates) {
        Instant start = Instant.now();
        LOG.infof("[HybridPhase1a] Starting Phase 2: %s (using Lessons with isLabHour=true)", roomTag);

        // Load LabSessions directly from Lessons (TASK-228: LabBlocks no longer exist)
        Map<BlockKey, List<LabSession>> sessionsByKey = loadLabSessionsByRoomTag(roomTag);
        if (sessionsByKey.isEmpty()) {
            LOG.infof("[HybridPhase1a] No lab lessons found for roomTag=%s, skipping", roomTag);
            return createEmptyPhaseResult("2-LABS-" + roomTag, roomTag, start);
        }
        LOG.infof("[HybridPhase1a] Loaded %d session groups for Phase 2 (%s)", sessionsByKey.size(), roomTag);

        // Build test configuration
        RotationTestRunner.TestConfig config = buildTestConfig(roomTag);

        // TASK-516 (opt-in, default OFF): reserve H7 headroom for the pair's
        // quota-bound lab demand this phase is NOT pinning (the probe's cure).
        sessionsByKey = applyBudgetReservations(sessionsByKey);

        // Use CSP backtracking instead of Cartesian product
        GreedyRotationAssigner.AssignmentResult result = greedyAssigner.assignTeachers(
            roomTag, sessionsByKey, config, existingLessonCounts, newLessonDates);
        currentCandidatesTried = result.candidatesTried();

        // Pin pre-computed slot assignments (no redundant re-search needed)
        int pinned = pinTeacherDayAssignment(result);

        // Count total sessions to pin
        int totalSessions = sessionsByKey.values().stream().mapToInt(List::size).sum();

        // Phase 2 is ALWAYS considered success - even if we pinned nothing.
        // The solver will handle all sessions that couldn't be pre-assigned.
        // This is different from Phase 1 where we need at least some pins to succeed.
        boolean phaseSuccess = true;

        if (pinned < totalSessions) {
            if (pinned == 0) {
                LOG.infof("[HybridPhase1a] Phase 2 (%s) NO sessions pinned (all %d will be handled by solver)",
                    roomTag, totalSessions);
            } else {
                LOG.infof("[HybridPhase1a] Phase 2 (%s) PARTIAL success: pinned %d/%d sessions after %d candidates (overflow to solver)",
                    roomTag, pinned, totalSessions, result.candidatesTried());
            }
        } else {
            LOG.infof("[HybridPhase1a] Phase 2 (%s) succeeded with CSP backtracking after %d candidates, pinned %d entities",
                roomTag, result.candidatesTried(), pinned);
        }

        return new HybridPhase1aReport.PhaseResult(
                "2-LABS-" + roomTag, true, result.candidatesTried(),
                Duration.between(start, Instant.now()), pinned, roomTag
            );
    }

    /** Teacher entities for the current guard call (cached per applyTeacherBudgetGuard). */
    private static final Map<Long, Teacher> teacherById = new HashMap<>();

    /**
     * ONE teacher's pinned hours per weekday, all their groups combined (the residual's
     * subtrahend). Run5 caught the bug this fixes: the first version merged EVERY
     * teacher's pins into one map, so each pair's residual subtracted the whole school's
     * load (~2500h/weekday) and the intersection collapsed to 0 for everyone: the guard
     * declined ALL six acconciatura pairs it should have judged individually.
     */
    private static Map<DayOfWeek, Integer> teacherPinnedByWeekday(long teacherId) {
        Map<DayOfWeek, Integer> load = new EnumMap<>(DayOfWeek.class);
        for (Lesson l : Lesson.<Lesson>find("pinned = true and timeslot is not null").list()) {
            if (l.course == null || l.course.teacher == null || l.timeslot == null
                    || l.course.teacher.id != teacherId) {
                continue;
            }
            load.merge(l.timeslot.getDate().getDayOfWeek(), 1, Integer::sum);
        }
        return load;
    }

    private static Map<DayOfWeek, Integer> labWeekdayHours(BlockKey pair, List<LabSession> sessions) {
        Map<DayOfWeek, Integer> hours = new EnumMap<>(DayOfWeek.class);
        for (LabSession sess : sessions) {
            DayOfWeek wd = sess.labRotation != null && sess.labRotation.dayOfWeek != null
                    ? sess.labRotation.dayOfWeek : null;
            if (wd != null) {
                hours.merge(wd, sess.lessons.size(), Integer::sum);
            }
        }
        return hours;
    }

    /**
     * TASK-502 follow-up: filter the Phase 2 session map through the ANNUAL teacher-budget
     * guard. For each (teacher, group) pair: non-lab pool = the teacher's course hours on
     * the group that are NOT lab-tagged; lab pool = this phase's session hours; weekday
     * capacity = the group's calendar window size per weekday minus the already-pinned
     * load, scaled over the teaching weeks. A pair is DECLINED (whole, logged) when
     * pinning its lab pool would leave the non-lab pool without enough residual slots.
     * Declining is improve-or-leave for feasibility: the solver places what we decline.
     */
    private Map<BlockKey, List<LabSession>> applyTeacherBudgetGuard(
            String roomTag, Map<BlockKey, List<LabSession>> sessionsByKey) {
        if (!teacherBudgetEnabled() || sessionsByKey.isEmpty()) {
            return sessionsByKey;
        }
        teacherById.clear();
        Map<Long, StudentGroup> groupById = new HashMap<>();
        for (List<LabSession> sessions : sessionsByKey.values()) {
            for (LabSession sess : sessions) {
                if (sess.course != null && sess.course.studentGroup != null) {
                    groupById.putIfAbsent(sess.course.studentGroup.id, sess.course.studentGroup);
                }
                if (sess.course != null && sess.course.teacher != null) {
                    teacherById.putIfAbsent(sess.course.teacher.id, sess.course.teacher);
                }
            }
        }
        // One representative week's free slots per (group, weekday): calendar window minus
        // the weekly share of the pinned load already sitting on that weekday.
        Map<Long, Map<DayOfWeek, Integer>> freeByGroupWeekday = new HashMap<>();
        Map<Long, Map<DayOfWeek, Integer>> pinnedLoad = new HashMap<>();
        for (Lesson l : Lesson.<Lesson>find("pinned = true and timeslot is not null").list()) {
            if (l.course == null || l.course.studentGroup == null || l.timeslot == null) {
                continue;
            }
            long gid = l.course.studentGroup.id;
            pinnedLoad.computeIfAbsent(gid, g -> new EnumMap<>(DayOfWeek.class))
                    .merge(l.timeslot.getDate().getDayOfWeek(), 1, Integer::sum);
        }
        for (StudentGroup g : groupById.values()) {
            Map<DayOfWeek, Integer> perWeekday = new EnumMap<>(DayOfWeek.class);
            for (DayOfWeek wd : DayOfWeek.values()) {
                if (wd == DayOfWeek.SATURDAY || wd == DayOfWeek.SUNDAY) {
                    continue;
                }
                List<GroupCalendarRule> rules = GroupCalendarRule
                        .<GroupCalendarRule>find("studentGroup", g).list();
                // date-scoped rules cannot apply without a date: filter to the weekday's
                // date-INDEPENDENT rules (effectiveRange NPEs on a null date when any
                // rule carries effectiveFrom/To; run4 hit exactly that on G13).
                int window = 8;
                GroupCalendarRule best = null;
                for (GroupCalendarRule rule : rules) {
                    if (rule.dayOfWeek != wd || rule.effectiveFrom != null || rule.effectiveTo != null) {
                        continue;
                    }
                    if (best == null || (rule.maxSlot - rule.minSlot) < (best.maxSlot - best.minSlot)) {
                        best = rule;
                    }
                }
                if (best != null) {
                    window = Math.max(0, best.maxSlot - best.minSlot + 1);
                }
                int load = pinnedLoad.getOrDefault(g.id, Map.of()).getOrDefault(wd, 0) / WeeksInYear;
                perWeekday.put(wd, Math.max(0, window - load));
            }
            freeByGroupWeekday.put(g.id, perWeekday);
        }
        Map<BlockKey, Integer> nonLabByPair = new HashMap<>();
        for (Course c : Course.<Course>listAll()) {
            if (c.teacher == null || c.studentGroup == null || c.isLab) {
                continue;
            }
            nonLabByPair.merge(new BlockKey(c.teacher.id, c.studentGroup.id), c.totalHours, Integer::sum);
        }
        Map<BlockKey, List<LabSession>> filtered = new LinkedHashMap<>();
        int declinedPairs = 0;
        int declinedSessions = 0;
        for (Map.Entry<BlockKey, List<LabSession>> e : sessionsByKey.entrySet()) {
            int labHours = e.getValue().stream().mapToInt(sess -> sess.lessons.size()).sum();
            Integer nonLab = nonLabByPair.get(e.getKey());
            StudentGroup g = groupById.get(e.getKey().studentGroupId());
            if (g == null || nonLab == null) {
                filtered.put(e.getKey(), e.getValue());
                continue;
            }
            Map<DayOfWeek, Integer> free = freeByGroupWeekday.get(g.id);
            // per-weekday min(group spare, teacher residual after this pin), annual:
            // groupSpare_i = (window_i - groupPinned_i/weeks) * weeks; teacherResid_i =
            // cap_i*weeks - teacherPinnedEverywhere_i - (lab hours this pin adds to i,
            // concentrated on the pair's lab weekdays = its rotation days)
            Map<DayOfWeek, Integer> teacherPinned = teacherPinnedByWeekday(e.getKey().teacherId());
            Map<DayOfWeek, Integer> labWeekdays = labWeekdayHours(e.getKey(), e.getValue());
            Map<DayOfWeek, Integer> intersection = new EnumMap<>(DayOfWeek.class);
            for (DayOfWeek wd : free.keySet()) {
                int cap = teacherById.getOrDefault(e.getKey().teacherId(),
                        Teacher.<Teacher>findById(e.getKey().teacherId())).getEffectiveMaxDailyHours();
                int groupSpare = free.get(wd) * WeeksInYear;
                int teacherResid = cap * WeeksInYear
                        - teacherPinned.getOrDefault(wd, 0)
                        - labWeekdays.getOrDefault(wd, 0);
                intersection.put(wd, Math.max(0, Math.min(groupSpare, teacherResid)));
            }
            if (!teacherBudgetAllowsPin(labHours, 0, nonLab, 0, intersection)) {
                declinedPairs++;
                declinedSessions += e.getValue().size();
                LabSession any = e.getValue().get(0);
                LOG.infof("[HybridPhase1a] TASK-502 teacher-budget guard: DECLINED %s/%s %s"
                                + " (lab %dh would strand %dh non-lab; free/week %s)",
                        any.course != null && any.course.teacher != null
                                ? any.course.teacher.name : e.getKey().teacherId(),
                        g.name, roomTag, labHours, nonLab, free);
                continue;
            }
            filtered.put(e.getKey(), e.getValue());
        }
        if (declinedPairs > 0) {
            LOG.infof("[HybridPhase1a] TASK-502 teacher-budget guard (%s): declined %d pair(s),"
                    + " %d session(s) left to the solver", roomTag, declinedPairs, declinedSessions);
        }
        return filtered;
    }

    /**
     * TASK-516 (opt-in, default OFF): publish the phase's H7 reservations to the
     * shared {@link PreAssignmentContext} the greedy gate reads. Called before
     * EVERY assignTeachers invocation (phase 1 greedy/enumerator, phase 2 per
     * roomTag): each phase's pin population defines its own non-enumerated demand,
     * so the map is REPLACED, never merged. Disabled explicitly clears, so an
     * unarmed phase after an armed one cannot inherit stale reservations.
     */
    private Map<BlockKey, List<LabSession>> applyBudgetReservations(
            Map<BlockKey, List<LabSession>> sessionsByKey) {
        if (!BudgetReservation.enabled()) {
            context.setH7BudgetReservations(Map.of());
            return sessionsByKey;
        }
        var reservations = BudgetReservation.computeForPhase(sessionsByKey);
        context.setH7BudgetReservations(reservations);
        // TASK-518 plan sizing: trim trailing sessions when the pair's plan exceeds
        // the NET budget ((cap - reservation) x labDays, the demand included).
        return BudgetReservation.applyPlanSizingTo(sessionsByKey, reservations);
    }

    /**
     * Phase 3: Professionalizzanti on stage days.
     *
     * <p>Places professional subjects on stage days (students on stage earn
     * equivalent credits). Fundamentals are blocked on these days by H4.</p>
     *
     * <p>This phase doesn't use rotation candidates - it's a deterministic
     * placement following the human planner's heuristic.</p>
     */
    private HybridPhase1aReport.PhaseResult runPhase3() {
        Instant start = Instant.now();
        LOG.info("[HybridPhase1a] Starting Phase 3: Professionalizzanti");

        // Load all professional subjects that need assignment
        List<Lesson> professionalLessons = Lesson.find(
            "course.professionalizzanti = true and timeslot is null"
        ).list();

        if (professionalLessons.isEmpty()) {
            LOG.info("[HybridPhase1a] No professional lessons to assign, skipping Phase 3");
            return createEmptyPhaseResult("3-PROFESSIONALIZZANTI", null, start);
        }

        LOG.infof("[HybridPhase1a] Found %d professional lessons to assign", professionalLessons.size());

        // Load stage patterns for stage day detection
        List<StagePattern> stagePatterns = StagePattern.listAll();
        Set<Long> groupsWithStagePatterns = stagePatterns.stream()
            .map(sp -> sp.studentGroup.id)
            .collect(Collectors.toSet());
        Set<LocalDate> holidays = loadHolidays();

        // Compute ideal end dates for all groups (critical constraint: never place lessons after this date)
        // Use centralized method to build ideal end dates map
        Set<Long> allGroupIds = new LinkedHashSet<>(groupsWithStagePatterns);
        Map<Long, LocalDate> idealEndDatesByGroup = targetEndDateCalculator.buildIdealEndDatesMap(allGroupIds);

        // Log the computed dates for debugging
        for (var entry : idealEndDatesByGroup.entrySet()) {
            StudentGroup group = StudentGroup.findById(entry.getKey());
            if (group != null && group.idealEndDateInfo != null) {
                LOG.infof("[HybridPhase1a] Group %s: idealEndDate = %s (totalHours=%d)",
                    group.name, group.idealEndDateInfo.idealEndDate(),
                    group.idealEndDateInfo.totalHours());
            }
        }

        // H11: Build cache of existing pinned lesson counts per group per date
        // This avoids repeated database queries during Phase 3
        System.out.println("=== H11-DEBUG: About to call buildExistingLessonCountsCache ===");
        Map<Long, Map<LocalDate, Integer>> existingLessonCounts = buildExistingLessonCountsCache(groupsWithStagePatterns);
        System.out.println("=== H11-DEBUG: buildExistingLessonCountsCache RETURNED, cache size=" + existingLessonCounts.size() + " ===");

        // H11: Track daily lesson counts to enforce maxHoursPerDay
        // Maps: groupId -> (date -> lessonCount)
        // Combined with existing counts, this gives total lessons per day
        Map<Long, Map<LocalDate, Integer>> newLessonDates = new HashMap<>();

        // DEBUG H11: Log initial state for groups with maxHoursPerDay=5
        LOG.infof("[H11-DEBUG] Phase 3 starting - logging existing lesson counts for groups with maxHoursPerDay=5");
        for (Long groupId : groupsWithStagePatterns) {
            StudentGroup g = StudentGroup.findById(groupId);
            if (g != null && g.maxHoursPerDay != null && g.maxHoursPerDay == 5) {
                Map<LocalDate, Integer> groupCounts = existingLessonCounts.getOrDefault(groupId, Collections.emptyMap());
                LOG.infof("[H11-DEBUG] Group %s (maxHoursPerDay=5): existing lessons on %d dates",
                    g.name, groupCounts.size());
                for (Map.Entry<LocalDate, Integer> entry : groupCounts.entrySet()) {
                    if (entry.getValue() >= 5) {
                        LOG.warnf("[H11-DEBUG]   Date %s: %d existing lessons (AT OR OVER LIMIT!)",
                            entry.getKey(), entry.getValue());
                    } else {
                        LOG.infof("[H11-DEBUG]   Date %s: %d existing lessons",
                            entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        // TASK-279: Day-by-day rotation - process by date instead of by course
        // Each professional day should have 1 lesson from each subject, rotating through subjects
        // Group lessons by (group, course) for efficient round-robin processing
        Map<Long, Map<Long, List<Lesson>>> lessonsByGroupAndCourse = professionalLessons.stream()
            .collect(Collectors.groupingBy(l -> l.course.studentGroup.id,
                Collectors.groupingBy(l -> l.course.id)));

        // Track remaining hours per course for round-robin selection
        // Map: groupId -> courseId -> remaining lesson count
        Map<Long, Map<Long, Integer>> remainingHoursByGroupAndCourse = new HashMap<>();
        for (Map.Entry<Long, Map<Long, List<Lesson>>> groupEntry : lessonsByGroupAndCourse.entrySet()) {
            Long groupId = groupEntry.getKey();
            Map<Long, Integer> courseRemaining = new HashMap<>();
            for (Map.Entry<Long, List<Lesson>> courseEntry : groupEntry.getValue().entrySet()) {
                courseRemaining.put(courseEntry.getKey(), courseEntry.getValue().size());
            }
            remainingHoursByGroupAndCourse.put(groupId, courseRemaining);
        }

        // Track which courses assigned on each date (max 1 lesson per course per day)
        // Map: groupId -> date -> set of courseIds assigned today
        Map<Long, Map<LocalDate, Set<Long>>> assignedCoursesByDate = new HashMap<>();

        LOG.infof("[HybridPhase1a] Phase 3: %d groups with professional lessons for day-by-day rotation",
            lessonsByGroupAndCourse.size());

        int pinned = 0;
        int skipped = 0;
        int noStagePattern = 0;
        int noProfessionalDay = 0;

        // Process each group independently with date-first round-robin
        for (Map.Entry<Long, Map<Long, List<Lesson>>> groupEntry : lessonsByGroupAndCourse.entrySet()) {
            Long groupId = groupEntry.getKey();
            StudentGroup group = StudentGroup.findById(groupId);

            if (group == null) {
                skipped += groupEntry.getValue().values().stream().mapToInt(List::size).sum();
                continue;
            }

            // Skip lessons from groups without stage patterns (e.g., first-year students)
            if (!groupsWithStagePatterns.contains(group.id)) {
                noStagePattern += groupEntry.getValue().values().stream().mapToInt(List::size).sum();
                continue;
            }

            // A stage group WITHOUT a ProfessionalizzantiDay row also has nothing to
            // pin: getProfessionalDatesInRange deliberately returns an empty list ("no
            // professional day configured"), the solver places those hours freely.
            // Latent-bug fix (TASK-364 chain smoke, 2026-08-16): the success predicate
            // below used to count these as candidates, so ANY run of Phase 3 on a seed
            // with zero profDay rows (the 2026/27 seed) reported INFEASIBLE even with
            // nothing to do. Expected-empty, same family as noStagePattern.
            if (ProfessionalizzantiDay.find("studentGroup", group).firstResult() == null) {
                noProfessionalDay += groupEntry.getValue().values().stream().mapToInt(List::size).sum();
                continue;
            }

            // Check if group should skip stage day placement
            List<Lesson> groupLessons = groupEntry.getValue().values().stream()
                .flatMap(List::stream).collect(Collectors.toList());
            if (!groupLessons.isEmpty()) {
                Lesson probeLesson = groupLessons.get(0);
                if (StageEligibilityUtil.shouldSkipStageDay(probeLesson.course, group)) {
                    LOG.infof("[HybridPhase1a] Skipping %s: allowProfessionalizzantiDuringStage=%s",
                        group.name, group.allowProfessionalizzantiDuringStage);
                    skipped += groupLessons.size();
                    continue;
                }
            }

            // Get all professional dates for this group and round-robin through them
            List<LocalDate> professionalDates = getProfessionalDatesInRange(group, stagePatterns, holidays, idealEndDatesByGroup.get(group.id));
            LOG.infof("[HybridPhase1a] Group %s: found %d professional dates for day-by-day rotation",
                group.name, professionalDates.size());

            // Cache professional day configuration for this group (queried once per group)
            DayOfWeek expectedDay = null;
            ProfessionalizzantiDay profDayConfig = ProfessionalizzantiDay.find("studentGroup", group).firstResult();
            if (profDayConfig != null && profDayConfig.dayOfWeek != null) {
                expectedDay = profDayConfig.dayOfWeek;
            }

            // Initialize assigned courses tracking for this group (lazy init per date)
            assignedCoursesByDate.put(group.id, new HashMap<>());

            // Pre-fetch all courses for this group to avoid N+1 queries during sorting
            List<Long> courseIds = new ArrayList<>(groupEntry.getValue().keySet());
            Map<Long, Course> courseCache = new HashMap<>();
            for (Long courseId : courseIds) {
                Optional<Course> opt = Course.findByIdOptional(courseId);
                opt.ifPresent(c -> courseCache.put(courseId, c));
            }

            // Sort courses by subject name using cached Course objects
            courseIds.sort((c1, c2) -> {
                Course course1 = courseCache.get(c1);
                Course course2 = courseCache.get(c2);
                if (course1 == null || course2 == null) return 0;
                return course1.subject.compareTo(course2.subject);
            });

            // Round-robin through courses on each date
            int courseIndex = 0;
            int totalCourses = courseIds.size();
            boolean anyPlaced = false;

            for (LocalDate date : professionalDates) {
                // H11: Initial check - skip dates already at maxHoursPerDay from Phases 1-2
                // Note: The real incremental H11 enforcement happens inside the placement loop
                if (wouldExceedDailyLimit(group, date, existingLessonCounts, newLessonDates)) {
                    LOG.debugf("[HybridPhase1a] Group %s: date %s already at maxHoursPerDay limit, skipping",
                        group.name, date);
                    continue;
                }

                // Find available slots on this date (pass cached expectedDay to avoid redundant query)
                List<Timeslot> availableSlots = findAvailableSlotsForDate(
                    date, expectedDay, holidays, idealEndDatesByGroup.get(group.id));

                if (availableSlots.isEmpty()) {
                    LOG.debugf("[HybridPhase1a] Group %s: no available slots on date %s",
                        group.name, date);
                    continue;
                }

                // Round-robin through courses, placing at most 1 lesson per course per day
                int slotsUsed = 0;
                int attempts = 0;
                int maxAttempts = totalCourses * 2; // Prevent infinite loop

                while (slotsUsed < availableSlots.size() && attempts < maxAttempts) {
                    Long courseId = courseIds.get(courseIndex);
                    Integer remaining = remainingHoursByGroupAndCourse.get(group.id).getOrDefault(courseId, 0);

                    if (remaining <= 0) {
                        // No more lessons for this course, move to next
                        courseIndex = (courseIndex + 1) % totalCourses;
                        attempts++;
                        continue;
                    }

                    // Check if this course already assigned on this date (safe lazy init)
                    Set<Long> coursesAssignedToday = assignedCoursesByDate.get(group.id).computeIfAbsent(date, k -> new HashSet<>());
                    if (coursesAssignedToday.contains(courseId)) {
                        courseIndex = (courseIndex + 1) % totalCourses;
                        attempts++;
                        continue;
                    }

                    // Find a lesson from this course that hasn't been placed yet
                    Optional<Lesson> lessonOpt = groupEntry.getValue().get(courseId).stream()
                        .filter(l -> l.timeslot == null)
                        .findFirst();

                    if (lessonOpt.isEmpty()) {
                        // All lessons of this course are placed
                        remainingHoursByGroupAndCourse.get(group.id).put(courseId, 0);
                        courseIndex = (courseIndex + 1) % totalCourses;
                        attempts++;
                        continue;
                    }

                    Lesson lesson = lessonOpt.get();
                    Timeslot slot = availableSlots.get(slotsUsed);

                    // Verify slot is still valid (check conflicts)
                    Room room = findRoomForLesson(lesson);
                    if (room != null && isValidSlot(lesson, room, slot)) {
                        // H11: Check if placing this lesson would exceed maxHoursPerDay (incremental check)
                        // This ensures each lesson placement is validated against the running total
                        if (wouldExceedDailyLimit(group, date, existingLessonCounts, newLessonDates)) {
                            LOG.debugf("[HybridPhase1a] Group %s: date %s reached maxHoursPerDay during placement, stopping",
                                group.name, date);
                            break;  // Exit placement loop for this date
                        }

                        // Pin the lesson
                        RotationTestRunner.SlotAssignment assignment = new RotationTestRunner.SlotAssignment(
                            new BlockKey(lesson.getTeacher().id, group.id),
                            null, slot, room, List.of(slot)
                        );
                        pinLesson(lesson, assignment);

                        // Track for H11 daily limit
                        newLessonDates.computeIfAbsent(group.id, k -> new HashMap<>())
                            .merge(date, 1, Integer::sum);

                        // Mark course as assigned on this date (lazy init)
                        assignedCoursesByDate.get(group.id).computeIfAbsent(date, k -> new HashSet<>()).add(courseId);

                        // Decrement remaining hours
                        remainingHoursByGroupAndCourse.get(group.id).put(courseId, remaining - 1);

                        pinned++;
                        slotsUsed++;
                        anyPlaced = true;

                        int slotIndex = slot.startDateTime.getHour() - 8; // 08:00 = slot 0
                        LOG.debugf("[HybridPhase1a] Group %s: placed %s on %s (slot %d, courseIndex=%d, remaining=%d)",
                            group.name, lesson.course.subject, date, slotIndex, courseIndex, remaining - 1);
                    } else {
                        // Slot not valid for this lesson, try next course
                        int slotIndex = slot.startDateTime.getHour() - 8; // 08:00 = slot 0
                        LOG.debugf("[HybridPhase1a] Group %s: slot %d on %s not valid for %s, trying next course",
                            group.name, slotIndex, date, lesson.course.subject);
                    }

                    courseIndex = (courseIndex + 1) % totalCourses;
                    attempts++;
                }

                // Break if all lessons are placed
                int totalRemaining = remainingHoursByGroupAndCourse.get(group.id).values().stream()
                    .mapToInt(Integer::intValue).sum();
                if (totalRemaining == 0) {
                    LOG.infof("[HybridPhase1a] Group %s: all %d professional lessons placed",
                        group.name, groupEntry.getValue().values().stream().mapToInt(List::size).sum());
                    break;
                }
            }

            if (!anyPlaced) {
                LOG.warnf("[HybridPhase1a] Group %s: no professional lessons could be assigned",
                    group.name);
            }
        }

        Duration duration = Duration.between(start, Instant.now());

        if (pinned > 0) {
            LOG.infof("[HybridPhase1a] Phase 3 completed: pinned %d professional lessons, skipped %d, no-stage-pattern %d, no-prof-day %d in %s",
                pinned, skipped, noStagePattern, noProfessionalDay, duration);
        } else if (noStagePattern + noProfessionalDay > 0 && skipped == 0) {
            LOG.infof("[HybridPhase1a] Phase 3: all %d professional lessons are from groups without stage patterns (%d) or without a declared professional day (%d), this is expected",
                noStagePattern + noProfessionalDay, noStagePattern, noProfessionalDay);
        } else {
            LOG.warnf("[HybridPhase1a] Phase 3: no professional lessons could be assigned (skipped %d, no-stage-pattern %d, no-prof-day %d)",
                skipped, noStagePattern, noProfessionalDay);
        }

        // Phase 3 is successful if:
        // 1. We pinned at least some lessons, OR
        // 2. All lessons are from groups without stage patterns (first-year - no internship), OR
        // 3. All lessons are from groups without a declared professional day (nothing to pin by configuration), OR
        // 4. There were no lessons to assign
        int candidateLessons = professionalLessons.size() - noStagePattern - noProfessionalDay;
        boolean success = pinned > 0 || candidateLessons == 0 || professionalLessons.isEmpty();

        return new HybridPhase1aReport.PhaseResult(
            "3-PROFESSIONALIZZANTI", success, 1, // 1 "candidate" (deterministic)
            duration, pinned, null
        );
    }

    /**
     * Build a cache of existing pinned lesson counts per group per date.
     * This is used for H11 enforcement to avoid querying the database repeatedly.
     *
     * H11 T28 EXCEPTION: External teacher lessons don't count toward daily limit.
     * We exclude them from the cache to match solver behavior.
     *
     * @return Map of groupId -> (date -> lessonCount)
     */
    private Map<Long, Map<LocalDate, Integer>> buildExistingLessonCountsCache(Set<Long> groupIds) {
        System.out.println("=== H11-DEBUG: buildExistingLessonCountsCache START, groups=" + groupIds.size() + " ===");
        Map<Long, Map<LocalDate, Integer>> cache = new HashMap<>();

        // Use native SQL aggregation instead of JPA to avoid eager loading overhead
        // This is MUCH faster - we only need counts per group per date, not full entity graphs
        for (Long groupId : groupIds) {
            System.out.println("=== H11-DEBUG: Processing group ID=" + groupId + " ===");

            // Native SQL query to count lessons by date, excluding external teacher lessons
            List<Object[]> results = entityManager.createNativeQuery(
                "SELECT CAST(ts.startDateTime AS DATE), COUNT(l.id) " +
                "FROM lesson l " +
                "JOIN course c ON l.course_id = c.id " +
                "JOIN timeslot ts ON l.timeslot_id = ts.id " +
                "WHERE c.studentgroup_id = :groupId " +
                "  AND l.pinned = true " +
                "  AND (c.teacher_id IS NULL OR EXISTS (SELECT 1 FROM teacher t WHERE t.id = c.teacher_id AND t.external = false)) " +
                "GROUP BY CAST(ts.startDateTime AS DATE)")
                .setParameter("groupId", groupId)
                .getResultList();

            System.out.println("=== H11-DEBUG: Group " + groupId + " returned " + results.size() + " date entries ===");

            Map<LocalDate, Integer> dateCounts = new HashMap<>();
            for (Object[] row : results) {
                LocalDate date = (LocalDate) row[0];
                Long count = (Long) row[1];
                dateCounts.put(date, count.intValue());
            }
            cache.put(groupId, dateCounts);
        }

        System.out.println("=== H11-DEBUG: buildExistingLessonCountsCache COMPLETE, cache size=" + cache.size() + " ===");
        return cache;
    }

    /**
     * Get all group IDs that have lessons to assign.
     * Used for H11 cache building across all phases.
     *
     * @return Set of group IDs with unpinned lessons
     */
    private Set<Long> getAllGroupIdsWithLessons() {
        List<Long> groupIds = entityManager.createNativeQuery(
            "SELECT DISTINCT c.studentgroup_id FROM lesson l " +
            "JOIN course c ON l.course_id = c.id " +
            "WHERE l.timeslot_id IS NULL")
            .getResultList();
        return new HashSet<>(groupIds);
    }

    /**
     * Check if placing a SINGLE lesson on the given date would exceed the group's maxHoursPerDay.
     * Combines existing pinned lessons with lessons already placed in this phase.
     *
     * <p><strong>Use case:</strong> Phase 3 (professionalizzanti) places individual lessons
     * one at a time, so single-lesson validation is appropriate.</p>
     *
     * <p><strong>For multi-hour block validation:</strong> Use the overloaded method that includes
     * the {@code blockSize} parameter to validate the entire block upfront.</p>
     *
     * @param group The student group
     * @param date The date to check
     * @param existingLessonCounts Existing pinned lessons per group per date
     * @param newLessonDates Lessons placed in this phase per group per date
     * @return true if placing another lesson would exceed maxHoursPerDay
     */
    boolean wouldExceedDailyLimit(
            StudentGroup group,
            LocalDate date,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            Map<Long, Map<LocalDate, Integer>> newLessonDates) {

        // Groups with no maxHoursPerDay limit are unconstrained
        if (group.maxHoursPerDay == null || group.maxHoursPerDay == 0) {
            return false;
        }

        int existingCount = existingLessonCounts.getOrDefault(group.id, Collections.emptyMap())
            .getOrDefault(date, 0);
        int newCount = newLessonDates.getOrDefault(group.id, Collections.emptyMap())
            .getOrDefault(date, 0);
        int totalHours = existingCount + newCount;

        // Check if we've already reached the limit (not just would exceed)
        return totalHours >= group.maxHoursPerDay;
    }

    /**
     * Check if placing a BLOCK of lessons would exceed the group's maxHoursPerDay (H11 constraint).
     *
     * <p><strong>Block-aware validation:</strong> This overload includes the {@code blockSize} parameter
     * to validate entire multi-lesson blocks upfront, preventing partial placement that would
     * exceed the daily limit.</p>
     *
     * <p><strong>Use case:</strong> Phase 1/2 (acconciatura, labs) place multi-hour blocks
     * and must validate the entire block before placement. Without this, a 2-hour block placed
     * at slot 12 (12:00-13:00) for a 5-hour group would exceed the limit at slot 13 (13:00-14:00).</p>
     *
     * <p><strong>Example (G12 Informatica bug):</strong></p>
     * <ul>
     *   <li>Group: G12 with maxHoursPerDay=5 (valid slots 0-4: 08:00-13:00)</li>
     *   <li>Bug: 2-hour blocks placed at slots 12-13 (12:00-14:00)</li>
     *   <li>Slot 12 (12:00-13:00): 5th hour, within limit ✓</li>
     *   <li>Slot 13 (13:00-14:00): 6th hour, EXCEEDS LIMIT ✗</li>
     * </ul>
     *
     * @param group The student group
     * @param date The date to check
     * @param existingLessonCounts Existing pinned lessons per group per date
     * @param newLessonDates Lessons placed in this phase per group per date
     * @param blockSize The number of lessons in the block being placed
     * @return true if placing this block would exceed maxHoursPerDay
     */
    boolean wouldExceedDailyLimit(
            StudentGroup group,
            LocalDate date,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            Map<Long, Map<LocalDate, Integer>> newLessonDates,
            int blockSize) {

        // Groups with no maxHoursPerDay limit are unconstrained
        if (group.maxHoursPerDay == null || group.maxHoursPerDay == 0) {
            return false;
        }

        int existingCount = existingLessonCounts.getOrDefault(group.id, Collections.emptyMap())
            .getOrDefault(date, 0);
        int newCount = newLessonDates.getOrDefault(group.id, Collections.emptyMap())
            .getOrDefault(date, 0);
        int totalHours = existingCount + newCount + blockSize;

        // Check if placing this block would exceed the limit
        return totalHours > group.maxHoursPerDay;
    }

    /**
     * Load LabSessions from Lessons (replaces LabBlock-based loading after TASK-228).
     *
     * <p>Queries Lessons with isLabHour=true and pinned=false, groups them by
     * (teacherId, studentGroupId), and creates LabSession wrappers. Also assigns
     * labRotation based on matching teacher, group, and period.</p>
     */
    @Transactional
    public Map<BlockKey, List<LabSession>> loadLabSessionsByRoomTag(String roomTag) {
        // Find lab hour lessons whose course requires this room tag
        // NOTE: Includes professionalizzanti courses - spalmone shows these follow
        // regular rotation patterns (e.g., Lavaggio on Wednesdays for G10), not exclusively
        // stage-based placement. Phase 1 handles rotation-based placement for all courses
        // with room tags, while Phase 3 adds additional professional hours on stage days
        // when allowProfessionalizzantiDuringStage=true.
        List<Lesson> labLessons = Lesson.find(
            "SELECT l FROM Lesson l JOIN l.course c " +
            "WHERE l.isLabHour = true AND l.pinned = false AND ?1 MEMBER OF c.requiredRoomTags",
            roomTag
        ).list();

        LOG.infof("[HybridPhase1a] Found %d unpinned lab lessons for roomTag=%s", labLessons.size(), roomTag);

        // Load all LabRotations for this roomTag, ordered by period start
        List<LabRotation> rotations = LabRotation.find(
            "roomTag = ?1 ORDER BY periodStart", roomTag).list();

        LOG.infof("[HybridPhase1a] Found %d LabRotations for roomTag=%s", rotations.size(), roomTag);

        // Load holidays for accurate slot counting
        Set<LocalDate> holidays = loadHolidays();

        // Group lessons by (teacherId, studentGroupId, courseId) to form sessions
        Map<Long, Map<Long, Map<Long, List<Lesson>>>> lessonsByTeacherGroupCourse = new LinkedHashMap<>();
        for (Lesson lesson : labLessons) {
            Teacher teacher = lesson.getTeacher();
            StudentGroup group = lesson.getStudentGroup();
            Course course = lesson.course;
            if (teacher != null && group != null && course != null) {
                lessonsByTeacherGroupCourse
                    .computeIfAbsent(teacher.id, k -> new LinkedHashMap<>())
                    .computeIfAbsent(group.id, k -> new LinkedHashMap<>())
                    .computeIfAbsent(course.id, k -> new ArrayList<>())
                    .add(lesson);
            }
        }

        // Group rotations by (teacherId, studentGroupId) for proportional distribution
        Map<Long, Map<Long, List<LabRotation>>> rotationsByTeacherAndGroup = new LinkedHashMap<>();
        for (LabRotation rotation : rotations) {
            if (rotation.teacher != null && rotation.studentGroup != null) {
                rotationsByTeacherAndGroup
                    .computeIfAbsent(rotation.teacher.id, k -> new LinkedHashMap<>())
                    .computeIfAbsent(rotation.studentGroup.id, k -> new ArrayList<>())
                    .add(rotation);
            }
        }

        // Result: sessions grouped by BlockKey (teacherId, studentGroupId)
        Map<BlockKey, List<LabSession>> result = new LinkedHashMap<>();

        // For each teacher-group-course combination, create LabSessions
        // IMPORTANT: Use lesson.labTotalHours (from course.labBlockSize) as session size
        for (Map.Entry<Long, Map<Long, Map<Long, List<Lesson>>>> teacherEntry : lessonsByTeacherGroupCourse.entrySet()) {
            Long teacherId = teacherEntry.getKey();

            for (Map.Entry<Long, Map<Long, List<Lesson>>> groupEntry : teacherEntry.getValue().entrySet()) {
                Long groupId = groupEntry.getKey();
                BlockKey key = new BlockKey(teacherId, groupId);

                // Find matching rotations for this teacher-group pair
                Map<Long, List<LabRotation>> groupRotations = rotationsByTeacherAndGroup.get(teacherId);
                List<LabRotation> rotationsForPair = (groupRotations != null)
                    ? groupRotations.get(groupId)
                    : null;
                for (Map.Entry<Long, List<Lesson>> courseEntry : groupEntry.getValue().entrySet()) {
                    List<Lesson> courseLessons = courseEntry.getValue();
                    if (courseLessons.isEmpty()) continue;

                    Course course = courseLessons.get(0).course;

                    // Get session size from lesson's labTotalHours (set by LessonGenerator from course.labBlockSize)
                    // This preserves the original lab definition from the admin page
                    int blockSize = courseLessons.get(0).labTotalHours;
                    if (blockSize <= 0) {
                        blockSize = 6; // Fallback (shouldn't happen for lab lessons)
                    }

                    // Sort lessons by ID to preserve LessonGenerator insertion order.
                    // LessonGenerator creates consecutive (h0, h1, ..., hN-1) pairs,
                    // so sorting by ID ensures sessions contain the correct block partners.
                    // Sorting by labHourIndex would group all h0 together, breaking blocks.
                    courseLessons.sort(Comparator.comparingLong(l -> l.id));

                    // Pre-filter rotations by subject prefix (constant per course)
                    List<LabRotation> subjectRotations = filterBySubjectPrefix(rotationsForPair, course.subject);

                    // Split lessons into daily sessions of blockSize each
                    int totalLessons = courseLessons.size();
                    int sessionCount = (totalLessons + blockSize - 1) / blockSize; // Ceiling division

                    for (int i = 0; i < totalLessons; i += blockSize) {
                        int endIndex = Math.min(i + blockSize, totalLessons);
                        List<Lesson> sessionLessons = courseLessons.subList(i, endIndex);

                        // Create a LabSession for this day's lessons
                        LabSession session = new LabSession(course);
                        for (Lesson lesson : sessionLessons) {
                            session.lessons.add(lesson);
                        }
                        session.totalHours = sessionLessons.size();

                        // Assign rotation: round-robin across matching rotations,
                        // or single rotation if only one matches.
                        // subjectPrefix=null matches all courses (legacy behavior).
                        int sessionIndex = i / blockSize;
                        if (!subjectRotations.isEmpty() && subjectRotations.size() > 1) {
                            session.labRotation = subjectRotations.get(sessionIndex % subjectRotations.size());
                        } else {
                            session.labRotation = !subjectRotations.isEmpty()
                                ? subjectRotations.get(0) : null;
                        }

                        result.computeIfAbsent(key, k -> new ArrayList<>()).add(session);
                    }

                    if (sessionCount > 1) {
                        LOG.debugf("[HybridPhase1a] Split %d lessons into %d sessions of blockSize=%d for course %s",
                            totalLessons, sessionCount, blockSize, course.subject);
                    }
                }
            }
        }

        LOG.infof("[HybridPhase1a] Created %d LabSession groups for roomTag=%s", result.size(), roomTag);

        // Log sessions without rotation
        int sessionsWithoutRotation = 0;
        for (List<LabSession> sessions : result.values()) {
            for (LabSession session : sessions) {
                if (session.labRotation == null) {
                    sessionsWithoutRotation++;
                }
            }
        }
        if (sessionsWithoutRotation > 0) {
            LOG.infof("[HybridPhase1a] %d sessions without labRotation — will use CSP placement",
                sessionsWithoutRotation);
        }

        return result;
    }


    // Note: LabBlock-based methods (loadBlocksByRoomTag, labBlockToLabSession, convertToLabSessions)
    // were removed in TASK-228 - all labs are now pinned Lessons

    /**
     * Build TestConfig from database for the given roomTag.
     */
    private RotationTestRunner.TestConfig buildTestConfig(String roomTag) {
        LocalDate periodStart = getPeriodStart();
        LocalDate periodEnd = getPeriodEnd();
        return RotationTestRunner.TestConfig.fromDatabase(periodStart, periodEnd, roomTag);
    }

    /**
     * Get period start from LabRotation records (earliest periodStart).
     *
     * <p>LabRotation is intentionally empty for 2026/27 (seeds deferred to
     * TASK-364), so this ALWAYS falls back to
     * TimeslotSequence.SCHOOL_YEAR_START (the single source of truth for the
     * school-year bounds - see that field's javadoc) so pre-assignment
     * room-tag rotation testing (buildTestConfig(), and the direct callers
     * below) uses the correct school-year bounds instead of silently reusing
     * a stale literal.</p>
     */
    private LocalDate getPeriodStart() {
        LabRotation earliest = LabRotation.find("order by periodStart").firstResult();
        if (earliest != null && earliest.periodStart != null) {
            return earliest.periodStart;
        }
        // Default: start of the current school year
        return TimeslotSequence.SCHOOL_YEAR_START;
    }

    /**
     * Get period end from LabRotation records (latest periodEnd). See
     * getPeriodStart() javadoc - same TASK-343 fallback rationale.
     */
    private LocalDate getPeriodEnd() {
        LabRotation latest = LabRotation.find("order by periodEnd desc").firstResult();
        if (latest != null && latest.periodEnd != null) {
            return latest.periodEnd;
        }
        // Default: end of the current school year
        return TimeslotSequence.SCHOOL_YEAR_END;
    }

    /**
     * Load all school holidays as a Set of dates.
     */
    private Set<LocalDate> loadHolidays() {
        Set<LocalDate> holidays = new HashSet<>();
        List<SchoolHoliday> holidayEntities = SchoolHoliday.listAll();
        for (SchoolHoliday h : holidayEntities) {
            if (h.date != null) {
                holidays.add(h.date);
            }
        }
        return holidays;
    }

    /**
     * Count actual available slots for a rotation, accounting for holidays.
     *
     * <p>This method counts how many occurrences of the rotation's dayOfWeek
     * exist within the rotation period, excluding school holidays.</p>
     *
     * @param rotation The lab rotation
     * @param holidays Set of holiday dates
     * @return Number of available slots
     */
    private int countAvailableSlots(LabRotation rotation, Set<LocalDate> holidays) {
        if (rotation.periodStart == null || rotation.periodEnd == null || rotation.dayOfWeek == null) {
            return 0;
        }

        int count = 0;
        LocalDate date = rotation.periodStart;

        // Find first occurrence of the target day
        while (date.getDayOfWeek() != rotation.dayOfWeek && !date.isAfter(rotation.periodEnd)) {
            date = date.plusDays(1);
        }

        // Count all occurrences of the target day, excluding holidays
        while (!date.isAfter(rotation.periodEnd)) {
            if (!holidays.contains(date)) {
                count++;
            }
            date = date.plusWeeks(1);
        }

        return count;
    }

    /**
     * Pin LabSession assignments to the database.
     * Each session contains multiple lessons that need to be pinned.
     *
     * @param assignments Successful assignments from test runner
     * @return Number of lessons pinned
     */
    @Transactional
    int pinAssignments(List<RotationTestRunner.SlotAssignment> assignments) {
        int count = 0;
        for (RotationTestRunner.SlotAssignment assignment : assignments) {
            LabSession session = assignment.session();
            List<Timeslot> occupiedSlots = assignment.occupiedTimeslots();
            Room room = assignment.room();

            // Verify slot count matches lesson count
            if (session.lessons.size() != occupiedSlots.size()) {
                LOG.warnf("Session lesson count (%d) != occupied slots count (%d), skipping assignment",
                    session.lessons.size(), occupiedSlots.size());
                continue;
            }

            // Pin each lesson in the session to its corresponding timeslot
            for (int i = 0; i < session.lessons.size(); i++) {
                Lesson lesson = session.lessons.get(i);
                lesson.timeslot = occupiedSlots.get(i);
                lesson.room = room;
                lesson.pinned = true;

                // Persist changes
                if (lesson.id != null) {
                    Lesson.getEntityManager().merge(lesson);
                } else {
                    lesson.persist();
                }

                // Update context so next phase sees this pin
                context.trackLesson(lesson);
                count++;
            }
        }
        return count;
    }

    /**
     * TASK-467 AC#4: try the enumerator's ranked rotation plans against the UNCHANGED greedy
     * backtracking. Builds one {@link com.school.planner.solver.RotationEnumerator.RotationTeacher}
     * per teacher from the phase's own session data (budgets = summed session hours, weekday
     * cells = the LabRotation rows the sessions carry, blockSize/slot from the same rows),
     * enumerates top-k timelines per teacher, and tries plan after plan: plan j restricts each
     * (teacher, group) PAIR to the weekday set where the j-th ranked timeline hosts that group
     * (per PAIR, not per teacher: the union of weekdays is invariant across candidates, only
     * differing per-group weekday SETS distinguish plan j from plan j-1; candidates that
     * differ only by period permutation collapse to the same restriction and are deduped).
     * The first plan whose restricted
     * backtracking succeeds wins; a plan that contradicts a pair's valid days fails fast
     * (never silently drops the pair); when every ranked plan fails (or the enumeration caps
     * out) the caller falls back to the plain greedy, so the FALLBACK branch can never do
     * worse than today's single candidate. An ADOPTED plan rides the greedy's
     * partial-success semantics (a pair unplaceable at slot level is skipped, as in the
     * unarmed baseline): the restriction only narrows domains, so an adoption A/B must
     * compare pinned-session counts too, not just hard/KPI (see the task's VERDICT).
     * Returns {@code null} to signal "fall back".
     */
    private GreedyRotationAssigner.AssignmentResult assignWithEnumeratorPlans(
            String roomTag,
            Map<RotationTestRunner.BlockKey, List<LabSession>> sessionsByKey,
            RotationTestRunner.TestConfig config,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            Map<Long, Map<LocalDate, Integer>> newLessonDates) {
        int topK = RotationEnumerator.topK();
        int weeksPerPeriod = RotationEnumerator.weeksPerPeriod();
        int maxPeriods = RotationEnumerator.maxPeriods();

        Map<Long, RotationEnumerator.RotationTeacher> teachersById = buildEnumeratorTeachers(sessionsByKey);
        if (teachersById.isEmpty()) {
            LOG.infof("[HybridPhase1a] TASK-467: no rotation-bearing sessions for roomTag=%s - greedy path",
                    roomTag);
            return null;
        }
        // Group lab days across ALL teachers (the enumerator's ranking input): the distinct
        // LabRotation weekdays each group appears on, teacher-agnostic.
        Map<String, Set<DayOfWeek>> labDaysByGroup = new TreeMap<>();
        for (List<LabSession> sessions : sessionsByKey.values()) {
            for (LabSession s : sessions) {
                if (s.labRotation != null && s.labRotation.dayOfWeek != null
                        && s.course != null && s.course.studentGroup != null) {
                    labDaysByGroup.computeIfAbsent(s.course.studentGroup.name,
                            g -> EnumSet.noneOf(DayOfWeek.class)).add(s.labRotation.dayOfWeek);
                }
            }
        }
        // Group name -> id, the inverse of the ONE names map (built in the shared walk
        // below, same pass buildEnumeratorTeachers reads). A collision (two ids, same
        // name) would silently bind the WRONG pair: logged loudly instead.
        Map<String, Long> groupIdByName = new TreeMap<>();
        groupNamesById(sessionsByKey).forEach((id, name) -> {
            Long prev = groupIdByName.putIfAbsent(name, id);
            if (prev != null && !prev.equals(id)) {
                LOG.warnf("[HybridPhase1a] TASK-467: group name '%s' maps to ids %d AND %d"
                        + " - restricting only the first", name, prev, id);
            }
        });
        // Per teacher: the ranked timelines, each PRE-CONVERTED to its per-pair restriction
        // (a timeline's restriction depends only on teacherId + timeline + groupIdByName,
        // so converting once here beats re-deriving it on every plan iteration).
        // The restriction is PER PAIR (the weekday SET where each group sits in that
        // timeline): the union-of-weekdays is invariant across candidates (every complete
        // timeline uses every weekday cell), so only differing per-group weekday SETS
        // distinguish plan j from plan j-1. Two caveats kept honest: candidates that
        // differ only by PERIOD permutation collapse to the same restriction (deduped
        // below), and a pair already pinned to one weekday by its own rotation rows is
        // bound by nothing (the restriction either contains that weekday or the plan
        // fails fast).
        Map<Long, List<Map<RotationTestRunner.BlockKey, Set<DayOfWeek>>>> restrictionsByTeacher = new TreeMap<>();
        for (Map.Entry<Long, RotationEnumerator.RotationTeacher> e : teachersById.entrySet()) {
            var ranked = RotationEnumerator.enumerateTopK(e.getValue(), labDaysByGroup,
                    maxPeriods, weeksPerPeriod, topK);
            LOG.infof("[HybridPhase1a] TASK-467 teacher %s: %d ranked timeline(s)%s",
                    e.getValue().name(), ranked.candidates().size(),
                    ranked.capped() ? " (CAPPED: raise maxPeriods or lower budgets)" : "");
            restrictionsByTeacher.put(e.getKey(), ranked.candidates().stream()
                    .map(st -> restrictionFromTimeline(e.getKey(), st.timeline(), groupIdByName))
                    .collect(Collectors.toList()));
        }
        // Plan j: every teacher's j-th ranked restriction (fewer candidates -> reuse the
        // last). Pairs whose group never appears in the timeline stay UNRESTRICTED (absent
        // from the map), never dropped: the plan cannot make a pair infeasible that it
        // simply ignores. Identical restrictions are skipped (a duplicate re-runs a
        // deterministic backtracking on the same input for the same result).
        int plans = restrictionsByTeacher.values().stream().mapToInt(List::size).max().orElse(0);
        Set<Map<RotationTestRunner.BlockKey, Set<DayOfWeek>>> tried = new HashSet<>();
        for (int j = 0; j < plans; j++) {
            Map<RotationTestRunner.BlockKey, Set<DayOfWeek>> restriction = new HashMap<>();
            for (Map.Entry<Long, List<Map<RotationTestRunner.BlockKey, Set<DayOfWeek>>>> e
                    : restrictionsByTeacher.entrySet()) {
                if (e.getValue().isEmpty()) {
                    continue;
                }
                restriction.putAll(e.getValue().get(Math.min(j, e.getValue().size() - 1)));
            }
            if (restriction.isEmpty() || !tried.add(restriction)) {
                continue;
            }
            LOG.infof("[HybridPhase1a] TASK-467: trying enumerator plan %d/%d (rank %d, %d pair(s) restricted)",
                    j + 1, plans, j + 1, restriction.size());
            GreedyRotationAssigner.AssignmentResult r = greedyAssigner.assignTeachers(
                    roomTag, sessionsByKey, config, existingLessonCounts, newLessonDates, restriction);
            if (r.success()) {
                LOG.infof("[HybridPhase1a] TASK-467: plan %d/%d FEASIBLE - using it (candidates tried %d)",
                        j + 1, plans, r.candidatesTried());
                return r;
            }
            LOG.infof("[HybridPhase1a] TASK-467: plan %d/%d infeasible (%s) - next",
                    j + 1, plans, r.failureReason());
        }
        LOG.infof("[HybridPhase1a] TASK-467: no ranked plan feasible - greedy fallback");
        return null;
    }

    /**
     * TASK-502 follow-up (round 3-BOXED), forma (a): Phase 2's teacher-budget guard, the
     * ANNUAL mirror of the greedy's daily teacherCap. Pure and unit-testable: a
     * (teacher, group) pair's lab block may be pinned only if the teacher's NON-lab
     * hours on the SAME group remain placeable in the weekdays the lab pins will NOT
     * consume. Round 3 pinned 436/476 of T15's lab hours onto the very weekdays
     * where G04 had quota-spare for his 50h RA course: the 20 boxed were that H1
     * self-conflict, not a search failure. Inputs are plain maps so the rule is
     * testable without a DB.
     *
     * @param labBlockHours   the hours this single pin would add to the pair
     * @param alreadyPinned   the pair's already-pinned lab hours (this phase, prior blocks)
     * @param nonLabHours     the teacher's NON-lab hours on the same group (the course pool)
     * @param capacityPerWeekday slots available per weekday for the group (window size)
     * @param availableWeekdaySlots per-weekday capacity still free BEFORE this pin
     * @return true when the pin leaves the non-lab pool placeable in the remaining slots
     */
    static boolean teacherBudgetAllowsPin(int labBlockHours, int alreadyPinned, int nonLabHours,
            int capacityPerWeekday, java.util.Map<DayOfWeek, Integer> availableWeekdaySlots) {
        if (!teacherBudgetEnabled()) {
            return true; // prototype off: today's behavior, byte-identical
        }
        if (nonLabHours <= 0) {
            return true; // nothing beyond the lab to protect
        }
        // ROUND 4-PREVIEW refinement: the intersection form. The annual aggregate
        // (round 4) counted free slots in the WRONG weekdays (G04's Monday spare is
        // real but the RA lives where group-spare AND teacher-residual overlap). Here
        // the caller passes PER-WEEKDAY the pair's usable capacity: min(group spare on
        // the weekday, teacher residual slots on the weekday AFTER the proposed pin),
        // already scaled over the year. The pin is declined when the SUM of the
        // per-weekday minima cannot host the non-lab pool.
        int intersection = 0;
        for (Integer perWeekday : availableWeekdaySlots.values()) {
            intersection += Math.max(0, perWeekday);
        }
        return nonLabHours <= intersection;
    }

    /** The guard's flag: {@code planner.pre-assign.teacher-budget.enabled}, opt-in. */
    public static final String TEACHER_BUDGET_FLAG = "planner.pre-assign.teacher-budget.enabled";

    /** Number of teaching weeks in the 2026/27 grid (school days / 5, rounded down). */
    static final int WeeksInYear = 34;

    static boolean teacherBudgetEnabled() {
        return Boolean.parseBoolean(System.getProperty(TEACHER_BUDGET_FLAG));
    }

    /**
     * Group id -> name from this phase's own sessions (ONE walk: both the enumerator
     * teachers and the plan loop's name-to-id inverse derive from it, so their null
     * guards cannot diverge).
     */
    private static Map<Long, String> groupNamesById(
            Map<RotationTestRunner.BlockKey, List<LabSession>> sessionsByKey) {
        Map<Long, String> names = new TreeMap<>();
        for (Map.Entry<RotationTestRunner.BlockKey, List<LabSession>> e : sessionsByKey.entrySet()) {
            for (LabSession s : e.getValue()) {
                if (s.course != null && s.course.studentGroup != null
                        && s.course.studentGroup.name != null) {
                    names.putIfAbsent(e.getKey().studentGroupId(), s.course.studentGroup.name);
                }
            }
        }
        return names;
    }

    /** One RotationTeacher per phase teacher, built from the sessions' own LabRotation rows. */
    private static Map<Long, RotationEnumerator.RotationTeacher> buildEnumeratorTeachers(
            Map<RotationTestRunner.BlockKey, List<LabSession>> sessionsByKey) {
        Map<Long, String> teacherNameById = new TreeMap<>();
        Map<Long, String> groupNameById = groupNamesById(sessionsByKey);
        Map<Long, List<RotationEnumerator.RotationGroup>> groupsById = new TreeMap<>();
        Map<Long, Set<DayOfWeek>> daysById = new TreeMap<>();
        Map<Long, int[]> blockSizeSlotById = new TreeMap<>();
        for (Map.Entry<RotationTestRunner.BlockKey, List<LabSession>> e : sessionsByKey.entrySet()) {
            long teacherId = e.getKey().teacherId();
            int budget = 0;
            for (LabSession s : e.getValue()) {
                budget += s.totalHours;
                if (s.labRotation != null && s.labRotation.dayOfWeek != null) {
                    daysById.computeIfAbsent(teacherId, t -> EnumSet.noneOf(DayOfWeek.class))
                            .add(s.labRotation.dayOfWeek);
                    blockSizeSlotById.computeIfAbsent(teacherId,
                            t -> new int[] { s.labRotation.blockSize, s.labRotation.slotStart });
                }
                if (s.course != null && s.course.teacher != null) {
                    teacherNameById.put(teacherId, s.course.teacher.name);
                }
            }
            if (budget > 0) {
                groupsById.computeIfAbsent(teacherId, t -> new ArrayList<>())
                        .add(new RotationEnumerator.RotationGroup(
                                groupNameById.get(e.getKey().studentGroupId()), budget));
            }
        }
        Map<Long, RotationEnumerator.RotationTeacher> out = new TreeMap<>();
        for (Map.Entry<Long, List<RotationEnumerator.RotationGroup>> e : groupsById.entrySet()) {
            int[] block = blockSizeSlotById.get(e.getKey());
            if (block == null || daysById.get(e.getKey()) == null
                    || teacherNameById.get(e.getKey()) == null) {
                continue;   // no rotation rows: not this lever's population
            }
            out.put(e.getKey(), new RotationEnumerator.RotationTeacher(
                    teacherNameById.get(e.getKey()), null,
                    List.copyOf(RotationEnumerator.sortedWeekdays(daysById.get(e.getKey()))),
                    e.getValue(), block[0], block[1]));
        }
        return out;
    }

    /**
     * The plan restriction one ranked timeline contributes: per PAIR (teacher, group) the
     * weekday set where the timeline hosts that group (the enumerator's own shared
     * {@link RotationEnumerator#daysByGroup} walk, translated from names to BlockKeys).
     * Package-private: unit-testable without a DB. Group names the caller cannot resolve
     * to an id are skipped (they are not this phase's pairs); a pair the timeline never
     * hosts stays absent = unrestricted.
     */
    static Map<RotationTestRunner.BlockKey, Set<DayOfWeek>> restrictionFromTimeline(long teacherId,
            RotationEnumerator.RotationTimeline timeline, Map<String, Long> groupIdByName) {
        Map<RotationTestRunner.BlockKey, Set<DayOfWeek>> pairDays = new HashMap<>();
        RotationEnumerator.daysByGroup(timeline).forEach((groupName, weekdays) -> {
            Long gid = groupIdByName.get(groupName);
            if (gid != null) {
                pairDays.put(new RotationTestRunner.BlockKey(teacherId, gid),
                        EnumSet.copyOf(weekdays));
            }
        });
        return pairDays;
    }

    /**
     * Pin pre-computed slot assignments from GreedyRotationAssigner.
     *
     * <p>The AssignmentResult now carries pre-computed SlotAssignments, eliminating
     * the redundant second-pass slot-finding that the old pinTeacherDayAssignment did.</p>
     *
     * @param assignmentResult The result from GreedyRotationAssigner (carries slot assignments)
     * @return Number of entities pinned
     */
    @Transactional
    int pinTeacherDayAssignment(GreedyRotationAssigner.AssignmentResult assignmentResult) {
        if (assignmentResult == null || !assignmentResult.success()
                || assignmentResult.slotAssignments().isEmpty()) {
            return 0;
        }

        return pinAssignments(assignmentResult.slotAssignments());
    }

    /**
     * Pin a single Lesson assignment (timeslot + room from a pre-computed slot).
     */
    @Transactional
    void pinLesson(Lesson lesson, RotationTestRunner.SlotAssignment slot) {
        persistPinnedLesson(lesson, slot.startTimeslot(), slot.room());
    }

    /**
     * Find a valid slot for a professional lesson on a stage day.
     *
     * @param lesson The lesson to place
     * @param group The student group
     * @param stagePatterns All stage patterns
     * @param holidays School holidays
     * @param existingLessonCounts Existing pinned lessons per group per date
     * @param newLessonDates Lessons placed in this phase per group per date
     * @return Optional SlotAssignment if found
     */
    private Optional<RotationTestRunner.SlotAssignment> findStageDaySlot(
            Lesson lesson, StudentGroup group,
            List<StagePattern> stagePatterns, Set<LocalDate> holidays,
            Map<Long, Map<LocalDate, Integer>> existingLessonCounts,
            Map<Long, Map<LocalDate, Integer>> newLessonDates,
            Map<Long, LocalDate> idealEndDatesByGroup) {

        Teacher teacher = lesson.getTeacher();
        if (teacher == null) {
            return Optional.empty();
        }

        // Find stage patterns for this group
        List<StagePattern> groupPatterns = stagePatterns.stream()
            .filter(sp -> sp.studentGroup != null && sp.studentGroup.id.equals(group.id))
            .collect(Collectors.toList());

        if (groupPatterns.isEmpty()) {
            return Optional.empty();
        }

        // Find a room for this lesson
        Room room = findRoomForLesson(lesson);
        if (room == null) {
            return Optional.empty();
        }

        // Check if there's a designated professionalizzanti day for this group
        DayOfWeek expectedDay = null;
        ProfessionalizzantiDay profDay = ProfessionalizzantiDay.find("studentGroup", group).firstResult();
        if (profDay != null && profDay.dayOfWeek != null) {
            expectedDay = profDay.dayOfWeek;
            LOG.infof("[HybridPhase1a] Group %s has ProfessionalizzantiDay: %s (getValue=%d, from record id=%d)",
                group.name, expectedDay, expectedDay.getValue(), profDay.id);
        } else {
            LOG.debugf("[HybridPhase1a] Group %s has no ProfessionalizzantiDay (profDay=%s, dayOfWeek=%s)",
                group.name, profDay, profDay != null ? profDay.dayOfWeek : "N/A");
        }

        // TASK-268: If no professionalizzanti day is designated, skip this group entirely
        // Groups without a designated day should not have lessons placed on stage days
        // This prevents lessons from being scattered across all stage days when the group
        // should only have lessons on specific days (or none at all)
        if (expectedDay == null) {
            return Optional.empty();
        }

        // Iterate through stage days looking for a valid slot
        LocalDate date = getPeriodStart();
        LocalDate end = getPeriodEnd();
        
        LOG.infof("[HybridPhase1a] findStageDaySlot for group %s, lesson %s: period %s to %s, expectedDay=%s",
            group.name, lesson.course.subject, date, end, expectedDay);

        while (!date.isAfter(end)) {
            // Skip holidays
            if (holidays.contains(date)) {
                date = date.plusDays(1);
                continue;
            }

            // IDEAL END DATE constraint: never place lessons strictly after group's ideal end date
            LocalDate idealEndDate = idealEndDatesByGroup.get(group.id);
            if (idealEndDate != null && date.isAfter(idealEndDate)) {
                LOG.infof("[HybridPhase1a] Group %s: date %s is after idealEndDate %s, stopping placement",
                    group.name, date, idealEndDate);
                break;  // No need to check later dates - they're all beyond ideal end
            }

            // If a professionalizzanti day is designated, only look at that specific day
            // Otherwise, look for stage days (days when students attend school during stage periods)
            boolean isValidDay = (expectedDay != null)
                ? date.getDayOfWeek().equals(expectedDay)
                : isStageDay(date, groupPatterns);

            if (!isValidDay) {
                date = date.plusDays(1);
                continue;
            }

            // H11: Check daily hour limit before checking slots on this day
            if (wouldExceedDailyLimit(group, date, existingLessonCounts, newLessonDates)) {
                LOG.infof("[HybridPhase1a] Group %s: date %s would exceed maxHoursPerDay (%s), trying next date",
                    group.name, date, group.maxHoursPerDay);
                date = date.plusDays(1);
                continue;
            }

            // Debug: log first valid day found
            LOG.infof("[HybridPhase1a] Group %s lesson %s: checking date %s (dayOfWeek=%s, expectedDay=%s)",
                group.name, lesson.course.subject, date, date.getDayOfWeek(), expectedDay);

            // Try each slot on this day
            LocalTime[] slotTimes = SlotCandidate.getSlotTimes(date.getDayOfWeek());
            for (int slotIndex = 0; slotIndex < slotTimes.length; slotIndex++) {
                LocalDateTime start = LocalDateTime.of(date, slotTimes[slotIndex]);
                Timeslot ts = Timeslot.find("startDateTime", start).firstResult();

                if (ts != null && isValidSlot(lesson, room, ts)) {
                    // Found a valid slot!
                    return Optional.of(new RotationTestRunner.SlotAssignment(
                        new BlockKey(teacher.id, group.id),
                        null, // No LabBlock for lessons
                        ts,
                        room,
                        List.of(ts)
                    ));
                }
            }
            date = date.plusDays(1);
        }

        return Optional.empty();
    }

    /**
     * Check if a date is a stage day for the given group patterns.
     */
    private boolean isStageDay(LocalDate date, List<StagePattern> patterns) {
        DayOfWeek dow = date.getDayOfWeek();
        return patterns.stream().anyMatch(sp ->
            !date.isBefore(sp.startDate) &&
            !date.isAfter(sp.endDate) &&
            sp.dayOfWeekMask.contains(dow)
        );
    }

    /**
     * Find a suitable room for a lesson.
     */
    private Room findRoomForLesson(Lesson lesson) {
        // If lesson already has a room, use it
        if (lesson.room != null) {
            return lesson.room;
        }

        // If course has required tags, find matching room
        if (lesson.course != null && lesson.course.requiredRoomTags != null
            && !lesson.course.requiredRoomTags.isEmpty()) {
            String tag = lesson.course.requiredRoomTags.iterator().next();
            return Room.find("SELECT r FROM Room r JOIN r.tags t WHERE t = ?1", tag).firstResult();
        }

        // Find any regular classroom
        return Room.find("SELECT r FROM Room r WHERE r.tags IS EMPTY").firstResult();
    }

    /**
     * Get all professional dates for a group within the period.
     * Returns dates matching the group's ProfessionalizzantiDay configuration,
     * filtered by stage patterns, holidays, and ideal end date.
     *
     * @param group The student group
     * @param stagePatterns All stage patterns
     * @param holidays School holidays
     * @param idealEndDate Group's ideal end date (lessons not placed after this)
     * @return List of valid professional dates
     */
    private List<LocalDate> getProfessionalDatesInRange(
            StudentGroup group,
            List<StagePattern> stagePatterns,
            Set<LocalDate> holidays,
            LocalDate idealEndDate) {

        List<LocalDate> dates = new ArrayList<>();

        // Only pin professionalizzanti during the stage period.
        // Outside stage there's no hard constraint forcing a specific day,
        // so leave those lessons for the solver to place optimally.
        LocalDate stageStart = null;
        LocalDate stageEnd = null;
        for (StagePattern sp : stagePatterns) {
            if (!sp.studentGroup.id.equals(group.id)) continue;
            if (sp.startDate != null) stageStart = stageStart == null || sp.startDate.isBefore(stageStart) ? sp.startDate : stageStart;
            if (sp.endDate != null) stageEnd = stageEnd == null || sp.endDate.isAfter(stageEnd) ? sp.endDate : stageEnd;
        }
        if (stageStart == null || stageEnd == null) {
            return dates; // no stage period → nothing to pin
        }

        LocalDate date = stageStart;
        LocalDate effectiveEnd = idealEndDate != null && idealEndDate.isBefore(stageEnd)
            ? idealEndDate
            : stageEnd;

        // Get professional day for this group
        DayOfWeek expectedDay = null;
        ProfessionalizzantiDay profDay = ProfessionalizzantiDay.find("studentGroup", group).firstResult();
        if (profDay != null && profDay.dayOfWeek != null) {
            expectedDay = profDay.dayOfWeek;
        }

        // No professional day configured - return empty list
        if (expectedDay == null) {
            return dates;
        }

        // Collect all dates matching the professional day
        while (!date.isAfter(effectiveEnd)) {
            // Skip holidays
            if (holidays.contains(date)) {
                date = date.plusDays(1);
                continue;
            }

            // Only include dates matching the professional day
            if (date.getDayOfWeek().equals(expectedDay)) {
                dates.add(date);
            }

            date = date.plusDays(1);
        }

        return dates;
    }

    /**
     * Find all available slots on a specific date for a group.
     * Returns slots that don't conflict with existing lessons and respect H11 daily limit.
     *
     * @param date The date to check
     * @param expectedDay The professional day of week (cached to avoid redundant queries)
     * @param holidays School holidays
     * @param idealEndDate Group's ideal end date
     * @return List of available timeslots
     */
    private List<Timeslot> findAvailableSlotsForDate(
            LocalDate date,
            DayOfWeek expectedDay,
            Set<LocalDate> holidays,
            LocalDate idealEndDate) {

        List<Timeslot> slots = new ArrayList<>();

        // Check if date matches the expected professional day
        if (expectedDay == null || !date.getDayOfWeek().equals(expectedDay)) {
            return slots; // Not a professional day
        }

        // Skip holidays
        if (holidays.contains(date)) {
            return slots;
        }

        // Check ideal end date (allow lessons ON the ideal end date)
        if (idealEndDate != null && date.isAfter(idealEndDate)) {
            return slots;
        }

        // Find all timeslots on this date (H11 check is done by caller)
        LocalTime[] slotTimes = SlotCandidate.getSlotTimes(date.getDayOfWeek());
        for (int slotIndex = 0; slotIndex < slotTimes.length; slotIndex++) {
            LocalDateTime start = LocalDateTime.of(date, slotTimes[slotIndex]);
            Timeslot ts = Timeslot.find("startDateTime", start).firstResult();
            if (ts != null) {
                slots.add(ts);
            }
        }

        return slots;
    }

    /**
     * Check if a slot is valid for a lesson (no conflicts).
     * task-231: Added M15 validation to prevent same-day same-subject gaps.
     */
    private boolean isValidSlot(Lesson lesson, Room room, Timeslot ts) {
        Teacher teacher = lesson.getTeacher();
        StudentGroup group = lesson.getStudentGroup();

        // H1: Teacher conflict
        if (context.hasTeacherConflict(teacher, ts)) {
            return false;
        }

        // H2: Room conflict
        if (context.hasRoomConflict(room, ts)) {
            return false;
        }

        // H3: Student group conflict
        if (context.hasStudentGroupConflict(group, ts)) {
            return false;
        }

        // H5: Teacher unavailability
        if (context.hasH5Violation(teacher, ts)) {
            return false;
        }

        // M15: Same-day same-subject must be consecutive (no gaps)
        if (context.hasH15Violation(lesson, ts)) {
            return false;
        }

        return true;
    }

    /**
     * Create an empty phase result (for phases with nothing to do).
     */
    private HybridPhase1aReport.PhaseResult createEmptyPhaseResult(
            String phaseName, String roomTag, Instant start) {
        return new HybridPhase1aReport.PhaseResult(
            phaseName, true, 0,
            Duration.between(start, Instant.now()), 0, roomTag
        );
    }

    /**
     * Build a success report.
     */
    private HybridPhase1aReport buildSuccessReport(
            List<HybridPhase1aReport.PhaseResult> phaseResults, Instant start) {
        int totalCandidates = phaseResults.stream()
            .mapToInt(r -> r.candidatesEvaluated)
            .sum();

        int totalPinned = phaseResults.stream()
            .mapToInt(r -> r.entitiesPinned)
            .sum();

        LOG.infof("[HybridPhase1a] Success: %d total candidates evaluated, %d entities pinned",
            totalCandidates, totalPinned);

        HybridPhase1aReport report = new HybridPhase1aReport(
            true, "COMPLETE", Duration.between(start, Instant.now()),
            totalCandidates, null, null, phaseResults
        );

        // Store for phase history retrieval
        lastCompletedReport = report;

        return report;
    }

    /**
     * Build a failure report.
     */
    private HybridPhase1aReport buildFailureReport(
            HybridPhase1aReport.PhaseResult failedPhase,
            List<HybridPhase1aReport.PhaseResult> phaseResults, Instant start) {
        int totalCandidates = phaseResults.stream()
            .mapToInt(r -> r.candidatesEvaluated)
            .sum();

        // Build failure analysis
        FailureAnalysis analysis = new FailureAnalysis(
            failedPhase.phaseName,
            "No valid rotation candidate found within candidate limit",
            List.of(new FailureAnalysis.ConflictPattern(
                "CANDIDATE_LIMIT",
                String.format("Tested %d candidates without finding a valid assignment",
                    failedPhase.candidatesEvaluated),
                failedPhase.candidatesEvaluated,
                List.of("Consider increasing planner.phase1a.max-candidates")
            )),
            Set.of(),
            Set.of(),
            String.format("Phase %s failed. Review lab rotation data, teacher availability, " +
                "or increase candidate limit.", failedPhase.phaseName)
        );

        LOG.errorf("[HybridPhase1a] === FAILURE SUMMARY ===");
        LOG.errorf("[HybridPhase1a] Failed at phase: %s after %d total candidates",
            failedPhase.phaseName, totalCandidates);
        if (analysis != null) {
            LOG.errorf("[HybridPhase1a] %s", analysis.toFormattedString());
        }

        return new HybridPhase1aReport(
            false, failedPhase.phaseName, Duration.between(start, Instant.now()),
            totalCandidates, null, analysis, phaseResults
        );
    }

    /**
     * Build a failure report from an unhandled exception.
     */
    private HybridPhase1aReport buildFailureReportFromException(Throwable ex, Instant start) {
        LOG.errorf("[HybridPhase1a] === EXCEPTION FAILURE ===");
        LOG.errorf("[HybridPhase1a] Exception type: %s", ex.getClass().getName());
        LOG.errorf("[HybridPhase1a] Exception message: %s", ex.getMessage());

        // Log stack trace
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        LOG.errorf("[HybridPhase1a] Stack trace:\n%s", sw.toString());

        Duration duration = Duration.between(start, Instant.now());

        // Build failure analysis from exception
        FailureAnalysis analysis = new FailureAnalysis(
            "EXCEPTION",
            ex.getClass().getSimpleName() + ": " + ex.getMessage(),
            List.of(new FailureAnalysis.ConflictPattern(
                "UNHANDLED_EXCEPTION",
                ex.getMessage() != null ? ex.getMessage() : "No message",
                0,
                List.of("Review logs for stack trace", "Check database connectivity")
            )),
            Set.of(),
            Set.of(),
            "Pre-assignment failed with unexpected exception: " + ex.getMessage()
        );

        return new HybridPhase1aReport(
            false, "EXCEPTION", duration,
            0, null, analysis, List.of()
        );
    }

    /**
     * Filter rotations by subjectPrefix. A rotation matches if its subjectPrefix is null
     * (legacy: matches all courses) or the course subject starts with the prefix.
     */
    private List<LabRotation> filterBySubjectPrefix(List<LabRotation> rotations, String subject) {
        if (rotations == null || rotations.isEmpty() || subject == null) return List.of();
        return rotations.stream()
            .filter(r -> r.subjectPrefix == null || subject.startsWith(r.subjectPrefix))
            .toList();
    }
}
