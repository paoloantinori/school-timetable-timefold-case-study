package com.school.planner.solver;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.StudentGroup;
import com.school.planner.service.ConstrainedTeacherAllocator;
import com.school.planner.util.SubjectNormalization;

/**
 * Pure-Java, deterministic builder for TASK-381 "L2" weekly template
 * construction: given each group's typical weekly calendar shape and each
 * course's weekly-hour target, greedily builds ONE representative school
 * week (Mon-Fri, {@code TimeslotSequence} slot indices) that {@link
 * DayClusteredConstructionPhase} then STAMPS across every matching school
 * date. Produces the golden's weekly recurrence "by construction" instead of
 * hoping local search discovers it (run 12/13 measured 16.9% unpinned
 * recurrence under the old day-clustered round-robin CH, vs the golden's
 * 41.5-72% - see {@code claudedocs/spalmone-2026-27/experiment-log.md}).
 *
 * <p>Deliberately has NO dependency on Timefold or {@code SchoolSchedule}'s
 * static VRP snapshots - every input (calendar windows, quota caps, blocked
 * teacher/group cells, weekly-hour targets) is resolved by the caller from
 * those sources and passed in as plain data, so this class is unit-testable
 * without a solver context. Correctness of the FINAL placement never depends
 * on this class being exactly right: {@link DayClusteredConstructionPhase}
 * re-validates every stamped cell against the real VRP
 * ({@code Lesson.getAvailableTimeslots()}) and in-memory conflict tracking
 * before actually placing a lesson - this builder only decides WHERE to
 * *try* first, so any imprecision here costs recurrence quality, never
 * correctness (H1/H2/H3/H5/H6/H20/quota safety nets are all downstream).</p>
 *
 * <p><b>Determinism:</b> every collection this class iterates for ordering
 * decisions is either a sorted {@code List} the caller supplies or a
 * {@code TreeMap}/{@code TreeSet} keyed by a stable, comparable key (never
 * raw {@code HashMap}/{@code HashSet} iteration) - required because the
 * solver demands reproducible construction (see the class's own callers for
 * the anti-drift rationale already established for M15/H16/H17).</p>
 */
public final class WeeklyTemplateBuilder {

    /** Monday-Friday, the only weekdays the 2026/27 school calendar ever uses. */
    public static final DayOfWeek[] WEEKDAYS = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    };

    private WeeklyTemplateBuilder() {
    }

    /**
     * TASK-480 independence gate: GRASP-style construction diversification (default OFF).
     * When enabled, the builder's tie-breaks (the per-course weekday scan start in the main
     * placement loop, and the free-slot pick in {@link #nextContiguousSlot}) draw from a SEEDED
     * {@link Random} instead of their deterministic choices, so N builds with N seeds produce N
     * genuinely independent templates. Default OFF = byte-identical to the deterministic builder,
     * so production construction and the M32 reference ({@link WeeklyTemplatePlanner#buildReference},
     * which delegates here) are unchanged. Read LIVE ({@code System.getProperty}) so a {@code -D}
     * on the {@code java -jar} command line, or a {@code System.setProperty} from a test, reaches
     * the app JVM, same pattern as {@code planner.template.room-aware.enabled}. The seeded Random
     * is created once per {@link #build} call (see {@link #newGraspRandom}) and threaded through
     * {@link PlacementState}; null when GRASP is off, which makes every gated branch take the
     * deterministic path.
     */
    public static boolean graspEnabled() {
        return Boolean.parseBoolean(System.getProperty("planner.construction.grasp.enabled", "false"));
    }

    public static long graspSeed() {
        return Long.getLong("planner.construction.grasp.seed", 0L);
    }

    /**
     * The seeded Random for one {@link #build} call, or null when GRASP is off. Created here (not
     * a static field) so each build reseeds deterministically from {@link #graspSeed} and two
     * builds never share mutable Random state, even if a future caller runs them concurrently.
     */
    private static Random newGraspRandom() {
        return graspEnabled() ? new Random(graspSeed()) : null;
    }

    /** One placed cell in the weekly template: {@code course} occupies slots [{@code slotIndex}, {@code slotIndex + length}). */
    public record TemplateCell(Course course, int slotIndex, int length) {
    }

    /** A (teacher, weekday, slot) cell — used both for the caller's "already blocked" input and this class's own bookkeeping. */
    public record TeacherSlot(Long teacherId, DayOfWeek day, int slot) {
    }

    /** A (group, weekday, slot) cell — same shape as {@link TeacherSlot}, for the group side. */
    public record GroupSlot(Long groupId, DayOfWeek day, int slot) {
    }

    /**
     * One group's typical weekly calendar/quota shape, resolved by the caller
     * from {@code SchoolSchedule}'s static snapshots (majority/modal pattern
     * over the real school year - see the class javadoc on why exact fidelity
     * to every date isn't required here).
     */
    public static final class GroupWeeklyShape {
        public final StudentGroup group;
        /** Typical [minSlot, maxSlot] per weekday (inclusive); {@code maxSlot < minSlot} means closed that weekday. */
        private final Map<DayOfWeek, int[]> window = new EnumMap<>(DayOfWeek.class);
        /** Only meaningful when {@link #quotaGroup}: modal non-zero labHours budget per weekday (0 = not usable for quota-bound-lab subjects). */
        private final Map<DayOfWeek, Integer> quotaLabCap = new EnumMap<>(DayOfWeek.class);
        /** Only meaningful when {@link #quotaGroup}: modal non-zero aulaHours budget per weekday (0 = not usable for ordinary subjects). */
        private final Map<DayOfWeek, Integer> quotaAulaCap = new EnumMap<>(DayOfWeek.class);
        /**
         * TASK-385: modal per-date expected-hours fact for this weekday
         * (calendario_class_days.csv/calendario_fc.csv, capped by the
         * calendar window - see {@code DayClusteredConstructionPhase#buildGroupWeeklyShape}),
         * or -1 if unset (no expected-hours data for this group/weekday, or
         * every sampled date was calendar-closed - e.g. G14's Monday, Q16).
         * -1 means "no target" - the day's cap falls back to the window/H11
         * cap alone, exactly as before this field existed.
         */
        private final Map<DayOfWeek, Integer> dailyTargetHours = new EnumMap<>(DayOfWeek.class);
        public final boolean quotaGroup;

        public GroupWeeklyShape(StudentGroup group, boolean quotaGroup) {
            this.group = group;
            this.quotaGroup = quotaGroup;
            for (DayOfWeek day : WEEKDAYS) {
                // Default = closed (maxSlot < minSlot, per windowFor()'s own
                // contract) until setWindow() is called for this weekday.
                // {-1,-1} would NOT satisfy that check (equal, not less-than)
                // and windowFor() would wrongly treat an un-set weekday as
                // "open" at slot -1 - caught by WeeklyTemplateConstructionRealSeedTest
                // (ArrayIndexOutOfBoundsException: -1) when a weekday never
                // matched any calendar-open date for a group.
                window.put(day, new int[] { 0, -1 });
                quotaLabCap.put(day, 0);
                quotaAulaCap.put(day, 0);
                dailyTargetHours.put(day, -1);
            }
        }

        public void setWindow(DayOfWeek day, int minSlot, int maxSlot) {
            window.put(day, new int[] { minSlot, maxSlot });
        }

        public void setQuotaCaps(DayOfWeek day, int labCap, int aulaCap) {
            quotaLabCap.put(day, labCap);
            quotaAulaCap.put(day, aulaCap);
        }

        /** TASK-385: sets this weekday's expected-hours target (see {@link #dailyTargetHours} field javadoc). */
        public void setDailyTarget(DayOfWeek day, int hours) {
            dailyTargetHours.put(day, hours);
        }

        /** Inclusive [minSlot, maxSlot] for this weekday, or {@code null} if the day is fully closed. */
        int[] windowFor(DayOfWeek day) {
            int[] w = window.get(day);
            if (w == null || w[1] < w[0]) return null;
            return w;
        }

        /** Per-course-kind cap for this weekday (quota groups only); {@link Integer#MAX_VALUE} for non-quota groups (unrestricted). */
        int capFor(DayOfWeek day, boolean quotaBoundLabSubject) {
            if (!quotaGroup) return Integer.MAX_VALUE;
            return quotaBoundLabSubject ? quotaLabCap.get(day) : quotaAulaCap.get(day);
        }

        /** TASK-385: this weekday's expected-hours target, or -1 if unset (see {@link #dailyTargetHours} field javadoc). */
        int dailyTargetHours(DayOfWeek day) {
            return dailyTargetHours.get(day);
        }

        /** TASK-385: sum of every weekday's {@link #dailyTargetHours}, or -1 if no weekday has a target set at all. */
        int weeklyTargetTotal() {
            int total = 0;
            boolean anySet = false;
            for (DayOfWeek day : WEEKDAYS) {
                int t = dailyTargetHours.get(day);
                if (t >= 0) {
                    total += t;
                    anySet = true;
                }
            }
            return anySet ? total : -1;
        }
    }

    /**
     * Builds one representative week: for every group with a non-empty
     * {@code weeklyTargets} entry, a {@code (weekday -> ordered cell list)}
     * map. Cells within one weekday are always contiguous from the window's
     * first open slot (compactness, H17) and never exceed
     * {@link TimeslotSequence#AFTERNOON_START_SLOT_INDEX} unless the group's
     * own demand cannot fit in the morning (M28 - afternoons are a last
     * resort, mirrored from the golden's own sparing use of them).
     *
     * @param shapes             one entry per group that has at least one course in {@code weeklyTargets}
     * @param weeklyTargets      course -> target hours/week (the caller's round-robin allocation of remaining unpinned hours over active weeks)
     * @param unavailableTeacherSlots cells a teacher is majority-unavailable (VACATION/SICK) - never assignable, never counted as a taught hour
     * @param teachingTeacherSlots  cells already majority-occupied by that teacher's own recurring PINNED lessons - never assignable, AND counted toward {@code teacherDailyCap} (H7) since they are real taught hours (review fix: a partial-day-vacation teacher was previously under-allocated because {@code unavailableTeacherSlots} cells were wrongly counted toward the same cap)
     * @param blockedGroupSlots  cells a group's own recurring pinned lessons (e.g. MODA shifts) already occupy - count toward the day's fill but are never reassigned
     * @param maxHoursPerDay     group -> H11 cap (null = unrestricted beyond the calendar window)
     * @param teacherDailyCap    teacherId -> H7 cap (missing/null = unrestricted, e.g. pseudo teachers)
     */
    public static Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> build(
            List<GroupWeeklyShape> shapes,
            Map<Course, Integer> weeklyTargetsIn,
            Set<TeacherSlot> unavailableTeacherSlots,
            Set<TeacherSlot> teachingTeacherSlots,
            Set<GroupSlot> blockedGroupSlots,
            Map<StudentGroup, Integer> maxHoursPerDay,
            Map<Long, Integer> teacherDailyCap,
            Set<String> monoRoomTags) {

        // Defensive copy: the reservation passes (reserveSlot0Anchors, reserveFirstHourBlock)
        // decrement targets as they commit cells, and callers may pass an immutable map (the
        // unit fixtures do, via Map.of). Nothing reads the caller's map after build() returns,
        // so build() keeps a non-mutating contract instead of demanding mutability.
        Map<Course, Integer> weeklyTargets = new HashMap<>(weeklyTargetsIn);

        Map<StudentGroup, GroupWeeklyShape> shapeByGroup = new HashMap<>();
        for (GroupWeeklyShape shape : shapes) {
            shapeByGroup.put(shape.group, shape);
        }

        // Live occupancy, seeded from the caller's "already blocked" input so
        // the greedy search never proposes a cell that collides with it.
        // teacherOccupiedBySlotKey blocks placement for BOTH unavailability and
        // existing teaching; teacherTeachingBySlotKey is the H7-cap-only subset
        // (existing teaching hours plus whatever this build() commits) - see
        // the two parameters' own javadoc above for why they're kept apart.
        Map<Long, TreeSet<Integer>> teacherOccupiedBySlotKey = new TreeMap<>();
        Map<Long, TreeSet<Integer>> teacherTeachingBySlotKey = new TreeMap<>();
        for (TeacherSlot ts : unavailableTeacherSlots) {
            teacherOccupiedBySlotKey
                    .computeIfAbsent(slotKeyId(ts.teacherId()), k -> new TreeSet<>())
                    .add(slotKeyOrdinal(ts.day(), ts.slot()));
        }
        for (TeacherSlot ts : teachingTeacherSlots) {
            teacherOccupiedBySlotKey
                    .computeIfAbsent(slotKeyId(ts.teacherId()), k -> new TreeSet<>())
                    .add(slotKeyOrdinal(ts.day(), ts.slot()));
            teacherTeachingBySlotKey
                    .computeIfAbsent(slotKeyId(ts.teacherId()), k -> new TreeSet<>())
                    .add(slotKeyOrdinal(ts.day(), ts.slot()));
        }
        Map<Long, TreeSet<Integer>> groupOccupiedBySlotKey = new TreeMap<>();
        for (GroupSlot gs : blockedGroupSlots) {
            groupOccupiedBySlotKey
                    .computeIfAbsent(slotKeyId(gs.groupId()), k -> new TreeSet<>())
                    .add(slotKeyOrdinal(gs.day(), gs.slot()));
        }
        // Per-(group,weekday) quota-kind cell counters (quota groups only) — index 0 = aula-kind, 1 = lab-kind.
        Map<StudentGroup, Map<DayOfWeek, int[]>> kindCounts = new HashMap<>();

        Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> result = new TreeMap<>(
                Comparator.comparing(g -> g.name));

        // Deterministic course list: every course with a positive target, sorted for reproducibility.
        List<Course> courses = new ArrayList<>();
        for (Map.Entry<Course, Integer> entry : weeklyTargets.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                courses.add(entry.getKey());
            }
        }
        // Stable SEMANTIC ordering (TASK-381 L2 review) - no raw course.id in
        // the key, so identical school data produces an identical courses
        // list regardless of which order the DB happened to auto-increment
        // Course rows in on a given reseed (the 75-81% run-to-run recurrence
        // variance this review measured). Descending weekly-target ("remaining
        // hours" proxy) first so big recurring courses claim cells before
        // smaller ones fight over the leftovers.
        //
        // TASK-577 9/09 Fase A (teacher-centric; design doc in
        // task577-results/teacher-centric-construction-design-2026-09-09.md):
        // when pair-major is ON, the primary key becomes the (teacher, group)
        // PAIR's total weekly hours instead of the single course's, and the
        // rotation below keys on the pair, so a teacher's whole package for a
        // group forms one compact weekly cell package - the golden's own
        // shape (170 pairs with >=20h: median 7 cells holding 80% of the
        // pair's hours). Opt-in DEFAULT OFF; OFF keeps this sort byte-identical.
        // MISURATO 9/09 (TeacherPairMajorBenchTest): CRITERIO PRIMARIO
        // FALLITO - concentrazione mediana top-3 0.83 -> 0.85 (+2pp, soglia
        // di successo +5pp) perche' il braccio OFF e' GIA' a 0.83, sopra il
        // riferimento golden 0.80: il builder attuale produce gia' la forma
        // per coppia al livello template, e il costo e' H34 a costruzione
        // 21 -> 28 (G13 +4). NON e' la leva per le celle vuote: resta
        // default-OFF come prototipo misurato (la falsificazione della
        // premessa e' il risultato: il gap qualita' non e' di shape-pair).
        boolean pairMajor = Boolean.getBoolean("planner.template.pair-major.enabled");
        Map<String, Integer> pairWeeklyHours = pairMajor ? new HashMap<>() : Map.of();
        if (pairMajor) {
            for (Course c : courses) {
                pairWeeklyHours.merge(pairKeyOf(c), weeklyTargets.getOrDefault(c, 0), Integer::sum);
            }
        }
        courses.sort(Comparator
                .comparing((Course c) -> pairMajor
                        ? pairWeeklyHours.getOrDefault(pairKeyOf(c), 0)
                        : weeklyTargets.getOrDefault(c, 0), Comparator.reverseOrder())
                .thenComparing(c -> c.studentGroup != null ? c.studentGroup.name : "")
                .thenComparing(c -> c.subject == null ? "" : c.subject)
                .thenComparing(c -> c.teacher != null ? c.teacher.name : ""));

        // Hardest-constrained-teacher-first: sort teachers by ascending free
        // capacity (5 weekdays x 8 slots minus already-blocked cells).
        Map<Long, List<Course>> coursesByTeacher = new TreeMap<>();
        for (Course c : courses) {
            // A single shared sentinel for every no-teacher course would make
            // unrelated courses (different groups, no real teacher in common)
            // block each other's cells in teacherOccupiedBySlotKey as if they
            // were the same person. Each no-teacher course gets its own
            // negative id instead (real teacher ids are positive IDENTITY
            // values, so no collision is possible).
            Long teacherId = teacherIdOf(c);
            coursesByTeacher.computeIfAbsent(teacherId, k -> new ArrayList<>()).add(c);
        }
        List<Long> teacherOrder = new ArrayList<>(coursesByTeacher.keySet());
        teacherOrder.sort(Comparator
                .comparingInt((Long teacherId) -> freeCapacity(teacherOccupiedBySlotKey.get(teacherId)))
                .thenComparing(teacherId -> teacherId));

        // Bundles the seven collections every tryPlaceChunk() call shares
        // unchanged within one build() invocation (only shape/group/course/
        // length/quotaBoundLab/startOffset/allowAfternoon/teacherId vary
        // per call).
        // S2 SPIKE (TASK-449 cure, flag-gated default OFF): a cross-group tracker of
        // (mono-tag, weekday, slot) cells already claimed, so a tagged course can prefer a
        // cell no other group's same-tag course took (the acconciatura-style room pile-up).
        // OFF (default): roomTagAware=false -> tryPlaceChunk runs today's single pass and the
        // tracker is never touched -> byte-identical template. Read LIVE (Boolean.getBoolean)
        // like the slot0 flag below, so a -D on java -jar reaches both this and the reference path.
        boolean roomTagAware = Boolean.getBoolean("planner.template.room-aware.enabled");
        // TASK-470 theory-spread prototype: read LIVE (opt-in, default OFF) so a -D on the
        // java -jar command line reaches the app JVM, threaded into PlacementState for
        // tryPlaceChunk's preference pass.
        boolean theorySpread = isTheorySpreadEnabled();
        Map<String, TreeSet<Integer>> tagOccupiedBySlotKey = new TreeMap<>();

        PlacementState state = new PlacementState(teacherOccupiedBySlotKey, teacherTeachingBySlotKey,
                groupOccupiedBySlotKey, kindCounts, maxHoursPerDay, teacherDailyCap,
                roomTagAware, theorySpread, monoRoomTags, tagOccupiedBySlotKey, result, newGraspRandom());

        // TASK-430 (candidate, flag-gated, default OFF): reserve slot-0 anchors distributively
        // BEFORE the per-teacher greedy, so a shared teacher is not monopolised at slot 0 by the
        // first-processed group (the cross-group H1 contention the TASK-426 day-start anatomy
        // isolated). Seeds the live maps the unchanged main placement reads, so no new conflicts.
        // NON-ADOPTED: measured by WeeklyTemplateConstructionRealSeedTest + kpi-daystart only.
        if (Boolean.getBoolean("planner.construction.slot0-balancing.enabled")) {
            reserveSlot0Anchors(state, shapeByGroup, weeklyTargets);
        }

        // TASK-451: coordinated first-hour slot0-1 block. Generalizes reserveSlot0Anchors
        // (1h at slot0, greedy) to a 2h slot0+slot1 block whose teacher is the GOLDEN's own
        // slot-0 anchor for (group, weekday) where stable (e.g. T08 -> G11 Friday).
        // Reserving the BLOCK closes the slot1 hole slot0-balancing left open (the "sposta
        // invece di coordinare" of verdict 30c); the golden preference is the cross-group
        // coordination (replicate the school's own anchor map). Seeds the live maps the
        // unchanged main placement reads, so no new conflicts. ADOPTED default ON (maintainer,
        // 2026-07-31, A/B n=2 stable: hard -173 -> -108/-109, boxed 3 -> 0, room-null
        // 167 -> 90, day-start 99.9%; accepted cost: 18 H17 holes, single G13-Friday
        // pattern with a downstream cure). Opt-out: -Dplanner.construction.first-hour-slot.enabled=false.
        if (isFirstHourSlotEnabled()) {
            reserveFirstHourBlock(state, shapeByGroup, weeklyTargets);
        }

        // TASK-577 Fase A: under pair-major + GRASP, the pair's shared rotation is
        // drawn ONCE per pair (memoized) so all its courses rotate together even
        // in the randomized attempts - without this the flag would be inert on
        // every GRASP attempt (each course would draw its own start again).
        Map<String, Integer> pairGraspRotation = new HashMap<>();
        for (Long teacherId : teacherOrder) {
            for (Course course : coursesByTeacher.get(teacherId)) {
                StudentGroup group = course.studentGroup;
                GroupWeeklyShape shape = group != null ? shapeByGroup.get(group) : null;
                if (shape == null) continue;
                boolean quotaBoundLab = shape.quotaGroup
                        && DailyActivityQuota.isQuotaBoundLabSubject(group.name, course.subject);

                int left = weeklyTargets.getOrDefault(course, 0);
                // Rotate each course's preferred weekday scan order so different
                // courses spread across the week instead of every course racing
                // to fill Monday first. Seeded from (group.name + subject) - a
                // stable semantic key, not course.id - so the offset is
                // identical across re-seeded profiles even when Course
                // auto-increment ids land differently (String.hashCode is
                // JVM-stable within one run; floorMod handles its sign).
                // TASK-577 Fase A: under pair-major the key is the PAIR, so the
                // teacher's whole package for the group rotates as one unit.
                String rotationKey = pairMajor
                        ? pairKeyOf(course)
                        : (group.name == null ? "" : group.name) + "|" + (course.subject == null ? "" : course.subject);
                // TASK-480 GRASP gate: under GRASP each course draws a fresh weekday scan start from
                // the seeded Random (per-course diversification), instead of the deterministic
                // semantic hash. Null (GRASP off) = the byte-identical floorMod(hash, 5) of today.
                Random grasp = state.graspRandom();
                int startOffset;
                if (grasp == null) {
                    startOffset = Math.floorMod(rotationKey.hashCode(), WEEKDAYS.length);
                } else if (pairMajor) {
                    startOffset = pairGraspRotation.computeIfAbsent(rotationKey,
                            k -> grasp.nextInt(WEEKDAYS.length));
                } else {
                    startOffset = grasp.nextInt(WEEKDAYS.length);
                }

                // Dormant sessionIndex/labBlockSize gap: this chunking is
                // block-size-blind (always 2h-then-1h), which only matches
                // reality because DataGenerator seeds labBlockSize=0 for every
                // 2026/27 course. If a real block size is ever seeded, this
                // must become block-aware (M23) instead of a flat 2h cap.
                while (left > 0) {
                    // TASK-637 (extends TASK-470's spread): theory-spread ON also
                    // demotes THEORY courses to 1h chunks. The 470 weekday preference
                    // alone was measurably INERT against M-TR (banco 21/09: dumps
                    // byte-identical): every 2h chunk is itself one M-TR cell (two
                    // same-day same-subject hours), so the lever is the chunk SIZE,
                    // not the weekday. The golden's practice is theory at 1h/day
                    // (~440 M-TR cells vs our 2696). Same exempt set as M-TR via
                    // SubjectNormalization.isTheoryForRule (single source). Labs keep
                    // the 2h chunks (M23/S17 blocks, golden-practiced).
                    int chunk = left >= 2 && !(isTheorySpreadEnabled()
                            && SubjectNormalization.isTheoryForRule(course.isLab, course.subject))
                            ? 2 : 1;
                    boolean placed = tryPlaceChunk(state, shape, group, course, chunk, quotaBoundLab, startOffset,
                            false /* morning-only pass */, teacherId);
                    if (!placed && chunk == 2) {
                        // Couldn't fit a 2h block anywhere this week — fall back to a single hour.
                        chunk = 1;
                        placed = tryPlaceChunk(state, shape, group, course, chunk, quotaBoundLab, startOffset,
                                false, teacherId);
                    }
                    if (!placed) {
                        // Morning-only exhausted — M28 last resort: allow the afternoon.
                        placed = tryPlaceChunk(state, shape, group, course, chunk, quotaBoundLab, startOffset,
                                true /* allow afternoon */, teacherId);
                    }
                    if (!placed) {
                        // No weekday can host this course again this week — the
                        // remainder becomes a leftover for the stamping/round-robin
                        // repair pass (task spec: "a small mixed pattern" is expected).
                        break;
                    }
                    left -= chunk;
                }
            }
        }

        return result;
    }

    /**
     * Pre-S2 7-arg overload for direct unit tests ({@code WeeklyTemplateBuilderTest}): no
     * mono-room tags, so the room-aware spike is inert even if its flag is ON (matches the
     * surface before S2). Production goes through the 8-arg build via WeeklyTemplatePlanner.
     */
    public static Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> build(
            List<GroupWeeklyShape> shapes,
            Map<Course, Integer> weeklyTargets,
            Set<TeacherSlot> unavailableTeacherSlots,
            Set<TeacherSlot> teachingTeacherSlots,
            Set<GroupSlot> blockedGroupSlots,
            Map<StudentGroup, Integer> maxHoursPerDay,
            Map<Long, Integer> teacherDailyCap) {
        return build(shapes, weeklyTargets, unavailableTeacherSlots, teachingTeacherSlots,
                blockedGroupSlots, maxHoursPerDay, teacherDailyCap, Set.of());
    }

    /**
     * TASK-430 (candidate, flag-gated): reserve one slot-0 anchor per (group, weekday)
     * distributively, BEFORE the main per-teacher placement, so a teacher shared across groups
     * is not monopolised at slot 0 by whichever group the greedy happens to process first (the
     * cross-group H1 contention the TASK-426 day-start anatomy isolated as the root cause of the
     * 90 non-anchored group-days). For each weekday independently it gathers, per group whose
     * calendar window opens slot 0, the courses whose teacher is free at (weekday, slot 0)
     * cross-group and that can legally take one hour there, then assigns greedily HARDEST-to-anchor
     * groups first (fewest candidates), each consuming its teacher's (weekday, slot 0).
     *
     * <p>The reservations are committed straight into the live occupancy maps ({@link
     * PlacementState}) and the result, and decrement {@code weeklyTargets}, so the UNCHANGED main
     * placement fills the rest of the week around them: every reserved cell is conflict-free by
     * construction (teacher checked, group slot checked, quota-kind cap checked), so the pass can
     * add no H1/H3 conflict and no over-quota cell. NON-ADOPTED: enabled only by
     * {@code -Dplanner.construction.slot0-balancing.enabled=true}; measured, not shipped.
     */
    private static void reserveSlot0Anchors(PlacementState state, Map<StudentGroup, GroupWeeklyShape> shapeByGroup,
            Map<Course, Integer> weeklyTargets) {
        final int anchorSlot = 0;
        for (DayOfWeek day : WEEKDAYS) {
            // group -> candidate courses (deterministic order: the courses list is already sorted
            // descending weekly-target then group/subject/teacher, so iteration is stable).
            Map<StudentGroup, List<Course>> candidatesByGroup = new TreeMap<>(Comparator.comparing(g -> g.name));
            for (Map.Entry<Course, Integer> entry : weeklyTargets.entrySet()) {
                Course course = entry.getKey();
                if (entry.getValue() == null || entry.getValue() <= 0) continue;
                StudentGroup group = course.studentGroup;
                if (group == null) continue;
                GroupWeeklyShape shape = shapeByGroup.get(group);
                if (shape == null) continue;
                int[] window = shape.windowFor(day);
                if (window == null || window[0] > anchorSlot || window[1] < anchorSlot) continue;
                if (shape.dailyTargetHours(day) == 0) continue;   // the plan asks for zero hours this weekday
                Long teacherId = teacherIdOf(course);
                TreeSet<Integer> teacherSlots = state.teacherOccupiedBySlotKey().get(teacherId);
                if (teacherSlots != null && teacherSlots.contains(slotKeyOrdinal(day, anchorSlot))) continue;
                // H7: a reserved hour counts toward the teacher's daily cap (mirrors tryPlaceChunk's check).
                TreeSet<Integer> teacherTeaching = state.teacherTeachingBySlotKey()
                        .computeIfAbsent(teacherId, k -> new TreeSet<>());
                Integer teacherCap = state.teacherDailyCap().get(teacherId);
                if (teacherCap != null && teacherCap > 0 && countInDay(teacherTeaching, day) + 1 > teacherCap) continue;
                TreeSet<Integer> groupSlots = state.groupOccupiedBySlotKey()
                        .computeIfAbsent(slotKeyId(group.id), k -> new TreeSet<>());
                if (groupSlots.contains(slotKeyOrdinal(day, anchorSlot))) continue;
                // H11 + day-target ceiling (mirrors tryPlaceChunk): one anchor hour must fit the day's
                // cap, else the reservation would commit a cell the stamp then refuses (a latent H11
                // over-commit caught only by defense-in-depth). Each group reserves at most one hour per
                // weekday, so the pre-reservation count + 1 is the post-reservation total.
                Integer dailyCap = state.maxHoursPerDay().get(group);
                int windowSlotCount = window[1] - window[0] + 1;
                int effectiveDailyCap = dailyCap != null && dailyCap > 0 ? Math.min(dailyCap, windowSlotCount) : windowSlotCount;
                int dayTarget = shape.dailyTargetHours(day);
                if (dayTarget >= 0) {
                    effectiveDailyCap = Math.min(effectiveDailyCap, dayTarget);
                }
                if (countInDay(groupSlots, day) + 1 > effectiveDailyCap) continue;
                if (shape.quotaGroup) {
                    boolean quotaBoundLab = DailyActivityQuota.isQuotaBoundLabSubject(group.name, course.subject);
                    int cap = shape.capFor(day, quotaBoundLab);
                    int[] counts = state.kindCounts().computeIfAbsent(group, k -> new HashMap<>())
                            .computeIfAbsent(day, k -> new int[2]);
                    int kindIndex = quotaBoundLab ? 1 : 0;
                    if (counts[kindIndex] + 1 > cap) continue;
                }
                candidatesByGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(course);
            }
            // Hardest-to-anchor first (fewest candidates); the scarcest teachers then go to the
            // groups that have no alternative, exactly the distribution the golden achieves.
            List<StudentGroup> groups = new ArrayList<>(candidatesByGroup.keySet());
            groups.sort(Comparator.comparingInt((StudentGroup g) -> candidatesByGroup.get(g).size())
                    .thenComparing(g -> g.name));
            for (StudentGroup group : groups) {
                Course picked = null;
                for (Course c : candidatesByGroup.get(group)) {
                    // Re-check: a prior reservation on this same weekday may have consumed the teacher
                    // or exhausted the course's target.
                    Long teacherId = teacherIdOf(c);
                    TreeSet<Integer> teacherSlots = state.teacherOccupiedBySlotKey().get(teacherId);
                    if (teacherSlots != null && teacherSlots.contains(slotKeyOrdinal(day, anchorSlot))) continue;
                    // H7 re-check: a prior reservation on this weekday may have filled the daily cap.
                    TreeSet<Integer> teacherTeaching = state.teacherTeachingBySlotKey()
                            .computeIfAbsent(teacherId, k -> new TreeSet<>());
                    Integer teacherCap = state.teacherDailyCap().get(teacherId);
                    if (teacherCap != null && teacherCap > 0 && countInDay(teacherTeaching, day) + 1 > teacherCap) continue;
                    if (weeklyTargets.getOrDefault(c, 0) <= 0) continue;
                    picked = c;
                    break;
                }
                if (picked == null) continue;
                Long teacherId = teacherIdOf(picked);
                state.teacherOccupiedBySlotKey().computeIfAbsent(teacherId, k -> new TreeSet<>())
                        .add(slotKeyOrdinal(day, anchorSlot));
                state.teacherTeachingBySlotKey().computeIfAbsent(teacherId, k -> new TreeSet<>())
                        .add(slotKeyOrdinal(day, anchorSlot));
                state.groupOccupiedBySlotKey().computeIfAbsent(slotKeyId(group.id), k -> new TreeSet<>())
                        .add(slotKeyOrdinal(day, anchorSlot));
                GroupWeeklyShape shape = shapeByGroup.get(group);
                if (shape.quotaGroup) {
                    boolean quotaBoundLab = DailyActivityQuota.isQuotaBoundLabSubject(group.name, picked.subject);
                    state.kindCounts().get(group).get(day)[quotaBoundLab ? 1 : 0] += 1;
                }
                weeklyTargets.merge(picked, -1, Integer::sum);   // main placement places one fewer hour of it
                state.result().computeIfAbsent(group, k -> new EnumMap<>(DayOfWeek.class))
                        .computeIfAbsent(day, k -> new ArrayList<>())
                        .add(new TemplateCell(picked, anchorSlot, 1));
            }
        }
    }

    /**
     * TASK-451 (candidate, flag-gated, default OFF): reserve a 2h slot0+slot1 first-hour block
     * per (group, weekday), BEFORE the main per-teacher placement, assigning the GOLDEN's own
     * slot-0 anchor teacher where it is stable. Generalizes {@link #reserveSlot0Anchors} (1h at
     * slot0, greedy) in two ways: (1) it reserves the BLOCK slot0+slot1, so slot1 is filled too
     * and the H17 late-start hole slot0-balancing left cannot open; (2) the teacher is the
     * golden's modal slot-0 anchor for (group, weekday) (e.g. T08 for G11 Friday), loaded
     * once via {@link ConstrainedTeacherAllocator#loadGoldenSlot0Anchors}, so the cross-group
     * assignment replicates the school's own coordination rather than a greedy shift.
     *
     * <p>Mirrors {@link #reserveSlot0Anchors}: for each weekday it gathers, per group whose
     * calendar window opens BOTH slot0 and slot1, the courses whose teacher is free at
     * (weekday, slot0 AND slot1) and can legally take a 2h block there (H7 teacher cap, H11 group
     * cap, quota-kind cap, weekly target >= 2), assigns HARDEST-to-block groups first (fewest
     * candidates) preferring the golden-anchor teacher within each group's candidate list, and
     * commits the block to the live maps + result + {@code weeklyTargets -= 2}. Every reserved
     * block is conflict-free by construction; the stamp ({@code DayClusteredConstructionPhase})
     * places {@code TemplateCell(course, 0, 2)} at slots 0-1 across every date of that weekday.
     * ADOPTED default ON (maintainer, 2026-07-31); opt-out via
     * {@code -Dplanner.construction.first-hour-slot.enabled=false}, see {@link #isFirstHourSlotEnabled()}.
     */
    /**
     * TASK-451 first-hour block flag, default ON since the 2026-07-31 ratification. Opt-out
     * semantics ({@code null} or anything but "false" enables), same form as
     * {@code HybridPhase1aService#isConstrainedTeacherEnabled()}: read live so the property
     * reaches the app JVM on the {@code java -jar} command line (MAVEN_OPTS lesson, CLAUDE.md).
     */
    public static boolean isFirstHourSlotEnabled() {
        String v = System.getProperty("planner.construction.first-hour-slot.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * TASK-470 (prototype, flag-gated DEFAULT OFF): theory-spread in the weekly template.
     * When ON, placing a theory (non-lab, non-practical-technical) course PREFERS a weekday
     * where that subject is not yet placed for the group this week (the golden's own practice:
     * theory at 1h/day rather than packed 2h blocks); if no such weekday can host the chunk it
     * FALLS BACK to today's first-fit, i.e. it may repeat the subject same-day. This is the
     * house "stamp can only decline, never force a hole" pattern, mirrored from the S2
     * room-aware spike: a pure preference inside {@link #tryPlaceChunk}'s weekday choice, so it
     * can introduce no H1/H3 conflict and leaves {@code stampSkipConflict} at its flag-OFF value.
     *
     * <p>Origin: the TASK-468 sweep verdict showed the 2h theory blocks are BORN in construction
     * (residual 1672 at end of construction vs the golden's ~440), inert to any M-TR weight in
     * [1,1000]; the lever is upstream here, not a weight. Opt-in like M-TC/H-COPRESENZA
     * ({@code Boolean.parseBoolean}, null/missing = OFF), read LIVE so {@code -D} on the
     * {@code java -jar} command line reaches the app JVM. ADOPTION = maintainer only; the gate is
     * {@code WeeklyTemplateConstructionRealSeedTest#theorySpreadOn_measuresResidualAtConstructionEnd}
     * (construction residual toward golden ~440-600, zero H1/H3, recurrence floor held) then a
     * full staged A/B.
     */
    public static boolean isTheorySpreadEnabled() {
        String v = System.getProperty("planner.construction.theory-spread.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    private static void reserveFirstHourBlock(PlacementState state, Map<StudentGroup, GroupWeeklyShape> shapeByGroup,
            Map<Course, Integer> weeklyTargets) {
        final int slotA = 0; // 08:00
        final int slotB = 1; // 09:00
        Map<String, Map<DayOfWeek, String>> goldenAnchor = ConstrainedTeacherAllocator.loadGoldenSlot0Anchors();
        for (DayOfWeek day : WEEKDAYS) {
            // group -> candidate courses; group -> golden-anchor teacher name (first-seen stable)
            Map<StudentGroup, List<Course>> candidatesByGroup = new TreeMap<>(Comparator.comparing(g -> g.name));
            Map<StudentGroup, String> anchorByGroup = new HashMap<>();
            for (Map.Entry<Course, Integer> entry : weeklyTargets.entrySet()) {
                Course course = entry.getKey();
                if (entry.getValue() == null || entry.getValue() < 2) {
                    continue; // a 2h block needs at least 2h of weekly target left
                }
                StudentGroup group = course.studentGroup;
                if (group == null) {
                    continue;
                }
                GroupWeeklyShape shape = shapeByGroup.get(group);
                if (shape == null) {
                    continue;
                }
                int[] window = shape.windowFor(day);
                if (window == null || window[0] > slotA || window[1] < slotB) {
                    continue; // the group's calendar must open both slot0 and slot1
                }
                int dayTarget = shape.dailyTargetHours(day);
                if (dayTarget >= 0 && dayTarget < 2) {
                    continue; // the plan asks for fewer than 2h this weekday (-1 = unknown fact, proceed to window/H11 cap)
                }
                if (!teacherFreeBothSlots(state, course, day, slotA, slotB)) {
                    continue; // teacher already committed at slot0 or slot1 this weekday
                }
                TreeSet<Integer> teacherTeaching = state.teacherTeachingBySlotKey()
                        .computeIfAbsent(teacherIdOf(course), k -> new TreeSet<>());
                Integer teacherCap = state.teacherDailyCap().get(teacherIdOf(course));
                if (teacherCap != null && teacherCap > 0 && countInDay(teacherTeaching, day) + 2 > teacherCap) {
                    continue; // H7: a 2h block must fit the teacher's daily cap
                }
                TreeSet<Integer> groupSlots = state.groupOccupiedBySlotKey()
                        .computeIfAbsent(slotKeyId(group.id), k -> new TreeSet<>());
                if (groupSlots.contains(slotKeyOrdinal(day, slotA)) || groupSlots.contains(slotKeyOrdinal(day, slotB))) {
                    continue; // group slot0 or slot1 already taken
                }
                Integer dailyCap = state.maxHoursPerDay().get(group);
                int windowSlotCount = window[1] - window[0] + 1;
                int effectiveDailyCap = dailyCap != null && dailyCap > 0
                        ? Math.min(dailyCap, windowSlotCount) : windowSlotCount;
                if (dayTarget >= 0) { // hoisted above: known fact caps the day; -1 = unknown, leave the window cap
                    effectiveDailyCap = Math.min(effectiveDailyCap, dayTarget);
                }
                if (countInDay(groupSlots, day) + 2 > effectiveDailyCap) {
                    continue; // H11 + day-target: a 2h block must fit the day's cap
                }
                if (shape.quotaGroup) {
                    boolean quotaBoundLab = DailyActivityQuota.isQuotaBoundLabSubject(group.name, course.subject);
                    int cap = shape.capFor(day, quotaBoundLab);
                    int[] counts = state.kindCounts().computeIfAbsent(group, k -> new HashMap<>())
                            .computeIfAbsent(day, k -> new int[2]);
                    int kindIndex = quotaBoundLab ? 1 : 0;
                    if (counts[kindIndex] + 2 > cap) {
                        continue; // quota-kind cap
                    }
                }
                candidatesByGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(course);
                String want = goldenAnchor.getOrDefault(group.name, Map.of()).get(day);
                if (want != null) {
                    anchorByGroup.putIfAbsent(group, want);
                }
            }
            // Hardest-to-block first (fewest candidates); within a group prefer the golden anchor.
            List<StudentGroup> groups = new ArrayList<>(candidatesByGroup.keySet());
            groups.sort(Comparator.comparingInt((StudentGroup g) -> candidatesByGroup.get(g).size())
                    .thenComparing(g -> g.name));
            for (StudentGroup group : groups) {
                String want = anchorByGroup.get(group);
                List<Course> cs = candidatesByGroup.get(group);
                // golden-anchor teacher first; tiebreak by SEMANTIC key (never raw course.id - the L2
                // determinism review at lines 236-247 measured 75-81% run-to-run recurrence variance
                // from id-based ordering, which differs across reseeds for identical school data).
                cs.sort(Comparator.comparing((Course c) -> !wantMatches(want, c))
                        .thenComparing(WeeklyTemplateBuilder::semanticCourseKey));
                Course picked = null;
                for (Course c : cs) {
                    // Re-check: a prior block this weekday may have consumed the teacher/target/group slot.
                    if (!teacherFreeBothSlots(state, c, day, slotA, slotB)) {
                        continue;
                    }
                    TreeSet<Integer> teacherTeaching = state.teacherTeachingBySlotKey()
                            .computeIfAbsent(teacherIdOf(c), k -> new TreeSet<>());
                    Integer teacherCap = state.teacherDailyCap().get(teacherIdOf(c));
                    if (teacherCap != null && teacherCap > 0 && countInDay(teacherTeaching, day) + 2 > teacherCap) {
                        continue;
                    }
                    if (weeklyTargets.getOrDefault(c, 0) < 2) {
                        continue;
                    }
                    TreeSet<Integer> groupSlots = state.groupOccupiedBySlotKey()
                            .computeIfAbsent(slotKeyId(group.id), k -> new TreeSet<>());
                    if (groupSlots.contains(slotKeyOrdinal(day, slotA)) || groupSlots.contains(slotKeyOrdinal(day, slotB))) {
                        continue;
                    }
                    picked = c;
                    break;
                }
                if (picked == null) {
                    continue;
                }
                Long teacherId = teacherIdOf(picked);
                state.teacherOccupiedBySlotKey().computeIfAbsent(teacherId, k -> new TreeSet<>())
                        .add(slotKeyOrdinal(day, slotA));
                state.teacherOccupiedBySlotKey().computeIfAbsent(teacherId, k -> new TreeSet<>())
                        .add(slotKeyOrdinal(day, slotB));
                state.teacherTeachingBySlotKey().computeIfAbsent(teacherId, k -> new TreeSet<>())
                        .add(slotKeyOrdinal(day, slotA));
                state.teacherTeachingBySlotKey().computeIfAbsent(teacherId, k -> new TreeSet<>())
                        .add(slotKeyOrdinal(day, slotB));
                state.groupOccupiedBySlotKey().computeIfAbsent(slotKeyId(group.id), k -> new TreeSet<>())
                        .add(slotKeyOrdinal(day, slotA));
                state.groupOccupiedBySlotKey().computeIfAbsent(slotKeyId(group.id), k -> new TreeSet<>())
                        .add(slotKeyOrdinal(day, slotB));
                GroupWeeklyShape shape = shapeByGroup.get(group);
                if (shape.quotaGroup) {
                    boolean quotaBoundLab = DailyActivityQuota.isQuotaBoundLabSubject(group.name, picked.subject);
                    state.kindCounts().get(group).get(day)[quotaBoundLab ? 1 : 0] += 2;
                }
                weeklyTargets.merge(picked, -2, Integer::sum); // main placement places 2 fewer hours of it
                state.result().computeIfAbsent(group, k -> new EnumMap<>(DayOfWeek.class))
                        .computeIfAbsent(day, k -> new ArrayList<>())
                        .add(new TemplateCell(picked, slotA, 2));
            }
        }
    }

    /** The stable teacher id of a course's teacher, mirroring {@code build}'s negative-id convention. */
    private static Long teacherIdOf(Course course) {
        return course.teacher != null ? course.teacher.id : -(course.id + 1);
    }

    /**
     * TASK-577 Fase A: the (teacher, group) PAIR key. Semantic (names, not ids)
     * like the sort keys it feeds, so an identical school re-seed produces the
     * identical pair identity. A no-teacher course is its OWN pair (mirrors
     * {@link #teacherIdOf}'s per-course negative sentinel: unrelated no-teacher
     * courses must not share a rotation).
     */
    private static String pairKeyOf(Course course) {
        String teacher = course.teacher != null ? course.teacher.name : ("noid" + teacherIdOf(course));
        String group = course.studentGroup != null ? course.studentGroup.name : "";
        return teacher + "|" + group;
    }

    /** True when the course's teacher is not committed (occupied) at either of two slots this weekday. */
    private static boolean teacherFreeBothSlots(PlacementState state, Course course, DayOfWeek day, int slotA, int slotB) {
        TreeSet<Integer> teacherSlots = state.teacherOccupiedBySlotKey().get(teacherIdOf(course));
        return teacherSlots == null
                || (!teacherSlots.contains(slotKeyOrdinal(day, slotA)) && !teacherSlots.contains(slotKeyOrdinal(day, slotB)));
    }

    /** True when the course's teacher name equals the golden-anchor teacher (the modal slot-0 anchor). */
    private static boolean wantMatches(String want, Course course) {
        return want != null && course.teacher != null && want.equals(course.teacher.name);
    }

    /**
     * The seven collections {@link #tryPlaceChunk} needs that stay the SAME
     * object across every call within one {@link #build} invocation - see
     * that method's own comment for why this exists. {@code
     * teacherOccupiedBySlotKey} blocks placement (unavailability + teaching);
     * {@code teacherTeachingBySlotKey} is the strict subset that counts
     * toward the H7 daily cap (review fix - see {@link #build}'s parameter
     * javadoc).
     */
    private record PlacementState(
            Map<Long, TreeSet<Integer>> teacherOccupiedBySlotKey,
            Map<Long, TreeSet<Integer>> teacherTeachingBySlotKey,
            Map<Long, TreeSet<Integer>> groupOccupiedBySlotKey,
            Map<StudentGroup, Map<DayOfWeek, int[]>> kindCounts,
            Map<StudentGroup, Integer> maxHoursPerDay,
            Map<Long, Integer> teacherDailyCap,
            boolean roomTagAware,
            boolean theorySpread,
            Set<String> monoRoomTags,
            Map<String, TreeSet<Integer>> tagOccupiedBySlotKey,
            Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> result,
            Random graspRandom) {
    }

    private static boolean tryPlaceChunk(PlacementState state, GroupWeeklyShape shape, StudentGroup group,
            Course course, int length, boolean quotaBoundLab, int startOffset, boolean allowAfternoon, Long teacherId) {
        // Two orthogonal "preference with fallback" dimensions (the house "stamp can only
        // decline, never force a hole" pattern): a room-tag dimension (S2 spike,
        // planner.template.room-aware.enabled) and a theory-subject dimension (TASK-470,
        // planner.construction.theory-spread.enabled). Each pass applies a STRICTER weekday
        // filter; the final pass is today's exact first-fit with no filter, so a placement is
        // never forced and the template stays conflict-free (no new H1/H3, stampSkipConflict
        // unchanged). With BOTH flags OFF every filter is inert and only the final pass runs,
        // giving a byte-identical template to today; with one flag OFF its dimension is inert
        // (monoTag == null skips pass 1, spread == false skips pass 2 and forces
        // skipSubjectOnDay=false in pass 1). PRIORITY when both are ON and they conflict on a
        // weekday: theory-spread outranks room-tag (pass 2 keeps the subject filter but drops the
        // tag filter) - declared here, not emergent from the cascade.
        String monoTag = monoTagFor(state, course);
        boolean spread = state.theorySpread()
                && SubjectNormalization.isTheoryForRule(course.isLab, course.subject);
        // Pass 0 (iter-11): teacher-weekday-spread preference - the same first-fit legality
        // but the weekdays are tried in TEACHER-LOAD order (fewest already-placed hours first),
        // so a recurring hour does not cluster into the weekday its teacher's other blocks own
        // (the G13-Friday anatomy: measured 20 promoted pins T10/G04 all on the weekday
        // his G13 block owns). FIRST because a teacher-level conflict outranks the subject-
        // level preferences below; still preference-only: every later pass is unchanged, so a
        // placement is never forced.
        if (teacherWeekdaySpreadEnabled() && placeChunkOnce(state, shape, group, course, length, quotaBoundLab,
                startOffset, allowAfternoon, teacherId, monoTag, false, false, true)) {
            return true;
        }
        // Pass 1: room-tag preference, also honouring theory-spread when active so a tagged
        // course prefers a weekday that is BOTH tag-free cross-group and free of this subject.
        if (monoTag != null && placeChunkOnce(state, shape, group, course, length, quotaBoundLab,
                startOffset, allowAfternoon, teacherId, monoTag, true, spread)) {
            return true;
        }
        // Pass 2: theory-spread preference only (a weekday where this subject is not yet placed
        // for the group this week). Skipped when theory-spread is OFF or the course is
        // lab/practical-technical (isTheoryForRule false).
        if (spread && placeChunkOnce(state, shape, group, course, length, quotaBoundLab,
                startOffset, allowAfternoon, teacherId, monoTag, false, true)) {
            return true;
        }
        // Pass 4 (fallback): today's exact first-fit, no preference. Always the last resort, so
        // a course that cannot spread repeats same-day exactly as it would today (the golden
        // itself repeats theory ~432 days when fill demands it; repetition is legitimate).
        return placeChunkOnce(state, shape, group, course, length, quotaBoundLab,
                startOffset, allowAfternoon, teacherId, monoTag, false, false);
    }

    /**
     * TASK-542 iter-11: teacher-weekday-spread preference in the template cell assignment.
     * A recurring course prefers the weekday where its teacher carries the FEWEST already-placed
     * template hours (anti-clustering: the G13-Friday anatomy measured a teacher's recurring
     * single hour frozen into the very weekday his other group's multi-hour block lives, where
     * no within-day move can ever untangle it - iter-8 geometry).
     *
     * <p>RATIFICATO 12/09 (maintainer): standard opt-out form - null/absent -> ON, only the literal
     * "false" disarms. Gate: spread-11sep [-11/0] (la migliore della serie: H0 G14 chiuse,
     * H33/H29/H31 azzerate, full-day in banda, collaterale H20 dell'iter-11 assente); la
     * semantica E' la regola del pianificatore umano (audio 11/09: "slot regolare per docente,
     * materia-insegnante e' la discriminante"). Ruga dichiarata: ricorrenza unpinned 70,3% vs
     * banda 76+-2. Letto LIVE per build like the sibling construction flags.</p>
     */
    public static boolean teacherWeekdaySpreadEnabled() {
        String v = System.getProperty("planner.construction.teacher-weekday-spread.enabled");
        return v == null || !v.equalsIgnoreCase("false");
    }

    private static boolean placeChunkOnce(PlacementState state, GroupWeeklyShape shape, StudentGroup group,
            Course course, int length, boolean quotaBoundLab, int startOffset, boolean allowAfternoon,
            Long teacherId, String monoTag, boolean skipTagTaken, boolean skipSubjectOnDay) {
        return placeChunkOnce(state, shape, group, course, length, quotaBoundLab, startOffset,
                allowAfternoon, teacherId, monoTag, skipTagTaken, skipSubjectOnDay, false);
    }

    private static boolean placeChunkOnce(PlacementState state, GroupWeeklyShape shape, StudentGroup group,
            Course course, int length, boolean quotaBoundLab, int startOffset, boolean allowAfternoon,
            Long teacherId, String monoTag, boolean skipTagTaken, boolean skipSubjectOnDay,
            boolean orderByTeacherLoad) {

        DayOfWeek[] dayOrder = WEEKDAYS;
        if (orderByTeacherLoad) {
            // fewest already-placed hours of THIS teacher first; ties keep the rotation order
            // (deterministic). teacherTeachingBySlotKey holds the teacher's real taught cells.
            TreeSet<Integer> teacherTeaching = teacherId == null ? null
                    : state.teacherTeachingBySlotKey().get(teacherId);
            java.util.Map<DayOfWeek, Integer> load = new java.util.EnumMap<>(DayOfWeek.class);
            for (DayOfWeek d : WEEKDAYS) {
                load.put(d, teacherTeaching == null ? 0 : countInDay(teacherTeaching, d));
            }
            DayOfWeek[] ordered = WEEKDAYS.clone();
            java.util.Arrays.sort(ordered, (a, b) -> {
                int c = Integer.compare(load.get(a), load.get(b));
                if (c != 0) {
                    return c;
                }
                return Integer.compare(java.util.Arrays.asList(WEEKDAYS).indexOf(a),
                        java.util.Arrays.asList(WEEKDAYS).indexOf(b));
            });
            dayOrder = ordered;
        }
        for (int i = 0; i < dayOrder.length; i++) {
            DayOfWeek day = dayOrder[(startOffset + i) % dayOrder.length];
            int[] window = shape.windowFor(day);
            if (window == null) continue;

            // capFor() only returns <=0 for quota groups (non-quota groups
            // always get Integer.MAX_VALUE) - no separate quotaGroup check needed.
            int cap = shape.capFor(day, quotaBoundLab);
            if (cap <= 0) continue;

            TreeSet<Integer> groupDaySlots = state.groupOccupiedBySlotKey()
                    .computeIfAbsent(slotKeyId(group.id), k -> new TreeSet<>());
            int effectiveMax = allowAfternoon ? window[1]
                    : Math.min(window[1], TimeslotSequence.AFTERNOON_START_SLOT_INDEX - 1);
            int nextSlot = nextContiguousSlot(groupDaySlots, day, window[0], effectiveMax, length,
                    state.graspRandom());
            if (nextSlot < 0) continue;

            Integer dailyCap = state.maxHoursPerDay().get(group);
            int windowSlotCount = window[1] - window[0] + 1;
            int effectiveDailyCap = dailyCap != null && dailyCap > 0 ? Math.min(dailyCap, windowSlotCount) : windowSlotCount;
            // TASK-385: the school's own per-date expected-hours plan caps the
            // day further still, when known - replaces the window as the
            // implicit "how full should this day be" target (a target that's
            // unset, -1, changes nothing here).
            int dayTarget = shape.dailyTargetHours(day);
            if (dayTarget >= 0) {
                effectiveDailyCap = Math.min(effectiveDailyCap, dayTarget);
            }
            int alreadyUsedToday = countInDay(groupDaySlots, day);
            if (alreadyUsedToday + length > effectiveDailyCap) continue;

            if (shape.quotaGroup) {
                int[] counts = state.kindCounts().computeIfAbsent(group, k -> new HashMap<>())
                        .computeIfAbsent(day, k -> new int[2]);
                int kindIndex = quotaBoundLab ? 1 : 0;
                if (counts[kindIndex] + length > cap) continue;
            }

            TreeSet<Integer> teacherSlots = state.teacherOccupiedBySlotKey()
                    .computeIfAbsent(teacherId, k -> new TreeSet<>());
            boolean teacherFree = true;
            for (int s = nextSlot; s < nextSlot + length; s++) {
                if (teacherSlots.contains(slotKeyOrdinal(day, s))) {
                    teacherFree = false;
                    break;
                }
            }
            if (!teacherFree) continue;

            // H7: teacher daily cap (null/absent = unrestricted, e.g. pseudo
            // teachers) - counted from teacherTeachingSlots ONLY, never from
            // mere unavailability (review fix: see build()'s
            // teachingTeacherSlots javadoc for the partial-day-vacation
            // under-allocation this corrects).
            TreeSet<Integer> teacherTeachingSlots = state.teacherTeachingBySlotKey()
                    .computeIfAbsent(teacherId, k -> new TreeSet<>());
            Integer teacherCap = state.teacherDailyCap().get(teacherId);
            if (teacherCap != null && teacherCap > 0 && countInDay(teacherTeachingSlots, day) + length > teacherCap) continue;

            // S2 SPIKE (pass 1 only): this cell's mono-tag is already taken cross-group by
            // another course, so try the next weekday. Pass 2 (skipTagTaken=false) ignores the
            // tag and falls back to today's first-fit, so the placement is never forced.
            if (skipTagTaken && monoTag != null && tagCellTaken(state, monoTag, day, nextSlot, length)) continue;

            // TASK-470 theory-spread (pass with skipSubjectOnDay=true only): this theory subject
            // is already placed for this group on this weekday, so prefer the next weekday. The
            // caller's fallback pass (skipSubjectOnDay=false) ignores this and repeats if no
            // alternative fits, so the placement is never forced and the day's fill is preserved.
            if (skipSubjectOnDay && subjectAlreadyOnDay(state.result(), group, course.subject, day)) continue;

            // All checks passed. Commit the placement.
            for (int s = nextSlot; s < nextSlot + length; s++) {
                teacherSlots.add(slotKeyOrdinal(day, s));
                teacherTeachingSlots.add(slotKeyOrdinal(day, s));
                groupDaySlots.add(slotKeyOrdinal(day, s));
            }
            // S2 SPIKE: record this tag's cell occupancy so later same-tag courses (any group)
            // can avoid it in their pass 1. Gated on monoTag (flag ON + tagged course).
            if (monoTag != null) {
                TreeSet<Integer> tagSet = state.tagOccupiedBySlotKey()
                        .computeIfAbsent(monoTag, k -> new TreeSet<>());
                for (int s = nextSlot; s < nextSlot + length; s++) tagSet.add(slotKeyOrdinal(day, s));
            }
            if (shape.quotaGroup) {
                state.kindCounts().get(group).get(day)[quotaBoundLab ? 1 : 0] += length;
            }
            state.result().computeIfAbsent(group, k -> new EnumMap<>(DayOfWeek.class))
                    .computeIfAbsent(day, k -> new ArrayList<>())
                    .add(new TemplateCell(course, nextSlot, length));
            return true;
        }
        return false;
    }

    /** S2 SPIKE: the course's mono-room tag if the flag is ON and it carries one, else null. */
    private static String monoTagFor(PlacementState state, Course course) {
        if (!state.roomTagAware() || state.monoRoomTags() == null || course.requiredRoomTags == null) {
            return null;
        }
        for (String tag : course.requiredRoomTags) {
            if (state.monoRoomTags().contains(tag)) return tag;   // first mono tag wins (2026/27: 1 tag/course)
        }
        return null;
    }

    /** S2 SPIKE: is any slot of [start, start+len) already claimed for this tag cross-group? */
    private static boolean tagCellTaken(PlacementState state, String tag, DayOfWeek day, int start, int len) {
        TreeSet<Integer> set = state.tagOccupiedBySlotKey().get(tag);
        if (set == null) return false;
        for (int s = start; s < start + len; s++) {
            if (set.contains(slotKeyOrdinal(day, s))) return true;
        }
        return false;
    }

    /**
     * TASK-470 theory-spread: is this subject already placed for the group on this weekday in
     * the template built so far? Reads the live {@code result} map (the single source of truth
     * for what the build has committed, kept in sync by {@link #placeChunkOnce} and the
     * {@code reserve*} pre-passes), so no separate tracker is needed and pre-reserved cells
     * (e.g. a first-hour block) are counted too. Comparison key is {@link
     * SubjectNormalization#theoryRuleGroupingKey}, the single-sourced "same subject" key shared
     * with the M-TR constraint (T06 alias hook included, identity by default), so the
     * construction and the constraint cannot drift on what counts as a repeat.
     */
    private static boolean subjectAlreadyOnDay(Map<StudentGroup, Map<DayOfWeek, List<TemplateCell>>> result,
            StudentGroup group, String subject, DayOfWeek day) {
        if (subject == null) {
            return false;
        }
        Map<DayOfWeek, List<TemplateCell>> byDay = result.get(group);
        if (byDay == null) {
            return false;
        }
        List<TemplateCell> cells = byDay.get(day);
        if (cells == null || cells.isEmpty()) {
            return false;
        }
        String key = SubjectNormalization.theoryRuleGroupingKey(subject);
        for (TemplateCell cell : cells) {
            if (cell.course() != null && cell.course().subject != null
                    && SubjectNormalization.theoryRuleGroupingKey(cell.course().subject).equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lowest slot {@code s} in {@code [windowMinSlot, windowMaxSlot]} where the
     * whole {@code [s, s + length - 1]} chunk is free THIS weekday, or {@code
     * -1} if no such chunk fits. Review fix: the previous implementation
     * returned {@code max(occupied) + 1} unconditionally, silently discarding
     * any free slots BELOW a mid-day occupied block (real case: a MODA
     * second-shift pin at slots 3-5 leaves 0-2 free but the old code jumped
     * straight to slot 6, forcing the placement into the afternoon or failing
     * outright). Scanning bottom-up from the window start naturally lands
     * adjacent to an existing block when the low slots are free (e.g. 0-2
     * next to occupied 3-5), which is exactly the compact placement wanted -
     * no separate adjacency logic needed.
     */
    private static int nextContiguousSlot(TreeSet<Integer> occupiedOrdinals, DayOfWeek day, int windowMinSlot,
            int windowMaxSlot, int length, Random grasp) {
        int dayBase = day.ordinal() * TimeslotSequence.SLOTS_PER_DAY;
        if (grasp == null) {
            // Deterministic path (production default): return the FIRST free slot bottom-up,
            // byte-identical to today. Kept eager rather than a full scan because this is the hot
            // path of every build, including the M32 reference re-publishes during a staged solve.
            for (int s = windowMinSlot; s + length - 1 <= windowMaxSlot; s++) {
                if (isChunkFree(occupiedOrdinals, dayBase, s, length)) return s;
            }
            return -1;
        }
        // GRASP path (TASK-480 gate): reservoir sampling (Algorithm R) picks one free slot
        // uniformly at random in a single pass, O(1) space and no per-call allocation, so two
        // builds with different seeds land the chunk on different slots.
        int count = 0;
        int selected = -1;
        for (int s = windowMinSlot; s + length - 1 <= windowMaxSlot; s++) {
            if (isChunkFree(occupiedOrdinals, dayBase, s, length)) {
                count++;
                if (grasp.nextInt(count) == 0) selected = s;
            }
        }
        return selected;
    }

    /** True iff every slot in [start, start + length) at the given day-base offset is unoccupied. */
    private static boolean isChunkFree(TreeSet<Integer> occupiedOrdinals, int dayBase, int start, int length) {
        for (int k = 0; k < length; k++) {
            if (occupiedOrdinals.contains(dayBase + start + k)) return false;
        }
        return true;
    }

    private static int countInDay(TreeSet<Integer> occupiedOrdinals, DayOfWeek day) {
        int count = 0;
        for (int ordinal : occupiedOrdinals) {
            if (ordinal / TimeslotSequence.SLOTS_PER_DAY == day.ordinal()) count++;
        }
        return count;
    }

    /** Encodes (weekday, slot) as a single comparable ordinal for the per-entity TreeSet. */
    private static int slotKeyOrdinal(DayOfWeek day, int slot) {
        return day.ordinal() * TimeslotSequence.SLOTS_PER_DAY + slot;
    }

    /** Stable per-teacher/group map key (both {@link TeacherSlot}/{@link GroupSlot} carry a raw id, not an entity). */
    private static Long slotKeyId(Long id) {
        return id != null ? id : -1L;
    }

    private static int freeCapacity(TreeSet<Integer> occupied) {
        int total = WEEKDAYS.length * TimeslotSequence.SLOTS_PER_DAY;
        return occupied == null ? total : total - occupied.size();
    }

    /**
     * Largest-remainder (Hamilton) apportionment (TASK-385): allocates
     * {@code weeklyTotal} integer "seats" across {@code hoursByCourse} in
     * proportion to each course's {@code hours / activeWeeks} share, so the
     * returned per-course targets sum to EXACTLY {@code weeklyTotal} whenever
     * there is enough candidate demand to reach it ("where demand allows" -
     * a plain per-course {@code Math.round} left systematic 1-2h daily holes,
     * because independent per-course rounding rarely sums to the group's
     * real weekly total, task-385's own measurement: 252 such days).
     *
     * <p>Each course is floor-rounded first; the shortfall (or, if the
     * floors already reach or exceed the total, the excess) is then
     * distributed to the courses with the largest (resp. smallest)
     * fractional remainder, AT MOST ONE bonus (resp. one deduction) seat per
     * course - the standard single-pass Hamilton method, so if the shortfall
     * exceeds the number of candidate courses (e.g. one course averaging 4h
     * some weeks against a 5h weekly total with no second course to carry
     * the rest) it is only PARTIALLY closed, never fabricated onto a single
     * course - courses tie-broken by a stable semantic key (subject/teacher/group names - no raw id, same cross-profile determinism rationale as the L2 review fixes) for
     * determinism, matching this class's own courses-list convention
     * elsewhere. {@code weeklyTotal &lt;= 0} or an empty input returns every
     * course at its plain floor (no seats to distribute) - the caller
     * decides whether that's meaningful (0 total means "no expected-hours
     * fact for this group", handled by the caller falling back to the old
     * per-course rounding instead of calling this at all).</p>
     */
    /** Stable cross-profile tie-break key - never raw ids (TASK-381 L2 review rationale). */
    private static String semanticCourseKey(Course c) {
        return (c.studentGroup != null ? c.studentGroup.name : "") + "|" + c.subject + "|"
                + (c.teacher != null ? c.teacher.name : "");
    }

    public static Map<Course, Integer> allocateLargestRemainder(
            Map<Course, Integer> hoursByCourse, double activeWeeks, int weeklyTotal) {
        Map<Course, Integer> floorTarget = new LinkedHashMap<>();
        if (hoursByCourse.isEmpty() || activeWeeks <= 0) {
            return floorTarget;
        }
        // Iteration order doesn't matter for the floor/remainder computation
        // itself (only the two directional sorts below, built fresh from
        // this list, care about order) - no upfront sort needed here.
        List<Course> courses = new ArrayList<>(hoursByCourse.keySet());

        Map<Course, Double> remainder = new HashMap<>();
        int floorSum = 0;
        for (Course c : courses) {
            double share = hoursByCourse.get(c) / activeWeeks;
            int floor = (int) Math.floor(share);
            floorTarget.put(c, floor);
            remainder.put(c, share - floor);
            floorSum += floor;
        }

        int diff = weeklyTotal - floorSum;
        if (diff > 0) {
            List<Course> byRemainderDesc = new ArrayList<>(courses);
            byRemainderDesc.sort(Comparator.<Course>comparingDouble(remainder::get).reversed()
                    .thenComparing(WeeklyTemplateBuilder::semanticCourseKey));
            for (int i = 0; i < diff && i < byRemainderDesc.size(); i++) {
                floorTarget.merge(byRemainderDesc.get(i), 1, Integer::sum);
            }
        } else if (diff < 0) {
            List<Course> byRemainderAsc = new ArrayList<>(courses);
            byRemainderAsc.sort(Comparator.<Course>comparingDouble(remainder::get)
                    .thenComparing(WeeklyTemplateBuilder::semanticCourseKey));
            int toRemove = -diff;
            // Walk the WHOLE list (not just the first `toRemove` positions),
            // and repeat the walk across MULTIPLE passes: a floor-0 course
            // can sort ahead of a floor>=1 course with a smaller true
            // remainder-of-1+ (e.g. share=0.5 floor=0 vs share=1.6 floor=1,
            // remainder 0.5 < 0.6) but can't be decremented - stopping at a
            // fixed index would waste that trim slot instead of finding the
            // next real candidate (code-review fix: an index-bounded loop
            // under-trimmed in exactly this case). A single pass over the
            // list is also insufficient on its own whenever toRemove exceeds
            // the number of floor>=1 courses (e.g. two courses at floor 5
            // each against toRemove=8: one pass only removes 2, one per
            // course) - each course can absorb more than one deduction, so
            // passes repeat until toRemove is exhausted or a full pass
            // removes nothing (every floor is 0). The latter can only occur
            // for a degenerate weeklyTotal < 0, which callers never pass -
            // it is not a real exhaustion case, just the loop's safety exit.
            int removed = 0;
            while (removed < toRemove) {
                boolean progressed = false;
                for (Course c : byRemainderAsc) {
                    if (removed >= toRemove) break;
                    int current = floorTarget.get(c);
                    if (current > 0) {
                        floorTarget.put(c, current - 1);
                        removed++;
                        progressed = true;
                    }
                }
                if (!progressed) break;
            }
        }
        return floorTarget;
    }
}
