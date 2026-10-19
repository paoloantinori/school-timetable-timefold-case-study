package com.school.planner.bootstrap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.TimeslotSequence;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-415 localized room-phase harness — the first OFFLINE way to exercise the room
 * constraints (H2 room conflict, H-M2 room suitability, M3 room capacity, M-LA lab
 * assistant) without a full solve. Two modes, selected by which env var is set.
 *
 * <p><b>Default (pipe) mode</b>, {@code PLANNER_TEST_ROOM_PHASE}: loads a committed pipe
 * snapshot ({@link SnapshotFixture#loadSnapshotNoClean}, no room column at all). NULLS every
 * room, seeds the 18 real Rooms ({@link RealSeedFixture#seedRooms}), GREEDILY
 * pre-assigns a room to each placed lesson (the production phase-4 room CH is deliberately
 * BYPASSED — see the allowsUnassigned null-trap note at the call site / TASK-417), then runs
 * ONLY the phase-5 LS room refinement (reduced budget) — the phase the golden/checkpoint
 * fixtures cannot exercise because neither seeds any Room.
 *
 * <p><b>Export mode</b>, {@code PLANNER_TEST_ROOM_PHASE_EXPORT}: a follow-up gap closed after
 * TASK-415 (the pipe dump above has no room column, so that mode always starts from an
 * artificially room-null state, never the run's REAL room assignments). Loads an {@code
 * export.json.gz} via {@link SnapshotFixture#loadFromExport}, which preserves every real
 * per-lesson room (including genuinely null ones, i.e. lessons the run left room-unassigned)
 * and the real {@code Course.isLab}/{@code requiredRoomTags}. No nulling, no greedy
 * pre-assign: phase-5 LS runs straight from the run's real room state, so this mode
 * validates a phase-5/H-M2 change against a state the solver actually produced, not a
 * synthetic one. Both env vars must be set to run export mode ({@code
 * PLANNER_TEST_ROOM_PHASE} still gates the whole class, see "Default OFF" below); invoke:
 * {@code PLANNER_TEST_ROOM_PHASE=1 PLANNER_TEST_ROOM_PHASE_EXPORT=path/to/export.json.gz
 * ./scripts/core/test.sh -c RoomPhaseSnapshotTest}.
 *
 * <p><b>Verdict class — TREND only, NEVER adoption.</b> A snapshot fixes ONE trajectory
 * (TASK-401 protocol); this harness proves the room phase RUNS end-to-end offline and
 * produces a sensible hard score (a low, attributable room-conflict residual), not that the
 * model is right. On a single-group slice H2 is naturally low or 0 because cross-group room
 * contention is not even in the slice — a clean run is NOT evidence H2 would be 0 on the full
 * population. Room-phase tuning adoption passes through the laptop A/B gate.
 *
 * <p>The room LS is built PROGRAMMATICALLY, mirroring the phase-5 (room refinement)
 * {@code <localSearch>} of {@code solverConfig.xml} field-for-field: union(room changeMove w2,
 * room swapMove w1) + entityTabu 7 + acceptedCountLimit 1000, with descriptive XML anchors (not
 * line numbers). The termination is capped short (10s) — a smoke, not a real refine.
 *
 * <p><b>Default OFF from the suite</b> ({@code @EnabledIfEnvironmentVariable}): boots Quarkus
 * + reconstructs ~12k lessons, so it is opt-in via the env var {@code PLANNER_TEST_ROOM_PHASE}
 * ({@code 1} = default snapshot, or a snapshot path) to keep the default suite budget intact
 * (localized-testing-guidelines §5). An env var (not {@code -D}) because {@code
 * scripts/core/test.sh} rejects explicit {@code -D} args and does not forward MAVEN_OPTS to the
 * surefire fork. Invoke: {@code PLANNER_TEST_ROOM_PHASE=1 ./scripts/core/test.sh -c
 * RoomPhaseSnapshotTest} (slice override via {@code PLANNER_TEST_ROOM_PHASE_GROUPS="A,B"}).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_ROOM_PHASE", matches = ".+")
class RoomPhaseSnapshotTest {

    /** Default group slice: a SINGLE group (~500 placed lessons) keeps the room LS tractable
     *  offline (see the slice + budget notes in the test). Override with the env var
     *  PLANNER_TEST_ROOM_PHASE_GROUPS="A,B" (empty = all groups — will be very slow). */
    private static final String DEFAULT_SLICE_GROUPS = "G01";
    /** Phase-5 LS room budget (s). Production caps at 600s; default 10 is a bounded offline smoke.
     *  Override with {@code PLANNER_TEST_ROOM_PHASE_BUDGET} for a longer all-groups measurement
     *  (e.g. the TASK-446/447 M37 "how many of the 291 non-lab room-null take a room" run). */
    private static final long LS_BUDGET_SECONDS = Long.parseLong(
            System.getenv().getOrDefault("PLANNER_TEST_ROOM_PHASE_BUDGET", "10"));

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        em.createQuery("DELETE FROM Room").executeUpdate();
        SnapshotFixture.resetStatics();
        // Clear every env-bridged system property so a flag-enabled run cannot leak into the
        // surefire fork and drift a later test's solve (e.g. a leaked
        // planner.room.lab-fallback.enabled=false would opt a later solve out of the ratified
        // default-ON; a leaked weight knob would shift M-LF). The override block only clears the
        // lab-fallback props in its else-branch (env unset), so this is the belt-and-suspenders
        // cleanup for the set-then-never-restored path; M37's property rides the same bridge.
        System.clearProperty("planner.m37.weight");
        System.clearProperty("planner.room.lab-fallback.enabled");
        System.clearProperty("planner.room.lab-fallback.weight");
        System.clearProperty("planner.tag-capacity.enabled");
        System.clearProperty("planner.tag-capacity.weight");
        System.clearProperty("planner.co-presenza.enabled");
    }

    @Test
    @Transactional
    void roomPhasesAssignRoomsWithZeroOrAttributableHard() throws Exception {
        String exportEnv = System.getenv("PLANNER_TEST_ROOM_PHASE_EXPORT");
        boolean exportMode = exportEnv != null && !exportEnv.isBlank();

        SnapshotFixture.Snapshot snap;
        List<Room> rooms;
        String sourceLabel;

        if (exportMode) {
            // Export mode: the run's real per-lesson room state (including genuinely null
            // rooms) is preserved as-is. No nulling, no greedy pre-assign, see the class
            // javadoc "Export mode" section.
            java.nio.file.Path exportPath = java.nio.file.Path.of(exportEnv);
            snap = SnapshotFixture.loadFromExport(exportPath, availabilityExpansionService, true);
            rooms = snap.rooms;
            SchoolSchedule.setAllRoomsStatic(rooms);
            sourceLabel = exportPath.toString();
        } else {
            java.nio.file.Path dir = SnapshotFixture.resolveSnapshotDir(System.getenv("PLANNER_TEST_ROOM_PHASE"));
            snap = SnapshotFixture.loadSnapshotNoClean(dir, availabilityExpansionService, true);

            // Seed the 18 real Rooms (mirrors DataGenerator.generateRooms via rooms.csv + tag map),
            // and wire the static before any Lesson.getAvailableRooms() call reads it.
            rooms = RealSeedFixture.seedRooms();
            SchoolSchedule.setAllRoomsStatic(rooms);

            // The snapshot's rooms are not reconstructed (lessons.txt carries no room column), so
            // every placed lesson starts room-null. We GREEDILY pre-assign a room here rather than
            // rely on a phase-4 CH: with @PlanningVariable(allowsUnassigned=true), Timefold prepends
            // null to the value range, and the ONLY_DOWN default forager (FIRST_NON_DETERIORATING_
            // SCORE) picks null for every entity (it's always score-neutral), so the CH "initializes"
            // everything to null and never reaches the real rooms (verified: 0 assignments in 670
            // steps with a correct 11-room value range). Pre-assigning lets phase-5 LS refine the
            // rooms against H2/H-M2/M3/M-LA, which is the harness's actual goal.
            greedilyAssignRooms(snap.lessons, rooms);
            sourceLabel = dir.toString();
        }

        // === Fase 3 (TASK-446 notte) in-memory experiment hooks. NO protected-file change
        //    (DataGenerator.java / seed CSVs untouched): mutate the LOADED Course/Room objects
        //    after loadFromExport + setAllRoomsStatic, BEFORE solver.solve. EXPORT MODE ONLY:
        //    in pipe mode greedilyAssignRooms (above) already calls getAvailableRooms() and
        //    populates the per-lesson cache, so a tag override there would be silently stale;
        //    in export mode the first getAvailableRooms() is inside solver.solve, so the cache
        //    is still empty and no invalidation is needed. Inner vars env-gated; default runs
        //    unaffected. ===
        if (exportMode) {
            String untagEnv = System.getenv("PLANNER_TEST_ROOM_TAG_UNTAG"); // comma-list of subject prefixes to DE-TAG (-> non-lab/aula)
            if (untagEnv != null && !untagEnv.isBlank()) {
                java.util.Set<String> prefixes = java.util.Arrays.stream(untagEnv.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .map(String::toLowerCase).collect(java.util.stream.Collectors.toSet());
                long changed = snap.problem.getCourses().stream()
                        .filter(c -> c.subject != null && prefixes.stream().anyMatch(p -> c.subject.toLowerCase().startsWith(p)))
                        .peek(c -> c.requiredRoomTags.clear())
                        .count();
                // A de-tagged lesson still sitting in its old lab room is now OUTSIDE its non-lab
                // value range (aulas only, per Lesson.getAvailableRooms) -> Timefold IllegalState at
                // solve init. Null those rooms so phase-5 LS re-assigns them to an aula. The filter
                // (requiredRoomTags now empty && room is a tagged/lab room) matches exactly the
                // lessons the post-de-tag value range excludes (the de-tagged set on the M36 export).
                long roomsNulled = snap.lessons.stream()
                        .filter(l -> l.course != null && l.course.requiredRoomTags.isEmpty()
                                && l.room != null && l.room.tags != null && !l.room.tags.isEmpty())
                        .map(l -> { l.room = null; return l; })
                        .count();
                System.out.println("=== FASE3 override: de-tagged " + changed + " course(s) matching " + prefixes
                        + " (now non-lab/aula); nulled " + roomsNulled + " old lab-room assignment(s) ===");
            }
            // UNTAG by (group, subject): TASK-456 Tappa B pre-misura (risposte attese scuola).
            // Format "GROUP=>subj1,subj2;GROUP2=>subj3" (specs ';'-separated, subjects ','
            // -separated). Clears BOTH requiredRoomTags AND course.isLab for courses matching
            // (group name, subject in list), scoping the de-tag to ONE group (UNTAG above is a
            // global subject-prefix). Clearing isLab too (unlike UNTAG, which leaves it) is what
            // keeps the LAB-room-null count clean: a de-tagged G14/G12 hour stops counting as
            // a lab hour, so "LAB REAL room-null before" stays at the 182 GENUINE lab lessons and
            // "after" measures how many of THOSE close once the group's lab-tagged hours stop
            // contending for the lab. Nulls the rooms of affected lessons still sitting in their
            // now-incompatible lab room (same fix-up as UNTAG/RETAG). Used with lab-fallback ON:
            // the freed lab capacity lets genuine lab lessons take the vacated slots.
            String untagGroupEnv = System.getenv("PLANNER_TEST_ROOM_TAG_UNTAG_GROUP");
            if (untagGroupEnv != null && !untagGroupEnv.isBlank()) {
                java.util.Map<String, java.util.Set<String>> specs = new java.util.HashMap<>();
                for (String spec : untagGroupEnv.split(";")) {
                    String[] kv = spec.split("=>");
                    if (kv.length == 2 && !kv[0].trim().isEmpty() && !kv[1].trim().isEmpty()) {
                        java.util.Set<String> subs = java.util.Arrays.stream(kv[1].split(","))
                                .map(String::trim).filter(s -> !s.isEmpty())
                                .map(String::toLowerCase).collect(java.util.stream.Collectors.toSet());
                        specs.put(kv[0].trim().toLowerCase(), subs);
                    }
                }
                long changed = snap.problem.getCourses().stream()
                        .filter(c -> c.subject != null && c.studentGroup != null
                                && specs.containsKey(c.studentGroup.name.toLowerCase())
                                && specs.get(c.studentGroup.name.toLowerCase())
                                        .contains(c.subject.toLowerCase()))
                        .peek(c -> { c.requiredRoomTags.clear(); c.isLab = false; })
                        .count();
                long roomsNulled = snap.lessons.stream()
                        .filter(l -> l.course != null && l.course.requiredRoomTags.isEmpty()
                                && !l.course.isLab
                                && l.room != null && l.room.isTagged())
                        .map(l -> { l.room = null; return l; })
                        .count();
                System.out.println("=== FASE3 override: group-de-tagged " + changed + " course(s) per " + specs
                        + " (requiredRoomTags cleared + isLab=false); nulled " + roomsNulled
                        + " old lab-room assignment(s) ===");
            }
            String salCapEnv = System.getenv("PLANNER_TEST_ROOM_R17_CAP"); // integer capacity override for R17
            if (salCapEnv != null && !salCapEnv.isBlank()) {
                int cap = Integer.parseInt(salCapEnv.trim());
                long n = rooms.stream().filter(r -> "R17".equals(r.name)).peek(r -> r.capacity = cap).count();
                System.out.println("=== FASE3 override: R17 capacity -> " + cap + " (matched " + n + " room) ===");
            }
            // RE-TAG (mirror of the UNTAG hook above): comma-list of "subject-prefix->newtag"
            // that KEEPS the course lab-tagged but CHANGES which room tag it requires (e.g.
            // "taglio metalli->saldatura" so Taglio Metalli moves R15 -> R17).
            // Use with PLANNER_TEST_ROOM_R17_CAP to give the target room real capacity.
            String retagEnv = System.getenv("PLANNER_TEST_ROOM_TAG_RETAG");
            if (retagEnv != null && !retagEnv.isBlank()) {
                java.util.Map<String, String> remap = new java.util.HashMap<>();
                for (String spec : retagEnv.split(",")) {
                    String[] kv = spec.split("->");
                    if (kv.length == 2 && !kv[0].trim().isEmpty() && !kv[1].trim().isEmpty()) {
                        remap.put(kv[0].trim().toLowerCase(), kv[1].trim().toLowerCase());
                    }
                }
                long changed = 0;
                for (com.school.planner.domain.Course c : snap.problem.getCourses()) {
                    if (c.subject == null) continue;
                    String prefix = remap.keySet().stream()
                            .filter(p -> c.subject.toLowerCase().startsWith(p)).findFirst().orElse(null);
                    if (prefix == null) continue;
                    c.requiredRoomTags.clear();
                    c.requiredRoomTags.add(remap.get(prefix));
                    changed++;
                }
                // A re-tagged lesson still sitting in a room whose tags no longer cover the new
                // required tag is outside its value range (IllegalState at solve init), so null
                // those rooms and let phase-5 LS re-assign to a new-tag room. Same fix-up as the
                // UNTAG hook applies to de-tagged lessons landing in their old lab room.
                long roomsNulled = snap.lessons.stream()
                        .filter(l -> l.course != null && !l.course.requiredRoomTags.isEmpty()
                                && l.room != null && (l.room.tags == null
                                        || !l.room.tags.containsAll(l.course.requiredRoomTags)))
                        .map(l -> { l.room = null; return l; })
                        .count();
                System.out.println("=== FASE3 override: re-tagged " + changed + " course(s) per " + remap
                        + "; nulled " + roomsNulled + " old room assignment(s) no longer tag-compatible ===");
            }
            // EXCLUDE rooms (TASK-456 point 4 - "R18 esclusa come chiede la scuola"):
            // comma-list of room names to REMOVE from the value domain entirely. The school wants
            // the cap-0 overflow room (R18, 204 lessons of which 181 have no real
            // alternative) out of the schedule; without lab-fallback that was catastrophic
            // (189 -> 370). With lab-fallback ON the displaced overflow may shelter in a free lab
            // instead, so this re-measures the exclusion in the new lab-fallback frame. Lessons
            // sitting in an excluded room are nulled (become room-null for the LS to re-place),
            // mirroring the de-tag/retag null fix-up; the static room list is rebuilt so
            // getAvailableRooms stops proposing the excluded room. CAVEAT: the null is unconditional,
            // so a PINNED lesson whose room is excluded is left room-null for the whole LS (pinned
            // lessons are immovable, H-M2 would fire with no legal recovery). Safe for the intended
            // target (R18, cap-0 overflow that hosts no pinned labs); do not point this
            // at a room that carries a real lab-rotation/golden pin.
            String excludeEnv = System.getenv("PLANNER_TEST_ROOM_EXCLUDE");
            if (excludeEnv != null && !excludeEnv.isBlank()) {
                java.util.Set<String> excludeNames = java.util.Arrays.stream(excludeEnv.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .map(String::toLowerCase).collect(java.util.stream.Collectors.toSet());
                long nulled = snap.lessons.stream()
                        .filter(l -> l.room != null && l.room.name != null
                                && excludeNames.contains(l.room.name.toLowerCase()))
                        .map(l -> { l.room = null; return l; })
                        .count();
                int before = rooms.size();
                rooms = rooms.stream()
                        .filter(r -> r.name == null || !excludeNames.contains(r.name.toLowerCase()))
                        .collect(java.util.stream.Collectors.toList());
                SchoolSchedule.setAllRoomsStatic(rooms);
                System.out.println("=== FASE3 override: excluded " + (before - rooms.size())
                        + " room(s) " + excludeNames + "; nulled " + nulled + " lesson(s) in them ===");
            }
        }

        // SLICE to a group subset. A full 12k-entity phase-5 room LS is slow offline (production's
        // speed depends on a clean post-phases-1-4 state a raw snapshot doesn't reproduce); a single
        // group (~500 lessons) keeps the harness a fast offline exercise of H2/H-M2/M3/M-LA, the
        // same slice precedent HumanFaithfulConstructionSpikeTest.sliceScaling uses. Override with
        // PLANNER_TEST_ROOM_PHASE_GROUPS="G01,G05" (empty string = all groups).
        String groupsEnv = System.getenv("PLANNER_TEST_ROOM_PHASE_GROUPS");
        String groupsProp = groupsEnv != null ? groupsEnv : DEFAULT_SLICE_GROUPS;
        java.util.Set<String> wantGroups = groupsProp.isBlank() ? java.util.Set.of()
                : java.util.Arrays.stream(groupsProp.split(",")).map(String::trim)
                        .filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toSet());
        List<Lesson> slice = snap.lessons.stream()
                .filter(l -> wantGroups.isEmpty()
                        || (l.getStudentGroup() != null && wantGroups.contains(l.getStudentGroup().name)))
                .toList();
        SchoolSchedule.setAllLessonsStatic(slice);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(slice);

        SchoolSchedule problem = new SchoolSchedule(snap.timeslots, rooms, List.of(),
                com.school.planner.domain.TeacherAvailability.listAll(), List.of(),
                com.school.planner.domain.StudentGroup.listAll(), com.school.planner.domain.Teacher.listAll(),
                new ArrayList<>(snap.problem.getCourses()), slice, List.of());

        long placedLessons = slice.stream().filter(l -> l.timeslot != null).count();
        long unassignedRoomsBefore = slice.stream().filter(l -> l.timeslot != null && l.room == null).count();
        long nonLabRealRoomNullBefore = countNonLabRealRoomNull(slice);
        long labRealRoomNullBefore = countLabRealRoomNull(slice);
        long roomChangesBefore = countRoomChanges(slice);
        long nonLabInTaggedBefore = countNonLabInTaggedRoom(slice);

        // FASE B / SPIKE 1 (TASK-447): override M37 weight for this run. The constraint reads
        // planner.m37.weight INLINE at defineConstraints (per-build, see SchoolScheduleConstraint
        // Provider#consecutiveSameSubjectSameTeacherSameRoom), so setting the system property here
        // - before buildSolver - takes effect for THIS solve only. test.sh does not forward -D, so
        // the harness bridges an env var (PLANNER_TEST_M37_WEIGHT) to the property. Default 100.
        int m37Weight = bridgeIntEnv("PLANNER_TEST_M37_WEIGHT", "planner.m37.weight", 100);

        // TASK-456 lab-fallback override (domanda 21): bridge env -> system property (same helper
        // as the M37 weight bridge above). The flag is read LIVE by Lesson.getAvailableRooms at
        // cache-build AND by the M-LF constraint at defineConstraints, so setting it here -
        // before buildSolver - widens the room value range (non-lab lessons may sit in tagged
        // rooms) and activates the M-LF discouragement for THIS solve only. PLANNER_TEST_ROOM_
        // LAB_FALLBACK=1 forces ON explicitly; PLANNER_TEST_ROOM_LAB_FALLBACK_WEIGHT=N sets the
        // M-LF weight (default 1000, the decided TASK-456 weight). NOTE: the ratified production
        // default is now ON (SchoolSchedule.isLabFallbackEnabled), so a default run with no env is
        // ALREADY lab-fallback-ON - this env no longer opts INTO anything, it just makes ON
        // explicit. To measure the opt-OUT baseline instead set -Dplanner.room.lab-fallback
        // .enabled=false (no env hook for that in this harness).
        // exportMode-only: in pipe mode greedilyAssignRooms (above) already called
        // getAvailableRooms() and populated cachedAvailableRooms under the production default-ON,
        // and that cache is not rebuilt during solve, so forcing the env here would be silently
        // inert on the value range while M-LF still activated (a misleading no-op). Export mode's
        // first getAvailableRooms is inside solver.solve (cache fresh after the override), so a
        // forced change takes effect honestly there - same exportMode guard the FASE3 hooks above use.
        boolean labFallback = exportMode && "1".equals(System.getenv("PLANNER_TEST_ROOM_LAB_FALLBACK"));
        int labFallbackWeight;
        if (labFallback) {
            System.setProperty("planner.room.lab-fallback.enabled", "true");
            labFallbackWeight = bridgeIntEnv(
                    "PLANNER_TEST_ROOM_LAB_FALLBACK_WEIGHT", "planner.room.lab-fallback.weight", 1000);
        } else {
            System.clearProperty("planner.room.lab-fallback.enabled");
            System.clearProperty("planner.room.lab-fallback.weight");
            labFallbackWeight = 1000;
        }

        // TASK-455 POINT 3: M-TC tag-capacity override, same shape as the lab-fallback
        // bridge above. The flag is read per-build by SchoolSchedule.isTagCapacityEnabled
        // at defineConstraints and the weight by readCadenceKnobProperty, so setting both
        // here - before buildSolver - activates M-TC for THIS solve only.
        // PLANNER_TEST_ROOM_TAG_CAPACITY=1 forces ON (production default OFF);
        // PLANNER_TEST_ROOM_TAG_CAPACITY_WEIGHT=N overrides the weight (default 1000).
        // exportMode-only for the same reason as lab-fallback (pipe mode caches rooms pre-solve).
        boolean tagCapacity = exportMode && "1".equals(System.getenv("PLANNER_TEST_ROOM_TAG_CAPACITY"));
        if (tagCapacity) {
            System.setProperty("planner.tag-capacity.enabled", "true");
            bridgeIntEnv("PLANNER_TEST_ROOM_TAG_CAPACITY_WEIGHT", "planner.tag-capacity.weight", 1000);
        } else {
            System.clearProperty("planner.tag-capacity.enabled");
            System.clearProperty("planner.tag-capacity.weight");
        }

        // TASK-464 co-presenza ospite override, same shape as the tag-capacity bridge above.
        // The flag is read per-build by SchoolSchedule.isCoPresenceEnabled at defineConstraints,
        // AND the golden depth map is read by the H2 pairwise exemption and the H-COPRESENCE
        // cap, so activating the flag here - before buildSolver - AND publishing the depth map
        // turns co-presenza on for THIS solve only. The depth map is built from the loaded
        // Courses' subject->requiredRoomTags (single-tag), the same authoritative source
        // ScheduleService.refreshSolverStatics uses (no second SubjectRegistry copy).
        // Production default is ON since the 2026-08-01 ratification, so this env's job is to
        // PUBLISH the golden depth map (the second gate): without it the else-branch empties
        // the depth map, which is what keeps co-presenza inert in this isolated test solve
        // (empty map -> depth 0 everywhere -> the H2 exemption and the cap are both no-ops
        // regardless of the flag). exportMode-only for the same reason as tag-capacity (pipe
        // mode caches rooms pre-solve). Slice must include ALL co-presenza groups (1/2/G10 +
        // G12 + G14) via PLANNER_TEST_ROOM_PHASE_GROUPS: co-presenza is cross-group,
        // invisible on the default single-group slice.
        boolean coPresence = exportMode && "1".equals(System.getenv("PLANNER_TEST_CO_PRESENCE"));
        if (coPresence) {
            System.setProperty("planner.co-presenza.enabled", "true");
            java.util.Map<String, String> subjectToTag = com.school.planner.service.ConstrainedTeacherAllocator
                    .buildSubjectToSingleTagMap(snap.problem.getCourses());
            java.util.Map<String, Integer> depth = com.school.planner.service.ConstrainedTeacherAllocator
                    .loadGoldenCoPresenceDepth(subjectToTag);
            SchoolSchedule.setGoldenCoPresenceDepthStatic(depth);
            long cells = depth.values().stream().filter(d -> d >= 2).count();
            System.out.println("=== TASK-464 co-presenza: flag ON, published golden depth map ("
                    + cells + " depth>=2 cells of " + depth.size() + " tallied) ===");
        } else {
            System.clearProperty("planner.co-presenza.enabled");
            SchoolSchedule.setGoldenCoPresenceDepthStatic(java.util.Map.of());
        }

        Solver<SchoolSchedule> solver = SolverFactory.<SchoolSchedule>create(roomPhasesOnly(rooms)).buildSolver();
        SchoolSchedule solved = solver.solve(problem);

        long unassignedRoomsAfter = solved.getLessons().stream()
                .filter(l -> l.timeslot != null && l.room == null).count();
        long nonLabRealRoomNullAfter = countNonLabRealRoomNull(solved.getLessons());
        long labRealRoomNullAfter = countLabRealRoomNull(solved.getLessons());
        long roomConflictsAfter = countRoomConflicts(solved);
        long roomChangesAfter = countRoomChanges(solved.getLessons());
        long nonLabInTaggedAfter = countNonLabInTaggedRoom(solved.getLessons());
        BendableScore score = (BendableScore) solved.getScore();

        System.out.println("=== room-phase snapshot: " + sourceLabel
                + (exportMode ? " (export mode, real room state)" : " (pipe mode, room-null + greedy)")
                + (wantGroups.isEmpty() ? "" : " (slice: " + wantGroups + ")") + " ===");
        System.out.printf("  placed lessons=%d, rooms before=%d unassigned%n", placedLessons, unassignedRoomsBefore);
        System.out.printf("  rooms after: %d unassigned, %d room-conflict cells (>1 lesson/cell; H2 counts pairs, this counts cells)%n",
                unassignedRoomsAfter, roomConflictsAfter);
        System.out.printf("  non-lab REAL room-null (the H29/M37 target): before=%d after=%d (phase-5 LS filled %d)%n",
                nonLabRealRoomNullBefore, nonLabRealRoomNullAfter,
                nonLabRealRoomNullBefore - nonLabRealRoomNullAfter);
        System.out.printf("  LAB REAL room-null (Fase 3 target, the 256): before=%d after=%d (phase-5 LS filled %d)%n",
                labRealRoomNullBefore, labRealRoomNullAfter,
                labRealRoomNullBefore - labRealRoomNullAfter);
        System.out.printf("  M37 room-changes (weight %d): before=%d after=%d (adjacent same-subject same-teacher pairs, different room; the FASE B metric)%n",
                m37Weight, roomChangesBefore, roomChangesAfter);
        System.out.printf("  lab-fallback (flag=%s, M-LF weight %d): non-lab-in-tagged-room before=%d after=%d (rooms the phase-5 LS sheltered in a free lab; S10 soft co-fires)%n",
                labFallback, labFallbackWeight, nonLabInTaggedBefore, nonLabInTaggedAfter);
        System.out.printf("  score: hard=%d medium=%d soft=%d%n",
                ScoreCompat.hardOf(score), ScoreCompat.mediumOf(score), ScoreCompat.softOf(score));
        System.out.println("  (snapshot fixes ONE trajectory, TREND only, never adoption; "
                + "H2/H-M2/M3/M-LA now exercisable offline)");

        // Structural guarantees, pipe mode only: (1) greedy assigned rooms (rooms-before
        // unassigned is a small fraction; zero would mean the value range or tag filter is
        // broken); (2) the LS did not unassign them all (rooms-after stays assigned). A hard
        // EXPLOSION is the review signal this is built for; the exact conflict count is printed,
        // not asserted. Export mode has no greedy step to validate, so only (2) applies there:
        // the real run's own room state is whatever it is, faithfully reproduced, not a signal
        // of "broken" by itself.
        if (!exportMode) {
            assertTrue(unassignedRoomsBefore < placedLessons,
                    "greedy pre-assign must give most placed lessons a room, got "
                            + unassignedRoomsBefore + " unassigned of " + placedLessons
                            + " (value range / tag filter broken?)");
        }
        assertTrue(unassignedRoomsAfter < placedLessons,
                "phase-5 LS must keep rooms assigned, got " + unassignedRoomsAfter
                        + " unassigned of " + placedLessons);
    }

    /**
     * Bridge an int env var to a system property (scripts/core/test.sh does not forward {@code -D}
     * to the surefire fork). Valid value -> set the property; blank/unset/unparseable -> clear it
     * so the consuming constraint applies its OWN default. Returns the parsed value, or {@code
     * fallback} when blank/unparseable, for the diagnostic printf. Shared by the M37 weight bridge
     * and the TASK-456 lab-fallback weight bridge (same shape, one failure mode).
     */
    private static int bridgeIntEnv(String envName, String propName, int fallback) {
        String env = System.getenv(envName);
        if (env == null || env.isBlank()) {
            System.clearProperty(propName);
            return fallback;
        }
        try {
            int value = Integer.parseInt(env.trim());
            System.setProperty(propName, env.trim());
            return value;
        } catch (NumberFormatException e) {
            System.clearProperty(propName);
            return fallback;
        }
    }

    /** Count H2 room conflicts (two placed lessons in the same room at the same timeslot). */
    private long countRoomConflicts(SchoolSchedule solved) {
        Map<String, Integer> roomSlot = new HashMap<>();
        for (Lesson l : solved.getLessons()) {
            if (l.timeslot == null || l.room == null) continue;
            roomSlot.merge(l.room.id + "@" + l.timeslot.id, 1, Integer::sum);
        }
        // Count distinct (room, timeslot) cells with >1 lesson. This is NOT the H2 score
        // (H2 penalizes per conflicting PAIR, C(n,2) per cell); it is a cheaper cell-count
        // proxy for the TREND signal — labelled accordingly at the call site.
        return roomSlot.values().stream().filter(c -> c > 1).count();
    }

    /**
     * Count M37 matches: adjacent same-subject same-teacher pairs in the same (group, date),
     * both non-lab, both roomed, with DIFFERENT rooms. Mirrors {@code
     * SchoolScheduleConstraintProvider#consecutiveSameSubjectSameTeacherSameRoom} exactly
     * (the FASE B metric maintainer asked for), so the "before" count on the gate-finale export
     * should match the campaign's reported M37 (~124/102 run-to-run). Used to measure how
     * raising planner.m37.weight reduces mid-block room switches under the room-only phase-5 LS.
     */
    private static long countRoomChanges(List<Lesson> lessons) {
        Map<String, List<Lesson>> byCell = new HashMap<>();
        for (Lesson l : lessons) {
            if (l.timeslot == null || l.room == null || l.isLab()
                    || l.getStudentGroup() == null || l.getSubject() == null) {
                continue;
            }
            byCell.computeIfAbsent(l.getStudentGroup().id + "|" + l.getSubject() + "|" + l.timeslot.getDate(),
                            k -> new ArrayList<>())
                    .add(l);
        }
        long count = 0;
        for (List<Lesson> cell : byCell.values()) {
            for (int i = 0; i < cell.size(); i++) {
                for (int j = i + 1; j < cell.size(); j++) {
                    Lesson a = cell.get(i);
                    Lesson b = cell.get(j);
                    if (TimeslotSequence.isAdjacent(a.timeslot, b.timeslot)
                            && java.util.Objects.equals(a.getTeacher(), b.getTeacher())
                            && !java.util.Objects.equals(a.room, b.room)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Count placed lessons that are room-null AND non-lab AND not pinned AND not pseudo-teacher:
     * H29's filter (timeslot!=null && room==null && !pinned && teacher null-or-!pseudo) PLUS
     * !isLab(). H29 (was M36; the historical 547 = 291 non-lab + 256 lab count at medium is in
     * gate-l5-m36/VERDICT.md) narrows to the 291-non-lab subset the room-null trap left
     * unassigned. With H29 now HARD weight 1 (TASK-447), every null->room changeMove removes
     * -1 hard, so phase-5 LS assigns whenever a legal room exists; M37 medium only discourages
     * the change mid-block. TREND signal on a phase-5-only smoke, not the full B-ROOM.
     */
    private static long countNonLabRealRoomNull(List<Lesson> lessons) {
        return lessons.stream().filter(l -> l.timeslot != null && l.room == null && !l.pinned
                && !l.isLab()
                && (l.getTeacher() == null || !l.getTeacher().pseudo)).count();
    }

    /** Fase 3 (TASK-446 notte): the LAB counterpart of {@link #countNonLabRealRoomNull} -
     *  H29's filter PLUS isLab() (the 256 lab room-null of the gate-l5-m36 run). With the
     *  env-gated de-tag override, theory subjects move from this bucket to the non-lab bucket
     *  (aula, fillable), and freeing their lab room may let genuine lab-practice lessons
     *  (e.g. Laboratorio Termoidraulico) take it. */
    private static long countLabRealRoomNull(List<Lesson> lessons) {
        return lessons.stream().filter(l -> l.timeslot != null && l.room == null && !l.pinned
                && l.isLab()
                && (l.getTeacher() == null || !l.getTeacher().pseudo)).count();
    }

    /**
     * TASK-456 lab-fallback: NON-lab lessons sitting in a tagged (lab) room - the exact population
     * M-LF penalizes (and S10, softly). "before" on the cert is ~0 (the value domain was closed
     * when the cert was generated, so non-lab could never land in a lab); "after" with the flag ON
     * measures how many room-null ordinarie the phase-5 LS sheltered in a free lab. Mirrors the
     * M-LF filter EXACTLY (room!=null, tagged, !isLab - no timeslot check, since M-LF uses
     * forEachIncludingUnassigned without one) so the count tracks the constraint 1:1; pinned/lab
     * exclusions are NOT applied here (M-LF itself is not pinned-exempt), matching S10's population.
     */
    private static long countNonLabInTaggedRoom(List<Lesson> lessons) {
        return lessons.stream().filter(l -> l.room != null
                && l.room.isTagged() && !l.isLab()).count();
    }

    /**
     * Greedy room assignment: for each placed lesson, pick the first tag-compatible room
     * (the same filter {@link Lesson#getAvailableRooms()} applies) that has no other lesson
     * of THIS slice already in it at that timeslot, else the least-loaded such room. Spreads
     * load to minimize the H2 conflicts the LS then refines. Bypasses the CH (see the
     * allowsUnassigned null-trap note at the call site).
     */
    private void greedilyAssignRooms(List<Lesson> lessons, List<Room> rooms) {
        Map<Long, Map<Long, Integer>> loadByRoomTimeslot = new HashMap<>(); // roomId -> tsId -> count
        for (Lesson l : lessons) {
            if (l.timeslot == null || l.pinned) continue; // pinned keeps its (null) room; boxed skipped
            List<Room> candidates = l.getAvailableRooms();
            if (candidates.isEmpty()) continue;
            Room best = null;
            int bestLoad = Integer.MAX_VALUE;
            for (Room r : candidates) {
                int load = loadByRoomTimeslot.getOrDefault(r.id, Map.of()).getOrDefault(l.timeslot.id, 0);
                if (load == 0) { best = r; break; } // conflict-free preferred
                if (load < bestLoad) { bestLoad = load; best = r; }
            }
            l.room = best;
            // No cache invalidation needed: non-pinned lessons' getAvailableRooms() filters by
            // course tags (room-independent), and pinned lessons are skipped above.
            loadByRoomTimeslot.computeIfAbsent(best.id, k -> new HashMap<>())
                    .merge(l.timeslot.id, 1, Integer::sum);
        }
    }

    /**
     * SolverConfig with ONLY phase 5 (LS room refinement) = the phase-5 (room refinement)
     * {@code <localSearch>} of solverConfig.xml, replicated field-for-field (descriptive XML
     * anchors, not line numbers): union(room changeMove w2, room swapMove w1) + entityTabu 7 +
     * accepted 1000, termination capped to LS_BUDGET_SECONDS (production 600s). No phase-4 CH:
     * rooms are greedily pre-assigned by the harness (see the allowsUnassigned null-trap note), so
     * the LS has rooms to refine against H2/H-M2/M3/M-LA — the harness's actual goal.
     */
    private SolverConfig roomPhasesOnly(List<Room> rooms) {
        ChangeMoveSelectorConfig lsRoomChange = new ChangeMoveSelectorConfig()
                .withFixedProbabilityWeight(2.0) // phase-5 room changeMove weight
                .withEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withValueSelectorConfig(new ai.timefold.solver.core.config.heuristic.selector.value.ValueSelectorConfig()
                        .withVariableName("room")); // phase-5 room var
        SwapMoveSelectorConfig lsRoomSwap = new SwapMoveSelectorConfig()
                .withFixedProbabilityWeight(1.0) // phase-5 room swapMove weight
                .withEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withSecondaryEntitySelectorConfig(new EntitySelectorConfig(Lesson.class))
                .withVariableNameIncludes("room"); // phase-5 room var
        UnionMoveSelectorConfig lsMoveSelector = new UnionMoveSelectorConfig()
                .withMoveSelectorList(List.of(lsRoomChange, lsRoomSwap));
        LocalSearchPhaseConfig roomRefinement = new LocalSearchPhaseConfig()
                .withMoveSelectorConfig(lsMoveSelector)
                .withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(LS_BUDGET_SECONDS)) // smoke cap (prod 600s)
                .withAcceptorConfig(new LocalSearchAcceptorConfig().withEntityTabuSize(7)) // phase-5 entityTabuSize
                .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1000)); // phase-5 acceptedCountLimit

        return SnapshotFixture.applyEnvironmentMode(new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(new ArrayList<>(List.of(roomRefinement))));
    }
}
