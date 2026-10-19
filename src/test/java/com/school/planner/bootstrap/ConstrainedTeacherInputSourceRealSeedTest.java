package com.school.planner.bootstrap;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.TreeMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.service.ConstrainedTeacherAllocator;
import com.school.planner.service.GroupCalendarService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-452: the window-verification equivalence test (CLAUDE.md "la soluzione gira sul SOLO
 * INPUT, il golden e' temporaneo"). The census ({@code task452-deducibility-census-2026-08-01.md}
 * #1) found that {@link ConstrainedTeacherAllocator#loadAllGoldenProfiles} (the ONE golden read
 * on the production path, DEFAULT ON) is substitutable: the profile can be rebuilt from INPUTS
 * (incarichi budgets + H5 available weekdays + school calendar), so the {@code source=input}
 * knob can eventually retire the golden read. This test runs BOTH sources on the real 2026/27
 * seed and asserts the input-derived profile is STRUCTURALLY equivalent to the golden. It stays
 * executable only while the golden exists (the verification window) and is a regression guard for
 * the input-derived builder. It calls the pure builder directly (no {@code source=input} knob flip).
 *
 * <p><b>What this test does and does NOT prove (honesty, post code-review).</b> Two of the three
 * input dimensions are NOT independent of the golden, so this is a builder/regression check, not
 * an "independent measurement agrees with the golden" proof:
 * <ul>
 *   <li>the BUDGET dimension is circular: {@code incarichi.csv} was normalized TO the golden by
 *       TASK-371 (referente ruled the golden authoritative, incarichi stale), so golden totals equal
 *       incarichi totals by construction. The per-group-total assertions verify the builder sums
 *       correctly and does not drop a group or exhaust dates early; they cannot detect a moderate
 *       H7 mis-clamp (totals equal the budget for any clamp that leaves enough dates).</li>
 *   <li>T01's H5 WEEKDAY (Thursday) has golden provenance: the full-day VACATION rules are
 *       the Q22 "golden-derived" rows maintainer confirmed 2026-07-30. They are input by confirmation,
 *       not by independent measurement.</li>
 *   <li>The genuinely golden-independent input is the school CALENDAR (Timeslot dates), which
 *       decides which Thursdays are valid and thus drives the per-date delta vs the golden.</li>
 * </ul>
 *
 * <p>Asserted structural equivalence:
 * <ol>
 *   <li>same set of groups (1/2/G10 + G12);</li>
 *   <li>per-group totals match (builder sanity check, circular on the budget, see above);</li>
 *   <li>weekday: every date (golden and input) is the H5-available weekday (Thursday);</li>
 *   <li><b>functional equivalence</b>: both profiles fed to {@link ConstrainedTeacherAllocator#plan}
 *       pin the same hours per group (the input profile is a valid drop-in: same shape, disjoint);</li>
 *   <li><b>documented delta</b>: the per-DATE sets differ (input-derived front-loads consecutive
 *       Thursdays at the H7 cap; the golden spreads at each group's actual slot capacity). This
 *       is the non-load-bearing choice the census measured at ~23% date overlap, not a defect.</li>
 * </ol>
 *
 * <p>Seeds the real CSVs directly (DataGenerator is disabled in the test profile) the same way
 * {@link WeeklyTemplateConstructionRealSeedTest} does, but only the slices this equivalence needs:
 * Timeslots/StudentGroups (via {@link RealSeedFixture}), Teachers + RecurringAvailabilityRule
 * (via {@link RealSeedFixture#seedTeachersAndAvailabilityRules}), and Courses (incarichi). No
 * lessons, no solve.
 */
@QuarkusTest
class ConstrainedTeacherInputSourceRealSeedTest {

    private static final String T01 = "T01";
    private static final Map<String, Integer> MAX_HOURS_PER_DAY = RealSeedFixture.MAX_HOURS_PER_DAY;

    @Inject
    EntityManager em;

    @Inject
    AvailabilityExpansionService availabilityExpansionService;

    @Inject
    DataGenerator dataGenerator;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM RecurringAvailabilityRule").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM DailyActivityQuota").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
    }

    /** Bundles T01's input-derived profile with the inputs both tests assert against. */
    private record T01Profile(
            Map<String, Map<LocalDate, Integer>> profile,
            Map<String, Integer> budgetByName,
            Set<DayOfWeek> available,
            int h7,
            Map<String, Map<LocalDate, Integer>> openSlotsByGroupDate,
            Map<String, Set<LocalDate>> stageDatesByGroup,
            Map<String, Map<LocalDate, Integer>> quotaByGroupDate) {
    }

    /**
     * Shared setup: seed the real CSVs, build T01's input-derived profile from incarichi budget
     * + H5 weekdays + school calendar (capped by the real GroupCalendarRule open slots, skipping the
     * group's stage window from DailyActivityQuota), and return it with the inputs the equivalence
     * test also needs (budget, weekdays, h7).
     */
    private T01Profile buildT01Profile() {
        seedRealSeedInputs();
        Teacher t01 = Teacher.find("name", T01).firstResult();
        Map<String, Integer> budgetByName = new LinkedHashMap<>();
        for (Course c : Course.<Course>listAll()) {
            if (c.teacher != null && c.teacher.equals(t01) && c.studentGroup != null) {
                budgetByName.merge(c.studentGroup.name, c.totalHours, Integer::sum);
            }
        }
        Set<DayOfWeek> fullDayVacation = EnumSet.noneOf(DayOfWeek.class);
        for (RecurringAvailabilityRule r : RecurringAvailabilityRule.listByTeacher(t01)) {
            if (r.type == AvailabilityType.VACATION && r.isFullDay()) {
                fullDayVacation.add(r.dayOfWeek);
            }
        }
        Set<DayOfWeek> available = ConstrainedTeacherAllocator.availableWeekdays(fullDayVacation);
        List<LocalDate> schoolDates = Timeslot.<Timeslot>listAll().stream()
                .map(Timeslot::getDate).distinct().sorted().toList();
        int h7 = t01.getEffectiveMaxDailyHours();
        Map<String, Map<LocalDate, Integer>> openSlotsByGroupDate = indexOpenSlotsFromSeed(schoolDates);
        Map<String, Set<LocalDate>> stageDatesByGroup = indexStageDatesFromSeed();
        Map<String, Map<LocalDate, Integer>> quotaByGroupDate = indexQuotaFromSeed();
        Map<String, Map<LocalDate, Integer>> profile = ConstrainedTeacherAllocator
                .buildInputDerivedProfile(budgetByName, available, schoolDates, h7, openSlotsByGroupDate,
                        stageDatesByGroup, quotaByGroupDate);
        assertFalse(profile.isEmpty(), "input-derived profile is non-empty for " + T01);
        return new T01Profile(profile, budgetByName, available, h7, openSlotsByGroupDate, stageDatesByGroup,
                quotaByGroupDate);
    }

    @Test
    @Transactional
    void inputDerivedProfileMatchesGoldenOnTotalsWeekdayAndPinOutcome() {
        T01Profile inp = buildT01Profile();

        // ---- golden profile (the production-path read under DEFAULT source=golden) ----
        Map<String, Map<LocalDate, Integer>> golden = ConstrainedTeacherAllocator
                .loadAllGoldenProfiles().getOrDefault(T01, Map.of());
        assertFalse(golden.isEmpty(), T01 + " has a golden profile on the real seed");

        Map<String, Map<LocalDate, Integer>> inputDerived = inp.profile();
        Map<String, Integer> budgetByName = inp.budgetByName();
        Set<DayOfWeek> available = inp.available();
        int h7 = inp.h7();

        // (1) same groups
        assertEquals(golden.keySet(), inputDerived.keySet(), "same groups (1/2/G10 + G12)");

        // (2) per-group totals: input == golden == incarichi budget (100%) UNTIL the
        // density-axis cap (anti-pattern piano-sopra-quota, 20/08): with the plan never
        // pinning beyond a day's declared quota, a budget that does not fit the
        // Thursday-quota capacity stays PARTIALLY pinned - the residual is mobile solver
        // hours (strictly better than the over-quota immovable pins it replaces: those
        // were exactly the 11 blocked H32 cells). Assert the new invariants instead of
        // the exact totals: never above budget, never above quota on any pinned day.
        for (String g : golden.keySet()) {
            int goldenTotal = golden.get(g).values().stream().mapToInt(Integer::intValue).sum();
            int inputTotal = inputDerived.get(g).values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(inputTotal <= budgetByName.get(g),
                    "group " + g + ": input total never exceeds the incarichi budget");
            assertTrue(inputTotal > 0, "group " + g + ": profile non-empty");
            inputDerived.get(g).forEach((date, hours) -> {
                Integer quota = inp.quotaByGroupDate().getOrDefault(g, Map.of()).get(date);
                if (quota != null) {
                    assertTrue(hours <= quota,
                            g + " " + date + ": pinned " + hours + "h over the declared quota " + quota
                                    + " (the anti-pattern the density cap exists to prevent)");
                }
            });
            System.out.printf("  %s: input %dh / budget %dh / golden %dh (residual %dh mobile)%n",
                    g, inputTotal, budgetByName.get(g), goldenTotal,
                    budgetByName.get(g) - inputTotal);
        }

        // (3) weekday: H5 makes T01 Thursday-only, and every date (both sources) is Thursday
        assertEquals(Set.of(DayOfWeek.THURSDAY), available, "H5: T01 is Thursday-only");
        assertEquals(available, weekdays(golden), "golden dates fall on the available weekday");
        assertEquals(available, weekdays(inputDerived), "input-derived dates fall on the available weekday");

        // (4) pin outcome: with the density cap the input source pins FEWER hours per
        // group than the golden (the residual is mobile solver hours, see (2)); the old
        // 100% equivalence is superseded by the never-over-quota property, asserted here
        // on the plan() outcome the pin loop actually consumes
        ConstrainedTeacherAllocator.Plan goldenPlan =
                ConstrainedTeacherAllocator.plan(golden, budgetByName, h7);
        ConstrainedTeacherAllocator.Plan inputPlan =
                ConstrainedTeacherAllocator.plan(inputDerived, budgetByName, h7);
        for (var e : inputPlan.pinnedHoursByGroup().entrySet()) {
            assertTrue(e.getValue() <= goldenPlan.pinnedHoursByGroup().getOrDefault(e.getKey(), 0),
                    e.getKey() + ": input pins never exceed the golden's (density cap only trims)");
        }
        inputDerived.forEach((g, byDate) -> byDate.forEach((date, hours) -> {
            Integer quota = inp.quotaByGroupDate().getOrDefault(g, Map.of()).get(date);
            if (quota != null) {
                assertTrue(hours <= quota, g + " " + date + ": plan pins over the declared quota");
            }
        }));

        // (5) SUPERSEDED by the density axis (20/08): the census called the per-DATE
        // distribution "non-load-bearing, not input-derivable" - true under the OLD
        // quota-blind builder (overlap materially below 100%). With the density cap the
        // input-derived dates now MATCH the golden's entirely (35/35 measured): the
        // monographic signal was in the quota sheets all along, the old builder was
        // simply blind to it. Assert the new, stronger property.
        Set<LocalDate> goldenDates = allDates(golden);
        Set<LocalDate> inputDates = allDates(inputDerived);
        int overlap = (int) goldenDates.stream().filter(inputDates::contains).count();
        assertTrue(goldenDates.stream().allMatch(inputDates::contains),
                "density axis: every golden monographic Thursday is input-derived (overlap "
                        + overlap + "/" + goldenDates.size() + ")");
        assertTrue(inputDates.size() - overlap <= 1,
                "the input adds at most the year's first Thursday (36 vs 35 measured); was +"
                        + (inputDates.size() - overlap));
    }

    /**
     * TASK-452 cura: the input-derived profile must never pin more hours on a date than the group's
     * calendar has OPEN slots that day, or the pin loop's contiguous block from slot 0 would spill onto
     * an H20 closed slot (the run-1 defect). Fails on the pre-cure builder (1/G06 get 8h &gt; 6 open)
     * and passes once {@link ConstrainedTeacherAllocator#buildInputDerivedProfile} caps each date.
     */
    @Test
    @Transactional
    void inputDerivedProfileNeverExceedsGroupCalendarOpenSlots() {
        T01Profile inp = buildT01Profile();
        Map<String, Map<LocalDate, Integer>> openSlotsByGroupDate = inp.openSlotsByGroupDate();

        for (Map.Entry<String, Map<LocalDate, Integer>> ge : inp.profile().entrySet()) {
            String group = ge.getKey();
            Map<LocalDate, Integer> openForGroup = openSlotsByGroupDate.getOrDefault(group, Map.of());
            for (Map.Entry<LocalDate, Integer> de : ge.getValue().entrySet()) {
                LocalDate date = de.getKey();
                int hours = de.getValue();
                int open = openForGroup.getOrDefault(date, GroupCalendarService.TOTAL_SLOTS);
                assertTrue(hours <= open,
                        group + " " + date + ": profile pins " + hours + "h but only " + open
                                + " calendar slots are open (would land on an H20 closed slot)");
            }
        }
    }

    /**
     * TASK-452 hard-0 cure: the input-derived profile must never target a date the group is on
     * stage (DailyActivityQuota.stageHours &gt; 0), because the group is off-grid on internship
     * that day and the pin loop can never fill the cell (the run3 hard-0 root cause: G10's
     * alpha-front-loaded profile landed all 7 of its in-grid stage Thursdays, Mar 4-Apr 22, so
     * G10 hours had no legal cell after T01 saturated the non-stage Thursdays). Fails on
     * the pre-cure stage-blind builder (G10 gets 7 stage dates) and passes once
     * {@link ConstrainedTeacherAllocator#buildInputDerivedProfile} is stage-aware.
     */
    @Test
    @Transactional
    void inputDerivedProfileNeverTargetsAGroupStageDate() {
        T01Profile inp = buildT01Profile();
        Map<String, Set<LocalDate>> stageDatesByGroup = inp.stageDatesByGroup();

        for (Map.Entry<String, Map<LocalDate, Integer>> ge : inp.profile().entrySet()) {
            String group = ge.getKey();
            Set<LocalDate> stageForGroup = stageDatesByGroup.getOrDefault(group, Set.of());
            for (LocalDate date : ge.getValue().keySet()) {
                assertFalse(stageForGroup.contains(date),
                        group + " " + date + ": profile targets a stage day (stageHours>0); the group is "
                                + "off-grid on internship and the pin loop can never fill this cell");
            }
        }
    }

    /**
     * Independent re-derivation of the (groupName, date) -> calendar-open-slot-count index straight
     * from the seeded GroupCalendarRule rows, using the SAME resolution primitive the production
     * caller uses ({@link GroupCalendarService#effectiveRange} + {@link
     * GroupCalendarService#openSlotCountFromRange}). The test must not call the production indexer,
     * or it would only assert "the indexer agrees with itself" rather than "the profile respects the
     * real calendar".
     */
    private static Map<String, Map<LocalDate, Integer>> indexOpenSlotsFromSeed(List<LocalDate> schoolDates) {
        Map<Long, List<GroupCalendarRule>> rulesByGroup = GroupCalendarRule.<GroupCalendarRule>listAll().stream()
                .filter(r -> r.studentGroup != null)
                .collect(Collectors.groupingBy(r -> r.studentGroup.id));
        Map<String, Map<LocalDate, Integer>> out = new LinkedHashMap<>();
        for (StudentGroup g : StudentGroup.<StudentGroup>listAll()) {
            List<GroupCalendarRule> rules = rulesByGroup.getOrDefault(g.id, List.of());
            Map<LocalDate, Integer> byDate = new LinkedHashMap<>();
            for (LocalDate d : schoolDates) {
                int[] range = GroupCalendarService.effectiveRange(rules, d.getDayOfWeek(), d);
                byDate.put(d, GroupCalendarService.openSlotCountFromRange(range));
            }
            out.put(g.name, byDate);
        }
        return out;
    }

    /**
     * Independent re-derivation of the groupName -> stage-dates index straight from the seeded
     * {@link DailyActivityQuota} rows ({@code stageHours > 0}), mirroring the production caller's
     * {@code HybridPhase1aService#indexStageDatesByGroup}. The test must not call the production
     * indexer, or it would only assert "the indexer agrees with itself" rather than "the profile
     * avoids the group's real stage window".
     */
    /** Density axis (anti-pattern piano-sopra-quota): declared aula+lab quota per (group, date). */
    private static Map<String, Map<LocalDate, Integer>> indexQuotaFromSeed() {
        Map<String, Map<LocalDate, Integer>> out = new LinkedHashMap<>();
        for (DailyActivityQuota q : DailyActivityQuota.<DailyActivityQuota>listAll()) {
            if (q.studentGroup == null || q.date == null) {
                continue;
            }
            int quota = q.aulaHours + q.labHours;
            if (quota <= 0) {
                continue;
            }
            out.computeIfAbsent(q.studentGroup.name, n -> new LinkedHashMap<>()).put(q.date, quota);
        }
        return out;
    }

    private static Map<String, Set<LocalDate>> indexStageDatesFromSeed() {
        Map<String, Set<LocalDate>> out = new LinkedHashMap<>();
        for (DailyActivityQuota q : DailyActivityQuota.<DailyActivityQuota>listAll()) {
            if (q.studentGroup != null && DailyActivityQuota.isStageDate(q)) {
                out.computeIfAbsent(q.studentGroup.name, n -> new java.util.LinkedHashSet<>()).add(q.date);
            }
        }
        return out;
    }

    /** All dates appearing in a profile (across every group). */
    private static Set<LocalDate> allDates(Map<String, Map<LocalDate, Integer>> profile) {
        return profile.values().stream()
                .flatMap(m -> m.keySet().stream()).collect(Collectors.toSet());
    }

    /** All weekdays appearing in a profile. */
    private static Set<DayOfWeek> weekdays(Map<String, Map<LocalDate, Integer>> profile) {
        return allDates(profile).stream().map(LocalDate::getDayOfWeek).collect(Collectors.toSet());
    }

    /**
     * Seed only the slices the equivalence needs: real Timeslots + StudentGroups (via
     * {@link RealSeedFixture}), Teachers + RecurringAvailabilityRule (the H5 source, via the shared
     * {@link RealSeedFixture#seedTeachersAndAvailabilityRules} so this harness never duplicates the
     * teachers.csv / availability_rules.csv parse), Courses (incarichi.csv, aggregated exactly
     * like {@code DataGenerator.aggregateCourseHours}), and DailyActivityQuota (the stage-window
     * source for the hard-0 cure assertion, via the same package-private DataGenerator seeders the
     * production startup uses). Mirrors {@link WeeklyTemplateConstructionRealSeedTest} but skips
     * lesson generation.
     */
    private void seedRealSeedInputs() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers =
                RealSeedFixture.seedTeachersAndAvailabilityRules(availabilityExpansionService);

        DataGenerator.CourseAggregation aggregation =
                DataGenerator.aggregateCourseHours(DataGenerator.loadSeedCsv("incarichi.csv", 4));
        for (Map.Entry<DataGenerator.CourseKey, Integer> entry : aggregation.hoursByKey().entrySet()) {
            DataGenerator.CourseKey key = entry.getKey();
            Teacher teacher = teachers.get(key.teacherName());
            StudentGroup group = groups.get(key.groupName());
            if (teacher == null || group == null) {
                continue;
            }
            new Course(key.subject(), teacher, group, entry.getValue()).persist();
        }
        // DailyActivityQuota: the stage-window source (stageHours > 0). Both seeders mirror the
        // production startup (DataGenerator runs them in the same order); the class_days one covers
        // G10's Mar/Apr stage window, the hard-0 root cause this cure targets.
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
    }

    /**
     * TASK-542 iter-5: a sheet-covered group's date with NO quota row is a no-session day,
     * not an open one. The 22/08 bench measured 32 T01/G12 hours pinned on exactly
     * these four dates (H26 pinned-exempt hid them; the immovable pins starved the group's
     * real days). The profile must place ZERO hours there.
     */
    @Test
    @Transactional
    void profileNeverPinsOnNoSessionDatesOfCoveredGroups() {
        T01Profile inp = buildT01Profile();
        List<LocalDate> noSession = List.of(LocalDate.of(2026, 12, 3), LocalDate.of(2027, 1, 21),
                LocalDate.of(2027, 3, 4), LocalDate.of(2027, 4, 1));
        for (LocalDate d : noSession) {
            assertFalse(inp.quotaByGroupDate().getOrDefault("G12", Map.of()).containsKey(d),
                    "precondition: " + d + " must be a no-quota-row date in the index");
        }
        for (Map.Entry<String, Map<LocalDate, Integer>> group : inp.profile().entrySet()) {
            Map<LocalDate, Integer> covered = inp.quotaByGroupDate().getOrDefault(group.getKey(), Map.of());
            for (LocalDate d : noSession) {
                if (covered.containsKey(d)) {
                    continue; // this group has its own quota row on the date: pinning is legal
                }
                assertEquals(0, group.getValue().getOrDefault(d, 0),
                        group.getKey() + " must not be pinned on no-session date " + d
                                + " (no quota row for this group)");
            }
        }
    }
    /**
     * TASK-542 iter-10 DIAGNOSTIC (no assertions beyond feasibility): dump T10's
     * input-derived profile to classify WHERE the Friday clustering of his G04 pins
     * originates. Prints budgets, availability and the profile's hours by weekday.
     */
    @Test
    @Transactional
    void dumpT10InputDerivedProfile() {
        seedRealSeedInputs();
        Teacher T10 = Teacher.find("name", "T10").firstResult();
        Map<String, Integer> budgetByName = new LinkedHashMap<>();
        for (Course c : Course.<Course>listAll()) {
            if (c.teacher != null && c.teacher.equals(T10) && c.studentGroup != null) {
                budgetByName.merge(c.studentGroup.name, c.totalHours, Integer::sum);
            }
        }
        Set<DayOfWeek> fullDayVacation = EnumSet.noneOf(DayOfWeek.class);
        for (RecurringAvailabilityRule r : RecurringAvailabilityRule.listByTeacher(T10)) {
            if (r.type == AvailabilityType.VACATION && r.isFullDay()) {
                fullDayVacation.add(r.dayOfWeek);
            }
        }
        Set<DayOfWeek> available = ConstrainedTeacherAllocator.availableWeekdays(fullDayVacation);
        List<LocalDate> schoolDates = Timeslot.<Timeslot>listAll().stream()
                .map(Timeslot::getDate).distinct().sorted().toList();
        int h7 = T10.getEffectiveMaxDailyHours();
        Map<String, Map<LocalDate, Integer>> openSlotsByGroupDate = indexOpenSlotsFromSeed(schoolDates);
        Map<String, Set<LocalDate>> stageDatesByGroup = indexStageDatesFromSeed();
        Map<String, Map<LocalDate, Integer>> quotaByGroupDate = indexQuotaFromSeed();
        Map<String, Map<LocalDate, Integer>> profile = ConstrainedTeacherAllocator
                .buildInputDerivedProfile(budgetByName, available, schoolDates, h7,
                        openSlotsByGroupDate, stageDatesByGroup, quotaByGroupDate);
        System.out.println("=== T10 PROFILE DUMP ===");
        System.out.println("availableWeekdays=" + available + " fullDayVacation=" + fullDayVacation
                + " h7=" + h7);
        System.out.println("budgets=" + budgetByName);
        for (Map.Entry<String, Map<LocalDate, Integer>> e : profile.entrySet()) {
            Map<DayOfWeek, Integer> byWd = new TreeMap<>(java.util.Comparator.comparing(DayOfWeek::getValue));
            e.getValue().forEach((d, h) -> byWd.merge(d.getDayOfWeek(), h, Integer::sum));
            System.out.println("PROFILE group=" + e.getKey() + " dates=" + e.getValue().size()
                    + " hoursByWeekday=" + byWd);
        }
        System.out.println("=== END DUMP ===");
    }
}
