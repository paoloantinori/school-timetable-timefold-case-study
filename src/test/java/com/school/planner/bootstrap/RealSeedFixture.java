package com.school.planner.bootstrap;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.solver.TimeslotSequence;

/**
 * Shared real-seed fixture core for {@link CalendarioImportRealSeedStageTest}
 * and {@link WeeklyTemplateConstructionRealSeedTest}: real school-year
 * Timeslots (mirrors {@code DataGenerator.generateTimeslots}), all 14 real
 * StudentGroups (groups.csv), and GroupCalendarRule for every group/weekday
 * including the Monday ramp (mirrors {@code
 * DataGenerator.generateGroupCalendarRules}) - byte-for-byte identical in
 * both callers before this extraction.
 *
 * <p>Package-private, in {@code com.school.planner.bootstrap} rather than
 * {@code com.school.planner.testsupport}: every {@code DataGenerator} member
 * this needs ({@code loadSeedCsv}, {@code computeSchoolDays}, {@code
 * GROUP_CALENDAR_PATTERNS}, {@code GROUP_CALENDAR_WEEKDAYS}, {@code
 * parseCalendarCell}, {@code STANDARD_SLOTS}, {@code CLOSURE_DATES_2026_27})
 * is itself package-private, so a cross-package {@code testsupport} helper
 * (the {@link com.school.planner.testsupport.ModaCourseFixtures}/{@code
 * LessonFixtures} pattern) cannot call them - {@code DataGenerator.java} is
 * protected seed data and neither {@code generateTimeslots} nor {@code
 * generateGroupCalendarRules} may be widened or touched to work around
 * that (see the two callers' own javadoc for why this replication exists at
 * all).</p>
 */
final class RealSeedFixture {

    private RealSeedFixture() {
    }

    /**
     * Mirrors DataGenerator's private MAX_HOURS_PER_DAY table (TASK-343) — the
     * per-group H11 caps. Single shared copy for every real-seed test in this
     * package (was duplicated 5×; DataGenerator's own table is package-private
     * seed data that must not be widened, same rationale as the class javadoc).
     */
    static final Map<String, Integer> MAX_HOURS_PER_DAY = Map.ofEntries(
            Map.entry("G01", 6), Map.entry("G02", 6), Map.entry("G03", 6), Map.entry("G04", 8),
            Map.entry("G05", 6), Map.entry("G06", 8), Map.entry("G07", 8), Map.entry("G08", 8),
            Map.entry("G09", 8), Map.entry("G10", 8), Map.entry("G11", 8), Map.entry("G12", 8),
            Map.entry("G13", 8), Map.entry("G14", 5));

    /**
     * @param excludedDate         a single school date to skip when generating Timeslots
     *                              (null = generate all) - {@link CalendarioImportRealSeedStageTest}'s
     *                              one known MODA mixed-shift conflict date
     * @param maxHoursPerDayByGroup group name -> {@code StudentGroup.maxHoursPerDay} override
     *                              (null = leave every group's field unset) - {@link
     *                              WeeklyTemplateConstructionRealSeedTest}'s H11 table
     */
    static Map<String, StudentGroup> seedTimeslotsGroupsAndCalendarRules(LocalDate excludedDate,
            Map<String, Integer> maxHoursPerDayByGroup) {
        for (LocalDate date : DataGenerator.computeSchoolDays(
                TimeslotSequence.SCHOOL_YEAR_START, TimeslotSequence.SCHOOL_YEAR_END, DataGenerator.CLOSURE_DATES_2026_27)) {
            if (date.equals(excludedDate)) continue;
            for (LocalTime[] slot : DataGenerator.STANDARD_SLOTS) {
                new Timeslot(LocalDateTime.of(date, slot[0]), LocalDateTime.of(date, slot[1])).persist();
            }
        }

        Map<String, StudentGroup> groups = new LinkedHashMap<>();
        // Mirrors DataGenerator.NO_PROFESSIONALIZZANTI_DURING_STAGE (private;
        // drift risk documented like the room tag map copy in seedRooms)
        java.util.Set<String> noProfDuringStage = java.util.Set.of("G08", "G09");
        for (String[] row : DataGenerator.loadSeedCsv("groups.csv", 3)) {
            StudentGroup g = new StudentGroup(row[0].trim(), Integer.parseInt(row[2].trim()));
            if (maxHoursPerDayByGroup != null) {
                g.maxHoursPerDay = maxHoursPerDayByGroup.get(g.name);
            }
            g.allowProfessionalizzantiDuringStage = !noProfDuringStage.contains(g.name);
            g.persist();
            groups.put(g.name, g);
        }

        List<StudentGroup> rampEligible = new ArrayList<>();
        for (String[] row : DataGenerator.GROUP_CALENDAR_PATTERNS) {
            StudentGroup group = groups.get(row[0]);
            if (group == null) continue;
            for (int i = 0; i < DataGenerator.GROUP_CALENDAR_WEEKDAYS.length; i++) {
                int[] slots = DataGenerator.parseCalendarCell(row[i + 1]);
                new GroupCalendarRule(group, DataGenerator.GROUP_CALENDAR_WEEKDAYS[i], slots[0], slots[1]).persist();
            }
            if (row[1] != null) rampEligible.add(group);
        }
        for (StudentGroup group : rampEligible) {
            new GroupCalendarRule(group, DayOfWeek.MONDAY, 1, 4,
                    TimeslotSequence.SCHOOL_YEAR_START, TimeslotSequence.SCHOOL_YEAR_START).persist();
        }

        return groups;
    }

    /**
     * Real teachers (teachers.csv, incl. per-teacher {@code maxDailyHours}) plus their
     * recurring availability rules (availability_rules.csv), expanded into concrete
     * {@code TeacherAvailability} rows via {@code AvailabilityExpansionService.expandAll()}
     * — extracted from {@link HumanFaithfulConstructionSpikeTest}'s fixture so VRP-fidelity
     * harnesses ({@link BoxVrpFeasibilityDiscriminatorTest}) can seed the H5/H7 inputs
     * without also generating the full 10k-Lesson population.
     */
    static Map<String, Teacher> seedTeachersAndAvailabilityRules(AvailabilityExpansionService availabilityExpansionService) {
        return seedTeachersAndAvailabilityRules(availabilityExpansionService, true);
    }

    /**
     * @param includeQ22GoldenDerivedRules false = skip the Q22 fixed-day rows (note
     *        prefix "Q22", 2026-07-24) — for harnesses that reconstruct a SOLVE
     *        ARTIFACT produced BEFORE those rules existed ({@code BoxRepairCheckpointTest},
     *        {@code BoxVrpFeasibilityDiscriminatorTest}): under the new rules the old
     *        grid legitimately scores H5 hard (the measure of how much those solutions
     *        violated the then-unencoded constraints), which is not those tests' claim.
     */
    static Map<String, Teacher> seedTeachersAndAvailabilityRules(
            AvailabilityExpansionService availabilityExpansionService, boolean includeQ22GoldenDerivedRules) {
        Map<String, Teacher> teachers = new LinkedHashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("teachers.csv", 6)) {
            Teacher t = new Teacher(row[0].trim());
            String maxDailyHours = row[3].trim();
            t.maxDailyHours = maxDailyHours.isEmpty() ? null : Integer.valueOf(maxDailyHours);
            t.persist();
            teachers.put(t.name, t);
        }
        for (String[] row : DataGenerator.loadSeedCsv("availability_rules.csv", 8)) {
            Teacher teacher = teachers.get(row[0].trim());
            if (teacher == null) continue;
            if (!includeQ22GoldenDerivedRules && row[7] != null && row[7].trim().startsWith("Q22")) continue;
            DayOfWeek dayOfWeek = DayOfWeek.valueOf(row[1].trim());
            int minSlot = Integer.parseInt(row[2].trim());
            int maxSlot = Integer.parseInt(row[3].trim());
            AvailabilityType type = AvailabilityType.valueOf(row[4].trim());
            LocalDate effectiveFrom = row[5].isBlank() ? null : LocalDate.parse(row[5].trim());
            LocalDate effectiveTo = row[6].isBlank() ? null : LocalDate.parse(row[6].trim());
            new RecurringAvailabilityRule(teacher, dayOfWeek, minSlot, maxSlot, type, effectiveFrom, effectiveTo, row[7].trim())
                    .persist();
        }
        availabilityExpansionService.expandAll();
        return teachers;
    }

    /**
     * The 18 real Rooms, mirroring {@code DataGenerator.generateRooms()} byte-for-byte:
     * reads {@code seed/rooms.csv} (name, capacity — the authoritative shared resource,
     * NOT {@code DataGenerator.java}) and applies the SAME tag map as {@code
     * DataGenerator.ROOM_TAG_MAP} (7 tagged rooms: the labs/officina/R16/R17;
     * the rest tagless. R17 has cap 22 (referente 29/07); R18 stays cap 0).
     *
     * <p><b>Drift risk (documented):</b> the tag map is DUPLICATED from {@code
     * DataGenerator.ROOM_TAG_MAP} because that constant is private and {@code
     * DataGenerator} is protected seed data that must not be widened to expose it. If a
     * room is ever added to {@code rooms.csv} AND its tag to {@code ROOM_TAG_MAP}, this
     * copy must follow — a {@link DataGeneratorSeedTest}-style count check is the guard.
     * Needed by the localized room-phase harnesses (ph5+6: H2/H-M2/M3/M-LA), which the
     * golden/checkpoint fixtures cannot exercise because neither seeds any Room.
     */
    static java.util.List<com.school.planner.domain.Room> seedRooms() {
        // Mirrors DataGenerator.ROOM_TAG_MAP (private, see javadoc).
        java.util.Map<String, String> roomTagMap = java.util.Map.of(
                "R14", "termoidraulica",
                "R15", "meccanica",
                "R11", "acconciatura",
                "R12", "informatica",
                "R13", "informatica",
                "R16", "moda",
                "R17", "saldatura");
        java.util.List<com.school.planner.domain.Room> rooms = new java.util.ArrayList<>();
        for (String[] row : DataGenerator.loadSeedCsv("rooms.csv", 2)) {
            String name = row[0].trim();
            int capacity;
            try {
                capacity = Integer.parseInt(row[1].trim());
            } catch (NumberFormatException e) {
                throw new RuntimeException("seedRooms: malformed capacity in rooms.csv row: "
                        + java.util.Arrays.toString(row), e);
            }
            String tag = roomTagMap.get(name);
            com.school.planner.domain.Room room =
                    new com.school.planner.domain.Room(name, capacity, tag != null ? List.of(tag) : List.of());
            room.persist();
            rooms.add(room);
        }
        return rooms;
    }
}
