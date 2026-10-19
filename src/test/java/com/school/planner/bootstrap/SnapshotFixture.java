package com.school.planner.bootstrap;

import jakarta.transaction.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import jakarta.persistence.EntityManager;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.service.GoldenScheduleImportService;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.solver.WeeklyTemplateBuilder;
import com.school.planner.solver.WeeklyTemplatePlanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared reconstruction of a solve/run snapshot from its committed pipe-format
 * artifacts into an in-memory {@link SchoolSchedule}, twin of {@link RealSeedFixture}
 * (same package, for the same package-private {@code DataGenerator} access via
 * {@link RealSeedFixture}). Extracted from {@link BoxRepairCheckpointTest}'s
 * private {@code runCheckpoint} body (and re-used by {@link
 * BoxVrpFeasibilityDiscriminatorTest}'s parse+seed step) so the new localized-test
 * harnesses (score-a-snapshot, ph2-smoke, ph5+6 room) can replay ANY committed
 * snapshot offline without re-solving.
 *
 * <p><b>Snapshot format (the contract this fixture consumes):</b> a directory with
 * <ul>
 * <li>{@code lessons.txt} — placed grid rows {@code group|date|hour|subject|
 *     teacher|pinned} (hour = wall-clock start hour 8-15, NOT slotIndex);</li>
 * <li>{@code boxed.txt} — un-taught (H0) lessons {@code group|teacher|subject}.</li>
 * </ul>
 * Exactly the format {@code capture-run.sh} writes and the KPI scripts read.
 *
 * <p><b>Fidelity (mirrors the two original tests' own documented fidelity):</b>
 * real school-year Timeslots + all 14 StudentGroups + GroupCalendarRules + real
 * Teachers with per-teacher availability (via {@link RealSeedFixture}); lessons and
 * courses are built in-memory with hand-assigned ids (Timefold needs a
 * {@code @PlanningId}; Hibernate is never asked to persist them); a pseudo "Stage"
 * teacher is added so boxed/placed rows referencing it resolve. No
 * DailyActivityQuota / expected-hours / school holidays / stage blackouts are
 * seeded here (faithful for the box/conflict/gap claims the snapshot harnesses
 * make; score-a-snapshot seeds them itself where the medium tier must be
 * comparable — see {@link ScoreASnapshotTest}).
 *
 * <p>Reconstruction is deterministic and takes seconds (not the 80s of a generated
 * fixture), and nothing here runs a local search.
 *
 * <p><b>Two snapshot sources, one deliberate fidelity/cost tradeoff:</b> {@link
 * #loadSnapshotNoClean} above reads the LIGHTWEIGHT pipe dump (no room, no lesson/course id,
 * hand-assigned ids), cheap and sufficient for the box/conflict/gap/template claims most
 * harnesses make. {@link #loadFromExport} reads the full {@code export.json.gz} (the {@code
 * GET /api/schedule/export} payload) instead: it is room- and id-FAITHFUL (real
 * room/null-room state, real {@code Course.isLab}/{@code requiredRoomTags}/{@code
 * labBlockSize}, the export's own Lesson/Course/Room ids preserved), at the cost of a larger
 * committed artifact and a bit more parsing. Use it when the claim under test is about rooms
 * (H2/H-M2/M3/M-LA) or needs real room state as a starting point, see the export-based mode
 * of {@code RoomPhaseSnapshotTest}.
 */
public final class SnapshotFixture {

    private SnapshotFixture() {
    }

    /** Default snapshot when a harness's env var is set to a plain enable flag ({@code 1}/{@code true})
     *  rather than an explicit path. The most recent committed run (Q22 world, the val M32 is tuned
     *  against). Shared by every snapshot harness (score-a-snapshot, ph2-smoke, room-phase). */
    static final String DEFAULT_SNAPSHOT =
            "claudedocs/spalmone-2026-27/task410-results/val410-run2";

    /** Resolve a snapshot dir from an env-var value: {@code null}/blank/{@code 1}/{@code true}
     *  (case-insensitive) means "use {@link #DEFAULT_SNAPSHOT}"; anything else is a snapshot path.
     *  The single place the harnesses' "1/true = default, else path" idiom lives. */
    public static Path resolveSnapshotDir(String envValue) {
        if (envValue == null) {
            return Path.of(DEFAULT_SNAPSHOT);
        }
        String v = envValue.trim();
        if (v.isEmpty() || v.equals("1") || v.equalsIgnoreCase("true")) {
            return Path.of(DEFAULT_SNAPSHOT);
        }
        return Path.of(v);
    }

    /** Env var selecting solve determinism for every ad-hoc offline {@link SolverConfig} this
     *  package builds ({@code REPRODUCIBLE}, default, or {@code NON_REPRODUCIBLE}). */
    static final String ENV_MODE_VAR = "PLANNER_TEST_ENV_MODE";
    /** Env var overriding the fixed seed used in {@code REPRODUCIBLE} mode (default {@code 0}). */
    static final String SEED_VAR = "PLANNER_TEST_SEED";

    /**
     * Make a harness's ad-hoc {@link SolverConfig}'s determinism EXPLICIT and DECLARED (printed
     * to stdout), instead of silently inheriting Timefold's own default.
     *
     * <p><b>Gap this closes (2026-07-25):</b> none of the offline harnesses in this package
     * ({@code Phase2SmokeSnapshotTest}, {@code Task415BenchmarkScreeningDemoTest}, {@code
     * Task419Stage1SpikeTest}, {@code RoomPhaseSnapshotTest}) ever called {@code
     * withEnvironmentMode} on their programmatic {@link SolverConfig}, so every one of them
     * silently defaulted to Timefold's own default, {@link EnvironmentMode#PHASE_ASSERT} (which
     * is reproducible and, per {@code DefaultSolverFactory}, uses a fixed default random seed
     * of 0 whenever no explicit seed is set), while production ({@code solverConfig.xml}) runs
     * {@link EnvironmentMode#NON_REPRODUCIBLE}. Every prior local screening therefore replayed
     * the exact same LS trajectory across "different" config variants, an unannounced confound
     * (two variants that scored differently were NOT proven to differ because of the config
     * change alone).
     *
     * <p>Default behavior is UNCHANGED (still reproducible, still seed 0), this only makes the
     * choice visible and gives a caller an explicit opt-out for production-realistic screening.
     *
     * @param config the harness's own {@link SolverConfig} (mutated in place and returned)
     */
    static SolverConfig applyEnvironmentMode(SolverConfig config) {
        String modeEnv = System.getenv(ENV_MODE_VAR);
        boolean nonReproducible = modeEnv != null && modeEnv.trim().equalsIgnoreCase("NON_REPRODUCIBLE");
        if (nonReproducible) {
            config.setEnvironmentMode(EnvironmentMode.NON_REPRODUCIBLE);
            System.out.println("  [env] environmentMode=NON_REPRODUCIBLE (seed n/a, " + ENV_MODE_VAR
                    + "=NON_REPRODUCIBLE)");
        } else {
            String seedEnv = System.getenv(SEED_VAR);
            long seed = (seedEnv == null || seedEnv.isBlank()) ? 0L : Long.parseLong(seedEnv.trim());
            config.setEnvironmentMode(EnvironmentMode.PHASE_ASSERT);
            config.setRandomSeed(seed);
            System.out.println("  [env] environmentMode=REPRODUCIBLE (PHASE_ASSERT) seed=" + seed
                    + " (default; set " + ENV_MODE_VAR + "=NON_REPRODUCIBLE for production-realistic runs)");
        }
        return config;
    }

    /** A reconstructed snapshot: the built {@link SchoolSchedule} plus the pieces the
     *  caller still needs (boxed lessons for per-id resolution, groups/teachers maps). */
    public static final class Snapshot {
        public final SchoolSchedule problem;
        public final List<Lesson> lessons;        // all (placed + boxed), hand-assigned ids
        public final List<Lesson> boxedLessons;   // subset with timeslot == null (H0)
        public final Map<String, StudentGroup> groups;
        public final Map<String, Teacher> teachers;
        public final List<Timeslot> timeslots;
        /**
         * Rows of the historical dump silently outside today's calendar grid
         * (whole date now closed - see the loader's TASK-408 skip). Train
         * review: tallied and exposed so a future calendar change can never
         * vaporize rows without a test noticing - harnesses with a known
         * expectation (e.g. 62/63 for val398/val410 after the 2027-01-22
         * closure fix) should assert it.
         */
        public final int skippedClosedDateRows;
        /**
         * The canonical Room list, faithful to the source, if this snapshot came from an
         * export ({@link #loadFromExport}). Empty for a pipe-format snapshot ({@link
         * #loadSnapshotNoClean}), which carries no room column at all; a caller that needs
         * rooms on a pipe snapshot builds its own (see {@code RealSeedFixture#seedRooms}).
         */
        public final List<Room> rooms;

        private Snapshot(SchoolSchedule problem, List<Lesson> lessons, List<Lesson> boxedLessons,
                         Map<String, StudentGroup> groups, Map<String, Teacher> teachers,
                         List<Timeslot> timeslots, int skippedClosedDateRows, List<Room> rooms) {
            this.problem = problem;
            this.lessons = lessons;
            this.boxedLessons = boxedLessons;
            this.groups = groups;
            this.teachers = teachers;
            this.timeslots = timeslots;
            this.skippedClosedDateRows = skippedClosedDateRows;
            this.rooms = rooms;
        }
    }

    /** One placed row of {@code lessons.txt}. */
    record PlacedRow(String group, LocalDate date, int hour, String subject, String teacher, boolean pinned) {
        static PlacedRow parse(String line) {
            String[] f = line.split("\\|");
            return new PlacedRow(f[0].trim(), LocalDate.parse(f[1].trim()), Integer.parseInt(f[2].trim()),
                    f[3].trim(), f[4].trim(), Boolean.parseBoolean(f[5].trim()));
        }
    }

    /** One boxed lesson of {@code boxed.txt}: {@code group|teacher|subject}. */
    record BoxedRow(String group, String teacher, String subject) {
        static BoxedRow parse(String line) {
            String[] f = line.split("\\|");
            return new BoxedRow(f[0].trim(), f[1].trim(), f[2].trim());
        }
    }

    /** Parse {@code lessons.txt} into placed rows (skips blank / {@code SET} marker lines). */
    static List<PlacedRow> parsePlacedRows(Path lessonsFile) throws Exception {
        assertTrue(Files.exists(lessonsFile), "missing artifact " + lessonsFile.toAbsolutePath());
        List<PlacedRow> rows = new ArrayList<>();
        for (String line : Files.readAllLines(lessonsFile)) {
            String t = line.trim();
            if (t.isEmpty() || t.equals("SET")) continue;
            rows.add(PlacedRow.parse(t));
        }
        return rows;
    }

    /** Parse {@code boxed.txt} into boxed rows (skips blank / {@code SET} marker lines). */
    static List<BoxedRow> parseBoxedRows(Path boxedFile) throws Exception {
        assertTrue(Files.exists(boxedFile), "missing artifact " + boxedFile.toAbsolutePath());
        List<BoxedRow> rows = new ArrayList<>();
        for (String line : Files.readAllLines(boxedFile)) {
            String t = line.trim();
            if (t.isEmpty() || t.equals("SET")) continue;
            rows.add(BoxedRow.parse(t));
        }
        return rows;
    }

    /**
     * Reconstruct a snapshot directory into a fully-wired in-memory {@link SchoolSchedule}:
     * seeds the real VRP fixture (timeslots/groups/calendars/teachers/availability),
     * builds placed + boxed Lessons in-memory with hand-assigned ids, wires every static
     * VRP snapshot {@link SchoolSchedule} reads during scoring, and returns the built
     * problem plus the boxed-lesson list (kept with its pre-solve {@code null} timeslot
     * so the caller can resolve them by id against a solved clone).
     *
     * <p>Assumes the caller already cleared the DB tables (the harnesses' own
     * {@code @BeforeEach}/{@code @AfterEach} {@link #clearTables} does this) — it reconstructs
     * without a double-delete.
     *
     * <p>{@code boxed.txt} is OPTIONAL: a snapshot dir with only {@code lessons.txt} (e.g. a
     * construction-end dump, {@code target/construction-lessons.txt} copied in as {@code
     * lessons.txt}) reconstructs with zero boxed lessons, so a pure-construction state is a valid
     * snapshot too.
     *
     * @param dir                      snapshot dir ({@code lessons.txt}, optional {@code boxed.txt})
     * @param availabilityExpansionService injected (expands recurring rules to concrete availability)
     * @param includeQ22GoldenDerivedRules false for snapshots produced before the Q22 rules
     *        existed (the snapshot legitimately scores H5 hard under them — not the claim)
     */
    public static Snapshot loadSnapshotNoClean(Path dir, AvailabilityExpansionService availabilityExpansionService,
                                        boolean includeQ22GoldenDerivedRules) throws Exception {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers =
                RealSeedFixture.seedTeachersAndAvailabilityRules(availabilityExpansionService, includeQ22GoldenDerivedRules);
        Teacher stage = new Teacher(GoldenScheduleImportService.STAGE_NAME);
        stage.pseudo = true;
        stage.persist();
        teachers.put(stage.name, stage);

        List<Timeslot> timeslots = Timeslot.listAll();
        Map<String, Timeslot> tsByDateHour = new HashMap<>();
        Set<LocalDate> gridDates = new HashSet<>();
        for (Timeslot ts : timeslots) {
            tsByDateHour.put(ts.getDate() + "@" + ts.startDateTime.getHour(), ts);
            gridDates.add(ts.getDate());
        }

        // ── Reconstruct the run's population in-memory (hand-assigned ids, no persist). ──
        Map<String, Course> courseByKey = new HashMap<>();
        List<Lesson> lessons = new ArrayList<>();
        long nextId = 1;
        int skippedClosedDateRows = 0;
        for (PlacedRow row : parsePlacedRows(dir.resolve("lessons.txt"))) {
            // TASK-408: the 2027-01-22 closure typo fix (golden 100% closed that
            // Friday) removed that date's Timeslots. These historical checkpoint
            // artifacts were captured under the old 172-day calendar and contain
            // phantom placements there (15 pinned Stage stubs + ~48 unpinned) that
            // are no longer valid grid cells - skip any row whose whole date is now
            // closed. A missing slot on an OPEN date is still a hard error below.
            if (!gridDates.contains(row.date())) {
                skippedClosedDateRows++;
                continue;
            }
            Course course = courseByKey.computeIfAbsent(row.group() + "|" + row.teacher() + "|" + row.subject, k -> {
                Course c = new Course(row.subject(), teachers.get(row.teacher()), groups.get(row.group()), 1);
                c.id = (long) (10_000 + courseByKey.size());
                return c;
            });
            Timeslot ts = tsByDateHour.get(row.date() + "@" + row.hour());
            assertTrue(ts != null, "no Timeslot for grid row " + row);
            Lesson lesson = new Lesson(course);
            lesson.id = nextId++;
            lesson.timeslot = ts;
            lesson.pinned = row.pinned();
            lessons.add(lesson);
        }

        List<Lesson> boxedLessons = new ArrayList<>();
        Path boxedFile = dir.resolve("boxed.txt");
        List<BoxedRow> boxedRows = Files.exists(boxedFile) ? parseBoxedRows(boxedFile) : List.of();
        for (BoxedRow row : boxedRows) {
            Course course = courseByKey.computeIfAbsent(row.group() + "|" + row.teacher() + "|" + row.subject, k -> {
                Course c = new Course(row.subject(), teachers.get(row.teacher()), groups.get(row.group()), 1);
                c.id = (long) (10_000 + courseByKey.size());
                return c;
            });
            Lesson lesson = new Lesson(course);
            lesson.id = nextId++;
            boxedLessons.add(lesson);
            lessons.add(lesson);
        }

        // ── Wire the static VRP snapshots (mirrors the discriminator/checkpoint tests). ──
        List<TeacherAvailability> teacherAvailabilities = TeacherAvailability.listAll();
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(new HashMap<>());
        SchoolSchedule.setTeacherAvailabilitiesStatic(teacherAvailabilities);
        SchoolSchedule.setSchoolHolidaysStatic(List.<SchoolHoliday>of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(GroupCalendarRule.listAll());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        // TASK-609 fidelity fix: the co-presenza depth map was never published by the
        // loaders (only wireConstructionStatics did), so the H2 guest exemption stayed
        // inert and legal guest pairs read as phantom -1000/couple H2 in EVERY replay
        // bench (ScoreASnapshotTest included). Same production loader, subjectToTag
        // derived from the reconstructed lessons themselves.
        SchoolSchedule.setGoldenCoPresenceDepthStatic(
                com.school.planner.service.ConstrainedTeacherAllocator.loadGoldenCoPresenceDepth(
                        buildSubjectToTag(lessons)));

        SchoolSchedule problem = new SchoolSchedule(timeslots, List.of(), List.of(), teacherAvailabilities,
                List.of(), StudentGroup.listAll(), Teacher.listAll(),
                new ArrayList<>(courseByKey.values()), lessons, List.of());

        if (skippedClosedDateRows > 0) {
            System.out.println("SnapshotFixture: skipped " + skippedClosedDateRows
                    + " historical rows on now-closed dates (see Snapshot.skippedClosedDateRows)");
        }
        return new Snapshot(problem, lessons, boxedLessons, groups, teachers, timeslots, skippedClosedDateRows,
                List.of());
    }

    // ─────────────────────────────── export-based loader (room/id fidelity) ───────────────────────────────

    /** One room row of the export's top-level {@code rooms} array (id/name/capacity/tags,
     *  see {@link Room}). */
    record ExportRoomRow(Long id, String name, int capacity, List<String> tags) {
    }

    /**
     * One lesson row of the export's {@code lessons} array. Unlike {@link PlacedRow}/{@link
     * BoxedRow} (the pipe format's flat {@code group|date|hour|subject|teacher|pinned} line,
     * no room concept at all, hand-assigned ids), every field here, including {@code roomId}/
     * {@code roomName} (null = unassigned room) and {@code date}/{@code hour} (null = H0
     * unassigned lesson), is read straight off the real exported {@code Lesson}/{@code Course}
     * JSON, so room and lab-tag fidelity ({@code isLab}/{@code requiredRoomTags}/{@code
     * labBlockSize}) is real, not defaulted.
     */
    record ExportLessonRow(Long id, Long courseId, String group, String teacher, String subject,
                           LocalDate date, Integer hour, boolean pinned, Long roomId, String roomName,
                           boolean isLab, List<String> requiredRoomTags, int labBlockSize,
                           boolean professionalizzanti) {
    }

    /** Parsed export document: the top-level {@code rooms} array (the full Room value range,
     *  independent of which rooms any one lesson actually uses) plus every {@code lessons} row
     *  (placed + boxed/unassigned, room-faithful). Pure parsing, no DB, no Quarkus, no {@link
     *  RealSeedFixture}, so it is unit-testable standalone. */
    record ParsedExport(List<ExportRoomRow> rooms, List<ExportLessonRow> lessons) {
    }

    /**
     * Gunzip + parse an {@code export.json.gz} (the {@code GET /api/schedule/export} payload,
     * see {@code ScheduleService#getFullScheduleForExport}) into {@link ParsedExport}.
     *
     * <p>Uses {@link JsonNode} tree-walking rather than binding straight to {@code
     * SchoolSchedule.class}: {@code Lesson}'s JSON carries several derived/redundant getters
     * ({@code subject}, {@code teacher}, {@code studentGroup}, {@code lab}, {@code
     * stageConfined}, a duplicate {@code labBlockSize}) that have no matching setter, and
     * walking the tree sidesteps that entirely (no {@code FAIL_ON_UNKNOWN_PROPERTIES}
     * configuration to get right, no risk of silently binding the wrong duplicate field).
     * Mandatory structural fields ({@code course}/{@code studentGroup}/{@code teacher}/{@code
     * subject}, and {@code id}/{@code name} on rooms) use {@code JsonNode.get} so a malformed
     * export fails loudly; optional/defaultable ones ({@code isLab}, {@code
     * requiredRoomTags}, {@code labBlockSize}, {@code professionalizzanti}, room {@code
     * capacity}/{@code tags}) use {@code JsonNode.path} so the minimal inline fixture the
     * parser unit test uses does not need to restate every field.
     */
    static ParsedExport parseExport(Path exportJsonGz) throws IOException {
        byte[] bytes;
        try (GZIPInputStream gz = new GZIPInputStream(Files.newInputStream(exportJsonGz))) {
            bytes = gz.readAllBytes();
        }
        JsonNode root = new ObjectMapper().readTree(bytes);

        List<ExportRoomRow> rooms = new ArrayList<>();
        for (JsonNode r : jsonArray(root.get("rooms"))) {
            List<String> tags = new ArrayList<>();
            for (JsonNode t : jsonArray(r.get("tags"))) tags.add(t.asText());
            rooms.add(new ExportRoomRow(r.get("id").asLong(), r.get("name").asText(),
                    r.path("capacity").asInt(0), tags));
        }

        List<ExportLessonRow> lessons = new ArrayList<>();
        for (JsonNode l : jsonArray(root.get("lessons"))) {
            JsonNode course = l.get("course");
            assertTrue(course != null && !course.isNull(), "export lesson " + l.get("id") + " has no course");
            JsonNode teacherNode = course.get("teacher");
            JsonNode groupNode = course.get("studentGroup");
            assertTrue(teacherNode != null && !teacherNode.isNull(), "export course has no teacher");
            assertTrue(groupNode != null && !groupNode.isNull(), "export course has no studentGroup");
            JsonNode timeslotNode = l.get("timeslot");
            JsonNode roomNode = l.get("room");

            LocalDate date = null;
            Integer hour = null;
            if (timeslotNode != null && !timeslotNode.isNull()) {
                date = LocalDate.parse(timeslotNode.get("date").asText());
                hour = LocalDateTime.parse(timeslotNode.get("startDateTime").asText()).getHour();
            }
            Long roomId = null;
            String roomName = null;
            if (roomNode != null && !roomNode.isNull()) {
                roomId = roomNode.get("id").asLong();
                roomName = roomNode.get("name").asText();
            }
            List<String> requiredRoomTags = new ArrayList<>();
            for (JsonNode t : jsonArray(course.get("requiredRoomTags"))) requiredRoomTags.add(t.asText());

            lessons.add(new ExportLessonRow(
                    l.get("id").asLong(),
                    course.get("id").asLong(),
                    groupNode.get("name").asText(),
                    teacherNode.get("name").asText(),
                    course.get("subject").asText(),
                    date, hour,
                    l.path("pinned").asBoolean(false),
                    roomId, roomName,
                    course.path("isLab").asBoolean(false),
                    requiredRoomTags,
                    course.path("labBlockSize").asInt(0),
                    course.path("professionalizzanti").asBoolean(false)));
        }
        return new ParsedExport(rooms, lessons);
    }

    /** Null/missing-safe array iteration helper for {@link #parseExport}. */
    private static List<JsonNode> jsonArray(JsonNode arrayNode) {
        List<JsonNode> out = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(out::add);
        }
        return out;
    }

    /**
     * Reconstruct an EXPORT snapshot ({@code export.json.gz}) into a fully-wired in-memory
     * {@link SchoolSchedule}, the room/id-faithful twin of {@link #loadSnapshotNoClean} (which
     * reconstructs from the lighter {@code lessons.txt}/{@code boxed.txt} pipe dump). See the
     * class javadoc "Two snapshot sources" section for the full tradeoff.
     *
     * <p>Groups/teachers/timeslots/calendar rules/availability are seeded exactly like {@link
     * #loadSnapshotNoClean} (via {@link RealSeedFixture}, real 2026/27 school data, persisted
     * to the H2 test DB): those cross-referenced entities cannot reuse the export's own
     * DB-assigned ids (a freshly seeded test DB hands out its own sequence), so Lessons are
     * matched to them by NATURAL KEY (group/teacher name, timeslot date+hour), the same
     * technique the pipe loader uses.
     *
     * <p><b>Rooms are the exception.</b> They are NOT re-seeded via {@code
     * RealSeedFixture.seedRooms()}'s hardcoded canonical list. They are built directly from the
     * export's own {@code rooms} array (id/name/capacity/tags preserved verbatim); the export's
     * own room ids are self-consistent within one document, so a Lesson's embedded {@code room}
     * reference is matched back to the canonical list BY ID, and a slice of a run that used
     * non-standard rooms is reproduced faithfully rather than silently substituted. {@code
     * Course.isLab}/{@code requiredRoomTags}/{@code labBlockSize} are likewise copied from the
     * export's own {@code Course}, not left at the pipe loader's zero-value default (that gap
     * is exactly why the pipe-based {@code RoomPhaseSnapshotTest} mode starts hundreds of
     * lessons room-null before its greedy pre-assign, see that class's own javadoc). {@code
     * Lesson.id}/{@code Course.id} also reuse the export's own values (no hand-assigned
     * sequence needed, since the export already guarantees uniqueness within each type).
     *
     * <p>Not carried over from the export by this loader: {@code GroupCalendarRule} (present
     * in the export payload since TASK-454, but still not read here: the static stays seeded
     * from today's real calendar via {@link RealSeedFixture}, exactly like the pipe path;
     * switch to {@code export.getGroupCalendarRules()} if you ever need to replay a
     * historical calendar), {@code DailyActivityQuota} and
     * {@code LabAssistant} (present in the export but not read here; a caller that needs the
     * medium tier comparable can layer {@link #seedMediumTierFacts} on top exactly as it does
     * for a pipe snapshot, since that helper only needs {@code snap.lessons}/{@code
     * snap.timeslots}).
     *
     * @param exportJsonGz path to a committed/captured {@code export.json.gz}
     */
    static Snapshot loadFromExport(Path exportJsonGz, AvailabilityExpansionService availabilityExpansionService,
                                   boolean includeQ22GoldenDerivedRules) throws Exception {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers =
                RealSeedFixture.seedTeachersAndAvailabilityRules(availabilityExpansionService, includeQ22GoldenDerivedRules);
        Teacher stage = new Teacher(GoldenScheduleImportService.STAGE_NAME);
        stage.pseudo = true;
        stage.persist();
        teachers.put(stage.name, stage);

        List<Timeslot> timeslots = Timeslot.listAll();
        Map<String, Timeslot> tsByDateHour = new HashMap<>();
        Set<LocalDate> gridDates = new HashSet<>();
        for (Timeslot ts : timeslots) {
            tsByDateHour.put(ts.getDate() + "@" + ts.startDateTime.getHour(), ts);
            gridDates.add(ts.getDate());
        }

        ParsedExport export = parseExport(exportJsonGz);

        // ── Canonical Rooms sourced from the export itself (not RealSeedFixture.seedRooms()). ──
        List<Room> rooms = new ArrayList<>();
        Map<Long, Room> roomById = new HashMap<>();
        for (ExportRoomRow row : export.rooms()) {
            Room room = new Room(row.name(), row.capacity(), row.tags());
            room.id = row.id();
            rooms.add(room);
            roomById.put(row.id(), room);
        }

        // ── Reconstruct the run's population in-memory (export's own ids, no persist). ──
        Map<String, Course> courseByKey = new HashMap<>();
        List<Lesson> lessons = new ArrayList<>();
        List<Lesson> boxedLessons = new ArrayList<>();
        int skippedClosedDateRows = 0;
        for (ExportLessonRow row : export.lessons()) {
            // Same TASK-408 skip as loadSnapshotNoClean: a historical row on a date the
            // current calendar has since closed is no longer a valid grid cell.
            if (row.date() != null && !gridDates.contains(row.date())) {
                skippedClosedDateRows++;
                continue;
            }
            StudentGroup group = groups.get(row.group());
            Teacher teacher = teachers.get(row.teacher());
            assertTrue(group != null, "no StudentGroup for export row group=" + row.group());
            assertTrue(teacher != null, "no Teacher for export row teacher=" + row.teacher());

            Course course = courseByKey.computeIfAbsent(row.group() + "|" + row.teacher() + "|" + row.subject(),
                    k -> {
                        Course c = new Course(row.subject(), teacher, group, 1);
                        c.id = row.courseId();
                        c.isLab = row.isLab();
                        c.requiredRoomTags = new ArrayList<>(row.requiredRoomTags());
                        c.labBlockSize = row.labBlockSize();
                        c.professionalizzanti = row.professionalizzanti();
                        return c;
                    });

            Lesson lesson = new Lesson(course);
            lesson.id = row.id();
            lesson.pinned = row.pinned();
            if (row.date() != null && row.hour() != null) {
                Timeslot ts = tsByDateHour.get(row.date() + "@" + row.hour());
                assertTrue(ts != null, "no Timeslot for export grid row " + row);
                lesson.timeslot = ts;
            }
            if (row.roomId() != null) {
                Room room = roomById.get(row.roomId());
                assertTrue(room != null, "no canonical Room for export roomId=" + row.roomId()
                        + " (" + row.roomName() + ")");
                lesson.room = room;
            }
            lessons.add(lesson);
            if (lesson.timeslot == null) boxedLessons.add(lesson);
        }

        // ── Wire the static VRP snapshots (mirrors loadSnapshotNoClean, plus real Rooms). ──
        List<TeacherAvailability> teacherAvailabilities = TeacherAvailability.listAll();
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(rooms);
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(new HashMap<>());
        SchoolSchedule.setTeacherAvailabilitiesStatic(teacherAvailabilities);
        SchoolSchedule.setSchoolHolidaysStatic(List.<SchoolHoliday>of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(GroupCalendarRule.listAll());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        // TASK-609 fidelity fix: the co-presenza depth map was never published by the
        // loaders (only wireConstructionStatics did), so the H2 guest exemption stayed
        // inert and legal guest pairs read as phantom -1000/couple H2 in EVERY replay
        // bench (ScoreASnapshotTest included). Same production loader, subjectToTag
        // derived from the reconstructed lessons themselves.
        SchoolSchedule.setGoldenCoPresenceDepthStatic(
                com.school.planner.service.ConstrainedTeacherAllocator.loadGoldenCoPresenceDepth(
                        buildSubjectToTag(lessons)));

        SchoolSchedule problem = new SchoolSchedule(timeslots, rooms, List.of(), teacherAvailabilities,
                List.of(), StudentGroup.listAll(), Teacher.listAll(),
                new ArrayList<>(courseByKey.values()), lessons, List.of());

        if (skippedClosedDateRows > 0) {
            System.out.println("SnapshotFixture.loadFromExport: skipped " + skippedClosedDateRows
                    + " historical rows on now-closed dates (see Snapshot.skippedClosedDateRows)");
        }
        return new Snapshot(problem, lessons, boxedLessons, groups, teachers, timeslots, skippedClosedDateRows,
                rooms);
    }

    /**
     * Seed the real VRP fixture (timeslots/groups/calendars/teachers/availability) and wire
     * the static VRP snapshots for a PROBE-based harness that does NOT reconstruct the full
     * placed population (e.g. {@link BoxVrpFeasibilityDiscriminatorTest}: it builds throw-away
     * {@code Course}/{@code Lesson} probes against the parsed grid's own occupancy, so the
     * pinned-lesson map is intentionally empty and no placed lessons are wired). Distinct from
     * {@link #loadSnapshotNoClean}, which wires the full reconstructed population.
     */
    static RealSeedStatics seedAndWireForVrpProbe(AvailabilityExpansionService availabilityExpansionService,
                                                 boolean includeQ22GoldenDerivedRules) {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers =
                RealSeedFixture.seedTeachersAndAvailabilityRules(availabilityExpansionService, includeQ22GoldenDerivedRules);
        List<Timeslot> timeslots = Timeslot.listAll();
        List<TeacherAvailability> teacherAvailabilities = TeacherAvailability.listAll();
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setTeacherAvailabilitiesStatic(teacherAvailabilities);
        SchoolSchedule.setSchoolHolidaysStatic(List.<SchoolHoliday>of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), Set.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(GroupCalendarRule.listAll());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        return new RealSeedStatics(groups, teachers, timeslots, teacherAvailabilities);
    }

    /** The real-seed outputs a probe harness needs back (groups/teachers for building probes). */
    record RealSeedStatics(Map<String, StudentGroup> groups, Map<String, Teacher> teachers,
                           List<Timeslot> timeslots, List<TeacherAvailability> teacherAvailabilities) {
    }

    /** Clear the DB tables the real-seed step (and the quota-seeding callers) populate.
     *  DailyActivityQuota is deleted before StudentGroup because it FK-references it
     *  (a caller that seeded quota facts would otherwise block the group delete). */
    public static void clearTables(EntityManager em) {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        // TASK-593: the witness bench seeds these three; leaving them alive would
        // double-seed on re-run and leak rows into the shared H2 (review finding:
        // FK note for DailyActivityQuota applies identically to StagePattern)
        em.createQuery("DELETE FROM SchoolHoliday").executeUpdate();
        em.createQuery("DELETE FROM StagePattern").executeUpdate();
        em.createQuery("DELETE FROM Room").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM RecurringAvailabilityRule").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM DailyActivityQuota").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
    }

    /** Reset every static VRP snapshot {@link SchoolSchedule} holds that has a public setter
     *  (call in teardown). Sets EMPTY collections (not null): several consumers read these
     *  statics with {@code .contains()} without a null guard, so {@link SchoolSchedule#clearStaticState()}
     *  (which nulls them) NPEs when a later consumer reads before re-init. The fields without a
     *  public setter (labAssistant ids, available-timeslot counts, the template/expected-hours
     *  maps) are NOT cleared here — callers that seed them (score-a-snapshot, ph2-smoke) overwrite
     *  them at setup, and clearStaticState() between benchmark runs is the full-reset path. */
    public static void resetStatics() {
        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(List.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setTeacherAvailabilitiesStatic(List.of());
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(null);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        // TASK-536: a day-floor map left behind would arm H33 in the NEXT harness run.
        SchoolSchedule.setDayFloorsStatic(Map.of());
        SchoolSchedule.setWeeklyTemplateStatic(Map.of(), Map.of());
        // TASK-419: a promoted-pin registry left behind would strip the pinned
        // exemptions of every constraint in the NEXT harness run.
        SchoolSchedule.setPromotedLessonIdsStatic(List.of());
        // TASK-464: a golden co-presenza depth map left behind would let a later harness run
        // that sets the flag ON without publishing its own map inherit a stale depth.
        SchoolSchedule.setGoldenCoPresenceDepthStatic(Map.of());
        // TASK-497: same stale-inheritance shape - a pool-cap target map (and its captured
        // armed flag) left behind would price the next dataset's (group,date) cells against
        // the previous dataset's groups after a clearTables + re-seed (ids restart at 1 and
        // collide). Clearing happens via clearM20CapPoolStatics (also nulls the ratio map,
        // the TASK-461 sibling gap the same review surfaced).
        SchoolSchedule.clearM20CapPoolStatics();
    }

    /**
     * Publish the pre-solve weekly template + per-course scarcity weight that M32
     * (template deviation, TASK-413) scores against — a faithful mirror of {@code
     * ScheduleService.publishWeeklyTemplateStatics}, which runs at the same pre-solve point
     * in production. WITHOUT this, a snapshot scored offline scores M32 against an EMPTY
     * template (every unpinned templated lesson counted as a deviation), inflating M32's
     * medium contribution — silently wrong, not crashing. Call this AFTER the quota/expected
     * facts are seeded and {@link SchoolSchedule#computeLessonAvailableTimeslotCounts} has run
     * (the scarcity weight reads the VRP counts). No-op for harnesses that don't care about
     * the medium tier (BoxRepair/BoxVrp never call it — M32 is not their claim).
     *
     * <p>Uses {@link WeeklyTemplatePlanner#buildReference} (TASK-416): the CH-mode
     * template population is {@code timeslot == null}, which is empty on a FINAL snapshot,
     * so CH mode would publish an empty template and M32 would score ~0. Reference mode is
     * assignment-agnostic (population = every unpinned lesson), so M32 scores against the
     * real template on a final snapshot just as it does in a live solve.
     */
    static void publishWeeklyTemplateStatics(List<Lesson> lessons, List<Timeslot> timeslots) {
        Set<LocalDate> holidays = SchoolSchedule.getSchoolHolidaysStatic() == null
                ? Set.of() : new HashSet<>(SchoolSchedule.getSchoolHolidaysStatic());
        Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> template =
                WeeklyTemplatePlanner.buildReference(lessons, timeslots, holidays, List.of());

        Map<Long, Set<Integer>> cellsByCourse = new HashMap<>();
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
    }

    /**
     * Seed the production quota + expected-hours facts and re-publish the VRP/template statics so a
     * snapshot's MEDIUM tier is comparable to a live solve (M20/M12 score against the real per-date
     * target instead of the calendar fallback). Mirrors {@code GoldenSolutionRegressionTest}'s two
     * {@code generateDailyActivityQuotas}/{@code generateExpectedDailyHours} calls and
     * {@code ScheduleService}'s pre-solve static publishing. Shared by {@link ScoreASnapshotTest}
     * and {@link Phase2SmokeSnapshotTest} (identical setup).
     *
     * <p>M32 (template deviation) IS made comparable by this call on a FINAL snapshot since
     * TASK-416: {@link #publishWeeklyTemplateStatics} now builds the reference template in an
     * assignment-agnostic way ({@link WeeklyTemplatePlanner#buildReference}), so a final snapshot
     * scores M32 against the real pre-solve template just like a live solve.
     */
    /** The production subject-to-single-tag map from the snapshot's own lessons. */
    private static java.util.Map<String, String> buildSubjectToTag(List<Lesson> lessons) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        for (Lesson l : lessons) {
            if (l.course != null && l.course.subject != null
                    && l.course.requiredRoomTags != null && l.course.requiredRoomTags.size() == 1) {
                m.putIfAbsent(l.course.subject, l.course.requiredRoomTags.get(0));
            }
        }
        return m;
    }

    public static void seedMediumTierFacts(DataGenerator dataGenerator, Snapshot snap) {
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
        List<DailyActivityQuota> quotas = DailyActivityQuota.listAll();
        SchoolSchedule.setDailyActivityQuotasStatic(quotas);
        // The snapshot's SchoolSchedule was built BEFORE this seeding, so its
        // @ProblemFactCollectionProperty quota list is empty and every
        // forEach(DailyActivityQuota) constraint (M20.2, M30.1/M30.2) scores 0
        // no matter what the statics say (M20/dailyQuota reads the statics and
        // is unaffected). Discovered 2026-07-26 re-scoring val419-staged-run1:
        // live M20.2 -164k/31 vs snapshot 0/0. Attach the facts to the solution
        // so fact-driven constraints see them too.
        snap.problem.setDailyActivityQuotas(quotas);
        // Re-publish the available-timeslot counts now that quota facts exist (the VRP filter
        // consults them): the snapshot's lessons were built before this seeding.
        SchoolSchedule.computeLessonAvailableTimeslotCounts(snap.lessons);
        // Publish the pre-solve weekly template so M32 scores against the real template, not an
        // empty one. Mirrors ScheduleService; assignment-agnostic reference mode (TASK-416) so it
        // is comparable on a final snapshot too.
        publishWeeklyTemplateStatics(snap.lessons, snap.timeslots);
        // TASK-609 fidelity fix #3: H4/H30 were structurally dead in every replay bench
        // (the loaders pass List.of() for StagePattern because RealSeedFixture seeds none:
        // the code-review "silently-empty statics are how a bench lies" lesson, TASK-597).
        // Derive the patterns from the quota rows just generated (stageHours > 0 dates,
        // contiguous ranges per group) and arm BOTH the static and the problem facts.
        List<StagePattern> stagePatterns = productionStagePatterns(snap.groups);
        SchoolSchedule.setStageBlackoutDatesStatic(stagePatterns, java.util.Set.of());
        snap.problem.setStagePatterns(stagePatterns);
    }

    /**
     * The production StagePatterns, mirrored from {@code DataGenerator.generateStagePatterns}
     * (protected file: this is a faithful copy, not a call). Deriving them from quota rows
     * was tried and REJECTED: production seeds patterns for only 3 of 14 groups ON PURPOSE
     * (the S-day VRP filter carries the other groups' confinement, H23/H24/H25), and a
     * derive-from-quotas arms 11 extra groups with H4 blackout windows that the real model
     * never had (+94 phantom hard on the m39-off replay, caught by the step1 -11 pin).
     */
    static List<StagePattern> productionStagePatterns(Map<String, StudentGroup> groups) {
        List<StagePattern> out = new ArrayList<>();
        addPattern(out, groups.get("G07"),
                LocalDate.of(2027, 3, 23), LocalDate.of(2027, 5, 28),
                java.util.Set.of(java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
                        java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY));
        addPattern(out, groups.get("G11"),
                LocalDate.of(2027, 1, 13), LocalDate.of(2027, 3, 19),
                java.util.Set.of(java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
                        java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY));
        // Wednesday deliberately NOT in the mask (TASK-370: see DataGenerator for the
        // mechanical derivation - adding it blows up H4's calibrated golden pin).
        addPattern(out, groups.get("G12"),
                LocalDate.of(2026, 11, 20), LocalDate.of(2027, 4, 17),
                java.util.Set.of(java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY));
        return out;
    }

    private static void addPattern(List<StagePattern> out, StudentGroup group,
            LocalDate start, LocalDate end, java.util.Set<java.time.DayOfWeek> mask) {
        if (group != null) {
            out.add(new StagePattern(group, start, end, mask));
        }
    }

    /** Weekly-recurrence KPI result: distinct recurring cells, total placed cells, and the percent. */
    record RecurrenceKpi(int recurring, int total, double percent) {
    }

    /**
     * Weekly recurrence among placed real+pseudo lessons — the single shared definition used by
     * {@link Phase2SmokeSnapshotTest} and {@code WeeklyTemplateConstructionRealSeedTest}. A cell
     * (group, date, slotIndex) recurs when the SAME group/slotIndex cell exactly 7 days earlier
     * carries the SAME teacher AND subject.
     */
    static RecurrenceKpi weeklyRecurrence(List<Lesson> lessons) {
        Map<String, Lesson> byGroupDateSlot = new HashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.timeslot == null || lesson.getStudentGroup() == null) continue;
            int slotIndex = TimeslotSequence.slotIndexOf(lesson.timeslot);
            byGroupDateSlot.put(lesson.getStudentGroup().id + "@" + lesson.timeslot.getDate() + "@" + slotIndex, lesson);
        }
        int total = 0;
        int recurring = 0;
        for (Lesson lesson : lessons) {
            if (lesson.timeslot == null || lesson.getStudentGroup() == null) continue;
            total++;
            int slotIndex = TimeslotSequence.slotIndexOf(lesson.timeslot);
            LocalDate priorWeek = lesson.timeslot.getDate().minusDays(7);
            Lesson prior = byGroupDateSlot.get(lesson.getStudentGroup().id + "@" + priorWeek + "@" + slotIndex);
            if (prior == null) continue;
            boolean sameTeacher = java.util.Objects.equals(
                    prior.getTeacher() != null ? prior.getTeacher().id : null,
                    lesson.getTeacher() != null ? lesson.getTeacher().id : null);
            if (sameTeacher && java.util.Objects.equals(prior.getSubject(), lesson.getSubject())) recurring++;
        }
        double percent = total == 0 ? 0 : 100.0 * recurring / total;
        return new RecurrenceKpi(recurring, total, percent);
    }

    /**
     * Write a placed grid back out in the SAME pipe format this fixture consumes
     * ({@code group|date|hour|subject|teacher|pinned}, {@code SET} header), so a harness's
     * OUTCOME can be fed to the {@code scripts/analyze/} KPI scripts (daily-fill / deep-day
     * histogram / resemblance / cadence) without a server or a re-solve. Round-trips into
     * {@link #parsePlacedRows}: {@code hour} is the wall-clock start hour, not the slotIndex.
     *
     * <p>Sorted by (group, date, hour), the same convention the other two pipe-format dump
     * writers use ({@link com.school.planner.solver.PhaseTransitionMarker#writeTransitionDump}
     * in main, {@link WeeklyTemplateConstructionRealSeedTest}'s construction-end dump) so all
     * three are diff-comparable rather than in insertion order.
     */
    public static void writePlacedGrid(List<Lesson> lessons, Path out) throws IOException {
        List<Lesson> placed = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (lesson.timeslot == null || lesson.getStudentGroup() == null) continue;
            placed.add(lesson);
        }
        placed.sort(Comparator
                .comparing((Lesson l) -> l.getStudentGroup().name)
                .thenComparing(l -> l.timeslot.getDate())
                .thenComparingInt(l -> l.timeslot.startDateTime.getHour()));
        StringBuilder sb = new StringBuilder("SET\n");
        for (Lesson lesson : placed) {
            sb.append(lesson.getStudentGroup().name).append('|')
                    .append(lesson.timeslot.getDate()).append('|')
                    .append(lesson.timeslot.startDateTime.getHour()).append('|')
                    .append(lesson.getSubject()).append('|')
                    .append(lesson.getTeacher() != null ? lesson.getTeacher().name : "").append('|')
                    .append(lesson.pinned).append('\n');
        }
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString());
    }

    // ── TASK-597 shared bench counters (hoisted from the 6 private copies) ──

    /**
     * TASK-597: the construction-bench family's per-prefix constraint-match
     * counter (6th-copy hoist). Match COUNT, prefix semantics, -1 on exception:
     * byte-identical to the private copies it replaces. NOTE for new benches:
     * match counts read compliant groupBy days as violations (H7-style) and
     * prefixes collide (H3 absorbs H30-H34) - prefer {@link #providerHardPoints}.
     */
    public static int providerCount(
            ai.timefold.solver.core.api.solver.SolutionManager<com.school.planner.domain.SchoolSchedule,
                    ai.timefold.solver.core.api.score.BendableScore> solutionManager,
            com.school.planner.domain.SchoolSchedule schedule, String constraintPrefix) {
        var factory = ((ai.timefold.solver.core.impl.solver.DefaultSolutionManager<com.school.planner.domain.SchoolSchedule,
                ai.timefold.solver.core.api.score.BendableScore>) solutionManager)
                .getScoreDirectorFactory();
        try (var sd = factory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            sd.setWorkingSolution(schedule);
            sd.calculateScore();
            for (var t : sd.getConstraintMatchTotalMap().values()) {
                if (t.getConstraintRef().id().startsWith(constraintPrefix)) {
                    return t.getConstraintMatchCount();
                }
            }
        } catch (Exception e) {
            System.out.printf("DIAG countByProvider(%s) exception: %s%n", constraintPrefix, e.getMessage());
        }
        return -1;
    }

    /**
     * TASK-597: the match-COUNT multi-family form (one rescore, exact ID-token):
     * per-family DAY/cell counts, the units the census and sibling benches print
     * (H33=3 reads "3 days below floor", NOT hard points). Distinct from
     * {@link #providerHardPoints}: match counts read compliant groupBy days as
     * violations if a family is missing from the set - pass exactly the tokens
     * you mean to read.
     */
    public static java.util.Map<String, Integer> providerMatchCounts(
            ai.timefold.solver.core.api.solver.SolutionManager<com.school.planner.domain.SchoolSchedule,
                    ai.timefold.solver.core.api.score.BendableScore> solutionManager,
            com.school.planner.domain.SchoolSchedule schedule, java.util.Set<String> familyTokens) {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        var factory = ((ai.timefold.solver.core.impl.solver.DefaultSolutionManager<com.school.planner.domain.SchoolSchedule,
                ai.timefold.solver.core.api.score.BendableScore>) solutionManager)
                .getScoreDirectorFactory();
        try (var sd = factory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            sd.setWorkingSolution(schedule);
            sd.calculateScore();
            for (var t : sd.getConstraintMatchTotalMap().values()) {
                String token = t.getConstraintRef().id().split(" - ")[0];
                if (familyTokens.contains(token)) {
                    out.merge(token, t.getConstraintMatchCount(), Integer::sum);
                }
            }
        } catch (Exception e) {
            throw new AssertionError("provider rescore failed", e);
        }
        return out;
    }

    /**
     * TASK-597: the full-oracle form (born in WitnessBenchTest's oracle v3):
     * HARD POINTS per family (hard level 0 sums via ScoreCompat.hardOf - match
     * counts read compliant groupBy days as violations), EXACT ID-token match
     * (prefixes collide: "H3" absorbs H30-H34), fail-loud exceptions (a rescore
     * failure is a harness failure, not a search negative).
     */
    public static java.util.Map<String, Integer> providerHardPoints(
            ai.timefold.solver.core.api.solver.SolutionManager<com.school.planner.domain.SchoolSchedule,
                    ai.timefold.solver.core.api.score.BendableScore> solutionManager,
            com.school.planner.domain.SchoolSchedule schedule, java.util.Set<String> familyTokens) {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        var factory = ((ai.timefold.solver.core.impl.solver.DefaultSolutionManager<com.school.planner.domain.SchoolSchedule,
                ai.timefold.solver.core.api.score.BendableScore>) solutionManager)
                .getScoreDirectorFactory();
        try (var sd = factory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            sd.setWorkingSolution(schedule);
            sd.calculateScore();
            for (var t : sd.getConstraintMatchTotalMap().values()) {
                String token = t.getConstraintRef().id().split(" - ")[0];
                if (familyTokens.contains(token)) {
                    out.merge(token, (int) -com.school.planner.solver.ScoreCompat.hardOf(
                            (ai.timefold.solver.core.api.score.BendableScore) t.getScore()), Integer::sum);
                }
            }
        } catch (Exception e) {
            throw new AssertionError("provider rescore failed", e);
        }
        return out;
    }

    // ── TASK-597 shared construction-bench wiring (hoisted from the 8 copies) ──

    /** The bench wiring bundle (the old per-bench Wires records, unified). */
    public record BenchWires(List<com.school.planner.domain.Timeslot> timeslots,
            List<com.school.planner.domain.StudentGroup> groups,
            List<com.school.planner.domain.Teacher> teachers,
            List<com.school.planner.domain.Course> courses,
            List<com.school.planner.domain.TeacherAvailability> availabilities,
            List<com.school.planner.domain.DailyActivityQuota> quotas,
            List<com.school.planner.domain.Room> rooms) {}

    /**
     * TASK-597 hoist of the bench-family wireStatics: publishes every static the
     * construction benches consult, from the inputs the caller supplies. The
     * DIVERGENT axes stay explicit so each bench keeps its exact prior behavior:
     * pass {@code Room.listAll()} or {@code List.of()} for rooms, and the
     * holidays/stage-patterns/co-presenza depth maps you actually seed (the
     * empty forms leave those constraint families inert - the WitnessBench
     * review found the silently-empty variants are how a bench lies). ALWAYS
     * publishes the pinned-cells static from the lessons passed in; any bench
     * that runs a pre-assignment AFTER this call MUST re-publish it
     * (re-invoke with the post-pre-assign lesson list, or the H3 VRP
     * exclusion stays disarmed - the exact shape of the TASK-590 review
     * finding 1).
     */
    public static BenchWires wireConstructionStatics(
            List<com.school.planner.domain.Timeslot> timeslots,
            List<com.school.planner.domain.StudentGroup> groups,
            List<com.school.planner.domain.Lesson> lessons,
            List<com.school.planner.domain.Teacher> teachers,
            List<com.school.planner.domain.Course> courses,
            List<com.school.planner.domain.TeacherAvailability> availabilities,
            List<com.school.planner.domain.Room> rooms,
            List<com.school.planner.domain.SchoolHoliday> holidays,
            List<com.school.planner.domain.StagePattern> stagePatterns,
            java.util.Map<String, Integer> coPresenceDepth,
            boolean publishFloors, boolean publishPoolCap) {
        for (var g : groups) {
            g.idealEndDateVrpBuffered = com.school.planner.solver.TimeslotSequence.SCHOOL_YEAR_END;
        }
        com.school.planner.domain.SchoolSchedule.setAllTimeslotsStatic(timeslots);
        com.school.planner.domain.SchoolSchedule.setAllRoomsStatic(rooms);
        com.school.planner.domain.SchoolSchedule.setAllLessonsStatic(lessons);
        com.school.planner.domain.SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        com.school.planner.domain.SchoolSchedule.setIdealEndDateVrpBufferedStatic(java.util.Map.of());
        com.school.planner.domain.SchoolSchedule.setTeacherAvailabilitiesStatic(availabilities);
        java.util.Set<java.time.LocalDate> holidayDates = new java.util.HashSet<>();
        for (var h : holidays) {
            holidayDates.add(h.date);
        }
        com.school.planner.domain.SchoolSchedule.setSchoolHolidaysStatic(holidays);
        com.school.planner.domain.SchoolSchedule.setStageBlackoutDatesStatic(stagePatterns, holidayDates);
        com.school.planner.domain.SchoolSchedule.setTimeslotSequenceStatic(
                new com.school.planner.solver.TimeslotSequence(timeslots));
        com.school.planner.domain.SchoolSchedule.setGroupCalendarRulesStatic(
                com.school.planner.domain.GroupCalendarRule.listAll());
        List<com.school.planner.domain.DailyActivityQuota> quotas =
                com.school.planner.domain.DailyActivityQuota.listAll();
        com.school.planner.domain.SchoolSchedule.setDailyActivityQuotasStatic(quotas);
        com.school.planner.domain.SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);
        if (coPresenceDepth != null) {
            com.school.planner.domain.SchoolSchedule.setGoldenCoPresenceDepthStatic(coPresenceDepth);
        }
        if (publishFloors) {
            java.util.Map<String, Integer> floorsByName = com.school.planner.service.DayFloorDeriver
                    .deriveHardFloors(groups, courses, quotas);
            java.util.Map<Long, Integer> floorsById = new java.util.HashMap<>();
            for (var g : groups) {
                Integer floor = floorsByName.get(g.name);
                if (floor != null && floor > 0) {
                    floorsById.put(g.id, floor);
                }
            }
            com.school.planner.domain.SchoolSchedule.setDayFloorsStatic(floorsById);
        }
        if (publishPoolCap) {
            com.school.planner.domain.SchoolSchedule.setM20PoolCapTargetsStatic(
                    com.school.planner.solver.SchoolScheduleConstraintProvider
                            .computeM20PoolCapTargetsByGroup(groups, lessons, quotas));
            com.school.planner.domain.SchoolSchedule.captureM20CapPoolFlag();
        }
        return new BenchWires(timeslots, groups, teachers, courses, availabilities, quotas, rooms);
    }

    /**
     * TASK-597 hoist of the incarichi course-generation loop (SubjectRegistry
     * classification included: a course created without it leaves H-M2/H23/H24
     * structurally inert - the WitnessBench review finding). The subjectRegistry
     * may be null for legacy benches that never classified (their behavior
     * stays as-is); new benches must pass the injected registry.
     */
    // NOTE: no @Transactional - CDI interceptors never apply to static methods.
    // The CALLER must be @Transactional (all three current callers are); the
    // requirement is documented here because it was implicit in the pre-hoist
    // inline loops, which lived inside the callers' transactions.
    public static void seedCoursesFromIncarichi(Map<String, com.school.planner.domain.Teacher> teachers,
            Map<String, com.school.planner.domain.StudentGroup> groups,
            com.school.planner.service.LessonGenerator lessonGenerator,
            com.school.planner.service.SubjectRegistry subjectRegistry) {
        if (subjectRegistry == null) {
            System.out.println("seedCoursesFromIncarichi: NO SubjectRegistry - H-M2/H23/H24 stay inert (legacy bench behavior)");
        }
        var aggregation = DataGenerator.aggregateCourseHours(DataGenerator.loadSeedCsv("incarichi.csv", 4));
        for (var entry : aggregation.hoursByKey().entrySet()) {
            var key = entry.getKey();
            var teacher = teachers.get(key.teacherName());
            var group = groups.get(key.groupName());
            if (teacher == null || group == null) {
                continue;
            }
            var course = new com.school.planner.domain.Course(key.subject(), teacher, group, entry.getValue());
            if (subjectRegistry != null) {
                var flags = subjectRegistry.flagsFor(key.subject(), group.name);
                course.isLab = flags.isLab();
                course.requiredRoomTags = flags.roomTags();
                course.professionalizzanti = flags.professionalizzanti();
            }
            course.persist();
            lessonGenerator.generateForCourse(course.id);
        }
    }

    /**
     * TASK-597 hoist of the construction-only solve harness (4th-copy shape):
     * builds the problem from the given lesson list and wires, runs ONLY the
     * DayClusteredConstructionPhase (optionally + an LS phase), and manages the
     * GRASP flags around the solve. keepPins=true leaves pinned lessons placed
     * (the pre-assign ride-along shape); keepPins=false clears every placement
     * (the pin-less shape). graspSeed=null forces deterministic construction.
     */
    public static com.school.planner.domain.SchoolSchedule solveConstructionOnly(
            List<com.school.planner.domain.Lesson> lessons,
            BenchWires w, boolean keepPins, Integer graspSeed) {
        for (var l : lessons) {
            if (keepPins && l.pinned) {
                continue;
            }
            l.timeslot = null;
            l.room = null;
            l.pinned = false;
            l.promotedPin = false;
        }
        com.school.planner.domain.SchoolSchedule problem =
                new com.school.planner.domain.SchoolSchedule(w.timeslots(),
                        List.<com.school.planner.domain.Room>of(), List.of(),
                        w.availabilities(), List.of(), w.groups(), w.teachers(),
                        w.courses(), lessons, List.of());
        problem.setDailyActivityQuotas(w.quotas());
        var config = new ai.timefold.solver.core.config.solver.SolverConfig()
                .withSolutionClass(com.school.planner.domain.SchoolSchedule.class)
                .withEntityClasses(com.school.planner.domain.Lesson.class)
                .withScoreDirectorFactory(
                        new ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(
                                        com.school.planner.solver.SchoolScheduleConstraintProvider.class));
        java.util.List<ai.timefold.solver.core.config.phase.PhaseConfig> phases = new java.util.ArrayList<>(
                List.of(new ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig()
                        .withCustomPhaseCommandList(List.of(
                                new com.school.planner.solver.DayClusteredConstructionPhase<com.school.planner.domain.SchoolSchedule>()))));
        // deliberately NO LS phase: a bare default LocalSearchPhaseConfig measures
        // nothing the family recognizes (production selectors live in solverConfig.xml;
        // benches needing production LS extract it themselves - see BoxDeficitOnly)
        config.withPhaseList(phases);
        if (graspSeed == null) {
            System.setProperty("planner.construction.grasp.enabled", "false");
        } else {
            System.setProperty("planner.construction.grasp.enabled", "true");
            System.setProperty("planner.construction.grasp.seed", String.valueOf(graspSeed));
        }
        com.school.planner.domain.SchoolSchedule solved;
        try {
            solved = ai.timefold.solver.core.api.solver.SolverFactory
                    .<com.school.planner.domain.SchoolSchedule>create(applyEnvironmentMode(config))
                    .buildSolver().solve(problem);
        } finally {
            // a thrown solve must not leak grasp.enabled into the reused surefire JVM
            System.clearProperty("planner.construction.grasp.enabled");
            System.clearProperty("planner.construction.grasp.seed");
        }
        return solved;
    }

    /**
     * TASK-597 hoist of the empty-declared-cells recount (4th-copy shape, was
     * ALREADY diverged: the census variant filters isQuotaFillExcluded, the
     * pin-less twins do not). Counts (group, date) quota cells aula+lab > 0,
     * in-grid, with ZERO non-Stage placed lessons. excludeQuotaFillGroups=true
     * applies the H32 exclusion set (the census scope); false is the raw
     * population (PoolCapFill/TeacherPair shape, whose absolute counts are
     * committed evidence and must not change scope).
     */
    public static java.util.Map<String, Integer> emptyDeclaredCellsByGroup(
            java.util.Map<Long, java.util.Set<java.time.LocalDate>> placedByGroup,
            List<com.school.planner.domain.DailyActivityQuota> quotas,
            java.util.Set<java.time.LocalDate> grid, boolean excludeQuotaFillGroups) {
        java.util.Map<String, Integer> emptiesByGroup = new java.util.TreeMap<>();
        for (var q : quotas) {
            if (q.aulaHours + q.labHours <= 0 || !grid.contains(q.date)) {
                continue;
            }
            if (excludeQuotaFillGroups
                    && com.school.planner.solver.SchoolScheduleConstraintProvider
                            .isQuotaFillExcluded(q.studentGroup.name)) {
                continue;
            }
            var placed = placedByGroup.get(q.studentGroup.id);
            if (placed == null || !placed.contains(q.date)) {
                emptiesByGroup.merge(q.studentGroup.name, 1, Integer::sum);
            }
        }
        return emptiesByGroup;
    }

    /**
     * TASK-636: publish the cadence reference (M34/M35 caps + the cumulative
     * pace curves the construction tail's cadence-pace preference tracks) so
     * bench fixtures see the same statics a production solve publishes. Delegates
     * to the ONE parser ({@code ScheduleService.parseCadenceReference}): the
     * micro-pair rule, the week encoding and the fail-loud behavior live in a
     * single place. Fail-safe like the publisher: any parse failure publishes
     * empty maps (preference inert).
     */
    public static void publishCadenceReferenceStatics() {
        try {
            var ref = com.school.planner.service.ScheduleService.parseCadenceReference(
                    Path.of("claudedocs/spalmone-2026-27/data/golden-cadence-reference.json").toFile(),
                    new ObjectMapper());
            com.school.planner.domain.SchoolSchedule.setCadenceReferenceStatic(
                    ref.burstCap(), ref.activeWeeks(), ref.pace());
        } catch (java.io.IOException | IllegalArgumentException e) {
            System.err.println("[SNAPSHOT-FIXTURE] cadence reference publish failed, "
                    + "preference inert: " + e.getMessage());
            com.school.planner.domain.SchoolSchedule.setCadenceReferenceStatic(
                    Map.of(), Map.of(), Map.of());
        }
    }

    /**
     * TASK-638 near-miss feed: print the CELL identities of a state's residual
     * hard (the lessons justified by each H0/H17/H32/H34/H7 constraint match),
     * prefixed with {@code tag}. Shared by the replay benches so a blind puzzle
     * can be built from real data instead of family counts: the benches' logs
     * are often the only artifact that survives.
     */
    public static void printResidualCells(String tag,
            com.school.planner.domain.SchoolSchedule problem) {
        ai.timefold.solver.core.config.solver.SolverConfig cfg =
                new ai.timefold.solver.core.config.solver.SolverConfig()
                        .withSolutionClass(com.school.planner.domain.SchoolSchedule.class)
                        .withEntityClasses(Lesson.class)
                        .withScoreDirectorFactory(new ai.timefold.solver.core.config.score.director
                                .ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(
                                        com.school.planner.solver.SchoolScheduleConstraintProvider.class));
        ai.timefold.solver.core.api.solver.SolverFactory<
                com.school.planner.domain.SchoolSchedule> factory =
                ai.timefold.solver.core.api.solver.SolverFactory.create(cfg);
        ai.timefold.solver.core.api.solver.SolutionManager<
                com.school.planner.domain.SchoolSchedule, ai.timefold.solver.core.api.score.BendableScore>
                manager = ai.timefold.solver.core.api.solver.SolutionManager.create(factory);
        manager.update(problem);
        var directorFactory = ((ai.timefold.solver.core.impl.solver.DefaultSolutionManager<
                com.school.planner.domain.SchoolSchedule, ai.timefold.solver.core.api.score.BendableScore>)
                manager).getScoreDirectorFactory();
        java.util.Map<Long, Lesson> lessonById = problem.getLessons().stream()
                .collect(java.util.stream.Collectors.toMap(l -> l.id, l -> l));
        try (var director = directorFactory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(
                        ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            director.setWorkingSolution(problem);
            director.calculateScore();
            for (var entry : director.getConstraintMatchTotalMap().entrySet()) {
                String id = entry.getKey().id();
                if (!id.startsWith("H32") && !id.startsWith("H34")
                        && !id.startsWith("H7 ") && !id.startsWith("H17")
                        && !id.startsWith("H0")) {
                    continue;
                }
                for (var match : entry.getValue().getConstraintMatchSet()) {
                    // Only VIOLATING matches: the ENABLED policy also returns
                    // zero-score matches (H7 at-cap days etc.), pure noise for
                    // puzzle building (code-review of the first replay 21/09).
                    if (com.school.planner.solver.ScoreCompat.hardOf(match.getScore()) >= 0) {
                        continue;
                    }
                    StringBuilder sb = new StringBuilder("[CELL] " + tag + " " + id + ":");
                    var resolved = com.school.planner.service.ScheduleService
                            .resolveLessons(match.getJustification(), lessonById);
                    if (!resolved.isEmpty()) {
                        for (Lesson l : resolved) {
                            sb.append(" [")
                                    .append(l.course != null && l.course.studentGroup != null
                                            ? l.course.studentGroup.name : "?")
                                    .append("|").append(l.timeslot != null
                                            ? String.valueOf(l.timeslot.getDate()) : "BOXED")
                                    .append("@").append(l.timeslot != null
                                            ? String.valueOf(l.timeslot.startDateTime.toLocalTime()) : "-")
                                    .append("|").append(l.course != null ? l.course.subject : "?")
                                    .append("|").append(l.getTeacher() != null ? l.getTeacher().name : "?")
                                    .append(l.pinned ? "|PIN" : "").append("]");
                        }
                    } else {
                        // Tuple-shaped justifications (H34 empty-day group/date,
                        // H7 teacher/day) carry no Lesson: print the facts raw
                        // (the PureOneOffCheckpointTest precedent).
                        sb.append(" ").append(String.valueOf(match.getJustification()));
                    }
                    System.out.println(sb);
                }
            }
        }
    }

    /**
     * TASK-674: gzips JSON bytes to {@code out} and returns it, the feed shape
     * {@link #loadFromExport} consumes. New helper following the inline gzip
     * pattern of {@code SnapshotFixtureExportParserTest} (which stays String-in);
     * taking the full destination path keeps the caller in charge of naming.
     */
    public static Path gzipJson(Path out, byte[] json) throws IOException {
        try (GZIPOutputStream zos = new GZIPOutputStream(Files.newOutputStream(out))) {
            zos.write(json);
        }
        return out;
    }
}
