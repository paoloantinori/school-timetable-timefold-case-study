package com.school.planner.bootstrap;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import org.jboss.logging.Logger;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.LabAssistant;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.service.GoldenScheduleImportService;
import com.school.planner.service.LessonGenerator;
import com.school.planner.service.SubjectRegistry;
import com.school.planner.service.SubjectRegistry.SubjectFlags;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.util.SeedCsv;
import com.school.planner.util.SubjectNormalization;

@ApplicationScoped
public class DataGenerator {

    private static final Logger LOG = Logger.getLogger(DataGenerator.class);

    @Inject
    EntityManager em;

    @Inject
    LessonGenerator lessonGenerator;

    @Inject
    AvailabilityExpansionService availabilityExpansionService;

    @Inject
    ExpectedDaysInitializer expectedDaysInitializer;

    // Subject taxonomy (TASK-437, audit C1): the old hardcoded Italian-prefix
    // matchers (getRoomTags/isLabSubject/isProfessionalizzante) are gone; the
    // classification now lives as data in seed/subjects.csv, with per-(group,
    // subject) exceptions in seed/subject_overrides.csv (empty on 2026/27).
    @Inject
    SubjectRegistry subjectRegistry;
    @ConfigProperty(name = "planner.datagenerator.enabled", defaultValue = "true")
    boolean enabled;


    // Vibrant categorical palette — 12 distinct hues for maximum visual separation.
    // Used with auto text color (black/white by luminance) and 70% bg opacity.
    // Combined with 4 patterns (plain/dotted/hatched/striped) = 48 unique visual identities.
    private static final String[] TEACHER_COLORS = {
        "#2563eb", // Blue
        "#dc2626", // Red
        "#7c3aed", // Violet
        "#059669", // Emerald
        "#d97706", // Amber
        "#db2777", // Pink
        "#0891b2", // Cyan
        "#65a30d", // Lime
        "#ea580c", // Orange
        "#6366f1", // Indigo
        "#be123c", // Rose
        "#0d9488", // Teal
    };

    private static final String[] PATTERNS = {"plain", "dotted", "hatched", "striped"};

    // 2026/27 school year bounds (TASK-341, spalmone override 2026-07-16 —
    // see claudedocs/spalmone-2026-27/analysis.md). Single source of truth is
    // TimeslotSequence.SCHOOL_YEAR_START/END, so solver-side services
    // (HybridPhase1aService, StudentGroupCompletionService) don't need a
    // bootstrap-package dependency just to know the school year bounds.

    // 8 slots/day, uniform for every school day (no Friday-specific reduction):
    // 6 morning (08:00-14:00) + lunch break 14:00-14:30 + 2 afternoon (14:30-16:30).
    // Start times come from TimeslotSequence.SLOT_START_TIMES (single source of
    // truth for the slot schema, shared with the M15/H16/H17 gap-detection logic);
    // every slot is exactly 1 hour long. See claudedocs/spalmone-2026-27/analysis.md
    // section 2 ("Calendar structure").
    static final LocalTime[][] STANDARD_SLOTS = buildStandardSlots();

    private static LocalTime[][] buildStandardSlots() {
        LocalTime[] starts = TimeslotSequence.SLOT_START_TIMES;
        LocalTime[][] slots = new LocalTime[starts.length][2];
        for (int i = 0; i < starts.length; i++) {
            slots[i][0] = starts[i];
            slots[i][1] = starts[i].plusHours(1);
        }
        return slots;
    }

    // School closure dates for the 2026/27 calendar, mapped to a human-readable
    // description. Transcribed by hand from claudedocs/spalmone-2026-27/data/closures.csv
    // ("Parametri" sheet — dates only, no description column; descriptions here are
    // inferred from fixed Italian national holidays / standard school breaks) on
    // 2026-07-16 — the CSV itself is not on the runtime classpath. Single source of
    // truth for both timeslot exclusion (CLOSURE_DATES_2026_27) and SchoolHoliday
    // seeding (generateSchoolHolidays()). 29 raw dates. 23
    // fall on weekdays within [SCHOOL_YEAR_START, SCHOOL_YEAR_END] -> 171
    // school days x 8 slots/day = 1368 timeslots (verified in
    // DataGeneratorCalendarTest). (TASK-408: the 2027-01-22 entry was previously
    // mistranscribed as 2026-01-22, out of range and inert; the golden is 100%
    // closed that Friday, so 172/1376 -> 171/1368 once corrected.)
    private static final Map<LocalDate, String> CLOSURES_2026_27 = buildClosures2026_27();
    static final Set<LocalDate> CLOSURE_DATES_2026_27 = CLOSURES_2026_27.keySet();

    /**
     * TASK-550 grid extension (maintainer 25/08 07:50): the golden-HELD hours per
     * (group, date) PAST the pre-extension horizon (2027-06-10), derived mechanically
     * from lessons.csv (rows with closed != true - the same technique as the cure-(a)
     * seeding). Stage rows feed the stage bucket, every other subject the aula bucket:
     * exactly the five authorized quota rows (G04 06-11 stage 8, G04 06-14 stage 8,
     * G08 06-14 R04, G04 06-15 R04, G08 06-15 R04). A (group, date) the
     * golden does not practice yields NO entry: the CSVs' divergent rows in that window
     * (G08 8h vs golden 5h; G04 06-15 zero vs golden 5; G13/G09/G10/G11
     * rows with no golden practice) are NOT seeded - H26 keeps those groups out of the
     * extension days entirely.
     */
    static Map<String, Map<LocalDate, int[]>> goldenHeldBeyondHorizon() {
        Map<String, Map<LocalDate, int[]>> held = new LinkedHashMap<>();
        for (String[] row : loadSeedCsv("lessons.csv", 7)) {
            LocalDate date;
            try {
                date = LocalDate.parse(row[0].trim());
            } catch (Exception e) {
                continue;
            }
            if (!date.isAfter(PRE_EXTENSION_HORIZON)) continue;
            if ("true".equalsIgnoreCase(row[3].trim())) continue; // held = closed false
            String groupName = row[4].trim();
            boolean stage = "Stage".equalsIgnoreCase(row[6].trim());
            int[] buckets = held.computeIfAbsent(groupName, k -> new LinkedHashMap<>())
                    .computeIfAbsent(date, k -> new int[2]); // {aula, stage}
            if (stage) {
                buckets[1] += 1;
            } else {
                buckets[0] += 1;
            }
        }
        return held;
    }

    private static final LocalDate PRE_EXTENSION_HORIZON = java.time.LocalDate.of(2027, 6, 10);

    private static Map<LocalDate, String> buildClosures2026_27() {
        Map<LocalDate, String> closures = new LinkedHashMap<>();
        closures.put(LocalDate.of(2027, 1, 22), "Chiusura da golden/fogli (2027-01-22; era un typo 2026)");
        closures.put(LocalDate.of(2026, 12, 7), "Ponte Immacolata Concezione");
        closures.put(LocalDate.of(2026, 12, 8), "Immacolata Concezione");
        for (int d = 23; d <= 31; d++) {
            closures.put(LocalDate.of(2026, 12, d), "Vacanze di Natale");
        }
        for (int d = 1; d <= 6; d++) {
            closures.put(LocalDate.of(2027, 1, d), "Vacanze di Natale");
        }
        closures.put(LocalDate.of(2027, 2, 8), "Carnevale");
        closures.put(LocalDate.of(2027, 2, 9), "Carnevale");
        for (int d = 25; d <= 30; d++) {
            closures.put(LocalDate.of(2027, 3, d), "Vacanze di Pasqua");
        }
        closures.put(LocalDate.of(2027, 5, 31), "Ponte Festa della Repubblica");
        closures.put(LocalDate.of(2027, 6, 1), "Ponte Festa della Repubblica");
        closures.put(LocalDate.of(2027, 6, 2), "Festa della Repubblica");
        return Collections.unmodifiableMap(closures);
    }

    // 2026/27 seed data (TASK-343, spalmone override 2026-07-16): loaded at
    // startup from classpath CSVs under src/main/resources/seed/, copied
    // verbatim from claudedocs/spalmone-2026-27/data/{teachers,groups,rooms,
    // incarichi}.csv (the "Docenti"/"Elenchi"/"Incarichi in riga" sheets of
    // the school's xlsx). See loadSeedCsv() for the fail-loud parser.

    // Pseudo-teacher rows in incarichi.csv that are NOT real Course
    // assignments (analysis.md section 1): Stage/Esame hours are scheduled
    // as lessons but their modeling is a separate concern (stage-modeling
    // task, not this one); RA (Recuperi ed Approfondimenti pseudo-teacher,
    // distinct from the real per-teacher "Recuperi ed Approfondimenti"
    // course some teachers DO teach, e.g. T15/G04) has no single
    // teacher to attach to; "Totale ore corso" is a summary row (990h/group).
    // Package-private (not private): DataGeneratorSeedTest cross-checks the
    // seed data by calling this constant and loadSeedCsv() directly instead
    // of re-deriving its own copy, so a future change to either can't drift
    // silently out of sync with what the test verifies.
    static final Set<String> PSEUDO_COURSE_TEACHERS = Set.of(
        "Stage", "Esame", "RA", "Totale ore corso"
    );

    // Groups that do NOT allow professionalizzanti courses during stage
    // periods: vocational/technical subjects must not be scheduled during
    // their stage blackout. G10 is deliberately NOT in this set - T28
    // teaches G10 during its stage period (Nov 27, Dec 4/11/18, Jan 8/15 =
    // 6 of 8 blocks), so professionalizzanti must be allowed there.
    private static final Set<String> NO_PROFESSIONALIZZANTI_DURING_STAGE = Set.of(
        "G08",
        "G09"
    );

    // Room-name -> tag mapping, preserving the existing tag vocabulary
    // (informatica/meccanica/termoidraulica/acconciatura/moda) used by
    // Course.requiredRoomTags, plus "saldatura" for R17 / "Tecniche di
    // saldatura". Rooms not listed here get no tags (regular classrooms).
    private static final Map<String, String> ROOM_TAG_MAP = Map.of(
        "R14", "termoidraulica",
        "R15", "meccanica",
        "R11", "acconciatura",
        "R12", "informatica",
        "R13", "informatica",
        "R16", "moda",
        "R17", "saldatura"
    );

    // Per-group maxHoursPerDay (TASK-343), derived MECHANICALLY (not guessed)
    // as the max observed hours/day per group across the full 2026/27 school
    // year in claudedocs/spalmone-2026-27/data/lessons.csv (same aggregation
    // style as GROUP_CALENDAR_PATTERNS). Matches analysis.md section 2:
    // first-year groups (no afternoons) cap at 6h, G14 at 5h, every group
    // with genuine afternoon usage caps at 8h (crossing the lunch break).
    private static final Map<String, Integer> MAX_HOURS_PER_DAY = Map.ofEntries(
        Map.entry("G01", 6),
        Map.entry("G02", 6),
        Map.entry("G03", 6),
        Map.entry("G04", 8),
        Map.entry("G05", 6),
        Map.entry("G06", 8),
        Map.entry("G07", 8),
        Map.entry("G08", 8),
        Map.entry("G09", 8),
        Map.entry("G10", 8),
        Map.entry("G11", 8),
        Map.entry("G12", 8),
        Map.entry("G13", 8),
        Map.entry("G14", 5)
    );

    /**
     * Load a seed CSV from the classpath (src/main/resources/seed/). Thin
     * delegator to {@link SeedCsv#load} - the parser lives in a standalone
     * util class so later seeding tasks can reuse it without depending on
     * the DataGenerator bootstrap bean.
     *
     * <p>Package-private (not private): DataGeneratorSeedTest calls this
     * directly to cross-check seed counts against the exact same parser,
     * instead of maintaining a second copy of the parsing rules.</p>
     */
    static List<String[]> loadSeedCsv(String resourceName, int expectedColumns) {
        return SeedCsv.load(resourceName, expectedColumns);
    }

    @Transactional
    public void onStartup(@Observes StartupEvent event) {
        // Skip data generation if disabled (e.g., for tests)
        if (!enabled) {
            LOG.info("DataGenerator disabled, skipping seed data generation");
            return;
        }

        // labBlockSize is not seeded for 2026/27 lab subjects - it defaults
        // to 0, which LessonGenerator treats as "individual 1-hour lessons",
        // so nothing breaks. A future task can derive real block sizes from
        // the block-size histogram in analysis.md section 4.

        // professionalizzanti (and isLab/requiredRoomTags) are set once, from
        // SubjectRegistry, when each Course is created in generateCourses()
        // below (only on a fresh seed). The old every-startup force-update that
        // recomputed professionalizzanti is GONE (TASK-437): it overwrote admin
        // corrections on every boot. Now an admin edit to the flag persists.

        // Cap labBlockSize at student group's maxHoursPerDay (TASK-245)
        // Lab blocks should not exceed the group's daily maximum hours
        // Example: G10 (maxHoursPerDay=5) should have 5-hour blocks, not 6-hour
        for (Course c : Course.<Course>listAll()) {
            if (c.isLab && c.studentGroup != null && c.studentGroup.maxHoursPerDay != null) {
                if (c.labBlockSize > c.studentGroup.maxHoursPerDay) {
                    LOG.infof("Capping labBlockSize for %s / %s: %d -> %d (group maxHoursPerDay=%d)",
                        c.teacher.name, c.studentGroup.name, c.labBlockSize,
                        c.studentGroup.maxHoursPerDay, c.studentGroup.maxHoursPerDay);
                    c.labBlockSize = c.studentGroup.maxHoursPerDay;
                }
            }
        }

        // Generate ProfessionalizzantiDay seed data (idempotent - runs on every startup)
        generateProfessionalizzantiDays();

        // Note: LabBlocks were deprecated in TASK-228 - blockSize regeneration check removed

        // Skip if data already exists
        if (Timeslot.count() > 0) {
            return;
        }

        generateTimeslots();
        generateRooms();
        generateStudentGroups();
        List<Teacher> teachers = generateTeachers();
        generateCourses(teachers);
        generateStageCourses();
        generateSchoolHolidays();
        generateStagePatterns();
        generateGroupCalendarRules();
        generateDailyActivityQuotas();
        generateExpectedDailyHours();
        generateProfessionalizzantiDays();
        generateLabRotations();
        generateAvailabilityRules(teachers);
        availabilityExpansionService.expandAll();

        // Initialize expectedLabDaysPerWeek for all lab courses
        // (idempotent - safe to run on every startup)
        expectedDaysInitializer.initializeExpectedLabDays();
    }

    private void generateTimeslots() {
        int count = 0;
        for (LocalDate date : computeSchoolDays(TimeslotSequence.SCHOOL_YEAR_START, TimeslotSequence.SCHOOL_YEAR_END, CLOSURE_DATES_2026_27)) {
            for (LocalTime[] slot : STANDARD_SLOTS) {
                Timeslot ts = new Timeslot(
                        LocalDateTime.of(date, slot[0]),
                        LocalDateTime.of(date, slot[1]));
                ts.persist();
                count++;
                if (count % 100 == 0) {
                    em.flush();
                    em.clear();
                }
            }
        }
        em.flush();
        em.clear();
    }

    /**
     * Pure calendar computation: school days in [start, end] excluding weekends
     * and the given closure dates. Extracted so the 2026/27 calendar (171 school
     * days, TASK-341; 172 prima del fix typo chiusura 2027-01-22, TASK-408) can
     * be unit-tested without a Quarkus/CDI context.
     */
    static List<LocalDate> computeSchoolDays(LocalDate start, LocalDate end, Set<LocalDate> closures) {
        List<LocalDate> schoolDays = new ArrayList<>();
        LocalDate date = start;
        while (!date.isAfter(end)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !closures.contains(date)) {
                schoolDays.add(date);
            }
            date = date.plusDays(1);
        }
        return schoolDays;
    }

    /**
     * Seed Room entities from src/main/resources/seed/rooms.csv: 18 rooms
     * total. R17 has real capacity 22 (referente 29/07: usata solo per
     * saldatura, 22 postazioni - era 0); R18 stays cap 0 (referente:
     * non usata, fuori dal parco aule). See DataGeneratorSeedTest for the exact
     * count and capacity checks.
     *
     * <p>Tags preserve the existing vocabulary (informatica/meccanica/
     * termoidraulica/acconciatura/moda) used by Course.requiredRoomTags,
     * plus "saldatura" for R17 (see ROOM_TAG_MAP for the full mapping).
     * Regular classrooms (R01..10, R02, R10, AULA
     * POLIVALENTE) get no tags.</p>
     */
    private void generateRooms() {
        for (String[] row : loadSeedCsv("rooms.csv", 2)) {
            String name = row[0].trim();
            int capacity;
            try {
                capacity = Integer.parseInt(row[1].trim());
            } catch (NumberFormatException e) {
                throw new RuntimeException("generateRooms: malformed capacity in rooms.csv row: "
                        + String.join(",", row), e);
            }
            String tag = ROOM_TAG_MAP.get(name);
            new Room(name, capacity, tag != null ? List.of(tag) : List.of()).persist();
        }
    }

    /**
     * Seed StudentGroup entities from src/main/resources/seed/groups.csv: 14
     * groups including G14 (see ScheduleService.EXCLUDED_GROUPS and
     * GROUP_CALENDAR_PATTERNS for its calendar treatment).
     *
     * maxHoursPerDay comes from MAX_HOURS_PER_DAY (mechanically derived from
     * lessons.csv, see that map's javadoc) instead of hand-picked per-group
     * literals.
     */
    private void generateStudentGroups() {
        // allowProfessionalizzantiDuringStage defaults to true for every
        // group except those in NO_PROFESSIONALIZZANTI_DURING_STAGE (G08,
        // G09): professionalizzanti courses are otherwise allowed during
        // the stage blackout (see H4/stageBlackout()).
        for (String[] row : loadSeedCsv("groups.csv", 3)) {
            String name = row[0].trim();
            int enrolled;
            try {
                enrolled = Integer.parseInt(row[2].trim());
            } catch (NumberFormatException e) {
                throw new RuntimeException("generateStudentGroups: malformed enrollment in groups.csv row: "
                        + String.join(",", row), e);
            }

            StudentGroup group = new StudentGroup(name, enrolled);
            group.maxHoursPerDay = MAX_HOURS_PER_DAY.get(name);
            LOG.infof("Set maxHoursPerDay=%s for group %s", group.maxHoursPerDay, group.name);
            group.startDate = TimeslotSequence.SCHOOL_YEAR_START;

            boolean allowDuringStage = !NO_PROFESSIONALIZZANTI_DURING_STAGE.contains(group.name);
            group.allowProfessionalizzantiDuringStage = allowDuringStage;
            LOG.infof("Set allowProfessionalizzantiDuringStage=%s for group %s", allowDuringStage, group.name);

            group.persist();
        }
    }

    /**
     * Seed Teacher entities from src/main/resources/seed/teachers.csv: 26
     * teachers, using the "short" column as the display name (matches
     * incarichi.csv's teacher column so course lookups are exact string
     * matches, not prefix guesses). T28 (external, Thursday-only) is not in
     * the 2026/27 roster, so no VACATION records or LabRotation seeds
     * reference her.
     *
     * <p>T01's real 8h/day exception (analysis.md finding #2, full-day
     * lab crossing lunch) is modeled via the CSV's optional 4th column
     * ({@code maxDailyHours}, TASK-348): blank for every teacher except
     * T01 ("8"), so H7 applies its global 6h/day
     * {@code Teacher.DEFAULT_MAX_DAILY_HOURS} default to everyone else and
     * only relaxes it for her.</p>
     *
     * <p>TASK-368 adds two more optional columns following the same
     * blank-means-unconstrained pattern: {@code maxEarlyStartDaysPerWeek}
     * (T11=1, enforced by M24) and {@code requiredFreeDaysPerWeek}
     * (T05=2, enforced by M25) - see {@link Teacher}'s field javadocs for
     * the choice-day mechanism both encode.</p>
     */
    private List<Teacher> generateTeachers() {
        int i = 0;
        for (String[] row : loadSeedCsv("teachers.csv", 6)) {
            String shortName = row[0].trim();
            Teacher teacher = new Teacher(shortName);
            // Sequential color+pattern: teachers 0-3 share color[0] with 4 different patterns, etc.
            teacher.color = TEACHER_COLORS[i / 4 % TEACHER_COLORS.length];
            teacher.pattern = PATTERNS[i % 4];
            String maxDailyHours = row[3].trim();
            teacher.maxDailyHours = maxDailyHours.isEmpty() ? null : Integer.valueOf(maxDailyHours);
            String maxEarlyStartDaysPerWeek = row[4].trim();
            teacher.maxEarlyStartDaysPerWeek = maxEarlyStartDaysPerWeek.isEmpty() ? null : Integer.valueOf(maxEarlyStartDaysPerWeek);
            String requiredFreeDaysPerWeek = row[5].trim();
            teacher.requiredFreeDaysPerWeek = requiredFreeDaysPerWeek.isEmpty() ? null : Integer.valueOf(requiredFreeDaysPerWeek);
            teacher.persist();
            i++;
        }
        return Teacher.listAll();
    }

    /**
     * Identity key for one Course: (teacher, subject, group). incarichi.csv
     * rows sharing a key are merged (summed hours) instead of producing
     * duplicate Course rows - see {@link #aggregateCourseHours}.
     */
    record CourseKey(String teacherName, String subject, String groupName) {}

    /**
     * Result of {@link #aggregateCourseHours}: the merged (teacher, subject,
     * group) -&gt; hours map, plus the pseudo-row skip stats needed for
     * generateCourses()'s summary log - computed in the same pass instead of
     * a second scan over the same rows.
     */
    record CourseAggregation(Map<CourseKey, Integer> hoursByKey, int skippedPseudoRows,
                              Map<String, Integer> skippedByPseudoTeacher) {}

    /**
     * Aggregate incarichi.csv rows by {@link CourseKey}, summing hours for any
     * duplicate key - every Course must correspond to a unique (teacher,
     * subject, group) triple, never a duplicate row. Pseudo-teacher rows are
     * skipped here too (not just counted by the caller), so they never enter
     * the aggregation. Fails loud (WARN log, not silent) whenever a merge
     * actually happens - on the current 266-row seed every key is unique
     * (verified in DataGeneratorSeedTest), so a merge firing at all signals
     * new duplicate data worth a human's attention, not routine behavior to
     * swallow quietly.
     *
     * <p>Package-private static (no DB access) so DataGeneratorSeedTest can
     * unit-test the merge directly against a synthetic duplicate row, without
     * needing a live incarichi.csv duplicate to exercise it.</p>
     */
    /**
     * incarichi.csv hours column: double-then-round (the sheet formats hours
     * as "8.0"), failing loud with the caller's row context on malformed
     * input. One owner for {@link #aggregateCourseHours} and
     * {@link #generateStageCourses}.
     */
    static int parseIncarichiHours(String hoursStr, String context) {
        try {
            return (int) Math.round(Double.parseDouble(hoursStr));
        } catch (NumberFormatException e) {
            throw new RuntimeException(context + ": malformed hours '" + hoursStr + "' in incarichi.csv", e);
        }
    }

    static CourseAggregation aggregateCourseHours(List<String[]> rows) {
        Map<CourseKey, Integer> aggregated = new LinkedHashMap<>();
        int skipped = 0;
        Map<String, Integer> skippedByPseudoTeacher = new LinkedHashMap<>();
        for (String[] row : rows) {
            String teacherName = row[0].trim();
            if (PSEUDO_COURSE_TEACHERS.contains(teacherName)) {
                skipped++;
                skippedByPseudoTeacher.merge(teacherName, 1, Integer::sum);
                continue;
            }
            String rawSubject = row[1].trim();
            String subject = SubjectNormalization.normalize(rawSubject);
            String groupName = row[2].trim();
            String hoursStr = row[3].trim();

            int hours = parseIncarichiHours(hoursStr, "generateCourses: row " + String.join(",", row));

            CourseKey key = new CourseKey(teacherName, subject, groupName);
            Integer previousHours = aggregated.get(key);
            if (previousHours == null) {
                aggregated.put(key, hours);
            } else {
                int mergedHours = previousHours + hours;
                aggregated.put(key, mergedHours);
                LOG.warnf("generateCourses: merged duplicate incarichi.csv row for teacher='%s' subject='%s' group='%s': %dh + %dh = %dh",
                        teacherName, subject, groupName, previousHours, hours, mergedHours);
            }
        }
        return new CourseAggregation(aggregated, skipped, skippedByPseudoTeacher);
    }

    /**
     * Seed Course entities from src/main/resources/seed/incarichi.csv
     * (TASK-343: 266 real courses, down from 294 raw rows after skipping the
     * 28 pseudo-teacher rows in PSEUDO_COURSE_TEACHERS - see that constant's
     * javadoc). Duplicate (teacher, subject, group) rows are merged by
     * {@link #aggregateCourseHours} before Course creation.
     *
     * <p>isLab/requiredRoomTags/professionalizzanti are classified by
     * SubjectRegistry from seed/subjects.csv (TASK-437, audit C1: the old
     * hardcoded Italian-prefix matchers are gone). Per-(group, subject)
     * exceptions come from seed/subject_overrides.csv (empty on 2026/27).</p>
     *
     * <p>labBlockSize is intentionally left at its default (0) - out of
     * scope for this task, see the onStartup() comment where the old
     * subject-name-keyed Course.update() calls were removed.</p>
     */
    private void generateCourses(List<Teacher> teachers) {
        List<StudentGroup> groups = StudentGroup.listAll();
        List<String[]> rows = loadSeedCsv("incarichi.csv", 4);

        CourseAggregation aggregation = aggregateCourseHours(rows);
        Map<CourseKey, Integer> aggregated = aggregation.hoursByKey();

        int created = 0;
        for (Map.Entry<CourseKey, Integer> entry : aggregated.entrySet()) {
            CourseKey key = entry.getKey();
            int hours = entry.getValue();

            Teacher teacher = findTeacherByExactName(teachers, key.teacherName());
            if (teacher == null) {
                throw new RuntimeException("generateCourses: unknown teacher '" + key.teacherName()
                        + "' for subject '" + key.subject() + "' group '" + key.groupName() + "'");
            }
            StudentGroup group = findGroupByName(groups, key.groupName());
            if (group == null) {
                throw new RuntimeException("generateCourses: unknown group '" + key.groupName()
                        + "' for teacher '" + key.teacherName() + "' subject '" + key.subject() + "'");
            }

            Course course = new Course(key.subject(), teacher, group, hours);
            // Subject taxonomy from seed/subjects.csv (+ per-(group,subject)
            // overrides from seed/subject_overrides.csv), not prefix matchers.
            SubjectFlags flags = subjectRegistry.flagsFor(key.subject(), group.name);
            course.isLab = flags.isLab();
            course.requiredRoomTags = flags.roomTags();
            course.professionalizzanti = flags.professionalizzanti();
            course.persist();
            created++;
        }

        LOG.infof("generateCourses: created %d courses from incarichi.csv (%d unique teacher/subject/group keys), skipped %d pseudo-rows %s",
                created, aggregated.size(), aggregation.skippedPseudoRows(), aggregation.skippedByPseudoTeacher());

        em.flush();
        em.clear();

        logRoomTagSanityWarnings();

        List<Course> allCourses = Course.listAll();
        for (Course course : allCourses) {
            if (course.totalHours > 0) {
                lessonGenerator.generateForCourse(course.id);
            }
        }
    }

    /**
     * Seeds Stage pseudo-courses from incarichi.csv's 7 "Stage" pseudo-teacher
     * rows (TASK-381 "stage on the grid", maintainer 2026-07-20 explicit
     * authorization - the one addition maintainer approved to this otherwise
     * protected file, see {@code .claude/rules/datagenerator-protection.md}).
     *
     * <p>Before this method, {@link #aggregateCourseHours} skipped every
     * {@code PSEUDO_COURSE_TEACHERS} row (Stage included) inside {@link
     * #generateCourses}, so a fresh calendario-only profile had NO Stage
     * courses at all - stage days were just empty capacity holes instead of
     * pinned lessons like the school's own timetable. This method fills that
     * gap the same way {@link #generateCourses} fills real courses: one
     * Course + its stub Lessons per row, so {@code CalendarioImportService
     * #planStagePins} (and, on profiles that also run it, {@code
     * GoldenScheduleImportService}) has a course to pin against from the
     * moment the profile is created.</p>
     *
     * <p>Mirrors {@code GoldenScheduleImportService}'s synthetic Stage
     * teacher/course conventions EXACTLY, referencing its (now public)
     * {@link GoldenScheduleImportService#STAGE_NAME} directly so the
     * convention can never silently diverge across packages.
     * {@code GoldenScheduleImportService#findOrCreateStageCourse} and
     * {@code CalendarioImportService#planStagePins} both find-or-reuse by
     * (pseudo teacher name, subject, group), so a later golden import never
     * creates a duplicate.</p>
     *
     * <p>Esame/RA pseudo rows stay skipped - out of scope for this task,
     * unlike Stage they are never pinned from the calendario sheets.</p>
     *
     * <p>Package-private (not private): {@code DataGeneratorStageCoursesSeedTest}
     * calls this directly against a minimal (StudentGroup-only) fixture,
     * same convention as {@code DataGeneratorDailyActivityQuotaSeedTest} calling
     * {@link #generateDailyActivityQuotas}.</p>
     */
    void generateStageCourses() {
        List<String[]> rows = loadSeedCsv("incarichi.csv", 4);
        List<StudentGroup> groups = StudentGroup.listAll();

        Teacher stageTeacher = Teacher.find("name = ?1 and pseudo = true",
                GoldenScheduleImportService.STAGE_NAME).firstResult();
        if (stageTeacher == null) {
            stageTeacher = new Teacher(GoldenScheduleImportService.STAGE_NAME);
            stageTeacher.pseudo = true;
            stageTeacher.persist();
        }

        // TASK-550 cure (a), approved by maintainer 24/08 22:30 for THIS change: GOLDEN-OPEN
        // DENSITY seeding. The sheet's Stage rows ask 295-300h per group, but the golden
        // HELD only 112-295 of them (lessons.csv rows with closed != true): the excess
        // was phantom demand - the direct cause of the ~314 unplaceable boxed H0 of the
        // iter-30/32 runs (task550-diagnosi-boxed-vrp.md; NOTE the doc's first version
        // blamed the GroupCalendarRule windows: WRONG root cause, corrected - the windows
        // were the practice-derived calendar, the demand was the fantasy). Golden-first
        // extension to inputs (TASK-466 family): adopt the golden-held count, declare the
        // difference to the school (referente question, cure (c)) via expectedUnscheduledHours
        // - the existing asked-but-off-the-grid mechanism. Fail-safe: a group with NO
        // golden Stage rows (a future golden-less year) keeps the sheet's full hours.
        Map<String, Integer> goldenHeld = new HashMap<>();
        for (String[] g : loadSeedCsv("lessons.csv", 7)) {
            if (!GoldenScheduleImportService.STAGE_NAME.equals(g[6].trim())) continue;
            if ("true".equalsIgnoreCase(g[3].trim())) continue; // held = closed false
            goldenHeld.merge(g[4].trim(), 1, Integer::sum);
        }

        int created = 0;
        int totalStubs = 0;
        int phantom = 0;
        for (String[] row : rows) {
            if (!GoldenScheduleImportService.STAGE_NAME.equals(row[0].trim())) continue;
            String groupName = row[2].trim();
            StudentGroup group = findGroupByName(groups, groupName);
            if (group == null) {
                throw new RuntimeException("generateStageCourses: unknown group '" + groupName + "' for Stage row");
            }
            int hours = parseIncarichiHours(row[3].trim(), "generateStageCourses: Stage row for " + groupName);
            Integer held = goldenHeld.get(groupName);
            int stubs = (held != null && held > 0 && held <= hours) ? held : hours;

            Course course = new Course(GoldenScheduleImportService.STAGE_NAME, stageTeacher, group, stubs);
            if (stubs < hours) {
                course.expectedUnscheduledHours = hours - stubs; // pending the school's answer (cure (c))
                phantom += hours - stubs;
            }
            course.professionalizzanti = false;
            course.persist();
            created++;
            if (stubs > 0) {
                totalStubs += lessonGenerator.generateForCourse(course.id);
            }
        }

        LOG.infof("generateStageCourses: created %d Stage pseudo-course(s) (%d stub Lessons, golden-open density"
                + " TASK-550: %d sheet hours deferred to expectedUnscheduledHours pending the school's answer)"
                + " - TASK-381 stage-on-the-grid (maintainer 2026-07-20) + TASK-550 cure (a) (maintainer 24/08)",
                created, totalStubs, phantom);
    }

    /**
     * Seed-tag sanity check: after seeding Courses, WARN (don't fail) for
     * every requiredRoomTag that has no room able to satisfy it - either no
     * Room carries that tag at all, or every Room that does has capacity 0.
     * Today this fires for "saldatura": only R17 carries that tag, and
     * R17's seeded capacity is 0 (see generateRooms() javadoc). A WARN,
     * not a hard failure, because a zero-capacity room may itself be a
     * data-entry placeholder pending school confirmation, and this check has
     * no way to tell the difference.
     */
    private void logRoomTagSanityWarnings() {
        List<Room> rooms = Room.listAll();
        Set<String> requiredTags = Course.<Course>listAll().stream()
                .flatMap(c -> c.requiredRoomTags.stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String tag : requiredTags) {
            List<Room> matching = rooms.stream().filter(r -> r.tags.contains(tag)).collect(Collectors.toList());
            if (matching.isEmpty()) {
                LOG.warnf("Seed-tag sanity: requiredRoomTag '%s' matches no room", tag);
            } else if (matching.stream().allMatch(r -> r.capacity == 0)) {
                LOG.warnf("Seed-tag sanity: requiredRoomTag '%s' only matches zero-capacity room(s): %s",
                        tag, matching.stream().map(r -> r.name).collect(Collectors.joining(", ")));
            }
        }
    }

    /**
     * Generate stage (internship) patterns for 2026/27 (TASK-343).
     * Source: claudedocs/spalmone-2026-27/data/vincoli_classi.csv ("Vincoli
     * Classi" sheet) for the 3 windows the school confirmed; all other
     * groups' Stage hours (analysis.md section 1: G07/G08/G10/G09/
     * G13 all have Stage pseudo-teacher rows in incarichi.csv too) have no
     * corresponding stage_start/stage_end row in vincoli_classi.csv yet, so
     * no StagePattern is seeded for them - an open question for the school,
     * not invented here.
     *
     * <p>dayOfWeekMask is the BLACKOUT set (H4 fires when a non-professionalizzanti
     * lesson's date falls on one of these weekdays within [start,end] - see
     * BaseConstraintProvider.stageBlackout()). Derived MECHANICALLY from
     * claudedocs/spalmone-2026-27/data/lessons.csv: for each window, non-stage/
     * non-pseudo lesson-hours were aggregated per weekday (excluding Stage/
     * Esame/RA rows, which are scheduled AS lessons in the grid and would
     * otherwise swamp every weekday equally).</p>
     */
    private void generateStagePatterns() {
        List<StudentGroup> groups = StudentGroup.listAll();

        // G07: stage 2027-03-23..2027-05-28. Non-stage lessons during the
        // window are 100% on Monday (48h across 8 days; every other weekday
        // shows 0 non-stage hours) -> return day = Monday, blackout = Tue-Fri.
        addStagePattern(groups, "G07",
                LocalDate.of(2027, 3, 23), LocalDate.of(2027, 5, 28),
                Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));

        // G11: stage 2027-01-13..2027-03-19. vincoli_classi.csv note:
        // "4 giorni a settimana rientrando tutti i lunedì" (4 days/week
        // stage, returning every Monday) - confirmed mechanically: Monday
        // carries 48h/8 days of non-stage lessons, every other weekday ~0
        // (Friday has one 4h exception day).
        addStagePattern(groups, "G11",
                LocalDate.of(2027, 1, 13), LocalDate.of(2027, 3, 19),
                Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));

        // G12: professionalizzanti window 2026-11-20..2027-04-17.
        // vincoli_classi.csv note: "caricare ore prof. il giovedì e il
        // venerdì" (load professionalizzanti hours on Thu+Fri). Mechanically
        // INVERTED from the FB groups: Mon/Tue/Wed carry heavy regular-
        // lesson hours (80/80/89h across 16-18 days each - normal school),
        // while Thu/Fri are mostly blacked out (26h/4 days, 5h/1 day - only
        // the occasional professionalizzanti exception breaks through).
        //
        // WEDNESDAY DELIBERATELY NOT ADDED (TASK-370 investigated this): this
        // mask means "whole day reserved for professionalizzanti", which
        // doesn't describe Wednesday's real mixed usage (56 genuine non-prof
        // lesson-hours in-window, unlike Thu/Fri's near-total reservation) -
        // adding it would blow up H4's calibrated golden pin with spurious
        // violations, not fix anything. See CLAUDE.md "Professionalizzanti at
        // day edges (H22, task-370)" for the full mechanical derivation; the
        // docx's actual Wednesday rule is H22 (SchoolScheduleConstraintProvider),
        // a separate mechanism that doesn't touch StagePattern/H4 at all.
        addStagePattern(groups, "G12",
                LocalDate.of(2026, 11, 20), LocalDate.of(2027, 4, 17),
                Set.of(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
    }

    private void addStagePattern(List<StudentGroup> groups, String groupName,
                                 LocalDate start, LocalDate end, Set<DayOfWeek> mask) {
        StudentGroup group = findGroupByName(groups, groupName);
        if (group != null) {
            new StagePattern(group, start, end, mask).persist();
        }
    }

    /**
     * Per-group weekly opening pattern (TASK-342): each entry is
     * {groupName, MON, TUE, WED, THU, FRI}, where each weekday value is an
     * inclusive slot-index range "min-max" (see TimeslotSequence.SLOT_START_TIMES),
     * or null if the group never has lessons that weekday.
     *
     * Derived MECHANICALLY (not guessed) by aggregating the union of occupied
     * slot indices per (group, weekday) across the full 2026/27 school year in
     * claudedocs/spalmone-2026-27/data/lessons.csv (all rows with closed=False,
     * teacher non-empty, day != Sabato). Every group's per-day union came out as
     * a contiguous [0, max] range with no gaps, confirming the minSlot=0 /
     * contiguous-range assumption from the task spec. See analysis.md section 2.
     *
     * G14 is a real StudentGroup (ScheduleService.EXCLUDED_GROUPS is
     * empty). Its row is null/null/null (fully closed Mon/Tue/Wed - zero
     * Monday lessons confirmed mechanically in lessons.csv, see
     * generateGroupCalendarRules()), Thu "0-4", Fri "0-2" - verified against
     * GroupCalendarServiceTest#G14Shape_onlyThursdayAndFridayOpen.
     */
    static final String[][] GROUP_CALENDAR_PATTERNS = {
            // group,    MON,    TUE,    WED,    THU,    FRI
            {"G03",   "0-5",  "0-5",  "0-5",  "0-5",  "0-4"},
            {"G07",   "0-5",  "0-7",  "0-7",  "0-7",  "0-7"},
            {"G11",   "0-5",  "0-7",  "0-7",  "0-7",  "0-7"},
            {"G02",    "0-5",  "0-5",  "0-5",  "0-5",  "0-4"},
            {"G06",    "0-5",  "0-5",  "0-7",  "0-5",  "0-4"},
            {"G10",    "0-4",  "0-7",  "0-7",  "0-7",  "0-7"},
            {"G12",   "0-4",  "0-4",  "0-4",  "0-7",  "0-6"},
            {"G01",   "0-5",  "0-5",  "0-5",  "0-5",  "0-4"},
            {"G05",   "0-5",  "0-5",  "0-5",  "0-5",  "0-4"},
            {"G09",   "0-7",  "0-7",  "0-7",  "0-7",  "0-7"},
            {"G04",    "0-7",  "0-7",  "0-7",  "0-7",  "0-7"},
            {"G08",    "0-7",  "0-7",  "0-7",  "0-7",  "0-7"},
            {"G13",     "0-5",  "0-7",  "0-7",  "0-7",  "0-7"},
            {"G14",    null,   null,   null,   "0-4",  "0-2"},
    };

    static final DayOfWeek[] GROUP_CALENDAR_WEEKDAYS = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    };

    /**
     * Seed GroupCalendarRule rows (TASK-342) from GROUP_CALENDAR_PATTERNS, plus
     * one date-scoped override for the first school day.
     *
     * First-day ramp: 2026-09-14 (Monday, TimeslotSequence.SCHOOL_YEAR_START) is the only date in the
     * whole year where every group's lessons.csv rows are confined to slots
     * 1-4 (09:00-13:00) regardless of that group's normal Monday range — the
     * school starts an hour late and finishes at lunchtime on day one only
     * (checked 2026-09-14..09-18: only 09-14 shows this narrowing). Modeled as
     * one narrow-date rule per group so it wins over the regular Monday rule
     * via "most specific" resolution (see GroupCalendarService).
     *
     * <p>G14 is excluded from the ramp override: its Monday cell
     * is null (fully closed - zero G14 lessons on any Monday all year,
     * including 2026-09-14, per lessons.csv), so opening slots 1-4 for it on
     * the first day would contradict the real data.</p>
     */
    private void generateGroupCalendarRules() {
        List<StudentGroup> groups = StudentGroup.listAll();
        List<StudentGroup> rampEligibleGroups = new ArrayList<>();

        for (String[] row : GROUP_CALENDAR_PATTERNS) {
            String groupName = row[0];
            StudentGroup group = findGroupByName(groups, groupName);
            if (group == null) continue;
            for (int i = 0; i < GROUP_CALENDAR_WEEKDAYS.length; i++) {
                int[] slots = parseCalendarCell(row[i + 1]);
                new GroupCalendarRule(group, GROUP_CALENDAR_WEEKDAYS[i], slots[0], slots[1]).persist();
            }
            // row[1] is the Monday cell; null means fully closed that day.
            if (row[1] != null) {
                rampEligibleGroups.add(group);
            }
        }

        // Scoped to groups with an open Monday (not every StudentGroup) so a
        // future group with no seeded pattern row - or one that's closed on
        // Monday, like G14 - doesn't silently get a Monday calendar it
        // shouldn't have on the first school day.
        for (StudentGroup group : rampEligibleGroups) {
            new GroupCalendarRule(group, DayOfWeek.MONDAY, 1, 4, TimeslotSequence.SCHOOL_YEAR_START, TimeslotSequence.SCHOOL_YEAR_START).persist();
        }
    }

    /**
     * Parses one GROUP_CALENDAR_PATTERNS cell ("min-max", e.g. "0-5") into a
     * {@code [minSlot, maxSlot]} pair. A null cell means the group never has
     * lessons that weekday, and is parsed as the closed-day sentinel
     * (maxSlot &lt; minSlot) — see {@link com.school.planner.domain.GroupCalendarRule}.
     * Package-private static (no DB access) so it's directly unit-testable
     * from GroupCalendarSeedTest.
     */
    static int[] parseCalendarCell(String range) {
        if (range == null) return new int[] {0, -1};
        String[] parts = range.split("-");
        return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    /**
     * Seed {@link DailyActivityQuota} rows (TASK-381 "L1-bis") from
     * calendario_fc.csv ({@code group,date,aula_hours,lab_hours,stage_hours}) -
     * the school's authoritative "DEF FC TUTTI" sheet for G04/G08/G13
     * (990h each). Only rows with {@code (aula+lab+stage) > 0} AND a date
     * inside the Timeslot horizon ({@link TimeslotSequence#SCHOOL_YEAR_START}..
     * {@link TimeslotSequence#SCHOOL_YEAR_END}) are persisted: a zero-hour row
     * carries no information ({@link DailyActivityQuota}'s own javadoc treats
     * "no row" as "fully closed"), and a beyond-horizon row has no {@link
     * Timeslot} to ever place a Lesson on. Skipped out-of-horizon hours are
     * logged, not silently dropped: 36 total on the current sheet (G08 24
     * aula-hours across 2027-06-14..16, G13 12 aula-hours across
     * 2027-06-15..16 - verified mechanically against the real CSV, TASK-381
     * task brief). Expected persisted total: 513 rows (G04 aula 745/lab
     * 245/stage 0; G08 560/106/300; G13 473/210/295).
     *
     * <p>Package-private (not private): {@code DataGeneratorDailyActivityQuotaSeedTest}
     * calls this directly against a minimal fixture, same convention as
     * {@code CalendarioImportService#consumeStubs} and this class's own
     * {@link #loadSeedCsv}.</p>
     */
    void generateDailyActivityQuotas() {
        List<StudentGroup> groups = StudentGroup.listAll();
        int persisted = 0;
        int skippedOutOfHorizonRows = 0;
        int skippedOutOfHorizonHours = 0;
        for (String[] row : loadSeedCsv("calendario_fc.csv", 5)) {
            String groupName = row[0].trim();
            LocalDate date = LocalDate.parse(row[1].trim());
            int aulaHours = Integer.parseInt(row[2].trim());
            int labHours = Integer.parseInt(row[3].trim());
            int stageHours = Integer.parseInt(row[4].trim());
            int totalHours = aulaHours + labHours + stageHours;
            if (totalHours <= 0) continue;
            if (date.isBefore(TimeslotSequence.SCHOOL_YEAR_START) || date.isAfter(TimeslotSequence.SCHOOL_YEAR_END)) {
                skippedOutOfHorizonRows++;
                skippedOutOfHorizonHours += totalHours;
                continue;
            }
            StudentGroup group = findGroupByName(groups, groupName);
            if (group == null) continue;
            // TASK-550 extension: past the PRE-extension horizon the CSV diverges from
            // the golden (G08 8h vs held 5h; G04 06-15 zero vs held 5; G13 rows
            // with no golden practice): the golden holds, the sheet row is skipped and
            // the golden-mirrored row is seeded after the loop instead.
            if (date.isAfter(TimeslotSequence.PRE_EXTENSION_YEAR_END)) {
                skippedOutOfHorizonRows++;
                continue;
            }
            new DailyActivityQuota(group, date, aulaHours, labHours, stageHours).persist();
            persisted++;
        }
        int mirrored = 0;
        for (var byGroup : goldenHeldBeyondHorizon().entrySet()) {
            StudentGroup group = findGroupByName(groups, byGroup.getKey());
            if (group == null) continue;
            for (var byDate : byGroup.getValue().entrySet()) {
                int aula = byDate.getValue()[0];
                int stage = byDate.getValue()[1];
                if (aula + stage <= 0) continue;
                new DailyActivityQuota(group, byDate.getKey(), aula, 0, stage).persist();
                persisted++;
                mirrored++;
            }
        }
        LOG.infof("generateDailyActivityQuotas: persisted %d rows (%d golden-mirrored beyond 06-10, TASK-550 extension), skipped %d out-of-horizon rows (%d hours)",
                persisted, mirrored, skippedOutOfHorizonRows, skippedOutOfHorizonHours);
    }

    /**
     * The 3 groups already seeded by {@link #generateDailyActivityQuotas}
     * from calendario_fc.csv's finer aula/lab/stage split - {@link
     * #generateExpectedDailyHours} below must never double-seed them from
     * the coarser calendario_class_days.csv sheet (TASK-385).
     */
    private static final Set<String> FC_QUOTA_GROUP_NAMES = Set.of("G04", "G08", "G13");

    /**
     * Additive sibling of {@link #generateDailyActivityQuotas} (TASK-385):
     * seeds {@link DailyActivityQuota} rows for the OTHER 11 groups (every
     * 2026/27 group except {@link #FC_QUOTA_GROUP_NAMES}) from {@code
     * calendario_class_days.csv} ({@code group,date,hours,modality}) - the
     * school's per-class per-date expected-hours sheet, all 14 groups.
     * Since TASK-462 a date MAY be split across two rows (G14, read from
     * the real 'G14 2' sheet, has 5 aula+lab days): rows are aggregated
     * into one {@link DailyActivityQuota} per (group, date), and only a
     * duplicate (group, date, modality) row stays fatal.
     *
     * <p>Every persisted row carries {@code FCQuota = false} (see that
     * field's own javadoc): H23/H24/M30/M31 and the aula/lab VRP filter in
     * {@code Lesson#getAvailableTimeslots()} stay scoped to the ORIGINAL 3
     * FC groups exactly as before this method existed - the new universal
     * expected-hours FACT this method adds is read through {@link
     * com.school.planner.domain.SchoolSchedule#getExpectedHours} instead
     * (M20/the weekly template/the stamp, TASK-385), never through the
     * "quota group" machinery.</p>
     *
     * <p>Same horizon/zero-hour skip convention as {@link
     * #generateDailyActivityQuotas}: 12 out-of-horizon rows (all past {@link
     * TimeslotSequence#SCHOOL_YEAR_END} - the sheet runs to 2027-06-24) are
     * dropped; none of the sheet's non-FC rows are currently zero-hour.
     * Expected persisted total: 1,739 entities across the 11 groups (per-group
     * aula/lab/stage sums independently re-derived from the raw CSV, see
     * {@code DataGeneratorExpectedDailyHoursSeedTest}).</p>
     *
     * <p>Package-private (not private): {@code DataGeneratorExpectedDailyHoursSeedTest}
     * calls this directly against a minimal fixture, same convention as
     * {@link #generateDailyActivityQuotas}.</p>
     */
    void generateExpectedDailyHours() {
        List<StudentGroup> groups = StudentGroup.listAll();
        int persisted = 0;
        int skippedOutOfHorizonRows = 0;
        int skippedOutOfHorizonHours = 0;
        // getExpectedHours assumes ONE ENTITY per (group, date), but since
        // TASK-462 a date may span two CSV rows (e.g. G14 2027-03-11:
        // A=2 + L=3), so rows aggregate here; only a duplicate (group, date,
        // modality) row stays fatal.
        record GroupDateKey(String groupName, LocalDate date) {}
        Map<GroupDateKey, int[]> byGroupDate = new LinkedHashMap<>(); // value: {aula, lab, stage}
        Set<String> seenGroupDateModality = new LinkedHashSet<>();
        for (String[] row : loadSeedCsv("calendario_class_days.csv", 4)) {
            String groupName = row[0].trim();
            if (FC_QUOTA_GROUP_NAMES.contains(groupName)) continue; // already seeded above, finer source
            LocalDate date = LocalDate.parse(row[1].trim());
            // Double-then-round like every other seed-CSV hours reader (a
            // re-exported sheet may format hours as "8.0").
            int hours = (int) Math.round(Double.parseDouble(row[2].trim()));
            if (hours <= 0) continue;
            String modality = row[3].trim();
            if (!seenGroupDateModality.add(groupName + "|" + date + "|" + modality)) {
                throw new RuntimeException("generateExpectedDailyHours: duplicate (group, date, modality) row in "
                        + "calendario_class_days.csv for " + groupName + " " + date + " " + modality);
            }
            if (date.isBefore(TimeslotSequence.SCHOOL_YEAR_START) || date.isAfter(TimeslotSequence.SCHOOL_YEAR_END)) {
                skippedOutOfHorizonRows++;
                skippedOutOfHorizonHours += hours;
                continue;
            }
            // TASK-550 extension: past the pre-extension horizon the sheet's rows
            // (G09/G10/G11/G13 in the 06-11..15 window) have NO golden
            // practice behind them - the golden holds nothing for these groups there.
            // Authorization (maintainer 07:50): capacity ONLY where the golden practices
            // it; H26 keeps every other group out of those days. Skip them.
            if (date.isAfter(TimeslotSequence.PRE_EXTENSION_YEAR_END)) {
                skippedOutOfHorizonRows++;
                skippedOutOfHorizonHours += hours;
                continue;
            }
            int[] split = byGroupDate.computeIfAbsent(new GroupDateKey(groupName, date), k -> new int[3]);
            if ("A".equals(modality)) split[0] += hours;
            else if ("L".equals(modality)) split[1] += hours;
            else if ("S".equals(modality)) split[2] += hours;
            else {
                // Fail loud: pre-TASK-462 an unknown modality at least collided with the
                // (group, date) dup check when it shared a date; silently dropping its
                // hours here would shrink a day's quota with no error anywhere.
                throw new RuntimeException("generateExpectedDailyHours: unknown modality '" + modality
                        + "' in calendario_class_days.csv for " + groupName + " " + date);
            }
        }
        for (Map.Entry<GroupDateKey, int[]> e : byGroupDate.entrySet()) {
            StudentGroup group = findGroupByName(groups, e.getKey().groupName());
            if (group == null) continue;
            int[] s = e.getValue();
            new DailyActivityQuota(group, e.getKey().date(), s[0], s[1], s[2], false).persist();
            persisted++;
        }
        LOG.infof("generateExpectedDailyHours: persisted %d rows, skipped %d out-of-horizon rows (%d hours)",
                persisted, skippedOutOfHorizonRows, skippedOutOfHorizonHours);
    }

    /**
     * Seed {@link RecurringAvailabilityRule} rows (TASK-345) from
     * src/main/resources/seed/availability_rules.csv: the machine-transcribable
     * subset of the "Vincoli Docenti" free-text sheet (see analysis.md section 5
     * and claudedocs/spalmone-2026-27/availability-not-yet-modeled.md for the
     * full transcription table, including every clause that could NOT be
     * expressed this way). Rows are templates only — {@code onStartup()} calls
     * {@code availabilityExpansionService.expandAll()} right after this method
     * to turn them into the dated {@code TeacherAvailability} rows H5/S3
     * actually read.
     *
     * <p>Fails loud (throws, aborting startup) on the same two invariants the
     * REST layer enforces with a 400 — {@link RecurringAvailabilityRule#validateDateCoherence}
     * and {@link RecurringAvailabilityRule#overlaps} — since the curated 27
     * rows in the CSV must be exactly as coherent as anything entered through
     * the API; a bad row here would otherwise silently mis-seed real school
     * data. The overlap check re-queries {@link RecurringAvailabilityRule#listByTeacher}
     * after each persist rather than keeping a separate in-memory list —
     * {@code onStartup()} is {@code @Transactional} and the id is
     * {@code IDENTITY}-generated (inserted immediately on {@code persist()}),
     * so every already-seeded row for that teacher is already visible to the
     * query, exactly like the REST layer's own overlap check.
     */
    private void generateAvailabilityRules(List<Teacher> teachers) {
        for (String[] row : loadSeedCsv("availability_rules.csv", 8)) {
            String teacherName = row[0].trim();
            Teacher teacher = findTeacherByExactName(teachers, teacherName);
            if (teacher == null) {
                LOG.warnf("availability_rules.csv: unknown teacher '%s', skipping row", teacherName);
                continue;
            }
            DayOfWeek dayOfWeek = DayOfWeek.valueOf(row[1].trim());
            int minSlot = Integer.parseInt(row[2].trim());
            int maxSlot = Integer.parseInt(row[3].trim());
            AvailabilityType type = AvailabilityType.valueOf(row[4].trim());
            LocalDate effectiveFrom = row[5].isBlank() ? null : LocalDate.parse(row[5].trim());
            LocalDate effectiveTo = row[6].isBlank() ? null : LocalDate.parse(row[6].trim());
            String note = row[7].trim();

            String dateError = RecurringAvailabilityRule.validateDateCoherence(dayOfWeek, effectiveFrom, effectiveTo);
            if (dateError != null) {
                throw new IllegalStateException("availability_rules.csv row for '" + teacherName + "': " + dateError);
            }

            RecurringAvailabilityRule rule = new RecurringAvailabilityRule(teacher, dayOfWeek, minSlot, maxSlot, type,
                    effectiveFrom, effectiveTo, note);
            for (RecurringAvailabilityRule existing : RecurringAvailabilityRule.listByTeacher(teacher)) {
                if (RecurringAvailabilityRule.overlaps(rule, existing)) {
                    throw new IllegalStateException("availability_rules.csv: rule for '" + teacherName + "' ("
                            + rule + ") overlaps an already-seeded rule (" + existing + ")");
                }
            }

            rule.persist();
        }
    }

    /**
     * The ProfessionalizzantiDay table is intentionally seeded with NOTHING
     * for the 2026/27 school year: inventing exception days without school
     * confirmation would be a guess, not data. TASK-364 imports the real
     * pre-set 2026/27 calendar, which supersedes hand-authored
     * ProfessionalizzantiDay rows entirely. The method is kept so its two
     * call sites in onStartup() keep compiling.
     */
    private void generateProfessionalizzantiDays() {
        // Intentionally empty - see javadoc above and TASK-364.
    }

    /**
     * Create LabAssistant records linking teachers to their lab rooms.
     * The solver uses these as MEDIUM constraints (M-LA) to prefer scheduling
     * the assistant in the lab when lab sessions are running, rather than
     * requiring it as a HARD constraint on every course.
     */
    private void createLabAssistants(List<Teacher> teachers) {
        createLabAssistant(findTeacherByExactName(teachers, "T18"), "acconciatura");
        createLabAssistant(findTeacherByExactName(teachers, "T10"), "meccanica");
        createLabAssistant(findTeacherByExactName(teachers, "T14"), "moda");
        // TASK-475 A1 (APPLICATO, maintainer 01/08): T20 e' il secondo assistente moda (doppio
        // R16). Evidenza verbatim estratto vincoli-tabs-golden-xlsx-2026-07-31.txt riga 97:
        // "T20 | Libero per doppio in 2 (100 ore) e g09 (85 ore) in R16". M-LA
        // (BaseConstraintProvider.labAssistantConflictLessonLesson) gestisce la co-presenza
        // automaticamente, nessun nuovo vincolo. A2 (M budget, se i budget sono vincolanti)
        // resta design gated referente.
        createLabAssistant(findTeacherByExactName(teachers, "T20"), "moda");
        createLabAssistant(findTeacherByExactName(teachers, "T03"), "termoidraulica");
    }

    private void createLabAssistant(Teacher teacher, String roomTag) {
        LabAssistant la = new LabAssistant(teacher, roomTag);
        la.persist();
    }

    /**
     * The LabRotation table is intentionally seeded with NOTHING for the
     * 2026/27 school year: inventing rotation days without school
     * confirmation would be a guess, not data. TASK-364 imports the real
     * pre-set 2026/27 calendar (the golden lessons.csv reference), which
     * supersedes hand-authored LabRotation rows entirely. The method is kept
     * (with no parameters, since it seeds nothing) so its call in
     * onStartup() and the pre-assignment pipeline (HybridPhase1aService,
     * GreedyRotationAssigner) keep compiling against a real (empty)
     * LabRotation table.
     *
     * <p>An empty LabRotation table makes {@code HybridPhase1aService.
     * getPeriodStart()/getPeriodEnd()} always fall through to their default
     * case (no earliest/latest row to query): {@code TimeslotSequence.
     * SCHOOL_YEAR_START/END}, so pre-assignment room-tag rotation testing
     * doesn't silently run against last year's dates.</p>
     *
     * @see HybridPhase1aService#loadLabSessionsByRoomTag(String)
     * @see GreedyRotationAssigner
     */
    private void generateLabRotations() {
        // Intentionally empty - see javadoc above and TASK-364.
    }

    private StudentGroup findGroupByName(List<StudentGroup> groups, String name) {
        return groups.stream()
                .filter(g -> g.name.equals(name))
                .findFirst()
                .orElse(null);
    }

    // Subject taxonomy (roomTags / isLab / professionalizzanti) moved to
    // SubjectRegistry + seed/subjects.csv (TASK-437, audit C1). Per-(group,
    // subject) exceptions live in seed/subject_overrides.csv. The old hardcoded
    // Italian-prefix matchers (getRoomTags/isLabSubject/isProfessionalizzante)
    // are gone: a new school year is planned by editing the CSV, not Java.

    /**
     * Exact match against the seeded teacher short names. Not a prefix
     * match: a future short name that happens to be a prefix of another
     * (e.g. "Bassi" vs "T03") must not silently resolve to the wrong
     * teacher. Callers still get the fail-loud unknown-teacher
     * RuntimeException when no exact match exists (see generateCourses()).
     */
    private Teacher findTeacherByExactName(List<Teacher> teachers, String name) {
        return teachers.stream()
                .filter(t -> t.name.equals(name))
                .findFirst()
                .orElse(null);
    }

    private void generateSchoolHolidays() {
        // 2026/27 calendar closures (TASK-341, spalmone override 2026-07-16),
        // transcribed by hand from claudedocs/spalmone-2026-27/data/closures.csv
        // (not on the runtime classpath). Derived from CLOSURES_2026_27 - the same
        // map that CLOSURE_DATES_2026_27 uses to exclude days from
        // generateTimeslots() - restricted to dates within the school year (all
        // 29 entries fall in range since the TASK-408 typo fix, 2026-01-22 -> 2027-01-22;
        // the in-range guard below is now a no-op but kept as a defensive invariant).
        for (Map.Entry<LocalDate, String> closure : CLOSURES_2026_27.entrySet()) {
            LocalDate date = closure.getKey();
            if (date.isBefore(TimeslotSequence.SCHOOL_YEAR_START) || date.isAfter(TimeslotSequence.SCHOOL_YEAR_END)) continue;
            new SchoolHoliday(date, closure.getValue()).persist();
        }
    }

}
