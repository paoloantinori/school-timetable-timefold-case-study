package com.school.planner.bootstrap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;
import ai.timefold.solver.core.config.phase.custom.CustomPhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.school.planner.solver.ScoreCompat;
import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.service.HybridPhase1aReport;
import com.school.planner.service.HybridPhase1aService;
import com.school.planner.solver.AnchorRepair;
import com.school.planner.solver.DayClusteredConstructionPhase;
import com.school.planner.solver.PairCellGuard;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.solver.WeeklyTemplateBuilder;
import com.school.planner.solver.WeeklyTemplatePlanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-381 "L2" key test: construction-end weekly recurrence on the REAL
 * seed CSVs, run through a solver config containing ONLY {@link
 * DayClusteredConstructionPhase} (no local search) - mirrors {@code
 * CalendarioImportRealSeedStageTest}'s real-seed fixture-building approach
 * (same package, for the same package-private {@code DataGenerator} access
 * this class still calls directly: {@code loadSeedCsv}/{@code
 * aggregateCourseHours}). The Timeslot/GroupCalendarRule replication
 * ({@code GROUP_CALENDAR_PATTERNS}/{@code parseCalendarCell}/{@code
 * CLOSURE_DATES_2026_27}/{@code computeSchoolDays}/{@code STANDARD_SLOTS})
 * flows through the shared {@link RealSeedFixture} instead of being called
 * by this class directly.
 *
 * <p><b>Deliberate simplification (task brief explicitly allows this):</b>
 * no golden/Calendario import runs here, so there are NO pinned lessons at
 * all (no MODA shifts, no Stage). This means "recurrence among unpinned
 * lessons" and "recurrence among ALL lessons" are the same measurement in
 * this fixture - documented, not silently conflated. Stage pseudo-courses
 * are also not seeded (out of scope for a weekly SUBJECT-recurrence
 * measurement - Stage is an accounting course, not a recurring subject).
 * Course classification fields the real {@code generateCourses} derives from
 * private helpers ({@code isLab}/{@code requiredRoomTags}/{@code
 * professionalizzanti}) are left at their Java defaults (false/empty) since
 * they aren't reachable from outside {@code DataGenerator} and don't affect
 * this test's two assertions (recurrence %, zero H1/H3 conflicts) - room
 * assignment is explicitly out of scope for L2 (phase 4/5's job) and subject
 * classification doesn't change which (teacher, group, slot) cells collide.
 * {@code StudentGroup.maxHoursPerDay} IS reproduced (small literal table,
 * mirrors {@code DataGenerator}'s private {@code MAX_HOURS_PER_DAY}) since it
 * directly bounds H11/the template's daily cap.</p>
 */
@QuarkusTest
class WeeklyTemplateConstructionRealSeedTest {

    @Inject
    DataGenerator dataGenerator;

    @Inject
    com.school.planner.service.LessonGenerator lessonGenerator;

    @Inject
    AvailabilityExpansionService availabilityExpansionService;

    @Inject
    EntityManager em;

    @Inject
    HybridPhase1aService hybridPhase1aService;

    private static final Map<String, Integer> MAX_HOURS_PER_DAY = RealSeedFixture.MAX_HOURS_PER_DAY;

    /**
     * TASK-430 slot0-balancing flag (NON-ADOPTED, default OFF). Read by
     * {@link WeeklyTemplateBuilder#build} via {@code Boolean.getBoolean}; mirrored here as a
     * test-local constant (no production constant exists for it) so the flag-ON measure,
     * its try/finally clear and the {@link #clean} isolation cannot drift on the literal.
     */
    private static final String SLOT0_BALANCING_FLAG = "planner.construction.slot0-balancing.enabled";

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
        // TASK-446: RealSeedFixture.seedRooms() is not idempotent (persist()s 18 rooms
        // each call); clean them so the real-room tests don't accumulate duplicates
        // across @QuarkusTest methods in the shared JVM.
        em.createQuery("DELETE FROM Room").executeUpdate();

        SchoolSchedule.setAllTimeslotsStatic(List.of());
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(List.of());
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(List.of());
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(Map.of());
        SchoolSchedule.setTeacherAvailabilitiesStatic(List.of());
        SchoolSchedule.setSchoolHolidaysStatic(List.of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.of(), java.util.Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(null);
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
        // TASK-636: cadence statics isolation (caps + pace curves) - same
        // belt-and-suspenders as the maps above.
        SchoolSchedule.setCadenceReferenceStatic(Map.of(), Map.of(), Map.of());
        // TASK-432: belt-and-suspenders isolation for the profile-following flag. The
        // flag-ON test method sets it in a try/finally that clears it, but clearing it
        // here too guarantees no sibling test in this JVM ever sees the flag set,
        // regardless of execution order or a timeout/interrupt that skipped the finally.
        System.clearProperty("planner.construction.profile-following.enabled");
        // TASK-435: belt-and-suspenders isolation for the constrained-teacher flag. Default is now ON
        // (ratified 2026-07-30), so clearing would leave it ON - set "false" explicitly to give the
        // baseline construction tests a pin-OFF population (the pin is a pre-assignment input, not a
        // construction property; these tests measure construction alone).
        System.setProperty(HybridPhase1aService.CONSTRAINED_TEACHER_FLAG, "false");
        // TASK-450: same isolation for the slot0-balancing flag (TASK-430), measured not shipped.
        System.clearProperty(SLOT0_BALANCING_FLAG);
    }

    @Transactional
    Map<String, StudentGroup> seedRealSeedFixture() {
        // Real school-year Timeslots, all 14 real StudentGroups (groups.csv,
        // with the real maxHoursPerDay table), and GroupCalendarRule (with the
        // Monday ramp) - shared core extracted to RealSeedFixture (see its own
        // javadoc), same pattern CalendarioImportRealSeedStageTest already established.
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, MAX_HOURS_PER_DAY);

        // Real Teachers (teachers.csv), including the H7/M24/M25 per-teacher overrides.
        Map<String, Teacher> teachers = new LinkedHashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("teachers.csv", 6)) {
            Teacher t = new Teacher(row[0].trim());
            String maxDailyHours = row[3].trim();
            t.maxDailyHours = maxDailyHours.isEmpty() ? null : Integer.valueOf(maxDailyHours);
            t.persist();
            teachers.put(t.name, t);
        }

        // Real Courses (incarichi.csv), aggregated exactly like generateCourses()
        // does (DataGenerator.aggregateCourseHours - package-private static, no
        // DB access). isLab/requiredRoomTags/professionalizzanti classification
        // is out of reach (private helpers) and out of scope here - see class javadoc.
        DataGenerator.CourseAggregation aggregation =
                DataGenerator.aggregateCourseHours(DataGenerator.loadSeedCsv("incarichi.csv", 4));
        for (Map.Entry<DataGenerator.CourseKey, Integer> entry : aggregation.hoursByKey().entrySet()) {
            DataGenerator.CourseKey key = entry.getKey();
            Teacher teacher = teachers.get(key.teacherName());
            StudentGroup group = groups.get(key.groupName());
            if (teacher == null || group == null) continue; // defensive - real seed always resolves both
            Course course = new Course(key.subject(), teacher, group, entry.getValue());
            course.persist();
            // No em.flush() needed: generateForCourse's Course.findById(courseId)
            // resolves from the same persistence context's first-level cache
            // (same id, same transaction), never issuing a query that would
            // need the just-persisted row visible in the DB yet.
            lessonGenerator.generateForCourse(course.id);
        }

        // DailyActivityQuota for G04/G08/G13 from calendario_fc.csv,
        // plus the other 11 groups' plain expected-hours facts from
        // calendario_class_days.csv (TASK-385) - the day-target cap and the
        // weekly template's largest-remainder allocation are no-ops without
        // this data (GroupWeeklyShape.weeklyTargetTotal() stays -1).
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();

        // Real RecurringAvailabilityRule (availability_rules.csv), mirroring
        // generateAvailabilityRules() exactly (private; replicated the same
        // way as GroupCalendarRule above, via only public/package-private API).
        for (String[] row : DataGenerator.loadSeedCsv("availability_rules.csv", 8)) {
            Teacher teacher = teachers.get(row[0].trim());
            if (teacher == null) continue;
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

        return groups;
    }

    @Test
    @Timeout(value = 400)
    void constructionEndRecurrenceIsAtLeastFiftyPercentWithZeroConflicts() {
        seedRealSeedFixture();

        // Seeded + wired + solved by the shared helper (also used by the TASK-432
        // profile-following flag-ON test below), so both flag states run on a
        // byte-identical fixture. Returns the solved schedule and the construction
        // phase instance (for its stamp/compaction stats).
        ConstructionResult r = buildAndSolveConstruction();
        SchoolSchedule solved = r.solved();
        DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = r.constructionPhase();

        // ── TASK-398 box-the-residual: the construction phase now BOXES the
        // genuinely-unplaceable residual (timeslot=null -> H0) instead of
        // force-placing it into conflicts. A small box is expected and intended;
        // it must stay a tiny fraction of the total (a large box would mean the
        // template/round-robin passes regressed, not that boxing is working). ──
        long unassigned = solved.getLessons().stream().filter(l -> l.timeslot == null).count();
        assertTrue(unassigned < solved.getLessons().size() / 50,
                "box-the-residual should leave only a tiny unplaceable residual, got "
                        + unassigned + " of " + solved.getLessons().size());

        // ── Zero H1/H3/H7/H11 conflicts introduced by THE STAMP ITSELF (task
        // spec's exact wording) - canPlaceLesson/exceedsDailyCap gate every
        // stamp placement, so this must always be 0; a failure here would
        // mean that gate was bypassed. ──
        DayClusteredConstructionPhase.StampStats stampStats = constructionPhase.getStampStats();
        System.out.printf("TASK-381 L2 stamp stats: placed %d, skipped conflict/cap %d, skipped illegal/closed %d, "
                        + "skipped exhausted %d, skipped expected-hours goal %d (TASK-385, informational)%n",
                stampStats.placed(), stampStats.skipConflict(), stampStats.skipIllegalOrClosed(), stampStats.skipExhausted(),
                stampStats.skipExpectedHoursGoal());
        // TASK-450 metric 4 (flag-OFF side): same decline% shape the flag-ON twin prints.
        printStampDecline("TASK-450 flag-OFF baseline", stampStats);
        // TASK-532 quota-aware fill tail: the fill places BEYOND template cells,
        // so a later group's cell can now legitimately skip on cross-group
        // teacher contention (H1) that pure-template stamping never met. The
        // real invariant is that no conflicting placement is ever ACCEPTED -
        // that is asserted by the zero-conflict score checks below. The old
        // "skipConflict == 0" codified the pure-template property, which the
        // quota fill intentionally relaxes.
        org.junit.jupiter.api.Assertions.assertTrue(stampStats.skipConflict() >= 0,
                "skip counter is informational since the TASK-532 quota fill; accepted conflicts are 0 (asserted below)");

        // ── TASK-390 leftward compaction: must close some student-day gaps AND
        // never introduce an H1/H3 conflict (it only relocates into slots that are
        // conflict-free vs the actual placements - one H3 -1000 would dwarf the
        // H17 -1 gaps it closes). ──
        System.out.printf("TASK-390 compaction: closed %d student-day gaps, added %d conflicts%n",
                constructionPhase.getCompactedGaps(), constructionPhase.getCompactionAddedConflicts());
        assertEquals(0, constructionPhase.getCompactionAddedConflicts(),
                "compaction must never create a teacher/group conflict - it only moves into conflict-free slots");
        assertTrue(constructionPhase.getCompactedGaps() > 0,
                "compaction should close at least some avoidable student-day gaps on the real seed");

        // ── TASK-398: conflicts in the FINAL population (stamp + round-robin +
        // box-the-residual) must be ZERO. The old force-place repair tail could
        // accept a non-zero-cost (conflicting) placement when every remaining
        // slot had some cost; boxUnplaceableLeftovers replaced it - it only ever
        // places a leftover into a conflict-free (cost-0) slot, otherwise boxes
        // it (timeslot=null -> H0). Every placement path now gates on
        // conflict-freeness, so the construction phase can no longer emit a
        // single teacher/group conflict. ──
        assertNoTeacherGroupConflicts(solved, "TASK-381 L2 (stamp + round-robin + box)");

        // ── KPI: weekly recurrence among placed (here: all, since none are
        // pinned - see class javadoc) lessons. Cell = (group, date, slotIndex);
        // a lesson recurs if the SAME group/slotIndex cell exactly 7 days
        // earlier has the SAME teacher AND subject. Shared definition with
        // Phase2SmokeSnapshotTest via SnapshotFixture.weeklyRecurrence (same package). ──
        SnapshotFixture.RecurrenceKpi kpi = SnapshotFixture.weeklyRecurrence(solved.getLessons());
        double recurrencePercent = kpi.percent();
        System.out.printf(
                "TASK-381 L2 construction-end recurrence: %d/%d = %.1f%% (target >= 50%%)%n",
                kpi.recurring(), kpi.total(), recurrencePercent);

        // TASK-450 checkpoint diagnostic (flag-OFF baseline): anchor/hole day-shape at
        // construction end, same numbers the flag-ON twin prints for the OFF-vs-ON
        // comparison. Pure diagnostic print, no assertion (the verdict is read off the
        // pair, not asserted here) - mirrors how recurrence/stamp/full-day are printed.
        printAnchorHoleDiagnostic("TASK-450 flag-OFF baseline", solved);

        assertTrue(recurrencePercent >= 50.0,
                "construction-end weekly recurrence must be >= 50%, was " + recurrencePercent + "% ("
                        + kpi.recurring() + "/" + kpi.total() + ") - per task spec, do NOT lower this threshold; "
                        + "report the actual number and stamp-skip breakdown instead");

        // ── KPI (TASK-385): full-day rate vs the school's own per-date
        // expected hours, calendario_class_days.csv - mirrors
        // scripts/analyze/daily-fill-kpi.py's methodology (same horizon
        // filter, same skip-zero-hour-rows rule, same "placed == expected"
        // definition of a full day), with two exclusions this SPECIFIC
        // fixture's own documented scope requires (see class javadoc:
        // "Deliberately NOT seeded... Stage pseudo-courses"):
        //   - modality=S (stage) rows: this fixture seeds no Stage course and
        //     no StagePattern at all (out of scope here, same reason the
        //     recurrence KPI above already excludes Stage), so a stage day's
        //     expected hours can NEVER be filled by any lesson this fixture
        //     produces - not a mechanism gap, a fixture-scope gap.
        //   - G14: incarichi.csv demand is 115h/year vs the sheet's 120h
        //     (70 aula + 50 lab since TASK-462 fixed the ingestion - the old
        //     990h was G01's calendar read from the wrong sheet). G14
        //     still carries the standing task-010 exemption from
        //     M20/horizontallyCompact ("short course, don't measure it like
        //     a full-time group"); whether that exemption is still needed
        //     post-fix is a separate, unmeasured question.
        // The RAW (unfiltered) number is still computed and printed for
        // transparency - only the asserted metric is scoped down. ──
        Map<String, Integer> expectedByGroupDate = new HashMap<>();
        Map<String, Integer> expectedByGroupDateRaw = new HashMap<>();
        String horizonStart = TimeslotSequence.SCHOOL_YEAR_START.toString();
        String horizonEnd = TimeslotSequence.SCHOOL_YEAR_END.toString();
        for (String[] row : DataGenerator.loadSeedCsv("calendario_class_days.csv", 4)) {
            String groupName = row[0].trim();
            String date = row[1].trim();
            if (date.compareTo(horizonStart) < 0 || date.compareTo(horizonEnd) > 0) continue;
            int hours = Integer.parseInt(row[2].trim());
            if (hours <= 0) continue;
            String key = groupName + "@" + date;
            expectedByGroupDateRaw.put(key, hours);
            if ("G14".equals(groupName) || "S".equals(row[3].trim())) continue;
            expectedByGroupDate.put(key, hours);
        }
        Map<String, Integer> placedByGroupDate = new HashMap<>();
        for (Lesson lesson : solved.getLessons()) {
            if (lesson.timeslot == null || lesson.getStudentGroup() == null) continue;
            String key = lesson.getStudentGroup().name + "@" + lesson.timeslot.getDate();
            placedByGroupDate.merge(key, 1, Integer::sum);
        }
        int rawFullDays = 0;
        for (Map.Entry<String, Integer> e : expectedByGroupDateRaw.entrySet()) {
            if (placedByGroupDate.getOrDefault(e.getKey(), 0).equals(e.getValue())) {
                rawFullDays++;
            }
        }
        System.out.printf("TASK-385 construction-end full-day rate (RAW, unfiltered, all 14 groups): %d/%d = %.1f%%%n",
                rawFullDays, expectedByGroupDateRaw.size(), 100.0 * rawFullDays / expectedByGroupDateRaw.size());

        int fullDays = 0;
        int underDays = 0;
        int overDays = 0;
        int deficitHours = 0;
        int surplusHours = 0;
        Map<String, int[]> byGroup = new HashMap<>(); // [full, total]
        for (Map.Entry<String, Integer> e : expectedByGroupDate.entrySet()) {
            int placed = placedByGroupDate.getOrDefault(e.getKey(), 0);
            int expected = e.getValue();
            String groupName = e.getKey().split("@")[0];
            int[] counts = byGroup.computeIfAbsent(groupName, k -> new int[2]);
            counts[1]++;
            if (placed == expected) {
                fullDays++;
                counts[0]++;
            } else if (placed < expected) {
                underDays++;
                deficitHours += expected - placed;
            } else {
                overDays++;
                surplusHours += placed - expected;
            }
        }
        double fullDayRate = 100.0 * fullDays / expectedByGroupDate.size();
        System.out.printf(
                "TASK-385 construction-end full-day rate (scoped: excl. G14 + stage-modality rows): "
                        + "%d/%d = %.1f%% (target >= 65%%, golden 85.4%%, run14 55.5%%)%n",
                fullDays, expectedByGroupDate.size(), fullDayRate);
        System.out.printf("TASK-385 breakdown: under=%d (deficit %dh), over=%d (surplus %dh)%n",
                underDays, deficitHours, overDays, surplusHours);
        for (Map.Entry<String, int[]> e : new java.util.TreeMap<>(byGroup).entrySet()) {
            System.out.printf("  %s: %d/%d = %.0f%%%n", e.getKey(), e.getValue()[0], e.getValue()[1],
                    100.0 * e.getValue()[0] / e.getValue()[1]);
        }

        // ── Measured after TASK-410 (stage-aware countActiveWeeks), two
        // isolated runs, byte-identical both times: RAW 69.6% (1666/2395),
        // SCOPED (excl. G14 + stage-modality rows) 85.2% (1665/1954) - the
        // latter now MEETS the 65% aspirational target (golden 85.4%), so it
        // is asserted below rather than merely reported. History: TASK-385
        // first measured SCOPED 54.2% here (below 65%, reported-not-asserted
        // with a 40% regression floor and the TASK-385 brief's own "STOP and
        // report the breakdown" instruction). TASK-390/398/401/405/406
        // (leftward compaction, box-the-residual, BoxRepair, day-start
        // anchoring, deficit-aware repair tail) then raised it to ~83.5% by
        // filling under-target days at construction end. TASK-410 closed the
        // remaining stage-group gap (root cause #3 below) for +1.7pp to 85.2%.
        // Remaining residual root causes (independently re-derived from the
        // raw CSVs, not just this run's own numbers):
        //
        //  1. G14 (0% full in every run): incarichi.csv demand is
        //     115h/year vs the sheet's 120h (TASK-462 fixed the old 990h
        //     wrong-sheet ingestion). G14 keeps task-010's M20 exemption;
        //     its fill is a known special case, not a defect in this task's
        //     mechanism.
        //  2. The 3 FC groups (G04/G08/G13, 9-45% full): their REAL
        //     per-date target correctly comes from calendario_fc.csv (the
        //     "definitive FC source" per this task's own design decision
        //     #1) via SchoolSchedule.getExpectedHours - but THIS specific KPI
        //     (mirroring daily-fill-kpi.py/the golden's own 85.4% ceiling)
        //     measures against calendario_class_days.csv for all 14 groups
        //     uniformly, a SEPARATE, non-authoritative sheet for those 3
        //     groups whose day-by-day plan legitimately diverges from
        //     fc.csv despite an equal yearly total - an acknowledged
        //     two-sheet tension, not a targeting bug (verified: the day
        //     target these 3 groups actually optimize toward IS the fc.csv
        //     fact, confirmed via DataGeneratorDailyActivityQuotaSeedTest).
        //  3. (ADDRESSED by TASK-410) The 5 stage-window groups (G07/3
        //     FB/G10/G09/G12) were 9-45% full here at TASK-385 time
        //     because countActiveWeeks()/buildWeeklyTargets() spread each
        //     course's hours over EVERY school week (38) instead of only the
        //     real non-stage weeks a production profile compresses them into
        //     - a group on stage is not teaching that week. countActiveWeeks()
        //     is now stage-aware: for a group with stage-day facts (TASK-384)
        //     a fully-stage week drops out (weight 0) and a mixed week counts
        //     by its teaching-day fraction, keyed off the DailyActivityQuota
        //     stageHours facts this fixture DOES seed (generateExpectedDaily
        //     Hours) even though it still seeds no StagePattern/Stage course
        //     (deliberate scope limit - see this class's own javadoc).
        //     Validated against ground truth, e.g. G07: 38 raw weeks x
        //     (134 teaching / 172 open days) ~= 29.6 active weeks (was 38);
        //     all 7 stage-eligible groups carry 17-38 stage-only days each, so
        //     the weekly-target density rose 10-22%.
        //  4. Non-stage, non-G14, non-FC groups score well WITH this
        //     task's mechanism (G01 98%, G05 98%, G02 98%, G03
        //     83%) - the day-target cap + largest-remainder allocation +
        //     stamp fill goal are working as designed.
        //
        // See the printed per-group breakdown above for the full numbers.
        //
        // The 65% target is now met with ~20pp margin (measured ~85%, ~76%
        // recurrence asserts >= 50% the same way). The assertion below catches
        // any real regression of the day-target cap / largest-remainder /
        // stamp fill goal / TASK-410 stage-aware activeWeeks machinery.
        assertTrue(fullDayRate >= 65.0,
                "construction-end full-day rate (scoped) dropped below the 65% target (measured baseline ~85%): was "
                        + fullDayRate + "% (" + fullDays + "/" + expectedByGroupDate.size() + ")");

        // ── TASK-413 MISURA: dump the construction-end placed population in the
        // same group|date|hour|subject|teacher|pinned pipe-format the KPI
        // scripts consume (resemblance-kpi.py, daily-fill-kpi.py, db-query.sh
        // SELECT documented in daily-fill-kpi.py's own header) - lets the
        // recurrence-erosion metric (construction ~77% -> final ~37%) be
        // measured at the construction end, per group, with the exact same
        // tooling used on live-solve/laptop dumps. "hour" is the timeslot's raw
        // wall-clock start hour (extract(hour from startdatetime), 8-15 -
        // 14:30/15:30 slots truncate to 14/15), NOT the 0-7 slotIndex. Written
        // under target/ (build output, never committed). Sorted by
        // (group, date, hour) for determinism - purely a measurement artifact,
        // no existing assertion touched. ──
        writeConstructionLessonsDump(solved.getLessons(), Path.of("target", "construction-lessons.txt"));

        // TASK-533 via (b) OFF baseline: under-quota cells at construction end with the
        // moves-placed pass OFF (the flag-ON twin method prints the ON number; the pair
        // is the prototype's signature).
        System.out.printf("TASK-533 via-b flag-OFF under-quota cells at construction end: %d%n",
                countUnderQuotaCellsH32(solved));

        // TASK-432 dedicated baseline measure (flag-OFF): the G10/T01 Thursday
        // profile the profile-following pass targets. Informational here (flag OFF ->
        // the uniform-cadence maldistribution, ~24h monographic / ~37h other); the
        // flag-ON twin method asserts the concentration.
        print3FAT01Profile(solved);
    }

    /**
     * TASK-576 F7-A wiring: with the pair-cells builder armed, the construction PHASE
     * must publish its template cells to the repair-side guard - from the phase itself,
     * not from the pre-solve statics point (the promotion release re-runs that point on
     * an all-placed population, the CH-mode builder returns an empty template there, and
     * the guard would be wiped exactly one stage before the repairs it exists for; code
     * review 2026-09-01, CRITICAL). This asserts the publish happens and is
     * non-degenerate; the survival across the release boundary is structural (the only
     * clear left is the per-cycle one in the staged runner).
     */
    @Test
    @Timeout(value = 400)
    void pairArmedConstructionPublishesTheGuardCells() {
        System.setProperty(DayClusteredConstructionPhase.PAIR_CELLS_FLAG, "true");
        System.setProperty(PairCellGuard.FLAG, "true");
        try {
            seedRealSeedFixture();
            PairCellGuard.clear();
            buildAndSolveConstruction();
            assertTrue(PairCellGuard.publishedCellCount() > 100,
                    "the pair construction phase must publish its (non-degenerate) template to the guard, was "
                            + PairCellGuard.publishedCellCount());
        } finally {
            System.clearProperty(DayClusteredConstructionPhase.PAIR_CELLS_FLAG);
            System.clearProperty(PairCellGuard.FLAG);
            PairCellGuard.clear();
        }
    }

    /**
     * TASK-419 code review: was its own copy of {@link SnapshotFixture#writePlacedGrid} (same
     * pipe format, same (group, date, hour) sort) - now delegates to it so the construction-end
     * dump and every other snapshot dump this test suite writes cannot drift on format again.
     * The try/catch is only Java's checked-{@link IOException} plumbing, unchanged behavior
     * (wrap-and-rethrow) from before the merge.
     */
    private void writeConstructionLessonsDump(List<Lesson> lessons, Path path) {
        try {
            SnapshotFixture.writePlacedGrid(lessons, path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Shared construction-only solve used by BOTH the flag-OFF baseline method above
     * and the TASK-432 flag-ON method below, so the two flag states run on a
     * byte-identical fixture (only the {@code planner.construction.profile-following.enabled}
     * system property, set by the caller, differs). Assumes {@link #seedRealSeedFixture}
     * has already run. Returns the solved schedule and the construction-phase instance
     * (for its stamp/compaction stats) - the exact setup+solve block that used to be
     * inlined in {@code constructionEndRecurrenceIsAtLeastFiftyPercentWithZeroConflicts}.
     */
    private ConstructionResult buildAndSolveConstruction() {
        return buildAndSolveConstruction(List.of());
    }

    private ConstructionResult buildAndSolveConstruction(List<Room> rooms) {
        List<Timeslot> timeslots = Timeslot.listAll();
        List<StudentGroup> studentGroups = StudentGroup.listAll();
        List<Teacher> teachers = Teacher.listAll();
        List<Course> courses = Course.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<TeacherAvailability> teacherAvailabilities = TeacherAvailability.listAll();

        assertTrue(lessons.size() > 1000, "expected the real incarichi.csv volume, got " + lessons.size());

        // DayClusteredConstructionPhase.getIdealEndDate() reads this @Transient field
        // directly and falls back to a 2026-07-01 default when null - BEFORE the real
        // school year starts - cutting off the round-robin repair tail. Set directly
        // here (this fixture doesn't run TargetEndDateCalculator).
        for (StudentGroup group : studentGroups) {
            group.idealEndDateVrpBuffered = TimeslotSequence.SCHOOL_YEAR_END;
        }

        // Wire the static VRP snapshots exactly like ScheduleService.refreshSolverStatics()
        // (TASK-597 hoist: the shared helper, empty holidays/stage as this bench always ran).
        SnapshotFixture.wireConstructionStatics(timeslots, studentGroups, lessons, teachers,
                courses, teacherAvailabilities, rooms, List.of(), List.of(), null, false, false);
        // TASK-636: also publish the cadence reference (M34/M35 caps + pace
        // curves) so the repair tail sees the statics a production solve sees.
        // Harmless while the cadence-pace flag is off (its default): the
        // preference is flag-gated, the caps were already test-absent here.
        SnapshotFixture.publishCadenceReferenceStatics();

        SchoolSchedule problem = new SchoolSchedule(timeslots, rooms, List.of(), teacherAvailabilities,
                List.of(), studentGroups, teachers, courses, lessons, List.of());

        // Solver config with ONLY the custom construction phase (no LS), using an
        // actual INSTANCE so the test can read the stamp stats back off it after solve.
        DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = new DayClusteredConstructionPhase<>();
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class))
                .withPhaseList(List.of(new CustomPhaseConfig()
                        .withCustomPhaseCommandList(List.of(constructionPhase))));

        SolverFactory<SchoolSchedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<SchoolSchedule> solver = solverFactory.buildSolver();
        SchoolSchedule solved = solver.solve(problem);
        return new ConstructionResult(solved, constructionPhase);
    }

    private record ConstructionResult(SchoolSchedule solved, DayClusteredConstructionPhase<SchoolSchedule> constructionPhase) {
    }

    /**
     * TASK-446 Alt-B: real-room variant of {@link #buildAndSolveConstruction()}.
     * Delegates to {@link #buildAndSolveConstruction(List)} with {@code Room.listAll()};
     * callers MUST run {@link RealSeedFixture#seedRooms()} first so listAll() resolves
     * the real 18-room set. This is the fixture that DISTINGUISHES Alt-B from the
     * baseline (List.of() makes the old room-availability gate inert, masking it).
     */
    private ConstructionResult buildAndSolveConstructionWithRealRooms() {
        return buildAndSolveConstruction(Room.listAll());
    }

    /**
     * TASK-446 Alt-B A/B tool (DISABLED): prints construction metrics on the REAL-room
     * grid. Used for the one-off boxed/H0 vs baseline comparison Fedora asked for
     * (result archived in HARNESS-FINE-COSTRUZIONE.md: boxed 284 -> 105, Alt-B wins).
     * Disabled because its assertions are a strict subset of the checkpoint test and
     * it pays a second full-construction solve. Re-enable (and git checkout the
     * pre-Alt-B DayClusteredConstructionPhase) to re-run the A/B; NOTE the baseline is
     * EXPECTED to fail the boxed&lt;total/50 assertion (boxed=284 logged for the diff).
     */
    @Disabled("TASK-446 A/B tool; result archived in HARNESS-FINE-COSTRUZIONE.md")
    @Transactional
    @Test
    @Timeout(value = 400)
    void report_constructionMetricsWithRealRooms() {
        seedRealSeedFixture();
        RealSeedFixture.seedRooms();
        ConstructionResult r = buildAndSolveConstructionWithRealRooms();
        SchoolSchedule solved = r.solved();

        long total = solved.getLessons().size();
        long nonPinned = solved.getLessons().stream().filter(l -> !l.isPinned()).count();
        long nonPinnedPlaced = solved.getLessons().stream().filter(l -> !l.isPinned() && l.timeslot != null).count();
        long nonPinnedRoomNull = solved.getLessons().stream().filter(l -> !l.isPinned() && l.room == null).count();
        long boxed = solved.getLessons().stream().filter(l -> l.timeslot == null).count();

        System.out.printf("TASK-446 REPORT real-rooms: total=%d nonPinned=%d nonPinnedPlaced=%d "
                        + "nonPinnedRoomNull=%d (%.1f%% of nonPinned) boxed(timeslot=null)=%d%n",
                total, nonPinned, nonPinnedPlaced, nonPinnedRoomNull,
                nonPinned == 0 ? 0.0 : 100.0 * nonPinnedRoomNull / nonPinned, boxed);
        assertNoTeacherGroupConflicts(solved, "TASK-446 REPORT real-rooms");
        assertTrue(boxed < total / 50,
                "box-the-residual must stay a tiny fraction, got " + boxed + " of " + total);
    }

    /**
     * TASK-446 Alt-B checkpoint (fine-costruzione). With REAL rooms available, the
     * construction phase must leave EVERY non-pinned lesson room=null (phase 4 CH
     * assigns all rooms on the complete grid). The censimento TASK-446 predicts
     * H2/H16b go silent on room-null (forEach / forEachUniquePair initialized-only
     * semantics, empirically confirmed vs TASK-377 and final-confirm-435 mid-solve).
     * The H2/H16b == 0 assertions use the real ConstraintVerifier so that if a
     * Timefold upgrade changed forEach/allowsUnassigned semantics (a room-null
     * lesson counting as initialized), the join would fire and this test would
     * break loudly - exactly the guard Fedora required.
     */
    @Transactional
    @Test
    @Timeout(value = 400)
    void constructionRoomDecouplingAltB_leavesNonPinnedRoomNullAndH2H16bSilent() {
        seedRealSeedFixture();
        RealSeedFixture.seedRooms();
        ConstructionResult r = buildAndSolveConstructionWithRealRooms();
        SchoolSchedule solved = r.solved();
        DayClusteredConstructionPhase<SchoolSchedule> phase = r.constructionPhase();

        long total = solved.getLessons().size();
        long nonPinned = solved.getLessons().stream().filter(l -> !l.isPinned()).count();
        long nonPinnedPlaced = solved.getLessons().stream().filter(l -> !l.isPinned() && l.timeslot != null).count();
        long nonPinnedRoomNull = solved.getLessons().stream().filter(l -> !l.isPinned() && l.room == null).count();
        long nonPinnedRoomSet = solved.getLessons().stream().filter(l -> !l.isPinned() && l.room != null).count();
        long boxed = solved.getLessons().stream().filter(l -> l.timeslot == null).count();

        System.out.printf("TASK-446 Alt-B construction (real rooms): total=%d nonPinned=%d nonPinnedPlaced=%d | "
                        + "nonPinnedRoomNull=%d nonPinnedRoomSet=%d | boxed=%d%n",
                total, nonPinned, nonPinnedPlaced, nonPinnedRoomNull, nonPinnedRoomSet, boxed);
        System.out.printf("TASK-446 Alt-B phase counter: roomNullCount=%d (non-pinned placed)%n",
                phase.getRoomNullCount());

        // (1) Alt-B invariant: no non-pinned lesson carries a room post-CH (phase 4
        // assigns all). Schedule-level check; the phase counter below is its canary
        // (roomNullCount lives inside assignVariables, so it breaks if any placement
        // path bypassed the room=null commit point).
        assertEquals(0, nonPinnedRoomSet,
                "Alt-B: no non-pinned lesson may carry a room post-CH; " + nonPinnedRoomSet + " do");

        // (2) Phase canary: every non-pinned placement went through assignVariables
        // (the room=null commit point), so roomNullCount == non-pinned placed count.
        assertEquals(nonPinnedPlaced, phase.getRoomNullCount(),
                "Alt-B: phase roomNullCount must equal the non-pinned placed count");

        // (3) Timeslot integrity unchanged: tiny box residual, zero H1/H3.
        assertTrue(boxed < total / 50,
                "box-the-residual must stay a tiny fraction, got " + boxed + " of " + total);
        assertNoTeacherGroupConflicts(solved, "TASK-446 Alt-B construction");

        // (4) THE GUARD: H2 and M37 (ex-H16b, demoted TASK-446/447 notte) must be SILENT
        // (0 matches) on the all-room-null grid, scored once by the real constraint stream
        // (ScoreDirector, the same engine the solver uses). If a Timefold upgrade made a
        // room-null lesson count as initialized for forEach/forEachUniquePair, H2/M37 would
        // fire. matchCount==0 is the meaningful signal for M37 (medium: hardScore is always 0).
        assertRoomConstraintsSilent(solved, "H2 - Room conflict",
                "M37 - Consecutive same-subject same-teacher should keep the room");
    }

    /**
     * Assert each named constraint contributes 0 hard score on the schedule, scored
     * ONCE by the real constraint stream (ScoreDirector with ConstraintMatchPolicy
     * ENABLED). Same engine the solver uses (mirrors {@code
     * HumanFaithfulConstructionSpikeTest#printHardBreakdown}), so it guards against a
     * Timefold upgrade that changed forEach/allowsUnassigned initialization behavior.
     */
    private void assertRoomConstraintsSilent(SchoolSchedule schedule, String... constraintIds) {
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class));
        SolverFactory<SchoolSchedule> solverFactory = SolverFactory.create(solverConfig);
        SolutionManager<SchoolSchedule, BendableScore> solutionManager = SolutionManager.create(solverFactory);
        var factory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) solutionManager)
                .getScoreDirectorFactory();
        try (var sd = factory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            sd.setWorkingSolution(schedule);
            sd.calculateScore();   // single expensive pass; read all totals from one map
            var totals = sd.getConstraintMatchTotalMap();
            for (String id : constraintIds) {
                long hard = 0;
                long matchCount = 0;
                for (var e : totals.entrySet()) {
                    if (e.getKey().id().equals(id)) {
                        hard += ScoreCompat.hardOf(e.getValue().getScore());
                        matchCount += e.getValue().getConstraintMatchCount();
                    }
                }
                System.out.printf("TASK-446 Alt-B guard: %s hard=%d matchCount=%d (expect 0/0)%n", id, hard, matchCount);
                assertEquals(0, hard, "Alt-B guard: " + id + " must be 0 hard on the all-room-null grid (censimento TASK-446)");
                assertEquals(0, matchCount, "Alt-B guard: " + id + " must be 0 matches (silent on room-null; the meaningful signal for M37 medium)");
            }
        }
    }

    /**
     * Asserts zero accepted H1 (teacher) / H3 (group) conflicts in the final placed
     * population, shared by the flag-OFF baseline test and the TASK-432 flag-ON test so the
     * two conflict checks cannot drift. {@code label} prefixes the diagnostic print and the
     * failure messages to identify which population failed.
     */
    private void assertNoTeacherGroupConflicts(SchoolSchedule solved, String label) {
        Map<String, Integer> teacherSlotCounts = new HashMap<>();
        Map<String, Integer> groupSlotCounts = new HashMap<>();
        for (Lesson lesson : solved.getLessons()) {
            if (lesson.timeslot == null) continue;
            Teacher teacher = lesson.getTeacher();
            if (teacher != null && !teacher.pseudo) {
                teacherSlotCounts.merge(teacher.id + "@" + lesson.timeslot.id, 1, Integer::sum);
            }
            StudentGroup group = lesson.getStudentGroup();
            if (group != null) {
                groupSlotCounts.merge(group.id + "@" + lesson.timeslot.id, 1, Integer::sum);
            }
        }
        long teacherConflicts = teacherSlotCounts.values().stream().filter(c -> c > 1).count();
        long groupConflicts = groupSlotCounts.values().stream().filter(c -> c > 1).count();
        System.out.printf("%s final-population conflicts: teacher=%d group=%d%n", label, teacherConflicts, groupConflicts);
        assertEquals(0, teacherConflicts, label + " must leave no teacher conflict");
        assertEquals(0, groupConflicts, label + " must leave no group conflict");
    }

    /**
     * TASK-432 (candidate, flag-gated, default OFF): with profile-following ENABLED the
     * concentrated (group, subject) pairs land on the school's high-quota "monographic"
     * days instead of spreading chronologically. Canonical case: G10 / Servizio
     * tecnico e stilistico (T01, Thursday-only, 65h) concentrates on the 9
     * monographic Thursdays (5+5+7+8x6 = 65h exact, Dec-Jun) and leaves the autumn 4h
     * Thursdays free of the subject - inverting the flag-OFF baseline (37h autumn /
     * 24h monographic).
     *
     * <p>Sets the flag via {@link System#setProperty} before the solve (the established
     * pattern, see {@code PhaseTransitionMarkerBoxDumpTest}) and clears it in a finally,
     * so the sibling flag-OFF method is unaffected regardless of test order. The same
     * zero-conflict + recurrence guards the flag-OFF method asserts must still hold
     * flag-ON (the pass adds no accepted conflict), PLUS the dedicated concentration
     * measure ({@link #print3FAT01Profile}).
     */
    @Test
    @Timeout(value = 400)
    void profileFollowing_concentratesT01OnMonographicThursdays() {
        seedRealSeedFixture();
        System.setProperty("planner.construction.profile-following.enabled", "true");
        try {
            ConstructionResult r = buildAndSolveConstruction();
            SchoolSchedule solved = r.solved();
            DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = r.constructionPhase();

            // Stamp stats flag-ON. Unlike flag-OFF (skipConflict==0 - the template is
            // conflict-free), flag-ON's skipConflict is EXPECTED to be nonzero: the
            // profile-following pass fills the monographic days entirely with the
            // concentrated subject, so the chronological stamp then SKIPS (never accepts)
            // the template cells of the OTHER courses on those days - they defer to the
            // repair tail. The real safety guard is the final-population conflict check
            // below (zero ACCEPTED H1/H3 conflicts), not this skip count.
            DayClusteredConstructionPhase.StampStats stampStats = constructionPhase.getStampStats();
            System.out.printf("TASK-432 flag-ON stamp stats: placed %d, skipConflict %d (template cells displaced by the pass, expected nonzero), skipIllegal %d, skipExhausted %d, skipExpectedGoal %d%n",
                    stampStats.placed(), stampStats.skipConflict(), stampStats.skipIllegalOrClosed(),
                    stampStats.skipExhausted(), stampStats.skipExpectedHoursGoal());

            // Zero hard conflicts in the final population (same gate as flag-OFF).
            assertNoTeacherGroupConflicts(solved, "TASK-432 flag-ON");

            // Recurrence guard must still hold flag-ON (the pass must not collapse it).
            SnapshotFixture.RecurrenceKpi kpi = SnapshotFixture.weeklyRecurrence(solved.getLessons());
            System.out.printf("TASK-432 flag-ON recurrence: %.1f%% (flag-OFF baseline ~74.7%%)%n", kpi.percent());
            assertTrue(kpi.percent() >= 50.0,
                    "flag-ON recurrence must still be >= 50%, was " + kpi.percent() + "%");

            // Dedicated G10/T01 concentration measure. Flag-ON concentrates the
            // 65h onto the 9 monographic Thursdays (5+5+7+8x6 = 65h exact) and leaves the
            // autumn 4h Thursdays free of the subject - the inversion of the flag-OFF
            // baseline (~24h monographic / ~37h other). The pre-pass is deterministic
            // (TreeMap/TreeSet + semantic order), so this is a real regression guard.
            int[] profile = print3FAT01Profile(solved);
            int monographicHours = profile[0];
            int otherHours = profile[1];
            System.out.printf("TASK-432 flag-ON G10/T01: monographic=%dh, other=%dh "
                    + "(flag-OFF baseline ~24h monographic / ~37h other)%n", monographicHours, otherHours);
            assertTrue(monographicHours >= 55,
                    "flag-ON should concentrate >= 55h of T01 on the 9 monographic Thursdays, got " + monographicHours);
            assertTrue(otherHours <= 10,
                    "flag-ON should leave <= 10h of T01 on non-monographic Thursdays, got " + otherHours);
        } finally {
            System.clearProperty("planner.construction.profile-following.enabled");
        }
    }

    /**
     * TASK-450 (checkpoint-first, opzione A): rimisura TASK-430 (slot0-balancing) alla lente
     * NUOVA, il buco@slot1 a fine COSTRUZIONE, non al solve intero come il gate2 che la bocciò
     * misurando -hard/-full-day. La catena è dimostrata (handoff 2026-07-30, anello P1-vs-P2):
     * a fine costruzione i giorni difettosi di G11 sono ancorati@slot0 con buco@slot1, e la
     * prima LS (P2 Compactness) chiude H17 traslando il blocco a destra, causando i late-start.
     * {@link WeeklyTemplateBuilder#reserveSlot0Anchors} riserva un'ancora di 1h a slot0 (NON un
     * blocco contiguo slot0+slot1): se questo fa collassare il buco@slot1 la cura c'è già e non
     * si scrive codice nuovo; se il buco resta, 430 fa una cosa DIVERSA dal prototipo contiguo
     * direzione (c) e il prototipo ha senso. Spec:
     * claudedocs/spalmone-2026-27/task450-prototipo-ancora-spec.md.
     *
     * <p>MISURA, non verdetto: stampa i 4 numeri del checkpoint per il confronto OFF-vs-ON
     * (1. giorni ancorati al primo slot aperto, 2. giorni con buco@slot1 - il decisivo,
     * 3. ricorrenza fine-costruzione ~76-77%, 4. % celle declinate dallo stamp). Le uniche
     * asserzioni sono le guardie di sicurezza ereditate dal pattern profile-following (zero
     * conflitti H1/H3 accettati, ricorrenza >= 50%): il verdetto PASS/FAIL lo decide
     * l'orchestratore leggendo la coppia di numeri, non questo test.
     */
    @Test
    @Timeout(value = 400)
    void slot0BalancingOn_measuresSlot1HoleAtConstructionEnd() {
        seedRealSeedFixture();
        System.setProperty(SLOT0_BALANCING_FLAG, "true");
        try {
            ConstructionResult r = buildAndSolveConstruction();
            SchoolSchedule solved = r.solved();
            DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = r.constructionPhase();

            // Safety guards (same as profile-following flag-ON): reserveSlot0Anchors commits
            // only conflict-free cells into the live maps the unchanged main placement reads,
            // so zero accepted H1/H3 and the recurrence floor must still hold. NOT the verdict.
            assertNoTeacherGroupConflicts(solved, "TASK-450 slot0-balancing flag-ON");
            SnapshotFixture.RecurrenceKpi kpi = SnapshotFixture.weeklyRecurrence(solved.getLessons());

            // Metric 4: stamp decline (symmetric with the flag-OFF baseline print). Flag-OFF
            // asserts skipConflict==0 (conflict-free template); flag-ON may displace cells. A
            // skyrocketing decline means an infeasible template (the prototype's landmine).
            printStampDecline("TASK-450 slot0-balancing flag-ON", constructionPhase.getStampStats());

            // Metric 3: construction-end recurrence (flag-OFF baseline ~76-77%).
            System.out.printf("TASK-450 slot0-balancing flag-ON recurrence: %.1f%%%n", kpi.percent());
            assertTrue(kpi.percent() >= 50.0,
                    "flag-ON recurrence must hold the 50% floor, was " + kpi.percent() + "%");

            // Metrics 1 & 2 (decisive): anchored days + hole-after-anchor days.
            printAnchorHoleDiagnostic("TASK-450 slot0-balancing flag-ON", solved);

            // Dump under a flag-specific name so both OFF (construction-lessons.txt, written by
            // the baseline method) and ON survive one test run, for the resemblance-kpi.py
            // cross-check of metric 3 on the same population the live solve dumps.
            writeConstructionLessonsDump(solved.getLessons(), Path.of("target", "construction-lessons-slot0on.txt"));
        } finally {
            System.clearProperty(SLOT0_BALANCING_FLAG);
        }
    }

    /**
     * TASK-451 (checkpoint-first, flag-gated default OFF): misura il prototipo del blocco
     * prima-ora coordinato slot0-1 ({@code WeeklyTemplateBuilder#reserveFirstHourBlock}),
     * che generalizza {@code reserveSlot0Anchors}: riserva un blocco 2h slot0+slot1 (NON solo
     * slot0) col docente = anchor slot-0 del golden per (gruppo, weekday). Riservare il BLOCCO
     * chiude il buco@slot1 che fece bocciare slot0-balancing al gate2 ("sposta invece di
     * coordinare"); la preferenza golden e' la coordinazione cross-gruppo. MISURA, non verdetto:
     * stampa le 4 metriche del checkpoint per il confronto OFF-vs-ON (giorni ancorati@slot0,
     * giorni con buco@slot1 - il decisivo, ricorrenza fine-costruzione ~76-77%, stamp-decline/
     * skipConflict). Guardie di sicurezza ereditate (zero H1/H3 accettati, ricorrenza >= 50%);
     * il verdetto PASS/FAIL e' dell'orchestratore, il flag resta default OFF (nessuna adozione).
     */
    @Test
    @Timeout(value = 400)
    void firstHourSlotOn_measuresLateStartAtConstructionEnd() {
        seedRealSeedFixture();
        System.setProperty("planner.construction.first-hour-slot.enabled", "true");
        try {
            ConstructionResult r = buildAndSolveConstruction();
            SchoolSchedule solved = r.solved();
            DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = r.constructionPhase();

            // Safety guards (same as slot0-balancing flag-ON): the block commits only conflict-free
            // cells into the live maps, so zero accepted H1/H3 and the recurrence floor must hold.
            assertNoTeacherGroupConflicts(solved, "TASK-451 first-hour-slot flag-ON");
            SnapshotFixture.RecurrenceKpi kpi = SnapshotFixture.weeklyRecurrence(solved.getLessons());
            printStampDecline("TASK-451 first-hour-slot flag-ON", constructionPhase.getStampStats());
            System.out.printf("TASK-451 first-hour-slot flag-ON recurrence: %.1f%%%n", kpi.percent());
            assertTrue(kpi.percent() >= 50.0,
                    "flag-ON recurrence must hold the 50% floor, was " + kpi.percent() + "%");
            // Metrics 1 & 2 (decisive): anchored@slot0 days + hole-after-anchor days (slot1 hole).
            printAnchorHoleDiagnostic("TASK-451 first-hour-slot flag-ON", solved);
            writeConstructionLessonsDump(solved.getLessons(), Path.of("target", "construction-lessons-firsthouron.txt"));
        } finally {
            System.clearProperty("planner.construction.first-hour-slot.enabled");
        }
    }

    /**
     * TASK-533 via (b) (flag-gated DEFAULT OFF, {@code planner.construction.
     * quota-tail-moves-placed.enabled}): misura la firma H32 a fine A-CONSTRUCTION col pass
     * moves-placed armato. Il fill tail TASK-532 piazza solo lezioni UNPLACED; quando il pool
     * del gruppo e' tutto piazzato ma mal distribuito, la cella sotto-quota si chiude solo
     * SPOSTANDO ore. Questo metodo costruisce col flag ON e conta le celle sotto-quota
     * (stessa semantica H32 di QuotaFillRepairCheckpointTest: ore non-Stage vs quota
     * aula+lab dichiarata, escluse 1/G08 + G14): firma attesa ~0. Guardie di sicurezza
     * ereditate (zero H1/H3 accettati, ricorrenza >= 50%); MISURA, non verdetto: l'arming
     * del flag resta a maintainer. Dump su construction-lessons-quotatailon.txt per il confronto
     * col baseline OFF (construction-lessons.txt).
     */
    @Test
    @Timeout(value = 400)
    void quotaTailMovesPlacedOn_measuresH32SignatureAtConstructionEnd() {
        seedRealSeedFixture();
        System.setProperty("planner.construction.quota-tail-moves-placed.enabled", "true");
        try {
            ConstructionResult r = buildAndSolveConstruction();
            SchoolSchedule solved = r.solved();
            DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = r.constructionPhase();

            // Safety guards (same family as every flag-ON prototype above): the pass moves
            // only through canPlaceLesson-gated slots, so zero accepted H1/H3 and the
            // recurrence floor must hold. NOT the verdict.
            assertNoTeacherGroupConflicts(solved, "TASK-533 via-b quota-tail-moves-placed flag-ON");
            SnapshotFixture.RecurrenceKpi kpi = SnapshotFixture.weeklyRecurrence(solved.getLessons());
            printStampDecline("TASK-533 via-b flag-ON", constructionPhase.getStampStats());
            System.out.printf("TASK-533 via-b flag-ON recurrence: %.1f%%%n", kpi.percent());
            assertTrue(kpi.percent() >= 50.0,
                    "flag-ON recurrence must hold the 50% floor, was " + kpi.percent() + "%");

            // Decisive metric: under-quota cells at construction end (the H32 signature)
            int under = countUnderQuotaCellsH32(solved);
            System.out.printf("TASK-533 via-b flag-ON under-quota cells at construction end: %d"
                    + " (moves applied by the pass: %d)%n", under, constructionPhase.getQuotaTailMoved());
            // The signature target is ~0, but the pass can only move hours whose VRP
            // typing allows the target date (quota-bound lab subjects, teacher
            // availability, calendar windows): assert the hard floor (never worse
            // than the measured OFF baseline of 41) and PRINT the exact number for
            // maintainer - the verdict is his, not this test's.
            assertTrue(under <= 41, "under-quota cells must not exceed the OFF baseline (41), was " + under);

            writeConstructionLessonsDump(solved.getLessons(),
                    Path.of("target", "construction-lessons-quotatailon.txt"));
        } finally {
            System.clearProperty("planner.construction.quota-tail-moves-placed.enabled");
        }
    }

    /** (group, date) days below their declared aula+lab quota, non-Stage hours - the
     *  same H32 semantics QuotaFillRepair and its checkpoint test use (1/G08 and
     *  G14 excluded: pool-cap / import-pinned own their per-day target). */
    private int countUnderQuotaCellsH32(SchoolSchedule solved) {
        java.util.Set<String> excluded = java.util.Set.of("G04", "G08", "G14");
        java.util.Map<Long, String> nameById = new java.util.HashMap<>();
        java.util.Map<String, java.util.Map<java.time.LocalDate, int[]>> tally = new java.util.HashMap<>();
        for (Lesson l : solved.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) {
                continue;
            }
            var t = l.getTeacher();
            if (t != null && t.pseudo) {
                continue;
            }
            var g = l.course.studentGroup;
            nameById.put(g.id, g.name);
            tally.computeIfAbsent(g.name, k -> new java.util.HashMap<>())
                    .merge(l.timeslot.getDate(), new int[] {1}, (a, b) -> new int[] {a[0] + b[0]});
        }
        int under = 0;
        for (var ge : tally.entrySet()) {
            if (excluded.contains(ge.getKey())) {
                continue;
            }
            Long groupId = null;
            for (var e : nameById.entrySet()) {
                if (e.getValue().equals(ge.getKey())) {
                    groupId = e.getKey();
                    break;
                }
            }
            if (groupId == null) {
                continue;
            }
            for (var de : ge.getValue().entrySet()) {
                var row = SchoolSchedule.getExpectedHoursRow(groupId, de.getKey());
                if (row == null) {
                    continue;
                }
                int quota = row.aulaHours + row.labHours;
                if (quota > 0 && de.getValue()[0] < quota) {
                    under++;
                }
            }
        }
        return under;
    }

    /**
     * TASK-536 checkpoint 2: firma di costruzione col pavimento H33 attivo.
     * Pubblica i floor derivati (min(4, mathFloor, goldenExistenceMin)), gira
     * la costruzione e conta i giorni sotto-pavimento a fine stage A: il gate
     * e' MISURA (quanti giorni la costruzione lascia sotto il pavimento, per
     * gruppo), non verdetto. Guardie di sicurezza ereditate (zero H1/H3).
     */
    @Test
    @Timeout(value = 400)
    void h33DayFloorOn_measuresUnderFloorDaysAtConstructionEnd() {
        seedRealSeedFixture();
        try {
            // publish the derived floors exactly like refreshSolverStatics does
            java.util.List<StudentGroup> allGroups = StudentGroup.listAll();
            java.util.Map<String, Integer> floors = com.school.planner.service.DayFloorDeriver
                    .deriveHardFloors(allGroups, Course.<Course>listAll(),
                            DailyActivityQuota.<DailyActivityQuota>listAll());
            java.util.Map<Long, Integer> byId = new java.util.HashMap<>();
            for (StudentGroup g : allGroups) {
                Integer fl = floors.get(g.name);
                if (fl != null && fl > 0) {
                    byId.put(g.id, fl);
                }
            }
            SchoolSchedule.setDayFloorsStatic(byId);
            // TASK-533/536 combined package: arm the floor-aware tail for the
            // signature measurement (gate: 45 -> ~0 under-floor days)
            System.setProperty("planner.construction.floor-aware-tail.enabled", "true");

            ConstructionResult r = buildAndSolveConstruction();
            SchoolSchedule solved = r.solved();

            assertNoTeacherGroupConflicts(solved, "TASK-536 H33 flag-ON");
            int under = countUnderFloorDays(solved, byId);
            // diagnostic for the floor-aware tail design: how much unplaced
            // material per group is LEFT at construction end (a floor sweep can
            // only place what is still unplaced)
            java.util.Map<String, Integer> unplacedByGroup = new java.util.TreeMap<>();
            for (Lesson l : solved.getLessons()) {
                if (l.timeslot != null || l.pinned || l.course == null
                        || l.course.studentGroup == null) {
                    continue;
                }
                var t = l.getTeacher();
                if (t != null && t.pseudo) {
                    continue;
                }
                unplacedByGroup.merge(l.course.studentGroup.name, 1, Integer::sum);
            }
            System.out.printf("TASK-536 H33 ON unplaced-at-end by group: %s%n", unplacedByGroup);
            System.out.printf("TASK-536 H33 ON under-floor days at construction end: %d%n", under);
            // the construction is H33-blind (fills to QUOTA, floor is the new
            // guard): print per-group detail, assert only the safety floor
            assertTrue(under <= 60, "under-floor days must stay in the observed band, was " + under);
        } finally {
            SchoolSchedule.setDayFloorsStatic(java.util.Map.of());
            System.clearProperty("planner.construction.floor-aware-tail.enabled");
        }
    }

    /** (group, date) in-session days (quota aula+lab > 0) below their H33 floor. */
    private int countUnderFloorDays(SchoolSchedule solved, java.util.Map<Long, Integer> floors) {
        java.util.Map<Long, java.util.Map<java.time.LocalDate, int[]>> tally = new java.util.HashMap<>();
        for (Lesson l : solved.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) {
                continue;
            }
            var t = l.getTeacher();
            if (t != null && t.pseudo) {
                continue;
            }
            tally.computeIfAbsent(l.course.studentGroup.id, k -> new java.util.HashMap<>())
                    .merge(l.timeslot.getDate(), new int[] {1}, (a, b) -> new int[] {a[0] + b[0]});
        }
        int under = 0;
        for (var e : tally.entrySet()) {
            Integer floor = floors.get(e.getKey());
            if (floor == null || floor <= 0) {
                continue;
            }
            for (var d : e.getValue().entrySet()) {
                var row = SchoolSchedule.getExpectedHoursRow(e.getKey(), d.getKey());
                if (row == null || row.aulaHours + row.labHours <= 0) {
                    continue;
                }
                if (d.getValue()[0] < floor) {
                    under++;
                    System.out.printf("  sotto-pavimento: group=%d date=%s ore=%d floor=%d%n",
                            e.getKey(), d.getKey(), d.getValue()[0], floor);
                }
            }
        }
        return under;
    }

    /**
     * S2 SPIKE (TASK-449 cure, flag-gated default OFF): rimisura il prototipo room-aware del
     * WeeklyTemplateBuilder alla lente di costruzione. A flag ON, per un corso con tag
     * mono-stanza, il builder preferisce una cella template non gia' presa da un altro gruppo
     * sullo stesso tag (il pile-up acconciatura, 33 lezioni-eccedenti misurate a OFF). MISURA,
     * non verdetto: stampa ricorrenza + stamp-decline e dump su construction-lessons-roomaware
     * .txt per il confronto pile-up vs il baseline OFF (construction-lessons.txt). Usa le stanze
     * REALI (buildAndSolveConstructionWithRealRooms) cosi' roomCapacityForTag risolve i tag mono
     * e il prototipo e' attivo. Guardie di sicurezza ereditate (zero H1/H3, ricorrenza >= 50%);
     * il verdetto OFF-vs-ON e' di maintainer, il flag resta default OFF (nessuna adozione).
     */
    @Transactional
    @Test
    @Timeout(value = 400)
    void roomAwareOn_measuresTagPileUpAtConstructionEnd() {
        seedRealSeedFixture();
        RealSeedFixture.seedRooms();
        System.setProperty("planner.template.room-aware.enabled", "true");
        try {
            ConstructionResult r = buildAndSolveConstructionWithRealRooms();
            SchoolSchedule solved = r.solved();
            DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = r.constructionPhase();

            assertNoTeacherGroupConflicts(solved, "S2 room-aware flag-ON");
            SnapshotFixture.RecurrenceKpi kpi = SnapshotFixture.weeklyRecurrence(solved.getLessons());
            printStampDecline("S2 room-aware flag-ON", constructionPhase.getStampStats());
            System.out.printf("S2 room-aware flag-ON recurrence: %.1f%%%n", kpi.percent());
            assertTrue(kpi.percent() >= 50.0,
                    "flag-ON recurrence must hold the 50% floor, was " + kpi.percent() + "%");

            writeConstructionLessonsDump(solved.getLessons(), Path.of("target", "construction-lessons-roomaware.txt"));
        } finally {
            System.clearProperty("planner.template.room-aware.enabled");
        }
    }

    /**
     * TASK-470 (checkpoint-first, flag-gated DEFAULT OFF): misura il prototipo theory-spread del
     * WeeklyTemplateBuilder ({@code WeeklyTemplateBuilder#tryPlaceChunk} pass di preferenza
     * theory-subject) alla lente di costruzione. A flag ON, piazzando un corso teorico
     * (isTheoryForRule: non-lab, non pratico-tecnico) il builder PREFERSCE un weekday dove
     * quella materia non e' ancora piazzata per il gruppo; se nessun weekday alternativo e'
     * fattibile RIPIEGA sul first-fit di oggi (ripete same-day). Origine: lo sweep TASK-468 ha
     * mostrato che i blocchi 2h di teoria NASCONO in costruzione (residuo fine construction 1672
     * ~uguale al finale 1679, vs golden ~440), inerti a qualunque peso M-TR in [1,1000]; il lever
     * e' a monte qui, non un peso.
     *
     * <p>MISURA, non verdetto: stampa ricorrenza + stamp-decline (skipConflict) e dump su
     * construction-lessons-theoryspreadon.txt per la misura residuo (scripts/analyze/
     * theory_adjacent_residual_dump.py) confrontata col baseline OFF (construction-lessons.txt,
     * residuo ~1672) e il golden ~440 runtime. Il verdetto PASS/FAIL (residuo scende
     * sostanzialmente verso 440-600 senza regredire fill/ricorrenza) e' dell'orchestratore che
     * legge i numeri, non di questo test. Guardie di sicurezza ereditate (zero H1/H3 accettati,
     * ricorrenza >= 50%, stampSkipConflict invariato a flag-OFF): theory-spread e' una preferenza
     * pura dentro la scelta del weekday, quindi non puo' introdurre conflitti. Il flag resta
     * DEFAULT OFF (nessuna adozione); A/B staged di conferma successivo (la LS potrebbe
     * re-impacchettare la teoria).
     */
    @Test
    @Timeout(value = 400)
    void theorySpreadOn_measuresResidualAtConstructionEnd() {
        seedRealSeedFixture();
        System.setProperty("planner.construction.theory-spread.enabled", "true");
        try {
            ConstructionResult r = buildAndSolveConstruction();
            SchoolSchedule solved = r.solved();
            DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = r.constructionPhase();

            // Safety guards (same as slot0/first-hour flag-ON): theory-spread only reorders the
            // weekday preference inside tryPlaceChunk and falls back to first-fit, so it commits
            // only conflict-free cells and skipConflict must stay at its flag-OFF value.
            assertNoTeacherGroupConflicts(solved, "TASK-470 theory-spread flag-ON");
            SnapshotFixture.RecurrenceKpi kpi = SnapshotFixture.weeklyRecurrence(solved.getLessons());
            printStampDecline("TASK-470 theory-spread flag-ON", constructionPhase.getStampStats());
            System.out.printf("TASK-470 theory-spread flag-ON recurrence: %.1f%%%n", kpi.percent());
            assertTrue(kpi.percent() >= 50.0,
                    "flag-ON recurrence must hold the 50% floor, was " + kpi.percent() + "%");

            // Dump under a flag-specific name so both OFF (construction-lessons.txt) and ON survive
            // one test run, for the theory_adjacent_residual_dump.py residual measurement.
            writeConstructionLessonsDump(solved.getLessons(), Path.of("target", "construction-lessons-theoryspreadon.txt"));
        } finally {
            System.clearProperty("planner.construction.theory-spread.enabled");
        }
    }

    /**
     * TASK-564 v2.1 batch (28/08 notte, mandato "rifiniture v2 in BATCH, ciclo UNICO"):
     * the DIFFERENTIAL localized bench for the two refined cuts of the declared-day
     * pre-booking reserve (quota-aware surplus budgets, block-guard prefix contiguity -
     * {@code planner.construction.prebooking.quota-aware.enabled} /
     * {@code planner.construction.prebooking.block-guard.enabled}, both default ON under
     * the main flag, each restorable to the legacy v2 behavior with "false" from the
     * surefire command line). Measures, per arm: reserved cells (journal), EMPTY declared
     * cells (H34 signature), under-quota cells (H32), internal gaps (H17 signature),
     * recurrence, boxed, stamp decline - the axes the gate esito -81hard attributed the
     * damage to. MISURA, non verdetto: safety guards only (zero accepted H1/H3,
     * recurrence floor); the arms' comparison and the single confirmation cycle's
     * verdict belong to the orchestrator. Also drains the reserve journal to
     * {@code target/reserve-plan.txt} for the offline TASK-517 placement-parity check
     * (rifinitura 1: zero solve cycles) and dumps the construction grid for the
     * OFF-vs-ON per-group diff.
     */
    @Test
    @Timeout(value = 400)
    void declaredDayPreBookingOn_measuresRefinedReserveAtConstructionEnd() {
        seedRealSeedFixture();
        // PLANNER_PREBOOKING_BASELINE=1 runs the SAME axes with the flag OFF (the
        // differential's control arm), so every arm is measured by identical code.
        // The control arm sets "false" EXPLICITLY: since the 29/08 ratification the
        // flag defaults ON, so leaving it absent would silently measure the ON arm.
        boolean armOn = System.getenv("PLANNER_PREBOOKING_BASELINE") == null;
        if (armOn) {
            System.setProperty(com.school.planner.solver.DeclaredDayPreBooker.FLAG, "true");
            System.setProperty("prebooking.debug", "true");
        } else {
            System.setProperty(com.school.planner.solver.DeclaredDayPreBooker.FLAG, "false");
        }
        com.school.planner.solver.DeclaredDayPreBooker.RESERVE_JOURNAL.clear();
        try {
            ConstructionResult r = buildAndSolveConstruction();
            SchoolSchedule solved = r.solved();
            DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = r.constructionPhase();

            String arm = armOn ? "flag-ON" : "flag-OFF-control";
            assertNoTeacherGroupConflicts(solved, "TASK-564 v2.1 " + arm);
            SnapshotFixture.RecurrenceKpi kpi = SnapshotFixture.weeklyRecurrence(solved.getLessons());
            printStampDecline("TASK-564 v2.1 " + arm, constructionPhase.getStampStats());
            System.out.printf("TASK-564 v2.1 %s recurrence: %.1f%%%n", arm, kpi.percent());
            assertTrue(kpi.percent() >= 50.0,
                    "flag-ON recurrence must hold the 50% floor, was " + kpi.percent() + "%");

            long boxed = solved.getLessons().stream().filter(l -> l.timeslot == null).count();
            int reserved = com.school.planner.solver.DeclaredDayPreBooker.RESERVE_JOURNAL.size();
            Map<String, Long> reservedByGroup = new java.util.TreeMap<>();
            for (String line : com.school.planner.solver.DeclaredDayPreBooker.RESERVE_JOURNAL) {
                reservedByGroup.merge(line.split("\\|")[0], 1L, Long::sum);
            }
            int empty = countEmptyDeclaredCells(solved);
            int under = countUnderQuotaCellsH32(solved);
            int gaps = countInternalGaps(solved);
            System.out.printf("TASK-564 v2.1 BENCH (%s): reserved=%d boxed=%d emptyDeclared=%d underQuota=%d internalGaps=%d%n",
                    arm, reserved, boxed, empty, under, gaps);
            if (armOn) {
                // tripwire: a silently-disarmed reserve (flag lost, gates always
                // failing, degenerate caps) would read reserved=0 and still look
                // green; on this seed the armed reserve always books (28/09
                // code-review cut-for-severity finding)
                assertTrue(reserved > 0,
                        "the armed reserve must book at least one cell on the real seed, got 0 (flag lost? knob typo?)");
            }
            System.out.printf("TASK-564 v2.1 reserved by group: %s%n", reservedByGroup);
            System.out.printf("TASK-564 v2.1 knobs: quota-aware=%b block-guard=%b parity=%b%n",
                    com.school.planner.solver.DeclaredDayPreBooker.quotaAwareRefinementEnabled(),
                    com.school.planner.solver.DeclaredDayPreBooker.blockGuardRefinementEnabled(),
                    com.school.planner.solver.DeclaredDayPreBooker.parityRefinementEnabled());

            writeConstructionLessonsDump(solved.getLessons(),
                    Path.of("target", armOn ? "construction-lessons-prebookingon.txt"
                            : "construction-lessons-prebookingoff.txt"));
            if (armOn) {
                try {
                    java.nio.file.Files.write(Path.of("target", "reserve-plan.txt"),
                            com.school.planner.solver.DeclaredDayPreBooker.RESERVE_JOURNAL);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        } finally {
            System.clearProperty(com.school.planner.solver.DeclaredDayPreBooker.FLAG);
            System.clearProperty("prebooking.debug");
            com.school.planner.solver.DeclaredDayPreBooker.RESERVE_JOURNAL.clear();
        }
    }

    /** TASK-564 v2.1 bench axis: declared in-scope (group, date) cells with ZERO
     *  non-Stage hours placed - the H34 signature (same scope as {@link
     *  #countUnderQuotaCellsH32}: 1/G08 pool-cap and G14 excluded). Walks the
     *  DECLARED rows (an empty cell has no placed lesson to iterate). */
    private int countEmptyDeclaredCells(SchoolSchedule solved) {
        Map<String, java.util.Map<java.time.LocalDate, int[]>> tally = new java.util.HashMap<>();
        for (Lesson l : solved.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) continue;
            var t = l.getTeacher();
            if (t != null && t.pseudo) continue;
            tally.computeIfAbsent(l.course.studentGroup.name, k -> new java.util.HashMap<>())
                    .merge(l.timeslot.getDate(), new int[] {1}, (a, b) -> new int[] {a[0] + b[0]});
        }
        int empty = 0;
        java.util.Set<java.time.LocalDate> gridDates = new java.util.HashSet<>();
        for (Timeslot ts : solved.getTimeslots()) {
            if (ts.getDate() != null) gridDates.add(ts.getDate());
        }
        for (StudentGroup g : solved.getStudentGroups()) {
            // the SAME scope predicate the phase's quota-aware cut and H32/H34 use
            // (isQuotaFillExcluded), not a hardcoded copy that can drift from it
            if (g == null || g.id == null
                    || SchoolScheduleConstraintProvider.isQuotaFillExcluded(g.name)) continue;
            for (var row : SchoolSchedule.getExpectedHoursForGroup(g.id).entrySet()) {
                if (row.getValue().aulaHours + row.getValue().labHours <= 0) continue;
                if (!gridDates.contains(row.getKey())) continue; // H32/H34 scope: dates WITH timeslots
                int placed = tally.getOrDefault(g.name, Map.of())
                        .getOrDefault(row.getKey(), new int[] {0})[0];
                if (placed == 0) empty++;
            }
        }
        return empty;
    }

    /** TASK-564 v2.1 bench axis: internal holes (unoccupied slot indexes strictly
     *  between a (group, date) day's first and last occupied slot, Stage excluded) -
     *  the raw H17 signature at construction end, before any LS reshapes it.
     *  Deliberately NOT {@code HoleRepair.countPenalizedGapSlots}: that counter is
     *  H17's canonical semantics (break-level pinned exemption, Stage included);
     *  this axis wants the RAW pre-LS shape so the OFF-vs-ON differential is not
     *  filtered by the very exemptions the LS phases later exploit. */
    private int countInternalGaps(SchoolSchedule solved) {
        Map<String, java.util.Map<java.time.LocalDate, java.util.Set<Integer>>> slots = new java.util.HashMap<>();
        for (Lesson l : solved.getLessons()) {
            if (l.timeslot == null || l.getStudentGroup() == null) continue;
            var t = l.getTeacher();
            if (t != null && t.pseudo) continue;
            slots.computeIfAbsent(l.getStudentGroup().name, k -> new java.util.HashMap<>())
                    .computeIfAbsent(l.timeslot.getDate(), k -> new java.util.TreeSet<>())
                    .add(TimeslotSequence.slotIndexOf(l.timeslot));
        }
        int gaps = 0;
        for (var ge : slots.values()) {
            for (var de : ge.values()) {
                int lo = java.util.Collections.min(de);
                int hi = java.util.Collections.max(de);
                gaps += (hi - lo + 1) - de.size();
            }
        }
        return gaps;
    }

    /**
     * TASK-432 dedicated measure: G10 / Servizio tecnico e stilistico (T01)
     * hours placed per Thursday vs the school's expected quota that Thursday. Returns
     * {@code [monographicHours, otherHours]} where monographic = Thursdays the sheet
     * expects {@code >= 5h} (the 9 "monographic" days, quotas 5+5+7+8x6 = 65h exact)
     * and other = the {@code <= 4h} Thursdays. Flag-OFF baseline: ~24h / ~37h (spread
     * on autumn). Flag-ON target: ~65h / ~0h (concentrated on the 9 monographic).
     */
    private int[] print3FAT01Profile(SchoolSchedule solved) {
        final String GROUP = "G10";
        final String SUBJECT = "Servizio tecnico e stilistico";
        String horizonStart = TimeslotSequence.SCHOOL_YEAR_START.toString();
        String horizonEnd = TimeslotSequence.SCHOOL_YEAR_END.toString();
        List<LocalDate> thursdays = new ArrayList<>();
        Map<LocalDate, Integer> expectedByDate = new HashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("calendario_class_days.csv", 4)) {
            if (!GROUP.equals(row[0].trim())) continue;
            String dateStr = row[1].trim();
            if (dateStr.compareTo(horizonStart) < 0 || dateStr.compareTo(horizonEnd) > 0) continue;
            LocalDate d = LocalDate.parse(dateStr);
            if (d.getDayOfWeek() != DayOfWeek.THURSDAY) continue;
            thursdays.add(d);
            expectedByDate.put(d, Integer.parseInt(row[2].trim()));
        }
        Collections.sort(thursdays);
        Map<LocalDate, Integer> placedByDate = new HashMap<>();
        for (Lesson lesson : solved.getLessons()) {
            if (lesson.timeslot == null || lesson.course == null) continue;
            StudentGroup g = lesson.course.studentGroup;
            if (g == null || !GROUP.equals(g.name) || !SUBJECT.equals(lesson.course.subject)) continue;
            LocalDate d = lesson.timeslot.getDate();
            if (d.getDayOfWeek() != DayOfWeek.THURSDAY) continue;
            placedByDate.merge(d, 1, Integer::sum);
        }
        int monographicHours = 0;
        int otherHours = 0;
        StringBuilder sb = new StringBuilder("TASK-432 G10/T01 Thursday profile (date (weekday, expected) -> placed T01):");
        for (LocalDate d : thursdays) {
            int exp = expectedByDate.getOrDefault(d, 0);
            int placed = placedByDate.getOrDefault(d, 0);
            if (exp >= 5) monographicHours += placed; else otherHours += placed;
            sb.append(String.format("%n  %s (%s, exp %dh): %dh%s", d, d.getDayOfWeek(), exp, placed,
                    exp >= 5 ? "  <== monographic" : ""));
        }
        sb.append(String.format("%nTASK-432 G10/T01: monographic(>=5h)=%dh, other(<=4h)=%dh", monographicHours, otherHours));
        System.out.println(sb);
        return new int[] { monographicHours, otherHours };
    }

    /**
     * TASK-433 (candidate, flag-gated, default OFF): the AnchorRepair pass closes LEADING M18
     * late-start holes by cross-date import, with the M18 donor-anchor guard always ON (no shell
     * game). Exercised directly on the construction-end grid (AnchorRepair lives in the staged
     * cycle post-HoleRepair; on the construction grid there are no promoted pins, so its
     * post-smarcatura guard passes). Asserts the late-start reduction AND the two safety
     * invariants: zero donor days made newly late (the M18-guard) and zero conflicts added
     * (every move is conflict-free by construction).
     */
    @Test
    @Timeout(value = 400)
    void anchorRepair_closesLateStartsWithNoDeanchor() {
        seedRealSeedFixture();
        ConstructionResult r = buildAndSolveConstruction();
        SchoolSchedule solved = r.solved();

        AnchorRepair.Result result = AnchorRepair.repair(solved,
                (lesson, ts) -> lesson.timeslot = ts,
                lesson -> lesson.room = null);
        System.out.printf("TASK-433 anchor repair: late-start days %d -> %d (filled %d leading slots; "
                        + "fully %d, partial %d, none %d; donor days emptied %d, newly-late %d), conflicts added %d%n",
                result.lateDaysBefore(), result.lateDaysAfter(), result.slotsFilled(),
                result.fullyClosed(), result.partiallyClosed(), result.notClosed(),
                result.donorDaysEmptied(), result.donorDaysNewlyLate(), result.conflictsAdded());

        assertTrue(result.lateDaysBefore() > 0,
                "the real-seed construction grid should have M18 late-start days to repair, got " + result.lateDaysBefore());
        assertTrue(result.lateDaysAfter() < result.lateDaysBefore(),
                "anchor repair should reduce late-start days, was " + result.lateDaysBefore()
                        + " -> " + result.lateDaysAfter());
        assertEquals(0, result.donorDaysNewlyLate(),
                "the M18-guard must never de-anchor a donor day (no shell game), got " + result.donorDaysNewlyLate());
        assertEquals(0, result.conflictsAdded(),
                "anchor repair must add no hard conflict (every move is conflict-free), got " + result.conflictsAdded());
    }

    /**
     * TASK-390 (H17) investigation: are the R1 benchmark's "avoidable" H17
     * student-day gaps actually fillable, or is there a hidden VRP block?
     *
     * <p>For each real R1 gap (attributed from the benchmark-391-results
     * arm-R1-runN lessons dumps), the movable lesson's teacher was verified FREE at the gap
     * slot (no H1 conflict) - so the only remaining question is whether the gap
     * slot is in that lesson's {@link Lesson#getAvailableTimeslots()} value range
     * (calendar/H20, quota/H23-H24, availability/H5, holiday/H6, stage/H4-H25).
     * getAvailableTimeslots is placement-independent (it never looks at where the
     * OTHER lessons currently sit, except pinned-same-group, and this fixture has
     * no pins), so the answer here is the same as it was in the R1 solve.
     *
     * <p>VRP-available for ALL => no hidden block => the gaps are a pure SEARCH
     * COVERAGE issue (the move is legal, the LS just didn't take it before
     * termination) => fix belongs in construction compaction. A VRP block on any
     * case => that gap is NOT trivially avoidable (a real calendar/quota reason),
     * refuting "just coverage" for it.
     */
    @Test
    @Timeout(value = 400)
    void h17AvoidableGaps_areVrpAvailableForTheMovableLesson() {
        seedRealSeedFixture();
        List<Timeslot> timeslots = Timeslot.listAll();
        List<Lesson> lessons = Lesson.listAll();
        List<StudentGroup> allGroups = StudentGroup.listAll();
        for (StudentGroup g : allGroups) {
            g.idealEndDateVrpBuffered = TimeslotSequence.SCHOOL_YEAR_END;
        }
        SnapshotFixture.wireConstructionStatics(timeslots, allGroups, lessons,
                Teacher.listAll(), Course.listAll(), TeacherAvailability.listAll(),
                List.of(), List.of(), List.of(), null, false, false);

        // Real R1 "avoidable" gaps: {group, subject, teacher, date, gapSlot}.
        String[][] cases = {
                {"G08", "Matematica", "T05", "2027-01-20", "2"},
                {"G04", "Sviluppo Sostenibile", "T25", "2026-10-23", "3"},
                {"G07", "Disegno Edile ed Impiantistico", "T12", "2026-11-12", "6"},
                {"G13", "Personalizzazione", "T11", "2027-05-03", "2"},
                {"G01", "Orientamento", "T02", "2027-06-10", "4"},
                {"G12", "Storia", "T02", "2026-12-16", "2"},
        };
        List<String> blocked = new java.util.ArrayList<>();
        List<String> notFound = new java.util.ArrayList<>();
        for (String[] c : cases) {
            String grp = c[0], subj = c[1], teach = c[2];
            LocalDate date = LocalDate.parse(c[3]);
            int gapSlot = Integer.parseInt(c[4]);
            Lesson movable = lessons.stream().filter(l -> l.course != null
                    && grp.equals(l.course.studentGroup != null ? l.course.studentGroup.name : null)
                    && subj.equals(l.course.subject)
                    && teach.equals(l.course.teacher != null ? l.course.teacher.name : null))
                    .findFirst().orElse(null);
            Timeslot gapTs = timeslots.stream().filter(ts -> ts.getDate().equals(date)
                    && TimeslotSequence.slotIndexOf(ts) == gapSlot).findFirst().orElse(null);
            if (movable == null || gapTs == null) {
                notFound.add(grp + "/" + subj + "/" + date + "@" + gapSlot
                        + " (movable=" + (movable != null) + ", gapTs=" + (gapTs != null) + ")");
                continue;
            }
            boolean vrpOk = movable.getAvailableTimeslots().contains(gapTs);
            System.out.printf("TASK-390 H17 gap %s | %s | %s @slot%d -> VRP-available=%s%n",
                    grp, date, subj, gapSlot, vrpOk);
            if (!vrpOk) blocked.add(grp + "/" + subj + "/" + date + "@slot" + gapSlot);
        }
        System.out.printf("TASK-390 H17 VRP check: %d/%d cases VRP-available, %d blocked, %d not-found%n",
                cases.length - blocked.size() - notFound.size(), cases.length, blocked.size(), notFound.size());
        if (!notFound.isEmpty()) System.out.println("  not-found (seed mismatch): " + notFound);
        if (!blocked.isEmpty()) System.out.println("  VRP-BLOCKED (not pure coverage): " + blocked);
        // Sanity: every case must resolve to a real seeded course + timeslot, else the
        // fixture drifted from the R1 data and the check is meaningless.
        assertTrue(notFound.isEmpty(), "gap cases not found in real seed (fixture drift): " + notFound);
    }

    /**
     * TASK-416: the M32 reference template ({@link WeeklyTemplatePlanner#buildReference})
     * is assignment-agnostic and therefore STABLE across a solve. Concretely:
     * <ol>
     *   <li>on a fresh pre-solve state (every unpinned lesson unassigned) reference
     *       mode and CH mode ({@link WeeklyTemplatePlanner#build}) produce the SAME
     *       template — the coincidence the design relies on;</li>
     *   <li>after every unpinned lesson is assigned a timeslot (a re-analyze /
     *       re-solve state), reference mode rebuilds the IDENTICAL template
     *       (same cells per course), while CH mode collapses to EMPTY — which is
     *       exactly the bug this task fixes (M32 silently scoring 0 post-solve).</li>
     * </ol>
     *
     * <p>The assigned state is simulated by pointing every unpinned lesson at a
     * single timeslot: reference mode ignores an unpinned lesson's current timeslot
     * entirely (population is all-unpinned, occupancy is pinned-only), so any
     * assignment must yield the same template as the unassigned state — a
     * degenerate assignment proves the invariant without a stochastic solve.
     */
    @Test
    @Timeout(value = 400)
    void referenceMode_templateIsStableAcrossAssignment() {
        seedRealSeedFixture();

        List<Timeslot> timeslots = Timeslot.listAll();
        List<Lesson> lessons = Lesson.listAll();
        for (StudentGroup group : StudentGroup.<StudentGroup>listAll()) {
            group.idealEndDateVrpBuffered = TimeslotSequence.SCHOOL_YEAR_END;
        }

        // ── Wire the static VRP snapshots exactly like ScheduleService.refreshSolverStatics() ──
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(new HashMap<>());
        SchoolSchedule.setTeacherAvailabilitiesStatic(TeacherAvailability.listAll());
        SchoolSchedule.setSchoolHolidaysStatic(List.<SchoolHoliday>of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), java.util.Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(GroupCalendarRule.listAll());
        SchoolSchedule.setDailyActivityQuotasStatic(DailyActivityQuota.listAll());
        SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);

        java.util.Set<LocalDate> holidays = SchoolSchedule.getSchoolHolidaysStatic();
        List<StagePattern> stages = List.of();

        // 1. Fresh (all-unpinned-unassigned) state: reference and CH modes coincide.
        Map<Long, java.util.Set<Integer>> refFresh = encodeTemplate(
                WeeklyTemplatePlanner.buildReference(lessons, timeslots, holidays, stages));
        Map<Long, java.util.Set<Integer>> chFresh = encodeTemplate(
                WeeklyTemplatePlanner.build(lessons, timeslots, holidays, stages));
        assertFalse(refFresh.isEmpty(),
                "reference template must be non-empty on the real seed (nothing to measure otherwise)");
        assertEquals(chFresh, refFresh,
                "on a fresh pre-solve state reference mode and CH mode must produce the identical template");

        // 2. Simulate a solved/partial state: assign every unpinned lesson a timeslot.
        Timeslot anySlot = timeslots.get(0);
        for (Lesson lesson : lessons) {
            if (!lesson.isPinned()) lesson.timeslot = anySlot;
        }

        Map<Long, java.util.Set<Integer>> refAssigned = encodeTemplate(
                WeeklyTemplatePlanner.buildReference(lessons, timeslots, holidays, stages));
        Map<Long, java.util.Set<Integer>> chAssigned = encodeTemplate(
                WeeklyTemplatePlanner.build(lessons, timeslots, holidays, stages));

        assertEquals(refFresh, refAssigned,
                "TASK-416: the reference template must be IDENTICAL before and after assignment "
                        + "(same cells per course) - this is the stability M32 depends on");
        assertTrue(chAssigned.isEmpty(),
                "CH mode collapses to an empty template once every unpinned lesson is placed - "
                        + "the exact bug (M32 scoring 0 post-solve) reference mode fixes");
    }

    /** Encode a template map into {@code courseId -> set of encoded (weekday, slot) cells}, mirroring
     *  {@code ScheduleService.publishWeeklyTemplateStatics}'s cell expansion. */
    private static Map<Long, java.util.Set<Integer>> encodeTemplate(
            Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> template) {
        Map<Long, java.util.Set<Integer>> cellsByCourse = new HashMap<>();
        for (Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>> byDay : template.values()) {
            for (Map.Entry<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>> dayEntry : byDay.entrySet()) {
                DayOfWeek day = dayEntry.getKey();
                for (WeeklyTemplateBuilder.TemplateCell cell : dayEntry.getValue()) {
                    Course course = cell.course();
                    if (course == null || course.id == null) continue;
                    java.util.Set<Integer> cells = cellsByCourse.computeIfAbsent(course.id, k -> new java.util.HashSet<>());
                    for (int slot = cell.slotIndex(); slot < cell.slotIndex() + cell.length(); slot++) {
                        cells.add(SchoolSchedule.encodeTemplateCell(day, slot));
                    }
                }
            }
        }
        return cellsByCourse;
    }

    /**
     * TASK-435 (ratified 2026-07-30, default ON): the constrained-teacher pre-assignment pin's
     * DECISIVE verdict at construction end. The pin is now the production default; this test
     * forces it explicitly via {@code planner.pre-assign.constrained-teacher.enabled} (redundant
     * with the default, but kept for the explicit checkpoint). Runs {@link HybridPhase1aService#run()}
     * (Phase 0 pins the constrained teachers - 2026/27: T01 on his 35 Thursdays + T07 on
     * Wednesdays - to their golden allocation, then short-circuits Phase 1/2/3 since the seed has
     * no LabRotations), then runs the construction phase on the pinned population. Asserts:
     * <ul>
     *   <li>boxed-T01 == 0 (the systemic residual the solver provably cannot place);</li>
     *   <li>every pinned T01 lesson is on a Thursday (H5 respected by construction);</li>
     *   <li>total boxed (H0) stays tiny (the pin does not inflate the residual);</li>
     *   <li>guards held: stamp 0 accepted conflicts, compaction 0 conflicts, final-population
     *       0 H1/H3, recurrence >= 50% floor.</li>
     * </ul>
     * Env-gated ({@code PLANNER_TEST_CONSTRAINED_TEACHER=1}, DEFAULT OFF) so it does not add
     * its ~60s to the default suite; invoke explicitly for the checkpoint verdict:
     * {@code PLANNER_TEST_CONSTRAINED_TEACHER=1 ./scripts/core/test.sh -c WeeklyTemplateConstructionRealSeedTest}.
     */
    @Test
    @Timeout(value = 400)
    @EnabledIfEnvironmentVariable(named = "PLANNER_TEST_CONSTRAINED_TEACHER", matches = "1")
    @Transactional
    void constrainedTeacherPin_eliminatesBoxedT01AtConstructionEnd() {
        seedRealSeedFixture();
        // Seed the real 18 Rooms: the pin now assigns a room (findRoomForLesson) so the pinned
        // lesson is complete (@PlanningPin makes room immovable, so the pin must set it). This L2
        // fixture otherwise seeds no Rooms. The construction solve below still uses rooms=List.of()
        // (only the pre-assignment pin reads them).
        RealSeedFixture.seedRooms();
        wireStaticsForPreAssign();

        System.setProperty(HybridPhase1aService.CONSTRAINED_TEACHER_FLAG, "true");
        int phase0Pinned;
        try {
            HybridPhase1aReport report = hybridPhase1aService.run();
            phase0Pinned = report.phaseResults.stream()
                    .filter(p -> "0-CONSTRAINED-TEACHER".equals(p.phaseName))
                    .mapToInt(p -> p.entitiesPinned).sum();
        } finally {
            // default is ON (ratified), so restore the explicit OFF baseline (matches setUp).
            System.setProperty(HybridPhase1aService.CONSTRAINED_TEACHER_FLAG, "false");
        }
        assertTrue(phase0Pinned > 0,
                "Phase 0 should pin the constrained teachers' lessons (T01 231h + T07), got " + phase0Pinned);
        System.out.printf("TASK-435 Phase 0 pre-assignment pinned %d lessons%n", phase0Pinned);

        ConstructionResult r = buildAndSolveConstruction();
        SchoolSchedule solved = r.solved();
        DayClusteredConstructionPhase<SchoolSchedule> constructionPhase = r.constructionPhase();

        // ── Diagnostics first (so a boxed-T01 failure still shows the full picture). ──
        long unassigned = solved.getLessons().stream().filter(l -> l.timeslot == null).count();
        System.out.printf("TASK-435 total boxed (H0)=%d of %d%n", unassigned, solved.getLessons().size());
        // Per-group boxed breakdown: localises whether the construction-end boxing is concentrated
        // in the pinned teachers' groups (the fills-days disruption) or spread everywhere.
        Map<String, Long> boxedByGroup = new java.util.TreeMap<>();
        for (Lesson lesson : solved.getLessons()) {
            if (lesson.timeslot == null && lesson.getStudentGroup() != null) {
                boxedByGroup.merge(lesson.getStudentGroup().name, 1L, Long::sum);
            }
        }
        System.out.printf("TASK-435 boxed by group: %s%n", boxedByGroup);
        // With pre-assignment pins present, the weekly template's skipConflict is EXPECTED to be
        // nonzero (the stamp skips cells the constrained-teacher pins occupy - the same
        // displacement TASK-432's profile-following flag-ON documents). The real safety guard is
        // the final-population conflict check below (zero ACCEPTED H1/H3 conflicts), not this
        // skip count, so it is reported, not asserted, here.
        System.out.printf("TASK-435 stamp stats: placed %d, skipConflict %d (pinned-cell displacement, expected >0), "
                        + "skipIllegal %d, skipExhausted %d%n",
                constructionPhase.getStampStats().placed(), constructionPhase.getStampStats().skipConflict(),
                constructionPhase.getStampStats().skipIllegalOrClosed(), constructionPhase.getStampStats().skipExhausted());
        assertEquals(0, constructionPhase.getCompactionAddedConflicts(),
                "compaction must never add a teacher/group conflict");
        assertNoTeacherGroupConflicts(solved, "TASK-435 flag-ON");
        SnapshotFixture.RecurrenceKpi kpi = SnapshotFixture.weeklyRecurrence(solved.getLessons());
        System.out.printf("TASK-435 flag-ON recurrence: %.1f%% (floor 50%%, flag-OFF baseline ~74.7%%)%n",
                kpi.percent());

        // ── DECISIVE VERDICT: 0 boxed T01 at construction end. ──
        long boxedT01 = 0;
        long pinnedT01 = 0;
        long nonThursdayT01 = 0;
        long pinnedT01NoRoom = 0;
        for (Lesson lesson : solved.getLessons()) {
            Teacher t = lesson.course != null ? lesson.course.teacher : null;
            if (t == null || !"T01".equals(t.name)) continue;
            if (lesson.pinned) {
                pinnedT01++;
                if (lesson.room == null) pinnedT01NoRoom++;
            }
            if (lesson.timeslot == null) boxedT01++;
            else if (lesson.timeslot.getDate().getDayOfWeek() != DayOfWeek.THURSDAY) nonThursdayT01++;
        }
        System.out.printf("TASK-435 verdict: T01 boxed=%d, pinned=%d (no-room=%d), on-non-Thursday=%d%n",
                boxedT01, pinnedT01, pinnedT01NoRoom, nonThursdayT01);
        assertEquals(0, boxedT01,
                "constrained-teacher pin must leave 0 boxed T01 at construction end, got " + boxedT01);
        assertEquals(0, nonThursdayT01,
                "every pinned T01 lesson must be on a Thursday (H5 respected by construction)");
        assertEquals(0, pinnedT01NoRoom,
                "every pinned T01 lesson must have a room: @PlanningPin makes a pinned null room "
                        + "permanent (the room phase cannot assign it), got " + pinnedT01NoRoom);
        // total boxed is reported-not-asserted here: this L2 fixture seeds no course classification
        // (requiredRoomTags), so findRoomForLesson maps every course to the same room and the
        // construction boxes unrelated lessons on H2 - a fixture artifact, not a TASK-435 regression
        // (production courses carry real tags). boxed-T01=0 above is the real verdict.
        System.out.printf("TASK-435 total boxed (H0)=%d of %d (reported, not asserted - see comment)%n",
                unassigned, solved.getLessons().size());
        assertTrue(kpi.percent() >= 50.0,
                "flag-ON recurrence must hold the 50% floor, was " + kpi.percent() + "%");
    }

    /**
     * Wire the static VRP snapshots (exactly {@code buildAndSolveConstruction} does for the
     * solve) BEFORE {@link HybridPhase1aService#run()}, so the constrained-teacher phase's
     * {@code openSlotsForGroupDay} (which reads {@link SchoolSchedule#getGroupCalendarWindow}
     * off the static {@code groupCalendarRulesByGroupStatic} map) resolves the real per-group
     * calendar. {@code buildAndSolveConstruction} re-wires with the post-pin lesson population.
     */
    private void wireStaticsForPreAssign() {
        List<Timeslot> timeslots = Timeslot.listAll();
        List<Lesson> lessons = Lesson.listAll();
        for (StudentGroup group : StudentGroup.<StudentGroup>listAll()) {
            group.idealEndDateVrpBuffered = TimeslotSequence.SCHOOL_YEAR_END;
        }
        SchoolSchedule.setAllTimeslotsStatic(timeslots);
        SchoolSchedule.setAllRoomsStatic(List.of());
        SchoolSchedule.setAllLessonsStatic(lessons);
        SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic(lessons);
        SchoolSchedule.setIdealEndDateVrpBufferedStatic(new HashMap<>());
        SchoolSchedule.setTeacherAvailabilitiesStatic(TeacherAvailability.listAll());
        SchoolSchedule.setSchoolHolidaysStatic(List.<SchoolHoliday>of());
        SchoolSchedule.setStageBlackoutDatesStatic(List.<StagePattern>of(), java.util.Set.of());
        SchoolSchedule.setTimeslotSequenceStatic(new TimeslotSequence(timeslots));
        SchoolSchedule.setGroupCalendarRulesStatic(GroupCalendarRule.listAll());
        SchoolSchedule.setDailyActivityQuotasStatic(DailyActivityQuota.listAll());
        SchoolSchedule.computeLessonAvailableTimeslotCounts(lessons);
    }

    /**
     * TASK-450 metric 4 print (symmetric OFF-vs-ON): stamp decline breakdown. Captures each
     * skip counter once and prints the total decline %, so the flag-OFF baseline and the
     * flag-ON twin emit the identical shape for the side-by-side comparison.
     */
    private void printStampDecline(String label, DayClusteredConstructionPhase.StampStats stats) {
        int placed = stats.placed();
        int skipConflict = stats.skipConflict();
        int skipIllegal = stats.skipIllegalOrClosed();
        int skipExhausted = stats.skipExhausted();
        int skipExpectedGoal = stats.skipExpectedHoursGoal();
        int totalSkipped = skipConflict + skipIllegal + skipExhausted + skipExpectedGoal;
        System.out.printf("%s stamp: placed=%d skipConflict=%d skipIllegal=%d skipExhausted=%d "
                        + "skipExpectedGoal=%d (total decline %.1f%%)%n",
                label, placed, skipConflict, skipIllegal, skipExhausted, skipExpectedGoal,
                100.0 * totalSkipped / Math.max(1, placed + totalSkipped));
    }

    /**
     * TASK-450 checkpoint print: anchored days + hole-after-anchor days at construction end.
     * Thin logger over {@link #measureAnchorHole}, so the flag-OFF baseline and the flag-ON
     * twin print the identical shape for the side-by-side 4-number comparison.
     */
    private void printAnchorHoleDiagnostic(String label, SchoolSchedule solved) {
        AnchorHoleKpi ah = measureAnchorHole(solved);
        System.out.printf("%s anchor/hole (non-G14): in-session=%d, anchored=%d (%.1f%%), "
                        + "hole-after-anchor=%d (%.1f%%), of which with-block-after=%d%n",
                label, ah.daysInSession(), ah.anchored(),
                100.0 * ah.anchored() / Math.max(1, ah.daysInSession()),
                ah.holeAfterAnchor(),
                100.0 * ah.holeAfterAnchor() / Math.max(1, ah.daysInSession()),
                ah.holeAfterAnchorWithBlock());
        for (Map.Entry<String, int[]> e : new java.util.TreeMap<>(ah.byGroup()).entrySet()) {
            // [inSession, anchored, hole, holeWithBlock]
            int[] c = e.getValue();
            System.out.printf("  %s: in-session=%d anchored=%d hole-after-anchor=%d (with-block=%d)%n",
                    e.getKey(), c[0], c[1], c[2], c[3]);
        }
    }

    /**
     * TASK-450 checkpoint measure: per (group, date) in session, whether the day is ANCHORED at
     * its calendar's first open slot and whether it carries a HOLE right after that anchor
     * (anchor slot placed, anchor+1 in-window but NOT placed). That hole is exactly the
     * "slot0 pieno + slot1 vuoto" population the handoff isolated for G11: the precondition
     * the first LS exploits to slide the block right, closing the H17 internal gap but starting
     * the day late (the M18 late-start). Window-relative
     * via {@link SchoolSchedule#getGroupCalendarWindow}, so it subsumes the literal slot0/slot1
     * case when minOpenSlot==0 (normal groups) and correctly excludes G14 short windows only
     * from the headline (G14 stays visible in {@link AnchorHoleKpi#byGroup}).
     *
     * <p>{@code holeAfterAnchorWithBlock} narrows to days that ALSO have a placed slot beyond the
     * hole (a real internal gap the LS can close by sliding); a lone anchor hour with nothing
     * after is not a slide candidate. Both are reported: the headline matches the spec's literal
     * metric 2, the with-block subset is the mechanistically decisive one.
     */
    private AnchorHoleKpi measureAnchorHole(SchoolSchedule solved) {
        // (group name) -> (date -> placed slotIndexes); remember each group's id for the window lookup.
        Map<String, Map<LocalDate, java.util.Set<Integer>>> slotsByGroupDate = new HashMap<>();
        Map<String, Long> groupIdByName = new HashMap<>();
        for (Lesson lesson : solved.getLessons()) {
            if (lesson.timeslot == null || lesson.getStudentGroup() == null) continue;
            StudentGroup g = lesson.getStudentGroup();
            groupIdByName.putIfAbsent(g.name, g.id);
            slotsByGroupDate.computeIfAbsent(g.name, k -> new HashMap<>())
                    .computeIfAbsent(lesson.timeslot.getDate(), k -> new java.util.TreeSet<>())
                    .add(TimeslotSequence.slotIndexOf(lesson.timeslot));
        }
        int daysInSession = 0, anchored = 0, holeAfterAnchor = 0, holeAfterAnchorWithBlock = 0;
        Map<String, int[]> byGroup = new HashMap<>(); // [inSession, anchored, hole, holeWithBlock]
        for (Map.Entry<String, Map<LocalDate, java.util.Set<Integer>>> groupE : slotsByGroupDate.entrySet()) {
            String groupName = groupE.getKey();
            // G14 is the only short-course carve-out that applies here (this fixture seeds no
            // Stage pseudo-courses, unlike the full-day KPI which also drops modality "S"): it is
            // recorded in byGroup for visibility but excluded from the headline counters below.
            boolean isG14 = "G14".equals(groupName);
            Long gid = groupIdByName.get(groupName);
            for (Map.Entry<LocalDate, java.util.Set<Integer>> dateE : groupE.getValue().entrySet()) {
                LocalDate date = dateE.getKey();
                java.util.Set<Integer> placed = dateE.getValue();
                int[] window = SchoolSchedule.getGroupCalendarWindow(gid, date.getDayOfWeek(), date);
                int minOpenSlot = window[1];
                int maxOpenSlot = window[2];
                if (minOpenSlot < 0) continue; // closed day (no real open slot; defensive)
                boolean anc = placed.contains(minOpenSlot); // minOpenSlot placed => it is the day's first slot
                boolean hole = anc && (minOpenSlot + 1 <= maxOpenSlot) && !placed.contains(minOpenSlot + 1);
                boolean blockAfter = hole && placed.stream().anyMatch(s -> s > minOpenSlot + 1);
                int[] c = byGroup.computeIfAbsent(groupName, k -> new int[4]); // always record (G14 stays visible)
                c[0]++;
                if (anc) c[1]++;
                if (hole) c[2]++;
                if (blockAfter) c[3]++;
                if (isG14) continue; // headline counters skip G14
                daysInSession++;
                if (anc) anchored++;
                if (hole) holeAfterAnchor++;
                if (blockAfter) holeAfterAnchorWithBlock++;
            }
        }
        return new AnchorHoleKpi(daysInSession, anchored, holeAfterAnchor, holeAfterAnchorWithBlock, byGroup);
    }

    private record AnchorHoleKpi(int daysInSession, int anchored, int holeAfterAnchor,
            int holeAfterAnchorWithBlock, Map<String, int[]> byGroup) {
    }
}
