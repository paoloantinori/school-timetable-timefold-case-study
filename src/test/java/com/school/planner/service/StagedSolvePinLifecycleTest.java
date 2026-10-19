package com.school.planner.service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.solver.CompactionPhase;
import com.school.planner.solver.DayClusteredConstructionPhase;
import com.school.planner.solver.PhaseTransitionMarker;
import com.school.planner.solver.StagedPinPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-419 (staged solve): the DB side of the pin lifecycle - promote, demote, release -
 * on a small H2 fixture carrying BOTH classes of pin, plus the extraction of the three phase
 * subsets the cycle runs, pinned against the REAL production {@code solverConfig.xml}.
 *
 * <p>The cycle is {@code A-CONSTRUCTION -> PROMOTION -> B-TIMESLOT [-> C-DEMOTION ->
 * B-TIMESLOT]* -> RELEASE -> B-ROOM}. The release sits BEFORE the room stage on purpose:
 * {@code @PlanningPin} freezes the whole entity, room included, while the weekly template only
 * ever protects TIMESLOTS - so a promoted pin held through the room phases carries the
 * construction's provisional room into the final grid (the val419 smoke paid -895 hard of H16b
 * for exactly that). Three tests pin that split: the two config extractions (a timeslot half
 * with no construction heuristic, a room half whose selectors move {@code room} and nothing
 * else) and {@code assertRoomStagePreconditions}, the runtime guard that refuses to start the
 * room stage while any promoted pin is still held.
 *
 * <p>The central assertion is the one the whole marker design exists for: the count of
 * AUTHORITATIVE pins (golden/calendario/stage/hand: {@code pinned = true, promotedPin =
 * false}) is IDENTICAL before and after a full promote/demote/release round trip. The
 * closing release is keyed on the persistent marker alone, so it is also the crash-recovery
 * path ({@code onStartReleasePromotedPins}); a promoted pin that outlived its cycle is the
 * TASK-383 vanished-pins failure mode in reverse.
 *
 * <p>Scope note: the promotion is written here the way {@code
 * ScheduleService#promoteTemplatePins} writes it (the two flags, together, from {@link
 * StagedPinPolicy#selectPromotions}) rather than by calling that method, which would first
 * re-derive the whole weekly template from this 5-lesson fixture - a different claim
 * (template derivation) with nothing to do with pin accounting. The SELECTION rule itself is
 * pinned by {@code com.school.planner.solver.StagedPinPolicyTest}.
 */
@QuarkusTest
class StagedSolvePinLifecycleTest {

    /**
     * The REAL production pipeline (the classpath copy in tests is the fast smoke config, which
     * is the negative case in the "refuses" tests): the phase-subset extractions are pinned on
     * it, so reordering or renaming a production phase cannot silently make a stage run the
     * wrong variable.
     */
    private static final File PRODUCTION_XML = new File("src/main/resources/solverConfig.xml");

    @Inject ScheduleService scheduleService;
    @Inject EntityManager em;

    private final List<Long> onTemplateIds = new ArrayList<>();
    private long authoritativeExpected;

    @BeforeEach
    @Transactional
    void seed() {
        clearTables();
        SchoolSchedule.clearStaticState();

        StudentGroup group = new StudentGroup();
        group.name = "G03";
        group.size = 20;
        group.persist();
        Teacher teacher = new Teacher("Rossi");
        teacher.persist();
        Course course = new Course("Matematica", teacher, group, 10);
        course.persist();

        List<Timeslot> timeslots = new ArrayList<>();
        for (int slot = 0; slot < 5; slot++) {
            Timeslot ts = new Timeslot(LocalDateTime.of(2026, 9, 14, 8 + slot, 0),
                    LocalDateTime.of(2026, 9, 14, 9 + slot, 0));
            ts.persist();
            timeslots.add(ts);
        }

        onTemplateIds.clear();
        // 2 authoritative pins (the school's), 2 free lessons that stage 1 will promote,
        // 1 free lesson that stays free, 1 boxed lesson (no timeslot).
        for (int i = 0; i < 5; i++) {
            Lesson lesson = new Lesson(course);
            lesson.timeslot = i < 4 ? timeslots.get(i) : null;
            lesson.pinned = i < 2;
            lesson.persist();
            if (i == 2 || i == 3) onTemplateIds.add(lesson.id);
        }
        authoritativeExpected = 2;
    }

    @AfterEach
    @Transactional
    void cleanup() {
        clearTables();
        SchoolSchedule.clearStaticState();
    }

    /**
     * Full wipe in FK order (same convention, and the same reason, as {@code
     * SnapshotFixture.clearTables}): the suite shares one H2 instance, so a fixture that only
     * deleted its own rows would count lessons another test left behind - and one that
     * deleted Teacher without its dependents would hit a referential-integrity error the
     * moment an availability-seeding test ran before this one.
     */
    private void clearTables() {
        for (String entity : List.of("Lesson", "TeacherAvailability", "RecurringAvailabilityRule",
                "LabRotation", "LabAssistant", "ProfessionalizzantiDay", "GroupCalendarRule",
                "DailyActivityQuota", "StagePattern", "Course", "Teacher", "StudentGroup", "Timeslot")) {
            em.createQuery("DELETE FROM " + entity).executeUpdate();
        }
    }

    @Test
    void promoteDemoteReleaseRoundTripLeavesTheAuthoritativePinsUntouched() {
        assertEquals(authoritativeExpected, scheduleService.countAuthoritativePins());
        assertEquals(1, scheduleService.countBoxedLessons(), "one unpinned lesson with no timeslot");

        promote(onTemplateIds);
        assertEquals(2, countPromoted(), "both template lessons are frozen");
        assertEquals(authoritativeExpected, scheduleService.countAuthoritativePins(),
                "a promotion must never touch the school's own pins");
        assertEquals(4, countPinned(), "2 authoritative + 2 promoted");

        // Stage C releases one of them back to quasi-pinned (M32 still charges it).
        int demoted = scheduleService.demotePins(List.of(onTemplateIds.get(0)));
        assertEquals(1, demoted);
        assertEquals(1, countPromoted());
        assertEquals(authoritativeExpected, scheduleService.countAuthoritativePins());
        Lesson demotedLesson = find(onTemplateIds.get(0));
        assertFalse(demotedLesson.pinned, "a demoted lesson is free for the solver again");
        assertFalse(demotedLesson.promotedPin, "and carries no stale marker");

        // Closing smarcatura.
        int released = scheduleService.releasePromotedPins();
        assertEquals(1, released, "only the still-promoted pin is released");
        assertEquals(0, countPromoted());
        assertEquals(authoritativeExpected, scheduleService.countAuthoritativePins(),
                "THE assertion: the authoritative pin count is invariant across the whole cycle");
        assertEquals(authoritativeExpected, countPinned(),
                "and nothing else is left pinned");
        for (Long id : onTemplateIds) {
            assertFalse(find(id).pinned, "released lesson " + id + " is unpinned");
        }
    }

    @Test
    void releaseIsIdempotentAndAStandaloneCrashRecovery() {
        promote(onTemplateIds);
        assertEquals(2, scheduleService.releasePromotedPins());
        assertEquals(0, scheduleService.releasePromotedPins(), "second call has nothing to release");
        assertEquals(authoritativeExpected, scheduleService.countAuthoritativePins());
    }

    /**
     * Stage A must run the PRODUCTION construction phase and nothing else. Pinned on the real
     * {@code src/main/resources/solverConfig.xml} (the classpath copy in tests is the fast
     * smoke config, which is the negative case below), so reordering or renaming the phases
     * cannot silently turn stage A into a local search.
     */
    @Test
    void constructionOnlyKeepsExactlyTheProductionConstructionPhase() {
        SolverConfig production = SolverConfig.createFromXmlFile(PRODUCTION_XML);
        assertTrue(production.getPhaseConfigList().size() > 1, "the production pipeline has many phases");

        List<PhaseConfig> phases = ScheduleService.constructionOnly(production).getPhaseConfigList();

        assertEquals(1, phases.size(), "stage A runs one phase only");
        assertEquals(List.of(DayClusteredConstructionPhase.class),
                ((CustomPhaseConfig) phases.get(0)).getCustomPhaseCommandClassList());
    }

    /**
     * Config (a) - stage B-TIMESLOT: every production phase up to and INCLUDING the
     * {@link CompactionPhase}, and not one construction heuristic (the only CH in the
     * production XML is the room assigner, which belongs to the other half).
     */
    @Test
    void timeslotPhasesKeepEveryProductionPhaseThroughTheCompaction() {
        List<PhaseConfig> production = SolverConfig.createFromXmlFile(PRODUCTION_XML).getPhaseConfigList();
        int compaction = indexOfCustomPhase(production, CompactionPhase.class);
        assertTrue(compaction > 0, "the production pipeline has a CompactionPhase after other phases");

        List<PhaseConfig> phases = ScheduleService.timeslotPhases(
                SolverConfig.createFromXmlFile(PRODUCTION_XML)).getPhaseConfigList();

        assertEquals(compaction + 1, phases.size(), "config (a) is the production head, up to the compaction");
        assertTrue(isCustomPhase(phases.get(0), DayClusteredConstructionPhase.class),
                "and still starts with the construction");
        assertTrue(isCustomPhase(phases.get(phases.size() - 1), CompactionPhase.class),
                "and ends with the last phase that can move a timeslot");
        assertTrue(phases.stream().noneMatch(ConstructionHeuristicPhaseConfig.class::isInstance),
                "the room CH must NOT run while the promoted pins are held - it could not free their rooms anyway");
    }

    /**
     * Config (b) - stage B-ROOM: the room CH plus the room-refinement LS, and NOTHING that
     * moves a timeslot. This is the load-bearing half: it runs AFTER the smarcatura, so a
     * timeslot selector sneaking in here would erode the very template the cycle exists to
     * protect. Every leaf move selector is walked, and every variable it touches must be
     * {@code room}.
     */
    @Test
    void roomPhasesKeepOnlyTheRoomChAndTheRoomRefinementAndMoveRoomsOnly() {
        List<PhaseConfig> production = SolverConfig.createFromXmlFile(PRODUCTION_XML).getPhaseConfigList();
        int compaction = indexOfCustomPhase(production, CompactionPhase.class);

        List<PhaseConfig> phases = ScheduleService.roomPhases(
                SolverConfig.createFromXmlFile(PRODUCTION_XML)).getPhaseConfigList();

        assertEquals(production.size() - compaction - 1, phases.size(), "config (b) is the production tail");
        assertFalse(phases.isEmpty(), "the production pipeline has room phases");
        assertEquals(1, phases.stream().filter(ConstructionHeuristicPhaseConfig.class::isInstance).count(),
                "exactly one CH: the room assigner");
        assertTrue(phases.stream().anyMatch(LocalSearchPhaseConfig.class::isInstance),
                "and the room refinement LS");
        for (PhaseConfig phase : phases) {
            if (phase instanceof CustomPhaseConfig custom) {
                assertEquals(List.of(PhaseTransitionMarker.class), custom.getCustomPhaseCommandClassList(),
                        "the only custom phases allowed after the smarcatura are the progression markers");
            }
        }
        assertEquals(Set.of("room"), variablesMovedBy(phases),
                "config (b) must move the room variable and nothing else - it runs after the release");
    }

    @Test
    void thePhaseSplitRefusesAConfigWithoutTheCompactionPhase() {
        // Same negative case as the construction extraction below: the test classpath's
        // solverConfig.xml is a plain CH+LS smoke config, with no phase the split can anchor on.
        IllegalStateException timeslots = assertThrows(IllegalStateException.class,
                () -> scheduleService.timeslotPhasesConfig());
        assertTrue(timeslots.getMessage().contains("CompactionPhase"), timeslots.getMessage());
        IllegalStateException rooms = assertThrows(IllegalStateException.class,
                () -> scheduleService.roomPhasesConfig());
        assertTrue(rooms.getMessage().contains("CompactionPhase"), rooms.getMessage());
    }

    /**
     * The runtime guard behind the "release BEFORE the room stage" ordering: while a promoted
     * pin is held, starting the room phases is refused outright. Without the guard the same
     * mistake would come back silently, as -895 hard of H16b (the val419 smoke).
     */
    @Test
    void theRoomStageRefusesToStartWhileAPromotedPinIsStillHeld() {
        scheduleService.assertRoomStagePreconditions();

        promote(onTemplateIds);
        IllegalStateException held = assertThrows(IllegalStateException.class,
                () -> scheduleService.assertRoomStagePreconditions());
        assertTrue(held.getMessage().contains("AFTER the smarcatura"), held.getMessage());

        scheduleService.releasePromotedPins();
        SchoolSchedule.setPromotedLessonIdsStatic(List.of());
        scheduleService.assertRoomStagePreconditions();
    }

    /** The room-tier exclusion of the stage-C trigger: families, never {@code startsWith}.
     *  H16b was demoted to M37 (medium) in TASK-446/447 notte, so it left this set: M37 now
     *  appears below among the NON room-tier-hard ids (it is medium, never hard). */
    @Test
    void onlyTheRoomTierHardConstraintsAreExcludedFromTheStageCTrigger() {
        for (String roomTier : List.of("H2", "H2.1", "H2.3", "H-M2", "H-M2.1")) {
            assertTrue(ScheduleService.isRoomTierHardConstraint(roomTier), roomTier);
        }
        // The trap: H20/H23/H24/H25/H26 all start with "H2" and are timeslot rules;
        // M37 (ex-H16b, now medium) is not a room-tier-HARD family either.
        for (String timeslotTier : List.of("H0", "H1", "H3", "H16", "H16a", "H16b", "H17", "H20", "H23",
                "H24", "H25", "H26", "M20", "M37")) {
            assertFalse(ScheduleService.isRoomTierHardConstraint(timeslotTier), timeslotTier);
        }
        assertFalse(ScheduleService.isRoomTierHardConstraint(null), "an unnamed constraint is not room-tier");
    }

    @Test
    void constructionOnlyRefusesAConfigWithoutTheConstructionPhase() {
        // The test classpath's solverConfig.xml is a plain CH+LS smoke config (the same XML
        // the injected solver is built from, since stage A now reads the resource name off
        // quarkus.timefold.solver.solver-config-xml): refusing loudly is the point - silently
        // running "whatever the first phase is" would make stage A a local search and the
        // promotion meaningless.
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> scheduleService.constructionOnlyConfig());
        assertTrue(failure.getMessage().contains("DayClusteredConstructionPhase"), failure.getMessage());
    }

    @Test
    void demotingIgnoresIdsThatAreNotPromotedPins() {
        long authoritativeId = QuarkusTransaction.requiringNew()
                .call(() -> Lesson.<Lesson>find("pinned = true and promotedPin = false").firstResult().id);
        assertEquals(0, scheduleService.demotePins(List.of(authoritativeId)),
                "an authoritative pin is out of the demotion statement's WHERE by construction");
        assertEquals(authoritativeExpected, scheduleService.countAuthoritativePins());
        assertTrue(find(authoritativeId).pinned);
    }

    /**
     * The promotion write of {@code ScheduleService#promoteTemplatePins}: both flags,
     * together. Runs in its OWN transaction ({@link QuarkusTransaction}) rather than relying
     * on {@code @Transactional} interception of a helper called from a test method.
     */
    void promote(List<Long> lessonIds) {
        QuarkusTransaction.requiringNew().run(() -> {
            List<Lesson> lessons = Lesson.list("id in ?1", lessonIds);
            for (Lesson lesson : lessons) {
                lesson.pinned = true;
                lesson.promotedPin = true;
            }
            SchoolSchedule.setPromotedLessonIdsStatic(lessonIds);
        });
    }

    private long count(String where) {
        return QuarkusTransaction.requiringNew().call(() -> Lesson.count(where));
    }

    private Lesson find(Long id) {
        return QuarkusTransaction.requiringNew().call(() -> Lesson.findById(id));
    }

    private long countPromoted() {
        return count("promotedPin = true");
    }

    private long countPinned() {
        return count("pinned = true");
    }

    private static boolean isCustomPhase(PhaseConfig phase, Class<?> command) {
        return phase instanceof CustomPhaseConfig custom
                && custom.getCustomPhaseCommandClassList() != null
                && custom.getCustomPhaseCommandClassList().contains(command);
    }

    private static int indexOfCustomPhase(List<PhaseConfig> phases, Class<?> command) {
        for (int i = 0; i < phases.size(); i++) {
            if (isCustomPhase(phases.get(i), command)) return i;
        }
        return -1;
    }

    /**
     * Every planning variable the given phases can move, by walking the move-selector tree down
     * to its leaves. An unrecognised selector type FAILS rather than being skipped: the claim
     * this backs is "config (b) moves rooms and nothing else", and silently ignoring a selector
     * nobody taught this method about would turn that claim into a guess.
     */
    private static Set<String> variablesMovedBy(List<PhaseConfig> phases) {
        Set<String> variables = new java.util.TreeSet<>();
        for (PhaseConfig phase : phases) {
            if (phase instanceof ConstructionHeuristicPhaseConfig ch
                    && ch.getEntityPlacerConfig() instanceof QueuedEntityPlacerConfig placer) {
                collectVariables(placer.getMoveSelectorConfigList(), variables);
            } else if (phase instanceof LocalSearchPhaseConfig ls) {
                collectVariables(List.of(ls.getMoveSelectorConfig()), variables);
            }
        }
        return variables;
    }

    private static void collectVariables(List<MoveSelectorConfig> selectors, Set<String> variables) {
        if (selectors == null) return;
        for (MoveSelectorConfig selector : selectors) {
            if (selector instanceof UnionMoveSelectorConfig union) {
                collectVariables(union.getMoveSelectorList(), variables);
            } else if (selector instanceof ChangeMoveSelectorConfig change) {
                variables.add(change.getValueSelectorConfig().getVariableName());
            } else if (selector instanceof SwapMoveSelectorConfig swap) {
                variables.addAll(swap.getVariableNameIncludeList());
            } else {
                throw new AssertionError("unhandled move selector type in the room phases: "
                        + selector.getClass().getName());
            }
        }
    }
}
