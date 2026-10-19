package com.school.planner.solver;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;
import com.school.planner.util.StageEligibilityUtil;

/**
 * Pure, reusable derivation of TASK-381 "L2"'s weekly template — extracted
 * from {@link DayClusteredConstructionPhase#stampWeeklyTemplate}'s "Step 1"
 * (TASK-413) so it can be computed in TWO places from IDENTICAL pre-solve
 * state:
 * <ol>
 *   <li>inside the construction phase (as before), and</li>
 *   <li>PRE-SOLVE in {@code ScheduleService.refreshSolverStatics}, to publish
 *       the template + per-course scarcity weights that back the new M32
 *       (Template deviation) constraint.</li>
 * </ol>
 *
 * <p><b>Why the template must be computed pre-solve, not captured mid-solve:</b>
 * a constraint's static reference that changes halfway through a solve corrupts
 * the incremental score (already-scored matches are not re-evaluated) — see
 * {@code claudedocs/design/task-413-quasi-pinning-graduato-2026-07-24.md}. The
 * template does NOT depend on the construction: {@link #plan} derives it purely
 * from problem facts that are identical between the DB-loaded pre-solve schedule
 * and the phase's working (planning-cloned) solution — timeslots, holidays,
 * stage patterns, and the pinned/unpinned Lesson population. Because this is a
 * deterministic pure function keyed by ids (never by object identity — the phase
 * runs on cloned {@code Lesson}s while {@code Course}/{@code StudentGroup}/{@code
 * Timeslot} are shared problem facts), both call sites produce a BYTE-IDENTICAL
 * template. {@code WeeklyTemplateConstructionRealSeedTest} pins the resulting
 * recurrence/stamp stats and must stay unchanged.
 *
 * <p>Depends on the same {@code SchoolSchedule} static snapshots the phase's
 * Step 1 already read ({@link SchoolSchedule#getGroupCalendarWindow}, {@link
 * SchoolSchedule#getDailyQuota}, {@link SchoolSchedule#getExpectedHoursRow},
 * {@link SchoolSchedule#getTeacherAvailabilitiesStatic}, …); the pre-solve
 * caller invokes it only AFTER those statics are refreshed, so both sides read
 * the same data.
 */
public final class WeeklyTemplatePlanner {

    private final Set<LocalDate> holidays;
    private final List<StagePattern> stagePatterns;
    private final boolean referenceMode;

    private WeeklyTemplatePlanner(Set<LocalDate> holidays, List<StagePattern> stagePatterns, boolean referenceMode) {
        this.holidays = holidays != null ? holidays : Set.of();
        this.stagePatterns = stagePatterns != null ? stagePatterns : List.of();
        this.referenceMode = referenceMode;
    }

    /**
     * CH mode — builds the weekly template from the population the construction
     * phase actually templates: unpinned lessons that are NOT yet placed
     * ({@code timeslot == null}). Returns an empty (name-ordered) map — leaving
     * M32 inert — when there is no such lesson (e.g. a fully-pinned golden import,
     * OR a fully-solved state where every unpinned lesson already has a timeslot).
     *
     * <p>This is the CH phase's correct semantics: template only what remains to
     * be placed. It stays BYTE-IDENTICAL and the phase keeps calling it; on a
     * fresh pre-solve state (every unpinned lesson unassigned) it coincides with
     * {@link #buildReference} by construction.
     *
     * @param lessons        every lesson (pinned and unpinned) of the schedule
     * @param timeslots      every timeslot problem fact
     * @param holidays       school-holiday dates (excluded from the "typical week")
     * @param stagePatterns  stage patterns (blackout dates skipped when counting active weeks)
     */
    public static Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> build(
            List<Lesson> lessons, List<Timeslot> timeslots, Set<LocalDate> holidays, List<StagePattern> stagePatterns) {
        return new WeeklyTemplatePlanner(holidays, stagePatterns, false).plan(lessons, timeslots);
    }

    /**
     * REFERENCE mode — builds the M32 reference template in an
     * assignment-agnostic way (TASK-416): the population is EVERY unpinned lesson
     * regardless of its current {@code timeslot}, and the occupancy/blocked-slot
     * maps are built ONLY from PINNED lessons (never from a solver-placed unpinned
     * lesson).
     *
     * <p><b>Why this exists (the bug it fixes):</b> M32's reference template must
     * stay FIXED for the whole life of a schedule — it is the pre-solve template
     * every unpinned lesson is measured against. The CH-mode {@link #build}
     * selects its population by {@code timeslot == null}, which is correct for the
     * construction phase (template only what's left to place) but wrong as an M32
     * reference: on a solved (or partially solved) state every unpinned lesson
     * already has a timeslot, so the CH-mode population is empty → empty template →
     * M32 silently scores 0 in any post-solve re-analysis and would be inert on a
     * re-solve of an already-solved profile. Reference mode ignores the current
     * assignment, so it reproduces the SAME template regardless of solve progress.
     *
     * <p><b>Stability guarantee (why the two modes agree where it matters):</b> on
     * a fresh pre-solve state (all unpinned lessons unassigned) reference mode and
     * CH mode coincide by construction — the population is the same set and no
     * unpinned lesson contributes to occupancy either way. On a solved/partial
     * state, reference mode rebuilds EXACTLY the fresh pre-solve template (for the
     * same pinned set) because it treats every already-placed unpinned lesson as if
     * it were still to be placed and never lets a solver-placed lesson block a
     * template cell. So publishing the reference-mode template pre-solve and
     * re-publishing it post-solve yields the identical map.
     *
     * @param lessons        every lesson (pinned and unpinned) of the schedule
     * @param timeslots      every timeslot problem fact
     * @param holidays       school-holiday dates (excluded from the "typical week")
     * @param stagePatterns  stage patterns (blackout dates skipped when counting active weeks)
     */
    public static Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> buildReference(
            List<Lesson> lessons, List<Timeslot> timeslots, Set<LocalDate> holidays, List<StagePattern> stagePatterns) {
        return new WeeklyTemplatePlanner(holidays, stagePatterns, true).plan(lessons, timeslots);
    }

    /**
     * PAIR-CELLS mode (TASK-576 F4, PROTOTYPE behind {@code
     * planner.construction.pair-cells.enabled}, default OFF): same contract and same
     * pre-work as {@link #build} (population, occupancy, blocked slots, caps), but the
     * weekly template is built by {@link PairCellsTemplateBuilder}: the (teacher,
     * group) PAIR is the planning unit and each pair with a declared golden form
     * (seed/golden-pair-cells.csv) gets its practiced cells instead of a greedy search.
     *
     * <p>The mirror of {@code plan()} below is DELIBERATE: this is a flag-gated
     * experiment, not a refactor; the duplication lives until the prototype is either
     * ratified (then the two plans merge) or dropped.
     */
    public static Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> buildPairCells(
            List<Lesson> lessons, List<Timeslot> timeslots, Set<LocalDate> holidays, List<StagePattern> stagePatterns) {
        return new WeeklyTemplatePlanner(holidays, stagePatterns, false).planPairCells(lessons, timeslots);
    }

    private Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> plan(
            List<Lesson> lessons, List<Timeslot> timeslots) {

        // Population: unpinned lessons grouped by student group. CH mode takes
        // only the not-yet-placed ones (timeslot == null) — identical to
        // DayClusteredConstructionPhase.changeWorkingSolution's unassigned-lesson
        // selection + groupByStudentGroup (TASK-413 extraction). REFERENCE mode
        // (TASK-416) takes EVERY unpinned lesson regardless of its current
        // timeslot, so the M32 reference template is the same before and after a
        // solve — see buildReference's javadoc.
        Map<StudentGroup, List<Lesson>> groupedByStudentGroup = new HashMap<>();
        if (lessons != null) {
            for (Lesson lesson : lessons) {
                if (lesson.isPinned()) continue;
                if (!referenceMode && lesson.timeslot != null) continue;
                StudentGroup group = lesson.getStudentGroup();
                if (group != null) {
                    groupedByStudentGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(lesson);
                }
            }
        }
        Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> empty =
                new TreeMap<>(Comparator.comparing(g -> g.name));
        if (groupedByStudentGroup.isEmpty()) {
            return empty;
        }

        // Every already-timeslotted lesson (pinned or pre-placed during a
        // re-solve) — mirrors DayClusteredConstructionPhase.initializeTrackingMaps
        // for the two maps the blocked-slot builders read. Keyed by teacher/group
        // id and holding shared Timeslot problem facts, so the result is identical
        // whether called on the DB-loaded schedule or the phase's clone. REFERENCE
        // mode (TASK-416) counts ONLY pinned lessons here: a solver-placed unpinned
        // lesson must never block a template cell, or the reference template would
        // drift with solve progress instead of staying fixed to the pre-solve one.
        Map<Long, Set<Timeslot>> teacherAssignedTimeslots = new HashMap<>();
        Map<Long, Set<Timeslot>> groupAssignedTimeslots = new HashMap<>();
        if (lessons != null) {
            for (Lesson lesson : lessons) {
                if (lesson.timeslot == null) continue;
                if (referenceMode && !lesson.isPinned()) continue;
                if (lesson.getTeacher() != null) {
                    teacherAssignedTimeslots
                            .computeIfAbsent(lesson.getTeacher().id, k -> new HashSet<>())
                            .add(lesson.timeslot);
                }
                if (lesson.getStudentGroup() != null) {
                    groupAssignedTimeslots
                            .computeIfAbsent(lesson.getStudentGroup().id, k -> new HashSet<>())
                            .add(lesson.timeslot);
                }
            }
        }

        // O(1) (date, slotIndex) -> Timeslot index, then the sorted school-date
        // list and per-weekday date buckets (holidays excluded; weekends never
        // generate a Timeslot in the first place).
        Map<LocalDate, Timeslot[]> timeslotsByDateSlot = new HashMap<>();
        if (timeslots != null) {
            for (Timeslot ts : timeslots) {
                int slotIdx = TimeslotSequence.slotIndexOf(ts);
                if (slotIdx < 0) continue;
                timeslotsByDateSlot
                        .computeIfAbsent(ts.getDate(), d -> new Timeslot[TimeslotSequence.SLOTS_PER_DAY])[slotIdx] = ts;
            }
        }
        List<LocalDate> allSchoolDates = new ArrayList<>(timeslotsByDateSlot.keySet());
        Collections.sort(allSchoolDates);
        if (allSchoolDates.isEmpty()) {
            return empty;
        }
        Map<DayOfWeek, List<LocalDate>> datesByWeekday = new EnumMap<>(DayOfWeek.class);
        for (LocalDate date : allSchoolDates) {
            if (holidays.contains(date)) continue;
            datesByWeekday.computeIfAbsent(date.getDayOfWeek(), d -> new ArrayList<>()).add(date);
        }

        List<StudentGroup> groups = new ArrayList<>(groupedByStudentGroup.keySet());
        groups.sort(Comparator.comparing(g -> g.name));

        // ── Step 1: build the weekly template ──
        List<WeeklyTemplateBuilder.GroupWeeklyShape> shapes = new ArrayList<>();
        Map<StudentGroup, WeeklyTemplateBuilder.GroupWeeklyShape> shapeByGroup = new HashMap<>();
        for (StudentGroup group : groups) {
            WeeklyTemplateBuilder.GroupWeeklyShape shape = buildGroupWeeklyShape(group, datesByWeekday);
            shapes.add(shape);
            shapeByGroup.put(group, shape);
        }
        Map<Course, Integer> weeklyTargets = buildWeeklyTargets(groupedByStudentGroup, allSchoolDates, shapeByGroup);
        TeacherBlockedSlots teacherBlockedSlots =
                buildBlockedTeacherSlots(groups, groupedByStudentGroup, teacherAssignedTimeslots, datesByWeekday, timeslotsByDateSlot);
        Set<WeeklyTemplateBuilder.GroupSlot> blockedGroupSlots =
                buildBlockedGroupSlots(groups, groupAssignedTimeslots, datesByWeekday, timeslotsByDateSlot);

        Map<StudentGroup, Integer> maxHoursPerDay = new HashMap<>();
        for (StudentGroup group : groups) {
            maxHoursPerDay.put(group, group.maxHoursPerDay);
        }
        Map<Long, Integer> teacherDailyCap = new HashMap<>();
        for (List<Lesson> groupLessons : groupedByStudentGroup.values()) {
            for (Lesson lesson : groupLessons) {
                Teacher teacher = lesson.getTeacher();
                if (teacher != null && !teacher.pseudo) {
                    teacherDailyCap.put(teacher.id, teacher.getEffectiveMaxDailyHours());
                }
            }
        }

        // S2 SPIKE (flag-gated default OFF in WeeklyTemplateBuilder): the mono-room tags
        // (a tag mapped to exactly 1 assignable room), resolved from the school's published
        // per-tag room capacity (ScheduleService.refreshSolverStatics -> SchoolSchedule
        // .roomCapacityForTag). Passed to the builder so a tagged course can, when the flag is
        // ON, prefer a template cell no other group's same-tag course already took (the
        // acconciatura-style room pile-up). Empty when the capacity map is not wired (a unit
        // test with no rooms), so the feature stays inert even with the flag ON.
        Set<String> monoRoomTags = new TreeSet<>();
        for (Course c : weeklyTargets.keySet()) {
            if (c.requiredRoomTags == null) continue;
            for (String tag : c.requiredRoomTags) {
                if (SchoolSchedule.roomCapacityForTag(tag) == 1) monoRoomTags.add(tag);
            }
        }

        return WeeklyTemplateBuilder.build(
                shapes, weeklyTargets, teacherBlockedSlots.unavailableSlots(), teacherBlockedSlots.teachingSlots(),
                blockedGroupSlots, maxHoursPerDay, teacherDailyCap, monoRoomTags);
    }

    /**
     * PAIR-CELLS plan (TASK-576 F4): the mirror of {@link #plan}'s pre-work feeding
     * {@link PairCellsTemplateBuilder} instead of {@link WeeklyTemplateBuilder}.
     * Flag-gated prototype; see {@link #buildPairCells}.
     */
    private Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> planPairCells(
            List<Lesson> lessons, List<Timeslot> timeslots) {

        // Population: CH mode (unpinned, not yet placed), same selection as plan()
        Map<StudentGroup, List<Lesson>> groupedByStudentGroup = new HashMap<>();
        if (lessons != null) {
            for (Lesson lesson : lessons) {
                if (lesson.isPinned()) continue;
                if (lesson.timeslot != null) continue;
                StudentGroup group = lesson.getStudentGroup();
                if (group != null) {
                    groupedByStudentGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(lesson);
                }
            }
        }
        Map<StudentGroup, Map<DayOfWeek, List<WeeklyTemplateBuilder.TemplateCell>>> empty =
                new TreeMap<>(Comparator.comparing(g -> g.name));
        if (groupedByStudentGroup.isEmpty()) {
            return empty;
        }

        // Occupancy from every already-placed lesson (pinned or pre-placed), CH mode
        Map<Long, Set<Timeslot>> teacherAssignedTimeslots = new HashMap<>();
        Map<Long, Set<Timeslot>> groupAssignedTimeslots = new HashMap<>();
        if (lessons != null) {
            for (Lesson lesson : lessons) {
                if (lesson.timeslot == null) continue;
                if (lesson.getTeacher() != null) {
                    teacherAssignedTimeslots
                            .computeIfAbsent(lesson.getTeacher().id, k -> new HashSet<>())
                            .add(lesson.timeslot);
                }
                if (lesson.getStudentGroup() != null) {
                    groupAssignedTimeslots
                            .computeIfAbsent(lesson.getStudentGroup().id, k -> new HashSet<>())
                            .add(lesson.timeslot);
                }
            }
        }

        Map<LocalDate, Timeslot[]> timeslotsByDateSlot = new HashMap<>();
        if (timeslots != null) {
            for (Timeslot ts : timeslots) {
                int slotIdx = TimeslotSequence.slotIndexOf(ts);
                if (slotIdx < 0) continue;
                timeslotsByDateSlot
                        .computeIfAbsent(ts.getDate(), d -> new Timeslot[TimeslotSequence.SLOTS_PER_DAY])[slotIdx] = ts;
            }
        }
        List<LocalDate> allSchoolDates = new ArrayList<>(timeslotsByDateSlot.keySet());
        Collections.sort(allSchoolDates);
        if (allSchoolDates.isEmpty()) {
            return empty;
        }
        Map<DayOfWeek, List<LocalDate>> datesByWeekday = new EnumMap<>(DayOfWeek.class);
        for (LocalDate date : allSchoolDates) {
            if (holidays.contains(date)) continue;
            datesByWeekday.computeIfAbsent(date.getDayOfWeek(), d -> new ArrayList<>()).add(date);
        }

        List<StudentGroup> groups = new ArrayList<>(groupedByStudentGroup.keySet());
        groups.sort(Comparator.comparing(g -> g.name));

        // The pair builder plans by COURSE: distinct courses of the population, plus
        // the same caps plan() derives from it (group maxHoursPerDay, teacher daily cap)
        List<Course> courses = new ArrayList<>();
        Set<Long> seenCourseIds = new HashSet<>();
        Map<StudentGroup, Integer> maxHoursPerDay = new HashMap<>();
        Map<Long, Integer> teacherDailyCap = new HashMap<>();
        for (StudentGroup group : groups) {
            maxHoursPerDay.put(group, group.maxHoursPerDay);
            for (Lesson lesson : groupedByStudentGroup.get(group)) {
                Course course = lesson.course;
                if (course != null && course.teacher != null && course.studentGroup != null
                        && seenCourseIds.add(course.id)) {
                    courses.add(course);
                }
                Teacher teacher = lesson.getTeacher();
                if (teacher != null && !teacher.pseudo) {
                    teacherDailyCap.put(teacher.id, teacher.getEffectiveMaxDailyHours());
                }
            }
        }

        TeacherBlockedSlots teacherBlockedSlots =
                buildBlockedTeacherSlots(groups, groupedByStudentGroup, teacherAssignedTimeslots, datesByWeekday, timeslotsByDateSlot);
        Set<WeeklyTemplateBuilder.GroupSlot> blockedGroupSlots =
                buildBlockedGroupSlots(groups, groupAssignedTimeslots, datesByWeekday, timeslotsByDateSlot);

        // Active weeks: distinct ISO weeks of the non-holiday school dates (the
        // annual-to-weekly divisor; floor 1 like the builder's own weeklyHoursOf)
        Set<Integer> distinctWeeks = new HashSet<>();
        for (List<LocalDate> dates : datesByWeekday.values()) {
            for (LocalDate date : dates) {
                distinctWeeks.add(TimeslotSequence.isoWeekKey(date));
            }
        }
        int activeWeeks = Math.max(1, distinctWeeks.size());

        Map<PairCellsTemplateBuilder.PairKey, PairCellsTemplateBuilder.PairForm> goldenForms =
                PairCellsTemplateBuilder.loadGoldenPairForms(courses);

        return PairCellsTemplateBuilder.build(
                courses, goldenForms,
                teacherBlockedSlots.unavailableSlots(), teacherBlockedSlots.teachingSlots(),
                blockedGroupSlots, maxHoursPerDay, teacherDailyCap, activeWeeks);
    }

    /**
     * Resolves one group's typical weekly calendar/quota shape (TASK-381
     * "L2") by taking the MODAL [minSlot,maxSlot] window and, for quota
     * groups, the modal non-zero lab/aula hour budgets per weekday across
     * every real school date - not every date's exact value, since rare
     * calendar exceptions (closures, one-off overrides) shouldn't distort
     * the representative week the template is built from. Holidays are
     * excluded from the tally entirely (never "typical").
     *
     * <p><b>Why a per-date scan and not one direct weekly-rule lookup:</b>
     * {@code GroupCalendarRule}/{@link SchoolSchedule#getGroupCalendarWindow}
     * resolve DATE-SCOPED overrides ({@code effectiveFrom}/{@code
     * effectiveTo}, e.g. the Monday ramp that opens a group's very first
     * school day at a later start slot than every other Monday) - there is no
     * date-independent "just the weekly rule" API to call instead, because
     * for a ramp weekday the "weekly rule" genuinely differs by date. Reading
     * a single representative date would silently pick up whichever variant
     * that date happens to hit (the ramp exception, if unlucky). The modal
     * vote across every real date already lands on the right value (the ramp
     * is one date out of ~34 per weekday, so it never wins the vote) without
     * needing to special-case it here.
     */
    private WeeklyTemplateBuilder.GroupWeeklyShape buildGroupWeeklyShape(StudentGroup group,
            Map<DayOfWeek, List<LocalDate>> datesByWeekday) {
        boolean quotaGroup = SchoolSchedule.isQuotaGroup(group.id);
        WeeklyTemplateBuilder.GroupWeeklyShape shape = new WeeklyTemplateBuilder.GroupWeeklyShape(group, quotaGroup);

        for (DayOfWeek day : WeeklyTemplateBuilder.WEEKDAYS) {
            Map<Integer, Integer> windowFrequency = new TreeMap<>();
            Map<Integer, Integer> labFrequency = new TreeMap<>();
            Map<Integer, Integer> aulaFrequency = new TreeMap<>();
            Map<Integer, Integer> expectedFrequency = new TreeMap<>(); // TASK-385
            for (LocalDate date : datesByWeekday.getOrDefault(day, List.of())) {
                int[] window = SchoolSchedule.getGroupCalendarWindow(group.id, day, date);
                if (window[1] < 0) continue; // closed that date - not "typical"
                windowFrequency.merge(window[1] * 10 + window[2], 1, Integer::sum);
                DailyActivityQuota quota = SchoolSchedule.getDailyQuota(group.id, date);
                boolean stageOnly = stageWeekdayVoteExclusionEnabled() && stageOnlyDate(quota);
                if (quotaGroup && !stageOnly) {
                    int lab = quota != null ? quota.labHours : 0;
                    int aula = quota != null ? quota.aulaHours : 0;
                    // Tally EVERY sample, including zero: a weekday that's
                    // genuinely a non-lab (or non-aula) day the vast majority
                    // of the time must let 0 win the majority vote too, or a
                    // couple of anomalous non-zero outlier dates would be the
                    // only candidates mostFrequentKey ever sees and "win" by
                    // default even though they're not typical at all.
                    labFrequency.merge(lab, 1, Integer::sum);
                    aulaFrequency.merge(aula, 1, Integer::sum);
                }
                // TASK-385: modal per-date expected-hours fact, capped by that
                // SAME date's calendar-open count (window[0], per
                // getGroupCalendarWindow's own {openSlotCount,min,max} contract) -
                // the Q16 rule (G14's sheet gives it days its Thu-Fri
                // calendar doesn't have; here that manifests as the date never
                // reaching this tally at all, since window[1] < 0 already
                // skipped it above). Only tallied when a fact exists at all -
                // an absent row must not silently vote for a modal target of 0.
                // Stage-exclusive (aula+lab only) - same basis M20 and the stamp
                // fill goal use.
                DailyActivityQuota expectedRow = SchoolSchedule.getExpectedHoursRow(group.id, date);
                if (expectedRow != null && !(stageWeekdayVoteExclusionEnabled() && stageOnlyDate(expectedRow))) {
                    int expected = expectedRow.aulaHours + expectedRow.labHours;
                    expectedFrequency.merge(Math.min(expected, window[0]), 1, Integer::sum);
                }
            }
            Integer modalWindowCode = mostFrequentKey(windowFrequency);
            if (modalWindowCode != null) {
                shape.setWindow(day, modalWindowCode / 10, modalWindowCode % 10);
            }
            if (quotaGroup) {
                Integer modalLab = mostFrequentKey(labFrequency);
                Integer modalAula = mostFrequentKey(aulaFrequency);
                shape.setQuotaCaps(day, modalLab != null ? modalLab : 0, modalAula != null ? modalAula : 0);
            }
            Integer modalExpected = mostFrequentKey(expectedFrequency); // TASK-385 (stage dates excluded above when armed)
            if (modalExpected != null) {
                shape.setDailyTarget(day, modalExpected);
            }
        }
        return shape;
    }

    /**
     * TASK-600 stage-weekday vote exclusion: a stage-only date (quota row with
     * stageHours &gt; 0 and aula = lab = 0) must NOT vote in the ordinary weekly
     * shape's modal tallies. Stage dates legitimately carry aula=0 because the
     * group is elsewhere that day; counting them made stage-MAJORITY weekdays
     * collapse to cap 0 forever (the measured case: G12 Friday, 18 stage
     * dates vs 12 ordinary dates declaring 4-5h -> no template cell ever -> the
     * 12 declared Fridays stayed empty, the census's NEVER_ATTEMPTED). The stamp
     * already declines a template cell per-date on the actual stage dates
     * (canPlaceLesson + the per-date expected-hours cap), so the ordinary
     * minority's cells place only where the date declares ordinary hours.
     *
     * <p>Blast radius on the 2026/27 seed (mechanically recomputed): exactly TWO
     * aula modals change, G12 Friday 0-&gt;4 and G11 Thursday 0-&gt;4 - both
     * the real pathology. The first bench attempt (modal over non-zero samples,
     * no stage filter) armed 11 group-weekday pairs including 1-date outliers
     * (G13 Friday 1/34) and moved the construction's under-quota signature
     * 41 -&gt; 57: the stage filter, not a minority threshold, is the honest
     * discriminator. DEFAULT OFF until the bench gate; read LIVE per build like
     * the sibling construction flags.
     */
    static boolean stageWeekdayVoteExclusionEnabled() {
        String v = System.getProperty("planner.construction.template-ignore-stage-weekdays.enabled");
        return v != null && !v.equalsIgnoreCase("false");
    }

    /** True when {@code quota} is a stage-only date (stage hours and no ordinary
     * hours): not evidence about the ordinary weekly shape either way. Delegates to
     * the entity's own single decision method (shared with M39, TASK-604). */
    private static boolean stageOnlyDate(DailyActivityQuota quota) {
        return DailyActivityQuota.isStageOnlyDay(quota);
    }

    /**
     * Key with the highest frequency, ties broken by natural (ascending) order for
     * determinism ({@code freq} must be sorted, e.g. a TreeMap). Package-private so the
     * construction phase's TASK-432 profile-following pre-pass can reuse it for its modal
     * weekday-quota computation instead of duplicating the helper.
     */
    static Integer mostFrequentKey(Map<Integer, Integer> freq) {
        Integer best = null;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : freq.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    /**
     * Round-robin allocation of each course's remaining unpinned hours over
     * its group's "active weeks" (weeks with at least one calendar-open,
     * non-holiday, non-stage-blackout day for the group - {@link
     * #isDateAvailable} plus the group's own calendar window). Known
     * simplification: {@link #isDateAvailable} treats any stage-blackout
     * date as inactive regardless of course type, so a group mixing
     * professionalizzanti and non-prof courses gets a slightly LOWER active-
     * week count than professionalizzanti courses could actually use -
     * pushing their weekly target (and thus template pressure) a little
     * high. Harmless for correctness (the builder's own daily/weekly caps
     * still bound every placement); only costs template quality on the
     * handful of stage-window groups.
     *
     * <p><b>Micro-course policy (review fix):</b> a course whose remaining
     * hours can't fill even half the group's active weeks gets a
     * {@code weeklyTarget} of 0 instead - such a course would otherwise
     * reserve a permanent weekly template cell, exhaust it after 1-2 weeks,
     * and deadlock that cell for the rest of the year against a course that
     * genuinely recurs every week. A 0 target is skipped entirely by {@link
     * WeeklyTemplateBuilder#build} (it only templates positive targets), so
     * the course's hours go straight to the round-robin/force-place repair
     * tail instead.
     *
     * <p><b>Largest-remainder allocation (TASK-385):</b> for a group whose
     * {@link WeeklyTemplateBuilder.GroupWeeklyShape} carries an expected-hours
     * weekly total ({@link WeeklyTemplateBuilder.GroupWeeklyShape#weeklyTargetTotal()}),
     * the surviving (non-micro) courses' targets are apportioned by {@link
     * WeeklyTemplateBuilder#allocateLargestRemainder} instead of each being
     * independently rounded - so the group's targets SUM to that weekly
     * total exactly (where demand allows), instead of the old per-course
     * {@code Math.round} which left systematic 1-2h daily holes across the
     * year. A group with no expected-hours fact at all (weekly total -1)
     * falls back to the old per-course rounding unchanged.
     */
    private Map<Course, Integer> buildWeeklyTargets(Map<StudentGroup, List<Lesson>> groupedByStudentGroup,
            List<LocalDate> allSchoolDates, Map<StudentGroup, WeeklyTemplateBuilder.GroupWeeklyShape> shapeByGroup) {
        Map<Course, Integer> targets = new TreeMap<>(Comparator.comparing(c -> c.id));
        for (Map.Entry<StudentGroup, List<Lesson>> entry : groupedByStudentGroup.entrySet()) {
            StudentGroup group = entry.getKey();
            double activeWeeks = countActiveWeeks(group, allSchoolDates);
            Map<Course, Long> hoursByCourse = entry.getValue().stream()
                    .filter(l -> l.course != null)
                    .collect(Collectors.groupingBy(l -> l.course, Collectors.counting()));

            Map<Course, Integer> nonMicroHours = new LinkedHashMap<>();
            for (Map.Entry<Course, Long> courseEntry : hoursByCourse.entrySet()) {
                int hours = courseEntry.getValue().intValue();
                // Micro-course policy - see this method's own javadoc. Uses
                // hours*2 < activeWeeks (not hours < activeWeeks/2.0) so the
                // left operand stays an exact integer against the double
                // activeWeeks, with no division at the half-boundary.
                if (hours * 2 < activeWeeks) {
                    targets.put(courseEntry.getKey(), 0);
                } else {
                    nonMicroHours.put(courseEntry.getKey(), hours);
                }
            }

            WeeklyTemplateBuilder.GroupWeeklyShape shape = shapeByGroup.get(group);
            int weeklyTotal = shape != null ? shape.weeklyTargetTotal() : -1;
            if (weeklyTotal > 0) {
                targets.putAll(WeeklyTemplateBuilder.allocateLargestRemainder(nonMicroHours, activeWeeks, weeklyTotal));
            } else {
                for (Map.Entry<Course, Integer> nonMicroEntry : nonMicroHours.entrySet()) {
                    int hours = nonMicroEntry.getValue();
                    // Per-week target floored at 1; narrowing to int is safe (bounded by course hours).
                    targets.put(nonMicroEntry.getKey(), (int) Math.max(1L, Math.round(hours / activeWeeks)));
                }
            }
        }
        return targets;
    }

    /**
     * Effective number of teaching weeks over which a group's course hours are
     * spread to derive a per-week target (TASK-410). A group WITHOUT stage-day
     * facts gets the unchanged integer count of distinct ISO weeks (year-based)
     * with at least one {@link #isOpenSchoolDay}.
     *
     * <p>A group WITH stage-day facts ({@link SchoolSchedule#groupHasStageFacts},
     * TASK-384) is <b>stage-aware</b>: it is not teaching during a stage week
     * (workplace internship), so each ISO week contributes only its
     * teaching-day fraction of open days - a fully-stage week drops out (0), a
     * mixed week counts fractionally. This stops course hours being smeared
     * across stage weeks the group never teaches in.
     *
     * <p><b>Pro-rate choice (day-count fraction):</b> {@code weekWeight =
     * teachingDays / openDaysInWeek}. Floored at 1.0 so the caller never
     * divides by &lt;1. Returns {@code double} because the pro-rated sum is
     * fractional.
     */
    private double countActiveWeeks(StudentGroup group, List<LocalDate> allSchoolDates) {
        if (!SchoolSchedule.groupHasStageFacts(group.id)) {
            Set<Integer> isoWeeks = new HashSet<>();
            for (LocalDate date : allSchoolDates) {
                if (!isOpenSchoolDay(date, group)) continue;
                isoWeeks.add(TimeslotSequence.isoWeekKey(date));
            }
            return Math.max(1, isoWeeks.size());
        }
        // Stage group: weight each ISO week by its teaching-day fraction so
        // fully-stage weeks drop out (0) and mixed weeks count fractionally.
        // tally[0]=teachingDays, tally[1]=openDays (every open school day).
        Map<Integer, int[]> weekTally = new HashMap<>();
        for (LocalDate date : allSchoolDates) {
            if (!isOpenSchoolDay(date, group)) continue;
            DailyActivityQuota row = SchoolSchedule.getExpectedHoursRow(group.id, date);
            boolean teachingDay = row == null || (row.aulaHours + row.labHours) > 0;
            int[] tally = weekTally.computeIfAbsent(TimeslotSequence.isoWeekKey(date), k -> new int[2]);
            tally[1]++;
            if (teachingDay) tally[0]++;
        }
        double effective = 0.0;
        for (int[] tally : weekTally.values()) {
            effective += (double) tally[0] / tally[1];
        }
        return Math.max(1.0, effective);
    }

    /**
     * Shared "calendar-open school day" gate both the stage-aware and the
     * legacy {@link #countActiveWeeks} paths use: not a holiday/weekend/stage
     * blackout ({@link #isDateAvailable}) AND the group's calendar window is
     * open that date ({@code window[1] >= 0}). Factored out so the two paths
     * cannot drift on what counts as an active day.
     */
    private boolean isOpenSchoolDay(LocalDate date, StudentGroup group) {
        if (!isDateAvailable(date, group)) return false;
        int[] window = SchoolSchedule.getGroupCalendarWindow(group.id, date.getDayOfWeek(), date);
        return window[1] >= 0;
    }

    /**
     * Teacher-agnostic date availability for the template path (the phase's
     * own {@code isDateAvailable} layers a teacher-vacation check on top of
     * the same shared gate) - delegates to {@link
     * StageEligibilityUtil#isOpenTeachingDate} so the two paths cannot drift.
     */
    private boolean isDateAvailable(LocalDate date, StudentGroup group) {
        return StageEligibilityUtil.isOpenTeachingDate(date, group, holidays, stagePatterns);
    }

    /**
     * True for a (weekday, slot) cell where {@code matcher} holds in a
     * MAJORITY of {@code datesThisWeekday} (strictly more than half) - the
     * shared "considered vs. matched" vote both {@link
     * #buildBlockedTeacherSlots} and {@link #buildBlockedGroupSlots} need. A
     * cell with zero considered dates (e.g. a weekday the group/teacher never
     * has a real Timeslot on) is never majority-blocked.
     */
    private static boolean isMajorityMatch(List<LocalDate> datesThisWeekday, Map<LocalDate, Timeslot[]> timeslotsByDateSlot,
            int slot, java.util.function.Predicate<Timeslot> matcher) {
        int considered = 0, matched = 0;
        for (LocalDate date : datesThisWeekday) {
            Timeslot[] daySlots = timeslotsByDateSlot.get(date);
            Timeslot ts = daySlots != null && slot < daySlots.length ? daySlots[slot] : null;
            if (ts == null) continue;
            considered++;
            if (matcher.test(ts)) matched++;
        }
        return considered > 0 && matched * 2 > considered;
    }

    /**
     * Split result of {@link #buildBlockedTeacherSlots}: BOTH subsets block
     * template placement, but only {@code teachingSlots} counts toward
     * {@link WeeklyTemplateBuilder}'s H7 daily-cap arithmetic (majority-
     * unavailable cells are not taught hours and must never count toward a
     * teacher's daily teaching cap).
     */
    private record TeacherBlockedSlots(
            Set<WeeklyTemplateBuilder.TeacherSlot> unavailableSlots,
            Set<WeeklyTemplateBuilder.TeacherSlot> teachingSlots) {
    }

    /**
     * Cells a teacher is majority-blocked on (weekday, slot), split by
     * reason: {@code unavailableSlots} (dated {@code TeacherAvailability}
     * VACATION/SICK, {@link Lesson#isTeacherUnavailable}) and {@code
     * teachingSlots} (already majority-occupied by that teacher's own
     * already-timeslotted lessons, e.g. recurring MODA shifts) - both read
     * from the passed-in tracking map (populated from every already-timeslotted
     * lesson - pinned OR previously placed during a re-solve).
     */
    private TeacherBlockedSlots buildBlockedTeacherSlots(List<StudentGroup> groups,
            Map<StudentGroup, List<Lesson>> groupedByStudentGroup, Map<Long, Set<Timeslot>> teacherAssignedTimeslots,
            Map<DayOfWeek, List<LocalDate>> datesByWeekday, Map<LocalDate, Timeslot[]> timeslotsByDateSlot) {
        Set<WeeklyTemplateBuilder.TeacherSlot> unavailableSlots = new HashSet<>();
        Set<WeeklyTemplateBuilder.TeacherSlot> teachingSlots = new HashSet<>();
        Set<Teacher> teachers = new TreeSet<>(Comparator.comparing(t -> t.id));
        for (StudentGroup group : groups) {
            for (Lesson lesson : groupedByStudentGroup.getOrDefault(group, List.of())) {
                if (lesson.getTeacher() != null) teachers.add(lesson.getTeacher());
            }
        }
        Map<Long, List<TeacherAvailability>> teacherAvail =
                SchoolSchedule.getTeacherAvailabilitiesStatic();

        for (Teacher teacher : teachers) {
            Set<Timeslot> alreadyPlacedSlots = teacherAssignedTimeslots.get(teacher.id);
            // Skip the second isMajorityMatch scan entirely for a teacher with
            // no already-placed slots at all - its matcher would return false
            // for every one of the ~34 dates in datesThisWeekday anyway.
            boolean hasPlaced = alreadyPlacedSlots != null && !alreadyPlacedSlots.isEmpty();
            for (DayOfWeek day : WeeklyTemplateBuilder.WEEKDAYS) {
                List<LocalDate> datesThisWeekday = datesByWeekday.getOrDefault(day, List.of());
                if (datesThisWeekday.isEmpty()) continue;

                for (int slot = 0; slot < TimeslotSequence.SLOTS_PER_DAY; slot++) {
                    boolean unavailableCell = isMajorityMatch(datesThisWeekday, timeslotsByDateSlot, slot,
                            ts -> Lesson.isTeacherUnavailable(ts, teacher.id, teacherAvail));
                    boolean teachingCell = hasPlaced && isMajorityMatch(datesThisWeekday, timeslotsByDateSlot, slot,
                            alreadyPlacedSlots::contains);
                    if (unavailableCell) {
                        unavailableSlots.add(new WeeklyTemplateBuilder.TeacherSlot(teacher.id, day, slot));
                    }
                    if (teachingCell) {
                        teachingSlots.add(new WeeklyTemplateBuilder.TeacherSlot(teacher.id, day, slot));
                    }
                }
            }
        }
        return new TeacherBlockedSlots(unavailableSlots, teachingSlots);
    }

    /**
     * Cells a group's OWN already-timeslotted lessons majority-occupy
     * (weekday, slot) - e.g. MODA lab shifts (pinned) or a re-solve's
     * already-placed unpinned lessons. Mirrors {@link #buildBlockedTeacherSlots}
     * exactly (sharing {@link #isMajorityMatch}), reading from the passed-in
     * group tracking map instead.
     */
    private Set<WeeklyTemplateBuilder.GroupSlot> buildBlockedGroupSlots(List<StudentGroup> groups,
            Map<Long, Set<Timeslot>> groupAssignedTimeslots,
            Map<DayOfWeek, List<LocalDate>> datesByWeekday, Map<LocalDate, Timeslot[]> timeslotsByDateSlot) {
        Set<WeeklyTemplateBuilder.GroupSlot> blocked = new HashSet<>();
        for (StudentGroup group : groups) {
            Set<Timeslot> alreadyPlacedSlots = groupAssignedTimeslots.get(group.id);
            if (alreadyPlacedSlots == null || alreadyPlacedSlots.isEmpty()) continue;

            for (DayOfWeek day : WeeklyTemplateBuilder.WEEKDAYS) {
                List<LocalDate> datesThisWeekday = datesByWeekday.getOrDefault(day, List.of());
                if (datesThisWeekday.isEmpty()) continue;

                for (int slot = 0; slot < TimeslotSequence.SLOTS_PER_DAY; slot++) {
                    if (isMajorityMatch(datesThisWeekday, timeslotsByDateSlot, slot, alreadyPlacedSlots::contains)) {
                        blocked.add(new WeeklyTemplateBuilder.GroupSlot(group.id, day, slot));
                    }
                }
            }
        }
        return blocked;
    }
}
